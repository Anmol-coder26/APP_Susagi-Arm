package com.susagi.stt

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Bounded ring-buffer audio queue for holding up to 10 seconds of PCM audio
 * during transient STT network disconnects.
 * 16,000 Hz * 16 bits (2 bytes) = 32,000 bytes/sec -> 320,000 bytes for 10s.
 */
class AudioRingBufferQueue(
    private val maxCapacityBytes: Int = 320_000
) {
    private val buffer = ByteArray(maxCapacityBytes)
    private var head = 0
    private var tail = 0
    private var size = 0

    @Synchronized
    fun push(samples: ShortArray) {
        val byteBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) byteBuffer.putShort(s)
        val bytes = byteBuffer.array()
        pushBytes(bytes)
    }

    @Synchronized
    fun pushBytes(bytes: ByteArray) {
        for (b in bytes) {
            if (size == maxCapacityBytes) {
                // Drop oldest byte
                head = (head + 1) % maxCapacityBytes
                size--
            }
            buffer[tail] = b
            tail = (tail + 1) % maxCapacityBytes
            size++
        }
    }

    @Synchronized
    fun drain(): ByteArray {
        if (size == 0) return ByteArray(0)
        val out = ByteArray(size)
        for (i in 0 until size) {
            out[i] = buffer[(head + i) % maxCapacityBytes]
        }
        head = 0
        tail = 0
        size = 0
        return out
    }

    @Synchronized
    fun clear() {
        head = 0
        tail = 0
        size = 0
    }

    val currentSizeBytes: Int @Synchronized get() = size
}
