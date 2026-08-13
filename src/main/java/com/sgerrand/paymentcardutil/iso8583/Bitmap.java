package com.sgerrand.paymentcardutil.iso8583;

import java.util.Arrays;

/**
 * An ISO 8583 bitmap: the bits that say which data elements a message holds.
 *
 * <p>The primary bitmap is 8 bytes and covers fields 1 to 64. If bit 1 is set,
 * a secondary bitmap follows and covers fields 65 to 128. Fields are numbered
 * from 1.
 */
public final class Bitmap {

    private static final int PRIMARY_BYTES = 8;
    private static final int SECONDARY_BYTES = 8;

    private byte[] bytes;

    private Bitmap(byte[] bytes) {
        this.bytes = bytes;
    }

    /** An empty bitmap with only the primary half. */
    public static Bitmap empty() {
        return new Bitmap(new byte[PRIMARY_BYTES]);
    }

    /**
     * Reads a bitmap from {@code source}, starting at {@code offset}. Reads 8
     * or 16 bytes depending on whether bit 1 is set.
     *
     * @throws IllegalArgumentException if {@code source} is too short
     */
    public static Bitmap read(byte[] source, int offset) {
        if (source.length - offset < PRIMARY_BYTES) {
            throw new IllegalArgumentException("Not enough bytes for a primary bitmap");
        }
        boolean hasSecondary = (source[offset] & 0x80) != 0;
        int length = hasSecondary ? PRIMARY_BYTES + SECONDARY_BYTES : PRIMARY_BYTES;
        if (source.length - offset < length) {
            throw new IllegalArgumentException("Not enough bytes for a secondary bitmap");
        }
        return new Bitmap(Arrays.copyOfRange(source, offset, offset + length));
    }

    /** How many bytes this bitmap takes up in a message: 8 or 16. */
    public int lengthInBytes() {
        return bytes.length;
    }

    /** The highest field number this bitmap can hold: 64 or 128. */
    public int capacity() {
        return bytes.length * 8;
    }

    /**
     * Whether the message holds the given field.
     *
     * @param field the field number, from 1
     */
    public boolean isSet(int field) {
        checkField(field);
        if (field > capacity()) {
            return false;
        }
        int index = field - 1;
        return (bytes[index / 8] & (0x80 >>> (index % 8))) != 0;
    }

    /**
     * Marks the given field as present. Grows the bitmap to 16 bytes, and sets
     * bit 1, if the field number needs a secondary bitmap.
     *
     * @param field the field number, from 1
     */
    public void set(int field) {
        checkField(field);
        if (field > capacity()) {
            bytes = Arrays.copyOf(bytes, PRIMARY_BYTES + SECONDARY_BYTES);
        }
        int index = field - 1;
        bytes[index / 8] |= (byte) (0x80 >>> (index % 8));
        if (field > 64) {
            bytes[0] |= (byte) 0x80;
        }
    }

    /** The field numbers this bitmap has set, in order. */
    public int[] fields() {
        int count = 0;
        for (int field = 1; field <= capacity(); field++) {
            if (isSet(field)) {
                count++;
            }
        }
        int[] result = new int[count];
        int next = 0;
        for (int field = 1; field <= capacity(); field++) {
            if (isSet(field)) {
                result[next++] = field;
            }
        }
        return result;
    }

    /** A copy of the raw bytes. */
    public byte[] toByteArray() {
        return bytes.clone();
    }

    private static void checkField(int field) {
        if (field < 1 || field > PRIMARY_BYTES * 8 + SECONDARY_BYTES * 8) {
            throw new IllegalArgumentException("Field number out of range: " + field);
        }
    }

    @Override
    public String toString() {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append("%02X".formatted(b));
        }
        return hex.toString();
    }
}
