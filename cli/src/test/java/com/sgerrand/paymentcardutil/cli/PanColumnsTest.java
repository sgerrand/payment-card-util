package com.sgerrand.paymentcardutil.cli;

import com.sgerrand.paymentcardutil.config.FieldConfig;
import com.sgerrand.paymentcardutil.config.FieldProcessor;
import com.sgerrand.paymentcardutil.config.FieldType;
import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.config.ValueType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which columns {@code mci-ipm-to-csv} masks.
 *
 * <p>The point of these is that the choice follows the layout's field
 * processor, not the element's human readable name.
 */
class PanColumnsTest {

    private static FieldConfig field(String name, FieldProcessor processor) {
        return new FieldConfig(name, FieldType.LLVAR, 19, ValueType.TEXT, null, processor, null);
    }

    @Test
    void aMarkedElementIsMaskedWhateverItIsCalled() {
        IsoConfig config = IsoConfig.defaults().toBuilder()
                .field(2, field("Primary Account Number", FieldProcessor.PAN))
                .build();

        assertEquals(List.of("DE2"), IpmToCsv.panColumns(config));
    }

    @Test
    void anElementHoldingOnlyAPrefixIsMarkedToo() {
        IsoConfig config = IsoConfig.defaults().toBuilder()
                .field(2, field("Numero de carte", FieldProcessor.PAN_PREFIX))
                .build();

        assertEquals(List.of("DE2"), IpmToCsv.panColumns(config));
    }

    @Test
    void severalMarkedElementsComeBackInFieldOrder() {
        IsoConfig config = IsoConfig.defaults().toBuilder()
                .field(34, field("Card number, extended", FieldProcessor.PAN))
                .field(2, field("Card number", FieldProcessor.PAN_PREFIX))
                .build();

        assertEquals(List.of("DE2", "DE34"), IpmToCsv.panColumns(config));
    }

    @Test
    void aLayoutMarkingNothingFallsBackToTheName() {
        // What the built-in Mastercard layout does: it comes from cardutil's
        // config, and cardutil marks no element, since it does not mask.
        assertEquals(List.of("DE2"), IpmToCsv.panColumns(IsoConfig.defaults()));
    }

    @Test
    void theNameIsIgnoredOnceTheLayoutMarksSomething() {
        IsoConfig config = IsoConfig.defaults().toBuilder()
                .field(34, field("Card number, extended", FieldProcessor.PAN))
                .build();

        // DE2 is still called PAN in the built-in layout, but the layout now
        // says outright which element it means, so the name stops deciding.
        assertEquals(List.of("DE34"), IpmToCsv.panColumns(config));
    }
}
