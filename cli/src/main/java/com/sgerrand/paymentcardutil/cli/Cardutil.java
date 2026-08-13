package com.sgerrand.paymentcardutil.cli;

import com.sgerrand.paymentcardutil.HexDump;
import com.sgerrand.paymentcardutil.PaymentCardException;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Options;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Optional;

/**
 * The command line front end.
 *
 * <p>Run a tool by name:
 *
 * <pre>{@code
 * java -jar payment-card-util-cli.jar mci-ipm-to-csv clearing.ipm
 * }</pre>
 *
 * <p>The tools match those in the Python cardutil package, with hyphens in place
 * of underscores.
 */
@Command(
        name = "payment-card-util",
        mixinStandardHelpOptions = true,
        versionProvider = Cardutil.Version.class,
        description = "Tools for Mastercard IPM clearing and parameter files.",
        subcommands = {
                IpmToCsv.class,
                CsvToIpm.class,
                IpmEncode.class,
                IpmParamToCsv.class,
                IpmParamEncode.class,
        })
public final class Cardutil {

    /** picocli builds one of these to hang the subcommands off. */
    Cardutil() {
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Cardutil())
                .setExecutionExceptionHandler(new ErrorHandler())
                .execute(args));
    }

    /** Reports the version stamped into the jar. */
    static final class Version implements CommandLine.IVersionProvider {

        @Override
        public String[] getVersion() {
            String version = Cardutil.class.getPackage().getImplementationVersion();
            return new String[] {"payment-card-util " + (version == null ? "(from source)" : version)};
        }
    }

    /**
     * Turns a data problem into a short message, a hex dump of the bytes that
     * caused it, and a failure code, rather than a stack trace nobody asked for.
     * {@code --debug} brings the trace back.
     */
    static final class ErrorHandler implements CommandLine.IExecutionExceptionHandler {

        /**
         * How much of the offending record to dump. A clearing record can run to
         * 6000 bytes, and whatever went wrong is almost always near the front.
         */
        private static final int DUMP_LIMIT = 512;

        @Override
        public int handleExecutionException(Exception exception, CommandLine command,
                                            CommandLine.ParseResult parseResult) {
            PrintWriter err = command.getErr();
            err.println("Processing stopped: " + exception.getMessage());

            if (exception instanceof PaymentCardException problem) {
                problem.recordNumber().ifPresent(number ->
                        err.println("The trouble is in record " + number + "."));
                problem.binaryContext().ifPresent(bytes -> {
                    err.println("Bytes at that point, read as " + inputCharset(parseResult) + ":");
                    err.println(HexDump.format(bytes, inputCharset(parseResult), DUMP_LIMIT));
                });
            }

            if (isDebug(parseResult)) {
                exception.printStackTrace(err);
            } else {
                err.println("Run again with --debug for the full details.");
            }
            return CommandLine.ExitCode.SOFTWARE;
        }

        /**
         * The character set the tool was reading with, so the text column of the
         * dump is worth reading. An EBCDIC record shown as Latin-1 is noise.
         */
        private static Charset inputCharset(CommandLine.ParseResult parseResult) {
            return ErrorHandler.<String>optionValue(parseResult, "--in-encoding")
                    .map(CommonOptions::charset)
                    .orElse(Iso8583Options.DEFAULT_CHARSET);
        }

        private static boolean isDebug(CommandLine.ParseResult parseResult) {
            return ErrorHandler.<Boolean>optionValue(parseResult, "--debug")
                    .orElse(false);
        }

        /**
         * An option's value on the subcommand that ran, whether it was typed or
         * left to its default.
         */
        private static <T> Optional<T> optionValue(CommandLine.ParseResult parseResult, String name) {
            if (!parseResult.hasSubcommand()) {
                return Optional.empty();
            }
            CommandLine.Model.OptionSpec option =
                    parseResult.subcommand().commandSpec().findOption(name);
            return option == null ? Optional.empty() : Optional.ofNullable(option.getValue());
        }
    }
}
