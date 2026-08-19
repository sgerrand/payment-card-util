/**
 * Pin blocks and pin verification values.
 *
 * <p>A pin is never sent on its own. It is wrapped into a fixed size block first, so that the same
 * pin does not always encrypt to the same bytes: {@link
 * com.sgerrand.paymentcardutil.pin.Iso0PinBlock} mixes in the card number, {@link
 * com.sgerrand.paymentcardutil.pin.Iso4PinBlock} mixes in random bytes.
 *
 * <p>{@link com.sgerrand.paymentcardutil.pin.VisaPvv} works out the value an issuer stores instead
 * of the pin itself.
 *
 * <p>Nothing here holds a pin any longer than it has to, and no class in this package puts a pin in
 * its {@code toString()}.
 */
package com.sgerrand.paymentcardutil.pin;
