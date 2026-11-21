package com.susagi.audio

import android.util.Log
import com.guardian.app.voip.CallDiagnostics
import com.guardian.app.voip.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * AudioFramePipeline
 *
 * Normalizes, resamples, and repacketizes PCM audio into exact 100ms chunks (3,200 bytes @ 16kHz mono PCM16).
 * Safe, bounded, and non-blocking when called from real-time audio threads.
 */
class AudioFramePipeline(
    val label: String,                               // "local" or "remote"
    private val chunkBytes: Int = 3200,              // 100ms @ 16kHz / 16-bit / mono
    queueCapacityChunks: Int = 30,                   // ~3 seconds max queue depth
    private val onChunkReady: suspend (ByteArray) -> Unit
) {
    private val resampler = PcmResampler(targetSampleRate = 16000)
    private val queue = ArrayBlockingQueue<ByteArray>(queueCapacityChunks)
    private val accumulator = ByteArrayOutputStream(chunkBytes * 2)
    private val running = AtomicBoolean(false)
    private var senderJob: Job? = null
    private var statsJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile var droppedChunks: Long = 0; private set
    @Volatile var sentChunks: Long = 0; private set
    @Volatile var receivedFramesCount: Long = 0; private set
    @Volatile var invalidFramesCount: Long = 0; private set
    @Volatile var lastSendError: String? = null; private set
    @Volatile var latestRms: Double = 0.0; private set

    private var firstFrameLogged = false
    private var zeroRmsConsecutiveSeconds = 0

    fun recordInvalidFrame() {
        invalidFramesCount++
    }

    fun start() {
        if (running.getAndSet(true)) return
        firstFrameLogged = false
        receivedFramesCount = 0
        sentChunks = 0
        droppedChunks = 0
        invalidFramesCount = 0
        zeroRmsConsecutiveSeconds = 0
        resampler.reset()
        synchronized(accumulator) { accumulator.reset() }
        queue.clear()

        senderJob = scope.launch {
            while (isActive && running.get()) {
                val chunk = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                try {
                    val rms = calculateRms(chunk)
                    latestRms = rms
                    onChunkReady(chunk)
                    sentChunks++
                    lastSendError = null
                } catch (e: Exception) {
                    lastSendError = e.message
                    Log.w("AudioFramePipeline[$label]", "Audio chunk delivery error: ${e.message}", e)
                }
            }
        }

        statsJob = scope.launch {
            var lastCount = 0L
            while (isActive && running.get()) {
                delay(5000)
                val currentCount = receivedFramesCount
                val diff = currentCount - lastCount
                lastCount = currentCount
                CallDiagnostics.log(
                    "PCM",
                    "[$label] 5s rolling count: $diff frames (Total: $currentCount, Sent Chunks: $sentChunks, Dropped: $droppedChunks, RMS: ${String.format("%.1f", latestRms)})"
                )

                if (latestRms < 1.0) {
                    zeroRmsConsecutiveSeconds += 5
                    if (zeroRmsConsecutiveSeconds >= 15) {
                        CallDiagnostics.log("PCM", "[$label] Persistent silent PCM detected (RMS ~0 for ${zeroRmsConsecutiveSeconds}s)", LogLevel.WARN)
                    }
                } else {
                    zeroRmsConsecutiveSeconds = 0
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        senderJob?.cancel()
        statsJob?.cancel()
        synchronized(accumulator) { accumulator.reset() }
        queue.clear()
        resampler.reset()
    }

    /**
     * Ingest raw PCM data from audio callback.
     * Thread-safe, non-blocking. Performs normalization, resampling, and 100ms slicing.
     */
    fun onRawPcm(
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size,
        sampleRate: Int = 16000,
        channels: Int = 1
    ) {
        if (!running.get() || length <= 0) return
        if (sampleRate !in 8000..48000 || channels !in 1..2) {
            invalidFramesCount++
            return
        }
        receivedFramesCount++

        if (!firstFrameLogged) {
            firstFrameLogged = true
            CallDiagnostics.log("PCM", "[$label] First frame received: length=$length bytes, rate=$sampleRate Hz, ch=$channels")
        }

        // 1. Resample & downmix to 16kHz mono PCM16 bytes
        val normalizedBytes = resampler.resample(data, offset, length, sampleRate, channels)
        if (normalizedBytes.isEmpty()) return

        // 2. Accumulate and carve into 100ms chunks (3,200 bytes)
        synchronized(accumulator) {
            accumulator.write(normalizedBytes)
            val buf = accumulator.toByteArray()
            var start = 0
            while (buf.size - start >= chunkBytes) {
                val chunk = buf.copyOfRange(start, start + chunkBytes)
                start += chunkBytes

                if (!queue.offer(chunk)) {
                    // Queue full under network congestion: drop oldest chunk to maintain real-time posture
                    queue.poll()
                    queue.offer(chunk)
                    droppedChunks++
                }
            }
            val remainder = buf.copyOfRange(start, buf.size)
            accumulator.reset()
            accumulator.write(remainder)
        }
    }

    /**
     * Convenience method for ShortArray input.
     */
    fun onRawPcmShorts(shorts: ShortArray, sampleRate: Int = 16000, channels: Int = 1) {
        val bytes = resampler.monoShortsToBytes(shorts)
        onRawPcm(bytes, 0, bytes.size, sampleRate, channels)
    }

    private fun calculateRms(pcmBytes: ByteArray): Double {
        val shortBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var sumSquares = 0.0
        val count = shortBuffer.remaining()
        if (count == 0) return 0.0
        while (shortBuffer.hasRemaining()) {
            val sample = shortBuffer.get().toDouble()
            sumSquares += sample * sample
        }
        return sqrt(sumSquares / count)
    }
}
