/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import net.postchain.rell.base.utils.ImmSet
import net.postchain.rell.base.utils.toImmSet
import java.util.*

object M_TypeSets {
    val ALL: M_TypeSet = M_TypeSet_All

    fun one(type: M_Type): M_TypeSet {
        return type.oneTypeSet
    }

    fun superOf(type: M_Type): M_TypeSet {
        return when (type) {
            M_Types.ANYTHING -> one(type)
            M_Types.NOTHING -> ALL
            else -> type.superTypesSet
        }
    }

    fun subOf(type: M_Type): M_TypeSet {
        return when (type) {
            M_Types.ANYTHING -> ALL
            M_Types.NOTHING -> one(type)
            M_Types.NULL -> one(type)
            else -> type.subTypesSet
        }
    }
}

object M_TypeSetUtils {
    fun newOne(type: M_Type): M_TypeSet = M_TypeSet_One_Internal(type)
    fun newSuperOf(type: M_Type): M_TypeSet = M_TypeSet_SuperOf_Internal(type)
    fun newSubOf(type: M_Type): M_TypeSet = M_TypeSet_SubOf_Internal(type)
}

sealed class M_TypeSet {
    abstract fun strCode(): String
    fun strMsg(): String = strCode()

    final override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is M_TypeSet) return false
        return matchTypeEqual(other) { type1, type2 ->
            type1 == type2
        }
    }

    protected abstract fun hashCode0(): Int

    final override fun hashCode(): Int {
        return hashCode0()
    }

    abstract fun matchTypeEqual(typeSet: M_TypeSet, handler: M_TypeMatchEqualHandler): Boolean
    abstract fun matchTypeSuper(typeSet: M_TypeSet, handler: M_TypeMatchSuperHandler): Boolean

    fun isSuperSetOf(other: M_TypeSet): Boolean {
        return matchTypeSuper(other) { type1, type2, rel ->
            M_TypeUtils.matchTypeIsSuperTypeOf(type1, type2, rel)
        }
    }

    protected abstract fun getCommonSuperSet0(typeSet: M_TypeSet): M_TypeSet

    fun getCommonSuperSet(typeSet: M_TypeSet): M_TypeSet {
        val capSet1 = getCapturedSet()
        val capSet2 = typeSet.getCapturedSet()
        return if (capSet1 != null || capSet2 != null) {
            (capSet1 ?: this).getCommonSuperSet(capSet2 ?: typeSet)
        } else {
            getCommonSuperSet0(typeSet)
        }
    }

    protected abstract fun getCommonSubSet0(typeSet: M_TypeSet): M_TypeSet?

    fun getCommonSubSet(typeSet: M_TypeSet): M_TypeSet? {
        val capSet1 = getCapturedSet()
        val capSet2 = typeSet.getCapturedSet()
        return if (capSet1 != null || capSet2 != null) {
            (capSet1 ?: this).getCommonSubSet(capSet2 ?: typeSet)
        } else {
            getCommonSubSet0(typeSet)
        }
    }

    abstract fun containsType(type: M_Type): Boolean
    abstract fun allSuperTypes(): M_TypeSet
    abstract fun allSubTypes(): M_TypeSet
    abstract fun canonicalInType(): M_Type
    abstract fun canonicalOutType(): M_Type
    abstract fun getSubType(): M_Type?
    abstract fun getSuperType(): M_Type?
    open fun getExactType(): M_Type? = null

    abstract fun getTypeParams0(res: MutableSet<M_TypeParam>)

    fun getTypeParams(): ImmSet<M_TypeParam> {
        val set = mutableSetOf<M_TypeParam>()
        getTypeParams0(set)
        return set.toImmSet()
    }

    abstract fun captureType(): M_Type
    abstract fun captureTypeSet(): M_TypeSet
    protected open fun getCapturedSet(): M_TypeSet? = null

    abstract fun replaceParams(map: Map<M_TypeParam, M_TypeSet>, capture: Boolean): M_TypeSet
    abstract fun expandCaptures(mode: M_TypeExpandMode): M_TypeSet?

    /** @throws [M_TypeException] */
    abstract fun validate()

    final override fun toString() = strCode()
}

sealed class M_TypeSet_Many: M_TypeSet() {
    final override fun captureType() = M_Types.capture(this)
    final override fun captureTypeSet(): M_TypeSet = M_TypeSets.one(captureType())
}

object M_TypeSet_All: M_TypeSet_Many() {
    override fun strCode() = "*"

    override fun hashCode0() = javaClass.hashCode()

    override fun matchTypeEqual(typeSet: M_TypeSet, handler: M_TypeMatchEqualHandler) = this === typeSet
    override fun matchTypeSuper(typeSet: M_TypeSet, handler: M_TypeMatchSuperHandler) = true

    override fun getCommonSuperSet0(typeSet: M_TypeSet) = this
    override fun getCommonSubSet0(typeSet: M_TypeSet) = typeSet

    override fun containsType(type: M_Type) = true
    override fun allSuperTypes() = this
    override fun allSubTypes() = this
    override fun canonicalInType() = M_Types.NOTHING
    override fun canonicalOutType() = M_Types.ANYTHING
    override fun getSubType() = null
    override fun getSuperType() = null

    override fun getTypeParams0(res: MutableSet<M_TypeParam>) = Unit
    override fun replaceParams(map: Map<M_TypeParam, M_TypeSet>, capture: Boolean): M_TypeSet = this
    override fun expandCaptures(mode: M_TypeExpandMode) = this

    override fun validate() = Unit
}

sealed class M_TypeSet_One(val type: M_Type): M_TypeSet() {
    override fun strCode() = type.strCode()

    override fun hashCode0() = Objects.hash(javaClass, type)

    override fun matchTypeEqual(typeSet: M_TypeSet, handler: M_TypeMatchEqualHandler): Boolean {
        return typeSet is M_TypeSet_One && handler.handle(type, typeSet.type)
    }

    override fun matchTypeSuper(typeSet: M_TypeSet, handler: M_TypeMatchSuperHandler): Boolean {
        return when (typeSet) {
            M_TypeSet_All -> false
            is M_TypeSet_One -> handler.handle(type, typeSet.type, M_TypeMatchSuperRelation.EQUAL)
            is M_TypeSet_SubOf -> false
            is M_TypeSet_SuperOf -> false
        }
    }

    override fun getCommonSuperSet0(typeSet: M_TypeSet): M_TypeSet {
        return when (typeSet) {
            M_TypeSet_All -> typeSet
            is M_TypeSet_One -> {
                if (type == typeSet.type) {
                    this
                } else {
                    val superType = type.getCommonSuperType(typeSet.type)
                    if (superType != null) M_TypeSets.subOf(superType) else {
                        val subType = type.getCommonSubType(typeSet.type)
                        if (subType != null) M_TypeSets.superOf(subType) else M_TypeSets.ALL
                    }
                }
            }
            is M_TypeSet_SubOf -> M_TypeSetInternals.getCommonSuperSet(this, typeSet)
            is M_TypeSet_SuperOf -> M_TypeSetInternals.getCommonSuperSet(this, typeSet)
        }
    }

    override fun getCommonSubSet0(typeSet: M_TypeSet): M_TypeSet? {
        return if (typeSet.containsType(type)) {
            this
        } else {
            null
        }
    }

    override fun containsType(type: M_Type) = type == this.type
    override fun allSubTypes(): M_TypeSet = M_TypeSets.subOf(type)
    override fun allSuperTypes(): M_TypeSet = M_TypeSets.superOf(type)
    override fun canonicalInType() = type
    override fun canonicalOutType() = type
    override fun getSubType() = type
    override fun getSuperType() = type
    override fun getExactType() = type

    override fun getTypeParams0(res: MutableSet<M_TypeParam>) = type.getTypeParams0(res)
    override fun captureType() = type
    override fun captureTypeSet() = this
    override fun getCapturedSet() = type.getCapturedSet()

    override fun replaceParams(map: Map<M_TypeParam, M_TypeSet>, capture: Boolean): M_TypeSet {
        val typeRep = type.replaceParams(map, capture)
        return if (typeRep.isSameType(type)) this else typeRep.typeSet()
    }

    override fun expandCaptures(mode: M_TypeExpandMode): M_TypeSet? {
        return when (mode) {
            M_TypeExpandMode.SUPER -> {
                val expSuperType = type.expandCaptures(M_TypeExpandMode.SUPER)
                when {
                    expSuperType === type -> this
                    expSuperType != null -> M_TypeSets.subOf(expSuperType)
                    else -> {
                        val expSubType = type.expandCaptures(M_TypeExpandMode.SUB)
                        if (expSubType != null) M_TypeSets.superOf(expSubType) else M_TypeSets.ALL
                    }
                }
            }
            M_TypeExpandMode.SUB -> {
                val expSubType = type.expandCaptures(M_TypeExpandMode.SUB)
                if (expSubType === type) this else null
            }
        }
    }

    override fun validate() {
        type.validate()
    }
}

private class M_TypeSet_One_Internal(type: M_Type): M_TypeSet_One(type)

sealed class M_TypeSet_SubOf(val boundType: M_Type): M_TypeSet_Many() {
    override fun strCode() = "-${boundType.strCode()}"

    override fun hashCode0() = Objects.hash(javaClass, boundType)

    override fun matchTypeEqual(typeSet: M_TypeSet, handler: M_TypeMatchEqualHandler): Boolean {
        return typeSet is M_TypeSet_SubOf && handler.handle(boundType, typeSet.boundType)
    }

    override fun matchTypeSuper(typeSet: M_TypeSet, handler: M_TypeMatchSuperHandler): Boolean {
        return when (typeSet) {
            M_TypeSet_All -> handler.handle(boundType, M_Types.ANYTHING, M_TypeMatchSuperRelation.EQUAL)
            is M_TypeSet_One -> handler.handle(boundType, typeSet.type, M_TypeMatchSuperRelation.SUPER)
            is M_TypeSet_SubOf -> handler.handle(boundType, typeSet.boundType, M_TypeMatchSuperRelation.SUPER)
            is M_TypeSet_SuperOf -> {
                handler.handle(boundType, M_Types.ANYTHING, M_TypeMatchSuperRelation.EQUAL)
                        || handler.handle(typeSet.boundType, M_Types.NOTHING, M_TypeMatchSuperRelation.EQUAL)
            }
        }
    }

    override fun getCommonSuperSet0(typeSet: M_TypeSet): M_TypeSet {
        return when (typeSet) {
            M_TypeSet_All -> typeSet
            is M_TypeSet_One -> M_TypeSetInternals.getCommonSuperSet(typeSet, this)
            is M_TypeSet_SubOf -> {
                val commonType = boundType.getCommonSuperType(typeSet.boundType)
                if (commonType != null) M_TypeSets.subOf(commonType) else M_TypeSets.ALL
            }
            is M_TypeSet_SuperOf -> M_TypeSets.ALL
        }
    }

    override fun getCommonSubSet0(typeSet: M_TypeSet): M_TypeSet? {
        return when (typeSet) {
            M_TypeSet_All -> this
            is M_TypeSet_One -> if (containsType(typeSet.type)) typeSet else null
            is M_TypeSet_SubOf -> {
                val commonType = boundType.getCommonSubType(typeSet.boundType)
                if (commonType != null) M_TypeSets.subOf(commonType) else null
            }
            is M_TypeSet_SuperOf -> null
        }
    }

    override fun containsType(type: M_Type) = boundType.isSuperTypeOf(type)
    override fun allSubTypes(): M_TypeSet = this
    override fun allSuperTypes(): M_TypeSet = M_TypeSet_All
    override fun canonicalInType() = M_Types.NOTHING
    override fun canonicalOutType() = boundType
    override fun getSubType() = null
    override fun getSuperType() = boundType

    override fun getTypeParams0(res: MutableSet<M_TypeParam>) = boundType.getTypeParams0(res)

    override fun replaceParams(map: Map<M_TypeParam, M_TypeSet>, capture: Boolean): M_TypeSet {
        val typeRep = boundType.replaceParams(map, capture)
        return if (typeRep.isSameType(boundType)) this else typeRep.typeSet().allSubTypes()
    }

    override fun expandCaptures(mode: M_TypeExpandMode): M_TypeSet {
        val expType = boundType.expandCaptures(mode)
        if (expType === boundType) {
            return this
        }

        return when (mode) {
            M_TypeExpandMode.SUPER -> if (expType == null) M_TypeSets.ALL else M_TypeSets.subOf(expType)
            M_TypeExpandMode.SUB -> if (expType == null) M_TypeSets.one(M_Types.NOTHING) else M_TypeSets.subOf(expType)
        }
    }

    override fun validate() {
        boundType.validate()
    }
}

private class M_TypeSet_SubOf_Internal(boundType: M_Type): M_TypeSet_SubOf(boundType)

sealed class M_TypeSet_SuperOf(val boundType: M_Type): M_TypeSet_Many() {
    override fun strCode() = "+${boundType.strCode()}"

    override fun hashCode0() = Objects.hash(javaClass, boundType)

    override fun matchTypeEqual(typeSet: M_TypeSet, handler: M_TypeMatchEqualHandler): Boolean {
        return typeSet is M_TypeSet_SuperOf && handler.handle(boundType, typeSet.boundType)
    }

    override fun matchTypeSuper(typeSet: M_TypeSet, handler: M_TypeMatchSuperHandler): Boolean {
        return when (typeSet) {
            M_TypeSet_All -> handler.handle(boundType, M_Types.NOTHING, M_TypeMatchSuperRelation.EQUAL)
            is M_TypeSet_One -> handler.handle(boundType, typeSet.type, M_TypeMatchSuperRelation.SUB)
            is M_TypeSet_SubOf -> {
                handler.handle(boundType, M_Types.NOTHING, M_TypeMatchSuperRelation.EQUAL)
                        || handler.handle(typeSet.boundType, M_Types.ANYTHING, M_TypeMatchSuperRelation.EQUAL)
            }
            is M_TypeSet_SuperOf -> handler.handle(boundType, typeSet.boundType, M_TypeMatchSuperRelation.SUB)
        }
    }

    override fun getCommonSuperSet0(typeSet: M_TypeSet): M_TypeSet {
        return when (typeSet) {
            M_TypeSet_All -> typeSet
            is M_TypeSet_One -> M_TypeSetInternals.getCommonSuperSet(typeSet, this)
            is M_TypeSet_SubOf -> M_TypeSets.ALL
            is M_TypeSet_SuperOf -> {
                val commonType = boundType.getCommonSubType(typeSet.boundType)
                if (commonType != null) M_TypeSets.superOf(commonType) else M_TypeSets.ALL
            }
        }
    }

    override fun getCommonSubSet0(typeSet: M_TypeSet): M_TypeSet? {
        return when (typeSet) {
            M_TypeSet_All -> this
            is M_TypeSet_One -> if (containsType(typeSet.type)) typeSet else null
            is M_TypeSet_SubOf -> null
            is M_TypeSet_SuperOf -> {
                val commonType = boundType.getCommonSuperType(typeSet.boundType)
                if (commonType != null) M_TypeSets.superOf(commonType) else null
            }
        }
    }

    override fun containsType(type: M_Type) = type.isSuperTypeOf(boundType)
    override fun allSubTypes(): M_TypeSet = M_TypeSet_All
    override fun allSuperTypes(): M_TypeSet = this
    override fun canonicalInType() = boundType
    override fun canonicalOutType() = M_Types.ANYTHING
    override fun getSubType() = boundType
    override fun getSuperType() = null

    override fun getTypeParams0(res: MutableSet<M_TypeParam>) = boundType.getTypeParams0(res)

    override fun replaceParams(map: Map<M_TypeParam, M_TypeSet>, capture: Boolean): M_TypeSet {
        val typeRep = boundType.replaceParams(map, capture)
        return if (typeRep.isSameType(boundType)) this else typeRep.typeSet().allSuperTypes()
    }

    override fun expandCaptures(mode: M_TypeExpandMode): M_TypeSet {
        return when (mode) {
            M_TypeExpandMode.SUPER -> {
                val expType = boundType.expandCaptures(M_TypeExpandMode.SUB)
                when {
                    expType === boundType -> this
                    expType == null -> M_TypeSets.ALL
                    // The Kotlin way is +nothing, but it would be converted to *, so not that simple.
                    //expType == null -> M_TypeSets.superOf(M_Type_Nothing)
                    else -> M_TypeSets.superOf(expType)
                }
            }
            M_TypeExpandMode.SUB -> {
                val expType = boundType.expandCaptures(M_TypeExpandMode.SUPER)
                when {
                    expType === boundType -> this
                    expType == null -> M_TypeSets.one(M_Types.ANYTHING)
                    else -> M_TypeSets.superOf(expType)
                }
            }
        }
    }

    override fun validate() {
        boundType.validate()
    }
}

private class M_TypeSet_SuperOf_Internal(boundType: M_Type): M_TypeSet_SuperOf(boundType)

private object M_TypeSetInternals {
    fun getCommonSuperSet(set1: M_TypeSet_One, set2: M_TypeSet_SubOf): M_TypeSet {
        return if (set2.containsType(set1.type)) {
            set2
        } else {
            val commonType = set1.type.getCommonSuperType(set2.boundType)
            if (commonType != null) M_TypeSets.subOf(commonType) else M_TypeSets.ALL
        }
    }

    fun getCommonSuperSet(set1: M_TypeSet_One, set2: M_TypeSet_SuperOf): M_TypeSet {
        return if (set2.containsType(set1.type)) {
            set2
        } else {
            val commonType = set1.type.getCommonSubType(set2.boundType)
            if (commonType != null) M_TypeSets.superOf(commonType) else M_TypeSets.ALL
        }
    }
}
