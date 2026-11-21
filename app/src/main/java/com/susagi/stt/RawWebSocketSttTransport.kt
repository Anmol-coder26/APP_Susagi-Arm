package com.susagi.stt

import com.guardian.app.voip.CallDiagnostics
import com.guardian.app.voip.LogLevel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RawWebSocketSttTransport(
    private val label: String = "ws-transport"
) : SttTransport {

    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var connected = false
    override val isConnected: Boolean get() = connected

    override fun connect(url: String, authToken: String?, language: String, listener: SttTransportListener) {
        CallDiagnostics.log("STT-TRANS", "[$label] Connecting to $url")
        val builder = Request.Builder().url(url)
        authToken?.let { builder.header("Authorization", "Bearer $it") }

        val wsListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                CallDiagnostics.log("STT-TRANS", "[$label] WebSocket connected (HTTP ${response.code})")
                sendStart(language)
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val transcript = json.optString(
                        "transcript",
                        json.optString("partial_transcript", "")
                    )
                    val isFinal = json.optBoolean("isFinal", json.optBoolean("final", false))
                    if (transcript.isNotBlank()) {
                        listener.onTranscript(transcript, isFinal)
                    }
                } catch (e: Exception) {
                    CallDiagnostics.log("STT-TRANS", "[$label] Error parsing message: ${e.message}", LogLevel.WARN)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                CallDiagnostics.log("STT-TRANS", "[$label] Connection failure: ${t.message}", LogLevel.ERROR)
                listener.onError(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                CallDiagnostics.log("STT-TRANS", "[$label] WebSocket closed ($code): $reason", LogLevel.WARN)
                listener.onClose(code, reason)
            }
        }

        webSocket = client.newWebSocket(builder.build(), wsListener)
    }

    override fun sendStart(language: String) {
        val ws = webSocket ?: return
        val startEvent = JSONObject().apply {
            put("event", "start")
            put("language", language)
            put("useVad", true)
            put("inputEncoding", JSONObject().apply {
                put("encoding", "linear16")
                put("samplingRate", 16000)
                put("bitsPerSample", 16)
                put("numChannels", 1)
            })
            put("vadConfig", JSONObject().apply {
                put("pStart", 0.4)
                put("pauseMs", 800)
            })
            put("interimIntervalMs", 200)
            put("preProcessors", JSONArray().put("vad"))
        }
        try {
            ws.send(startEvent.toString())
            CallDiagnostics.log("STT-TRANS", "[$label] Sent start event for lang=$language")
        } catch (e: Exception) {
            CallDiagnostics.log("STT-TRANS", "[$label] Failed to send start event: ${e.message}", LogLevel.WARN)
        }
    }

    override fun sendAudio(data: ByteArray): Boolean {
        val ws = webSocket ?: return false
        if (!connected) return false
        return try {
            ws.send(data.toByteString())
        } catch (e: Exception) {
            CallDiagnostics.log("STT-TRANS", "[$label] Send audio failed: ${e.message}", LogLevel.WARN)
            false
        }
    }

    override fun close() {
        connected = false
        webSocket?.let { ws ->
            try {
                ws.send(JSONObject().put("event", "finalize").toString())
                ws.send(JSONObject().put("event", "stop").toString())
            } catch (_: Exception) { }
            ws.close(1000, "transport closing")
        }
        webSocket = null
    }
}
