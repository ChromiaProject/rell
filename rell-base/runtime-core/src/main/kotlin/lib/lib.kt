/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.compiler.base.core.C_SysLibScope
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.compiler.base.lib.C_LibNamespace
import net.postchain.rell.base.lib.test.Lib_RellTest
import net.postchain.rell.base.lib.type.Lib_Types
import net.postchain.rell.base.model.R_EnumDefinition
import net.postchain.rell.base.model.R_StructType
import net.postchain.rell.base.utils.toImmList

object Lib_Rell {
    // Force-load C_LibBridgeImpl so its <clinit> sets C_LibBridge.instance
    // BEFORE MODULE construction needs it.
    @Suppress("unused")
    private val LOAD_BRIDGE: Any = C_LibBridgeImpl

    val MODULE = C_LibModule.make("rell") {
        include(Lib_Types.NAMESPACE)

        include(Lib_Exists.NAMESPACE)
        include(Lib_Math.NAMESPACE)
        include(Lib_Meta.NAMESPACE)
        include(Lib_Print.NAMESPACE)
        include(Lib_Require.NAMESPACE)
        include(Lib_Crypto.NAMESPACE)
        include(Lib_TryCall.NAMESPACE)
        include(Lib_RellTimeFormat.NAMESPACE)

        include(Lib_ChainContext.NAMESPACE)
        include(Lib_OpContext.NAMESPACE)

        // At least an empty namespace "rell" must be defined.
        namespace("rell", since = "0.6.0") {
        }
    }

    val BIG_INTEGER_TYPE = MODULE.getTypeDef("big_integer")
    val BOOLEAN_TYPE = MODULE.getTypeDef("boolean")
    val BYTE_ARRAY_TYPE = MODULE.getTypeDef("byte_array")
    val DECIMAL_TYPE = MODULE.getTypeDef("decimal")
    val GTV_TYPE = MODULE.getTypeDef("gtv")
    val GUID_TYPE = MODULE.getTypeDef("guid")
    val INTEGER_TYPE = MODULE.getTypeDef("integer")
    val JSON_TYPE = MODULE.getTypeDef("json")
    val RANGE_TYPE = MODULE.getTypeDef("range")
    val ROWID_TYPE = MODULE.getTypeDef("rowid")
    val SIGNER_TYPE = MODULE.getTypeDef("signer")
    val TEXT_TYPE = MODULE.getTypeDef("text")
    val RELL_TIME_FORMAT_TYPE = MODULE.getTypeDef("rell.time.format")
    val UNIT_TYPE = MODULE.getTypeDef("unit")
    val ITERABLE_TYPE = MODULE.getTypeDef("iterable")
    val LIST_TYPE = MODULE.getTypeDef("list")
    val SET_TYPE = MODULE.getTypeDef("set")
    val MAP_TYPE = MODULE.getTypeDef("map")
    val RELL_ERROR_TYPE = MODULE.getTypeDef("rell.error_type")
    val VIRTUAL_TYPE = MODULE.getTypeDef("virtual")
    val VIRTUAL_LIST_TYPE = MODULE.getTypeDef("virtual_list")
    val VIRTUAL_SET_TYPE = MODULE.getTypeDef("virtual_set")
    val VIRTUAL_MAP_TYPE = MODULE.getTypeDef("virtual_map")

    val NULL_EXTENSION_TYPE = MODULE.getTypeDef("null_ext")

    val IMMUTABLE_MIRROR_STRUCT = MODULE.getTypeDef("immutable_mirror_struct")
    val MUTABLE_MIRROR_STRUCT = MODULE.getTypeDef("mutable_mirror_struct")

    val RELL_META_TYPE = MODULE.getTypeDef("rell.meta")

    val TRY_CALL_RESULT_TYPE = MODULE.getTypeDef("try_call_result")

    @JvmField
    val GTV_TYPE_ENUM: R_EnumDefinition = MODULE.lModule.getEnum("gtv_type").rEnum

    // Doesn't belong here logically, but shall be here for explicit initialization.
    private val GTX_OPERATION_STRUCT = MODULE.lModule.getStruct("gtx_operation").rStruct

    val GTX_OPERATION_STRUCT_TYPE: R_StructType
        get() = GTX_OPERATION_STRUCT.type

    init {
        // Register R_Type → L_TypeDef bindings for builtin types AFTER all type defs are constructed.
        // Done from here (not Lib_Types.<clinit>) to avoid the circular init: Lib_Rell.MODULE construction
        // triggers Lib_Types init, which would try to read Lib_Rell.RELL_ERROR_TYPE before it exists.
        Lib_Types.registerLibTypeDefs()
    }
}

internal object C_SystemLibrary {
    private val CACHE = mutableMapOf<Config, C_SysLibScope>()

    fun getScope(config: Config): C_SysLibScope = synchronized(this) {
        CACHE.getOrPut(config) { createScope(config) }
    }

    private fun createScope(cfg: Config): C_SysLibScope {
        val modules = ArrayList<C_LibModule>(4)

        if (cfg.defaultLib) {
            modules += Lib_Rell.MODULE
        }

        if (cfg.hiddenLib) {
            modules += Lib_RellHidden.MODULE
        }

        if (cfg.testLib) {
            modules += Lib_RellTest.MODULE
        }

        if (cfg.extraMod != null) {
            modules += cfg.extraMod
        }

        val combinedNamespace = C_LibNamespace.merge(modules.map { it.namespace })

        val nsProto = combinedNamespace.toSysNsProto()
        return C_SysLibScope(nsProto, modules.toImmList())
    }

    data class Config(
        val defaultLib: Boolean,
        val testLib: Boolean,
        val hiddenLib: Boolean,
        val extraMod: C_LibModule?,
    )
}
