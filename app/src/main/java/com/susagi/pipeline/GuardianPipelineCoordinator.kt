package com.susagi.pipeline

import android.content.Context
import com.guardian.app.voip.CallDiagnostics
import com.guardian.app.voip.LogLevel
import com.susagi.audio.AudioFrameBridge
import com.susagi.audio.AudioFramePipeline
import com.susagi.risk.GeminiRoleClient
import com.susagi.risk.LiveRiskEngine
import com.susagi.risk.RiskResult
import com.susagi.stt.DualSttController
import com.susagi.stt.SttEngineMode
import com.susagi.stt.TranscriptLine
import com.susagi.stt.TranscriptMerger
import io.agora.rtc2.Constants
import io.agora.rtc2.RtcEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PipelineState {
    IDLE,
    INITIALIZING,
    READY,
    RUNNING,
    STOPPING
}

/**
 * GuardianPipelineCoordinator
 *
 * Master orchestrator for SuSagi Guardian's real-time audio capture, dual-stream ASR,
 * and AI scam detection intelligence pipeline during Agora VoIP calls.
 *
 * Coordinates:
 * 1. Agora native PCM audio capture via [AudioFrameBridge].
 * 2. 16kHz mono normalization and 100ms slicing via [AudioFramePipeline].
 * 3. Concurrent dual-stream ASR via [DualSttController].
 * 4. Chronological dialogue merging via [TranscriptMerger].
 * 5. Semantic coercion & digital arrest analysis via [LiveRiskEngine].
 */
class GuardianPipelineCoordinator(
    private val geminiApiKey: String,
    private val bhashiniEndpoint: String,
    private val bhashiniToken: String? = null,
    private var initialLanguage: String = "hi",
    private val context: Context? = null,
    initialEngineMode: SttEngineMode = SttEngineMode.BHASHINI_ONLINE
) {
    private val _pipelineState = MutableStateFlow(PipelineState.IDLE)
    val pipelineState: StateFlow<PipelineState> = _pipelineState.asStateFlow()

    val merger = TranscriptMerger()
    private val geminiClient = GeminiRoleClient(apiKey = geminiApiKey)
    val threatEngine = com.susagi.intelligence.UnifiedThreatEngine()
    val riskEngine = LiveRiskEngine(geminiClient, merger, threatEngine)

    val dualStt = DualSttController(
        language = initialLanguage,
        endpointUrl = bhashiniEndpoint,
        authToken = bhashiniToken,
        merger = merger,
        context = context,
        initialEngineMode = initialEngineMode
    )

    val sttEngineMode: StateFlow<SttEngineMode> = dualStt.engineMode
    val sttEngineLabel: StateFlow<String> = dualStt.activeEngineLabel

    fun setSttEngineMode(mode: SttEngineMode) {
        dualStt.setEngineMode(mode)
    }

    val localPipeline = AudioFramePipeline(label = "local") { chunk ->
        dualStt.pushLocalPcmBytes(chunk, sampleRate = 16000)
    }

    val remotePipeline = AudioFramePipeline(label = "remote") { chunk ->
        dualStt.pushRemotePcmBytes(chunk, sampleRate = 16000)
    }

    private var audioFrameBridge: AudioFrameBridge? = null
    private var boundRtcEngine: RtcEngine? = null

    val transcriptsFlow: SharedFlow<TranscriptLine> = merger.transcriptUpdates
    val riskFlow: StateFlow<RiskResult> = riskEngine.riskResult

    val isLocalSttConnected: StateFlow<Boolean> = dualStt.isLocalConnected
    val isRemoteSttConnected: StateFlow<Boolean> = dualStt.isRemoteConnected

    /**
     * Initializes and starts the audio and intelligence pipeline.
     * Must be called before or immediately after joining the Agora channel.
     */
    fun attachToAgora(rtcEngine: RtcEngine) {
        if (_pipelineState.value == PipelineState.RUNNING) return
        _pipelineState.value = PipelineState.INITIALIZING
        CallDiagnostics.log("PIPELINE", "Initializing Guardian Pipeline...")

        this.boundRtcEngine = rtcEngine

        // 1. Configure Agora Audio Parameters for 16kHz 16-bit mono PCM
        try {
            rtcEngine.setRecordingAudioFrameParameters(
                16000, 1, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1600
            )
            rtcEngine.setPlaybackAudioFrameParameters(
                16000, 1, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1600
            )
            rtcEngine.setPlaybackAudioFrameBeforeMixingParameters(16000, 1)
        } catch (e: Throwable) {
            CallDiagnostics.log("PIPELINE", "Agora audio frame parameter configuration warning: ${e.message}", LogLevel.WARN)
        }

        // 2. Instantiate and register the native audio frame bridge
        try {
            val bridge = AudioFrameBridge(
                localPipeline = localPipeline,
                remotePipeline = remotePipeline
            ) { remoteUid ->
                CallDiagnostics.log("PIPELINE", "Remote participant audio UID detected: $remoteUid")
            }
            this.audioFrameBridge = bridge
            val resObserver = rtcEngine.registerAudioFrameObserver(bridge)
            CallDiagnostics.log("PIPELINE", "registerAudioFrameObserver result code: $resObserver")
        } catch (e: Throwable) {
            CallDiagnostics.log("PIPELINE", "registerAudioFrameObserver exception: ${e.message}", LogLevel.ERROR)
        }

        // 3. Start audio chunking pipelines
        localPipeline.start()
        remotePipeline.start()

        // 4. Start dual STT recognizers
        dualStt.start()

        // 5. Start live risk engine
        riskEngine.start()

        _pipelineState.value = PipelineState.RUNNING
        CallDiagnostics.log("PIPELINE", "Guardian Pipeline is fully operational (RUNNING)")
    }

    fun setLanguage(language: String) {
        this.initialLanguage = language
        dualStt.setLanguage(language)
    }

    /**
     * Gracefully stops and decouples all pipeline components.
     */
    fun stop() {
        if (_pipelineState.value == PipelineState.IDLE) return
        _pipelineState.value = PipelineState.STOPPING
        CallDiagnostics.log("PIPELINE", "Stopping Guardian Pipeline...")

        // 1. Unregister Agora Audio Frame Observer
        boundRtcEngine?.let { rtc ->
            try {
                rtc.registerAudioFrameObserver(null)
            } catch (e: Exception) {
                CallDiagnostics.log("PIPELINE", "Error unregistering audio frame observer: ${e.message}", LogLevel.WARN)
            }
        }
        audioFrameBridge = null
        boundRtcEngine = null

        // 2. Stop audio pipelines
        localPipeline.stop()
        remotePipeline.stop()

        // 3. Stop STT sessions
        dualStt.close()

        // 4. Stop Risk Engine
        riskEngine.stop()

        // 5. Clear transcript merger history
        merger.clear()

        _pipelineState.value = PipelineState.IDLE
        CallDiagnostics.log("PIPELINE", "Guardian Pipeline stopped cleanly (IDLE)")
    }
}
