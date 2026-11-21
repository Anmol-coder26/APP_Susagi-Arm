package com.susagi.stt.whisper

import android.content.Context
import android.util.Log
import com.guardian.app.voip.CallDiagnostics
import com.guardian.app.voip.LogLevel
import com.susagi.stt.SttTransport
import com.susagi.stt.SttTransportListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WhisperSttTransport
 *
 * Implements [SttTransport] for 100% on-device speech recognition via ExecuTorch Whisper.
 * Audio chunks from Agora VoIP (local mic or remote caller) are queued asynchronously
 * without blocking real-time audio threads, segmented by VAD, and transcribed by [WhisperSttEngine].
 */
class WhisperSttTransport(
    private val context: Context,
    val label: String = "local",
    private val engine: WhisperSttEngine = WhisperSttEngine.getInstance(context)
) : SttTransport {

    companion object {
        private const val TAG = "WhisperSttTransport"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioChannel = Channel<ByteArray>(capacity = Channel.BUFFERED)
    private val isRunning = AtomicBoolean(false)

    private var listener: SttTransportListener? = null
    private var activeLanguage: String = "hi"
    private var workerJob: Job? = null

    private val audioProcessor = WhisperAudioProcessor(
        tempDir = File(context.cacheDir, "whisper_temp_$label"),
        vadThresholdRms = 120.0,
        minTurnBytes = 25_600, // 800ms min
        maxSegmentBytes = 160_000, // 5.0s max
        silenceHangoverFrames = 5 // ~500ms
    )

    override val isConnected: Boolean
        get() = isRunning.get() && engine.isReady

    override fun connect(url: String, authToken: String?, language: String, listener: SttTransportListener) {
        this.listener = listener
        this.activeLanguage = language
        isRunning.set(true)

        CallDiagnostics.log("STT-WHISPER", "[$label] Connecting Whisper STT transport (lang=$language)")

        scope.launch {
            try {
                if (!engine.isReady) {
                    CallDiagnostics.log("STT-WHISPER", "[$label] Preloading ExecuTorch Whisper engine...")
                    val loaded = engine.load()
                    if (!loaded) {
                        CallDiagnostics.log("STT-WHISPER", "[$label] Whisper model not loaded on device yet.", LogLevel.WARN)
                    }
                }
                listener.onOpen()
            } catch (t: Throwable) {
                CallDiagnostics.log("STT-WHISPER", "[$label] Connection error: ${t.message}", LogLevel.ERROR)
                listener.onError(t)
            }
        }

        startProcessingWorker()
    }

    override fun sendStart(language: String) {
        this.activeLanguage = language
        CallDiagnostics.log("STT-WHISPER", "[$label] Updated language to $language")
    }

    override fun sendAudio(data: ByteArray): Boolean {
        if (!isRunning.get() || data.isEmpty()) return false
        // Non-blocking trySend to preserve real-time audio thread latency
        val result = audioChannel.trySend(data)
        return result.isSuccess
    }

    private fun startProcessingWorker() {
        workerJob?.cancel()
        workerJob = scope.launch {
            CallDiagnostics.log("STT-WHISPER", "[$label] Audio processing worker started")
            for (chunk in audioChannel) {
                if (!isRunning.get()) break
                try {
                    val wavFile = audioProcessor.processPcmChunk(chunk)
                    if (wavFile != null) {
                        transcribeWavSegment(wavFile)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "[$label] Audio processing error: ${t.message}", t)
                }
            }
        }
    }

    private suspend fun transcribeWavSegment(wavFile: File) {
        try {
            CallDiagnostics.log("STT-WHISPER", "[$label] Transcribing turn segment (${wavFile.length()} bytes)...")
            val result = engine.transcribeWav(wavFile, language = activeLanguage)
            if (result.isSuccess && result.text.isNotBlank()) {
                CallDiagnostics.log("STT-WHISPER", "[$label] Whisper Recognized (${result.latencyMs}ms, RTF ${"%.2f".format(result.realTimeFactor)}x): \"${result.text}\"")
                listener?.onTranscript(result.text, isFinal = true)
            } else if (!result.isSuccess) {
                CallDiagnostics.log("STT-WHISPER", "[$label] Whisper transcription error: ${result.errorMessage}", LogLevel.WARN)
            }
        } catch (t: Throwable) {
            CallDiagnostics.log("STT-WHISPER", "[$label] Whisper execution error: ${t.message}", LogLevel.ERROR)
        } finally {
            try { wavFile.delete() } catch (_: Exception) {}
        }
    }

    override fun close() {
        if (!isRunning.getAndSet(false)) return
        CallDiagnostics.log("STT-WHISPER", "[$label] Closing Whisper transport")

        // Flush any remaining audio turn
        scope.launch {
            try {
                val remainingWav = audioProcessor.flush()
                if (remainingWav != null) {
                    transcribeWavSegment(remainingWav)
                }
            } catch (_: Exception) {}
            finally {
                audioProcessor.cleanupTempFiles()
                listener?.onClose(1000, "Whisper transport closed")
                scope.cancel()
            }
        }
    }
}
