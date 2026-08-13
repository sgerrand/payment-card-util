# payment-card-util

Java utilities for payment card systems: card numbers, ISO 8583 messages, and
Mastercard IPM clearing files.

Requires Java 21.

## Install

```xml
<dependency>
  <groupId>com.sgerrand</groupId>
  <artifactId>payment-card-util</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Packages

| Package | What it holds |
| --- | --- |
| `com.sgerrand.paymentcardutil` | Card numbers, BINs, schemes, Luhn checks |
| `com.sgerrand.paymentcardutil.iso8583` | Message type indicators and bitmaps |
| `com.sgerrand.paymentcardutil.ipm` | Reading Mastercard IPM files |
| `com.sgerrand.paymentcardutil.ipm.de` | Data elements and private data subelements |

## Card numbers

```java
Pan pan = Pan.parse("4111 1111 1111 1111");

pan.isLuhnValid();  // true
pan.scheme();       // CardScheme.VISA
pan.bin();          // 411111
pan.lastFour();     // "1111"
pan.toString();     // "411111******1111"
```

`Pan.toString()` always masks the number, so logging one by accident cannot
leak it. Call `pan.digits()` only where you need the real value.

## IPM files

An IPM file is a stream of ISO 8583 messages. Each message starts with a
4-byte Record Descriptor Word giving its length. Files are often sent in
1014-byte blocks, where the last 2 bytes of every block are filler.

```java
try (IpmReader reader = IpmReader.blocked(Files.newInputStream(path))) {
    IpmMessage message;
    while ((message = reader.next()) != null) {
        System.out.println(message.mti());             // e.g. 1240
        System.out.println(message.presentFields());   // data elements present
    }
}
```

Use `IpmReader.unblocked(...)` for a file with no block padding.

The reader parses the message type and bitmap, and hands back the rest as raw
bytes. Reading a single data element needs its format from the IPM
specification, which this library does not ship.

## Build

```sh
mvn verify
```

## Handling card data

Test data in this repository must use published test card numbers only. Never
commit a real card number, and never log a full PAN.

## Licence

BSD 2-Clause. See [LICENSE](LICENSE).
