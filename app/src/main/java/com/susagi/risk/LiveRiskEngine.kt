package com.susagi.risk

import com.guardian.app.voip.CallDiagnostics
import com.guardian.app.voip.LogLevel
import com.susagi.audio.AudioSource
import com.susagi.stt.TranscriptLine
import com.susagi.stt.TranscriptMerger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class RoleAssignment(
    val victim: String?,
    val scammer: String?,
    val confidence: Double,
    val topSignals: List<String> = emptyList(),
    val psychologicalPressure: String? = null
)

/**
 * LiveRiskEngine
 *
 * Orchestrates semantic threat analysis over real-time call transcripts.
 * Evaluates dual-speaker conversational context using a 2-second debounced sliding window,
 * maintains 45-60s rolling history, triggers fast-path evaluations on emergency scam keywords,
 * and emits structured [RiskResult] updates.
 */
class LiveRiskEngine(
    private val client: GeminiRoleClient,
    private val merger: TranscriptMerger? = null,
    private val threatEngine: com.susagi.intelligence.UnifiedThreatEngine? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var windowJob: Job? = null
    private val isRunning = AtomicBoolean(false)

    private val _riskResult = MutableStateFlow(
        RiskResult(
            riskScore = 0,
            victimRole = "none",
            scammerRole = "none",
            roleConfidence = 1.0,
            tactics = emptyList(),
            explanation = "Guardian Sentinel is active. Monitoring for coercive threats..."
        )
    )
    val riskResult: StateFlow<RiskResult> = _riskResult.asStateFlow()

    private var lastAnalyzedTranscript: String = ""
    private var maxHistoricalRisk: Int = 0

    @Volatile private var cachedRoleAssignment: RoleAssignment? = null

    init {
        // If a merger is provided, observe its finalized turns
        merger?.let { m ->
            scope.launch {
                m.transcriptUpdates.collect { line ->
                    onTranscriptLine(line)
                }
            }
        }
    }

    fun start() {
        if (isRunning.getAndSet(true)) return
        startSlidingWindowAnalyzer()
        CallDiagnostics.log("RISK", "LiveRiskEngine started with sliding window analyzer")
    }

    private fun startSlidingWindowAnalyzer() {
        windowJob?.cancel()
        windowJob = scope.launch {
            while (isActive && isRunning.get()) {
                delay(2000) // 2-second debounce sliding window

                val currentTranscript = merger?.getRecentTranscriptWindow(windowMs = 45000L) ?: ""
                if (currentTranscript.isNotBlank() && currentTranscript != lastAnalyzedTranscript) {
                    evaluateTranscript(currentTranscript)
                }
            }
        }
    }

    fun onTranscriptLine(line: TranscriptLine) {
        val text = line.text.trim()
        if (text.isBlank()) return

        // Fast-path keyword trigger for high-threat scenarios
        if (line.source == AudioSource.REMOTE && containsUrgentThreatKeywords(text)) {
            CallDiagnostics.log("RISK", "Fast-path threat trigger detected in remote speech: '$text'", LogLevel.WARN)
            val currentTranscript = merger?.getRecentTranscriptWindow(windowMs = 45000L) ?: "[REMOTE]: $text"
            scope.launch {
                evaluateTranscript(currentTranscript)
            }
        }
    }

    /**
     * Backward-compatible window ingestion for legacy callers.
     */
    suspend fun onTranscriptWindow(local: String, remote: String) {
        val sb = StringBuilder()
        if (local.isNotBlank()) sb.append("[LOCAL (You)]: ").append(local).append("\n")
        if (remote.isNotBlank()) sb.append("[REMOTE (Caller)]: ").append(remote)
        val combined = sb.toString().trim()
        if (combined.isNotBlank() && combined != lastAnalyzedTranscript) {
            evaluateTranscript(combined)
        }
    }

    /**
     * Immediate keyword signal injection (e.g. from local rule engine).
     */
    fun onKeywordSignal(score: Int) {
        if (score > maxHistoricalRisk) {
            maxHistoricalRisk = score
            val current = _riskResult.value
            _riskResult.value = current.copy(
                riskScore = maxHistoricalRisk,
                explanation = "Keyword risk signal registered ($score)."
            )
            updateCachedRoleAssignment(_riskResult.value)
        }
    }

    private suspend fun evaluateTranscript(transcript: String) {
        lastAnalyzedTranscript = transcript
        val startTime = System.currentTimeMillis()

        try {
            // Priority 1: Check On-Device Llama Engine if provisioned & ready (Arm Edge AI PS3)
            val onDeviceLlama = threatEngine?.llamaEngine
            var result: RiskResult? = if (onDeviceLlama != null && onDeviceLlama.isReady) {
                CallDiagnostics.log("RISK-EDGE", "Running on-device Llama ExecuTorch inference on call transcript...")
                val llamaInfer = threatEngine.analyzeCallTranscript(transcript)
                if (llamaInfer.analysisSource == "LLAMA_EXECUTORCH") {
                    RiskResult(
                        riskScore = llamaInfer.riskScore,
                        victimRole = "local",
                        scammerRole = if (llamaInfer.riskScore >= 40) "remote" else "none",
                        roleConfidence = 0.92,
                        tactics = llamaInfer.detectedTactics,
                        explanation = llamaInfer.explanation,
                        source = "LLAMA_EXECUTORCH"
                    )
                } else null
            } else null

            // Priority 2: Cloud Gemini Client with automatic heuristic fallback
            if (result == null) {
                result = client.analyzeConversation(transcript)
            }

            val elapsed = System.currentTimeMillis() - startTime

            // Risk score monotonic persistence: risk does not silently drop if a scammer pauses
            val effectiveScore = if (result.riskScore > maxHistoricalRisk) {
                maxHistoricalRisk = result.riskScore
                result.riskScore
            } else if (result.riskScore >= 60) {
                result.riskScore
            } else {
                // If previously flagged high, retain caution posture
                if (maxHistoricalRisk >= 60) (maxHistoricalRisk * 0.9).toInt() else result.riskScore
            }

            val finalResult = result.copy(riskScore = effectiveScore)
            _riskResult.value = finalResult
            updateCachedRoleAssignment(finalResult)

            CallDiagnostics.log(
                "RISK",
                "Analysis complete in ${elapsed}ms [Source: ${finalResult.source}] -> Score: ${finalResult.riskScore}, Scammer: ${finalResult.scammerRole}, Victim: ${finalResult.victimRole}, Confidence: ${finalResult.roleConfidence}"
            )
        } catch (e: Exception) {
            CallDiagnostics.log("RISK", "Evaluation failed: ${e.message}", LogLevel.WARN)
        }
    }

    private fun updateCachedRoleAssignment(result: RiskResult) {
        val pressure = result.tactics.firstOrNull { tactic ->
            tactic.contains("pressure", ignoreCase = true) ||
            tactic.contains("urgency", ignoreCase = true) ||
            tactic.contains("arrest", ignoreCase = true) ||
            tactic.contains("coercion", ignoreCase = true)
        }

        cachedRoleAssignment = RoleAssignment(
            victim = if (result.victimRole != "none") result.victimRole else null,
            scammer = if (result.scammerRole != "none") result.scammerRole else null,
            confidence = result.roleConfidence,
            topSignals = result.tactics,
            psychologicalPressure = pressure
        )
    }

    private fun containsUrgentThreatKeywords(text: String): Boolean {
        val l = text.lowercase()
        return l.contains("digital arrest") || l.contains("arrest warrant") ||
               l.contains("cbi") || l.contains("police") || l.contains("crime branch") ||
               l.contains("गिरफ्तार") || l.contains("वारंट") || l.contains("safe account") ||
               l.contains("transfer") || l.contains("otp")
    }

    fun currentRoleAssignment(): RoleAssignment? = cachedRoleAssignment

    fun currentDisplayedRisk(): Int = _riskResult.value.riskScore

    fun reset() {
        lastAnalyzedTranscript = ""
        maxHistoricalRisk = 0
        cachedRoleAssignment = null
        _riskResult.value = RiskResult(
            riskScore = 0,
            victimRole = "none",
            scammerRole = "none",
            roleConfidence = 1.0,
            explanation = "Monitoring for threats..."
        )
    }

    fun stop() {
        isRunning.set(false)
        windowJob?.cancel()
        reset()
        scope.cancel()
    }
}
