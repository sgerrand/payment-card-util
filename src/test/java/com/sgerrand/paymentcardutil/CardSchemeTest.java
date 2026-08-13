package com.sgerrand.paymentcardutil;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardSchemeTest {

    @ParameterizedTest
    @CsvSource({
            "4111111111111111, VISA",
            "5555555555554444, MASTERCARD",
            "2221001234567890, MASTERCARD",
            "378282246310005,  AMERICAN_EXPRESS",
            "6011111111111117, DISCOVER",
            "3530111333300000, JCB",
            "36700102000000,   DINERS_CLUB",
            "6212345678901232, UNION_PAY",
            "9999999999999999, UNKNOWN",
    })
    void picksTheSchemeFromTheLeadingDigits(String digits, CardScheme expected) {
        assertEquals(expected, CardScheme.fromDigits(digits));
    }
}
