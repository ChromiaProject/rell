# Contributing

- Read the [Architecture](README.md#architecture) for a high-level understanding.
- Make sure your changes are covered by tests. `./gradlew testCodeCoverageReport` may help find uncovered branches.
- If you add Rell code to `testResources`, make sure it is tested by all relevant modules in the implementation layer.
- Integration tests use [TestContainers](https://testcontainers.com), so a Docker daemon must be running.
