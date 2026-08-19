package com.sgerrand.paymentcardutil.iso8583;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The four digits that start a message, and what each of them means.
 */
class MtiTest {

    @Test
    void eachDigitSaysWhatItMeans() {
        // 1240: a 1993 financial presentment advice from an acquirer.
        Mti mti = new Mti("1240");

        assertEquals(1, mti.version());
        assertEquals(2, mti.messageClass());
        assertEquals(4, mti.function());
        assertEquals(0, mti.origin());
    }

    @Test
    void theDigitsComeBackAsTheyWereGiven() {
        assertEquals("1644", new Mti("1644").digits());
        assertEquals("1644", new Mti("1644").toString());
    }

    @Test
    void anythingButFourDigitsIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Mti(null));
        assertThrows(IllegalArgumentException.class, () -> new Mti("124"));
        assertThrows(IllegalArgumentException.class, () -> new Mti("12400"));
        assertThrows(IllegalArgumentException.class, () -> new Mti("12X0"));
    }
}
