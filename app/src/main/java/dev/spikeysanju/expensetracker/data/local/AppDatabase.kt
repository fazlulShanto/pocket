package dev.spikeysanju.expensetracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.spikeysanju.expensetracker.model.Tag
import dev.spikeysanju.expensetracker.model.Transaction

@Database(
    entities = [Transaction::class, Tag::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getTransactionDao(): TransactionDao
    abstract fun getTagDao(): TagDao
}
