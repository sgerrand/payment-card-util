package com.sgerrand.paymentcardutil.vectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.function.ThrowingConsumer;

/**
 * Loads the golden vectors generated from the Python cardutil package.
 *
 * <p>The file is produced by {@code tools/gen_vectors.py}. Every value in it carries a type tag, so
 * a message read here can be compared against what cardutil produced without guessing what {@code
 * "12345"} was meant to be.
 */
public final class Vectors {

    private static final String RESOURCE = "/vectors/cardutil.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonNode ROOT = load();

    private Vectors() {}

    /** The cardutil version the vectors came from. */
    public static String cardutilVersion() {
        return ROOT.get("cardutil_version").asText();
    }

    /** Every case under a top level key such as {@code iso8583} or {@code ipm}. */
    public static List<JsonNode> cases(String group) {
        List<JsonNode> cases = new ArrayList<>();
        ROOT.get(group).forEach(cases::add);
        if (cases.isEmpty()) {
            throw new IllegalStateException("No vectors under " + group);
        }
        return cases;
    }

    /**
     * One dynamic test per case in a group, named by the case's {@code name}.
     *
     * <p>Every parity test is the same shape — walk the cases, name each one, run an assertion
     * against it — so the shape lives here and each test class is left holding only its assertion.
     */
    public static Stream<DynamicTest> tests(String group, ThrowingConsumer<JsonNode> body) {
        return tests(group, testCase -> testCase.get("name").asText(), body);
    }

    /**
     * One dynamic test per case in a group.
     *
     * @param naming what to call each case, for groups whose cases carry no {@code name}
     */
    public static Stream<DynamicTest> tests(
            String group, Function<JsonNode, String> naming, ThrowingConsumer<JsonNode> body) {
        return cases(group).stream()
                .map(
                        testCase ->
                                DynamicTest.dynamicTest(
                                        naming.apply(testCase), () -> body.accept(testCase)));
    }

    /** Rebuilds a tagged map of message values with their Java types. */
    public static Map<String, Object> values(JsonNode node) {
        Map<String, Object> values = new LinkedHashMap<>();
        node.properties().forEach(entry -> values.put(entry.getKey(), value(entry.getValue())));
        return values;
    }

    /** Rebuilds one tagged value. */
    public static Object value(JsonNode node) {
        String type = node.get("t").asText();
        String text = node.get("v").asText();
        return switch (type) {
            case "str" -> text;
            case "int" -> Long.valueOf(text);
            case "decimal" -> new BigDecimal(text);
            case "datetime" -> LocalDateTime.parse(text);
            case "bytes" -> HexFormat.of().parseHex(text);
            default -> throw new IllegalStateException("Unknown vector value type: " + type);
        };
    }

    /** Maps a Python codec name onto the matching Java charset. */
    public static Charset charset(String pythonEncoding) {
        return switch (pythonEncoding) {
            case "latin_1", "latin-1", "iso8859-1" -> StandardCharsets.ISO_8859_1;
            case "cp500" -> Charset.forName("IBM500");
            case "cp037" -> Charset.forName("IBM037");
            case "ascii" -> StandardCharsets.US_ASCII;
            default -> Charset.forName(pythonEncoding);
        };
    }

    /**
     * Compares message values one key at a time, so a failure names the key that differs rather
     * than dumping two whole maps.
     */
    public static void assertValuesEqual(Map<String, Object> expected, Map<String, Object> actual) {
        org.junit.jupiter.api.Assertions.assertEquals(
                expected.keySet(), actual.keySet(), "keys present");
        expected.forEach(
                (key, expectedValue) -> {
                    Object actualValue = actual.get(key);
                    if (expectedValue instanceof byte[] expectedBytes) {
                        org.junit.jupiter.api.Assertions.assertInstanceOf(
                                byte[].class, actualValue, key);
                        org.junit.jupiter.api.Assertions.assertEquals(
                                hex(expectedBytes), hex((byte[]) actualValue), key);
                        return;
                    }
                    org.junit.jupiter.api.Assertions.assertEquals(
                            expectedValue,
                            actualValue,
                            () ->
                                    key
                                            + " (expected "
                                            + expectedValue.getClass().getSimpleName()
                                            + ", got "
                                            + (actualValue == null
                                                    ? "nothing"
                                                    : actualValue.getClass().getSimpleName())
                                            + ")");
                });
    }

    public static byte[] hex(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    public static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private static JsonNode load() {
        try (InputStream in = Vectors.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Missing " + RESOURCE + ". Regenerate it with tools/gen_vectors.py.");
            }
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
