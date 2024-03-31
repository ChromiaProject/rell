/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.compiler.base.lib.C_LibUtils
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl

object Lib_Types {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("immutable", abstract = true, hidden = true, since = "0.13.2") {
            supertypeStrategySpecial { mType ->
                C_LibUtils.isImmutableType(mType)
            }
        }

        type("comparable", abstract = true, hidden = true, since = "0.13.2") {
            supertypeStrategySpecial { mType ->
                val rType = L_TypeUtils.getRType(mType)
                rType?.comparator() != null
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
