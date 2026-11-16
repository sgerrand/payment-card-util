package com.sgerrand.paymentcardutil.iso8583;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.config.PdsSubfield;
import com.sgerrand.paymentcardutil.config.ValueType;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Splitting private data subelements into parts.
 *
 * <p>The parity vectors cover the built in layout. These cover what they cannot: a layout of your
 * own, number conversion, and parts that are changed before writing.
 */
class PdsSubfieldTest {

    private static final String FILE_ID = "002" + "261116" + "01234567890" + "00001";

    private static Iso8583Message roundTrip(Iso8583Message message, Iso8583Options options) {
        return Iso8583.parse(Iso8583.serialize(message, options), options);
    }

    private static Iso8583Message header() {
        return Iso8583Message.builder().mti("1644").de(24, "697").pds(105, FILE_ID).build();
    }

    @Test
    void theBuiltInLayoutSplitsTheFileId() {
        Iso8583Message parsed = roundTrip(header(), Iso8583Options.defaults());

        assertEquals(Optional.of("002"), parsed.pdsSubfield(105, "1"));
        assertEquals(Optional.of("261116"), parsed.pdsSubfield(105, "2"));
        assertEquals(Optional.of("01234567890"), parsed.pdsSubfield(105, "3"));
        assertEquals(Optional.of("00001"), parsed.pdsSubfield(105, "4"));
        assertEquals(Optional.of(FILE_ID), parsed.pds(105));
    }

    @Test
    void aMessageReadWithPartsWritesBackUnchanged() {
        byte[] raw = Iso8583.serialize(header());
        Iso8583Message parsed = Iso8583.parse(raw);

        assertTrue(parsed.has("PDS0105_SF1"));
        assertArrayEquals(raw, Iso8583.serialize(parsed));
    }

    @Test
    void changingAPartDoesNotChangeWhatIsWritten() {
        // Parts are read only. The subelement is what goes in the file, so an
        // edit has to be made there.
        Iso8583Message parsed = Iso8583.parse(Iso8583.serialize(header()));
        Iso8583Message edited =
                parsed.toBuilder().put(Iso8583Message.pdsSubfieldKey(105, "1"), "999").build();

        assertEquals(
                Optional.of("002"), Iso8583.parse(Iso8583.serialize(edited)).pdsSubfield(105, "1"));
    }

    @Test
    void aLayoutOfYourOwnDecidesWhatIsSplit() {
        Map<String, PdsSubfield> parts = new LinkedHashMap<>();
        parts.put("A", PdsSubfield.of("First two", 0, 2));
        IsoConfig config =
                IsoConfig.defaults().toBuilder()
                        .pdsSubfields(Map.of())
                        .pdsSubfields("0158", parts)
                        .build();
        Iso8583Options options = Iso8583Options.defaults().withConfig(config);

        Iso8583Message parsed =
                roundTrip(header().toBuilder().pds(158, "XY12345678").build(), options);

        assertEquals(Optional.of("XY"), parsed.pdsSubfield(158, "A"));
        assertFalse(parsed.has("PDS0105_SF1"));
    }

    @Test
    void numbersAreConvertedAndTextThatIsNotOneIsKept() {
        Map<String, PdsSubfield> parts = new LinkedHashMap<>();
        parts.put("1", new PdsSubfield("Count", 0, 4, ValueType.LONG));
        parts.put("2", new PdsSubfield("Amount", 4, 5, ValueType.DECIMAL));
        parts.put("3", new PdsSubfield("Not a count", 9, 3, ValueType.LONG));
        IsoConfig config = IsoConfig.defaults().toBuilder().pdsSubfields("0158", parts).build();
        Iso8583Options options = Iso8583Options.defaults().withConfig(config);

        Iso8583Message parsed =
                roundTrip(
                        Iso8583Message.builder().mti("1240").pds(158, "0042 12.5ABC").build(),
                        options);

        assertEquals(Optional.of(42L), parsed.value("PDS0158_SF1"));
        assertEquals(Optional.of(new BigDecimal("12.5")), parsed.value("PDS0158_SF2"));
        // cardutil keeps the text rather than failing the message.
        assertEquals(Optional.of("ABC"), parsed.value("PDS0158_SF3"));
    }

    @Test
    void aPartRunningPastTheEndIsLeftOut() {
        Iso8583Message parsed =
                roundTrip(
                        Iso8583Message.builder().mti("1644").pds(105, "0022611").build(),
                        Iso8583Options.defaults());

        assertEquals(Optional.of("002"), parsed.pdsSubfield(105, "1"));
        assertFalse(parsed.has("PDS0105_SF2"));
    }

    @Test
    void cardutilsTypeNamesAreReadTheWayItReadsThem() {
        assertEquals(ValueType.LONG, PdsSubfield.valueTypeFor("int"));
        assertEquals(ValueType.DECIMAL, PdsSubfield.valueTypeFor("decimal"));
        assertEquals(ValueType.TEXT, PdsSubfield.valueTypeFor("string"));
        assertEquals(ValueType.TEXT, PdsSubfield.valueTypeFor("long"));
        assertEquals(ValueType.TEXT, PdsSubfield.valueTypeFor(null));
    }

    @Test
    void onlyPartKeysAreSkippedWhenPacking() {
        assertTrue(PdsCodec.isSubfieldKey("PDS0105_SF1"));
        assertTrue(PdsCodec.isSubfieldKey("PDS0105_SFX"));
        assertFalse(PdsCodec.isSubfieldKey("PDS0105"));
        assertFalse(PdsCodec.isSubfieldKey("PDS_SF1"));
        assertFalse(PdsCodec.isSubfieldKey("PDSAB_SF1"));
    }

    @Test
    void aTagIsFourCharacters() {
        IsoConfig.Builder builder = IsoConfig.builder();
        Map<String, PdsSubfield> parts = Map.of("1", PdsSubfield.of("x", 0, 1));

        assertThrows(IllegalArgumentException.class, () -> builder.pdsSubfields("105", parts));
    }
}
