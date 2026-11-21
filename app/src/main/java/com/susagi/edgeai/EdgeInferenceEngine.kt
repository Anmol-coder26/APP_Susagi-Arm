package com.susagi.edgeai

/**
 * Common edge inference interface for on-device Arm cybersecurity intelligence.
 */
interface EdgeInferenceEngine {
    val name: String
    val isReady: Boolean

    suspend fun analyzeThreat(input: ThreatInput): InferenceResult
    fun getDiagnostics(): InferenceDiagnostics
    fun close()
}
