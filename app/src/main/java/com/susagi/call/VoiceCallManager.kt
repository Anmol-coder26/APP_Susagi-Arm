package com.susagi.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import com.guardian.app.voip.CallDiagnostics
import com.guardian.app.voip.LogLevel
import com.susagi.audio.AudioSource
import com.susagi.pipeline.GuardianPipelineCoordinator
import com.susagi.rtc.AgoraEngineManager
import com.susagi.stt.SttEngineMode
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.IRtcEngineEventHandler.RtcStats
import io.agora.rtc2.RtcEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Speaker { LOCAL, REMOTE }

enum class PillStatus { GREEN, AMBER, RED }

data class DiagnosticsPillsState(
    val rtcJoined: PillStatus = PillStatus.RED,
    val peerPresent: PillStatus = PillStatus.RED,
    val localPcmFlowing: PillStatus = PillStatus.RED,
    val remotePcmFlowing: PillStatus = PillStatus.RED,
    val sttLocalStatus: PillStatus = PillStatus.RED,
    val sttRemoteStatus: PillStatus = PillStatus.RED,
    val localPcmCount: Long = 0,
    val remotePcmCount: Long = 0,
    val localNoAudioWarning: Boolean = false,
    val remoteNoAudioWarning: Boolean = false
)

data class TranscriptLine(
    val speaker: Speaker,
    val text: String,
    val isFinal: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)

data class LiveRiskState(
    val riskScore: Int = 0,
    val victimRole: Speaker? = null,
    val scammerRole: Speaker? = null,
    val roleConfidence: Double = 0.0,
    val topSignals: List<String> = emptyList(),
    val psychologicalPressure: String? = null
)

/**
 * VoiceCallManager
 *
 * Manages Agora RTC call signaling, audio routing, headset detection, and network handoff.
 * Integrates directly with [GuardianPipelineCoordinator] for non-blocking PCM extraction,
 * dual-stream Bhashini ASR, and Gemini real-time semantic scam detection.
 */
class VoiceCallManager(
    private val context: Context,
    private val agoraAppId: String,
    geminiApiKey: String = "",
    private val bhashiniEndpointUrl: String = "",
    private val bhashiniAuthToken: String? = null,
    private var sttLanguage: String = "hi",
    val enableAiPipeline: Boolean = false
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val pipelineCoordinator by lazy {
        GuardianPipelineCoordinator(
            geminiApiKey = geminiApiKey,
            bhashiniEndpoint = bhashiniEndpointUrl,
            bhashiniToken = bhashiniAuthToken,
            initialLanguage = sttLanguage,
            context = context
        )
    }

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerphone = MutableStateFlow(true)
    val isSpeakerphone: StateFlow<Boolean> = _isSpeakerphone.asStateFlow()

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        if (::rtcEngine.isInitialized) {
            try {
                rtcEngine.muteLocalAudioStream(muted)
                CallDiagnostics.log("RTC", "Local microphone mute state changed to: $muted")
            } catch (e: Exception) {
                CallDiagnostics.log("RTC", "Failed to mute audio stream: ${e.message}", LogLevel.WARN)
            }
        }
        updatePills()
    }

    fun toggleMute() = setMuted(!_isMuted.value)

    fun setSpeakerphone(speaker: Boolean) {
        _isSpeakerphone.value = speaker
        if (::rtcEngine.isInitialized) {
            try {
                rtcEngine.setEnableSpeakerphone(speaker)
                CallDiagnostics.log("RTC", "Speakerphone routing set to: $speaker")
            } catch (e: Exception) {
                CallDiagnostics.log("RTC", "Failed to set speakerphone: ${e.message}", LogLevel.WARN)
            }
        }
    }

    fun toggleSpeakerphone() = setSpeakerphone(!_isSpeakerphone.value)

    private val fallbackSttEngineMode: StateFlow<SttEngineMode> = MutableStateFlow(SttEngineMode.WHISPER_OFFLINE)
    val sttEngineMode: StateFlow<SttEngineMode> get() = if (enableAiPipeline) pipelineCoordinator.sttEngineMode else fallbackSttEngineMode

    private val fallbackSttEngineLabel: StateFlow<String> = MutableStateFlow("Direct Two-Way Audio (Pure VoIP)")
    val sttEngineLabel: StateFlow<String> get() = if (enableAiPipeline) pipelineCoordinator.sttEngineLabel else fallbackSttEngineLabel

    fun setSttEngineMode(mode: SttEngineMode) {
        if (enableAiPipeline) {
            pipelineCoordinator.setSttEngineMode(mode)
        }
    }

    private val _currentSttLanguage = MutableStateFlow(sttLanguage)
    val currentSttLanguage: StateFlow<String> = _currentSttLanguage.asStateFlow()

    fun setSttLanguage(newLanguage: String) {
        if (sttLanguage == newLanguage) return
        sttLanguage = newLanguage
        _currentSttLanguage.value = newLanguage
        CallDiagnostics.log("STT", "Call language changed to $newLanguage")
        if (enableAiPipeline) {
            pipelineCoordinator.setLanguage(newLanguage)
        }
    }

    private lateinit var rtcEngine: RtcEngine
    private var pillMonitorJob: Job? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var headsetReceiver: BroadcastReceiver? = null

    private val _transcripts = MutableSharedFlow<TranscriptLine>(extraBufferCapacity = 64)
    val transcriptsFlow: SharedFlow<TranscriptLine> = _transcripts.asSharedFlow()

    private val _riskState = MutableStateFlow(LiveRiskState())
    val riskState: StateFlow<LiveRiskState> = _riskState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _pillsState = MutableStateFlow(DiagnosticsPillsState())
    val pillsState: StateFlow<DiagnosticsPillsState> = _pillsState.asStateFlow()

    private var rtcJoinedFlag = false
    private var peerPresentFlag = false
    private var callStartTs = 0L

    var onTokenRenewalNeeded: (() -> Unit)? = null
    var onRemoteUserHungUp: ((reason: Int) -> Unit)? = null
    var onConnectionStateUpdate: ((state: Int, reason: Int) -> Unit)? = null
    var onErrorOccurred: ((err: Int) -> Unit)? = null

    private var connectivityManager: android.net.ConnectivityManager? = null
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    private val eventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            CallDiagnostics.log("RTC", "onJoinChannelSuccess: channel=$channel, uid=$uid, elapsed=${elapsed}ms")
            rtcJoinedFlag = true
            _isConnected.value = true
            updatePills()
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            CallDiagnostics.log("RTC", "onUserJoined: peer uid=$uid, elapsed=${elapsed}ms")
            peerPresentFlag = true
            try {
                rtcEngine.muteRemoteAudioStream(uid, false)
            } catch (_: Exception) {}
            updatePills()
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            CallDiagnostics.log("RTC", "onUserOffline: peer uid=$uid, reason=$reason", LogLevel.WARN)
            peerPresentFlag = false
            updatePills()
            onRemoteUserHungUp?.invoke(reason)
        }

        override fun onLeaveChannel(stats: RtcStats?) {
            CallDiagnostics.log("RTC", "onLeaveChannel: duration=${stats?.totalDuration}s")
            rtcJoinedFlag = false
            peerPresentFlag = false
            _isConnected.value = false
            updatePills()
        }

        override fun onTokenPrivilegeWillExpire(token: String?) {
            CallDiagnostics.log("RTC", "onTokenPrivilegeWillExpire: Token expiring soon, requesting renewal...", LogLevel.WARN)
            onTokenRenewalNeeded?.invoke()
        }

        override fun onRequestToken() {
            CallDiagnostics.log("RTC", "onRequestToken: Token expired, requesting renewal...", LogLevel.WARN)
            onTokenRenewalNeeded?.invoke()
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            val stateName = when (state) {
                1 -> "DISCONNECTED"
                2 -> "CONNECTING"
                3 -> "CONNECTED"
                4 -> "RECONNECTING"
                5 -> "FAILED"
                else -> "UNKNOWN($state)"
            }
            CallDiagnostics.log("RTC", "onConnectionStateChanged: state=$stateName, reason=$reason")
            onConnectionStateUpdate?.invoke(state, reason)
        }

        override fun onError(err: Int) {
            CallDiagnostics.log("RTC", "Agora RTC error code: $err", LogLevel.ERROR)
            onErrorOccurred?.invoke(err)
        }
    }

    init {
        if (enableAiPipeline) {
            // Forward transcripts from the pipeline merger to the UI flow
            scope.launch {
                pipelineCoordinator.transcriptsFlow.collect { line ->
                    val speaker = if (line.source == AudioSource.LOCAL) Speaker.LOCAL else Speaker.REMOTE
                    _transcripts.tryEmit(
                        TranscriptLine(
                            speaker = speaker,
                            text = line.text,
                            isFinal = line.isFinal,
                            timestampMs = line.timestampMs
                        )
                    )
                }
            }

            // Forward risk updates from the pipeline risk engine to the UI flow
            scope.launch {
                pipelineCoordinator.riskFlow.collect { risk ->
                    val victim = when (risk.victimRole) {
                        "local" -> Speaker.LOCAL
                        "remote" -> Speaker.REMOTE
                        else -> null
                    }
                    val scammer = when (risk.scammerRole) {
                        "local" -> Speaker.LOCAL
                        "remote" -> Speaker.REMOTE
                        else -> null
                    }
                    val pressure = risk.tactics.firstOrNull { t ->
                        t.contains("pressure", ignoreCase = true) ||
                        t.contains("urgency", ignoreCase = true) ||
                        t.contains("arrest", ignoreCase = true) ||
                        t.contains("coercion", ignoreCase = true)
                    }

                    _riskState.value = LiveRiskState(
                        riskScore = risk.riskScore,
                        victimRole = victim,
                        scammerRole = scammer,
                        roleConfidence = risk.roleConfidence,
                        topSignals = risk.tactics,
                        psychologicalPressure = pressure
                    )
                }
            }

            // React to STT connection status
            scope.launch {
                pipelineCoordinator.isLocalSttConnected.collect { updatePills() }
            }
            scope.launch {
                pipelineCoordinator.isRemoteSttConnected.collect { updatePills() }
            }
        }
    }

    fun renewToken(newToken: String) {
        if (this::rtcEngine.isInitialized) {
            val res = rtcEngine.renewToken(newToken)
            CallDiagnostics.log("RTC", "renewToken result=$res for new token length=${newToken.length}")
        }
    }

    fun startCall(channelName: String, token: String, uid: Int = 0) {
        callStartTs = System.currentTimeMillis()
        try {
            if (agoraAppId.isBlank()) {
                CallDiagnostics.log("RTC", "Agora App ID is unconfigured or blank; direct dev mode fallback", LogLevel.WARN)
                onErrorOccurred?.invoke(101)
                return
            }
            rtcEngine = AgoraEngineManager.get(context, agoraAppId, eventHandler)
            try { rtcEngine.addHandler(eventHandler) } catch (_: Exception) {}

            val myUid = if (uid != 0) uid else (100000..999999).random()

            // 1. Audio Scenario & Profile Configuration
            rtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            rtcEngine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
            rtcEngine.setAudioProfile(Constants.AUDIO_PROFILE_DEFAULT, Constants.AUDIO_SCENARIO_DEFAULT)
            rtcEngine.enableAudio()
            rtcEngine.enableLocalAudio(true)
            rtcEngine.muteLocalAudioStream(false)
            rtcEngine.muteAllRemoteAudioStreams(false)
            rtcEngine.setDefaultAudioRoutetoSpeakerphone(true)
            rtcEngine.setEnableSpeakerphone(true)
            rtcEngine.adjustRecordingSignalVolume(100)
            rtcEngine.adjustPlaybackSignalVolume(100)
            rtcEngine.enableAudioVolumeIndication(200, 3, true)

            setupAudioRouting()

            // 2. Attach Guardian Pipeline Coordinator (registers AudioFrameBridge and starts pipelines)
            if (enableAiPipeline) {
                pipelineCoordinator.attachToAgora(rtcEngine)
            } else {
                CallDiagnostics.log("RTC", "Pure minimal VoIP mode active: AI/STT pipeline detached for zero-latency direct two-way audio.")
            }

            // 3. Start diagnostic pill monitoring loop
            startPillMonitor()

            // 4. Join channel
            val tokenToPass = if (token.isNullOrBlank()) null else token
            val options = ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                publishMicrophoneTrack = true
                autoSubscribeAudio = true
            }

            CallDiagnostics.log("RTC", "joinChannel starting for channel '$channelName', myUid=$myUid, tokenPresent=${tokenToPass != null}")
            var res = rtcEngine.joinChannel(tokenToPass, channelName, myUid, options)
            if (res != 0) {
                CallDiagnostics.log("RTC", "joinChannel with options returned $res, retrying standard joinChannel...", LogLevel.WARN)
                res = rtcEngine.joinChannel(tokenToPass, channelName, "", myUid)
            }
            CallDiagnostics.log("RTC", "joinChannel return code: $res", if (res == 0) LogLevel.INFO else LogLevel.ERROR)
            if (res != 0) {
                CallDiagnostics.log("RTC", "joinChannel invocation failed with code $res", LogLevel.ERROR)
                onErrorOccurred?.invoke(res)
            }
        } catch (t: Throwable) {
            CallDiagnostics.log("RTC", "FATAL: startCall initialization error: ${t.message}", LogLevel.ERROR)
            onErrorOccurred?.invoke(-999)
        }
    }

    private fun setupAudioRouting() {
        headsetReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_HEADSET_PLUG) {
                    val state = intent.getIntExtra("state", -1)
                    val isPlugged = (state == 1)
                    CallDiagnostics.log("PCM", "Headset event: plugged=$isPlugged")
                    if (::rtcEngine.isInitialized) {
                        rtcEngine.setEnableSpeakerphone(!isPlugged)
                    }
                }
            }
        }
        try {
            context.registerReceiver(headsetReceiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))
        } catch (_: Exception) {}

        setupNetworkHandoffListener()
    }

    private fun setupNetworkHandoffListener() {
        try {
            connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val request = android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    CallDiagnostics.log("RTC", "Network available: $network")
                }

                override fun onLost(network: android.net.Network) {
                    CallDiagnostics.log("RTC", "Network lost: $network, waiting for handoff...", LogLevel.WARN)
                }

                override fun onCapabilitiesChanged(
                    network: android.net.Network,
                    capabilities: android.net.NetworkCapabilities
                ) {
                    val isWifi = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                    val isCellular = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                    val transportType = when {
                        isWifi -> "Wi-Fi"
                        isCellular -> "Cellular/LTE"
                        else -> "Other"
                    }
                    CallDiagnostics.log("RTC", "Network handoff updated: active transport = $transportType")
                }
            }
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            CallDiagnostics.log("RTC", "Network handoff listener failed: ${e.message}", LogLevel.WARN)
        }
    }

    fun endCall() {
        CallDiagnostics.log("RTC", "Ending call")
        pillMonitorJob?.cancel()

        // Decouple pipeline if active
        if (enableAiPipeline) {
            pipelineCoordinator.stop()
        }

        if (this::rtcEngine.isInitialized) {
            try {
                rtcEngine.leaveChannel()
                rtcEngine.removeHandler(eventHandler)
            } catch (e: Exception) {
                CallDiagnostics.log("RTC", "Error in endCall: ${e.message}", LogLevel.WARN)
            }
        }

        headsetReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        networkCallback?.let {
            try { connectivityManager?.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }

        rtcJoinedFlag = false
        peerPresentFlag = false
        _isConnected.value = false
        updatePills()
    }

    private fun startPillMonitor() {
        pillMonitorJob?.cancel()
        pillMonitorJob = scope.launch {
            while (isActive) {
                delay(2000)
                updatePills()
            }
        }
    }

    private fun updatePills() {
        if (!enableAiPipeline) {
            _pillsState.value = DiagnosticsPillsState(
                rtcJoined = if (rtcJoinedFlag) PillStatus.GREEN else PillStatus.RED,
                peerPresent = if (peerPresentFlag) PillStatus.GREEN else PillStatus.AMBER,
                localPcmFlowing = if (rtcJoinedFlag && !_isMuted.value) PillStatus.GREEN else PillStatus.RED,
                remotePcmFlowing = if (peerPresentFlag) PillStatus.GREEN else PillStatus.AMBER,
                sttLocalStatus = PillStatus.AMBER, // Marked standby in pure VoIP mode
                sttRemoteStatus = PillStatus.AMBER,
                localPcmCount = if (rtcJoinedFlag) 1 else 0,
                remotePcmCount = if (peerPresentFlag) 1 else 0,
                localNoAudioWarning = false,
                remoteNoAudioWarning = false
            )
            return
        }

        val locCount = pipelineCoordinator.localPipeline.receivedFramesCount
        val remCount = pipelineCoordinator.remotePipeline.receivedFramesCount
        val locRms = pipelineCoordinator.localPipeline.latestRms
        val remRms = pipelineCoordinator.remotePipeline.latestRms

        val callElapsed = System.currentTimeMillis() - callStartTs
        val locDead = locCount > 0 && locRms < 1.0 && callElapsed > 3000
        val remDead = peerPresentFlag && remCount > 0 && remRms < 1.0 && callElapsed > 3000

        val sttLocConn = pipelineCoordinator.isLocalSttConnected.value
        val sttRemConn = pipelineCoordinator.isRemoteSttConnected.value

        val locStatus = when {
            locDead -> PillStatus.RED
            locCount > 0 && locRms >= 1.0 -> PillStatus.GREEN
            locCount > 0 -> PillStatus.AMBER
            else -> PillStatus.RED
        }

        val remStatus = when {
            !peerPresentFlag -> PillStatus.RED
            remDead -> PillStatus.RED
            remCount > 0 && remRms >= 1.0 -> PillStatus.GREEN
            remCount > 0 -> PillStatus.AMBER
            else -> PillStatus.RED
        }

        _pillsState.value = DiagnosticsPillsState(
            rtcJoined = if (rtcJoinedFlag) PillStatus.GREEN else PillStatus.RED,
            peerPresent = if (peerPresentFlag) PillStatus.GREEN else PillStatus.AMBER,
            localPcmFlowing = locStatus,
            remotePcmFlowing = remStatus,
            sttLocalStatus = if (sttLocConn) PillStatus.GREEN else PillStatus.RED,
            sttRemoteStatus = if (sttRemConn) PillStatus.GREEN else PillStatus.RED,
            localPcmCount = locCount,
            remotePcmCount = remCount,
            localNoAudioWarning = locDead,
            remoteNoAudioWarning = remDead
        )
    }
}
