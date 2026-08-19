package com.sgerrand.paymentcardutil.config;

/** How a data element's length is worked out when reading a message. */
public enum FieldType {

    /** The field is always {@link FieldConfig#length()} bytes long. */
    FIXED(0),

    /** The field is prefixed by a 2 digit length. */
    LLVAR(2),

    /** The field is prefixed by a 3 digit length. */
    LLLVAR(3);

    private final int lengthSize;

    FieldType(int lengthSize) {
        this.lengthSize = lengthSize;
    }

    /** How many bytes the length prefix takes up: 0 for a fixed field. */
    public int lengthSize() {
        return lengthSize;
    }
}
