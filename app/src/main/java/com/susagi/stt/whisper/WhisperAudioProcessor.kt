package com.susagi.stt.whisper

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * WhisperAudioProcessor
 *
 * Processes real-time 16kHz 16-bit mono PCM audio for on-device Whisper transcription:
 * - Computes frame RMS energy for Voice Activity Detection (VAD).
 * - Implements a speech turn accumulator with configurable silence hangover (500ms).
 * - Enforces minimum turn length (800ms) to reject impulse noise and maximum segment (5.0s)
 *   to ensure low-latency incremental transcription during active calling.
 * - Wraps raw PCM into standard 44-byte RIFF WAV files for ExecuTorch [AsrModule].
 */
class WhisperAudioProcessor(
    private val tempDir: File,
    private val vadThresholdRms: Double = 120.0,
    private val minTurnBytes: Int = 25_600, // 800ms at 16kHz 16-bit mono (32,000 bytes/sec)
    private val maxSegmentBytes: Int = 160_000, // 5.0s at 16kHz 16-bit mono
    private val silenceHangoverFrames: Int = 5 // ~500ms at 100ms frames
) {

    private val speechBuffer = ByteArrayOutputStream(maxSegmentBytes)
    private var isSpeechActive = false
    private var silenceFrameCount = 0
    private var fileCounter = 0

    init {
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
    }

    /**
     * Feeds a chunk of 16kHz 16-bit mono PCM bytes.
     * Returns a valid WAV [File] if a speech turn segment is ready for Whisper inference,
     * or null if accumulation is ongoing.
     */
    @Synchronized
    fun processPcmChunk(pcmData: ByteArray): File? {
        if (pcmData.isEmpty()) return null

        val rms = calculateRms(pcmData)
        val isVoicePresent = rms >= vadThresholdRms

        if (isVoicePresent) {
            isSpeechActive = true
            silenceFrameCount = 0
            speechBuffer.write(pcmData)
        } else if (isSpeechActive) {
            // During speech hangover
            silenceFrameCount++
            speechBuffer.write(pcmData)

            if (silenceFrameCount >= silenceHangoverFrames) {
                // Speech pause reached
                return flushIfSufficient()
            }
        }

        // Check if maximum segment length reached during continuous speech
        if (speechBuffer.size() >= maxSegmentBytes) {
            return flushIfSufficient()
        }

        return null
    }

    /**
     * Forces flushing any accumulated audio into a WAV file.
     */
    @Synchronized
    fun flush(): File? {
        return flushIfSufficient(ignoreMinLength = true)
    }

    private fun flushIfSufficient(ignoreMinLength: Boolean = false): File? {
        val size = speechBuffer.size()
        if (size == 0) return null

        if (!ignoreMinLength && size < minTurnBytes) {
            // Discard too short noise/click
            speechBuffer.reset()
            isSpeechActive = false
            silenceFrameCount = 0
            return null
        }

        val rawPcm = speechBuffer.toByteArray()
        speechBuffer.reset()
        isSpeechActive = false
        silenceFrameCount = 0

        return writeWavFile(rawPcm)
    }

    /**
     * Writes raw 16kHz 16-bit mono PCM bytes into a 44-byte RIFF WAV file.
     */
    fun writeWavFile(pcmBytes: ByteArray, sampleRate: Int = 16000, channels: Int = 1): File {
        fileCounter++
        val wavFile = File(tempDir, "whisper_${System.currentTimeMillis()}_${fileCounter}.wav")
        val totalAudioLen = pcmBytes.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * 2).toLong()

        FileOutputStream(wavFile).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

            // RIFF chunk descriptor
            header.put("RIFF".toByteArray())
            header.putInt(totalDataLen.toInt())
            header.put("WAVE".toByteArray())

            // "fmt " sub-chunk
            header.put("fmt ".toByteArray())
            header.putInt(16) // Subchunk1Size for PCM
            header.putShort(1.toShort()) // AudioFormat 1 = PCM
            header.putShort(channels.toShort()) // NumChannels
            header.putInt(sampleRate) // SampleRate
            header.putInt(byteRate.toInt()) // ByteRate
            header.putShort((channels * 2).toShort()) // BlockAlign
            header.putShort(16.toShort()) // BitsPerSample

            // "data" sub-chunk
            header.put("data".toByteArray())
            header.putInt(totalAudioLen.toInt())

            out.write(header.array())
            out.write(pcmBytes)
            out.flush()
        }

        return wavFile
    }

    /**
     * Calculates Root Mean Square (RMS) energy for 16-bit PCM bytes.
     */
    fun calculateRms(pcmBytes: ByteArray): Double {
        if (pcmBytes.isEmpty()) return 0.0
        var sumSquares = 0.0
        val sampleCount = pcmBytes.size / 2
        val bb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            val sample = bb.short.toDouble()
            sumSquares += sample * sample
        }
        return sqrt(sumSquares / sampleCount)
    }

    /**
     * Cleans up temporary audio files in the tempDir.
     */
    fun cleanupTempFiles() {
        try {
            tempDir.listFiles { _, name -> name.startsWith("whisper_") && name.endsWith(".wav") }
                ?.forEach { it.delete() }
        } catch (_: Exception) {}
    }
}
