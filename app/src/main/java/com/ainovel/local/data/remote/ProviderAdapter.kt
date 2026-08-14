package com.ainovel.local.data.remote

import com.ainovel.local.data.model.TaskType
import kotlinx.coroutines.flow.Flow

interface ProviderAdapter {
    val providerId: String

    suspend fun listModels(baseUrl: String, apiKey: String): List<ProviderModel>

    fun prepare(
        taskType: TaskType,
        novelId: String?,
        targetEntityId: String?,
        modelId: String,
        logicalContext: List<LogicalContextItem>,
        messages: List<ProviderMessage>,
        settings: GenerationSettings,
    ): PreparedRequest

    fun stream(
        baseUrl: String,
        apiKey: String,
        preparedRequest: PreparedRequest,
    ): Flow<StreamEvent>
}
