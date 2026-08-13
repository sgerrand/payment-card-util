package com.sgerrand.paymentcardutil.pin;

/**
 * Checks shared by the pin block formats.
 */
final class Pins {

    /** Shortest pin the block formats allow. */
    static final int MIN_PIN_LENGTH = 4;

    /** Longest pin the block formats allow. */
    static final int MAX_PIN_LENGTH = 12;

    private Pins() {
    }

    /**
     * @throws IllegalArgumentException if the pin is not 4 to 12 digits
     */
    static String checkPin(String pin) {
        if (pin == null) {
            throw new IllegalArgumentException("A pin is needed");
        }
        if (pin.length() < MIN_PIN_LENGTH || pin.length() > MAX_PIN_LENGTH) {
            throw new IllegalArgumentException(
                    "A pin is " + MIN_PIN_LENGTH + " to " + MAX_PIN_LENGTH + " digits, was " + pin.length());
        }
        requireDigits(pin, "pin");
        return pin;
    }

    /**
     * @throws IllegalArgumentException if the card number is too short to build
     *                                  a pin block from
     */
    static String checkCardNumber(String cardNumber) {
        if (cardNumber == null) {
            throw new IllegalArgumentException("A card number is needed");
        }
        if (cardNumber.length() < 13) {
            throw new IllegalArgumentException(
                    "A card number of at least 13 digits is needed, was " + cardNumber.length());
        }
        requireDigits(cardNumber, "card number");
        return cardNumber;
    }

    /**
     * The last {@code count} digits of the card number, not counting the check
     * digit. This is the part that goes into a pin block or a verification
     * value.
     */
    static String rightmostBeforeCheckDigit(String cardNumber, int count) {
        int end = cardNumber.length() - 1;
        int start = end - count;
        if (start < 0) {
            throw new IllegalArgumentException(
                    "The card number needs at least " + (count + 1) + " digits, has " + cardNumber.length());
        }
        return cardNumber.substring(start, end);
    }

    private static void requireDigits(String value, String what) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("The " + what + " must hold only digits");
            }
        }
    }
}
