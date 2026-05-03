/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.core.C_DefinitionName
import net.postchain.rell.base.compiler.base.namespace.C_Namespace
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.model.expr.R_FunctionExtensionsTable
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocDefinition
import net.postchain.rell.base.utils.doc.DocSourcePos
import net.postchain.rell.base.utils.doc.DocSymbol

class R_DefinitionBase(
    val defId: DefinitionId,
    val defName: DefinitionName,
    val cDefName: C_DefinitionName,
    val initFrameGetter: C_LateGetter<R_CallFrame>,
    val docPos: DocSourcePos?,
    val docGetter: C_LateGetter<DocSymbol>,
)

abstract class R_Definition(base: R_DefinitionBase): DocDefinition() {
    val defId = base.defId
    val defName = base.defName
    val cDefName = base.cDefName
    val initFrameGetter = base.initFrameGetter

    private val docGetter = base.docGetter

    val simpleName = defName.simpleName
    val moduleLevelName = defName.qualifiedName
    val appLevelName = defName.appLevelName

    final override val docSymbol: DocSymbol get() = docGetter.get()
    final override val docSourcePos = base.docPos

    final override fun toString() = "${javaClass.simpleName}[$appLevelName]"
}

class R_DefinitionMeta(
    val kind: String,
    val moduleName: String,
    val fullName: String,
    val simpleName: String,
    val mountName: MountName,
    val externalChain: Nullable<String>? = null,
) {
    constructor(
        kind: String,
        defName: DefinitionName,
        mountName: MountName,
        externalChain: Nullable<String>? = null,
    ): this(
        kind = kind,
        moduleName = defName.module,
        fullName = defName.strictAppLevelName,
        simpleName = defName.simpleName,
        mountName = mountName,
        externalChain = externalChain,
    )

    companion object {
        fun forModule(moduleName: ModuleName, mountName: MountName): R_DefinitionMeta {
            val moduleNameStr = moduleName.str()
            return R_DefinitionMeta(
                kind = "module",
                moduleName = moduleNameStr,
                fullName = moduleNameStr,
                simpleName = moduleName.parts.lastOrNull()?.str ?: "",
                mountName = mountName,
            )
        }
    }
}

class R_ExternalChainsRoot
class R_ExternalChainRef(val root: R_ExternalChainsRoot, val name: String, val index: Int)

class R_Module(
    val name: ModuleName,
    val directory: Boolean,
    val abstract: Boolean,
    val external: Boolean,
    val externalChain: String?,
    val test: Boolean,
    val disabled: Boolean,
    val selected: Boolean,
    val entities: ImmMap<String, R_EntityDefinition>,
    val objects: ImmMap<String, R_ObjectDefinition>,
    val structs: ImmMap<String, R_StructDefinition>,
    val enums: ImmMap<String, R_EnumDefinition>,
    val operations: ImmMap<String, R_OperationDefinition>,
    val queries: ImmMap<String, R_QueryDefinition>,
    val functions: ImmMap<String, R_FunctionDefinition>,
    val constants: ImmMap<String, R_GlobalConstantDefinition>,
    val imports: ImmSet<ModuleName>,
    val moduleArgs: R_StructDefinition?,
    override val docSymbol: DocSymbol,
    override val docSourcePos: DocSourcePos?,
    private val nsGetter: () -> C_Namespace,
): DocDefinition() {
    val key = ModuleKey(name, externalChain)

    private val nsLazy: C_Namespace by lazy { nsGetter() }

    override fun toString() = name.toString()

    override fun getDocMembers0() = nsLazy.getDocMembers()
}

class R_AppSqlDefs(
    val entities: ImmList<R_EntityDefinition>,
    val objects: ImmList<R_ObjectDefinition>,
    val topologicalEntities: ImmList<R_EntityDefinition>,
) {
    init {
        checkEquals(this.topologicalEntities.size, this.entities.size)
    }

    fun same(other: R_AppSqlDefs): Boolean {
        return entities == other.entities
                && objects == other.objects
                && topologicalEntities == other.topologicalEntities
    }

    companion object {
        val EMPTY = R_AppSqlDefs(immListOf(), immListOf(), immListOf())
    }
}

class R_App(
    val valid: Boolean,
    val uid: AppUid,
    val modules: ImmList<R_Module>,
    val operations: ImmMap<MountName, R_OperationDefinition>,
    val queries: ImmMap<MountName, R_QueryDefinition>,
    val constants: ImmList<R_GlobalConstantDefinition>,
    val moduleArgs: ImmMap<ModuleName, R_StructDefinition>,
    val functionExtensions: R_FunctionExtensionsTable,
    val externalChainsRoot: R_ExternalChainsRoot,
    val externalChains: ImmList<R_ExternalChainRef>,
    val sqlDefs: R_AppSqlDefs,
    val nativeFunctions: ImmMap<FullName, R_FunctionHeader>,
) {
    val moduleMap = this.modules.associateByToImmMap { it.name }

    init {
        for ((i, c) in this.constants.withIndex()) {
            checkEquals(c.constId.index, i)
        }

        for ((i, c) in this.externalChains.withIndex()) {
            check(c.root === externalChainsRoot)
            checkEquals(c.index, i)
        }
    }
}
