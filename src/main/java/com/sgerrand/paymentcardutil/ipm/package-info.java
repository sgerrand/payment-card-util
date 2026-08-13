/**
 * Reading and writing Mastercard IPM (Integrated Product Message) clearing
 * files.
 *
 * <p>An IPM file is a stream of ISO 8583 messages. Each message is wrapped in a
 * Record Descriptor Word that gives its length, and the whole stream is usually
 * split into 1014-byte blocks. {@link com.sgerrand.paymentcardutil.ipm.IpmReader}
 * undoes both wrappers and hands back one message at a time.
 */
package com.sgerrand.paymentcardutil.ipm;
