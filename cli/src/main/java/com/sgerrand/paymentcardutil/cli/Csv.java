package com.sgerrand.paymentcardutil.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reading and writing CSV the way Python's {@code csv} module does, so files move between these
 * tools and cardutil's without surprises.
 *
 * <p>That means: fields are separated by commas and rows by a single newline; a field is quoted
 * only where it holds a comma, a quote or a line break; and a quote inside a quoted field is
 * written twice.
 */
final class Csv {

    /** Python's csv writer uses a bare newline, not a carriage return pair. */
    private static final String ROW_END = "\n";

    /** How a date and time is written, matching Python's {@code str(datetime)}. */
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Csv() {}

    /**
     * Writes rows under a fixed set of columns.
     *
     * <p>A row missing a column writes an empty cell, and anything in a row that is not a named
     * column is left out.
     */
    static void write(BufferedWriter out, List<String> columns, Iterable<Map<String, ?>> rows)
            throws IOException {
        writeRow(out, columns);
        for (Map<String, ?> row : rows) {
            List<String> cells = new ArrayList<>(columns.size());
            for (String column : columns) {
                cells.add(format(row.get(column)));
            }
            writeRow(out, cells);
        }
        out.flush();
    }

    private static void writeRow(BufferedWriter out, List<String> cells) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(quote(cells.get(i)));
        }
        out.write(line.append(ROW_END).toString());
    }

    /** Turns a message value into the text that goes in a cell. */
    static String format(Object value) {
        return switch (value) {
            case null -> "";
            case String text -> text;
            case LocalDateTime dateTime -> DATE_TIME.format(dateTime);
            case BigDecimal decimal -> decimal.toPlainString();
            case byte[] bytes -> HexFormat.of().formatHex(bytes);
            default -> String.valueOf(value);
        };
    }

    private static String quote(String value) {
        boolean needsQuotes =
                value.indexOf(',') >= 0
                        || value.indexOf('"') >= 0
                        || value.indexOf('\n') >= 0
                        || value.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /**
     * Reads a CSV file into one map per row, keyed by the header row.
     *
     * <p>Empty cells are left out, matching what cardutil's CSV to IPM tool does, so an empty
     * column does not become an empty data element.
     */
    static List<Map<String, String>> read(Reader in) throws IOException {
        List<List<String>> rows = parse(in);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> header = rows.get(0);
        List<Map<String, String>> records = new ArrayList<>(rows.size() - 1);

        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            Map<String, String> record = new LinkedHashMap<>();
            for (int c = 0; c < header.size() && c < row.size(); c++) {
                String value = row.get(c);
                if (!value.isEmpty()) {
                    record.put(header.get(c), value);
                }
            }
            records.add(record);
        }
        return records;
    }

    /** Splits CSV text into rows of cells, honouring quotes and quoted newlines. */
    private static List<List<String>> parse(Reader reader) throws IOException {
        // One character of pushback, so the character after a closing quote can
        // be handed back to the main loop rather than handled a second time.
        PushbackReader in = new PushbackReader(reader);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        boolean rowStarted = false;
        int c;

        while ((c = in.read()) >= 0) {
            char ch = (char) c;
            if (inQuotes) {
                if (ch != '"') {
                    cell.append(ch);
                    continue;
                }
                int peek = in.read();
                if (peek == '"') {
                    cell.append('"');
                } else {
                    inQuotes = false;
                    if (peek >= 0) {
                        in.unread(peek);
                    }
                }
                continue;
            }

            switch (ch) {
                case '"' -> {
                    inQuotes = true;
                    rowStarted = true;
                }
                case ',' -> {
                    row.add(cell.toString());
                    cell.setLength(0);
                    rowStarted = true;
                }
                case '\r' -> {
                    // Ignore: a following newline ends the row. Ending the row
                    // on the carriage return instead would leave that newline
                    // to start, and immediately end, a second empty row.
                }
                case '\n' -> {
                    row.add(cell.toString());
                    cell.setLength(0);
                    rows.add(row);
                    row = new ArrayList<>();
                    rowStarted = false;
                }
                default -> {
                    cell.append(ch);
                    rowStarted = true;
                }
            }
        }

        if (rowStarted || !cell.isEmpty() || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }
}
