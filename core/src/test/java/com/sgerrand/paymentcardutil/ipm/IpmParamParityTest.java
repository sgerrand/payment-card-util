package com.sgerrand.paymentcardutil.ipm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.vectors.Vectors;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Checks parameter table extracts against the Python cardutil package. */
class IpmParamParityTest {

    @TestFactory
    @DisplayName("parameter rows match cardutil")
    Stream<DynamicTest> matchesCardutil() {
        return Vectors.tests(
                "param",
                testCase -> {
                    byte[] file = Vectors.hex(testCase.get("file_hex").asText());
                    List<Map<String, String>> read = new ArrayList<>();

                    try (IpmParamReader reader =
                            IpmParamReader.open(
                                    new ByteArrayInputStream(file),
                                    testCase.get("table_id").asText(),
                                    IpmParamReader.DEFAULT_CHARSET,
                                    testCase.get("blocked").asBoolean(),
                                    testCase.get("expanded").asBoolean(),
                                    IsoConfig.defaults())) {
                        reader.forEach(record -> read.add(record.asMap()));
                    }

                    JsonNode expected = testCase.get("parsed");
                    assertEquals(expected.size(), read.size(), "row count");
                    for (int i = 0; i < expected.size(); i++) {
                        assertEquals(asMap(expected.get(i)), read.get(i), "row " + (i + 1));
                    }
                });
    }

    private static Map<String, String> asMap(JsonNode node) {
        Map<String, String> values = new LinkedHashMap<>();
        node.properties().forEach(entry -> values.put(entry.getKey(), entry.getValue().asText()));
        return values;
    }
}
