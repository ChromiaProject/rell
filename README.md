# Rell Codegen

Generates client stubs from rell sources.

## Setup

Create the distribution:
```commandline
./gradlew installDist
```
Unpack a distribution from `rellgen/build/distributions` and run `./rellgen/bin/rellgen`

## Usage

Find usage instructions using
```commandline
rellgen -h
```

## Contribute

Build and run unit tests:
```commandline
./gradlew build
```

Build maven plugin:
```commandline
./gradlew publishToMavenLocal publishMavenPluginToMavenLocal
```
or build the maven project from `plugins/rellgen-maven-plugin`. Note though that you first must push the gradle jars to maven local..