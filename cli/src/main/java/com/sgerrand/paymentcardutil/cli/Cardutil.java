package com.sgerrand.paymentcardutil.cli;

import com.sgerrand.paymentcardutil.PaymentCardException;
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
     * Turns a data problem into a short message and a failure code, rather than
     * a stack trace nobody asked for. {@code --debug} brings the trace back.
     */
    static final class ErrorHandler implements CommandLine.IExecutionExceptionHandler {

        @Override
        public int handleExecutionException(Exception exception, CommandLine command,
                                            CommandLine.ParseResult parseResult) {
            boolean debug = parseResult.hasSubcommand()
                    && parseResult.subcommand().matchedOptionValue("--debug", false);

            command.getErr().println("Processing stopped: " + exception.getMessage());
            if (exception instanceof PaymentCardException problem) {
                problem.recordNumber().ifPresent(number ->
                        command.getErr().println("The trouble is in record " + number + "."));
                problem.contextHex().ifPresent(hex ->
                        command.getErr().println("Bytes at that point: " + hex));
            }
            if (debug) {
                exception.printStackTrace(command.getErr());
            } else {
                command.getErr().println("Run again with --debug for the full details.");
            }
            return CommandLine.ExitCode.SOFTWARE;
        }
    }
}
