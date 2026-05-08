package dev.spikeysanju.expensetracker.voice.model

data class OpenRouterModelOption(
    val id: String,
    val label: String,
    val description: String?,
    val promptPrice: String?,
    val completionPrice: String?,
    val contextLength: Int?,
    val supportsStructuredOutputs: Boolean,
    val supportsResponseFormat: Boolean,
    val isFree: Boolean
) {
    fun matchesSelection(modelId: String?): Boolean {
        return id.equals(modelId, ignoreCase = true)
    }

    override fun toString(): String {
        return "$label ($id)"
    }

    fun displaySubtitle(): String {
        val price = when {
            isFree -> "Free"
            !promptPrice.isNullOrBlank() || !completionPrice.isNullOrBlank() -> {
                "Prompt ${promptPrice ?: "-"} • Completion ${completionPrice ?: "-"}"
            }
            else -> null
        }
        val context = contextLength?.let { "Context $it" }
        return listOfNotNull(description?.takeIf { it.isNotBlank() }, price, context)
            .joinToString(separator = " • ")
    }
}

data class SpeechTranscriptSegment(
    val startSeconds: Double?,
    val endSeconds: Double?,
    val text: String
)

data class SpeechTranscript(
    val rawText: String,
    val requestedLanguageCode: String?,
    val detectedLanguageCode: String?,
    val durationSeconds: Double?,
    val segments: List<SpeechTranscriptSegment>
)

data class VoiceTransactionExtraction(
    val title: String?,
    val amount: Double?,
    val transactionType: String?,
    val tag: String?,
    val tagHint: String?,
    val dateIso: String?,
    val note: String?,
    val missingFields: List<String>,
    val rawTranscript: String
)

data class VoiceTransactionDraft(
    val title: String?,
    val amount: Double?,
    val transactionType: String?,
    val tag: String?,
    val date: String?,
    val note: String?,
    val tagHint: String?,
    val rawTranscript: String,
    val missingFields: List<String>
)

data class VoiceExtractionContext(
    val currentDateIso: String,
    val currentDateDisplay: String,
    val timezoneId: String,
    val speechLanguageCode: String?,
    val speechLanguageLabel: String,
    val selectedCurrencyCode: String,
    val selectedCurrencySymbol: String,
    val allowedTransactionTypes: List<String>,
    val allowedTagsByType: Map<String, List<String>>
)

data class VoiceTransactionResult(
    val transcript: SpeechTranscript,
    val extraction: VoiceTransactionExtraction,
    val draft: VoiceTransactionDraft
)

data class VoiceConnectionTestResult(
    val groqReachable: Boolean,
    val openRouterReachable: Boolean
) {
    val isSuccessful: Boolean
        get() = groqReachable && openRouterReachable
}

enum class VoiceProcessingStage {
    Transcribing,
    Parsing
}
