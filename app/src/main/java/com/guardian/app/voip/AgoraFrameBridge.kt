package com.guardian.app.voip

import io.agora.rtc2.Constants
import io.agora.rtc2.IAudioFrameObserver
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.audio.AudioParams
import com.susagi.audio.AudioFramePipeline
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AgoraFrameBridge routes Agora's raw PCM callbacks directly to AudioFramePipelines.
 *
 * CRITICAL PERFORMANCE REQUIREMENT:
 * onRecordAudioFrame and onPlaybackAudioFrameBeforeMixing MUST remain non-blocking.
 * All array copies and buffer pushes to AudioFramePipeline are instant O(1) operations.
 */
class AgoraFrameBridge(
    private val rtcEngine: RtcEngine,
    private val localPipeline: AudioFramePipeline? = null,
    private val remotePipeline: AudioFramePipeline? = null,
    private val onLocalPcm: ((ShortArray, Int) -> Unit)? = null,
    private val onRemotePcm: ((uid: Int, ShortArray, Int) -> Unit)? = null
) : IAudioFrameObserver {

    fun register() {
        rtcEngine.registerAudioFrameObserver(this)
        rtcEngine.setPlaybackAudioFrameParameters(16000, 1, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1600)
        rtcEngine.setRecordingAudioFrameParameters(16000, 1, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1600)
    }

    fun unregister() {
        rtcEngine.registerAudioFrameObserver(null)
    }

    override fun onRecordAudioFrame(
        channelId: String?,
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avSyncType: Int
    ): Boolean {
        val safeRate = if (samplesPerSec in 8000..48000) samplesPerSec else 16000
        val safeChannels = if (channels in 1..2) channels else 1
        buffer?.let { buf ->
            val remaining = buf.remaining()
            if (remaining > 0) {
                val bytes = ByteArray(remaining)
                val dup = buf.duplicate()
                dup.get(bytes)

                // Non-blocking push to AudioFramePipeline with validated sample rate and channels
                localPipeline?.onRawPcm(bytes, 0, bytes.size, safeRate, safeChannels)

                // Backwards-compatible callback path
                if (onLocalPcm != null) {
                    val shorts = byteBufferToShorts(buf, samplesPerChannel, safeChannels)
                    if (shorts.isNotEmpty()) {
                        onLocalPcm.invoke(shorts, safeRate)
                    }
                }
            }
        }
        return true
    }

    override fun onPlaybackAudioFrameBeforeMixing(
        channelId: String?,
        uid: Int,
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avSyncType: Int,
        is_mute: Int
    ): Boolean {
        val safeRate = if (samplesPerSec in 8000..48000) samplesPerSec else 16000
        val safeChannels = if (channels in 1..2) channels else 1
        buffer?.let { buf ->
            val remaining = buf.remaining()
            if (remaining > 0) {
                val bytes = ByteArray(remaining)
                val dup = buf.duplicate()
                dup.get(bytes)

                // Non-blocking push to AudioFramePipeline with validated sample rate and channels
                remotePipeline?.onRawPcm(bytes, 0, bytes.size, safeRate, safeChannels)

                // Backwards-compatible callback path
                if (onRemotePcm != null) {
                    val shorts = byteBufferToShorts(buf, samplesPerChannel, safeChannels)
                    if (shorts.isNotEmpty()) {
                        onRemotePcm.invoke(uid, shorts, safeRate)
                    }
                }
            }
        }
        return true
    }

    override fun onPlaybackAudioFrame(
        channelId: String?,
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avSyncType: Int
    ): Boolean = true

    override fun onMixedAudioFrame(
        channelId: String?,
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avSyncType: Int
    ): Boolean = true

    override fun onEarMonitoringAudioFrame(
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avSyncType: Int
    ): Boolean = true

    override fun getObservedAudioFramePosition(): Int = 0x0008 or 0x0001 // AUDIO_FRAME_POSITION_BEFORE_MIXING | AUDIO_FRAME_POSITION_RECORD

    override fun getRecordAudioParams(): AudioParams? = null
    override fun getPlaybackAudioParams(): AudioParams? = null
    override fun getMixedAudioParams(): AudioParams? = null
    override fun getEarMonitoringAudioParams(): AudioParams? = null

    private fun byteBufferToShorts(byteBuffer: ByteBuffer, samplesPerChannel: Int, channels: Int): ShortArray {
        val totalSamples = samplesPerChannel * channels
        val shortArray = ShortArray(totalSamples)
        val duplicate = byteBuffer.duplicate()
        duplicate.order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = duplicate.asShortBuffer()
        val count = minOf(shortBuffer.remaining(), totalSamples)
        if (count > 0) {
            shortBuffer.get(shortArray, 0, count)
        }
        return shortArray
    }
}
