/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.core

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.def.C_MountTables
import net.postchain.rell.base.compiler.base.def.C_MountTablesBuilder
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.compiler.base.module.*
import net.postchain.rell.base.compiler.base.namespace.C_Namespace
import net.postchain.rell.base.compiler.base.namespace.C_LibNsMemberFactory
import net.postchain.rell.base.compiler.base.namespace.C_SysNsProto
import net.postchain.rell.base.compiler.base.namespace.C_SysNsProtoBuilder
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.lib.C_SystemLibrary
import net.postchain.rell.base.lib.Lib_SysQueries
import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.*
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
    VALIDATION,
    APPLICATION,
    FINISH
    ;

    fun prev(): C_CompilerPass {
        return VALUES[ordinal - 1]
    }

    fun next(): C_CompilerPass {
        return VALUES[ordinal + 1]
    }

    companion object {
        private val VALUES = values()

        val LAST = VALUES[VALUES.size - 1]
    }
}

class C_StatementVars(declared: Set<R_Name>, modified: Set<R_Name>) {
    val declared = declared.toImmSet()
    val modified = modified.toImmSet()

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

    fun modified() = modified.toSet()
}

class C_SystemDefsScope(
    val ns: C_Namespace,
    val nsProto: C_SysNsProto,
    val modules: List<C_LibModule>,
)

class C_SystemDefsCommon(
    val blockEntity: R_EntityDefinition,
    val transactionEntity: R_EntityDefinition,
    val mntTables: C_MountTables,
    entities: List<R_EntityDefinition>,
    queries: List<R_QueryDefinition>,
) {
    val entities = entities.toImmList()
    val queries = queries.toImmList()
}

class C_SystemDefs private constructor(
    val common: C_SystemDefsCommon,
    val appScope: C_SystemDefsScope,
    val testScope: C_SystemDefsScope,
) {
    companion object {
        fun create(appCtx: C_AppContext, stamp: R_AppUid, extraMod: C_LibModule?): C_SystemDefs {
            val blockEntity = C_Utils.createBlockEntity(appCtx, null)
            val transactionEntity = C_Utils.createTransactionEntity(appCtx, null, blockEntity)
            val queries = Lib_SysQueries.createQueries(appCtx.executor)
            return create(appCtx.globalCtx, stamp, blockEntity, transactionEntity, queries, extraMod)
        }

        fun create(
            globalCtx: C_GlobalContext,
            stamp: R_AppUid,
            blockEntity: R_EntityDefinition,
            transactionEntity: R_EntityDefinition,
            queries: List<R_QueryDefinition>,
            extraMod: C_LibModule?,
        ): C_SystemDefs {
            val sysEntities = listOf(blockEntity, transactionEntity)

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
            val libScope = C_SystemLibrary.getScope(test, globalCtx.compilerOptions.hiddenLib, extraMod)

            val memberFactory = C_LibNsMemberFactory(C_RFullNamePath.of(R_ModuleName.EMPTY))
            val nsBuilder = C_SysNsProtoBuilder()
            nsBuilder.addAll(libScope.nsProto)

            for (entity in sysEntities) {
                val ideInfo = C_IdeSymbolInfo.direct(IdeSymbolKind.DEF_ENTITY, doc = entity.docSymbol)
                val member = memberFactory.sysEntity(entity.rName, entity, ideInfo)
                nsBuilder.addMember(entity.rName, member)
            }

            val nsProto = nsBuilder.build()
            val ns = nsProto.toNamespace()
            return C_SystemDefsScope(ns, nsProto, libScope.modules)
        }
    }
}

enum class C_AtAttrShadowing {
    FULL,
    PARTIAL,
    NONE,
    ;

    companion object {
        val DEFAULT = FULL
    }
}

// Instantiated in Eclipse IDE, change parameters carefully.
data class C_CompilerOptions(
    val compatibility: R_LangVersion?,
    val gtv: Boolean,
    val deprecatedError: Boolean,
    val blockCheck: Boolean,
    val atAttrShadowing: C_AtAttrShadowing,
    val testLib: Boolean,
    val hiddenLib: Boolean,
    val allowDbModificationsInObjectExprs: Boolean,
    val symbolInfoFile: C_SourcePath?,
    val complexWhatEnabled: Boolean,
    val mountConflictError: Boolean,
    val appModuleInTestsError: Boolean,
    val useTestDependencyExtensions: Boolean,
    val allowLibNamedArgsAnyVersion: Boolean,
    val allowOlderCompatibilityVersion: Boolean,
    val ide: Boolean,
    val ideDocSymbolsEnabled: Boolean,
    val ideDefIdConflictError: Boolean,
) {
    init {
        if (!allowOlderCompatibilityVersion) {
            RellVersions.checkCompatibilityVersion(compatibility) { IllegalArgumentException(it) }
        }
    }

    fun toBuilder() = Builder(this)

    fun toPojoMap(): Map<String, Any> {
        val map = mutableMapOf(
            "gtv" to gtv,
            "deprecatedError" to deprecatedError,
            "ide" to ide,
            "atAttrShadowing" to atAttrShadowing.name,
            "testLib" to testLib,
            "hiddenLib" to hiddenLib,
            "allowDbModificationsInObjectExprs" to allowDbModificationsInObjectExprs,
            "complexWhatEnabled" to complexWhatEnabled,
            "mountConflictError" to mountConflictError,
            "appModuleInTestsError" to appModuleInTestsError,
            "useTestDependencyExtensions" to useTestDependencyExtensions,
        )

        putNotNull(map, "symbolInfoFile", symbolInfoFile?.str())
        putNotNull(map, "compatibility", compatibility?.str())
        putNotDefault(map, "ideDocSymbolsEnabled") { it.ideDocSymbolsEnabled }
        putNotDefault(map, "ideDefIdConflictError") { it.ideDefIdConflictError }
        putNotDefault(map, "allowLibNamedArgsAnyVersion") { it.allowLibNamedArgsAnyVersion }
        putNotDefault(map, "allowOlderCompatibilityVersion") { it.allowOlderCompatibilityVersion }

        return map.toImmMap()
    }

    private fun <K, V: Any> putNotNull(map: MutableMap<K, V>, key: K, value: V?) {
        if (value != null) {
            map[key] = value
        }
    }

    private fun <K, V: Any> putNotDefault(map: MutableMap<K, V>, key: K, getter: (C_CompilerOptions) -> V) {
        val value = getter(this)
        val defaultValue = getter(DEFAULT)
        if (value != defaultValue) {
            map[key] = value
        }
    }

    override fun toString(): String = javaClass.simpleName

    companion object {
        @JvmField val DEFAULT = C_CompilerOptions(
            compatibility = null,
            gtv = true,
            deprecatedError = false,
            blockCheck = false,
            atAttrShadowing = C_AtAttrShadowing.DEFAULT,
            testLib = false,
            hiddenLib = false,
            allowDbModificationsInObjectExprs = true,
            symbolInfoFile = null,
            complexWhatEnabled = true,
            mountConflictError = true,
            appModuleInTestsError = false,
            useTestDependencyExtensions = false,
            allowLibNamedArgsAnyVersion = false,
            allowOlderCompatibilityVersion = false,
            ide = false,
            ideDocSymbolsEnabled = false,
            ideDefIdConflictError = false,
        )

        @JvmStatic fun builder() = Builder()

        @JvmStatic fun builder(options: C_CompilerOptions) = Builder(options)

        @JvmStatic fun fromPojoMap(map: Map<String, Any>): C_CompilerOptions {
            return C_CompilerOptions(
                compatibility = (map["compatibility"] as String?)?.let { R_LangVersion.of(it) },
                blockCheck = true,
                gtv = map.getValue("gtv") as Boolean,
                deprecatedError = map.getValue("deprecatedError") as Boolean,
                atAttrShadowing = (map["atAttrShadowing"] as String?)
                        ?.let { C_AtAttrShadowing.valueOf(it) } ?: DEFAULT.atAttrShadowing,
                ide = getBoolOpt(map, "ide", DEFAULT.ide),
                testLib = getBoolOpt(map, "testLib", DEFAULT.testLib),
                hiddenLib = getBoolOpt(map, "hiddenLib", DEFAULT.hiddenLib),
                allowDbModificationsInObjectExprs =
                        getBoolOpt(map, "allowDbModificationsInObjectExprs", DEFAULT.allowDbModificationsInObjectExprs),
                symbolInfoFile = (map["symbolInfoFile"] as String?)?.let { C_SourcePath.parse(it) },
                complexWhatEnabled = getBoolOpt(map, "complexWhatEnabled", DEFAULT.complexWhatEnabled),
                mountConflictError = getBoolOpt(map, "mountConflictError", DEFAULT.mountConflictError),
                appModuleInTestsError = getBoolOpt(map, "appModuleInTestsError", DEFAULT.appModuleInTestsError),
                useTestDependencyExtensions =
                        getBoolOpt(map, "useTestDependencyExtensions", DEFAULT.useTestDependencyExtensions),
                allowLibNamedArgsAnyVersion =
                        getBoolOpt(map, "allowLibNamedArgsAnyVersion", DEFAULT.allowLibNamedArgsAnyVersion),
                allowOlderCompatibilityVersion =
                        getBoolOpt(map, "allowOlderCompatibilityVersion", DEFAULT.allowOlderCompatibilityVersion),
                ideDocSymbolsEnabled = getBoolOpt(map, "ideDocSymbolsEnabled", DEFAULT.ideDocSymbolsEnabled),
                ideDefIdConflictError = getBoolOpt(map, "ideDefIdConflictError", DEFAULT.ideDefIdConflictError),
            )
        }

        fun forLangVersion(version: R_LangVersion): C_CompilerOptions {
            return Builder().compatibility(version).build()
        }

        private fun getBoolOpt(map: Map<String, Any>, key: String, def: Boolean): Boolean = (map[key] as Boolean?) ?: def
    }

    class Builder(proto: C_CompilerOptions = DEFAULT) {
        private var compatibility = proto.compatibility
        private var gtv = proto.gtv
        private var deprecatedError = proto.deprecatedError
        private var blockCheck = proto.blockCheck
        private var atAttrShadowing = proto.atAttrShadowing
        private var testLib = proto.testLib
        private var hiddenLib = proto.hiddenLib
        private var allowDbModificationsInObjectExprs = proto.allowDbModificationsInObjectExprs
        private var symbolInfoFile = proto.symbolInfoFile
        private var complexWhatEnabled = proto.complexWhatEnabled
        private var mountConflictError = proto.mountConflictError
        private var appModuleInTestsError = proto.appModuleInTestsError
        private var useTestDependencyExtensions = proto.useTestDependencyExtensions
        private var allowLibNamedArgsAnyVersion = proto.allowLibNamedArgsAnyVersion
        private var allowOlderCompatibilityVersion = proto.allowOlderCompatibilityVersion
        private var ide = proto.ide
        private var ideDocSymbolsEnabled = proto.ideDocSymbolsEnabled
        private var ideDefIdConflictError = proto.ideDefIdConflictError

        @Suppress("UNUSED") fun compatibility(v: R_LangVersion) = apply { compatibility = v }
        @Suppress("UNUSED") fun gtv(v: Boolean) = apply { gtv = v }
        @Suppress("UNUSED") fun deprecatedError(v: Boolean) = apply { deprecatedError = v }
        @Suppress("UNUSED") fun blockCheck(v: Boolean) = apply { blockCheck = v }
        @Suppress("UNUSED") fun atAttrShadowing(v: C_AtAttrShadowing) = apply { atAttrShadowing = v }
        @Suppress("UNUSED") fun testLib(v: Boolean) = apply { testLib = v }
        @Suppress("UNUSED") fun hiddenLib(v: Boolean) = apply { hiddenLib = v }
        @Suppress("UNUSED") fun allowDbModificationsInObjectExprs(v: Boolean) = apply { allowDbModificationsInObjectExprs = v }
        @Suppress("UNUSED") fun symbolInfoFile(v: C_SourcePath?) = apply { symbolInfoFile = v }
        @Suppress("UNUSED") fun complexWhatEnabled(v: Boolean) = apply { complexWhatEnabled = v }
        @Suppress("UNUSED") fun mountConflictError(v: Boolean) = apply { mountConflictError = v }
        @Suppress("UNUSED") fun appModuleInTestsError(v: Boolean) = apply { appModuleInTestsError = v }
        @Suppress("UNUSED") fun useTestDependencyExtensions(v: Boolean) = apply { useTestDependencyExtensions = v }
        @Suppress("UNUSED") fun allowLibNamedArgsAnyVersion(v: Boolean) = apply { allowLibNamedArgsAnyVersion = v }
        @Suppress("UNUSED") fun allowOlderCompatibilityVersion(v: Boolean) = apply { allowOlderCompatibilityVersion = v }
        @Suppress("UNUSED") fun ide(v: Boolean) = apply { ide = v }
        @Suppress("UNUSED") fun ideDocSymbolsEnabled(v: Boolean) = apply { ideDocSymbolsEnabled = v }
        @Suppress("UNUSED") fun ideDefIdConflictError(v: Boolean) = apply { ideDefIdConflictError = v }

        fun build() = C_CompilerOptions(
            compatibility = compatibility,
            gtv = gtv,
            deprecatedError = deprecatedError,
            blockCheck = blockCheck,
            atAttrShadowing = atAttrShadowing,
            testLib = testLib,
            hiddenLib = hiddenLib,
            allowDbModificationsInObjectExprs = allowDbModificationsInObjectExprs,
            symbolInfoFile = symbolInfoFile,
            complexWhatEnabled = complexWhatEnabled,
            mountConflictError = mountConflictError,
            appModuleInTestsError = appModuleInTestsError,
            useTestDependencyExtensions = useTestDependencyExtensions,
            allowLibNamedArgsAnyVersion = allowLibNamedArgsAnyVersion,
            allowOlderCompatibilityVersion = allowOlderCompatibilityVersion,
            ide = ide,
            ideDocSymbolsEnabled = ideDocSymbolsEnabled,
            ideDefIdConflictError = ideDefIdConflictError,
        )
    }
}

class C_CompilerModuleSelection(
    appModules: List<R_ModuleName>?,
    testModules: List<R_ModuleName> = listOf(),
    val testSubModules: Boolean = true,
    val appSubModules: Boolean = false,
) {
    val appModules = appModules?.toImmList()
    val testModules = testModules.toImmList()
}

object C_Compiler {
    fun compile(
        sourceDir: C_SourceDir,
        modules: List<R_ModuleName>,
        options: C_CompilerOptions = C_CompilerOptions.DEFAULT,
    ): C_CompilationResult {
        val modSel = C_CompilerModuleSelection(modules, listOf())
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

        val res = C_LateInit.context(controller.executor) {
            compile0(sourceDir, msgCtx, controller, moduleSelection, extraLibMod)
        }

        return res
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
            val midModules = loadMidModules(msgCtx, sourceDir, moduleSelection, symCtxProvider)
            checkMainModules(msgCtx, moduleSelection, midModules)
            midModules
        } ?: immListOf()

        val extModules = msgCtx.consumeError {
            compileMidModules(msgCtx, midModules)
        } ?: immListOf()

        val moduleHeaders = midModules.associate { it.moduleName to it.compiledHeader }.toImmMap()
        val appCtx = C_AppContext(msgCtx, controller.executor, false, C_ReplAppState.EMPTY, moduleHeaders, extraLibMod)

        msgCtx.consumeError {
            val extCompiler = C_ExtModuleCompiler(appCtx, extModules, immMapOf())
            extCompiler.compileModules()
        }

        val files = extModules.flatMap { it.midModule.filePaths() }.toImmList()

        controller.run()
        val ideSymbolInfos = symCtxManager.finish()

        val app = appCtx.getApp()

        val messages = CommonUtils.sortedByCopy(msgCtx.messages()) { C_ComparablePos(it.pos) }
        val errors = messages.filter { it.type == C_MessageType.ERROR }

        val rApp = if (errors.isEmpty()) app else null
        return C_CompilationResult(rApp, messages, files, ideSymbolInfos)
    }

    private fun compileMidModules(
        msgCtx: C_MessageContext,
        midModules: List<C_MidModule>,
    ): List<C_ExtModule> {
        val selModules = midModules.filter { it.isSelected }

        val midCompiler = C_MidModuleCompiler(msgCtx, midModules)
        for (selModule in selModules) {
            midCompiler.compileModule(selModule.moduleName, null)
        }

        return midCompiler.getExtModules()
    }

    private fun loadMidModules(
            msgCtx: C_MessageContext,
            sourceDir: C_SourceDir,
            modSel: C_CompilerModuleSelection,
            symCtxProvider: C_SymbolContextProvider,
    ): List<C_MidModule> {
        val modLdr = C_ModuleLoader(msgCtx, symCtxProvider, sourceDir, immMapOf())

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

        for (moduleName in modSel.appModules ?: listOf()) {
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

abstract class C_AbstractResult(messages: List<C_Message>) {
    val messages = messages.toImmList()
    val warnings = this.messages.filter { it.type == C_MessageType.WARNING }.toImmList()
    val errors = this.messages.filter { it.type == C_MessageType.ERROR }.toImmList()
}

class C_CompilationResult(
    val app: R_App?,
    messages: List<C_Message>,
    files: List<C_SourcePath>,
    ideSymbolInfos: Map<S_Pos, IdeSymbolInfo>,
): C_AbstractResult(messages) {
    val files = files.toImmList()
    val ideSymbolInfos = ideSymbolInfos.toImmMap()
}

abstract class C_CompilerExecutor {
    abstract fun checkPass(minPass: C_CompilerPass?, maxPass: C_CompilerPass?)
    fun checkPass(pass: C_CompilerPass) = checkPass(pass, pass)

    abstract fun onPass(pass: C_CompilerPass, code: () -> Unit)

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

class C_CompilerController(private val msgCtx: C_MessageContext) {
    val executor: C_CompilerExecutor = ExecutorImpl()

    private val passes = C_CompilerPass.values().map { it to queueOf<C_PassTask>() }.toMap()
    private var currentPass = C_CompilerPass.values()[0]

    private var runCalled = false

    fun run() {
        check(!runCalled)
        runCalled = true

        for (pass in C_CompilerPass.values()) {
            currentPass = pass
            val queue = passes.getValue(pass)
            while (!queue.isEmpty()) {
                val task = queue.remove()
                task.execute()
            }
        }
    }

    private fun onPass0(pass: C_CompilerPass, code: () -> Unit) {
        check(currentPass < pass) { "currentPass: $currentPass targetPass: $pass" }

        val nextPass = currentPass.next()

        if (pass == currentPass || pass == nextPass) {
            val task = C_PassTask(code)
            passes.getValue(pass).add(task)
        } else {
            // Extra code is needed to maintain execution order:
            // - entity 0 adds code to pass A, that code adds code to pass B
            // - entity 1 adds code to pass B directly
            // -> on pass B entity 0 must be executed before entity 1
            val task = C_PassTask { executor.onPass(pass, code) }
            passes.getValue(nextPass).add(task)
        }
    }

    private inner class C_PassTask(private val code: () -> Unit) {
        fun execute() {
            msgCtx.consumeError(code)
        }
    }

    private inner class ExecutorImpl: C_CompilerExecutor() {
        override fun checkPass(minPass: C_CompilerPass?, maxPass: C_CompilerPass?) {
            checkPass(currentPass, minPass, maxPass)
        }

        override fun onPass(pass: C_CompilerPass, code: () -> Unit) {
            onPass0(pass, code)
        }
    }
}
