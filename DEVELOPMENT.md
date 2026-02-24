# Contributing to Rell

This document provides guidelines and instructions for contributing to the Rell project.

[[_TOC_]]

## Introduction

### What are Postchain and Chromia?

- **Postchain**: Postchain is a container that runs blockchain modules. It handles incoming requests to a node, delegates operations and queries to Rell, and builds blocks.
- **Chromia**: A full blockchain network that consists of Postchain, Rell, and additional components.
- **Rell**: A programming language, allowing developers to write applications.

Rell's two key features are blockchain integration and SQL-like capabilities:
- **Blockchain features**: Operations, queries, and library APIs (`chain_context`, `op_context`, etc.)
- **SQL-like features**: Entity and object definitions, at-expressions, and database manipulation operations

## Required Tools

1. **[IntelliJ IDEA](https://www.jetbrains.com/idea/)** - The recommended IDE
2. **JDK 21** - The project requires Java Development Kit 21 (can be managed by IDEA)
3. **[PostgreSQL](https://www.postgresql.org/download/)** (`psql`) - For setting up PostgreSQL for tests
4. **[Docker](https://docs.docker.com/get-started/get-docker/)** - Only for running PostgreSQL in an isolated container

## Project Structure

The Rell project is organized into several modules:

- **rell-api-base**: Base API definitions
- **rell-api-gtx**: Generic Transaction Protocol (GTX) API implementation
- **rell-api-shell**: Shell/REPL implementation
- **rell-base**: Core language implementation
- **rell-gtx**: GTX integration
- **rell-tools**: Developer tools
- **coverage-report-aggregate**: Test coverage reporting

Other directories:
- **doc**: Documentation, including language guide and release notes
- **pytests**: Python-based end-to-end tests
- **work**: Scripts and configuration for running Rell

## Building and Testing

### Building the Project

To build of project distribution without running tests:

```shell
./gradlew assemble
```

### Running Tests

**Launch PostgreSQL for Testing**

The highly recommended and simple way to provide a Postgres instance for running tests is to use Docker container:

```shell
./work/psql/psql-docker.sh
```

**Run tests** (requires local PostgreSQL to be running):

```shell
./gradlew check
```

**In IntelliJ IDEA**

Use the run configuration `All_tests` (Gradle `check`).

### Running Rell Shell (REPL)

To start the interactive Rell shell:

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

### Running Other Tools

Generate grammar test snippets:
- In IntelliJ, use the run configuration from `work/Test_snippets.run.xml`

Running a simple dapp via the chr tool:
```shell
# You may avoid installing chr by using Docker for it, too
# https://docs.chromia.com/intro/getting-started/installation/cli-installation#start-the-docker-container-with-chromia-cli-pre-installed

chr create-rell-dapp
chr node start
chr query -brid <BRID> <QUERY_NAME> <ARGS_AS_GTV_DICT_STR>
chr tx -brid <BRID> <OP_NAME> ARG_AS_GTV_STR*
```

### Checking Tests With Different Locales

The `work/check-with-locales.sh` script runs the test suite under several non-default locales to verify that the implementation is locale-independent. This matters because Rell must be deterministic.

Some Java/Kotlin functions are locale-sensitive by default. For example, on a machine configured for Turkish (`tr_TR`), `"i".toUpperCase()` produces `"İ"` (dotted capital I) rather than `'I'`, and `String.format` uses locale-specific decimal and grouping separators. Despite minimal probability, these differences can cause divergence of nodes in a blockchain network.

```shell
./work/check-with-locales.sh
```

The script tests the following locales:

| Locale  | Description          |
|---------|----------------------|
| tr_TR   | Turkish (Turkey)     |
| hi_IN   | Hindi (India)        |
| ar_SA   | Arabic (Saudi Arabia)|
| ja_JP   | Japanese (Japan)     |

Turkish is the most common source of case-conversion bugs in Java code. The remaining locales cover
different numeric, date, and script conventions.

### Using local-chr.sh

The `local-chr.sh` script in the `work` directory is a wrapper for the Chromia CLI (`chr`) that simplifies development workflow by using your local Rell build instead of the officially published release of Rell.

To use it:

```shell
./work/local-chr.sh [arguments]
```

This script allows you to test your local Rell changes directly with the Chromia CLI. Examples:

```shell
# Print the software versions, for example
./work/local-chr.sh --version
```

The script automatically patches CLI's POM to your local Rell version, making it ideal for testing language changes or new features before they're deployed.

If you need to clone the local Chromia CLI repository and patch it again, then add the `--rebuild` flag:

```shell
./work/local-chr.sh --rebuild --version
```

### Generating Documentation Preview

The project includes a script for generating Rell documentation locally. This is useful for previewing changes to the Rell system libraries documentation:

```shell
./work/build-local-docs.sh
```

This script will generate documentation in the `libdoc` directory, which you can then browse locally to see how the documentation will look.

## Binary Compatibility Checker

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

## Test Coverage (JaCoCo)

The project uses [JaCoCo](https://www.jacoco.org/) for code coverage reporting. Coverage reports are generated automatically when tests run.

Running tests for any module automatically generates a JaCoCo report. Reports are located at `{module}/build/reports/jacoco/test/html/index.html`.

**To generate a single coverage report across all modules:**

```shell
./gradlew testCodeCoverageReport # tCCR
```
The aggregate report is located at `coverage-report-aggregate/build/reports/jacoco/testCodeCoverageReport/html/index.html`.

## Coding Conventions

### Naming Prefixes

The project uses various prefixes for different types of classes:

- `S_` - AST nodes
- `G_` - Helper grammar classes
- `L_` - Library framework
- `M_` - Type framework
- `C_` - Compilation
- `R_` - Compiled objects
- `Rt_` - Runtime

### General Conventions

1. Follow the existing code style when modifying files
2. The project has .editorconfig, but auto-formatting is not used strictly. Don't commit changes that only consist of applying formatting
3. All calculations in the implementation of Rell must be deterministic and reproducible (for blockchain). Expressions must produce the same results in both interpreted and database contexts

### Miscellaneous Kotlin conventions
1. Write statements (`if`, `for`...) with braces if they require a line break.

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

**Important:** Never hardcode version numbers like `since = "0.16.0"`. The `SINCE_NOW` constant is defined as the current development version and will be replaced with the actual version during the release process.

## Git Workflow

1. Create a branch for your changes
2. Make your changes, following the project's coding conventions
3. Write or update tests as necessary
4. Submit a merge request (MR) on GitLab
5. Address any feedback from code reviewers

## Release Process

This section describes how to publish a new Rell release `A.B.C`.

### 1. Finalize Release Notes

Before branching, prepare the release notes:

1. Review and finalize `doc/release-notes/dev.txt`. Make sure all user-facing changes are documented and the content follows the formatting guidelines described in the [Release Notes](#release-notes) section below.
2. Rename `dev.txt` to `doc/release-notes/A.B.C.txt`.
3. In the renamed file, replace the `UNRELEASED NOTES` header with:
   ```
   RELEASE NOTES A.B.C (YYYY-MM-DD)
   ```
   Use today's actual release date.
4. Double-check the file follows all review checklist items (see [Review Checklist](#review-checklist) below).
5. Create a new blank `doc/release-notes/dev.txt` with just the header line:
   ```
   UNRELEASED NOTES
   ```

### 2. Create the Release Branch and Bump Version

Create a branch named `version-A.B.C` from `dev`:

```shell
git checkout -b version-A.B.C
```

Update the version in two places:

- **`build.gradle.kts`** — change `version = "..."` to the release version (without `-SNAPSHOT`):
  ```kotlin
  version = "A.B.C"
  ```

- **`rell-base/src/main/kotlin/net/postchain/rell/base/utils/RellVersions.kt`** — change `VERSION_STR` to the release version:
  ```kotlin
  const val VERSION_STR = "A.B.C"
  ```

- **Standard library source files** — replace all uses of `RellVersions.SINCE_NOW` with the literal version string `"A.B.C"` in `since` annotations throughout the standard library code. Replace each `(since =) RellVersions.SINCE_NOW` with `(since =) "A.B.C"`. This ensures that library declarations record the exact version in which they were introduced.

Commit and push the branch. Pushing the `version-A.B.C` branch triggers the GitLab CI pipeline, which publishes the release automatically.

### 3. Announce the Release

After the CI pipeline completes successfully, report the new version on **Zulip**.

### 4. Post-Release Cleanup on `dev`

Switch back to the `dev` branch and perform these follow-up steps:

1. **Update `doc/release-notes/all-releases.txt`** — add an entry for the new release at the top of the list:
   ```
   - A.B.C
     Notes: A.B.C.txt
     GitLab: https://gitlab.com/chromaway/rell/-/tree/<commit-sha>/
   ```
   Use the commit SHA of the tagged release commit.

2. **If this was a major release** (A or B changed), update `VERSION_STR` in `dev` to the next development snapshot:
   ```kotlin
   // In RellVersions.kt on dev branch:
   const val VERSION_STR = "0.(B+1).0-SNAPSHOT"
   ```
   Also update `build.gradle.kts` accordingly:
   ```kotlin
   version = "0.(B+1).0-SNAPSHOT"
   ```

## Documentations

It's recommended to read:

1. The Language Guide

   https://gitlab.com/chromaway/rell/-/tree/dev/doc/guide
2. Release notes (starting with the oldest version, 0.6.1)

   https://gitlab.com/chromaway/rell/-/tree/dev/doc/release-notes

3. Rell system library documentation

   https://docs.chromia.com/pages/rell/index.html

4. Chromia documentation

   https://docs.chromia.com/ and
    https://docs.chromia.com/intro/getting-started/create-dapp/run-dapp-cli

## Release Notes

**Note**: The formatting conventions described below are guidelines, not strict rules. Use judgment to choose the most appropriate formatting for the content. The goal is clarity and consistency within each release notes file.

### File Naming and Header Conventions

#### File Naming

Release notes files are stored in `doc/release-notes/` and follow this naming pattern:

- **Major/Minor releases**: `{MAJOR}.{MINOR}.{PATCH}.txt` (e.g., `0.14.0.txt`, `0.13.5.txt`)
- **Development version**: `dev.txt` (for unreleased changes)

#### Header Format

Every release notes file starts with a standard header:

```
RELEASE NOTES {VERSION} ({YYYY-MM-DD})
UNRELEASED NOTES (for dev.txt)
```

### Section Organization and Categories

#### Section Separator Format

Each section is separated by a line of @ symbols:

```
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
```

#### Standard Categories

Sections are numbered and categorized with standard prefixes:

- **Language**: Core language features, syntax changes, new constructs
  - Example: `1. Language: Null analysis of complex expressions`

- **Library**: Standard library additions, changes to built-in functions
  - Example: `2. Library: function try_call()`

- **Tools**: CLI tools, build system, development utilities
  - Example: `3. Tools: multirun.sh --sqllog`

- **Runtime**: Runtime behavior, execution engine changes
  - Example: `4. Runtime: Default parameter values in operations`

- **Compiler**: Compiler improvements, error messages, performance
  - Example: `5. Compiler: Not stopping on first compilation error`

- **Docs**: Documentation system, doc comments, help text
  - Example: `6. Docs: New comment tags`

- **Tests**: Unit testing framework, test utilities
  - Example: `7. Tests: Function-level @test annotation`

- **API**: Public API changes, integration points
  - Example: `8. API: Functions to check the validity of Rell tokens`

### Formatting Conventions

#### Code Examples

**Indentation**: Use 4 spaces for code blocks

```
    val v: my_type? = get_v();
    if (v != null) {
        // compiler knows that "v" is not nullable here
    }
```

#### Tables

Use Unicode box drawing for tables:

```
    ┌───────────┬───────────────────────────────┐
    │ Specifier │ Meaning                       │
    ├───────────┼───────────────────────────────┤
    │ y         │ Year                          │
    │ M         │ Month in the year             │
    └───────────┴───────────────────────────────┘
```

#### Lists

**Bulleted lists**: Use `-` for main points:

```
- A value can have up to 131072 decimal digits
- Literals have suffix "L": 123L, 0x123L
- Uses Java class java.math.BigInteger internally
```

**Numbered lists**: Use Arabic numerals for sequential points or detailed explanations:

```
1. If a new key is added and the values of its attributes in the database aren't unique, database initialization fails.
2. Adding a key or index may be slow for big tables.
3. Adding new key or index attributes to an entity is supported too.
```

**Sub-items**: Use letters for sub-categorization:

```
(a) Constants:

    big_integer.PRECISION: integer

        Maximum number of decimal digits (131072).

(b) Constructors:

    big_integer(integer): big_integer

        Creates a big_integer from integer.
```

### Special Formatting for Critical Changes

#### Breaking Changes

Always include compatibility warnings:

```
Note. This is a breaking change - it may break compilation of existing code, because types of some expressions may change from nullable to not nullable. Applications deployed with older versions of Rell will not be affected (thanks to the backward compatibility mode).
```

#### Deprecation Notices

Format deprecated features clearly:

```
New @return tag is to be used instead of the old @returns tag, which is now deprecated.
```

#### Bug Fixes

For significant bug fixes, explain the impact:

```
14. Bug fixes

(1) False "Wrong operand types..." compilation error on expression: T in list<T?>.
(2) Conversion from gtv big integer value to decimal.
```

#### Review Checklist

Before publishing new release's notes, verify:

- [ ] Header format is correct with proper date
- [ ] Categories are appropriate and consistently formatted
- [ ] Code examples are properly indented with 4 spaces
- [ ] Breaking changes include compatibility warnings
- [ ] Examples are tested and work correctly
- [ ] Grammar and spelling are correct
- [ ] Cross-references to other sections are accurate
