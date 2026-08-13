/**
 * Reading and writing ISO 8583 messages.
 *
 * <p>{@link com.sgerrand.paymentcardutil.iso8583.Iso8583} turns bytes into an
 * {@link com.sgerrand.paymentcardutil.iso8583.Iso8583Message} and back.
 * {@link com.sgerrand.paymentcardutil.iso8583.Iso8583Options} says which
 * character set, bitmap form and layout to use.
 *
 * <p>Some data elements hold more structure inside them, and that is pulled out
 * as the message is read: Mastercard private data subelements ({@code PDSxxxx}),
 * the parts of the card acceptor name and location in DE 43
 * ({@code DE43_NAME} and friends), and chip data tags in DE 55
 * ({@code TAGxxxx}).
 */
package com.sgerrand.paymentcardutil.iso8583;
