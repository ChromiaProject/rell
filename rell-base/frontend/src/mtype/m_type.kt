/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype


import net.postchain.rell.base.utils.ImmSet
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmSet
import java.util.*

object M_Types {
    val ANYTHING: M_Type = M_Type_Simple.ANYTHING
    val NOTHING: M_Type = M_Type_Simple.NOTHING
    val ANY: M_Type = M_Type_Simple.ANY
    val NULL: M_Type = M_NullableTypeUtils.NULL

    fun param(param: M_TypeParam): M_Type = M_Type_Param.make(param)
    fun capture(set: M_TypeSet_Many): M_Type = M_Type_Capture.make(set)
    fun function(result: M_Type, params: List<M_Type>): M_Type = M_FunctionTypeUtils.makeType(result, params.toImmList())
    fun nullable(valueType: M_Type): M_Type = M_NullableTypeUtils.makeType(valueType)
    fun tuple(types: List<M_Type>): M_Type = M_TupleTypeUtils.makeType(types)
    fun tuple(types: List<M_Type>, names: List<String?>): M_Type = M_TupleTypeUtils.makeType(types, names)
}

abstract class M_Type {
    final override fun toString() = strCode()

    abstract fun strCode(): String
    fun strMsg(): String = strCode()

    val oneTypeSet: M_TypeSet by lazy {
        M_TypeSetUtils.newOne(this)
    }

    val superTypesSet: M_TypeSet by lazy {
        M_TypeSetUtils.newSuperOf(this)
    }

    val subTypesSet: M_TypeSet by lazy {
        M_TypeSetUtils.newSubOf(this)
    }

    private val lazyHashCode: Int by lazy {
        val h0 = hashCode0()
        Objects.hash(javaClass, h0)
    }

    final override fun equals(other: Any?): Boolean {
        return when {
            other == null -> false
            other === this -> true
            other !is M_Type -> false
            lazyHashCode != other.lazyHashCode -> false
            else -> matchTypeEqual(other) { type1, type2 ->
                type1 == type2
            }
        }
    }

    protected abstract fun hashCode0(): Int

    final override fun hashCode(): Int = lazyHashCode

    protected abstract fun matchTypeEqual0(type: M_Type, handler: M_TypeMatchEqualHandler): Boolean

    private fun matchTypeEqual(type: M_Type, handler: M_TypeMatchEqualHandler): Boolean {
        return type === this || matchTypeEqual0(type, handler)
    }

    protected abstract fun matchTypeSuper0(type: M_Type, handler: M_TypeMatchSuperHandler): Boolean

    private fun matchTypeSuper(type: M_Type, handler: M_TypeMatchSuperHandler): Boolean {
        if (type == M_Types.NOTHING || type == this) {
            return true
        }

        val set = when (type) {
            is M_Type_Capture -> type.set
            is M_Type_Param -> type.param.bounds
            else -> null
        }
        val upper = set?.getSuperType()
        if (upper != null) {
            return matchTypeSuper0(upper, handler)
        }

        return matchTypeSuper0(type, handler)
    }

    protected open fun matchTypeConvert0(type: M_Type, handler: M_TypeMatchConvertHandler): Boolean {
        val res = matchTypeSuper(type) { type1, type2, rel ->
            handler.handle(type1, type2, rel.convertRel)
        }
        return res || getConversion0(type) != null
    }

    open fun getParentType(): M_Type? = null

    fun isSuperTypeOf(type: M_Type): Boolean {
        val res = matchTypeSuper(type) { type1, type2, rel ->
            M_TypeUtils.matchTypeIsSuperTypeOf(type1, type2, rel)
        }
        return res
    }

    protected open fun getCommonSuperType0(type: M_Type): M_Type? = null

    fun getCommonSuperType(type: M_Type): M_Type? {
        return when {
            isSuperTypeOf(type) -> this
            type.isSuperTypeOf(this) -> type
            else -> getCommonSuperType0(type) ?: type.getCommonSuperType0(this)
        }
    }

    protected open fun getCommonSubType0(type: M_Type): M_Type? = null

    fun getCommonSubType(type: M_Type): M_Type? {
        return when {
            isSuperTypeOf(type) -> type
            type.isSuperTypeOf(this) -> this
            else -> getCommonSubType0(type) ?: type.getCommonSubType0(this)
        }
    }

    fun getCommonConversionType(type: M_Type): M_Type? {
        val commonType = getCommonSuperType(type)
        val res = when {
            commonType != null -> commonType
            getConversion0(type) != null -> this
            type.getConversion0(this) != null -> type
            else -> getCommonConversionType0(type) ?: type.getCommonConversionType0(this)
        }
        return res
    }

    protected open fun getCommonConversionType0(type: M_Type): M_Type? {
        val conversion = getConversion0(type)
        return if (conversion != null) this else null
    }

    fun getConversion(sourceType: M_Type): M_Conversion? {
        return if (isSuperTypeOf(sourceType)) {
            M_Conversion_Direct
        } else {
            getConversion0(sourceType)
        }
    }

    protected open fun getConversion0(sourceType: M_Type): M_Conversion? = null

    abstract fun getTypeParams0(res: MutableSet<M_TypeParam>)

    fun getTypeParams(): ImmSet<M_TypeParam> {
        val set = mutableSetOf<M_TypeParam>()
        getTypeParams0(set)
        return set.toImmSet()
    }

    abstract fun replaceParams(map: Map<M_TypeParam, M_TypeSet>, capture: Boolean): M_TypeRep

    fun replaceParamsIn(map: Map<M_TypeParam, M_TypeSet>): M_Type {
        return replaceParamsInOut(map, M_TypeExpandMode.SUB, M_Types.NOTHING)
    }

    fun replaceParamsOut(map: Map<M_TypeParam, M_TypeSet>): M_Type {
        return replaceParamsInOut(map, M_TypeExpandMode.SUPER, M_Types.ANYTHING)
    }

    private fun replaceParamsInOut(map: Map<M_TypeParam, M_TypeSet>, mode: M_TypeExpandMode, noneType: M_Type): M_Type {
        val capType = replaceParams(map, true).type()
        return if (capType === this) this else (capType.expandCaptures(mode) ?: noneType)
    }

    abstract fun expandCaptures(mode: M_TypeExpandMode): M_Type?

    abstract fun captureWildcards(): M_Type
    open fun getCapturedSet(): M_TypeSet? = null

    fun matchTypeParamsIn(type: M_Type, handler: M_TypeParamMatchHandler): Boolean {
        return matchTypeParams0(type, M_TypeMatchRelation.CONVERT, handler)
    }

    fun matchTypeParamsOut(type: M_Type, handler: M_TypeParamMatchHandler): Boolean {
        return matchTypeParams0(type, M_TypeMatchRelation.SUB, handler)
    }

    protected open fun matchTypeParams0(
        type: M_Type,
        rel: M_TypeMatchRelation,
        handler: M_TypeParamMatchHandler,
    ): Boolean {
        return when (rel) {
            M_TypeMatchRelation.CONVERT -> matchTypeConvert0(type) { type1, type2, rel2 ->
                type1.matchTypeParams0(type2, rel2, handler)
            }
            M_TypeMatchRelation.EQUAL -> matchTypeEqual(type) { type1, type2 ->
                type1.matchTypeParams0(type2, M_TypeMatchRelation.EQUAL, handler)
            }
            M_TypeMatchRelation.SUPER -> matchTypeSuper(type) { type1, type2, rel2 ->
                type1.matchTypeParams0(type2, rel2.convertRel, handler)
            }
            M_TypeMatchRelation.SUB -> type.matchTypeSuper(this) { type1, type2, rel2 ->
                val subRel2 = when (rel2) {
                    M_TypeMatchSuperRelation.EQUAL -> M_TypeMatchRelation.EQUAL
                    M_TypeMatchSuperRelation.SUB -> M_TypeMatchRelation.SUPER
                    M_TypeMatchSuperRelation.SUPER -> M_TypeMatchRelation.SUB
                }
                type2.matchTypeParams0(type1, subRel2, handler)
            }
        }
    }

    /** @throws [M_TypeException] */
    abstract fun validate()
}
