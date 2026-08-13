package com.sgerrand.paymentcardutil.ipm.de;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrivateDataSubelementTest {

    @Test
    void packsTagLengthAndData() {
        assertEquals("01580011", new PrivateDataSubelement("0158", "1").pack());
    }

    @Test
    void lengthIsAlwaysThreeDigits() {
        assertEquals("0158012ABCDEFGHIJKL", new PrivateDataSubelement("0158", "ABCDEFGHIJKL").pack());
    }

    @Test
    void rejectsATagOfTheWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> new PrivateDataSubelement("158", "1"));
    }

    @Test
    void rejectsDataThatIsTooLong() {
        assertThrows(IllegalArgumentException.class,
                () -> new PrivateDataSubelement("0158", "x".repeat(1000)));
    }
}
