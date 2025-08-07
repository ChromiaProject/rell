/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.gtv.Gtv
import net.postchain.rell.base.compiler.base.core.C_DefinitionName
import net.postchain.rell.base.compiler.base.core.C_IdeSymbolInfo
import net.postchain.rell.base.compiler.base.lib.C_MemberRestrictions
import net.postchain.rell.base.compiler.base.namespace.C_Namespace
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.model.expr.R_FunctionExtensionsTable
import net.postchain.rell.base.runtime.utils.toGtv
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocDefinition
import net.postchain.rell.base.utils.doc.DocSourcePos
import net.postchain.rell.base.utils.doc.DocSymbol

class R_DefinitionName(
    val module: String,
    val qualifiedName: String,
    val simpleName: String,
) {
    val appLevelName = appLevelName(module, qualifiedName)

    val strictAppLevelName: String by lazy {
        if (module.isEmpty()) ":$appLevelName" else appLevelName
    }

    constructor(fullName: R_FullName): this(fullName.moduleName.str(), fullName.qualifiedName.str(), fullName.last.str)

    override fun toString() = appLevelName

    companion object {
        fun appLevelName(module: String, qualifiedName: String): String {
            return if (module.isEmpty()) qualifiedName else R_DefinitionId.str(module, qualifiedName)
        }
    }
}

class R_DefinitionBase internal constructor(
    val defId: R_DefinitionId,
    val defName: R_DefinitionName,
    internal val cDefName: C_DefinitionName,
    internal val initFrameGetter: C_LateGetter<R_CallFrame>,
    internal val docPos: DocSourcePos?,
    internal val docGetter: C_LateGetter<DocSymbol>,
)

abstract class R_Definition internal constructor(
    base: R_DefinitionBase,
): DocDefinition() {
    val defId = base.defId
    val defName = base.defName
    internal val cDefName = base.cDefName
    internal val initFrameGetter = base.initFrameGetter

    private val docGetter = base.docGetter

    val simpleName = defName.simpleName
    val moduleLevelName = defName.qualifiedName
    val appLevelName = defName.appLevelName

    final override val docSymbol: DocSymbol get() = docGetter.get()
    final override val docSourcePos = base.docPos

    internal abstract fun toMetaGtv(): Gtv

    final override fun toString() = "${javaClass.simpleName}[$appLevelName]"
}

class R_DefinitionMeta(
    val kind: String,
    val moduleName: String,
    val fullName: String,
    val simpleName: String,
    val mountName: R_MountName,
    val externalChain: Nullable<String>? = null,
) {
    constructor(
        kind: String,
        defName: R_DefinitionName,
        mountName: R_MountName,
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
        fun forModule(moduleName: R_ModuleName, mountName: R_MountName): R_DefinitionMeta {
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

internal class R_AppUid(val id: Long) {
    override fun toString() = "App[$id]"
}

internal class R_ContainerUid(val id: Long, val app: R_AppUid) {
    override fun toString(): String {
        val params = listOf(id.toString()).filter { it.isNotEmpty() }.joinToString(",")
        return "$app/Container[$params]"
    }
}

internal class R_FnUid(val id: Long, private val container: R_ContainerUid) {
    override fun toString() = "$container/Fn[$id]"
}

internal class R_FrameBlockUid(val id: Long, val fn: R_FnUid) {
    override fun toString() = "$fn/Block[$id]"
}

data class R_AtExprId(val id: Long) {
    fun toRawString() = "$id"
    override fun toString() = "AtExpr[$id]"
}

data class R_AtEntityId(val exprId: R_AtExprId, val id: Long) {
    override fun toString() = "AtEntity[${exprId.toRawString()}:$id]"
}

class R_DefaultValue(val rExpr: R_Expr, val isDbModification: Boolean)

enum class R_KeyIndexKind(val code: String) {
    KEY("key"),
    INDEX("index"),
    ;

    val nameMsg = MsgString(code)
}

class R_Attribute(
    val index: Int,
    val rName: R_Name,
    val type: R_Type,
    val mutable: Boolean,
    val keyIndexKind: R_KeyIndexKind?,
    val ideInfo: C_IdeSymbolInfo,
    val restrictions: C_MemberRestrictions = C_MemberRestrictions.NULL,
    val canSetInCreate: Boolean = true,
    val sqlMapping: String = rName.str,
    override val docSourcePos: DocSourcePos? = null,
    private val exprGetter: C_LateGetter<R_DefaultValue>?,
): DocDefinition() {
    val ideName = R_IdeName(rName, ideInfo)
    val name = rName.str

    val expr: R_Expr? get() = exprGetter?.get()?.rExpr
    val isExprDbModification: Boolean get() = exprGetter?.get()?.isDbModification ?: false

    val hasExpr: Boolean get() = exprGetter != null

    override val docSymbol: DocSymbol get() = ideInfo.getIdeInfo().doc ?: DocSymbol.NONE

    fun toMetaGtv(): Gtv {
        return mapOf(
            "name" to name.toGtv(),
            "type" to type.toMetaGtv(),
            "mutable" to mutable.toGtv(),
        ).toGtv()
    }

    fun copy(mutable: Boolean, ideInfo: C_IdeSymbolInfo): R_Attribute {
        return R_Attribute(
            index = index,
            rName = rName,
            type = type,
            mutable = mutable,
            keyIndexKind = keyIndexKind,
            ideInfo = ideInfo,
            docSourcePos = docSourcePos,
            restrictions = restrictions,
            canSetInCreate = true,
            sqlMapping = sqlMapping,
            exprGetter = if (canSetInCreate) exprGetter else null, // Not copying default value e. g. for "transaction".
        )
    }

    override fun toString() = name
}

data class R_ModuleKey(val name: R_ModuleName, val externalChain: String?) {
    fun str() = str(name, externalChain)
    override fun toString() = str()

    companion object {
        val EMPTY = R_ModuleKey(R_ModuleName.EMPTY, null)

        fun str(name: R_ModuleName, externalChain: String?): String {
            return if (externalChain == null) name.toString() else "$name[$externalChain]"
        }
    }
}

class R_Module(
    val name: R_ModuleName,
    val directory: Boolean,
    val abstract: Boolean,
    val external: Boolean,
    val externalChain: String?,
    val test: Boolean,
    val selected: Boolean,
    val entities: ImmMap<String, R_EntityDefinition>,
    val objects: ImmMap<String, R_ObjectDefinition>,
    val structs: ImmMap<String, R_StructDefinition>,
    val enums: ImmMap<String, R_EnumDefinition>,
    val operations: ImmMap<String, R_OperationDefinition>,
    val queries: ImmMap<String, R_QueryDefinition>,
    val functions: ImmMap<String, R_FunctionDefinition>,
    val constants: ImmMap<String, R_GlobalConstantDefinition>,
    val imports: ImmSet<R_ModuleName>,
    val moduleArgs: R_StructDefinition?,
    override val docSymbol: DocSymbol,
    override val docSourcePos: DocSourcePos?,
    private val nsGetter: Getter<C_Namespace>,
): DocDefinition() {
    val key = R_ModuleKey(name, externalChain)

    private val nsLazy: C_Namespace by lazy { nsGetter() }

    override fun toString() = name.toString()

    fun toMetaGtv(): Gtv {
        val map = mutableMapOf(
                "name" to name.str().toGtv()
        )

        if (abstract) map["abstract"] = true.toGtv()
        if (external) map["external"] = true.toGtv()
        if (externalChain != null) map["externalChain"] = externalChain.toGtv()

        addGtvDefs(map, "entities", entities)
        addGtvDefs(map, "objects", objects)
        addGtvDefs(map, "structs", structs)
        addGtvDefs(map, "enums", enums)
        addGtvDefs(map, "operations", operations)
        addGtvDefs(map, "queries", queries)
        addGtvDefs(map, "functions", functions)
        addGtvDefs(map, "constants", constants)

        return map.toGtv()
    }

    private fun addGtvDefs(map: MutableMap<String, Gtv>, key: String, defs: Map<String, R_Definition>) {
        if (defs.isNotEmpty()) {
            map[key] = defs.keys.sorted().associateWith { defs.getValue(it).toMetaGtv() }.toGtv()
        }
    }

    override fun getDocMembers0() = nsLazy.getDocMembers()
}

class R_AppSqlDefs(
        val entities: ImmList<R_EntityDefinition>,
        val objects: ImmList<R_ObjectDefinition>,
        val topologicalEntities: ImmList<R_EntityDefinition>
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

class R_App internal constructor(
    val valid: Boolean,
    internal val uid: R_AppUid,
    val modules: ImmList<R_Module>,
    val operations: ImmMap<R_MountName, R_OperationDefinition>,
    val queries: ImmMap<R_MountName, R_QueryDefinition>,
    val constants: ImmList<R_GlobalConstantDefinition>,
    val moduleArgs: ImmMap<R_ModuleName, R_StructDefinition>,
    val functionExtensions: R_FunctionExtensionsTable,
    val externalChainsRoot: R_ExternalChainsRoot,
    val externalChains: ImmList<R_ExternalChainRef>,
    val sqlDefs: R_AppSqlDefs,
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

    fun toMetaGtv(): Gtv {
        return mapOf(
            "modules" to modules.associate {
                val name = it.name.str()
                val fullName = if (it.externalChain == null) name else "$name[${it.externalChain}]"
                fullName to it.toMetaGtv()
            }.toGtv()
        ).toGtv()
    }
}
