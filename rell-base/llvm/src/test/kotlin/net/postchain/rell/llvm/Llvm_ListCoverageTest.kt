/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.llvm

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.Rt_AppContext
import net.postchain.rell.base.runtime.Rt_ChainContext
import net.postchain.rell.base.runtime.Rt_CommonError
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_ExecutionContext
import net.postchain.rell.base.runtime.Rt_GlobalContext
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_ListValue
import net.postchain.rell.base.runtime.Rt_NopPrinter
import net.postchain.rell.base.runtime.Rt_NullOpContext
import net.postchain.rell.base.runtime.Rt_NullSqlContext
import net.postchain.rell.base.runtime.Rt_RR_EnumValue
import net.postchain.rell.base.runtime.Rt_StructValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * NATIVE in-memory lists — reusing the arena-owned heap-value carrier machinery from the
 * tuple/struct (COMPOSITE) lowering, bit-exact vs the interpreter.
 *
 * A list crosses the JNI boundary as an arena-owned LIST carrier (`rell_runtime.h` §1): `value.cpp
 * ::from_jvm` cracks an `Rt_ListValue` to a LIST carrying a global-ref to its JVM `Rt_ValueClass`
 * type (`value.getType()`) and RECURSIVELY-cracked element values (via `Llvm_SysBridge.listSize`/
 * `listGet`); `to_jvm` rebuilds the EXACT `Rt_ListValue` via `Llvm_SysBridge.listValue`
 * (`Rt_ListValue(type, elems)` — the interpreter's construction). `lower_expr.cpp` lowers
 * `ListLiteralExpr` to a `rell_make_list` call (the list type resolved from an interned id mirrored
 * by [Llvm_ListTypeWalker]) and `ListSubscriptExpr` to a bounds-checked `rell_list_get`; an
 * out-of-bounds subscript raises the EXACT interpreter `list:index` Rt_Exception. `lower_stmt.cpp`
 * lowers for-over-list to a native size/element-get loop (iteration order = `Rt_ListValue.elements`).
 *
 * Each test pins `jitHits>0` / `jitMisses==0` AND that the result equals what the interpreter
 * produces. List EQUALITY / `.size()`-as-member and set/map literals SOFT-FAIL (the by-value
 * `rell_sysfn_call` return ABI is not yet sret), asserted to still return the correct value via the
 * interpreter (`jitMisses>=1`). Mirrors [Llvm_CompositeCoverageTest].
 */
class Llvm_ListCoverageTest {

    @Test
    fun `construct a list and read elements is native and bit-exact`() {
        val (backend, exeCtx) = setup(
            "function f(a: integer, b: integer): integer {\n" +
                "    val xs = [a, b, a + b];\n" +
                "    return xs[0] + xs[2];\n" +  // a + (a + b)
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(5L, callInt(backend, exeCtx, fn, int(1), int(3)))   // 1 + (1+3) = 5
        assertEquals(0L, callInt(backend, exeCtx, fn, int(0), int(0)))
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a list param round-trips identity through the value ABI`() {
        // from_jvm cracked the Rt_ListValue to a LIST (typeRef global + recursive elements); to_jvm
        // rebuilt the EXACT Rt_ListValue. Proves the type-ref + recursive element marshalling.
        val (backend, exeCtx) = setup("function id(xs: list<integer>): list<integer> = xs;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["id"]!!

        val input = intList(backend, listOf(10L, 20L, 30L))
        val result = backend.callFunction(fn, exeCtx, listOf(input))
        assertEquals(input, result)
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a function returning a constructed list round-trips`() {
        val (backend, exeCtx) = setup(
            "function make(a: integer, b: integer): list<integer> = [a, b];"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["make"]!!

        val result = backend.callFunction(fn, exeCtx, listOf(int(7), int(8)))
        assertEquals(intList(backend, listOf(7L, 8L)), result)
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a list of structs round-trips identity`() {
        // Each element is a struct COMPOSITE; from_jvm/to_jvm recurse element-by-element.
        val (backend, exeCtx) = setup(
            "struct Point { x: integer; y: integer; }\n" +
                "function id(xs: list<Point>): list<Point> = xs;"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["id"]!!

        val p1 = struct(backend, "Point", listOf(int(1), int(2)))
        val p2 = struct(backend, "Point", listOf(int(3), int(4)))
        val input = structList(backend, "Point", listOf(p1, p2))
        val result = backend.callFunction(fn, exeCtx, listOf(input))
        assertEquals(input, result)
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a list of enums round-trips identity`() {
        val (backend, exeCtx) = setup(
            "enum Color { RED, GREEN, BLUE }\n" +
                "function id(xs: list<Color>): list<Color> = xs;"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["id"]!!

        val input = enumList(backend, "Color", listOf("RED", "BLUE", "GREEN"))
        val result = backend.callFunction(fn, exeCtx, listOf(input))
        assertEquals(input, result)
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `for over a list param sums elements natively`() {
        val (backend, exeCtx) = setup(
            "function sum(xs: list<integer>): integer {\n" +
                "    var acc = 0;\n" +
                "    for (x in xs) acc += x;\n" +
                "    return acc;\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["sum"]!!

        assertEquals(60L, callInt(backend, exeCtx, fn, intList(backend, listOf(10L, 20L, 30L))))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `for over an EMPTY list runs zero iterations`() {
        val (backend, exeCtx) = setup(
            "function sum(xs: list<integer>): integer {\n" +
                "    var acc = 100;\n" +
                "    for (x in xs) acc += x;\n" +
                "    return acc;\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["sum"]!!

        assertEquals(100L, callInt(backend, exeCtx, fn, intList(backend, emptyList())))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `for over a list with break and continue is native and bit-exact`() {
        // continue skips odd-positioned values; break stops at the element equal to the stop value.
        // The parameter is `stop`, not `limit`: `limit` is the at-expression limit-clause keyword and
        // a parameter named `limit` is a [syntax] compile error.
        val (backend, exeCtx) = setup(
            "function f(xs: list<integer>, stop: integer): integer {\n" +
                "    var acc = 0;\n" +
                "    for (x in xs) {\n" +
                "        if (x == stop) break;\n" +
                "        if (x == 0) continue;\n" +
                "        acc += x;\n" +
                "    }\n" +
                "    return acc;\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        // [1, 0, 2, 9, 3], limit 9 -> 1 + 2 = 3 (0 skipped, break at 9).
        assertEquals(3L, callInt(backend, exeCtx, fn, intList(backend, listOf(1L, 0L, 2L, 9L, 3L)), int(9)))
        // no break/continue trigger: 1+2+3 = 6.
        assertEquals(6L, callInt(backend, exeCtx, fn, intList(backend, listOf(1L, 2L, 3L)), int(-1)))
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a list subscript out of bounds raises the same error as the interpreter on the JIT path`() {
        val (backend, exeCtx) = setup(
            "function get(xs: list<integer>, i: integer): integer = xs[i];"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["get"]!!

        // In bounds: native, bit-exact.
        assertEquals(20L, callInt(backend, exeCtx, fn, intList(backend, listOf(10L, 20L, 30L)), int(1)))

        // Out of bounds (index == size): the JIT path raises the EXACT interpreter Rt_Exception.
        val e1 = assertFailsWith<Rt_Exception> {
            callInt(backend, exeCtx, fn, intList(backend, listOf(10L, 20L, 30L)), int(3))
        }
        assertEquals("list:index:3:3", (e1.err as Rt_CommonError).code)

        // Negative index is OUT OF BOUNDS (no Python-style wrap), per the interpreter.
        val e2 = assertFailsWith<Rt_Exception> {
            callInt(backend, exeCtx, fn, intList(backend, listOf(10L, 20L, 30L)), int(-1))
        }
        assertEquals("list:index:3:-1", (e2.err as Rt_CommonError).code)

        // All three calls executed natively (the in-bounds read + two native OOB raises count as hits;
        // the OOB raise is the JIT result, not an interpreter fallback — see Llvm_Backend overflow path).
        assertEquals(3, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `list equality is native and bit-exact`() {
        // List == is lowered to the native rell_value_equals structural walk over the LIST carriers
        // (both list literals build native LIST reps; integer elements compare inline), so the function
        // JITs — no back-call. Bit-exact with Rt_ListValue.equals (positional element equality).
        val (backend, exeCtx) = setup(
            "function eq(a: integer): boolean = [a, a + 1] == [1, 2];"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["eq"]!!

        assertEquals(true, callBool(backend, exeCtx, fn, int(1)))
        assertEquals(false, callBool(backend, exeCtx, fn, int(5)))
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a list size member call soft-fails but returns the correct value`() {
        val (backend, exeCtx) = setup(
            "function sz(a: integer): integer = [a, a, a].size();"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["sz"]!!

        assertEquals(3L, callInt(backend, exeCtx, fn, int(7)))
        assertEquals(0, backend.jitHits)
        assertEquals(1, backend.jitMisses)
    }

    @Test
    fun `a map literal still soft-fails but returns the correct value`() {
        // Map/set literals are NOT lowered natively (the LIST carrier covers list only); they soft-fail.
        val (backend, exeCtx) = setup(
            "function f(a: integer): integer {\n" +
                "    val m = [a: a + 1];\n" +
                "    return m[a];\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(5L, callInt(backend, exeCtx, fn, int(4)))
        assertEquals(0, backend.jitHits)
        assertEquals(1, backend.jitMisses)
    }

    // ---- helpers ----------------------------------------------------------------------

    private fun int(n: Int): Rt_Value = Rt_IntValue.get(n.toLong())

    private fun intList(backend: Llvm_Backend, vals: List<Long>): Rt_Value {
        val rtType = backend.resolveType(RR_Type.List(RR_Type.Primitive(RR_PrimitiveKind.INTEGER)))
        return Rt_ListValue(rtType, vals.map { Rt_IntValue.get(it) as Rt_Value }.toMutableList())
    }

    @Suppress("SameParameterValue")
    private fun structList(backend: Llvm_Backend, simpleName: String, vals: List<Rt_Value>): Rt_Value {
        val defIndex = backend.rrApp.allStructs.indexOfFirst { it.base.simpleName == simpleName }
        check(defIndex >= 0) { "struct $simpleName not found in app" }
        val rtType = backend.resolveType(RR_Type.List(RR_Type.Struct(defIndex)))
        return Rt_ListValue(rtType, vals.toMutableList())
    }

    @Suppress("SameParameterValue")
    private fun enumList(backend: Llvm_Backend, simpleName: String, attrNames: List<String>): Rt_Value {
        val typeIdx = backend.rrApp.allEnums.indexOfFirst { it.base.simpleName == simpleName }
        check(typeIdx >= 0) { "enum $simpleName not found in app" }
        val rtType = backend.resolveType(RR_Type.List(RR_Type.Enum(typeIdx)))
        val vals = attrNames.map { color(backend, simpleName, it) }
        return Rt_ListValue(rtType, vals.toMutableList())
    }

    private fun color(backend: Llvm_Backend, enumName: String, attrName: String): Rt_Value {
        val typeIdx = backend.rrApp.allEnums.indexOfFirst { it.base.simpleName == enumName }
        check(typeIdx >= 0) { "enum $enumName not found in app" }
        val rtType = backend.resolveType(RR_Type.Enum(typeIdx))
        val attr = backend.rrApp.allEnums[typeIdx].attr(attrName)
            ?: error("attr $attrName not found in enum $enumName")
        return Rt_RR_EnumValue(rtType, attr)
    }

    @Suppress("SameParameterValue")
    private fun struct(backend: Llvm_Backend, simpleName: String, attrs: List<Rt_Value>): Rt_Value {
        val defIndex = backend.rrApp.allStructs.indexOfFirst { it.base.simpleName == simpleName }
        check(defIndex >= 0) { "struct $simpleName not found in app" }
        val rtType = backend.resolveType(RR_Type.Struct(defIndex))
        val attrNames = backend.rrApp.allStructs[defIndex].struct.attributesList.map { it.name }
        return Rt_StructValue(rtType, attrNames, attrs.toMutableList())
    }

    private fun callBool(
        backend: Llvm_Backend,
        exeCtx: Rt_ExecutionContext,
        fn: net.postchain.rell.base.model.rr.RR_FunctionDefinition,
        vararg args: Rt_Value,
    ): Boolean =
        (backend.callFunction(fn, exeCtx, args.toList()) as net.postchain.rell.base.runtime.Rt_BooleanValue).value

    private fun callInt(
        backend: Llvm_Backend,
        exeCtx: Rt_ExecutionContext,
        fn: net.postchain.rell.base.model.rr.RR_FunctionDefinition,
        vararg args: Rt_Value,
    ): Long = (backend.callFunction(fn, exeCtx, args.toList()) as Rt_IntValue).value

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
