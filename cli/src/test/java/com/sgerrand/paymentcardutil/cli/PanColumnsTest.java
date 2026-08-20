package com.sgerrand.paymentcardutil.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sgerrand.paymentcardutil.config.FieldConfig;
import com.sgerrand.paymentcardutil.config.FieldProcessors;
import com.sgerrand.paymentcardutil.config.FieldType;
import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.config.ValueType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Which columns {@code mci-ipm-to-csv} masks.
 *
 * <p>The point of these is that the choice follows the layout's field processor, not the element's
 * human readable name.
 */
class PanColumnsTest {

    private static FieldConfig field(String name, String processor) {
        return new FieldConfig(name, FieldType.LLVAR, 19, ValueType.TEXT, null, processor, null);
    }

    @Test
    void aMarkedElementIsMaskedWhateverItIsCalled() {
        IsoConfig config =
                IsoConfig.defaults().toBuilder()
                        .field(2, field("Primary Account Number", FieldProcessors.PAN))
                        .build();

        assertEquals(List.of("DE2"), IpmToCsv.panColumns(config));
    }

    @Test
    void anElementHoldingOnlyAPrefixIsMarkedToo() {
        IsoConfig config =
                IsoConfig.defaults().toBuilder()
                        .field(2, field("Numero de carte", FieldProcessors.PAN_PREFIX))
                        .build();

        assertEquals(List.of("DE2"), IpmToCsv.panColumns(config));
    }

    @Test
    void severalMarkedElementsComeBackInFieldOrder() {
        IsoConfig config =
                IsoConfig.defaults().toBuilder()
                        .field(34, field("Card number, extended", FieldProcessors.PAN))
                        .field(2, field("Card number", FieldProcessors.PAN_PREFIX))
                        .field(35, field("Track 2 data", null))
                        .build();

        assertEquals(List.of("DE2", "DE34"), IpmToCsv.panColumns(config));
    }

    @Test
    void anElementNamedPanIsMaskedWithNoProcessorToGoOn() {
        // What the built-in Mastercard layout does: it comes from cardutil's
        // config, and cardutil marks no element, since it does not mask.
        assertEquals(List.of("DE2"), IpmToCsv.panColumns(IsoConfig.defaults()));
    }

    @Test
    void bothSignalsCount() {
        IsoConfig config =
                IsoConfig.defaults().toBuilder()
                        .field(34, field("Card number, extended", FieldProcessors.PAN))
                        .build();

        // DE34 is marked outright; DE2 is still called PAN in the built-in
        // layout. Marking one element must not unmask the other.
        assertEquals(List.of("DE2", "DE34"), IpmToCsv.panColumns(config));
    }
}
