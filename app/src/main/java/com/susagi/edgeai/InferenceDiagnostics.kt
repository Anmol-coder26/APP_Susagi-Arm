package com.susagi.edgeai

/**
 * Diagnostics and telemetry for on-device Arm ExecuTorch + Llama execution.
 */
data class InferenceDiagnostics(
    val engineName: String = "ExecuTorch-Llama",
    val modelArchitecture: String = "Llama-3.2-1B-Instruct-Q4",
    val targetHardware: String = "Arm64 CPU (XNNPACK)",
    val modelSizeBytes: Long = 0L,
    val isModelLoaded: Boolean = false,
    val initializationLatencyMs: Long = 0L,
    val lastInferenceLatencyMs: Long = 0L,
    val timeToFirstTokenMs: Long = 0L,
    val tokensPerSecond: Float = 0f,
    val peakMemoryUsageMb: Long = 0L,
    val totalInferencesExecuted: Long = 0L,
    val lastError: String? = null
)
