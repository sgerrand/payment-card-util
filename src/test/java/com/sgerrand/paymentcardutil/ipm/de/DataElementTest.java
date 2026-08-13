package com.sgerrand.paymentcardutil.ipm.de;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataElementTest {

    @Test
    void keepsTheRawBytes() {
        DataElement de = new DataElement(4, "000000012345".getBytes(StandardCharsets.US_ASCII));
        assertEquals(12, de.length());
        assertEquals("000000012345", de.asText(StandardCharsets.US_ASCII));
    }

    @Test
    void copiesTheBytesOnTheWayInAndOut() {
        byte[] source = {1, 2, 3};
        DataElement de = new DataElement(4, source);

        source[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, de.value());

        de.value()[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, de.value());
    }

    @Test
    void equalityComparesTheBytes() {
        assertEquals(new DataElement(4, new byte[] {1, 2}), new DataElement(4, new byte[] {1, 2}));
        assertEquals(
                new DataElement(4, new byte[] {1, 2}).hashCode(),
                new DataElement(4, new byte[] {1, 2}).hashCode());
        assertNotEquals(new DataElement(4, new byte[] {1, 2}), new DataElement(5, new byte[] {1, 2}));
        assertNotEquals(new DataElement(4, new byte[] {1, 2}), new DataElement(4, new byte[] {1, 3}));
    }

    @Test
    void rejectsAnElementNumberOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new DataElement(0, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new DataElement(129, new byte[0]));
    }

    @Test
    void toStringHidesTheValue() {
        assertEquals("DE4[2 bytes]", new DataElement(4, new byte[] {1, 2}).toString());
    }
}
