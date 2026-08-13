package com.sgerrand.paymentcardutil.ipm;

import com.sgerrand.paymentcardutil.iso8583.Bitmap;
import com.sgerrand.paymentcardutil.iso8583.Mti;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Reads messages one at a time from an IPM file.
 *
 * <p>Each message in the file starts with a Record Descriptor Word: 4 bytes
 * holding the length of the message that follows. A length of zero marks the
 * end of the file.
 *
 * <p>Example:
 * <pre>{@code
 * try (IpmReader reader = IpmReader.blocked(Files.newInputStream(path))) {
 *     IpmMessage message;
 *     while ((message = reader.next()) != null) {
 *         System.out.println(message);
 *     }
 * }
 * }</pre>
 */
public final class IpmReader implements Closeable {

    /** The character set IPM files use for text fields. */
    public static final Charset EBCDIC = Charset.forName("IBM1047");

    private static final int RDW_LENGTH = 4;
    private static final int MTI_LENGTH = 4;
    private static final int MAX_MESSAGE_LENGTH = 1 << 20;

    private final DataInputStream in;

    private IpmReader(InputStream in) {
        this.in = new DataInputStream(in);
    }

    /** Reads a file that has no block padding. */
    public static IpmReader unblocked(InputStream in) {
        return new IpmReader(in);
    }

    /** Reads a file sent in 1014 byte blocks. */
    public static IpmReader blocked(InputStream in) {
        return new IpmReader(new DeblockingInputStream(in));
    }

    /**
     * Reads the next message.
     *
     * @return the message, or {@code null} at the end of the file
     * @throws IOException if the stream fails or the file is malformed
     */
    public IpmMessage next() throws IOException {
        int length = readRecordLength();
        if (length <= 0) {
            return null;
        }
        byte[] record = in.readNBytes(length);
        if (record.length < length) {
            throw new EOFException("Message ended early: wanted " + length + " bytes, got " + record.length);
        }
        return parse(record);
    }

    /**
     * Turns one raw message, with its RDW already stripped, into an
     * {@link IpmMessage}.
     *
     * @throws IOException if the message is too short to hold an MTI and bitmap
     */
    public static IpmMessage parse(byte[] record) throws IOException {
        if (record.length < MTI_LENGTH) {
            throw new IOException("Message too short to hold an MTI: " + record.length + " bytes");
        }
        Mti mti = new Mti(new String(record, 0, MTI_LENGTH, EBCDIC));
        Bitmap bitmap;
        try {
            bitmap = Bitmap.read(record, MTI_LENGTH);
        } catch (IllegalArgumentException e) {
            throw new IOException("Message too short to hold a bitmap", e);
        }
        int bodyStart = MTI_LENGTH + bitmap.lengthInBytes();
        byte[] body = new byte[record.length - bodyStart];
        System.arraycopy(record, bodyStart, body, 0, body.length);
        return new IpmMessage(mti, bitmap, body);
    }

    /**
     * @return the length from the next RDW, or 0 at the end of the file
     */
    private int readRecordLength() throws IOException {
        byte[] rdw = in.readNBytes(RDW_LENGTH);
        if (rdw.length == 0) {
            return 0;
        }
        if (rdw.length < RDW_LENGTH) {
            throw new EOFException("File ended in the middle of a record descriptor word");
        }
        int length = ((rdw[0] & 0xFF) << 24)
                | ((rdw[1] & 0xFF) << 16)
                | ((rdw[2] & 0xFF) << 8)
                | (rdw[3] & 0xFF);
        if (length < 0 || length > MAX_MESSAGE_LENGTH) {
            throw new IOException("Record length out of range: " + length);
        }
        return length;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
