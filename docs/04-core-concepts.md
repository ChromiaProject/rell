# Core Concepts

This document explains the fundamental concepts you need to understand to use rell-codegen effectively.

## What is Rell?

**Rell** is a domain-specific programming language designed for writing dApps on the **Chromia blockchain platform**.

### Rell Language Features

#### Entities (Blockchain State)
Entities represent persistent data stored on the blockchain:

```rell
entity user {
    name: text;
    age: integer;
    email: text;
}
```

- Similar to database tables or rows
- Each entity instance is stored on-chain
- Has an implicit `rowid` (unique identifier)
- Can be queried and modified through operations

#### Structs (Data Structures)
Structs are custom composite types (not stored directly on-chain):

```rell
struct address {
    street: text;
    city: text;
    postal_code: text;
}
```

- Used for grouping related data
- Can be passed as parameters or return values
- Not persisted directly (only as part of entities)

#### Enums (Enumeration Types)
Enums define a fixed set of named values:

```rell
enum user_role {
    admin,
    moderator,
    member
}
```

- Stored as integers internally (ordinal values)
- Provide type-safe named constants

#### Queries (Read Operations)
Queries retrieve data from the blockchain without modifying state:

```rell
query get_user_by_id(user_id: rowid): user {
    return user @ { .rowid == user_id };
}

query count_users(): integer {
    return user @* {} ( .rowid ).size();
}
```

- **Read-only**: Cannot modify blockchain state
- **No transaction cost**: Free to execute
- **Return values**: Can return entities, structs, primitives, or collections

#### Operations (Write Operations)
Operations modify blockchain state and require cryptographic signatures:

```rell
operation create_user(name: text, age: integer) {
    create user(name, age);
}

operation update_user_age(user_id: rowid, new_age: integer) {
    update user @ { .rowid == user_id } ( age = new_age );
}
```

- **State-modifying**: Change blockchain data
- **Transaction cost**: Require fees to execute
- **Signatures required**: Must be signed by authorized accounts
- **No return values**: Operations do not return data (use queries after)

### Rell Type System

#### Primitive Types

| Rell Type | Description                        | Example Values             |
|-----------|------------------------------------|----------------------------|
| `text` | UTF-8 string                       | `"hello"`, `"world"`       |
| `integer` | 64-bit signed integer              | `42`, `-100`, `0`          |
| `big_integer` | Large integers with high precision | `123456789L`               |
| `boolean` | True or false                      | `true`, `false`            |
| `byte_array` | Byte sequence                      | `x"deadbeef"`              |
| `decimal` | Fixed-point decimal                | `3.14159`, `0.001`         |
| `rowid` | Entity row identifier              | (implicit, auto-generated) |

#### Collection Types

| Rell Type | Description | Ordered? | Duplicates? |
|-----------|-------------|----------|-------------|
| `list<T>` | Ordered collection | Yes | Yes |
| `set<T>` | Unique collection | No | No |
| `map<K, V>` | Key-value pairs | No | No (keys) |

Example:
```rell
struct user_profile {
    tags: list<text>;
    favorite_numbers: set<integer>;
    metadata: map<text, text>;
}
```

#### Special Types

| Rell Type | Description | Usage |
|-----------|-------------|-------|
| `pubkey` | Cryptographic public key | User authentication |
| `gtv` | Generic type value (serialized data) | Dynamic data storage |

## What is GTV?

**GTV (Generic Type Value)** is Rell's serialization format for encoding data in blockchain transactions.

### Why GTV Exists

Blockchain transactions must be:
- **Deterministic**: Same input always produces same output
- **Compact**: Minimize storage and bandwidth
- **Type-safe**: Preserve type information

GTV provides a binary serialization format that meets these requirements.

### GTV Type Mappings

When Rell values are sent to/from the blockchain, they're encoded as GTV:

| Rell Type | Input GTV Type | Output GTV Type | Notes |
|-----------|----------------|-----------------|-------|
| `integer` | `GtvInteger` | `GtvInteger` | 64-bit signed |
| `big_integer` | `GtvBigInteger` | `GtvBigInteger` | Arbitrary precision |
| `text` | `GtvString` | `GtvString` | UTF-8 encoded |
| `boolean` | `GtvInteger` | `GtvInteger` | 0 = false, 1 = true |
| `byte_array` | `GtvByteArray` | `GtvByteArray` | Raw bytes |
| `decimal` | `GtvString` | `GtvString` | Encoded as decimal string |
| `rowid` | `GtvInteger` | `GtvInteger` | Entity row ID |
| `entity` | `GtvInteger` | `GtvInteger` | Represented as row ID |
| `struct` | `GtvArray` | `GtvDict` | **Asymmetric** (see below) |
| `enum` | `GtvInteger` | `GtvInteger` | Ordinal value |
| `list<T>` | `GtvArray` | `GtvArray` | Array of encoded elements |
| `set<T>` | `GtvArray` | `GtvArray` | Array (order not guaranteed) |
| `map<K,V>` | `GtvDict` | `GtvDict` | Dictionary with key-value pairs |

### Struct Serialization Asymmetry

**Important**: Structs have different input and output formats:

**Input (to blockchain)**:
```
GtvArray [value1, value2, value3]  // Positional array
```

**Output (from blockchain)**:
```
GtvDict { "field1": value1, "field2": value2, "field3": value3 }  // Named dictionary
```

**Why?** Historical Rell design decision. Input uses positional encoding for compactness, output uses named fields for clarity.

**Impact on Generated Code**: Client libraries must handle both formats:
- Serialize structs as arrays for operation parameters
- Deserialize structs as dictionaries from query results

## How rell-codegen Uses These Concepts

### Type Extraction
rell-codegen parses Rell source to extract:
1. All entity definitions → Generate client data types
2. All struct definitions → Generate client data types
3. All enum definitions → Generate client enum types
4. All query signatures → Generate client query methods
5. All operation signatures → Generate client operation methods

### Type Mapping
For each Rell type, rell-codegen determines the equivalent type in the target language:

**Example: Rell `user` entity**
```rell
entity user {
    name: text;
    age: integer;
    active: boolean;
}
```

**Generated Kotlin:**
```kotlin
data class User(
    val name: String,
    val age: Long,
    val active: Boolean
)
```

**Generated TypeScript:**
```typescript
interface User {
    name: string;
    age: number;
    active: boolean;
}
```

**Generated Python:**
```python
@dataclass
class User:
    name: str
    age: int
    active: bool
```

### Serialization/Deserialization
Generated code includes logic to:
- **Serialize** client types to GTV for sending to blockchain (operations)
- **Deserialize** GTV responses from blockchain to client types (queries)

**Example: Operation Parameter Serialization (Kotlin)**
```kotlin
fun TransactionBuilder.createUser(name: String, age: Long) {
    ...
}
```

**Example: Query Result Deserialization (Kotlin)**
```kotlin
fun PostchainQuery.getUser(userId: Long): User {
    ...
}
```

### Dependency Resolution
rell-codegen analyzes type dependencies to ensure correct import order:

## Rell Compilation Process

rell-codegen uses Rell's compiler API (`rell-api-base`) to compile Rell source:

This produces a compiled **Rell AST (Abstract Syntax Tree)** containing:
- All type definitions with full metadata
- All function signatures with parameter and return types
- All dependencies between types
- Error diagnostics (if compilation fails)

The compiled AST provides structured access to Rell definitions, which rell-codegen traverses to extract information.
