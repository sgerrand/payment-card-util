package com.sgerrand.paymentcardutil.cli;

import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.ipm.VbsReader;
import com.sgerrand.paymentcardutil.ipm.VbsWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

/**
 * Rewrites a Mastercard IPM parameter file in a different character set or blocking.
 *
 * <p>Parameter records are plain text, not ISO 8583 messages, so each record is simply decoded and
 * encoded again rather than being taken apart.
 */
@Command(
        name = "mci-ipm-param-encode",
        description =
                "Rewrite a Mastercard IPM parameter file in another character set or file format.",
        mixinStandardHelpOptions = true)
final class IpmParamEncode implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "PARAM_FILE", description = "The parameter file to read.")
    Path inFile;

    @Mixin EncodeOptions options = new EncodeOptions();

    @Override
    public Integer call() throws Exception {
        Path out = CommonOptions.outputPath(inFile, options.outFile, ".out");
        Charset from = options.inCharset();
        Charset to = options.outCharset();

        // Records are copied as they stand, so no layout is needed to read
        // them. The config still says how long a record may be before the file
        // is called damaged, which is the one thing this command must not
        // decide for itself.
        IsoConfig config = ConfigFiles.load(null);

        int count = 0;
        try (InputStream in = Files.newInputStream(inFile);
                OutputStream stream = Files.newOutputStream(out);
                VbsReader reader =
                        options.inFormat.blocked()
                                ? VbsReader.blocked(in, config)
                                : VbsReader.of(in, config);
                VbsWriter writer =
                        options.outFormat.blocked()
                                ? VbsWriter.blocked(stream)
                                : VbsWriter.of(stream)) {
            for (byte[] record : reader) {
                writer.write(new String(record, from).getBytes(to));
                count++;
            }
        }
        System.out.println("Wrote " + count + " records to " + out);
        return 0;
    }
}
