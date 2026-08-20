package com.sgerrand.paymentcardutil.iso8583;

import com.sgerrand.paymentcardutil.config.DateFormats;
import com.sgerrand.paymentcardutil.config.FieldConfig;
import com.sgerrand.paymentcardutil.config.FieldProcessor;
import com.sgerrand.paymentcardutil.config.IsoConfig;
import java.io.ByteArrayOutputStream;
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
        byte[] body = java.util.Arrays.copyOfRange(message, headerLength, message.length);

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
                pointer += readField(de, field, body, pointer, options, builder);
            } catch (Iso8583Exception problem) {
                throw withBytes(problem, message);
            }
        }

        if (pointer != body.length) {
            throw new Iso8583Exception(
                    "Message data is the wrong length. The bitmap accounts for "
                            + pointer
                            + " bytes, the message holds "
                            + body.length,
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
            body.writeBytes(writeField(field, value, options.charset()));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(checkMti(values.get(Iso8583Message.MTI_KEY)).getBytes(options.charset()));
        out.writeBytes(writeBitmap(bitmap, options));
        out.writeBytes(body.toByteArray());
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
     * @return how many bytes of {@code body} the element took up
     */
    private static int readField(
            int bit,
            FieldConfig field,
            byte[] body,
            int pointer,
            Iso8583Options options,
            Iso8583Message.Builder builder) {
        int lengthSize = field.lengthSize();
        int fieldLength = field.length();

        if (lengthSize > 0) {
            if (pointer + lengthSize > body.length) {
                throw new Iso8583Exception(
                        "DE" + bit + " length runs past the end of the message", body, null);
            }
            String lengthText = new String(body, pointer, lengthSize, options.charset());
            try {
                fieldLength = Integer.parseInt(lengthText.trim());
            } catch (NumberFormatException e) {
                throw new Iso8583Exception(
                        "DE" + bit + " has a length that is not a number: " + lengthText, body, e);
            }
            if (fieldLength < 0) {
                throw new Iso8583Exception(
                        "DE" + bit + " has a negative length: " + fieldLength, body, null);
            }
        }

        int start = pointer + lengthSize;
        // Take what is there. A field running off the end is reported by the
        // whole-message length check once every element has been walked.
        int end = Math.min(start + fieldLength, body.length);
        byte[] raw =
                java.util.Arrays.copyOfRange(
                        body, Math.min(start, body.length), Math.max(start, end));

        if (field.processor() == FieldProcessor.ICC) {
            builder.put(Iso8583Message.deKey(bit), raw);
            builder.putAll(IccCodec.unpack(bit, raw, field.processorConfig()));
            return fieldLength + lengthSize;
        }

        // PAN and PAN_PREFIX only mark a field as holding a card number; they
        // do not touch the value. cardutil masks and truncates here, which
        // makes a file it has read impossible to write back unchanged, and
        // leaves a caller no way to ask for the real value. Masking belongs to
        // whatever shows the data, so mci-ipm-to-csv does it and --unmask-pan
        // turns it off.
        String text = new String(raw, options.charset());

        builder.put(Iso8583Message.deKey(bit), toValue(bit, text, field));

        switch (field.processor()) {
            case PDS -> builder.putAll(PdsCodec.unpack(text));
            case DE43 -> builder.putAll(De43Codec.unpack(text, field.processorConfig()));
            default -> {
                // Nothing further to pull out of this field.
            }
        }
        return fieldLength + lengthSize;
    }

    private static byte[] writeField(FieldConfig field, Object value, Charset charset) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        if (value instanceof byte[] bytes) {
            int length =
                    field.lengthSize() > 0 ? bytes.length : Math.min(field.length(), bytes.length);
            if (field.lengthSize() > 0) {
                out.writeBytes(lengthPrefix(length, field.lengthSize(), charset));
            }
            out.write(bytes, 0, length);
            return out.toByteArray();
        }

        String text = toText(value, field);
        if (field.lengthSize() > 0) {
            out.writeBytes(lengthPrefix(text.length(), field.lengthSize(), charset));
            out.writeBytes(text.getBytes(charset));
            return out.toByteArray();
        }

        // Fixed field: cut to length, then pad on the right with spaces.
        String fixed = text.length() > field.length() ? text.substring(0, field.length()) : text;
        out.writeBytes(padRight(fixed, field.length()).getBytes(charset));
        return out.toByteArray();
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
        List<Integer> carriers = config.bitsWithProcessor(FieldProcessor.PDS);
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
