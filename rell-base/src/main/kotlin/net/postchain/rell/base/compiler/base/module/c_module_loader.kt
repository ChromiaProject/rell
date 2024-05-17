/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.module

import net.postchain.rell.base.compiler.base.core.C_CompilerPass
import net.postchain.rell.base.compiler.base.core.C_MessageContext
import net.postchain.rell.base.compiler.base.core.C_SymbolContextProvider
import net.postchain.rell.base.compiler.base.utils.C_CommonError
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.compiler.base.utils.C_LateInit
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.model.R_MountName
import net.postchain.rell.base.utils.Nullable
import net.postchain.rell.base.utils.doc.*
import net.postchain.rell.base.utils.futures.FcFuture
import net.postchain.rell.base.utils.futures.FcFutures
import net.postchain.rell.base.utils.futures.FcManager
import net.postchain.rell.base.utils.ide.IdeFilePath
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmMap

class C_ModuleInfo(
    val idePath: IdeFilePath?,
    val docSymbolGetter: C_LateGetter<Nullable<DocSymbol>>,
)

sealed class C_ImportModuleLoader {
    abstract fun loadModule(name: R_ModuleName)

    // Load the specified module. If the module doesn't exist, loads the deepest existing parent module.
    // Returns info about all existing parent modules.
    abstract fun loadModuleEx(name: R_ModuleName): Map<R_ModuleName, C_ModuleInfo>
}

class C_ModuleLoader(
    msgCtx: C_MessageContext,
    symCtxProvider: C_SymbolContextProvider,
    sourceDir: C_SourceDir,
    preModuleHeaders: Map<R_ModuleName, C_ModuleHeader>,
) {
    private val preModuleHeaders = preModuleHeaders.toImmMap()

    val readerCtx = C_ModuleReaderContext(S_AppContext(msgCtx, symCtxProvider, C_ImportModuleLoaderImpl()))
    private val moduleReader = C_ModuleReader(readerCtx, sourceDir)

    private val fcMgr = FcManager.create(allowRecursiveExecution = true)
    private val moduleStates = mutableMapOf<R_ModuleName, C_ModuleState>()

    private val selectedModules = mutableSetOf<R_ModuleName>()
    private var loadingTestDependencies = false // Looks like a hack (depends on methods invocation order), but fine.

    private var done = false

    fun finish(): List<C_MidModule> {
        check(!done)
        done = true
        fcMgr.finish()
        val midModules = moduleStates.values
            .map { it.fcFuture.getResult() }
            .filter { C_ModuleUtils.isAllowedModuleName(it.moduleName) }
            .map { it.toMidModule(it.moduleName in selectedModules) }
        return midModules.toImmList()
    }

    fun loadAllModules() {
        discoverModulesTree(R_ModuleName.EMPTY, false)
        fcMgr.execute()
    }

    fun loadModule(name: R_ModuleName, includeSubModules: Boolean = false) {
        check(!loadingTestDependencies)
        if (includeSubModules) {
            discoverModulesTree(name, false)
            fcMgr.execute()
        } else {
            loadModule0(name, true)
        }
    }

    private fun loadModule0(name: R_ModuleName, select: Boolean): Boolean {
        check(!done)

        if (select) {
            selectedModules.add(name)
        }

        if (isModuleLoaded(name)) {
            return true
        }

        val source = moduleReader.readModuleSource(name)
        source ?: return false

        addModule(name, source)
        fcMgr.execute()
        return true
    }

    private fun addModule(name: R_ModuleName, source: C_ModuleSource) {
        val state = C_ModuleState(name, source)
        moduleStates[name] = state
    }

    fun loadTestModule(moduleName: R_ModuleName, subModules: Boolean) {
        check(!done)
        loadingTestDependencies = true

        if (subModules) {
            discoverModulesTree(moduleName, true)
            fcMgr.execute()
        } else {
            loadModule0(moduleName, true)
        }
    }

    private fun findParentModule(name: R_ModuleName): R_ModuleName? {
        // Need to check all parent modules, not just the direct (immediate) parent, because the direct parent
        // not necessarily exists.

        var curName = name

        while (!curName.isEmpty()) {
            curName = curName.parent()

            if (isModuleLoaded(curName)) {
                return curName
            }

            val source = try {
                moduleReader.readModuleSource(curName)
            } catch (e: C_CommonError) {
                // ignore
                null
            }

            if (source != null) {
                addModule(curName, source)
                return curName
            }
        }

        return null
    }

    private fun isModuleLoaded(name: R_ModuleName) = name in moduleStates || name in preModuleHeaders

    private fun discoverModulesTree(rootModule: R_ModuleName, test: Boolean) {
        val handler = C_ModulesTreeHandler(rootModule, test)

        val source = moduleReader.readModuleSource(rootModule)
        if (source != null) {
            handler.handle(source)
        }

        when (source) {
            is C_FileModuleSource -> {}
            null, is C_DirModuleSource -> {
                discoverModulesTree0(rootModule, handler)
            }
        }
    }

    private fun discoverModulesTree0(moduleName: R_ModuleName, handler: C_ModulesTreeHandler) {
        val fileSources = moduleReader.fileSubModules(moduleName)

        for (source in fileSources) {
            handler.handle(source)
        }

        val dirSubModules = moduleReader.dirSubModules(moduleName)

        for (subModuleName in dirSubModules) {
            val source = moduleReader.readModuleSource(subModuleName)
            if (source != null) {
                handler.handle(source)
            }
            discoverModulesTree0(subModuleName, handler)
        }
    }

    private inner class C_ModulesTreeHandler(
        private val rootModule: R_ModuleName,
        private val targetIsTest: Boolean,
    ) {
        fun handle(source: C_ModuleSource) {
            val name = source.moduleName
            val isTest = source.compileHeader()?.test == true

            val match = isTest == targetIsTest
            if (match) {
                selectedModules.add(name)
            }

            if (match || name == rootModule) {
                if (!isModuleLoaded(name)) {
                    addModule(name, source)
                }
            }
        }
    }

    private inner class C_ImportModuleLoaderImpl: C_ImportModuleLoader() {
        override fun loadModule(name: R_ModuleName) {
            loadModule0(name, false)
        }

        override fun loadModuleEx(name: R_ModuleName): Map<R_ModuleName, C_ModuleInfo> {
            var curName = name
            while (!loadModule0(curName, false) && !curName.isEmpty()) {
                curName = curName.parent()
            }

            val res = mutableMapOf<R_ModuleName, C_ModuleInfo>()

            while (true) {
                val job = moduleStates[curName]

                val modInfo = when {
                    job != null -> C_ModuleInfo(job.idePath, job.docSymbolGetter)
                    curName in preModuleHeaders -> C_ModuleInfo(null, C_LateGetter.const(Nullable.of()))
                    else -> null
                }
                if (modInfo != null) {
                    res[curName] = modInfo
                }

                if (curName.isEmpty()) break
                curName = curName.parent()
            }

            return res.toImmMap()
        }
    }

    private inner class C_ModuleState(
        val moduleName: R_ModuleName,
        source: C_ModuleSource,
    ) {
        private val docSymbolLate: C_LateInit<Nullable<DocSymbol>> = C_LateInit(C_CompilerPass.MODULES, Nullable.of())

        val idePath = source.idePath()
        val docSymbolGetter = docSymbolLate.getter

        private var modSource: C_ModuleSource? = source

        val fcFuture: FcFuture<C_LoaderModule> = fcMgr.future().delegate {
            load()
        }

        private fun load(): FcFuture<C_LoaderModule> {
            val source = modSource!!
            modSource = null

            val header = source.compileHeader()
            val parentName = if (header != null && header.test) null else findParentModule(moduleName)

            val parentJob = if (parentName == null) null else {
                loadModule0(parentName, false)
                moduleStates[parentName]
            }

            val parentFuture: FcFuture<C_LoaderModule?> = parentJob?.fcFuture ?: FcFutures.value(null)
            return fcMgr.future().after(parentFuture).compute { parentLdrModule ->
                makeLoaderModule(source, header, parentName, parentLdrModule)
            }
        }

        private fun makeLoaderModule(
            source: C_ModuleSource,
            header: C_SourceModuleHeader?,
            parentName: R_ModuleName?,
            parentModule: C_LoaderModule?,
        ): C_LoaderModule {
            val midFiles = source.compile()

            val preParentMountName = if (parentName == null) null else preModuleHeaders[parentName]?.mountName
            val parentMountName = preParentMountName ?: parentModule?.mountName ?: R_MountName.EMPTY

            val mount = header?.mount?.process(true)
            val mountName = mount?.calculateMountName(readerCtx.msgCtx, parentMountName) ?: parentMountName

            return C_LoaderModule(
                moduleName,
                mountName,
                parentName,
                header,
                midFiles,
                isDirectory = source.isDirectory(),
                isTestDependency = loadingTestDependencies,
                docPos = source.docPos(),
                docFactory = readerCtx.msgCtx.globalCtx.docFactory,
                docSymbolLate = docSymbolLate,
            )
        }
    }
}

private class C_LoaderModule(
    val moduleName: R_ModuleName,
    val mountName: R_MountName,
    private val parentName: R_ModuleName?,
    private val header: C_SourceModuleHeader?,
    private val files: List<C_MidModuleFile>,
    private val isDirectory: Boolean,
    private val isTestDependency: Boolean,
    private val docPos: DocSourcePos,
    private val docFactory: DocSymbolFactory,
    private val docSymbolLate: C_LateInit<Nullable<DocSymbol>>,
) {
    fun toMidModule(isSelected: Boolean): C_MidModule {
        val docSymbol = makeDocSymbol(moduleName, mountName, header?.docModifiers ?: DocModifiers.NONE)
        docSymbolLate.set(Nullable.of(docSymbol), allowEarly = true)

        val midHeader = if (header == null) null else {
            C_MidModuleHeader(header.pos, header.abstract, header.external, header.test)
        }

        val compiledHeader = C_ModuleHeader(
            mountName = mountName,
            abstract = header?.abstract != null,
            external = header?.external ?: false,
            test = header?.test ?: false,
            docPos,
            docSymbol,
        )

        return C_MidModule(
            moduleName = moduleName,
            parentName = parentName,
            mountName = mountName,
            header = midHeader,
            compiledHeader = compiledHeader,
            files = files,
            isDirectory = isDirectory,
            isTestDependency = isTestDependency,
            isSelected = isSelected,
        )
    }

    private fun makeDocSymbol(moduleName: R_ModuleName, mountName: R_MountName, mods: DocModifiers): DocSymbol {
        val docMountName = if (mountName.isEmpty()) null else mountName.str()
        val docDec = DocDeclaration_Module(mods)
        return docFactory.makeDocSymbol(
            DocSymbolKind.MODULE,
            DocSymbolName.module(moduleName.str()),
            mountName = docMountName,
            declaration = docDec,
        )
    }
}
