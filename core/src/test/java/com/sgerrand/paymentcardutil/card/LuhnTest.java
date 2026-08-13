package com.sgerrand.paymentcardutil.card;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuhnTest {

    @ParameterizedTest
    @ValueSource(strings = {"4111111111111111", "5555555555554444", "378282246310005", "6011111111111117"})
    void acceptsKnownGoodNumbers(String digits) {
        assertTrue(Luhn.isValid(digits));
    }

    @Test
    void rejectsANumberWithATypo() {
        assertFalse(Luhn.isValid("4111111111111112"));
    }

    @Test
    void worksOutTheCheckDigit() {
        assertEquals(1, Luhn.checkDigit("411111111111111"));
        assertEquals(4, Luhn.checkDigit("555555555555444"));
    }

    @Test
    void aNumberPlusItsCheckDigitIsValid() {
        String body = "601111111111111";
        assertTrue(Luhn.isValid(body + Luhn.checkDigit(body)));
    }
}
