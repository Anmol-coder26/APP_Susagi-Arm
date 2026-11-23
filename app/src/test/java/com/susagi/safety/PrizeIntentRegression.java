package com.susagi.safety;

public final class PrizeIntentRegression {
    public static void main(String[] args) {
        Object[][] rows = {
            {"hello sir toh have a lottery of 10 k", null, "CLAIM"},
            {"you have won a lottery of ten thousand rupees", null, "CLAIM"},
            {"sir, lottery", null, "INCOMPLETE"},
            {"you won ten thousand rupees", "first send five hundred to claim your prize", "ADVANCE_FEE"},
            {"sir aapki 10 hazaar ki lottery lagi hai, prize lene ke liye pehle processing fee bhejo", null, "ADVANCE_FEE"},
            {"आपकी लॉटरी लगी है, इनाम पाने के लिए फीस भेजें", null, "ADVANCE_FEE"},
            {"your prize is ready", "pay 250 to collect your winnings", "ADVANCE_FEE"},
            {"your prize is ready", "never pay a fee to collect your prize", "CLAIM"},
            {"our school conducted a lottery, and a student won ten thousand rupees", null, "NONE"},
            {"don't believe anyone who says you won a lottery and asks you to pay a release fee", null, "NONE"},
            {"the message said you won a jackpot", null, "NONE"},
            {"implement lottery fraud detection with examples of OTP theft", null, "NONE"},
            {"hello madam", null, "NONE"},
            {"you won the chess tournament", null, "NONE"},
            {"she said your prize was ready", "send money to claim the prize", "NONE"}
        };
        long start = System.nanoTime();
        for (Object[] row : rows) {
            String actual = PrizeIntent.inspect((String)row[0], (String)row[1]).name();
            if (!actual.equals(row[2])) throw new AssertionError(row[0] + ": " + actual + " expected " + row[2]);
        }
        System.out.println(rows.length + " authored component checks passed; batch ns including class initialization: " + (System.nanoTime()-start));
    }

    @org.junit.Test
    public void testPrizeIntentRegressionComponentChecks() {
        main(new String[0]);
    }
}
