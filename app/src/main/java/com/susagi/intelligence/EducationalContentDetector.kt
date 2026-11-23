package com.susagi.intelligence

import java.util.Locale

/**
 * Educational, Advisory, and Technical Content Detector.
 *
 * Distinguishes security analysis, technical discussions, educational advisories,
 * and defensive warnings from active cyber attack execution.
 * Eliminates the false-positive 98% danger score bug on harmless technical content.
 */
object EducationalContentDetector {

    private val educationalPhrases = listOf(
        "researchers found", "cve-", "vulnerability", "advisory", "analysis",
        "protect against", "prevent", "how to avoid", "cybersecurity training",
        "security tip", "security awareness", "best practices", "whitepaper",
        "threat report", "infosec", "educational purpose", "awareness campaign",
        "cert-in", "advisory notice", "guidelines to stay safe", "tips to prevent",
        "warning signs of scam", "learn how to identify", "security training",
        "simulation", "cyber hygiene", "security researchers", "threat actor",
        "incident response", "security recommendations", "scam awareness",
        "scammers often", "attackers use", "common scam tactics", "never fall for",
        "stay safe from", "how scammers operate", "beware of", "do not be fooled",
        "case study", "detection rule", "indicator of compromise", "iocs",
        "mitre att&ck", "poc", "proof of concept", "cyber crime awareness",
        // Technical & Software Engineering Context (prevents development prompts from triggering false alarms)
        "cybersecurity", "development", "engineering", "implementation", "architecture",
        "source code", "master directive", "directive", "problem statement", "hackathon",
        "challenge", "ps3", "arm ai-soc", "android application", "codebase", "software",
        "algorithm", "pipeline", "detector", "kotlin", "gradle", "unit test", "regression test",
        "false positive", "safebrowsing", "link shield",
        // Hindi / Hinglish phrases
        "जागरूकता", "बचने के उपाय", "सावधानी", "सलाह", "सुरक्षा उपाय",
        "धोखाधड़ी से कैसे बचें", "जागरूक रहें", "साइबर सुरक्षा टिप्स", "टिप्स",
        "शिकायत दर्ज करें", "स्कैम से बचें", "सुरक्षा दिशा-निर्देश"
    )

    private val quotedScamPatterns = listOf(
        Regex("""(?i)\b(?:scammers? (?:say|claim|ask|write|use|told me)|example(?: of)?(?: a)? scam|user reported|received a message|reported that)\s*:?\s*["'“][^"'”\n]+["'”]"""),
        Regex("""(?i)\b(?:if (?:someone|a caller|an unknown person) (?:calls|asks|demands))\b"""),
        Regex("""(?i)\b(?:never (?:give|share|send|transfer))\b""")
    )

    /**
     * Imperative attack execution directives.
     * Attacks contain direct commands instructing the victim to act immediately.
     */
    private val attackCallToActionKeywords = listOf(
        "click here", "click link", "open this link", "download apk", "install this",
        "transfer now", "send money to", "pay immediately", "share otp", "tell me otp",
        "tell your otp", "give otp", "enter pin", "enter upi pin", "approve request", "accept collect",
        "connect anydesk", "install teamviewer", "do not hang up", "do not disconnect",
        "stay on the line", "room lock", "कमरा बंद", "फोन मत काटना", "पैसे भेजो",
        "ओटीपी बताओ", "पिन दर्ज करो", "तुरंत भेजो", "transfer to safe account",
        "pay to avoid arrest", "call immediately", "call electricity officer",
        "transfer ₹", "transfer rs", "transfer 50000", "send 50000", "pay 50000",
        "deposit 2000", "deposit fee", "deposit registration fee", "pay registration fee",
        "registration fee to activate", "deposit to activate",
        "डिजिटल अरेस्ट में रहें", "तुरंत डिजिटल अरेस्ट", "stay in digital arrest"
    )

    /**
     * Returns true if the text is identified as educational, advisory, technical analysis,
     * or a defensive explanation rather than an active attack.
     */
    fun isEducationalOrAnalysis(text: String): Boolean {
        if (text.isBlank()) return false
        if (hasActionableAttackDirective(text)) return false
        val lower = text.lowercase(Locale.ROOT)

        // Direct phrase match
        if (educationalPhrases.any { lower.contains(it) }) {
            return true
        }

        // Quoted context match
        if (quotedScamPatterns.any { it.containsMatchIn(text) }) {
            return true
        }

        return false
    }

    /**
     * Returns true if the text contains an unnegated actionable attack directive / call-to-action.
     */
    fun hasActionableAttackDirective(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase(Locale.ROOT)
        return attackCallToActionKeywords.any { kw ->
            val index = lower.indexOf(kw)
            if (index == -1) {
                false
            } else {
                // Check if negated by preceding defensive words
                val preceding = lower.substring(maxOf(0, index - 30), index)
                val isNegated = preceding.contains("never") || preceding.contains("don't") ||
                        preceding.contains("do not") || preceding.contains("should not") ||
                        preceding.contains("avoid") || preceding.contains("refuse") ||
                        preceding.contains("मत") || preceding.contains("नहीं") ||
                        preceding.contains("how scammers") || preceding.contains("warning signs")
                !isNegated
            }
        }
    }
}
