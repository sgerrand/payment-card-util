package com.sgerrand.paymentcardutil.config;

/**
 * The field processor names cardutil uses.
 *
 * <p>A processor says what is packed inside a data element, and a layout names it as a string, the
 * way a cardutil config file does. These are the names cardutil itself knows; a layout is free to
 * name something else and supply the codec that reads it, which is why this is a handful of
 * constants rather than a closed set of values.
 *
 * <p>A field naming a processor nothing can read is left as it stands, holding its own value and
 * contributing nothing further. cardutil does the same, and a name it does not recognise simply
 * falls through its own list.
 */
public final class FieldProcessors {

    /** Break the field into Mastercard private data subelements, keyed {@code PDSxxxx}. */
    public static final String PDS = "PDS";

    /** Break DE 43 into name and address parts using a regular expression. */
    public static final String DE43 = "DE43";

    /** Break the field into ICC tags, keyed {@code TAGxxxx}. The field keeps its raw bytes. */
    public static final String ICC = "ICC";

    /**
     * The field holds a card number.
     *
     * <p>A marker, not a change: the value is read as it stands. Tools that show the data mask it,
     * which is what {@code mci-ipm-to-csv} does unless {@code --unmask-pan} is passed.
     */
    public static final String PAN = "PAN";

    /**
     * The field holds the first nine digits of a card number.
     *
     * <p>A marker, as {@link #PAN} is. The value is read as it stands, however long the file wrote
     * it.
     */
    public static final String PAN_PREFIX = "PAN-PREFIX";

    private FieldProcessors() {}
}
