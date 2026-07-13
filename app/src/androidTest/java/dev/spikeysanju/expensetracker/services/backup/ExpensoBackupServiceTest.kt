package dev.spikeysanju.expensetracker.services.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.spikeysanju.expensetracker.data.local.AppDatabase
import dev.spikeysanju.expensetracker.data.local.datastore.CurrencyPreference
import dev.spikeysanju.expensetracker.data.local.datastore.UIModeImpl
import dev.spikeysanju.expensetracker.model.Tag
import dev.spikeysanju.expensetracker.model.Transaction
import dev.spikeysanju.expensetracker.utils.SupportedCurrency
import dev.spikeysanju.expensetracker.voice.data.local.VoiceConfigStore
import dev.spikeysanju.expensetracker.voice.model.GroqReasoningModels
import dev.spikeysanju.expensetracker.voice.model.VoiceSettingsConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpensoBackupServiceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var currencyPreference: FakeCurrencyPreference
    private lateinit var uiMode: FakeUiMode
    private lateinit var voiceConfigStore: FakeVoiceConfigStore
    private lateinit var service: ExpensoBackupService

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        currencyPreference = FakeCurrencyPreference(SupportedCurrency.BDT)
        uiMode = FakeUiMode(false)
        voiceConfigStore = FakeVoiceConfigStore(
            VoiceSettingsConfig(
                groqApiKey = "local-groq-secret",
                reasoningModelId = "old-model"
            )
        )
        service = ExpensoBackupService(
            appContext = context,
            database = database,
            currencyPreference = currencyPreference,
            uiModeDataStore = uiMode,
            voiceConfigStore = voiceConfigStore,
            codec = ExpensoBackupCodec()
        )

        database.getTagDao().insertTag(
            Tag(
                id = 1,
                tagName = "Old tag",
                tagType = "Expense",
                iconName = "archive",
                keyword = "old"
            )
        )
        database.getTransactionDao().insertTransaction(
            Transaction(
                id = 1,
                title = "Old transaction",
                amount = 10.0,
                transactionType = "Expense",
                tag = "Old tag",
                date = "01/01/2026",
                note = "",
                createdAt = 100
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun restore_replacesDatabasePreservesIdsAndKeepsLocalApiKeys() = runBlocking {
        val backup = replacementBackup()

        service.restore(backup)
        service.restore(backup)

        val transactions = database.getTransactionDao().getAllTransactionsSnapshot()
        val tags = database.getTagDao().getAllTagsSnapshot()
        assertEquals(1, transactions.size)
        assertEquals(77, transactions.single().id)
        assertEquals("Restored transaction", transactions.single().title)
        assertEquals(1, tags.size)
        assertEquals(55, tags.single().id)
        assertEquals(SupportedCurrency.USD, currencyPreference.current.value)
        assertEquals(true, uiMode.current.value)
        assertEquals("local-groq-secret", voiceConfigStore.config.groqApiKey)
        assertEquals("new-model", voiceConfigStore.config.reasoningModelId)
    }

    @Test
    fun validationFailureLeavesCurrentDatabaseUntouched() = runBlocking {
        val invalid = replacementBackup().copy(tags = emptyList())

        assertThrows(BackupValidationException::class.java) {
            runBlocking { service.restore(invalid) }
        }

        assertEquals("Old transaction", database.getTransactionDao().getAllTransactionsSnapshot().single().title)
        assertEquals("Old tag", database.getTagDao().getAllTagsSnapshot().single().tagName)
        assertEquals(SupportedCurrency.BDT, currencyPreference.current.value)
        assertEquals(false, uiMode.current.value)
        assertEquals("old-model", voiceConfigStore.config.reasoningModelId)
    }

    @Test
    fun legacyBackupResetsOpenRouterModelToGroqDefault() = runBlocking {
        val legacyBackup = replacementBackup().let { backup ->
            backup.copy(
                preferences = backup.preferences.copy(
                    reasoningModelId = "legacy/openrouter-model",
                    reasoningModelLabel = "Legacy model",
                    reasoningProvider = null
                )
            )
        }

        service.restore(legacyBackup)

        assertEquals(
            GroqReasoningModels.DEFAULT_MODEL_ID,
            voiceConfigStore.config.reasoningModelId
        )
        assertEquals("local-groq-secret", voiceConfigStore.config.groqApiKey)
    }

    @Test
    fun preferenceFailureRestoresThePreviousDatabaseAndPreferences() = runBlocking {
        voiceConfigStore.failNextSave = true

        assertThrows(BackupStorageException::class.java) {
            runBlocking { service.restore(replacementBackup()) }
        }

        assertEquals("Old transaction", database.getTransactionDao().getAllTransactionsSnapshot().single().title)
        assertEquals("Old tag", database.getTagDao().getAllTagsSnapshot().single().tagName)
        assertEquals(SupportedCurrency.BDT, currencyPreference.current.value)
        assertEquals(false, uiMode.current.value)
        assertEquals("old-model", voiceConfigStore.config.reasoningModelId)
    }

    @Test
    fun backupContractsUseTheExpensoDocumentType() {
        val createIntent = CreateBackupContract().createIntent(context, "backup.expenso")
        val restoreIntent = RestoreBackupContract().createIntent(context, Unit)

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, createIntent.action)
        assertEquals(BACKUP_MIME_TYPE, createIntent.type)
        assertEquals("backup.expenso", createIntent.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, restoreIntent.action)
        assertEquals(BACKUP_MIME_TYPE, restoreIntent.type)
        assertNull(CreateBackupContract().parseResult(Activity.RESULT_CANCELED, null))
        assertNull(RestoreBackupContract().parseResult(Activity.RESULT_CANCELED, null))
    }

    private fun replacementBackup() = BackupDocumentV1(
        exportedAt = 2000,
        appVersion = "test (1)",
        transactions = listOf(
            BackupTransaction(
                id = 77,
                title = "Restored transaction",
                amount = 99.5,
                transactionType = "Income",
                tag = "Restored tag",
                date = "13/07/2026",
                note = "restored",
                createdAt = 1999
            )
        ),
        tags = listOf(
            BackupTag(
                id = 55,
                tagName = "Restored tag",
                tagType = "Income",
                iconName = "wallet",
                keyword = "salary"
            )
        ),
        preferences = BackupPreferences(
            currencyCode = "USD",
            darkMode = true,
            reasoningModelId = "new-model",
            reasoningModelLabel = "New model",
            speechLanguageCode = null,
            speechLanguageLabel = "Auto detect",
            reasoningProvider = GroqReasoningModels.PROVIDER_ID
        )
    )

    private class FakeCurrencyPreference(initial: SupportedCurrency) : CurrencyPreference {
        val current = MutableStateFlow(initial)
        override val selectedCurrency: Flow<SupportedCurrency> = current

        override suspend fun saveSelectedCurrency(currency: SupportedCurrency) {
            current.value = currency
        }
    }

    private class FakeUiMode(initial: Boolean) : UIModeImpl {
        val current = MutableStateFlow(initial)
        override val uiMode: Flow<Boolean> = current

        override suspend fun saveToDataStore(isNightMode: Boolean) {
            current.value = isNightMode
        }
    }

    private class FakeVoiceConfigStore(initial: VoiceSettingsConfig) : VoiceConfigStore {
        var config: VoiceSettingsConfig = initial
        var failNextSave: Boolean = false

        override suspend fun getConfig(): VoiceSettingsConfig = config

        override suspend fun saveConfig(config: VoiceSettingsConfig) {
            if (failNextSave) {
                failNextSave = false
                throw IllegalStateException("Simulated preference write failure")
            }
            this.config = config
        }
    }
}
