package com.guardian.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticAnalyzerTest {

    @Test
    fun testJsonParser_FullValidGeminiResponse() {
        val sampleJson = """
        {
          "risk_score": 92,
          "engines": {
            "pretext_legitimacy": {
              "detected": true,
              "type": "police",
              "confidence": 0.95,
              "summary": "Police / CBI authority pretext"
            },
            "intent_risk": {
              "detected": true,
              "action": "transfer",
              "confidence": 0.90,
              "summary": "Immediate money transfer demand"
            },
            "psychological_pressure": {
              "detected": true,
              "tactic": "fear",
              "confidence": 0.98,
              "summary": "Digital arrest and prison threat"
            },
            "information_asymmetry": {
              "detected": true,
              "evidence": "Demands security deposit without valid judicial warrant"
            },
            "zero_shot_variant": {
              "detected": true,
              "variant_name": "Digital Arrest Extortion Scheme",
              "explanation": "Known cybercrime pattern impersonating law enforcement"
            }
          },
          "explanation_en": "Caller is impersonating police demanding money under fake arrest threat.",
          "explanation_hi": "कॉलर फर्जी गिरफ्तारी की धमकी देकर पैसे की मांग कर रहा है।",
          "highlighted_phrases": ["digital arrest", "CBI", "transfer money"]
        }
        """.trimIndent()

        val report = RiskReportJsonParser.parse(sampleJson, isOffline = false, latencyMs = 850L)

        assertEquals(92, report.riskScore)
        assertEquals(RiskStatus.High, report.status)
        assertTrue(report.engines.pretextLegitimacy.detected)
        assertEquals("police", report.engines.pretextLegitimacy.type)
        assertTrue(report.engines.intentRisk.detected)
        assertEquals("transfer", report.engines.intentRisk.action)
        assertTrue(report.engines.psychologicalPressure.detected)
        assertEquals("fear", report.engines.psychologicalPressure.tactic)
        assertTrue(report.engines.informationAsymmetry.detected)
        assertTrue(report.engines.zeroShotVariant.detected)
        assertEquals("Digital Arrest Extortion Scheme", report.engines.zeroShotVariant.variantName)
        assertEquals(3, report.highlightedPhrases.size)
        assertFalse(report.isOffline)
        assertEquals(850L, report.latencyMs)
        assertEquals(3, report.topSignals.size)
    }

    @Test
    fun testJsonParser_CappedAt98PercentGuardrail() {
        val overflowJson = """
        {
          "risk_score": 100,
          "engines": {},
          "explanation_en": "Over-confident score",
          "explanation_hi": "परीक्षण",
          "highlighted_phrases": []
        }
        """.trimIndent()

        val report = RiskReportJsonParser.parse(overflowJson)
        assertEquals(98, report.riskScore) // Verified capped at 98%
    }

    @Test
    fun testJsonParser_MarkdownFencesHandling() {
        val wrappedJson = """
        ```json
        {
          "risk_score": 75,
          "engines": {
            "pretext_legitimacy": {"detected": true, "type": "bank", "confidence": 0.85, "summary": "Bank pretext"}
          },
          "explanation_en": "Bank pretext detected",
          "explanation_hi": "बैंक बहाना पहचाना गया",
          "highlighted_phrases": ["bank"]
        }
        ```
        """.trimIndent()

        val report = RiskReportJsonParser.parse(wrappedJson)
        assertEquals(75, report.riskScore)
        assertTrue(report.engines.pretextLegitimacy.detected)
    }

    @Test
    fun testKeywordScorer_PureKotlinOffline_PoliceScam() {
        val transcript = "Hello I am CBI police officer. You are under digital arrest. Transfer money immediately."
        val report = KeywordScorer.score(transcript)

        assertTrue(report.riskScore >= 60)
        assertEquals(RiskStatus.High, report.status)
        assertTrue(report.engines.pretextLegitimacy.detected)
        assertEquals("police", report.engines.pretextLegitimacy.type)
        assertTrue(report.engines.intentRisk.detected)
        assertTrue(report.engines.psychologicalPressure.detected)
        assertTrue(report.isOffline)
    }

    @Test
    fun testKeywordScorer_PureKotlinOffline_BenignCall() {
        val transcript = "Hey mom, I am heading to the supermarket to buy some milk and vegetables."
        val report = KeywordScorer.score(transcript)

        assertEquals(0, report.riskScore)
        assertEquals(RiskStatus.Low, report.status)
        assertFalse(report.engines.pretextLegitimacy.detected)
        assertFalse(report.engines.intentRisk.detected)
    }

    @Test
    fun testKeywordScorer_HarmlessTechnicalContent_ReceivesLowRisk() {
        // Harmless technical advisory containing security keywords (vulnerability, advisory, protect against, money transfer)
        val technicalAdvisory = "Security researchers found a new vulnerability in banking apps. In this advisory, we analyze threat reports and provide guidelines on how to avoid phishing and protect against unauthorized money transfer."
        val report = KeywordScorer.score(technicalAdvisory)

        assertEquals(RiskStatus.Low, report.status)
        assertTrue("Risk score must be <= 15 on educational content but was ${report.riskScore}", report.riskScore <= 15)
        assertFalse(report.engines.pretextLegitimacy.detected)
    }

    @Test
    fun testKeywordScorer_EducationalAdvisoryWithPoliceMention_ReceivesLowRisk() {
        // Educational awareness mentioning digital arrest, police, CBI, OTP
        val awarenessArticle = "Cybersecurity training and scam awareness: Learn how to identify digital arrest scams where fraudsters pose as police or CBI officers asking for OTP. Always report incidents to 1930."
        val report = KeywordScorer.score(awarenessArticle)

        assertEquals(RiskStatus.Low, report.status)
        assertTrue("Risk score must be <= 15 on awareness article but was ${report.riskScore}", report.riskScore <= 15)
        assertFalse(report.engines.pretextLegitimacy.detected)
    }

    @Test
    fun testKeywordScorer_HindiEducationalTips_ReceivesLowRisk() {
        // Hindi cyber safety tips
        val hindiAdvisory = "साइबर सुरक्षा टिप्स: धोखाधड़ी से कैसे बचें और बैंक से जुड़े सुरक्षा उपाय क्या हैं।"
        val report = KeywordScorer.score(hindiAdvisory)

        assertEquals(RiskStatus.Low, report.status)
        assertTrue("Hindi educational advice must receive low risk but was ${report.riskScore}", report.riskScore <= 15)
    }
}
