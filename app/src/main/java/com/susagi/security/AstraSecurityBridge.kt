package com.susagi.security

import com.guardian.app.KeywordScorer
import com.guardian.app.LinkCheckActivity
import com.guardian.app.RiskStatus
import com.guardian.app.protect.RegionalScamKeywords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [SecurityAssessmentContract] bridging the Android UI layer
 * to Astra's underlying scam and phishing intelligence engines.
 */
object AstraSecurityBridge : SecurityAssessmentContract {

    private val supportedLanguages = listOf("hi", "en", "ta", "te", "bn", "mr", "kn", "ml", "pa", "gu")

    override suspend fun assessUrl(url: String): UrlSecurityDecision = withContext(Dispatchers.Default) {
        val verdict = LinkCheckActivity.evaluateLinkSafety(url)
        val lowerUrl = url.lowercase().trim()

        val isHomoglyph = verdict.title.contains("HOMOGLYPH", ignoreCase = true) ||
                verdict.title.contains("PUNYCODE", ignoreCase = true) ||
                lowerUrl.contains("xn--")
        val isApk = lowerUrl.endsWith(".apk") || lowerUrl.contains(".apk?") ||
                verdict.title.contains("APK", ignoreCase = true) ||
                verdict.title.contains("PACKAGE", ignoreCase = true)
        val isIp = verdict.title.contains("IP HOST", ignoreCase = true) ||
                Regex("""^https?://\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""").containsMatchIn(lowerUrl)

        val riskScore = when (verdict.status) {
            RiskStatus.High -> 85
            RiskStatus.Suspicious -> 50
            RiskStatus.Low -> 10
        }

        val reasons = mutableListOf<String>()
        if (isHomoglyph) reasons.add("Deceptive Unicode / Punycode mixed-script characters mimicking legitimate domain")
        if (isApk) reasons.add("Direct Android package (.apk) download payload detected")
        if (isIp) reasons.add("Numeric IP host address used instead of verified registered domain")
        if (verdict.title.contains("IMPERSONATION", ignoreCase = true)) reasons.add("Financial institution or brand impersonation detected")
        if (verdict.title.contains("SHORTENER", ignoreCase = true)) reasons.add("URL shortener obscuring final destination")

        val host = try {
            val uriStr = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
            java.net.URI(uriStr).host ?: ""
        } catch (_: Exception) {
            ""
        }

        UrlSecurityDecision(
            url = url,
            status = verdict.status,
            riskScore = riskScore,
            title = verdict.title,
            detail = verdict.detail,
            reasons = if (reasons.isEmpty()) listOf(verdict.detail) else reasons,
            canonicalHost = host,
            isHomoglyphOrPunycode = isHomoglyph,
            isDirectApkDownload = isApk,
            isIpHost = isIp
        )
    }

    override suspend fun assessMessage(text: String, sender: String?): MessageSecurityDecision = withContext(Dispatchers.Default) {
        val extractedUrl = LinkCheckActivity.extractUrlFromText(text)
        val urls = if (extractedUrl.isNotBlank()) listOf(extractedUrl) else emptyList()

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

        val status = when {
            combinedScore >= 70 -> RiskStatus.High
            combinedScore >= 35 -> RiskStatus.Suspicious
            else -> RiskStatus.Low
        }

        val lowerText = text.lowercase()
        val threatCategory = when {
            allCategories.any { it.contains("digital_arrest", ignoreCase = true) } ||
                    lowerText.contains("digital arrest") ||
                    lowerText.contains("डिजिटल अरेस्ट") ||
                    lowerText.contains("गिरफ्तार") ||
                    (lowerText.contains("सीबीआई") && lowerText.contains("नोटिस")) ||
                    allCategories.any { it.contains("authority", ignoreCase = true) } -> "Digital Arrest Coercion"
            allCategories.any { it.contains("electricity", ignoreCase = true) } ||
                    lowerText.contains("electricity") ||
                    lowerText.contains("power cut") -> "Utility / Electricity Bill Disconnection Fraud"
            allCategories.any { it.contains("lottery", ignoreCase = true) } ||
                    lowerText.contains("lottery") ||
                    lowerText.contains("kyc") -> "KYC / Account Block Extortion"
            urls.any { it.endsWith(".apk") } -> "Malicious APK Sideload Trap"
            combinedScore >= 60 -> "Financial / Impersonation Scam"
            combinedScore >= 35 -> "Unverified High-Pressure Request"
            else -> "No Known Scam Pattern"
        }

        val guidance = when (status) {
            RiskStatus.High -> "DO NOT transfer funds, share OTPs, or install APK files. Indian law enforcement never conducts digital arrests over phone/video calls."
            RiskStatus.Suspicious -> "Exercise caution. Verify directly with the official service provider via their verified portal or toll-free helpline."
            RiskStatus.Low -> "No verified threat triggers found in this message. Always verify unknown senders before taking action."
        }

        val combinedTactics: List<String> = (allCategories + semanticSignals).toSet().toList()

        MessageSecurityDecision(
            rawText = text,
            sender = sender,
            riskScore = combinedScore,
            status = status,
            threatCategory = threatCategory,
            matchedKeywords = allMatchedKeywords.toSet().toList(),
            detectedTactics = combinedTactics,
            guidance = guidance,
            extractedUrls = urls
        )
    }

    override suspend fun assessCall(
        channelOrNumber: String,
        transcriptLines: List<String>
    ): CallSecurityDecision = withContext(Dispatchers.Default) {
        val fullTranscript = transcriptLines.joinToString(" ")
        val msgDecision = assessMessage(fullTranscript)

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
