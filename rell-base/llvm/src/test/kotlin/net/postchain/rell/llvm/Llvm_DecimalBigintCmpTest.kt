/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.llvm

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.runtime.Rt_AppContext
import net.postchain.rell.base.runtime.Rt_BooleanValue
import net.postchain.rell.base.runtime.Rt_ChainContext
import net.postchain.rell.base.runtime.Rt_DecimalValue
import net.postchain.rell.base.runtime.Rt_BigIntegerValue
import net.postchain.rell.base.runtime.Rt_ExecutionContext
import net.postchain.rell.base.runtime.Rt_GlobalContext
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
 * REVIEW_coverage target 2 — NATIVE decimal / big_integer comparison, bit-exact vs the interpreter.
 *
 * The long-fit envelope now carries `decimal`/`big_integer` inline: `value.cpp::from_jvm` cracks a
 * long-fit decimal into a canonical stripped DEC_LONG (and a long-fit big_integer into BIGINT_LONG),
 * and `lower_ops.cpp::lowerComparison` lowers `<`/`>`/`<=`/`>=` natively — a scale-aware mantissa
 * compare for DEC_LONG (aligning by 10^scaleDelta) and a signed i64 compare for BIGINT_LONG. A wide
 * operand (a HANDLE at runtime) or a scale-alignment overflow ESCAPES via `rell_jit_escape`, so the
 * whole call re-runs on the interpreter.
 *
 * This test pins the result is bit-exact (including the scale-aware `1.0 == 1.00` EQUAL case the
 * review names — trivial because from_jvm hands stripped canonical forms, both `(1, 0)`) AND that
 * the long-fit comparison takes the NATIVE path (`jitHits` increments, `jitMisses` stays put). Should
 * anyone break the scale-aware compare or the canonical-form invariant, the bit-exact assertions here
 * are the regression tripwire.
 */
class Llvm_DecimalBigintCmpTest {
    @Test
    fun `decimal less-than is native and bit-exact`() {
        val (backend, exeCtx) = setup("function lt(a: decimal, b: decimal): boolean = a < b;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["lt"]!!

        val hits0 = backend.jitHits
        val misses0 = backend.jitMisses

        assertEquals(true, callBool(backend, exeCtx, fn, dec("1.5"), dec("2.0")))
        assertEquals(false, callBool(backend, exeCtx, fn, dec("2.0"), dec("1.5")))
        assertEquals(false, callBool(backend, exeCtx, fn, dec("1.0"), dec("1.00")))  // equal -> not <

        // Long-fit comparison took the native path: every call hit, none missed.
        assertEquals(hits0 + 3, backend.jitHits)
        assertEquals(misses0, backend.jitMisses)
    }

    @Test
    fun `decimal scale-aware equality 1_0 equals 1_00 natively`() {
        val (backend, exeCtx) = setup("function eq(a: decimal, b: decimal): boolean = a == b;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["eq"]!!

        // 1.0 and 1.00 differ in their source (mantissa, scale) but from_jvm strips both to the
        // canonical (1, 0); the scale-aware compare yields sign 0 -> EQUAL. This is exactly the case a
        // naive raw-mantissa icmp would get wrong; the canonical normal form (M3) makes it correct.
        assertEquals(true, callBool(backend, exeCtx, fn, dec("1.0"), dec("1.00")))
        assertEquals(true, callBool(backend, exeCtx, fn, dec("1.50"), dec("1.5")))
        assertEquals(false, callBool(backend, exeCtx, fn, dec("1.0"), dec("1.01")))
        assertEquals(3, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `big_integer comparison is native and bit-exact`() {
        val (backend, exeCtx) = setup("function ge(a: big_integer, b: big_integer): boolean = a >= b;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["ge"]!!

        assertEquals(true, callBool(backend, exeCtx, fn, big(5), big(5)))
        assertEquals(true, callBool(backend, exeCtx, fn, big(7), big(3)))
        assertEquals(false, callBool(backend, exeCtx, fn, big(3), big(7)))
        assertEquals(3, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    private fun dec(s: String): Rt_Value = Rt_DecimalValue.get(s)
    private fun big(v: Long): Rt_Value = Rt_BigIntegerValue.get(v)

    private fun callBool(
        backend: Llvm_Backend,
        exeCtx: Rt_ExecutionContext,
        fn: net.postchain.rell.base.model.rr.RR_FunctionDefinition,
        vararg args: Rt_Value,
    ): Boolean {
        val result = backend.callFunction(fn, exeCtx, args.toList())
        return (result as Rt_BooleanValue).value
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
