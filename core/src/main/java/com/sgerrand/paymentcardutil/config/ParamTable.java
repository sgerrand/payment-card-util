package com.sgerrand.paymentcardutil.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Where each field sits in a record of one IPM parameter table.
 *
 * <p>Fields are kept in the order they were declared, which is the order the
 * CSV tool writes its columns in.
 *
 * @param tableId the table this describes, such as {@code IP0040T1}
 * @param fields  field name to its position in the record
 */
public record ParamTable(String tableId, Map<String, Position> fields) {

    public ParamTable {
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(fields, "fields");
        // LinkedHashMap, not Map.copyOf: field order decides CSV column order.
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    /**
     * A half open range of character positions in a parameter record.
     *
     * @param start first character, counting from zero
     * @param end   one past the last character
     */
    public record Position(int start, int end) {

        public Position {
            if (start < 0) {
                throw new IllegalArgumentException("Start must not be negative: " + start);
            }
            if (end < start) {
                throw new IllegalArgumentException("End (" + end + ") is before start (" + start + ")");
            }
        }

        /** How many characters the field takes up. */
        public int length() {
            return end - start;
        }
    }
}
