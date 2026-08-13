package com.sgerrand.paymentcardutil.card;

/**
 * The card scheme (network) that issued a card number.
 */
public enum CardScheme {

    VISA,
    MASTERCARD,
    AMERICAN_EXPRESS,
    DISCOVER,
    JCB,
    DINERS_CLUB,
    UNION_PAY,
    /** The number does not match any scheme this library knows about. */
    UNKNOWN;

    /**
     * Works out the scheme from the leading digits of a card number.
     *
     * @param digits the card number, or at least its first six digits
     */
    public static CardScheme fromDigits(String digits) {
        if (digits == null || digits.isEmpty()) {
            return UNKNOWN;
        }
        int two = prefix(digits, 2);
        int three = prefix(digits, 3);
        int four = prefix(digits, 4);
        int six = prefix(digits, 6);

        if (digits.charAt(0) == '4') {
            return VISA;
        }
        if ((two >= 51 && two <= 55) || (six >= 222100 && six <= 272099)) {
            return MASTERCARD;
        }
        if (two == 34 || two == 37) {
            return AMERICAN_EXPRESS;
        }
        if (four == 6011 || two == 65 || (three >= 644 && three <= 649)) {
            return DISCOVER;
        }
        if (four >= 3528 && four <= 3589) {
            return JCB;
        }
        if (two == 36 || two == 38 || (three >= 300 && three <= 305) || three == 309) {
            return DINERS_CLUB;
        }
        if (two == 62 || two == 81) {
            return UNION_PAY;
        }
        return UNKNOWN;
    }

    private static int prefix(String digits, int length) {
        if (digits.length() < length) {
            return -1;
        }
        try {
            return Integer.parseInt(digits, 0, length, 10);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
