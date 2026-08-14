package com.ainovel.local.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ainovel.local.data.model.TaskType
import com.ainovel.local.data.remote.PreparedRequest
import com.ainovel.local.ui.components.PrimaryAction
import com.ainovel.local.ui.components.SecondaryAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreparedRequestSheet(
    prepared: PreparedRequest,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }
    val pages = listOf("故事记忆" to prepared.logicalContext.size, "发送内容" to prepared.messages.size, "完整请求" to 1)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxHeight(0.92f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("发送前，再看一眼", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "内容还没有发出。确认无误后，再把它交给模型。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null)
                    Column {
                        Text("${taskLabel(prepared.taskType)} · ${prepared.providerId} / ${prepared.modelId}", fontWeight = FontWeight.SemiBold)
                        Text("密钥和只用于本地辨认的名称，都不会随内容发送。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.forEachIndexed { index, item ->
                    FilterChip(
                        selected = page == index,
                        onClick = { page = index },
                        label = { Text(if (index < 2) "${item.first} ${item.second}" else item.first) },
                    )
                }
            }
            when (page) {
                0 -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(prepared.logicalContext, key = { _, item -> "${item.order}:${item.sourceId}" }) { index, item ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Text("${"%02d".format(index + 1)}  ${sourceLabel(item.sourceType)}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                    item.localDisplayName?.let {
                                        Text("仅本地可见：$it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                HorizontalDivider()
                                SelectionContainer {
                                    Text(item.transmittedContent.ifBlank { "（这里还没有内容）" }, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                1 -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(prepared.messages) { index, message ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${"%02d".format(index + 1)}  ${roleLabel(message.role)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            SelectionContainer { Text(message.content, style = MaterialTheme.typography.bodyMedium) }
                            HorizontalDivider(Modifier.padding(top = 6.dp))
                        }
                    }
                }

                else -> SelectionContainer {
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        LazyColumn(Modifier.padding(14.dp)) {
                            item {
                                Text(
                                    prepared.redactedRequestBody,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryAction("再改一改", onClick = onDismiss, modifier = Modifier.weight(1f))
                PrimaryAction("确认发送", onClick = onSend, modifier = Modifier.weight(1f), icon = Icons.AutoMirrored.Outlined.Send)
            }
        }
    }
}

private fun taskLabel(task: TaskType): String = when (task) {
    TaskType.BODY -> "正文候选"
    TaskType.SUMMARY -> "总结候选"
    TaskType.ASSISTANT -> "创作助手"
    TaskType.MODEL_VALIDATION -> "模型验证"
}

private fun sourceLabel(sourceType: String): String = when (sourceType) {
    "PRESET" -> "预设"
    "WORLDBOOK" -> "世界书"
    "CHAPTER" -> "已接受正文"
    "SUMMARY" -> "已接受总结"
    "ONE_TIME_CREATIVE_REQUIREMENT" -> "本次创作要求"
    "SUMMARY_INSTRUCTION" -> "总结指令"
    "SUMMARY_DENSITY" -> "总结密度"
    "ASSISTANT_INSTRUCTION" -> "助手指令"
    "ASSISTANT_MESSAGE" -> "新消息"
    else -> sourceType
}

private fun roleLabel(role: String): String = when (role) {
    "system" -> "系统消息"
    "assistant" -> "模型回复"
    else -> "用户消息"
}
