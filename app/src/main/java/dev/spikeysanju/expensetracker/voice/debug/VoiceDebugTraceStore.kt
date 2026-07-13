package dev.spikeysanju.expensetracker.voice.debug

import dev.spikeysanju.expensetracker.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VoiceDebugTraceStore {
    private const val MAX_TRACE_LENGTH = 128 * 1024
    private val trace = StringBuilder()

    @Synchronized
    fun clear() {
        trace.clear()
    }

    @Synchronized
    fun append(section: String, details: String) {
        if (!BuildConfig.DEBUG) return

        if (trace.isNotEmpty()) {
            trace.append("\n\n")
        }
        trace.append("===== ")
            .append(section)
            .append(" @ ")
            .append(SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date()))
            .append(" =====\n")
            .append(details)

        val overflow = trace.length - MAX_TRACE_LENGTH
        if (overflow > 0) {
            trace.delete(0, overflow)
        }
    }

    @Synchronized
    fun snapshot(): String = if (BuildConfig.DEBUG) trace.toString() else ""
}
