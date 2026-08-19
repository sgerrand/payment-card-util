package com.sgerrand.paymentcardutil.cli;

import com.sgerrand.paymentcardutil.config.FieldConfig;
import com.sgerrand.paymentcardutil.config.FieldProcessor;
import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.ipm.IpmReader;
import com.sgerrand.paymentcardutil.ipm.IpmWriter;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Message;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Options;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

/** Rewrites a Mastercard IPM file in a different character set or blocking. */
@Command(
        name = "mci-ipm-encode",
        description = "Rewrite a Mastercard IPM file in another character set or file format.",
        mixinStandardHelpOptions = true)
final class IpmEncode implements Callable<Integer>, FileCommand {

    @Parameters(index = "0", paramLabel = "IPM_FILE", description = "The IPM file to read.")
    Path inFile;

    @Mixin EncodeOptions options = new EncodeOptions();

    @Override
    public Integer call() throws Exception {
        Path out = CommonOptions.outputPath(inFile, options.outFile, ".out");

        // Copying a file across must not repack its private data. Reading DE48
        // would break it into PDS values, and writing would then build the field
        // afresh, so a field this layout does not fully understand would come out
        // changed. Dropping the PDS handling keeps those fields exactly as read.
        IsoConfig config = withoutPdsProcessing(IsoConfig.defaults());

        Iso8583Options readOptions =
                Iso8583Options.defaults().withCharset(options.inCharset()).withConfig(config);
        Iso8583Options writeOptions = readOptions.withCharset(options.outCharset());

        int count = 0;
        try (InputStream in = Files.newInputStream(inFile);
                OutputStream stream = Files.newOutputStream(out);
                IpmReader reader = IpmReader.open(in, readOptions, options.inFormat.blocked());
                IpmWriter writer =
                        IpmWriter.open(stream, writeOptions, options.outFormat.blocked())) {
            for (Iso8583Message message : reader) {
                writer.write(message);
                count++;
            }
        }
        System.out.println("Wrote " + count + " messages to " + out);
        return 0;
    }

    /** The same layout with private data left packed as it was read. */
    static IsoConfig withoutPdsProcessing(IsoConfig config) {
        IsoConfig.Builder builder = config.toBuilder();
        for (int bit : config.bitsWithProcessor(FieldProcessor.PDS)) {
            FieldConfig field = config.bitConfig().get(bit);
            builder.field(
                    bit,
                    new FieldConfig(
                            field.name(),
                            field.type(),
                            field.length(),
                            field.valueType(),
                            field.dateFormat(),
                            FieldProcessor.NONE,
                            field.processorConfig()));
        }
        return builder.build();
    }

    @Override
    public Path inputFile() {
        return inFile;
    }

    @Override
    public InputOptions inputOptions() {
        return options;
    }

    @Override
    public boolean readsIpmMessages() {
        return true;
    }
}
