package com.susagi.stt.whisper

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.pytorch.executorch.extension.asr.AsrCallback
import org.pytorch.executorch.extension.asr.AsrModule
import org.pytorch.executorch.extension.asr.AsrTranscribeConfig
import java.io.File

/**
 * WhisperSttEngine
 *
 * Direct native ExecuTorch integration for on-device Whisper speech-to-text inference on Arm.
 * Uses [AsrModule] from org.pytorch:executorch-android:1.5.1 to transcribe 16kHz mono WAV audio
 * with zero cloud reliance, zero data leakage, and full airplane-mode compatibility.
 */
class WhisperSttEngine(
    private val context: Context,
    val modelManager: WhisperModelManager = WhisperModelManager(context)
) {

    companion object {
        private const val TAG = "WhisperSttEngine"
        const val ENGINE_NAME = "ExecuTorch-Whisper-Arm"

        @Volatile
        private var instance: WhisperSttEngine? = null

        fun getInstance(context: Context): WhisperSttEngine {
            return instance ?: synchronized(this) {
                instance ?: WhisperSttEngine(context.applicationContext).also { instance = it }
            }
        }

        private fun logD(msg: String) { try { Log.d(TAG, msg) } catch (_: Throwable) { println("[$TAG] $msg") } }
        private fun logI(msg: String) { try { Log.i(TAG, msg) } catch (_: Throwable) { println("[$TAG] $msg") } }
        private fun logW(msg: String) { try { Log.w(TAG, msg) } catch (_: Throwable) { println("[$TAG] $msg") } }
        private fun logE(msg: String, t: Throwable? = null) { try { Log.e(TAG, msg, t) } catch (_: Throwable) { println("[$TAG] $msg ${t?.message ?: ""}") } }
    }

    private val inferenceMutex = Mutex()
    private val audioProcessor = WhisperAudioProcessor(modelManager.tempAudioDir)

    @Volatile
    private var asrModule: AsrModule? = null

    @Volatile
    private var isModuleLoaded = false

    private var initLatencyMs: Long = 0L
    private var lastInferenceLatencyMs: Long = 0L
    private var lastAudioDurationMs: Long = 0L
    private var lastRtf: Float = 0f
    private var lastTokensGenerated: Int = 0
    private var lastTokensPerSec: Float = 0f
    private var totalTranscriptions: Long = 0L
    private var lastTranscript: String = ""
    private var lastError: String? = null

    val isReady: Boolean
        get() = isModuleLoaded && modelManager.isReady()

    /**
     * Initializes and loads the ExecuTorch AsrModule on Arm hardware.
     */
    @Synchronized
    fun load(): Boolean {
        if (isModuleLoaded && asrModule != null) return true

        val startTime = System.currentTimeMillis()
        try {
            if (!modelManager.isReady()) {
                lastError = "Whisper model assets missing (model, preprocessor, or tokenizer)"
                logD(lastError!!)
                return false
            }

            val modelPath = modelManager.modelFile.absolutePath
            val preprocessorPath = modelManager.preprocessorFile.absolutePath
            val tokenizerPath = modelManager.tokenizerFile.absolutePath

            logI("Loading ExecuTorch AsrModule:\n  model=$modelPath\n  preprocessor=$preprocessorPath\n  tokenizer=$tokenizerPath")

            val module = AsrModule(
                modelPath = modelPath,
                tokenizerPath = tokenizerPath,
                dataPath = null,
                preprocessorPath = preprocessorPath
            )

            val loadStatus = module.load()
            if (loadStatus != 0) {
                lastError = "AsrModule native load() returned non-zero status code: $loadStatus"
                logE(lastError!!)
                module.close()
                return false
            }

            asrModule = module
            isModuleLoaded = true
            initLatencyMs = System.currentTimeMillis() - startTime
            lastError = null
            logI("Whisper AsrModule successfully loaded in ${initLatencyMs}ms")
            return true
        } catch (t: Throwable) {
            lastError = "Failed to load AsrModule: ${t.message}"
            logE(lastError!!, t)
            isModuleLoaded = false
            asrModule = null
            return false
        }
    }

    /**
     * Transcribes an audio file (standard 16kHz 16-bit mono WAV).
     */
    suspend fun transcribeWav(
        wavFile: File,
        language: String = "hi",
        maxTokens: Long = 128L
    ): WhisperInferenceResult = withContext(Dispatchers.IO) {
        inferenceMutex.withLock {
            if (!wavFile.exists() || wavFile.length() < 44L) {
                return@withContext WhisperInferenceResult(
                    text = "",
                    latencyMs = 0L,
                    audioDurationMs = 0L,
                    realTimeFactor = 0f,
                    tokensGenerated = 0,
                    tokensPerSecond = 0f,
                    isSuccess = false,
                    errorMessage = "WAV file does not exist or is empty"
                )
            }

            if (!isModuleLoaded || asrModule == null) {
                val loaded = load()
                if (!loaded) {
                    return@withContext WhisperInferenceResult(
                        text = "",
                        latencyMs = 0L,
                        audioDurationMs = 0L,
                        realTimeFactor = 0f,
                        tokensGenerated = 0,
                        tokensPerSecond = 0f,
                        isSuccess = false,
                        errorMessage = lastError ?: "Whisper model not initialized"
                    )
                }
            }

            val module = asrModule ?: return@withContext WhisperInferenceResult(
                text = "",
                latencyMs = 0L,
                audioDurationMs = 0L,
                realTimeFactor = 0f,
                tokensGenerated = 0,
                tokensPerSecond = 0f,
                isSuccess = false,
                errorMessage = "AsrModule reference lost"
            )

            val tokenCount = java.util.concurrent.atomic.AtomicInteger(0)
            val tokenBuffer = StringBuilder()
            val startNs = SystemClock.elapsedRealtimeNanos()

            // Calculate audio duration from WAV size: (fileLength - 44) / 32,000 bytes/sec * 1000 ms
            val audioBytes = (wavFile.length() - 44).coerceAtLeast(0L)
            val audioDurationMs = (audioBytes * 1000L) / 32000L

            try {
                var callbackError: String? = null
                val callback = object : AsrCallback {
                    override fun onToken(token: String) {
                        tokenCount.incrementAndGet()
                        tokenBuffer.append(token)
                    }

                    override fun onError(code: Int, error: String) {
                        logW("AsrCallback onError (code=$code): $error")
                        callbackError = "AsrCallback error ($code): $error"
                        lastError = callbackError
                    }
                }

                val config = AsrTranscribeConfig(
                    maxNewTokens = maxTokens,
                    temperature = 0.0f,
                    decoderStartTokenId = 50258L
                )

                logD("Starting native transcribe on ${wavFile.name} (${audioDurationMs}ms audio)...")
                val nativeResult = module.transcribe(wavFile.absolutePath, config, callback)

                val elapsedNs = SystemClock.elapsedRealtimeNanos() - startNs
                val latencyMs = elapsedNs / 1_000_000L
                val rtf = if (audioDurationMs > 0) latencyMs.toFloat() / audioDurationMs.toFloat() else 0f
                val tokens = tokenCount.get()
                val rawText = if (nativeResult.isNotBlank()) nativeResult else tokenBuffer.toString()
                val cleanedText = WhisperDecoder.decode(rawText)

                val effectiveTokens = if (tokens > 0) tokens else if (cleanedText.isNotBlank()) cleanedText.split(Regex("\\s+")).size else 0
                val tokensPerSec = if (latencyMs > 0) (effectiveTokens * 1000f) / latencyMs.toFloat() else 0f

                lastInferenceLatencyMs = latencyMs
                lastAudioDurationMs = audioDurationMs
                lastRtf = rtf
                lastTokensGenerated = effectiveTokens
                lastTokensPerSec = tokensPerSec

                val isSuccess = callbackError == null && cleanedText.isNotBlank() && effectiveTokens > 0
                if (isSuccess) {
                    totalTranscriptions++
                    lastTranscript = cleanedText
                    lastError = null
                    logI("Whisper transcription completed in ${latencyMs}ms (RTF: ${"%.2f".format(rtf)}x, ${effectiveTokens} tokens): $cleanedText")
                } else {
                    lastError = callbackError ?: if (cleanedText.isBlank()) "Empty transcription generated" else "Inference failed"
                    logW("Whisper transcription failed or yielded empty result (isSuccess=false): $lastError")
                }

                WhisperInferenceResult(
                    text = cleanedText,
                    latencyMs = latencyMs,
                    audioDurationMs = audioDurationMs,
                    realTimeFactor = rtf,
                    tokensGenerated = tokens,
                    tokensPerSecond = tokensPerSec,
                    isSuccess = isSuccess,
                    errorMessage = if (!isSuccess) lastError else null
                )
            } catch (t: Throwable) {
                val elapsedNs = SystemClock.elapsedRealtimeNanos() - startNs
                val latencyMs = elapsedNs / 1_000_000L
                lastError = "Inference exception: ${t.message}"
                logE(lastError!!, t)

                WhisperInferenceResult(
                    text = "",
                    latencyMs = latencyMs,
                    audioDurationMs = audioDurationMs,
                    realTimeFactor = 0f,
                    tokensGenerated = 0,
                    tokensPerSecond = 0f,
                    isSuccess = false,
                    errorMessage = lastError
                )
            }
        }
    }

    /**
     * Transcribes raw 16kHz 16-bit mono PCM bytes by wrapping them in a temporary WAV file.
     */
    suspend fun transcribePcm(
        pcmBytes: ByteArray,
        sampleRate: Int = 16000,
        language: String = "hi"
    ): WhisperInferenceResult {
        if (pcmBytes.isEmpty()) {
            return WhisperInferenceResult(
                text = "",
                latencyMs = 0L,
                audioDurationMs = 0L,
                realTimeFactor = 0f,
                tokensGenerated = 0,
                tokensPerSecond = 0f,
                isSuccess = false,
                errorMessage = "PCM audio data is empty"
            )
        }

        val wavFile = audioProcessor.writeWavFile(pcmBytes, sampleRate = sampleRate)
        return try {
            transcribeWav(wavFile, language = language)
        } finally {
            try { wavFile.delete() } catch (_: Exception) {}
        }
    }

    /**
     * Closes the native ExecuTorch module and frees Arm memory.
     */
    @Synchronized
    fun close() {
        try {
            asrModule?.close()
        } catch (e: Exception) {
            logW("Error closing AsrModule: ${e.message}")
        } finally {
            asrModule = null
            isModuleLoaded = false
            audioProcessor.cleanupTempFiles()
            logI("WhisperSttEngine closed and resources released.")
        }
    }

    fun getDiagnostics(): WhisperDiagnostics {
        return WhisperDiagnostics(
            isModelLoaded = isModuleLoaded,
            modelSizeBytes = if (modelManager.modelFile.exists()) modelManager.modelFile.length() else 0L,
            modelFileName = modelManager.modelFile.name,
            hasPreprocessor = modelManager.preprocessorFile.exists(),
            hasTokenizer = modelManager.tokenizerFile.exists(),
            lastInferenceLatencyMs = lastInferenceLatencyMs,
            audioDurationMs = lastAudioDurationMs,
            realTimeFactor = lastRtf,
            tokensGenerated = lastTokensGenerated,
            tokensPerSecond = lastTokensPerSec,
            totalTranscriptions = totalTranscriptions,
            activeEngine = ENGINE_NAME,
            lastTranscript = lastTranscript,
            lastError = lastError
        )
    }
}
