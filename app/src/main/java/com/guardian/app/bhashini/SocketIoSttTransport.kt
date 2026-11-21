package com.guardian.app.bhashini

import android.util.Base64
import com.guardian.app.BuildConfig
import com.guardian.app.voip.CallDiagnostics
import io.socket.client.IO
import io.socket.client.Socket
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SocketIoSttTransport(
    private val tag: String = "STT-SocketIO",
    private val onTranscript: (text: String, isFinal: Boolean) -> Unit,
    private val onError: (error: String) -> Unit = {}
) : SttTransport {

    companion object {
        private const val TARGET_SAMPLE_RATE = 16000
        private const val CHUNK_SIZE_BYTES = 3200 // 100ms at 16kHz 16-bit mono
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var socket: Socket? = null
    private val _isRunning = AtomicBoolean(false)
    private val _isConnected = AtomicBoolean(false)
    private var activeLanguage = "hi"
    private var currentServiceId = ""
    private var inferenceApiKey = ""
    private var callbackUrl = "https://dhruva-api.bhashini.gov.in"
    private val pushBuffer = ByteArrayOutputStream()
    private val ringBuffer = AudioRingBufferQueue()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    private var backoffDelayMs = 1000L

    override val isConnected: Boolean get() = _isConnected.get()
    override val isRunning: Boolean get() = _isRunning.get()

    override fun connect(languageCode: String) {
        if (BuildConfig.BHASHINI_INFERENCE_API_KEY.isBlank() && BuildConfig.BHASHINI_ULCA_API_KEY.isBlank()) {
            CallDiagnostics.log("STT", "[$tag] Bhashini API keys are missing in BuildConfig!")
            onError("Bhashini inference API key is missing")
            return
        }

        activeLanguage = languageCode
        _isRunning.set(true)

        // Run pipeline config fetch on worker thread
        Executors.newSingleThreadExecutor().execute {
            fetchPipelineConfigAndConnect()
        }
    }

    private fun fetchPipelineConfigAndConnect() {
        if (!_isRunning.get()) return

        CallDiagnostics.log("STT", "[$tag] Requesting Dhruva pipeline config for lang: $activeLanguage...")

        val restEndpoint = BuildConfig.BHASHINI_REST_ENDPOINT.ifBlank {
            "https://dhruva-api.bhashini.gov.in/services/inference/pipeline"
        }

        val userId = BuildConfig.BHASHINI_USER_ID
        val ulcaKey = BuildConfig.BHASHINI_ULCA_API_KEY.ifBlank { BuildConfig.BHASHINI_INFERENCE_API_KEY }
        val pipelineId = BuildConfig.BHASHINI_PIPELINE_ID.ifBlank { "64392f96daac500b55c543d6" }

        val payload = JSONObject().apply {
            put("pipelineTasks", JSONArray().apply {
                put(JSONObject().apply {
                    put("taskType", "asr")
                    put("config", JSONObject().apply {
                        put("language", JSONObject().apply {
                            put("sourceLanguage", activeLanguage)
                        })
                    })
                })
            })
            put("pipelineRequestConfig", JSONObject().apply {
                put("pipelineId", pipelineId)
            })
        }

        val reqBuilder = Request.Builder()
            .url(restEndpoint)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))

        if (userId.isNotBlank()) reqBuilder.addHeader("userID", userId)
        if (ulcaKey.isNotBlank()) {
            reqBuilder.addHeader("ulcaApiKey", ulcaKey)
            reqBuilder.addHeader("Authorization", ulcaKey)
        }

        try {
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    val endPoint = json.optJSONObject("pipelineInferenceAPIEndPoint")
                    callbackUrl = endPoint?.optString("callbackUrl", "https://dhruva-api.bhashini.gov.in")
                        ?: "https://dhruva-api.bhashini.gov.in"
                    
                    val keyObj = endPoint?.optJSONObject("inferenceApiKey")
                    inferenceApiKey = keyObj?.optString("value", ulcaKey) ?: ulcaKey

                    val tasks = json.optJSONArray("pipelineResponseConfig")
                    if (tasks != null && tasks.length() > 0) {
                        val taskConfig = tasks.getJSONObject(0).optJSONArray("config")
                        if (taskConfig != null && taskConfig.length() > 0) {
                            currentServiceId = taskConfig.getJSONObject(0).optString("serviceId", "")
                        }
                    }
                    CallDiagnostics.log("STT", "[$tag] Pipeline config succeeded. ServiceId: $currentServiceId")
                } else {
                    CallDiagnostics.log("STT", "[$tag] Pipeline config HTTP ${response.code}: $bodyStr")
                    // Fall back to defaults
                    currentServiceId = "ai4bharat/conformer-hi-gpu--t4"
                    inferenceApiKey = ulcaKey
                }
            }
        } catch (e: Exception) {
            CallDiagnostics.log("STT", "[$tag] Pipeline config error: ${e.message}")
            currentServiceId = "ai4bharat/conformer-hi-gpu--t4"
            inferenceApiKey = ulcaKey
        }

        initSocketIo()
    }

    private fun initSocketIo() {
        if (!_isRunning.get()) return

        try {
            val options = IO.Options().apply {
                transports = arrayOf("websocket")
                timeout = 5000
                extraHeaders = mapOf(
                    "Authorization" to listOf(inferenceApiKey.ifBlank { BuildConfig.BHASHINI_INFERENCE_API_KEY })
                )
            }

            CallDiagnostics.log("STT", "[$tag] Connecting Socket.IO to $callbackUrl...")
            val s = IO.socket(callbackUrl, options)

            s.on(Socket.EVENT_CONNECT) {
                _isConnected.set(true)
                backoffDelayMs = 1000L
                CallDiagnostics.log("STT", "[$tag] Socket.IO connected cleanly!")

                // Emit start event
                val startConfig = JSONObject().apply {
                    put("task", "asr")
                    put("serviceId", currentServiceId)
                    put("language", activeLanguage)
                    put("samplingRate", TARGET_SAMPLE_RATE)
                }
                s.emit("start", startConfig)
                flushRingBuffer()
            }

            s.on(Socket.EVENT_CONNECT_ERROR) { args ->
                _isConnected.set(false)
                val err = if (args.isNotEmpty()) args[0].toString() else "Connect error"
                CallDiagnostics.log("STT", "[$tag] Socket.IO connect error: $err")
                onError(err)
                scheduleReconnect()
            }

            s.on("response") { args -> handleServerPayload(args) }
            s.on("data") { args -> handleServerPayload(args) }
            s.on("transcript") { args -> handleServerPayload(args) }

            s.on(Socket.EVENT_DISCONNECT) {
                _isConnected.set(false)
                CallDiagnostics.log("STT", "[$tag] Socket.IO disconnected")
                scheduleReconnect()
            }

            socket = s
            s.connect()

        } catch (e: Exception) {
            _isConnected.set(false)
            CallDiagnostics.log("STT", "[$tag] Socket.IO init exception: ${e.message}")
            onError(e.message ?: "Socket.IO init exception")
            scheduleReconnect()
        }
    }

    private fun handleServerPayload(args: Array<Any>) {
        if (args.isEmpty()) return
        try {
            val dataStr = args[0].toString()
            val json = JSONObject(dataStr)
            
            var text = ""
            var isFinal = false

            // Shape 1: Bhashini Dhruva pipelineResponse schema
            val pipelineResp = json.optJSONArray("pipelineResponse")
            if (pipelineResp != null && pipelineResp.length() > 0) {
                val outputArr = pipelineResp.getJSONObject(0).optJSONArray("output")
                if (outputArr != null && outputArr.length() > 0) {
                    val outObj = outputArr.getJSONObject(0)
                    text = outObj.optString("source", outObj.optString("target", ""))
                    isFinal = outObj.optBoolean("isFinal", true)
                }
            }

            // Shape 2: Direct transcript object schema
            if (text.isBlank()) {
                text = json.optString("text", json.optString("transcript", json.optString("source", "")))
                isFinal = json.optBoolean("isFinal", json.optBoolean("final", false))
            }

            if (text.isNotBlank()) {
                onTranscript(text.trim(), isFinal)
            }
        } catch (e: Exception) {
            CallDiagnostics.log("STT", "[$tag] Error parsing Socket.IO payload: ${e.message}")
        }
    }

    private fun scheduleReconnect() {
        if (!_isRunning.get()) return
        CallDiagnostics.log("STT", "[$tag] Scheduling Socket.IO reconnect in ${backoffDelayMs / 1000}s...")
        scheduler.schedule({
            if (_isRunning.get() && !_isConnected.get()) {
                fetchPipelineConfigAndConnect()
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
                val base64Chunk = Base64.encodeToString(chunk, Base64.NO_WRAP)

                val dataPayload = JSONObject().apply {
                    put("audio", JSONArray().apply {
                        put(JSONObject().apply {
                            put("audioContent", base64Chunk)
                        })
                    })
                }

                socket?.emit("data", dataPayload)
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

    override fun finalizeStream() {
        try {
            socket?.emit("terminate", JSONObject())
        } catch (_: Exception) {}
    }

    override fun close() {
        _isRunning.set(false)
        _isConnected.set(false)
        try {
            socket?.disconnect()
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        ringBuffer.clear()
        synchronized(pushBuffer) {
            pushBuffer.reset()
        }
        CallDiagnostics.log("STT", "[$tag] Stopped cleanly")
    }
}
