package com.sgerrand.paymentcardutil.ipm.de;

import java.util.Objects;

/**
 * A Private Data Subelement (PDS): a tagged value packed inside a Mastercard
 * private data element such as DE 48 or DE 62.
 *
 * <p>On the wire a PDS is a four digit tag, a three digit length, then that
 * many bytes of data.
 *
 * @param tag  the four digit PDS tag, such as {@code 0158}
 * @param data the subelement data
 */
public record PrivateDataSubelement(String tag, String data) {

    /** How many characters the tag takes up. */
    public static final int TAG_LENGTH = 4;

    /** How many characters the length field takes up. */
    public static final int LENGTH_LENGTH = 3;

    public PrivateDataSubelement {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(data, "data");
        if (tag.length() != TAG_LENGTH) {
            throw new IllegalArgumentException("PDS tag must be " + TAG_LENGTH + " characters: " + tag);
        }
        if (data.length() > 999) {
            throw new IllegalArgumentException("PDS data must be at most 999 characters");
        }
    }

    /** The subelement packed back into its wire form. */
    public String pack() {
        return tag + "%03d".formatted(data.length()) + data;
    }

    @Override
    public String toString() {
        return "PDS" + tag + "[" + data.length() + "]";
    }
}
