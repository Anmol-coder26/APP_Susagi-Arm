package com.susagi.stt.whisper

import com.susagi.stt.DualSttController
import com.susagi.stt.SttEngineMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WhisperModuleTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testTempDir: File

    @Before
    fun setup() {
        testTempDir = tempFolder.newFolder("whisper_test")
    }

    @Test
    fun testWhisperDecoder_stripsSpecialTokensAndTimestamps() {
        val rawInput = "<|startoftranscript|><|hi|><|transcribe|><|notimestamps|>यह एक डिजिटल अरेस्ट फ्रॉड कॉल है<|endoftext|>"
        val decoded = WhisperDecoder.decode(rawInput)
        assertEquals("यह एक डिजिटल अरेस्ट फ्रॉड कॉल है", decoded)
    }

    @Test
    fun testWhisperDecoder_stripsTimestampTokens() {
        val rawInput = "<|0.00|>Hello Inspector<|1.50|> please stay on the video call <|3.20|><|endoftext|>"
        val decoded = WhisperDecoder.decode(rawInput)
        assertEquals("Hello Inspector please stay on the video call", decoded)
    }

    @Test
    fun testWhisperDecoder_languageTokens() {
        assertEquals("<|hi|>", WhisperDecoder.getLanguageToken("hi"))
        assertEquals("<|en|>", WhisperDecoder.getLanguageToken("en"))
        assertEquals("<|ta|>", WhisperDecoder.getLanguageToken("ta"))
        assertEquals("<|en|>", WhisperDecoder.getLanguageToken("unknown"))
    }

    @Test
    fun testWhisperDecoder_suppressesRepetitions() {
        val repetitive = "transfer transfer transfer transfer transfer transfer money now"
        val cleaned = WhisperDecoder.decode(repetitive)
        assertFalse(cleaned.contains("transfer transfer transfer transfer"))
        assertTrue(cleaned.contains("money now"))
    }

    @Test
    fun testAudioProcessor_calculatesRmsAccurately() {
        val processor = WhisperAudioProcessor(tempDir = testTempDir)

        // Silent audio: all zeroes
        val silence = ByteArray(3200) // 100ms
        val silenceRms = processor.calculateRms(silence)
        assertEquals(0.0, silenceRms, 0.001)

        // Synthetic tone audio: alternating amplitude 1000
        val bb = ByteBuffer.allocate(3200).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 1600) {
            bb.putShort(if (i % 2 == 0) 1000.toShort() else (-1000).toShort())
        }
        val toneRms = processor.calculateRms(bb.array())
        assertEquals(1000.0, toneRms, 1.0)
    }

    @Test
    fun testAudioProcessor_writesValidRiffWavHeader() {
        val processor = WhisperAudioProcessor(tempDir = testTempDir)

        // Create 1 second of 16kHz 16-bit mono PCM (32,000 bytes)
        val pcm = ByteArray(32_000) { 1 }
        val wavFile = processor.writeWavFile(pcm, sampleRate = 16000, channels = 1)

        assertTrue(wavFile.exists())
        assertEquals(32_000L + 44L, wavFile.length())

        val bytes = wavFile.readBytes()
        val riff = String(bytes, 0, 4)
        val wave = String(bytes, 8, 4)
        val fmt = String(bytes, 12, 4)
        val data = String(bytes, 36, 4)

        assertEquals("RIFF", riff)
        assertEquals("WAVE", wave)
        assertEquals("fmt ", fmt)
        assertEquals("data", data)

        // Check sample rate at offset 24
        val sampleRate = ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(16000, sampleRate)

        // Check channels at offset 22
        val channels = ByteBuffer.wrap(bytes, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(1.toShort(), channels)

        // Check bits per sample at offset 34
        val bitsPerSample = ByteBuffer.wrap(bytes, 34, 2).order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(16.toShort(), bitsPerSample)

        wavFile.delete()
    }

    @Test
    fun testDualSttController_modeSwitching() {
        val defaultController = DualSttController(
            language = "hi",
            endpointUrl = "https://dhruva-api.bhashini.gov.in/services/inference/pipeline"
        )
        assertEquals(SttEngineMode.BHASHINI_ONLINE, defaultController.engineMode.value)
        defaultController.close()

        val controller = DualSttController(
            language = "hi",
            endpointUrl = "https://dhruva-api.bhashini.gov.in/services/inference/pipeline",
            initialEngineMode = SttEngineMode.AUTO_HYBRID
        )

        assertEquals(SttEngineMode.AUTO_HYBRID, controller.engineMode.value)

        controller.setEngineMode(SttEngineMode.WHISPER_OFFLINE)
        assertEquals(SttEngineMode.WHISPER_OFFLINE, controller.engineMode.value)
        assertTrue(controller.activeEngineLabel.value.contains("Whisper"))

        controller.setEngineMode(SttEngineMode.BHASHINI_ONLINE)
        assertEquals(SttEngineMode.BHASHINI_ONLINE, controller.engineMode.value)
        assertTrue(controller.activeEngineLabel.value.contains("Bhashini"))

        controller.close()
    }
}
