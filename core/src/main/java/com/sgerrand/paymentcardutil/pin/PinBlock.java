package com.sgerrand.paymentcardutil.pin;

/**
 * A pin wrapped up in one of the standard block formats, ready to be encrypted.
 *
 * <p>A pin is never sent on its own. It is padded and mixed with other data
 * into a fixed size block, so that the same pin does not always produce the
 * same ciphertext.
 *
 * @see Iso0PinBlock ISO 9564 format 0
 * @see Iso4PinBlock ISO 9564 format 4
 */
public sealed interface PinBlock permits Iso0PinBlock, Iso4PinBlock {

    /** The pin this block holds. */
    String pin();

    /** The block as it goes on the wire, before encryption. */
    byte[] toBytes();

    /**
     * The block encrypted under a pin protection key.
     *
     * @param key the key as hex
     */
    byte[] toEncryptedBytes(String key);
}
