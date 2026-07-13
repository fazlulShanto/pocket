package dev.spikeysanju.expensetracker.view.voicesettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.spikeysanju.expensetracker.R
import dev.spikeysanju.expensetracker.databinding.FragmentVoiceSettingsBinding
import dev.spikeysanju.expensetracker.voice.model.GroqReasoningModels
import dev.spikeysanju.expensetracker.voice.model.SupportedSpeechLanguage
import dev.spikeysanju.expensetracker.view.base.BaseFragment
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VoiceSettingsFragment : BaseFragment<FragmentVoiceSettingsBinding, VoiceSettingsViewModel>() {
    override val viewModel: VoiceSettingsViewModel by viewModels()

    private lateinit var speechLanguageAdapter: ArrayAdapter<SupportedSpeechLanguage>
    private lateinit var modelAdapter: ArrayAdapter<String>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        observeUiState()
        observeMessages()
    }

    private fun initViews() = with(binding) {
        speechLanguageAdapter = ArrayAdapter(
            requireContext(),
            R.layout.item_autocomplete_layout,
            SupportedSpeechLanguage.ALL
        )
        modelAdapter = ArrayAdapter(
            requireContext(),
            R.layout.item_autocomplete_layout,
            GroqReasoningModels.suggestedModelIds
        )
        speechLanguageDropdown.setAdapter(speechLanguageAdapter)
        reasoningModelDropdown.setAdapter(modelAdapter)

        groqApiKeyInput.doAfterTextChanged { editable ->
            viewModel.onGroqApiKeyChanged(editable?.toString().orEmpty())
        }
        reasoningModelDropdown.doAfterTextChanged { editable ->
            viewModel.onReasoningModelChanged(editable?.toString().orEmpty())
        }
        speechLanguageDropdown.setOnItemClickListener { parent, _, position, _ ->
            val language = parent.getItemAtPosition(position) as SupportedSpeechLanguage
            viewModel.onSpeechLanguageSelected(language)
        }
        testConnectionButton.setOnClickListener { viewModel.testConnection() }
        saveVoiceSettingsButton.setOnClickListener { viewModel.saveSettings() }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun observeMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { message ->
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun render(state: VoiceSettingsUiState) = with(binding) {
        if (groqApiKeyInput.text?.toString() != state.groqApiKey) {
            groqApiKeyInput.setText(state.groqApiKey)
            groqApiKeyInput.setSelection(groqApiKeyInput.text?.length ?: 0)
        }

        if (reasoningModelDropdown.text?.toString() != state.selectedModelId) {
            reasoningModelDropdown.setText(state.selectedModelId, false)
            reasoningModelDropdown.setSelection(reasoningModelDropdown.text?.length ?: 0)
        }
        if (speechLanguageDropdown.text?.toString() != state.selectedSpeechLanguage.label) {
            speechLanguageDropdown.setText(state.selectedSpeechLanguage.label, false)
        }

        voiceSettingsProgress.isVisible = state.isBusy
        voiceSettingsStatusText.text = when {
            state.isLoading -> getString(R.string.voice_settings_loading)
            state.isTestingConnection -> getString(R.string.voice_settings_testing_connection)
            state.isSaving -> getString(R.string.voice_settings_saving)
            else -> getString(R.string.voice_settings_ready)
        }
        testConnectionButton.isEnabled = !state.isBusy
        saveVoiceSettingsButton.isEnabled = !state.isBusy
    }

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = FragmentVoiceSettingsBinding.inflate(inflater, container, false)
}
