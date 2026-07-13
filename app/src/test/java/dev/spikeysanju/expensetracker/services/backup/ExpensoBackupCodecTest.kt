package dev.spikeysanju.expensetracker.services.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpensoBackupCodecTest {

    private val codec = ExpensoBackupCodec()

    @Test
    fun `round trip preserves all supported backup data`() {
        val document = sampleDocument()

        val json = codec.encode(document)
        val restored = codec.decode(json)

        assertEquals(document, restored)
        assertTrue(json.contains("Expenso backup \uD83D\uDCB0"))
        assertFalse(json.contains("groqApiKey"))
        assertFalse(json.contains("openRouterApiKey"))
        assertTrue(json.contains("\"reasoningProvider\":\"groq\""))
    }

    @Test
    fun `decode rejects a truncated document`() {
        val error = assertThrows(BackupValidationException::class.java) {
            codec.decode("{\"format\":\"expenso-backup\"")
        }

        assertTrue(error.message.orEmpty().contains("not valid JSON"))
    }

    @Test
    fun `decode accepts legacy backup without reasoning provider`() {
        val root = org.json.JSONObject(codec.encode(sampleDocument()))
        root.getJSONObject("preferences").remove("reasoningProvider")

        val restored = codec.decode(root.toString())

        assertNull(restored.preferences.reasoningProvider)
    }

    @Test
    fun `decode rejects unsupported format and version`() {
        val wrongFormat = codec.encode(sampleDocument()).replace(
            "\"format\":\"expenso-backup\"",
            "\"format\":\"another-app\""
        )
        val wrongVersion = codec.encode(sampleDocument()).replace(
            "\"version\":1",
            "\"version\":2"
        )

        val formatError = assertThrows(BackupValidationException::class.java) {
            codec.decode(wrongFormat)
        }
        val versionError = assertThrows(BackupValidationException::class.java) {
            codec.decode(wrongVersion)
        }
        assertTrue(formatError.message.orEmpty().contains("not an Expenso backup"))
        assertTrue(versionError.message.orEmpty().contains("version 2 is not supported"))
    }

    @Test
    fun `validation rejects duplicate ids and missing tag references`() {
        val duplicateTransaction = sampleDocument().let { document ->
            document.copy(transactions = document.transactions + document.transactions.first())
        }
        val missingTag = sampleDocument().let { document ->
            document.copy(
                transactions = document.transactions.map { transaction ->
                    transaction.copy(tag = "Missing category")
                }
            )
        }

        assertThrows(BackupValidationException::class.java) { codec.encode(duplicateTransaction) }
        assertThrows(BackupValidationException::class.java) { codec.encode(missingTag) }
    }

    @Test
    fun `validation rejects non finite amounts`() {
        val document = sampleDocument().let { backup ->
            backup.copy(
                transactions = backup.transactions.map { transaction ->
                    transaction.copy(amount = Double.POSITIVE_INFINITY)
                }
            )
        }

        assertThrows(BackupValidationException::class.java) { codec.encode(document) }
    }

    private fun sampleDocument() = BackupDocumentV1(
        exportedAt = 1_720_000_000_000,
        appVersion = "v1.0.0-alpha01 (1)",
        transactions = listOf(
            BackupTransaction(
                id = 42,
                title = "Expenso backup \uD83D\uDCB0",
                amount = 1250.75,
                transactionType = "Expense",
                tag = "Food & Dining",
                date = "13/07/2026",
                note = "Dinner, dessert, and a quoted \"note\"\nSecond line",
                createdAt = 1_719_999_999_000
            )
        ),
        tags = listOf(
            BackupTag(
                id = 7,
                tagName = "Food & Dining",
                tagType = "Expense",
                iconName = "utensils",
                keyword = "food,dinner"
            )
        ),
        preferences = BackupPreferences(
            currencyCode = "BDT",
            darkMode = true,
            reasoningModelId = "custom/groq-model",
            reasoningModelLabel = "custom/groq-model",
            speechLanguageCode = "bn-BD",
            speechLanguageLabel = "Bangla (Bangladesh)",
            reasoningProvider = "groq"
        )
    )
}
