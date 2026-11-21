package com.guardian.app.bhashini

import com.guardian.app.voip.CallDiagnostics
import java.util.ArrayDeque

class AudioRingBufferQueue(private val maxSamples: Int = 160_000) { // ~10 seconds at 16kHz
    private val buffer = ArrayDeque<Short>()
    private var droppedSampleCount = 0L

    @Synchronized
    fun push(samples: ShortArray) {
        for (sample in samples) {
            if (buffer.size >= maxSamples) {
                buffer.pollFirst()
                droppedSampleCount++
            }
            buffer.addLast(sample)
        }
        if (droppedSampleCount > 0 && droppedSampleCount % 16_000 == 0L) {
            CallDiagnostics.log("STT", "AudioRingBuffer: Dropped $droppedSampleCount samples while disconnected")
        }
    }

    @Synchronized
    fun popAll(): ShortArray {
        val result = ShortArray(buffer.size)
        var idx = 0
        while (!buffer.isEmpty()) {
            val sample = buffer.pollFirst()
            if (sample != null) {
                result[idx++] = sample
            }
        }
        return result.copyOf(idx)
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        droppedSampleCount = 0L
    }

    val size: Int
        @Synchronized get() = buffer.size
}
