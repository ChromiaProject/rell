/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:JvmName("ProfileSampleKt")

package net.postchain.rell.performance.profiler

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.performance.benchmarks.RellBackendBenchmark
import one.profiler.AsyncProfiler
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.div
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
        help = "Comma-separated subset of: flat, tree, collapsed, flamegraph, jfr (default: flat,tree,collapsed).",
    ).split(",").default(listOf("flat", "tree", "collapsed"))

    private val topMethods by option(
        "--top",
        help = "Number of hot methods to print to stdout / write to flat.txt (default: 30).",
    ).int().default(30)

    override fun run() {
        val resourcePath = "$sample/main.rell"
        val queryName = query ?: defaultQuery(sample)
        val argValue = arg ?: defaultArg(sample, queryName)
        val outDir = (outputDir ?: (perfDir() / "reports" / "sample-$sample-$queryName")).also {
            it.createDirectories()
        }

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
        repeat(warmups) { harness.interpreter.callQuery(q, harness.exeCtx, args) }

        val profiler = AsyncProfiler.getInstance()
        log("profile-sample", "async-profiler ${profiler.version}")
        profiler.execute("start,event=$event,interval=$intervalNs,total")

        log("profile-sample", "measure × $reps...")
        repeat(reps) { harness.interpreter.callQuery(q, harness.exeCtx, args) }

        val totalSamples = profiler.samples
        log("profile-sample", "captured $totalSamples samples")

        for (fmt in formats) writeFormat(profiler, fmt, outDir)
        profiler.stop()

        // Always print the top-N flat methods to stdout — the most LLM-grokkable summary.
        val flat = profiler.dumpFlat(topMethods)
        println()
        println("─── top $topMethods hot methods (flat profile) ──────────────────────────────")
        println(flat)
        println()
        log("profile-sample", "wrote artifacts to $outDir")
    }

    private fun writeFormat(profiler: AsyncProfiler, format: String, outDir: Path) {
        when (format.trim().lowercase()) {
            "flat" -> {
                val text = profiler.dumpFlat(topMethods)
                (outDir / "flat.txt").writeText(text)
            }
            "tree" -> {
                // `tree=N` limits the depth of nesting in the textual call tree; we want full
                // depth here because the profile is short and the tree is the headline output.
                val text = profiler.execute("tree,total")
                (outDir / "tree.txt").writeText(text)
            }
            "collapsed" -> {
                profiler.execute("collapsed,total,file=${(outDir / "collapsed.txt").absolutePathString()}")
            }
            "flamegraph" -> {
                profiler.execute("flamegraph,file=${(outDir / "flamegraph.html").absolutePathString()}")
            }
            "jfr" -> {
                profiler.execute("jfr,file=${(outDir / "profile.jfr").absolutePathString()}")
            }
            else -> log("profile-sample", "WARNING: unknown format `$format` — skipped")
        }
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
