package com.susagi.edgeai

/**
 * Standard structured threat analysis result compliant with Arm Bharat AI-SoC Challenge PS3.
 */
data class InferenceResult(
    val threatType: String,
    val riskScore: Int,
    val severity: String,
    val detectedTactics: List<String>,
    val evidence: List<String>,
    val explanation: String,
    val recommendedActions: List<String>,
    val analysisSource: String, // "LLAMA_EXECUTORCH", "LOCAL_CLASSIFIER", "LOCAL_RULES", "HEURISTIC_FALLBACK", "CLOUD_SERVICE", "UNAVAILABLE"
    val inferenceLatencyMs: Long = 0L,
    val tokensPerSecond: Float = 0f
) {
    val isDangerous: Boolean get() = riskScore >= 70
    val isSuspicious: Boolean get() = riskScore in 40..69
    val isClean: Boolean get() = riskScore < 40

    fun toDiagnosticSummary(): String {
        return "[$analysisSource] Score: $riskScore ($severity), Type: $threatType, Tactics: ${detectedTactics.joinToString()}, Latency: ${inferenceLatencyMs}ms"
    }
}

object AnalysisSources {
    const val LLAMA_EXECUTORCH = "LLAMA_EXECUTORCH"
    const val LOCAL_CLASSIFIER = "LOCAL_CLASSIFIER"
    const val LOCAL_RULES = "LOCAL_RULES"
    const val LOCAL_HEURISTIC = "LOCAL_HEURISTIC"
    const val HEURISTIC_FALLBACK = "HEURISTIC_FALLBACK"
    const val CLOUD_SERVICE = "CLOUD_SERVICE"
    const val UNAVAILABLE = "UNAVAILABLE"
}

enum class ThreatSeverity {
    LOW,
    MODERATE,
    HIGH,
    CRITICAL,
    UNKNOWN
}

enum class ThreatChannel {
    URL,
    MESSAGE,
    VOIP_CALL,
    NETWORK
}

data class ThreatInput(
    val channel: ThreatChannel,
    val rawContent: String,
    val structuredEvidence: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)
