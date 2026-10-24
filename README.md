# SE-CA6 — TinyME Matching-Engine Coursework

A collaborative Java coursework project based on the TinyME matching engine. The source models brokers, shareholders, securities, orders, trades, and order-book matching.

## Contents

- `TinyME-main/src/main/java/ir/ramtung/tinyme/`: domain logic, order processing, messaging, and repositories.
- `TinyME-main/src/main/resources/`: application configuration and demonstration persistence fixtures.
- `TinyME-main/src/test/`: domain and order-processing tests.

This is an assignment snapshot. Features and expected behavior are defined by this revision's implementation and tests.

## Requirements and checks

Use JDK 17 and Maven. The supplied Maven project uses Spring Boot 3.2.0 and Artemis messaging.

```sh
cd TinyME-main
mvn test
mvn package
```

For application startup, configure the broker and persistence settings in `src/main/resources/application.properties`, then use:

```sh
mvn spring-boot:run
```

Some tests replace external messaging with test configuration; review the test fixtures before running against a real broker. No live market or production deployment is included.

## Snapshot scope

This public copy contains source and coursework fixtures with fresh Git history. IDE metadata and generated build output are excluded. Existing licenses and third-party attribution files are preserved where supplied.

## Credits and source

This is a public portfolio copy of the shared project [SoheilHajianManesh/SE-CA6](https://github.com/SoheilHajianManesh/SE-CA6), prepared for Sobhan Alaeddini's account. Original authorship and existing license notices are retained. See [SOURCE.md](SOURCE.md) for the source revision and publication notes.
