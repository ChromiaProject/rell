# Contributing

- Make sure you read the [Architecture](README.md#Architecture) to have a high-level understanding of this repo.
- Make sure your changes are covered by tests. `./gradlew testCodeCoverageReport` may help find uncovered branches.
- If you have added rell code to `testResources`, make sure it is tested by all relevant modules in the implementation layer.

## Build and test this repo

```shell
./gradlew build
```

This repo uses [TestContainers](https://testcontainers.com) for integration testing so it is required to have a docker daemon running to be able to run the test suite.

## Merge request

Create a merge request explaining what you have added and why. Include a suggestion for release notes to use externally.
Use the provided gitlab template.
