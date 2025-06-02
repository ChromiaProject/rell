/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.namespace

import com.google.common.collect.Sets
import net.postchain.rell.base.compiler.ast.S_ExactImportTargetItem
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.module.*
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.model.R_AppUid
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.ide.IdeGlobalSymbolLink
import net.postchain.rell.base.utils.ide.IdeSymbolCategory
import net.postchain.rell.base.utils.ide.IdeSymbolGlobalId
import java.util.*

interface C_NsAsm_BasicAssembler {
    fun futureNs(): Getter<C_Namespace>
}

interface C_NsAsm_ComponentAssembler: C_NsAsm_BasicAssembler {
    fun namespacePath(): C_RNamePath
    fun addDef(name: C_Name, member: C_NamespaceMember)

    fun addNamespace(
        name: C_Name,
        merge: Boolean,
        ideInfo: C_IdeSymbolInfo,
        deprecated: C_Deprecated?,
    ): C_NsAsm_ComponentAssembler

    fun addModuleImport(alias: C_Name, module: C_ModuleDescriptor, ideInfo: C_IdeSymbolInfo)

    fun addExactImport(
        alias: C_Name,
        module: C_ModuleKey,
        qNameHand: C_QualifiedNameHandle,
        aliasPair: Pair<C_NameHandle, DocSymbolTransformer>?,
    )

    fun addWildcardImport(module: C_ModuleKey, pathHands: ImmList<C_NameHandle>)
}

interface C_NsAsm_ModuleAssembler: C_NsAsm_BasicAssembler {
    fun futureDefs(): C_LateGetter<C_ModuleDefs>
    fun addComponent(): C_NsAsm_ComponentAssembler
}

interface C_NsAsm_ReplAssembler: C_NsAsm_BasicAssembler {
    fun addComponent(): C_NsAsm_ComponentAssembler
}

interface C_NsAsm_AppAssembler {
    fun addModule(moduleKey: C_ModuleKey, sysNsProto: C_SysNsProto, exportSysEntities: Boolean): C_NsAsm_ModuleAssembler
    fun addRepl(sysNsProto: C_SysNsProto, linkedModule: C_ModuleKey?, oldState: C_NsAsm_ReplState): C_NsAsm_ReplAssembler
    fun assemble(): C_NsAsm_App

    companion object {
        fun create(
                executor: C_CompilerExecutor,
                msgCtx: C_MessageContext,
                appUid: R_AppUid,
                preModules: ImmMap<C_ModuleKey, C_PrecompiledModule>
        ): C_NsAsm_AppAssembler {
            return C_NsAsm_InternalAppAssembler(executor, msgCtx, appUid, preModules)
        }
    }
}

class C_NsAsm_Module(val rawNs: C_NsAsm_RawNamespace, val impNs: C_NsImp_Namespace, val ns: C_Namespace)

class C_NsAsm_App(val newReplState: C_NsAsm_ReplState, val modules: ImmMap<C_ModuleKey, C_NsAsm_Module>) {
    companion object { val EMPTY = C_NsAsm_App(C_NsAsm_ReplState.EMPTY, immMapOf()) }
}

sealed class C_NsAsm_ReplState {
    companion object { val EMPTY: C_NsAsm_ReplState = C_NsAsm_InternalReplState.EMPTY }
}

class C_NsAsm_WildcardImport(val module: C_ModuleKey, val path: ImmList<C_Name>, val names: C_NsAsm_WildcardImportNames)

class C_NsAsm_ExactImport(val module: C_ModuleKey, val qName: C_QualifiedName)

class C_NsAsm_WildcardImportNames(executor: C_CompilerExecutor, private val pathHands: ImmList<C_NameHandle>) {
    private var ideInfoDefined = false

    init {
        executor.onPass(C_CompilerPass.FINISH) {
            if (!ideInfoDefined) {
                setIdeInfo(immListOf())
            }
        }
    }

    fun setIdeInfo(ideInfos: List<C_IdeSymbolInfo>) {
        check(!ideInfoDefined)
        ideInfoDefined = true

        pathHands.forEachIndexed { i, part ->
            val ideInfo = ideInfos.getOrNull(i) ?: C_IdeSymbolInfo.UNKNOWN
            part.setIdeInfo(ideInfo)
        }
    }
}

class C_NsAsm_ExactImportNames(
    executor: C_CompilerExecutor,
    private val qNameHand: C_QualifiedNameHandle,
    private val alias: Alias?,
) {
    private var ideInfoDefined = false

    init {
        executor.onPass(C_CompilerPass.FINISH) {
            if (!ideInfoDefined) {
                setIdeInfo(immListOf())
            }
        }
    }

    fun setIdeInfo(ideInfos: List<C_IdeSymbolInfo>) {
        check(!ideInfoDefined)
        ideInfoDefined = true

        val fullIdeInfos = qNameHand.parts.indices
            .mapToImmList { ideInfos.getOrNull(it) ?: C_IdeSymbolInfo.UNKNOWN }

        qNameHand.setIdeInfo(fullIdeInfos)

        if (alias != null) {
            val defId = alias.ideId.symId
            val lastIdeInfo0 = fullIdeInfos.lastOrNull() ?: C_IdeSymbolInfo.UNKNOWN
            val lastIdeInfo = lastIdeInfo0
                .update(defId = defId, link = null)
                .transformDocSymbol(alias.docSymbolTransformer)
            alias.nameHand.setIdeInfo(lastIdeInfo)
        }
    }

    fun aliasMember(targetMember: C_NamespaceMember): C_NamespaceMember {
        alias ?: return targetMember
        val link = IdeGlobalSymbolLink(alias.ideId)
        val aliasIdeInfo = targetMember.ideInfo
            .update(defId = null, link = link)
            .transformDocSymbol(alias.docSymbolTransformer)

        val aliasBase = C_NamespaceMemberBase(alias.defName, aliasIdeInfo, targetMember.restrictions)
        val docPos = alias.nameHand.pos.toDocPos()

        return C_NamespaceMember_Alias(aliasBase, targetMember, docPos)
    }

    class Alias(
        val defName: C_DefinitionName,
        val ideId: IdeSymbolGlobalId,
        val nameHand: C_NameHandle,
        val docSymbolTransformer: DocSymbolTransformer,
    )
}

class C_NsAsm_Namespace(
    val defs: ImmMap<R_Name, C_NsAsm_Def>,
    val importDefs: ImmMultimap<R_Name, C_NsAsm_Def>,
    val wildcardImports: ImmList<C_NsAsm_WildcardImport>,
) {
    companion object { val EMPTY = C_NsAsm_Namespace(immMapOf(), immMultimapOf(), immListOf()) }
}

sealed class C_NsAsm_Def

class C_NsAsm_Def_Simple(val member: C_NamespaceMember): C_NsAsm_Def()

class C_NsAsm_Def_ExactImport(
    val imp: C_NsAsm_ExactImport,
    val names: C_NsAsm_ExactImportNames,
): C_NsAsm_Def()

sealed class C_NsAsm_Def_Namespace(
    val defName: C_DefinitionName,
    val deprecated: C_Deprecated?,
    val importModule: C_ModuleDescriptor?,
): C_NsAsm_Def() {
    abstract fun ns(): C_NsAsm_Namespace
    abstract fun ideSymbolInfo(): C_IdeSymbolInfo

    companion object {
        fun createDirect(
            ns: C_NsAsm_Namespace,
            defName: C_DefinitionName,
            deprecated: C_Deprecated?,
            ideInfo: C_IdeSymbolInfo,
            importModule: C_ModuleDescriptor?,
        ): C_NsAsm_Def {
            return C_NsAsm_Def_DirectNamespace(defName, deprecated, importModule, ns, ideInfo)
        }

        fun createLate(
            defName: C_DefinitionName,
            deprecated: C_Deprecated?,
            ideInfo: C_IdeSymbolInfo,
            importModule: C_ModuleDescriptor?,
            getter: LateGetter<C_NsAsm_Namespace>,
        ): C_NsAsm_Def {
            return C_NsAsm_Def_LateNamespace(defName, deprecated, importModule, ideInfo, getter)
        }
    }
}

private class C_NsAsm_Def_DirectNamespace(
    defName: C_DefinitionName,
    deprecated: C_Deprecated?,
    importModule: C_ModuleDescriptor?,
    private val ns: C_NsAsm_Namespace,
    private val ideInfo: C_IdeSymbolInfo,
): C_NsAsm_Def_Namespace(defName, deprecated, importModule) {
    override fun ns() = ns
    override fun ideSymbolInfo() = ideInfo
}

private class C_NsAsm_Def_LateNamespace(
    defName: C_DefinitionName,
    deprecated: C_Deprecated?,
    importModule: C_ModuleDescriptor?,
    private val ideInfo: C_IdeSymbolInfo,
    private val getter: LateGetter<C_NsAsm_Namespace>,
): C_NsAsm_Def_Namespace(defName, deprecated, importModule) {
    override fun ns() = getter.get()
    override fun ideSymbolInfo() = ideInfo
}

private sealed class C_NsAsm_InternalAssembler {
    private var assembled = false

    protected fun checkCanModify() {
        check(!assembled)
    }

    protected fun finish() {
        check(!assembled)
        assembled = true
    }
}

private sealed class C_NsAsm_InternalBasicAssembler(
        protected val nsLinker: C_NsAsm_NamespaceLinker,
        protected val nsKey: C_NsAsm_NamespaceKey,
        protected val stamp: R_AppUid
): C_NsAsm_InternalAssembler(), C_NsAsm_BasicAssembler {
    private val getter = nsLinker.getLink(nsKey)
    final override fun futureNs() = getter
}

private class C_NsAsm_MutableEntry(
    val name: C_Name,
    val type: C_DeclarationType,
    val def: C_NsAsm_MutableDef,
    val identity: C_NsAsm_Identity,
    val container: C_ContainerKey,
    val stamp: R_AppUid,
) {
    fun assemble(): C_NsAsm_RawEntry {
        val def2 = def.assemble()
        return C_NsAsm_RawEntry(name, type, def2, identity, container, stamp)
    }
}

private sealed class C_NsAsm_MutableDef {
    abstract fun assemble(): C_NsAsm_RawDef
}

private class C_NsAsm_MutableDef_Simple(private val def: C_NsAsm_RawDef): C_NsAsm_MutableDef() {
    override fun assemble() = def
}

private class C_NsAsm_MutableDef_Namespace(
    private val asm: C_NsAsm_InternalComponentAssembler,
    private val merge: Boolean,
    private val defName: C_DefinitionName,
    private val ideInfo: C_IdeSymbolInfo,
    private val deprecated: C_Deprecated?,
): C_NsAsm_MutableDef() {
    override fun assemble(): C_NsAsm_RawDef {
        val ns = asm.assemble()
        return C_NsAsm_RawDef_Namespace(ns, merge, defName, ideInfo, deprecated, null)
    }
}

private class C_NsAsm_InternalComponentAssembler(
    private val executor: C_CompilerExecutor,
    nsLinker: C_NsAsm_NamespaceLinker,
    nsKey: C_NsAsm_NamespaceKey,
    stamp: R_AppUid,
): C_NsAsm_InternalBasicAssembler(nsLinker, nsKey, stamp), C_NsAsm_ComponentAssembler {
    private val entries = mutableListOf<C_NsAsm_MutableEntry>()
    private val wildcardImports = mutableListOf<C_NsAsm_RawWildcardImport>()

    private fun addEntry(name: C_Name, type: C_DeclarationType, def: C_NsAsm_MutableDef, identity: C_NsAsm_Identity) {
        checkCanModify()
        entries.add(C_NsAsm_MutableEntry(name, type, def, identity, nsKey.container, stamp))
    }

    override fun namespacePath(): C_RNamePath = nsKey.path

    override fun addDef(name: C_Name, member: C_NamespaceMember) {
        val type = member.declarationType()
        val def2 = C_NsAsm_RawDef_Simple(member)
        addEntry(name, type, C_NsAsm_MutableDef_Simple(def2), C_NsAsm_Identity_Def(def2))
    }

    override fun addNamespace(
        name: C_Name,
        merge: Boolean,
        ideInfo: C_IdeSymbolInfo,
        deprecated: C_Deprecated?,
    ): C_NsAsm_ComponentAssembler {
        checkCanModify()
        val asm = createSubAssembler(name)
        val defName = nsKey.toDefPath().subName(name.rName)
        val def = C_NsAsm_MutableDef_Namespace(asm, merge, defName, ideInfo, deprecated)
        addEntry(name, C_DeclarationType.NAMESPACE, def, C_NsAsm_Identity_Unique())
        return asm
    }

    override fun addModuleImport(alias: C_Name, module: C_ModuleDescriptor, ideInfo: C_IdeSymbolInfo) {
        checkCanModify()
        val asm = createSubAssembler(alias)
        asm.addWildcardImport(module.key, immListOf())
        val ns = asm.assemble()
        val defName = nsKey.toDefPath().subName(alias.rName)
        val def = C_NsAsm_RawDef_Namespace(ns, false, defName, ideInfo, null, module)
        val mutDef = C_NsAsm_MutableDef_Simple(def)
        addEntry(alias, C_DeclarationType.IMPORT, mutDef, C_NsAsm_Identity_ModuleImport(module.key))
    }

    private fun createSubAssembler(name: C_Name): C_NsAsm_InternalComponentAssembler {
        val subNsKey = nsKey.sub(name.rName)
        return C_NsAsm_InternalComponentAssembler(executor, nsLinker, subNsKey, stamp)
    }

    override fun addExactImport(
        alias: C_Name,
        module: C_ModuleKey,
        qNameHand: C_QualifiedNameHandle,
        aliasPair: Pair<C_NameHandle, DocSymbolTransformer>?,
    ) {
        val namesAlias = if (aliasPair == null) null else {
            val aliasHand = aliasPair.first
            val defName = nsKey.toDefPath().subName(aliasHand.rName)
            val qualifiedName = nsKey.path.qualifiedName(aliasHand.rName)
            val ideId = S_ExactImportTargetItem.makeAliasIdeId(qualifiedName, aliasHand.name, IdeSymbolCategory.IMPORT)
            C_NsAsm_ExactImportNames.Alias(defName, ideId, aliasHand, aliasPair.second)
        }

        val names = C_NsAsm_ExactImportNames(executor, qNameHand, namesAlias)
        val imp = C_NsAsm_ExactImport(module, qNameHand.cName)
        val def = C_NsAsm_RawDef_ExactImport(imp, names)
        val mutDef = C_NsAsm_MutableDef_Simple(def)

        addEntry(alias, C_DeclarationType.IMPORT, mutDef, C_NsAsm_Identity_ExactImport(imp))
    }

    override fun addWildcardImport(module: C_ModuleKey, pathHands: ImmList<C_NameHandle>) {
        checkCanModify()
        val path = pathHands.mapToImmList { it.name }
        val names = C_NsAsm_WildcardImportNames(executor, pathHands)
        wildcardImports.add(C_NsAsm_RawWildcardImport(module, path, names, stamp))
    }

    fun assemble(): C_NsAsm_RawNamespace {
        finish()
        val resEntries = entries.mapToImmList { it.assemble() }
        val resWildcardImports = wildcardImports.toImmList()
        return C_NsAsm_RawNamespace(resEntries, resWildcardImports)
    }
}

private class C_NsAsm_InternalReplAssembler(
        nsLinker: C_NsAsm_NamespaceLinker,
        nsKey: C_NsAsm_NamespaceKey,
        executor: C_CompilerExecutor,
        private val msgCtx: C_MessageContext,
        private val sysNsProto: C_SysNsProto,
        private val stamp: R_AppUid,
        private val linkedModule: C_ModuleKey?,
        private val oldState: C_NsAsm_InternalReplState,
): C_NsAsm_ReplAssembler {
    val comAsm = C_NsAsm_InternalComponentAssembler(executor, nsLinker, nsKey, stamp)
    private var componentAdded = false

    private val nsGetter: Getter<C_Namespace> = if (linkedModule != null) {
        val linkedContainer = C_ModuleContainerKey.of(linkedModule)
        val linkedNsKey = C_NsAsm_NamespaceKey(linkedContainer, C_RNamePath.EMPTY)
        nsLinker.getLink(linkedNsKey)
    } else {
        { C_Namespace.EMPTY }
    }

    override fun futureNs() = nsGetter

    override fun addComponent(): C_NsAsm_ComponentAssembler {
        check(!componentAdded)
        componentAdded = true
        return comAsm
    }

    fun assemble(modules: Map<C_ModuleKey, C_NsAsm_Module>): C_NsAsm_InternalReplState {
        val conflictsProcessor = C_NsAsm_ConflictsProcessor(msgCtx, sysNsProto, stamp)

        var newRawNs = comAsm.assemble()
        newRawNs = conflictsProcessor.mergeAndProcess(newRawNs)

        var combinedRawNs = C_NsAsm_Utils.concatNamespaces(listOf(oldState.rawNs, newRawNs))
        combinedRawNs = conflictsProcessor.mergeAndProcess(combinedRawNs)

        if (linkedModule != null) {
            val linkedModule = modules[linkedModule]
            if (linkedModule != null) {
                val rawNs = C_NsAsm_Utils.concatNamespaces(listOf(linkedModule.rawNs, combinedRawNs))
                conflictsProcessor.process(rawNs)
            }
        }

        val newNs = newRawNs.assemble(immListOf())
        val impModules = modules.mapValuesToImmMap { (_, v) -> v.impNs }
        val newImpNs = C_NsImp_ImportsProcessor.process(msgCtx, newNs, impModules)
        val combinedImpNs = mergeReplNamespace(oldState.impNs, newImpNs)

        val ns = C_NsRes_ResultMaker.make(combinedImpNs)
        return C_NsAsm_InternalReplState(combinedRawNs, combinedImpNs, ns)
    }

    private fun mergeReplNamespace(oldNs: C_NsImp_Namespace, newNs: C_NsImp_Namespace): C_NsImp_Namespace {
        val directNames = Sets.union(oldNs.directDefs.keys, newNs.directDefs.keys)
        val directDefs = directNames.associateWithToImmList {
            val oldDef = oldNs.directDefs[it]
            val newDef = newNs.directDefs[it]
            mergeReplDef(oldDef, newDef)
        }

        val importDefs = mutableMultimapOf<R_Name, C_NsImp_Def>()
        importDefs.putAll(oldNs.importDefs)
        importDefs.putAll(newNs.importDefs)

        return C_NsImp_Namespace(directDefs, importDefs.toImmMultimap())
    }

    private fun mergeReplDef(oldDef: C_NsImp_Def?, newDef: C_NsImp_Def?): C_NsImp_Def {
        if (oldDef == null) {
            return newDef!!
        } else if (newDef == null) {
            return oldDef
        }

        return if (oldDef is C_NsImp_Def_Namespace && newDef is C_NsImp_Def_Namespace) {
            val oldNs = oldDef.ns()
            val newNs = newDef.ns()
            val resNs = mergeReplNamespace(oldNs, newNs)
            val resDeprecated = oldDef.deprecated ?: newDef.deprecated
            val getter = LateGetter.of(resNs)
            C_NsImp_Def_Namespace(getter, oldDef.defName, oldDef.ideInfo, resDeprecated)
        } else {
            oldDef
        }
    }
}

private class C_NsAsm_RawModule(val rawNs: C_NsAsm_RawNamespace, val ns: C_NsAsm_Namespace)

class C_NsAsm_RawNamespace(
    val entries: ImmList<C_NsAsm_RawEntry>,
    val wildcardImports: ImmList<C_NsAsm_RawWildcardImport>,
) {
    fun addToDefs(b: C_ModuleDefsBuilder) {
        for (entry in entries) {
            entry.def.addToDefs(b)
        }
    }

    fun filterByStamp(stamp: R_AppUid): C_NsAsm_RawNamespace {
        val resEntries = entries.mapNotNullToImmList { it.filterByStamp(stamp) }
        val resWildcards = wildcardImports.filterToImmList { it.stamp == stamp}
        return C_NsAsm_RawNamespace(resEntries, resWildcards)
    }

    fun assemble(sysEntries: List<C_NsEntry>): C_NsAsm_Namespace {
        val defs = mutableMapOf<R_Name, C_NsAsm_Def>()

        for (entry in sysEntries) {
            defs[entry.name] = C_NsAsm_Def_Simple(entry.member)
        }

        for (entry in entries) {
            val name = entry.name.rName
            val def = entry.def.assemble()
            if (name !in defs) defs[name] = def
        }

        val rawImports = makeUnique(wildcardImports)
        val resImports = rawImports.mapToImmList { it.assemble() }

        return C_NsAsm_Namespace(defs.toImmMap(), immMultimapOf(), resImports)
    }

    private fun makeUnique(imports: List<C_NsAsm_RawWildcardImport>): List<C_NsAsm_RawWildcardImport> {
        val list = mutableListOf<C_NsAsm_RawWildcardImport>()
        val set = mutableSetOf<ImportKey>()
        for (imp in imports) {
            val key = ImportKey(imp.module, imp.path.mapToImmList { it.rName })
            if (set.add(key)) list.add(imp)
        }
        return list
    }

    private data class ImportKey(val module: C_ModuleKey, val path: ImmList<R_Name>)

    companion object { val EMPTY = C_NsAsm_RawNamespace(immListOf(), immListOf()) }
}

class C_NsAsm_RawEntry(
        val name: C_Name,
        val type: C_DeclarationType,
        val def: C_NsAsm_RawDef,
        val identity: C_NsAsm_Identity,
        val container: C_ContainerKey,
        val stamp: R_AppUid
) {
    fun filterByStamp(targetStamp: R_AppUid): C_NsAsm_RawEntry? {
        if (stamp != targetStamp) {
            return null
        }
        val resDef = def.filterByStamp(targetStamp)
        return if (resDef === def) this else {
            C_NsAsm_RawEntry(name, type, resDef, identity, container, stamp)
        }
    }
}

class C_NsAsm_RawWildcardImport(
        val module: C_ModuleKey,
        val path: ImmList<C_Name>,
        private val names: C_NsAsm_WildcardImportNames,
        val stamp: R_AppUid
) {
    fun assemble() = C_NsAsm_WildcardImport(module, path, names)
}

sealed class C_NsAsm_RawDef {
    abstract fun filterByStamp(targetStamp: R_AppUid): C_NsAsm_RawDef
    open fun addToDefs(b: C_ModuleDefsBuilder) {}
    abstract fun assemble(): C_NsAsm_Def
}

private class C_NsAsm_RawDef_Simple(private val member: C_NamespaceMember): C_NsAsm_RawDef() {
    override fun filterByStamp(targetStamp: R_AppUid) = this

    override fun addToDefs(b: C_ModuleDefsBuilder) = member.addToDefs(b)

    override fun assemble(): C_NsAsm_Def {
        return C_NsAsm_Def_Simple(member)
    }
}

private class C_NsAsm_RawDef_Namespace(
    val ns: C_NsAsm_RawNamespace,
    val merge: Boolean,
    val defName: C_DefinitionName,
    val ideInfo: C_IdeSymbolInfo,
    val deprecated: C_Deprecated?,
    val importModule: C_ModuleDescriptor? = null,
): C_NsAsm_RawDef() {
    override fun filterByStamp(targetStamp: R_AppUid): C_NsAsm_RawDef {
        val resNs = ns.filterByStamp(targetStamp)
        return if (resNs === ns) this else {
            C_NsAsm_RawDef_Namespace(resNs, merge, defName, ideInfo, deprecated, importModule)
        }
    }

    override fun addToDefs(b: C_ModuleDefsBuilder) = ns.addToDefs(b)

    override fun assemble(): C_NsAsm_Def {
        val ns2 = ns.assemble(immListOf())
        return C_NsAsm_Def_Namespace.createDirect(ns2, defName, deprecated, ideInfo, importModule)
    }
}

private class C_NsAsm_RawDef_ExactImport(
    private val imp: C_NsAsm_ExactImport,
    private val names: C_NsAsm_ExactImportNames,
): C_NsAsm_RawDef() {
    override fun filterByStamp(targetStamp: R_AppUid) = this
    override fun assemble() = C_NsAsm_Def_ExactImport(imp, names)
}

sealed class C_NsAsm_Identity

private class C_NsAsm_Identity_Unique: C_NsAsm_Identity()

private class C_NsAsm_Identity_Def(private val def: C_NsAsm_RawDef): C_NsAsm_Identity() {
    override fun equals(other: Any?) = other is C_NsAsm_Identity_Def && def == other.def
    override fun hashCode() = def.hashCode()
}

private class C_NsAsm_Identity_ModuleImport(private val module: C_ModuleKey): C_NsAsm_Identity() {
    override fun equals(other: Any?) = other is C_NsAsm_Identity_ModuleImport && module == other.module
    override fun hashCode() = module.hashCode()
}

private class C_NsAsm_Identity_ExactImport(imp: C_NsAsm_ExactImport): C_NsAsm_Identity() {
    private val module = imp.module
    private val qName = imp.qName.toRName()

    override fun equals(other: Any?) = other is C_NsAsm_Identity_ExactImport
            && module == other.module && qName == other.qName
    override fun hashCode() = Objects.hash(module, qName)
}

private class C_NsAsm_InternalModuleAssembler(
    private val executor: C_CompilerExecutor,
    private val msgCtx: C_MessageContext,
    private val sysNsProto: C_SysNsProto,
    private val exportSysEntities: Boolean,
    nsLinker: C_NsAsm_NamespaceLinker,
    nsKey: C_NsAsm_NamespaceKey,
    stamp: R_AppUid,
): C_NsAsm_InternalBasicAssembler(nsLinker, nsKey, stamp), C_NsAsm_ModuleAssembler {
    private val modDefsLate = C_LateInit(C_CompilerPass.NAMESPACES, C_ModuleDefs.EMPTY)

    private val components = mutableListOf<C_NsAsm_InternalComponentAssembler>()

    override fun futureDefs() = modDefsLate.getter

    override fun addComponent(): C_NsAsm_ComponentAssembler {
        checkCanModify()
        val asm = C_NsAsm_InternalComponentAssembler(executor, nsLinker, nsKey, stamp)
        components.add(asm)
        return asm
    }

    fun assemble(): C_NsAsm_RawModule {
        finish()

        val conflictsProcessor = C_NsAsm_ConflictsProcessor(msgCtx, sysNsProto, stamp)

        val componentNss = components.map {
            val rawNs = it.assemble()
            conflictsProcessor.mergeAndProcess(rawNs)
        }

        var modNs = C_NsAsm_Utils.concatNamespaces(componentNss)

        val fullModNs = conflictsProcessor.mergeAndProcess(modNs)
        val modDefs = createModuleDefs(fullModNs)
        modDefsLate.set(modDefs)

        modNs = fullModNs.filterByStamp(stamp)

        val resSysEntries = if (exportSysEntities) sysNsProto.entities else immListOf()
        val resNs = modNs.assemble(resSysEntries)

        return C_NsAsm_RawModule(fullModNs, resNs)
    }

    private fun createModuleDefs(ns: C_NsAsm_RawNamespace): C_ModuleDefs {
        val defsBuilder = C_ModuleDefsBuilder()
        ns.addToDefs(defsBuilder)
        return defsBuilder.build()
    }
}

private class C_NsAsm_InternalAppAssembler(
    private val executor: C_CompilerExecutor,
    private val msgCtx: C_MessageContext,
    private val stamp: R_AppUid,
    private val preModules: ImmMap<C_ModuleKey, C_PrecompiledModule>,
): C_NsAsm_InternalAssembler(), C_NsAsm_AppAssembler {
    private val nsLinker = C_NsAsm_InternalNamespaceLinker(msgCtx.globalCtx.compilerOptions)

    private val moduleAsms = mutableMapOf<C_ModuleKey, C_NsAsm_InternalModuleAssembler>()
    private var replAsm: C_NsAsm_InternalReplAssembler? = null

    override fun addModule(
        moduleKey: C_ModuleKey,
        sysNsProto: C_SysNsProto,
        exportSysEntities: Boolean,
    ): C_NsAsm_ModuleAssembler {
        checkCanModify()
        check(moduleKey !in moduleAsms) { moduleKey }
        check(moduleKey !in preModules) { moduleKey }

        val containerKey = C_ModuleContainerKey.of(moduleKey)
        val nsKey = C_NsAsm_NamespaceKey(containerKey)

        val moduleAsm = C_NsAsm_InternalModuleAssembler(
                executor,
                msgCtx,
                sysNsProto,
                exportSysEntities,
                nsLinker,
                nsKey,
                stamp
        )

        moduleAsms[moduleKey] = moduleAsm
        return moduleAsm
    }

    override fun addRepl(
        sysNsProto: C_SysNsProto,
        linkedModule: C_ModuleKey?,
        oldState: C_NsAsm_ReplState
    ): C_NsAsm_ReplAssembler {
        checkCanModify()
        check(replAsm == null)

        val nsKey = C_NsAsm_NamespaceKey(C_ReplContainerKey)

        val replAsm = C_NsAsm_InternalReplAssembler(
            nsLinker,
            nsKey,
            executor,
            msgCtx,
            sysNsProto,
            stamp,
            linkedModule,
            oldState as C_NsAsm_InternalReplState,
        )

        this.replAsm = replAsm
        return replAsm
    }

    override fun assemble(): C_NsAsm_App {
        finish()

        val modules = moduleAsms.mapValues { (_, v) -> v.assemble() }
        val nsModules = modules.mapValuesToImmMap { (_, v) -> v.ns }
        val preImpModules = preModules.mapValuesToImmMap { (_, v) -> v.asmModule.impNs }
        val impModules = C_NsImp_ImportsProcessor.process(msgCtx, nsModules, preImpModules)

        val resModules = C_NsRes_ResultMaker.make(impModules)
        val appModules = createAppModules(modules, impModules, resModules)
        val replState = replAsm?.assemble(appModules) ?: C_NsAsm_InternalReplState.EMPTY

        val linkModules = preModules.map { (k, v) -> C_ModuleContainerKey.of(k) to v.asmModule.ns }.toMap() +
                resModules.mapKeys { (k, _) -> C_ModuleContainerKey.of(k) } +
                mapOf(C_ReplContainerKey to replState.ns)
        nsLinker.setModules(linkModules)

        return C_NsAsm_App(replState, appModules)
    }

    private fun createAppModules(
            rawModules: Map<C_ModuleKey, C_NsAsm_RawModule>,
            impModules: Map<C_ModuleKey, C_NsImp_Namespace>,
            resModules: Map<C_ModuleKey, C_Namespace>
    ): ImmMap<C_ModuleKey, C_NsAsm_Module> {
        checkEquals(impModules.keys, rawModules.keys)
        checkEquals(resModules.keys, rawModules.keys)
        checkEquals(Sets.intersection(rawModules.keys, preModules.keys), setOf<C_ModuleKey>())

        val oldModules = preModules.mapValuesToImmMap { (_, v) -> v.asmModule }
        val newModules = rawModules.mapValues { (k, v) -> C_NsAsm_Module(v.rawNs, impModules.getValue(k), resModules.getValue(k)) }
        return oldModules + newModules
    }
}

private class C_NsAsm_InternalReplState(
        val rawNs: C_NsAsm_RawNamespace,
        val impNs: C_NsImp_Namespace,
        val ns: C_Namespace
): C_NsAsm_ReplState() {
    companion object {
        val EMPTY = C_NsAsm_InternalReplState(C_NsAsm_RawNamespace.EMPTY, C_NsImp_Namespace.EMPTY, C_Namespace.EMPTY)
    }
}

private class C_NsAsm_ConflictsProcessor(
    private val msgCtx: C_MessageContext,
    sysNsProto: C_SysNsProto,
    private val stamp: R_AppUid,
) {
    private val sysDefs: ImmMap<R_Name, C_DeclarationType> = let {
        val mutSysDefs = mutableMapOf<R_Name, C_DeclarationType>()

        for (entry in sysNsProto.entries) {
            if (entry.name !in mutSysDefs) {
                mutSysDefs[entry.name] = entry.member.declarationType()
            }
        }

        mutSysDefs.toImmMap()
    }

    private val errors = mutableSetOf<S_Pos>()

    fun process(ns: C_NsAsm_RawNamespace): C_NsAsm_RawNamespace {
        return C_NsAsm_Utils.transformNamespaces(ns, false, stamp) { oldNs, newNs ->
            process0(newNs, oldNs === ns)
        }
    }

    fun mergeAndProcess(ns: C_NsAsm_RawNamespace): C_NsAsm_RawNamespace {
        val mergedNs = C_NsAsm_Utils.mergeNamespaces(ns, stamp)
        return process(mergedNs)
    }

    private fun process0(ns: C_NsAsm_RawNamespace, checkSysDefs: Boolean): C_NsAsm_RawNamespace {
        val map = mutableMapOf<String, C_NsAsm_RawEntry>()
        for (entry in ns.entries) {
            addEntry(map, entry, checkSysDefs)
        }
        return C_NsAsm_RawNamespace(map.values.toImmList(), ns.wildcardImports)
    }

    private fun addEntry(map: MutableMap<String, C_NsAsm_RawEntry>, entry: C_NsAsm_RawEntry, checkSysDefs: Boolean) {
        val name = entry.name

        val sysDefType = sysDefs[name.rName]
        if (sysDefType != null && checkSysDefs) {
            report(name, entry.stamp, sysDefType, null)
            return
        }

        val oldEntry = map[name.str]
        if (oldEntry != null) {
            if (oldEntry.identity != entry.identity) {
                report(oldEntry, entry)
                report(entry, oldEntry)
            }
            return
        }

        map[name.str] = entry
    }

    private fun report(entry: C_NsAsm_RawEntry, otherEntry: C_NsAsm_RawEntry) {
        if (entry.container == C_ReplContainerKey || otherEntry.container != C_ReplContainerKey) {
            report(entry.name, entry.stamp, otherEntry.type, otherEntry.name)
        }
    }

    private fun report(name: C_Name, entryStamp: R_AppUid, otherType: C_DeclarationType, otherName: C_Name?) {
        if (entryStamp == stamp && errors.add(name.pos)) {
            msgCtx.error(C_Errors.errNameConflict(name, otherType, otherName?.pos))
        }
    }
}

private data class C_NsAsm_NamespaceKey(val container: C_ContainerKey, val path: C_RNamePath = C_RNamePath.EMPTY) {
    fun sub(name: R_Name): C_NsAsm_NamespaceKey {
        val subPath = path.append(name)
        return C_NsAsm_NamespaceKey(container, subPath)
    }

    fun toDefPath(): C_DefinitionPath {
        return C_DefinitionPath(container.defModuleName(), path.parts.mapToImmList { it.str })
    }
}

private sealed class C_NsAsm_NamespaceLinker {
    abstract fun getLink(nsKey: C_NsAsm_NamespaceKey): Getter<C_Namespace>
}

private class C_NsAsm_InternalNamespaceLinker(
    compilerOptions: C_CompilerOptions,
): C_NsAsm_NamespaceLinker() {
    private val langVersion = compilerOptions.compatibility

    private val links = mutableMapOf<C_NsAsm_NamespaceKey, NamespaceLink>()
    private val containerNamespaces = mutableMapOf<C_ContainerKey, C_Namespace>()
    private var completed = false

    override fun getLink(nsKey: C_NsAsm_NamespaceKey): Getter<C_Namespace> {
        check(!completed)
        val link = links.computeIfAbsent(nsKey) { NamespaceLink(it) }
        return link.getter
    }

    fun setModules(containers: Map<C_ContainerKey, C_Namespace>) {
        check(!completed)
        completed = true
        containerNamespaces.putAll(containers)
    }

    private fun resolveNamespace(nsKey: C_NsAsm_NamespaceKey): C_Namespace {
        var ns = containerNamespaces[nsKey.container]
        for (name in nsKey.path.parts) {
            if (ns == null) return C_Namespace.EMPTY
            ns = ns.getElement(name, langVersion, C_NamespaceMemberTag.NAMESPACE.list)?.member?.getNamespaceOpt()
        }
        return ns ?: C_Namespace.EMPTY
    }

    private inner class NamespaceLink(private val nsKey: C_NsAsm_NamespaceKey) {
        val getter: Getter<C_Namespace> = {
            get()
        }

        private var ns: C_Namespace? = null

        private fun get(): C_Namespace {
            var res = ns
            if (res == null) {
                res = resolveNamespace(nsKey)
                ns = res
            }
            return res
        }
    }
}

private object C_NsAsm_Utils {
    fun mergeNamespaces(ns: C_NsAsm_RawNamespace, stamp: R_AppUid): C_NsAsm_RawNamespace {
        return transformNamespaces(ns, true, stamp) { oldNs, _ ->
            mergeNamespaces0(oldNs, stamp)
        }
    }

    private fun mergeNamespaces0(ns: C_NsAsm_RawNamespace, stamp: R_AppUid): C_NsAsm_RawNamespace {
        val namespaces = mutableMapOf<String, MutableList<C_NsAsm_RawDef_Namespace>>()
        for (entry in ns.entries) {
            if (entry.def is C_NsAsm_RawDef_Namespace && entry.def.merge) {
                val list = namespaces.computeIfAbsent(entry.name.str) { mutableListOf() }
                list.add(entry.def)
            }
        }

        val entries = mutableListOf<C_NsAsm_RawEntry>()
        for (entry in ns.entries) {
            if (entry.def is C_NsAsm_RawDef_Namespace && entry.def.merge) {
                val list = namespaces.remove(entry.name.str)
                if (list != null) {
                    val mergedEntry = mergeNamespaceDefs(entry, entry.def, list, stamp)
                    entries.add(mergedEntry)
                }
            } else {
                entries.add(entry)
            }
        }

        return C_NsAsm_RawNamespace(entries.toImmList(), ns.wildcardImports)
    }

    private fun mergeNamespaceDefs(
        entry: C_NsAsm_RawEntry,
        def: C_NsAsm_RawDef_Namespace,
        list: List<C_NsAsm_RawDef_Namespace>,
        stamp: R_AppUid,
    ): C_NsAsm_RawEntry {
        if (list.size < 2) {
            return entry
        }

        val nsEntries = list.flatMapToImmList { it.ns.entries }
        val wildcardImports = list.flatMapToImmList { it.ns.wildcardImports }
        val mergedNs = C_NsAsm_RawNamespace(nsEntries, wildcardImports)

        val mergedIdeInfo = mergeIdeInfos(list)
        val mergedDeprecated = list.firstNotNullOfOrNull { it.deprecated }
        val mergedDef = C_NsAsm_RawDef_Namespace(mergedNs, true, def.defName, mergedIdeInfo, mergedDeprecated)

        return C_NsAsm_RawEntry(entry.name, entry.type, mergedDef, entry.identity, entry.container, stamp)
    }

    private fun mergeIdeInfos(defs: List<C_NsAsm_RawDef_Namespace>): C_IdeSymbolInfo {
        val ideInfos = defs.map { it.ideInfo }
        val firstDef = defs.first()
        return firstDef.ideInfo.transformDocSymbol { doc ->
            val comment = ideInfos.firstNotNullOfOrNull { it.getIdeInfo().doc?.comment }
            DocSymbol(doc.kind, doc.symbolName, doc.mountName, doc.declaration, comment)
        }
    }

    fun concatNamespaces(nss: List<C_NsAsm_RawNamespace>): C_NsAsm_RawNamespace {
        val entries = nss.flatMapToImmList { it.entries }
        val wildcardImports = nss.flatMapToImmList { it.wildcardImports }
        return C_NsAsm_RawNamespace(entries, wildcardImports)
    }

    fun transformNamespaces(
        ns: C_NsAsm_RawNamespace,
        pre: Boolean,
        stamp: R_AppUid,
        f: (C_NsAsm_RawNamespace, C_NsAsm_RawNamespace) -> C_NsAsm_RawNamespace,
    ): C_NsAsm_RawNamespace {
        val preNs = if (pre) f(ns, ns) else ns
        val transNs = transformNamespacesSub(preNs, pre, stamp, f)
        val postNs = if (!pre) f(ns, transNs) else transNs
        return postNs
    }

    private fun transformNamespacesSub(
        ns: C_NsAsm_RawNamespace,
        pre: Boolean,
        stamp: R_AppUid,
        f: (C_NsAsm_RawNamespace, C_NsAsm_RawNamespace) -> C_NsAsm_RawNamespace,
    ): C_NsAsm_RawNamespace {
        val entries = mutableListOf<C_NsAsm_RawEntry>()
        for (entry in ns.entries) {
            val entry2 = if (entry.def !is C_NsAsm_RawDef_Namespace) entry else {
                val ns2 = transformNamespaces(entry.def.ns, pre, stamp, f)
                val def2 = C_NsAsm_RawDef_Namespace(
                    ns2,
                    entry.def.merge,
                    entry.def.defName,
                    entry.def.ideInfo,
                    entry.def.deprecated,
                    entry.def.importModule,
                )
                C_NsAsm_RawEntry(entry.name, entry.type, def2, entry.identity, entry.container, stamp)
            }
            entries.add(entry2)
        }
        return C_NsAsm_RawNamespace(entries.toImmList(), ns.wildcardImports)
    }
}
