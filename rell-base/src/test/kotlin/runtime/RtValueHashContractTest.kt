/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests pinning the equals/hashCode contract for value classes whose hash is derived
 * from the value's [Rt_ValueClass]. Different `Rt_ValueClass` implementations use different
 * hashCode strategies (defIndex vs name.hashCode() vs identity), so deriving the value hash
 * from `type.hashCode()` would break HashMap/HashSet semantics for values built via different
 * construction routes (e.g. a struct produced by the compiler vs by a stdlib factory).
 *
 * Regression: see commit cb938c6a9 (Rt_Value sealed-hierarchy refactor).
 */
class RtValueHashContractTest {
    private class FakeStructTypeByDefIndex(val defIndex: Int, override val name: String): Rt_ValueClass<Rt_StructValue> {
        override val klass: KClass<Rt_StructValue> = Rt_StructValue::class
        override fun equals(other: Any?) = other is FakeStructTypeByDefIndex && defIndex == other.defIndex
        override fun hashCode() = defIndex
    }

    private class FakeStructTypeByName(override val name: String): Rt_ValueClass<Rt_StructValue> {
        override val klass: KClass<Rt_StructValue> = Rt_StructValue::class
        override fun equals(other: Any?) = other is FakeStructTypeByName && name == other.name
        override fun hashCode() = name.hashCode()
    }

    @Test fun structValueHashIsConsistentAcrossTypeClassRoutes() {
        val typeByDefIndex = FakeStructTypeByDefIndex(defIndex = 42, name = "shared")
        val typeByName = FakeStructTypeByName(name = "shared")

        // Sanity: the two Rt_ValueClass instances disagree on hashCode but have the same name.
        assertEquals(typeByDefIndex.name, typeByName.name)
        assertTrue(typeByDefIndex.hashCode() != typeByName.hashCode())

        val attrs1 = mutableListOf<Rt_Value>(Rt_IntValue.get(1), Rt_IntValue.get(2))
        val attrs2 = mutableListOf<Rt_Value>(Rt_IntValue.get(1), Rt_IntValue.get(2))
        val sv1 = Rt_StructValue(typeByDefIndex, attrs1)
        val sv2 = Rt_StructValue(typeByName, attrs2)

        assertEquals(sv1, sv2, "Equal-by-attributes structs should be equal")
        assertEquals(sv1.hashCode(), sv2.hashCode(), "hashCode must be stable across Rt_ValueClass routes")

        val set = hashSetOf<Rt_Value>(sv1)
        assertTrue(sv2 in set, "HashSet.contains must hold across routes")

        val map = hashMapOf<Rt_Value, String>(sv1 to "found")
        assertEquals("found", map[sv2], "HashMap.get must hold across routes")
    }

    @Test fun virtualStructValueHashIsConsistentAcrossTypeClassRoutes() {
        val typeByDefIndex = object: Rt_ValueClass<Rt_VirtualStructValue> {
            override val name = "virtual<shared>"
            override val klass: KClass<Rt_VirtualStructValue> = Rt_VirtualStructValue::class
            override fun equals(other: Any?) = other === this
            override fun hashCode() = 7
        }
        val typeByName = object: Rt_ValueClass<Rt_VirtualStructValue> {
            override val name = "virtual<shared>"
            override val klass: KClass<Rt_VirtualStructValue> = Rt_VirtualStructValue::class
            override fun equals(other: Any?) = other === this
            override fun hashCode() = name.hashCode()
        }
        val innerType = object: Rt_ValueClass<Rt_StructValue> {
            override val name = "shared"
            override val klass: KClass<Rt_StructValue> = Rt_StructValue::class
            override fun equals(other: Any?) = other === this
            override fun hashCode() = 0
        }

        assertTrue(typeByDefIndex.hashCode() != typeByName.hashCode())

        val attrNames = listOf("a", "b")
        val attrs1: List<Rt_Value?> = listOf(Rt_IntValue.get(1), Rt_IntValue.get(2))
        val attrs2: List<Rt_Value?> = listOf(Rt_IntValue.get(1), Rt_IntValue.get(2))
        val gtv = net.postchain.gtv.GtvFactory.gtv(net.postchain.gtv.GtvNull)

        val v1 = Rt_VirtualStructValue(gtv, typeByDefIndex, innerType, "shared", attrNames, attrs1)
        val v2 = Rt_VirtualStructValue(gtv, typeByName, innerType, "shared", attrNames, attrs2)

        assertEquals(v1, v2)
        assertEquals(v1.hashCode(), v2.hashCode(), "virtual struct hashCode must be stable across routes")
    }
}
