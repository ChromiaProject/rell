/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Two pure-RR GTV-conversion entry points exist post-cb938c6a9:
 *
 * - [createGtvConversion] in `rt_type_gtv.kt` — used by the R_Type bridge ([buildBridgeGtvConversion])
 *   for primitives and null.
 * - [Rt_Interpreter.buildCompositeGtvConversion] in `rr_interp_gtv.kt` — used by the pure-RR
 *   interpreter for primitives, null, nullable, composites (list/set/map/tuple), and virtuals.
 *
 * For overlapping kinds (primitives + null) the two entry points must dispatch to the same
 * [Rt_GtvCompatibleValueClass] singleton — otherwise GTV encoding/decoding could subtly diverge
 * between the bridge and the interpreter (consensus risk). This test pins that invariant.
 *
 * Composite parity is exercised end-to-end by `RR_InterpreterTest` and the `SqlEmissionTest`
 * round-trips; it cannot be unit-tested here without a full `Rt_Interpreter` fixture.
 */
class RtTypeGtvConversionParityTest {
    @Test fun primitivesUsePureRRConversionFromValueClassCompanion() {
        val expected = RR_PrimitiveKind.values().toList()
            .filter { it != RR_PrimitiveKind.GUID && it != RR_PrimitiveKind.SIGNER }
            .filter { it != RR_PrimitiveKind.RANGE && it != RR_PrimitiveKind.UNIT }
        // Guard against a future enum refactor that empties this list (which would make the loop vacuous).
        assertTrue(expected.isNotEmpty(), "expected at least one comparable primitive kind")
        for (kind in expected) {
            val viaCreate = createGtvConversion(RR_Type.Primitive(kind))
            val viaCompanion = primitiveValueClass(kind)?.gtvConversion
            assertNotNull(viaCreate, "createGtvConversion(Primitive($kind)) should be non-null")
            assertNotNull(viaCompanion, "primitiveValueClass($kind).gtvConversion should be non-null")
            assertSame(
                viaCompanion,
                viaCreate,
                "Both pure-RR entry points must share the same conversion singleton for $kind",
            )
        }
    }

    @Test fun nullUsesSameSingletonOnBothPaths() {
        val viaCreate = createGtvConversion(RR_Type.Null)
        // primitiveValueClass doesn't model RR_Type.Null; the parallel is Rt_NullValue.gtvConversion.
        val viaCompanion = Rt_NullValue.gtvConversion
        assertSame(viaCompanion, viaCreate, "Null gtv-conversion must be the singleton on both paths")
    }

    /** GUID and SIGNER lack value-class companions; pure-RR conversion returns null. */
    @Test fun unsupportedPrimitivesYieldNullOnBothPaths() {
        for (kind in listOf(RR_PrimitiveKind.GUID, RR_PrimitiveKind.SIGNER)) {
            assertNull(
                createGtvConversion(RR_Type.Primitive(kind)),
                "createGtvConversion should refuse $kind",
            )
            assertNull(
                primitiveValueClass(kind),
                "primitiveValueClass should refuse $kind",
            )
        }
    }
}
