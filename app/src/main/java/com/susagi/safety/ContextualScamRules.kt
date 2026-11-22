package com.susagi.safety

internal object ContextualScamRules {
    private val warning = Regex("""\b(?:never|do not|don't|avoid|refuse to)\s+(?:ask you to\s+)?(?:install|download|send|pay|transfer|deposit|invest)\b|पैसे मत भेज|पैसे न भेज|पैसे पाठवू नका""")
    private val contrast = Regex("""\b(?:but|however|instead|lekin)\b|लेकिन|मगर""")
    private val demand = Regex("""\b(?:pay|transfer|send money|bharo|jama karo|paise bhejo)\b|\bdeposit\s+(?:now|funds|money|inr|rs\b|₹|\d)|भुगतान करें|जमा कर|पैसे भेज|पैसे पाठवा""")
    private val offer = Regex("""\b(?:job|vacancy|task|commission|sarkari yojana|government grant)\b|नौकरी|सरकारी योजना|नोकरी""")
    private val advance = Regex("""registration fee|joining fee|security deposit|withdrawal fee|unlock (?:your )?(?:earnings|commission)|पंजीकरण शुल्क|नोंदणी शुल्क""")
    private val investment = Regex("""invest(?:ment)?|trading|निवेश|गुंतवणूक""")
    private val guarantee = Regex("""guaranteed (?:daily |monthly )?(?:profit|returns?)|गारंटीड रिटर्न|हमखास परतावा""")
    private val remote = Regex("""anydesk|teamviewer|remote desktop|screen.sharing app""")
    private val install = Regex("""\b(?:install|download)\b|इंस्टॉल|डाउनलोड""")
    private val financial = Regex("""\b(?:kyc|bank|refund|upi)\b|बैंक|रिफंड|केवाईसी""")
    private val family = Regex("""\b(?:mum|mom|dad|papa|mummy|beta|beti)\b|मम्मी|पापा""")
    private val number = Regex("""new number|lost my phone|naya number|नया नंबर""")
    private val urgent = Regex("""urgent|right now|immediately|abhi|तुरंत""")
    private val restriction = Regex("""suspend|deactivat|block|बंद""")
    private val unlock = Regex("""(?:release|unlock) (?:your |the )?(?:balance|earnings|payout)""")
    private val advancePayment = Regex("""(?:send|transfer|pay)\s+(?:rs\.?\s*|inr\s*|₹\s*)\d""")

    fun inspect(clause: String, hasLink: Boolean): List<Signal> = buildList {
        for (part in contrast.split(clause)) {
            if (warning.containsMatchIn(part)) continue
            if (unlock.containsMatchIn(part) && advancePayment.containsMatchIn(part)) add(Signal.ADVANCE_FEE)
            if (remote.containsMatchIn(part) && install.containsMatchIn(part) && financial.containsMatchIn(part))
                add(Signal.REMOTE_ACCESS)
            if (!demand.containsMatchIn(part)) continue
            if (offer.containsMatchIn(part) && advance.containsMatchIn(part)) add(Signal.ADVANCE_FEE)
            if (investment.containsMatchIn(part) && guarantee.containsMatchIn(part)) add(Signal.GUARANTEED_RETURN)
            if (family.containsMatchIn(part) && number.containsMatchIn(part) && urgent.containsMatchIn(part))
                add(Signal.FAMILY_PAYMENT)
            if (hasLink && ("fastag" in part || "फास्टैग" in part) && restriction.containsMatchIn(part))
                add(Signal.FASTAG)
        }
    }
}
