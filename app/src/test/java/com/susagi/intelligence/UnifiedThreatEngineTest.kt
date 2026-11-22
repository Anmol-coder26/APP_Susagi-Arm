package com.susagi.intelligence

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UnifiedThreatEngineTest {

    private lateinit var engine: UnifiedThreatEngine

    @Before
    fun setUp() {
        engine = UnifiedThreatEngine()
    }

    @Test fun sharedEngineCarriesLocalEvidenceAcrossBothEntryPoints() = runBlocking {
        val link = engine.analyzeUrl("https://ax1sbank.example/login")
        assertTrue(link.isDangerous)
        assertTrue("brand" in link.detectedTactics)
        val message = engine.analyzeMessage("Digital arrest: do not hang up.")
        assertTrue(message.isDangerous)
        assertTrue("arrest" in message.detectedTactics)
    }

    @Test
    fun testUrlAnalysis_maliciousApk_highRisk() = runBlocking {
        val result = engine.analyzeUrl("https://download-center-sbi.top/update_kyc_app.apk")
        assertTrue("Expected score >= 70 for direct APK download, got ${result.riskScore}", result.riskScore >= 70)
        assertEquals("HIGH", result.severity)
        assertTrue("Should detect APK sideload tactic", result.detectedTactics.any { it.contains("apk") || it.contains("malware") })
        assertNotNull(result.explanation)
        assertTrue("Should provide actionable recommendations", result.recommendedActions.isNotEmpty())
    }

    @Test
    fun testUrlAnalysis_phishingElectricityKyc_highRisk() = runBlocking {
        val result = engine.analyzeUrl("https://electricity-bill-pay.com/update-kyc")
        assertTrue("Expected score >= 70 for phishing URL, got ${result.riskScore}", result.riskScore >= 70)
        assertEquals("HIGH", result.severity)
        assertTrue("Should flag credential harvesting", result.detectedTactics.any { it.contains("credential") || it.contains("coercion") })
    }

    @Test
    fun testUrlAnalysis_cleanDomain_lowRisk() = runBlocking {
        val result = engine.analyzeUrl("https://google.com/search?q=weather")
        assertTrue("Expected score < 40 for clean domain, got ${result.riskScore}", result.riskScore < 40)
        assertEquals("LOW", result.severity)
        assertTrue(result.isClean)
    }

    @Test
    fun testMessageAnalysis_digitalArrestExtortion_highRisk() = runBlocking {
        val message = "FIR registered against you by cyber crime police. You are under digital arrest. Transfer fifty thousand rupees immediately or face arrest."
        val result = engine.analyzeMessage(message)
        assertTrue("Expected score >= 85 for digital arrest extortion, got ${result.riskScore}", result.riskScore >= 85)
        assertEquals("HIGH", result.severity)
        assertEquals("digital_arrest_extortion", result.threatType)
        assertTrue("Must include authority impersonation tactic", result.detectedTactics.contains("authority_impersonation"))
        assertTrue("Explanation must mention Digital Arrest or police", result.explanation.contains("Digital Arrest") || result.explanation.contains("police"))
        assertTrue("Must recommend contacting cybercrime helpline 1930", result.recommendedActions.any { it.contains("1930") })
    }

    @Test
    fun testMessageAnalysis_fakeKycUpdate_highRisk() = runBlocking {
        val message = "Dear SBI customer, your net banking account is blocked. Update your PAN card immediately on this link."
        val result = engine.analyzeMessage(message)
        assertTrue("Expected score >= 80 for fake KYC, got ${result.riskScore}", result.riskScore >= 80)
        assertEquals("HIGH", result.severity)
        assertEquals("fake_kyc_banking_fraud", result.threatType)
        assertTrue(result.recommendedActions.any { it.contains("KYC") || it.contains("bank") })
    }

    @Test
    fun testMessageAnalysis_electricityDisconnection_highRisk() = runBlocking {
        val message = "Dear Consumer, your electricity power cut will be done tonight at 9:30 PM from electricity office because your bill was not updated. Call officer immediately."
        val result = engine.analyzeMessage(message)
        assertTrue("Expected score >= 80 for power cut scam, got ${result.riskScore}", result.riskScore >= 80)
        assertEquals("HIGH", result.severity)
        assertEquals("electricity_disconnection_fraud", result.threatType)
    }

    @Test
    fun testMessageAnalysis_benignFamilyText_lowRisk() = runBlocking {
        val message = "Hi, I am leaving office now. Please keep the dinner ready."
        val result = engine.analyzeMessage(message)
        assertTrue("Expected score < 40 for harmless message, got ${result.riskScore}", result.riskScore < 40)
        assertEquals("LOW", result.severity)
        assertTrue(result.isClean)
    }

    @Test
    fun testMessageAnalysis_upiPinFraud_highRisk() = runBlocking {
        val message = "Congratulations! You won ₹5000 cashback. Enter UPI PIN to receive money in your bank account."
        val result = engine.analyzeMessage(message)
        assertTrue("Expected score >= 80 for UPI PIN fraud, got ${result.riskScore}", result.riskScore >= 80)
        assertEquals("HIGH", result.severity)
        assertEquals("upi_pin_fraud", result.threatType)
        assertTrue(result.explanation.contains("PIN"))
    }

    @Test
    fun testMessageAnalysis_courierCustomsFraud_highRisk() = runBlocking {
        val message = "Your international parcel is held at customs warehouse due to unpaid duty. Pay clearance fee immediately."
        val result = engine.analyzeMessage(message)
        assertTrue("Expected score >= 75 for customs courier fraud, got ${result.riskScore}", result.riskScore >= 75)
        assertEquals("HIGH", result.severity)
        assertEquals("courier_customs_fraud", result.threatType)
    }

    @Test
    fun testMessageAnalysis_benignBankCredit_lowRisk() = runBlocking {
        val message = "Dear Customer, your A/C XX1234 is credited with INR 500.00 on 21-Nov-2026 by UPI. Avl Bal: INR 15,240.50."
        val result = engine.analyzeMessage(message)
        assertTrue("Expected score < 40 for bank credit SMS, got ${result.riskScore}", result.riskScore < 40)
        assertEquals("LOW", result.severity)
        assertTrue(result.isClean)
    }

    @Test
    fun testMessageAnalysis_ambiguousUrgentPersonal_lowRisk() = runBlocking {
        val message = "Please call me back immediately, it is very urgent regarding our team project."
        val result = engine.analyzeMessage(message)
        assertTrue("Expected score < 40 for personal urgent text, got ${result.riskScore}", result.riskScore < 40)
        assertEquals("LOW", result.severity)
    }

    @Test
    fun testUrlAnalysis_userinfoDeception_highRisk() = runBlocking {
        val url = "https://onlinesbi.sbi@verification-portal-kyc.top/login"
        val result = engine.analyzeUrl(url)
        assertTrue("Expected score >= 70 for userinfo impersonation spoof, got ${result.riskScore}", result.riskScore >= 70)
        assertEquals("HIGH", result.severity)
        assertTrue(result.evidence.any { it.contains("IMPERSONATION") || it.contains("UserInfo") || it.contains("Host") })
    }

    @Test
    fun testUrlAnalysis_rawIpHost_highRisk() = runBlocking {
        val url = "http://198.51.100.42/auth/login.php"
        val result = engine.analyzeUrl(url)
        assertTrue("Expected score >= 70 for raw IP host URL, got ${result.riskScore}", result.riskScore >= 70)
        assertEquals("HIGH", result.severity)
        assertTrue(result.evidence.any { it.contains("IP") })
    }

    @Test
    fun testCallTranscriptAnalysis_policeCoercion_highRisk() = runBlocking {
        val transcript = "[REMOTE]: I am calling from cyber crime police. You are under digital arrest. Do not disconnect and transfer fifty thousand rupees immediately."
        val result = engine.analyzeCallTranscript(transcript)
        assertTrue("Expected score >= 85 for live call extortion, got ${result.riskScore}", result.riskScore >= 85)
        assertEquals("HIGH", result.severity)
        assertTrue("Must detect financial coercion", result.detectedTactics.contains("financial_coercion"))
    }

    @Test
    fun testSafetyBarrierToThreatEngine_highVerdictNeverShowsBenignAdvice() = runBlocking {
        val dangerousScenarios = listOf(
            "FIR registered against you by police. You are under digital arrest. Transfer funds now.",
            "Dear SBI customer, your net banking account is blocked. Update your PAN card immediately on this link: https://fake-sbi-portal.top/kyc",
            "To receive your cashback of INR 5000, scan this QR code and enter UPI PIN.",
            "Our bank agent needs you to download TeamViewer for KYC support.",
            "Mum this is my new number please send money right now to emergency account."
        )

        for (scenario in dangerousScenarios) {
            val result = engine.analyzeMessage(scenario)
            assertEquals("HIGH", result.severity)
            assertTrue("High threat verdict must mark isDangerous=true", result.isDangerous)
            assertFalse("High threat verdict must NEVER mark isClean=true", result.isClean)
            assertFalse("High threat explanation must not claim no threats found",
                result.explanation.contains("No known threat") || result.explanation.contains("safe to open"))
            assertTrue("Must include actionable protective recommendations", result.recommendedActions.isNotEmpty())
            assertFalse("Must never provide benign navigation advice on HIGH risk",
                result.recommendedActions.any { it.contains("Safe to proceed", ignoreCase = true) || it.contains("No threat detected", ignoreCase = true) })
        }
    }

    @Test
    fun testSafetyBarrierToThreatEngine_lowVerdictNeverClaimsVerifiedSafe() = runBlocking {
        val benignScenarios = listOf(
            "Hi Mom, I will reach home by 7 PM today.",
            "Please find the meeting notes attached for tomorrow's discussion.",
            "Never share your OTP with anyone calling from the bank."
        )

        for (scenario in benignScenarios) {
            val result = engine.analyzeMessage(scenario)
            assertEquals("LOW", result.severity)
            assertTrue("Clean verdict must mark isClean=true", result.isClean)
            assertFalse("Clean verdict must not claim absolute verified safety",
                result.explanation.contains("verified safe", ignoreCase = true) ||
                result.recommendedActions.any { it.contains("verified safe", ignoreCase = true) })
        }
    }

    @Test
    fun testDiagnostics_returnsExecuTorchHardwareMetadata() {
        val diag = engine.getDiagnostics()
        assertEquals("ExecuTorch-Llama-Arm", diag.engineName)
        assertEquals("Llama-3.2-1B-Instruct-Q4", diag.modelArchitecture)
        assertNotNull(diag.targetHardware)
        assertTrue(diag.targetHardware.contains("Arm") || diag.targetHardware.contains("CPU"))
    }

    @Test
    fun testUnifiedThreatEngine_screenCredentialAndRefundFeeIntegration() = runBlocking {
        val screenResult = engine.analyzeMessage("Share your screen while entering your UPI PIN")
        assertEquals("HIGH", screenResult.severity)
        assertTrue(screenResult.isDangerous)
        assertTrue(screenResult.detectedTactics.contains("credentials") || screenResult.detectedTactics.contains("screen_credential"))
        assertTrue(screenResult.evidence.any { it.contains("share your screen while entering a financial credential", ignoreCase = true) })

        val refundResult = engine.analyzeMessage("Pay a processing fee to receive your refund")
        assertEquals("HIGH", refundResult.severity)
        assertTrue(refundResult.isDangerous)
        assertTrue(refundResult.detectedTactics.contains("advance_fee") || refundResult.detectedTactics.contains("refund_fee"))
        assertTrue(refundResult.evidence.any { it.contains("upfront fee to obtain a refund", ignoreCase = true) })

        val benignScreen = engine.analyzeMessage("Never share your screen while entering your PIN")
        assertEquals("LOW", benignScreen.severity)
        assertTrue(benignScreen.isClean)

        val benignRefund = engine.analyzeMessage("Never pay a processing fee to receive your refund")
        assertEquals("LOW", benignRefund.severity)
        assertTrue(benignRefund.isClean)

        val pastCredit = engine.analyzeMessage("Your processing fee refund was credited")
        assertEquals("LOW", pastCredit.severity)
        assertTrue(pastCredit.isClean)
    }
}
