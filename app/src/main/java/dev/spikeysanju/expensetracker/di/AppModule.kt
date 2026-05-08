package dev.spikeysanju.expensetracker.di

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.spikeysanju.expensetracker.data.local.AppDatabase
import dev.spikeysanju.expensetracker.data.local.datastore.CurrencyPreference
import dev.spikeysanju.expensetracker.data.local.datastore.CurrencyPreferenceDataStore
import dev.spikeysanju.expensetracker.data.local.datastore.UIModeDataStore
import dev.spikeysanju.expensetracker.data.local.datastore.UIModeImpl
import dev.spikeysanju.expensetracker.services.exportcsv.ExportCsvService
import dev.spikeysanju.expensetracker.utils.TagCatalog
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object AppModule {

    private val migration2To3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE tags ADD COLUMN iconName TEXT NOT NULL DEFAULT '${dev.spikeysanju.expensetracker.model.Tag.DEFAULT_ICON_NAME}'"
            )
            db.execSQL("ALTER TABLE tags ADD COLUMN keyword TEXT NOT NULL DEFAULT ''")

            TagCatalog.defaultSeedTags.forEach { seed ->
                db.execSQL(
                    """
                    UPDATE tags
                    SET iconName = ?, keyword = ?
                    WHERE LOWER(tagName) = LOWER(?) AND LOWER(tagType) = LOWER(?)
                    """.trimIndent(),
                    arrayOf(seed.iconName, seed.keyword, seed.tagName, seed.tagType)
                )
            }
        }
    }

    @Singleton
    @Provides
    fun providePreferenceManager(@ApplicationContext context: Context): UIModeImpl {
        return UIModeDataStore(context)
    }

    @Singleton
    @Provides
    fun provideCurrencyPreference(@ApplicationContext context: Context): CurrencyPreference {
        return CurrencyPreferenceDataStore(context)
    }

    @Singleton
    @Provides
    fun provideNoteDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "transaction.db")
            .addMigrations(migration2To3)
            .fallbackToDestructiveMigration()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    TagCatalog.defaultSeedTags.forEach { seed ->
                        db.execSQL(
                            "INSERT INTO tags (tagName, tagType, iconName, keyword) VALUES (?, ?, ?, ?)",
                            arrayOf(seed.tagName, seed.tagType, seed.iconName, seed.keyword)
                        )
                    }
                }
            })
            .build()
    }

    @Singleton
    @Provides
    fun provideExportCSV(@ApplicationContext context: Context): ExportCsvService {
        return ExportCsvService(appContext = context)
    }
}
