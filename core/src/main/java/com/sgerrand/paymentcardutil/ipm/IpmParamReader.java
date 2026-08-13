package com.sgerrand.paymentcardutil.ipm;

import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.config.ParamTable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Reads one table out of a Mastercard IPM parameter extract file.
 *
 * <pre>{@code
 * try (IpmParamReader reader = IpmParamReader.blocked(in, "IP0040T1")) {
 *     for (ParamRecord record : reader) {
 *         System.out.println(record.field("gcms_product_id").orElse(""));
 *     }
 * }
 * }</pre>
 *
 * <p>A parameter file starts with an index, the {@code IP0000T1} table, saying
 * which three character code stands for which table. The reader walks that index
 * first, then hands back only the rows belonging to the table asked for.
 *
 * <p>Records come in two shapes. Compressed records, the usual kind, name their
 * table by its short code. Expanded records carry the full table id instead; set
 * {@code expanded} for those.
 */
public final class IpmParamReader implements Iterable<ParamRecord>, Iterator<ParamRecord>, Closeable {

    /** Where the index records say which table they describe. */
    private static final int INDEX_KEY_START = 11;
    private static final int INDEX_KEY_END = 19;
    private static final int INDEX_TABLE_ID_START = 19;
    private static final int INDEX_TABLE_ID_END = 27;
    private static final int INDEX_SUB_ID_START = 243;
    private static final int INDEX_SUB_ID_END = 246;

    /** Where the common parts sit in a compressed record. */
    private static final int COMPRESSED_TIMESTAMP_END = 7;
    private static final int COMPRESSED_ACTIVE_END = 8;
    private static final int COMPRESSED_SUB_ID_END = 11;

    /** Where the common parts sit in an expanded record. */
    private static final int EXPANDED_TIMESTAMP_END = 10;
    private static final int EXPANDED_ACTIVE_END = 11;
    private static final int EXPANDED_TABLE_ID_END = 19;

    /** Compressed records leave out the 8 character table id the config counts from. */
    private static final int COMPRESSED_FIELD_OFFSET = -8;

    private static final String INDEX_TABLE = "IP0000T1";
    private static final String TRAILER_PREFIX = "TRAILER RECORD IP0000T1";

    /** The default character set for parameter files, matching cardutil. */
    public static final Charset DEFAULT_CHARSET = java.nio.charset.StandardCharsets.ISO_8859_1;

    private final VbsReader records;
    private final String tableId;
    private final Charset charset;
    private final boolean expanded;
    private final ParamTable table;
    private final Map<String, String> tableIndex = new HashMap<>();

    private ParamRecord pending;
    private boolean exhausted;

    private IpmParamReader(VbsReader records, String tableId, Charset charset, boolean expanded, IsoConfig config) {
        this.records = records;
        this.tableId = tableId;
        this.charset = charset;
        this.expanded = expanded;
        this.table = config.parameterTable(tableId).orElseThrow(() -> new IpmDataException(
                "No layout configured for parameter table " + tableId));
        readIndex();
    }

    /** Reads a parameter file with no blocking, using the default settings. */
    public static IpmParamReader of(InputStream in, String tableId) {
        return new IpmParamReader(VbsReader.of(in), tableId, DEFAULT_CHARSET, false, IsoConfig.defaults());
    }

    /** Reads a parameter file in 1014 byte blocks, using the default settings. */
    public static IpmParamReader blocked(InputStream in, String tableId) {
        return new IpmParamReader(VbsReader.blocked(in), tableId, DEFAULT_CHARSET, false, IsoConfig.defaults());
    }

    /**
     * Reads a parameter file.
     *
     * @param in       the file
     * @param tableId  the table wanted, such as {@code IP0040T1}
     * @param charset  the file's character set
     * @param blocked  whether the file is in 1014 byte blocks
     * @param expanded whether records carry the full table id
     * @param config   the layout to read the table with
     */
    public static IpmParamReader open(InputStream in, String tableId, Charset charset,
                                      boolean blocked, boolean expanded, IsoConfig config) {
        VbsReader records = blocked ? VbsReader.blocked(in, config) : VbsReader.of(in, config);
        return new IpmParamReader(records, tableId, charset, expanded, config);
    }

    /** Which table this reader is pulling out. */
    public String tableId() {
        return tableId;
    }

    /**
     * The column names this table's rows carry, in order: the row details first,
     * then the table's own fields.
     */
    public java.util.List<String> columnNames() {
        java.util.List<String> names = new java.util.ArrayList<>(
                java.util.List.of("table_id", "effective_timestamp", "active_inactive_code"));
        names.addAll(table.fields().keySet());
        return names;
    }

    @Override
    public Iterator<ParamRecord> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        if (pending != null) {
            return true;
        }
        if (exhausted) {
            return false;
        }
        pending = readNextMatching();
        if (pending == null) {
            exhausted = true;
            return false;
        }
        return true;
    }

    @Override
    public ParamRecord next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more rows in " + tableId);
        }
        ParamRecord record = pending;
        pending = null;
        return record;
    }

    @Override
    public void close() throws IOException {
        records.close();
    }

    /**
     * Walks the {@code IP0000T1} index at the front of the file, learning which
     * short code stands for which table.
     *
     * @throws IpmDataException if the index has no trailer, which means the file
     *                          was cut short or is not a parameter file
     */
    private void readIndex() {
        boolean trailerFound = false;
        while (records.hasNext()) {
            String record = new String(records.next(), charset);
            if (between(record, INDEX_KEY_START, INDEX_KEY_END).equals(INDEX_TABLE)) {
                tableIndex.put(
                        between(record, INDEX_SUB_ID_START, INDEX_SUB_ID_END),
                        between(record, INDEX_TABLE_ID_START, INDEX_TABLE_ID_END));
            }
            if (record.startsWith(TRAILER_PREFIX)) {
                trailerFound = true;
                break;
            }
        }
        if (!trailerFound) {
            throw new IpmDataException("Parameter file has no " + INDEX_TABLE + " trailer record");
        }
    }

    /** @return the next row belonging to the wanted table, or {@code null} at the end */
    private ParamRecord readNextMatching() {
        while (records.hasNext()) {
            String record = new String(records.next(), charset);
            String recordTableId = tableIdOf(record);
            if (!tableId.equals(recordTableId)) {
                continue;
            }
            return new ParamRecord(
                    recordTableId,
                    expanded ? between(record, 0, EXPANDED_TIMESTAMP_END)
                             : between(record, 0, COMPRESSED_TIMESTAMP_END),
                    expanded ? between(record, EXPANDED_TIMESTAMP_END, EXPANDED_ACTIVE_END)
                             : between(record, COMPRESSED_TIMESTAMP_END, COMPRESSED_ACTIVE_END),
                    readFields(record));
        }
        return null;
    }

    private String tableIdOf(String record) {
        if (expanded) {
            return between(record, EXPANDED_ACTIVE_END, EXPANDED_TABLE_ID_END);
        }
        return tableIndex.get(between(record, COMPRESSED_ACTIVE_END, COMPRESSED_SUB_ID_END));
    }

    private Map<String, String> readFields(String record) {
        int offset = expanded ? 0 : COMPRESSED_FIELD_OFFSET;
        Map<String, String> fields = new LinkedHashMap<>();
        table.fields().forEach((name, position) ->
                fields.put(name, between(record, position.start() + offset, position.end() + offset)));
        return fields;
    }

    /**
     * A slice of a record, clipped to what is actually there.
     *
     * <p>Python string slicing never runs off the end, and parameter records are
     * often shorter than the layout allows, so a short record gives a short
     * value rather than an error.
     */
    private static String between(String record, int start, int end) {
        int from = Math.min(Math.max(start, 0), record.length());
        int to = Math.min(Math.max(end, from), record.length());
        return record.substring(from, to);
    }
}
