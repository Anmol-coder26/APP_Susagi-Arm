package com.susagi.security

import com.guardian.app.KeywordScorer
import com.guardian.app.LinkCheckActivity
import com.guardian.app.RiskStatus
import com.guardian.app.protect.RegionalScamKeywords
import com.susagi.safety.Risk
import com.susagi.safety.SafetyBarrier
import com.susagi.safety.Signal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Universal Security Bridge powered by Astra's native [SafetyBarrier] offline engine.
 *
 * Provides a unified evaluation pipeline integrating Astra's high-speed deterministic regex
 * safety rules with on-device regional language and heuristic scorers.
 */
object AstraSecurityBridge : SecurityAssessmentContract {

    private val supportedLanguages = listOf("hi", "en", "ta", "te", "bn", "mr", "kn", "ml", "pa", "gu")

    override suspend fun assessUrl(url: String): UrlSecurityDecision = withContext(Dispatchers.Default) {
        val astra = SafetyBarrier.analyzeUrl(url)
        val heuristic = LinkCheckActivity.evaluateLinkSafety(url)
        val lowerUrl = url.lowercase().trim()

        val isHomoglyph = astra.evidence.any { it.id == Signal.MIXED || it.id == Signal.BRAND } ||
                heuristic.title.contains("HOMOGLYPH", ignoreCase = true) ||
                heuristic.title.contains("PUNYCODE", ignoreCase = true) ||
                lowerUrl.contains("xn--")
        val isApk = astra.evidence.any { it.id == Signal.APK } ||
                lowerUrl.endsWith(".apk") || lowerUrl.contains(".apk?") ||
                heuristic.title.contains("APK", ignoreCase = true) ||
                heuristic.title.contains("PACKAGE", ignoreCase = true)
        val isIp = astra.evidence.any { it.id == Signal.IP } ||
                heuristic.title.contains("IP HOST", ignoreCase = true) ||
                Regex("""^https?://\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""").containsMatchIn(lowerUrl)

        val isHigh = astra.risk == Risk.HIGH || heuristic.status == RiskStatus.High
        val isReview = astra.risk == Risk.REVIEW || heuristic.status == RiskStatus.Suspicious
        val status = when {
            isHigh -> RiskStatus.High
            isReview -> RiskStatus.Suspicious
            else -> RiskStatus.Low
        }

        val riskScore = when (status) {
            RiskStatus.High -> maxOf(85, heuristic.riskScore)
            RiskStatus.Suspicious -> maxOf(50, heuristic.riskScore)
            RiskStatus.Low -> minOf(10, heuristic.riskScore)
        }

        val reasons = mutableListOf<String>()
        astra.evidence.forEach { evidence ->
            reasons.add("[${evidence.id.name}] ${evidence.id.explanation}")
        }
        if (isHomoglyph && reasons.none { it.contains("Unicode", true) || it.contains("Punycode", true) }) {
            reasons.add("Deceptive Unicode / Punycode mixed-script characters mimicking legitimate domain")
        }
        if (isApk && reasons.none { it.contains("APK", true) }) {
            reasons.add("Direct Android package (.apk) download payload detected")
        }
        if (isIp && reasons.none { it.contains("IP", true) }) {
            reasons.add("Numeric IP host address used instead of verified registered domain")
        }
        if (heuristic.supportingEvidence.isNotEmpty()) {
            heuristic.supportingEvidence.forEach { ev ->
                if (!reasons.contains(ev)) reasons.add(ev)
            }
        }
        if (reasons.isEmpty()) {
            reasons.add(heuristic.detail)
        }

        val host = astra.destinationHost ?: try {
            val uriStr = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
            java.net.URI(uriStr).host ?: ""
        } catch (_: Exception) {
            ""
        }

        UrlSecurityDecision(
            url = url,
            status = status,
            riskScore = riskScore,
            title = if (status == RiskStatus.Low) "🛡️ NO KNOWN LOCAL THREAT INDICATORS DETECTED" else heuristic.title,
            detail = astra.explanation.ifBlank { heuristic.detail },
            reasons = reasons,
            canonicalHost = host,
            isHomoglyphOrPunycode = isHomoglyph,
            isDirectApkDownload = isApk,
            isIpHost = isIp
        )
    }

    override suspend fun assessMessage(text: String, sender: String?): MessageSecurityDecision = withContext(Dispatchers.Default) {
        val astra = SafetyBarrier.analyze(null, text)
        val extractedUrls = astra.destinations.map { it.originalUrl }.ifEmpty {
            val single = LinkCheckActivity.extractUrlFromText(text)
            if (single.isNotBlank()) listOf(single) else emptyList()
        }

        // Fast-path for educational / advisory / technical research content:
        // Never allow harmless technical discussions to trigger false alarms.
        if (com.susagi.intelligence.EducationalContentDetector.isEducationalOrAnalysis(text) && astra.risk == Risk.LOW) {
            return@withContext MessageSecurityDecision(
                rawText = text,
                sender = sender,
                riskScore = 10,
                status = RiskStatus.Low,
                threatCategory = "Educational / Security Advisory",
                matchedKeywords = emptyList(),
                detectedTactics = listOf("educational_advisory"),
                guidance = "This content discusses cybersecurity concepts, analysis, or defensive guidance rather than an active attack.",
                extractedUrls = extractedUrls
            )
        }

        var highestLangScore = 0
        val allMatchedKeywords = mutableListOf<String>()
        val allCategories = mutableListOf<String>()

        for (lang in supportedLanguages) {
            val score = RegionalScamKeywords.score(text, lang)
            if (score > highestLangScore) highestLangScore = score
            val matches = RegionalScamKeywords.match(text, lang)
            matches.forEach { allMatchedKeywords.add(it.phrase) }
            allCategories.addAll(RegionalScamKeywords.categories(text, lang))
        }

        // KeywordScorer offline evaluation
        val semanticReport = KeywordScorer.score(text)
        val combinedScore = maxOf(highestLangScore, semanticReport.riskScore)
        val semanticSignals: List<String> = semanticReport.topSignals.map { it.title }

        val astraSignals = astra.evidence.map { it.id.name }
        val isAstraHigh = astra.risk == Risk.HIGH
        val isAstraReview = astra.risk == Risk.REVIEW

        val finalScore = when {
            isAstraHigh -> maxOf(combinedScore, 85)
            isAstraReview -> maxOf(combinedScore, 50)
            combinedScore >= 70 -> combinedScore
            combinedScore >= 35 -> combinedScore
            else -> minOf(combinedScore, 10)
        }

        val status = when {
            finalScore >= 70 || isAstraHigh -> RiskStatus.High
            finalScore >= 35 || isAstraReview -> RiskStatus.Suspicious
            else -> RiskStatus.Low
        }

        val lowerText = text.lowercase()
        val threatCategory = when {
            astra.evidence.any { it.id == Signal.ARREST } ||
                    allCategories.any { it.contains("digital_arrest", ignoreCase = true) } ||
                    lowerText.contains("digital arrest") ||
                    lowerText.contains("डिजिटल अरेस्ट") ||
                    lowerText.contains("गिरफ्तार") ||
                    (lowerText.contains("सीबीआई") && lowerText.contains("नोटिस")) ||
                    allCategories.any { it.contains("authority", ignoreCase = true) } -> "Digital Arrest Coercion"
            astra.evidence.any { it.id == Signal.UTILITY } ||
                    allCategories.any { it.contains("electricity", ignoreCase = true) } ||
                    lowerText.contains("electricity") ||
                    lowerText.contains("power cut") -> "Utility / Electricity Bill Disconnection Fraud"
            astra.evidence.any { it.id == Signal.KYC } ||
                    allCategories.any { it.contains("lottery", ignoreCase = true) } ||
                    lowerText.contains("lottery") ||
                    lowerText.contains("kyc") -> "KYC / Account Block Extortion"
            astra.evidence.any { it.id == Signal.GUARANTEED_RETURN } -> "High-Yield Investment / Ponzi Scam"
            astra.evidence.any { it.id == Signal.ADVANCE_FEE || it.id == Signal.REFUND_FEE } -> "Advance Fee / Payout Release Fraud"
            astra.evidence.any { it.id == Signal.UPI } -> "UPI PIN / Payment Collect Trap"
            astra.evidence.any { it.id == Signal.OTP } -> "OTP / Credential Harvesting Fraud"
            astra.evidence.any { it.id == Signal.APK } || extractedUrls.any { it.endsWith(".apk") } -> "Malicious APK Sideload Trap"
            finalScore >= 60 -> "Financial / Impersonation Scam"
            finalScore >= 35 -> "Unverified High-Pressure Request"
            else -> "No Known Scam Pattern"
        }

        val guidance = when (status) {
            RiskStatus.High -> "DO NOT transfer funds, share OTPs, or install APK files. Indian law enforcement never conducts digital arrests over phone/video calls."
            RiskStatus.Suspicious -> "Exercise caution. Verify directly with the official service provider via their verified portal or toll-free helpline."
            RiskStatus.Low -> "No verified threat triggers found in this message. Always verify unknown senders before taking action."
        }

        val combinedTactics: List<String> = (allCategories + semanticSignals + astraSignals).toSet().toList()

        MessageSecurityDecision(
            rawText = text,
            sender = sender,
            riskScore = finalScore,
            status = status,
            threatCategory = threatCategory,
            matchedKeywords = allMatchedKeywords.toSet().toList(),
            detectedTactics = combinedTactics,
            guidance = guidance,
            extractedUrls = extractedUrls
        )
    }

    override suspend fun assessCall(
        channelOrNumber: String,
        transcriptLines: List<String>
    ): CallSecurityDecision = withContext(Dispatchers.Default) {
        val fullTranscript = transcriptLines.joinToString(" ")
        val msgDecision = assessMessage(fullTranscript, channelOrNumber)

        CallSecurityDecision(
            channelOrNumber = channelOrNumber,
            riskScore = msgDecision.riskScore,
            status = msgDecision.status,
            suspectedScammerRole = if (msgDecision.isHighRisk) "Remote Caller" else null,
            detectedTactics = msgDecision.detectedTactics,
            emergencyEscalationRecommended = msgDecision.riskScore >= 75
        )
    }
}
