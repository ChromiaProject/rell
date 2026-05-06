# Rell Performance Suite

Two complementary tools, one Gradle module:

- **JMH microbenchmarks** — parser, interpreter, and Truffle backends pushed through hand-tuned and real-world Rell workloads. Output: kotlinx-benchmark JSON → HTML report.
- **End-to-end profiler** — builds local Rell, starts a Chromia node with a test dapp, attaches async-profiler via the HotSpot Attach API, runs a workload, and renders an HTML report with component breakdown (Rell / Postchain / PostgreSQL / JVM), hot methods, PG stats, and an embedded interactive flame graph.

## Quick start — JMH benchmarks

```bash
./gradlew :performance:mainBenchmark           # all suites, all backends
./gradlew :performance:smokeFt4                # quick smoke (no JMH)
./gradlew :performance:smokeMna
./gradlew :performance:traceTruffle            # Truffle compilation log (deopts, PE failures)
```

The HTML lands in `performance/build/reports/benchmarks/html/report.html`.

GraalVM is required for execution - for proper performance of Truffle.

## Profile a single sample query

`profileSample` runs **one** Rell query under in-process async-profiler — no node, no
workload, no HTML. Output is plain text (`flat.txt`, `tree.txt`, `collapsed.txt`) sized
for an LLM to read and decide which subtrees / hot methods to optimise.

```bash
# Defaults: synthetic_bench/bench, interpreter, 200 reps after 30 warmups, top-30 flat.
./gradlew :performance:profileSample --args="--sample synthetic_bench"

# Pick another sample + query, smaller arg, Truffle backend.
./gradlew :performance:profileSample \
    --args="--sample mna_bench --query bench_decimal_pow --arg 50 --backend truffle"

# Custom output dir, all five formats.
./gradlew :performance:profileSample \
    --args="--sample ft4_bench --formats flat,tree,collapsed,flamegraph,jfr --output-dir /tmp/prof"
```

Default output dir: `performance/reports/sample-<sample>-<query>/`. The top-N flat
profile is also printed to stdout for quick inspection. `--sample` directories live
under `performance/src/main/resources/`: `synthetic_bench`, `ft4_bench`, `mna_bench`.

## Quick start — end-to-end profiler

```bash
# Prerequisites: PostgreSQL on localhost:5432 (./work/psql/psql-docker.sh).
./gradlew :performance:profile --args="--users 50 --posts 20"
```

`profile` auto-provisions async-profiler on first run (downloads into
`performance/async-profiler/`); `:performance:provisionAsprof` exists as an opt-in
escape hatch if you want the download cached ahead of time.

Output: `performance/reports/report.html` (auto-opens in a browser when run from a desktop session) + `profile.jfr`, `flamegraph.html`, and PostgreSQL snapshot diffs.

### Profiler options

```
profile.kt [--users N] [--posts N] [--profile-event EVENT]
```

Environment variables:

| Variable           | Default                 | Description                          |
|--------------------|-------------------------|--------------------------------------|
| `PROFILER_VERSION` | `4.3`                   | async-profiler version to download   |
| `NODE_URL`         | `http://localhost:7740` | Postchain REST API URL               |
| `JAVA_ARGS`        | *(empty)*               | Extra JVM flags for the Chromia node |

`local.properties` supplies `JAVA_HOME` for the node.

## OS support

async-profiler 4.3 covers **Linux (x86_64, aarch64)** and **macOS**. On Windows, run under WSL2.

| Platform    | Notes                                                                                                                                                                |
|-------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Linux**   | Kernel perf events may require lowering `kernel.perf_event_paranoid` (`sudo sysctl -w kernel.perf_event_paranoid=1`). Inside Docker, see the original profiler docs. |
| **macOS**   | Usually no extra setup. SIP can block hardened JVMs — use Temurin / plain OpenJDK, not Apple-codesigned distributions.                                               |

## Test dapp

`dapp/` contains a minimal Rell application with `user`, `post`, `tag`, `post_tag` entities, four operations, and eight queries. It's the workload driver for the end-to-end profiler.

## Component classification

Stack frames are tagged by walking the full stack. Priority **PostgreSQL > Rell > Postchain > JVM**, with PostgreSQL further split by upstream caller — `PostgreSQL (Rell)` for SQL emitted by Rell-generated queries vs `PostgreSQL (Postchain)` for block-storage / consensus SQL.

| Component      | Matched packages / class prefixes                                                                                                  |
|----------------|------------------------------------------------------------------------------------------------------------------------------------|
| **Rell**       | `net.postchain.rell.*`, `lib.rell.*`, `Rt_*`, `R_*`, `C_*`, `L_*`, `M_*`, `S_*` (class-boundary match)                             |
| **PostgreSQL** | `org.postgresql.*`, `java.sql.*`, `javax.sql.*`, `com.zaxxer.hikari.*`, `net.postchain.base.data.*`, `net.postchain.common.data.*` |
| **Postchain**  | `net.postchain.*`, `com.chromia.*`                                                                                                 |
| **JVM**        | Everything else (GC, JIT, classloading, Kotlin stdlib, native waits)                                                               |

## How the profiler works

1. Builds local Rell via `work/local-chr.sh` if `chr` is missing (subprocess — no Java entry point).
2. Starts a single-node Chromia blockchain with `chr node start --wipe`.
3. Attaches async-profiler via the HotSpot **Attach API** (`com.sun.tools.attach.VirtualMachine`) — the same mechanism the `asprof` CLI uses, just without spawning it.
4. Snapshots PostgreSQL stats over **JDBC**.
5. Drives transactions and queries via the `WorkloadCommand`.
6. Issues `dump` commands to the same loaded agent for `collapsed.txt` / `flamegraph.html`, then `stop` to finalise the JFR file.
7. Snapshots PG stats *after* the workload and diffs against the *before* snapshot.
