# Working in this repo

A collection of small, independent Java experiments (data structures, concurrency, distributed-systems ideas).

- Java 17, Maven. Build and test with `mvn -B -q test`.
- Each experiment lives in its own package under `src/main/java/com/gladunalexander/<experiment>/`.
  Put a new experiment in a new package; don't change unrelated ones.
- Lombok and Vavr are available. Prefer small interfaces with a `Default...` implementation, as the existing code does.
- Tests go in `src/test/java` with JUnit 5. Add the `junit-jupiter` test dependency and a recent
  `maven-surefire-plugin` to `pom.xml` the first time you need them.
