# Setup and Build Guide

For general prerequisites, building, testing, and release instructions, see the root [DEVELOPMENT.md](../../DEVELOPMENT.md).

This document covers only rell-toolbox-specific details.

---

## Modules

| Module             | Gradle path                    |
|--------------------|--------------------------------|
| `ast`              | `:rell-toolbox:ast`            |
| `common`           | `:rell-toolbox:common`         |
| `indexer`          | `:rell-toolbox:indexer`        |
| `code-quality`     | `:rell-toolbox:code-quality`   |
| `language-server`  | `:rell-toolbox:language-server`|
| `seeder`           | `:rell-toolbox:seeder`         |

---

## Running the Language Server Locally

### Stdio Mode

```bash
./gradlew :rell-toolbox:language-server:shadowJar
java -jar rell-toolbox/language-server/build/libs/language-server-dev-all.jar
```

Reads JSON-RPC from stdin, writes to stdout. Use this to test integration with IDE extensions.

### Socket Mode (from IDE)

Run `com.chromaway.rell.tools.lsp.SocketMain` directly in IntelliJ for hot reload and debugging.

---

## Code Quality

Detekt static analysis runs as part of `check`. To run it standalone:

```bash
./gradlew :rell-toolbox:language-server:detekt
```

Report: `rell-toolbox/language-server/build/reports/detekt/detekt.html`
