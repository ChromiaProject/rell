/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

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
    protected abstract fun evaluate0(frame: Rt_CallFrame): Rt_Value

    fun evaluate(frame: Rt_CallFrame): Rt_Value {
        val res = evaluate0(frame)
        typeCheck(frame, type, res)
        return res
    }

    companion object {
        fun typeCheck(frame: Rt_CallFrame, type: R_Type, value: Rt_Value) {
            if (frame.defCtx.globalCtx.typeCheck) {
                val resType = value.type()
                check(type == R_UnitType || type.isAssignableFrom(resType)) {
                    "${R_Expr::class.java.simpleName}: expected ${type.name}, actual ${resType.name}"
                }
            }
        }
    }
}

class R_ErrorExpr(type: R_Type, private val message: String): R_Expr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        throw RellInterpreterCrashException(message)
    }
}

sealed class R_DestinationExpr(type: R_Type): R_Expr(type) {
    abstract fun evaluateRef(frame: Rt_CallFrame): Rt_ValueRef?

    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val ref = evaluateRef(frame)
        val value = ref?.get()
        return value ?: Rt_NullValue
    }
}

class R_VarExpr(type: R_Type, val ptr: R_VarPtr, val name: String): R_DestinationExpr(type) {
    override fun evaluateRef(frame: Rt_CallFrame): Rt_ValueRef {
        return Rt_VarValueRef(type, ptr, name, frame)
    }

    private class Rt_VarValueRef(
            val type: R_Type,
            val ptr: R_VarPtr,
            val name: String,
            val frame: Rt_CallFrame
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

class R_StructMemberExpr(val base: R_Expr, val attr: R_Attribute): R_DestinationExpr(attr.type) {
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

class R_ConstantValueExpr(type: R_Type, private val value: Rt_Value): R_Expr(type) {
    constructor(value: Rt_Value): this(value.type(), value)

    override fun evaluate0(frame: Rt_CallFrame): Rt_Value = value

    companion object {
        fun makeNull() = R_ConstantValueExpr(Rt_NullValue)
        fun makeBool(v: Boolean) = R_ConstantValueExpr(Rt_BooleanValue.get(v))
        fun makeInt(v: Long) = R_ConstantValueExpr(Rt_IntValue.get(v))
        fun makeText(v: String) = R_ConstantValueExpr(Rt_TextValue.get(v))
        fun makeBytes(v: ByteArray) = R_ConstantValueExpr(Rt_ByteArrayValue.get(v))
    }
}

class R_TupleExpr(private val tupleType: R_TupleType, private val exprs: ImmList<R_Expr>): R_Expr(tupleType) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val values = exprs.map { it.evaluate(frame) }
        return Rt_TupleValue(tupleType, values)
    }
}

class R_ListLiteralExpr(type: R_ListType, val exprs: ImmList<R_Expr>): R_Expr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val list = MutableList(exprs.size) { exprs[it].evaluate(frame) }
        return Rt_ListValue(type, list)
    }
}

class R_MapLiteralExpr(
    private val mapType: R_MapType,
    private val entries: ImmList<Pair<R_Expr, R_Expr>>,
): R_Expr(mapType) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val map = mutableMapOf<Rt_Value, Rt_Value>()
        for ((keyExpr, valueExpr) in entries) {
            val key = keyExpr.evaluate(frame)
            val value = valueExpr.evaluate(frame)
            if (key in map) {
                throw Rt_Exception.common("expr_map_dupkey:${key.strCode()}", "Duplicate map key: ${key.str()}")
            }
            map[key] = value
        }
        return Rt_MapValue(mapType, map)
    }
}

class R_ListSubscriptExpr(type: R_Type, val base: R_Expr, val expr: R_Expr): R_DestinationExpr(type) {
    override fun evaluateRef(frame: Rt_CallFrame): Rt_ValueRef {
        val baseValue = base.evaluate(frame)
        val indexValue = expr.evaluate(frame)
        val list = baseValue.asList()
        val index = indexValue.asInteger()
        Rt_ListValue.checkIndex(list.size, index)
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

class R_VirtualListSubscriptExpr(type: R_Type, val base: R_Expr, val expr: R_Expr): R_Expr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val baseValue = base.evaluate(frame)
        val indexValue = expr.evaluate(frame)
        val list = baseValue.asVirtualList()
        val index = indexValue.asInteger()
        val res = list.get(index)
        return res
    }
}

class R_MapSubscriptExpr(type: R_Type, val base: R_Expr, val expr: R_Expr): R_DestinationExpr(type) {
    override fun evaluateRef(frame: Rt_CallFrame): Rt_ValueRef {
        val baseValue = base.evaluate(frame)
        val keyValue = expr.evaluate(frame)
        val map = baseValue.asMutableMap()
        return Rt_MapValueRef(map, keyValue)
    }

    private class Rt_MapValueRef(val map: MutableMap<Rt_Value, Rt_Value>, val key: Rt_Value): Rt_ValueRef() {
        override fun get() = getValue(map, key)

        override fun set(value: Rt_Value) {
            map.put(key, value)
        }
    }

    companion object {
        fun getValue(map: Map<Rt_Value, Rt_Value>, key: Rt_Value): Rt_Value {
            val value = map[key]
            if (value == null) {
                throw Rt_Exception.common("fn_map_get_novalue:${key.strCode()}", "Key not in map: ${key.str()}")
            }
            return value
        }
    }
}

class R_VirtualMapSubscriptExpr(type: R_Type, val base: R_Expr, val expr: R_Expr): R_Expr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val baseValue = base.evaluate(frame)
        val keyValue = expr.evaluate(frame)
        val map = baseValue.asMap()
        val res = R_MapSubscriptExpr.getValue(map, keyValue)
        return res
    }
}

class R_TextSubscriptExpr(val base: R_Expr, val expr: R_Expr): R_Expr(R_TextType) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val baseValue = base.evaluate(frame)
        val indexValue = expr.evaluate(frame)
        val str = baseValue.asString()
        val index = indexValue.asInteger()

        if (index < 0 || index >= str.length) {
            throw Rt_Exception.common("expr_text_subscript_index:${str.length}:$index",
                    "Index out of bounds: $index (length ${str.length})")
        }

        val i = index.toInt()
        val res = str.substring(i, i + 1)
        return Rt_TextValue.get(res)
    }
}

class R_ByteArraySubscriptExpr(val base: R_Expr, val expr: R_Expr): R_Expr(R_IntegerType) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val baseValue = base.evaluate(frame)
        val indexValue = expr.evaluate(frame)
        val array = baseValue.asByteArray()
        val index = indexValue.asInteger()

        if (index < 0 || index >= array.size) {
            throw Rt_Exception.common("expr_bytearray_subscript_index:${array.size}:$index",
                    "Index out of bounds: $index (length ${array.size})")
        }

        val i = index.toInt()
        val v = array[i].toLong()
        val res = if (v >= 0) v else v + 256
        return Rt_IntValue.get(res)
    }
}

class R_ElvisExpr(type: R_Type, val left: R_Expr, val right: R_Expr): R_Expr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val leftVal = left.evaluate(frame)
        if (leftVal != Rt_NullValue) {
            return leftVal
        }

        val rightVal = right.evaluate(frame)
        return rightVal
    }
}

class R_NotNullExpr(type: R_Type, val expr: R_Expr): R_Expr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val v = expr.evaluate(frame)
        if (v == Rt_NullValue) {
            throw Rt_Exception.common("null_value", "Null value")
        }
        return v
    }
}

class R_IfExpr(type: R_Type, val cond: R_Expr, val trueExpr: R_Expr, val falseExpr: R_Expr): R_Expr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val v = cond.evaluate(frame)
        val b = v.asBoolean()
        val expr = if (b) trueExpr else falseExpr
        val res = expr.evaluate(frame)
        return res
    }
}

sealed class R_WhenChooser {
    abstract fun choose(frame: Rt_CallFrame): Int?
}

class R_IterativeWhenChooser(val keyExpr: R_Expr, val exprs: ImmList<IndexedValue<R_Expr>>, val elseIdx: Int?): R_WhenChooser() {
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

class R_LookupWhenChooser(val keyExpr: R_Expr, val map: ImmMap<Rt_Value, Int>, val elseIdx: Int?): R_WhenChooser() {
    override fun choose(frame: Rt_CallFrame): Int? {
        val keyValue = keyExpr.evaluate(frame)
        val idx = map[keyValue]
        return idx ?: elseIdx
    }
}

class R_WhenExpr(type: R_Type, val chooser: R_WhenChooser, val exprs: ImmList<R_Expr>): R_Expr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val choice = chooser.choose(frame)
        check(choice != null)
        val expr = exprs[choice]
        val res = expr.evaluate(frame)
        return res
    }
}

class R_CreateExprAttr(val attr: R_Attribute, private val expr: R_Expr) {
    fun evaluate(frame: Rt_CallFrame) = expr.evaluate(frame)
}

sealed class R_CreateExpr(type: R_Type, private val rEntity: R_EntityDefinition): R_Expr(type) {
    protected abstract fun evaluateData(frame: Rt_CallFrame): InsertData
    protected abstract fun evaluateResult(entities: List<Rt_Value>): Rt_Value

    final override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        frame.checkDbUpdateAllowed()

        val allValues = evaluateData(frame)
        val allRows = mutableListOf<List<Rt_Value>>()

        val valuesPages = splitValues(frame.appCtx.globalCtx, allValues)

        for (page in valuesPages) {
            val sqlCtx = frame.defCtx.sqlCtx
            val rtSql = buildSql(sqlCtx, rEntity, page)
            val rtSel = SqlSelect(rtSql, immListOf(rEntity.type))
            val rows = rtSel.execute(frame.userSqlExec)
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

            val constraintsSql = SqlGen.genAddAttrConstraintsSql(sqlCtx, table, attrs.map { it.attr })
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

class R_RegularCreateExpr(
    rEntity: R_EntityDefinition,
    val attrs: ImmList<R_CreateExprAttr>,
): R_CreateExpr(rEntity.type, rEntity) {
    override fun evaluateData(frame: Rt_CallFrame): InsertData {
        val resAttrs = attrs.mapToImmList { it.attr }
        val rowidSql = buildDefaultRowidSql(frame.sqlCtx)
        val values = attrs.mapToImmList { it.evaluate(frame) }
        val row = InsertRow(rowidSql, values)
        return InsertData(resAttrs, immListOf(row))
    }

    override fun evaluateResult(entities: List<Rt_Value>): Rt_Value {
        checkEquals(entities.size, 1)
        return entities[0]
    }
}

class R_StructCreateExpr(
    rEntity: R_EntityDefinition,
    private val structType: R_StructType,
    private val structExpr: R_Expr,
): R_CreateExpr(rEntity.type, rEntity) {
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

class R_StructListCreateExpr(
    rEntity: R_EntityDefinition,
    private val structType: R_StructType,
    private val resultListType: R_ListType,
    private val listExpr: R_Expr,
): R_CreateExpr(resultListType, rEntity) {
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

class R_StructExpr(private val struct: R_Struct, private val attrs: ImmList<R_CreateExprAttr>): R_Expr(struct.type) {
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

class R_AssignExpr(
        type: R_Type,
        val op: R_BinaryOp,
        val dstExpr: R_DestinationExpr,
        val srcExpr: R_Expr,
        val post: Boolean
): R_Expr(type) {
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

class R_StatementExpr(val stmt: R_Statement): R_Expr(R_UnitType) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val res = stmt.execute(frame)
        check(res == null)
        return Rt_UnitValue
    }
}

class R_ChainHeightExpr(val chain: R_ExternalChainRef): R_Expr(R_IntegerType) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val rtChain = frame.defCtx.sqlCtx.linkedChain(chain)
        return Rt_IntValue.get(rtChain.height)
    }
}

class R_StackTraceExpr(private val subExpr: R_Expr, private val filePos: R_FilePos): R_Expr(subExpr.type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        return trackStack(frame, filePos) {
            subExpr.evaluate(frame)
        }
    }

    companion object {
        fun <T> trackStack(frame: Rt_CallFrame, filePos: R_FilePos, code: () -> T): T {
            try {
                return code()
            } catch (e: Rt_Exception) {
                throw if (e.info.stack.isNotEmpty()) e else {
                    val stack = frame.stackTrace(filePos)
                    val info = Rt_ExceptionInfo(extraMessage = e.info.extraMessage, stack = stack.toImmList())
                    Rt_Exception(e.err, info, e)
                }
            }
        }
    }
}

class R_TypeAdapterExpr(type: R_Type, private val expr: R_Expr, private val adapter: R_TypeAdapter): R_Expr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val value = expr.evaluate(frame)
        val value2 = adapter.adaptValue(value)
        return value2
    }
}

sealed class R_TypeAdapter {
    abstract fun adaptValue(value: Rt_Value): Rt_Value
}

object R_TypeAdapter_Direct: R_TypeAdapter() {
    override fun adaptValue(value: Rt_Value) = value
}

object R_TypeAdapter_IntegerToBigInteger: R_TypeAdapter() {
    override fun adaptValue(value: Rt_Value): Rt_Value {
        val r = Lib_Type_BigInteger.calcFromInteger(value)
        return r
    }
}

object R_TypeAdapter_IntegerToDecimal: R_TypeAdapter() {
    override fun adaptValue(value: Rt_Value): Rt_Value {
        val r = Lib_Type_Decimal.calcFromInteger(value)
        return r
    }
}

object R_TypeAdapter_BigIntegerToDecimal: R_TypeAdapter() {
    override fun adaptValue(value: Rt_Value): Rt_Value {
        val bigInt = value.asBigInteger()
        val bigDec = BigDecimal(bigInt)
        val r = Rt_DecimalValue.get(bigDec)
        return r
    }
}

class R_TypeAdapter_Nullable(private val innerAdapter: R_TypeAdapter): R_TypeAdapter() {
    override fun adaptValue(value: Rt_Value): Rt_Value {
        return if (value == Rt_NullValue) {
            Rt_NullValue
        } else {
            innerAdapter.adaptValue(value)
        }
    }
}

class R_ParameterDefaultValueExpr(
    type: R_Type,
    private val callFilePos: R_FilePos,
    private val initFrameGetter: C_LateGetter<R_CallFrame>,
    private val exprGetter: C_LateGetter<R_Expr>,
): R_Expr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val expr = exprGetter.get()
        val res = Rt_Utils.evaluateInNewFrame(frame.defCtx, frame, expr, callFilePos, initFrameGetter)
        return res
    }
}

class R_AttributeDefaultValueExpr(
    private val attr: R_Attribute,
    private val createFilePos: R_FilePos?,
    private val initFrameGetter: C_LateGetter<R_CallFrame>,
): R_Expr(attr.type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        return Rt_Utils.evaluateInNewFrame(frame.defCtx, frame, attr.expr!!, createFilePos, initFrameGetter)
    }
}

class R_BlockCheckExpr(private val expr: R_Expr, private val blockUid: R_FrameBlockUid): R_Expr(expr.type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        frame.checkBlock(blockUid)
        return expr.evaluate(frame)
    }
}

class R_GlobalConstantExpr(
        type: R_Type,
        private val constId: R_GlobalConstantId
): R_Expr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        return frame.appCtx.getGlobalConstant(constId)
    }
}
