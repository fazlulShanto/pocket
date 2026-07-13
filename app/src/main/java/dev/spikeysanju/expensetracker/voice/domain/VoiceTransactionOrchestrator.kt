package dev.spikeysanju.expensetracker.voice.domain

import dev.spikeysanju.expensetracker.voice.data.remote.GroqService
import dev.spikeysanju.expensetracker.voice.model.VoiceExtractionContext
import dev.spikeysanju.expensetracker.voice.model.VoiceProcessingStage
import dev.spikeysanju.expensetracker.voice.model.VoiceSettingsConfig
import dev.spikeysanju.expensetracker.voice.model.VoiceTransactionResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceTransactionOrchestrator @Inject constructor(
    private val groqService: GroqService,
    private val voiceDraftMapper: VoiceDraftMapper
) {
    suspend fun createDraft(
        audioFile: File,
        config: VoiceSettingsConfig,
        context: VoiceExtractionContext,
        onStageChanged: (VoiceProcessingStage) -> Unit = {}
    ): VoiceTransactionResult {
        val missingRequirements = config.missingRequirements()
        if (missingRequirements.isNotEmpty()) {
            throw IllegalStateException(
                "Voice settings are incomplete: ${missingRequirements.joinToString(separator = ", ")}."
            )
        }

        val selectedSpeechLanguage = config.selectedSpeechLanguage()
        val extractionContext = context.copy(
            speechLanguageCode = selectedSpeechLanguage.code,
            speechLanguageLabel = selectedSpeechLanguage.label
        )
        onStageChanged(VoiceProcessingStage.Transcribing)
        val transcript = groqService.transcribe(
            apiKey = config.groqApiKey,
            audioFile = audioFile,
            requestedLanguageCode = selectedSpeechLanguage.code,
            prompt = VoicePromptFactory.buildTranscriptionPrompt(
                currencyCode = extractionContext.selectedCurrencyCode,
                currencySymbol = extractionContext.selectedCurrencySymbol,
                allowedTagsByType = extractionContext.allowedTagsByType
            )
        )
        onStageChanged(VoiceProcessingStage.Parsing)
        val extraction = groqService.extractTransaction(
            apiKey = config.groqApiKey,
            modelId = config.reasoningModelId,
            transcript = transcript.rawText,
            context = extractionContext
        ).copy(rawTranscript = transcript.rawText)
        val draft = voiceDraftMapper.map(extraction, extractionContext)

        return VoiceTransactionResult(
            transcript = transcript,
            extraction = extraction,
            draft = draft
        )
    }
}
