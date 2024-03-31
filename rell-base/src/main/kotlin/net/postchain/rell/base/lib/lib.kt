/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.compiler.base.lib.C_LibNamespace
import net.postchain.rell.base.compiler.base.namespace.C_SysNsProto
import net.postchain.rell.base.lib.test.Lib_RellTest
import net.postchain.rell.base.lib.type.Lib_Types
import net.postchain.rell.base.lib.type.R_ListType
import net.postchain.rell.base.model.R_StructType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.utils.toImmList

object Lib_Rell {
    val MODULE = C_LibModule.make("rell") {
        include(Lib_Types.NAMESPACE)

        include(Lib_Exists.NAMESPACE)
        include(Lib_Math.NAMESPACE)
        include(Lib_Meta.NAMESPACE)
        include(Lib_Print.NAMESPACE)
        include(Lib_Require.NAMESPACE)
        include(Lib_Crypto.NAMESPACE)
        include(Lib_TryCall.NAMESPACE)

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
    val UNIT_TYPE = MODULE.getTypeDef("unit")
    val ITERABLE_TYPE = MODULE.getTypeDef("iterable")
    val LIST_TYPE = MODULE.getTypeDef("list")
    val SET_TYPE = MODULE.getTypeDef("set")
    val MAP_TYPE = MODULE.getTypeDef("map")
    val VIRTUAL_TYPE = MODULE.getTypeDef("virtual")
    val VIRTUAL_LIST_TYPE = MODULE.getTypeDef("virtual_list")
    val VIRTUAL_SET_TYPE = MODULE.getTypeDef("virtual_set")
    val VIRTUAL_MAP_TYPE = MODULE.getTypeDef("virtual_map")

    val NULL_EXTENSION_TYPE = MODULE.getTypeDef("null_ext")

    val IMMUTABLE_MIRROR_STRUCT = MODULE.getTypeDef("immutable_mirror_struct")
    val MUTABLE_MIRROR_STRUCT = MODULE.getTypeDef("mutable_mirror_struct")

    val RELL_META_TYPE = MODULE.getTypeDef("rell.meta")

    // Doesn't belong here logically, but shall be here for explicit initialization.
    private val GTX_OPERATION_STRUCT = MODULE.lModule.getStruct("gtx_operation").rStruct
    val GTX_OPERATION_STRUCT_TYPE: R_StructType get() = GTX_OPERATION_STRUCT.type
    val OP_CONTEXT_GET_ALL_OPERATIONS_RETURN_TYPE: R_Type = R_ListType(GTX_OPERATION_STRUCT_TYPE)
}

private data class C_SysLibConfig(
    val testLib: Boolean,
    val hiddenLib: Boolean,
    val extraMod: C_LibModule?,
)

class C_SysLibScope(
    val nsProto: C_SysNsProto,
    val modules: List<C_LibModule>,
)

object C_SystemLibrary {
    private val CACHE = mutableMapOf<C_SysLibConfig, C_SysLibScope>()

    fun getScope(testLib: Boolean, hiddenLib: Boolean, extraMod: C_LibModule?): C_SysLibScope {
        val cfg = C_SysLibConfig(testLib = testLib, hiddenLib = hiddenLib, extraMod = extraMod)
        return synchronized(this) {
            CACHE.computeIfAbsent(cfg) {
                createScope(cfg)
            }
        }
    }

    private fun createScope(cfg: C_SysLibConfig): C_SysLibScope {
        val modules = mutableListOf<C_LibModule>()

        modules.add(Lib_Rell.MODULE)

        if (cfg.hiddenLib) {
            modules.add(Lib_RellHidden.MODULE)
        }

        if (cfg.testLib) {
            modules.add(Lib_RellTest.MODULE)
        }

        if (cfg.extraMod != null) {
            modules.add(cfg.extraMod)
        }

        val namespaces = modules.map { it.namespace }
        val combinedNamespace = C_LibNamespace.merge(namespaces)

        val nsProto = combinedNamespace.toSysNsProto()
        return C_SysLibScope(nsProto, modules.toImmList())
    }
}
