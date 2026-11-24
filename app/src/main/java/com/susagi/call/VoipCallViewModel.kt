package com.susagi.call

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.guardian.app.BuildConfig
import com.guardian.app.voip.CallDiagnostics
import com.guardian.app.voip.DiagnosticEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.guardian.app.voip.LogLevel
import com.susagi.stt.SttEngineMode
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class TranscriptUiLine(
    val speaker: Speaker,
    val text: String,
    val roleLabel: String
)

data class VoipUiState(
    val transcriptLines: List<TranscriptUiLine> = emptyList(),
    val riskScore: Int = 0,
    val victimRole: String = "Unknown",
    val scammerRole: String = "Unknown",
    val roleConfidence: Float = 0f,
    val topSignals: List<String> = emptyList(),
    val psychologicalPressure: String? = null,
    val isConnected: Boolean = false,
    val isCallActive: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeakerphone: Boolean = true,
    val channelName: String = "",
    val connectionBanner: String? = null,
    val activeSttEngine: String = "Direct Two-Way VoIP",
    val pills: DiagnosticsPillsState = DiagnosticsPillsState(),
    val logs: List<DiagnosticEntry> = emptyList(),
    val callState: CallConnectionState = CallConnectionState.IDLE,
    val diagnosticReason: String? = null,
    val isSimulatedCall: Boolean = false,
    val isTranscriptionActive: Boolean = false
)

class VoipCallViewModel @JvmOverloads constructor(
    application: Application,
    agoraAppId: String = BuildConfig.AGORA_APP_ID,
    geminiApiKey: String = BuildConfig.GEMINI_API_KEY,
    bhashiniEndpointUrl: String = BuildConfig.BHASHINI_STT_ENDPOINT.ifBlank { "https://dhruva-api.bhashini.gov.in/services/inference/pipeline" },
    bhashiniAuthToken: String? = BuildConfig.BHASHINI_INFERENCE_API_KEY,
    private val tokenServerUrl: String = BuildConfig.BACKEND_URL + "/agora/token"
) : AndroidViewModel(application) {

    private val manager = VoiceCallManager(
        context = application.applicationContext,
        agoraAppId = agoraAppId,
        geminiApiKey = geminiApiKey,
        bhashiniEndpointUrl = bhashiniEndpointUrl,
        bhashiniAuthToken = bhashiniAuthToken,
        enableAiPipeline = true
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .build()
    private val rawTranscriptLines = mutableListOf<TranscriptLine>()
    private var peerNumber: String = ""
    private var familyAlertSent: Boolean = false
    private var simulationJob: Job? = null

    private val _uiState = MutableStateFlow(VoipUiState())
    val uiState: StateFlow<VoipUiState> = _uiState.asStateFlow()

    fun setPeerInfo(number: String) {
        if (number.isNotBlank()) {
            this.peerNumber = number
        }
    }

    init {
        if (agoraAppId.isBlank()) {
            _uiState.value = _uiState.value.copy(connectionBanner = "CALLING NOT CONFIGURED: Agora App ID unconfigured")
        }

        manager.onTokenRenewalNeeded = {
            viewModelScope.launch {
                val currentChannel = _uiState.value.channelName
                if (currentChannel.isNotBlank()) {
                    CallDiagnostics.log("RTC", "Token renewal triggered for channel '$currentChannel'")
                    val newToken = fetchAgoraToken(currentChannel)
                    if (newToken.isNotBlank()) {
                        manager.renewToken(newToken)
                    }
                }
            }
        }

        manager.onRemoteUserHungUp = { reason ->
            if (reason == io.agora.rtc2.Constants.USER_OFFLINE_QUIT) {
                CallDiagnostics.log("SIG", "Remote peer hung up (USER_OFFLINE_QUIT)")
                _uiState.value = _uiState.value.copy(connectionBanner = "Remote caller hung up.")
            } else {
                CallDiagnostics.log("SIG", "Remote peer connection dropped/reconnecting (reason=$reason)", LogLevel.WARN)
                _uiState.value = _uiState.value.copy(connectionBanner = "Remote caller connection unstable, waiting...")
            }
        }

        manager.onConnectionStateUpdate = { state, reason ->
            val reasonDesc = VoiceCallManager.mapConnectionReason(reason)
            when (state) {
                1 -> {
                    _uiState.value = _uiState.value.copy(
                        callState = CallConnectionState.DISCONNECTED,
                        connectionBanner = "Disconnected ($reasonDesc)"
                    )
                }
                2 -> {
                    _uiState.value = _uiState.value.copy(
                        callState = CallConnectionState.CONNECTING,
                        connectionBanner = "Connecting to Agora RTC room..."
                    )
                }
                3 -> {
                    _uiState.value = _uiState.value.copy(
                        callState = if (_uiState.value.pills.peerPresent == PillStatus.GREEN) CallConnectionState.PEER_CONNECTED else CallConnectionState.JOINED,
                        connectionBanner = null,
                        diagnosticReason = null
                    )
                }
                4 -> {
                    _uiState.value = _uiState.value.copy(
                        callState = CallConnectionState.RECONNECTING,
                        connectionBanner = "Network unstable, reconnecting... ($reasonDesc)"
                    )
                }
                5 -> {
                    val detail = buildString {
                        append("Agora connection failed: $reasonDesc.")
                        val tokenErr = _uiState.value.diagnosticReason
                        if (!tokenErr.isNullOrBlank()) {
                            append(" [$tokenErr]")
                        }
                        if (reason == 9 || reason == 10) {
                            append(" (RTC Token required by project)")
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        callState = CallConnectionState.FAILED,
                        connectionBanner = "Call connection failed: $reasonDesc",
                        diagnosticReason = detail
                    )
                }
            }
        }

        manager.onErrorOccurred = { err ->
            val message = VoiceCallManager.mapErrorCode(err)
            CallDiagnostics.log("RTC", "Agora Error: $message", LogLevel.ERROR)
            _uiState.value = _uiState.value.copy(
                callState = CallConnectionState.FAILED,
                connectionBanner = message,
                diagnosticReason = message
            )
        }

        viewModelScope.launch {
            manager.callConnectionState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    callState = state,
                    isTranscriptionActive = (state == CallConnectionState.AUDIO_ACTIVE)
                )
            }
        }

        viewModelScope.launch {
            manager.transcriptsFlow.collect { line ->
                if (line.isFinal) {
                    rawTranscriptLines.add(line)
                    rebuildTranscriptUi()
                }
            }
        }
        viewModelScope.launch {
            manager.riskState.collect { risk ->
                _uiState.value = _uiState.value.copy(
                    riskScore = risk.riskScore,
                    victimRole = risk.victimRole?.let { roleLabelFor(it) } ?: "Unknown",
                    scammerRole = risk.scammerRole?.let { roleLabelFor(it) } ?: "Unknown",
                    roleConfidence = risk.roleConfidence.toFloat(),
                    topSignals = risk.topSignals,
                    psychologicalPressure = risk.psychologicalPressure
                )
                rebuildTranscriptUi()

                if (risk.riskScore >= 75 && !familyAlertSent) {
                    familyAlertSent = true
                    val summary = rawTranscriptLines.takeLast(6).joinToString(" ") { it.text }
                    val scamTypeDesc = if (risk.scammerRole != null) "Impersonation / High-Risk Fraud" else "Suspicious Call Pattern"
                    sendFamilyAlert(
                        riskScore = risk.riskScore,
                        callerNumber = peerNumber,
                        scamType = scamTypeDesc,
                        transcriptSummary = summary
                    )
                }
            }
        }
        viewModelScope.launch {
            manager.isConnected.collect { connected ->
                _uiState.value = _uiState.value.copy(isConnected = connected)
            }
        }
        viewModelScope.launch {
            manager.pillsState.collect { pills ->
                _uiState.value = _uiState.value.copy(pills = pills)
            }
        }
        viewModelScope.launch {
            CallDiagnostics.logs.collect { logs ->
                _uiState.value = _uiState.value.copy(logs = logs)
            }
        }
        viewModelScope.launch {
            manager.sttEngineLabel.collect { label ->
                _uiState.value = _uiState.value.copy(activeSttEngine = label)
            }
        }
        viewModelScope.launch {
            manager.isMuted.collect { muted ->
                _uiState.value = _uiState.value.copy(isMuted = muted)
            }
        }
        viewModelScope.launch {
            manager.isSpeakerphone.collect { speaker ->
                _uiState.value = _uiState.value.copy(isSpeakerphone = speaker)
            }
        }
    }

    fun toggleMute() = manager.toggleMute()
    fun toggleSpeakerphone() = manager.toggleSpeakerphone()

    val sttEngineMode: StateFlow<SttEngineMode> = manager.sttEngineMode

    fun setSttEngineMode(mode: SttEngineMode) {
        manager.setSttEngineMode(mode)
    }

    private fun roleLabelFor(speaker: Speaker): String = if (speaker == Speaker.LOCAL) "You" else "Caller"

    private fun rebuildTranscriptUi() {
        val risk = manager.riskState.value
        val uiLines = rawTranscriptLines.map { line ->
            val isVictim = risk.victimRole == line.speaker
            val isScammer = risk.scammerRole == line.speaker
            val label = when {
                isScammer -> if (line.speaker == Speaker.LOCAL) "You (flagged)" else "Scammer"
                isVictim -> if (line.speaker == Speaker.LOCAL) "You" else "Victim"
                line.speaker == Speaker.LOCAL -> "You"
                else -> "Caller"
            }
            TranscriptUiLine(line.speaker, line.text, label)
        }
        _uiState.value = _uiState.value.copy(transcriptLines = uiLines)
    }

    fun startCallWithToken(channelName: String, token: String, peerPhone: String = "") {
        if (peerPhone.isNotBlank()) this.peerNumber = peerPhone
        familyAlertSent = false
        viewModelScope.launch {
            CallDiagnostics.log("RTC", "Starting call using server-issued RTC token for channel '$channelName'")
            _uiState.value = _uiState.value.copy(
                isCallActive = true,
                channelName = channelName,
                callState = CallConnectionState.CONNECTING,
                connectionBanner = "Connecting with pre-issued token...",
                diagnosticReason = null,
                isSimulatedCall = false
            )
            manager.startCall(channelName, token)
        }
    }

    fun startCall(channelName: String, peerPhone: String = "") {
        if (peerPhone.isNotBlank()) this.peerNumber = peerPhone
        familyAlertSent = false
        viewModelScope.launch {
            CallDiagnostics.log("RTC", "Initiating Agora VoIP call for channel '$channelName', requesting token from $tokenServerUrl...")
            _uiState.value = _uiState.value.copy(
                isCallActive = true,
                channelName = channelName,
                callState = CallConnectionState.REQUESTING_TOKEN,
                connectionBanner = "Requesting token from $tokenServerUrl...",
                diagnosticReason = null,
                isSimulatedCall = false
            )
            val tokenResult = fetchAgoraTokenDetailed(channelName)
            if (tokenResult.token.isNotBlank()) {
                CallDiagnostics.log("RTC", "Acquired token from server (${tokenResult.latencyMs}ms), connecting to Agora channel '$channelName'")
                _uiState.value = _uiState.value.copy(
                    callState = CallConnectionState.CONNECTING,
                    connectionBanner = "Connecting to encrypted Agora room..."
                )
            } else {
                CallDiagnostics.log("RTC", "Token acquisition failed: ${tokenResult.error}; attempting direct connection", LogLevel.WARN)
                _uiState.value = _uiState.value.copy(
                    diagnosticReason = tokenResult.error,
                    connectionBanner = "Connecting (Token Server: ${tokenResult.error})..."
                )
            }
            manager.startCall(channelName, tokenResult.token)
        }
    }

    fun endCall() {
        stopSimulation()
        familyAlertSent = false
        manager.endCall()
        _uiState.value = _uiState.value.copy(
            isCallActive = false,
            isConnected = false,
            callState = CallConnectionState.ENDED,
            connectionBanner = null,
            diagnosticReason = null,
            isSimulatedCall = false
        )
    }

    fun simulateScamCall() {
        simulationJob?.cancel()
        _uiState.value = _uiState.value.copy(
            callState = CallConnectionState.AUDIO_ACTIVE,
            isConnected = true,
            isCallActive = true,
            isTranscriptionActive = true,
            isSimulatedCall = true,
            diagnosticReason = null,
            connectionBanner = "DEMO — SYNTHETIC INPUT (Offline Extortion Simulation)"
        )
        simulationJob = viewModelScope.launch {
            val script = listOf(
                Pair(Speaker.REMOTE, "This is Officer Rathore from CBI Cyber Crime Cell Mumbai."),
                Pair(Speaker.LOCAL, "Hello? Officer, why are you calling me?"),
                Pair(Speaker.REMOTE, "A parcel shipped in your name to Cambodia containing illegal narcotics was seized at customs."),
                Pair(Speaker.LOCAL, "I never sent any parcel, this must be a mistake!"),
                Pair(Speaker.REMOTE, "Your Aadhaar number is linked. A non-bailable arrest warrant has been issued. You are placed under digital arrest right now."),
                Pair(Speaker.LOCAL, "Sir please help me, I am innocent, what should I do?"),
                Pair(Speaker.REMOTE, "Do not disconnect this line. Transfer Rs 45,000 verification clearance fee to RBI Treasury account or police will arrive at your home in 30 minutes.")
            )

            rawTranscriptLines.clear()
            for ((speaker, text) in script) {
                kotlinx.coroutines.delay(1600)
                rawTranscriptLines.add(TranscriptLine(speaker, text, isFinal = true))
                val currentRisk = when (rawTranscriptLines.size) {
                    1 -> 25
                    2 -> 25
                    3 -> 55
                    4 -> 55
                    5 -> 78
                    6 -> 78
                    7 -> 92
                    else -> _uiState.value.riskScore
                }
                val tactics = mutableListOf<String>()
                if (rawTranscriptLines.size >= 1) tactics.add("Authority Impersonation (CBI / Police)")
                if (rawTranscriptLines.size >= 3) tactics.add("Illegal Parcel Narcotics Pretext")
                if (rawTranscriptLines.size >= 5) tactics.add("Digital Arrest & Isolation")
                if (rawTranscriptLines.size >= 7) tactics.add("Urgent Treasury Verification Fee Extortion")

                _uiState.value = _uiState.value.copy(
                    callState = CallConnectionState.AUDIO_ACTIVE,
                    isTranscriptionActive = true,
                    riskScore = currentRisk,
                    victimRole = "You",
                    scammerRole = "Caller (Flagged)",
                    roleConfidence = 0.94f,
                    topSignals = tactics,
                    psychologicalPressure = if (currentRisk >= 75) "Digital Arrest & Police Seclusion" else null,
                    pills = DiagnosticsPillsState(
                        rtcJoined = PillStatus.GREEN,
                        peerPresent = PillStatus.GREEN,
                        localPcmFlowing = PillStatus.GREEN,
                        remotePcmFlowing = PillStatus.GREEN,
                        sttLocalStatus = PillStatus.GREEN,
                        sttRemoteStatus = PillStatus.GREEN
                    )
                )
                rebuildTranscriptUi()

                if (currentRisk >= 75 && !familyAlertSent) {
                    familyAlertSent = true
                    sendFamilyAlert(
                        riskScore = currentRisk,
                        callerNumber = peerNumber.ifBlank { "+91 98765 43210 (Scam Simulation)" },
                        scamType = "Digital Arrest Extortion",
                        transcriptSummary = text
                    )
                }
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        _uiState.value = _uiState.value.copy(
            isSimulatedCall = false,
            callState = CallConnectionState.IDLE,
            isTranscriptionActive = false,
            connectionBanner = null,
            diagnosticReason = null
        )
    }

    data class TokenFetchResult(
        val token: String,
        val latencyMs: Long,
        val error: String? = null
    )

    suspend fun fetchAgoraTokenDetailed(channelName: String): TokenFetchResult = withContext(Dispatchers.IO) {
        val startTs = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url("$tokenServerUrl?channelName=$channelName")
                .get()
                .build()
            http.newCall(request).execute().use { resp ->
                val latency = System.currentTimeMillis() - startTs
                val body = resp.body?.string()
                CallDiagnostics.log("RTC", "Token HTTP response code: ${resp.code} (${latency}ms)")
                if (resp.isSuccessful && !body.isNullOrBlank()) {
                    val token = JSONObject(body).optString("token", "")
                    if (token.isNotBlank()) {
                        CallDiagnostics.log("RTC", "Token received successfully (length=${token.length})")
                        TokenFetchResult(token, latency)
                    } else {
                        TokenFetchResult("", latency, "Backend returned empty token (App Certificate unconfigured)")
                    }
                } else {
                    TokenFetchResult("", latency, "Backend returned HTTP ${resp.code}")
                }
            }
        } catch (e: java.net.ConnectException) {
            val latency = System.currentTimeMillis() - startTs
            CallDiagnostics.log("RTC", "Token server connection refused at $tokenServerUrl (${latency}ms)", LogLevel.WARN)
            TokenFetchResult("", latency, "Token backend unreachable at $tokenServerUrl (Connection refused on port 3001)")
        } catch (e: java.net.SocketTimeoutException) {
            val latency = System.currentTimeMillis() - startTs
            CallDiagnostics.log("RTC", "Token server timed out at $tokenServerUrl (>1000ms)", LogLevel.WARN)
            TokenFetchResult("", latency, "Token backend timed out at $tokenServerUrl (>1000ms)")
        } catch (e: SecurityException) {
            val latency = System.currentTimeMillis() - startTs
            CallDiagnostics.log("RTC", "Cleartext HTTP blocked by network security policy", LogLevel.WARN)
            TokenFetchResult("", latency, "Cleartext HTTP traffic blocked by Android security policy")
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTs
            CallDiagnostics.log("RTC", "Token request exception: ${e.message}", LogLevel.WARN)
            TokenFetchResult("", latency, e.message ?: "Token request failed")
        }
    }

    private suspend fun fetchAgoraToken(channelName: String): String = fetchAgoraTokenDetailed(channelName).token

    private fun sendFamilyAlert(
        riskScore: Int,
        callerNumber: String,
        scamType: String,
        transcriptSummary: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val effectiveCaller = callerNumber.ifBlank { _uiState.value.channelName.ifBlank { "VoIP Caller" } }
            val backendUrl = BuildConfig.BACKEND_URL.trimEnd('/')

            // 1. Dispatch to configured Saved Emergency Contact
            try {
                val sentToTrusted = com.guardian.app.protect.advanced.TrustedContactAlertSender.sendAlert(
                    context = app.applicationContext,
                    callerNumber = effectiveCaller,
                    riskScore = riskScore,
                    topSignals = listOf(scamType, "Psychological Coercion / Urgency"),
                    backendUrl = backendUrl
                )
                if (sentToTrusted) {
                    CallDiagnostics.log("SIG", "Emergency alert successfully delivered to saved trusted contact!")
                } else {
                    CallDiagnostics.log("SIG", "Saved contact alert skipped (no contact configured or disabled)")
                }
            } catch (e: Exception) {
                CallDiagnostics.log("SIG", "Trusted contact alert error: ${e.message}", LogLevel.WARN)
            }

            // 2. Dispatch to Ably + FCM family mesh
            try {
                val prefs = app.getSharedPreferences("guardian_secure_prefs", android.content.Context.MODE_PRIVATE)
                val userId = prefs.getString("user_id", "protected_user_1") ?: "protected_user_1"

                val payload = JSONObject().apply {
                    put("protectedUserId", userId)
                    put("callerNumber", effectiveCaller)
                    put("riskScore", riskScore)
                    put("scamType", scamType)
                    put("transcriptSummary", transcriptSummary)
                    put("alertType", "scam_detected")
                }

                val body = payload.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$backendUrl/alerts/family")
                    .post(body)
                    .build()

                http.newCall(request).execute().use { resp ->
                    CallDiagnostics.log("SIG", "Family mesh alert dispatched (status=${resp.code}, risk=$riskScore)")
                }
            } catch (e: Exception) {
                CallDiagnostics.log("SIG", "Failed to dispatch family mesh alert: ${e.message}", LogLevel.WARN)
            }
        }
    }



    override fun onCleared() {
        super.onCleared()
        if (_uiState.value.isCallActive) manager.endCall()
    }
}
