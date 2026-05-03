/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.compiler.base.lib.C_LibUtils
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.*
import net.postchain.rell.base.runtime.createComparator
import net.postchain.rell.base.runtime.rTypeToRRType

object Lib_Types {
    init {
        // Register member providers — these only register lambdas, no Lib_Rell.X_TYPE access.
        R_LibTypeMemberRegistry.registerValueMembers(R_EntityType::class) { type ->
            lazy { Lib_Type_Entity.getValueMembers(type) }
        }
        R_LibTypeMemberRegistry.registerValueMembers(R_TupleType::class) { type ->
            lazy { Lib_Type_Tuple.getValueMembers(type) }
        }
        R_LibTypeMemberRegistry.registerValueMembers(R_StructType::class) { type ->
            lazy { Lib_Type_Struct.getValueMembers(type.struct) }
        }
        R_LibTypeMemberRegistry.registerValueMembers(R_ObjectType::class) { type ->
            lazy { Lib_Type_Object.getMemberValues(type) }
        }
        R_LibTypeMemberRegistry.registerValueMembers(R_VirtualStructType::class) { type ->
            lazy { Lib_Type_VirtualStruct.getValueMembers(type) }
        }
        R_LibTypeMemberRegistry.registerValueMembers(R_VirtualTupleType::class) { type ->
            lazy { Lib_Type_VirtualTuple.getValueMembers(type) }
        }
        R_LibTypeMemberRegistry.registerStaticMembers(R_EnumType::class) { type ->
            Lib_Type_Enum.getStaticMembers(type)
        }
    }

    /** Called from [Lib_Rell] companion init AFTER all type defs are constructed. */
    fun registerLibTypeDefs() {
        R_LibUniqueType.registerLibTypeDef(net.postchain.rell.base.lib.R_RellMetaType, Lib_Rell.RELL_META_TYPE)
        R_LibUniqueType.registerLibTypeDef(net.postchain.rell.base.lib.R_TimeFormatType, Lib_Rell.RELL_TIME_FORMAT_TYPE)
        R_LibUniqueType.registerLibTypeDef(R_IntegerType, Lib_Rell.INTEGER_TYPE)
        R_LibUniqueType.registerLibTypeDef(R_BigIntegerType, Lib_Rell.BIG_INTEGER_TYPE)
        R_LibUniqueType.registerLibTypeDef(R_DecimalType, Lib_Rell.DECIMAL_TYPE)
        R_LibUniqueType.registerLibTypeDef(R_TextType, Lib_Rell.TEXT_TYPE)
        R_LibUniqueType.registerLibTypeDef(R_ByteArrayType, Lib_Rell.BYTE_ARRAY_TYPE)
        R_LibUniqueType.registerLibTypeDef(R_BooleanType, Lib_Rell.BOOLEAN_TYPE)
        R_LibUniqueType.registerLibTypeDef(R_RowidType, Lib_Rell.ROWID_TYPE)
        R_LibUniqueType.registerLibTypeDef(R_RangeType, Lib_Rell.RANGE_TYPE)
        R_LibUniqueType.registerLibTypeDef(R_JsonType, Lib_Rell.JSON_TYPE)
        R_LibUniqueType.registerLibTypeDef(R_GtvType, Lib_Rell.GTV_TYPE)
        R_LibUniqueType.registerLibTypeDef(R_UnitType, Lib_Rell.UNIT_TYPE)
        R_LibUniqueType.registerLibTypeDef(R_SignerType, Lib_Rell.SIGNER_TYPE)
        R_LibUniqueType.registerLibTypeDef(R_GUIDType, Lib_Rell.GUID_TYPE)
        R_LibUniqueType.registerLibTypeDef(R_RellErrorType, Lib_Rell.RELL_ERROR_TYPE)
        // Generic types
        R_LibUniqueType.registerGenericLibTypeDef(R_ListType::class, Lib_Rell.LIST_TYPE)
        R_LibUniqueType.registerGenericLibTypeDef(R_SetType::class, Lib_Rell.SET_TYPE)
        R_LibUniqueType.registerGenericLibTypeDef(R_MapType::class, Lib_Rell.MAP_TYPE)
        // Virtual types
        R_LibUniqueType.registerGenericLibTypeDef(R_VirtualStructType::class, Lib_Rell.VIRTUAL_TYPE)
        R_LibUniqueType.registerGenericLibTypeDef(R_VirtualTupleType::class, Lib_Rell.VIRTUAL_TYPE)
        R_LibUniqueType.registerGenericLibTypeDef(R_VirtualListType::class, Lib_Rell.VIRTUAL_LIST_TYPE)
        R_LibUniqueType.registerGenericLibTypeDef(R_VirtualSetType::class, Lib_Rell.VIRTUAL_SET_TYPE)
        R_LibUniqueType.registerGenericLibTypeDef(R_VirtualMapType::class, Lib_Rell.VIRTUAL_MAP_TYPE)
        // Mirror struct type defs (require Lib_Rell.X_TYPE so they belong here)
        R_LibTypeMemberRegistry.registerTypeDef(R_StructType.IMMUTABLE_MIRROR_STRUCT_KEY, Lib_Rell.IMMUTABLE_MIRROR_STRUCT)
        R_LibTypeMemberRegistry.registerTypeDef(R_StructType.MUTABLE_MIRROR_STRUCT_KEY, Lib_Rell.MUTABLE_MIRROR_STRUCT)
    }

    val NAMESPACE = Ld_NamespaceDsl.make {
        type("immutable", abstract = true, hidden = true, since = "0.13.2") {
            supertypeStrategySpecial { mType ->
                C_LibUtils.isImmutableType(mType)
            }
        }

        type("comparable", abstract = true, hidden = true, since = "0.13.2") {
            supertypeStrategySpecial { mType ->
                val rType = L_TypeUtils.getRTypeOrNull(mType)
                rType != null && createComparator(rTypeToRRType(rType)) != null
            }
        }

        include(Lib_Type_Unit.NAMESPACE)
        include(Lib_Type_Boolean.NAMESPACE)
        include(Lib_Type_Integer.NAMESPACE)
        include(Lib_Type_BigInteger.NAMESPACE)
        include(Lib_Type_Decimal.NAMESPACE)
        include(Lib_Type_Text.NAMESPACE)
        include(Lib_Type_ByteArray.NAMESPACE)
        include(Lib_Type_Rowid.NAMESPACE)
        include(Lib_Type_Json.NAMESPACE)
        include(Lib_Type_Range.NAMESPACE)
        include(Lib_Type_Gtv.NAMESPACE)
        include(Lib_Type_Signer.NAMESPACE)
        include(Lib_Type_Guid.NAMESPACE)

        include(Lib_Type_Iterable.NAMESPACE)
        include(Lib_Type_Collection.NAMESPACE)
        include(Lib_Type_List.NAMESPACE)
        include(Lib_Type_Set.NAMESPACE)
        include(Lib_Type_Map.NAMESPACE)

        include(Lib_Type_Virtual.NAMESPACE)
        include(Lib_Type_VirtualCollection.NAMESPACE)
        include(Lib_Type_VirtualList.NAMESPACE)
        include(Lib_Type_VirtualSet.NAMESPACE)
        include(Lib_Type_VirtualMap.NAMESPACE)

        include(Lib_Type_Enum.NAMESPACE)
        include(Lib_Type_Struct.NAMESPACE)
        include(Lib_Type_Entity.NAMESPACE)
        include(Lib_Type_Object.NAMESPACE)
        include(Lib_Type_Operation.NAMESPACE)
        include(Lib_Type_Null.NAMESPACE)
    }
}
