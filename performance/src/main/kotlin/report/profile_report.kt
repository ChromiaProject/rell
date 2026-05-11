/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.performance.report

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import org.intellij.lang.annotations.Language
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*

// ─── Component classification ────────────────────────────────────────────────────────────

private const val PG_RELL = "Postgres (Rell)"
private const val PG_POSTCHAIN = "Postgres (Postchain)"

private val COMPONENT_RULES: List<Triple<String, List<String>, List<String>>> = listOf(
    Triple(
        "Postgres",
        listOf(
            "org.postgresql.", "java.sql.", "javax.sql.",
            "com.zaxxer.hikari.",
            "net.postchain.base.data.", "net.postchain.common.data.",
        ),
        emptyList(),
    ),
    Triple(
        "Rell",
        listOf("net.postchain.rell.", "lib.rell."),
        listOf("Rt_", "R_", "C_", "L_", "M_", "S_"),
    ),
    Triple(
        "Postchain",
        listOf("net.postchain.", "com.chromia."),
        emptyList(),
    ),
)

private val COMPONENT_COLORS = mapOf(
    "Rell" to "#1D4ED8",
    "Postchain" to "#166534",
    PG_RELL to "#B45309",
    PG_POSTCHAIN to "#D97706",
    "JVM" to "#44403C",
    "Idle" to "#A8A9AE",
)

/** Leaf → parent group. Leaves not present here are their own parent. */
private val COMPONENT_PARENT = mapOf(
    PG_RELL to "Postgres",
    PG_POSTCHAIN to "Postgres",
)

/** Color per parent group (only used when a parent has >1 child). */
private val PARENT_COLORS = mapOf(
    "Postgres" to "#92400E",
)

private val COMPONENT_ORDER = listOf("Rell", "Postchain", PG_RELL, PG_POSTCHAIN, "JVM", "Idle")
private val DEFAULT_DISABLED_COMPONENTS = setOf("JVM", "Idle")

private val IDLE_FRAME_MARKERS = listOf(
    "__psynch_cvwait", "__ulock_wait", "__psynch_mutexwait",
    "_pthread_cond_wait",
    "nanosleep", "epoll_wait", "kevent", "kqueue_wait",
    "LockSupport.park", "Unsafe.park", "UnsafePark",
    "Monitor::wait", "PlatformMonitor::wait", "PlatformEvent::park",
    "PlatformParker::park", "os::PlatformEvent::park",
    "JVM_Sleep", "Thread.sleep",
    "mach_msg2_trap",
)

private val THREAD_CATEGORIES: List<Triple<String, String, List<String>>> = listOf(
    Triple("gc", "GC", listOf("G1CollectedHeap::", "G1ConcurrentMark", "G1PrimaryConcurrent",
        "G1ServiceThread", "G1ConcurrentRefine", "G1YoungCollector",
        "ConcurrentGCThread::", "ConcurrentMarkThread", "ParallelGCTask",
        "ParallelCleanup", "YoungGCTask", "Monitor deflation thread",
        "VMThread::", "VM_Operation", "VM_G1", "VM_CGC")),
    Triple("jit", "JIT compiler", listOf("CompileBroker::", "C2Compiler::", "C1Compiler::",
        "Compile::Compile", "Compilation::", "PhaseIdealLoop::",
        "LIR_Assembler::", "Sweeper::", "Matcher::match", "PhaseChaitin",
        "PhaseCCP", "PhaseIterGVN")),
    Triple("vm", "VM internals", listOf("ReferenceHandler.run", "Finalizer.run",
        "ServiceThread::", "Common-Cleaner", "NotificationThread", "MonitorDeflationThread")),
    Triple("io", "Net / IO", listOf("org.eclipse.jetty.", "io.netty.", "okhttp3.",
        "sun.nio.ch.EPoll", "sun.nio.ch.KQueue", "ForkJoinPool.commonPool")),
    Triple("db-pool", "DB pool", listOf("com.zaxxer.hikari.")),
    Triple("postchain", "Postchain", listOf("net.postchain.ebft.", "net.postchain.core.framework.",
        "RevoltTracker", "BaseBlockchainProcess", "ValidatorSyncManager")),
)
private val DEFAULT_DISABLED_THREADS = setOf("gc", "jit", "vm")

@Suppress("RegExpUnnecessaryNonCapturingGroup")
private val CLASS_BOUNDARY_REGEXES: Map<String, Regex?> = COMPONENT_RULES.associate { (comp, _, prefixes) ->
    comp to if (prefixes.isEmpty()) null
    else Regex("(?:^|[./$])(?:${prefixes.joinToString("|") { Regex.escape(it) }})")
}

private fun isIdleFrame(frame: String): Boolean = IDLE_FRAME_MARKERS.any { it in frame }

private fun classifyFrame(frame: String): String {
    val normalized = frame.replace('/', '.')
    for ((component, substrings, _) in COMPONENT_RULES) {
        if (substrings.any { it in normalized }) return component
        CLASS_BOUNDARY_REGEXES[component]?.containsMatchIn(normalized)?.let { if (it) return component }
    }
    return "JVM"
}

private fun classifyStack(stack: String): String {
    val frames = stack.split(';')
    val seen = HashSet<String>()
    for (f in frames) seen += classifyFrame(f)
    if ("Postgres" in seen) return if ("Rell" in seen) PG_RELL else PG_POSTCHAIN
    if ("Rell" in seen) return "Rell"
    if ("Postchain" in seen) return "Postchain"
    if (frames.isNotEmpty() && isIdleFrame(frames.last())) return "Idle"
    return "JVM"
}

private fun pgBucketFor(frames: List<String>): String =
    if (frames.any { classifyFrame(it) == "Rell" }) PG_RELL else PG_POSTCHAIN

private fun classifyThread(stack: String): String {
    for ((cat, _, markers) in THREAD_CATEGORIES) {
        if (markers.any { it in stack }) return cat
    }
    return "app"
}

private fun threadCategoryLabel(cat: String): String =
    THREAD_CATEGORIES.firstOrNull { it.first == cat }?.second ?: "Application"

// ─── Parsing async-profiler collapsed stacks ─────────────────────────────────────────────

private val THREAD_TOKEN = Regex("^\\[([^]]+?)(?:\\s+tid=\\d+)?]$")

private data class Stack(val thread: String, val frames: String, val count: Long)

private fun parseCollapsed(path: Path): List<Stack> {
    if (!path.exists()) return emptyList()
    val out = ArrayList<Stack>()
    path.useLines { lines ->
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val sep = line.lastIndexOf(' ')
            if (sep <= 0) continue
            val count = line.substring(sep + 1).toLongOrNull() ?: continue
            var frames = line.substring(0, sep).split(';')
            var thread = ""
            if (frames.isNotEmpty()) {
                THREAD_TOKEN.matchEntire(frames[0])?.let {
                    thread = it.groupValues[1]
                    frames = frames.drop(1)
                }
            }
            out += Stack(thread, frames.joinToString(";"), count)
        }
    }
    return out
}

// ─── Aggregations ────────────────────────────────────────────────────────────────────────

private fun computeBreakdown(stacks: List<Stack>): Map<String, Long> {
    val out = LinkedHashMap<String, Long>()
    for (s in stacks) out.merge(classifyStack(s.frames), s.count, Long::plus)
    return out
}

private data class ThreadSummary(val cat: String, val label: String, val samples: Long, val pct: Double)

private fun computeThreadSummary(stacks: List<Stack>): List<ThreadSummary> {
    val totals = LinkedHashMap<String, Long>()
    for (s in stacks) totals.merge(classifyThread(s.frames), s.count, Long::plus)
    val grand = totals.values.sum().coerceAtLeast(1)
    val ordered = THREAD_CATEGORIES.map { it.first } + "app"
    return ordered
        .filter { (totals[it] ?: 0) > 0 }
        .map { cat ->
            val n = totals[cat] ?: 0
            ThreadSummary(cat, threadCategoryLabel(cat), n, (10000.0 * n / grand).toInt() / 100.0)
        }
}

private data class ComponentHotspot(val method: String, val total: Long, val byThread: Map<String, Long>)

@Suppress("SameParameterValue")
private fun computeComponentHotspots(stacks: List<Stack>, topN: Int = 3): Map<String, List<ComponentHotspot>> {
    // (component, method) -> per-thread sample counts
    val perCompMethod = mutableMapOf<String, HashMap<String, HashMap<String, Long>>>()
    for (k in listOf("Rell", "Postchain", PG_RELL, PG_POSTCHAIN, "JVM", "Idle")) perCompMethod[k] = HashMap()
    for (s in stacks) {
        val frames = s.frames.split(';')
        val pgBucket = pgBucketFor(frames)
        val tCat = classifyThread(s.frames)
        val seen = HashSet<String>()
        for (frame in frames) {
            if (frame in seen) continue
            seen += frame
            val comp = if (isIdleFrame(frame)) "Idle" else {
                var c = classifyFrame(frame)
                if (c == "Postgres") c = pgBucket
                c
            }
            perCompMethod.getOrPut(comp) { HashMap() }
                .getOrPut(frame) { HashMap() }
                .merge(tCat, s.count, Long::plus)
        }
    }
    return perCompMethod.mapValues { (_, methods) ->
        methods.entries
            .map { (method, bag) -> ComponentHotspot(method, bag.values.sum(), bag) }
            .sortedByDescending { it.total }
            .take(topN)
    }
}

private data class Hotspot(
    val method: String,
    val component: String,
    val samples: Long,
    val ownSamples: Long,
    val pct: Double,
    val ownPct: Double,
)

@Suppress("SameParameterValue")
private fun computeHotspots(stacks: List<Stack>, topN: Int = 30): List<Hotspot> {
    val inclusive = HashMap<Pair<String, String>, Long>()
    val own = HashMap<Pair<String, String>, Long>()
    var grand = 0L
    for (s in stacks) {
        grand += s.count
        val frames = s.frames.split(';')
        if (frames.isEmpty()) continue
        val pgBucket = pgBucketFor(frames)
        val seen = HashSet<Pair<String, String>>()
        for (fr in frames) {
            var comp = classifyFrame(fr)
            if (comp == "Postgres") comp = pgBucket
            val key = fr to comp
            if (key in seen) continue
            seen += key
            inclusive.merge(key, s.count, Long::plus)
        }
        val leaf = frames.last()
        var leafComp = classifyFrame(leaf)
        if (leafComp == "Postgres") leafComp = pgBucket
        own.merge(leaf to leafComp, s.count, Long::plus)
    }
    val total = grand.coerceAtLeast(1).toDouble()
    return inclusive.entries.sortedByDescending { it.value }.take(topN).map { (key, samples) ->
        val (m, comp) = key
        val ownSamples = own[key] ?: 0
        Hotspot(m, comp, samples,
            ownSamples,
            (10000.0 * samples / total).toInt() / 100.0,
            (10000.0 * ownSamples / total).toInt() / 100.0)
    }
}

private data class HotspotFiltered(
    val method: String,
    val component: String,
    val incl: Map<String, Long>,
    val own: Map<String, Long>,
)

@Suppress("SameParameterValue")
private fun computeHotspotsFilterable(stacks: List<Stack>, maxMethods: Int = 300): List<HotspotFiltered> {
    val inclusive = HashMap<Pair<String, String>, HashMap<String, Long>>()
    val own = HashMap<Pair<String, String>, HashMap<String, Long>>()
    val totals = HashMap<Pair<String, String>, Long>()

    for (s in stacks) {
        val tCat = classifyThread(s.frames)
        val frames = s.frames.split(';')
        if (frames.isEmpty()) continue
        val pgBucket = pgBucketFor(frames)
        val seen = HashSet<Pair<String, String>>()
        for (fr in frames) {
            var comp = classifyFrame(fr)
            if (comp == "Postgres") comp = pgBucket
            val key = fr to comp
            if (key in seen) continue
            seen += key
            inclusive.getOrPut(key) { HashMap() }.merge(tCat, s.count, Long::plus)
            totals.merge(key, s.count, Long::plus)
        }
        val leaf = frames.last()
        var leafComp = classifyFrame(leaf)
        if (leafComp == "Postgres") leafComp = pgBucket
        own.getOrPut(leaf to leafComp) { HashMap() }.merge(tCat, s.count, Long::plus)
    }
    return totals.entries.sortedByDescending { it.value }.take(maxMethods).map { (key, _) ->
        val (m, comp) = key
        HotspotFiltered(m, comp, inclusive[key].orEmpty(), own[key].orEmpty())
    }
}

// ─── HTML report entry point ─────────────────────────────────────────────────────────────

fun main(args: Array<String>) {
    val runDir = args.firstOrNull()?.let { Path.of(it) } ?: Path.of("performance/reports")
    require(runDir.exists()) { "Run directory does not exist: $runDir" }
    generateProfileReport(runDir)
    println("Wrote HTML report: ${runDir / "report.html"}")
}

fun generateProfileReport(runDir: Path) {
    val stacks = parseCollapsed(runDir / "collapsed.txt")
    val breakdown = computeBreakdown(stacks)
    val componentHotspots = computeComponentHotspots(stacks, topN = 3)
    val hotspots = computeHotspots(stacks, topN = 15)
    val hotspotsFull = computeHotspotsFilterable(stacks, maxMethods = 300)
    val threadSummary = computeThreadSummary(stacks)

    val grid: List<Map<String, Any>> = run {
        val matrix = HashMap<Pair<String, String>, Long>()
        for (s in stacks) matrix.merge(classifyStack(s.frames) to classifyThread(s.frames), s.count, Long::plus)
        matrix.entries.filter { it.value > 0 }.map { (k, n) -> mapOf("c" to k.first, "t" to k.second, "n" to n) }
    }

    val mapper = jacksonObjectMapper()
    val workload = readJsonObject(runDir / "workload-results.json", mapper)
    val sysinfo = readJsonObject(runDir / "system-info.json", mapper)
    val pgTables = readJsonArray(runDir / "pg-table-stats.json", mapper)
    val pgIndexes = readJsonArray(runDir / "pg-index-stats.json", mapper)
    val pgSizes = readJsonArray(runDir / "pg-sizes.json", mapper)
    val flamegraphHtml = (runDir / "flamegraph.html").let { if (it.exists()) it.readText() else "" }

    val totalSamples = breakdown.values.sum()
    val phases = (workload?.get("phases") as? ObjectNode) ?: jacksonObjectMapper().createObjectNode()

    val numTx = (workload?.get("num_users")?.asInt(0) ?: 0) * (workload?.get("posts_per_user")?.asInt(0) ?: 0) +
        2 * (workload?.get("num_users")?.asInt(0) ?: 0)
    val ts = sysinfo?.get("timestamp")?.asText("") ?: ""
    val dateDisplay = if (ts.isNotEmpty()) ts.replace("T", " · ").trimEnd('Z') else ""

    (runDir / "report.html").bufferedWriter().use { writer ->
        writer.write("<!DOCTYPE html>\n")
        writer.appendHTML().html {
            renderProfileReport(
                breakdown = breakdown,
                componentHotspots = componentHotspots,
                hotspots = hotspots,
                hotspotsFull = hotspotsFull,
                threadSummary = threadSummary,
                grid = grid,
                sysinfo = sysinfo,
                workload = workload,
                phases = phases,
                pgTables = pgTables,
                pgIndexes = pgIndexes,
                pgSizes = pgSizes,
                flamegraphHtml = flamegraphHtml,
                totalSamples = totalSamples,
                numTx = numTx,
                ts = ts,
                dateDisplay = dateDisplay,
                mapper = mapper,
            )
        }
    }
}

private fun readJsonObject(path: Path, mapper: ObjectMapper): ObjectNode? = try {
    if (path.exists()) mapper.readTree(path.inputStream()) as? ObjectNode else null
} catch (_: Exception) { null }

private fun readJsonArray(path: Path, mapper: ObjectMapper): List<JsonNode> = try {
    if (path.exists()) (mapper.readTree(path.inputStream()) as? ArrayNode)?.toList().orEmpty() else emptyList()
} catch (_: Exception) { emptyList() }

// ─── HTML rendering (kotlinx.html DSL) ───────────────────────────────────────────────────

@Suppress("LongParameterList")
private fun HTML.renderProfileReport(
    breakdown: Map<String, Long>,
    componentHotspots: Map<String, List<ComponentHotspot>>,
    hotspots: List<Hotspot>,
    hotspotsFull: List<HotspotFiltered>,
    threadSummary: List<ThreadSummary>,
    grid: List<Map<String, Any>>,
    sysinfo: ObjectNode?,
    workload: ObjectNode?,
    phases: ObjectNode,
    pgTables: List<JsonNode>,
    pgIndexes: List<JsonNode>,
    pgSizes: List<JsonNode>,
    flamegraphHtml: String,
    totalSamples: Long,
    numTx: Int,
    ts: String,
    dateDisplay: String,
    mapper: ObjectMapper,
) {
    val versionsNode = sysinfo?.get("versions") as? ObjectNode
    val rellVer = versionsNode?.get("rell")?.asText("—") ?: "—"
    val postchainVer = versionsNode?.get("postchain")?.asText("—") ?: "—"

    val workloadTime = workload?.get("total_time_s")?.asDouble(0.0) ?: 0.0
    val totalQueries = workload?.get("total_queries")?.asInt(0) ?: 0
    val event = sysinfo?.get("profiler_event")?.asText("cpu") ?: "cpu"

    attributes["lang"] = "en"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title("Postchain Node Profile — ${ts.ifEmpty { "Report" }}")
        link(rel = "preconnect", href = "https://fonts.googleapis.com")
        link(rel = "preconnect", href = "https://fonts.gstatic.com") { attributes["crossorigin"] = "" }
        link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Geist:wght@300;400;500;600;700" +
                "&family=JetBrains+Mono:wght@400;500;600;700&display=swap",
        )
        style { unsafe { +(BASE_CSS + "\n" + PROFILE_EXTRA_CSS) } }
    }
    body {
        renderDocHead("Postchain Node Profile", listOf("rell" to rellVer, "postchain" to postchainVer))
        main {
            renderSection("System Information") { renderSystemInfo(sysinfo) }
            renderSection("Workload") {
                renderDappInfo()
                renderMetrics(workloadTime, numTx, totalQueries, totalSamples, event)
            }
            renderSection("Filters", "Applies to Component Breakdown and Hot Methods below") {
                renderFilters(breakdown, threadSummary)
            }
            renderSection(
                "Component Breakdown",
                "Click a row to toggle that component — mirrors the component filter above",
            ) { renderBreakdown(breakdown, componentHotspots) }
            renderSection("Phases", "Durations per phase") {
                div(classes = "bars-wrap") { unsafe { +phasesBarsSvg(phases) } }
            }
            renderSection(
                "Hot Methods",
                "Top 15 by inclusive samples — honours the component and thread filters above",
            ) { renderHotspots(hotspots) }
            renderSection("Postgres", "Activity since the workload started — table / index / size") {
                h3(classes = "sub") { +"Table activity" }; renderPgTable(pgTables)
                h3(classes = "sub") { +"Index usage" }; renderPgIndex(pgIndexes)
                h3(classes = "sub") { +"Relation sizes" }; renderPgSizes(pgSizes)
            }
            renderSection("Flame Graph", "Interactive call-stack visualisation") {
                renderFlamegraph(flamegraphHtml)
            }
        }
        renderColophon(dateDisplay.ifEmpty { "—" }, java.time.Instant.now())
        renderClientScript(grid, threadSummary, hotspotsFull, mapper)
    }
}

private fun FlowContent.renderSystemInfo(sysinfo: ObjectNode?) {
    if (sysinfo == null) {
        p(classes = "empty") { +"No system info recorded" }
        return
    }
    val versions = sysinfo.get("versions") as? ObjectNode
    val asProfiler = sysinfo.get("async_profiler")?.asText("—") ?: "—"
    div(classes = "sysinfo-grid") {
        hostBlock(HostInfo.fromJson(sysinfo))
        sysinfoBlock("JVM") {
            val jvm = JvmInfo.fromJson(sysinfo)
            dlRow("Vendor", jvm.vendorLabel())
            dlRow("Version", jvm.runtimeVersion, mono = true)
            dlRow("VM", "${jvm.vmName} ${jvm.vmVersion}".trim(), mono = true)
            dlRow("JAVA_HOME", jvm.path, mono = true)
            dlRow("async-profiler", asProfiler, mono = true)
        }
        div(classes = "sysinfo-block") {
            h3(classes = "cmd") {
                span(classes = "cmd-prompt") { +"$" }
                +" chr version"
            }
            dl { renderVersionRows(versions) }
        }
    }
}

private fun DL.renderVersionRows(versions: ObjectNode?) {
    if (versions == null || versions.isEmpty) {
        dt { +"Versions" }
        dd(classes = "dim") { +"Not available" }
        return
    }
    val order = listOf("chr", "rell", "postchain", "eif", "java")
    val labels = mapOf(
        "chr" to "chr", "rell" to "Rell", "postchain" to "Postchain",
        "eif" to "EIF", "java" to "Java (chr process)",
    )
    for (k in order) versions.get(k)?.let { v -> dlRow(labels.getValue(k), v.asText(""), mono = true) }
    versions.properties().forEach { (k, v) ->
        if (k !in order) dlRow(k, v.asText(""), mono = true)
    }
}

private const val DAPP_SOURCE_URL = "$RELL_BLOB/performance/dapp/src/main.rell"

private fun FlowContent.renderDappInfo() = div(classes = "dapp-info") {
    p(classes = "dapp-info-desc") {
        +"Social-posts schema — "
        code { +"user" }
        +" (mutable bio), "
        code { +"post" }
        +" (authored, timestamped), "
        code { +"tag" }
        +", "
        code { +"post_tag" }
        +". The workload runs "
        code { +"create_user" }
        +", "
        code { +"create_post" }
        +", "
        code { +"update_bio" }
        +", then a mix of read queries ("
        code { +"count_users" }
        +", "
        code { +"list_users" }
        +", "
        code { +"get_user" }
        +", "
        code { +"get_posts_by_user" }
        +", "
        code { +"search_posts" }
        +", "
        code { +"get_user_post_count" }
        +")."
    }
    ul(classes = "dapp-info-sources") {
        li {
            a(href = DAPP_SOURCE_URL) {
                attributes["target"] = "_blank"
                attributes["rel"] = "noopener"
                +"rell · performance/dapp/src/main.rell"
            }
        }
    }
}

private fun FlowContent.renderMetrics(
    workloadTime: Double, numTx: Int, queries: Int, samples: Long, event: String,
) = div(classes = "metrics") {
    metric("Wall clock", "%.1f".format(Locale.ROOT, workloadTime), "s", "total workload")
    metric("Transactions", numTx.localized(), "", "tx ops + updates")
    metric("Queries", queries.localized(), "", "REST calls")
    metric("Samples", samples.localized(), "", "event: $event")
}

private fun FlowContent.renderFilters(
    breakdown: Map<String, Long>,
    threadSummary: List<ThreadSummary>,
) = div(classes = "filters-panel") {
    div(classes = "filter-bar") {
        id = "component-filter"
        span(classes = "filter-label") { +"components" }
        for (comp in COMPONENT_ORDER) {
            val n = breakdown[comp] ?: 0
            if (n == 0L) continue
            val color = COMPONENT_COLORS[comp] ?: "#888"
            label(classes = "filter-chip") {
                checkBoxInput {
                    attributes["data-component"] = comp
                    if (comp !in DEFAULT_DISABLED_COMPONENTS) checked = true
                }
                span(classes = "filter-swatch") { attributes["style"] = "background:$color" }
                span(classes = "filter-chip-label") { +comp }
                span(classes = "filter-chip-count") { +n.localized() }
            }
        }
    }
    div(classes = "filter-bar") {
        id = "thread-filter"
        span(classes = "filter-label") { +"threads" }
        if (threadSummary.isEmpty()) {
            span(classes = "empty") { +"no thread data" }
        } else {
            for (row in threadSummary) {
                label(classes = "filter-chip") {
                    checkBoxInput {
                        attributes["data-thread-cat"] = row.cat
                        if (row.cat !in DEFAULT_DISABLED_THREADS) checked = true
                    }
                    span(classes = "filter-chip-label") { +row.label }
                    span(classes = "filter-chip-count") { +row.samples.localized() }
                }
            }
        }
    }
}

private fun FlowContent.renderBreakdown(
    breakdown: Map<String, Long>,
    componentHotspots: Map<String, List<ComponentHotspot>>,
) = div(classes = "breakdown-grid") {
    figure(classes = "donut-figure") { unsafe { +donutPlaceholderSvg() } }
    div(classes = "breakdown-table") {
        table(classes = "breakdown") {
            thead {
                tr {
                    th { +"Component" }
                    th(classes = "num") { +"Samples" }
                    th(classes = "num") { +"Share" }
                    th {}
                }
            }
            tbody {
                val activeTotal = breakdown.entries
                    .filter { it.key !in DEFAULT_DISABLED_COMPONENTS }
                    .sumOf { it.value }.coerceAtLeast(1)
                for (comp in COMPONENT_ORDER) {
                    val n = breakdown[comp] ?: 0
                    val off = comp in DEFAULT_DISABLED_COMPONENTS
                    val pct = if (off) 0.0 else 100.0 * n / activeTotal
                    val color = COMPONENT_COLORS[comp] ?: "#888"
                    tr(classes = if (off) "comp-row disabled" else "comp-row") {
                        attributes["data-component"] = comp
                        attributes["data-samples"] = n.toString()
                        td(classes = "comp-name") {
                            span(classes = "swatch") { attributes["style"] = "background:$color" }
                            +comp
                        }
                        td(classes = "num") { +n.localized() }
                        td(classes = "num strong comp-share") {
                            +(if (off) "—" else "%.1f%%".format(Locale.ROOT, pct))
                        }
                        td(classes = "bar-cell") {
                            div(classes = "pct-bar") {
                                attributes["style"] = "width:${"%.1f".format(Locale.ROOT, pct)}%;background:$color"
                            }
                        }
                    }
                    if (n > 0) {
                        val bagMapper = jacksonObjectMapper()
                        for (h in componentHotspots[comp].orEmpty()) {
                            val mPct = if (off) 0.0 else 100.0 * h.total / activeTotal
                            tr(classes = if (off) "comp-hotspot disabled" else "comp-hotspot") {
                                attributes["data-parent"] = comp
                                attributes["data-samples"] = h.total.toString()
                                attributes["data-bag"] = bagMapper.writeValueAsString(h.byThread)
                                td(classes = "comp-hotspot-name") {
                                    span(classes = "comp-hotspot-rule") { attributes["style"] = "background:$color" }
                                    span(classes = "comp-hotspot-method") {
                                        attributes["title"] = h.method
                                        +shortMethod(h.method)
                                    }
                                }
                                td(classes = "num") { +h.total.localized() }
                                td(classes = "num comp-hotspot-share") {
                                    +(if (off) "—" else "%.1f%%".format(Locale.ROOT, mPct))
                                }
                                td {}
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun FlowContent.renderHotspots(hotspots: List<Hotspot>) = div(classes = "table-scroll") {
    table(classes = "hotspots") {
        thead {
            tr {
                th(classes = "num rank") { +"№" }
                th { +"Method" }
                th { +"Component" }
                th(classes = "num") { +"Samples" }
                th(classes = "num") { +"Own" }
                th(classes = "num") { +"%" }
                th {}
            }
        }
        tbody {
            id = "hotspots-tbody"
            for ((i, h) in hotspots.withIndex()) {
                val color = COMPONENT_COLORS[h.component] ?: "#888"
                val method = h.method.replace('/', '.')
                val display = if (method.length <= 90) method else method.substring(0, 87) + "..."
                tr {
                    td(classes = "num rank") { +"%02d".format(i + 1) }
                    td(classes = "method") {
                        attributes["title"] = method
                        +display
                    }
                    td {
                        span(classes = "chip") {
                            attributes["style"] = "--c:$color"
                            +h.component
                        }
                    }
                    td(classes = "num") { +h.samples.localized() }
                    td(classes = "num") { +h.ownSamples.localized() }
                    td(classes = "num strong") { +"%.1f%%".format(Locale.ROOT, h.pct) }
                    td(classes = "bar-cell") {
                        div(classes = "pct-bar") {
                            attributes["style"] = "width:${"%.1f".format(Locale.ROOT, h.pct)}%;background:$color"
                        }
                    }
                }
            }
        }
    }
}

private fun shortMethod(method: String): String {
    val m = method.replace("()", "").replace('/', '.')
    val parts = m.split('.')
    for ((i, p) in parts.withIndex()) {
        if (p.isNotEmpty() && (p[0].isUpperCase() || p[0] == '<' || p[0] == '$')) return parts.drop(i).joinToString(".")
    }
    return parts.takeLast(2).joinToString(".")
}

private fun FlowContent.renderPgTable(rows: List<JsonNode>, limit: Int = 10) {
    if (rows.isEmpty()) {
        p(classes = "empty") { +"No table statistics available" }
        return
    }
    val sorted = rows.sortedByDescending {
        it.path("seq_tup_read").asLong(0) + it.path("idx_tup_fetch").asLong(0)
    }.take(limit)
    div(classes = "table-scroll") {
        table {
            thead {
                tr {
                    th { +"Table" }
                    th { +"Seq Scans" }; th { +"Seq Rows" }
                    th { +"Idx Scans" }; th { +"Idx Fetches" }
                    th { +"Inserts" }; th { +"Updates" }; th { +"Live Rows" }
                }
            }
            tbody {
                val cols = listOf(
                    "seq_scan", "seq_tup_read", "idx_scan", "idx_tup_fetch",
                    "n_tup_ins", "n_tup_upd", "n_live_tup",
                )
                for (t in sorted) {
                    tr {
                        td(classes = "mono") { +t.path("relname").asText("") }
                        for (col in cols) td(classes = "num") { +t.path(col).asLong(0).localized() }
                    }
                }
            }
        }
    }
}

private fun FlowContent.renderPgIndex(rows: List<JsonNode>) {
    if (rows.isEmpty()) {
        p(classes = "empty") { +"No index statistics available" }
        return
    }
    div(classes = "table-scroll") {
        table {
            thead {
                tr {
                    th { +"Index" }; th { +"Scans" }; th { +"Rows Read" }
                    th { +"Rows Fetched" }; th { +"Size" }
                }
            }
            tbody {
                for (idx in rows) tr {
                    td(classes = "mono") { +idx.path("indexrelname").asText("") }
                    td(classes = "num") { +idx.path("idx_scan").asLong(0).localized() }
                    td(classes = "num") { +idx.path("idx_tup_read").asLong(0).localized() }
                    td(classes = "num") { +idx.path("idx_tup_fetch").asLong(0).localized() }
                    td(classes = "num") { +idx.path("index_size_bytes").asLong(0).humanBytes() }
                }
            }
        }
    }
}

private fun FlowContent.renderPgSizes(rows: List<JsonNode>, limit: Int = 10) {
    if (rows.isEmpty()) {
        p(classes = "empty") { +"No size data available" }
        return
    }
    div(classes = "table-scroll") {
        table {
            thead {
                tr { th { +"Relation" }; th { +"Total" }; th { +"Table" }; th { +"Indexes" } }
            }
            tbody {
                for (s in rows.take(limit)) tr {
                    td(classes = "mono") { +s.path("relname").asText("") }
                    td(classes = "num") { +s.path("total_bytes").asLong(0).humanBytes() }
                    td(classes = "num") { +s.path("table_bytes").asLong(0).humanBytes() }
                    td(classes = "num") { +s.path("indexes_bytes").asLong(0).humanBytes() }
                }
            }
        }
    }
}

private fun FlowContent.renderFlamegraph(html: String) {
    if (html.isEmpty()) {
        p(classes = "empty") { +"No flame graph — async-profiler did not attach successfully." }
        return
    }
    div(classes = "flame-wrap") {
        div(classes = "flame-hint") {
            span { +"Hover frames for detail, click to zoom, Ctrl+F to search." }
            a(href = "flamegraph.html", classes = "flame-btn") {
                attributes["target"] = "_blank"
                +"Open full-screen →"
            }
        }
        iframe(classes = "flame-iframe") {
            src = "flamegraph.html"
            title = "Flame graph"
        }
    }
}

private fun FlowContent.renderClientScript(
    grid: List<Map<String, Any>>,
    threadSummary: List<ThreadSummary>,
    hotspotsFull: List<HotspotFiltered>,
    mapper: ObjectMapper,
) {
    val threadSummaryJson = mapper.writeValueAsString(threadSummary.map {
        mapOf("cat" to it.cat, "label" to it.label, "samples" to it.samples, "pct" to it.pct)
    })
    val hotspotsFullJson = mapper.writeValueAsString(hotspotsFull.map {
        mapOf("m" to it.method, "c" to it.component, "incl" to it.incl, "own" to it.own)
    })
    val preamble = """
        const GRID = ${mapper.writeValueAsString(grid)};
        const THREAD_SUMMARY = $threadSummaryJson;
        const HOTSPOTS = $hotspotsFullJson;
        const HOTSPOTS_VISIBLE = 15;
        const COLORS = ${mapper.writeValueAsString(COMPONENT_COLORS)};
        const ORDER = ${mapper.writeValueAsString(COMPONENT_ORDER)};
        const PARENT = ${mapper.writeValueAsString(COMPONENT_PARENT)};
        const PARENT_COLORS = ${mapper.writeValueAsString(PARENT_COLORS)};
        const disabledComp = new Set(${mapper.writeValueAsString(DEFAULT_DISABLED_COMPONENTS.sorted())});
        const disabledThread = new Set(${mapper.writeValueAsString(DEFAULT_DISABLED_THREADS.sorted())});
    """.trimIndent()
    script {
        unsafe { +"(function() {\n$preamble\n$JS_BODY})();" }
    }
}

private fun Long.localized(): String = "%,d".format(Locale.ROOT, this)
private fun Int.localized(): String = "%,d".format(Locale.ROOT, this)

// ─── SVG charts ──────────────────────────────────────────────────────────────────────────

private const val DONUT_SIZE = 420

/** Empty SVG placeholder; the donut is drawn client-side in [JS_BODY] from GRID + filters. */
private fun donutPlaceholderSvg(): String =
    """<svg id="donut" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $DONUT_SIZE $DONUT_SIZE"
        width="$DONUT_SIZE" height="$DONUT_SIZE" role="img" aria-label="component breakdown"></svg>""".trimIndent()

private fun phasesBarsSvg(phases: ObjectNode): String {
    if (phases.size() == 0) return "<p class=\"empty\">No workload data</p>"
    val items = phases.properties().toList()
    val labels = items.map { it.key.replace('_', ' ') }
    val values = items.map { it.value.path("time_s").asDouble(0.0) }
    val palette = listOf(ACCENT_HEX, "#2E5E8A", "#1F6B4A", "#B07A1E", "#5E2E8A", "#8A5E2E")
    return stackedHbarSvg(labels, values, palette, unit = "s", width = 720)
}

internal fun stackedHbarSvg(
    labels: List<String>,
    values: List<Double>,
    palette: List<String>,
    unit: String,
    width: Int,
): String {
    if (labels.isEmpty()) return ""
    val padX = 12
    val barH = 30
    val labelGap = 12   // vertical distance between bar edge and label
    val labelLineH = 14
    val height = barH + 2 * (labelGap + labelLineH) + 12
    val barY = labelGap + labelLineH + 6
    val plotW = width - 2 * padX
    val total = values.sum().coerceAtLeast(1e-9)

    val sb = StringBuilder()
    sb.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" """)
    sb.append("""width="$width" height="$height" role="img">""")

    var x = padX.toDouble()
    for (i in labels.indices) {
        val w = plotW * (values[i] / total)
        val color = palette[i % palette.size]
        sb.append("""<rect x="${"%.2f".formatRoot(x)}" y="$barY" """)
        sb.append("""width="${"%.2f".formatRoot(w)}" height="$barH" fill="$color"/>""")
        val cx = x + w / 2
        val valueText = formatDuration(values[i], unit)
        if (w > 60) {
            sb.append("""<text class="hbar-value" x="${"%.2f".formatRoot(cx)}" y="${barY + barH / 2 + 4}" text-anchor="middle">$valueText</text>""")
        }
        // Alternate above (even) / below (odd) so adjacent labels never collide.
        val above = i % 2 == 0
        val labelY = if (above) barY - labelGap else barY + barH + labelGap + labelLineH - 4
        val tickY1 = if (above) barY else barY + barH
        val tickY2 = if (above) barY - labelGap + 4 else barY + barH + labelGap - 4
        sb.append("""<line class="hbar-tick" x1="${"%.2f".formatRoot(cx)}" y1="$tickY1" """)
        sb.append("""x2="${"%.2f".formatRoot(cx)}" y2="$tickY2"/>""")
        val text = "${labels[i].xmlEscape()} · $valueText"
        // Clamp anchor: if a centered label would overflow the SVG horizontally, switch
        // to start/end alignment at the appropriate edge.
        val approxCharW = 6.6
        val halfTextW = (text.length * approxCharW) / 2
        val (textX, anchor) = when {
            cx - halfTextW < padX -> Pair(padX.toDouble(), "start")
            cx + halfTextW > width - padX -> Pair((width - padX).toDouble(), "end")
            else -> Pair(cx, "middle")
        }
        sb.append("""<text class="hbar-label" x="${"%.2f".formatRoot(textX)}" y="$labelY" text-anchor="$anchor">$text</text>""")
        x += w
    }
    sb.append("</svg>")
    return sb.toString()
}

private fun formatDuration(v: Double, unit: String): String =
    "%.2f $unit".formatRoot(v)

internal fun barsSvg(
    labels: List<String>,
    values: List<Double>,
    unit: String,
    color: String,
    width: Int,
    height: Int,
): String {
    if (labels.isEmpty()) return ""
    val padL = 44; val padR = 16; val padT = 14; val padB = 36
    val plotW = width - padL - padR
    val plotH = height - padT - padB
    val maxV = (values.maxOrNull() ?: 0.0).coerceAtLeast(1e-9)
    val niceMax = niceCeiling(maxV)
    val barGap = 0.25
    val nBars = labels.size
    val slot = plotW.toDouble() / nBars
    val barW = slot * (1 - barGap)

    val sb = StringBuilder()
    sb.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" """)
    sb.append("""role="img">""")

    // Y-axis ticks (4 divisions)
    val ticks = 4
    for (i in 0..ticks) {
        val v = niceMax * i / ticks
        val y = padT + plotH - plotH * i / ticks
        sb.append("""<line class="grid" x1="$padL" y1="$y" x2="${padL + plotW}" y2="$y"/>""")
        sb.append("""<text class="tick" x="${padL - 6}" y="${y + 3}" text-anchor="end">${formatTick(v)}</text>""")
    }

    // Baseline
    sb.append("""<line class="baseline" x1="$padL" y1="${padT + plotH}" x2="${padL + plotW}" y2="${padT + plotH}"/>""")

    // Bars
    for (i in labels.indices) {
        val v = values[i]
        val h = plotH * (v / niceMax)
        val x = padL + slot * i + (slot - barW) / 2
        val y = padT + plotH - h
        sb.append("""<rect x="${"%.2f".formatRoot(x)}" y="${"%.2f".formatRoot(y)}" """)
        sb.append("""width="${"%.2f".formatRoot(barW)}" height="${"%.2f".formatRoot(h)}" fill="$color"/>""")
        // X-axis label
        val cx = padL + slot * i + slot / 2
        val esc = labels[i].xmlEscape()
        sb.append("""<text class="bar-axis" x="${"%.2f".formatRoot(cx)}" y="${padT + plotH + 18}" text-anchor="middle">$esc</text>""")
        // Value on top
        if (v > 0) {
            val label = "%.2f".formatRoot(v) + (if (unit.isNotEmpty()) " $unit" else "")
            sb.append("""<text class="bar-value" x="${"%.2f".formatRoot(cx)}" y="${"%.2f".formatRoot(y - 4)}" text-anchor="middle">$label</text>""")
        }
    }
    sb.append("</svg>")
    return sb.toString()
}

private fun niceCeiling(v: Double): Double {
    if (v <= 0) return 1.0
    val mag = Math.pow(10.0, Math.floor(Math.log10(v)))
    val n = v / mag
    val nice = when {
        n <= 1.0 -> 1.0
        n <= 2.0 -> 2.0
        n <= 2.5 -> 2.5
        n <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * mag
}

private fun formatTick(v: Double): String =
    if (v >= 100) "%.0f".formatRoot(v)
    else if (v >= 10) "%.1f".formatRoot(v)
    else "%.2f".formatRoot(v)

private fun String.xmlEscape(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

// ─── Helpers ─────────────────────────────────────────────────────────────────────────────



// ─── Profile-specific CSS additions on top of BASE_CSS ───────────────────────────────────

@Suppress("CssUnresolvedCustomProperty", "CssUnusedSymbol", "CssNoGenericFontName")
@Language("CSS")
private val PROFILE_EXTRA_CSS = """
.sysinfo-block h3.cmd { text-transform: none; letter-spacing: 0; color: var(--ink); font-weight: 500; }
.sysinfo-block h3.cmd .cmd-prompt { color: var(--accent); margin-right: .35rem; font-weight: 600; }

.dapp-info {
  background: var(--surface); border: 1px solid var(--rule);
  padding: .9rem 1rem; margin-bottom: 1rem;
}
.dapp-info-desc {
  font-family: var(--sans); font-size: .82rem; line-height: 1.5;
  color: var(--ink-soft); margin: 0 0 .5rem;
}
.dapp-info-desc code {
  font-family: var(--mono); font-size: .78rem; color: var(--ink);
  background: rgba(24,17,12,0.04); padding: 0 .3rem;
}
.dapp-info-sources {
  list-style: none; padding: 0; margin: 0;
  border-top: 1px dashed var(--rule-hair); padding-top: .45rem;
  font-family: var(--mono); font-size: .72rem;
}
.dapp-info-sources li { margin: .12rem 0; overflow-wrap: anywhere; }
.dapp-info-sources a { color: var(--accent); }

.filters-panel {
  display: flex; flex-direction: column;
  border: 1px solid var(--rule); background: var(--surface);
}
.filters-panel .filter-bar { border: 0; margin-bottom: 0; background: transparent; }
.filters-panel .filter-bar + .filter-bar { border-top: 1px solid var(--rule-hair); }

.filter-bar {
  display: flex; flex-wrap: wrap; align-items: center;
  gap: .5rem; margin-bottom: .8rem;
  padding: .6rem .9rem; background: var(--surface); border: 1px solid var(--rule);
  font-family: var(--mono);
}
.filter-label {
  font-family: var(--mono); font-size: .68rem; text-transform: uppercase;
  letter-spacing: .18em; color: var(--muted); font-weight: 600; margin-right: .3rem;
}
.filter-chip {
  display: inline-flex; align-items: center; gap: .4rem;
  padding: .2rem .55rem .2rem .4rem; border: 1px solid var(--rule);
  font-size: .72rem; color: var(--ink-soft); cursor: pointer; user-select: none;
  transition: background .12s ease, border-color .12s ease, color .12s ease;
}
.filter-chip:has(input:checked) { background: var(--accent-bg); border-color: var(--accent); color: var(--ink); }
.filter-chip input { accent-color: var(--accent); margin: 0; width: 12px; height: 12px; cursor: pointer; }
.filter-chip-label { font-family: var(--mono); font-weight: 500; }
.filter-chip-count { font-family: var(--mono); color: var(--muted); font-size: .68rem; font-variant-numeric: tabular-nums; }
.filter-swatch { display: inline-block; width: 10px; height: 10px; background: var(--muted); flex-shrink: 0; }

.breakdown-grid {
  display: grid; grid-template-columns: minmax(0, 440px) minmax(0, 1fr); gap: 2rem;
  align-items: start; background: var(--surface); border: 1px solid var(--rule); padding: 1.2rem;
}
.donut-figure { display: flex; flex-direction: column; align-items: center; min-width: 0; }
.donut-figure svg { display: block; max-width: 100%; height: auto; }
.breakdown-table { min-width: 0; overflow-x: auto; }
.breakdown-table table.breakdown { table-layout: auto; }
.donut-label-in { font-family: var(--mono); font-size: 12px; font-weight: 600; fill: #fff; }
.donut-label-in-pct { font-family: var(--mono); font-size: 11px; fill: rgba(255,255,255,0.85); }
.donut-label-out { font-family: var(--mono); font-size: 12px; font-weight: 600; fill: #fff; }
svg .grid { stroke: var(--rule-hair); stroke-width: 1; }
svg .baseline { stroke: var(--ink); stroke-width: 1; }
svg .tick { font-family: var(--mono); font-size: 11px; fill: var(--muted); }
svg .bar-axis { font-family: var(--mono); font-size: 11px; fill: var(--ink-soft); }
svg .bar-value { font-family: var(--mono); font-size: 10px; fill: var(--ink); }
svg .hbar-value { font-family: var(--mono); font-size: 12px; font-weight: 600; fill: #fff; }
svg .hbar-label { font-family: var(--mono); font-size: 11px; fill: var(--ink-soft); }
svg .hbar-tick { stroke: var(--rule); stroke-width: 1; }
.swatch { display: inline-block; width: 10px; height: 10px; background: var(--muted); }

.bars-wrap {
  max-width: 720px; background: var(--surface); border: 1px solid var(--rule);
  padding: .9rem 1rem;
}
.bars-wrap svg { display: block; max-width: 100%; height: auto; }

.bar-cell { width: 80px; max-width: 80px; padding-left: 0; padding-right: .5rem; overflow: hidden; }
.pct-bar { height: 4px; min-width: 0; max-width: 100%; transition: width .3s ease; }

.method { font-family: var(--mono); font-size: .77rem; word-break: break-all; max-width: 560px; color: var(--ink); }

table.breakdown tr.comp-row td { padding: .7rem .75rem; font-family: var(--sans); font-size: .92rem; font-weight: 500; }
table.breakdown tr.comp-row { cursor: pointer; user-select: none; }
table.breakdown tr.comp-row .comp-name { display: flex; align-items: center; gap: .55rem; }
table.breakdown tr.comp-row .comp-name .swatch { width: 12px; height: 12px; }
table.breakdown tr.comp-row:hover { background: rgba(24,17,12,0.025); }
table.breakdown tr.comp-row.disabled { color: var(--faint); }
table.breakdown tr.comp-row.disabled .comp-name { text-decoration: line-through; }
table.breakdown tr.comp-row.disabled .swatch { opacity: .35; }
table.breakdown tr.comp-hotspot.disabled { opacity: .45; }
table.breakdown tr.comp-hotspot.disabled .comp-hotspot-method { text-decoration: line-through; }
table.breakdown tr.comp-hotspot td {
  padding: .18rem .75rem .18rem 2rem;
  border-bottom: 1px dashed var(--rule-hair); background: transparent;
}
table.breakdown tr.comp-hotspot:hover { background: rgba(24,17,12,0.025); }
table.breakdown tr.comp-hotspot td.num {
  font-family: var(--mono); font-size: .72rem; color: var(--muted); font-weight: 500;
}
.comp-hotspot-name { display: flex; align-items: center; gap: .7rem; }
.comp-hotspot-rule { display: inline-block; width: 18px; height: 1px; flex-shrink: 0; }
.comp-hotspot-method {
  font-family: var(--mono); font-size: .72rem; color: var(--ink-soft);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1;
}

.chip {
  display: inline-block; padding: .1rem .55rem;
  font-family: var(--mono); font-size: .66rem; font-weight: 600;
  letter-spacing: .08em; text-transform: uppercase;
  color: var(--c, var(--muted)); border: 1px solid currentColor; border-radius: 0; white-space: nowrap;
}

.flame-wrap { border: 1px solid var(--rule); background: #FFFFFF; }
.flame-hint {
  display: flex; justify-content: space-between; align-items: center;
  padding: .7rem 1rem; border-bottom: 1px solid var(--rule);
  font-family: var(--mono); font-size: .75rem; color: var(--muted);
}
.flame-btn { font-weight: 600; color: var(--accent); border-bottom: 1px solid var(--accent); }
.flame-iframe { width: 100%; height: 620px; border: 0; display: block; background: #fff; }

@media print { .flame-iframe { display: none; } }

@media (max-width: 960px) {
  .breakdown-grid { grid-template-columns: 1fr; }
  .donut-figure { margin-bottom: 1.5rem; }
}
""".trimIndent()

@Suppress("JSUnresolvedReference")
@Language("JavaScript")
private val JS_BODY = $$"""
function componentSamples(comp) {
  let s = 0;
  for (const cell of GRID) {
    if (cell.c !== comp) continue;
    if (disabledThread.has(cell.t)) continue;
    s += cell.n;
  }
  return s;
}
function activeTotal() {
  return ORDER.filter(c => !disabledComp.has(c)).reduce((s, c) => s + componentSamples(c), 0);
}
function updateRows() {
  const total = activeTotal() || 1;
  document.querySelectorAll("tr.comp-row").forEach(row => {
    const comp = row.dataset.component;
    const samples = componentSamples(comp);
    const off = disabledComp.has(comp);
    row.classList.toggle("disabled", off);
    row.querySelector("td.num").textContent = samples.toLocaleString();
    const pct = off ? 0 : (100 * samples / total);
    const share = row.querySelector(".comp-share");
    if (share) share.textContent = off ? "—" : `${pct.toFixed(1)}%`;
    const bar = row.querySelector(".pct-bar");
    if (bar) bar.style.width = pct.toFixed(1) + "%";
  });
  document.querySelectorAll("tr.comp-hotspot").forEach(row => {
    const parent = row.dataset.parent;
    let bag = null;
    try { bag = JSON.parse(row.dataset.bag || "null"); } catch (e) {}
    let samples = 0;
    if (bag) {
      for (const [t, n] of Object.entries(bag)) {
        if (disabledThread.has(t)) continue;
        samples += n;
      }
    } else {
      samples = parseInt(row.dataset.samples, 10) || 0;
    }
    const off = disabledComp.has(parent);
    row.classList.toggle("disabled", off);
    const samplesCell = row.querySelector("td.num");
    if (samplesCell) samplesCell.textContent = samples.toLocaleString();
    const pct = (off || total === 0) ? 0 : (100 * samples / total);
    const share = row.querySelector(".comp-hotspot-share");
    if (share) share.textContent = off ? "—" : `${pct.toFixed(1)}%`;
  });
}
function escapeHtml(s) {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}
function sumCounts(bag) {
  let s = 0;
  for (const [t, n] of Object.entries(bag)) {
    if (disabledThread.has(t)) continue;
    s += n;
  }
  return s;
}
function renderHotspots() {
  const tbody = document.getElementById("hotspots-tbody");
  if (!tbody) return;
  const total = activeTotal() || 1;
  const ranked = [];
  for (const h of HOTSPOTS) {
    if (disabledComp.has(h.c)) continue;
    const incl = sumCounts(h.incl);
    if (incl === 0) continue;
    const own = sumCounts(h.own);
    ranked.push({m: h.m, c: h.c, incl: incl, own: own});
  }
  ranked.sort((a, b) => b.incl - a.incl);
  const top = ranked.slice(0, HOTSPOTS_VISIBLE);
  tbody.textContent = "";
  top.forEach((h, i) => {
    const color = COLORS[h.c] || "#888";
    const method = h.m.replace(/\//g, ".");
    const display = method.length <= 90 ? method : method.slice(0, 87) + "...";
    const pct = 100 * h.incl / total;
    const tr = document.createElement("tr");
    tr.innerHTML =
      `<td class="num rank">${String(i+1).padStart(2,"0")}</td>` +
      `<td class="method" title="${escapeHtml(method)}">${escapeHtml(display)}</td>` +
      `<td><span class="chip" style="--c:${color}">${h.c}</span></td>` +
      `<td class="num">${h.incl.toLocaleString()}</td>` +
      `<td class="num">${h.own.toLocaleString()}</td>` +
      `<td class="num strong">${pct.toFixed(1)}%</td>` +
      `<td class="bar-cell"><div class="pct-bar" style="width:${pct.toFixed(1)}%;background:${color}"></div></td>`;
    tbody.appendChild(tr);
  });
  if (top.length === 0) {
    const tr = document.createElement("tr");
    tr.innerHTML = '<td colspan="7" class="empty">No methods match the current filters.</td>';
    tbody.appendChild(tr);
  }
}
const DONUT_SIZE = 420, DONUT_CX = DONUT_SIZE / 2, DONUT_CY = DONUT_SIZE / 2;
const DONUT_R_OUT = 180;   // outer ring radius (parent labels live here)
const DONUT_R_MID = 128;   // inner/outer boundary for nested groups
const DONUT_NS = "http://www.w3.org/2000/svg";
let donutPrev = null;
let donutAnim = null;
function donutSlicePath(cx, cy, rIn, rOut, a0, a1) {
  const sweep = a1 - a0;
  if (sweep <= 1e-6) return "";
  // Full circle: SVG arcs collapse when start == end. Split into two semicircles.
  if (sweep >= 2 * Math.PI - 1e-3) {
    if (rIn <= 0.0001) {
      return `M ${(cx - rOut).toFixed(3)} ${cy.toFixed(3)} A ${rOut} ${rOut} 0 1 1 ${(cx + rOut).toFixed(3)} ${cy.toFixed(3)} A ${rOut} ${rOut} 0 1 1 ${(cx - rOut).toFixed(3)} ${cy.toFixed(3)} Z`;
    }
    return `M ${(cx - rOut).toFixed(3)} ${cy.toFixed(3)} A ${rOut} ${rOut} 0 1 1 ${(cx + rOut).toFixed(3)} ${cy.toFixed(3)} A ${rOut} ${rOut} 0 1 1 ${(cx - rOut).toFixed(3)} ${cy.toFixed(3)} M ${(cx - rIn).toFixed(3)} ${cy.toFixed(3)} A ${rIn} ${rIn} 0 1 0 ${(cx + rIn).toFixed(3)} ${cy.toFixed(3)} A ${rIn} ${rIn} 0 1 0 ${(cx - rIn).toFixed(3)} ${cy.toFixed(3)} Z`;
  }
  const large = sweep > Math.PI ? 1 : 0;
  const ox0 = cx + rOut * Math.cos(a0), oy0 = cy + rOut * Math.sin(a0);
  const ox1 = cx + rOut * Math.cos(a1), oy1 = cy + rOut * Math.sin(a1);
  if (rIn <= 0.0001) {
    return `M ${cx} ${cy} L ${ox0.toFixed(3)} ${oy0.toFixed(3)} A ${rOut} ${rOut} 0 ${large} 1 ${ox1.toFixed(3)} ${oy1.toFixed(3)} Z`;
  }
  const ix0 = cx + rIn * Math.cos(a1), iy0 = cy + rIn * Math.sin(a1);
  const ix1 = cx + rIn * Math.cos(a0), iy1 = cy + rIn * Math.sin(a0);
  return `M ${ox0.toFixed(3)} ${oy0.toFixed(3)} A ${rOut} ${rOut} 0 ${large} 1 ${ox1.toFixed(3)} ${oy1.toFixed(3)} L ${ix0.toFixed(3)} ${iy0.toFixed(3)} A ${rIn} ${rIn} 0 ${large} 0 ${ix1.toFixed(3)} ${iy1.toFixed(3)} Z`;
}
/** Build slice records: leaf-level angles + parent groups. */
function donutSlices() {
  const active = ORDER
    .filter(c => !disabledComp.has(c))
    .map(c => ({c: c, n: componentSamples(c)}))
    .filter(x => x.n > 0);
  const total = active.reduce((s, x) => s + x.n, 0);
  if (total === 0) return {leaves: [], groups: []};
  let a = -Math.PI / 2;
  const leaves = active.map(x => {
    const sweep = (x.n / total) * 2 * Math.PI;
    const a0 = a, a1 = a + sweep;
    a = a1;
    const parent = PARENT[x.c] || x.c;
    return {c: x.c, parent: parent, n: x.n, a0: a0, a1: a1, pct: 100 * x.n / total};
  });
  // Group leaves by parent (preserving order); record parent-level a0/a1.
  const groups = [];
  for (const lf of leaves) {
    const last = groups.length > 0 ? groups[groups.length - 1] : null;
    if (last && last.parent === lf.parent) {
      last.a1 = lf.a1;
      last.children.push(lf);
    } else {
      groups.push({parent: lf.parent, a0: lf.a0, a1: lf.a1, children: [lf]});
    }
  }
  return {leaves: leaves, groups: groups};
}
function donutDrawFrame(svg, model) {
  while (svg.firstChild) svg.removeChild(svg.firstChild);
  const {leaves, groups} = model;
  // Each leaf: extends 0 → either R_MID (if its parent has multiple children visible) or R_OUT.
  for (const lf of leaves) {
    const parentMulti = groups.find(g => g.parent === lf.parent && g.children.length > 1);
    const rOut = parentMulti ? DONUT_R_MID : DONUT_R_OUT;
    const path = document.createElementNS(DONUT_NS, "path");
    path.setAttribute("d", donutSlicePath(DONUT_CX, DONUT_CY, 0, rOut, lf.a0, lf.a1));
    path.setAttribute("fill", COLORS[lf.c] || "#888");
    path.setAttribute("stroke", "#fff");
    path.setAttribute("stroke-width", "1");
    svg.appendChild(path);
  }
  // Outer ring band for multi-child parents only.
  for (const g of groups) {
    if (g.children.length <= 1) continue;
    const path = document.createElementNS(DONUT_NS, "path");
    path.setAttribute("d", donutSlicePath(DONUT_CX, DONUT_CY, DONUT_R_MID, DONUT_R_OUT, g.a0, g.a1));
    path.setAttribute("fill", PARENT_COLORS[g.parent] || "#444");
    path.setAttribute("stroke", "#fff");
    path.setAttribute("stroke-width", "1");
    svg.appendChild(path);
  }
  // Leaf labels (inside each leaf wedge). For multi-child parents, drop the parent prefix
  // since the outer band already names the parent.
  for (const lf of leaves) {
    const sweep = lf.a1 - lf.a0;
    if (sweep < 0.18) continue;
    const parentMulti = groups.find(g => g.parent === lf.parent && g.children.length > 1);
    const rOuter = parentMulti ? DONUT_R_MID : DONUT_R_OUT;
    const labelR = rOuter * 0.62;
    const mid = (lf.a0 + lf.a1) / 2;
    const lx = DONUT_CX + labelR * Math.cos(mid);
    const ly = DONUT_CY + labelR * Math.sin(mid);
    let display = lf.c;
    if (parentMulti && lf.c.startsWith(lf.parent + " ")) {
      display = lf.c.slice(lf.parent.length + 1).replace(/^\(|\)$/g, "");
    }
    const tName = document.createElementNS(DONUT_NS, "text");
    tName.setAttribute("class", "donut-label-in");
    tName.setAttribute("x", lx.toFixed(2));
    tName.setAttribute("y", (ly - 3).toFixed(2));
    tName.setAttribute("text-anchor", "middle");
    tName.textContent = display;
    svg.appendChild(tName);
    const tPct = document.createElementNS(DONUT_NS, "text");
    tPct.setAttribute("class", "donut-label-in-pct");
    tPct.setAttribute("x", lx.toFixed(2));
    tPct.setAttribute("y", (ly + 12).toFixed(2));
    tPct.setAttribute("text-anchor", "middle");
    tPct.textContent = lf.pct.toFixed(0) + "%";
    svg.appendChild(tPct);
  }
  // Outer-band parent labels (rotated along the arc tangent so they always fit the band).
  for (const g of groups) {
    if (g.children.length <= 1) continue;
    const sweep = g.a1 - g.a0;
    if (sweep < 0.18) continue;
    const mid = (g.a0 + g.a1) / 2;
    const r = (DONUT_R_MID + DONUT_R_OUT) / 2;
    const lx = DONUT_CX + r * Math.cos(mid);
    const ly = DONUT_CY + r * Math.sin(mid);
    let rot = (mid * 180 / Math.PI) + 90;
    // Flip so the text reads left-to-right on the bottom half of the circle.
    if (rot > 90 && rot < 270) rot -= 180;
    const tName = document.createElementNS(DONUT_NS, "text");
    tName.setAttribute("class", "donut-label-out");
    tName.setAttribute("x", lx.toFixed(2));
    tName.setAttribute("y", (ly + 4).toFixed(2));
    tName.setAttribute("text-anchor", "middle");
    tName.setAttribute("transform", `rotate(${rot.toFixed(2)} ${lx.toFixed(2)} ${ly.toFixed(2)})`);
    tName.textContent = g.parent;
    svg.appendChild(tName);
  }
}
function donutInterpolate(prevByComp, target, t) {
  const tweenedLeaves = target.leaves.map(s => {
    const p = prevByComp[s.c];
    if (!p) return {...s, a0: s.a0, a1: s.a0 + (s.a1 - s.a0) * t};
    return {...s, a0: p.a0 + (s.a0 - p.a0) * t, a1: p.a1 + (s.a1 - p.a1) * t};
  });
  // Recompute groups from tweened leaves.
  const groups = [];
  for (const lf of tweenedLeaves) {
    const last = groups.length > 0 ? groups[groups.length - 1] : null;
    if (last && last.parent === lf.parent) {
      last.a1 = lf.a1;
      last.children.push(lf);
    } else {
      groups.push({parent: lf.parent, a0: lf.a0, a1: lf.a1, children: [lf]});
    }
  }
  return {leaves: tweenedLeaves, groups: groups};
}
function renderDonut() {
  const svg = document.getElementById("donut");
  if (!svg) return;
  const target = donutSlices();
  if (donutAnim) cancelAnimationFrame(donutAnim);
  if (!donutPrev || donutPrev.leaves.length === 0) {
    donutDrawFrame(svg, target);
    donutPrev = target;
    return;
  }
  const prevByComp = {};
  for (const lf of donutPrev.leaves) prevByComp[lf.c] = lf;
  const start = performance.now();
  const dur = 280;
  function step(now) {
    const t = Math.min(1, (now - start) / dur);
    const eased = 1 - Math.pow(1 - t, 3);
    donutDrawFrame(svg, donutInterpolate(prevByComp, target, eased));
    if (t < 1) donutAnim = requestAnimationFrame(step);
    else { donutPrev = target; donutAnim = null; }
  }
  donutAnim = requestAnimationFrame(step);
}
function rerender() { updateRows(); renderHotspots(); renderDonut(); }
function syncComponentUi() {
  document.querySelectorAll("#component-filter input[type=checkbox]").forEach(cb => {
    cb.checked = !disabledComp.has(cb.dataset.component);
  });
}
function toggleComponent(comp) {
  if (disabledComp.has(comp)) disabledComp.delete(comp); else disabledComp.add(comp);
  syncComponentUi(); rerender();
}
document.querySelectorAll("tr.comp-row").forEach(row => {
  row.addEventListener("click", () => toggleComponent(row.dataset.component));
});
document.querySelectorAll("#component-filter input[type=checkbox]").forEach(cb => {
  cb.addEventListener("change", () => toggleComponent(cb.dataset.component));
});
document.querySelectorAll("#thread-filter input[type=checkbox]").forEach(cb => {
  cb.addEventListener("change", () => {
    const cat = cb.dataset.threadCat;
    if (cb.checked) disabledThread.delete(cat); else disabledThread.add(cat);
    rerender();
  });
});
rerender();
""".trimIndent()
