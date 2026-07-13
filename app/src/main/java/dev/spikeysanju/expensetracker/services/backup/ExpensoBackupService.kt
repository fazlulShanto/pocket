package dev.spikeysanju.expensetracker.services.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.spikeysanju.expensetracker.BuildConfig
import dev.spikeysanju.expensetracker.data.local.AppDatabase
import dev.spikeysanju.expensetracker.data.local.datastore.CurrencyPreference
import dev.spikeysanju.expensetracker.data.local.datastore.UIModeImpl
import dev.spikeysanju.expensetracker.model.Tag
import dev.spikeysanju.expensetracker.model.Transaction
import dev.spikeysanju.expensetracker.utils.SupportedCurrency
import dev.spikeysanju.expensetracker.voice.data.local.VoiceConfigStore
import dev.spikeysanju.expensetracker.voice.model.VoiceSettingsConfig
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class ExpensoBackupService @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val database: AppDatabase,
    private val currencyPreference: CurrencyPreference,
    private val uiModeDataStore: UIModeImpl,
    private val voiceConfigStore: VoiceConfigStore,
    private val codec: ExpensoBackupCodec
) {

    suspend fun createBackup(destination: Uri): Uri = withContext(Dispatchers.IO) {
        val document = captureDocument()
        val json = codec.encode(document)
        try {
            val outputStream = appContext.contentResolver.openOutputStream(destination, "wt")
                ?: throw BackupStorageException(
                    "Expenso could not open the selected backup location."
                )
            outputStream.use { stream ->
                OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                    writer.write(json)
                }
            }
        } catch (error: BackupStorageException) {
            throw error
        } catch (error: Exception) {
            throw BackupStorageException("Expenso could not write the backup file.", error)
        }
        destination
    }

    suspend fun inspectBackup(source: Uri): InspectedBackup = withContext(Dispatchers.IO) {
        val json = try {
            val inputStream = appContext.contentResolver.openInputStream(source)
                ?: throw BackupStorageException("Expenso could not open the selected backup file.")
            inputStream.use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                    readLimited(reader)
                }
            }
        } catch (error: BackupStorageException) {
            throw error
        } catch (error: BackupValidationException) {
            throw error
        } catch (error: Exception) {
            throw BackupStorageException("Expenso could not read the selected backup file.", error)
        }

        val document = codec.decode(json)
        InspectedBackup(
            document = document,
            preview = RestorePreview(
                exportedAt = document.exportedAt,
                appVersion = document.appVersion,
                transactionCount = document.transactions.size,
                tagCount = document.tags.size,
                currencyCode = document.preferences.currencyCode,
                darkMode = document.preferences.darkMode
            )
        )
    }

    suspend fun restore(document: BackupDocumentV1): RestoreResult = withContext(Dispatchers.IO) {
        codec.validate(document)
        val previousDocument = captureDocument()
        val previousVoiceConfig = voiceConfigStore.getConfig()

        try {
            replaceDatabase(document)
            applyPreferences(
                preferences = document.preferences,
                localVoiceConfig = previousVoiceConfig,
                resetModelRefresh = true
            )
        } catch (restoreError: Exception) {
            val databaseRollback = runCatching { replaceDatabase(previousDocument) }
            val preferencesRollback = runCatching {
                applyPreferences(
                    preferences = previousDocument.preferences,
                    localVoiceConfig = previousVoiceConfig,
                    resetModelRefresh = false
                )
            }
            if (databaseRollback.isFailure || preferencesRollback.isFailure) {
                throw BackupStorageException(
                    "Restore failed and Expenso could not fully recover the previous state.",
                    restoreError
                )
            }
            throw BackupStorageException(
                "Restore failed. Your previous data was recovered.",
                restoreError
            )
        }

        RestoreResult(
            transactionCount = document.transactions.size,
            tagCount = document.tags.size
        )
    }

    private suspend fun captureDocument(): BackupDocumentV1 {
        val databaseSnapshot = database.withTransaction {
            DatabaseSnapshot(
                transactions = database.getTransactionDao().getAllTransactionsSnapshot(),
                tags = database.getTagDao().getAllTagsSnapshot()
            )
        }
        val currency = currencyPreference.selectedCurrency.first()
        val darkMode = uiModeDataStore.uiMode.first()
        val voiceConfig = voiceConfigStore.getConfig()

        return BackupDocumentV1(
            exportedAt = System.currentTimeMillis(),
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            transactions = databaseSnapshot.transactions.map { transaction ->
                BackupTransaction(
                    id = transaction.id,
                    title = transaction.title,
                    amount = transaction.amount,
                    transactionType = transaction.transactionType,
                    tag = transaction.tag,
                    date = transaction.date,
                    note = transaction.note,
                    createdAt = transaction.createdAt
                )
            },
            tags = databaseSnapshot.tags.map { tag ->
                BackupTag(
                    id = tag.id,
                    tagName = tag.tagName,
                    tagType = tag.tagType,
                    iconName = tag.iconName,
                    keyword = tag.keyword
                )
            },
            preferences = BackupPreferences(
                currencyCode = currency.code,
                darkMode = darkMode,
                reasoningModelId = voiceConfig.reasoningModelId,
                reasoningModelLabel = voiceConfig.reasoningModelLabel,
                speechLanguageCode = voiceConfig.speechLanguageCode,
                speechLanguageLabel = voiceConfig.speechLanguageLabel
            )
        )
    }

    private suspend fun replaceDatabase(document: BackupDocumentV1) {
        database.withTransaction {
            val transactionDao = database.getTransactionDao()
            val tagDao = database.getTagDao()
            transactionDao.deleteAllTransactions()
            tagDao.deleteAllTags()

            val tags = document.tags.map { tag ->
                Tag(
                    id = tag.id,
                    tagName = tag.tagName,
                    tagType = tag.tagType,
                    iconName = tag.iconName,
                    keyword = tag.keyword
                )
            }
            val transactions = document.transactions.map { transaction ->
                Transaction(
                    id = transaction.id,
                    title = transaction.title,
                    amount = transaction.amount,
                    transactionType = transaction.transactionType,
                    tag = transaction.tag,
                    date = transaction.date,
                    note = transaction.note,
                    createdAt = transaction.createdAt
                )
            }
            if (tags.isNotEmpty()) {
                tagDao.insertTags(tags)
            }
            if (transactions.isNotEmpty()) {
                transactionDao.insertTransactions(transactions)
            }
        }
    }

    private suspend fun applyPreferences(
        preferences: BackupPreferences,
        localVoiceConfig: VoiceSettingsConfig,
        resetModelRefresh: Boolean
    ) {
        uiModeDataStore.saveToDataStore(preferences.darkMode)
        currencyPreference.saveSelectedCurrency(
            SupportedCurrency.fromCode(preferences.currencyCode)
        )
        voiceConfigStore.saveConfig(
            localVoiceConfig.copy(
                reasoningModelId = preferences.reasoningModelId,
                reasoningModelLabel = preferences.reasoningModelLabel,
                speechLanguageCode = preferences.speechLanguageCode,
                speechLanguageLabel = preferences.speechLanguageLabel,
                lastModelRefreshAt = if (resetModelRefresh) {
                    null
                } else {
                    localVoiceConfig.lastModelRefreshAt
                }
            )
        )
    }

    private fun readLimited(reader: InputStreamReader): String {
        val result = StringBuilder()
        val buffer = CharArray(READ_BUFFER_SIZE)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) {
                break
            }
            result.append(buffer, 0, count)
            if (result.length > MAX_BACKUP_CHARACTERS) {
                throw BackupValidationException("The selected backup file is too large.")
            }
        }
        return result.toString()
    }

    private data class DatabaseSnapshot(
        val transactions: List<Transaction>,
        val tags: List<Tag>
    )

    private companion object {
        const val READ_BUFFER_SIZE = 8_192
        const val MAX_BACKUP_CHARACTERS = 25 * 1024 * 1024
    }
}
