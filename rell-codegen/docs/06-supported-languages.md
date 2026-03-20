# Kotlin Code Generation

Complete guide to using rell-codegen for Kotlin client libraries.

## Overview

Kotlin code generation produces type-safe client code that integrates with the Postchain GTX (Generic Transaction) framework.

**Key Features:**
- Data classes for structs
- Enum classes for Rell enums
- Extension methods for queries and operations
- Full type safety with null-safety
- Serialization/deserialization to/from GTV

## Generating Kotlin Code

```bash
rellgen \
  ./rell-src \
  ./generated/kotlin \
  --kotlin --package com.example.app
```

**Required Parameter:**
- `--package`: Kotlin package name (e.g., `com.example.app`)

**Output:** `.kt` files in directory structure matching package name

## Type Mappings

### Primitive Types

| Rell Type | Kotlin Type | GTV Type |
|-----------|-------------|----------|
| `text` | `String` | `GtvString` |
| `integer` | `Long` | `GtvInteger` |
| `big_integer` | `BigInteger` | `GtvBigInteger` |
| `boolean` | `Boolean` | `GtvInteger` (0/1) |
| `byte_array` | `ByteArray` | `GtvByteArray` |
| `decimal` | `BigDecimal` | `GtvString` |
| `rowid` | `Long` | `GtvInteger` |

### Collection Types

| Rell Type | Kotlin Type | GTV Type |
|-----------|-------------|----------|
| `list<T>` | `List<T>` | `GtvArray` |
| `set<T>` | `Set<T>` | `GtvArray` |
| `map<K,V>` | `Map<K,V>` | `GtvDict` |

### Special Types

| Rell Type | Kotlin Type | Notes |
|-----------|-------------|-------|
| `pubkey` | `ByteArray` | 33-byte public key |
| `blockchain_rid` | `ByteArray` | 32-byte blockchain ID |
| `gtv` | `Gtv` | Raw GTV value |

## Integration with Postchain SDK

### Dependencies

Add to `build.gradle.kts`:

```kotlin
dependencies {
    // Postchain client library
    implementation("net.postchain:postchain-client:<latest>")

    // GTV serialization
    implementation("net.postchain:postchain-gtv:<latest>")

    // Common utilities
    implementation("net.postchain:postchain-common:<latest>")
}
```


# TypeScript Code Generation

Complete guide to using rell-codegen for TypeScript client libraries.

## Overview

TypeScript code generation produces type-safe client code for web browsers and Node.js environments, integrating with the Postchain Client library.

**Key Features:**
- Interfaces for structs
- Enum types for Rell enums
- Async functions for queries and operations
- Full TypeScript type safety
- Promise-based API

## Generating TypeScript Code

```bash
rellgen \
  ./rell-src \
  ./generated/typescript \
  --typescript
```

**Output:** `.ts` files in the target directory

## Type Mappings

| Rell Type | TypeScript Type | Notes |
|-----------|-----------------|-------|
| `text` | `string` | UTF-8 string |
| `integer` | `number` | JavaScript number (64-bit float, but used as integer) |
| `big_integer` | `bigint` | ES2020 BigInt |
| `boolean` | `boolean` | true/false |
| `byte_array` | `Uint8Array` | Typed array |
| `decimal` | `string` | Serialized as decimal string |
| `rowid` | `number` | Entity row ID |
| `list<T>` | `T[]` | Array |
| `set<T>` | `T[]` | Array (no Set type used) |
| `map<K,V>` | `Record<K,V>` or `Map<K,V>` | Depends on context |
| `pubkey` | `Uint8Array` | 33-byte public key |
| `blockchain_rid` | `Uint8Array` | 32-byte blockchain ID |

## Integration with Postchain Client

### Installation

```bash
npm install postchain-client
```

# JavaScript Code Generation

## Overview

JavaScript code generation produces plain JavaScript code compatible with Node.js and browsers, without TypeScript type annotations.

**Key Features:**
- Plain JavaScript objects (no interfaces)
- Numeric enum constants
- functions for queries and operations
- No build step required

## Generating JavaScript Code

```bash
rellgen \
  ./rell-src \
  ./generated/javascript \
  --javascript
```

**Output:** `.js` files in the target directory

**Note:** CLI source has typo in variable name (`JavscriptOption`), but functionality works correctly.


## Type Mappings

| Rell Type | JavaScript Type | Runtime Representation |
|-----------|-----------------|------------------------|
| `text` | String | `"hello"` |
| `integer` | Number | `42` |
| `big_integer` | BigInt | `1234567890123456789n` |
| `boolean` | Boolean | `true` / `false` |
| `byte_array` | Uint8Array | `Uint8Array([...])` |
| `decimal` | String | `"3.14159"` |
| `rowid` | Number | `1` |
| `list<T>` | Array | `[...]` |
| `set<T>` | Array | `[...]` |
| `map<K,V>` | Object or Map | `{...}` or `Map` |

## Integration with Postchain Client

### Installation

```bash
npm install postchain-client
```


# Python Code Generation

## Overview

Python code generation produces type-hinted client code compatible with Python 3.7+ and the Postchain Python client.

**Key Features:**
- Dataclasses for entities and structs
- Enum classes for Rell enums
- Type-hinted functions for queries and operations
- Integration with Postchain Python client

## Generating Python Code

```bash
rellgen \
  ./rell-src \
  ./generated/python \
  --python
```

**Output:** `.py` files in the target directory


## Type Mappings

| Rell Type | Python Type | Notes |
|-----------|-------------|-------|
| `text` | `str` | UTF-8 string |
| `integer` | `int` | Python arbitrary-precision int |
| `big_integer` | `int` | Python int (handles arbitrary size) |
| `boolean` | `bool` | True/False |
| `byte_array` | `bytes` | Byte sequence |
| `decimal` | `str` or `Decimal` | May use decimal.Decimal |
| `rowid` | `int` | Entity row ID |
| `list<T>` | `List[T]` | typing.List |
| `set<T>` | `Set[T]` | typing.Set |
| `map<K,V>` | `Dict[K,V]` | typing.Dict |
| `pubkey` | `bytes` | 33-byte public key |
| `blockchain_rid` | `bytes` | 32-byte blockchain ID |

## Integration with Postchain Python Client

### Installation

```bash
pip install postchain-client
```

Or with Poetry:
```bash
poetry add postchain-client
```



# Mermaid Diagram Generation

## Overview

Mermaid code generation produces visual documentation in Mermaid diagram syntax, embedded in Markdown files.

**Mermaid** is a diagramming and charting tool that uses text-based syntax to create diagrams dynamically.

**Use Cases:**
- Visual documentation of blockchain schema
- Architecture diagrams for stakeholders
- Embedded diagrams in documentation sites
- Entity-relationship visualization

## Generating Mermaid Diagrams

### Class Diagram (Default)

```bash
rellgen \
  ./rell-src \
  ./docs \
  --mermaid
```

### Entity-Relationship Diagram

```bash
rellgen \
  ./rell-src \
  ./docs \
  --mermaid --entity-relation
```

## Viewing Diagrams

### Mermaid Live Editor
Use https://mermaid.live to preview and edit diagrams.