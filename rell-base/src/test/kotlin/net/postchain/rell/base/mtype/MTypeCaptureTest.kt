/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import org.junit.Test

class MTypeCaptureTest: BaseMTypeTest() {
    @Test fun testIsSuperTypeOf() {
        chkIsSuperTypeOfSelf("CAP<-int>")
        chkIsSuperTypeOfSuper("CAP<-int>", "anything", "num", "int", "CAP<+num>", "CAP<+int>", self = false)
        chkIsSuperTypeOfSub("CAP<-int>", "nothing", self = false)
        chkIsSuperTypeOfOther("CAP<-int>", "int32", "int64", "real", self = false)
        chkIsSuperTypeOfOther("CAP<-int>", "CAP<-int>", "CAP<-int32>", "CAP<+int32>", "CAP<*>", self = false)

        chkIsSuperTypeOfSelf("CAP<+int>")
        chkIsSuperTypeOfSuper("CAP<+int>", "anything", self = false)
        chkIsSuperTypeOfSub("CAP<+int>", "nothing", "int", "int32", "CAP<-int>", "CAP<-int32>", self = false)
        chkIsSuperTypeOfOther("CAP<+int>", "num", "real", self = false)
        chkIsSuperTypeOfOther("CAP<+int>", "CAP<+int>", "CAP<+num>", "CAP<+int32>", "CAP<-num>", "CAP<*>", self = false)

        chkIsSuperTypeOfSelf("CAP<*>")
        chkIsSuperTypeOfSuper("CAP<*>", "anything", self = false)
        chkIsSuperTypeOfSub("CAP<*>", "nothing", self = false)
        chkIsSuperTypeOfOther("CAP<*>", "num", "int", "int32", "real", self = false)
        chkIsSuperTypeOfOther("CAP<*>", "CAP<*>", "CAP<+int>", "CAP<-int>", self = false)
    }

    @Test fun testCommonSuperType() {
        chkCommonSuperType("CAP<-int>", "num", "num")
        chkCommonSuperType("CAP<-int>", "int", "int")
        chkCommonSuperType("CAP<-int>", "int32", "int")
        chkCommonSuperType("CAP<-int>", "real", "num")
        chkCommonSuperType("CAP<-num>", "int", "num")
        chkCommonSuperType("CAP<-int32>", "int", "int")

        chkCommonSuperType("CAP<+int>", "num", "n/a")
        chkCommonSuperType("CAP<+int>", "int", "CAP<+int>")
        chkCommonSuperType("CAP<+int>", "int32", "CAP<+int>")
        chkCommonSuperType("CAP<+num>", "int", "CAP<+num>")
        chkCommonSuperType("CAP<+int32>", "int", "n/a")

        chkCommonSuperType("CAP<*>", "int", "n/a")
    }

    @Test fun testCommonSubType() {
        chkCommonSubType("CAP<-int>", "num", "CAP<-int>")
        chkCommonSubType("CAP<-int>", "int", "CAP<-int>")
        chkCommonSubType("CAP<-int>", "int32", "n/a")
        chkCommonSubType("CAP<-num>", "int", "n/a")
        chkCommonSubType("CAP<-int32>", "int", "CAP<-int32>")

        chkCommonSubType("CAP<+int>", "num", "int")
        chkCommonSubType("CAP<+int>", "int", "int")
        chkCommonSubType("CAP<+int>", "int32", "int32")
        chkCommonSubType("CAP<+num>", "int", "int")
        chkCommonSubType("CAP<+int32>", "int", "int32")

        chkCommonSubType("CAP<*>", "int", "n/a")
    }
}
