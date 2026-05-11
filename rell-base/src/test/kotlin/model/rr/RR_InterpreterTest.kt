/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.rr

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Tests for [Rt_Interpreter]: compile → resolve → interpret via RR model.
 */
class Rt_InterpreterTest {

    private fun evalFunction(code: String, fnName: String = "test", args: List<Rt_Value> = listOf()): Rt_Value {
        val sourceDir = C_SourceDir.mapDirOf(RellTestUtils.MAIN_FILE to code)
        val modSel = C_CompilerModuleSelection(immListOf(ModuleName.EMPTY), immListOf())
        val cRes = RellTestUtils.compileApp(sourceDir, modSel, RellTestUtils.DEFAULT_COMPILER_OPTIONS)
        check(cRes.errors.isEmpty()) { "Compilation errors: ${cRes.errors.map { it.code }}" }

        val rrApp = cRes.rrApp!!
        val interpreter = RellTestUtils.forCompilation(rrApp, cRes.compilationSysFns)

        val globalCtx = Rt_GlobalContext(
            RellTestUtils.DEFAULT_COMPILER_OPTIONS,
            Rt_NopPrinter,
            Rt_NopPrinter,
            typeCheck = false,
        )
        val chainCtx = Rt_ChainContext.NULL
        val appCtx = Rt_AppContext(globalCtx, chainCtx, interpreter)
        val sqlDefs = rrApp.sqlDefs
        val sqlCtx = Rt_NullSqlContext.create(sqlDefs)
        val exeCtx = Rt_ExecutionContext(appCtx, Rt_NullOpContext, sqlCtx, NoConnSqlExecutor)

        val fn = rrApp.module(ModuleName.EMPTY)!!.functions[fnName]!!
        return interpreter.callFunction(fn, exeCtx, args)
    }

    private fun evalQuery(code: String, queryName: String = "test", args: List<Rt_Value> = listOf()): Rt_Value {
        val sourceDir = C_SourceDir.mapDirOf(RellTestUtils.MAIN_FILE to code)
        val modSel = C_CompilerModuleSelection(immListOf(ModuleName.EMPTY), immListOf())
        val cRes = RellTestUtils.compileApp(sourceDir, modSel, RellTestUtils.DEFAULT_COMPILER_OPTIONS)
        check(cRes.errors.isEmpty()) { "Compilation errors: ${cRes.errors.map { it.code }}" }

        val rrApp = cRes.rrApp!!
        val interpreter = RellTestUtils.forCompilation(rrApp, cRes.compilationSysFns)

        val globalCtx = Rt_GlobalContext(
            RellTestUtils.DEFAULT_COMPILER_OPTIONS,
            Rt_NopPrinter,
            Rt_NopPrinter,
            typeCheck = false,
        )
        val chainCtx = Rt_ChainContext.NULL
        val appCtx = Rt_AppContext(globalCtx, chainCtx, interpreter)
        val sqlDefs = rrApp.sqlDefs
        val sqlCtx = Rt_NullSqlContext.create(sqlDefs)
        val exeCtx = Rt_ExecutionContext(appCtx, Rt_NullOpContext, sqlCtx, NoConnSqlExecutor)

        val query = rrApp.module(ModuleName.EMPTY)!!.queries[queryName]!!
        return interpreter.callQuery(query, exeCtx, args)
    }

    // --- Constant expressions ---

    @Test fun testIntegerConstant() {
        val result = evalFunction("function test(): integer = 42;")
        assertEquals(42L, (result as Rt_IntValue).value)
    }

    @Test fun testTextConstant() {
        val result = evalFunction("function test(): text = 'hello';")
        assertEquals("hello", (result as Rt_TextValue).value)
    }

    @Test fun testBooleanConstant() {
        val result = evalFunction("function test(): boolean = true;")
        assertEquals(true, (result as Rt_BooleanValue).value)
    }

    // --- Arithmetic ---

    @Test fun testIntegerAdd() {
        val result = evalFunction("function test(): integer = 1 + 2;")
        assertEquals(3L, (result as Rt_IntValue).value)
    }

    @Test fun testIntegerSub() {
        val result = evalFunction("function test(): integer = 10 - 3;")
        assertEquals(7L, (result as Rt_IntValue).value)
    }

    @Test fun testIntegerMul() {
        val result = evalFunction("function test(): integer = 4 * 5;")
        assertEquals(20L, (result as Rt_IntValue).value)
    }

    @Test fun testIntegerDiv() {
        val result = evalFunction("function test(): integer = 15 / 4;")
        assertEquals(3L, (result as Rt_IntValue).value)
    }

    @Test fun testIntegerMod() {
        val result = evalFunction("function test(): integer = 17 % 5;")
        assertEquals(2L, (result as Rt_IntValue).value)
    }

    @Test fun testUnaryMinus() {
        val result = evalFunction("function test(): integer = -(42);")
        assertEquals(-42L, (result as Rt_IntValue).value)
    }

    @Test fun testNot() {
        val result = evalFunction("function test(): boolean = not true;")
        assertEquals(false, (result as Rt_BooleanValue).value)
    }

    // --- Comparison ---

    @Test fun testLessThan() {
        val result = evalFunction("function test(): boolean = 1 < 2;")
        assertEquals(true, (result as Rt_BooleanValue).value)
    }

    @Test fun testEquality() {
        val result = evalFunction("function test(): boolean = 3 == 3;")
        assertEquals(true, (result as Rt_BooleanValue).value)
    }

    @Test fun testInequality() {
        val result = evalFunction("function test(): boolean = 3 != 4;")
        assertEquals(true, (result as Rt_BooleanValue).value)
    }

    // --- Logical ---

    @Test fun testAnd() {
        val result = evalFunction("function test(): boolean = true and false;")
        assertEquals(false, (result as Rt_BooleanValue).value)
    }

    @Test fun testOr() {
        val result = evalFunction("function test(): boolean = false or true;")
        assertEquals(true, (result as Rt_BooleanValue).value)
    }

    // --- Control flow ---

    @Test fun testIfExpression() {
        val result = evalFunction("function test(): integer = if (true) 1 else 2;")
        assertEquals(1L, (result as Rt_IntValue).value)
    }

    @Test fun testIfStatement() {
        val result = evalFunction("""
            function test(): integer {
                if (1 < 2) return 10;
                return 20;
            }
        """)
        assertEquals(10L, (result as Rt_IntValue).value)
    }

    @Test fun testWhileLoop() {
        val result = evalFunction("""
            function test(): integer {
                var i = 0;
                var sum = 0;
                while (i < 5) {
                    sum += i;
                    i += 1;
                }
                return sum;
            }
        """)
        assertEquals(10L, (result as Rt_IntValue).value)
    }

    @Test fun testForLoop() {
        val result = evalFunction("""
            function test(): integer {
                var sum = 0;
                for (x in [1, 2, 3, 4, 5]) {
                    sum += x;
                }
                return sum;
            }
        """)
        assertEquals(15L, (result as Rt_IntValue).value)
    }

    @Test fun testBreak() {
        val result = evalFunction("""
            function test(): integer {
                var sum = 0;
                for (x in [1, 2, 3, 4, 5]) {
                    if (x == 4) break;
                    sum += x;
                }
                return sum;
            }
        """)
        assertEquals(6L, (result as Rt_IntValue).value)
    }

    @Test fun testContinue() {
        val result = evalFunction("""
            function test(): integer {
                var sum = 0;
                for (x in [1, 2, 3, 4, 5]) {
                    if (x == 3) continue;
                    sum += x;
                }
                return sum;
            }
        """)
        assertEquals(12L, (result as Rt_IntValue).value)
    }

    // --- Function calls ---

    @Test fun testFunctionCall() {
        val result = evalFunction("""
            function add(a: integer, b: integer): integer = a + b;
            function test(): integer = add(3, 4);
        """)
        assertEquals(7L, (result as Rt_IntValue).value)
    }

    @Test fun testRecursion() {
        val result = evalFunction("""
            function fib(n: integer): integer {
                if (n <= 1) return n;
                return fib(n - 1) + fib(n - 2);
            }
            function test(): integer = fib(10);
        """)
        assertEquals(55L, (result as Rt_IntValue).value)
    }

    // --- System function calls ---

    @Test fun testSysFunctionMaxInteger() {
        val result = evalFunction("function test(): integer = max(123, 456);")
        assertEquals(456L, (result as Rt_IntValue).value)
    }

    @Test fun testSysFunctionMinInteger() {
        val result = evalFunction("function test(): integer = min(123, 456);")
        assertEquals(123L, (result as Rt_IntValue).value)
    }

    @Test fun testSysFunctionAbsInteger() {
        val result = evalFunction("function test(): integer = abs(-5);")
        assertEquals(5L, (result as Rt_IntValue).value)
    }

    // TODO: member function calls (e.g., .upper_case(), .size()) use R_MemberCalculator_CommonCall
    //  which needs a member calculator registry to be built during resolve.
    //  @Test fun testSysMemberTextUpperCase()

    @Test fun testTextConcat() {
        val result = evalFunction("function test(): text = 'hello' + ' ' + 'world';")
        assertEquals("hello world", (result as Rt_TextValue).value)
    }

    // --- Collections ---

    @Test fun testListLiteralSize() {
        // List.size() uses R_MemberCalculator_CommonCall which needs member calculator registry — test basic list instead.
        val result = evalFunction("function test(): integer = [1, 2, 3][0];")
        assertEquals(1L, (result as Rt_IntValue).value)
    }

    @Test fun testListSubscript() {
        val result = evalFunction("function test(): integer = [10, 20, 30][1];")
        assertEquals(20L, (result as Rt_IntValue).value)
    }

    // --- Variables ---

    @Test fun testVarAssignment() {
        val result = evalFunction("""
            function test(): integer {
                var x = 10;
                x = 20;
                return x;
            }
        """)
        assertEquals(20L, (result as Rt_IntValue).value)
    }

    @Test fun testCompoundAssignment() {
        val result = evalFunction("""
            function test(): integer {
                var x = 10;
                x += 5;
                return x;
            }
        """)
        assertEquals(15L, (result as Rt_IntValue).value)
    }

    // --- Nullable ---

    @Test fun testElvisOperator() {
        val result = evalFunction("""
            function test(): integer {
                val x: integer? = null;
                return x ?: 42;
            }
        """)
        assertEquals(42L, (result as Rt_IntValue).value)
    }

    // --- Global constants ---

    @Test fun testGlobalConstant() {
        val result = evalFunction("""
            val MAX: integer = 100;
            function test(): integer = MAX;
        """)
        assertEquals(100L, (result as Rt_IntValue).value)
    }

    @Test fun testGlobalConstantMax() {
        val result = evalFunction("""
            val X: integer = max(123, 456);
            function test(): integer = X;
        """)
        assertEquals(456L, (result as Rt_IntValue).value)
    }

    // --- Structs ---

    // TODO: struct creation needs R_StructType which requires member calculator registry for member access.
    //  @Test fun testStructCreate()

    // --- Queries ---

    @Test fun testSimpleQuery() {
        val result = evalQuery("query test(): integer = 42;")
        assertEquals(42L, (result as Rt_IntValue).value)
    }

    // --- When expression ---

    @Test fun testWhenExpression() {
        val result = evalFunction("""
            function test(): text {
                val x = 2;
                return when (x) {
                    1 -> 'one';
                    2 -> 'two';
                    3 -> 'three';
                    else -> 'other';
                };
            }
        """)
        assertEquals("two", (result as Rt_TextValue).value)
    }
}
