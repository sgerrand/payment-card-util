package com.sgerrand.paymentcardutil.iso8583;

import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the chip data in DE 55 as a run of tag-length-value records.
 *
 * <p>A tag is one byte, or two if the first byte is {@code 9F} or {@code 5F}.
 * The length is one byte. A tag of {@code 00} marks the end of the data.
 */
final class IccCodec {

    private static final HexFormat HEX = HexFormat.of();

    /** Tag names are written in upper case, values in lower. */
    private static final HexFormat UPPER_HEX = HexFormat.of().withUpperCase();

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
     * @param bit             which data element the chip data came from, for
     *                        error messages
     * @param fieldData       the raw bytes of the field
     * @param processorConfig settings from the field config, such as {@code on_error=ERROR}
     * @return {@code ICC_DATA} holding the whole field as hex, plus one
     *         {@code TAGxxxx} entry per tag read
     * @throws Iso8583Exception if the data is malformed and {@code on_error=ERROR}
     */
    static Map<String, String> unpack(int bit, byte[] fieldData, String processorConfig) {
        OnError onError = OnError.from(processorConfig);
        Map<String, String> values = new LinkedHashMap<>();
        values.put(Iso8583Message.ICC_DATA_KEY, HEX.formatHex(fieldData));

        int pointer = 0;
        while (pointer < fieldData.length) {
            int tagStart = pointer;
            int tagLength = isTwoByteTag(fieldData[pointer]) ? 2 : 1;
            if (tagStart + tagLength > fieldData.length) {
                stopOrThrow(onError, bit, "Incomplete tag at position " + tagStart);
                break;
            }
            pointer = tagStart + tagLength;
            String tag = UPPER_HEX.formatHex(fieldData, tagStart, pointer);

            // Low values mean the rest of the field is padding.
            if (tag.equals("00")) {
                break;
            }
            if (pointer >= fieldData.length) {
                stopOrThrow(onError, bit, "No length byte for tag " + tag + " at position " + pointer);
                break;
            }
            int length = fieldData[pointer] & 0xFF;
            int valueStart = pointer + 1;
            int valueEnd = valueStart + length;
            if (valueEnd > fieldData.length) {
                stopOrThrow(onError, bit, "Tag " + tag + " says it is " + length + " bytes, but only "
                        + (fieldData.length - valueStart) + " remain");
                break;
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
     * Reading stops here either way; this decides whether it stops quietly.
     *
     * @throws Iso8583Exception if the setting says to fail
     */
    private static void stopOrThrow(OnError onError, int bit, String message) {
        if (onError == OnError.ERROR) {
            throw new Iso8583Exception("DE" + bit + ": " + message);
        }
    }
}
