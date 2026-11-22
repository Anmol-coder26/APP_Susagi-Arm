package com.guardian.app.evidence

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object EvidenceSharer {

    fun sharePdf(context: Context, pdfFile: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", pdfFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Guardian Evidence Report")
            putExtra(
                Intent.EXTRA_TEXT,
                "Auto-generated scam evidence report. Please review before filing."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(shareIntent, "File complaint via")
        )
    }

    fun openCybercrimePortal(context: Context) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("https://cybercrime.gov.in")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun dialCybercrimeHelpline(context: Context) {
        val intent = Intent(
            Intent.ACTION_DIAL,
            android.net.Uri.parse("tel:1930")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun shareSafetySummary(context: Context, subject: String, text: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Safety Summary")
        chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            context.startActivity(chooser)
        } catch (_: Exception) {}
    }
}
