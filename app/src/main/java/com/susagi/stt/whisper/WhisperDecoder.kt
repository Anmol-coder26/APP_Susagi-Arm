package com.susagi.stt.whisper

import java.util.regex.Pattern

/**
 * WhisperDecoder
 *
 * Post-processes, decodes, and sanitizes output from the Whisper speech model.
 * - Strips Whisper special tokens (<|endoftext|>, <|transcribe|>, <|notimestamps|>, language tags, timestamps).
 * - Suppresses repetitive loops and hallucinations.
 * - Trims and formats multilingual transcriptions (Hindi, English, Hinglish).
 */
object WhisperDecoder {

    // Pattern matching any Whisper special token: <|...|>
    private val SPECIAL_TOKEN_REGEX = Pattern.compile("<\\|[a-zA-Z0-9_.-]+\\|>")
    
    // Pattern matching timestamp tokens: <|0.00|>, <|1.20|>, etc.
    private val TIMESTAMP_TOKEN_REGEX = Pattern.compile("<\\|[0-9]+(\\.[0-9]+)?\\|>")

    /**
     * Cleans and sanitizes raw Whisper output text.
     */
    fun decode(rawOutput: String): String {
        if (rawOutput.isBlank()) return ""

        // 1. Strip special tokens
        var cleaned = SPECIAL_TOKEN_REGEX.matcher(rawOutput).replaceAll("")
        cleaned = TIMESTAMP_TOKEN_REGEX.matcher(cleaned).replaceAll("")

        // 2. Normalize whitespace
        cleaned = cleaned.replace("\\s+".toRegex(), " ").trim()

        // 3. Hallucination and repetitive loop suppression
        cleaned = suppressRepetitions(cleaned)

        return cleaned
    }

    /**
     * Suppresses repetitive token / phrase loops generated when audio contains silence or background noise.
     */
    private fun suppressRepetitions(text: String): String {
        if (text.length < 20) return text

        val words = text.split(" ")
        if (words.size < 6) return text

        // Check if single word is repeated 4+ times consecutively
        val deduplicatedWords = mutableListOf<String>()
        var repeatCount = 0
        var lastWord = ""

        for (w in words) {
            if (w.equals(lastWord, ignoreCase = true)) {
                repeatCount++
                if (repeatCount < 3) {
                    deduplicatedWords.add(w)
                }
            } else {
                repeatCount = 0
                lastWord = w
                deduplicatedWords.add(w)
            }
        }

        return deduplicatedWords.joinToString(" ")
    }

    /**
     * Maps language code (e.g. "hi", "en") to Whisper decoder start prompt token if required.
     */
    fun getLanguageToken(langCode: String): String {
        return when (langCode.lowercase()) {
            "hi" -> "<|hi|>"
            "en" -> "<|en|>"
            "ta" -> "<|ta|>"
            "te" -> "<|te|>"
            "mr" -> "<|mr|>"
            "bn" -> "<|bn|>"
            "gu" -> "<|gu|>"
            "kn" -> "<|kn|>"
            "ml" -> "<|ml|>"
            else -> "<|en|>"
        }
    }
}
