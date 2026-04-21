/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.rr

import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.mapKeysToImmMap
import net.postchain.rell.base.utils.toImmList

data class RR_EntityDefinition(
    val base: RR_DefinitionBase,
    val rName: Name,
    val flags: EntityFlags,
    val sqlMapping: RR_EntitySqlMapping,
    val external: RR_ExternalEntity?,
    val type: RR_Type,
    val keys: ImmList<R_Key>,
    val indexes: ImmList<R_Index>,
    val attributes: ImmMap<Name, RR_Attribute>,
) {
    val mountName: MountName get() = sqlMapping.mountName

    /** Derived view of [attributes] keyed by `Name.str`. */
    val strAttributes: ImmMap<String, RR_Attribute> by lazy {
        attributes.mapKeysToImmMap { it.key.str }
    }
}

/**
 * SQL mapping for an `entity`: mount name, rowid column, and the [kind] discriminator
 * that tells the interpreter how to derive the actual table name at runtime.
 */
data class RR_EntitySqlMapping(
    val mountName: MountName,
    val metaName: String,
    val rowidColumn: String,
    val autoCreateTable: Boolean,
    val isSystemEntity: Boolean,
    val kind: RR_EntitySqlMappingKind,
    /** Index into externalChains, or -1 if not external. */
    val externalChainIndex: Int,
)

/** Discriminator for entity SQL mapping table-name resolution. */
enum class RR_EntitySqlMappingKind {
    /** Regular user entity: table = chainMapping.fullName(mountName). */
    REGULAR,

    /** External entity: table = linkedChain(chainIndex).sqlMapping.fullName(mountName). */
    EXTERNAL,

    /** System transaction entity: table = chainMapping.transactionsTable. */
    TRANSACTION,

    /** System block entity: table = chainMapping.blocksTable. */
    BLOCK,
}

/**
 * External entity descriptor for entities imported from another chain.
 * @param chainName name of the external chain
 * @param metaCheck whether metadata compatibility should be checked
 */
data class RR_ExternalEntity(
    val chainName: String,
    val metaCheck: Boolean,
)

data class RR_ObjectDefinition(
    val base: RR_DefinitionBase,
    val rEntity: RR_EntityDefinition,
)

data class RR_StructFlags(
    val typeFlags: TypeFlags,
    val cyclic: Boolean,
    val infinite: Boolean,
)

/**
 * Mirror struct metadata for structs generated from entities, objects, or operations.
 * @param definitionType e.g. "ENTITY", "OBJECT", "OPERATION"
 * @param definition the app-level name of the mirrored definition, e.g. "lib:user"
 * @param mutable whether this is a mutable mirror struct
 */
data class RR_MirrorStructInfo(
    val definitionType: String,
    val definition: String,
    val mutable: Boolean,
)

data class RR_Struct(
    val name: String,
    val attributes: ImmMap<Name, RR_Attribute>,
    val flags: RR_StructFlags,
    val mirrorInfo: RR_MirrorStructInfo? = null,
) {
    /** Derived view of [attributes] in attribute-index order. */
    val attributesList: ImmList<RR_Attribute> by lazy { attributes.values.toImmList() }

    /** Derived view of [attributes] keyed by `Name.str`. */
    val strAttributes: ImmMap<String, RR_Attribute> by lazy {
        attributes.mapKeysToImmMap { it.key.str }
    }

    override fun toString() = name
}

data class RR_StructDefinition(
    val base: RR_DefinitionBase,
    val struct: RR_Struct,
    val hasDefaultConstructor: Boolean,
)

data class RR_GlobalConstantDefinition(
    val base: RR_DefinitionBase,
    val constId: GlobalConstantId,
    val type: RR_Type,
    val expr: RR_Expr,
    /** Pre-computed GTV value as JSON string for compile-time constants, used in `get_app_structure` meta. */
    val metaGtvJson: String? = null,
)

/** Variable slot for a function/operation/query parameter: type + frame pointer. */
data class RR_ParamVar(
    val type: RR_Type,
    val ptr: RR_VarPtr,
)

data class RR_OperationDefinition(
    val base: RR_DefinitionBase,
    val mountName: MountName,
    val modifiers: OperationModifiers,
    val params: ImmList<RR_FunctionParam>,
    val paramVars: ImmList<RR_ParamVar>,
    val body: RR_Statement,
    val guardBody: RR_Statement?,
    val frame: RR_FrameDescriptor,
)

sealed interface RR_QueryBody {
    val retType: RR_Type
    val params: ImmList<RR_FunctionParam>
}

data class RR_UserQueryBody(
    override val retType: RR_Type,
    override val params: ImmList<RR_FunctionParam>,
    val paramVars: ImmList<RR_ParamVar>,
    val body: RR_Statement,
    val frame: RR_FrameDescriptor,
): RR_QueryBody

data class RR_SysQueryBody(
    override val retType: RR_Type,
    override val params: ImmList<RR_FunctionParam>,
    val fnName: String,
): RR_QueryBody

data class RR_QueryDefinition(
    val base: RR_DefinitionBase,
    val mountName: MountName,
    val body: RR_QueryBody,
) {
    fun type(): RR_Type = body.retType
    fun params() = body.params
}

/**
 * Compiled function body: parameters, result type, parameter variable slots, statement body, and frame layout.
 * Shared by `function`, `operation`, and `query` definitions, as well as abstract override bodies.
 */
data class RR_FunctionBase(
    val defId: DefinitionId,
    val defName: DefinitionName,
    val params: ImmList<RR_FunctionParam>,
    val resultType: RR_Type,
    val paramVars: ImmList<RR_ParamVar>,
    val body: RR_Statement,
    val frame: RR_FrameDescriptor,
) {
    override fun toString() = defName.appLevelName
}

data class RR_FunctionDefinition(
    val base: RR_DefinitionBase,
    val fnBase: RR_FunctionBase,
    val isTest: Boolean,
    val disabled: Boolean,
) {
    fun params() = fnBase.params
}
