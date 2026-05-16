/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:Suppress("unused")
@file:JvmName("StdlibCallBenchmarkKt")

package net.postchain.rell.performance.benchmarks

import kotlinx.benchmark.*
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.rr.RR_QueryDefinition
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_Value
import org.openjdk.jmh.annotations.Fork

/**
 * Microbenchmark of the lib-DSL system-function call path on the tree-walker. The Rell
 * workload (`stdlib_call_bench/main.rell`) hammers `integer.sign()` — a typed-param
 * (`self()`) stdlib function whose lambda body goes through the body funnel that publishes
 * the in-flight call arguments for typed delegates. It isolates that funnel's per-call cost
 * from collections, BigInteger/BigDecimal, and DB access.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
@Fork(2)
class StdlibCallBenchmark : RellBackendBenchmark() {

    private val args: List<Rt_Value> = listOf(Rt_IntValue.get(100_000L))

    lateinit var query: RR_QueryDefinition

    @Setup
    fun setUp() {
        val rrApp = setUpBackend("interpreter", "stdlib_call_bench/main.rell")
        query = rrApp.module(ModuleName.EMPTY)!!.queries.getValue("bench")
    }

    @Benchmark
    fun runQuery(blackhole: Blackhole) {
        blackhole.consume(interpreter.callQuery(query, exeCtx, args))
    }
}
