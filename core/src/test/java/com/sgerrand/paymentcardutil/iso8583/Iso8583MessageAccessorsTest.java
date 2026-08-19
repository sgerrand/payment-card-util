package com.sgerrand.paymentcardutil.iso8583;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The typed accessors over a message's values.
 *
 * <p>A message is a map of string keys underneath, which is what keeps parity
 * with cardutil. These are what a caller is meant to use instead of reaching
 * into the map and guessing at the type.
 */
class Iso8583MessageAccessorsTest {

    private static final Iso8583Message MESSAGE = Iso8583Message.builder()
            .mti("1240")
            .de(2, "4444555566667777")
            .de(4, 12345L)
            .de(5, new BigDecimal("123.45"))
            .de(12, LocalDateTime.of(2020, 3, 4, 5, 6, 7))
            .de(37, "REF00000001")
            .de(55, HexFormat.of().parseHex("9f0206000000001000"))
            .put("TAG9F02", "000000001000")
            .pds(158, "0000000000")
            .build();

    @Test
    void saysWhichDataElementsAreThere() {
        assertTrue(MESSAGE.hasField(2));
        assertFalse(MESSAGE.hasField(3));
    }

    @Test
    void readsAWholeNumber() {
        assertEquals(12345L, MESSAGE.number(4).orElseThrow());
    }

    @Test
    void readsAnAmount() {
        assertEquals(new BigDecimal("123.45"), MESSAGE.amount(5).orElseThrow());
        assertEquals(BigDecimal.valueOf(12345L), MESSAGE.amount(4).orElseThrow());
    }

    @Test
    void readsADateAndTime() {
        assertEquals(LocalDateTime.of(2020, 3, 4, 5, 6, 7), MESSAGE.dateTime(12).orElseThrow());
    }

    @Test
    void refusesToReadTextAsADate() {
        assertThrows(Iso8583Exception.class, () -> MESSAGE.dateTime(2));
    }

    @Test
    void refusesToReadTextAsANumber() {
        // A card number is all digits and does read as one; a reference is not.
        assertEquals(4444555566667777L, MESSAGE.number(2).orElseThrow());
        assertThrows(Iso8583Exception.class, () -> MESSAGE.number(37));
    }

    @Test
    void readsPrivateData() {
        assertEquals("0000000000", MESSAGE.pds(158).orElseThrow());
    }

    @Test
    void readsAChipTagWhateverCaseItIsAskedFor() {
        assertEquals("000000001000", MESSAGE.iccTag("9F02").orElseThrow());
        assertEquals("000000001000", MESSAGE.iccTag("9f02").orElseThrow());
    }

    @Test
    void anElementThatIsNotThereComesBackEmpty() {
        assertTrue(MESSAGE.number(99).isEmpty());
        assertTrue(MESSAGE.dateTime(99).isEmpty());
        assertTrue(MESSAGE.pds(1).isEmpty());
        assertTrue(MESSAGE.iccTag("9F03").isEmpty());
    }
}
