# Changelog

## [0.1.1](https://github.com/sgerrand/payment-card-util/compare/payment-card-util-v0.1.0...payment-card-util-v0.1.1) (2026-08-21)


### Performance Improvements

* **iso8583:** read and write a record without copying it about ([#7](https://github.com/sgerrand/payment-card-util/issues/7)) ([05ff3d6](https://github.com/sgerrand/payment-card-util/commit/05ff3d698da470b0b268f533b0e2af4416b85f59))


### Documentation

* **ipm:** say where the parameter row shape comes from ([#9](https://github.com/sgerrand/payment-card-util/issues/9)) ([4c95804](https://github.com/sgerrand/payment-card-util/commit/4c95804045a90c61b9f541cde0568b62c2b1e496))

## 0.1.0 (2026-08-20)


### Features

* **cli:** let mci-ipm-param-encode take a config file ([4ac98dd](https://github.com/sgerrand/payment-card-util/commit/4ac98dd5056bd225f7188e4067ce53a0f2a84cf3))
* **config:** name a field processor with a string, not an enum ([cf16189](https://github.com/sgerrand/payment-card-util/commit/cf16189c9998a9992555aa1f1f6b850b82ed5c1d))
* dump the offending bytes when a file will not read ([3db7f29](https://github.com/sgerrand/payment-card-util/commit/3db7f29e3395805a2a9f28118e593099f71c7a4a))
* port the cardutil library to Java ([8ac7201](https://github.com/sgerrand/payment-card-util/commit/8ac72015903df963ca04f2162d3e187dd4fea55a))
* scaffold payment card utilities library ([4ae9063](https://github.com/sgerrand/payment-card-util/commit/4ae9063d88539a22a48c733538288f1be5225551))


### Bug Fixes

* **cli:** choose the masked columns by field processor ([40d2dbe](https://github.com/sgerrand/payment-card-util/commit/40d2dbe47bfe6cb582a84a5a8967193cb4e4cd8e))
* **cli:** keep the rest of the layout when a config file names one element ([2a3e5fc](https://github.com/sgerrand/payment-card-util/commit/2a3e5fc7159febb53be7311015186c3200754d27))
* **cli:** mask a column marked by either signal ([6d2dc45](https://github.com/sgerrand/payment-card-util/commit/6d2dc45ab94eaa83c1483577c40f35fe1256e046))
* **cli:** read parameter files at the configured record length ([105aff4](https://github.com/sgerrand/payment-card-util/commit/105aff47cf14153e766f7bf0335c15958977ebd9))
* **cli:** stop CRLF line endings producing empty CSV rows ([#1](https://github.com/sgerrand/payment-card-util/issues/1)) ([6dd34d8](https://github.com/sgerrand/payment-card-util/commit/6dd34d8e84e4edcffa70a3bdf7041bdf595d8aaf))
* **cli:** take --no1014blocking on the encode tools as well ([57450d8](https://github.com/sgerrand/payment-card-util/commit/57450d8fb2b69c22c8bb532ac7a53c2741f69ab5))
* **core:** compare messages by value when they carry chip data ([#2](https://github.com/sgerrand/payment-card-util/issues/2)) ([6c7d55d](https://github.com/sgerrand/payment-card-util/commit/6c7d55d5eed124f6f06756a8b09cc8d03ebbbaf6))
* **core:** give a data problem the record it came from ([8f62e17](https://github.com/sgerrand/payment-card-util/commit/8f62e174fc0d8d1581f1c23179786fc19a0e08e7))
* **core:** refuse to write a message with no type indicator ([decf713](https://github.com/sgerrand/payment-card-util/commit/decf713fd21cf77c085ee5f920f16cf3f53d2468))
* **iso8583:** stop the PAN processors changing the value ([ca312eb](https://github.com/sgerrand/payment-card-util/commit/ca312ebc05d066d5ee4a97b7df0432e8c589c734))
* stop claiming to know which EBCDIC code page a file uses ([415f9d6](https://github.com/sgerrand/payment-card-util/commit/415f9d6a28005ed73d200d16daf5d4041db9092a))


### Performance Improvements

* **core:** stop decoding a string per byte in hex dumps ([edfab02](https://github.com/sgerrand/payment-card-util/commit/edfab025eb31a5375d96a9aad2ac752794dc08e5))


### Documentation

* add CLAUDE.md ([69291dc](https://github.com/sgerrand/payment-card-util/commit/69291dcdd5ad5bab014cfe4d84f01b3a164c7868))
* keep every version in the examples current ([9c2636a](https://github.com/sgerrand/payment-card-util/commit/9c2636a92a106c847957e2009cb2653df5ff3e41))
* keep the release marker out of the install snippet ([b0ef9d5](https://github.com/sgerrand/payment-card-util/commit/b0ef9d5d327cb41b9ab98115a5e7135fbec8fd67))
* say that mci-ipm-param-encode takes a config file ([0a2d8be](https://github.com/sgerrand/payment-card-util/commit/0a2d8be217b9ce17337bee05a96e20fdb55773e8))
* say why the cryptography looks wrong ([d9ed775](https://github.com/sgerrand/payment-card-util/commit/d9ed775fd2aaebeb425fc0a8653f598f9e0bebd7))
