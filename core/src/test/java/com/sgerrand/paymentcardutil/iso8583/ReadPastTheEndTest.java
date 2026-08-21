package com.sgerrand.paymentcardutil.iso8583;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * What happens when a field's length carries the reader past the end of the record.
 *
 * <p>A variable field says how long it is, so a damaged one can claim more bytes than the record
 * holds. The reader takes what is there and carries on: every element named by the bitmap is walked
 * before anything is said, and the whole-message length check is what reports the problem, naming
 * the number of bytes rather than whichever field happened to be read first.
 *
 * <p>That means a fixed field can start beyond the end of the record. Reading it must not throw
 * from inside the array handling, or the failure stops being the readable one above.
 */
class ReadPastTheEndTest {

    /**
     * A message whose DE2 claims 99 bytes it does not have, followed by DE3, a fixed six character
     * field that therefore starts well past the end.
     */
    private static byte[] overrunningRecord() {
        byte[] bitmap = new byte[16];
        bitmap[0] = (byte) 0x60; // DE2 and DE3
        byte[] head = "1240".getBytes(StandardCharsets.ISO_8859_1);
        byte[] body = "99444455".getBytes(StandardCharsets.ISO_8859_1);

        byte[] record = new byte[head.length + bitmap.length + body.length];
        System.arraycopy(head, 0, record, 0, head.length);
        System.arraycopy(bitmap, 0, record, head.length, bitmap.length);
        System.arraycopy(body, 0, record, head.length + bitmap.length, body.length);
        return record;
    }

    @Test
    void aFieldStartingPastTheEndIsReportedAsALengthProblem() {
        Iso8583Exception problem =
                assertThrows(
                        Iso8583Exception.class,
                        () -> Iso8583.parse(overrunningRecord(), Iso8583Options.defaults()));

        assertTrue(
                problem.getMessage().contains("Message data is the wrong length"),
                "the length check reports it, not the array handling: " + problem.getMessage());
        assertTrue(problem.binaryContext().isPresent(), "carrying the record that would not read");
    }

    @Test
    void theRecordItselfIsWhatComesBack() {
        byte[] record = overrunningRecord();

        Iso8583Exception problem =
                assertThrows(
                        Iso8583Exception.class,
                        () -> Iso8583.parse(record, Iso8583Options.defaults()));

        assertTrue(
                problem.binaryContext().orElseThrow().length > 0,
                "something to dump for whoever sent the file");
    }
}
