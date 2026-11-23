package com.susagi.intelligence

import java.util.UUID

/**
 * SecurityModelResult
 *
 * Kotlin-accessible contract specified in Directive 37 & 44 for the authoritative
 * on-device cybersecurity model and unified inspection pipeline.
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
    val uncertaintyOrAbstentionStatus: String, // "CONFIRMED_HIGH", "CAUTION_REVIEW", "BENIGN_NORMAL", "EDUCATIONAL_ADVISORY"
    val assessmentId: String = UUID.randomUUID().toString(),
    val inputType: String = "MESSAGE",
    val riskCategory: String = when (uncertaintyOrAbstentionStatus) {
        "CONFIRMED_HIGH" -> "HIGH"
        "CAUTION_REVIEW" -> "REVIEW"
        else -> "LOW"
    },
    val recommendedProtectiveSteps: List<String> = when (uncertaintyOrAbstentionStatus) {
        "CONFIRMED_HIGH" -> listOf(
            "DO NOT transfer funds, share OTPs, or approve UPI collect requests.",
            "Law enforcement and banks never conduct arrests or investigations via video call.",
            "Report fraudulent communications immediately to the 1930 Cybercrime Helpline or cybercrime.gov.in."
        )
        "CAUTION_REVIEW" -> listOf(
            "Verify sender identity directly through official channels before sharing sensitive information.",
            "Never click unverified short links or install unexpected APK files."
        )
        "EDUCATIONAL_ADVISORY" -> listOf(
            "Educational / security research content. No protective action required."
        )
        else -> listOf(
            "No immediate threat detected. Maintain standard digital hygiene."
        )
    },
    val timestampMs: Long = System.currentTimeMillis()
) {
    val detectorVersion: String get() = modelVersion
}

/**
 * SecurityModel
 *
 * Primary entry point for SuSagi Threat Intelligence on-device inference.
 */
object SecurityModel {

    suspend fun analyze(
        text: String,
        context: String? = null,
        inputType: String = "MESSAGE"
    ): SecurityModelResult {
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

        val riskCat = when (finding.confidenceStatus) {
            "CONFIRMED_HIGH" -> "HIGH"
            "CAUTION_REVIEW" -> "REVIEW"
            else -> "LOW"
        }

        val protectiveSteps = when (finding.confidenceStatus) {
            "CONFIRMED_HIGH" -> listOf(
                "DO NOT transfer funds, share OTPs, or approve UPI collect requests.",
                "Law enforcement and banks never conduct arrests or investigations via video call.",
                "Report fraudulent communications immediately to the 1930 Cybercrime Helpline or cybercrime.gov.in."
            )
            "CAUTION_REVIEW" -> listOf(
                "Verify sender identity directly through official channels before sharing sensitive information.",
                "Never click unverified short links or install unexpected APK files."
            )
            "EDUCATIONAL_ADVISORY" -> listOf(
                "Educational / security research content. No protective action required."
            )
            else -> listOf(
                "No immediate threat detected. Maintain standard digital hygiene."
            )
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
            uncertaintyOrAbstentionStatus = finding.confidenceStatus,
            inputType = inputType,
            riskCategory = riskCat,
            recommendedProtectiveSteps = protectiveSteps
        )
    }
}
