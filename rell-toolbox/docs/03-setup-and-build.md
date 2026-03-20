# Setup and Build Guide

## Document Purpose

This document provides step-by-step instructions for setting up a development environment, building the project, running tests, and creating releases.

Assumes **zero prior knowledge** of this codebase.

---

## Prerequisites

### Required Software

| Tool | Version | Purpose | Installation |
|------|---------|---------|--------------|
| **JDK** | 21 | Java compilation and runtime | [Download OpenJDK 21](https://adoptium.net/) |
| **Git** | Any recent | Version control | [Download Git](https://git-scm.com/) |

### Optional Software

| Tool | Purpose |
|------|---------|
| **IntelliJ IDEA** | Kotlin IDE (recommended for development) |
| **Gradle** | Build tool (wrapper included, manual install optional) |

---

## Initial Setup

### 1. Clone Repository

```bash
git clone <repository-url>
cd rell-toolbox
```

**Note**: Repository URL not publicly documented (likely internal GitLab).

### 2. Verify JDK Version

```bash
java -version
```

**Expected Output**:
```
openjdk version "21.0.x" ...
```

If wrong version:
- **macOS/Linux**: Use `JAVA_HOME` environment variable or `jenv`
- **Windows**: Set `JAVA_HOME` system environment variable

### 3. Verify Gradle Wrapper

```bash
./gradlew --version
```

**Expected Output**:
```
Gradle 8.7
Kotlin: ...
JVM: 21.0.x
```

The Gradle wrapper (`./gradlew`) is **included in the repository**. You do not need to install Gradle manually.

---

## Building the Project

### Full Build (All Modules)

```bash
./gradlew build
```

**What This Does**:
1. Downloads dependencies (first run only)
2. Compiles Kotlin code for all 6 modules
3. Generates ANTLR4 parser from `ast/src/main/antlr/Rell.g4`
4. Runs unit tests
5. Packages JARs for each module
6. Runs code quality checks (Detekt static analysis)

**Expected Output**:
```
BUILD SUCCESSFUL in Xs
```

**First Build**: May take 2-5 minutes (downloads dependencies).
**Subsequent Builds**: 30-60 seconds (incremental compilation).

### Build Single Module

```bash
./gradlew :language-server:build
```

**Module Names**:
- `:ast`
- `:common`
- `:indexer`
- `:code-quality`
- `:language-server`
- `:seeder`

### Clean Build (Remove All Build Artifacts)

```bash
./gradlew clean build
```

**When to Use**: If you suspect stale build artifacts or cache corruption.

### Build Without Tests

```bash
./gradlew build -x test
```

**Use Case**: Faster iteration during development.

---

## Running Tests

### Run All Tests

```bash
./gradlew test
```

**What This Does**:
- Executes all JUnit5 tests across all modules
- Generates test reports in `<module>/build/reports/tests/test/index.html`
- Generates coverage reports (JaCoCo) in `<module>/build/reports/jacoco/test/html/index.html`

### Run Tests for Single Module

```bash
./gradlew :language-server:test
```

### Run Specific Test Class

```bash
./gradlew :language-server:test --tests "com.chromaway.rell.tools.lsp.RellLanguageServerTest"
```

### Run Specific Test Method

```bash
./gradlew :language-server:test --tests "com.chromaway.rell.tools.lsp.RellLanguageServerTest.testInitialize"
```

### Continuous Testing (Watch Mode)

```bash
./gradlew test --continuous
```

**Behavior**: Re-runs tests automatically when source files change.

### View Test Results

After running tests:

```bash
open language-server/build/reports/tests/test/index.html
```

(Use `xdg-open` on Linux, `start` on Windows)

---

## Running the Language Server Locally

### Stdio Mode (Production-Like)

```bash
./gradlew :language-server:shadowJar
java -jar language-server/build/libs/language-server-dev-all.jar
```

**Notes**:
- Reads JSON-RPC from stdin, writes to stdout
- Use this to test integration with IDE extensions
- To exit: Ctrl+C

**Shadow JAR**: A "fat JAR" containing all dependencies. The `shadowJar` task is provided by the Shadow plugin.

### Running From IDE (IntelliJ IDEA)

1. Open project in IntelliJ
2. Gradle sync will auto-detect modules
3. Navigate to `com.chromaway.rell.tools.lsp.SocketMain`
4. Right-click → Run 'SocketMain'
5. Set breakpoints as needed
6. Use IntelliJ's debugger

**Advantages**:
- Hot reload (faster iteration)
- Visual debugging
- Better log inspection

---

## Code Quality Checks

### Run Detekt (Static Analysis)

```bash
./gradlew detekt
```

**What It Checks**:
- Code smells
- Complexity metrics
- Style violations
- Potential bugs

**Reports**: `build/reports/detekt/detekt.html`

### Run All Quality Checks

```bash
./gradlew check
```

**Includes**:
- Tests
- Detekt
- Dependency verification

---

## Dependency Management

### View Dependency Tree

```bash
./gradlew :language-server:dependencies
```

**Use Case**: Debug version conflicts or understand transitive dependencies.

### Update Dependencies

Dependencies are managed in `gradle/libs.versions.toml`.

**To Update**:
1. Edit `gradle/libs.versions.toml`
2. Change version number
3. Run `./gradlew build`

**Example**:
```toml
[versions]
koin = "3.5.0"  # Change to "3.6.0"
```

### Add New Dependency

1. Add to `gradle/libs.versions.toml`:
```toml
[libraries]
new-library = { module = "com.example:library", version = "1.0.0" }
```

2. Reference in module's `build.gradle.kts`:
```kotlin
dependencies {
    implementation(libs.new.library)
}
```

---


## Release Process

**Context**: Releases are managed via GitLab CI/CD pipeline.

### Prerequisites

- Access to GitLab repository
- Write permissions to main branch (`dev`)
- Maven registry credentials configured

### Release Steps

1. **Ensure All Tests Pass**:
   ```bash
   ./gradlew test
   ```

2. **Merge Changes to `dev` Branch**:

3. **Trigger Pipeline**:
   - Navigate to [GitLab Pipelines](https://gitlab.com/chromaway/core-tools/rell-toolbox/-/pipelines)
   - Wait for pipeline to complete (build, test stages)

4. **Manually Trigger Release Stage**:
   - Click on pipeline
   - Find "release" stage
   - Choose:
     - `release-patch` (0.15.0 → 0.15.1)
     - `release-minor` (0.15.0 → 0.16.0)

5. **Verify Release**:
   - Check GitLab Maven registry for new version
   - Verify git tag created

### Version Numbering

**Current Version**: 0.15.0 (as of this documentation)

**Versioning Scheme**: Semantic Versioning (SemVer)
- **Patch** (0.15.X): Bug fixes, no breaking changes
- **Minor** (0.X.0): New features, backward-compatible
- **Major** (X.0.0): Breaking changes (no documented process)


## CI/CD Pipeline

### Pipeline Configuration

**File**: `.gitlab-ci.yml`

**Stages**:
1. `build` - Compile and package
2. `test` - Run all tests
3. `release` - Publish to Maven registry (manual trigger)

**Runner**: GitLab Docker executor
**Image**: `gradle:8.7-jdk21-alpine`

### Environment Variables

Pipeline uses these variables (configured in GitLab):
- `CI_JOB_TOKEN` - GitLab authentication
- `CI_REGISTRY` - Maven registry URL
- `SENTRY_DSN` - Error reporting endpoint (optional)

### Viewing Pipeline Results

1. Navigate to [Pipelines](https://gitlab.com/chromaway/core-tools/rell-toolbox/-/pipelines)
2. Click on pipeline ID
3. View job logs for failures

---
