package dev.spikeysanju.expensetracker.services.datamanagement

import androidx.room.withTransaction
import dev.spikeysanju.expensetracker.data.local.AppDatabase
import dev.spikeysanju.expensetracker.model.Tag
import dev.spikeysanju.expensetracker.utils.TagCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionDataService @Inject constructor(
    private val database: AppDatabase
) {

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

}
