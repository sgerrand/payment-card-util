package com.sgerrand.paymentcardutil.ipm;

import com.sgerrand.paymentcardutil.iso8583.Bitmap;
import com.sgerrand.paymentcardutil.iso8583.Mti;

import java.util.Arrays;
import java.util.Objects;

/**
 * One message read from an IPM file.
 *
 * <p>The message type and bitmap are parsed. The rest of the message is kept as
 * raw bytes in {@link #body()}, because reading a data element needs its format
 * from the IPM specification.
 *
 * @param mti    the message type indicator
 * @param bitmap the bitmap saying which data elements follow
 * @param body   the bytes after the bitmap
 */
public record IpmMessage(Mti mti, Bitmap bitmap, byte[] body) {

    public IpmMessage {
        Objects.requireNonNull(mti, "mti");
        Objects.requireNonNull(bitmap, "bitmap");
        Objects.requireNonNull(body, "body");
        body = body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    /** The data element numbers this message says it holds. */
    public int[] presentFields() {
        return bitmap.fields();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof IpmMessage m
                && m.mti.equals(mti)
                && Arrays.equals(m.bitmap.toByteArray(), bitmap.toByteArray())
                && Arrays.equals(m.body, body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mti, Arrays.hashCode(bitmap.toByteArray()), Arrays.hashCode(body));
    }

    @Override
    public String toString() {
        return "IpmMessage[mti=" + mti + ", fields=" + Arrays.toString(presentFields()) + "]";
    }
}
