package com.ainovel.local.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.ainovel.local.ui.localization.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainovel.local.data.model.AcceptanceRecordEntity
import com.ainovel.local.data.model.CandidateEntity
import com.ainovel.local.data.model.RequestSnapshotEntity
import com.ainovel.local.data.model.ResponseStatus
import com.ainovel.local.data.model.TaskType
import com.ainovel.local.data.model.UsageRecordEntity
import com.ainovel.local.ui.AppViewModel
import com.ainovel.local.domain.HistoryCleanupPreview
import com.ainovel.local.ui.components.StatusPill
import com.ainovel.local.ui.components.StatusTone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: AppViewModel) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val candidates by viewModel.historyCandidates.collectAsStateWithLifecycle()
    val usage by viewModel.historyUsage.collectAsStateWithLifecycle()
    val acceptances by viewModel.acceptances.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<TaskType?>(null) }
    var ageDays by remember { mutableStateOf<Int?>(null) }
    var cleanupPreview by remember { mutableStateOf<HistoryCleanupPreview?>(null) }
    var selected by remember { mutableStateOf<RequestSnapshotEntity?>(null) }
    val candidateByRequest = remember(candidates) { candidates.associateBy { it.requestSnapshotId } }
    val usageByRequest = remember(usage) { usage.associateBy { it.requestSnapshotId } }
    val filtered = remember(history, filter, ageDays) {
        val cutoff = ageDays?.let { System.currentTimeMillis() - it * 86_400_000L }
        history.filter { snapshot ->
            (filter == null || snapshot.taskType == filter) && (cutoff == null || snapshot.requestedAt < cutoff)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("创作足迹", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "每一次生成都会留下可以回看的痕迹；以后调整模型或故事记忆，也不会改变过去。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.padding(12.dp))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("全部 ${history.size}") }) }
            items(listOf(TaskType.BODY, TaskType.SUMMARY, TaskType.ASSISTANT)) { task ->
                FilterChip(selected = filter == task, onClick = { filter = task }, label = { Text(taskLabel(task)) })
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = ageDays == null, onClick = { ageDays = null }, label = { Text("不限时间") }) }
                items(listOf(7, 30, 90)) { days ->
                    FilterChip(selected = ageDays == days, onClick = { ageDays = days }, label = { Text("早于 $days 天") })
                }
            }
            OutlinedButton(
                onClick = {
                    val olderThan = ageDays?.let { System.currentTimeMillis() - it * 86_400_000L }
                    viewModel.previewHistoryCleanup(filter, olderThan) { cleanupPreview = it }
                },
            ) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                Text("清理", modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (filtered.isEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
                Text("还没有留下符合条件的创作记录。", modifier = Modifier.fillMaxWidth().padding(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(filtered, key = { it.id }) { snapshot ->
                    HistoryRow(
                        snapshot = snapshot,
                        candidate = candidateByRequest[snapshot.id],
                        usage = usageByRequest[snapshot.id],
                        onOpen = { selected = snapshot },
                    )
                }
            }
        }
    }

    selected?.let { snapshot ->
        SnapshotDialog(
            snapshot = snapshot,
            candidate = candidateByRequest[snapshot.id],
            usage = usageByRequest[snapshot.id],
            acceptance = candidateByRequest[snapshot.id]?.let { candidate -> acceptances.firstOrNull { it.candidateId == candidate.id } },
            onUndo = viewModel::undoAcceptance,
            onDismiss = { selected = null },
        )
    }
    cleanupPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { cleanupPreview = null },
            title = { Text("确认批量清理？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("这次筛选找到 ${preview.matchedRequests} 条记录，其中 ${preview.deletableRequests} 条可以安全清理；另外 ${preview.blockedRequests} 条仍与正文或版本相关，会继续保留。")
                    Text("同时会移除 ${preview.candidateCount} 个未采用的候选，以及 ${preview.usageCount} 条对应的用量记录。", style = MaterialTheme.typography.bodyMedium)
                    Text(preview.traceabilityNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = preview.deletableRequests > 0,
                    onClick = {
                        val olderThan = ageDays?.let { System.currentTimeMillis() - it * 86_400_000L }
                        viewModel.cleanupHistory(filter, olderThan)
                        cleanupPreview = null
                    },
                ) { Text("清理可安全移除的记录") }
            },
            dismissButton = { TextButton(onClick = { cleanupPreview = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun HistoryRow(
    snapshot: RequestSnapshotEntity,
    candidate: CandidateEntity?,
    usage: UsageRecordEntity?,
    onOpen: () -> Unit,
) {
    Surface(onClick = onOpen, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(taskLabel(snapshot.taskType), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    StatusPill(statusLabel(snapshot.responseStatus), statusTone(snapshot.responseStatus))
                    if (candidate != null) Text("候选 ${"%02d".format(candidate.rollNumber)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text("${formatTime(snapshot.requestedAt)} · ${snapshot.providerId} / ${snapshot.modelId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    candidate?.editedText?.ifBlank { null } ?: snapshot.errorJson ?: "已保存发送前的完整记录",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(usage?.let { "${it.totalTokens} Token" } ?: if (snapshot.usageMissing) "未返回用量" else "暂无用量记录", style = MaterialTheme.typography.labelMedium)
                usage?.let {
                    Text(if (it.staleAgainstCurrentContent) "内容后来有过变化" else "模型返回的实际用量", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SnapshotDialog(
    snapshot: RequestSnapshotEntity,
    candidate: CandidateEntity?,
    usage: UsageRecordEntity?,
    acceptance: AcceptanceRecordEntity?,
    onUndo: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember(snapshot.id) { mutableStateOf("context") }
    val context = LocalContext.current
    val visibleSnapshotText = when (page) {
        "messages" -> snapshot.finalMessagesJson
        "request" -> snapshot.redactedRequestBody
        "candidate" -> buildString {
            append("模型原稿：\n")
            append(candidate?.rawOutput.orEmpty())
            if (candidate?.editedText != candidate?.rawOutput) {
                append("\n\n作者修改稿：\n")
                append(candidate?.editedText.orEmpty())
            }
            candidate?.reasoningContent?.takeIf { it.isNotBlank() }?.let {
                append("\n\n思考过程（不会进入正文或总结）：\n")
                append(it)
            }
        }
        else -> snapshot.logicalContextJson
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("${taskLabel(snapshot.taskType)} · ${formatTime(snapshot.requestedAt)}")
                Text("${snapshot.providerId} / ${snapshot.modelId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = page == "context", onClick = { page = "context" }, label = { Text("故事记忆") }) }
                    item { FilterChip(selected = page == "messages", onClick = { page = "messages" }, label = { Text("发送内容") }) }
                    item { FilterChip(selected = page == "request", onClick = { page = "request" }, label = { Text("完整请求") }) }
                    if (candidate != null) {
                        item { FilterChip(selected = page == "candidate", onClick = { page = "candidate" }, label = { Text("候选") }) }
                    }
                }
                SelectionContainer {
                    Surface(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        LazyColumn(Modifier.padding(12.dp)) {
                            item {
                                Text(
                                    visibleSnapshotText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                )
                            }
                        }
                    }
                }
                candidate?.let {
                    HorizontalDivider()
                    Text("候选 ${"%02d".format(it.rollNumber)} · ${if (it.complete) "生成完成" else "中途停下"}", style = MaterialTheme.typography.labelLarge)
                    Text("模型原稿和你的修改稿都会留在“候选”页，可以分别查看或复制。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                usage?.let {
                    Text("本次用量：输入 ${it.inputTokens} · 输出 ${it.outputTokens} · 总计 ${it.totalTokens} Token", style = MaterialTheme.typography.labelLarge)
                }
                acceptance?.takeIf { it.undoneAt == null }?.let {
                    OutlinedButton(onClick = { onUndo(it.id); onDismiss() }) {
                        Icon(Icons.Outlined.Restore, contentDescription = null)
                        Text("尝试撤销这次正文追加", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        dismissButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("绀海之约创作记录", visibleSnapshotText))
                },
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Text("复制当前内容", modifier = Modifier.padding(start = 6.dp))
            }
        },
    )
}

private fun taskLabel(task: TaskType): String = when (task) {
    TaskType.BODY -> "正文"
    TaskType.SUMMARY -> "总结"
    TaskType.ASSISTANT -> "灵感对谈"
    TaskType.MODEL_VALIDATION -> "模型检查"
}

private fun statusLabel(status: ResponseStatus) = when (status) {
    ResponseStatus.PREPARED -> "准备发送"
    ResponseStatus.STREAMING -> "创作中"
    ResponseStatus.SUCCEEDED -> "已完成"
    ResponseStatus.PARTIAL -> "中途停下"
    ResponseStatus.FAILED -> "未完成"
}

private fun statusTone(status: ResponseStatus) = when (status) {
    ResponseStatus.SUCCEEDED -> StatusTone.SAVED
    ResponseStatus.FAILED -> StatusTone.ERROR
    ResponseStatus.PARTIAL, ResponseStatus.STREAMING -> StatusTone.WARNING
    ResponseStatus.PREPARED -> StatusTone.NEUTRAL
}

private fun formatTime(value: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(value))
