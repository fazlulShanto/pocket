package dev.spikeysanju.expensetracker.utils.viewState

sealed class ImportState {
    object Loading : ImportState()
    object Empty : ImportState()
    data class Success(val importedCount: Int) : ImportState()
    data class Error(val exception: Throwable) : ImportState()
}

sealed class ClearDataState {
    object Loading : ClearDataState()
    object Empty : ClearDataState()
    data class Success(val deletedTransactionCount: Int) : ClearDataState()
    data class Error(val exception: Throwable) : ClearDataState()
}