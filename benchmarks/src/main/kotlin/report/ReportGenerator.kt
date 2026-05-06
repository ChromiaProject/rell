/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.benchmarks.report

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlinx.kandy.dsl.categorical
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.toSVG
import org.jetbrains.kotlinx.kandy.letsplot.feature.layout
import org.jetbrains.kotlinx.kandy.letsplot.layers.bars
import org.jetbrains.kotlinx.kandy.letsplot.scales.guide.LegendType
import org.jetbrains.kotlinx.kandy.util.color.Color
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
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

private data class SampleSource(val label: String, val url: String)

private data class SampleInfo(
    val title: String,
    val description: String,
    val sources: List<SampleSource>,
)

private const val FT4_BLOB = "https://gitlab.com/chromaway/ft4-lib/-/blob/development/rell/src/lib/ft4"
private const val MNA_BLOB = "https://bitbucket.org/chromawallet/mna-blockchain/src/development/rell/src"

/**
 * Per-sample editorial copy: a human-readable title, a short description of what the workload
 * actually computes (so a reader can tell whether a slow result matters), and pointers to the
 * upstream source. The keys must match the JMH `sample` parameter values reported in the JSON.
 */
private val SAMPLE_INFO: Map<String, SampleInfo> = mapOf(
    "collatz_primes_fib" to SampleInfo(
        title = "Collatz · primality · Fibonacci",
        description = "Sums Collatz sequence lengths for every prime in [2, 100_000], then adds the 20th Fibonacci number.",
        sources = listOf(
            SampleSource(
                "synthetic_bench/main.rell",
                "https://gitlab.com/chromaway/rell/-/blob/dev/benchmarks/src/main/resources/synthetic_bench/main.rell",
            ),
        ),
    ),
    "gtv_text" to SampleInfo(
        title = "convert_gtv_to_text",
        description = "Pretty-prints a mid-sized nested gtv (lists, dicts, ints, byte_arrays, booleans) 200 times.",
        sources = listOf(
            SampleSource("ft4-lib · utils/utils.rell", "$FT4_BLOB/utils/utils.rell"),
        ),
    ),
    "rule_serde" to SampleInfo(
        title = "auth descriptor rule serde",
        description = "500 round-trips of a five-rule list: serialize_rules to gtv, then map_rule_expressions_from_gtv back.",
        sources = listOf(
            SampleSource(
                "ft4-lib · auth_descriptor_rule_validation.rell",
                "$FT4_BLOB/core/accounts/auth_descriptor_rule_validation.rell",
            ),
            SampleSource(
                "ft4-lib · auth_descriptor_rule_expression.rell",
                "$FT4_BLOB/core/accounts/auth_descriptor_rule_expression.rell",
            ),
        ),
    ),
    "rule_eval" to SampleInfo(
        title = "auth descriptor rule evaluation",
        description = "5_000 calls of is_rule_violated against a fixed five-rule set and a map<text, gtv> of variable values.",
        sources = listOf(
            SampleSource(
                "ft4-lib · auth_descriptor_rule_validation.rell",
                "$FT4_BLOB/core/accounts/auth_descriptor_rule_validation.rell",
            ),
        ),
    ),
    "decimal_pow" to SampleInfo(
        title = "decimal power · ln · exp",
        description = "200 calls × 4 (base, exponent) shapes of power: integer exponent, non-integer exponent, fractional base near 1, and negative exponent.",
        sources = listOf(
            SampleSource("mna-blockchain · math/math.rell", "$MNA_BLOB/math/math.rell"),
        ),
    ),
    "perlin_noise" to SampleInfo(
        title = "8-octave 2D Simplex noise",
        description = "20 calls of sum_octave_2d on a 5×5 grid (500 simplex_2d samples per rep) with the canonical permutation tables.",
        sources = listOf(
            SampleSource("mna-blockchain · noise/functions.rell", "$MNA_BLOB/math/noise/functions.rell"),
            SampleSource("mna-blockchain · noise/simplex_2d.rell", "$MNA_BLOB/math/noise/simplex_2d.rell"),
        ),
    ),
    "locations" to SampleInfo(
        title = "rotate · area_for_locations",
        description = "200 reps × 4 cardinal rotations of a 64-point list<location>; each rotated copy is collected into a set<location> and reduced via area_for_locations.",
        sources = listOf(
            SampleSource(
                "mna-blockchain · griddables/griddables.rell",
                "$MNA_BLOB/griddables/griddables.rell",
            ),
        ),
    ),
)

private fun sampleInfo(sample: String): SampleInfo? = SAMPLE_INFO[sample]
private fun sampleTitle(sample: String): String = sampleInfo(sample)?.title ?: sample

private val METHOD_COLOR = mapOf(
    "antlr" to ACCENT,
    "antlrSLL" to "#1F6B4A",
    "betterParse" to "#0F0F10",
    "runQuery" to ACCENT,
    "interpreter" to ACCENT,
    "truffle" to "#2E5E8A",
    // InterpreterBenchmark variant labels — `runQuery[<backend>]` after the report's
    // `${method}[${variant}]` collapse, plus the bare backend names emitted when the
    // benchmark class only has one method.
    "runQuery[interpreter]" to ACCENT,
    "runQuery[truffle]" to "#2E5E8A",
)

private val PALETTE = listOf(ACCENT, "#2E5E8A", "#1F6B4A", "#8A5E2E", "#5E2E8A", "#B07A1E")

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
        renderDocHead()
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
    val distinctMethods = results.map { it.method() }.distinct()
    val variantOf: (JmhResult) -> String = { r ->
        val extra = r.params.filterKeys { it != "sample" }.toSortedMap()
        when {
            extra.isEmpty() -> r.method()
            distinctMethods.size == 1 -> extra.values.joinToString("/")
            else -> "${r.method()}[${extra.values.joinToString("/")}]"
        }
    }
    val samples = results.map { it.sample() }.distinct()
    val variants = results.map(variantOf).distinct().sorted()
    val pivot: Map<String, Map<String, JmhResult>> = samples.associateWith { sample ->
        variants.mapNotNull { variant ->
            results.firstOrNull { it.sample() == sample && variantOf(it) == variant }?.let { variant to it }
        }.toMap()
    }
    renderResults(benchmarkClass, pivot, variants, results)
}

private fun FlowContent.renderDocHead() = header(classes = "doc-head") {
    div(classes = "doc-head-inner") { h1(classes = "doc-title") { +"Rell Benchmarks" } }
}

private inline fun FlowContent.renderSection(
    title: String,
    strap: String = "",
    crossinline body: FlowContent.() -> Unit,
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
    allResults: List<JmhResult>,
) {
    val unit = pivot.values.flatMap { it.values }
        .map { it.primaryMetric.scoreUnit }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    val head = allResults.first()
    val timing = "warmup ${head.warmupIterations}× ${head.warmupTime ?: "—"} · " +
        "measurement ${head.measurementIterations}× ${head.measurementTime ?: "—"}"
    val strap = buildList {
        if (unit.isNotEmpty()) add("$unit · lower is better")
        add(timing)
    }.joinToString("  ·  ")
    renderSection(benchmarkClass.lowercase(), strap) {
        div(classes = "bars-grid") {
            pivot.forEach { (sample, byMethod) ->
                val svg = renderSamplePanel(byMethod, methods)
                if (svg.isNotEmpty()) {
                    val info = sampleInfo(sample)
                    div(classes = "bars-panel") {
                        div(classes = "bars-panel-title") { +sampleTitle(sample) }
                        if (info != null) {
                            div(classes = "bars-panel-desc") { +info.description }
                            if (info.sources.isNotEmpty()) {
                                ul(classes = "bars-panel-sources") {
                                    info.sources.forEach { src ->
                                        li {
                                            a(href = src.url) {
                                                attributes["target"] = "_blank"
                                                attributes["rel"] = "noopener"
                                                +src.label
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        div(classes = "bars-panel-svg") { unsafe { +svg } }
                    }
                }
            }
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
                        val winner = methods
                            .mapNotNull { m -> byMethod[m]?.primaryMetric?.score?.let { m to it } }
                            .filter { it.second > 0 }
                            .minByOrNull { it.second }
                            ?.first
                            ?.takeIf { byMethod.values.count { it.primaryMetric.score > 0 } > 1 }
                        tr {
                            td(classes = "name") { +sampleTitle(sample) }
                            methods.forEach { method ->
                                val r = byMethod[method]
                                val cls = if (method == winner) "num winner" else "num"
                                td(classes = cls) {
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

private fun FlowContent.renderColophon(source: File, generatedAt: Instant) = footer(classes = "colophon") {
    div(classes = "colophon-inner") {
        span { a(href = "https://gitlab.com/chromaway/rell") { +"chromaway/rell" } }
        span(classes = "colophon-spacer") {}
        span(classes = "colophon-meta") { +source.name }
        span(classes = "sep") { +"·" }
        span(classes = "colophon-meta") { +generatedAt.toHuman() }
    }
}

private fun renderSamplePanel(
    byMethod: Map<String, JmhResult>,
    methods: List<String>,
): String {
    val present = methods.filter { byMethod[it] != null }
    if (present.isEmpty()) return ""
    val methodCol = present.toMutableList()
    val scores = present.map { byMethod.getValue(it).primaryMetric.score }.toMutableList()

    return plot {
        bars {
            x(methodCol) { axis.name = "" }
            y(scores) { axis.name = "" }
            fillColor(methodCol) {
                scale = categorical(
                    *methods.mapIndexed { i, m -> m to Color.hex(colorFor(m, i)) }.toTypedArray(),
                )
                legend.type = LegendType.None
            }
        }
        layout {
            size = 260 to 160
        }
    }.toSVG()
}

private fun JmhResult.benchmarkClass(): String = benchmark.substringBeforeLast('.').substringAfterLast('.')
private fun JmhResult.method(): String = benchmark.substringAfterLast('.')
private fun JmhResult.sample(): String = params["sample"] ?: "—"

private fun Instant.toHuman(): String =
    atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd · HH:mm z"))

private fun String.formatRoot(vararg args: Any?): String = format(Locale.ROOT, *args)

@Suppress("CssUnresolvedCustomProperty", "CssUnusedSymbol", "CssNoGenericFontName")
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

.timing {
  background: var(--surface); border: 1px solid var(--rule);
  padding: .9rem 1.2rem; margin-bottom: 1rem;
}
.timing h3 {
  font-family: var(--mono); font-size: .68rem; font-weight: 700;
  text-transform: uppercase; letter-spacing: .18em; color: var(--muted);
  margin-bottom: .55rem; padding-bottom: .3rem;
  border-bottom: 1px solid var(--rule-hair);
}
.timing dl { display: grid; grid-template-columns: max-content 1fr; gap: .25rem .8rem; font-size: .8rem; }
.timing dt { color: var(--muted); font-size: .72rem; font-family: var(--mono); font-weight: 500; letter-spacing: .02em; }
.timing dd { color: var(--ink); font-family: var(--sans); font-size: .82rem; }

.bars-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: .4rem;
  background: var(--surface);
  border: 1px solid var(--rule);
  padding: .9rem 1rem;
  margin-bottom: 1.2rem;
}
.bars-panel { display: flex; flex-direction: column; min-width: 0; }
.bars-panel-title {
  font-family: var(--mono); font-size: .78rem; font-weight: 600;
  text-transform: lowercase; letter-spacing: .04em;
  color: var(--ink); padding: .2rem .25rem .35rem;
  border-bottom: 1px solid var(--rule-hair); margin-bottom: .45rem;
}
.bars-panel-desc {
  font-family: var(--sans); font-size: .76rem; line-height: 1.45;
  color: var(--ink-soft); padding: 0 .25rem .5rem; margin-bottom: .15rem;
}
.bars-panel-sources {
  list-style: none; padding: 0 .25rem .55rem; margin: 0 0 .25rem;
  border-bottom: 1px dashed var(--rule-hair);
  font-family: var(--mono); font-size: .68rem;
}
.bars-panel-sources li { margin: .12rem 0; overflow-wrap: anywhere; }
.bars-panel-sources a { color: var(--accent); }
.bars-panel-svg svg { display: block; max-width: 100%; height: auto; }
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
td.num.winner { font-weight: 700; color: var(--accent); }
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
