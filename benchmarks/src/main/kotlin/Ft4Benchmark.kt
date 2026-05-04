/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:Suppress("unused")

package net.postchain.rell.benchmarks

import kotlinx.benchmark.*
import org.openjdk.jmh.annotations.Fork
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.rr.RR_QueryDefinition
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_Value

/**
 * Real-world microbenchmark driven by code lifted out of
 * [chromaway/ft4-lib](https://gitlab.com/chromaway/ft4-lib) — the canonical Rell library
 * that most Chromia dapps depend on. Three pure-compute, DB-free workloads:
 *
 *   * `gtv_text` — `convert_gtv_to_text`, recursive pretty-printer over a nested gtv.
 *     Stresses string allocation, recursion and dispatch over the gtv tag predicates.
 *   * `rule_serde` — round-trip a list of `rule_expression`s through `serialize_rules`
 *     and `map_rule_expressions_from_gtv`. Stresses gtv build/parse and `from_gtv`/
 *     `to_gtv` conversions.
 *   * `rule_eval` — evaluate a parsed rule set against a `map<text, gtv>` of variables
 *     via `is_rule_violated`. Stresses enum dispatch, `when` chains and map lookups.
 *
 * The Rell source for all three lives in `src/main/resources/ft4_bench/main.rell`. Only the
 * tree-walker and Truffle backends are compared — there is no clean single-language Kotlin
 * equivalent for these workloads (gtv-typed values, dynamic dispatch). For a Kotlin baseline
 * see [InterpreterBenchmark].
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@Warmup(iterations = 10, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
@Fork(
    jvmArgsPrepend = [
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+EnableJVMCI",
        "-XX:+UseJVMCINativeLibrary",
        "--enable-native-access=ALL-UNNAMED",
    ],
)
class Ft4Benchmark : RellBackendBenchmark() {

    @Param("interpreter", "truffle")
    lateinit var backend: String

    /**
     * Picks one of the three ft4-derived workloads exposed as queries by `ft4_bench/main.rell`.
     * Named `sample` so the existing report generator pivots rows by workload without changes.
     */
    @Param("gtv_text", "rule_serde", "rule_eval")
    lateinit var sample: String

    private lateinit var query: RR_QueryDefinition
    private var args: List<Rt_Value> = emptyList()

    @Setup
    fun setUp() {
        val rrApp = setUpBackend(backend, "ft4_bench/main.rell")
        val (queryName, repsArg) = workloadConfig(sample)
        query = rrApp.moduleMap.getValue(ModuleName.EMPTY).queries.getValue(queryName)
        args = listOf(Rt_IntValue.get(repsArg))
    }

    @Benchmark
    fun runQuery(blackhole: Blackhole) = blackhole.consume(interpreter.callQuery(query, exeCtx, args))

    /**
     * Per-workload tuning. The `reps` argument is sized so each invocation lands in the
     * single-millisecond range on the tree-walker — small enough that JMH's 2-second
     * iterations get plenty of samples, large enough to amortise per-call dispatch.
     */
    private fun workloadConfig(sample: String): Pair<String, Long> = when (sample) {
        "gtv_text" -> "bench_gtv_text" to 200L
        "rule_serde" -> "bench_rule_serde" to 500L
        "rule_eval" -> "bench_rule_eval" to 5_000L
        else -> error("Unknown sample: $sample")
    }
}

fun main() {
    val b = Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.")
    for (sample in listOf("gtv_text", "rule_serde", "rule_eval")) {
        val bm = Ft4Benchmark()
        bm.backend = "interpreter"
        bm.sample = sample
        bm.setUp()
        val start = System.nanoTime()
        bm.runQuery(b)
        println("smoke sample=$sample backend=interpreter elapsed_ms=${(System.nanoTime() - start) / 1_000_000}")
    }
}
