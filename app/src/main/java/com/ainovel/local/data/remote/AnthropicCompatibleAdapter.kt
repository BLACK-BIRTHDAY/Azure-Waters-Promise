package com.ainovel.local.data.remote

import com.ainovel.local.data.model.TaskType
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AnthropicCompatibleAdapter(
    override val providerId: String = "anthropic-compatible",
    private val providerDisplayName: String = "Anthropic 兼容接口",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true },
) : ProviderAdapter {
    override suspend fun listModels(baseUrl: String, apiKey: String): List<ProviderModel> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint(baseUrl, "models"))
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("Accept", "application/json")
            .get()
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw ProviderException(retryable = true, message = "无法连接 $providerDisplayName：${error.message.orEmpty()}", cause = error)
        }
        response.use {
            val body = it.body.string()
            if (!it.isSuccessful) throw httpError(it.code, body, apiKey)
            parseObject(body)["data"]?.jsonArray.orEmpty().mapNotNull { element ->
                val id = element.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                ProviderModel(id = id)
            }
        }
    }

    override fun prepare(
        taskType: TaskType,
        novelId: String?,
        targetEntityId: String?,
        modelId: String,
        logicalContext: List<LogicalContextItem>,
        messages: List<ProviderMessage>,
        settings: GenerationSettings,
    ): PreparedRequest {
        require(modelId.isNotBlank()) { "请选择模型" }
        val system = messages.filter { it.role == "system" }.joinToString("\n") { it.content }
        val body = buildJsonObject {
            put("model", modelId)
            put("max_tokens", settings.maxOutputTokens ?: DEFAULT_MAX_TOKENS)
            put("stream", settings.streamOutput)
            if (system.isNotBlank()) put("system", system)
            put("messages", buildJsonArray {
                messages.filterNot { it.role == "system" }.forEach { message ->
                    add(buildJsonObject {
                        put("role", if (message.role == "assistant") "assistant" else "user")
                        put("content", message.content)
                    })
                }
            })
            settings.temperature?.let { put("temperature", it.coerceIn(0.0, 1.0)) }
        }
        val serialized = json.encodeToString(JsonObject.serializer(), body)
        return PreparedRequest(
            taskType = taskType,
            novelId = novelId,
            targetEntityId = targetEntityId,
            providerId = providerId,
            modelId = modelId,
            logicalContext = logicalContext,
            messages = messages,
            settings = settings,
            serializedRequestBody = serialized,
            redactedRequestBody = serialized,
        )
    }

    override fun stream(baseUrl: String, apiKey: String, preparedRequest: PreparedRequest): Flow<StreamEvent> = flow {
        val request = Request.Builder()
            .url(endpoint(baseUrl, "messages"))
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("Accept", if (preparedRequest.settings.streamOutput) "text/event-stream" else "application/json")
            .post(preparedRequest.serializedRequestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val call = client.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw httpError(response.code, response.body.string(), apiKey)
                if (preparedRequest.settings.streamOutput) {
                    val content = StringBuilder()
                    var finishReason: String? = null
                    var inputTokens: Long? = null
                    var outputTokens: Long? = null
                    val source = response.body.source()
                    while (!source.exhausted()) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val raw = line.removePrefix("data:").trim()
                        if (raw.isBlank() || raw == "[DONE]") continue
                        val event = runCatching { parseObject(raw) }.getOrNull() ?: continue
                        when (event.string("type")) {
                            "message_start" -> inputTokens = event["message"]?.jsonObject?.get("usage")?.jsonObject?.longOrNull("input_tokens")
                            "content_block_delta" -> {
                                val delta = event["delta"]?.jsonObject
                                val text = delta?.string("text").orEmpty()
                                if (text.isNotEmpty()) { content.append(text); emit(StreamEvent.Delta(text, null)) }
                            }
                            "message_delta" -> {
                                finishReason = event["delta"]?.jsonObject?.string("stop_reason")
                                outputTokens = event["usage"]?.jsonObject?.longOrNull("output_tokens")
                            }
                        }
                    }
                    val usage = usage(inputTokens, outputTokens)
                    emit(StreamEvent.Finished(StreamResult(content.toString(), null, finishReason != null, finishReason, usage, if (usage == null) "厂商未返回完整 usage" else null)))
                } else {
                    val raw = response.body.string()
                    val root = parseObject(raw)
                    val content = root["content"]?.jsonArray.orEmpty().joinToString("") { block ->
                        block.jsonObject.takeIf { it.string("type") == "text" }?.string("text").orEmpty()
                    }
                    val usageObject = root["usage"]?.takeUnless { it is JsonNull }?.jsonObject
                    val usage = usage(usageObject?.longOrNull("input_tokens"), usageObject?.longOrNull("output_tokens"))
                    if (content.isNotEmpty()) emit(StreamEvent.Delta(content, null))
                    emit(StreamEvent.Finished(StreamResult(content, null, root.string("stop_reason") != null, root.string("stop_reason"), usage, if (usage == null) "厂商未返回完整 usage" else null)))
                }
            }
        } catch (error: CancellationException) {
            call.cancel(); throw error
        } catch (error: ProviderException) {
            throw error
        } catch (error: IOException) {
            throw ProviderException(retryable = true, message = "$providerDisplayName 连接中断：${error.message.orEmpty()}", cause = error)
        } finally {
            if (!call.isExecuted()) call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun usage(input: Long?, output: Long?): NormalizedUsage? {
        if (input == null || output == null) return null
        return NormalizedUsage(
            rawJson = json.encodeToString(mapOf("input_tokens" to input, "output_tokens" to output)),
            inputTokens = input,
            outputTokens = output,
            totalTokens = input + output,
            complete = true,
        )
    }

    private fun endpoint(baseUrl: String, path: String) = "${baseUrl.trim().trimEnd('/')}/${path.trimStart('/')}"
    private fun parseObject(raw: String): JsonObject = json.parseToJsonElement(raw).jsonObject
    private fun JsonObject.string(key: String): String? = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
    private fun JsonObject.longOrNull(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
    private fun httpError(status: Int, body: String, apiKey: String): ProviderException {
        val safe = body.replace(apiKey, "***")
        return ProviderException(
            statusCode = status,
            retryable = status == 408 || status == 429 || status >= 500,
            responseBody = safe.take(4_000),
            message = when (status) {
                401, 403 -> "接口密钥无效或没有权限"
                404 -> "接口地址或模型不存在"
                408, 429 -> "请求过于频繁或暂时超时"
                else -> "$providerDisplayName 请求失败（HTTP $status）"
            },
        )
    }

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val DEFAULT_MAX_TOKENS = 8_192L
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
