package com.sgerrand.paymentcardutil.crypto;

import com.sgerrand.paymentcardutil.PaymentCardException;

/**
 * Thrown when a key or pin block operation cannot be carried out.
 */
public class CryptoException extends PaymentCardException {

    private static final long serialVersionUID = 1L;

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
