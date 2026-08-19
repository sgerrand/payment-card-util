package com.sgerrand.paymentcardutil.crypto;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Triple DES and AES in ECB mode, as payment key handling uses them.
 *
 * <p>Payment systems quote Triple DES keys as 16 hex bytes, meaning a two key bundle where the
 * third key repeats the first. Java wants all 24 bytes, so a 16 byte key is stretched here before
 * use.
 *
 * <p>ECB with no padding is what the standards specify for these operations. It is the right mode
 * for encrypting a single fixed size block such as a pin block or a key, and the wrong mode for
 * anything longer.
 */
public final class DesKeys {

    private static final String TDES = "DESede";
    private static final String TDES_ECB = "DESede/ECB/NoPadding";
    private static final String AES = "AES";
    private static final String AES_ECB = "AES/ECB/NoPadding";

    /** Bytes in a two key Triple DES bundle, as payment systems quote them. */
    public static final int TDES_TWO_KEY_LENGTH = 16;

    /** Bytes in the three key bundle Java wants. */
    public static final int TDES_THREE_KEY_LENGTH = 24;

    private DesKeys() {}

    /** Encrypts with Triple DES. */
    public static byte[] tripleDesEncrypt(byte[] key, byte[] data) {
        return crypt(TDES_ECB, tripleDesKey(key), Cipher.ENCRYPT_MODE, data);
    }

    /** Decrypts with Triple DES. */
    public static byte[] tripleDesDecrypt(byte[] key, byte[] data) {
        return crypt(TDES_ECB, tripleDesKey(key), Cipher.DECRYPT_MODE, data);
    }

    /** Encrypts with AES. */
    public static byte[] aesEncrypt(byte[] key, byte[] data) {
        return crypt(AES_ECB, new SecretKeySpec(key, AES), Cipher.ENCRYPT_MODE, data);
    }

    /** Decrypts with AES. */
    public static byte[] aesDecrypt(byte[] key, byte[] data) {
        return crypt(AES_ECB, new SecretKeySpec(key, AES), Cipher.DECRYPT_MODE, data);
    }

    /**
     * Turns a quoted Triple DES key into the 24 bytes Java wants.
     *
     * @param key 16 or 24 bytes. 16 means a two key bundle, where the first 8 bytes are repeated on
     *     the end
     * @throws IllegalArgumentException if the key is any other length
     */
    static SecretKeySpec tripleDesKey(byte[] key) {
        byte[] full;
        if (key.length == TDES_THREE_KEY_LENGTH) {
            full = key;
        } else if (key.length == TDES_TWO_KEY_LENGTH) {
            full = Arrays.copyOf(key, TDES_THREE_KEY_LENGTH);
            System.arraycopy(key, 0, full, TDES_TWO_KEY_LENGTH, 8);
        } else {
            throw new IllegalArgumentException(
                    "A Triple DES key is 16 or 24 bytes, was " + key.length);
        }
        return new SecretKeySpec(full, TDES);
    }

    private static byte[] crypt(String transformation, SecretKeySpec key, int mode, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(transformation);
            cipher.init(mode, key);
            return cipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Cannot run " + transformation, e);
        }
    }
}
