package com.sgerrand.paymentcardutil.cli;

import picocli.CommandLine.Option;

import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * Options the two rewriting tools take.
 *
 * <p>They read a file and write it back out, so they name a character set and a
 * layout on each side rather than the single pair {@link CommonOptions} carries.
 * Their input default is cp500, since a file worth rewriting usually came off a
 * mainframe.
 */
class EncodeOptions {

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

    Charset inCharset() {
        return CommonOptions.charset(inEncoding);
    }

    Charset outCharset() {
        return CommonOptions.charset(outEncoding);
    }
}
