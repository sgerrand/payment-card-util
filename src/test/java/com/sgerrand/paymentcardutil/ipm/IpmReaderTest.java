package com.sgerrand.paymentcardutil.ipm;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IpmReaderTest {

    @Test
    void readsOneMessage() throws IOException {
        byte[] file = file(message("1240", primaryBitmapWith(2, 3), "PAYLOAD"));

        try (IpmReader reader = IpmReader.unblocked(new ByteArrayInputStream(file))) {
            IpmMessage message = reader.next();
            assertNotNull(message);
            assertEquals("1240", message.mti().digits());
            assertArrayEquals(new int[] {2, 3}, message.presentFields());
            assertEquals("PAYLOAD", new String(message.body(), IpmReader.EBCDIC));
            assertNull(reader.next(), "the file holds only one message");
        }
    }

    @Test
    void readsSeveralMessagesInOrder() throws IOException {
        byte[] file = file(
                message("1240", primaryBitmapWith(2), "ONE"),
                message("1644", primaryBitmapWith(3), "TWO"));

        try (IpmReader reader = IpmReader.unblocked(new ByteArrayInputStream(file))) {
            assertEquals("1240", reader.next().mti().digits());
            assertEquals("1644", reader.next().mti().digits());
            assertNull(reader.next());
        }
    }

    @Test
    void readsAMessageWithASecondaryBitmap() throws IOException {
        byte[] bitmap = new byte[16];
        bitmap[0] = (byte) 0x80;
        bitmap[8] = 0x40;
        byte[] file = file(message("1240", bitmap, "BODY"));

        try (IpmReader reader = IpmReader.unblocked(new ByteArrayInputStream(file))) {
            IpmMessage message = reader.next();
            assertArrayEquals(new int[] {1, 66}, message.presentFields());
            assertEquals("BODY", new String(message.body(), IpmReader.EBCDIC));
        }
    }

    @Test
    void anEmptyFileHasNoMessages() throws IOException {
        try (IpmReader reader = IpmReader.unblocked(new ByteArrayInputStream(new byte[0]))) {
            assertNull(reader.next());
        }
    }

    @Test
    void aZeroLengthRecordEndsTheFile() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(message("1240", primaryBitmapWith(2), "ONE"));
        out.writeBytes(new byte[] {0, 0, 0, 0});
        out.writeBytes(message("1644", primaryBitmapWith(3), "NEVER READ"));

        try (IpmReader reader = IpmReader.unblocked(new ByteArrayInputStream(out.toByteArray()))) {
            assertEquals("1240", reader.next().mti().digits());
            assertNull(reader.next());
        }
    }

    @Test
    void complainsWhenAMessageIsCutShort() {
        byte[] truncated = {0, 0, 0, 32, (byte) 0xF1, (byte) 0xF2};

        try (IpmReader reader = IpmReader.unblocked(new ByteArrayInputStream(truncated))) {
            assertThrows(EOFException.class, reader::next);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void complainsWhenTheMessageIsTooShortForABitmap() {
        byte[] tiny = {(byte) 0xF1, (byte) 0xF2, (byte) 0xF4, (byte) 0xF0, 0x00};
        assertThrows(IOException.class, () -> IpmReader.parse(tiny));
    }

    @Test
    void readsThroughBlockPadding() throws IOException {
        byte[] unblocked = file(
                message("1240", primaryBitmapWith(2), "A".repeat(600)),
                message("1644", primaryBitmapWith(3), "B".repeat(600)));

        try (IpmReader reader = IpmReader.blocked(new ByteArrayInputStream(block(unblocked)))) {
            assertEquals("1240", reader.next().mti().digits());
            IpmMessage second = reader.next();
            assertEquals("1644", second.mti().digits());
            assertEquals("B".repeat(600), new String(second.body(), IpmReader.EBCDIC));
        }
    }

    /** Joins messages into a file, each already carrying its record descriptor word. */
    private static byte[] file(byte[]... messages) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] message : messages) {
            out.writeBytes(message);
        }
        return out.toByteArray();
    }

    /** Builds one record: a record descriptor word, then MTI, bitmap and body. */
    private static byte[] message(String mti, byte[] bitmap, String body) {
        byte[] mtiBytes = mti.getBytes(IpmReader.EBCDIC);
        byte[] bodyBytes = body.getBytes(IpmReader.EBCDIC);
        int length = mtiBytes.length + bitmap.length + bodyBytes.length;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {
                (byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length});
        out.writeBytes(mtiBytes);
        out.writeBytes(bitmap);
        out.writeBytes(bodyBytes);
        return out.toByteArray();
    }

    private static byte[] primaryBitmapWith(int... fields) {
        byte[] bitmap = new byte[8];
        for (int field : fields) {
            int index = field - 1;
            bitmap[index / 8] |= (byte) (0x80 >>> (index % 8));
        }
        return bitmap;
    }

    /** Cuts a byte run into 1014 byte blocks, padding the last one. */
    private static byte[] block(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int offset = 0;
        while (offset < data.length) {
            int chunk = Math.min(DeblockingInputStream.DATA_SIZE, data.length - offset);
            out.write(data, offset, chunk);
            for (int i = chunk; i < DeblockingInputStream.DATA_SIZE; i++) {
                out.write(0x40);
            }
            out.write(0x40);
            out.write(0x40);
            offset += chunk;
        }
        return out.toByteArray();
    }
}
