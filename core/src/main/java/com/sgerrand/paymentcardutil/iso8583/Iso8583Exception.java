package com.sgerrand.paymentcardutil.iso8583;

import com.sgerrand.paymentcardutil.PaymentCardException;

/**
 * Thrown when an ISO 8583 message cannot be read or written as configured.
 */
public class Iso8583Exception extends PaymentCardException {

    private static final long serialVersionUID = 1L;

    public Iso8583Exception(String message) {
        super(message);
    }

    public Iso8583Exception(String message, Throwable cause) {
        super(message, cause);
    }

    public Iso8583Exception(String message, byte[] binaryContext, Throwable cause) {
        super(message, binaryContext, null, cause);
    }
}
