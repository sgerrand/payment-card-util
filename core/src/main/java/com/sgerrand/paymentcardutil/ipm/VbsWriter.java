package com.sgerrand.paymentcardutil.ipm;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes a variable blocked (VBS) file.
 *
 * <p>Each record is written with a 4 byte length in front of it. Closing the writer adds the zero
 * length record that marks the end of the file, and fills out the last block if the file is
 * blocked.
 *
 * <pre>{@code
 * try (VbsWriter writer = VbsWriter.blocked(Files.newOutputStream(path))) {
 *     writer.write("This is the record".getBytes(StandardCharsets.ISO_8859_1));
 * }
 * }</pre>
 *
 * <p>A file whose writer was never closed has no end marker. Readers here cope with that, but other
 * systems may not.
 */
public final class VbsWriter implements Closeable {

    private final OutputStream out;
    private final BlockingOutputStream blocking;

    private VbsWriter(OutputStream out, boolean blocked) {
        if (blocked) {
            this.blocking = new BlockingOutputStream(out);
            this.out = this.blocking;
        } else {
            this.blocking = null;
            this.out = out;
        }
    }

    /** Writes a file with no blocking. */
    public static VbsWriter of(OutputStream out) {
        return new VbsWriter(out, false);
    }

    /** Writes a file in 1014 byte blocks. */
    public static VbsWriter blocked(OutputStream out) {
        return new VbsWriter(out, true);
    }

    /** Adds a record to the file. */
    public void write(byte[] record) throws IOException {
        out.write(Vbs.lengthPrefix(record.length));
        out.write(record);
    }

    /** Adds several records. */
    public void writeAll(Iterable<byte[]> records) throws IOException {
        for (byte[] record : records) {
            write(record);
        }
    }

    /** Whether this writer is adding 1014 byte blocking. */
    public boolean isBlocked() {
        return blocking != null;
    }

    /**
     * Finishes the file: writes the zero length end marker, fills out the last block if blocked,
     * and closes the stream underneath.
     */
    @Override
    public void close() throws IOException {
        try {
            out.write(new byte[4]);
            if (blocking != null) {
                blocking.finish();
            }
            out.flush();
        } finally {
            out.close();
        }
    }
}
