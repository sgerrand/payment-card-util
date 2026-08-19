package com.sgerrand.paymentcardutil.cli;

import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.ipm.IpmParamReader;
import com.sgerrand.paymentcardutil.ipm.ParamRecord;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Pulls one table out of a Mastercard IPM parameter extract and writes it as
 * CSV.
 */
@Command(name = "mci-ipm-param-to-csv",
        description = "Write one table from a Mastercard IPM parameter file out as CSV.",
        mixinStandardHelpOptions = true)
final class IpmParamToCsv implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "PARAM_FILE", description = "The parameter file to read.")
    Path inFile;

    @Parameters(index = "1", paramLabel = "TABLE_ID",
            description = "Which table to pull out, such as IP0040T1.")
    String tableId;

    @Option(names = {"-o", "--out-filename"},
            description = "Where to write the CSV. Default: the input file, the table id and .csv.")
    Path outFile;

    @Option(names = "--config-file", description = "JSON file holding the table layouts.")
    Path configFile;

    @Option(names = "--expanded",
            description = "The records carry the full table id rather than a short code.")
    boolean expanded;

    @Mixin
    CommonOptions common = new CommonOptions();

    @Override
    public Integer call() throws Exception {
        IsoConfig config = ConfigFiles.load(configFile);
        Path out = CommonOptions.outputPath(inFile, outFile, "_" + tableId + ".csv");

        List<Map<String, ?>> rows = new ArrayList<>();
        List<String> columns;

        try (InputStream in = Files.newInputStream(inFile);
             IpmParamReader reader = IpmParamReader.open(
                     in, tableId, common.inCharset(), common.blocked(), expanded, config)) {
            columns = reader.columnNames();
            for (ParamRecord record : reader) {
                rows.add(record.asMap());
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(out, common.outCharset())) {
            Csv.write(writer, columns, rows);
        }
        System.out.println("Wrote " + rows.size() + " rows of " + tableId + " to " + out);
        return 0;
    }
}
