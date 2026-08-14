package com.ainovel.local.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.ainovel.local.data.local.NovelDatabase
import com.ainovel.local.data.security.SecretStore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupRepository(
    private val context: Context,
    private val database: NovelDatabase,
    private val backupDao: BackupDao,
    private val secretStore: SecretStore,
    private val json: Json,
) {
    fun rollbackFile(): File = File(context.filesDir, ROLLBACK_FILE_NAME)

    suspend fun createBackup(output: OutputStream, password: CharArray) = withContext(Dispatchers.IO) {
        try {
            requirePassword(password)
            val payload = database.withTransaction {
                val providers = backupDao.providers()
                val novels = backupDao.novels()
                BackupPayload(
                    appSettings = backupDao.appSettings(),
                    novels = novels,
                    chapters = backupDao.chapters(),
                    summaries = backupDao.summaries(),
                    summarySources = backupDao.summarySources(),
                    contextEntries = backupDao.contextEntries(),
                    providerConfigs = providers,
                    providerSecrets = providers.mapNotNull { provider ->
                        secretStore.get(provider.secretReference)?.let { provider.providerId to it }
                    }.toMap(),
                    modelSettings = backupDao.models(),
                    candidateGroups = backupDao.groups(),
                    candidates = backupDao.candidates(),
                    requestSnapshots = backupDao.snapshots(),
                    usageRecords = backupDao.usage(),
                    assistantSessions = backupDao.sessions(),
                    chapterRevisions = backupDao.revisions(),
                    acceptanceRecords = backupDao.acceptances(),
                    coverImages = novels.mapNotNull { novel ->
                        novel.coverFileName?.let { fileName ->
                            coverFile(fileName).takeIf(File::isFile)?.readBytes()?.let { bytes ->
                                novel.id to encode(bytes)
                            }
                        }
                    }.toMap(),
                )
            }
            val salt = ByteArray(16).also(SecureRandom()::nextBytes)
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
                updateAAD(AAD)
            }
            val cipherText = cipher.doFinal(json.encodeToString(payload).toByteArray(Charsets.UTF_8))
            val envelope = BackupEnvelope(
                createdAt = System.currentTimeMillis(),
                saltBase64 = encode(salt),
                ivBase64 = encode(iv),
                cipherTextBase64 = encode(cipherText),
                encryptedPayloadSha256 = sha256(cipherText),
            )
            output.bufferedWriter(Charsets.UTF_8).use { it.write(json.encodeToString(envelope)) }
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun preview(input: InputStream, password: CharArray): BackupRestorePreview = withContext(Dispatchers.IO) {
        val (envelope, payload) = decrypt(input, password)
        validatePayload(payload)
        BackupRestorePreview(
            novelCount = payload.novels.size,
            chapterCount = payload.chapters.size,
            summaryCount = payload.summaries.size,
            candidateCount = payload.candidates.size,
            requestCount = payload.requestSnapshots.size,
            createdAt = envelope.createdAt,
        )
    }

    suspend fun restore(input: InputStream, password: CharArray): File = withContext(Dispatchers.IO) {
        val rollbackPassword = password.copyOf()
        try {
            val (_, payload) = decrypt(input, password)
            validatePayload(payload)

            val rollbackFile = rollbackFile()
            FileOutputStream(rollbackFile, false).use { output ->
                createBackup(output, rollbackPassword.copyOf())
            }

            val oldProviders = backupDao.providers()
            val oldSecrets = oldProviders.associate { it.secretReference to secretStore.get(it.secretReference) }
            val targetReferences = payload.providerConfigs.mapTo(mutableSetOf()) { it.secretReference }
            val touchedReferences = oldSecrets.keys + targetReferences
            try {
                payload.providerConfigs.forEach { provider ->
                    val secret = payload.providerSecrets[provider.providerId]
                    if (secret == null) secretStore.delete(provider.secretReference)
                    else secretStore.put(provider.secretReference, secret)
                }
                oldSecrets.keys.filterNot { it in targetReferences }.forEach(secretStore::delete)

                database.withTransaction {
                    clearAll()
                    backupDao.insertAppSettings(payload.appSettings)
                    backupDao.insertNovels(payload.novels)
                    backupDao.insertProviders(payload.providerConfigs)
                    backupDao.insertModels(payload.modelSettings)
                    backupDao.insertChapters(payload.chapters)
                    backupDao.insertContextEntries(payload.contextEntries)
                    backupDao.insertSummaries(payload.summaries)
                    backupDao.insertSummarySources(payload.summarySources)
                    backupDao.insertSnapshots(payload.requestSnapshots)
                    backupDao.insertGroups(payload.candidateGroups)
                    backupDao.insertCandidates(payload.candidates)
                    backupDao.insertUsage(payload.usageRecords)
                    backupDao.insertSessions(payload.assistantSessions)
                    backupDao.insertRevisions(payload.chapterRevisions)
                    backupDao.insertAcceptances(payload.acceptanceRecords)
                }
                restoreCoverImages(payload)
            } catch (error: Throwable) {
                touchedReferences.forEach { reference ->
                    val previous = oldSecrets[reference]
                    if (previous == null) secretStore.delete(reference) else secretStore.put(reference, previous)
                }
                throw error
            }
            rollbackFile
        } finally {
            password.fill('\u0000')
            rollbackPassword.fill('\u0000')
        }
    }

    private fun decrypt(input: InputStream, password: CharArray): Pair<BackupEnvelope, BackupPayload> {
        requirePassword(password)
        val raw = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val envelope = json.decodeFromString<BackupEnvelope>(raw)
        require(envelope.magic == "AINOVELBACKUP") { "不是本软件的备份文件" }
        require(envelope.formatVersion == 1) { "不支持的备份版本：${envelope.formatVersion}" }
        val cipherText = decode(envelope.cipherTextBase64)
        require(sha256(cipherText) == envelope.encryptedPayloadSha256) { "备份完整性校验失败" }
        val key = deriveKey(password, decode(envelope.saltBase64))
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, decode(envelope.ivBase64)))
            updateAAD(AAD)
        }
        val plain = try {
            cipher.doFinal(cipherText)
        } catch (_: Exception) {
            throw IllegalArgumentException("备份密码错误或文件已损坏")
        } finally {
            password.fill('\u0000')
        }
        return envelope to json.decodeFromString(plain.toString(Charsets.UTF_8))
    }

    private fun validatePayload(payload: BackupPayload) {
        fun <T> unique(values: List<T>, label: String) {
            require(values.size == values.toSet().size) { "$label 存在重复 ID" }
        }
        unique(payload.novels.map { it.id }, "小说")
        unique(payload.appSettings.map { it.id }, "应用设置")
        require(payload.appSettings.size <= 1 && payload.appSettings.all { it.id == "app" }) { "应用设置结构无效" }
        unique(payload.chapters.map { it.id }, "章节")
        unique(payload.summaries.map { it.id }, "总结")
        unique(payload.candidates.map { it.id }, "候选")
        unique(payload.requestSnapshots.map { it.id }, "请求快照")
        val novelIds = payload.novels.mapTo(mutableSetOf()) { it.id }
        require(payload.novels.all { it.coverFileName == null || it.coverFileName == "${it.id}.webp" }) {
            "封面文件名无效"
        }
        require(payload.chapters.all { it.novelId in novelIds }) { "章节引用了不存在的小说" }
        require(payload.summaries.all { it.novelId in novelIds }) { "总结引用了不存在的小说" }
        val chapterIds = payload.chapters.mapTo(mutableSetOf()) { it.id }
        val summaryIds = payload.summaries.mapTo(mutableSetOf()) { it.id }
        require(payload.summarySources.all { it.summaryId in summaryIds }) { "总结来源关系损坏" }
        require(payload.summarySources.all {
            when (it.sourceNodeType) {
                com.ainovel.local.data.model.ContextNodeType.CHAPTER -> it.sourceNodeId in chapterIds
                com.ainovel.local.data.model.ContextNodeType.SUMMARY -> it.sourceNodeId in summaryIds
            }
        }) { "总结来源节点不存在" }
        val snapshotIds = payload.requestSnapshots.mapTo(mutableSetOf()) { it.id }
        require(payload.candidateGroups.all { it.frozenRequestSnapshotId in snapshotIds }) { "候选组冻结请求缺失" }
        val groupIds = payload.candidateGroups.mapTo(mutableSetOf()) { it.id }
        require(payload.candidates.all { it.candidateGroupId in groupIds && it.requestSnapshotId in snapshotIds }) {
            "候选引用关系损坏"
        }
        require(payload.coverImages.keys.all { it in novelIds }) { "封面引用了不存在的小说" }
        require(
            payload.novels.filter { it.coverFileName != null }.mapTo(mutableSetOf()) { it.id } ==
                payload.coverImages.keys,
        ) { "封面文件缺失" }
        payload.coverImages.values.forEach { encoded ->
            require(runCatching { decode(encoded).size <= MAX_COVER_BYTES }.getOrDefault(false)) { "封面数据无效或过大" }
        }
    }

    private fun coverDirectory(): File = File(context.filesDir, "novel-covers").also { it.mkdirs() }

    private fun coverFile(fileName: String): File = File(coverDirectory(), fileName)

    private fun restoreCoverImages(payload: BackupPayload) {
        val directory = coverDirectory()
        directory.listFiles()?.filter { it.isFile }?.forEach(File::delete)
        payload.novels.forEach { novel ->
            val fileName = novel.coverFileName ?: return@forEach
            val encoded = payload.coverImages[novel.id] ?: return@forEach
            coverFile(fileName).writeBytes(decode(encoded))
        }
    }

    private suspend fun clearAll() {
        backupDao.clearSummarySources()
        backupDao.clearAcceptances()
        backupDao.clearRevisions()
        backupDao.clearUsage()
        backupDao.clearCandidates()
        backupDao.clearGroups()
        backupDao.clearSnapshots()
        backupDao.clearSessions()
        backupDao.clearSummaries()
        backupDao.clearContextEntries()
        backupDao.clearChapters()
        backupDao.clearModels()
        backupDao.clearProviders()
        backupDao.clearNovels()
        backupDao.clearAppSettings()
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun requirePassword(password: CharArray) {
        require(password.size >= 8) { "备份密码至少需要 8 个字符" }
    }

    private fun sha256(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    private fun encode(data: ByteArray): String = Base64.getEncoder().encodeToString(data)
    private fun decode(data: String): ByteArray = Base64.getDecoder().decode(data)

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PBKDF2_ITERATIONS = 310_000
        const val MAX_COVER_BYTES = 5 * 1024 * 1024
        const val ROLLBACK_FILE_NAME = "restore_rollback.ainovelbackup"
        val AAD = "AINOVELBACKUP|1".toByteArray(Charsets.UTF_8)
    }
}
