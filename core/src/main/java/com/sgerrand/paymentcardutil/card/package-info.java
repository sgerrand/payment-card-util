/**
 * Card numbers and what can be told from them.
 *
 * <p>{@link com.sgerrand.paymentcardutil.card.Pan} is a card number that has
 * been checked, and that masks itself when printed.
 * {@link com.sgerrand.paymentcardutil.card.Luhn} is the check digit rule,
 * {@link com.sgerrand.paymentcardutil.card.Bin} the issuer prefix, and
 * {@link com.sgerrand.paymentcardutil.card.CardScheme} works out the network
 * from the leading digits.
 */
package com.sgerrand.paymentcardutil.card;
