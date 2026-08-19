package com.sgerrand.paymentcardutil.ipm;

import com.sgerrand.paymentcardutil.config.IsoConfig;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Iterator;

/**
 * Reads the records out of a variable blocked (VBS) file.
 *
 * <p>Each record is a 4 byte length, most significant byte first, then that many bytes of data. A
 * length of zero marks the end of the file.
 *
 * <pre>{@code
 * try (VbsReader reader = VbsReader.blocked(Files.newInputStream(path))) {
 *     for (byte[] record : reader) {
 *         System.out.println(record.length);
 *     }
 * }
 * }</pre>
 */
public final class VbsReader extends LookAheadIterator<byte[]>
        implements Iterable<byte[]>, Closeable {

    private final InputStream in;
    private final int maxRecordLength;

    private int recordNumber;
    private byte[] lastLengthBytes;
    private byte[] lastRecordBody;

    private VbsReader(InputStream in, int maxRecordLength) {
        this.in = in;
        this.maxRecordLength = maxRecordLength;
    }

    /** Reads a file that is not blocked. */
    public static VbsReader of(InputStream in) {
        return new VbsReader(in, IsoConfig.DEFAULT_MAX_VBS_RECORD_LENGTH);
    }

    /** Reads a file that is not blocked, with a record length limit from the config. */
    public static VbsReader of(InputStream in, IsoConfig config) {
        return new VbsReader(in, config.maxVbsRecordLength());
    }

    /** Reads a file in 1014 byte blocks. */
    public static VbsReader blocked(InputStream in) {
        return new VbsReader(
                new UnblockingInputStream(in), IsoConfig.DEFAULT_MAX_VBS_RECORD_LENGTH);
    }

    /** Reads a file in 1014 byte blocks, with a record length limit from the config. */
    public static VbsReader blocked(InputStream in, IsoConfig config) {
        return new VbsReader(new UnblockingInputStream(in), config.maxVbsRecordLength());
    }

    /** Which record was read last, counting from 1. Zero before the first read. */
    public int recordNumber() {
        return recordNumber;
    }

    /**
     * The record read last, with its length prefix still on the front. Useful for reporting a bad
     * record back to whoever sent the file.
     */
    public byte[] lastRecord() {
        // Joined only when someone asks, which is on the error path. Keeping a
        // second copy of every record just in case would double the reader's
        // memory traffic for a file that reads cleanly.
        return lastRecordBody == null ? null : Vbs.withPrefix(lastLengthBytes, lastRecordBody);
    }

    @Override
    public Iterator<byte[]> iterator() {
        return this;
    }

    @Override
    String endMessage() {
        return "No more records";
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    /**
     * @return the next record, or {@code null} at the end of the file
     * @throws IpmDataException if the framing does not add up
     * @throws UncheckedIOException if the stream fails
     */
    @Override
    byte[] readNext() {
        try {
            byte[] lengthBytes = in.readNBytes(Vbs.LENGTH_PREFIX);
            if (lengthBytes.length < Vbs.LENGTH_PREFIX) {
                // A file whose writer never wrote the zero length end marker.
                // cardutil accepts this and stops, so this does too.
                return null;
            }

            long length = Vbs.recordLength(lengthBytes, 0);

            if (length > maxRecordLength) {
                throw new IpmDataException(
                        "Record is "
                                + length
                                + " bytes, past the "
                                + maxRecordLength
                                + " byte limit, which usually means the file is not what it claims to be",
                        lengthBytes,
                        recordNumber + 1,
                        null);
            }
            if (length == 0) {
                return null;
            }

            byte[] record = in.readNBytes((int) length);
            if (record.length != length) {
                byte[] context = Vbs.withPrefix(lengthBytes, record);
                throw new IpmDataException(
                        "Record says it is "
                                + length
                                + " bytes, but only "
                                + record.length
                                + " could be read",
                        context,
                        recordNumber + 1,
                        null);
            }

            recordNumber++;
            lastLengthBytes = lengthBytes;
            lastRecordBody = record;
            return record;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
