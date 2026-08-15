package com.sgerrand.paymentcardutil.iso8583;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the value semantics of a message, which chip data makes awkward: DE 55
 * is held as a {@code byte[]}, and arrays compare by identity.
 */
class Iso8583MessageTest {

    private static final String ICC_HEX = "9f0206000000001000";

    private static Iso8583Message withChipData(String iccHex) {
        return Iso8583Message.builder()
                .mti("1240")
                .de(2, "4444555566667777")
                .de(55, HexFormat.of().parseHex(iccHex))
                .build();
    }

    @Test
    void messagesWithTheSameChipDataAreEqual() {
        assertEquals(withChipData(ICC_HEX), withChipData(ICC_HEX));
    }

    @Test
    void messagesWithTheSameChipDataHashAlike() {
        assertEquals(withChipData(ICC_HEX).hashCode(), withChipData(ICC_HEX).hashCode());
    }

    @Test
    void messagesWithDifferentChipDataAreNotEqual() {
        assertNotEquals(withChipData(ICC_HEX), withChipData("9f0206000000002000"));
    }

    @Test
    void parsingTheSameBytesTwiceGivesEqualMessages() {
        byte[] wire = Iso8583.serialize(withChipData(ICC_HEX));
        assertEquals(Iso8583.parse(wire), Iso8583.parse(wire));
    }

    @Test
    void aMessageWithChipDataWorksAsASetMember() {
        byte[] wire = Iso8583.serialize(withChipData(ICC_HEX));
        Set<Iso8583Message> messages = Set.of(Iso8583.parse(wire));
        assertTrue(messages.contains(Iso8583.parse(wire)));
    }

    @Test
    void messagesWithoutChipDataStillCompareByValue() {
        Iso8583Message one = Iso8583Message.builder().mti("1240").de(2, "4444555566667777").build();
        Iso8583Message two = Iso8583Message.builder().mti("1240").de(2, "4444555566667777").build();
        assertEquals(one, two);
        assertEquals(one.hashCode(), two.hashCode());
        assertNotEquals(one, Iso8583Message.builder().mti("1240").de(2, "4444555566667778").build());
    }

    @Test
    void messagesWithDifferentKeysAreNotEqual() {
        Iso8583Message one = Iso8583Message.builder().mti("1240").de(2, "4444555566667777").build();
        assertNotEquals(one, one.toBuilder().de(4, 100L).build());
        assertNotEquals(one.toBuilder().de(4, 100L).build(), one);
    }

    @Test
    void changingTheArrayHandedToTheBuilderLeavesTheMessageAlone() {
        byte[] chipData = HexFormat.of().parseHex(ICC_HEX);
        Iso8583Message message = Iso8583Message.builder().mti("1240").de(55, chipData).build();

        chipData[0] = 0x00;

        assertEquals(ICC_HEX, HexFormat.of().formatHex((byte[]) message.values().get("DE55")));
    }

    @Test
    void changingTheArrayFromValuesLeavesTheMessageAlone() {
        Iso8583Message message = withChipData(ICC_HEX);

        ((byte[]) message.values().get("DE55"))[0] = 0x00;

        assertEquals(ICC_HEX, HexFormat.of().formatHex((byte[]) message.values().get("DE55")));
    }

    @Test
    void changingTheArrayFromValueLeavesTheMessageAlone() {
        Iso8583Message message = withChipData(ICC_HEX);

        ((byte[]) message.value("DE55").orElseThrow())[0] = 0x00;

        assertEquals(ICC_HEX, HexFormat.of().formatHex((byte[]) message.values().get("DE55")));
    }

    @Test
    void valuesStaysUnmodifiableWhenThereIsChipDataToCopy() {
        Map<String, Object> values = withChipData(ICC_HEX).values();
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class, () -> values.put("DE3", "000000"));
    }
}
