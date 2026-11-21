package com.guardian.app.bhashini

import android.util.Log
import com.guardian.app.BuildConfig
import com.guardian.app.voip.CallDiagnostics
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RawWebSocketSttTransport(
    private val tag: String = "STT-RawWS",
    private val onTranscript: (text: String, isFinal: Boolean) -> Unit,
    private val onError: (error: String) -> Unit = {}
) : SttTransport {

    companion object {
        private const val TARGET_SAMPLE_RATE = 16000
        private const val CHUNK_SIZE_BYTES = 3200 // 100ms at 16kHz 16-bit mono
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val _isRunning = AtomicBoolean(false)
    private val _isConnected = AtomicBoolean(false)
    private var activeLanguage = "hi"
    private val pushBuffer = ByteArrayOutputStream()
    private val ringBuffer = AudioRingBufferQueue()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    private var backoffDelayMs = 1000L

    override val isConnected: Boolean get() = _isConnected.get()
    override val isRunning: Boolean get() = _isRunning.get()

    private val wsListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _isConnected.set(true)
            backoffDelayMs = 1000L
            CallDiagnostics.log("STT", "[$tag] Raw WebSocket connected for lang: $activeLanguage")
            sendStartEvent(webSocket, activeLanguage)
            flushRingBuffer()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            parseServerMessage(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _isConnected.set(false)
            val errMsg = t.message ?: "WebSocket failure"
            CallDiagnostics.log("STT", "[$tag] WebSocket failure: $errMsg")
            onError(errMsg)
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _isConnected.set(false)
            CallDiagnostics.log("STT", "[$tag] WebSocket closed: $code / $reason")
            scheduleReconnect()
        }
    }

    override fun connect(languageCode: String) {
        if (BuildConfig.BHASHINI_INFERENCE_API_KEY.isBlank()) {
            CallDiagnostics.log("STT", "[$tag] BHASHINI_INFERENCE_API_KEY is blank!")
            onError("Bhashini inference API key is missing")
            return
        }

        activeLanguage = languageCode
        _isRunning.set(true)
        doConnect()
    }

    private fun doConnect() {
        if (!_isRunning.get()) return

        val endpoint = BuildConfig.BHASHINI_STT_ENDPOINT.ifBlank {
            "wss://dhruva-api.bhashini.gov.in/v1/stt/stream"
        }

        CallDiagnostics.log("STT", "[$tag] Connecting to $endpoint (lang: $activeLanguage)...")

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", BuildConfig.BHASHINI_INFERENCE_API_KEY)
            .addHeader("x-pipeline-id", BuildConfig.BHASHINI_PIPELINE_ID)
            .build()

        webSocket = client.newWebSocket(request, wsListener)
    }

    private fun scheduleReconnect() {
        if (!_isRunning.get()) return
        CallDiagnostics.log("STT", "[$tag] Scheduling reconnect in ${backoffDelayMs / 1000}s...")
        scheduler.schedule({
            if (_isRunning.get() && !_isConnected.get()) {
                doConnect()
            }
        }, backoffDelayMs, TimeUnit.MILLISECONDS)
        backoffDelayMs = (backoffDelayMs * 2).coerceAtMost(10000L)
    }

    override fun pushPcm(samples: ShortArray, sampleRate: Int) {
        if (!_isRunning.get() || samples.isEmpty()) return

        val resampled = if (sampleRate == TARGET_SAMPLE_RATE) {
            samples
        } else {
            resampleLinear(samples, sampleRate, TARGET_SAMPLE_RATE)
        }

        if (!_isConnected.get()) {
            ringBuffer.push(resampled)
            return
        }

        sendPcmInternal(resampled)
    }

    private fun sendPcmInternal(samples: ShortArray) {
        val pcmBytes = ByteArray(samples.size * 2)
        val byteBuf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            byteBuf.putShort(s)
        }

        synchronized(pushBuffer) {
            pushBuffer.write(pcmBytes)
            while (pushBuffer.size() >= CHUNK_SIZE_BYTES) {
                val fullBytes = pushBuffer.toByteArray()
                val chunk = fullBytes.copyOfRange(0, CHUNK_SIZE_BYTES)
                webSocket?.send(chunk.toByteString())
                pushBuffer.reset()
                if (fullBytes.size > CHUNK_SIZE_BYTES) {
                    pushBuffer.write(fullBytes, CHUNK_SIZE_BYTES, fullBytes.size - CHUNK_SIZE_BYTES)
                }
            }
        }
    }

    private fun flushRingBuffer() {
        val buffered = ringBuffer.popAll()
        if (buffered.isNotEmpty()) {
            CallDiagnostics.log("STT", "[$tag] Flushing ${buffered.size} buffered samples after reconnect")
            sendPcmInternal(buffered)
        }
    }

    private fun resampleLinear(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate <= 0 || toRate <= 0) return input
        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outLength = (input.size / ratio).toInt()
        val out = ShortArray(outLength)
        for (i in 0 until outLength) {
            val srcIndex = i * ratio
            val srcIndexFloor = srcIndex.toInt()
            val fraction = srcIndex - srcIndexFloor
            if (srcIndexFloor + 1 < input.size) {
                val sample1 = input[srcIndexFloor].toDouble()
                val sample2 = input[srcIndexFloor + 1].toDouble()
                out[i] = (sample1 + fraction * (sample2 - sample1)).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            } else if (srcIndexFloor < input.size) {
                out[i] = input[srcIndexFloor]
            }
        }
        return out
    }

    private fun sendStartEvent(ws: WebSocket, lang: String) {
        try {
            val startPayload = JSONObject().apply {
                put("event", "start")
                put("language", lang)
                put("useVad", true)
                put("inputEncoding", JSONObject().apply {
                    put("encoding", "linear16")
                    put("samplingRate", TARGET_SAMPLE_RATE)
                    put("bitsPerSample", 16)
                    put("numChannels", 1)
                })
                put("vadConfig", JSONObject().apply {
                    put("pStart", 0.4)
                    put("pauseMs", 800)
                })
                put("interimIntervalMs", 200)
            }
            ws.send(startPayload.toString())
        } catch (e: Exception) {
            CallDiagnostics.log("STT", "[$tag] Failed to send start payload: ${e.message}")
        }
    }

    private fun parseServerMessage(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val event = json.optString("event", "")

            when (event) {
                "speech_end", "transcript" -> {
                    val text = json.optString("text", json.optString("transcript", ""))
                    val isFinal = json.optBoolean("isFinal", event == "speech_end")
                    if (text.isNotBlank()) {
                        onTranscript(text.trim(), isFinal)
                    }
                }
                else -> {
                    val pipelineResponse = json.optJSONArray("pipelineResponse")
                    if (pipelineResponse != null && pipelineResponse.length() > 0) {
                        val firstResp = pipelineResponse.getJSONObject(0)
                        val outputArr = firstResp.optJSONArray("output")
                        if (outputArr != null && outputArr.length() > 0) {
                            val outputObj = outputArr.getJSONObject(0)
                            val text = outputObj.optString("source", outputObj.optString("target", ""))
                            val isFinal = outputObj.optBoolean("isFinal", true)
                            if (text.isNotBlank()) {
                                onTranscript(text.trim(), isFinal)
                            }
                        }
                    } else if (json.has("text") || json.has("transcript")) {
                        val text = json.optString("text", json.optString("transcript", ""))
                        val isFinal = json.optBoolean("isFinal", false)
                        if (text.isNotBlank()) {
                            onTranscript(text.trim(), isFinal)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            CallDiagnostics.log("STT", "[$tag] Error parsing response: ${e.message}")
        }
    }

    override fun finalizeStream() {
        try {
            webSocket?.send(JSONObject().apply { put("event", "finalize") }.toString())
        } catch (_: Exception) {}
    }

    override fun close() {
        _isRunning.set(false)
        _isConnected.set(false)
        try {
            webSocket?.close(1000, "Closed")
        } catch (_: Exception) {}
        webSocket = null
        ringBuffer.clear()
        synchronized(pushBuffer) {
            pushBuffer.reset()
        }
        CallDiagnostics.log("STT", "[$tag] Stopped cleanly")
    }
}
