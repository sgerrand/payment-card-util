package com.sgerrand.paymentcardutil.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks the CSV handling matches Python's csv module, which is what cardutil writes with. */
class CsvTest {

    private static String write(List<String> columns, List<Map<String, ?>> rows)
            throws IOException {
        StringWriter out = new StringWriter();
        Csv.write(new BufferedWriter(out), columns, rows);
        return out.toString();
    }

    @Test
    void writesAHeaderAndOneLinePerRow() throws IOException {
        assertEquals(
                "a,b\n1,2\n3,4\n",
                write(
                        List.of("a", "b"),
                        List.of(Map.of("a", "1", "b", "2"), Map.of("a", "3", "b", "4"))));
    }

    @Test
    void missingValuesBecomeEmptyCells() throws IOException {
        assertEquals(
                "a,b,c\n1,,3\n",
                write(List.of("a", "b", "c"), List.of(Map.of("a", "1", "c", "3"))));
    }

    @Test
    void valuesNotInTheColumnListAreLeftOut() throws IOException {
        assertEquals("a\n1\n", write(List.of("a"), List.of(Map.of("a", "1", "z", "9"))));
    }

    @Test
    void onlyAwkwardValuesAreQuoted() throws IOException {
        assertEquals(
                "a,b,c,d\nplain,\"has,comma\",\"has\"\"quote\",\"has\nnewline\"\n",
                write(
                        List.of("a", "b", "c", "d"),
                        List.of(
                                Map.of(
                                        "a",
                                        "plain",
                                        "b",
                                        "has,comma",
                                        "c",
                                        "has\"quote",
                                        "d",
                                        "has\nnewline"))));
    }

    @Test
    void rowsEndWithABareNewline() throws IOException {
        assertEquals("a\n1\n", write(List.of("a"), List.of(Map.of("a", "1"))));
    }

    @Test
    void datesAreWrittenTheWayPythonPrintsThem() {
        assertEquals("2020-03-04 05:06:07", Csv.format(LocalDateTime.of(2020, 3, 4, 5, 6, 7)));
        assertEquals("2020-03-04 00:00:00", Csv.format(LocalDateTime.of(2020, 3, 4, 0, 0, 0)));
    }

    @Test
    void numbersAreWrittenPlainly() {
        assertEquals("12345", Csv.format(12345L));
        assertEquals("0", Csv.format(0L));
        assertEquals("0.000001", Csv.format(new BigDecimal("0.000001")));
    }

    @Test
    void readsBackWhatItWrites() throws IOException {
        String csv = write(List.of("a", "b"), List.of(Map.of("a", "has,comma", "b", "has\"quote")));
        assertEquals(
                List.of(Map.of("a", "has,comma", "b", "has\"quote")),
                Csv.read(new StringReader(csv)));
    }

    @Test
    void readingLeavesOutEmptyCells() throws IOException {
        assertEquals(
                List.of(Map.of("a", "1", "c", "3")), Csv.read(new StringReader("a,b,c\n1,,3\n")));
    }

    @Test
    void readingCopesWithQuotedNewlines() throws IOException {
        assertEquals(
                List.of(Map.of("a", "one\ntwo", "b", "x")),
                Csv.read(new StringReader("a,b\n\"one\ntwo\",x\n")));
    }

    /**
     * Excel and Python's csv module both end rows with a carriage return and a newline. Ending the
     * row on the carriage return would leave the newline to start, and immediately end, a row of
     * its own, and mci-csv-to-ipm would write an empty message for each one.
     */
    @Test
    void readingCopesWithCarriageReturnsAfterAQuotedCell() throws IOException {
        assertEquals(
                List.of(Map.of("a", "1", "b", "has,comma"), Map.of("a", "2", "b", "also,comma")),
                Csv.read(new StringReader("a,b\r\n1,\"has,comma\"\r\n2,\"also,comma\"\r\n")));
    }

    @Test
    void readingCopesWithCarriageReturnsAfterAPlainCell() throws IOException {
        assertEquals(
                List.of(Map.of("a", "1", "b", "2")), Csv.read(new StringReader("a,b\r\n1,2\r\n")));
    }

    @Test
    void aQuotedCellKeepsTheLineEndingsInsideIt() throws IOException {
        assertEquals(
                List.of(Map.of("a", "one\r\ntwo", "b", "x")),
                Csv.read(new StringReader("a,b\r\n\"one\r\ntwo\",x\r\n")));
    }

    @Test
    void aQuotedCellCanEndTheFileWithoutANewline() throws IOException {
        assertEquals(
                List.of(Map.of("a", "1", "b", "has,comma")),
                Csv.read(new StringReader("a,b\r\n1,\"has,comma\"")));
    }

    @Test
    void anEmptyFileHasNoRows() throws IOException {
        assertEquals(List.of(), Csv.read(new StringReader("")));
    }

    @Test
    void aHeaderOnlyFileHasNoRows() throws IOException {
        assertEquals(List.of(), Csv.read(new StringReader("a,b\n")));
    }
}
