/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import net.postchain.common.BlockchainRid
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_CompilationResult
import net.postchain.rell.base.compiler.base.core.C_Compiler
import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.model.*
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.sql.SqlManager
import net.postchain.rell.base.utils.CommonUtils

object RellTestUtils {
    const val RELL_VER = "0.13.9"

    const val MAIN_FILE = "main.rell"
    val MAIN_FILE_PATH = C_SourcePath.parse(MAIN_FILE)

    val DEFAULT_COMPILER_OPTIONS = C_CompilerOptions.builder().hiddenLib(true).build()

    val ENCODER_PLAIN = { _: R_Type, v: Rt_Value -> v.str(Rt_Value.StrFormat.V1) }
    val ENCODER_STRICT = { _: R_Type, v: Rt_Value -> v.strCode() }
    val ENCODER_GTV = { t: R_Type, v: Rt_Value -> GtvTestUtils.gtvToStr(t.rtToGtv(v, true)) }
    val ENCODER_GTV_STRICT = { t: R_Type, v: Rt_Value -> GtvTestUtils.encodeGtvStr(t.rtToGtv(v, true)) }

    fun processApp(code: String, processor: (T_App) -> String): String {
        val sourceDir = C_SourceDir.mapDirOf(MAIN_FILE to code)
        return processApp(sourceDir, processor = processor)
    }

    fun processApp(
        sourceDir: C_SourceDir,
        errPos: Boolean = false,
        options: C_CompilerOptions = DEFAULT_COMPILER_OPTIONS,
        outMessages: MutableList<C_Message>? = null,
        modules: List<R_ModuleName> = listOf(R_ModuleName.EMPTY),
        testModules: List<R_ModuleName> = listOf(),
        extraLibMod: C_LibModule? = null,
        processor: (T_App) -> String,
    ): String {
        val modSel = C_CompilerModuleSelection(modules, testModules)
        val cRes = compileApp(sourceDir, modSel, options, extraLibMod)
        outMessages?.addAll(cRes.messages)

        if (cRes.errors.isNotEmpty()) {
            val s = msgsToString(cRes.errors, errPos)
            return "ct_err:$s"
        }

        val tApp = T_App(cRes.app!!, cRes.messages)
        return processor(tApp)
    }

    fun msgsToString(errs: List<C_Message>, errPos: Boolean = false): String {
        val forceFile = errs.any { it.pos.path().str() != "main.rell" }

        val errMsgs = errs
                .sortedBy { it.code }
                .sortedBy { it.pos.column() }
                .sortedBy { it.pos.line() }
                .sortedBy { it.pos.path() }
                .sortedBy { it.pos.path().str() != "main.rell" }
                .map { errToString(it.pos, it.code, errPos, forceFile) }

        return if (errMsgs.size == 1) errMsgs[0] else errMsgs.joinToString("") { "[$it]" }
    }

    fun catchCtErr(errPos: Boolean, block: () -> String): String {
        val r = catchCtErr0(errPos, block)
        return r.first ?: r.second!!
    }

    fun <T> catchCtErr0(errPos: Boolean, block: () -> T): Pair<String?, T?> {
        try {
            val res = block()
            return Pair(null, res)
        } catch (e: C_Error) {
            val p = errToString(e.pos, e.code, errPos, false)
            return Pair("ct_err:$p", null)
        }
    }

    fun errToString(pos: S_Pos, code: String, forcePos: Boolean, forceFile: Boolean): String {
        val file = pos.path().str()
        return if (forcePos) {
            "$pos:$code"
        } else if (forceFile || file != "main.rell") {
            "$file:$code"
        } else {
            code
        }
    }

    fun catchRtErr(block: () -> String): String {
        val p = catchRtErr0(block)
        return p.first?.res ?: p.second!!
    }

    fun <T> catchRtErr0(block: () -> T): Pair<TestCallResult?, T?> {
        return try {
            val res = block()
            Pair(null, res)
        } catch (e: Throwable) {
            val res = rtErrToResult(e)
            Pair(res, null)
        }
    }

    fun rtErrToResult(e: Throwable): TestCallResult {
        return when (e) {
            is Rt_Exception -> when (e.err) {
                is Rt_ValueTypeError -> throw e // Internal error, test shall crash.
                else -> TestCallResult(e.err.code(), e.info.stack)
            }
            else -> throw e
        }
    }

    fun callFn(exeCtx: Rt_ExecutionContext, name: String, args: List<Rt_Value>, strict: Boolean): String {
        val fn = findFn(exeCtx.appCtx.app, name)
        val res = catchRtErr {
            val v = fn.callTop(exeCtx, args)
            if (strict) v.strCode() else v.toString()
        }
        return res
    }

    private fun findFn(app: R_App, name: String): R_FunctionDefinition {
        for (module in app.modules) {
            val fn = module.functions[name]
            if (fn != null) return fn
        }
        throw IllegalStateException("Function not found: '$name'")
    }

    fun callQuery(exeCtx: Rt_ExecutionContext, name: String, args: List<Rt_Value>, encoder: (R_Type, Rt_Value) -> String): String {
        val decoder = { _: List<R_FunctionParam>, args2: List<Rt_Value> -> args2 }
        val eval = RellTestEval()
        return eval.eval {
            callQueryGeneric(eval, exeCtx, name, args, decoder, encoder)
        }
    }

    fun <T> callQueryGeneric(
        eval: RellTestEval,
        exeCtx: Rt_ExecutionContext,
        name: String,
        args: List<T>,
        decoder: (List<R_FunctionParam>, List<T>) -> List<Rt_Value>,
        encoder: (R_Type, Rt_Value) -> String
    ): String {
        val mName = R_MountName.of(name)
        val query = exeCtx.appCtx.app.queries[mName]
        if (query == null) throw IllegalStateException("Query not found: '$name'")

        val rtArgs = eval.wrapRt { decoder(query.params(), args) }

        val res = eval.wrapRt {
            val v = query.call(exeCtx, rtArgs!!)
            encoder(query.type(), v)
        }

        return res
    }

    fun <T> callOpGeneric(
            appCtx: Rt_AppContext,
            opCtx: Rt_OpContext,
            sqlCtx: Rt_SqlContext,
            sqlMgr: SqlManager,
            name: String,
            args: List<T>,
            decoder: (List<R_FunctionParam>, List<T>) -> List<Rt_Value>
    ): String {
        val mName = R_MountName.of(name)
        val op = appCtx.app.operations[mName]
        if (op == null) throw IllegalStateException("Operation not found: '$name'")

        val (rtErr, rtArgs) = catchRtErr0 { decoder(op.params(), args) }
        if (rtErr != null) {
            return rtErr.res
        }

        return catchRtErr {
            sqlMgr.transaction { sqlExec ->
                val exeCtx = Rt_ExecutionContext(appCtx, opCtx, sqlCtx, sqlExec)
                op.call(exeCtx, rtArgs!!)
                "OK"
            }
        }
    }

    fun compileApp(
        sourceDir: C_SourceDir,
        modSel: C_CompilerModuleSelection,
        options: C_CompilerOptions,
        extraMod: C_LibModule? = null,
    ): C_CompilationResult {
        val res = C_Compiler.compileInternal(sourceDir, modSel, options, extraMod)
        TestSnippetsRecorder.record(sourceDir, modSel, options, res)
        return res
    }

    fun strToRidHex(s: String) = (s + "00".repeat(32)).substring(0, 64)
    fun strToRidBytes(s: String) = CommonUtils.hexToBytes(strToRidHex(s))
    fun strToBlockchainRid(s: String) = BlockchainRid(strToRidBytes(s))

    class TestCallResult(val res: String, val stack: List<R_StackPos>)
}
