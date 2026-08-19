package com.sgerrand.paymentcardutil.ipm;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Strips 1014 byte blocking, handing back only the data.
 *
 * <p>Blocks are read whole and the two filler bytes on the end of each are
 * dropped. A short final block is passed on as it stands, which is what happens
 * when a file was cut off before it was filled out.
 *
 * <p>Only wrap a file that really is blocked. Running an unblocked file through
 * this drops two good bytes out of every 1014.
 */
public final class UnblockingInputStream extends FilterInputStream {

    private final byte[] block = new byte[Blocking.BLOCK_SIZE];
    private int available;
    private int position;
    private boolean endOfFile;

    public UnblockingInputStream(InputStream in) {
        super(in);
    }

    @Override
    public int read() throws IOException {
        if (!fill()) {
            return -1;
        }
        return block[position++] & 0xFF;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        if (!fill()) {
            return -1;
        }
        int count = Math.min(length, available - position);
        System.arraycopy(block, position, buffer, offset, count);
        position += count;
        return count;
    }

    @Override
    public int available() throws IOException {
        return available - position;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    /**
     * Makes sure at least one unread data byte is buffered.
     *
     * @return false at the end of the file
     */
    private boolean fill() throws IOException {
        if (position < available) {
            return true;
        }
        if (endOfFile) {
            return false;
        }
        int read = in.readNBytes(block, 0, Blocking.BLOCK_SIZE);
        if (read <= 0) {
            endOfFile = true;
            return false;
        }
        if (read < Blocking.BLOCK_SIZE) {
            // Truncated final block: keep what is there, filler and all.
            endOfFile = true;
            available = read;
        } else {
            available = Blocking.DATA_SIZE;
        }
        position = 0;
        return true;
    }
}
