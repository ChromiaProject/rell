/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.common.exception.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvType
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_FeatureSwitch
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lib.type.Rt_JsonValue
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.sql.SqlExecutor
import net.postchain.rell.base.utils.immListOf
import org.apache.commons.collections4.MultiValuedMap
import org.apache.commons.collections4.multimap.HashSetValuedHashMap
import java.math.BigInteger

const val GTV_QUERY_PRETTY = true
const val GTV_OPERATION_PRETTY = false

interface GtvToRtDefaultValueEvaluator {
    fun evaluate(rDefBase: R_DefinitionBase, expr: R_Expr): Rt_Value

    companion object {
        private val STRUCT_DEFAULT_SWITCH = C_FeatureSwitch("0.14.12")

        fun getError(): GtvToRtDefaultValueEvaluator = GtvToRtDefaultValueEvaluator_Error

        fun getNormal(exeCtx: Rt_ExecutionContext): GtvToRtDefaultValueEvaluator {
            return GtvToRtDefaultValueEvaluator_Default(exeCtx)
        }

        fun getStructDefault(exeCtx: Rt_ExecutionContext): GtvToRtDefaultValueEvaluator? {
            val isActive = STRUCT_DEFAULT_SWITCH.isActive(exeCtx.globalCtx.compilerOptions)
            return if (isActive) getNormal(exeCtx) else null
        }
    }
}

private class GtvToRtState(
    val pretty: Boolean,
    val validateOnly: Boolean,
    val strictGtvConversion: Boolean,
    val bigIntegerSupport: Boolean,
    val defaultValueEvaluator: GtvToRtDefaultValueEvaluator?,
) {
    private val entityRowids: MultiValuedMap<R_EntityDefinition, Long> = HashSetValuedHashMap()

    fun trackRecord(entity: R_EntityDefinition, rowid: Long) {
        entityRowids.put(entity, rowid)
    }

    fun finish(exeCtx: Rt_ExecutionContext) {
        for (rEntities in entityRowids.keySet()) {
            val rowids = entityRowids.get(rEntities)
            checkRowids(exeCtx.sysSqlExec, exeCtx.sqlCtx, rEntities, rowids)
        }
    }

    private fun checkRowids(sqlExec: SqlExecutor, sqlCtx: Rt_SqlContext, rEntity: R_EntityDefinition, rowids: Collection<Long>) {
        val existingIds = selectExistingIds(sqlExec, sqlCtx, rEntity, rowids)
        val missingIds = rowids.toSet() - existingIds
        if (missingIds.isNotEmpty()) {
            val s = missingIds.toList().sorted()
            val name = rEntity.appLevelName
            throw Rt_GtvError.exception("obj_missing:[$name]:${missingIds.joinToString(",")}", "Missing objects of entity '$name': $s")
        }
    }

    private fun selectExistingIds(sqlExec: SqlExecutor, sqlCtx: Rt_SqlContext, rEntity: R_EntityDefinition, rowids: Collection<Long>): Set<Long> {
        val buf = StringBuilder()
        buf.append("\"").append(rEntity.sqlMapping.rowidColumn()).append("\" IN (")
        rowids.joinTo(buf, ",")
        buf.append(")")
        val whereSql = buf.toString()

        val sql = rEntity.sqlMapping.selectExistingObjects(sqlCtx, whereSql)
        val existingIds = mutableSetOf<Long>()
        sqlExec.executeQuery(sql, {}) { existingIds.add(it.getLong(1)) }
        return existingIds
    }
}

sealed class GtvToRtSymbol {
    abstract fun codeMsg(): C_CodeMsg
}

class GtvToRtSymbol_Param(private val param: R_FunctionParam): GtvToRtSymbol() {
    override fun codeMsg() = "param:${param.name}" toCodeMsg "parameter: ${param.name}"
}

class GtvToRtSymbol_Attr(private val typeName: String, private val attr: R_Attribute): GtvToRtSymbol() {
    override fun codeMsg() = "attr:[$typeName]:${attr.name}" toCodeMsg "attribute: $typeName.${attr.name}"
}

class GtvToRtContext private constructor(
    private val state: GtvToRtState,
    val symbol: GtvToRtSymbol?,
    private val keepSymbol: Boolean,
) {
    internal val pretty = state.pretty
    internal val strictGtvConversion = state.strictGtvConversion
    internal val bigIntegerSupport = state.bigIntegerSupport
    internal val defaultValueEvaluator = state.defaultValueEvaluator

    fun updateSymbol(symbol: GtvToRtSymbol, keep: Boolean = false): GtvToRtContext {
        if (this.symbol != null && this.keepSymbol) return this
        return if (symbol === this.symbol) this else GtvToRtContext(state, symbol, keep)
    }

    fun rtValue(supplier: () -> Rt_Value): Rt_Value {
        return if (state.validateOnly) Rt_UnitValue else supplier()
    }

    fun trackRecord(entity: R_EntityDefinition, rowid: Long) = state.trackRecord(entity, rowid)
    fun finish(exeCtx: Rt_ExecutionContext) = state.finish(exeCtx)

    companion object {
        private val BIG_INTEGER_SWITCH = C_FeatureSwitch("0.11.0")

        fun make(
            pretty: Boolean,
            validateOnly: Boolean = false,
            strictGtvConversion: Boolean = false,
            defaultValueEvaluator: GtvToRtDefaultValueEvaluator? = null,
            compilerOptions: C_CompilerOptions = C_CompilerOptions.DEFAULT,
        ): GtvToRtContext {
            val state = GtvToRtState(
                pretty = pretty,
                validateOnly = validateOnly,
                strictGtvConversion = strictGtvConversion,
                bigIntegerSupport = BIG_INTEGER_SWITCH.isActive(compilerOptions),
                defaultValueEvaluator = defaultValueEvaluator,
            )
            return GtvToRtContext(state, null, false)
        }
    }
}

abstract class GtvRtConversion {
    abstract fun directCompatibility(): R_GtvCompatibility
    abstract fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv
    abstract fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value
}

object GtvRtConversion_None: GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(false, false)
    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = throw UnsupportedOperationException()
    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv) = throw UnsupportedOperationException()
}

private class GtvToRtDefaultValueEvaluator_Default(
    private val exeCtx: Rt_ExecutionContext,
): GtvToRtDefaultValueEvaluator {
    override fun evaluate(rDefBase: R_DefinitionBase, expr: R_Expr): Rt_Value {
        val frame = Rt_CallFrame.createInitFrame(
            exeCtx,
            rDefBase.defId,
            rDefBase.initFrameGetter,
            modsAllowed = !exeCtx.dbReadOnly,
        )
        return expr.evaluate(frame)
    }
}

private object GtvToRtDefaultValueEvaluator_Error: GtvToRtDefaultValueEvaluator {
    override fun evaluate(rDefBase: R_DefinitionBase, expr: R_Expr): Rt_Value {
        throw UnsupportedOperationException("Default values evaluation not supported")
    }
}

object GtvRtUtils {
    fun gtvToInteger(ctx: GtvToRtContext, gtv: Gtv, rellType: R_Type): Long {
        return when (gtv.type) {
            GtvType.INTEGER -> {
                gtv.asInteger()
            }
            GtvType.BIGINTEGER -> {
                val v = gtv.asBigInteger()
                if (ctx.strictGtvConversion || !ctx.bigIntegerSupport) {
                    throw errGtvType(ctx, rellType, gtv, GtvType.INTEGER, "invalid value: '$v'")
                }
                try {
                    return v.longValueExact()
                } catch (e: ArithmeticException) {
                    throw errGtvType(ctx, rellType, "out_of_range:$v", "value out of range: $v")
                }
            }
            else -> throw errGtvType(ctx, rellType, gtv, GtvType.INTEGER, null)
        }
    }

    fun gtvToBigInteger(ctx: GtvToRtContext, gtv: Gtv, rellType: R_Type): BigInteger {
        return when {
            gtv.type == GtvType.BIGINTEGER -> try {
                gtv.asBigInteger()
            } catch (e: UserMistake) {
                throw errGtvType(ctx, rellType, gtv, GtvType.BIGINTEGER, e)
            }
            ctx.pretty && gtv.type == GtvType.INTEGER -> try {
                gtv.asInteger().toBigInteger()
            } catch (e: UserMistake) {
                throw errGtvType(ctx, rellType, gtv, GtvType.BIGINTEGER, e)
            }
            else -> throw errGtvType(ctx, rellType, gtv, GtvType.BIGINTEGER, null)
        }
    }

    fun gtvToBoolean(ctx: GtvToRtContext, gtv: Gtv, rellType: R_Type): Boolean {
        val i = try {
            gtv.asInteger()
        } catch (e: UserMistake) {
            throw errGtvType(ctx, rellType, gtv, GtvType.INTEGER, e)
        }

        return when (i) {
            0L -> false
            1L -> true
            else -> throw errGtvType(ctx, rellType, "bad_value:$i", "expected 0 or 1, actual $i")
        }
    }

    fun gtvToString(ctx: GtvToRtContext, gtv: Gtv, rellType: R_Type): String {
        try {
            return gtv.asString()
        } catch (e: UserMistake) {
            throw errGtvType(ctx, rellType, gtv, GtvType.STRING, e)
        }
    }

    fun gtvToByteArray(ctx: GtvToRtContext, gtv: Gtv, rellType: R_Type): ByteArray {
        try {
            return gtv.asByteArray(convert = !ctx.strictGtvConversion)
        } catch (e: UserMistake) {
            val exp = immListOf(GtvType.BYTEARRAY, GtvType.STRING)
            if (gtv.type in exp) {
                throw errGtvType(ctx, rellType, "bad_value:${gtv.type}", e.message ?: "invalid value")
            } else {
                val code = "${exp.joinToString(",")}:${gtv.type}"
                val msg = "expected $exp, actual ${gtv.type}"
                throw errGtvType(ctx, rellType, code, msg)
            }
        }
    }

    fun gtvToJson(ctx: GtvToRtContext, gtv: Gtv, rellType: R_Type): Rt_Value {
        val str = try {
            gtv.asString()
        } catch (e: UserMistake) {
            throw errGtvType(ctx, rellType, gtv, GtvType.STRING, e)
        }
        try {
            return Rt_JsonValue.parse(str)
        } catch (e: IllegalArgumentException) {
            throw errGtvType(ctx, rellType, "bad_value", e.message ?: "invalid value")
        }
    }

    fun gtvToArray(ctx: GtvToRtContext, gtv: Gtv, size: Int, errCode: String, rellType: R_Type): Array<out Gtv> {
        val array = gtvToArray(ctx, gtv, rellType)
        val actSize = array.size
        if (actSize != size) {
            throw errGtvType(ctx, rellType, "$errCode:$size:$actSize", "wrong array size: $actSize instead of $size")
        }
        return array
    }

    fun gtvToArray(ctx: GtvToRtContext, gtv: Gtv, rellType: R_Type): Array<out Gtv> {
        try {
            return gtv.asArray()
        } catch (e: UserMistake) {
            throw errGtvType(ctx, rellType, gtv, GtvType.ARRAY, e)
        }
    }

    fun gtvToMap(ctx: GtvToRtContext, gtv: Gtv, rellType: R_Type): Map<String, Gtv> {
        try {
            return gtv.asDict()
        } catch (e: UserMistake) {
            throw errGtvType(ctx, rellType, gtv, GtvType.DICT, e)
        }
    }

    private fun errGtvType(
        ctx: GtvToRtContext,
        rellType: R_Type,
        actualGtv: Gtv,
        expectedGtvType: GtvType,
        e: UserMistake,
    ): Rt_Exception {
        return errGtvType(ctx, rellType, actualGtv, expectedGtvType, e.message)
    }

    private fun errGtvType(
        ctx: GtvToRtContext,
        rellType: R_Type,
        actualGtv: Gtv,
        expectedGtvType: GtvType,
        errMsg: String?,
    ): Rt_Exception {
        val code = "$expectedGtvType:${actualGtv.type}"
        val msg = when {
            actualGtv.type != expectedGtvType -> "expected $expectedGtvType, actual ${actualGtv.type}"
            !errMsg.isNullOrBlank() -> errMsg
            else -> actualGtv.type.name
        }
        return errGtvType(ctx, rellType, code, msg)
    }

    fun errGtvType(ctx: GtvToRtContext, rellType: R_Type, code: String, msg: String): Rt_Exception {
        val fullCode = "type:[${rellType.strCode()}]:$code"
        val fullMsg = "Decoding type '${rellType.str()}': $msg"
        return errGtv(ctx, fullCode, fullMsg)
    }

    fun errGtv(ctx: GtvToRtContext, code: String, msg: String): Rt_Exception {
        var code2 = code
        var msg2 = msg
        if (ctx.symbol != null) {
            val symCodeMsg = ctx.symbol.codeMsg()
            code2 = "$code:${symCodeMsg.code}"
            msg2 = "$msg (${symCodeMsg.msg})"
        }
        return Rt_GtvError.exception(code2, msg2)
    }
}
