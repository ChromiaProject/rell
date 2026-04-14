/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.core

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.def.C_MountTables
import net.postchain.rell.base.compiler.base.def.C_MountTablesBuilder
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.compiler.base.module.*
import net.postchain.rell.base.compiler.base.namespace.C_LibNsMemberFactory
import net.postchain.rell.base.compiler.base.namespace.C_Namespace
import net.postchain.rell.base.compiler.base.namespace.C_SysNsProto
import net.postchain.rell.base.compiler.base.namespace.C_SysNsProtoBuilder
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.lib.C_SystemLibrary
import net.postchain.rell.base.lib.Lib_SysQueries
import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.ide.IdeCompletion
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.base.utils.ide.IdeSymbolKind

class C_Entity(val defPos: S_Pos?, val entity: R_EntityDefinition)

enum class C_CompilerPass {
    DEFINITIONS,
    NAMESPACES,
    MODULES,
    MEMBERS,
    ABSTRACT,
    APPDEFS,
    EXPRESSIONS,
    FRAMES,
    DOCS,
    COMPLETIONS,
    VALIDATION,
    APPLICATION,
    FINISH
    ;

    fun prev(): C_CompilerPass = entries[ordinal - 1]
    fun next(): C_CompilerPass = entries[ordinal + 1]
}

class C_StatementVars(val declared: ImmSet<R_Name>, val modified: ImmSet<R_Name>) {
    companion object {
        val EMPTY = C_StatementVars(immSetOf(), immSetOf())
    }
}

class C_StatementVarsBlock {
    private val declared = mutableSetOf<R_Name>()
    private val modified = mutableSetOf<R_Name>()

    fun declared(names: Set<R_Name>) {
        declared.addAll(names)
    }

    fun modified(names: Set<R_Name>) {
        for (name in names) {
            if (name !in declared) {
                modified.add(name)
            }
        }
    }

    fun modified() = modified.toImmSet()
}

internal class C_SystemDefsScope(
    val ns: C_Namespace,
    val nsProto: C_SysNsProto,
    val modules: ImmList<C_LibModule>,
)

internal class C_SystemDefsCommon(
    val blockEntity: R_EntityDefinition,
    val transactionEntity: R_EntityDefinition,
    val mntTables: C_MountTables,
    val entities: ImmList<R_EntityDefinition>,
    val queries: ImmList<R_QueryDefinition>,
)

internal class C_SystemDefs private constructor(
    val common: C_SystemDefsCommon,
    val appScope: C_SystemDefsScope,
    val testScope: C_SystemDefsScope,
) {
    companion object {
        internal fun create(appCtx: C_AppContext, stamp: R_AppUid, extraMod: C_LibModule?): C_SystemDefs {
            val blockEntity = C_Utils.createBlockEntity(appCtx, null)
            val transactionEntity = C_Utils.createTransactionEntity(appCtx, null, blockEntity)
            val queries = Lib_SysQueries.createQueries(appCtx.executor)
            return create(appCtx.globalCtx, stamp, blockEntity, transactionEntity, queries, extraMod)
        }

        internal fun create(
            globalCtx: C_GlobalContext,
            stamp: R_AppUid,
            blockEntity: R_EntityDefinition,
            transactionEntity: R_EntityDefinition,
            queries: ImmList<R_QueryDefinition>,
            extraMod: C_LibModule?,
        ): C_SystemDefs {
            val sysEntities = immListOf(blockEntity, transactionEntity)

            val appScope = createNsScope(globalCtx, sysEntities, extraMod, false)
            val testScope = createNsScope(globalCtx, sysEntities, extraMod, true)

            val mntBuilder = C_MountTablesBuilder(stamp)
            for (entity in sysEntities) mntBuilder.addSysEntity(entity)
            for (query in queries) mntBuilder.addQuery(query)
            val mntTables = mntBuilder.build()

            val common = C_SystemDefsCommon(blockEntity, transactionEntity, mntTables, sysEntities, queries)
            return C_SystemDefs(common, appScope, testScope)
        }

        private fun createNsScope(
            globalCtx: C_GlobalContext,
            sysEntities: List<R_EntityDefinition>,
            extraMod: C_LibModule?,
            test: Boolean,
        ): C_SystemDefsScope {
            val opts = globalCtx.compilerOptions
            val libConfig = C_SystemLibrary.Config(
                defaultLib = opts.defaultLib,
                testLib = test,
                hiddenLib = opts.hiddenLib,
                extraMod,
            )
            val libScope = C_SystemLibrary.getScope(libConfig)

            val memberFactory = C_LibNsMemberFactory(C_RFullNamePath.of(R_ModuleName.EMPTY))
            val nsBuilder = C_SysNsProtoBuilder()
            nsBuilder.addAll(libScope.nsProto)

            if (opts.defaultLib) {
                for (entity in sysEntities) {
                    val ideInfo = C_IdeSymbolInfo.direct(IdeSymbolKind.DEF_ENTITY, doc = entity.docSymbol)
                    val member = memberFactory.sysEntity(entity.rName, entity, ideInfo)
                    nsBuilder.addMember(entity.rName, member)
                }
            }

            val nsProto = nsBuilder.build()
            val ns = nsProto.toNamespace()
            return C_SystemDefsScope(ns, nsProto, libScope.modules)
        }
    }
}

class C_CompilerModuleSelection(
    val appModules: ImmList<R_ModuleName>?,
    val testModules: ImmList<R_ModuleName> = immListOf(),
    val testSubModules: Boolean = true,
    val appSubModules: Boolean = false,
)

object C_Compiler {
    fun compile(
        sourceDir: C_SourceDir,
        modules: List<R_ModuleName>,
        options: C_CompilerOptions = C_CompilerOptions.DEFAULT,
    ): C_CompilationResult {
        val modSel = C_CompilerModuleSelection(modules.toImmList(), immListOf())
        return compile(sourceDir, modSel, options)
    }

    fun compile(
        sourceDir: C_SourceDir,
        moduleSelection: C_CompilerModuleSelection,
        options: C_CompilerOptions,
    ): C_CompilationResult {
        return compileInternal(sourceDir, moduleSelection, options, extraLibMod = null)
    }

    internal fun compileInternal(
        sourceDir: C_SourceDir,
        moduleSelection: C_CompilerModuleSelection,
        options: C_CompilerOptions,
        extraLibMod: C_LibModule?,
    ): C_CompilationResult {
        val globalCtx = C_GlobalContext(options, sourceDir)
        val msgCtx = C_MessageContext.create(globalCtx)
        val controller = C_CompilerController(msgCtx)

        return compile0(sourceDir, msgCtx, controller, moduleSelection, extraLibMod)
    }

    private fun compile0(
        sourceDir: C_SourceDir,
        msgCtx: C_MessageContext,
        controller: C_CompilerController,
        moduleSelection: C_CompilerModuleSelection,
        extraLibMod: C_LibModule?,
    ): C_CompilationResult {
        val symCtxManager = C_SymbolContextManager(msgCtx, msgCtx.globalCtx.compilerOptions)
        val symCtxProvider = symCtxManager.provider

        val midModules = msgCtx.consumeError {
            val midModules = loadMidModules(msgCtx, controller, sourceDir, moduleSelection, symCtxProvider)
            checkMainModules(msgCtx, moduleSelection, midModules)
            midModules
        }.orEmpty()

        val extModules = msgCtx.consumeError {
            compileMidModules(msgCtx, symCtxProvider, midModules)
        }.orEmpty()

        val moduleHeaders = midModules.associateToImmMap { it.moduleName to it.compiledHeader }

        val appCtx = C_AppContext(
            msgCtx,
            symCtxProvider,
            controller,
            false,
            C_ReplAppState.EMPTY,
            moduleHeaders,
            extraLibMod,
        )

        val modIdeCompletions = msgCtx.consumeError {
            val extCompiler = C_ExtModuleCompiler(appCtx, extModules, immMapOf())
            extCompiler.compileModules()
        } ?: C_LateGetter.const(immMultimapOf())

        val files = extModules.flatMapToImmList { it.midModule.filePaths() }

        controller.run()

        val symFinish = symCtxManager.finish()
        val appFinish = appCtx.finish()

        val messages = CommonUtils.sortedByCopy(msgCtx.messages()) { C_ComparablePos(it.pos) }
        val errors = messages.filter { it.type == C_MessageType.ERROR }

        val ideCompletions = modIdeCompletions.get().asMap().mapValues { it.value.distinct() }.toImmMultimap()

        val rApp = if (errors.isEmpty()) appFinish?.rApp else null
        return C_CompilationResult(rApp, messages, files, symFinish.symbolInfos, ideCompletions)
    }

    private fun compileMidModules(
        msgCtx: C_MessageContext,
        symCtxProvider: C_SymbolContextProvider,
        midModules: List<C_MidModule>,
    ): ImmList<C_ExtModule> {
        val selModules = midModules.filter { it.isSelected }

        val midCompiler = C_MidModuleCompiler(msgCtx, symCtxProvider, midModules)
        for (selModule in selModules) {
            midCompiler.compileModule(selModule.moduleName, null)
        }

        return midCompiler.getExtModules()
    }

    private fun loadMidModules(
        msgCtx: C_MessageContext,
        executor: C_CompilerExecutor,
        sourceDir: C_SourceDir,
        modSel: C_CompilerModuleSelection,
        symCtxProvider: C_SymbolContextProvider,
    ): ImmList<C_MidModule> {
        val modLdr = C_ModuleLoader(msgCtx, symCtxProvider, executor, sourceDir, immMapOf())

        if (modSel.appModules == null) {
            modLdr.loadAllModules()
        } else {
            for (moduleName in modSel.appModules) {
                modLdr.loadModule(moduleName, modSel.appSubModules)
            }
        }

        for (moduleName in modSel.testModules) {
            modLdr.loadTestModule(moduleName, modSel.testSubModules)
        }

        return modLdr.finish()
    }

    private fun checkMainModules(
            msgCtx: C_MessageContext,
            modSel: C_CompilerModuleSelection,
            midModules: List<C_MidModule>
    ) {
        val options = msgCtx.globalCtx.compilerOptions

        val midModulesMap = midModules.associateBy { it.moduleName }

        for (moduleName in modSel.appModules.orEmpty()) {
            val midModule = midModulesMap[moduleName]
            midModule ?: throw C_CommonError(C_Errors.msgModuleNotFound(moduleName))

            val absPos = midModule.header?.abstract
            if (absPos != null && !options.ide) {
                msgCtx.error(absPos, "module:main_abstract:$moduleName",
                        "Module '${moduleName.str()}' is abstract, cannot be used as a main module")
            }

            if (midModule.isTest && !options.ide && options.appModuleInTestsError) {
                throw C_CommonError(C_CodeMsg("module:main_test:$moduleName", "Module '$moduleName' is a test module"))
            }
        }

        if (modSel.testSubModules) {
            val parentsOfTestModules = mutableSetOf<R_ModuleName>()
            for (midModule in midModules) {
                if (midModule.isTest) {
                    val path = CommonUtils.chainToList(midModule.moduleName) { it.parentOrNull() }
                    parentsOfTestModules.addAll(path)
                }
            }
            for (moduleName in modSel.testModules) {
                if (moduleName !in parentsOfTestModules) {
                    if (moduleName in midModulesMap) {
                        if (options.appModuleInTestsError) {
                            throw C_CommonError(msgModuleNotTest(moduleName))
                        }
                    } else {
                        throw C_CommonError(C_Errors.msgModuleNotFound(moduleName))
                    }
                }
            }
        } else {
            for (moduleName in modSel.testModules) {
                val midModule = midModulesMap[moduleName]
                midModule ?: throw C_CommonError(C_Errors.msgModuleNotFound(moduleName))
                if (!midModule.isTest && options.appModuleInTestsError) {
                    throw C_CommonError(msgModuleNotTest(moduleName))
                }
            }
        }
    }

    private fun msgModuleNotTest(moduleName: R_ModuleName): C_CodeMsg {
        return "module:not_test:$moduleName" toCodeMsg "Module '$moduleName' is not a test module"
    }
}

class C_ComparablePos(sPos: S_Pos): Comparable<C_ComparablePos> {
    private val path: C_SourcePath = sPos.path()
    private val line = sPos.line()
    private val column = sPos.column()

    override fun compareTo(other: C_ComparablePos): Int {
        var d = path.compareTo(other.path)
        if (d == 0) d = line.compareTo(other.line)
        if (d == 0) d = column.compareTo(other.column)
        return d
    }
}

abstract class C_AbstractResult(val messages: ImmList<C_Message>) {
    val warnings = this.messages.filterToImmList { it.type == C_MessageType.WARNING }
    val errors = this.messages.filterToImmList { it.type == C_MessageType.ERROR }
}

class C_CompilationResult(
    val app: R_App?,
    messages: ImmList<C_Message>,
    val files: ImmList<C_SourcePath>,
    val ideSymbolInfos: ImmMap<S_Pos, IdeSymbolInfo>,
    val ideCompletions: ImmMultimap<String, IdeCompletion>,
): C_AbstractResult(messages)

abstract class C_CompilerExecutor {
    abstract fun checkPass(minPass: C_CompilerPass?, maxPass: C_CompilerPass?)
    fun checkPass(pass: C_CompilerPass) = checkPass(pass, pass)

    abstract fun onPass(pass: C_CompilerPass, code: () -> Unit)
    abstract fun onClose(code: () -> Unit)

    companion object {
        fun checkPass(currentPass: C_CompilerPass, minPass: C_CompilerPass?, maxPass: C_CompilerPass?) {
            if (minPass != null) {
                check(currentPass >= minPass) { "Expected pass >= $minPass, actual $currentPass" }
            }
            if (maxPass != null) {
                check(currentPass <= maxPass) { "Expected pass <= $maxPass, actual $currentPass" }
            }
        }
    }
}

class C_CompilerController(private val msgCtx: C_MessageContext): C_CompilerExecutor() {
    private val passes = C_CompilerPass.entries.associateWith { ArrayDeque<() -> Unit>() }
    private val closeCallbacks = mutableListOf<() -> Unit>()
    private var currentPass = C_CompilerPass.entries[0]

    private var runCalled = false

    fun run() {
        check(!runCalled)
        runCalled = true

        for (pass in C_CompilerPass.entries) {
            currentPass = pass
            val queue = passes.getValue(pass)

            while (!queue.isEmpty()) {
                val task = queue.removeFirst()
                task()
            }
        }

        for (code in closeCallbacks) {
            code()
        }

        closeCallbacks.clear()
    }

    override fun checkPass(minPass: C_CompilerPass?, maxPass: C_CompilerPass?) {
        checkPass(currentPass, minPass, maxPass)
    }

    override fun onPass(pass: C_CompilerPass, code: () -> Unit) {
        check(currentPass < pass) { "currentPass: $currentPass targetPass: $pass" }

        val nextPass = currentPass.next()

        if (pass == currentPass || pass == nextPass) {
            passes.getValue(pass).add { msgCtx.consumeError(code) }
        } else {
            // Extra code is needed to maintain execution order:
            // - entity 0 adds code to pass A, that code adds code to pass B
            // - entity 1 adds code to pass B directly
            // -> on pass B entity 0 must be executed before entity 1
            passes.getValue(nextPass).add { msgCtx.consumeError { onPass(pass, code) } }
        }
    }

    override fun onClose(code: () -> Unit) {
        closeCallbacks += code
    }
}
