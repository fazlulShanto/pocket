package dev.spikeysanju.expensetracker.utils.viewState

import android.net.Uri
import dev.spikeysanju.expensetracker.services.backup.RestorePreview
import dev.spikeysanju.expensetracker.services.backup.RestoreResult

sealed class BackupState {
    object Empty : BackupState()
    object Loading : BackupState()
    data class Success(val fileUri: Uri) : BackupState()
    data class Error(val exception: Throwable) : BackupState()
}

sealed class RestoreState {
    object Empty : RestoreState()
    object Inspecting : RestoreState()
    object Restoring : RestoreState()
    data class Preview(val preview: RestorePreview) : RestoreState()
    data class Success(val result: RestoreResult) : RestoreState()
    data class Error(val exception: Throwable) : RestoreState()
}

sealed class ClearDataState {
    object Loading : ClearDataState()
    object Empty : ClearDataState()
    data class Success(val deletedTransactionCount: Int) : ClearDataState()
    data class Error(val exception: Throwable) : ClearDataState()
}
