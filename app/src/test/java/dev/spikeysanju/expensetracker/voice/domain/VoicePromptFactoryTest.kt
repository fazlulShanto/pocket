package dev.spikeysanju.expensetracker.voice.domain

import dev.spikeysanju.expensetracker.voice.model.VoiceExtractionContext
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePromptFactoryTest {
    @Test
    fun `extraction prompt declares exact delimiter contract without json schema`() {
        val prompt = VoicePromptFactory.buildExtractionSystemPrompt()

        listOf(
            "===voice_transaction_extraction_start===",
            "===title_start===",
            "===amount_start===",
            "===transaction_type_start===",
            "===tag_start===",
            "===tag_hint_start===",
            "===date_iso_start===",
            "===note_start===",
            "===missing_fields_start===",
            "===missing_field_start===",
            "===voice_transaction_extraction_end==="
        ).forEach { marker -> assertTrue(prompt.contains(marker)) }
        assertTrue(prompt.contains("Return tagged text only"))
        assertFalse(prompt.contains("json_schema"))
        assertFalse(prompt.contains("response_format"))
    }

    @Test
    fun `format template uses unmistakable replaceable placeholders`() {
        val prompt = VoicePromptFactory.buildExtractionSystemPrompt()

        listOf(
            "{{TITLE_OR_EMPTY}}",
            "{{POSITIVE_AMOUNT_OR_EMPTY}}",
            "{{ALLOWED_TRANSACTION_TYPE_OR_EMPTY}}",
            "{{ALLOWED_TAG_OR_EMPTY}}",
            "{{TAG_HINT_OR_EMPTY}}",
            "{{ISO_DATE_OR_EMPTY}}",
            "{{NOTE_OR_EMPTY}}",
            "{{ZERO_OR_MORE_MISSING_FIELD_BLOCKS}}"
        ).forEach { placeholder -> assertTrue(prompt.contains(placeholder)) }
        assertTrue(prompt.contains("Never return the placeholder tokens themselves"))
        assertFalse(prompt.contains("Transaction title or empty"))
    }

    @Test
    fun `critical output contract is first and last and bans markdown`() {
        val prompt = VoicePromptFactory.buildExtractionSystemPrompt()
        val nonBlankLines = prompt.lineSequence().filter(String::isNotBlank).toList()

        assertEquals(nonBlankLines.first(), nonBlankLines.last())
        assertTrue(nonBlankLines.first().contains("tagged text only"))
        assertTrue(nonBlankLines.first().contains("No JSON, markdown code fences, preamble, or commentary"))
        assertTrue(prompt.contains("[TASK]"))
        assertTrue(prompt.contains("[CORRECTION RULES]"))
        assertTrue(prompt.contains("[CONTENT RULES]"))
        assertTrue(prompt.contains("[OUTPUT CONTRACT]"))
        assertTrue(prompt.contains("[WORKED EXAMPLES]"))
    }

    @Test
    fun `prompt includes relative date locale noise and controlled value rules`() {
        val prompt = VoicePromptFactory.buildExtractionSystemPrompt()

        assertTrue(prompt.contains("Resolve relative date expressions"))
        assertTrue(prompt.contains("currentDateIso and timezoneId"))
        assertTrue(prompt.contains("using . as the decimal separator"))
        assertTrue(prompt.contains("no extractable transaction"))
        assertTrue(prompt.contains("every required field in missing_fields"))
        assertTrue(prompt.contains("Only use values present in allowedTransactionTypes and allowedTagsByType"))
    }

    @Test
    fun `prompt provides correction and ambiguity worked examples`() {
        val prompt = VoicePromptFactory.buildExtractionSystemPrompt()

        assertTrue(prompt.contains("[EXAMPLE A: HIGH-CONFIDENCE ASR CORRECTION]"))
        assertTrue(prompt.contains("Lunch at Food Corner"))
        assertTrue(prompt.contains("[EXAMPLE B: PRESERVE AMBIGUITY]"))
        assertTrue(prompt.contains("Zorple"))
        assertTrue(prompt.contains("blenko"))
        assertTrue(prompt.contains("===missing_field_start===\ntag\n===missing_field_end==="))
    }

    @Test
    fun `transcription prompt is bounded spelling context for whisper`() {
        val prompt = VoicePromptFactory.buildTranscriptionPrompt(
            currencyCode = "BDT",
            currencySymbol = "৳",
            allowedTagsByType = mapOf(
                "Expense" to (1..100).map { index -> "Category-$index-with-a-long-name" }
            )
        )

        assertTrue(prompt.startsWith("Personal finance transaction dictation."))
        assertTrue(prompt.contains("Currency spelling: BDT"))
        assertTrue(prompt.contains("Expected category spellings:"))
        assertFalse(prompt.contains("Preserve spoken"))
        assertTrue(prompt.length <= 500)
    }

    @Test
    fun `correction prompt includes validation details and tagged only instruction`() {
        val prompt = VoicePromptFactory.buildExtractionCorrectionPrompt("amount is invalid")

        assertTrue(prompt.contains("amount is invalid"))
        assertTrue(prompt.contains("Return tagged text only"))
    }

    @Test
    fun `extraction prompt treats transcript as noisy speech recognition output`() {
        val prompt = VoicePromptFactory.buildExtractionSystemPrompt()

        assertTrue(prompt.contains("speech-recognition errors"))
        assertTrue(prompt.contains("Correct only high-confidence mistakes"))
        assertTrue(prompt.contains("allowed transaction types and tags"))
        assertTrue(prompt.contains("Do not change a spoken amount or date"))
        assertTrue(prompt.contains("corrected wording in title and note"))
    }

    @Test
    fun `correction prompt rechecks likely transcription mistakes`() {
        val prompt = VoicePromptFactory.buildExtractionCorrectionPrompt("title is missing")

        assertTrue(prompt.contains("Re-check the transcript for likely speech-recognition mistakes"))
        assertTrue(prompt.contains("high confidence"))
    }

    @Test
    fun `user payload identifies transcript as automatic speech recognition output`() {
        val payload = JSONObject(
            VoicePromptFactory.buildExtractionUserPayload(
                transcript = "launch at foot place",
                context = VoiceExtractionContext(
                    currentDateIso = "2026-07-14",
                    currentDateDisplay = "14/07/2026",
                    timezoneId = "Asia/Dhaka",
                    speechLanguageCode = "en",
                    speechLanguageLabel = "English",
                    selectedCurrencyCode = "BDT",
                    selectedCurrencySymbol = "৳",
                    allowedTransactionTypes = listOf("Income", "Expense"),
                    allowedTagsByType = mapOf("Expense" to listOf("Food"))
                )
            )
        )

        assertEquals("automatic_speech_recognition", payload.getString("transcriptSource"))
        assertTrue(payload.getBoolean("transcriptMayContainSpeechRecognitionErrors"))
    }
}
