/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.mapToImmList

object M_FunctionTypeUtils {
    fun makeType(resultType: M_Type, paramTypes: ImmList<M_Type>): M_Type {
        return M_Type_Function_Internal(resultType, paramTypes)
    }
}

sealed class M_Type_Function(
    val resultType: M_Type,
    val paramTypes: ImmList<M_Type>,
): M_Type_Composite(paramTypes.size + 1)

private class M_Type_Function_Internal(
    resultType: M_Type,
    paramTypes: ImmList<M_Type>,
): M_Type_Function(resultType, paramTypes) {
    override val canonicalArgs: ImmList<M_TypeSet> = (listOf(resultType) + paramTypes).mapToImmList { M_TypeSets.one(it) }

    override fun strCode(): String {
        val paramsStr = paramTypes.joinToString(",", "(", ")") { it.strCode() }
        return "$paramsStr->${resultType.strCode()}"
    }

    override fun equalsComposite0(other: M_Type_Composite): Boolean = other is M_Type_Function_Internal
    override fun hashCodeComposite0() = 0

    override fun getTypeArgVariance(index: Int): M_TypeVariance {
        return if (index == 0) M_TypeVariance.OUT else M_TypeVariance.IN
    }

    override fun captureWildcards(): M_Type = this

    override fun newInstance(newArgs: List<M_TypeSet>): M_Type_Composite {
        check(newArgs.isNotEmpty())
        val newResultType = newArgs[0].canonicalOutType()
        val newParamTypes = newArgs.drop(1).mapToImmList { it.canonicalInType() }
        return M_Type_Function_Internal(newResultType, newParamTypes)
    }

    override fun getCorrespondingSuperType(otherType: M_Type_Composite): M_Type_Composite? {
        return if (otherType is M_Type_Function_Internal) this else null
    }

    override fun validate() {
        resultType.validate()
        for (paramType in paramTypes) {
            paramType.validate()
        }
    }
}
