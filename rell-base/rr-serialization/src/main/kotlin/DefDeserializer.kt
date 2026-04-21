/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.DefinitionId
import net.postchain.rell.base.model.DefinitionName
import net.postchain.rell.base.model.EntityFlags
import net.postchain.rell.base.model.KeyIndexKind
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.MountName
import net.postchain.rell.base.model.OperationModifiers
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.mapToImmList
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmMap
import rell.ir.*
import rell.ir.Attribute as FbAttribute
import rell.ir.EntityDefinition as FbEntityDefinition
import rell.ir.EntityFlags as FbEntityFlags
import rell.ir.EnumDefinition as FbEnumDefinition
import rell.ir.FunctionBody as FbFunctionBody
import rell.ir.FunctionDefinition as FbFunctionDefinition
import rell.ir.GlobalConstantDefinition as FbGlobalConstantDefinition
import rell.ir.KeyIndexKind as FbKeyIndexKind
import rell.ir.ObjectDefinition as FbObjectDefinition
import rell.ir.OperationDefinition as FbOperationDefinition
import rell.ir.QueryDefinition as FbQueryDefinition
import rell.ir.StructDefinition as FbStructDefinition

// --- Definition base ---

private fun deserializeDefinitionBase(defName: DefinitionName): RR_DefinitionBase {
    return RR_DefinitionBase(
        defId = DefinitionId(defName.module, defName.qualifiedName),
        defName = defName,
        initFrame = RR_FrameDescriptor(
            size = 0,
            rootBlock = RR_FrameBlock(parentUid = null, uid = 0, offset = 0, size = 0),
            hasGuardBlock = false,
        ),
    )
}

// --- Entities ---

fun deserializeEntityDefinition(fb: FbEntityDefinition): RR_EntityDefinition {
    val defName = deserializeDefinitionName(fb.defName)
    val initFrame = fb.initFrame?.let { deserializeFrameDescriptor(it) }
    val base = if (initFrame != null) {
        RR_DefinitionBase(
            defId = DefinitionId(defName.module, defName.qualifiedName),
            defName = defName,
            initFrame = initFrame,
        )
    } else {
        deserializeDefinitionBase(defName)
    }
    val flags = deserializeEntityFlags(fb.flags)
    val sqlMapping = deserializeEntitySqlMapping(fb.sqlMapping)
    val attrs = deserializeAttributes(fb)
    val keys = deserializeKeyIndices(fb.keysLength) { fb.keys(it) }
    val indexes = deserializeKeyIndices(fb.indicesLength) { fb.indices(it) }
    val external = fb.external?.let { RR_ExternalEntity(chainName = it.chainName ?: "", metaCheck = it.metaCheck) }

    val rName = Name.of(defName.simpleName)
    val attrMap = attrs.associateBy { it.rName }.toImmMap()

    return RR_EntityDefinition(
        base = base,
        rName = rName,
        flags = flags,
        sqlMapping = sqlMapping,
        external = external,
        type = RR_Type.Error, // Will be set to Entity(defIndex) by the app deserializer.
        keys = keys.map { R_Key(it.toImmList()) }.toImmList(),
        indexes = indexes.map { R_Index(it.toImmList()) }.toImmList(),
        attributes = attrMap,
    )
}

private fun deserializeEntityFlags(fb: FbEntityFlags?): EntityFlags {
    if (fb == null) return EntityFlags(
        isObject = false,
        canCreate = true,
        canUpdate = false,
        canDelete = false,
        gtv = false,
        log = false,
    )
    return EntityFlags(
        isObject = fb.isObject,
        canCreate = fb.canCreate,
        canUpdate = fb.canUpdate,
        canDelete = fb.canDelete,
        gtv = fb.gtv,
        log = fb.log,
    )
}

private fun deserializeEntitySqlMapping(fb: EntitySqlMapping?): RR_EntitySqlMapping {
    if (fb == null) return RR_EntitySqlMapping(
        mountName = MountName.EMPTY, metaName = "", rowidColumn = "rowid",
        autoCreateTable = true, isSystemEntity = false, kind = RR_EntitySqlMappingKind.REGULAR, externalChainIndex = -1,
    )
    val kind = when (fb.kind) {
        EntitySqlMappingKind.EXTERNAL -> RR_EntitySqlMappingKind.EXTERNAL
        EntitySqlMappingKind.TRANSACTION -> RR_EntitySqlMappingKind.TRANSACTION
        EntitySqlMappingKind.BLOCK -> RR_EntitySqlMappingKind.BLOCK
        else -> RR_EntitySqlMappingKind.REGULAR
    }
    val mountName = deserializeMountName(fb.mountName)
    return RR_EntitySqlMapping(
        mountName = mountName,
        metaName = fb.metaName ?: mountName.parts.joinToString(".") { it.str },
        rowidColumn = fb.rowidColumn ?: "rowid",
        autoCreateTable = fb.autoCreateTable,
        isSystemEntity = fb.isSystemEntity,
        kind = kind,
        externalChainIndex = fb.chainIndex,
    )
}

// --- Structs ---

fun deserializeStructDefinition(fb: FbStructDefinition): RR_StructDefinition {
    val defName = deserializeDefinitionName(fb.defName)
    val base = deserializeDefinitionBase(defName)
    val name = fb.name
    val attrs = deserializeAttributes(fb)
    val attrMap = attrs.associateBy { it.rName }.toImmMap()

    val fbFlags = fb.flags
    val structFlags = if (fbFlags != null) {
        RR_StructFlags(
            typeFlags = TypeFlags(
                pure = fbFlags.pure,
                mutable = fbFlags.mutable,
                gtv = GtvCompatibility(fromGtv = fbFlags.gtvFrom, toGtv = fbFlags.gtvTo),
                virtualable = fbFlags.virtualable,
                mixedTuple = fbFlags.mixedTuple,
                hasTypeVariable = fbFlags.hasTypeVariable,
            ),
            cyclic = fbFlags.cyclic,
            infinite = fbFlags.infinite,
        )
    } else {
        RR_StructFlags(
            typeFlags = TypeFlags(true, false, GtvCompatibility(true, true), true, false, false),
            cyclic = false,
            infinite = false,
        )
    }
    val mirrorInfo = fb.mirrorInfo?.let {
        RR_MirrorStructInfo(it.definitionType, it.definition, it.mutable)
    }

    val struct = RR_Struct(
        name = name,
        attributes = attrMap,
        flags = structFlags,
        mirrorInfo = mirrorInfo,
    )

    return RR_StructDefinition(base = base, struct = struct, hasDefaultConstructor = fb.hasDefaultConstructor)
}

// --- Enums ---

fun deserializeEnumDefinition(fb: FbEnumDefinition): RR_EnumDefinition {
    val defName = deserializeDefinitionName(fb.defName)
    val base = deserializeDefinitionBase(defName)
    val attrs = (0 until fb.attrsLength).mapToImmList { i ->
        val a = fb.attrs(i)
        RR_EnumAttr(rName = Name.of(a.name), value = a.value)
    }
    return RR_EnumDefinition(base = base, attrs = attrs)
}

// --- Objects ---

fun deserializeObjectDefinition(fb: FbObjectDefinition, entities: List<RR_EntityDefinition>): RR_ObjectDefinition {
    val defName = deserializeDefinitionName(fb.defName)
    val base = deserializeDefinitionBase(defName)
    val entityIdx = fb.entityDefIndex.toInt()
    val entity = checkNotNull(entities.getOrNull(entityIdx)) { "Entity index out of range: $entityIdx" }
    return RR_ObjectDefinition(base = base, rEntity = entity)
}

// --- Functions ---

fun deserializeFunctionDefinition(fb: FbFunctionDefinition): RR_FunctionDefinition {
    val defName = deserializeDefinitionName(fb.defName)
    val base = deserializeDefinitionBase(defName)
    val fnBase = deserializeFunctionBody(fb.body, defName)
    return RR_FunctionDefinition(base = base, fnBase = fnBase, isTest = fb.isTest, disabled = false)
}

internal fun deserializeFunctionBody(fb: FbFunctionBody?, fallbackDefName: DefinitionName): RR_FunctionBase {
    if (fb == null) error("Null function body for ${fallbackDefName.qualifiedName}")
    val defName = fb.defName?.let { deserializeDefinitionName(it) } ?: fallbackDefName
    val params = deserializeFunctionParams(fb)
    val paramVars = (0 until fb.paramPtrsLength).mapToImmList { i ->
        val pv = fb.paramPtrs(i)
        val paramType = params.getOrNull(i)?.type ?: RR_Type.Error
        RR_ParamVar(paramType, RR_VarPtr(pv.blockUid.toLong(), pv.offset))
    }
    return RR_FunctionBase(
        defId = DefinitionId(defName.module, defName.qualifiedName),
        defName = defName,
        params = params,
        resultType = deserializeType(fb.type),
        paramVars = paramVars,
        body = deserializeStmt(fb.body),
        frame = deserializeFrameDescriptor(fb.frame),
    )
}

// --- Operations ---

fun deserializeOperationDefinition(fb: FbOperationDefinition): RR_OperationDefinition {
    val defName = deserializeDefinitionName(fb.defName)
    val base = deserializeDefinitionBase(defName)
    val mountName = deserializeMountName(fb.mountName)
    val modifiers = fb.modifiers.let {
        OperationModifiers(isCompound = it.isCompound, isSingular = it.isSingular)
    }

    val body = fb.body!!
    val params = deserializeFunctionParams(body)
    val paramVars = (0 until body.paramPtrsLength).mapToImmList { i ->
        val pv = body.paramPtrs(i)
        val paramType = params.getOrNull(i)?.type ?: RR_Type.Error
        RR_ParamVar(paramType, RR_VarPtr(pv.blockUid.toLong(), pv.offset))
    }
    val guardBody = fb.guardBody?.let { deserializeStmt(it) }

    return RR_OperationDefinition(
        base = base,
        mountName = mountName,
        modifiers = modifiers,
        params = params,
        paramVars = paramVars,
        body = deserializeStmt(body.body),
        guardBody = guardBody,
        frame = deserializeFrameDescriptor(body.frame),
    )
}

// --- Queries ---

fun deserializeQueryDefinition(fb: FbQueryDefinition): RR_QueryDefinition {
    val defName = deserializeDefinitionName(fb.defName)
    val base = deserializeDefinitionBase(defName)
    val mountName = deserializeMountName(fb.mountName)
    val body = deserializeQueryBody(fb.body)
    return RR_QueryDefinition(base = base, mountName = mountName, body = body)
}

private fun deserializeQueryBody(fb: QueryBody?): RR_QueryBody = when (fb?.bodyType) {
    null -> error("Null query body")
    QueryBodyUnion.UserQueryBody -> {
        val b = UserQueryBody().also { fb.body(it) }
        val params = deserializeFunctionParamsFromQuery(b)
        val paramVars = (0 until b.paramPtrsLength).mapToImmList { i ->
            val pv = b.paramPtrs(i)
            val paramType = params.getOrNull(i)?.type ?: RR_Type.Error
            RR_ParamVar(paramType, RR_VarPtr(pv.blockUid.toLong(), pv.offset))
        }
        RR_UserQueryBody(
            retType = deserializeType(b.retType),
            params = params,
            paramVars = paramVars,
            body = deserializeStmt(b.body),
            frame = deserializeFrameDescriptor(b.frame),
        )
    }

    QueryBodyUnion.SysQueryBody -> {
        val b = SysQueryBody().also { fb.body(it) }
        val params = (0 until b.paramsLength).mapToImmList { deserializeFunctionParam(b.params(it)) }
        RR_SysQueryBody(
            retType = deserializeType(b.retType),
            params = params,
            fnName = b.fnName,
        )
    }

    else -> error("Unknown query body type: ${fb.bodyType}")
}

// --- Constants ---

fun deserializeGlobalConstantDefinition(fb: FbGlobalConstantDefinition): RR_GlobalConstantDefinition {
    val defName = deserializeDefinitionName(fb.defName)
    val base = RR_DefinitionBase(
        defId = DefinitionId(defName.module, defName.qualifiedName),
        defName = defName,
        initFrame = deserializeFrameDescriptor(fb.frame),
    )
    val constIndex = fb.constIndex.toInt()
    val appUid = AppUid(0)
    val moduleKey = ModuleKey(ModuleName.of(defName.module), null)
    return RR_GlobalConstantDefinition(
        base = base,
        constId = GlobalConstantId(constIndex, appUid, moduleKey, defName.qualifiedName, defName.simpleName),
        type = deserializeType(fb.type),
        expr = deserializeExpr(fb.value),
        metaGtvJson = fb.metaGtvJson,
    )
}

// --- Helpers ---

private fun deserializeAttributes(fb: FbEntityDefinition): List<RR_Attribute> =
    (0 until fb.attributesLength).map { i -> deserializeAttribute(fb.attributes(i)) }

private fun deserializeAttributes(fb: FbStructDefinition): List<RR_Attribute> =
    (0 until fb.attributesLength).map { i -> deserializeAttribute(fb.attributes(i)) }

private fun deserializeAttribute(fb: FbAttribute): RR_Attribute {
    val kik = if (fb.hasKeyIndexKind) {
        when (fb.keyIndexKind) {
            FbKeyIndexKind.KEY -> KeyIndexKind.KEY
            FbKeyIndexKind.INDEX -> KeyIndexKind.INDEX
            else -> null
        }
    } else null

    return RR_Attribute(
        index = fb.index,
        rName = Name.of(fb.name),
        type = deserializeType(fb.type),
        mutable = fb.mutable,
        keyIndexKind = kik,
        canSetInCreate = fb.canSetInCreate,
        sqlMapping = fb.sqlMapping,
        defaultExpr = fb.defaultExpr?.let { deserializeExpr(it) },
        isDbModification = fb.isDbModification,
        sizeConstraint = fb.sizeConstraint?.let { deserializeSizeConstraint(it) },
    )
}

private fun deserializeFunctionParam(p: FunctionParam): RR_FunctionParam = RR_FunctionParam(
    name = Name.of(p.name),
    type = deserializeType(p.type),
    initFrame = p.initFrame?.let { deserializeFrameDescriptor(it) }
        ?: RR_FrameDescriptor(0, RR_FrameBlock(null, 0, 0, 0), false),
    defaultExpr = p.defaultExpr?.let { deserializeExpr(it) },
    sizeConstraint = p.sizeConstraint?.let { deserializeSizeConstraint(it) },
)

private fun deserializeFunctionParams(fb: FbFunctionBody): ImmList<RR_FunctionParam> =
    (0 until fb.paramsLength).mapToImmList { deserializeFunctionParam(fb.params(it)) }

private fun deserializeFunctionParamsFromQuery(fb: UserQueryBody): ImmList<RR_FunctionParam> =
    (0 until fb.paramsLength).mapToImmList { deserializeFunctionParam(fb.params(it)) }

private fun deserializeSizeConstraint(fb: SizeConstraint): RR_SizeConstraint {
    val kind = when (fb.kind) {
        SizeConstraintKind.TEXT -> RR_SizeConstraintKind.TEXT
        else -> RR_SizeConstraintKind.BYTE_ARRAY
    }
    return RR_SizeConstraint(
        min = if (fb.hasMin) fb.min else null,
        max = if (fb.hasMax) fb.max else null,
        kind = kind,
        codePrefix = fb.codePrefix,
    )
}

private fun deserializeKeyIndices(count: Int, accessor: (Int) -> KeyIndex): List<List<Name>> {
    return (0 until count).map { i ->
        val ki = accessor(i)
        (0 until ki.attrNamesLength).map { j -> Name.of(ki.attrNames(j)) }
    }
}
