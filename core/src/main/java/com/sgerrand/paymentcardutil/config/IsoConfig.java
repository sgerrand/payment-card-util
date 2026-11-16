package com.sgerrand.paymentcardutil.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Everything the message readers and writers need to know about a message layout: which data
 * elements exist, how each is encoded, which ones the CSV tools write out, and how parameter table
 * records are laid out.
 *
 * <p>{@link #defaults()} holds the Mastercard IPM layout. Build a changed copy with {@link
 * #toBuilder()} when a file needs a different layout.
 */
public final class IsoConfig {

    /** Longest VBS record accepted before a file is called malformed. */
    public static final int DEFAULT_MAX_VBS_RECORD_LENGTH = 6000;

    private static final IsoConfig DEFAULTS = DefaultConfig.build();

    private final Map<Integer, FieldConfig> bitConfig;
    private final List<String> outputDataElements;
    private final Map<String, ParamTable> parameterTables;
    private final Map<String, Map<String, PdsSubfield>> pdsSubfields;
    private final int maxVbsRecordLength;

    private IsoConfig(Builder builder) {
        this.bitConfig = Collections.unmodifiableMap(new TreeMap<>(builder.bitConfig));
        this.outputDataElements = List.copyOf(builder.outputDataElements);
        this.parameterTables =
                Collections.unmodifiableMap(new LinkedHashMap<>(builder.parameterTables));
        Map<String, Map<String, PdsSubfield>> subfields = new LinkedHashMap<>();
        builder.pdsSubfields.forEach(
                (tag, parts) ->
                        subfields.put(
                                tag, Collections.unmodifiableMap(new LinkedHashMap<>(parts))));
        this.pdsSubfields = Collections.unmodifiableMap(subfields);
        this.maxVbsRecordLength = builder.maxVbsRecordLength;
    }

    /** The built in Mastercard IPM layout. */
    public static IsoConfig defaults() {
        return DEFAULTS;
    }

    /** An empty config to build one up from scratch. */
    public static Builder builder() {
        return new Builder();
    }

    /** A builder holding a copy of this config, ready to be changed. */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.bitConfig.putAll(bitConfig);
        builder.outputDataElements.addAll(outputDataElements);
        builder.parameterTables.putAll(parameterTables);
        builder.pdsSubfields.putAll(pdsSubfields);
        builder.maxVbsRecordLength = maxVbsRecordLength;
        return builder;
    }

    /**
     * The layout of one data element.
     *
     * @param bit the data element number, from 1
     */
    public Optional<FieldConfig> field(int bit) {
        return Optional.ofNullable(bitConfig.get(bit));
    }

    /** Every configured data element, in number order. */
    public Map<Integer, FieldConfig> bitConfig() {
        return bitConfig;
    }

    /**
     * The data elements handled by one field processor, in number order.
     *
     * <p>Which element carries private data, chip data or an acquirer address is a property of the
     * layout, so asking the config beats every caller walking {@link #bitConfig()} with its own
     * filter.
     *
     * @param processor a name from {@link FieldProcessors}, or one of your own
     */
    public List<Integer> bitsWithProcessor(String processor) {
        return bitConfig.entrySet().stream()
                .filter(entry -> processor.equals(entry.getValue().processor()))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * The keys the CSV tools write out, in column order. Keys are of the same form as message keys:
     * {@code MTI}, {@code DE2}, {@code PDS0158}, {@code DE43_NAME}, {@code ICC_DATA}.
     */
    public List<String> outputDataElements() {
        return outputDataElements;
    }

    /** The known IPM parameter tables, keyed by table id. */
    public Map<String, ParamTable> parameterTables() {
        return parameterTables;
    }

    /** The layout of one parameter table. */
    public Optional<ParamTable> parameterTable(String tableId) {
        return Optional.ofNullable(parameterTables.get(tableId));
    }

    /**
     * The parts read out of private data subelements, by four character PDS tag and then by
     * subfield number.
     *
     * <p>A message read with this layout holds each part next to its subelement, keyed {@code
     * PDS<tag>_SF<number>}: the built in layout splits {@code PDS0105} into {@code PDS0105_SF1} to
     * {@code PDS0105_SF4}. A part that runs past the end of the subelement is left out. The parts
     * are only ever read: writing a message ignores them and writes the subelement itself, so
     * change {@code PDS0105} to change what goes in the file.
     */
    public Map<String, Map<String, PdsSubfield>> pdsSubfields() {
        return pdsSubfields;
    }

    /**
     * The parts read out of one private data subelement, by subfield number.
     *
     * @param tag the four character PDS tag, such as {@code 0105}
     * @return the parts, or an empty map if the subelement is not split
     */
    public Map<String, PdsSubfield> pdsSubfields(String tag) {
        return pdsSubfields.getOrDefault(tag, Map.of());
    }

    /** Longest VBS record accepted before a file is called malformed. */
    public int maxVbsRecordLength() {
        return maxVbsRecordLength;
    }

    /** Builds an {@link IsoConfig}. */
    public static final class Builder {

        private final Map<Integer, FieldConfig> bitConfig = new TreeMap<>();
        private final List<String> outputDataElements = new java.util.ArrayList<>();
        private final Map<String, ParamTable> parameterTables = new LinkedHashMap<>();
        private final Map<String, Map<String, PdsSubfield>> pdsSubfields = new LinkedHashMap<>();
        private int maxVbsRecordLength = DEFAULT_MAX_VBS_RECORD_LENGTH;

        private Builder() {}

        /** Sets the layout of one data element, replacing any existing entry. */
        public Builder field(int bit, FieldConfig config) {
            if (bit < 1 || bit > 128) {
                throw new IllegalArgumentException("Data element number out of range: " + bit);
            }
            bitConfig.put(bit, Objects.requireNonNull(config, "config"));
            return this;
        }

        /** Removes a data element from the layout. */
        public Builder removeField(int bit) {
            bitConfig.remove(bit);
            return this;
        }

        /** Replaces the whole bit config. */
        public Builder bitConfig(Map<Integer, FieldConfig> config) {
            bitConfig.clear();
            config.forEach(this::field);
            return this;
        }

        /** Replaces the CSV column list. */
        public Builder outputDataElements(List<String> keys) {
            outputDataElements.clear();
            outputDataElements.addAll(keys);
            return this;
        }

        /** Adds or replaces one parameter table layout. */
        public Builder parameterTable(ParamTable table) {
            parameterTables.put(table.tableId(), table);
            return this;
        }

        /** Replaces the whole set of parameter table layouts. */
        public Builder parameterTables(Map<String, ParamTable> tables) {
            parameterTables.clear();
            parameterTables.putAll(tables);
            return this;
        }

        /**
         * Sets the parts read out of one private data subelement, replacing any already set for it.
         * An empty map stops the subelement being split.
         *
         * @param tag the four character PDS tag, such as {@code 0105}
         * @param subfields the parts, by subfield number, in the order they should be read
         */
        public Builder pdsSubfields(String tag, Map<String, PdsSubfield> subfields) {
            Objects.requireNonNull(tag, "tag");
            if (tag.length() != 4) {
                throw new IllegalArgumentException(
                        "A PDS tag is four characters, such as 0105: " + tag);
            }
            Map<String, PdsSubfield> copy = new LinkedHashMap<>();
            Objects.requireNonNull(subfields, "subfields")
                    .forEach(
                            (number, part) ->
                                    copy.put(
                                            Objects.requireNonNull(number, "number"),
                                            Objects.requireNonNull(part, "subfield")));
            if (copy.isEmpty()) {
                pdsSubfields.remove(tag);
            } else {
                pdsSubfields.put(tag, copy);
            }
            return this;
        }

        /** Replaces every subelement's parts. An empty map splits none of them. */
        public Builder pdsSubfields(Map<String, Map<String, PdsSubfield>> subfields) {
            pdsSubfields.clear();
            subfields.forEach(this::pdsSubfields);
            return this;
        }

        /** Sets the longest VBS record that will be accepted. */
        public Builder maxVbsRecordLength(int length) {
            if (length < 1) {
                throw new IllegalArgumentException(
                        "Maximum record length must be positive: " + length);
            }
            this.maxVbsRecordLength = length;
            return this;
        }

        public IsoConfig build() {
            return new IsoConfig(this);
        }
    }
}
