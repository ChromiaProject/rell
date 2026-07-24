/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:JvmName("Lsp_reportKt")

package net.postchain.rell.performance.report

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import org.intellij.lang.annotations.Language
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.useLines
import kotlin.io.path.writeText

// ─── Data model ──────────────────────────────────────────────────────────────────────────

private val RUNS = listOf("cold", "hot")

private class LspRunData(
    val name: String,
    val milestones: JsonNode,
    val componentSeconds: Map<String, Double>,
    val hotMethods: List<Pair<String, Double>>,
    val activeSeconds: Double,
) {
    fun milestone(field: String): Double? =
        milestones.get(field)?.takeIf { !it.isNull }?.asDouble()
}

// ─── Component classification ────────────────────────────────────────────────────────────
//
// Frames are scanned leaf-upward and the first match wins, so a sample is attributed to the
// deepest recognizable layer (a linter frame inside an indexing stack counts as linter, not
// indexer). Idle stacks — threads parked in waits or blocked on stdio — are dropped first;
// what remains is genuine work, summed across all threads (so totals can exceed wall time).

private val LSP_COMPONENT_RULES: List<Pair<String, Regex>> = listOf(
    "JIT compiler" to Regex("CompileBroker|JVMCI|jdk\\.graal|org\\.graalvm\\.compiler|Compilation::|C2Compiler|C1Compiler|PhaseIdealLoop|PhaseChaitin"),
    "GC" to Regex("G1Par|G1Concurrent|G1Young|GreyToBlack|GCTaskThread|ZWorker|VM_Operation|G1ServiceThread|ConcurrentMark"),
    "Linter (code-quality)" to Regex("toolbox\\.linter"),
    "ANTLR parsing" to Regex("org\\.antlr\\.|rell\\.base\\.compiler\\.parser\\.antlr"),
    "Rell compiler (rell-base)" to Regex("net\\.postchain\\.rell\\.base\\."),
    "Toolbox indexer" to Regex("toolbox\\.indexer"),
    "Toolbox AST" to Regex("toolbox\\.ast"),
    "Index cache (Fory)" to Regex("org\\.apache\\.fory|RellIndexSerializer|IndexCaching"),
    "LSP server layer" to Regex("toolbox\\.lsp|org\\.eclipse\\.lsp4j|com\\.google\\.gson"),
    "Koin DI" to Regex("org\\.koin\\."),
    "Logging" to Regex("log4j|slf4j|oshai\\.kotlinlogging"),
    "Class loading" to Regex("ClassLoader|defineClass|SystemDictionary|ClassFileParser|loadClass|JarFile|ZipFile"),
)

private const val OTHER_COMPONENT = "Other JVM / stdlib"

private val LSP_IDLE_MARKERS = listOf(
    "__psynch_cvwait", "__ulock_wait", "__psynch_mutexwait", "_pthread_cond_wait",
    "pthread_cond_wait", "nanosleep", "epoll_wait", "kevent", "kqueue",
    "LockSupport.park", "Unsafe.park", "Unsafe_Park",
    "Monitor::wait", "PlatformMonitor::wait", "PlatformEvent::park", "PlatformParker::park",
    "JVM_Sleep", "Thread.sleep", "mach_msg", "semaphore_wait", "__workq", "__wait4",
    "Object.wait", "__select", "poll",
)

/** Leaf frames that mean "blocked on I/O" — e.g. the stdio listener waiting for the client. */
private val LSP_BLOCKING_LEAVES = Regex("^(read|write|recv|recvfrom|accept|__read|pread)(_\\[[a-z0-9]+])?$")

private val FRAME_ANNOTATION = Regex("_\\[[a-z0-9]+]$")

private fun isIdleStack(frames: List<String>): Boolean {
    if (LSP_BLOCKING_LEAVES.matches(frames.last())) return true
    return frames.takeLast(3).any { frame -> LSP_IDLE_MARKERS.any { it in frame } }
}

// ─── Collapsed-stack aggregation ─────────────────────────────────────────────────────────

private fun aggregateRun(name: String, milestones: JsonNode, collapsedFile: Path, intervalMs: Double): LspRunData {
    val componentSamples = LinkedHashMap<String, Long>()
    val selfSamples = HashMap<String, Long>()
    var activeSamples = 0L

    collapsedFile.useLines { lines ->
        for (line in lines) {
            val sep = line.lastIndexOf(' ')
            if (sep <= 0) continue
            val count = line.substring(sep + 1).toLongOrNull() ?: continue
            val frames = line.substring(0, sep).split(';')
            if (frames.isEmpty() || isIdleStack(frames)) continue

            activeSamples += count
            val component = frames.asReversed().firstNotNullOfOrNull { frame ->
                LSP_COMPONENT_RULES.firstOrNull { (_, rx) -> rx.containsMatchIn(frame) }?.first
            } ?: OTHER_COMPONENT
            componentSamples.merge(component, count, Long::plus)

            val leaf = frames.last().replace(FRAME_ANNOTATION, "")
            selfSamples.merge(leaf, count, Long::plus)
        }
    }

    fun Long.toSeconds(): Double = this * intervalMs / 1000.0
    return LspRunData(
        name = name,
        milestones = milestones,
        componentSeconds = componentSamples.entries.associate { it.key to it.value.toSeconds() },
        hotMethods = selfSamples.entries
            .sortedByDescending { it.value }
            .take(20)
            .map { it.key to it.value.toSeconds() },
        activeSeconds = activeSamples.toSeconds(),
    )
}

// ─── Rendering ───────────────────────────────────────────────────────────────────────────

/** Render `report.html` from the artifacts `ProfileLspCommand` wrote into [outDir]. */
fun renderLspReport(outDir: Path) {
    val mapper = jacksonObjectMapper()
    val sysInfo: JsonNode? = (outDir / "system-info.json").takeIf { it.exists() }?.let { mapper.readTree(it.readText()) }
    val intervalMs = sysInfo?.get("interval_ms")?.asDouble(2.0) ?: 2.0

    val runs = RUNS.map { run ->
        val milestones = mapper.readTree((outDir / "milestones-$run.json").readText())
        aggregateRun(run, milestones, outDir / "collapsed-$run.txt", intervalMs)
    }

    val generatedAt = Instant.now()
    val html = buildString {
        append("<!DOCTYPE html>\n")
        appendHTML().html {
            head {
                meta(charset = "utf-8")
                titleWithTimestamp("Rell LSP Startup Profile", generatedAt)
                linkWebFonts()
                style { unsafe { +BASE_CSS }; unsafe { +LSP_REPORT_CSS } }
            }
            body {
                renderDocHead(
                    "rell lsp startup profile",
                    listOf(
                        "workspace" to (sysInfo?.get("workspace")?.asText() ?: "—"),
                        "server" to (sysInfo?.get("server_jar")?.asText() ?: "—"),
                        "event" to "${sysInfo?.get("profiler_event")?.asText() ?: "wall"} / ${intervalMs.toInt()} ms",
                    ),
                )
                main {
                    renderHeadlineMetrics(runs)
                    renderMilestones(runs)
                    renderComponents(runs)
                    renderHotMethods(runs)
                    renderFlamegraphs()
                    renderSysInfo(sysInfo)
                }
                renderColophon("profileLsp · cold + hot index cache", generatedAt)
            }
        }
    }
    (outDir / "report.html").writeText(html)
}

private fun FlowContent.renderHeadlineMetrics(runs: List<LspRunData>) {
    renderSection(
        "startup",
        "Time from process spawn to the initialize response — workspace indexing runs inside the " +
            "initialize request, so this is the delay before the editor becomes functional.",
    ) {
        div(classes = "metrics") {
            runs.forEach { run ->
                metric(
                    "${run.name} · initialize",
                    run.milestone("initializeResponseSec")?.let { "%.2f".formatRoot(it) } ?: "—",
                    "s",
                    "${run.milestones.get("diagnosticsFiles")?.asInt() ?: 0} files diagnosed",
                )
            }
            val cold = runs.first().milestone("initializeResponseSec")
            val hot = runs.last().milestone("initializeResponseSec")
            if (cold != null && hot != null && hot > 0) {
                metric("cold / hot", "%.1f".formatRoot(cold / hot), "×", "initialize speedup from index cache")
            }
            runs.forEach { run ->
                metric("${run.name} · active work", "%.1f".formatRoot(run.activeSeconds), "s",
                    "thread time across all threads")
            }
        }
    }
}

private fun FlowContent.renderMilestones(runs: List<LspRunData>) {
    val rows = listOf(
        "First byte from server" to "firstByteSec",
        "initialize response (workspace indexed)" to "initializeResponseSec",
        "Last diagnostic published" to "lastDiagnosticSec",
        "Quiescent" to "quiescentSec",
        "Process exit" to "exitSec",
    )
    renderSection("milestones", "Seconds since process spawn.") {
        div(classes = "milestone-timeline") { unsafe { +milestoneTimelineSvg(runs) } }
        table {
            thead {
                tr {
                    th { +"Event" }
                    runs.forEach { th(classes = "num") { +it.name } }
                }
            }
            tbody {
                rows.forEach { (label, field) ->
                    tr {
                        td(classes = "name") { +label }
                        runs.forEach { run ->
                            td(classes = "num") { +(run.milestone(field)?.let { "%.2f".formatRoot(it) } ?: "—") }
                        }
                    }
                }
            }
        }
    }
}

private fun FlowContent.renderComponents(runs: List<LspRunData>) {
    val components = LinkedHashSet<String>()
    runs.forEach { components += it.componentSeconds.keys }
    val ordered = components.sortedByDescending { runs.first().componentSeconds[it] ?: 0.0 }

    renderSection(
        "active time by component",
        "Wall-clock samples with idle stacks (parked threads, blocking stdio) removed, attributed to the " +
            "deepest recognizable layer, summed across all threads — totals can exceed wall time.",
    ) {
        div(classes = "component-bars") { unsafe { +componentBarsSvg(runs, ordered) } }
        table {
            thead {
                tr {
                    th { +"Component" }
                    runs.forEach { run ->
                        th(classes = "num") { +"${run.name} (s)" }
                        th(classes = "num") { +"${run.name} (%)" }
                    }
                }
            }
            tbody {
                ordered.forEach { component ->
                    tr {
                        td(classes = "name") { +component }
                        runs.forEach { run ->
                            val sec = run.componentSeconds[component] ?: 0.0
                            td(classes = "num") { +"%.2f".formatRoot(sec) }
                            td(classes = "num") {
                                +if (run.activeSeconds > 0) "%.1f".formatRoot(100.0 * sec / run.activeSeconds) else "—"
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun FlowContent.renderHotMethods(runs: List<LspRunData>) {
    renderSection("hot methods", "Top methods by self time (active samples only).") {
        runs.forEach { run ->
            h3(classes = "sub") { +"${run.name} run" }
            table {
                thead {
                    tr {
                        th(classes = "rank") { +"#" }
                        th { +"Method" }
                        th(classes = "num") { +"Self (s)" }
                        th(classes = "num") { +"Share" }
                    }
                }
                tbody {
                    run.hotMethods.forEachIndexed { i, (method, sec) ->
                        tr {
                            td(classes = "rank num") { +"${i + 1}" }
                            td(classes = "name mono") { +method }
                            td(classes = "num") { +"%.2f".formatRoot(sec) }
                            td(classes = "num") {
                                +if (run.activeSeconds > 0) "%.1f%%".formatRoot(100.0 * sec / run.activeSeconds) else "—"
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun FlowContent.renderFlamegraphs() {
    renderSection("flame graphs", "Wall clock, all threads, one graph per run.") {
        RUNS.forEach { run ->
            h3(classes = "sub") {
                +"$run run "
                a(href = "flamegraph-$run.html") { +"(open full page)" }
            }
            iframe(classes = "flame-iframe") { src = "flamegraph-$run.html" }
        }
    }
}

private fun FlowContent.renderSysInfo(sysInfo: JsonNode?) {
    renderSection("system", "") {
        div(classes = "sysinfo-grid") {
            hostBlock(HostInfo.fromJson(sysInfo))
            jvmBlock(JvmInfo.fromJson(sysInfo))
        }
    }
}

// ─── SVG chart ───────────────────────────────────────────────────────────────────────────

private const val COLD_COLOR = ACCENT_HEX
private const val HOT_COLOR = "#1D4ED8"

private fun xmlEscape(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/**
 * One horizontal track per run on a shared time axis: colored segments between consecutive
 * milestones (spawn → initialize response → quiescent → exit), tick markers for first byte
 * and last diagnostic, and a seconds axis below.
 */
private fun milestoneTimelineSvg(runs: List<LspRunData>): String {
    val width = 900
    val labelW = 60
    val plotW = width - labelW - 20
    val trackH = 16
    val rowGap = 34
    val legendH = 26
    val axisH = 30
    val height = legendH + runs.size * (trackH + rowGap) + axisH

    val maxSec = runs.maxOf { it.milestone("exitSec") ?: 0.0 }.coerceAtLeast(0.001)
    fun x(sec: Double): Double = labelW + plotW * sec / maxSec

    // (label, from-field, to-field, color) — segments between consecutive milestones.
    val segments = listOf(
        Triple("spawn → initialize response", null to "initializeResponseSec", ACCENT_HEX),
        Triple("→ quiescent", "initializeResponseSec" to "quiescentSec", HOT_COLOR),
        Triple("→ exit", "quiescentSec" to "exitSec", "#A8A9AE"),
    )

    val sb = StringBuilder()
    sb.append(
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" """ +
            """width="$width" height="$height" role="img" aria-label="startup milestones timeline">""",
    )

    var legendX = labelW
    segments.forEach { (label, _, color) ->
        sb.append("""<rect x="$legendX" y="6" width="14" height="9" fill="$color"/>""")
        sb.append("""<text class="lsp-bar-label" x="${legendX + 20}" y="14">${xmlEscape(label)}</text>""")
        legendX += 30 + label.length * 6
    }

    runs.forEachIndexed { ri, run ->
        val y = legendH + ri * (trackH + rowGap)
        sb.append(
            """<text class="lsp-bar-name" x="${labelW - 10}" y="${y + trackH / 2 + 4}" text-anchor="end">""" +
                """${xmlEscape(run.name)}</text>""",
        )
        segments.forEach { (_, fields, color) ->
            val from = fields.first?.let { run.milestone(it) } ?: 0.0
            val to = fields.second.let { run.milestone(it) } ?: return@forEach
            if (to <= from) return@forEach
            sb.append(
                """<rect x="${"%.2f".formatRoot(x(from))}" y="$y" """ +
                    """width="${"%.2f".formatRoot(x(to) - x(from))}" height="$trackH" fill="$color"/>""",
            )
        }
        // Tick markers with per-run captions: first byte and initialize response.
        listOf(
            "firstByteSec" to "first byte",
            "initializeResponseSec" to "initialize",
        ).forEach { (field, caption) ->
            val sec = run.milestone(field) ?: return@forEach
            val mx = "%.2f".formatRoot(x(sec))
            sb.append("""<line x1="$mx" y1="${y - 4}" x2="$mx" y2="${y + trackH + 4}" stroke="$INK_HEX" stroke-width="1"/>""")
            sb.append(
                """<text class="lsp-bar-label" x="$mx" y="${y + trackH + 16}" text-anchor="middle">""" +
                    """${xmlEscape(caption)} ${"%.2f".formatRoot(sec)} s</text>""",
            )
        }
    }

    // Axis: baseline plus ticks at a step that yields ~6 divisions.
    val axisY = legendH + runs.size * (trackH + rowGap)
    val rawStep = maxSec / 6
    val step = listOf(0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 30.0, 60.0).firstOrNull { it >= rawStep } ?: 120.0
    sb.append("""<line x1="$labelW" y1="$axisY" x2="${labelW + plotW}" y2="$axisY" stroke="$INK_HEX" stroke-width="1"/>""")
    var tick = 0.0
    while (tick <= maxSec) {
        val tx = "%.2f".formatRoot(x(tick))
        sb.append("""<line x1="$tx" y1="$axisY" x2="$tx" y2="${axisY + 5}" stroke="$INK_HEX" stroke-width="1"/>""")
        sb.append(
            """<text class="lsp-bar-label" x="$tx" y="${axisY + 18}" text-anchor="middle">""" +
                """${if (step >= 1.0) "%.0f".formatRoot(tick) else "%.1f".formatRoot(tick)} s</text>""",
        )
        tick += step
    }

    sb.append("</svg>")
    return sb.toString()
}

/**
 * Paired horizontal bars, one group per component: cold on top, hot below, both on a shared
 * seconds scale. Static server-side SVG, same approach as the other reports' charts.
 */
private fun componentBarsSvg(runs: List<LspRunData>, ordered: List<String>): String {
    val width = 900
    val labelW = 230
    val valueW = 70
    val barMaxW = width - labelW - valueW
    val barH = 9
    val barGap = 3
    val groupGap = 14
    val groupH = runs.size * (barH + barGap) - barGap + groupGap
    val legendH = 26
    val height = legendH + ordered.size * groupH

    val maxSec = ordered.maxOf { comp -> runs.maxOf { it.componentSeconds[comp] ?: 0.0 } }
        .coerceAtLeast(0.001)
    val colors = listOf(COLD_COLOR, HOT_COLOR)

    val sb = StringBuilder()
    sb.append(
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" """ +
            """width="$width" height="$height" role="img" aria-label="active time by component, cold vs hot">""",
    )

    // Legend.
    var legendX = labelW
    runs.forEachIndexed { i, run ->
        sb.append("""<rect x="$legendX" y="6" width="14" height="$barH" fill="${colors[i % colors.size]}"/>""")
        sb.append("""<text class="lsp-bar-label" x="${legendX + 20}" y="${6 + barH}">${xmlEscape(run.name)}</text>""")
        legendX += 90
    }

    ordered.forEachIndexed { gi, comp ->
        val groupY = legendH + gi * groupH
        val labelY = groupY + (runs.size * (barH + barGap) - barGap) / 2 + 4
        sb.append(
            """<text class="lsp-bar-name" x="${labelW - 10}" y="$labelY" text-anchor="end">${xmlEscape(comp)}</text>""",
        )
        runs.forEachIndexed { ri, run ->
            val sec = run.componentSeconds[comp] ?: 0.0
            val w = (barMaxW * sec / maxSec).coerceAtLeast(if (sec > 0) 1.0 else 0.0)
            val y = groupY + ri * (barH + barGap)
            sb.append(
                """<rect x="$labelW" y="$y" width="${"%.2f".formatRoot(w)}" height="$barH" """ +
                    """fill="${colors[ri % colors.size]}"/>""",
            )
            sb.append(
                """<text class="lsp-bar-label" x="${"%.2f".formatRoot(labelW + w + 6)}" y="${y + barH - 1}">""" +
                    """${"%.2f".formatRoot(sec)} s</text>""",
            )
        }
    }

    sb.append("</svg>")
    return sb.toString()
}

/** Re-render `report.html` from existing artifacts: `main [outputDir]`. */
fun main(args: Array<String>) {
    val outDir = Path.of(args.getOrElse(0) { "reports/lsp-startup" })
    renderLspReport(outDir)
    println("report: ${outDir.resolve("report.html").toAbsolutePath().normalize()}")
}

@Suppress("CssUnusedSymbol")
@Language("CSS")
private val LSP_REPORT_CSS = """
.flame-iframe { width: 100%; height: 560px; border: 1px solid var(--rule); display: block; background: #fff; margin-bottom: 1.2rem; }
@media print { .flame-iframe { display: none; } }
.component-bars { margin-bottom: 1.2rem; overflow-x: auto; }
.component-bars svg { display: block; max-width: 100%; height: auto; }
.milestone-timeline { margin-bottom: 1.2rem; overflow-x: auto; }
.milestone-timeline svg { display: block; max-width: 100%; height: auto; }
.lsp-bar-name { font-family: var(--sans); font-size: 12px; fill: var(--ink); }
.lsp-bar-label { font-family: var(--mono); font-size: 10px; fill: var(--muted); font-variant-numeric: tabular-nums; }
""".trimIndent()
