package com.sgerrand.paymentcardutil.config;

import java.util.Objects;
import java.util.Optional;

/**
 * One fixed position part of a Mastercard private data subelement, such as the file type at the
 * start of PDS 0105.
 *
 * <p>When a message is read, each configured part is copied out of its subelement and kept
 * alongside it under a key like {@code PDS0105_SF1}. See {@link IsoConfig#pdsSubfields()}.
 *
 * @param name what the part is called, for humans
 * @param start where the part starts in the subelement's data, counting from 0
 * @param length how many characters the part takes up
 * @param valueType the Java type the text is turned into: {@link ValueType#TEXT}, {@link
 *     ValueType#LONG} or {@link ValueType#DECIMAL}
 */
public record PdsSubfield(String name, int start, int length, ValueType valueType) {

    /** The text between a subelement's key and its subfield number, as in {@code PDS0105_SF1}. */
    public static final String KEY_MARKER = "_SF";

    public PdsSubfield {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(valueType, "valueType");
        if (start < 0) {
            throw new IllegalArgumentException("Subfield start must not be negative: " + start);
        }
        if (length < 0) {
            throw new IllegalArgumentException("Subfield length must not be negative: " + length);
        }
        if (valueType == ValueType.DATETIME) {
            throw new IllegalArgumentException("A PDS subfield cannot hold a date");
        }
    }

    /** A text part. */
    public static PdsSubfield of(String name, int start, int length) {
        return new PdsSubfield(name, start, length, ValueType.TEXT);
    }

    /**
     * Maps a cardutil {@code field_python_type} name onto the type a subfield is read as.
     *
     * <p>This is not {@link ValueType#fromPythonType}. cardutil converts a subfield only when it
     * says {@code int} or {@code decimal}, and leaves anything else as text, {@code long} and
     * {@code datetime} included. That is matched here on purpose.
     *
     * @param pythonType the name from the config; {@code null} means text
     */
    public static ValueType valueTypeFor(String pythonType) {
        if (pythonType == null) {
            return ValueType.TEXT;
        }
        return switch (pythonType) {
            case "int" -> ValueType.LONG;
            case "decimal" -> ValueType.DECIMAL;
            default -> ValueType.TEXT;
        };
    }

    /**
     * The message key a part is kept under, such as {@code PDS0105_SF1}.
     *
     * <p>The one place that builds these keys, so the reader that makes them and anything that
     * looks for them, such as the CSV masking, cannot drift apart.
     *
     * @param tag the four character PDS tag, such as {@code 0105}
     * @param number the subfield number, such as {@code 1}
     */
    public static String key(String tag, String number) {
        return "PDS"
                + Objects.requireNonNull(tag, "tag")
                + KEY_MARKER
                + Objects.requireNonNull(number, "number");
    }

    /**
     * The subelement a part's key belongs to: {@code PDS0105} for {@code PDS0105_SF1}.
     *
     * @return the subelement's key, or empty if this is not a part's key. A part's key is {@code
     *     PDS}, one or more digits, {@value #KEY_MARKER}, then the subfield number.
     */
    public static Optional<String> parentKey(String key) {
        if (!key.startsWith("PDS")) {
            return Optional.empty();
        }
        int marker = key.indexOf(KEY_MARKER, 3);
        if (marker <= 3) {
            return Optional.empty();
        }
        for (int i = 3; i < marker; i++) {
            char c = key.charAt(i);
            if (c < '0' || c > '9') {
                return Optional.empty();
            }
        }
        return Optional.of(key.substring(0, marker));
    }

    /** The character just past the end of this part. */
    public int end() {
        return start + length;
    }
}
