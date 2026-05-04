/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:Suppress("unused")

package net.postchain.rell.benchmarks

import com.oracle.truffle.api.Truffle
import kotlinx.benchmark.Blackhole

/**
 * Standalone diagnostic: drives a Truffle-backed benchmark long enough for compilation to
 * trigger, with stdout/stderr free of JMH framing so polyglot trace logs
 * (`-Dpolyglot.engine.TraceCompilation=true`, etc.) are easy to parse.
 *
 * Wire-up: `:benchmarks:traceTruffle` Gradle task supplies the JVMCI/Truffle flags, points
 * mainClass here, and runs against runtimeClasspath. The task fails loudly when Truffle
 * isn't running on top of Graal.
 *
 * Default workload is the synthetic primality/Collatz/fib query in [InterpreterBenchmark]
 * — small, predictable, easy to read in compilation logs. Set `TRACE_TARGET=ft4` plus
 * `TRACE_SAMPLE=<gtv_text|rule_serde|rule_eval>` to drive an [Ft4Benchmark] workload instead.
 */
fun main() {
    val rt = Truffle.getRuntime()
    println("=== Truffle runtime: ${rt.name} (${rt::class.java.name}) ===")
    check(rt.name != "Interpreted") {
        "Truffle is using the interpreter fallback - pass GraalVM/JVMCI flags to the JVM."
    }

    val target = System.getenv("TRACE_TARGET") ?: "synthetic"
    val reps = (System.getenv("TRACE_REPS")?.toInt()) ?: 30
    val blackhole = Blackhole(
        "Today's password is swordfish. I understand instantiating Blackholes directly is dangerous."
    )

    when (target) {
        "ft4" -> {
            val sample = System.getenv("TRACE_SAMPLE") ?: "gtv_text"
            val bm = Ft4Benchmark()
            bm.backend = "truffle"
            bm.sample = sample
            bm.setUp()
            println("=== Driving $reps iterations of Ft4Benchmark sample=$sample ===")
            for (i in 1..reps) {
                val start = System.nanoTime()
                bm.runQuery(blackhole)
                println("iter=$i ms=${(System.nanoTime() - start) / 1_000_000}")
            }
        }

        else -> {
            val bm = InterpreterBenchmark()
            bm.backend = "truffle"
            bm.setUp()
            println("=== Driving $reps iterations of InterpreterBenchmark (synthetic) ===")
            for (i in 1..reps) {
                val start = System.nanoTime()
                bm.runQuery(blackhole)
                println("iter=$i ms=${(System.nanoTime() - start) / 1_000_000}")
            }
        }
    }
}
