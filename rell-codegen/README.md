# Rell Codegen

Generates client stubs from Rell sources.

## Usage

Build and run:

```shell
./gradlew :rell-codegen:rellgen:installDist
./rell-codegen/rellgen/build/install/rellgen/bin/rellgen -h
```

## GTV Type Mappings

### Query/Operation

| type       | input           | output          | remark                             |
|------------|-----------------|-----------------|------------------------------------|
| entity     | GtvInteger      | GtvInteger      |                                    |
| enum       | GtvInteger      | GtvInteger      |                                    |
| struct     | GtvArray        | GtvDict         | Can be GtvDict as input to query   |
| decimal    | GtvString       | GtvString       |                                    |
| boolean    | GtvInteger      | GtvInteger      |                                    |
| rowid      | GtvInteger      | GtvInteger      |                                    |
| json       | GtvString       | GtvString       |                                    |
| nullable   | GtvNull or type | GtvNull or type |                                    |
| collection | GtvArray        | GtvArray        | Both set and list                  |
| map        | GtvDict         | GtvDict         | If key is text                     |
| map        | GtvArray        | GtvArray        | If key is not text [k1,v1,k2,v2..] |
| tuple      | GtvDict         | GtvDict         | If typed                           |
| tuple      | GtvArray        | GtvArray        | If not typed                       |

### Structures

```
entity -> Long
```

## Example

The following Rell code:

```rell
enum test_enum { a }
operation input_parameter_enum(e: test_enum) {}
```

generates this Kotlin code:

```kotlin
enum class TestEnum {
    a
}

fun GTXTransactionBuilder.inputParameterEnumOperation(e: TestEnum) =
    addOperation("input_parameter_enum", gtv(e.ordinal.toLong()))
```

## Architecture

Three layers: interface/logic, implementation, and CLI.

**codegen** — core business logic. `DocumentFactory` compiles a Rell application and produces a `DocumentSection` for each Rell component; sections are collected into `Document`s which can be saved to disk.

**codegen-X** (codegen-kotlin, codegen-typescript, etc.) — each target implements `DocumentFactory` and its `DocumentSection`s. The `testResources` folder contains Rell files with definitions that must be covered by tests in each implementation.

**rellgen** — CLI entry point for manual testing and client-facing usage.
