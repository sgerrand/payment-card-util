package com.sgerrand.paymentcardutil.iso8583;

import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Times reading and writing one clearing message.
 *
 * <p>Nothing runs this during the build: it has no tests in it, and it is here so that a claim
 * about speed can be checked rather than believed. Reading and writing IPM files is all these tools
 * do, and a clearing file is millions of records, so a microsecond a record is a minute an hour of
 * work.
 *
 * <pre>{@code
 * mvn -pl core test-compile
 * java -cp core/target/classes:core/target/test-classes \
 *     com.sgerrand.paymentcardutil.iso8583.ParseWriteBenchmark
 * }</pre>
 *
 * <p>To measure a change, build the version to compare against somewhere else and run the same
 * class against each in turn:
 *
 * <pre>{@code
 * git worktree add /tmp/before payment-card-util-v0.1.0
 * (cd /tmp/before && mvn -q -pl core test-compile)
 * java -cp /tmp/before/core/target/classes:/tmp/before/core/target/test-classes \
 *     com.sgerrand.paymentcardutil.iso8583.ParseWriteBenchmark before
 * }</pre>
 *
 * <p>This is a stopwatch, not a laboratory. It warms the code up, runs each side several times and
 * reports the best, which is enough to see a change of a few per cent and not enough to argue about
 * one. Every result is added into a checksum that gets printed, so that the work cannot be
 * optimised away as unused.
 */
final class ParseWriteBenchmark {

    private static final int WARMUP = 50_000;
    private static final int RUNS = 300_000;
    private static final int TRIALS = 3;

    private ParseWriteBenchmark() {}

    /** A message of the shape a Mastercard clearing file is full of. */
    private static Iso8583Message clearingMessage() {
        return Iso8583Message.builder()
                .mti("1240")
                .de(2, "4444555566667777")
                .de(3, "000000")
                .de(4, 12345L)
                .de(12, LocalDateTime.of(2020, 3, 4, 5, 6, 7))
                .de(22, "F00101")
                .de(24, "200")
                .de(26, "5999")
                .de(31, "REF00000001")
                .de(33, "12345678")
                .de(37, "REF00000001")
                .de(38, "AUTH01")
                .de(40, "999")
                .de(41, "TERM0001")
                .de(42, "CARD ACCEPTOR  ")
                .de(49, "840")
                .de(50, "840")
                .de(55, HexFormat.of().parseHex("9f0206000000001000"))
                .pds(23, "SOMETHING")
                .pds(158, "0000000000")
                .build();
    }

    public static void main(String[] args) {
        String label = args.length > 0 ? args[0] : "now";
        Iso8583Options options = Iso8583Options.defaults();
        Iso8583Message message = clearingMessage();
        byte[] record = Iso8583.serialize(message, options);

        long checksum = 0;
        for (int i = 0; i < WARMUP; i++) {
            checksum += Iso8583.parse(record, options).values().size();
            checksum += Iso8583.serialize(message, options).length;
        }

        long parse = Long.MAX_VALUE;
        long write = Long.MAX_VALUE;
        for (int trial = 0; trial < TRIALS; trial++) {
            long start = System.nanoTime();
            for (int i = 0; i < RUNS; i++) {
                checksum += Iso8583.parse(record, options).values().size();
            }
            parse = Math.min(parse, System.nanoTime() - start);

            start = System.nanoTime();
            for (int i = 0; i < RUNS; i++) {
                checksum += Iso8583.serialize(message, options).length;
            }
            write = Math.min(write, System.nanoTime() - start);
        }

        System.out.printf(
                Locale.ROOT,
                "%-10s record %d bytes  parse %.2f us  write %.2f us  (checksum %d)%n",
                label,
                record.length,
                parse / 1000.0 / RUNS,
                write / 1000.0 / RUNS,
                checksum);
    }
}
