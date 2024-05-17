/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.module

import net.postchain.rell.base.compiler.ast.C_ImportTarget
import net.postchain.rell.base.compiler.ast.S_BasicDefinition
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.modifier.C_MountAnnotationValue
import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceMemberBase
import net.postchain.rell.base.compiler.base.namespace.C_UserNsProtoBuilder
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.compiler.base.utils.C_LateInit
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.model.R_EnumDefinition
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.toImmList

data class C_ExtChainName(val name: String) {
    fun toExtChain(appCtx: C_AppContext): C_ExternalChain = appCtx.addExternalChain(name)
}

class C_ExtModule(
    val midModule: C_MidModule,
    val chain: C_ExtChainName?,
    files: List<C_ExtModuleFile>,
) {
    private val files = files.toImmList()

    fun compileFiles(modCtx: C_ModuleContext) = files.map { it.compile(modCtx) }
}

class C_ExtModuleFile(
    private val path: C_SourcePath,
    members: List<C_ExtModuleMember>,
    private val symCtx: C_SymbolContext,
) {
    private val members = members.toImmList()

    fun compile(modCtx: C_ModuleContext): C_CompiledRellFile {
        modCtx.executor.checkPass(C_CompilerPass.DEFINITIONS)

        val actualSymCtx = if (modCtx.extChain != null) symCtx.nopContext() else symCtx
        val fileCtx = C_FileContext(modCtx, actualSymCtx)
        val mntCtx = fileCtx.createMountContext()

        compileMembers(mntCtx)

        val mntTables = fileCtx.mntBuilder.build()
        val fileContents = fileCtx.createContents()

        return C_CompiledRellFile(path, mntTables, fileContents)
    }

    private fun compileMembers(mntCtx: C_MountContext) {
        for (mem in members) {
            mem.compile(mntCtx)
        }
    }
}

sealed class C_ExtModuleMember {
    protected abstract fun compile0(mntCtx: C_MountContext)

    fun compile(mntCtx: C_MountContext) {
        mntCtx.msgCtx.consumeError {
            compile0(mntCtx)
        }
    }
}

class C_ExtModuleMember_Basic(private val def: S_BasicDefinition): C_ExtModuleMember() {
    override fun compile0(mntCtx: C_MountContext) {
        def.compileBasic(mntCtx)
    }
}

class C_ExtModuleMember_Enum(
    private val cName: C_Name,
    private val rEnum: R_EnumDefinition,
    private val memBase: C_NamespaceMemberBase,
): C_ExtModuleMember() {
    override fun compile0(mntCtx: C_MountContext) {
        mntCtx.nsBuilder.addEnum(memBase, cName, rEnum)
    }
}

class C_ExtModuleMember_Import(
        private val importDef: C_ImportDefinition,
        private val target: C_ImportTarget,
        private val moduleName: R_ModuleName,
        private val extChainName: C_ExtChainName?
): C_ExtModuleMember() {
    override fun compile0(mntCtx: C_MountContext) {
        val pos = importDef.pos
        val extChain = extChainName?.toExtChain(mntCtx.appCtx)

        val module = mntCtx.modCtx.getModule(moduleName, extChain)
        if (module == null) {
            mntCtx.msgCtx.error(pos, C_Errors.msgModuleNotFound(moduleName))
            return
        }

        target.addToNamespace(mntCtx, importDef, module)

        if ((extChain != null || mntCtx.modCtx.external) && !module.header.external) {
            mntCtx.msgCtx.error(pos, "import:module_not_external:$moduleName", "Module '$moduleName' is not external")
        }

        if (module.header.test && !(mntCtx.modCtx.test || mntCtx.modCtx.repl)) {
            mntCtx.msgCtx.error(pos, "import:module_test:$moduleName",
                    "Cannot import a test module '$moduleName' from a non-test module")
        }

        mntCtx.fileCtx.addImport(C_ImportDescriptor(pos, module))
    }
}

class C_ExtModuleMember_Namespace(
    private val qualifiedName: C_IdeQualifiedName?,
    members: List<C_ExtModuleMember>,
    private val mount: C_MountAnnotationValue?,
    private val extChainName: C_ExtChainName?,
    private val deprecated: C_Deprecated?,
): C_ExtModuleMember() {
    private val members = members.toImmList()

    override fun compile0(mntCtx: C_MountContext) {
        val subMntCtx = createSubMountContext(mntCtx)
        for (member in members) {
            member.compile(subMntCtx)
        }
    }

    private fun createSubMountContext(mntCtx: C_MountContext): C_MountContext {
        val extChain = extChainName?.toExtChain(mntCtx.appCtx) ?: mntCtx.extChain

        if (qualifiedName == null) {
            val subMountName = mntCtx.mountName(mount, null)
            return C_MountContext(mntCtx.fileCtx, mntCtx.nsCtx, extChain, mntCtx.nsBuilder, subMountName)
        }

        var node = NsNode(mntCtx.fileCtx, mntCtx.nsBuilder, mntCtx)

        for (ideName in qualifiedName.parts.dropLast(1)) {
            node = node.subNode(ideName, null, null, mntCtx.extChain)
        }

        val lastName = qualifiedName.parts.last()
        node = node.subNode(lastName, mount, deprecated, extChain)

        return node.mntCtx
    }

    private class NsNode(
        val fileCtx: C_FileContext,
        val nsBuilder: C_UserNsProtoBuilder,
        val mntCtx: C_MountContext,
    ) {
        fun subNode(
            ideName: C_IdeName,
            subMount: C_MountAnnotationValue?,
            subDeprecated: C_Deprecated?,
            subExtChain: C_ExternalChain?,
        ): NsNode {
            val resNsBuilder = nsBuilder.addNamespace(ideName.name, true, ideName.ideInfo, deprecated = subDeprecated)
            val nsPath = mntCtx.nsCtx.namespacePath.append(ideName.name.rName)
            val subScopeBuilder = mntCtx.nsCtx.scopeBuilder.nested(resNsBuilder.futureNs())
            val resNsCtx = C_NamespaceContext(fileCtx.modCtx, fileCtx.symCtx, nsPath, subScopeBuilder)
            val resMountName = mntCtx.mountName(subMount, C_QualifiedName(ideName.name))
            val resMntCtx = C_MountContext(mntCtx.fileCtx, resNsCtx, subExtChain, resNsBuilder, resMountName)
            return NsNode(fileCtx, resNsBuilder, resMntCtx)
        }
    }
}

class C_ExtModuleCompiler(
        private val appCtx: C_AppContext,
        extModules: List<C_ExtModule>,
        preModules: Map<C_ModuleKey, C_PrecompiledModule>
) {
    private val bases = extModules.map { compileModuleBasis(it) }

    val modProvider = C_ModuleProvider(bases.associate { it.module.key to it.module }, preModules)

    private var done = false

    fun compileModules() {
        appCtx.executor.checkPass(C_CompilerPass.DEFINITIONS)
        check(!done)
        done = true

        for (base in bases) {
            base.compile(appCtx, modProvider)
        }
    }

    private fun compileModuleBasis(extModule: C_ExtModule): C_ModuleBasis {
        val midModule = extModule.midModule

        val parentName = midModule.parentName
        val parentModuleKey = if (parentName == null) null else C_ModuleKey(parentName, null)

        val extChain = extModule.chain?.toExtChain(appCtx)
        val moduleKey = C_ModuleKey(midModule.moduleName, extChain)

        val internalsLate = C_LateInit(C_CompilerPass.MODULES, C_ModuleInternals.empty(moduleKey))
        val importsGetter = internalsLate.getter.transform { it.importsDescriptor }
        val descriptor = C_ModuleDescriptor(moduleKey, midModule.compiledHeader, midModule.isDirectory, importsGetter)

        val module = C_Module(
                appCtx.executor,
                descriptor,
                parentModuleKey,
                internalsLate.getter
        )

        return C_ModuleBasis(extModule, module, internalsLate)
    }
}

private class C_ModuleBasis(
    val extModule: C_ExtModule,
    val module: C_Module,
    private val internalsLate: C_LateInit<C_ModuleInternals>,
) {
    fun compile(appCtx: C_AppContext, modProvider: C_ModuleProvider) {
        checkParentModule(appCtx.msgCtx, modProvider)

        val modCtx = C_RegularModuleContext(
                appCtx,
                modProvider,
                module,
                selected = extModule.midModule.isSelected,
                isTestDependency = extModule.midModule.isTestDependency,
        )

        val compiledFiles = extModule.compileFiles(modCtx)

        appCtx.executor.onPass(C_CompilerPass.MODULES) {
            val docPos = module.header.docPos
            val docSymbol = module.header.docSymbol
            val compiled = C_ModuleCompiler.compile(modCtx, compiledFiles, docPos, docSymbol, modCtx.nsGetter)
            internalsLate.set(C_ModuleInternals(compiled.contents, compiled.importsDescriptor))
            appCtx.addModule(module.descriptor, compiled)
        }
    }

    private fun checkParentModule(msgCtx: C_MessageContext, modProvider: C_ModuleProvider) {
        val midModule = extModule.midModule
        val startPos = midModule.startPos
        val header = module.header
        val parentHeader = module.parentKey?.let { modProvider.getModule(it.name, it.extChain)?.header }

        if (startPos != null && !header.test && parentHeader != null && parentHeader.test) {
            val name = midModule.moduleName
            val parentName = midModule.parentName
            msgCtx.error(startPos, "module:parent_is_test:$name:$parentName",
                    "Parent module of '$name' is a test module '$parentName'")
        }
    }
}
