# Project Overview

## Document Purpose

This document provides a high-level introduction to the Rell Toolbox project for engineers with general software development knowledge but **zero prior context** about Chromaway, Rell, or this codebase.

---

## What Is Rell Toolbox?

**Rell Toolbox** is a collection of developer tools for the **Rell programming language**. It provides IDE integration, code analysis, formatting, and test data generation capabilities.

### What Problem Does It Solve?

Modern developers expect their IDEs to provide:
- Code completion and IntelliSense
- Go-to-definition and find-all-references
- Real-time error highlighting
- Automatic code formatting
- Type information on hover

Rell Toolbox delivers these features for Rell, allowing developers to work with Rell code in modern IDEs (VS Code, IntelliJ, etc.) with the same productivity they'd expect from mainstream languages like JavaScript or Python.

---

## What Is Rell?

**Rell** is a programming language developed by **Chromaway** for blockchain development, specifically for the **Chromia platform**.

### Key Characteristics
- Relational blockchain programming language
- Combines SQL-like syntax with general-purpose programming
- Designed for decentralized application (dApp) development

### Why This Context Matters
- This toolbox **does not** define the Rell language itself
- It **consumes** the Rell compiler as a dependency
- The grammar and language semantics are defined upstream in the Rell compiler
- This project's job is to **expose compiler functionality** in IDE-friendly ways

---

## Core Deliverables

### 1. Language Server (Primary)
A full **Language Server Protocol (LSP)** implementation that provides:
- Syntax highlighting via semantic tokens
- Code diagnostics (errors and warnings)
- Code completion
- Go-to-definition and find-references
- Hover information (type hints, documentation)
- Document formatting
- Inlay hints (inline type annotations)

**Location**: `language-server/` module
**Entry Points**:
- `com.chromaway.rell.tools.lsp.StdioMain` (production)
- `com.chromaway.rell.tools.lsp.SocketMain` (development/debugging)

### 2. Code Parser & AST
An **ANTLR4-based parser** that:
- Parses Rell source code into an Abstract Syntax Tree (AST)
- Provides **error recovery** (handles incomplete/broken code gracefully)
- Transforms ANTLR AST into Rell compiler's internal AST format

**Location**: `ast/` module
**Grammar File**: `ast/src/main/antlr/Rell.g4`

**Why Separate Parser?**: The Rell compiler's native parser is **not recoverable** (fails completely on syntax errors). IDEs need parsers that can handle incomplete code typed in real-time.

### 3. Code Formatter
Applies consistent formatting rules to Rell code:
- Configurable via `.rellformat` files
- Supports indentation, line width, spacing rules
- Integrates with EditorConfig

**Location**: `code-quality/` module

### 4. Workspace Indexer
Analyzes entire Rell projects to build symbol indexes:
- Global, module, and local symbol tables
- Cross-reference tracking (who calls what, who references what)
- Serialized to disk cache for fast startup

**Location**: `indexer/` module

### 5. Test Data Seeder
Generates realistic test data for Rell applications:
- Parses Rell schema definitions
- Generates data using fake data libraries
- Exports to JSON, YAML, SQL, CSV, or Rell insert statements

**Location**: `seeder/` module
**Status**: Partially complete (database insertion marked TODO)

---


## Target Users

### Primary: IDE Extension Authors
Engineers building IDE extensions/plugins for Rell (e.g., VS Code extension, IntelliJ plugin). They consume the language server JAR as a subprocess.

### Secondary: Rell Application Developers
Indirectly via IDE extensions. They never interact with this codebase directly but benefit from its features through their IDE.

### Tertiary: Rell Tooling Developers
Engineers maintaining or extending this toolbox itself.


---

## Repository Structure (High-Level)

```
rell-toolbox/
├── ast/                    # Parser (ANTLR4 grammar → Rell AST)
├── common/                 # Shared utilities
├── language-server/        # LSP server implementation ⭐ MAIN DELIVERABLE
├── indexer/               # Workspace symbol indexing
├── code-quality/          # Formatter and linting
├── seeder/                # Test data generation
├── docs/                  # Documentation (this file is here)
├── buildSrc/              # Custom Gradle plugins
├── build.gradle.kts       # Root build config
└── settings.gradle.kts    # Module configuration
```

---