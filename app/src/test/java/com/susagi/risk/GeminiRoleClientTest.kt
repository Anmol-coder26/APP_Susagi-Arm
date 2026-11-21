package com.susagi.risk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeminiRoleClientTest {

    private lateinit var client: GeminiRoleClient

    @Before
    fun setUp() {
        client = GeminiRoleClient(apiKey = "")
    }

    @Test
    fun testParseGeminiResponse_validStructuredJson() {
        val jsonPayload = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  {
                    "text": "{\"risk_score\": 92, \"victim_role\": \"local\", \"scammer_role\": \"remote\", \"role_confidence\": 0.96, \"tactics\": [\"digital_arrest\", \"authority_impersonation\"], \"explanation\": \"Caller impersonates police and demands immediate transfer under threat of arrest.\"}"
                  }
                ]
              }
            }
          ]
        }
        """.trimIndent()

        val result = client.parseGeminiResponse(jsonPayload)

        assertEquals(92, result.riskScore)
        assertEquals("local", result.victimRole)
        assertEquals("remote", result.scammerRole)
        assertEquals(0.96, result.roleConfidence, 0.01)
        assertEquals("GEMINI_CLOUD", result.source)
        assertFalse(result.isFallback)
        assertTrue(result.tactics.contains("digital_arrest"))
        assertTrue(result.tactics.contains("authority_impersonation"))
        assertTrue(result.isScamDetected)
    }

    @Test
    fun testParseGeminiResponse_withMarkdownCodeFences() {
        val jsonPayload = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  {
                    "text": "```json\n{\"risk_score\": 80, \"victim_role\": \"local\", \"scammer_role\": \"remote\", \"role_confidence\": 0.90, \"tactics\": [\"financial_coercion\"], \"explanation\": \"Demanding bank details.\"}\n```"
                  }
                ]
              }
            }
          ]
        }
        """.trimIndent()

        val result = client.parseGeminiResponse(jsonPayload)
        assertEquals(80, result.riskScore)
        assertEquals("remote", result.scammerRole)
        assertEquals("local", result.victimRole)
        assertEquals("GEMINI_CLOUD", result.source)
    }

    @Test
    fun testHeuristicEvaluation_detectsDigitalArrestThreat() {
        val dialogue = """
            [REMOTE (Caller)]: This is Inspector Sharma from Mumbai Police Crime Branch.
            [REMOTE (Caller)]: A warrant has been issued against you. You are under digital arrest.
            [LOCAL (User)]: Sir I did not do anything!
            [REMOTE (Caller)]: Do not disconnect this call. Transfer funds to our safe account for verification immediately.
        """.trimIndent()

        val result = client.evaluateHeuristic(dialogue)

        assertTrue("Expected scam score >= 60, got ${result.riskScore}", result.riskScore >= 60)
        assertEquals("remote", result.scammerRole)
        assertEquals("local", result.victimRole)
        assertEquals("LOCAL_HEURISTIC", result.source)
        assertTrue(result.tactics.contains("authority_impersonation"))
        assertTrue(result.isScamDetected)
    }

    @Test
    fun testHeuristicEvaluation_benignConversationIsSafe() {
        val dialogue = """
            [LOCAL (User)]: Hi Rahul, are we meeting for lunch at 1 PM?
            [REMOTE (Caller)]: Hey! Yes, let's meet at the cafe near the office.
            [LOCAL (User)]: Sounds great, see you there!
        """.trimIndent()

        val result = client.evaluateHeuristic(dialogue)

        assertTrue("Expected safe score < 30, got ${result.riskScore}", result.riskScore < 30)
        assertEquals("none", result.scammerRole)
        assertEquals("none", result.victimRole)
        assertEquals("LOCAL_HEURISTIC", result.source)
        assertTrue(result.isSafe)
    }

    @Test
    fun testHeuristicEvaluation_fallbackMode_doesNotProduceMisleadingSafeResult() {
        val benignDialogue = "[LOCAL (User)]: Dinner is ready."
        val result = client.evaluateHeuristic(benignDialogue, isFallback = true, failureReason = "HTTP 403 Forbidden")

        // In fallback mode, must not claim benign conversation is verified safe by Cloud AI
        assertTrue(result.explanation.contains("[Degraded / Cloud AI Unavailable]"))
        assertTrue(result.explanation.contains("HTTP 403 Forbidden"))
        assertTrue("Role confidence should be degraded", result.roleConfidence <= 0.50)
        assertEquals("LOCAL_HEURISTIC", result.source)
        assertTrue(result.isFallback)
    }

    @Test
    fun testHeuristicEvaluation_actualTask4Dialogue_flagsCybercrimeExtortion() {
        val dialogue = """
            [LOCAL (User)]: Hello, how are you?
            [REMOTE (Caller)]: I am calling from cybercrime police. Do not disconnect. Transfer 50,000 rupees immediately.
        """.trimIndent()

        val result = client.evaluateHeuristic(dialogue, isFallback = true, failureReason = "Cloud fallback")

        assertTrue("Expected threat score >= 75, got ${result.riskScore}", result.riskScore >= 75)
        assertEquals("remote", result.scammerRole)
        assertEquals("local", result.victimRole)
        assertEquals("LOCAL_HEURISTIC", result.source)
        assertTrue(result.isFallback)
        assertTrue(result.tactics.contains("authority_impersonation"))
        assertTrue(result.tactics.contains("financial_coercion"))
        assertTrue(result.tactics.contains("intimidation"))
        assertTrue(result.isScamDetected)
    }
}
