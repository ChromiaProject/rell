/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.sql.NoConnSqlExecutor
import net.postchain.rell.base.utils.CommonUtils
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals

class RellExprIpTester: RellExprTester() {
    override fun chkExpr(expr: String, expected: String, vararg args: TstVal) {
        val actual = calcExpr(expr, args.toList())
        assertEquals(expected, actual)
    }

    private fun calcExpr(expr: String, args: List<TstVal>): String {
        val expr2 = replaceParams(expr, args.size)
        val args2 = args.map { it as InterpTstVal }
        val types = args2.map { it.type }

        val globalCtx = Rt_GlobalContext(
                RellTestUtils.DEFAULT_COMPILER_OPTIONS,
                Rt_FailingPrinter,
                Rt_FailingPrinter,
                typeCheck = true,
        )

        val res = processExpr0(expr2, types) { app ->
            val ctx = ValCtx(app)
            val rtArgs = args2.map { it.rt(ctx) }
            val sqlCtx = Rt_RegularSqlContext.createNoExternalChains(app, Rt_ChainSqlMapping(0))
            val appCtx = Rt_AppContext(globalCtx, Rt_ChainContext.NULL, app, repl = false, test = false)
            val exeCtx = Rt_ExecutionContext(appCtx, Rt_NullOpContext, sqlCtx, NoConnSqlExecutor)
            RellTestUtils.callQuery(exeCtx, "q", rtArgs, RellTestUtils.ENCODER_STRICT)
        }

        return res
    }

    override fun compileExpr(expr: String, types: List<String>): String {
        val expr2 = replaceParams(expr, types.size)
        return compileExpr0(expr2, types)
    }

    private fun replaceParams(expr: String, count: Int): String {
        var s = expr
        for (idx in 0 until count) {
            s = s.replace("#$idx", paramName(idx))
        }
        return s
    }

    private fun compileExpr0(expr: String, types: List<String>): String {
        return processExpr0(expr, types) { "OK" }
    }

    private fun processExpr0(expr: String, types: List<String>, block: (R_App) -> String): String {
        val params = types.withIndex().joinToString(", ") { (idx, type) -> "${paramName(idx)}: $type" }
        val code = """
            entity company { name: text; }
            entity user { name: text; company; }
            query q($params) = $expr;
        """

        val res = RellTestUtils.processApp(code, processor = { block(it.rApp) })
        return res
    }

    private fun paramName(idx: Int) = "" + ('a' + idx)

    override fun rtErr(code: String) = "rt_err:$code"

    override fun vBool(v: Boolean): TstVal = InterpTstVal.Bool(v)
    override fun vInt(v: Long): TstVal = InterpTstVal.Integer(v)
    override fun vBigInt(v: BigInteger): TstVal = InterpTstVal.BigInteger(v)
    override fun vDec(v: BigDecimal): TstVal = InterpTstVal.Decimal(v)
    override fun vText(v: String): TstVal = InterpTstVal.Text(v)
    override fun vBytes(v: String): TstVal = InterpTstVal.Bytes(v)
    override fun vRowid(v: Long): TstVal = InterpTstVal.Rowid(v)
    override fun vJson(v: String): TstVal = InterpTstVal.Json(v)
    override fun vObj(ent: String, id: Long): TstVal = InterpTstVal.Obj(ent, id)

    private class ValCtx(val app: R_App)

    private sealed class InterpTstVal(val type: String): TstVal() {
        abstract fun rt(c: ValCtx): Rt_Value

        class Bool(val v: Boolean): InterpTstVal("boolean") {
            override fun rt(c: ValCtx): Rt_Value = Rt_BooleanValue.get(v)
        }

        class Integer(val v: Long): InterpTstVal("integer") {
            override fun rt(c: ValCtx): Rt_Value = Rt_IntValue.get(v)
        }

        class BigInteger(val v: java.math.BigInteger): InterpTstVal("big_integer") {
            override fun rt(c: ValCtx): Rt_Value = Rt_BigIntegerValue.get(v)
        }

        class Decimal(val v: BigDecimal): InterpTstVal("decimal") {
            override fun rt(c: ValCtx): Rt_Value = Rt_DecimalValue.get(v)
        }

        class Text(val v: String): InterpTstVal("text") {
            override fun rt(c: ValCtx): Rt_Value = Rt_TextValue.get(v)
        }

        class Bytes(str: String): InterpTstVal("byte_array") {
            private val v = CommonUtils.hexToBytes(str)
            override fun rt(c: ValCtx): Rt_Value = Rt_ByteArrayValue.get(v)
        }

        class Rowid(val v: Long): InterpTstVal("rowid") {
            override fun rt(c: ValCtx): Rt_Value = Rt_RowidValue.get(v)
        }

        class Json(val v: String): InterpTstVal("json") {
            override fun rt(c: ValCtx): Rt_Value = Rt_JsonValue.parse(v)
        }

        class Obj(val ent: String, val id: Long): InterpTstVal(ent) {
            override fun rt(c: ValCtx): Rt_Value {
                val entity = findEntity(c.app, ent)
                return Rt_EntityValue(entity.type, id)
            }

            private fun findEntity(app: R_App, name: String): R_EntityDefinition {
                for (module in app.modules) {
                    val c = module.entities[name]
                    if (c != null) return c
                }
                throw IllegalStateException("Entity not found: '$name'")
            }
        }
    }
}
