# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Java port of the Python [cardutil](https://github.com/adelosa/cardutil) package
(MIT, by Anthony Delosa), pinned to cardutil **0.7.3**. Payment card work: ISO
8583 messages, Mastercard IPM clearing and parameter files, card numbers, pin
blocks, key handling.

The port keeps cardutil's behaviour but not its API: cardutil passes dictionaries
around, this uses typed classes.

## Commands

```sh
mvn verify                      # build and test everything
mvn -pl core test               # one module
mvn -pl core test -Dtest=Iso8583ParityTest          # one test class
mvn -pl core test -Dtest='Iso8583ParityTest#roundTrip'   # one test method
mvn test -Dtest=CsvTest -Dsurefire.failIfNoSpecifiedTests=false   # class in an unknown module
mvn package                     # also builds the shaded CLI jar
```

Running a CLI tool:

```sh
java -jar cli/target/payment-card-util-cli-*-all.jar --help
java -jar cli/target/payment-card-util-cli-*-all.jar mci-ipm-to-csv clearing.ipm
```

`-pl cli` on its own works once `mvn install` has put `core` in the local repo.
Adding `-am` rebuilds `core` too, which then needs
`-Dsurefire.failIfNoSpecifiedTests=false` when `-Dtest` names a CLI-only class.

## Correctness is defined by cardutil, not by hand written expectations

This is the single most important thing about this repository.

`core/src/test/resources/vectors/cardutil.json` holds 56 cases — messages, files,
pin blocks, keys, parameter tables, hex dumps — each produced by **running the
Python package**. The parity tests (`*ParityTest`, `@TestFactory` over the vectors)
replay them and compare byte for byte.

So when a parity test fails, the Java side is wrong. Do not adjust a vector to
make a test pass. Vectors change only by regenerating them against a new cardutil
release.

Two files are generated and must never be hand edited:

| File | Generator |
| --- | --- |
| `core/.../config/DefaultConfig.java` | `tools/gen_config.py` |
| `core/src/test/resources/vectors/cardutil.json` | `tools/gen_vectors.py` |

To regenerate, from the repository root (the root has no `cardutil/` directory,
so the installed package is not shadowed):

```sh
python -m venv .venv && .venv/bin/pip install 'cardutil[crypto]'
.venv/bin/python tools/gen_config.py core/src/main/java/com/sgerrand/paymentcardutil/config/DefaultConfig.java
.venv/bin/python tools/gen_vectors.py core/src/test/resources/vectors/cardutil.json
```

Both generators are reproducible: run against 0.7.3 they produce no diff. The
`cardutil drift` workflow relies on that, running weekly to regenerate against the
newest cardutil and fail if anything moved.

## Layout

Two Maven modules, and the split is a constraint rather than tidiness:

- **`core`** (`com.sgerrand:payment-card-util`) has **no runtime dependencies**,
  matching cardutil's own promise. Jackson appears in `core/pom.xml` at
  **test scope only**, to read the vector files. Do not add a compile-scope
  dependency here.
- **`cli`** (`com.sgerrand:payment-card-util-cli`) holds picocli and Jackson and
  the five commands, so library users inherit nothing.

Packages under `com.sgerrand.paymentcardutil`: `card`, `iso8583`, `ipm`, `pin`,
`crypto`, `config`, and `cli` in the other module.

## Architecture

**Parsing is driven by config, never hardcoded.** `IsoConfig` holds a
`FieldConfig` per data element (length rule, value type, date format, processor).
`Iso8583` reads that; it knows nothing about DE 48 or DE 55 specifically. A file
in another layout needs a different `IsoConfig`, not new parser code. The CLI can
build one from a JSON file in cardutil's own shape (`ConfigFiles`).

**Message values keep cardutil's string keys.** `Iso8583Message` stores a
`Map<String, Object>` keyed `MTI`, `DE2`, `PDS0158`, `TAG9F02`, `DE43_NAME`,
`ICC_DATA`, with typed accessors (`text`, `number`, `dateTime`, `pds`, `iccTag`)
layered over it. The key naming is not cosmetic — parity depends on it, and the
CSV column list in the config refers to those keys.

**Subfield extraction happens during parse.** A `FieldProcessor` on a field pulls
structure out of it as the message is read: `PDS` breaks a field into `PDSxxxx`
entries (`PdsCodec`), `ICC` into `TAGxxxx` (`IccCodec`), `DE43` into named parts
via a regex (`De43Codec`). Those extra keys sit alongside the raw `DEn` value in
the same map.

**IPM files are two layers.** VBS framing (4 byte length, zero length record ends
the file) in `VbsReader`/`VbsWriter`, and optional 1014 byte blocking in
`BlockingOutputStream`/`UnblockingInputStream`. `IpmReader`/`IpmWriter` add ISO
8583 on top. `IpmParamReader` reads parameter extracts, whose records are fixed
position text rather than messages.

## Parity traps

These caused real bugs or would have. Do not "simplify" them.

- **Two digit years follow Python, not Java.** `00`–`68` mean 2000s, `69`–`99`
  mean 1900s. Java's `yy` pattern would read every one as 2000s, dating 1990s
  records a century late. `DateFormats` builds the formatter with an explicit
  1969 base.
- **The bitmap is always 16 bytes and bit 1 is always set** when writing,
  regardless of which fields are present. Parsing likewise assumes 16 bytes and
  never consults bit 1. Fields 2–127 only; 128 is never read or written.
- **Empty values are dropped when writing**, zeroes are kept. Python treats `''`
  as falsy and skips it, but explicitly allows `0`.
- **The blocking writer defers its padding.** Filler is only written once more
  data arrives, so a run ending exactly on a block boundary leaves the block open.
  Emitting it eagerly changes the trailing bytes of every file.
- **Packing private data overwrites data elements.** `PDSxxxx` values are packed
  into DE 48, 62, 123, 124, 125 in order, replacing anything already there. A
  message holding both is not round trip safe — this matches cardutil.
- **`mci-ipm-encode` strips PDS processing** from the config before copying, so
  private data is carried across exactly as read rather than unpacked and rebuilt.

## Deliberate divergences from cardutil

Seven, each listed in the README with its reason. Do not close them in the name
of parity without checking there first — each exists because cardutil's behaviour
is wrong or unsafe. The one most likely to surprise: `mci-ipm-to-csv` masks card
numbers unless `--unmask-pan` is passed, while the library itself never masks —
not even for a field the layout marks `PAN` — so files stay round trip safe.

## Conventions

- Javadoc explains *why*, in plain English, and comments mark the places where
  cardutil's behaviour is being matched deliberately.
- Anything holding a pin or a card number masks or omits it in `toString()`.
- Data problems throw `PaymentCardException` (unchecked, carries the offending
  bytes and record number); real I/O failures stay `IOException`.
- `.gitignore` blocks `*.ipm` and `testdata/`. Test data uses published test card
  numbers only.

## Other agent config

A Codex config exists at `~/.codex/config.toml`. To bring anything from it into
Claude Code, reply `/import` to see what is importable, then
`/import --yes=<digest>`. Do not read that file or hand write the equivalent
config.
