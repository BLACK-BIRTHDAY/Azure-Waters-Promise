package com.ainovel.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ainovel.local.data.local.NovelDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val databaseName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NovelDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesNovelAndAddsApplicationSettings() {
        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                """
                INSERT INTO novels(
                    id, name, bodyProviderId, bodyModelId, summaryProviderId, summaryModelId,
                    assistantProviderId, assistantModelId, summaryInstruction, assistantInstruction,
                    createdAt, updatedAt
                ) VALUES(
                    'old-novel', '旧版本小说', NULL, NULL, NULL, NULL, NULL, NULL,
                    '旧总结指令', '旧助手指令', 1, 1
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 2, true).apply {
            query("SELECT name, summaryInstruction FROM novels WHERE id = 'old-novel'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("旧版本小说", cursor.getString(0))
                assertEquals("旧总结指令", cursor.getString(1))
            }
            query("SELECT COUNT(*) FROM app_settings").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrate2To3_preservesNovelAndAddsPersistentGenerationSettings() {
        helper.createDatabase(databaseName, 2).apply {
            execSQL(
                """INSERT INTO novels(
                    id, name, bodyProviderId, bodyModelId, summaryProviderId, summaryModelId,
                    assistantProviderId, assistantModelId, summaryInstruction, assistantInstruction,
                    createdAt, updatedAt
                ) VALUES('v2-novel', '参数迁移', NULL, NULL, NULL, NULL, NULL, NULL,
                    '总结', '助手', 1, 1)""",
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 3, true).apply {
            query(
                "SELECT bodyGenerationSettingsJson, summaryGenerationSettingsJson, assistantGenerationSettingsJson FROM novels WHERE id = 'v2-novel'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(true, cursor.isNull(0))
                assertEquals(true, cursor.isNull(1))
                assertEquals(true, cursor.isNull(2))
            }
            close()
        }
    }

    @Test
    fun migrate3To4_preservesNovelAndAddsOptionalCover() {
        helper.createDatabase(databaseName, 3).apply {
            execSQL(
                """INSERT INTO novels(
                    id, name, bodyProviderId, bodyModelId, summaryProviderId, summaryModelId,
                    assistantProviderId, assistantModelId, summaryInstruction, assistantInstruction,
                    bodyGenerationSettingsJson, summaryGenerationSettingsJson, assistantGenerationSettingsJson,
                    createdAt, updatedAt
                ) VALUES('v3-novel', '封面迁移', NULL, NULL, NULL, NULL, NULL, NULL,
                    '总结', '助手', NULL, NULL, NULL, 1, 1)""",
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 4, true).apply {
            query("SELECT name, coverFileName FROM novels WHERE id = 'v3-novel'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("封面迁移", cursor.getString(0))
                assertEquals(true, cursor.isNull(1))
            }
            close()
        }
    }
}
