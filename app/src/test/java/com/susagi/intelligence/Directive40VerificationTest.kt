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
}
