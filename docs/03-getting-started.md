# Getting Started

## Prerequisites

### Required Software
- **Java Development Kit (JDK) 21** or higher
- **Gradle 8.7** (included via Gradle wrapper)
- **Git** (for cloning the repository)

### Optional (for testing)
- **Docker** (required for integration tests using TestContainers)
- **Docker Compose** (for orchestrating test environments)

### Knowledge Prerequisites
- Basic command-line usage
- Understanding of your target programming language (Kotlin/TypeScript/Python/etc.)
- Familiarity with blockchain concepts (helpful but not required)

## Installation

### Option 1: Clone and Build from Source

```bash
# Clone the repository
git clone git@gitlab.com:chromaway/core-tools/rell-codegen.git
cd rell-codegen

# Build the project
./gradlew build

# This will:
# - Download dependencies
# - Compile all modules
# - Run unit tests
# - Create JAR artifacts
```

**Output**: The CLI tool will be available as a ZIP at:
```
rellgen/build/distributions/rellgen-dev.zip
```

## Verify Installation

Extract the ZIP file:

```bash
unzip rellgen/build/distributions/rellgen-dev.zip -d rellgen/build/distributions/
```

Run the help command to verify the CLI works:

```bash
./rellgen/build/distributions/rellgen-dev/bin/rellgen --help
```
