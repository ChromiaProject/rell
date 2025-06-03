/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import net.postchain.rell.base.mtype.utils.MTestParser
import net.postchain.rell.base.mtype.utils.MTestScope
import org.junit.Test
import kotlin.test.assertEquals

class MTypeParamsMatchTest: BaseMTypeTest() {
    override fun initScope(b: MTestScope.Builder) {
        MTestScope.initBasic(b)
        MTestScope.initNumeric(b)
        MTestScope.initCollections(b, "_")
        MTestScope.initConsumerSupplier(b)
        MTestScope.initRellPrimitives(b)
        MTestScope.initRellCollections(b)
    }

    @Test fun testMatchTypeConSup() {
        chkMatchTypeParams("consumer<T>", "consumer<int>", "T < int")
        chkMatchTypeParams("supplier<T>", "supplier<int>", "T > int")
    }

    @Test fun testMatchTypeParamsBasic() {
        chkMatchTypeParams("T", "num", "T ~ num")
        chkMatchTypeParams("T", "int", "T ~ int")
        chkMatchTypeParams("T", "int32", "T ~ int32")
        chkMatchTypeParams("T", "text", "T ~ text")
        chkMatchTypeParams("T", "list<int>", "T ~ list<int>")
        chkMatchTypeParams("T", "(int,real)", "T ~ (int,real)")
        chkMatchTypeParams("T", "(int)->real", "T ~ (int)->real")
    }

    @Test fun testMatchTypeParamsNullable() {
        chkMatchTypeParams("T?", "int?", "T ~ int")
        chkMatchTypeParams("T?", "int", "T ~ int")
        chkMatchTypeParams("T?", "null", "T ~ null")
        chkMatchTypeParams("T?", "list<int>", "T ~ list<int>")
    }

    @Test fun testMatchTypeParamsTuple() {
        chkMatchTypeParams("(A,B)", "(int,real)", "A > int, B > real")
        chkMatchTypeParams("(A,B)", "int", "n/a")
        chkMatchTypeParams("(A,B)", "(int,real,text)", "n/a")
        chkMatchTypeParams("(A,B)", "(int,real)?", "n/a")

        chkMatchTypeParams("(A?,B)", "(int,real)", "A > int, B > real")
        chkMatchTypeParams("(A,B?)", "(int,real)", "A > int, B > real")
        chkMatchTypeParams("(A?,B?)", "(int,real)", "A > int, B > real")
        chkMatchTypeParams("(A,B)?", "(int,real)", "A > int, B > real")
    }

    @Test fun testMatchTypeParamsPair() {
        chkMatchTypeParams("pair<A,B>", "(int,real)", "A > int, B > real")
        chkMatchTypeParams("pair<A,B>", "int", "n/a")
        chkMatchTypeParams("pair<A,B>", "(int,real,text)", "n/a")
        chkMatchTypeParams("pair<A,B>", "(int,real)?", "n/a")

        chkMatchTypeParams("pair<A?,B>", "(int,real)", "A > int, B > real")
        chkMatchTypeParams("pair<A,B?>", "(int,real)", "A > int, B > real")
        chkMatchTypeParams("pair<A?,B?>", "(int,real)", "A > int, B > real")
        chkMatchTypeParams("pair<A,B>?", "(int,real)", "A > int, B > real")
    }

    @Test fun testMatchTypeParamsFunction() {
        chkMatchTypeParams("(A)->B", "(int)->real", "A < int, B > real")
        chkMatchTypeParams("(A)->B", "(int,text)->real", "n/a")
        chkMatchTypeParams("(A)->B", "()->real", "n/a")
    }

    @Test fun testMatchTypeParamsGenericEqual() {
        chkMatchTypeParams("_collection<T>", "_collection<int>", "T = int")
        chkMatchTypeParams("_collection<T>", "_list<int>", "T = int")
        chkMatchTypeParams("_collection<T>", "_set<int>", "T = int")
        chkMatchTypeParams("_collection<T>", "_iterable<int>", "n/a")
        chkMatchTypeParams("_collection<T>", "_array<int>", "n/a")

        chkMatchTypeParams("_iterable<T>", "_iterable<int>", "T = int")
        chkMatchTypeParams("_iterable<T>", "_array<int>", "T = int")
        chkMatchTypeParams("_iterable<T>", "_collection<int>", "T = int")
        chkMatchTypeParams("_iterable<T>", "_list<int>", "T = int")
        chkMatchTypeParams("_iterable<T>", "_set<int>", "T = int")

        chkMatchTypeParams("_collection<T>", "_collection<num>", "T = num")
        chkMatchTypeParams("_collection<T>", "_collection<int32>", "T = int32")
        chkMatchTypeParams("_collection<T>", "_collection<real>", "T = real")

        chkMatchTypeParams("_collection<T>", "_collection<-int>", "n/a")
        chkMatchTypeParams("_collection<T>", "_collection<+int>", "n/a")
    }

    @Test fun testMatchTypeParamsGenericSub() {
        chkMatchTypeParams("_collection<-T>", "_collection<int>", "T > int")
        chkMatchTypeParams("_collection<-T>", "_list<int>", "T > int")
        chkMatchTypeParams("_collection<-T>", "_set<int>", "T > int")
        chkMatchTypeParams("_collection<-T>", "_iterable<int>", "n/a")
        chkMatchTypeParams("_collection<-T>", "_array<int>", "n/a")

        chkMatchTypeParams("_collection<-T>", "_collection<-num>", "T > num")
        chkMatchTypeParams("_collection<-T>", "_collection<-int>", "T > int")
        chkMatchTypeParams("_collection<-T>", "_collection<-int32>", "T > int32")
        chkMatchTypeParams("_collection<-T>", "_collection<-real>", "T > real")

        chkMatchTypeParams("_collection<-T>", "_collection<+num>", "T = anything")
        chkMatchTypeParams("_collection<-T>", "_collection<+int>", "T = anything")
        chkMatchTypeParams("_collection<-T>", "_collection<+int32>", "T = anything")
    }

    @Test fun testMatchTypeParamsGenericSuper() {
        chkMatchTypeParams("_collection<+T>", "_collection<int>", "T < int")
        chkMatchTypeParams("_collection<+T>", "_list<int>", "T < int")
        chkMatchTypeParams("_collection<+T>", "_set<int>", "T < int")
        chkMatchTypeParams("_collection<+T>", "_iterable<int>", "n/a")
        chkMatchTypeParams("_collection<+T>", "_array<int>", "n/a")

        chkMatchTypeParams("_collection<+T>", "_collection<-num>", "T = nothing")
        chkMatchTypeParams("_collection<+T>", "_collection<-int>", "T = nothing")
        chkMatchTypeParams("_collection<+T>", "_collection<-int32>", "T = nothing")

        chkMatchTypeParams("_collection<+T>", "_collection<+num>", "T < num")
        chkMatchTypeParams("_collection<+T>", "_collection<+int>", "T < int")
        chkMatchTypeParams("_collection<+T>", "_collection<+int32>", "T < int32")
    }

    @Test fun testMatchTypeParamsGenericComplexFunctionParam() {
        chkMatchTypeParams("(_collection<A>)->text", "(_collection<int>)->text", "A = int")
        chkMatchTypeParams("(_collection<A>)->text", "(_iterable<int>)->text", "A = int")
        chkMatchTypeParams("(_collection<A>)->text", "(_list<int>)->text", "n/a")

        chkMatchTypeParams("(_collection<A>)->text", "(_collection<(int,real)>)->text", "A = (int,real)")
        chkMatchTypeParams("(_collection<(A,B)>)->text", "(_collection<(int,real)>)->text", "A = int, B = real")

        chkMatchTypeParams("(_collection<A>)->text", "(_collection<-int>)->text", "A < int")
        chkMatchTypeParams("(_collection<A>)->text", "(_collection<+int>)->text", "A > int")

        chkMatchTypeParams("(_collection<-A>)->text", "(_collection<int>)->text", "n/a")
        chkMatchTypeParams("(_collection<+A>)->text", "(_collection<int>)->text", "n/a")

        chkMatchTypeParams("(_collection<-A>)->text", "(_collection<-int>)->text", "A < int")
        chkMatchTypeParams("(_collection<-A>)->text", "(_collection<+int>)->text", "n/a")
        chkMatchTypeParams("(_collection<+A>)->text", "(_collection<-int>)->text", "n/a")
        chkMatchTypeParams("(_collection<+A>)->text", "(_collection<+int>)->text", "A > int")
    }

    @Test fun testMatchTypeParamsGenericComplexFunctionResult() {
        chkMatchTypeParams("()->_collection<A>", "()->_iterable<int>", "n/a")
        chkMatchTypeParams("()->_collection<A>", "()->_collection<int>", "A = int")
        chkMatchTypeParams("()->_collection<A>", "()->_list<int>", "A = int")

        chkMatchTypeParams("()->_collection<A>", "()->_collection<-int>", "n/a")
        chkMatchTypeParams("()->_collection<A>", "()->_collection<+int>", "n/a")

        chkMatchTypeParams("()->_collection<-A>", "()->_collection<int>", "A > int")
        chkMatchTypeParams("()->_collection<-A>", "()->_collection<-int>", "A > int")
        chkMatchTypeParams("()->_collection<-A>", "()->_collection<+int>", "A = anything")

        chkMatchTypeParams("()->_collection<+A>", "()->_collection<int>", "A < int")
        chkMatchTypeParams("()->_collection<+A>", "()->_collection<-int>", "A = nothing")
        chkMatchTypeParams("()->_collection<+A>", "()->_collection<+int>", "A < int")

        chkMatchTypeParams("()->_collection<A>", "()->_collection<(int,real)>", "A = (int,real)")
        chkMatchTypeParams("()->_collection<(A,B)>", "()->_collection<(int,real)>", "A = int, B = real")
    }

    @Test fun testMatchTypeParamsGenericComplexNestedGeneric() {
        chkMatchTypeParams("_iterable<A>", "_iterable<_list<int>>", "A = _list<int>")
        chkMatchTypeParams("_iterable<A>", "_iterable<-_list<int>>", "n/a")
        chkMatchTypeParams("_iterable<A>", "_iterable<+_list<int>>", "n/a")

        chkMatchTypeParams("_iterable<_collection<A>>", "_iterable<_collection<int>>", "A = int")
        chkMatchTypeParams("_iterable<_collection<A>>", "_iterable<_iterable<int>>", "n/a")
        chkMatchTypeParams("_iterable<_collection<A>>", "_iterable<_list<int>>", "n/a")
        chkMatchTypeParams("_iterable<_collection<A>>", "_iterable<_collection<-int>>", "n/a")
        chkMatchTypeParams("_iterable<_collection<A>>", "_iterable<_collection<+int>>", "n/a")
        chkMatchTypeParams("_iterable<_collection<A>>", "_iterable<-_collection<int>>", "n/a")
        chkMatchTypeParams("_iterable<_collection<A>>", "_iterable<+_collection<int>>", "n/a")

        chkMatchTypeParams("_iterable<_collection<-A>>", "_iterable<_collection<-int>>", "A = int")
        chkMatchTypeParams("_iterable<_collection<-A>>", "_iterable<_collection<+int>>", "n/a")
        chkMatchTypeParams("_iterable<_collection<-A>>", "_iterable<_collection<int>>", "n/a")
        chkMatchTypeParams("_iterable<_collection<-A>>", "_iterable<_iterable<-int>>", "n/a")
        chkMatchTypeParams("_iterable<_collection<-A>>", "_iterable<_list<-int>>", "n/a")
        chkMatchTypeParams("_iterable<_collection<-A>>", "_iterable<_collection<-num>>", "A = num")
        chkMatchTypeParams("_iterable<_collection<-A>>", "_iterable<_collection<-int32>>", "A = int32")
        chkMatchTypeParams("_iterable<_collection<-A>>", "_iterable<_collection<num>>", "n/a")
        chkMatchTypeParams("_iterable<_collection<-A>>", "_iterable<_collection<int32>>", "n/a")

        chkMatchTypeParams("_iterable<_collection<+A>>", "_iterable<_collection<+int>>", "A = int")
        chkMatchTypeParams("_iterable<_collection<+A>>", "_iterable<_collection<-int>>", "n/a")
        chkMatchTypeParams("_iterable<_collection<+A>>", "_iterable<_collection<int>>", "n/a")
        chkMatchTypeParams("_iterable<_collection<+A>>", "_iterable<_iterable<+int>>", "n/a")
        chkMatchTypeParams("_iterable<_collection<+A>>", "_iterable<_list<+int>>", "n/a")
    }

    @Test fun testMatchTypeParamsGenericComplexDoubleInversion() {
        chkMatchTypeParams("((_collection<A>)->B)->text", "((_collection<int>)->real)->text", "A = int, B < real")
        chkMatchTypeParams("((_collection<A>)->B)->text", "((_iterable<int>)->real)->text", "n/a")
        chkMatchTypeParams("((_collection<A>)->B)->text", "((_list<int>)->real)->text", "A = int, B < real")
        chkMatchTypeParams("((_collection<A>)->B)->text", "((_set<int>)->real)->text", "A = int, B < real")

        chkMatchTypeParams("((_collection<A>)->B)->text", "((_collection<-int>)->real)->text", "n/a")
        chkMatchTypeParams("((_collection<A>)->B)->text", "((_collection<+int>)->real)->text", "n/a")

        chkMatchTypeParams("((_collection<-A>)->B)->text", "((_collection<int>)->real)->text", "A > int, B < real")
        chkMatchTypeParams("((_collection<-A>)->B)->text", "((_collection<-int>)->real)->text", "A > int, B < real")
        chkMatchTypeParams("((_collection<-A>)->B)->text", "((_collection<+int>)->real)->text", "A = anything, B < real")

        chkMatchTypeParams("((_collection<+A>)->B)->text", "((_collection<int>)->real)->text", "A < int, B < real")
        chkMatchTypeParams("((_collection<+A>)->B)->text", "((_collection<-int>)->real)->text", "A = nothing, B < real")
        chkMatchTypeParams("((_collection<+A>)->B)->text", "((_collection<+int>)->real)->text", "A < int, B < real")
    }

    @Test fun testMatchTypeParamsConversion() {
        chkMatchTypeParams("int64", "int32", "OK")
        chkMatchTypeParams("real32", "int32", "OK")
        chkMatchTypeParams("real32", "int64", "OK")
        chkMatchTypeParams("real64", "int32", "OK")
        chkMatchTypeParams("real64", "int64", "OK")
        chkMatchTypeParams("real64", "real32", "OK")
        chkMatchTypeParams("real64", "real64", "OK")

        chkMatchTypeParams("int32", "int64", "n/a")
        chkMatchTypeParams("int64", "real32", "n/a")
        chkMatchTypeParams("real32", "real64", "n/a")
    }

    @Test fun testMatchTypeParamsCoverageCases1() {
        chkMatchTypeParams("T?", "int?", "T ~ int")
        chkMatchTypeParams("list<T?>", "list<int?>", "T = int")
        chkMatchTypeParams("()->T?", "()->int?", "T > int")
        chkMatchTypeParams("(list<T?>)->unit", "(list<int?>)->unit", "T = int")
        chkMatchTypeParams("(T?)->unit", "(int?)->unit", "T < int")
        chkMatchTypeParams("((T?)->text)->unit", "((int?)->text)->unit", "T > int")
        chkMatchTypeParams("list<list<T>>", "list<-list<int>>", "n/a")
        chkMatchTypeParams("list<list<T>>", "list<+list<int>>", "n/a")
        chkMatchTypeParams("(list<-T>)->unit", "(list<int>)->unit", "n/a")
        chkMatchTypeParams("(list<+T>)->unit", "(list<int>)->unit", "n/a")
    }

    @Test fun testMatchTypeParamsCoverageCases2() {
        chkMatchTypeParams("list<list<T>>", "list<list<int>>", "T = int")
        chkMatchTypeParams("list<list<-T>>", "list<list<-int>>", "T = int")
        chkMatchTypeParams("list<list<+T>>", "list<list<+int>>", "T = int")
        chkMatchTypeParams("list<list<T>>", "list<list<-int>>", "n/a")
        chkMatchTypeParams("list<list<T>>", "list<list<+int>>", "n/a")
        chkMatchTypeParams("list<list<-T>>", "list<list<int>>", "n/a")
        chkMatchTypeParams("list<list<+T>>", "list<list<int>>", "n/a")
    }

    @Test fun testMatchTypeParamsCoverageCases3() {
        chkMatchTypeParams("list<T>", "list<int>", "T = int")
        chkMatchTypeParams("list<T>", "list<-int>", "n/a")
        chkMatchTypeParams("list<T>", "list<+int>", "n/a")
        chkMatchTypeParams("list<-T>", "list<int>", "T > int")
        chkMatchTypeParams("list<-T>", "list<-int>", "T > int")
        chkMatchTypeParams("list<-T>", "list<+int>", "T = anything")
        chkMatchTypeParams("list<+T>", "list<int>", "T < int")
        chkMatchTypeParams("list<+T>", "list<-int>", "T = nothing")
        chkMatchTypeParams("list<+T>", "list<+int>", "T < int")
    }

    @Test fun testMatchTypeParamsCoverageCases4() {
        chkMatchTypeParams("()->T", "()->int", "T > int")
        chkMatchTypeParams("(()->T,text)", "(()->int,text)", "T > int")
        chkMatchTypeParams("list<()->T>", "list<()->int>", "T = int")

        chkMatchTypeParams("T?", "int?", "T ~ int")
        chkMatchTypeParams("(T?,text)", "(int?,text)", "T > int")
        chkMatchTypeParams("list<T?>", "list<int?>", "T = int")

        chkMatchTypeParams("(T,text)", "(int,text)", "T > int")
        chkMatchTypeParams("((T,text),boolean)", "((int,text),boolean)", "T > int")
        chkMatchTypeParams("list<(T,text)>", "list<(int,text)>", "T = int")

        chkMatchTypeParams("pair<T,text>", "(int,text)", "T > int")
        chkMatchTypeParams("(pair<T,text>,boolean)", "((int,text),boolean)", "T > int")
        chkMatchTypeParams("(pair<T,text>,boolean)", "(pair<int,text>,boolean)", "T > int")
        chkMatchTypeParams("list<pair<T,text>>", "list<(int,text)>", "n/a")
        chkMatchTypeParams("list<pair<T,text>>", "list<pair<int,text>>", "T = int")

        chkMatchTypeParams("list<T>", "list<int>", "T = int")
        chkMatchTypeParams("(list<T>,text)", "(list<int>,text)", "T = int")
        chkMatchTypeParams("list<list<T>>", "list<list<int>>", "T = int")
    }

    private fun chkMatchTypeParams(type1: String, type2: String, exp: String) {
        //val typeParams = ('A' .. 'Z').map { it.toString() }.toImmSet()
        val mType1 = MTestParser.parseType(type1, scopeB.build())
        val mType2 = MTestParser.parseType(type2, scopeB.build())
        val res = mutableListOf<String>()

        val ok = mType1.matchTypeParamsIn(mType2) { param, type, rel ->
            val k = when (rel) {
                M_TypeMatchRelation.CONVERT -> "~"
                M_TypeMatchRelation.EQUAL -> "="
                M_TypeMatchRelation.SUPER -> ">"
                M_TypeMatchRelation.SUB -> "<"
            }
            res.add("${param.name} $k ${type.strCode()}")
        }
        val act = when {
            !ok -> "n/a"
            res.isEmpty() -> "OK"
            else -> res.sorted().joinToString(", ")
        }
        assertEquals(exp, act)
    }
}
