/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvInteger
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lib.Lib_Math
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_GtvCompatibility
import net.postchain.rell.base.model.R_PrimitiveType
import net.postchain.rell.base.model.R_TypeSqlAdapter
import net.postchain.rell.base.model.R_TypeSqlAdapter_Primitive
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Comparator
import net.postchain.rell.base.sql.SqlConstants
import net.postchain.rell.base.utils.toImmList
import org.jooq.impl.SQLDataType
import java.math.BigDecimal
import java.math.BigInteger
import java.sql.PreparedStatement
import java.sql.ResultSet

object Lib_Type_Integer {
    val NAMESPACE = Ld_NamespaceDsl.make {
        alias("timestamp", "integer")

        type("integer", rType = R_IntegerType) {
            constant("MIN_VALUE", Long.MIN_VALUE)
            constant("MAX_VALUE", Long.MAX_VALUE)

            constructor(pure = true) {
                param("value", "text")
                param("radix", "integer", arity = L_ParamArity.ZERO_ONE)
                bodyOpt1 { a, b ->
                    val r = b?.asInteger() ?: 10
                    calcFromText(a, r)
                }
            }

            constructor {
                param("value", "decimal")
                bodyRaw(Lib_Type_Decimal.ToInteger)
            }

            staticFunction("from_text", "integer", pure = true) {
                param("value", "text")
                param("radix", "integer", arity = L_ParamArity.ZERO_ONE)
                bodyOpt1 { a, b ->
                    val r = b?.asInteger() ?: 10
                    calcFromText(a, r)
                }
            }

            staticFunction("from_hex", "integer", pure = true) {
                alias("parseHex", C_MessageType.ERROR)
                param("value", "text")
                body { a ->
                    val s = a.asString()
                    val r = try {
                        java.lang.Long.parseUnsignedLong(s, 16)
                    } catch (e: NumberFormatException) {
                        throw Rt_Exception.common("fn:integer.from_hex:$s", "Invalid hex number: '$s'")
                    }
                    Rt_IntValue.get(r)
                }
            }

            function("abs", "integer") {
                bodyRaw(Lib_Math.Abs_Integer)
            }

            function("min", "integer") {
                param("value", "integer")
                bodyRaw(Lib_Math.Min_Integer)
            }

            function("min", "big_integer", pure = true) {
                param("value", "big_integer")
                dbFunctionSimple("min", "LEAST")
                body { a, b ->
                    val v1 = a.asInteger()
                    val v2 = b.asBigInteger()
                    val r = BigInteger.valueOf(v1).min(v2)
                    Rt_BigIntegerValue.get(r)
                }
            }

            function("min", "decimal", pure = true) {
                param("value", "decimal")
                dbFunctionSimple("min", "LEAST")
                body { a, b ->
                    val v1 = a.asInteger()
                    val v2 = b.asDecimal()
                    val r = BigDecimal(v1).min(v2)
                    Rt_DecimalValue.get(r)
                }
            }

            function("max", "integer") {
                param("value", "integer")
                bodyRaw(Lib_Math.Max_Integer)
            }

            function("max", "big_integer", pure = true) {
                param("value", "big_integer")
                dbFunctionSimple("max", "GREATEST")
                body { a, b ->
                    val v1 = a.asInteger()
                    val v2 = b.asBigInteger()
                    val r = BigInteger.valueOf(v1).max(v2)
                    Rt_BigIntegerValue.get(r)
                }
            }

            function("max", "decimal", pure = true) {
                param("value", "decimal")
                dbFunctionSimple("max", "GREATEST")
                body { a, b ->
                    val v1 = a.asInteger()
                    val v2 = b.asDecimal()
                    val r = BigDecimal(v1).max(v2)
                    Rt_DecimalValue.get(r)
                }
            }

            function("pow", result = "integer", pure = true) {
                param(name = "exponent", type = "integer")
                dbFunctionSimple(fnSimpleName, SqlConstants.FN_INTEGER_POWER)
                body { a, b ->
                    val base = a.asInteger()
                    val exp = b.asInteger()
                    val res = Lib_BigIntegerMath.genericPower(fnSimpleName, base, exp, Lib_BigIntegerMath.NumericType_Long)
                    Rt_IntValue.get(res)
                }
            }

            function("sign", "integer", pure = true) {
                alias("signum", C_MessageType.ERROR)
                dbFunctionSimple("sign", "SIGN")
                body { a ->
                    val v = a.asInteger()
                    val r = java.lang.Long.signum(v).toLong()
                    Rt_IntValue.get(r)
                }
            }

            function("to_big_integer", "big_integer") {
                bodyRaw(Lib_Type_BigInteger.FromInteger)
            }

            function("to_decimal", "decimal") {
                bodyRaw(Lib_Type_Decimal.FromInteger)
            }

            function("to_text", "text", pure = true) {
                alias("str")
                dbFunctionCast("int.to_text", "TEXT")
                body { a ->
                    val v = a.asInteger()
                    Rt_TextValue.get(v.toString())
                }
            }

            function("to_text", "text", pure = true) {
                alias("str")
                param("radix", "integer")
                body { a, b ->
                    val v = a.asInteger()
                    val r = b.asInteger()
                    if (r < Character.MIN_RADIX || r > Character.MAX_RADIX) {
                        throw Rt_Exception.common("fn_int_str_radix:$r", "Invalid radix: $r")
                    }
                    val s = v.toString(r.toInt())
                    Rt_TextValue.get(s)
                }
            }

            function("to_hex", "text", pure = true) {
                alias("hex", C_MessageType.ERROR)
                body { a ->
                    val v = a.asInteger()
                    Rt_TextValue.get(java.lang.Long.toHexString(v))
                }
            }
        }
    }

    private fun calcFromText(a: Rt_Value, radix: Long): Rt_Value {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            throw Rt_Exception.common("fn:integer.from_text:radix:$radix", "Invalid radix: $radix")
        }

        val s = a.asString()
        val r = try {
            java.lang.Long.parseLong(s, radix.toInt())
        } catch (e: NumberFormatException) {
            throw Rt_Exception.common("fn:integer.from_text:$s", "Invalid number: '$s'")
        }

        return Rt_IntValue.get(r)
    }
}

object R_IntegerType: R_PrimitiveType("integer") {
    override fun defaultValue() = Rt_IntValue.ZERO

    override fun comparator() = Rt_Comparator.create { it.asInteger() }
    override fun fromCli(s: String): Rt_Value = Rt_IntValue.get(s.toLong())

    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_Integer
    override fun createSqlAdapter(): R_TypeSqlAdapter = R_TypeSqlAdapter_Integer

    override fun getLibTypeDef() = Lib_Rell.INTEGER_TYPE

    private object R_TypeSqlAdapter_Integer: R_TypeSqlAdapter_Primitive("integer", SQLDataType.BIGINT) {
        override fun toSqlValue(value: Rt_Value) = value.asInteger()

        override fun toSql(stmt: PreparedStatement, idx: Int, value: Rt_Value) {
            stmt.setLong(idx, value.asInteger())
        }

        override fun fromSql(rs: ResultSet, idx: Int, nullable: Boolean): Rt_Value {
            val v = rs.getLong(idx)
            return checkSqlNull(v == 0L, rs, R_IntegerType, nullable) ?: Rt_IntValue.get(v)
        }
    }
}

class Rt_IntValue private constructor(val value: Long): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.INTEGER.type()

    override fun type() = R_IntegerType
    override fun asInteger() = value
    override fun toFormatArg() = value
    override fun strCode(showTupleFieldNames: Boolean) = "int[$value]"
    override fun str() = "" + value
    override fun equals(other: Any?) = other is Rt_IntValue && value == other.value
    override fun hashCode() = java.lang.Long.hashCode(value)

    companion object {
        private const val NVALUES = 1000

        private val VALUES: List<Rt_Value> = (-NVALUES .. NVALUES).map { Rt_IntValue(it.toLong()) }.toImmList()

        val ZERO: Rt_Value = get(0)

        fun get(v: Long): Rt_Value {
            return if (v >= -NVALUES && v <= NVALUES) {
                VALUES[(v + NVALUES).toInt()]
            } else {
                Rt_IntValue(v)
            }
        }
    }
}

private object GtvRtConversion_Integer: GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(true, true)
    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = GtvInteger(rt.asInteger())

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val v = GtvRtUtils.gtvToInteger(ctx, gtv, R_IntegerType)
        return ctx.rtValue {
            Rt_IntValue.get(v)
        }
    }
}
