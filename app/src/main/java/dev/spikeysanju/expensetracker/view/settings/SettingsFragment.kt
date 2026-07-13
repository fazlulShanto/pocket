package dev.spikeysanju.expensetracker.view.settings

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.spikeysanju.expensetracker.R
import dev.spikeysanju.expensetracker.databinding.FragmentSettingsBinding
import dev.spikeysanju.expensetracker.services.backup.CreateBackupContract
import dev.spikeysanju.expensetracker.services.backup.RestoreBackupContract
import dev.spikeysanju.expensetracker.services.backup.RestorePreview
import dev.spikeysanju.expensetracker.utils.SupportedCurrency
import dev.spikeysanju.expensetracker.utils.viewState.BackupState
import dev.spikeysanju.expensetracker.utils.viewState.ClearDataState
import dev.spikeysanju.expensetracker.utils.viewState.RestoreState
import dev.spikeysanju.expensetracker.view.base.BaseFragment
import dev.spikeysanju.expensetracker.view.main.viewmodel.TransactionViewModel
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collect
import snack

@AndroidEntryPoint
class SettingsFragment : BaseFragment<FragmentSettingsBinding, TransactionViewModel>() {
    override val viewModel: TransactionViewModel by activityViewModels()
    private var applyingThemeState = false
    private var awaitingClearResult = false
    private var selectedCurrency: SupportedCurrency = SupportedCurrency.DEFAULT
    private var restorePreviewDialog: AlertDialog? = null

    private val createBackupLauncher =
        registerForActivityResult(CreateBackupContract()) { uri: Uri? ->
            uri?.let(viewModel::createBackup)
        }

    private val restoreBackupLauncher =
        registerForActivityResult(RestoreBackupContract()) { uri: Uri? ->
            uri?.let(viewModel::inspectBackup)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        observeThemeMode()
        observeSelectedCurrency()
        observeBackupState()
        observeRestoreState()
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

        restoreBackupButton.setOnClickListener {
            restoreBackupLauncher.launch(Unit)
        }

        createBackupButton.setOnClickListener {
            val timestamp = SimpleDateFormat(BACKUP_FILENAME_PATTERN, Locale.US).format(Date())
            createBackupLauncher.launch("expenso_backup_$timestamp.expenso")
        }

        clearDataButton.setOnClickListener {
            showClearDataDialog()
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

    private fun observeBackupState() = lifecycleScope.launchWhenStarted {
        viewModel.backupState.collect { state ->
            updateDataActionAvailability()
            when (state) {
                BackupState.Empty,
                BackupState.Loading -> Unit
                is BackupState.Success -> {
                    binding.root.snack(R.string.success_backup_created)
                    viewModel.consumeBackupState()
                }
                is BackupState.Error -> {
                    showErrorDialog(R.string.backup_error_title, state.exception)
                    viewModel.consumeBackupState()
                }
            }
        }
    }

    private fun observeRestoreState() = lifecycleScope.launchWhenStarted {
        viewModel.restoreState.collect { state ->
            updateDataActionAvailability()
            when (state) {
                RestoreState.Empty,
                RestoreState.Inspecting,
                RestoreState.Restoring -> Unit
                is RestoreState.Preview -> showRestorePreview(state.preview)
                is RestoreState.Success -> {
                    Snackbar.make(
                        binding.root,
                        getString(
                            R.string.success_backup_restored,
                            state.result.transactionCount,
                            state.result.tagCount
                        ),
                        Snackbar.LENGTH_LONG
                    ).show()
                    viewModel.consumeRestoreState()
                }
                is RestoreState.Error -> {
                    showErrorDialog(R.string.restore_error_title, state.exception)
                    viewModel.consumeRestoreState()
                }
            }
        }
    }

    private fun showRestorePreview(preview: RestorePreview) {
        if (restorePreviewDialog?.isShowing == true) {
            return
        }

        val exportedAt = DateFormat.getDateTimeInstance().format(Date(preview.exportedAt))
        val theme = getString(
            if (preview.darkMode) R.string.backup_theme_dark else R.string.backup_theme_light
        )
        restorePreviewDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.restore_preview_title)
            .setMessage(
                getString(
                    R.string.restore_preview_message,
                    exportedAt,
                    preview.appVersion,
                    preview.transactionCount,
                    preview.tagCount,
                    preview.currencyCode,
                    theme
                )
            )
            .setNegativeButton(R.string.cancel) { _, _ -> viewModel.cancelRestore() }
            .setPositiveButton(R.string.restore_confirm) { _, _ -> viewModel.confirmRestore() }
            .setOnCancelListener { viewModel.cancelRestore() }
            .create()
            .also { dialog ->
                dialog.setOnDismissListener { restorePreviewDialog = null }
                dialog.show()
            }
    }

    private fun showErrorDialog(title: Int, error: Throwable) {
        val message = generateSequence(error) { it.cause }
            .mapNotNull { throwable -> throwable.message }
            .firstOrNull { it.isNotBlank() }
            ?: getString(R.string.backup_unknown_error)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun updateDataActionAvailability() = with(binding) {
        val busy = viewModel.backupState.value is BackupState.Loading ||
            viewModel.restoreState.value is RestoreState.Inspecting ||
            viewModel.restoreState.value is RestoreState.Restoring
        createBackupButton.isEnabled = !busy
        restoreBackupButton.isEnabled = !busy
        createBackupButton.alpha = if (busy) DISABLED_ALPHA else ENABLED_ALPHA
        restoreBackupButton.alpha = if (busy) DISABLED_ALPHA else ENABLED_ALPHA
    }

    private fun observeClearDataState() = lifecycleScope.launchWhenStarted {
        viewModel.clearDataState.collect { state ->
            when (state) {
                ClearDataState.Empty,
                ClearDataState.Loading -> Unit
                is ClearDataState.Error -> {
                    if (awaitingClearResult) {
                        awaitingClearResult = false
                        binding.root.snack(string = R.string.failed_clear_data)
                    }
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

    override fun onDestroyView() {
        restorePreviewDialog = null
        super.onDestroyView()
    }

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = FragmentSettingsBinding.inflate(inflater, container, false)

    private companion object {
        const val BACKUP_FILENAME_PATTERN = "yyyyMMdd_HHmmss"
        const val DISABLED_ALPHA = 0.5f
        const val ENABLED_ALPHA = 1f
    }
}
