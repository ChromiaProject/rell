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

## Mappings

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

```commandline
entity -> Long
```

## Contribute

Build and run unit tests:

```commandline
./gradlew build
```

# Example

The following rell code

```
enum test_enum { a }
operation input_parameter_enum(e: test_enum) {}
```

generates this kotlin code:

```kotlin
/*
* Enum test_enum
*/
@Generated("net.postchain.rell.codegen.CodeGenerator", comments = "test_enum", date = "Tue Jul 12 08:51:21 CEST 2022")
enum class TestEnum {
    a
}

/**
 * Operation operations:input_parameter_enum
 */
@Generated(
    "net.postchain.rell.codegen.CodeGenerator",
    comments = "operations:input_parameter_enum",
    date = "Tue Jul 12 08:51:21 CEST 2022"
)
fun GTXTransactionBuilder.inputParameterEnumOperation(e: TestEnum) =
    addOperation("input_parameter_enum", gtv(e.ordinal.toLong()))
```

# Architecture

The architecture of this repo consists of three layers; one interface/logic layer, one implementation layer and one cli-layer.

## Interface/Logic Layer (codegen)

This module consists of all the business logic of the inner workings of the code generator. 
Ideally, any features should be implemented here using abstract patterns such as interfaces and factories. 

The data model consists of
`DocumentFactory` - represents and abstract factory class for generating a "file"
`Document` - Represents one file in the file system
`DocumentSection` - An isolated portion of a `Document`

All in all, the `DocumentFactory` will compile a rell application and produce a `DocumentSection` for each rell component.
The sections are then collected into `Document`s which can be saved to disk.

## Implementation Layer (codegen-X)

Each code generation target gets implemented here. This could be a language or a flavor to be generated. 
Each implementation module implements the abstract factory `DocumentFactory` and its `DocumentSection`s. This can also implement a special configuration class with their own properties if needed.

### Testing

The folder `testResources` contains a number of rell files with a set of rell definitions that must be covered by the tests in each implementation layer.

## CLI-layer (rellgen)

The final module consists of a simple cli which defines a simple cli. This is used for manual testing of the repo. 
Here one can make sure that the implementations works and see how they will look like from a clients perspective (in terms of configuration etc).
