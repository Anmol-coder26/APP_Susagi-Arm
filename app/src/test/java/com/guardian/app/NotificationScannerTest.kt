package com.guardian.app

import com.susagi.safety.Risk
import com.susagi.safety.SafetyBarrier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationScannerTest {

    @Before
    fun setUp() {
        NotificationScannerService.clearAlertHistory()
    }

    @Test
    fun testDuplicateAlertSuppression() {
        val testHash = "Scam Alert: Electricity Cut".hashCode()
        val t0 = 1000_000L

        // First alert must be allowed
        assertTrue("First alert must trigger", NotificationScannerService.shouldAlert(testHash, t0))

        // Immediate duplicate (1 second later) must be suppressed
        assertFalse("Duplicate within window must be suppressed", NotificationScannerService.shouldAlert(testHash, t0 + 1000L))

        // Duplicate after 4 minutes must still be suppressed
        assertFalse("Duplicate at 4 min must be suppressed", NotificationScannerService.shouldAlert(testHash, t0 + 240_000L))

        // Different notification hash at same time must be allowed
        val otherHash = "Bank Alert: OTP Required".hashCode()
        assertTrue("Different content hash must be allowed", NotificationScannerService.shouldAlert(otherHash, t0 + 240_000L))

        // Duplicate after 6 minutes (> 5 min window) must be allowed again
        assertTrue("Alert after 6 minutes must be allowed", NotificationScannerService.shouldAlert(testHash, t0 + 360_000L))
    }

    @Test
    fun testClearAlertHistory() {
        val testHash = "Arrest Warrant Notice".hashCode()
        assertTrue(NotificationScannerService.shouldAlert(testHash))
        assertFalse(NotificationScannerService.shouldAlert(testHash))

        NotificationScannerService.clearAlertHistory()
        assertTrue("After clear, alert must be allowed immediately", NotificationScannerService.shouldAlert(testHash))
    }

    @Test
    fun testScamNotificationDetection() = kotlinx.coroutines.runBlocking {
        val maliciousNotification = "URGENT: Electricity disconnection tonight at 9:30 PM. Pay bill now at http://sbpdcl-update.top"
        val assessment = SafetyBarrier.analyze(null, maliciousNotification)
        assertEquals("Malicious utility disconnection notification must be HIGH risk", Risk.HIGH, assessment.risk)
        assertTrue(assessment.evidence.any { it.id == com.susagi.safety.Signal.UTILITY })
    }

    @Test
    fun testBenignNotificationPreservation() = kotlinx.coroutines.runBlocking {
        val benignNotification = "HDFC Bank Alert: Rs. 4,500.00 credited to account XX1049. Never share your password or OTP with anyone."
        val assessment = SafetyBarrier.analyze(null, benignNotification)
        assertEquals("Benign banking credit notification must be LOW risk", Risk.LOW, assessment.risk)
    }
}
