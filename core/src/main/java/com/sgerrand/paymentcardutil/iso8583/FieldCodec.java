package com.sgerrand.paymentcardutil.iso8583;

import com.sgerrand.paymentcardutil.config.FieldConfig;
import java.util.Map;

/**
 * Pulls structure out of one data element as the message is read.
 *
 * <p>Mastercard packs several things inside a single element: DE 48 carries private data as {@code
 * PDSxxxx} subelements, DE 55 carries chip data as {@code TAGxxxx} records, DE 43 is an address in
 * fixed columns. Which element does that, and how, is a property of the layout rather than of ISO
 * 8583, so the reader does not know about any of them: it asks the layout which codec a field has
 * and hands the field over.
 *
 * <p>A file in a layout this library has never seen needs a codec and a config naming it, not a
 * change to the reader.
 */
public interface FieldCodec {

    /**
     * The extra keys this field contributes to the message.
     *
     * <p>The element's own value is already in the message under its {@code DEn} key by the time
     * this is called, so what comes back is the parts pulled out of it: {@code PDS0158}, {@code
     * TAG9F02}, {@code DE43_NAME}. Returning a {@code DEn} key replaces the value there.
     *
     * <p>What comes back is along for the ride. Writing a message puts the element's own value back
     * on the wire and ignores the parts, so a file read with a codec of your own writes back
     * unchanged. Private data is the one exception in the other direction: {@code PDSxxxx} values
     * are packed into the elements set up to carry them, replacing what was there, which is what
     * cardutil does and the only place it does it.
     *
     * @param bit which data element this is, for error messages
     * @param raw the element's bytes as the file held them
     * @param text those bytes read in the message's character set
     * @param field the element's layout, including {@link FieldConfig#processorConfig()}
     * @return the keys to add, or an empty map to add none
     */
    Map<String, ?> unpack(int bit, byte[] raw, String text, FieldConfig field);

    /**
     * Whether the element's own value is its raw bytes rather than text.
     *
     * <p>Chip data is the one that is: DE 55 is binary, and turning it into text would lose it.
     * Everything else reads as text and is converted to whatever the layout says the field holds.
     */
    default boolean readsRawBytes() {
        return false;
    }
}
