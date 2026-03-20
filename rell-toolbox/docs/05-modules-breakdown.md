# Modules Breakdown

## Document Purpose

This document provides a **detailed analysis** of each module in Rell Toolbox: directory structure, key classes, responsibilities,
and implementation details.

Assumes you've read **01-project-overview.md** and **02-architecture.md**.

---

## Module Overview Table

| Module | Lines of Code (Approx) | Primary Responsibility | External Dependencies |
|--------|----------------------|------------------------|----------------------|
| `ast/` | ~5,000 | Parsing Rell code into AST | ANTLR4, Rell Compiler |
| `common/` | ~1,000 | Shared utilities | Rell Compiler, EC4J |
| `indexer/` | ~3,000 | Workspace symbol indexing | AST, Rell Compiler |
| `code-quality/` | ~2,000 | Code formatting | AST, Indexer, Java Diff Utils |
| `language-server/` | ~8,000 | LSP server implementation | LSP4J, All other modules |
| `seeder/` | ~2,000 | Test data generation | Common, Kotlin Faker |

*Note: Line counts are estimates based on typical module sizes; not measured.*

---

## Module 1: `ast/`

### Purpose

Parse Rell source code into Abstract Syntax Trees (AST) using **ANTLR4**, and transform ANTLR AST into **Rell compiler's internal AST format**.

### Why Separate Parser?

**Problem**: Rell compiler's native parser **fails completely** on syntax errors (non-recoverable).

**Solution**: ANTLR4 parser with **error recovery** continues parsing even with syntax errors, returning partial AST. This is critical for IDE features like code completion in incomplete code.

### Key Files

#### `Rell.g4` - Grammar Definition

**Purpose**: Defines Rell syntax in ANTLR4 format.

**Maintenance**:
- Must be manually synced with Rell compiler when language changes
- Test cases validate grammar against compiler behavior

#### `AntlrRellParser.kt` - High-Level API

**Purpose**: Simplifies parsing for consumers.

**Error Handling**:
- Collects syntax errors but continues parsing
- Returns partial AST even with errors

#### `AntlrToRell.kt` - AST Transformer

**Purpose**: Converts ANTLR's parse tree into Rell compiler's AST format.

**Why Needed**: Rell compiler's type checker and semantic analyzer expect specific AST node types (`S_*` classes).

**Challenges**:
- ANTLR AST has different structure than Rell AST
- Must handle partial AST from error recovery
- Position information (line/column) must be preserved

### Dependencies

**External**:
- `org.antlr:antlr4-runtime` - ANTLR runtime
- `net.postchain.rell:rell-base` - Rell compiler (for AST classes)

### Testing Strategy

**Test Types**:
1. **Grammar Tests**: Parse valid Rell code, verify no errors
2. **Error Recovery Tests**: Parse invalid code, verify partial AST
3. **Transformation Tests**: Verify ANTLR AST → Rell AST correctness

**Test Resources**: Sample Rell code snippets in `src/test/resources/`

### Known Limitations

1. **Grammar Sync**: Manual process to sync with Rell compiler
2. **Error Messages**: ANTLR error messages may differ from compiler's

---

## Module 2: `common/`

### Purpose

Provide **shared utilities** used across all modules to avoid code duplication.


### Key Components

#### EditorConfig Support

**File**: `editorconfig/RellFormatterOptionsResolver.kt`

**Purpose**: Read and parse `.editorconfig` files for formatting options.

**Integration**: Used by `code-quality` module for formatting.

#### Resource Abstraction

**File**: `resources/RellResource.kt`

**Purpose**: Represent compiled Rell files in a uniform way.

**Why Needed**: Different sources (filesystem, memory, archive) need common interface.

#### Workspace Utilities

**File**: `workspace/WorkspaceUtils.kt`

**Purpose**: Path handling, file discovery, workspace structure helpers.

**Example Functions**:
- `findRellFiles(directory)` - Recursively find `.rell` files
- `resolveModulePath(module)` - Map module name to file path
- `isWorkspaceRoot(directory)` - Detect workspace root (e.g., presence of `rell.json`)


### Dependencies

**External**:
- `net.postchain.rell:rell-base` - Rell compiler
- `org.ec4j.core:ec4j-core` - EditorConfig parsing


### Testing Strategy

**Focus**: Unit tests for individual utilities (file path handling, etc.).

---

## Module 3: `indexer/`

### Purpose

Analyze entire Rell projects and build **symbol indexes** for IDE features (go-to-definition, find-references, code completion).


### Dependencies

**External**:
- `net.postchain.rell:rell-base` - Rell compiler

**Internal**:
- `ast/` - Parsing
- `common/` - Workspace utilities

### Caching Strategy

**Problem**: Indexing large projects is slow (seconds).

**Solution**: Serialize index to disk, load on next startup.

**Implementation**: Done in `language-server/` module (see caching section).

### Testing Strategy

**Test Resources**: `src/test/resources/sample-projects/`
- Real-world Rell projects
- Tests verify symbols are extracted correctly
- Tests verify references are tracked correctly


---

## Module 4: `code-quality/`

### Purpose

Apply **consistent formatting rules** to Rell code and provide **code quality analysis**.

### Key Components

#### `RellFormatter.kt` - Main Formatter

**Purpose**: Format Rell code according to rules.


**Formatting Rules**:
1. **Indentation**: Spaces vs tabs, indent size
2. **Spacing**: Around operators, after commas
3. **Line Breaks**: Function declarations, block statements
4. **Alignment**: Multi-line expressions

#### `FormattableDocument.kt` - Edit Tracking

**Purpose**: Track text edits during formatting.

**Why Needed**: Formatting rules may conflict; document tracks all edits and resolves conflicts.

#### Diff Utilities

**File**: `diff/DiffUtils.kt`

**Purpose**: Compute minimal text edits (for LSP `TextEdit` format).

**Integration**: Uses `java-diff-utils` library.

**Why Minimal Edits**: IDEs prefer minimal edits (preserves cursor position, undo history).

### Dependencies

**External**:
- `io.github.java-diff-utils:java-diff-utils` - Diff computation

**Internal**:
- `ast/` - Parsing
- `common/` - EditorConfig support
- `indexer/` - Symbol information (possibly)

---

## Module 5: `language-server/` (Main Deliverable)

### Purpose

Implement **Language Server Protocol (LSP)** to provide IDE integration for Rell.

This is the **primary deliverable** of Rell Toolbox.

### Key Components

#### `RellLanguageServer.kt` - Main Server

**Purpose**: Implements LSP4J interfaces.


**Lifecycle**:
1. **Initialize**: IDE sends workspace root, capabilities
2. **Indexing**: Server indexes workspace (may take seconds)
3. **Initialized**: Server ready, sends `textDocument/publishDiagnostics`
4. **Requests**: IDE sends LSP requests (completion, hover, etc.)
5. **Shutdown**: IDE sends shutdown request
6. **Exit**: Server exits

#### `RellWorkspaceManager.kt` - State Management

**Purpose**: Manage indexed workspace state.

**Responsibilities**:
- Load/save index cache
- Track open documents
- Incremental updates (future)


#### Entry Points

**`StdioMain.kt`** (Production):
**`SocketMain.kt`** (Development):


### LSP Features Implemented


### Caching (Fury Serialization)

**File**: `caching/IndexCache.kt`
**Purpose**: Serialize workspace index to disk for fast startup.
**Cache Key**: Hash of workspace root path

### Dependencies

**External**:
- `org.eclipse.lsp4j:org.eclipse.lsp4j` - LSP protocol
- `io.insert-koin:koin-core` - Dependency injection
- `org.furyio:fury-core` - Serialization
- `io.sentry:sentry-log4j2` - Error tracking

**Internal**:
- `ast/` - Parsing
- `common/` - Utilities
- `indexer/` - Symbol indexing
- `code-quality/` - Formatting

### Build Artifacts

**Regular JAR**: `build/libs/language-server-dev.jar` (no dependencies)

**Shadow JAR**: `build/libs/language-server-dev-all.jar` (with dependencies)

**IDE extensions use**: Shadow JAR (standalone executable)

### Testing Strategy

**Unit Tests**: Individual feature handlers (completion, diagnostics, etc.)

**Integration Tests**: Full LSP request/response cycle

**System Stubs**: Mock stdin/stdout for stdio mode tests


## Module 6: `seeder/`

### Purpose

Generate realistic **test data** for Rell applications.



#### Schema Parser

**File**: `schema/SchemaReader.kt`

**Purpose**: Extract entity definitions from Rell code.

#### Data Generation

**File**: `generator/DataGenerator.kt`
**Purpose**: Generate fake data using Kotlin Faker.


#### Export Formats
- **Rell** (`exporter/RellDataExporter.kt`)
- **JSON** (`exporter/JsonExporter.kt`)
- **YAML** (`exporter/YamlDataExporter.kt`)
- **SQL** (`exporter/SqlDataExporter.kt`)
- **CSV** (`exporter/CsvDataExporter.kt`)

### Dependencies

**External**:
- `io.github.serpro69:kotlin-faker` - Fake data generation
- `com.fasterxml.jackson` - JSON/YAML serialization

**Internal**:
- `common/` - Workspace utilities

### Testing Strategy

**Unit Tests**: Each generator type (faker, random, etc.)

**Integration Tests**: End-to-end (parse schema → generate → export)

---