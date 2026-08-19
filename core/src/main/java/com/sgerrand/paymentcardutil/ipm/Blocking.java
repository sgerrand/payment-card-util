package com.sgerrand.paymentcardutil.ipm;

/**
 * The 1014 byte block layout used to ship Mastercard files.
 *
 * <p>The data is cut into runs of {@value #DATA_SIZE} bytes, and two {@code x'40'} bytes are added
 * after each run, making blocks of {@value #BLOCK_SIZE}. The last block is filled out with the same
 * byte so every file is a whole number of blocks.
 */
public final class Blocking {

    /** Bytes in one block, filler included. */
    public static final int BLOCK_SIZE = 1014;

    /** Bytes of real data in one block. */
    public static final int DATA_SIZE = 1012;

    /** The filler byte: a space in EBCDIC. */
    public static final byte PAD = 0x40;

    private Blocking() {}
}
