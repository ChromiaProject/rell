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
import net.postchain.rell.base.runtime.Rt_BooleanValue
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
import net.postchain.rell.base.runtime.Rt_StructValue
import net.postchain.rell.base.runtime.Rt_TextValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * NATIVE text — reusing the arena-owned heap-value carrier machinery from byte_array (BYTEARRAY) and
 * list (LIST), bit-exact vs the interpreter.
 *
 * A text crosses the JNI boundary as an arena-owned TEXT carrier (`rell_runtime.h` §1): a buffer of
 * UTF-16 code UNITS (jchar) — the EXACT physical layout of the `java.lang.String` backing
 * [Rt_TextValue] (`rt_value_text.kt`). `value.cpp::from_jvm` copies the String's code units into a fresh
 * arena buffer via `GetStringRegion` (a COPY, never a pin); `to_jvm` rebuilds the EXACT [Rt_TextValue]
 * via `NewString` + `Llvm_SysBridge.textValue` ([Rt_TextValue.get], canonicalising empty -> `EMPTY`), so
 * `from_jvm`∘`to_jvm` is content-identity ([Rt_TextValue.equals] == String content equality), including
 * non-ASCII and surrogate-pair strings (the code units survive exactly — NewString takes UTF-16 units).
 *
 * `lower_expr.cpp` lowers a `"..."` literal to `rell_make_text` (the FlatBuffer UTF-8 string is decoded
 * to UTF-16 at compile time, matching the JVM's decode of the same wire bytes) and `TextSubscriptExpr`
 * to a bounds-checked `rell_text_get` (returning a 1-code-unit text; an OOB subscript raises the EXACT
 * interpreter `expr_text_subscript_index` Rt_Exception). `lower_ops.cpp` lowers `==`/`!=` to native
 * code-unit content equality and `<`/`<=`/`>`/`>=` to a native `String.compareTo` (UNSIGNED 16-bit
 * code-unit order, then length — `rt_ops.kt` `R_CmpType_Text`), and `+` to a native concat (`rt_ops.kt`
 * `R_BinaryOp_Concat_Text`). `lower_call.cpp` lowers `.size()`/`.empty()` natively (the TEXT carrier
 * exposes the code-unit length; `.size()` is `String.length` == CODE UNITS, NOT code points).
 *
 * Each test pins `jitHits>0` / `jitMisses==0` AND that the result equals what the interpreter produces.
 * Non-trivial String stdlib ops (`.upper_case()`/`.sub()`/...) route through the JVM (`rell_sysfn_call`),
 * which soft-fails the function today (the by-value return ABI is not yet sret), asserted to still return
 * the correct value via the interpreter. Mirrors [Llvm_ByteArrayCoverageTest].
 */
class Llvm_TextCoverageTest {

    @Test
    fun `an ASCII text literal round-trips through the value ABI`() {
        val (backend, exeCtx) = setup("function f(): text = \"hello\";")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(txt("hello"), backend.callFunction(fn, exeCtx, listOf()))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a non-ASCII text literal round-trips (BMP accents)`() {
        // "héllo" — 'é' is U+00E9, a single BMP code unit (2 UTF-8 bytes -> 1 UTF-16 unit). The compile-
        // time UTF-8 -> UTF-16 decode must produce the identical String the JVM decodes.
        val (backend, exeCtx) = setup("function f(): text = \"héllo\";")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(txt("héllo"), backend.callFunction(fn, exeCtx, listOf()))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a surrogate-pair text literal round-trips (non-BMP emoji)`() {
        // "a😀b" — 😀 is U+1F600, a SUPPLEMENTARY code point: 4 UTF-8 bytes -> a SURROGATE PAIR of two
        // UTF-16 units (0xD83D 0xDE00). The carrier stores both units exactly as the JVM String does, and
        // to_jvm's NewString reconstructs the identical String.
        val (backend, exeCtx) = setup("function f(): text = \"a😀b\";")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(txt("a😀b"), backend.callFunction(fn, exeCtx, listOf()))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a text param round-trips identity (incl non-ASCII and a surrogate pair)`() {
        // from_jvm copied the code units into an arena buffer; to_jvm rebuilt the EXACT Rt_TextValue.
        val (backend, exeCtx) = setup("function id(t: text): text = t;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["id"]!!

        for (s in listOf("ascii", "héllo wörld", "a😀b", "")) {
            assertEquals(txt(s), backend.callFunction(fn, exeCtx, listOf(txt(s))))
        }
        assertEquals(4, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `an empty text round-trips`() {
        val (backend, exeCtx) = setup("function f(): text = \"\";")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        // The empty literal canonicalises to Rt_TextValue.EMPTY on rebox.
        assertEquals(txt(""), backend.callFunction(fn, exeCtx, listOf()))
        assertEquals(Rt_TextValue.EMPTY, backend.callFunction(fn, exeCtx, listOf()))
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `text size is native and bit-exact (CODE UNITS not code points)`() {
        val (backend, exeCtx) = setup("function sz(t: text): integer = t.size();")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["sz"]!!

        assertEquals(5L, callInt(backend, exeCtx, fn, txt("hello")))
        assertEquals(0L, callInt(backend, exeCtx, fn, txt("")))
        assertEquals(5L, callInt(backend, exeCtx, fn, txt("héllo")))  // 'é' is 1 code unit.
        // CRITICAL code-unit-vs-codepoint case: "a😀b" is 4 CODE UNITS (a + high-surrogate + low-surrogate
        // + b), NOT 3 code points. String.length is 4, and the native buffer length is 4 — bit-exact. A
        // code-point count would (wrongly) be 3.
        assertEquals(4L, callInt(backend, exeCtx, fn, txt("a😀b")))
        assertEquals(4, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `text empty is native and bit-exact`() {
        val (backend, exeCtx) = setup("function e(t: text): boolean = t.empty();")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["e"]!!

        assertEquals(true, callBool(backend, exeCtx, fn, txt("")))
        assertEquals(false, callBool(backend, exeCtx, fn, txt("x")))
        assertEquals(2, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `text equality and inequality are native and bit-exact`() {
        val (backend, exeCtx) = setup(
            "function eq(a: text, b: text): boolean = a == b;\n" +
                "function ne(a: text, b: text): boolean = a != b;"
        )
        val eq = backend.rrApp.module(ModuleName.EMPTY)!!.functions["eq"]!!
        val ne = backend.rrApp.module(ModuleName.EMPTY)!!.functions["ne"]!!

        assertEquals(true, callBool(backend, exeCtx, eq, txt("abc"), txt("abc")))
        assertEquals(false, callBool(backend, exeCtx, eq, txt("abc"), txt("abd")))
        assertEquals(false, callBool(backend, exeCtx, eq, txt("ab"), txt("abc")))  // length differs
        assertEquals(true, callBool(backend, exeCtx, eq, txt(""), txt("")))          // both empty
        assertEquals(true, callBool(backend, exeCtx, eq, txt("héllo"), txt("héllo")))  // non-ASCII
        assertEquals(true, callBool(backend, exeCtx, ne, txt("a"), txt("b")))
        assertEquals(false, callBool(backend, exeCtx, ne, txt("xy"), txt("xy")))
        assertEquals(7, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    // The "￿".compareTo("😀") < 0 oracle is deliberately a compile-time constant: it pins the JVM's
    // UNSIGNED code-unit ordering as the expected value. The inspections that flag it as constant /
    // simplifiable are suppressed because the constantness IS the point of the assertion.
    @Suppress("KotlinConstantConditions", "ReplaceCallWithBinaryOperator", "SimplifyBooleanWithConstants")
    fun `text comparison ordering matches String compareTo (UNSIGNED code-unit order)`() {
        val (backend, exeCtx) = setup("function lt(a: text, b: text): boolean = a < b;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["lt"]!!

        // "a" < "b" (basic).
        assertEquals(true, callBool(backend, exeCtx, fn, txt("a"), txt("b")))
        // CRITICAL: "Z" (0x5A) < "a" (0x61) — uppercase sorts BEFORE lowercase by code unit (the same as
        // String.compareTo, which is the interpreter's order via rt_ops.kt R_CmpType_Text).
        assertEquals(true, callBool(backend, exeCtx, fn, txt("Z"), txt("a")))
        // Prefix is LESS than the longer string (length tiebreak): "ab" < "abc".
        assertEquals(true, callBool(backend, exeCtx, fn, txt("ab"), txt("abc")))
        // Equal -> not less-than.
        assertEquals(false, callBool(backend, exeCtx, fn, txt("xy"), txt("xy")))
        // CRITICAL non-BMP vs BMP ordering: "￿" (a BMP code unit, 0xFFFF) vs "😀" (the
        // emoji, whose FIRST code unit is the high surrogate 0xD83D). UNSIGNED 16-bit: 0xFFFF > 0xD83D, so
        // "￿" is NOT less than the emoji string. String.compareTo agrees (it compares the first
        // differing CODE UNIT as unsigned char, NOT by code point), proving we compare code units unsigned,
        // not code points. A code-point comparison would put U+FFFF (65535) BELOW U+1F600 (128512) and
        // wrongly answer true.
        assertEquals(
            "￿".compareTo("😀") < 0,  // == false (sanity-pin the JVM oracle)
            callBool(backend, exeCtx, fn, txt("￿"), txt("😀")),
        )
        assertEquals(false, callBool(backend, exeCtx, fn, txt("￿"), txt("😀")))
        assertEquals(6, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `text subscript returns a 1-code-unit text`() {
        val (backend, exeCtx) = setup("function get(t: text, i: integer): text = t[i];")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["get"]!!

        assertEquals(txt("h"), backend.callFunction(fn, exeCtx, listOf(txt("hello"), int(0))))
        assertEquals(txt("o"), backend.callFunction(fn, exeCtx, listOf(txt("hello"), int(4))))
        // 'é' (BMP) is a single code unit -> a length-1 text.
        assertEquals(txt("é"), backend.callFunction(fn, exeCtx, listOf(txt("héllo"), int(1))))
        // A surrogate index yields the LONE high surrogate as a length-1 text — exactly as the
        // interpreter (Rt_TextValue.get(text[idx].toString())): "a😀b"[1] is the high surrogate 0xD83D.
        assertEquals(
            txt("a😀b"[1].toString()),
            backend.callFunction(fn, exeCtx, listOf(txt("a😀b"), int(1))),
        )
        assertEquals(4, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a text subscript out of bounds raises the same error as the interpreter on the JIT path`() {
        val (backend, exeCtx) = setup("function get(t: text, i: integer): text = t[i];")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["get"]!!

        // In bounds: native, bit-exact.
        assertEquals(txt("o"), backend.callFunction(fn, exeCtx, listOf(txt("hello"), int(4))))

        // Out of bounds (index == length): the JIT path raises the EXACT interpreter Rt_Exception.
        val e1 = assertFailsWith<Rt_Exception> {
            backend.callFunction(fn, exeCtx, listOf(txt("hello"), int(5)))
        }
        assertEquals("expr_text_subscript_index:5:5", (e1.err as Rt_CommonError).code)

        // Negative index is OUT OF BOUNDS (no Python-style wrap), per the interpreter.
        val e2 = assertFailsWith<Rt_Exception> {
            backend.callFunction(fn, exeCtx, listOf(txt("hello"), int(-1)))
        }
        assertEquals("expr_text_subscript_index:5:-1", (e2.err as Rt_CommonError).code)

        // All three calls executed natively (the in-bounds read + two native OOB raises count as hits).
        assertEquals(3, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `text concat is native and bit-exact`() {
        val (backend, exeCtx) = setup("function cat(a: text, b: text): text = a + b;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["cat"]!!

        assertEquals(txt("abcd"), backend.callFunction(fn, exeCtx, listOf(txt("ab"), txt("cd"))))
        // Empty-operand identity (both directions).
        assertEquals(txt("x"), backend.callFunction(fn, exeCtx, listOf(txt("x"), txt(""))))
        assertEquals(txt("y"), backend.callFunction(fn, exeCtx, listOf(txt(""), txt("y"))))
        assertEquals(txt(""), backend.callFunction(fn, exeCtx, listOf(txt(""), txt(""))))
        // Concatenating across a surrogate boundary preserves the units (no re-normalisation).
        assertEquals(txt("a😀"), backend.callFunction(fn, exeCtx, listOf(txt("a"), txt("😀"))))
        assertEquals(5, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a text literal concat is native`() {
        val (backend, exeCtx) = setup("function f(): text = \"ab\" + \"cd\";")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["f"]!!

        assertEquals(txt("abcd"), backend.callFunction(fn, exeCtx, listOf()))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a text inside a struct round-trips identity`() {
        val (backend, exeCtx) = setup(
            "struct Box { tag: integer; label: text; }\n" +
                "function id(x: Box): Box = x;"
        )
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["id"]!!

        val input = struct(backend, "Box", listOf(int(7), txt("héllo 😀")))
        assertEquals(input, backend.callFunction(fn, exeCtx, listOf(input)))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `a list of text round-trips identity`() {
        val (backend, exeCtx) = setup("function id(xs: list<text>): list<text> = xs;")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["id"]!!

        val input = textList(backend, listOf(txt("a"), txt("héllo"), txt(""), txt("😀")))
        assertEquals(input, backend.callFunction(fn, exeCtx, listOf(input)))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `upper_case soft-fails but returns the correct value via the interpreter`() {
        // .upper_case() routes through the JVM (rell_sysfn_call), which soft-fails the whole function
        // today (the by-value return ABI is not sret) — but the value is still correct.
        val (backend, exeCtx) = setup("function u(t: text): text = t.upper_case();")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["u"]!!

        assertEquals(txt("HELLO"), backend.callFunction(fn, exeCtx, listOf(txt("hello"))))
        assertEquals(0, backend.jitHits)
        assertEquals(1, backend.jitMisses)
    }

    @Test
    fun `sub is native and bit-exact (member text ops wired in Llvm_TextMemberOpsTest)`() {
        // sub() is now lowered NATIVELY (rell_text_sub2 over the code-unit buffer) — see
        // Llvm_TextMemberOpsTest for the full member-op coverage. This pins that the previously soft-
        // failing case is now a JIT hit with the identical value.
        val (backend, exeCtx) = setup("function s(t: text): text = t.sub(1, 3);")
        val fn = backend.rrApp.module(ModuleName.EMPTY)!!.functions["s"]!!

        assertEquals(txt("el"), backend.callFunction(fn, exeCtx, listOf(txt("hello"))))
        assertEquals(1, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    // ---- helpers ----------------------------------------------------------------------

    private fun int(n: Int): Rt_Value = Rt_IntValue.get(n.toLong())

    private fun txt(s: String): Rt_Value = Rt_TextValue.get(s)

    private fun textList(backend: Llvm_Backend, vals: List<Rt_Value>): Rt_Value {
        val rtType = backend.resolveType(RR_Type.List(RR_Type.Primitive(RR_PrimitiveKind.TEXT)))
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
