/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

sealed class M_Type_Capture(val set: M_TypeSet_Many): M_Type() {
    companion object {
        fun make(set: M_TypeSet_Many): M_Type = M_Type_Capture_Internal(set)
    }
}

private class M_Type_Capture_Internal(set: M_TypeSet_Many): M_Type_Capture(set) {
    override fun strCode() = "CAP<${set.strCode()}>"
    override fun hashCode0() = System.identityHashCode(this)

    override fun matchTypeEqual0(type: M_Type, handler: M_TypeMatchEqualHandler) = false

    override fun matchTypeSuper0(type: M_Type, handler: M_TypeMatchSuperHandler): Boolean {
        val lower = set.getSubType()
        return lower != null && handler.handle(lower, type, M_TypeMatchSuperRelation.SUPER)
    }

    override fun getCommonSuperType0(type: M_Type): M_Type? {
        val top = set.getSuperType()
        return top?.getCommonSuperType(type)
    }

    override fun getCommonSubType0(type: M_Type): M_Type? {
        val sub = set.getSubType()
        return sub?.getCommonSubType(type)
    }

    override fun getTypeParams0(res: MutableSet<M_TypeParam>) {
        set.getTypeParams0(res)
    }

    override fun replaceParams(map: Map<M_TypeParam, M_TypeSet>, capture: Boolean): M_TypeRep {
        val set2 = set.replaceParams(map, capture)
        val res = if (set2 === set) this else set2.captureType()
        return M_TypeRep_Type(res)
    }

    override fun expandCaptures(mode: M_TypeExpandMode): M_Type? = when (mode) {
        M_TypeExpandMode.SUPER -> set.getSuperType()
        M_TypeExpandMode.SUB -> set.getSubType()
    }

    override fun captureWildcards(): M_Type = this
    override fun getCapturedSet() = set

    override fun validate() {
        set.validate()
    }
}
