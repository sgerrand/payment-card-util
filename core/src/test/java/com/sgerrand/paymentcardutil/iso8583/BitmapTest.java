package com.sgerrand.paymentcardutil.iso8583;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void readsAPrimaryOnlyBitmap() {
        byte[] source = {0x60, 0, 0, 0, 0, 0, 0, 0};
        Bitmap bitmap = Bitmap.read(source, 0);
        assertEquals(8, bitmap.lengthInBytes());
        assertArrayEquals(new int[] {2, 3}, bitmap.fields());
    }

    @Test
    void readsASecondaryBitmapWhenBitOneIsSet() {
        byte[] source = new byte[16];
        source[0] = (byte) 0x80;
        source[8] = 0x40;
        Bitmap bitmap = Bitmap.read(source, 0);
        assertEquals(16, bitmap.lengthInBytes());
        assertArrayEquals(new int[] {1, 66}, bitmap.fields());
    }

    @Test
    void readsFromAnOffset() {
        byte[] source = new byte[10];
        source[2] = 0x40;
        assertArrayEquals(new int[] {2}, Bitmap.read(source, 2).fields());
    }

    @Test
    void rejectsASourceThatIsTooShort() {
        assertThrows(IllegalArgumentException.class, () -> Bitmap.read(new byte[4], 0));
        byte[] truncatedSecondary = new byte[8];
        truncatedSecondary[0] = (byte) 0x80;
        assertThrows(IllegalArgumentException.class, () -> Bitmap.read(truncatedSecondary, 0));
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
