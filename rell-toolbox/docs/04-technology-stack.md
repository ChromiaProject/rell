# Technology Stack

Rationale for each technology choice in Rell Toolbox.

---

## Core

- **Kotlin** — JVM language. Chosen for Java interop (LSP4J, ANTLR), null safety, and DSL ergonomics (Gradle, Koin).
- **Gradle 8.7** (Kotlin DSL) — multi-module build with version catalog (`libs.versions.toml`), Shadow JAR, ANTLR, Detekt, JaCoCo. Parallel builds and caching enabled; 4GB JVM heap.
- **JDK 21** — LTS, supported until 2029.

---

## Language Server Protocol

### LSP4J 0.23.1

Eclipse's LSP implementation. Chosen for spec compliance, maturity, and JSON-RPC handling.

Modules used:
- `org.eclipse.lsp4j` — core interfaces (`LanguageServer`, `TextDocumentService`, `WorkspaceService`)
- `org.eclipse.lsp4j.debug` — DAP (unused)
- `org.eclipse.lsp4j.websocket` — WebSocket transport (unused; stdio/socket only)

---

## Parsing

### ANTLR4 4.13.1

Parser generator. Chosen over the Rell compiler's own parser because the compiler parser is **non-recoverable** — IDEs need partial parsing of incomplete code.

- Grammar: `ast/src/main/antlr/Rell.g4`
- Generated: `RellLexer`, `RellParser`, `RellVisitor`, `RellBaseVisitor`
- Build: `antlr` plugin in `ast/build.gradle.kts`

---

## Dependency Injection

### Koin 3.5.0

Kotlin-native DI. No annotation processing, no reflection — fast builds, easy test swapping. Trade-off: DI errors surface at runtime, not compile time.

Modules: `koin-core`, `koin-logger-slf4j`. Dagger/Spring rejected as too heavy for a CLI tool.

---

## Configuration and Formatting

- **EC4J 0.3.0** — parses `.editorconfig`. Consumed by `RellFormatterOptionsResolver` in `code-quality`.
- **Java Diff Utils 4.12** — computes minimal text diffs, converted to LSP `TextEdit`s so the formatter avoids sending whole files.

---

## Rell Compiler Integration

### Rell Base 0.15.0

Reuses the Rell compiler's type checker and semantic analyzer instead of reimplementing them. The toolbox transforms its ANTLR AST into Rell's internal AST and compiles modules to extract symbols.

Dependencies:
- `net.postchain.rell:rell-base` — core compiler
- `net.postchain.rell:rell-api-base` — public tooling API

Key class prefixes: `S_` (AST), `C_` (compiled), `R_` (runtime).
