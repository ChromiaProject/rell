/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle.values

import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.Rt_BooleanValue
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_StructValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.Rt_ValueClass
import net.postchain.rell.base.runtime.asBoolean
import net.postchain.rell.base.runtime.asInteger
import net.postchain.rell.base.runtime.checkAttrSizeConstraint
import net.postchain.rell.base.runtime.truffle.Tf_Unchecked

/**
 * Truffle-backed struct leaf produced via the Static Object Model. The generated final classes
 * (one per Rell struct type) extend [Tf_DynStruct] and inherit its three constructor parameters
 * — [rrType] (for default-value/validator lookup), [type] (the per-Rell-type [Rt_ValueClass]),
 * and [shape] (the [Tf_StructShape] that owns the SOM
 * [com.oracle.truffle.api.staticobject.StaticShape] + per-attribute
 * [com.oracle.truffle.api.staticobject.StaticProperty] handles).
 *
 * Instances are not constructed directly — go through [Tf_StructShape.newInstance] which routes
 * through the SOM factory's `create` method to materialise a generated subclass instance.
 *
 * Layout-vs-capability separation: get/set route through [Rt_StructValue]'s abstract API. The
 * [shape]'s StaticProperty handles are read in O(1) by attribute index. Per-attribute slot kind
 * is recorded in [Tf_StructShape.slotKinds]: non-nullable `integer` / `boolean` attributes use
 * primitive `long` / `boolean` slots and box only at this API boundary (when the caller asks for
 * an `Rt_Value`); other attributes stay on `Object` slots. The typed read/write helpers
 * ([getLong], [setLong], [getBoolean], [setBoolean]) skip the box altogether and are the
 * primary callers from `Tf_MemberAccessNode$StructAttr$IntAttr` and `Tf_StructCreateNode`.
 */
open class Tf_DynStruct(
    val rrType: RR_Type.Struct,
    override val type: Rt_ValueClass<*>,
    val shape: Tf_StructShape,
): Rt_StructValue() {
    final override fun size(): Int = shape.properties.size

    final override fun get(index: Int): Rt_Value {
        val prop = shape.properties[index]
        return when (shape.slotKindAt(index)) {
            TF_SLOT_LONG -> Rt_IntValue.get(prop.getLong(this))
            TF_SLOT_BOOLEAN -> Rt_BooleanValue.get(prop.getBoolean(this))
            else -> Tf_Unchecked.cast(prop.getObject(this))
        }
    }

    final override fun set(index: Int, value: Rt_Value) {
        // Mirror Rt_HeapStruct.set: per-attribute validators (e.g. text/byte_array size
        // constraints) must run on every external write, not just the assignTo path that
        // the interpreter happens to re-validate. Without this, a direct
        // Rt_StructValue.set(...) on a Truffle-backed struct silently bypasses constraints
        // that the heap-backed struct would have caught.
        shape.sizeConstraints[index]?.let { checkAttrSizeConstraint(it, value) }
        val prop = shape.properties[index]
        when (shape.slotKindAt(index)) {
            TF_SLOT_LONG -> prop.setLong(this, value.asInteger())
            TF_SLOT_BOOLEAN -> prop.setBoolean(this, value.asBoolean())
            else -> prop.setObject(this, value)
        }
    }

    /**
     * Typed primitive read for `integer` slots. The caller is responsible for verifying the
     * slot is a `long` slot (`shape.slotKindAt(index) == TF_SLOT_LONG`); on mismatch the SOM
     * runtime throws `IllegalStateException` from `getLong` against an `Object` slot.
     */
    fun getLong(index: Int): Long = shape.properties[index].getLong(this)

    /** Typed primitive read for `boolean` slots. See [getLong] for the slot-kind contract. */
    fun getBoolean(index: Int): Boolean = shape.properties[index].getBoolean(this)

    /** Typed primitive write for `integer` slots — skips the [Rt_IntValue] box. */
    fun setLong(index: Int, value: Long) {
        shape.properties[index].setLong(this, value)
    }

    /** Typed primitive write for `boolean` slots — skips the [Rt_BooleanValue] box. */
    fun setBoolean(index: Int, value: Boolean) {
        shape.properties[index].setBoolean(this, value)
    }

    final override val effectiveNamesOrNull: List<String>
        get() = shape.attrNames
}
