package com.sgerrand.paymentcardutil.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sgerrand.paymentcardutil.ipm.IpmReader;
import com.sgerrand.paymentcardutil.ipm.IpmWriter;
import com.sgerrand.paymentcardutil.ipm.VbsReader;
import com.sgerrand.paymentcardutil.ipm.VbsWriter;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Message;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Options;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** Drives the command line tools over real files. */
class IpmToolsTest {

    @TempDir Path directory;

    private Path ipmFile;

    private static final List<Iso8583Message> MESSAGES =
            List.of(
                    Iso8583Message.builder()
                            .mti("1240")
                            .de(2, "4444555566667777")
                            .de(4, 12345L)
                            .de(12, LocalDateTime.of(2020, 3, 4, 5, 6, 7))
                            .de(37, "REF00000001")
                            .pds(158, "0000000000")
                            .build(),
                    Iso8583Message.builder()
                            .mti("1644")
                            .de(2, "5555444433332222")
                            .de(4, 0L)
                            .de(38, "AUTH01")
                            .build());

    @BeforeEach
    void writeIpmFile() throws IOException {
        ipmFile = directory.resolve("clearing.ipm");
        try (OutputStream out = Files.newOutputStream(ipmFile);
                IpmWriter writer = IpmWriter.blocked(out)) {
            writer.writeAll(MESSAGES);
        }
    }

    private static int run(String... args) {
        return new CommandLine(new Cardutil()).execute(args);
    }

    @Test
    void ipmToCsvWritesTheConfiguredColumns() throws IOException {
        Path csv = directory.resolve("out.csv");
        assertEquals(0, run("mci-ipm-to-csv", ipmFile.toString(), "-o", csv.toString()));

        List<String> lines = Files.readAllLines(csv);
        assertEquals(3, lines.size(), "a header and one line per message");
        assertTrue(lines.get(0).startsWith("MTI,DE2,DE3,DE4,DE12"), lines.get(0));
        assertTrue(lines.get(1).startsWith("1240,"), lines.get(1));
    }

    @Test
    void cardNumbersAreMaskedUnlessAskedFor() throws IOException {
        Path masked = directory.resolve("masked.csv");
        Path plain = directory.resolve("plain.csv");
        run("mci-ipm-to-csv", ipmFile.toString(), "-o", masked.toString());
        run("mci-ipm-to-csv", ipmFile.toString(), "-o", plain.toString(), "--unmask-pan");

        String maskedText = Files.readString(masked);
        assertTrue(maskedText.contains("444455******7777"), maskedText);
        assertFalse(maskedText.contains("4444555566667777"), "the full number must not be written");
        assertTrue(Files.readString(plain).contains("4444555566667777"));
    }

    @Test
    void aLayoutMarkingTheCardNumberStillHonoursUnmaskPan() throws IOException {
        // The element is marked by processor and is not called PAN, so the
        // processor is the only thing that can have found it. Parsing leaves
        // the number alone, so --unmask-pan still has something to show.
        Path config = writeConfigMarkingTheCardNumber();
        Path masked = directory.resolve("marked-masked.csv");
        Path plain = directory.resolve("marked-plain.csv");

        assertEquals(
                0,
                run(
                        "mci-ipm-to-csv",
                        ipmFile.toString(),
                        "-o",
                        masked.toString(),
                        "--config-file",
                        config.toString()));
        assertEquals(
                0,
                run(
                        "mci-ipm-to-csv",
                        ipmFile.toString(),
                        "-o",
                        plain.toString(),
                        "--unmask-pan",
                        "--config-file",
                        config.toString()));

        String maskedText = Files.readString(masked);
        assertTrue(maskedText.contains("444455******7777"), maskedText);
        assertFalse(maskedText.contains("4444555566667777"), "the full number must not be written");
        assertTrue(
                Files.readString(plain).contains("4444555566667777"),
                "--unmask-pan writes it in full");
    }

    /** A layout naming the card number something else and marking it by processor. */
    private Path writeConfigMarkingTheCardNumber() throws IOException {
        Path config = directory.resolve("layout.json");
        Files.writeString(
                config,
                """
                {
                  "bit_config": {
                    "2": {"field_name": "Card number", "field_type": "LLVAR",
                          "field_length": 19, "field_processor": "PAN"},
                    "4": {"field_name": "Amount transaction", "field_type": "FIXED",
                          "field_length": 12, "field_python_type": "long"},
                    "12": {"field_name": "Date/Time local transaction", "field_type": "FIXED",
                           "field_length": 12, "field_python_type": "datetime",
                           "field_date_format": "%y%m%d%H%M%S"},
                    "37": {"field_name": "Retrieval reference number", "field_type": "FIXED",
                           "field_length": 12},
                    "38": {"field_name": "Approval code", "field_type": "FIXED", "field_length": 6},
                    "48": {"field_name": "Additional data", "field_type": "LLLVAR",
                           "field_length": 0, "field_processor": "PDS"}
                  },
                  "output_data_elements": ["MTI", "DE2", "DE4", "DE12", "DE37", "DE38"]
                }
                """);
        return config;
    }

    @Test
    void paramEncodeChangesTheCharacterSet() throws IOException {
        Path paramFile = writeParamFile();
        Path encoded = directory.resolve("params.out");

        assertEquals(
                0, run("mci-ipm-param-encode", paramFile.toString(), "-o", encoded.toString()));

        try (InputStream in = Files.newInputStream(encoded);
                VbsReader reader = VbsReader.blocked(in)) {
            assertEquals(
                    "IP0000T1 FIRST RECORD",
                    new String(reader.next(), StandardCharsets.ISO_8859_1));
        }
    }

    @Test
    void paramEncodeTakesTheRecordLengthFromTheConfigFile() throws IOException {
        Path paramFile = writeParamFile();
        Path config = directory.resolve("short-records.json");
        Files.writeString(config, "{\"MAX_VBS_RECORD_LENGTH\": 4}");

        assertNotEquals(
                0,
                run(
                        "mci-ipm-param-encode",
                        paramFile.toString(),
                        "-o",
                        directory.resolve("params.out").toString(),
                        "--config-file",
                        config.toString()),
                "a record past the configured limit must stop the copy");
    }

    /** A small blocked parameter file, written in the cp500 the tools expect. */
    private Path writeParamFile() throws IOException {
        Path paramFile = directory.resolve("params.ipm");
        try (OutputStream out = Files.newOutputStream(paramFile);
                VbsWriter writer = VbsWriter.blocked(out)) {
            writer.write("IP0000T1 FIRST RECORD".getBytes(CommonOptions.charset("cp500")));
            writer.write("IP0000T1 SECOND RECORD".getBytes(CommonOptions.charset("cp500")));
        }
        return paramFile;
    }

    @Test
    void datesAreWrittenWithSecondsAndASpace() throws IOException {
        Path csv = directory.resolve("dates.csv");
        run("mci-ipm-to-csv", ipmFile.toString(), "-o", csv.toString());
        assertTrue(Files.readString(csv).contains("2020-03-04 05:06:07"), "date format");
    }

    @Test
    void csvToIpmRebuildsTheFile() throws IOException {
        Path csv = directory.resolve("out.csv");
        Path rebuilt = directory.resolve("rebuilt.ipm");
        run("mci-ipm-to-csv", ipmFile.toString(), "-o", csv.toString(), "--unmask-pan");
        assertEquals(0, run("mci-csv-to-ipm", csv.toString(), "-o", rebuilt.toString()));

        assertEquals(
                readMessages(ipmFile, Iso8583Options.defaults(), true),
                readMessages(rebuilt, Iso8583Options.defaults(), true));
    }

    @Test
    void encodeChangesCharacterSetAndBlocking() throws IOException {
        Path encoded = directory.resolve("encoded.ipm");
        assertEquals(
                0,
                run(
                        "mci-ipm-encode",
                        ipmFile.toString(),
                        "-o",
                        encoded.toString(),
                        "--in-encoding",
                        "latin_1",
                        "--out-encoding",
                        "cp500",
                        "--out-format",
                        "VBS"));

        assertNotEquals(
                Files.readAllBytes(ipmFile).length,
                Files.readAllBytes(encoded).length,
                "dropping the blocking should change the size");

        List<Iso8583Message> readBack =
                readMessages(
                        encoded,
                        Iso8583Options.defaults().withCharset(Iso8583Options.EBCDIC_CP500),
                        false);
        assertEquals(MESSAGES.size(), readBack.size());
        assertEquals("4444555566667777", readBack.get(0).text(2).orElseThrow());
    }

    @Test
    void encodeLeavesPrivateDataExactlyAsItWasRead() throws IOException {
        Path encoded = directory.resolve("encoded.ipm");
        run(
                "mci-ipm-encode",
                ipmFile.toString(),
                "-o",
                encoded.toString(),
                "--in-encoding",
                "latin_1",
                "--out-encoding",
                "latin_1");

        List<Iso8583Message> original = readMessages(ipmFile, Iso8583Options.defaults(), true);
        List<Iso8583Message> copied = readMessages(encoded, Iso8583Options.defaults(), true);
        assertEquals(
                original.get(0).text(48), copied.get(0).text(48), "DE48 carried across unchanged");
        assertEquals("0000000000", copied.get(0).pds(158).orElseThrow());
    }

    @Test
    void aFileThatIsNotIpmFailsWithAnExplanation() throws IOException {
        Path rubbish = directory.resolve("rubbish.bin");
        Files.write(
                rubbish,
                "this is not an IPM file, not even close".getBytes(StandardCharsets.UTF_8));

        assertNotEquals(
                0,
                run(
                        "mci-ipm-to-csv",
                        rubbish.toString(),
                        "-o",
                        directory.resolve("never.csv").toString()));
    }

    @Test
    void aBadRecordIsReportedWithAHexDump() throws IOException {
        // A record whose length says 40 bytes but whose contents are nonsense.
        Path broken = directory.resolve("broken.ipm");
        byte[] record = new byte[44];
        System.arraycopy(new byte[] {0, 0, 0, 40}, 0, record, 0, 4);
        java.util.Arrays.fill(record, 4, 44, (byte) 0xFF);
        Files.write(broken, record);

        String errors =
                runCapturingErrors(
                        "mci-ipm-to-csv",
                        broken.toString(),
                        "--no1014blocking",
                        "-o",
                        directory.resolve("never.csv").toString());

        assertTrue(errors.contains("Processing stopped:"), errors);
        assertTrue(errors.contains("The trouble is in record 1."), errors);
        // The dump, not a wall of hex: offset, spaced bytes, then a text column.
        assertTrue(errors.contains("00000000: 00 00 00 28 FF FF FF FF "), errors);
    }

    @Test
    void theDumpIsReadInTheCharacterSetBeingParsed() throws IOException {
        // An EBCDIC record claiming a data element that the layout does not use,
        // so it fails after the message type has been read.
        Path broken = directory.resolve("ebcdic.ipm");
        byte[] body = "1240".getBytes(Iso8583Options.EBCDIC_CP500);
        byte[] record = new byte[4 + body.length + 16];
        record[3] = (byte) (body.length + 16);
        System.arraycopy(body, 0, record, 4, body.length);
        record[4 + body.length] = (byte) 0x01; // DE 8, which the layout has no entry for
        Files.write(broken, record);

        String errors =
                runCapturingErrors(
                        "mci-ipm-to-csv",
                        broken.toString(),
                        "--no1014blocking",
                        "--in-encoding",
                        "cp500",
                        "-o",
                        directory.resolve("never.csv").toString());

        assertTrue(errors.contains("read as IBM500"), errors);
        assertTrue(
                errors.contains("1240"), "the text column should show the message type: " + errors);
    }

    private static String runCapturingErrors(String... args) {
        java.io.StringWriter errors = new java.io.StringWriter();
        CommandLine command =
                new CommandLine(new Cardutil())
                        .setExecutionExceptionHandler(new Cardutil.ErrorHandler())
                        .setErr(new java.io.PrintWriter(errors));
        assertNotEquals(0, command.execute(args));
        return errors.toString();
    }

    private static List<Iso8583Message> readMessages(
            Path file, Iso8583Options options, boolean blocked) throws IOException {
        List<Iso8583Message> messages = new ArrayList<>();
        try (InputStream in = Files.newInputStream(file);
                IpmReader reader = IpmReader.open(in, options, blocked)) {
            reader.forEach(messages::add);
        }
        return messages;
    }
}
