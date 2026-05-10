/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:JvmName("ProfileSampleKt")

package net.postchain.rell.performance.profiler

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.rr.RR_QueryDefinition
import net.postchain.rell.base.runtime.Rt_ExecutionContext
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_Interpreter
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.performance.benchmarks.RellBackendBenchmark
import one.convert.Arguments as JfrConverterArgs
import one.convert.Main as JfrConverterMain
import one.profiler.AsyncProfiler
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Profile a single sample query in-process and emit LLM-parseable artifacts. No JMH framing,
 * no aggregated report — just `tree.txt` + `flat.txt` + `collapsed.txt` for a chosen
 * `<sample>/<query>` so an automated reader (or Claude) can decide which subtrees to
 * optimise.
 *
 * Why in-process: the profile target IS this JVM (we drive the query in a tight loop here).
 * `one.profiler.AsyncProfiler.getInstance()` `System.load`s libasyncProfiler from the bundled
 * Maven jar — no Attach API, no `asprof` subprocess, no provisioning step.
 */
class ProfileSampleCommand : CliktCommand(name = "profile-sample") {
    override fun help(context: Context) = "Profile one sample query under async-profiler and " +
        "emit machine-readable hot-method (`flat`) and call-tree (`tree`) text dumps."

    private val sample by option(
        "--sample",
        help = "Sample directory under performance/src/main/resources (e.g. ft4_bench, mna_bench, struct_bench, synthetic_bench).",
    ).required()

    private val query by option(
        "--query",
        help = "Query name to call. Defaults: bench (synthetic_bench), bench_gtv_text (ft4_bench), bench_decimal_pow (mna_bench), bench_dto_mapping (struct_bench).",
    )

    private val arg by option(
        "--arg",
        help = "Single integer argument passed to the query. Defaults to a sensible per-sample reps count.",
    ).long()

    private val backend by option(
        "--backend", help = "Runtime backend: interpreter or truffle (default: interpreter).",
    ).default("interpreter")

    private val reps by option(
        "--reps", help = "Number of query invocations under the profiler (default: 200).",
    ).int().default(200)

    private val warmups by option(
        "--warmups",
        help = "Pre-profile invocations to warm up the JIT/Truffle PE pipeline (default: 30).",
    ).int().default(30)

    private val event by option(
        "--event", help = "async-profiler event: cpu | wall | alloc | lock (default: cpu).",
    ).default("cpu")

    private val intervalNs by option(
        "--interval-ns",
        help = "Sampling interval in nanoseconds (default: 1_000_000 = 1 ms; lower = more samples).",
    ).long().default(1_000_000L)

    private val outputDir by option(
        "--output-dir",
        help = "Directory to write flat.txt / tree.txt / collapsed.txt (default: " +
            "performance/reports/sample-<sample>-<query>).",
    ).path()

    private val formats by option(
        "--formats",
        help = "Comma-separated subset of: flat, tree, butterfly, collapsed, flamegraph, jfr " +
            "(default: flat,tree,butterfly,collapsed).",
    ).split(",").default(listOf("flat", "tree", "butterfly", "collapsed"))

    private val topMethods by option(
        "--top",
        help = "Number of hot methods to print to stdout / write to flat.txt and to seed butterfly " +
            "view (default: 30).",
    ).int().default(30)

    private val lines by option(
        "--lines",
        help = "Include source line numbers in frames (default: on). Requires `-XX:+DebugNonSafepoints` " +
            "for accurate attribution; the Gradle task already passes it.",
    ).flag("--no-lines", default = true)

    private val butterflyDepth by option(
        "--butterfly-depth",
        help = "Maximum caller depth printed per hot leaf in butterfly.txt (default: 6).",
    ).int().default(6)

    private val callerThresholdPct by option(
        "--butterfly-min-pct",
        help = "Prune butterfly callers contributing less than N%% of the leaf's self time " +
            "(default: 5.0).",
    ).double().default(5.0)

    override fun run() {
        val resourcePath = "$sample/main.rell"
        val queryName = query ?: defaultQuery(sample)
        val argValue = arg ?: defaultArg(sample, queryName)
        val outDir = (outputDir ?: (perfDir() / "reports" / "sample-$sample-$queryName-$backend"))
            .also { it.createDirectories() }

        log("profile-sample", "sample=$sample query=$queryName arg=$argValue backend=$backend reps=$reps")
        log("profile-sample", "output dir: $outDir")

        // Compile the sample, get the query, share the existing benchmark scaffolding.
        val harness = SampleHarness()
        val rrApp = harness.setUpBackend(backend, resourcePath)
        val q = rrApp.module(ModuleName.EMPTY)!!.queries[queryName]
            ?: error("Query `$queryName` not found in $resourcePath. Available: " +
                rrApp.module(ModuleName.EMPTY)!!.queries.keys.joinToString())
        val args: List<Rt_Value> = listOf(Rt_IntValue.get(argValue))

        // Warm-up phase — do not start the profiler until JIT / Truffle have settled.
        log("profile-sample", "warmup × $warmups...")
        repeat(warmups) { ProfileSampleHotLoop.runOnce(harness.interpreter, q, harness.exeCtx, args) }

        // async-profiler text dumps don't include source line numbers — its formatter writes
        // `class.method` only. We always capture the full JFR snapshot and let `jfr-converter`
        // (which has --lines support) re-render the line-attributed collapsed and flamegraph.
        // Flat / tree text dumps stay on async-profiler since they have a stable familiar
        // format and the per-call-site detail is captured in butterfly.txt anyway.
        val jfrFile = outDir / "profile.jfr"
        val profiler = AsyncProfiler.getInstance()
        log("profile-sample", "async-profiler ${profiler.version}")
        // JFR is configured at start time; the file is finalized on stop.
        profiler.execute(
            "start,event=$event,interval=$intervalNs,total," +
                "jfr,file=${jfrFile.absolutePathString()}",
        )

        log("profile-sample", "measure × $reps...")
        repeat(reps) { ProfileSampleHotLoop.runOnce(harness.interpreter, q, harness.exeCtx, args) }

        val totalSamples = profiler.samples
        log("profile-sample", "captured $totalSamples samples")

        val asprofFlatTopN: String? = if ("flat" in formats) {
            normalizeFrames(profiler.execute("flat=$topMethods,total")).also {
                (outDir / "flat.txt").writeText(it)
            }
        } else null

        if ("tree" in formats) {
            (outDir / "tree.txt").writeText(normalizeFrames(profiler.execute("tree,total")))
        }

        profiler.stop()

        // From here on we work off the JFR file via jfr-converter. It carries source line
        // numbers, lambda normalization, and BCI when DebugNonSafepoints is on (the Gradle
        // task sets it).
        val collapsedText = renderCollapsedFromJfr(jfrFile)
        if ("collapsed" in formats) (outDir / "collapsed.txt").writeText(collapsedText)
        if ("butterfly" in formats) {
            val text = Butterfly.render(
                collapsedText = collapsedText,
                topLeaves = topMethods,
                maxDepth = butterflyDepth,
                callerThresholdPct = callerThresholdPct,
            )
            (outDir / "butterfly.txt").writeText(text)
        }
        if ("flamegraph" in formats) {
            val flameOut = outDir / "flamegraph.html"
            convertJfr(jfrFile, flameOut, "html")
            // The flamegraph HTML embeds the same frame strings as the collapsed output, so
            // run normalization over it for consistent diff-ability.
            flameOut.writeText(normalizeFrames(flameOut.readText()))
        }
        if ("jfr" !in formats) jfrFile.toFile().delete()

        // Always print the top-N flat methods to stdout — the most LLM-grokkable summary.
        val flatStdout = asprofFlatTopN ?: normalizeFrames(profiler.execute("flat=$topMethods,total"))
        println()
        println("─── top $topMethods hot methods (flat profile) ──────────────────────────────")
        println(flatStdout)
        println()
        log("profile-sample", "wrote artifacts to $outDir")
    }

    /**
     * Render the JFR recording at [jfrFile] as a collapsed-stack text buffer with source-line
     * attribution. We use a temp file because [JfrConverterMain.convert] writes to disk only.
     */
    private fun renderCollapsedFromJfr(jfrFile: Path): String {
        val tmp = jfrFile.resolveSibling(".collapsed-with-lines.tmp.txt")
        try {
            convertJfr(jfrFile, tmp, "collapsed")
            return normalizeFrames(tmp.readText())
        } finally {
            tmp.toFile().delete()
        }
    }

    private fun convertJfr(jfrFile: Path, output: Path, format: String) {
        val flagList = buildList {
            add("--output"); add(format)
            add("--total")
            if (lines) add("--lines")
            // `--dot` matches async-profiler's text-dump style (`java.util.Arrays`) so the
            // butterfly view diffs cleanly against `flat.txt` / `tree.txt`.
            add("--dot")
            // `--norm` collapses a few stable hidden-class patterns; our regex-based
            // normalizeFrames() handles the rest (HotSpot stub hashes, etc.).
            add("--norm")
        }
        val args = JfrConverterArgs(*flagList.toTypedArray())
        JfrConverterMain.convert(jfrFile.absolutePathString(), output.absolutePathString(), args)
    }
}

/**
 * Hot loop wrapper kept out-of-line. The Gradle task pins `-XX:CompileCommand=dontinline` on
 * `runOnce` so each rep stays as a distinct stack frame at sample time — without this, the
 * JIT can fold the loop body and async-profiler attributes samples to the wrong caller.
 */
internal object ProfileSampleHotLoop {
    @JvmStatic
    fun runOnce(
        interp: Rt_Interpreter,
        q: RR_QueryDefinition,
        ctx: Rt_ExecutionContext,
        args: List<Rt_Value>,
    ) {
        interp.callQuery(q, ctx, args)
    }
}

/** Tiny visible-from-outside subclass of [RellBackendBenchmark] used by the CLI. */
private class SampleHarness : RellBackendBenchmark()

/** Per-sample default query name when `--query` is omitted. */
private fun defaultQuery(sample: String): String = when (sample) {
    "synthetic_bench" -> "bench"
    "ft4_bench" -> "bench_gtv_text"
    "mna_bench" -> "bench_decimal_pow"
    "struct_bench" -> "bench_dto_mapping"
    else -> error("Unknown sample `$sample`; pass --query explicitly.")
}

/** Per-(sample, query) default `reps` argument — sized so one call lands in the few-ms range. */
private fun defaultArg(sample: String, query: String): Long = when (sample to query) {
    "synthetic_bench" to "bench" -> 100_000L
    "ft4_bench" to "bench_gtv_text" -> 200L
    "ft4_bench" to "bench_rule_serde" -> 500L
    "ft4_bench" to "bench_rule_eval" -> 5_000L
    "mna_bench" to "bench_decimal_pow" -> 200L
    "mna_bench" to "bench_perlin_noise" -> 20L
    "mna_bench" to "bench_locations" -> 200L
    "struct_bench" to "bench_dto_mapping" -> 50L
    "struct_bench" to "bench_cursor_codec" -> 100L
    "struct_bench" to "bench_multi_sig" -> 500L
    else -> 100L
}

fun main(args: Array<String>) = ProfileSampleCommand().main(args)
