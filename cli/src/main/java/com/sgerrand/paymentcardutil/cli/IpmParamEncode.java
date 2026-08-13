package com.sgerrand.paymentcardutil.cli;

import com.sgerrand.paymentcardutil.ipm.VbsReader;
import com.sgerrand.paymentcardutil.ipm.VbsWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Rewrites a Mastercard IPM parameter file in a different character set or
 * blocking.
 *
 * <p>Parameter records are plain text, not ISO 8583 messages, so each record is
 * simply decoded and encoded again rather than being taken apart.
 */
@Command(name = "mci-ipm-param-encode",
        description = "Rewrite a Mastercard IPM parameter file in another character set or file format.",
        mixinStandardHelpOptions = true)
final class IpmParamEncode implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "PARAM_FILE", description = "The parameter file to read.")
    Path inFile;

    @Option(names = {"-o", "--out-filename"},
            description = "Where to write the result. Default: the input file with .out on the end.")
    Path outFile;

    @Option(names = "--in-encoding", description = "Character set of the input. Default: ${DEFAULT-VALUE}.")
    String inEncoding = "cp500";

    @Option(names = "--out-encoding", description = "Character set to write. Default: ${DEFAULT-VALUE}.")
    String outEncoding = "latin_1";

    @Option(names = "--in-format", description = "Layout of the input: ${COMPLETION-CANDIDATES}. "
            + "Default: ${DEFAULT-VALUE}.")
    IpmEncode.Format inFormat = IpmEncode.Format.BLOCKED_1014;

    @Option(names = "--out-format", description = "Layout to write: ${COMPLETION-CANDIDATES}. "
            + "Default: ${DEFAULT-VALUE}.")
    IpmEncode.Format outFormat = IpmEncode.Format.BLOCKED_1014;

    @Option(names = "--debug", description = "Print the full stack trace when something goes wrong.")
    boolean debug;

    @Override
    public Integer call() throws Exception {
        Path out = outFile != null ? outFile : Path.of(inFile + ".out");
        Charset from = CommonOptions.charset(inEncoding);
        Charset to = CommonOptions.charset(outEncoding);

        int count = 0;
        try (InputStream in = Files.newInputStream(inFile);
             OutputStream stream = Files.newOutputStream(out);
             VbsReader reader = inFormat.blocked() ? VbsReader.blocked(in) : VbsReader.of(in);
             VbsWriter writer = outFormat.blocked() ? VbsWriter.blocked(stream) : VbsWriter.of(stream)) {
            for (byte[] record : reader) {
                writer.write(new String(record, from).getBytes(to));
                count++;
            }
        }
        System.out.println("Wrote " + count + " records to " + out);
        return 0;
    }
}
