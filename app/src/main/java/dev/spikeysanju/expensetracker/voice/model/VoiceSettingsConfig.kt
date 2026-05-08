package dev.spikeysanju.expensetracker.voice.model

data class VoiceSettingsConfig(
    val groqApiKey: String = "",
    val openRouterApiKey: String = "",
    val reasoningModelId: String? = null,
    val reasoningModelLabel: String? = null,
    val speechLanguageCode: String? = null,
    val speechLanguageLabel: String = SupportedSpeechLanguage.AUTO_DETECT.label,
    val lastModelRefreshAt: Long? = null
) {
    fun selectedSpeechLanguage(): SupportedSpeechLanguage {
        return SupportedSpeechLanguage.fromCodeOrLabel(speechLanguageCode, speechLanguageLabel)
    }

    fun canStartVoiceEntry(): Boolean {
        return groqApiKey.isNotBlank() &&
            openRouterApiKey.isNotBlank() &&
            !reasoningModelId.isNullOrBlank()
    }

    fun missingRequirements(): List<String> {
        val missing = mutableListOf<String>()
        if (groqApiKey.isBlank()) {
            missing += "Groq API key"
        }
        if (openRouterApiKey.isBlank()) {
            missing += "OpenRouter API key"
        }
        if (reasoningModelId.isNullOrBlank()) {
            missing += "reasoning model"
        }
        return missing
    }
}
