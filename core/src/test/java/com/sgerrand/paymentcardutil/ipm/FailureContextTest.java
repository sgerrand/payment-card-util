package com.sgerrand.paymentcardutil.ipm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sgerrand.paymentcardutil.PaymentCardException;
import com.sgerrand.paymentcardutil.iso8583.Iso8583;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Message;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Options;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * What a failure carries with it.
 *
 * <p>A data problem is reported to somebody holding a file they cannot read, so the exception has
 * to say which record went wrong and hand back the bytes, not just name the trouble.
 */
class FailureContextTest {

    private static final Iso8583Message GOOD =
            Iso8583Message.builder().mti("1240").de(2, "4444555566667777").build();

    /** A record whose DE2 length prefix says more bytes than the record holds. */
    private static byte[] brokenRecord() {
        byte[] record = Iso8583.serialize(GOOD);
        // The two length digits sit right after the message type and bitmap.
        record[20] = '9';
        record[21] = '9';
        return record;
    }

    private static byte[] fileOf(byte[]... records) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (VbsWriter writer = VbsWriter.of(out)) {
            for (byte[] record : records) {
                writer.write(record);
            }
        }
        return out.toByteArray();
    }

    @Test
    void readingSaysWhichRecordAndHandsBackTheBytes() throws IOException {
        byte[] file = fileOf(Iso8583.serialize(GOOD), brokenRecord());

        try (IpmReader reader =
                IpmReader.of(new ByteArrayInputStream(file), Iso8583Options.defaults())) {
            reader.next();
            PaymentCardException problem = assertThrows(PaymentCardException.class, reader::next);

            assertEquals(2, problem.recordNumber().orElseThrow(), "which record");
            assertTrue(problem.binaryContext().isPresent(), "the bytes that would not read");
            assertEquals(
                    problem.getCause().getMessage(),
                    problem.getMessage(),
                    "the reader keeps the parser's own words rather than replacing them");
            assertTrue(
                    problem.getMessage().contains("wrong length"),
                    "and those words say what is actually wrong: " + problem.getMessage());
        }
    }

    @Test
    void parsingOnItsOwnStillHandsBackTheBytes() {
        PaymentCardException problem =
                assertThrows(
                        PaymentCardException.class,
                        () -> Iso8583.parse(brokenRecord(), Iso8583Options.defaults()));

        assertTrue(problem.binaryContext().isPresent(), "the bytes that would not read");
        assertTrue(problem.recordNumber().isEmpty(), "a lone message has no record number");
    }

    @Test
    void aFailureFromInsideAFieldStillCarriesTheRecord() {
        // DE12 is a fixed twelve character date, and a codec pulling a field
        // apart sees only that field, so what it throws has no bytes of its own.
        Iso8583Message dated =
                Iso8583Message.builder()
                        .mti("1240")
                        .de(12, java.time.LocalDateTime.of(2020, 3, 4, 5, 6, 7))
                        .build();
        byte[] record = Iso8583.serialize(dated);
        System.arraycopy(
                "notadate1234".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1),
                0,
                record,
                20,
                12);

        PaymentCardException problem =
                assertThrows(
                        PaymentCardException.class,
                        () -> Iso8583.parse(record, Iso8583Options.defaults()));

        assertTrue(problem.getMessage().contains("DE12"), problem.getMessage());
        assertTrue(problem.binaryContext().isPresent(), "the record the field came from");
    }

    @Test
    void writingSaysWhichMessageInTheRunWasWrong() throws IOException {
        Iso8583Message noMti = Iso8583Message.builder().de(2, "4444555566667777").build();

        try (IpmWriter writer = IpmWriter.of(new ByteArrayOutputStream())) {
            writer.write(GOOD);
            PaymentCardException problem =
                    assertThrows(PaymentCardException.class, () -> writer.write(noMti));

            assertEquals(2, problem.recordNumber().orElseThrow(), "which message");
            assertTrue(
                    problem.getMessage().contains("message type indicator"), problem.getMessage());
        }
    }
}
