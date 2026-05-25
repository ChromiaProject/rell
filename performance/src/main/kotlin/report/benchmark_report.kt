/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:JvmName("BenchmarkReportKt")

package net.postchain.rell.performance.report

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import org.intellij.lang.annotations.Language
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.*
import kotlin.system.exitProcess

private data class JmhMetric(
    val score: Double = 0.0,
    val scoreError: Double = 0.0,
    // 95% CI [lo, hi] reported by JMH for AverageTime mode. Used by the AocBenchmark forest
    // plot, which renders CI whiskers rather than bars + ±err (some AoC rows have a CI several
    // times wider than the mean — a bar tip hides that, a whisker exposes it).
    val scoreConfidence: List<Double> = emptyList(),
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

private data class SampleSource(val label: String, val url: String)

private data class SampleInfo(
    val title: String,
    val description: String,
    val sources: List<SampleSource>,
    // GitLab URL (with `#L<line>` anchor) pointing at the in-repo query this sample drives.
    // The URL targets the `dev` branch, so it becomes valid as soon as the bench file lands
    // there. Rendered as a hyperlink on the sample title in the result table.
    val benchUrl: String? = null,
)

private const val FT4_BLOB = "https://gitlab.com/chromaway/ft4-lib/-/blob/development/rell/src/lib/ft4"
private const val RELL_BENCH_BLOB = "$RELL_BLOB/performance/src/main/resources"

// Line numbers of the AoC bench queries — kept as a table so they round-trip cleanly with
// `grep -nE 'query bench_'` if the file gets reshuffled.
private val AOC_QUERY_LINE = mapOf(
    "day1a" to 58, "day1b" to 69,
    "day2a" to 131, "day2b" to 142,
    "day3a" to 187, "day3b" to 197,
    "day4a" to 276, "day4b" to 287,
    "day5a" to 390, "day5b" to 401,
    "day6a" to 535, "day6b" to 546,
    "day7a" to 660, "day7b" to 671,
)

/** Keys must match the JMH `sample` parameter values in the JSON. */
private val SAMPLE_INFO: Map<String, SampleInfo> = mapOf(
    "collatz_primes_fib" to SampleInfo(
        title = "Collatz · primality · Fibonacci",
        description = "Sums Collatz sequence lengths for every prime in [2, 100_000], then adds the 20th Fibonacci number.",
        sources = emptyList(),
        benchUrl = "$RELL_BENCH_BLOB/synthetic_bench/main.rell#L34",
    ),
    "gtv_text" to SampleInfo(
        title = "convert_gtv_to_text",
        description = "Pretty-prints a mid-sized nested gtv (lists, dicts, ints, byte_arrays, booleans) 200 times.",
        sources = listOf(
            SampleSource("ft4-lib · utils/utils.rell", "$FT4_BLOB/utils/utils.rell"),
        ),
        benchUrl = "$RELL_BENCH_BLOB/ft4_bench/main.rell#L169",
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
        benchUrl = "$RELL_BENCH_BLOB/ft4_bench/main.rell#L188",
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
        benchUrl = "$RELL_BENCH_BLOB/ft4_bench/main.rell#L211",
    ),
    "decimal_pow" to SampleInfo(
        title = "decimal power · ln · exp",
        description = "200 calls × 4 (base, exponent) shapes of power: integer exponent, non-integer exponent, fractional base near 1, and negative exponent. Note: ≳60% of wall time is spent in JDK BigInteger / BigDecimal arithmetic — small Rell-runtime wins translate to small ms/op deltas here. Distilled from chromaway/mna-blockchain (closed source).",
        sources = emptyList(),
        benchUrl = "$RELL_BENCH_BLOB/mna_bench/main.rell#L314",
    ),
    "perlin_noise" to SampleInfo(
        title = "8-octave 2D Simplex noise",
        description = "20 calls of sum_octave_2d on a 5×5 grid (500 simplex_2d samples per rep) with the canonical permutation tables. Note: ~40-50% of wall time is JDK BigDecimal / BigInteger arithmetic; the truffle backend's int128/long-scale decimal leaves shave a chunk off when values stay in range. Distilled from chromaway/mna-blockchain (closed source).",
        sources = emptyList(),
        benchUrl = "$RELL_BENCH_BLOB/mna_bench/main.rell#L340",
    ),
    "locations" to SampleInfo(
        title = "rotate · area_for_locations",
        description = "200 reps × 4 cardinal rotations of a 64-point list<location>; each rotated copy is collected into a set<location> and reduced via area_for_locations. Distilled from chromaway/mna-blockchain (closed source).",
        sources = emptyList(),
        benchUrl = "$RELL_BENCH_BLOB/mna_bench/main.rell#L389",
    ),
    "dto_mapping" to SampleInfo(
        title = "nested DTO mapping",
        description = "50 reps × 16 quests × (3 goals × 4 assets) × 4 rewards — triple-nested struct construction in a tight loop, mirroring the quests/community_quests get_*_dto path. Distilled from chromaway/mna-blockchain (closed source).",
        sources = emptyList(),
        benchUrl = "$RELL_BENCH_BLOB/struct_bench/main.rell#L149",
    ),
    "cursor_codec" to SampleInfo(
        title = "page_cursor codec · paged_result",
        description = "100 reps of cursor encode (to_bytes + base64) plus paged_result assembly over a 32-row pagination_result. Adapted from ft4-lib make_page; preserves the struct-ser hot path.",
        sources = listOf(
            SampleSource(
                "ft4-lib · utils/pagination.rell",
                "$FT4_BLOB/utils/pagination.rell",
            ),
        ),
        benchUrl = "$RELL_BENCH_BLOB/struct_bench/main.rell#L235",
    ),
    "multi_sig" to SampleInfo(
        title = "multi-sig auth descriptor eval",
        description = "500 reps of multi-sig signer-list evaluation: struct deserialization (to_struct from gtv), set/list intersection over the candidate signer set, and an early-exit signer loop.",
        sources = listOf(
            SampleSource(
                "ft4-lib · core/accounts/auth_basic.rell",
                "$FT4_BLOB/core/accounts/auth_basic.rell",
            ),
        ),
        benchUrl = "$RELL_BENCH_BLOB/struct_bench/main.rell#L336",
    ),
) + AOC_QUERY_LINE.mapValues { (key, line) ->
    SampleInfo(
        title = key,
        description = "AoC 2024 — direct port of github.com/mikaelstaldal/AdventOfCode2024 day part.",
        sources = emptyList(),
        benchUrl = "$RELL_BENCH_BLOB/aoc_bench/main.rell#L$line",
    )
}

private fun sampleInfo(sample: String): SampleInfo? = SAMPLE_INFO[sample]
private fun sampleTitle(sample: String): String = sampleInfo(sample)?.title ?: sample

/**
 * Renders the sample-name cell in a result table: the title becomes a hyperlink to the bench
 * query (when `benchUrl` is set), and any additional `sources` (e.g. ft4-lib functions under
 * test) appear as a small inline list directly under it.
 */
private fun FlowContent.sampleNameCell(sample: String) {
    val info = sampleInfo(sample)
    val title = info?.title ?: sample
    val href = info?.benchUrl
    if (href != null) {
        a(href = href, classes = "sample-link") {
            attributes["target"] = "_blank"
            attributes["rel"] = "noopener"
            +title
        }
    } else {
        +title
    }
    if (info?.sources?.isNotEmpty() == true) {
        ul(classes = "sample-cell-sources") {
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

private val METHOD_COLOR = mapOf(
    "antlr" to ACCENT_HEX,
    "antlrSLL" to "#1F6B4A",
    "betterParse" to INK_HEX,
    "runQuery" to ACCENT_HEX,
    "interpreter" to ACCENT_HEX,
    "truffle" to "#2E5E8A",
    "kotlin" to "#1F6B4A",
    "runQuery[interpreter]" to ACCENT_HEX,
    "runQuery[truffle]" to "#2E5E8A",
    "runQuery[kotlin]" to "#1F6B4A",
)

private val PALETTE = listOf(ACCENT_HEX, "#2E5E8A", "#1F6B4A", "#8A5E2E", "#5E2E8A", "#B07A1E")

private const val DEFAULT_COLOR = "#6B6D73"

private fun colorFor(method: String, fallbackIndex: Int): String =
    METHOD_COLOR[method] ?: PALETTE.getOrElse(fallbackIndex) { DEFAULT_COLOR }

fun main(args: Array<String>) {
    val parsed = parseArgs(args)
    val input = Path(parsed.input).also {
        require(it.isRegularFile()) { "Input JSON does not exist: $it" }
    }
    val output = Path(parsed.output)
    output.parent?.createDirectories()

    val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    val results: List<JmhResult> = mapper.readValue(
        input.inputStream(),
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
    println("Wrote HTML report: ${output.absolutePathString()}")
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

private fun HTML.renderReport(results: List<JmhResult>, source: Path, generatedAt: Instant) {
    val head = results.first()
    val byClass: Map<String, List<JmhResult>> = results.groupBy { it.benchmarkClass() }
        .toSortedMap()
    val totalSamples = results.map { it.benchmarkClass() to it.sample() }.distinct().size
    val totalMethods = results.map { it.benchmarkClass() to it.method() }.distinct().size

    attributes["lang"] = "en"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        titleWithTimestamp("Rell Benchmarks", generatedAt)
        linkWebFonts()
        style { unsafe { +(BASE_CSS + EXTRA_CSS) } }
    }
    body {
        renderDocHead("Rell Benchmarks")
        main {
            renderEnvironment(head)
            renderMetrics(results, totalSamples, totalMethods, byClass.size)
            byClass.forEach { (cls, group) -> renderGroup(cls, group) }
            renderMethodology(head)
        }
        renderColophon(source.name, generatedAt)
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
    if (benchmarkClass == "AocBenchmark") {
        renderAocResults(benchmarkClass, pivot, variants, results)
    } else {
        renderResults(benchmarkClass, pivot, variants, results)
    }
}

private fun FlowContent.renderEnvironment(head: JmhResult) = renderSection("environment") {
    // Host + JVM info come from System.getProperty / InetAddress at report-generation time.
    // The benchmarkHtmlReport task pins to JDK 21 via javaToolchains, the same toolchain that
    // runs mainBenchmark — so vendor/host read here reflect the JVM that actually executed JMH.
    // JMH's JSON carries jvm path, jdkVersion, vmName, vmVersion but no vendor, hence the
    // System.getProperty path for vendor and the JMH-JSON path for the version strings.
    val host = HostInfo.fromSystem()
    val jvm = JvmInfo.fromSystem(jvmPath = head.jvm).copy(
        runtimeVersion = head.jdkVersion ?: System.getProperty("java.runtime.version") ?: "—",
        vmName = head.vmName ?: System.getProperty("java.vm.name") ?: "—",
        vmVersion = head.vmVersion ?: System.getProperty("java.vm.version") ?: "—",
    )
    div(classes = "sysinfo-grid") {
        hostBlock(host)
        jvmBlock(jvm, pathLabel = "Path")
        sysinfoBlock("Harness") {
            dlRow("JMH", head.jmhVersion ?: "—")
            dlRow("Mode", head.mode.ifBlank { "—" })
            dlRow("Threads / forks", "${head.threads} / ${head.forks}")
            dlRow("Unit", head.primaryMetric.scoreUnit.ifBlank { "—" })
        }
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
                            // Source links used to live here; they are now rendered in the
                            // result table next to the sample name, where the deep-link is
                            // closer to the row it belongs to.
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
                    }
                }
                tbody {
                    pivot.forEach { (sample, byMethod) ->
                        val positiveScores = methods.mapNotNull { m ->
                            byMethod[m]?.primaryMetric?.score?.takeIf { it > 0 }?.let { m to it }
                        }
                        val best = positiveScores.minByOrNull { it.second }
                        val winner = best?.first?.takeIf { positiveScores.size > 1 }
                        val bestScore = best?.second?.takeIf { positiveScores.size > 1 }
                        tr {
                            td(classes = "name") { sampleNameCell(sample) }
                            methods.forEach { method ->
                                val r = byMethod[method]
                                val cls = if (method == winner) "num winner" else "num"
                                td(classes = cls) {
                                    if (r != null) {
                                        val m = r.primaryMetric
                                        +formatScoreBench(m.score)
                                        span(classes = "err") { +" ± ${formatScoreBench(m.scoreError)}" }
                                        if (bestScore != null && winner != null && method != winner && m.score > 0) {
                                            span(classes = "ratio-note") {
                                                +" "
                                                span(classes = "ratio") { +"×%.1f".formatRoot(m.score / bestScore) }
                                                +" $winner"
                                            }
                                        }
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

/**
 * AocBenchmark renderer: a slopegraph (three engine columns, 14 sample lines, shared log10
 * y-axis) plus a headline strip showing median slowdown vs the Kotlin baseline.
 *
 * Why a slopegraph and not the per-sample forest plot: the 14 AoC samples are independent
 * replications of the same comparison (kotlin vs truffle vs interpreter), so day is a
 * nuisance variable. The slopegraph marginalizes it — every day becomes one faint line in a
 * bundle, and the *consistency* of the ×22 / ×44 ratio reads as a tight fan of near-parallel
 * lines. The bold median path running through the bundle anchors the headline ratio
 * visually; outliers (samples whose ratio diverges from the median) show up as lines that
 * cross or fan away.
 */
private fun FlowContent.renderAocResults(
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
        if (unit.isNotEmpty()) add("$unit · log y · lower is better · day marginalized")
        add(timing)
    }.joinToString("  ·  ")

    // Fastest-first ordering. `methods` here is the variant axis (backend names) from the
    // pivot; intersecting with this preserves "kotlin / truffle / interpreter" only if the
    // data actually contains them.
    val ordered = listOf("kotlin", "truffle", "interpreter").filter { it in methods }
    val samples = pivot.keys.toList()

    // Global log-y range across every score, snapped to whole decades. CIs are intentionally
    // ignored: the slopegraph hides day-level uncertainty by design — the question is "is the
    // ×22 / ×44 ratio consistent across days", not "how wide is each day's CI".
    val ys = pivot.values.flatMap { it.values.map { r -> r.primaryMetric.score } }
        .filter { it.isFinite() && it > 0.0 }
    val yMinLog = if (ys.isEmpty()) -3.0 else kotlin.math.floor(kotlin.math.log10(ys.min()))
    val yMaxLog = if (ys.isEmpty()) 2.0 else kotlin.math.ceil(kotlin.math.log10(ys.max()))

    // Median ratio vs kotlin, per non-kotlin engine. Median of *ratios*, not ratio of medians:
    // each sample's per-engine slowdown contributes one number to the median, which makes the
    // headline robust to a single outlier day.
    val kotlinByS: Map<String, Double> = pivot.mapValues { (_, byM) ->
        byM["kotlin"]?.primaryMetric?.score ?: Double.NaN
    }
    val medianRatio: Map<String, Double?> = ordered.associateWith { backend ->
        if (backend == "kotlin") null
        else {
            val ratios = samples.mapNotNull { s ->
                val k = kotlinByS[s] ?: return@mapNotNull null
                val v = pivot[s]?.get(backend)?.primaryMetric?.score ?: return@mapNotNull null
                if (k > 0 && v > 0) v / k else null
            }.sorted()
            if (ratios.isEmpty()) null
            else if (ratios.size % 2 == 1) ratios[ratios.size / 2]
            else (ratios[ratios.size / 2 - 1] + ratios[ratios.size / 2]) / 2.0
        }
    }

    // Median score per engine across the 14 samples — the path of the bold median line drawn
    // through the bundle. Marginalizes day, which is the entire point.
    val medianScoreByBackend: Map<String, Double?> = ordered.associateWith { backend ->
        val scores = samples.mapNotNull { s ->
            pivot[s]?.get(backend)?.primaryMetric?.score?.takeIf { it.isFinite() && it > 0 }
        }.sorted()
        if (scores.isEmpty()) null
        else if (scores.size % 2 == 1) scores[scores.size / 2]
        else (scores[scores.size / 2 - 1] + scores[scores.size / 2]) / 2.0
    }

    renderSection(benchmarkClass.lowercase(), strap) {
        // Headline strip: one cell per engine. Kotlin is "baseline"; the others get the
        // median slowdown ratio, which is exactly the question the user keeps asking.
        div(classes = "aoc-headline") {
            ordered.forEach { backend ->
                val ratio = medianRatio[backend]
                div(classes = "aoc-headline-cell") {
                    span(classes = "aoc-headline-swatch") {
                        attributes["style"] = "background:${colorFor(backend, ordered.indexOf(backend))}"
                    }
                    span(classes = "aoc-headline-engine") { +backend }
                    if (ratio != null) {
                        span(classes = "aoc-headline-ratio") { +"×${formatRatio(ratio)}" }
                        span(classes = "aoc-headline-note") { +"slower than kotlin (median)" }
                    } else {
                        span(classes = "aoc-headline-note") { +"baseline" }
                    }
                }
            }
        }
        // Single slopegraph: 14 faint lines (one per sample) + one bold median path.
        div(classes = "aoc-slope") {
            unsafe { +aocSlopegraphSvg(pivot, samples, ordered, yMinLog, yMaxLog, medianScoreByBackend) }
        }
        // Reuse the existing table layout below the plot — different question (exact numbers,
        // per-row winner) so it's worth keeping alongside the visual.
        div(classes = "table-scroll") {
            table(classes = "results") {
                thead {
                    tr {
                        th { +"Sample" }
                        ordered.forEach { method ->
                            th(classes = "num") {
                                +method
                                if (unit.isNotEmpty()) span(classes = "unit") { +" $unit" }
                            }
                        }
                    }
                }
                tbody {
                    pivot.forEach { (sample, byMethod) ->
                        val positiveScores = ordered.mapNotNull { m ->
                            byMethod[m]?.primaryMetric?.score?.takeIf { it > 0 }?.let { m to it }
                        }
                        val best = positiveScores.minByOrNull { it.second }
                        val winner = best?.first?.takeIf { positiveScores.size > 1 }
                        val bestScore = best?.second?.takeIf { positiveScores.size > 1 }
                        tr {
                            td(classes = "name") { sampleNameCell(sample) }
                            ordered.forEach { method ->
                                val r = byMethod[method]
                                val cls = if (method == winner) "num winner" else "num"
                                td(classes = cls) {
                                    if (r != null) {
                                        val m = r.primaryMetric
                                        +formatScoreBench(m.score)
                                        span(classes = "err") { +" ± ${formatScoreBench(m.scoreError)}" }
                                        if (bestScore != null && winner != null && method != winner && m.score > 0) {
                                            span(classes = "ratio-note") {
                                                +" "
                                                span(classes = "ratio") { +"×%.1f".formatRoot(m.score / bestScore) }
                                                +" $winner"
                                            }
                                        }
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

private fun formatRatio(r: Double): String = when {
    r >= 100 -> "%.0f".formatRoot(r)
    r >= 10 -> "%.0f".formatRoot(r)
    else -> "%.1f".formatRoot(r)
}

private fun aocSlopegraphSvg(
    pivot: Map<String, Map<String, JmhResult>>,
    samples: List<String>,
    backends: List<String>,
    yMinLog: Double,
    yMaxLog: Double,
    medianByBackend: Map<String, Double?>,
): String {
    val padL = 64
    val padR = 56
    val padT = 36
    val padB = 28
    val plotW = 540
    val plotH = 360
    val width = padL + plotW + padR
    val height = padT + plotH + padB
    val ySpan = (yMaxLog - yMinLog).coerceAtLeast(1e-9)

    // Three columns evenly spaced inside the plot area. If we ever had >3 backends this still
    // works; if we had 2 it splits the plot in half. The single-column degenerate case is
    // handled by clamping the divisor.
    val n = backends.size
    val colX = backends.mapIndexed { i, _ ->
        if (n == 1) padL + plotW / 2.0
        else padL + plotW.toDouble() * i / (n - 1)
    }

    // Larger y → larger time → lower on screen ("lower is better" → top).
    fun yFor(v: Double): Double {
        val L = kotlin.math.log10(v.coerceAtLeast(1e-12)).coerceIn(yMinLog, yMaxLog)
        return padT + plotH * (yMaxLog - L) / ySpan
    }

    val sb = StringBuilder()
    sb.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" """)
    sb.append("""width="$width" height="$height" role="img">""")

    // Y-axis decade gridlines + tick labels (1µs .. 100ms).
    var dec = yMinLog.toInt()
    while (dec.toDouble() <= yMaxLog + 1e-9) {
        val y = yFor(Math.pow(10.0, dec.toDouble()))
        sb.append("""<line class="grid" x1="$padL" y1="${"%.2f".formatRoot(y)}" x2="${padL + plotW}" y2="${"%.2f".formatRoot(y)}"/>""")
        sb.append("""<text class="tick" x="${padL - 8}" y="${"%.2f".formatRoot(y + 3)}" text-anchor="end">${decadeLabel(dec)}</text>""")
        dec += 1
    }

    // Vertical column rules + column labels.
    backends.forEachIndexed { i, backend ->
        val x = colX[i]
        val color = colorFor(backend, i)
        sb.append("""<line class="aoc-col" x1="${"%.2f".formatRoot(x)}" y1="$padT" x2="${"%.2f".formatRoot(x)}" y2="${padT + plotH}"/>""")
        sb.append("""<text class="aoc-col-label" x="${"%.2f".formatRoot(x)}" y="${padT - 16}" text-anchor="middle" fill="$color">${backend.xmlEscapeBench()}</text>""")
    }

    // 14 faint sample lines. One polyline per sample, threaded through the engine columns in
    // ordered order. Per-sample point glyphs are intentionally small so the bundle reads as a
    // single fan rather than three rows of dots.
    samples.forEach { sample ->
        val pts = backends.mapIndexedNotNull { i, b ->
            val v = pivot[sample]?.get(b)?.primaryMetric?.score?.takeIf { it.isFinite() && it > 0 }
            v?.let { Pair(colX[i], yFor(it)) }
        }
        if (pts.size < 2) return@forEach
        val ptsStr = pts.joinToString(" ") { (x, y) -> "${"%.2f".formatRoot(x)},${"%.2f".formatRoot(y)}" }
        sb.append("""<polyline class="aoc-slope-line" points="$ptsStr"/>""")
        pts.forEach { (x, y) ->
            sb.append("""<circle class="aoc-slope-pt" cx="${"%.2f".formatRoot(x)}" cy="${"%.2f".formatRoot(y)}" r="2"/>""")
        }
    }

    // Median path: bold line threading through median score per engine. This is what makes
    // the headline ratio (×22 / ×44) legible inside the bundle.
    val medianPts = backends.mapIndexedNotNull { i, b ->
        medianByBackend[b]?.takeIf { it.isFinite() && it > 0 }?.let { Pair(colX[i], yFor(it)) }
    }
    if (medianPts.size >= 2) {
        val ptsStr = medianPts.joinToString(" ") { (x, y) -> "${"%.2f".formatRoot(x)},${"%.2f".formatRoot(y)}" }
        sb.append("""<polyline class="aoc-median-line" points="$ptsStr"/>""")
        medianPts.forEach { (x, y) ->
            sb.append("""<circle class="aoc-median-pt" cx="${"%.2f".formatRoot(x)}" cy="${"%.2f".formatRoot(y)}" r="4"/>""")
        }
    }
    // Legend tag for the median path, anchored at the rightmost median point.
    if (medianPts.isNotEmpty()) {
        val (lastX, lastY) = medianPts.last()
        sb.append("""<text class="aoc-median-label" x="${"%.2f".formatRoot(lastX + 8)}" y="${"%.2f".formatRoot(lastY + 3)}">median</text>""")
    }

    sb.append("</svg>")
    return sb.toString()
}

private fun decadeLabel(dec: Int): String = when (dec) {
    -3 -> "1µs"
    -2 -> "10µs"
    -1 -> "100µs"
    0 -> "1ms"
    1 -> "10ms"
    2 -> "100ms"
    3 -> "1s"
    4 -> "10s"
    else -> "10^$dec"
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

private fun renderSamplePanel(
    byMethod: Map<String, JmhResult>,
    methods: List<String>,
): String {
    val present = methods.filter { byMethod[it] != null }
    if (present.isEmpty()) return ""
    val scores = present.map { byMethod.getValue(it).primaryMetric.score }
    val errors = present.map { byMethod.getValue(it).primaryMetric.scoreError }
    return barsSvgPerMethod(present, scores, errors, methods, width = 280, height = 200)
}

private fun barsSvgPerMethod(
    labels: List<String>,
    values: List<Double>,
    errors: List<Double>,
    allMethods: List<String>,
    width: Int,
    height: Int,
): String {
    if (labels.isEmpty()) return ""
    val padL = 36; val padR = 12; val padT = 14; val padB = 28
    val plotW = width - padL - padR
    val plotH = height - padT - padB
    val maxV = values.indices.maxOf { values[it] + (errors.getOrNull(it) ?: 0.0).coerceAtLeast(0.0) }
        .coerceAtLeast(1e-9)
    val niceMax = niceCeilingBench(maxV)
    val barGap = 0.30
    val nBars = labels.size
    val slot = plotW.toDouble() / nBars
    val barW = slot * (1 - barGap)

    val sb = StringBuilder()
    sb.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" """)
    sb.append("""width="$width" height="$height" role="img">""")
    val ticks = 4
    for (i in 0..ticks) {
        val v = niceMax * i / ticks
        val y = padT + plotH - plotH * i / ticks
        sb.append("""<line class="grid" x1="$padL" y1="$y" x2="${padL + plotW}" y2="$y"/>""")
        sb.append("""<text class="tick" x="${padL - 5}" y="${y + 3}" text-anchor="end">${formatTickBench(v)}</text>""")
    }
    sb.append("""<line class="baseline" x1="$padL" y1="${padT + plotH}" x2="${padL + plotW}" y2="${padT + plotH}"/>""")
    for (i in labels.indices) {
        val v = values[i]
        val err = errors.getOrNull(i)?.takeIf { it.isFinite() && it > 0 } ?: 0.0
        val h = plotH * (v / niceMax)
        val x = padL + slot * i + (slot - barW) / 2
        val y = padT + plotH - h
        val color = colorFor(labels[i], allMethods.indexOf(labels[i]).coerceAtLeast(0))
        sb.append("""<rect x="${"%.2f".formatRoot(x)}" y="${"%.2f".formatRoot(y)}" """)
        sb.append("""width="${"%.2f".formatRoot(barW)}" height="${"%.2f".formatRoot(h)}" fill="$color"/>""")
        if (err > 0) {
            val cxBar = x + barW / 2
            val yHi = padT + plotH - plotH * ((v + err) / niceMax)
            val yLo = padT + plotH - plotH * ((v - err).coerceAtLeast(0.0) / niceMax)
            val cap = (barW * 0.3).coerceAtMost(8.0)
            sb.append("""<line class="err-line" x1="${"%.2f".formatRoot(cxBar)}" y1="${"%.2f".formatRoot(yHi)}" """)
            sb.append("""x2="${"%.2f".formatRoot(cxBar)}" y2="${"%.2f".formatRoot(yLo)}"/>""")
            sb.append("""<line class="err-line" x1="${"%.2f".formatRoot(cxBar - cap)}" y1="${"%.2f".formatRoot(yHi)}" """)
            sb.append("""x2="${"%.2f".formatRoot(cxBar + cap)}" y2="${"%.2f".formatRoot(yHi)}"/>""")
            sb.append("""<line class="err-line" x1="${"%.2f".formatRoot(cxBar - cap)}" y1="${"%.2f".formatRoot(yLo)}" """)
            sb.append("""x2="${"%.2f".formatRoot(cxBar + cap)}" y2="${"%.2f".formatRoot(yLo)}"/>""")
        }
        val cx = padL + slot * i + slot / 2
        sb.append("""<text class="bar-axis" x="${"%.2f".formatRoot(cx)}" y="${padT + plotH + 16}" text-anchor="middle">${labels[i].xmlEscapeBench()}</text>""")
    }
    sb.append("</svg>")
    return sb.toString()
}

private fun niceCeilingBench(v: Double): Double {
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

private fun formatScoreBench(v: Double): String {
    if (v == 0.0 || !v.isFinite()) return "%.1f".formatRoot(v)
    val abs = kotlin.math.abs(v)
    val decimals = if (abs >= 0.1) 1 else kotlin.math.ceil(-kotlin.math.log10(abs)).toInt().coerceAtLeast(1)
    return "%.${decimals}f".formatRoot(v)
}

private fun formatTickBench(v: Double): String =
    if (v >= 100) "%.0f".formatRoot(v)
    else if (v >= 10) "%.1f".formatRoot(v)
    else "%.2f".formatRoot(v)

private fun String.xmlEscapeBench(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun JmhResult.benchmarkClass(): String = benchmark.substringBeforeLast('.').substringAfterLast('.')
private fun JmhResult.method(): String = benchmark.substringAfterLast('.')
private fun JmhResult.sample(): String = params["sample"] ?: "—"

@Suppress("CssUnresolvedCustomProperty", "CssUnusedSymbol", "CssNoGenericFontName")
@Language("CSS")
private val EXTRA_CSS = """
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
.bars-panel-svg svg { display: block; max-width: 100%; height: auto; }

td.name .sample-link {
  color: var(--ink); font-weight: 600;
  /* Solid accent underline so bold sample names still read as links. The dashed --rule
     variant blended into the surface at this font weight. */
  border-bottom: 1px solid var(--accent);
}
td.name .sample-link:hover {
  color: var(--accent); border-bottom-width: 2px;
}
ul.sample-cell-sources {
  list-style: none; padding: 0; margin: .15rem 0 0;
  font-family: var(--mono); font-size: .68rem;
}
ul.sample-cell-sources li { margin: .08rem 0; overflow-wrap: anywhere; }
ul.sample-cell-sources a { color: var(--accent); }
.grid { stroke: var(--rule-hair); stroke-width: 1; }
.baseline { stroke: var(--ink); stroke-width: 1; }
.tick { font-family: var(--mono); font-size: 11px; fill: var(--muted); font-variant-numeric: tabular-nums; }
.axis-label { font-family: var(--mono); font-size: 10px; fill: var(--muted); text-transform: uppercase; letter-spacing: .12em; }
.group-label { font-family: var(--mono); font-size: 11px; fill: var(--ink-soft); font-variant-numeric: tabular-nums; }
.bar-label { font-family: var(--mono); font-size: 11px; fill: var(--ink); font-variant-numeric: tabular-nums; }
.err-line { stroke: var(--ink); stroke-width: 1; }

.aoc-headline {
  display: flex; flex-wrap: wrap; gap: 1.2rem;
  margin: -.4rem 0 .8rem; padding: .6rem .9rem;
  background: var(--surface); border: 1px solid var(--rule);
  font-family: var(--mono); font-size: .78rem;
}
.aoc-headline-cell { display: inline-flex; align-items: baseline; gap: .4rem; }
.aoc-headline-swatch { display: inline-block; width: 10px; height: 10px; align-self: center; flex-shrink: 0; }
.aoc-headline-engine { color: var(--ink); font-weight: 600; letter-spacing: .04em; }
.aoc-headline-ratio { color: var(--accent); font-weight: 700; font-variant-numeric: tabular-nums; }
.aoc-headline-note { color: var(--muted); letter-spacing: .04em; }

.aoc-slope {
  background: var(--surface); border: 1px solid var(--rule);
  padding: .9rem 1rem; margin-bottom: 1.2rem;
}
.aoc-slope svg { display: block; max-width: 100%; height: auto; }
.aoc-slope .aoc-col { stroke: var(--rule); stroke-width: 1; }
.aoc-slope .aoc-col-label {
  font-family: var(--mono); font-size: 12px; font-weight: 600;
  text-transform: lowercase; letter-spacing: .04em;
}
/* Per-sample lines: light enough to read as a bundle, dark enough that the count of 14 is
   visible. Day is a nuisance variable — none gets a colour. */
.aoc-slope .aoc-slope-line {
  fill: none; stroke: var(--ink-soft); stroke-width: 1; opacity: .28;
}
.aoc-slope .aoc-slope-pt { fill: var(--ink-soft); opacity: .55; }
/* Median path: the visual subject. Solid ink, thick enough to read through the bundle. */
.aoc-slope .aoc-median-line {
  fill: none; stroke: var(--accent); stroke-width: 2.4;
}
.aoc-slope .aoc-median-pt { fill: var(--accent); }
.aoc-slope .aoc-median-label {
  font-family: var(--mono); font-size: 11px; fill: var(--accent);
  font-variant-numeric: tabular-nums; letter-spacing: .04em;
}

.legend { display: flex; gap: 1.4rem; flex-wrap: wrap; margin-top: -.5rem; margin-bottom: 1.2rem; font-family: var(--mono); font-size: .72rem; color: var(--ink-soft); }
.legend-item { display: inline-flex; align-items: center; gap: .5rem; }
.swatch { display: inline-block; width: 10px; height: 10px; background: var(--muted); flex-shrink: 0; }
.legend-label { letter-spacing: .04em; }

thead th .unit { color: var(--faint); font-weight: 500; letter-spacing: .04em; }
td.num .err { color: var(--muted); font-size: .75rem; }
td.num.winner { font-weight: 700; color: var(--accent); }
td.num .ratio { color: var(--accent); font-weight: 600; }
td.num .ratio-note { color: var(--muted); font-family: var(--mono); font-size: .72rem; letter-spacing: .04em; margin-left: .35rem; text-transform: lowercase; }
""".trimIndent()
