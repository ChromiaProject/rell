# Rell Regression Toolkit

Clones a curated set of public (and optionally private) Rell projects and verifies that the
**local Rell build** still compiles *and runs the projects' own tests* against them. Runs are
long &mdash; the suite is not wired into the default CI pipeline; it ships as a manually-triggered
job (`pages:regression`) and a collection of Gradle tasks for local use.

The default pipeline per project is `chr install` &rarr; `chr build` &rarr; `chr test`, all
invoked against the locally-built `chr` (built from the Rell snapshot in this repo by the shared
`:performance:buildLocalChr` task). Projects without a `test:` block in their chromia.yml override
`commands` to drop the test step &mdash; otherwise `chr test` exits with "No tests to run".

Parallelism is owned by Gradle. The build script parses the JSON configs and generates one task
per project (`regressionFt4Lib`, `regressionDirectoryChain`, &hellip;) plus the aggregate
`regression` / `regressionPublic`. Each task fans every (project, backend) pipeline out across
Gradle's **worker pool** (bounded by `--max-workers` / `org.gradle.workers.max`); each work
item runs `chr install` &rarr; `chr build` &rarr; `chr test` end-to-end against its own
throw-away PostgreSQL spun up via Testcontainers (`withProjectPostgres` in
`src/regression/compile.kt`). The whole pipeline parallelises freely &mdash; no shared-schema
serialisation. Each project gets a fresh database, so suites never see leftover state from a
sibling run. Requires a reachable Docker daemon: locally pick it up from `DOCKER_HOST` /
`local.properties`; in CI the `.gitlab-ci.yml` variables already point at the DIND service.

## Quick start

```bash
# End-to-end: build chr, clone every project, compile + test each, render the HTML.
./gradlew :regression:regression

# Just one project (handy while debugging a single regression):
./gradlew :regression:regressionFt4Lib

# Public-only flavour (what CI runs; never touches private.json):
./gradlew :regression:regressionPublic

# Individual steps also exist:
./gradlew :performance:buildLocalChr       # build the local chr binary (shared across the repo)
./gradlew :regression:regressionClone      # clone (or pull) every repo into regression/workdir
./gradlew :regression:regressionReport     # merge reports/parts/*.json -> results.json -> report.html
```

Each per-project run writes a result fragment under `regression/reports/parts/`; `regressionReport`
(wired as a `finalizedBy` of every run task) merges them into `results.json` and renders the HTML.

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
