package com.sgerrand.paymentcardutil.iso8583;

import com.sgerrand.paymentcardutil.config.IsoConfig;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Settings for reading and writing ISO 8583 messages.
 *
 * @param charset how text in the message is encoded. IPM files from a mainframe are usually EBCDIC:
 *     {@link #EBCDIC_CP500} or {@link #EBCDIC_CP037}
 * @param hexBitmap {@code true} if the bitmap is written as 32 hex characters rather than 16 raw
 *     bytes
 * @param config the message layout
 */
public record Iso8583Options(Charset charset, boolean hexBitmap, IsoConfig config) {

    /** The default character set, matching cardutil's {@code latin_1}. */
    public static final Charset DEFAULT_CHARSET = StandardCharsets.ISO_8859_1;

    /** EBCDIC as used by many Mastercard files. cardutil calls this {@code cp500}. */
    public static final Charset EBCDIC_CP500 = Charset.forName("IBM500");

    /** EBCDIC as used by IPM parameter files. cardutil calls this {@code cp037}. */
    public static final Charset EBCDIC_CP037 = Charset.forName("IBM037");

    private static final Iso8583Options DEFAULTS =
            new Iso8583Options(DEFAULT_CHARSET, false, IsoConfig.defaults());

    public Iso8583Options {
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(config, "config");
    }

    /** Latin-1, a binary bitmap and the built in Mastercard IPM layout. */
    public static Iso8583Options defaults() {
        return DEFAULTS;
    }

    public Iso8583Options withCharset(Charset charset) {
        return new Iso8583Options(charset, hexBitmap, config);
    }

    public Iso8583Options withHexBitmap(boolean hexBitmap) {
        return new Iso8583Options(charset, hexBitmap, config);
    }

    public Iso8583Options withConfig(IsoConfig config) {
        return new Iso8583Options(charset, hexBitmap, config);
    }
}
