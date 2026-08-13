package com.sgerrand.paymentcardutil.cli;

import com.sgerrand.paymentcardutil.card.Pan;
import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.ipm.IpmInfo;
import com.sgerrand.paymentcardutil.ipm.IpmReader;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Message;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Options;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Turns a Mastercard IPM clearing file into CSV.
 */
@Command(name = "mci-ipm-to-csv",
        description = "Write the messages in a Mastercard IPM file out as CSV.",
        mixinStandardHelpOptions = true)
final class IpmToCsv implements Callable<Integer> {

    /** The data element whose name marks it as holding a card number. */
    private static final String PAN_FIELD_NAME = "PAN";

    @Parameters(index = "0", paramLabel = "IPM_FILE", description = "The IPM file to read.")
    Path inFile;

    @Option(names = {"-o", "--out-filename"},
            description = "Where to write the CSV. Default: the input file with .csv on the end.")
    Path outFile;

    @Option(names = "--config-file", description = "JSON file holding the message layout.")
    Path configFile;

    @Option(names = "--unmask-pan",
            description = "Write full card numbers. By default they are masked, since a CSV "
                    + "of card numbers is easy to mislay.")
    boolean unmaskPan;

    @Mixin
    CommonOptions common = new CommonOptions();

    @Override
    public Integer call() throws Exception {
        IsoConfig config = ConfigFiles.load(configFile);
        Path out = outFile != null ? outFile : Path.of(inFile + ".csv");
        Iso8583Options options = Iso8583Options.defaults()
                .withCharset(common.inCharset())
                .withConfig(config);

        List<String> maskedColumns = unmaskPan ? List.of() : panColumns(config);
        List<Map<String, ?>> rows = new ArrayList<>();

        try (InputStream in = Files.newInputStream(inFile);
             IpmReader reader = IpmReader.open(in, options, common.blocked())) {
            for (Iso8583Message message : reader) {
                rows.add(mask(message.values(), maskedColumns));
            }
        } catch (com.sgerrand.paymentcardutil.PaymentCardException e) {
            reportFileTrouble();
            throw e;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(out, common.outCharset())) {
            Csv.write(writer, config.outputDataElements(), rows);
        }
        System.out.println("Wrote " + rows.size() + " messages to " + out);
        return 0;
    }

    /**
     * What was found out about the file's character set, said plainly.
     *
     * <p>The check reads the message type indicator, which is four digits. That
     * separates ASCII from EBCDIC and no further, so naming one EBCDIC code page
     * here would claim more than was actually found.
     */
    private static String describe(IpmInfo.Encoding encoding) {
        return switch (encoding) {
            case ASCII -> "single byte ASCII, such as latin_1";
            case EBCDIC -> "EBCDIC. Which code page cannot be told from the digits alone, "
                    + "so try cp500, then cp037";
            case UNKNOWN -> "could not tell";
        };
    }

    /** Columns holding a card number, according to the layout. */
    private static List<String> panColumns(IsoConfig config) {
        List<String> columns = new ArrayList<>();
        config.bitConfig().forEach((bit, field) -> {
            if (PAN_FIELD_NAME.equalsIgnoreCase(field.name())) {
                columns.add(Iso8583Message.deKey(bit));
            }
        });
        return columns;
    }

    private static Map<String, ?> mask(Map<String, Object> values, List<String> columns) {
        if (columns.isEmpty()) {
            return values;
        }
        Map<String, Object> masked = new LinkedHashMap<>(values);
        for (String column : columns) {
            Object value = masked.get(column);
            if (value instanceof String text) {
                masked.put(column, Pan.maskDigits(text));
            }
        }
        return masked;
    }

    /**
     * Says what could be worked out about the file, which is usually enough to
     * explain why reading it failed.
     */
    private void reportFileTrouble() {
        try (InputStream in = Files.newInputStream(inFile)) {
            IpmInfo info = IpmInfo.inspect(in);
            System.err.println("What this file looks like:");
            if (!info.valid()) {
                System.err.println("  It does not look like an IPM file. " + info.reason());
                return;
            }
            System.err.println("  Character set: " + describe(info.encoding()));
            System.err.println("  1014 byte blocking: " + (info.blocked() ? "yes" : "no")
                    + (info.blocked() == common.blocked() ? "" : ", which is not what was asked for"));
        } catch (java.io.IOException ignored) {
            // The original failure is the one worth reporting.
        }
    }
}
