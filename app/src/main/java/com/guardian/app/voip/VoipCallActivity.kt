package com.guardian.app.voip

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.guardian.app.ui.theme.GuardianTheme
import com.susagi.call.VoipCallScreen
import com.susagi.call.VoipCallViewModel

class VoipCallActivity : ComponentActivity() {

    private val viewModel: VoipCallViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return VoipCallViewModel(application) as T
            }
        }
    }
    private var channelName: String = ""
    private var callId: String = ""
    private var rtcToken: String = ""
    private var peerName: String = ""
    private var peerNumber: String = ""

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
            if (micGranted) {
                initiateVoipCall()
            } else {
                CallDiagnostics.log("SIG", "Microphone permission denied by user", LogLevel.ERROR)
                Toast.makeText(this, "Microphone permission is required for Secure VoIP calls", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val incoming = intent.getStringExtra("channel_name") ?: intent.getStringExtra("channel")
        if (incoming.isNullOrBlank()) {
            CallDiagnostics.log("SIG", "VoipCallActivity failed: No room code supplied", LogLevel.ERROR)
            Toast.makeText(this, "No room code supplied", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        channelName = incoming
        callId = intent.getStringExtra("call_id") ?: intent.getStringExtra("callId") ?: ""
        rtcToken = intent.getStringExtra("rtc_token") ?: intent.getStringExtra("rtcToken") ?: ""
        peerName = intent.getStringExtra("peer_name") ?: intent.getStringExtra("peerName") ?: ""
        peerNumber = intent.getStringExtra("peer_number") ?: intent.getStringExtra("peerNumber") ?: ""

        if (peerNumber.isNotBlank()) {
            viewModel.setPeerInfo(peerNumber)
        }

        CallDiagnostics.log("SIG", "VoipCallActivity launched for channel=$channelName, callId=$callId, peer=$peerName ($peerNumber)")

        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            initiateVoipCall()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_PHONE_STATE
                )
            )
        }

        setContent {
            GuardianTheme {
                val uiState by viewModel.uiState.collectAsState()
                VoipCallScreen(
                    uiState = uiState,
                    onToggleMute = { viewModel.toggleMute() },
                    onToggleSpeaker = { viewModel.toggleSpeakerphone() },
                    onSimulateScam = { viewModel.simulateScamCall() },
                    onStopSimulation = { viewModel.stopSimulation() },
                    onEndCall = {
                        endVoipCall()
                        finish()
                    }
                )
            }
        }
    }

    private fun initiateVoipCall() {
        CallDiagnostics.log("SIG", "Starting VoIP call and foreground service for channel $channelName")
        try {
            VoipCallService.startService(this, channelName)
        } catch (e: Exception) {
            CallDiagnostics.log("SIG", "VoipCallService start failed: ${e.message}", LogLevel.WARN)
        }
        if (rtcToken.isNotBlank()) {
            viewModel.startCallWithToken(channelName, rtcToken, peerNumber)
        } else {
            viewModel.startCall(channelName, peerNumber)
        }
    }

    private fun endVoipCall() {
        CallDiagnostics.log("SIG", "Ending VoIP call and stopping foreground service")
        try {
            viewModel.endCall()
        } catch (e: Exception) {
            CallDiagnostics.log("SIG", "viewModel.endCall failed: ${e.message}", LogLevel.WARN)
        }
        try {
            VoipCallService.stopService(this)
        } catch (e: Exception) {
            CallDiagnostics.log("SIG", "VoipCallService.stopService failed: ${e.message}", LogLevel.WARN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        endVoipCall()
    }
}
