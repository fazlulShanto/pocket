package dev.spikeysanju.expensetracker.voice.data.local

import dev.spikeysanju.expensetracker.voice.model.VoiceSettingsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceConfigPreferencesMapperTest {
    @Test
    fun `preference snapshot round trip preserves voice settings`() {
        val config = VoiceSettingsConfig(
            groqApiKey = "groq-key",
            openRouterApiKey = "openrouter-key",
            reasoningModelId = "openrouter/model-a",
            reasoningModelLabel = "Model A",
            speechLanguageCode = "bn",
            speechLanguageLabel = "Bangla",
            lastModelRefreshAt = 1_715_171_717_000
        )

        val restored = VoiceConfigPreferencesMapper.fromPreferenceSnapshot(
            VoiceConfigPreferencesMapper.toPreferenceSnapshot(config)
        )

        assertEquals(config.groqApiKey, restored.groqApiKey)
        assertEquals(config.openRouterApiKey, restored.openRouterApiKey)
        assertEquals(config.reasoningModelId, restored.reasoningModelId)
        assertEquals(config.reasoningModelLabel, restored.reasoningModelLabel)
        assertEquals(config.speechLanguageCode, restored.speechLanguageCode)
        assertEquals(config.speechLanguageLabel, restored.speechLanguageLabel)
        assertEquals(config.lastModelRefreshAt, restored.lastModelRefreshAt)
    }

    @Test
    fun `missing speech language falls back to auto detect`() {
        val restored = VoiceConfigPreferencesMapper.fromPreferenceSnapshot(
            mapOf(
                VoiceConfigPreferencesMapper.KEY_GROQ_API_KEY to "groq-key",
                VoiceConfigPreferencesMapper.KEY_OPEN_ROUTER_API_KEY to "openrouter-key",
                VoiceConfigPreferencesMapper.KEY_REASONING_MODEL_ID to "model-id",
                VoiceConfigPreferencesMapper.KEY_REASONING_MODEL_LABEL to "Model label"
            )
        )

        assertNull(restored.speechLanguageCode)
        assertEquals("Auto detect", restored.speechLanguageLabel)
    }
}
