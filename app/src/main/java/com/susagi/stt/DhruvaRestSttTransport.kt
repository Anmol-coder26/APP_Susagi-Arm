package com.susagi.stt

import android.util.Base64
import com.guardian.app.BuildConfig
import com.guardian.app.voip.CallDiagnostics
import com.guardian.app.voip.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * DhruvaRestSttTransport
 *
 * Implements high-performance conversational speech recognition over the verified
 * Bhashini Dhruva REST Inference Pipeline API (https://dhruva-api.bhashini.gov.in/services/inference/pipeline).
 *
 * Employs Voice Activity Detection (VAD) and silence hangover tracking to accumulate
 * raw 100ms PCM chunks into natural, complete speech phrases (0.8s - 5.0s) rather than
 * dispatching arbitrary fixed-time fragments or silent frames.
 */
class DhruvaRestSttTransport(
    private val tag: String = "STT-DhruvaRest"
) : SttTransport {

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val DEFAULT_PIPELINE_URL = "https://dhruva-api.bhashini.gov.in/services/inference/pipeline"

        // Energy threshold in 16-bit PCM scale (120.0 captures conversational speech while rejecting ambient noise)
        private const val SPEECH_RMS_THRESHOLD = 120.0

        // Silence duration required to seal a speech utterance (500ms natural sentence pause)
        private const val SILENCE_HANGOVER_MS = 500L

        // Minimum audio length required to submit an utterance (300ms @ 16kHz mono 16-bit = 9,600 bytes)
        private const val MIN_SPEECH_BYTES = 9600

        // Maximum audio length before forcing a boundary split on run-on speech (5.0s = 160,000 bytes)
        private const val MAX_SPEECH_BYTES = 160000
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var workerJob: Job? = null

    private val _isConnected = AtomicBoolean(false)
    private val _isRunning = AtomicBoolean(false)
    override val isConnected: Boolean get() = _isConnected.get()

    private var activeLanguage = "hi"
    private var endpointUrl = DEFAULT_PIPELINE_URL
    private var inferenceApiKey = ""
    private var pipelineId = BuildConfig.BHASHINI_PIPELINE_ID.ifBlank { "64392f96daac500b55c543cd" }
    private var listener: SttTransportListener? = null

    // VAD & Speech Accumulation State
    private val lock = Any()
    private val speechAccumulator = ByteArrayOutputStream()
    private val preSpeechRingBuffer = mutableListOf<ByteArray>() // holds ~200ms pre-speech audio
    private var isSpeechActive = false
    private var lastSpeechTimeMs = 0L

    @Volatile var vadSpeechSegmentsDetected: Long = 0; private set
    @Volatile var asrRequestsSent: Long = 0; private set
    @Volatile var asrResponsesReceived: Long = 0; private set
    @Volatile var transcriptsEmitted: Long = 0; private set

    // Outbound dispatch queue
    private val dispatchQueue = LinkedBlockingQueue<ByteArray>(20)

    override fun connect(url: String, authToken: String?, language: String, listener: SttTransportListener) {
        this.endpointUrl = if (url.isNotBlank() && url.startsWith("http")) url else DEFAULT_PIPELINE_URL
        this.inferenceApiKey = authToken ?: BuildConfig.BHASHINI_INFERENCE_API_KEY
        this.activeLanguage = language
        this.listener = listener
        this._isRunning.set(true)
        this._isConnected.set(true)

        CallDiagnostics.log("STT-TRANS", "[$tag] Connected to Dhruva REST transport (endpoint=$endpointUrl, lang=$language)")
        listener.onOpen()

        startWorker()
    }

    private fun startWorker() {
        workerJob?.cancel()
        workerJob = scope.launch {
            while (isActive && _isRunning.get()) {
                val utteranceBytes = dispatchQueue.poll(200, TimeUnit.MILLISECONDS) ?: continue
                dispatchAudioUtterance(utteranceBytes)
            }
        }
    }

    /**
     * Ingests 100ms raw PCM chunks from AudioFramePipeline.
     * Evaluates energy-based VAD and assembles natural conversational phrases.
     */
    override fun sendAudio(data: ByteArray): Boolean {
        if (!_isRunning.get() || data.isEmpty()) return false

        val rms = calculateRms(data)
        val isSpeechChunk = rms >= SPEECH_RMS_THRESHOLD
        val now = System.currentTimeMillis()

        synchronized(lock) {
            if (isSpeechChunk) {
                if (!isSpeechActive) {
                    isSpeechActive = true
                    vadSpeechSegmentsDetected++
                    CallDiagnostics.log("STT-VAD", "[$tag] Speech onset detected (RMS=${rms.toInt()}) [Total Segments: $vadSpeechSegmentsDetected]")
                    // Flush pre-speech buffer so leading consonants are preserved
                    for (pre in preSpeechRingBuffer) {
                        speechAccumulator.write(pre)
                    }
                    preSpeechRingBuffer.clear()
                }
                speechAccumulator.write(data)
                lastSpeechTimeMs = now
            } else {
                if (isSpeechActive) {
                    // Include trailing pause audio up to silence hangover
                    speechAccumulator.write(data)
                    val silenceDuration = now - lastSpeechTimeMs

                    if (silenceDuration >= SILENCE_HANGOVER_MS) {
                        val totalBytes = speechAccumulator.size()
                        if (totalBytes >= MIN_SPEECH_BYTES) {
                            CallDiagnostics.log("STT-VAD", "[$tag] Speech turn completed (${totalBytes / 32000.0}s, silence=${silenceDuration}ms)")
                            dispatchQueue.offer(speechAccumulator.toByteArray())
                        } else {
                            CallDiagnostics.log("STT-VAD", "[$tag] Discarded sub-threshold audio click (${totalBytes}b)")
                        }
                        speechAccumulator.reset()
                        isSpeechActive = false
                    }
                } else {
                    // Keep sliding 200ms pre-speech buffer
                    preSpeechRingBuffer.add(data.copyOf())
                    if (preSpeechRingBuffer.size > 2) {
                        preSpeechRingBuffer.removeAt(0)
                    }
                }
            }

            // Forced max duration boundary cut to prevent unbounded speech delay
            if (isSpeechActive && speechAccumulator.size() >= MAX_SPEECH_BYTES) {
                CallDiagnostics.log("STT-VAD", "[$tag] Max speech duration reached (5.0s), dispatching segment")
                dispatchQueue.offer(speechAccumulator.toByteArray())
                speechAccumulator.reset()
            }
        }
        return true
    }

    private fun dispatchAudioUtterance(pcmBytes: ByteArray) {
        val startTs = System.currentTimeMillis()
        try {
            val wavBytes = wrapPcmInWav(pcmBytes, sampleRate = 16000, channels = 1)
            val audioBase64 = Base64.encodeToString(wavBytes, Base64.NO_WRAP)

            val payload = JSONObject().apply {
                put("pipelineTasks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("taskType", "asr")
                        put("config", JSONObject().apply {
                            put("language", JSONObject().apply {
                                put("sourceLanguage", activeLanguage)
                            })
                            put("audioFormat", "wav")
                            put("samplingRate", 16000)
                        })
                    })
                })
                put("inputData", JSONObject().apply {
                    put("audio", JSONArray().apply {
                        put(JSONObject().apply {
                            put("audioContent", audioBase64)
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(endpointUrl)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", inferenceApiKey)
                .addHeader("x-pipeline-id", pipelineId)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            asrRequestsSent++
            httpClient.newCall(request).execute().use { response ->
                val latencyMs = System.currentTimeMillis() - startTs
                val bodyStr = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val code = response.code
                    CallDiagnostics.log("STT-TRANS", "[$tag] Dhruva HTTP $code error in ${latencyMs}ms: ${bodyStr.take(120)}", LogLevel.WARN)
                    return
                }

                asrResponsesReceived++
                val json = JSONObject(bodyStr)
                val pipeResp = json.optJSONArray("pipelineResponse")
                if (pipeResp != null && pipeResp.length() > 0) {
                    val outputArr = pipeResp.getJSONObject(0).optJSONArray("output")
                    if (outputArr != null && outputArr.length() > 0) {
                        val text = outputArr.getJSONObject(0).optString("source", "").trim()
                        if (text.isNotBlank() && text != "null") {
                            transcriptsEmitted++
                            CallDiagnostics.log("STT-TRANS", "[$tag] Transcript emitted in ${latencyMs}ms (#$transcriptsEmitted): '$text'")
                            listener?.onTranscript(text, isFinal = true)
                        } else {
                            CallDiagnostics.log("STT-TRANS", "[$tag] Empty ASR transcript in ${latencyMs}ms")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            val latencyMs = System.currentTimeMillis() - startTs
            CallDiagnostics.log("STT-TRANS", "[$tag] ASR dispatch failed in ${latencyMs}ms: ${e.message}", LogLevel.WARN)
        }
    }

    override fun sendStart(language: String) {
        this.activeLanguage = language
        synchronized(lock) {
            speechAccumulator.reset()
            preSpeechRingBuffer.clear()
            isSpeechActive = false
        }
        CallDiagnostics.log("STT-TRANS", "[$tag] Configured language to $language")
    }

    override fun close() {
        _isRunning.set(false)
        _isConnected.set(false)
        workerJob?.cancel()
        synchronized(lock) {
            // Flush remaining speech if present
            if (isSpeechActive && speechAccumulator.size() >= MIN_SPEECH_BYTES) {
                dispatchQueue.offer(speechAccumulator.toByteArray())
            }
            speechAccumulator.reset()
            preSpeechRingBuffer.clear()
            isSpeechActive = false
        }
        listener?.onClose(1000, "Closed cleanly")
        CallDiagnostics.log("STT-TRANS", "[$tag] Dhruva REST transport closed")
    }

    private fun calculateRms(pcmBytes: ByteArray): Double {
        val shortBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var sumSquares = 0.0
        val count = shortBuffer.remaining()
        if (count == 0) return 0.0
        while (shortBuffer.hasRemaining()) {
            val sample = shortBuffer.get().toDouble()
            sumSquares += sample * sample
        }
        return sqrt(sumSquares / count)
    }

    /**
     * Constructs a valid 44-byte RIFF WAVE container around raw 16kHz mono PCM16 bytes.
     */
    private fun wrapPcmInWav(pcmData: ByteArray, sampleRate: Int = 16000, channels: Int = 1): ByteArray {
        val totalDataLen = pcmData.size
        val totalAudioLen = totalDataLen + 36
        val byteRate = sampleRate * channels * 2
        val header = ByteArray(44)

        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buf.order(ByteOrder.BIG_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(totalAudioLen)
        buf.order(ByteOrder.BIG_ENDIAN)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(16) // Subchunk1Size for PCM
        buf.putShort(1) // AudioFormat: 1 = PCM
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort((channels * 2).toShort()) // BlockAlign
        buf.putShort(16) // BitsPerSample
        buf.order(ByteOrder.BIG_ENDIAN)
        buf.put("data".toByteArray())
        buf.order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(totalDataLen)

        val out = ByteArray(44 + totalDataLen)
        System.arraycopy(header, 0, out, 0, 44)
        System.arraycopy(pcmData, 0, out, 44, totalDataLen)
        return out
    }
}
