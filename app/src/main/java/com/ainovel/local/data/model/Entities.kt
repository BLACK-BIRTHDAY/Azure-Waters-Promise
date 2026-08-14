package com.ainovel.local.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import kotlinx.serialization.Serializable

object Ids {
    fun newId(): String = UUID.randomUUID().toString()
}

@Serializable enum class EntryType { PRESET, WORLDBOOK }
@Serializable enum class EntryScope { GLOBAL, NOVEL }
@Serializable enum class SummaryStatus { VALID, STALE, INVALID }
@Serializable enum class SummaryDensity { CONCISE, STANDARD, DETAILED }
@Serializable enum class TaskType { BODY, SUMMARY, ASSISTANT, MODEL_VALIDATION }
@Serializable enum class CandidateGroupStatus { GENERATING, PENDING, ACCEPTED, DISCARDED, FAILED }
@Serializable enum class RevisionType { AUTOSAVE, CANDIDATE_ACCEPT, ACCEPT_UNDO, RESTORE }
@Serializable enum class ContextNodeType { CHAPTER, SUMMARY }
@Serializable enum class ResponseStatus { PREPARED, STREAMING, SUCCEEDED, PARTIAL, FAILED }
@Serializable enum class ReasoningKind { NONE, REASONING_CONTENT, REASONING_SUMMARY }

@Entity(tableName = "novels")
@Serializable
data class NovelEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val name: String,
    val bodyProviderId: String? = null,
    val bodyModelId: String? = null,
    val summaryProviderId: String? = null,
    val summaryModelId: String? = null,
    val assistantProviderId: String? = null,
    val assistantModelId: String? = null,
    val summaryInstruction: String = DefaultInstructions.summary,
    val assistantInstruction: String = DefaultInstructions.assistant,
    val bodyGenerationSettingsJson: String? = null,
    val summaryGenerationSettingsJson: String? = null,
    val assistantGenerationSettingsJson: String? = null,
    val coverFileName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("novelId"), Index(value = ["novelId", "storyOrder"], unique = true)],
)
@Serializable
data class ChapterEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val novelId: String,
    val storyOrder: Long,
    val name: String? = null,
    val content: String = "",
    val includeInContext: Boolean = true,
    val contentVersion: Long = 0,
    val deleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(
    tableName = "summaries",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("novelId")],
)
@Serializable
data class SummaryEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val novelId: String,
    val name: String,
    val content: String,
    val includeInContext: Boolean = true,
    val directSourceNodeIdsJson: String,
    val coveredChapterIdsJson: String,
    val sourceVersionSnapshotJson: String,
    val contentVersion: Long = 1,
    val status: SummaryStatus = SummaryStatus.VALID,
    val density: SummaryDensity = SummaryDensity.STANDARD,
    val instructionSnapshot: String,
    val logicalPosition: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(
    tableName = "summary_sources",
    primaryKeys = ["summaryId", "sourceNodeId"],
    indices = [Index("sourceNodeId")],
    foreignKeys = [
        ForeignKey(
            entity = SummaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["summaryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
@Serializable
data class SummarySourceEntity(
    val summaryId: String,
    val sourceNodeId: String,
    val sourceNodeType: ContextNodeType,
    val sourceContentVersion: Long,
)

@Entity(
    tableName = "context_entries",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("novelId"), Index(value = ["scope", "type", "sortOrder"])],
)
@Serializable
data class ContextEntryEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val type: EntryType,
    val scope: EntryScope,
    val novelId: String? = null,
    val name: String,
    val content: String,
    val enabled: Boolean = true,
    val sortOrder: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(tableName = "provider_configs")
@Serializable
data class ProviderConfigEntity(
    @PrimaryKey val providerId: String,
    val displayName: String,
    val baseUrl: String,
    val secretReference: String,
    val customParametersJson: String = "{}",
    val validationState: String = "NOT_CHECKED",
    val lastValidatedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(
    tableName = "model_settings",
    primaryKeys = ["providerId", "modelId"],
)
@Serializable
data class ModelSettingEntity(
    val providerId: String,
    val modelId: String,
    val maxContextTokens: Long = 1_048_576,
    val maxContextSource: String = "default",
    val userModified: Boolean = false,
    val capabilitiesJson: String = "{}",
    val manualModel: Boolean = false,
    val validated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(
    tableName = "request_snapshots",
    indices = [Index("novelId"), Index("taskType"), Index("candidateGroupId")],
)
@Serializable
data class RequestSnapshotEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val taskType: TaskType,
    val novelId: String?,
    val targetEntityId: String?,
    val candidateGroupId: String?,
    val providerId: String,
    val modelId: String,
    val logicalContextJson: String,
    val finalMessagesJson: String,
    val generationParametersJson: String,
    val redactedRequestBody: String,
    val responseStatus: ResponseStatus = ResponseStatus.PREPARED,
    val errorJson: String? = null,
    val usageMissing: Boolean = false,
    val usageParseErrorJson: String? = null,
    val requestedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)

@Entity(
    tableName = "candidate_groups",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RequestSnapshotEntity::class,
            parentColumns = ["id"],
            childColumns = ["frozenRequestSnapshotId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("novelId"), Index("targetChapterId"), Index("frozenRequestSnapshotId")],
)
@Serializable
data class CandidateGroupEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val taskType: TaskType,
    val novelId: String,
    val targetChapterId: String? = null,
    val oneTimeCreativeRequirement: String? = null,
    val summarySourceSnapshotJson: String? = null,
    val frozenRequestSnapshotId: String,
    val status: CandidateGroupStatus = CandidateGroupStatus.GENERATING,
    val createdAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
)

@Entity(
    tableName = "candidates",
    foreignKeys = [
        ForeignKey(
            entity = CandidateGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["candidateGroupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RequestSnapshotEntity::class,
            parentColumns = ["id"],
            childColumns = ["requestSnapshotId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("candidateGroupId"), Index("requestSnapshotId")],
)
@Serializable
data class CandidateEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val candidateGroupId: String,
    val rollNumber: Int,
    val rawOutput: String = "",
    val editedText: String = "",
    val reasoningContent: String? = null,
    val reasoningKind: ReasoningKind = ReasoningKind.NONE,
    val complete: Boolean = false,
    val accepted: Boolean = false,
    val requestSnapshotId: String,
    val usageRecordId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(
    tableName = "usage_records",
    indices = [Index(value = ["requestSnapshotId"], unique = true), Index("novelId"), Index("taskType")],
)
@Serializable
data class UsageRecordEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val requestSnapshotId: String,
    val novelId: String?,
    val taskType: TaskType,
    val rawUsageJson: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
    val reasoningTokens: Long? = null,
    val cacheTokens: Long? = null,
    val completeStatistics: Boolean,
    val providerId: String,
    val modelId: String,
    val maxContextTokensSnapshot: Long,
    val maxContextSourceSnapshot: String,
    val staleAgainstCurrentContent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "chapter_revisions",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("chapterId"), Index("candidateId"), Index("acceptanceId")],
)
@Serializable
data class ChapterRevisionEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val chapterId: String,
    val revisionType: RevisionType,
    val contentSnapshot: String,
    val candidateId: String? = null,
    val acceptanceId: String? = null,
    val current: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "acceptance_records",
    indices = [Index("chapterId"), Index(value = ["candidateId"], unique = true)],
)
@Serializable
data class AcceptanceRecordEntity(
    @PrimaryKey val id: String = Ids.newId(),
    val candidateId: String,
    val chapterId: String,
    val chapterVersionBefore: Long,
    val chapterVersionAfter: Long,
    val prefixLength: Int,
    val appendedText: String,
    val acceptedAt: Long = System.currentTimeMillis(),
    val undoneAt: Long? = null,
)

@Entity(
    tableName = "assistant_sessions",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
@Serializable
data class AssistantSessionEntity(
    @PrimaryKey val novelId: String,
    val messagesJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(tableName = "app_settings")
@Serializable
data class AppSettingsEntity(
    @PrimaryKey val id: String = SINGLETON_ID,
    val defaultSummaryInstruction: String = DefaultInstructions.summary,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
) {
    companion object {
        const val SINGLETON_ID = "app"
    }
}

object DefaultInstructions {
    val summary = """
        请仅根据给定来源总结小说内容，不要续写剧情，不要评价文笔，也不要添加来源中不存在的设定。
        保留重要姓名、数字和因果关系，并按以下结构输出：
        1. 主要剧情：按时间顺序记录重要事件及因果关系。
        2. 角色状态：位置、目标、关系、伤势、能力和立场变化。
        3. 重要设定与物品：已经出现并产生作用的地点、规则、组织和物品。
        4. 伏笔与未解决事项：未解决的冲突、承诺、秘密和后续线索。
        按指定总结密度删减对后续创作价值较低的描写细节。
    """.trimIndent()

    val assistant = """
        你是小说创作讨论助手。帮助作者分析剧情走向、人物动机、冲突设计和后续灵感。
        只提供讨论与建议，不声称已经修改正文、世界书、预设或创作需求。
        明确区分已知剧情事实、合理推断和新建议。
    """.trimIndent()
}
