package com.sgerrand.paymentcardutil.config;

import java.util.Objects;

/**
 * How one ISO 8583 data element is laid out and read.
 *
 * @param name what the element is called, for humans
 * @param type how the element's length is worked out
 * @param length the fixed length, or the maximum length of a variable field
 * @param valueType the Java type the text is turned into
 * @param dateFormat the date pattern, in Python {@code strftime} form, used when {@code valueType}
 *     is {@link ValueType#DATETIME}; {@code null} falls back to {@code %y%m%d}
 * @param processor extra handling for the field
 * @param processorConfig settings for that handling; meaning depends on the processor
 */
public record FieldConfig(
        String name,
        FieldType type,
        int length,
        ValueType valueType,
        String dateFormat,
        String processor,
        String processorConfig) {

    /** The date pattern used when a datetime field does not name one. */
    public static final String DEFAULT_DATE_FORMAT = "%y%m%d";

    public FieldConfig {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(valueType, "valueType");
        if (length < 0) {
            throw new IllegalArgumentException("Field length must not be negative: " + length);
        }
    }

    /** A plain text field with no extra handling. */
    public static FieldConfig of(String name, FieldType type, int length) {
        return new FieldConfig(name, type, length, ValueType.TEXT, null, null, null);
    }

    /** How many bytes the field's length prefix takes up: 0 for a fixed field. */
    public int lengthSize() {
        return type.lengthSize();
    }

    /** The date pattern to use, falling back to {@link #DEFAULT_DATE_FORMAT}. */
    public String dateFormatOrDefault() {
        return dateFormat == null ? DEFAULT_DATE_FORMAT : dateFormat;
    }
}
