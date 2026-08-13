package com.sgerrand.paymentcardutil.iso8583;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

/**
 * Breaks DE 43, the card acceptor name and location, into its parts.
 *
 * <p>The field is one string with backslash separators. Which parts to pull out
 * is set by a regular expression in the field config, so a different acquirer
 * layout needs only a different pattern.
 */
final class De43Codec {

    private static final Map<String, PythonRegex> CACHE = new ConcurrentHashMap<>();

    /** Part whose trailing spaces are stripped, matching cardutil. */
    private static final String POSTCODE_KEY = "DE43_POSTCODE";

    private De43Codec() {
    }

    /**
     * Pulls the parts out of DE 43.
     *
     * @param fieldData       the field's text
     * @param processorConfig the pattern, in Python form; {@code null} means do nothing
     * @return the parts, keyed by group name, or empty if the field does not
     *         match the pattern
     */
    static Map<String, String> unpack(String fieldData, String processorConfig) {
        if (processorConfig == null || processorConfig.isEmpty()) {
            return Map.of();
        }
        PythonRegex regex = CACHE.computeIfAbsent(processorConfig, PythonRegex::compile);
        Matcher matcher = regex.pattern().matcher(fieldData);

        // Python's re.match anchors at the start only, as lookingAt does.
        if (!matcher.lookingAt()) {
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (int group = 1; group <= regex.groupNames().size() && group <= matcher.groupCount(); group++) {
            String name = regex.groupNames().get(group - 1);
            String value = matcher.group(group);
            if (value == null) {
                continue;
            }
            if (name.equals(POSTCODE_KEY)) {
                value = value.stripTrailing();
            }
            values.put(name, value);
        }
        return values;
    }
}
