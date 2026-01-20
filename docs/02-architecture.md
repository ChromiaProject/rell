# Architecture & Design

## High-Level Architecture

rell-codegen follows a **3-layer pluggable architecture** that separates concerns and allows easy extension with new target languages.

```
┌─────────────────────────────────────────────────────┐
│  CLI Layer (rellgen)                                │
│  User-facing command-line interface                 │
│  - Argument parsing (Clikt framework)               │
│  - Language option groups                           │
└─────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────┐
│  Implementation Layer (codegen-X)                   │
│  Language-specific code generation                  │
│  - codegen-kotlin                                   │
│  - codegen-typescript                               │
│  - codegen-javascript                               │
│  - codegen-python                                   │
│  - codegen-mermaid                                  │
└─────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────┐
│  Core Logic Layer (codegen)                         │
│  Abstract interfaces and orchestration              │
│  - Rell parsing and compilation                     │
│  - Type extraction and dependency analysis          │
│  - Document and section abstractions                │
└─────────────────────────────────────────────────────┘
```

## Design Pattern: Factory Pattern

The core design uses the **Factory Pattern** to enable pluggable code generation targets.

### Core Abstractions

#### DocumentFactory (Interface)
The central abstraction that each language implements:

```kotlin
interface DocumentFactory {
    // Create language-specific document sections
    fun createEntity(entity: Entity): DocumentSection
    fun createStruct(struct: Struct): DocumentSection
    fun createEnum(enum: Enum): DocumentSection
    fun createQuery(query: Query): DocumentSection
    fun createOperation(operation: Operation): DocumentSection

    // Create complete documents
    fun createDocument(sections: List<DocumentSection>): Document
}
```

Each language module provides a concrete implementation:
- `KotlinDocumentFactory`
- `TypescriptDocumentFactory`
- `JavascriptDocumentFactory`
- `PythonDocumentFactory`
- `MermaidDocumentFactory`

#### Document (Interface)
Represents a generated file:

```kotlin
interface Document {
    val name: String              // File name (without extension)
    val extension: String         // File extension (.kt, .ts, etc.)
    fun getContent(): String      // Rendered file content
}
```

#### DocumentSection (Interface)
Represents a single component within a document (entity, query, operation, etc.):

```kotlin
interface DocumentSection {
    val name: String                           // Section identifier
    val dependencies: Set<DocumentSection>     // Sections this depends on
    fun render(): String                       // Rendered code
}
```

## Code Generation Process Flow

### 1. CLI Invocation
User runs: `rellgen <source> <target> --kotlin --package com.example`

The CLI layer:
- Parses arguments using Clikt
- Validates input/output paths
- Collects language options (package names, flags, etc.)

### 2. Rell Compilation
The core layer uses Rell's compiler API (`rell-api-base`):

```kotlin
val compiledApp = RellApiCompile.compileApp(
    rellSource = sourceFiles,
    compilerOptions = options
)
```

**Output**: Compiled Rell AST with all type information

### 3. Type Extraction
The `CodeGenerator` traverses the compiled Rell app to extract:

- **Entities**: All `entity` definitions with their fields
- **Structs**: All `struct` definitions
- **Enums**: All `enum` definitions
- **Queries**: All `query` functions with signatures
- **Operations**: All `operation` functions with signatures

For each, it resolves:
- Parameter types
- Return types
- Field types
- Dependencies between types

### 4. Dependency Graph Construction
The system builds a directed graph of dependencies:

```
User (entity)
  ↓ depends on
Address (struct)
  ↓ depends on
Country (enum)
```

This ensures:
- Types are defined before use
- Import statements are correct
- Circular dependencies are handled

### 5. Section Creation
For each language, the corresponding `DocumentFactory` creates sections:

```kotlin
val kotlinFactory = KotlinDocumentFactory(config)

val entitySection = kotlinFactory.createEntity(userEntity)
val querySection = kotlinFactory.createQuery(getUserQuery)
```

Each section knows:
- How to render itself in the target language
- What other sections it depends on

### 6. Document Organization
Sections are organized into documents based on `FileSaveMode`:

- **Module Mode**: One file per Rell module
- **Dapp Mode**: One file for the entire dapp
- **Separate Mode**: One file per entity/struct/enum

The factory creates appropriate `Document` instances grouping related sections.

### 7. Code Rendering
Each document renders its content:

```kotlin
document.getContent()  // Produces complete file source code
```

This triggers:
- Section rendering in dependency order
- Import statement generation
- Package/module declarations
- Language-specific formatting

### 8. File Persistence
The `DocumentSaver` writes documents to disk:

```kotlin
DocumentSaver.save(
    documents = generatedDocuments,
    outputDirectory = targetPath
)
```

## Module Structure

### codegen/ (Core Module)
**Location**: `rell-codegen/codegen/`

**Key Components**:
- `CodeGenerator.kt` - Main orchestrator
- `document/DocumentFactory.kt` - Factory interface
- `document/Document.kt` - Document interface
- `section/DocumentSection.kt` - Section interface
- `deps/DependencyResolver.kt` - Dependency graph management
- `util/TypeMapper.kt` - Rell-to-target type conversions

**Responsibilities**:
- Parse Rell source
- Extract types and callables
- Coordinate code generation
- Manage dependencies

**Dependencies**: `rell-api-base`, `postchain-gtv`, `postchain-common`

### codegen-kotlin/ (Kotlin Implementation)
**Location**: `rell-codegen/codegen-kotlin/`

**Key Components**:
- `KotlinDocumentFactory.kt` - Factory implementation
- `KotlinDocument.kt` - Kotlin file representation
- `section/KotlinEntity.kt` - Entity-to-data-class
- `section/KotlinStruct.kt` - Struct-to-data-class
- `section/KotlinQuery.kt` - Query extension methods
- `section/KotlinOperation.kt` - Operation extension methods
- `section/DataClassSection.kt` - Reusable data class generator
- `type/KotlinBuiltinType.kt` - Builtin type mappings

**Output Style**:
```kotlin
// Entities/Structs as data classes
data class User(val name: String, val age: Long)

// Queries as PostchainQuery extensions
fun PostchainQuery.getUser(userId: Long): User { ... }

// Operations as TransactionBuilder extensions
fun TransactionBuilder.createUser(name: String, age: Long) { ... }
```

### codegen-typescript/ (TypeScript Implementation)
**Location**: `rell-codegen/codegen-typescript/`

**Key Components**:
- `TypescriptDocumentFactory.kt`
- `TypescriptDocument.kt`
- `section/TypescriptEntity.kt`
- `section/TypescriptStruct.kt`
- `section/TypescriptQuery.kt`
- `section/TypescriptOperation.kt`
- `section/DataTypeSection.kt` - Interface/type definitions
- `type/TypescriptBuiltinType.kt`

**Output Style**:
```typescript
// Entities/Structs as interfaces
interface User {
    name: string;
    age: number;
}

// Queries as async functions
async function getUser(client: IClient, userId: number): Promise<User> { ... }
```

### codegen-javascript/ (JavaScript Implementation)
**Location**: `rell-codegen/codegen-javascript/`

Similar to TypeScript but generates plain JavaScript (no type annotations).

### codegen-python/ (Python Implementation)
**Location**: `rell-codegen/codegen-python/`

**Output Style**:
```python
# Entities/Structs as dataclasses
@dataclass
class User:
    name: str
    age: int

# Queries as functions
def get_user(client: PostchainClient, user_id: int) -> User: ...
```

### codegen-mermaid/ (Mermaid Diagrams)
**Location**: `rell-codegen/codegen-mermaid/`

**Key Components**:
- `MermaidDocumentFactory.kt`
- `MermaidDocument.kt`
- `section/MermaidClass.kt` - Entity/Struct visualization
- `section/MermaidEntityReference.kt` - Relationship arrows

**Output Modes**:
- **Class Diagrams**: Show all types and relationships
- **Entity-Relationship Diagrams**: Show only entities and relationships

**Optional MDX Support**: Wraps diagrams in MDX tags for documentation frameworks

### rellgen/ (CLI Module)
**Location**: `rell-codegen/rellgen/`

**Key Components**:
- `App.kt` - Entry point (`main` function)
- `CodeGenCommand.kt` - Clikt command class
- `LanguageOption.kt` - Base for language options
- `KotlinOptionGroup.kt` - Kotlin-specific CLI flags
- `TypescriptOption.kt`, `JavascriptOption.kt`, etc.

**Responsibilities**:
- Parse CLI arguments
- Validate paths
- Instantiate appropriate factories
- Invoke `CodeGenerator`
- Handle errors and output

## Section-Based Composition

Each language implementation creates **section classes** that inherit from `DocumentSection`:

### Entity Sections
Convert Rell entities to language-specific representations:
- Kotlin: `data class` with `@Serializable`
- TypeScript: `interface`
- Python: `@dataclass`

### Struct Sections
Convert Rell structs similarly to entities.

### Enum Sections
Convert Rell enums to native enum types:
- Kotlin: `enum class`
- TypeScript: `enum`
- Python: `Enum` class

### Query Sections
Generate read-only query methods:
- Kotlin: Extension methods on `PostchainQuery`
- TypeScript: Async functions using `IClient.query()`
- Python: Functions using `PostchainClient.query()`

### Operation Sections
Generate state-modifying transaction methods:
- Kotlin: Extension methods on `TransactionBuilder`
- TypeScript: Async functions using `IClient.call()`
- Python: Functions using `PostchainClient.call()`

### Builtin Sections
Handle built-in Rell types requiring special serialization:
- `big_integer` → Kotlin `BigInteger`, TypeScript `bigint`
- `byte_array` → Kotlin `ByteArray`, TypeScript `Uint8Array`
- `decimal` → Kotlin `BigDecimal`, TypeScript `string` (serialized)
- `gtv` → GTV serialization utilities

## Design Decisions & Rationale

### Why Factory Pattern?
- **Extensibility**: Adding new languages requires implementing one interface
- **Separation of Concerns**: Core logic doesn't know about language specifics
- **Testability**: Can mock factories for testing
- **Consistency**: All languages follow the same generation process

### Why Section-Based?
- **Modularity**: Each component (entity, query, etc.) is self-contained
- **Dependency Management**: Sections declare dependencies explicitly
- **Rendering Order**: Can sort sections by dependencies
- **Reusability**: Common patterns (like data classes) can be extracted into base classes

### Why Three Layers?
- **CLI Layer**: Keeps user interface concerns separate
- **Implementation Layer**: Allows language-specific optimizations
- **Core Layer**: Provides stable abstractions and business logic

