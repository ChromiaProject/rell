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
 * mna-blockchain-derived microbenchmarks (`decimal_pow`, `perlin_noise`, `locations`) on the
 * legacy R_Expr tree-walker. Pure compute, DB-free; Rell source in `mna_bench/main.rell`.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
class MnaBenchmark : RellBenchmarkBase() {

    @Param("r-expr-legacy")
    lateinit var backend: String

    @Param("decimal_pow", "perlin_noise", "locations")
    lateinit var sample: String

    private lateinit var query: R_QueryDefinition
    private var args: List<Rt_Value> = emptyList()

    @Setup
    fun setUp() {
        val app = setUpApp("mna_bench/main.rell")
        val (queryName, repsArg) = workloadConfig(sample)
        query = app.moduleMap.getValue(R_ModuleName.EMPTY).queries.getValue(queryName)
        args = listOf(Rt_IntValue.get(repsArg))
    }

    @Benchmark
    fun runQuery(blackhole: Blackhole) {
        blackhole.consume(query.call(exeCtx, args))
    }

    private fun workloadConfig(sample: String): Pair<String, Long> = when (sample) {
        "decimal_pow" -> "bench_decimal_pow" to 200L
        "perlin_noise" -> "bench_perlin_noise" to 20L
        "locations" -> "bench_locations" to 200L
        else -> error("Unknown sample: $sample")
    }
}
