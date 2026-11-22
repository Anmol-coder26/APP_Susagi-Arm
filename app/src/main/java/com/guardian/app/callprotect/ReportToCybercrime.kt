package com.guardian.app.callprotect

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportToCybercrime {
    fun formatSummary(entry: CallHistoryEntry): String {
        val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
        return """
            Scam call reported by Guardian AI.
            Caller Number: ${entry.number}
            Incident Timestamp: $dateStr
            Risk Score: ${entry.riskScore}%
            Threat Signals: ${entry.topSignals}
            Transcript / Context: ${entry.transcriptSummary}
            Action Taken: ${entry.actionTaken}
        """.trimIndent()
    }

    fun openPortal(context: Context, entry: CallHistoryEntry? = null) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://cybercrime.gov.in")
            if (entry != null) {
                putExtra(Intent.EXTRA_TEXT, formatSummary(entry))
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun report(context: Context, entry: CallHistoryEntry) = openPortal(context, entry)

    fun dialHelpline(context: Context) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun shareSummary(context: Context, entry: CallHistoryEntry) {
        val text = formatSummary(entry)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Guardian Scam Call Report: ${entry.number}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Safety Summary")
        chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            context.startActivity(chooser)
        } catch (_: Exception) {}
    }
}
