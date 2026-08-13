package com.sgerrand.paymentcardutil.pin;

import com.sgerrand.paymentcardutil.crypto.DesKeys;

import java.util.HexFormat;

/**
 * The Visa pin verification value.
 *
 * <p>A PVV lets an issuer check a pin without storing it. Card number, key
 * index and pin are joined into a sixteen digit block, encrypted under the
 * verification key, and the first four decimal digits of the result are kept.
 * Where the result has fewer than four decimal digits, the letters are folded
 * down into digits to make the count up.
 *
 * @see <a href="https://www.ibm.com/docs/en/linux-on-z">IBM's description of the algorithm</a>
 */
public final class VisaPvv {

    /** The key index used when none is given. */
    public static final int DEFAULT_KEY_INDEX = 1;

    /** Digits in a pin verification value. */
    public static final int PVV_LENGTH = 4;

    /** Card number digits that go into the block. */
    private static final int CARD_DIGITS = 11;

    /**
     * The only pin length a PVV is defined for. The block it is worked out from
     * is 11 card digits, a key index and the pin, and that has to come to
     * exactly 16 digits.
     */
    public static final int REQUIRED_PIN_LENGTH = 4;

    private VisaPvv() {
    }

    /**
     * Works out the verification value for a pin.
     *
     * @param pin        the pin
     * @param pvvKey     the pin verification key, as hex
     * @param keyIndex   which verification key was used
     * @param cardNumber the full card number
     * @throws IllegalArgumentException if the pin is not
     *                                  {@value #REQUIRED_PIN_LENGTH} digits, or
     *                                  the key index is not a single digit
     */
    public static String calculate(String pin, String pvvKey, int keyIndex, String cardNumber) {
        Pins.checkPin(pin);
        Pins.checkCardNumber(cardNumber);
        if (pin.length() != REQUIRED_PIN_LENGTH) {
            throw new IllegalArgumentException(
                    "A pin verification value needs a " + REQUIRED_PIN_LENGTH
                            + " digit pin, this one is " + pin.length());
        }
        if (keyIndex < 0 || keyIndex > 9) {
            throw new IllegalArgumentException("The key index is a single digit, was " + keyIndex);
        }

        String block = Pins.rightmostBeforeCheckDigit(cardNumber, CARD_DIGITS) + keyIndex + pin;
        byte[] encrypted = DesKeys.tripleDesEncrypt(HexFormat.of().parseHex(pvvKey), HexFormat.of().parseHex(block));
        return decimalise(HexFormat.of().formatHex(encrypted));
    }

    /**
     * Keeps the first four decimal digits of the ciphertext. If there are fewer
     * than four, the letters are used in order, each reduced by ten, until there
     * are enough.
     */
    private static String decimalise(String hex) {
        StringBuilder digits = new StringBuilder(PVV_LENGTH);
        for (int i = 0; i < hex.length() && digits.length() < PVV_LENGTH; i++) {
            char c = hex.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        for (int i = 0; i < hex.length() && digits.length() < PVV_LENGTH; i++) {
            char c = hex.charAt(i);
            if (Character.isLetter(c)) {
                digits.append(Character.digit(c, 16) - 10);
            }
        }
        return digits.toString();
    }
}
