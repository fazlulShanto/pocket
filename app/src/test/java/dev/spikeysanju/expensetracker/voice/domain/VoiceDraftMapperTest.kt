package dev.spikeysanju.expensetracker.voice.domain

import dev.spikeysanju.expensetracker.voice.model.VoiceExtractionContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceDraftMapperTest {
    private val mapper = VoiceDraftMapper()
    private val context = VoiceExtractionContext(
        currentDateIso = "2026-05-08",
        currentDateDisplay = "08/05/2026",
        timezoneId = "Asia/Dhaka",
        speechLanguageCode = "en",
        speechLanguageLabel = "English",
        selectedCurrencyCode = "BDT",
        selectedCurrencySymbol = "৳",
        allowedTransactionTypes = listOf("Income", "Expense"),
        allowedTagsByType = mapOf(
            "Expense" to listOf("Food", "Transportation"),
            "Income" to listOf("Work")
        )
    )

    @Test
    fun `parses extracted response and maps it into a ready draft`() {
        val response = """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\"title\":\"Lunch at cafe\",\"amount\":125.5,\"transactionType\":\"Expense\",\"tag\":\"Food\",\"tagHint\":null,\"dateIso\":\"2026-05-07\",\"note\":\"team lunch\",\"missingFields\":[],\"rawTranscript\":\"lunch at cafe for 125.5 yesterday\"}"
                  }
                }
              ]
            }
        """.trimIndent()

        val extraction = VoiceExtractionParser.parseResponse(
            responseBody = response,
            rawTranscript = "fallback transcript"
        )
        val draft = mapper.map(extraction, context)

        assertEquals("Lunch at cafe", draft.title)
        assertEquals(125.5, draft.amount ?: -1.0, 0.0)
        assertEquals("Expense", draft.transactionType)
        assertEquals("Food", draft.tag)
        assertEquals("07/05/2026", draft.date)
        assertEquals("team lunch", draft.note)
        assertTrue(draft.missingFields.isEmpty())
    }

    @Test
    fun `keeps unmatched tag in hint and falls back to current date`() {
        val response = """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\"title\":\"Office snacks\",\"amount\":42,\"transactionType\":\"Expense\",\"tag\":\"Snacks\",\"tagHint\":null,\"dateIso\":null,\"note\":null,\"missingFields\":[\"tag\",\"dateIso\"],\"rawTranscript\":\"office snacks 42 taka\"}"
                  }
                }
              ]
            }
        """.trimIndent()

        val extraction = VoiceExtractionParser.parseResponse(
            responseBody = response,
            rawTranscript = "fallback transcript"
        )
        val draft = mapper.map(extraction, context)

        assertNull(draft.tag)
        assertEquals("Snacks", draft.tagHint)
        assertEquals("08/05/2026", draft.date)
        assertEquals(listOf("tag"), draft.missingFields)
    }
}
