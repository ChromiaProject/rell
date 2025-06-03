/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

sealed class M_Type_Param(val param: M_TypeParam): M_Type() {
    companion object {
        fun make(param: M_TypeParam): M_Type = M_Type_Param_Internal(param)
    }
}

private class M_Type_Param_Internal(param: M_TypeParam): M_Type_Param(param) {
    override fun strCode(): String = param.name
    override fun hashCode0() = param.hashCode()

    override fun matchTypeEqual0(type: M_Type, handler: M_TypeMatchEqualHandler): Boolean {
        return type is M_Type_Param_Internal && param == type.param
    }

    override fun matchTypeSuper0(type: M_Type, handler: M_TypeMatchSuperHandler): Boolean {
        val lower = param.bounds.getSubType()
        return lower != null && handler.handle(lower, type, M_TypeMatchSuperRelation.SUPER)
    }

    override fun getTypeParams0(res: MutableSet<M_TypeParam>) {
        res.add(param)
    }

    override fun replaceParams(map: Map<M_TypeParam, M_TypeSet>, capture: Boolean): M_TypeRep {
        val set = map[param]
        set ?: return M_TypeRep_Type(this)
        return if (capture) {
            val type = set.captureType()
            M_TypeRep_Type(type)
        } else {
            M_TypeRep_TypeSet(set)
        }
    }

    override fun expandCaptures(mode: M_TypeExpandMode): M_Type = this
    override fun captureWildcards(): M_Type = this

    override fun matchTypeParams0(
        type: M_Type,
        rel: M_TypeMatchRelation,
        handler: M_TypeParamMatchHandler
    ): Boolean {
        handler.accept(param, type, rel)
        return true
    }

    override fun validate() {
        param.validate()
    }
}
