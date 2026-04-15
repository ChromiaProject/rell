# Setup & Local Development

For general prerequisites and building, see the root [DEVELOPMENT.md](../../DEVELOPMENT.md).

## Generating Documentation

### User Project Documentation

```bash
./gradlew :rell-dokka-plugin:run --args="--source /path/to/rell/src --target ./docs/output --modules module1,module2"
```

| Flag                   | Description                                            | Default          |
|------------------------|--------------------------------------------------------|------------------|
| `--source`             | Source directory containing Rell files                 | `src`            |
| `--target`             | Output directory for generated HTML                    | `out`            |
| `--modules`            | Comma-separated entry point modules                    | *(all)*          |
| `--additional-modules` | Extra modules to include                               | *(none)*         |
| `--name`               | Project name shown in docs                             | `My Rell Dapp`   |
| `--filtered-modules`   | Modules to hide from navigation                        | *(none)*         |
| `--includes`           | Extra documentation files to include                   | *(none)*         |
| `--source-link`        | Source link mapping: `<path>=<url>[#lineSuffix]`       | *(none)*         |

### System Library Documentation

```bash
./gradlew :rell-dokka-plugin:run --args="--system --target ./system-docs"
```

Uses predefined module definitions (no source compilation). This is also available via `work/build-local-docs.sh`.

## Extending the Plugin

Register new pipeline extensions in `RellDokkaPlugin.kt`:

```kotlin
val myTransformer by extending {
    CoreExtensions.documentableTransformer providing ::MyTransformer
}
```
