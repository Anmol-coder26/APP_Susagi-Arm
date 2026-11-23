package com.susagi.intelligence

/**
 * SecurityModelResult
 *
 * Kotlin-accessible contract specified in Directive 37 Part A2 for the dedicated
 * on-device cybersecurity model.
 */
data class SecurityModelResult(
    val threatCategoryCandidates: List<String>,
    val primaryThreatCategory: String,
    val requestedAction: String,
    val claimedAuthority: String,
    val credentialDemand: Boolean,
    val paymentDemand: Boolean,
    val coercionEvidence: List<String>,
    val quotationOrWarningContext: Boolean,
    val suspiciousUrlIndicators: List<String>,
    val modelRawScore: Int,
    val evidenceIdentifiers: List<String>,
    val modelVersion: String = "v2.1-offline-arm",
    val uncertaintyOrAbstentionStatus: String // "CONFIRMED_HIGH", "CAUTION_REVIEW", "BENIGN_NORMAL", "EDUCATIONAL_ADVISORY"
)

/**
 * SecurityModel
 *
 * Primary entry point for SuSagi Threat Intelligence on-device inference.
 */
object SecurityModel {

    suspend fun analyze(text: String, context: String? = null): SecurityModelResult {
        val finding = AstraDedicatedSecurityClassifier.classify(text)
        val candidates = mutableListOf<String>()
        candidates.add(finding.threatCategory)
        if (finding.coercionIndicators.isNotEmpty()) {
            candidates.add("Coercive Pressure / Social Engineering")
        }
        if (finding.credentialRequest) {
            candidates.add("Credential Harvesting")
        }
        if (finding.paymentRequest) {
            candidates.add("Payment / Fund Transfer Deception")
        }

        return SecurityModelResult(
            threatCategoryCandidates = candidates.distinct(),
            primaryThreatCategory = finding.threatCategory,
            requestedAction = finding.requestedAction,
            claimedAuthority = finding.claimedAuthority,
            credentialDemand = finding.credentialRequest,
            paymentDemand = finding.paymentRequest,
            coercionEvidence = finding.coercionIndicators,
            quotationOrWarningContext = finding.warningOrQuotedContext,
            suspiciousUrlIndicators = finding.urlIndicators,
            modelRawScore = finding.modelScore,
            evidenceIdentifiers = finding.evidence,
            modelVersion = "v2.1-offline-arm",
            uncertaintyOrAbstentionStatus = finding.confidenceStatus
        )
    }
}
