package com.sgerrand.paymentcardutil;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lays bytes out as a hex dump, for working out why a file will not read.
 *
 * <pre>
 * 00000000: 00 00 00 1C 54 68 69 73  20 69 73 20 66 69 72 73  ....This is firs
 * 00000010: 74 20 72 65 63 6F 72 64  20 31 32 33 34 35 36 37  t record 1234567
 * </pre>
 *
 * <p>The right hand column is the same bytes read as text. Which character set to read them in
 * matters: an IPM file from a mainframe is EBCDIC, and reading it as Latin-1 turns every readable
 * field into noise. Pass the file's own character set and the column becomes worth looking at.
 */
public final class HexDump {

    /** Bytes shown per line. */
    private static final int BYTES_PER_LINE = 16;

    /** A wider gap goes here, splitting the line in half. */
    private static final int HALF = BYTES_PER_LINE / 2;

    /**
     * Width of the hex column, so a short final line still lines up: two characters a byte, a space
     * between each, and one more splitting the line.
     */
    private static final int HEX_WIDTH = BYTES_PER_LINE * 3;

    /** Gap between the hex column and the text column. */
    private static final String COLUMN_GAP = "  ";

    /** Stands in for anything that would not show up as a character. */
    private static final char UNPRINTABLE = '.';

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    /**
     * The text column, worked out once per character set.
     *
     * <p>A byte always reads as the same character, so a 256 entry table beats decoding one byte at
     * a time: a 6000 byte record dump would otherwise allocate an array and a string per byte.
     */
    private static final Map<Charset, char[]> READABLE = new ConcurrentHashMap<>();

    private HexDump() {}

    /** Dumps every byte, reading the text column as Latin-1. */
    public static String format(byte[] data) {
        return format(data, StandardCharsets.ISO_8859_1);
    }

    /**
     * Dumps every byte.
     *
     * @param charset how to read the text column
     */
    public static String format(byte[] data, Charset charset) {
        return format(data, charset, Integer.MAX_VALUE);
    }

    /**
     * Dumps the start of a run of bytes.
     *
     * <p>A whole clearing record can be 6000 bytes, which is 375 lines nobody will read. Where the
     * dump is going somewhere with a reader attached, cut it short; the tail rarely says anything
     * the head did not.
     *
     * @param charset how to read the text column
     * @param maxBytes how many bytes to show before stopping. A line saying how many were left out
     *     is added when it stops early
     */
    public static String format(byte[] data, Charset charset, int maxBytes) {
        if (data.length == 0) {
            return "";
        }
        int shown = Math.min(data.length, maxBytes);
        StringBuilder out = new StringBuilder();

        for (int offset = 0; offset < shown; offset += BYTES_PER_LINE) {
            if (offset > 0) {
                out.append('\n');
            }
            appendLine(out, data, offset, Math.min(offset + BYTES_PER_LINE, shown), charset);
        }
        if (shown < data.length) {
            out.append('\n').append("... ").append(data.length - shown).append(" more bytes");
        }
        return out.toString();
    }

    private static void appendLine(
            StringBuilder out, byte[] data, int from, int to, Charset charset) {
        appendHex(out, from >>> 24);
        appendHex(out, from >>> 16);
        appendHex(out, from >>> 8);
        appendHex(out, from);
        out.append(": ");

        int width = 0;
        for (int i = from; i < to; i++) {
            if (i > from) {
                out.append(' ');
                width++;
            }
            if ((i - from) == HALF) {
                out.append(' ');
                width++;
            }
            appendHex(out, data[i]);
            width += 2;
        }
        out.append(" ".repeat(HEX_WIDTH - width)).append(COLUMN_GAP);

        char[] readable = READABLE.computeIfAbsent(charset, HexDump::readableTable);
        for (int i = from; i < to; i++) {
            out.append(readable[data[i] & 0xFF]);
        }
    }

    /** Two hex digits of the low byte of {@code value}. */
    private static void appendHex(StringBuilder out, int value) {
        out.append(HEX_DIGITS[(value >>> 4) & 0xF]).append(HEX_DIGITS[value & 0xF]);
    }

    /** How every byte value reads in one character set. */
    private static char[] readableTable(Charset charset) {
        char[] table = new char[256];
        for (int value = 0; value < table.length; value++) {
            table[value] = readable((byte) value, charset);
        }
        return table;
    }

    /**
     * One byte read as a character, or a full stop where it would not show up.
     *
     * <p>What counts as showing up follows Python's {@code str.isprintable}, since that is what the
     * dump this matches uses: a space counts, and so does an accented letter, but control and
     * formatting characters do not.
     */
    private static char readable(byte value, Charset charset) {
        String decoded = new String(new byte[] {value}, charset);
        if (decoded.length() != 1) {
            return UNPRINTABLE;
        }
        char c = decoded.charAt(0);
        if (c == ' ') {
            return c;
        }
        return switch (Character.getType(c)) {
            case Character.CONTROL,
                    Character.FORMAT,
                    Character.SURROGATE,
                    Character.PRIVATE_USE,
                    Character.UNASSIGNED,
                    Character.LINE_SEPARATOR,
                    Character.PARAGRAPH_SEPARATOR,
                    Character.SPACE_SEPARATOR ->
                    UNPRINTABLE;
            default -> c;
        };
    }
}
