package com.sgerrand.paymentcardutil.ipm;

import com.sgerrand.paymentcardutil.PaymentCardException;
import com.sgerrand.paymentcardutil.iso8583.Iso8583;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Message;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Options;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes a Mastercard IPM clearing file.
 *
 * <pre>{@code
 * try (IpmWriter writer = IpmWriter.blocked(Files.newOutputStream(path))) {
 *     writer.write(Iso8583Message.builder()
 *             .mti("1240")
 *             .de(2, "4444555566667777")
 *             .build());
 * }
 * }</pre>
 *
 * <p>Closing the writer finishes the file off. A file left unclosed has no end marker and, if
 * blocked, an unfinished last block.
 */
public final class IpmWriter implements java.io.Closeable {

    private final VbsWriter records;
    private final Iso8583Options options;

    private int recordNumber;

    private IpmWriter(OutputStream out, Iso8583Options options, boolean blocked) {
        this.records = blocked ? VbsWriter.blocked(out) : VbsWriter.of(out);
        this.options = options;
    }

    /** Writes a file with no blocking, using the default settings. */
    public static IpmWriter of(OutputStream out) {
        return of(out, Iso8583Options.defaults());
    }

    /** Writes a file with no blocking. */
    public static IpmWriter of(OutputStream out, Iso8583Options options) {
        return new IpmWriter(out, options, false);
    }

    /** Writes a file in 1014 byte blocks, using the default settings. */
    public static IpmWriter blocked(OutputStream out) {
        return blocked(out, Iso8583Options.defaults());
    }

    /** Writes a file in 1014 byte blocks. */
    public static IpmWriter blocked(OutputStream out, Iso8583Options options) {
        return new IpmWriter(out, options, true);
    }

    /** Writes a file, blocked or not as {@code blocked} says. */
    public static IpmWriter open(OutputStream out, Iso8583Options options, boolean blocked) {
        return new IpmWriter(out, options, blocked);
    }

    /** Adds a message to the file. */
    public void write(Iso8583Message message) throws IOException {
        byte[] record;
        try {
            record = Iso8583.serialize(message, options);
        } catch (PaymentCardException e) {
            // Nothing was written, so there are no offending bytes to hand
            // back. Which message in the run it was is still worth saying: a
            // file built from a thousand CSV rows names the row that is wrong.
            throw new IpmDataException(e.getMessage(), null, recordNumber + 1, e);
        }
        records.write(record);
        recordNumber++;
    }

    /** Adds several messages. */
    public void writeAll(Iterable<Iso8583Message> messages) throws IOException {
        for (Iso8583Message message : messages) {
            write(message);
        }
    }

    /**
     * Adds a record whose bytes are already in ISO 8583 form. For copying a file across without
     * reading its messages.
     */
    public void writeRaw(byte[] record) throws IOException {
        records.write(record);
    }

    /** Whether this writer is adding 1014 byte blocking. */
    public boolean isBlocked() {
        return records.isBlocked();
    }

    /** Finishes the file off and closes the stream underneath. */
    @Override
    public void close() throws IOException {
        records.close();
    }
}
