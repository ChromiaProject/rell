/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.llvm

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.rr.RR_FunctionDefinition
import net.postchain.rell.base.runtime.Rt_AppContext
import net.postchain.rell.base.runtime.Rt_BooleanValue
import net.postchain.rell.base.runtime.Rt_ChainContext
import net.postchain.rell.base.runtime.Rt_CommonError
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_ExecutionContext
import net.postchain.rell.base.runtime.Rt_GlobalContext
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_NopPrinter
import net.postchain.rell.base.runtime.Rt_NullOpContext
import net.postchain.rell.base.runtime.Rt_NullSqlContext
import net.postchain.rell.base.runtime.Rt_TextValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * NATIVE member text stdlib ops wired into the JIT (`lower_call.cpp` `tryInlineTextMemberArgs` ->
 * `value.cpp` `rell_text_*`), operating directly on the arena-owned UTF-16 code-unit buffer (the TEXT
 * carrier — `rell_runtime.h` §1), bit-exact with `lib_type_text.kt` (whose bodies delegate to
 * `java.lang.String`, the ground truth).
 *
 * WIRED NATIVE (this file pins `jitHits>0` / `jitMisses==0` AND value-equality vs the interpreter):
 *  - `sub(start)` / `sub(start, end)`     — substring by CODE-UNIT index; `calcSub` range check is
 *    ERROR-on-OOB (NOT clamped) -> exact `fn:text.sub:range:<len>:<start>:<end>` Rt_Exception.
 *  - `starts_with` / `ends_with` / `contains` — boolean, code-unit, empty needle always matches.
 *  - `index_of(sub)` / `index_of(sub, start)` — first code-unit index or -1; the 2-arg overload
 *    pre-checks `start in [0,len)` -> exact `fn:text.index_of:index:...` Rt_Exception.
 *  - `char_at(i)`                          — the UNSIGNED 16-bit CODE UNIT as integer; OOB -> exact
 *    `fn:text.char_at:index:...` Rt_Exception.
 *  - `replace(old, new)`                   — LITERAL all-occurrence replace (`String.replace`); an EMPTY
 *    `old` escapes to the interpreter (`rell_jit_escape`) for a provable match.
 *  - `repeat(n)`                           — n copies; `n<0` / `n>Int.MAX` / `total>Int.MAX` -> exact
 *    `fn:text.repeat:{n_negative,n_out_of_range,too_big}:...` Rt_Exception.
 *
 * DEFERRED (kept on the `rell_sysfn_call` back-call, which soft-fails today -> the interpreter runs the
 * function bit-exactly, asserted via the `jitMisses>=1` negative case below): `upper_case` / `lower_case`
 * (LOCALE-sensitive `String.uppercase`/`lowercase`), `format`, `split`, `trim`, `matches` / `like` /
 * `regex_replace`, `to_bytes`, `compare_to`, `reversed`, `last_index_of`.
 *
 * Mirrors [Llvm_TextCoverageTest].
 */
class Llvm_TextMemberOpsTest {

    @Test
    fun `sub(start, end) is native and bit-exact incl surrogate code-unit indexing`() {
        val (backend, exeCtx) = setup("function s(t: text, a: integer, b: integer): text = t.sub(a, b);")
        val fn = fn(backend, "s")

        // "hello".sub(1, 3) == "el".
        assertEquals(txt("el"), backend.callFunction(fn, exeCtx, listOf(txt("hello"), int(1), int(3))))
        // Whole-string and empty sub-ranges.
        assertEquals(txt("hello"), backend.callFunction(fn, exeCtx, listOf(txt("hello"), int(0), int(5))))
        assertEquals(txt(""), backend.callFunction(fn, exeCtx, listOf(txt("hello"), int(2), int(2))))
        // CODE-UNIT indexing across a surrogate pair: "a😀b" code units are [a, hi-surr, lo-surr, b].
        // sub(1, 3) is the two surrogate units == the emoji; sub(1, 2) is the LONE high surrogate.
        assertEquals(txt("😀"), backend.callFunction(fn, exeCtx, listOf(txt("a😀b"), int(1), int(3))))
        assertEquals(
            txt("a😀b".substring(1, 2)),  // the lone high surrogate, exactly as String.substring
            backend.callFunction(fn, exeCtx, listOf(txt("a😀b"), int(1), int(2))),
        )
        assertEquals(5, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `sub(start) is native and bit-exact`() {
        val (backend, exeCtx) = setup("function s(t: text, a: integer): text = t.sub(a);")
        val fn = fn(backend, "s")

        assertEquals(txt("llo"), backend.callFunction(fn, exeCtx, listOf(txt("hello"), int(2))))
        assertEquals(txt("hello"), backend.callFunction(fn, exeCtx, listOf(txt("hello"), int(0))))
        assertEquals(txt(""), backend.callFunction(fn, exeCtx, listOf(txt("hello"), int(5))))  // start == len
        assertEquals(3, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `sub out of range raises the exact interpreter Rt_Exception on the JIT path`() {
        val (backend, exeCtx) = setup("function s(t: text, a: integer, b: integer): text = t.sub(a, b);")
        val fn = fn(backend, "s")

        // calcSub: start > len -> range error (code "fn:text.sub:range:<len>:<start>:<end>").
        val e1 = assertFailsWith<Rt_Exception> {
            backend.callFunction(fn, exeCtx, listOf(txt("hello"), int(6), int(6)))
        }
        assertEquals("fn:text.sub:range:5:6:6", (e1.err as Rt_CommonError).code)

        // end < start -> range error.
        val e2 = assertFailsWith<Rt_Exception> {
            backend.callFunction(fn, exeCtx, listOf(txt("hello"), int(3), int(1)))
        }
        assertEquals("fn:text.sub:range:5:3:1", (e2.err as Rt_CommonError).code)

        // negative start -> range error (NO clamp).
        val e3 = assertFailsWith<Rt_Exception> {
            backend.callFunction(fn, exeCtx, listOf(txt("hello"), int(-1), int(3)))
        }
        assertEquals("fn:text.sub:range:5:-1:3", (e3.err as Rt_CommonError).code)

        // end > len -> range error.
        val e4 = assertFailsWith<Rt_Exception> {
            backend.callFunction(fn, exeCtx, listOf(txt("hello"), int(0), int(6)))
        }
        assertEquals("fn:text.sub:range:5:0:6", (e4.err as Rt_CommonError).code)

        // All four OOB raises ran NATIVELY (JIT hits, no misses).
        assertEquals(4, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `starts_with and ends_with are native and bit-exact`() {
        val (backend, exeCtx) = setup(
            "function sw(t: text, p: text): boolean = t.starts_with(p);\n" +
                "function ew(t: text, s: text): boolean = t.ends_with(s);"
        )
        val sw = fn(backend, "sw")
        val ew = fn(backend, "ew")

        assertEquals(true, callBool(backend, exeCtx, sw, txt("hello"), txt("he")))
        assertEquals(false, callBool(backend, exeCtx, sw, txt("hello"), txt("lo")))
        assertEquals(true, callBool(backend, exeCtx, sw, txt("hello"), txt("")))      // empty prefix
        assertEquals(false, callBool(backend, exeCtx, sw, txt("ab"), txt("abc")))     // longer than self
        assertEquals(true, callBool(backend, exeCtx, ew, txt("hello"), txt("lo")))
        assertEquals(false, callBool(backend, exeCtx, ew, txt("hello"), txt("he")))
        assertEquals(true, callBool(backend, exeCtx, ew, txt("hello"), txt("")))      // empty suffix
        assertEquals(false, callBool(backend, exeCtx, ew, txt("ab"), txt("xab")))     // longer than self
        assertEquals(8, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `contains is native and bit-exact`() {
        val (backend, exeCtx) = setup("function c(t: text, s: text): boolean = t.contains(s);")
        val fn = fn(backend, "c")

        assertEquals(true, callBool(backend, exeCtx, fn, txt("hello"), txt("ell")))
        assertEquals(false, callBool(backend, exeCtx, fn, txt("hello"), txt("xyz")))
        assertEquals(true, callBool(backend, exeCtx, fn, txt("hello"), txt("")))       // empty -> true
        assertEquals(true, callBool(backend, exeCtx, fn, txt("hello"), txt("hello")))  // whole string
        assertEquals(4, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `index_of(sub) is native and bit-exact incl not-found and surrogate units`() {
        val (backend, exeCtx) = setup("function io(t: text, s: text): integer = t.index_of(s);")
        val fn = fn(backend, "io")

        assertEquals(1L, callInt(backend, exeCtx, fn, txt("hello"), txt("ell")))
        assertEquals(-1L, callInt(backend, exeCtx, fn, txt("hello"), txt("xyz")))   // not found -> -1
        assertEquals(0L, callInt(backend, exeCtx, fn, txt("hello"), txt("")))       // empty -> 0
        assertEquals(2L, callInt(backend, exeCtx, fn, txt("hello"), txt("llo")))
        // CODE-UNIT index of a surrogate: "a😀b".indexOf("😀") is the high-surrogate's index (1).
        assertEquals(
            "a😀b".indexOf("😀").toLong(),  // == 1 (sanity-pin the JVM oracle)
            callInt(backend, exeCtx, fn, txt("a😀b"), txt("😀")),
        )
        assertEquals(5, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `index_of(sub, start) is native, bit-exact, and raises on bad start`() {
        val (backend, exeCtx) =
            setup("function io(t: text, s: text, i: integer): integer = t.index_of(s, i);")
        val fn = fn(backend, "io")

        // "ababab".index_of("ab", 1) == 2 (first match at or after index 1).
        assertEquals(2L, callInt(backend, exeCtx, fn, txt("ababab"), txt("ab"), int(1)))
        assertEquals(0L, callInt(backend, exeCtx, fn, txt("ababab"), txt("ab"), int(0)))
        assertEquals(-1L, callInt(backend, exeCtx, fn, txt("ababab"), txt("zz"), int(0)))

        // start out of [0, len): the 2-arg overload throws fn:text.index_of:index:<len>:<start>.
        val e1 = assertFailsWith<Rt_Exception> {
            backend.callFunction(fn, exeCtx, listOf(txt("hello"), txt("l"), int(5)))
        }
        assertEquals("fn:text.index_of:index:5:5", (e1.err as Rt_CommonError).code)
        val e2 = assertFailsWith<Rt_Exception> {
            backend.callFunction(fn, exeCtx, listOf(txt("hello"), txt("l"), int(-1)))
        }
        assertEquals("fn:text.index_of:index:5:-1", (e2.err as Rt_CommonError).code)

        assertEquals(5, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `char_at is native and bit-exact incl OOB and a surrogate code unit`() {
        val (backend, exeCtx) = setup("function c(t: text, i: integer): integer = t.char_at(i);")
        val fn = fn(backend, "c")

        assertEquals('h'.code.toLong(), callInt(backend, exeCtx, fn, txt("hello"), int(0)))
        assertEquals('o'.code.toLong(), callInt(backend, exeCtx, fn, txt("hello"), int(4)))
        assertEquals('é'.code.toLong(), callInt(backend, exeCtx, fn, txt("héllo"), int(1)))
        // char_at at a surrogate index yields the lone high-surrogate's 16-bit code (UNSIGNED 0..65535).
        assertEquals(
            "a😀b"[1].code.toLong(),  // == 0xD83D, the high surrogate (sanity-pin the JVM oracle)
            callInt(backend, exeCtx, fn, txt("a😀b"), int(1)),
        )

        // OOB (index >= length) and negative -> exact fn:text.char_at:index:<len>:<i> error.
        val e1 = assertFailsWith<Rt_Exception> {
            backend.callFunction(fn, exeCtx, listOf(txt("hello"), int(5)))
        }
        assertEquals("fn:text.char_at:index:5:5", (e1.err as Rt_CommonError).code)
        val e2 = assertFailsWith<Rt_Exception> {
            backend.callFunction(fn, exeCtx, listOf(txt("hello"), int(-1)))
        }
        assertEquals("fn:text.char_at:index:5:-1", (e2.err as Rt_CommonError).code)

        assertEquals(6, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `replace is native and bit-exact for a non-empty target`() {
        val (backend, exeCtx) =
            setup("function r(t: text, o: text, n: text): text = t.replace(o, n);")
        val fn = fn(backend, "r")

        // replace("aa", "a", "b") -> "bb" (two non-overlapping single-char matches).
        assertEquals(txt("bb"), backend.callFunction(fn, exeCtx, listOf(txt("aa"), txt("a"), txt("b"))))
        // Multi-char target, longer replacement.
        assertEquals(
            txt("X-Y-Z"),
            backend.callFunction(fn, exeCtx, listOf(txt("X.Y.Z"), txt("."), txt("-"))),
        )
        // No occurrence -> unchanged.
        assertEquals(
            txt("hello"),
            backend.callFunction(fn, exeCtx, listOf(txt("hello"), txt("z"), txt("Z"))),
        )
        // Replacement shorter (deletion of all matches).
        assertEquals(
            txt("abc"),
            backend.callFunction(fn, exeCtx, listOf(txt("axbxc"), txt("x"), txt(""))),
        )
        // Non-overlapping left-to-right (String.replace): "aaa".replace("aa","b") == "ba".
        assertEquals(txt("ba"), backend.callFunction(fn, exeCtx, listOf(txt("aaa"), txt("aa"), txt("b"))))
        assertEquals(5, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `replace with an empty target escapes to the interpreter but is bit-exact`() {
        // An EMPTY old_value has subtle JVM splice semantics ("ab".replace("","-") == "-a-b-"); the
        // native helper escapes (rell_jit_escape), so the interpreter runs the call — a JIT MISS, but the
        // value is exactly String.replace's.
        val (backend, exeCtx) =
            setup("function r(t: text, n: text): text = t.replace(\"\", n);")
        val fn = fn(backend, "r")

        assertEquals(
            txt("ab".replace("", "-")),  // "-a-b-" — the JVM oracle
            backend.callFunction(fn, exeCtx, listOf(txt("ab"), txt("-"))),
        )
        assertEquals(0, backend.jitHits)
        assertEquals(1, backend.jitMisses)
    }

    @Test
    fun `repeat is native and bit-exact incl zero and error edges`() {
        val (backend, exeCtx) = setup("function r(t: text, n: integer): text = t.repeat(n);")
        val fn = fn(backend, "r")

        assertEquals(txt("abcabcabc"), backend.callFunction(fn, exeCtx, listOf(txt("abc"), int(3))))
        assertEquals(txt(""), backend.callFunction(fn, exeCtx, listOf(txt("abc"), int(0))))   // n == 0
        assertEquals(txt("abc"), backend.callFunction(fn, exeCtx, listOf(txt("abc"), int(1)))) // n == 1
        assertEquals(txt(""), backend.callFunction(fn, exeCtx, listOf(txt(""), int(5))))       // empty self

        // n < 0 -> fn:text.repeat:n_negative:<n>.
        val e1 = assertFailsWith<Rt_Exception> {
            backend.callFunction(fn, exeCtx, listOf(txt("abc"), int(-1)))
        }
        assertEquals("fn:text.repeat:n_negative:-1", (e1.err as Rt_CommonError).code)

        assertEquals(5, backend.jitHits)
        assertEquals(0, backend.jitMisses)
    }

    @Test
    fun `upper_case stays a back-call (deferred) but returns the correct value`() {
        // NEGATIVE control: upper_case is DEFERRED (locale-sensitive String.uppercase) — it routes through
        // rell_sysfn_call, which soft-fails today, so the interpreter runs the function (jitMisses >= 1)
        // and the value is still correct.
        val (backend, exeCtx) = setup("function u(t: text): text = t.upper_case();")
        val fn = fn(backend, "u")

        assertEquals(txt("HELLO"), backend.callFunction(fn, exeCtx, listOf(txt("hello"))))
        assertEquals(0, backend.jitHits)
        assertEquals(1, backend.jitMisses)
    }

    // ---- helpers ----------------------------------------------------------------------

    private fun int(n: Int): Rt_Value = Rt_IntValue.get(n.toLong())

    private fun txt(s: String): Rt_Value = Rt_TextValue.get(s)

    private fun fn(backend: Llvm_Backend, name: String): RR_FunctionDefinition =
        backend.rrApp.module(ModuleName.EMPTY)!!.functions[name]!!

    private fun callBool(
        backend: Llvm_Backend,
        exeCtx: Rt_ExecutionContext,
        fn: RR_FunctionDefinition,
        vararg args: Rt_Value,
    ): Boolean = (backend.callFunction(fn, exeCtx, args.toList()) as Rt_BooleanValue).value

    private fun callInt(
        backend: Llvm_Backend,
        exeCtx: Rt_ExecutionContext,
        fn: RR_FunctionDefinition,
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
