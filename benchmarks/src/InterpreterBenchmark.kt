/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:Suppress("unused")

package net.postchain.rell.benchmarks

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
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf

/**
 * Benchmark for the legacy `R_Expr` tree-walking interpreter.
 *
 * Runs a CPU-bound Rell query (prime sieve + Collatz + recursive Fibonacci) end-to-end
 * through `R_QueryDefinition.call`, exercising the same interpreter paths used by
 * production query evaluation (no SQL, no I/O).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
class InterpreterBenchmark {
    /**
     * Single-value param so the JSON output carries a `backend` label that
     * lines up with rell2's `InterpreterBenchmark.backend = {interpreter,
     * truffle, kotlin}` axis when results are merged into one report.
     */
    @Param("r-expr-legacy")
    lateinit var backend: String

    private val code = """
        function is_prime(n: integer): boolean {
            if (n < 2) { return false; }
            if (n < 4) { return true; }
            if (n % 2 == 0) { return false; }
            var i = 3;
            while (i * i <= n) {
                if (n % i == 0) { return false; }
                i += 2;
            }
            return true;
        }

        function collatz_steps(n: integer): integer {
            var m = n;
            var steps = 0;
            while (m > 1) {
                if (m % 2 == 0) {
                    m = m / 2;
                } else {
                    m = 3 * m + 1;
                }
                steps += 1;
            }
            return steps;
        }

        function fib(n: integer): integer {
            if (n < 2) { return n; }
            return fib(n - 1) + fib(n - 2);
        }

        query bench(cap: integer): integer {
            var acc = 0;
            var i = 2;
            while (i <= cap) {
                if (is_prime(i)) {
                    acc += collatz_steps(i);
                }
                i += 1;
            }
            return acc + fib(20);
        }
    """.trimIndent()

    private val limit: List<Rt_Value> = listOf(Rt_IntValue.get(100_000L))

    private lateinit var app: R_App
    private lateinit var exeCtx: Rt_ExecutionContext
    private lateinit var query: R_QueryDefinition

    @Setup
    fun setUp() {
        val sourceDir = C_SourceDir.mapDirOf(RellTestUtils.MAIN_FILE to code)
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
        query = app.moduleMap[R_ModuleName.EMPTY]!!.queries.getValue("bench")
    }

    @Benchmark
    fun runQuery(blackhole: Blackhole) {
        blackhole.consume(query.call(exeCtx, limit))
    }
}
