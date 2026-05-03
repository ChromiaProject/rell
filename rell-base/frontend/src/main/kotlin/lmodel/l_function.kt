/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_TypeAdapter
import net.postchain.rell.base.compiler.base.fn.C_ArgMatching
import net.postchain.rell.base.compiler.base.lib.C_MemberRestrictions
import net.postchain.rell.base.compiler.base.lib.C_SpecialLibGlobalFunctionBody
import net.postchain.rell.base.compiler.base.lib.C_SpecialLibMemberFunctionBody
import net.postchain.rell.base.compiler.base.lib.C_SysFunction
import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.model.*
import net.postchain.rell.base.mtype.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocDefinition
import net.postchain.rell.base.utils.doc.DocSymbol

enum class L_ParamArity(val mArity: M_ParamArity) {
    ONE(M_ParamArity.ONE),
    ZERO_ONE(M_ParamArity.ZERO_ONE),
    ZERO_MANY(M_ParamArity.ZERO_MANY),
    ONE_MANY(M_ParamArity.ONE_MANY),
}

class L_ParamImplication private constructor(val kind: Kind, val since: R_LangVersion?) {
    fun since(version: String): L_ParamImplication {
        require(since == null)
        val rVersion = R_LangVersion.of(version)
        return L_ParamImplication(kind, rVersion)
    }

    enum class Kind {
        TRUE,
        NOT_NULL,
    }

    companion object {
        val TRUE = L_ParamImplication(Kind.TRUE, null)
        val NOT_NULL = L_ParamImplication(Kind.NOT_NULL, null)
    }
}

class L_FunctionParam constructor(
    val name: Name,
    val mParam: M_FunctionParam,
    val lazy: Boolean,
    val implies: L_ParamImplication?,
    val restrictions: C_MemberRestrictions,
    override val docSymbol: DocSymbol,
): DocDefinition() {
    val arity = mParam.arity
    val type: M_Type get() = mParam.type
    val rType: R_Type get() = mParam.rType

    val nullable = mParam.nullable
    val exact = mParam.exact

    override val docSourcePos = null

    override fun toString() = strCode()

    fun strCode(): String {
        var res = mParam.strCode(compact = false)
        if (lazy) res = "@lazy $res"
        if (implies != null) res = "@implies($implies) $res"
        return res
    }

    fun replaceMParam(newMParam: M_FunctionParam): L_FunctionParam {
        return if (newMParam === mParam) this else L_FunctionParam(
            name = name,
            newMParam,
            lazy = lazy,
            implies = implies,
            restrictions = restrictions,
            docSymbol = docSymbol,
        )
    }

    fun toSimpleParam(): L_FunctionParam {
        return if (arity == M_ParamArity.ONE) this else L_FunctionParam(
            name = name,
            mParam = mParam.toSimpleParam(),
            lazy = lazy,
            implies = implies,
            restrictions = restrictions,
            docSymbol = docSymbol,
        )
    }
}

abstract class L_CommonFunctionHeader(
    val params: ImmList<L_FunctionParam>,
)

class L_FunctionHeader constructor(
    val intHeader: L_InternalFunctionHeader,
): L_CommonFunctionHeader(intHeader.params) {
    val typeParams = intHeader.typeParams
    val resultType: M_Type by lazy { intHeader.rResultType.mType }

    val rResultType: R_Type get() = intHeader.rResultType

    init {
        checkEquals(this.params.size, intHeader.params.size)
    }

    fun strCode(name: String? = null): String {
        val parts = mutableListOf<String>()
        if (typeParams.isNotEmpty()) parts.add(typeParams.joinToString(",", "<", ">") { it.strCode() })
        val s = "${name ?: ""}(${params.joinToString(", ") { it.strCode() }}): ${resultType.strCode()}"
        parts.add(s)
        return parts.joinToString(" ")
    }

    override fun toString() = strCode()

    fun validate() {
        intHeader.validate()
    }

    fun replaceTypeParams(map: Map<M_TypeParam, M_TypeSet>): L_FunctionHeader {
        if (map.isEmpty()) {
            return this
        }

        val intHeader2 = intHeader.replaceTypeParams(map)
        return if (intHeader2 === intHeader) this else L_FunctionHeader(intHeader2)
    }
}

class L_FunctionParamsMatch(
    private val header: L_InternalFunctionHeader,
    private val paramIndexes: ImmList<Int>,
    val actualParams: ImmList<L_FunctionParam>,
    val argMatching: C_ArgMatching,
) {
    fun matchArgs(argTypes: List<R_Type>, expectedResultType: R_Type?): L_FunctionHeaderMatch? {
        val m = matchArgs0(argTypes, expectedResultType)
        m ?: return null

        val rTypeArgs = m.typeArgs.mapKeysToImmMap { Name.of(it.key) }

        return L_FunctionHeaderMatch(
            m.actualHeader,
            adapters = m.adapters,
            typeArgs = rTypeArgs,
        )
    }

    private fun matchArgs0(argTypes: List<R_Type>, expectedResultType: R_Type?): HeaderMatch? {
        checkEquals(argTypes.size, paramIndexes.size)

        val match = if (header.typeParams.isEmpty()) {
            val actualHeader = L_InternalFunctionHeader(immListOf(), header.resultType, actualParams)
            TypeParamsMatch(immMapOf(), actualHeader)
        } else {
            matchTypeArgs(argTypes, expectedResultType)
        }
        match ?: return null

        val badTypeArgs = match.typeArgs.values.filter { it.isAbstract() || !it.isValid() }
        if (badTypeArgs.isNotEmpty()) {
            return null
        }

        val adapters = match.actualHeader.params.indices.mapNotNullAllOrNull { i ->
            val param = match.actualHeader.params[i]
            val rParamType = param.rType
            val rArgType = argTypes[i]

            when {
                param.exact && rArgType != rParamType -> null
                param.nullable && rArgType !is R_NullableType -> null
                else -> rParamType.getTypeAdapter(rArgType)
            }
        }

        adapters ?: return null

        val rTypeArgs = match.typeArgs.mapValuesToImmMap { it.value }
        return HeaderMatch(rTypeArgs, match.actualHeader, adapters)
    }

    private class HeaderMatch(
        val typeArgs: ImmMap<String, R_Type>,
        val actualHeader: L_InternalFunctionHeader,
        val adapters: ImmList<C_TypeAdapter>,
    ) {
        init {
            checkEquals(adapters.size, actualHeader.params.size)
        }
    }

    private fun matchTypeArgs(argTypes: List<R_Type>, resultType: R_Type?): TypeParamsMatch? {
        val map = mutableMapOf<String, R_Type>()

        for (param in header.typeParams) {
            if (param.bounds is M_TypeSet_SuperOf) {
                map[param.name] = L_TypeUtils.getRType(param.bounds.boundType)
            }
        }

        for ((i, param) in actualParams.withIndex()) {
            val argType = argTypes[i]
            if (!matchParamType(param.rType, argType, map)) {
                return null
            }
        }

        if (resultType != null && !resultType.hasTypeVariable()) {
            matchResultType(header.rResultType, resultType, map)
        }

        val typeArgs = map.toImmMap()

        for (param in header.typeParams) {
            val typeArg = typeArgs[param.name]
            if (typeArg != null) {
                val valid = when (param.bounds) {
                    is M_TypeSet_SubOf -> {
                        val paramType = L_TypeUtils.getRType(param.bounds.boundType)
                        paramType.isAssignableFrom(typeArg)
                    }
                    is M_TypeSet_SuperOf -> {
                        val paramType = L_TypeUtils.getRType(param.bounds.boundType)
                        typeArg.isAssignableFrom(paramType)
                    }
                    else -> true
                }
                if (!valid) {
                    return null
                }
            }
        }

        val typeSets = header.typeParams
            .mapNotNull { param -> typeArgs[param.name]?.let { param to M_TypeSets.one(it.mType) } }
            .toImmMap()
        val replacedHeader = header.replaceTypeParams(typeSets)

        val fullParams = paramIndexes.mapToImmList { replacedHeader.params[it] }
        val unresolved = header.typeParams.filterToImmList { it.name !in typeArgs }

        val resParams = actualParams.indices.mapToImmList { fullParams[it] }
        val actualHeader = L_InternalFunctionHeader(unresolved, replacedHeader.resultType, resParams)
        return TypeParamsMatch(typeArgs, actualHeader)
    }

    private fun matchParamType(paramType: R_Type, argType: R_Type, map: MutableMap<String, R_Type>): Boolean {
        return when {
            paramType.isAssignableFrom(argType) -> true
            paramType is R_BaseGenericType -> matchParamTypeGeneric(paramType, argType, map)
            paramType is R_NullableType -> when (argType) {
                is R_NullableType -> matchParamType(paramType.valueType, argType.valueType, map)
                else -> matchParamType(paramType.valueType, argType, map)
            }
            paramType is R_FunctionType -> when (argType) {
                is R_FunctionType -> {
                    paramType.params.size == argType.params.size
                            && matchTypeArg(paramType.result, argType.result, map)
                            && paramType.params.indices.all { matchTypeArg(paramType.params[it], argType.params[it], map) }
                }
                else -> false
            }
            paramType is R_VariableType -> {
                val old = map[paramType.name]
                val common = when {
                    old == null -> argType
                    else -> getCommonType(old, argType, true)
                }
                common?.let {
                    map[paramType.name] = it
                    true
                } ?: false
            }
            else -> paramType.getTypeAdapter(argType) != null
        }
    }

    private fun matchParamTypeGeneric(
        paramType: R_BaseGenericType,
        argType: R_Type,
        map: MutableMap<String, R_Type>,
    ): Boolean {
        var curArgType = argType
        while (curArgType !is R_BaseGenericType || curArgType.typeName != paramType.typeName) {
            val parentType = curArgType.parentType as? R_BaseGenericType
            curArgType = parentType ?: return false
        }

        for ((i, dstType) in paramType.args.withIndex()) {
            val srcType = curArgType.args[i]
            if (!matchTypeArg(dstType, srcType, map)) {
                return false
            }
        }

        return true
    }

    private fun matchTypeArg(dstType: R_Type, srcType: R_Type, map: MutableMap<String, R_Type>): Boolean {
        if (dstType.isAssignableArg(srcType)) {
            return true
        }

        return when (dstType) {
            is R_SubType -> matchTypeArg(dstType.valueType, srcType, map)
            is R_VariableType -> {
                val old = map[dstType.name]
                val common = if (old == null) srcType else getCommonType(old, srcType, false)
                common?.let {
                    map[dstType.name] = it
                    true
                } ?: false
            }
            is R_GenericType -> when (dstType.typeName) {
                "map_entry" -> srcType is R_TupleType
                        && srcType.fields.size == 2
                        && matchTypeArg(dstType.args[0], srcType.fields[0].type, map)
                        && matchTypeArg(dstType.args[1], srcType.fields[1].type, map)
                else -> false
            }
            else -> false
        }
    }

    private fun matchResultType(formalType: R_Type, actualType: R_Type, map: MutableMap<String, R_Type>) {
        when (formalType) {
            is R_BaseGenericType -> {
                when (actualType) {
                    is R_BaseGenericType -> {
                        var curType: R_Type = formalType
                        while (curType !is R_BaseGenericType || curType.typeName != actualType.typeName) {
                            val parentType = curType.parentType as? R_BaseGenericType
                            curType = parentType ?: return
                        }
                        for ((i, dstType) in curType.args.withIndex()) {
                            val srcType = actualType.args[i]
                            matchTypeArg(dstType, srcType, map)
                        }
                    }
                }
            }
            is R_VariableType -> {
                val old = map[formalType.name]
                val common = when {
                    old == null -> actualType
                    old.isAssignableFrom(actualType) -> actualType
                    actualType.isAssignableFrom(old) -> old
                    else -> null
                }
                if (common != null) {
                    map[formalType.name] = common
                }
            }
        }
    }

    private fun getCommonType(type1: R_Type, type2: R_Type, conv: Boolean): R_Type? {
        val res = R_Type.commonTypeOpt(type1, type2)
        return when {
            res != null -> res
            conv -> getCommonConvertibleType(type1, type2)
            else -> null
        }
    }

    private fun getCommonConvertibleType(type1: R_Type, type2: R_Type): R_Type? {
        var res = getCommonConvertibleType0(type1, type2)
        if (res == null) {
            res = getCommonConvertibleType0(type2, type1)
        }
        return res
    }

    private fun getCommonConvertibleType0(type1: R_Type, type2: R_Type): R_Type? = when {
        type1.isAssignableFrom(type2) -> type1
        type1.getTypeAdapter(type2) != null -> type1
        type1 is R_NullableType -> {
            val commonType = when (type2) {
                is R_NullableType -> getCommonConvertibleType(type1.valueType, type2.valueType)
                else -> getCommonConvertibleType(type1.valueType, type2)
            }
            commonType?.let { R_NullableType(commonType) }
        }
        else -> null
    }

    private class TypeParamsMatch(
        val typeArgs: ImmMap<String, R_Type>,
        val actualHeader: L_InternalFunctionHeader,
    )
}

class L_InternalFunctionHeader(
    val typeParams: ImmList<M_TypeParam>,
    val resultType: M_Type,
    val params: ImmList<L_FunctionParam>,
) {
    val rResultType: R_Type by lazy { L_TypeUtils.getRType(resultType) }

    fun replaceTypeParams(map: Map<M_TypeParam, M_TypeSet>): L_InternalFunctionHeader {
        val mHeader = M_FunctionHeader(typeParams, resultType, params.mapToImmList { it.mParam })
        val mResHeader = mHeader.replaceTypeParams(map)
        val resParams = params.mapIndexedToImmList { i, param -> param.replaceMParam(mResHeader.params[i]) }
        return L_InternalFunctionHeader(mResHeader.typeParams, mResHeader.resultType, resParams)
    }

    fun validate() {
        typeParams.forEach { it.validate() }
        resultType.validate()
        params.forEach { it.type.validate() }
    }
}

class L_FunctionHeaderMatch(
    val actualHeader: L_InternalFunctionHeader,
    val adapters: ImmList<C_TypeAdapter>,
    val typeArgs: ImmMap<Name, R_Type>,
)

class L_FunctionFlags(
    val isStatic: Boolean,
    val isPure: Boolean,
)

class L_Function constructor(
    val fullName: FullName,
    val header: L_FunctionHeader,
    val flags: L_FunctionFlags,
    val body: L_FunctionBody,
) {
    val docMembers: ImmMap<String, DocDefinition> by lazy {
        header.params.associateByToImmMap { it.name.str }
    }

    fun strCode(actualName: QualifiedName = fullName.qualifiedName): String {
        val parts = listOfNotNull(
            if (flags.isStatic) "static" else null,
            if (flags.isPure) "pure" else null,
            "function",
            header.strCode(actualName.str()),
        )
        return parts.joinToString(" ")
    }

    fun replaceTypeParams(map: Map<M_TypeParam, M_TypeSet>): L_Function {
        val header2 = header.replaceTypeParams(map)
        return if (header2 === header) this else L_Function(fullName, header2, flags, body)
    }

    fun validate() {
        header.validate()
    }
}

class L_FunctionBodyMeta(
    val callPos: S_Pos,
    val rSelfType: R_Type,
    val rResultType: R_Type,
    val rTypeArgs: ImmMap<String, R_Type>,
) {
    fun typeArg(name: String): R_Type {
        return rTypeArgs.getValue(name)
    }

    fun typeArgs(name1: String, name2: String): Pair<R_Type, R_Type> {
        val type1 = rTypeArgs.getValue(name1)
        val type2 = rTypeArgs.getValue(name2)
        return Pair(type1, type2)
    }
}

sealed class L_FunctionBody {
    abstract fun getSysFunction(meta: L_FunctionBodyMeta): C_SysFunction

    private class L_FunctionBody_Direct(private val fn: C_SysFunction): L_FunctionBody() {
        override fun getSysFunction(meta: L_FunctionBodyMeta) = fn
    }

    private class L_FunctionBody_Delegating(
        private val block: (L_FunctionBodyMeta) -> C_SysFunction,
    ): L_FunctionBody() {
        override fun getSysFunction(meta: L_FunctionBodyMeta): C_SysFunction {
            return block(meta)
        }
    }

    companion object {
        fun direct(fn: C_SysFunction): L_FunctionBody = L_FunctionBody_Direct(fn)

        fun delegating(block: (L_FunctionBodyMeta) -> C_SysFunction): L_FunctionBody {
            return L_FunctionBody_Delegating(block)
        }
    }
}

class L_NamespaceMember_Function(
    fullName: FullName,
    header: L_MemberHeader,
    docSymbol: DocSymbol,
    val function: L_Function,
    val deprecated: C_Deprecated?,
): L_NamespaceMember(fullName, header, docSymbol) {
    override fun strCode(): String {
        val parts = listOfNotNull(
            L_InternalUtils.deprecatedStrCodeOrNull(deprecated),
            function.strCode(qualifiedName),
        )
        return parts.joinToString(" ")
    }

    override fun getDocMembers0() = function.docMembers
}

class L_NamespaceMember_SpecialFunction(
    fullName: FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val fn: C_SpecialLibGlobalFunctionBody,
): L_NamespaceMember(fullName, header, doc) {
    override fun strCode() = "special function ${qualifiedName.str()}()"
}

class L_TypeDefMember_Function(
    fullName: FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val function: L_Function,
    val deprecated: C_Deprecated?,
): L_TypeDefMember(fullName, header, doc) {
    override fun strCode(): String {
        val parts = listOfNotNull(
            L_InternalUtils.deprecatedStrCodeOrNull(deprecated),
            function.strCode(QualifiedName.of(listOf(simpleName))),
        )
        return parts.joinToString(" ")
    }

    fun replaceTypeParams(map: Map<M_TypeParam, M_TypeSet>): L_TypeDefMember_Function {
        val function2 = function.replaceTypeParams(map)
        return if (function2 === function) this else {
            function2.validate()
            L_TypeDefMember_Function(fullName, header, docSymbol, function2, deprecated)
        }
    }

    override fun getDocMembers0() = function.docMembers
}

class L_TypeDefMember_ValueSpecialFunction(
    fullName: FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val fn: C_SpecialLibMemberFunctionBody,
): L_TypeDefMember(fullName, header, doc) {
    override fun strCode() = "special function $simpleName(...)"
}

class L_TypeDefMember_StaticSpecialFunction(
    fullName: FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val fn: C_SpecialLibGlobalFunctionBody,
): L_TypeDefMember(fullName, header, doc) {
    override fun strCode() = "static special function $simpleName(...)"
}
