/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import net.postchain.rell.base.mtype.utils.MTestParser
import org.junit.Test
import kotlin.test.assertEquals

class MTypeParamsTest: BaseMTypeTest() {
    @Test fun testParamBoundsSimple() {
        scopeB.typeDef("entry<T:-int>")

        chkConstruct("entry<num>", "TE:param_bounds:entry:T:-int:num")
        chkConstruct("entry<int>", "entry<int>")
        chkConstruct("entry<int32>", "entry<int32>")
        chkConstruct("entry<int64>", "entry<int64>")
        chkConstruct("entry<real>", "TE:param_bounds:entry:T:-int:real")
        chkConstruct("entry<real32>", "TE:param_bounds:entry:T:-int:real32")
        chkConstruct("entry<real64>", "TE:param_bounds:entry:T:-int:real64")
        chkConstruct("entry<str>", "TE:param_bounds:entry:T:-int:str")
    }

    @Test fun testParamBoundsWild() {
        scopeB.typeDef("entry<T:-int>")

        chkConstruct("entry<-num>", "TE:param_bounds:entry:T:-int:-num")
        chkConstruct("entry<-int>", "entry<-int>")
        chkConstruct("entry<-int32>", "entry<-int32>")
        chkConstruct("entry<-int64>", "entry<-int64>")
        chkConstruct("entry<-real>", "TE:param_bounds:entry:T:-int:-real")

        chkConstruct("entry<+num>", "TE:param_bounds:entry:T:-int:+num")
        chkConstruct("entry<+int>", "TE:param_bounds:entry:T:-int:+int")
        chkConstruct("entry<+int32>", "TE:param_bounds:entry:T:-int:+int32")
        chkConstruct("entry<+real>", "TE:param_bounds:entry:T:-int:+real")

        chkConstruct("entry<*>", "entry<*>")
    }

    @Test fun testParamBoundsCap() {
        scopeB.typeDef("entry<T:-int>")

        chkConstruct("entry<CAP<-num>>", "TE:param_bounds:entry:T:-int:CAP<-num>")
        chkConstruct("entry<CAP<-int>>", "entry<CAP<-int>>")
        chkConstruct("entry<CAP<-int32>>", "entry<CAP<-int32>>")
        chkConstruct("entry<CAP<-real>>", "TE:param_bounds:entry:T:-int:CAP<-real>")

        chkConstruct("entry<CAP<+num>>", "TE:param_bounds:entry:T:-int:CAP<+num>")
        chkConstruct("entry<CAP<+int>>", "TE:param_bounds:entry:T:-int:CAP<+int>")
        chkConstruct("entry<CAP<+int32>>", "TE:param_bounds:entry:T:-int:CAP<+int32>")
        chkConstruct("entry<CAP<+real>>", "TE:param_bounds:entry:T:-int:CAP<+real>")

        // Maybe shall work, consider supporting.
        chkConstruct("entry<CAP<*>>", "TE:param_bounds:entry:T:-int:CAP<*>")
    }

    @Test fun testParamBoundsParam() {
        scopeB.typeDef("entry<T:-int>")

        chkParamBoundsParam("A:-int", "entry<A>")
        chkParamBoundsParam("A:-int32", "entry<A>")
        chkParamBoundsParam("A:-int64", "entry<A>")
        chkParamBoundsParam("A:-num", "TE:param_bounds:entry:T:-int:A")
        chkParamBoundsParam("A:-real", "TE:param_bounds:entry:T:-int:A")

        chkParamBoundsParam("A:+num", "TE:param_bounds:entry:T:-int:A")
        chkParamBoundsParam("A:+int", "TE:param_bounds:entry:T:-int:A")
        chkParamBoundsParam("A:+int32", "TE:param_bounds:entry:T:-int:A")
        chkParamBoundsParam("A:+real", "TE:param_bounds:entry:T:-int:A")

        chkParamBoundsParam("A", "TE:param_bounds:entry:T:-int:A")
    }

    private fun chkParamBoundsParam(paramDef: String, exp: String) {
        val subScopeB = scopeB.copy()
        subScopeB.paramDef(paramDef)
        chkConstruct("entry<A>", exp, subScopeB.build())
    }

    @Test fun testParamBoundsParent() {
        scopeB.typeDef("parent1<T:-int>")
        scopeB.typeDef("parent2<T:+int>")

        chkTypeDef("child<T>: parent1<T>", "TE:param_bounds:parent1:T:-int:T")
        chkTypeDef("child<T:-num>: parent1<T>", "TE:param_bounds:parent1:T:-int:T")
        chkTypeDef("child<T:-int>: parent1<T>", "OK")
        chkTypeDef("child<T:-int32>: parent1<T>", "OK")
        chkTypeDef("child<T:+num>: parent1<T>", "TE:param_bounds:parent1:T:-int:T")
        chkTypeDef("child<T:+int>: parent1<T>", "TE:param_bounds:parent1:T:-int:T")
        chkTypeDef("child<T:+int32>: parent1<T>", "TE:param_bounds:parent1:T:-int:T")

        chkTypeDef("child<T>: parent2<T>", "TE:param_bounds:parent2:T:+int:T")
        chkTypeDef("child<T:-num>: parent2<T>", "TE:param_bounds:parent2:T:+int:T")
        chkTypeDef("child<T:-int>: parent2<T>", "TE:param_bounds:parent2:T:+int:T")
        chkTypeDef("child<T:-int32>: parent2<T>", "TE:param_bounds:parent2:T:+int:T")
        chkTypeDef("child<T:+num>: parent2<T>", "OK")
        chkTypeDef("child<T:+int>: parent2<T>", "OK")
        chkTypeDef("child<T:+int32>: parent2<T>", "TE:param_bounds:parent2:T:+int:T")
    }

    private fun chkTypeDef(code: String, exp: String) {
        val scope = scopeB.build()
        val act = calcStr(
            { MTestParser.parseTypeDef(code, scope) },
            { "OK" },
        )
        assertEquals(exp, act)
    }

    @Test fun testParamBoundsParent2() {
        scopeB.typeDef("parent<T:-int>")
        scopeB.typeDef("child1<T:-int>: parent<T>")
        scopeB.typeDef("child2<T:-int32>: parent<T>")

        chkParent("child1<int>", "parent<int>")
        chkParent("child1<int32>", "parent<int32>")
        chkParent("child1<int64>", "parent<int64>")
        chkParent("child1<num>", "TE:param_bounds:child1:T:-int:num")
        chkParent("child1<real>", "TE:param_bounds:child1:T:-int:real")

        chkParent("child1<*>", "parent<*>")
        chkParent("child1<-int>", "parent<-int>")
        chkParent("child1<-int32>", "parent<-int32>")
        chkParent("child1<-int64>", "parent<-int64>")
        chkParent("child1<-num>", "TE:param_bounds:child1:T:-int:-num")
        chkParent("child1<+int>", "TE:param_bounds:child1:T:-int:+int")

        chkParent("child2<int32>", "parent<int32>")
        chkParent("child2<int>", "TE:param_bounds:child2:T:-int32:int")
        chkParent("child2<int64>", "TE:param_bounds:child2:T:-int32:int64")
        chkParent("child2<num>", "TE:param_bounds:child2:T:-int32:num")
        chkParent("child2<real>", "TE:param_bounds:child2:T:-int32:real")
    }

    @Test fun testParamBoundsReplaceParamsRaw() {
        scopeB.typeDef("entry<T:-int>")
        scopeB.paramDef("A:-int")

        chkReplaceParamsRaw("entry<A>", "A=num", "TE:param_bounds:entry:T:-int:num")
        chkReplaceParamsRaw("entry<A>", "A=int", "entry<int>")
        chkReplaceParamsRaw("entry<A>", "A=int32", "entry<int32>")
        chkReplaceParamsRaw("entry<A>", "A=int64", "entry<int64>")
        chkReplaceParamsRaw("entry<A>", "A=real", "TE:param_bounds:entry:T:-int:real")

        chkReplaceParamsRaw("entry<A>", "A=-num", "TE:param_bounds:entry:T:-int:-num")
        chkReplaceParamsRaw("entry<A>", "A=-int", "entry<-int>")
        chkReplaceParamsRaw("entry<A>", "A=-int32", "entry<-int32>")
        chkReplaceParamsRaw("entry<A>", "A=-int64", "entry<-int64>")
        chkReplaceParamsRaw("entry<A>", "A=-real", "TE:param_bounds:entry:T:-int:-real")

        chkReplaceParamsRaw("entry<A>", "A=+int", "TE:param_bounds:entry:T:-int:+int")

        chkReplaceParamsRaw("entry<A>", "A=*", "entry<*>")
    }

    @Test fun testParamBoundsReplaceParamsCap() {
        scopeB.typeDef("entry<T:-int>")
        scopeB.paramDef("A:-int")

        chkReplaceParamsCap("entry<A>", "A=-num", "TE:param_bounds:entry:T:-int:CAP<-num>")
        chkReplaceParamsCap("entry<A>", "A=-int", "entry<CAP<-int>>")
        chkReplaceParamsCap("entry<A>", "A=-int32", "entry<CAP<-int32>>")
        chkReplaceParamsCap("entry<A>", "A=-int64", "entry<CAP<-int64>>")
        chkReplaceParamsCap("entry<A>", "A=-real", "TE:param_bounds:entry:T:-int:CAP<-real>")

        chkReplaceParamsCap("entry<A>", "A=+int", "TE:param_bounds:entry:T:-int:CAP<+int>")

        chkReplaceParamsCap("entry<A>", "A=*", "TE:param_bounds:entry:T:-int:CAP<*>")
    }

    @Test fun testReplaceParamsSimple() {
        chkReplaceParamsRaw("int", "T=real", "int")
        chkReplaceParamsRaw("A", "T=real", "A")
        chkReplaceParamsRaw("T", "T=real", "real")

        chkReplaceParamsRaw("T", "T=-int", "CAP<-int>")
        chkReplaceParamsRaw("T", "T=+int", "CAP<+int>")
        chkReplaceParamsRaw("T", "T=*", "CAP<*>")
    }

    @Test fun testReplaceParamsGeneric() {
        chkReplaceParamsRaw("list<T>", "T=int", "list<int>")
        chkReplaceParamsRaw("list<-T>", "T=int", "list<-int>")
        chkReplaceParamsRaw("list<+T>", "T=int", "list<+int>")

        chkReplaceParamsRaw("list<T>", "T=-int", "list<-int>")
        chkReplaceParamsRaw("list<T>", "T=+int", "list<+int>")

        chkReplaceParamsRaw("list<-T>", "T=-int", "list<-int>")
        chkReplaceParamsRaw("list<-T>", "T=+int", "list<*>")
        chkReplaceParamsRaw("list<+T>", "T=-int", "list<*>")
        chkReplaceParamsRaw("list<+T>", "T=+int", "list<+int>")
    }

    @Test fun testReplaceParamsGenericVariance() {
        chkReplaceParamsRaw("consumer<T>", "T=int", "consumer<int>")
        chkReplaceParamsRaw("consumer<T>", "T=-int", "consumer<nothing>")
        chkReplaceParamsRaw("consumer<T>", "T=+int", "consumer<int>")
        chkReplaceParamsRaw("consumer<T>", "T=*", "consumer<nothing>")

        chkReplaceParamsRaw("supplier<T>", "T=int", "supplier<int>")
        chkReplaceParamsRaw("supplier<T>", "T=-int", "supplier<int>")
        chkReplaceParamsRaw("supplier<T>", "T=+int", "supplier<anything>")
        chkReplaceParamsRaw("supplier<T>", "T=*", "supplier<anything>")
    }

    @Test fun testReplaceParamsCapSimple() {
        chkReplaceParamsCap("int", "T=real", "int")
        chkReplaceParamsCap("A", "T=real", "A")
        chkReplaceParamsCap("T", "T=real", "real")

        chkReplaceParamsCap("T", "T=-int", "CAP<-int>")
        chkReplaceParamsCap("T", "T=+int", "CAP<+int>")
        chkReplaceParamsCap("T", "T=*", "CAP<*>")
    }

    @Test fun testReplaceParamsCapGeneric() {
        chkReplaceParamsCap("data<T>", "T=-int", "data<CAP<-int>>")
        chkReplaceParamsCap("data<T>", "T=+int", "data<CAP<+int>>")
        chkReplaceParamsCap("data<T>", "T=*", "data<CAP<*>>")
        chkReplaceParamsCap("data<-T>", "T=-int", "data<-CAP<-int>>")
        chkReplaceParamsCap("data<-T>", "T=+int", "data<-CAP<+int>>")
        chkReplaceParamsCap("data<-T>", "T=*", "data<-CAP<*>>")
        chkReplaceParamsCap("data<+T>", "T=-int", "data<+CAP<-int>>")
        chkReplaceParamsCap("data<+T>", "T=+int", "data<+CAP<+int>>")
        chkReplaceParamsCap("data<+T>", "T=*", "data<+CAP<*>>")

        chkReplaceParamsCap("list<data<T>>", "T=-int", "list<data<CAP<-int>>>")
        chkReplaceParamsCap("list<data<T>>", "T=+int", "list<data<CAP<+int>>>")
        chkReplaceParamsCap("list<data<T>>", "T=*", "list<data<CAP<*>>>")
        chkReplaceParamsCap("list<data<-T>>", "T=-int", "list<data<-CAP<-int>>>")
        chkReplaceParamsCap("list<data<-T>>", "T=+int", "list<data<-CAP<+int>>>")
        chkReplaceParamsCap("list<data<-T>>", "T=*", "list<data<-CAP<*>>>")
        chkReplaceParamsCap("list<data<+T>>", "T=-int", "list<data<+CAP<-int>>>")
        chkReplaceParamsCap("list<data<+T>>", "T=+int", "list<data<+CAP<+int>>>")
        chkReplaceParamsCap("list<data<+T>>", "T=*", "list<data<+CAP<*>>>")

        chkReplaceParamsCap("list<-data<T>>", "T=-int", "list<-data<CAP<-int>>>")
        chkReplaceParamsCap("list<-data<T>>", "T=+int", "list<-data<CAP<+int>>>")
        chkReplaceParamsCap("list<-data<T>>", "T=*", "list<-data<CAP<*>>>")
        chkReplaceParamsCap("list<-data<-T>>", "T=-int", "list<-data<-CAP<-int>>>")
        chkReplaceParamsCap("list<-data<-T>>", "T=+int", "list<-data<-CAP<+int>>>")
        chkReplaceParamsCap("list<-data<-T>>", "T=*", "list<-data<-CAP<*>>>")
        chkReplaceParamsCap("list<-data<+T>>", "T=-int", "list<-data<+CAP<-int>>>")
        chkReplaceParamsCap("list<-data<+T>>", "T=+int", "list<-data<+CAP<+int>>>")
        chkReplaceParamsCap("list<-data<+T>>", "T=*", "list<-data<+CAP<*>>>")

        chkReplaceParamsCap("list<+data<T>>", "T=-int", "list<+data<CAP<-int>>>")
        chkReplaceParamsCap("list<+data<T>>", "T=+int", "list<+data<CAP<+int>>>")
        chkReplaceParamsCap("list<+data<T>>", "T=*", "list<+data<CAP<*>>>")
        chkReplaceParamsCap("list<+data<-T>>", "T=-int", "list<+data<-CAP<-int>>>")
        chkReplaceParamsCap("list<+data<-T>>", "T=+int", "list<+data<-CAP<+int>>>")
        chkReplaceParamsCap("list<+data<-T>>", "T=*", "list<+data<-CAP<*>>>")
        chkReplaceParamsCap("list<+data<+T>>", "T=-int", "list<+data<+CAP<-int>>>")
        chkReplaceParamsCap("list<+data<+T>>", "T=+int", "list<+data<+CAP<+int>>>")
        chkReplaceParamsCap("list<+data<+T>>", "T=*", "list<+data<+CAP<*>>>")

        chkReplaceParamsCap("list<T>", "T=data<-int>", "list<data<-int>>")
        chkReplaceParamsCap("list<T>", "T=data<+int>", "list<data<+int>>")
        chkReplaceParamsCap("list<T>", "T=data<*>", "list<data<*>>")
        chkReplaceParamsCap("list<-T>", "T=data<-int>", "list<-data<-int>>")
        chkReplaceParamsCap("list<-T>", "T=data<+int>", "list<-data<+int>>")
        chkReplaceParamsCap("list<-T>", "T=data<*>", "list<-data<*>>")
        chkReplaceParamsCap("list<+T>", "T=data<-int>", "list<+data<-int>>")
        chkReplaceParamsCap("list<+T>", "T=data<+int>", "list<+data<+int>>")
        chkReplaceParamsCap("list<+T>", "T=data<*>", "list<+data<*>>")

        chkReplaceParamsCap("list<T>", "T=-data<-int>", "list<CAP<-data<-int>>>")
        chkReplaceParamsCap("list<T>", "T=-data<+int>", "list<CAP<-data<+int>>>")
        chkReplaceParamsCap("list<T>", "T=+data<-int>", "list<CAP<+data<-int>>>")
        chkReplaceParamsCap("list<T>", "T=+data<+int>", "list<CAP<+data<+int>>>")
        chkReplaceParamsCap("list<-T>", "T=-data<-int>", "list<-CAP<-data<-int>>>")
        chkReplaceParamsCap("list<-T>", "T=-data<+int>", "list<-CAP<-data<+int>>>")
        chkReplaceParamsCap("list<-T>", "T=+data<-int>", "list<-CAP<+data<-int>>>")
        chkReplaceParamsCap("list<-T>", "T=+data<+int>", "list<-CAP<+data<+int>>>")
        chkReplaceParamsCap("list<+T>", "T=-data<-int>", "list<+CAP<-data<-int>>>")
        chkReplaceParamsCap("list<+T>", "T=-data<+int>", "list<+CAP<-data<+int>>>")
        chkReplaceParamsCap("list<+T>", "T=+data<-int>", "list<+CAP<+data<-int>>>")
        chkReplaceParamsCap("list<+T>", "T=+data<+int>", "list<+CAP<+data<+int>>>")
    }

    @Test fun testReplaceParamsCapFunction() {
        chkReplaceParamsCap("(A)->B", "A=int,B=real", "(int)->real")
        chkReplaceParamsCap("(A)->B", "A=-int,B=real", "(CAP<-int>)->real")
        chkReplaceParamsCap("(A)->B", "A=+int,B=real", "(CAP<+int>)->real")
        chkReplaceParamsCap("(A)->B", "A=*,B=real", "(CAP<*>)->real")
        chkReplaceParamsCap("(A)->B", "A=int,B=-real", "(int)->CAP<-real>")
        chkReplaceParamsCap("(A)->B", "A=int,B=+real", "(int)->CAP<+real>")
        chkReplaceParamsCap("(A)->B", "A=int,B=*", "(int)->CAP<*>")
    }

    @Test fun testReplaceParamsInOutBasic() {
        chkReplaceParamsInOut("T", "T=int", "int", "int")
        chkReplaceParamsInOut("T", "T=data<int>", "data<int>", "data<int>")
        chkReplaceParamsInOut("T", "T=data<-int>", "data<-int>", "data<-int>")
        chkReplaceParamsInOut("T", "T=data<+int>", "data<+int>", "data<+int>")
        chkReplaceParamsInOut("T", "T=data<*>", "data<*>", "data<*>")

        chkReplaceParamsInOut("list<T>", "T=int", "list<int>", "list<int>")
        chkReplaceParamsInOut("list<T>", "T=data<int>", "list<data<int>>", "list<data<int>>")
        chkReplaceParamsInOut("list<T>", "T=data<-int>", "list<data<-int>>", "list<data<-int>>")
        chkReplaceParamsInOut("list<T>", "T=data<+int>", "list<data<+int>>", "list<data<+int>>")
        chkReplaceParamsInOut("list<T>", "T=data<*>", "list<data<*>>", "list<data<*>>")

        chkReplaceParamsInOut("list<-T>", "T=int", "list<-int>", "list<-int>")
        chkReplaceParamsInOut("list<-T>", "T=data<int>", "list<-data<int>>", "list<-data<int>>")
        chkReplaceParamsInOut("list<-T>", "T=data<-int>", "list<-data<-int>>", "list<-data<-int>>")
        chkReplaceParamsInOut("list<-T>", "T=data<+int>", "list<-data<+int>>", "list<-data<+int>>")
        chkReplaceParamsInOut("list<-T>", "T=data<*>", "list<-data<*>>", "list<-data<*>>")

        chkReplaceParamsInOut("list<+T>", "T=int", "list<+int>", "list<+int>")
        chkReplaceParamsInOut("list<+T>", "T=data<int>", "list<+data<int>>", "list<+data<int>>")
        chkReplaceParamsInOut("list<+T>", "T=data<-int>", "list<+data<-int>>", "list<+data<-int>>")
        chkReplaceParamsInOut("list<+T>", "T=data<+int>", "list<+data<+int>>", "list<+data<+int>>")
        chkReplaceParamsInOut("list<+T>", "T=data<*>", "list<+data<*>>", "list<+data<*>>")
    }

    @Test fun testReplaceParamsInOutWild() {
        chkReplaceParamsInOut("T", "T=-int", "nothing", "int")
        chkReplaceParamsInOut("T", "T=+int", "int", "anything")
        chkReplaceParamsInOut("T", "T=*", "nothing", "anything")

        chkReplaceParamsInOut("data<T>", "T=-int", "nothing", "data<-int>")
        chkReplaceParamsInOut("data<T>", "T=+int", "nothing", "data<+int>")
        chkReplaceParamsInOut("data<T>", "T=*", "nothing", "data<*>")
        chkReplaceParamsInOut("data<-T>", "T=-int", "data<nothing>", "data<-int>")
        chkReplaceParamsInOut("data<-T>", "T=+int", "data<-int>", "data<*>")
        chkReplaceParamsInOut("data<-T>", "T=*", "data<nothing>", "data<*>")
        chkReplaceParamsInOut("data<+T>", "T=-int", "data<+int>", "data<*>")
        chkReplaceParamsInOut("data<+T>", "T=+int", "data<anything>", "data<+int>")
        chkReplaceParamsInOut("data<+T>", "T=*", "data<anything>", "data<*>")
    }

    @Test fun testReplaceParamsInOutWildVariance() {
        chkReplaceParamsInOut("consumer<T>", "T=-int", "consumer<int>", "consumer<nothing>")
        chkReplaceParamsInOut("consumer<T>", "T=+int", "consumer<anything>", "consumer<int>")
        chkReplaceParamsInOut("consumer<T>", "T=*", "consumer<anything>", "consumer<nothing>")

        chkReplaceParamsInOut("supplier<T>", "T=-int", "supplier<nothing>", "supplier<int>")
        chkReplaceParamsInOut("supplier<T>", "T=+int", "supplier<int>", "supplier<anything>")
        chkReplaceParamsInOut("supplier<T>", "T=*", "supplier<nothing>", "supplier<anything>")
    }

    @Test fun testReplaceParamsInOutWildComplexNoVariance() {
        chkReplaceParamsInOut("list<data<T>>", "T=-int", "nothing", "list<-data<-int>>")
        chkReplaceParamsInOut("list<data<T>>", "T=+int", "nothing", "list<-data<+int>>")
        chkReplaceParamsInOut("list<data<T>>", "T=*", "nothing", "list<-data<*>>")
        chkReplaceParamsInOut("list<data<-T>>", "T=-int", "nothing", "list<-data<-int>>")
        chkReplaceParamsInOut("list<data<-T>>", "T=+int", "nothing", "list<-data<*>>")
        chkReplaceParamsInOut("list<data<-T>>", "T=*", "nothing", "list<-data<*>>")
        chkReplaceParamsInOut("list<data<+T>>", "T=-int", "nothing", "list<-data<*>>")
        chkReplaceParamsInOut("list<data<+T>>", "T=+int", "nothing", "list<-data<+int>>")
        chkReplaceParamsInOut("list<data<+T>>", "T=*", "nothing", "list<-data<*>>")

        chkReplaceParamsInOut("list<-data<T>>", "T=-int", "list<nothing>", "list<-data<-int>>")
        chkReplaceParamsInOut("list<-data<T>>", "T=+int", "list<nothing>", "list<-data<+int>>")
        chkReplaceParamsInOut("list<-data<T>>", "T=*", "list<nothing>", "list<-data<*>>")
        chkReplaceParamsInOut("list<-data<-T>>", "T=-int", "list<-data<nothing>>", "list<-data<-int>>")
        chkReplaceParamsInOut("list<-data<-T>>", "T=+int", "list<-data<-int>>", "list<-data<*>>")
        chkReplaceParamsInOut("list<-data<-T>>", "T=*", "list<-data<nothing>>", "list<-data<*>>")
        chkReplaceParamsInOut("list<-data<+T>>", "T=-int", "list<-data<+int>>", "list<-data<*>>")
        chkReplaceParamsInOut("list<-data<+T>>", "T=+int", "list<-data<anything>>", "list<-data<+int>>")
        chkReplaceParamsInOut("list<-data<+T>>", "T=*", "list<-data<anything>>", "list<-data<*>>")

        chkReplaceParamsInOut("list<+data<T>>", "T=-int", "list<+data<-int>>", "list<*>")
        chkReplaceParamsInOut("list<+data<T>>", "T=+int", "list<+data<+int>>", "list<*>")
        chkReplaceParamsInOut("list<+data<T>>", "T=*", "list<+data<*>>", "list<*>")
        chkReplaceParamsInOut("list<+data<-T>>", "T=-int", "list<+data<-int>>", "list<+data<nothing>>")
        chkReplaceParamsInOut("list<+data<-T>>", "T=+int", "list<+data<*>>", "list<+data<-int>>")
        chkReplaceParamsInOut("list<+data<-T>>", "T=*", "list<+data<*>>", "list<+data<nothing>>")
        chkReplaceParamsInOut("list<+data<+T>>", "T=-int", "list<+data<*>>", "list<+data<+int>>")
        chkReplaceParamsInOut("list<+data<+T>>", "T=+int", "list<+data<+int>>", "list<+data<anything>>")
        chkReplaceParamsInOut("list<+data<+T>>", "T=*", "list<+data<*>>", "list<+data<anything>>")
    }

    @Test fun testReplaceParamsInOutWildComplexVariance() {
        chkReplaceParamsInOut("list<consumer<T>>", "T=-int", "nothing", "list<-consumer<nothing>>")
        chkReplaceParamsInOut("list<consumer<T>>", "T=+int", "nothing", "list<-consumer<int>>")
        chkReplaceParamsInOut("list<consumer<T>>", "T=*", "nothing", "list<-consumer<nothing>>")
        chkReplaceParamsInOut("list<-consumer<T>>", "T=-int", "list<-consumer<int>>", "list<-consumer<nothing>>")
        chkReplaceParamsInOut("list<-consumer<T>>", "T=+int", "list<-consumer<anything>>", "list<-consumer<int>>")
        chkReplaceParamsInOut("list<-consumer<T>>", "T=*", "list<-consumer<anything>>", "list<-consumer<nothing>>")
        chkReplaceParamsInOut("list<+consumer<T>>", "T=-int", "list<+consumer<nothing>>", "list<+consumer<int>>")
        chkReplaceParamsInOut("list<+consumer<T>>", "T=+int", "list<+consumer<int>>", "list<+consumer<anything>>")
        chkReplaceParamsInOut("list<+consumer<T>>", "T=*", "list<+consumer<nothing>>", "list<+consumer<anything>>")

        chkReplaceParamsInOut("list<supplier<T>>", "T=-int", "nothing", "list<-supplier<int>>")
        chkReplaceParamsInOut("list<supplier<T>>", "T=+int", "nothing", "list<-supplier<anything>>")
        chkReplaceParamsInOut("list<supplier<T>>", "T=*", "nothing", "list<-supplier<anything>>")
        chkReplaceParamsInOut("list<-supplier<T>>", "T=-int", "list<-supplier<nothing>>", "list<-supplier<int>>")
        chkReplaceParamsInOut("list<-supplier<T>>", "T=+int", "list<-supplier<int>>", "list<-supplier<anything>>")
        chkReplaceParamsInOut("list<-supplier<T>>", "T=*", "list<-supplier<nothing>>", "list<-supplier<anything>>")
        chkReplaceParamsInOut("list<+supplier<T>>", "T=-int", "list<+supplier<int>>", "list<+supplier<nothing>>")
        chkReplaceParamsInOut("list<+supplier<T>>", "T=+int", "list<+supplier<anything>>", "list<+supplier<int>>")
        chkReplaceParamsInOut("list<+supplier<T>>", "T=*", "list<+supplier<anything>>", "list<+supplier<nothing>>")
    }
}
