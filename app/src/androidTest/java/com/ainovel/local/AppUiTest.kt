package com.ainovel.local

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ainovel.local.data.model.CandidateEntity
import com.ainovel.local.data.model.CandidateGroupEntity
import com.ainovel.local.data.model.CandidateGroupStatus
import com.ainovel.local.data.model.Ids
import com.ainovel.local.data.model.ReasoningKind
import com.ainovel.local.data.model.RequestSnapshotEntity
import com.ainovel.local.data.model.ResponseStatus
import com.ainovel.local.data.model.TaskType
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearAppState() {
        val application = compose.activity.application as LocalNovelApplication
        runBlocking { application.container.database.clearAllTables() }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    @Test
    fun createNovel_editBody_andNavigateMainDestinations() {
        compose.onNodeWithText("开始一个故事").performScrollTo().performClick()
        compose.waitUntil(3_000) { compose.onAllNodes(hasTestTag("novel-name-input")).fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("novel-name-input").performTextInput("界面验收小说")
        compose.onNodeWithText("放上书架", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("绀海之约")).fetchSemanticsNodes().isEmpty()
        }
        waitUntilDisplayed { compose.onNodeWithTag("manuscript-editor").assertIsDisplayed() }
        compose.onNodeWithTag("manuscript-editor").performClick().performTextInput("第一段本地正文。")
        compose.onNodeWithContentDescription("展开章节信息").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { activity ->
            androidx.lifecycle.ViewModelProvider(activity)[com.ainovel.local.ui.AppViewModel::class.java]
                .openDestination(com.ainovel.local.ui.AppDestination.CONTEXT)
        }
        compose.onAllNodes(hasText("故事记忆"))[0].assertIsDisplayed()
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("灵感")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("灵感", useUnmergedTree = true).performClick()
        compose.onAllNodes(hasText("灵感对谈"))[0].assertIsDisplayed()
        compose.onNodeWithText("足迹", useUnmergedTree = true).performClick()
        compose.onAllNodes(hasText("创作足迹"))[0].assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("界面验收小说", substring = true).assertIsDisplayed()
    }

    @Test
    fun backReturnsToLibrary_andNovelCanBeSelectedAgain() {
        compose.onNodeWithText("开始一个故事").performScrollTo().performClick()
        compose.onNodeWithTag("novel-name-input").performTextInput("返回路径小说")
        compose.onNodeWithText("放上书架", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("返回作品列表").performClick()
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("我的书架")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("我的书架").assertIsDisplayed()
        compose.onNode(hasText("返回路径小说") and hasClickAction()).performClick()
        waitUntilDisplayed { compose.onNodeWithTag("manuscript-editor").assertIsDisplayed() }
    }

    @Test
    fun candidateCanReturnToPrompt_withoutLosingCandidateOrReasoning() {
        compose.onNodeWithText("开始一个故事").performScrollTo().performClick()
        compose.onNodeWithTag("novel-name-input").performTextInput("候选返回小说")
        compose.onNodeWithText("放上书架", useUnmergedTree = true).performClick()
        waitUntilDisplayed { compose.onNodeWithTag("manuscript-editor").assertIsDisplayed() }

        val application = compose.activity.application as LocalNovelApplication
        runBlocking {
            val database = application.container.database
            val novel = database.novelDao().allNovels().single()
            val chapter = database.novelDao().allChapters(novel.id).single()
            val groupId = Ids.newId()
            val requestId = Ids.newId()
            database.generationDao().insertRequestSnapshot(
                RequestSnapshotEntity(
                    id = requestId,
                    taskType = TaskType.BODY,
                    novelId = novel.id,
                    targetEntityId = chapter.id,
                    candidateGroupId = groupId,
                    providerId = "ui-test",
                    modelId = "reasoning-test",
                    logicalContextJson = "{}",
                    finalMessagesJson = "[]",
                    generationParametersJson = "{}",
                    redactedRequestBody = "{}",
                    responseStatus = ResponseStatus.SUCCEEDED,
                ),
            )
            database.generationDao().insertCandidateGroup(
                CandidateGroupEntity(
                    id = groupId,
                    taskType = TaskType.BODY,
                    novelId = novel.id,
                    targetChapterId = chapter.id,
                    oneTimeCreativeRequirement = "测试候选返回",
                    frozenRequestSnapshotId = requestId,
                    status = CandidateGroupStatus.PENDING,
                ),
            )
            database.generationDao().insertCandidate(
                CandidateEntity(
                    candidateGroupId = groupId,
                    rollNumber = 1,
                    rawOutput = "候选正文",
                    editedText = "候选正文",
                    reasoningContent = "用于验收的思考过程",
                    reasoningKind = ReasoningKind.REASONING_CONTENT,
                    complete = true,
                    requestSnapshotId = requestId,
                ),
            )
            database.generationDao().insertCandidate(
                CandidateEntity(
                    candidateGroupId = groupId,
                    rollNumber = 2,
                    rawOutput = "第二个候选正文",
                    editedText = "第二个候选正文",
                    complete = true,
                    requestSnapshotId = requestId,
                ),
            )
        }

        compose.waitUntil(3_000) { compose.onAllNodes(hasText("正文候选")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("返回创作要求").performClick()
        compose.onNodeWithText("这一次", substring = true).assertIsDisplayed()
        compose.onNodeWithText("2 个候选待选择").assertIsDisplayed().performClick()
        compose.onNodeWithText("正文候选").assertIsDisplayed()
        compose.onNodeWithContentDescription("下一个候选").performClick()
        compose.onNodeWithText("第二个候选正文").assertIsDisplayed()
        compose.onNodeWithContentDescription("上一个候选").performClick()
        compose.onNodeWithText("思考过程").performClick()
        compose.onNodeWithText("用于验收的思考过程").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun deleteCurrentCandidate_thenDeleteAllRemainingCandidates() {
        compose.onNodeWithText("开始一个故事").performScrollTo().performClick()
        compose.onNodeWithTag("novel-name-input").performTextInput("候选删除小说")
        compose.onNodeWithText("放上书架", useUnmergedTree = true).performClick()
        waitUntilDisplayed { compose.onNodeWithTag("manuscript-editor").assertIsDisplayed() }

        val application = compose.activity.application as LocalNovelApplication
        runBlocking {
            val database = application.container.database
            val novel = database.novelDao().allNovels().single()
            val chapter = database.novelDao().allChapters(novel.id).single()
            val groupId = Ids.newId()
            val requestId = Ids.newId()
            database.generationDao().insertRequestSnapshot(
                RequestSnapshotEntity(
                    id = requestId,
                    taskType = TaskType.BODY,
                    novelId = novel.id,
                    targetEntityId = chapter.id,
                    candidateGroupId = groupId,
                    providerId = "ui-test",
                    modelId = "reasoning-test",
                    logicalContextJson = "{}",
                    finalMessagesJson = "[]",
                    generationParametersJson = "{}",
                    redactedRequestBody = "{}",
                    responseStatus = ResponseStatus.PARTIAL,
                ),
            )
            database.generationDao().insertCandidateGroup(
                CandidateGroupEntity(
                    id = groupId,
                    taskType = TaskType.BODY,
                    novelId = novel.id,
                    targetChapterId = chapter.id,
                    frozenRequestSnapshotId = requestId,
                    status = CandidateGroupStatus.PENDING,
                ),
            )
            database.generationDao().insertCandidate(
                CandidateEntity(
                    candidateGroupId = groupId,
                    rollNumber = 1,
                    reasoningContent = "只有思维链，也必须能删",
                    reasoningKind = ReasoningKind.REASONING_CONTENT,
                    requestSnapshotId = requestId,
                ),
            )
            database.generationDao().insertCandidate(
                CandidateEntity(
                    candidateGroupId = groupId,
                    rollNumber = 2,
                    rawOutput = "另一个正文候选",
                    editedText = "另一个正文候选",
                    requestSnapshotId = requestId,
                ),
            )
        }

        compose.waitUntil(3_000) { compose.onAllNodes(hasText("正文候选")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("删除当前候选").performClick()
        compose.onNodeWithText("删除当前候选？").assertIsDisplayed()
        compose.onNodeWithText("删除", useUnmergedTree = true).performClick()
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("另一个正文候选")).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithContentDescription("返回创作要求").performClick()
        compose.onNodeWithContentDescription("删除全部候选").performClick()
        compose.onNodeWithText("删除全部候选？").assertIsDisplayed()
        compose.onNodeWithText("全部删除", useUnmergedTree = true).performClick()
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("1 个候选待选择")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("creative-requirement-input").assertIsDisplayed()
    }

    @Test
    fun settings_connectionAndBackupActionsRemainReachable() {
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("模型供应商").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("DeepSeek", useUnmergedTree = true).performClick()
        compose.onNodeWithText("OpenAI").assertIsDisplayed()
        compose.onNodeWithText("验证并安全保存").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("完整加密备份"))
        compose.onNodeWithText("完整加密备份").assertIsDisplayed()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("创建备份"))
        compose.onNodeWithText("创建备份").assertIsDisplayed()
    }

    @Test
    fun manuscriptRemainsVisible_whenSoftwareKeyboardOpens() {
        compose.onNodeWithText("开始一个故事").performScrollTo().performClick()
        compose.onNodeWithTag("novel-name-input").performTextInput("键盘验收小说")
        compose.onNodeWithText("放上书架", useUnmergedTree = true).performClick()
        waitUntilDisplayed { compose.onNodeWithTag("manuscript-editor").assertIsDisplayed() }

        compose.onNodeWithTag("manuscript-editor").performClick().performTextInput("键盘弹出后仍应看见这段正文")

        compose.onNodeWithTag("manuscript-editor").assertIsDisplayed()
        compose.onNodeWithText("键盘弹出后仍应看见这段正文", substring = true).assertIsDisplayed()
    }

    @Test
    fun requirementInputAndManuscriptRemainVisible_whenSoftwareKeyboardOpens() {
        compose.onNodeWithText("开始一个故事").performScrollTo().performClick()
        compose.onNodeWithTag("novel-name-input").performTextInput("创作要求键盘验收")
        compose.onNodeWithText("放上书架", useUnmergedTree = true).performClick()
        waitUntilDisplayed { compose.onNodeWithTag("manuscript-editor").assertIsDisplayed() }
        compose.onNodeWithTag("manuscript-editor").performClick().performTextInput("键盘上方必须保留的正文")

        compose.onNodeWithTag("creative-requirement-input").performClick().performTextInput("继续推进剧情")

        compose.onNodeWithTag("creative-requirement-input").assertIsDisplayed()
        compose.onNodeWithTag("manuscript-editor").assertIsDisplayed()
        compose.onNodeWithText("键盘上方必须保留的正文", substring = true).assertIsDisplayed()
    }

    private fun waitUntilDisplayed(assertion: () -> Unit) {
        compose.waitUntil(5_000) {
            runCatching {
                assertion()
                true
            }.getOrDefault(false)
        }
    }
}
