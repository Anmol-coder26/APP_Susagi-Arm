package com.susagi.stt.whisper

/**
 * Diagnostics and performance metrics for on-device Whisper speech recognition.
 */
data class WhisperDiagnostics(
    val isModelLoaded: Boolean = false,
    val modelSizeBytes: Long = 0L,
    val modelFileName: String = "",
    val hasPreprocessor: Boolean = false,
    val hasTokenizer: Boolean = false,
    val lastInferenceLatencyMs: Long = 0L,
    val audioDurationMs: Long = 0L,
    val realTimeFactor: Float = 0f,
    val tokensGenerated: Int = 0,
    val tokensPerSecond: Float = 0f,
    val totalTranscriptions: Long = 0L,
    val activeEngine: String = "ExecuTorch-Whisper-Arm",
    val lastTranscript: String = "",
    val lastError: String? = null
)

data class WhisperInferenceResult(
    val text: String,
    val latencyMs: Long,
    val audioDurationMs: Long,
    val realTimeFactor: Float,
    val tokensGenerated: Int,
    val tokensPerSecond: Float,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)
