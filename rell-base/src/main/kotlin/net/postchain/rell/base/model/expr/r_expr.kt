/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import com.fasterxml.jackson.databind.JsonNode
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.stmt.R_Statement
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.RellInterpreterCrashException
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.SqlGen
import net.postchain.rell.base.utils.*
import org.apache.commons.collections4.ListUtils
import java.math.BigDecimal
import kotlin.math.max

abstract class R_Expr(val type: R_Type) {
    internal abstract fun evaluate(frame: Rt_CallFrame): Rt_Value
}

internal abstract class R_BaseExpr(type: R_Type): R_Expr(type) {
    protected abstract fun evaluate0(frame: Rt_CallFrame): Rt_Value

    final override fun evaluate(frame: Rt_CallFrame): Rt_Value {
        val res = evaluate0(frame)
        typeCheck(frame, type, res)
        return res
    }

    companion object {
        internal fun typeCheck(frame: Rt_CallFrame, type: R_Type, value: Rt_Value) {
            if (frame.defCtx.globalCtx.typeCheck) {
                val resType = value.type()
                check(type == R_UnitType || type.isAssignableFrom(resType)) {
                    "${R_Expr::class.java.simpleName}: expected ${type.name}, actual ${resType.name}"
                }
            }
        }
    }
}

internal class R_ErrorExpr(type: R_Type, private val message: String): R_BaseExpr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        throw RellInterpreterCrashException(message)
    }
}

internal sealed class R_DestinationExpr(type: R_Type): R_BaseExpr(type) {
    abstract fun evaluateRef(frame: Rt_CallFrame): Rt_ValueRef?

    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val ref = evaluateRef(frame)
        val value = ref?.get()
        return value ?: Rt_NullValue
    }
}

internal class R_VarExpr(type: R_Type, private val ptr: R_VarPtr, private val name: String): R_DestinationExpr(type) {
    override fun evaluateRef(frame: Rt_CallFrame): Rt_ValueRef {
        return Rt_VarValueRef(type, ptr, name, frame)
    }

    private class Rt_VarValueRef(
        val type: R_Type,
        val ptr: R_VarPtr,
        val name: String,
        val frame: Rt_CallFrame,
    ): Rt_ValueRef() {
        override fun get(): Rt_Value {
            val value = frame.getOpt(ptr)
            if (value == null) {
                throw Rt_Exception.common("expr_var_uninit:$name", "Variable '$name' has not been initialized")
            }
            return value
        }

        override fun set(value: Rt_Value) {
            frame.set(ptr, type, value, true)
        }
    }
}

internal class R_StructMemberExpr(val base: R_Expr, val attr: R_Attribute): R_DestinationExpr(attr.type) {
    override fun evaluateRef(frame: Rt_CallFrame): Rt_ValueRef? {
        val baseValue = base.evaluate(frame)
        if (baseValue is Rt_NullValue) {
            // Must be operator "?."
            return null
        }

        val structValue = baseValue.asStruct()
        return Rt_StructAttrRef(structValue, attr)
    }

    private class Rt_StructAttrRef(val struct: Rt_StructValue, val attr: R_Attribute): Rt_ValueRef() {
        override fun get(): Rt_Value {
            val value = struct.get(attr.index)
            return value
        }

        override fun set(value: Rt_Value) {
            struct.set(attr.index, value)
        }
    }
}

internal class R_ConstantValueExpr(type: R_Type, private val value: Rt_Value): R_BaseExpr(type) {
    constructor(value: Rt_Value): this(value.type(), value)

    override fun evaluate0(frame: Rt_CallFrame): Rt_Value = value

    companion object {
        fun makeNull() = R_ConstantValueExpr(Rt_NullValue)
        fun makeBool(v: Boolean) = R_ConstantValueExpr(Rt_BooleanValue.get(v))
        fun makeInt(v: Long) = R_ConstantValueExpr(Rt_IntValue.get(v))
    }
}

internal class R_TupleExpr(
    private val tupleType: R_TupleType,
    private val exprs: ImmList<R_Expr>,
): R_BaseExpr(tupleType) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val values = exprs.map { it.evaluate(frame) }
        return Rt_TupleValue(tupleType, values)
    }
}

internal class R_ListLiteralExpr(type: R_ListType, private val exprs: ImmList<R_Expr>): R_BaseExpr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val list = MutableList(exprs.size) { exprs[it].evaluate(frame) }
        return Rt_ListValue(type, list)
    }
}

internal class R_MapLiteralExpr(
    private val mapType: R_MapType,
    private val entries: ImmList<Pair<R_Expr, R_Expr>>,
    private val errPos: R_ErrorPos,
): R_BaseExpr(mapType) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val map = mutableMapOf<Rt_Value, Rt_Value>()
        for ((keyExpr, valueExpr) in entries) {
            val key = keyExpr.evaluate(frame)
            val value = valueExpr.evaluate(frame)
            if (key in map) {
                frame.error(errPos, "expr_map_dupkey:${key.strCode()}", "Duplicate map key: ${key.str()}")
            }
            map[key] = value
        }
        return Rt_MapValue(mapType, map)
    }
}

internal class R_ListSubscriptExpr(
    type: R_Type,
    private val base: R_Expr,
    private val expr: R_Expr,
    private val errPos: R_ErrorPos,
): R_DestinationExpr(type) {
    override fun evaluateRef(frame: Rt_CallFrame): Rt_ValueRef {
        val baseValue = base.evaluate(frame)
        val indexValue = expr.evaluate(frame)
        val list = baseValue.asList()
        val index = indexValue.asInteger()
        Rt_ListValue.checkIndex(frame, errPos, list.size, index)
        return Rt_ListValueRef(list, index.toInt())
    }

    private class Rt_ListValueRef(val list: MutableList<Rt_Value>, val index: Int): Rt_ValueRef() {
        override fun get(): Rt_Value {
            return list[index]
        }

        override fun set(value: Rt_Value) {
            list[index] = value
        }
    }
}

internal class R_VirtualListSubscriptExpr(
    type: R_Type,
    private val base: R_Expr,
    private val expr: R_Expr,
): R_BaseExpr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val baseValue = base.evaluate(frame)
        val indexValue = expr.evaluate(frame)
        val list = baseValue.asVirtualList()
        val index = indexValue.asInteger()
        val res = list.get(index)
        return res
    }
}

internal class R_MapSubscriptExpr(
    type: R_Type,
    private val base: R_Expr,
    private val expr: R_Expr,
    private val errPos: R_ErrorPos,
): R_DestinationExpr(type) {
    override fun evaluateRef(frame: Rt_CallFrame): Rt_ValueRef {
        val baseValue = base.evaluate(frame)
        val keyValue = expr.evaluate(frame)
        val map = baseValue.asMutableMap()
        return Rt_MapValueRef(frame, errPos, map, keyValue)
    }

    private class Rt_MapValueRef(
        val frame: Rt_CallFrame,
        val errPos: R_ErrorPos,
        val map: MutableMap<Rt_Value, Rt_Value>,
        val key: Rt_Value,
    ): Rt_ValueRef() {
        override fun get() = getValue(frame, errPos, map, key)

        override fun set(value: Rt_Value) {
            map.put(key, value)
        }
    }

    companion object {
        fun getValue(frame: Rt_CallFrame, errPos: R_ErrorPos, map: Map<Rt_Value, Rt_Value>, key: Rt_Value): Rt_Value {
            val value = map[key]
            if (value == null) {
                frame.error(errPos, "fn_map_get_novalue:${key.strCode()}", "Key not in map: ${key.str()}")
            }
            return value
        }
    }
}

internal class R_VirtualMapSubscriptExpr(
    type: R_Type,
    private val base: R_Expr,
    private val expr: R_Expr,
    private val errPos: R_ErrorPos,
): R_BaseExpr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val baseValue = base.evaluate(frame)
        val keyValue = expr.evaluate(frame)
        val map = baseValue.asMap()
        val res = R_MapSubscriptExpr.getValue(frame, errPos, map, keyValue)
        return res
    }
}

internal class R_TextSubscriptExpr(
    private val base: R_Expr,
    private val expr: R_Expr,
    private val errPos: R_ErrorPos,
): R_BaseExpr(R_TextType) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val baseValue = base.evaluate(frame)
        val indexValue = expr.evaluate(frame)
        val str = baseValue.asString()
        val index = indexValue.asInteger()

        if (index < 0 || index >= str.length) {
            frame.error(
                errPos,
                "expr_text_subscript_index:${str.length}:$index",
                "Index out of bounds: $index (length ${str.length})",
            )
        }

        val i = index.toInt()
        val res = str.substring(i, i + 1)
        return Rt_TextValue.get(res)
    }
}

internal sealed class R_JsonSubscriptExpr<SubscriptT>(
    private val base: R_Expr,
    private val expr: R_Expr,
    private val errPos: R_ErrorPos,
): R_BaseExpr(R_JsonType) {
    abstract fun getSubscript(value: Rt_Value): SubscriptT
    abstract fun runGetOperation(node: JsonNode, subscript: SubscriptT): JsonUtils.GetOperationResult

    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val baseValue = base.evaluate(frame)
        val subscriptValue = expr.evaluate(frame)
        val jsonNode = baseValue.asJson().node
        val subscript = getSubscript(subscriptValue)
        when (val result = runGetOperation(jsonNode, subscript)) {
            is JsonUtils.Success -> return Rt_JsonValue(result.value)
            is JsonUtils.Failure -> frame.error(errPos, result.codeMsg.code, result.codeMsg.msg)
        }
    }
}

internal class R_JsonArraySubscriptExpr(
    base: R_Expr,
    expr: R_Expr,
    errPos: R_ErrorPos,
): R_JsonSubscriptExpr<Int>(base, expr, errPos) {
    override fun getSubscript(value: Rt_Value) = value.asInteger().toIntExact()
    override fun runGetOperation(node: JsonNode, subscript: Int) = JsonUtils.arrayGet(node, subscript, "subscript")
}

internal class R_JsonObjectSubscriptExpr(
    base: R_Expr,
    expr: R_Expr,
    errPos: R_ErrorPos,
): R_JsonSubscriptExpr<String>(base, expr, errPos) {
    override fun getSubscript(value: Rt_Value) = value.asString()
    override fun runGetOperation(node: JsonNode, subscript: String) = JsonUtils.objectGet(node, subscript, "subscript")
}

internal class R_ByteArraySubscriptExpr(
    private val base: R_Expr,
    private val expr: R_Expr,
    private val errPos: R_ErrorPos,
): R_BaseExpr(R_IntegerType) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val baseValue = base.evaluate(frame)
        val indexValue = expr.evaluate(frame)
        val array = baseValue.asByteArray()
        val index = indexValue.asInteger()

        if (index < 0 || index >= array.size) {
            frame.error(
                errPos,
                "expr_bytearray_subscript_index:${array.size}:$index",
                "Index out of bounds: $index (length ${array.size})",
            )
        }

        val i = index.toInt()
        val v = array[i].toLong()
        val res = if (v >= 0) v else v + 256
        return Rt_IntValue.get(res)
    }
}

internal class R_ElvisExpr(
    type: R_Type,
    private val left: R_Expr,
    private val right: R_Expr,
): R_BaseExpr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val leftVal = left.evaluate(frame)
        if (leftVal != Rt_NullValue) {
            return leftVal
        }

        val rightVal = right.evaluate(frame)
        return rightVal
    }
}

internal class R_NotNullExpr(
    type: R_Type,
    private val expr: R_Expr,
    private val errPos: R_ErrorPos,
): R_BaseExpr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val v = expr.evaluate(frame)
        if (v == Rt_NullValue) {
            frame.error(errPos, "null_value", "Null value")
        }
        return v
    }
}

internal class R_IfExpr(
    type: R_Type,
    private val cond: R_Expr,
    private val trueExpr: R_Expr,
    private val falseExpr: R_Expr,
): R_BaseExpr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val v = cond.evaluate(frame)
        val b = v.asBoolean()
        val expr = if (b) trueExpr else falseExpr
        val res = expr.evaluate(frame)
        return res
    }
}

internal sealed class R_WhenChooser {
    abstract fun choose(frame: Rt_CallFrame): Int?
}

internal class R_IterativeWhenChooser(
    private val keyExpr: R_Expr,
    private val exprs: ImmList<IndexedValue<R_Expr>>,
    private val elseIdx: Int?,
): R_WhenChooser() {
    override fun choose(frame: Rt_CallFrame): Int? {
        val keyValue = keyExpr.evaluate(frame)
        for ((i, expr) in exprs) {
            val value = expr.evaluate(frame)
            if (value == keyValue) {
                return i
            }
        }
        return elseIdx
    }
}

internal class R_LookupWhenChooser(
    private val keyExpr: R_Expr,
    private val map: ImmMap<Rt_Value, Int>,
    private val elseIdx: Int?,
): R_WhenChooser() {
    override fun choose(frame: Rt_CallFrame): Int? {
        val keyValue = keyExpr.evaluate(frame)
        val idx = map[keyValue]
        return idx ?: elseIdx
    }
}

internal class R_WhenExpr(
    type: R_Type,
    private val chooser: R_WhenChooser,
    private val exprs: ImmList<R_Expr>,
): R_BaseExpr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val choice = chooser.choose(frame)
        check(choice != null)
        val expr = exprs[choice]
        val res = expr.evaluate(frame)
        return res
    }
}

internal class R_CreateExprAttr(val attr: R_Attribute, private val expr: R_Expr) {
    fun evaluate(frame: Rt_CallFrame) = expr.evaluate(frame)
}

internal sealed class R_CreateExpr(
    type: R_Type,
    private val rEntity: R_EntityDefinition,
    private val errPos: R_ErrorPos,
): R_BaseExpr(type) {
    protected abstract fun evaluateData(frame: Rt_CallFrame): InsertData
    protected abstract fun evaluateResult(entities: List<Rt_Value>): Rt_Value

    final override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        return try {
            evaluateCreate(frame)
        } catch (e: Rt_Exception) {
            frame.error(errPos, e)
        }
    }

    private fun evaluateCreate(frame: Rt_CallFrame): Rt_Value {
        frame.checkDbUpdateAllowed()

        val allValues = evaluateData(frame)
        val allRows = mutableListOf<List<Rt_Value>>()

        val valuesPages = splitValues(frame.appCtx.globalCtx, allValues)

        for (page in valuesPages) {
            val sqlCtx = frame.defCtx.sqlCtx
            val rtSql = buildSql(sqlCtx, rEntity, page)
            val rtSel = SqlSelect(rtSql, immListOf(rEntity.type))
            val rows = rtSel.execute(frame.userSqlExec)
            processSnapshot(frame, page, rows)
            allRows.addAll(rows)
        }

        val entities = allRows.map { it.single() }
        val res = evaluateResult(entities)
        return res
    }

    private fun splitValues(globalCtx: Rt_GlobalContext, values: InsertData): ImmList<InsertData> {
        if (values.rows.isEmpty()) {
            return immListOf()
        } else if (values.attrs.isEmpty()) {
            return immListOf(values)
        }

        val recordsPerPage = max(globalCtx.sqlUpdatePortionSize / values.attrs.size, 1)
        val pages = ListUtils.partition(values.rows, recordsPerPage)
        return pages.mapToImmList { InsertData(values.attrs, it.toImmList()) }
    }

    private fun processSnapshot(frame: Rt_CallFrame, data: InsertData, rows: List<List<Rt_Value>>) {
        val opCtx = frame.exeCtx.opCtx
        if (!opCtx.hasSnapshotContext()) {
            return
        }

        for ((i, dataRow) in data.rows.withIndex()) {
            val rowid = rows[i][0].asObjectId()
            val datum = makeDatum(data, dataRow)
            opCtx.emitDatum(rowid, datum, false)
        }
    }

    private fun makeDatum(data: InsertData, dataRow: InsertRow): Gtv {
        val attrs = data.attrs.mapIndexed { i, attr ->
                val v = dataRow.values[i]
                attr.name to attr.type.rtToGtv(v, false)
            }
            .toImmMap()
        return gtv(gtv(rEntity.sqlMapping.metaName), gtv(attrs))
    }

    companion object {
        fun buildDefaultRowidSql(sqlCtx: Rt_SqlContext): ParameterizedSql {
            val rowidFunc = sqlCtx.mainChainMapping().rowidFunction
            return ParameterizedSql.generate { b ->
                b.append("\"$rowidFunc\"()")
            }
        }

        fun buildSql(
            sqlCtx: Rt_SqlContext,
            rEntity: R_EntityDefinition,
            values: InsertData,
        ): ParameterizedSql {
            val b = SqlBuilder()

            val table = rEntity.sqlMapping.table(sqlCtx)
            val rowid = rEntity.sqlMapping.rowidColumn()

            b.append("INSERT INTO ")
            b.appendName(table)

            b.append("(")
            b.appendName(rowid)
            b.append(values.attrs, "") { attr ->
                b.append(", ")
                b.appendName(attr.sqlMapping)
            }
            b.append(")")

            b.append(" VALUES ")

            b.append(values.rows, ", ") { row ->
                b.append("(")
                b.append(row.rowidSql)
                b.append(row.values, "") { value ->
                    b.append(", ")
                    b.append(value)
                }
                b.append(")")
            }

            b.append(" RETURNING ")
            b.appendName(rowid)

            return b.build()
        }

        fun buildAddColumnsSql(
            frame: Rt_CallFrame,
            rEntity: R_EntityDefinition,
            attrs: List<R_CreateExprAttr>,
            existingRecs: Boolean,
            isSnapshot: Boolean,
        ): ParameterizedSql {
            val sqlCtx = frame.defCtx.sqlCtx
            val table = rEntity.sqlMapping.table(sqlCtx)

            val b = SqlBuilder()

            for (attr in attrs) {
                val columnSql = SqlGen.genAddColumnSql(table, attr.attr, existingRecs)
                b.append(columnSql)
                b.append(";\n")
            }

            if (existingRecs) {
                b.append("UPDATE ")
                b.appendName(table)
                b.append(" SET ")
                b.append(attrs, ", ") { attr ->
                    val value = attr.evaluate(frame)
                    b.appendName(attr.attr.sqlMapping)
                    b.append(" = ")
                    b.append(value)
                }
                b.append(";\n")

                for (attr in attrs) {
                    b.append("ALTER TABLE ")
                    b.appendName(table)
                    b.append(" ALTER COLUMN ")
                    b.appendName(attr.attr.sqlMapping)
                    b.append(" SET NOT NULL;\n")
                }
            }

            val constraintsSql = SqlGen.genAddAttrConstraintsSql(sqlCtx, rEntity, attrs.map { it.attr }, !isSnapshot)
            if (constraintsSql.isNotEmpty()) {
                b.append(constraintsSql)
                b.append(";\n")
            }

            return b.build()
        }
    }

    class InsertRow(val rowidSql: ParameterizedSql, val values: ImmList<Rt_Value>)

    class InsertData(val attrs: ImmList<R_Attribute>, val rows: ImmList<InsertRow>) {
        init {
            for (row in rows) {
                checkEquals(row.values.size, attrs.size)
            }
        }
    }
}

internal class R_RegularCreateExpr(
    rEntity: R_EntityDefinition,
    errPos: R_ErrorPos,
    private val attrs: ImmList<R_CreateExprAttr>,
): R_CreateExpr(rEntity.type, rEntity, errPos) {
    override fun evaluateData(frame: Rt_CallFrame): InsertData {
        val resAttrs = attrs.mapToImmList { it.attr }
        val rowidSql = buildDefaultRowidSql(frame.sqlCtx)
        val values = attrs.mapToImmList { it.evaluate(frame) }
        validateValues(values)
        val row = InsertRow(rowidSql, values)
        return InsertData(resAttrs, immListOf(row))
    }

    override fun evaluateResult(entities: List<Rt_Value>): Rt_Value {
        checkEquals(entities.size, 1)
        return entities[0]
    }

    private fun validateValues(values: List<Rt_Value>) {
        attrs.withIndex().forEach { (i, attr) -> attr.attr.validator?.check(values[i])?.raise() }
    }
}

internal class R_StructCreateExpr(
    rEntity: R_EntityDefinition,
    errPos: R_ErrorPos,
    private val structType: R_StructType,
    private val structExpr: R_Expr,
): R_CreateExpr(rEntity.type, rEntity, errPos) {
    override fun evaluateData(frame: Rt_CallFrame): InsertData {
        val structValue = structExpr.evaluate(frame).asStruct()
        val attrs = structType.struct.attributesList
        val rowidSql = buildDefaultRowidSql(frame.sqlCtx)
        val values = attrs.indices.mapToImmList { structValue.get(it) }
        val row = InsertRow(rowidSql, values)
        return InsertData(attrs, immListOf(row))
    }

    override fun evaluateResult(entities: List<Rt_Value>): Rt_Value {
        checkEquals(entities.size, 1)
        return entities[0]
    }
}

internal class R_StructListCreateExpr(
    rEntity: R_EntityDefinition,
    errPos: R_ErrorPos,
    private val structType: R_StructType,
    private val resultListType: R_ListType,
    private val listExpr: R_Expr,
): R_CreateExpr(resultListType, rEntity, errPos) {
    override fun evaluateData(frame: Rt_CallFrame): InsertData {
        val listValue = listExpr.evaluate(frame).asList()
        val attrs = structType.struct.attributesList
        val rowidGen = getRowidGenerator(frame, listValue.size)
        val rows = listValue.mapIndexedToImmList { index, value ->
            val structValue = value.asStruct()
            val rowidSql = rowidGen(index)
            val values = attrs.indices.mapToImmList { i -> structValue.get(i) }
            InsertRow(rowidSql, values)
        }
        return InsertData(attrs, rows)
    }

    private fun getRowidGenerator(frame: Rt_CallFrame, count: Int): (Int) -> ParameterizedSql {
        if (count < frame.defCtx.globalCtx.sqlInsertFastRowidCountThreshold) {
            val resSql = buildDefaultRowidSql(frame.sqlCtx)
            return { resSql }
        }

        val sql = ParameterizedSql.generate { b ->
            val rowidsFn = frame.sqlCtx.mainChainMapping().rowidsFunction
            b.append("""SELECT "$rowidsFn"(""")
            b.append(count.toLong())
            b.append(")")
        }

        var firstRowidVar: Long? = null
        sql.executeQuery(frame.userSqlExec) { row ->
            firstRowidVar = row.getLong(1)
        }

        val firstRowid = firstRowidVar!!
        check(firstRowid > 0) { "$count $firstRowid" }

        return { index ->
            ParameterizedSql.generate {
                it.append(firstRowid + index)
            }
        }
    }

    override fun evaluateResult(entities: List<Rt_Value>): Rt_Value {
        return Rt_ListValue(resultListType, entities.toMutableList())
    }
}

internal class R_StructExpr(
    private val struct: R_Struct,
    private val attrs: ImmList<R_CreateExprAttr>,
): R_BaseExpr(struct.type) {
    init {
        checkEquals(attrs.map { it.attr.index }.sorted(), struct.attributesList.indices.toList())
    }

    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val b = Rt_StructValue.Builder(struct.type)
        for (attr in attrs) {
            val value = attr.evaluate(frame)
            b.set(attr.attr, value)
        }
        return b.build()
    }
}

internal class R_AssignExpr(
    type: R_Type,
    private val op: R_BinaryOp,
    private val dstExpr: R_DestinationExpr,
    private val srcExpr: R_Expr,
    private val post: Boolean,
): R_BaseExpr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val ref = dstExpr.evaluateRef(frame)
        ref ?: return Rt_NullValue // Null-safe access operator

        val oldValue = ref.get()
        val srcValue = srcExpr.evaluate(frame)
        val newValue = op.evaluate(oldValue, srcValue)
        ref.set(newValue)

        return if (post) oldValue else newValue
    }
}

internal class R_StatementExpr(private val stmt: R_Statement): R_BaseExpr(R_UnitType) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val res = stmt.execute(frame)
        check(res == null)
        return Rt_UnitValue
    }
}

internal class R_ChainHeightExpr(private val chain: R_ExternalChainRef): R_BaseExpr(R_IntegerType) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val rtChain = frame.defCtx.sqlCtx.linkedChain(chain)
        return Rt_IntValue.get(rtChain.height)
    }
}

internal class R_TypeAdapterExpr(
    type: R_Type,
    private val expr: R_Expr,
    private val adapter: R_TypeAdapter,
): R_BaseExpr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val value = expr.evaluate(frame)
        val value2 = adapter.adaptValue(value)
        return value2
    }
}

internal sealed class R_TypeAdapter {
    abstract fun adaptValue(value: Rt_Value): Rt_Value
}

internal data object R_TypeAdapter_Direct: R_TypeAdapter() {
    override fun adaptValue(value: Rt_Value) = value
}

internal data object R_TypeAdapter_IntegerToBigInteger: R_TypeAdapter() {
    override fun adaptValue(value: Rt_Value): Rt_Value {
        val r = Lib_Type_BigInteger.calcFromInteger(value)
        return r
    }
}

internal data object R_TypeAdapter_IntegerToDecimal: R_TypeAdapter() {
    override fun adaptValue(value: Rt_Value): Rt_Value {
        val r = Lib_Type_Decimal.calcFromInteger(value)
        return r
    }
}

internal data object R_TypeAdapter_BigIntegerToDecimal: R_TypeAdapter() {
    override fun adaptValue(value: Rt_Value): Rt_Value {
        val bigInt = value.asBigInteger()
        val bigDec = BigDecimal(bigInt)
        val r = Rt_DecimalValue.get(bigDec)
        return r
    }
}

internal class R_TypeAdapter_Nullable(private val innerAdapter: R_TypeAdapter): R_TypeAdapter() {
    override fun adaptValue(value: Rt_Value): Rt_Value {
        return if (value == Rt_NullValue) {
            Rt_NullValue
        } else {
            innerAdapter.adaptValue(value)
        }
    }
}

internal class R_ParameterDefaultValueExpr(
    type: R_Type,
    private val callFilePos: R_FilePos,
    private val initFrameGetter: C_LateGetter<R_CallFrame>,
    private val exprGetter: C_LateGetter<R_Expr>,
): R_BaseExpr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val expr = exprGetter.get()
        val res = R_AttributeDefaultValueExpr.evaluate(frame, expr, callFilePos, initFrameGetter)
        return res
    }
}

internal class R_AttributeDefaultValueExpr(
    private val attr: R_Attribute,
    private val createFilePos: R_FilePos?,
    private val initFrameGetter: C_LateGetter<R_CallFrame>,
): R_BaseExpr(attr.type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        return evaluate(frame, attr.expr!!, createFilePos, initFrameGetter)
    }

    companion object {
        fun evaluate(
            frame: Rt_CallFrame,
            expr: R_Expr,
            filePos: R_FilePos?,
            rFrameGetter: C_LateGetter<R_CallFrame>,
        ): Rt_Value {
            return try {
                Rt_Utils.evaluateInNewFrame(frame.defCtx, frame, expr, rFrameGetter)
            } catch (e: Rt_Exception) {
                filePos ?: throw e
                frame.error(R_ErrorPos(filePos), e, true)
            }
        }
    }
}

internal class R_GlobalConstantExpr(
    type: R_Type,
    private val constId: R_GlobalConstantId,
): R_BaseExpr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        return frame.appCtx.getGlobalConstant(constId)
    }
}
