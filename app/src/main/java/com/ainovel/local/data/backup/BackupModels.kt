package com.ainovel.local.data.backup

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
import kotlinx.serialization.Serializable

@Serializable
data class BackupPayload(
    val appSettings: List<AppSettingsEntity> = emptyList(),
    val novels: List<NovelEntity>,
    val chapters: List<ChapterEntity>,
    val summaries: List<SummaryEntity>,
    val summarySources: List<SummarySourceEntity>,
    val contextEntries: List<ContextEntryEntity>,
    val providerConfigs: List<ProviderConfigEntity>,
    val providerSecrets: Map<String, String>,
    val modelSettings: List<ModelSettingEntity>,
    val candidateGroups: List<CandidateGroupEntity>,
    val candidates: List<CandidateEntity>,
    val requestSnapshots: List<RequestSnapshotEntity>,
    val usageRecords: List<UsageRecordEntity>,
    val assistantSessions: List<AssistantSessionEntity>,
    val chapterRevisions: List<ChapterRevisionEntity>,
    val acceptanceRecords: List<AcceptanceRecordEntity>,
    val coverImages: Map<String, String> = emptyMap(),
)

@Serializable
data class BackupEnvelope(
    val magic: String = "AINOVELBACKUP",
    val formatVersion: Int = 1,
    val createdAt: Long,
    val saltBase64: String,
    val ivBase64: String,
    val cipherTextBase64: String,
    val encryptedPayloadSha256: String,
)

data class BackupRestorePreview(
    val novelCount: Int,
    val chapterCount: Int,
    val summaryCount: Int,
    val candidateCount: Int,
    val requestCount: Int,
    val createdAt: Long,
)
