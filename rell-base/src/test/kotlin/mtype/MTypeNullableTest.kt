/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import net.postchain.rell.base.mtype.utils.MTestScope
import kotlin.test.Test

internal class MTypeNullableTest: BaseMTypeTest() {
    override fun initScope(b: MTestScope.Builder) {
        super.initScope(b)
        MTestScope.initRellPrimitives(b)
    }

    @Test fun testConstruct() {
        chkConstruct("int?", "int?")
        chkConstruct("T?", "T?")

        chkConstruct("data<int>?", "data<int>?")
        chkConstruct("data<int?>", "data<int?>")
        chkConstruct("data<int?>?", "data<int?>?")

        chkConstruct("null", "null")
        chkConstruct("null?", "null")
        chkConstruct("anything?", "anything")
        chkConstruct("nothing?", "null")
        chkConstruct("any?", "anything")
    }

    @Test fun testTypeSet() {
        chkConstruct("data<+null>", "data<+null>")
        chkConstruct("data<-null>", "data<null>")
    }

    @Test fun testIsSuperTypeOf() {
        chkIsSuperTypeOfTrue("int?", "nothing", "null", "int", "int32", "int64")
        chkIsSuperTypeOfTrue("int?", "int32?", "int64?")
        chkIsSuperTypeOfFalse("int?", "num", "num?", "real", "real32", "real?", "real32?", "str", "str?")
        chkIsSuperTypeOfTrue("anything", "int?")
    }

    @Test fun testIsSuperTypeOfComplex() {
        scopeB.typeDef("two<-A,-B>")

        chkIsSuperTypeOfTrue("two<int?,real>",
            "two<int,real>", "two<int32,real>", "two<int,real32>", "two<int32,real32>",
            "two<int?,real>", "two<int32?,real>", "two<int?,real32>", "two<int32?,real32>",
        )
        chkIsSuperTypeOfFalse("two<int?,real>", "two<int,real?>", "two<int?,real?>")

        chkIsSuperTypeOfTrue("two<int,real?>",
            "two<int,real>", "two<int32,real>", "two<int,real32>", "two<int32,real32>",
            "two<int,real?>", "two<int32,real?>", "two<int,real32?>", "two<int32,real32?>",
        )
        chkIsSuperTypeOfFalse("two<int,real?>", "two<int?,real>", "two<int?,real?>")

        chkIsSuperTypeOfTrue("two<int?,real?>",
            "two<int,real>", "two<int32,real>", "two<int,real32>", "two<int32,real32>",
            "two<int?,real>", "two<int32?,real>", "two<int?,real32>", "two<int32?,real32>",
            "two<int,real?>", "two<int32,real?>", "two<int,real32?>", "two<int32,real32?>",
            "two<int?,real?>", "two<int32?,real?>", "two<int?,real32?>", "two<int32?,real32?>",
        )
        chkIsSuperTypeOfFalse("two<int?,real?>", "two<int,num>", "two<num,real>", "two<int?,num>", "two<num,real?>")
    }

    @Test fun testReplaceParamsRaw() {
        chkReplaceParamsRaw("T?", "T=int", "int?")
        chkReplaceParamsRaw("T?", "T=null", "null")
        chkReplaceParamsRaw("T?", "T=anything", "anything")
        chkReplaceParamsRaw("T?", "T=nothing", "null")
        chkReplaceParamsRaw("T?", "T=any", "anything")
        chkReplaceParamsRaw("T?", "T=int?", "int?")
    }

    @Test fun testReplaceParamsRawWild() {
        chkReplaceParamsRaw("T?", "T=-int", "int?")
        chkReplaceParamsRaw("T?", "T=+int", "anything")
        chkReplaceParamsRaw("T?", "T=*", "anything")
        chkReplaceParamsRaw("T?", "T=-int?", "int?")
        chkReplaceParamsRaw("T?", "T=+int?", "anything")
    }

    @Test fun testReplaceParamsCap() {
        chkReplaceParamsCap("T?", "T=-int", "CAP<-int>?")
        chkReplaceParamsCap("T?", "T=+int", "CAP<+int>?")
        chkReplaceParamsCap("T?", "T=*", "CAP<*>?")
        chkReplaceParamsCap("T?", "T=-int?", "CAP<-int?>?")
        chkReplaceParamsCap("T?", "T=+int?", "CAP<+int?>?")
    }

    @Test fun testReplaceParamsInOut() {
        chkReplaceParamsInOut("T?", "T=-int", "null", "int?")
        chkReplaceParamsInOut("T?", "T=+int", "int?", "anything")
        chkReplaceParamsInOut("T?", "T=*", "null", "anything")
        chkReplaceParamsInOut("T?", "T=-int?", "null", "int?")
        chkReplaceParamsInOut("T?", "T=+int?", "int?", "anything")
    }

    @Test fun testReplaceParamsInOutComplex() {
        chkReplaceParamsInOut("data<T?>", "T=-int", "nothing", "data<-int?>")
        chkReplaceParamsInOut("data<T?>", "T=+int", "nothing", "data<*>")
        chkReplaceParamsInOut("data<T?>", "T=*", "nothing", "data<*>")

        chkReplaceParamsInOut("data<-T?>", "T=-int", "data<null>", "data<-int?>")
        chkReplaceParamsInOut("data<-T?>", "T=+int", "data<-int?>", "data<*>")
        chkReplaceParamsInOut("data<-T?>", "T=*", "data<null>", "data<*>")

        chkReplaceParamsInOut("data<+T?>", "T=-int", "data<+int?>", "data<+null>")
        chkReplaceParamsInOut("data<+T?>", "T=+int", "data<anything>", "data<+int?>")
        chkReplaceParamsInOut("data<+T?>", "T=*", "data<anything>", "data<+null>")
    }

    @Test fun testCommonSuperTypeNullable() {
        chkCommonSuperType("int?", "int?", "int?")
        chkCommonSuperType("int?", "real?", "num?")
        chkCommonSuperType("int?", "int", "int?")
        chkCommonSuperType("int32?", "real64?", "num?")
        chkCommonSuperType("int32?", "real64", "num?")
        chkCommonSuperType("int", "null", "int?")
        chkCommonSuperType("int?", "null", "int?")
        chkCommonSuperType("int?", "str", "n/a")
        chkCommonSuperType("int", "str?", "n/a")
        chkCommonSuperType("unit", "null", "unit?")
    }

    @Test fun testCommonSubTypeNullable() {
        chkCommonSubType("int?", "int?", "int?")
        chkCommonSubType("int?", "int", "int")
        chkCommonSubType("int?", "num", "n/a")
        chkCommonSubType("num?", "int", "int")
        chkCommonSubType("int", "null", "n/a")
        chkCommonSubType("int?", "num?", "int?")
        chkCommonSubType("int32", "num?", "int32")
    }
}
