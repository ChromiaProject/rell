/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.repl

import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.utils.C_CommonError
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.compiler.base.utils.lateInit
import net.postchain.rell.base.model.DefinitionId
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.R_CallFrame
import net.postchain.rell.base.model.rr.RR_App
import net.postchain.rell.base.model.rr.RR_AppSqlDefs
import net.postchain.rell.base.model.rr.RR_ReplCode
import net.postchain.rell.base.model.rr.resolveWithReplStatements
import net.postchain.rell.base.model.stmt.R_Statement
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.sql.*
import net.postchain.rell.base.utils.*

class C_ReplCodeState(
    val frameProto: C_CallFrameProto,
    val blockCodeProto: C_BlockCodeProto,
) {
    companion object { val EMPTY = C_ReplCodeState(C_CallFrameProto.EMPTY, C_BlockCodeProto.EMPTY) }
}

class Rt_ReplCodeState(
    val frameState: Rt_CallFrameState,
    val globalConstants: Rt_GlobalConstants.State,
) {
    companion object { val EMPTY = Rt_ReplCodeState(Rt_CallFrameState.EMPTY, Rt_GlobalConstants.State()) }
}

class ReplCodeState(val cState: C_ReplCodeState, val rtState: Rt_ReplCodeState) {
    companion object { val EMPTY = ReplCodeState(C_ReplCodeState.EMPTY, Rt_ReplCodeState.EMPTY) }
}

class ReplCode(
    val rCode: R_ReplCode,
    private val newCtState: C_ReplCodeState,
    private val oldRtState: Rt_ReplCodeState,
) {
    fun execute(exeCtx: Rt_ExecutionContext, rrReplCode: RR_ReplCode): ReplCodeState {
        val interpreter = exeCtx.appCtx.interpreter
        val newRtState = R_ReplCode.executeViaInterpreter(interpreter, rrReplCode, exeCtx, oldRtState)
        return ReplCodeState(newCtState, newRtState)
    }

    companion object {
        val ERROR = ReplCode(R_ReplCode(R_CallFrame.ERROR, immListOf()), C_ReplCodeState.EMPTY, Rt_ReplCodeState.EMPTY)
    }
}

class C_ReplCommandContext(
    val frameCtx: C_FrameContext,
    val codeState: ReplCodeState,
) {
    val fnCtx = frameCtx.fnCtx
    val defCtx = fnCtx.defCtx
    val mntCtx = defCtx.mntCtx
    val nsCtx = mntCtx.nsCtx
    val executor = nsCtx.executor

    init {
        check(mntCtx.nsCtx === frameCtx.fnCtx.nsCtx)
    }

    private val commandLate = defCtx.lateInit(C_CompilerPass.EXPRESSIONS, ReplCode.ERROR)
    val commandGetter = commandLate.getter

    fun setCommand(code: ReplCode) = commandLate.set(code)
}

class R_ReplCode(
    val frame: R_CallFrame,
    val stmts: ImmList<R_Statement>,
) {
    companion object {
        fun executeViaInterpreter(
            interpreter: Rt_Interpreter,
            rrCode: RR_ReplCode,
            exeCtx: Rt_ExecutionContext,
            oldState: Rt_ReplCodeState,
        ): Rt_ReplCodeState {
            val rtDefCtx = Rt_DefinitionContext(exeCtx, false, DefinitionId("", "<console>"))
            val rtFrame = Rt_CallFrame(rtDefCtx, rrCode.frame, oldState.frameState)

            interpreter.executeStatements(rrCode.stmts, rtFrame)

            val newFrameState = rtFrame.dumpState()
            val newConstantsState = exeCtx.appCtx.dumpGlobalConstants()
            return Rt_ReplCodeState(newFrameState, newConstantsState)
        }
    }
}

interface ReplInterpreterProjExt: ProjExt {
    fun getSqlInitProjExt(): SqlInitProjExt
    fun createBlockRunner(sourceDir: C_SourceDir, modules: List<ModuleName>): Rt_UnitTestBlockRunner
}

object NullReplInterpreterProjExt: ReplInterpreterProjExt {
    override fun getSqlInitProjExt(): SqlInitProjExt = NullSqlInitProjExt

    override fun createBlockRunner(sourceDir: C_SourceDir, modules: List<ModuleName>): Rt_UnitTestBlockRunner =
        Rt_NullUnitTestBlockRunner
}

class ReplInterpreterConfig(
    val compilerOptions: C_CompilerOptions,
    val sourceDir: C_SourceDir,
    val module: ModuleName?,
    val rtGlobalCtx: Rt_GlobalContext,
    val sqlMgr: SqlManager,
    val projExt: ReplInterpreterProjExt,
    val outChannel: ReplOutputChannel,
    val moduleArgsSource: Rt_ModuleArgsSource,
)

class ReplInterpreter private constructor(
    private val config: ReplInterpreterConfig,
) {
    private val sqlMgr = config.sqlMgr
    private val outChannel = config.outChannel

    private val commands = ControlCommands()
    private val cGlobalCtx = C_GlobalContext(config.compilerOptions, config.sourceDir)

    private var defsState = C_ReplDefsState.EMPTY
    private var codeState = ReplCodeState.EMPTY
    private var exeState: Rt_ExecutionContext.State? = null
    private var lastUpdateSqlDefs: RR_AppSqlDefs? = null

    private var mustQuit = false
    private var sqlUpdateAuto = false

    fun mustQuit() = mustQuit

    fun getHelpCommand() = commands.helpCmd
    fun getQuitCommand() = commands.quitCmd

    fun execute(command: String) {
        val trim = command.trim()
        if (trim.startsWith("\\")) {
            val ctrl = commands.map[trim]
            if (ctrl != null) {
                ctrl.action()
            } else {
                outChannel.printCompilerError("repl:invalid_command:$trim", "Invalid command: '$trim'")
            }
        } else {
            executeCode(command, false)
        }
    }

    private fun executeCode(code: String, forceSqlUpdate: Boolean): Boolean {
        val success = compile(code) ?: return false

        return executeCatch {
            val rCode = success.code.rCode
            val resolverRuntime = Rt_ResolverRuntime()
            val (rrApp, rrReplCode) = resolveWithReplStatements(success.app, resolverRuntime, rCode.stmts, rCode.frame)
            val compilationSysFns = resolverRuntime.collectedSysFns()
            val sqlCtx = Rt_RegularSqlContext.createNoExternalChains(rrApp, Rt_ChainSqlMapping(0))
            val rtAppCtx = createRtAppContext(config.rtGlobalCtx, rrApp, compilationSysFns)
            sqlUpdate(rtAppCtx, sqlCtx, forceSqlUpdate)

            sqlMgr.access { sqlExec ->
                val exeCtx = Rt_ExecutionContext(rtAppCtx, Rt_NullOpContext, sqlCtx, sqlExec, dbReadOnly = false, exeState)
                codeState = success.code.execute(exeCtx, rrReplCode)
                defsState = success.defsState
                exeState = exeCtx.toState()
            }
        }
    }

    private fun compile(code: String): C_ReplSuccess? {
        val cRes = try {
            C_ReplCompiler.compile(config.sourceDir, config.module, code, cGlobalCtx, defsState, codeState)
        } catch (e: C_CommonError) {
            outChannel.printCompilerError(e.code, e.msg)
            return null
        }

        for (message in cRes.messages) {
            outChannel.printCompilerMessage(message)
        }

        return cRes.success
    }

    private fun executeCatch(code: () -> Unit): Boolean {
        try {
            code()
            return true
        } catch (e: Rt_Exception) {
            outChannel.printRuntimeError(e)
        } catch (e: Throwable) {
            outChannel.printPlatformRuntimeError(e)
        }
        return false
    }

    private fun createRtAppContext(
        globalCtx: Rt_GlobalContext,
        rrApp: RR_App,
        compilationSysFns: Map<String, Any> = emptyMap(),
    ): Rt_AppContext {
        val modules = (listOfNotNull(config.module).toSet() + defsState.appState.modules.keys.map { it.name }).toList()

        return Rt_AppContext(
            globalCtx = globalCtx,
            chainCtx = Rt_ChainContext.NULL,
            interpreter = Rt_Interpreter.forCompilation(rrApp, compilationSysFns),
            repl = true,
            test = false,
            replOut = outChannel,
            blockRunner = config.projExt.createBlockRunner(config.sourceDir, modules),
            moduleArgsSource = config.moduleArgsSource,
            globalConstantsState = codeState.rtState.globalConstants,
        )
    }

    private fun sqlUpdate(appCtx: Rt_AppContext, sqlCtx: Rt_SqlContext, force: Boolean) {
        if (!sqlUpdateAuto && !force) {
            return
        }

        val lastDefs = lastUpdateSqlDefs
        val appDefs = sqlCtx.appDefs

        if (sqlMgr.hasConnection && (lastDefs == null || appDefs != lastDefs)) {
            val logging = if (force) SQL_INIT_LOGGING_FORCE else SQL_INIT_LOGGING_AUTO
            sqlMgr.transaction { sqlExec ->
                val exeCtx = Rt_ExecutionContext(appCtx, Rt_NullOpContext, sqlCtx, sqlExec, dbReadOnly = false)
                val initProjExt = config.projExt.getSqlInitProjExt()
                SqlInit.init(exeCtx, initProjExt, logging)
            }
            lastUpdateSqlDefs = appDefs
        }
    }

    private inner class ControlCommands {
        private fun dbUpdate() {
            executeCode("", true)
        }

        private fun dbAuto() {
            val v = !sqlUpdateAuto
            sqlUpdateAuto = v
            val s = if (v) "on" else "off"
            outChannel.printControl("db-auto:$v", "SQL auto-update is $s")
            if (v) {
                dbUpdate()
            }
        }

        private fun help() {
            val table = mutableListOf<List<String>>()
            val aliases = mutableMapOf<Ctrl, String>()

            for (cmd in map.keys.sorted()) {
                val ctrl = map.getValue(cmd)
                val alias = aliases[ctrl]
                val help = if (alias == null) ctrl.help else "Same as '$alias'."
                aliases.putIfAbsent(ctrl, cmd)
                table.add(listOf(cmd, help))
            }

            val tableList = CommonUtils.tableToStrings(table)
            val str = "List of all control commands:\n" + tableList.joinToString("\n")
            config.rtGlobalCtx.outPrinter.print(str)
        }

        private fun exit() {
            mustQuit = true
        }

        private val dbUpdate = Ctrl("Update SQL tables to match defined entities and objects.") { dbUpdate() }
        private val dbAuto = Ctrl("Automatically update SQL tables when new entities or objects are defined.") { dbAuto() }
        private val help = Ctrl("Display this help.") { help() }
        private val exit = Ctrl("Exit.") { exit() }

        private fun formatCtrl(msg: String, format: ReplValueFormat) = Ctrl("Output values as $msg") {
            outChannel.setValueFormat(format)
        }

        private val rawHelpCmd = "?"
        private val rawQuitCmd = "q"

        val helpCmd = fullCmd(rawHelpCmd)
        val quitCmd = fullCmd(rawQuitCmd)

        private val rawMap = mapOf(
                rawHelpCmd to help,
                "exit" to exit,
                rawQuitCmd to exit,
                "db-update" to dbUpdate,
                "db-auto" to dbAuto,
                "og" to formatCtrl("Gtv.toString()", ReplValueFormat.GTV_STRING),
                "oj" to formatCtrl("JSON (Gtv)", ReplValueFormat.GTV_JSON),
                "ox" to formatCtrl("XML (Gtv)", ReplValueFormat.GTV_XML),
                "ol" to formatCtrl("one collection item per line", ReplValueFormat.ONE_ITEM_PER_LINE),
                "od" to formatCtrl("default text representation (result of to_text())", ReplValueFormat.DEFAULT),
        )

        val map = rawMap.mapKeysToImmMap { (k, _) -> fullCmd(k) }

        private fun fullCmd(rawCmd: String) = "\\$rawCmd"
    }

    private class Ctrl(val help: String, val action: () -> Unit)

    companion object {
        private val SQL_INIT_LOGGING_AUTO = SqlInitLogging(step = true, stepEmptyDb = true)

        private val SQL_INIT_LOGGING_FORCE = SqlInitLogging(
                header = true,
                step = true,
                stepEmptyDb = true,
                metaNoCode = true
        )

        fun create(config: ReplInterpreterConfig): ReplInterpreter? {
            val interpreter = ReplInterpreter(config)
            val init = interpreter.executeCode("", true) // Make sure the module can be found and has no errors.
            return if (init) interpreter else null
        }
    }
}
