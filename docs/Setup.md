# Setup & Local Development

## Prerequisites

### Required Tools and Versions

**Java JDK 21 or later**
**Gradle (via Gradle Wrapper)**

### Optional Tools

**Docker** (Optional, for serving documentation)
- **Purpose:** Can be used to serve generated HTML documentation
- **Note:** Not required for development, only for viewing generated docs

**Node.js and http-server** (Optional, for serving documentation)
- **Purpose:** Alternative to Docker for serving generated HTML documentation
- **Note:** Not required for development, only for viewing generated docs

## Step-by-Step Setup Instructions

### 1. Clone the Repository

```bash
git clone https://gitlab.com/chromaway/core-tools/rell-dokka-plugin
cd rell-dokka-plugin
```

### 3. Build the Project

```bash
./gradlew build
```

**What this does:**
- Downloads dependencies (Dokka, Rell compiler API, etc.)
- Compiles Kotlin source code
- Runs tests
- Creates JAR artifacts in `build/libs/`
- Generates documentation for the plugin itself in `build/dokka/`

**Expected output:**
- Build should complete successfully
- Test results in `build/reports/tests/`
- JAR files in `build/libs/`
- Plugin documentation in `build/dokka/`

### 4. Run Tests

```bash
./gradlew test
```

**What this does:**
- Runs all unit and integration tests
- Generates test reports in `build/reports/tests/test/`
- Verifies plugin functionality

**Test coverage:**
- Tests verify documentation generation for various Rell code patterns
- Tests verify system library documentation generation
- Tests verify HTML rendering and navigation

## Running Locally

### Option 1: Generate Documentation via CLI (Recommended)

**Generate documentation for a Rell project:**

```bash
./gradlew run --args="--source /path/to/rell/project/src --target ./docs/output --modules module1,module2"
```

**Parameters:**
- `--source`: Source directory containing Rell files (default: `src`)
- `--target`: Output directory for generated documentation (default: `out`)
- `--modules`: Comma-separated list of entry point modules (optional)
- `--additional-modules`: Comma-separated list of additional modules to include (optional)
- `--name`: Project name for documentation (default: "My Rell Dapp")
- `--filtered-modules`: Comma-separated list of modules to hide from navigation (optional)
- `--includes`: Comma-separated list of documentation files to include (optional)
- `--source-link`: Source code link mapping in format `<path>=<url>[#lineSuffix]` (optional)
- `--system`: Generate system library documentation (hidden flag)

**Example with all options:**
```bash
./gradlew run --args="--source ./my-rell-project/src --target ./docs --modules mymodule --additional-modules lib.helper --name 'My Project' --filtered-modules lib.internal"
```

**What this does:**
- Compiles Rell source code using Rell compiler API
- Analyzes modules and extracts documentation
- Generates HTML documentation site in target directory
- Creates navigation, search, and custom styling

### Option 2: Generate System Library Documentation

**Generate system library documentation:**

```bash
./gradlew run --args="--system --target ./system-docs"
```

**What this does:**
- Generates documentation for Rell standard library
- Includes all system modules (rell, rell.test, etc.)
- Outputs "Rell System Library API Reference"

**Note:** System library documentation uses predefined module definitions, not source code compilation.

### Option 3: Build and Use as Library

**Build JAR artifact:**

```bash
./gradlew build
```

**JAR location:**
- `build/libs/rell-dokka-plugin-dev.jar` - Main JAR
- `build/libs/rell-dokka-plugin-dev-sources.jar` - Source JAR
- `build/libs/rell-dokka-plugin-dev-javadoc.jar` - Javadoc JAR

**Use in another project:**
- Add JAR to classpath or publish to Maven repository
- Configure as Dokka plugin in build system

### Option 4: Publish to Maven Local (For Testing)

**Publish to local Maven repository:**

```bash
./gradlew publishToMavenLocal
```

**What this does:**
- Publishes plugin to `~/.m2/repository/`
- Can be used in other projects via `mavenLocal()` repository (mainly `chromia-cli`)
- Useful for testing plugin changes before publishing

## Testing

### Running Tests

**Run all tests:**
```bash
./gradlew test
```

**Run specific test class:**
```bash
./gradlew test --tests "com.chromia.rell.dokka.RellDokkaPluginTest"
```

### Test Structure

**Test locations:**
- `src/test/kotlin/com/chromia/rell/dokka/` - Test source files
- `src/test/resources/my-rell-dapp/` - Test Rell project files

**Test types:**
- Unit tests for individual components
- Integration tests for documentation generation
- HTML rendering tests
- System library documentation tests

**Test utilities:**
- `BaseAbstractTest` - Base class for Dokka plugin tests
- Allows writing Kotlin/Java code in tests without external files
- Provides utilities for testing documentation generation

**Test resources:**
- Place test Rell files in `src/test/resources/`
- Reference in tests using relative paths

## Development Workflow

### Making Code Changes

1. **Edit code** in `src/main/kotlin/`
2. **Rebuild:** `./gradlew build`
3. **Run tests:** `./gradlew test`
4. **Test changes:** Generate documentation for a test project
5. **Verify output:** Check generated HTML documentation

### Adding New Features

**To add a new pipeline extension:**

1. **Create implementation class** in appropriate package
2. **Register extension** in `RellDokkaPlugin.kt` using `extending` mechanism
3. **Add tests** for new functionality

**Example - Adding a new transformer:**

```kotlin
// In RellDokkaPlugin.kt
val myTransformer by extending {
    CoreExtensions.documentableTransformer providing ::MyTransformer
}
```

### Common Development Tasks

**Regenerate plugin documentation:**
```bash
./gradlew dokkaHtml
```

Generates documentation for the plugin itself in `build/dokka/`

## Viewing Generated Documentation

### Option 1: Using Docker

**From the output directory:**
```bash
cd docs/output  # or your target directory
docker run -dit --name my-apache-app -p 8080:80 -v "$PWD":/usr/local/apache2/htdocs/ httpd:2.4
```

**Access documentation:**
- Open browser to `http://localhost:8080`
- View generated HTML documentation

**Stop server:**
```bash
docker stop my-apache-app
docker rm my-apache-app
```

## Common Setup Pitfalls and Fixes

### Rell Compilation Errors

**Problem:** Documentation generation fails with Rell compilation errors.

**Solution:**
- Ensure Rell source code compiles successfully
- Check Rell compiler API version matches Rell language version
- Verify source directory structure is correct
- Check module names match actual module names in source

### Missing Modules

**Problem:** Documentation generation fails with "module not found" errors.

**Solution:**
- Verify module names in `--modules` parameter match actual module names
- Check source directory contains specified modules
- Ensure module structure is valid
