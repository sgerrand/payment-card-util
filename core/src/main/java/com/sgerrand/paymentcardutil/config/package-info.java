/**
 * The message and file layouts the readers and writers work from.
 *
 * <p>{@link com.sgerrand.paymentcardutil.config.IsoConfig#defaults()} holds the Mastercard IPM
 * layout: which data elements exist, how each is encoded, which ones the CSV tools write out, and
 * where the fields sit in a parameter table record.
 *
 * <p>Nothing here is hard coded into the parsers, so a file that follows a different layout only
 * needs a different config:
 *
 * <pre>{@code
 * IsoConfig config = IsoConfig.defaults().toBuilder()
 *         .field(2, FieldConfig.of("PAN", FieldType.LLVAR, 19))
 *         .build();
 * }</pre>
 */
package com.sgerrand.paymentcardutil.config;
