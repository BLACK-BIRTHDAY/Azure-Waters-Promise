package com.ainovel.local

import android.content.Context
import com.ainovel.local.data.local.NovelDatabase
import com.ainovel.local.data.cover.CoverStorage
import com.ainovel.local.data.security.SecureSecretStore
import com.ainovel.local.data.backup.BackupRepository
import com.ainovel.local.domain.ContextAssembler
import com.ainovel.local.domain.GenerationRepository
import com.ainovel.local.domain.NovelRepository
import kotlinx.serialization.json.Json

class AppContainer(context: Context) {
    val coverStorage = CoverStorage(context.applicationContext)
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = false
    }
    val database = NovelDatabase.get(context)
    val secretStore = SecureSecretStore(context)
    val contextAssembler = ContextAssembler(database.novelDao(), json)
    val novelRepository = NovelRepository(
        database,
        database.novelDao(),
        database.generationDao(),
        database.modelDao(),
        json,
    )
    val generationRepository = GenerationRepository(
        database,
        database.novelDao(),
        database.generationDao(),
        database.modelDao(),
        secretStore,
        contextAssembler,
        json,
    )
    val backupRepository = BackupRepository(
        context,
        database,
        database.backupDao(),
        secretStore,
        json,
    )
}
