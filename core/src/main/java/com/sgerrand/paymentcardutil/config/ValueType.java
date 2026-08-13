package com.sgerrand.paymentcardutil.config;

/**
 * The Java type a field's text is turned into when a message is read.
 */
public enum ValueType {

    /** Left as a {@link String}. The default when a field says nothing. */
    TEXT,

    /** Read as a {@link Long}. Covers the {@code int} and {@code long} types in cardutil. */
    LONG,

    /** Read as a {@link java.math.BigDecimal}. */
    DECIMAL,

    /** Read as a {@link java.time.LocalDateTime} using the field's date format. */
    DATETIME;

    /**
     * Maps a cardutil {@code field_python_type} name onto a value type.
     *
     * @param pythonType one of {@code int}, {@code long}, {@code decimal} or
     *                   {@code datetime}; {@code null} means text
     * @throws IllegalArgumentException if the name is not one of those
     */
    public static ValueType fromPythonType(String pythonType) {
        if (pythonType == null) {
            return TEXT;
        }
        return switch (pythonType) {
            case "int", "long" -> LONG;
            case "decimal" -> DECIMAL;
            case "datetime" -> DATETIME;
            default -> throw new IllegalArgumentException("Unknown field python type: " + pythonType);
        };
    }
}
