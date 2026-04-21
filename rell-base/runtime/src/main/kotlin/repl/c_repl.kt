/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.repl

import net.postchain.rell.base.compiler.ast.S_PosRange
import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.base.core.C_ExtReplCommand
import net.postchain.rell.base.compiler.base.expr.C_StmtContext
import net.postchain.rell.base.compiler.base.module.C_ExtModuleCompiler
import net.postchain.rell.base.compiler.base.module.C_ModuleKey
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.MountName
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.repl.*
import net.postchain.rell.base.utils.CommonUtils
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.ImmTypedKeyMap
import net.postchain.rell.base.utils.MutableTypedKeyMap
import net.postchain.rell.base.utils.ide.IdeSymbolKind

private const val REPL_NAME = "<REPL>"

private val REPL_POS_RANGE = S_PosRange(C_Parser.REPL_NULL_POS, C_Parser.REPL_NULL_POS)

fun C_ExtReplCommand.compile(appCtx: net.postchain.rell.base.compiler.base.core.C_AppContext, codeState: ReplCodeState): C_LateGetter<ReplCode> {
    return C_ExtReplCommandCompiler(this).compile(appCtx, codeState)
}

private class C_ExtReplCommandCompiler(private val cmd: C_ExtReplCommand) {
    fun compile(appCtx: net.postchain.rell.base.compiler.base.core.C_AppContext, codeState: ReplCodeState): C_LateGetter<ReplCode> {
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

    private fun createMountContext(appCtx: net.postchain.rell.base.compiler.base.core.C_AppContext, modProvider: net.postchain.rell.base.compiler.base.core.C_ModuleProvider): net.postchain.rell.base.compiler.base.core.C_MountContext {
        val currentModuleKey = cmd.currentModuleName?.let { C_ModuleKey(it, null) }
        val replNsAssembler = appCtx.createReplNsAssembler(currentModuleKey)
        val componentNsAssembler = replNsAssembler.addComponent()

        val modCtx = _root_ide_package_.net.postchain.rell.base.compiler.base.core.C_ReplModuleContext(
            appCtx,
            modProvider,
            cmd.currentModuleName ?: ModuleName.EMPTY,
            replNsAssembler.futureNs(),
            componentNsAssembler.futureNs(),
        )

        val symCtx = appCtx.symCtxProvider.getNopSymbolContext()
        val fileCtx = _root_ide_package_.net.postchain.rell.base.compiler.base.core.C_FileContext(
            modCtx,
            symCtx,
            C_SourcePath.EMPTY
        )

        appCtx.executor.onPass(net.postchain.rell.base.compiler.base.core.C_CompilerPass.MODULES) {
            val fileFinish = fileCtx.finish()
            appCtx.addExtraMountTables(fileFinish.mountTables)
        }

        return S_RellFile.createMountContext(fileCtx, MountName.EMPTY, componentNsAssembler)
    }

    private fun createReplContext(mntCtx: net.postchain.rell.base.compiler.base.core.C_MountContext, codeState: ReplCodeState): C_ReplCommandContext {
        val stmtVars = discoverStatementVars()
        val qName = C_StringQualifiedName.of(REPL_NAME)

        val cDefBase = mntCtx.defBaseCommon(
            net.postchain.rell.base.compiler.base.core.C_DefinitionType.REPL,
            IdeSymbolKind.UNKNOWN,
            qName,
            mountName = null,
            extChain = null,
            commentProvider = net.postchain.rell.base.compiler.base.core.C_SymbolContext.CommentProvider.NULL,
        )

        val defCtx = cDefBase.defCtx(mntCtx)
        val fnCtx = _root_ide_package_.net.postchain.rell.base.compiler.base.core.C_FunctionContext(
            defCtx,
            REPL_NAME,
            null,
            stmtVars
        )
        val frameCtx = net.postchain.rell.base.compiler.base.core.C_FrameContext.create(fnCtx, codeState.cState.frameProto)
        return C_ReplCommandContext(frameCtx, codeState)
    }

    private fun discoverStatementVars(): ImmTypedKeyMap {
        val map = MutableTypedKeyMap()
        for (stmt in cmd.statements) {
            stmt.discoverVars(map)
        }
        return map.toImmTypedKeyMap()
    }

    private fun compileStatements(ctx: C_ReplCommandContext) {
        val stmtCtx = C_StmtContext.createRoot(ctx.frameCtx.rootBlkCtx)

        ctx.executor.onPass(net.postchain.rell.base.compiler.base.core.C_CompilerPass.EXPRESSIONS) {
            val builder = _root_ide_package_.net.postchain.rell.base.compiler.base.core.C_BlockCodeBuilder(
                stmtCtx,
                repl = true,
                hasGuardBlock = false,
                posRange = REPL_POS_RANGE,
                ctx.codeState.cState.blockCodeProto,
            )

            for (stmt in cmd.statements) {
                builder.add(stmt)
            }

            val blockCode = builder.build()
            val replCode = createReplCode(ctx, blockCode)
            ctx.setCommand(replCode)
        }
    }

    private fun createReplCode(ctx: C_ReplCommandContext, blockCode: net.postchain.rell.base.compiler.base.core.C_BlockCode): ReplCode {
        val callFrame = ctx.frameCtx.makeCallFrame(false)
        val rCommand = R_ReplCode(callFrame.rFrame, blockCode.rStmts)
        val blockCodeProto = blockCode.createProto()
        val cState = C_ReplCodeState(callFrame.proto, blockCodeProto)
        return ReplCode(rCommand, cState, ctx.codeState.rtState)
    }
}

object C_ReplCompiler {
    fun compile(
        sourceDir: C_SourceDir,
        currentModuleName: ModuleName?,
        code: String,
        globalCtx: net.postchain.rell.base.compiler.base.core.C_GlobalContext,
        oldDefsState: net.postchain.rell.base.compiler.base.core.C_ReplDefsState,
        oldCodeState: ReplCodeState,
    ): C_ReplResult {
        net.postchain.rell.base.compiler.base.core.C_LibBridge.ensureInitialized()
        val msgCtx = net.postchain.rell.base.compiler.base.core.C_MessageContext.create(globalCtx)
        val symCtxProvider = _root_ide_package_.net.postchain.rell.base.compiler.base.core.C_SymbolContextManager(
            msgCtx,
            globalCtx.compilerOptions
        ).provider
        val controller = _root_ide_package_.net.postchain.rell.base.compiler.base.core.C_CompilerController(msgCtx)

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
            C_ReplResult(null, msgCtx.messages())
        } else {
            compileExt(msgCtx, symCtxProvider, controller, extCommand, oldDefsState, oldCodeState)
        }
    }

    private fun compileExt(
        msgCtx: net.postchain.rell.base.compiler.base.core.C_MessageContext,
        symCtxProvider: net.postchain.rell.base.compiler.base.core.C_SymbolContextProvider,
        controller: net.postchain.rell.base.compiler.base.core.C_CompilerController,
        extCommand: C_ExtReplCommand,
        oldDefsState: net.postchain.rell.base.compiler.base.core.C_ReplDefsState,
        oldCodeState: ReplCodeState,
    ): C_ReplResult {
        val appCtx = _root_ide_package_.net.postchain.rell.base.compiler.base.core.C_AppContext(
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
        val messages = CommonUtils.sortedByCopy(msgCtx.messages()) {
            _root_ide_package_.net.postchain.rell.base.compiler.base.core.C_ComparablePos(
                it.pos
            )
        }
        val errors = messages.filter { it.type == C_MessageType.ERROR }

        val success = if (appFinish == null || codeGetter == null || errors.isNotEmpty()) null else {
            val cCode = codeGetter.get()
            val newState =
                _root_ide_package_.net.postchain.rell.base.compiler.base.core.C_ReplDefsState(appFinish.replState)
            C_ReplSuccess(appFinish.rApp, newState, cCode)
        }

        return C_ReplResult(success, messages)
    }
}

// C_ReplAppState and C_ReplDefsState are in compiler/base/core/c_repl_types.kt (frontend module).

class C_ReplSuccess(val app: R_App, val defsState: net.postchain.rell.base.compiler.base.core.C_ReplDefsState, val code: ReplCode)
class C_ReplResult(val success: C_ReplSuccess?, messages: ImmList<C_Message>): net.postchain.rell.base.compiler.base.core.C_AbstractResult(messages)
