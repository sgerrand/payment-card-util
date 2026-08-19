package com.sgerrand.paymentcardutil.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.sgerrand.paymentcardutil.vectors.Vectors;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Checks key combining, check values and key encryption against the Python cardutil package. */
class KeysParityTest {

    @TestFactory
    @DisplayName("key handling matches cardutil")
    Stream<DynamicTest> matchesCardutil() {
        return Vectors.tests(
                "keys",
                testCase -> components(testCase).size() + " components",
                testCase -> {
                    String[] components = components(testCase).toArray(String[]::new);
                    String masterKey = testCase.get("master_key").asText();

                    String clear = Keys.zoneMasterKey(components);
                    assertEquals(testCase.get("clear_key").asText(), clear, "combined key");
                    assertEquals(
                            testCase.get("kcv").asText(), Keys.keyCheckValue(clear), "check value");

                    Keys.EncryptedKey encrypted =
                            Keys.encryptedZoneMasterKey(masterKey, components);
                    assertEquals(
                            testCase.get("encrypted_key").asText(),
                            encrypted.encryptedKey(),
                            "encrypted key");
                    assertEquals(
                            testCase.get("encrypted_kcv").asText(),
                            encrypted.keyCheckValue(),
                            "check value of the encrypted key");

                    assertEquals(
                            clear,
                            Keys.decryptKey(encrypted.encryptedKey(), masterKey),
                            "decrypting gives the key back");
                });
    }

    private static List<String> components(JsonNode testCase) {
        List<String> components = new ArrayList<>();
        testCase.get("components").forEach(component -> components.add(component.asText()));
        return components;
    }
}
