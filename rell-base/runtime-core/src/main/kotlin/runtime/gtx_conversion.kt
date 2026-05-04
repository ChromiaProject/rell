/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.common.exception.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvType
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_FeatureSwitch
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.rr.RR_Attribute
import net.postchain.rell.base.model.rr.RR_EntityDefinition
import net.postchain.rell.base.model.rr.RR_EntitySqlMappingKind
import net.postchain.rell.base.sql.SqlExecutor
import net.postchain.rell.base.utils.immListOf
import java.math.BigInteger

const val GTV_QUERY_PRETTY = true
const val GTV_OPERATION_PRETTY = false

interface GtvToRtDefaultValueEvaluator {
    fun evaluate(rDefBase: R_DefinitionBase, attrIndex: Int): Rt_Value
    fun evaluateByDefId(defId: DefinitionId, attrIndex: Int): Rt_Value

    companion object {
        private val STRUCT_DEFAULT_SWITCH = C_FeatureSwitch("0.14.12")

        fun getNormal(exeCtx: Rt_ExecutionContext): GtvToRtDefaultValueEvaluator =
            GtvToRtDefaultValueEvaluator_Default(exeCtx)

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
    private val entityRowids = mutableMapOf<R_EntityDefinition, MutableSet<Long>>()

    /** Pure-RR tracking: definition index → rowids. Uses (rrEntityDef, rowids) for the post-decode existence check. */
    private val rrEntityRowids = mutableMapOf<RR_EntityDefinition, MutableSet<Long>>()

    fun trackRecord(entity: R_EntityDefinition, rowid: Long) {
        entityRowids.getOrPut(entity) { mutableSetOf() }.add(rowid)
    }

    fun trackRecordRR(entity: RR_EntityDefinition, rowid: Long) {
        rrEntityRowids.getOrPut(entity) { mutableSetOf() }.add(rowid)
    }

    fun finish(exeCtx: Rt_ExecutionContext) {
        for (rEntities in entityRowids.keys) {
            val rowids = entityRowids.getValue(rEntities)
            checkRowids(exeCtx.sysSqlExec, exeCtx.sqlCtx, rEntities, rowids)
        }
        for (rrEntity in rrEntityRowids.keys) {
            val rowids = rrEntityRowids.getValue(rrEntity)
            checkRowidsRR(exeCtx.sysSqlExec, exeCtx.sqlCtx, rrEntity, rowids)
        }
    }

    private fun checkRowidsRR(
        sqlExec: SqlExecutor,
        sqlCtx: Rt_SqlContext,
        rrEntity: RR_EntityDefinition,
        rowids: Collection<Long>,
    ) {
        val mapping = rrEntity.sqlMapping
        val rowidCol = mapping.rowidColumn
        val tableName = mapping.table(sqlCtx)
        val rowidList = rowids.joinToString(",")
        val sql = when {
            mapping.kind === RR_EntitySqlMappingKind.EXTERNAL -> {
                // External user entities are visible only up to the linked chain's height — join
                // through the external chain's block/transaction tables.
                // Mirrors `R_ExternalEntitySqlMapping.selectExistingObjects`.
                val linked = sqlCtx.chainMappingByIndex(mapping.externalChainIndex)
                val blkTbl = linked.blocksTable
                val txTbl = linked.transactionsTable
                val height = sqlCtx.linkedChainByIndex(mapping.externalChainIndex).height
                """SELECT A."$rowidCol"
                  | FROM "$tableName" A JOIN "$txTbl" T ON T.tx_iid = A.transaction
                  | JOIN "$blkTbl" B ON B.block_iid = T.block_iid
                  | WHERE A."$rowidCol" IN ($rowidList) AND B.block_height <= $height""".trimMargin()
            }
            mapping.externalChainIndex >= 0 && mapping.kind === RR_EntitySqlMappingKind.TRANSACTION -> {
                val linked = sqlCtx.chainMappingByIndex(mapping.externalChainIndex)
                val blkTbl = linked.blocksTable
                val height = sqlCtx.linkedChainByIndex(mapping.externalChainIndex).height
                """SELECT T."$rowidCol"
                  | FROM "$tableName" T JOIN "$blkTbl" B ON B.block_iid = T.block_iid
                  | WHERE T."$rowidCol" IN ($rowidList) AND B.block_height <= $height""".trimMargin()
            }
            mapping.externalChainIndex >= 0 && mapping.kind === RR_EntitySqlMappingKind.BLOCK -> {
                val height = sqlCtx.linkedChainByIndex(mapping.externalChainIndex).height
                "SELECT \"$rowidCol\" FROM \"$tableName\" WHERE \"$rowidCol\" IN ($rowidList) AND block_height <= $height"
            }
            else -> {
                "SELECT \"$rowidCol\" FROM \"$tableName\" WHERE \"$rowidCol\" IN ($rowidList)"
            }
        }
        val existingIds = mutableSetOf<Long>()
        sqlExec.executeQuery(ParameterizedSql(sql, immListOf())) { existingIds.add(it.getLong(1)) }
        val missingIds = rowids.toSet() - existingIds
        if (missingIds.isNotEmpty()) {
            val name = rrEntity.base.appLevelName
            val msg = "Missing objects of entity '$name': ${missingIds.toList().sorted()}"
            throw Rt_GtvError.exception("obj_missing:[$name]:${missingIds.joinToString(",")}", msg)
        }
    }

    private fun checkRowids(
        sqlExec: SqlExecutor,
        sqlCtx: Rt_SqlContext,
        rEntity: R_EntityDefinition,
        rowids: Collection<Long>,
    ) {
        val existingIds = selectExistingIds(sqlExec, sqlCtx, rEntity, rowids)
        val missingIds = rowids.toSet() - existingIds
        if (missingIds.isNotEmpty()) {
            val s = missingIds.toList().sorted()
            val name = rEntity.appLevelName
            val msg = "Missing objects of entity '$name': $s"
            throw Rt_GtvError.exception("obj_missing:[$name]:${missingIds.joinToString(",")}", msg)
        }
    }

    private fun selectExistingIds(
        sqlExec: SqlExecutor,
        sqlCtx: Rt_SqlContext,
        rEntity: R_EntityDefinition,
        rowids: Collection<Long>,
    ): Set<Long> {
        val buf = StringBuilder()
        buf.append("\"").append(rEntity.sqlMapping.rowidColumn()).append("\" IN (")
        rowids.joinTo(buf, ",")
        buf.append(")")
        val whereSql = buf.toString()

        val sql = rEntity.sqlMapping.selectExistingObjects(sqlCtx, whereSql)
        val existingIds = mutableSetOf<Long>()
        sqlExec.executeQuery(ParameterizedSql(sql, immListOf())) { existingIds.add(it.getLong(1)) }
        return existingIds
    }
}

sealed class GtvToRtSymbol {
    abstract fun codeMsg(): C_CodeMsg
}

class GtvToRtSymbol_ParamName(private val name: Name): GtvToRtSymbol() {
    override fun codeMsg() = "param:$name" toCodeMsg "parameter: $name"
}

class GtvToRtSymbol_Attr(private val typeName: String, private val attr: R_Attribute): GtvToRtSymbol() {
    override fun codeMsg() = "attr:[$typeName]:${attr.name}" toCodeMsg "attribute: $typeName.${attr.name}"
}

/** RR-path overload — same encoding as [GtvToRtSymbol_Attr], but identifies the attribute by name only. */
class GtvToRtSymbol_AttrName(private val typeName: String, private val attrName: String): GtvToRtSymbol() {
    override fun codeMsg() = "attr:[$typeName]:$attrName" toCodeMsg "attribute: $typeName.$attrName"
}

class GtvToRtContext private constructor(
    private val state: GtvToRtState,
    val symbol: GtvToRtSymbol?,
    private val keepSymbol: Boolean,
) {
    val pretty = state.pretty
    val strictGtvConversion = state.strictGtvConversion
    val bigIntegerSupport = state.bigIntegerSupport
    private val defaultValueEvaluator = state.defaultValueEvaluator

    fun updateSymbol(symbol: GtvToRtSymbol, keep: Boolean = false): GtvToRtContext {
        if (this.symbol != null && this.keepSymbol) return this
        return if (symbol === this.symbol) this else GtvToRtContext(state, symbol, keep)
    }

    fun rtValue(supplier: () -> Rt_Value): Rt_Value = if (state.validateOnly) Rt_UnitValue else supplier()

    fun trackRecord(entity: R_EntityDefinition, rowid: Long) = state.trackRecord(entity, rowid)
    fun trackRecordRR(entity: RR_EntityDefinition, rowid: Long) =
        state.trackRecordRR(entity, rowid)

    fun finish(exeCtx: Rt_ExecutionContext) = state.finish(exeCtx)

    fun getDefaultValue(
        entity: RR_EntityDefinition,
        attr: RR_Attribute,
        @Suppress("UNUSED_PARAMETER") interpreter: Rt_Interpreter,
    ): Rt_Value {
        // Default value evaluation via R_DefinitionBase is not supported in the RR path.
        // For snapshot import (where this is used), defaultValueEvaluator is null anyway.
        val typeName = entity.rName.str
        throw GtvRtUtils.errGtv(
            this,
            "entity_noattr:$typeName:${attr.name}",
            "Missing entity attribute value: '$typeName.${attr.name}'",
        )
    }

    fun getDefaultValue(defBase: R_DefinitionBase?, attr: R_Attribute, name: String, kind: String): Rt_Value {
        // In validateOnly mode the actual default isn't computed (rtValue short-circuits to
        // Rt_UnitValue); the attribute having a default expression is enough to accept the input.
        if (defBase != null && attr.hasExpr && (defaultValueEvaluator != null || state.validateOnly)) {
            return rtValue { defaultValueEvaluator!!.evaluate(defBase, attr.index) }
        }
        throw GtvRtUtils.errGtv(
            this,
            "${kind}_noattr:$name:${attr.name}",
            "Missing $kind attribute value: '$name.${attr.name}'",
        )
    }

    /**
     * RR-path overload for obtaining an attribute's default value by definition id and attribute index.
     * Used by the pure-RR struct/entity GTV decoders when an attribute is missing from the input.
     * Throws a `${kind}_noattr` error if no default is available.
     */
    fun getDefaultValueByDefId(
        defId: DefinitionId?,
        attrIndex: Int,
        attrName: String,
        hasExpr: Boolean,
        typeName: String,
        kind: String,
    ): Rt_Value {
        if (defId != null && hasExpr && (defaultValueEvaluator != null || state.validateOnly)) {
            return rtValue { defaultValueEvaluator!!.evaluateByDefId(defId, attrIndex) }
        }
        throw GtvRtUtils.errGtv(
            this,
            "${kind}_noattr:$typeName:$attrName",
            "Missing $kind attribute value: '$typeName.$attrName'",
        )
    }

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

private class GtvToRtDefaultValueEvaluator_Default(
    private val exeCtx: Rt_ExecutionContext,
): GtvToRtDefaultValueEvaluator {
    override fun evaluate(rDefBase: R_DefinitionBase, attrIndex: Int): Rt_Value {
        val interpreter = exeCtx.appCtx.interpreter
        return interpreter.evaluateAttributeDefault(rDefBase.defId, attrIndex, exeCtx)
    }

    override fun evaluateByDefId(defId: DefinitionId, attrIndex: Int): Rt_Value {
        val interpreter = exeCtx.appCtx.interpreter
        return interpreter.evaluateAttributeDefault(defId, attrIndex, exeCtx)
    }
}

object GtvRtUtils {
    // -------------------------------------------------------------------------
    // String-name primary path. R_Type-typed overloads below delegate via .strCode().
    // -------------------------------------------------------------------------

    fun gtvToInteger(ctx: GtvToRtContext, gtv: Gtv, typeName: String): Long {
        return when (gtv.type) {
            GtvType.INTEGER -> gtv.asInteger()
            GtvType.BIGINTEGER -> {
                val v = gtv.asBigInteger()
                if (ctx.strictGtvConversion || !ctx.bigIntegerSupport) {
                    throw errGtvType(ctx, typeName, gtv, GtvType.INTEGER, "invalid value: '$v'")
                }
                try {
                    v.longValueExact()
                } catch (_: ArithmeticException) {
                    throw errGtvType(ctx, typeName, "out_of_range:$v", "value out of range: $v")
                }
            }

            else -> throw errGtvType(ctx, typeName, gtv, GtvType.INTEGER, null)
        }
    }

    fun gtvToBigInteger(ctx: GtvToRtContext, gtv: Gtv, typeName: String): BigInteger {
        return when {
            gtv.type === GtvType.BIGINTEGER -> try {
                gtv.asBigInteger()
            } catch (e: UserMistake) {
                throw errGtvType(ctx, typeName, gtv, GtvType.BIGINTEGER, e)
            }

            ctx.pretty && gtv.type === GtvType.INTEGER -> try {
                gtv.asInteger().toBigInteger()
            } catch (e: UserMistake) {
                throw errGtvType(ctx, typeName, gtv, GtvType.BIGINTEGER, e)
            }

            else -> throw errGtvType(ctx, typeName, gtv, GtvType.BIGINTEGER, null)
        }
    }

    fun gtvToBoolean(ctx: GtvToRtContext, gtv: Gtv, typeName: String): Boolean {
        val i = try {
            gtv.asInteger()
        } catch (e: UserMistake) {
            throw errGtvType(ctx, typeName, gtv, GtvType.INTEGER, e)
        }
        return when (i) {
            0L -> false
            1L -> true
            else -> throw errGtvType(ctx, typeName, "bad_value:$i", "expected 0 or 1, actual $i")
        }
    }

    fun gtvToString(ctx: GtvToRtContext, gtv: Gtv, typeName: String): String {
        try {
            return gtv.asString()
        } catch (e: UserMistake) {
            throw errGtvType(ctx, typeName, gtv, GtvType.STRING, e)
        }
    }

    fun gtvToByteArray(ctx: GtvToRtContext, gtv: Gtv, typeName: String): ByteArray {
        try {
            return gtv.asByteArray(convert = !ctx.strictGtvConversion)
        } catch (e: UserMistake) {
            val exp = immListOf(GtvType.BYTEARRAY, GtvType.STRING)
            if (gtv.type in exp) {
                throw errGtvType(ctx, typeName, "bad_value:${gtv.type}", e.message ?: "invalid value")
            } else {
                val code = "${exp.joinToString(",")}:${gtv.type}"
                val msg = "expected $exp, actual ${gtv.type}"
                throw errGtvType(ctx, typeName, code, msg)
            }
        }
    }

    fun gtvToJson(ctx: GtvToRtContext, gtv: Gtv, typeName: String): Rt_Value {
        val str = try {
            gtv.asString()
        } catch (e: UserMistake) {
            throw errGtvType(ctx, typeName, gtv, GtvType.STRING, e)
        }
        try {
            return Rt_JsonValue.parse(str)
        } catch (e: IllegalArgumentException) {
            throw errGtvType(ctx, typeName, "bad_value", e.message ?: "invalid value")
        }
    }

    /** Exact-size variant: throws `${errCode}:expected:actual` if array size doesn't match. */
    fun gtvToArray(ctx: GtvToRtContext, gtv: Gtv, size: Int, errCode: String, typeName: String): Array<out Gtv> {
        val array = gtvToArrayAny(ctx, gtv, typeName)
        val actSize = array.size
        if (actSize != size) {
            throw errGtvType(ctx, typeName, "$errCode:$size:$actSize", "wrong array size: $actSize instead of $size")
        }
        return array
    }

    /** Any-size variant — used by collection conversions that don't constrain length. */
    fun gtvToArrayAny(ctx: GtvToRtContext, gtv: Gtv, typeName: String): Array<out Gtv> {
        try {
            return gtv.asArray()
        } catch (e: UserMistake) {
            throw errGtvType(ctx, typeName, gtv, GtvType.ARRAY, e)
        }
    }

    fun gtvToMap(ctx: GtvToRtContext, gtv: Gtv, typeName: String): Map<String, Gtv> {
        try {
            return gtv.asDict()
        } catch (e: UserMistake) {
            throw errGtvType(ctx, typeName, gtv, GtvType.DICT, e)
        }
    }

    fun errGtvType(ctx: GtvToRtContext, typeName: String, code: String, msg: String): Rt_Exception {
        val fullCode = "type:[$typeName]:$code"
        val fullMsg = "Decoding type '$typeName': $msg"
        return errGtv(ctx, fullCode, fullMsg)
    }

    private fun errGtvType(
        ctx: GtvToRtContext,
        typeName: String,
        actualGtv: Gtv,
        expectedGtvType: GtvType,
        e: UserMistake,
    ): Rt_Exception = errGtvType(ctx, typeName, actualGtv, expectedGtvType, e.message)

    private fun errGtvType(
        ctx: GtvToRtContext,
        typeName: String,
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
        return errGtvType(ctx, typeName, code, msg)
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
