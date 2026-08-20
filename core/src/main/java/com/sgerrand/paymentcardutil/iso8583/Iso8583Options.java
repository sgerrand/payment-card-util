package com.sgerrand.paymentcardutil.iso8583;

import com.sgerrand.paymentcardutil.config.FieldProcessor;
import com.sgerrand.paymentcardutil.config.IsoConfig;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
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
 * @param codecs which codec pulls a field apart, by the processor the layout names. Anything left
 *     out keeps the built in codec, so a caller only has to name what they are replacing
 */
public record Iso8583Options(
        Charset charset,
        boolean hexBitmap,
        IsoConfig config,
        Map<FieldProcessor, FieldCodec> codecs) {

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
        // EnumMap's copy constructor cannot take an empty plain map, and an
        // empty map is the usual case: no codec replaced.
        Map<FieldProcessor, FieldCodec> copy = new EnumMap<>(FieldProcessor.class);
        copy.putAll(Objects.requireNonNull(codecs, "codecs"));
        codecs = Collections.unmodifiableMap(copy);
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
     * FieldCodec}, put it in place of the processor the config names, and the reader uses it. The
     * reader has no list of its own to add to.
     */
    public Iso8583Options withCodec(FieldProcessor processor, FieldCodec codec) {
        Map<FieldProcessor, FieldCodec> replaced = new EnumMap<>(FieldProcessor.class);
        replaced.putAll(codecs);
        replaced.put(
                Objects.requireNonNull(processor, "processor"),
                Objects.requireNonNull(codec, "codec"));
        return new Iso8583Options(charset, hexBitmap, config, replaced);
    }

    /** The codec that pulls a field with this processor apart. */
    public FieldCodec codec(FieldProcessor processor) {
        FieldCodec codec = codecs.get(processor);
        return codec != null ? codec : FieldCodecs.of(processor);
    }
}
