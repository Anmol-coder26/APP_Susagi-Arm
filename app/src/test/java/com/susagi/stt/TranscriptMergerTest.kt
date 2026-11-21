package com.susagi.stt

import com.susagi.audio.AudioSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TranscriptMergerTest {

    private lateinit var merger: TranscriptMerger

    @Before
    fun setUp() {
        merger = TranscriptMerger(maxHistoryTurns = 20, deduplicationWindowMs = 2000L)
    }

    @Test
    fun testDialogueSourcePreservation() {
        merger.onTranscript(AudioSource.LOCAL, "Hello, who is this?", isFinal = true)
        merger.onTranscript(AudioSource.REMOTE, "I am calling from Mumbai Police.", isFinal = true)

        val history = merger.transcriptHistory.value
        assertEquals(2, history.size)

        assertEquals(AudioSource.LOCAL, history[0].source)
        assertEquals("Hello, who is this?", history[0].text)

        assertEquals(AudioSource.REMOTE, history[1].source)
        assertEquals("I am calling from Mumbai Police.", history[1].text)
    }

    @Test
    fun testDeduplication_suppressesIdenticalUtterancesWithinWindow() {
        val now = System.currentTimeMillis()
        merger.onTranscript(AudioSource.REMOTE, "Please stay on the line", isFinal = true, timestampMs = now)
        // Immediate repeat of identical transcript
        merger.onTranscript(AudioSource.REMOTE, "Please stay on the line", isFinal = true, timestampMs = now + 500)

        val history = merger.transcriptHistory.value
        assertEquals("Duplicate transcript should be deduplicated", 1, history.size)
    }

    @Test
    fun testRecentTranscriptWindowFormatting() {
        merger.onTranscript(AudioSource.LOCAL, "Yes, sir.", isFinal = true)
        merger.onTranscript(AudioSource.REMOTE, "Your Aadhaar is linked to illegal narcotics.", isFinal = true)

        val window = merger.getRecentTranscriptWindow(windowMs = 60000L)

        assertTrue(window.contains("[LOCAL (User)]: Yes, sir."))
        assertTrue(window.contains("[REMOTE (Caller)]: Your Aadhaar is linked to illegal narcotics."))
    }

    @Test
    fun testClear_resetsHistory() {
        merger.onTranscript(AudioSource.LOCAL, "Testing", isFinal = true)
        assertEquals(1, merger.transcriptHistory.value.size)

        merger.clear()
        assertEquals(0, merger.transcriptHistory.value.size)
    }
}
