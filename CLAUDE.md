# CLAUDE.md

Rell — Kotlin/Gradle multi-module implementation of the Rell blockchain language for Chromia. SQL-like at-expressions over PostgreSQL entities, blockchain ops/queries. **Determinism is non-negotiable** (consensus code).

**Read DEVELOPMENT.md for complex tasks** — coding conventions, testing, `local.properties`, ABI, release process. Its project-structure section may lag the build; `settings.gradle.kts` is authoritative for the module list.

## Quick Reference

### Prerequisites

- JDK 21 (Gradle toolchain + `jvmTarget`; CI runs on `graalvm/jdk:21`).
- PostgreSQL for tests: `./work/psql/psql-docker.sh` (runs a `postgres:16` container and applies `work/psql/init.sql`), or a local instance initialised with `work/psql/init.sql` — it creates the `postchain`/`postchain` user, the `postchain` DB (`C.UTF-8` collation) and the `wrong_collation` DB.
- Docker-compatible daemon for Testcontainers-based tests (codegen IT, `regression`).
- Maven repositories are GitLab package registries — full builds need `-PgitlabAuthHeaderValue=<token>` (header name comes from `gradle.properties`).

### Commands

```bash
./gradlew assemble                # build
./gradlew check                   # tests (needs PostgreSQL)
./gradlew apiDump / apiCheck      # ABI dumps (binary-compatibility-validator)
./work/rell.sh                    # REPL (builds :rell-tools:installRellDist first)
./work/rell.sh -d <dir> <module> <function>   # run a program
./gradlew :performance:buildLocalChr          # build a local `chr` CLI
./work/build-local-docs.sh        # docs preview → libdoc/
```

Other `work/` wrappers over the installed dist: `rellcfg.sh`, `multigen.sh`, `multirun.sh`, `psql/psql-shell.sh`. IntelliJ run configs: `work/All_tests.run.xml`, `work/Kotlin_ABI_Dump.run.xml`.

### Coverage check for new code

After implementing a feature, verify the new code is actually exercised — per class and per changed line, not module-level percentages:

```bash
./gradlew testCodeCoverageReport   # → coverage-report-aggregate/build/reports/jacoco/testCodeCoverageReport/ (XML + HTML)
```

Parse the XML for the classes/sourcefiles you touched (`class`/`sourcefile` elements carry LINE/BRANCH/METHOD counters and per-line `mi`/`ci`). Caveats that make raw numbers lie:

- The aggregate only includes each module's `test` task. Code exercised solely by `testTruffle`, `testRoundTrip`, or `:rell-toolbox:ast:grammarTest` (e.g. runtime-truffle paths, the better-parse `S_Grammar` — production parsing goes through ANTLR + `RellAntlrVisitor`) shows as uncovered even when those suites pass.
- If the Docker daemon is unavailable, exclude the Testcontainers suites: `-x :rell-codegen:codegen-typescript:test -x :rell-codegen:codegen-javascript:test -x :rell-codegen:codegen-python:test`.
- A 0%-covered new class is either a missing test or dead code — decide which and fix that (delete unreachable code rather than writing a test to reach it).

## Project Structure

### rell-base sub-modules
| Sub-module | Purpose |
|---|---|
| `utils` | Shared utilities, no Rell deps |
| `rr-tree` | Serializable RR_ data classes (types, defs, IR, frames) |
| `rr-serialization` | FlatBuffers ser/deser for RR_ tree |
| `frontend` | Compiler (AST, compilation, R_ model, types, lib framework) + R_→RR_ resolver |
| `runtime-core` | Rt_ values/types, stdlib declarations + impls, SQL adapters |
| `runtime-interpreter` | Rt_ interpreter over the RR_ tree, SQL gen |
| `runtime-truffle` | Truffle/GraalVM execution backend |
| `test-utils` | Shared test fixtures |

Tests live inside each sub-module (there is no `rell-base:tests`).

### Other modules
- **rell-api-{base,gtx,shell,native}** — API layers (ABI-checked: `.api` dumps via binary-compatibility-validator)
- **rell-gtx** — GTX integration
- **rell-tools** — Developer tools; `installRellDist` produces the runnable dist
- **rell-toolbox/** — LSP server: `common`, `ast` (ANTLR; grammar tests via `:rell-toolbox:ast:grammarTest`), `indexer`, `code-quality`, `language-server` (shadow JAR), `seeder`
- **rell-codegen/** — Client stub gen: `codegen` core + `codegen-{kotlin,typescript,javascript,python,mermaid}` + `rellgen` CLI
- **rell-dokka-plugin** — Dokka plugin for stdlib docs (also ABI-checked)
- **performance/** — benchmarks; hosts `buildLocalChr` (populates/patches `chromia-cli-local/` and `chromia-cli-tools-local/` checkouts)
- **regression/** — per-project regression compile runs against Testcontainers PostgreSQL
- **coverage-report-aggregate** — aggregate JaCoCo report (`./gradlew testCodeCoverageReport`)
- **doc/** — Guide (`doc/guide/`), architecture notes (`doc/rell-architecture.md`), release notes (`doc/release-notes/`, dev changes in `dev.txt`), release guides
- **work/** — Scripts and manual test projects

CI is GitLab (`.gitlab-ci.yml` + `.gitlab/ci/`); the canonical repo is GitLab, GitHub is a read-only mirror.

## Architecture

### Compilation Pipeline (`C_CompilerPass` in `frontend/src/main/kotlin/compiler/base/core/c_compiler.kt`)
DEFINITIONS → NAMESPACES → MODULES → MEMBERS → ABSTRACT → APPDEFS → EXPRESSIONS → FRAMES → DOCS → COMPLETIONS → VALIDATION → APPLICATION → FINISH

### Layer Prefixes

| Prefix | Layer | Location | Notes |
|---|---|---|---|
| `S_` | AST | `frontend/src/main/kotlin/compiler/ast/` | Parsed source |
| `C_` | Compilation | `frontend/src/main/kotlin/compiler/base/` | AST → runtime transform |
| `R_` | Compiler model | `frontend/src/main/kotlin/model/` | Mutable, lazy, compiler-coupled — NOT serializable |
| (none) | Shared value types | `utils/src/model/` | Pure data shared by compiler and runtime (`Name`, `KeyIndex`/`Key`/`Index`, `KeyIndexKind`, `DefinitionName`, `AtCardinality`, etc.) — no prefix, no Rell deps |
| `RR_` | Resolved runtime | `rr-tree/src/main/` + resolver in `frontend/src/main/kotlin/model/rr/` | Immutable, serializable — **only model the runtime consumes** |
| `Rt_` | Runtime exec | `runtime-core/src/main/kotlin/runtime/` (values, types) + `runtime-interpreter/src/runtime/` (interpreter) | |
| `M_` | Type system | `frontend/src/main/kotlin/mtype/` | Compile-time generic types |
| `L_` | Library framework | `frontend/src/main/kotlin/lmodel/` + `runtime-core/src/main/kotlin/{lib,lmodel}/` | Stdlib declarations + impls |
| `G_` | Grammar | — | Parser helpers |

Source-set roots are not uniform: `utils`, `rr-tree`, and `runtime-interpreter` use flat custom roots (`utils/src/model`, `rr-tree/src/main`, `runtime-interpreter/src/runtime`, …), while `frontend`, `runtime-core`, and `runtime-truffle` use the standard `src/main/kotlin`.

### Pipeline Flow
```
Source → [Compiler] → R_ (mutable, lazy) → [resolve()] → RR_ (immutable, serializable)
    → Rt_ interpreter (runtime-interpreter) | Truffle (runtime-truffle) | FlatBuffers (rr-serialization)
```

### RR_ Key Files
- **Data classes** (`rr-tree/src/main/`): `rr_base.kt`, `rr_def.kt`, `rr_def_base.kt`, `rr_attr.kt`, `rr_enum.kt`, `rr_param.kt`, `rr_frame.kt`, `rr_type.kt`, `rr_ir.kt`
- **R_→RR_ resolver** (`frontend/src/main/kotlin/model/rr/`): `rr_resolve.kt`, `rr_ir_resolve.kt`, `rr_base.kt`, `rr_resolver_runtime.kt`, `rr_constant_display.kt`
- **Interpreter** (`runtime-interpreter/src/runtime/`): `rr_interpreter.kt`, `rr_interp_expr.kt`, `rr_interp_stmt.kt`, `rr_interp_db_at.kt`, `rr_interp_db_write.kt`, `rr_interp_sql_gen.kt`, `rr_interp_meta.kt`, `rr_interp_gtv.kt`
- **Stdlib env** (`runtime-core/src/main/kotlin/runtime/`): `rr_stdlib_env.kt`

### Rt_ValueClass
Runtime type concept — every `Rt_Value` points at one via `Rt_Value.type`. The interface (`runtime-core/src/main/kotlin/runtime/rt_value.kt`) carries `name`, `rrType`, `comparator`, plus `gtvConversion`/`sqlAdapter`/`nativeConversion` and `cast()`. Capabilities are sealed-sibling interfaces (`Rt_GtvCompatibleValueClass`, `Rt_SqlCompatibleValueClass`, `Rt_NativeCompatibleValueClass`); a class either implements them or doesn't (no nullable bag).

### Database Operations
At-expressions: `entity @{}` (one), `@?{}` (0-1), `@*{}` (list), `@+{}` (1+) — `AtCardinality` in `utils/src/model/at_cardinality.kt`. Compiled to SQL via `DbSqlGen` in `runtime-interpreter/src/runtime/rr_interp_sql_gen.kt`.

## Coding Conventions, Testing, ABI, Release Notes

See `DEVELOPMENT.md` for full details. PostgreSQL is required for tests (prefer a local instance). Test knobs via project properties: `testJvmMaxHeap`, `junitParallelThreads`, `withLocales`, `regressionParallelism`, `regressionTestHeap`. Build failure with ABI errors → `./gradlew apiDump`, review `*.api` changes. Release notes live in `doc/release-notes/`, dev changes in `dev.txt`; for formatting and checklist, invoke the `write-release-notes` skill.

## Release Process

Full procedure: `doc/release-guide.md`. Key facts, and a hard rule to avoid mis-stating what "the current/next release" is:

- `RellVersions.VERSION_STR` (in `rell-base/utils/src/utils/RellVersions.kt`) and `build.gradle.kts`'s `version` reflect **`dev`'s own version**, not necessarily the release that's actually in flight. A patch release (e.g. `0.16.1`) is commonly branched from an existing release commit/tag, not from `dev` — `dev` can simultaneously be sitting on a higher, unrelated version (e.g. `0.17.0-SNAPSHOT`) for the next major/minor release.
- `doc/release-notes/dev.txt` describes unreleased changes accumulating on `dev`; it is not automatically the changelog for whatever release is currently being cut.
- **Never state a release version, or draft release notes/announcements, from `dev.txt` or `VERSION_STR` alone.** Always cross-check the actual release branches and tags first:
  ```shell
  git branch -a | grep '^\(remotes/origin/\)\?version-'
  git tag -l
  ```
  The branch/tag actually being worked (e.g. `version-0.16.1`) — and its own `doc/release-notes/<version>.txt` — is the source of truth for what that release contains. If unsure which release the user means, ask.
