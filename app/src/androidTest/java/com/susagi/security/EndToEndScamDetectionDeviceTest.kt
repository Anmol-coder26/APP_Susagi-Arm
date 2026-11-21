package com.susagi.security

import android.test.InstrumentationTestCase
import com.susagi.intelligence.UnifiedThreatEngine
import com.susagi.safety.Risk
import com.susagi.safety.SafetyBarrier
import com.susagi.safety.Signal
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Strict Android on-device acceptance test for end-to-end scam detection workflows.
 * Verifies real offline SafetyBarrier, UnifiedThreatEngine, and multi-link destination evaluation.
 */
class EndToEndScamDetectionDeviceTest : InstrumentationTestCase() {

    @Test
    fun testEndToEnd_digitalArrestScenario() = runBlocking {
        val message = "This is CBI Officer Sharma. A digital arrest warrant is issued in your name. Do not hang up or tell anyone."
        val assessment = SafetyBarrier.analyze(null, message)

        assertEquals("Digital arrest extortion must trigger HIGH risk", Risk.HIGH, assessment.risk)
        assertTrue("Evidence must contain ARREST signal", assessment.evidence.any { it.id == Signal.ARREST })
        assertTrue("Verdict must advise independence", assessment.recommendations.isNotEmpty())

        val engine = UnifiedThreatEngine()
        val engineResult = engine.analyzeMessage(message)
        assertTrue("Threat engine must flag digital arrest as dangerous", engineResult.isDangerous)
        assertTrue("Detected tactics must include arrest", engineResult.detectedTactics.any { it.contains("arrest") })
    }

    @Test
    fun testEndToEnd_upiRefundTrapScenario() = runBlocking {
        val message = "Dear customer, your ₹5,000 refund is pending. To receive cashback, approve this mandate in your UPI app immediately."
        val assessment = SafetyBarrier.analyze(null, message)

        assertEquals("UPI mandate trap must trigger HIGH risk", Risk.HIGH, assessment.risk)
        assertTrue("Evidence must contain UPI signal", assessment.evidence.any { it.id == Signal.UPI })

        val engine = UnifiedThreatEngine()
        val engineResult = engine.analyzeMessage(message)
        assertTrue("Threat engine must flag UPI trap as dangerous", engineResult.isDangerous)
        assertTrue("Detected tactics must include upi", engineResult.detectedTactics.any { it.contains("upi") })
    }

    @Test
    fun testEndToEnd_electricityCutoffExtortion() = runBlocking {
        val message = "Electricity bill overdue: Power will be disconnected tonight at 9:30 PM. Call electricity officer immediately: https://pay-bill-officer.example/update.apk"
        val assessment = SafetyBarrier.analyze("https://pay-bill-officer.example/update.apk", message)

        assertEquals("Utility cutoff extortion must trigger HIGH risk", Risk.HIGH, assessment.risk)
        assertTrue(
            "Evidence must contain UTILITY or APK signal",
            assessment.evidence.any { it.id == Signal.UTILITY || it.id == Signal.APK }
        )
    }

    @Test
    fun testEndToEnd_benignMessageControl() = runBlocking {
        val message = "Hi Papa, reached the hostel safely. Will call you after dinner."
        val assessment = SafetyBarrier.analyze(null, message)

        assertEquals("Benign family message must evaluate to LOW risk", Risk.LOW, assessment.risk)
        assertTrue("No threat evidence should be triggered", assessment.evidence.isEmpty())
        assertTrue("Explanation must be grounded and truthful", assessment.explanation.contains("No known local threat indicators detected"))

        val engine = UnifiedThreatEngine()
        val engineResult = engine.analyzeMessage(message)
        assertFalse("Benign message must not be flagged dangerous", engineResult.isDangerous)
    }

    @Test
    fun testEndToEnd_multiDestinationMessage() = runBlocking {
        val message = "Check out our shop https://axisbank.com/offers and download our app at https://download-sbi-secure.top/app.apk"
        val assessment = SafetyBarrier.analyzeMessage(message)

        assertEquals("Malicious APK in multi-link message must raise HIGH risk", Risk.HIGH, assessment.risk)
        assertTrue("Destinations must have at least 2 entries", assessment.destinations.size >= 2)
        assertTrue("Must detect legitimate axisbank destination", assessment.destinations.any { it.originalUrl.contains("axisbank.com") && it.assessment.risk == Risk.LOW })
        assertTrue("Must detect dangerous APK destination", assessment.destinations.any { it.assessment.risk == Risk.HIGH })
    }
}
