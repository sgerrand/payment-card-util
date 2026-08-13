package com.sgerrand.paymentcardutil.config;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns Python {@code strftime} date patterns, as used in the ISO 8583 field
 * config, into Java date formatters.
 *
 * <p>Two digit years follow Python's rule, not Java's: {@code 00} to {@code 68}
 * mean 2000 to 2068, and {@code 69} to {@code 99} mean 1969 to 1999. Java's own
 * {@code yy} pattern would read every two digit year as 2000 to 2099, which
 * would date 1990s records a century late.
 */
public final class DateFormats {

    /**
     * First year of the 100 year window a two digit year falls in. Matches
     * Python's {@code time.strptime}.
     */
    static final int TWO_DIGIT_YEAR_BASE = 1969;

    private static final Map<String, DateTimeFormatter> CACHE = new ConcurrentHashMap<>();

    private DateFormats() {
    }

    /**
     * Builds a formatter for a Python date pattern.
     *
     * <p>Supported directives: {@code %y %Y %m %d %H %M %S %j %f %%}. Anything
     * else in the pattern is treated as literal text.
     *
     * @param pythonFormat the pattern, such as {@code %y%m%d%H%M%S}
     * @throws IllegalArgumentException if the pattern uses an unsupported directive
     */
    public static DateTimeFormatter of(String pythonFormat) {
        return CACHE.computeIfAbsent(pythonFormat, DateFormats::build);
    }

    private static DateTimeFormatter build(String pythonFormat) {
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        StringBuilder literal = new StringBuilder();

        for (int i = 0; i < pythonFormat.length(); i++) {
            char c = pythonFormat.charAt(i);
            if (c != '%') {
                literal.append(c);
                continue;
            }
            if (i + 1 >= pythonFormat.length()) {
                throw new IllegalArgumentException("Date format ends with a stray %: " + pythonFormat);
            }
            char directive = pythonFormat.charAt(++i);
            if (directive == '%') {
                literal.append('%');
                continue;
            }
            if (!literal.isEmpty()) {
                builder.appendLiteral(literal.toString());
                literal.setLength(0);
            }
            append(builder, directive, pythonFormat);
        }
        if (!literal.isEmpty()) {
            builder.appendLiteral(literal.toString());
        }
        // A pattern like %y%m%d carries no time, but the parsed value is a
        // LocalDateTime. Fill in the parts the pattern leaves out, as Python does.
        builder.parseDefaulting(ChronoField.MONTH_OF_YEAR, 1);
        builder.parseDefaulting(ChronoField.DAY_OF_MONTH, 1);
        builder.parseDefaulting(ChronoField.HOUR_OF_DAY, 0);
        builder.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0);
        builder.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0);
        return builder.toFormatter();
    }

    private static void append(DateTimeFormatterBuilder builder, char directive, String pythonFormat) {
        switch (directive) {
            case 'y' -> builder.appendValueReduced(ChronoField.YEAR, 2, 2, TWO_DIGIT_YEAR_BASE);
            case 'Y' -> builder.appendValue(ChronoField.YEAR, 4);
            case 'm' -> builder.appendValue(ChronoField.MONTH_OF_YEAR, 2);
            case 'd' -> builder.appendValue(ChronoField.DAY_OF_MONTH, 2);
            case 'j' -> builder.appendValue(ChronoField.DAY_OF_YEAR, 3);
            case 'H' -> builder.appendValue(ChronoField.HOUR_OF_DAY, 2);
            case 'M' -> builder.appendValue(ChronoField.MINUTE_OF_HOUR, 2);
            case 'S' -> builder.appendValue(ChronoField.SECOND_OF_MINUTE, 2);
            case 'f' -> builder.appendValue(ChronoField.MICRO_OF_SECOND, 6);
            default -> throw new IllegalArgumentException(
                    "Unsupported date directive %" + directive + " in " + pythonFormat);
        }
    }
}
