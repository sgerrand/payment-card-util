package com.sgerrand.paymentcardutil.ipm;

import com.sgerrand.paymentcardutil.PaymentCardException;
import com.sgerrand.paymentcardutil.iso8583.Iso8583;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Message;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Options;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Reads the messages out of a Mastercard IPM clearing file.
 *
 * <pre>{@code
 * try (IpmReader reader = IpmReader.blocked(Files.newInputStream(path))) {
 *     for (Iso8583Message message : reader) {
 *         System.out.println(message.mti() + " " + message.text(2).orElse(""));
 *     }
 * }
 * }</pre>
 *
 * <p>Use {@link #of} for a file with no blocking, and pass {@link Iso8583Options} for a file in
 * EBCDIC or with a different layout. {@link IpmInfo#inspect} can work out both from the file
 * itself.
 */
public final class IpmReader
        implements Iterable<Iso8583Message>, Iterator<Iso8583Message>, Closeable {

    private final VbsReader records;
    private final Iso8583Options options;

    private IpmReader(VbsReader records, Iso8583Options options) {
        this.records = records;
        this.options = options;
    }

    /** Reads a file with no blocking, using the default settings. */
    public static IpmReader of(InputStream in) {
        return of(in, Iso8583Options.defaults());
    }

    /** Reads a file with no blocking. */
    public static IpmReader of(InputStream in, Iso8583Options options) {
        return new IpmReader(VbsReader.of(in, options.config()), options);
    }

    /** Reads a file in 1014 byte blocks, using the default settings. */
    public static IpmReader blocked(InputStream in) {
        return blocked(in, Iso8583Options.defaults());
    }

    /** Reads a file in 1014 byte blocks. */
    public static IpmReader blocked(InputStream in, Iso8583Options options) {
        return new IpmReader(VbsReader.blocked(in, options.config()), options);
    }

    /**
     * Reads a file, blocked or not as {@code blocked} says.
     *
     * <p>Handy after {@link IpmInfo#inspect}, which works the answer out from the file.
     */
    public static IpmReader open(InputStream in, Iso8583Options options, boolean blocked) {
        return blocked ? blocked(in, options) : of(in, options);
    }

    @Override
    public Iterator<Iso8583Message> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        return records.hasNext();
    }

    /**
     * @throws IpmDataException if the next record is not a message this can read
     */
    @Override
    public Iso8583Message next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more messages");
        }
        byte[] record = records.next();
        try {
            return Iso8583.parse(record, options);
        } catch (PaymentCardException e) {
            // The parser said what is wrong but has no idea which record it was
            // reading; that is this reader's half of the story. Keeping the
            // parser's own words means the report leads with the reason rather
            // than with a sentence saying a message could not be read.
            throw new IpmDataException(
                    e.getMessage(), records.lastRecord(), records.recordNumber(), e);
        }
    }

    /** Which record was read last, counting from 1. */
    public int recordNumber() {
        return records.recordNumber();
    }

    @Override
    public void close() throws IOException {
        records.close();
    }
}
