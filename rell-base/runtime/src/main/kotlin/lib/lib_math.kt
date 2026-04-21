/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.lib.type.Rt_BigIntegerValue
import net.postchain.rell.base.lib.type.Rt_DecimalValue
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceBodyDsl
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.expr.Db_SysFunction
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.simple
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal object Lib_Math {
    val Abs_Integer = C_SysFunctionBody.simple(Db_SysFunction.simple("abs", "ABS"), pure = true) { a ->
        val v = a.asInteger()
        if (v == Long.MIN_VALUE) {
            throw Rt_Exception.common("abs:integer:overflow:$v", "Integer overflow: $v")
        }
        val r = abs(v)
        Rt_IntValue.get(r)
    }

    val Abs_BigInteger = C_SysFunctionBody.simple(Db_SysFunction.simple("abs", "ABS"), pure = true) { a ->
        val v = a.asBigInteger()
        val r = v.abs()
        Rt_BigIntegerValue.get(r)
    }

    val Abs_Decimal = C_SysFunctionBody.simple(Db_SysFunction.simple("abs", "ABS"), pure = true) { a ->
        val v = a.asDecimal()
        val r = v.abs()
        Rt_DecimalValue.get(r)
    }

    val Min_Integer = C_SysFunctionBody.simple(Db_SysFunction.simple("min", "LEAST"), pure = true) { a, b ->
        val v1 = a.asInteger()
        val v2 = b.asInteger()
        val r = min(v1, v2)
        Rt_IntValue.get(r)
    }

    val Min_BigInteger = C_SysFunctionBody.simple(Db_SysFunction.simple("min", "LEAST"), pure = true) { a, b ->
        val v1 = a.asBigInteger()
        val v2 = b.asBigInteger()
        val r = v1.min(v2)
        Rt_BigIntegerValue.get(r)
    }

    val Min_Decimal = C_SysFunctionBody.simple(Db_SysFunction.simple("min", "LEAST"), pure = true) { a, b ->
        val v1 = a.asDecimal()
        val v2 = b.asDecimal()
        val r = v1.min(v2)
        Rt_DecimalValue.get(r)
    }

    val Max_Integer = C_SysFunctionBody.simple(Db_SysFunction.simple("max", "GREATEST"), pure = true) { a, b ->
        val v1 = a.asInteger()
        val v2 = b.asInteger()
        val r = max(v1, v2)
        Rt_IntValue.get(r)
    }

    val Max_BigInteger = C_SysFunctionBody.simple(Db_SysFunction.simple("max", "GREATEST"), pure = true) { a, b ->
        val v1 = a.asBigInteger()
        val v2 = b.asBigInteger()
        val r = v1.max(v2)
        Rt_BigIntegerValue.get(r)
    }

    val Max_Decimal = C_SysFunctionBody.simple(Db_SysFunction.simple("max", "GREATEST"), pure = true) { a, b ->
        val v1 = a.asDecimal()
        val v2 = b.asDecimal()
        val r = v1.max(v2)
        Rt_DecimalValue.get(r)
    }

    val NAMESPACE = Ld_NamespaceDsl.make {
        defFnAbs(this, "integer", Abs_Integer, since = "0.6.0")
        defFnAbs(this, "big_integer", Abs_BigInteger, since = "0.12.0")
        defFnAbs(this, "decimal", Abs_Decimal, since = "0.9.1")

        defFnMinMax(this, "integer", Min_Integer, Max_Integer, since = "0.6.0")
        defFnMinMax(this, "big_integer", Min_BigInteger, Max_BigInteger, since = "0.12.0")
        defFnMinMax(this, "decimal", Min_Decimal, Max_Decimal, since = "0.9.1")
    }

    private fun defFnAbs(d: Ld_NamespaceBodyDsl, type: String, fn: C_SysFunctionBody, since: String) {
        d.function("abs", type, since = since, comment = """
                Returns the absolute value of a $type value; i.e. the value itself if it's positive or its negation if
                it's negative.
                @return the absolute value of the argument
            """) {
            param("a", type, comment = "the $type for which to determine an absolute value")
            bodyRaw(fn)
        }
    }

    private fun defFnMinMax(
        d: Ld_NamespaceBodyDsl,
        type: String,
        fnMin: C_SysFunctionBody,
        fnMax: C_SysFunctionBody,
        since: String,
    ) {
        d.function("min", type, since = since, comment = """
                Returns the lesser of two $type values; i.e. `a` if `a < b`, or `b` otherwise.
                @return the lesser of `a` and `b`
            """) {
            param("a", type, comment = "the first $type to compare")
            param("b", type, comment = "the second $type to compare")
            bodyRaw(fnMin)
        }

        d.function("max", type, since = since, comment = """
                Returns the greater of two $type values; i.e. `a` if `a > b`, or `b` otherwise.
                @return the greater of `a` and `b`
            """) {
            param("a", type, comment = "the first $type to compare")
            param("b", type, comment = "the second $type to compare")
            bodyRaw(fnMax)
        }
    }
}
