/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.module

import net.postchain.rell.base.compiler.ast.*
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.modifier.C_ModifierContext
import net.postchain.rell.base.compiler.base.modifier.C_ModifierFields
import net.postchain.rell.base.compiler.base.modifier.C_ModifierTargetType
import net.postchain.rell.base.compiler.base.modifier.C_ModifierValues
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceMemberBase
import net.postchain.rell.base.compiler.base.utils.C_LateInit
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.*

internal class C_MidModuleContext(
    val msgCtx: C_MessageContext,
    val modImporter: C_MidModuleImporter,
    val moduleName: R_ModuleName,
    val extChain: C_ExtChainName?,
) {
    val globalCtx = msgCtx.globalCtx
}

internal class C_MidMemberContext(
    val modCtx: C_MidModuleContext,
    val modifierCtx: C_ModifierContext,
    val extChain: C_ExtChainName?,
) {
    fun externalChain(modExtChain: C_ExtChainName?): C_ExtChainName? {
        return modExtChain ?: extChain
    }
}

internal class C_MidModuleHeader(
    val pos: S_Pos,
    val abstract: S_Pos?,
    val external: Boolean,
    val test: Boolean,
    val disabled: Boolean,
)

internal class C_MidModule(
    val moduleName: R_ModuleName,
    val parentName: R_ModuleName?,
    val mountName: R_MountName,
    val header: C_MidModuleHeader?,
    val compiledHeader: C_ModuleHeader,
    private val files: ImmList<C_MidModuleFile>,
    val isDirectory: Boolean,
    val isTestDependency: Boolean,
    val isSelected: Boolean,
) {
    val startPos = header?.pos ?: files.firstOrNull()?.startPos
    val isTest = header?.test ?: false
    val isDisabled = header?.disabled ?: false

    fun filePaths() = files.mapToImmList { it.path }

    fun compile(ctx: C_MidModuleContext): C_ExtModule {
        val extFiles = files.mapToImmList { it.compile(ctx) }
        return C_ExtModule(this, ctx.extChain, extFiles)
    }

    override fun toString() = moduleName.toString()
}

internal class C_MidModuleFile(
    val path: C_SourcePath,
    val members: ImmList<C_MidModuleMember>,
    val startPos: S_Pos?,
    private val symCtx: C_SymbolContext,
) {
    fun compile(ctx: C_MidModuleContext): C_ExtModuleFile {
        val modifierCtx = C_ModifierContext(ctx.msgCtx, symCtx)
        val memCtx = C_MidMemberContext(ctx, modifierCtx, ctx.extChain)
        val extMembers = members.mapToImmList { it.compile(memCtx) }
        return C_ExtModuleFile(path, extMembers, symCtx)
    }

    override fun toString() = path.toString()
}

internal sealed class C_MidModuleMember {
    abstract fun compile(ctx: C_MidMemberContext): C_ExtModuleMember
}

internal class C_MidModuleMember_Basic(
    private val def: S_BasicDefinition,
): C_MidModuleMember() {
    override fun compile(ctx: C_MidMemberContext): C_ExtModuleMember {
        return C_ExtModuleMember_Basic(def)
    }
}

internal class C_MidModuleMember_Enum(
    private val cName: C_Name,
    private val rEnum: R_EnumDefinition,
    private val memBase: C_NamespaceMemberBase,
): C_MidModuleMember() {
    override fun compile(ctx: C_MidMemberContext): C_ExtModuleMember {
        return C_ExtModuleMember_Enum(cName, rEnum, memBase)
    }
}

class C_ImportDefinition(
    val pos: S_Pos,
)

internal class C_MidModuleMember_Import(
    private val importDef: C_ImportDefinition,
    private val target: C_ImportTarget,
    private val moduleName: R_ModuleName,
    private val modExtChain: C_ExtChainName?,
): C_MidModuleMember() {
    override fun compile(ctx: C_MidMemberContext): C_ExtModuleMember {
        val extChainName = ctx.externalChain(modExtChain)
        ctx.modCtx.modImporter.importModule(moduleName, extChainName)
        return C_ExtModuleMember_Import(importDef, target, moduleName, extChainName)
    }
}

internal class C_MidModuleMember_Namespace(
    private val modifiers: S_Modifiers,
    private val qualifiedName: ImmList<NamePart>,
    private val comment: S_Comment?,
    private val posRange: S_PosRange,
    private val members: ImmList<C_MidModuleMember>,
): C_MidModuleMember() {
    override fun compile(ctx: C_MidMemberContext): C_ExtModuleMember {
        val lastName = qualifiedName.lastOrNull()?.ideName?.name
        val mods = C_ModifierValues(C_ModifierTargetType.NAMESPACE, name = lastName)
        val modExternal = mods.field(C_ModifierFields.EXTERNAL_CHAIN)
        val modMount = mods.field(C_ModifierFields.MOUNT)
        val modDeprecated = if (qualifiedName.isEmpty()) null else mods.field(C_ModifierFields.DEPRECATED)
        val docModifiers = modifiers.compile(ctx.modifierCtx, mods)

        for ((i, namePart) in qualifiedName.withIndex()) {
            val isLast = i == qualifiedName.size - 1
            val actualDocModifiers = if (isLast) docModifiers else DocModifiers.NONE
            val actualComment = if (isLast) comment else null
            val docSymbol = makeDocSymbol(ctx, namePart, actualDocModifiers, actualComment)
            namePart.docSymbolLate.set(docSymbol, allowEarly = true)
        }

        val mount = modMount.value()?.process(true)
        val extChainName = ctx.externalChain(modExternal.value())

        val subCtx = C_MidMemberContext(ctx.modCtx, ctx.modifierCtx, extChainName)
        val subMembers = members.mapToImmList { it.compile(subCtx) }

        val ideQualifiedName = if (qualifiedName.isEmpty()) null else C_IdeQualifiedName(qualifiedName.mapToImmList { it.ideName })

        return C_ExtModuleMember_Namespace(
            ideQualifiedName,
            posRange,
            subMembers,
            mount,
            extChainName,
            modDeprecated?.value(),
        )
    }

    private fun makeDocSymbol(
        ctx: C_MidMemberContext,
        namePart: NamePart,
        docModifiers: DocModifiers,
        actualComment: S_Comment?,
    ): DocSymbol {
        val qName = namePart.qualifiedName
        val rFullName = R_FullName(ctx.modCtx.moduleName, qName)
        return ctx.modifierCtx.symCtx.makeDocSymbol(
            kind = DocSymbolKind.NAMESPACE,
            symbolName = DocSymbolName.global(rFullName),
            declaration = DocDeclarationProto_Namespace(docModifiers, rFullName.last).toLazyDeclaration(),
            comment = actualComment,
        )
    }

    class NamePart(
        val ideName: C_IdeName,
        val qualifiedName: R_QualifiedName,
        val docSymbolLate: C_LateInit<DocSymbol?>,
    )
}

internal class C_MidModuleCompiler(
    private val msgCtx: C_MessageContext,
    private val symCtxProvider: C_SymbolContextProvider,
    midModules: List<C_MidModule>,
) {
    private val midModulesMap = midModules.associateByToImmMap { it.moduleName }

    private val modImporter = C_MidModuleImporter_Impl()

    private var done = false
    private val compiledModules = mutableSetOf<C_ExtModuleKey>()
    private val moduleQueue = ArrayDeque<Pair<C_MidModule, C_ExtChainName?>>()
    private val extModules = mutableListOf<C_ExtModule>()

    fun compileModule(moduleName: R_ModuleName, extChain: C_ExtChainName?) {
        check(!done)
        importModule(moduleName, extChain)
        processQueue()
    }

    fun compileReplMembers(moduleName: R_ModuleName, members: List<C_MidModuleMember>): ImmList<C_ExtModuleMember> {
        check(!done)

        val moduleCtx = C_MidModuleContext(msgCtx, modImporter, moduleName, null)
        val symCtx = symCtxProvider.getNopSymbolContext()
        val modifierCtx = C_ModifierContext(msgCtx, symCtx)
        val memberCtx = C_MidMemberContext(moduleCtx, modifierCtx, null)
        val res = members.mapToImmList { it.compile(memberCtx) }

        processQueue()
        return res
    }

    private fun importModule(name: R_ModuleName, extChain: C_ExtChainName?) {
        check(!done)

        val midModule = midModulesMap[name]
        midModule ?: return

        processParentModules(midModule)
        addModuleToQueue(midModule, extChain)
    }

    private fun processParentModules(midModule: C_MidModule) {
        val modules = CommonUtils.chainToList(midModule) {
            if (it.parentName == null) null else midModulesMap[it.parentName]
        }

        for (parent in modules.subList(1, modules.size).asReversed()) {
            addModuleToQueue(parent, null)
        }
    }

    private fun addModuleToQueue(midModule: C_MidModule, extChain: C_ExtChainName?) {
        val key = C_ExtModuleKey(midModule.moduleName, extChain)
        if (compiledModules.add(key)) {
            moduleQueue.add(midModule to extChain)
        }
    }

    private fun processQueue() {
        while (moduleQueue.isNotEmpty()) {
            val (midModule, extChain) = moduleQueue.removeFirst()
            processModule(midModule, extChain)
        }
    }

    private fun processModule(midModule: C_MidModule, extChain: C_ExtChainName?) {
        val midCtx = C_MidModuleContext(msgCtx, modImporter, midModule.moduleName, extChain)
        val extModule = midModule.compile(midCtx)
        extModules.add(extModule)
    }

    fun getExtModules(): ImmList<C_ExtModule> {
        done = true
        return extModules.toImmList()
    }

    private inner class C_MidModuleImporter_Impl: C_MidModuleImporter() {
        override fun importModule(name: R_ModuleName, extChainName: C_ExtChainName?) {
            this@C_MidModuleCompiler.importModule(name, extChainName)
        }
    }

    private data class C_ExtModuleKey(val name: R_ModuleName, val chain: C_ExtChainName?)
}

internal abstract class C_MidModuleImporter {
    abstract fun importModule(name: R_ModuleName, extChainName: C_ExtChainName?)
}
