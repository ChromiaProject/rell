# Rell Programming Language

[![pipeline status](https://gitlab.com/chromaway/rell/badges/dev/pipeline.svg)](https://gitlab.com/chromaway/rell/-/commits/dev)
[![coverage report](https://gitlab.com/chromaway/rell/badges/dev/coverage.svg)](https://gitlab.com/chromaway/rell/-/commits/dev)
[![latest tag](https://img.shields.io/gitlab/v/tag/chromaway%2Frell?sort=semver)](https://gitlab.com/chromaway/rell/-/tags)
[![license: GPLv3](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE)

Rell is the relational blockchain programming language for [Chromia](https://chromia.com/). It combines a SQL-like data model with a statically typed, imperative syntax, and compiles against a PostgreSQL-backed runtime.

> **The canonical repository is hosted on GitLab: [gitlab.com/chromaway/rell](https://gitlab.com/chromaway/rell).**
> The GitHub repository is a read-only mirror — please file issues and merge requests on GitLab.

## Links

- Documentation: https://docs.chromia.com/rell/rell-intro
- API reference: https://docs.chromia.com/pages/rell/index.html
- Release notes: https://gitlab.com/chromaway/rell/-/tree/dev/doc/release-notes
- Chromia documentation: https://docs.chromia.com/

## Requirements

- Java 21
- [Docker](https://docs.docker.com/get-started/get-docker/) — for PostgreSQL and Testcontainers-based integration tests

## Build

| Command                     | Description                                                        |
|-----------------------------|--------------------------------------------------------------------|
| `./gradlew assemble`        | Compile and package all JARs                                       |
| `./gradlew build`           | Assemble and run all tests (requires PostgreSQL and Docker)        |
| `./gradlew clean`           | Clean build outputs                                                |
| `./gradlew installRellDist` | Install the Rell runtime to `rell-tools/build/install/rell-dist/`  |

## Run

### REPL

```shell
./work/rell.sh
```

```text
Rell 0.16.0-SNAPSHOT
Type '\q' to quit or '\?' for help.
>>> 2+2
4
>>> range(10) @* {} (@sum $)
45
>>>
```

### Execute a program

Create `hello.rell`:

```rell
module;

function main() {
    print('Hello, world!');
}
```

Run it (pass the parent directory of `hello.rell` via `-d`):

```shell
./work/rell.sh -d . hello main
```

```text
Hello, world!
```
