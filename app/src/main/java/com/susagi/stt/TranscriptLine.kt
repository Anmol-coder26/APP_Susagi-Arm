package com.susagi.stt

import com.susagi.audio.AudioSource

/**
 * Represents a single transcribed dialogue turn or utterance from either participant.
 */
data class TranscriptLine(
    val source: AudioSource,
    val text: String,
    val isFinal: Boolean,
    val timestampMs: Long = System.currentTimeMillis(),
    val sessionId: String = "",
    val utteranceId: String? = null
)
