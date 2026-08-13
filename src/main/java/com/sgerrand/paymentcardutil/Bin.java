package com.sgerrand.paymentcardutil;

/**
 * A Bank Identification Number: the leading digits of a card number that
 * identify the issuer. Also called an IIN (Issuer Identification Number).
 *
 * @param digits the BIN digits, six or eight long
 */
public record Bin(String digits) {

    public Bin {
        if (digits == null) {
            throw new IllegalArgumentException("BIN must not be null");
        }
        if (digits.length() != 6 && digits.length() != 8) {
            throw new IllegalArgumentException("BIN must be 6 or 8 digits, was " + digits.length());
        }
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("BIN must hold only digits: " + digits);
            }
        }
    }

    /** The scheme this BIN belongs to. */
    public CardScheme scheme() {
        return CardScheme.fromDigits(digits);
    }

    @Override
    public String toString() {
        return digits;
    }
}
