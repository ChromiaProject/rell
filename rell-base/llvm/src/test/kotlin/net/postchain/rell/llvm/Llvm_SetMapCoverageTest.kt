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
import net.postchain.rell.base.runtime.Rt_MapValue
import net.postchain.rell.base.runtime.Rt_NopPrinter
import net.postchain.rell.base.runtime.Rt_NullOpContext
import net.postchain.rell.base.runtime.Rt_NullSqlContext
import net.postchain.rell.base.runtime.Rt_SetValue
import net.postchain.rell.base.runtime.Rt_TextValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * SET / MAP coverage for the LLVM value-ABI.
 *
 * DECISION (consensus-critical): set/map are carried as OPAQUE HANDLEs (JVM Rt_SetValue/Rt_MapValue),
 * NOT a native C++ container. `value.cpp::from_jvm` adopts the whole set/map as a tracked global ref;
 * `to_jvm`'s HANDLE case hands that SAME global ref straight back — so from_jvm∘to_jvm is the IDENTICAL
 * JVM instance, with NO re-encoding of elements, iteration order, key equality/hashing, or gtv. A
 * native set/map would have to reproduce JVM LinkedHashMap/LinkedHashSet insertion order AND
 * Rt_Value.hashCode/equals bit-for-bit (NOT provable), so per the correctness floor we keep HANDLEs.
 *
 * THE WIN — the value-ABI type gate (jni_bridge.cpp isSetMapHandleType) now ADMITS set/map signatures,
 * so a function with a set/map-typed param/return/local no longer forces a WHOLE-function soft-fail:
 * its surrounding control flow JITs while the set/map FLOWS as a HANDLE. Map/set OPERATIONS still
 * soft-fail in the body lowering (map subscript / map literal hit lowerExpr's default; membership /
 * .size() / .contains() route the stdlib back-call which soft-fails the whole function — the by-value
 * rell_sysfn_call return ABI is not yet sret; for-over-set/map uses the LEGACY_MAP/non-DIRECT iterable
 * adapter which soft-fails). Those bodies fall back to the interpreter — correct, never wrong.
 *
 * Each test pins both the bit-exact value AND whether the path was NATIVE (jitHits>0, jitMisses==0,
 * set/map carried as a HANDLE the body never touches) or BACK-CALL/SOFT-FAIL (jitMisses>=1, the
 * set/map op ran on the interpreter). BOTH yield the interpreter-identical value. Mirrors
 * [Llvm_ListCoverageTest].
 */
class Llvm_SetMapCoverageTest {

    // ---- NATIVE: the surrounding function JITs while the set/map flows as an opaque HANDLE -------

    @Test
    fun `a map param round-trips identity through the value ABI as a HANDLE`() {
        // from_jvm adopted the Rt_MapValue as a global ref; to_jvm handed back the SAME ref. The
        // result is the IDENTICAL instance (===), proving HANDLE pass-through is identity.
        val (backend, exeCtx) = setup("function id(m: map<text, integer>): map<text, integer> = m;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["id"]!!

        val input = textIntMap(backend, listOf("a" to 1L, "b" to 2L, "c" to 3L))
        val result = backend.callFunction(fn, exeCtx, listOf(input))
        assertEquals(input, result)
        // HANDLE pass-through: the exact same JVM object crosses the boundary unchanged.
        assert(input === result) { "expected the identical map instance back (HANDLE pass-through)" }
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a set param round-trips identity through the value ABI as a HANDLE`() {
        val (backend, exeCtx) = setup("function id(s: set<integer>): set<integer> = s;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["id"]!!

        val input = intSet(backend, listOf(10L, 20L, 30L))
        val result = backend.callFunction(fn, exeCtx, listOf(input))
        assertEquals(input, result)
        assert(input === result) { "expected the identical set instance back (HANDLE pass-through)" }
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a function taking a map param JITs its integer control flow natively (map is an untouched HANDLE)`() {
        // The map is a param but the body never operates on it: pure integer control flow. The gate
        // now admits the map signature, so the whole body JITs while the map rides as an opaque HANDLE.
        val (backend, exeCtx) = setup(
            "function f(m: map<text, integer>, a: integer, b: integer): integer {\n" +
                "    var acc = 0;\n" +
                "    for (i in range(a, b)) acc += i;\n" +
                "    return acc;\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        val m = textIntMap(backend, listOf("x" to 100L))
        // range(2, 5) -> 2+3+4 = 9.
        assertEquals(9L, callInt(backend, exeCtx, fn, m, int(2), int(5)))
        // range(0, 0) -> empty -> 0.
        assertEquals(0L, callInt(backend, exeCtx, fn, m, int(0), int(0)))
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a function taking a set param JITs its integer control flow natively (set is an untouched HANDLE)`() {
        val (backend, exeCtx) = setup(
            "function f(s: set<integer>, a: integer): integer = a * a;"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        val s = intSet(backend, listOf(1L, 2L, 3L))
        assertEquals(49L, callInt(backend, exeCtx, fn, s, int(7)))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a map passed through and returned round-trips identity while integer control flow JITs`() {
        // The map is returned unchanged; the function also does native integer work to prove the body
        // is genuinely JIT'd (not just a trivial param echo). The returned map is the identical HANDLE.
        val (backend, exeCtx) = setup(
            "function pick(m: map<text, integer>, a: integer): map<text, integer> {\n" +
                "    if (a > 0) return m;\n" +
                "    return m;\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["pick"]!!

        val m = textIntMap(backend, listOf("k" to 5L))
        val result = backend.callFunction(fn, exeCtx, listOf(m, int(1)))
        assertEquals(m, result)
        assert(m === result) { "expected the identical map instance back" }
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    // ---- BACK-CALL / SOFT-FAIL: the set/map OPERATION runs on the interpreter, value still correct --

    @Test
    fun `a map literal soft-fails but returns the correct value`() {
        // MapLiteralExpr is not lowered natively (hits lowerExpr's default soft-fail); the WHOLE
        // function runs on the interpreter, bit-exact.
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

    @Test
    fun `a map subscript soft-fails but returns the correct value`() {
        // m[k] on a map param: MapSubscriptExpr is not lowered natively -> whole-function soft-fail.
        val (backend, exeCtx) = setup(
            "function get(m: map<integer, integer>, k: integer): integer = m[k];"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["get"]!!

        val m = intIntMap(backend, listOf(1L to 10L, 2L to 20L, 3L to 30L))
        assertEquals(20L, callInt(backend, exeCtx, fn, m, int(2)))
        assertEquals(30L, callInt(backend, exeCtx, fn, m, int(3)))
        assertEquals(0, backend.jitHits)
        assertEquals(2, backend.jitMisses)
    }

    @Test
    fun `a map subscript missing key raises the same error as the interpreter`() {
        // The op soft-fails to the interpreter, which raises the EXACT Rt_Exception. Code is the
        // consensus identifier: fn_map_get_novalue:<key.strCode()> (rr_interpreter.kt MapSubscript).
        val (backend, exeCtx) = setup(
            "function get(m: map<integer, integer>, k: integer): integer = m[k];"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["get"]!!

        val m = intIntMap(backend, listOf(1L to 10L, 2L to 20L))
        val e = assertFailsWith<Rt_Exception> {
            callInt(backend, exeCtx, fn, m, int(99))
        }
        assertEquals("fn_map_get_novalue:int[99]", (e.err as Rt_CommonError).code)
        // The whole function soft-fails (interpreter runs it and raises) — counted as a miss.
        assertEquals(0, backend.jitHits)
        assertEquals(1, backend.jitMisses)
    }

    @Test
    fun `a map size member call soft-fails but returns the correct value`() {
        // .size() routes through the stdlib back-call (return ABI not sret) -> whole-function soft-fail.
        val (backend, exeCtx) = setup(
            "function sz(m: map<text, integer>): integer = m.size();"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["sz"]!!

        val m = textIntMap(backend, listOf("a" to 1L, "b" to 2L, "c" to 3L))
        assertEquals(3L, callInt(backend, exeCtx, fn, m))
        assertEquals(0, backend.jitHits)
        assertEquals(1, backend.jitMisses)
    }

    @Test
    fun `a set membership test soft-fails but returns the correct value`() {
        // `k in s` routes through the stdlib back-call -> whole-function soft-fail.
        val (backend, exeCtx) = setup(
            "function has(s: set<integer>, k: integer): boolean = k in s;"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["has"]!!

        val s = intSet(backend, listOf(10L, 20L, 30L))
        assertEquals(true, callBool(backend, exeCtx, fn, s, int(20)))
        assertEquals(false, callBool(backend, exeCtx, fn, s, int(99)))
        assertEquals(0, backend.jitHits)
        assertEquals(2, backend.jitMisses)
    }

    @Test
    fun `a key in map test soft-fails but returns the correct value`() {
        val (backend, exeCtx) = setup(
            "function has(m: map<integer, integer>, k: integer): boolean = k in m;"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["has"]!!

        val m = intIntMap(backend, listOf(1L to 100L, 2L to 200L))
        assertEquals(true, callBool(backend, exeCtx, fn, m, int(1)))
        assertEquals(false, callBool(backend, exeCtx, fn, m, int(7)))
        assertEquals(0, backend.jitHits)
        assertEquals(2, backend.jitMisses)
    }

    @Test
    fun `for over a set soft-fails but iterates in the correct order`() {
        // for-over-set uses the LEGACY_MAP/non-DIRECT iterable adapter -> whole-function soft-fail; the
        // interpreter iterates in LinkedHashSet insertion order, bit-exact.
        val (backend, exeCtx) = setup(
            "function f(s: set<integer>): integer {\n" +
                "    var acc = 0;\n" +
                "    var mul = 1;\n" +
                "    for (x in s) { acc += x * mul; mul *= 10; }\n" +
                "    return acc;\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        // Insertion order [3, 1, 2] -> 3*1 + 1*10 + 2*100 = 213 (order is observable).
        val s = intSet(backend, listOf(3L, 1L, 2L))
        assertEquals(213L, callInt(backend, exeCtx, fn, s))
        assertEquals(0, backend.jitHits)
        assertEquals(1, backend.jitMisses)
    }

    @Test
    fun `for over a map soft-fails but iterates entries in the correct order`() {
        // for-over-map yields (k, v) tuples via the LEGACY_MAP adapter -> whole-function soft-fail; the
        // interpreter iterates in LinkedHashMap insertion order.
        val (backend, exeCtx) = setup(
            "function f(m: map<integer, integer>): integer {\n" +
                "    var acc = 0;\n" +
                "    for ((k, v) in m) acc += k * v;\n" +
                "    return acc;\n" +
                "}"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        // 2*5 + 3*7 = 31.
        val m = intIntMap(backend, listOf(2L to 5L, 3L to 7L))
        assertEquals(31L, callInt(backend, exeCtx, fn, m))
        assertEquals(0, backend.jitHits)
        assertEquals(1, backend.jitMisses)
    }

    @Test
    fun `map equality soft-fails but returns the correct value`() {
        // Map == routes through the JVM (by-value back-call ABI not sret) -> whole-function soft-fail.
        val (backend, exeCtx) = setup(
            "function eq(m: map<integer, integer>, k: integer): boolean = m == [k: k + 1];"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["eq"]!!

        assertEquals(true, callBool(backend, exeCtx, fn, intIntMap(backend, listOf(1L to 2L)), int(1)))
        assertEquals(false, callBool(backend, exeCtx, fn, intIntMap(backend, listOf(1L to 2L)), int(5)))
        assertEquals(0, backend.jitHits)
        assertEquals(2, backend.jitMisses)
    }

    @Test
    fun `set equality soft-fails but returns the correct value`() {
        val (backend, exeCtx) = setup(
            "function eq(s: set<integer>): boolean = s == set([1, 2, 3]);"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["eq"]!!

        assertEquals(true, callBool(backend, exeCtx, fn, intSet(backend, listOf(1L, 2L, 3L))))
        // Set equality ignores order: {3,2,1} == {1,2,3}.
        assertEquals(true, callBool(backend, exeCtx, fn, intSet(backend, listOf(3L, 2L, 1L))))
        assertEquals(false, callBool(backend, exeCtx, fn, intSet(backend, listOf(1L, 2L))))
        assertEquals(0, backend.jitHits)
        assertEquals(3, backend.jitMisses)
    }

    // ---- helpers ----------------------------------------------------------------------

    private fun int(n: Int): Rt_Value = Rt_IntValue.get(n.toLong())

    private fun textIntMap(backend: Llvm_Backend, pairs: List<Pair<String, Long>>): Rt_Value {
        val rtType = backend.resolveType(
            RR_Type.Map(RR_Type.Primitive(RR_PrimitiveKind.TEXT), RR_Type.Primitive(RR_PrimitiveKind.INTEGER))
        )
        // mutableMapOf == LinkedHashMap: insertion order matches the interpreter's MapLiteral.
        val m = LinkedHashMap<Rt_Value, Rt_Value>()
        for ((k, v) in pairs) m[Rt_TextValue.get(k)] = Rt_IntValue.get(v)
        return Rt_MapValue(rtType, m)
    }

    private fun intIntMap(backend: Llvm_Backend, pairs: List<Pair<Long, Long>>): Rt_Value {
        val rtType = backend.resolveType(
            RR_Type.Map(RR_Type.Primitive(RR_PrimitiveKind.INTEGER), RR_Type.Primitive(RR_PrimitiveKind.INTEGER))
        )
        val m = LinkedHashMap<Rt_Value, Rt_Value>()
        for ((k, v) in pairs) m[Rt_IntValue.get(k)] = Rt_IntValue.get(v)
        return Rt_MapValue(rtType, m)
    }

    private fun intSet(backend: Llvm_Backend, vals: List<Long>): Rt_Value {
        val rtType = backend.resolveType(RR_Type.Set(RR_Type.Primitive(RR_PrimitiveKind.INTEGER)))
        // mutableSetOf == LinkedHashSet: insertion order matches the interpreter's set() constructor.
        val s = LinkedHashSet<Rt_Value>()
        for (v in vals) s.add(Rt_IntValue.get(v))
        return Rt_SetValue(rtType, s)
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
