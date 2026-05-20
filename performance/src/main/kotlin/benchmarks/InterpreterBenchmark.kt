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
 * Synthetic microbenchmark on the **legacy R_Expr tree-walker** (this branch's only
 * interpreter — the master codebase predates the RR_App refactor and the Truffle backend).
 *
 * Mirrors rell2's `InterpreterBenchmark` so the two reports merge cleanly: same Rell source
 * (`synthetic_bench/main.rell`), same `sample = "collatz_primes_fib"`, same query name,
 * same input size. The `backend` axis carries the single value `r-expr-legacy` so this
 * branch's row sits next to rell2's `interpreter | truffle | kotlin` rows in the merged
 * report.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@Warmup(iterations = 10, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
class InterpreterBenchmark {

    /**
     * Single-value @Param so the JSON output carries a `backend` label that lines up
     * with rell2's `{interpreter, truffle, kotlin}` axis when results are merged.
     */
    @Param("r-expr-legacy")
    lateinit var backend: String

    /**
     * Single-value @Param so the report generator pivots this row by `sample` the same
     * way it does for [Ft4Benchmark].
     */
    @Param("collatz_primes_fib")
    lateinit var sample: String

    private val limitInt = 100_000L
    private val limit: List<Rt_Value> = listOf(Rt_IntValue.get(limitInt))

    private lateinit var app: R_App
    private lateinit var exeCtx: Rt_ExecutionContext
    private lateinit var query: R_QueryDefinition

    @Setup
    fun setUp() {
        val rellSource = loadRellResource("synthetic_bench/main.rell")
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
        query = app.moduleMap.getValue(R_ModuleName.EMPTY).queries.getValue("bench")
    }

    @Benchmark
    fun runQuery(blackhole: Blackhole) {
        blackhole.consume(query.call(exeCtx, limit))
    }
}

internal fun loadRellResource(path: String): String =
    InterpreterBenchmark::class.java.classLoader.getResourceAsStream(path)
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: error("Benchmark resource not found on classpath: $path")
