package com.sgerrand.paymentcardutil.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sgerrand.paymentcardutil.config.FieldConfig;
import com.sgerrand.paymentcardutil.config.FieldProcessors;
import com.sgerrand.paymentcardutil.config.FieldType;
import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.config.PdsSubfield;
import com.sgerrand.paymentcardutil.config.ValueType;
import java.util.List;
import java.util.Map;
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

        assertEquals(List.of("DE2", "PDS0001_SF2"), IpmToCsv.panColumns(config));
    }

    @Test
    void anElementHoldingOnlyAPrefixIsMarkedToo() {
        IsoConfig config =
                IsoConfig.defaults().toBuilder()
                        .field(2, field("Numero de carte", FieldProcessors.PAN_PREFIX))
                        .build();

        assertEquals(List.of("DE2", "PDS0001_SF2"), IpmToCsv.panColumns(config));
    }

    @Test
    void severalMarkedElementsComeBackInFieldOrder() {
        IsoConfig config =
                IsoConfig.defaults().toBuilder()
                        .field(34, field("Card number, extended", FieldProcessors.PAN))
                        .field(2, field("Card number", FieldProcessors.PAN_PREFIX))
                        .field(35, field("Track 2 data", null))
                        .build();

        assertEquals(List.of("DE2", "DE34", "PDS0001_SF2"), IpmToCsv.panColumns(config));
    }

    @Test
    void anElementNamedPanIsMaskedWithNoProcessorToGoOn() {
        // What the built-in Mastercard layout does: it comes from cardutil's
        // config, and cardutil marks no element, since it does not mask.
        // PDS 0001 holds a card number too, in a part cardutil calls the
        // account number.
        assertEquals(List.of("DE2", "PDS0001_SF2"), IpmToCsv.panColumns(IsoConfig.defaults()));
    }

    @Test
    void aSubfieldIsMaskedByItsName() {
        IsoConfig config =
                IsoConfig.builder()
                        .pdsSubfields(
                                "0158",
                                Map.of(
                                        "1", PdsSubfield.of("pan", 0, 16),
                                        "2", PdsSubfield.of("Account number type", 16, 2)))
                        .build();

        assertEquals(List.of("PDS0158_SF1"), IpmToCsv.panColumns(config));
    }

    @Test
    void aCardNumberReadAsANumberIsMaskedToo() {
        // A layout may read a card number subfield as an int, which arrives
        // as a Long. It must not slip past the masking for not being text.
        Map<String, Object> values = Map.of("PDS0158_SF1", 5555555555554444L, "DE4", 100L);

        Map<String, ?> masked = IpmToCsv.mask(values, List.of("PDS0158_SF1"));

        assertEquals("555555******4444", masked.get("PDS0158_SF1"));
        assertEquals(100L, masked.get("DE4"));
    }

    @Test
    void bothSignalsCount() {
        IsoConfig config =
                IsoConfig.defaults().toBuilder()
                        .field(34, field("Card number, extended", FieldProcessors.PAN))
                        .build();

        // DE34 is marked outright; DE2 is still called PAN in the built-in
        // layout. Marking one element must not unmask the other.
        assertEquals(List.of("DE2", "DE34", "PDS0001_SF2"), IpmToCsv.panColumns(config));
    }
}
