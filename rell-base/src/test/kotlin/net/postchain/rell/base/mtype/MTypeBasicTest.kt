/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import org.junit.Test

class MTypeBasicTest: BaseMTypeTest() {
    @Test fun testConstruct() {
        chkConstruct("T", "T")
        chkConstruct("int", "int")
        chkConstruct("real", "real")

        chkConstruct("data<T>", "data<T>")
        chkConstruct("data<int>", "data<int>")
        chkConstruct("data<-int>", "data<-int>")
        chkConstruct("data<+int>", "data<+int>")
        chkConstruct("data<*>", "data<*>")

        chkConstruct("data<+anything>", "data<anything>")
        chkConstruct("data<-anything>", "data<*>")
        chkConstruct("data<+nothing>", "data<*>")
        chkConstruct("data<-nothing>", "data<nothing>")
    }

    @Test fun testConstructVariance() {
        chkConstruct("consumer<int>", "consumer<int>")
        chkConstruct("consumer<-int>", "consumer<nothing>")
        chkConstruct("consumer<+int>", "consumer<int>")
        chkConstruct("consumer<*>", "consumer<nothing>")
        chkConstruct("consumer<nothing>", "consumer<nothing>")
        chkConstruct("consumer<anything>", "consumer<anything>")

        chkConstruct("supplier<int>", "supplier<int>")
        chkConstruct("supplier<-int>", "supplier<int>")
        chkConstruct("supplier<+int>", "supplier<anything>")
        chkConstruct("supplier<*>", "supplier<anything>")
        chkConstruct("supplier<nothing>", "supplier<nothing>")
        chkConstruct("supplier<anything>", "supplier<anything>")
    }

    @Test fun testParentTypeSimple() {
        chkParent("num", "-")
        chkParent("int", "num")
        chkParent("real", "num")
        chkParent("int32", "int")
        chkParent("int64", "int")
        chkParent("real32", "real")
        chkParent("real64", "real")
        chkParent("data<int>", "-")
        chkParent("data<T>", "-")
    }

    @Test fun testParentTypeCollections() {
        chkParent("iterable<int>", "-")
        chkParent("array<int>", "iterable<int>")
        chkParent("collection<int>", "iterable<int>")
        chkParent("list<int>", "collection<int>")
        chkParent("set<int>", "collection<int>")

        chkParent("collection<T>", "iterable<T>")
        chkParent("collection<*>", "iterable<*>")
        chkParent("collection<-int>", "iterable<-int>")
        chkParent("collection<+int>", "iterable<+int>")
        chkParent("collection<-T>", "iterable<-T>")
        chkParent("collection<+T>", "iterable<+T>")
    }

    @Test fun testParentTypeFunction() {
        scopeB.typeDef("data_fun<A,B>: data<(A)->B>")

        chkParent("data_fun<int,real>", "data<(int)->real>")
        chkParent("data_fun<-int,real>", "data<(nothing)->real>")
        chkParent("data_fun<+int,real>", "data<(int)->real>")
        chkParent("data_fun<*,real>", "data<(nothing)->real>")
        chkParent("data_fun<int,-real>", "data<(int)->real>")
        chkParent("data_fun<int,+real>", "data<(int)->anything>")
        chkParent("data_fun<int,*>", "data<(int)->anything>")
        chkParent("data_fun<*,*>", "data<(nothing)->anything>")
    }

    @Test fun testIsSuperTypeOfBasic() {
        chkIsSuperTypeOfSub("num", "int", "int32", "int64", "real", "real32", "real64")
        chkIsSuperTypeOfSub("int", "int32", "int64")
        chkIsSuperTypeOfSub("real", "real32", "real64")

        val other = arrayOf("str", "bool", "list<num>", "list<int>")
        chkIsSuperTypeOfOther("num", *other)
        chkIsSuperTypeOfOther("int", *other)
        chkIsSuperTypeOfOther("real", *other)
        chkIsSuperTypeOfOther("int32", *other)
        chkIsSuperTypeOfOther("int64", *other)
        chkIsSuperTypeOfOther("real32", *other)
        chkIsSuperTypeOfOther("real64", *other)
    }

    @Test fun testIsSuperTypeOfNothing() {
        chkIsSuperTypeOfSuper("nothing", "num", "int", "real", "int32", "int64", "real32", "real64")
        chkIsSuperTypeOfSuper("nothing", "data<int>", "data<real>", "data<*>", "data<nothing>", "data<anything>")
        chkIsSuperTypeOfSuper("nothing", "(int)->real", "()->real", "(int,real)->num")
        chkIsSuperTypeOfSuper("nothing", "anything")
    }

    @Test fun testIsSuperTypeOfParam() {
        scopeB.paramDef("A")
        scopeB.paramDef("B:-int")
        scopeB.paramDef("C:+int")

        chkIsSuperTypeOfSuper("A", "anything")
        chkIsSuperTypeOfSub("A", "nothing")
        chkIsSuperTypeOfOther("A", "int", "int32", "real")

        chkIsSuperTypeOfSuper("B", "anything", "num", "int")
        chkIsSuperTypeOfSub("B", "nothing")
        chkIsSuperTypeOfOther("B", "int32", "int64", "real")

        chkIsSuperTypeOfSuper("C", "anything")
        chkIsSuperTypeOfSub("C", "nothing", "int", "int32", "int64")
        chkIsSuperTypeOfOther("C", "num", "real", "real32", "real64")
    }

    @Test fun testIsSuperTypeOfAny() {
        scopeB.paramDef("A")
        scopeB.paramDef("B:-int")
        scopeB.paramDef("C:+int")

        chkIsSuperTypeOfSuper("any", "anything")
        chkIsSuperTypeOfSub("any", "num", "int", "real", "int32", "int64", "real32", "real64")
        chkIsSuperTypeOfSub("any", "CAP<-num>", "CAP<-int>", "CAP<-real>", "CAP<-any>")
        chkIsSuperTypeOfSuper("any", "CAP<+any>")
        chkIsSuperTypeOfOther("any", "CAP<+num>", "CAP<+int>", "CAP<+int32>", "CAP<*>")
        chkIsSuperTypeOfSub("any", "B")
        chkIsSuperTypeOfOther("any", "A", "C")
    }

    @Test fun testIsSuperTypeOfGeneric1() {
        chkIsSuperTypeOfSub("iterable<int>", "array<int>", "collection<int>", "list<int>", "set<int>")
        chkIsSuperTypeOfSub("collection<int>", "list<int>", "set<int>")

        val other = arrayOf("str", "collection<num>", "collection<int32>", "collection<real>")
        chkIsSuperTypeOfOther("iterable<int>", *other)
        chkIsSuperTypeOfOther("array<int>", *other)
        chkIsSuperTypeOfOther("collection<int>", *other)
        chkIsSuperTypeOfOther("list<int>", *other)
        chkIsSuperTypeOfOther("set<int>", *other)
    }

    @Test fun testIsSuperTypeOfGenericWild() {
        chkIsSuperTypeOfSuper("collection<int>", "iterable<int>", "iterable<-num>", "collection<-num>")
        chkIsSuperTypeOfSub("collection<int>", "list<int>", "set<int>")
        chkIsSuperTypeOfOther("collection<int>", "collection<num>", "collection<int32>", "collection<real>")

        chkIsSuperTypeOfSuper("collection<-int>", "iterable<-int>", "collection<-num>")
        chkIsSuperTypeOfSub("collection<-int>", "collection<int>", "collection<int32>", "collection<-int32>")
        chkIsSuperTypeOfOther("collection<-int>", "collection<num>", "collection<+int>")

        chkIsSuperTypeOfSuper("collection<+int>", "iterable<+int>", "collection<+int32>")
        chkIsSuperTypeOfSub("collection<+int>", "collection<int>", "collection<num>", "collection<+num>")
        chkIsSuperTypeOfOther("collection<+int>", "collection<int32>", "collection<-int>")

        chkIsSuperTypeOfSuper("collection<*>", "iterable<*>")
        chkIsSuperTypeOfSub("collection<*>",
            "collection<num>", "collection<int>", "collection<int32>", "collection<-int>", "collection<+int>",
            "list<num>", "list<int>", "list<int32>", "list<-int>", "list<+int>",
            "collection<str>",
        )
    }

    @Test fun testIsSuperTypeOfGenericVariance() {
        chkIsSuperTypeOfSuper("consumer<int>", "anything")
        chkIsSuperTypeOfSub("consumer<int>", "nothing", "consumer<num>")
        chkIsSuperTypeOf("consumer<int>", "consumer<int32>", false)
        chkIsSuperTypeOf("consumer<int>", "consumer<int64>", false)

        chkIsSuperTypeOfSuper("consumer<-int>", "anything")
        chkIsSuperTypeOfSub("consumer<-int>", "nothing", "consumer<num>", "consumer<int>", "consumer<int32>", "consumer<int64>")

        chkIsSuperTypeOfSuper("supplier<int>", "anything")
        chkIsSuperTypeOfSub("supplier<int>", "nothing", "supplier<int32>", "supplier<int64>")

        chkIsSuperTypeOfSuper("supplier<+int>", "anything")
        chkIsSuperTypeOfSub("supplier<+int>", "nothing", "supplier<num>", "supplier<int32>", "supplier<int64>")
    }

    @Test fun testCommonSuperType() {
        chkCommonSuperType("int", "int", "int")
        chkCommonSuperType("int", "num", "num")
        chkCommonSuperType("int32", "num", "num")
        chkCommonSuperType("int32", "real64", "num")
        chkCommonSuperType("int", "str", "n/a")
    }

    @Test fun testCommonSubType() {
        chkCommonSubType("int", "int", "int")
        chkCommonSubType("num", "int", "int")
        chkCommonSubType("int", "int64", "int64")
        chkCommonSubType("num", "int64", "int64")
        chkCommonSubType("int", "real", "n/a")
        chkCommonSubType("int32", "real64", "n/a")
    }
}
