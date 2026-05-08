package dev.spikeysanju.expensetracker.services.datamanagement

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.opencsv.CSVReader
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.spikeysanju.expensetracker.data.local.AppDatabase
import dev.spikeysanju.expensetracker.model.Tag
import dev.spikeysanju.expensetracker.model.Transaction
import dev.spikeysanju.expensetracker.utils.TagCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.InputStreamReader
import java.text.DateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionDataService @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val database: AppDatabase
) {

    suspend fun importTransactionsFromCsv(csvFileUri: Uri): Int = withContext(Dispatchers.IO) {
        val parsedTransactions = appContext.contentResolver.openInputStream(csvFileUri)
            ?.use(::parseTransactionsCsv)
            ?: throw IllegalStateException("Unable to open the selected CSV file.")

        database.withTransaction {
            val tagDao = database.getTagDao()
            val transactionDao = database.getTransactionDao()
            val tagRegistry = tagDao.getAllTagsSnapshot()
                .associateByTo(mutableMapOf(), ::tagKey)

            val missingTags = parsedTransactions.mapNotNull { row ->
                val key = tagKey(row.tagName, row.transactionType)
                if (tagRegistry.containsKey(key)) {
                    null
                } else {
                    Tag(
                        tagName = row.tagName,
                        tagType = row.transactionType,
                        iconName = TagCatalog.defaultIconNameForTag(
                            tagName = row.tagName,
                            tagType = row.transactionType
                        ),
                        keyword = TagCatalog.defaultKeywordForTag(
                            tagName = row.tagName,
                            tagType = row.transactionType
                        )
                    ).also { tag ->
                        tagRegistry[key] = tag
                    }
                }
            }

            if (missingTags.isNotEmpty()) {
                tagDao.insertTags(missingTags)
            }

            val transactions = parsedTransactions.map { row ->
                val tag = tagRegistry.getValue(tagKey(row.tagName, row.transactionType))
                Transaction(
                    title = row.title,
                    amount = row.amount,
                    transactionType = row.transactionType,
                    tag = tag.tagName,
                    date = row.date,
                    note = row.note,
                    createdAt = row.createdAt
                )
            }

            transactionDao.insertTransactions(transactions)
            transactions.size
        }
    }

    suspend fun clearAllData(): Int = withContext(Dispatchers.IO) {
        database.withTransaction {
            val transactionDao = database.getTransactionDao()
            val tagDao = database.getTagDao()
            val deletedTransactionCount = transactionDao.getTransactionCount()

            transactionDao.deleteAllTransactions()
            tagDao.deleteAllTags()
            tagDao.insertTags(defaultTags())

            deletedTransactionCount
        }
    }

    private fun parseTransactionsCsv(inputStream: InputStream): List<ParsedCsvTransaction> {
        InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
            CSVReader(reader).use { csvReader ->
                val rows = csvReader.readAll()
                if (rows.isEmpty()) {
                    throw IllegalArgumentException("The selected CSV file is empty.")
                }

                val headerIndex = rows.first()
                    .mapIndexedNotNull { index, header ->
                        normalizeHeader(header)
                            .takeIf { it.isNotEmpty() }
                            ?.let { normalizedHeader -> normalizedHeader to index }
                    }
                    .toMap()

                requireColumn(headerIndex, "title")
                requireColumn(headerIndex, "amount")
                requireColumn(headerIndex, "transactiontype")
                requireColumn(headerIndex, "tag")
                requireColumn(headerIndex, "date")

                val parsedRows = rows.drop(1)
                    .mapIndexedNotNull { index, row ->
                        if (row.all { column -> column.isBlank() }) {
                            null
                        } else {
                            parseRow(rowNumber = index + 2, row = row, headerIndex = headerIndex)
                        }
                    }

                if (parsedRows.isEmpty()) {
                    throw IllegalArgumentException("The selected CSV file has no transaction rows.")
                }

                return parsedRows
            }
        }
    }

    private fun parseRow(
        rowNumber: Int,
        row: Array<String>,
        headerIndex: Map<String, Int>
    ): ParsedCsvTransaction {
        val title = requiredCell(row, headerIndex, "title", rowNumber)
        val amount = requiredCell(row, headerIndex, "amount", rowNumber)
            .toDoubleOrNull()
            ?: throw IllegalArgumentException("Row $rowNumber: amount is invalid.")
        val transactionType = normalizeTransactionType(
            requiredCell(row, headerIndex, "transactiontype", rowNumber),
            rowNumber
        )
        val tag = requiredCell(row, headerIndex, "tag", rowNumber)
        val date = requiredCell(row, headerIndex, "date", rowNumber)
        val note = optionalCell(row, headerIndex, "note")
        val createdAt = parseCreatedAt(optionalCell(row, headerIndex, "createdat"))

        return ParsedCsvTransaction(
            title = title,
            amount = amount,
            transactionType = transactionType,
            tagName = tag,
            date = date,
            note = note,
            createdAt = createdAt
        )
    }

    private fun requiredCell(
        row: Array<String>,
        headerIndex: Map<String, Int>,
        column: String,
        rowNumber: Int
    ): String {
        return optionalCell(row, headerIndex, column)
            .takeIf { value -> value.isNotBlank() }
            ?: throw IllegalArgumentException(
                "Row $rowNumber: $column is missing or empty."
            )
    }

    private fun optionalCell(
        row: Array<String>,
        headerIndex: Map<String, Int>,
        column: String
    ): String {
        val columnIndex = headerIndex[column] ?: return ""
        return row.getOrNull(columnIndex)?.trim().orEmpty()
    }

    private fun requireColumn(headerIndex: Map<String, Int>, column: String) {
        if (!headerIndex.containsKey(column)) {
            throw IllegalArgumentException("Missing required CSV column: $column")
        }
    }

    private fun normalizeHeader(header: String): String {
        return header.trim().replace(" ", "").lowercase(Locale.ROOT)
    }

    private fun normalizeTransactionType(value: String, rowNumber: Int): String {
        return when (value.trim().lowercase(Locale.ROOT)) {
            "income" -> "Income"
            "expense" -> "Expense"
            else -> throw IllegalArgumentException(
                "Row $rowNumber: transactionType must be Income or Expense."
            )
        }
    }

    private fun parseCreatedAt(value: String): Long {
        if (value.isBlank()) {
            return System.currentTimeMillis()
        }

        value.toLongOrNull()?.let { return it }

        return runCatching {
            DateFormat.getDateTimeInstance().parse(value)?.time
        }.getOrNull() ?: System.currentTimeMillis()
    }

    private fun defaultTags(): List<Tag> {
        return TagCatalog.defaultSeedTags.map { seed ->
            Tag(
                tagName = seed.tagName,
                tagType = seed.tagType,
                iconName = seed.iconName,
                keyword = seed.keyword
            )
        }
    }

    private fun tagKey(tag: Tag): String = tagKey(tag.tagName, tag.tagType)

    private fun tagKey(tagName: String, tagType: String): String {
        return "${tagType.trim().lowercase(Locale.ROOT)}:${tagName.trim().lowercase(Locale.ROOT)}"
    }

    private data class ParsedCsvTransaction(
        val title: String,
        val amount: Double,
        val transactionType: String,
        val tagName: String,
        val date: String,
        val note: String,
        val createdAt: Long
    )
}