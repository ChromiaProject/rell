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
 * Real-world microbenchmark driven by code lifted out of
 * [chromaway/mna-blockchain](https://bitbucket.org/chromawallet/mna-blockchain) — the MNA dapp
 * (development @ 09617dc7). Three pure-compute, DB-free workloads with shapes orthogonal to the
 * three already covered by [Ft4Benchmark]:
 *
 *   * `decimal_pow` — `power` (binary exponentiation for integer exponents, Padé/Taylor
 *     transcendentals via `exp(exponent * ln(base))` for non-integer ones). Stresses
 *     `Rt_DecimalValue` arithmetic, while loops with convergence tests, recursion, and
 *     branch-heavy fast-path / slow-path dispatch.
 *   * `perlin_noise` — 8-octave 2D Simplex noise sampled across a 5×5 grid via
 *     `sum_octave_2d` → `simplex_2d`. Stresses list-indexed numerical kernels with mixed
 *     integer/decimal arithmetic — exactly the hot inner loop Truffle's PE should specialise.
 *   * `locations` — rotate a 64-point `list<location>` through four cardinal rotations,
 *     materialise each as a `set<location>`, and reduce with `area_for_locations`. Stresses
 *     struct construction, list iteration, enum dispatch via `when`, set hashing, and integer
 *     min/max reduction.
 *
 * The Rell source for all three lives in `src/main/resources/mna_bench/main.rell`. Same shape
 * as [Ft4Benchmark]: only the tree-walker and Truffle backends are compared; there is no clean
 * single-language Kotlin equivalent for these workloads.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@Warmup(iterations = 10, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
@Fork(
    jvmArgsPrepend = [
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+EnableJVMCI",
        "-XX:+UseJVMCINativeLibrary",
        "--enable-native-access=ALL-UNNAMED",
    ],
)
class MnaBenchmark : RellBackendBenchmark() {

    @Param("interpreter", "truffle")
    lateinit var backend: String

    /**
     * Picks one of the three mna-derived workloads exposed as queries by `mna_bench/main.rell`.
     * Named `sample` so the existing report generator pivots rows by workload without changes.
     */
    @Param("decimal_pow", "perlin_noise", "locations")
    lateinit var sample: String

    private lateinit var query: RR_QueryDefinition
    private var args: List<Rt_Value> = emptyList()

    @Setup
    fun setUp() {
        val rrApp = setUpBackend(backend, "mna_bench/main.rell")
        val (queryName, repsArg) = workloadConfig(sample)
        query = rrApp.moduleMap.getValue(ModuleName.EMPTY).queries.getValue(queryName)
        args = listOf(Rt_IntValue.get(repsArg))
    }

    @Benchmark
    fun runQuery(blackhole: Blackhole) = blackhole.consume(interpreter.callQuery(query, exeCtx, args))

    /**
     * Per-workload tuning. `reps` is sized so each invocation lands in the single-millisecond
     * range on the tree-walker — small enough that JMH's 2-second iterations get plenty of
     * samples, large enough to amortise per-call dispatch.
     */
    private fun workloadConfig(sample: String): Pair<String, Long> = when (sample) {
        "decimal_pow" -> "bench_decimal_pow" to 200L
        "perlin_noise" -> "bench_perlin_noise" to 20L
        "locations" -> "bench_locations" to 200L
        else -> error("Unknown sample: $sample")
    }
}

fun main() {
    val b = Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.")
    for (sample in listOf("decimal_pow", "perlin_noise", "locations")) {
        val bm = MnaBenchmark()
        bm.backend = "interpreter"
        bm.sample = sample
        bm.setUp()
        val start = System.nanoTime()
        bm.runQuery(b)
        println("smoke sample=$sample backend=interpreter elapsed_ms=${(System.nanoTime() - start) / 1_000_000}")
    }
}
