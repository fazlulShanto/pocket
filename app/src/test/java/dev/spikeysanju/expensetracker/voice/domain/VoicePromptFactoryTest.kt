package dev.spikeysanju.expensetracker.voice.domain

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
    fun `correction prompt includes validation details and tagged only instruction`() {
        val prompt = VoicePromptFactory.buildExtractionCorrectionPrompt("amount is invalid")

        assertTrue(prompt.contains("amount is invalid"))
        assertTrue(prompt.contains("Return tagged text only"))
    }
}
