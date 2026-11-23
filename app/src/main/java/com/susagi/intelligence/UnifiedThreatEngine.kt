package com.susagi.intelligence

import android.content.Context
import com.susagi.safety.Assessment
import com.susagi.safety.Risk
import com.susagi.safety.SafetyBarrier
import android.util.Log
import com.guardian.app.LinkCheckActivity
import com.guardian.app.RiskStatus
import com.susagi.edgeai.AnalysisSources
import com.susagi.edgeai.EdgeInferenceEngine
import com.susagi.edgeai.ExecuTorchLlamaEngine
import com.susagi.edgeai.InferenceDiagnostics
import com.susagi.edgeai.InferenceResult
import com.susagi.edgeai.ThreatChannel
import com.susagi.edgeai.ThreatInput
import com.susagi.risk.GeminiRoleClient
import com.susagi.risk.RiskResult

/**
 * Unified Cybersecurity Intelligence Engine for SuSagi Guardian 3.0.
 * Orchestrates threat detection across URLs, Messages, VoIP Calls, and Network signals
 * using on-device Llama + ExecuTorch on Arm hardware, with cloud Gemini enhancement when online.
 */
class UnifiedThreatEngine(
    private val context: Context? = null,
    val llamaEngine: EdgeInferenceEngine = ExecuTorchLlamaEngine(context),
    private val geminiClient: GeminiRoleClient? = null,
    val networkClassifier: com.susagi.network.NetworkAnomalyClassifier = com.susagi.network.NetworkAnomalyClassifier()
) {
    companion object {
        private const val TAG = "UnifiedThreatEngine"

        @Volatile
        private var instance: UnifiedThreatEngine? = null

        fun getInstance(context: Context): UnifiedThreatEngine {
            return instance ?: synchronized(this) {
                instance ?: UnifiedThreatEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Link Shield: Analyzes a target URL for phishing, homoglyphs, malicious APK downloads, and brand impersonation.
     */
    suspend fun analyzeUrl(url: String): InferenceResult {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            return InferenceResult(
                threatType = "empty_input",
                riskScore = 0,
                severity = "LOW",
                detectedTactics = emptyList(),
                evidence = emptyList(),
                explanation = "No URL provided for analysis.",
                recommendedActions = listOf("Provide a valid web address."),
                analysisSource = AnalysisSources.LOCAL_RULES
            )
        }

        // 1. Extract deterministic structured evidence using LinkCheck heuristic analyzer
        val verdict = LinkCheckActivity.evaluateLinkSafety(trimmed)
        val evidenceList = mutableListOf<String>()
        if (verdict.status != RiskStatus.Low) {
            evidenceList.add("${verdict.title}: ${verdict.detail}")
        }
        val host = LinkCheckActivity.extractHost(trimmed)
        if (host.isNotBlank()) {
            evidenceList.add("Host: $host")
        }

        val input = ThreatInput(
            channel = ThreatChannel.URL,
            rawContent = trimmed,
            structuredEvidence = evidenceList,
            metadata = mapOf("host" to host, "initial_status" to verdict.status.name)
        )

        // 2. Run through Edge AI Engine (Llama + ExecuTorch)
        val result = llamaEngine.analyzeThreat(input)

        // Ensure LinkCheck high-risk detections always enforce high score
        val finalScore = if (verdict.status == RiskStatus.High && result.riskScore < 70) 85 else result.riskScore
        val finalSeverity = if (finalScore >= 70) "HIGH" else if (finalScore >= 40) "MEDIUM" else "LOW"

        return result.copy(
            riskScore = finalScore,
            severity = finalSeverity,
            evidence = evidenceList.ifEmpty { result.evidence }
        ).withBarrier(SafetyBarrier.analyzeUrl(trimmed))
    }

    /**
     * Message Shield: Analyzes SMS, shared texts, and notification messages for KYC fraud,
     * OTP theft, utility extortion, and digital arrest threats.
     */
    suspend fun analyzeMessage(message: String, sender: String = ""): InferenceResult {
        val trimmed = message.trim()
        if (trimmed.isBlank()) {
            return InferenceResult(
                threatType = "empty_input",
                riskScore = 0,
                severity = "LOW",
                detectedTactics = emptyList(),
                evidence = emptyList(),
                explanation = "No message content provided.",
                recommendedActions = emptyList(),
                analysisSource = AnalysisSources.LOCAL_RULES
            )
        }

        val assessment = SafetyBarrier.analyzeMessage(trimmed)
        val evidenceList = assessment.evidence.map { it.id.explanation }

        val input = ThreatInput(
            channel = ThreatChannel.MESSAGE,
            rawContent = trimmed,
            structuredEvidence = evidenceList,
            metadata = mapOf("sender" to sender)
        )

        // 2. On-Device Llama inference
        return llamaEngine.analyzeThreat(input).withBarrier(assessment)
    }

    private fun InferenceResult.withBarrier(assessment: Assessment): InferenceResult {
        if (assessment.evidence.isEmpty()) return this
        val floor = when (assessment.risk) {
            Risk.HIGH -> 85
            Risk.UNKNOWN, Risk.REVIEW -> 40
            Risk.LOW -> 0
        }
        val score = maxOf(riskScore, floor)
        val raised = score > riskScore
        return copy(
            threatType = if (raised && !isDangerous) assessment.evidence.first { it.id.risk == assessment.risk }.id.category else threatType,
            riskScore = score,
            severity = if (score >= 70) "HIGH" else if (score >= 40) "MEDIUM" else "LOW",
            detectedTactics = (detectedTactics + assessment.evidence.map { it.id.name.lowercase() }).distinct(),
            evidence = (evidence + assessment.evidence.map { it.id.explanation }).distinct(),
            explanation = if (raised) assessment.explanation else explanation,
            recommendedActions = if (raised && isClean) assessment.recommendations
                else (assessment.recommendations + recommendedActions).distinct()
        )
    }

    /**
     * Call Shield: Analyzes real-time VoIP transcript buffer for psychological coercion and digital arrest.
     */
    suspend fun analyzeCallTranscript(transcript: String): InferenceResult {
        val trimmed = transcript.trim()
        if (trimmed.isBlank()) {
            return InferenceResult(
                threatType = "quiet_channel",
                riskScore = 0,
                severity = "LOW",
                detectedTactics = emptyList(),
                evidence = emptyList(),
                explanation = "No speech detected in call window.",
                recommendedActions = emptyList(),
                analysisSource = "INSUFFICIENT_DATA"
            )
        }

        val evidenceList = mutableListOf<String>()
        val lower = trimmed.lowercase()
        if (lower.contains("cyber") || lower.contains("police") || lower.contains("crime") || lower.contains("cbi") || lower.contains("पुलिस")) {
            evidenceList.add("Impersonation of Police/CBI/Cybercrime Bureau")
        }
        if (lower.contains("transfer") || lower.contains("rupees") || lower.contains("account") || lower.contains("money") || lower.contains("पैसे")) {
            evidenceList.add("Demand for Financial Transfer/Verification")
        }
        if (lower.contains("immediately") || lower.contains("disconnect") || lower.contains("urgent") || lower.contains("तुरंत")) {
            evidenceList.add("Urgency & Anti-Disconnection Pressure")
        }
        if (lower.contains("secret") || lower.contains("don't tell") || lower.contains("confidential") || lower.contains("किसी को मत बताना")) {
            evidenceList.add("Forced Isolation / Secrecy Requirement")
        }
        if (lower.contains("lottery") || lower.contains("lotery") || lower.contains("lucky draw") || lower.contains("jackpot") || lower.contains("prize") || lower.contains("लॉटरी") || lower.contains("इनाम") || (lower.contains("won") && (lower.contains("10 k") || lower.contains("10k")))) {
            evidenceList.add("Unsolicited Lottery / Prize Solicitation Indicator")
        }

        val barrierAssessment = SafetyBarrier.analyzeMessage(trimmed)
        val input = ThreatInput(
            channel = ThreatChannel.VOIP_CALL,
            rawContent = trimmed,
            structuredEvidence = if (evidenceList.isNotEmpty()) evidenceList else barrierAssessment.evidence.map { it.id.explanation }
        )

        if (barrierAssessment.evidence.any { it.id.category in setOf("unverified_prize", "incomplete_prize") }) {
            val high = barrierAssessment.risk == Risk.HIGH
            return InferenceResult(
                threatType = barrierAssessment.evidence.first { it.id.risk == barrierAssessment.risk }.id.category,
                riskScore = if (high) 85 else 40,
                severity = if (high) "HIGH" else "MEDIUM",
                detectedTactics = barrierAssessment.evidence.map { it.id.name },
                evidence = barrierAssessment.evidence.map { it.id.explanation },
                explanation = barrierAssessment.explanation,
                recommendedActions = barrierAssessment.recommendations,
                analysisSource = AnalysisSources.LOCAL_RULES
            )
        }

        return llamaEngine.analyzeThreat(input).withBarrier(barrierAssessment)
    }

    /**
     * Network Shield: Analyzes network flow telemetry for port scanning, C2 beaconing, and connection spikes.
     */
    suspend fun analyzeNetworkBatch(flows: List<com.susagi.network.NetworkFlowRecord>): InferenceResult {
        if (flows.isEmpty()) {
            return InferenceResult(
                threatType = "empty_telemetry",
                riskScore = 0,
                severity = "LOW",
                detectedTactics = emptyList(),
                evidence = emptyList(),
                explanation = "No network telemetry flows provided for analysis.",
                recommendedActions = emptyList(),
                analysisSource = AnalysisSources.LOCAL_RULES
            )
        }

        val verdict = networkClassifier.classifyFlowBatch(flows)
        val evidenceList = verdict.indicators.toMutableList()

        val input = ThreatInput(
            channel = ThreatChannel.NETWORK,
            rawContent = verdict.explanation,
            structuredEvidence = evidenceList,
            metadata = mapOf(
                "anomaly_type" to verdict.anomalyType.name,
                "flow_count" to flows.size.toString(),
                "initial_score" to verdict.riskScore.toString()
            )
        )

        val result = llamaEngine.analyzeThreat(input)
        val finalScore = if (verdict.isAnomalous && result.riskScore < verdict.riskScore) verdict.riskScore else result.riskScore
        val finalSeverity = when {
            finalScore >= 80 -> "HIGH"
            finalScore >= 50 -> "MEDIUM"
            else -> "LOW"
        }

        return result.copy(
            riskScore = finalScore,
            severity = finalSeverity,
            evidence = evidenceList.ifEmpty { result.evidence }
        )
    }

    /**
     * Analyzes a single network flow record in isolation.
     */
    suspend fun analyzeNetworkSingle(flow: com.susagi.network.NetworkFlowRecord): InferenceResult {
        return analyzeNetworkBatch(listOf(flow))
    }

    /**
     * Unified Inspection Pipeline:
     * Input -> Type Detection -> Normalization -> Security Inspection -> Structured Evidence -> Authoritative Verdict -> Actionable Guidance
     */
    suspend fun inspectGenericInput(rawInput: String, sender: String = ""): GenericThreatAssessment {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            return GenericThreatAssessment(
                detectedType = DetectedInputType.PLAIN_MESSAGE,
                normalizedInput = "",
                riskScore = 0,
                severity = "LOW",
                threatCategory = "Empty Input",
                explanation = "No input provided for inspection.",
                evidence = emptyList(),
                detectedTactics = emptyList(),
                recommendedActions = listOf("Provide message text, URL, or UPI payload.")
            )
        }

        val lower = trimmed.lowercase()

        // 1. UPI Payload Detection
        if (lower.startsWith("upi://")) {
            val upi = com.susagi.safety.PaymentFields.inspectUri(trimmed)
            val isHigh = !upi.isValid || upi.isAdvanceFeeOrReverseDebit
            val score = if (!upi.isValid) 90 else if (upi.isAdvanceFeeOrReverseDebit) 95 else 40
            val sev = if (isHigh) "HIGH" else "MEDIUM"
            val cat = when {
                !upi.isValid -> "Invalid / Tampered UPI URI"
                upi.isAdvanceFeeOrReverseDebit -> "Reverse Debit / Refund Trap"
                else -> "Unverified UPI Destination"
            }
            val explanation = upi.riskReason ?: if (isHigh) "Fraudulent payment collect or refund trap." else "Valid UPI format. Verify recipient independently."
            val ev = mutableListOf<String>()
            upi.payeeVpa?.let { ev.add("Payee VPA: $it") }
            upi.amount?.let { ev.add("Amount: ₹$it") }
            upi.riskReason?.let { ev.add(it) }

            val actions = if (isHigh) listOf("Do NOT approve payment or enter UPI PIN.", "Never approve collect requests to receive refunds.")
                          else listOf("Verify payee identity before confirming in your UPI app.", "Never enter UPI PIN to receive money.")

            return GenericThreatAssessment(
                detectedType = DetectedInputType.UPI_URI,
                normalizedInput = trimmed,
                riskScore = score,
                severity = sev,
                threatCategory = cat,
                explanation = explanation,
                evidence = ev,
                detectedTactics = if (isHigh) listOf("upi_collect_trap", "reverse_debit") else listOf("unverified_upi"),
                recommendedActions = actions,
                upiInspection = upi
            )
        }

        // 2. Single URL vs Multi-URL vs Message
        val extracted = com.susagi.safety.SafetyBarrier.analyze(null, trimmed).destinations
        val extractedUrls = extracted.map { it.originalUrl }
        val isPureSingleUrl = (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) &&
                !trimmed.contains(" ") && extractedUrls.size <= 1

        if (isPureSingleUrl) {
            val res = analyzeUrl(trimmed)
            return GenericThreatAssessment(
                detectedType = DetectedInputType.SINGLE_URL,
                normalizedInput = trimmed,
                riskScore = res.riskScore,
                severity = res.severity,
                threatCategory = res.threatType,
                explanation = res.explanation,
                evidence = res.evidence,
                detectedTactics = res.detectedTactics,
                recommendedActions = res.recommendedActions,
                extractedUrls = listOf(trimmed)
            )
        }

        // Multi-URL or Plain Message
        val msgRes = analyzeMessage(trimmed, sender)
        val inputType = if (extractedUrls.size > 1) DetectedInputType.MULTI_URL_MESSAGE else DetectedInputType.PLAIN_MESSAGE

        return GenericThreatAssessment(
            detectedType = inputType,
            normalizedInput = trimmed,
            riskScore = msgRes.riskScore,
            severity = msgRes.severity,
            threatCategory = msgRes.threatType,
            explanation = msgRes.explanation,
            evidence = msgRes.evidence,
            detectedTactics = msgRes.detectedTactics,
            recommendedActions = msgRes.recommendedActions,
            extractedUrls = extractedUrls
        )
    }

    /**
     * Returns live edge diagnostics for hackathon compliance reporting.
     */
    fun getDiagnostics(): InferenceDiagnostics = llamaEngine.getDiagnostics()
}

enum class DetectedInputType {
    UPI_URI,
    QR_PAYLOAD,
    SINGLE_URL,
    MULTI_URL_MESSAGE,
    PLAIN_MESSAGE
}

data class GenericThreatAssessment(
    val detectedType: DetectedInputType,
    val normalizedInput: String,
    val riskScore: Int,
    val severity: String,
    val threatCategory: String,
    val explanation: String,
    val evidence: List<String>,
    val detectedTactics: List<String>,
    val recommendedActions: List<String>,
    val extractedUrls: List<String> = emptyList(),
    val upiInspection: com.susagi.safety.UpiInspection? = null,
    val qrVerdict: com.guardian.app.QrVerdict? = null
)
