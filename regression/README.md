# Rell Regression Toolkit

Clones a curated set of public (and optionally private) Rell projects and verifies that the
**local Rell build** still compiles *and runs the projects' own tests* against them. Runs are
long &mdash; the suite is not wired into the default CI pipeline; it ships as a manually-triggered
job (`pages:regression`) and a collection of Gradle tasks for local use.

The default pipeline per project is `chr install` &rarr; `chr build` &rarr; `chr test`, all
invoked against the locally-built `chr` (built from the Rell snapshot in this repo by the shared
`:performance:buildLocalChr` task). Projects without a `test:` block in their chromia.yml override
`commands` to drop the test step &mdash; otherwise `chr test` exits with "No tests to run".

Parallelism is owned by JUnit Jupiter. A `@TestFactory`
(`test/regression/RegressionTest.kt`) emits one `DynamicTest` per project; cross-project
fan-out runs concurrently inside one test JVM (capped by `-PregressionParallelism`, default 4
locally / 8 in CI). Each test loops both backends (Interpreter &rarr; Truffle) sequentially so
`chr install` doesn't race against itself in the shared `src/lib/<name>` clone tree, and chr
itself runs out-of-process via `ProcessBuilder` &mdash; its ~2.5 GB JVM lives in the OS, not in
the test JVM. Each concurrent project also leases its own throw-away PostgreSQL spun up via
Testcontainers (`withProjectPostgres` in `src/regression/compile.kt`). Each project gets a
fresh database, so suites never see leftover state from a sibling run. Requires a reachable
Docker daemon: locally pick it up from `DOCKER_HOST` / `local.properties`; in CI the
`.gitlab-ci.yml` variables already point at the DIND service.

## Quick start

```bash
# End-to-end: build chr, clone every project, compile + test each, render the HTML.
./gradlew :regression:regression

# Just one project (handy while debugging a single regression):
./gradlew :regression:regression --tests "ft4-lib"

# Public-only flavour (what CI runs; never touches private.json):
./gradlew :regression:regressionPublic

# Individual steps also exist:
./gradlew :performance:buildLocalChr       # build the local chr binary (shared across the repo)
./gradlew :regression:regressionClone      # clone (or pull) every repo into regression/workdir
./gradlew :regression:regressionReport     # merge reports/parts/*.json -> results.json -> report.html
```

Each project run writes a result fragment under `regression/reports/parts/`; `regressionReport`
(wired as a `finalizedBy` of every Test task) merges them into `results.json` and renders the
HTML. `ignoreFailures = true` on the Test task means a failing project surfaces in the HTML but
doesn't fail the build &mdash; the report is the verdict.

Reports land in `regression/reports/report.html`.

## Project configuration

The toolkit reads two JSON files in this directory:

- **`public.json`** &mdash; tracked. Open-source Rell projects: ft4-lib, postchain-eif, etc.
- **`private.json`** &mdash; gitignored. Copy `private.json.example`, fill in the URLs you have access to.

Each entry:

```jsonc
{
  "name": "ft4-lib",                              // workdir directory + report row label
  "url": "https://gitlab.com/chromaway/ft4-lib",  // git clone URL
  "ref": "development",                           // optional; default = repository default branch
  "rellPath": ".",                                // dir containing chromia.yml, relative to repo root
  "commands": [["install"], ["build"], ["test"]], // chr invocations run sequentially from rellPath;
                                                  // non-zero exit short-circuits.
                                                  // Default: [["install"], ["build"], ["test"]].
                                                  // Drop ["test"] for projects without a test: block.
  "expectedFailure": false,                       // ok to fail; reported as "expected fail"
  "notes": "Reference Chromia FT4 token-fungibles library."
}
```
