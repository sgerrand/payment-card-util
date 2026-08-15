package com.sgerrand.paymentcardutil.iso8583;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The message type indicator sits in front of the bitmap, so a missing or short
 * one shifts the whole record along and nothing can read it back.
 *
 * <p>cardutil 0.7.3 writes such a record anyway, and then fails on its own
 * output with "Failed decoding MTI field". This port refuses to write it, which
 * is the sixth divergence listed in the README.
 */
class Iso8583SerializeMtiTest {

    private static Iso8583Message.Builder message() {
        return Iso8583Message.builder().de(2, "4444555566667777");
    }

    @Test
    void aMessageWithNoMtiIsRefused() {
        Iso8583Message message = message().build();
        Iso8583Exception thrown = assertThrows(Iso8583Exception.class, () -> Iso8583.serialize(message));
        assertEquals("The message has no message type indicator", thrown.getMessage());
    }

    @Test
    void anEmptyMessageIsRefused() {
        Iso8583Message message = Iso8583Message.builder().build();
        assertThrows(Iso8583Exception.class, () -> Iso8583.serialize(message));
    }

    @Test
    void anEmptyMtiIsRefused() {
        Iso8583Message message = message().put(Iso8583Message.MTI_KEY, "").build();
        assertThrows(Iso8583Exception.class, () -> Iso8583.serialize(message));
    }

    @Test
    void anMtiOfTheWrongLengthIsRefused() {
        for (String mti : new String[] {"12", "124", "12400"}) {
            Iso8583Message message = message().put(Iso8583Message.MTI_KEY, mti).build();
            assertThrows(Iso8583Exception.class, () -> Iso8583.serialize(message), mti);
        }
    }

    @Test
    void anMtiThatIsNotANumberIsRefused() {
        Iso8583Message message = message().put(Iso8583Message.MTI_KEY, "ABCD").build();
        assertThrows(Iso8583Exception.class, () -> Iso8583.serialize(message));
    }

    @Test
    void anOrdinaryMtiStillWrites() {
        byte[] written = Iso8583.serialize(message().mti("1240").build());
        assertEquals("1240", new String(written, 0, 4, StandardCharsets.ISO_8859_1));
    }

    /**
     * Anything {@link Iso8583#parse} accepts has to write back, or reading a
     * file and writing it out again would stop working. Parsing takes four
     * characters that are a number once trimmed, so serializing must too.
     */
    @Test
    void anMtiThatParseAcceptsCanBeWrittenBack() {
        byte[] wire = Iso8583.serialize(message().mti("1240").build());
        System.arraycopy(" 124".getBytes(StandardCharsets.ISO_8859_1), 0, wire, 0, 4);

        Iso8583Message parsed = Iso8583.parse(wire);
        assertEquals(" 124", parsed.mtiText().orElseThrow());
        assertArrayEquals(wire, Iso8583.serialize(parsed));
    }
}
