package com.susagi.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Stateful PCM16 Resampler and Format Normalizer.
 *
 * Converts arbitrary sample rates (8kHz, 16kHz, 32kHz, 44.1kHz, 48kHz) and channel configurations
 * (mono, stereo) to normalized 16000 Hz, 16-bit, 1-channel, Little-Endian Signed PCM.
 *
 * Preserves fractional interpolation state across consecutive audio frames to prevent
 * boundary clicks, discontinuities, or artificial silence.
 */
class PcmResampler(
    val targetSampleRate: Int = 16000
) {
    private var lastSample: Double = 0.0
    private var hasLastSample: Boolean = false
    private var phase: Double = 0.0

    /**
     * Resets the interpolation phase and history between audio sessions.
     */
    @Synchronized
    fun reset() {
        lastSample = 0.0
        hasLastSample = false
        phase = 0.0
    }

    /**
     * Resamples raw byte data from an input format to normalized 16kHz mono PCM16 bytes.
     */
    @Synchronized
    fun resample(
        inputBytes: ByteArray,
        offset: Int = 0,
        length: Int = inputBytes.size,
        inputSampleRate: Int,
        inputChannels: Int = 1
    ): ByteArray {
        if (length <= 0) return ByteArray(0)
        val safeRate = if (inputSampleRate > 0) inputSampleRate else targetSampleRate
        val safeChannels = if (inputChannels in 1..8) inputChannels else 1

        // 1. Convert input bytes to ShortArray (handling endianness and stereo-to-mono)
        val monoShorts = bytesToMonoShorts(inputBytes, offset, length, safeChannels)
        if (monoShorts.isEmpty()) return ByteArray(0)

        // 2. If input rate matches target rate, bypass interpolation
        if (safeRate == targetSampleRate) {
            hasLastSample = true
            lastSample = monoShorts.last().toDouble()
            return monoShortsToBytes(monoShorts)
        }

        // 3. Stateful Linear Interpolation Resampling
        val resampledShorts = resampleShorts(monoShorts, safeRate, targetSampleRate)
        return monoShortsToBytes(resampledShorts)
    }

    /**
     * Resamples ShortArray audio using stateful linear interpolation.
     */
    @Synchronized
    fun resampleShorts(
        input: ShortArray,
        inRate: Int,
        outRate: Int = targetSampleRate
    ): ShortArray {
        if (input.isEmpty()) return ShortArray(0)
        if (inRate <= 0 || outRate <= 0 || inRate == outRate) {
            hasLastSample = true
            lastSample = input.last().toDouble()
            return input
        }

        val step = inRate.toDouble() / outRate.toDouble()
        if (step <= 0.0001) return input

        val estimatedOutCount = ((input.size - phase) / step).toInt().coerceAtLeast(0) + 2
        val output = ArrayList<Short>(estimatedOutCount)

        var currentPhase = phase
        var iterations = 0
        val maxIterations = (estimatedOutCount * 4) + 200

        while (true) {
            if (++iterations > maxIterations) break

            val idx = floor(currentPhase).toInt()
            val frac = currentPhase - idx

            val s0: Double
            val s1: Double

            if (idx < 0) {
                s0 = if (hasLastSample) lastSample else input[0].toDouble()
                s1 = input[0].toDouble()
            } else if (idx < input.size - 1) {
                s0 = input[idx].toDouble()
                s1 = input[idx + 1].toDouble()
            } else if (idx == input.size - 1) {
                s0 = input[idx].toDouble()
                // At the boundary, extrapolate linearly or hold
                s1 = s0
            } else {
                // Beyond current frame, save remaining phase relative to input.size and break
                break
            }

            val interpolated = s0 + frac * (s1 - s0)
            val clamped = interpolated.roundToInt().coerceIn(-32768, 32767).toShort()
            output.add(clamped)

            currentPhase += step
        }

        // Save phase carried forward into next frame
        phase = currentPhase - input.size
        lastSample = input.last().toDouble()
        hasLastSample = true

        val result = ShortArray(output.size)
        for (i in output.indices) {
            result[i] = output[i]
        }
        return result
    }

    /**
     * Converts Little-Endian PCM16 bytes to a mono ShortArray.
     * Downmixes stereo channels using (L + R) / 2 with integer overflow clamping.
     */
    private fun bytesToMonoShorts(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        channels: Int
    ): ShortArray {
        val bytesPerSample = 2
        val bytesPerFrame = bytesPerSample * channels
        val frameCount = length / bytesPerFrame
        if (frameCount <= 0) return ShortArray(0)

        val shorts = ShortArray(frameCount)
        val buf = ByteBuffer.wrap(bytes, offset, length).order(ByteOrder.LITTLE_ENDIAN)

        if (channels == 1) {
            for (i in 0 until frameCount) {
                shorts[i] = buf.short
            }
        } else {
            // Stereo downmixing to mono
            for (i in 0 until frameCount) {
                val left = buf.short.toInt()
                val right = buf.short.toInt()
                // Skip additional channels if multichannel (> 2)
                for (c in 2 until channels) {
                    buf.short
                }
                val mixed = (left + right) / 2
                shorts[i] = mixed.coerceIn(-32768, 32767).toShort()
            }
        }
        return shorts
    }

    /**
     * Encodes a mono ShortArray into Little-Endian PCM16 bytes.
     */
    fun monoShortsToBytes(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (s in shorts) {
            buf.putShort(s)
        }
        return bytes
    }
}
