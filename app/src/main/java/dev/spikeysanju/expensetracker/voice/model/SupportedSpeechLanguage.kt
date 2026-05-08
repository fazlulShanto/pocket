package dev.spikeysanju.expensetracker.voice.model

data class SupportedSpeechLanguage(
    val code: String?,
    val label: String
) {
    override fun toString(): String = label

    companion object {
        val AUTO_DETECT = SupportedSpeechLanguage(code = null, label = "Auto detect")

        val ALL = listOf(
            AUTO_DETECT,
            SupportedSpeechLanguage(code = "en", label = "English"),
            SupportedSpeechLanguage(code = "bn", label = "Bangla"),
            SupportedSpeechLanguage(code = "hi", label = "Hindi"),
            SupportedSpeechLanguage(code = "ar", label = "Arabic"),
            SupportedSpeechLanguage(code = "es", label = "Spanish"),
            SupportedSpeechLanguage(code = "fr", label = "French"),
            SupportedSpeechLanguage(code = "de", label = "German"),
            SupportedSpeechLanguage(code = "id", label = "Indonesian"),
            SupportedSpeechLanguage(code = "ja", label = "Japanese"),
            SupportedSpeechLanguage(code = "ko", label = "Korean"),
            SupportedSpeechLanguage(code = "pt", label = "Portuguese"),
            SupportedSpeechLanguage(code = "ru", label = "Russian"),
            SupportedSpeechLanguage(code = "tr", label = "Turkish"),
            SupportedSpeechLanguage(code = "ur", label = "Urdu"),
            SupportedSpeechLanguage(code = "vi", label = "Vietnamese"),
            SupportedSpeechLanguage(code = "zh", label = "Chinese"),
        )

        fun fromCodeOrLabel(code: String?, label: String?): SupportedSpeechLanguage {
            return ALL.firstOrNull { language ->
                !code.isNullOrBlank() && language.code.equals(code, ignoreCase = true)
            } ?: ALL.firstOrNull { language ->
                !label.isNullOrBlank() && language.label.equals(label, ignoreCase = true)
            } ?: AUTO_DETECT
        }
    }
}
