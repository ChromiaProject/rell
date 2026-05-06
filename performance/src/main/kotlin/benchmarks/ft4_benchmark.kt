/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:Suppress("unused")
@file:JvmName("Ft4BenchmarkKt")

package net.postchain.rell.performance.benchmarks

import kotlinx.benchmark.*
import org.openjdk.jmh.annotations.Fork
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.rr.RR_QueryDefinition
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_Value

/**
 * ft4-lib-derived microbenchmarks (`gtv_text`, `rule_serde`, `rule_eval`) on the tree-walker
 * vs. Truffle. Pure compute, DB-free; Rell source in `ft4_bench/main.rell`.
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

    // `reps` sized so each invocation lands in the single-millisecond range on the tree-walker.
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
