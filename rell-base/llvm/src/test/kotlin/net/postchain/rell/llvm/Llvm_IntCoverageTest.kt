/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.llvm

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.runtime.Rt_AppContext
import net.postchain.rell.base.runtime.Rt_ChainContext
import net.postchain.rell.base.runtime.Rt_ExecutionContext
import net.postchain.rell.base.runtime.Rt_GlobalContext
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_NopPrinter
import net.postchain.rell.base.runtime.Rt_NullOpContext
import net.postchain.rell.base.runtime.Rt_NullSqlContext
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Coverage for the extended integer/boolean JIT slice in `jni_bridge.cpp`'s [Lowerer]: general
 * statement lowering (Block/Var/Assign/If/While/Break/Continue/Return), integer comparisons,
 * boolean short-circuit / NOT, if-expressions, and direct self-recursion.
 *
 * Each test asserts via [Llvm_Backend.jitHits] / [Llvm_Backend.jitMisses] that the function
 * actually took the native path (and produces the same result the tree-walker would), or — for
 * div/mod — that it safely soft-falls back to the interpreter.
 */
class Llvm_IntCoverageTest {
    @Test
    fun `if-return control flow now JITs`() {
        val (backend, exeCtx) =
            setup("function f(a: integer, b: integer): integer { if (a > b) return a; return b; }")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(7L, callInt(backend, exeCtx, fn, 7, 3))
        assertEquals(0, backend.jitMisses)
        assertEquals(1, backend.jitHits)

        assertEquals(9L, callInt(backend, exeCtx, fn, 4, 9))
        assertEquals(0, backend.jitMisses)
        assertEquals(2, backend.jitHits)
    }

    @Test
    fun `while-loop sum JITs`() {
        val (backend, exeCtx) = setup(
            """
            function sum(n: integer): integer {
                var acc = 0;
                var i = 1;
                while (i <= n) {
                    acc += i;
                    i += 1;
                }
                return acc;
            }
            """.trimIndent()
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["sum"]!!

        assertEquals(55L, callInt(backend, exeCtx, fn, 10))   // 1+...+10
        assertEquals(0L, callInt(backend, exeCtx, fn, 0))
        assertEquals(0, backend.jitMisses)
        assertEquals(2, backend.jitHits)
    }

    @Test
    fun `self-recursive fib JITs`() {
        val (backend, exeCtx) = setup(
            """
            function fib(n: integer): integer {
                if (n < 2) return n;
                return fib(n - 1) + fib(n - 2);
            }
            """.trimIndent()
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["fib"]!!

        assertEquals(0L, callInt(backend, exeCtx, fn, 0))
        assertEquals(1L, callInt(backend, exeCtx, fn, 1))
        assertEquals(55L, callInt(backend, exeCtx, fn, 10))
        assertEquals(832040L, callInt(backend, exeCtx, fn, 30))
        // Every top-level call took the native path; the recursive calls run entirely inside
        // the JIT'd function (one outer hit per callFunction), none miss.
        assertEquals(0, backend.jitMisses)
        assertEquals(4, backend.jitHits)
    }

    @Test
    fun `boolean logic and not JIT`() {
        val (backend, exeCtx) = setup(
            "function pick(a: integer, b: integer): integer { if (a > 0 and not (b > a)) return a; return b; }"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["pick"]!!

        assertEquals(5L, callInt(backend, exeCtx, fn, 5, 3))   // a>0 && !(b>a) → true → a
        assertEquals(8L, callInt(backend, exeCtx, fn, 5, 8))   // b>a true → not(..) false → b
        assertEquals(2L, callInt(backend, exeCtx, fn, -1, 2))  // a>0 false → short-circuit → b
        assertEquals(0, backend.jitMisses)
        assertEquals(3, backend.jitHits)
    }

    @Test
    fun `mod safely soft-fails to the interpreter`() {
        val (backend, exeCtx) = setup("function m(a: integer, b: integer) = a % b;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["m"]!!

        // Correct result via the tree-walker; the JIT must NOT have lowered sdiv/srem.
        assertEquals(2L, callInt(backend, exeCtx, fn, 17, 5))
        assertEquals(1, backend.jitMisses)
        assertEquals(0, backend.jitHits)
    }

    @Test
    fun `div safely soft-fails to the interpreter`() {
        val (backend, exeCtx) = setup("function d(a: integer, b: integer) = a / b;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["d"]!!

        assertEquals(3L, callInt(backend, exeCtx, fn, 17, 5))
        assertEquals(1, backend.jitMisses)
        assertEquals(0, backend.jitHits)
    }

    private fun callInt(
        backend: Llvm_Backend,
        exeCtx: Rt_ExecutionContext,
        fn: net.postchain.rell.base.model.rr.RR_FunctionDefinition,
        vararg args: Long,
    ): Long {
        val rtArgs: List<Rt_Value> = args.map { Rt_IntValue.get(it) }
        val result = backend.callFunction(fn, exeCtx, rtArgs)
        return (result as Rt_IntValue).value
    }

    private fun setup(code: String): Pair<Llvm_Backend, Rt_ExecutionContext> {
        val sourceDir = C_SourceDir.mapDirOf(RellTestUtils.MAIN_FILE to code)
        val modSel = C_CompilerModuleSelection(immListOf(ModuleName.EMPTY), immListOf())
        val cRes = RellTestUtils.compileApp(sourceDir, modSel, RellTestUtils.DEFAULT_COMPILER_OPTIONS)
        check(cRes.errors.isEmpty()) { "Compilation errors: ${cRes.errors.map { it.code }}" }
        val rrApp = checkNotNull(cRes.rrApp) { "compileApp returned null RR_App" }

        val backend = Llvm_Backend.forCompilation(rrApp, cRes.compilationSysFns)

        val globalCtx = Rt_GlobalContext(
            RellTestUtils.DEFAULT_COMPILER_OPTIONS,
            Rt_NopPrinter,
            Rt_NopPrinter,
            typeCheck = false,
        )

        val appCtx = Rt_AppContext(globalCtx, Rt_ChainContext.NULL, backend)
        val sqlCtx = Rt_NullSqlContext.create(rrApp.sqlDefs)
        val exeCtx = Rt_ExecutionContext(appCtx, Rt_NullOpContext, sqlCtx, NoConnSqlExecutor)
        return backend to exeCtx
    }
}
