package com.sgerrand.paymentcardutil.pin;

import com.sgerrand.paymentcardutil.vectors.Vectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks pin blocks and pin verification values against the Python cardutil
 * package.
 */
class PinBlockParityTest {

    @TestFactory
    @DisplayName("format 0 blocks match cardutil")
    Stream<DynamicTest> iso0MatchesCardutil() {
        return Vectors.cases("iso0_pinblock").stream().map(testCase -> DynamicTest.dynamicTest(
                testCase.get("pin").asText() + " on " + testCase.get("card_number").asText(),
                () -> {
                    String pin = testCase.get("pin").asText();
                    String cardNumber = testCase.get("card_number").asText();
                    Iso0PinBlock block = new Iso0PinBlock(pin, cardNumber);

                    assertEquals(testCase.get("block_hex").asText(), Vectors.hex(block.toBytes()), "block");
                    assertEquals(testCase.get("encrypted_hex").asText(),
                            Vectors.hex(block.toEncryptedBytes(testCase.get("key").asText())), "encrypted block");

                    if (!testCase.get("pvv").isNull()) {
                        assertEquals(testCase.get("pvv").asText(),
                                block.toPvv(testCase.get("pvv_key").asText(),
                                        testCase.get("pvv_key_index").asInt()), "pvv");
                    }
                }));
    }

    @TestFactory
    @DisplayName("format 0 blocks read back to the same pin")
    Stream<DynamicTest> iso0RoundTrips() {
        return Vectors.cases("iso0_pinblock").stream().map(testCase -> DynamicTest.dynamicTest(
                testCase.get("pin").asText(),
                () -> {
                    String pin = testCase.get("pin").asText();
                    String cardNumber = testCase.get("card_number").asText();
                    String key = testCase.get("key").asText();

                    assertEquals(pin, Iso0PinBlock.fromBytes(
                            Vectors.hex(testCase.get("block_hex").asText()), cardNumber).pin());
                    assertEquals(pin, Iso0PinBlock.fromEncryptedBytes(
                            Vectors.hex(testCase.get("encrypted_hex").asText()), cardNumber, key).pin());
                }));
    }

    @TestFactory
    @DisplayName("format 4 blocks match cardutil")
    Stream<DynamicTest> iso4MatchesCardutil() {
        return Vectors.cases("iso4_pinblock").stream().map(testCase -> DynamicTest.dynamicTest(
                testCase.get("pin").asText(),
                () -> {
                    Iso4PinBlock block = new Iso4PinBlock(
                            testCase.get("pin").asText(),
                            Vectors.hex(testCase.get("random_hex").asText()));

                    assertEquals(testCase.get("block_hex").asText(), Vectors.hex(block.toBytes()), "block");
                    assertEquals(testCase.get("encrypted_hex").asText(),
                            Vectors.hex(block.toEncryptedBytes(testCase.get("key").asText())), "encrypted block");
                }));
    }

    @TestFactory
    @DisplayName("format 4 blocks read back to the same pin")
    Stream<DynamicTest> iso4RoundTrips() {
        return Vectors.cases("iso4_pinblock").stream().map(testCase -> DynamicTest.dynamicTest(
                testCase.get("pin").asText(),
                () -> {
                    String pin = testCase.get("pin").asText();
                    assertEquals(pin, Iso4PinBlock.fromBytes(
                            Vectors.hex(testCase.get("block_hex").asText())).pin());
                    assertEquals(pin, Iso4PinBlock.fromEncryptedBytes(
                            Vectors.hex(testCase.get("encrypted_hex").asText()),
                            testCase.get("key").asText()).pin());
                }));
    }
}
