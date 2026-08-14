package com.ainovel.local.data.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ainovel.local.data.model.AcceptanceRecordEntity
import com.ainovel.local.data.model.AssistantSessionEntity
import com.ainovel.local.data.model.AppSettingsEntity
import com.ainovel.local.data.model.CandidateEntity
import com.ainovel.local.data.model.CandidateGroupEntity
import com.ainovel.local.data.model.ChapterEntity
import com.ainovel.local.data.model.ChapterRevisionEntity
import com.ainovel.local.data.model.ContextEntryEntity
import com.ainovel.local.data.model.ModelSettingEntity
import com.ainovel.local.data.model.NovelEntity
import com.ainovel.local.data.model.ProviderConfigEntity
import com.ainovel.local.data.model.RequestSnapshotEntity
import com.ainovel.local.data.model.SummaryEntity
import com.ainovel.local.data.model.SummarySourceEntity
import com.ainovel.local.data.model.UsageRecordEntity

@Dao
interface BackupDao {
    @Query("SELECT * FROM app_settings") suspend fun appSettings(): List<AppSettingsEntity>
    @Query("SELECT * FROM novels") suspend fun novels(): List<NovelEntity>
    @Query("SELECT * FROM chapters") suspend fun chapters(): List<ChapterEntity>
    @Query("SELECT * FROM summaries") suspend fun summaries(): List<SummaryEntity>
    @Query("SELECT * FROM summary_sources") suspend fun summarySources(): List<SummarySourceEntity>
    @Query("SELECT * FROM context_entries") suspend fun contextEntries(): List<ContextEntryEntity>
    @Query("SELECT * FROM provider_configs") suspend fun providers(): List<ProviderConfigEntity>
    @Query("SELECT * FROM model_settings") suspend fun models(): List<ModelSettingEntity>
    @Query("SELECT * FROM candidate_groups") suspend fun groups(): List<CandidateGroupEntity>
    @Query("SELECT * FROM candidates") suspend fun candidates(): List<CandidateEntity>
    @Query("SELECT * FROM request_snapshots") suspend fun snapshots(): List<RequestSnapshotEntity>
    @Query("SELECT * FROM usage_records") suspend fun usage(): List<UsageRecordEntity>
    @Query("SELECT * FROM assistant_sessions") suspend fun sessions(): List<AssistantSessionEntity>
    @Query("SELECT * FROM chapter_revisions") suspend fun revisions(): List<ChapterRevisionEntity>
    @Query("SELECT * FROM acceptance_records") suspend fun acceptances(): List<AcceptanceRecordEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertNovels(items: List<NovelEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAppSettings(items: List<AppSettingsEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertChapters(items: List<ChapterEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSummaries(items: List<SummaryEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSummarySources(items: List<SummarySourceEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertContextEntries(items: List<ContextEntryEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertProviders(items: List<ProviderConfigEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertModels(items: List<ModelSettingEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertGroups(items: List<CandidateGroupEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCandidates(items: List<CandidateEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSnapshots(items: List<RequestSnapshotEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertUsage(items: List<UsageRecordEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSessions(items: List<AssistantSessionEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRevisions(items: List<ChapterRevisionEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAcceptances(items: List<AcceptanceRecordEntity>)

    @Query("DELETE FROM summary_sources") suspend fun clearSummarySources()
    @Query("DELETE FROM acceptance_records") suspend fun clearAcceptances()
    @Query("DELETE FROM chapter_revisions") suspend fun clearRevisions()
    @Query("DELETE FROM usage_records") suspend fun clearUsage()
    @Query("DELETE FROM candidates") suspend fun clearCandidates()
    @Query("DELETE FROM candidate_groups") suspend fun clearGroups()
    @Query("DELETE FROM request_snapshots") suspend fun clearSnapshots()
    @Query("DELETE FROM assistant_sessions") suspend fun clearSessions()
    @Query("DELETE FROM summaries") suspend fun clearSummaries()
    @Query("DELETE FROM context_entries") suspend fun clearContextEntries()
    @Query("DELETE FROM chapters") suspend fun clearChapters()
    @Query("DELETE FROM model_settings") suspend fun clearModels()
    @Query("DELETE FROM provider_configs") suspend fun clearProviders()
    @Query("DELETE FROM novels") suspend fun clearNovels()
    @Query("DELETE FROM app_settings") suspend fun clearAppSettings()
}
