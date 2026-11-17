package com.sgerrand.paymentcardutil.iso8583;

import com.sgerrand.paymentcardutil.config.PdsSubfield;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Packs and unpacks Mastercard private data subelements.
 *
 * <p>A PDS is a four digit tag, a three digit length, then that many characters of data. Several
 * are packed one after another into a data element such as DE 48.
 *
 * <p>Some subelements are split further into fixed position parts, kept as {@code PDSxxxx_SFn}
 * keys. Those are read only: packing skips them, see {@link #pack}.
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
     * Reads the subelements packed into one data element, and the parts of any the layout splits.
     *
     * @param fieldData the data element's text
     * @param subfields the parts to read, by PDS tag and then subfield number
     * @return subelement values keyed {@code PDSxxxx}, each followed by its parts keyed {@code
     *     PDSxxxx_SFn}, in the order they appeared
     * @throws Iso8583Exception if a tag or length is cut short or is not a number
     */
    static Map<String, Object> unpack(
            String fieldData, Map<String, Map<String, PdsSubfield>> subfields) {
        Map<String, Object> values = new LinkedHashMap<>();
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
            String data = fieldData.substring(headerEnd, headerEnd + length);
            values.put("PDS" + tag, data);
            Map<String, PdsSubfield> parts = subfields.get(tag);
            if (parts != null) {
                unpackSubfields(tag, data, parts, values);
            }
            pointer = headerEnd + length;
        }
        return values;
    }

    /**
     * Copies the configured parts out of one subelement.
     *
     * <p>Matches cardutil's {@code _parse_pds_subfields}: a part that runs past the end of the data
     * is left out rather than cut short, and a number that does not read as one stays text.
     */
    private static void unpackSubfields(
            String tag, String data, Map<String, PdsSubfield> parts, Map<String, Object> values) {
        for (Map.Entry<String, PdsSubfield> entry : parts.entrySet()) {
            PdsSubfield part = entry.getValue();
            // As a long, so a start and length near the int limit cannot wrap
            // round and pass the check.
            if ((long) part.start() + part.length() > data.length()) {
                continue;
            }
            String text = data.substring(part.start(), part.end());
            values.put(PdsSubfield.key(tag, entry.getKey()), subfieldValue(text, part));
        }
    }

    private static Object subfieldValue(String text, PdsSubfield part) {
        try {
            return switch (part.valueType()) {
                case LONG -> wholeNumber(text.trim());
                case DECIMAL -> new BigDecimal(text.trim());
                case TEXT, DATETIME -> text;
            };
        } catch (NumberFormatException e) {
            // cardutil keeps the text when it does not convert, rather than
            // failing the whole message over one part.
            return text;
        }
    }

    /**
     * A whole number of any size, as Python's {@code int} reads one: a {@link Long} when it fits, a
     * {@link BigInteger} when it does not.
     */
    private static Number wholeNumber(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            // Either not a number, which BigInteger rejects too, or one with
            // more digits than a long holds.
            return new BigInteger(text);
        }
    }

    /**
     * Packs every {@code PDSxxxx} value in a message into groups, each short enough to fit one data
     * element.
     *
     * <p>Tags are packed in ascending order. A group is closed off once adding the next subelement
     * would take it past {@value #MAX_GROUP_LENGTH} characters.
     *
     * <p>Subfield keys such as {@code PDS0105_SF1} are skipped when the message also holds the
     * subelement they were read from, which is what gets written. cardutil 0.7.4 tries to pack them
     * as subelements too, and fails on every message it read with one, so it cannot write back a
     * file it has just read.
     *
     * @return one string per data element needed, or an empty list if the message has no PDS values
     * @throws Iso8583Exception if the message holds a subfield key without its subelement, since
     *     the part would otherwise be lost without a word
     */
    static List<String> pack(Map<String, Object> messageValues) {
        Map<String, Object> pdsValues = new TreeMap<>();
        messageValues.forEach(
                (key, value) -> {
                    if (!key.startsWith("PDS")) {
                        return;
                    }
                    Optional<String> parentKey = PdsSubfield.parentKey(key);
                    if (parentKey.isEmpty()) {
                        pdsValues.put(key, value);
                        return;
                    }
                    String parent = parentKey.get();
                    if (!messageValues.containsKey(parent)) {
                        throw new Iso8583Exception(
                                key
                                        + " is a part of "
                                        + parent
                                        + ", which the message does not hold. Parts are only"
                                        + " read, never written: set "
                                        + parent
                                        + " itself.");
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
