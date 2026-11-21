package com.susagi.safety

import android.test.InstrumentationTestCase
import com.susagi.edgeai.ModelManifest
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Strict Arm64 Android on-device acceptance test for real ExecuTorch Llama 3.2 1B native inference.
 * Verifies model file integrity and validates that the native runtime produces genuine output.
 */
class LlamaNativeInferenceDeviceTest : InstrumentationTestCase() {

    @Test
    fun testLlamaNativeInference_realModel_generatesOtp() {
        val context = instrumentation.targetContext
        val modelsDir = File(context.filesDir, "models")
        val spec = ModelManifest.LLAMA_3_2_1B_SPINQUANT

        // 1. Check both model files inside context.filesDir/models
        val modelFile = File(modelsDir, spec.modelFileName)
        val tokenizerFile = File(modelsDir, spec.tokenizerFileName)

        assertTrue(
            "Model file must exist at ${modelFile.absolutePath}",
            modelFile.exists() && modelFile.isFile
        )
        assertTrue(
            "Tokenizer file must exist at ${tokenizerFile.absolutePath}",
            tokenizerFile.exists() && tokenizerFile.isFile
        )

        // 2. Validate file sizes and SHA-256 digests using ModelManifest
        assertEquals(
            "Model file size must match ModelManifest.LLAMA_3_2_1B_SPINQUANT (${spec.modelSizeBytes} bytes)",
            spec.modelSizeBytes,
            modelFile.length()
        )
        assertEquals(
            "Tokenizer file size must match ModelManifest.LLAMA_3_2_1B_SPINQUANT (${spec.tokenizerSizeBytes} bytes)",
            spec.tokenizerSizeBytes,
            tokenizerFile.length()
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

        val actualModelSha256 = computeSha256(modelFile)
        assertTrue(
            "Model SHA-256 mismatch. Expected: ${spec.modelSha256}, actual: $actualModelSha256",
            actualModelSha256.equals(spec.modelSha256, ignoreCase = true)
        )

        val actualTokenizerSha256 = computeSha256(tokenizerFile)
        assertTrue(
            "Tokenizer SHA-256 mismatch. Expected: ${spec.tokenizerSha256}, actual: $actualTokenizerSha256",
            actualTokenizerSha256.equals(spec.tokenizerSha256, ignoreCase = true)
        )

        val explainer = LocalEvidenceExplainer(modelsDir)
        try {
            runBlocking {
                // 3. Execute SafetyBarrier.analyzeMessage("Send me your OTP")
                val assessment = SafetyBarrier.analyzeMessage("Send me your OTP")
                assertTrue(
                    "SafetyBarrier assessment must identify Signal.OTP",
                    assessment.evidence.any { it.id == Signal.OTP }
                )

                // 4. Pass the resulting assessment to LocalEvidenceExplainer(File(context.filesDir, "models")).explain(...)
                val result = explainer.explain(assessment)

                // 5. Require the actual Generated result
                assertTrue(
                    "ExplanationResult must be Generated, but was: $result",
                    result is ExplanationResult.Generated
                )
                val generated = result as ExplanationResult.Generated

                // 6. Inspect the raw Generated.nativeOutput, not fallback guidance
                val rawOutput = generated.nativeOutput
                android.util.Log.i("LlamaNativeInferenceDeviceTest", "Raw native Llama output: '$rawOutput'")

                // 7. Assert that nativeOutput.trim() equals OTP
                assertEquals(
                    "Raw native model output must strictly equal 'OTP'. Received: '$rawOutput'",
                    "OTP",
                    rawOutput.trim()
                )
            }
        } finally {
            // 8. Call release() in a finally block
            runBlocking {
                explainer.release()
            }
        }
    }
}
