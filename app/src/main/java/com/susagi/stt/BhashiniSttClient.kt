package com.susagi.stt

import android.util.Log
import com.guardian.app.voip.CallDiagnostics
import com.guardian.app.voip.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

class BhashiniSttClient(
    private var language: String,
    private val endpointUrl: String,
    private val authToken: String? = null,
    val label: String = "local",
    transport: SttTransport? = null,
    private val onTranscript: (text: String, isFinal: Boolean) -> Unit
) {
    constructor(
        language: String,
        endpointUrl: String,
        authToken: String? = null,
        label: String = "local",
        onTranscript: (text: String, isFinal: Boolean) -> Unit
    ) : this(language, endpointUrl, authToken, label, null, onTranscript)

    companion object {
        fun createDefaultTransport(endpointUrl: String, label: String): SttTransport {
            return when {
                endpointUrl.contains("inference/pipeline") || endpointUrl.contains("dhruva-api.bhashini.gov.in") -> {
                    DhruvaRestSttTransport(tag = "STT-Dhruva-$label")
                }
                endpointUrl.startsWith("ws://") || endpointUrl.startsWith("wss://") -> {
                    RawWebSocketSttTransport(label = "STT-WS-$label")
                }
                else -> {
                    SocketIoSttTransport(tag = "STT-SocketIO-$label")
                }
            }
        }
    }

    private val transport: SttTransport = transport ?: createDefaultTransport(endpointUrl, label)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val shouldRun = AtomicBoolean(false)
    private var reconnectAttempt = 0
    private var consecutiveFailures = 0
    private var reconnectJob: Job? = null
    private var receivedFirstResponse = false

    private val audioQueue = AudioRingBufferQueue()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun start() {
        shouldRun.set(true)
        reconnectAttempt = 0
        consecutiveFailures = 0
        receivedFirstResponse = false
        audioQueue.clear()
        CallDiagnostics.log("STT", "[$label] Starting STT client (lang=$language, endpoint=$endpointUrl, transport=${transport.javaClass.simpleName})")
        connect()
    }

    private fun connect() {
        if (!shouldRun.get()) return
        CallDiagnostics.log("STT", "[$label] Connecting transport to $endpointUrl")

        transport.connect(endpointUrl, authToken, language, object : SttTransportListener {
            override fun onOpen() {
                reconnectAttempt = 0
                consecutiveFailures = 0
                _isConnected.value = true
                CallDiagnostics.log("STT", "[$label] Transport connected successfully")

                // Flush buffered audio queue on reconnect
                flushBufferedAudio()
            }

            override fun onTranscript(text: String, isFinal: Boolean) {
                if (!receivedFirstResponse) {
                    receivedFirstResponse = true
                    CallDiagnostics.log("STT", "[$label] First transcript received: ${text.take(80)}")
                }
                CallDiagnostics.log("STT", "[$label] Transcript (isFinal=$isFinal): $text")
                onTranscript(text, isFinal)
            }

            override fun onError(throwable: Throwable) {
                CallDiagnostics.log("STT", "[$label] Transport error: ${throwable.message}", LogLevel.ERROR)
                _isConnected.value = false
                consecutiveFailures++
                scheduleReconnect()
            }

            override fun onClose(code: Int, reason: String) {
                CallDiagnostics.log("STT", "[$label] Transport closed ($code): $reason", LogLevel.WARN)
                _isConnected.value = false
                if (shouldRun.get()) scheduleReconnect()
            }
        })
    }

    private fun flushBufferedAudio() {
        val bytes = audioQueue.drain()
        if (bytes.isNotEmpty()) {
            CallDiagnostics.log("STT", "[$label] Flushing ${bytes.size} bytes of buffered audio to transport")
            val success = transport.sendAudio(bytes)
            if (!success) {
                CallDiagnostics.log("STT", "[$label] Flush buffered audio failed, re-queueing", LogLevel.WARN)
                audioQueue.pushBytes(bytes)
            }
        }
    }

    private fun scheduleReconnect() {
        if (!shouldRun.get()) return
        reconnectJob?.cancel()

        val delayMs = min(1000L * (1 shl reconnectAttempt), 16000L)
        reconnectAttempt++
        CallDiagnostics.log("STT", "[$label] Scheduling reconnect attempt #$reconnectAttempt in ${delayMs}ms", LogLevel.WARN)
        reconnectJob = scope.launch {
            delay(delayMs)
            if (shouldRun.get()) connect()
        }
    }

    fun pushPcm(samples: ShortArray, sampleRate: Int = 16000) {
        if (!shouldRun.get() || samples.isEmpty()) return
        val byteBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) byteBuffer.putShort(s)
        pushPcmBytes(byteBuffer.array(), sampleRate)
    }

    fun pushPcmBytes(bytes: ByteArray, sampleRate: Int = 16000) {
        if (!shouldRun.get() || bytes.isEmpty()) return
        if (label == "remote") {
            Log.d("RemoteStt", "Pushing ${bytes.size} PCM bytes to Bhashini ($label)")
        }
        if (transport.isConnected) {
            val sent = transport.sendAudio(bytes)
            if (!sent) {
                audioQueue.pushBytes(bytes)
            }
        } else {
            audioQueue.pushBytes(bytes)
        }
    }

    fun setLanguage(newLanguage: String) {
        if (language == newLanguage) return
        CallDiagnostics.log("STT", "[$label] Changing language from $language to $newLanguage")
        language = newLanguage
        if (transport.isConnected) {
            transport.sendStart(newLanguage)
        }
    }

    fun close() {
        shouldRun.set(false)
        reconnectJob?.cancel()
        transport.close()
        _isConnected.value = false
        audioQueue.clear()
        CallDiagnostics.log("STT", "[$label] Client closed")
        scope.cancel()
    }
}
