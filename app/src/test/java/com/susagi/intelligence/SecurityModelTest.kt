package com.susagi.intelligence

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying the dedicated on-device cybersecurity model contract [SecurityModel].
 * Satisfies Directive 37 Part A2 requirements.
 */
class SecurityModelTest {

    @Test
    fun testAnalyze_digitalArrest_returnsStructuredFindings() = runBlocking {
        val text = "सीबीआई हेडक्वार्टर से नोटिस: आपके आधार पर मनी लॉन्ड्रिंग केस है, तुरंत डिजिटल अरेस्ट में रहें।"
        val result = SecurityModel.analyze(text)

        assertTrue(result.primaryThreatCategory.contains("Digital Arrest", ignoreCase = true))
        assertTrue(result.claimedAuthority.contains("Law Enforcement", ignoreCase = true) || result.claimedAuthority.contains("CBI", ignoreCase = true))
        assertTrue(result.coercionEvidence.isNotEmpty())
        assertEquals("CONFIRMED_HIGH", result.uncertaintyOrAbstentionStatus)
        assertTrue(result.modelRawScore >= 70)
        assertFalse(result.quotationOrWarningContext)
        assertEquals("v2.1-offline-arm", result.modelVersion)
    }

    @Test
    fun testAnalyze_upiCollectTrap_flagsPaymentAndCredential() = runBlocking {
        val text = "You have received cashback of Rs 5000. Accept collect request and enter UPI PIN to receive money."
        val result = SecurityModel.analyze(text)

        assertTrue(result.paymentDemand)
        assertTrue(result.credentialDemand)
        assertTrue(result.primaryThreatCategory.contains("UPI", ignoreCase = true))
        assertEquals("CONFIRMED_HIGH", result.uncertaintyOrAbstentionStatus)
        assertTrue(result.requestedAction.contains("UPI PIN", ignoreCase = true) || result.requestedAction.contains("collect request", ignoreCase = true))
    }

    @Test
    fun testAnalyze_educationalSecurityDiscussion_returnsLowRisk() = runBlocking {
        val text = "Security researchers found a new vulnerability in banking apps. CVE-2026-9921 advisory. Learn how to protect against phishing and never share passwords."
        val result = SecurityModel.analyze(text)

        assertTrue(result.quotationOrWarningContext)
        assertEquals("EDUCATIONAL_ADVISORY", result.uncertaintyOrAbstentionStatus)
        assertTrue(result.modelRawScore <= 15)
        assertEquals("Educational / Security Advisory", result.primaryThreatCategory)
    }

    @Test
    fun testAnalyze_benignNormalMessage_returnsBenignNormal() = runBlocking {
        val text = "Hi Papa, I reached college safely. See you in the evening."
        val result = SecurityModel.analyze(text)

        assertEquals("BENIGN_NORMAL", result.uncertaintyOrAbstentionStatus)
        assertTrue(result.modelRawScore <= 20)
        assertFalse(result.credentialDemand)
        assertFalse(result.paymentDemand)
    }

    @Test
    fun testAnalyze_maliciousApkLink_flagsApkSideload() = runBlocking {
        val text = "Update your banking service now. Download official app: https://sbi-secure.net/banking_support.apk"
        val result = SecurityModel.analyze(text)

        assertTrue(result.suspiciousUrlIndicators.any { it.endsWith(".apk") })
        assertTrue(result.requestedAction.contains("APK", ignoreCase = true))
        assertEquals("CONFIRMED_HIGH", result.uncertaintyOrAbstentionStatus)
    }
}
