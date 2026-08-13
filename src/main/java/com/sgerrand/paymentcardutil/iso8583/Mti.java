package com.sgerrand.paymentcardutil.iso8583;

/**
 * A Message Type Indicator: the four digits that start every ISO 8583 message
 * and say what the message is for.
 *
 * <p>The four digits are, in order: version, message class, message function
 * and message origin. For example {@code 1240} is a version 1993 financial
 * presentment advice from an acquirer.
 *
 * @param digits the four MTI digits
 */
public record Mti(String digits) {

    public Mti {
        if (digits == null || digits.length() != 4) {
            throw new IllegalArgumentException("MTI must be 4 digits");
        }
        for (int i = 0; i < 4; i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("MTI must hold only digits: " + digits);
            }
        }
    }

    /** First digit: which version of ISO 8583 the message follows. */
    public int version() {
        return digits.charAt(0) - '0';
    }

    /** Second digit: the message class, such as authorisation or reversal. */
    public int messageClass() {
        return digits.charAt(1) - '0';
    }

    /** Third digit: the message function, such as request or advice. */
    public int function() {
        return digits.charAt(2) - '0';
    }

    /** Fourth digit: who sent the message. */
    public int origin() {
        return digits.charAt(3) - '0';
    }

    @Override
    public String toString() {
        return digits;
    }
}
