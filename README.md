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

# Release

Performing a release consists of the following sequence on the dev branch

```shell
git tag X.Y.Z
git push --tags 
```
