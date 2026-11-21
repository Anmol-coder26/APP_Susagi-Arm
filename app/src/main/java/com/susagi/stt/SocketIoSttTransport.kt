package com.susagi.stt

import android.util.Base64
import com.guardian.app.BuildConfig
import com.guardian.app.voip.CallDiagnostics
import com.guardian.app.voip.LogLevel
import io.socket.client.IO
import io.socket.client.Socket
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SocketIoSttTransport(
    private val tag: String = "STT-SocketIO"
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
    private var currentServiceId = "ai4bharat/conformer-hi-gpu--t4"
    private var inferenceApiKey = ""
    private var callbackUrl = "https://dhruva-api.bhashini.gov.in"
    private val pushBuffer = ByteArrayOutputStream()
    private val pendingAudioQueue = ByteArrayOutputStream()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var listener: SttTransportListener? = null
    private var endpointUrl: String = ""
    private var tokenAuth: String? = null
    private var backoffDelayMs = 1000L

    override val isConnected: Boolean get() = _isConnected.get()

    override fun connect(url: String, authToken: String?, language: String, listener: SttTransportListener) {
        this.endpointUrl = url
        this.tokenAuth = authToken
        this.activeLanguage = language
        this.listener = listener
        this._isRunning.set(true)
        this.backoffDelayMs = 1000L

        CallDiagnostics.log("STT-TRANS", "[$tag] Starting Socket.IO transport for lang: $language (endpoint: $url)")

        Executors.newSingleThreadExecutor().execute {
            fetchPipelineConfigAndConnect()
        }
    }

    private fun fetchPipelineConfigAndConnect() {
        if (!_isRunning.get()) return

        val configEndpoint = "https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline"

        val userId = BuildConfig.BHASHINI_USER_ID
        if (userId.contains("@")) {
            val err = "BHASHINI_USER_ID must be the ALPHANUMERIC ID, NOT an email address: '$userId'"
            CallDiagnostics.log("STT-TRANS", "[$tag] $err", LogLevel.ERROR)
            _isConnected.set(false)
            listener?.onError(IllegalArgumentException(err))
            scheduleReconnect(30000L)
            return
        }

        val ulcaKey = BuildConfig.BHASHINI_ULCA_API_KEY.ifBlank {
            tokenAuth ?: BuildConfig.BHASHINI_INFERENCE_API_KEY
        }
        val pipelineId = BuildConfig.BHASHINI_PIPELINE_ID.ifBlank { "64392f96daac500b55c543cd" }

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
            .url(configEndpoint)
            .addHeader("Content-Type", "application/json")
            .addHeader("userID", userId)
            .addHeader("ulcaApiKey", ulcaKey)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))

        try {
            CallDiagnostics.log("STT-TRANS", "[$tag] Making ULCA handshake to $configEndpoint with userID=$userId...")
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    val socketEndPoint = json.optJSONObject("pipelineInferenceSocketEndPoint")
                    val apiEndPoint = json.optJSONObject("pipelineInferenceAPIEndPoint")
                    val endPoint = socketEndPoint ?: apiEndPoint

                    callbackUrl = endPoint?.optString("callbackUrl", "")
                        ?: endPoint?.optString("socketUrl", "")
                        ?: ""

                    if (callbackUrl.isBlank()) {
                        val noUrlErr = "ULCA handshake succeeded but callbackUrl is missing in response"
                        CallDiagnostics.log("STT-TRANS", "[$tag] $noUrlErr: $bodyStr", LogLevel.ERROR)
                        _isConnected.set(false)
                        listener?.onError(Exception(noUrlErr))
                        scheduleReconnect(30000L)
                        return
                    }

                    val keyObj = endPoint?.optJSONObject("inferenceApiKey")
                    inferenceApiKey = keyObj?.optString("value", ulcaKey) ?: ulcaKey

                    val tasks = json.optJSONArray("pipelineResponseConfig")
                    if (tasks != null && tasks.length() > 0) {
                        val taskConfig = tasks.getJSONObject(0).optJSONArray("config")
                        if (taskConfig != null && taskConfig.length() > 0) {
                            currentServiceId = taskConfig.getJSONObject(0).optString("serviceId", "ai4bharat/conformer-hi-gpu--t4")
                        }
                    }
                    CallDiagnostics.log("STT-TRANS", "[$tag] Handshake OK! ServiceId: $currentServiceId, Callback: $callbackUrl")
                    initSocketIo()
                } else {
                    val errMsg = try {
                        val errJson = JSONObject(bodyStr)
                        errJson.optString("message", bodyStr.take(160))
                    } catch (_: Exception) {
                        bodyStr.take(160)
                    }
                    val fullErr = "Bhashini ULCA Handshake HTTP ${response.code}: $errMsg"
                    val directKey = BuildConfig.BHASHINI_INFERENCE_API_KEY.ifBlank { tokenAuth ?: "" }
                    if (directKey.isNotBlank()) {
                        CallDiagnostics.log("STT-TRANS", "[$tag] $fullErr -> Direct inference key found, connecting to Dhruva ($callbackUrl)", LogLevel.WARN)
                        inferenceApiKey = directKey
                        initSocketIo()
                    } else {
                        CallDiagnostics.log("STT-TRANS", "[$tag] $fullErr", LogLevel.ERROR)
                        _isConnected.set(false)
                        listener?.onError(Exception(fullErr))
                        scheduleReconnect(30000L)
                    }
                }
            }
        } catch (e: Exception) {
            val netErr = "ULCA handshake network error: ${e.message}"
            val directKey = BuildConfig.BHASHINI_INFERENCE_API_KEY.ifBlank { tokenAuth ?: "" }
            if (directKey.isNotBlank()) {
                CallDiagnostics.log("STT-TRANS", "[$tag] $netErr -> Direct inference key found, connecting to Dhruva ($callbackUrl)", LogLevel.WARN)
                inferenceApiKey = directKey
                initSocketIo()
            } else {
                CallDiagnostics.log("STT-TRANS", "[$tag] $netErr", LogLevel.ERROR)
                _isConnected.set(false)
                listener?.onError(e)
                scheduleReconnect(15000L)
            }
        }
    }

    private fun initSocketIo() {
        if (!_isRunning.get()) return

        try {
            val options = IO.Options().apply {
                transports = arrayOf("websocket", "polling")
                timeout = 10000
                if (inferenceApiKey.isNotBlank()) {
                    extraHeaders = mapOf("Authorization" to listOf(inferenceApiKey))
                }
            }

            CallDiagnostics.log("STT-TRANS", "[$tag] Connecting Socket.IO to $callbackUrl...")
            val s = IO.socket(callbackUrl, options)

            s.on(Socket.EVENT_CONNECT) {
                _isConnected.set(true)
                backoffDelayMs = 1000L
                CallDiagnostics.log("STT-TRANS", "[$tag] Socket.IO connected cleanly!")

                sendStart(activeLanguage)
                listener?.onOpen()
                flushPendingAudio()
            }

            s.on(Socket.EVENT_CONNECT_ERROR) { args ->
                _isConnected.set(false)
                val err = if (args.isNotEmpty()) args[0].toString() else "Connect error"
                CallDiagnostics.log("STT-TRANS", "[$tag] Socket.IO connect error: $err", LogLevel.WARN)
                listener?.onError(Exception(err))
                scheduleReconnect()
            }

            s.on(Socket.EVENT_DISCONNECT) {
                _isConnected.set(false)
                CallDiagnostics.log("STT-TRANS", "[$tag] Socket.IO disconnected", LogLevel.WARN)
                listener?.onClose(1000, "Disconnected")
                scheduleReconnect()
            }

            s.on("response") { args -> handleServerPayload(args) }
            s.on("data") { args -> handleServerPayload(args) }
            s.on("transcript") { args -> handleServerPayload(args) }
            s.on("message") { args -> handleServerPayload(args) }
            s.on("ready") { CallDiagnostics.log("STT-TRANS", "[$tag] Bhashini Dhruva server ready event received") }

            socket = s
            s.connect()

        } catch (e: Exception) {
            _isConnected.set(false)
            CallDiagnostics.log("STT-TRANS", "[$tag] Socket.IO initialization failed: ${e.message}", LogLevel.ERROR)
            listener?.onError(e)
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
                listener?.onTranscript(text.trim(), isFinal)
            }
        } catch (e: Exception) {
            CallDiagnostics.log("STT-TRANS", "[$tag] Error parsing Socket.IO payload: ${e.message}", LogLevel.WARN)
        }
    }

    private fun scheduleReconnect(delayMs: Long = backoffDelayMs) {
        if (!_isRunning.get()) return
        CallDiagnostics.log("STT-TRANS", "[$tag] Scheduling Socket.IO reconnect in ${delayMs / 1000}s...", LogLevel.WARN)
        scheduler.schedule({
            if (_isRunning.get() && !_isConnected.get()) {
                fetchPipelineConfigAndConnect()
            }
        }, delayMs, TimeUnit.MILLISECONDS)
        backoffDelayMs = (backoffDelayMs * 2).coerceAtMost(30000L)
    }

    override fun sendStart(language: String) {
        val s = socket ?: return
        this.activeLanguage = language
        val startConfig = JSONObject().apply {
            put("event", "start")
            put("language", language)
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
            put("preProcessors", JSONArray().apply {
                put("vad")
            })
        }
        try {
            s.emit("start", startConfig)
            CallDiagnostics.log("STT-TRANS", "[$tag] Sent Socket.IO start event for lang=$language: $startConfig")
        } catch (e: Exception) {
            CallDiagnostics.log("STT-TRANS", "[$tag] Failed to send start event: ${e.message}", LogLevel.ERROR)
            listener?.onError(e)
        }
    }

    override fun sendAudio(data: ByteArray): Boolean {
        if (!_isRunning.get() || data.isEmpty()) return false
        val s = socket
        if (s == null || !_isConnected.get()) {
            synchronized(pendingAudioQueue) {
                // Keep max 10s buffer (~320000 bytes)
                if (pendingAudioQueue.size() < 320000) {
                    pendingAudioQueue.write(data)
                }
            }
            return false
        }

        synchronized(pushBuffer) {
            pushBuffer.write(data)
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

                s.emit("data", dataPayload)
                pushBuffer.reset()
                if (fullBytes.size > CHUNK_SIZE_BYTES) {
                    pushBuffer.write(fullBytes, CHUNK_SIZE_BYTES, fullBytes.size - CHUNK_SIZE_BYTES)
                }
            }
        }
        return true
    }

    private fun flushPendingAudio() {
        val bytes = synchronized(pendingAudioQueue) {
            val b = pendingAudioQueue.toByteArray()
            pendingAudioQueue.reset()
            b
        }
        if (bytes.isNotEmpty()) {
            CallDiagnostics.log("STT-TRANS", "[$tag] Flushing ${bytes.size} bytes of pending audio after connect")
            sendAudio(bytes)
        }
    }

    override fun close() {
        _isRunning.set(false)
        _isConnected.set(false)
        try {
            socket?.disconnect()
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        synchronized(pushBuffer) { pushBuffer.reset() }
        synchronized(pendingAudioQueue) { pendingAudioQueue.reset() }
        CallDiagnostics.log("STT-TRANS", "[$tag] Socket.IO transport closed cleanly")
    }
}
