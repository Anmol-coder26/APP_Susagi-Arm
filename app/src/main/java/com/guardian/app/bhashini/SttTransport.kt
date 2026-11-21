package com.guardian.app.bhashini

interface SttTransport {
    fun connect(languageCode: String)
    fun pushPcm(samples: ShortArray, sampleRate: Int)
    fun finalizeStream()
    fun close()
    val isConnected: Boolean
    val isRunning: Boolean
}
