package com.guardian.app.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.guardian.app.BuildConfig
import com.guardian.app.voip.CallDiagnostics
import com.guardian.app.voip.IncomingCallActivity
import com.guardian.app.voip.LogLevel
import com.guardian.app.voip.OutgoingCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class GuardianFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID_INCOMING = "guardian_incoming_calls"
        const val NOTIF_ID_INCOMING = 2003

        fun broadcastSignal(context: Context, action: String, extraKey: String? = null, extraVal: String? = null) {
            val intent = Intent(action).apply {
                if (extraKey != null && extraVal != null) putExtra(extraKey, extraVal)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val userId = getUserId()
        com.susagi.alerts.AblyHelper.connect(applicationContext, userId)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("GuardianFCM", "New FCM token: ${token.take(20)}...")
        CallDiagnostics.log("SIG", "New FCM token generated: ${token.take(15)}...")
        sendTokenToBackend(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("GuardianFCM", "Message received from: ${remoteMessage.from}")

        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val type = data["type"] ?: ""
            CallDiagnostics.log("SIG", "FCM data payload received: type=$type")

            when (type) {
                "incoming_call" -> handleIncomingCall(data)
                "call_accepted" -> handleCallSignal(OutgoingCallActivity.ACTION_CALL_ACCEPTED, data)
                "call_rejected" -> handleCallSignal(OutgoingCallActivity.ACTION_CALL_REJECTED, data)
                "call_cancelled" -> handleCallCancelled(data)
                "call_ended" -> handleCallSignal(OutgoingCallActivity.ACTION_CALL_ENDED, data)
                "call_timeout" -> handleCallSignal(OutgoingCallActivity.ACTION_CALL_TIMEOUT, data)
                else -> handleFamilyAlert(data)
            }
        }
    }

    private fun handleIncomingCall(data: Map<String, String>) {
        val callId = data["callId"] ?: ""
        val channel = data["channel"] ?: ""
        val callerUid = data["callerUid"] ?: ""
        val callerName = data["callerName"] ?: "Guardian Caller"
        val callerNumber = data["callerNumber"] ?: ""
        val rtcToken = data["rtcToken"] ?: ""
        val expiresAt = data["expiresAt"] ?: ""

        CallDiagnostics.log("SIG", "FCM incoming_call received: callId=$callId, caller=$callerName ($callerNumber)")

        showIncomingCallNotification(
            context = this,
            callId = callId,
            channel = channel,
            callerUid = callerUid,
            callerName = callerName,
            callerNumber = callerNumber,
            rtcToken = rtcToken,
            expiresAt = expiresAt
        )
    }

    private fun handleCallCancelled(data: Map<String, String>) {
        val callId = data["callId"] ?: ""
        CallDiagnostics.log("SIG", "FCM call_cancelled received for callId=$callId", LogLevel.WARN)

        // Cancel notification
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID_INCOMING)

        // Broadcast to dismiss IncomingCallActivity
        broadcastSignal(this, IncomingCallActivity.ACTION_CANCEL, "callId", callId)
    }

    private fun handleCallSignal(action: String, data: Map<String, String>) {
        val callId = data["callId"] ?: ""
        CallDiagnostics.log("SIG", "FCM signal $action received for callId=$callId")
        broadcastSignal(this, action, "callId", callId)
    }

    private fun showIncomingCallNotification(
        context: Context,
        callId: String,
        channel: String,
        callerUid: String,
        callerName: String,
        callerNumber: String,
        rtcToken: String,
        expiresAt: String
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID_INCOMING,
                "Guardian Incoming VoIP Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority full-screen incoming VoIP calls"
                setBypassDnd(true)
            }
            nm.createNotificationChannel(chan)
        }

        val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
            putExtra("callId", callId)
            putExtra("channel", channel)
            putExtra("callerUid", callerUid)
            putExtra("callerName", callerName)
            putExtra("callerNumber", callerNumber)
            putExtra("rtcToken", rtcToken)
            putExtra("expiresAt", expiresAt)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            callId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Accept Action Intent
        val acceptIntent = Intent(context, IncomingCallActivity::class.java).apply {
            action = IncomingCallActivity.ACTION_ACCEPT
            putExtra("callId", callId)
            putExtra("channel", channel)
            putExtra("callerUid", callerUid)
            putExtra("callerName", callerName)
            putExtra("callerNumber", callerNumber)
            putExtra("rtcToken", rtcToken)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            context,
            callId.hashCode() + 1,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Reject Action Intent
        val rejectIntent = Intent(context, IncomingCallActivity::class.java).apply {
            action = IncomingCallActivity.ACTION_REJECT
            putExtra("callId", callId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val rejectPendingIntent = PendingIntent.getActivity(
            context,
            callId.hashCode() + 2,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerPerson = androidx.core.app.Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()

        val canUseFullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nm.canUseFullScreenIntent()
        } else {
            true
        }

        CallDiagnostics.log("SIG", "Notification path: fullScreenIntentGranted=$canUseFullScreen")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_INCOMING)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming Guardian Call")
            .setContentText("$callerName is calling...")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)

        try {
            val style = NotificationCompat.CallStyle.forIncomingCall(
                callerPerson,
                rejectPendingIntent,
                acceptPendingIntent
            )
            builder.setStyle(style)
        } catch (e: Exception) {
            builder.addAction(android.R.drawable.ic_menu_call, "Accept", acceptPendingIntent)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", rejectPendingIntent)
        }

        nm.notify(NOTIF_ID_INCOMING, builder.build())
    }

    private fun handleFamilyAlert(data: Map<String, String>) {
        val alertType = data["alert_type"] ?: "scam_detected"
        val protectedUserName = data["protected_user"] ?: "Family member"
        val riskScore = data["risk_score"]?.toIntOrNull() ?: 0
        val scamType = data["scam_type"] ?: "Unknown"
        val callerNumber = data["caller_number"] ?: "Unknown"
        val transcriptSummary = data["transcript_summary"] ?: ""

        GuardianNotificationHelper.showFamilyAlert(
            context = this,
            alertType = alertType,
            protectedUserName = protectedUserName,
            riskScore = riskScore,
            scamType = scamType,
            callerNumber = callerNumber,
            transcriptSummary = transcriptSummary
        )
    }

    fun sendTokenToBackend(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = JSONObject().apply {
                    put("fcmToken", token)
                    put("userId", getUserId())
                    put("timestamp", System.currentTimeMillis())
                }

                val request = Request.Builder()
                    .url("${BuildConfig.BACKEND_URL}/family/register")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                OkHttpClient().newCall(request).execute()
                Log.d("GuardianFCM", "Token registered with backend")
            } catch (e: Exception) {
                Log.e("GuardianFCM", "Token registration failed", e)
            }
        }
    }

    private fun getUserId(): String {
        return getSharedPreferences("guardian_secure_prefs", MODE_PRIVATE)
            .getString("user_id", "anonymous") ?: "anonymous"
    }
}
