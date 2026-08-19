package com.sgerrand.paymentcardutil.iso8583;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Packs and unpacks Mastercard private data subelements.
 *
 * <p>A PDS is a four digit tag, a three digit length, then that many characters of data. Several
 * are packed one after another into a data element such as DE 48.
 */
final class PdsCodec {

    /** Characters in a PDS tag. */
    static final int TAG_LENGTH = 4;

    /** Characters in a PDS length. */
    static final int LENGTH_LENGTH = 3;

    /** Longest a packed group may be before it spills into the next data element. */
    static final int MAX_GROUP_LENGTH = 999;

    private PdsCodec() {}

    /**
     * Reads the subelements packed into one data element.
     *
     * @param fieldData the data element's text
     * @return subelement values keyed {@code PDSxxxx}, in the order they appeared
     * @throws Iso8583Exception if a tag or length is cut short or is not a number
     */
    static Map<String, String> unpack(String fieldData) {
        Map<String, String> values = new LinkedHashMap<>();
        int pointer = 0;

        while (pointer < fieldData.length()) {
            int headerEnd = pointer + TAG_LENGTH + LENGTH_LENGTH;
            if (headerEnd > fieldData.length()) {
                throw new Iso8583Exception(
                        "PDS header runs past the end of the field at position " + pointer);
            }
            String tag = fieldData.substring(pointer, pointer + TAG_LENGTH);
            String lengthText = fieldData.substring(pointer + TAG_LENGTH, headerEnd);
            int length;
            try {
                length = Integer.parseInt(lengthText.trim());
            } catch (NumberFormatException e) {
                throw new Iso8583Exception(
                        "PDS" + tag + " has a length that is not a number: " + lengthText, e);
            }
            if (length < 0 || headerEnd + length > fieldData.length()) {
                throw new Iso8583Exception(
                        "PDS"
                                + tag
                                + " says it is "
                                + length
                                + " characters, "
                                + "but only "
                                + (fieldData.length() - headerEnd)
                                + " remain");
            }
            values.put("PDS" + tag, fieldData.substring(headerEnd, headerEnd + length));
            pointer = headerEnd + length;
        }
        return values;
    }

    /**
     * Packs every {@code PDSxxxx} value in a message into groups, each short enough to fit one data
     * element.
     *
     * <p>Tags are packed in ascending order. A group is closed off once adding the next subelement
     * would take it past {@value #MAX_GROUP_LENGTH} characters.
     *
     * @return one string per data element needed, or an empty list if the message has no PDS values
     */
    static List<String> pack(Map<String, Object> messageValues) {
        Map<String, Object> pdsValues = new TreeMap<>();
        messageValues.forEach(
                (key, value) -> {
                    if (key.startsWith("PDS")) {
                        pdsValues.put(key, value);
                    }
                });
        if (pdsValues.isEmpty()) {
            return List.of();
        }

        List<String> groups = new ArrayList<>();
        StringBuilder group = new StringBuilder();

        for (Map.Entry<String, Object> entry : pdsValues.entrySet()) {
            int tag = parseTag(entry.getKey());
            String value = String.valueOf(entry.getValue());
            if (value.length() > MAX_GROUP_LENGTH - TAG_LENGTH - LENGTH_LENGTH) {
                // cardutil emits an empty group here, which the writer then drops
                // as a falsy value, losing the data without saying so. Fail instead.
                throw new Iso8583Exception(
                        entry.getKey()
                                + " is "
                                + value.length()
                                + " characters, too long to pack into a "
                                + MAX_GROUP_LENGTH
                                + " character data element");
            }
            String packed = "%04d%03d%s".formatted(tag, value.length(), value);

            if (group.length() + packed.length() > MAX_GROUP_LENGTH && !group.isEmpty()) {
                groups.add(group.toString());
                group.setLength(0);
            }
            group.append(packed);
        }
        if (!group.isEmpty()) {
            groups.add(group.toString());
        }
        return groups;
    }

    private static int parseTag(String key) {
        try {
            return Integer.parseInt(key.substring(3));
        } catch (NumberFormatException e) {
            throw new Iso8583Exception("Not a valid PDS key: " + key, e);
        }
    }
}
