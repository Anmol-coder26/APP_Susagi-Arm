package com.susagi.intelligence

import com.guardian.app.LinkCheckActivity
import com.susagi.safety.Risk
import com.susagi.safety.SafetyBarrier
import com.susagi.safety.Signal
import java.util.Locale

/**
 * AstraDedicatedSecurityClassifier
 *
 * Dedicated lightweight on-device classifier focused on:
 * - Indian digital arrest scams
 * - UPI refund/payment collect traps
 * - Fake electricity / utility bill disconnection fraud
 * - Bank KYC & account suspension phishing
 * - Credential & OTP harvesting
 * - Malicious APK sideloading
 * - Distinguishing educational / technical cybersecurity discussions from attacks
 *
 * Grounded in structured evidence, producing calibrated scores without false precision.
 */
object AstraDedicatedSecurityClassifier {

    suspend fun classify(text: String): DedicatedSecurityFinding {
        if (text.isBlank()) {
            return DedicatedSecurityFinding(
                threatCategory = "Empty Input",
                requestedAction = "None",
                claimedAuthority = "None",
                coercionIndicators = emptyList(),
                credentialRequest = false,
                paymentRequest = false,
                urlIndicators = emptyList(),
                warningOrQuotedContext = false,
                modelScore = 0,
                confidenceStatus = "BENIGN_NORMAL",
                evidence = emptyList()
            )
        }

        val trimmed = text.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. Check for Educational / Technical Advisory / Defensive Warning Context
        val isEducational = EducationalContentDetector.isEducationalOrAnalysis(trimmed)
        if (isEducational) {
            return DedicatedSecurityFinding(
                threatCategory = "Educational / Security Advisory",
                requestedAction = "None (Defensive / Informational Guidance)",
                claimedAuthority = "None (Independent Security Research)",
                coercionIndicators = emptyList(),
                credentialRequest = false,
                paymentRequest = false,
                urlIndicators = emptyList(),
                warningOrQuotedContext = true,
                modelScore = 10,
                confidenceStatus = "EDUCATIONAL_ADVISORY",
                evidence = listOf(
                    "Identified educational or advisory context ('analysis', 'advisory', 'protect against', 'researchers found')",
                    "No imperative coercion or actionable demand targeting the recipient",
                    "Classified as low risk to prevent false positives on security discussions"
                )
            )
        }

        // 2. Run deterministic SafetyBarrier inspection
        val barrier = SafetyBarrier.analyze(null, trimmed)
        val extractedUrls = barrier.destinations.map { it.originalUrl }.ifEmpty {
            val single = LinkCheckActivity.extractUrlFromText(trimmed)
            if (single.isNotBlank()) listOf(single) else emptyList()
        }

        // 3. Extract Claimed Authority
        val hasPolice = lower.contains("police") || lower.contains("cbi") || lower.contains("crime branch") ||
                lower.contains("narcotics") || lower.contains("court") || lower.contains("warrant") ||
                lower.contains("पुलिस") || lower.contains("सीबीआई") || lower.contains("वारंट")
        val hasBank = lower.contains("sbi") || lower.contains("hdfc") || lower.contains("icici") ||
                lower.contains("rbi") || lower.contains("bank") || lower.contains("खाता") || lower.contains("केवाईसी")
        val hasUtility = lower.contains("electricity") || lower.contains("power cut") || lower.contains("bijli") ||
                lower.contains("discom") || lower.contains("बिजली")
        val hasCustoms = lower.contains("customs") || lower.contains("courier") || lower.contains("parcel") ||
                lower.contains("speed post") || lower.contains("पार्सल")

        val hasLottery = lower.contains("lottery") || lower.contains("lotery") ||
                lower.contains("lucky draw") || lower.contains("jackpot") ||
                lower.contains("cash prize") || lower.contains("won a prize") ||
                lower.contains("won prize") || lower.contains("लॉटरी") ||
                lower.contains("लकी ड्रॉ") || lower.contains("इनाम") || lower.contains("पुरस्कार") ||
                (lower.contains("won") && (lower.contains("10 k") || lower.contains("10k") || lower.contains("crore") || lower.contains("lakh") || lower.contains("₹") || lower.contains("rs")))

        val hasFee = lower.contains("fee") || lower.contains("charge") || lower.contains("tax") ||
                lower.contains("deposit") || lower.contains("registration") || lower.contains("processing") ||
                lower.contains("शुल्क") || lower.contains("टैक्स")

        val isLegitimatePersonal = lower.contains("our school") || lower.contains("school lottery") ||
                lower.contains("bought a lottery") || lower.contains("bought a ticket") || lower.contains("purchased a ticket") ||
                lower.contains("friend won") || lower.contains("colleague won") || lower.contains("yesterday i bought")

        val claimedAuthority = when {
            hasPolice -> "Law Enforcement / Police / CBI Extortion"
            hasBank -> "Banking Institution / RBI Impersonation"
            hasUtility -> "Electricity DISCOM / Utility Authority"
            hasCustoms -> "Postal / Customs Authority"
            hasLottery && !isLegitimatePersonal -> "Purported Lottery / Lucky Draw Sponsor"
            else -> "Unverified Senders"
        }

        // 4. Extract Coercion Indicators
        val coercionIndicators = mutableListOf<String>()
        if (lower.contains("digital arrest") || lower.contains("डिजिटल अरेस्ट") || (hasPolice && lower.contains("arrest"))) {
            coercionIndicators.add("Digital arrest extortion (threatening unlawful video confinement)")
        }
        if (hasUtility && (lower.contains("tonight") || lower.contains("disconnect") || lower.contains("cut off") || lower.contains("कट जाएगी"))) {
            coercionIndicators.add("Utility disconnection threat within hours")
        }
        if (hasBank && (lower.contains("blocked") || lower.contains("suspended") || lower.contains("बंद हो जाएगा"))) {
            coercionIndicators.add("Banking suspension panic tactic")
        }
        if (lower.contains("urgent") || lower.contains("immediately") || lower.contains("within 10 minutes") || lower.contains("तुरंत")) {
            coercionIndicators.add("Psychological urgency pressure")
        }
        if (hasLottery && !isLegitimatePersonal) {
            coercionIndicators.add("Unsolicited prize or lottery claim announcement")
        }

        // 5. Credential & Payment Extraction
        val hasOtp = lower.contains("otp") || lower.contains("cvv") || lower.contains("pin") ||
                lower.contains("password") || lower.contains("ओटीपी") || lower.contains("पिन")
        val hasPayment = lower.contains("transfer") || lower.contains("pay now") || lower.contains("send money") ||
                lower.contains("fine") || lower.contains("fee") || lower.contains("रुपये") || lower.contains("पैसे") ||
                lower.contains("upi://") || lower.contains("collect request")

        val hasDigitalArrest = lower.contains("digital arrest") || lower.contains("डिजिटल अरेस्ट") ||
                (hasPolice && (lower.contains("arrest") || lower.contains("गिरफ्तार") || lower.contains("अरेस्ट")))

        // 6. Requested Action
        val requestedAction = when {
            hasDigitalArrest ->
                "Remain in unlawful isolation / video confinement under false digital arrest"
            hasLottery && !isLegitimatePersonal && hasOtp ->
                "Disclose confidential OTP or PIN to claim prize payout"
            hasLottery && !isLegitimatePersonal && (hasPayment || hasFee) ->
                "Pay upfront processing fee or tax to release promised lottery winnings"
            hasLottery && !isLegitimatePersonal ->
                "Respond to unverified prize announcement (potential fee/credential bait)"
            hasOtp && (lower.contains("share") || lower.contains("tell") || lower.contains("send") || lower.contains("बताओ")) ->
                "Disclose confidential OTP or authentication credential"
            lower.contains("upi pin") || (lower.contains("collect") && lower.contains("receive")) ->
                "Approve reverse-debit collect request or enter UPI PIN"
            extractedUrls.any { it.endsWith(".apk") } ->
                "Download and sideload unverified APK application"
            hasPayment && coercionIndicators.isNotEmpty() ->
                "Transfer urgent monetary funds to avoid punitive action"
            hasBank && lower.contains("kyc") ->
                "Navigate to unverified portal to update banking credentials"
            else -> "Unverified informational communication"
        }

        // 7. Grounded Evidence Assembly
        val evidenceList = mutableListOf<String>()
        barrier.evidence.forEach { ev ->
            if (ev.id != Signal.INVALID && ev.id != Signal.LIMIT) {
                evidenceList.add("${ev.id.name}: ${ev.id.explanation}")
            }
        }
        if (coercionIndicators.isNotEmpty()) {
            evidenceList.add("Coercion: ${coercionIndicators.joinToString("; ")}")
        }
        if (extractedUrls.isNotEmpty()) {
            evidenceList.add("Extracted Destinations: ${extractedUrls.joinToString(", ")}")
        }

        // 8. Calibrated Scoring (Bound keyword matching, require intent for HIGH)
        val hasAttackDirective = EducationalContentDetector.hasActionableAttackDirective(trimmed)
        val isAstraHigh = barrier.risk == Risk.HIGH
        val isAstraReview = barrier.risk == Risk.REVIEW

        val (score, status, category) = when {
            hasDigitalArrest -> {
                Triple(88, "CONFIRMED_HIGH", "Digital Arrest Law Enforcement Extortion")
            }
            hasLottery && !isLegitimatePersonal && hasOtp -> {
                Triple(90, "CONFIRMED_HIGH", "Prize Claim Credential & OTP Harvesting")
            }
            hasLottery && !isLegitimatePersonal && (hasPayment || hasFee) -> {
                Triple(88, "CONFIRMED_HIGH", "Advance-Fee Lottery & Prize Fraud")
            }
            isAstraHigh || (coercionIndicators.isNotEmpty() && (hasPayment || hasOtp || hasAttackDirective)) -> {
                val cat = when {
                    hasPolice -> "Digital Arrest Law Enforcement Extortion"
                    hasUtility -> "Electricity Bill Disconnection Panic Fraud"
                    hasPayment && lower.contains("upi") -> "UPI PIN Reverse-Debit Trap"
                    hasOtp -> "2FA Credential & OTP Harvesting"
                    extractedUrls.any { it.endsWith(".apk") } -> "Malicious Remote APK Sideload Trap"
                    else -> "Coercive Social Engineering Fraud"
                }
                Triple(88, "CONFIRMED_HIGH", cat)
            }
            hasLottery && !isLegitimatePersonal -> {
                Triple(55, "CAUTION_REVIEW", "Unverified Lottery / Prize Solicitation")
            }
            isAstraReview || coercionIndicators.isNotEmpty() || hasOtp || hasPayment -> {
                Triple(50, "CAUTION_REVIEW", "Suspicious Communication Requiring Verification")
            }
            else -> {
                Triple(15, "BENIGN_NORMAL", "Normal Content / No Threats Identified")
            }
        }

        return DedicatedSecurityFinding(
            threatCategory = category,
            requestedAction = requestedAction,
            claimedAuthority = claimedAuthority,
            coercionIndicators = coercionIndicators,
            credentialRequest = hasOtp,
            paymentRequest = hasPayment,
            urlIndicators = extractedUrls,
            warningOrQuotedContext = false,
            modelScore = score,
            confidenceStatus = status,
            evidence = evidenceList.ifEmpty { listOf("No anomalous indicators detected") }
        )
    }
}
