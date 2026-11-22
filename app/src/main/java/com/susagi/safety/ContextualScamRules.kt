package com.susagi.safety

internal object ContextualScamRules {
    private val warning = Regex("""\b(?:never|do not|don't|avoid|refuse to)\s+(?:ask you to\s+)?(?:install|download|send|pay|transfer|deposit|invest)\b|पैसे मत भेज|पैसे न भेज|पैसे पाठवू नका""")
    private val contrast = Regex("""\b(?:but|however|instead|lekin)\b|लेकिन|मगर""")
    private val demand = Regex("""\b(?:pay|transfer|send money|bharo|jama karo|paise bhejo)\b|\bdeposit\s+(?:now|funds|money|inr|rs\b|₹|\d)|\bwire\s+\d|भुगतान करें|जमा कर|पैसे भेज|पैसे पाठवा|रुपये भेजें|पैसे ट्रांसफर""")
    private val offer = Regex("""\b(?:job|vacancy|task|commission|sarkari yojana|government grant|subsidy|recruiter|salary)\b|नौकरी|सरकारी योजना|नोकरी""")
    private val advance = Regex("""registration fee|joining fee|security deposit|withdrawal fee|withdrawal tax|salary release fee|task deposit|unlock (?:your )?(?:earnings|commission)|पंजीकरण शुल्क|नोंदणी शुल्क""")
    private val investment = Regex("""invest(?:ment)?|trading|deposit|portfolio|stock\b|ट्रेडिंग|निवेश|गुंतवणूक""")
    private val guarantee = Regex("""risk[- ]free profit|guaranteed (?:daily |monthly )?(?:profit|returns?)|(?:double|triple) your (?:deposit|money|investment)|(?:overnight|daily) returns?|गारंटीड रिटर्न|हमखास परतावा|पैसे डबल""")
    private val remote = Regex("""anydesk|teamviewer|remote desktop|screen.sharing app""")
    private val install = Regex("""\b(?:install|download)\b|इंस्टॉल|डाउनलोड""")
    private val financial = Regex("""\b(?:kyc|bank|refund|upi)\b|बैंक|रिफंड|केवाईसी""")
    private val family = Regex("""\b(?:mum|mom|dad|papa|mummy|beta|beti)\b|मम्मी|पापा""")
    private val number = Regex("""new number|replacement sim|lost my phone|naya number|नया नंबर""")
    private val urgent = Regex("""urgent|right now|immediately|abhi|तुरंत""")
    private val avoidsCall = Regex("""do not call my old number|cannot take calls|purane number par call mat""")
    private val restriction = Regex("""suspend|deactivat|block|बंद""")
    private val shareScreen = Regex("""\bshare (?:your |the )?screen\b|screen share karo|स्क्रीन शेयर कर""")
    private val enterCode = Regex("""(?:enter(?:ing)?|type|daalo|डालें)\s+(?:(?:your|the|upi|bank|card)\s+){0,3}(?:otp|pin|cvv|पिन|ओटीपी)\b""")
    private val feeForCredit = Regex("""(?:to (?:claim|receive|get|release)|activate) (?:your |the )?(?:cashback|refund)|(?:refund|cashback) paane ke liye""")
    private val payFee = Regex("""\bpay\s+(?:(?:a|the|an|our|small|activation|processing|release|withdrawal)\s+){0,4}(?:fee|tax|charge|deposit)\b|fee bharo|टैक्स भरें|शुल्क""")
    private val unlock = Regex("""(?:release|unlock|unfreeze) (?:your |the )?(?:balance|earnings|payout|portfolio)|बैलेंस निकालने""")
    private val advancePayment = Regex("""(?:send|transfer|pay|wire)\s+(?:rs\.?\s*|inr\s*|₹\s*)\d""")
    private val frozenBalance = Regex("""\b(?:withdrawals?|account|portfolio|funds?|balance)\s+(?:are|is)?\s*(?:frozen|blocked|suspended|locked|on hold)\b|खाता (?:ब्लॉक|फ्रीज)|निकासी रोक|खाता फ्रीज""")
    private val accountVerification = Regex("""(?:to )?(?:verify|unblock|activate) your (?:account|identity|card)""")

    fun inspect(clause: String, hasLink: Boolean, nextClause: String? = null): List<Signal> = buildList {
        for (part in contrast.split(clause)) {
            if (warning.containsMatchIn(part)) continue
            if (shareScreen.containsMatchIn(part) && enterCode.containsMatchIn(part) &&
                !screenWarning.containsMatchIn(part)) add(Signal.SCREEN_CREDENTIAL)
            if (feeForCredit.containsMatchIn(part) && payFee.containsMatchIn(part)) add(Signal.REFUND_FEE)
            if (unlock.containsMatchIn(part) && (advancePayment.containsMatchIn(part) || payFee.containsMatchIn(part))) add(Signal.ADVANCE_FEE)
            if (frozenBalance.containsMatchIn(part) && (payFee.containsMatchIn(part) || demand.containsMatchIn(part))) add(Signal.ADVANCE_FEE)
            if (accountVerification.containsMatchIn(part) && (advancePayment.containsMatchIn(part) || payFee.containsMatchIn(part))) add(Signal.ADVANCE_FEE)
            if (remote.containsMatchIn(part) && install.containsMatchIn(part) && financial.containsMatchIn(part))
                add(Signal.REMOTE_ACCESS)
            if (demand.containsMatchIn(part)) {
                if (offer.containsMatchIn(part) && advance.containsMatchIn(part)) add(Signal.ADVANCE_FEE)
                if ((investment.containsMatchIn(part) || guarantee.containsMatchIn(part)) && guarantee.containsMatchIn(part)) add(Signal.GUARANTEED_RETURN)
            } else if (nextClause != null) {
                val nextActionable = warning.replace(nextClause, "")
                if (guarantee.containsMatchIn(part) && demand.containsMatchIn(nextActionable)) add(Signal.GUARANTEED_RETURN)
                if ((unlock.containsMatchIn(part) || frozenBalance.containsMatchIn(part)) && (payFee.containsMatchIn(nextActionable) || demand.containsMatchIn(nextActionable))) add(Signal.ADVANCE_FEE)
            }
            if (family.containsMatchIn(part) && number.containsMatchIn(part) && (urgent.containsMatchIn(part) || avoidsCall.containsMatchIn(part)))
                add(Signal.FAMILY_PAYMENT)
            if (hasLink && ("fastag" in part || "फास्टैग" in part) && restriction.containsMatchIn(part))
                add(Signal.FASTAG)
        }
    }

    private val screenWarning = Regex("""(?:never|do not|don't)\s+share|screen share mat karo|स्क्रीन शेयर न कर""")
}
