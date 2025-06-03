/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.lib.type.Lib_Type_Object
import net.postchain.rell.base.runtime.GtvRtConversion
import net.postchain.rell.base.runtime.GtvRtConversion_None
import net.postchain.rell.base.runtime.Rt_CoreValueTypes
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.toGtv
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.doc.DocCode

class R_ObjectType(val rObject: R_ObjectDefinition): R_Type(rObject.appLevelName, rObject.cDefName) {
    init {
        checkEquals(rObject.type, null) // during initialization
    }

    override fun equals0(other: R_Type): Boolean = other is R_ObjectType && other.rObject == rObject
    override fun hashCode0(): Int = rObject.hashCode()

    override fun isDirectVirtualable() = false
    override fun isDirectPure() = false
    override fun isCacheable() = true
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_None
    override fun strCode(): String = name
    override fun toMetaGtv() = rObject.appLevelName.toGtv()

    override fun getLibType0() = C_LibType.make(
        this,
        DocCode.link(rObject.moduleLevelName),
        valueMembers = lazy { Lib_Type_Object.getMemberValues(this) },
    )
}

class Rt_ObjectValue(private val type: R_ObjectType): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.OBJECT.type()

    override fun type() = type
    override fun strCode(showTupleFieldNames: Boolean) = type.name
    override fun str(format: StrFormat) = type.name
}
