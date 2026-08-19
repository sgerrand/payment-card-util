package com.sgerrand.paymentcardutil.cli;

import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * A command that reads a file.
 *
 * <p>When one of them fails, the error handler has to say what the tool was working with: which
 * file, read in which character set, and whether the stack trace was asked for. Finding that out by
 * looking options up by name works only while every command spells them the same way, and says
 * nothing when one does not. This is the same thing asked of the command itself.
 */
interface FileCommand {

    /** The file being read. */
    Path inputFile();

    /** How the file is being read. */
    InputOptions inputOptions();

    /**
     * Whether the file should hold ISO 8583 messages, so that inspecting it says something useful.
     * A CSV or a parameter file has no bitmap to check.
     */
    default boolean readsIpmMessages() {
        return false;
    }

    /** The parts of a command's options that say how a file is being read. */
    interface InputOptions {

        /** The character set the file is being read in. */
        Charset inCharset();

        /** Whether the file is expected in 1014 byte blocks. */
        boolean blocked();

        /** Whether the full stack trace was asked for. */
        boolean debug();
    }
}
