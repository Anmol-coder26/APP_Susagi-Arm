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

    @Test fun directive21SemanticCombinationsAndBenignControls() = runBlocking {
        val harmfulCombinations = listOf(
            Triple(Signal.ARREST, "Customs found drugs in your parcel; pay us to avoid detention", "Avoid detention parcel extortion"),
            Triple(Signal.ARREST, "Your bank is under investigation. Do not speak to a lawyer and wire your balance.", "Investigation isolation wire"),
            Triple(Signal.ARREST, "सीबीआई जांच शुरू हो गई है। किसी वकील से संपर्क न करें और अपने पैसे सुरक्षा खाते में ट्रांसफर करें।", "Hindi CBI isolation"),
            Triple(Signal.ARREST, "कस्टम अधिकारी बोल रहा हूँ: पार्सल में नशीले पदार्थ मिले हैं, गिरफ्तारी से बचने के लिए जुर्माना भरें", "Hindi customs drugs"),
            Triple(Signal.ADVANCE_FEE, "Pay the withdrawal tax to unlock your portfolio balance", "Withdrawal tax unlock"),
            Triple(Signal.ADVANCE_FEE, "Withdrawals are frozen until you pay our release fee", "Frozen withdrawals release fee"),
            Triple(Signal.ADVANCE_FEE, "आपका ट्रेडिंग खाता फ्रीज कर दिया गया है। बैलेंस निकालने के लिए टैक्स भरें।", "Hindi frozen trading account fee"),
            Triple(Signal.GUARANTEED_RETURN, "We will double your deposit overnight; send money now", "Overnight double deposit"),
            Triple(Signal.GUARANTEED_RETURN, "हम 24 घंटे में आपके पैसे डबल कर देंगे, तुरंत रुपये भेजें।", "Hindi double money scheme"),
            Triple(Signal.ADVANCE_FEE, "To verify your account transfer INR 5000 now", "Account verification fee")
        )

        for ((expectedSignal, message, label) in harmfulCombinations) {
            val result = SafetyBarrier.analyzeMessage(message)
            assertEquals("Expected HIGH risk for: $label ('$message')", Risk.HIGH, result.risk)
            assertTrue("Expected signal ${expectedSignal.name} for: $label", result.evidence.any { it.id == expectedSignal })
        }

        val benignControls = listOf(
            "Market returns fluctuate and principal is at risk",
            "The fund published its monthly holdings",
            "Mutual funds are subject to market risks, read all scheme related documents carefully.",
            "Your scheduled SIP installment was collected",
            "Bank never asks you to disclose your password or PIN.",
            "SBI warns customers: do not share your OTP with anyone.",
            "अपना पासवर्ड कभी किसी को न बताएं।",
            "Police published an advisory on digital arrest frauds.",
            "Risk-free profit is a warning sign in investment advertisements.",
            "Officers will never demand a verification bond over chat.",
            "Your account was credited with INR 2,500 salary today.",
            "The brokerage returned your unused margin.",
            "Electricity bill payment of Rs 450 received successfully. Receipt #12345.",
            "Government notice regarding traffic challan payment rules published on official portal.",
            "सीबीआई की वार्षिक रिपोर्ट आज जारी हुई",
            "Dad this is my replacement SIM, save my number.",
            "Mum this is my new number, see you tonight.",
            "पिताजी ने कहा कि नया फोन खरीद लिया है, शाम को बात करेंगे।"
        )

        for (control in benignControls) {
            val result = SafetyBarrier.analyzeMessage(control)
            assertEquals("Expected LOW risk for benign control: '$control'", Risk.LOW, result.risk)
            assertEquals("Expected OFFER_OPEN for benign control: '$control'", Action.OFFER_OPEN, result.action)
        }
    }
}
