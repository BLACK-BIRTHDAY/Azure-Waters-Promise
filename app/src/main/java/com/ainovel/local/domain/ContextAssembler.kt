package com.ainovel.local.domain

import com.ainovel.local.data.local.NovelDao
import com.ainovel.local.data.model.ChapterEntity
import com.ainovel.local.data.model.ContextNodeType
import com.ainovel.local.data.model.EntryType
import com.ainovel.local.data.model.SummaryDensity
import com.ainovel.local.data.model.SummaryEntity
import com.ainovel.local.data.model.SummaryStatus
import com.ainovel.local.data.model.TaskType
import com.ainovel.local.data.remote.GenerationSettings
import com.ainovel.local.data.remote.LogicalContextItem
import com.ainovel.local.data.remote.PreparedRequest
import com.ainovel.local.data.remote.ProviderAdapter
import com.ainovel.local.data.remote.ProviderMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SummarySourceSelection(
    val nodeId: String,
    val nodeType: ContextNodeType,
)

@Serializable
data class CoverageOverlap(
    val chapterId: String,
    val coveringSummaryIds: List<String>,
)

class ContextAssembler(
    private val novelDao: NovelDao,
    private val json: Json,
) {
    suspend fun prepareBody(
        novelId: String,
        targetChapterId: String,
        providerId: String,
        modelId: String,
        oneTimeCreativeRequirement: String,
        settings: GenerationSettings,
        adapter: ProviderAdapter,
    ): PreparedRequest {
        require(oneTimeCreativeRequirement.isNotBlank()) { "请填写本次创作要求" }
        val logical = baseNovelContext(novelId).toMutableList()
        logical += LogicalContextItem(
            sourceId = "one-time-requirement",
            sourceType = "ONE_TIME_CREATIVE_REQUIREMENT",
            transmittedContent = oneTimeCreativeRequirement.trim(),
            order = logical.size,
        )
        val messages = logical.map { item -> item.toBodyMessage() }
        return adapter.prepare(
            taskType = TaskType.BODY,
            novelId = novelId,
            targetEntityId = targetChapterId,
            modelId = modelId,
            logicalContext = logical,
            messages = messages,
            settings = settings,
        )
    }

    suspend fun prepareSummary(
        novelId: String,
        sourceSelections: List<SummarySourceSelection>,
        density: SummaryDensity,
        providerId: String,
        modelId: String,
        settings: GenerationSettings,
        adapter: ProviderAdapter,
    ): PreparedRequest {
        require(sourceSelections.isNotEmpty()) { "请至少选择一个总结来源" }
        val novel = novelDao.novel(novelId) ?: error("小说不存在")
        val chapters = novelDao.allChapters(novelId).associateBy { it.id }
        val summaries = novelDao.allSummaries(novelId).associateBy { it.id }
        val selected = sourceSelections.map { selection ->
            when (selection.nodeType) {
                ContextNodeType.CHAPTER -> {
                    val chapter = chapters[selection.nodeId]
                        ?.takeUnless { it.deleted }
                        ?: error("章节来源已删除或不存在")
                    OrderedSource(chapter.storyOrder, selection, chapter.content, "章节")
                }

                ContextNodeType.SUMMARY -> {
                    val summary = summaries[selection.nodeId]
                        ?.takeUnless { it.status == SummaryStatus.INVALID }
                        ?: error("这份总结的来源已经失效")
                    OrderedSource(summary.logicalPosition, selection, summary.content, "总结")
                }
            }
        }.sortedWith(compareBy<OrderedSource> { it.position }.thenBy { it.selection.nodeId })

        val logical = buildList {
            add(
                LogicalContextItem(
                    sourceId = novel.id,
                    sourceType = "SUMMARY_INSTRUCTION",
                    localDisplayName = "小说总结指令",
                    transmittedContent = novel.summaryInstruction,
                    order = size,
                ),
            )
            selected.forEach { source ->
                add(
                    LogicalContextItem(
                        sourceId = source.selection.nodeId,
                        sourceType = source.selection.nodeType.name,
                        localDisplayName = source.localType,
                        transmittedContent = source.content,
                        order = size,
                    ),
                )
            }
            add(
                LogicalContextItem(
                    sourceId = "summary-density",
                    sourceType = "SUMMARY_DENSITY",
                    transmittedContent = densityPrompt(density),
                    order = size,
                ),
            )
        }
        val messages = logical.map { item ->
            when (item.sourceType) {
                "SUMMARY_INSTRUCTION" -> ProviderMessage("system", item.transmittedContent)
                "SUMMARY_DENSITY" -> ProviderMessage("user", item.transmittedContent)
                "CHAPTER" -> ProviderMessage("user", "[章节来源]\n${item.transmittedContent}")
                else -> ProviderMessage("user", "[已有总结来源]\n${item.transmittedContent}")
            }
        }
        return adapter.prepare(
            taskType = TaskType.SUMMARY,
            novelId = novelId,
            targetEntityId = null,
            modelId = modelId,
            logicalContext = logical,
            messages = messages,
            settings = settings,
        )
    }

    suspend fun prepareAssistant(
        novelId: String,
        currentSessionMessages: List<ProviderMessage>,
        newMessage: String,
        providerId: String,
        modelId: String,
        settings: GenerationSettings,
        adapter: ProviderAdapter,
    ): PreparedRequest {
        require(newMessage.isNotBlank()) { "请输入要讨论的问题" }
        val novel = novelDao.novel(novelId) ?: error("小说不存在")
        val logical = baseNovelContext(novelId).toMutableList()
        logical += LogicalContextItem(
            sourceId = novel.id,
            sourceType = "ASSISTANT_INSTRUCTION",
            localDisplayName = "小说助手指令",
            transmittedContent = novel.assistantInstruction,
            order = logical.size,
        )
        logical += LogicalContextItem(
            sourceId = "assistant-new-message",
            sourceType = "ASSISTANT_MESSAGE",
            transmittedContent = newMessage.trim(),
            order = logical.size,
        )
        val messages = buildList {
            addAll(baseNovelContextToMessages(logical.dropLast(2)))
            add(ProviderMessage("system", novel.assistantInstruction))
            addAll(currentSessionMessages.map { ProviderMessage(it.role, it.content) })
            add(ProviderMessage("user", newMessage.trim()))
        }
        return adapter.prepare(
            taskType = TaskType.ASSISTANT,
            novelId = novelId,
            targetEntityId = novelId,
            modelId = modelId,
            logicalContext = logical,
            messages = messages,
            settings = settings,
        )
    }

    suspend fun baseNovelContext(novelId: String): List<LogicalContextItem> {
        val entries = novelDao.enabledNovelEntries(novelId)
        val presets = entries.filter { it.type == EntryType.PRESET }.sortedBy { it.sortOrder }
        val worldbook = entries.filter { it.type == EntryType.WORLDBOOK }.sortedBy { it.sortOrder }
        val chapters = novelDao.allChapters(novelId)
            .filter { !it.deleted && it.includeInContext }
        val summaries = novelDao.allSummaries(novelId)
            .filter { it.includeInContext && it.status != SummaryStatus.INVALID }
        val nodes = buildList {
            chapters.forEach { add(OrderedNode(it.storyOrder, 0, it.id, "CHAPTER", it.displayName(), it.content)) }
            summaries.forEach { add(OrderedNode(it.logicalPosition, 1, it.id, "SUMMARY", it.name, it.content)) }
        }.sortedWith(compareBy<OrderedNode> { it.position }.thenBy { it.kindOrder }.thenBy { it.id })

        return buildList {
            presets.forEach { entry ->
                add(
                    LogicalContextItem(
                        sourceId = entry.id,
                        sourceType = "PRESET",
                        localDisplayName = entry.name,
                        transmittedContent = entry.content,
                        order = size,
                    ),
                )
            }
            worldbook.forEach { entry ->
                add(
                    LogicalContextItem(
                        sourceId = entry.id,
                        sourceType = "WORLDBOOK",
                        localDisplayName = entry.name,
                        transmittedContent = entry.content,
                        order = size,
                    ),
                )
            }
            nodes.forEach { node ->
                add(
                    LogicalContextItem(
                        sourceId = node.id,
                        sourceType = node.type,
                        localDisplayName = node.localName,
                        transmittedContent = node.content,
                        order = size,
                    ),
                )
            }
        }
    }

    suspend fun coverageOverlap(novelId: String): List<CoverageOverlap> {
        val includedChapterIds = novelDao.allChapters(novelId)
            .filter { !it.deleted && it.includeInContext }
            .mapTo(mutableSetOf()) { it.id }
        val includedSummaries = novelDao.allSummaries(novelId)
            .filter { it.includeInContext && it.status != SummaryStatus.INVALID }
        val coveredBy = linkedMapOf<String, MutableList<String>>()
        includedSummaries.forEach { summary ->
            decodeStringList(summary.coveredChapterIdsJson).forEach { chapterId ->
                if (chapterId in includedChapterIds) {
                    coveredBy.getOrPut(chapterId) { mutableListOf() } += summary.id
                }
            }
        }
        return coveredBy.map { (chapterId, summaries) -> CoverageOverlap(chapterId, summaries) }
    }

    suspend fun computeDefaultSummarySources(novelId: String): List<SummarySourceSelection> {
        val chapters = novelDao.allChapters(novelId).filter { !it.deleted }
        val summaries = novelDao.allSummaries(novelId).filter { it.status != SummaryStatus.INVALID }
        val coveredByValidSummaries = summaries
            .flatMap { decodeStringList(it.coveredChapterIdsJson) }
            .toSet()
        val summariesUsedByHigherLevel = summaries
            .flatMap { summary -> novelDao.sourcesForSummary(summary.id) }
            .filter { it.sourceNodeType == ContextNodeType.SUMMARY }
            .mapTo(mutableSetOf()) { it.sourceNodeId }

        val chapterSelections = chapters
            .filterNot { it.id in coveredByValidSummaries }
            .map { SummarySourceSelection(it.id, ContextNodeType.CHAPTER) }
        val summarySelections = summaries
            .filterNot { it.id in summariesUsedByHigherLevel }
            .sortedBy { it.logicalPosition }
            .map { SummarySourceSelection(it.id, ContextNodeType.SUMMARY) }
        return chapterSelections + summarySelections
    }

    private fun baseNovelContextToMessages(items: List<LogicalContextItem>): List<ProviderMessage> =
        items.map { it.toBodyMessage() }

    private fun LogicalContextItem.toBodyMessage(): ProviderMessage = when (sourceType) {
        "PRESET" -> ProviderMessage("system", "[写作规则]\n$transmittedContent")
        "WORLDBOOK" -> ProviderMessage("system", "[世界设定]\n$transmittedContent")
        "CHAPTER" -> ProviderMessage("system", "[已接受正文]\n$transmittedContent")
        "SUMMARY" -> ProviderMessage("system", "[已接受总结]\n$transmittedContent")
        "ONE_TIME_CREATIVE_REQUIREMENT" -> ProviderMessage("user", "[本次续写要求]\n$transmittedContent")
        else -> ProviderMessage("user", transmittedContent)
    }

    private fun densityPrompt(density: SummaryDensity): String = when (density) {
        SummaryDensity.CONCISE -> "请生成简洁总结，只保留对后续创作最关键的信息。"
        SummaryDensity.STANDARD -> "请生成标准密度总结，在完整与精炼之间保持平衡。"
        SummaryDensity.DETAILED -> "请生成详细总结，尽量保留有后续价值的状态、因果和细节。"
    }

    private fun ChapterEntity.displayName(): String = name?.takeIf { it.isNotBlank() } ?: "未命名章节"
    private fun decodeStringList(raw: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())

    private data class OrderedNode(
        val position: Long,
        val kindOrder: Int,
        val id: String,
        val type: String,
        val localName: String,
        val content: String,
    )

    private data class OrderedSource(
        val position: Long,
        val selection: SummarySourceSelection,
        val content: String,
        val localType: String,
    )
}
