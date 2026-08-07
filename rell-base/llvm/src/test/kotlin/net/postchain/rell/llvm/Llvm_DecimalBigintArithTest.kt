/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.llvm

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.rr.RR_FunctionDefinition
import net.postchain.rell.base.runtime.Rt_AppContext
import net.postchain.rell.base.runtime.Rt_BigIntegerValue
import net.postchain.rell.base.runtime.Rt_ChainContext
import net.postchain.rell.base.runtime.Rt_DecimalValue
import net.postchain.rell.base.runtime.Rt_ExecutionContext
import net.postchain.rell.base.runtime.Rt_GlobalContext
import net.postchain.rell.base.runtime.Rt_NopPrinter
import net.postchain.rell.base.runtime.Rt_NullOpContext
import net.postchain.rell.base.runtime.Rt_NullSqlContext
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.test.assertEquals

/**
 * NATIVE long-fit `decimal` and `big_integer` value-ABI arithmetic / comparison through the general
 * call path ([Llvm_Backend.callFunction] -> [RellLlvmNative.callValueFunction]), NOT the dedicated
 * `compileDecimalFunctionByIndex` string path.
 *
 * Proves, for a function with decimal/big_integer params, locals and return:
 *  - add / sub / mul / div / mod are bit-exact vs the interpreter and take the native path
 *    (jitHits > 0, jitMisses == 0) for in-envelope operands;
 *  - comparison incl. the scale-aware `1.0 == 1.00` EQUAL and `2.5 < 2.50` (== EQUAL) cases;
 *  - mul HALF_UP rounding at exactly the .5 tie (away from zero);
 *  - unary minus produces the canonical stripped normal form;
 *  - a WIDE decimal / big_integer operand correctly SOFT-FAILS the native path (jitMisses == 1 — it
 *    escapes at runtime to a HANDLE and re-runs on the interpreter) and still returns the right value.
 *
 * The bit-exactness oracle is [Rt_DecimalValue.get] / [Rt_BigIntegerValue.get], which run the SAME
 * canonical normal-form path (Lib_DecimalMath.scale) the interpreter uses, so a value-equal assertion
 * against them is an assertion against the interpreter's representation. Each non-escape case also
 * asserts jitHits so the result provably came from JIT'd native code, not a silent interpreter
 * delegate.
 */
class Llvm_DecimalBigintArithTest {

    // ---- decimal arithmetic --------------------------------------------------------------

    @Test
    fun `decimal add sub mul are native and bit-exact`() {
        val (backend, exeCtx) = setup(
            """
            function add(a: decimal, b: decimal): decimal = a + b;
            function sub(a: decimal, b: decimal): decimal = a - b;
            function mul(a: decimal, b: decimal): decimal = a * b;
            """.trimIndent()
        )
        val add = fn(backend, "add")
        val sub = fn(backend, "sub")
        val mul = fn(backend, "mul")

        val h0 = backend.jitHits
        val m0 = backend.jitMisses

        assertEquals(dec("2.8"), callDec(backend, exeCtx, add, dec("2.5"), dec("0.3")))
        assertEquals(dec("5"), callDec(backend, exeCtx, add, dec("2"), dec("3")))
        assertEquals(dec("1.5"), callDec(backend, exeCtx, sub, dec("2.0"), dec("0.5")))
        assertEquals(dec("0"), callDec(backend, exeCtx, sub, dec("1.5"), dec("1.50")))  // -> stripped 0
        assertEquals(dec("3.75"), callDec(backend, exeCtx, mul, dec("1.5"), dec("2.5")))
        assertEquals(dec("2"), callDec(backend, exeCtx, mul, dec("0.5"), dec("4")))

        assertEquals(h0 + 6, backend.jitHits)
        assertEquals(m0, backend.jitMisses)
    }

    @Test
    fun `decimal div mod soft-fail to the interpreter and are bit-exact`() {
        val (backend, exeCtx) = setup(
            """
            function d(a: decimal, b: decimal): decimal = a / b;
            function m(a: decimal, b: decimal): decimal = a % b;
            """.trimIndent()
        )
        val d = fn(backend, "d")
        val m = fn(backend, "m")

        val h0 = backend.jitHits
        val miss0 = backend.jitMisses

        // div: HALF_UP to scale 20; mod: BigDecimal.remainder, normalized. Both escape (no inline
        // long-arithmetic leaf) and re-run on the interpreter, but the VALUE is bit-exact.
        assertEquals(dec("0.33333333333333333333"), callDec(backend, exeCtx, d, dec("1"), dec("3")))
        assertEquals(dec("2.5"), callDec(backend, exeCtx, d, dec("5"), dec("2")))
        assertEquals(dec("1"), callDec(backend, exeCtx, m, dec("7"), dec("3")))

        // div/mod escape at runtime -> counted as misses (interpreter ran them).
        assertEquals(h0, backend.jitHits)
        assertEquals(miss0 + 3, backend.jitMisses)
    }

    @Test
    fun `decimal mul HALF_UP rounds the exact half tie away from zero`() {
        // A product whose scale exceeds DECIMAL_FRAC_DIGITS (20) so the rescale + HALF_UP rounding
        // fires, with the remainder landing on the EXACT .5 tie — the divergence point vs HALF_EVEN.
        // 0.0000000005 (mantissa 5, scale 10) * 0.00000000001 (mantissa 1, scale 11):
        //   product mantissa 5 at newScale 21 > 20 -> drop k=1 digit, divisor 10.
        //   quotient 0, remainder 5; |rem|*2 == 10 == divisor -> tie -> round AWAY from zero -> 1,
        //   scale 20 -> 1E-20. HALF_EVEN would round to 0 (even). The interpreter (BigDecimal HALF_UP
        //   via Lib_DecimalMath.scale) yields 1E-20, which this native path must match bit-for-bit.
        val (backend, exeCtx) = setup("function mul(a: decimal, b: decimal): decimal = a * b;")
        val mul = fn(backend, "mul")
        val h0 = backend.jitHits
        val miss0 = backend.jitMisses

        assertEquals(dec("0.00000000000000000001"),
            callDec(backend, exeCtx, mul, dec("0.0000000005"), dec("0.00000000001")))

        // A negative product ties symmetrically away from zero (-1E-20), not toward even.
        assertEquals(dec("-0.00000000000000000001"),
            callDec(backend, exeCtx, mul, dec("-0.0000000005"), dec("0.00000000001")))

        assertEquals(h0 + 2, backend.jitHits)
        assertEquals(miss0, backend.jitMisses)
    }

    @Test
    fun `decimal unary minus is native and canonical`() {
        val (backend, exeCtx) = setup("function neg(a: decimal): decimal = -a;")
        val neg = fn(backend, "neg")
        val h0 = backend.jitHits

        assertEquals(dec("-1.5"), callDec(backend, exeCtx, neg, dec("1.5")))
        assertEquals(dec("2.5"), callDec(backend, exeCtx, neg, dec("-2.5")))
        // -0.0 normalizes to the canonical zero (stripped (0, 0)).
        assertEquals(dec("0"), callDec(backend, exeCtx, neg, dec("0")))
        // A padded-scale operand negates to the same canonical stripped form (1.50 -> -1.5).
        assertEquals(dec("-1.5"), callDec(backend, exeCtx, neg, dec("1.50")))

        assertEquals(h0 + 4, backend.jitHits)
    }

    @Test
    fun `decimal comparison incl scale-insensitive equality is native`() {
        val (backend, exeCtx) = setup(
            """
            function lt(a: decimal, b: decimal): boolean = a < b;
            function eq(a: decimal, b: decimal): boolean = a == b;
            """.trimIndent()
        )
        val lt = fn(backend, "lt")
        val eq = fn(backend, "eq")
        val h0 = backend.jitHits
        val miss0 = backend.jitMisses

        assertEquals(true, callBool(backend, exeCtx, lt, dec("1.5"), dec("2.0")))
        assertEquals(false, callBool(backend, exeCtx, lt, dec("2.5"), dec("2.50")))  // equal -> not <
        assertEquals(true, callBool(backend, exeCtx, eq, dec("1.0"), dec("1.00")))   // scale-insensitive
        assertEquals(true, callBool(backend, exeCtx, eq, dec("2.5"), dec("2.50")))
        assertEquals(false, callBool(backend, exeCtx, eq, dec("2.5"), dec("2.51")))

        assertEquals(h0 + 5, backend.jitHits)
        assertEquals(miss0, backend.jitMisses)
    }

    @Test
    fun `decimal locals JIT`() {
        val (backend, exeCtx) = setup(
            """
            function f(a: decimal, b: decimal): decimal {
                val s: decimal = a + b;
                val p: decimal = s * b;
                return p - a;
            }
            """.trimIndent()
        )
        val f = fn(backend, "f")
        val h0 = backend.jitHits
        val miss0 = backend.jitMisses

        // (a+b)*b - a, e.g. (1.5+0.5)*0.5 - 1.5 = 2.0*0.5 - 1.5 = 1.0 - 1.5 = -0.5
        assertEquals(dec("-0.5"), callDec(backend, exeCtx, f, dec("1.5"), dec("0.5")))
        assertEquals(h0 + 1, backend.jitHits)
        assertEquals(miss0, backend.jitMisses)
    }

    // ---- big_integer arithmetic ----------------------------------------------------------

    @Test
    fun `big_integer add sub mul are native and bit-exact`() {
        val (backend, exeCtx) = setup(
            """
            function add(a: big_integer, b: big_integer): big_integer = a + b;
            function sub(a: big_integer, b: big_integer): big_integer = a - b;
            function mul(a: big_integer, b: big_integer): big_integer = a * b;
            function neg(a: big_integer): big_integer = -a;
            """.trimIndent()
        )
        val add = fn(backend, "add")
        val sub = fn(backend, "sub")
        val mul = fn(backend, "mul")
        val neg = fn(backend, "neg")
        val h0 = backend.jitHits
        val miss0 = backend.jitMisses

        assertEquals(big(8), callBig(backend, exeCtx, add, big(5), big(3)))
        assertEquals(big(2), callBig(backend, exeCtx, sub, big(5), big(3)))
        assertEquals(big(15), callBig(backend, exeCtx, mul, big(5), big(3)))
        assertEquals(big(-5), callBig(backend, exeCtx, neg, big(5)))

        assertEquals(h0 + 4, backend.jitHits)
        assertEquals(miss0, backend.jitMisses)
    }

    @Test
    fun `big_integer div mod soft-fail and are bit-exact`() {
        val (backend, exeCtx) = setup(
            """
            function d(a: big_integer, b: big_integer): big_integer = a / b;
            function m(a: big_integer, b: big_integer): big_integer = a % b;
            """.trimIndent()
        )
        val d = fn(backend, "d")
        val m = fn(backend, "m")
        val h0 = backend.jitHits
        val miss0 = backend.jitMisses

        assertEquals(big(2), callBig(backend, exeCtx, d, big(7), big(3)))
        assertEquals(big(1), callBig(backend, exeCtx, m, big(7), big(3)))

        assertEquals(h0, backend.jitHits)
        assertEquals(miss0 + 2, backend.jitMisses)
    }

    @Test
    fun `big_integer comparison is native`() {
        val (backend, exeCtx) = setup("function ge(a: big_integer, b: big_integer): boolean = a >= b;")
        val ge = fn(backend, "ge")
        val h0 = backend.jitHits
        val miss0 = backend.jitMisses

        assertEquals(true, callBool(backend, exeCtx, ge, big(5), big(5)))
        assertEquals(false, callBool(backend, exeCtx, ge, big(3), big(7)))

        assertEquals(h0 + 2, backend.jitHits)
        assertEquals(miss0, backend.jitMisses)
    }

    // ---- WIDE (out-of-envelope) cases soft-fail and stay bit-exact -----------------------

    @Test
    fun `wide big_integer operand soft-fails at runtime and is bit-exact`() {
        val (backend, exeCtx) = setup("function add(a: big_integer, b: big_integer): big_integer = a + b;")
        val add = fn(backend, "add")
        val h0 = backend.jitHits
        val miss0 = backend.jitMisses

        // 2^70 exceeds the i64 BIGINT_LONG slice -> a HANDLE at runtime -> the native body escapes
        // (rell_jit_escape) and the WHOLE call re-runs on the interpreter. The value is still exact.
        val wide = BigInteger.TWO.pow(70)
        val expected = Rt_BigIntegerValue.get(wide.add(BigInteger.ONE))
        assertEquals(expected, callBig(backend, exeCtx, add, Rt_BigIntegerValue.get(wide), big(1)))

        // Native path was attempted but escaped at runtime -> counted as a miss, not a hit.
        assertEquals(h0, backend.jitHits)
        assertEquals(miss0 + 1, backend.jitMisses)
    }

    @Test
    fun `wide decimal operand soft-fails at runtime and is bit-exact`() {
        val (backend, exeCtx) = setup("function add(a: decimal, b: decimal): decimal = a + b;")
        val add = fn(backend, "add")
        val h0 = backend.jitHits
        val miss0 = backend.jitMisses

        // A decimal whose unscaled value exceeds i64 (mantissa > Long.MAX) is a HANDLE at runtime;
        // the native add escapes and the interpreter runs it. Value stays bit-exact.
        val wide = Rt_DecimalValue.get("123456789012345678901234567890")  // 30 digits > i64
        val expected = Rt_DecimalValue.get("123456789012345678901234567891")
        assertEquals(expected, callDec(backend, exeCtx, add, wide, dec("1")))

        assertEquals(h0, backend.jitHits)
        assertEquals(miss0 + 1, backend.jitMisses)
    }

    @Test
    fun `mixed long-fit and wide calls hit and miss respectively`() {
        val (backend, exeCtx) = setup("function add(a: decimal, b: decimal): decimal = a + b;")
        val add = fn(backend, "add")
        val h0 = backend.jitHits
        val miss0 = backend.jitMisses

        // Long-fit: native hit.
        assertEquals(dec("3"), callDec(backend, exeCtx, add, dec("1"), dec("2")))
        // Wide: runtime escape -> miss.
        val wide = Rt_DecimalValue.get("99999999999999999999999999999")
        assertEquals(Rt_DecimalValue.get("100000000000000000000000000000"),
            callDec(backend, exeCtx, add, wide, dec("1")))
        // Long-fit again: native hit (the escape did not poison the JIT cache).
        assertEquals(dec("5"), callDec(backend, exeCtx, add, dec("2"), dec("3")))

        assertEquals(h0 + 2, backend.jitHits)
        assertEquals(miss0 + 1, backend.jitMisses)
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun dec(s: String): Rt_DecimalValue = Rt_DecimalValue.get(s)
    private fun big(v: Long): Rt_BigIntegerValue = Rt_BigIntegerValue.get(v)

    private fun fn(backend: Llvm_Backend, name: String): RR_FunctionDefinition =
        backend.rrApp.module(ModuleName.EMPTY)!!.functions[name]!!

    private fun callDec(
        backend: Llvm_Backend,
        exeCtx: Rt_ExecutionContext,
        fn: RR_FunctionDefinition,
        vararg args: Rt_Value,
    ): Rt_Value = backend.callFunction(fn, exeCtx, args.toList())

    private fun callBig(
        backend: Llvm_Backend,
        exeCtx: Rt_ExecutionContext,
        fn: RR_FunctionDefinition,
        vararg args: Rt_Value,
    ): Rt_Value = backend.callFunction(fn, exeCtx, args.toList())

    private fun callBool(
        backend: Llvm_Backend,
        exeCtx: Rt_ExecutionContext,
        fn: RR_FunctionDefinition,
        vararg args: Rt_Value,
    ): Boolean {
        val result = backend.callFunction(fn, exeCtx, args.toList())
        return (result as net.postchain.rell.base.runtime.Rt_BooleanValue).value
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
