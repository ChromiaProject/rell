/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.module

import com.google.common.collect.Multimap
import net.postchain.rell.base.compiler.ast.C_ImportTarget
import net.postchain.rell.base.compiler.ast.S_BasicDefinition
import net.postchain.rell.base.compiler.ast.S_PosRange
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.modifier.C_MountAnnotationValue
import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceMemberBase
import net.postchain.rell.base.compiler.base.namespace.C_UserNsProtoBuilder
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.model.R_EnumDefinition
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.ide.IdeCompletion

internal data class C_ExtChainName(val name: String) {
    fun toExtChain(appCtx: C_AppContext): C_ExternalChain = appCtx.addExternalChain(name)
}

internal class C_ExtModule(
    val midModule: C_MidModule,
    val chain: C_ExtChainName?,
    private val files: ImmList<C_ExtModuleFile>,
) {
    fun compileFiles(modCtx: C_ModuleContext) = files.map { it.compile(modCtx) }
}

internal class C_ExtModuleFile(
    private val path: C_SourcePath,
    private val members: ImmList<C_ExtModuleMember>,
    private val symCtx: C_SymbolContext,
) {
    private val ideCompletions = mutableListOf<C_LateGetter<Multimap<String, IdeCompletion>>>()

    fun compile(modCtx: C_ModuleContext): C_CompiledRellFile {
        modCtx.executor.checkPass(C_CompilerPass.DEFINITIONS)

        val actualSymCtx = if (modCtx.extChain == null) symCtx else modCtx.appCtx.symCtxProvider.getNopSymbolContext()
        val fileCtx = C_FileContext(modCtx, actualSymCtx, path)
        val mntCtx = fileCtx.createMountContext()

        if (C_IdeCompletionsUtils.isTargetScope(modCtx.globalCtx.compilerOptions, path, null)) {
            ideCompletions.add(mntCtx.nsCtx.ideCompletions())
        }

        compileMembers(mntCtx)

        val fileFinish = fileCtx.finish()

        val resIdeCompletions = ideCompletions.toImmList().transform {
            list -> list.flatMap { map -> map.entries().map { it.key to it.value } }.toImmMultimap()
        }
        return C_CompiledRellFile(path, fileFinish.mountTables, fileFinish.importsDescriptor, resIdeCompletions)
    }

    private fun compileMembers(mntCtx: C_MountContext) {
        for (mem in members) {
            ideCompletions.add(mem.compile(mntCtx))
        }
    }
}

internal sealed class C_ExtModuleMember {
    protected abstract fun compile0(mntCtx: C_MountContext): C_LateGetter<Multimap<String, IdeCompletion>>

    fun compile(mntCtx: C_MountContext): C_LateGetter<Multimap<String, IdeCompletion>> {
        return mntCtx.msgCtx.consumeError {
            compile0(mntCtx)
        } ?: C_LateGetter.const(immMultimapOf())
    }
}

internal class C_ExtModuleMember_Basic(private val def: S_BasicDefinition): C_ExtModuleMember() {
    override fun compile0(mntCtx: C_MountContext): C_LateGetter<Multimap<String, IdeCompletion>> {
        val subMntCtx = C_MountContext(
            fileCtx = mntCtx.fileCtx,
            nsCtx = mntCtx.nsCtx,
            extChain = mntCtx.extChain,
            nsBuilder = mntCtx.nsBuilder,
            mountName = mntCtx.mountName,
        )

        return def.compileBasic(subMntCtx)
    }
}

internal class C_ExtModuleMember_Enum(
    private val cName: C_Name,
    private val rEnum: R_EnumDefinition,
    private val memBase: C_NamespaceMemberBase,
): C_ExtModuleMember() {
    override fun compile0(mntCtx: C_MountContext): C_LateGetter<Multimap<String, IdeCompletion>> {
        mntCtx.nsBuilder.addEnum(memBase, cName, rEnum)
        return C_LateGetter.const(immMultimapOf())
    }
}

internal class C_ExtModuleMember_Import(
    private val importDef: C_ImportDefinition,
    private val target: C_ImportTarget,
    private val moduleName: R_ModuleName,
    private val extChainName: C_ExtChainName?,
): C_ExtModuleMember() {
    override fun compile0(mntCtx: C_MountContext): C_LateGetter<Multimap<String, IdeCompletion>> {
        val pos = importDef.pos
        val extChain = extChainName?.toExtChain(mntCtx.appCtx)

        val module = mntCtx.modCtx.getModule(moduleName, extChain)
        if (module == null) {
            mntCtx.msgCtx.error(pos, C_Errors.msgModuleNotFound(moduleName))
            return C_LateGetter.const(immMultimapOf())
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
        return C_LateGetter.const(immMultimapOf())
    }
}

internal class C_ExtModuleMember_Namespace(
    private val qualifiedName: C_IdeQualifiedName?,
    private val posRange: S_PosRange,
    private val members: ImmList<C_ExtModuleMember>,
    private val mount: C_MountAnnotationValue?,
    private val extChainName: C_ExtChainName?,
    private val deprecated: C_Deprecated?,
): C_ExtModuleMember() {
    override fun compile0(mntCtx: C_MountContext): C_LateGetter<Multimap<String, IdeCompletion>> {
        val subMntCtx = createSubMountContext(mntCtx)

        val ideCompletions = mutableListOf<C_LateGetter<Multimap<String, IdeCompletion>>>()

        for (member in members) {
            ideCompletions.add(member.compile(subMntCtx))
        }

        if (C_IdeCompletionsUtils.isTargetScope(mntCtx.globalCtx.compilerOptions, mntCtx.fileCtx.path, posRange)) {
            ideCompletions.add(subMntCtx.nsCtx.ideCompletions())
        }

        return ideCompletions.toImmList().transform {
            it.flatMap { map -> map.entries().map { e -> e.key to e.value } }.toImmMultimap()
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

internal class C_ExtModuleCompiler(
    private val appCtx: C_AppContext,
    extModules: List<C_ExtModule>,
    preModules: ImmMap<C_ModuleKey, C_PrecompiledModule>,
) {
    private val bases = extModules.map { compileModuleBasis(it) }

    val modProvider = C_ModuleProvider(bases.associateToImmMap { it.module.key to it.module }, preModules)

    private var done = false

    fun compileModules(): C_LateGetter<Multimap<String, IdeCompletion>> {
        appCtx.executor.checkPass(C_CompilerPass.DEFINITIONS)
        check(!done)
        done = true

        val ideCompletions = mutableListOf<C_LateGetter<Multimap<String, IdeCompletion>>>()

        for (base in bases) {
            ideCompletions.add(base.compile(appCtx, modProvider))
        }

        return ideCompletions.toImmList().transform {
            list -> list.flatMap { map -> map.entries().map { it.key to it.value } }.toImmMultimap()
        }
    }

    private fun compileModuleBasis(extModule: C_ExtModule): C_ModuleBasis {
        val midModule = extModule.midModule

        val parentName = midModule.parentName
        val parentModuleKey = if (parentName == null) null else C_ModuleKey(parentName, null)

        val extChain = extModule.chain?.toExtChain(appCtx)
        val moduleKey = C_ModuleKey(midModule.moduleName, extChain)

        val internalsLate = appCtx.executor.lateInit(C_CompilerPass.MODULES, C_ModuleInternals.empty(moduleKey))
        val importsGetter = internalsLate.getter.transform { it.importsDescriptor }
        val descriptor = C_ModuleDescriptor(moduleKey, midModule.compiledHeader, midModule.isDirectory, importsGetter)

        val module = C_Module(
            appCtx.executor,
            descriptor,
            parentModuleKey,
            internalsLate.getter,
        )

        return C_ModuleBasis(extModule, module, internalsLate)
    }
}

private class C_ModuleBasis(
    val extModule: C_ExtModule,
    val module: C_Module,
    private val internalsLate: C_LateInit<C_ModuleInternals>,
) {
    fun compile(appCtx: C_AppContext, modProvider: C_ModuleProvider): C_LateGetter<Multimap<String, IdeCompletion>> {
        checkParentModule(appCtx.msgCtx, modProvider)

        val modCtx = C_RegularModuleContext(
            appCtx,
            modProvider,
            module,
            selected = extModule.midModule.isSelected,
            isTestDependency = extModule.midModule.isTestDependency,
        )

        val compiledFiles = extModule.compileFiles(modCtx)
        val ideCompletions = compiledFiles.mapToImmList { it.ideCompletions }

        appCtx.executor.onPass(C_CompilerPass.MODULES) {
            val docPos = module.header.docPos
            val docSymbol = module.header.docSymbol
            val compiled = C_ModuleCompiler.compile(modCtx, compiledFiles, docPos, docSymbol, modCtx.nsGetter)
            internalsLate.set(C_ModuleInternals(compiled.contents, compiled.importsDescriptor))
            appCtx.addModule(module.descriptor, compiled)
        }

        return ideCompletions.transform {
            list -> list.flatMap { map -> map.entries().map { it.key to it.value } }.toImmMultimap()
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
