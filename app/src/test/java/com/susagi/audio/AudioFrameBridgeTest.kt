package com.susagi.audio

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList

class AudioFrameBridgeTest {

    @Test
    fun testBufferMutationIsolation_queuedBytesRemainUnchanged() = runBlocking {
        val capturedChunks = CopyOnWriteArrayList<ByteArray>()
        val localPipeline = AudioFramePipeline(
            label = "test-local",
            chunkBytes = 3200,
            queueCapacityChunks = 10
        ) { chunk ->
            capturedChunks.add(chunk)
        }
        val remotePipeline = AudioFramePipeline(
            label = "test-remote",
            chunkBytes = 3200,
            queueCapacityChunks = 10
        ) {}

        localPipeline.start()
        val bridge = AudioFrameBridge(localPipeline, remotePipeline)

        // 1. Create a direct ByteBuffer matching Agora 16kHz mono 20ms frame: 320 samples * 1 ch * 2 bytes = 640 bytes
        val frameBytes = 640
        val directBuffer = ByteBuffer.allocateDirect(frameBytes)
        val originalPattern = ByteArray(frameBytes) { i -> ((i * 7 + 3) % 127).toByte() }
        directBuffer.put(originalPattern)
        directBuffer.flip()

        // 2. Exercise production extraction method directly
        val extractedOwned = AudioFrameBridge.extractOwnedPcm(
            buffer = directBuffer,
            samplesPerChannel = 320,
            channels = 1,
            bytesPerSample = 2
        )
        assertNotNull("Extracted PCM must not be null", extractedOwned)
        assertArrayEquals("Extracted PCM must match original bytes", originalPattern, extractedOwned)

        // 3. Deliver 5 frames (total 3200 bytes = 1 complete 100ms chunk) via production onRecordAudioFrame
        repeat(5) {
            directBuffer.clear()
            directBuffer.put(originalPattern)
            directBuffer.flip()

            bridge.onRecordAudioFrame(
                channelId = "test_channel",
                type = 0,
                samplesPerChannel = 320,
                bytesPerSample = 2,
                channels = 1,
                samplesPerSec = 16000,
                buffer = directBuffer,
                renderTimeMs = System.currentTimeMillis(),
                avSyncType = 0
            )

            // CRITICAL TEST: Aggressively mutate the original Agora ByteBuffer immediately after callback
            directBuffer.position(0)
            for (i in 0 until frameBytes) {
                directBuffer.put(0xCC.toByte()) // Corrupt native memory
            }
        }

        // Allow pipeline coroutine to process
        delay(150)

        // 4. Verify that pipeline assembled the chunk without ANY corruption from buffer mutation
        assertEquals("Expected exactly 1 assembled chunk", 1, capturedChunks.size)
        val chunk = capturedChunks[0]
        assertEquals(3200, chunk.size)

        // Check that none of the bytes in the chunk became 0xCC
        var corruptedCount = 0
        for (b in chunk) {
            if (b == 0xCC.toByte()) corruptedCount++
        }
        assertEquals("Queued PCM bytes must be completely immune to post-callback buffer mutations", 0, corruptedCount)

        localPipeline.stop()
    }

    @Test
    fun testPreMixingAntiDuplicationGuard() = runBlocking {
        var remoteFramesReceived = 0
        val localPipeline = AudioFramePipeline("local") {}
        val remotePipeline = AudioFramePipeline("remote") {
            remoteFramesReceived++
        }

        remotePipeline.start()
        val bridge = AudioFrameBridge(localPipeline, remotePipeline)

        val frame = ByteBuffer.allocateDirect(320)
        frame.put(ByteArray(320) { 1 })
        frame.flip()

        // 1. Deliver pre-mixing frame
        val preMixingSuccess = bridge.onPlaybackAudioFrameBeforeMixing(
            channelId = "c",
            uid = 12345,
            type = 0,
            samplesPerChannel = 160,
            bytesPerSample = 2,
            channels = 1,
            samplesPerSec = 16000,
            buffer = frame,
            renderTimeMs = 0L,
            avSyncType = 0,
            is_mute = 0
        )
        assertTrue(preMixingSuccess)

        val countAfterPreMixing = remotePipeline.receivedFramesCount
        assertEquals("Pre-mixing callback must deliver frame", 1, countAfterPreMixing)

        // 2. Deliver playback frame (which would duplicate audio if not guarded)
        frame.position(0)
        bridge.onPlaybackAudioFrame(
            channelId = "c",
            type = 0,
            samplesPerChannel = 160,
            bytesPerSample = 2,
            channels = 1,
            samplesPerSec = 16000,
            buffer = frame,
            renderTimeMs = 0L,
            avSyncType = 0
        )

        // Verify that onPlaybackAudioFrame was IGNORED
        assertEquals("Mixed playback must be suppressed when pre-mixing is active to prevent duplicate transcription",
            countAfterPreMixing,
            remotePipeline.receivedFramesCount
        )

        remotePipeline.stop()
    }

    @Test
    fun testFallbackPlayback_whenNoPreMixing() = runBlocking {
        val localPipeline = AudioFramePipeline("local") {}
        val remotePipeline = AudioFramePipeline("remote") {}
        remotePipeline.start()

        val bridge = AudioFrameBridge(localPipeline, remotePipeline)

        val frame = ByteBuffer.allocateDirect(320)
        frame.put(ByteArray(320) { 1 })
        frame.flip()

        // Without any pre-mixing callback, onPlaybackAudioFrame serves as fallback
        bridge.onPlaybackAudioFrame(
            channelId = "c",
            type = 0,
            samplesPerChannel = 160,
            bytesPerSample = 2,
            channels = 1,
            samplesPerSec = 16000,
            buffer = frame,
            renderTimeMs = 0L,
            avSyncType = 0
        )

        assertEquals("Mixed playback must be accepted when pre-mixing has not occurred", 1, remotePipeline.receivedFramesCount)
        remotePipeline.stop()
    }
}
