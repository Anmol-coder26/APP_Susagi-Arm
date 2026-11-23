package com.susagi.safety;

import java.util.regex.Pattern;

/** Lexical observations in one normalized utterance and its immediate continuation. */
public final class PrizeIntent {
    public enum Finding { NONE, INCOMPLETE, CLAIM, ADVANCE_FEE }
    private PrizeIntent() {}

    private static final Pattern PRIZE = Pattern.compile("\\b(?:lottery|lotteri|prize|jackpot|winnings)\\b|लॉटरी|इनाम");
    private static final Pattern ADDRESSED = Pattern.compile("\\b(?:you(?:'ve)? (?:have |have been )?(?:won|selected)|your (?:prize|winnings)|aapk[ioa]|aapko|apki)\\b|आपकी|आपको|आपने");
    private static final Pattern VOCATIVE = Pattern.compile("^\\s*(?:hello[ ,]+|hi[ ,]+)?(?:sir|madam|maam|सर|मैडम)\\b");
    private static final Pattern AMOUNT = Pattern.compile("[\\p{Nd}]+|\\b(?:thousand|hundred|lakh|hazaar|hazar)\\b|हजार|हज़ार");
    private static final Pattern WON = Pattern.compile("\\byou(?:'ve)? (?:have )?won\\b|आपने.{0,24}जीत");
    private static final Pattern CONTEXT = Pattern.compile("\\b(?:never|do not|don't|beware|warned|awareness|example|scammers? (?:say|said|ask)|(?:he|she|they) said|message (?:said|read)|school (?:conducted|held)|student won)\\b|मत भेज|सावधान");
    private static final Pattern CLAIM = Pattern.compile("\\b(?:claim|collect|release|receive)(?:\\s+(?:your |the )?(?:prize|winnings|jackpot))?|(?:prize|lottery) (?:lene|paane) ke liye|इनाम (?:पाने|लेने)");
    private static final Pattern MONEY = Pattern.compile("\\b(?:pay|send|transfer|deposit)\\s+(?:(?:the|a|processing|release|registration)\\s+){0,3}(?:[₹$]?[0-9]+|one|two|three|four|five|six|seven|eight|nine|ten|fee|charge|money|funds)\\b|(?:fee|fees|paise|शुल्क|फीस)\\s+(?:bhej[oa]|jama karo|भेजें|भेजो|जमा)");
    private static boolean has(Pattern p, String s) { return p.matcher(s).find(); }

    public static Finding inspect(String clause, String next) {
        if (clause.length() > 32768 || has(CONTEXT, clause)) return Finding.NONE;
        boolean prize = has(PRIZE, clause);
        boolean won = has(WON, clause);
        if (!prize && !won) return Finding.NONE;
        boolean directed = has(ADDRESSED, clause) || has(VOCATIVE, clause);
        boolean monetaryWin = won && has(AMOUNT, clause);
        if (!directed && !monetaryWin) return Finding.NONE;
        if (has(CLAIM, clause) && has(MONEY, clause)) return Finding.ADVANCE_FEE;
        if (next != null && next.length() <= 32768 && !has(CONTEXT, next)
                && has(CLAIM, next) && has(MONEY, next)) return Finding.ADVANCE_FEE;
        if (prize && (has(ADDRESSED, clause) || has(AMOUNT, clause))) return Finding.CLAIM;
        if (prize && has(VOCATIVE, clause) && clause.trim().length() <= 36) return Finding.INCOMPLETE;
        return Finding.NONE;
    }
}
