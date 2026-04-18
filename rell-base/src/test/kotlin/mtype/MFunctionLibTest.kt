/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import kotlin.test.Test

internal class MFunctionLibTest: BaseMFunctionTest() {
    @Test fun testTryCallUnit() {
        chkGlobal("(()->unit): boolean", "()->unit", "boolean")
        chkGlobal("(()->unit): boolean", "()->integer", "n/a")
        chkGlobal("(()->unit): boolean", "null", "n/a")
    }

    @Test fun testTryCallTyped() {
        chkGlobal("<T> (()->T): T?", "()->unit", "unit? [T=unit]")
        chkGlobal("<T> (()->T): T?", "()->integer", "integer? [T=integer]")
        chkGlobal("<T> (()->T): T?", "()->integer?", "integer? [T=integer?]")
        chkGlobal("<T> (()->T): T?", "null", "n/a")
    }

    @Test fun testTryCallDefault() {
        val h = "<T> (()->T, T): T"
        chkGlobal(h, "()->unit,integer", "n/a")
        chkGlobal(h, "()->unit,null", "unit? [T=unit?]")
        chkGlobal(h, "()->integer,null", "integer? [T=integer?]")
        chkGlobal(h, "()->integer?,null", "integer? [T=integer?]")
        chkGlobal(h, "()->integer?,integer", "integer? [T=integer?]")
        chkGlobal(h, "()->integer,decimal", "n/a")
        chkGlobal(h, "()->decimal,integer", "n/a")
        chkGlobal(h, "()->decimal,integer?", "n/a")
        chkGlobal(h, "()->decimal?,integer", "n/a")
        chkGlobal(h, "()->decimal?,integer?", "n/a")
    }

    @Test fun testRequireCollection() {
        val h = "<T> (list<T>?): list<T>"
        chkGlobal(h, "list<integer>", "list<integer> [T=integer]")
        chkGlobal(h, "list<integer>?", "list<integer> [T=integer]")
        chkGlobal(h, "collection<integer>", "n/a")
        chkGlobal(h, "set<integer>", "n/a")
        chkGlobal(h, "set<integer>?", "n/a")
        chkGlobal(h, "null", "unresolved:T")
    }

    @Test fun testRequireMap() {
        val h = "<K,V> (map<K,V>?): map<K,V>"
        chkGlobal(h, "map<integer,text>", "map<integer,text> [K=integer,V=text]")
        chkGlobal(h, "map<integer,text>?", "map<integer,text> [K=integer,V=text]")
        chkGlobal(h, "null", "unresolved:K,V")
    }

    @Test fun testCollectionContainsAll() {
        chkMember("collection<int>", "(collection<-T>):boolean", "list<num>", "n/a")
        chkMember("collection<int>", "(collection<-T>):boolean", "list<num?>", "n/a")
        chkMember("collection<int>", "(collection<-T>):boolean", "list<int>", "boolean")
        chkMember("collection<int>", "(collection<-T>):boolean", "list<int?>", "n/a")
        chkMember("collection<int>", "(collection<-T>):boolean", "list<int32>", "boolean")
        chkMember("collection<int>", "(collection<-T>):boolean", "list<int32?>", "n/a")

        chkMember("collection<int>", "(collection<+T>):boolean", "list<num>", "boolean")
        chkMember("collection<int>", "(collection<+T>):boolean", "list<num?>", "boolean")
        chkMember("collection<int>", "(collection<+T>):boolean", "list<int>", "boolean")
        chkMember("collection<int>", "(collection<+T>):boolean", "list<int?>", "boolean")
        chkMember("collection<int>", "(collection<+T>):boolean", "list<int32>", "n/a")
        chkMember("collection<int>", "(collection<+T>):boolean", "list<int32?>", "n/a")

        chkMember("collection<int?>", "(collection<-T>):boolean", "list<num>", "n/a")
        chkMember("collection<int?>", "(collection<-T>):boolean", "list<num?>", "n/a")
        chkMember("collection<int?>", "(collection<-T>):boolean", "list<int>", "boolean")
        chkMember("collection<int?>", "(collection<-T>):boolean", "list<int?>", "boolean")
        chkMember("collection<int?>", "(collection<-T>):boolean", "list<int32>", "boolean")
        chkMember("collection<int?>", "(collection<-T>):boolean", "list<int32?>", "boolean")

        chkMember("collection<int?>", "(collection<+T>):boolean", "list<num>", "n/a")
        chkMember("collection<int?>", "(collection<+T>):boolean", "list<num?>", "boolean")
        chkMember("collection<int?>", "(collection<+T>):boolean", "list<int>", "n/a")
        chkMember("collection<int?>", "(collection<+T>):boolean", "list<int?>", "boolean")
        chkMember("collection<int?>", "(collection<+T>):boolean", "list<int32>", "n/a")
        chkMember("collection<int?>", "(collection<+T>):boolean", "list<int32?>", "n/a")
    }

    @Test fun testMapGet() {
        chkMember("map<integer,text>", "(K):V", "integer", "text")
        chkMember("map<integer,text>", "(K):V", "text", "n/a")
        chkMember("map<integer,text>", "(K):V", "integer?", "n/a")
        chkMember("map<integer,text>", "(K):V", "null", "n/a")
        chkMember("map<integer,text>", "(K):V", "decimal", "n/a")

        chkMember("map<decimal,text>", "(K):V", "decimal", "text")
        chkMember("map<decimal,text>", "(K):V", "integer", "n/a")
        chkMember("map<decimal,text>", "(K):V", "boolean", "n/a")

        chkMember("map<decimal?,text>", "(K):V", "decimal", "text")
        chkMember("map<decimal?,text>", "(K):V", "decimal?", "text")
        chkMember("map<decimal?,text>", "(K):V", "null", "text")
        chkMember("map<decimal?,text>", "(K):V", "integer", "n/a")
        chkMember("map<decimal?,text>", "(K):V", "integer?", "n/a")
    }

    @Test fun testMapGetOrDefault() {
        chkMember("map<text,integer>", "<R:+V>(K,R):R", "text,integer", "integer [R=integer]")
        chkMember("map<text,integer>", "<R:+V>(K,R):R", "text,integer?", "integer? [R=integer?]")
        chkMember("map<text,integer>", "<R:+V>(K,R):R", "text,null", "integer? [R=integer?]")

        chkMember("map<text,integer?>", "<R:+V>(K,R):R", "text,integer?", "integer? [R=integer?]")
        chkMember("map<text,integer?>", "<R:+V>(K,R):R", "text,integer", "integer? [R=integer?]")
        chkMember("map<text,integer?>", "<R:+V>(K,R):R", "text,null", "integer? [R=integer?]")

        chkMember("map<text,integer>", "<R:+V>(K,R):R", "text,decimal", "n/a")
        chkMember("map<text,decimal>", "<R:+V>(K,R):R", "text,integer", "n/a")
        chkMember("map<text,decimal>", "<R:+V>(K,R):R", "text,integer?", "n/a")

        chkMember("map<text,int>", "<R:+V>(K,R):R", "text,int", "int [R=int]")
        chkMember("map<text,int>", "<R:+V>(K,R):R", "text,num", "num [R=num]")
        chkMember("map<text,int>", "<R:+V>(K,R):R", "text,int32", "int [R=int]")
        chkMember("map<text,int>", "<R:+V>(K,R):R", "text,real", "num [R=num]")
        chkMember("map<text,int>", "<R:+V>(K,R):R", "text,real32", "num [R=num]")

        chkMember("map<text,int>", "<R:+V>(K,R):R", "text,real?", "num? [R=num?]")
        chkMember("map<text,int?>", "<R:+V>(K,R):R", "text,real", "num? [R=num?]")
    }

    @Test fun testMapPutAll() {
        chkMember("map<int,real>", "(map<-K,-V>):unit", "map<int,real>", "unit")
        chkMember("map<int,real>", "(map<-K,-V>):unit", "map<int32,real>", "unit")
        chkMember("map<int,real>", "(map<-K,-V>):unit", "map<int,real32>", "unit")
        chkMember("map<int,real>", "(map<-K,-V>):unit", "map<int32,real32>", "unit")

        chkMember("map<int,real>", "(map<-K,-V>):unit", "map<num,real>", "n/a")
        chkMember("map<int,real>", "(map<-K,-V>):unit", "map<int,num>", "n/a")
        chkMember("map<int,real>", "(map<-K,-V>):unit", "map<num,num>", "n/a")

        chkMember("map<int,real>", "(map<-K,-V>):unit", "map<int,int>", "n/a")
        chkMember("map<int,real>", "(map<-K,-V>):unit", "map<real,real>", "n/a")

        chkMember("map<int,real>", "(map<-K,-V>):unit", "map<int?,real>", "n/a")
        chkMember("map<int,real>", "(map<-K,-V>):unit", "map<int,real?>", "n/a")
        chkMember("map<int,real>", "(map<-K,-V>):unit", "map<int?,real?>", "n/a")

        chkMember("map<int?,real>", "(map<-K,-V>):unit", "map<int,real>", "unit")
        chkMember("map<int?,real>", "(map<-K,-V>):unit", "map<int?,real>", "unit")
        chkMember("map<int?,real>", "(map<-K,-V>):unit", "map<int,real?>", "n/a")
        chkMember("map<int?,real>", "(map<-K,-V>):unit", "map<int?,real?>", "n/a")
    }
}
