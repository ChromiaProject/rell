/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import net.postchain.rell.base.utils.*

fun interface M_TypeParamMatchHandler {
    fun accept(param: M_TypeParam, type: M_Type, rel: M_TypeMatchRelation)
}

data class M_TypeParamMatch(val type: M_Type, val rel: M_TypeMatchRelation) {
    override fun toString() = "${type.strCode()}:$rel"
}

class M_TypeParamsResolver(private val params: ImmList<M_TypeParam>) {
    private val refMap: ImmMap<M_TypeParam, MutableSet<M_TypeParamMatch>> = params
        .associateWithToImmMap { mutableSetOf() }

    fun allParamsMatched(): Boolean {
        return params.all { refMap[it]?.isNotEmpty() ?: false }
    }

    fun matchTypeParamsIn(type1: M_Type, type2: M_Type): Boolean {
        return type1.matchTypeParamsIn(type2, this::addRef)
    }

    fun matchTypeParamsOut(type1: M_Type, type2: M_Type): Boolean {
        return type1.matchTypeParamsOut(type2, this::addRef)
    }

    fun addRef(param: M_TypeParam, type: M_Type, rel: M_TypeMatchRelation) {
        val set = refMap.getValue(param)
        set.add(M_TypeParamMatch(type, rel))
    }

    fun resolve(): Map<M_TypeParam, M_Type>? {
        val resolved = mutableMapOf<M_TypeParam, M_Type>()
        val resolvedSet = mutableMapOf<M_TypeParam, M_TypeSet>()

        for (param in params) {
            val refs = refMap.getValue(param)
            if (refs.isEmpty()) {
                continue
            }

            val bounds = param.bounds.replaceParams(resolvedSet.toImmMap(), true)
            val type = M_SingleTypeParamResolver.resolve(bounds, refs)
            type ?: return null

            resolved[param] = type
            resolvedSet[param] = M_TypeSets.one(type)
        }

        val res = resolved.toImmMap()

        // Need additional validation of type params, as bound may depend on type params (e.g. T:collection<R>).
        for (param in params) {
            val type = res[param]
            if (type != null && !validateTypeParam(param, type, res)) {
                return null
            }
        }

        return res
    }

    private fun validateTypeParam(param: M_TypeParam, type: M_Type, binding: Map<M_TypeParam, M_Type>): Boolean {
        val typeSets = binding.mapValues { M_TypeSets.one(it.value) }
        val bounds = param.bounds.replaceParams(typeSets, false)
        return bounds.containsType(type)
    }

    companion object {
        fun resolveTypeParams(
            typeParams: ImmList<M_TypeParam>,
            patternType: M_Type,
            actualType: M_Type,
        ): Map<M_TypeParam, M_Type>? {
            val resolver = M_TypeParamsResolver(typeParams)
            val match = resolver.matchTypeParamsIn(patternType, actualType)
            if (!match) {
                return null
            }

            val res = resolver.resolve()
            return res
        }
    }
}

private object M_SingleTypeParamResolver {
    fun resolve(boundSet: M_TypeSet, refs: Set<M_TypeParamMatch>): M_Type? {
        val converts = mutableListOf<M_Type>()
        val equals = mutableListOf<M_Type>()
        val subs = mutableListOf<M_Type>()
        val supers = mutableListOf<M_Type>()

        for (ref in refs) {
            val list = when (ref.rel) {
                M_TypeMatchRelation.EQUAL -> equals
                M_TypeMatchRelation.SUB -> subs
                M_TypeMatchRelation.SUPER -> supers
                M_TypeMatchRelation.CONVERT -> converts
            }
            list.add(ref.type)
        }

        val bounds = getBounds(boundSet, supers, subs)
        bounds ?: return null

        return if (equals.isNotEmpty()) {
            resolveEqual(bounds, equals, converts)
        } else if (converts.isNotEmpty()) {
            resolveConvert(bounds, converts)
        } else {
            bounds.lower ?: bounds.upper
        }
    }

    private fun resolveEqual(bounds: M_TypeBounds, equals: List<M_Type>, converts: List<M_Type>): M_Type? {
        if (equals.size > 1) {
            return null
        }

        val arg = equals.single()
        if (!isInBounds(arg, bounds)) {
            return null
        }

        for (type in converts) {
            val ok = arg.getConversion(type) != null
            if (!ok) {
                return null
            }
        }

        return arg
    }

    private fun resolveConvert(bounds: M_TypeBounds, converts: List<M_Type>): M_Type? {
        val commonOne = getCommonType(bounds.lower, converts, M_Type::getCommonSuperType)
        val common = commonOne?.value
        if (common != null && isInBounds(common, bounds)) {
            return common
        }

        val resType = resolveConvertSearch(bounds, converts)
        return if (resType != null && isInBounds(resType, bounds)) {
            resType
        } else {
            null
        }
    }

    private fun resolveConvertSearch(bounds: M_TypeBounds, converts: List<M_Type>): M_Type? {
        val candidates = converts.filter { type ->
            isConvertCandidate(type, bounds, converts)
        }
        if (candidates.size == 1) {
            return candidates.first()
        } else if (candidates.size > 1) {
            return resolveConvertCommon(bounds, null, candidates)
        }

        val boundsList = listOfNotNull(bounds.lower, bounds.upper)
        var resType = boundsList.firstOrNull { type ->
            isConvertCandidate(type, bounds, converts)
        }

        if (resType == null) {
            resType = resolveConvertCommon(bounds, null, converts)
        }

        if (resType == null) {
            resType = boundsList.firstNotNullOfOrNull { type ->
                resolveConvertCommon(bounds, type, converts)
            }
        }

        return resType
    }

    private fun isConvertCandidate(type: M_Type, bounds: M_TypeBounds, converts: List<M_Type>): Boolean {
        val inBounds = isInBounds(type, bounds)
        return inBounds && converts.all { type.getConversion(it) != null }
    }

    private fun resolveConvertCommon(bounds: M_TypeBounds, first: M_Type?, types: Iterable<M_Type>): M_Type? {
        val res = getCommonType(first, types, M_Type::getCommonConversionType)?.value
        return if (res != null && isInBounds(res, bounds)) res else null
    }

    private fun isInBounds(type: M_Type, lowerBound: M_Type?, upperBound: M_Type?): Boolean {
        return (lowerBound == null || type.isSuperTypeOf(lowerBound))
                && (upperBound == null || upperBound.isSuperTypeOf(type))
    }

    fun isInBounds(type: M_Type, bounds: M_TypeBounds): Boolean {
        return isInBounds(type, bounds.lower, bounds.upper)
    }

    private fun getBounds(boundSet: M_TypeSet, supers: List<M_Type>, subs: List<M_Type>): M_TypeBounds? {
        val lower = getCommonType(boundSet.getSubType(), supers, M_Type::getCommonSuperType)
        val upper = getCommonType(boundSet.getSuperType(), subs, M_Type::getCommonSubType)

        if (lower == null || upper == null) {
            return null
        }

        val lowerBound = lower.value
        val upperBound = upper.value
        if (lowerBound != null && upperBound != null && !upperBound.isSuperTypeOf(lowerBound)) {
            return null
        }

        return M_TypeBounds(lowerBound, upperBound)
    }

    private fun getCommonType(first: M_Type?, types: Iterable<M_Type>, op: (M_Type, M_Type) -> M_Type?): One<M_Type?>? {
        var common = first
        for (type in types) {
            common = if (common == null) type else op(common, type)
            common ?: return null
        }
        return One(common)
    }
}

private class M_TypeBounds(val lower: M_Type?, val upper: M_Type?) {
    override fun toString(): String {
        return "${lower?.strCode()?:"*"}:${upper?.strCode()?:"*"}"
    }
}
