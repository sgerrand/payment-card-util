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
     * The names of the row's own details, ahead of the table's fields.
     *
     * <p>They are cardutil's names, not this port's: cardutil hands a parameter row back as a
     * dictionary keyed exactly like this, and the vectors compare against it, so they are fixed by
     * parity rather than chosen. Kept in one place so the header {@link
     * IpmParamReader#columnNames()} builds and the rows {@link #asMap()} builds cannot drift apart.
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
     * The row as a flat map, with the row's own details first.
     *
     * <p>This is the shape cardutil hands a row back in, which is why the keys read the way they
     * do. {@code mci-ipm-param-to-csv} writes it out unchanged, but the shape is not that tool's:
     * changing it would put this port out of step with the Python one.
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
