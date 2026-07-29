# Modules Breakdown

## Document Purpose

This document provides a **detailed analysis** of each module in Rell Toolbox: directory structure, key classes, responsibilities,
and implementation details.

Assumes you've read **01-project-overview.md** and **02-architecture.md**.

---

## Module Overview Table

| Module | Primary Responsibility |
|--------|------------------------|
| `ast/` | Parsing Rell code into AST |
| `common/` | Shared utilities |
| `indexer/` | Workspace symbol indexing |
| `code-quality/` | Code formatting |
| `language-server/` | LSP server implementation |
| `seeder/` | Test data generation |

---

## Module 1: `ast/`

### Purpose

Parse Rell source code into Abstract Syntax Trees (AST) using **ANTLR4**, and transform ANTLR AST into **Rell compiler's internal AST format**.

### Why Separate Parser?

**Problem**: Rell compiler's native parser **fails completely** on syntax errors (non-recoverable).

**Solution**: ANTLR4 parser with **error recovery** continues parsing even with syntax errors, returning partial AST. This is critical for IDE features like code completion in incomplete code.

### Key Files

The `Rell.g4` grammar and the lexer/parser generated from it live in `:rell-base:frontend`; this module
depends on that for them.

#### `AntlrRellParser.kt` - High-Level API

**Purpose**: Simplifies parsing for consumers.

**Error Handling**:
- Collects syntax errors but continues parsing
- Returns partial AST even with errors

#### `RellCompilerApi.kt` - AST Transformer

**Purpose**: Converts ANTLR's parse tree into Rell compiler's AST format, delegating to the compiler's `RellAntlrVisitor`.

**Why Needed**: Rell compiler's type checker and semantic analyzer expect specific AST node types (`S_*` classes).

**Challenges**:
- ANTLR AST has different structure than Rell AST
- Must handle partial AST from error recovery
- Position information (line/column) must be preserved


### Testing Strategy

**Test Types**:
1. **Grammar Tests**: Parse valid Rell code, verify no errors
2. **Error Recovery Tests**: Parse invalid code, verify partial AST
3. **Transformation Tests**: Verify ANTLR AST → Rell AST correctness

Grammar/parser correctness tests are slow and tagged `grammar`, so they are excluded from `test` and
`check`. Run them explicitly after grammar or parser changes:

```bash
./gradlew :rell-toolbox:ast:grammarTest
```

### Known Limitations

**Error Messages**: ANTLR error messages may differ from the compiler's hand-written parser.

---

## Module 2: `common/`

### Purpose

Provide **shared utilities** used across all modules to avoid code duplication.


### Key Components

#### EditorConfig Support

**File**: `editorconfig/EditorConfigParser.kt`

**Purpose**: Read and parse `.editorconfig` files for formatting and linter options.

**Integration**: `formatter/FormatterOptions.kt` and `linter/LinterOptions.kt` build on it; the language server resolves per-document options in `RellFormatterOptionsResolver`.

#### Resource Abstraction

**File**: `indexer/Resource.kt`

**Purpose**: Represent compiled Rell files in a uniform way.

**Why Needed**: Different sources (filesystem, memory, archive) need common interface.

#### Shared Utilities

**File**: `common/CommonUtils.kt`

**Purpose**: URI handling and offset/position conversion used across modules.



### Testing Strategy

**Focus**: Unit tests for individual utilities (file path handling, etc.).

---

## Module 3: `indexer/`

### Purpose

Analyze entire Rell projects and build **symbol indexes** for IDE features (go-to-definition, find-references, code completion).



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

**File**: `formatter/Diff.kt`

**Purpose**: Compute minimal text edits (for LSP `TextEdit` format).

**Integration**: Uses `java-diff-utils` library.

**Why Minimal Edits**: IDEs prefer minimal edits (preserves cursor position, undo history).


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


### Caching (Fory Serialization)

**File**: `caching/RellIndexCachingService.kt`
**Purpose**: Serialize workspace index to disk for fast startup.
**Cache Key**: Hash of workspace root path


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
- **JSON** (`exporter/JsonDataExporter.kt`)
- **YAML** (`exporter/YamlDataExporter.kt`)
- **SQL** (`exporter/SqlDataExporter.kt`)
- **CSV** (`exporter/CsvDataExporter.kt`)


### Testing Strategy

**Unit Tests**: Each generator type (faker, random, etc.)

**Integration Tests**: End-to-end (parse schema → generate → export)

---