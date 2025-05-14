/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import net.postchain.rell.base.utils.mapIndexedToImmList
import net.postchain.rell.base.utils.mapNotNullAllOrNull
import java.util.*

sealed class M_Type_Composite(private val componentCount: Int): M_Type() {
    protected abstract val canonicalArgs: List<M_TypeSet>

    private val varianceArgs: List<M_TypeSet> by lazy {
        canonicalArgs.mapIndexedToImmList { i, arg ->
            val variance = getTypeArgVariance(i)
            when (variance) {
                M_TypeVariance.NONE -> arg
                M_TypeVariance.IN -> arg.allSuperTypes()
                M_TypeVariance.OUT -> arg.allSubTypes()
            }
        }
    }

    protected abstract fun equalsComposite0(other: M_Type_Composite): Boolean
    protected abstract fun hashCodeComposite0(): Int

    final override fun hashCode0(): Int {
        return Objects.hash(canonicalArgs, hashCodeComposite0())
    }

    protected abstract fun getTypeArgVariance(index: Int): M_TypeVariance

    final override fun matchTypeEqual0(type: M_Type, handler: M_TypeMatchEqualHandler): Boolean {
        if (type !is M_Type_Composite || canonicalArgs.size != type.canonicalArgs.size || !equalsComposite0(type)) {
            return false
        }

        return canonicalArgs.indices.all { i ->
            val arg1 = canonicalArgs[i]
            val arg2 = type.canonicalArgs[i]
            arg1.matchTypeEqual(arg2, handler)
        }
    }

    override fun matchTypeSuper0(type: M_Type, handler: M_TypeMatchSuperHandler): Boolean {
        if (type !is M_Type_Composite) return false
        val otherType = type.getCorrespondingSuperType(this)
        otherType ?: return false
        return matchTypeSuperBase(otherType, handler)
    }

    protected fun matchTypeSuperBase(type: M_Type_Composite, handler: M_TypeMatchSuperHandler): Boolean {
        if (type.varianceArgs.size != varianceArgs.size) {
            return false
        }

        return varianceArgs.indices.all { i ->
            val arg1 = varianceArgs[i]
            val arg2 = type.varianceArgs[i]
            arg1.matchTypeSuper(arg2, handler)
        }
    }

    protected abstract fun newInstance(newArgs: List<M_TypeSet>): M_Type_Composite
    protected abstract fun getCorrespondingSuperType(otherType: M_Type_Composite): M_Type_Composite?

    // Non-final on purpose.
    override fun getCommonSuperType0(type: M_Type): M_Type? {
        return getCommonType0(type, false)
    }

    final override fun getCommonSubType0(type: M_Type): M_Type? {
        return getCommonType0(type, true)
    }

    private fun getCommonType0(type: M_Type, sub: Boolean): M_Type? {
        if (type !is M_Type_Composite || componentCount != type.componentCount || !equalsComposite0(type)) {
            return null
        }

        val resArgs = varianceArgs.indices.mapNotNullAllOrNull { i ->
            val arg1 = varianceArgs[i]
            val arg2 = type.varianceArgs[i]
            if (sub) arg1.getCommonSubSet(arg2) else arg1.getCommonSuperSet(arg2)
        }
        resArgs ?: return null

        return newInstance(resArgs)
    }

    final override fun getTypeParams0(res: MutableSet<M_TypeParam>) {
        for (arg in canonicalArgs) {
            arg.getTypeParams0(res)
        }
    }

    final override fun replaceParams(map: Map<M_TypeParam, M_TypeSet>, capture: Boolean): M_TypeRep {
        val resArgs = canonicalArgs.map { it.replaceParams(map, capture) }
        val resType = if (resArgs.indices.all { resArgs[it] === canonicalArgs[it] }) this else newInstance(resArgs)
        return M_TypeRep_Type(resType)
    }

    final override fun expandCaptures(mode: M_TypeExpandMode): M_Type? {
        val resArgs = varianceArgs.mapNotNullAllOrNull { set ->
            val expSet = set.expandCaptures(mode)
            expSet ?: when (mode) {
                M_TypeExpandMode.SUPER -> M_TypeSets.ALL
                M_TypeExpandMode.SUB -> null
            }
        }

        return when {
            resArgs == null -> null
            resArgs.indices.all { resArgs[it] === varianceArgs[it] } -> this
            else -> newInstance(resArgs)
        }
    }
}
