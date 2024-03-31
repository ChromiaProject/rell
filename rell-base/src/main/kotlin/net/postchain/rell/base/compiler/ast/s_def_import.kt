/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.modifier.C_ModifierContext
import net.postchain.rell.base.compiler.base.modifier.C_ModifierFields
import net.postchain.rell.base.compiler.base.modifier.C_ModifierTargetType
import net.postchain.rell.base.compiler.base.modifier.C_ModifierValues
import net.postchain.rell.base.compiler.base.module.*
import net.postchain.rell.base.compiler.base.namespace.C_UserNsProtoBuilder
import net.postchain.rell.base.compiler.base.utils.C_CommonError
import net.postchain.rell.base.compiler.base.utils.C_DefaultMessageManager
import net.postchain.rell.base.compiler.base.utils.C_MessageManager
import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_QualifiedName
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.doc.*
import net.postchain.rell.base.utils.ide.*
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.immMapOf

private val INVALID_MODULE_SYMBOL_INFO = C_IdeSymbolInfo.UNKNOWN

class C_ImportModulePathHandle(
        val moduleName: R_ModuleName,
        val implicitAlias: C_Name?,
        private val nameHand: C_QualifiedNameHandle?,
        private val pathModuleNames: List<R_ModuleName>,
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
        private val moduleName: S_QualifiedName?
) {
    fun ideImplicitAlias() = moduleName?.last

    fun compile(
        msgMgr: C_MessageManager,
        symCtx: C_SymbolContext,
        importPos: S_Pos,
        currentModule: R_ModuleName,
    ): C_ImportModulePathHandle? {
        val nameHand = moduleName?.compile(symCtx)

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
        val rPath = cModuleName?.parts?.map { it.rName } ?: immListOf()

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
        val pathNames = path.indices.map { R_ModuleName.of(base + path.subList(0, it + 1)) }
        return ModuleNameDetails(moduleName, pathNames)
    }

    private class ModuleNameDetails(val moduleName: R_ModuleName, val pathModuleNames: List<R_ModuleName>)
}

sealed class C_ImportTarget {
    open fun moduleIdeDefId(): IdeSymbolId? = null

    abstract fun aliasIdeInfo(): C_IdeSymbolInfo
    abstract fun addToNamespace(ctx: C_MountContext, def: C_ImportDefinition, module: C_ModuleKey)

    protected fun getNsBuilder(
        ctx: C_MountContext,
        alias: C_Name?,
        ideInfo: C_IdeSymbolInfo = C_IdeSymbolInfo.get(IdeSymbolKind.DEF_NAMESPACE),
    ): C_UserNsProtoBuilder {
        return if (alias == null) ctx.nsBuilder else ctx.nsBuilder.addNamespace(alias, false, ideInfo, deprecated = null)
    }
}

sealed class S_ImportTarget {
    abstract fun compile(
        ctx: S_DefinitionContext,
        moduleName: R_ModuleName,
        explicitAlias: C_Name?,
        implicitAlias: C_Name?,
        docModifiers: DocModifiers,
    ): C_ImportTarget

    protected fun aliasNamespaceIdeDef(
        ctx: S_DefinitionContext,
        explicitAlias: C_Name?,
        docDecMaker: (C_Name) -> DocDeclaration,
    ): C_IdeSymbolDef {
        var aliasIdeId: IdeSymbolGlobalId? = null
        var doc: DocSymbol? = null

        if (explicitAlias != null) {
            val fullName = ctx.namespacePath.qualifiedName(explicitAlias.rName)
            val id = IdeSymbolId(IdeSymbolCategory.NAMESPACE, fullName.str())
            aliasIdeId = IdeSymbolGlobalId(ctx.fileCtx.idePath, id)

            val docDec = docDecMaker(explicitAlias)
            doc = ctx.docFactory.makeDocSymbol(
                DocSymbolKind.IMPORT,
                DocSymbolName.global(ctx.moduleName.str(), fullName.str()),
                docDec,
            )
        }

        return C_IdeSymbolDef.make(IdeSymbolKind.DEF_NAMESPACE, aliasIdeId, doc)
    }
}

object S_DefaultImportTarget: S_ImportTarget() {
    override fun compile(
        ctx: S_DefinitionContext,
        moduleName: R_ModuleName,
        explicitAlias: C_Name?,
        implicitAlias: C_Name?,
        docModifiers: DocModifiers,
    ): C_ImportTarget {
        val explicitAliasIdeDefId = aliasIdeDefId(ctx, explicitAlias)
        val implicitAliasIdeDefId = if (explicitAlias != null) null else aliasIdeDefId(ctx, implicitAlias)

        val aliasFullName = if (explicitAlias == null) null else {
            R_FullName(ctx.moduleName, ctx.namespacePath.qualifiedName(explicitAlias.rName))
        }
        val docDeclaration = DocDeclaration_ImportModule(docModifiers, moduleName, explicitAlias?.rName)

        return C_DefaultImportTarget(
            ctx.docFactory,
            explicitAliasIdeDefId,
            implicitAliasIdeDefId,
            aliasFullName,
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
        val docFactory: DocSymbolFactory,
        val explicitAliasIdeDefId: IdeSymbolId?,
        val implicitAliasIdeDefId: IdeSymbolId?,
        val aliasFullName: R_FullName?,
        val docDeclaration: DocDeclaration,
    ): C_ImportTarget() {
        override fun moduleIdeDefId() = implicitAliasIdeDefId

        override fun aliasIdeInfo(): C_IdeSymbolInfo {
            val docSymbol = if (aliasFullName == null) DocSymbol.NONE else docFactory.makeDocSymbol(
                kind = C_DefinitionType.IMPORT.docKind,
                symbolName = DocSymbolName.global(aliasFullName.moduleName.str(), aliasFullName.qualifiedName.str()),
                declaration = docDeclaration,
            )
            return C_IdeSymbolInfo.direct(IdeSymbolKind.DEF_IMPORT_ALIAS, defId = explicitAliasIdeDefId, doc = docSymbol)
        }

        override fun addToNamespace(ctx: C_MountContext, def: C_ImportDefinition, module: C_ModuleKey) {
            val alias = def.alias ?: def.implicitAlias
            if (alias == null) {
                ctx.msgCtx.error(def.pos, "import:no_alias", "Cannot infer an alias, specify import alias explicitly")
                return
            }

            val ideKind = if (def.alias != null) IdeSymbolKind.EXPR_IMPORT_ALIAS else IdeSymbolKind.DEF_IMPORT_MODULE
            val cDefBase = ctx.defBase(alias, C_DefinitionType.IMPORT, ideKind, null, module.extChain)
            cDefBase.setDocDeclaration(docDeclaration)

            ctx.nsBuilder.addModuleImport(alias, module, cDefBase.ideRefInfo)
        }
    }
}

class S_ExactImportTargetItem(
    private val alias: S_Name?,
    private val name: S_QualifiedName,
    private val wildcard: Boolean,
) {
    fun addToNamespace(
        ctx: C_MountContext,
        docModifiers: DocModifiers,
        importAlias: C_Name?,
        currentModuleName: R_ModuleName,
        nsBuilder: C_UserNsProtoBuilder,
        targetModule: C_ModuleKey,
    ) {
        val aliasHand = alias?.compile(ctx.symCtx, def = true)
        val nameHand = name.compile(ctx.symCtx)

        if (wildcard) {
            val nsBuilder2 = if (aliasHand == null) nsBuilder else {
                val qualifiedName = nsBuilder.namespacePath().qualifiedName(aliasHand.rName)

                val doc = makeDocSymbol(
                    ctx.globalCtx.docFactory,
                    docModifiers,
                    importAlias,
                    currentModuleName,
                    targetModule.name,
                    nameHand.rName,
                    qualifiedName,
                )

                val ideId = makeAliasIdeId(qualifiedName, aliasHand.name, IdeSymbolCategory.NAMESPACE)
                val ideDef = C_IdeSymbolDef.make(IdeSymbolKind.DEF_NAMESPACE, ideId, doc)
                aliasHand.setIdeInfo(ideDef.defInfo)

                nsBuilder.addNamespace(aliasHand.name, false, ideDef.refInfo, deprecated = null)
            }
            nsBuilder2.addWildcardImport(targetModule, nameHand.parts)
        } else {
            val aliasDocSymbol = if (aliasHand == null) null else {
                val qualifiedName = nsBuilder.namespacePath().qualifiedName(aliasHand.rName)
                makeDocSymbol(
                    ctx.globalCtx.docFactory,
                    docModifiers,
                    importAlias,
                    currentModuleName,
                    targetModule.name,
                    nameHand.rName,
                    qualifiedName,
                )
            }

            val realAlias = aliasHand ?: nameHand.last
            nsBuilder.addExactImport(realAlias.name, targetModule, nameHand, aliasHand, aliasDocSymbol)
        }
    }

    private fun makeDocSymbol(
        docFactory: DocSymbolFactory,
        docModifiers: DocModifiers,
        importAlias: C_Name?,
        currentModuleName: R_ModuleName,
        targetModuleName: R_ModuleName,
        targetQualifiedName: R_QualifiedName,
        qualifiedName: R_QualifiedName,
    ): DocSymbol {
        val docDec = DocDeclaration_ImportExactAlias(
            docModifiers,
            targetModuleName,
            targetQualifiedName,
            importAlias?.rName,
            qualifiedName.last,
            wildcard = wildcard,
        )

        return docFactory.makeDocSymbol(
            DocSymbolKind.IMPORT,
            DocSymbolName.global(currentModuleName.str(), qualifiedName.str()),
            docDec,
        )
    }

    companion object {
        fun makeAliasIdeId(fullName: R_QualifiedName, alias: C_Name, category: IdeSymbolCategory): IdeSymbolGlobalId {
            val ideId = IdeSymbolId(category, fullName.str())
            return IdeSymbolGlobalId(alias.pos.idePath(), ideId)
        }
    }
}

class S_ExactImportTarget(private val items: List<S_ExactImportTargetItem>): S_ImportTarget() {
    override fun compile(
        ctx: S_DefinitionContext,
        moduleName: R_ModuleName,
        explicitAlias: C_Name?,
        implicitAlias: C_Name?,
        docModifiers: DocModifiers,
    ): C_ImportTarget {
        val aliasIdeDef = aliasNamespaceIdeDef(ctx, explicitAlias) { expAlias ->
            DocDeclaration_ImportExactModule(docModifiers, moduleName, expAlias.rName)
        }
        return C_ExactImportTarget(docModifiers, explicitAlias, aliasIdeDef)
    }

    private inner class C_ExactImportTarget(
        val docModifiers: DocModifiers,
        val explicitAlias: C_Name?,
        val aliasIdeDef: C_IdeSymbolDef,
    ): C_ImportTarget() {
        override fun aliasIdeInfo() = aliasIdeDef.defInfo

        override fun addToNamespace(ctx: C_MountContext, def: C_ImportDefinition, module: C_ModuleKey) {
            checkEquals(def.alias, explicitAlias)
            val nsBuilder = getNsBuilder(ctx, def.alias, aliasIdeDef.refInfo)
            for (item in items) {
                item.addToNamespace(ctx, docModifiers, def.alias, ctx.modCtx.moduleName, nsBuilder, module)
            }
        }
    }
}

object S_WildcardImportTarget: S_ImportTarget() {
    override fun compile(
        ctx: S_DefinitionContext,
        moduleName: R_ModuleName,
        explicitAlias: C_Name?,
        implicitAlias: C_Name?,
        docModifiers: DocModifiers,
    ): C_ImportTarget {
        val aliasIdeDef = aliasNamespaceIdeDef(ctx, explicitAlias) { expAlias ->
            DocDeclaration_ImportWildcard(docModifiers, moduleName, expAlias.rName)
        }
        return C_WildcardImportTarget(explicitAlias, aliasIdeDef)
    }

    private class C_WildcardImportTarget(
        val explicitAlias: C_Name?,
        val aliasIdeDef: C_IdeSymbolDef,
    ): C_ImportTarget() {
        override fun aliasIdeInfo() = aliasIdeDef.defInfo

        override fun addToNamespace(ctx: C_MountContext, def: C_ImportDefinition, module: C_ModuleKey) {
            checkEquals(def.alias, explicitAlias)
            val nsBuilder = getNsBuilder(ctx, def.alias, aliasIdeDef.refInfo)
            nsBuilder.addWildcardImport(module, listOf())
        }
    }
}

class S_ImportDefinition(
    pos: S_Pos,
    modifiers: S_Modifiers,
    private val alias: S_Name?,
    private val modulePath: S_ImportModulePath,
    private val target: S_ImportTarget,
): S_Definition(pos, modifiers) {
    override fun compile(ctx: S_DefinitionContext): C_MidModuleMember? {
        val modifierCtx = C_ModifierContext(ctx.msgCtx, ctx.symCtx)
        val mods = C_ModifierValues(C_ModifierTargetType.IMPORT, null)
        val modExternal = mods.field(C_ModifierFields.EXTERNAL_CHAIN)
        val docModifiers = modifiers.compile(modifierCtx, mods)

        val cModulePath = modulePath.compile(ctx.msgCtx, ctx.symCtx, kwPos, ctx.moduleName)
        cModulePath ?: return null

        val moduleName = cModulePath.moduleName

        val cAliasHand = alias?.compile(ctx.symCtx, def = true)
        val cTarget = target.compile(ctx, moduleName, cAliasHand?.name, cModulePath.implicitAlias, docModifiers)

        val moduleInfos = try {
            ctx.appCtx.importLoader.loadModuleEx(moduleName)
        } catch (e: C_CommonError) {
            ctx.msgCtx.error(kwPos, e.code, e.msg)
            cModulePath.resolveIdeInfo(cTarget, immMapOf())
            return null
        }

        cModulePath.resolveIdeInfo(cTarget, moduleInfos)

        if (cAliasHand != null) {
            val aliasIdeInfo = cTarget.aliasIdeInfo()
            cAliasHand.setIdeInfo(aliasIdeInfo)
        }

        val importDef = C_ImportDefinition(kwPos, cAliasHand?.name, cModulePath.implicitAlias)
        return C_MidModuleMember_Import(modifiers, importDef, cTarget, moduleName, modExternal.value())
    }

    override fun ideGetImportedModules(moduleName: R_ModuleName, res: MutableSet<R_ModuleName>) {
        val msgMgr = C_DefaultMessageManager()
        val symCtx = C_NopSymbolContext(msgMgr, C_CompilerOptions.DEFAULT)
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
}
