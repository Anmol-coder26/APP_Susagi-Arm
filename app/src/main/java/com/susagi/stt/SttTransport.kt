package com.susagi.stt

interface SttTransportListener {
    fun onOpen()
    fun onTranscript(text: String, isFinal: Boolean)
    fun onError(throwable: Throwable)
    fun onClose(code: Int, reason: String)
}

interface SttTransport {
    val isConnected: Boolean
    fun connect(url: String, authToken: String?, language: String, listener: SttTransportListener)
    fun sendStart(language: String)
    fun sendAudio(data: ByteArray): Boolean
    fun close()
}
