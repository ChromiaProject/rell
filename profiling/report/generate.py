#!/usr/bin/env python3
"""
Rell Stack Profiling Report Generator

Parses async-profiler collapsed stacks + workload results + Postgres stats
and generates a standalone HTML report with:
  - Component time breakdown (Rell / Postchain / PostgreSQL / JVM)
  - Embedded interactive flame graph
  - Hot method table
  - Database statistics
  - Workload throughput summary

Usage: python3 generate.py <run-directory>
"""

import json
import math
import os
import re
import sys
from collections import defaultdict
from pathlib import Path

# ── Component classification ─────────────────────────────────────────────

# Rules are checked in order. First match wins.
# - "substrings" match anywhere in the frame (for package prefixes)
# - "class_prefixes" match only at a class-name boundary — i.e. right after
#   a '.', '/', '$' or at the start of the frame. This prevents false
#   positives like "JVM_Sleep" matching "M_" as a substring.
COMPONENT_RULES = [
    # component, substring_prefixes,                       class_prefixes
    ("PostgreSQL", ["org.postgresql.", "java.sql.", "javax.sql.",
                    "com.zaxxer.hikari.",
                    "net.postchain.base.data.",
                    "net.postchain.common.data."],                         []),
    ("Rell",       ["net.postchain.rell.", "lib.rell."],
                   ["Rt_", "R_", "C_", "L_", "M_", "S_"]),
    ("Postchain",  ["net.postchain.", "com.chromia."],                    []),
]

COMPONENT_COLORS = {
    "Rell":       "#1D4ED8",  # deep indigo
    "Postchain":  "#166534",  # forest
    "PostgreSQL": "#B45309",  # amber
    "JVM":        "#44403C",  # charcoal
    "Idle":       "#A8A9AE",  # neutral grey — parked threads
}

# Components hidden on first paint. The user re-enables by clicking the
# row or swatch. These dominate idle single-node captures and would
# otherwise drown out the signal we actually care about.
DEFAULT_DISABLED_COMPONENTS = frozenset({"JVM", "Idle"})

# Frames that indicate a thread is genuinely parked/waiting (not doing
# any application work). A stack is "Idle" only if it has NO app frames
# AND terminates in one of these.
IDLE_FRAME_MARKERS = (
    "__psynch_cvwait", "__ulock_wait", "__psynch_mutexwait",
    "_pthread_cond_wait",
    "nanosleep", "epoll_wait", "kevent", "kqueue_wait",
    "LockSupport.park", "Unsafe.park", "UnsafePark",
    "Monitor::wait", "PlatformMonitor::wait", "PlatformEvent::park",
    "PlatformParker::park", "os::PlatformEvent::park",
    "JVM_Sleep", "Thread.sleep",
    "mach_msg2_trap",
)


def is_idle_frame(frame: str) -> bool:
    for marker in IDLE_FRAME_MARKERS:
        if marker in frame:
            return True
    return False


# Thread categorisation. Async-profiler's collapsed format doesn't carry
# a thread-name token, so we classify each stack by signature frames:
# the characteristic JVM internals that appear only on specific kinds
# of threads (JIT compilers emit `CompileBroker::`, G1 GC emits `G1*`,
# VM internals emit known patterns, etc.).
#
# Each entry: (category, display_label, list_of_frame_substrings).
# Order matters — first match wins.
THREAD_CATEGORIES = [
    ("gc",        "GC",           ["G1CollectedHeap::", "G1ConcurrentMark",
                                   "G1PrimaryConcurrent", "G1ServiceThread",
                                   "G1ConcurrentRefine", "G1YoungCollector",
                                   "ConcurrentGCThread::", "ConcurrentMarkThread",
                                   "ParallelGCTask", "ParallelCleanup",
                                   "YoungGCTask", "Monitor deflation thread",
                                   "VMThread::", "VM_Operation",
                                   "VM_G1", "VM_CGC"]),
    ("jit",       "JIT compiler", ["CompileBroker::", "C2Compiler::",
                                   "C1Compiler::", "Compile::Compile",
                                   "Compilation::", "PhaseIdealLoop::",
                                   "LIR_Assembler::", "Sweeper::",
                                   "Matcher::match", "PhaseChaitin",
                                   "PhaseCCP", "PhaseIterGVN"]),
    ("vm",        "VM internals", ["ReferenceHandler.run",
                                   "Finalizer.run", "ServiceThread::",
                                   "Common-Cleaner", "NotificationThread",
                                   "MonitorDeflationThread"]),
    ("io",        "Net / IO",     ["org.eclipse.jetty.", "io.netty.",
                                   "okhttp3.", "sun.nio.ch.EPoll",
                                   "sun.nio.ch.KQueue",
                                   "ForkJoinPool.commonPool"]),
    ("db-pool",   "DB pool",      ["com.zaxxer.hikari."]),
    ("postchain", "Postchain",    ["net.postchain.ebft.",
                                   "net.postchain.core.framework.",
                                   "RevoltTracker", "BaseBlockchainProcess",
                                   "ValidatorSyncManager"]),
]

# Categories hidden on first paint (pure infrastructure noise).
DEFAULT_DISABLED_THREADS = frozenset({"gc", "jit", "vm"})


def classify_thread(stack: str) -> str:
    """Classify which kind of thread produced this stack by looking for
    signature frames anywhere in it. Falls back to 'app' when no
    infrastructure markers are present — that's where Rell execution
    and PostgreSQL client work actually live."""
    for cat, _, markers in THREAD_CATEGORIES:
        for m in markers:
            if m in stack:
                return cat
    return "app"


def thread_category_label(cat: str) -> str:
    for c, label, _ in THREAD_CATEGORIES:
        if c == cat:
            return label
    return "Application"


def _build_class_regex(prefixes: list[str]):
    if not prefixes:
        return None
    escaped = "|".join(re.escape(p) for p in prefixes)
    return re.compile(rf"(?:^|[./$])(?:{escaped})")


_CLASS_REGEXES = {
    comp: _build_class_regex(class_prefixes)
    for comp, _, class_prefixes in COMPONENT_RULES
}


def classify_frame(frame: str) -> str:
    # async-profiler's collapsed format uses '/' as package separator
    # (JVM bytecode style: 'net/postchain/...'). Normalize to dots so
    # our rules can use the familiar 'net.postchain.' form.
    normalized = frame.replace("/", ".")
    for component, substrings, _ in COMPONENT_RULES:
        for s in substrings:
            if s in normalized:
                return component
        regex = _CLASS_REGEXES.get(component)
        if regex and regex.search(normalized):
            return component
    return "JVM"

def classify_stack(stack: str) -> str:
    """Classify by scanning all frames for component membership.
    Priority: PostgreSQL > Rell > Postchain > Idle > JVM.
    Idle: no application frames AND leaf is a park/wait primitive.
    JVM: no application frames and leaf isn't idle (GC/JIT/classloading)."""
    frames = stack.split(";")
    seen = set()
    for frame in frames:
        seen.add(classify_frame(frame))
    if "PostgreSQL" in seen:
        return "PostgreSQL"
    if "Rell" in seen:
        return "Rell"
    if "Postchain" in seen:
        return "Postchain"
    # No application frames. Distinguish parked threads from actual
    # JVM-internal work (compilation, GC, class loading, ...).
    if frames and is_idle_frame(frames[-1]):
        return "Idle"
    return "JVM"


# ── Parsing ──────────────────────────────────────────────────────────────

_THREAD_TOKEN_RE = re.compile(r"^\[([^\]]+?)(?:\s+tid=\d+)?\]$")


def _extract_thread(first_frame: str) -> tuple[str, bool]:
    """Detect async-profiler's thread marker '[name tid=N]' at the top
    of a collapsed stack (only present when profiling with -t).
    Returns (thread_name_or_empty, was_a_thread_token)."""
    m = _THREAD_TOKEN_RE.match(first_frame)
    if m:
        return m.group(1), True
    return "", False


def parse_collapsed(path: str) -> list[tuple[str, str, int]]:
    """Parse async-profiler collapsed stacks.

    With `-t`, each line is:
      [thread-name tid=N];frame1;frame2;... count
    Without `-t`, it's just:
      frame1;frame2;... count

    Returns a list of (thread_name, stack_no_thread, count) tuples,
    where thread_name is '' if the profile wasn't captured with -t.
    """
    stacks = []
    if not os.path.exists(path):
        return stacks
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.rsplit(" ", 1)
            if len(parts) != 2:
                continue
            try:
                count = int(parts[1])
            except ValueError:
                continue
            frames = parts[0].split(";")
            thread, is_thread = _extract_thread(frames[0]) if frames else ("", False)
            if is_thread:
                frames = frames[1:]
            stacks.append((thread, ";".join(frames), count))
    return stacks


def _drop_thread(stacks: list[tuple[str, str, int]]) -> list[tuple[str, int]]:
    """Convert (thread, stack, count) -> (stack, count)."""
    return [(st, cnt) for _, st, cnt in stacks]


def compute_thread_summary(stacks) -> list[dict]:
    """Per-thread-category sample totals, for the report's filter panel.
    Classification uses signature frames within each stack."""
    if stacks and len(stacks[0]) == 3:
        stacks = _drop_thread(stacks)
    cat_totals: dict[str, int] = defaultdict(int)
    for stack, count in stacks:
        cat_totals[classify_thread(stack)] += count
    total = sum(cat_totals.values()) or 1
    ordered = [c for c, _, _ in THREAD_CATEGORIES] + ["app"]
    return [
        {
            "cat": cat,
            "label": thread_category_label(cat),
            "samples": cat_totals.get(cat, 0),
            "pct": round(100.0 * cat_totals.get(cat, 0) / total, 1),
        }
        for cat in ordered
        if cat_totals.get(cat, 0) > 0
    ]


def compute_breakdown(stacks) -> dict[str, int]:
    """Compute sample counts per component. Accepts either the new
    (thread, stack, count) tuples or the old (stack, count) tuples."""
    if stacks and len(stacks[0]) == 3:
        stacks = _drop_thread(stacks)
    breakdown = defaultdict(int)
    for stack, count in stacks:
        breakdown[classify_stack(stack)] += count
    return dict(breakdown)


def compute_component_hotspots(stacks,
                               top_n: int = 3) -> dict[str, list[tuple[str, int]]]:
    """For each component, the top N frames by inclusive samples.
    Per-stack dedup so recursion doesn't inflate."""
    if stacks and len(stacks[0]) == 3:
        stacks = _drop_thread(stacks)
    per_comp: dict[str, dict[str, int]] = {
        "Rell": defaultdict(int),
        "Postchain": defaultdict(int),
        "PostgreSQL": defaultdict(int),
        "JVM": defaultdict(int),
        "Idle": defaultdict(int),
    }
    for stack, count in stacks:
        seen = set()
        for frame in stack.split(";"):
            if frame in seen:
                continue
            seen.add(frame)
            # Route idle-wait frames to the "Idle" bucket so you can see
            # *which* wait primitives dominate (cvwait, park, ...)
            if is_idle_frame(frame):
                per_comp["Idle"][frame] += count
            else:
                per_comp[classify_frame(frame)][frame] += count
    return {
        comp: sorted(methods.items(), key=lambda x: -x[1])[:top_n]
        for comp, methods in per_comp.items()
    }


def compute_hotspots(stacks, top_n: int = 30) -> list[dict]:
    """Inclusive (Samples) and self (Own Samples) counts per method,
    sorted by inclusive. Recursive frames are deduplicated per stack."""
    if stacks and len(stacks[0]) == 3:
        stacks = _drop_thread(stacks)
    inclusive = defaultdict(int)
    own = defaultdict(int)
    grand_total = 0
    for stack, count in stacks:
        grand_total += count
        frames = stack.split(";")
        if not frames:
            continue
        for fr in set(frames):
            inclusive[fr] += count
        own[frames[-1]] += count
    grand_total = grand_total or 1
    sorted_methods = sorted(inclusive.items(), key=lambda x: -x[1])[:top_n]
    return [
        {
            "method": m,
            "samples": s,
            "own_samples": own.get(m, 0),
            "pct": round(100.0 * s / grand_total, 2),
            "own_pct": round(100.0 * own.get(m, 0) / grand_total, 2),
            "component": classify_frame(m),
        }
        for m, s in sorted_methods
    ]


def compute_hotspots_filterable(stacks, max_methods: int = 300) -> list[dict]:
    """Like compute_hotspots, but per-method samples are split by
    thread-category so the client can re-rank as filters toggle.
    Returns entries shaped for the JS re-render loop:
      {m: method, c: component, incl: {t: n}, own: {t: n}}
    """
    if stacks and len(stacks[0]) == 3:
        stacks = _drop_thread(stacks)

    inclusive: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    own: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    totals: dict[str, int] = defaultdict(int)

    for stack, count in stacks:
        t_cat = classify_thread(stack)
        frames = stack.split(";")
        if not frames:
            continue
        for fr in set(frames):
            inclusive[fr][t_cat] += count
            totals[fr] += count
        own[frames[-1]][t_cat] += count

    # Rank by total inclusive and keep the top `max_methods` so the JS
    # can filter within a reasonably bounded set.
    ranked = sorted(totals.items(), key=lambda x: -x[1])[:max_methods]
    return [
        {
            "m": m,
            "c": classify_frame(m),
            "incl": dict(inclusive[m]),
            "own":  dict(own[m]),
        }
        for m, _total in ranked
    ]


# ── SVG chart generation ─────────────────────────────────────────────────

def svg_donut(breakdown: dict[str, int], size: int = 320,
              disabled: frozenset[str] = frozenset()) -> str:
    """Editorial donut: heavy weighted arcs, hairline inner stroke,
    percentage glyphs rendered along each segment's mid-angle."""
    total = sum(v for c, v in breakdown.items() if c not in disabled) or 1
    cx, cy = size / 2, size / 2
    r = size / 2 - 32
    r_inner = r * 0.62

    parts = [f'<svg id="donut" width="{size}" height="{size}" viewBox="0 0 {size} {size}" '
             'xmlns="http://www.w3.org/2000/svg" aria-labelledby="donut-title">'
             '<title id="donut-title">Component breakdown donut chart</title>'
             '<g id="donut-arcs">']

    angle = -90
    labels = []
    def _arc_d(start_deg: float, sweep_deg: float) -> str:
        sr, er = math.radians(start_deg), math.radians(start_deg + sweep_deg)
        x1o, y1o = cx + r * math.cos(sr), cy + r * math.sin(sr)
        x2o, y2o = cx + r * math.cos(er), cy + r * math.sin(er)
        x1i, y1i = cx + r_inner * math.cos(er), cy + r_inner * math.sin(er)
        x2i, y2i = cx + r_inner * math.cos(sr), cy + r_inner * math.sin(sr)
        large = 1 if sweep_deg > 180 else 0
        return (f"M {x1o:.2f} {y1o:.2f} "
                f"A {r:.2f} {r:.2f} 0 {large} 1 {x2o:.2f} {y2o:.2f} "
                f"L {x1i:.2f} {y1i:.2f} "
                f"A {r_inner:.2f} {r_inner:.2f} 0 {large} 0 {x2i:.2f} {y2i:.2f} Z")

    for component in ["Rell", "Postchain", "PostgreSQL", "JVM", "Idle"]:
        if component in disabled:
            continue
        count = breakdown.get(component, 0)
        if count == 0:
            continue
        pct = count / total
        sweep = pct * 360
        color = COMPONENT_COLORS[component]

        # SVG arcs degenerate at sweep == 360 (start == end). Split in two.
        sweeps = [180.0, 180.0] if sweep >= 359.99 else [sweep]
        seg_start = angle
        for seg in sweeps:
            parts.append(
                f'  <path d="{_arc_d(seg_start, seg)}" fill="{color}">'
                f'<title>{component}: {pct*100:.1f}%</title></path>'
            )
            seg_start += seg

        if pct >= 0.05:
            mid_rad = math.radians(angle + sweep / 2)
            r_mid = (r + r_inner) / 2
            lx = cx + r_mid * math.cos(mid_rad)
            ly = cy + r_mid * math.sin(mid_rad) + 4
            labels.append(
                f'  <text x="{lx:.1f}" y="{ly:.1f}" text-anchor="middle" '
                f'font-family="\'JetBrains Mono\', monospace" font-size="12" '
                f'font-weight="600" fill="#FFF7EC">{pct*100:.0f}%</text>'
            )
        angle += sweep

    parts.extend(labels)
    parts.append('</g>')

    # Inner circle for a crisp well
    parts.append(f'  <circle cx="{cx}" cy="{cy}" r="{r_inner - 1:.1f}" '
                 f'fill="none" stroke="rgba(24,17,12,0.08)" stroke-width="1"/>')

    # Centre: mono label + mono numeric total (system-document style)
    parts.append(
        f'  <text x="{cx}" y="{cy - 6}" text-anchor="middle" '
        f'font-family="\'JetBrains Mono\', monospace" font-size="9.5" '
        f'fill="#6B6B6B" letter-spacing="2.8" font-weight="600">SAMPLES</text>'
    )
    parts.append(
        f'  <text id="donut-total" x="{cx}" y="{cy + 22}" text-anchor="middle" '
        f'font-family="\'JetBrains Mono\', monospace" font-size="24" font-weight="600" '
        f'fill="#0F0F0F" letter-spacing="-0.01em">{total:,}</text>'
    )
    parts.append('</svg>')
    return "\n".join(parts)


def svg_bar_chart(phases: dict, width: int = 640, bar_h: int = 30) -> str:
    """Editorial bar chart: single accent color, hairline baseline,
    monospace measurements, left-aligned labels with ordinals."""
    if not phases:
        return '<p class="empty">No workload data</p>'

    max_time = max(p.get("time_s", 0) for p in phases.values()) or 1
    pad_left = 160
    pad_right = 150
    gap = 10
    h = len(phases) * (bar_h + gap) + 16

    parts = [f'<svg width="100%" viewBox="0 0 {width} {h}" xmlns="http://www.w3.org/2000/svg" '
             'preserveAspectRatio="xMinYMin meet">']

    # Reference gridlines at 0, 25, 50, 75, 100 %
    track_w = width - pad_left - pad_right
    for i in range(5):
        gx = pad_left + (i / 4) * track_w
        parts.append(
            f'  <line x1="{gx:.1f}" y1="18" x2="{gx:.1f}" y2="{h - 6}" '
            f'stroke="rgba(24,17,12,0.08)" stroke-width="1"/>'
        )

    y = 20
    for idx, (name, data) in enumerate(phases.items(), 1):
        t = data.get("time_s", 0)
        count = data.get("count", 0)
        bar_w = max(4, (t / max_time) * track_w)
        label = name.replace("_", " ").title()
        tps = (count / t) if t > 0 else 0

        # Ordinal + label (left column)
        parts.append(
            f'  <text x="24" y="{y + bar_h/2 + 4:.0f}" '
            f'font-family="\'JetBrains Mono\', monospace" font-size="11" '
            f'fill="#7A6E63" font-weight="600">0{idx}</text>'
        )
        parts.append(
            f'  <text x="{pad_left - 14}" y="{y + bar_h/2 + 4:.0f}" text-anchor="end" '
            f'font-family="\'JetBrains Mono\', monospace" font-size="12" '
            f'font-weight="600" fill="#0F0F0F" letter-spacing="-.01em">{label.lower().replace(" ", ".")}</text>'
        )

        # Bar (thick, flat, single accent color)
        parts.append(
            f'  <rect x="{pad_left}" y="{y + 8}" width="{bar_w:.1f}" height="{bar_h - 16}" '
            f'fill="#C1440E"/>'
        )
        # Thin baseline under each bar
        parts.append(
            f'  <line x1="{pad_left}" y1="{y + bar_h - 4}" '
            f'x2="{pad_left + track_w}" y2="{y + bar_h - 4}" '
            f'stroke="rgba(24,17,12,0.15)" stroke-width="1"/>'
        )

        # Right: duration + throughput
        parts.append(
            f'  <text x="{pad_left + track_w + 14}" y="{y + bar_h/2 - 2:.0f}" '
            f'font-family="\'JetBrains Mono\', monospace" font-size="13" '
            f'font-weight="600" fill="#18110C">{t:.2f}s</text>'
        )
        parts.append(
            f'  <text x="{pad_left + track_w + 14}" y="{y + bar_h/2 + 14:.0f}" '
            f'font-family="\'JetBrains Mono\', monospace" font-size="10" '
            f'fill="#7A6E63">{count:,} ops · {tps:.1f}/s</text>'
        )
        y += bar_h + gap

    parts.append('</svg>')
    return "\n".join(parts)


# ── HTML report ──────────────────────────────────────────────────────────

def generate_html(run_dir: str) -> str:
    run = Path(run_dir)

    # Load profiling data — collapsed.txt is dumped by async-profiler
    # from the same recording that produced profile.jfr. With `-t`, each
    # line carries a leading [thread-name tid=N] marker that we parse out.
    stacks = parse_collapsed(str(run / "collapsed.txt"))
    breakdown = compute_breakdown(stacks)
    component_hotspots = compute_component_hotspots(stacks, top_n=3)
    hotspots = compute_hotspots(stacks, top_n=15)
    hotspots_full = compute_hotspots_filterable(stacks, max_methods=300)
    thread_summary = compute_thread_summary(stacks)

    # Compact (component × thread-cat) cross-tab so the client-side
    # filter can recompute totals instantly without shipping every stack.
    matrix: dict[tuple[str, str], int] = defaultdict(int)
    for _thread, stack, count in stacks:
        matrix[(classify_stack(stack), classify_thread(stack))] += count
    grid = [{"c": comp, "t": t_cat, "n": n}
            for (comp, t_cat), n in matrix.items() if n > 0]

    workload = {}
    wl_path = run / "workload-results.json"
    if wl_path.exists():
        workload = json.loads(wl_path.read_text())

    sysinfo = {}
    si_path = run / "system-info.json"
    if si_path.exists():
        sysinfo = json.loads(si_path.read_text())

    pg_tables = []
    pgt_path = run / "pg-table-stats.json"
    if pgt_path.exists():
        try:
            pg_tables = json.loads(pgt_path.read_text()) or []
        except (json.JSONDecodeError, TypeError):
            pass

    pg_indexes = []
    pgi_path = run / "pg-index-stats.json"
    if pgi_path.exists():
        try:
            pg_indexes = json.loads(pgi_path.read_text()) or []
        except (json.JSONDecodeError, TypeError):
            pass

    pg_sizes = []
    pgs_path = run / "pg-sizes.json"
    if pgs_path.exists():
        try:
            pg_sizes = json.loads(pgs_path.read_text()) or []
        except (json.JSONDecodeError, TypeError):
            pass

    flamegraph_html = ""
    fg_path = run / "flamegraph.html"
    if fg_path.exists():
        flamegraph_html = fg_path.read_text()

    total_samples = sum(breakdown.values())
    phases = workload.get("phases", {})

    num_tx   = (workload.get('num_users', 0) * workload.get('posts_per_user', 0)
                + workload.get('num_users', 0) * 2)

    # Parse timestamp for a human-friendly date line
    ts = sysinfo.get("timestamp", "")
    date_display = ts.replace("T", " · ").rstrip("Z") if ts else ""

    colors_json = json.dumps(COMPONENT_COLORS)
    default_disabled_components_json = json.dumps(sorted(DEFAULT_DISABLED_COMPONENTS))
    default_disabled_threads_json = json.dumps(sorted(DEFAULT_DISABLED_THREADS))
    grid_json = json.dumps(grid, separators=(",", ":"))
    thread_summary_json = json.dumps(thread_summary, separators=(",", ":"))
    hotspots_full_json = json.dumps(hotspots_full, separators=(",", ":"))

    return rf"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Postchain Node Profile — {ts or 'Report'}</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Geist:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500;600;700&display=swap" rel="stylesheet">
<style>
{CSS}
</style>
</head>
<body>

<header class="doc-head">
  <div class="doc-head-inner">
    <h1 class="doc-title">Postchain Node Profile</h1>
    <div class="doc-meta">
      <span class="doc-meta-pair"><span class="k">rell</span><span class="v">{_escape(sysinfo.get('versions',{}).get('rell','—'))}</span></span>
      <span class="doc-meta-pair"><span class="k">postchain</span><span class="v">{_escape(sysinfo.get('versions',{}).get('postchain','—'))}</span></span>
    </div>
  </div>
</header>

<main>

{_section_open("01", "System Information", "")}
  <div class="sysinfo-grid">
    <div class="sysinfo-block">
      <h3>Host</h3>
      <dl>
        <dt>Hostname</dt><dd>{_escape(sysinfo.get('hostname','—'))}</dd>
        <dt>OS</dt><dd>{_escape(sysinfo.get('os','—'))}</dd>
        <dt>Arch</dt><dd>{_escape(sysinfo.get('arch','—'))}</dd>
        <dt>CPUs</dt><dd>{sysinfo.get('cpus','—')}</dd>
        <dt>Memory</dt><dd>{sysinfo.get('memory_gib','—')} GiB</dd>
      </dl>
    </div>
    <div class="sysinfo-block">
      <h3>JDK</h3>
      <dl>
        <dt>JAVA_HOME</dt><dd class="mono">{_escape(str(sysinfo.get('java_home','—')))}</dd>
        <dt>Version</dt><dd class="mono">{_escape(str(sysinfo.get('java_version','—')))}</dd>
        <dt>async-profiler</dt><dd class="mono">{_escape(str(sysinfo.get('async_profiler','—')))}</dd>
      </dl>
    </div>
    <div class="sysinfo-block">
      <h3 class="cmd"><span class="cmd-prompt">$</span> chr version</h3>
      <dl>
        {_version_rows(sysinfo.get('versions',{}))}
      </dl>
    </div>
  </div>
{_section_close()}

{_section_open("02", "Workload", "")}
  <div class="metrics">
    {_metric("Wall clock", f"{workload.get('total_time_s', 0):.1f}", "s", "total workload")}
    {_metric("Transactions", f"{num_tx:,}", "", "tx ops + updates")}
    {_metric("Queries", f"{workload.get('total_queries', 0):,}", "", "REST calls")}
    {_metric("Samples", f"{total_samples:,}", "", f"event: {_escape(sysinfo.get('profiler_event','cpu'))}")}
  </div>
{_section_close()}

{_section_open("03", "Filters", "Applies to Component Breakdown and Hot Methods below")}
  <div class="filters-panel">
    <div class="filter-bar" id="component-filter">
      <span class="filter-label">components</span>
      {_component_filter_html(breakdown, DEFAULT_DISABLED_COMPONENTS)}
    </div>
    <div class="filter-bar" id="thread-filter">
      <span class="filter-label">threads</span>
      {_thread_filter_html(thread_summary, DEFAULT_DISABLED_THREADS)}
    </div>
  </div>
{_section_close()}

{_section_open("04", "Component Breakdown", "Click a row to toggle that component — mirrors the component filter above")}
  <div class="breakdown-grid">
    <figure class="donut-figure">
      {svg_donut(breakdown, disabled=DEFAULT_DISABLED_COMPONENTS)}
    </figure>
    <div class="breakdown-table">
      <table class="breakdown">
        <thead>
          <tr><th>Component</th><th class="num">Samples</th><th class="num">Share</th><th></th></tr>
        </thead>
        <tbody>
          {_breakdown_rows(breakdown, total_samples, component_hotspots, disabled=DEFAULT_DISABLED_COMPONENTS)}
        </tbody>
      </table>
    </div>
  </div>
{_section_close()}

{_section_open("04", "Phases", "Durations & throughput per phase")}
  <div class="bars-wrap">{svg_bar_chart(phases)}</div>
{_section_close()}

{_section_open("05", "Hot Methods", "Top 15 by inclusive samples — honours the component and thread filters above")}
  <div class="table-scroll">
    <table class="hotspots">
      <thead><tr>
        <th class="num rank">№</th>
        <th>Method</th>
        <th>Component</th>
        <th class="num">Samples</th>
        <th class="num">Own</th>
        <th class="num">%</th>
        <th></th>
      </tr></thead>
      <tbody id="hotspots-tbody">
        {_hotspot_rows(hotspots)}
      </tbody>
    </table>
  </div>
{_section_close()}

{_section_open("06", "PostgreSQL", "Activity since the workload started — table / index / size")}
  <h3 class="sub">Table activity</h3>
  {_pg_table_html(pg_tables)}
  <h3 class="sub">Index usage</h3>
  {_pg_index_html(pg_indexes)}
  <h3 class="sub">Relation sizes</h3>
  {_pg_sizes_html(pg_sizes)}
{_section_close()}

{_section_open("07", "Flame Graph", "Interactive call-stack visualisation")}
  {_flamegraph_embed(flamegraph_html, run_dir)}
{_section_close()}

</main>

<footer class="colophon">
  <div class="colophon-inner">
    <span>async-profiler + Python, standalone</span>
    <span class="sep">·</span>
    <span><a href="https://gitlab.com/chromaway/rell">chromaway/rell</a></span>
    <span class="colophon-spacer"></span>
    <span class="colophon-meta">{date_display or '—'}</span>
  </div>
</footer>

<script>
(function() {{
  // GRID: per-(component × thread-category) sample counts.
  // Toggling components *or* threads recomputes totals from the same
  // source of truth — no double-bookkeeping.
  const GRID = {grid_json};
  // HOTSPOTS: top methods with per-thread-cat inclusive & own counts.
  // The hot-methods table re-ranks over this data whenever filters toggle.
  const HOTSPOTS = {hotspots_full_json};
  const HOTSPOTS_VISIBLE = 15;
  const COLORS = {colors_json};
  const ORDER = ["Rell", "Postchain", "PostgreSQL", "JVM", "Idle"];
  const disabledComp = new Set({default_disabled_components_json});
  const disabledThread = new Set({default_disabled_threads_json});

  const SIZE = 320, CX = SIZE/2, CY = SIZE/2, R = SIZE/2 - 32, R_IN = R * 0.62;
  const SVG_NS = "http://www.w3.org/2000/svg";

  function arcPath(startDeg, sweepDeg) {{
    const start = startDeg * Math.PI / 180;
    const end = (startDeg + sweepDeg) * Math.PI / 180;
    const large = sweepDeg > 180 ? 1 : 0;
    const x1o = CX + R * Math.cos(start), y1o = CY + R * Math.sin(start);
    const x2o = CX + R * Math.cos(end),   y2o = CY + R * Math.sin(end);
    const x1i = CX + R_IN * Math.cos(end),   y1i = CY + R_IN * Math.sin(end);
    const x2i = CX + R_IN * Math.cos(start), y2i = CY + R_IN * Math.sin(start);
    return `M ${{x1o.toFixed(2)}} ${{y1o.toFixed(2)}} `
         + `A ${{R.toFixed(2)}} ${{R.toFixed(2)}} 0 ${{large}} 1 ${{x2o.toFixed(2)}} ${{y2o.toFixed(2)}} `
         + `L ${{x1i.toFixed(2)}} ${{y1i.toFixed(2)}} `
         + `A ${{R_IN.toFixed(2)}} ${{R_IN.toFixed(2)}} 0 ${{large}} 0 ${{x2i.toFixed(2)}} ${{y2i.toFixed(2)}} Z`;
  }}

  // Samples for a component, filtered by the current thread-category set.
  function componentSamples(comp) {{
    let s = 0;
    for (const cell of GRID) {{
      if (cell.c !== comp) continue;
      if (disabledThread.has(cell.t)) continue;
      s += cell.n;
    }}
    return s;
  }}

  function activeTotal() {{
    return ORDER.filter(c => !disabledComp.has(c))
                .reduce((s, c) => s + componentSamples(c), 0);
  }}

  function renderDonut() {{
    const arcs = document.getElementById("donut-arcs");
    if (!arcs) return;
    arcs.textContent = "";
    const total = activeTotal() || 1;
    let angle = -90;
    const labels = [];
    for (const comp of ORDER) {{
      if (disabledComp.has(comp)) continue;
      const count = componentSamples(comp);
      if (!count) continue;
      const pct = count / total;
      const sweep = pct * 360;
      const sweeps = sweep >= 359.99 ? [180, 180] : [sweep];
      let segStart = angle;
      for (const seg of sweeps) {{
        const path = document.createElementNS(SVG_NS, "path");
        path.setAttribute("d", arcPath(segStart, seg));
        path.setAttribute("fill", COLORS[comp]);
        const title = document.createElementNS(SVG_NS, "title");
        title.textContent = `${{comp}}: ${{(pct * 100).toFixed(1)}}%`;
        path.appendChild(title);
        arcs.appendChild(path);
        segStart += seg;
      }}
      if (pct >= 0.05) {{
        const midRad = (angle + sweep / 2) * Math.PI / 180;
        const rMid = (R + R_IN) / 2;
        const lx = CX + rMid * Math.cos(midRad);
        const ly = CY + rMid * Math.sin(midRad) + 4;
        const label = document.createElementNS(SVG_NS, "text");
        label.setAttribute("x", lx.toFixed(1));
        label.setAttribute("y", ly.toFixed(1));
        label.setAttribute("text-anchor", "middle");
        label.setAttribute("font-family", "'JetBrains Mono', monospace");
        label.setAttribute("font-size", "12");
        label.setAttribute("font-weight", "600");
        label.setAttribute("fill", "#FFF7EC");
        label.textContent = `${{Math.round(pct * 100)}}%`;
        labels.push(label);
      }}
      angle += sweep;
    }}
    labels.forEach(l => arcs.appendChild(l));
    const totalEl = document.getElementById("donut-total");
    if (totalEl) totalEl.textContent = total.toLocaleString();
  }}

  function updateRows() {{
    const total = activeTotal() || 1;
    document.querySelectorAll("tr.comp-row").forEach(row => {{
      const comp = row.dataset.component;
      const samples = componentSamples(comp);
      const off = disabledComp.has(comp);
      row.classList.toggle("disabled", off);
      row.querySelector("td.num").textContent = samples.toLocaleString();
      const pct = off ? 0 : (100 * samples / total);
      const share = row.querySelector(".comp-share");
      if (share) share.textContent = off ? "—" : `${{pct.toFixed(1)}}%`;
      const bar = row.querySelector(".pct-bar");
      if (bar) bar.style.width = pct.toFixed(1) + "%";
    }});
    document.querySelectorAll("tr.comp-hotspot").forEach(row => {{
      const parent = row.dataset.parent;
      const samples = parseInt(row.dataset.samples, 10) || 0;
      const off = disabledComp.has(parent);
      row.classList.toggle("disabled", off);
      const pct = off ? 0 : (100 * samples / total);
      const share = row.querySelector(".comp-hotspot-share");
      if (share) share.textContent = off ? "—" : `${{pct.toFixed(1)}}%`;
    }});
  }}

  function shortMethod(m) {{
    const s = m.replace(/\(\)/g, "").replace(/\//g, ".");
    const parts = s.split(".");
    for (let i = 0; i < parts.length; i++) {{
      const p = parts[i];
      if (p && (/^[A-Z<$]/.test(p))) return parts.slice(i).join(".");
    }}
    return parts.slice(-2).join(".") || s;
  }}

  function escapeHtml(s) {{
    return s.replace(/&/g, "&amp;").replace(/</g, "&lt;")
            .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }}

  function sumCounts(bag) {{
    let s = 0;
    for (const [t, n] of Object.entries(bag)) {{
      if (disabledThread.has(t)) continue;
      s += n;
    }}
    return s;
  }}

  function renderHotspots() {{
    const tbody = document.getElementById("hotspots-tbody");
    if (!tbody) return;
    const total = activeTotal() || 1;

    // Filter by component, re-rank by inclusive under current thread filter,
    // then take the top N.
    const ranked = [];
    for (const h of HOTSPOTS) {{
      if (disabledComp.has(h.c)) continue;
      const incl = sumCounts(h.incl);
      if (incl === 0) continue;
      const own = sumCounts(h.own);
      ranked.push({{m: h.m, c: h.c, incl: incl, own: own}});
    }}
    ranked.sort((a, b) => b.incl - a.incl);
    const top = ranked.slice(0, HOTSPOTS_VISIBLE);

    tbody.textContent = "";
    top.forEach((h, i) => {{
      const color = COLORS[h.c] || "#888";
      const method = h.m.replace(/\//g, ".");
      const display = method.length <= 90 ? method : method.slice(0, 87) + "...";
      const pct = 100 * h.incl / total;
      const tr = document.createElement("tr");
      tr.innerHTML =
        `<td class="num rank">${{String(i+1).padStart(2,"0")}}</td>` +
        `<td class="method" title="${{escapeHtml(method)}}">${{escapeHtml(display)}}</td>` +
        `<td><span class="chip" style="--c:${{color}}">${{h.c}}</span></td>` +
        `<td class="num">${{h.incl.toLocaleString()}}</td>` +
        `<td class="num">${{h.own.toLocaleString()}}</td>` +
        `<td class="num strong">${{pct.toFixed(1)}}%</td>` +
        `<td class="bar-cell"><div class="pct-bar" style="width:${{pct.toFixed(1)}}%;background:${{color}}"></div></td>`;
      tbody.appendChild(tr);
    }});
    if (top.length === 0) {{
      const tr = document.createElement("tr");
      tr.innerHTML = '<td colspan="7" class="empty">No methods match the current filters.</td>';
      tbody.appendChild(tr);
    }}
  }}

  function rerender() {{
    renderDonut();
    updateRows();
    renderHotspots();
  }}

  // Keep component checkboxes and breakdown rows in visual sync.
  function syncComponentUi() {{
    document.querySelectorAll("#component-filter input[type=checkbox]").forEach(cb => {{
      cb.checked = !disabledComp.has(cb.dataset.component);
    }});
  }}

  // Toggle a component (used by row clicks and checkbox clicks)
  function toggleComponent(comp) {{
    if (disabledComp.has(comp)) disabledComp.delete(comp);
    else disabledComp.add(comp);
    syncComponentUi();
    rerender();
  }}

  // Component row click → toggle component visibility
  document.querySelectorAll("tr.comp-row").forEach(row => {{
    row.addEventListener("click", () => toggleComponent(row.dataset.component));
  }});

  // Component filter checkbox → toggle visibility
  document.querySelectorAll("#component-filter input[type=checkbox]").forEach(cb => {{
    cb.addEventListener("change", () => toggleComponent(cb.dataset.component));
  }});

  // Thread-category checkbox → toggle inclusion of that category
  document.querySelectorAll("#thread-filter input[type=checkbox]").forEach(cb => {{
    cb.addEventListener("change", () => {{
      const cat = cb.dataset.threadCat;
      if (cb.checked) disabledThread.delete(cat);
      else disabledThread.add(cat);
      rerender();
    }});
  }});

  // Apply default state on load (server-rendered the no-thread-filter view)
  rerender();
}})();
</script>

</body>
</html>"""


# ── HTML fragments ───────────────────────────────────────────────────────

def _section_open(num: str, title: str, strapline: str = "") -> str:
    # num is kept in the signature for call-site simplicity but is no
    # longer rendered — paragraph numbers read as editorial.
    del num
    return (
        '<section class="section">'
        '<header class="section-head">'
        f'<h2 class="section-title">{title}</h2>'
        + (f'<p class="section-strap">{strapline}</p>' if strapline else '')
        + '</header>'
        '<div class="section-body">'
    )

def _section_close() -> str:
    return '</div></section>'


def _metric(label: str, value: str, unit: str, sub: str) -> str:
    unit_html = f'<span class="metric-unit">{unit}</span>' if unit else ""
    return (
        '<div class="metric">'
        f'<div class="metric-label">{label}</div>'
        f'<div class="metric-value">{value}{unit_html}</div>'
        f'<div class="metric-sub">{sub}</div>'
        '</div>'
    )


def _breakdown_rows(breakdown: dict, total: int,
                    component_hotspots: dict[str, list[tuple[str, int]]] | None = None,
                    disabled: frozenset[str] = frozenset()) -> str:
    del total  # recomputed below from enabled components only
    active_total = sum(v for c, v in breakdown.items() if c not in disabled) or 1
    rows = []
    for comp in ["Rell", "Postchain", "PostgreSQL", "JVM", "Idle"]:
        s = breakdown.get(comp, 0)
        off = comp in disabled
        pct = 0.0 if off else 100.0 * s / active_total
        color = COMPONENT_COLORS[comp]
        bar = f'<div class="pct-bar" style="width:{pct:.1f}%;background:{color}"></div>'
        share_text = "—" if off else f"{pct:.1f}%"
        row_cls = "comp-row disabled" if off else "comp-row"
        rows.append(
            f'<tr class="{row_cls}" data-component="{comp}" data-samples="{s}">'
            f'<td class="comp-name"><span class="swatch" style="background:{color}"></span>{comp}</td>'
            f'<td class="num">{s:,}</td>'
            f'<td class="num strong comp-share">{share_text}</td>'
            f'<td class="bar-cell">{bar}</td>'
            '</tr>'
        )
        if component_hotspots and s > 0:
            top = component_hotspots.get(comp, [])
            for method, count in top:
                m_pct = 0.0 if off else 100.0 * count / active_total
                m_share = "—" if off else f"{m_pct:.1f}%"
                row_cls = "comp-hotspot disabled" if off else "comp-hotspot"
                short = _short_method(method)
                rows.append(
                    f'<tr class="{row_cls}" data-parent="{comp}" data-samples="{count}">'
                    f'<td class="comp-hotspot-name">'
                    f'<span class="comp-hotspot-rule" style="background:{color}"></span>'
                    f'<span class="comp-hotspot-method" title="{_escape(method)}">{_escape(short)}</span>'
                    f'</td>'
                    f'<td class="num">{count:,}</td>'
                    f'<td class="num comp-hotspot-share">{m_share}</td>'
                    f'<td></td>'
                    '</tr>'
                )
    return "\n".join(rows)


def _thread_filter_html(summary: list[dict], disabled: frozenset[str]) -> str:
    if not summary:
        return '<span class="empty">no thread data</span>'
    parts = []
    for row in summary:
        cat = row["cat"]
        checked = "" if cat in disabled else "checked"
        parts.append(
            '<label class="filter-chip">'
            f'<input type="checkbox" data-thread-cat="{cat}" {checked}>'
            f'<span class="filter-chip-label">{_escape(row["label"])}</span>'
            f'<span class="filter-chip-count">{row["samples"]:,}</span>'
            '</label>'
        )
    return "\n".join(parts)


def _component_filter_html(breakdown: dict, disabled: frozenset[str]) -> str:
    parts = []
    for comp in ["Rell", "Postchain", "PostgreSQL", "JVM", "Idle"]:
        samples = breakdown.get(comp, 0)
        if samples == 0:
            continue
        color = COMPONENT_COLORS.get(comp, "#888")
        checked = "" if comp in disabled else "checked"
        parts.append(
            '<label class="filter-chip">'
            f'<input type="checkbox" data-component="{comp}" {checked}>'
            f'<span class="filter-swatch" style="background:{color}"></span>'
            f'<span class="filter-chip-label">{comp}</span>'
            f'<span class="filter-chip-count">{samples:,}</span>'
            '</label>'
        )
    return "\n".join(parts)


def _short_method(m: str) -> str:
    """Shorten a fully-qualified method name for compact display.
    Drops the package prefix (lowercase dot-segments) but keeps the
    class, any nested types, and the method name."""
    m = m.replace("()", "").replace("/", ".")
    parts = m.split(".")
    # Find the first segment that looks like a class (starts uppercase
    # or with a JVM-special char like '<'). Anything before it is package.
    for i, p in enumerate(parts):
        if p and (p[0].isupper() or p[0] in "<$"):
            return ".".join(parts[i:])
    return ".".join(parts[-2:]) if len(parts) >= 2 else m



def _hotspot_rows(hotspots: list[dict]) -> str:
    rows = []
    for i, h in enumerate(hotspots, 1):
        color = COMPONENT_COLORS.get(h["component"], "#888")
        method = h["method"].replace("/", ".")
        display = method if len(method) <= 90 else method[:87] + "..."
        bar = f'<div class="pct-bar" style="width:{h["pct"]:.1f}%;background:{color}"></div>'
        rows.append(
            '<tr>'
            f'<td class="num rank">{i:02d}</td>'
            f'<td class="method" title="{_escape(method)}">{_escape(display)}</td>'
            f'<td><span class="chip" style="--c:{color}">{h["component"]}</span></td>'
            f'<td class="num">{h["samples"]:,}</td>'
            f'<td class="num">{h["own_samples"]:,}</td>'
            f'<td class="num strong">{h["pct"]:.1f}%</td>'
            f'<td class="bar-cell">{bar}</td>'
            '</tr>'
        )
    return "\n".join(rows)

def _pg_table_html(tables: list, limit: int = 10) -> str:
    if not tables:
        return '<p class="empty">No table statistics available</p>'
    tables = sorted(tables, key=lambda t: _int(t.get("seq_tup_read")) + _int(t.get("idx_tup_fetch")), reverse=True)[:limit]
    rows = []
    for t in tables:
        rows.append(
            f'<tr><td class="mono">{_escape(t.get("relname",""))}</td>'
            f'<td class="num">{_int(t.get("seq_scan")):,}</td>'
            f'<td class="num">{_int(t.get("seq_tup_read")):,}</td>'
            f'<td class="num">{_int(t.get("idx_scan")):,}</td>'
            f'<td class="num">{_int(t.get("idx_tup_fetch")):,}</td>'
            f'<td class="num">{_int(t.get("n_tup_ins")):,}</td>'
            f'<td class="num">{_int(t.get("n_tup_upd")):,}</td>'
            f'<td class="num">{_int(t.get("n_live_tup")):,}</td>'
            f'</tr>'
        )
    return (
        '<div class="table-scroll"><table>'
        '<thead><tr><th>Table</th><th>Seq Scans</th><th>Seq Rows</th>'
        '<th>Idx Scans</th><th>Idx Fetches</th>'
        '<th>Inserts</th><th>Updates</th><th>Live Rows</th></tr></thead>'
        '<tbody>' + "\n".join(rows) + '</tbody></table></div>'
    )

def _pg_index_html(indexes: list) -> str:
    if not indexes:
        return '<p class="empty">No index statistics available</p>'
    rows = []
    for idx in indexes:
        size = idx.get("index_size_bytes", 0)
        size_str = _human_bytes(size)
        rows.append(
            f'<tr><td class="mono">{_escape(idx.get("indexrelname",""))}</td>'
            f'<td class="num">{_int(idx.get("idx_scan")):,}</td>'
            f'<td class="num">{_int(idx.get("idx_tup_read")):,}</td>'
            f'<td class="num">{_int(idx.get("idx_tup_fetch")):,}</td>'
            f'<td class="num">{size_str}</td>'
            f'</tr>'
        )
    return (
        '<div class="table-scroll"><table>'
        '<thead><tr><th>Index</th><th>Scans</th><th>Rows Read</th>'
        '<th>Rows Fetched</th><th>Size</th></tr></thead>'
        '<tbody>' + "\n".join(rows) + '</tbody></table></div>'
    )

def _pg_sizes_html(sizes: list, limit: int = 10) -> str:
    if not sizes:
        return '<p class="empty">No size data available</p>'
    rows = []
    for s in sizes[:limit]:
        rows.append(
            f'<tr><td class="mono">{_escape(s.get("relname",""))}</td>'
            f'<td class="num">{_human_bytes(s.get("total_bytes",0))}</td>'
            f'<td class="num">{_human_bytes(s.get("table_bytes",0))}</td>'
            f'<td class="num">{_human_bytes(s.get("indexes_bytes",0))}</td>'
            f'</tr>'
        )
    return (
        '<div class="table-scroll"><table>'
        '<thead><tr><th>Relation</th><th>Total</th><th>Table</th><th>Indexes</th></tr></thead>'
        '<tbody>' + "\n".join(rows) + '</tbody></table></div>'
    )

def _flamegraph_embed(fg_html: str, run_dir: str) -> str:
    if not fg_html:
        return ('<p class="empty">No flame graph &mdash; async-profiler did not '
                'attach successfully.</p>')
    return (
        '<div class="flame-wrap">'
        '<div class="flame-hint">'
        '<span>Hover frames for detail, click to zoom, Ctrl+F to search.</span>'
        '<a href="flamegraph.html" target="_blank" class="flame-btn">Open full-screen →</a>'
        '</div>'
        '<iframe src="flamegraph.html" class="flame-iframe" title="Flame graph"></iframe>'
        '</div>'
    )

def _int(v) -> int:
    """Safely coerce a value to int (PG JSON can contain None)."""
    if v is None:
        return 0
    try:
        return int(v)
    except (TypeError, ValueError):
        return 0

def _version_rows(versions: dict) -> str:
    """Render key=value rows for the versions dict from `chr --version`."""
    if not versions:
        return '<dt>Versions</dt><dd class="dim">Not available</dd>'
    # Pretty order, known keys first
    order = ["chr", "rell", "postchain", "eif", "java"]
    labels = {"chr": "chr", "rell": "Rell", "postchain": "Postchain",
              "eif": "EIF", "java": "Java (chr process)"}
    rows = []
    for key in order:
        if key in versions:
            rows.append(f'<dt>{labels[key]}</dt><dd class="mono">{_escape(versions[key])}</dd>')
    for key, val in versions.items():
        if key not in order:
            rows.append(f'<dt>{_escape(key)}</dt><dd class="mono">{_escape(val)}</dd>')
    return "\n".join(rows)


def _escape(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")

def _human_bytes(b) -> str:
    b = _int(b)
    if b == 0:
        return "0 B"
    for unit in ["B", "KB", "MB", "GB"]:
        if abs(b) < 1024:
            return f"{b:.1f} {unit}"
        b /= 1024
    return f"{b:.1f} TB"


# ── CSS ──────────────────────────────────────────────────────────────────

CSS = r"""
/* ============================================================
   "Engineering Report" — flat neutral surfaces, near-black ink,
   one technical accent. No serifs, no shadows, no gradients.
   Monospace carries the document's structural signals; a
   clean sans carries prose. RFC / pprof / compiler-output
   lineage.
   ============================================================ */

:root {
  /* Palette — neutral engineering surfaces */
  --ink:        #0F0F10;   /* near-black, slightly cool */
  --ink-soft:   #2B2B2F;   /* secondary body text */
  --muted:      #6B6D73;   /* labels / metadata */
  --faint:      #A8A9AE;   /* de-emphasised */
  --bg:         #FAFAF7;   /* near-neutral off-white */
  --bg-alt:     #F2F2EE;   /* subtle block fill */
  --surface:    #FFFFFF;   /* pure white cells */
  --rule:       rgba(15,15,16,0.12);
  --rule-hair:  rgba(15,15,16,0.06);
  --accent:     #C1440E;   /* burnt orange — data-highlight */
  --accent-bg:  rgba(193,68,14,0.06);

  /* Type */
  --sans: 'Geist', -apple-system, BlinkMacSystemFont, 'Helvetica Neue', Arial, sans-serif;
  --mono: 'JetBrains Mono', 'SF Mono', 'Cascadia Code', Consolas, ui-monospace, monospace;
}

*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

html { font-size: 15px; scroll-behavior: smooth; }

body {
  font-family: var(--sans);
  color: var(--ink);
  background: var(--bg);
  line-height: 1.55;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  font-feature-settings: "ss01", "cv01";
}

a { color: var(--accent); text-decoration: none; border-bottom: 1px solid transparent;
    transition: border-color .15s ease; }
a:hover { border-bottom-color: var(--accent); }

/* ─── Document head (system-report header) ──────────────── */
.doc-head {
  background: var(--surface);
  border-bottom: 2px solid var(--ink);
}
.doc-head-inner {
  max-width: 1180px;
  margin: 0 auto;
  padding: 1.5rem 2rem 1.2rem;
}

.doc-title {
  font-family: var(--mono);
  font-weight: 600;
  font-size: clamp(1.7rem, 3.4vw, 2.4rem);
  letter-spacing: -0.03em;
  color: var(--ink);
  line-height: 1.05;
  margin-bottom: .7rem;
}

.doc-meta {
  display: flex;
  flex-wrap: wrap;
  gap: .35rem 1.1rem;
  font-family: var(--mono);
  font-size: .74rem;
  color: var(--ink-soft);
}
.doc-meta-pair { display: inline-flex; gap: .4rem; }
.doc-meta-pair .k {
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: .12em;
  font-size: .68rem;
  font-weight: 600;
  line-height: 1.6;
}
.doc-meta-pair .v {
  color: var(--ink);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

/* ─── Main container ───────────────────────────────────── */
main {
  max-width: 1180px;
  margin: 0 auto;
  padding: 2rem 2rem 2.5rem;
}

/* ─── Section primitives ───────────────────────────────── */
.section { margin-bottom: 2.4rem; position: relative; }
.section-head {
  padding: 1rem 0 .8rem;
  margin-bottom: 1.1rem;
  border-top: 1px solid var(--ink);
}
.section-title {
  font-family: var(--mono);
  font-weight: 600;
  font-size: 1.05rem;
  letter-spacing: -0.01em;
  line-height: 1.25;
  color: var(--ink);
  text-transform: lowercase;
}
.section-title::before {
  content: '# ';
  color: var(--faint);
  font-weight: 400;
}
.section-strap {
  margin-top: .25rem;
  font-family: var(--sans);
  font-size: .82rem;
  color: var(--muted);
  max-width: 64ch;
  line-height: 1.5;
}
.section-body h3.sub {
  font-family: var(--mono);
  font-size: .7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: .18em;
  color: var(--muted);
  margin: 1.4rem 0 .5rem;
  padding-bottom: .3rem;
  border-bottom: 1px solid var(--rule-hair);
}
.section-body > h3.sub:first-child { margin-top: 0; }

/* ─── At-a-glance metrics ──────────────────────────────── */
.metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0;
  background: var(--surface);
  border: 1px solid var(--rule);
}
.metric {
  padding: 1rem 1.2rem;
  border-left: 1px solid var(--rule-hair);
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 92px;
}
.metric:first-child { border-left: 0; }
.metric-label {
  font-family: var(--mono);
  font-size: .66rem;
  text-transform: uppercase;
  letter-spacing: .18em;
  color: var(--muted);
  font-weight: 600;
  margin-bottom: .35rem;
}
.metric-value {
  font-family: var(--mono);
  font-size: 1.8rem;
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.02em;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
}
.metric-unit {
  font-family: var(--mono);
  font-size: .8rem;
  font-weight: 500;
  color: var(--muted);
  margin-left: .1rem;
  letter-spacing: 0;
}
.metric-sub {
  margin-top: .3rem;
  font-size: .72rem;
  color: var(--muted);
  font-family: var(--mono);
}

/* ─── System information grid ──────────────────────────── */
.sysinfo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 0;
  background: var(--surface);
  border: 1px solid var(--rule);
}
.sysinfo-block {
  padding: 1rem 1.2rem;
  border-left: 1px solid var(--rule-hair);
}
.sysinfo-block:first-child { border-left: 0; }
.sysinfo-block h3 {
  font-family: var(--mono);
  font-size: .68rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: .18em;
  color: var(--muted);
  margin-bottom: .7rem;
  padding-bottom: .35rem;
  border-bottom: 1px solid var(--rule-hair);
}
.sysinfo-block h3 em {
  color: var(--faint);
  font-style: normal;
  font-weight: 500;
  letter-spacing: .08em;
  text-transform: lowercase;
}
.sysinfo-block h3.cmd {
  text-transform: none;
  letter-spacing: 0;
  color: var(--ink);
  font-weight: 500;
}
.sysinfo-block h3.cmd .cmd-prompt {
  color: var(--accent);
  margin-right: .35rem;
  font-weight: 600;
}

.sysinfo-block dl {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: .3rem .8rem;
  font-size: .8rem;
}
.sysinfo-block dt {
  color: var(--muted);
  font-size: .72rem;
  font-family: var(--mono);
  font-weight: 500;
  letter-spacing: .02em;
}
.sysinfo-block dd {
  color: var(--ink);
  font-family: var(--sans);
  font-size: .82rem;
  word-break: break-all;
}
.mono { font-family: var(--mono); font-size: .76rem; }

/* ─── Filters panel (applies to Breakdown + Hot Methods) ──── */
.filters-panel {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--rule);
  background: var(--surface);
}
.filters-panel .filter-bar {
  border: 0;
  margin-bottom: 0;
  background: transparent;
}
.filters-panel .filter-bar + .filter-bar {
  border-top: 1px solid var(--rule-hair);
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: .5rem;
  margin-bottom: .8rem;
  padding: .6rem .9rem;
  background: var(--surface);
  border: 1px solid var(--rule);
  font-family: var(--mono);
}
.filter-label {
  font-family: var(--mono);
  font-size: .68rem;
  text-transform: uppercase;
  letter-spacing: .18em;
  color: var(--muted);
  font-weight: 600;
  margin-right: .3rem;
}
.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: .4rem;
  padding: .2rem .55rem .2rem .4rem;
  border: 1px solid var(--rule);
  font-size: .72rem;
  color: var(--ink-soft);
  cursor: pointer;
  user-select: none;
  transition: background .12s ease, border-color .12s ease, color .12s ease;
}
.filter-chip:has(input:checked) {
  background: var(--accent-bg);
  border-color: var(--accent);
  color: var(--ink);
}
.filter-chip input {
  accent-color: var(--accent);
  margin: 0;
  width: 12px;
  height: 12px;
  cursor: pointer;
}
.filter-chip-label { font-family: var(--mono); font-weight: 500; }
.filter-chip-count {
  font-family: var(--mono);
  color: var(--muted);
  font-size: .68rem;
  font-variant-numeric: tabular-nums;
}
.filter-swatch {
  display: inline-block;
  width: 10px;
  height: 10px;
  background: var(--muted);
  flex-shrink: 0;
}

/* ─── Breakdown (donut + table) ────────────────────────── */
.breakdown-grid {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 2rem;
  align-items: start;
  background: var(--surface);
  border: 1px solid var(--rule);
  padding: 1.2rem;
}
.donut-figure {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.swatch {
  display: inline-block;
  width: 10px;
  height: 10px;
  background: var(--muted);
}

.bars-wrap {
  max-width: 720px;
  background: var(--surface);
  border: 1px solid var(--rule);
  padding: .9rem 1rem;
}
.bars-wrap svg { display: block; }

/* ─── Tables ───────────────────────────────────────────── */
table { border-collapse: collapse; width: 100%; }

thead th {
  text-align: left;
  font-family: var(--mono);
  font-weight: 600;
  font-size: .68rem;
  text-transform: uppercase;
  letter-spacing: .18em;
  color: var(--muted);
  padding: .5rem .75rem;
  border-bottom: 1px solid var(--ink);
  background: transparent;
}
thead th.num { text-align: right; }

td {
  padding: .55rem .75rem;
  border-bottom: 1px solid var(--rule-hair);
  vertical-align: middle;
  font-size: .85rem;
}
tbody tr:hover { background: rgba(193, 68, 14, 0.04); }

.num {
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-family: var(--mono);
  font-size: .82rem;
}
.num.strong { font-weight: 700; color: var(--ink); }
.rank { color: var(--muted); font-weight: 600; width: 40px; }

.method {
  font-family: var(--mono);
  font-size: .77rem;
  word-break: break-all;
  max-width: 560px;
  color: var(--ink);
}

.table-scroll { overflow-x: auto; margin: 0 -.75rem; padding: 0 .75rem; }

/* Percentage bars */
.bar-cell { width: 140px; min-width: 90px; padding-left: 0; }
.pct-bar {
  height: 4px;
  min-width: 2px;
  transition: width .3s ease;
}

/* ─── Component breakdown table ────────────────────────── */
table.breakdown tr.comp-row td {
  padding: .7rem .75rem;
  font-family: var(--sans);
  font-size: .92rem;
  font-weight: 500;
}
table.breakdown tr.comp-row {
  cursor: pointer;
  user-select: none;
}
table.breakdown tr.comp-row .comp-name {
  display: flex;
  align-items: center;
  gap: .55rem;
}
table.breakdown tr.comp-row .comp-name .swatch { width: 12px; height: 12px; }
table.breakdown tr.comp-row:hover { background: rgba(24,17,12,0.025); }
table.breakdown tr.comp-row.disabled { color: var(--faint); }
table.breakdown tr.comp-row.disabled .comp-name { text-decoration: line-through; }
table.breakdown tr.comp-row.disabled .swatch { opacity: .35; }
table.breakdown tr.comp-hotspot.disabled { opacity: .45; }
table.breakdown tr.comp-hotspot.disabled .comp-hotspot-method { text-decoration: line-through; }

table.breakdown tr.comp-hotspot td {
  padding: .18rem .75rem .18rem 2rem;
  border-bottom: 1px dashed var(--rule-hair);
  background: transparent;
}
table.breakdown tr.comp-hotspot:hover { background: rgba(24,17,12,0.025); }

table.breakdown tr.comp-hotspot td.num {
  font-family: var(--mono);
  font-size: .72rem;
  color: var(--muted);
  font-weight: 500;
}
.comp-hotspot-name {
  display: flex;
  align-items: center;
  gap: .7rem;
}
.comp-hotspot-rule {
  display: inline-block;
  width: 18px;
  height: 1px;
  flex-shrink: 0;
}
.comp-hotspot-method {
  font-family: var(--mono);
  font-size: .72rem;
  color: var(--ink-soft);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

/* ─── Chips (component tags) ───────────────────────────── */
.chip {
  display: inline-block;
  padding: .1rem .55rem;
  font-family: var(--mono);
  font-size: .66rem;
  font-weight: 600;
  letter-spacing: .08em;
  text-transform: uppercase;
  color: var(--c, var(--muted));
  border: 1px solid currentColor;
  border-radius: 0;
  white-space: nowrap;
}

/* ─── Flame graph ──────────────────────────────────────── */
.flame-wrap {
  border: 1px solid var(--rule);
  background: #FFFFFF;
}
.flame-hint {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: .7rem 1rem;
  border-bottom: 1px solid var(--rule);
  font-family: var(--mono);
  font-size: .75rem;
  color: var(--muted);
}
.flame-btn {
  font-weight: 600;
  color: var(--accent);
  border-bottom: 1px solid var(--accent);
}
.flame-iframe {
  width: 100%;
  height: 620px;
  border: 0;
  display: block;
  background: #fff;
}

/* ─── Colophon / footer ────────────────────────────────── */
.colophon {
  margin-top: 3rem;
  border-top: 1px solid var(--rule);
  background: var(--surface);
}
.colophon-inner {
  max-width: 1180px;
  margin: 0 auto;
  padding: 1.1rem 2rem 1.3rem;
  font-family: var(--mono);
  font-size: .7rem;
  letter-spacing: .06em;
  color: var(--muted);
  display: flex;
  gap: .5rem;
  flex-wrap: wrap;
}
.colophon-inner a { color: var(--accent); border-bottom: 1px solid transparent; }
.colophon-inner a:hover { border-bottom-color: var(--accent); }
.colophon-inner .sep { color: var(--faint); }
.colophon-spacer { flex: 1; }
.colophon-meta { color: var(--faint); font-size: .66rem; letter-spacing: .02em; }

/* ─── Empty & utility ──────────────────────────────────── */
.empty {
  font-family: var(--sans);
  color: var(--muted);
  font-style: italic;
  padding: 1rem 0;
}

/* ─── Responsive ───────────────────────────────────────── */
@media (max-width: 960px) {
  .breakdown-grid { grid-template-columns: 1fr; }
  .donut-figure { margin-bottom: 1.5rem; }
}

@media (max-width: 720px) {
  html { font-size: 14px; }
  .doc-head-inner, main, .colophon-inner { padding-left: 1.1rem; padding-right: 1.1rem; }
  .sysinfo-grid, .metrics { grid-template-columns: 1fr; }
  .sysinfo-block, .metric { border-left: 0; border-top: 1px solid var(--rule-hair); }
  .sysinfo-block:first-child, .metric:first-child { border-top: 0; }
}

/* ─── Print ────────────────────────────────────────────── */
@media print {
  body { background: #fff; }
  .flame-iframe { display: none; }
  .section { break-inside: avoid; }
  a { color: var(--ink); border-bottom: 0; }
}
"""


# ── Main ─────────────────────────────────────────────────────────────────

def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <run-directory>", file=sys.stderr)
        sys.exit(1)

    run_dir = sys.argv[1]
    if not os.path.isdir(run_dir):
        print(f"Error: {run_dir} is not a directory", file=sys.stderr)
        sys.exit(1)

    html = generate_html(run_dir)
    out_path = os.path.join(run_dir, "report.html")
    with open(out_path, "w") as f:
        f.write(html)


if __name__ == "__main__":
    main()
