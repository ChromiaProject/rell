/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.utils

import mu.KLogger
import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.model.R_StackPos
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.utils.ImmList
import java.sql.SQLException
import kotlin.math.min

fun Boolean.toGtv(): Gtv = GtvFactory.gtv(this)
fun Int.toGtv(): Gtv = GtvFactory.gtv(this.toLong())
fun Long.toGtv(): Gtv = GtvFactory.gtv(this)
fun String.toGtv(): Gtv = GtvFactory.gtv(this)
fun BlockchainRid.toGtv(): Gtv = GtvFactory.gtv(this.data)

@JvmName("listOfGtvToGtv")
fun List<Gtv>.toGtv(): Gtv = GtvFactory.gtv(this)

@JvmName("listOfStringToGtv")
fun List<String>.toGtv(): Gtv = GtvFactory.gtv(this.map { it.toGtv() })

fun Map<String, Gtv>.toGtv(): Gtv = GtvFactory.gtv(this)

class RellInterpreterCrashException(message: String): RuntimeException(message)

internal class Rt_Comparator<T>(
    private val getter: (Rt_Value) -> T,
    private val comparator: Comparator<T>,
): Comparator<Rt_Value> {
    override fun compare(o1: Rt_Value, o2: Rt_Value): Int {
        if (o1 == Rt_NullValue) {
            return if (o2 == Rt_NullValue) 0 else -1
        } else if (o2 == Rt_NullValue) {
            return 1
        } else {
            val v1 = getter(o1)
            val v2 = getter(o2)
            val c = comparator.compare(v1, v2)
            return c
        }
    }

    companion object {
        fun <T: Comparable<T>> create(getter: (Rt_Value) -> T): Comparator<Rt_Value> =
            Rt_Comparator(getter) { x, y -> x.compareTo(y) }
    }
}

internal class Rt_ListComparator(private val elemComparator: Comparator<Rt_Value>): Comparator<Rt_Value> {
    override fun compare(a: Rt_Value, b: Rt_Value): Int {
        val l1 = a.asList()
        val l2 = b.asList()
        val n1 = l1.size
        val n2 = l2.size
        for (i in 0 until min(n1, n2)) {
            val c = elemComparator.compare(l1[i], l2[i])
            if (c != 0) {
                return c
            }
        }
        return n1.compareTo(n2)
    }
}

internal class Rt_TupleComparator(private val elemComparators: ImmList<Comparator<Rt_Value>>): Comparator<Rt_Value> {
    override fun compare(a: Rt_Value, b: Rt_Value): Int {
        val t1 = a.asTuple()
        val t2 = b.asTuple()
        for ((i, element) in elemComparators.withIndex()) {
            val c = element.compare(t1[i], t2[i])
            if (c != 0) {
                return c
            }
        }
        return 0
    }
}

class Rt_Messages(private val logger: KLogger) {
    private val warningCodes = mutableListOf<String>()
    private val errors = mutableListOf<Rt_CommonError>()

    fun warning(code: String, msg: String) {
        warningCodes += code
        logger.warn(msg)
    }

    fun error(code: String, msg: String) {
        errors += Rt_CommonError(code, msg)
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
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        } catch (e: SQLException) {
            if (e.isPostgresQueryCanceled) {
                throw e
            }
            val errCode = errCodeFn()
            throw Rt_Exception.common(errCode, e.message ?: "error")
        } catch (e: Throwable) {
            val errCode = errCodeFn()
            throw Rt_Exception.common(errCode, e.message ?: "error")
        }
    }

    fun appendStackTrace(msg: String, stack: List<R_StackPos>): String {
        return if (stack.isEmpty()) msg else (msg + "\n" + stack.joinToString("\n") { "\tat $it" })
    }

    fun check(b: Boolean, msgProvider: () -> Pair<String, String>) {
        if (!b) {
            val (code, msg) = msgProvider()
            throw Rt_Exception.common(code, msg)
        }
    }

    fun <T> checkNotNull(value: T?, msgProvider: () -> Pair<String, String>): T {
        if (value == null) {
            val codeMsg = msgProvider()
            throw Rt_Exception.common(codeMsg.first, codeMsg.second)
        }
        return value
    }

    fun <T> checkEquals(actual: T, expected: T) {
        this.check(expected == actual) {
            val code = "check_equals:$expected:$actual"
            val msg = "expected <$expected> actual <$actual>"
            code to msg
        }
    }

    fun <T: Comparable<T>> checkRange(actual: T, min: T, max: T) {
        this.check(actual in min..max) {
            val code = "check_range:$min:$max:$actual"
            val msg = "expected <$min>..<$max> actual <$actual>"
            code to msg
        }
    }

    fun <T: Comparable<T>> checkRange(actual: T, min: T, max: T, mgsProvider: () -> Pair<String, String>) {
        this.check(actual in min..max, mgsProvider)
    }
}
