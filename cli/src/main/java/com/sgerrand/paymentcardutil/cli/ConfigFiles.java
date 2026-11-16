package com.sgerrand.paymentcardutil.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgerrand.paymentcardutil.config.FieldConfig;
import com.sgerrand.paymentcardutil.config.FieldType;
import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.config.ParamTable;
import com.sgerrand.paymentcardutil.config.PdsSubfield;
import com.sgerrand.paymentcardutil.config.ValueType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a message layout from a JSON file, so the tools can read files that do not follow the built
 * in Mastercard IPM layout.
 *
 * <p>The file has the same shape as cardutil's, so a config written for the Python tools works here
 * unchanged:
 *
 * <pre>{@code
 * {
 *   "MAX_VBS_RECORD_LENGTH": 6000,
 *   "bit_config": {
 *     "2": {"field_name": "PAN", "field_type": "LLVAR", "field_length": 0}
 *   },
 *   "output_data_elements": ["MTI", "DE2"],
 *   "mci_parameter_tables": {
 *     "IP0006T1": {"card_program_id": {"start": 19, "end": 22}}
 *   },
 *   "PDS": {
 *     "0105": {"subfields": {"1": {"field_name": "File Type", "field_start": 0, "field_length": 3}}}
 *   }
 * }
 * }</pre>
 *
 * <p>Anything the file leaves out keeps its built in value, so a config only has to say what is
 * different. That holds element by element: naming DE 2 changes DE 2 and leaves the other sixty
 * alone. The one exception is {@code output_data_elements}, which is a column order rather than a
 * set of parts, so a file naming it replaces the list outright. {@code PDS} goes subelement by
 * subelement the same way; give one an empty {@code subfields} to stop it being split.
 *
 * <p>cardutil itself ignores a {@code PDS} section in a config file and always splits the
 * subelements its own built in config names. Here the file is read, as every other section is.
 */
final class ConfigFiles {

    /** Environment variable naming a directory holding {@value #CONFIG_FILENAME}. */
    static final String CONFIG_ENV_VAR = "CARDUTIL_CONFIG";

    /** The file looked for in that directory. */
    static final String CONFIG_FILENAME = "cardutil.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConfigFiles() {}

    /**
     * Works out which layout to use.
     *
     * <p>A file named on the command line wins. Otherwise a {@value #CONFIG_FILENAME} in the
     * directory named by {@value #CONFIG_ENV_VAR} is used. Failing both, the built in layout.
     *
     * @throws IOException if a config file was named but cannot be read
     */
    static IsoConfig load(Path commandLineFile) throws IOException {
        if (commandLineFile != null) {
            if (!Files.isRegularFile(commandLineFile)) {
                throw new IOException("No config file at " + commandLineFile);
            }
            return parse(Files.readString(commandLineFile), IsoConfig.defaults());
        }

        String directory = System.getenv(CONFIG_ENV_VAR);
        if (directory != null) {
            Path fromEnvironment = Path.of(directory).toAbsolutePath().resolve(CONFIG_FILENAME);
            if (Files.isRegularFile(fromEnvironment)) {
                return parse(Files.readString(fromEnvironment), IsoConfig.defaults());
            }
        }
        return IsoConfig.defaults();
    }

    /** Builds a layout from JSON, starting from {@code base}. */
    static IsoConfig parse(String json, IsoConfig base) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        IsoConfig.Builder builder = base.toBuilder();

        if (root.has("MAX_VBS_RECORD_LENGTH")) {
            builder.maxVbsRecordLength(root.get("MAX_VBS_RECORD_LENGTH").asInt());
        }
        if (root.has("bit_config")) {
            readBitConfig(root.get("bit_config")).forEach(builder::field);
        }
        if (root.has("output_data_elements")) {
            List<String> keys = new ArrayList<>();
            root.get("output_data_elements").forEach(key -> keys.add(key.asText()));
            builder.outputDataElements(keys);
        }
        if (root.has("mci_parameter_tables")) {
            readParameterTables(root.get("mci_parameter_tables"))
                    .values()
                    .forEach(builder::parameterTable);
        }
        if (root.has("PDS")) {
            readPdsSubfields(root.get("PDS")).forEach(builder::pdsSubfields);
        }
        return builder.build();
    }

    private static Map<Integer, FieldConfig> readBitConfig(JsonNode node) {
        Map<Integer, FieldConfig> fields = new LinkedHashMap<>();
        node.properties()
                .forEach(
                        entry -> {
                            JsonNode field = entry.getValue();
                            fields.put(
                                    Integer.parseInt(entry.getKey()),
                                    new FieldConfig(
                                            text(field, "field_name", ""),
                                            FieldType.valueOf(text(field, "field_type", "FIXED")),
                                            field.path("field_length").asInt(0),
                                            ValueType.fromPythonType(
                                                    text(field, "field_python_type", null)),
                                            text(field, "field_date_format", null),
                                            text(field, "field_processor", null),
                                            text(field, "field_processor_config", null)));
                        });
        return fields;
    }

    private static Map<String, Map<String, PdsSubfield>> readPdsSubfields(JsonNode node) {
        Map<String, Map<String, PdsSubfield>> subfields = new LinkedHashMap<>();
        node.properties()
                .forEach(
                        tagEntry -> {
                            Map<String, PdsSubfield> parts = new LinkedHashMap<>();
                            tagEntry.getValue()
                                    .path("subfields")
                                    .properties()
                                    .forEach(
                                            part -> {
                                                JsonNode field = part.getValue();
                                                parts.put(
                                                        part.getKey(),
                                                        new PdsSubfield(
                                                                text(field, "field_name", ""),
                                                                field.path("field_start").asInt(0),
                                                                field.path("field_length").asInt(0),
                                                                PdsSubfield.valueTypeFor(
                                                                        text(
                                                                                field,
                                                                                "field_python_type",
                                                                                null))));
                                            });
                            subfields.put(tagEntry.getKey(), parts);
                        });
        return subfields;
    }

    private static Map<String, ParamTable> readParameterTables(JsonNode node) {
        Map<String, ParamTable> tables = new LinkedHashMap<>();
        node.properties()
                .forEach(
                        tableEntry -> {
                            Map<String, ParamTable.Position> positions = new LinkedHashMap<>();
                            tableEntry
                                    .getValue()
                                    .properties()
                                    .forEach(
                                            fieldEntry ->
                                                    positions.put(
                                                            fieldEntry.getKey(),
                                                            new ParamTable.Position(
                                                                    fieldEntry
                                                                            .getValue()
                                                                            .get("start")
                                                                            .asInt(),
                                                                    fieldEntry
                                                                            .getValue()
                                                                            .get("end")
                                                                            .asInt())));
                            tables.put(
                                    tableEntry.getKey(),
                                    new ParamTable(tableEntry.getKey(), positions));
                        });
        return tables;
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }
}
