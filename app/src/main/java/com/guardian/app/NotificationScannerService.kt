package com.guardian.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.susagi.safety.Risk
import com.susagi.safety.SafetyBarrier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationScannerService : NotificationListenerService() {

    companion object {
        private const val CHANNEL_ID = "guardian_sms_interception"
        private const val CHANNEL_NAME = "Guardian Scam Message Shield"
        private val recentAlertTimestamps = mutableMapOf<Int, Long>()
        private const val DEDUPLICATION_WINDOW_MS = 300_000L // 5 minutes

        fun isNotificationAccessGranted(context: Context): Boolean {
            return try {
                val enabledListeners = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
                enabledListeners.contains(context.packageName)
            } catch (_: Exception) {
                false
            }
        }

        @Synchronized
        fun shouldAlert(contentHash: Int, nowMs: Long = System.currentTimeMillis()): Boolean {
            recentAlertTimestamps.entries.removeAll { (nowMs - it.value) > DEDUPLICATION_WINDOW_MS }
            val lastAlertTime = recentAlertTimestamps[contentHash]
            if (lastAlertTime != null && (nowMs - lastAlertTime) < DEDUPLICATION_WINDOW_MS) {
                return false
            }
            recentAlertTimestamps[contentHash] = nowMs
            return true
        }

        @Synchronized
        fun clearAlertHistory() {
            recentAlertTimestamps.clear()
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null || sbn.packageName == packageName) return

        val prefs = getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("message_protection_enabled", true)) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty()
        val fullContent = "$title $text $bigText".trim()

        if (fullContent.isBlank()) return

        serviceScope.launch {
            // Use Astra SafetyBarrier to accurately discriminate real threat from legitimate warnings (e.g. "Never share OTP")
            val assessment = SafetyBarrier.analyze(null, fullContent)
            val isHighRisk = assessment.risk == Risk.HIGH
            val isReview = assessment.risk == Risk.REVIEW

            if (isHighRisk || isReview) {
                val contentHash = (title + fullContent).hashCode()
                if (!shouldAlert(contentHash)) return@launch

                try {
                    com.susagi.history.IncidentHistoryStore.getInstance(applicationContext).recordAssessment(
                        channel = "NOTIFICATION",
                        title = title.ifBlank { "Suspicious Alert" },
                        assessment = assessment,
                        rawSnippet = fullContent
                    )
                } catch (_: Exception) {}

                val originalPendingIntent: PendingIntent? = sbn.notification.contentIntent

                // IMPORTANT (Directive 12): Stop default silent cancellation of user notifications.
                // Do NOT suppress the original notification — preserve legitimate OTPs and messages.
                // Post a separate companion warning alert alongside the original notification.
                val threatDesc = assessment.explanation.ifBlank { "Suspicious payment, extortion or unverified link patterns detected." }
                postGuardianInterceptNotification(
                    packageName = sbn.packageName,
                    title = title.ifBlank { "Suspicious Alert" },
                    body = fullContent,
                    explanation = threatDesc,
                    isHighRisk = isHighRisk,
                    originalPendingIntent = originalPendingIntent,
                    contentHash = contentHash
                )
            }
        }
    }

    private fun postGuardianInterceptNotification(
        packageName: String,
        title: String,
        body: String,
        explanation: String,
        isHighRisk: Boolean,
        originalPendingIntent: PendingIntent?,
        contentHash: Int = (title + body).hashCode()
    ) {
        val warningIntent = Intent(this, ScamWarningActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("source", packageName)
            putExtra("title", title)
            putExtra("body", body)
            putExtra("score", if (isHighRisk) 85 else 50)
            putExtra("explanation", explanation)
            if (originalPendingIntent != null) {
                putExtra("original_pending_intent", originalPendingIntent)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            contentHash,
            warningIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Scam Threat Alert: $title")
            .setContentText("Guardian detected potential threat indicators. Tap to inspect safely.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Guardian Companion Alert: $explanation Tap to inspect safely."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(contentHash, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when malicious messages or phishing SMS are intercepted"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
