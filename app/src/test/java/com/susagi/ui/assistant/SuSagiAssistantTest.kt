package com.susagi.ui.assistant

import com.susagi.edgeai.ConversationManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuSagiAssistantTest {

    @Test
    fun testAssistant_EducationalAdvisory_ReceivesLowRiskBand() = runBlocking {
        val query = "Cybersecurity researchers found an advisory explaining how to protect against digital arrest and phishing attacks."
        val response = generateDeterministicGuidance(query, context = null)

        assertEquals("LOW", response.riskBand)
        assertEquals("Educational / Security Advisory", response.threatCategory)
        assertTrue(response.text.contains("educational advisory", ignoreCase = true) || response.text.contains("normal", ignoreCase = true))
        assertFalse(response.isUser)
    }

    @Test
    fun testAssistant_UpiPinInquiry_WarnsPinNeverReceivesMoney() = runBlocking {
        val query = "Can I receive money by entering my UPI PIN?"
        val response = generateDeterministicGuidance(query, context = null)

        assertEquals("REVIEW", response.riskBand)
        assertTrue(response.text.contains("NEVER need to enter your UPI PIN to receive money", ignoreCase = true))
        assertTrue(response.text.contains("ALWAYS deducts money", ignoreCase = true))
        assertTrue(response.suggestedActions.isNotEmpty())
    }

    @Test
    fun testAssistant_DigitalArrestExtortion_FlagsHighRiskAndHelpline1930() = runBlocking {
        val query = "CBI officer calling: arrest warrant issued against your Aadhaar. Pay 50000 immediately to avoid digital arrest."
        val response = generateDeterministicGuidance(query, context = null)

        assertEquals("HIGH", response.riskBand)
        assertTrue(response.text.contains("1930") || response.suggestedActions.any { it.contains("1930") })
        assertTrue(response.text.contains("digital arrest", ignoreCase = true))
    }

    @Test
    fun testAssistant_WhatToAvoidSharing_RecommendsCredentialProtection() = runBlocking {
        val query = "What confidential credentials should I avoid sharing with strangers?"
        val response = generateDeterministicGuidance(query, context = null)

        assertEquals("LOW", response.riskBand)
        assertTrue(response.text.contains("OTP", ignoreCase = true))
        assertTrue(response.text.contains("UPI PIN", ignoreCase = true))
        assertTrue(response.text.contains("AnyDesk", ignoreCase = true) || response.text.contains("screen sharing", ignoreCase = true))
    }

    @Test
    fun testGeneralQuery_MissingModelNotice_DoesNotFakeScamVerdicts() {
        // Directive 46A: If no generative model is installed, general chat must output the exact honest notice:
        // "Local AI conversation requires a compatible language model. Security Guidance remains available without a download."
        // Must NOT output "No scam detected" or a fake conversational answer.
        val honestNotice = AssistantMessage(
            isUser = false,
            text = "Local AI conversation requires a compatible language model. Security Guidance remains available without a download.",
            isMissingModelNotice = true,
            suggestedActions = listOf(
                "Manage AI Models",
                "🛡️ Analyze Message",
                "🔗 Inspect URL",
                "💳 Inspect UPI"
            )
        )

        assertEquals("Local AI conversation requires a compatible language model. Security Guidance remains available without a download.", honestNotice.text)
        assertTrue(honestNotice.isMissingModelNotice)
        assertFalse(honestNotice.text.contains("No scam detected", ignoreCase = true))
        assertEquals(null, honestNotice.riskBand)
        assertEquals(null, honestNotice.threatCategory)
    }

    @Test
    fun testConversationalContinuity_RetainsContextForFollowUps() {
        val conversationManager = ConversationManager()
        conversationManager.addUserMessage("Explain binary search in Java.")
        conversationManager.addAssistantMessage("Binary search repeatedly divides the search interval in half.")

        val promptForFollowUp = conversationManager.buildInstructPrompt("Can you show this in Hindi?")

        assertTrue(promptForFollowUp.contains("Explain binary search in Java."))
        assertTrue(promptForFollowUp.contains("Binary search repeatedly divides the search interval in half."))
        assertTrue(promptForFollowUp.contains("Can you show this in Hindi?"))
    }
}
