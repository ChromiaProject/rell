# Project Overview

## What is rell-codegen?

**rell-codegen** is a code generation framework that automatically creates language-specific client libraries from Rell smart contract definitions.

## Purpose

When building blockchain applications on Chromia using Rell (a smart contract language), developers need client code to interact with the blockchain from various programming languages. Manually writing and maintaining these client libraries is:
- Time-consuming and error-prone
- Difficult to keep in sync with contract changes
- Repetitive across multiple languages

**rell-codegen solves this by:**
- Parsing Rell source code to extract definitions
- Automatically generating type-safe client stubs in multiple languages
- Ensuring consistency between contracts and client code
- Reducing manual boilerplate

## What Gets Generated

Given Rell smart contract code containing:
- **Entities** (blockchain state objects)
- **Structs** (custom data structures)
- **Enums** (enumeration types)
- **Queries** (read-only operations)
- **Operations** (state-modifying transactions)

rell-codegen produces:
- **Kotlin** client code with data classes and extension methods for GTX transaction builder
- **TypeScript** client code with interfaces and async methods for PostchainClient
- **JavaScript** client code (Node.js compatible)
- **Python** client code with dataclasses and functions
- **Mermaid diagrams** for visual documentation (entity-relationship or class diagrams)

## Key Benefits

1. **Type Safety**: Generated code is strongly typed, catching errors at compile time
2. **Consistency**: Clients always match the contract definition
3. **Multi-Language Support**: One definition, multiple client libraries
4. **Reduced Maintenance**: Changes to Rell code automatically propagate to clients
5. **Integration Ready**: Generated code integrates with Postchain SDKs (GTX, PostchainClient)

## Who Should Use This

- **Blockchain developers** building Chromia dapps with Rell contracts
- **Frontend/backend developers** needing to interact with Rell applications
- **Teams** maintaining multiple client languages for the same blockchain app
- **Projects** where contract-client synchronization is critical

## What This Documentation Covers

This documentation assumes you have general software engineering knowledge but **no prior experience with**:
- Chromia blockchain platform
- Rell programming language
- Postchain architecture
- GTV (Rell's serialization format)

We explain each concept from first principles, covering:
- How the code generation works internally
- How to use the CLI tool
- How to integrate generated code into your applications
- Type mappings between Rell and target languages
- Testing and contribution guidelines
- Known limitations and gaps

## Repository Information

- **Git Remote**: `git@gitlab.com:chromaway/core-tools/rell-codegen.git`
- **License**: Apache 2.0
- **Primary Language**: Kotlin
- **Build System**: Gradle 8.7
- **Current Version**: dev (with Rell 0.15.0 support)

