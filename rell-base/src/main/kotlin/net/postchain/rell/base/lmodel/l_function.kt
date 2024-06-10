/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_TypeAdapter
import net.postchain.rell.base.compiler.base.core.C_VarUid
import net.postchain.rell.base.compiler.base.expr.C_VarFact
import net.postchain.rell.base.compiler.base.expr.C_VarFacts
import net.postchain.rell.base.compiler.base.fn.C_ArgMatching
import net.postchain.rell.base.compiler.base.lib.C_MemberRestrictions
import net.postchain.rell.base.compiler.base.lib.C_SpecialLibGlobalFunctionBody
import net.postchain.rell.base.compiler.base.lib.C_SpecialLibMemberFunctionBody
import net.postchain.rell.base.compiler.base.lib.C_SysFunction
import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_QualifiedName
import net.postchain.rell.base.model.R_Type
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

enum class L_ParamImplication {
    NOT_NULL {
        override fun toVarFacts(varId: C_VarUid): C_VarFacts {
            return C_VarFacts.of(nulled = mapOf(varId to C_VarFact.NO))
        }
    },
    ;

    abstract fun toVarFacts(varId: C_VarUid): C_VarFacts
}

class L_FunctionParam(
    val name: R_Name,
    val mParam: M_FunctionParam,
    val lazy: Boolean,
    val implies: L_ParamImplication?,
    val restrictions: C_MemberRestrictions,
    override val docSymbol: DocSymbol,
): DocDefinition {
    val type = mParam.type
    val arity = mParam.arity
    val nullable = mParam.nullable

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

    fun validate() {
        mParam.validate()
    }
}

abstract class L_CommonFunctionHeader(
    val params: List<L_FunctionParam>,
) {
    private val paramsMap: Map<R_Name, L_FunctionParam> by lazy {
        params.associateBy { it.name }.toImmMap()
    }

    fun getParam(name: R_Name): L_FunctionParam? {
        return paramsMap[name]
    }
}

class L_FunctionHeader(
    val mHeader: M_FunctionHeader,
    params: List<L_FunctionParam>,
): L_CommonFunctionHeader(params) {
    val typeParams = mHeader.typeParams
    val resultType = mHeader.resultType

    init {
        checkEquals(this.params.size, mHeader.params.size)
        for (i in this.params.indices) {
            check(this.params[i].mParam === mHeader.params[i]) {
                "$mHeader ; $i ; ${this.params[i].mParam} ; ${mHeader.params[i]}"
            }
        }
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
        mHeader.validate()
    }

    fun replaceTypeParams(map: Map<M_TypeParam, M_TypeSet>): L_FunctionHeader {
        if (map.isEmpty()) {
            return this
        }

        val mHeader2 = mHeader.replaceTypeParams(map)
        checkEquals(mHeader2.params.size, params.size)

        val params2 = params.mapIndexedOrSame { i, param ->
            param.replaceMParam(mHeader2.params[i])
        }

        return if (mHeader2 === mHeader && params2 === params) this else L_FunctionHeader(mHeader2, params2)
    }
}

class L_FunctionParamsMatch(
    private val mMatch: M_FunctionParamsMatch,
    val actualParams: List<L_FunctionParam>,
    val argMatching: C_ArgMatching,
) {
    fun matchArgs(argTypes: List<M_Type>, expectedResultType: M_Type?): L_FunctionHeaderMatch? {
        val m = mMatch.matchArgs(argTypes, expectedResultType)
        m ?: return null

        val adapters = m.conversions.mapNotNullAllOrNull {
            L_TypeUtils.getTypeAdapter(it)
        }

        adapters ?: return null

        val resParams = actualParams
            .mapIndexed { i, lParam -> lParam.replaceMParam(m.actualHeader.params[i]) }
            .toImmList()

        val actualHeader = L_FunctionHeader(m.actualHeader, resParams)

        return L_FunctionHeaderMatch(
            actualHeader,
            adapters = adapters,
            typeArgs = m.typeArgs.mapKeys { R_Name.of(it.key) }.toImmMap(),
        )
    }
}

class L_FunctionHeaderMatch(
    val actualHeader: L_FunctionHeader,
    val adapters: List<C_TypeAdapter>,
    val typeArgs: Map<R_Name, M_Type>,
)

class L_FunctionFlags(
    val isStatic: Boolean,
    val isPure: Boolean,
)

class L_Function(
    val qualifiedName: R_QualifiedName,
    val header: L_FunctionHeader,
    val flags: L_FunctionFlags,
    val body: L_FunctionBody,
) {
    fun strCode(actualName: R_QualifiedName = qualifiedName): String {
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
        return if (header2 === header) this else L_Function(qualifiedName, header2, flags, body)
    }

    fun validate() {
        header.validate()
    }

    fun getDocMember(name: String): DocDefinition? {
        val rName = R_Name.of(name)
        return header.getParam(rName)
    }
}

class L_FunctionBodyMeta(
    val callPos: S_Pos,
    val rSelfType: R_Type,
    val rResultType: R_Type,
    val rTypeArgs: Map<String, R_Type>,
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
    fullName: R_FullName,
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

    override fun getDocMember(name: String): DocDefinition? {
        return function.getDocMember(name)
    }
}

class L_NamespaceMember_SpecialFunction(
    fullName: R_FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val fn: C_SpecialLibGlobalFunctionBody,
): L_NamespaceMember(fullName, header, doc) {
    override fun strCode() = "special function ${qualifiedName.str()}()"
}

class L_TypeDefMember_Function(
    fullName: R_FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val function: L_Function,
    val deprecated: C_Deprecated?,
): L_TypeDefMember(fullName, header, doc) {
    override fun strCode(): String {
        val parts = listOfNotNull(
            L_InternalUtils.deprecatedStrCodeOrNull(deprecated),
            function.strCode(R_QualifiedName.of(simpleName)),
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

    override fun getDocMember(name: String): DocDefinition? {
        return function.getDocMember(name)
    }
}

class L_TypeDefMember_ValueSpecialFunction(
    fullName: R_FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val fn: C_SpecialLibMemberFunctionBody,
): L_TypeDefMember(fullName, header, doc) {
    override fun strCode() = "special function $simpleName(...)"
}

class L_TypeDefMember_StaticSpecialFunction(
    fullName: R_FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val fn: C_SpecialLibGlobalFunctionBody,
): L_TypeDefMember(fullName, header, doc) {
    override fun strCode() = "static special function $simpleName(...)"
}
