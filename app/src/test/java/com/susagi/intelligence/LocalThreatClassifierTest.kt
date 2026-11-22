package com.susagi.intelligence

import com.susagi.safety.Risk
import com.susagi.safety.SafetyBarrier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalThreatClassifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testLifecycle_notProvisioned_returnsTransparentDeterministicFallback() = runBlocking {
        val emptyDir = tempFolder.newFolder("empty_models")
        val classifier = LocalThreatClassifier(emptyDir)

        assertEquals(ClassifierLifecycle.NOT_PROVISIONED, classifier.getLifecycle())

        val result = classifier.classify("Police warrant against you. Transfer fifty thousand now to avoid arrest.")
        assertTrue("Heuristic fallback must be active", result.heuristicFallbackActive)
        assertEquals(ClassifierLifecycle.NOT_PROVISIONED, result.lifecycle)
        assertTrue("Status summary must state deterministic fallback active",
            result.statusSummary.contains("deterministic SafetyBarrier active"))
        assertTrue("Must detect extortion threat from SafetyBarrier", result.isMalicious)
        assertTrue("Score must be >= 85", result.calibratedScore >= 85)

        val benignResult = classifier.classify("Hi Dad, reaching home around 7 PM.")
        assertFalse("Benign message must not be malicious", benignResult.isMalicious)
        assertEquals(5, benignResult.calibratedScore)
    }

    @Test
    fun testLifecycle_corruptedAssetSize_flagsInvalidAsset() {
        val modelDir = tempFolder.newFolder("corrupted_models")
        val spec = ThreatModelSpec(
            version = "test-v1",
            expectedSizeBytes = 100L,
            expectedSha256 = "dummy_sha"
        )
        val file = File(modelDir, "threat_classifier_test-v1.bin")
        file.writeBytes(ByteArray(50)) // size is 50, expected 100

        val classifier = LocalThreatClassifier(modelDir, spec)
        assertEquals(ClassifierLifecycle.INVALID_ASSET, classifier.getLifecycle())
        assertTrue("Failure reason must explain size mismatch",
            classifier.getFailureReason()?.contains("size mismatch") == true)
    }

    @Test
    fun testLifecycle_corruptedAssetHash_flagsInvalidAsset() {
        val modelDir = tempFolder.newFolder("tampered_models")
        val spec = ThreatModelSpec(
            version = "test-v2",
            expectedSizeBytes = 16L,
            expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
        )
        val file = File(modelDir, "threat_classifier_test-v2.bin")
        file.writeBytes(ByteArray(16) { 0xFF.toByte() })

        val classifier = LocalThreatClassifier(modelDir, spec)
        assertEquals(ClassifierLifecycle.INVALID_ASSET, classifier.getLifecycle())
        assertTrue("Failure reason must explain checksum mismatch",
            classifier.getFailureReason()?.contains("checksum mismatch") == true)
    }

    @Test
    fun testInMemoryInference_parityAndClassification() = runBlocking {
        val classifier = LocalThreatClassifier()
        val vocabSize = 1024
        val weights = FloatArray(vocabSize)

        // Train a synthetic weight for "otp" and "pin"
        val ngrams = classifier.extractHashedNgrams("enter upi pin otp", vocabSize, 3..4)
        for ((idx, _) in ngrams) {
            weights[idx] = 4.0f
        }

        classifier.loadFromMemory(weights, modelBias = -1.0f)
        assertEquals(ClassifierLifecycle.READY, classifier.getLifecycle())

        val maliciousResult = classifier.classify("Enter your UPI PIN right now")
        assertEquals(ClassifierLifecycle.READY, maliciousResult.lifecycle)
        assertFalse("Fallback should be false when ML model is ready", maliciousResult.heuristicFallbackActive)
        assertTrue("Calibrated score should be elevated", maliciousResult.calibratedScore >= 50)
        assertTrue("Should detect malicious threat", maliciousResult.isMalicious)

        val benignResult = classifier.classify("The weather is nice today")
        assertFalse("Benign text should have low score", benignResult.isMalicious)
        assertTrue("Confidence should be low for neutral text", benignResult.confidence < 0.50f)
    }

    @Test
    fun testFeatureExtraction_boundedPerformance() {
        val classifier = LocalThreatClassifier()
        val emptyFeatures = classifier.extractHashedNgrams("", 512, 3..4)
        assertTrue(emptyFeatures.isEmpty())

        val largeText = "A".repeat(32768)
        val features = classifier.extractHashedNgrams(largeText, 512, 3..4)
        assertNotNull(features)
        assertTrue(features.values.all { it in 0.0f..1.0f })
    }
}
