package com.guardian.app

import com.guardian.app.voip.LiveRiskAnalyzer
import com.guardian.app.voip.LiveState
import com.susagi.call.Speaker
import com.susagi.call.TranscriptLine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoipPipelineTest {

    @Test
    fun testInstantKeywordScoring_triggersImmediateHighRiskScore() = runBlocking {
        val flow = MutableSharedFlow<TranscriptLine>(replay = 1, extraBufferCapacity = 16)
        var latestState: LiveState? = null

        val analyzer = SemanticAnalyzer()
        val liveRiskAnalyzer = LiveRiskAnalyzer(analyzer, "en") { state ->
            latestState = state
        }

        liveRiskAnalyzer.start(flow)
        kotlinx.coroutines.delay(100)

        // Emit instant high risk keyword "otp"
        flow.emit(TranscriptLine(Speaker.REMOTE, "Please share your OTP immediately", isFinal = false))
        kotlinx.coroutines.delay(300)

        // Assert score jumped to >= 60% instantly
        assertTrue(latestState != null)
        assertEquals(60, latestState?.riskScore)

        liveRiskAnalyzer.stop()
    }

    @Test
    fun testInstantKeywordScoring_hindiKeyword_triggersImmediateRiskScore() = runBlocking {
        val flow = MutableSharedFlow<TranscriptLine>(replay = 1, extraBufferCapacity = 16)
        var latestState: LiveState? = null

        val analyzer = SemanticAnalyzer()
        val liveRiskAnalyzer = LiveRiskAnalyzer(analyzer, "hi") { state ->
            latestState = state
        }

        liveRiskAnalyzer.start(flow)
        kotlinx.coroutines.delay(100)

        // Emit instant Hindi high risk keyword "गिरफ्तार" (arrest)
        flow.emit(TranscriptLine(Speaker.REMOTE, "आपको पुलिस द्वारा गिरफ्तार किया जाएगा", isFinal = false))
        kotlinx.coroutines.delay(300)

        assertTrue(latestState != null)
        assertEquals(60, latestState?.riskScore)

        liveRiskAnalyzer.stop()
    }
}
