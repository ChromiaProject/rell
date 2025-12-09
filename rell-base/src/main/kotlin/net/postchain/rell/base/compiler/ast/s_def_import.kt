/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.modifier.C_ModifierContext
import net.postchain.rell.base.compiler.base.modifier.C_ModifierFields
import net.postchain.rell.base.compiler.base.modifier.C_ModifierTargetType
import net.postchain.rell.base.compiler.base.modifier.C_ModifierValues
import net.postchain.rell.base.compiler.base.module.*
import net.postchain.rell.base.compiler.base.namespace.C_UserNsProtoBuilder
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_QualifiedName
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.*
import net.postchain.rell.base.utils.ide.*

private val INVALID_MODULE_SYMBOL_INFO = C_IdeSymbolInfo.UNKNOWN

internal class C_ImportModulePathHandle(
    val moduleName: R_ModuleName,
    val implicitAlias: C_Name?,
    private val nameHand: C_QualifiedNameHandle?,
    private val pathModuleNames: ImmList<R_ModuleName>,
) {
    init {
        checkEquals(pathModuleNames.size, nameHand?.parts?.size ?: 0)
    }

    fun resolveIdeInfo(target: C_ImportTarget, moduleInfos: Map<R_ModuleName, C_ModuleInfo>) {
        nameHand ?: return

        var valid = false
        for (i in pathModuleNames.indices.reversed()) {
            val modName = pathModuleNames[i]
            val modInfo = moduleInfos[modName]
            var ideInfo = when {
                modInfo != null -> {
                    valid = true
                    makeModuleIdeInfo(target, modInfo)
                }
                valid -> C_IdeSymbolInfo.get(IdeSymbolKind.DEF_IMPORT_MODULE)
                else -> INVALID_MODULE_SYMBOL_INFO
            }
            if (i < pathModuleNames.indices.last) {
                ideInfo = ideInfo.update(defId = null)
            }
            nameHand.parts[i].setIdeInfo(ideInfo)
        }
    }

    private fun makeModuleIdeInfo(target: C_ImportTarget, moduleInfo: C_ModuleInfo): C_IdeSymbolInfo {
        val ideId = target.moduleIdeDefId()
        val ideLink = if (moduleInfo.idePath == null) null else IdeModuleSymbolLink(moduleInfo.idePath)
        return C_IdeSymbolInfo.late(
            IdeSymbolKind.DEF_IMPORT_MODULE,
            defId = ideId,
            link = ideLink,
            docGetter = moduleInfo.docSymbolGetter,
        )
    }
}

class S_RelativeImportModulePath(val pos: S_Pos, val ups: Int)

class S_ImportModulePath(
    private val relative: S_RelativeImportModulePath?,
    private val moduleName: S_QualifiedName?,
) {
    fun ideImplicitAlias() = moduleName?.last

    internal fun compile(
        msgMgr: C_MessageManager,
        symCtx: C_SymbolContext,
        importPos: S_Pos,
        currentModule: R_ModuleName,
    ): C_ImportModulePathHandle? {
        val nameHand = moduleName?.compile(symCtx.nameCtx, def = true)

        val cModuleName = nameHand?.cName
        val modNameDetails = compileModuleName(msgMgr, importPos, currentModule, cModuleName)

        return if (modNameDetails == null) {
            nameHand?.setIdeInfo(INVALID_MODULE_SYMBOL_INFO)
            null
        } else {
            val implicitAlias = cModuleName?.last
            C_ImportModulePathHandle(modNameDetails.moduleName, implicitAlias, nameHand, modNameDetails.pathModuleNames)
        }
    }

    private fun compileModuleName(
            msgMgr: C_MessageManager,
            importPos: S_Pos,
            currentModule: R_ModuleName,
            cModuleName: C_QualifiedName?
    ): ModuleNameDetails? {
        val rPath = cModuleName?.parts?.map { it.rName }.orEmpty()

        if (relative == null) {
            if (rPath.isEmpty()) {
                msgMgr.error(importPos, "import:no_path", "Module not specified")
                return null
            }
            return makeModuleNameDetails(listOf(), rPath)
        }

        if (relative.ups > currentModule.parts.size) {
            val code = "import:up:${currentModule.parts.size}:${relative.ups}"
            val msg = "Cannot go up by ${relative.ups}, current module is '${currentModule}'"
            msgMgr.error(relative.pos, code, msg)
            return null
        }

        val base = currentModule.parts.subList(0, currentModule.parts.size - relative.ups)
        return makeModuleNameDetails(base, rPath)
    }

    private fun makeModuleNameDetails(base: List<R_Name>, path: List<R_Name>): ModuleNameDetails {
        val moduleName = R_ModuleName.of(base + path)
        val pathNames = path.indices.mapToImmList { R_ModuleName.of(base + path.subList(0, it + 1)) }
        return ModuleNameDetails(moduleName, pathNames)
    }

    private class ModuleNameDetails(val moduleName: R_ModuleName, val pathModuleNames: ImmList<R_ModuleName>)
}

class C_ImportAlias(val explicit: C_Name?, val implicit: C_Name?, val anonymous: C_Name?) {
    init {
        check((explicit ?: implicit ?: anonymous) != null)
        if (anonymous != null) {
            check(explicit == null)
            check(implicit == null)
        }
    }
}

internal sealed class C_ImportTarget {
    open fun moduleIdeDefId(): IdeSymbolId? = null

    abstract fun aliasIdeInfo(): C_IdeSymbolInfo
    abstract fun addToNamespace(ctx: C_MountContext, def: C_ImportDefinition, module: C_ModuleDescriptor)

    protected fun getNsBuilder(
        ctx: C_MountContext,
        alias: C_Name?,
        ideInfo: C_IdeSymbolInfo = C_IdeSymbolInfo.get(IdeSymbolKind.DEF_NAMESPACE),
    ): C_UserNsProtoBuilder {
        return if (alias == null) ctx.nsBuilder else ctx.nsBuilder.addNamespace(alias, false, ideInfo, deprecated = null)
    }
}

internal sealed class S_ImportTarget {
    internal abstract fun compile(
        ctx: S_DefinitionContext,
        moduleName: R_ModuleName,
        importAlias: C_ImportAlias?,
        docModifiers: DocModifiers,
        comment: S_Comment?,
    ): C_ImportTarget

    protected fun aliasNamespaceIdeDef(
        ctx: S_DefinitionContext,
        importAlias: C_ImportAlias?,
        comment: S_Comment?,
        docDecMaker: (C_Name) -> Lazy<DocDeclaration>,
    ): C_IdeSymbolDef {
        val anonymous = importAlias?.anonymous
        if (anonymous != null) {
            val msg = "Only a simple module import can be anonymous"
            ctx.msgCtx.error(anonymous.pos, "import:anonymous_not_allowed", msg)
        }

        val explicitAlias = importAlias?.explicit
        var aliasIdeId: IdeSymbolGlobalId? = null
        var doc: DocSymbol? = null

        if (explicitAlias != null) {
            val fullName = ctx.namespacePath.fullName(explicitAlias.rName)
            val id = IdeSymbolId(IdeSymbolCategory.NAMESPACE, fullName.qualifiedName.str())
            aliasIdeId = IdeSymbolGlobalId(ctx.fileCtx.idePath, id)

            val docDec = docDecMaker(explicitAlias)

            doc = ctx.symCtx.makeDocSymbol(
                DocSymbolKind.IMPORT,
                DocSymbolName.global(fullName),
                docDec,
                comment = comment,
            )
        }

        return C_IdeSymbolDef.make(IdeSymbolKind.DEF_NAMESPACE, aliasIdeId, doc)
    }
}

internal data object S_DefaultImportTarget: S_ImportTarget() {
    override fun compile(
        ctx: S_DefinitionContext,
        moduleName: R_ModuleName,
        importAlias: C_ImportAlias?,
        docModifiers: DocModifiers,
        comment: S_Comment?,
    ): C_ImportTarget {
        val explicitAlias = importAlias?.explicit
        val explicitAliasIdeDefId = aliasIdeDefId(ctx, explicitAlias)
        val implicitAliasIdeDefId = if (explicitAlias != null) null else aliasIdeDefId(ctx, importAlias?.implicit)

        val aliasFullName = if (explicitAlias == null) null else ctx.namespacePath.fullName(explicitAlias.rName)

        val actualAlias = importAlias?.explicit ?: importAlias?.implicit

        val docDeclaration = if (actualAlias == null) DocDeclarationProto.NONE else {
            DocDeclarationProto_ImportModule(docModifiers, moduleName, explicitAlias?.rName)
        }
        val docComment = if (importAlias?.explicit == null) null else {
            comment?.compile(ctx.symCtx.docSymbolFactory, DocSymbolKind.ALIAS)
        }

        return C_DefaultImportTarget(
            ctx.symCtx,
            actualAlias,
            importAlias,
            explicitAliasIdeDefId,
            implicitAliasIdeDefId,
            aliasFullName,
            docComment,
            docDeclaration,
        )
    }

    private fun aliasIdeDefId(ctx: S_DefinitionContext, alias: C_Name?): IdeSymbolId? {
        return if (alias == null) null else {
            val qualifiedName = ctx.namespacePath.qualifiedName(alias.rName)
            val nameStr = qualifiedName.str()
            IdeSymbolId(IdeSymbolCategory.IMPORT, nameStr, immListOf())
        }
    }

    private class C_DefaultImportTarget(
        val symCtx: C_SymbolContext,
        val actualAlias: C_Name?,
        val importAlias: C_ImportAlias?,
        val explicitAliasIdeDefId: IdeSymbolId?,
        val implicitAliasIdeDefId: IdeSymbolId?,
        val aliasFullName: R_FullName?,
        val docComment: DocComment?,
        val docDeclaration: DocDeclarationProto,
    ): C_ImportTarget() {
        override fun moduleIdeDefId() = implicitAliasIdeDefId

        override fun aliasIdeInfo(): C_IdeSymbolInfo {
            val docSymbol = if (aliasFullName == null) DocSymbol.NONE else symCtx.docSymbolFactory.makeDocSymbol(
                kind = C_DefinitionType.IMPORT.docKind,
                symbolName = DocSymbolName.global(aliasFullName),
                declaration = docDeclaration.toLazyDeclaration(),
                comment = docComment,
            )
            return C_IdeSymbolInfo.direct(IdeSymbolKind.DEF_IMPORT_ALIAS, defId = explicitAliasIdeDefId, doc = docSymbol)
        }

        override fun addToNamespace(ctx: C_MountContext, def: C_ImportDefinition, module: C_ModuleDescriptor) {
            if (importAlias == null) {
                ctx.msgCtx.error(def.pos, "import:no_alias", "Cannot infer an alias, specify import alias explicitly")
                return
            }

            if (actualAlias != null) {
                val ideInfo = ideSymbolInfo(ctx, module, actualAlias)
                ctx.nsBuilder.addModuleImport(actualAlias, module, ideInfo)
            }
        }

        private fun ideSymbolInfo(ctx: C_MountContext, module: C_ModuleDescriptor, alias: C_Name): C_IdeSymbolInfo {
            val ideKind = if (importAlias?.explicit != null) IdeSymbolKind.EXPR_IMPORT_ALIAS else {
                IdeSymbolKind.DEF_IMPORT_MODULE
            }

            val cDefBase = ctx.defBase(
                alias,
                C_DefinitionType.IMPORT,
                ideKind,
                mountName = null,
                extChain = module.extChain,
                commentProvider = ctx.symCtx.commentProvider(C_LateGetter.const(docComment)),
            )

            cDefBase.setDocDeclaration(docDeclaration)
            return cDefBase.ideRefInfo
        }
    }
}

class S_ExactImportTargetItem(
    private val alias: S_Name?,
    private val name: S_QualifiedName,
    private val wildcard: Boolean,
    private val comment: S_Comment?,
) {
    internal fun addToNamespace(ctx: C_MountContext, nsBuilder: C_UserNsProtoBuilder, details: Details) {
        if (wildcard) {
            addToNsWildcard(ctx, nsBuilder, details)
        } else {
            addToNsDirect(ctx, nsBuilder, details)
        }
    }

    private fun addToNsWildcard(ctx: C_MountContext, nsBuilder: C_UserNsProtoBuilder, details: Details) {
        val aliasHand = alias?.compile(ctx.symCtx, def = true)
        val nameHand = name.compile(ctx.symCtx)

        val nsBuilder2 = if (aliasHand == null) nsBuilder else {
            val qualifiedName = nsBuilder.namespacePath().qualifiedName(aliasHand.rName)
            val fullName = R_FullName(details.currentModuleName, qualifiedName)

            val docDeclaration = DocDeclarationProto_ImportExactAlias_Wildcard(
                details.docModifiers,
                details.targetModule.name,
                nameHand.rName,
                details.importAlias?.rName,
                fullName.last,
            )

            val doc = ctx.symCtx.makeDocSymbol(
                DocSymbolKind.IMPORT,
                DocSymbolName.global(fullName),
                docDeclaration.toLazyDeclaration(),
                comment = comment,
            )

            val ideId = makeAliasIdeId(qualifiedName, aliasHand.name, IdeSymbolCategory.NAMESPACE)
            val ideDef = C_IdeSymbolDef.make(IdeSymbolKind.DEF_NAMESPACE, ideId, doc)
            aliasHand.setIdeInfo(ideDef.defInfo)

            nsBuilder.addNamespace(aliasHand.name, false, ideDef.refInfo, deprecated = null)
        }

        nsBuilder2.addWildcardImport(details.targetModule, nameHand.parts)
    }

    private fun addToNsDirect(ctx: C_MountContext, nsBuilder: C_UserNsProtoBuilder, details: Details) {
        val aliasHand = alias?.compile(ctx.symCtx, def = true)
        val nameHand = name.compile(ctx.symCtx)

        val aliasPair = if (aliasHand == null) null else {
            val qualifiedName = nsBuilder.namespacePath().qualifiedName(aliasHand.rName)
            val fullName = R_FullName(details.currentModuleName, qualifiedName)

            val docTrans: DocSymbolTransformer = { doc ->
                val docDeclaration = DocDeclarationProto_ImportExactAlias_Single(
                    details.docModifiers,
                    details.targetModule.name,
                    nameHand.rName,
                    details.importAlias?.rName,
                    fullName.last,
                    doc.declaration,
                )

                ctx.symCtx.makeDocSymbol(
                    DocSymbolKind.IMPORT,
                    DocSymbolName.global(fullName),
                    docDeclaration.toLazyDeclaration(),
                    comment = comment,
                )
            }

            aliasHand to docTrans
        }

        val realAlias = aliasHand ?: nameHand.last
        nsBuilder.addExactImport(realAlias.name, details.targetModule, nameHand, aliasPair)
    }

    internal class Details(
        val docModifiers: DocModifiers,
        val importAlias: C_Name?,
        val currentModuleName: R_ModuleName,
        val targetModule: C_ModuleKey,
    )

    companion object {
        fun makeAliasIdeId(fullName: R_QualifiedName, alias: C_Name, category: IdeSymbolCategory): IdeSymbolGlobalId {
            val ideId = IdeSymbolId(category, fullName.str())
            return IdeSymbolGlobalId(alias.pos.idePath(), ideId)
        }
    }
}

internal class S_ExactImportTarget(private val items: ImmList<S_ExactImportTargetItem>): S_ImportTarget() {
    override fun compile(
        ctx: S_DefinitionContext,
        moduleName: R_ModuleName,
        importAlias: C_ImportAlias?,
        docModifiers: DocModifiers,
        comment: S_Comment?,
    ): C_ImportTarget {
        val aliasIdeDef = aliasNamespaceIdeDef(ctx, importAlias, comment) { expAlias ->
            DocDeclarationProto_ImportExactModule(docModifiers, moduleName, expAlias.rName).toLazyDeclaration()
        }
        return C_ExactImportTarget(docModifiers, importAlias?.explicit, aliasIdeDef)
    }

    private inner class C_ExactImportTarget(
        val docModifiers: DocModifiers,
        val explicitAlias: C_Name?,
        val aliasIdeDef: C_IdeSymbolDef,
    ): C_ImportTarget() {
        override fun aliasIdeInfo() = aliasIdeDef.defInfo

        override fun addToNamespace(ctx: C_MountContext, def: C_ImportDefinition, module: C_ModuleDescriptor) {
            val nsBuilder = getNsBuilder(ctx, explicitAlias, aliasIdeDef.refInfo)
            val details = S_ExactImportTargetItem.Details(docModifiers, explicitAlias, ctx.modCtx.moduleName, module.key)
            for (item in items) {
                item.addToNamespace(ctx, nsBuilder, details)
            }
        }
    }
}

internal data object S_WildcardImportTarget: S_ImportTarget() {
    override fun compile(
        ctx: S_DefinitionContext,
        moduleName: R_ModuleName,
        importAlias: C_ImportAlias?,
        docModifiers: DocModifiers,
        comment: S_Comment?,
    ): C_ImportTarget {
        val aliasIdeDef = aliasNamespaceIdeDef(ctx, importAlias, comment) { expAlias ->
            DocDeclarationProto_ImportWildcard(docModifiers, moduleName, expAlias.rName).toLazyDeclaration()
        }
        return C_WildcardImportTarget(importAlias?.explicit, aliasIdeDef)
    }

    private class C_WildcardImportTarget(
        val explicitAlias: C_Name?,
        val aliasIdeDef: C_IdeSymbolDef,
    ): C_ImportTarget() {
        override fun aliasIdeInfo() = aliasIdeDef.defInfo

        override fun addToNamespace(ctx: C_MountContext, def: C_ImportDefinition, module: C_ModuleDescriptor) {
            val nsBuilder = getNsBuilder(ctx, explicitAlias, aliasIdeDef.refInfo)
            nsBuilder.addWildcardImport(module.key, immListOf())
        }
    }
}

class S_ImportDefinition internal constructor(
    base: S_DefinitionBase,
    private val alias: S_Name?,
    private val modulePath: S_ImportModulePath,
    private val target: S_ImportTarget,
): S_Definition(base) {
    override fun compile(ctx: S_DefinitionContext): C_MidModuleMember? {
        val modifierCtx = C_ModifierContext(ctx.msgCtx, ctx.symCtx)
        val mods = C_ModifierValues(C_ModifierTargetType.IMPORT, null)
        val modExternal = mods.field(C_ModifierFields.EXTERNAL_CHAIN)
        val docModifiers = modifiers.compile(modifierCtx, mods)

        val cModulePath = modulePath.compile(ctx.msgCtx, ctx.symCtx, kwPos, ctx.moduleName)
        cModulePath ?: return null

        val moduleName = cModulePath.moduleName

        val cAliasHand = alias?.compile(ctx.symCtx, def = true)

        val importAlias = compileImportAlias(ctx, cAliasHand, cModulePath)
        val cTarget = target.compile(ctx, moduleName, importAlias, docModifiers, comment)

        val moduleInfos = try {
            ctx.appCtx.importLoader.loadModuleEx(moduleName)
        } catch (e: C_CommonError) {
            ctx.msgCtx.error(kwPos, e.code, e.msg)
            cModulePath.resolveIdeInfo(cTarget, immMapOf())
            return null
        }

        cModulePath.resolveIdeInfo(cTarget, moduleInfos)

        if (cAliasHand != null) {
            val aliasIdeInfo = if (importAlias?.anonymous == null) cTarget.aliasIdeInfo() else C_IdeSymbolInfo.UNKNOWN
            cAliasHand.setIdeInfo(aliasIdeInfo)
        }

        val importDef = C_ImportDefinition(kwPos)
        return C_MidModuleMember_Import(importDef, cTarget, moduleName, modExternal.value())
    }

    private fun compileImportAlias(
        ctx: S_DefinitionContext,
        cAliasHand: C_NameHandle?,
        cModulePath: C_ImportModulePathHandle,
    ): C_ImportAlias? {
        return when {
            cAliasHand?.str == "_" -> {
                ANONYMOUS_IMPORT_RESTRICTIONS.access(ctx.msgCtx, cAliasHand.pos)
                C_ImportAlias(null, null, cAliasHand.name)
            }
            cAliasHand != null || cModulePath.implicitAlias != null -> {
                C_ImportAlias(cAliasHand?.name, cModulePath.implicitAlias, null)
            }
            else -> null
        }
    }

    override fun ideGetImportedModules(moduleName: R_ModuleName, res: MutableSet<R_ModuleName>) {
        val msgMgr = C_DefaultMessageManager()
        val symCtxMgr = C_SymbolContextManager(msgMgr, C_CompilerOptions.DEFAULT)
        val symCtx = symCtxMgr.provider.getNopSymbolContext()
        val cModulePath = modulePath.compile(msgMgr, symCtx, kwPos, moduleName)
        if (cModulePath != null) {
            res.add(cModulePath.moduleName)
        }
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        val alias = alias ?: modulePath.ideImplicitAlias()
        if (alias != null) {
            b.node(this, alias, IdeOutlineNodeType.IMPORT)
        }
    }

    companion object {
        private val ANONYMOUS_IMPORT_RESTRICTIONS = C_FeatureRestrictions.make(
            "0.13.12",
            "anonymous_import" toCodeMsg "Anonymous imports",
        )
    }
}
