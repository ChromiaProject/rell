/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:Suppress("unused")
@file:JvmName("InterpreterBenchmarkKt")

package net.postchain.rell.performance.benchmarks

import kotlinx.benchmark.*
import org.openjdk.jmh.annotations.Fork
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.rr.RR_QueryDefinition
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_Value

/**
 * Synthetic microbenchmark comparing the tree-walker, Truffle, and a Kotlin baseline on
 * primality + Collatz + recursive Fibonacci. Rell source in `synthetic_bench/main.rell`.
 *
 * The single sample exercises one part of the runtime hot-path:
 *   - `collatz_primes_fib` → tight integer loop (`is_prime`, `collatz_steps`) plus a
 *                            recursive `fib(20)`. No collections, no JDK BigInteger /
 *                            BigDecimal — the bottleneck is `Rt_IntValue` boxing across
 *                            user-fn calls and `if`/`while` dispatch overhead. The
 *                            `kotlin` backend is a hand-compiled Kotlin reimplementation
 *                            kept as a "what's the JVM ceiling on this code" baseline.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
// Force the Graal optimizing compiler in JMH's forked JVM so Graal truffle-runtime engages.
@Fork(
    jvmArgsPrepend = [
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+EnableJVMCI",
        "-XX:+UseJVMCINativeLibrary",
        "--enable-native-access=ALL-UNNAMED",
    ],
)
class InterpreterBenchmark : RellBackendBenchmark() {

    @Param("interpreter", "truffle", "kotlin")
    lateinit var backend: String

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
        query = rrApp.module(ModuleName.EMPTY)!!.queries.getValue("bench")
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
