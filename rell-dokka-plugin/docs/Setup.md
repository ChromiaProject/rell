# Setup & Local Development

For general prerequisites and building, see the root [DEVELOPMENT.md](../../DEVELOPMENT.md).

## Generating Documentation

The application entry point is `com.chromia.rell.dokka.cli.MainKt` (declared in `build.gradle.kts`). Invoke it through Gradle:

### Project Documentation

```bash
./gradlew :rell-dokka-plugin:run --args="--source /path/to/rell/src --target ./docs/output --modules module1,module2"
```

| Flag                   | Description                                                | Default        |
|------------------------|------------------------------------------------------------|----------------|
| `--source`             | Source directory containing Rell files (must exist)        | `src`          |
| `--target`             | Output directory for generated HTML                        | `out`          |
| `--modules`            | Comma-separated entry-point modules                        | *(all)*        |
| `--additional-modules` | Extra modules to include / un-hide                         | *(none)*       |
| `--name`               | Project name shown in the site title                       | `My Rell Dapp` |
| `--styles`             | Custom stylesheets (comma-separated; copied to `styles/`)  | *(none)*       |
| `--assets`             | Custom assets (comma-separated; copied to `images/`)       | *(none)*       |
| `--filtered-modules`   | Modules to hide from navigation                            | *(none)*       |
| `--includes`           | Markdown files with `# Dapp/Module/Package` fragments      | *(none)*       |
| `--source-link`        | Source link mapping: `<localDir>=<remoteUrl>[#lineSuffix]` | *(none)*       |
| `--system`             | Generate stdlib docs (hidden flag, see below)              | off            |

The footer is hardwired to `© <current-year> Chromia`.

### System Library Documentation

```bash
./gradlew :rell-dokka-plugin:run --args="--system --target ./system-docs"
```

`--system` walks the in-process `Lib_Rell.MODULE` + `Lib_RellTest.MODULE` namespaces &mdash; no source files are read. The bundled `src/main/resources/rell.md` is folded in automatically for package summaries, so you don't need to pass it via `--includes`.

`work/build-local-docs.sh` is the local-preview wrapper for this mode.

## Programmatic Use

The same generator runs in-process from chromia-cli's `GenerateDocsSiteCommand` and from `rell-gradle-plugin`'s `RellDocsTask`. The public API surface is:

```kotlin
val builder = RellDokkaPluginConfigurationBuilder(
    title = "My Dapp",
    modules = listOf("main"),
    projectRoot = File("src"),
)
    .targetFolder(File("out"))
    .includes(listOf(File("docs/overview.md")))
    .filteredModules(listOf("lib.internal"))
    .additionalModules(emptyList())
    .footerMessage("© 2026 Acme")
    .addSourceLink("src", URI("https://example.com/blob/main/src"), "#L")
    .cliEnv(myRellCliEnv)              // optional - wires diagnostics into your IO surface

RellDokkaGenerator(builder).generate()

// System-lib variant:
val systemBuilder = RellDokkaPluginConfigurationBuilder.SYSTEM
    .targetFolder(File("system-docs"))
RellDokkaGenerator(systemBuilder).generate()
```

`RellDokkaGenerator` and the public methods on `RellDokkaPluginConfigurationBuilder` are pinned by this contract &mdash; changing them requires coordinating a chromia-cli release.

## Extending the Renderer

The implementation is no longer a Dokka plugin. The historical "extension points" model is gone; extension is done by editing source. Concretely:

- **New definition type** &mdash; add a sealed subtype to `Doc_Def` (`model/DocDef.kt`), project it from compose (`SourceBuild` and/or `SystemBuild`), and render it in `SignatureRender` + `Pages.defKicker`.
- **New `Doc_Type` shape** &mdash; extend the `Doc_Type` sealed hierarchy and add the corresponding branch in `TypeRender`. Also update `TypeConv.kt` if the new shape comes from `M_Type`.
- **New page kind** &mdash; add a body builder method to `Pages.kt` and a `writeXxx(...)` step in `SiteRender`.
- **Styling / theme** &mdash; edit `Styles.SITE_CSS` (the constant in `render/Styles.kt`). `ThemeAssets` holds the theme-boot JS, toggle SVG, and search JS.
- **Search index schema** &mdash; `render/Search.kt` is the single source of truth for `scripts/pages.json`. The schema is shared with downstream consumers, so coordinate before changing it.
