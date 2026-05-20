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
import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.model.R_QueryDefinition
import net.postchain.rell.base.runtime.Rt_AppContext
import net.postchain.rell.base.runtime.Rt_ChainContext
import net.postchain.rell.base.runtime.Rt_ExecutionContext
import net.postchain.rell.base.runtime.Rt_GlobalContext
import net.postchain.rell.base.runtime.Rt_NopPrinter
import net.postchain.rell.base.runtime.Rt_NullOpContext
import net.postchain.rell.base.runtime.Rt_NullSqlContext
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf

/**
 * Real-world microbenchmark on the **legacy R_Expr tree-walker** (master only has this
 * single backend). Driven by code lifted out of
 * [chromaway/ft4-lib](https://gitlab.com/chromaway/ft4-lib) — the canonical Rell library
 * that most Chromia dapps depend on. Three pure-compute, DB-free workloads:
 *
 *   * `gtv_text` — `convert_gtv_to_text`, recursive pretty-printer over a nested gtv.
 *   * `rule_serde` — round-trip a list of `rule_expression`s through gtv.
 *   * `rule_eval` — evaluate a parsed rule set against a `map<text, gtv>` of variables.
 *
 * The Rell source for all three lives in `src/main/resources/ft4_bench/main.rell`.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@Warmup(iterations = 10, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
class Ft4Benchmark {

    @Param("r-expr-legacy")
    lateinit var backend: String

    /**
     * Picks one of the three ft4-derived workloads exposed as queries by `ft4_bench/main.rell`.
     * Named `sample` so the report generator pivots rows by workload.
     */
    @Param("gtv_text", "rule_serde", "rule_eval")
    lateinit var sample: String

    private lateinit var app: R_App
    private lateinit var exeCtx: Rt_ExecutionContext
    private lateinit var query: R_QueryDefinition
    private var args: List<Rt_Value> = emptyList()

    @Setup
    fun setUp() {
        val rellSource = loadRellResource("ft4_bench/main.rell")

        val sourceDir = C_SourceDir.mapDirOf(RellTestUtils.MAIN_FILE to rellSource)
        val modSel = C_CompilerModuleSelection(immListOf(R_ModuleName.EMPTY), immListOf())
        val cRes = RellTestUtils.compileApp(sourceDir, modSel, RellTestUtils.DEFAULT_COMPILER_OPTIONS)
        check(cRes.errors.isEmpty()) {
            "Compilation errors: ${cRes.errors.joinToString("\n") { "${it.pos} ${it.code}: ${it.text}" }}"
        }

        app = cRes.app!!

        val globalCtx = Rt_GlobalContext(
            RellTestUtils.DEFAULT_COMPILER_OPTIONS,
            Rt_NopPrinter,
            Rt_NopPrinter,
            typeCheck = false,
        )
        val appCtx = Rt_AppContext(globalCtx, Rt_ChainContext.NULL, app)
        val sqlCtx = Rt_NullSqlContext.create(app)
        exeCtx = Rt_ExecutionContext(appCtx, Rt_NullOpContext, sqlCtx, NoConnSqlExecutor)

        val (queryName, repsArg) = workloadConfig(sample)
        query = app.moduleMap.getValue(R_ModuleName.EMPTY).queries.getValue(queryName)
        args = listOf(Rt_IntValue.get(repsArg))
    }

    @Benchmark
    fun runQuery(blackhole: Blackhole) {
        blackhole.consume(query.call(exeCtx, args))
    }

    /**
     * Per-workload tuning. The `reps` argument matches rell2's sizing so cross-branch
     * results are directly comparable.
     */
    private fun workloadConfig(sample: String): Pair<String, Long> = when (sample) {
        "gtv_text" -> "bench_gtv_text" to 200L
        "rule_serde" -> "bench_rule_serde" to 500L
        "rule_eval" -> "bench_rule_eval" to 5_000L
        else -> error("Unknown sample: $sample")
    }
}
