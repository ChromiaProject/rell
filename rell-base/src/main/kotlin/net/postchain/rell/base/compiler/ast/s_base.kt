/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import net.postchain.rell.base.compiler.base.core.C_FileContext
import net.postchain.rell.base.compiler.base.core.C_MountContext
import net.postchain.rell.base.compiler.base.core.C_NamespaceContext
import net.postchain.rell.base.compiler.base.modifier.*
import net.postchain.rell.base.compiler.base.module.C_MidModuleFile
import net.postchain.rell.base.compiler.base.module.C_ModuleUtils
import net.postchain.rell.base.compiler.base.module.C_SourceModuleHeader
import net.postchain.rell.base.compiler.base.module.S_FileContext
import net.postchain.rell.base.compiler.base.namespace.C_NsAsm_ComponentAssembler
import net.postchain.rell.base.compiler.base.namespace.C_UserNsProtoBuilder
import net.postchain.rell.base.compiler.base.utils.C_RNamePath
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.model.R_MountName
import net.postchain.rell.base.utils.ide.IdeModuleInfo
import net.postchain.rell.base.utils.ide.IdeOutlineTreeBuilder
import net.postchain.rell.base.utils.mapNotNullToImmList
import net.postchain.rell.base.utils.toImmSet

class S_ModuleHeader(
    private val modifiers: S_Modifiers,
    val pos: S_Pos,
    private val comment: S_Comment?,
) {
    fun compile(ctx: C_ModifierContext): C_SourceModuleHeader {
        val mods = C_ModifierValues(C_ModifierTargetType.MODULE, null)
        val modAbstract = mods.field(C_ModifierFields.ABSTRACT)
        val modExternal = mods.field(C_ModifierFields.EXTERNAL_MODULE)
        val modMount = mods.field(C_ModifierFields.MOUNT)
        val modTest = mods.field(C_ModifierFields.TEST)
        val docModifiers = modifiers.compile(ctx, mods)

        C_AnnUtils.checkModsZeroOne(ctx.msgCtx, modAbstract, modTest)
        C_AnnUtils.checkModsZeroOne(ctx.msgCtx, modMount, modTest)

        val mount = modMount.value()
        val abstractPos = modAbstract.pos()
        val external = modExternal.hasValue()
        val test = modTest.hasValue()

        return C_SourceModuleHeader(pos, mount, abstractPos, external, test, comment, docModifiers)
    }

    fun ideIsTestFile(): Boolean {
        return modifiers.modifiers.any { it.ideIsTestFile() }
    }
}

class S_RellFile(
    val header: S_ModuleHeader?,
    private val definitions: List<S_Definition>,
): S_Node() {
    val startPos = header?.pos ?: definitions.firstOrNull()?.startPos

    fun compileHeader(modifierCtx: C_ModifierContext): C_SourceModuleHeader? {
        return header?.compile(modifierCtx)
    }

    fun compile(ctx: S_FileContext): C_MidModuleFile {
        val defCtx = ctx.createDefinitionContext()
        val members = definitions.mapNotNullToImmList { it.compile(defCtx) }
        return C_MidModuleFile(ctx.path, members, startPos, defCtx.symCtx)
    }

    fun ideModuleInfo(path: C_SourcePath): IdeModuleInfo? {
        val (moduleName, directory) = C_ModuleUtils.getModuleInfo(path, this)
        moduleName ?: return null

        val imports = mutableSetOf<R_ModuleName>()
        for (def in definitions) {
            def.ideGetImportedModules(moduleName, imports)
        }

        val test = header != null && header.ideIsTestFile()

        return IdeModuleInfo(moduleName, directory, app = !test, test = test, imports = imports.toImmSet())
    }

    fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        for (def in definitions) {
            def.ideBuildOutlineTree(b)
        }
    }

    companion object {
        fun createMountContext(
            fileCtx: C_FileContext,
            mountName: R_MountName,
            nsAssembler: C_NsAsm_ComponentAssembler,
        ): C_MountContext {
            val modCtx = fileCtx.modCtx
            val nsBuilder = C_UserNsProtoBuilder(nsAssembler)
            val fileScopeBuilder = modCtx.scopeBuilder.nested(nsAssembler.futureNs())
            val nsCtx = C_NamespaceContext(modCtx, fileCtx.symCtx, C_RNamePath.EMPTY, fileScopeBuilder)
            return C_MountContext(fileCtx, nsCtx, modCtx.extChain, nsBuilder, mountName)
        }
    }
}
