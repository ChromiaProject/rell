# Rell Toolbox Development Guide

## Build & Test Commands
- Build: `./gradlew build`
- Run all tests: `./gradlew test`
- Run single test: `./gradlew :module:test --tests "fully.qualified.TestClassName.testMethod"`
- Example: `./gradlew :indexer:test --tests "net.postchain.rell.toolbox.indexer.WorkspaceIndexerTest.initialFileIndexBuild_builds_index_mapper_of_files_in_workspace"`

## Code Style Guidelines
- Use Kotlin DSL (build.gradle.kts) for Gradle configuration
- Max line length: 120 characters
- Indentation: 4 spaces (no tabs)
- Classes: PascalCase (`[A-Z][a-zA-Z0-9]*`)
- Functions/Variables: camelCase (`[a-z][a-zA-Z0-9]*`)
- Constants: UPPER_SNAKE_CASE (`[A-Z][_A-Z0-9]*`)
- Enums: UPPER_SNAKE_CASE (`[A-Z][_a-zA-Z0-9]*`)
- Max method length: 60 lines
- Max nested block depth: 4
- Error handling: Include messages in exceptions, prefer Check/Error/Require over direct throwing
- Import order: standard imports, then java.**, javax.**, kotlin.**, other imports
- Wildcard imports allowed only for java.util.*

## Project Structure
- Current branch: dev
- Main modules: ast, common, code-quality (formatter/linter), indexer, language-server, seeder