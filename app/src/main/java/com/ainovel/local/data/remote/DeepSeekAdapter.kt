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
import kotlinx.serialization.json.JsonPrimitive
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

class DeepSeekAdapter(
    override val providerId: String = "deepseek",
    private val providerDisplayName: String = "DeepSeek",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
) : ProviderAdapter {
    override suspend fun listModels(baseUrl: String, apiKey: String): List<ProviderModel> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint(baseUrl, "models"))
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .get()
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw ProviderException(retryable = true, message = "无法连接 $providerDisplayName：${error.readableMessage()}", cause = error)
        }
        response.use {
            val body = it.body.string()
            if (!it.isSuccessful) throw httpError(it.code, body, apiKey)
            val root = parseObject(body)
            return@withContext root["data"]?.jsonArray.orEmpty().mapNotNull { element ->
                val id = element.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                inferCapabilities(id)
            }.filter { it.supportsTextGeneration }
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
        require(messages.isNotEmpty()) { "请求消息不能为空" }
        val body = buildRequestBody(modelId, messages, settings)
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

    override fun stream(
        baseUrl: String,
        apiKey: String,
        preparedRequest: PreparedRequest,
    ): Flow<StreamEvent> = flow {
        val request = Request.Builder()
            .url(endpoint(baseUrl, "chat/completions"))
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", if (preparedRequest.settings.streamOutput) "text/event-stream" else "application/json")
            .post(preparedRequest.serializedRequestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val call = client.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body.string()
                    throw httpError(response.code, errorBody, apiKey)
                }
                if (preparedRequest.settings.streamOutput) {
                    val content = StringBuilder()
                    val reasoning = StringBuilder()
                    var finishReason: String? = null
                    var usage: NormalizedUsage? = null
                    var usageParseError: String? = null
                    var sawDone = false
                    val source = response.body.source()
                    while (!source.exhausted()) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") {
                            sawDone = true
                            break
                        }
                        if (data.isBlank()) continue
                        val chunk = try {
                            parseObject(data)
                        } catch (error: Exception) {
                            if (usageParseError == null) usageParseError = "流式响应解析失败：${error.readableMessage()}"
                            continue
                        }
                        val choices = chunk["choices"]?.jsonArray.orEmpty()
                        val choice = choices.firstOrNull()?.jsonObject
                        val delta = choice?.get("delta")?.jsonObject
                        val contentDelta = delta?.stringOrNull("content").orEmpty()
                        val reasoningDelta = delta?.stringOrNull("reasoning_content").orEmpty()
                        if (contentDelta.isNotEmpty() || reasoningDelta.isNotEmpty()) {
                            content.append(contentDelta)
                            reasoning.append(reasoningDelta)
                            emit(StreamEvent.Delta(contentDelta, reasoningDelta.ifEmpty { null }))
                        }
                        choice?.stringOrNull("finish_reason")?.let { finishReason = it }
                        chunk["usage"]?.takeUnless { it is JsonNull }?.let { usageElement ->
                            try {
                                usage = parseUsage(usageElement.jsonObject, data)
                            } catch (error: Exception) {
                                usageParseError = "usage 解析失败：${error.readableMessage()}"
                            }
                        }
                    }
                    val complete = sawDone && finishReason != null
                    emit(
                        StreamEvent.Finished(
                            StreamResult(
                                content = content.toString(),
                                reasoningContent = reasoning.toString().ifBlank { null },
                                complete = complete,
                                finishReason = finishReason,
                                usage = usage,
                                usageParseError = usageParseError,
                            ),
                        ),
                    )
                } else {
                    val raw = response.body.string()
                    val root = parseObject(raw)
                    val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                    val message = choice?.get("message")?.jsonObject
                    val content = message?.stringOrNull("content").orEmpty()
                    val reasoning = message?.stringOrNull("reasoning_content")
                    val finishReason = choice?.stringOrNull("finish_reason")
                    var usage: NormalizedUsage? = null
                    var usageParseError: String? = null
                    root["usage"]?.takeUnless { it is JsonNull }?.let { usageElement ->
                        try {
                            usage = parseUsage(usageElement.jsonObject, json.encodeToString(JsonElement.serializer(), usageElement))
                        } catch (error: Exception) {
                            usageParseError = "usage 解析失败：${error.readableMessage()}"
                        }
                    }
                    if (content.isNotEmpty() || !reasoning.isNullOrEmpty()) {
                        emit(StreamEvent.Delta(content, reasoning))
                    }
                    emit(
                        StreamEvent.Finished(
                            StreamResult(
                                content = content,
                                reasoningContent = reasoning,
                                complete = finishReason != null,
                                finishReason = finishReason,
                                usage = usage,
                                usageParseError = usageParseError,
                            ),
                        ),
                    )
                }
            }
        } catch (error: CancellationException) {
            call.cancel()
            throw error
        } catch (error: ProviderException) {
            throw error
        } catch (error: IOException) {
            throw ProviderException(retryable = true, message = "$providerDisplayName 连接中断：${error.readableMessage()}", cause = error)
        } finally {
            if (!call.isExecuted()) call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequestBody(
        modelId: String,
        messages: List<ProviderMessage>,
        settings: GenerationSettings,
    ): JsonObject = buildJsonObject {
        put("model", modelId)
        put("messages", buildJsonArray {
            messages.forEach { message ->
                add(buildJsonObject {
                    put("role", message.role)
                    put("content", message.content)
                })
            }
        })
        put("stream", settings.streamOutput)
        if (settings.streamOutput && providerId in STREAM_USAGE_PROVIDERS) {
            put("stream_options", buildJsonObject { put("include_usage", true) })
        }
        settings.maxOutputTokens?.let { put("max_tokens", it) }

        when {
            providerId != "deepseek" -> {
                if (settings.thinkingEnabled) {
                    if (providerId == "openai" || providerId == "xai") {
                        put(
                            "reasoning_effort",
                            when (settings.reasoningStrength) {
                                ReasoningStrength.WEAK -> "low"
                                ReasoningStrength.STRONG -> "high"
                                ReasoningStrength.UNSUPPORTED,
                                ReasoningStrength.MEDIUM,
                                -> "medium"
                            },
                        )
                    }
                } else {
                    settings.temperature?.let { put("temperature", it) }
                }
            }
            modelId.startsWith("deepseek-v4") -> {
                put("thinking", buildJsonObject {
                    put("type", if (settings.thinkingEnabled) "enabled" else "disabled")
                })
                if (settings.thinkingEnabled && settings.reasoningStrength != ReasoningStrength.UNSUPPORTED) {
                    put(
                        "reasoning_effort",
                        when (settings.reasoningStrength) {
                            ReasoningStrength.STRONG -> "max"
                            ReasoningStrength.UNSUPPORTED,
                            ReasoningStrength.MEDIUM,
                            ReasoningStrength.WEAK,
                            -> "high"
                        },
                    )
                }
                if (!settings.thinkingEnabled) settings.temperature?.let { put("temperature", it) }
            }

            modelId == "deepseek-reasoner" -> Unit
            else -> settings.temperature?.let { put("temperature", it) }
        }
    }

    private fun parseUsage(usage: JsonObject, rawJson: String): NormalizedUsage {
        val input = usage.long("prompt_tokens")
        val output = usage.long("completion_tokens")
        val total = usage.long("total_tokens")
        val details = usage["completion_tokens_details"]?.takeUnless { it is JsonNull }?.jsonObject
        return NormalizedUsage(
            rawJson = rawJson,
            inputTokens = input,
            outputTokens = output,
            totalTokens = total,
            reasoningTokens = details?.get("reasoning_tokens")?.jsonPrimitive?.longOrNull,
            cacheTokens = usage["prompt_cache_hit_tokens"]?.jsonPrimitive?.longOrNull,
            complete = usage.containsKey("prompt_tokens") &&
                usage.containsKey("completion_tokens") &&
                usage.containsKey("total_tokens"),
        )
    }

    private fun parseObject(raw: String): JsonObject = json.parseToJsonElement(raw).jsonObject

    private fun endpoint(baseUrl: String, path: String): String =
        "${baseUrl.trim().trimEnd('/')}/${path.trimStart('/')}"

    private fun httpError(status: Int, body: String, apiKey: String): ProviderException {
        val safeBody = body.replace(apiKey, "***")
        val serverMessage = runCatching {
            val root = parseObject(safeBody)
            root["error"]?.jsonObject?.stringOrNull("message")
        }.getOrNull()
        val message = when (status) {
            401, 403 -> "接口密钥无效或没有权限"
            404 -> "接口地址或模型不存在"
            408, 429 -> "请求过于频繁或暂时超时"
            in 500..599 -> "$providerDisplayName 服务暂时不可用"
            else -> serverMessage ?: "$providerDisplayName 请求失败（HTTP $status）"
        }
        val diagnostic = if (
            status == 400 &&
            (serverMessage?.contains("context", ignoreCase = true) == true ||
                serverMessage?.contains("token", ignoreCase = true) == true)
        ) {
            "；可能已超过厂商实际上下文限制。本地最大上下文仅用于显示占用比例，可能与厂商限制不一致"
        } else {
            ""
        }
        return ProviderException(
            statusCode = status,
            retryable = status == 408 || status == 429 || status >= 500,
            responseBody = safeBody.take(4_000),
            message = message + diagnostic,
        )
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull

    private fun JsonObject.long(key: String): Long =
        this[key]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("缺少 $key")

    private fun Throwable.readableMessage(): String = message?.take(160) ?: this::class.java.simpleName

    private fun inferCapabilities(modelId: String): ProviderModel {
        val lower = modelId.lowercase()
        val nonTextModel = NON_TEXT_MODEL_MARKERS.any(lower::contains)
        val reasoning = when (providerId) {
            "deepseek" -> lower.startsWith("deepseek-v4") || lower == "deepseek-reasoner"
            "openai" -> lower.startsWith("o1") || lower.startsWith("o3") || lower.startsWith("o4") || lower.startsWith("gpt-5")
            "xai" -> lower.contains("grok")
            "moonshot" -> lower.contains("thinking") || lower.contains("k2") || lower.contains("k3")
            "zhipu" -> lower.contains("glm-z") || lower.contains("thinking")
            "minimax" -> lower.contains("m1") || lower.contains("m2")
            else -> lower.contains("reason") || lower.contains("thinking")
        }
        return ProviderModel(
            id = modelId,
            supportsTextGeneration = !nonTextModel,
            supportsReasoning = reasoning,
            supportsReasoningStrength = providerId == "deepseek" && lower.startsWith("deepseek-v4"),
        )
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val STREAM_USAGE_PROVIDERS = setOf("deepseek", "openai", "xai", "openrouter", "siliconflow")
        val NON_TEXT_MODEL_MARKERS = setOf(
            "embedding", "moderation", "whisper", "transcribe", "tts", "realtime",
            "dall-e", "image", "sora", "audio", "video",
        )
    }
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
