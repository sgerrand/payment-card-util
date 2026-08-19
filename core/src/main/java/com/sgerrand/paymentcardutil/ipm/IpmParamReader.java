package com.sgerrand.paymentcardutil.ipm;

import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.config.ParamTable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
public final class IpmParamReader extends LookAheadIterator<ParamRecord> implements Iterable<ParamRecord>, Closeable {

    /** Where the index records say which table they describe. */
    private static final int INDEX_KEY_START = 11;
    private static final int INDEX_KEY_END = 19;
    private static final int INDEX_TABLE_ID_START = 19;
    private static final int INDEX_TABLE_ID_END = 27;
    private static final int INDEX_SUB_ID_START = 243;
    private static final int INDEX_SUB_ID_END = 246;

    /**
     * Where the parts common to every record sit.
     *
     * <p>The two record shapes differ only in these offsets, so the shape is
     * picked once and read from here rather than branched on at each use.
     *
     * @param timestampEnd where the effective timestamp stops
     * @param activeEnd    where the active/inactive code stops
     * @param tableEnd     where the table name stops: the full table id in an
     *                     expanded record, the three character code in a
     *                     compressed one
     * @param fieldOffset  how far the config's field positions have to shift.
     *                     Compressed records leave out the 8 character table id
     *                     the config counts from
     */
    private record Shape(int timestampEnd, int activeEnd, int tableEnd, int fieldOffset) {

        static final Shape COMPRESSED = new Shape(7, 8, 11, -8);
        static final Shape EXPANDED = new Shape(10, 11, 19, 0);
    }

    private static final String INDEX_TABLE = "IP0000T1";
    private static final String TRAILER_PREFIX = "TRAILER RECORD IP0000T1";

    /** The default character set for parameter files, matching cardutil. */
    public static final Charset DEFAULT_CHARSET = java.nio.charset.StandardCharsets.ISO_8859_1;

    private final VbsReader records;
    private final String tableId;
    private final Charset charset;
    private final boolean expanded;
    private final Shape shape;
    private final ParamTable table;
    private final Map<String, String> tableIndex = new HashMap<>();

    private IpmParamReader(VbsReader records, String tableId, Charset charset, boolean expanded, IsoConfig config) {
        this.records = records;
        this.tableId = tableId;
        this.charset = charset;
        this.expanded = expanded;
        this.shape = expanded ? Shape.EXPANDED : Shape.COMPRESSED;
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
    public List<String> columnNames() {
        List<String> names = new ArrayList<>(ParamRecord.DETAIL_COLUMNS);
        names.addAll(table.fields().keySet());
        return names;
    }

    @Override
    public Iterator<ParamRecord> iterator() {
        return this;
    }

    @Override
    String endMessage() {
        return "No more rows in " + tableId;
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
    @Override
    ParamRecord readNext() {
        while (records.hasNext()) {
            String record = new String(records.next(), charset);
            String recordTableId = tableIdOf(record);
            if (!tableId.equals(recordTableId)) {
                continue;
            }
            return new ParamRecord(
                    recordTableId,
                    between(record, 0, shape.timestampEnd()),
                    between(record, shape.timestampEnd(), shape.activeEnd()),
                    readFields(record));
        }
        return null;
    }

    private String tableIdOf(String record) {
        String name = between(record, shape.activeEnd(), shape.tableEnd());
        // An expanded record names its table outright; a compressed one gives
        // the three character code the file's index translates.
        return expanded ? name : tableIndex.get(name);
    }

    private Map<String, String> readFields(String record) {
        int offset = shape.fieldOffset();
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
