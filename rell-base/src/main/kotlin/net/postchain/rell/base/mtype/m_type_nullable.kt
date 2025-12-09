/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

object M_NullableTypeUtils {
    val NULL: M_Type = M_Type_Null

    fun makeType(valueType: M_Type): M_Type {
        return when (valueType) {
            M_Type_Null -> valueType
            M_Types.ANYTHING -> valueType
            M_Types.NOTHING -> M_Type_Null
            M_Types.ANY -> M_Types.ANYTHING
            is M_Type_Nullable -> valueType
            else -> M_Type_Nullable_Internal(valueType)
        }
    }
}

private object M_Type_Null: M_Type_Simple("null") {
    override fun matchTypeSuper0(type: M_Type, handler: M_TypeMatchSuperHandler) = false
    override fun getCommonSuperType0(type: M_Type) = M_NullableTypeUtils.makeType(type)
}

sealed class M_Type_Nullable(val valueType: M_Type): M_Type()

private class M_Type_Nullable_Internal(valueType: M_Type): M_Type_Nullable(valueType) {
    init {
        check(valueType != M_Types.ANYTHING)
        check(valueType != M_Types.ANY)
        check(valueType != M_Types.NOTHING)
        check(valueType != M_Types.NULL)
        check(valueType !is M_Type_Nullable) { valueType.strCode() }
    }

    override fun strCode() = "${valueType.strCode()}?"

    override fun hashCode0() = valueType.hashCode()

    override fun matchTypeEqual0(type: M_Type, handler: M_TypeMatchEqualHandler): Boolean {
        return type is M_Type_Nullable && handler.handle(valueType, type.valueType)
    }

    override fun matchTypeSuper0(type: M_Type, handler: M_TypeMatchSuperHandler): Boolean {
        return matchTypeSuperCommon(type) { type1, type2 ->
            handler.handle(type1, type2, M_TypeMatchSuperRelation.SUPER)
        }
    }

    override fun matchTypeConvert0(type: M_Type, handler: M_TypeMatchConvertHandler): Boolean {
        return matchTypeSuperCommon(type) { type1, type2 ->
            handler.handle(type1, type2, M_TypeMatchRelation.CONVERT)
        }
    }

    private fun matchTypeSuperCommon(type: M_Type, handler: (M_Type, M_Type) -> Boolean): Boolean {
        return when (type) {
            is M_Type_Nullable -> handler(valueType, type.valueType)
            M_Type_Null -> {
                handler(valueType, type)
                true // Always returning true for null, ignoring the handler result.
            }
            else -> handler(valueType, type)
        }
    }

    override fun getCommonSuperType0(type: M_Type): M_Type? {
        return when (type) {
            M_Type_Null -> this
            is M_Type_Nullable -> {
                val commonValueType = valueType.getCommonSuperType(type.valueType)
                if (commonValueType == null) null else M_NullableTypeUtils.makeType(commonValueType)
            }
            else -> {
                val commonValueType = valueType.getCommonSuperType(type)
                if (commonValueType == null) null else M_NullableTypeUtils.makeType(commonValueType)
            }
        }
    }

    override fun getCommonConversionType0(type: M_Type): M_Type? {
        val commonValueType = when (type) {
            is M_Type_Nullable -> valueType.getCommonConversionType(type.valueType)
            else -> valueType.getCommonConversionType(type)
        }
        return if (commonValueType == null) null else M_NullableTypeUtils.makeType(commonValueType)
    }

    override fun getTypeParams0(res: MutableSet<M_TypeParam>) {
        valueType.getTypeParams0(res)
    }

    override fun replaceParams(map: Map<M_TypeParam, M_TypeSet>, capture: Boolean): M_TypeRep {
        val innerRep = valueType.replaceParams(map, capture)
        return when {
            innerRep.isSameType(valueType) -> M_TypeRep_Type(this)
            else -> {
                val resType = M_NullableTypeUtils.makeType(innerRep.typeSet().canonicalOutType())
                M_TypeRep_Type(resType)
            }
        }
    }

    override fun expandCaptures(mode: M_TypeExpandMode): M_Type {
        val expValueType = valueType.expandCaptures(mode)
        return when {
            expValueType === valueType -> this
            expValueType != null -> M_NullableTypeUtils.makeType(expValueType)
            else -> when (mode) {
                M_TypeExpandMode.SUPER -> M_Types.ANYTHING
                M_TypeExpandMode.SUB -> M_Types.NULL
            }
        }
    }

    override fun captureWildcards(): M_Type {
        val resValueType = valueType.captureWildcards()
        return if (resValueType === valueType) this else M_NullableTypeUtils.makeType(resValueType)
    }

    override fun validate() {
        valueType.validate()
    }

    override fun getConversion0(sourceType: M_Type): M_Conversion? {
        return when (sourceType) {
            is M_Type_Nullable -> {
                if (valueType.getConversion(sourceType.valueType) == null) null else M_Conversion_Nullable(this)
            }
            else -> valueType.getConversion(sourceType)
        }
    }
}
