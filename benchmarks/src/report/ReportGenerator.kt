/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.benchmarks.report

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.html.DL
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.code
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.footer
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.html
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.stream.appendHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr
import kotlinx.html.unsafe
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlinx.kandy.dsl.categorical
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.toSVG
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.bars
import org.jetbrains.kotlinx.kandy.util.color.Color
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.system.exitProcess

private data class JmhMetric(
    val score: Double = 0.0,
    val scoreError: Double = 0.0,
    val scoreUnit: String = "",
)

private data class JmhResult(
    val benchmark: String = "",
    val mode: String = "",
    val threads: Int = 1,
    val forks: Int = 1,
    val warmupIterations: Int = 0,
    val warmupTime: String? = null,
    val measurementIterations: Int = 0,
    val measurementTime: String? = null,
    val params: Map<String, String> = emptyMap(),
    val primaryMetric: JmhMetric = JmhMetric(),
    val jvm: String? = null,
    val jdkVersion: String? = null,
    val vmName: String? = null,
    val vmVersion: String? = null,
    val jmhVersion: String? = null,
)

private const val ACCENT = "#C1440E"

private val METHOD_COLOR = mapOf(
    "antlr" to ACCENT,
    "antlrNoTree" to "#2E5E8A",
    "antlrSLL" to "#1F6B4A",
    "betterParse" to "#0F0F10",
    "runQuery" to ACCENT,
)

private val PALETTE = listOf(ACCENT, "#2E5E8A", "#1F6B4A", "#0F0F10", "#8A5E2E", "#5E2E8A")

private const val DEFAULT_COLOR = "#6B6D73"

private fun colorFor(method: String, fallbackIndex: Int): String =
    METHOD_COLOR[method] ?: PALETTE.getOrElse(fallbackIndex) { DEFAULT_COLOR }

fun main(args: Array<String>) {
    val parsed = parseArgs(args)
    val input = File(parsed.input).also {
        require(it.isFile) { "Input JSON does not exist: $it" }
    }
    val output = File(parsed.output)
    output.parentFile?.mkdirs()

    val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    val results: List<JmhResult> = mapper.readValue(
        input,
        mapper.typeFactory.constructCollectionType(List::class.java, JmhResult::class.java),
    )
    require(results.isNotEmpty()) { "No benchmark results found in $input" }

    val html = buildString {
        appendLine("<!DOCTYPE html>")
        appendHTML().html {
            renderReport(results, input, Instant.now())
        }
    }
    output.writeText(html)
    println("Wrote HTML report: ${output.absolutePath}")
}

private data class Args(val input: String, val output: String)

private fun parseArgs(args: Array<String>): Args {
    var input: String? = null
    var output: String? = null
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--input" -> input = args.getOrNull(++i)
            "--output" -> output = args.getOrNull(++i)
            "-h", "--help" -> {
                println("Usage: report --input <json> --output <html>")
                exitProcess(0)
            }
            else -> error("Unknown argument: ${args[i]}")
        }
        i++
    }
    return Args(
        input = checkNotNull(input) { "Missing --input" },
        output = checkNotNull(output) { "Missing --output" },
    )
}

private fun HTML.renderReport(results: List<JmhResult>, source: File, generatedAt: Instant) {
    val head = results.first()
    val byClass: Map<String, List<JmhResult>> = results.groupBy { it.benchmarkClass() }
        .toSortedMap()
    val totalSamples = results.map { it.benchmarkClass() to it.sample() }.distinct().size
    val totalMethods = results.map { it.benchmarkClass() to it.method() }.distinct().size

    attributes["lang"] = "en"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title("Rell Benchmarks — ${generatedAt.toHuman()}")
        link(rel = "preconnect", href = "https://fonts.googleapis.com")
        link(rel = "preconnect", href = "https://fonts.gstatic.com") { attributes["crossorigin"] = "" }
        link(
            rel = "stylesheet",
            href = "https://fonts.googleapis.com/css2?family=Geist:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500;600;700&display=swap",
        )
        style { unsafe { +CSS } }
    }
    body {
        renderDocHead(head, generatedAt)
        main {
            renderEnvironment(head)
            renderMetrics(results, totalSamples, totalMethods, byClass.size)
            byClass.forEach { (cls, group) -> renderGroup(cls, group) }
            renderMethodology(head)
        }
        renderColophon(source, generatedAt)
    }
}

private fun FlowContent.renderGroup(benchmarkClass: String, results: List<JmhResult>) {
    val samples = results.map { it.sample() }.distinct()
    val methods = results.map { it.method() }.distinct().sorted()
    val pivot: Map<String, Map<String, JmhResult>> = samples.associateWith { sample ->
        methods.mapNotNull { method ->
            results.firstOrNull { it.sample() == sample && it.method() == method }?.let { method to it }
        }.toMap()
    }
    renderResults(benchmarkClass, pivot, methods)
}

private fun FlowContent.renderDocHead(head: JmhResult, generatedAt: Instant) {
    header(classes = "doc-head") {
        div(classes = "doc-head-inner") {
            h1(classes = "doc-title") { +"Rell Benchmarks" }
        }
    }
}

private fun FlowContent.renderSection(
    title: String,
    strap: String = "",
    body: FlowContent.() -> Unit,
) {
    section(classes = "section") {
        div(classes = "section-head") {
            div(classes = "section-title") { +title }
            if (strap.isNotEmpty()) div(classes = "section-strap") { +strap }
        }
        div(classes = "section-body") { body() }
    }
}

private fun FlowContent.renderEnvironment(head: JmhResult) = renderSection("environment") {
    div(classes = "sysinfo-grid") {
        div(classes = "sysinfo-block") {
            h3 { +"JVM" }
            dl {
                dlRow("Name", head.vmName ?: "—")
                dlRow("Version", head.vmVersion ?: "—")
                dlRow("JDK", head.jdkVersion ?: "—")
                dlRow("Path", head.jvm ?: "—", mono = true)
            }
        }
        div(classes = "sysinfo-block") {
            h3 { +"Harness" }
            dl {
                dlRow("JMH", head.jmhVersion ?: "—")
                dlRow("Mode", head.mode.ifBlank { "—" })
                dlRow("Threads / forks", "${head.threads} / ${head.forks}")
                dlRow("Unit", head.primaryMetric.scoreUnit.ifBlank { "—" })
            }
        }
        div(classes = "sysinfo-block") {
            h3 { +"Timing" }
            dl {
                dlRow("Warmup", "${head.warmupIterations}× ${head.warmupTime ?: "—"}")
                dlRow("Measurement", "${head.measurementIterations}× ${head.measurementTime ?: "—"}")
            }
        }
    }
}

private fun DL.dlRow(key: String, value: String, mono: Boolean = false) {
    dt { +key }
    dd {
        if (mono) attributes["class"] = "mono"
        +value
    }
}

private fun FlowContent.renderMetrics(
    results: List<JmhResult>,
    sampleCount: Int,
    methodCount: Int,
    classCount: Int,
) = renderSection("workload") {
    div(classes = "metrics") {
        metric("Benchmarks", results.size.toString(), "", "runs recorded")
        metric("Suites", classCount.toString(), "", "benchmark classes")
        metric("Samples", sampleCount.toString(), "", "distinct inputs")
        metric("Methods", methodCount.toString(), "", "under test")
    }
}

private fun FlowContent.metric(label: String, value: String, unit: String, sub: String) {
    div(classes = "metric") {
        div(classes = "metric-label") { +label }
        div(classes = "metric-value") {
            +value
            if (unit.isNotEmpty()) span(classes = "metric-unit") { +" $unit" }
        }
        div(classes = "metric-sub") { +sub }
    }
}

private fun FlowContent.renderResults(
    benchmarkClass: String,
    pivot: Map<String, Map<String, JmhResult>>,
    methods: List<String>,
) {
    val unit = pivot.values.flatMap { it.values }
        .map { it.primaryMetric.scoreUnit }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    val strap = if (unit.isNotEmpty()) "$unit · lower is better" else ""
    renderSection(benchmarkClass.lowercase(), strap) {
    div(classes = "bars-wrap") {
        unsafe { +renderBarChart(pivot, methods) }
    }
    div(classes = "legend") {
        methods.forEachIndexed { i, method ->
            div(classes = "legend-item") {
                span(classes = "swatch") {
                    attributes["style"] = "background:${colorFor(method, i)}"
                }
                span(classes = "legend-label") { +method }
            }
        }
    }
    div(classes = "table-scroll") {
        table(classes = "results") {
            thead {
                tr {
                    th { +"Sample" }
                    methods.forEach { method ->
                        th(classes = "num") {
                            +method
                            if (unit.isNotEmpty()) span(classes = "unit") { +" $unit" }
                        }
                    }
                    if (methods.size == 2) th(classes = "num") { +"Ratio" }
                }
            }
            tbody {
                pivot.forEach { (sample, byMethod) ->
                    tr {
                        td(classes = "name") { +sample }
                        methods.forEach { method ->
                            val r = byMethod[method]
                            td(classes = "num") {
                                if (r != null) {
                                    val m = r.primaryMetric
                                    +"%.3f".formatRoot(m.score)
                                    span(classes = "err") { +" ± %.3f".formatRoot(m.scoreError) }
                                } else {
                                    +"—"
                                }
                            }
                        }
                        if (methods.size == 2) {
                            td(classes = "num") {
                                val a = byMethod[methods[0]]?.primaryMetric?.score
                                val b = byMethod[methods[1]]?.primaryMetric?.score
                                if (a != null && b != null && a > 0 && b > 0) {
                                    val ratio = if (a < b) b / a else a / b
                                    val faster = if (a < b) methods[0] else methods[1]
                                    span(classes = "ratio") { +"%.2f×".formatRoot(ratio) }
                                    span(classes = "ratio-note") { +" $faster" }
                                } else {
                                    +"—"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

private fun FlowContent.renderMethodology(head: JmhResult) = renderSection("methodology") {
    p(classes = "prose") {
        +"Benchmarks are driven by "
        code { +"org.jetbrains.kotlinx.benchmark" }
        +" on the JMH backend. Mode is "
        code { +head.mode }
        +"; each iteration produces a mean reported in "
        code { +head.primaryMetric.scoreUnit }
        +"."
    }
    p(classes = "prose") {
        +"Each benchmark class exposes one or more methods (shown as columns) exercised against a set of samples "
        +"(shown as rows). Samples come from JMH "
        code { +"@Param" }
        +" inputs when present, otherwise collapse to a single synthetic row."
    }
}

private fun FlowContent.renderColophon(source: File, generatedAt: Instant) {
    footer(classes = "colophon") {
        div(classes = "colophon-inner") {
            span { +"kotlinx-benchmark + Kotlin renderer" }
            span(classes = "sep") { +"·" }
            span { a(href = "https://gitlab.com/chromaway/rell") { +"chromaway/rell" } }
            span(classes = "colophon-spacer") {}
            span(classes = "colophon-meta") { +source.name }
            span(classes = "sep") { +"·" }
            span(classes = "colophon-meta") { +generatedAt.toHuman() }
        }
    }
}

private fun renderBarChart(
    pivot: Map<String, Map<String, JmhResult>>,
    methods: List<String>,
): String {
    if (pivot.isEmpty() || methods.isEmpty()) return ""
    val flat = pivot.values.flatMap { it.values }
    if (flat.isEmpty()) return ""
    val unit = flat.first().primaryMetric.scoreUnit

    val samples = mutableListOf<String>()
    val methodCol = mutableListOf<String>()
    val scores = mutableListOf<Double>()
    pivot.forEach { (sample, byMethod) ->
        methods.forEach { method ->
            val r = byMethod[method] ?: return@forEach
            samples += sample
            methodCol += method
            scores += r.primaryMetric.score
        }
    }

    return plot {
        bars {
            x(samples) { axis.name = "" }
            y(scores) { axis.name = "$unit (lower is better)" }
            fillColor(methodCol) {
                scale = categorical(
                    *methods.mapIndexed { i, m -> m to Color.hex(colorFor(m, i)) }.toTypedArray(),
                )
                legend.name = ""
            }
        }
        layout {
            size = 720 to 360
        }
    }.toSVG()
}

private fun JmhResult.benchmarkClass(): String =
    benchmark.substringBeforeLast('.').substringAfterLast('.')
private fun JmhResult.method(): String = benchmark.substringAfterLast('.')
private fun JmhResult.sample(): String = params["sample"] ?: "—"

private fun Instant.toHuman(): String =
    atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd · HH:mm z"))

private fun String.formatRoot(vararg args: Any?): String = format(Locale.ROOT, *args)

@Suppress("CssUnresolvedCustomProperty")
@Language("CSS")
private val CSS = """
:root {
  --ink:        #0F0F10;
  --ink-soft:   #2B2B2F;
  --muted:      #6B6D73;
  --faint:      #A8A9AE;
  --bg:         #FAFAF7;
  --bg-alt:     #F2F2EE;
  --surface:    #FFFFFF;
  --rule:       rgba(15,15,16,0.12);
  --rule-hair:  rgba(15,15,16,0.06);
  --accent:     #C1440E;
  --accent-bg:  rgba(193,68,14,0.06);
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
a { color: var(--accent); text-decoration: none; border-bottom: 1px solid transparent; transition: border-color .15s ease; }
a:hover { border-bottom-color: var(--accent); }

.doc-head { background: var(--surface); border-bottom: 2px solid var(--ink); }
.doc-head-inner { max-width: 1180px; margin: 0 auto; padding: 1.5rem 2rem 1.2rem; }
.doc-title {
  font-family: var(--mono); font-weight: 600;
  font-size: clamp(1.7rem, 3.4vw, 2.4rem);
  letter-spacing: -0.03em; color: var(--ink); line-height: 1.05; margin-bottom: .7rem;
}
.doc-meta { display: flex; flex-wrap: wrap; gap: .35rem 1.1rem; font-family: var(--mono); font-size: .74rem; color: var(--ink-soft); }
.doc-meta-pair { display: inline-flex; gap: .4rem; }
.doc-meta-pair .k {
  color: var(--muted); text-transform: uppercase; letter-spacing: .12em;
  font-size: .68rem; font-weight: 600; line-height: 1.6;
}
.doc-meta-pair .v { color: var(--ink); font-weight: 500; font-variant-numeric: tabular-nums; }

main { max-width: 1180px; margin: 0 auto; padding: 2rem 2rem 2.5rem; }

.section { margin-bottom: 2.4rem; position: relative; }
.section-head {
  padding: 1rem 0 .8rem; margin-bottom: 1.1rem;
  border-top: 1px solid var(--ink);
}
.section-title {
  font-family: var(--mono); font-weight: 600;
  font-size: 1.05rem; letter-spacing: -0.01em; line-height: 1.25;
  color: var(--ink); text-transform: lowercase;
  white-space: pre-wrap;
}
.section-title::before { content: '# '; color: var(--faint); font-weight: 400; }
.section-strap { margin-top: .25rem; font-family: var(--sans); font-size: .82rem; color: var(--muted); max-width: 64ch; line-height: 1.5; }
.section-body h3 {
  font-family: var(--mono); font-size: .68rem; font-weight: 700;
  text-transform: uppercase; letter-spacing: .18em; color: var(--muted);
  margin-bottom: .7rem; padding-bottom: .35rem;
  border-bottom: 1px solid var(--rule-hair);
}
.prose { font-size: .92rem; color: var(--ink-soft); margin-bottom: .8rem; }
.prose code {
  font-family: var(--mono); font-size: .82rem;
  color: var(--ink); background: var(--bg-alt);
  padding: 1px 5px; border-radius: 2px;
}

.metrics {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  background: var(--surface); border: 1px solid var(--rule);
}
.metric { padding: 1rem 1.2rem; border-left: 1px solid var(--rule-hair); display: flex; flex-direction: column; justify-content: center; min-height: 92px; }
.metric:first-child { border-left: 0; }
.metric-label {
  font-family: var(--mono); font-size: .66rem;
  text-transform: uppercase; letter-spacing: .18em;
  color: var(--muted); font-weight: 600; margin-bottom: .35rem;
}
.metric-value {
  font-family: var(--mono); font-size: 1.8rem; font-weight: 600;
  line-height: 1; letter-spacing: -0.02em;
  color: var(--ink); font-variant-numeric: tabular-nums;
}
.metric-unit { font-family: var(--mono); font-size: .8rem; font-weight: 500; color: var(--muted); margin-left: .1rem; letter-spacing: 0; }
.metric-sub { margin-top: .3rem; font-size: .72rem; color: var(--muted); font-family: var(--mono); }

.sysinfo-grid {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  background: var(--surface); border: 1px solid var(--rule);
}
.sysinfo-block { padding: 1rem 1.2rem; border-left: 1px solid var(--rule-hair); }
.sysinfo-block:first-child { border-left: 0; }
.sysinfo-block dl { display: grid; grid-template-columns: max-content 1fr; gap: .3rem .8rem; font-size: .8rem; }
.sysinfo-block dt { color: var(--muted); font-size: .72rem; font-family: var(--mono); font-weight: 500; letter-spacing: .02em; }
.sysinfo-block dd { color: var(--ink); font-family: var(--sans); font-size: .82rem; word-break: break-all; }
.mono { font-family: var(--mono); font-size: .76rem; }

.bars-wrap {
  background: var(--surface);
  border: 1px solid var(--rule);
  padding: .9rem 1rem;
  margin-bottom: 1.2rem;
  max-width: 720px;
}
.bars-wrap svg { display: block; }
.grid { stroke: var(--rule-hair); stroke-width: 1; }
.baseline { stroke: var(--ink); stroke-width: 1; }
.tick { font-family: var(--mono); font-size: 11px; fill: var(--muted); font-variant-numeric: tabular-nums; }
.axis-label { font-family: var(--mono); font-size: 10px; fill: var(--muted); text-transform: uppercase; letter-spacing: .12em; }
.group-label { font-family: var(--mono); font-size: 11px; fill: var(--ink-soft); font-variant-numeric: tabular-nums; }
.bar-label { font-family: var(--mono); font-size: 11px; fill: var(--ink); font-variant-numeric: tabular-nums; }
.err-line { stroke: var(--ink); stroke-width: 1; }

.legend { display: flex; gap: 1.4rem; flex-wrap: wrap; margin-top: -.5rem; margin-bottom: 1.2rem; font-family: var(--mono); font-size: .72rem; color: var(--ink-soft); }
.legend-item { display: inline-flex; align-items: center; gap: .5rem; }
.swatch { display: inline-block; width: 10px; height: 10px; background: var(--muted); flex-shrink: 0; }
.legend-label { letter-spacing: .04em; }

table { border-collapse: collapse; width: 100%; }
.table-scroll { overflow-x: auto; margin: 0 -.75rem; padding: 0 .75rem; }
thead th {
  text-align: left; font-family: var(--mono); font-weight: 600;
  font-size: .68rem; text-transform: uppercase; letter-spacing: .18em;
  color: var(--muted); padding: .5rem .75rem;
  border-bottom: 1px solid var(--ink); background: transparent;
}
thead th.num { text-align: right; }
thead th .unit { color: var(--faint); font-weight: 500; letter-spacing: .04em; }
td { padding: .55rem .75rem; border-bottom: 1px solid var(--rule-hair); vertical-align: middle; font-size: .85rem; }
tbody tr:hover { background: rgba(193,68,14,0.04); }
td.name { font-family: var(--sans); font-weight: 500; }
td.num { text-align: right; font-variant-numeric: tabular-nums; font-family: var(--mono); font-size: .82rem; color: var(--ink); }
td.num .err { color: var(--muted); font-size: .75rem; }
td.num .ratio { color: var(--accent); font-weight: 600; }
td.num .ratio-note { color: var(--muted); font-family: var(--mono); font-size: .72rem; letter-spacing: .04em; margin-left: .35rem; text-transform: lowercase; }

.colophon { margin-top: 3rem; border-top: 1px solid var(--rule); background: var(--surface); }
.colophon-inner {
  max-width: 1180px; margin: 0 auto; padding: 1.1rem 2rem 1.3rem;
  font-family: var(--mono); font-size: .7rem; letter-spacing: .06em;
  color: var(--muted); display: flex; gap: .5rem; flex-wrap: wrap; align-items: center;
}
.colophon-inner .sep { color: var(--faint); }
.colophon-spacer { flex: 1; }
.colophon-meta { color: var(--faint); font-size: .66rem; letter-spacing: .02em; }

@media (max-width: 720px) {
  html { font-size: 14px; }
  .doc-head-inner, main, .colophon-inner { padding-left: 1.1rem; padding-right: 1.1rem; }
  .sysinfo-grid, .metrics { grid-template-columns: 1fr; }
  .sysinfo-block, .metric { border-left: 0; border-top: 1px solid var(--rule-hair); }
  .sysinfo-block:first-child, .metric:first-child { border-top: 0; }
}

@media print {
  body { background: #fff; }
  .section { break-inside: avoid; }
  a { color: var(--ink); border-bottom: 0; }
}
""".trimIndent()
