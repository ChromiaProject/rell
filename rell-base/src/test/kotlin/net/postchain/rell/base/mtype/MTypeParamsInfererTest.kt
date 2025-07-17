/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype

import net.postchain.rell.base.mtype.utils.MTestParser
import net.postchain.rell.base.mtype.utils.MTestScope
import net.postchain.rell.base.utils.checkEquals
import kotlin.test.Test
import kotlin.test.assertEquals

class MTypeParamsInfererTest: BaseMTypeTest() {
    override fun initScope(b: MTestScope.Builder) {
        MTestScope.initBasic(b)
        MTestScope.initNumeric(b)
        MTestScope.initCollections(b, "_")
        MTestScope.initRellPrimitives(b)
        MTestScope.initRellCollections(b)
        MTestScope.initMisc(b)
    }

    @Test fun testBasic() {
        chkRes("T", listOf(), "")
        chkRes("T", listOf("T ~ int"), "T = int")
        chkRes("T", listOf("T = int"), "T = int")
        chkRes("T", listOf("T < int"), "T = int")
        chkRes("T", listOf("T > int"), "T = int")
    }

    @Test fun testEqual() {
        chkRes("T", listOf("T = int"), "T = int")
        chkRes("T", listOf("T = int", "T = num"), "n/a")
        chkRes("T", listOf("T = int", "T < num"), "T = int")
        chkRes("T", listOf("T = int", "T < int"), "T = int")
        chkRes("T", listOf("T = int", "T < int32"), "n/a")
        chkRes("T", listOf("T = int", "T > num"), "n/a")
        chkRes("T", listOf("T = int", "T > int"), "T = int")
        chkRes("T", listOf("T = int", "T > int32"), "T = int")
        chkRes("T", listOf("T = int", "T ~ num"), "n/a")
        chkRes("T", listOf("T = int", "T ~ int"), "T = int")
        chkRes("T", listOf("T = int", "T ~ int32"), "T = int")
    }

    @Test fun testSub() {
        chkRes("T", listOf("T < int"), "T = int")
        chkRes("T", listOf("T < int", "T = num"), "n/a")
        chkRes("T", listOf("T < int", "T = int"), "T = int")
        chkRes("T", listOf("T < int", "T = int32"), "T = int32")
        chkRes("T", listOf("T < int", "T ~ num"), "n/a")
        chkRes("T", listOf("T < int", "T ~ int"), "T = int")
        chkRes("T", listOf("T < int", "T ~ int32"), "T = int32")
        chkRes("T", listOf("T < int", "T < num"), "T = int")
        chkRes("T", listOf("T < int", "T < int"), "T = int")
        chkRes("T", listOf("T < int", "T < int32"), "T = int32")
        chkRes("T", listOf("T < int", "T < real"), "n/a")
        chkRes("T", listOf("T < int", "T > num"), "n/a")
        chkRes("T", listOf("T < int", "T > int"), "T = int")
        chkRes("T", listOf("T < int", "T > int32"), "T = int32")
        chkRes("T", listOf("T < int", "T > real"), "n/a")
        chkRes("T", listOf("T < int", "T > unit"), "n/a")
    }

    @Test fun testSuper() {
        chkRes("T", listOf("T > int"), "T = int")
        chkRes("T", listOf("T > int", "T = num"), "T = num")
        chkRes("T", listOf("T > int", "T = int"), "T = int")
        chkRes("T", listOf("T > int", "T = int32"), "n/a")
        chkRes("T", listOf("T > int", "T ~ num"), "T = num")
        chkRes("T", listOf("T > int", "T ~ int"), "T = int")
        chkRes("T", listOf("T > int", "T ~ int32"), "T = int")
        chkRes("T", listOf("T > int", "T < num"), "T = int")
        chkRes("T", listOf("T > int", "T < int"), "T = int")
        chkRes("T", listOf("T > int", "T < int32"), "n/a")
        chkRes("T", listOf("T > int", "T < real"), "n/a")
        chkRes("T", listOf("T > int", "T > num"), "T = num")
        chkRes("T", listOf("T > int", "T > int"), "T = int")
        chkRes("T", listOf("T > int", "T > int32"), "T = int")
        chkRes("T", listOf("T > int", "T > real"), "T = num")
        chkRes("T", listOf("T > int", "T > str"), "n/a")
    }

    @Test fun testConvert() {
        chkRes("T", listOf("T ~ int"), "T = int")
        chkRes("T", listOf("T ~ int", "T ~ int32"), "T = int")
        chkRes("T", listOf("T ~ int64", "T ~ int32"), "T = int")
        chkRes("T", listOf("T ~ integer", "T ~ integer"), "T = integer")
        chkRes("T", listOf("T ~ integer", "T ~ big_integer"), "T = big_integer")
        chkRes("T", listOf("T ~ integer", "T ~ decimal"), "T = decimal")
        chkRes("T", listOf("T > integer", "T > decimal"), "n/a")
        chkRes("T", listOf("T ~ big_integer", "T ~ big_integer"), "T = big_integer")
        chkRes("T", listOf("T ~ big_integer", "T ~ decimal"), "T = decimal")
        chkRes("T", listOf("T > big_integer", "T > decimal"), "n/a")

        chkRes("T", listOf("T ~ integer", "T < integer"), "T = integer")
        chkRes("T", listOf("T ~ integer", "T < big_integer"), "T = big_integer")
        chkRes("T", listOf("T ~ integer", "T < decimal"), "T = decimal")
        chkRes("T", listOf("T ~ integer", "T < integer"), "T = integer")
        chkRes("T", listOf("T ~ big_integer", "T < integer"), "n/a")
        chkRes("T", listOf("T ~ decimal", "T < integer"), "n/a")

        chkRes("T", listOf("T ~ integer", "T > integer"), "T = integer")
        chkRes("T", listOf("T ~ integer", "T > big_integer"), "T = big_integer")
        chkRes("T", listOf("T ~ integer", "T > decimal"), "T = decimal")
        chkRes("T", listOf("T ~ integer", "T > integer"), "T = integer")
        chkRes("T", listOf("T ~ big_integer", "T > integer"), "n/a")
        chkRes("T", listOf("T ~ decimal", "T > integer"), "n/a")

        chkRes("T", listOf("T ~ integer", "T = integer"), "T = integer")
        chkRes("T", listOf("T ~ integer", "T = big_integer"), "T = big_integer")
        chkRes("T", listOf("T ~ integer", "T = decimal"), "T = decimal")
        chkRes("T", listOf("T ~ integer", "T = integer"), "T = integer")
        chkRes("T", listOf("T ~ big_integer", "T = integer"), "n/a")
        chkRes("T", listOf("T ~ decimal", "T = integer"), "n/a")
    }

    @Test fun testConvertAdvanced() {
        chkRes("T", listOf("T ~ integer", "T ~ big_integer", "T ~ decimal"), "T = decimal")
        chkRes("T", listOf("T > integer", "T ~ big_integer", "T ~ decimal"), "n/a")
        chkRes("T", listOf("T ~ integer", "T > big_integer", "T ~ decimal"), "n/a")
        chkRes("T", listOf("T ~ integer", "T ~ big_integer", "T > decimal"), "T = decimal")
        chkRes("T", listOf("T ~ integer", "T > big_integer", "T > decimal"), "n/a")
        chkRes("T", listOf("T > integer", "T ~ big_integer", "T > decimal"), "n/a")
        chkRes("T", listOf("T > integer", "T > big_integer", "T ~ decimal"), "n/a")

        chkRes("T", listOf("T < integer", "T ~ big_integer", "T ~ decimal"), "n/a")
        chkRes("T", listOf("T ~ integer", "T < big_integer", "T ~ decimal"), "n/a")
        chkRes("T", listOf("T ~ integer", "T ~ big_integer", "T < decimal"), "T = decimal")
    }

    @Test fun testConvertNullable() {
        chkRes("T", listOf("T ~ integer", "T ~ decimal"), "T = decimal")
        chkRes("T", listOf("T ~ integer?", "T ~ decimal"), "T = decimal?")
        chkRes("T", listOf("T ~ integer", "T ~ decimal?"), "T = decimal?")
        chkRes("T", listOf("T ~ integer?", "T ~ decimal?"), "T = decimal?")
    }

    @Test fun testConvertCommon() {
        chkRes("T", listOf("T ~ integer?", "T ~ decimal"), "T = decimal?")
        chkRes("T", listOf("T > integer?", "T ~ decimal"), "n/a")
        chkRes("T", listOf("T ~ integer?", "T > decimal"), "T = decimal?")
        chkRes("T", listOf("T > integer?", "T > decimal"), "n/a")

        chkRes("T:-decimal", listOf("T ~ integer?"), "n/a")
        chkRes("T:-decimal", listOf("T > integer?"), "n/a")
        chkRes("T:-decimal?", listOf("T ~ integer?"), "T = decimal?")
        chkRes("T:-decimal?", listOf("T > integer?"), "n/a")

        chkRes("T:+decimal", listOf("T ~ integer?"), "T = decimal?")
        chkRes("T:+decimal", listOf("T > integer?"), "n/a")
    }

    @Test fun testConvertBound() {
        chkRes("T:-decimal", listOf("T ~ integer"), "T = decimal")
        chkRes("T:-decimal", listOf("T ~ big_integer"), "T = decimal")
        chkRes("T:-decimal", listOf("T ~ decimal"), "T = decimal")

        chkRes("T:+decimal", listOf("T ~ integer"), "T = decimal")
        chkRes("T:+decimal", listOf("T ~ big_integer"), "T = decimal")
        chkRes("T:+decimal", listOf("T ~ decimal"), "T = decimal")
    }

    @Test fun testBoundDependent() {
        chkRes("A,B:-A", listOf("A > int", "B > num"), "n/a") //TODO support
        chkRes("A,B:-A", listOf("A > int", "B > int"), "A = int, B = int")
        chkRes("A,B:-A", listOf("A > int", "B > int32"), "A = int, B = int32")

        chkRes("A,B:+A", listOf("A > int", "B > num"), "A = int, B = num")
        chkRes("A,B:+A", listOf("A > int", "B > int"), "A = int, B = int")
        chkRes("A,B:+A", listOf("A > int", "B > int32"), "A = int, B = int")

        chkRes("T,R:-_collection<T>", listOf("T > int", "R > _collection<int>"), "R = _collection<int>, T = int")
        chkRes("T,R:-_collection<T>", listOf("T > int", "R > _iterable<int>"), "n/a")
        chkRes("T,R:-_collection<T>", listOf("T > int", "R > _list<int>"), "R = _list<int>, T = int")
        chkRes("T,R:-_collection<T>", listOf("T > int", "R > _set<int>"), "R = _set<int>, T = int")

        chkRes("T,R:+_collection<T>", listOf("T > int", "R > _collection<int>"), "R = _collection<int>, T = int")
        chkRes("T,R:+_collection<T>", listOf("T > int", "R > _iterable<int>"), "R = _iterable<int>, T = int")
        chkRes("T,R:+_collection<T>", listOf("T > int", "R > _list<int>"), "R = _collection<int>, T = int")
        chkRes("T,R:+_collection<T>", listOf("T > int", "R > _set<int>"), "R = _collection<int>, T = int")

        chkRes("T,R:-_collection<T>", listOf("T > int", "R > _collection<num>"), "n/a") //TODO support
        chkRes("T,R:-_collection<T>", listOf("T > int", "R > _collection<int32>"), "n/a") //TODO support

        chkRes("T,R:-_collection<-T>", listOf("T > int", "R > _collection<int32>"), "R = _collection<int32>, T = int")
        chkRes("T,R:-_collection<-T>", listOf("T > int", "R > _list<int32>"), "R = _list<int32>, T = int")
        chkRes("T,R:-_collection<+T>", listOf("T > int", "R > _collection<num>"), "R = _collection<num>, T = int")
        chkRes("T,R:-_collection<+T>", listOf("T > int", "R > _list<num>"), "R = _list<num>, T = int")
    }

    private fun chkRes(params: String, refs: List<String>, exp: String) {
        val act = calcRes(params, refs)
        assertEquals(exp, act)
    }

    private fun calcRes(params: String, refs: List<String>): String {
        val mParams = MTestParser.parseTypeParams(params, scopeB.build())
        val resolver = M_TypeParamsResolver(mParams)
        for (ref in refs) {
            val t = parseRef(ref, mParams)
            resolver.addRef(t.first, t.second, t.third)
        }

        val res = resolver.resolve()
        res ?: return "n/a"

        return res.entries.sortedBy { it.key.name }.joinToString(", ") {
            "${it.key.name} = ${it.value}"
        }
    }

    private fun parseRef(ref: String, params: List<M_TypeParam>): Triple<M_TypeParam, M_Type, M_TypeMatchRelation> {
        val parts = ref.split(" ")
        checkEquals(parts.size, 3)
        val name = parts[0]
        val param = params.first { it.name == name }
        val mType = MTestParser.parseType(parts[2], scopeB.build())
        val rel = when (parts[1]) {
            "=" -> M_TypeMatchRelation.EQUAL
            "<" -> M_TypeMatchRelation.SUB
            ">" -> M_TypeMatchRelation.SUPER
            "~" -> M_TypeMatchRelation.CONVERT
            else -> throw IllegalStateException(ref)
        }
        return Triple(param, mType, rel)
    }
}
