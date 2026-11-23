package com.susagi.safety

import com.susagi.edgeai.ExecuTorchLlamaEngine
import com.susagi.edgeai.ThreatChannel
import com.susagi.edgeai.ThreatInput
import com.susagi.intelligence.AstraDedicatedSecurityClassifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LotteryScamEvaluationTest
 *
 * Verifies Directive 56: Operation Real Voice Intelligence.
 * Validates calibrated contextual classification across:
 * - Case A: Unverified prize solicitation bait ("hello sir toh have a lottery of 10 k") -> NEEDS REVIEW / MEDIUM
 * - Case B: Advance-fee lottery fraud -> HIGH RISK
 * - Case C: Credential harvesting lottery fraud -> HIGH RISK
 * - Case D: Scam awareness / Educational advisory -> LOW RISK
 * - Case E: Legitimate / Personal recount -> LOW RISK
 * - Case F: Hinglish prize solicitation -> NEEDS REVIEW / MEDIUM
 */
class LotteryScamEvaluationTest {

    @Test
    fun testCaseA_LotteryBait_SafetyBarrierAndClassifier() = runBlocking {
        val input = "hello sir toh have a lottery of 10 k"

        // 1. SafetyBarrier assessment
        val barrier = SafetyBarrier.analyzeMessage(input)
        assertEquals(Risk.REVIEW, barrier.risk)
        assertTrue(
            "Evidence must contain Signal.LOTTERY",
            barrier.evidence.any { it.id == Signal.LOTTERY }
        )

        // 2. Astra Dedicated Security Classifier
        val finding = AstraDedicatedSecurityClassifier.classify(input)
        assertEquals("CAUTION_REVIEW", finding.confidenceStatus)
        assertEquals(55, finding.modelScore)
        assertEquals("Unverified Lottery / Prize Solicitation", finding.threatCategory)
        assertTrue(finding.coercionIndicators.any { it.contains("prize or lottery", ignoreCase = true) })
        assertTrue(finding.requestedAction.contains("prize", ignoreCase = true))

        // 3. ExecuTorch Llama Engine (Heuristic Call Channel)
        val engine = ExecuTorchLlamaEngine()
        val result = engine.analyzeThreat(
            ThreatInput(
                channel = ThreatChannel.VOIP_CALL,
                rawContent = input,
                structuredEvidence = listOf("Unsolicited Lottery / Prize Solicitation Indicator")
            )
        )
        assertEquals("MEDIUM", result.severity)
        assertEquals(55, result.riskScore)
        assertEquals("unverified_prize_solicitation", result.threatType)
        assertTrue(result.explanation.contains("lottery win or cash prize", ignoreCase = true))
        assertTrue(result.recommendedActions.any { it.contains("Do not pay any fee", ignoreCase = true) })
    }

    @Test
    fun testCaseB_AdvanceFeeLotteryFraud_HighRisk() = runBlocking {
        val input = "You have won ₹10,000. Send ₹500 processing fee to claim."

        // 1. SafetyBarrier
        val barrier = SafetyBarrier.analyzeMessage(input)
        assertEquals(Risk.HIGH, barrier.risk)
        assertTrue(
            "Must flag Signal.ADVANCE_FEE",
            barrier.evidence.any { it.id == Signal.ADVANCE_FEE }
        )

        // 2. Astra Classifier
        val finding = AstraDedicatedSecurityClassifier.classify(input)
        assertEquals("CONFIRMED_HIGH", finding.confidenceStatus)
        assertEquals(88, finding.modelScore)
        assertEquals("Advance-Fee Lottery & Prize Fraud", finding.threatCategory)
        assertTrue(finding.requestedAction.contains("fee", ignoreCase = true))

        // 3. ExecuTorch Engine
        val engine = ExecuTorchLlamaEngine()
        val result = engine.analyzeThreat(
            ThreatInput(
                channel = ThreatChannel.MESSAGE,
                rawContent = input
            )
        )
        assertEquals("HIGH", result.severity)
        assertEquals(88, result.riskScore)
        assertEquals("advance_fee_lottery_fraud", result.threatType)
    }

    @Test
    fun testCaseC_CredentialHarvestingLotteryFraud_HighRisk() = runBlocking {
        val input = "You won lottery. Share OTP to deposit money."

        // 1. SafetyBarrier
        val barrier = SafetyBarrier.analyzeMessage(input)
        assertEquals(Risk.HIGH, barrier.risk)
        assertTrue(
            "Must flag Signal.OTP",
            barrier.evidence.any { it.id == Signal.OTP }
        )

        // 2. Astra Classifier
        val finding = AstraDedicatedSecurityClassifier.classify(input)
        assertEquals("CONFIRMED_HIGH", finding.confidenceStatus)
        assertEquals(90, finding.modelScore)
        assertEquals("Prize Claim Credential & OTP Harvesting", finding.threatCategory)
        assertTrue(finding.credentialRequest)

        // 3. ExecuTorch Engine
        val engine = ExecuTorchLlamaEngine()
        val result = engine.analyzeThreat(
            ThreatInput(
                channel = ThreatChannel.VOIP_CALL,
                rawContent = input
            )
        )
        assertEquals("HIGH", result.severity)
        assertEquals(90, result.riskScore)
        assertEquals("credential_harvesting_lottery_fraud", result.threatType)
    }

    @Test
    fun testCaseD_EducationalScamAwareness_LowRisk() = runBlocking {
        val input = "Beware of callers claiming you won a lottery."

        val barrier = SafetyBarrier.analyzeMessage(input)
        assertEquals(Risk.LOW, barrier.risk)

        val finding = AstraDedicatedSecurityClassifier.classify(input)
        assertEquals("EDUCATIONAL_ADVISORY", finding.confidenceStatus)
        assertEquals(10, finding.modelScore)

        val engine = ExecuTorchLlamaEngine()
        val result = engine.analyzeThreat(
            ThreatInput(
                channel = ThreatChannel.VOIP_CALL,
                rawContent = input
            )
        )
        assertEquals("LOW", result.severity)
        assertEquals(10, result.riskScore)
        assertEquals("educational_cybersecurity_content", result.threatType)
    }

    @Test
    fun testCaseE_LegitimatePersonalContext_LowRisk() = runBlocking {
        val input = "Our school lottery draw awarded me ₹10,000 yesterday."

        val barrier = SafetyBarrier.analyzeMessage(input)
        assertEquals(Risk.LOW, barrier.risk)

        val finding = AstraDedicatedSecurityClassifier.classify(input)
        assertEquals("BENIGN_NORMAL", finding.confidenceStatus)
        assertEquals(15, finding.modelScore)

        val engine = ExecuTorchLlamaEngine()
        val result = engine.analyzeThreat(
            ThreatInput(
                channel = ThreatChannel.VOIP_CALL,
                rawContent = input
            )
        )
        assertEquals("LOW", result.severity)
        assertEquals(15, result.riskScore)
        assertEquals("benign_activity", result.threatType)
    }

    @Test
    fun testCaseF_HinglishPrizeSolicitation_NeedsReview() = runBlocking {
        val input = "Sir aapki das hazaar ki lottery lagi hai."

        val barrier = SafetyBarrier.analyzeMessage(input)
        assertEquals(Risk.REVIEW, barrier.risk)
        assertTrue(barrier.evidence.any { it.id == Signal.LOTTERY })

        val finding = AstraDedicatedSecurityClassifier.classify(input)
        assertEquals("CAUTION_REVIEW", finding.confidenceStatus)
        assertEquals(55, finding.modelScore)
        assertEquals("Unverified Lottery / Prize Solicitation", finding.threatCategory)

        val engine = ExecuTorchLlamaEngine()
        val result = engine.analyzeThreat(
            ThreatInput(
                channel = ThreatChannel.VOIP_CALL,
                rawContent = input
            )
        )
        assertEquals("MEDIUM", result.severity)
        assertEquals(55, result.riskScore)
        assertEquals("unverified_prize_solicitation", result.threatType)
    }
}
