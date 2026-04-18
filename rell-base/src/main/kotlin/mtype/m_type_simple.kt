/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

/** Simple type - a type that does not have inner type components, shall have a single [M_Type] instance, but may have
 * multiple value instances. */
abstract class M_Type_Simple(private val name: String): M_Type() {
    private val typeRep: M_TypeRep = M_TypeRep_Type(this)

    final override fun strCode() = name
    final override fun hashCode0() = System.identityHashCode(this)
    final override fun matchTypeEqual0(type: M_Type, handler: M_TypeMatchEqualHandler) = false

    final override fun getTypeParams0(res: MutableSet<M_TypeParam>) = Unit
    final override fun replaceParams(map: Map<M_TypeParam, M_TypeSet>, capture: Boolean): M_TypeRep = typeRep
    final override fun expandCaptures(mode: M_TypeExpandMode): M_Type = this
    final override fun captureWildcards(): M_Type = this

    final override fun validate() = Unit

    internal companion object {
        val ANYTHING: M_Type = M_Type_Anything
        val NOTHING: M_Type = M_Type_Nothing
        val ANY: M_Type = M_Type_Any
    }
}

private object M_Type_Nothing: M_Type_Simple("nothing") {
    override fun matchTypeSuper0(type: M_Type, handler: M_TypeMatchSuperHandler) = false
}

private object M_Type_Anything: M_Type_Simple("anything") {
    override fun matchTypeSuper0(type: M_Type, handler: M_TypeMatchSuperHandler) = true
}

private object M_Type_Any: M_Type_Simple("any") {
    override fun matchTypeSuper0(type: M_Type, handler: M_TypeMatchSuperHandler): Boolean {
        return when (type) {
            M_Types.ANYTHING, M_Types.NULL -> false
            is M_Type_Nullable -> false
            is M_Type_Capture -> {
                val top = type.set.getSuperType()
                top != null && handler.handle(this, top, M_TypeMatchSuperRelation.SUPER)
            }
            is M_Type_Param -> {
                val top = type.param.bounds.getSuperType()
                top != null && handler.handle(this, top, M_TypeMatchSuperRelation.SUPER)
            }
            else -> true
        }
    }
}
