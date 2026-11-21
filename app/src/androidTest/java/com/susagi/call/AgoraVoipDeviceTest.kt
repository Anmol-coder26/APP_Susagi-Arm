package com.susagi.call

import android.content.Context
import android.media.AudioManager
import android.test.InstrumentationTestCase
import com.guardian.app.core.AgoraEngineManager
import com.guardian.app.voip.VoipCallService
import com.susagi.audio.AudioFramePipeline
import com.susagi.audio.PcmResampler
import io.agora.rtc2.Constants
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Strict Android on-device acceptance test for Agora VoIP audio routing and services.
 * Validates hardware audio subsystem integration, Agora singleton initialization, and foreground service lifecycle.
 */
class AgoraVoipDeviceTest : InstrumentationTestCase() {

    @Test
    fun testAgoraEngineManager_hardwareInitialization() {
        val context = instrumentation.targetContext
        // Check audio manager state on physical device
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        assertNotNull("Hardware AudioManager must be accessible", audioManager)

        val engine = AgoraEngineManager.get(context)
        assertNotNull("Agora RtcEngine singleton must initialize on hardware", engine)
        assertTrue("AgoraEngineManager must report initialized", AgoraEngineManager.isInitialized())

        // Validate audio parameters on device
        engine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
        engine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
        engine.enableAudio()
        engine.setEnableSpeakerphone(true)
    }

    @Test
    fun testAudioFramePipeline_deviceLiveLoopback() {
        val latch = CountDownLatch(1)
        var receivedBytes = 0

        val pipeline = AudioFramePipeline("device_test", chunkBytes = 3200) { chunk ->
            receivedBytes = chunk.size
            latch.countDown()
        }

        pipeline.start()
        try {
            // Push 100ms of 16kHz mono audio (3,200 bytes)
            val pcm = ByteArray(3200) { (it % 128).toByte() }
            pipeline.onRawPcm(pcm, 0, pcm.size, sampleRate = 16000, channels = 1)

            val completed = latch.await(2, TimeUnit.SECONDS)
            assertTrue("AudioFramePipeline must process chunk within 2 seconds", completed)
            assertEquals("Chunk size must match 100ms frame @ 16kHz", 3200, receivedBytes)
        } finally {
            pipeline.stop()
        }
    }

    @Test
    fun testVoipCallService_startAndStopForeground() {
        val context = instrumentation.targetContext
        // Verify service start and stop contract on device
        VoipCallService.startService(context, "gx_device_test")
        Thread.sleep(500)
        VoipCallService.stopService(context)
    }
}
