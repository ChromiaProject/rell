# Rell Regression Toolkit

Clones a curated set of public (and optionally private) Rell projects and verifies that the
**local Rell build** still compiles *and runs the projects' own tests* against them. Runs are
long &mdash; the suite is not wired into the default CI pipeline; it ships as a manually-triggered
job (`pages:regression`) and a collection of Gradle tasks for local use.

The default pipeline per project is `chr install` &rarr; `chr build` &rarr; `chr test`, all
invoked against the locally-bootstrapped `chr` (built from the Rell snapshot in this repo via
`work/local-chr.sh`). Projects without a `test:` block in their chromia.yml override `commands`
to drop the test step &mdash; otherwise `chr test` exits with "No tests to run".

`chr install` and `chr build` run **in parallel** across projects: each only touches its own
clone tree, so the compile phase fans them out over a worker pool (sized to the CPU count, or
to `REGRESSION_COMPILE_JOBS` if that env var is set &mdash; useful for capping memory on a CI
runner). `chr test` then runs **serially** &mdash; every project's suite hits the same local
PostgreSQL instance, and concurrent runs would race on shared schemas.

## Quick start

```bash
# 1. Make sure the bootstrap-once chr build will succeed (see work/local-chr.sh prerequisites).
# 2. End-to-end: clone every project, compile + test each, render the HTML.
./gradlew :regression:regression

# Or, step-by-step:
./gradlew :regression:regressionClone     # clone (or pull) every repo into regression/workdir
./gradlew :regression:regressionCompile   # run install/build/test against each; writes results.json
./gradlew :regression:regressionReport    # rebuild the HTML from cached results.json
```

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
