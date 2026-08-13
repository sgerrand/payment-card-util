package com.sgerrand.paymentcardutil.iso8583;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * One ISO 8583 message, held as its data elements and any subfields pulled out
 * of them.
 *
 * <p>Values are keyed the same way as in cardutil, so a message read here and a
 * message read there hold the same keys:
 *
 * <ul>
 *   <li>{@code MTI} - the message type indicator</li>
 *   <li>{@code DE1} to {@code DE127} - data elements</li>
 *   <li>{@code PDSxxxx} - Mastercard private data subelements</li>
 *   <li>{@code TAGxxxx} and {@code ICC_DATA} - chip data from DE 55</li>
 *   <li>{@code DE43_NAME}, {@code DE43_SUBURB} and friends - parts of DE 43</li>
 * </ul>
 *
 * <p>Prefer the typed accessors ({@link #text}, {@link #number}, {@link #pds})
 * over {@link #values()}; the map is there for CSV output and for code coming
 * across from cardutil.
 *
 * <p>Instances are immutable. Build one with {@link #builder()}, or change a
 * copy with {@link #toBuilder()}.
 */
public final class Iso8583Message {

    /** Key holding the message type indicator. */
    public static final String MTI_KEY = "MTI";

    /** Key holding DE 55 in full, as hex. */
    public static final String ICC_DATA_KEY = "ICC_DATA";

    private final Map<String, Object> values;

    private Iso8583Message(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builds a message from cardutil style keys. */
    public static Iso8583Message of(Map<String, ?> values) {
        Builder builder = new Builder();
        values.forEach(builder::put);
        return builder.build();
    }

    /** A builder holding a copy of this message. */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.values.putAll(values);
        return builder;
    }

    /**
     * The message type indicator, if the message has one.
     *
     * @throws IllegalArgumentException if the message carries something that is
     *                                  not four digits; use {@link #mtiText()}
     *                                  to see it as it was read
     */
    public Optional<Mti> mti() {
        return text(MTI_KEY).map(Mti::new);
    }

    /** The message type indicator exactly as it was read. */
    public Optional<String> mtiText() {
        return text(MTI_KEY);
    }

    /** Every value, keyed as described on this class. Unmodifiable. */
    public Map<String, Object> values() {
        return values;
    }

    /** Whether the message holds the given key. */
    public boolean has(String key) {
        return values.containsKey(key);
    }

    /** Whether the message holds the given data element. */
    public boolean hasField(int de) {
        return values.containsKey(deKey(de));
    }

    /** The data element numbers present, in order. */
    public java.util.SortedSet<Integer> fieldNumbers() {
        TreeSet<Integer> numbers = new TreeSet<>();
        for (String key : values.keySet()) {
            if (key.length() > 2 && key.startsWith("DE") && key.indexOf('_') < 0) {
                try {
                    numbers.add(Integer.parseInt(key.substring(2)));
                } catch (NumberFormatException ignored) {
                    // Not a plain DE key, such as DE43_NAME. Skip it.
                }
            }
        }
        return numbers;
    }

    /** The raw value behind a key. */
    public Optional<Object> value(String key) {
        return Optional.ofNullable(values.get(key));
    }

    /** A value as text, whatever type it was stored as. */
    public Optional<String> text(String key) {
        return value(key).map(String::valueOf);
    }

    /** A data element as text. */
    public Optional<String> text(int de) {
        return text(deKey(de));
    }

    /**
     * A data element as a whole number.
     *
     * @throws Iso8583Exception if the value is not a number
     */
    public Optional<Long> number(int de) {
        return value(deKey(de)).map(value -> switch (value) {
            case Number n -> n.longValue();
            case String s -> parseLong(de, s);
            default -> throw new Iso8583Exception("DE" + de + " is not a number: " + value.getClass());
        });
    }

    /**
     * A data element as a decimal.
     *
     * @throws Iso8583Exception if the value is not a number
     */
    public Optional<BigDecimal> amount(int de) {
        return value(deKey(de)).map(value -> switch (value) {
            case BigDecimal d -> d;
            case Number n -> BigDecimal.valueOf(n.longValue());
            case String s -> parseDecimal(de, s);
            default -> throw new Iso8583Exception("DE" + de + " is not a number: " + value.getClass());
        });
    }

    /**
     * A data element as a date and time.
     *
     * @throws Iso8583Exception if the value was not read as a date
     */
    public Optional<LocalDateTime> dateTime(int de) {
        return value(deKey(de)).map(value -> {
            if (value instanceof LocalDateTime dateTime) {
                return dateTime;
            }
            throw new Iso8583Exception("DE" + de + " is not a date: " + value.getClass());
        });
    }

    /**
     * A Mastercard private data subelement.
     *
     * @param tag the PDS tag, such as 158
     */
    public Optional<String> pds(int tag) {
        return text(pdsKey(tag));
    }

    /**
     * An ICC tag read out of DE 55, as hex.
     *
     * @param tag the tag in hex, such as {@code 9F02}; case does not matter
     */
    public Optional<String> iccTag(String tag) {
        return text("TAG" + tag.toUpperCase(java.util.Locale.ROOT));
    }

    /** DE 55 in full, as hex. */
    public Optional<String> iccData() {
        return text(ICC_DATA_KEY);
    }

    /** The key holding a data element, such as {@code DE2}. */
    public static String deKey(int de) {
        if (de < 1 || de > 128) {
            throw new IllegalArgumentException("Data element number out of range: " + de);
        }
        return "DE" + de;
    }

    /** The key holding a private data subelement, such as {@code PDS0158}. */
    public static String pdsKey(int tag) {
        if (tag < 0 || tag > 9999) {
            throw new IllegalArgumentException("PDS tag out of range: " + tag);
        }
        return "PDS%04d".formatted(tag);
    }

    private static long parseLong(int de, String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            throw new Iso8583Exception("DE" + de + " is not a number: " + text, e);
        }
    }

    private static BigDecimal parseDecimal(int de, String text) {
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new Iso8583Exception("DE" + de + " is not a number: " + text, e);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Iso8583Message message && message.values.equals(values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    /**
     * A summary safe to log: the message type and which data elements are
     * present, but none of their values.
     */
    @Override
    public String toString() {
        return "Iso8583Message[mti=" + values.getOrDefault(MTI_KEY, "none")
                + ", fields=" + fieldNumbers() + "]";
    }

    /** Builds an {@link Iso8583Message}. */
    public static final class Builder {

        private final Map<String, Object> values = new LinkedHashMap<>();

        private Builder() {
        }

        /** Sets the message type indicator. */
        public Builder mti(String mti) {
            return put(MTI_KEY, new Mti(mti).digits());
        }

        /** Sets the message type indicator. */
        public Builder mti(Mti mti) {
            return put(MTI_KEY, mti.digits());
        }

        /** Sets a data element. */
        public Builder de(int de, Object value) {
            return put(deKey(de), value);
        }

        /** Sets a Mastercard private data subelement. */
        public Builder pds(int tag, String value) {
            return put(pdsKey(tag), value);
        }

        /** Sets any key directly. Use the typed setters where one fits. */
        public Builder put(String key, Object value) {
            Objects.requireNonNull(key, "key");
            if (value == null) {
                values.remove(key);
            } else {
                values.put(key, value);
            }
            return this;
        }

        /** Sets many keys at once. */
        public Builder putAll(Map<String, ?> values) {
            values.forEach(this::put);
            return this;
        }

        /** Removes a key. */
        public Builder remove(String key) {
            values.remove(key);
            return this;
        }

        public Iso8583Message build() {
            return new Iso8583Message(values);
        }
    }
}
