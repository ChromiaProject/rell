/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import net.postchain.rell.base.mtype.utils.MTestParser
import net.postchain.rell.base.mtype.utils.MTestScope
import net.postchain.rell.base.utils.immMapOf
import net.postchain.rell.base.utils.toImmMap
import kotlin.test.assertEquals

abstract class BaseMFunctionTest: BaseMTypeTest() {
    override fun initScope(b: MTestScope.Builder) {
        MTestScope.initBasic(b)
        MTestScope.initNumeric(b)
        MTestScope.initCollections(b, "_")
        MTestScope.initConsumerSupplier(b)
        MTestScope.initMisc(b)
        MTestScope.initRellPrimitives(b)
        MTestScope.initRellCollections(b)
    }

    protected fun chkGlobal(def: String, args: String, exp: String) {
        val act = calcMatch(null, def, args)
        assertEquals(exp, act)
    }

    protected fun chkMember(type: String, def: String, args: String, exp: String) {
        val mType = MTestParser.parseType(type, scopeB.build())
        val act = calcMatch(mType, def, args)
        assertEquals(exp, act)
    }

    protected fun chkGlobalEx(def: String, args: String, exp: String) {
        val act = calcMatchEx(null, def, args)
        assertEquals(exp, act)
    }

    protected fun chkMemberEx(type: String, def: String, args: String, exp: String) {
        val mType = MTestParser.parseType(type, scopeB.build())
        val act = calcMatchEx(mType, def, args)
        assertEquals(exp, act)
    }

    private fun calcMatch(selfType: M_Type?, def: String, call: String): String {
        return calcMatch0(selfType, def, call) { _, m ->
            when {
                m == null -> "n/a"
                m.actualHeader.typeParams.isNotEmpty() -> "unresolved:${m.actualHeader.typeParams.joinToString(",")}"
                else -> {
                    var res = m.actualHeader.resultType.strCode()
                    if (m.typeArgs.isNotEmpty()) {
                        res = "$res [${m.typeArgs.entries.joinToString(",")}]"
                    }
                    res
                }
            }
        }
    }

    private fun calcMatchEx(selfType: M_Type?, def: String, call: String): String {
        return calcMatch0(selfType, def, call) { header, m ->
            val headerStr = if (selfType == null) null else header.strCode()
            when {
                m == null -> listOfNotNull(headerStr, "n/a").joinToString(" ")
                m.actualHeader.typeParams.isNotEmpty() -> {
                    val s = "unresolved:${m.actualHeader.typeParams.joinToString(",")}"
                    listOfNotNull(headerStr, s).joinToString(" ")
                }
                else -> {
                    val parts = listOfNotNull(headerStr).toMutableList()
                    if (m.typeArgs.isNotEmpty()) parts.add("[${m.typeArgs.entries.joinToString(",")}]")
                    val headerStr2 = m.actualHeader.strCode()
                    if (headerStr2 != headerStr) parts.add(headerStr2) else parts.add("OK")
                    parts.joinToString(" ")
                }
            }
        }
    }

    private fun calcMatch0(
        selfType: M_Type?,
        def: String,
        call: String,
        toString: (M_FunctionHeader, M_FunctionHeaderMatch?) -> String,
    ): String {
        var scope = scopeB.build()
        var selfTypeArgs: Map<M_TypeParam, M_TypeSet> = mapOf()

        if (selfType != null) {
            selfType as M_Type_Generic
            val subScopeB = scope.toBuilder()
            for (param in selfType.genericType.params) {
                subScopeB.paramDef(param)
            }
            scope = subScopeB.build()
            selfTypeArgs = selfType.genericType.params.indices.map { i ->
                selfType.genericType.params[i] to selfType.typeArgs[i]
            }.toImmMap()
        }

        var header = MTestParser.parseFunctionHeader(def, scope)
        checkTypeParams(selfTypeArgs, header)

        if (selfTypeArgs.isNotEmpty()) {
            header = header.replaceTypeParams(selfTypeArgs)
            checkTypeParams(immMapOf(), header)
        }

        val (argTypes, resType) = MTestParser.parseFunctionCall(call, scope)

        val match1 = header.matchParams(argTypes.size)
        val match2 = match1?.matchArgs(argTypes, resType)
        return toString(header, match2)
    }

    private fun checkTypeParams(selfTypeArgs: Map<M_TypeParam, M_TypeSet>, header: M_FunctionHeader) {
        val allTypeParams = selfTypeArgs.keys + header.typeParams
        for (typeParam in header.typeParams) {
            for (subTypeParam in typeParam.bounds.getTypeParams()) {
                check(subTypeParam in allTypeParams) { "$subTypeParam $header $allTypeParams" }
            }
        }
        for (type in header.params.map { it.type } + header.resultType) {
            for (subTypeParam in type.getTypeParams()) {
                check(subTypeParam in allTypeParams) { "$subTypeParam $header $allTypeParams" }
            }
        }
    }
}
