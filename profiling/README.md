# Rell End-to-End Profiler

Profiles the complete Rell stack &mdash; interpreter, Postchain node, and PostgreSQL &mdash;
on a live local blockchain running a test dapp. Uses
[async-profiler](https://github.com/async-profiler/async-profiler) and
generates an HTML report with component breakdown, flame graphs, hotspot
tables, and database analysis.

## Prerequisites

- JDK 21; the project targets JDK 21 and async-profiler 4.3, see [local.properties](#localproperties) below
- Docker, for PostgreSQL via `work/psql/psql-docker.sh`
- Python 3.10+, standard library only
- PostgreSQL client tools (`psql`, `pg_isready`) for DB statistics

## OS support

async-profiler 4.3 supports **Linux (x86_64, aarch64)** and **macOS**. On Windows, run the profiler in WSL2.

Platform-specific notes:

| Platform    | Notes                                                                                                                                                                                                                                                                                     |
|-------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Linux**   | Kernel perf events may require lowering `kernel.perf_event_paranoid`: `sudo sysctl -w kernel.perf_event_paranoid=1`. Running inside Docker usually needs `--cap-add SYS_ADMIN --cap-add SYS_PTRACE --security-opt seccomp=unconfined`.                                                    |
| **macOS**   | No extra setup usually needed. If dynamic attach fails with *Operation not permitted*, the profiler falls back cleanly; a sudo re-run may help. System Integrity Protection can block profiling of hardened JVMs &mdash; use Temurin / plain OpenJDK, not Apple-codesigned distributions. |
| **Windows** | Not supported. Use WSL2.                                                                                                                                                                                                                                                                  |

## `local.properties`

Local configuration. Create one from the example:

```properties
# Required
JAVA_HOME=/path/to/jdk-21
```

## Quick start

```bash
# 1. Start PostgreSQL (from repo root)
./work/psql/psql-docker.sh &

# 2. Configure JDK 21 (once)
echo "JAVA_HOME=/path/to/jdk-21" > profiling/local.properties

# 3. Run the profiler &mdash; auto-builds chr if missing
cd profiling
./profile.py

# 4. Report opens automatically in your browser; raw output in reports/
```

```bash
./profile.py --users 50 --posts 20   # bigger workload (reuses existing build)
```

## Scripts

| Script               | Purpose                                                                   |
|----------------------|---------------------------------------------------------------------------|
| `profile.py`         | Main orchestrator &mdash; builds, starts node, profiles, generates report |
| `workload.py`        | Sends transactions + queries to a running node                            |
| `provision.py`       | Downloads async-profiler for your OS/arch                                 |
| `report/generate.py` | Parses profiling data and produces `report.html`                          |

## Options

```
./profile.py [--users N] [--posts N] [--profile-event EVENT]

  --users N          Number of test users (default: 20)
  --posts N          Posts per user (default: 10)
  --profile-event E  async-profiler event: cpu, wall, alloc, lock (default: cpu)
```

To force a full rebuild of chromia-cli, delete `chromia-cli-local/` first.

Environment variables:

| Variable           | Default                 | Description                          |
|--------------------|-------------------------|--------------------------------------|
| `PROFILER_VERSION` | `4.3`                   | async-profiler version to download   |
| `NODE_URL`         | `http://localhost:7740` | Postchain REST API URL               |
| `JAVA_ARGS`        | *(empty)*               | Extra JVM flags for the Chromia node |

## Test dapp

`dapp/` contains a minimal Rell application with:

- Entities: `user`, `post`, `tag`, `post_tag` (with keys and indexes)
- Operations: `create_user`, `update_bio`, `create_post`, `tag_post`
- Queries: `get_user`, `list_users`, `count_users`, `get_posts_by_user`,
  `count_posts`, `search_posts`, `get_posts_by_tag`, `get_user_post_count`

## Report

The generated `reports/report.html` is a standalone file containing:

- System information
- Total time, transaction/query counts, CPU samples
- Component breakdown: donut chart and table splitting CPU time into Rell interpreter, Postchain node, PostgreSQL/JDBC, and JVM overhead
- Workload phases: bar chart with throughput (ops/s) per phase
- Hot methods
- PostgreSQL tables
- PostgreSQL indexes
- Embedded interactive flame graph from async-profiler

`reports/profile.jfr` is openable in IntelliJ IDEA via
**Run → Open Profiler Snapshot** for deeper analysis.

## How it works

1. `profile.py` builds local Rell via `work/local-chr.sh` if `chr` is missing
2. Starts a single-node Chromia blockchain with `chr node start --wipe`
   using `JAVA_HOME` from `local.properties`
3. Attaches async-profiler to the JVM with `asprof start`
4. Snapshots PostgreSQL stats (pre-workload)
5. `workload.py` sends transactions (via `chr tx`) and queries (via REST API)
6. Uses `asprof dump` three times from the **same session** to emit
   `profile.jfr`, `collapsed.txt`, and `flamegraph.html` (no re-capturing)
7. Snapshots PostgreSQL stats (post-workload) and diffs against pre
8. `report/generate.py` parses everything into a standalone HTML report

## Component classification

Stack frames are classified by package prefix, walking the full stack
(a Rell method that calls into JVM stdlib is still attributed to Rell;
a JDBC call from Rell is attributed to PostgreSQL since that's where the
time is spent). Priority: **PostgreSQL > Rell > Postchain > JVM**.

| Component      | Matched packages / class prefixes                                                                                                  |
|----------------|------------------------------------------------------------------------------------------------------------------------------------|
| **Rell**       | `net.postchain.rell.*`, `lib.rell.*`, `Rt_*`, `R_*`, `C_*`, `L_*`, `M_*`, `S_*` (class-boundary match)                             |
| **PostgreSQL** | `org.postgresql.*`, `java.sql.*`, `javax.sql.*`, `com.zaxxer.hikari.*`, `net.postchain.base.data.*`, `net.postchain.common.data.*` |
| **Postchain**  | `net.postchain.*`, `com.chromia.*`                                                                                                 |
| **JVM**        | Everything else (GC, JIT, classloading, Kotlin stdlib, native waits)                                                               |
