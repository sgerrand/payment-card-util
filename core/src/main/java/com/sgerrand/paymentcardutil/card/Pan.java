package com.sgerrand.paymentcardutil.card;

/**
 * A Primary Account Number: the number printed on a payment card.
 *
 * <p>A PAN is sensitive data. {@link #toString()} always masks it, so logging a
 * {@code Pan} by accident cannot leak the full number. Call {@link #digits()}
 * only where you need the real value.
 *
 * @param digits the full card number, 12 to 19 digits
 */
public record Pan(String digits) {

    private static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 19;

    public Pan {
        if (digits == null) {
            throw new IllegalArgumentException("PAN must not be null");
        }
        if (digits.length() < MIN_LENGTH || digits.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "PAN must be " + MIN_LENGTH + " to " + MAX_LENGTH + " digits, was " + digits.length());
        }
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("PAN must hold only digits");
            }
        }
    }

    /**
     * Builds a PAN from a string that may hold spaces or hyphens.
     */
    public static Pan parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("PAN must not be null");
        }
        StringBuilder cleaned = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '-') {
                continue;
            }
            cleaned.append(c);
        }
        return new Pan(cleaned.toString());
    }

    /** Whether the number passes the Luhn check. */
    public boolean isLuhnValid() {
        return Luhn.isValid(digits);
    }

    /** The scheme this card belongs to. */
    public CardScheme scheme() {
        return CardScheme.fromDigits(digits);
    }

    /** The first six digits, as a {@link Bin}. */
    public Bin bin() {
        return new Bin(digits.substring(0, 6));
    }

    /** The last four digits, safe to show to cardholders. */
    public String lastFour() {
        return digits.substring(digits.length() - 4);
    }

    /**
     * The number with everything but the first six and last four digits
     * replaced by {@code *}, as allowed by PCI DSS.
     */
    public String masked() {
        return maskDigits(digits);
    }

    /**
     * Masks any run of digits, keeping the first six and last four.
     *
     * <p>Unlike the rest of this class, nothing is checked: the input does not
     * have to be a valid card number. A data element holds whatever the file
     * holds, so the tools that write card numbers out call this rather than
     * building a {@link Pan}.
     *
     * @param digits the digits to mask
     * @return the masked digits, or the input unchanged if it is 10 characters
     *         or shorter, since there would be nothing left to hide
     */
    public static String maskDigits(String digits) {
        if (digits.length() <= 10) {
            return digits;
        }
        return digits.substring(0, 6)
                + "*".repeat(digits.length() - 10)
                + digits.substring(digits.length() - 4);
    }

    @Override
    public String toString() {
        return masked();
    }
}
