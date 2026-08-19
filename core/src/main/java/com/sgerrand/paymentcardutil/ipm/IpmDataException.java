package com.sgerrand.paymentcardutil.ipm;

import com.sgerrand.paymentcardutil.PaymentCardException;

/** Thrown when a Mastercard file does not hold what its own framing says it does. */
public class IpmDataException extends PaymentCardException {

    private static final long serialVersionUID = 1L;

    public IpmDataException(String message) {
        super(message);
    }

    public IpmDataException(
            String message, byte[] binaryContext, Integer recordNumber, Throwable cause) {
        super(message, binaryContext, recordNumber, cause);
    }
}
