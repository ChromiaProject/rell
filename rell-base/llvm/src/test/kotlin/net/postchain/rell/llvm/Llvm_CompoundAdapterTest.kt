/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.llvm

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.runtime.Rt_AppContext
import net.postchain.rell.base.runtime.Rt_ChainContext
import net.postchain.rell.base.runtime.Rt_CommonError
import net.postchain.rell.base.runtime.Rt_Exception
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
import kotlin.test.assertFailsWith

/**
 * REVIEW_coverage target 3: compound assignment (`+=` / `-=` / `*=`) to a local integer slot and
 * the mechanical integer-widening TypeAdapter declarators (`INTEGER_TO_BIG_INTEGER` /
 * `INTEGER_TO_DECIMAL`) are lowered natively by `lower_stmt.cpp` instead of soft-failing the
 * function.
 *
 * Compound assign desugars to the SAME checked integer intrinsic (`intrinsicInteger`) as the plain
 * binary op, so `+=` overflow raises the identical Rell code the tree-walker would. The widening
 * adapters re-tag the i64 payload (BIGINT_LONG / DEC_LONG, scale 0) — bit-exact with the
 * interpreter's integer->big_integer / integer->decimal conversion (value.cpp rebox).
 *
 * Mirrors the setup of [Llvm_IntCoverageTest] / [Llvm_IntOverflowTest].
 */
class Llvm_CompoundAdapterTest {
    @Test
    fun `compound add-assign to a local JITs and is bit-exact`() {
        val (backend, exeCtx) = setup(
            """
            function acc(n: integer): integer {
                var s = 0;
                s += n;
                s += n * 2;
                return s;
            }
            """.trimIndent()
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["acc"]!!

        assertEquals(30L, callInt(backend, exeCtx, fn, 10))  // 10 + 20
        assertEquals(0L, callInt(backend, exeCtx, fn, 0))
        assertEquals(0, backend.jitMisses)
        assertEquals(2, backend.jitHits)
    }

    @Test
    fun `compound sub-assign and mul-assign JIT and are bit-exact`() {
        val (backend, exeCtx) = setup(
            """
            function f(a: integer, b: integer): integer {
                var x = a;
                x -= b;
                x *= 3;
                return x;
            }
            """.trimIndent()
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(21L, callInt(backend, exeCtx, fn, 10, 3))  // (10 - 3) * 3
        assertEquals(0, backend.jitMisses)
        assertEquals(1, backend.jitHits)
    }

    @Test
    fun `compound add-assign overflow raises the exact code on the JIT path`() {
        val (backend, exeCtx) = setup(
            """
            function f(a: integer, b: integer): integer {
                var s = a;
                s += b;
                return s;
            }
            """.trimIndent()
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(5L, callInt(backend, exeCtx, fn, 2, 3))
        assertEquals(1, backend.jitHits)

        // Long.MAX_VALUE += 1 overflows: the desugared checked add records expr:+:overflow:<a>:<b>,
        // exactly as the plain `a + b` binary op does (Math.addExact in rt_ops.kt).
        val e = assertFailsWith<Rt_Exception> { callInt(backend, exeCtx, fn, Long.MAX_VALUE, 1L) }
        assertEquals("expr:+:overflow:${Long.MAX_VALUE}:1", (e.err as Rt_CommonError).code)
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `integer-to-big_integer adapter declarator JITs`() {
        // The `var b: big_integer = n;` declarator carries an INTEGER_TO_BIG_INTEGER adapter; the
        // function returns integer (value-ABI gate) so the widened local is internal. Before the
        // fix the adapter declarator soft-failed the whole function; now it re-tags inline and the
        // function JITs with a bit-exact integer result.
        val (backend, exeCtx) = setup(
            """
            function f(n: integer): integer {
                var b: big_integer = n;
                return n + 1;
            }
            """.trimIndent()
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(8L, callInt(backend, exeCtx, fn, 7))
        assertEquals(1L, callInt(backend, exeCtx, fn, 0))
        assertEquals(0, backend.jitMisses)
        assertEquals(2, backend.jitHits)
    }

    @Test
    fun `integer-to-decimal adapter declarator JITs`() {
        val (backend, exeCtx) = setup(
            """
            function f(n: integer): integer {
                var d: decimal = n;
                return n * 2;
            }
            """.trimIndent()
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(14L, callInt(backend, exeCtx, fn, 7))
        assertEquals(0, backend.jitMisses)
        assertEquals(1, backend.jitHits)
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
