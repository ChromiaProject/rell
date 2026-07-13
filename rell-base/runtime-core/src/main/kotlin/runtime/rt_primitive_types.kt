/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type

object Rt_PrimitiveTypes {
    init {
        // Suppress jOOQ banner / tips. Must run before any `org.jooq.*` class loads — the
        // earlier `init` block in `SqlGen` was too late because primitive value-class
        // companions (which advertise `org.jooq.DataType` typed properties) trigger jOOQ
        // class-loading via `Rt_PrimitiveTypes.<clinit>`, before `SqlGen` is touched.
        System.setProperty("org.jooq.no-logo", "true")
        System.setProperty("org.jooq.no-tips", "true")
    }

    val BOOLEAN: Rt_ValueClass<*> = Rt_BooleanValue
    val INTEGER: Rt_ValueClass<*> = Rt_IntValue
    val BIG_INTEGER: Rt_ValueClass<*> = Rt_BigIntegerValue
    val DECIMAL: Rt_ValueClass<*> = Rt_DecimalValue
    val TEXT: Rt_ValueClass<*> = Rt_TextValue
    val BYTE_ARRAY: Rt_ValueClass<*> = Rt_ByteArrayValue
    val ROWID: Rt_ValueClass<*> = Rt_RowidValue
    val JSON: Rt_ValueClass<*> = Rt_JsonValue
    val GTV: Rt_ValueClass<*> = Rt_GtvValue
    val RANGE: Rt_ValueClass<*> = Rt_RangeValue
}

/**
 * Companion [Rt_ValueClass] for a primitive kind, when one exists. `guid` and `signer`
 * have no value-class companion (no `Rt_*Value` class is sealed to them), so this returns null.
 */
fun primitiveValueClass(kind: RR_PrimitiveKind): Rt_ValueClass<*>? = when (kind) {
    RR_PrimitiveKind.BOOLEAN -> Rt_BooleanValue
    RR_PrimitiveKind.INTEGER -> Rt_IntValue
    RR_PrimitiveKind.BIG_INTEGER -> Rt_BigIntegerValue
    RR_PrimitiveKind.DECIMAL -> Rt_DecimalValue
    RR_PrimitiveKind.TEXT -> Rt_TextValue
    RR_PrimitiveKind.BYTE_ARRAY -> Rt_ByteArrayValue
    RR_PrimitiveKind.ROWID -> Rt_RowidValue
    RR_PrimitiveKind.JSON -> Rt_JsonValue
    RR_PrimitiveKind.GTV -> Rt_GtvValue
    RR_PrimitiveKind.RANGE -> Rt_RangeValue
    RR_PrimitiveKind.UNIT -> Rt_UnitValue
    RR_PrimitiveKind.GUID, RR_PrimitiveKind.SIGNER -> null
    RR_PrimitiveKind.NOTHING -> Rt_NothingType
}

/**
 * Runtime stand-in for the bottom type of an always-exiting expression. No value of this type
 * ever exists at runtime - evaluating such an expression escapes the enclosing statement - so
 * the class carries no capabilities; it only lets nothing-typed slots resolve.
 */
object Rt_NothingType: Rt_ValueClass<Rt_Value> {
    override val name get() = "nothing"
    override val rrType: RR_Type = RR_Type.Primitive(RR_PrimitiveKind.NOTHING)
}


