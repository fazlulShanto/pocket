package dev.spikeysanju.expensetracker.view.add

import android.Manifest
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.spikeysanju.expensetracker.R
import dev.spikeysanju.expensetracker.databinding.FragmentAddTransactionBinding
import dev.spikeysanju.expensetracker.model.Tag
import dev.spikeysanju.expensetracker.model.Transaction
import dev.spikeysanju.expensetracker.utils.Constants
import dev.spikeysanju.expensetracker.utils.SupportedCurrency
import dev.spikeysanju.expensetracker.utils.TagKeywordMatcher
import dev.spikeysanju.expensetracker.view.base.BaseFragment
import dev.spikeysanju.expensetracker.view.main.viewmodel.TagViewModel
import dev.spikeysanju.expensetracker.view.main.viewmodel.TransactionViewModel
import dev.spikeysanju.expensetracker.voice.model.SupportedSpeechLanguage
import dev.spikeysanju.expensetracker.voice.model.VoiceExtractionContext
import dev.spikeysanju.expensetracker.voice.model.VoiceTransactionDraft
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch
import parseDouble
import snack
import transformIntoDatePicker

@AndroidEntryPoint
class AddTransactionFragment :
    BaseFragment<FragmentAddTransactionBinding, TransactionViewModel>() {
    override val viewModel: TransactionViewModel by activityViewModels()

    private val tagViewModel: TagViewModel by activityViewModels()
    private val voiceViewModel: AddTransactionVoiceViewModel by viewModels()

    private var availableTags: List<Tag> = emptyList()
    private var selectedCurrency: SupportedCurrency = SupportedCurrency.DEFAULT
    private var shouldStartVoiceCaptureAfterPermission = false

    private val dateFormat by lazy {
        SimpleDateFormat(DATE_PATTERN, Locale.getDefault())
    }
    private val isoDateFormat by lazy {
        SimpleDateFormat(ISO_DATE_PATTERN, Locale.US)
    }

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                if (shouldStartVoiceCaptureAfterPermission) {
                    startVoiceCapture()
                }
            } else {
                Snackbar.make(binding.root, R.string.voice_permission_denied, Snackbar.LENGTH_LONG)
                    .show()
            }
            shouldStartVoiceCaptureAfterPermission = false
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        observeTags()
        observeSelectedCurrency()
        observeVoiceUiState()
        observeVoiceMessages()
        observeVoiceDrafts()
    }

    override fun onDestroyView() {
        voiceViewModel.cancelVoiceCapture()
        super.onDestroyView()
    }

    private fun initViews() {
        val transactionTypeAdapter = ArrayAdapter(
            requireContext(),
            R.layout.item_autocomplete_layout,
            Constants.transactionType
        )

        with(binding) {
            addTransactionLayout.etTransactionType.setAdapter(transactionTypeAdapter)
            addTransactionLayout.etTransactionType.setText("Expense", false)
            addTransactionLayout.etTitle.doAfterTextChanged {
                applyKeywordSuggestionIfNeeded()
            }
            addTransactionLayout.etWhen.transformIntoDatePicker(
                requireContext(),
                DATE_PATTERN,
                Date()
            )
            setTransactionDate(Date())
            setupQuickDateChips()
            renderVoiceState(voiceViewModel.uiState.value)

            addTransactionLayout.etTransactionType.setOnItemClickListener { _, _, _, _ ->
                updateTagsAdapter()
                addTransactionLayout.etTag.text = null
            }

            addTransactionLayout.voiceInputButton.setOnTouchListener { view, event ->
                handleVoiceButtonTouch(view, event)
            }

            btnSaveTransaction.setOnClickListener {
                applyKeywordSuggestionIfNeeded()
                val transaction = getTransactionContent()
                binding.addTransactionLayout.apply {
                    when {
                        transaction.title.isEmpty() -> {
                            etTitle.error = "Title must not be empty"
                        }
                        transaction.amount.isNaN() -> {
                            etAmount.error = "Amount must not be empty"
                        }
                        transaction.transactionType.isEmpty() -> {
                            etTransactionType.error = "Transaction type must not be empty"
                        }
                        transaction.tag.isEmpty() -> {
                            etTag.error = "Tag must not be empty"
                        }
                        transaction.date.isEmpty() -> {
                            etWhen.error = "Date must not be empty"
                        }
                        else -> {
                            viewModel.insertTransaction(transaction).run {
                                binding.root.snack(string = R.string.success_expense_saved)
                                findNavController().navigateUp()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleVoiceButtonTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when (voiceViewModel.uiState.value.stage) {
                    VoiceEntryStage.Transcribing,
                    VoiceEntryStage.Parsing -> Unit

                    else -> {
                        if (hasRecordAudioPermission()) {
                            startVoiceCapture()
                        } else {
                            shouldStartVoiceCaptureAfterPermission = true
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                view.performClick()
                when (voiceViewModel.uiState.value.stage) {
                    VoiceEntryStage.Listening,
                    VoiceEntryStage.Speaking -> voiceViewModel.completeVoiceCapture()
                    else -> Unit
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                when (voiceViewModel.uiState.value.stage) {
                    VoiceEntryStage.Listening,
                    VoiceEntryStage.Speaking -> voiceViewModel.cancelVoiceCapture()
                    else -> Unit
                }
                return true
            }
        }
        return false
    }

    private fun startVoiceCapture() {
        voiceViewModel.startVoiceCapture(buildVoiceExtractionContext())
    }

    private fun setupQuickDateChips() = with(binding.addTransactionLayout) {
        chipYesterday.setOnClickListener {
            setTransactionDate(getRelativeDate(daysOffset = -1))
        }

        chipTwoDaysAgo.setOnClickListener {
            setTransactionDate(getRelativeDate(daysOffset = -2))
        }
    }

    private fun observeTags() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tagViewModel.tags.collect { tags ->
                    availableTags = tags
                    updateTagsAdapter()
                    applyKeywordSuggestionIfNeeded()
                }
            }
        }
    }

    private fun observeSelectedCurrency() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedCurrency.collect { currency ->
                    selectedCurrency = currency
                    binding.addTransactionLayout.etAmountView.prefixText = currency.symbol
                }
            }
        }
    }

    private fun observeVoiceUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                voiceViewModel.uiState.collect(::renderVoiceState)
            }
        }
    }

    private fun observeVoiceMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                voiceViewModel.messages.collect { message ->
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun observeVoiceDrafts() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                voiceViewModel.drafts.collect(::applyVoiceDraft)
            }
        }
    }

    private fun renderVoiceState(state: AddTransactionVoiceUiState) = with(binding.addTransactionLayout) {
        voiceStatusCard.strokeColor = ContextCompat.getColor(
            requireContext(),
            if (state.stage == VoiceEntryStage.Error) R.color.expense else R.color.background
        )
        voiceStatusTitle.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (state.stage == VoiceEntryStage.Error) R.color.expense else R.color.black
            )
        )
        voiceStatusTitle.text = when (state.stage) {
            VoiceEntryStage.Idle -> getString(R.string.voice_status_idle_title)
            VoiceEntryStage.Listening -> getString(R.string.voice_status_listening_title)
            VoiceEntryStage.Speaking -> getString(R.string.voice_status_speaking_title)
            VoiceEntryStage.Transcribing -> getString(R.string.voice_status_transcribing_title)
            VoiceEntryStage.Parsing -> getString(R.string.voice_status_parsing_title)
            VoiceEntryStage.Ready -> getString(R.string.voice_status_ready_title)
            VoiceEntryStage.Error -> getString(R.string.voice_status_error_title)
        }
        voiceStatusDetail.text = when (state.stage) {
            VoiceEntryStage.Idle -> getString(R.string.voice_status_idle_detail)
            VoiceEntryStage.Listening,
            VoiceEntryStage.Speaking -> getString(R.string.voice_status_listening_detail)

            VoiceEntryStage.Transcribing,
            VoiceEntryStage.Parsing -> getString(R.string.voice_status_transcribing_detail)

            VoiceEntryStage.Ready -> state.transcript
                ?.takeIf { it.isNotBlank() }
                ?.let(::buildTranscriptPreview)
                ?: getString(R.string.voice_status_ready_detail)

            VoiceEntryStage.Error -> state.errorMessage ?: getString(R.string.text_error_occurred)
        }
        voiceInputButton.contentDescription = when (state.stage) {
            VoiceEntryStage.Listening,
            VoiceEntryStage.Speaking -> getString(R.string.voice_input_button_stop)

            VoiceEntryStage.Transcribing,
            VoiceEntryStage.Parsing -> getString(R.string.voice_input_button_processing)

            VoiceEntryStage.Ready -> getString(R.string.voice_input_button_retry)
            else -> getString(R.string.voice_input_button_start)
        }
        voiceInputButton.isEnabled = state.stage !in setOf(
            VoiceEntryStage.Transcribing,
            VoiceEntryStage.Parsing
        )
        val buttonStrokeColor = ContextCompat.getColor(
            requireContext(),
            when {
                state.stage == VoiceEntryStage.Error -> R.color.expense
                state.isWorking || state.stage == VoiceEntryStage.Ready -> R.color.blue_500
                else -> R.color.background
            }
        )
        val buttonBackgroundColor = ContextCompat.getColor(
            requireContext(),
            when {
                state.stage == VoiceEntryStage.Error -> R.color.background
                state.isWorking -> R.color.blue_500
                else -> R.color.surface
            }
        )
        val buttonIconColor = ContextCompat.getColor(
            requireContext(),
            when {
                state.isWorking -> R.color.white
                state.stage == VoiceEntryStage.Error -> R.color.expense
                else -> R.color.blue_500
            }
        )
        voiceInputButton.strokeColor = buttonStrokeColor
        voiceInputButton.setCardBackgroundColor(buttonBackgroundColor)
        voiceInputIcon.imageTintList = ColorStateList.valueOf(buttonIconColor)
    }

    private fun buildTranscriptPreview(transcript: String): String {
        return if (transcript.length > TRANSCRIPT_PREVIEW_LIMIT) {
            transcript.take(TRANSCRIPT_PREVIEW_LIMIT - 3).trimEnd() + "..."
        } else {
            transcript
        }
    }

    private fun applyVoiceDraft(draft: VoiceTransactionDraft) = with(binding.addTransactionLayout) {
        draft.title?.let(etTitle::setText)
        draft.amount?.let { amount ->
            etAmount.setText(BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString())
        }
        draft.transactionType?.let { transactionType ->
            etTransactionType.setText(transactionType, false)
            updateTagsAdapter()
        }
        when {
            !draft.tag.isNullOrBlank() -> etTag.setText(draft.tag, false)
            !draft.transactionType.isNullOrBlank() -> etTag.text = null
        }
        draft.date?.let(etWhen::setText)
        draft.note?.takeIf { it.isNotBlank() }?.let(etNote::setText)
        applyKeywordSuggestionIfNeeded()
    }

    private fun applyKeywordSuggestionIfNeeded() = with(binding.addTransactionLayout) {
        if (etTag.text.toString().isNotBlank()) {
            return
        }

        val matchedTag = TagKeywordMatcher.findBestMatch(
            tags = availableTags,
            title = etTitle.text.toString()
        ) ?: return

        if (etTransactionType.text.toString() != matchedTag.tagType) {
            etTransactionType.setText(matchedTag.tagType, false)
            updateTagsAdapter()
        }
        etTag.setText(matchedTag.tagName, false)
    }

    private fun updateTagsAdapter() {
        val transactionType = binding.addTransactionLayout.etTransactionType.text.toString()
        val filteredTags = if (transactionType.isNotEmpty()) {
            availableTags
                .filter { it.tagType == transactionType }
                .map { it.tagName }
                .sorted()
        } else {
            emptyList()
        }
        val tagsAdapter = ArrayAdapter(
            requireContext(),
            R.layout.item_autocomplete_layout,
            filteredTags
        )
        binding.addTransactionLayout.etTag.setAdapter(tagsAdapter)
    }

    private fun buildVoiceExtractionContext(): VoiceExtractionContext {
        val currentDate = Date()
        return VoiceExtractionContext(
            currentDateIso = isoDateFormat.format(currentDate),
            currentDateDisplay = dateFormat.format(currentDate),
            timezoneId = TimeZone.getDefault().id,
            speechLanguageCode = SupportedSpeechLanguage.AUTO_DETECT.code,
            speechLanguageLabel = SupportedSpeechLanguage.AUTO_DETECT.label,
            selectedCurrencyCode = selectedCurrency.code,
            selectedCurrencySymbol = selectedCurrency.symbol,
            allowedTransactionTypes = Constants.transactionType,
            allowedTagsByType = availableTags
                .groupBy { tag -> tag.tagType }
                .mapValues { (_, tags) -> tags.map { it.tagName }.distinct().sorted() }
        )
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getRelativeDate(daysOffset: Int): Date = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, daysOffset)
    }.time

    private fun setTransactionDate(date: Date) {
        binding.addTransactionLayout.etWhen.setText(dateFormat.format(date))
    }

    private fun getTransactionContent(): Transaction = binding.addTransactionLayout.let {
        val title = it.etTitle.text.toString()
        val amount = parseDouble(it.etAmount.text.toString())
        val transactionType = it.etTransactionType.text.toString()
        val tag = it.etTag.text.toString()
        val date = it.etWhen.text.toString()
        val note = it.etNote.text.toString() + ""

        Transaction(title, amount, transactionType, tag, date, note)
    }

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = FragmentAddTransactionBinding.inflate(inflater, container, false)

    private companion object {
        const val DATE_PATTERN = "dd/MM/yyyy"
        const val ISO_DATE_PATTERN = "yyyy-MM-dd"
        const val TRANSCRIPT_PREVIEW_LIMIT = 120
    }
}
