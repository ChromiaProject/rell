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
 * Coverage for the native lowering of [for-over-range][net.postchain.rell.base.model.rr.RR_Statement.For]
 * and [when-statement][net.postchain.rell.base.model.rr.RR_Statement.When] in `lower_stmt.cpp`.
 *
 * For-over-range is bit-exact with `Rt_RangeValue.RangeIterator` (rt_value_range.kt): exclusive end,
 * sign-of-step bound test, saturatedAdd advance, empty-range = zero iterations. The JVM builds and
 * validates the range (so a bad step/direction still raises the exact `fn_range_args` exception); the
 * native loop only iterates the decoded (start, end, step) Long fields.
 *
 * When-statement reuses the exact chooser semantics + inline-key restriction of when-expression
 * (BOOLEAN/INTEGER/ROWID/ENUM canonical inline carriers only). Non-inline keys (text/enum) soft-fail.
 *
 * Each positive test asserts the function took the native path ([Llvm_Backend.jitHits] increments,
 * [Llvm_Backend.jitMisses]==0) AND produced the tree-walker's result. Each negative test asserts a
 * clean whole-function soft-fall to the interpreter (jitMisses==1) with the correct value.
 */
class Llvm_ForWhenCoverageTest {
    // ---- for-over-range: positive (native) ----------------------------------------------

    @Test
    fun `for over range sums 0 until n`() {
        val (backend, exeCtx) = setup(
            """
            function sum(n: integer): integer {
                var acc = 0;
                for (i in range(n)) {
                    acc += i;
                }
                return acc;
            }
            """.trimIndent()
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["sum"]!!

        // range(n) == range(0, n, 1): 0,1,...,n-1 (exclusive end). Sum = n*(n-1)/2.
        assertEquals(10L, callInt(backend, exeCtx, fn, 5))   // 0+1+2+3+4
        assertEquals(45L, callInt(backend, exeCtx, fn, 10))
        assertEquals(0, backend.jitMisses)
        assertEquals(2, backend.jitHits)
    }

    @Test
    fun `for over range with explicit start end JITs`() {
        val (backend, exeCtx) = setup(
            """
            function sum(lo: integer, hi: integer): integer {
                var acc = 0;
                for (i in range(lo, hi)) {
                    acc += i;
                }
                return acc;
            }
            """.trimIndent()
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["sum"]!!

        assertEquals(2L + 3 + 4, callInt(backend, exeCtx, fn, 2, 5))  // 2,3,4 (5 exclusive)
        assertEquals(0, backend.jitMisses)
        assertEquals(1, backend.jitHits)
    }

    @Test
    fun `for over range with break JITs`() {
        val (backend, exeCtx) = setup(
            """
            function firstOver(n: integer, lim: integer): integer {
                var found = -1;
                for (i in range(n)) {
                    if (i >= lim) {
                        found = i;
                        break;
                    }
                }
                return found;
            }
            """.trimIndent()
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["firstOver"]!!

        assertEquals(3L, callInt(backend, exeCtx, fn, 10, 3))   // first i >= 3
        assertEquals(-1L, callInt(backend, exeCtx, fn, 3, 100)) // never reached
        assertEquals(0, backend.jitMisses)
        assertEquals(2, backend.jitHits)
    }

    @Test
    fun `for over range with continue JITs`() {
        val (backend, exeCtx) = setup(
            """
            function sumSkipping(n: integer, skip: integer): integer {
                var acc = 0;
                for (i in range(n)) {
                    if (i == skip) continue;
                    acc += i;
                }
                return acc;
            }
            """.trimIndent()
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["sumSkipping"]!!

        // range(5) = 0,1,2,3,4; skipping 3 -> 0+1+2+4 = 7. `continue` must re-test hasNext WITHOUT
        // re-advancing (advance already happened at body entry), exactly as the interpreter's
        // iterator does. Only native, comparison/compound-add ops are used so the function stays JIT.
        assertEquals(7L, callInt(backend, exeCtx, fn, 5, 3))
        assertEquals(10L, callInt(backend, exeCtx, fn, 5, 9))  // skip never matches -> 0+1+2+3+4
        assertEquals(0, backend.jitMisses)
        assertEquals(2, backend.jitHits)
    }

    @Test
    fun `empty range yields zero iterations`() {
        val (backend, exeCtx) = setup(
            """
            function count(n: integer): integer {
                var c = 0;
                for (i in range(n, n)) {
                    c += 1;
                }
                return c;
            }
            """.trimIndent()
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["count"]!!

        // range(n, n) is empty (start == end, exclusive) -> zero iterations -> 0.
        assertEquals(0L, callInt(backend, exeCtx, fn, 5))
        assertEquals(0L, callInt(backend, exeCtx, fn, 0))
        assertEquals(0, backend.jitMisses)
        assertEquals(2, backend.jitHits)
    }

    @Test
    fun `nested for over range JITs`() {
        val (backend, exeCtx) = setup(
            """
            function grid(n: integer, m: integer): integer {
                var acc = 0;
                for (i in range(n)) {
                    for (j in range(m)) {
                        acc += 1;
                    }
                }
                return acc;
            }
            """.trimIndent()
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["grid"]!!

        assertEquals(12L, callInt(backend, exeCtx, fn, 3, 4))  // 3*4 iterations
        assertEquals(0L, callInt(backend, exeCtx, fn, 0, 5))   // outer empty
        assertEquals(0, backend.jitMisses)
        assertEquals(2, backend.jitHits)
    }

    // ---- when-statement: positive (native) ----------------------------------------------

    @Test
    fun `when statement over integer with arms and else JITs`() {
        val (backend, exeCtx) = setup(
            """
            function classify(x: integer): integer {
                var r = 0;
                when (x) {
                    1 -> r = 100;
                    2 -> r = 200;
                    else -> r = 999;
                }
                return r;
            }
            """.trimIndent()
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["classify"]!!

        assertEquals(100L, callInt(backend, exeCtx, fn, 1))
        assertEquals(200L, callInt(backend, exeCtx, fn, 2))
        assertEquals(999L, callInt(backend, exeCtx, fn, 7))  // hits else
        assertEquals(0, backend.jitMisses)
        assertEquals(3, backend.jitHits)
    }

    @Test
    fun `when statement without else falls through as no-op`() {
        val (backend, exeCtx) = setup(
            """
            function bump(x: integer): integer {
                var r = -1;
                when (x) {
                    1 -> r = 10;
                    2 -> r = 20;
                }
                return r;
            }
            """.trimIndent()
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["bump"]!!

        assertEquals(10L, callInt(backend, exeCtx, fn, 1))
        assertEquals(20L, callInt(backend, exeCtx, fn, 2))
        assertEquals(-1L, callInt(backend, exeCtx, fn, 9))  // no arm -> r unchanged
        assertEquals(0, backend.jitMisses)
        assertEquals(3, backend.jitHits)
    }

    // ---- negative: clean whole-function soft-fail ---------------------------------------

    @Test
    fun `for over a list literal is native and bit-exact`() {
        val (backend, exeCtx) = setup(
            """
            function sumList(n: integer): integer {
                var acc = 0;
                for (x in [10, 20, 30]) {
                    acc += x;
                }
                return acc + n;
            }
            """.trimIndent()
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["sumList"]!!

        // for-over-list-literal is JIT'd natively (the list literal builds a native LIST carrier and
        // the for-loop iterates it in element order); the WHOLE function runs natively, bit-exact.
        assertEquals(61L, callInt(backend, exeCtx, fn, 1))  // 10+20+30+1
        assertEquals(0, backend.jitMisses)
        assertEquals(1, backend.jitHits)
    }

    @Test
    fun `when over text key safely soft-fails to the interpreter`() {
        val (backend, exeCtx) = setup(
            """
            function pick(n: integer): integer {
                val s = if (n > 0) "pos" else "neg";
                var r = 0;
                when (s) {
                    "pos" -> r = 1;
                    "neg" -> r = 2;
                    else -> r = 3;
                }
                return r;
            }
            """.trimIndent()
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["pick"]!!

        // A text key is not an inline-canonical carrier (structural equality crosses the HANDLE
        // boundary) -> the whole function soft-falls to the interpreter, with the correct result.
        assertEquals(1L, callInt(backend, exeCtx, fn, 5))
        assertEquals(2L, callInt(backend, exeCtx, fn, -5))
        assertEquals(2, backend.jitMisses)  // both calls soft-failed to the interpreter
        assertEquals(0, backend.jitHits)
    }

    // ---- harness (mirrors Llvm_IntCoverageTest) -----------------------------------------

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
