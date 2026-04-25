/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvInteger
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.GtvCompatibility
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.utils.immSetOf
import org.jooq.impl.SQLDataType
import kotlin.reflect.full.createType

object Lib_Type_Boolean {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("boolean", since = "0.6.0") {
            rrType(RR_Type.Primitive(RR_PrimitiveKind.BOOLEAN))
            comment("""
                A data type with two possible values: `true` and `false`.

                Boolean logic can be performed with the `and`, `or` and `not` operators, for example:

                ```rell
                >>> true and false
                false
                >>> true or false
                true
                >>> not true
                false
                >>> not false
                true
                ```

                In addition, booleans are used in ternary expressions:

                ```rell
                >>> if (true) 1 else 2
                1
                >>> if (false) 1 else 2
                2
                ```

                The `in` operator returns a boolean:

                ```rell
                >>> 1 in list<integer>()
                false
                >>> 1 in [1, 2]
                true
                ```

                Booleans are used in `if`- and `if/else`-statements:

                ```rell
                >>> if (false) { print("hello"); } else { print("goodbye"); }
                goodbye
                >>> if (false) { print("hello"); }
                >>> if (true) { print("hello"); }
                hello
                >>>
                ```

                Boolean expressions are the basis of zero-argument `when`-statements (each expression to the left of a
                '`->`' symbol has boolean type (and in this context `else` is equivalent to `true`)):

                ```rell
                when {
                    x == 1 -> return 'One';
                    x >= 2 and x <= 7 -> return 'Several';
                    x == 11, x == 111 -> return 'Magic number';
                    some_value > 1000 -> return 'Special case';
                    else -> return 'Unknown';
                }
                ```

                Boolean conditions are used in while loops:

                ```rell
                while (x < 10) {
                    print(x);
                    x = x + 1;
                }
                ```

                Functions can have `boolean` return type (as can queries and operations), and indeed many functions and
                properties in the Rell standard library have `boolean` type.

                ```rell
                function foo(x: integer): boolean {
                    return x >= 10;
                }
                ```
            """)
        }
    }
}

object Rt_NativeConversion_Boolean: Rt_TypeNativeConversion {
    override val nativeTypes = immSetOf(Boolean::class.createType())
    override fun rtToNative(value: Rt_Value) = value.asBoolean()
    override fun nativeToRt(value: Any?) = Rt_BooleanValue.get(value as Boolean)
}

object Rt_ValueSqlAdapter_Boolean: Rt_ValueSqlAdapter_Primitive("boolean", SQLDataType.BOOLEAN) {
    override fun toSqlValue(value: Rt_Value) = value.asBoolean()

    override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) {
        params.setBoolean(idx, value.asBoolean())
    }

    override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
        val v = row.getBoolean(idx)
        return checkSqlNull(!v, row, name, nullable) ?: Rt_BooleanValue.get(v)
    }
}

class Rt_BooleanValue private constructor(val value: Boolean): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.BOOLEAN.type()

    private val strCode = "boolean[$value]"

    override fun type() = Rt_PrimitiveTypes.BOOLEAN
    override fun asBoolean() = value
    override fun toFormatArg() = value
    override fun strCode(showTupleFieldNames: Boolean) = strCode
    override fun str(format: StrFormat) = if (value) "true" else "false"
    override fun equals(other: Any?) = other is Rt_BooleanValue && value == other.value
    override fun hashCode() = value.hashCode()

    companion object {
        val TRUE: Rt_Value = Rt_BooleanValue(true)
        val FALSE: Rt_Value = Rt_BooleanValue(false)

        fun get(value: Boolean): Rt_Value = if (value) TRUE else FALSE
    }
}

object GtvRtConversion_Boolean: GtvRtConversion {
    override val directCompatibility = GtvCompatibility(fromGtv = true, toGtv = true)
    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = GtvInteger(if (rt.asBoolean()) 1L else 0L)

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val v = GtvRtUtils.gtvToBoolean(ctx, gtv, "boolean")
        return Rt_BooleanValue.get(v)
    }
}
