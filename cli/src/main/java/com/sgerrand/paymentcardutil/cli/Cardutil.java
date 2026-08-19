package com.sgerrand.paymentcardutil.cli;

import com.sgerrand.paymentcardutil.HexDump;
import com.sgerrand.paymentcardutil.PaymentCardException;
import com.sgerrand.paymentcardutil.ipm.IpmInfo;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Options;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Optional;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * The command line front end.
 *
 * <p>Run a tool by name:
 *
 * <pre>{@code
 * java -jar payment-card-util-cli.jar mci-ipm-to-csv clearing.ipm
 * }</pre>
 *
 * <p>The tools match those in the Python cardutil package, with hyphens in place of underscores.
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
    Cardutil() {}

    public static void main(String[] args) {
        System.exit(commandLine().execute(args));
    }

    /** The front end, wired up to report a data problem plainly. */
    static CommandLine commandLine() {
        return new CommandLine(new Cardutil()).setExecutionExceptionHandler(new ErrorHandler());
    }

    /** Reports the version stamped into the jar. */
    static final class Version implements CommandLine.IVersionProvider {

        @Override
        public String[] getVersion() {
            String version = Cardutil.class.getPackage().getImplementationVersion();
            return new String[] {
                "payment-card-util " + (version == null ? "(from source)" : version)
            };
        }
    }

    /**
     * Turns a data problem into a short message, a hex dump of the bytes that caused it, and a
     * failure code, rather than a stack trace nobody asked for. {@code --debug} brings the trace
     * back.
     *
     * <p>Where the command was reading an IPM file, it also says what the file looks like. Most
     * failures are a file read in the wrong character set or the wrong blocking, and those two
     * lines usually name the mistake outright.
     */
    static final class ErrorHandler implements CommandLine.IExecutionExceptionHandler {

        /**
         * How much of the offending record to dump. A clearing record can run to 6000 bytes, and
         * whatever went wrong is almost always near the front.
         */
        private static final int DUMP_LIMIT = 512;

        @Override
        public int handleExecutionException(
                Exception exception, CommandLine command, CommandLine.ParseResult parseResult) {
            PrintWriter err = command.getErr();
            Optional<FileCommand> fileCommand = fileCommand(parseResult);
            Charset charset =
                    fileCommand
                            .map(reader -> reader.inputOptions().inCharset())
                            .orElse(Iso8583Options.DEFAULT_CHARSET);

            err.println("Processing stopped: " + exception.getMessage());

            if (exception instanceof PaymentCardException problem) {
                problem.recordNumber()
                        .ifPresent(
                                number -> err.println("The trouble is in record " + number + "."));
                problem.binaryContext()
                        .ifPresent(
                                bytes -> {
                                    err.println("Bytes at that point, read as " + charset + ":");
                                    err.println(HexDump.format(bytes, charset, DUMP_LIMIT));
                                });
                fileCommand
                        .filter(FileCommand::readsIpmMessages)
                        .ifPresent(reader -> describeFile(err, reader));
            }

            if (fileCommand.map(reader -> reader.inputOptions().debug()).orElse(false)) {
                exception.printStackTrace(err);
            } else {
                err.println("Run again with --debug for the full details.");
            }
            return CommandLine.ExitCode.SOFTWARE;
        }

        /**
         * Says what could be worked out about the file, which is usually enough to explain why
         * reading it failed.
         */
        private static void describeFile(PrintWriter err, FileCommand reader) {
            try (InputStream in = Files.newInputStream(reader.inputFile())) {
                IpmInfo info = IpmInfo.inspect(in);
                err.println("What this file looks like:");
                if (!info.valid()) {
                    err.println("  It does not look like an IPM file. " + info.reason());
                    return;
                }
                err.println("  Character set: " + describe(info.encoding()));
                err.println(
                        "  1014 byte blocking: "
                                + (info.blocked() ? "yes" : "no")
                                + (info.blocked() == reader.inputOptions().blocked()
                                        ? ""
                                        : ", which is not what was asked for"));
            } catch (IOException ignored) {
                // The original failure is the one worth reporting.
            }
        }

        /**
         * What was found out about the file's character set, said plainly.
         *
         * <p>The check reads the message type indicator, which is four digits. That separates ASCII
         * from EBCDIC and no further, so naming one EBCDIC code page here would claim more than was
         * actually found.
         */
        private static String describe(IpmInfo.Encoding encoding) {
            return switch (encoding) {
                case ASCII -> "single byte ASCII, such as latin_1";
                case EBCDIC ->
                        "EBCDIC. Which code page cannot be told from the digits alone, "
                                + "so try cp500, then cp037";
                case UNKNOWN -> "could not tell";
            };
        }

        /**
         * The subcommand that ran, where it reads a file.
         *
         * <p>Asking the command itself beats looking its options up by name: a command that spells
         * an option differently, or does not have it, is a compile error rather than a report that
         * quietly says the wrong thing.
         */
        private static Optional<FileCommand> fileCommand(CommandLine.ParseResult parseResult) {
            if (!parseResult.hasSubcommand()) {
                return Optional.empty();
            }
            Object command = parseResult.subcommand().commandSpec().userObject();
            return command instanceof FileCommand reader ? Optional.of(reader) : Optional.empty();
        }
    }
}
