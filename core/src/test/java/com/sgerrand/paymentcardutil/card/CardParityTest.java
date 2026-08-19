package com.sgerrand.paymentcardutil.card;

import com.sgerrand.paymentcardutil.vectors.Vectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks check digits and masking against the Python cardutil package.
 */
class CardParityTest {

    @TestFactory
    @DisplayName("check digits and masking match cardutil")
    Stream<DynamicTest> matchesCardutil() {
        return Vectors.tests("card", testCase -> testCase.get("card_number").asText(), testCase -> {
            String cardNumber = testCase.get("card_number").asText();
            String withoutCheckDigit = cardNumber.substring(0, cardNumber.length() - 1);

            assertEquals(Integer.parseInt(testCase.get("check_digit").asText()),
                    Luhn.checkDigit(withoutCheckDigit), "check digit");
            assertEquals(testCase.get("masked").asText(),
                    Pan.maskDigits(cardNumber), "masked number");
            assertTrue(Luhn.isValid(withoutCheckDigit + Luhn.checkDigit(withoutCheckDigit)),
                    "a number carrying its own check digit passes");
        });
    }
}
