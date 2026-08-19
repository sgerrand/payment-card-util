package com.sgerrand.paymentcardutil.ipm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One row of an IPM parameter table.
 *
 * @param tableId which table the row came from, such as {@code IP0040T1}
 * @param effectiveTimestamp when the row takes effect, as written in the file
 * @param activeInactiveCode whether the row is live
 * @param fields the row's fields, in the order the table declares them
 */
public record ParamRecord(
        String tableId,
        String effectiveTimestamp,
        String activeInactiveCode,
        Map<String, String> fields) {

    /**
     * The column names for the row's own details, ahead of the table's fields. Kept here so the CSV
     * header {@link IpmParamReader#columnNames()} builds and the rows {@link #asMap()} builds
     * cannot drift apart.
     */
    static final List<String> DETAIL_COLUMNS =
            List.of("table_id", "effective_timestamp", "active_inactive_code");

    public ParamRecord {
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(fields, "fields");
        // LinkedHashMap, not Map.copyOf: field order decides CSV column order.
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    /** One field of the row. */
    public Optional<String> field(String name) {
        return Optional.ofNullable(fields.get(name));
    }

    /**
     * The row as a flat map, with the row's own details first. This is what the CSV tool writes
     * out.
     */
    public Map<String, String> asMap() {
        Map<String, String> all = new LinkedHashMap<>();
        all.put(DETAIL_COLUMNS.get(0), tableId);
        all.put(DETAIL_COLUMNS.get(1), effectiveTimestamp);
        all.put(DETAIL_COLUMNS.get(2), activeInactiveCode);
        all.putAll(fields);
        return all;
    }
}
