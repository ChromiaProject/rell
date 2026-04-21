/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
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
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.MountName
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.model.R_StackPos
import net.postchain.rell.base.model.rr.RR_App
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.sql.SqlManager
import net.postchain.rell.base.utils.*
import net.postchain.rell.serialization.deserializeRellApp
import net.postchain.rell.serialization.serializeRellApp

object RellTestUtils {
    const val RELL_VER = RellVersions.VERSION_STR

    val NEXT_VER: String = getNextVersion().str()

    const val MAIN_FILE = "main.rell"

    val DEFAULT_COMPILER_OPTIONS = C_CompilerOptions.builder().hiddenLib(true).build()

    /**
     * When `true`, every test compilation round-trips the [RR_App] through
     * `deserialize(serialize(rrApp))` before handing it to the interpreter.
     *
     * Controlled by the system property `rell.test.roundtrip`.
     * Run with: `./gradlew :rell-base:tests:test -Drell.test.roundtrip=true`
     */
    val ROUND_TRIP: Boolean = System.getProperty("rell.test.roundtrip")?.toBooleanStrictOrNull() ?: false

    fun maybeRoundTrip(rrApp: RR_App): RR_App {
        if (!ROUND_TRIP) return rrApp
        val bytes = serializeRellApp(rrApp)
        return deserializeRellApp(bytes)
    }

    val ENCODER_PLAIN = { _: Rt_Type, v: Rt_Value -> v.str(Rt_Value.StrFormat.V1) }
    val ENCODER_STRICT = { _: Rt_Type, v: Rt_Value -> v.strCode() }
    val ENCODER_GTV = { t: Rt_Type, v: Rt_Value -> GtvTestUtils.gtvToStr(t.gtvConversion!!.rtToGtv(v, true)) }
    val ENCODER_GTV_STRICT = { t: Rt_Type, v: Rt_Value -> GtvTestUtils.encodeGtvStr(t.gtvConversion!!.rtToGtv(v, true)) }

    inline fun processApp(code: String, processor: (T_App) -> String): String {
        val sourceDir = C_SourceDir.mapDirOf(MAIN_FILE to code)
        return processApp(sourceDir, processor = processor)
    }

    inline fun processApp(
        sourceDir: C_SourceDir,
        errPos: Boolean = false,
        options: C_CompilerOptions = DEFAULT_COMPILER_OPTIONS,
        outMessages: MutableList<C_Message>? = null,
        modSel: C_CompilerModuleSelection = C_CompilerModuleSelection(immListOf(ModuleName.EMPTY), immListOf()),
        extraLibMod: C_LibModule? = null,
        processor: (T_App) -> String,
    ): String {
        val cRes = compileApp(sourceDir, modSel, options, extraLibMod)
        outMessages?.addAll(cRes.messages)

        if (cRes.errors.isNotEmpty()) {
            val s = msgsToString(cRes.errors, errPos)
            return "ct_err:$s"
        }

        val rrApp = maybeRoundTrip(cRes.rrApp!!)
        val tApp = T_App(
            rrApp = rrApp,
            sourceDir = sourceDir,
            rApp = cRes.app,
            compilationSysFns = cRes.compilationSysFns,
        )
        return processor(tApp)
    }

    fun msgsToString(errs: List<C_Message>, errPos: Boolean = false): String {
        val forceFile = errs.any { it.pos.path().str() != "main.rell" }

        val errMsgs = errs
                .asSequence()
                .sortedBy { it.code }
                .sortedBy { it.pos.column() }
                .sortedBy { it.pos.line() }
                .sortedBy { it.pos.path() }
                .sortedBy { it.pos.path().str() != "main.rell" }
                .map { errToString(it.pos, it.code, errPos, forceFile) }
                .toList()

        return if (errMsgs.size == 1) errMsgs[0] else errMsgs.joinToString("") { "[$it]" }
    }

    fun catchCtErr(errPos: Boolean, block: () -> String): String {
        val r = catchCtErr0(errPos, block)
        return r.first ?: r.second!!
    }

    inline fun <T> catchCtErr0(errPos: Boolean, block: () -> T): Pair<String?, T?> = try {
        val res = block()
        Pair(null, res)
    } catch (e: C_Error) {
        val p = errToString(e.pos, e.code, errPos, false)
        Pair("ct_err:$p", null)
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

    inline fun catchRtErr(block: () -> String): String {
        val p = catchRtErr0(block)
        return p.first?.res ?: p.second!!
    }

    inline fun <T> catchRtErr0(block: () -> T): Pair<TestCallResult?, T?> = try {
        val res = block()
        Pair(null, res)
    } catch (e: Throwable) {
        val res = rtErrToResult(e)
        Pair(res, null)
    }

    fun rtErrToResult(e: Throwable): TestCallResult = when (e) {
        is Rt_Exception -> when (e.err) {
            is Rt_ValueTypeError -> throw e // Internal error, test shall crash.
            else -> TestCallResult(e.err.code(), e.info.stack)
        }
        else -> throw e
    }

    fun callFn(exeCtx: Rt_ExecutionContext, name: String, args: List<Rt_Value>, strict: Boolean): String {
        val interp = exeCtx.appCtx.interpreter
        val fn = findRRFn(interp.rrApp, name)
        val res = catchRtErr {
            val v = interp.callFunction(fn, exeCtx, args, dbUpdateAllowed = true)
            if (strict) v.strCode() else v.toString()
        }
        return res
    }

    private fun findRRFn(rrApp: RR_App, name: String): net.postchain.rell.base.model.rr.RR_FunctionDefinition {
        for (module in rrApp.modules) {
            val fn = module.functions[name]
            if (fn != null) return fn
        }
        throw IllegalStateException("Function not found: '$name'")
    }

    fun callQuery(
        exeCtx: Rt_ExecutionContext,
        name: String,
        args: List<Rt_Value>,
        encoder: (Rt_Type, Rt_Value) -> String,
    ): String {
        val decoder = { _: List<Rt_Type>, args2: List<Rt_Value> -> args2 }
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
        decoder: (List<Rt_Type>, List<T>) -> List<Rt_Value>,
        encoder: (Rt_Type, Rt_Value) -> String
    ): String {
        val interp = exeCtx.appCtx.interpreter
        val mName = MountName.of(name)
        val rrQuery = checkNotNull(interp.rrApp.queries[mName]) { "Query not found: '$name'" }

        val rtParams = rrQuery.params().map { rrp -> interp.resolveType(rrp.type) }
        val rtArgs = eval.wrapRt { decoder(rtParams, args) }

        val res = eval.wrapRt {
            val v = interp.callQuery(rrQuery, exeCtx, rtArgs)
            val rtRetType = interp.resolveType(rrQuery.type())
            encoder(rtRetType, v)
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
            decoder: (List<Rt_Type>, List<T>) -> List<Rt_Value>
    ): String {
        val interp = appCtx.interpreter
        val mName = MountName.of(name)
        val rrOp = checkNotNull(interp.rrApp.operations[mName]) { "Operation not found: '$name'" }

        val rtParams = rrOp.params.map { rrp -> interp.resolveType(rrp.type) }
        val (rtErr, rtArgs) = catchRtErr0 { decoder(rtParams, args) }
        if (rtErr != null) {
            return rtErr.res
        }

        return catchRtErr {
            sqlMgr.transaction { sqlExec ->
                val exeCtx = Rt_ExecutionContext(appCtx, opCtx, sqlCtx, sqlExec, dbReadOnly = false)
                interp.callOperation(rrOp, exeCtx, rtArgs!!)
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
        TestSnippetsRecorder.recordParsing(sourceDir, modSel, options, res)
        return res
    }

    fun getPrevVersion(ver: R_LangVersion): R_LangVersion =
        RellVersions.SUPPORTED_VERSIONS.asSequence().filter { it < ver }.max()

    fun getPrevVersion(ver: String): String = getPrevVersion(R_LangVersion.of(ver)).str()

    fun strToRidHex(s: String) = (s + "00".repeat(32)).substring(0, 64)
    fun strToRidBytes(s: String) = CommonUtils.hexToBytes(strToRidHex(s))
    fun strToBlockchainRid(s: String) = BlockchainRid(strToRidBytes(s))

    private fun getNextVersion(): R_LangVersion {
        val parts = RellVersions.VERSION.parts()
        val delta = listOf(0, 0, 1)
        val nextParts = delta.indices.mapToImmList { parts[it] + delta[it] }
        return R_LangVersion.of(nextParts)
    }

    class TestCallResult(val res: String, val stack: ImmList<R_StackPos>)
}
