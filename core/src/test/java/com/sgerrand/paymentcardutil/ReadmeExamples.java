package com.sgerrand.paymentcardutil;

import com.sgerrand.paymentcardutil.card.CardScheme;
import com.sgerrand.paymentcardutil.card.Pan;
import com.sgerrand.paymentcardutil.config.IsoConfig;
import com.sgerrand.paymentcardutil.ipm.IpmInfo;
import com.sgerrand.paymentcardutil.ipm.IpmReader;
import com.sgerrand.paymentcardutil.ipm.IpmWriter;
import com.sgerrand.paymentcardutil.iso8583.Iso8583;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Message;
import com.sgerrand.paymentcardutil.iso8583.Iso8583Options;
import com.sgerrand.paymentcardutil.pin.Iso0PinBlock;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Every Java example in README.md, copied out.
 *
 * <p>Nothing here is run: it is here so that a change to the library that leaves the README behind
 * stops the build. The examples went stale silently before this existed.
 */
final class ReadmeExamples {

    void block0(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path);
                IpmReader reader = IpmReader.blocked(in)) {
            for (Iso8583Message message : reader) {
                message.mti(); // 1240
                message.text(2); // the card number
                message.number(4); // the amount, as a long
                message.dateTime(12); // the local transaction time
                message.pds(158); // a Mastercard private subelement
                message.iccTag("9F02"); // a chip tag out of DE 55
            }
        }
    }

    void block1(Path path) throws Exception {
        IpmInfo info = IpmInfo.inspect(Files.newInputStream(path));
        info.valid();
        info.blocked();
        info.encoding();
        info.charset();
    }

    void block2(Path path) throws Exception {
        try (OutputStream out = Files.newOutputStream(path);
                IpmWriter writer = IpmWriter.blocked(out)) {
            writer.write(
                    Iso8583Message.builder()
                            .mti("1240")
                            .de(2, "4444555566667777")
                            .de(4, 12345L)
                            .de(12, LocalDateTime.now())
                            .pds(158, "0000000000")
                            .build());
        }
    }

    void block3(byte[] bytes) {
        Iso8583Message message = Iso8583.parse(bytes);
        byte[] out = Iso8583.serialize(message);
    }

    void block4(byte[] bytes, IsoConfig myConfig) {
        Iso8583Options options =
                Iso8583Options.defaults()
                        .withCharset(Iso8583Options.EBCDIC_CP500)
                        .withConfig(myConfig);
        Iso8583Message message = Iso8583.parse(bytes, options);
    }

    void block5() {
        Pan pan = Pan.parse("4111 1111 1111 1111");
        pan.isLuhnValid();
        CardScheme scheme = pan.scheme();
        pan.bin();
        pan.toString();
    }

    void block6(String ppk, String pvvKey, byte[] bytes, String cardNumber) {
        Iso0PinBlock block = new Iso0PinBlock("1234", "1111222233334444");
        block.toBytes();
        block.toEncryptedBytes(ppk);
        block.toPvv(pvvKey);

        Iso0PinBlock.fromEncryptedBytes(bytes, cardNumber, ppk).pin();
    }

    void block7(IsoConfig myLayout) {
        Iso8583Options options =
                Iso8583Options.defaults()
                        .withConfig(myLayout)
                        .withCodec(
                                "BRANCH-CODE",
                                (bit, raw, text, field) ->
                                        Map.of("DE" + bit + "_BRANCH", text.substring(0, 4)));
    }
}
