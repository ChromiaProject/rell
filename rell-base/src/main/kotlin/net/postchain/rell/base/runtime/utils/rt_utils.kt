/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.utils

import mu.KLogger
import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.sql.SqlExecutor
import net.postchain.rell.base.sql.SqlManager
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

fun Boolean.toGtv(): Gtv = GtvFactory.gtv(this)
fun Int.toGtv(): Gtv = GtvFactory.gtv(this.toLong())
fun Long.toGtv(): Gtv = GtvFactory.gtv(this)
fun String.toGtv(): Gtv = GtvFactory.gtv(this)
fun BlockchainRid.toGtv(): Gtv = GtvFactory.gtv(this.data)
fun List<Gtv>.toGtv(): Gtv = GtvFactory.gtv(this)
fun Map<String, Gtv>.toGtv(): Gtv = GtvFactory.gtv(this)

class RellInterpreterCrashException(message: String): RuntimeException(message)

class Rt_Comparator<T>(private val getter: (Rt_Value) -> T, private val comparator: Comparator<T>): Comparator<Rt_Value> {
    override fun compare(o1: Rt_Value?, o2: Rt_Value?): Int {
        if (o1 == null || o1 == Rt_NullValue) {
            return if (o2 == null || o2 == Rt_NullValue) 0 else -1
        } else if (o2 == null || o2 == Rt_NullValue) {
            return 1
        } else {
            val v1 = getter(o1)
            val v2 = getter(o2)
            val c = comparator.compare(v1, v2)
            return c
        }
    }

    companion object {
        fun <T: Comparable<T>> create(getter: (Rt_Value) -> T): Comparator<Rt_Value> {
            return Rt_Comparator(getter, Comparator { x, y -> x.compareTo(y) })
        }
    }
}

class Rt_ListComparator(private val elemComparator: Comparator<Rt_Value>): Comparator<Rt_Value> {
    override fun compare(o1: Rt_Value?, o2: Rt_Value?): Int {
        val l1 = o1!!.asList()
        val l2 = o2!!.asList()
        val n1 = l1.size
        val n2 = l2.size
        for (i in 0 until Math.min(n1, n2)) {
            val c = elemComparator.compare(l1[i], l2[i])
            if (c != 0) {
                return c
            }
        }
        return n1.compareTo(n2)
    }
}

class Rt_TupleComparator(private val elemComparators: List<Comparator<Rt_Value>>): Comparator<Rt_Value> {
    override fun compare(o1: Rt_Value?, o2: Rt_Value?): Int {
        val t1 = o1!!.asTuple()
        val t2 = o2!!.asTuple()
        for (i in 0 until elemComparators.size) {
            val c = elemComparators[i].compare(t1[i], t2[i])
            if (c != 0) {
                return c
            }
        }
        return 0
    }
}

class Rt_SqlManager(private val sqlMgr: SqlManager, private val logErrors: Boolean): SqlManager() {
    override val hasConnection = sqlMgr.hasConnection

    override fun <T> execute0(tx: Boolean, code: (SqlExecutor) -> T): T {
        val res = sqlMgr.execute(tx) { sqlExec ->
            val sqlExec2 = Rt_SqlExecutor(sqlExec, logErrors)
            code(sqlExec2)
        }
        return res
    }
}

class Rt_SqlExecutor(private val sqlExec: SqlExecutor, private val logErrors: Boolean): SqlExecutor() {
    override fun <T> connection(code: (Connection) -> T): T {
        val res = wrapErr("(connection)") {
            sqlExec.connection(code)
        }
        return res
    }

    override fun hasRealConnection() = sqlExec.hasRealConnection()

    override fun execute(sql: String) {
        wrapErr(sql) {
            sqlExec.execute(sql)
        }
    }

    override fun execute(sql: String, preparator: (PreparedStatement) -> Unit) {
        wrapErr(sql) {
            sqlExec.execute(sql, preparator)
        }
    }

    override fun executeUpdate(sql: String, preparator: (PreparedStatement) -> Unit): Int {
        return wrapErr(sql) {
            sqlExec.executeUpdate(sql, preparator)
        }
    }

    override fun executeQuery(sql: String, preparator: (PreparedStatement) -> Unit, consumer: (ResultSet) -> Unit) {
        wrapErr(sql) {
            sqlExec.executeQuery(sql, preparator, consumer)
        }
    }

    private fun <T> wrapErr(sql: String, code: () -> T): T {
        try {
            val res = code()
            return res
        } catch (e: SQLException) {
            if (logErrors) {
                System.err.println("SQL: $sql")
                e.printStackTrace()
            }
            throw Rt_Exception.common("sqlerr:${e.errorCode}", "SQL Error: ${e.message}")
        }
    }
}

class Rt_Messages(private val logger: KLogger) {
    private val warningCodes = mutableListOf<String>()
    private val errors = mutableListOf<Rt_CommonError>()

    fun warning(code: String, msg: String) {
        warningCodes.add(code)
        logger.warn(msg)
    }

    fun error(code: String, msg: String) {
        errors.add(Rt_CommonError(code, msg))
    }

    fun errorIfNotEmpty(list: Collection<String>, code: String, msg: String) {
        if (!list.isEmpty()) {
            val codeList = list.joinToString(",")
            val msgList = list.joinToString(", ")
            error("$code:$codeList", "$msg: $msgList")
        }
    }

    fun checkErrors() {
        if (errors.isEmpty()) {
            return
        }

        if (errors.size == 1) {
            throw Rt_Exception(errors[0])
        }

        val code = errors.joinToString(",") { it.code }
        val msg = errors.joinToString("\n") { it.message() }
        throw Rt_Exception.common(code, msg)
    }

    fun warningCodes() = warningCodes.toList()
}

object Rt_Utils {
    fun errNotSupported(msg: String): Rt_Exception {
        return Rt_Exception.common("not_supported", msg)
    }

    fun <T> wrapErr(errCode: String, code: () -> T): T {
        return wrapErr({ errCode }, code)
    }

    fun <T> wrapErr(errCodeFn: () -> String, code: () -> T): T {
        try {
            val res = code()
            return res
        } catch (e: Rt_Exception) {
            throw e
        } catch (e: Throwable) {
            val errCode = errCodeFn()
            throw Rt_Exception.common(errCode, e.message ?: "error")
        }
    }

    fun appendStackTrace(msg: String, stack: List<R_StackPos>): String {
        return if (stack.isEmpty()) msg else (msg + "\n" + stack.joinToString("\n") { "\tat $it" })
    }

    fun check(b: Boolean, msgProvider: () -> C_CodeMsg) {
        if (!b) {
            val codeMsg = msgProvider()
            throw Rt_Exception.common(codeMsg.code, codeMsg.msg)
        }
    }

    fun <T> checkNotNull(value: T?, msgProvider: () -> C_CodeMsg): T {
        if (value == null) {
            val codeMsg = msgProvider()
            throw Rt_Exception.common(codeMsg.code, codeMsg.msg)
        }
        return value
    }

    fun <T> checkEquals(actual: T, expected: T) {
        check(expected == actual) {
            val code = "check_equals:$expected:$actual"
            val msg = "expected <$expected> actual <$actual>"
            code toCodeMsg msg
        }
    }

    fun <T: Comparable<T>> checkRange(actual: T, min: T, max: T) {
        check(actual in min .. max) {
            val code = "check_range:$min:$max:$actual"
            val msg = "expected <$min>..<$max> actual <$actual>"
            code toCodeMsg msg
        }
    }

    fun <T: Comparable<T>> checkRange(actual: T, min: T, max: T, mgsProvider: () -> C_CodeMsg) {
        check(actual in min .. max, mgsProvider)
    }

    fun evaluateInNewFrame(
        defCtx: Rt_DefinitionContext,
        frame: Rt_CallFrame?,
        expr: R_Expr,
        filePos: R_FilePos?,
        rFrameGetter: C_LateGetter<R_CallFrame>,
    ): Rt_Value {
        val stack = if (filePos == null || frame == null) null else frame.subStack(filePos)
        val rSubFrame = rFrameGetter.get()
        val callCtx = Rt_CallContext(defCtx, stack, frame?.dbUpdateAllowed() ?: true)
        val subFrame = R_FunctionBase.createCallFrame(callCtx, rSubFrame)
        return expr.evaluate(subFrame)
    }
}
