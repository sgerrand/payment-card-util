package com.sgerrand.paymentcardutil.pin;

import com.sgerrand.paymentcardutil.crypto.DesKeys;

import java.util.HexFormat;

/**
 * ISO 9564-1 format 0, also called ANSI X9.8, Visa-1 and ECI-0.
 *
 * <p>The block is eight bytes: the pin, padded with {@code F}, mixed by
 * exclusive or with twelve digits of the card number. Because the card number
 * goes in, the same pin on two different cards gives two different blocks.
 *
 * <pre>
 * P1 = LLPPPPFFFFFFFFFF     L = pin length, P = pin, F = filler x'F'
 * P2 = 0000CCCCCCCCCCCC     C = card number, last 12 digits before the check digit
 * block = P1 XOR P2
 * </pre>
 *
 * <p>Unwrapping the block needs the same card number that went in, so
 * {@link #fromBytes} asks for it.
 *
 * <p>The pin length goes in as a single hex digit, so a 12 digit pin writes
 * {@code C}. cardutil writes it as decimal text instead, which pushes the rest
 * of the block along and produces a block no reader can make sense of. Pins of
 * 10 digits or more are the only case where the two disagree.
 */
public final class Iso0PinBlock implements PinBlock {

    private static final HexFormat HEX = HexFormat.of();

    private static final int BLOCK_BYTES = 8;
    private static final char FILLER = 'f';

    private final String pin;
    private final String cardNumber;

    /**
     * @param pin        the pin, 4 to 12 digits
     * @param cardNumber the full card number, check digit included
     */
    public Iso0PinBlock(String pin, String cardNumber) {
        this.pin = Pins.checkPin(pin);
        this.cardNumber = Pins.checkCardNumber(cardNumber);
    }

    /**
     * Reads a pin back out of a block.
     *
     * @param block      the eight block bytes
     * @param cardNumber the card number the block was built with
     */
    public static Iso0PinBlock fromBytes(byte[] block, String cardNumber) {
        if (block.length != BLOCK_BYTES) {
            throw new IllegalArgumentException(
                    "A format 0 pin block is " + BLOCK_BYTES + " bytes, was " + block.length);
        }
        String p1 = xorHex(HEX.formatHex(block), pinPad(cardNumber));
        int pinLength = Character.digit(p1.charAt(1), 16);
        if (pinLength < Pins.MIN_PIN_LENGTH || pinLength > Pins.MAX_PIN_LENGTH) {
            throw new IllegalArgumentException(
                    "The block does not hold a pin: it says the pin is " + pinLength + " digits");
        }
        return new Iso0PinBlock(p1.substring(2, 2 + pinLength), cardNumber);
    }

    /**
     * Reads a pin out of an encrypted block.
     *
     * @param encryptedBlock the encrypted block
     * @param cardNumber     the card number the block was built with
     * @param key            the pin protection key, as hex
     */
    public static Iso0PinBlock fromEncryptedBytes(byte[] encryptedBlock, String cardNumber, String key) {
        return fromBytes(DesKeys.tripleDesDecrypt(HEX.parseHex(key), encryptedBlock), cardNumber);
    }

    @Override
    public String pin() {
        return pin;
    }

    /** The card number this block is tied to. */
    public String cardNumber() {
        return cardNumber;
    }

    @Override
    public byte[] toBytes() {
        String p1 = padRight("0" + Integer.toHexString(pin.length()) + pin);
        return HEX.parseHex(xorHex(p1, pinPad(cardNumber)));
    }

    @Override
    public byte[] toEncryptedBytes(String key) {
        return DesKeys.tripleDesEncrypt(HEX.parseHex(key), toBytes());
    }

    /**
     * A Visa pin verification value for this pin and card.
     *
     * @param pvvKey   the pin verification key, as hex
     * @param keyIndex which pin verification key was used
     */
    public String toPvv(String pvvKey, int keyIndex) {
        return VisaPvv.calculate(pin, pvvKey, keyIndex, cardNumber);
    }

    /** A Visa pin verification value using key index 1. */
    public String toPvv(String pvvKey) {
        return toPvv(pvvKey, VisaPvv.DEFAULT_KEY_INDEX);
    }

    /** The twelve card number digits that go into the block, as a 16 digit half. */
    private static String pinPad(String cardNumber) {
        return "0000" + Pins.rightmostBeforeCheckDigit(cardNumber, 12);
    }

    private static String padRight(String value) {
        return value + String.valueOf(FILLER).repeat(16 - value.length());
    }

    /** Exclusive or of two equal length hex strings, as lower case hex. */
    private static String xorHex(String left, String right) {
        StringBuilder result = new StringBuilder(left.length());
        for (int i = 0; i < left.length(); i++) {
            int value = Character.digit(left.charAt(i), 16) ^ Character.digit(right.charAt(i), 16);
            result.append(Character.forDigit(value, 16));
        }
        return result.toString();
    }

    /** Never shows the pin. */
    @Override
    public String toString() {
        return "Iso0PinBlock[" + pin.length() + " digit pin]";
    }
}
