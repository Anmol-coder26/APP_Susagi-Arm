package com.guardian.app.voip

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.guardian.app.BuildConfig
import com.guardian.app.ui.theme.GuardianTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IncomingCallActivity : ComponentActivity() {

    companion object {
        const val ACTION_ACCEPT = "com.guardian.app.voip.ACTION_ACCEPT"
        const val ACTION_REJECT = "com.guardian.app.voip.ACTION_REJECT"
        const val ACTION_CANCEL = "com.guardian.app.voip.ACTION_CANCEL"
    }

    private var callId: String = ""
    private var channelName: String = ""
    private var rtcToken: String = ""
    private var callerUid: String = ""
    private var callerName: String = ""
    private var callerNumber: String = ""

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var timeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            CallDiagnostics.log("SIG", "IncomingCallActivity received call_cancelled signal")
            stopRingtoneAndVibration()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenFlags()

        callId = intent.getStringExtra("callId") ?: ""
        channelName = intent.getStringExtra("channel") ?: ""
        rtcToken = intent.getStringExtra("rtcToken") ?: ""
        callerUid = intent.getStringExtra("callerUid") ?: ""
        callerName = intent.getStringExtra("callerName") ?: "Guardian Caller"
        callerNumber = intent.getStringExtra("callerNumber") ?: ""

        CallDiagnostics.log("SIG", "IncomingCallActivity created for callId=$callId from $callerName ($callerNumber)")

        startRingtoneAndVibration()
        startTimeoutTimer()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(cancelReceiver, IntentFilter(ACTION_CANCEL), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(cancelReceiver, IntentFilter(ACTION_CANCEL))
        }

        if (intent.action == ACTION_ACCEPT) {
            acceptCall()
            return
        } else if (intent.action == ACTION_REJECT) {
            rejectCall()
            return
        }

        setContent {
            GuardianTheme {
                IncomingCallScreen(
                    callerName = callerName,
                    callerNumber = callerNumber,
                    onAccept = { acceptCall() },
                    onReject = { rejectCall() }
                )
            }
        }
    }

    private fun configureLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    private fun startRingtoneAndVibration() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, ringtoneUri)
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone?.play()

            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator = v
            if (v.hasVibrator()) {
                val pattern = longArrayOf(0, 1000, 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(pattern, 0)
                }
            }
        } catch (e: Exception) {
            CallDiagnostics.log("SIG", "Ringtone/vibration init error: ${e.message}", LogLevel.WARN)
        }
    }

    private fun stopRingtoneAndVibration() {
        try {
            ringtone?.stop()
            vibrator?.cancel()
        } catch (_: Exception) {}
    }

    private fun startTimeoutTimer() {
        timeoutJob = scope.launch {
            delay(45000)
            CallDiagnostics.log("SIG", "Incoming call timed out after 45s")
            stopRingtoneAndVibration()
            Toast.makeText(this@IncomingCallActivity, "Missed call from $callerName", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun acceptCall() {
        stopRingtoneAndVibration()
        timeoutJob?.cancel()
        CallDiagnostics.log("SIG", "Accepting call $callId")

        // Dismiss notification
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(2003)

        scope.launch(Dispatchers.IO) {
            sendStateApi("accept")
            withContext(Dispatchers.Main) {
                val intent = Intent(this@IncomingCallActivity, VoipCallActivity::class.java).apply {
                    putExtra("channel_name", channelName)
                    putExtra("callId", callId)
                    putExtra("rtcToken", rtcToken)
                    putExtra("peerName", callerName)
                    putExtra("peerNumber", callerNumber)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intent)
                finish()
            }
        }
    }

    private fun rejectCall() {
        stopRingtoneAndVibration()
        timeoutJob?.cancel()
        CallDiagnostics.log("SIG", "Rejecting call $callId")

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(2003)

        scope.launch(Dispatchers.IO) {
            sendStateApi("reject")
            withContext(Dispatchers.Main) {
                finish()
            }
        }
    }

    private suspend fun sendStateApi(action: String) {
        try {
            val user = FirebaseAuth.getInstance().currentUser
            val token = user?.getIdToken(true)?.result?.token ?: ""
            val url = "${BuildConfig.BACKEND_URL}/calls/$callId/$action"
            val request = okhttp3.Request.Builder()
                .url(url)
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .header("Authorization", "Bearer $token")
                .build()
            okhttp3.OkHttpClient().newCall(request).execute().use { resp ->
                CallDiagnostics.log("SIG", "POST /calls/$callId/$action response: ${resp.code}")
            }
        } catch (e: Exception) {
            CallDiagnostics.log("SIG", "Failed to POST /calls/$callId/$action: ${e.message}", LogLevel.WARN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtoneAndVibration()
        timeoutJob?.cancel()
        try {
            unregisterReceiver(cancelReceiver)
        } catch (_: Exception) {}
    }
}

@Composable
private fun IncomingCallScreen(
    callerName: String,
    callerNumber: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = Color(0xFF38BDF8)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = callerName,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = callerNumber.ifBlank { "Guardian VoIP Call" },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFF166534), shape = RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Incoming Guardian Encrypted Call", color = Color(0xFF4ADE80), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reject Button
                Button(
                    onClick = onReject,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape
                ) {
                    Icon(imageVector = Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White)
                }

                // Accept Button
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape
                ) {
                    Icon(imageVector = Icons.Default.Call, contentDescription = "Accept", tint = Color.White)
                }
            }
        }
    }
}
