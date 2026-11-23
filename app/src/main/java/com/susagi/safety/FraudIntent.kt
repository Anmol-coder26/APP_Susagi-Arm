package com.susagi.safety

import java.text.Normalizer
import java.util.Locale

/** Observations refer to clause ordinals. No raw message data is retained. */
object FraudIntent {
    enum class Role { BANK, AUTHORITY, EMPLOYER, FAMILY, SUPPORT, INVESTMENT }
    enum class Act { TRANSFER, PAY_FEE, INSTALL, SCREEN, DISCLOSE, SCAN, APPROVE, KEEP_SECRET }
    enum class Pressure { WORK_GATED, WITHHELD, SUSPENSION, CASE, URGENCY, CREDIT }
    enum class Voice { INSTRUCTION, WARNING, REPORTED }
    data class Clause(val index: Int, val roles: Set<Role>, val actions: Set<Act>,
                      val pressures: Set<Pressure>, val voice: Voice)
    data class Result(val clauses: List<Clause>, val exceededLimit: Boolean)

    private fun <T> rules(vararg pairs: Pair<T, String>) = pairs.map { it.first to Regex(it.second) }
    private val roles = rules(
        Role.BANK to """\b(?:bank officer|bank representative|banking team)\b""",
        Role.AUTHORITY to """\b(?:police|cybercrime|investigating officer|enforcement directorate|cbi)\b""",
        Role.EMPLOYER to """\b(?:employer|payroll|recruiter|hr department)\b""",
        Role.FAMILY to """\b(?:mum|papa|mummy|dad|beta)\b""",
        Role.SUPPORT to """\b(?:helpdesk|customer support|support team)\b""",
        Role.INVESTMENT to """\b(?:investment adviser|investment advisor|broker)\b"""
    )
    private val acts = rules(
        Act.TRANSFER to """\b(?:transfer (?:money|funds|the balance)|wire money|paise bhejo)\b""",
        Act.PAY_FEE to """\b(?:pay|remit|submit|deposit) (?:a |the |your )?(?:clearance|processing|verification|release) (?:charge|fee|deposit)\b|\b(?:task|registration|activation) (?:fee|deposit) (?:must be paid|is required)\b""",
        Act.INSTALL to """\b(?:install|download) (?:our |the |this )?(?:apk|remote support app)\b""",
        Act.SCREEN to """\b(?:share your screen|enable remote access|screen share karo)\b""",
        Act.DISCLOSE to """\b(?:read back|dictate|send me) (?:your |the )?(?:pin|otp|verification code)\b""",
        Act.SCAN to """\bscan (?:this |the )?(?:payment )?qr\b""",
        Act.APPROVE to """\bapprove (?:this |the )?(?:debit|mandate|payment)\b""",
        Act.KEEP_SECRET to """\b(?:keep this confidential|do not tell anyone|kisi ko mat batana)\b"""
    )
    private val pressures = rules(
        Pressure.WORK_GATED to """\bbefore (?:you can |the next |your next )(?:start|work|task|assignment|merchant rating)\b""",
        Pressure.WITHHELD to """\b(?:salary|withdrawal|payout) (?:remains |is |will be )?(?:withheld|on hold|pending release)\b""",
        Pressure.SUSPENSION to """\b(?:account suspension|account will be blocked)\b""",
        Pressure.CASE to """\b(?:under investigation|avoid detention|avoid prosecution)\b""",
        Pressure.URGENCY to """\b(?:immediately|right now|abhi)\b""",
        Pressure.CREDIT to """\b(?:to receive|to claim) (?:the |your )?(?:refund|cashback)\b"""
    )
    private val separator = Regex("""[.!?;।\n]+|\b(?:but|instead|however|lekin)\b""")
    private val warning = Regex("""\b(?:never|do not pay|don't pay|do not transfer|do not remit|no need to|should not|mat bhejo|must not|beware|do not approve)\b""")
    private val reported = Regex("""\b(?:scammer (?:said|asked)|scammers say|scam example|caller told me|fraud awareness)\b""")
    private fun <T> matched(text: String, patterns: List<Pair<T, Regex>>) =
        patterns.filter { it.second.containsMatchIn(text) }.mapTo(linkedSetOf()) { it.first }

    private val spacing = Regex("""[\p{Zs}\t]+""")

    fun inspect(text: String): Result {
        if (text.length > 32768) return Result(emptyList(), true)
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            .replace("\u200b", "").replace(spacing, " ")
        val result = mutableListOf<Clause>()
        separator.splitToSequence(normalized).forEachIndexed { index, part ->
            val actions = matched(part, acts)
            val identities = matched(part, roles)
            val context = matched(part, pressures)
            if (actions.isNotEmpty() || identities.isNotEmpty() || context.isNotEmpty()) {
                val voice = when {
                    reported.containsMatchIn(part) -> Voice.REPORTED
                    warning.containsMatchIn(part) -> Voice.WARNING
                    else -> Voice.INSTRUCTION
                }
                result.add(Clause(index, identities, actions, context, voice))
            }
        }
        return Result(result, false)
    }
}
