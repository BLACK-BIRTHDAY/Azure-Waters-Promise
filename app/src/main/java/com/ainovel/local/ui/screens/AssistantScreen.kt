package com.ainovel.local.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PsychologyAlt
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.ainovel.local.ui.localization.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainovel.local.data.remote.ProviderMessage
import com.ainovel.local.ui.AppViewModel
import com.ainovel.local.ui.components.PrimaryAction
import com.ainovel.local.ui.components.SecondaryAction

@Composable
fun AssistantScreen(viewModel: AppViewModel) {
    val messages by viewModel.assistantMessages.collectAsStateWithLifecycle()
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val busy by viewModel.isBusy.collectAsStateWithLifecycle()
    val live by viewModel.assistantLive.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var showReset by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val transcriptItemCount = messages.size + if (live.active) 1 else 0
    LaunchedEffect(transcriptItemCount) {
        if (transcriptItemCount > 0) listState.animateScrollToItem(transcriptItemCount - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.PsychologyAlt, contentDescription = null, modifier = Modifier.padding(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("灵感对谈", style = MaterialTheme.typography.titleLarge)
                Text(
                    "这段对话会一直陪着当前故事，也不会擅自改动正文。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SecondaryAction("清空对话", onClick = { showReset = true }, icon = Icons.Outlined.DeleteSweep, enabled = messages.isNotEmpty() && !busy)
        }
        HorizontalDivider()
        if (messages.isEmpty() && !live.active) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                Text("从一个困住你的问题开始", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "例如：“主角此时为什么会相信对手？请区分已知事实、推断和新建议。”",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
                itemsIndexed(messages) { index, message ->
                    TranscriptEntry(index, message)
                }
                if (live.active) {
                    item(key = "assistant-live-response") {
                        LiveAssistantEntry(live.content, live.reasoning)
                    }
                }
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("想聊些什么？") },
                    placeholder = { Text("一起看看人物动机、冲突或故事的下一步") },
                    minLines = 2,
                    maxLines = 5,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        workspace.novel?.assistantModelId ?: workspace.novel?.bodyModelId ?: "尚未配置模型",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (busy) {
                        SecondaryAction("停止生成", onClick = viewModel::stopGeneration, icon = Icons.Outlined.StopCircle)
                    } else {
                        PrimaryAction(
                            text = "发送前检查",
                            onClick = { viewModel.inspectAssistantRequest(draft) },
                            enabled = draft.isNotBlank(),
                            icon = Icons.AutoMirrored.Outlined.FactCheck,
                        )
                    }
                }
            }
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("要告别这段对话吗？") },
            text = { Text("清空后，当前对话与相关记录都会永久消失，无法找回；正文不会受到影响。") },
            confirmButton = {
                TextButton(onClick = { viewModel.resetAssistant(); showReset = false }) { Text("清空对话") }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun LiveAssistantEntry(content: String, reasoning: String) {
    var reasoningExpanded by rememberSaveable { mutableStateOf(true) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("正在寻找线索…", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        if (reasoning.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth().animateContentSize(tween(220)),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { reasoningExpanded = !reasoningExpanded }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("正在梳理思路", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Icon(if (reasoningExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                    }
                    AnimatedVisibility(visible = reasoningExpanded, enter = fadeIn(tween(180)), exit = fadeOut(tween(120))) {
                        Text(
                            reasoning,
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (reasoning.isBlank() && content.isBlank()) {
            Text(
                "灵感正在路上…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (content.isNotBlank()) Text(content, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TranscriptEntry(index: Int, message: ProviderMessage) {
    var reasoningExpanded by rememberSaveable(index, message.content) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%02d".format(index + 1),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (message.role == "assistant") "灵感回应" else "你的问题",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (message.role == "assistant") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        if (message.role == "assistant") {
            val reasoning = message.reasoningContent?.takeIf { it.isNotBlank() }
            if (reasoning != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().animateContentSize(tween(220)),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClickLabel = if (reasoningExpanded) "折叠思考过程" else "展开思考过程") {
                                    reasoningExpanded = !reasoningExpanded
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("思考过程", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                Text("只在这里陪你梳理，不会带进下一轮对话。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(
                                if (reasoningExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = null,
                            )
                        }
                        AnimatedVisibility(
                            visible = reasoningExpanded,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(120)),
                        ) {
                            Text(
                                reasoning,
                                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else if (message.reasoningRequested != null) {
                Text(
                    if (message.reasoningRequested == true) "这一次没有留下可展示的思考过程。" else "这一次没有开启思考。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(message.content, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}
