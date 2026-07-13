package dev.spikeysanju.expensetracker.view.add

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.spikeysanju.expensetracker.voice.audio.VoiceActivityDetector
import dev.spikeysanju.expensetracker.voice.audio.WavWriter
import dev.spikeysanju.expensetracker.voice.data.local.VoiceConfigStore
import dev.spikeysanju.expensetracker.voice.debug.VoiceDebugTraceStore
import dev.spikeysanju.expensetracker.voice.domain.VoiceTransactionOrchestrator
import dev.spikeysanju.expensetracker.voice.model.VoiceExtractionContext
import dev.spikeysanju.expensetracker.voice.model.VoiceProcessingStage
import dev.spikeysanju.expensetracker.voice.model.VoiceTransactionDraft
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AddTransactionVoiceViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val voiceConfigStore: VoiceConfigStore,
    private val voiceTransactionOrchestrator: VoiceTransactionOrchestrator
) : ViewModel() {

    private val voiceActivityDetector = VoiceActivityDetector(::handleSpeechDetected)
    private var pendingContext: VoiceExtractionContext? = null

    private val _uiState = MutableStateFlow(AddTransactionVoiceUiState())
    val uiState: StateFlow<AddTransactionVoiceUiState> = _uiState

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _drafts = MutableSharedFlow<VoiceTransactionDraft>()
    val drafts: SharedFlow<VoiceTransactionDraft> = _drafts.asSharedFlow()

    init {
        observeDetectorState()
    }

    fun startVoiceCapture(context: VoiceExtractionContext) {
        if (_uiState.value.isWorking) {
            return
        }

        pendingContext = context
        viewModelScope.launch {
            val config = voiceConfigStore.getConfig()
            val missingRequirements = config.missingRequirements()
            if (missingRequirements.isNotEmpty()) {
                _uiState.value = AddTransactionVoiceUiState(
                    stage = VoiceEntryStage.Error,
                    errorMessage = "Configure ${missingRequirements.joinToString(separator = ", ")} before using voice entry."
                )
                _messages.emit(_uiState.value.errorMessage.orEmpty())
                return@launch
            }

            try {
                voiceActivityDetector.startListening()
                _uiState.value = AddTransactionVoiceUiState(stage = VoiceEntryStage.Listening)
            } catch (error: Throwable) {
                val message = error.message ?: "Unable to start voice entry."
                _uiState.value = AddTransactionVoiceUiState(
                    stage = VoiceEntryStage.Error,
                    errorMessage = message
                )
                _messages.emit(message)
            }
        }
    }

    fun completeVoiceCapture() {
        val didCaptureSpeech = voiceActivityDetector.stopListening(finalizePendingSpeech = true)
        _uiState.update { current ->
            when {
                didCaptureSpeech -> current.copy(stage = VoiceEntryStage.Transcribing)
                current.stage == VoiceEntryStage.Ready -> current
                else -> AddTransactionVoiceUiState()
            }
        }
    }

    fun cancelVoiceCapture() {
        voiceActivityDetector.stopListening()
        _uiState.update { current ->
            if (current.stage == VoiceEntryStage.Ready) {
                current
            } else {
                AddTransactionVoiceUiState()
            }
        }
    }

    fun stopVoiceCapture() {
        cancelVoiceCapture()
    }

    private fun observeDetectorState() {
        viewModelScope.launch {
            voiceActivityDetector.isListening.collect { isListening ->
                _uiState.update { current ->
                    when {
                        isListening && current.stage == VoiceEntryStage.Idle -> {
                            current.copy(stage = VoiceEntryStage.Listening)
                        }
                        !isListening && current.stage in setOf(VoiceEntryStage.Listening, VoiceEntryStage.Speaking) -> {
                            current.copy(stage = VoiceEntryStage.Idle)
                        }
                        else -> current
                    }
                }
            }
        }

        viewModelScope.launch {
            voiceActivityDetector.isSpeaking.collect { isSpeaking ->
                _uiState.update { current ->
                    when {
                        isSpeaking && current.stage == VoiceEntryStage.Listening -> {
                            current.copy(stage = VoiceEntryStage.Speaking)
                        }
                        !isSpeaking && current.stage == VoiceEntryStage.Speaking -> {
                            current.copy(stage = VoiceEntryStage.Listening)
                        }
                        else -> current
                    }
                }
            }
        }
    }

    private fun handleSpeechDetected(audioData: ByteArray) {
        VoiceDebugTraceStore.clear()
        VoiceDebugTraceStore.append(
            section = "VOICE CAPTURE",
            details = "pcmByteCount=${audioData.size}\nsampleRate=${VoiceActivityDetector.SAMPLE_RATE}"
        )
        _uiState.update { current ->
            current.copy(
                stage = VoiceEntryStage.Transcribing,
                errorMessage = null,
                debugDetails = null
            )
        }
        viewModelScope.launch {
            val context = pendingContext
            if (context == null) {
                _uiState.value = AddTransactionVoiceUiState(
                    stage = VoiceEntryStage.Error,
                    errorMessage = "Voice entry context is unavailable."
                )
                _messages.emit(_uiState.value.errorMessage.orEmpty())
                return@launch
            }

            val tempFile = File(appContext.cacheDir, "voice_transaction_${System.currentTimeMillis()}.wav")
            try {
                WavWriter.write(audioData, VoiceActivityDetector.SAMPLE_RATE, tempFile)
                val config = voiceConfigStore.getConfig()
                val result = voiceTransactionOrchestrator.createDraft(
                    audioFile = tempFile,
                    config = config,
                    context = context,
                    onStageChanged = { stage ->
                        _uiState.update { current ->
                            current.copy(
                                stage = when (stage) {
                                    VoiceProcessingStage.Transcribing -> VoiceEntryStage.Transcribing
                                    VoiceProcessingStage.Parsing -> VoiceEntryStage.Parsing
                                }
                            )
                        }
                    }
                )
                _uiState.value = AddTransactionVoiceUiState(
                    stage = VoiceEntryStage.Ready,
                    transcript = result.transcript.rawText,
                    missingFields = result.draft.missingFields,
                    tagHint = result.draft.tagHint,
                    debugDetails = VoiceDebugTraceStore.snapshot().takeIf(String::isNotBlank)
                )
                _drafts.emit(result.draft)
                if (result.draft.missingFields.isNotEmpty()) {
                    _messages.emit(
                        "Voice draft is missing ${result.draft.missingFields.joinToString(separator = ", ")}."
                    )
                }
            } catch (error: Throwable) {
                val message = error.message ?: "Voice processing failed."
                VoiceDebugTraceStore.append(
                    section = "VOICE FLOW ERROR",
                    details = error.stackTraceToString()
                )
                _uiState.value = AddTransactionVoiceUiState(
                    stage = VoiceEntryStage.Error,
                    errorMessage = message,
                    debugDetails = VoiceDebugTraceStore.snapshot().takeIf(String::isNotBlank)
                )
                _messages.emit(message)
            } finally {
                tempFile.delete()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceActivityDetector.destroy()
    }
}

enum class VoiceEntryStage {
    Idle,
    Listening,
    Speaking,
    Transcribing,
    Parsing,
    Ready,
    Error
}

data class AddTransactionVoiceUiState(
    val stage: VoiceEntryStage = VoiceEntryStage.Idle,
    val transcript: String? = null,
    val missingFields: List<String> = emptyList(),
    val tagHint: String? = null,
    val errorMessage: String? = null,
    val debugDetails: String? = null
) {
    val isWorking: Boolean
        get() = stage in setOf(
            VoiceEntryStage.Listening,
            VoiceEntryStage.Speaking,
            VoiceEntryStage.Transcribing,
            VoiceEntryStage.Parsing
        )
}
