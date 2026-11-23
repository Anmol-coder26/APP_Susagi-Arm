package com.susagi.safety

import com.susagi.intelligence.UnifiedThreatEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrizeContextTest {
    @Test fun clipTranscriptUsesReviewWithoutInventingADemand() = runBlocking {
        val clip = "hello sir toh have a lottery of 10 k"
        val result = UnifiedThreatEngine().analyzeCallTranscript(clip)
        assertEquals("MEDIUM", result.severity)
        assertEquals("unverified_prize", result.threatType)
        assertEquals(listOf("PRIZE_CLAIM"), result.detectedTactics)
    }

    @Test fun classifyRequestsAndControls() = runBlocking {
        val cases = listOf(
            "Sir, lottery..." to Risk.REVIEW,
            "Hello sir, you have won a lottery of ten thousand rupees." to Risk.REVIEW,
            "You won ten thousand rupees. First send five hundred to claim your prize." to Risk.HIGH,
            "Congratulations, you won ten thousand. Tell us the OTP from your bank to receive the money." to Risk.HIGH,
            "Our school conducted a lottery, and a student won ten thousand rupees." to Risk.LOW,
            "Don't believe anyone who says you won a lottery and asks you to pay a release fee." to Risk.LOW,
            "Sir aapki 10 hazaar ki lottery lagi hai, prize lene ke liye pehle processing fee bhejo." to Risk.HIGH,
            "आपकी लॉटरी लगी है, इनाम पाने के लिए फीस भेजें।" to Risk.HIGH,
            "Write regression tests for prize fraud and phishing education." to Risk.LOW,
            "Scam awareness training starts today. Tell me your bank OTP immediately." to Risk.HIGH
        )
        for ((input, expected) in cases) {
            val result = SafetyBarrier.analyzeMessage(input)
            assertEquals(input, expected, result.risk)
            if (expected != Risk.LOW) assertTrue(result.evidence.isNotEmpty())
        }
    }
}
