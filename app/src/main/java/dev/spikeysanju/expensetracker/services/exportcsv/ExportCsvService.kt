package dev.spikeysanju.expensetracker.services.exportcsv

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import com.opencsv.CSVWriter
import java.io.OutputStreamWriter
import javax.inject.Inject

class ExportCsvService @Inject constructor(
    private val appContext: Context
) {

    @WorkerThread
    suspend fun writeToCSV(
        csvFileUri: Uri,
        header: Array<String>,
        rows: List<Array<String>>
    ): Uri {
        val outputStream = appContext.contentResolver.openOutputStream(csvFileUri, "wt")
            ?: throw IllegalStateException("failed to open output stream")

        outputStream.use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                CSVWriter(writer).use { csvWriter ->
                    csvWriter.writeNext(header, false)
                    rows.forEach { row ->
                        csvWriter.writeNext(row, false)
                    }
                }
            }
        }

        return csvFileUri
    }
}
