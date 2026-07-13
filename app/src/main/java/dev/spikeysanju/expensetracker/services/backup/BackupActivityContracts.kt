package dev.spikeysanju.expensetracker.services.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

class CreateBackupContract : ActivityResultContract<String, Uri?>() {

    override fun createIntent(context: Context, input: String): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = BACKUP_MIME_TYPE
            putExtra(Intent.EXTRA_TITLE, input)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return intent?.data.takeIf { resultCode == Activity.RESULT_OK }
    }
}

class RestoreBackupContract : ActivityResultContract<Unit, Uri?>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = BACKUP_MIME_TYPE
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(BACKUP_MIME_TYPE, "application/json", "application/octet-stream")
            )
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return intent?.data.takeIf { resultCode == Activity.RESULT_OK }
    }
}

const val BACKUP_MIME_TYPE = "application/vnd.expenso.backup+json"
