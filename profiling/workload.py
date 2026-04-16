#!/usr/bin/env python3
"""
Generates test workload against a running Chromia node.
Sends transactions via `chr` CLI and queries via REST API.

Usage: ./workload.py [--chr PATH] [--users N] [--posts N] [--node-url URL] [--results PATH]
"""

import argparse
import concurrent.futures
import json
import os
import subprocess
import sys
import time
from pathlib import Path
from urllib.error import URLError
from urllib.request import Request, urlopen

SCRIPT_DIR = Path(__file__).resolve().parent
DAPP_DIR = SCRIPT_DIR / "dapp"
DEFAULT_CHR = SCRIPT_DIR.parent / "chromia-cli-local" / "chromia-cli" / "target" / "chromia-cli-dev-dist" / "bin" / "chr"


def log(msg: str) -> None:
    ts = time.strftime("%H:%M:%S", time.gmtime())
    print(f"[workload] {ts} {msg}", flush=True)


def wait_for_node(url: str, timeout: int = 60) -> None:
    log(f"Waiting for node at {url} ...")
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            urlopen(f"{url}/brid/iid_0", timeout=2)
            log("Node is ready")
            return
        except (URLError, OSError):
            time.sleep(1)
    sys.exit(f"Node did not become ready after {timeout}s")


def get_brid(url: str) -> str:
    resp = urlopen(f"{url}/brid/iid_0", timeout=5)
    return resp.read().decode().strip().strip('"')


def chr_tx(chr_bin: str, node_url: str, brid: str, op: str, *args: str) -> int:
    """Submit one transaction via chr. Returns return-code.
    --await blocks until the tx is included in a block.
    Note: chr accepts -brid (single dash), not --brid."""
    # chr CLI expects each Rell argument as a GTV literal: text values
    # must be quoted, numbers are bare. Assume text args for now.
    gtv_args = [f'"{a}"' for a in args]
    cmd = [chr_bin, "tx", "--await", "--api-url", node_url, "-brid", brid,
           op, *gtv_args]
    r = subprocess.run(cmd, cwd=str(DAPP_DIR), capture_output=True, timeout=60)
    return r.returncode


def chr_tx_parallel(chr_bin: str, node_url: str, brid: str,
                    tasks: list[tuple[str, tuple[str, ...]]],
                    workers: int) -> int:
    """Submit many transactions concurrently. With many parallel
    submissions, multiple transactions land in the same block — the
    node stops sitting idle between blocks.

    Returns the number of failures.
    """
    failures = 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        futures = [pool.submit(chr_tx, chr_bin, node_url, brid, op, *args)
                   for op, args in tasks]
        for f in concurrent.futures.as_completed(futures):
            if f.result() != 0:
                failures += 1
    return failures


def rest_query(node_url: str, body: dict) -> bytes:
    data = json.dumps(body).encode()
    req = Request(f"{node_url}/query/iid_0", data=data, headers={"Content-Type": "application/json"})
    try:
        return urlopen(req, timeout=10).read()
    except (URLError, OSError):
        return b'{"error":"query failed"}'


def timed(fn) -> float:
    start = time.monotonic()
    fn()
    return round(time.monotonic() - start, 3)


def main() -> None:
    parser = argparse.ArgumentParser(description="Rell profiling workload generator")
    parser.add_argument("--chr", default=str(DEFAULT_CHR), help="Path to chr binary")
    parser.add_argument("--users", type=int, default=20, help="Number of test users")
    parser.add_argument("--posts", type=int, default=10, help="Posts per user")
    parser.add_argument("--node-url", default=os.environ.get("NODE_URL", "http://localhost:7740"))
    parser.add_argument("--results", default=None, help="Path for results JSON")
    parser.add_argument("--tx-workers", type=int, default=16,
                        help="Concurrent chr tx submitters (default: 16). "
                             "Higher values pack more tx per block and keep "
                             "the node from idling between blocks.")
    args = parser.parse_args()

    results_path = Path(args.results) if args.results else SCRIPT_DIR / "reports" / "workload-results.json"
    results_path.parent.mkdir(parents=True, exist_ok=True)

    wait_for_node(args.node_url)
    brid = get_brid(args.node_url)
    log(f"Blockchain RID: {brid}")

    total_start = time.monotonic()

    workers = args.tx_workers
    log(f"Using {workers} parallel tx submitters")

    # Phase 1: Create users (parallel — fills blocks instead of one-per-block)
    log(f"Phase 1: Creating {args.users} users...")
    tasks = [("create_user",
              (f"user_{i}", f"Bio for user {i} — blockchain developer and tester"))
             for i in range(1, args.users + 1)]
    def phase1():
        fails = chr_tx_parallel(args.chr, args.node_url, brid, tasks, workers)
        if fails: log(f"  WARNING: {fails}/{len(tasks)} tx failed")
    p1_time = timed(phase1)
    log(f"  Created {args.users} users in {p1_time}s")

    # Phase 2: Create posts (parallel)
    total_posts = args.users * args.posts
    log(f"Phase 2: Creating {total_posts} posts ({args.posts} per user)...")
    tasks = [("create_post",
              (f"user_{i}", f"Post {j} by user {i}",
               f"Content of post {j} by user_{i}. "
               f"Exercises database storage and retrieval."))
             for i in range(1, args.users + 1)
             for j in range(1, args.posts + 1)]
    def phase2():
        fails = chr_tx_parallel(args.chr, args.node_url, brid, tasks, workers)
        if fails: log(f"  WARNING: {fails}/{len(tasks)} tx failed")
    p2_time = timed(phase2)
    log(f"  Created {total_posts} posts in {p2_time}s")

    # Phase 3: Update bios (parallel)
    log(f"Phase 3: Updating {args.users} user bios...")
    tasks = [("update_bio", (f"user_{i}", f"Updated bio #{i} — now with more detail"))
             for i in range(1, args.users + 1)]
    def phase3():
        fails = chr_tx_parallel(args.chr, args.node_url, brid, tasks, workers)
        if fails: log(f"  WARNING: {fails}/{len(tasks)} tx failed")
    p3_time = timed(phase3)
    log(f"  Updated {args.users} bios in {p3_time}s")

    # Phase 4: Queries via REST API (parallel — saturates the query path)
    log("Phase 4: Running queries...")
    QUERY_ROUNDS = 10

    def build_queries():
        qs = []
        for _ in range(QUERY_ROUNDS):
            qs.append({"type": "count_users"})
            qs.append({"type": "count_posts"})
            for i in range(1, args.users + 1):
                qs.append({"type": "get_user", "name": f"user_{i}"})
            for page in range(5):
                qs.append({"type": "list_users", "page_size": 5, "skip": page * 5})
            for i in range(1, args.users + 1):
                qs.append({"type": "get_posts_by_user",
                           "author_name": f"user_{i}", "max_count": args.posts})
            for term in ["Post 1", "Post 5", "content", "blockchain", "user_1"]:
                qs.append({"type": "search_posts", "term": term})
            for i in range(1, args.users + 1):
                qs.append({"type": "get_user_post_count", "author_name": f"user_{i}"})
        return qs

    queries = build_queries()
    query_count = len(queries)

    def phase4():
        with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
            list(pool.map(lambda q: rest_query(args.node_url, q), queries))

    p4_time = timed(phase4)
    log(f"  Ran {query_count} queries in {p4_time}s")

    total_time = round(time.monotonic() - total_start, 3)
    log(f"Workload complete in {total_time}s")

    results = {
        "total_time_s": total_time,
        "num_users": args.users,
        "posts_per_user": args.posts,
        "total_posts": total_posts,
        "total_queries": query_count,
        "phases": {
            "create_users": {"count": args.users, "time_s": p1_time},
            "create_posts": {"count": total_posts, "time_s": p2_time},
            "update_bios":  {"count": args.users, "time_s": p3_time},
            "queries":      {"count": query_count, "time_s": p4_time},
        },
    }
    results_path.write_text(json.dumps(results, indent=2))
    log(f"Results written to {results_path}")


if __name__ == "__main__":
    main()
