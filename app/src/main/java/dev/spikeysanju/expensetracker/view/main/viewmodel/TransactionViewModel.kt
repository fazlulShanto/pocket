package dev.spikeysanju.expensetracker.view.main.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.spikeysanju.expensetracker.data.local.datastore.CurrencyPreference
import dev.spikeysanju.expensetracker.data.local.datastore.UIModeImpl
import dev.spikeysanju.expensetracker.model.Transaction
import dev.spikeysanju.expensetracker.repo.TransactionRepo
import dev.spikeysanju.expensetracker.services.datamanagement.TransactionDataService
import dev.spikeysanju.expensetracker.services.exportcsv.TRANSACTION_CSV_HEADER
import dev.spikeysanju.expensetracker.services.exportcsv.ExportCsvService
import dev.spikeysanju.expensetracker.services.exportcsv.toCsvRows
import dev.spikeysanju.expensetracker.utils.viewState.ClearDataState
import dev.spikeysanju.expensetracker.utils.SupportedCurrency
import dev.spikeysanju.expensetracker.utils.viewState.DetailState
import dev.spikeysanju.expensetracker.utils.viewState.ExportState
import dev.spikeysanju.expensetracker.utils.viewState.ImportState
import dev.spikeysanju.expensetracker.utils.viewState.ViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val transactionRepo: TransactionRepo,
    private val exportService: ExportCsvService,
    private val transactionDataService: TransactionDataService,
    private val uiModeDataStore: UIModeImpl,
    private val currencyPreference: CurrencyPreference
) : ViewModel() {

    // state for export csv status
    private val _exportCsvState = MutableStateFlow<ExportState>(ExportState.Empty)
    val exportCsvState: StateFlow<ExportState> = _exportCsvState

    private val _importCsvState = MutableStateFlow<ImportState>(ImportState.Empty)
    val importCsvState: StateFlow<ImportState> = _importCsvState

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

    // export all Transactions to csv file
    fun exportTransactionsToCsv(csvFileUri: Uri) = viewModelScope.launch {
        _exportCsvState.value = ExportState.Loading
        runCatching {
            withContext(Dispatchers.IO) {
                val rows = transactionRepo.getAllTransactions().first().toCsvRows()
                exportService.writeToCSV(csvFileUri, TRANSACTION_CSV_HEADER, rows)
            }
        }.onSuccess { fileUri ->
            _exportCsvState.value = ExportState.Success(fileUri)
        }.onFailure { error ->
            _exportCsvState.value = ExportState.Error(error)
        }
    }

    fun importTransactionsFromCsv(csvFileUri: Uri) = viewModelScope.launch {
        _importCsvState.value = ImportState.Loading
        runCatching {
            transactionDataService.importTransactionsFromCsv(csvFileUri)
        }.onSuccess { importedCount ->
            _importCsvState.value = ImportState.Success(importedCount)
        }.onFailure { error ->
            _importCsvState.value = ImportState.Error(error)
        }
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
