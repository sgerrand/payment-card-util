package com.sgerrand.paymentcardutil.iso8583;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Translates a Python regular expression with named groups into a Java one.
 *
 * <p>Two things differ. Python writes a named group as {@code (?P<name>...)}, Java as {@code
 * (?<name>...)}. And Java only allows letters and digits in a group name, so a config name like
 * {@code DE43_POSTCODE} cannot be used as a Java group name at all.
 *
 * <p>So the names are stripped out and recorded in order instead, leaving plain numbered groups.
 * Group 1 is the first name, group 2 the second, and so on.
 *
 * @param pattern the translated pattern
 * @param groupNames the names, in the order their groups appear
 */
record PythonRegex(Pattern pattern, List<String> groupNames) {

    private static final String NAMED_GROUP_START = "(?P<";

    /**
     * Translates a Python pattern.
     *
     * @throws Iso8583Exception if the pattern is malformed
     */
    static PythonRegex compile(String pythonPattern) {
        StringBuilder translated = new StringBuilder(pythonPattern.length());
        List<String> names = new ArrayList<>();

        int index = 0;
        while (index < pythonPattern.length()) {
            char c = pythonPattern.charAt(index);

            // Step over escapes so that \( is not read as a group start.
            if (c == '\\' && index + 1 < pythonPattern.length()) {
                translated.append(c).append(pythonPattern.charAt(index + 1));
                index += 2;
                continue;
            }
            if (pythonPattern.startsWith(NAMED_GROUP_START, index)) {
                int nameEnd = pythonPattern.indexOf('>', index + NAMED_GROUP_START.length());
                if (nameEnd < 0) {
                    throw new Iso8583Exception("Unclosed group name in pattern: " + pythonPattern);
                }
                names.add(pythonPattern.substring(index + NAMED_GROUP_START.length(), nameEnd));
                translated.append('(');
                index = nameEnd + 1;
                continue;
            }
            translated.append(c);
            index++;
        }

        try {
            return new PythonRegex(Pattern.compile(translated.toString()), List.copyOf(names));
        } catch (java.util.regex.PatternSyntaxException e) {
            throw new Iso8583Exception("Cannot compile pattern: " + pythonPattern, e);
        }
    }
}
