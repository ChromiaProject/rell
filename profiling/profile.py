#!/usr/bin/env python3
"""
End-to-end profiling orchestrator for the Rell stack.

Builds local Rell, starts a Chromia node with a test dapp, attaches
async-profiler, runs a workload, and generates an HTML report with
component breakdown (Rell / Postchain / PostgreSQL / JVM).

Usage:
  ./profile.py [--skip-build] [--users N] [--posts N] [--profile-event EVENT]

Prerequisites:
  - Docker (for PostgreSQL via work/psql/psql-docker.sh)
  - Java 21, Maven, Git
  - Python 3.8+

Environment:
  PROFILER_VERSION  async-profiler version (default: 3.0)
  NODE_URL          Postchain REST API (default: http://localhost:7740)
  JAVA_ARGS         extra JVM flags forwarded to the Chromia node
"""

import argparse
import atexit
import json
import os
import platform
import re
import shutil
import signal
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import NoReturn
from urllib.error import URLError
from urllib.request import urlopen

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
REPORT_DIR = SCRIPT_DIR / "reports"
DAPP_DIR = SCRIPT_DIR / "dapp"

CHR_BIN = REPO_ROOT / "chromia-cli-local" / "chromia-cli" / "target" / "chromia-cli-dev-dist" / "bin" / "chr"
ASPROF = SCRIPT_DIR / "async-profiler" / "bin" / "asprof"

NODE_URL = os.environ.get("NODE_URL", "http://localhost:7740")

node_process: subprocess.Popen | None = None


def log(msg: str) -> None:
    ts = time.strftime("%H:%M:%S", time.gmtime())
    print(f"[profile] {ts} {msg}", flush=True)


def die(msg: str) -> NoReturn:
    log(f"FATAL: {msg}")
    sys.exit(1)


def cleanup() -> None:
    global node_process
    if node_process and node_process.poll() is None:
        log(f"Stopping node (PID {node_process.pid})...")
        node_process.terminate()
        try:
            node_process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            node_process.kill()
        node_process = None


atexit.register(cleanup)
# 128 + signal number is the POSIX convention for signal-triggered exits
signal.signal(signal.SIGINT, lambda *_: sys.exit(130))
signal.signal(signal.SIGTERM, lambda *_: sys.exit(143))


def check_prereqs() -> None:
    for cmd in ["java", "git", "docker", "curl"]:
        if not shutil.which(cmd):
            die(f"{cmd} is required but not found on PATH")


def load_local_properties() -> dict[str, str]:
    """Load key=value pairs from profiling/local.properties (git-ignored)."""
    props = {}
    path = SCRIPT_DIR / "local.properties"
    if not path.exists():
        return props
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" in line:
            k, v = line.split("=", 1)
            props[k.strip()] = v.strip()
    return props


def pg_is_ready() -> bool:
    if shutil.which("pg_isready"):
        r = subprocess.run(["pg_isready", "-h", "127.0.0.1", "-p", "5432", "-q"],
                           capture_output=True)
        return r.returncode == 0
    try:
        import socket
        s = socket.create_connection(("127.0.0.1", 5432), timeout=2)
        s.close()
        return True
    except OSError:
        return False


def node_is_up(url: str) -> bool:
    try:
        urlopen(f"{url}/brid/iid_0", timeout=2)
        return True
    except (URLError, OSError):
        return False


def find_java_pid() -> str | None:
    """Find the JVM PID running the Chromia node."""
    try:
        r = subprocess.run(["jps", "-l"], capture_output=True, text=True, timeout=5)
        for line in r.stdout.splitlines():
            if any(kw in line.lower() for kw in ["postchain", "chromia", "mainkt"]):
                return line.split()[0]
    except (subprocess.TimeoutExpired, FileNotFoundError):
        pass

    try:
        r = subprocess.run(["pgrep", "-f", "postchain|chromia.*node|net.postchain"],
                           capture_output=True, text=True, timeout=5)
        pids = r.stdout.strip().splitlines()
        if pids:
            return pids[0]
    except (subprocess.TimeoutExpired, FileNotFoundError):
        pass

    if node_process:
        return str(node_process.pid)
    return None


def asprof_cmd(*args: str) -> subprocess.CompletedProcess:
    return subprocess.run([str(ASPROF), *args], capture_output=True, text=True, timeout=30)


def human_size(path: Path) -> str:
    if not path.exists():
        return "0 B"
    b = path.stat().st_size
    for unit in ["B", "KB", "MB", "GB"]:
        if abs(b) < 1024:
            return f"{b:.1f} {unit}"
        b /= 1024
    return f"{b:.1f} TB"


def collect_pg_stats(run_dir: Path, suffix: str = "") -> None:
    """Collect PostgreSQL statistics snapshot."""
    queries = {
        f"pg-table-stats{suffix}.json": """
            SELECT json_agg(row_to_json(t)) FROM (
                SELECT schemaname, relname, seq_scan, seq_tup_read, idx_scan,
                       idx_tup_fetch, n_tup_ins, n_tup_upd, n_tup_del, n_live_tup
                FROM pg_stat_user_tables
                ORDER BY seq_tup_read + coalesce(idx_tup_fetch,0) DESC
            ) t;""",
        f"pg-index-stats{suffix}.json": """
            SELECT json_agg(row_to_json(t)) FROM (
                SELECT schemaname, indexrelname, idx_scan, idx_tup_read,
                       idx_tup_fetch, pg_relation_size(indexrelid) as index_size_bytes
                FROM pg_stat_user_indexes
                ORDER BY idx_scan DESC
            ) t;""",
        f"pg-sizes{suffix}.json": """
            SELECT json_agg(row_to_json(t)) FROM (
                SELECT schemaname, relname, pg_total_relation_size(relid) as total_bytes,
                       pg_table_size(relid) as table_bytes,
                       pg_indexes_size(relid) as indexes_bytes
                FROM pg_stat_user_tables
                ORDER BY pg_total_relation_size(relid) DESC
            ) t;""",
    }

    env = {**os.environ, "PGPASSWORD": "postchain"}
    for filename, query in queries.items():
        out_path = run_dir / filename
        try:
            r = subprocess.run(
                ["psql", "-h", "127.0.0.1", "-p", "5432", "-U", "postchain",
                 "-d", "postchain", "-q", "-t", "-A", "-c", query],
                capture_output=True, text=True, env=env, timeout=10,
            )
            result = r.stdout.strip()
            if result and result != "null":
                out_path.write_text(result)
            else:
                out_path.write_text("[]")
        except (subprocess.TimeoutExpired, FileNotFoundError):
            out_path.write_text("[]")


def diff_pg_stats(run_dir: Path) -> None:
    """Diff before/after PG snapshots, write only tables with delta > 0.
    Sizes are absolute, not deltas — keep the post-workload snapshot
    filtered to schemas that saw activity (so leftover test schemas
    in the shared dev DB don't dominate)."""
    INT_COLS = {"seq_scan", "seq_tup_read", "idx_scan", "idx_tup_fetch",
                "n_tup_ins", "n_tup_upd", "n_tup_del", "idx_tup_read"}

    active_schemas: set[str] = set()

    for name in ["pg-table-stats", "pg-index-stats", "pg-sizes"]:
        before_path = run_dir / f"{name}-before.json"
        after_path = run_dir / f"{name}-after.json"
        out_path = run_dir / f"{name}.json"

        try:
            before = json.loads(before_path.read_text()) or []
            after = json.loads(after_path.read_text()) or []
        except (json.JSONDecodeError, FileNotFoundError, TypeError):
            out_path.write_text("[]")
            continue

        if name == "pg-sizes":
            filtered = [r for r in after if r.get("schemaname", "") in active_schemas]
            filtered.sort(key=lambda r: r.get("total_bytes", 0) or 0, reverse=True)
            out_path.write_text(json.dumps(filtered[:30]))
            before_path.unlink(missing_ok=True)
            after_path.unlink(missing_ok=True)
            continue

        # Index "before" by (schemaname, relname/indexrelname)
        key_col = "indexrelname" if "index" in name else "relname"
        before_map = {}
        for row in before:
            k = (row.get("schemaname", ""), row.get(key_col, ""))
            before_map[k] = row

        result = []
        for row in after:
            k = (row.get("schemaname", ""), row.get(key_col, ""))
            prev = before_map.get(k, {})
            diff = dict(row)
            has_delta = False
            for col in INT_COLS:
                if col in diff and diff[col] is not None:
                    old_val = prev.get(col) or 0
                    new_val = diff[col] or 0
                    delta = new_val - old_val
                    diff[col] = delta
                    if delta > 0:
                        has_delta = True
            if has_delta:
                result.append(diff)
                active_schemas.add(diff.get("schemaname", ""))

        # Sort by activity and limit
        result.sort(key=lambda r: sum(r.get(c, 0) or 0 for c in INT_COLS), reverse=True)
        out_path.write_text(json.dumps(result[:30]))

        # Clean up snapshots
        before_path.unlink(missing_ok=True)
        after_path.unlink(missing_ok=True)


def detect_total_memory_bytes() -> int:
    """Detect physical memory in bytes. Uses platform-native sources
    because Python's sysconf('SC_PHYS_PAGES') reports the wrong value
    on macOS (it reports installed capacity minus the firmware reserve,
    but rounded to the next marketed bin on Apple Silicon)."""
    system = platform.system()

    if system == "Darwin":
        # sysctl hw.memsize is authoritative on macOS
        try:
            r = subprocess.run(["sysctl", "-n", "hw.memsize"],
                               capture_output=True, text=True, timeout=2, check=True)
            return int(r.stdout.strip())
        except (subprocess.SubprocessError, FileNotFoundError, ValueError):
            return 0

    if system == "Linux":
        # /proc/meminfo MemTotal is in kB
        try:
            with open("/proc/meminfo") as f:
                for line in f:
                    if line.startswith("MemTotal:"):
                        return int(line.split()[1]) * 1024
        except OSError:
            pass

    # Generic fallback (unreliable on some platforms)
    try:
        return os.sysconf("SC_PAGE_SIZE") * os.sysconf("SC_PHYS_PAGES")
    except (ValueError, OSError):
        return 0


def collect_system_info(run_dir: Path, args: argparse.Namespace,
                        versions: dict[str, str], java_home: str | None) -> None:
    cpus = os.cpu_count() or 0
    mem_bytes = detect_total_memory_bytes()
    # Binary gigabytes (GiB) — matches what macOS "About This Mac" shows
    mem_gib = round(mem_bytes / (1024 ** 3), 1) if mem_bytes else 0

    # Resolve the Java version actually used by the node (from JAVA_HOME
    # override in local.properties, or PATH default).
    node_java_ver = "unknown"
    java_bin = (Path(java_home) / "bin" / "java") if java_home else Path("java")
    try:
        r = subprocess.run([str(java_bin), "-version"],
                           capture_output=True, text=True, timeout=5)
        out = (r.stderr or r.stdout).strip().splitlines()
        if out:
            node_java_ver = out[0]
    except (subprocess.TimeoutExpired, FileNotFoundError):
        pass

    # async-profiler version (from asprof --version)
    asprof_ver = "unknown"
    try:
        r = subprocess.run([str(ASPROF), "--version"],
                           capture_output=True, text=True, timeout=5)
        line = (r.stdout or r.stderr).strip().splitlines()
        if line:
            asprof_ver = line[0]  # e.g. "Async-profiler 4.3 built on Jan 13 2026"
    except (subprocess.TimeoutExpired, FileNotFoundError):
        pass

    info = {
        "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "hostname": platform.node(),
        "os": f"{platform.system()} {platform.release()}",
        "arch": platform.machine(),
        "cpus": cpus,
        "memory_gib": mem_gib,
        "java_home": java_home or "(system default)",
        "java_version": node_java_ver,
        "async_profiler": asprof_ver,
        "versions": versions,
        "profiler_event": args.profile_event,
        "num_users": args.users,
        "posts_per_user": args.posts,
    }
    (run_dir / "system-info.json").write_text(json.dumps(info, indent=2))


def main() -> None:
    global node_process

    parser = argparse.ArgumentParser(description="Rell end-to-end profiler")
    parser.add_argument("--users", type=int, default=20, help="Number of test users (default: 20)")
    parser.add_argument("--posts", type=int, default=10, help="Posts per user (default: 10)")
    parser.add_argument("--profile-event", default="cpu",
                        help="async-profiler event: cpu, wall, alloc, lock (default: cpu)")
    args = parser.parse_args()

    log("=== Rell End-to-End Profiler ===")
    log("")

    # Load local.properties
    local_props = load_local_properties()

    # Preflight
    check_prereqs()

    if not ASPROF.exists():
        log("Provisioning async-profiler...")
        subprocess.run([sys.executable, str(SCRIPT_DIR / "provision.py")], check=True)
    if not ASPROF.exists():
        die(f"async-profiler not found at {ASPROF}")

    if not pg_is_ready():
        log("WARNING: PostgreSQL not reachable on localhost:5432")
        die("Start it with: ./work/psql/psql-docker.sh")

    # Run directory — single "latest" output, wiped each run
    run_dir = REPORT_DIR
    if run_dir.exists():
        shutil.rmtree(run_dir)
    run_dir.mkdir(parents=True, exist_ok=True)
    log(f"Output directory: {run_dir}")
    log(f"Profile event: {args.profile_event}")
    log(f"Workload: {args.users} users, {args.posts} posts/user")
    log("")

    # Step 1: Build chromia-cli if the chr binary is missing.
    # For a fresh rebuild, delete chromia-cli-local/ first.
    if CHR_BIN.exists():
        log("Step 1: chr binary exists, skipping build")
    else:
        log(f"Step 1: chr not found at {CHR_BIN} — building Rell + chromia-cli...")
        r = subprocess.run(
            ["bash", str(REPO_ROOT / "work" / "local-chr.sh"), "--version"],
            cwd=str(REPO_ROOT), timeout=600,
        )
        if r.returncode != 0:
            die("Build failed")

    if not CHR_BIN.exists():
        die(f"chr binary not found at {CHR_BIN}")
    log(f"  chr: {CHR_BIN}")

    # Parse multi-line `chr --version` output:
    #   chr version dev
    #   rell version 0.16.0-SNAPSHOT
    #   postchain version 3.49.2
    #   EIF version 0.32.0
    #   Java version 25.0.2  (← this is the chr process's Java, NOT the node's)
    versions: dict[str, str] = {}
    try:
        # Run chr with the same JAVA_HOME we'll give the node, so the
        # reported Java version is accurate.
        chr_env = dict(os.environ)
        if "JAVA_HOME" in local_props:
            chr_env["JAVA_HOME"] = local_props["JAVA_HOME"]
            chr_env["PATH"] = f"{local_props['JAVA_HOME']}/bin:{chr_env.get('PATH', '')}"
        r = subprocess.run([str(CHR_BIN), "--version"],
                           capture_output=True, text=True, timeout=15, env=chr_env)
        for line in (r.stdout or r.stderr).strip().splitlines():
            line = line.strip()
            m = re.match(r"(.+?) version (.+)$", line, flags=re.IGNORECASE)
            if m:
                versions[m.group(1).strip().lower()] = m.group(2).strip()
    except (subprocess.TimeoutExpired, FileNotFoundError):
        pass

    rell_version = versions.get("rell", "unknown")
    log(f"  Versions: chr={versions.get('chr','?')}, rell={rell_version}, "
        f"postchain={versions.get('postchain','?')}, EIF={versions.get('eif','?')}, "
        f"java={versions.get('java','?')}")

    # Step 2: System info
    log("Step 2: Collecting system info...")
    collect_system_info(run_dir, args, versions, local_props.get("JAVA_HOME"))

    # Step 3: Start node
    log("Step 3: Starting Chromia node with test dapp...")

    if node_is_up(NODE_URL):
        die(f"A node is already running on {NODE_URL} -- stop it first")

    java_args = os.environ.get("JAVA_ARGS", "")
    # -XX:+EnableDynamicAgentLoading: JDK 21+ disables dynamic agent
    # attach by default; async-profiler attaches as a JVMTI agent.
    java_args += (" --enable-native-access=ALL-UNNAMED"
                  " -XX:+EnableDynamicAgentLoading"
                  " -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints")
    env = {**os.environ, "JAVA_ARGS": java_args.strip()}

    # Apply JAVA_HOME from local.properties (project requires JDK 21)
    if "JAVA_HOME" in local_props:
        java_home = local_props["JAVA_HOME"]
        if not Path(java_home).is_dir():
            die(f"JAVA_HOME from local.properties does not exist: {java_home}")
        env["JAVA_HOME"] = java_home
        env["PATH"] = f"{java_home}/bin:{env.get('PATH', '')}"
        log(f"  JAVA_HOME: {java_home}")

    node_log_path = run_dir / "node.log"
    node_log = open(node_log_path, "w")
    node_process = subprocess.Popen(
        [str(CHR_BIN), "node", "start", "--wipe"],
        cwd=str(DAPP_DIR), stdout=node_log, stderr=subprocess.STDOUT, env=env,
    )
    log(f"  Node PID: {node_process.pid}")
    log("  Waiting for node to become ready...")

    NODE_START_TIMEOUT = 120
    start_time = time.monotonic()
    deadline = start_time + NODE_START_TIMEOUT
    while not node_is_up(NODE_URL):
        if time.monotonic() > deadline:
            node_log.flush()
            log("  Last 20 lines of node log:")
            try:
                for line in node_log_path.read_text().splitlines()[-20:]:
                    log(f"    {line}")
            except OSError as e:
                log(f"    (failed to read log: {e})")
            die(f"Node did not start within {NODE_START_TIMEOUT}s")
        if node_process.poll() is not None:
            die(f"Node process exited with code {node_process.returncode}")
        time.sleep(1)

    log(f"  Node ready after {int(time.monotonic() - start_time)}s")

    java_pid = find_java_pid()
    log(f"  JVM PID: {java_pid}")
    if not java_pid:
        die("Could not find JVM process")

    # Step 4: Attach async-profiler.
    # JFR must be configured as the primary output at `start` time —
    # that's the only way async-profiler will write the JFR stream.
    # Later we `dump` the same captured data in other formats
    # (collapsed, flamegraph) without stopping or re-capturing.
    jfr_path = run_dir / "profile.jfr"
    collapsed_path = run_dir / "collapsed.txt"
    fg_path = run_dir / "flamegraph.html"
    log(f"Step 4: Attaching async-profiler (event={args.profile_event})...")
    r = asprof_cmd("start", "-e", args.profile_event, "--total",
                    "-o", "jfr", "-f", str(jfr_path), java_pid)
    profiler_attached = r.returncode == 0
    if not profiler_attached:
        log(f"  WARNING: Could not attach: {r.stderr.strip()}")
        log("  On macOS you may need to allow ptrace; on Linux see the note")
        log("  on kernel.perf_event_paranoid in profiling/README.md")
    else:
        log(f"  {r.stdout.strip()}")

    # Snapshot PG stats before workload (for diffing)
    collect_pg_stats(run_dir, suffix="-before")

    # Step 5: Workload
    log("Step 5: Running workload...")
    wl_result = subprocess.run(
        [sys.executable, str(SCRIPT_DIR / "workload.py"),
         "--chr", str(CHR_BIN),
         "--users", str(args.users),
         "--posts", str(args.posts),
         "--node-url", NODE_URL,
         "--results", str(run_dir / "workload-results.json")],
        timeout=1800,
    )
    if wl_result.returncode != 0:
        log("WARNING: Workload finished with errors")

    # Step 6: Collect profiling data
    log("Step 6: Collecting profiling data...")

    if profiler_attached:
        # Dump the same captured data in extra formats (collapsed, flame
        # graph). `dump` emits output without stopping the session.
        for path, fmt, desc in [
            (collapsed_path, "collapsed",  "collapsed stacks (report input)"),
            (fg_path,        "flamegraph", "interactive flame graph"),
        ]:
            r = asprof_cmd("dump", "-o", fmt, "-f", str(path), java_pid)
            if r.returncode == 0 and path.exists() and path.stat().st_size > 0:
                log(f"  {path.name:<18} {human_size(path):>8}  — {desc}")
            else:
                log(f"  WARNING: failed to dump {fmt}: {r.stderr.strip()}")

        # Stop finalizes the JFR file that was configured at `start`.
        asprof_cmd("stop", java_pid)
        if jfr_path.exists() and jfr_path.stat().st_size > 0:
            log(f"  {jfr_path.name:<18} {human_size(jfr_path):>8}  "
                f"— JFR (openable in IntelliJ IDEA: Run > Open Profiler Snapshot)")

    # PostgreSQL stats — diff before/after snapshots
    log("  Collecting PostgreSQL statistics...")
    collect_pg_stats(run_dir, suffix="-after")
    diff_pg_stats(run_dir)
    log("  Collected: pg-table-stats.json, pg-index-stats.json, pg-sizes.json")

    # Step 7: Stop node
    log("Step 7: Stopping node...")
    cleanup()
    node_log.close()
    log("  Node stopped")

    # Step 8: Generate report
    log("Step 8: Generating HTML report...")
    subprocess.run(
        [sys.executable, str(SCRIPT_DIR / "report" / "generate.py"), str(run_dir)],
        check=True,
    )

    report_path = run_dir / "report.html"
    log("")
    log("=== Profiling complete ===")
    log(f"  Report:   {report_path}")
    log(f"  JFR:      {jfr_path}")
    log(f"  Run dir:  {run_dir}")
    log("")

    # Open in browser
    if shutil.which("open"):
        subprocess.run(["open", str(report_path)], check=False)
    elif shutil.which("xdg-open"):
        subprocess.run(["xdg-open", str(report_path)], check=False)


if __name__ == "__main__":
    main()
