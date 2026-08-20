package com.sgerrand.paymentcardutil.iso8583;

import com.sgerrand.paymentcardutil.config.FieldConfig;
import com.sgerrand.paymentcardutil.config.FieldProcessors;
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
    static final FieldCodec NO_EXTRAS = (bit, raw, text, field) -> Map.of();

    private static final Map<String, FieldCodec> BUILT_IN =
            Map.of(
                    // PAN and PAN-PREFIX say the field holds a card number and
                    // nothing more. Masking belongs to whatever shows the data,
                    // not to reading it.
                    FieldProcessors.PAN,
                    NO_EXTRAS,
                    FieldProcessors.PAN_PREFIX,
                    NO_EXTRAS,
                    FieldProcessors.PDS,
                    (bit, raw, text, field) -> PdsCodec.unpack(text),
                    FieldProcessors.DE43,
                    (bit, raw, text, field) -> De43Codec.unpack(text, field.processorConfig()),
                    FieldProcessors.ICC,
                    new Icc());

    private FieldCodecs() {}

    /**
     * The codec for a processor name.
     *
     * <p>A name nothing here knows leaves the field as it stands. cardutil does the same: an
     * unrecognised name falls through its own list of processors rather than stopping the read.
     */
    static FieldCodec of(String processor) {
        if (processor == null) {
            return NO_EXTRAS;
        }
        return BUILT_IN.getOrDefault(processor, NO_EXTRAS);
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
