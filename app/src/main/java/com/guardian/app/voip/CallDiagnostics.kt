package com.guardian.app.voip

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

enum class LogLevel { INFO, WARN, ERROR }

data class DiagnosticEntry(
    val timestampMs: Long = System.currentTimeMillis(),
    val tag: String,
    val level: LogLevel,
    val message: String
)

object CallDiagnostics {
    private const val MAX_CAPACITY = 200
    private val buffer = ConcurrentLinkedQueue<DiagnosticEntry>()
    private val _logs = MutableStateFlow<List<DiagnosticEntry>>(emptyList())
    val logs: StateFlow<List<DiagnosticEntry>> = _logs.asStateFlow()

    fun log(tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        val entry = DiagnosticEntry(tag = tag, level = level, message = message)
        buffer.add(entry)
        while (buffer.size > MAX_CAPACITY) {
            buffer.poll()
        }
        _logs.value = buffer.toList()

        try {
            when (level) {
                LogLevel.INFO -> android.util.Log.i("CallDiag[$tag]", message)
                LogLevel.WARN -> android.util.Log.w("CallDiag[$tag]", message)
                LogLevel.ERROR -> android.util.Log.e("CallDiag[$tag]", message)
            }
        } catch (_: Throwable) {}
    }

    fun clear() {
        buffer.clear()
        _logs.value = emptyList()
    }

    fun getFormattedLogs(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return buffer.joinToString("\n") { entry ->
            "[" + sdf.format(Date(entry.timestampMs)) + "] [" + entry.tag + "] [" + entry.level + "] " + entry.message
        }
    }
}
