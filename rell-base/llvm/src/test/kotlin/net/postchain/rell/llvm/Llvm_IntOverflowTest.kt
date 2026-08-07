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
 * C2 regression: a JIT'd integer arithmetic op that OVERFLOWS must raise the exact [Rt_Exception]
 * the tree-walker (`evalIntArith` in `rt_ops.kt`) would — same Rell error `code` — AND must still
 * take the native JIT path (so [Llvm_Backend.jitHits] is incremented, [Llvm_Backend.jitMisses] is
 * not). Before the fix, the overflow either surfaced as a bare `RuntimeException` (wrong error
 * class/code) or was computed on a poison `0` with no consumer — a consensus split. The fix records
 * the exact code into the thread-local integer-error channel and Kotlin re-raises it.
 *
 * Mirrors the setup of [Llvm_IntCoverageTest].
 */
class Llvm_IntOverflowTest {
    @Test
    fun `add overflow raises the exact Rell code on the JIT path`() {
        val (backend, exeCtx) = setup("function f(a: integer, b: integer): integer = a + b;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        // Non-overflow result still takes the native path and is bit-exact.
        assertEquals(5L, callInt(backend, exeCtx, fn, 2, 3))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)

        // Long.MAX_VALUE + 1 overflows: Math.addExact throws expr:+:overflow:<a>:<b>.
        val e = assertFailsWith<Rt_Exception> { callInt(backend, exeCtx, fn, Long.MAX_VALUE, 1L) }
        assertEquals("expr:+:overflow:${Long.MAX_VALUE}:1", (e.err as Rt_CommonError).code)
        // Still the JIT path — the overflow did NOT degrade to the interpreter.
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `mul overflow raises the exact Rell code on the JIT path`() {
        val (backend, exeCtx) = setup("function f(a: integer, b: integer): integer = a * b;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(12L, callInt(backend, exeCtx, fn, 3, 4))
        assertEquals(1, backend.jitHits)

        val e = assertFailsWith<Rt_Exception> { callInt(backend, exeCtx, fn, Long.MAX_VALUE, 2L) }
        assertEquals("expr:*:overflow:${Long.MAX_VALUE}:2", (e.err as Rt_CommonError).code)
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `unary minus overflow raises the exact Rell code on the JIT path`() {
        val (backend, exeCtx) = setup("function f(a: integer): integer = -a;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(-7L, callInt(backend, exeCtx, fn, 7))
        assertEquals(1, backend.jitHits)

        // -Long.MIN_VALUE overflows: subtractExact(0, v) throws expr:-:overflow:<v>.
        val e = assertFailsWith<Rt_Exception> { callInt(backend, exeCtx, fn, Long.MIN_VALUE) }
        assertEquals("expr:-:overflow:${Long.MIN_VALUE}", (e.err as Rt_CommonError).code)
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
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
