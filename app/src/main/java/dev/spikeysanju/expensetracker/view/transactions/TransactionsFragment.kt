package dev.spikeysanju.expensetracker.view.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import dev.spikeysanju.expensetracker.R
import dev.spikeysanju.expensetracker.databinding.FragmentTransactionsBinding
import dev.spikeysanju.expensetracker.model.Transaction
import dev.spikeysanju.expensetracker.utils.SupportedCurrency
import dev.spikeysanju.expensetracker.utils.viewState.ViewState
import dev.spikeysanju.expensetracker.view.adapter.TransactionAdapter
import dev.spikeysanju.expensetracker.view.base.BaseFragment
import dev.spikeysanju.expensetracker.view.main.viewmodel.TagViewModel
import dev.spikeysanju.expensetracker.view.main.viewmodel.TransactionViewModel
import hide
import kotlinx.coroutines.flow.collect
import show
import snack
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class TransactionsFragment :
	BaseFragment<FragmentTransactionsBinding, TransactionViewModel>() {
	override val viewModel: TransactionViewModel by activityViewModels()
	private val tagViewModel: TagViewModel by activityViewModels()
	private lateinit var transactionAdapter: TransactionAdapter
	private var allTransactions: List<Transaction> = emptyList()
	private var monthOptions: List<MonthFilterOption> = emptyList()
	private var tagOptions: List<TagFilterOption> = emptyList()
	private var appliedMonthKey: MonthKey? = currentMonthKey()
	private var appliedTag: String? = null
	private var appliedTitleQuery: String = ""
	private var draftMonthKey: MonthKey? = appliedMonthKey
	private var draftTag: String? = appliedTag
	private var draftTitleQuery: String = appliedTitleQuery
	private val inputDateFormatter = SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).apply {
		isLenient = false
	}
	private val monthFormatter = SimpleDateFormat(MONTH_LABEL_PATTERN, Locale.getDefault())

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		setupRV()
		initViews()
		observeTransaction()
		observeTags()
		observeSelectedCurrency()
		viewModel.getAllTransaction(DEFAULT_FILTER)
	}

	private fun setupRV() = with(binding) {
		transactionAdapter = TransactionAdapter()
		transactionsRecyclerView.apply {
			adapter = transactionAdapter
			layoutManager = LinearLayoutManager(activity)
		}
	}

	private fun initViews() {
		setupFilterSheet()
		binding.filterButton.setOnClickListener {
			openFilterSheet()
		}
		binding.clearAllFiltersButton.setOnClickListener {
			clearAppliedFilters()
		}
		binding.sheetCancelButton.setOnClickListener {
			closeFilterSheet()
		}
		binding.sheetApplyButton.setOnClickListener {
			applyDraftFilters()
		}
		transactionAdapter.setOnItemClickListener {
			val bundle = Bundle().apply {
				putSerializable("transaction", it)
			}
			findNavController().navigate(R.id.transactionDetailsFragment, bundle)
		}
	}

	private fun setupFilterSheet() = with(binding) {
		sheetTitleSearchEditText.doAfterTextChanged { editable ->
			draftTitleQuery = editable?.toString().orEmpty()
		}
		sheetTypeFilterDropdown.setOnItemClickListener { _, _, position, _ ->
			draftTag = tagOptions[position].value
		}

		sheetMonthFilterDropdown.setOnItemClickListener { _, _, position, _ ->
			draftMonthKey = monthOptions[position].key
		}

		updateTagOptions()
		updateMonthOptions()
		syncDraftFiltersWithAppliedFilters()
		syncSheetInputsWithDraftFilters()
	}

	private fun observeTransaction() = lifecycleScope.launchWhenStarted {
		viewModel.uiState.collect { uiState ->
			when (uiState) {
				is ViewState.Loading -> {
				}
				is ViewState.Success -> {
					allTransactions = uiState.transaction
					updateMonthOptions()
					updateTagOptions()
					renderFilteredTransactions()
				}
				is ViewState.Error -> {
					binding.root.snack(string = R.string.text_error)
				}
				is ViewState.Empty -> {
					allTransactions = emptyList()
					updateMonthOptions()
					updateTagOptions()
					renderFilteredTransactions()
				}
			}
		}
	}

	private fun observeSelectedCurrency() = lifecycleScope.launchWhenStarted {
		viewModel.selectedCurrency.collect { currency: SupportedCurrency ->
			transactionAdapter.updateCurrency(currency)
		}
	}

	private fun observeTags() = lifecycleScope.launchWhenStarted {
		tagViewModel.tags.collect { tags ->
			transactionAdapter.updateTagMetadata(tags)
		}
	}

	private fun updateMonthOptions() {
		monthOptions = buildMonthOptions(allTransactions)
		binding.sheetMonthFilterDropdown.setAdapter(
			ArrayAdapter(
				requireContext(),
				R.layout.item_autocomplete_layout,
				monthOptions.map { option -> option.label }
			)
		)

		if (appliedMonthKey != null && monthOptions.none { option -> option.key == appliedMonthKey }) {
			appliedMonthKey = currentMonthKey()
		}
		if (draftMonthKey != null && monthOptions.none { option -> option.key == draftMonthKey }) {
			draftMonthKey = appliedMonthKey
		}

		renderAppliedFilterChips()
		syncSheetInputsWithDraftFilters()
	}

	private fun updateTagOptions() {
		tagOptions = buildTagOptions(allTransactions)
		binding.sheetTypeFilterDropdown.setAdapter(
			ArrayAdapter(
				requireContext(),
				R.layout.item_autocomplete_layout,
				tagOptions.map { option -> option.label }
			)
		)

		if (appliedTag != null && tagOptions.none { option -> option.value.equals(appliedTag, ignoreCase = true) }) {
			appliedTag = null
		}
		if (draftTag != null && tagOptions.none { option -> option.value.equals(draftTag, ignoreCase = true) }) {
			draftTag = appliedTag
		}

		renderAppliedFilterChips()
		syncSheetInputsWithDraftFilters()
	}

	private fun renderFilteredTransactions() {
		renderAppliedFilterChips()

		val filteredTransactions = allTransactions.filter { transaction ->
			matchesMonth(transaction) &&
				matchesTag(transaction) &&
				matchesTitle(transaction)
		}

		when {
			filteredTransactions.isNotEmpty() -> {
				binding.transactionsRecyclerView.show()
				binding.emptyStateText.hide()
				transactionAdapter.differ.submitList(filteredTransactions)
			}

			allTransactions.isEmpty() -> {
				binding.transactionsRecyclerView.hide()
				binding.emptyStateText.text = getString(R.string.text_transactions_empty)
				binding.emptyStateText.show()
			}

			else -> {
				binding.transactionsRecyclerView.hide()
				binding.emptyStateText.text = getString(R.string.text_transactions_empty_filtered)
				binding.emptyStateText.show()
			}
		}
	}

	private fun buildMonthOptions(transactions: List<Transaction>): List<MonthFilterOption> {
		val currentMonth = currentMonthKey()
		val transactionMonths = transactions
			.mapNotNull { transaction -> parseMonthKey(transaction.date) }
			.distinct()
			.sortedWith(
				compareByDescending<MonthKey> { monthKey -> monthKey.year }
					.thenByDescending { monthKey -> monthKey.month }
			)

		val monthValues = buildList {
			add(currentMonth)
			addAll(transactionMonths.filterNot { monthKey -> monthKey == currentMonth })
		}

		return listOf(
			MonthFilterOption(
				key = null,
				label = getString(R.string.text_transactions_filter_all_time)
			)
		) + monthValues.map { monthKey ->
			MonthFilterOption(key = monthKey, label = formatMonthLabel(monthKey))
		}
	}

	private fun buildTagOptions(transactions: List<Transaction>): List<TagFilterOption> {
		val tags = transactions
			.map { transaction -> transaction.tag.trim() }
			.filter { tag -> tag.isNotEmpty() }
			.distinctBy { tag -> tag.lowercase(Locale.getDefault()) }
			.sortedBy { tag -> tag.lowercase(Locale.getDefault()) }

		return listOf(
			TagFilterOption(
				value = null,
				label = getString(R.string.text_transactions_filter_all_types)
			)
		) + tags.map { tag ->
			TagFilterOption(value = tag, label = tag)
		}
	}

	private fun matchesMonth(transaction: Transaction): Boolean {
		val monthKey = appliedMonthKey ?: return true
		return parseMonthKey(transaction.date) == monthKey
	}

	private fun matchesTag(transaction: Transaction): Boolean {
		val tag = appliedTag ?: return true
		return transaction.tag.equals(tag, ignoreCase = true)
	}

	private fun matchesTitle(transaction: Transaction): Boolean {
		if (appliedTitleQuery.isBlank()) {
			return true
		}

		return transaction.title.contains(appliedTitleQuery, ignoreCase = true)
	}

	private fun openFilterSheet() {
		syncDraftFiltersWithAppliedFilters()
		syncSheetInputsWithDraftFilters()
		binding.transactionsDrawerLayout.openDrawer(binding.filterSheetContainer)
	}

	private fun closeFilterSheet() {
		binding.transactionsDrawerLayout.closeDrawer(binding.filterSheetContainer)
	}

	private fun applyDraftFilters() {
		appliedMonthKey = draftMonthKey
		appliedTag = draftTag
		appliedTitleQuery = draftTitleQuery.trim()
		closeFilterSheet()
		renderFilteredTransactions()
	}

	private fun clearAppliedFilters() {
		appliedMonthKey = null
		appliedTag = null
		appliedTitleQuery = ""
		syncDraftFiltersWithAppliedFilters()
		syncSheetInputsWithDraftFilters()
		renderFilteredTransactions()
	}

	private fun syncDraftFiltersWithAppliedFilters() {
		draftMonthKey = appliedMonthKey
		draftTag = appliedTag
		draftTitleQuery = appliedTitleQuery
	}

	private fun syncSheetInputsWithDraftFilters() = with(binding) {
		sheetTitleSearchEditText.setText(draftTitleQuery)

		val selectedMonthOption = monthOptions.firstOrNull { option -> option.key == draftMonthKey }
			?: monthOptions.firstOrNull()
		selectedMonthOption?.let { option ->
			sheetMonthFilterDropdown.setText(option.label, false)
			draftMonthKey = option.key
		}

		val selectedTypeOption = tagOptions.firstOrNull { option -> option.value == draftTag }
			?: tagOptions.first()
		binding.sheetTypeFilterDropdown.setText(selectedTypeOption.label, false)
		draftTag = selectedTypeOption.value
	}

	private fun renderAppliedFilterChips() = with(binding) {
		activeFilterChipGroup.removeAllViews()

		appliedMonthKey?.let { monthKey ->
			activeFilterChipGroup.addView(
				createFilterChip(formatMonthLabel(monthKey)) {
					appliedMonthKey = null
					syncDraftFiltersWithAppliedFilters()
					syncSheetInputsWithDraftFilters()
					renderFilteredTransactions()
				}
			)
		}

		appliedTag?.let { tag ->
			val label = tagOptions.firstOrNull { option -> option.value == tag }?.label ?: tag
			activeFilterChipGroup.addView(
				createFilterChip(label) {
					appliedTag = null
					syncDraftFiltersWithAppliedFilters()
					syncSheetInputsWithDraftFilters()
					renderFilteredTransactions()
				}
			)
		}

		if (appliedTitleQuery.isNotBlank()) {
			activeFilterChipGroup.addView(
				createFilterChip(
					getString(R.string.text_transactions_filter_title_chip, appliedTitleQuery)
				) {
					appliedTitleQuery = ""
					syncDraftFiltersWithAppliedFilters()
					syncSheetInputsWithDraftFilters()
					renderFilteredTransactions()
				}
			)
		}

		if (activeFilterChipGroup.childCount == 0) {
			activeFiltersContainer.hide()
		} else {
			activeFiltersContainer.show()
		}
	}

	private fun createFilterChip(label: String, onClear: () -> Unit): Chip {
		return Chip(requireContext()).apply {
			text = label
			isCheckable = false
			isClickable = false
			isCloseIconVisible = true
			setEnsureMinTouchTargetSize(false)
			setOnCloseIconClickListener {
				onClear()
			}
		}
	}

	private fun parseMonthKey(date: String): MonthKey? {
		val parsedDate = runCatching {
			inputDateFormatter.parse(date)
		}.getOrNull() ?: return null

		return Calendar.getInstance().apply {
			time = parsedDate
		}.let { calendar ->
			MonthKey(
				year = calendar.get(Calendar.YEAR),
				month = calendar.get(Calendar.MONTH)
			)
		}
	}

	private fun formatMonthLabel(monthKey: MonthKey): String {
		return Calendar.getInstance().apply {
			clear()
			set(monthKey.year, monthKey.month, 1)
		}.let { calendar ->
			monthFormatter.format(calendar.time)
		}
	}

	private fun currentMonthKey(): MonthKey {
		return Calendar.getInstance().let { calendar ->
			MonthKey(
				year = calendar.get(Calendar.YEAR),
				month = calendar.get(Calendar.MONTH)
			)
		}
	}

	override fun getViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?
	) = FragmentTransactionsBinding.inflate(inflater, container, false)

	private companion object {
		const val DEFAULT_FILTER = "Overall"
		const val DATE_PATTERN = "dd/MM/yyyy"
		const val MONTH_LABEL_PATTERN = "MMMM yyyy"
	}

	private data class MonthKey(
		val year: Int,
		val month: Int
	)

	private data class MonthFilterOption(
		val key: MonthKey?,
		val label: String
	)

	private data class TagFilterOption(
		val value: String?,
		val label: String
	)
}