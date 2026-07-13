package dev.spikeysanju.expensetracker.voice.data.local

import dev.spikeysanju.expensetracker.voice.model.GroqReasoningModels
import dev.spikeysanju.expensetracker.voice.model.VoiceSettingsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceConfigPreferencesMapperTest {
    @Test
    fun `groq preference snapshot preserves an arbitrary model`() {
        val config = VoiceSettingsConfig(
            groqApiKey = "groq-key",
            reasoningModelId = "custom/model-id",
            speechLanguageCode = "bn",
            speechLanguageLabel = "Bangla"
        )

        val snapshot = VoiceConfigPreferencesMapper.toPreferenceSnapshot(config)
        val restored = VoiceConfigPreferencesMapper.fromPreferenceSnapshot(snapshot)

        assertEquals(config, restored)
        assertEquals("groq", snapshot[VoiceConfigPreferencesMapper.KEY_REASONING_PROVIDER])
        assertFalse(VoiceConfigPreferencesMapper.requiresMigration(snapshot))
    }

    @Test
    fun `legacy openrouter snapshot keeps groq key and language but resets model`() {
        val snapshot = mapOf(
            VoiceConfigPreferencesMapper.KEY_GROQ_API_KEY to "groq-key",
            VoiceConfigPreferencesMapper.KEY_OPEN_ROUTER_API_KEY to "old-openrouter-key",
            VoiceConfigPreferencesMapper.KEY_REASONING_MODEL_ID to "openrouter/model",
            VoiceConfigPreferencesMapper.KEY_REASONING_MODEL_LABEL to "Old model",
            VoiceConfigPreferencesMapper.KEY_SPEECH_LANGUAGE_CODE to "bn",
            VoiceConfigPreferencesMapper.KEY_SPEECH_LANGUAGE_LABEL to "Bangla"
        )

        val restored = VoiceConfigPreferencesMapper.fromPreferenceSnapshot(snapshot)

        assertEquals("groq-key", restored.groqApiKey)
        assertEquals(GroqReasoningModels.DEFAULT_MODEL_ID, restored.reasoningModelId)
        assertEquals("bn", restored.speechLanguageCode)
        assertTrue(VoiceConfigPreferencesMapper.requiresMigration(snapshot))
        val migrated = VoiceConfigPreferencesMapper.toPreferenceSnapshot(restored)
        assertNull(migrated[VoiceConfigPreferencesMapper.KEY_OPEN_ROUTER_API_KEY])
    }

    @Test
    fun `missing speech language falls back to auto detect`() {
        val restored = VoiceConfigPreferencesMapper.fromPreferenceSnapshot(
            mapOf(
                VoiceConfigPreferencesMapper.KEY_GROQ_API_KEY to "groq-key",
                VoiceConfigPreferencesMapper.KEY_REASONING_PROVIDER to "groq",
                VoiceConfigPreferencesMapper.KEY_REASONING_MODEL_ID to "model-id"
            )
        )

        assertNull(restored.speechLanguageCode)
        assertEquals("Auto detect", restored.speechLanguageLabel)
    }
}
