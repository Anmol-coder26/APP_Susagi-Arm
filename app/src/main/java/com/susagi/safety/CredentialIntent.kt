package com.susagi.safety

import java.text.Normalizer
import java.util.Locale

/** Clause indices refer to normalized message clauses. Findings contain no raw credentials. */
object CredentialIntent {
    enum class Context { REQUEST, WARNING, ATTRIBUTED }
    data class Finding(val clauseIndex: Int, val context: Context, val evidenceId: String)

    private val boundary = Regex("""[.!?;।\n]+|\b(?:instead|however|but|lekin)\b""")
    private val credential = Regex("""\b(?:otp|cvv|upi pin|verification code|authentication code|one.time password|six.digit code|digits from (?:the|your) bank message)\b|ओटीपी""")
    private val disclosure = Regex("""\b(?:read (?:me |us )?back|read out|dictate|send me|tell me|forward|batao|bhejo)\b|बताओ|भेजो""")
    private val caution = Regex("""\b(?:never|do not|don't|must not|mat|nahi)\b|मत|नहीं""")
    private val attribution = Regex("""\b(?:scammers? (?:asked|said|say)|caller asked me|example of a scam|fraud awareness)\b""")

    fun inspect(text: String): List<Finding> {
        if (text.length > 32768) return emptyList()
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).replace("\u200b", "").replace('’', '\'')
        return buildList {
            boundary.split(normalized).forEachIndexed { index, clause ->
                if (credential.containsMatchIn(clause) && disclosure.containsMatchIn(clause)) {
                    val context = when {
                        attribution.containsMatchIn(clause) -> Context.ATTRIBUTED
                        caution.containsMatchIn(clause) -> Context.WARNING
                        else -> Context.REQUEST
                    }
                    add(Finding(index, context, "CREDENTIAL_DISCLOSURE_" + context.name))
                }
            }
        }
    }
}
