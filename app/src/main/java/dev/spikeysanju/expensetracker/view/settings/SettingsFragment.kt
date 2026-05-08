package dev.spikeysanju.expensetracker.view.settings

import action
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.spikeysanju.expensetracker.R
import dev.spikeysanju.expensetracker.databinding.FragmentSettingsBinding
import dev.spikeysanju.expensetracker.services.exportcsv.CreateCsvContract
import dev.spikeysanju.expensetracker.services.exportcsv.ImportCsvContract
import dev.spikeysanju.expensetracker.services.exportcsv.OpenCsvContract
import dev.spikeysanju.expensetracker.utils.SupportedCurrency
import dev.spikeysanju.expensetracker.utils.viewState.ClearDataState
import dev.spikeysanju.expensetracker.utils.viewState.ExportState
import dev.spikeysanju.expensetracker.utils.viewState.ImportState
import dev.spikeysanju.expensetracker.view.base.BaseFragment
import dev.spikeysanju.expensetracker.view.main.viewmodel.TransactionViewModel
import kotlinx.coroutines.flow.collect
import snack

@AndroidEntryPoint
class SettingsFragment : BaseFragment<FragmentSettingsBinding, TransactionViewModel>() {
	override val viewModel: TransactionViewModel by activityViewModels()
	private var applyingThemeState = false
	private var awaitingExportResult = false
	private var awaitingImportResult = false
	private var awaitingClearResult = false
	private var selectedCurrency: SupportedCurrency = SupportedCurrency.DEFAULT

	private val csvCreateRequestLauncher =
		registerForActivityResult(CreateCsvContract()) { uri: Uri? ->
			if (uri != null) {
				exportCSV(uri)
			} else {
				awaitingExportResult = false
				binding.root.snack(string = R.string.failed_transaction_export)
			}
		}

	private val previewCsvRequestLauncher = registerForActivityResult(OpenCsvContract()) {}

	private val csvImportRequestLauncher =
		registerForActivityResult(ImportCsvContract()) { uri: Uri? ->
			if (uri != null) {
				importCSV(uri)
			} else {
				awaitingImportResult = false
				binding.root.snack(string = R.string.failed_transaction_import)
			}
		}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		initViews()
		observeThemeMode()
		observeSelectedCurrency()
		observeExportState()
		observeImportState()
		observeClearDataState()
	}

	private fun initViews() = with(binding) {
		themeModeRow.setOnClickListener {
			if (!applyingThemeState) {
				themeModeSwitch.isChecked = !themeModeSwitch.isChecked
			}
		}

		currencySelectorButton.setOnClickListener {
			showCurrencyDialog()
		}

		themeModeSwitch.setOnCheckedChangeListener { _, isChecked ->
			if (!applyingThemeState) {
				viewModel.setDarkMode(isChecked)
			}
		}

		manageTagsButton.setOnClickListener {
			findNavController().navigate(R.id.tagsFragment)
		}

		voiceSettingsButton.setOnClickListener {
			findNavController().navigate(R.id.voiceSettingsFragment)
		}

		importButton.setOnClickListener {
			csvImportRequestLauncher.launch(Unit)
		}

		exportButton.setOnClickListener {
			val csvFileName = "expenso_${System.currentTimeMillis()}"
			csvCreateRequestLauncher.launch(csvFileName)
		}

		clearDataButton.setOnClickListener {
			showClearDataDialog()
		}

		aboutButton.setOnClickListener {
			findNavController().navigate(R.id.aboutFragment)
		}
	}

	private fun observeThemeMode() = lifecycleScope.launchWhenStarted {
		viewModel.getUIMode.collect { isChecked ->
			applyingThemeState = true
			binding.themeModeSwitch.isChecked = isChecked
			applyingThemeState = false
		}
	}

	private fun observeSelectedCurrency() = lifecycleScope.launchWhenStarted {
		viewModel.selectedCurrency.collect { currency ->
			selectedCurrency = currency
			binding.currencyValue.text = currency.displayName
		}
	}

	private fun showCurrencyDialog() {
		val currencyOptions = SupportedCurrency.values()
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.text_currency)
			.setSingleChoiceItems(
				currencyOptions.map { it.displayName }.toTypedArray(),
				currencyOptions.indexOf(selectedCurrency)
			) { dialog, which ->
				viewModel.setSelectedCurrency(currencyOptions[which])
				dialog.dismiss()
			}
			.show()
	}

	private fun observeExportState() = lifecycleScope.launchWhenStarted {
		viewModel.exportCsvState.collect { state ->
			when (state) {
				ExportState.Empty -> {
				}
				is ExportState.Error -> {
					if (awaitingExportResult) {
						awaitingExportResult = false
						binding.root.snack(string = R.string.failed_transaction_export)
					}
				}
				ExportState.Loading -> {
				}
				is ExportState.Success -> {
					if (awaitingExportResult) {
						awaitingExportResult = false
						binding.root.snack(string = R.string.success_transaction_export) {
							action(text = R.string.text_open) {
								previewCsvRequestLauncher.launch(state.fileUri)
							}
						}
					}
				}
			}
		}
	}

	private fun observeImportState() = lifecycleScope.launchWhenStarted {
		viewModel.importCsvState.collect { state ->
			when (state) {
				ImportState.Empty -> {
				}
				is ImportState.Error -> {
					if (awaitingImportResult) {
						awaitingImportResult = false
						binding.root.snack(string = R.string.failed_transaction_import)
					}
				}
				ImportState.Loading -> {
				}
				is ImportState.Success -> {
					if (awaitingImportResult) {
						awaitingImportResult = false
						Snackbar.make(
							binding.root,
							getString(
								R.string.success_transaction_import,
								state.importedCount
							),
							Snackbar.LENGTH_LONG
						).show()
					}
				}
			}
		}
	}

	private fun observeClearDataState() = lifecycleScope.launchWhenStarted {
		viewModel.clearDataState.collect { state ->
			when (state) {
				ClearDataState.Empty -> {
				}
				is ClearDataState.Error -> {
					if (awaitingClearResult) {
						awaitingClearResult = false
						binding.root.snack(string = R.string.failed_clear_data)
					}
				}
				ClearDataState.Loading -> {
				}
				is ClearDataState.Success -> {
					if (awaitingClearResult) {
						awaitingClearResult = false
						Snackbar.make(
							binding.root,
							getString(
								R.string.success_clear_data,
								state.deletedTransactionCount
							),
							Snackbar.LENGTH_LONG
						).show()
					}
				}
			}
		}
	}

	private fun exportCSV(csvFileUri: Uri) {
		awaitingExportResult = true
		viewModel.exportTransactionsToCsv(csvFileUri)
	}

	private fun importCSV(csvFileUri: Uri) {
		awaitingImportResult = true
		viewModel.importTransactionsFromCsv(csvFileUri)
	}

	private fun showClearDataDialog() {
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.text_clear_data_title)
			.setMessage(R.string.text_clear_data_message)
			.setNegativeButton(R.string.cancel, null)
			.setPositiveButton(R.string.text_clear_data_confirm) { _, _ ->
				awaitingClearResult = true
				viewModel.clearAllData()
			}
			.show()
	}

	override fun getViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?
	) = FragmentSettingsBinding.inflate(inflater, container, false)
}