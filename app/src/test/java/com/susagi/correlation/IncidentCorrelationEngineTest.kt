package com.susagi.correlation

import com.susagi.edgeai.AnalysisSources
import com.susagi.edgeai.InferenceResult
import com.susagi.edgeai.ThreatChannel
import com.susagi.edgeai.ThreatSeverity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class IncidentCorrelationEngineTest {

    private lateinit var engine: IncidentCorrelationEngine

    @Before
    fun setUp() {
        engine = IncidentCorrelationEngine(correlationWindowMs = 3600_000L) // 1-hour window
    }

    @Test
    fun testCase1_scamMessageContainingSuspiciousUrl_ingestedCleanly() {
        val now = System.currentTimeMillis()
        val smsResult = InferenceResult(
            threatType = "fake_kyc_phishing",
            riskScore = 80,
            severity = "HIGH",
            detectedTactics = listOf("credential_harvesting"),
            evidence = listOf("Host: sbi-kyc-verify.top"),
            explanation = "Suspicious KYC message with phishing link.",
            recommendedActions = listOf("Do not click link"),
            analysisSource = AnalysisSources.LOCAL_RULES
        )
        val smsEvent = SecuritySignalEvent(
            eventId = "evt-sms-001",
            timestamp = now,
            channel = ThreatChannel.MESSAGE,
            rawContent = "Your SBI account is blocked! Update KYC at http://sbi-kyc-verify.top",
            extractedDomain = "sbi-kyc-verify.top",
            extractedIp = "185.220.101.5",
            threatIndicators = listOf("Account Block Threat"),
            assessment = smsResult
        )

        val incident = engine.ingestEvent(smsEvent)
        assertNull("Initial single-channel event should not trigger multi-channel correlation", incident)
        assertEquals("sbi-kyc-verify.top", smsEvent.normalizedDomain)
    }

    @Test
    fun testCase2_matchingNetworkEvent_correlatesIntoCriticalIncident() {
        val now = System.currentTimeMillis()

        // Event 1: SMS with domain sbi-kyc-verify.top
        val smsEvent = SecuritySignalEvent(
            eventId = "evt-sms-002",
            timestamp = now,
            channel = ThreatChannel.MESSAGE,
            rawContent = "Update KYC at http://sbi-kyc-verify.top",
            extractedDomain = "sbi-kyc-verify.top",
            extractedIp = "185.220.101.5",
            threatIndicators = listOf("Account Block Threat"),
            assessment = InferenceResult("fake_kyc", 80, "HIGH", listOf("phish"), listOf("Host: sbi-kyc-verify.top"), "Fake KYC", listOf("Block"), AnalysisSources.LOCAL_RULES)
        )
        engine.ingestEvent(smsEvent)

        // Event 2: Network flow to the exact same domain
        val netEvent = SecuritySignalEvent(
            eventId = "evt-net-002",
            timestamp = now + 15_000L,
            channel = ThreatChannel.NETWORK,
            rawContent = "Flow: outbound socket connection to 185.220.101.5:443",
            extractedDomain = "sbi-kyc-verify.top",
            extractedIp = "185.220.101.5",
            threatIndicators = listOf("Suspicious Outbound Connection"),
            assessment = InferenceResult("c2_beacon", 85, "HIGH", listOf("network"), listOf("Dest: 185.220.101.5"), "C2 Callback", listOf("Kill socket"), AnalysisSources.LOCAL_CLASSIFIER)
        )

        val eval = engine.evaluateCorrelation(smsEvent, netEvent)
        assertTrue("Matching domain must evaluate to isCorrelated = true", eval.isCorrelated)
        assertEquals("sbi-kyc-verify.top", eval.matchedEntity)
        assertTrue(eval.reason.contains("Shared verified destination domain"))

        val incident = engine.ingestEvent(netEvent)
        assertNotNull("Incident must be created upon matching network event", incident)
        assertEquals(ThreatSeverity.CRITICAL, incident!!.severity)
        assertTrue("Composite risk score should be elevated", incident.compositeRiskScore >= 90)
        assertEquals(setOf(ThreatChannel.MESSAGE, ThreatChannel.NETWORK), incident.participatingChannels)
    }

    @Test
    fun testCase3_nonmatchingNetworkEvent_doesNotCorrelate() {
        val now = System.currentTimeMillis()

        val smsEvent = SecuritySignalEvent(
            eventId = "evt-sms-003",
            timestamp = now,
            channel = ThreatChannel.MESSAGE,
            rawContent = "Electricity bill overdue. Pay at http://power-pay-bill.in",
            extractedDomain = "power-pay-bill.in",
            extractedIp = "192.0.2.1",
            threatIndicators = listOf("Utility Disconnect Threat"),
            assessment = InferenceResult("utility_scam", 75, "HIGH", emptyList(), emptyList(), "", emptyList(), AnalysisSources.LOCAL_RULES)
        )
        engine.ingestEvent(smsEvent)

        val netEvent = SecuritySignalEvent(
            eventId = "evt-net-003",
            timestamp = now + 5000L,
            channel = ThreatChannel.NETWORK,
            rawContent = "Outbound connection to 104.244.42.1 (twitter.com)",
            extractedDomain = "twitter.com",
            extractedIp = "104.244.42.1",
            threatIndicators = listOf("Social Media Flow"),
            assessment = InferenceResult("benign", 10, "LOW", emptyList(), emptyList(), "", emptyList(), AnalysisSources.LOCAL_RULES)
        )

        val eval = engine.evaluateCorrelation(smsEvent, netEvent)
        assertFalse("Nonmatching domain and IP must not correlate", eval.isCorrelated)
        assertTrue(eval.reason.contains("No shared verifiable entity"))

        val incident = engine.ingestEvent(netEvent)
        assertNull("Nonmatching event must not create a correlated incident", incident)
    }

    @Test
    fun testCase4_sameHostnameOutsideCorrelationWindow_doesNotCorrelate() {
        val t0 = 1000_000L

        val event1 = SecuritySignalEvent(
            eventId = "evt-time-1",
            timestamp = t0,
            channel = ThreatChannel.MESSAGE,
            rawContent = "Check account at http://fraud-portal.com",
            extractedDomain = "fraud-portal.com",
            extractedIp = "203.0.113.10",
            threatIndicators = listOf("Suspicious Link"),
            assessment = InferenceResult("scam", 70, "HIGH", emptyList(), emptyList(), "", emptyList(), AnalysisSources.LOCAL_RULES)
        )
        engine.ingestEvent(event1)

        // Event arrives 2 hours later (> 1 hour window)
        val event2 = SecuritySignalEvent(
            eventId = "evt-time-2",
            timestamp = t0 + 2 * 3600_000L,
            channel = ThreatChannel.URL,
            rawContent = "http://fraud-portal.com/login",
            extractedDomain = "fraud-portal.com",
            extractedIp = "203.0.113.10",
            threatIndicators = listOf("Phishing Target"),
            assessment = InferenceResult("phish", 80, "HIGH", emptyList(), emptyList(), "", emptyList(), AnalysisSources.LOCAL_RULES)
        )

        val eval = engine.evaluateCorrelation(event1, event2)
        assertFalse("Events outside correlation window must not correlate", eval.isCorrelated)
        assertTrue(eval.reason.contains("outside documented correlation window"))

        val incident = engine.ingestEvent(event2)
        assertNull("Expired events must not form an incident", incident)
    }

    @Test
    fun testCase5_unrelatedEventsWithSimilarDescriptions_doesNotCorrelate() {
        val now = System.currentTimeMillis()

        // Two events both mention "urgent KYC account suspension" and "CBI arrest", but target entirely different entities
        val eventA = SecuritySignalEvent(
            eventId = "evt-kyc-bankA",
            timestamp = now,
            channel = ThreatChannel.MESSAGE,
            rawContent = "URGENT: SBI KYC suspended by CBI order. Verify at http://fake-sbi-portal.top",
            extractedDomain = "fake-sbi-portal.top",
            extractedIp = "198.51.100.1",
            threatIndicators = listOf("KYC Suspension", "CBI Coercion"),
            assessment = InferenceResult("kyc_phish", 85, "HIGH", emptyList(), emptyList(), "Urgent KYC CBI", emptyList(), AnalysisSources.LOCAL_RULES)
        )

        val eventB = SecuritySignalEvent(
            eventId = "evt-kyc-bankB",
            timestamp = now + 1000L,
            channel = ThreatChannel.URL,
            rawContent = "http://hdfc-account-verify.online/login",
            extractedDomain = "hdfc-account-verify.online",
            extractedIp = "203.0.113.88",
            threatIndicators = listOf("KYC Suspension", "CBI Coercion"), // Similar words/tags!
            assessment = InferenceResult("kyc_phish", 85, "HIGH", emptyList(), emptyList(), "Urgent KYC CBI", emptyList(), AnalysisSources.LOCAL_RULES)
        )

        val eval = engine.evaluateCorrelation(eventA, eventB)
        assertFalse("Events with similar words but different domains/IPs MUST NOT correlate", eval.isCorrelated)
        assertTrue(eval.reason.contains("No shared verifiable entity"))
    }

    @Test
    fun testCase6_incompleteSourceEvidence_safelyRejected() {
        val now = System.currentTimeMillis()

        val eventIncompleteA = SecuritySignalEvent(
            eventId = "evt-inc-1",
            timestamp = now,
            channel = ThreatChannel.MESSAGE,
            rawContent = "Hello, call me back immediately.",
            extractedDomain = null,
            extractedIp = null,
            threatIndicators = emptyList(),
            assessment = InferenceResult("spam", 20, "LOW", emptyList(), emptyList(), "", emptyList(), AnalysisSources.LOCAL_RULES)
        )

        val eventIncompleteB = SecuritySignalEvent(
            eventId = "evt-inc-2",
            timestamp = now + 500L,
            channel = ThreatChannel.NETWORK,
            rawContent = "Outbound socket flow",
            extractedDomain = null,
            extractedIp = null,
            threatIndicators = emptyList(),
            assessment = InferenceResult("flow", 15, "LOW", emptyList(), emptyList(), "", emptyList(), AnalysisSources.LOCAL_RULES)
        )

        val eval = engine.evaluateCorrelation(eventIncompleteA, eventIncompleteB)
        assertFalse("Incomplete source evidence must not correlate", eval.isCorrelated)
        assertTrue(eval.reason.contains("Incomplete source evidence"))
    }
}
