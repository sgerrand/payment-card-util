package com.sgerrand.paymentcardutil.ipm;

import com.sgerrand.paymentcardutil.iso8583.Iso8583Message;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Options;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks what can be told about a file by looking at the start of it.
 */
class IpmInfoTest {

    private static final Iso8583Message MESSAGE = Iso8583Message.builder()
            .mti("1240")
            .de(2, "4444555566667777")
            .de(37, "REFERENCE001")
            .build();

    /** Builds a file big enough to see more than one block of. */
    private static byte[] file(Charset charset, boolean blocked) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (IpmWriter writer = IpmWriter.open(
                out, Iso8583Options.defaults().withCharset(charset), blocked)) {
            for (int i = 0; i < 60; i++) {
                writer.write(MESSAGE);
            }
        }
        return out.toByteArray();
    }

    @Test
    void readsBlockingAndCharacterSetOffAnAsciiFile() throws IOException {
        IpmInfo info = IpmInfo.inspect(file(StandardCharsets.ISO_8859_1, true));

        assertTrue(info.valid(), info.reason());
        assertTrue(info.blocked());
        assertEquals(IpmInfo.Encoding.ASCII, info.encoding());
        assertEquals(StandardCharsets.ISO_8859_1, info.charset().orElseThrow());
    }

    @Test
    void spotsAnUnblockedFile() throws IOException {
        IpmInfo info = IpmInfo.inspect(file(StandardCharsets.ISO_8859_1, false));

        assertTrue(info.valid(), info.reason());
        assertFalse(info.blocked());
    }

    @Test
    void tellsEbcdicFromAscii() throws IOException {
        IpmInfo info = IpmInfo.inspect(file(Iso8583Options.EBCDIC_CP500, true));

        assertTrue(info.valid(), info.reason());
        assertEquals(IpmInfo.Encoding.EBCDIC, info.encoding());
    }

    @Test
    void doesNotClaimToKnowWhichEbcdicCodePage() throws IOException {
        // The file is cp500, but the digits read the same in cp037, so the most
        // that can honestly be said is that it is EBCDIC.
        IpmInfo cp500 = IpmInfo.inspect(file(Iso8583Options.EBCDIC_CP500, true));
        IpmInfo cp037 = IpmInfo.inspect(file(Iso8583Options.EBCDIC_CP037, true));

        assertEquals(cp500.encoding(), cp037.encoding());
        assertEquals(IpmInfo.Encoding.EBCDIC, cp500.encoding());
    }

    @Test
    void theSuggestedCharsetReadsTheMessageTypeBack() throws IOException {
        byte[] data = file(Iso8583Options.EBCDIC_CP500, false);
        Charset suggested = IpmInfo.inspect(data).charset().orElseThrow();

        // Digits survive the guess even if the code page is not exactly right.
        assertEquals("1240", new String(data, 4, 4, suggested));
    }

    @Test
    void aShortFileIsNotValid() {
        IpmInfo info = IpmInfo.inspect(new byte[] {1, 2, 3});

        assertFalse(info.valid());
        assertEquals(IpmInfo.Encoding.UNKNOWN, info.encoding());
        assertTrue(info.charset().isEmpty());
        assertTrue(info.reason().contains("too short"), info.reason());
    }

    @Test
    void anAbsurdRecordLengthIsNotValid() {
        byte[] data = new byte[40];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xFF;

        IpmInfo info = IpmInfo.inspect(data);
        assertFalse(info.valid());
        assertTrue(info.reason().contains("past the"), info.reason());
    }

    @Test
    void aBitmapClaimingUnknownFieldsIsNotValid() {
        // A well formed length and message type, then a bitmap with every bit on.
        byte[] data = new byte[40];
        data[3] = 32;
        System.arraycopy("1240".getBytes(StandardCharsets.ISO_8859_1), 0, data, 4, 4);
        java.util.Arrays.fill(data, 8, 24, (byte) 0xFF);

        IpmInfo info = IpmInfo.inspect(data);
        assertFalse(info.valid());
        assertTrue(info.reason().contains("does not use"), info.reason());
    }
}
