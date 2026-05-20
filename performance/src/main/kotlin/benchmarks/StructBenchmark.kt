/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:Suppress("unused")

package net.postchain.rell.performance.benchmarks

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.model.R_QueryDefinition
import net.postchain.rell.base.runtime.Rt_Value

/**
 * Struct-heavy microbenchmarks distilled from real Chromia codebases (`dto_mapping`,
 * `cursor_codec`, `multi_sig`) on the legacy R_Expr tree-walker. Pure compute, DB-free;
 * Rell source in `struct_bench/main.rell`.
 *
 * Sized so cross-branch results merge cleanly with the rell repo's StructBenchmark
 * (`backend = r-expr-legacy` here vs. `{interpreter, truffle}` there).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
class StructBenchmark : RellBenchmarkBase() {

    @Param("r-expr-legacy")
    lateinit var backend: String

    @Param("dto_mapping", "cursor_codec", "multi_sig")
    lateinit var sample: String

    private lateinit var query: R_QueryDefinition
    private var args: List<Rt_Value> = emptyList()

    @Setup
    fun setUp() {
        val app = setUpApp("struct_bench/main.rell")
        val (queryName, repsArg) = workloadConfig(sample)
        query = app.moduleMap.getValue(R_ModuleName.EMPTY).queries.getValue(queryName)
        args = listOf(Rt_IntValue.get(repsArg))
    }

    @Benchmark
    fun runQuery(blackhole: Blackhole) {
        blackhole.consume(query.call(exeCtx, args))
    }

    private fun workloadConfig(sample: String): Pair<String, Long> = when (sample) {
        "dto_mapping" -> "bench_dto_mapping" to 50L
        "cursor_codec" -> "bench_cursor_codec" to 100L
        "multi_sig" -> "bench_multi_sig" to 500L
        else -> error("Unknown sample: $sample")
    }
}
