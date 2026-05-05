/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.benchmarks

import kotlinx.benchmark.Blackhole

fun main() {
    val b = Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.")
    for (sample in listOf("gtv_text", "rule_serde", "rule_eval")) {
        val bm = Ft4Benchmark()
        bm.backend = "r-expr-legacy"
        bm.sample = sample
        bm.setUp()
        val start = System.nanoTime()
        bm.runQuery(b)
        println("smoke sample=$sample backend=r-expr-legacy elapsed_ms=${(System.nanoTime() - start) / 1_000_000}")
    }
}
