package com.sgerrand.paymentcardutil.iso8583;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sgerrand.paymentcardutil.config.FieldConfig;
import com.sgerrand.paymentcardutil.config.FieldProcessor;
import com.sgerrand.paymentcardutil.config.FieldType;
import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.config.ValueType;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Reading a layout that packs something the reader has never seen inside a field.
 *
 * <p>The reader has no list of field processors of its own. It asks the settings which codec a
 * field has, so a layout only needs a codec and a config naming it.
 */
class FieldCodecTest {

    /** Splits a field into halves, the way a layout of somebody else's might. */
    private static final FieldCodec HALVES =
            (bit, raw, text, field) -> {
                Map<String, String> parts = new LinkedHashMap<>();
                parts.put("DE" + bit + "_LEFT", text.substring(0, text.length() / 2));
                parts.put("DE" + bit + "_RIGHT", text.substring(text.length() / 2));
                return parts;
            };

    private static Iso8583Options optionsWith(FieldProcessor processor, FieldCodec codec) {
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
                                        processor,
                                        null))
                        .build();
        return Iso8583Options.defaults().withConfig(config).withCodec(processor, codec);
    }

    private static Iso8583Message parsedWith(Iso8583Options options) {
        Iso8583Message message =
                Iso8583Message.builder().mti("1240").de(37, "REF000012345").build();
        return Iso8583.parse(Iso8583.serialize(message, options), options);
    }

    @Test
    void aCodecOfYourOwnPullsTheFieldApart() {
        Iso8583Message parsed = parsedWith(optionsWith(FieldProcessor.DE43, HALVES));

        assertEquals("REF000012345", parsed.text("DE37").orElseThrow(), "the field itself");
        assertEquals("REF000", parsed.text("DE37_LEFT").orElseThrow());
        assertEquals("012345", parsed.text("DE37_RIGHT").orElseThrow());
    }

    @Test
    void theBuiltInCodecIsUsedWhereNoneWasReplaced() {
        Iso8583Options options = optionsWith(FieldProcessor.DE43, HALVES);

        // DE 48 still carries private data the way the built in layout says.
        assertEquals(
                FieldProcessor.PDS,
                options.config().field(48).orElseThrow().processor(),
                "the layout is otherwise untouched");
        assertTrue(options.codec(FieldProcessor.PDS) != null, "and it still has its codec");
    }

    @Test
    void aCodecCanKeepTheFieldsBytesInsteadOfItsText() {
        FieldCodec binary =
                new FieldCodec() {
                    @Override
                    public Map<String, ?> unpack(
                            int bit, byte[] raw, String text, FieldConfig field) {
                        return Map.of("DE" + bit + "_HEX", HexFormat.of().formatHex(raw));
                    }

                    @Override
                    public boolean readsRawBytes() {
                        return true;
                    }
                };

        Iso8583Message parsed = parsedWith(optionsWith(FieldProcessor.DE43, binary));

        assertInstanceOf(byte[].class, parsed.value("DE37").orElseThrow(), "the field's own value");
        assertArrayEquals(
                "REF000012345".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1),
                (byte[]) parsed.value("DE37").orElseThrow());
        assertEquals("524546303030303132333435", parsed.text("DE37_HEX").orElseThrow());
    }
}
