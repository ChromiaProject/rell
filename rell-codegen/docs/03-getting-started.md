# Getting Started

For prerequisites and general build instructions, see the root [DEVELOPMENT.md](../../DEVELOPMENT.md).

## Build and Install CLI

```bash
./gradlew :rell-codegen:rellgen:installDist
```

The CLI tool will be available at:
```
rell-codegen/rellgen/build/install/rellgen/bin/rellgen
```

Verify it works:

```bash
./rell-codegen/rellgen/build/install/rellgen/bin/rellgen --help
```
