package com.sgerrand.paymentcardutil.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sgerrand.paymentcardutil.config.FieldProcessors;
import com.sgerrand.paymentcardutil.config.IsoConfig;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Reading a layout from a config file.
 *
 * <p>A config file says what is different, not what the whole layout is, so what it leaves out has
 * to survive.
 */
class ConfigFilesTest {

    private static IsoConfig parse(String json) throws IOException {
        return ConfigFiles.parse(json, IsoConfig.defaults());
    }

    @Test
    void namingOneElementLeavesTheRestAlone() throws IOException {
        IsoConfig config =
                parse(
                        """
                        {"bit_config": {"2": {"field_name": "Card number", "field_type": "LLVAR",
                                              "field_length": 19, "field_processor": "PAN"}}}
                        """);

        assertEquals("Card number", config.field(2).orElseThrow().name());
        assertEquals(FieldProcessors.PAN, config.field(2).orElseThrow().processor());
        assertEquals(
                IsoConfig.defaults().bitConfig().size(),
                config.bitConfig().size(),
                "the other elements stay");
        assertEquals(
                IsoConfig.defaults().field(4).orElseThrow(),
                config.field(4).orElseThrow(),
                "an element the file did not name is untouched");
    }

    @Test
    void namingOneParameterTableLeavesTheRestAlone() throws IOException {
        IsoConfig config =
                parse(
                        """
                        {"mci_parameter_tables": {"IP0040T1": {"gcms_product_id": {"start": 1, "end": 4}}}}
                        """);

        assertEquals(
                4,
                config.parameterTable("IP0040T1")
                        .orElseThrow()
                        .fields()
                        .get("gcms_product_id")
                        .end());
        assertTrue(
                config.parameterTables().size() >= IsoConfig.defaults().parameterTables().size(),
                "the other tables stay");
    }

    @Test
    void aProcessorNameThisLibraryDoesNotKnowIsCarriedThrough() throws IOException {
        // The names are the config file's to choose. Reading a layout that packs
        // something else inside an element takes a codec of that name, not a
        // change to the parser, so the loader must not vet the name.
        IsoConfig config =
                parse(
                        """
                        {"bit_config": {"37": {"field_name": "Branch", "field_type": "FIXED",
                                               "field_length": 12,
                                               "field_processor": "BRANCH-CODE"}}}
                        """);

        assertEquals("BRANCH-CODE", config.field(37).orElseThrow().processor());
    }

    @Test
    void theColumnListIsReplacedOutright() throws IOException {
        IsoConfig config = parse("{\"output_data_elements\": [\"MTI\", \"DE2\"]}");

        assertEquals(List.of("MTI", "DE2"), config.outputDataElements());
    }

    @Test
    void theRecordLengthLimitCanBeMovedOnItsOwn() throws IOException {
        IsoConfig config = parse("{\"MAX_VBS_RECORD_LENGTH\": 1234}");

        assertEquals(1234, config.maxVbsRecordLength());
        assertEquals(
                IsoConfig.defaults().bitConfig().size(),
                config.bitConfig().size(),
                "the layout is untouched");
    }

    @Test
    void anEmptyConfigChangesNothing() throws IOException {
        IsoConfig config = parse("{}");

        assertEquals(IsoConfig.defaults().bitConfig(), config.bitConfig());
        assertEquals(IsoConfig.defaults().outputDataElements(), config.outputDataElements());
        assertEquals(IsoConfig.defaults().maxVbsRecordLength(), config.maxVbsRecordLength());
    }
}
