package com.sgerrand.paymentcardutil.cli;

import com.sgerrand.paymentcardutil.iso8583.Iso8583Options;
import picocli.CommandLine.Option;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Path;

/**
 * Options every tool takes.
 */
class CommonOptions {

    @Option(names = "--in-encoding",
            description = "Character set of the input file. Default: ${DEFAULT-VALUE}. "
                    + "Files from a mainframe are usually cp500.")
    String inEncoding = "latin_1";

    @Option(names = "--out-encoding",
            description = "Character set of the output file. Default: ${DEFAULT-VALUE}.")
    String outEncoding = "latin_1";

    @Option(names = "--no1014blocking",
            description = "The file is not in 1014 byte blocks.")
    boolean no1014blocking;

    @Option(names = "--debug", description = "Print the full stack trace when something goes wrong.")
    boolean debug;

    /** Whether the file is blocked. */
    boolean blocked() {
        return !no1014blocking;
    }

    Charset inCharset() {
        return charset(inEncoding);
    }

    Charset outCharset() {
        return charset(outEncoding);
    }

    /**
     * Where a tool writes to: what was asked for, or the input file with a
     * suffix on the end.
     */
    static Path outputPath(Path inFile, Path outFile, String suffix) {
        return outFile != null ? outFile : Path.of(inFile + suffix);
    }

    /**
     * Maps a character set name onto a Java charset, understanding the Python
     * codec names cardutil's documentation uses.
     */
    static Charset charset(String name) {
        try {
            return switch (name.toLowerCase(java.util.Locale.ROOT)) {
                case "latin_1", "latin-1", "latin1", "iso8859-1" -> Iso8583Options.DEFAULT_CHARSET;
                case "cp500" -> Iso8583Options.EBCDIC_CP500;
                case "cp037" -> Iso8583Options.EBCDIC_CP037;
                default -> Charset.forName(name);
            };
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            throw new IllegalArgumentException("Unknown character set: " + name, e);
        }
    }
}
