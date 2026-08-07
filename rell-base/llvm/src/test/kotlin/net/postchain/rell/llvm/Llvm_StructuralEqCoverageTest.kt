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
import net.postchain.rell.base.runtime.Rt_DecimalValue
import net.postchain.rell.base.runtime.Rt_ExecutionContext
import net.postchain.rell.base.runtime.Rt_GlobalContext
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_ListValue
import net.postchain.rell.base.runtime.Rt_MapValue
import net.postchain.rell.base.runtime.Rt_NopPrinter
import net.postchain.rell.base.runtime.Rt_NullOpContext
import net.postchain.rell.base.runtime.Rt_NullSqlContext
import net.postchain.rell.base.runtime.Rt_RangeValue
import net.postchain.rell.base.runtime.Rt_SetValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

/**
 * NATIVE structural equality (==/!=) for composite & collection values, bit-exact vs the interpreter's
 * `Rt_Value.equals`. This replaces the back-call previously used for composite/collection equality
 * (see [Llvm_CompositeCoverageTest] / [Llvm_SetMapCoverageTest], whose old "soft-fail" expectations
 * are superseded for the tuple/struct/list cases).
 *
 * The lowering (`lower_ops.cpp::lowerEquality` -> `emitCompositeCollectionEq`) emits a call to the
 * pure-native recursive helper `rell_value_equals` (`value.cpp` §4h) over the native COMPOSITE
 * (tuple/struct) / LIST reps built by `from_jvm` / `rell_make_composite` / `rell_make_list`:
 *   * tuple/struct: same discriminant (tuple disc -1 / struct def-index), same arity, field-wise
 *     recursive equals (bit-exact with `Rt_TupleValue.equals` / `Rt_StructValue.structEquals`).
 *   * list: same length, positional recursive element equals (`Rt_ListValue.equals`).
 *   * scalar leaves recurse with the inline rules: i64 payload (int/bool/rowid/bigint/enum-ordinal),
 *     DEC_LONG (mantissa+scale on the stripped canonical form, so 1.0 == 1.00), TEXT/BYTEARRAY content.
 *   * ANY opaque HANDLE element (set, map, gtv, json, virtual, range, wide numeric, bare tuple) makes
 *     the helper call `rell_jit_escape`, so the WHOLE call re-runs on the interpreter (correct value,
 *     counted as a `jitMiss`) — never a wrong equality result.
 *
 * Each native test pins `jitHits>0` / `jitMisses==0` AND value-equality vs the interpreter; the
 * HANDLE-only / set / map cases pin the correct value via the back-call escape (`jitHits==0`,
 * `jitMisses>=1`). Mirrors [Llvm_CompositeCoverageTest].
 */
class Llvm_StructuralEqCoverageTest {

    @Test
    fun `tuple equality is native and bit-exact`() {
        // Two in-body tuple intermediates compared field-wise.
        val (backend, exeCtx) = setup(
            "function eq(a: integer, b: integer): boolean {\n" +
                "    return (a, b) == (1, 2);\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["eq"]!!

        assertEquals(true, callBool(backend, exeCtx, fn, int(1), int(2)))
        assertEquals(false, callBool(backend, exeCtx, fn, int(1), int(9)))
        assertEquals(false, callBool(backend, exeCtx, fn, int(7), int(2)))
        assertEquals(3, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `tuple inequality is native and bit-exact`() {
        val (backend, exeCtx) = setup(
            "function ne(a: integer, b: integer): boolean {\n" +
                "    return (a, b) != (1, 2);\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["ne"]!!

        assertEquals(false, callBool(backend, exeCtx, fn, int(1), int(2)))
        assertEquals(true, callBool(backend, exeCtx, fn, int(1), int(9)))
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `struct equality with mixed fields incl scale-insensitive decimal is native and bit-exact`() {
        // int + text + enum + decimal: decimal 1.0 vs 1.00 -> equal (DEC_LONG stripped canonical form).
        // Build the RHS with d = 1.00 and the LHS with d = 1.0 so the decimal recursion proves
        // scale-insensitivity natively.
        val (backend, exeCtx) = setup(
            "enum Color { RED, GREEN, BLUE }\n" +
                "struct Mixed { i: integer; t: text; c: Color; d: decimal; }\n" +
                "function eq(i: integer): boolean {\n" +
                "    return Mixed(i = i, t = 'hi', c = Color.GREEN, d = 1.0) ==\n" +
                "           Mixed(i = 7, t = 'hi', c = Color.GREEN, d = 1.00);\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["eq"]!!

        // i == 7: all fields equal (incl 1.0 == 1.00) -> true.
        assertEquals(true, callBool(backend, exeCtx, fn, int(7)))
        // i == 8: integer field differs -> false.
        assertEquals(false, callBool(backend, exeCtx, fn, int(8)))
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `nested struct equality is native and bit-exact`() {
        val (backend, exeCtx) = setup(
            "struct Inner { n: integer; }\n" +
                "struct Outer { inner: Inner; k: integer; }\n" +
                "function eq(a: integer, b: integer): boolean {\n" +
                "    return Outer(inner = Inner(n = a), k = b) == Outer(inner = Inner(n = 3), k = 4);\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["eq"]!!

        assertEquals(true, callBool(backend, exeCtx, fn, int(3), int(4)))
        // nested field differs
        assertEquals(false, callBool(backend, exeCtx, fn, int(9), int(4)))
        // outer field differs
        assertEquals(false, callBool(backend, exeCtx, fn, int(3), int(9)))
        assertEquals(3, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `nested tuple equality is native and bit-exact`() {
        val (backend, exeCtx) = setup(
            "function eq(a: integer, b: integer): boolean {\n" +
                "    return ((a, b), a) == ((1, 2), 1);\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["eq"]!!

        assertEquals(true, callBool(backend, exeCtx, fn, int(1), int(2)))
        assertEquals(false, callBool(backend, exeCtx, fn, int(1), int(9)))
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `list equality is native and bit-exact`() {
        // equal, different length, different element — all native (integer elements).
        val (backend, exeCtx) = setup(
            "function eq(a: integer, b: integer): boolean {\n" +
                "    return [a, b] == [1, 2];\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["eq"]!!

        assertEquals(true, callBool(backend, exeCtx, fn, int(1), int(2)))
        assertEquals(false, callBool(backend, exeCtx, fn, int(1), int(9)))
        assertEquals(false, callBool(backend, exeCtx, fn, int(9), int(2)))
        assertEquals(3, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `list of different length is not equal natively`() {
        val (backend, exeCtx) = setup(
            "function eq(a: integer): boolean {\n" +
                "    return [a, a] == [a];\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["eq"]!!

        assertEquals(false, callBool(backend, exeCtx, fn, int(5)))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `list of structs equality is native and bit-exact`() {
        val (backend, exeCtx) = setup(
            "struct Point { x: integer; y: integer; }\n" +
                "function eq(a: integer, b: integer): boolean {\n" +
                "    return [Point(x = a, y = b)] == [Point(x = 1, y = 2)];\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["eq"]!!

        assertEquals(true, callBool(backend, exeCtx, fn, int(1), int(2)))
        assertEquals(false, callBool(backend, exeCtx, fn, int(1), int(9)))
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `set equality is order-independent and correct via the back-call escape`() {
        // Both sets are HANDLE params (a set literal in-body would soft-fail the whole function anyway).
        // rell_value_equals sees a HANDLE operand and escapes -> the interpreter runs the bit-exact
        // Rt_SetValue.equals (order-independent). Correct value, counted as a jitMiss.
        val (backend, exeCtx) = setup(
            "function eq(s1: set<integer>, s2: set<integer>): boolean = s1 == s2;"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["eq"]!!

        // Same elements, different insertion order -> equal (order-independent).
        assertEquals(true, callBool(backend, exeCtx, fn, intSet(backend, listOf(1L, 2L, 3L)),
            intSet(backend, listOf(3L, 2L, 1L))))
        // Different elements -> not equal.
        assertEquals(false, callBool(backend, exeCtx, fn, intSet(backend, listOf(1L, 2L)),
            intSet(backend, listOf(1L, 2L, 3L))))
        assertEquals(0, backend.jitHits)
        assertEquals(2, backend.jitMisses)
    }

    @Test
    fun `map equality is order-independent and correct via the back-call escape`() {
        val (backend, exeCtx) = setup(
            "function eq(m1: map<integer, integer>, m2: map<integer, integer>): boolean = m1 == m2;"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["eq"]!!

        // Same entries, different insertion order -> equal.
        assertEquals(true, callBool(backend, exeCtx, fn,
            intIntMap(backend, listOf(1L to 10L, 2L to 20L)),
            intIntMap(backend, listOf(2L to 20L, 1L to 10L))))
        // Different value for a key -> not equal.
        assertEquals(false, callBool(backend, exeCtx, fn,
            intIntMap(backend, listOf(1L to 10L)),
            intIntMap(backend, listOf(1L to 99L))))
        assertEquals(0, backend.jitHits)
        assertEquals(2, backend.jitMisses)
    }

    @Test
    fun `struct with a HANDLE-only field falls back to the interpreter and is correct`() {
        // A `range` field is HANDLE-only (no native rep). The struct cracks to a COMPOSITE whose range
        // field is a HANDLE; rell_value_equals hits that HANDLE element and escapes -> the interpreter
        // runs the bit-exact Rt_StructValue.structEquals (range fields compared via Rt_RangeValue.equals).
        // Correct value, counted as a jitMiss (NOT a wrong equality result).
        val (backend, exeCtx) = setup(
            "struct Holder { n: integer; r: range; }\n" +
                "function eq(h1: Holder, h2: Holder): boolean = h1 == h2;"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["eq"]!!

        val r1 = range(0, 10, 1)
        val r2 = range(0, 10, 1)
        val r3 = range(0, 5, 1)
        // Equal n + equal range -> equal.
        assertEquals(true, callBool(backend, exeCtx, fn,
            holder(backend, 7, r1), holder(backend, 7, r2)))
        // Equal n + different range -> not equal.
        assertEquals(false, callBool(backend, exeCtx, fn,
            holder(backend, 7, r1), holder(backend, 7, r3)))
        // Different n + equal range -> not equal.
        assertEquals(false, callBool(backend, exeCtx, fn,
            holder(backend, 7, r1), holder(backend, 9, r2)))
        assertEquals(0, backend.jitHits)
        assertEquals(3, backend.jitMisses)
    }

    // ---- helpers ----------------------------------------------------------------------

    private fun int(n: Int): Rt_Value = Rt_IntValue.get(n.toLong())

    @Suppress("SameParameterValue")
    private fun range(start: Long, end: Long, step: Long): Rt_Value = Rt_RangeValue(start, end, step)

    /** Build a Holder { n: integer; r: range; } input the same way the backend reboxes a struct. */
    private fun holder(backend: Llvm_Backend, n: Int, r: Rt_Value): Rt_Value =
        struct(backend, "Holder", listOf(int(n), r))

    private fun struct(backend: Llvm_Backend, simpleName: String, attrs: List<Rt_Value>): Rt_Value {
        val defIndex = backend.rrApp.allStructs.indexOfFirst { it.base.simpleName == simpleName }
        check(defIndex >= 0) { "struct $simpleName not found in app" }
        val rtType = backend.resolveType(RR_Type.Struct(defIndex))
        val attrNames = backend.rrApp.allStructs[defIndex].struct.attributesList.map { it.name }
        return net.postchain.rell.base.runtime.Rt_StructValue(rtType, attrNames, attrs.toMutableList())
    }

    private fun intSet(backend: Llvm_Backend, vals: List<Long>): Rt_Value {
        val rtType = backend.resolveType(RR_Type.Set(RR_Type.Primitive(RR_PrimitiveKind.INTEGER)))
        val s = LinkedHashSet<Rt_Value>()
        for (v in vals) s.add(Rt_IntValue.get(v))
        return Rt_SetValue(rtType, s)
    }

    private fun intIntMap(backend: Llvm_Backend, pairs: List<Pair<Long, Long>>): Rt_Value {
        val rtType = backend.resolveType(
            RR_Type.Map(RR_Type.Primitive(RR_PrimitiveKind.INTEGER), RR_Type.Primitive(RR_PrimitiveKind.INTEGER))
        )
        val m = LinkedHashMap<Rt_Value, Rt_Value>()
        for ((k, v) in pairs) m[Rt_IntValue.get(k)] = Rt_IntValue.get(v)
        return Rt_MapValue(rtType, m)
    }

    @Suppress("unused")
    private fun dec(s: String): Rt_Value = Rt_DecimalValue.get(BigDecimal(s))

    private fun callBool(
        backend: Llvm_Backend,
        exeCtx: Rt_ExecutionContext,
        fn: net.postchain.rell.base.model.rr.RR_FunctionDefinition,
        vararg args: Rt_Value,
    ): Boolean =
        (backend.callFunction(fn, exeCtx, args.toList()) as net.postchain.rell.base.runtime.Rt_BooleanValue).value

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
