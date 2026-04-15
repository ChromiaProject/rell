# Testing & Development Guide

For prerequisites, general build/test instructions, and IDE setup, see the root [DEVELOPMENT.md](../../DEVELOPMENT.md).

Integration tests require Docker (TestContainers).

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
└── testResources/        # Shared test Rell files
```

## Running Tests

```bash
./gradlew :rell-codegen:test                                          # all modules
./gradlew :rell-codegen:codegen-kotlin:test                           # single module
./gradlew :rell-codegen:codegen-kotlin:test --tests "KotlinEntityTest" # single class
```

## Writing New Tests

Use AssertK for assertions:

```kotlin
@Test
fun `entity with list field generates List type`() {
    assertThat(result).isEqualTo("expected")
    assertThat(generated).contains("data class User")
}
```

If you add Rell code to `testResources`, make sure it is tested by all relevant modules in the implementation layer.
