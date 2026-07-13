package dev.spikeysanju.expensetracker.voice.model

object GroqReasoningModels {
    const val PROVIDER_ID = "groq"
    const val DEFAULT_MODEL_ID = "openai/gpt-oss-120b"

    val suggestedModelIds = listOf(
        DEFAULT_MODEL_ID,
        "qwen/qwen3.6-27b",
        "qwen/qwen3-32b",
        "llama-3.3-70b-versatile"
    )

    fun displayLabel(modelId: String): String {
        return when (modelId) {
            DEFAULT_MODEL_ID -> "GPT-OSS 120B"
            "qwen/qwen3.6-27b" -> "Qwen 3.6 27B"
            "qwen/qwen3-32b" -> "Qwen 3 32B"
            "llama-3.3-70b-versatile" -> "Llama 3.3 70B Versatile"
            else -> modelId
        }
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

enum class VoiceProcessingStage {
    Transcribing,
    Parsing
}
