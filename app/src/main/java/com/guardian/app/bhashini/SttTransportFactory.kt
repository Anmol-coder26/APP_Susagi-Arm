package com.guardian.app.bhashini

import com.guardian.app.BuildConfig

object SttTransportFactory {
    fun create(
        tag: String,
        onTranscript: (text: String, isFinal: Boolean) -> Unit,
        onError: (error: String) -> Unit = {}
    ): SttTransport {
        return if (BuildConfig.BHASHINI_TRANSPORT.equals("A", ignoreCase = true)) {
            RawWebSocketSttTransport(tag, onTranscript, onError)
        } else {
            SocketIoSttTransport(tag, onTranscript, onError)
        }
    }
}
