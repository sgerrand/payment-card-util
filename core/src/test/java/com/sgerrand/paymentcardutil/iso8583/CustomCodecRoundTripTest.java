package com.sgerrand.paymentcardutil.iso8583;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sgerrand.paymentcardutil.config.FieldConfig;
import com.sgerrand.paymentcardutil.config.FieldProcessors;
import com.sgerrand.paymentcardutil.config.FieldType;
import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.config.ValueType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A file read with a codec of your own writes back unchanged.
 *
 * <p>A codec only unpacks: there is no matching packer, so the parts it pulls out are along for the
 * ride and the element's own value is what gets written. That is what keeps a file round trip safe,
 * and it is the same way the built in DE 43 and chip data codecs behave.
 *
 * <p>Private data is the exception, and deliberately so: {@code PDSxxxx} values are packed back
 * into the elements set up to carry them, overwriting what was there. cardutil does that and only
 * that, which is why no other codec has a packing half.
 */
class CustomCodecRoundTripTest {

    private static final String PROCESSOR = "BRANCH-CODE";

    /** Splits an element into two named parts, the way a layout of somebody else's might. */
    private static final FieldCodec BRANCH_CODE =
            (bit, raw, text, field) -> {
                Map<String, String> parts = new LinkedHashMap<>();
                parts.put("DE" + bit + "_BRANCH", text.substring(0, 4));
                parts.put("DE" + bit + "_REST", text.substring(4));
                return parts;
            };

    private static Iso8583Options options() {
        IsoConfig config =
                IsoConfig.defaults().toBuilder()
                        .field(
                                37,
                                new FieldConfig(
                                        "Retrieval reference number",
                                        FieldType.FIXED,
                                        12,
                                        ValueType.TEXT,
                                        null,
                                        PROCESSOR,
                                        null))
                        .build();
        return Iso8583Options.defaults().withConfig(config).withCodec(PROCESSOR, BRANCH_CODE);
    }

    private static Iso8583Message message() {
        return Iso8583Message.builder()
                .mti("1240")
                .de(2, "4444555566667777")
                .de(37, "BR01ABCD2345")
                .build();
    }

    @Test
    void whatTheCodecPulledOutIsNotWrittenBackOut() {
        Iso8583Options options = options();
        byte[] written = Iso8583.serialize(message(), options);

        Iso8583Message parsed = Iso8583.parse(written, options);
        assertEquals("BR01", parsed.text("DE37_BRANCH").orElseThrow(), "the codec ran");

        assertArrayEquals(
                written,
                Iso8583.serialize(parsed, options),
                "a record read with a codec of your own writes back byte for byte");
    }

    @Test
    void theElementKeepsItsOwnValue() {
        Iso8583Options options = options();
        Iso8583Message parsed = Iso8583.parse(Iso8583.serialize(message(), options), options);

        assertEquals("BR01ABCD2345", parsed.text("DE37").orElseThrow());
    }

    @Test
    void theBuiltInAddressCodecBehavesTheSameWay() {
        // DE 43 is pulled apart into named parts and never packed back either.
        Iso8583Message message =
                Iso8583Message.builder()
                        .mti("1240")
                        .de(2, "4444555566667777")
                        .de(43, "SHOP NAME\\1 HIGH STREET\\SOMETOWN\\POSTCODE  NSWAUS")
                        .build();
        byte[] written = Iso8583.serialize(message);

        Iso8583Message parsed = Iso8583.parse(written);
        assertTrue(parsed.text("DE43_NAME").isPresent(), "the built in codec ran");
        assertArrayEquals(written, Iso8583.serialize(parsed));
    }

    @Test
    void privateDataIsTheOneThatIsPackedBack() {
        // The exception, and a documented parity trap: PDS values are packed
        // into the elements set up to carry them, replacing what was there.
        Iso8583Message message =
                Iso8583Message.builder().mti("1240").de(2, "4444555566667777").build();
        Iso8583Message withPds =
                Iso8583Message.builder()
                        .mti("1240")
                        .de(2, "4444555566667777")
                        .pds(158, "0000000000")
                        .build();

        int carrier = IsoConfig.defaults().bitsWithProcessor(FieldProcessors.PDS).get(0);
        Iso8583Message parsed = Iso8583.parse(Iso8583.serialize(withPds));

        assertTrue(
                parsed.text(Iso8583Message.deKey(carrier)).isPresent(),
                "the private data was packed into DE" + carrier);
        assertTrue(Iso8583.serialize(message).length < Iso8583.serialize(withPds).length);
    }
}
