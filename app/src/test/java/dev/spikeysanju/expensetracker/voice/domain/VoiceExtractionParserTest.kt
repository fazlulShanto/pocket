package dev.spikeysanju.expensetracker.voice.domain

import dev.spikeysanju.expensetracker.voice.model.VoiceExtractionContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class VoiceExtractionParserTest {
    private val context = VoiceExtractionContext(
        currentDateIso = "2026-07-14",
        currentDateDisplay = "14/07/2026",
        timezoneId = "Asia/Dhaka",
        speechLanguageCode = "en",
        speechLanguageLabel = "English",
        selectedCurrencyCode = "BDT",
        selectedCurrencySymbol = "৳",
        allowedTransactionTypes = listOf("Income", "Expense"),
        allowedTagsByType = mapOf("Expense" to listOf("Food"), "Income" to listOf("Work"))
    )

    @Test
    fun `allows reasoning outside markers and multiline scalar values`() {
        val content = "Reasoning that must be ignored.\n" + validContent(
            title = "Dinner\nwith friends",
            amount = "250",
            type = "expense",
            tag = "food",
            date = "2026-07-13"
        ) + "\nDone."

        val extraction = VoiceExtractionParser.parseDelimitedContent(
            content,
            rawTranscript = "trusted transcript",
            context = context
        )

        assertEquals("Dinner\nwith friends", extraction.title)
        assertEquals(250.0, extraction.amount ?: -1.0, 0.0)
        assertEquals("Expense", extraction.transactionType)
        assertEquals("Food", extraction.tag)
        assertEquals("trusted transcript", extraction.rawTranscript)
    }

    @Test
    fun `empty scalar sections become null`() {
        val extraction = VoiceExtractionParser.parseDelimitedContent(
            validContent(missingFields = listOf("title", "amount")),
            rawTranscript = "spoken words",
            context = context
        )

        assertNull(extraction.title)
        assertNull(extraction.amount)
        assertEquals(listOf("title", "amount"), extraction.missingFields)
    }

    @Test
    fun `rejects duplicated missing and out of order markers`() {
        val duplicateTitle = validContent().replace(
            "===amount_start===",
            "===title_start===duplicate===title_end===\n===amount_start==="
        )
        val outOfOrder = validContent().replace(
            "===title_start===\n\n===title_end===\n===amount_start===\n\n===amount_end===",
            "===amount_start===\n\n===amount_end===\n===title_start===\n\n===title_end==="
        )

        assertThrows(VoiceExtractionValidationException::class.java) {
            VoiceExtractionParser.parseDelimitedContent(duplicateTitle, "raw", context)
        }
        assertThrows(VoiceExtractionValidationException::class.java) {
            VoiceExtractionParser.parseDelimitedContent(outOfOrder, "raw", context)
        }
    }

    @Test
    fun `rejects invalid semantic values and untagged list content`() {
        val invalidAmount = validContent(amount = "zero")
        val invalidDate = validContent(date = "2026-02-30")
        val invalidTag = validContent(tag = "Unknown")
        val untaggedMissing = validContent().replace(
            "===missing_fields_end===",
            "amount\n===missing_fields_end==="
        )

        listOf(invalidAmount, invalidDate, invalidTag, untaggedMissing).forEach { content ->
            assertThrows(VoiceExtractionValidationException::class.java) {
                VoiceExtractionParser.parseDelimitedContent(content, "raw", context)
            }
        }
    }

    @Test
    fun `rejects empty choices content and truncated completions`() {
        val emptyChoices = "{\"choices\":[]}"
        val truncated = org.json.JSONObject()
            .put(
                "choices",
                org.json.JSONArray().put(
                    org.json.JSONObject()
                        .put("finish_reason", "length")
                        .put(
                            "message",
                            org.json.JSONObject().put("content", validContent())
                        )
                )
            )
            .toString()

        assertThrows(VoiceExtractionValidationException::class.java) {
            VoiceExtractionParser.parseResponse(emptyChoices, "raw", context)
        }
        assertThrows(VoiceExtractionValidationException::class.java) {
            VoiceExtractionParser.parseResponse(truncated, "raw", context)
        }
    }

    private fun validContent(
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
