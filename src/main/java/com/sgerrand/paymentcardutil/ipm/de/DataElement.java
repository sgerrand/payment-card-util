package com.sgerrand.paymentcardutil.ipm.de;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Objects;

/**
 * One data element read from an IPM message, kept as raw bytes.
 *
 * <p>The bytes are stored as they appeared in the file. Decoding them into a
 * date, amount or string is left to the caller, because the right reading
 * depends on the element's format in the IPM specification.
 *
 * @param number the data element number, from 1
 * @param value  the raw bytes of the element
 */
public record DataElement(int number, byte[] value) {

    public DataElement {
        if (number < 1 || number > 128) {
            throw new IllegalArgumentException("Data element number out of range: " + number);
        }
        Objects.requireNonNull(value, "value");
        value = value.clone();
    }

    @Override
    public byte[] value() {
        return value.clone();
    }

    /** How many bytes the element holds. */
    public int length() {
        return value.length;
    }

    /** The element read as text in the given character set. */
    public String asText(Charset charset) {
        return new String(value, charset);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DataElement de
                && de.number == number
                && Arrays.equals(de.value, value);
    }

    @Override
    public int hashCode() {
        return 31 * number + Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "DE" + number + "[" + value.length + " bytes]";
    }
}
