package com.sgerrand.paymentcardutil.iso8583;

import com.sgerrand.paymentcardutil.config.IsoConfig;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Settings for reading and writing ISO 8583 messages.
 *
 * @param charset how text in the message is encoded. IPM files from a mainframe are usually EBCDIC:
 *     {@link #EBCDIC_CP500} or {@link #EBCDIC_CP037}
 * @param hexBitmap {@code true} if the bitmap is written as 32 hex characters rather than 16 raw
 *     bytes
 * @param config the message layout
 * @param codecs which codec pulls a field apart, by the processor name the layout uses. Anything
 *     left out keeps the built in codec of that name, so a caller only has to name what they are
 *     adding or replacing
 */
public record Iso8583Options(
        Charset charset, boolean hexBitmap, IsoConfig config, Map<String, FieldCodec> codecs) {

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
        codecs = Map.copyOf(Objects.requireNonNull(codecs, "codecs"));
    }

    /** Settings with the built in codec for every field processor. */
    public Iso8583Options(Charset charset, boolean hexBitmap, IsoConfig config) {
        this(charset, hexBitmap, config, Map.of());
    }

    /** Latin-1, a binary bitmap and the built in Mastercard IPM layout. */
    public static Iso8583Options defaults() {
        return DEFAULTS;
    }

    public Iso8583Options withCharset(Charset charset) {
        return new Iso8583Options(charset, hexBitmap, config, codecs);
    }

    public Iso8583Options withHexBitmap(boolean hexBitmap) {
        return new Iso8583Options(charset, hexBitmap, config, codecs);
    }

    public Iso8583Options withConfig(IsoConfig config) {
        return new Iso8583Options(charset, hexBitmap, config, codecs);
    }

    /**
     * The same settings, reading a field processor with a codec of your own.
     *
     * <p>This is how a layout that packs something else inside an element is read: write a {@link
     * FieldCodec}, register it under the name the config gives that field, and the reader uses it.
     * The name is yours to choose — the reader has no list of its own to add to — so a config
     * saying {@code "field_processor": "BRANCH-CODE"} needs only a codec of that name.
     *
     * @param processor the processor name the layout uses, such as one of {@link
     *     com.sgerrand.paymentcardutil.config.FieldProcessors}
     */
    public Iso8583Options withCodec(String processor, FieldCodec codec) {
        Map<String, FieldCodec> replaced = new LinkedHashMap<>(codecs);
        replaced.put(
                Objects.requireNonNull(processor, "processor"),
                Objects.requireNonNull(codec, "codec"));
        return new Iso8583Options(charset, hexBitmap, config, replaced);
    }

    /**
     * The codec that pulls a field with this processor apart.
     *
     * <p>A name nobody has a codec for leaves the field as it stands, which is what cardutil does
     * with a processor it does not know.
     */
    public FieldCodec codec(String processor) {
        // Most fields name no processor at all, and an immutable map will not
        // even be asked about a null key.
        if (processor == null) {
            return FieldCodecs.NO_EXTRAS;
        }
        FieldCodec codec = codecs.get(processor);
        return codec != null ? codec : FieldCodecs.of(processor);
    }
}
