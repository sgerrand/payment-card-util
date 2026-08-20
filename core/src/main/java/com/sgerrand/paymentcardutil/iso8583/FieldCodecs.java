package com.sgerrand.paymentcardutil.iso8583;

import com.sgerrand.paymentcardutil.config.FieldConfig;
import com.sgerrand.paymentcardutil.config.FieldProcessor;
import java.util.EnumMap;
import java.util.Map;

/**
 * The codecs for the field processors cardutil names.
 *
 * <p>This is the only place that knows which name means which codec. The reader itself works from
 * whatever {@link Iso8583Options#codec} hands back, so a caller can put their own codec in place of
 * any of these.
 */
final class FieldCodecs {

    /** For a field with nothing packed inside it. Its value is the whole of it. */
    private static final FieldCodec NO_EXTRAS = (bit, raw, text, field) -> Map.of();

    private static final Map<FieldProcessor, FieldCodec> BUILT_IN = builtIn();

    private FieldCodecs() {}

    /** The codec for a processor. */
    static FieldCodec of(FieldProcessor processor) {
        return BUILT_IN.get(processor);
    }

    private static Map<FieldProcessor, FieldCodec> builtIn() {
        Map<FieldProcessor, FieldCodec> codecs = new EnumMap<>(FieldProcessor.class);
        codecs.put(FieldProcessor.NONE, NO_EXTRAS);
        // PAN and PAN-PREFIX say the field holds a card number and nothing more.
        // Masking belongs to whatever shows the data, not to reading it.
        codecs.put(FieldProcessor.PAN, NO_EXTRAS);
        codecs.put(FieldProcessor.PAN_PREFIX, NO_EXTRAS);
        codecs.put(FieldProcessor.PDS, (bit, raw, text, field) -> PdsCodec.unpack(text));
        codecs.put(
                FieldProcessor.DE43,
                (bit, raw, text, field) -> De43Codec.unpack(text, field.processorConfig()));
        codecs.put(FieldProcessor.ICC, new Icc());
        return codecs;
    }

    /** Chip data, which is binary and so keeps its bytes as the field's value. */
    private static final class Icc implements FieldCodec {

        @Override
        public Map<String, ?> unpack(int bit, byte[] raw, String text, FieldConfig field) {
            return IccCodec.unpack(bit, raw, field.processorConfig());
        }

        @Override
        public boolean readsRawBytes() {
            return true;
        }
    }
}
