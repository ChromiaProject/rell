/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:Suppress("unused")
@file:JvmName("TruffleTraceRunnerKt")

package net.postchain.rell.performance.benchmarks

import com.oracle.truffle.api.Truffle
import kotlinx.benchmark.Blackhole

/**
 * Drives a Truffle-backed benchmark without JMH framing so polyglot trace logs
 * (`-Dpolyglot.engine.TraceCompilation=true`, etc.) are easy to parse. Defaults to the
 * synthetic [InterpreterBenchmark] query; `TRACE_TARGET=ft4` + `TRACE_SAMPLE=...` selects
 * an [Ft4Benchmark] workload.
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
