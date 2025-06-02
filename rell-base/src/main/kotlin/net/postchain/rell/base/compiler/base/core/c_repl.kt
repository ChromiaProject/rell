/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.core

import net.postchain.rell.base.compiler.ast.S_PosRange
import net.postchain.rell.base.compiler.ast.S_RellFile
import net.postchain.rell.base.compiler.ast.S_Statement
import net.postchain.rell.base.compiler.base.def.C_FunctionExtensionsTable
import net.postchain.rell.base.compiler.base.def.C_MountTables
import net.postchain.rell.base.compiler.base.expr.C_StmtContext
import net.postchain.rell.base.compiler.base.module.*
import net.postchain.rell.base.compiler.base.namespace.C_NsAsm_ReplState
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.model.*
import net.postchain.rell.base.repl.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.ide.IdeSymbolKind

private const val REPL_NAME = "<REPL>"

private val REPL_POS_RANGE = S_PosRange(C_Parser.REPL_NULL_POS, C_Parser.REPL_NULL_POS)

class C_ExtReplCommand(
    private val extModules: ImmList<C_ExtModule>,
    private val extMembers: ImmList<C_ExtModuleMember>,
    private val currentModuleName: R_ModuleName?,
    private val statements: ImmList<S_Statement>,
    private val preModules: ImmMap<C_ModuleKey, C_PrecompiledModule>,
    val newModuleHeaders: ImmMap<R_ModuleName, C_ModuleHeader>,
) {
    fun compile(appCtx: C_AppContext, codeState: ReplCodeState): C_LateGetter<ReplCode> {
        val extCompiler = C_ExtModuleCompiler(appCtx, extModules, preModules)
        extCompiler.compileModules()

        if (currentModuleName != null) {
            val md = extCompiler.modProvider.getModule(currentModuleName, null)
            md ?: throw C_CommonError(C_Errors.msgModuleNotFound(currentModuleName))
        }

        val mntCtx = createMountContext(appCtx, extCompiler.modProvider)
        extMembers.forEach { it.compile(mntCtx) }

        val replCtx = createReplContext(mntCtx, codeState)
        compileStatements(replCtx)

        return replCtx.commandGetter
    }

    private fun createMountContext(appCtx: C_AppContext, modProvider: C_ModuleProvider): C_MountContext {
        val currentModuleKey = currentModuleName?.let { C_ModuleKey(it, null) }
        val replNsAssembler = appCtx.createReplNsAssembler(currentModuleKey)
        val componentNsAssembler = replNsAssembler.addComponent()

        val modCtx = C_ReplModuleContext(
            appCtx,
            modProvider,
            currentModuleName ?: R_ModuleName.EMPTY,
            replNsAssembler.futureNs(),
            componentNsAssembler.futureNs(),
        )

        val symCtx = appCtx.symCtxProvider.getNopSymbolContext()
        val fileCtx = C_FileContext(modCtx, symCtx, C_SourcePath.EMPTY)

        appCtx.executor.onPass(C_CompilerPass.MODULES) {
            val fileFinish = fileCtx.finish()
            appCtx.addExtraMountTables(fileFinish.mountTables)
        }

        return S_RellFile.createMountContext(fileCtx, R_MountName.EMPTY, componentNsAssembler)
    }

    private fun createReplContext(mntCtx: C_MountContext, codeState: ReplCodeState): C_ReplCommandContext {
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
        val frameCtx = C_FrameContext.create(fnCtx, codeState.cState.frameProto)
        return C_ReplCommandContext(frameCtx, codeState)
    }

    private fun discoverStatementVars(): ImmTypedKeyMap {
        val map = MutableTypedKeyMap()
        for (stmt in statements) {
            stmt.discoverVars(map)
        }
        return map.toImmTypedKeyMap()
    }

    private fun compileStatements(ctx: C_ReplCommandContext) {
        val stmtCtx = C_StmtContext.createRoot(ctx.frameCtx.rootBlkCtx)

        ctx.executor.onPass(C_CompilerPass.EXPRESSIONS) {
            val builder = C_BlockCodeBuilder(
                stmtCtx,
                repl = true,
                hasGuardBlock = false,
                posRange = REPL_POS_RANGE,
                ctx.codeState.cState.blockCodeProto,
            )

            for (stmt in statements) {
                builder.add(stmt)
            }

            val blockCode = builder.build()
            val replCode = createReplCode(ctx, blockCode)
            ctx.setCommand(replCode)
        }
    }

    private fun createReplCode(ctx: C_ReplCommandContext, blockCode: C_BlockCode): ReplCode {
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
        currentModuleName: R_ModuleName?,
        code: String,
        globalCtx: C_GlobalContext,
        oldDefsState: C_ReplDefsState,
        oldCodeState: ReplCodeState,
    ): C_ReplResult {
        val msgCtx = C_MessageContext.create(globalCtx)
        val symCtxProvider = C_SymbolContextManager(msgCtx, globalCtx.compilerOptions).provider
        val controller = C_CompilerController(msgCtx)

        val res = C_LateInit.context(controller.executor) {
            val extCommand = msgCtx.consumeError {
                val ast = C_Parser.parseRepl(code)
                ast.compile(
                    msgCtx,
                    symCtxProvider,
                    controller.executor,
                    sourceDir,
                    currentModuleName,
                    oldDefsState.appState,
                )
            }

            if (extCommand == null) {
                C_ReplResult(null, msgCtx.messages())
            } else {
                compileExt(msgCtx, symCtxProvider, controller, extCommand, oldDefsState, oldCodeState)
            }
        }

        return res
    }

    private fun compileExt(
        msgCtx: C_MessageContext,
        symCtxProvider: C_SymbolContextProvider,
        controller: C_CompilerController,
        extCommand: C_ExtReplCommand,
        oldDefsState: C_ReplDefsState,
        oldCodeState: ReplCodeState,
    ): C_ReplResult {
        val executor = controller.executor

        val appCtx = C_AppContext(
            msgCtx,
            symCtxProvider,
            executor,
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
            C_ReplSuccess(appFinish.rApp, newState, cCode)
        }

        return C_ReplResult(success, messages)
    }
}

class C_ReplAppState(
    val nsAsmState: C_NsAsm_ReplState,
    val moduleHeaders: ImmMap<R_ModuleName, C_ModuleHeader>,
    val modules: ImmMap<C_ModuleKey, C_PrecompiledModule>,
    val sysDefs: C_SystemDefs?,
    val sqlDefs: R_AppSqlDefs,
    val mntTables: C_MountTables,
    val constants: ImmList<R_GlobalConstantDefinition>,
    val moduleArgs: ImmMap<R_ModuleName, R_StructDefinition>,
    val functionExtensions: C_FunctionExtensionsTable,
) {
    companion object {
        val EMPTY = C_ReplAppState(
            C_NsAsm_ReplState.EMPTY,
            immMapOf(),
            immMapOf(),
            null,
            R_AppSqlDefs.EMPTY,
            C_MountTables.EMPTY,
            immListOf(),
            immMapOf(),
            C_FunctionExtensionsTable(immListOf()),
        )
    }
}

class C_ReplDefsState(val appState: C_ReplAppState) {
    companion object {
        val EMPTY = C_ReplDefsState(C_ReplAppState.EMPTY)
    }
}

class C_ReplSuccess(val app: R_App, val defsState: C_ReplDefsState, val code: ReplCode)
class C_ReplResult(val success: C_ReplSuccess?, messages: ImmList<C_Message>): C_AbstractResult(messages)
