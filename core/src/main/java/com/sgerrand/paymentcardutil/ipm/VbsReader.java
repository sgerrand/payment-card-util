package com.sgerrand.paymentcardutil.ipm;

import com.sgerrand.paymentcardutil.config.IsoConfig;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Reads the records out of a variable blocked (VBS) file.
 *
 * <p>Each record is a 4 byte length, most significant byte first, then that many
 * bytes of data. A length of zero marks the end of the file.
 *
 * <pre>{@code
 * try (VbsReader reader = VbsReader.blocked(Files.newInputStream(path))) {
 *     for (byte[] record : reader) {
 *         System.out.println(record.length);
 *     }
 * }
 * }</pre>
 */
public final class VbsReader implements Iterable<byte[]>, Iterator<byte[]>, Closeable {

    private static final int LENGTH_PREFIX = 4;

    private final InputStream in;
    private final int maxRecordLength;

    private byte[] pending;
    private boolean exhausted;
    private int recordNumber;
    private byte[] lastRecord;

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
        return new VbsReader(new UnblockingInputStream(in), IsoConfig.DEFAULT_MAX_VBS_RECORD_LENGTH);
    }

    /** Reads a file in 1014 byte blocks, with a record length limit from the config. */
    public static VbsReader blocked(InputStream in, IsoConfig config) {
        return new VbsReader(new UnblockingInputStream(in), config.maxVbsRecordLength());
    }

    /**
     * Which record was read last, counting from 1. Zero before the first read.
     */
    public int recordNumber() {
        return recordNumber;
    }

    /**
     * The record read last, with its length prefix still on the front. Useful
     * for reporting a bad record back to whoever sent the file.
     */
    public byte[] lastRecord() {
        return lastRecord == null ? null : lastRecord.clone();
    }

    @Override
    public Iterator<byte[]> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        if (pending != null) {
            return true;
        }
        if (exhausted) {
            return false;
        }
        pending = readRecord();
        if (pending == null) {
            exhausted = true;
            return false;
        }
        return true;
    }

    @Override
    public byte[] next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more records");
        }
        byte[] record = pending;
        pending = null;
        return record;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    /**
     * @return the next record, or {@code null} at the end of the file
     * @throws IpmDataException  if the framing does not add up
     * @throws UncheckedIOException if the stream fails
     */
    private byte[] readRecord() {
        try {
            byte[] lengthBytes = in.readNBytes(LENGTH_PREFIX);
            if (lengthBytes.length < LENGTH_PREFIX) {
                // A file whose writer never wrote the zero length end marker.
                // cardutil accepts this and stops, so this does too.
                return null;
            }

            long length = Integer.toUnsignedLong(
                    ((lengthBytes[0] & 0xFF) << 24)
                            | ((lengthBytes[1] & 0xFF) << 16)
                            | ((lengthBytes[2] & 0xFF) << 8)
                            | (lengthBytes[3] & 0xFF));

            if (length > maxRecordLength) {
                throw new IpmDataException(
                        "Record is " + length + " bytes, past the " + maxRecordLength
                                + " byte limit, which usually means the file is not what it claims to be",
                        lengthBytes, recordNumber + 1, null);
            }
            if (length == 0) {
                return null;
            }

            byte[] record = in.readNBytes((int) length);
            if (record.length != length) {
                byte[] context = new byte[lengthBytes.length + record.length];
                System.arraycopy(lengthBytes, 0, context, 0, lengthBytes.length);
                System.arraycopy(record, 0, context, lengthBytes.length, record.length);
                throw new IpmDataException(
                        "Record says it is " + length + " bytes, but only " + record.length + " could be read",
                        context, recordNumber + 1, null);
            }

            recordNumber++;
            lastRecord = new byte[lengthBytes.length + record.length];
            System.arraycopy(lengthBytes, 0, lastRecord, 0, lengthBytes.length);
            System.arraycopy(record, 0, lastRecord, lengthBytes.length, record.length);
            return record;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
