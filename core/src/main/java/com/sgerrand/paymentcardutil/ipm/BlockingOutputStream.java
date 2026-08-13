package com.sgerrand.paymentcardutil.ipm;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Adds 1014 byte blocking to whatever is written through it.
 *
 * <p>Every {@value Blocking#DATA_SIZE} bytes of data are followed by two filler
 * bytes. Closing the stream fills the part-written block out to a whole block,
 * so the finished file is always a multiple of {@value Blocking#BLOCK_SIZE}
 * bytes.
 */
public final class BlockingOutputStream extends FilterOutputStream {

    private int remainingInBlock = Blocking.DATA_SIZE;
    private boolean finished;

    public BlockingOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[] {(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        int position = offset;
        int left = length;

        // Not enough to finish the block: just add to it.
        if (left < remainingInBlock) {
            out.write(buffer, position, left);
            remainingInBlock -= left;
            return;
        }

        // Finish the block that is open, then the filler it has earned.
        out.write(buffer, position, remainingInBlock);
        writePadding(2);
        position += remainingInBlock;
        left -= remainingInBlock;

        // Whole blocks in the middle. Strictly greater than, so a run that ends
        // exactly on a boundary leaves its filler until more data arrives, or
        // until the file is finished off.
        while (left > Blocking.DATA_SIZE) {
            out.write(buffer, position, Blocking.DATA_SIZE);
            writePadding(2);
            position += Blocking.DATA_SIZE;
            left -= Blocking.DATA_SIZE;
        }

        out.write(buffer, position, left);
        remainingInBlock = Blocking.DATA_SIZE - left;
    }

    /**
     * Fills the part-written block out to a whole block.
     *
     * <p>Called by {@link #close()}. Calling it twice does nothing the second
     * time, so a stream closed after an explicit finish is still well formed.
     */
    public void finish() throws IOException {
        if (finished) {
            return;
        }
        finished = true;
        writePadding(remainingInBlock + 2);
        remainingInBlock = Blocking.DATA_SIZE;
        out.flush();
    }

    @Override
    public void close() throws IOException {
        try {
            finish();
        } finally {
            super.close();
        }
    }

    private void writePadding(int count) throws IOException {
        byte[] padding = new byte[count];
        java.util.Arrays.fill(padding, Blocking.PAD);
        out.write(padding);
    }
}
