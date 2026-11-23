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
    val logs: List<DiagnosticEntry> = emptyList()
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
            val banner = when (state) {
                4 -> "Network unstable, reconnecting..."
                5 -> "Call connection failed"
                else -> null
            }
            _uiState.value = _uiState.value.copy(connectionBanner = banner)
        }

        manager.onErrorOccurred = { err ->
            val message = when (err) {
                109, 110 -> "Agora token invalid/expired (Code $err)"
                101 -> "CALLING NOT CONFIGURED: Agora App ID unconfigured (Code $err)"
                17 -> "Join channel rejected by server (Code $err)"
                -999 -> "Audio/Agora initialization failure (Code $err)"
                else -> "Call connection error (Code $err)"
            }
            CallDiagnostics.log("RTC", "Agora Error: $message", LogLevel.ERROR)
            _uiState.value = _uiState.value.copy(connectionBanner = message)
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
            _uiState.value = _uiState.value.copy(isCallActive = true, channelName = channelName)
            manager.startCall(channelName, token)
        }
    }

    fun startCall(channelName: String, peerPhone: String = "") {
        if (peerPhone.isNotBlank()) this.peerNumber = peerPhone
        familyAlertSent = false
        viewModelScope.launch {
            CallDiagnostics.log("RTC", "Initiating Agora VoIP call for channel '$channelName', requesting token...")
            _uiState.value = _uiState.value.copy(isCallActive = true, channelName = channelName, connectionBanner = "Connecting...")
            val token = fetchAgoraToken(channelName)
            if (token.isNotBlank()) {
                CallDiagnostics.log("RTC", "Acquired token from server, connecting to Agora channel '$channelName'")
            } else {
                CallDiagnostics.log("RTC", "No token returned by server; using direct authentication", LogLevel.INFO)
            }
            manager.startCall(channelName, token)
        }
    }

    fun endCall() {
        stopSimulation()
        familyAlertSent = false
        manager.endCall()
        _uiState.value = _uiState.value.copy(isCallActive = false, isConnected = false)
    }

    fun simulateScamCall() {
        simulationJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isConnected = true,
            isCallActive = true,
            connectionBanner = "SIMULATION ACTIVE: Demonstrating real-time VoIP extortion defense"
        )
        viewModelScope.launch {
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
            connectionBanner = if (_uiState.value.isConnected) null else "Call connection standby"
        )
    }

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

    private suspend fun fetchAgoraToken(channelName: String): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$tokenServerUrl?channelName=$channelName")
                .get()
                .build()
            http.newCall(request).execute().use { resp ->
                val body = resp.body?.string()
                CallDiagnostics.log("RTC", "Token HTTP response code: ${resp.code}")
                if (resp.isSuccessful && !body.isNullOrBlank()) {
                    val token = JSONObject(body).optString("token", "")
                    CallDiagnostics.log("RTC", "Token received successfully (length=${token.length})")
                    token
                } else ""
            }
        } catch (e: Exception) {
            CallDiagnostics.log("RTC", "Token request exception: ${e.message}", com.guardian.app.voip.LogLevel.WARN)
            ""
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (_uiState.value.isCallActive) manager.endCall()
    }
}
