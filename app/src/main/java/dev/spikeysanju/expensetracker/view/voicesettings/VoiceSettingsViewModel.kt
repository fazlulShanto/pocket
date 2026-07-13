package dev.spikeysanju.expensetracker.view.voicesettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.spikeysanju.expensetracker.voice.data.local.VoiceConfigStore
import dev.spikeysanju.expensetracker.voice.data.remote.GroqService
import dev.spikeysanju.expensetracker.voice.model.GroqReasoningModels
import dev.spikeysanju.expensetracker.voice.model.SupportedSpeechLanguage
import dev.spikeysanju.expensetracker.voice.model.VoiceSettingsConfig
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val voiceConfigStore: VoiceConfigStore,
    private val groqService: GroqService
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceSettingsUiState())
    val uiState: StateFlow<VoiceSettingsUiState> = _uiState

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        loadSettings()
    }

    fun onGroqApiKeyChanged(value: String) {
        _uiState.update { current -> current.copy(groqApiKey = value) }
    }

    fun onReasoningModelChanged(value: String) {
        _uiState.update { current -> current.copy(selectedModelId = value) }
    }

    fun onSpeechLanguageSelected(language: SupportedSpeechLanguage) {
        _uiState.update { current -> current.copy(selectedSpeechLanguage = language) }
    }

    fun saveSettings() {
        val config = _uiState.value.asConfig()
        val missingRequirements = config.missingRequirements()
        if (missingRequirements.isNotEmpty()) {
            _messages.tryEmit(
                "Voice settings are incomplete: " +
                    missingRequirements.joinToString(separator = ", ") +
                    "."
            )
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                voiceConfigStore.saveConfig(config)
                _messages.emit("Voice settings saved.")
            } catch (error: Throwable) {
                _messages.emit(error.message ?: "Failed to save voice settings.")
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun testConnection() {
        val currentState = _uiState.value
        if (currentState.groqApiKey.isBlank() || currentState.selectedModelId.isBlank()) {
            _messages.tryEmit("Add a Groq API key and model ID before testing the connection.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true) }
            try {
                val accessible = groqService.testModelAccess(
                    apiKey = currentState.groqApiKey,
                    modelId = currentState.selectedModelId
                )
                _messages.emit(
                    if (accessible) {
                        "Groq is reachable and the selected model is available."
                    } else {
                        "Groq returned unexpected details for the selected model."
                    }
                )
            } catch (error: Throwable) {
                _messages.emit(error.message ?: "Groq connection test failed.")
            } finally {
                _uiState.update { it.copy(isTestingConnection = false) }
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val config = voiceConfigStore.getConfig()
                _uiState.value = VoiceSettingsUiState(
                    isLoading = false,
                    groqApiKey = config.groqApiKey,
                    selectedModelId = config.reasoningModelId,
                    selectedSpeechLanguage = config.selectedSpeechLanguage()
                )
            } catch (error: Throwable) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                _messages.emit(error.message ?: "Failed to load voice settings.")
            }
        }
    }
}

data class VoiceSettingsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isTestingConnection: Boolean = false,
    val groqApiKey: String = "",
    val selectedModelId: String = GroqReasoningModels.DEFAULT_MODEL_ID,
    val selectedSpeechLanguage: SupportedSpeechLanguage = SupportedSpeechLanguage.AUTO_DETECT
) {
    val isBusy: Boolean
        get() = isLoading || isSaving || isTestingConnection

    fun asConfig(): VoiceSettingsConfig {
        return VoiceSettingsConfig(
            groqApiKey = groqApiKey,
            reasoningModelId = selectedModelId,
            speechLanguageCode = selectedSpeechLanguage.code,
            speechLanguageLabel = selectedSpeechLanguage.label
        )
    }
}
