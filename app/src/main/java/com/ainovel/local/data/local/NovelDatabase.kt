package com.ainovel.local.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.AutoMigration
import com.ainovel.local.data.model.AcceptanceRecordEntity
import com.ainovel.local.data.model.AppSettingsEntity
import com.ainovel.local.data.model.AssistantSessionEntity
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
import com.ainovel.local.data.backup.BackupDao

@Database(
    entities = [
        NovelEntity::class,
        ChapterEntity::class,
        SummaryEntity::class,
        SummarySourceEntity::class,
        ContextEntryEntity::class,
        CandidateGroupEntity::class,
        CandidateEntity::class,
        RequestSnapshotEntity::class,
        UsageRecordEntity::class,
        AssistantSessionEntity::class,
        ChapterRevisionEntity::class,
        AcceptanceRecordEntity::class,
        ProviderConfigEntity::class,
        ModelSettingEntity::class,
        AppSettingsEntity::class,
    ],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class NovelDatabase : RoomDatabase() {
    abstract fun novelDao(): NovelDao
    abstract fun generationDao(): GenerationDao
    abstract fun modelDao(): ModelDao
    abstract fun backupDao(): BackupDao

    companion object {
        @Volatile private var instance: NovelDatabase? = null

        fun get(context: Context): NovelDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NovelDatabase::class.java,
                "local-ai-novel.db",
            ).build().also { instance = it }
        }
    }
}
