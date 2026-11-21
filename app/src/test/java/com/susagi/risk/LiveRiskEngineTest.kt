package com.susagi.risk

import com.susagi.audio.AudioSource
import com.susagi.stt.TranscriptLine
import com.susagi.stt.TranscriptMerger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LiveRiskEngineTest {

    private lateinit var client: GeminiRoleClient
    private lateinit var merger: TranscriptMerger
    private lateinit var engine: LiveRiskEngine

    @Before
    fun setUp() {
        client = GeminiRoleClient(apiKey = "")
        merger = TranscriptMerger()
        engine = LiveRiskEngine(client, merger)
    }

    @Test
    fun testKeywordSignal_elevatesDisplayedRisk() {
        engine.onKeywordSignal(60)
        assertEquals(60, engine.currentDisplayedRisk())

        // Lower score does not decrease existing risk
        engine.onKeywordSignal(30)
        assertEquals(60, engine.currentDisplayedRisk())
    }

    @Test
    fun testTranscriptWindowEvaluation_detectsExtortion() = runBlocking {
        engine.onTranscriptWindow(
            local = "Sir, what did I do?",
            remote = "CBI officer here. You are under digital arrest. Transfer to safe account now."
        )

        val risk = engine.riskResult.value
        assertTrue("Risk score should be elevated >= 60, was ${risk.riskScore}", risk.riskScore >= 60)
        assertEquals("remote", risk.scammerRole)
        assertEquals("local", risk.victimRole)
    }

    @Test
    fun testFastPathTrigger_reactsImmediatelyToUrgentThreatWords() = runBlocking {
        engine.start()

        // Push urgent digital arrest keyword
        val line = TranscriptLine(
            source = AudioSource.REMOTE,
            text = "This is Mumbai Police cyber cell, you are under digital arrest!",
            isFinal = true
        )
        merger.onTranscript(line)
        engine.onTranscriptLine(line)

        // Give fast-path coroutine moment to execute across Dispatchers.IO
        var risk = engine.riskResult.value
        for (i in 1..30) {
            if (risk.riskScore >= 60) break
            delay(50)
            risk = engine.riskResult.value
        }

        assertTrue("Expected fast path risk score >= 60, got ${risk.riskScore}", risk.riskScore >= 60)
        assertEquals("remote", risk.scammerRole)
        assertEquals("local", risk.victimRole)

        engine.stop()
    }
}
