/**
 * Reading and writing Mastercard clearing and parameter files.
 *
 * <p>Mastercard files come in two layers. The outer layer is VBS: a run of
 * records, each with a 4 byte length in front of it, ending with a zero length
 * record. On top of that, files are often cut into 1014 byte blocks with two
 * filler bytes on the end of each.
 *
 * <p>{@link com.sgerrand.paymentcardutil.ipm.VbsReader} and
 * {@link com.sgerrand.paymentcardutil.ipm.VbsWriter} handle the outer layer.
 * {@link com.sgerrand.paymentcardutil.ipm.IpmReader} and
 * {@link com.sgerrand.paymentcardutil.ipm.IpmWriter} add ISO 8583 on top, which
 * is what an IPM clearing file holds.
 * {@link com.sgerrand.paymentcardutil.ipm.IpmParamReader} reads parameter
 * extracts, whose records are plain text rather than messages.
 *
 * <p>If nobody recorded how a file was written,
 * {@link com.sgerrand.paymentcardutil.ipm.IpmInfo#inspect} works out its
 * character set and blocking by looking at it.
 */
package com.sgerrand.paymentcardutil.ipm;
