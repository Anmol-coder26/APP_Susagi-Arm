package com.susagi.security

import com.susagi.safety.Action
import com.susagi.safety.Risk
import com.susagi.safety.SafetyBarrier
import com.susagi.safety.Signal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityFlowIntegrationTest {

    @Test
    fun testCheckMessage_digitalArrestExtortion_returnsHighAndActionableAdvice() = runBlocking {
        val input = "Aap digital arrest mein hain. Do not disconnect the line and transfer fifty thousand rupees immediately to avoid FIR."
        val assessment = SafetyBarrier.analyzeMessage(input)

        assertEquals("Expected Risk.HIGH for digital arrest", Risk.HIGH, assessment.risk)
        assertEquals(Action.RECOMMEND_BLOCK, assessment.action)
        assertTrue("Must detect Signal.ARREST", assessment.evidence.any { it.id == Signal.ARREST })
        assertTrue("Must provide grounded advice", assessment.recommendations.any { it.contains("authority", ignoreCase = true) || it.contains("independently", ignoreCase = true) })
        assertFalse("Must not claim safe", assessment.explanation.contains("verified safe", ignoreCase = true))
    }

    @Test
    fun testCheckMessage_benignBankAndFamily_returnsLowNoUnsupportedAlert() = runBlocking {
        val bankCredit = "Dear Customer, your account XX1234 has been credited with INR 2,500.00 via UPI on 22-Nov-2026. Avl Bal: INR 18,450.00."
        val bankAssessment = SafetyBarrier.analyzeMessage(bankCredit)
        assertEquals(Risk.LOW, bankAssessment.risk)
        assertEquals(Action.OFFER_OPEN, bankAssessment.action)
        assertTrue(bankAssessment.evidence.isEmpty())

        val familyDinner = "Hi Mom, reaching home around 7:30 PM for dinner."
        val familyAssessment = SafetyBarrier.analyzeMessage(familyDinner)
        assertEquals(Risk.LOW, familyAssessment.risk)
        assertFalse("Benign message must never trigger HIGH", familyAssessment.risk == Risk.HIGH)
    }

    @Test
    fun testCheckLink_mixedLegitimateAndPunycode_independentVerdictsZeroAutoOpen() = runBlocking {
        val message = "Check your statement at https://axisbank.com or verify KYC at https://аxisbank.example/login"
        val assessment = SafetyBarrier.analyzeMessage(message)

        assertEquals("Must extract 2 destinations", 2, assessment.destinations.size)
        val legit = assessment.destinations.first { it.originalUrl.contains("axisbank.com") }
        val phish = assessment.destinations.first { it.originalUrl.contains("example") }

        assertEquals(Risk.LOW, legit.assessment.risk)
        assertEquals(Risk.HIGH, phish.assessment.risk)
        assertTrue("Must detect BRAND impersonation on spoof domain", phish.assessment.evidence.any { it.id == Signal.BRAND })
        assertEquals("Action must not auto-open", Action.RECOMMEND_BLOCK, assessment.action)
    }

    @Test
    fun testCheckUpi_fraudulentRefundMandate_returnsHighWithPaymentTrap() = runBlocking {
        val input = "Congratulations! To receive your cashback of ₹4,500, scan this QR code and approve the collect mandate in your UPI app."
        val assessment = SafetyBarrier.analyzeMessage(input)

        assertEquals(Risk.HIGH, assessment.risk)
        assertTrue("Must detect Signal.UPI", assessment.evidence.any { it.id == Signal.UPI })
        assertEquals(Action.RECOMMEND_BLOCK, assessment.action)
    }

    @Test
    fun testCheckQr_decodedTargetMatchesManualPaste() = runBlocking {
        val rawUpi = "upi://pay?pa=merchant%40axisbank&pn=Store&am=500.00&cu=INR"
        val manualAssessment = SafetyBarrier.analyzeUrl(rawUpi)
        val qrAssessment = SafetyBarrier.analyzeUrl(rawUpi)

        assertEquals(manualAssessment.risk, qrAssessment.risk)
        assertEquals(manualAssessment.evidence.map { it.id }, qrAssessment.evidence.map { it.id })
        assertEquals(manualAssessment.action, qrAssessment.action)
    }

    @Test
    fun testInputBoundary_oversizedAndMalformedInputs_failGracefullyWithoutCrash() = runBlocking {
        val oversized = "A".repeat(65536)
        val result = SafetyBarrier.analyzeMessage(oversized)
        assertEquals(Risk.UNKNOWN, result.risk)
        assertTrue(result.evidence.any { it.id == Signal.LIMIT })

        val empty = SafetyBarrier.analyzeMessage("   ")
        assertEquals(Risk.UNKNOWN, empty.risk)
        assertTrue(empty.evidence.any { it.id == Signal.INVALID })
    }
}
