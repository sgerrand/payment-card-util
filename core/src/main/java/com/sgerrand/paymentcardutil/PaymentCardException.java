package com.sgerrand.paymentcardutil;

import java.util.HexFormat;
import java.util.Optional;

/**
 * Thrown when data does not match the layout it is meant to follow.
 *
 * <p>These are unchecked. A malformed file is a data problem, not a problem
 * with the call, and wrapping every field read in a try block would make the
 * readers painful to use. Real I/O failures still surface as
 * {@link java.io.IOException}.
 *
 * <p>Where it helps, the exception carries the bytes that caused the trouble
 * and the record they came from, so a bad file can be tracked down without
 * re-reading it. The message itself stays plain: whoever catches this decides
 * how much of the context to show, and {@link HexDump} lays the bytes out
 * readably.
 */
public class PaymentCardException extends RuntimeException {

    private static final HexFormat HEX = HexFormat.of();

    private static final long serialVersionUID = 1L;

    /** How many bytes of context to show in {@link #contextHex()}. */
    private static final int CONTEXT_LIMIT = 64;

    private final byte[] binaryContext;
    private final Integer recordNumber;

    public PaymentCardException(String message) {
        this(message, null, null, null);
    }

    public PaymentCardException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    public PaymentCardException(String message, byte[] binaryContext, Integer recordNumber, Throwable cause) {
        super(message, cause);
        this.binaryContext = binaryContext == null ? null : binaryContext.clone();
        this.recordNumber = recordNumber;
    }

    /** The bytes that could not be read, if the thrower had them to hand. */
    public Optional<byte[]> binaryContext() {
        return Optional.ofNullable(binaryContext).map(byte[]::clone);
    }

    /** Which record in the file this came from, counting from 1. */
    public Optional<Integer> recordNumber() {
        return Optional.ofNullable(recordNumber);
    }

    /**
     * The first {@value #CONTEXT_LIMIT} bytes of context as hex, for a log line
     * that has room for only one.
     *
     * <p>For something readable, pass {@link #binaryContext()} to
     * {@link HexDump#format(byte[], java.nio.charset.Charset, int)} along with
     * the character set the file was being read in.
     */
    public Optional<String> contextHex() {
        return binaryContext()
                .map(bytes -> HEX.formatHex(bytes, 0, Math.min(bytes.length, CONTEXT_LIMIT)));
    }
}
