package com.susagi.security

import com.guardian.app.RiskStatus

/**
 * Universal security evaluation decision for web links, URLs, and QR code targets.
 */
data class UrlSecurityDecision(
    val url: String,
    val status: RiskStatus,
    val riskScore: Int,
    val title: String,
    val detail: String,
    val reasons: List<String> = emptyList(),
    val canonicalHost: String = "",
    val isHomoglyphOrPunycode: Boolean = false,
    val isDirectApkDownload: Boolean = false,
    val isIpHost: Boolean = false
) {
    val isHighRisk: Boolean get() = status == RiskStatus.High || riskScore >= 70
    val isSuspicious: Boolean get() = status == RiskStatus.Suspicious || (riskScore in 30..69)
    val isSafe: Boolean get() = status == RiskStatus.Low && riskScore < 30
}

/**
 * Universal security evaluation decision for incoming SMS, chat messages, and shared text.
 */
data class MessageSecurityDecision(
    val rawText: String,
    val sender: String? = null,
    val riskScore: Int,
    val status: RiskStatus,
    val threatCategory: String,
    val matchedKeywords: List<String> = emptyList(),
    val detectedTactics: List<String> = emptyList(),
    val guidance: String,
    val extractedUrls: List<String> = emptyList()
) {
    val isHighRisk: Boolean get() = status == RiskStatus.High || riskScore >= 70
    val isSuspicious: Boolean get() = status == RiskStatus.Suspicious || (riskScore in 30..69)
    val isSafe: Boolean get() = status == RiskStatus.Low && riskScore < 30
}

/**
 * Universal security evaluation decision for live or recorded audio call sessions.
 */
data class CallSecurityDecision(
    val channelOrNumber: String,
    val riskScore: Int,
    val status: RiskStatus,
    val suspectedScammerRole: String? = null,
    val detectedTactics: List<String> = emptyList(),
    val emergencyEscalationRecommended: Boolean = false
)

/**
 * SecurityAssessmentContract
 *
 * Defines the shared, decoupleable contract between the Android UI/Services layer
 * and the scam/threat intelligence engines (owned by Astra).
 *
 * Guarantees that UI components never execute ad-hoc heuristics or bypass
 * deterministic cybersecurity verification.
 */
interface SecurityAssessmentContract {
    /**
     * Evaluates a URL from manual paste, QR scan, deep link, or browser intent.
     */
    suspend fun assessUrl(url: String): UrlSecurityDecision

    /**
     * Evaluates message text from SMS, WhatsApp notification, or clipboard paste.
     */
    suspend fun assessMessage(text: String, sender: String? = null): MessageSecurityDecision

    /**
     * Evaluates live call context from VoIP or cellular speakerphone stream.
     */
    suspend fun assessCall(channelOrNumber: String, transcriptLines: List<String>): CallSecurityDecision
}
