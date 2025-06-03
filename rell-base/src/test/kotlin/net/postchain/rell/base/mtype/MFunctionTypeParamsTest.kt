/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import org.junit.Test

class MFunctionTypeParamsTest: BaseMFunctionTest() {
    @Test fun testSubType() {
        chkGlobal("<T> (T, T): T", "num,num", "num [T=num]")
        chkGlobal("<T> (T, T): T", "num,int", "num [T=num]")
        chkGlobal("<T> (T, T): T", "int,num", "num [T=num]")
        chkGlobal("<T> (T, T): T", "num,real32", "num [T=num]")
        chkGlobal("<T> (T, T): T", "real32,num", "num [T=num]")

        chkGlobal("<T> (T, T): T", "int,real", "num [T=num]")
        chkGlobal("<T> (T, T): T", "real,int", "num [T=num]")
        chkGlobal("<T> (T, T): T", "int32,real", "num [T=num]")
        chkGlobal("<T> (T, T): T", "real,int32", "num [T=num]")
        chkGlobal("<T> (T, T): T", "int64,real32", "num [T=num]")
        chkGlobal("<T> (T, T): T", "real32,int64", "num [T=num]")

        chkGlobal("<T> (T, T): T", "int,int32", "int [T=int]")
        chkGlobal("<T> (T, T): T", "int32,int", "int [T=int]")
        chkGlobal("<T> (T, T): T", "int32,int64", "int [T=int]")
        chkGlobal("<T> (T, T): T", "int64,int32", "int [T=int]")

        chkGlobal("<T> (T, T): T", "int,str", "n/a")
        chkGlobal("<T> (T, T): T", "str,int", "n/a")
        chkGlobal("<T> (T, T): T", "num,bool", "n/a")
        chkGlobal("<T> (T, T): T", "bool,num", "n/a")
    }

    @Test fun testSubTypeNullable() {
        chkGlobal("<T> (T, T): T", "num?,num", "num? [T=num?]")
        chkGlobal("<T> (T, T): T", "num,num?", "num? [T=num?]")
        chkGlobal("<T> (T, T): T", "num,null", "num? [T=num?]")
        chkGlobal("<T> (T, T): T", "null,num", "num? [T=num?]")

        chkGlobal("<T> (T, T): T", "real,int?", "num? [T=num?]")
        chkGlobal("<T> (T, T): T", "int?,real", "num? [T=num?]")
        chkGlobal("<T> (T, T): T", "int,real?", "num? [T=num?]")
        chkGlobal("<T> (T, T): T", "real?,int", "num? [T=num?]")
    }

    @Test fun testConversion() {
        chkGlobal("<T> (T, T): T", "integer,big_integer", "big_integer [T=big_integer]")
        chkGlobal("<T> (T, T): T", "big_integer,integer", "big_integer [T=big_integer]")
        chkGlobal("<T> (T, T): T", "integer,decimal", "decimal [T=decimal]")
        chkGlobal("<T> (T, T): T", "decimal,integer", "decimal [T=decimal]")
        chkGlobal("<T> (T, T): T", "big_integer,decimal", "decimal [T=decimal]")
        chkGlobal("<T> (T, T): T", "decimal,big_integer", "decimal [T=decimal]")
    }

    @Test fun testConversionNullable() {
        chkGlobal("<T> (T, T): T", "integer,big_integer?", "big_integer? [T=big_integer?]")
        chkGlobal("<T> (T, T): T", "big_integer?,integer", "big_integer? [T=big_integer?]")
        chkGlobal("<T> (T, T): T", "integer?,big_integer", "big_integer? [T=big_integer?]")
        chkGlobal("<T> (T, T): T", "big_integer,integer?", "big_integer? [T=big_integer?]")
        chkGlobal("<T> (T, T): T", "integer?,big_integer?", "big_integer? [T=big_integer?]")
        chkGlobal("<T> (T, T): T", "big_integer?,integer?", "big_integer? [T=big_integer?]")

        chkGlobal("<T> (T, T): T", "integer,decimal?", "decimal? [T=decimal?]")
        chkGlobal("<T> (T, T): T", "decimal?,integer", "decimal? [T=decimal?]")
        chkGlobal("<T> (T, T): T", "integer?,decimal", "decimal? [T=decimal?]")
        chkGlobal("<T> (T, T): T", "decimal,integer?", "decimal? [T=decimal?]")
        chkGlobal("<T> (T, T): T", "integer?,decimal?", "decimal? [T=decimal?]")
        chkGlobal("<T> (T, T): T", "decimal?,integer?", "decimal? [T=decimal?]")

        chkGlobal("<T> (T, T): T", "big_integer,decimal?", "decimal? [T=decimal?]")
        chkGlobal("<T> (T, T): T", "decimal?,big_integer", "decimal? [T=decimal?]")
        chkGlobal("<T> (T, T): T", "big_integer?,decimal", "decimal? [T=decimal?]")
        chkGlobal("<T> (T, T): T", "decimal,big_integer?", "decimal? [T=decimal?]")
        chkGlobal("<T> (T, T): T", "big_integer?,decimal?", "decimal? [T=decimal?]")
        chkGlobal("<T> (T, T): T", "decimal?,big_integer?", "decimal? [T=decimal?]")
    }

    @Test fun testConversionComplexNullable() {
        chkGlobal("<T> (T?, T?): T", "integer,decimal?", "decimal [T=decimal]")
        chkGlobal("<T> (T?, T?): T", "decimal?,integer", "decimal [T=decimal]")
        chkGlobal("<T> (T?, T?): T", "integer?,decimal", "decimal [T=decimal]")
        chkGlobal("<T> (T?, T?): T", "decimal,integer?", "decimal [T=decimal]")
        chkGlobal("<T> (T?, T?): T", "integer?,decimal?", "decimal [T=decimal]")
        chkGlobal("<T> (T?, T?): T", "decimal?,integer?", "decimal [T=decimal]")

        chkGlobal("<T> (T?, T): T", "integer,decimal?", "decimal? [T=decimal?]")
        chkGlobal("<T> (T?, T): T", "decimal?,integer", "decimal [T=decimal]")
        chkGlobal("<T> (T?, T): T", "integer?,decimal", "decimal [T=decimal]")
        chkGlobal("<T> (T?, T): T", "decimal,integer?", "decimal? [T=decimal?]")
        chkGlobal("<T> (T?, T): T", "integer?,decimal?", "decimal? [T=decimal?]")
        chkGlobal("<T> (T?, T): T", "decimal?,integer?", "decimal? [T=decimal?]")

        chkGlobal("<T> (T, T?): T", "integer,decimal?", "decimal [T=decimal]")
        chkGlobal("<T> (T, T?): T", "decimal?,integer", "decimal? [T=decimal?]")
        chkGlobal("<T> (T, T?): T", "integer?,decimal", "decimal? [T=decimal?]")
        chkGlobal("<T> (T, T?): T", "decimal,integer?", "decimal [T=decimal]")
        chkGlobal("<T> (T, T?): T", "integer?,decimal?", "decimal? [T=decimal?]")
        chkGlobal("<T> (T, T?): T", "decimal?,integer?", "decimal? [T=decimal?]")
    }

    @Test fun testConversionComplexNullableStrict() {
        chkGlobal("<T:-any> (@nullable T?, @nullable T?): T", "integer,decimal?", "n/a")
        chkGlobal("<T:-any> (@nullable T?, @nullable T?): T", "decimal?,integer", "n/a")
        chkGlobal("<T:-any> (@nullable T?, @nullable T?): T", "integer?,decimal", "n/a")
        chkGlobal("<T:-any> (@nullable T?, @nullable T?): T", "decimal,integer?", "n/a")
        chkGlobal("<T:-any> (@nullable T?, @nullable T?): T", "integer?,decimal?", "decimal [T=decimal]")
        chkGlobal("<T:-any> (@nullable T?, @nullable T?): T", "decimal?,integer?", "decimal [T=decimal]")

        chkGlobal("<T:-any> (@nullable T?, T?): T", "integer,decimal?", "n/a")
        chkGlobal("<T:-any> (@nullable T?, T?): T", "decimal?,integer", "decimal [T=decimal]")
        chkGlobal("<T:-any> (@nullable T?, T?): T", "integer?,decimal", "decimal [T=decimal]")
        chkGlobal("<T:-any> (@nullable T?, T?): T", "decimal,integer?", "n/a")
        chkGlobal("<T:-any> (@nullable T?, T?): T", "integer?,decimal?", "decimal [T=decimal]")
        chkGlobal("<T:-any> (@nullable T?, T?): T", "decimal?,integer?", "decimal [T=decimal]")

        chkGlobal("<T:-any> (T?, @nullable T?): T", "integer,decimal?", "decimal [T=decimal]")
        chkGlobal("<T:-any> (T?, @nullable T?): T", "decimal?,integer", "n/a")
        chkGlobal("<T:-any> (T?, @nullable T?): T", "integer?,decimal", "n/a")
        chkGlobal("<T:-any> (T?, @nullable T?): T", "decimal,integer?", "decimal [T=decimal]")
        chkGlobal("<T:-any> (T?, @nullable T?): T", "integer?,decimal?", "decimal [T=decimal]")
        chkGlobal("<T:-any> (T?, @nullable T?): T", "decimal?,integer?", "decimal [T=decimal]")
    }

    @Test fun testConversionComplexCompound() {
        chkGlobal("<T> (T, supplier<T>): T", "integer,supplier<decimal>", "decimal [T=decimal]")
        chkGlobal("<T> (T, supplier<T>): T", "decimal,supplier<integer>", "n/a")
        chkGlobal("<T> (supplier<T>, T): T", "supplier<decimal>,integer", "decimal [T=decimal]")
        chkGlobal("<T> (supplier<T>, T): T", "supplier<integer>,decimal", "n/a")
    }

    @Test fun testConversionBound() {
        chkGlobal("<T:-real> (T, T): T", "real32,int32", "real32 [T=real32]")
        chkGlobal("<T:-real> (T, T): T", "int32,real32", "real32 [T=real32]")
    }

    @Test fun testAny() {
        chkGlobal("<T> (T): T", "integer", "integer [T=integer]")
        chkGlobal("<T> (T): T", "integer?", "integer? [T=integer?]")
        chkGlobal("<T:-any> (T): T", "integer", "integer [T=integer]")
        chkGlobal("<T:-any> (T): T", "integer?", "n/a")
        chkGlobal("<T:-any?> (T): T", "integer", "integer [T=integer]")
        chkGlobal("<T:-any?> (T): T", "integer?", "integer? [T=integer?]")
        chkGlobal("<T:-any> (T?): T", "integer", "integer [T=integer]")
        chkGlobal("<T:-any> (T?): T", "integer?", "integer [T=integer]")
        chkGlobal("<T:-any> (@nullable T?): T", "integer", "n/a")
        chkGlobal("<T:-any> (@nullable T?): T", "integer?", "integer [T=integer]")
    }

    @Test fun testBoundUpper() {
        chkGlobal("<T:-num> (T): T", "num", "num [T=num]")
        chkGlobal("<T:-num> (T): T", "int", "int [T=int]")
        chkGlobal("<T:-num> (T): T", "real", "real [T=real]")
        chkGlobal("<T:-num> (T): T", "int32", "int32 [T=int32]")
        chkGlobal("<T:-num> (T): T", "str", "n/a")
        chkGlobal("<T:-num> (T): T", "bool", "n/a")

        chkGlobal("<T:-int> (T): T", "num", "n/a")
        chkGlobal("<T:-int> (T): T", "int", "int [T=int]")
        chkGlobal("<T:-int> (T): T", "int32", "int32 [T=int32]")
        chkGlobal("<T:-int> (T): T", "real", "n/a")
        chkGlobal("<T:-int> (T): T", "real32", "n/a")

        chkGlobal("<T:-int> (T, T): T", "num,num", "n/a")
        chkGlobal("<T:-int> (T, T): T", "int,num", "n/a")
        chkGlobal("<T:-int> (T, T): T", "num,int", "n/a")
        chkGlobal("<T:-int> (T, T): T", "int,int", "int [T=int]")
        chkGlobal("<T:-int> (T, T): T", "int32,int64", "int [T=int]")
        chkGlobal("<T:-int> (T, T): T", "int,real", "n/a")
        chkGlobal("<T:-int> (T, T): T", "real,int", "n/a")

        chkGlobal("<T:-int> (T, T): T", "int32,real32", "n/a")
        chkGlobal("<T:-any> (T, T): T", "int32,real32", "num [T=num]")
        chkGlobal("<T> (T, T): T", "int32,real32", "num [T=num]")
    }

    @Test fun testBoundLower() {
        chkGlobal("<T:+num> (T): T", "num", "num [T=num]")
        chkGlobal("<T:+num> (T): T", "int", "num [T=num]")
        chkGlobal("<T:+num> (T): T", "int32", "num [T=num]")
        chkGlobal("<T:+num> (T): T", "str", "n/a")
        chkGlobal("<T:+num> (T): T", "null", "num? [T=num?]")
        chkGlobal("<T:+num> (T): T", "int?", "num? [T=num?]")
        chkGlobal("<T:+num> (T): T", "str?", "n/a")

        chkGlobal("<T:+int> (T): T", "num", "num [T=num]")
        chkGlobal("<T:+int> (T): T", "int", "int [T=int]")
        chkGlobal("<T:+int> (T): T", "int32", "int [T=int]")
        chkGlobal("<T:+int> (T): T", "int64", "int [T=int]")
        chkGlobal("<T:+int> (T): T", "real", "num [T=num]")
        chkGlobal("<T:+int> (T): T", "real32", "num [T=num]")
        chkGlobal("<T:+int> (T): T", "str", "n/a")
        chkGlobal("<T:+int> (T): T", "null", "int? [T=int?]")
        chkGlobal("<T:+int> (T): T", "int32?", "int? [T=int?]")
        chkGlobal("<T:+int> (T): T", "real?", "num? [T=num?]")
        chkGlobal("<T:+int> (T): T", "str?", "n/a")

        chkGlobal("<T:+int32> (T): T", "num", "num [T=num]")
        chkGlobal("<T:+int32> (T): T", "int", "int [T=int]")
        chkGlobal("<T:+int32> (T): T", "int32", "int32 [T=int32]")
        chkGlobal("<T:+int32> (T): T", "int64", "int [T=int]")
        chkGlobal("<T:+int32> (T): T", "real", "num [T=num]")
        chkGlobal("<T:+int32> (T): T", "real32", "num [T=num]")
        chkGlobal("<T:+int32> (T): T", "str", "n/a")
        chkGlobal("<T:+int32> (T): T", "null", "int32? [T=int32?]")
        chkGlobal("<T:+int32> (T): T", "real32?", "num? [T=num?]")
        chkGlobal("<T:+int32> (T): T", "str?", "n/a")
    }

    @Test fun testCaseFilter() {
        val h = "<T>(collection<T>,(T)->boolean):list<T>"
        chkGlobal(h, "collection<int>,(int)->boolean", "list<int> [T=int]")
        chkGlobal(h, "list<int>,(int)->boolean", "list<int> [T=int]")
        chkGlobal(h, "set<int>,(int)->boolean", "list<int> [T=int]")
        chkGlobal(h, "collection<int>,(num)->boolean", "list<int> [T=int]")
        chkGlobal(h, "collection<int>,(int32)->boolean", "n/a")
    }

    @Test fun testCaseMap() {
        val h = "<T,R>(collection<T>,(T)->R):list<R>"
        chkGlobal(h, "collection<int>,(int)->text", "list<text> [T=int,R=text]")
        chkGlobal(h, "list<int>,(int)->text", "list<text> [T=int,R=text]")
        chkGlobal(h, "set<int>,(int)->text", "list<text> [T=int,R=text]")
    }

    @Test fun testCaseFlatMap() {
        val h = "<T,R>(collection<T>,(T)->collection<R>):list<R>"
        chkGlobal(h, "collection<int>,(int)->collection<text>", "list<text> [T=int,R=text]")
        chkGlobal(h, "collection<int>,(int)->list<text>", "list<text> [T=int,R=text]")
        chkGlobal(h, "collection<int>,(int)->set<text>", "list<text> [T=int,R=text]")
        chkGlobal(h, "list<int>,(int)->collection<text>", "list<text> [T=int,R=text]")
        chkGlobal(h, "set<int>,(int)->collection<text>", "list<text> [T=int,R=text]")
        chkGlobal(h, "collection<int>,(int)->text", "n/a")
    }

    @Test fun testCaseFold() {
        val h = "<T,R>(R,collection<T>,(R,T)->R):R"
        chkGlobal(h, "text,collection<int>,(text,int)->text", "text [T=int,R=text]")
        chkGlobal(h, "text,list<int>,(text,int)->text", "text [T=int,R=text]")
        chkGlobal(h, "text,set<int>,(text,int)->text", "text [T=int,R=text]")

        chkGlobal(h, "text,collection<int>,(text,num)->text", "text [T=int,R=text]")
        chkGlobal(h, "text,collection<int>,(text,int32)->text", "n/a")

        chkGlobal(h, "int,collection<text>,(int,text)->num", "n/a")
        chkGlobal(h, "int,collection<text>,(int,text)->int", "int [T=text,R=int]")
        chkGlobal(h, "int,collection<text>,(int,text)->int32", "int [T=text,R=int]")
        chkGlobal(h, "int,collection<text>,(num,text)->int", "int [T=text,R=int]")
        chkGlobal(h, "int,collection<text>,(int32,text)->int", "n/a")
    }

    @Test fun testCaseBoundDependsOnParam() {
        val h = "<T,R:-collection<T>>(T,collection<R>):R"
        chkGlobal(h, "int,collection<collection<int>>", "collection<int> [T=int,R=collection<int>]")
        chkGlobal(h, "int,list<collection<int>>", "collection<int> [T=int,R=collection<int>]")
        chkGlobal(h, "int,set<collection<int>>", "collection<int> [T=int,R=collection<int>]")

        chkGlobal(h, "int,collection<list<int>>", "list<int> [T=int,R=list<int>]")
        chkGlobal(h, "int,collection<set<int>>", "set<int> [T=int,R=set<int>]")
        chkGlobal(h, "int,list<list<int>>", "list<int> [T=int,R=list<int>]")
        chkGlobal(h, "int,set<list<int>>", "list<int> [T=int,R=list<int>]")

        chkGlobal(h, "num,collection<collection<int>>", "n/a")
        chkGlobal(h, "int32,collection<collection<int>>", "n/a") //TODO support in the future, complex
        chkGlobal(h, "int32,collection<list<int>>", "n/a") //TODO support in the future, complex
    }

    @Test fun testCaseListOfNoArg() {
        chkGlobal("<T>():list<T>", "", "unresolved:T")
        chkGlobal("<T>():list<T>", ":list<int>", "list<int> [T=int]")
        chkGlobal("<T>():list<T>", ":collection<int>", "list<int> [T=int]")
        chkGlobal("<T>():list<T>", ":set<int>", "unresolved:T")
        chkGlobal("<T>():list<T>", ":map<int,int>", "unresolved:T")
        chkGlobal("<T>():list<T>", ":int", "unresolved:T")

        chkGlobal("<T>():collection<T>", ":collection<int>", "collection<int> [T=int]")
        chkGlobal("<T>():collection<T>", ":list<int>", "unresolved:T")
        chkGlobal("<T>():collection<T>", ":set<int>", "unresolved:T")

        chkGlobal("<T>():list<T>?", ":list<int>", "unresolved:T")
        chkGlobal("<T>():list<T>?", ":list<int>?", "list<int>? [T=int]")
        chkGlobal("<T>():list<T>", ":list<int>?", "list<int> [T=int]")
        chkGlobal("<T>():list<T>?", ":collection<int>", "unresolved:T")
        chkGlobal("<T>():list<T>?", ":collection<int>?", "list<int>? [T=int]")
        chkGlobal("<T>():list<T>", ":collection<int>?", "list<int> [T=int]")

        chkGlobal("<T>():collection<T>?", ":collection<int>", "unresolved:T")
        chkGlobal("<T>():collection<T>?", ":collection<int>?", "collection<int>? [T=int]")
        chkGlobal("<T>():collection<T>", ":collection<int>?", "collection<int> [T=int]")
        chkGlobal("<T>():collection<T>", ":list<int>?", "unresolved:T")
        chkGlobal("<T>():collection<T>", ":set<int>?", "unresolved:T")
        chkGlobal("<T>():collection<T>?", ":list<int>?", "unresolved:T")
        chkGlobal("<T>():collection<T>?", ":set<int>?", "unresolved:T")
    }

    @Test fun testCaseListOfSomeArgs() {
        chkGlobal("<T,R>(T):_collection<R>", "int:_collection<real>", "_collection<real> [T=int,R=real]")
        chkGlobal("<T,R>(T):_collection<R>", "int:_iterable<real>", "_collection<real> [T=int,R=real]")
        chkGlobal("<T,R>(T):_collection<R>", "int:_list<real>", "unresolved:R")
    }

    @Test fun testVarianceResolution() {
        chkGlobal("<T>(consumer<T>):list<T>", "consumer<int>", "list<int> [T=int]")
        chkGlobal("<T>(supplier<T>):list<T>", "supplier<int>", "list<int> [T=int]")
        chkGlobal("<T>((T)->unit):list<T>", "(int)->unit", "list<int> [T=int]")
        chkGlobal("<T>(()->T):list<T>", "()->int", "list<int> [T=int]")
        chkGlobal("<T>((T,text)):list<T>", "(int,text)", "list<int> [T=int]")
    }

    @Test fun testCommonSuperType() {
        chkGlobal("<T> (T, T): T", "list<text>,set<text>", "collection<text> [T=collection<text>]")
        chkGlobal("<T> (T, T): T", "set<text>,list<text>", "collection<text> [T=collection<text>]")
        chkGlobal("<T> (T, T): T", "list<text>,collection<text>", "collection<text> [T=collection<text>]")
        chkGlobal("<T> (T, T): T", "collection<text>,set<text>", "collection<text> [T=collection<text>]")
        chkGlobal("<T> (T, T): T", "list<text>,set<integer>", "collection<*> [T=collection<*>]")
        chkGlobal("<T> (T, T): T", "collection<text>,set<integer>", "collection<*> [T=collection<*>]")
        chkGlobal("<T> (T, T): T", "collection<text>,collection<integer>", "collection<*> [T=collection<*>]")
        chkGlobal("<T> (T, T): T", "collection<int>,collection<real>", "collection<-num> [T=collection<-num>]")
    }

    @Test fun testWildcardCommonSuperTypeExact() {
        chkGlobal("<T>(T,T):unit", "list<int>,list<int>", "unit [T=list<int>]")
        chkGlobal("<T>(T,T):unit", "list<int>,list<num>", "unit [T=list<-num>]")
        chkGlobal("<T>(T,T):unit", "list<num>,list<int>", "unit [T=list<-num>]")
    }

    @Test fun testWildcardCommonSuperTypeSub() {
        chkGlobal("<T>(T,T):unit", "list<-int>,list<int>", "unit [T=list<-int>]")
        chkGlobal("<T>(T,T):unit", "list<-num>,list<int>", "unit [T=list<-num>]")
        chkGlobal("<T>(T,T):unit", "list<-int>,list<num>", "unit [T=list<-num>]")
        chkGlobal("<T>(T,T):unit", "list<-int32>,list<int>", "unit [T=list<-int>]")
        chkGlobal("<T>(T,T):unit", "list<-str>,list<int>", "unit [T=list<*>]")
        chkGlobal("<T>(T,T):unit", "list<-int>,list<str>", "unit [T=list<*>]")

        chkGlobal("<T>(T,T):unit", "list<-int>,list<-int>", "unit [T=list<-int>]")
        chkGlobal("<T>(T,T):unit", "list<-int>,list<-num>", "unit [T=list<-num>]")
        chkGlobal("<T>(T,T):unit", "list<-int>,list<-int32>", "unit [T=list<-int>]")
        chkGlobal("<T>(T,T):unit", "list<-int>,list<-str>", "unit [T=list<*>]")
    }

    @Test fun testWildcardCommonSuperTypeSuper() {
        chkGlobal("<T>(T,T):unit", "list<+int>,list<int>", "unit [T=list<+int>]")
        chkGlobal("<T>(T,T):unit", "list<+num>,list<int>", "unit [T=list<+int>]")
        chkGlobal("<T>(T,T):unit", "list<+int32>,list<int>", "unit [T=list<+int32>]")
        chkGlobal("<T>(T,T):unit", "list<+int>,list<num>", "unit [T=list<+int>]")
        chkGlobal("<T>(T,T):unit", "list<+int>,list<int32>", "unit [T=list<+int32>]")
        chkGlobal("<T>(T,T):unit", "list<+str>,list<int>", "unit [T=list<*>]")
        chkGlobal("<T>(T,T):unit", "list<+int>,list<str>", "unit [T=list<*>]")

        chkGlobal("<T>(T,T):unit", "list<+int>,list<-int>", "unit [T=list<*>]")
        chkGlobal("<T>(T,T):unit", "list<+num>,list<-int>", "unit [T=list<*>]")
        chkGlobal("<T>(T,T):unit", "list<+int>,list<-num>", "unit [T=list<*>]")

        chkGlobal("<T>(T,T):unit", "list<+int>,list<+int>", "unit [T=list<+int>]")
        chkGlobal("<T>(T,T):unit", "list<+int>,list<+num>", "unit [T=list<+int>]")
        chkGlobal("<T>(T,T):unit", "list<+int>,list<+int32>", "unit [T=list<+int32>]")
        chkGlobal("<T>(T,T):unit", "list<+int>,list<+str>", "unit [T=list<*>]")
    }

    @Test fun testFunctionTypeCommonSuperType() {
        chkGlobal("<T>(T,T):unit", "(int)->int,(int)->int", "unit [T=(int)->int]")
        chkGlobal("<T>(T,T):unit", "(int)->int,(int)->num", "unit [T=(int)->num]")
        chkGlobal("<T>(T,T):unit", "(int)->int,(int)->int32", "unit [T=(int)->int]")
        chkGlobal("<T>(T,T):unit", "(int)->int,(num)->int", "unit [T=(int)->int]")
        chkGlobal("<T>(T,T):unit", "(int)->int,(int32)->int", "unit [T=(int32)->int]")
        chkGlobal("<T>(T,T):unit", "(int64)->int,(int32)->int", "unit [T=(nothing)->int]")

        chkGlobal("<T>(T,T):unit", "(int,int)->int,(int,int)->int", "unit [T=(int,int)->int]")
        chkGlobal("<T>(T,T):unit", "(int,int)->int,(int,int)->num", "unit [T=(int,int)->num]")
        chkGlobal("<T>(T,T):unit", "(int,int)->int,(int,int)->int32", "unit [T=(int,int)->int]")

        chkGlobal("<T>(T,T):unit", "(int,int)->int,(num,int)->int", "unit [T=(int,int)->int]")
        chkGlobal("<T>(T,T):unit", "(int,int)->int,(int32,int)->int", "unit [T=(int32,int)->int]")
        chkGlobal("<T>(T,T):unit", "(int,num)->int,(num,int)->int", "unit [T=(int,int)->int]")

        chkGlobal("<T>(T,T):unit", "(int,int32)->int,(int64,int)->int", "unit [T=(int64,int32)->int]")
        chkGlobal("<T>(T,T):unit", "(num,int32)->int,(int64,int)->int", "unit [T=(int64,int32)->int]")
        chkGlobal("<T>(T,T):unit", "(int,int32)->int,(int64,num)->int", "unit [T=(int64,int32)->int]")
        chkGlobal("<T>(T,T):unit", "(num,int32)->int,(int64,num)->int", "unit [T=(int64,int32)->int]")

        chkGlobal("<T>(T,T):unit", "(int,int32)->int,(int64,int)->num", "unit [T=(int64,int32)->num]")
        chkGlobal("<T>(T,T):unit", "(int,int32)->int,(int64,int)->int32", "unit [T=(int64,int32)->int]")

        chkGlobal("<T>(T,T):unit", "(real,int32)->int,(int64,int)->int", "unit [T=(nothing,int32)->int]")
        chkGlobal("<T>(T,T):unit", "(int,int32)->int,(int64,real)->int", "unit [T=(int64,nothing)->int]")
    }

    @Test fun testResultType() {
        chkGlobalEx("<T>():T", ":int", "[T=int] ():int")
        chkGlobalEx("<T>():T", ":int?", "[T=int?] ():int?")
        chkGlobalEx("<T>():T", ":unit", "[T=unit] ():unit")
        chkGlobalEx("<T>():T", ":(int,real?)", "[T=(int,real?)] ():(int,real?)")
    }
}
