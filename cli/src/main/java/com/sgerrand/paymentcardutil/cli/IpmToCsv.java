package com.sgerrand.paymentcardutil.cli;

import com.sgerrand.paymentcardutil.card.Pan;
import com.sgerrand.paymentcardutil.config.FieldProcessors;
import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.ipm.IpmReader;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Message;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Options;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Turns a Mastercard IPM clearing file into CSV. */
@Command(
        name = "mci-ipm-to-csv",
        description = "Write the messages in a Mastercard IPM file out as CSV.",
        mixinStandardHelpOptions = true)
final class IpmToCsv implements Callable<Integer>, FileCommand {

    /** The field processors that mark an element as holding a card number. */
    private static final List<String> PAN_PROCESSORS =
            List.of(FieldProcessors.PAN, FieldProcessors.PAN_PREFIX);

    /** The element name that also marks a card number. See {@link #panColumns(IsoConfig)}. */
    private static final String PAN_FIELD_NAME = "PAN";

    @Parameters(index = "0", paramLabel = "IPM_FILE", description = "The IPM file to read.")
    Path inFile;

    @Option(
            names = {"-o", "--out-filename"},
            description = "Where to write the CSV. Default: the input file with .csv on the end.")
    Path outFile;

    @Option(names = "--config-file", description = "JSON file holding the message layout.")
    Path configFile;

    @Option(
            names = "--unmask-pan",
            description =
                    "Write full card numbers. By default they are masked, since a CSV "
                            + "of card numbers is easy to mislay.")
    boolean unmaskPan;

    @Mixin CommonOptions common = new CommonOptions();

    @Override
    public Integer call() throws Exception {
        IsoConfig config = ConfigFiles.load(configFile);
        Path out = CommonOptions.outputPath(inFile, outFile, ".csv");
        Iso8583Options options =
                Iso8583Options.defaults().withCharset(common.inCharset()).withConfig(config);

        List<String> maskedColumns = unmaskPan ? List.of() : panColumns(config);
        List<Map<String, ?>> rows = new ArrayList<>();

        try (InputStream in = Files.newInputStream(inFile);
                IpmReader reader = IpmReader.open(in, options, common.blocked())) {
            for (Iso8583Message message : reader) {
                rows.add(mask(message.values(), maskedColumns));
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(out, common.outCharset())) {
            Csv.write(writer, config.outputDataElements(), rows);
        }
        System.out.println("Wrote " + rows.size() + " messages to " + out);
        return 0;
    }

    /**
     * Columns holding a card number, according to the layout.
     *
     * <p>Two things mark one, and either is enough. The {@code PAN} or {@code PAN-PREFIX} field
     * processor is cardutil's own way of saying it and the only machine readable signal there is.
     * The element's name is a label meant for people, so it can be translated, written out in full
     * or reused, which makes it a poor signal on its own.
     *
     * <p>Both count because neither covers everything. The built-in Mastercard layout marks no
     * element by processor, since it is generated from cardutil's config and cardutil does not
     * mask, so going by the processor alone would quietly write full card numbers. Going by the
     * name alone misses an element a layout marks outright. Taking either means a column has to
     * shake off both to come out unmasked.
     */
    static List<String> panColumns(IsoConfig config) {
        List<String> columns = new ArrayList<>();
        config.bitConfig()
                .forEach(
                        (bit, field) -> {
                            // Most elements name no processor at all, and an
                            // immutable list will not even be asked whether it
                            // holds null.
                            if ((field.processor() != null
                                            && PAN_PROCESSORS.contains(field.processor()))
                                    || PAN_FIELD_NAME.equalsIgnoreCase(field.name())) {
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

    @Override
    public Path inputFile() {
        return inFile;
    }

    @Override
    public InputOptions inputOptions() {
        return common;
    }

    @Override
    public boolean readsIpmMessages() {
        return true;
    }
}
