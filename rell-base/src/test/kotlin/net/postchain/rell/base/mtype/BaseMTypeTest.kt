/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import net.postchain.rell.base.mtype.utils.MTestParser
import net.postchain.rell.base.mtype.utils.MTestScope
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

abstract class BaseMTypeTest {
    protected val scopeB: MTestScope.Builder by lazy {
        val b = MTestScope.Builder()
        initScope(b)
        b
    }

    protected open fun initScope(b: MTestScope.Builder) {
        MTestScope.initDefault(b)
    }

    protected fun <T> calcStr(toValue: () -> T, valueToString: (T) -> String): String {
        val value = try {
            toValue()
        } catch (e: M_TypeException) {
            return "TE:${e.code}"
        }
        return valueToString(value)
    }

    protected fun chkConstruct(code: String, exp: String, scope: MTestScope = scopeB.build()) {
        val act = calcStr(
            { MTestParser.parseType(code, scope) },
            { it.strCode() },
        )
        assertEquals(exp, act)
    }

    protected fun chkParent(type: String, exp: String) {
        val act = calcParent(type)
        assertEquals(exp, act)
    }

    private fun calcParent(type: String): String {
        val scope = scopeB.build()
        return calcStr(
            { MTestParser.parseType(type, scope) },
            { it.getParentType()?.strCode() ?: "-" },
        )
    }

    protected fun chkIsSuperTypeOfSelf(type: String) {
        val scope = scopeB.build()
        val mType = MTestParser.parseType(type, scope)
        assertEquals(true, mType.isSuperTypeOf(mType), mType.strCode())
    }

    protected fun chkIsSuperTypeOfSuper(type: String, vararg types: String, self: Boolean = true) {
        chkIsSuperTypeOfTypes(type, types.toList(), isSuper = false, isSub = true, self = self)
    }

    protected fun chkIsSuperTypeOfSub(type: String, vararg types: String, self: Boolean = true) {
        chkIsSuperTypeOfTypes(type, types.toList(), isSuper = true, isSub = false, self = self)
    }

    protected fun chkIsSuperTypeOfTrue(type: String, vararg types: String) {
        val scope = scopeB.build()
        val mType = MTestParser.parseType(type, scope)
        for (type2 in types) {
            val mType2 = MTestParser.parseType(type2, scope)
            assertTrue(mType.isSuperTypeOf(mType2), "$type:$type2")
        }
    }

    protected fun chkIsSuperTypeOfFalse(type: String, vararg types: String) {
        val scope = scopeB.build()
        val mType = MTestParser.parseType(type, scope)
        for (type2 in types) {
            val mType2 = MTestParser.parseType(type2, scope)
            assertFalse(mType.isSuperTypeOf(mType2), "$type:$type2")
        }
    }

    protected fun chkIsSuperTypeOfOther(type: String, vararg types: String, self: Boolean = true) {
        chkIsSuperTypeOfTypes(type, types.toList(), isSuper = false, isSub = false, self = self)
    }

    private fun chkIsSuperTypeOfTypes(type: String, types: List<String>, isSuper: Boolean, isSub: Boolean, self: Boolean) {
        if (self) {
            chkIsSuperTypeOf(type, type, true)
        }
        for (type2 in types) {
            chkIsSuperTypeOf(type, type2, isSuper)
            chkIsSuperTypeOf(type2, type, isSub)
        }
    }

    protected fun chkIsSuperTypeOf(type1: String, type2: String, exp: Boolean) {
        val scope = scopeB.build()
        val mType1 = MTestParser.parseType(type1, scope)
        val mType2 = MTestParser.parseType(type2, scope)
        assertEquals(exp, mType1.isSuperTypeOf(mType2), "$type1:$type2:$exp")
    }

    protected fun chkReplaceParamsRaw(type: String, args: String, exp: String) {
        chkReplaceParams0(type, args, false, exp)
    }

    protected fun chkReplaceParamsCap(type: String, args: String, exp: String) {
        chkReplaceParams0(type, args, true, exp)
    }

    private fun chkReplaceParams0(type: String, args: String, cap: Boolean, exp: String) {
        val scope = scopeB.build()
        val pType = MTestParser.parseTypeEx(type, scope)
        val subScopeB = scopeB.copy()
        for (param in pType.params) subScopeB.paramDef(param)
        val mType = pType.type
        val mArgs = MTestParser.parseTypeSetMap(args, scope = subScopeB.build())
        val act = calcReplaceParams(mType, mArgs, cap)
        assertEquals(exp, act)
    }

    private fun calcReplaceParams(mType: M_Type, args: Map<M_TypeParam, M_TypeSet>, cap: Boolean): String {
        val mResType = mType.replaceParams(args, cap).type()
        return calcStr(
            { mResType.validate() },
            { mResType.strCode() },
        )
    }

    protected fun chkReplaceParamsInOut(type: String, args: String, expIn: String, expOut: String) {
        val pType = MTestParser.parseTypeEx(type, scope = scopeB.build())
        val subScopeB = scopeB.copy()
        for (param in pType.params) subScopeB.paramDef(param)
        val mArgs = MTestParser.parseTypeSetMap(args, scope = subScopeB.build())
        val mType = pType.type
        val mTypeIn = mType.replaceParamsIn(mArgs)
        val mTypeOut = mType.replaceParamsOut(mArgs)
        assertEquals(expIn, mTypeIn.strCode())
        assertEquals(expOut, mTypeOut.strCode())
    }

    protected fun chkCommonSuperType(type1: String, type2: String, exp: String) {
        chkCommonSubSuperType(type1, type2, false, exp)
    }

    protected fun chkCommonSubType(type1: String, type2: String, exp: String) {
        chkCommonSubSuperType(type1, type2, true, exp)
    }

    private fun chkCommonSubSuperType(type1: String, type2: String, sub: Boolean, exp: String) {
        val scope = scopeB.build()
        val mType1 = MTestParser.parseType(type1, scope)
        val mType2 = MTestParser.parseType(type2, scope)
        val mResType1 = if (sub) mType1.getCommonSubType(mType2) else mType1.getCommonSuperType(mType2)
        val mResType2 = if (sub) mType2.getCommonSubType(mType1) else mType2.getCommonSuperType(mType1)
        assertEquals(exp, mResType1?.strCode() ?: "n/a")
        assertEquals(exp, mResType2?.strCode() ?: "n/a")
        assertEquals(mResType1, mResType2)
    }
}
