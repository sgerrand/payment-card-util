package com.sgerrand.paymentcardutil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sgerrand.paymentcardutil.vectors.Vectors;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Checks the hex dump against the one cardutil prints. */
class HexDumpParityTest {

    @TestFactory
    @DisplayName("dumps match cardutil line for line")
    Stream<DynamicTest> matchesCardutil() {
        return Vectors.tests(
                "hexdump",
                testCase ->
                        assertEquals(
                                testCase.get("dump").asText(),
                                HexDump.format(
                                        Vectors.hex(testCase.get("data_hex").asText()),
                                        Vectors.charset(testCase.get("encoding").asText()))));
    }

    @Test
    void stopsEarlyAndSaysHowMuchWasLeftOut() {
        byte[] data = new byte[100];
        String dump = HexDump.format(data, StandardCharsets.ISO_8859_1, 32);

        assertEquals(3, dump.lines().count(), "two lines of bytes and a note");
        assertTrue(dump.endsWith("... 68 more bytes"), dump);
    }

    @Test
    void saysNothingWhenThereIsNothingToSay() {
        assertEquals("", HexDump.format(new byte[0]));
    }

    @Test
    void aLimitBeyondTheDataChangesNothing() {
        byte[] data = "a short record".getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(HexDump.format(data), HexDump.format(data, StandardCharsets.ISO_8859_1, 1000));
    }
}
