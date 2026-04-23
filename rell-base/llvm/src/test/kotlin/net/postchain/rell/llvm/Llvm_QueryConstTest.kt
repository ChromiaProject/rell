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
import net.postchain.rell.base.runtime.Rt_TextValue
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * End-to-end tests proving the JIT now covers QUERIES and GLOBAL CONSTANTS in addition to
 * functions, reusing the integer i64 lowering. Same harness as [Llvm_BackendTest].
 */
class Llvm_QueryConstTest {
    @Test
    fun `integer query with control flow is JITed`() {
        val (backend, exeCtx) = setup(
            "query q(a: integer): integer { if (a > 0) return a; return -a; }"
        )
        val query = backend.rrApp.module(ModuleName.EMPTY)!!.queries["q"]!!

        val pos = backend.callQuery(query, exeCtx, listOf(Rt_IntValue.get(5)))
        assertEquals(5L, (pos as Rt_IntValue).value)
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)

        val neg = backend.callQuery(query, exeCtx, listOf(Rt_IntValue.get(-8)))
        assertEquals(8L, (neg as Rt_IntValue).value)
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `integer global constant is JITed through evaluateConstant`() {
        val (backend, exeCtx) = setup("val K: integer = 6 * 7;")
        val const = backend.rrApp.allConstants.single()

        // The constant may already have been evaluated through the JIT during app setup, so capture
        // the baseline and assert that this explicit evaluation adds exactly one more JIT hit.
        val hitsBefore = backend.jitHits
        val missesBefore = backend.jitMisses
        val result = backend.evaluateConstant(const, exeCtx)
        assertEquals(42L, (result as Rt_IntValue).value)
        assertEquals(hitsBefore + 1, backend.jitHits)
        assertEquals(missesBefore, backend.jitMisses)
    }

    @Test
    fun `text query soft-fails to the interpreter`() {
        val (backend, exeCtx) = setup("query greet(): text = 'hello';")
        val query = backend.rrApp.module(ModuleName.EMPTY)!!.queries["greet"]!!

        val result = backend.callQuery(query, exeCtx, listOf())
        assertEquals("hello", (result as Rt_TextValue).value)
        assertEquals(0, backend.jitHits)
        assertEquals(1, backend.jitMisses)
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
