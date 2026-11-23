package com.susagi.intelligence

/**
 * DedicatedSecurityFinding
 *
 * Structured threat intelligence output produced by Astra's dedicated lightweight
 * on-device cybersecurity model.
 *
 * Delivers fine-grained, evidence-linked attribution rather than uncalibrated probabilities.
 */
data class DedicatedSecurityFinding(
    val threatCategory: String,
    val requestedAction: String,
    val claimedAuthority: String,
    val coercionIndicators: List<String>,
    val credentialRequest: Boolean,
    val paymentRequest: Boolean,
    val urlIndicators: List<String>,
    val warningOrQuotedContext: Boolean,
    val modelScore: Int,
    val confidenceStatus: String, // "CONFIRMED_HIGH", "CAUTION_REVIEW", "BENIGN_NORMAL", "EDUCATIONAL_ADVISORY"
    val evidence: List<String>
)
