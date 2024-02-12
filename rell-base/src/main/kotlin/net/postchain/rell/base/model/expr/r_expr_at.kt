/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.lib.type.Rt_ListValue
import net.postchain.rell.base.model.*
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.utils.checkEquals

enum class R_AtCardinality(val zero: Boolean, val many: Boolean) {
    ZERO_ONE(true, false),
    ONE(false, false),
    ZERO_MANY(true, true),
    ONE_MANY(false, true),
    ;

    fun matches(count: Int): Boolean {
        if (count < 0 || count == 0 && !zero || count > 1 && !many) {
            return false
        }
        return true
    }
}

class R_DbAtEntity(val rEntity: R_EntityDefinition, val id: R_AtEntityId) {
    override fun toString() = "$rEntity:$id"

    companion object {
        fun checkList(entities : List<R_DbAtEntity>): R_AtExprId {
            val entityIds = entities.map { it.id }
            check(entityIds.toSet().size == entityIds.size) { "Entities not unique: $entityIds" }

            val exprIds = entityIds.map { it.exprId }.toSet()
            check(exprIds.size == 1) { "Entities belong to different expressions: $entityIds" }
            return exprIds.first()
        }
    }
}

sealed class R_AtExprRowDecoder {
    abstract fun decode(row: List<Rt_Value>): Rt_Value
}

object R_AtExprRowDecoder_Simple: R_AtExprRowDecoder() {
    override fun decode(row: List<Rt_Value>): Rt_Value {
        checkEquals(row.size, 1)
        return row[0]
    }
}

class R_AtExprRowDecoder_Tuple(val type: R_TupleType): R_AtExprRowDecoder() {
    override fun decode(row: List<Rt_Value>): Rt_Value {
        check(row.size == type.fields.size) { "row.size == ${row.size}, not ${type.fields.size}" }
        return Rt_TupleValue(type, row.toList())
    }
}

enum class R_AtWhatSort(val asc: Boolean) {
    ASC(true),
    DESC(false),
}

class R_AtWhatFieldFlags(val omit: Boolean, val sort: R_AtWhatSort?, val group: Boolean, val aggregate: Boolean) {
    companion object {
        val DEFAULT = R_AtWhatFieldFlags(omit = false, sort = null, group = false, aggregate = false)
    }
}

class R_AtExprExtras(private val limit: R_Expr?, private val offset: R_Expr?) {
    fun evaluate(frame: Rt_CallFrame): Rt_AtExprExtras {
        val limitVal = evalLimitOffset(frame, limit, "limit")
        val offsetVal = if (limitVal != null && limitVal <= 0L) null else evalLimitOffset(frame, offset, "offset")
        return Rt_AtExprExtras(limitVal, offsetVal)
    }

    private fun evalLimitOffset(frame: Rt_CallFrame, expr: R_Expr?, part: String): Long? {
        if (expr == null) {
            return null
        }

        val v0 = expr.evaluate(frame)
        val v = v0.asInteger()

        if (v < 0) {
            val codeFmt = "expr:at:$part:negative:$v"
            val msgFmt = "Negative $part: $v"
            throw Rt_Exception.common(codeFmt, msgFmt)
        }

        return v
    }
}

class Rt_AtExprExtras(val limit: Long?, val offset: Long?) {
    companion object {
        val NULL = Rt_AtExprExtras(null, null)
    }
}

class R_DbAtExprInternals(
    val block: R_FrameBlock,
    val rowDecoder: R_AtExprRowDecoder,
)

abstract class R_AtExpr(
    type: R_Type,
    val cardinality: R_AtCardinality,
    protected val extras: R_AtExprExtras,
): R_Expr(type) {
    protected fun evalResult(list: MutableList<Rt_Value>): Rt_Value {
        return if (cardinality.many) {
            Rt_ListValue(type, list)
        } else if (list.isNotEmpty()) {
            list[0]
        } else {
            Rt_NullValue
        }
    }

    companion object {
        fun checkCount(cardinality: R_AtCardinality, count: Int, itemMsg: String) {
            if (!cardinality.matches(count)) {
                val code = "at:wrong_count:$count"
                val msg = if (count == 0) "No $itemMsg found" else "Multiple $itemMsg found: $count"
                throw Rt_Exception.common(code, msg)
            }
        }
    }
}

class R_DbAtExpr(
    type: R_Type,
    val base: Db_AtExprBase,
    cardinality: R_AtCardinality,
    extras: R_AtExprExtras,
    private val internals: R_DbAtExprInternals,
): R_AtExpr(type, cardinality, extras) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val extraVals = extras.evaluate(frame)

        val records = frame.block(internals.block) {
            base.execute(frame, extraVals)
        }
        checkCount(cardinality, records.count(), "records")

        val values = MutableList(records.size) { internals.rowDecoder.decode(records[it]) }
        return evalResult(values)
    }
}
