package com.guardian.app.voip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class OutgoingCallActivity : ComponentActivity() {

    companion object {
        const val ACTION_CALL_ACCEPTED = "com.guardian.app.voip.ACTION_CALL_ACCEPTED"
        const val ACTION_CALL_REJECTED = "com.guardian.app.voip.ACTION_CALL_REJECTED"
        const val ACTION_CALL_ENDED = "com.guardian.app.voip.ACTION_CALL_ENDED"
        const val ACTION_CALL_TIMEOUT = "com.guardian.app.voip.ACTION_CALL_TIMEOUT"
    }

    private var calleeUid: String = ""
    private var calleeName: String = ""
    private var calleePhone: String = ""

    private var callId: String = ""
    private var channelName: String = ""
    private var rtcToken: String = ""

    private var callState by mutableStateOf("Dialing...")
    private val scope = CoroutineScope(Dispatchers.Main)
    private val client = OkHttpClient()

    private val signalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val recCallId = intent?.getStringExtra("callId")
            if (recCallId != null && recCallId != callId) return

            when (intent?.action) {
                ACTION_CALL_ACCEPTED -> {
                    CallDiagnostics.log("SIG", "Callee accepted call $callId")
                    callState = "Connected"
                    launchVoipCallActivity()
                }
                ACTION_CALL_REJECTED -> {
                    CallDiagnostics.log("SIG", "Callee rejected call $callId", LogLevel.WARN)
                    callState = "Call Declined"
                    Toast.makeText(this@OutgoingCallActivity, "$calleeName declined the call", Toast.LENGTH_SHORT).show()
                    finish()
                }
                ACTION_CALL_TIMEOUT -> {
                    CallDiagnostics.log("SIG", "Call $callId timed out (no answer)", LogLevel.WARN)
                    callState = "No Answer"
                    Toast.makeText(this@OutgoingCallActivity, "No answer from $calleeName", Toast.LENGTH_SHORT).show()
                    finish()
                }
                ACTION_CALL_ENDED -> {
                    CallDiagnostics.log("SIG", "Call $callId ended by peer")
                    callState = "Ended"
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        calleeUid = intent.getStringExtra("calleeUid") ?: ""
        calleeName = intent.getStringExtra("calleeName") ?: "Guardian Contact"
        calleePhone = intent.getStringExtra("calleePhone") ?: ""

        if (calleeUid.isBlank()) {
            Toast.makeText(this, "This contact isn't reachable right now.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_CALL_ACCEPTED)
            addAction(ACTION_CALL_REJECTED)
            addAction(ACTION_CALL_ENDED)
            addAction(ACTION_CALL_TIMEOUT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(signalReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(signalReceiver, filter)
        }

        initiateCall()

        setContent {
            GuardianTheme {
                OutgoingCallScreen(
                    calleeName = calleeName,
                    calleePhone = calleePhone,
                    statusText = callState,
                    onCancel = { cancelCall() }
                )
            }
        }
    }

    private fun initiateCall() {
        scope.launch(Dispatchers.IO) {
            CallDiagnostics.log("SIG", "Initiating call to $calleeName (uid=$calleeUid)")
            val user = FirebaseAuth.getInstance().currentUser
            val idToken = try {
                user?.getIdToken(true)?.await()?.token
            } catch (e: Exception) {
                null
            }

            val url = "${BuildConfig.BACKEND_URL}/calls"
            val payload = JSONObject().apply {
                put("calleeUid", calleeUid)
            }

            try {
                val reqBuilder = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))

                if (idToken != null) reqBuilder.header("Authorization", "Bearer $idToken")

                client.newCall(reqBuilder.build()).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful && body.isNotBlank()) {
                        val json = JSONObject(body)
                        callId = json.optString("callId")
                        channelName = json.optString("channel")
                        rtcToken = json.optString("rtcToken")

                        CallDiagnostics.log("SIG", "Call initiated: callId=$callId, channel=$channelName")
                        withContext(Dispatchers.Main) {
                            callState = "Ringing..."
                        }
                    } else {
                        CallDiagnostics.log("SIG", "Call initiation failed: HTTP ${resp.code}", LogLevel.ERROR)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@OutgoingCallActivity, "This contact isn't reachable right now.", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    }
                }
            } catch (e: Exception) {
                CallDiagnostics.log("SIG", "Call initiation exception: ${e.message}", LogLevel.ERROR)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@OutgoingCallActivity, "Call failed: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun cancelCall() {
        CallDiagnostics.log("SIG", "Cancelling call $callId")
        if (callId.isNotBlank()) {
            scope.launch(Dispatchers.IO) {
                try {
                    val user = FirebaseAuth.getInstance().currentUser
                    val token = user?.getIdToken(true)?.await()?.token ?: ""
                    val url = "${BuildConfig.BACKEND_URL}/calls/$callId/cancel"
                    val request = Request.Builder()
                        .url(url)
                        .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                        .header("Authorization", "Bearer $token")
                        .build()
                    client.newCall(request).execute()
                } catch (e: Exception) {
                    CallDiagnostics.log("SIG", "Cancel request error: ${e.message}", LogLevel.WARN)
                }
            }
        }
        finish()
    }

    private fun launchVoipCallActivity() {
        val intent = Intent(this, VoipCallActivity::class.java).apply {
            putExtra("channel_name", channelName)
            putExtra("callId", callId)
            putExtra("rtcToken", rtcToken)
            putExtra("peerName", calleeName)
            putExtra("peerNumber", calleePhone)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(signalReceiver)
        } catch (_: Exception) {}
    }
}

@Composable
private fun OutgoingCallScreen(
    calleeName: String,
    calleePhone: String,
    statusText: String,
    onCancel: () -> Unit
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
                    text = calleeName,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = calleePhone.ifBlank { "Guardian VoIP Call" },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF38BDF8), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(statusText, color = Color(0xFF38BDF8), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                modifier = Modifier.size(72.dp),
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Default.CallEnd, contentDescription = "Cancel", tint = Color.White)
            }
        }
    }
}
