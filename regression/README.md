# Rell Regression Toolkit

Clones a curated set of public (and optionally private) Rell projects and verifies that the
**local Rell build** still compiles them. Runs are long &mdash; the suite is not wired into the
default CI pipeline; it ships as a manually-triggered job (`pages:regression`) and a
collection of Gradle tasks for local use.

The main goal is narrow on purpose: *does Rell compile this project?* Currently, the toolkit is **not** a behavioural
test runner &mdash; it does not start blockchains, exercise integration tests, or compare on-chain output.

## Quick start

```bash
# 1. Make sure the bootstrap-once chr build will succeed (see work/local-chr.sh prerequisites).
# 2. End-to-end: clone every project, compile each, render the HTML.
./gradlew :regression:regression

# Or, step-by-step:
./gradlew :regression:regressionClone     # clone (or pull) every repo into regression/workdir
./gradlew :regression:regressionCompile   # run `chr install && chr build` against each
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
  "commands": [["install"], ["build"]],           // chr invocations run sequentially from rellPath;
                                                  // non-zero exit short-circuits.
                                                  // Default: [["install"], ["build"]].
  "expectedFailure": false,                       // ok to fail; reported as "expected fail"
  "notes": "Reference Chromia FT4 token-fungibles library."
}
```
