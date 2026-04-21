/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.def.C_GlobalFunction
import net.postchain.rell.base.compiler.base.def.C_StructGlobalFunction
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.lmodel.L_TypeDefDocCodeStrategy
import net.postchain.rell.base.utils.doc.DocCode
import net.postchain.rell.base.utils.doc.DocType
import net.postchain.rell.base.utils.doc.DocTypeSet
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.mapToImmList

class R_StructType(val struct: R_Struct): R_SimpleType(struct.name) {
    override fun equals0(other: R_Type) = false
    override fun hashCode0() = System.identityHashCode(this)

    override fun isReference() = true
    override fun isDirectMutable() = struct.isDirectlyMutable()
    override fun isDirectPure() = true
    override fun completeFlags() = struct.flags.typeFlags

    override fun componentTypes() = struct.attributesList.mapToImmList { it.type }

    override fun getLibType0(): C_LibType {
        val constructorFn: C_GlobalFunction = C_StructGlobalFunction(struct)
        val valueMembers = R_LibTypeMemberRegistry.getValueMembers(this)

        val ms = struct.mirrorStructs
        return if (ms != null) {
            val key = if (struct == ms.immutable) IMMUTABLE_MIRROR_STRUCT_KEY else MUTABLE_MIRROR_STRUCT_KEY
            val typeDef = R_LibTypeMemberRegistry.getTypeDef(key)
            C_LibType.make(typeDef, ms.innerType, constructorFn = constructorFn, valueMembers = valueMembers)
        } else {
            C_LibType.make(this, DocCode.link(struct.name), constructorFn = constructorFn, valueMembers = valueMembers)
        }
    }

    override fun docType(): DocType {
        struct.mirrorStructs ?: return DocType.name(name)

        val docArgs = immListOf(DocTypeSet.one(struct.mirrorStructs.innerType.docType()))
        val strategy = L_TypeDefDocCodeStrategy { argDocs ->
            val b = DocCode.builder()
            b.keyword("struct").raw("<")
            if (struct == struct.mirrorStructs.mutable) {
                b.keyword("mutable").raw(" ")
            }
            b.append(argDocs[0]).raw(">")
            b.build()
        }

        return DocType.generic(strategy, docArgs)
    }

    companion object {
        const val IMMUTABLE_MIRROR_STRUCT_KEY = "immutable_mirror_struct"
        const val MUTABLE_MIRROR_STRUCT_KEY = "mutable_mirror_struct"

        val IMMUTABLE_META = R_TypeMeta.make { t ->
            val ms = when (t) {
                is R_EntityType -> t.rEntity.mirrorStructs
                is R_OperationType -> t.rOperation.mirrorStructs
                else -> null
            }
            ms?.immutable?.type
        }

        val MUTABLE_META = R_TypeMeta.make { t ->
            val ms = when (t) {
                is R_EntityType -> t.rEntity.mirrorStructs
                is R_OperationType -> t.rOperation.mirrorStructs
                else -> null
            }
            ms?.mutable?.type
        }
    }
}
