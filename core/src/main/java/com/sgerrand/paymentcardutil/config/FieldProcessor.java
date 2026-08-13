package com.sgerrand.paymentcardutil.config;

/**
 * Extra handling applied to a field once its bytes have been read.
 */
public enum FieldProcessor {

    /** Nothing extra. */
    NONE,

    /** Break the field into Mastercard private data subelements, keyed {@code PDSxxxx}. */
    PDS,

    /** Break DE 43 into name and address parts using a regular expression. */
    DE43,

    /** Break the field into ICC tags, keyed {@code TAGxxxx}. */
    ICC,

    /** Mask the value, keeping the first six and last four digits. */
    PAN,

    /** Keep only the first nine digits of the value. */
    PAN_PREFIX;

    /**
     * Maps a cardutil {@code field_processor} name onto a processor.
     *
     * @param name {@code PDS}, {@code DE43}, {@code ICC}, {@code PAN} or
     *             {@code PAN-PREFIX}; {@code null} means none
     * @throws IllegalArgumentException if the name is not one of those
     */
    public static FieldProcessor fromName(String name) {
        if (name == null) {
            return NONE;
        }
        return switch (name) {
            case "PDS" -> PDS;
            case "DE43" -> DE43;
            case "ICC" -> ICC;
            case "PAN" -> PAN;
            case "PAN-PREFIX" -> PAN_PREFIX;
            default -> throw new IllegalArgumentException("Unknown field processor: " + name);
        };
    }
}
