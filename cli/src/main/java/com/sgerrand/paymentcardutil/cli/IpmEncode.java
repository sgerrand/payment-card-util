package com.sgerrand.paymentcardutil.cli;

import com.sgerrand.paymentcardutil.config.FieldConfig;
import com.sgerrand.paymentcardutil.config.FieldProcessor;
import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.ipm.IpmReader;
import com.sgerrand.paymentcardutil.ipm.IpmWriter;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Message;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Options;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Rewrites a Mastercard IPM file in a different character set or blocking.
 */
@Command(name = "mci-ipm-encode",
        description = "Rewrite a Mastercard IPM file in another character set or file format.",
        mixinStandardHelpOptions = true)
final class IpmEncode implements Callable<Integer> {

    /** The two file layouts a Mastercard file comes in. */
    enum Format {
        /** Records with a length in front, no blocking. */
        VBS,
        /** The same, in 1014 byte blocks. */
        BLOCKED_1014;

        boolean blocked() {
            return this == BLOCKED_1014;
        }
    }

    @Parameters(index = "0", paramLabel = "IPM_FILE", description = "The IPM file to read.")
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
    Format inFormat = Format.BLOCKED_1014;

    @Option(names = "--out-format", description = "Layout to write: ${COMPLETION-CANDIDATES}. "
            + "Default: ${DEFAULT-VALUE}.")
    Format outFormat = Format.BLOCKED_1014;

    @Option(names = "--debug", description = "Print the full stack trace when something goes wrong.")
    boolean debug;

    @Override
    public Integer call() throws Exception {
        Path out = outFile != null ? outFile : Path.of(inFile + ".out");

        // Copying a file across must not repack its private data. Reading DE48
        // would break it into PDS values, and writing would then build the field
        // afresh, so a field this layout does not fully understand would come out
        // changed. Dropping the PDS handling keeps those fields exactly as read.
        IsoConfig config = withoutPdsProcessing(IsoConfig.defaults());

        Iso8583Options readOptions = Iso8583Options.defaults()
                .withCharset(CommonOptions.charset(inEncoding))
                .withConfig(config);
        Iso8583Options writeOptions = readOptions.withCharset(CommonOptions.charset(outEncoding));

        int count = 0;
        try (InputStream in = Files.newInputStream(inFile);
             OutputStream stream = Files.newOutputStream(out);
             IpmReader reader = IpmReader.open(in, readOptions, inFormat.blocked());
             IpmWriter writer = IpmWriter.open(stream, writeOptions, outFormat.blocked())) {
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
        config.bitConfig().forEach((bit, field) -> {
            if (field.processor() == FieldProcessor.PDS) {
                builder.field(bit, new FieldConfig(
                        field.name(), field.type(), field.length(), field.valueType(),
                        field.dateFormat(), FieldProcessor.NONE, field.processorConfig()));
            }
        });
        return builder.build();
    }
}
