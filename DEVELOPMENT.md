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
3. **[Maven](https://maven.apache.org/download.cgi)** - For building and dependency management
4. **[Docker](https://docs.docker.com/get-started/get-docker/)** - For running PostgreSQL in an isolated container
5. **[PostgreSQL](https://www.postgresql.org/download/)** (`psql`) - For setting up PostgreSQL for tests

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

To make a clean build of project distribution without running tests (faster, PSQL is not needed):

```shell
mvn clean install -DskipTests -Pdistro
```

### Running Tests

**Launch PostgreSQL for Testing**

The highly recommended and simple way to provide a Postgres instance for running tests is to use Docker container:

```shell
./work/psql/psql-docker.sh
```

**Run tests** (requires local PostgreSQL to be running):

```shell
mvn verify
```

**In IntelliJ IDEA**
1. Ensure that your IntelliJ IDEA does not use JPS (IDEA's built-in build system) instead of Maven

   https://www.jetbrains.com/help/idea/delegate-build-and-run-actions-to-maven.html#build_through_maven
2. Use the run configuration `All_tests`.

### Running Rell Shell (REPL)

To start the interactive Rell shell:

```shell
work/rell.sh
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

## Binary Compatibility Checker

In case if your build fails with an error like this:

```
org.apache.maven.lifecycle.LifecycleExecutionException: Failed to execute goal com.chromaway:kotlin-bcv-maven-plugin:0.1.0:check (check) on project rell-api-base: API check failed:
...
```

Please run the following command to dump the API:

```shell
mvn compile kotlin-bcv:dump -DskipTests
```

Or just run configuration `Kotlin_ABI_Dump` in IntelliJ IDEA that does the same.

Afterward, review the *.api files that were changed by this dump and ensure the changes are intended.

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

## Git Workflow

1. Create a branch for your changes
2. Make your changes, following the project's coding conventions
3. Write or update tests as necessary
4. Submit a merge request (MR) on GitLab
5. Address any feedback from code reviewers

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
