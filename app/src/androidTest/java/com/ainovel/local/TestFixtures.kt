package com.ainovel.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ainovel.local.data.local.NovelDatabase
import com.ainovel.local.data.model.ModelSettingEntity
import com.ainovel.local.data.model.ProviderConfigEntity
import com.ainovel.local.data.model.TaskType
import com.ainovel.local.data.remote.GenerationSettings
import com.ainovel.local.data.remote.LogicalContextItem
import com.ainovel.local.data.remote.NormalizedUsage
import com.ainovel.local.data.remote.PreparedRequest
import com.ainovel.local.data.remote.ProviderAdapter
import com.ainovel.local.data.remote.ProviderMessage
import com.ainovel.local.data.remote.ProviderModel
import com.ainovel.local.data.remote.StreamEvent
import com.ainovel.local.data.remote.StreamResult
import com.ainovel.local.data.security.SecretStore
import com.ainovel.local.domain.ContextAssembler
import com.ainovel.local.domain.GenerationRepository
import com.ainovel.local.domain.NovelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MemorySecretStore : SecretStore {
    private val values = linkedMapOf<String, String>()
    override fun put(reference: String, secret: String) { values[reference] = secret }
    override fun get(reference: String): String? = values[reference]
    override fun delete(reference: String) { values.remove(reference) }
    fun snapshot(): Map<String, String> = values.toMap()
}

data class FakeResponse(
    val content: String,
    val reasoning: String? = null,
    val complete: Boolean = true,
    val usage: NormalizedUsage? = NormalizedUsage(
        rawJson = "{\"prompt_tokens\":11,\"completion_tokens\":7,\"total_tokens\":18}",
        inputTokens = 11,
        outputTokens = 7,
        totalTokens = 18,
        complete = true,
    ),
)

class FakeProviderAdapter(
    responses: List<FakeResponse>,
    private val json: Json,
    private val modelListFailure: Exception? = null,
) : ProviderAdapter {
    override val providerId: String = "fake"
    private val queue = ArrayDeque(responses)
    val streamedBodies = mutableListOf<String>()

    override suspend fun listModels(baseUrl: String, apiKey: String): List<ProviderModel> {
        modelListFailure?.let { throw it }
        return listOf(ProviderModel("fake-model", supportsReasoning = true, supportsReasoningStrength = true))
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
        val body = json.encodeToString(
            mapOf(
                "model" to modelId,
                "messages" to json.encodeToString(messages),
                "settings" to json.encodeToString(settings),
            ),
        )
        return PreparedRequest(
            taskType = taskType,
            novelId = novelId,
            targetEntityId = targetEntityId,
            providerId = providerId,
            modelId = modelId,
            logicalContext = logicalContext,
            messages = messages,
            settings = settings,
            serializedRequestBody = body,
            redactedRequestBody = body,
        )
    }

    override fun stream(baseUrl: String, apiKey: String, preparedRequest: PreparedRequest): Flow<StreamEvent> = flow {
        streamedBodies += preparedRequest.serializedRequestBody
        val response = queue.removeFirstOrNull() ?: error("测试响应队列为空")
        if (response.content.isNotEmpty() || !response.reasoning.isNullOrEmpty()) {
            emit(StreamEvent.Delta(response.content, response.reasoning))
        }
        emit(
            StreamEvent.Finished(
                StreamResult(
                    content = response.content,
                    reasoningContent = response.reasoning,
                    complete = response.complete,
                    finishReason = if (response.complete) "stop" else null,
                    usage = response.usage,
                ),
            ),
        )
    }
}

class RepositoryFixture(
    responses: List<FakeResponse>,
    modelListFailure: Exception? = null,
) : AutoCloseable {
    val context: Context = ApplicationProvider.getApplicationContext()
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    val database = Room.inMemoryDatabaseBuilder(context, NovelDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val secrets = MemorySecretStore()
    val adapter = FakeProviderAdapter(responses, json, modelListFailure)
    val assembler = ContextAssembler(database.novelDao(), json)
    val novels = NovelRepository(database, database.novelDao(), database.generationDao(), database.modelDao(), json)
    val generation = GenerationRepository(
        database,
        database.novelDao(),
        database.generationDao(),
        database.modelDao(),
        secrets,
        assembler,
        json,
        adapters = mapOf("fake" to adapter),
    )

    suspend fun configuredNovel(name: String = "测试小说") = novels.createNovel(name).also { novel ->
        val secretRef = "provider:fake:key"
        secrets.put(secretRef, "unit-test-secret")
        database.novelDao().upsertProvider(
            ProviderConfigEntity(
                providerId = "fake",
                displayName = "Fake",
                baseUrl = "https://example.invalid",
                secretReference = secretRef,
                validationState = "VERIFIED",
            ),
        )
        database.modelDao().upsertModel(
            ModelSettingEntity(providerId = "fake", modelId = "fake-model", validated = true),
        )
        novels.updateNovelModels(
            novel.id,
            bodyProviderId = "fake",
            bodyModelId = "fake-model",
            summaryProviderId = "fake",
            summaryModelId = "fake-model",
            assistantProviderId = "fake",
            assistantModelId = "fake-model",
        )
    }

    override fun close() = database.close()
}
