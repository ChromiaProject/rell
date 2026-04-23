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
 * End-to-end test that compiles a tiny Rell program, runs it through [Llvm_Backend], and
 * verifies the JIT path actually fires.
 *
 * Boundaries exercised:
 *   - Compile Rell source → RR_App (frontend).
 *   - Llvm_Compiler accepts the body and produces an opcode tape (Kotlin).
 *   - RellLlvmNative.compileFunction lowers the tape to LLVM IR and JITs it (C++/LLVM ORC).
 *   - Llvm_Backend.callFunction marshals Rt_IntValue args into a LongArray, invokes the
 *     native function, and wraps the result.
 *   - Llvm_Compiler rejects a body with control flow; Llvm_Backend transparently falls back
 *     to the tree-walker, with jitMisses incremented.
 */
class Llvm_BackendTest {

    @Test
    fun `integer add function is JITed and produces correct result`() {
        val (backend, exeCtx) = setup("function add(a: integer, b: integer): integer = a + b;")

        val addFn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["add"]!!
        val result = backend.callFunction(addFn, exeCtx, listOf(Rt_IntValue.get(2), Rt_IntValue.get(3)))

        assertEquals(5L, (result as Rt_IntValue).value)
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)

        // Second call hits the cache without recompiling.
        val again = backend.callFunction(addFn, exeCtx, listOf(Rt_IntValue.get(100), Rt_IntValue.get(50)))
        assertEquals(150L, (again as Rt_IntValue).value)
        assertEquals(2, backend.jitHits)
    }

    @Test
    fun `mixed arithmetic compiles to a single JIT function`() {
        val (backend, exeCtx) = setup(
            "function f(a: integer, b: integer, c: integer): integer = (a + b) * c - 7;"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!
        val args: List<Rt_Value> = listOf(Rt_IntValue.get(3), Rt_IntValue.get(4), Rt_IntValue.get(5))
        val result = backend.callFunction(fn, exeCtx, args)

        // (3 + 4) * 5 - 7 = 28
        assertEquals(28L, (result as Rt_IntValue).value)
        assertEquals(1, backend.jitHits)
    }

    @Test
    fun `non-integer body falls back to the tree-walker`() {
        val (backend, exeCtx) = setup("function greet(): text = 'hello';")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["greet"]!!
        val result = backend.callFunction(fn, exeCtx, listOf())

        assertEquals("hello", (result as net.postchain.rell.base.runtime.Rt_TextValue).value)
        assertEquals(0, backend.jitHits)
        assertEquals(1, backend.jitMisses)
    }

    @Test
    fun `control flow falls back to the tree-walker`() {
        val (backend, exeCtx) = setup(
            // `if` is outside the JIT slice: not compilable, must delegate.
            "function pick(a: integer, b: integer): integer { if (a > b) return a; return b; }"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["pick"]!!
        val result = backend.callFunction(fn, exeCtx, listOf(Rt_IntValue.get(7), Rt_IntValue.get(3)))

        assertEquals(7L, (result as Rt_IntValue).value)
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
