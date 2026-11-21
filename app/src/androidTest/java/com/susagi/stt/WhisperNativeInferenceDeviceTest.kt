package com.susagi.stt

import android.test.InstrumentationTestCase
import com.susagi.edgeai.ModelManifest
import com.susagi.stt.whisper.WhisperAudioProcessor
import com.susagi.stt.whisper.WhisperModelManager
import com.susagi.stt.whisper.WhisperSttEngine
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Strict Arm64 Android on-device acceptance test for real ExecuTorch Whisper ASR.
 * Validates speech asset integrity, native AsrModule loading, and end-to-end transcription on physical hardware.
 */
class WhisperNativeInferenceDeviceTest : InstrumentationTestCase() {

    private fun verifyWhisperAssets(): WhisperModelManager {
        val context = instrumentation.targetContext
        val manager = WhisperModelManager(context)
        val spec = ModelManifest.WHISPER_SMALL_ARM

        val modelFile = manager.modelFile
        val preprocessorFile = manager.preprocessorFile
        val tokenizerFile = manager.tokenizerFile

        assertTrue(
            "Whisper model file must exist at ${modelFile.absolutePath}",
            modelFile.exists() && modelFile.isFile && modelFile.length() > 30_000_000L
        )
        assertTrue(
            "Whisper preprocessor file must exist at ${preprocessorFile.absolutePath}",
            preprocessorFile.exists() && preprocessorFile.isFile
        )
        assertTrue(
            "Whisper tokenizer file must exist at ${tokenizerFile.absolutePath}",
            tokenizerFile.exists() && tokenizerFile.isFile
        )

        fun computeSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(262144)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        // If exact production profile is present, verify SHA-256
        if (modelFile.name == spec.modelFileName) {
            assertEquals("Model size must match ModelManifest", spec.modelSizeBytes, modelFile.length())
            val actualHash = computeSha256(modelFile)
            assertTrue(
                "Model SHA-256 mismatch. Expected: ${spec.modelSha256}, actual: $actualHash",
                actualHash.equals(spec.modelSha256, ignoreCase = true)
            )
        }

        return manager
    }

    /**
     * Verifies that the native ExecuTorch AsrModule initializes and loads on hardware.
     */
    @Test
    fun testWhisperNativeSmoke_loadAndInitialize() {
        val manager = verifyWhisperAssets()
        val context = instrumentation.targetContext
        val engine = WhisperSttEngine(context, manager)

        val loaded = engine.load()
        assertTrue("Whisper AsrModule must load successfully with zero error codes", loaded)
        assertTrue("Whisper engine must report ready", engine.isReady)
    }

    /**
     * Verifies on-device transcription of a 16kHz mono audio chunk through native ExecuTorch.
     */
    @Test
    fun testWhisperNativeInference_transcribeAudio() {
        val manager = verifyWhisperAssets()
        val context = instrumentation.targetContext
        val engine = WhisperSttEngine(context, manager)
        val loaded = engine.load()
        assertTrue("Whisper engine load failed", loaded)

        val processor = WhisperAudioProcessor(manager.tempAudioDir)
        // Synthesize 1.6s of 16kHz mono 16-bit PCM audio (~51,200 bytes)
        val sampleCount = 25600
        val pcmBytes = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            // 440 Hz test tone
            val sample = (Math.sin(2.0 * Math.PI * 440.0 * i / 16000.0) * 16000).toInt().toShort()
            pcmBytes[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcmBytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        val wavFile = processor.processPcmChunk(pcmBytes) ?: processor.flush()
        assertNotNull("Processor must generate a valid WAV segment", wavFile)

        try {
            runBlocking {
                val result = engine.transcribeWav(wavFile!!, language = "en")
                android.util.Log.i(
                    "WhisperDeviceTest",
                    "Native Whisper output: text='${result.text}', latency=${result.latencyMs}ms, RTF=${result.realTimeFactor}x"
                )
                assertTrue(
                    "Whisper native execution must report success: ${result.errorMessage}",
                    result.isSuccess
                )
            }
        } finally {
            wavFile?.delete()
            processor.cleanupTempFiles()
        }
    }
}
