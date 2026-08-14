package com.ainovel.local.data.remote

import com.ainovel.local.data.model.TaskType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ProviderModel(
    val id: String,
    val displayName: String = id,
    val supportsTextGeneration: Boolean = true,
    val supportsStreaming: Boolean = true,
    val supportsReasoning: Boolean = false,
    val supportsReasoningStrength: Boolean = false,
)

@Serializable
data class GenerationSettings(
    val streamOutput: Boolean = true,
    val thinkingEnabled: Boolean = false,
    val streamThinking: Boolean = false,
    val reasoningStrength: ReasoningStrength = ReasoningStrength.MEDIUM,
    val temperature: Double? = 0.7,
    val maxOutputTokens: Long? = null,
)

@Serializable
enum class ReasoningStrength { UNSUPPORTED, WEAK, MEDIUM, STRONG }

@Serializable
data class LogicalContextItem(
    val sourceId: String,
    val sourceType: String,
    val localDisplayName: String? = null,
    val transmittedContent: String,
    val order: Int,
)

@Serializable
data class ProviderMessage(
    val role: String,
    val content: String,
    /**
     * 仅用于本地会话展示和追溯。适配器发送请求时不会把它写入 messages，
     * 因而模型推理不会进入下一轮上下文。
     */
    val reasoningContent: String? = null,
    /** null 表示旧会话未知；true/false 只用于向作者说明本次是否请求了思考。 */
    val reasoningRequested: Boolean? = null,
)

@Serializable
data class PreparedRequest(
    val taskType: TaskType,
    val novelId: String?,
    val targetEntityId: String?,
    val providerId: String,
    val modelId: String,
    val logicalContext: List<LogicalContextItem>,
    val messages: List<ProviderMessage>,
    val settings: GenerationSettings,
    val serializedRequestBody: String,
    val redactedRequestBody: String,
)

@Serializable
data class NormalizedUsage(
    val rawJson: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
    val reasoningTokens: Long? = null,
    val cacheTokens: Long? = null,
    val complete: Boolean,
)

data class StreamResult(
    val content: String,
    val reasoningContent: String?,
    val complete: Boolean,
    val finishReason: String?,
    val usage: NormalizedUsage?,
    val usageParseError: String? = null,
)

sealed interface StreamEvent {
    data class Delta(val content: String, val reasoningContent: String?) : StreamEvent
    data class Finished(val result: StreamResult) : StreamEvent
}

class ProviderException(
    val statusCode: Int? = null,
    val retryable: Boolean,
    val responseBody: String? = null,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
