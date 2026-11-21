package com.susagi.audio

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class AudioFramePipelineTest {

    @Test
    fun testAccumulationAndChunking_producesExact3200ByteChunks() = runBlocking {
        val chunks = CopyOnWriteArrayList<ByteArray>()
        val pipeline = AudioFramePipeline(
            label = "test",
            chunkBytes = 3200,
            queueCapacityChunks = 10
        ) { chunk ->
            chunks.add(chunk)
        }

        pipeline.start()

        // Push 5 frames of 20ms (640 bytes each) = 3200 bytes (exactly one 100ms chunk)
        val frame20ms = ByteArray(640) { 1 }
        repeat(5) {
            pipeline.onRawPcm(frame20ms, sampleRate = 16000, channels = 1)
        }

        // Allow coroutine to dispatch
        delay(150)

        assertEquals("Expected exactly 1 chunk of 3200 bytes", 1, chunks.size)
        assertEquals(3200, chunks[0].size)

        pipeline.stop()
    }

    @Test
    fun testOversizedFrame_splitsIntoMultipleChunks() = runBlocking {
        val chunks = CopyOnWriteArrayList<ByteArray>()
        val pipeline = AudioFramePipeline(
            label = "test",
            chunkBytes = 3200,
            queueCapacityChunks = 10
        ) { chunk ->
            chunks.add(chunk)
        }

        pipeline.start()

        // Push 250ms of audio = 8000 bytes -> 2 full chunks (6400 bytes) + 1600 bytes remainder
        val frame250ms = ByteArray(8000) { 1 }
        pipeline.onRawPcm(frame250ms, sampleRate = 16000, channels = 1)

        delay(150)

        assertEquals("Expected 2 chunks of 3200 bytes from 8000 bytes input", 2, chunks.size)
        assertEquals(3200, chunks[0].size)
        assertEquals(3200, chunks[1].size)

        // Now push another 1600 bytes -> 1600 remainder + 1600 new = 3200 -> 3rd chunk!
        val frame50ms = ByteArray(1600) { 1 }
        pipeline.onRawPcm(frame50ms, sampleRate = 16000, channels = 1)

        delay(150)
        assertEquals("Expected 3 chunks after remainder is filled", 3, chunks.size)

        pipeline.stop()
    }

    @Test
    fun testQueueOverflow_dropsOldestChunkSafely() = runBlocking {
        // Pipeline with tiny queue of 2 chunks, and no consumer draining it
        val pipeline = AudioFramePipeline(
            label = "test-drop",
            chunkBytes = 3200,
            queueCapacityChunks = 2
        ) {
            // Simulate slow consumer by holding
            delay(5000)
        }

        pipeline.start()

        // Push 5 full chunks rapidly
        val chunk3200 = ByteArray(3200) { 1 }
        repeat(5) {
            pipeline.onRawPcm(chunk3200, sampleRate = 16000, channels = 1)
        }

        delay(100)

        // Since capacity is 2, earlier chunks should be dropped to preserve real-time posture
        assertTrue("Dropped chunks should be > 0 under overflow", pipeline.droppedChunks > 0)

        pipeline.stop()
    }
}
