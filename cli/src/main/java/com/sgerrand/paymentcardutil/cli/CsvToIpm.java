package com.sgerrand.paymentcardutil.cli;

import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.ipm.IpmWriter;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Message;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Options;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Builds a Mastercard IPM clearing file from CSV. */
@Command(
        name = "mci-csv-to-ipm",
        description = "Build a Mastercard IPM file from CSV.",
        mixinStandardHelpOptions = true)
final class CsvToIpm implements Callable<Integer>, FileCommand {

    @Parameters(index = "0", paramLabel = "CSV_FILE", description = "The CSV file to read.")
    Path inFile;

    @Option(
            names = {"-o", "--out-filename"},
            description =
                    "Where to write the IPM file. Default: the input file with .ipm on the end.")
    Path outFile;

    @Option(names = "--config-file", description = "JSON file holding the message layout.")
    Path configFile;

    @Mixin CommonOptions common = new CommonOptions();

    @Override
    public Integer call() throws Exception {
        IsoConfig config = ConfigFiles.load(configFile);
        Path out = CommonOptions.outputPath(inFile, outFile, ".ipm");
        Iso8583Options options =
                Iso8583Options.defaults().withCharset(common.outCharset()).withConfig(config);

        List<Map<String, String>> rows;
        try (BufferedReader reader = Files.newBufferedReader(inFile, common.inCharset())) {
            rows = Csv.read(reader);
        }

        try (OutputStream stream = Files.newOutputStream(out);
                IpmWriter writer = IpmWriter.open(stream, options, common.blocked())) {
            for (Map<String, String> row : rows) {
                writer.write(Iso8583Message.of(row));
            }
        }
        System.out.println("Wrote " + rows.size() + " messages to " + out);
        return 0;
    }

    @Override
    public Path inputFile() {
        return inFile;
    }

    @Override
    public InputOptions inputOptions() {
        return common;
    }
}
