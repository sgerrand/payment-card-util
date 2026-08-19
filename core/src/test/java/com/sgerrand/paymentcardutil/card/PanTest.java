package com.sgerrand.paymentcardutil.card;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PanTest {

    @Test
    void acceptsAWellFormedNumber() {
        Pan pan = new Pan("4111111111111111");
        assertTrue(pan.isLuhnValid());
        assertEquals(CardScheme.VISA, pan.scheme());
        assertEquals("1111", pan.lastFour());
    }

    @Test
    void parseIgnoresSpacesAndHyphens() {
        assertEquals(new Pan("5555555555554444"), Pan.parse("5555-5555 5555-4444"));
    }

    @Test
    void maskingKeepsFirstSixAndLastFour() {
        assertEquals("411111******1111", new Pan("4111111111111111").masked());
    }

    @Test
    void toStringNeverLeaksTheFullNumber() {
        String text = new Pan("4111111111111111").toString();
        assertFalse(text.contains("4111111111111111"));
        assertEquals("411111******1111", text);
    }

    @Test
    void binIsTheFirstSixDigits() {
        assertEquals(new Bin("411111"), new Pan("4111111111111111").bin());
    }

    @Test
    void rejectsANumberThatIsTooShort() {
        assertThrows(IllegalArgumentException.class, () -> new Pan("41111111111"));
    }

    @Test
    void rejectsNonDigits() {
        assertThrows(IllegalArgumentException.class, () -> new Pan("4111a11111111111"));
    }
}
