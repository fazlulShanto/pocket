package dev.spikeysanju.expensetracker.view.voicesettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.spikeysanju.expensetracker.voice.data.local.VoiceConfigStore
import dev.spikeysanju.expensetracker.voice.data.remote.OpenRouterService
import dev.spikeysanju.expensetracker.voice.domain.VoiceConnectionTester
import dev.spikeysanju.expensetracker.voice.model.OpenRouterModelOption
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
    private val openRouterService: OpenRouterService,
    private val voiceConnectionTester: VoiceConnectionTester
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceSettingsUiState())
    val uiState: StateFlow<VoiceSettingsUiState> = _uiState

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        loadSettings()
    }

    fun onGroqApiKeyChanged(value: String) {
        _uiState.update { current ->
            current.copy(groqApiKey = value)
        }
    }

    fun onOpenRouterApiKeyChanged(value: String) {
        _uiState.update { current ->
            current.copy(openRouterApiKey = value)
        }
    }

    fun onSpeechLanguageSelected(language: SupportedSpeechLanguage) {
        _uiState.update { current ->
            current.copy(selectedSpeechLanguage = language)
        }
    }

    fun onReasoningModelSelected(model: OpenRouterModelOption?) {
        _uiState.update { current ->
            current.copy(
                selectedModelId = model?.id,
                selectedModelLabel = model?.label
            )
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                voiceConfigStore.saveConfig(_uiState.value.asConfig())
                _messages.emit("Voice settings saved.")
            } catch (error: Throwable) {
                _messages.emit(error.message ?: "Failed to save voice settings.")
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun refreshModels() {
        val apiKey = _uiState.value.openRouterApiKey.trim()
        if (apiKey.isBlank()) {
            _messages.tryEmit("Add an OpenRouter API key before refreshing models.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingModels = true) }
            try {
                val refreshedModels = openRouterService.fetchCompatibleModels(apiKey)
                val previousSelectionId = _uiState.value.selectedModelId
                val preservedSelection = refreshedModels.firstOrNull { model ->
                    model.matchesSelection(previousSelectionId)
                }
                val refreshedAt = System.currentTimeMillis()
                _uiState.update { current ->
                    current.copy(
                        availableModels = refreshedModels,
                        selectedModelId = preservedSelection?.id,
                        selectedModelLabel = preservedSelection?.label,
                        lastModelRefreshAt = refreshedAt
                    )
                }
                voiceConfigStore.saveConfig(_uiState.value.asConfig())

                if (refreshedModels.isEmpty()) {
                    _messages.emit("No compatible structured-output models were returned by OpenRouter.")
                } else if (previousSelectionId != null && preservedSelection == null) {
                    _messages.emit("The previously selected model is no longer compatible. Choose a new model.")
                } else {
                    _messages.emit("Refreshed ${refreshedModels.size} compatible models.")
                }
            } catch (error: Throwable) {
                _messages.emit(error.message ?: "Failed to refresh OpenRouter models.")
            } finally {
                _uiState.update { it.copy(isRefreshingModels = false) }
            }
        }
    }

    fun testConnections() {
        val currentState = _uiState.value
        if (currentState.groqApiKey.isBlank() || currentState.openRouterApiKey.isBlank()) {
            _messages.tryEmit("Add both API keys before testing the voice connections.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnections = true) }
            try {
                val result = voiceConnectionTester.testConnections(
                    groqApiKey = currentState.groqApiKey,
                    openRouterApiKey = currentState.openRouterApiKey
                )
                val message = when {
                    result.isSuccessful -> "Groq and OpenRouter are reachable with the current keys."
                    result.groqReachable -> "Groq is reachable, but OpenRouter rejected the current key."
                    result.openRouterReachable -> "OpenRouter is reachable, but Groq rejected the current key."
                    else -> "Both voice providers rejected the current keys."
                }
                _messages.emit(message)
            } catch (error: Throwable) {
                _messages.emit(error.message ?: "Voice connection test failed.")
            } finally {
                _uiState.update { it.copy(isTestingConnections = false) }
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
                    openRouterApiKey = config.openRouterApiKey,
                    selectedModelId = config.reasoningModelId,
                    selectedModelLabel = config.reasoningModelLabel,
                    selectedSpeechLanguage = config.selectedSpeechLanguage(),
                    lastModelRefreshAt = config.lastModelRefreshAt
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
    val isRefreshingModels: Boolean = false,
    val isTestingConnections: Boolean = false,
    val groqApiKey: String = "",
    val openRouterApiKey: String = "",
    val availableModels: List<OpenRouterModelOption> = emptyList(),
    val selectedModelId: String? = null,
    val selectedModelLabel: String? = null,
    val selectedSpeechLanguage: SupportedSpeechLanguage = SupportedSpeechLanguage.AUTO_DETECT,
    val lastModelRefreshAt: Long? = null
) {
    val isBusy: Boolean
        get() = isLoading || isSaving || isRefreshingModels || isTestingConnections

    fun asConfig(): VoiceSettingsConfig {
        return VoiceSettingsConfig(
            groqApiKey = groqApiKey,
            openRouterApiKey = openRouterApiKey,
            reasoningModelId = selectedModelId,
            reasoningModelLabel = selectedModelLabel,
            speechLanguageCode = selectedSpeechLanguage.code,
            speechLanguageLabel = selectedSpeechLanguage.label,
            lastModelRefreshAt = lastModelRefreshAt
        )
    }
}
