/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import kotlin.test.Test

internal class MTypeTupleTest: BaseMTypeTest() {
    @Test fun testConstruct() {
        chkConstruct("(int)", "(int)")
        chkConstruct("(int,real)", "(int,real)")
        chkConstruct("(a:int,real)", "(a:int,real)")
        chkConstruct("(int,b:real)", "(int,b:real)")
        chkConstruct("(a:int,b:real)", "(a:int,b:real)")
    }

    @Test fun testIsSuperTypeOf() {
        chkIsSuperTypeOfTrue("(int,real)", "(int,real)", "(int32,real)", "(int,real32)", "(int32,real32)")
        chkIsSuperTypeOfFalse("(int,real)", "(num,real)", "(int,num)", "(int,int32)", "(real32,real)")
        chkIsSuperTypeOfFalse("(int,real)", "(int)", "(real)", "(int,real,int)", "(int,real,real)")

        chkIsSuperTypeOfTrue("(a:int,b:real)", "(a:int,b:real)", "(a:int32,b:real)", "(a:int,b:real32)", "(a:int32,b:real32)")
        chkIsSuperTypeOfFalse("(a:int,b:real)", "(a:num,b:real)", "(a:int,b:num)", "(a:int,b:int32)", "(a:real32,b:real)")
        chkIsSuperTypeOfFalse("(a:int,b:real)", "(a:int,real)", "(a:int32,real)", "(a:int,real32)", "(a:int32,real32)")
        chkIsSuperTypeOfFalse("(a:int,b:real)", "(int,b:real)", "(int32,b:real)", "(int,b:real32)", "(int32,b:real32)")
        chkIsSuperTypeOfFalse("(a:int,b:real)", "(int,real)", "(int32,real)", "(int,real32)", "(int32,real32)")

        chkIsSuperTypeOfTrue("(a:int,real)", "(a:int,real)", "(a:int32,real)", "(a:int,real32)", "(a:int32,real32)")
        chkIsSuperTypeOfFalse("(a:int,real)", "(a:int,b:real)", "(a:int32,b:real)", "(a:int,b:real32)", "(a:int32,b:real32)")
        chkIsSuperTypeOfFalse("(a:int,real)", "(int,b:real)", "(int32,b:real)", "(int,b:real32)", "(int32,b:real32)")
        chkIsSuperTypeOfFalse("(a:int,real)", "(int,real)", "(int32,real)", "(int,real32)", "(int32,real32)")

        chkIsSuperTypeOfFalse("(int,real)", "(a:int,b:real)", "(a:int32,b:real)", "(a:int,b:real32)", "(a:int32,b:real32)")
        chkIsSuperTypeOfFalse("(int,real)", "(a:int,real)", "(a:int32,real)", "(a:int,real32)", "(a:int32,real32)")
        chkIsSuperTypeOfFalse("(int,real)", "(int,b:real)", "(int32,b:real)", "(int,b:real32)", "(int32,b:real32)")
    }

    @Test fun testReplaceParams() {
        chkReplaceParamsRaw("(A,B)", "A=-int,B=real", "(int,real)")
        chkReplaceParamsRaw("(A,B)", "A=+int,B=real", "(anything,real)")
        chkReplaceParamsRaw("(A,B)", "A=*,B=real", "(anything,real)")
        chkReplaceParamsRaw("(A,B)", "A=int,B=-real", "(int,real)")
        chkReplaceParamsRaw("(A,B)", "A=int,B=+real", "(int,anything)")
        chkReplaceParamsRaw("(A,B)", "A=int,B=*", "(int,anything)")
    }

    @Test fun testReplaceParamsInOut() {
        chkReplaceParamsInOut("(A,B)", "A=-int,B=real", "(nothing,real)", "(int,real)")
        chkReplaceParamsInOut("(A,B)", "A=+int,B=real", "(int,real)", "(anything,real)")
        chkReplaceParamsInOut("(A,B)", "A=*,B=real", "(nothing,real)", "(anything,real)")
        chkReplaceParamsInOut("(A,B)", "A=int,B=-real", "(int,nothing)", "(int,real)")
        chkReplaceParamsInOut("(A,B)", "A=int,B=+real", "(int,real)", "(int,anything)")
        chkReplaceParamsInOut("(A,B)", "A=int,B=*", "(int,nothing)", "(int,anything)")
    }

    @Test fun testCommonSuperTypeTuple() {
        chkCommonSuperType("(int,real)", "(int,real)", "(int,real)")
        chkCommonSuperType("(int,real)", "(int,int)", "(int,num)")
        chkCommonSuperType("(int,real)", "(real,int)", "(num,num)")
        chkCommonSuperType("(int?,real)", "(real,int)", "(num?,num)")
        chkCommonSuperType("(int,real?)", "(real,int)", "(num,num?)")
        chkCommonSuperType("(int?,real)", "(real,int?)", "(num?,num?)")
        chkCommonSuperType("(int,real)", "(int,str)", "(int,anything)")
    }

    @Test fun testCommonSubTypeTuple() {
        chkCommonSubType("(int,real)", "(int,real)", "(int,real)")
        chkCommonSubType("(int32,real)", "(int,real)", "(int32,real)")
        chkCommonSubType("(int,real64)", "(int,real)", "(int,real64)")
        chkCommonSubType("(int32,real64)", "(int,real)", "(int32,real64)")
        chkCommonSubType("(int32,real)", "(int,real64)", "(int32,real64)")
        chkCommonSubType("(int32,real)", "(int64,real)", "n/a")
        chkCommonSubType("(int,real32)", "(int,real64)", "n/a")
    }
}
