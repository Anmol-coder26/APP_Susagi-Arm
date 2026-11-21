package com.susagi.audio

import com.guardian.app.voip.CallDiagnostics
import com.guardian.app.voip.LogLevel
import io.agora.rtc2.Constants
import io.agora.rtc2.IAudioFrameObserver
import io.agora.rtc2.audio.AudioParams
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AudioFrameBridge
 *
 * Implements Agora's native [IAudioFrameObserver] interface.
 * Safely extracts raw PCM audio buffers on Agora's real-time audio threads and delegates
 * them immediately to independent local and remote processing pipelines without blocking.
 */
class AudioFrameBridge(
    private val localPipeline: AudioFramePipeline,
    private val remotePipeline: AudioFramePipeline,
    private val onRemoteUidDetected: ((Int) -> Unit)? = null
) : IAudioFrameObserver {

    private val hasReceivedBeforeMixing = AtomicBoolean(false)
    @Volatile private var lastRemoteUid: Int = 0

    companion object {
        /**
         * Safely extracts an independent, deep-copied PCM byte array from Agora's native ByteBuffer.
         * Decouples caller buffer memory from downstream queues, ensuring subsequent native mutations
         * cannot corrupt speech recognition or VAD processing.
         */
        fun extractOwnedPcm(
            buffer: ByteBuffer?,
            samplesPerChannel: Int,
            channels: Int,
            bytesPerSample: Int
        ): ByteArray? {
            if (buffer == null || samplesPerChannel <= 0 || channels !in 1..2 || bytesPerSample != 2) {
                return null
            }
            val expectedBytes = samplesPerChannel * channels * bytesPerSample
            return try {
                val dup = buffer.duplicate()
                dup.position(0)
                val available = dup.remaining()
                val readLength = minOf(expectedBytes, available)
                if (readLength <= 0) return null

                val ownedBytes = ByteArray(readLength)
                dup.get(ownedBytes, 0, readLength)
                ownedBytes
            } catch (e: Exception) {
                null
            }
        }
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
        // Strict frame validation: reject corrupted or uninitialized audio buffers
        if (samplesPerSec !in 8000..48000 || channels !in 1..2 || bytesPerSample != 2 || buffer == null) {
            localPipeline.recordInvalidFrame()
            return true
        }

        try {
            val ownedBytes = extractOwnedPcm(buffer, samplesPerChannel, channels, bytesPerSample)
            if (ownedBytes != null && ownedBytes.isNotEmpty()) {
                localPipeline.onRawPcm(ownedBytes, 0, ownedBytes.size, samplesPerSec, channels)
            } else {
                localPipeline.recordInvalidFrame()
            }
        } catch (e: Exception) {
            CallDiagnostics.log("PCM", "onRecordAudioFrame copy error: ${e.message}", LogLevel.WARN)
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
        hasReceivedBeforeMixing.set(true)
        if (uid != lastRemoteUid && uid != 0) {
            lastRemoteUid = uid
            onRemoteUidDetected?.invoke(uid)
        }

        // Strict frame validation: reject corrupted or uninitialized audio buffers
        if (samplesPerSec !in 8000..48000 || channels !in 1..2 || bytesPerSample != 2 || buffer == null) {
            remotePipeline.recordInvalidFrame()
            return true
        }

        try {
            val ownedBytes = extractOwnedPcm(buffer, samplesPerChannel, channels, bytesPerSample)
            if (ownedBytes != null && ownedBytes.isNotEmpty()) {
                remotePipeline.onRawPcm(ownedBytes, 0, ownedBytes.size, samplesPerSec, channels)
            } else {
                remotePipeline.recordInvalidFrame()
            }
        } catch (e: Exception) {
            CallDiagnostics.log("PCM", "onPlaybackAudioFrameBeforeMixing copy error: ${e.message}", LogLevel.WARN)
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
    ): Boolean {
        // Anti-duplication guard: If pre-mixing callbacks are active, completely ignore mixed playback
        // to prevent double-feeding the remote ASR pipeline.
        if (hasReceivedBeforeMixing.get()) {
            return true
        }

        if (samplesPerSec !in 8000..48000 || channels !in 1..2 || bytesPerSample != 2 || buffer == null) {
            remotePipeline.recordInvalidFrame()
            return true
        }

        try {
            val ownedBytes = extractOwnedPcm(buffer, samplesPerChannel, channels, bytesPerSample)
            if (ownedBytes != null && ownedBytes.isNotEmpty()) {
                remotePipeline.onRawPcm(ownedBytes, 0, ownedBytes.size, samplesPerSec, channels)
            } else {
                remotePipeline.recordInvalidFrame()
            }
        } catch (e: Exception) {
            CallDiagnostics.log("PCM", "onPlaybackAudioFrame fallback error: ${e.message}", LogLevel.WARN)
        }
        return true
    }

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

    override fun getObservedAudioFramePosition(): Int =
        Constants.POSITION_RECORD or Constants.POSITION_PLAYBACK or Constants.POSITION_BEFORE_MIXING

    override fun getRecordAudioParams(): AudioParams =
        AudioParams(16000, 1, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1600)

    override fun getPlaybackAudioParams(): AudioParams =
        AudioParams(16000, 1, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY, 1600)

    override fun getMixedAudioParams(): AudioParams? = null
    override fun getEarMonitoringAudioParams(): AudioParams? = null
}
