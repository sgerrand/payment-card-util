package com.sgerrand.paymentcardutil.ipm;

import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.iso8583.Bitmap;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Options;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * What can be worked out about an IPM file by looking at the start of it.
 *
 * <p>Useful before reading a file whose blocking and character set nobody
 * recorded:
 *
 * <pre>{@code
 * IpmInfo info = IpmInfo.inspect(Files.newInputStream(path));
 * if (info.valid()) {
 *     Iso8583Options options = Iso8583Options.defaults()
 *             .withCharset(info.charset().orElseThrow());
 *     try (InputStream in = Files.newInputStream(path);
 *          IpmReader reader = IpmReader.open(in, options, info.blocked())) {
 *         ...
 *     }
 * }
 * }</pre>
 *
 * @param valid    whether this looks like an IPM file at all
 * @param reason   why not, when it does not
 * @param blocked  whether the file is in 1014 byte blocks
 * @param encoding the kind of character set the file appears to use
 */
public record IpmInfo(boolean valid, String reason, boolean blocked, Encoding encoding) {

    /**
     * The kind of character set a file appears to use.
     *
     * <p>This is worked out from the message type indicator, which is four
     * digits. That is enough to tell an ASCII compatible file from an EBCDIC
     * one, and no more: the digits sit in the same place in every EBCDIC code
     * page, so cp500 and cp037 cannot be told apart this way. Where the
     * difference matters, only the file's own paperwork can settle it.
     */
    public enum Encoding {

        /** ASCII compatible, such as Latin-1. */
        ASCII(StandardCharsets.ISO_8859_1),

        /** One of the EBCDIC code pages, but which one cannot be told from here. */
        EBCDIC(Charset.forName("IBM037")),

        /** Neither, so probably not an IPM file. */
        UNKNOWN(null);

        private final Charset representative;

        Encoding(Charset representative) {
            this.representative = representative;
        }

        /**
         * A character set of this kind, to read the file with.
         *
         * <p>For {@link #EBCDIC} this is a starting point rather than a finding.
         * If the file turns out to be a different code page, digits and plain
         * letters still read correctly; punctuation is where they differ.
         */
        public Optional<Charset> charset() {
            return Optional.ofNullable(representative);
        }
    }

    /** How much of the file is read to work this out. */
    public static final int SAMPLE_SIZE = 2500;

    /** Shortest run of bytes that could be a record length, message type and bitmap. */
    private static final int MINIMUM_SAMPLE = 24;

    /**
     * A character set to read the file with, if one could be worked out.
     *
     * @see Encoding#charset()
     */
    public Optional<Charset> charset() {
        return encoding.charset();
    }

    private static IpmInfo invalid(String reason) {
        return new IpmInfo(false, reason, false, Encoding.UNKNOWN);
    }

    /** Reads the start of a stream and works out what it holds. */
    public static IpmInfo inspect(InputStream in) throws IOException {
        return inspect(in.readNBytes(SAMPLE_SIZE));
    }

    /** Works out what a file holds from the start of it. */
    public static IpmInfo inspect(byte[] sample) {
        return inspect(sample, IsoConfig.defaults());
    }

    /**
     * Works out what a file holds from the start of it, checking the bitmap
     * against a given layout.
     */
    public static IpmInfo inspect(byte[] sample, IsoConfig config) {
        if (sample.length < MINIMUM_SAMPLE) {
            return invalid("File is too short to be an IPM file");
        }

        long recordLength = Vbs.recordLength(sample, 0);
        if (recordLength > config.maxVbsRecordLength()) {
            return invalid("First record says it is " + recordLength + " bytes, past the "
                    + config.maxVbsRecordLength() + " byte limit, which usually means the file is damaged");
        }

        String bitmapProblem = checkBitmap(java.util.Arrays.copyOfRange(sample, 8, 24), config);
        if (bitmapProblem != null) {
            return invalid(bitmapProblem);
        }

        return new IpmInfo(true, null, looksBlocked(sample), detectEncoding(sample));
    }

    /**
     * Whether every data element the first bitmap claims is one the layout knows
     * about.
     *
     * @return the problem, or {@code null} if the bitmap looks sound
     */
    private static String checkBitmap(byte[] bitmapBytes, IsoConfig config) {
        Bitmap bitmap = Bitmap.of(bitmapBytes);
        for (int bit = 2; bit <= 128; bit++) {
            if (bitmap.isSet(bit) && config.field(bit).isEmpty()) {
                return "Bitmap claims DE" + bit + ", which this layout does not use";
            }
        }
        return null;
    }

    /**
     * Whether the file is in 1014 byte blocks, told by the filler bytes on the
     * end of each block.
     *
     * <p>cardutil only reports blocking for files that are exactly one or two
     * blocks long, so it answers no for almost every real file. This checks the
     * blocks in the sample instead, whatever the file's total size.
     */
    private static boolean looksBlocked(byte[] sample) {
        int blocksChecked = 0;
        for (int end = Blocking.BLOCK_SIZE; end <= sample.length; end += Blocking.BLOCK_SIZE) {
            if (sample[end - 2] != Blocking.PAD || sample[end - 1] != Blocking.PAD) {
                return false;
            }
            blocksChecked++;
        }
        return blocksChecked > 0;
    }

    /**
     * Works out the kind of character set from the message type indicator, which
     * is always four digits. Whichever reading gives four digits is the one the
     * file uses.
     */
    private static Encoding detectEncoding(byte[] sample) {
        if (isAllDigits(new String(sample, 4, 4, Iso8583Options.DEFAULT_CHARSET))) {
            return Encoding.ASCII;
        }
        if (isAllDigits(new String(sample, 4, 4, Iso8583Options.EBCDIC_CP037))) {
            return Encoding.EBCDIC;
        }
        return Encoding.UNKNOWN;
    }

    private static boolean isAllDigits(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
