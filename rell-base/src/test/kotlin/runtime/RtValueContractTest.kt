/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvNull
import java.lang.reflect.Modifier
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.*

/**
 * Pins runtime contracts that are easy to silently regress:
 * - validated-data-class `copy()` must NOT be public (would bypass `get()` factory validation),
 * - construction-time validation throws on out-of-range / negative inputs,
 * - GTV decoders raise typed [Rt_GtvError]s for malformed inputs (not bare `IllegalStateException`),
 * - lazy values evaluate exactly once,
 * - common Gtv constants intern.
 */
class RtValueContractTest {
    /**
     * `@ConsistentCopyVisibility` + `private constructor` → synthesised `copy()` must inherit
     * private visibility. `Rt_DecimalValue` and `Rt_TextValue` no longer use this pattern (they
     * are sealed bases with concrete leaves), but the four below remain validated data classes
     * whose factory invariants (range checks, intern-on-canonical) would be bypassed by a
     * public `copy()`.
     */
    @Test fun copyOfValidatedDataClassesIsNotPublic() {
        val classes = listOf(
            Rt_IntValue::class.java,
            Rt_RowidValue::class.java,
            Rt_GtvValue::class.java,
            Rt_BigIntegerValue::class.java,
        )
        for (cls in classes) {
            val copyMethods = cls.declaredMethods.filter { it.name == "copy" }
            assertTrue(
                copyMethods.isNotEmpty(),
                "${cls.simpleName} expected to have a synthesized copy() method",
            )
            for (m in copyMethods) {
                assertFalse(
                    Modifier.isPublic(m.modifiers),
                    "${cls.simpleName}.copy() must NOT be public — would bypass construction-time validation",
                )
            }
        }
    }

    @Test fun decimalGetRejectsOverflow() {
        // Decimal range is -10^131072..10^131072 exclusive (Lib_DecimalMath.DECIMAL_INT_DIGITS).
        val tooBig = BigDecimal("1e131073")
        val ex = assertFails { Rt_DecimalValue.get(tooBig) }
        assertTrue(ex is Rt_Exception, "Expected Rt_Exception, got ${ex::class}")
        assertEquals("rt_err:decimal:overflow", (ex.err as Rt_CommonError).code())
    }

    @Test fun decimalGetOrNullReturnsNullOnOverflow() {
        assertEquals(null, Rt_DecimalValue.getOrNull(BigDecimal("1e131073")))
    }

    @Test fun bigIntegerGetRejectsOverflow() {
        // Big-integer range is -10^131072..10^131072 exclusive (Lib_BigIntegerMath.PRECISION).
        val tooBig = BigInteger.TEN.pow(131073)
        val ex = assertFails { Rt_BigIntegerValue.get(tooBig) }
        assertTrue(ex is Rt_Exception)
        assertEquals("rt_err:bigint:overflow", (ex.err as Rt_CommonError).code())
    }

    @Test fun rowidConstructorRejectsNegative() {
        val ex = assertFails { Rt_RowidValue.get(-1L) }
        assertTrue(ex is IllegalStateException, "Expected IllegalStateException from init check, got ${ex::class}")
    }

    /** Rt_RowidValue.fromGtv must reject a negative Gtv integer with a typed Rt_GtvError, not Rt_CommonError. */
    @Test fun rowidFromGtvRejectsNegativeInteger() {
        val ctx = GtvToRtContext.make(pretty = false)
        val ex = assertFails { Rt_RowidValue.fromGtv(ctx, GtvFactory.gtv(-5L)) }
        assertTrue(ex is Rt_Exception)
        val err = ex.err
        assertTrue(err is Rt_GtvError, "Expected Rt_GtvError, got ${err::class}")
        assertEquals("gtv_err:rowid:negative:-5", err.code())
    }

    /** Decimal fromGtv non-strict + bigInt branch coverage (rt_value_decimal.kt:90-105). */
    @Test fun decimalFromGtvAcceptsIntegerInNonStrictMode() {
        val ctx = GtvToRtContext.make(pretty = false, strictGtvConversion = false)
        val v = Rt_DecimalValue.fromGtv(ctx, GtvFactory.gtv(42L))
        // Compare numerically: Lib_DecimalMath.scale normalises the result to DECIMAL_FRAC_DIGITS,
        // so `v.value` is "42.00…0" and BigDecimal.equals (scale-sensitive) would reject it.
        assertEquals(0, BigDecimal("42").compareTo(v.value))
    }

    @Test fun decimalFromGtvAcceptsBigIntegerInNonStrictMode() {
        val ctx = GtvToRtContext.make(pretty = false, strictGtvConversion = false)
        val v = Rt_DecimalValue.fromGtv(ctx, GtvFactory.gtv(BigInteger("12345678901234567890")))
        assertEquals(0, BigDecimal("12345678901234567890").compareTo(v.value))
    }

    @Test fun decimalFromGtvRejectsIntegerInStrictMode() {
        val ctx = GtvToRtContext.make(pretty = false, strictGtvConversion = true)
        // strictGtvConversion forces the string-decode branch; a Gtv integer fails.
        val ex = assertFails { Rt_DecimalValue.fromGtv(ctx, GtvFactory.gtv(42L)) }
        assertTrue(ex is Rt_Exception, "Expected Rt_Exception, got ${ex::class}")
        assertTrue(ex.err is Rt_GtvError, "Expected Rt_GtvError, got ${ex.err::class}")
    }

    /** Rt_NullValue.fromGtv must throw a typed Rt_GtvError on non-null input, not bare IllegalStateException. */
    @Test fun nullValueFromGtvRejectsNonNullWithRtGtvError() {
        val ctx = GtvToRtContext.make(pretty = false)
        val ex = assertFails { Rt_NullValue.gtvConversion.fromGtv(ctx, GtvFactory.gtv(1L)) }
        assertTrue(ex is Rt_Exception, "Expected Rt_Exception, got ${ex::class}")
        assertTrue(ex.err is Rt_GtvError, "Expected Rt_GtvError, got ${ex.err::class}")
    }

    @Test fun nullValueFromGtvAcceptsGtvNull() {
        val ctx = GtvToRtContext.make(pretty = false)
        assertSame(Rt_NullValue, Rt_NullValue.gtvConversion.fromGtv(ctx, GtvNull))
    }

    /** Rt_GtvValue intern instances for canonical zero/empty values (rt_value_gtv.kt:get). */
    @Test fun gtvValueInternsCommonConstants() {
        assertSame(Rt_GtvValue.NULL, Rt_GtvValue.get(GtvNull))
        assertSame(Rt_GtvValue.get(GtvFactory.gtv(0L)), Rt_GtvValue.get(GtvFactory.gtv(0L)))
        assertSame(Rt_GtvValue.get(GtvFactory.gtv("")), Rt_GtvValue.get(GtvFactory.gtv("")))
        assertSame(Rt_GtvValue.get(GtvFactory.gtv(ByteArray(0))), Rt_GtvValue.get(GtvFactory.gtv(ByteArray(0))))
    }

    @Test fun gtvValueDoesNotInternNonCanonicalValues() {
        // Sanity: non-zero integers are not interned (would be a memory leak if they were).
        val a = Rt_GtvValue.get(GtvFactory.gtv(42L))
        val b = Rt_GtvValue.get(GtvFactory.gtv(42L))
        assertEquals(a, b)
        assertTrue(a !== b, "non-canonical Gtv values must not be interned")
    }

    /** Rt_LazyValue.resolveLazy must call compute() exactly once, even across multiple asLazyValue() calls. */
    @Test fun lazyValueComputesExactlyOnce() {
        var calls = 0
        val expected = Rt_IntValue.get(7)
        val lazy = Rt_DeferredLazyValue(Rt_IntValue.Companion) {
            calls++
            expected
        }
        assertSame(expected, lazy.asLazyValue())
        assertSame(expected, lazy.asLazyValue())
        assertSame(expected, lazy.asLazyValue())
        assertEquals(1, calls, "compute() must run exactly once")
    }

    /** Rt_Value.asLazyValue on a non-lazy value must throw a typed Rt_Exception, not a ClassCastException. */
    @Test fun asLazyValueOnNonLazyThrowsTyped() {
        val ex = assertFails { Rt_IntValue.get(1).asLazyValue() }
        assertTrue(ex is Rt_Exception, "Expected Rt_Exception, got ${ex::class}")
    }

    /**
     * Defensive type-tightening: Rt_VirtualMapValue is read-only ([Rt_MapBackedValue], not
     * [Rt_MutableMapBackedValue]). The compiler doesn't currently route a virtual map through
     * `RR_Expr.MapSubscript` (only `RR_Expr.VirtualMapSubscript`), so a `MutableMap` cast would
     * not actually be hit in production today. But `asMap() as MutableMap` succeeded at the cast
     * and would fail later with a generic `UnsupportedOperationException` from the read-only view.
     * `asMutableMap()` rejects up-front with a typed `Rt_Exception`, hardening against a future
     * compiler change that would expose the path.
     */
    @Test fun asMutableMapRefusesReadOnlyVirtualMap() {
        val virtual = Rt_VirtualMapValue(
            gtv = GtvNull,
            type = Rt_NullValue,
            virtualEntryRtType = Rt_NullValue,
            innerMapRtType = Rt_NullValue,
            map = mapOf(Rt_IntValue.get(1) to Rt_IntValue.get(2)),
        )

        assertEquals(1, virtual.asMap().size)

        val ex = assertFails { virtual.asMutableMap() }
        assertTrue(ex is Rt_Exception, "Expected Rt_Exception, got ${ex::class}")
    }
}
