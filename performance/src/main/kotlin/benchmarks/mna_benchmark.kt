/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:Suppress("unused")
@file:JvmName("MnaBenchmarkKt")

package net.postchain.rell.performance.benchmarks

import kotlinx.benchmark.*
import org.openjdk.jmh.annotations.Fork
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.rr.RR_QueryDefinition
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_Value

/**
 * mna-blockchain-derived microbenchmarks (`decimal_pow`, `perlin_noise`, `locations`) on the
 * tree-walker vs. Truffle. Pure compute, DB-free; Rell source in `mna_bench/main.rell`.
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
class MnaBenchmark : RellBackendBenchmark() {

    @Param("interpreter", "truffle")
    lateinit var backend: String

    @Param("decimal_pow", "perlin_noise", "locations")
    lateinit var sample: String

    private lateinit var query: RR_QueryDefinition
    private var args: List<Rt_Value> = emptyList()

    @Setup
    fun setUp() {
        val rrApp = setUpBackend(backend, "mna_bench/main.rell")
        val (queryName, repsArg) = workloadConfig(sample)
        query = rrApp.moduleMap.getValue(ModuleName.EMPTY).queries.getValue(queryName)
        args = listOf(Rt_IntValue.get(repsArg))
    }

    @Benchmark
    fun runQuery(blackhole: Blackhole) = blackhole.consume(interpreter.callQuery(query, exeCtx, args))

    // `reps` sized so each invocation lands in the single-millisecond range on the tree-walker.
    private fun workloadConfig(sample: String): Pair<String, Long> = when (sample) {
        "decimal_pow" -> "bench_decimal_pow" to 200L
        "perlin_noise" -> "bench_perlin_noise" to 20L
        "locations" -> "bench_locations" to 200L
        else -> error("Unknown sample: $sample")
    }
}

fun main() {
    val b = Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.")
    for (sample in listOf("decimal_pow", "perlin_noise", "locations")) {
        val bm = MnaBenchmark()
        bm.backend = "interpreter"
        bm.sample = sample
        bm.setUp()
        val start = System.nanoTime()
        bm.runQuery(b)
        println("smoke sample=$sample backend=interpreter elapsed_ms=${(System.nanoTime() - start) / 1_000_000}")
    }
}
