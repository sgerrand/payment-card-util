package com.sgerrand.paymentcardutil.ipm;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Strips the block padding from an IPM file that was sent in 1014 byte blocks.
 *
 * <p>In that layout the file is cut into 1014 byte blocks. The first 1012 bytes
 * of each block are real data; the last 2 are filler. This stream hands back
 * only the real data, so callers see one clean run of bytes.
 *
 * <p>Wrap the file in this stream only if it really is blocked. Many IPM files
 * are delivered unblocked, and passing one through here would drop good bytes.
 */
public final class DeblockingInputStream extends FilterInputStream {

    /** How many bytes make up one block, padding included. */
    public static final int BLOCK_SIZE = 1014;

    /** How many bytes of each block hold real data. */
    public static final int DATA_SIZE = 1012;

    private int positionInBlock;

    public DeblockingInputStream(InputStream in) {
        super(in);
    }

    @Override
    public int read() throws IOException {
        if (!skipPaddingIfNeeded()) {
            return -1;
        }
        int b = in.read();
        if (b >= 0) {
            positionInBlock++;
        }
        return b;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        if (!skipPaddingIfNeeded()) {
            return -1;
        }
        int allowed = Math.min(length, DATA_SIZE - positionInBlock);
        int read = in.read(buffer, offset, allowed);
        if (read > 0) {
            positionInBlock += read;
        }
        return read;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    /**
     * Moves past the 2 filler bytes if we are at the end of a block.
     *
     * @return false if the stream ended while skipping filler
     */
    private boolean skipPaddingIfNeeded() throws IOException {
        if (positionInBlock < DATA_SIZE) {
            return true;
        }
        long toSkip = BLOCK_SIZE - DATA_SIZE;
        while (toSkip > 0) {
            long skipped = in.skip(toSkip);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    return false;
                }
                skipped = 1;
            }
            toSkip -= skipped;
        }
        positionInBlock = 0;
        return true;
    }
}
