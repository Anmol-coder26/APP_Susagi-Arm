package com.susagi.risk

/**
 * RiskResult
 *
 * Immutable, structured result of real-time semantic analysis performed by Gemini Cloud AI
 * or on-device heuristic fallback during live phone calls.
 */
data class RiskResult(
    val riskScore: Int,                                 // 0 to 100
    val victimRole: String = "none",                   // "local", "remote", or "none"
    val scammerRole: String = "none",                  // "local", "remote", or "none"
    val roleConfidence: Double = 0.0,                  // 0.0 to 1.0
    val tactics: List<String> = emptyList(),           // Identified tactics
    val explanation: String = "",                      // Human-readable rationale
    val source: String = "GEMINI_CLOUD",               // "GEMINI_CLOUD" or "LOCAL_HEURISTIC"
    val isFallback: Boolean = false,
    val failureReason: String = "",
    val analyzedAtMs: Long = System.currentTimeMillis()
) {
    val isScamDetected: Boolean get() = riskScore >= 60
    val isSuspicious: Boolean get() = riskScore in 30..59
    val isSafe: Boolean get() = riskScore < 30
}
