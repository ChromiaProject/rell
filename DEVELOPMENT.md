# Contributing to Rell

This document provides guidelines and instructions for contributing to the Rell project.

[[_TOC_]]

## Introduction

**What are Postchain and Chromia?**

- **Postchain**: Postchain is a container that runs blockchain modules. It handles incoming requests to a node, delegates operations and queries to Rell, and builds blocks.
- **Chromia**: A full blockchain network that consists of Postchain, Rell, and additional components.
- **Rell**: A programming language, allowing developers to write applications.

Rell's two key features are blockchain integration and SQL-like capabilities:
- Blockchain features: Operations, queries, and library APIs (`chain_context`, `op_context`, etc.)
- Query features: Entity and object definitions, at-expressions, and database manipulation operations

## Required Tools

1. **[IntelliJ IDEA](https://www.jetbrains.com/idea/)** - The recommended IDE
2. **JDK 21** - The project requires Java Development Kit 21 (can be managed by IDEA)
3. **[PostgreSQL](https://www.postgresql.org/download/)** (`psql`) - For setting up PostgreSQL for core module tests
4. **[Docker](https://docs.docker.com/get-started/get-docker/)** - For running PostgreSQL in an isolated container and for Testcontainers-based integration tests
5. **[Maven](https://maven.apache.org/)** (optional) - Required by `work/local-chr.sh` and `work/build-local-docs.sh` (see [scripts requiring maven](#scripts-requiring-maven))

## Project Structure

The Rell project is organized into several modules:

- **rell-base**: Core language implementation, split into sub-modules:
  - `utils`: Shared utilities with no Rell-specific dependencies
  - `rr-tree`: Serializable RR_ tree data classes (types, definitions, IR, frames)
  - `rr-serialization`: FlatBuffers serializer/deserializer for the RR_ tree
  - `frontend`: Compiler frontend (AST, compilation layer, R_ model, type system, library framework) and `R_ → RR_` resolver
  - `runtime`: Rt_ interpreter, runtime values, SQL generation, standard library implementations
  - `test-utils`: Shared test fixtures
  - `tests`: Unit/integration tests for `rell-base`
- **rell-api-base**: Base API definitions
- **rell-api-gtx**: Generic Transaction Protocol (GTX) API implementation
- **rell-api-native**: Native API
- **rell-api-shell**: Shell/REPL implementation
- **rell-gtx**: GTX integration
- **rell-tools**: Developer tools
- **coverage-report-aggregate**: Test coverage reporting

`rell-toolbox/` &mdash; LSP server and code analysis tools:
- **common**: Shared utilities and Rell project model
- **ast**: ANTLR-based parser and AST
- **indexer**: Workspace file indexing
- **code-quality**: Formatter and linter
- **language-server**: LSP server (published as shadow JAR)
- **seeder**: Test data generation

`rell-codegen/` &mdash; generates client stubs from Rell contracts:
- **codegen**: Core code generation framework
- **codegen-kotlin**, **codegen-typescript**, **codegen-javascript**, **codegen-python**, **codegen-mermaid**: Language-specific generators
- **rellgen**: CLI application

- **rell-dokka-plugin**: Dokka plugin for Rell system library documentation

Other directories:
- **doc**: Documentation, including language guide and release notes
- **work**: Scripts and configuration for running Rell

## Building

To build the project distribution without running tests:

```shell
./gradlew assemble
```

## Testing

### PostgreSQL Setup

The highly recommended and simple way to provide a Postgres instance for running tests is to use Docker container:

```shell
./work/psql/psql-docker.sh
```

### Running Tests

Requires local PostgreSQL to be running:

```shell
./gradlew check                              # all modules
./gradlew :rell-base:check                   # single module
./gradlew :rell-base:test --tests '*.MyTest' # single test class
```

In IntelliJ IDEA, use the run configuration `All_tests` (Gradle `check`).

### Testcontainers

Some integration tests use [Testcontainers](https://www.testcontainers.org/) to spin up disposable Docker containers. Testcontainers requires a working Docker daemon. On Linux with Docker Engine installed natively this works out of the box.

#### macOS: Using Colima Instead of Docker Desktop

On macOS, [Colima](https://github.com/abiosoft/colima) is a lightweight alternative to Docker Desktop.

Because Colima exposes its Docker socket at a non-default path, you need to tell both Testcontainers and the Gradle build where to find it.

**1. Configure Testcontainers globally** &mdash; create or edit `~/.testcontainers.properties`:

```properties
docker.host=unix://${HOME}/.colima/default/docker.sock
ryuk.disabled=true
```

`ryuk.disabled=true` avoids a common issue where the Ryuk resource-reaper container fails to start under Colima.

**2. Forward Docker config to Gradle test JVMs** &mdash; add to `local.properties` in the project root:

```properties
DOCKER_HOST=unix://${HOME}/.colima/default/docker.sock
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
TESTCONTAINERS_RYUK_DISABLED=true
```

### Locale Testing

Passing `-PwithLocales` to `check` runs the test suite under several non-default locales to verify that the implementation is locale-independent. This matters because Rell must be deterministic.

Some Java/Kotlin functions are locale-sensitive by default. For example, on a machine configured for Turkish (`tr_TR`), `"i".toUpperCase()` produces `"İ"` (dotted capital I) rather than `'I'`, and `String.format` uses locale-specific decimal and grouping separators. Despite minimal probability, these differences can cause divergence of nodes in a blockchain network.

```shell
./gradlew check -PwithLocales
```

Tested locales: `tr_TR` (Turkish &mdash; the most common source of case-conversion bugs), `ar_SA`, `ja_JP`.

## Configuration

### `local.properties`

The build script loads `local.properties` from the project root (git-ignored) and forwards its entries as environment variables and system properties to every test JVM. This is the recommended way to set machine-specific configuration without touching tracked files.

The following variables are forwarded to test JVMs (via `local.properties` or the shell environment):

| Variable                                | Purpose                                           |
|-----------------------------------------|---------------------------------------------------|
| `DOCKER_HOST`                           | Docker daemon socket URL                          |
| `DOCKER_TLS_CERTDIR`                    | TLS certificate directory (if using TLS)          |
| `TESTCONTAINERS_HOST_OVERRIDE`          | Override the host Testcontainers connects to      |
| `TESTCONTAINERS_RYUK_DISABLED`          | Disable the Ryuk resource reaper (`true`/`false`) |
| `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` | Override the socket path inside the container     |

### Gradle Project Properties

The following project properties control resource allocation and test behavior. They can be passed on the command line or set in `gradle.properties`.

| Property                | Default                 | Purpose                                                   |
|-------------------------|-------------------------|-----------------------------------------------------------|
| `testJvmMaxHeap`        | `2g`                    | Max heap for each forked test JVM                         |
| `junitParallelThreads`  | `availableProcessors()` | JUnit ForkJoinPool threads per test worker                |
| `withLocales`           | unset                   | Run tests under extra locales (`tr_TR`, `ar_SA`, `ja_JP`) |
| `generateTestCases`     | unset                   | Generate grammar test case files during build             |
| `gitlabAuthHeaderValue` | unset                   | GitLab Package Registry auth token (CI only)              |

Example for a memory-constrained laptop:

```shell
./gradlew check -PtestJvmMaxHeap=1g -PjunitParallelThreads=2 --max-workers=2
```

## IntelliJ IDEA Setup

Shared run configurations are stored in `work/` as `.run.xml` files. Import them into your IDE via **File → Import Run Configuration** (or copy into `.idea/runConfigurations/`):

| File                       | Purpose                              |
|----------------------------|--------------------------------------|
| `All_tests.run.xml`        | Gradle `check` across all modules    |
| `Kotlin_ABI_Dump.run.xml`  | Gradle `apiDump` (binary compat)     |
| `Test_snippets.run.xml`    | Generate grammar test snippets       |

## Running Rell

### Rell Shell (REPL)

```shell
./work/rell.sh
```

Example usage:

```
Rell 0.15.0-SNAPSHOT
Type '\q' to quit or '\?' for help.
>>> 2+2
4
>>> range(10) @* {} (@sum $)
45
```

### Running a Rell Program

Create a file (e.g., `hello.rell`):

```rell
module;

function main() {
    print('Hello, world!');
}
```

Run it:

```shell
work/rell.sh -d <parent-directory> hello main
```

### Running a Dapp via `chr`

```shell
# You may avoid installing chr by using Docker for it, too
# https://docs.chromia.com/intro/getting-started/installation/cli-installation#start-the-docker-container-with-chromia-cli-pre-installed

chr create-rell-dapp
chr node start
chr query -brid <BRID> <QUERY_NAME> <ARGS_AS_GTV_DICT_STR>
chr tx -brid <BRID> <OP_NAME> ARG_AS_GTV_STR*
```

### Scripts Requiring Maven

The following scripts require [Maven](https://maven.apache.org/) to be installed, as they interact with the Chromia CLI which uses a Maven-based build.

#### local-chr.sh

The `local-chr.sh` script is a wrapper for the Chromia CLI (`chr`) that uses your local Rell build instead of the officially published release. It automatically patches CLI's POM to your local Rell version, making it ideal for testing language changes or new features before they're deployed.

```shell
./work/local-chr.sh [arguments]

# Print the software versions, for example
./work/local-chr.sh --version
```

If you need to clone the local Chromia CLI repository and patch it again, then add the `--rebuild` flag:

```shell
./work/local-chr.sh --rebuild --version
```

#### build-local-docs.sh

Generates Rell documentation locally. This is useful for previewing changes to the Rell system libraries documentation. Output goes to the `libdoc` directory.

```shell
./work/build-local-docs.sh
```

## Code Quality

### Binary Compatibility Checker

In case your build fails on the `apiCheck` task with an error like this:

```
> Task :rell-api-base:apiCheck FAILED

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':rell-api-base:apiCheck'.
> API check failed for project rell-api-base.
```

Run configuration `Kotlin_ABI_Dump` in IntelliJ IDEA or the following command to dump the API:

```shell
./gradlew apiDump
```

Afterward, review the *.api files that were changed by this dump and ensure the changes are intended.

### Static Analysis (Qodana)

CI runs [Qodana](https://www.jetbrains.com/qodana/) (Community edition). The SARIF report is saved as a pipeline artifact. Qodana produces a [GitLab Code Quality](https://docs.gitlab.com/ci/testing/code_quality/) report, so new findings appear inline in merge request. Locally, the same analysis can be performed by IntelliJ IDEA.

### Test Coverage (JaCoCo)

The project uses [JaCoCo](https://www.jacoco.org/) for code coverage reporting.

**Local reports.** Running tests for any module generates a per-module report at `{module}/build/reports/jacoco/test/html/index.html`. To generate a single aggregate report:

```shell
./gradlew testCodeCoverageReport # tCCR
```

The aggregate report is located at `coverage-report-aggregate/build/reports/jacoco/testCodeCoverageReport/html/index.html`.

**GitLab MR coverage.** CI pipelines upload per-module JaCoCo XML reports as a `coverage_report` artifact (format `jacoco`), so GitLab can display line-by-line coverage in merge request diffs. The pipeline also parses the aggregate CSV to extract an overall line-coverage percentage, which GitLab shows as the MR coverage badge.

## Coding Conventions

**Naming Prefixes**

The project uses various prefixes for different types of classes:

- `S_` - AST nodes
- `G_` - Helper grammar classes
- `L_` - Library framework
- `M_` - Type framework
- `C_` - Compilation
- `R_` - Compiled objects
- `RR_` - Resolved runtime model (serializable IR consumed by the interpreter)
- `Rt_` - Runtime

**Basic Conventions:**

1. The project has .editorconfig, but auto-formatting is not used strictly. Don't commit changes that only consist of applying formatting.
2. All calculations in the implementation of Rell must be deterministic and reproducible. Expressions must produce the same results in both interpreted and database contexts.
3. All dependency versions must be declared in `gradle/libs.versions.toml`. Do not hardcode version strings in `build.gradle.kts` files.

### Version Annotations for Library Declarations

When adding new library functions, types, properties, or other declarations, always use `RellVersions.SINCE_NOW` for the `since` parameter:

```kotlin
function("my_new_function", result = "integer", since = RellVersions.SINCE_NOW) {
    param("value", "text")
    body { arg ->
        // implementation
    }
}
```

The `SINCE_NOW` constant is defined as the current development version and will be replaced with the actual version during the release process.

## Further Reading

1. The Language Guide

   https://gitlab.com/chromaway/rell/-/tree/dev/doc/guide
2. Release notes (starting with the oldest version, 0.6.1)

   https://gitlab.com/chromaway/rell/-/tree/dev/doc/release-notes

3. Rell system library documentation

   https://docs.chromia.com/pages/rell/index.html

4. Chromia documentation

   https://docs.chromia.com/ and
   https://docs.chromia.com/intro/getting-started/create-dapp/run-dapp-cli

## Release Process

See [doc/release-guide.md](doc/release-guide.md).

## Release Notes

See [doc/release-notes-guide.md](doc/release-notes-guide.md).
