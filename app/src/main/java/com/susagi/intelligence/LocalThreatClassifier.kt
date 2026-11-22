package com.susagi.intelligence

import com.susagi.safety.Assessment
import com.susagi.safety.Risk
import com.susagi.safety.SafetyBarrier
import com.susagi.safety.Signal
import java.io.File
import java.security.MessageDigest
import kotlin.math.exp

/**
 * Lifecycle states for on-device dataset-trained ML models.
 */
enum class ClassifierLifecycle {
    NOT_PROVISIONED,
    INVALID_ASSET,
    READY,
    FAILED
}

/**
 * Structured output from on-device ML threat classification.
 */
data class ClassificationResult(
    val category: String,
    val calibratedScore: Int,
    val isMalicious: Boolean,
    val confidence: Float,
    val detectedSignals: List<String>,
    val modelVersion: String,
    val lifecycle: ClassifierLifecycle,
    val statusSummary: String,
    val heuristicFallbackActive: Boolean = false
)

/**
 * Model specifications and weights definition for Astra dataset-trained classifier.
 */
data class ThreatModelSpec(
    val version: String,
    val expectedSizeBytes: Long,
    val expectedSha256: String,
    val vocabularySize: Int = 1024,
    val decisionThreshold: Float = 0.50f,
    val featureNgramRange: IntRange = 3..4
)

/**
 * On-Device Dataset-Trained Threat Classifier for SuSagi Guardian V2.
 * Provides transparent lifecycle management (NOT_PROVISIONED -> READY / FAILED),
 * bounded feature extraction (character n-grams hashed into compact vector),
 * offline inference, and graceful fallback to deterministic SafetyBarrier rules.
 */
class LocalThreatClassifier(
    private val modelDirectory: File? = null,
    private val spec: ThreatModelSpec = ThreatModelSpec(
        version = "astra-scam-v1-arm",
        expectedSizeBytes = 4096L,
        expectedSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" // Sample placeholder
    )
) {
    private var weights: FloatArray? = null
    private var bias: Float = -1.2f // Baseline neutral prior
    private var lifecycleState: ClassifierLifecycle = ClassifierLifecycle.NOT_PROVISIONED
    private var failureReason: String? = null

    init {
        loadModelIfAvailable()
    }

    fun getLifecycle(): ClassifierLifecycle = lifecycleState
    fun getFailureReason(): String? = failureReason

    /**
     * Attempts to verify and load model weights from private storage.
     */
    @Synchronized
    fun loadModelIfAvailable() {
        val dir = modelDirectory
        if (dir == null || !dir.exists()) {
            lifecycleState = ClassifierLifecycle.NOT_PROVISIONED
            return
        }

        val modelFile = File(dir, "threat_classifier_${spec.version}.bin")
        if (!modelFile.exists()) {
            lifecycleState = ClassifierLifecycle.NOT_PROVISIONED
            return
        }

        try {
            // 1. Verify size
            if (modelFile.length() != spec.expectedSizeBytes) {
                lifecycleState = ClassifierLifecycle.INVALID_ASSET
                failureReason = "Asset size mismatch: expected ${spec.expectedSizeBytes} bytes, found ${modelFile.length()} bytes"
                return
            }

            // 2. Verify SHA-256
            val actualSha = computeSha256(modelFile)
            if (!actualSha.equals(spec.expectedSha256, ignoreCase = true)) {
                lifecycleState = ClassifierLifecycle.INVALID_ASSET
                failureReason = "Asset SHA-256 checksum mismatch: expected ${spec.expectedSha256}, found $actualSha"
                return
            }

            // 3. Load feature weights (compact floating point array)
            val bytes = modelFile.readBytes()
            val floatCount = bytes.size / 4
            val loadedWeights = FloatArray(floatCount)
            val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until floatCount) {
                loadedWeights[i] = buffer.float
            }
            this.weights = loadedWeights
            lifecycleState = ClassifierLifecycle.READY
            failureReason = null
        } catch (e: Exception) {
            lifecycleState = ClassifierLifecycle.FAILED
            failureReason = "Error loading model weights: ${e.message}"
        }
    }

    /**
     * In-memory loader for golden unit test vectors and calibrated weights.
     */
    fun loadFromMemory(calibratedWeights: FloatArray, modelBias: Float) {
        this.weights = calibratedWeights
        this.bias = modelBias
        this.lifecycleState = ClassifierLifecycle.READY
        this.failureReason = null
    }

    /**
     * Performs on-device classification on raw text or URL.
     * If model is not provisioned, transparently combines SafetyBarrier deterministic verdict.
     */
    suspend fun classify(text: String, barrierAssessment: Assessment? = null): ClassificationResult {
        val trimmed = text.take(32768).trim()
        val assessment = barrierAssessment ?: SafetyBarrier.analyze(null, trimmed)

        // Path A: ML Model Ready -> Evaluate features and calculate calibrated probability
        val currentWeights = weights
        if (lifecycleState == ClassifierLifecycle.READY && currentWeights != null) {
            val features = extractHashedNgrams(trimmed, spec.vocabularySize, spec.featureNgramRange)
            var logit = bias
            for ((index, value) in features) {
                if (index in currentWeights.indices) {
                    logit += currentWeights[index] * value
                }
            }
            val probability = sigmoid(logit)
            val isMalicious = probability >= spec.decisionThreshold || assessment.risk == Risk.HIGH
            val calibratedScore = (probability * 100).toInt().coerceIn(0, 100)

            val signals = mutableListOf<String>()
            if (probability >= spec.decisionThreshold) {
                signals.add("ml_pattern_anomaly: ${(probability * 100).toInt()}% confidence")
            }
            assessment.evidence.forEach { signals.add(it.id.category) }

            val category = when {
                assessment.risk == Risk.HIGH -> assessment.evidence.firstOrNull()?.id?.category ?: "high_threat"
                isMalicious -> "dataset_anomaly"
                assessment.risk == Risk.REVIEW -> "review_required"
                else -> "benign"
            }

            return ClassificationResult(
                category = category,
                calibratedScore = maxOf(calibratedScore, if (assessment.risk == Risk.HIGH) 85 else 0),
                isMalicious = isMalicious,
                confidence = probability,
                detectedSignals = signals.distinct(),
                modelVersion = spec.version,
                lifecycle = ClassifierLifecycle.READY,
                statusSummary = "ML Classification active (${spec.version})",
                heuristicFallbackActive = false
            )
        }

        // Path B: Model Not Provisioned / Invalid -> Transparently surface deterministic fallback
        val fallbackScore = when (assessment.risk) {
            Risk.HIGH -> 90
            Risk.REVIEW, Risk.UNKNOWN -> 45
            Risk.LOW -> 5
        }
        val isThreat = assessment.risk == Risk.HIGH || assessment.risk == Risk.REVIEW
        val signals = assessment.evidence.map { it.id.category }.distinct()

        return ClassificationResult(
            category = if (isThreat) assessment.evidence.firstOrNull()?.id?.category ?: "threat_detected" else "benign",
            calibratedScore = fallbackScore,
            isMalicious = assessment.risk == Risk.HIGH,
            confidence = if (assessment.risk == Risk.HIGH) 0.95f else 0.50f,
            detectedSignals = signals,
            modelVersion = "none",
            lifecycle = lifecycleState,
            statusSummary = when (lifecycleState) {
                ClassifierLifecycle.NOT_PROVISIONED -> "Classifier not provisioned — deterministic SafetyBarrier active"
                ClassifierLifecycle.INVALID_ASSET -> "Classifier asset verification failed: ${failureReason ?: "corrupted"}"
                ClassifierLifecycle.FAILED -> "Classifier engine error: ${failureReason ?: "runtime failure"}"
                ClassifierLifecycle.READY -> "Ready"
            },
            heuristicFallbackActive = true
        )
    }

    /**
     * Bounded, memory-safe character n-gram feature hashing (Murmur-like 32-bit hash).
     */
    fun extractHashedNgrams(input: String, vocabSize: Int, ngramRange: IntRange): Map<Int, Float> {
        val normalized = input.lowercase()
        val counts = mutableMapOf<Int, Int>()
        var total = 0

        for (n in ngramRange) {
            if (normalized.length < n) continue
            for (i in 0..normalized.length - n) {
                val ngram = normalized.substring(i, i + n)
                val hash = (ngram.hashCode() and 0x7FFFFFFF) % vocabSize
                counts[hash] = (counts[hash] ?: 0) + 1
                total++
            }
        }

        if (total == 0) return emptyMap()
        // Term frequency normalization
        return counts.mapValues { (_, count) -> count.toFloat() / total }
    }

    private fun sigmoid(x: Float): Float {
        return (1.0 / (1.0 + exp(-x.toDouble()))).toFloat()
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
