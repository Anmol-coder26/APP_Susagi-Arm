package com.susagi.security

import com.guardian.app.RiskStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraSecurityBridgeTest {

    @Test
    fun testAssessUrl_phishingDomain_returnsHighRiskDecision() = runBlocking {
        val decision = AstraSecurityBridge.assessUrl("https://login-kyc-verify.com/sbi")
        assertEquals(RiskStatus.High, decision.status)
        assertTrue(decision.isHighRisk)
        assertFalse(decision.isSafe)
        assertTrue(decision.riskScore >= 70)
    }

    @Test
    fun testAssessUrl_apkDownload_detectsDirectApkPayload() = runBlocking {
        val decision = AstraSecurityBridge.assessUrl("https://update-service.net/app_v2.apk")
        assertEquals(RiskStatus.High, decision.status)
        assertTrue(decision.isDirectApkDownload)
        assertTrue(decision.reasons.any { it.contains("APK", ignoreCase = true) })
    }

    @Test
    fun testAssessUrl_punycodeHomoglyph_detectsHomoglyph() = runBlocking {
        val decision = AstraSecurityBridge.assessUrl("https://xn--pypal-4ve.com/signin")
        assertEquals(RiskStatus.High, decision.status)
        assertTrue(decision.isHomoglyphOrPunycode)
    }

    @Test
    fun testAssessUrl_trustedBankDomain_returnsSafeDecision() = runBlocking {
        val decision = AstraSecurityBridge.assessUrl("https://login.axisbank.com/secure")
        assertEquals(RiskStatus.Low, decision.status)
        assertTrue(decision.isSafe)
        assertFalse(decision.isHighRisk)
        assertEquals("axisbank.com", decision.canonicalHost.removePrefix("login."))
    }

    @Test
    fun testAssessMessage_digitalArrestHindi_detectsCoercionThreat() = runBlocking {
        val text = "सीबीआई हेडक्वार्टर से नोटिस: आपके आधार पर मनी लॉन्ड्रिंग केस है, तुरंत डिजिटल अरेस्ट में रहें।"
        val decision = AstraSecurityBridge.assessMessage(text, "UNKNOWN_OFFICER")
        assertTrue(decision.isHighRisk)
        assertEquals(RiskStatus.High, decision.status)
        assertTrue(decision.threatCategory.contains("Digital Arrest", ignoreCase = true))
        assertTrue(decision.guidance.contains("Indian law enforcement never conducts digital arrests", ignoreCase = true))
    }

    @Test
    fun testAssessMessage_electricityBillScam_detectsUtilityFraud() = runBlocking {
        val text = "Dear customer, your electricity bill is unpaid. Power cut tonight. Call electricity officer immediately at 9876543210"
        val decision = AstraSecurityBridge.assessMessage(text, "VM-POWERCUT")
        assertTrue(decision.riskScore >= 60)
        assertTrue(decision.threatCategory.contains("Electricity", ignoreCase = true) || decision.threatCategory.contains("Financial", ignoreCase = true))
    }

    @Test
    fun testAssessMessage_innocentMessage_returnsSafeDecision() = runBlocking {
        val text = "Hi Mom, I have reached Bangalore safely. Will call you after dinner."
        val decision = AstraSecurityBridge.assessMessage(text, "Mom")
        assertTrue(decision.isSafe)
        assertEquals(RiskStatus.Low, decision.status)
        assertEquals("No Known Scam Pattern", decision.threatCategory)
    }
}
