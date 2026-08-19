package com.sgerrand.paymentcardutil.ipm;

/**
 * The 4 byte length that sits in front of every record in a variable blocked file, most significant
 * byte first.
 *
 * <p>Kept in one place so the reader, the writer and the file sniffer cannot drift apart on the
 * byte order.
 */
final class Vbs {

    /** Bytes of length in front of each record. */
    static final int LENGTH_PREFIX = 4;

    private Vbs() {}

    /**
     * Reads a record length.
     *
     * <p>Returned as a {@code long} because a damaged file can claim a length with the top bit set,
     * which is not a length any caller should see as negative.
     *
     * @param from where the 4 length bytes start
     */
    static long recordLength(byte[] bytes, int from) {
        return Integer.toUnsignedLong(
                ((bytes[from] & 0xFF) << 24)
                        | ((bytes[from + 1] & 0xFF) << 16)
                        | ((bytes[from + 2] & 0xFF) << 8)
                        | (bytes[from + 3] & 0xFF));
    }

    /** Writes a record length. */
    static byte[] lengthPrefix(int length) {
        return new byte[] {
            (byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length
        };
    }

    /** A record with its length prefix back on the front. */
    static byte[] withPrefix(byte[] prefix, byte[] record) {
        byte[] joined = new byte[prefix.length + record.length];
        System.arraycopy(prefix, 0, joined, 0, prefix.length);
        System.arraycopy(record, 0, joined, prefix.length, record.length);
        return joined;
    }
}
