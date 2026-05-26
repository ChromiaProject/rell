# Functional Overview

## High-Level Features

Rell Dokka Plugin generates HTML documentation for Rell code.
Two distinct sources of input &mdash; Rell project source trees and the in-process standard library &mdash; feed into the same model and the same renderer:

### 1. Project Documentation (`SourceBuild`)

- **Module compilation** through `RellApiBaseInternal.compileApp` with doc symbols enabled.
- **Definitions extracted** per `R_Module`: constants, entities, structs, objects, enums, functions, operations, queries.
- **Nested namespaces** inside a module produce sibling packages (`module.nested` shows up next to `module` in the site).
- **Extension functions** read from `R_App.functionExtensions.list`. Concrete `@extend(target)` implementations are documented under their target; orphans without a target are hidden.
- **Test modules** (`@test module`) are compiled (`includeTestSubModules(true)`) and emitted as additional packages alongside main modules.
- **Doc comments** flow from `DocSymbol` through `DocSymbolText.markdown(...)` and survive into the rendered HTML, including `@param` / `@return` / `@throws` / `@see` / `@since` / `@author` tags.
- **Default values** for constants and entity attributes are rendered using the runtime's `Gtv` formatter when the type is GTV-compatible, otherwise the `Rt_Value.toString()` form.
- **Mount names** show up only when distinct from the simple name (`@mount("name") operation foo` renders the annotation; `operation foo @mount("foo")` doesn't).

### 2. Standard Library Documentation (`SystemBuild`)

- **No source files.** The walker reads the in-process `Lib_Rell.MODULE` and `Lib_RellTest.MODULE` `L_Namespace` graphs.
- **Members handled:** types, structs, functions, properties, constants, aliases, and nested namespaces. `L_NamespaceMember_SpecialFunction` is included only for `exists` and `empty` (with a synthetic `(arg: T?) -> boolean` signature) &mdash; every other special function is skipped because it cannot be expressed as an ordinary `Doc_Function`.
- **Type members** (`L_TypeDefMember_*`): constructors, special constructors (`<type-name>(T)` form), static and instance methods, properties, constants, and aliases that resolve to a function on the same type.
- **Bundled package summaries:** `src/main/resources/rell.md` (`# Dapp` / `# Module` / `# Package` fragments) is folded in automatically when `--system` is set; the caller does not need to pass it via `--includes`.
- **Blacklists:** types `guid`, `signer` and the `tuid` alias are filtered out. Empty namespaces are dropped (they'd render an empty index page). Aliases from `Lib_RellTest` are skipped &mdash; only `Lib_Rell` contributes aliases.

### 3. HTML Site

- **Layout** &mdash; one `index.html`, one module index, one package index per package, one file per top-level definition, one file per class member. URLs use Dokka-compatible slugs so existing links stay valid.
- **Sidebar navigation** with collapsible packages, an inline root package, and an embedded search input wired to `scripts/pages.json`.
- **Search index** &mdash; `scripts/pages.json` with the same `{name, description, location, searchKeys[]}` schema as the previous Dokka output.
- **Markdown** rendered via CommonMark with GFM tables + autolink extensions. `[rell.qname]` shortcut-style references in doc comments are rewritten to relative `<a>` links by `Markdown.ResolveRefsVisitor`.
- **Signatures** mirror Rell source syntax (`function`, `operation`, `query`, `@extendable`, `@extend(target)`, `@mount`, `type Name<T> : super`, etc.) via `SignatureRender`.
- **Source links** &mdash; when `--source-link` is set, each `Doc_Source` whose path lives under the given local directory gets rewritten to the configured remote URL plus optional line suffix.
- **Custom CSS / assets** are copied into `styles/<sheet>.css` and `images/<asset>`, and the per-page `<head>` adds `<link rel="stylesheet">` entries for each user sheet.
- **Theme toggle** &mdash; light/dark switcher with localStorage persistence, applied at first paint by `THEME_BOOT_JS` (no FOUC).

### 4. Module Filtering

- `--filtered-modules` removes a module from the sidebar navigation. The module's pages and entries in the search index remain in place, so external links and cross-references keep working.
- `--additional-modules` re-includes a module that would otherwise be filtered (so you can hide most of a `lib.*` tree but selectively expose a few sub-modules).
- The set of hidden packages is `filteredModules - additionalModules`. System-mode forces this set to empty.

## Primary Flows

### Project Documentation

1. CLI parses argv (`cli/main.kt`) and builds a `RellDokkaPluginConfigurationBuilder` (non-system constructor).
2. `RellDokkaGenerator.generate()` resolves the builder to a `RellDokkaPluginConfiguration` POJO.
3. `ModuleDocs.load(includes, additionalTexts = [])` parses any markdown fragments the caller passed.
4. `SourceBuild.build(...)` runs the compiler over `projectRoot`, fans the resulting `R_Module`s out into `Doc_Package`s, and wraps everything in a `Doc_Module` titled with the site name.
5. The result becomes a `Doc_Site` with `system = false`, hidden packages = `filteredModules - additionalModules`, plus the user-supplied stylesheets/assets/source-links.
6. `SiteRender(targetFolder).render(site)` writes the HTML tree.

**Compilation failure:** `compileApp` throws (or `cRes.app` is null and the generator `requireNotNull`s) &mdash; there is no partial-render fallback. The thrown exception is surfaced to the caller (chromia-cli prints it through its own error path).

### System Library Documentation

1. Caller (CLI `--system`, or `RellDokkaPluginConfigurationBuilder.SYSTEM` programmatically) flips `system = true` and forces `title = "Rell System Library API Reference"`.
2. `ModuleDocs.load(includes, additionalTexts = [bundled rell.md])` is called &mdash; the bundled fragments populate package summaries automatically.
3. `SystemBuild.build(...)` walks `Lib_Rell.MODULE.lModule.namespace` then `Lib_RellTest.MODULE.lModule.namespace`, accumulating defs into a `parentQname → defs` map. Both namespaces share the same buckets, so contributions to the same package merge (e.g. `rell.test.keypairs` plus extensions to `rell`).
4. The single resulting `Doc_Module` is wrapped in a `Doc_Site` with `system = true` and rendered.

### Module Filtering

1. `--filtered-modules a,b` ends up in `RellDokkaPluginConfiguration.filteredModules`.
2. `--additional-modules b,c` ends up in `additionalModules`. (Modules in `additionalModules` are still added to `entryPointModules + additionalModules` for compilation, so they get documented.)
3. `RellDokkaGenerator.computeHiddenPackages(...)` returns `filteredModules - additionalModules` → ends up in `Doc_Site.hiddenPackages` (and is forced empty when `system = true`).
4. `Navigation.renderInto(...)` filters each module's packages by `pkg.qname !in site.hiddenPackages`. `SiteIndex` and `Search` still emit hidden packages (so cross-references keep resolving and direct URLs keep working).

### Doc Comment Processing

1. `RellApiBaseInternal.compileApp` is invoked with `docSymbolsEnabled(true)`, so every `R_Definition` keeps its `DocSymbol`.
2. `DocSymbolText.markdown(extraSuffix = …)` flattens description + tag map into a single markdown blob.
3. `Markdown.renderHtml(markdown, currentPage)` parses it with CommonMark (tables + autolink extensions), then `ResolveRefsVisitor` rewrites `[A.B.C]`-shaped references that resolve through `SiteIndex` into relative `<a>` links pointing at the right page.
4. The HTML is injected into the page body via `unsafe { +html }`.

### Source Link Generation

1. `--source-link <localDir>=<remoteUrl>[#lineSuffix]` is parsed in `cli/main.kt` and appended via `builder.addSourceLink(...)`.
2. `Doc_Source` (path + line) is attached to each definition during compose. For compose-system this is `null` (no source backing).
3. At render time, paths under the configured `localDirectory` get rewritten to `remoteUrl/<relative>#<lineSuffix><line>` for the "View source" link.

## Important Assumptions

### Rell Compilation

- **The project must compile.** `compileApp` is invoked with `mountConflictError(false)`, `moduleArgsMissingError(false)`, `appModuleInTestsError(false)` so a handful of soft errors are downgraded, but a hard compilation failure throws and aborts the run.
- **Tests are compiled by default** (`includeTestSubModules(true)`). Test modules are emitted into the same `Doc_Module` as main modules.
- **`InternalRellApi` opt-in.** `SourceBuild` uses `RellApiBaseInternal.compileApp` / `RellApiBaseInternal.makeCompilerOptions`, which are marked `@InternalRellApi`. The opt-in is file-scoped.

### System Library

- **Output reflects the linked `rell-base` version.** There is no source compilation involved &mdash; whatever `Lib_Rell.MODULE` exposes at runtime is what gets documented. Bumping the rell-base version changes the output without any code changes in the plugin.
- **Blacklists are hard-coded** in `SystemBuild` (`BLACKLISTED_TYPES`, `BLACKLISTED_ALIASES`).
- **Special functions are not generally supported.** Only `exists` / `empty` are emitted with a hand-rolled signature; the rest are dropped.
- **Bundled `rell.md`** is read from `src/main/resources/rell.md` at runtime. If you change it, rebuild the jar.

### Path Layout

- **`Paths.fileSlug` mangling is frozen.** Lowercase + digits stay; ASCII uppercase becomes `-` + lowercase; everything else passes through. `function#N` anonymous functions URL-escape `#` to `%23`.
- **All HTML hrefs are relative.** `Hrefs.relativeFrom(from, to)` computes `../`-prefixed paths so the site works under any base URL.

### Configuration Surface

- **`RellDokkaGenerator(builder).generate()` is the public binary contract.** Chromia-cli `GenerateDocsSiteCommand` and `rell-gradle-plugin`'s `RellDocsTask` call this directly. Changing the constructor signature requires coordinating with both consumers.
- **`RellDokkaPluginConfigurationBuilder` setters are part of the same contract.** Adding new options is safe (add a setter, default everything else); removing or renaming is not.

## Non-Obvious Behavior

### Hidden vs Filtered Packages

`Doc_Site.hiddenPackages` only affects `Navigation`. Hidden packages remain in `SiteIndex` (so `[ref]` resolution still works), in `Search` output (so the search bar surfaces them), and in the on-disk page tree (so direct URLs work). The intent is "remove from the menu", not "remove from the site".

### Root Package Inlining

When a module has a root package (qname `""`, displayed as `[root]`), it is inlined into the sidebar without a collapsible header &mdash; its defs read as the module's primary entry points. Other packages are collapsible groups.

### Single-Module Heading Suppression

For sites with exactly one `Doc_Module` (the common case &mdash; `--system`, or a single dapp), the nav drops the module-name heading. Multi-module sites keep it.

### Function Aliases

In the stdlib, an alias of a function emits a `Doc_Function` whose body is a copy of the target's signature with `aliasOfQname = "<target-qname>"` and an `"**Alias of** [target]"` suffix appended to the markdown. Type aliases become a `Doc_TypeAlias` (a distinct `Doc_Def` subtype).

### Extension Function Visibility

In project mode, a function with app-level name in `extensionFunctionAppLevelNames` is **hidden** in favor of its `@extendable` target &mdash; unless the function is itself `@extendable` (chained extensions). The actual extension functions are emitted in their own module's package (with `extendTargetQname` pointing at the target).

### Cross-Reference Resolution

`[name]` doc-comment references resolve in two passes: `SiteIndex.resolveAny(name, currentPackage)` first tries `currentPackage.qname + "." + name` (so unqualified names inside a package resolve to siblings), then falls back to a global lookup. Text inside code spans / fenced code blocks is left untouched.

### Anonymous Functions

Rell's `function#N` synthetic names survive into `Doc_Function.anonymous = true`. The `#` in their slug is URL-escaped to `%23` so the resulting filename works as an HTTP path.

### Per-Page TypeRender Instance

`SiteRender` constructs a fresh `TypeRender` (and `SignatureRender`) for each page because the renderer captures the current page's relative path to compute href targets. Reusing a single renderer across pages would mis-route the relative paths.

### Bundled Fonts

`SiteRender.copyBundledFonts()` copies `chromia/fonts/NBInternational/...` and `chromia/fonts/Battlefin-Black.otf` out of the jar at render time. Missing fonts are silently skipped &mdash; the CSS has a system-font fallback chain, so the site stays usable.

## Error Handling

### Compilation Errors

- `RellApiBaseInternal.compileApp` reports diagnostics through the `RellCliEnv` passed to the builder. Chromia-cli wires its own `BuildCliEnv` here to surface diagnostics in its standard format.
- A null `apiRes.cRes.app` triggers `requireNotNull(... ) { "Rell compilation failed" }` in `SourceBuild.compile`. The thrown exception propagates out of `RellDokkaGenerator.generate()`.

### Missing Modules

- Module names go through `ModuleName.of(it)` (which validates them). Unknown module names cause `compileApp` to fail with a missing-module error, which surfaces through the same diagnostic path.

### Invalid Filesystem Inputs

- `--source` requires the directory to exist (Clikt validation: `file(mustExist = true, canBeFile = false)`).
- Missing user stylesheets / assets are silently filtered (`filter { it.isRegularFile() }` in the generator) &mdash; they won't crash, but they won't be linked either.

### Renderer Robustness

- Render-time errors (I/O failures, malformed markdown) are unhandled: the exception propagates. There is no partial-output recovery.
- Markdown that fails to parse falls through to the CommonMark default (which is very permissive), so most invalid markdown still produces output, just not the output the author intended.

## Known Limitations

- **Stdlib special functions** beyond `exists` / `empty` are not documented. Adding more requires extending `SystemBuild.specialFunctionToDoc`.
- **Stdlib blacklists are hard-coded.** Filtering changes need source edits in `SystemBuild`.
- **No incremental rendering.** Every `generate()` rebuilds the entire site from scratch. For the stdlib this takes a fraction of a second; for large projects, the compile step dominates anyway.
- **Markdown supports CommonMark + GFM tables + autolink only.** Other GFM features (strikethrough, task lists, …) are not enabled.
- **`Doc_Source` is `null` for stdlib defs.** Source links don't apply to system-mode output.
