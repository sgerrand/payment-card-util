package com.sgerrand.paymentcardutil.pin;

import com.sgerrand.paymentcardutil.crypto.DesKeys;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * ISO 9564-1 format 4.
 *
 * <p>Sixteen bytes: a format marker, the pin length, the pin, filler, then eight random bytes. The
 * random tail is what stops the same pin producing the same block twice, so no card number is
 * needed to build or read one.
 *
 * <pre>
 * CLPPPPaaaaaaaaAARRRRRRRRRRRRRRRR
 *
 * C = format, x'4'      P = pin
 * L = pin length        a, A = filler x'A'
 * R = random
 * </pre>
 *
 * <p>Format 4 blocks are meant for AES, so {@link #toEncryptedBytes} uses it.
 *
 * <p>The pin length goes in as a single hex digit, so a 12 digit pin writes {@code C}. cardutil
 * writes it as decimal text instead, which pushes the rest of the block along and produces a block
 * no reader can make sense of. Pins of 10 digits or more are the only case where the two disagree.
 */
public final class Iso4PinBlock implements PinBlock {

    private static final HexFormat HEX = HexFormat.of();

    private static final int BLOCK_BYTES = 16;

    /** Hex characters in the half of the block that holds the pin. */
    private static final int PIN_HALF_HEX = 16;

    private static final char FILLER = 'a';
    private static final int RANDOM_BYTES = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String pin;
    private final byte[] randomValue;

    /**
     * Builds a block with a fresh random tail.
     *
     * @param pin the pin, 4 to 12 digits
     */
    public Iso4PinBlock(String pin) {
        this(pin, randomBytes());
    }

    /**
     * Builds a block with a given random tail. Use this only to reproduce a known block, such as in
     * a test; real blocks need fresh randomness.
     *
     * @param pin the pin, 4 to 12 digits
     * @param randomValue eight bytes
     */
    public Iso4PinBlock(String pin, byte[] randomValue) {
        this.pin = Pins.checkPin(pin);
        if (randomValue.length != RANDOM_BYTES) {
            throw new IllegalArgumentException(
                    "The random part is " + RANDOM_BYTES + " bytes, was " + randomValue.length);
        }
        this.randomValue = randomValue.clone();
    }

    /** Reads a pin back out of a block. */
    public static Iso4PinBlock fromBytes(byte[] block) {
        if (block.length != BLOCK_BYTES) {
            throw new IllegalArgumentException(
                    "A format 4 pin block is " + BLOCK_BYTES + " bytes, was " + block.length);
        }
        String hex = HEX.formatHex(block);
        int pinLength = Character.digit(hex.charAt(1), 16);
        if (pinLength < Pins.MIN_PIN_LENGTH || pinLength > Pins.MAX_PIN_LENGTH) {
            throw new IllegalArgumentException(
                    "The block does not hold a pin: it says the pin is " + pinLength + " digits");
        }
        return new Iso4PinBlock(
                hex.substring(2, 2 + pinLength),
                java.util.Arrays.copyOfRange(block, RANDOM_BYTES, BLOCK_BYTES));
    }

    /**
     * Reads a pin out of an encrypted block.
     *
     * @param encryptedBlock the encrypted block
     * @param key the pin protection key, as hex
     */
    public static Iso4PinBlock fromEncryptedBytes(byte[] encryptedBlock, String key) {
        return fromBytes(DesKeys.aesDecrypt(HEX.parseHex(key), encryptedBlock));
    }

    @Override
    public String pin() {
        return pin;
    }

    /** The random part of the block. */
    public byte[] randomValue() {
        return randomValue.clone();
    }

    @Override
    public byte[] toBytes() {
        String head = "4" + Integer.toHexString(pin.length()) + pin;
        String padded = head + String.valueOf(FILLER).repeat(PIN_HALF_HEX - head.length());
        return HEX.parseHex(padded + HEX.formatHex(randomValue));
    }

    @Override
    public byte[] toEncryptedBytes(String key) {
        return DesKeys.aesEncrypt(HEX.parseHex(key), toBytes());
    }

    /**
     * A Visa pin verification value for this pin and a card.
     *
     * @param pvvKey the pin verification key, as hex
     * @param keyIndex which pin verification key was used
     * @param cardNumber the card number, which a format 4 block does not carry
     */
    public String toPvv(String pvvKey, int keyIndex, String cardNumber) {
        return VisaPvv.calculate(pin, pvvKey, keyIndex, cardNumber);
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /** Never shows the pin. */
    @Override
    public String toString() {
        return "Iso4PinBlock[" + pin.length() + " digit pin]";
    }
}
