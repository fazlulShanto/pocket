package dev.spikeysanju.expensetracker.services.backup

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

@Singleton
class ExpensoBackupCodec @Inject constructor() {

    fun encode(document: BackupDocumentV1): String {
        validate(document)
        return JSONObject()
            .put(KEY_FORMAT, document.format)
            .put(KEY_VERSION, document.version)
            .put(KEY_EXPORTED_AT, document.exportedAt)
            .put(KEY_APP_VERSION, document.appVersion)
            .put(
                KEY_TRANSACTIONS,
                JSONArray().apply {
                    document.transactions.forEach { transaction ->
                        put(
                            JSONObject()
                                .put(KEY_ID, transaction.id)
                                .put(KEY_TITLE, transaction.title)
                                .put(KEY_AMOUNT, transaction.amount)
                                .put(KEY_TRANSACTION_TYPE, transaction.transactionType)
                                .put(KEY_TAG, transaction.tag)
                                .put(KEY_DATE, transaction.date)
                                .put(KEY_NOTE, transaction.note)
                                .put(KEY_CREATED_AT, transaction.createdAt)
                        )
                    }
                }
            )
            .put(
                KEY_TAGS,
                JSONArray().apply {
                    document.tags.forEach { tag ->
                        put(
                            JSONObject()
                                .put(KEY_ID, tag.id)
                                .put(KEY_TAG_NAME, tag.tagName)
                                .put(KEY_TAG_TYPE, tag.tagType)
                                .put(KEY_ICON_NAME, tag.iconName)
                                .put(KEY_KEYWORD, tag.keyword)
                        )
                    }
                }
            )
            .put(
                KEY_PREFERENCES,
                JSONObject()
                    .put(KEY_CURRENCY_CODE, document.preferences.currencyCode)
                    .put(KEY_DARK_MODE, document.preferences.darkMode)
                    .putNullable(KEY_REASONING_MODEL_ID, document.preferences.reasoningModelId)
                    .putNullable(KEY_REASONING_MODEL_LABEL, document.preferences.reasoningModelLabel)
                    .putNullable(KEY_SPEECH_LANGUAGE_CODE, document.preferences.speechLanguageCode)
                    .put(KEY_SPEECH_LANGUAGE_LABEL, document.preferences.speechLanguageLabel)
            )
            .toString()
    }

    fun decode(json: String): BackupDocumentV1 {
        val root = try {
            JSONObject(json)
        } catch (error: JSONException) {
            throw BackupValidationException("The selected file is not valid JSON.", error)
        }

        val format: String
        val version: Int
        try {
            format = root.getString(KEY_FORMAT)
            version = root.getInt(KEY_VERSION)
        } catch (error: JSONException) {
            throw BackupValidationException("This file is not an Expenso backup.", error)
        }
        validateHeader(format, version)

        val document = try {
            BackupDocumentV1(
                format = format,
                version = version,
                exportedAt = root.getLong(KEY_EXPORTED_AT),
                appVersion = root.getString(KEY_APP_VERSION),
                transactions = root.getJSONArray(KEY_TRANSACTIONS).mapObjects { item ->
                    BackupTransaction(
                        id = item.getInt(KEY_ID),
                        title = item.getString(KEY_TITLE),
                        amount = item.getDouble(KEY_AMOUNT),
                        transactionType = item.getString(KEY_TRANSACTION_TYPE),
                        tag = item.getString(KEY_TAG),
                        date = item.getString(KEY_DATE),
                        note = item.getString(KEY_NOTE),
                        createdAt = item.getLong(KEY_CREATED_AT)
                    )
                },
                tags = root.getJSONArray(KEY_TAGS).mapObjects { item ->
                    BackupTag(
                        id = item.getInt(KEY_ID),
                        tagName = item.getString(KEY_TAG_NAME),
                        tagType = item.getString(KEY_TAG_TYPE),
                        iconName = item.getString(KEY_ICON_NAME),
                        keyword = item.getString(KEY_KEYWORD)
                    )
                },
                preferences = root.getJSONObject(KEY_PREFERENCES).let { preferences ->
                    BackupPreferences(
                        currencyCode = preferences.getString(KEY_CURRENCY_CODE),
                        darkMode = preferences.getBoolean(KEY_DARK_MODE),
                        reasoningModelId = preferences.nullableString(KEY_REASONING_MODEL_ID),
                        reasoningModelLabel = preferences.nullableString(KEY_REASONING_MODEL_LABEL),
                        speechLanguageCode = preferences.nullableString(KEY_SPEECH_LANGUAGE_CODE),
                        speechLanguageLabel = preferences.getString(KEY_SPEECH_LANGUAGE_LABEL)
                    )
                }
            )
        } catch (error: JSONException) {
            throw BackupValidationException(
                "The backup is missing a required field or contains an invalid value.",
                error
            )
        }

        validate(document)
        return document
    }

    fun validate(document: BackupDocumentV1) {
        validateHeader(document.format, document.version)
        requireBackup(document.exportedAt >= 0) { "The backup export timestamp is invalid." }
        requireBackup(document.appVersion.isNotBlank()) { "The backup app version is missing." }

        validateUniqueIds(document.transactions.map(BackupTransaction::id), "transaction")
        validateUniqueIds(document.tags.map(BackupTag::id), "tag")

        document.tags.forEachIndexed { index, tag ->
            val number = index + 1
            requireBackup(tag.id > 0) { "Tag $number has an invalid ID." }
            requireBackup(tag.tagName.isNotBlank()) { "Tag $number has no name." }
            validateTransactionType(tag.tagType, "Tag $number")
            requireBackup(tag.iconName.isNotBlank()) { "Tag $number has no icon name." }
        }

        val availableTags = document.tags.mapTo(mutableSetOf()) { tag ->
            tagKey(tag.tagName, tag.tagType)
        }
        document.transactions.forEachIndexed { index, transaction ->
            val number = index + 1
            requireBackup(transaction.id > 0) { "Transaction $number has an invalid ID." }
            requireBackup(transaction.title.isNotBlank()) { "Transaction $number has no title." }
            requireBackup(transaction.amount.isFinite()) {
                "Transaction $number has an invalid amount."
            }
            validateTransactionType(transaction.transactionType, "Transaction $number")
            requireBackup(transaction.tag.isNotBlank()) { "Transaction $number has no tag." }
            requireBackup(transaction.date.isNotBlank()) { "Transaction $number has no date." }
            requireBackup(transaction.createdAt >= 0) {
                "Transaction $number has an invalid creation timestamp."
            }
            requireBackup(
                tagKey(transaction.tag, transaction.transactionType) in availableTags
            ) {
                "Transaction $number references a tag that is missing from the backup."
            }
        }

        requireBackup(document.preferences.currencyCode.uppercase(Locale.ROOT) in CURRENCIES) {
            "The backup currency is not supported."
        }
        requireBackup(document.preferences.speechLanguageLabel.isNotBlank()) {
            "The backup speech language is missing."
        }
        validateOptionalText(document.preferences.reasoningModelId, "reasoning model ID")
        validateOptionalText(document.preferences.reasoningModelLabel, "reasoning model label")
        validateOptionalText(document.preferences.speechLanguageCode, "speech language code")
    }

    private fun validateHeader(format: String, version: Int) {
        requireBackup(format == BackupDocumentV1.FORMAT) {
            "This file is not an Expenso backup."
        }
        requireBackup(version == BackupDocumentV1.VERSION) {
            "Backup version $version is not supported by this version of Expenso."
        }
    }

    private fun validateUniqueIds(ids: List<Int>, label: String) {
        requireBackup(ids.size == ids.toSet().size) {
            "The backup contains duplicate $label IDs."
        }
    }

    private fun validateTransactionType(value: String, prefix: String) {
        requireBackup(value == INCOME || value == EXPENSE) {
            "$prefix must use Income or Expense as its type."
        }
    }

    private fun validateOptionalText(value: String?, label: String) {
        requireBackup(value == null || value.isNotBlank()) {
            "The backup $label cannot be blank."
        }
    }

    private fun tagKey(name: String, type: String): String {
        return "${type.trim().lowercase(Locale.ROOT)}:${name.trim().lowercase(Locale.ROOT)}"
    }

    private inline fun requireBackup(condition: Boolean, message: () -> String) {
        if (!condition) {
            throw BackupValidationException(message())
        }
    }

    private fun JSONObject.putNullable(key: String, value: String?): JSONObject {
        return put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.nullableString(key: String): String? {
        return if (isNull(key)) null else getString(key)
    }

    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
        return (0 until length()).map { index -> transform(getJSONObject(index)) }
    }

    private companion object {
        const val KEY_FORMAT = "format"
        const val KEY_VERSION = "version"
        const val KEY_EXPORTED_AT = "exportedAt"
        const val KEY_APP_VERSION = "appVersion"
        const val KEY_TRANSACTIONS = "transactions"
        const val KEY_TAGS = "tags"
        const val KEY_PREFERENCES = "preferences"
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_AMOUNT = "amount"
        const val KEY_TRANSACTION_TYPE = "transactionType"
        const val KEY_TAG = "tag"
        const val KEY_DATE = "date"
        const val KEY_NOTE = "note"
        const val KEY_CREATED_AT = "createdAt"
        const val KEY_TAG_NAME = "tagName"
        const val KEY_TAG_TYPE = "tagType"
        const val KEY_ICON_NAME = "iconName"
        const val KEY_KEYWORD = "keyword"
        const val KEY_CURRENCY_CODE = "currencyCode"
        const val KEY_DARK_MODE = "darkMode"
        const val KEY_REASONING_MODEL_ID = "reasoningModelId"
        const val KEY_REASONING_MODEL_LABEL = "reasoningModelLabel"
        const val KEY_SPEECH_LANGUAGE_CODE = "speechLanguageCode"
        const val KEY_SPEECH_LANGUAGE_LABEL = "speechLanguageLabel"
        const val INCOME = "Income"
        const val EXPENSE = "Expense"
        val CURRENCIES = setOf("USD", "GBP", "EUR", "CNY", "BDT")
    }
}
