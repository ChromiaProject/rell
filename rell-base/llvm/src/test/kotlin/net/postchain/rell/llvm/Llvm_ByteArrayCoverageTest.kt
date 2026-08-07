/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.llvm

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.runtime.Rt_AppContext
import net.postchain.rell.base.runtime.Rt_BooleanValue
import net.postchain.rell.base.runtime.Rt_ByteArrayValue
import net.postchain.rell.base.runtime.Rt_ChainContext
import net.postchain.rell.base.runtime.Rt_CommonError
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_ExecutionContext
import net.postchain.rell.base.runtime.Rt_GlobalContext
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_NopPrinter
import net.postchain.rell.base.runtime.Rt_NullOpContext
import net.postchain.rell.base.runtime.Rt_NullSqlContext
import net.postchain.rell.base.runtime.Rt_StructValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.Rt_ListValue
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * NATIVE byte_array — reusing the arena-owned heap-value carrier machinery from the tuple/struct
 * (COMPOSITE) and list (LIST) lowerings, bit-exact vs the interpreter.
 *
 * A byte_array crosses the JNI boundary as an arena-owned BYTEARRAY carrier (`rell_runtime.h` §1):
 * `value.cpp::from_jvm` copies the `Rt_ByteArrayValue` bytes into a fresh arena buffer (a COPY, never
 * a pin); `to_jvm` rebuilds the EXACT `Rt_ByteArrayValue` via `Llvm_SysBridge.byteArrayValue`
 * (`Rt_ByteArrayValue.get`, canonicalising empty -> `EMPTY`), so `from_jvm`∘`to_jvm` is content-
 * identity (`Rt_ByteArrayValue.equals` == `contentEquals`). `lower_expr.cpp` lowers a `x"..."` literal
 * to `rell_make_bytearray` and `ByteArraySubscriptExpr` to a bounds-checked `rell_bytearray_get` (the
 * byte is returned as an UNSIGNED integer 0..255; an OOB subscript raises the EXACT interpreter
 * `expr_bytearray_subscript_index` Rt_Exception). `lower_ops.cpp` lowers `==`/`!=` to a native content
 * compare, `<`/`<=`/`>`/`>=` to a native UNSIGNED lexicographic compare (`rt_ops.kt`
 * `compareByteArrays`), and `+` to a native concat (`rt_ops.kt` `R_BinaryOp_Concat_ByteArray`).
 * `lower_call.cpp` lowers `.size()`/`.empty()` natively (the BYTEARRAY carrier exposes the length).
 *
 * Each test pins `jitHits>0` / `jitMisses==0` AND that the result equals what the interpreter
 * produces. Non-trivial stdlib ops (`.to_hex()`/`.sub()`/`sha256`/...) route through the JVM
 * (`rell_sysfn_call`), which soft-fails the function today (the by-value return ABI is not yet sret),
 * asserted to still return the correct value via the interpreter. Mirrors [Llvm_ListCoverageTest].
 */
class Llvm_ByteArrayCoverageTest {

    @Test
    fun `a byte_array literal round-trips through the value ABI`() {
        // The literal x"0102ff" is built natively (rell_make_bytearray) and reboxed via to_jvm; the
        // result equals the interpreter's Rt_ByteArrayValue.get of the same bytes.
        val (backend, exeCtx) = setup("function f(): byte_array = x\"0102ff\";")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(ba(0x01, 0x02, 0xff), backend.callFunction(fn, exeCtx, listOf()))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a byte_array param round-trips identity through the value ABI`() {
        // from_jvm copied the bytes into an arena buffer; to_jvm rebuilt the EXACT Rt_ByteArrayValue.
        val (backend, exeCtx) = setup("function id(b: byte_array): byte_array = b;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["id"]!!

        val input = ba(0xde, 0xad, 0xbe, 0xef)
        assertEquals(input, backend.callFunction(fn, exeCtx, listOf(input)))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `an empty byte_array round-trips`() {
        val (backend, exeCtx) = setup("function f(): byte_array = x\"\";")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        // The empty literal canonicalises to Rt_ByteArrayValue.EMPTY on rebox.
        assertEquals(ba(), backend.callFunction(fn, exeCtx, listOf()))
        assertEquals(Rt_ByteArrayValue.EMPTY, backend.callFunction(fn, exeCtx, listOf()))
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `byte_array size is native and bit-exact`() {
        val (backend, exeCtx) = setup("function sz(b: byte_array): integer = b.size();")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["sz"]!!

        assertEquals(3L, callInt(backend, exeCtx, fn, ba(0x01, 0x02, 0x03)))
        assertEquals(0L, callInt(backend, exeCtx, fn, ba()))
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `byte_array empty is native and bit-exact`() {
        val (backend, exeCtx) = setup("function e(b: byte_array): boolean = b.empty();")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["e"]!!

        assertEquals(true, callBool(backend, exeCtx, fn, ba()))
        assertEquals(false, callBool(backend, exeCtx, fn, ba(0x01)))
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `byte_array subscript returns the UNSIGNED byte value`() {
        val (backend, exeCtx) = setup(
            "function get(b: byte_array, i: integer): integer = b[i];"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["get"]!!

        // 0x01 -> 1, and CRUCIALLY 0xff -> 255 (UNSIGNED, not -1).
        assertEquals(1L, callInt(backend, exeCtx, fn, ba(0x01, 0x02, 0xff), int(0)))
        assertEquals(2L, callInt(backend, exeCtx, fn, ba(0x01, 0x02, 0xff), int(1)))
        assertEquals(255L, callInt(backend, exeCtx, fn, ba(0x01, 0x02, 0xff), int(2)))
        assertEquals(3, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a byte_array subscript out of bounds raises the same error as the interpreter on the JIT path`() {
        val (backend, exeCtx) = setup(
            "function get(b: byte_array, i: integer): integer = b[i];"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["get"]!!

        // In bounds: native, bit-exact.
        assertEquals(255L, callInt(backend, exeCtx, fn, ba(0x01, 0x02, 0xff), int(2)))

        // Out of bounds (index == size): the JIT path raises the EXACT interpreter Rt_Exception.
        val e1 = assertFailsWith<Rt_Exception> {
            callInt(backend, exeCtx, fn, ba(0x01, 0x02, 0xff), int(3))
        }
        assertEquals("expr_bytearray_subscript_index:3:3", (e1.err as Rt_CommonError).code)

        // Negative index is OUT OF BOUNDS (no Python-style wrap), per the interpreter.
        val e2 = assertFailsWith<Rt_Exception> {
            callInt(backend, exeCtx, fn, ba(0x01, 0x02, 0xff), int(-1))
        }
        assertEquals("expr_bytearray_subscript_index:3:-1", (e2.err as Rt_CommonError).code)

        // All three calls executed natively (the in-bounds read + two native OOB raises count as hits).
        assertEquals(3, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `byte_array equality and inequality are native and bit-exact`() {
        val (backend, exeCtx) = setup(
            "function eq(a: byte_array, b: byte_array): boolean = a == b;\n" +
                "function ne(a: byte_array, b: byte_array): boolean = a != b;"
        )
        val eq = backend.rrApp.module(ModuleName.EMPTY)!!.functions["eq"]!!
        val ne = backend.rrApp.module(ModuleName.EMPTY)!!.functions["ne"]!!

        assertEquals(true, callBool(backend, exeCtx, eq, ba(0x01, 0x02), ba(0x01, 0x02)))
        assertEquals(false, callBool(backend, exeCtx, eq, ba(0x01, 0x02), ba(0x01, 0x03)))
        assertEquals(false, callBool(backend, exeCtx, eq, ba(0x01), ba(0x01, 0x02)))  // length differs
        assertEquals(true, callBool(backend, exeCtx, eq, ba(), ba()))                  // both empty
        assertEquals(true, callBool(backend, exeCtx, ne, ba(0x01), ba(0x02)))
        assertEquals(false, callBool(backend, exeCtx, ne, ba(0x0a, 0x0b), ba(0x0a, 0x0b)))
        assertEquals(6, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `byte_array comparison ordering is UNSIGNED and bit-exact`() {
        val (backend, exeCtx) = setup(
            "function lt(a: byte_array, b: byte_array): boolean = a < b;"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["lt"]!!

        // CRITICAL: signed-vs-unsigned discriminator. As bytes, 0x80 == 128 and 0x7f == 127, so under
        // UNSIGNED ordering x"7f" < x"80" (TRUE). Under a (wrong) SIGNED ordering 0x80 == -128 < 0x7f,
        // which would make x"7f" < x"80" FALSE. The interpreter is UNSIGNED (rt_ops.kt
        // compareByteArrays / Integer.compareUnsigned), so the correct answer is TRUE.
        assertEquals(true, callBool(backend, exeCtx, fn, ba(0x7f), ba(0x80)))
        assertEquals(false, callBool(backend, exeCtx, fn, ba(0x80), ba(0x7f)))
        // Prefix is a prefix of the longer -> shorter compares LESS (length tiebreak).
        assertEquals(true, callBool(backend, exeCtx, fn, ba(0x01, 0x02), ba(0x01, 0x02, 0x00)))
        // Equal -> not less-than.
        assertEquals(false, callBool(backend, exeCtx, fn, ba(0x01, 0x02), ba(0x01, 0x02)))
        // First differing byte decides (unsigned): 0xff > 0x00.
        assertEquals(false, callBool(backend, exeCtx, fn, ba(0xff), ba(0x00)))
        assertEquals(5, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `byte_array concat is native and bit-exact`() {
        val (backend, exeCtx) = setup(
            "function cat(a: byte_array, b: byte_array): byte_array = a + b;"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["cat"]!!

        assertEquals(ba(0x01, 0x02), backend.callFunction(fn, exeCtx, listOf(ba(0x01), ba(0x02))))
        // Empty-operand identity (both directions).
        assertEquals(ba(0x0a), backend.callFunction(fn, exeCtx, listOf(ba(0x0a), ba())))
        assertEquals(ba(0x0b), backend.callFunction(fn, exeCtx, listOf(ba(), ba(0x0b))))
        assertEquals(ba(), backend.callFunction(fn, exeCtx, listOf(ba(), ba())))
        assertEquals(4, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a byte_array literal concat is native`() {
        val (backend, exeCtx) = setup("function f(): byte_array = x\"01\" + x\"02\";")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(ba(0x01, 0x02), backend.callFunction(fn, exeCtx, listOf()))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a byte_array inside a struct round-trips identity`() {
        val (backend, exeCtx) = setup(
            "struct Box { tag: integer; data: byte_array; }\n" +
                "function id(x: Box): Box = x;"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["id"]!!

        val input = struct(backend, "Box", listOf(int(7), ba(0x01, 0x02, 0xff)))
        assertEquals(input, backend.callFunction(fn, exeCtx, listOf(input)))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a list of byte_array round-trips identity`() {
        val (backend, exeCtx) = setup(
            "function id(xs: list<byte_array>): list<byte_array> = xs;"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["id"]!!

        val input = byteArrayList(backend, listOf(ba(0x01), ba(0x02, 0x03), ba()))
        assertEquals(input, backend.callFunction(fn, exeCtx, listOf(input)))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `to_hex soft-fails but returns the correct value via the interpreter`() {
        // .to_hex() routes through the JVM (rell_sysfn_call), which soft-fails the whole function
        // today (the by-value return ABI is not sret) — but the value is still correct.
        val (backend, exeCtx) = setup("function h(b: byte_array): text = b.to_hex();")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["h"]!!

        assertEquals(
            "0102ff",
            (backend.callFunction(fn, exeCtx, listOf(ba(0x01, 0x02, 0xff)))
                as net.postchain.rell.base.runtime.Rt_TextValue).value,
        )
        assertEquals(0, backend.jitHits)
        assertEquals(1, backend.jitMisses)
    }

    // ---- helpers ----------------------------------------------------------------------

    private fun int(n: Int): Rt_Value = Rt_IntValue.get(n.toLong())

    private fun ba(vararg bytes: Int): Rt_Value =
        Rt_ByteArrayValue.get(ByteArray(bytes.size) { bytes[it].toByte() })

    private fun byteArrayList(backend: Llvm_Backend, vals: List<Rt_Value>): Rt_Value {
        val rtType = backend.resolveType(RR_Type.List(RR_Type.Primitive(RR_PrimitiveKind.BYTE_ARRAY)))
        return Rt_ListValue(rtType, vals.toMutableList())
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
    ): Boolean = (backend.callFunction(fn, exeCtx, args.toList()) as Rt_BooleanValue).value

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
