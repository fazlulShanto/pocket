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
    fun `parses delimited response and maps it into a ready draft`() {
        val extraction = VoiceExtractionParser.parseResponse(
            responseBody = completionResponse(
                delimitedExtraction(
                    title = "Lunch at cafe",
                    amount = "125.5",
                    type = "Expense",
                    tag = "Food",
                    date = "2026-05-07",
                    note = "team lunch"
                )
            ),
            rawTranscript = "lunch at cafe for 125.5 yesterday",
            context = context
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
    fun `keeps unmatched tag hint and falls back to current date`() {
        val extraction = VoiceExtractionParser.parseResponse(
            responseBody = completionResponse(
                delimitedExtraction(
                    title = "Office snacks",
                    amount = "42",
                    type = "Expense",
                    tagHint = "Snacks",
                    missingFields = listOf("tag", "dateIso")
                )
            ),
            rawTranscript = "office snacks 42 taka",
            context = context
        )
        val draft = mapper.map(extraction, context)

        assertNull(draft.tag)
        assertEquals("Snacks", draft.tagHint)
        assertEquals("08/05/2026", draft.date)
        assertEquals(listOf("tag"), draft.missingFields)
    }

    private fun completionResponse(content: String): String {
        return org.json.JSONObject()
            .put(
                "choices",
                org.json.JSONArray().put(
                    org.json.JSONObject()
                        .put("finish_reason", "stop")
                        .put("message", org.json.JSONObject().put("content", content))
                )
            )
            .toString()
    }

    private fun delimitedExtraction(
        title: String = "",
        amount: String = "",
        type: String = "",
        tag: String = "",
        tagHint: String = "",
        date: String = "",
        note: String = "",
        missingFields: List<String> = emptyList()
    ): String = buildString {
        appendLine("===voice_transaction_extraction_start===")
        appendLine("===title_start===\n$title\n===title_end===")
        appendLine("===amount_start===\n$amount\n===amount_end===")
        appendLine("===transaction_type_start===\n$type\n===transaction_type_end===")
        appendLine("===tag_start===\n$tag\n===tag_end===")
        appendLine("===tag_hint_start===\n$tagHint\n===tag_hint_end===")
        appendLine("===date_iso_start===\n$date\n===date_iso_end===")
        appendLine("===note_start===\n$note\n===note_end===")
        appendLine("===missing_fields_start===")
        missingFields.forEach { field ->
            appendLine("===missing_field_start===\n$field\n===missing_field_end===")
        }
        appendLine("===missing_fields_end===")
        append("===voice_transaction_extraction_end===")
    }
}
