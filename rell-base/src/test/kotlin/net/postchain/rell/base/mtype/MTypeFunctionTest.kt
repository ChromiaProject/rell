/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import kotlin.test.Test

internal class MTypeFunctionTest: BaseMTypeTest() {
    @Test fun testFunctionConstruct() {
        chkConstruct("(int)->real", "(int)->real")
        chkConstruct("(num,int,real)->str", "(num,int,real)->str")
        chkConstruct("()->int", "()->int")
    }

    @Test fun testFunctionIsSuperTypeOf() {
        chkIsSuperTypeOfSub("(int)->real", "(int)->real32", "(int)->real64", "(num)->real", "(num)->real32")
        chkIsSuperTypeOfOther("(int)->real", "(int)->int", "(real)->int", "(real)->real")
        chkIsSuperTypeOfFalse("(int)->real", "()->real", "(int,int)->real", "(int,real)->real")
    }

    @Test fun testReplaceParamsFunction() {
        chkReplaceParamsRaw("(A)->B", "A=int,B=real", "(int)->real")

        chkReplaceParamsRaw("(A)->B", "A=list<int>,B=set<real>", "(list<int>)->set<real>")
        chkReplaceParamsRaw("(A)->B", "A=list<-int>,B=real", "(list<-int>)->real")
        chkReplaceParamsRaw("(A)->B", "A=list<+int>,B=real", "(list<+int>)->real")
        chkReplaceParamsRaw("(A)->B", "A=list<*>,B=real", "(list<*>)->real")
        chkReplaceParamsRaw("(A)->B", "A=int,B=set<-real>", "(int)->set<-real>")
        chkReplaceParamsRaw("(A)->B", "A=int,B=set<+real>", "(int)->set<+real>")
        chkReplaceParamsRaw("(A)->B", "A=int,B=set<*>", "(int)->set<*>")

        chkReplaceParamsRaw("(A)->B", "A=-int,B=real", "(nothing)->real")
        chkReplaceParamsRaw("(A)->B", "A=+int,B=real", "(int)->real")
        chkReplaceParamsRaw("(A)->B", "A=*,B=real", "(nothing)->real")
        chkReplaceParamsRaw("(A)->B", "A=int,B=-real", "(int)->real")
        chkReplaceParamsRaw("(A)->B", "A=int,B=+real", "(int)->anything")
        chkReplaceParamsRaw("(A)->B", "A=int,B=*", "(int)->anything")
    }

    @Test fun testCommonSuperTypeFunction() {
        chkCommonSuperType("(int,real)->int", "(int,real)->int", "(int,real)->int")
        chkCommonSuperType("(int,real)->num", "(int,real)->int", "(int,real)->num")
        chkCommonSuperType("(int,real)->int32", "(int,real)->int64", "(int,real)->int")
        chkCommonSuperType("(int,real)->int32", "(int,real)->real64", "(int,real)->num")
        chkCommonSuperType("(int,real)->int", "(int,real)->str", "(int,real)->anything")

        chkCommonSuperType("(int32,real)->int", "(int,real)->int", "(int32,real)->int")
        chkCommonSuperType("(int,real64)->int", "(int,real)->int", "(int,real64)->int")
        chkCommonSuperType("(int32,real)->int", "(int,real64)->int", "(int32,real64)->int")
        chkCommonSuperType("(int32,real)->real32", "(int,real64)->int64", "(int32,real64)->num")
        chkCommonSuperType("(int32,real)->int", "(int64,real)->int", "(nothing,real)->int")
    }

    @Test fun testCommonSubTypeFunction() {
        chkCommonSubType("(int,real)->int", "(int,real)->int", "(int,real)->int")
        chkCommonSubType("(int,real)->num", "(int,real)->int", "(int,real)->int")
        chkCommonSubType("(int,real)->num", "(int,real)->int32", "(int,real)->int32")
        chkCommonSubType("(int,real)->int", "(int,real)->real", "n/a")

        chkCommonSubType("(int32,real)->int", "(int,real)->int", "(int,real)->int")
        chkCommonSubType("(int,real64)->int", "(int,real)->int", "(int,real)->int")
        chkCommonSubType("(int32,real)->int", "(int,real64)->int", "(int,real)->int")
        chkCommonSubType("(int,real)->int", "(real,real)->int", "(num,real)->int")
        chkCommonSubType("(int32,real)->int", "(real64,real)->int", "(num,real)->int")
        chkCommonSubType("(int32,real64)->int", "(real32,int32)->int", "(num,num)->int")
        chkCommonSubType("(int32,real64)->num", "(real32,int32)->int", "(num,num)->int")
        chkCommonSubType("(int32,real64)->int", "(real32,int32)->real", "n/a")
    }
}
