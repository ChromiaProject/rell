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
 * Synthetic microbenchmark for the runtime backends.
 *
 * The `backend` parameter selects between the default tree-walker
 * ([net.postchain.rell.base.runtime.Rt_InterpreterImpl]), the Truffle peer
 * ([net.postchain.rell.base.runtime.truffle.Tf_Backend]), and a hand-written Kotlin baseline
 * computing the same function. JMH expands across all values, so each metric is reported once
 * per backend — letting us track Truffle/interpreter speed ratio and absolute distance from JIT-ed
 * Kotlin over time.
 *
 * Use:
 *   `./gradlew :benchmarks:mainBenchmark` (all backends)
 *   `./gradlew :benchmarks:mainBenchmark -Pbench.params=backend=truffle` (just Truffle)
 *
 * The benchmark exercises a query that combines a primality test, the Collatz sequence and a
 * recursive Fibonacci — covering arithmetic, control flow, and direct user-function calls,
 * which is what Truffle's partial evaluator should specialise hardest. Real-world ft4-lib
 * workloads live in [Ft4Benchmark]. The Rell source lives in
 * `src/main/resources/synthetic_bench/main.rell`.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@Warmup(iterations = 10, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
// Force the Graal optimizing compiler in JMH's forked JVM so Graal truffle-runtime engages
@Fork(
    jvmArgsPrepend = [
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+EnableJVMCI",
        "-XX:+UseJVMCINativeLibrary",
        "--enable-native-access=ALL-UNNAMED",
    ],
)
class InterpreterBenchmark : RellBackendBenchmark() {

    /**
     * Backend selector. Drives both the [net.postchain.rell.base.runtime.Rt_Interpreter] factory swap and JMH's reporting.
     *
     *   * `interpreter` - canonical tree-walker.
     *   * `truffle` - Truffle backend.
     *   * `kotlin` - hand-written Kotlin equivalent as JIT baseline.
     */
    @Param("interpreter", "truffle", "kotlin")
    lateinit var backend: String

    /**
     * Single-value @Param so the report generator pivots this row by `sample` and can attach
     * a description / source link the same way it does for [Ft4Benchmark].
     */
    @Param("collatz_primes_fib")
    lateinit var sample: String

    companion object {
        const val LIMIT_INT = 100_000L
        val LIMIT: List<Rt_Value> = listOf(Rt_IntValue.get(LIMIT_INT))
    }

    lateinit var query: RR_QueryDefinition

    @Setup
    fun setUp() {
        if (backend == "kotlin") return
        val rrApp = setUpBackend(backend, "synthetic_bench/main.rell")
        query = rrApp.moduleMap.getValue(ModuleName.EMPTY).queries.getValue("bench")
    }

    @Benchmark
    fun runQuery(blackhole: Blackhole) {
        if (backend == "kotlin") {
            blackhole.consume(KotlinBenchmark.bench(LIMIT_INT))
        } else {
            blackhole.consume(interpreter.callQuery(query, exeCtx, LIMIT))
        }
    }

    private object KotlinBenchmark {
        private fun is_prime(n: Long): Boolean {
            if (n < 2) return false
            if (n < 4) return true
            if (n % 2 == 0L) return false
            var i = 3L
            while (i * i <= n) {
                if (n % i == 0L) return false
                i += 2
            }
            return true
        }

        private fun collatz_steps(n: Long): Long {
            var m = n
            var steps = 0L
            while (m > 1) {
                m = if (m % 2 == 0L) m / 2 else 3 * m + 1
                steps += 1
            }
            return steps
        }

        private fun fib(n: Long): Long {
            if (n < 2) return n
            return fib(n - 1) + fib(n - 2)
        }

        @Suppress("SameParameterValue") fun bench(cap: Long): Long {
            var acc = 0L
            var i = 2L
            while (i <= cap) {
                if (is_prime(i)) acc += collatz_steps(i)
                i += 1
            }
            return acc + fib(20)
        }
    }
}

fun main() {
    val b = Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.")
    val bm = InterpreterBenchmark()
    bm.backend = "truffle"
    bm.sample = "collatz_primes_fib"
    bm.setUp()
    bm.runQuery(b)
}
