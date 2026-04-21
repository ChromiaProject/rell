/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import net.postchain.gtv.Gtv
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.lib.Lib_OpContext
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_StackPos
import net.postchain.rell.base.model.rr.RR_App
import net.postchain.rell.base.repl.NullReplInterpreterProjExt
import net.postchain.rell.base.repl.ReplInterpreterProjExt
import net.postchain.rell.base.runtime.Rt_Interpreter
import net.postchain.rell.base.runtime.Rt_OpContext
import net.postchain.rell.base.runtime.Rt_Printer
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.NullSqlInitProjExt
import net.postchain.rell.base.sql.SqlExecutor
import net.postchain.rell.base.sql.SqlInitProjExt
import net.postchain.rell.base.utils.*
import kotlin.test.assertEquals

fun String.unwrap(sep: String = ""): String = this.trim().replace(Regex("\\n\\s*"), sep)

fun <T> Boolean.iff(whenFalse: T, whenTrue: T): T = if (this) whenTrue else whenFalse
inline fun <reified T> Boolean.iffArray(vararg whenTrue: T): Array<out T> = if (this) whenTrue else arrayOf()

fun expCtError(vararg errors: String): String {
    require(errors.isNotEmpty())
    val suffix = if (errors.size == 1) errors[0] else errors.joinToString("") { "[$it]" }
    return "ct_err:$suffix"
}

class T_App(
    val rrApp: RR_App,
    val sourceDir: C_SourceDir,
    val rApp: R_App? = null,
    /** Sys-function registrations produced by the compilation that yielded [rrApp]. */
    val compilationSysFns: Map<String, Any> = emptyMap(),
)

interface RellTestProjExt: ProjExt {
    fun getSqlInitProjExt(): SqlInitProjExt
    fun getReplInterpreterProjExt(): ReplInterpreterProjExt

    fun initSysAppTables(sqlExec: SqlExecutor)

    fun createUnitTestBlockRunner(
            sourceDir: C_SourceDir,
            rrApp: RR_App,
            moduleArgs: Map<ModuleName, Gtv>,
    ): Rt_UnitTestBlockRunner
}

object BaseRellTestProjExt: RellTestProjExt {
    override fun getSqlInitProjExt(): SqlInitProjExt = NullSqlInitProjExt
    override fun getReplInterpreterProjExt(): ReplInterpreterProjExt = NullReplInterpreterProjExt

    override fun initSysAppTables(sqlExec: SqlExecutor) = SqlTestUtils.createSysAppTables(sqlExec)

    override fun createUnitTestBlockRunner(
        sourceDir: C_SourceDir,
        rrApp: RR_App,
        moduleArgs: Map<ModuleName, Gtv>
    ): Rt_UnitTestBlockRunner = Rt_NullUnitTestBlockRunner
}

class Rt_TestPrinter: Rt_Printer {
    private val queue = mutableListOf<String>()

    override fun print(str: String) {
        queue.add(str)
    }

    fun chk(vararg expected: String) {
        val expectedList = expected.toList()
        val actualList = queue.toList()
        assertEquals(expectedList, actualList)
        queue.clear()
    }
}

class Rt_TestOpContext(
    private val lastBlockTime: Long,
    private val transactionIid: Long,
    private val blockHeight: Long,
    private val opIndex: Int,
    private val signers: ImmList<Bytes>,
    private val allOperations: ImmList<Pair<String, List<Gtv>>>,
): Rt_OpContext {
    override fun exists() = true
    override fun lastBlockTime() = lastBlockTime
    override fun transactionIid() = transactionIid
    override fun blockHeight() = blockHeight
    override fun opIndex() = opIndex
    override fun isSigner(pubKey: Bytes) = pubKey in signers
    override fun signers() = signers

    override fun allOperations(interpreter: Rt_Interpreter): List<Rt_Value> = allOperations.map { op ->
        Lib_OpContext.gtxTransactionStructValue(interpreter, op.first, op.second)
    }

    override fun currentOperation(interpreter: Rt_Interpreter): Rt_Value {
        val op = allOperations[opIndex]
        return Lib_OpContext.gtxTransactionStructValue(interpreter, op.first, op.second)
    }

    override fun emitEvent(type: String, data: Gtv): Unit = throw Rt_Utils.errNotSupported("Not supported in tests")
}

class RellTestEval {
    @PublishedApi
    internal var wrapping = false

    private var lastErrorStack = listOf<R_StackPos>()

    inline fun eval(code: () -> String): String {
        val oldWrapping = wrapping
        wrapping = true
        return try {
            code()
        } catch (e: EvalException) {
            e.payload
        } finally {
            wrapping = oldWrapping
        }
    }

    fun errorStack() = lastErrorStack

    inline fun <T> wrapCt(code: () -> T): T = if (wrapping) {
        val p = RellTestUtils.catchCtErr0(false, code)
        result(p)
    } else {
        code()
    }

    fun <T> wrapRt(code: () -> T): T = if (wrapping) {
        val p = RellTestUtils.catchRtErr0(code)
        lastErrorStack = p.first?.stack.orEmpty()
        result(Pair(p.first?.res, p.second))
    } else {
        code()
    }

    fun <T> wrapAll(code: () -> T): T = wrapRt {
        wrapCt(code)
    }

    @PublishedApi internal fun <T> result(p: Pair<String?, T?>): T {
        if (p.first != null) throw EvalException(p.first!!)
        return p.second!!
    }

    class EvalException(val payload: String): RuntimeException()
}
