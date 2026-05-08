/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.lib.R_RellMetaType
import net.postchain.rell.base.model.R_DefinitionMeta

data class Rt_RellMetaValue(private val meta: R_DefinitionMeta): Rt_Value {
    val kindText: Rt_Value by lazy { Rt_TextValue.get(meta.kind) }
    val simpleName: Rt_Value by lazy { Rt_TextValue.get(meta.simpleName) }
    val moduleName: Rt_Value by lazy { Rt_TextValue.get(meta.moduleName) }
    val fullName: Rt_Value by lazy { Rt_TextValue.get(meta.fullName) }
    val mountName: Rt_Value by lazy { Rt_TextValue.get(meta.mountName.str()) }

    override val name
        get() = Companion.name

    override val type
        get() = Companion

    override fun str(format: Rt_StrFormat) = "meta[${meta.fullName}]"
    override fun strCode(showTupleFieldNames: Boolean) = "${R_RellMetaType.name}[${meta.fullName}]"

    companion object: Rt_ValueClass<Rt_RellMetaValue> {
        override val name
            get() = "rell.meta"

        override val klass = Rt_RellMetaValue::class

        fun get(v: Rt_Value): Rt_RellMetaValue = cast(v)
    }
}
