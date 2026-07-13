package dev.spikeysanju.expensetracker.services.backup

data class BackupDocumentV1(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long,
    val appVersion: String,
    val transactions: List<BackupTransaction>,
    val tags: List<BackupTag>,
    val preferences: BackupPreferences
) {
    companion object {
        const val FORMAT = "expenso-backup"
        const val VERSION = 1
    }
}

data class BackupTransaction(
    val id: Int,
    val title: String,
    val amount: Double,
    val transactionType: String,
    val tag: String,
    val date: String,
    val note: String,
    val createdAt: Long
)

data class BackupTag(
    val id: Int,
    val tagName: String,
    val tagType: String,
    val iconName: String,
    val keyword: String
)

data class BackupPreferences(
    val currencyCode: String,
    val darkMode: Boolean,
    val reasoningModelId: String?,
    val reasoningModelLabel: String?,
    val speechLanguageCode: String?,
    val speechLanguageLabel: String,
    val reasoningProvider: String? = null
)

data class RestorePreview(
    val exportedAt: Long,
    val appVersion: String,
    val transactionCount: Int,
    val tagCount: Int,
    val currencyCode: String,
    val darkMode: Boolean
)

data class RestoreResult(
    val transactionCount: Int,
    val tagCount: Int
)

data class InspectedBackup(
    val document: BackupDocumentV1,
    val preview: RestorePreview
)

class BackupValidationException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

class BackupStorageException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
