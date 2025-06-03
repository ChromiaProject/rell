/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel

import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.mtype.M_TypeParam
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.toImmList

sealed class L_Type {
    abstract val mType: M_Type

    abstract fun getTypeDefOrNull(): L_TypeDef?

    fun strCode(): String = mType.strCode()

    final override fun toString() = strCode()

    companion object {
        fun make(typeDef: L_TypeDef, vararg args: M_Type): L_Type {
            return make(typeDef, args.toImmList())
        }

        fun make(typeDef: L_TypeDef, args: List<M_Type>): L_Type {
            val args2 = args.toImmList()
            return if (args2.isEmpty()) L_Type_Simple(typeDef) else L_Type_Generic(typeDef, args2)
        }
    }
}

private class L_Type_Simple(private val typeDef: L_TypeDef): L_Type() {
    init {
        checkEquals(typeDef.mGenericType.params.size, 0) {
            "Wrong number of arguments for generic type '${typeDef.qualifiedName}'"
        }
    }

    override val mType: M_Type by lazy {
        typeDef.mGenericType.getType()
    }

    override fun getTypeDefOrNull() = typeDef
}

private class L_Type_Generic(private val typeDef: L_TypeDef, val args: ImmList<M_Type>): L_Type() {
    init {
        checkEquals(args.size, typeDef.mGenericType.params.size)
        check(args.isNotEmpty())
    }

    override val mType: M_Type by lazy {
        typeDef.mGenericType.getTypeSimple(args)
    }

    override fun getTypeDefOrNull() = typeDef
}

class L_TypeParams(val list: ImmList<M_TypeParam>, val map: ImmMap<R_Name, M_Type>)
