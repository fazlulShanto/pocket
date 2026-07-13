package dev.spikeysanju.expensetracker.voice.model

data class VoiceSettingsConfig(
    val groqApiKey: String = "",
    val reasoningModelId: String = GroqReasoningModels.DEFAULT_MODEL_ID,
    val speechLanguageCode: String? = null,
    val speechLanguageLabel: String = SupportedSpeechLanguage.AUTO_DETECT.label
) {
    fun selectedSpeechLanguage(): SupportedSpeechLanguage {
        return SupportedSpeechLanguage.fromCodeOrLabel(speechLanguageCode, speechLanguageLabel)
    }

    fun canStartVoiceEntry(): Boolean {
        return groqApiKey.isNotBlank() &&
            reasoningModelId.isNotBlank()
    }

    fun missingRequirements(): List<String> {
        val missing = mutableListOf<String>()
        if (groqApiKey.isBlank()) {
            missing += "Groq API key"
        }
        if (reasoningModelId.isBlank()) {
            missing += "reasoning model"
        }
        return missing
    }
}
