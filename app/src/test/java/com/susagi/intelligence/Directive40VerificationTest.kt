package com.susagi.intelligence

import com.guardian.app.LinkCheckActivity
import com.guardian.app.RiskStatus
import com.guardian.app.SharedPayload
import com.susagi.safety.Risk
import com.susagi.safety.SafetyBarrier
import com.susagi.security.AstraSecurityBridge
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Directive 40 Acceptance & Verification Test Suite.
 *
 * Explicitly validates:
 * 1. Harmless educational technical content evaluated through:
 *    - Production Message Shield (AstraSecurityBridge.assessMessage)
 *    - Android text-share entry point (LinkCheckActivity.assessPayloadAsync)
 *    - Underlying SafetyBarrier.analyze
 *    Verifying NO 98% false alarm and NO unsupported digital-arrest labeling.
 * 2. Mixed message containing educational preamble + active attack demand:
 *    Verifying attack directive is NOT bypassed.
 * 3. Legitimate scam detection remains operational (high recall).
 * 4. Benign normal family messages remain low risk.
 */
class Directive40VerificationTest {

    private val harmlessEducationalPrompt = """
        Security researchers found a new vulnerability in banking portals.
        CERT-In advisory: Here is an educational case study and awareness campaign on how scammers operate.
        Always follow cybersecurity best practices. Tips to prevent fraud:
        Never share your OTP with anyone. Never transfer money to unverified callers claiming to be police.
        Guidelines to stay safe: report suspicious incidents immediately to 1930.
    """.trimIndent()

    private val mixedPromptCamouflage = """
        Cybersecurity awareness tip: Attackers often impersonate police or banks.
        However, an urgent warrant has been issued against your account.
        Transfer 50000 immediately to the safe verification account to avoid digital arrest.
    """.trimIndent()

    private val realScamDigitalArrest = """
        सीबीआई हेडक्वार्टर से नोटिस: आपके आधार पर मनी लॉन्ड्रिंग केस दर्ज है। तुरंत कमरे में बंद रहें और डिजिटल अरेस्ट में रहें।
    """.trimIndent()

    private val benignFamilyMessage = """
        Hi beta, I reached the market. Please send 200 rupees for groceries when you are free.
    """.trimIndent()

    @Test
    fun testHarmlessEducational_throughSafetyBarrier_returnsLowRisk() = runBlocking {
        val barrier = SafetyBarrier.analyze(null, harmlessEducationalPrompt)
        assertEquals(Risk.LOW, barrier.risk)
        assertFalse(barrier.evidence.any { it.id.name == "ARREST" })
    }

    @Test
    fun testHarmlessEducational_throughMessageShield_returnsLowRiskAndNoDigitalArrest() = runBlocking {
        val decision = AstraSecurityBridge.assessMessage(harmlessEducationalPrompt)
        assertEquals(RiskStatus.Low, decision.status)
        assertTrue(decision.riskScore <= 15)
        assertNotEquals(98, decision.riskScore)
        assertFalse(decision.threatCategory.contains("Digital Arrest", ignoreCase = true))
        assertEquals("Educational / Security Advisory", decision.threatCategory)
    }

    @Test
    fun testHarmlessEducational_throughAndroidTextShare_returnsLowRiskAndNoDigitalArrest() = runBlocking {
        val payload = SharedPayload.SharedContent(rawText = harmlessEducationalPrompt, urls = emptyList())
        val assessment = LinkCheckActivity.assessPayloadAsync(payload)

        assertEquals(RiskStatus.Low, assessment.status)
        assertTrue(assessment.actualRiskScore <= 15)
        assertNotEquals(98, assessment.actualRiskScore)
        assertFalse(assessment.categoryTitle.contains("DIGITAL ARREST", ignoreCase = true))
        assertEquals("EDUCATIONAL / SECURITY ADVISORY", assessment.categoryTitle)
    }

    @Test
    fun testMixedMessage_educationalWithAttackDemand_flagsHighRiskThreat() = runBlocking {
        // Mixed message contains educational phrase, BUT ALSO an actionable extortion demand ("transfer 50000 immediately")
        val decision = AstraSecurityBridge.assessMessage(mixedPromptCamouflage)
        assertEquals(RiskStatus.High, decision.status)
        assertTrue(decision.riskScore >= 70)
        assertTrue(decision.threatCategory.contains("Digital Arrest", ignoreCase = true) || decision.threatCategory.contains("Financial", ignoreCase = true))

        val finding = SecurityModel.analyze(mixedPromptCamouflage)
        assertEquals("CONFIRMED_HIGH", finding.uncertaintyOrAbstentionStatus)
        assertTrue(finding.paymentDemand)
        assertFalse(finding.quotationOrWarningContext) // Not treated as benign quotation
    }

    @Test
    fun testRealScam_digitalArrest_detectedAccurately() = runBlocking {
        val decision = AstraSecurityBridge.assessMessage(realScamDigitalArrest)
        assertEquals(RiskStatus.High, decision.status)
        assertTrue(decision.riskScore >= 70)
        assertTrue(decision.threatCategory.contains("Digital Arrest", ignoreCase = true))

        val finding = SecurityModel.analyze(realScamDigitalArrest)
        assertEquals("CONFIRMED_HIGH", finding.uncertaintyOrAbstentionStatus)
        assertTrue(finding.primaryThreatCategory.contains("Digital Arrest", ignoreCase = true))
    }

    @Test
    fun testBenignFamily_evaluatedAsLowRisk() = runBlocking {
        val decision = AstraSecurityBridge.assessMessage(benignFamilyMessage)
        assertEquals(RiskStatus.Low, decision.status)
        assertTrue(decision.riskScore <= 20)
        assertFalse(decision.threatCategory.contains("Digital Arrest", ignoreCase = true))

        val finding = SecurityModel.analyze(benignFamilyMessage)
        assertEquals("BENIGN_NORMAL", finding.uncertaintyOrAbstentionStatus)
    }

    @Test
    fun testBenignA_technicalDevelopmentPrompt_evaluatesLowRisk() = runBlocking {
        val technicalPrompt = """
            # MASTER DIRECTIVE: OPERATION CYBERSECURITY APP
            We are engineering an Android application for the Arm AI-SoC Bharat Challenge PS3.
            The pipeline analyzes OTP theft, phishing URLs, digital arrest extortion schemes, and UPI traps.
            Write a Kotlin class implementing the security detector and verify with unit tests.
        """.trimIndent()

        // Message Shield
        val decision = AstraSecurityBridge.assessMessage(technicalPrompt)
        assertEquals(RiskStatus.Low, decision.status)
        assertTrue(decision.riskScore <= 15)
        assertFalse(decision.threatCategory.contains("Digital Arrest", ignoreCase = true))

        // Android Share
        val payload = SharedPayload.SharedContent(rawText = technicalPrompt, urls = emptyList())
        val assessment = LinkCheckActivity.assessPayloadAsync(payload)
        assertEquals(RiskStatus.Low, assessment.status)
        assertTrue(assessment.actualRiskScore <= 15)
        assertFalse(assessment.categoryTitle.contains("DIGITAL ARREST", ignoreCase = true))

        // Dedicated Security Model
        val finding = SecurityModel.analyze(technicalPrompt)
        assertEquals("EDUCATIONAL_ADVISORY", finding.uncertaintyOrAbstentionStatus)
        assertTrue(finding.modelRawScore <= 15)
    }

    @Test
    fun testBenignB_securityEducation_noDirectCredentialTheftAccusation() = runBlocking {
        val advisory = "Security tip: Never share your bank OTP, password, or PIN with any caller. Banks never ask for OTP."
        val decision = AstraSecurityBridge.assessMessage(advisory)
        assertEquals(RiskStatus.Low, decision.status)
        assertFalse(decision.threatCategory.contains("Credential", ignoreCase = true))

        val finding = SecurityModel.analyze(advisory)
        assertFalse(finding.credentialDemand)
    }

    @Test
    fun testBenignC_quotedScamReport_handledAppropriately() = runBlocking {
        val report = """User reported that scammers say: "Your electricity will be disconnected tonight, pay immediately." How do I report this?"""
        val decision = AstraSecurityBridge.assessMessage(report)
        assertTrue(decision.status == RiskStatus.Low || decision.status == RiskStatus.Suspicious)
        assertNotEquals(RiskStatus.High, decision.status)
    }

    @Test
    fun testMaliciousA_directOtpTheft_detectedHighRisk() = runBlocking {
        val scam = "Dear customer, your SBI card is blocked. Share your 6-digit OTP immediately to verify account."
        val decision = AstraSecurityBridge.assessMessage(scam)
        assertEquals(RiskStatus.High, decision.status)
        assertTrue(decision.riskScore >= 70)
        assertTrue(decision.threatCategory.contains("OTP", ignoreCase = true) ||
                decision.threatCategory.contains("Credential", ignoreCase = true) ||
                decision.threatCategory.contains("KYC", ignoreCase = true))
    }

    @Test
    fun testMaliciousB_taskDepositScam_detectedHighRisk() = runBlocking {
        val scam = "Telegram Part-time job offer: Earn 5000 daily with YouTube likes. Deposit 2000 rupees registration fee to activate account."
        val decision = AstraSecurityBridge.assessMessage(scam)
        assertEquals(RiskStatus.High, decision.status)
        assertTrue(decision.riskScore >= 70)
        assertTrue(decision.threatCategory.contains("Advance Fee", ignoreCase = true) ||
                decision.threatCategory.contains("Financial", ignoreCase = true))
    }

    @Test
    fun testMixedB_harmlessTechnicalWithEmbeddedUrl_independentAnalysis() = runBlocking {
        val techWithUrl = "Security research report on domain safety: review our open documentation at https://example.com/docs for guidance."
        val payload = SharedPayload.SharedContent(rawText = techWithUrl, urls = listOf("https://example.com/docs"))
        val assessment = LinkCheckActivity.assessPayloadAsync(payload)
        assertEquals(RiskStatus.Low, assessment.status)
        assertTrue(assessment.actualRiskScore <= 15)
        assertFalse(assessment.categoryTitle.contains("EXTORTION", ignoreCase = true))
    }
}
