# Testing & Development Guide

Guide for developers who want to contribute to rell-codegen or understand its testing strategy.

## Prerequisites

### Required Software
- **JDK 21** or higher
- **Gradle 8.7** (via wrapper)
- **Docker** (for integration tests)
- **Git**

### Recommended Tools
- **IntelliJ IDEA** (Kotlin IDE)
- **Docker Desktop** (easier than CLI Docker)
- **GitLab account** (for merge requests)

## Setting Up Development Environment

### 1. Clone Repository

```bash
git clone git@gitlab.com:chromaway/core-tools/rell-codegen.git
cd rell-codegen
```

### 2. Open in IntelliJ IDEA

```
File → Open → Select rell-codegen directory
```

IDEA will automatically:
- Detect Gradle project
- Download dependencies
- Configure Kotlin plugin

### 3. Verify Build

```bash
./gradlew build
```

This runs:
- Compilation of all modules
- Unit tests
- Integration tests (requires Docker)

**First build:** May take 5-10 minutes to download dependencies.

### 4. Verify Docker

Integration tests require Docker:

```bash
docker --version
docker ps  # Should not error
```

**If Docker not running:**
```bash
# macOS/Linux
sudo systemctl start docker

# macOS with Docker Desktop
open -a Docker
```

## Project Structure

```
rell-codegen/
├── codegen/              # Core logic (abstract interfaces)
├── codegen-kotlin/       # Kotlin implementation
├── codegen-typescript/   # TypeScript implementation
├── codegen-javascript/   # JavaScript implementation
├── codegen-python/       # Python implementation
├── codegen-mermaid/      # Mermaid diagram generation
├── rellgen/              # CLI application
├── buildSrc/             # Gradle build plugins
├── testResources/        # Shared test Rell files
├── gradle/               # Gradle wrapper
├── build.gradle.kts      # Root build config
├── settings.gradle.kts   # Multi-module config
└── gradle.properties     # Version and settings
```

## Running Tests

### Run All Tests

```bash
./gradlew test
```

### Run Tests for Specific Module

```bash
./gradlew :codegen:test
./gradlew :codegen-kotlin:test
./gradlew :codegen-typescript:test
```

### Run Single Test Class

```bash
./gradlew :codegen-kotlin:test --tests "KotlinEntityTest"
```

### Run Specific Test Method

```bash
./gradlew :codegen-kotlin:test --tests "KotlinEntityTest.should generate correct data class"
```

### Skip Tests

```bash
./gradlew build -x test
```

## Writing New Tests

### Test Naming Convention

```kotlin
@Test
fun `descriptive test name in backticks`() { ... }
```

**Good test names:**
- `entity with list field generates List type`
- `query with multiple parameters serializes correctly`
- `struct with nested struct resolves dependencies`

**Bad test names:**
- `test1()`
- `testEntity()`
- `it_should_work()`

### Assertions

Use AssertK for fluent assertions:

```kotlin
// Simple equality
assertThat(result).isEqualTo("expected")

// String contains
assertThat(generated).contains("data class User")

// Collection assertions
assertThat(list).hasSize(3)
assertThat(list).contains("item1", "item2")

// Null checks
assertThat(value).isNotNull()
assertThat(optional).isNull()
```

## Building and Packaging

### Build All Modules

```bash
./gradlew build
```

### Build Specific Module

```bash
./gradlew :codegen-kotlin:build
```

### Clean Build

```bash
./gradlew clean build
```

