package com.susagi.stt

import android.content.Context
import com.guardian.app.voip.CallDiagnostics
import com.guardian.app.voip.LogLevel
import com.susagi.audio.AudioSource
import com.susagi.stt.whisper.WhisperSttEngine
import com.susagi.stt.whisper.WhisperSttTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class SttEngineMode {
    WHISPER_OFFLINE,
    BHASHINI_ONLINE,
    AUTO_HYBRID
}

/**
 * DualSttController
 *
 * Coordinates dual-stream real-time speech recognition for Agora VoIP calls.
 * Supports:
 * 1. WHISPER_OFFLINE: 100% on-device ExecuTorch Whisper on Arm (zero network dependency).
 * 2. BHASHINI_ONLINE: Cloud-based Bhashini Dhruva ASR.
 * 3. AUTO_HYBRID: Automatically uses Whisper on-device if model is provisioned; falls back to Bhashini if online.
 *
 * Captures LOCAL microphone and REMOTE caller audio independently and feeds them into [TranscriptMerger].
 */
class DualSttController(
    private var language: String = "hi",
    private val endpointUrl: String,
    private val authToken: String? = null,
    val merger: TranscriptMerger = TranscriptMerger(),
    localTransport: SttTransport? = null,
    remoteTransport: SttTransport? = null,
    private val context: Context? = null,
    initialEngineMode: SttEngineMode = SttEngineMode.BHASHINI_ONLINE
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _engineMode = MutableStateFlow(initialEngineMode)
    val engineMode: StateFlow<SttEngineMode> = _engineMode.asStateFlow()

    private val _activeEngineLabel = MutableStateFlow("Initializing")
    val activeEngineLabel: StateFlow<String> = _activeEngineLabel.asStateFlow()

    // Bhashini online clients
    private val localBhashiniClient = BhashiniSttClient(
        language = language,
        endpointUrl = endpointUrl,
        authToken = authToken,
        label = "local",
        transport = localTransport
    ) { text, isFinal ->
        if (_engineMode.value == SttEngineMode.BHASHINI_ONLINE ||
            (_engineMode.value == SttEngineMode.AUTO_HYBRID && !isWhisperReady())
        ) {
            merger.onTranscript(
                TranscriptLine(
                    source = AudioSource.LOCAL,
                    text = text,
                    isFinal = isFinal
                )
            )
        }
    }

    private val remoteBhashiniClient = BhashiniSttClient(
        language = language,
        endpointUrl = endpointUrl,
        authToken = authToken,
        label = "remote",
        transport = remoteTransport
    ) { text, isFinal ->
        if (_engineMode.value == SttEngineMode.BHASHINI_ONLINE ||
            (_engineMode.value == SttEngineMode.AUTO_HYBRID && !isWhisperReady())
        ) {
            merger.onTranscript(
                TranscriptLine(
                    source = AudioSource.REMOTE,
                    text = text,
                    isFinal = isFinal
                )
            )
        }
    }

    // Whisper offline transports
    private val whisperEngine = context?.let { WhisperSttEngine.getInstance(it) }
    private val localWhisperTransport = context?.let { WhisperSttTransport(it, label = "local", engine = whisperEngine!!) }
    private val remoteWhisperTransport = context?.let { WhisperSttTransport(it, label = "remote", engine = whisperEngine!!) }

    private val _isLocalConnected = MutableStateFlow(false)
    val isLocalConnected: StateFlow<Boolean> = _isLocalConnected.asStateFlow()

    private val _isRemoteConnected = MutableStateFlow(false)
    val isRemoteConnected: StateFlow<Boolean> = _isRemoteConnected.asStateFlow()

    val transcriptUpdates: SharedFlow<TranscriptLine> = merger.transcriptUpdates
    val transcriptHistory: StateFlow<List<TranscriptLine>> = merger.transcriptHistory

    init {
        updateActiveEngineLabel()
    }

    private fun isWhisperReady(): Boolean = whisperEngine?.isReady == true

    fun setEngineMode(mode: SttEngineMode) {
        _engineMode.value = mode
        CallDiagnostics.log("STT-DUAL", "STT engine mode switched to: $mode")
        updateActiveEngineLabel()

        if (mode == SttEngineMode.WHISPER_OFFLINE && whisperEngine != null && !whisperEngine.isReady) {
            scope.launch {
                whisperEngine.load()
                updateActiveEngineLabel()
            }
        }
    }

    private fun updateActiveEngineLabel() {
        _activeEngineLabel.value = when (_engineMode.value) {
            SttEngineMode.WHISPER_OFFLINE -> "Whisper (Offline - ExecuTorch)"
            SttEngineMode.BHASHINI_ONLINE -> "Bhashini (Online - Cloud)"
            SttEngineMode.AUTO_HYBRID -> {
                if (isWhisperReady()) "Auto: Whisper (On-Device)" else "Auto: Bhashini (Cloud)"
            }
        }
    }

    fun start() {
        CallDiagnostics.log("STT-DUAL", "Starting DualSttController (mode=${_engineMode.value}, lang=$language)")

        // 1. Start Bhashini clients
        try {
            localBhashiniClient.start()
            remoteBhashiniClient.start()
        } catch (t: Throwable) {
            CallDiagnostics.log("STT-DUAL", "Bhashini start warning: ${t.message}", LogLevel.WARN)
        }

        // 2. Start Whisper transports if context available
        if (localWhisperTransport != null && remoteWhisperTransport != null) {
            localWhisperTransport.connect("", null, language, object : SttTransportListener {
                override fun onOpen() {
                    CallDiagnostics.log("STT-DUAL", "[local] Whisper transport open")
                    checkConnectionState()
                }

                override fun onTranscript(text: String, isFinal: Boolean) {
                    if (_engineMode.value == SttEngineMode.WHISPER_OFFLINE ||
                        (_engineMode.value == SttEngineMode.AUTO_HYBRID && isWhisperReady())
                    ) {
                        merger.onTranscript(
                            TranscriptLine(
                                source = AudioSource.LOCAL,
                                text = text,
                                isFinal = isFinal
                            )
                        )
                    }
                }

                override fun onError(throwable: Throwable) {
                    CallDiagnostics.log("STT-DUAL", "[local] Whisper transport error: ${throwable.message}", LogLevel.WARN)
                }

                override fun onClose(code: Int, reason: String) {
                    CallDiagnostics.log("STT-DUAL", "[local] Whisper transport closed: $reason")
                }
            })

            remoteWhisperTransport.connect("", null, language, object : SttTransportListener {
                override fun onOpen() {
                    CallDiagnostics.log("STT-DUAL", "[remote] Whisper transport open")
                    checkConnectionState()
                }

                override fun onTranscript(text: String, isFinal: Boolean) {
                    if (_engineMode.value == SttEngineMode.WHISPER_OFFLINE ||
                        (_engineMode.value == SttEngineMode.AUTO_HYBRID && isWhisperReady())
                    ) {
                        merger.onTranscript(
                            TranscriptLine(
                                source = AudioSource.REMOTE,
                                text = text,
                                isFinal = isFinal
                            )
                        )
                    }
                }

                override fun onError(throwable: Throwable) {
                    CallDiagnostics.log("STT-DUAL", "[remote] Whisper transport error: ${throwable.message}", LogLevel.WARN)
                }

                override fun onClose(code: Int, reason: String) {
                    CallDiagnostics.log("STT-DUAL", "[remote] Whisper transport closed: $reason")
                }
            })
        }

        checkConnectionState()
    }

    private fun checkConnectionState() {
        val useWhisper = _engineMode.value == SttEngineMode.WHISPER_OFFLINE ||
                (_engineMode.value == SttEngineMode.AUTO_HYBRID && isWhisperReady())

        _isLocalConnected.value = if (useWhisper) {
            localWhisperTransport?.isConnected == true
        } else {
            localBhashiniClient.isConnected.value
        }

        _isRemoteConnected.value = if (useWhisper) {
            remoteWhisperTransport?.isConnected == true
        } else {
            remoteBhashiniClient.isConnected.value
        }
    }

    fun pushLocalPcm(samples: ShortArray, sampleRate: Int = 16000) {
        if (samples.isEmpty()) return
        val byteBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) byteBuffer.putShort(s)
        pushLocalPcmBytes(byteBuffer.array(), sampleRate)
    }

    fun pushLocalPcmBytes(bytes: ByteArray, sampleRate: Int = 16000) {
        if (bytes.isEmpty()) return
        val useWhisper = _engineMode.value == SttEngineMode.WHISPER_OFFLINE ||
                (_engineMode.value == SttEngineMode.AUTO_HYBRID && isWhisperReady())

        if (useWhisper && localWhisperTransport != null) {
            localWhisperTransport.sendAudio(bytes)
        } else {
            localBhashiniClient.pushPcmBytes(bytes, sampleRate)
        }
    }

    fun pushRemotePcm(samples: ShortArray, sampleRate: Int = 16000) {
        if (samples.isEmpty()) return
        val byteBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) byteBuffer.putShort(s)
        pushRemotePcmBytes(byteBuffer.array(), sampleRate)
    }

    fun pushRemotePcmBytes(bytes: ByteArray, sampleRate: Int = 16000) {
        if (bytes.isEmpty()) return
        val useWhisper = _engineMode.value == SttEngineMode.WHISPER_OFFLINE ||
                (_engineMode.value == SttEngineMode.AUTO_HYBRID && isWhisperReady())

        if (useWhisper && remoteWhisperTransport != null) {
            remoteWhisperTransport.sendAudio(bytes)
        } else {
            remoteBhashiniClient.pushPcmBytes(bytes, sampleRate)
        }
    }

    fun setLanguage(newLanguage: String) {
        if (language == newLanguage) return
        language = newLanguage
        CallDiagnostics.log("STT-DUAL", "Updating language to $newLanguage across all STT sessions")
        localBhashiniClient.setLanguage(newLanguage)
        remoteBhashiniClient.setLanguage(newLanguage)
        localWhisperTransport?.sendStart(newLanguage)
        remoteWhisperTransport?.sendStart(newLanguage)
    }

    fun close() {
        CallDiagnostics.log("STT-DUAL", "Closing DualSttController")
        localBhashiniClient.close()
        remoteBhashiniClient.close()
        localWhisperTransport?.close()
        remoteWhisperTransport?.close()
    }
}
