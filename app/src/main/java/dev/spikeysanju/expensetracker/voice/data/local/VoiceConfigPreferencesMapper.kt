package dev.spikeysanju.expensetracker.voice.data.local

import dev.spikeysanju.expensetracker.voice.model.GroqReasoningModels
import dev.spikeysanju.expensetracker.voice.model.SupportedSpeechLanguage
import dev.spikeysanju.expensetracker.voice.model.VoiceSettingsConfig

internal object VoiceConfigPreferencesMapper {
    const val KEY_GROQ_API_KEY = "voice_groq_api_key"
    const val KEY_REASONING_MODEL_ID = "voice_reasoning_model_id"
    const val KEY_REASONING_PROVIDER = "voice_reasoning_provider"
    const val KEY_SPEECH_LANGUAGE_CODE = "voice_speech_language_code"
    const val KEY_SPEECH_LANGUAGE_LABEL = "voice_speech_language_label"

    const val KEY_OPEN_ROUTER_API_KEY = "voice_openrouter_api_key"
    const val KEY_REASONING_MODEL_LABEL = "voice_reasoning_model_label"
    const val KEY_LAST_MODEL_REFRESH_AT = "voice_last_model_refresh_at"

    val allKeys = listOf(
        KEY_GROQ_API_KEY,
        KEY_REASONING_MODEL_ID,
        KEY_REASONING_PROVIDER,
        KEY_SPEECH_LANGUAGE_CODE,
        KEY_SPEECH_LANGUAGE_LABEL,
        KEY_OPEN_ROUTER_API_KEY,
        KEY_REASONING_MODEL_LABEL,
        KEY_LAST_MODEL_REFRESH_AT
    )

    fun fromPreferenceSnapshot(snapshot: Map<String, String?>): VoiceSettingsConfig {
        val language = SupportedSpeechLanguage.fromCodeOrLabel(
            code = snapshot[KEY_SPEECH_LANGUAGE_CODE],
            label = snapshot[KEY_SPEECH_LANGUAGE_LABEL]
        )
        val modelId = if (snapshot[KEY_REASONING_PROVIDER] == GroqReasoningModels.PROVIDER_ID) {
            snapshot[KEY_REASONING_MODEL_ID]
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: GroqReasoningModels.DEFAULT_MODEL_ID
        } else {
            GroqReasoningModels.DEFAULT_MODEL_ID
        }
        return VoiceSettingsConfig(
            groqApiKey = snapshot[KEY_GROQ_API_KEY].orEmpty(),
            reasoningModelId = modelId,
            speechLanguageCode = language.code,
            speechLanguageLabel = language.label
        )
    }

    fun toPreferenceSnapshot(config: VoiceSettingsConfig): Map<String, String?> {
        val language = config.selectedSpeechLanguage()
        return linkedMapOf(
            KEY_GROQ_API_KEY to config.groqApiKey.trim(),
            KEY_REASONING_MODEL_ID to config.reasoningModelId.trim().ifBlank { null },
            KEY_REASONING_PROVIDER to GroqReasoningModels.PROVIDER_ID,
            KEY_SPEECH_LANGUAGE_CODE to language.code,
            KEY_SPEECH_LANGUAGE_LABEL to language.label
        )
    }

    fun requiresMigration(snapshot: Map<String, String?>): Boolean {
        return snapshot[KEY_REASONING_PROVIDER] != GroqReasoningModels.PROVIDER_ID ||
            snapshot[KEY_OPEN_ROUTER_API_KEY] != null ||
            snapshot[KEY_REASONING_MODEL_LABEL] != null ||
            snapshot[KEY_LAST_MODEL_REFRESH_AT] != null
    }
}
