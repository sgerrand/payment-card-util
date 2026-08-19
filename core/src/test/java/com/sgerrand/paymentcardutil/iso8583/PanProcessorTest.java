package com.sgerrand.paymentcardutil.iso8583;

import com.sgerrand.paymentcardutil.config.FieldConfig;
import com.sgerrand.paymentcardutil.config.FieldProcessor;
import com.sgerrand.paymentcardutil.config.FieldType;
import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.config.ValueType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The {@code PAN} and {@code PAN-PREFIX} field processors mark a card number;
 * they do not change it.
 *
 * <p>cardutil masks and truncates while parsing, which loses the file's own
 * bytes: the message cannot be written back unchanged, and a caller who needs
 * the real number has no way to ask for it. This is one of the divergences
 * listed in the README.
 */
class PanProcessorTest {

    private static final String CARD_NUMBER = "4444555566667777";

    private static IsoConfig config(FieldProcessor processor) {
        return IsoConfig.defaults().toBuilder()
                .field(2, new FieldConfig(
                        "PAN", FieldType.LLVAR, 19, ValueType.TEXT, null, processor, null))
                .build();
    }

    private static Iso8583Options options(FieldProcessor processor) {
        return Iso8583Options.defaults().withConfig(config(processor));
    }

    private static Iso8583Message message() {
        return Iso8583Message.builder().mti("1240").de(2, CARD_NUMBER).build();
    }

    @Test
    void aMarkedFieldIsReadAsTheFileWroteIt() {
        Iso8583Options options = options(FieldProcessor.PAN);
        byte[] raw = Iso8583.serialize(message(), options);

        assertEquals(CARD_NUMBER, Iso8583.parse(raw, options).text("DE2").orElseThrow());
    }

    @Test
    void aFieldMarkedAsAPrefixIsNotCutShortEither() {
        Iso8583Options options = options(FieldProcessor.PAN_PREFIX);
        byte[] raw = Iso8583.serialize(message(), options);

        assertEquals(CARD_NUMBER, Iso8583.parse(raw, options).text("DE2").orElseThrow());
    }

    @Test
    void aMarkedFieldWritesBackToTheSameBytes() {
        Iso8583Options options = options(FieldProcessor.PAN);
        byte[] raw = Iso8583.serialize(message(), options);

        assertArrayEquals(raw, Iso8583.serialize(Iso8583.parse(raw, options), options));
    }
}
