package com.ainovel.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ainovel.local.data.backup.BackupRepository
import com.ainovel.local.data.local.NovelDatabase
import com.ainovel.local.data.model.ContextEntryEntity
import com.ainovel.local.data.model.EntryScope
import com.ainovel.local.data.model.EntryType
import com.ainovel.local.data.model.ProviderConfigEntity
import com.ainovel.local.data.model.TaskType
import com.ainovel.local.data.remote.GenerationSettings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupAndAssistantTest {
    @Test
    fun assistantPersistsSingleSession_andResetDeletesOnlyItsHistory() = runBlocking {
        RepositoryFixture(listOf(FakeResponse("第一轮回复"), FakeResponse("第二轮回复"))).use { fixture ->
            val novel = fixture.configuredNovel()
            fixture.generation.generateAssistantReply(novel.id, "第一轮问题", GenerationSettings())
            fixture.generation.generateAssistantReply(novel.id, "第二轮问题", GenerationSettings())
            val session = fixture.database.novelDao().assistantSession(novel.id)!!
            assertTrue(session.messagesJson.contains("第一轮问题"))
            assertTrue(session.messagesJson.contains("第二轮回复"))
            assertEquals(2, fixture.database.generationDao().allRequestSnapshots().count { it.taskType == TaskType.ASSISTANT })
            assertEquals(2, fixture.database.generationDao().allUsage().count { it.taskType == TaskType.ASSISTANT })

            fixture.novels.resetAssistantSession(novel.id)
            assertEquals("[]", fixture.database.novelDao().assistantSession(novel.id)?.messagesJson)
            assertTrue(fixture.database.generationDao().allRequestSnapshots().none { it.taskType == TaskType.ASSISTANT })
            assertTrue(fixture.database.generationDao().allUsage().none { it.taskType == TaskType.ASSISTANT })
        }
    }

    @Test
    fun assistantReasoning_isPersistedForDisplayButExcludedFromNextRequest() = runBlocking {
        RepositoryFixture(
            listOf(
                FakeResponse("第一轮回复", reasoning = "第一轮内部推理"),
                FakeResponse("第二轮回复", reasoning = "第二轮内部推理"),
            ),
        ).use { fixture ->
            val novel = fixture.configuredNovel()
            val settings = GenerationSettings(thinkingEnabled = true, streamThinking = true)

            fixture.generation.generateAssistantReply(novel.id, "第一轮问题", settings)
            val firstSession = fixture.database.novelDao().assistantSession(novel.id)!!
            assertTrue(firstSession.messagesJson.contains("第一轮内部推理"))

            fixture.generation.generateAssistantReply(novel.id, "第二轮问题", settings)
            val secondRequest = fixture.adapter.streamedBodies.last()
            assertTrue(secondRequest.contains("第一轮回复"))
            assertTrue(!secondRequest.contains("第一轮内部推理"))
            val secondSession = fixture.database.novelDao().assistantSession(novel.id)!!
            assertTrue(secondSession.messagesJson.contains("第二轮内部推理"))
        }
    }

    @Test
    fun encryptedBackup_roundTripsAllStateAndSecrets() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceDb = Room.inMemoryDatabaseBuilder(context, NovelDatabase::class.java).allowMainThreadQueries().build()
        val targetDb = Room.inMemoryDatabaseBuilder(context, NovelDatabase::class.java).allowMainThreadQueries().build()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
        val sourceSecrets = MemorySecretStore()
        val targetSecrets = MemorySecretStore()
        val sourceRepo = com.ainovel.local.domain.NovelRepository(sourceDb, sourceDb.novelDao(), sourceDb.generationDao(), sourceDb.modelDao(), json)
        val sourceBackup = BackupRepository(context, sourceDb, sourceDb.backupDao(), sourceSecrets, json)
        val targetBackup = BackupRepository(context, targetDb, targetDb.backupDao(), targetSecrets, json)
        try {
            val novel = sourceRepo.createNovel("备份小说")
            val coverBytes = "encrypted-cover-fixture".toByteArray()
            val coverName = "${novel.id}.webp"
            val coverFile = File(context.filesDir, "novel-covers/$coverName").apply {
                parentFile?.mkdirs()
                writeBytes(coverBytes)
            }
            sourceRepo.updateNovelCover(novel.id, coverName)
            val chapter = sourceDb.novelDao().allChapters(novel.id).single()
            sourceRepo.saveChapterContent(chapter.id, "不能丢失的正文")
            sourceRepo.upsertContextEntry(
                ContextEntryEntity(
                    type = EntryType.WORLDBOOK,
                    scope = EntryScope.NOVEL,
                    novelId = novel.id,
                    name = "本机管理名",
                    content = "世界设定内容",
                    sortOrder = 1,
                ),
            )
            sourceDb.novelDao().upsertProvider(
                ProviderConfigEntity("deepseek", "DeepSeek", "https://api.deepseek.com", "provider:deepseek:key", validationState = "VERIFIED"),
            )
            sourceSecrets.put("provider:deepseek:key", "backup-secret-value")

            val output = ByteArrayOutputStream()
            sourceBackup.createBackup(output, "strong-password".toCharArray())
            val bytes = output.toByteArray()
            val rawEnvelope = bytes.toString(Charsets.UTF_8)
            assertFalse(rawEnvelope.contains("不能丢失的正文"))
            assertFalse(rawEnvelope.contains("backup-secret-value"))
            assertTrue(rawEnvelope.contains("AINOVELBACKUP"))
            assertTrue(coverFile.delete())

            val preview = targetBackup.preview(ByteArrayInputStream(bytes), "strong-password".toCharArray())
            assertEquals(1, preview.novelCount)
            assertEquals(1, preview.chapterCount)
            targetBackup.restore(ByteArrayInputStream(bytes), "strong-password".toCharArray())
            assertEquals(sourceDb.backupDao().novels(), targetDb.backupDao().novels())
            assertEquals(sourceDb.backupDao().chapters(), targetDb.backupDao().chapters())
            assertEquals(sourceDb.backupDao().contextEntries(), targetDb.backupDao().contextEntries())
            assertEquals("backup-secret-value", targetSecrets.get("provider:deepseek:key"))
            assertEquals(coverName, targetDb.backupDao().novels().single().coverFileName)
            assertTrue(coverFile.readBytes().contentEquals(coverBytes))
            assertTrue(targetBackup.rollbackFile().exists())
        } finally {
            sourceDb.close()
            targetDb.close()
            sourceBackup.rollbackFile().delete()
        }
    }

    @Test
    fun wrongPassword_doesNotModifyTargetDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceDb = Room.inMemoryDatabaseBuilder(context, NovelDatabase::class.java).allowMainThreadQueries().build()
        val targetDb = Room.inMemoryDatabaseBuilder(context, NovelDatabase::class.java).allowMainThreadQueries().build()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
        val sourceBackup = BackupRepository(context, sourceDb, sourceDb.backupDao(), MemorySecretStore(), json)
        val targetBackup = BackupRepository(context, targetDb, targetDb.backupDao(), MemorySecretStore(), json)
        val sourceRepo = com.ainovel.local.domain.NovelRepository(sourceDb, sourceDb.novelDao(), sourceDb.generationDao(), sourceDb.modelDao(), json)
        val targetRepo = com.ainovel.local.domain.NovelRepository(targetDb, targetDb.novelDao(), targetDb.generationDao(), targetDb.modelDao(), json)
        try {
            sourceRepo.createNovel("源数据")
            val targetNovel = targetRepo.createNovel("目标不应变")
            val before = targetDb.backupDao().novels()
            val output = ByteArrayOutputStream()
            sourceBackup.createBackup(output, "correct-password".toCharArray())
            val result = runCatching {
                targetBackup.restore(ByteArrayInputStream(output.toByteArray()), "wrong-password!".toCharArray())
            }
            assertTrue(result.isFailure)
            assertEquals(before, targetDb.backupDao().novels())
            assertEquals(targetNovel.id, targetDb.backupDao().novels().single().id)
        } finally {
            sourceDb.close()
            targetDb.close()
        }
    }
}
