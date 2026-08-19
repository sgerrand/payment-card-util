package com.sgerrand.paymentcardutil.ipm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Message;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Options;
import com.sgerrand.paymentcardutil.vectors.Vectors;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Checks IPM and VBS files against files produced by the Python cardutil package. */
class IpmParityTest {

    @TestFactory
    @DisplayName("writing an IPM file matches cardutil byte for byte")
    Stream<DynamicTest> writeMatchesCardutil() {
        return Vectors.tests(
                "ipm",
                testCase -> {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    try (IpmWriter writer =
                            IpmWriter.open(out, options(testCase), blocked(testCase))) {
                        for (JsonNode message : testCase.get("input")) {
                            writer.write(Iso8583Message.of(Vectors.values(message)));
                        }
                    }
                    assertEquals(testCase.get("file_hex").asText(), Vectors.hex(out.toByteArray()));
                });
    }

    @TestFactory
    @DisplayName("reading an IPM file gives the same messages as cardutil")
    Stream<DynamicTest> readMatchesCardutil() {
        return Vectors.tests(
                "ipm",
                testCase -> {
                    byte[] file = Vectors.hex(testCase.get("file_hex").asText());
                    List<Iso8583Message> read = new ArrayList<>();
                    try (IpmReader reader =
                            IpmReader.open(
                                    new ByteArrayInputStream(file),
                                    options(testCase),
                                    blocked(testCase))) {
                        reader.forEach(read::add);
                    }

                    JsonNode expected = testCase.get("parsed");
                    assertEquals(expected.size(), read.size(), "message count");
                    for (int i = 0; i < expected.size(); i++) {
                        Vectors.assertValuesEqual(
                                Vectors.values(expected.get(i)), read.get(i).values());
                    }
                });
    }

    @TestFactory
    @DisplayName("writing a VBS file matches cardutil byte for byte")
    Stream<DynamicTest> writeVbsMatchesCardutil() {
        return Vectors.tests(
                "vbs",
                testCase -> {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    boolean blocked = testCase.get("blocked").asBoolean();
                    try (VbsWriter writer = blocked ? VbsWriter.blocked(out) : VbsWriter.of(out)) {
                        for (JsonNode record : testCase.get("input")) {
                            writer.write(Vectors.hex(record.asText()));
                        }
                    }
                    assertEquals(testCase.get("file_hex").asText(), Vectors.hex(out.toByteArray()));
                });
    }

    @TestFactory
    @DisplayName("reading a VBS file gives the same records as cardutil")
    Stream<DynamicTest> readVbsMatchesCardutil() {
        return Vectors.tests(
                "vbs",
                testCase -> {
                    byte[] file = Vectors.hex(testCase.get("file_hex").asText());
                    boolean blocked = testCase.get("blocked").asBoolean();
                    List<String> read = new ArrayList<>();
                    try (VbsReader reader =
                            blocked
                                    ? VbsReader.blocked(new ByteArrayInputStream(file))
                                    : VbsReader.of(new ByteArrayInputStream(file))) {
                        reader.forEach(record -> read.add(Vectors.hex(record)));
                    }

                    List<String> expected = new ArrayList<>();
                    testCase.get("input").forEach(record -> expected.add(record.asText()));
                    assertEquals(expected, read);
                });
    }

    private static Iso8583Options options(JsonNode testCase) {
        return Iso8583Options.defaults()
                .withCharset(Vectors.charset(testCase.get("encoding").asText()));
    }

    private static boolean blocked(JsonNode testCase) {
        return testCase.get("blocked").asBoolean();
    }
}
