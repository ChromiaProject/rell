# Type Mappings Reference

Comprehensive reference for how Rell types map to target languages and GTV formats.

## Overview

This document provides complete type mapping tables for all supported languages. Use this as a reference when:
- Understanding generated code structure
- Debugging serialization issues
- Planning Rell contract design
- Integrating generated clients

## Primitive Types

### text

| Language | Type | Example | GTV Format |
|----------|------|---------|------------|
| **Rell** | `text` | `"hello"` | `GtvString` |
| **Kotlin** | `String` | `"hello"` | `GtvString("hello")` |
| **TypeScript** | `string` | `"hello"` | String |
| **JavaScript** | String | `"hello"` | String |
| **Python** | `str` | `"hello"` | str |

**Notes:**
- UTF-8 encoded
- No length limits in Rell (implementation-dependent)
- Immutable in all languages

### integer

| Language | Type | Range | GTV Format |
|----------|------|-------|------------|
| **Rell** | `integer` | -2^63 to 2^63-1 | `GtvInteger` |
| **Kotlin** | `Long` | -2^63 to 2^63-1 | `GtvInteger(long)` |
| **TypeScript** | `number` | -2^53 to 2^53-1 (safe) | Number |
| **JavaScript** | Number | -2^53 to 2^53-1 (safe) | Number |
| **Python** | `int` | Arbitrary | int |

**IMPORTANT - TypeScript/JavaScript Limitation:**
JavaScript `number` type can only safely represent integers up to ±2^53-1 (approximately ±9 quadrillion).

**Rell `integer` range exceeds JavaScript safe integer range.**

**Workaround:** Use `big_integer` instead if values may exceed JavaScript safe range.

### big_integer

| Language | Type | Example | GTV Format |
|----------|------|---------|------------|
| **Rell** | `big_integer` | `123456789012345678901234567890` | `GtvBigInteger` |
| **Kotlin** | `BigInteger` | `BigInteger("...")` | `GtvBigInteger` |
| **TypeScript** | `bigint` | `123456789012345678901234567890n` | BigInt |
| **JavaScript** | BigInt | `123456789012345678901234567890n` | BigInt |
| **Python** | `int` | `123456789012345678901234567890` | int |

**Notes:**
- Arbitrary precision (no limits except memory)
- TypeScript/JavaScript require ES2020+ for `bigint` support
- Python `int` handles arbitrary precision natively

### boolean

| Language | Type | Values | GTV Format |
|----------|------|--------|------------|
| **Rell** | `boolean` | `true`, `false` | `GtvInteger(0)` / `GtvInteger(1)` |
| **Kotlin** | `Boolean` | `true`, `false` | `GtvInteger(0)` / `GtvInteger(1)` |
| **TypeScript** | `boolean` | `true`, `false` | 0 / 1 |
| **JavaScript** | Boolean | `true`, `false` | 0 / 1 |
| **Python** | `bool` | `True`, `False` | 0 / 1 |

**IMPORTANT:** GTV encodes boolean as integer (0 = false, 1 = true), not a dedicated boolean type.

### byte_array

| Language | Type | Example | GTV Format |
|----------|------|---------|------------|
| **Rell** | `byte_array` | `x"deadbeef"` | `GtvByteArray` |
| **Kotlin** | `ByteArray` | `byteArrayOf(0xde.toByte(), ...)` | `GtvByteArray` |
| **TypeScript** | `Uint8Array` | `new Uint8Array([0xde, ...])` | Uint8Array |
| **JavaScript** | Uint8Array | `new Uint8Array([0xde, ...])` | Uint8Array |
| **Python** | `bytes` | `b"\xde\xad\xbe\xef"` | bytes |

**Notes:**
- Fixed-width byte sequences
- Efficient binary data storage
- Commonly used for cryptographic keys and hashes

### decimal

| Language | Type | Example | GTV Format |
|----------|------|---------|------------|
| **Rell** | `decimal` | `3.14159` | `GtvString("3.14159")` |
| **Kotlin** | `BigDecimal` | `BigDecimal("3.14159")` | `GtvString` |
| **TypeScript** | `string` | `"3.14159"` | String |
| **JavaScript** | String | `"3.14159"` | String |
| **Python** | `str` or `Decimal` | `"3.14159"` or `Decimal("3.14159")` | str |

**IMPORTANT:** GTV encodes decimals as strings to preserve precision. Do NOT use JavaScript `number` for decimals.

**Why Strings?**
- Avoids floating-point precision errors
- Preserves exact decimal representation
- Deterministic serialization

**Client-Side Handling:**
- Parse string to appropriate decimal type
- Kotlin: `BigDecimal(string)`
- TypeScript: Use `decimal.js` or `big.js` library
- Python: `Decimal(string)` from `decimal` module

### rowid

| Language | Type | Notes | GTV Format |
|----------|------|-------|------------|
| **Rell** | `rowid` | Entity unique identifier | `GtvInteger` |
| **Kotlin** | `Long` | 64-bit integer | `GtvInteger` |
| **TypeScript** | `number` | Within safe integer range | Number |
| **JavaScript** | Number | Within safe integer range | Number |
| **Python** | `int` | Arbitrary precision | int |

**Notes:**
- Auto-generated for entities
- Unique per entity instance
- Typically starts at 1 and increments

## Collection Types

### list\<T\>

| Language | Type | Example | Ordered? | Duplicates? | GTV Format |
|----------|------|---------|----------|-------------|------------|
| **Rell** | `list<T>` | `[1, 2, 3]` | Yes | Yes | `GtvArray` |
| **Kotlin** | `List<T>` | `listOf(1, 2, 3)` | Yes | Yes | `GtvArray` |
| **TypeScript** | `T[]` | `[1, 2, 3]` | Yes | Yes | Array |
| **JavaScript** | Array | `[1, 2, 3]` | Yes | Yes | Array |
| **Python** | `List[T]` | `[1, 2, 3]` | Yes | Yes | list |

**Notes:**
- Order preserved
- Duplicates allowed
- Zero-indexed

### set\<T\>

| Language | Type | Example | Ordered? | Duplicates? | GTV Format |
|----------|------|---------|----------|-------------|------------|
| **Rell** | `set<T>` | `set([1, 2, 3])` | No | No | `GtvArray` |
| **Kotlin** | `Set<T>` | `setOf(1, 2, 3)` | No | No | `GtvArray` |
| **TypeScript** | `T[]` | `[1, 2, 3]` | No | No | Array |
| **JavaScript** | Array | `[1, 2, 3]` | No | No | Array |
| **Python** | `Set[T]` | `{1, 2, 3}` | No | No | list |

**IMPORTANT:** GTV encodes sets as arrays. Order not guaranteed.

**TypeScript/JavaScript:**
Generated code uses `T[]` (arrays), not `Set<T>`. Clients must deduplicate if needed.

### map\<K, V\>

| Language | Type | Example | GTV Format |
|----------|------|---------|------------|
| **Rell** | `map<K, V>` | `["a": 1, "b": 2]` | `GtvDict` |
| **Kotlin** | `Map<K, V>` | `mapOf("a" to 1, "b" to 2)` | `GtvDict` |
| **TypeScript** | `Record<K, V>` or `Map<K, V>` | `{ a: 1, b: 2 }` or `new Map(...)` | Object or Map |
| **JavaScript** | Object or Map | `{ a: 1, b: 2 }` or `new Map(...)` | Object or Map |
| **Python** | `Dict[K, V]` | `{"a": 1, "b": 2}` | dict |

**Notes:**
- Key-value pairs
- Keys must be unique
- Unknown: Whether map key order is preserved in GTV

## Special Types

### pubkey

| Language | Type | Size | GTV Format |
|----------|------|------|------------|
| **Rell** | `pubkey` | 33 bytes (compressed) | `GtvByteArray` |
| **Kotlin** | `ByteArray` | 33 bytes | `GtvByteArray` |
| **TypeScript** | `Uint8Array` | 33 bytes | Uint8Array |
| **JavaScript** | Uint8Array | 33 bytes | Uint8Array |
| **Python** | `bytes` | 33 bytes | bytes |

**Notes:**
- Compressed elliptic curve public key (secp256k1)
- Used for authentication and signatures
- Always 33 bytes

### blockchain_rid

| Language | Type | Size | GTV Format |
|----------|------|------|------------|
| **Rell** | `blockchain_rid` | 32 bytes | `GtvByteArray` |
| **Kotlin** | `ByteArray` | 32 bytes | `GtvByteArray` |
| **TypeScript** | `Uint8Array` | 32 bytes | Uint8Array |
| **JavaScript** | Uint8Array | 32 bytes | Uint8Array |
| **Python** | `bytes` | 32 bytes | bytes |

**Notes:**
- Blockchain identifier (hash)
- Unique per blockchain
- Always 32 bytes

### gtv

| Language | Type | Notes | GTV Format |
|----------|------|-------|------------|
| **Rell** | `gtv` | Raw GTV value | `Gtv` |
| **Kotlin** | `Gtv` | `net.postchain.gtv.Gtv` | `Gtv` |
| **TypeScript** | `any` or `Gtv` | Dynamic type | any |
| **JavaScript** | any | Dynamic type | any |
| **Python** | `Any` or GTV wrapper | Dynamic type | Any |

**Notes:**
- Represents arbitrary GTV-encoded data
- No static type checking
- Use for dynamic/polymorphic data

## Custom Types

### entity

Entities are NOT serialized as complete objects. Only their `rowid` is transmitted.

| Language | Field in Client | GTV Format |
|----------|-----------------|------------|
| **Kotlin** | Separate data class (not nested) | Entity fields as `GtvDict` (query output) |
| **TypeScript** | Separate interface | Entity fields as object |
| **Python** | Separate dataclass | Entity fields as dict |

**Query Return:**
When a query returns an entity, the client receives all fields as a struct-like object.

**Operation Parameter:**
When an operation takes an entity parameter, only the `rowid` is passed.

**Example:**

**Rell:**
```rell
entity user {
    name: text;
}

query get_user(user_id: rowid): user;
operation link_user(other_user: user);
```

**Client Usage (TypeScript):**
```typescript
// Query returns full user object
const user: User = await getUser(client, 1);
// { name: "Alice" }

// Operation takes rowid, not full object
await linkUser(client, 1);  // Pass rowid, not user object
```

### struct

Structs are serialized differently for input vs output.

**Input (Operation Parameters):**
```
GtvArray [value1, value2, value3]  // Positional
```

**Output (Query Results):**
```
GtvDict { "field1": value1, "field2": value2, "field3": value3 }  // Named
```

**Example:**

**Rell:**
```rell
struct point {
    x: integer;
    y: integer;
}

operation move(p: point);
query get_position(): point;
```

**Generated Kotlin:**
```kotlin
data class Point(val x: Long, val y: Long)

// Operation serializes as array
fun TransactionBuilder.move(p: Point) {
    this.addOperation("move", mapOf(
        "p" to GtvArray(listOf(GtvInteger(p.x), GtvInteger(p.y)))
    ))
}

// Query deserializes from dict
fun PostchainQuery.getPosition(): Point {
    val result = this.query("get_position", emptyMap()) as GtvDict
    return Point(
        x = (result["x"] as GtvInteger).value,
        y = (result["y"] as GtvInteger).value
    )
}
```

**Why Asymmetric?**
Historical Rell design decision. Unknown: No rationale documented.

### enum

| Language | Type | Encoding | GTV Format |
|----------|------|----------|------------|
| **Rell** | `enum` | Ordinal (0-indexed) | `GtvInteger` |
| **Kotlin** | `enum class` | Ordinal | `GtvInteger(ordinal)` |
| **TypeScript** | `enum` | Ordinal | Number |
| **JavaScript** | Object (numeric constants) | Ordinal | Number |
| **Python** | `Enum` class | Ordinal | int |

**Example:**

**Rell:**
```rell
enum color { red, green, blue }
```

**GTV Encoding:**
- `red` → `GtvInteger(0)`
- `green` → `GtvInteger(1)`
- `blue` → `GtvInteger(2)`

**IMPORTANT:** Enum order matters. Adding/removing/reordering enum values breaks compatibility.

## Nullable Types

### Rell `T?` (Nullable)

| Language | Type | Null Representation | GTV Format |
|----------|------|---------------------|------------|
| **Rell** | `T?` | `null` | `GtvNull` |
| **Kotlin** | `T?` | `null` | `GtvNull` |
| **TypeScript** | `T \| null` | `null` | null |
| **JavaScript** | T or null | `null` | null |
| **Python** | `Optional[T]` | `None` | None |

**Example:**

**Rell:**
```rell
entity profile {
    bio: text?;
}
```

**Generated Kotlin:**
```kotlin
data class Profile(val bio: String?)
```

**Generated TypeScript:**
```typescript
interface Profile {
    bio: string | null;
}
```

## Tuple Types

Tuples are NOT a first-class Rell type but can be returned from queries.

**Rell:**
```rell
query get_pair(): (integer, text);
```

**Generated Code:**
Creates anonymous result struct:

**Kotlin:**
```kotlin
data class GetPairResult(val item1: Long, val item2: String)
```

**TypeScript:**
```typescript
interface GetPairResult {
    item1: number;
    item2: string;
}
```

## GTV Encoding Summary

| GTV Type | Rell Types | Binary Format |
|----------|------------|---------------|
| `GtvInteger` | `integer`, `big_integer`, `boolean`, `enum`, `rowid` | Variable-length integer |
| `GtvString` | `text`, `decimal` | UTF-8 encoded string |
| `GtvByteArray` | `byte_array`, `pubkey`, `blockchain_rid` | Raw bytes |
| `GtvArray` | `list<T>`, `set<T>`, struct (input) | Ordered elements |
| `GtvDict` | `map<K,V>`, struct (output), entity (output) | Key-value pairs |
| `GtvNull` | `T?` (nullable) | Null marker |

## Common Type Mapping Issues

### 1. JavaScript Integer Overflow

**Problem:** Rell `integer` exceeds JavaScript safe integer range.

**Solution:** Use `big_integer` instead:

```rell
// Bad
entity transaction {
    amount: integer;  // May overflow JavaScript
}

// Good
entity transaction {
    amount: big_integer;  // Safe for large values
}
```

### 2. Decimal Precision Loss

**Problem:** Using JavaScript `number` for decimals loses precision.

**Solution:** Always use string-based decimal libraries:

```typescript
import Decimal from 'decimal.js';

const price = new Decimal(product.price);  // product.price is string
const total = price.times(quantity);
```

### 3. Struct Input/Output Asymmetry

**Problem:** Struct serialization differs for input vs output.

**Solution:** Generated code handles this automatically. Do NOT manually serialize structs.

### 4. Enum Ordering

**Problem:** Reordering enum values breaks existing data.

**Solution:** Never reorder enums. Only append new values at the end.

```rell
// Original
enum status { active, inactive }

// Safe: Append new value
enum status { active, inactive, suspended }

// DANGEROUS: Reordering breaks compatibility
enum status { inactive, active, suspended }  // DON'T DO THIS
```
