/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.llvm

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.Rt_AppContext
import net.postchain.rell.base.runtime.Rt_ChainContext
import net.postchain.rell.base.runtime.Rt_ExecutionContext
import net.postchain.rell.base.runtime.Rt_GlobalContext
import net.postchain.rell.base.runtime.Rt_IntValue
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

/**
 * NATIVE in-memory tuples & structs — the first heap-value lowering, bit-exact vs the interpreter.
 *
 * Composites cross the JNI boundary as arena-owned native COMPOSITEs (`rell_runtime.h` §1):
 * `value.cpp::from_jvm` cracks an `Rt_StructValue` to a COMPOSITE carrying its `RR_App.allStructs`
 * def-index (via `Llvm_SysBridge.structDefIndex`) and RECURSIVELY-cracked field values; `to_jvm`
 * rebuilds the EXACT `Rt_StructValue` via `Llvm_SysBridge.structValue` (the same construction the
 * interpreter uses for an `RR_Expr.StructCreate`). `lower_expr.cpp` lowers `TupleExpr` / `StructExpr`
 * to a `rell_make_composite` call and `lower_call.cpp` lowers `StructAttr` / `TupleAttr` reads to
 * `rell_composite_get` — bit-exact with `(base as Rt_StructValue).get(i)` /
 * `Rt_TupleValue.elements\[i\]` (`rr_interp_expr.kt`). The value-ABI gate admits a STRUCT param/return (def-index
 * round-trip) but NOT a bare tuple (tuple types are structural, not def-index-addressable); a tuple
 * is therefore only an in-body INTERMEDIATE (constructed + field-read).
 *
 * Each test pins `jitHits>0` / `jitMisses==0` AND that the result equals what the interpreter
 * produces. Composite stdlib ops (equality / `.to_gtv()`) and list literals SOFT-FAIL (the by-value
 * `rell_sysfn_call` return ABI is not yet sret), asserted to still return the correct value via the
 * interpreter (`jitMisses==1`). Mirrors [Llvm_EnumCoverageTest] / [Llvm_IntCoverageTest].
 */
class Llvm_CompositeCoverageTest {

    @Test
    fun `construct a tuple and read its fields is native and bit-exact`() {
        // Tuple is an in-body INTERMEDIATE: constructed, fields read, never returned as a tuple.
        val (backend, exeCtx) = setup(
            "function f(a: integer, b: integer): integer {\n" +
                "    val t = (a, b);\n" +
                "    return t[0] + t[1];\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(7L, callInt(backend, exeCtx, fn, int(2), int(5)))
        assertEquals(0L, callInt(backend, exeCtx, fn, int(-3), int(3)))
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `construct a struct and read an attribute is native and bit-exact`() {
        val (backend, exeCtx) = setup(
            "struct Point { x: integer; y: integer; }\n" +
                "function f(a: integer, b: integer): integer {\n" +
                "    val p = Point(x = a, y = b);\n" +
                "    return p.x * p.y;\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(12L, callInt(backend, exeCtx, fn, int(3), int(4)))
        assertEquals(0L, callInt(backend, exeCtx, fn, int(0), int(9)))
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a function returning a struct round-trips through the value ABI`() {
        // Construct a struct natively and RETURN it: to_jvm reboxes the EXACT Rt_StructValue from the
        // def-index + field values; the result must structurally equal an interpreter-built struct.
        val (backend, exeCtx) = setup(
            "struct Point { x: integer; y: integer; }\n" +
                "function make(a: integer, b: integer): Point = Point(x = a, y = b);"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["make"]!!

        val result = backend.callFunction(fn, exeCtx, listOf(int(3), int(4)))
        val expected = struct(backend, "Point", listOf(int(3), int(4)))
        assertEquals(expected, result)
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a struct param round-trips identity through the value ABI`() {
        // Pass a struct in, return it: from_jvm cracked it to a COMPOSITE, to_jvm reboxed the exact
        // struct. Proves the def-index + recursive field marshalling round-trips.
        val (backend, exeCtx) = setup(
            "struct Point { x: integer; y: integer; }\n" +
                "function id(p: Point): Point = p;"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["id"]!!

        val input = struct(backend, "Point", listOf(int(11), int(22)))
        val result = backend.callFunction(fn, exeCtx, listOf(input))
        assertEquals(input, result)
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a struct with mixed field types round-trips`() {
        // integer + text(HANDLE) + enum + nested struct: every field marshals through the value ABI.
        // text stays a HANDLE; enum cracks to the inline ENUM tag; the nested struct is a COMPOSITE.
        val (backend, exeCtx) = setup(
            "enum Color { RED, GREEN, BLUE }\n" +
                "struct Inner { n: integer; }\n" +
                "struct Mixed { i: integer; t: text; c: Color; inner: Inner; }\n" +
                "function id(m: Mixed): Mixed = m;"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["id"]!!

        val inner = struct(backend, "Inner", listOf(int(99)))
        val input = struct(
            backend, "Mixed",
            listOf(int(7), text("hello"), color(backend, "GREEN"), inner),
        )
        val result = backend.callFunction(fn, exeCtx, listOf(input))
        assertEquals(input, result)  // structEquals: type name + pairwise attr equality
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a nested struct field read is native`() {
        // Read a struct attribute that is itself a struct, then a field of that nested struct.
        val (backend, exeCtx) = setup(
            "struct Inner { n: integer; }\n" +
                "struct Outer { inner: Inner; k: integer; }\n" +
                "function f(a: integer, b: integer): integer {\n" +
                "    val o = Outer(inner = Inner(n = a), k = b);\n" +
                "    return o.inner.n + o.k;\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(15L, callInt(backend, exeCtx, fn, int(10), int(5)))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a nested tuple field read is native`() {
        // A tuple whose element is itself a tuple, both in-body intermediates.
        val (backend, exeCtx) = setup(
            "function f(a: integer, b: integer): integer {\n" +
                "    val t = ((a, b), a);\n" +
                "    return t[0][0] + t[0][1] + t[1];\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(8L, callInt(backend, exeCtx, fn, int(3), int(2)))  // 3 + 2 + 3
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a struct equality op is native and bit-exact`() {
        // Composite == is now lowered to the native rell_value_equals structural walk (both Points are
        // native COMPOSITEs built in-body; integer fields recurse natively, no HANDLE element). Bit-exact
        // with Rt_StructValue.structEquals (size + positional get(i) equality).
        val (backend, exeCtx) = setup(
            "struct Point { x: integer; y: integer; }\n" +
                "function eq(a: integer, b: integer): boolean {\n" +
                "    return Point(x = a, y = b) == Point(x = 1, y = 2);\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["eq"]!!

        assertEquals(true, callBool(backend, exeCtx, fn, int(1), int(2)))
        assertEquals(false, callBool(backend, exeCtx, fn, int(1), int(3)))
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a struct to_gtv soft-fails but returns the correct value`() {
        val (backend, exeCtx) = setup(
            "struct Point { x: integer; y: integer; }\n" +
                "function g(a: integer): integer {\n" +
                "    val p = Point(x = a, y = a);\n" +
                "    p.to_gtv();\n" +  // composite sysfn -> back-call ABI hazard -> soft-fail
                "    return p.x;\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["g"]!!

        assertEquals(5L, callInt(backend, exeCtx, fn, int(5)))
        assertEquals(0, backend.jitHits)
        assertEquals(1, backend.jitMisses)
    }

    @Test
    fun `a list literal construction and subscript is native and bit-exact`() {
        // Lists are now native (the LIST carrier — value.cpp): [a, a+1] builds a native list via
        // rell_make_list, xs[0] reads it via rell_list_get. Bit-exact with the interpreter. (Was a
        // soft-fail before the LIST lowering landed.)
        val (backend, exeCtx) = setup(
            "function f(a: integer): integer {\n" +
                "    val xs = [a, a + 1];\n" +
                "    return xs[0];\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(4L, callInt(backend, exeCtx, fn, int(4)))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    // ---- helpers ----------------------------------------------------------------------

    private fun int(n: Int): Rt_Value = Rt_IntValue.get(n.toLong())

    @Suppress("SameParameterValue")
    private fun text(s: String): Rt_Value = net.postchain.rell.base.runtime.Rt_TextValue.get(s)

    /** Build a canonical input enum Rt_Value, mirroring Llvm_EnumCoverageTest.enumVal. */
    @Suppress("SameParameterValue")
    private fun color(backend: Llvm_Backend, attrName: String): Rt_Value {
        val typeIdx = backend.rrApp.allEnums.indexOfFirst { it.base.simpleName == "Color" }
        check(typeIdx >= 0) { "enum Color not found in app" }
        val rtType = backend.resolveType(RR_Type.Enum(typeIdx))
        val attr = backend.rrApp.allEnums[typeIdx].attr(attrName)
            ?: error("attr $attrName not found in enum Color")
        return Rt_RR_EnumValue(rtType, attr)
    }

    /** Build a canonical input struct Rt_Value the same way the backend reboxes one (reboxStruct). */
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
