package dev.spikeysanju.expensetracker.voice.data.local

import dev.spikeysanju.expensetracker.voice.model.SupportedSpeechLanguage
import dev.spikeysanju.expensetracker.voice.model.VoiceSettingsConfig

internal object VoiceConfigPreferencesMapper {
    const val KEY_GROQ_API_KEY = "voice_groq_api_key"
    const val KEY_OPEN_ROUTER_API_KEY = "voice_openrouter_api_key"
    const val KEY_REASONING_MODEL_ID = "voice_reasoning_model_id"
    const val KEY_REASONING_MODEL_LABEL = "voice_reasoning_model_label"
    const val KEY_SPEECH_LANGUAGE_CODE = "voice_speech_language_code"
    const val KEY_SPEECH_LANGUAGE_LABEL = "voice_speech_language_label"
    const val KEY_LAST_MODEL_REFRESH_AT = "voice_last_model_refresh_at"

    val allKeys = listOf(
        KEY_GROQ_API_KEY,
        KEY_OPEN_ROUTER_API_KEY,
        KEY_REASONING_MODEL_ID,
        KEY_REASONING_MODEL_LABEL,
        KEY_SPEECH_LANGUAGE_CODE,
        KEY_SPEECH_LANGUAGE_LABEL,
        KEY_LAST_MODEL_REFRESH_AT,
    )

    fun fromPreferenceSnapshot(snapshot: Map<String, String?>): VoiceSettingsConfig {
        val language = SupportedSpeechLanguage.fromCodeOrLabel(
            code = snapshot[KEY_SPEECH_LANGUAGE_CODE],
            label = snapshot[KEY_SPEECH_LANGUAGE_LABEL]
        )
        return VoiceSettingsConfig(
            groqApiKey = snapshot[KEY_GROQ_API_KEY].orEmpty(),
            openRouterApiKey = snapshot[KEY_OPEN_ROUTER_API_KEY].orEmpty(),
            reasoningModelId = snapshot[KEY_REASONING_MODEL_ID]?.takeIf { it.isNotBlank() },
            reasoningModelLabel = snapshot[KEY_REASONING_MODEL_LABEL]?.takeIf { it.isNotBlank() },
            speechLanguageCode = language.code,
            speechLanguageLabel = language.label,
            lastModelRefreshAt = snapshot[KEY_LAST_MODEL_REFRESH_AT]?.toLongOrNull(),
        )
    }

    fun toPreferenceSnapshot(config: VoiceSettingsConfig): Map<String, String?> {
        val language = config.selectedSpeechLanguage()
        return linkedMapOf(
            KEY_GROQ_API_KEY to config.groqApiKey.trim(),
            KEY_OPEN_ROUTER_API_KEY to config.openRouterApiKey.trim(),
            KEY_REASONING_MODEL_ID to config.reasoningModelId?.trim().orEmpty().ifBlank { null },
            KEY_REASONING_MODEL_LABEL to config.reasoningModelLabel?.trim().orEmpty().ifBlank { null },
            KEY_SPEECH_LANGUAGE_CODE to language.code,
            KEY_SPEECH_LANGUAGE_LABEL to language.label,
            KEY_LAST_MODEL_REFRESH_AT to config.lastModelRefreshAt?.toString(),
        )
    }
}
