# Technology Stack

Rationale for each technology choice in Rell Toolbox. This is not a dependency manifest — the
build files and `gradle/libs.versions.toml` are authoritative for what is used and at which version.

---

## Core

- **Kotlin** — JVM language. Chosen for Java interop (LSP4J, ANTLR), null safety, and DSL ergonomics (Gradle, Koin).
- **Gradle** (Kotlin DSL) — see the root [DEVELOPMENT.md](../../DEVELOPMENT.md) for build setup and prerequisites.
- **JDK 21** — LTS, supported until 2029.

---

## Language Server Protocol

### LSP4J

Eclipse's LSP implementation. Chosen for spec compliance, maturity, and JSON-RPC handling. The server
implements `LanguageServer`, `TextDocumentService` and `WorkspaceService`; only the stdio and socket
transports are used.

---

## Parsing

### ANTLR4

Parser generator. Chosen over the Rell compiler's hand-written parser because that one is **non-recoverable** — IDEs need partial parsing of incomplete code.

- Grammar: `rell-base/frontend/src/main/antlr/Rell.g4`
- Generated: `RellLexer`, `RellParser`, `RellVisitor`, `RellBaseVisitor` in package `net.postchain.rell.base.compiler.parser.antlr`
- Build: `antlr` plugin in `rell-base/frontend/build.gradle.kts`; `ast/` consumes these via `:rell-base:frontend`

---

## Dependency Injection

### Koin

Kotlin-native DI. No annotation processing, no reflection — fast builds, easy test swapping. Trade-off: DI errors surface at runtime, not compile time.

Dagger/Spring rejected as too heavy for a CLI tool.

---

## Configuration and Formatting

- **EC4J** — parses `.editorconfig`. Wrapped by `EditorConfigParser` in `common`; the language server resolves formatter options on top of it in `RellFormatterOptionsResolver`.
- **Java Diff Utils** — computes minimal text diffs, converted to LSP `TextEdit`s so the formatter avoids sending whole files.

---

## Rell Compiler Integration

### Rell Base

Reuses the Rell compiler's type checker and semantic analyzer instead of reimplementing them. The toolbox feeds the ANTLR parse tree back into Rell's internal AST and compiles modules to extract symbols.

Key class prefixes: `S_` (AST), `C_` (compiled), `R_` (runtime).
