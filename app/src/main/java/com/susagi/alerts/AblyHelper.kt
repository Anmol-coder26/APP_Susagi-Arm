package com.susagi.alerts

import android.content.Context
import android.util.Log
import com.guardian.app.BuildConfig
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.types.AblyException
import io.ably.lib.types.ClientOptions
import io.ably.lib.types.Message
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

object AblyHelper {
    private const val TAG = "AblyHelper"
    private var client: AblyRealtime? = null
    private val _incomingAlerts = MutableSharedFlow<AlertPayload>(extraBufferCapacity = 16)
    val incomingAlerts: SharedFlow<AlertPayload> = _incomingAlerts.asSharedFlow()

    data class AlertPayload(
        val callerNumber: String,
        val riskScore: Int,
        val transcriptSnippet: String,
        val scamType: String
    )

    fun connect(context: Context, clientId: String, apiKey: String = BuildConfig.ABLY_API_KEY) {
        if (client != null) return
        if (apiKey.isBlank() || apiKey == "YOUR_ABLY_API_KEY") {
            Log.w(TAG, "Ably API key not configured, skipping Ably connection")
            return
        }
        try {
            val options = ClientOptions(apiKey).apply {
                this.clientId = clientId
                this.autoConnect = true
            }
            client = AblyRealtime(options).apply {
                connection.on { stateChange ->
                    when (stateChange.current) {
                        ConnectionState.connected -> {
                            Log.d(TAG, "Ably connected cleanly for clientId: $clientId")
                            subscribeToFamilyChannel(clientId)
                        }
                        ConnectionState.failed -> {
                            Log.e(TAG, "Ably connection failed: ${stateChange.reason}")
                        }
                        else -> {
                            Log.d(TAG, "Ably state change: ${stateChange.current}")
                        }
                    }
                }
            }
        } catch (e: AblyException) {
            Log.e(TAG, "Ably connect exception: ${e.message}", e)
        }
    }

    private fun subscribeToFamilyChannel(clientId: String) {
        val channel = client?.channels?.get("family:$clientId") ?: return
        channel.subscribe("alert") { message ->
            try {
                when (val data = message.data) {
                    is Map<*, *> -> {
                        _incomingAlerts.tryEmit(
                            AlertPayload(
                                callerNumber = data["callerNumber"]?.toString() ?: "Unknown",
                                riskScore = (data["riskScore"] as? Number)?.toInt() ?: 0,
                                transcriptSnippet = data["transcript"]?.toString() ?: "",
                                scamType = data["scamType"]?.toString() ?: "Unknown"
                            )
                        )
                    }
                    is String -> {
                        val json = JSONObject(data)
                        _incomingAlerts.tryEmit(
                            AlertPayload(
                                callerNumber = json.optString("callerNumber", "Unknown"),
                                riskScore = json.optInt("riskScore", 0),
                                transcriptSnippet = json.optString("transcript", ""),
                                scamType = json.optString("scamType", "Unknown")
                            )
                        )
                    }
                    is JSONObject -> {
                        _incomingAlerts.tryEmit(
                            AlertPayload(
                                callerNumber = data.optString("callerNumber", "Unknown"),
                                riskScore = data.optInt("riskScore", 0),
                                transcriptSnippet = data.optString("transcript", ""),
                                scamType = data.optString("scamType", "Unknown")
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing incoming Ably message: ${e.message}", e)
            }
        }
    }

    fun disconnect() {
        try {
            client?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing Ably client: ${e.message}", e)
        }
        client = null
    }
}
