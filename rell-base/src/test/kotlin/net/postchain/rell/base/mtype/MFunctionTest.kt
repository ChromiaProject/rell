/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import kotlin.test.Test

internal class MFunctionTest: BaseMFunctionTest() {
    @Test fun testConversion() {
        chkGlobal("(int32): unit", "int32", "unit")
        chkGlobal("(int32): unit", "int64", "n/a")
        chkGlobal("(int32): unit", "real32", "n/a")
        chkGlobal("(int32): unit", "real64", "n/a")
        chkGlobal("(int32): unit", "real", "n/a")

        chkGlobal("(int64): unit", "int32", "n/a")
        chkGlobal("(int64): unit", "int64", "unit")
        chkGlobal("(int64): unit", "real32", "n/a")
        chkGlobal("(int64): unit", "real64", "n/a")
        chkGlobal("(int64): unit", "real", "n/a")
        chkGlobal("(int64): unit", "int", "n/a")

        chkGlobal("(real32): unit", "real32", "unit")
        chkGlobal("(real32): unit", "int32", "n/a")
        chkGlobal("(real32): unit", "int64", "n/a")
        chkGlobal("(real32): unit", "real64", "n/a")

        chkGlobal("(real64): unit", "real32", "n/a")
        chkGlobal("(real64): unit", "real64", "unit")
        chkGlobal("(real64): unit", "int32", "n/a")
        chkGlobal("(real64): unit", "int64", "n/a")
        chkGlobal("(real64): unit", "int", "n/a")
    }

    @Test fun testStrictNullable() {
        chkGlobal("(int?): unit", "int?", "unit")
        chkGlobal("(int?): unit", "null", "unit")
        chkGlobal("(int?): unit", "int", "unit")
        chkGlobal("(int?): unit", "int32?", "unit")
        chkGlobal("(int?): unit", "int32", "unit")

        chkGlobal("(@nullable int?): unit", "int?", "unit")
        chkGlobal("(@nullable int?): unit", "null", "n/a")
        chkGlobal("(@nullable int?): unit", "int", "n/a")
        chkGlobal("(@nullable int?): unit", "int32?", "unit")
        chkGlobal("(@nullable int?): unit", "int32", "n/a")
    }

    @Test fun testSubTypeHierarchy() {
        chkGlobal("(num): unit", "num", "unit")
        chkGlobal("(num): unit", "int", "unit")
        chkGlobal("(num): unit", "int32", "unit")
        chkGlobal("(num): unit", "real", "unit")
        chkGlobal("(num): unit", "real32", "unit")
        chkGlobal("(num): unit", "bool", "n/a")
        chkGlobal("(num): unit", "str", "n/a")

        chkGlobal("(int): unit", "num", "n/a")
        chkGlobal("(int): unit", "int", "unit")
        chkGlobal("(int): unit", "int32", "unit")
        chkGlobal("(int): unit", "int64", "unit")
        chkGlobal("(int): unit", "real", "n/a")
        chkGlobal("(int): unit", "real32", "n/a")
        chkGlobal("(int): unit", "bool", "n/a")
        chkGlobal("(int): unit", "str", "n/a")

        chkGlobal("(int32): unit", "num", "n/a")
        chkGlobal("(int32): unit", "int", "n/a")
        chkGlobal("(int32): unit", "int32", "unit")
        chkGlobal("(int32): unit", "int64", "n/a")
        chkGlobal("(int32): unit", "real", "n/a")
        chkGlobal("(int32): unit", "real32", "n/a")
        chkGlobal("(int32): unit", "bool", "n/a")
        chkGlobal("(int32): unit", "str", "n/a")
    }

    @Test fun testSubTypeNullable() {
        chkGlobal("(integer?): unit", "integer?", "unit")
        chkGlobal("(integer?): unit", "integer", "unit")
        chkGlobal("(integer?): unit", "null", "unit")
        chkGlobal("(integer?): unit", "decimal?", "n/a")
        chkGlobal("(integer?): unit", "decimal", "n/a")
        chkGlobal("(integer?): unit", "text?", "n/a")
        chkGlobal("(integer?): unit", "text", "n/a")
    }

    @Test fun testGenericCollectionSubType() {
        chkGlobal("(collection<int>): unit", "collection<int>", "unit")
        chkGlobal("(collection<int>): unit", "collection<num>", "n/a")
        chkGlobal("(collection<int>): unit", "collection<int32>", "n/a")
        chkGlobal("(collection<int>): unit", "list<int>", "unit")
        chkGlobal("(collection<int>): unit", "list<num>", "n/a")
        chkGlobal("(collection<int>): unit", "list<int32>", "n/a")
        chkGlobal("(collection<int>): unit", "set<int>", "unit")
        chkGlobal("(collection<int>): unit", "set<num>", "n/a")
        chkGlobal("(collection<int>): unit", "set<int32>", "n/a")

        chkGlobal("(list<int>): unit", "collection<int>", "n/a")
        chkGlobal("(list<int>): unit", "list<int>", "unit")
        chkGlobal("(list<int>): unit", "set<int>", "n/a")
        chkGlobal("(list<int>): unit", "list<num>", "n/a")
        chkGlobal("(list<int>): unit", "list<int32>", "n/a")

        chkGlobal("(set<int>): unit", "collection<int>", "n/a")
        chkGlobal("(set<int>): unit", "list<int>", "n/a")
        chkGlobal("(set<int>): unit", "set<int>", "unit")
        chkGlobal("(set<int>): unit", "set<num>", "n/a")
        chkGlobal("(set<int>): unit", "set<int32>", "n/a")
    }

    @Test fun testGenericCollectionConversion() {
        chkGlobal("(collection<real64>): unit", "collection<real64>", "unit")
        chkGlobal("(collection<real64>): unit", "collection<real32>", "n/a")
        chkGlobal("(collection<real64>): unit", "collection<int64>", "n/a")
        chkGlobal("(collection<real64>): unit", "collection<int32>", "n/a")

        chkGlobal("(list<real64>): unit", "list<real64>", "unit")
        chkGlobal("(list<real64>): unit", "list<real32>", "n/a")
        chkGlobal("(list<real64>): unit", "list<int64>", "n/a")
        chkGlobal("(list<real64>): unit", "list<int32>", "n/a")

        chkGlobal("(set<real64>): unit", "set<real64>", "unit")
        chkGlobal("(set<real64>): unit", "set<real32>", "n/a")
        chkGlobal("(set<real64>): unit", "set<int64>", "n/a")
        chkGlobal("(set<real64>): unit", "set<int32>", "n/a")
    }

    @Test fun testGenericMapSubType() {
        chkGlobal("(map<int,real>): unit", "map<int,real>", "unit")
        chkGlobal("(map<int,real>): unit", "map<int32,real>", "n/a")
        chkGlobal("(map<int,real>): unit", "map<int,real32>", "n/a")
        chkGlobal("(map<int,real>): unit", "map<num,real>", "n/a")
        chkGlobal("(map<int,real>): unit", "map<int,num>", "n/a")
        chkGlobal("(map<int,real>): unit", "map<num,num>", "n/a")
    }

    @Test fun testGenericMapConversion() {
        chkGlobal("(map<int64,real64>): unit", "map<int64,real64>", "unit")
        chkGlobal("(map<int64,real64>): unit", "map<int32,real64>", "n/a")
        chkGlobal("(map<int64,real64>): unit", "map<int64,real32>", "n/a")
        chkGlobal("(map<int64,real64>): unit", "map<int32,real32>", "n/a")
        chkGlobal("(map<int64,real64>): unit", "map<int64,int64>", "n/a")
        chkGlobal("(map<int64,real64>): unit", "map<int64,int32>", "n/a")
    }

    @Test fun testGenericWildcard() {
        chkGlobal("(list<*>):unit", "list<num>", "unit")
        chkGlobal("(list<*>):unit", "list<int>", "unit")
        chkGlobal("(list<*>):unit", "list<int32>", "unit")
        chkGlobal("(list<*>):unit", "list<int?>", "unit")
        chkGlobal("(list<*>):unit", "list<text>", "unit")
        chkGlobal("(list<*>):unit", "set<int>", "n/a")
        chkGlobal("(list<*>):unit", "null", "n/a")

        chkGlobal("(list<*>):unit", "list<int>?", "n/a")
        chkGlobal("(list<*>?):unit", "list<int>?", "unit")
    }

    @Test fun testGenericWildcardSub() {
        chkGlobal("(list<int>):unit", "list<num>", "n/a")
        chkGlobal("(list<int>):unit", "list<int>", "unit")
        chkGlobal("(list<int>):unit", "list<int32>", "n/a")

        chkGlobal("(list<-int>):unit", "list<num>", "n/a")
        chkGlobal("(list<-int>):unit", "list<int>", "unit")
        chkGlobal("(list<-int>):unit", "list<int32>", "unit")
        chkGlobal("(list<-int>):unit", "list<real32>", "n/a")

        chkGlobal("(list<-int>):unit", "list<int32>?", "n/a")
        chkGlobal("(list<-int>?):unit", "list<int32>?", "unit")
    }

    @Test fun testGenericWildcardSuper() {
        chkGlobal("(list<int>):unit", "list<num>", "n/a")
        chkGlobal("(list<int>):unit", "list<int>", "unit")
        chkGlobal("(list<int>):unit", "list<int32>", "n/a")

        chkGlobal("(list<+int>):unit", "list<num>", "unit")
        chkGlobal("(list<+int>):unit", "list<int>", "unit")
        chkGlobal("(list<+int>):unit", "list<int32>", "n/a")
        chkGlobal("(list<+int>):unit", "list<real32>", "n/a")

        chkGlobal("(list<+int>):unit", "list<num>?", "n/a")
        chkGlobal("(list<+int>?):unit", "list<num>?", "unit")
    }

    @Test fun testGenericWildcardHierarchy() {
        chkGlobal("(_collection<int>):unit", "_iterable<int>", "n/a")
        chkGlobal("(_collection<int>):unit", "_collection<int>", "unit")
        chkGlobal("(_collection<int>):unit", "_list<int>", "unit")
        chkGlobal("(_collection<int>):unit", "_set<int>", "unit")
        chkGlobal("(_collection<int>):unit", "_array<int>", "n/a")

        chkGlobal("(_collection<int>):unit", "_collection<num>", "n/a")
        chkGlobal("(_collection<int>):unit", "_collection<int32>", "n/a")
        chkGlobal("(_collection<int>):unit", "_list<num>", "n/a")
        chkGlobal("(_collection<int>):unit", "_list<int32>", "n/a")

        chkGlobal("(_collection<-int>):unit", "_collection<num>", "n/a")
        chkGlobal("(_collection<-int>):unit", "_collection<int>", "unit")
        chkGlobal("(_collection<-int>):unit", "_collection<int32>", "unit")
        chkGlobal("(_collection<-int>):unit", "_iterable<num>", "n/a")
        chkGlobal("(_collection<-int>):unit", "_iterable<int>", "n/a")
        chkGlobal("(_collection<-int>):unit", "_iterable<int32>", "n/a")
        chkGlobal("(_collection<-int>):unit", "_list<num>", "n/a")
        chkGlobal("(_collection<-int>):unit", "_list<int>", "unit")
        chkGlobal("(_collection<-int>):unit", "_list<int32>", "unit")

        chkGlobal("(_collection<+int>):unit", "_collection<num>", "unit")
        chkGlobal("(_collection<+int>):unit", "_collection<int>", "unit")
        chkGlobal("(_collection<+int>):unit", "_collection<int32>", "n/a")
        chkGlobal("(_collection<+int>):unit", "_iterable<num>", "n/a")
        chkGlobal("(_collection<+int>):unit", "_iterable<int>", "n/a")
        chkGlobal("(_collection<+int>):unit", "_iterable<int32>", "n/a")
        chkGlobal("(_collection<+int>):unit", "_list<num>", "unit")
        chkGlobal("(_collection<+int>):unit", "_list<int>", "unit")
        chkGlobal("(_collection<+int>):unit", "_list<int32>", "n/a")
    }

    @Test fun testGenericWildcardSuperType() {
        chkGlobal("(list<*>):unit", "list<num>", "unit")
        chkGlobal("(list<*>):unit", "list<int>", "unit")
        chkGlobal("(list<*>):unit", "list<int32>", "unit")

        chkGlobal("(list<*>):unit", "list<-num>", "unit")
        chkGlobal("(list<*>):unit", "list<-int>", "unit")
        chkGlobal("(list<*>):unit", "list<-int32>", "unit")

        chkGlobal("(list<*>):unit", "list<+num>", "unit")
        chkGlobal("(list<*>):unit", "list<+int>", "unit")
        chkGlobal("(list<*>):unit", "list<+int32>", "unit")
    }

    @Test fun testGenericWildcardSuperTypeAny() {
        chkGlobal("(list<-any>):unit", "list<num>", "unit")
        chkGlobal("(list<-any>):unit", "list<int>", "unit")
        chkGlobal("(list<-any>):unit", "list<int32>", "unit")

        chkGlobal("(list<-any>):unit", "list<-num>", "unit")
        chkGlobal("(list<-any>):unit", "list<-int>", "unit")
        chkGlobal("(list<-any>):unit", "list<-int32>", "unit")

        chkGlobal("(list<-any>):unit", "list<+num>", "n/a")
        chkGlobal("(list<-any>):unit", "list<+int>", "n/a")
        chkGlobal("(list<-any>):unit", "list<+int32>", "n/a")
    }

    @Test fun testGenericWildcardSuperTypeExact() {
        chkGlobal("(list<int>):unit", "list<num>", "n/a")
        chkGlobal("(list<int>):unit", "list<int>", "unit")
        chkGlobal("(list<int>):unit", "list<int32>", "n/a")

        chkGlobal("(list<int>):unit", "list<-num>", "n/a")
        chkGlobal("(list<int>):unit", "list<-int>", "n/a")
        chkGlobal("(list<int>):unit", "list<-int32>", "n/a")

        chkGlobal("(list<int>):unit", "list<+num>", "n/a")
        chkGlobal("(list<int>):unit", "list<+int>", "n/a")
        chkGlobal("(list<int>):unit", "list<+int32>", "n/a")
    }

    @Test fun testGenericWildcardSuperTypeSub() {
        chkGlobal("(list<-int>):unit", "list<num>", "n/a")
        chkGlobal("(list<-int>):unit", "list<int>", "unit")
        chkGlobal("(list<-int>):unit", "list<int32>", "unit")

        chkGlobal("(list<-int>):unit", "list<-num>", "n/a")
        chkGlobal("(list<-int>):unit", "list<-int>", "unit")
        chkGlobal("(list<-int>):unit", "list<-int32>", "unit")

        chkGlobal("(list<-int>):unit", "list<+num>", "n/a")
        chkGlobal("(list<-int>):unit", "list<+int>", "n/a")
        chkGlobal("(list<-int>):unit", "list<+int32>", "n/a")
    }

    @Test fun testGenericVarianceNonVariant() {
        chkGlobal("(data<int>): unit", "data<int>", "unit")
        chkGlobal("(data<int>): unit", "data<int32>", "n/a")
        chkGlobal("(data<int>): unit", "data<num>", "n/a")
    }

    @Test fun testGenericVarianceCovariant() {
        chkGlobal("(supplier<int>): unit", "supplier<num>", "n/a")
        chkGlobal("(supplier<int>): unit", "supplier<int>", "unit")
        chkGlobal("(supplier<int>): unit", "supplier<int32>", "unit")
    }

    @Test fun testGenericVarianceVariant() {
        chkGlobal("(consumer<int>): unit", "consumer<num>", "unit")
        chkGlobal("(consumer<int>): unit", "consumer<int>", "unit")
        chkGlobal("(consumer<int>): unit", "consumer<int32>", "n/a")
    }

    @Test fun testComparable() {
        chkGlobal("(comparable):unit", "integer", "unit")
        chkGlobal("(comparable):unit", "big_integer", "unit")
        chkGlobal("(comparable):unit", "decimal", "unit")
        chkGlobal("(comparable):unit", "text", "unit")
        chkGlobal("(comparable):unit", "boolean", "unit")
        chkGlobal("(comparable):unit", "gtv", "n/a")

        chkGlobal("(comparable):unit", "(integer,text)", "unit")
        chkGlobal("(comparable):unit", "(integer,gtv)", "n/a")
    }

    @Test fun testComparableTypeParam() {
        chkGlobal("<T:-comparable>(T,T):T", "integer,integer", "integer [T=integer]")
        chkGlobal("<T:-comparable>(T,T):T", "integer,decimal", "comparable [T=comparable]")
        chkGlobal("<T:-comparable>(T,T):T", "decimal,integer", "comparable [T=comparable]")
        chkGlobal("<T:-comparable>(T,T):T", "gtv,gtv", "n/a")
        chkGlobal("<T:-comparable>(T,T):T", "(integer,text),(integer,text)", "(integer,text) [T=(integer,text)]")
    }

    @Test fun testExact() {
        chkGlobal("(int): unit", "num", "n/a")
        chkGlobal("(int): unit", "int", "unit")
        chkGlobal("(int): unit", "int32", "unit")
        chkGlobal("(int): unit", "int64", "unit")

        chkGlobal("(@exact int): unit", "num", "n/a")
        chkGlobal("(@exact int): unit", "int", "unit")
        chkGlobal("(@exact int): unit", "int32", "n/a")
        chkGlobal("(@exact int): unit", "int64", "n/a")
    }

    @Test fun testExactNullable() {
        chkGlobal("(int?): unit", "null", "unit")
        chkGlobal("(int?): unit", "num", "n/a")
        chkGlobal("(int?): unit", "int", "unit")
        chkGlobal("(int?): unit", "int32", "unit")
        chkGlobal("(int?): unit", "int64", "unit")
        chkGlobal("(int?): unit", "num?", "n/a")
        chkGlobal("(int?): unit", "int?", "unit")
        chkGlobal("(int?): unit", "int32?", "unit")
        chkGlobal("(int?): unit", "int64?", "unit")

        chkGlobal("(@exact int?): unit", "null", "n/a")
        chkGlobal("(@exact int?): unit", "num", "n/a")
        chkGlobal("(@exact int?): unit", "int", "n/a")
        chkGlobal("(@exact int?): unit", "int32", "n/a")
        chkGlobal("(@exact int?): unit", "int64", "n/a")
        chkGlobal("(@exact int?): unit", "num?", "n/a")
        chkGlobal("(@exact int?): unit", "int?", "unit")
        chkGlobal("(@exact int?): unit", "int32?", "n/a")
        chkGlobal("(@exact int?): unit", "int64?", "n/a")
    }

    @Test fun testTuple() {
        chkGlobal("((integer,text)): unit", "(integer,text)", "unit")
        chkGlobal("((integer,text)): unit", "(integer,boolean)", "n/a")
        chkGlobal("((integer?,text)): unit", "(integer,text)", "unit")
        chkGlobal("((integer?,text)): unit", "(null,text)", "unit")
        chkGlobal("((integer?,text)): unit", "(integer,text?)", "n/a")

        chkGlobal("<T:-any> (T, T): unit", "(integer,text),(integer,text)", "unit [T=(integer,text)]")
        chkGlobal("<T:-any> (T, T): unit", "(integer?,text),(integer,text)", "unit [T=(integer?,text)]")
        chkGlobal("<T:-any> (T, T): unit", "(integer,text),(integer?,text)", "unit [T=(integer?,text)]")
        chkGlobal("<T:-any> (T, T): unit", "(integer?,text),(integer,text?)", "unit [T=(integer?,text?)]")
        chkGlobal("<T:-any> (T, T): unit", "(integer,text),(integer,text)", "unit [T=(integer,text)]")
    }
}
