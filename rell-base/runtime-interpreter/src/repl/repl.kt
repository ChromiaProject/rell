/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.repl

import net.postchain.rell.base.compiler.ast.S_PosRange
import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.expr.C_StmtContext
import net.postchain.rell.base.compiler.base.module.C_ExtModuleCompiler
import net.postchain.rell.base.compiler.base.module.C_ModuleKey
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.model.DefinitionId
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.MountName
import net.postchain.rell.base.model.R_CallFrame
import net.postchain.rell.base.model.rr.RR_App
import net.postchain.rell.base.model.rr.RR_AppSqlDefs
import net.postchain.rell.base.model.rr.RR_ReplCode
import net.postchain.rell.base.model.rr.resolveWithReplStatements
import net.postchain.rell.base.model.stmt.R_Statement
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.sql.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.ide.IdeSymbolKind

private const val REPL_NAME = "<REPL>"

private val REPL_POS_RANGE = S_PosRange(C_Parser.REPL_NULL_POS, C_Parser.REPL_NULL_POS)

internal class ReplCodeState(
    val frameProto: C_CallFrameProto,
    val blockCodeProto: C_BlockCodeProto,
    val frameState: Rt_CallFrameState,
    val globalConstants: Rt_GlobalConstants.State,
) {
    companion object {
        val EMPTY = ReplCodeState(
            C_CallFrameProto.EMPTY,
            C_BlockCodeProto.EMPTY,
            Rt_CallFrameState.EMPTY,
            Rt_GlobalConstants.State(),
        )
    }
}

internal class ReplCode(
    val frame: R_CallFrame,
    val stmts: ImmList<R_Statement>,
    private val state: ReplCodeState,
) {
    fun execute(exeCtx: Rt_ExecutionContext, rrReplCode: RR_ReplCode): ReplCodeState {
        val rtDefCtx = Rt_DefinitionContext(exeCtx, false, DefinitionId("", "<console>"))
        val rtFrame = Rt_CallFrame(rtDefCtx, rrReplCode.frame, state.frameState)
        (exeCtx.appCtx.interpreter as Rt_InterpreterImpl).executeStatements(rrReplCode.stmts, rtFrame)
        return ReplCodeState(
            frameProto = state.frameProto,
            blockCodeProto = state.blockCodeProto,
            frameState = rtFrame.dumpState(),
            globalConstants = exeCtx.appCtx.dumpGlobalConstants(),
        )
    }

    companion object {
        val ERROR = ReplCode(R_CallFrame.ERROR, immListOf(), ReplCodeState.EMPTY)
    }
}

internal class ReplCommandContext(
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

private fun C_ExtReplCommand.compile(appCtx: C_AppContext, codeState: ReplCodeState): C_LateGetter<ReplCode> =
    ExtReplCommandCompiler(this).compile(appCtx, codeState)

@JvmInline private value class ExtReplCommandCompiler(private val cmd: C_ExtReplCommand) {
    fun compile(appCtx: C_AppContext, codeState: ReplCodeState): C_LateGetter<ReplCode> {
        val extCompiler = C_ExtModuleCompiler(appCtx, cmd.extModules, cmd.preModules)
        extCompiler.compileModules()

        val curModName = cmd.currentModuleName
        if (curModName != null) {
            val md = extCompiler.modProvider.getModule(curModName, null)
            md ?: throw C_CommonError(C_Errors.msgModuleNotFound(curModName))
        }

        val mntCtx = createMountContext(appCtx, extCompiler.modProvider)
        cmd.extMembers.forEach { it.compile(mntCtx) }

        val replCtx = createReplContext(mntCtx, codeState)
        compileStatements(replCtx)

        return replCtx.commandGetter
    }

    private fun createMountContext(appCtx: C_AppContext, modProvider: C_ModuleProvider): C_MountContext {
        val currentModuleKey = cmd.currentModuleName?.let { C_ModuleKey(it, null) }
        val replNsAssembler = appCtx.createReplNsAssembler(currentModuleKey)
        val componentNsAssembler = replNsAssembler.addComponent()

        val modCtx = C_ReplModuleContext(
            appCtx,
            modProvider,
            cmd.currentModuleName ?: ModuleName.EMPTY,
            replNsAssembler.futureNs(),
            componentNsAssembler.futureNs(),
        )

        val symCtx = appCtx.symCtxProvider.getNopSymbolContext()
        val fileCtx = C_FileContext(modCtx, symCtx, C_SourcePath.EMPTY)

        appCtx.executor.onPass(C_CompilerPass.MODULES) {
            val fileFinish = fileCtx.finish()
            appCtx.addExtraMountTables(fileFinish.mountTables)
        }

        return S_RellFile.createMountContext(fileCtx, MountName.EMPTY, componentNsAssembler)
    }

    private fun createReplContext(mntCtx: C_MountContext, codeState: ReplCodeState): ReplCommandContext {
        val stmtVars = discoverStatementVars()
        val qName = C_StringQualifiedName.of(REPL_NAME)

        val cDefBase = mntCtx.defBaseCommon(
            C_DefinitionType.REPL,
            IdeSymbolKind.UNKNOWN,
            qName,
            mountName = null,
            extChain = null,
            commentProvider = C_SymbolContext.CommentProvider.NULL,
        )

        val defCtx = cDefBase.defCtx(mntCtx)
        val fnCtx = C_FunctionContext(defCtx, REPL_NAME, null, stmtVars)
        val frameCtx = C_FrameContext.create(fnCtx, codeState.frameProto)
        return ReplCommandContext(frameCtx, codeState)
    }

    private fun discoverStatementVars(): ImmTypedKeyMap {
        val map = MutableTypedKeyMap()
        for (stmt in cmd.statements) {
            stmt.discoverVars(map)
        }
        return map.toImmTypedKeyMap()
    }

    private fun compileStatements(ctx: ReplCommandContext) {
        val stmtCtx = C_StmtContext.createRoot(ctx.frameCtx.rootBlkCtx)

        ctx.executor.onPass(C_CompilerPass.EXPRESSIONS) {
            val builder = C_BlockCodeBuilder(
                stmtCtx,
                repl = true,
                hasGuardBlock = false,
                posRange = REPL_POS_RANGE,
                ctx.codeState.blockCodeProto,
            )

            for (stmt in cmd.statements) {
                builder.add(stmt)
            }

            val blockCode = builder.build()
            val replCode = createReplCode(ctx, blockCode)
            ctx.setCommand(replCode)
        }
    }

    private fun createReplCode(ctx: ReplCommandContext, blockCode: C_BlockCode): ReplCode {
        val callFrame = ctx.frameCtx.makeCallFrame(false)
        val newState = ReplCodeState(
            frameProto = callFrame.proto,
            blockCodeProto = blockCode.createProto(),
            frameState = ctx.codeState.frameState,
            globalConstants = ctx.codeState.globalConstants,
        )
        return ReplCode(callFrame.rFrame, blockCode.rStmts, newState)
    }
}

internal object ReplCompiler {
    fun compile(
        sourceDir: C_SourceDir,
        currentModuleName: ModuleName?,
        code: String,
        globalCtx: C_GlobalContext,
        oldDefsState: C_ReplDefsState,
        oldCodeState: ReplCodeState,
    ): ReplResult {
        C_LibBridge.ensureInitialized()
        val msgCtx = C_MessageContext.create(globalCtx)
        val symCtxProvider = C_SymbolContextManager(msgCtx, globalCtx.compilerOptions).provider
        val controller = C_CompilerController(msgCtx)

        val extCommand = msgCtx.consumeError {
            val ast = C_Parser.parseRepl(code)
            ast.compile(
                msgCtx,
                symCtxProvider,
                controller,
                sourceDir,
                currentModuleName,
                oldDefsState.appState,
            )
        }

        return if (extCommand == null) {
            ReplResult(null, msgCtx.messages())
        } else {
            compileExt(msgCtx, symCtxProvider, controller, extCommand, oldDefsState, oldCodeState)
        }
    }

    private fun compileExt(
        msgCtx: C_MessageContext,
        symCtxProvider: C_SymbolContextProvider,
        controller: C_CompilerController,
        extCommand: C_ExtReplCommand,
        oldDefsState: C_ReplDefsState,
        oldCodeState: ReplCodeState,
    ): ReplResult {
        val appCtx = C_AppContext(
            msgCtx,
            symCtxProvider,
            controller,
            true,
            oldDefsState.appState,
            extCommand.newModuleHeaders,
            extraLibMod = null,
        )

        val codeGetter = msgCtx.consumeError {
            extCommand.compile(appCtx, oldCodeState)
        }

        controller.run()

        val appFinish = appCtx.finish()
        val messages = CommonUtils.sortedByCopy(msgCtx.messages()) { C_ComparablePos(it.pos) }
        val errors = messages.filter { it.type == C_MessageType.ERROR }

        val success = if (appFinish == null || codeGetter == null || errors.isNotEmpty()) null else {
            val cCode = codeGetter.get()
            val newState = C_ReplDefsState(appFinish.replState)
            val resolverRuntime = Rt_ResolverRuntime()
            val (rrApp, rrReplCode) = resolveWithReplStatements(
                appFinish.rApp, resolverRuntime, cCode.stmts, cCode.frame,
            )
            ReplSuccess(rrApp, rrReplCode, resolverRuntime.collectedSysFns(), newState, cCode)
        }

        return ReplResult(success, messages)
    }
}

internal class ReplSuccess(
    val app: RR_App,
    val replCode: RR_ReplCode,
    val sysFns: Map<String, Any>,
    val defsState: C_ReplDefsState,
    val code: ReplCode,
)

internal class ReplResult(val success: ReplSuccess?, messages: ImmList<C_Message>): C_AbstractResult(messages)

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

class ReplInterpreter private constructor(private val config: ReplInterpreterConfig) {
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
            val sqlCtx = Rt_RegularSqlContext.createNoExternalChains(success.app, Rt_ChainSqlMapping(0))
            val rtAppCtx = createRtAppContext(config.rtGlobalCtx, success.app, success.sysFns)
            sqlUpdate(rtAppCtx, sqlCtx, forceSqlUpdate)

            sqlMgr.access { sqlExec ->
                val exeCtx = Rt_ExecutionContext(rtAppCtx, Rt_NullOpContext, sqlCtx, sqlExec, dbReadOnly = false, exeState)
                codeState = success.code.execute(exeCtx, success.replCode)
                defsState = success.defsState
                exeState = exeCtx.toState()
            }
        }
    }

    private fun compile(code: String): ReplSuccess? {
        val cRes = try {
            ReplCompiler.compile(config.sourceDir, config.module, code, cGlobalCtx, defsState, codeState)
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
            globalConstantsState = codeState.globalConstants,
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
