package com.sgerrand.paymentcardutil.iso8583;

import com.sgerrand.paymentcardutil.config.DateFormats;
import com.sgerrand.paymentcardutil.config.FieldConfig;
import com.sgerrand.paymentcardutil.config.FieldProcessors;
import com.sgerrand.paymentcardutil.config.IsoConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes ISO 8583 messages.
 *
 * <p>Read a message:
 *
 * <pre>{@code
 * Iso8583Message message = Iso8583.parse(bytes);
 * message.mti();      // 1144
 * message.text(2);    // "4444555566667777"
 * message.pds(158);   // a Mastercard private subelement
 * }</pre>
 *
 * <p>Write one:
 *
 * <pre>{@code
 * byte[] bytes = Iso8583.serialize(Iso8583Message.builder()
 *         .mti("1144")
 *         .de(2, "4444555566667777")
 *         .build());
 * }</pre>
 *
 * <p>Pass {@link Iso8583Options} for a different character set, a hex bitmap or a different message
 * layout.
 *
 * <p>How many digits and bytes each field takes comes from the layout in {@link IsoConfig}, not
 * from this class.
 */
public final class Iso8583 {

    private static final HexFormat HEX = HexFormat.of();

    /** Bytes taken by the message type indicator. */
    private static final int MTI_LENGTH = 4;

    /** Bytes taken by a binary bitmap: both halves, always. */
    private static final int BITMAP_LENGTH = 16;

    /** Characters taken by the same bitmap written as hex. */
    private static final int HEX_BITMAP_LENGTH = BITMAP_LENGTH * 2;

    /** Highest data element read or written. Matches cardutil, which stops at 127. */
    private static final int MAX_FIELD = 127;

    private Iso8583() {}

    /** Reads a message using the default settings. */
    public static Iso8583Message parse(byte[] message) {
        return parse(message, Iso8583Options.defaults());
    }

    /**
     * Reads a message.
     *
     * @throws Iso8583Exception if the message does not match the layout
     */
    public static Iso8583Message parse(byte[] message, Iso8583Options options) {
        int headerLength = MTI_LENGTH + (options.hexBitmap() ? HEX_BITMAP_LENGTH : BITMAP_LENGTH);
        if (message.length < headerLength) {
            throw new Iso8583Exception(
                    "Message is "
                            + message.length
                            + " bytes, too short for a message type and bitmap",
                    message,
                    null);
        }

        Bitmap bitmap = readBitmap(message, options);
        // The elements are read out of the record where they lie. Copying the
        // body out first would mean a second copy of every record in the file,
        // and a clearing file is millions of them.
        int bodyLength = message.length - headerLength;

        Iso8583Message.Builder builder = Iso8583Message.builder();
        builder.put(Iso8583Message.MTI_KEY, readMti(message, options.charset()));

        int pointer = 0;
        for (int bit = 2; bit <= MAX_FIELD; bit++) {
            if (!bitmap.isSet(bit)) {
                continue;
            }
            int de = bit;
            FieldConfig field =
                    options.config()
                            .field(de)
                            .orElseThrow(
                                    () ->
                                            new Iso8583Exception(
                                                    "No layout configured for DE" + de,
                                                    message,
                                                    null));
            try {
                pointer += readField(de, field, message, headerLength, pointer, options, builder);
            } catch (Iso8583Exception problem) {
                throw withBytes(problem, message);
            }
        }

        if (pointer != bodyLength) {
            throw new Iso8583Exception(
                    "Message data is the wrong length. The bitmap accounts for "
                            + pointer
                            + " bytes, the message holds "
                            + bodyLength,
                    message,
                    null);
        }
        return builder.build();
    }

    /** Writes a message using the default settings. */
    public static byte[] serialize(Iso8583Message message) {
        return serialize(message, Iso8583Options.defaults());
    }

    /**
     * Writes a message.
     *
     * <p>Any {@code PDSxxxx} values are packed into the data elements set up to carry them, in tag
     * order. Where a message holds both {@code PDSxxxx} values and the data element that carries
     * them, the packed values win.
     *
     * <p>The message needs a message type indicator of four characters. cardutil writes one
     * without, and then cannot read what it wrote; this refuses instead.
     *
     * @throws Iso8583Exception if a value does not fit its field, or the message type indicator is
     *     missing or the wrong length
     */
    public static byte[] serialize(Iso8583Message message, Iso8583Options options) {
        Map<String, Object> values = new LinkedHashMap<>(message.values());
        packPdsFields(values, options.config());

        Bitmap bitmap = Bitmap.emptyWithSecondary();
        // Bit 1 always on: every message this writes carries a full 16 byte bitmap.
        bitmap.set(1);

        ByteArrayOutputStream body = new ByteArrayOutputStream();

        for (int bit = 2; bit <= MAX_FIELD; bit++) {
            Object value = values.get(Iso8583Message.deKey(bit));
            if (isAbsent(value)) {
                continue;
            }
            int de = bit;
            FieldConfig field =
                    options.config()
                            .field(de)
                            .orElseThrow(
                                    () -> new Iso8583Exception("No layout configured for DE" + de));
            bitmap.set(de);
            writeField(body, field, value, options.charset());
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(checkMti(values.get(Iso8583Message.MTI_KEY)).getBytes(options.charset()));
        out.writeBytes(writeBitmap(bitmap, options));
        try {
            body.writeTo(out);
        } catch (IOException e) {
            throw new AssertionError("Writing one byte array into another cannot fail", e);
        }
        return out.toByteArray();
    }

    /**
     * Checks the message type indicator will read back, and returns it.
     *
     * <p>cardutil writes a message that has no message type indicator, or one that is not four
     * characters, and then cannot read the result: its own reader answers "Failed decoding MTI
     * field". Since the type indicator sits in front of the bitmap, a wrong length shifts
     * everything after it along, so the record is not merely missing a field, it is unreadable.
     * Refusing beats writing a record nothing can make sense of.
     *
     * <p>What is allowed here is what {@link #parse} allows, so a message read from a file can
     * always be written back.
     *
     * @throws Iso8583Exception if the message type indicator is missing, the wrong length, or not a
     *     number
     */
    private static String checkMti(Object value) {
        if (value == null) {
            throw new Iso8583Exception("The message has no message type indicator");
        }
        String mti = String.valueOf(value);
        if (mti.length() != MTI_LENGTH) {
            throw new Iso8583Exception(
                    "The message type indicator is "
                            + mti.length()
                            + " characters, and has to be "
                            + MTI_LENGTH
                            + ": "
                            + mti);
        }
        try {
            Integer.parseInt(mti.trim());
        } catch (NumberFormatException e) {
            throw new Iso8583Exception("Message type indicator is not a number: " + mti, e);
        }
        return mti;
    }

    /**
     * The same failure, carrying the bytes it happened in.
     *
     * <p>A codec pulling a field apart sees only the field, so what it throws names the trouble but
     * cannot say where in the record it sits. Reporting a bad file is most of what these exceptions
     * are for, so the reader that does have the bytes puts them in.
     */
    private static Iso8583Exception withBytes(Iso8583Exception problem, byte[] message) {
        return problem.binaryContext().isPresent()
                ? problem
                : new Iso8583Exception(problem.getMessage(), message, problem);
    }

    private static Bitmap readBitmap(byte[] message, Iso8583Options options) {
        if (!options.hexBitmap()) {
            return Bitmap.of(
                    java.util.Arrays.copyOfRange(message, MTI_LENGTH, MTI_LENGTH + BITMAP_LENGTH));
        }
        // Hex bitmaps are always ASCII digits, whatever the message encoding is.
        String hex =
                new String(message, MTI_LENGTH, HEX_BITMAP_LENGTH, StandardCharsets.ISO_8859_1);
        try {
            return Bitmap.of(HEX.parseHex(hex));
        } catch (IllegalArgumentException e) {
            throw new Iso8583Exception("Bitmap is not valid hex: " + hex, message, e);
        }
    }

    private static byte[] writeBitmap(Bitmap bitmap, Iso8583Options options) {
        byte[] bytes = bitmap.toByteArray();
        if (!options.hexBitmap()) {
            return bytes;
        }
        return HEX.formatHex(bytes).getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String readMti(byte[] message, Charset charset) {
        String mti = new String(message, 0, MTI_LENGTH, charset);
        try {
            Integer.parseInt(mti.trim());
        } catch (NumberFormatException e) {
            throw new Iso8583Exception(
                    "Message type indicator is not a number: " + mti, message, e);
        }
        return mti;
    }

    /**
     * Reads one data element into {@code builder}.
     *
     * <p>The element is read where it lies in {@code message}: {@code bodyStart} is where the data
     * elements begin, and {@code pointer} how far into them this one is.
     *
     * @return how many bytes of the message body the element took up
     */
    private static int readField(
            int bit,
            FieldConfig field,
            byte[] message,
            int bodyStart,
            int pointer,
            Iso8583Options options,
            Iso8583Message.Builder builder) {
        int lengthSize = field.lengthSize();
        int fieldLength = field.length();

        if (lengthSize > 0) {
            if (bodyStart + pointer + lengthSize > message.length) {
                throw new Iso8583Exception(
                        "DE" + bit + " length runs past the end of the message", message, null);
            }
            String lengthText =
                    new String(message, bodyStart + pointer, lengthSize, options.charset());
            try {
                fieldLength = Integer.parseInt(lengthText.trim());
            } catch (NumberFormatException e) {
                throw new Iso8583Exception(
                        "DE" + bit + " has a length that is not a number: " + lengthText,
                        message,
                        e);
            }
            if (fieldLength < 0) {
                throw new Iso8583Exception(
                        "DE" + bit + " has a negative length: " + fieldLength, message, null);
            }
        }

        int start = bodyStart + pointer + lengthSize;
        // Take what is there. A field running off the end is reported by the
        // whole-message length check once every element has been walked.
        int end = Math.min(start + fieldLength, message.length);

        // What a field holds beyond its own value is the layout's business, not
        // the reader's: the layout names a processor and that decides both how
        // the value is read and what is pulled out of it.
        FieldCodec codec = options.codec(field.processor());

        if (codec == FieldCodecs.NO_EXTRAS && start <= message.length) {
            // Nothing will look at the element's bytes, so it is read straight
            // out of the record. Most elements are this one, and a copy each
            // adds up to one per element per record.
            String text = new String(message, start, end - start, options.charset());
            builder.put(Iso8583Message.deKey(bit), toValue(bit, text, field));
            return fieldLength + lengthSize;
        }

        // A start past the end of the record gives a run of zero bytes, the
        // length of the overshoot. That is what copyOfRange does, and what the
        // rest of the read is written against; see ReadPastTheEndTest.
        byte[] raw =
                java.util.Arrays.copyOfRange(
                        message, Math.min(start, message.length), Math.max(start, end));
        String text = new String(raw, options.charset());

        builder.put(
                Iso8583Message.deKey(bit), codec.readsRawBytes() ? raw : toValue(bit, text, field));
        builder.putAll(codec.unpack(bit, raw, text, field));

        return fieldLength + lengthSize;
    }

    /** Writes one data element onto the end of the message body. */
    private static void writeField(
            ByteArrayOutputStream out, FieldConfig field, Object value, Charset charset) {
        // A value that is still bytes came from a codec that said it reads raw
        // bytes, so it goes back out as it came in.
        if (value instanceof byte[] bytes) {
            int length =
                    field.lengthSize() > 0 ? bytes.length : Math.min(field.length(), bytes.length);
            if (field.lengthSize() > 0) {
                out.writeBytes(lengthPrefix(length, field.lengthSize(), charset));
            }
            out.write(bytes, 0, length);
            return;
        }

        String text = toText(value, field);
        if (field.lengthSize() > 0) {
            out.writeBytes(lengthPrefix(text.length(), field.lengthSize(), charset));
            out.writeBytes(text.getBytes(charset));
            return;
        }

        // Fixed field: cut to length, then pad on the right with spaces.
        String fixed = text.length() > field.length() ? text.substring(0, field.length()) : text;
        out.writeBytes(padRight(fixed, field.length()).getBytes(charset));
    }

    private static byte[] lengthPrefix(int length, int lengthSize, Charset charset) {
        String text = Integer.toString(length);
        if (text.length() > lengthSize) {
            throw new Iso8583Exception(
                    "Field is " + length + " long, too long for a " + lengthSize + " digit length");
        }
        return padLeftZeros(text, lengthSize).getBytes(charset);
    }

    /** Turns a stored value into the text that goes on the wire. */
    private static String toText(Object value, FieldConfig field) {
        return switch (field.valueType()) {
            case LONG -> padLeftZeros(Long.toString(asLong(value)), field.length());
            case DECIMAL ->
                    padLeftZeros(
                            asDecimal(value)
                                    .setScale(6, java.math.RoundingMode.HALF_UP)
                                    .toPlainString(),
                            field.length());
            case DATETIME -> DateFormats.of(field.dateFormatOrDefault()).format(asDateTime(value));
            case TEXT -> String.valueOf(value);
        };
    }

    /** Turns text off the wire into the stored value. */
    private static Object toValue(int bit, String text, FieldConfig field) {
        try {
            return switch (field.valueType()) {
                case LONG -> Long.parseLong(text.trim());
                case DECIMAL -> new BigDecimal(text.trim());
                case DATETIME ->
                        LocalDateTime.parse(text, DateFormats.of(field.dateFormatOrDefault()));
                case TEXT -> text;
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new Iso8583Exception(
                    "DE" + bit + " does not hold a " + field.valueType() + ": " + text, e);
        }
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new Iso8583Exception("Not a whole number: " + value, e);
        }
    }

    private static BigDecimal asDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new Iso8583Exception("Not a number: " + value, e);
        }
    }

    private static LocalDateTime asDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.time.LocalDate date) {
            return date.atStartOfDay();
        }
        String text = String.valueOf(value).trim();
        try {
            return LocalDateTime.parse(text.replace(' ', 'T'));
        } catch (DateTimeParseException e) {
            try {
                return java.time.LocalDate.parse(text).atStartOfDay();
            } catch (DateTimeParseException nested) {
                throw new Iso8583Exception("Not a date this can read: " + text, nested);
            }
        }
    }

    /**
     * Moves every {@code PDSxxxx} value into the data elements set up to carry them, lowest
     * numbered element first.
     */
    private static void packPdsFields(Map<String, Object> values, IsoConfig config) {
        List<String> groups = PdsCodec.pack(values);
        if (groups.isEmpty()) {
            return;
        }
        List<Integer> carriers = config.bitsWithProcessor(FieldProcessors.PDS);
        if (groups.size() > carriers.size()) {
            throw new Iso8583Exception(
                    "The message holds more private data than fits: "
                            + groups.size()
                            + " data elements needed, the layout has "
                            + carriers.size());
        }
        for (int i = 0; i < groups.size(); i++) {
            values.put(Iso8583Message.deKey(carriers.get(i)), groups.get(i));
        }
    }

    /**
     * Whether a value should be left out of the message.
     *
     * <p>Missing and empty values are left out; a zero is written, since zero is a real amount.
     */
    private static boolean isAbsent(Object value) {
        return value == null
                || (value instanceof CharSequence text && text.isEmpty())
                || (value instanceof byte[] bytes && bytes.length == 0);
    }

    private static String padLeftZeros(String text, int width) {
        if (text.length() >= width) {
            return text;
        }
        boolean negative = text.startsWith("-");
        String digits = negative ? text.substring(1) : text;
        String padded = "0".repeat(width - text.length()) + digits;
        return negative ? "-" + padded : padded;
    }

    private static String padRight(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }
}
