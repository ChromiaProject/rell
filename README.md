# Rell Toolbox

[![Build Status](https://gitlab.com/chromaway/core-tools/rell-toolbox/badges/dev/pipeline.svg)](https://gitlab.com/chromaway/core-tools/rell-toolbox/)
[![Coverage Status](https://gitlab.com/chromaway/core-tools/rell-toolbox/badges/dev/coverage.svg)](https://gitlab.com/core-tools/rell-toolbox/)

This repository contains various tools for Rell programming language.
More details about each tool can be found in the corresponding subdirectory.

List of tools:

* [Rell Tools Core](core/docs/Core.md) - Core library for Rell tools (e.g. Parser, AST Converter, Indexer etc.)
* [Rell Tools Formatter](formatter/docs/Formatter.md) - Library for formatting of Rell source code
* [Rell Language Server](language-server/docs/LanguageServer.md) - Library for Language Server used for IDE support

## Building

```shell
./gradlew bulld
```

## Testing

```shell
./gradlew test
```

## Release

#### Language Server

New releases of the language server are handled by gitlab pipeline. Each pipeline triggered on dev branch has a release
stage that one needs to trigger manually from
the [pipeline view](https://gitlab.com/chromaway/core-tools/rell-toolbox/-/pipelines) on gitlab. Clicking on the release
stage it will prompt
you to run `release-patch` or `release-minor` job. Running either of these jobs will publish a new package into the
gitlab registry with the version automatically incremented with minor or patch versions. 