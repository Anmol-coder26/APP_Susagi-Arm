package com.susagi.safety

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ContextPairsTest {
    @Test fun quotedAwarenessCannotHideFollowingInstructions() = runBlocking {
        val warning = "Bank tip: scammers say “send your OTP”; end the call."
        assertEquals(Risk.LOW, SafetyBarrier.analyzeMessage(warning).risk)
        assertTrue(SafetyBarrier.analyzeMessage("$warning Now forward your ATM PIN.").evidence.any { it.id == Signal.OTP })
    }

    @Test fun contrastStatementsDoNotCauseAdvanceFeeFalsePositives() = runBlocking {
        val benignContrastCases = listOf(
            "Do not pay the caller; contact our support desk.",
            "Never pay a processing fee, instead report the incident to cyber police.",
            "Do not pay the caller, instead verify your account in the official bank app.",
            "Never send money to unverified numbers; visit your local branch instead.",
            "Scam alert: do not pay advance fees to callers claiming to offer lottery prizes."
        )
        for (message in benignContrastCases) {
            val result = SafetyBarrier.analyzeMessage(message)
            assertEquals("Expected LOW for: '$message'", Risk.LOW, result.risk)
            assertFalse("Must not trigger ADVANCE_FEE for: '$message'", result.evidence.any { it.id == Signal.ADVANCE_FEE })
        }
    }

    @Test fun demandsAndRelatedBenignCommunications() = runBlocking {
        val pairs = listOf(
            Triple(Signal.OTP, "Forward your authentication code to reverse the transaction", "Never forward your authentication code"),
            Triple(Signal.OTP, "Send your net-banking password to our agent", "Your net-banking password was changed"),
            Triple(Signal.FAMILY_PAYMENT, "Dad this is my replacement SIM, cannot take calls, send money", "Dad this is my replacement SIM, save my number"),
            Triple(Signal.ADVANCE_FEE, "Pay a salary release fee to our recruiter", "Your recruiter confirmed the salary today"),
            Triple(Signal.GUARANTEED_RETURN, "Transfer funds for risk-free profit on this investment", "This investment does not promise risk-free profit")
        )
        for ((signal, demand, control) in pairs) {
            assertTrue(signal.name, SafetyBarrier.analyzeMessage(demand).evidence.any { it.id == signal })
            assertEquals(control, Risk.LOW, SafetyBarrier.analyzeMessage(control).risk)
        }
    }
}
