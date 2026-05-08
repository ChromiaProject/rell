/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:Suppress("unused")
@file:JvmName("StructBenchmarkKt")

package net.postchain.rell.performance.benchmarks

import kotlinx.benchmark.*
import org.openjdk.jmh.annotations.Fork
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.rr.RR_QueryDefinition
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_Value

/**
 * Struct-heavy microbenchmarks distilled from real Chromia codebases (`dto_mapping`,
 * `cursor_codec`, `multi_sig`) on the tree-walker vs. Truffle. Pure compute, DB-free;
 * Rell source in `struct_bench/main.rell`.
 *
 * Each query exercises a different part of the struct hot-path:
 *   - `dto_mapping`  → triple-nested struct construction in tight loops
 *   - `cursor_codec` → struct ↔ bytes ↔ base64 round-tripping + list slicing
 *   - `multi_sig`    → struct deserialization + set/list intersection + early-exit signer loop
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
@Fork(
    jvmArgsPrepend = [
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+EnableJVMCI",
        "-XX:+UseJVMCINativeLibrary",
        "--enable-native-access=ALL-UNNAMED",
    ],
)
class StructBenchmark : RellBackendBenchmark() {

    @Param("interpreter", "truffle")
    lateinit var backend: String

    @Param("dto_mapping", "cursor_codec", "multi_sig")
    lateinit var sample: String

    private lateinit var query: RR_QueryDefinition
    private var args: List<Rt_Value> = emptyList()

    @Setup
    fun setUp() {
        val rrApp = setUpBackend(backend, "struct_bench/main.rell")
        val (queryName, repsArg) = workloadConfig(sample)
        query = rrApp.module(ModuleName.EMPTY)!!.queries.getValue(queryName)
        args = listOf(Rt_IntValue.get(repsArg))
    }

    @Benchmark
    fun runQuery(blackhole: Blackhole) = blackhole.consume(interpreter.callQuery(query, exeCtx, args))

    // `reps` sized so each invocation lands in the single-millisecond range on the tree-walker.
    private fun workloadConfig(sample: String): Pair<String, Long> = when (sample) {
        "dto_mapping" -> "bench_dto_mapping" to 50L
        "cursor_codec" -> "bench_cursor_codec" to 100L
        "multi_sig" -> "bench_multi_sig" to 500L
        else -> error("Unknown sample: $sample")
    }
}

fun main() {
    val b = Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.")
    for (sample in listOf("dto_mapping", "cursor_codec", "multi_sig")) {
        val bm = StructBenchmark()
        bm.backend = "interpreter"
        bm.sample = sample
        bm.setUp()
        val start = System.nanoTime()
        bm.runQuery(b)
        println("smoke sample=$sample backend=interpreter elapsed_ms=${(System.nanoTime() - start) / 1_000_000}")
    }
}
