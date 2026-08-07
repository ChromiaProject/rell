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
 * REVIEW_coverage target 1: the integer stdlib overlay (`abs` / `sign` / `min` / `max` on the
 * integer leaf) is now lowered to PURE INLINE IR by `lower_call.cpp` (`tryInlineSysFn` ->
 * `tryEmitIntegerSysFn`) instead of soft-failing the whole function. The overlay emits no
 * `rell_sysfn_call` back-call, so it dodges the by-value return-ABI hazard that keeps the generic
 * sys-call path soft-failing.
 *
 * Each test asserts (a) the function takes the native path (`jitHits` increments, `jitMisses`
 * stays 0) and (b) the result equals the interpreter's — including the `abs(Long.MIN_VALUE)`
 * overflow, which must raise the SAME Rell error code (`abs:integer:overflow:<v>`) the tree-walker
 * (`Lib_Math.Abs_Integer`) raises, routed through the integer-error channel.
 *
 * Mirrors the setup of [Llvm_IntCoverageTest] / [Llvm_IntOverflowTest].
 */
class Llvm_IntSysFnInlineTest {
    @Test
    fun `integer min and max inline on the JIT path`() {
        val (backend, exeCtx) = setup(
            "function lo(a: integer, b: integer): integer = min(a, b);\n" +
                "function hi(a: integer, b: integer): integer = max(a, b);"
        )
        val lo = backend.rrApp.module(ModuleName.EMPTY)!!.functions["lo"]!!
        val hi = backend.rrApp.module(ModuleName.EMPTY)!!.functions["hi"]!!

        assertEquals(3L, callInt(backend, exeCtx, lo, 3, 7))
        assertEquals(7L, callInt(backend, exeCtx, lo, 7, 7))
        assertEquals(-5L, callInt(backend, exeCtx, lo, -5, 2))
        assertEquals(7L, callInt(backend, exeCtx, hi, 3, 7))
        assertEquals(2L, callInt(backend, exeCtx, hi, -5, 2))
        assertEquals(0, backend.jitMisses)
        assertEquals(5, backend.jitHits)
    }

    @Test
    fun `integer abs inlines and is bit-exact`() {
        val (backend, exeCtx) = setup("function f(a: integer): integer = abs(a);")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(5L, callInt(backend, exeCtx, fn, 5))
        assertEquals(5L, callInt(backend, exeCtx, fn, -5))
        assertEquals(0L, callInt(backend, exeCtx, fn, 0))
        assertEquals(Long.MAX_VALUE, callInt(backend, exeCtx, fn, Long.MAX_VALUE))
        assertEquals(0, backend.jitMisses)
        assertEquals(4, backend.jitHits)
    }

    @Test
    fun `integer abs of Long-MIN raises the exact overflow code on the JIT path`() {
        val (backend, exeCtx) = setup("function f(a: integer): integer = abs(a);")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        // abs(Long.MIN_VALUE) has no representable positive: Lib_Math.Abs_Integer throws
        // "abs:integer:overflow:<v>". The inline overlay records the same code into the integer-error
        // channel and Kotlin re-raises it; the function must NOT degrade to the interpreter.
        val e = assertFailsWith<Rt_Exception> { callInt(backend, exeCtx, fn, Long.MIN_VALUE) }
        assertEquals("abs:integer:overflow:${Long.MIN_VALUE}", (e.err as Rt_CommonError).code)
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `integer sign inlines and is bit-exact`() {
        val (backend, exeCtx) = setup("function f(a: integer): integer = a.sign();")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(1L, callInt(backend, exeCtx, fn, 42))
        assertEquals(-1L, callInt(backend, exeCtx, fn, -42))
        assertEquals(0L, callInt(backend, exeCtx, fn, 0))
        assertEquals(1L, callInt(backend, exeCtx, fn, Long.MAX_VALUE))
        assertEquals(-1L, callInt(backend, exeCtx, fn, Long.MIN_VALUE))
        assertEquals(0, backend.jitMisses)
        assertEquals(5, backend.jitHits)
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
