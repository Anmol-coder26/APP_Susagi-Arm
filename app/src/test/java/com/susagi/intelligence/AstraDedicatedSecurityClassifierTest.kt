package com.susagi.intelligence

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraDedicatedSecurityClassifierTest {

    @Test
    fun testDigitalArrest_OutputsStructuredAttribution() = runBlocking {
        val message = "This is CBI Crime Branch officer. An arrest warrant has been issued against you under the Official Secrets Act. Transfer 50000 immediately to RBI safe account to avoid digital arrest."
        val finding = AstraDedicatedSecurityClassifier.classify(message)

        assertEquals("CONFIRMED_HIGH", finding.confidenceStatus)
        assertEquals("Digital Arrest Law Enforcement Extortion", finding.threatCategory)
        assertTrue(finding.claimedAuthority.contains("Police") || finding.claimedAuthority.contains("CBI"))
        assertTrue(finding.coercionIndicators.any { it.contains("Digital arrest", ignoreCase = true) })
        assertTrue(finding.paymentRequest)
        assertFalse(finding.warningOrQuotedContext)
        assertEquals(88, finding.modelScore) // Calibrated bounded score (never false 98)
        assertTrue(finding.evidence.isNotEmpty())
    }

    @Test
    fun testUpiCollectTrap_OutputsStructuredFinding() = runBlocking {
        val message = "Congratulations! Cashback of Rs 2500 received. Enter your UPI PIN to approve collect request and claim refund."
        val finding = AstraDedicatedSecurityClassifier.classify(message)

        assertEquals("CONFIRMED_HIGH", finding.confidenceStatus)
        assertTrue(finding.threatCategory.contains("UPI"))
        assertTrue(finding.credentialRequest)
        assertTrue(finding.requestedAction.contains("UPI PIN", ignoreCase = true) || finding.requestedAction.contains("collect", ignoreCase = true))
        assertFalse(finding.warningOrQuotedContext)
        assertEquals(88, finding.modelScore)
    }

    @Test
    fun testUtilityPowerDisconnection_OutputsCoercionIndicators() = runBlocking {
        val message = "Dear consumer, your electricity power will be disconnected tonight at 9:30 PM due to unpaid bill. Pay immediately at https://discom-bill.example/pay"
        val finding = AstraDedicatedSecurityClassifier.classify(message)

        assertEquals("CONFIRMED_HIGH", finding.confidenceStatus)
        assertEquals("Electricity Bill Disconnection Panic Fraud", finding.threatCategory)
        assertTrue(finding.claimedAuthority.contains("Utility") || finding.claimedAuthority.contains("Electricity"))
        assertTrue(finding.coercionIndicators.any { it.contains("disconnection", ignoreCase = true) })
        assertTrue(finding.urlIndicators.isNotEmpty())
    }

    @Test
    fun testEducationalContent_ReceivesEducationalAdvisoryStatus() = runBlocking {
        val advisory = "Security researchers found a new vulnerability in banking portals. In this advisory, we share best practices on how to avoid phishing and protect against unauthorized money transfer."
        val finding = AstraDedicatedSecurityClassifier.classify(advisory)

        assertEquals("EDUCATIONAL_ADVISORY", finding.confidenceStatus)
        assertEquals("Educational / Security Advisory", finding.threatCategory)
        assertTrue(finding.warningOrQuotedContext)
        assertEquals(10, finding.modelScore)
        assertFalse(finding.credentialRequest)
        assertFalse(finding.paymentRequest)
    }

    @Test
    fun testBenignFamilyMessage_ReceivesNormalStatus() = runBlocking {
        val benign = "Hi Mom, reached office safely. Will call you after the team meeting."
        val finding = AstraDedicatedSecurityClassifier.classify(benign)

        assertEquals("BENIGN_NORMAL", finding.confidenceStatus)
        assertEquals("Normal Content / No Threats Identified", finding.threatCategory)
        assertEquals(15, finding.modelScore)
        assertFalse(finding.warningOrQuotedContext)
    }
}
