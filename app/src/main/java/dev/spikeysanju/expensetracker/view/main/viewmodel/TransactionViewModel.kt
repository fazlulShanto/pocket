package dev.spikeysanju.expensetracker.view.main.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.spikeysanju.expensetracker.data.local.datastore.CurrencyPreference
import dev.spikeysanju.expensetracker.data.local.datastore.UIModeImpl
import dev.spikeysanju.expensetracker.model.Transaction
import dev.spikeysanju.expensetracker.repo.TransactionRepo
import dev.spikeysanju.expensetracker.services.backup.BackupDocumentV1
import dev.spikeysanju.expensetracker.services.backup.ExpensoBackupService
import dev.spikeysanju.expensetracker.services.datamanagement.TransactionDataService
import dev.spikeysanju.expensetracker.utils.viewState.BackupState
import dev.spikeysanju.expensetracker.utils.viewState.ClearDataState
import dev.spikeysanju.expensetracker.utils.SupportedCurrency
import dev.spikeysanju.expensetracker.utils.viewState.DetailState
import dev.spikeysanju.expensetracker.utils.viewState.RestoreState
import dev.spikeysanju.expensetracker.utils.viewState.ViewState
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val transactionRepo: TransactionRepo,
    private val backupService: ExpensoBackupService,
    private val transactionDataService: TransactionDataService,
    private val uiModeDataStore: UIModeImpl,
    private val currencyPreference: CurrencyPreference
) : ViewModel() {

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Empty)
    val backupState: StateFlow<BackupState> = _backupState

    private val _restoreState = MutableStateFlow<RestoreState>(RestoreState.Empty)
    val restoreState: StateFlow<RestoreState> = _restoreState
    private var pendingRestore: BackupDocumentV1? = null

    private val _clearDataState = MutableStateFlow<ClearDataState>(ClearDataState.Empty)
    val clearDataState: StateFlow<ClearDataState> = _clearDataState

    private val _uiState = MutableStateFlow<ViewState>(ViewState.Loading)
    private val _detailState = MutableStateFlow<DetailState>(DetailState.Loading)

    // UI collect from this stateFlow to get the state updates
    val uiState: StateFlow<ViewState> = _uiState
    val detailState: StateFlow<DetailState> = _detailState

    // get ui mode
    val getUIMode = uiModeDataStore.uiMode

    val selectedCurrency = currencyPreference.selectedCurrency

    // save ui mode
    fun setDarkMode(isNightMode: Boolean) {
        viewModelScope.launch(IO) {
            uiModeDataStore.saveToDataStore(isNightMode)
        }
    }

    fun setSelectedCurrency(currency: SupportedCurrency) {
        viewModelScope.launch(IO) {
            currencyPreference.saveSelectedCurrency(currency)
        }
    }

    fun createBackup(destination: Uri) = viewModelScope.launch {
        _backupState.value = BackupState.Loading
        runCatching { backupService.createBackup(destination) }
            .onSuccess { fileUri -> _backupState.value = BackupState.Success(fileUri) }
            .onFailure { error -> _backupState.value = BackupState.Error(error) }
    }

    fun inspectBackup(source: Uri) = viewModelScope.launch {
        pendingRestore = null
        _restoreState.value = RestoreState.Inspecting
        runCatching { backupService.inspectBackup(source) }
            .onSuccess { inspectedBackup ->
                pendingRestore = inspectedBackup.document
                _restoreState.value = RestoreState.Preview(inspectedBackup.preview)
            }
            .onFailure { error -> _restoreState.value = RestoreState.Error(error) }
    }

    fun confirmRestore() = viewModelScope.launch {
        val document = pendingRestore
        if (document == null) {
            _restoreState.value = RestoreState.Error(
                IllegalStateException("Select the backup file again before restoring.")
            )
            return@launch
        }

        _restoreState.value = RestoreState.Restoring
        runCatching { backupService.restore(document) }
            .onSuccess { result ->
                pendingRestore = null
                _restoreState.value = RestoreState.Success(result)
            }
            .onFailure { error -> _restoreState.value = RestoreState.Error(error) }
    }

    fun cancelRestore() {
        pendingRestore = null
        _restoreState.value = RestoreState.Empty
    }

    fun consumeBackupState() {
        _backupState.value = BackupState.Empty
    }

    fun consumeRestoreState() {
        _restoreState.value = RestoreState.Empty
    }

    fun clearAllData() = viewModelScope.launch {
        _clearDataState.value = ClearDataState.Loading
        runCatching {
            transactionDataService.clearAllData()
        }.onSuccess { deletedTransactionCount ->
            _clearDataState.value = ClearDataState.Success(deletedTransactionCount)
        }.onFailure { error ->
            _clearDataState.value = ClearDataState.Error(error)
        }
    }

    // insert transaction
    fun insertTransaction(transaction: Transaction) = viewModelScope.launch {
        transactionRepo.insert(transaction)
    }

    // update transaction
    fun updateTransaction(transaction: Transaction) = viewModelScope.launch {
        transactionRepo.update(transaction)
    }

    // delete transaction
    fun deleteTransaction(transaction: Transaction) = viewModelScope.launch {
        transactionRepo.delete(transaction)
    }

    // get all transaction
    fun getAllTransaction(type: String) = viewModelScope.launch {
        transactionRepo.getAllSingleTransaction(type).collect { result ->
            if (result.isNullOrEmpty()) {
                _uiState.value = ViewState.Empty
            } else {
                _uiState.value = ViewState.Success(result)
            }
        }
    }

    // get transaction by id
    fun getByID(id: Int) = viewModelScope.launch {
        _detailState.value = DetailState.Loading
        transactionRepo.getByID(id).collect { result: Transaction? ->
            if (result != null) {
                _detailState.value = DetailState.Success(result)
            }
        }
    }

    // delete transaction
    fun deleteByID(id: Int) = viewModelScope.launch {
        transactionRepo.deleteByID(id)
    }
}
