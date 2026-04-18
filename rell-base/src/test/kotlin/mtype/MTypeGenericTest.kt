/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import net.postchain.rell.base.mtype.utils.MTestParser
import net.postchain.rell.base.mtype.utils.MTestScope
import kotlin.test.Test
import kotlin.test.assertEquals

internal class MTypeGenericTest: BaseMTypeTest() {
    override fun initScope(b: MTestScope.Builder) {
        MTestScope.initBasic(b)
        MTestScope.initNumeric(b)
        MTestScope.initCollections(b, "_")
        MTestScope.initRellCollections(b)
        MTestScope.initConsumerSupplier(b)
        MTestScope.initMisc(b)
    }

    @Test fun testParentType() {
        chkParent("_list<int>", "_collection<int>")
        chkParent("_collection<int>", "_iterable<int>")
        chkParent("_list<-int>", "_collection<-int>")
        chkParent("_collection<-int>", "_iterable<-int>")
        chkParent("_list<+int>", "_collection<+int>")
        chkParent("_collection<+int>", "_iterable<+int>")
    }

    @Test fun testCommonSuperTypeExact() {
        chkCommonSuperType("list<int>", "list<int>", "list<int>")
        chkCommonSuperType("collection<int>", "list<int>", "collection<int>")
        chkCommonSuperType("list<int>", "set<int>", "collection<int>")
        chkCommonSuperType("list<int>", "list<num>", "list<-num>")
        chkCommonSuperType("list<int>", "list<int32>", "list<-int>")
    }

    @Test fun testCommonSuperTypeSub() {
        chkCommonSuperType("list<-int>", "list<-int>", "list<-int>")
        chkCommonSuperType("list<-int>", "list<-num>", "list<-num>")
        chkCommonSuperType("list<-int>", "list<-real>", "list<-num>")
        chkCommonSuperType("list<-int32>", "list<-real64>", "list<-num>")
        chkCommonSuperType("list<-int32>", "set<-real64>", "collection<-num>")
        chkCommonSuperType("list<-int>", "list<-str>", "list<*>")

        chkCommonSuperType("list<-int>", "list<int>", "list<-int>")
        chkCommonSuperType("list<-int>", "list<num>", "list<-num>")
        chkCommonSuperType("list<-num>", "list<int>", "list<-num>")
        chkCommonSuperType("list<-int>", "list<real>", "list<-num>")
        chkCommonSuperType("list<-real>", "list<int>", "list<-num>")
        chkCommonSuperType("list<-real>", "set<int>", "collection<-num>")
        chkCommonSuperType("list<-int>", "list<str>", "list<*>")
        chkCommonSuperType("list<-str>", "list<int>", "list<*>")
    }

    @Test fun testCommonSuperTypeSuper() {
        chkCommonSuperType("list<+int>", "list<+int>", "list<+int>")
        chkCommonSuperType("list<+int>", "list<+num>", "list<+int>")
        chkCommonSuperType("list<+int>", "list<+int32>", "list<+int32>")
        chkCommonSuperType("list<+int>", "list<+real>", "list<*>")
        chkCommonSuperType("list<+int>", "set<+num>", "collection<+int>")

        chkCommonSuperType("list<+(int,real)>", "list<+(int,real)>", "list<+(int,real)>")
        chkCommonSuperType("list<+(int,num)>", "list<+(num,real)>", "list<+(int,real)>")

        chkCommonSuperType("list<+int>", "list<int>", "list<+int>")
        chkCommonSuperType("list<+int>", "list<num>", "list<+int>")
        chkCommonSuperType("list<+int>", "list<int32>", "list<+int32>")
        chkCommonSuperType("list<+int>", "list<real>", "list<*>")
        chkCommonSuperType("list<+int>", "set<int32>", "collection<+int32>")

        chkCommonSuperType("list<+int>", "list<-int>", "list<*>")
        chkCommonSuperType("list<+int>", "list<-num>", "list<*>")
        chkCommonSuperType("list<+int>", "list<-int32>", "list<*>")
        chkCommonSuperType("list<+num>", "list<-int>", "list<*>")
        chkCommonSuperType("list<+int32>", "list<-int>", "list<*>")
    }

    @Test fun testCommonSuperTypeMixed() {
        chkCommonSuperType("list<-int>", "list<num>", "list<-num>")
        chkCommonSuperType("list<-int>", "list<int>", "list<-int>")
        chkCommonSuperType("list<-int>", "list<int32>", "list<-int>")
        chkCommonSuperType("list<-num>", "list<int>", "list<-num>")
        chkCommonSuperType("list<-int32>", "list<int>", "list<-int>")

        chkCommonSuperType("list<+int>", "list<num>", "list<+int>")
        chkCommonSuperType("list<+int>", "list<int>", "list<+int>")
        chkCommonSuperType("list<+int>", "list<int32>", "list<+int32>")
        chkCommonSuperType("list<+num>", "list<int>", "list<+int>")
        chkCommonSuperType("list<+int32>", "list<int>", "list<+int32>")

        chkCommonSuperType("list<*>", "list<int>", "list<*>")
    }

    @Test fun testCommonSuperTypeCapture() {
        chkCommonSuperType("list<CAP<-int>>", "list<num>", "list<-num>")
        chkCommonSuperType("list<CAP<-int>>", "list<int>", "list<-int>")
        chkCommonSuperType("list<CAP<-int>>", "list<int32>", "list<-int>")
        chkCommonSuperType("list<CAP<-num>>", "list<int>", "list<-num>")
        chkCommonSuperType("list<CAP<-int32>>", "list<int>", "list<-int>")

        chkCommonSuperType("list<CAP<+int>>", "list<num>", "list<+int>")
        chkCommonSuperType("list<CAP<+int>>", "list<int>", "list<+int>")
        chkCommonSuperType("list<CAP<+int>>", "list<int32>", "list<+int32>")
        chkCommonSuperType("list<CAP<+num>>", "list<int>", "list<+int>")
        chkCommonSuperType("list<CAP<+int32>>", "list<int>", "list<+int32>")

        chkCommonSuperType("list<CAP<*>>", "list<int>", "list<*>")
    }

    @Test fun testCommonSubTypeExact() {
        chkCommonSubType("list<int>", "list<int>", "list<int>")
        chkCommonSubType("list<int>", "collection<int>", "list<int>")
        chkCommonSubType("list<int>", "set<int>", "n/a")
        chkCommonSubType("list<int>", "list<num>", "n/a")
        chkCommonSubType("list<int>", "list<int32>", "n/a")
    }

    @Test fun testCommonSubTypeSub() {
        chkCommonSubType("list<-int>", "list<-int>", "list<-int>")
        chkCommonSubType("list<-int>", "list<-num>", "list<-int>")
        chkCommonSubType("list<-int>", "list<-int32>", "list<-int32>")
        chkCommonSubType("list<-int>", "list<-real>", "n/a")
        chkCommonSubType("list<-int>", "collection<-int>", "list<-int>")
        chkCommonSubType("list<-int>", "set<-int>", "n/a")

        chkCommonSubType("list<-int>", "list<int>", "list<int>")
        chkCommonSubType("list<-int>", "list<num>", "n/a")
        chkCommonSubType("list<-int>", "list<int32>", "list<int32>")
        chkCommonSubType("list<-int32>", "list<int>", "n/a")
        //chkCommonSubType("list<-int>", "collection<int>", "list<int>") //TODO support
        //chkCommonSubType("collection<-int>", "list<int>", "list<int>")
        //chkCommonSubType("list<-int>", "collection<int32>", "list<int32>")
        //chkCommonSubType("collection<-int>", "list<int32>", "list<int32>")
        chkCommonSubType("list<-int>", "set<int32>", "n/a")
        chkCommonSubType("set<-int>", "list<int32>", "n/a")
    }

    @Test fun testCommonSubTypeSuper() {
        chkCommonSubType("list<+int>", "list<+int>", "list<+int>")
        chkCommonSubType("list<+int>", "list<+num>", "list<+num>")
        chkCommonSubType("list<+int>", "list<+int32>", "list<+int>")
        chkCommonSubType("list<+int>", "collection<+int>", "list<+int>")
        chkCommonSubType("list<+int>", "set<+int>", "n/a")
        chkCommonSubType("list<+int>", "collection<+int32>", "list<+int>")
        chkCommonSubType("list<+int>", "list<+real>", "list<+num>")
        chkCommonSubType("list<+int32>", "list<+real64>", "list<+num>")
        chkCommonSubType("list<+int>", "list<+str>", "n/a")

        chkCommonSubType("list<+int>", "list<int>", "list<int>")
        chkCommonSubType("list<+int>", "list<num>", "list<num>")
        chkCommonSubType("list<+num>", "list<int>", "n/a")
        //chkCommonSubType("list<+int>", "collection<num>", "list<num>") //TODO support
        //chkCommonSubType("collection<+int>", "list<num>", "list<num>")

        chkCommonSubType("list<+int>", "list<-int>", "n/a")
    }

    @Test fun testCommonSubTypeMixed() {
        chkCommonSubType("list<-int>", "list<num>", "n/a")
        chkCommonSubType("list<-int>", "list<int>", "list<int>")
        chkCommonSubType("list<-int>", "list<int32>", "list<int32>")
        chkCommonSubType("list<-num>", "list<int>", "list<int>")
        chkCommonSubType("list<-int32>", "list<int>", "n/a")

        chkCommonSubType("list<+int>", "list<num>", "list<num>")
        chkCommonSubType("list<+int>", "list<int>", "list<int>")
        chkCommonSubType("list<+int>", "list<int32>", "n/a")
        chkCommonSubType("list<+num>", "list<int>", "n/a")
        chkCommonSubType("list<+int32>", "list<int>", "list<int>")
    }

    @Test fun testCommonSubTypeCapture() {
        chkCommonSubType("list<CAP<-int>>", "list<num>", "n/a")
        chkCommonSubType("list<CAP<-int>>", "list<int>", "list<int>")
        chkCommonSubType("list<CAP<-int>>", "list<int32>", "list<int32>")
        chkCommonSubType("list<CAP<-num>>", "list<int>", "list<int>")
        chkCommonSubType("list<CAP<-int32>>", "list<int>", "n/a")

        chkCommonSubType("list<CAP<+int>>", "list<num>", "list<num>")
        chkCommonSubType("list<CAP<+int>>", "list<int>", "list<int>")
        chkCommonSubType("list<CAP<+int>>", "list<int32>", "n/a")
        chkCommonSubType("list<CAP<+num>>", "list<int>", "n/a")
        chkCommonSubType("list<CAP<+int32>>", "list<int>", "list<int>")
    }

    @Test fun testConSupConsumer() {
        chkSuperType("consumer<int>", "consumer<num>", true)
        chkSuperType("consumer<int>", "consumer<int>", true)
        chkSuperType("consumer<int>", "consumer<int32>", false)

        chkCommonSuperType("consumer<int>", "consumer<num>", "consumer<int>")
        chkCommonSuperType("consumer<int>", "consumer<int>", "consumer<int>")
        chkCommonSuperType("consumer<int>", "consumer<int32>", "consumer<int32>")
        chkCommonSuperType("consumer<int>", "consumer<real>", "consumer<nothing>")

        chkCommonSubType("consumer<int>", "consumer<num>", "consumer<num>")
        chkCommonSubType("consumer<int>", "consumer<int>", "consumer<int>")
        chkCommonSubType("consumer<int>", "consumer<int32>", "consumer<int>")
        chkCommonSubType("consumer<int>", "consumer<real>", "consumer<num>")
    }

    @Test fun testConSupSupplier() {
        chkSuperType("supplier<int>", "supplier<num>", false)
        chkSuperType("supplier<int>", "supplier<int>", true)
        chkSuperType("supplier<int>", "supplier<int32>", true)

        chkCommonSuperType("supplier<int>", "supplier<num>", "supplier<num>")
        chkCommonSuperType("supplier<int>", "supplier<int>", "supplier<int>")
        chkCommonSuperType("supplier<int>", "supplier<int32>", "supplier<int>")
        chkCommonSuperType("supplier<int>", "supplier<real>", "supplier<num>")

        chkCommonSubType("supplier<int>", "supplier<num>", "supplier<int>")
        chkCommonSubType("supplier<int>", "supplier<int>", "supplier<int>")
        chkCommonSubType("supplier<int>", "supplier<int32>", "supplier<int32>")
        chkCommonSubType("supplier<int>", "supplier<real>", "n/a")
    }

    private fun chkSuperType(type1: String, type2: String, exp: Boolean) {
        val scope = scopeB.build()
        val mType1 = MTestParser.parseType(type1, scope)
        val mType2 = MTestParser.parseType(type2, scope)
        val act = mType1.isSuperTypeOf(mType2)
        assertEquals(exp, act)
    }
}
