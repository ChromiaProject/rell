# Technology Stack

## Document Purpose

This document explains **what technologies** are used in Rell Toolbox and **why** each was chosen. It provides context for engineers unfamiliar with the stack.

---

## Core Technologies

### Kotlin (Language)

**What It Is**: JVM-based programming language with enhanced type safety and concurrency features.

**Version**: Managed by Gradle plugin (typically 1.9.x based on test dependencies)

**Why Chosen**:
- ✅ **Interoperability**: Seamless integration with Java libraries (LSP4J, ANTLR, etc.)
- ✅ **Null Safety**: Eliminates null pointer exceptions at compile time
- ✅ **Coroutines**: Built-in async/concurrency support (not heavily used in this project)
- ✅ **DSL Capabilities**: Clean syntax for Gradle, Koin modules
- ✅ **Type Inference**: Less boilerplate than Java

**Trade-offs**:
- ❌ Slightly slower compilation than Java
- ❌ Smaller community than Java or JavaScript

---

### Gradle 8.7 (Build Tool)

**What It Is**: Build automation tool for JVM projects.

**Configuration**: Kotlin DSL (`build.gradle.kts` instead of Groovy)

**Why Chosen**:
- ✅ **Multi-Module Support**: Clean separation of 6 modules
- ✅ **Dependency Management**: Version catalog (`libs.versions.toml`)
- ✅ **Plugin Ecosystem**: Shadow JAR, ANTLR generation, Detekt, JaCoCo
- ✅ **Incremental Builds**: Fast iteration after initial build
- ✅ **Caching**: Local and remote build caching enabled

**Configuration Highlights**:
- **Parallel Builds**: `org.gradle.parallel=true`
- **Build Caching**: `org.gradle.caching=true`
- **JVM Heap**: 4GB (`org.gradle.jvmargs=-Xmx4096m`)

---

### JDK 21 (Runtime)

**What It Is**: Java Development Kit, version 21 (LTS release, September 2023).

**Why This Version**:
- ✅ **Long-Term Support**: Maintained until 2029
- ✅ **Performance**: G1GC improvements, faster startup
- ✅ **Modern Features**: Virtual threads, pattern matching (not used yet but available)


---

## Language Server Protocol (LSP) Stack

### LSP4J 0.23.1

**What It Is**: Eclipse's Java implementation of the Language Server Protocol.

**Why Chosen**:
- ✅ **Standard Compliance**: Implements LSP specification exactly
- ✅ **Mature**: Used by many language servers (Java, Groovy, etc.)
- ✅ **JSON-RPC Handling**: Abstracts communication protocol
- ✅ **Extensibility**: Easy to add custom LSP endpoints

**Modules Used**:
- `org.eclipse.lsp4j` - Core LSP interfaces (`LanguageServer`, `TextDocumentService`, etc.)
- `org.eclipse.lsp4j.debug` - Debug Adapter Protocol (unused in this codebase)
- `org.eclipse.lsp4j.websocket` - WebSocket transport (unused; stdio/socket only)


**Key Interfaces Implemented**:
- `LanguageServer` - Top-level server
- `TextDocumentService` - Core LSP features (completion, diagnostics, etc.)
- `WorkspaceService` - Workspace-level operations

---

## Parsing and AST

### ANTLR4 4.13.1

**What It Is**: Parser generator that produces Java/Kotlin parsers from grammars.

**Why Chosen**:
- ✅ **Error Recovery**: Continues parsing after syntax errors (critical for IDEs)
- ✅ **LL(*) Parsing**: Handles complex grammars without left recursion elimination
- ✅ **Tooling**: IDE plugins, grammar visualization, debugging
- ✅ **Performance**: Fast enough for real-time IDE use

**Grammar File**: `ast/src/main/antlr/Rell.g4`

**Generated Code**:
- `RellLexer.java` - Tokenizer
- `RellParser.java` - Parser
- `RellVisitor.java`, `RellBaseVisitor.java` - AST traversal

**Why Not Use Rell Compiler's Parser**:
- Rell compiler parser is **non-recoverable** (fails completely on syntax errors)
- IDEs need partial parsing of incomplete/broken code

**Build Integration**:
```kotlin
// In ast/build.gradle.kts
plugins {
    antlr
}
```

**Alternatives Considered**:
- **Hand-written parser**: Too much work, hard to maintain
- **JavaCC**: Older, less popular than ANTLR

---

## Dependency Injection

### Koin 3.5.0

**What It Is**: Lightweight dependency injection framework for Kotlin.

**Why Chosen**:
- ✅ **Simplicity**: No annotation processing, no reflection (Kotlin DSL)
- ✅ **Lightweight**: Minimal overhead compared to Dagger/Guice
- ✅ **Testability**: Easy to swap implementations in tests
- ✅ **Kotlin-Native**: Idiomatic Kotlin API


**Trade-offs**:
- ❌ **No Compile-Time Validation**: DI errors appear at runtime
- ✅ **Fast Build Times**: No annotation processing step

**Modules**:
- `koin-core` - Core DI framework
- `koin-logger-slf4j` - SLF4J integration for Koin logs

**Alternatives Considered**:
- **Dagger**: Too complex for this use case
- **Spring**: Heavyweight for a CLI tool
- **Manual DI**: No framework; rejected for testability

---


## Configuration and Formatting

### EC4J 0.3.0

**What It Is**: Java implementation of EditorConfig file parser.

**Why Chosen**:
- ✅ **Standard Compliance**: Parses `.editorconfig` files correctly
- ✅ **Cross-Language**: EditorConfig is an industry standard
- ✅ **Integration**: Used by `code-quality` module for formatting options

**Use Case**:
```
# .editorconfig
[*.rell]
indent_size = 4
indent_style = space
```

Parsed by `RellFormatterOptionsResolver` to apply formatting rules.

**Alternatives Considered**: Parse manually (rejected; reinventing the wheel).

---

### Java Diff Utils 4.12

**What It Is**: Library for computing text diffs (like Unix `diff`).

**Why Chosen**:
- ✅ **Text Edits**: Generates minimal edits to transform text
- ✅ **LSP Integration**: Converts diffs to LSP `TextEdit` format
- ✅ **Efficiency**: Avoids sending entire file on format

**Use Case**: Code formatter generates diffs, converts to LSP edits, sends to IDE.


---

## Rell Compiler Integration

### Rell Base 0.15.0

**What It Is**: Rell compiler and runtime library.

**Modules Used**:
- `net.postchain.rell:rell-base` - Core compiler
- `net.postchain.rell:rell-api-base` - Public API for tooling

**Why Dependency Exists**:
- **Type System**: Reuse Rell's type checker and semantic analyzer
- **AST Format**: Transform ANTLR AST into Rell's internal AST
- **Module Compilation**: Compile Rell modules to extract symbols

**Key Classes Used**:
- `S_*` classes - Rell's AST nodes (e.g., `S_Function`, `S_Expr`)
- `C_*` classes - Rell's compiled representations
- `R_*` classes - Rell's runtime types


---
