package com.sgerrand.paymentcardutil.crypto;

import java.util.HexFormat;

/**
 * Building and checking payment keys.
 *
 * <p>A zone master key is not handed over in one piece. It is split into components, each given to
 * a different person, and only becomes a key when they are combined. Combining is a bitwise
 * exclusive or, so no one component reveals anything about the result.
 *
 * <p>A key check value proves two parties hold the same key without either showing it: encrypt a
 * block of zeroes under the key and compare the first few digits.
 */
public final class Keys {

    /** Digits in a key check value, unless another length is asked for. */
    public static final int DEFAULT_KCV_LENGTH = 6;

    /** Bytes in the key a zone master key is built as. */
    private static final int KEY_LENGTH = 16;

    private static final HexFormat HEX = HexFormat.of();

    private Keys() {}

    /**
     * Combines key components into a clear key.
     *
     * @param components each component as hex, all the same length
     * @return the combined key as lower case hex
     * @throws IllegalArgumentException if no components are given, or they are not all the same
     *     length
     */
    public static String zoneMasterKey(String... components) {
        if (components.length == 0) {
            throw new IllegalArgumentException("A key needs at least one component");
        }
        byte[] combined = new byte[KEY_LENGTH];
        for (String component : components) {
            byte[] bytes = parseHex(component, "key component");
            if (bytes.length != KEY_LENGTH) {
                throw new IllegalArgumentException(
                        "A key component is " + KEY_LENGTH + " bytes, was " + bytes.length);
            }
            for (int i = 0; i < KEY_LENGTH; i++) {
                combined[i] ^= bytes[i];
            }
        }
        return hex(combined);
    }

    /**
     * The key check value of a key: the start of a block of zeroes encrypted under it.
     *
     * @param key the key as hex
     */
    public static String keyCheckValue(String key) {
        return keyCheckValue(key, DEFAULT_KCV_LENGTH);
    }

    /**
     * The key check value of a key, to a given number of hex digits.
     *
     * @param key the key as hex
     * @param length how many hex digits to return
     */
    public static String keyCheckValue(String key, int length) {
        if (length < 1) {
            throw new IllegalArgumentException("A check value needs at least one digit");
        }
        byte[] encrypted = DesKeys.tripleDesEncrypt(parseHex(key, "key"), new byte[KEY_LENGTH]);
        String full = hex(encrypted);
        return full.substring(0, Math.min(length, full.length()));
    }

    /**
     * Encrypts one key under another, for sending it somewhere.
     *
     * @param keyToEncrypt the key being sent, as hex
     * @param masterKey the key it travels under, as hex
     * @return the encrypted key as lower case hex
     */
    public static String encryptKey(String keyToEncrypt, String masterKey) {
        return hex(
                DesKeys.tripleDesEncrypt(
                        parseHex(masterKey, "master key"),
                        parseHex(keyToEncrypt, "key to encrypt")));
    }

    /** Undoes {@link #encryptKey}. */
    public static String decryptKey(String encryptedKey, String masterKey) {
        return hex(
                DesKeys.tripleDesDecrypt(
                        parseHex(masterKey, "master key"),
                        parseHex(encryptedKey, "encrypted key")));
    }

    /**
     * Combines components and encrypts the result under a master key, which is how a zone master
     * key is normally loaded.
     *
     * @return the encrypted key and its check value
     */
    public static EncryptedKey encryptedZoneMasterKey(String masterKey, String... components) {
        String clear = zoneMasterKey(components);
        return new EncryptedKey(encryptKey(clear, masterKey), keyCheckValue(clear));
    }

    /**
     * A key ready to be sent somewhere, with the check value that proves it arrived intact.
     *
     * @param encryptedKey the key, encrypted under a master key, as hex
     * @param keyCheckValue the check value of the clear key
     */
    public record EncryptedKey(String encryptedKey, String keyCheckValue) {}

    private static byte[] parseHex(String value, String what) {
        try {
            return HEX.parseHex(value);
        } catch (IllegalArgumentException e) {
            throw new CryptoException("The " + what + " is not valid hex", e);
        }
    }

    /** Lower case, matching what cardutil returns. */
    private static String hex(byte[] bytes) {
        return HEX.formatHex(bytes);
    }
}
