package com.sgerrand.paymentcardutil.ipm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sgerrand.paymentcardutil.iso8583.Iso8583;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Message;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Writing records whose bytes are already in ISO 8583 form.
 *
 * <p>This is how a file is copied across without reading its messages, which matters because a
 * message this layout does not fully understand comes out changed if it is taken apart and rebuilt.
 */
class IpmWriterRawTest {

    private static final Iso8583Message MESSAGE =
            Iso8583Message.builder()
                    .mti("1240")
                    .de(2, "4444555566667777")
                    .de(37, "REF00000001")
                    .build();

    @Test
    void aRawRecordIsWrittenAsItStands() throws IOException {
        byte[] record = Iso8583.serialize(MESSAGE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (IpmWriter writer = IpmWriter.of(out)) {
            writer.writeRaw(record);
        }

        try (VbsReader reader = VbsReader.of(new ByteArrayInputStream(out.toByteArray()))) {
            assertArrayEquals(record, reader.next());
        }
    }

    @Test
    void rawAndParsedRecordsGiveTheSameFile() throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        ByteArrayOutputStream parsed = new ByteArrayOutputStream();

        try (IpmWriter writer = IpmWriter.blocked(raw)) {
            writer.writeRaw(Iso8583.serialize(MESSAGE));
        }
        try (IpmWriter writer = IpmWriter.blocked(parsed)) {
            writer.write(MESSAGE);
        }

        assertArrayEquals(parsed.toByteArray(), raw.toByteArray());
    }

    @Test
    void aBlockedWriterSaysSo() throws IOException {
        try (IpmWriter blocked = IpmWriter.blocked(new ByteArrayOutputStream());
                IpmWriter plain = IpmWriter.of(new ByteArrayOutputStream())) {
            assertTrue(blocked.isBlocked());
            assertFalse(plain.isBlocked());
        }
    }
}
