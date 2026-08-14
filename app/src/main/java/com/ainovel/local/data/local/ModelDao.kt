package com.ainovel.local.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ainovel.local.data.model.ModelSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM model_settings ORDER BY providerId, modelId")
    fun observeAllModels(): Flow<List<ModelSettingEntity>>

    @Query("SELECT * FROM model_settings WHERE providerId = :providerId ORDER BY modelId")
    fun observeModels(providerId: String): Flow<List<ModelSettingEntity>>

    @Query("SELECT * FROM model_settings WHERE providerId = :providerId ORDER BY modelId")
    suspend fun models(providerId: String): List<ModelSettingEntity>

    @Query("SELECT * FROM model_settings")
    suspend fun allModels(): List<ModelSettingEntity>

    @Query("SELECT * FROM model_settings WHERE providerId = :providerId AND modelId = :modelId")
    suspend fun model(providerId: String, modelId: String): ModelSettingEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertModelsWithoutOverwriting(models: List<ModelSettingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModel(model: ModelSettingEntity)

    @Query("UPDATE model_settings SET maxContextTokens = :tokens, maxContextSource = 'manual', userModified = 1, updatedAt = :updatedAt WHERE providerId = :providerId AND modelId = :modelId")
    suspend fun setMaxContext(
        providerId: String,
        modelId: String,
        tokens: Long,
        updatedAt: Long = System.currentTimeMillis(),
    ): Int
}
