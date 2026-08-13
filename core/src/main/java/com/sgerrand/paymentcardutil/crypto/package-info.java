/**
 * Building, checking and moving payment keys.
 *
 * <p>{@link com.sgerrand.paymentcardutil.crypto.Keys} combines key components
 * into a working key, works out check values, and encrypts a key for sending.
 * {@link com.sgerrand.paymentcardutil.crypto.DesKeys} is the Triple DES and AES
 * layer underneath, which the pin block classes also use.
 *
 * <p>Everything here uses ECB mode with no padding, which is what the payment
 * standards call for when encrypting a single fixed size block such as a key or
 * a pin block. It is not a general purpose encryption toolkit and should not be
 * used as one.
 */
package com.sgerrand.paymentcardutil.crypto;
