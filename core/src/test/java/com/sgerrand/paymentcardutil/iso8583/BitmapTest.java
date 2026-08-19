package com.sgerrand.paymentcardutil.iso8583;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BitmapTest {

    @Test
    void anEmptyBitmapHasNoFields() {
        Bitmap bitmap = Bitmap.empty();
        assertEquals(8, bitmap.lengthInBytes());
        assertEquals(0, bitmap.fields().length);
        assertFalse(bitmap.isSet(2));
    }

    @Test
    void setThenIsSet() {
        Bitmap bitmap = Bitmap.empty();
        bitmap.set(2);
        bitmap.set(3);
        bitmap.set(64);
        assertArrayEquals(new int[] {2, 3, 64}, bitmap.fields());
    }

    @Test
    void settingAHighFieldGrowsTheBitmap() {
        Bitmap bitmap = Bitmap.empty();
        bitmap.set(100);
        assertEquals(16, bitmap.lengthInBytes());
        assertTrue(bitmap.isSet(1), "bit 1 marks that a secondary bitmap follows");
        assertTrue(bitmap.isSet(100));
    }

    @Test
    void wrapsAPrimaryOnlyBitmap() {
        Bitmap bitmap = Bitmap.of(new byte[] {0x60, 0, 0, 0, 0, 0, 0, 0});
        assertEquals(8, bitmap.lengthInBytes());
        assertArrayEquals(new int[] {2, 3}, bitmap.fields());
    }

    @Test
    void wrapsABitmapCarryingBothHalves() {
        byte[] source = new byte[16];
        source[0] = (byte) 0x80;
        source[8] = 0x40;

        Bitmap bitmap = Bitmap.of(source);
        assertEquals(16, bitmap.lengthInBytes());
        assertArrayEquals(new int[] {1, 66}, bitmap.fields());
    }

    @Test
    void theLengthComesFromTheBytesNotFromBitOne() {
        // Bit 1 set but only the primary half handed over: an IPM record does
        // the opposite, carrying both halves with bit 1 clear, so sizing the
        // bitmap by that bit would misread either one.
        byte[] bitOneSet = new byte[8];
        bitOneSet[0] = (byte) 0x80;

        assertEquals(8, Bitmap.of(bitOneSet).lengthInBytes());
    }

    @Test
    void rejectsBytesThatAreNotAWholeBitmap() {
        assertThrows(IllegalArgumentException.class, () -> Bitmap.of(new byte[4]));
        assertThrows(IllegalArgumentException.class, () -> Bitmap.of(new byte[12]));
    }

    @Test
    void rejectsFieldNumbersOutOfRange() {
        Bitmap bitmap = Bitmap.empty();
        assertThrows(IllegalArgumentException.class, () -> bitmap.set(0));
        assertThrows(IllegalArgumentException.class, () -> bitmap.set(129));
    }

    @Test
    void toStringIsHex() {
        Bitmap bitmap = Bitmap.empty();
        bitmap.set(2);
        assertEquals("4000000000000000", bitmap.toString());
    }
}
