/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:Suppress("unused")

package net.postchain.rell.performance.benchmarks

import kotlinx.benchmark.Blackhole

/**
 * Smoke: compile + run one invocation of every new benchmark to catch Rell source / wiring
 * errors before paying for a full JMH cycle.
 */
fun main() {
    smokeStruct()
    smokeMna()
    smokeInterpreter()
    smokeParser()
}

private fun smokeStruct() {
    val bh = Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.")
    for (sample in listOf("dto_mapping", "cursor_codec", "multi_sig")) {
        val bm = StructBenchmark()
        bm.backend = "r-expr-legacy"
        bm.sample = sample
        bm.setUp()
        val t0 = System.nanoTime()
        bm.runQuery(bh)
        println("smoke struct sample=$sample elapsed_ms=${(System.nanoTime() - t0) / 1_000_000}")
    }
}

private fun smokeMna() {
    val bh = Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.")
    for (sample in listOf("decimal_pow", "perlin_noise", "locations")) {
        val bm = MnaBenchmark()
        bm.backend = "r-expr-legacy"
        bm.sample = sample
        bm.setUp()
        val t0 = System.nanoTime()
        bm.runQuery(bh)
        println("smoke mna sample=$sample elapsed_ms=${(System.nanoTime() - t0) / 1_000_000}")
    }
}

private fun smokeInterpreter() {
    val bh = Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.")
    val bm = InterpreterBenchmark()
    bm.backend = "r-expr-legacy"
    bm.sample = "collatz_primes_fib"
    bm.setUp()
    val t0 = System.nanoTime()
    bm.runQuery(bh)
    println("smoke interpreter elapsed_ms=${(System.nanoTime() - t0) / 1_000_000}")
}

private fun smokeParser() {
    val bh = Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.")
    for (sample in listOf("profiling-dapp", "deathmatch", "operations_queries_api")) {
        val bm = ParserBenchmark()
        bm.sample = sample
        bm.setUp()
        val t0 = System.nanoTime()
        bm.betterParse(bh)
        bm.antlrSLL(bh)
        println("smoke parser sample=$sample elapsed_ms=${(System.nanoTime() - t0) / 1_000_000}")
    }
}
