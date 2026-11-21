package com.susagi.stt

import com.guardian.app.voip.CallDiagnostics
import com.susagi.audio.AudioSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * TranscriptMerger
 *
 * Collects, orders, and deduplicates real-time transcripts from both LOCAL and REMOTE audio streams.
 * Maintains chronological conversation history with strict source preservation, ensuring that
 * raw microphone and remote caller streams are never conflated or misattributed.
 */
class TranscriptMerger(
    private val maxHistoryTurns: Int = 100,
    private val deduplicationWindowMs: Long = 3000L
) {
    private val lock = Any()
    private val history = mutableListOf<TranscriptLine>()

    private val _transcriptUpdates = MutableSharedFlow<TranscriptLine>(extraBufferCapacity = 64)
    val transcriptUpdates: SharedFlow<TranscriptLine> = _transcriptUpdates.asSharedFlow()

    private val _transcriptHistory = MutableStateFlow<List<TranscriptLine>>(emptyList())
    val transcriptHistory: StateFlow<List<TranscriptLine>> = _transcriptHistory.asStateFlow()

    private var lastLocalText: String = ""
    private var lastLocalTs: Long = 0L

    private var lastRemoteText: String = ""
    private var lastRemoteTs: Long = 0L

    /**
     * Ingests a new transcript line from either LOCAL or REMOTE source.
     */
    fun onTranscript(line: TranscriptLine) {
        val trimmed = line.text.trim()
        if (trimmed.isBlank() || trimmed.equals("null", ignoreCase = true)) return

        synchronized(lock) {
            val now = line.timestampMs

            // Deduplication check per source
            if (line.source == AudioSource.LOCAL) {
                if (trimmed == lastLocalText && (now - lastLocalTs) < deduplicationWindowMs) {
                    return
                }
                lastLocalText = trimmed
                lastLocalTs = now
            } else {
                if (trimmed == lastRemoteText && (now - lastRemoteTs) < deduplicationWindowMs) {
                    return
                }
                lastRemoteText = trimmed
                lastRemoteTs = now
            }

            // Append finalized turns to history
            if (line.isFinal) {
                history.add(line)
                if (history.size > maxHistoryTurns) {
                    history.removeAt(0)
                }
                _transcriptHistory.value = history.toList()
            }
        }

        // Emit update to subscribers
        _transcriptUpdates.tryEmit(line)
        CallDiagnostics.log("MERGER", "[${line.source.name}] Transcript emitted: '$trimmed' (final=${line.isFinal})")
    }

    /**
     * Ingest convenience overload.
     */
    fun onTranscript(source: AudioSource, text: String, isFinal: Boolean, timestampMs: Long = System.currentTimeMillis()) {
        onTranscript(
            TranscriptLine(
                source = source,
                text = text,
                isFinal = isFinal,
                timestampMs = timestampMs
            )
        )
    }

    /**
     * Formats recent conversation turns within [windowMs] into an LLM-friendly transcript prompt.
     * Guaranteed to clearly label LOCAL (App User) vs REMOTE (Incoming Caller).
     */
    fun getRecentTranscriptWindow(windowMs: Long = 45000L): String {
        val cutoff = System.currentTimeMillis() - windowMs
        val recent: List<TranscriptLine>
        synchronized(lock) {
            recent = history.filter { it.timestampMs >= cutoff }.takeLast(20)
        }

        if (recent.isEmpty()) return ""

        return recent.joinToString("\n") { turn ->
            val speakerTag = if (turn.source == AudioSource.LOCAL) "LOCAL (User)" else "REMOTE (Caller)"
            "[$speakerTag]: ${turn.text}"
        }
    }

    /**
     * Returns the entire session history formatted as plain text.
     */
    fun getFullFormattedTranscript(): String {
        val all: List<TranscriptLine>
        synchronized(lock) {
            all = history.toList()
        }
        return all.joinToString("\n") { turn ->
            val speakerTag = if (turn.source == AudioSource.LOCAL) "LOCAL" else "REMOTE"
            "[$speakerTag]: ${turn.text}"
        }
    }

    /**
     * Resets conversation history and deduplication caches between calls.
     */
    fun clear() {
        synchronized(lock) {
            history.clear()
            lastLocalText = ""
            lastLocalTs = 0L
            lastRemoteText = ""
            lastRemoteTs = 0L
            _transcriptHistory.value = emptyList()
        }
        CallDiagnostics.log("MERGER", "Transcript merger history cleared")
    }
}
