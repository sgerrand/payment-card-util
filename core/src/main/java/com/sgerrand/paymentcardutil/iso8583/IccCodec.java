package com.sgerrand.paymentcardutil.iso8583;

import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the chip data in DE 55 as a run of tag-length-value records.
 *
 * <p>A tag is one byte, or two if the first byte is {@code 9F} or {@code 5F}.
 * The length is one byte. A tag of {@code 00} marks the end of the data.
 */
final class IccCodec {

    /** Key holding the whole field as hex. */
    static final String ICC_DATA_KEY = Iso8583Message.ICC_DATA_KEY;

    private static final HexFormat HEX = HexFormat.of();

    private IccCodec() {
    }

    /**
     * What to do when the chip data runs out mid-record.
     */
    enum OnError {
        /** Stop reading and keep whatever was read. The default. */
        WARN,
        /** Throw. */
        ERROR;

        /**
         * Reads the setting from a {@code field_processor_config} string such
         * as {@code on_error=ERROR}. Anything unrecognised means {@link #WARN}.
         */
        static OnError from(String processorConfig) {
            if (processorConfig == null) {
                return WARN;
            }
            for (String part : processorConfig.split(";")) {
                String[] pair = part.split("=", 2);
                if (pair.length == 2 && pair[0].trim().equals("on_error")) {
                    return pair[1].trim().equalsIgnoreCase("ERROR") ? ERROR : WARN;
                }
            }
            return WARN;
        }
    }

    /**
     * Breaks the field into tags.
     *
     * @param fieldData       the raw bytes of DE 55
     * @param processorConfig settings from the field config, such as {@code on_error=ERROR}
     * @return {@code ICC_DATA} holding the whole field as hex, plus one
     *         {@code TAGxxxx} entry per tag read
     * @throws Iso8583Exception if the data is malformed and {@code on_error=ERROR}
     */
    static Map<String, String> unpack(byte[] fieldData, String processorConfig) {
        OnError onError = OnError.from(processorConfig);
        Map<String, String> values = new LinkedHashMap<>();
        values.put(ICC_DATA_KEY, HEX.formatHex(fieldData));

        int pointer = 0;
        while (pointer < fieldData.length) {
            int tagStart = pointer;
            int tagLength = isTwoByteTag(fieldData[pointer]) ? 2 : 1;
            if (tagStart + tagLength > fieldData.length) {
                if (stop(onError, "Incomplete tag at position " + tagStart)) {
                    break;
                }
            }
            pointer = tagStart + tagLength;
            String tag = HEX.formatHex(fieldData, tagStart, pointer).toUpperCase(Locale.ROOT);

            // Low values mean the rest of the field is padding.
            if (tag.equals("00")) {
                break;
            }
            if (pointer >= fieldData.length) {
                if (stop(onError, "No length byte for tag " + tag + " at position " + pointer)) {
                    break;
                }
            }
            int length = fieldData[pointer] & 0xFF;
            int valueStart = pointer + 1;
            int valueEnd = valueStart + length;
            if (valueEnd > fieldData.length) {
                if (stop(onError, "Tag " + tag + " says it is " + length + " bytes, but only "
                        + (fieldData.length - valueStart) + " remain")) {
                    break;
                }
            }
            values.put("TAG" + tag, HEX.formatHex(fieldData, valueStart, valueEnd));
            pointer = valueEnd;
        }
        return values;
    }

    private static boolean isTwoByteTag(byte first) {
        int value = first & 0xFF;
        return value == 0x9F || value == 0x5F;
    }

    /**
     * @return true if reading should stop
     * @throws Iso8583Exception if the setting says to fail
     */
    private static boolean stop(OnError onError, String message) {
        if (onError == OnError.ERROR) {
            throw new Iso8583Exception("DE55: " + message);
        }
        return true;
    }
}
