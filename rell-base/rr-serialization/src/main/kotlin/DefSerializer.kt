/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import net.postchain.rell.base.model.KeyIndex
import net.postchain.rell.base.model.rr.*
import rell.ir.*
import rell.ir.Attribute as FbAttribute
import rell.ir.EntityDefinition as FbEntityDefinition
import rell.ir.EntityFlags as FbEntityFlags
import rell.ir.EnumAttr as FbEnumAttr
import rell.ir.EnumDefinition as FbEnumDefinition
import rell.ir.FunctionBody as FbFunctionBody
import rell.ir.FunctionDefinition as FbFunctionDefinition
import rell.ir.FunctionParam as FbFunctionParam
import rell.ir.GlobalConstantDefinition as FbGlobalConstantDefinition
import rell.ir.KeyIndex as FbKeyIndex
import rell.ir.KeyIndexKind as FbKeyIndexKind
import rell.ir.ObjectDefinition as FbObjectDefinition
import rell.ir.OperationDefinition as FbOperationDefinition
import rell.ir.QueryDefinition as FbQueryDefinition
import rell.ir.StructDefinition as FbStructDefinition

// --- Definition serializers ---

fun SerializerContext.serializeEntityDefinition(entity: RR_EntityDefinition): Int {
    val defName = serializeDefinitionName(entity.base.defName)
    val flags = serializeEntityFlags(entity.flags)
    val sqlMapping = serializeEntitySqlMapping(entity.sqlMapping)
    val attrs = serializeAttributes(entity.attributes.values.toList())
    val keys = serializeKeyIndices(entity.keys)
    val indices = serializeKeyIndices(entity.indexes)
    val external = entity.external?.let { serializeExternalEntity(it) }
    val initFrame = serializeFrameDescriptor(entity.base.initFrame)

    FbEntityDefinition.startEntityDefinition(builder)
    FbEntityDefinition.addDefName(builder, defName)
    FbEntityDefinition.addFlags(builder, flags)
    FbEntityDefinition.addSqlMapping(builder, sqlMapping)
    FbEntityDefinition.addAttributes(builder, attrs)
    FbEntityDefinition.addKeys(builder, keys)
    FbEntityDefinition.addIndices(builder, indices)
    if (external != null) FbEntityDefinition.addExternal(builder, external)
    FbEntityDefinition.addInitFrame(builder, initFrame)
    return FbEntityDefinition.endEntityDefinition(builder)
}

fun SerializerContext.serializeStructDefinition(struct: RR_StructDefinition): Int {
    val defName = serializeDefinitionName(struct.base.defName)
    val name = createString(struct.struct.name)
    val attrs = serializeAttributes(struct.struct.attributesList.toList())
    val tf = struct.struct.flags.typeFlags
    val flags = run {
        StructFlags.startStructFlags(builder)
        StructFlags.addPure(builder, tf.pure)
        StructFlags.addMutable(builder, tf.mutable)
        StructFlags.addGtvFrom(builder, tf.gtv.fromGtv)
        StructFlags.addGtvTo(builder, tf.gtv.toGtv)
        StructFlags.addVirtualable(builder, tf.virtualable)
        StructFlags.addMixedTuple(builder, tf.mixedTuple)
        StructFlags.addHasTypeVariable(builder, tf.hasTypeVariable)
        StructFlags.addCyclic(builder, struct.struct.flags.cyclic)
        StructFlags.addInfinite(builder, struct.struct.flags.infinite)
        StructFlags.endStructFlags(builder)
    }
    val mirrorInfo = struct.struct.mirrorInfo?.let { mi ->
        val defKind = serializeMirrorStructDefKind(mi.definitionType)
        val defStr = createString(mi.definition)
        MirrorStructInfo.createMirrorStructInfo(builder, defKind, defStr, mi.mutable)
    }

    FbStructDefinition.startStructDefinition(builder)
    FbStructDefinition.addDefName(builder, defName)
    FbStructDefinition.addName(builder, name)
    FbStructDefinition.addAttributes(builder, attrs)
    FbStructDefinition.addFlags(builder, flags)
    if (mirrorInfo != null) FbStructDefinition.addMirrorInfo(builder, mirrorInfo)
    FbStructDefinition.addHasDefaultConstructor(builder, struct.hasDefaultConstructor)
    return FbStructDefinition.endStructDefinition(builder)
}

fun SerializerContext.serializeEnumDefinition(enum: RR_EnumDefinition): Int {
    val defName = serializeDefinitionName(enum.base.defName)
    val attrs = enum.attrs.map { attr ->
        val attrName = createString(attr.name.str)
        FbEnumAttr.createEnumAttr(builder, attrName, attr.value)
    }.toIntArray()
    val attrsVec = builder.createVectorOfTables(attrs)

    FbEnumDefinition.startEnumDefinition(builder)
    FbEnumDefinition.addDefName(builder, defName)
    FbEnumDefinition.addAttrs(builder, attrsVec)
    return FbEnumDefinition.endEnumDefinition(builder)
}

fun SerializerContext.serializeObjectDefinition(obj: RR_ObjectDefinition): Int {
    val defName = serializeDefinitionName(obj.base.defName)
    val entityIdx = resolveEntityIndex(obj.rEntity).toUInt()

    FbObjectDefinition.startObjectDefinition(builder)
    FbObjectDefinition.addDefName(builder, defName)
    FbObjectDefinition.addEntityDefIndex(builder, entityIdx)
    return FbObjectDefinition.endObjectDefinition(builder)
}

fun SerializerContext.serializeFunctionDefinition(fn: RR_FunctionDefinition): Int {
    val defName = serializeDefinitionName(fn.base.defName)
    val bodyOff = serializeFunctionBody(fn.fnBase)

    FbFunctionDefinition.startFunctionDefinition(builder)
    FbFunctionDefinition.addDefName(builder, defName)
    FbFunctionDefinition.addBody(builder, bodyOff)
    FbFunctionDefinition.addIsTest(builder, fn.isTest)
    return FbFunctionDefinition.endFunctionDefinition(builder)
}

fun SerializerContext.serializeOperationDefinition(op: RR_OperationDefinition): Int {
    val defName = serializeDefinitionName(op.base.defName)
    val mountName = serializeMountName(op.mountName)
    val modifiers = OperationModifiers.createOperationModifiers(
        builder, op.modifiers.isCompound, op.modifiers.isSingular,
    )
    val body = serializeOperationBody(op)
    val guardBody = op.guardBody?.let { serializeStmt(it) }

    FbOperationDefinition.startOperationDefinition(builder)
    FbOperationDefinition.addDefName(builder, defName)
    FbOperationDefinition.addMountName(builder, mountName)
    FbOperationDefinition.addModifiers(builder, modifiers)
    FbOperationDefinition.addBody(builder, body)
    if (guardBody != null) FbOperationDefinition.addGuardBody(builder, guardBody)
    return FbOperationDefinition.endOperationDefinition(builder)
}

fun SerializerContext.serializeQueryDefinition(query: RR_QueryDefinition): Int {
    val defName = serializeDefinitionName(query.base.defName)
    val mountName = serializeMountName(query.mountName)
    val body = serializeQueryBody(query.body)

    FbQueryDefinition.startQueryDefinition(builder)
    FbQueryDefinition.addDefName(builder, defName)
    FbQueryDefinition.addMountName(builder, mountName)
    FbQueryDefinition.addBody(builder, body)
    return FbQueryDefinition.endQueryDefinition(builder)
}

fun SerializerContext.serializeGlobalConstantDefinition(const: RR_GlobalConstantDefinition): Int {
    val defName = serializeDefinitionName(const.base.defName)
    val type = serializeType(const.type)
    val value = serializeExpr(const.expr)
    val frame = serializeFrameDescriptor(const.base.initFrame)
    val metaGtvOff = const.metaGtvJson?.let { b64 ->
        FbGlobalConstantDefinition.createMetaGtvVector(builder, GtvBinaryHelper.base64ToBinary(b64).toUByteArray())
    }

    FbGlobalConstantDefinition.startGlobalConstantDefinition(builder)
    FbGlobalConstantDefinition.addDefName(builder, defName)
    FbGlobalConstantDefinition.addConstIndex(builder, const.constId.index.toUInt())
    FbGlobalConstantDefinition.addType(builder, type)
    FbGlobalConstantDefinition.addValue(builder, value)
    FbGlobalConstantDefinition.addFrame(builder, frame)
    if (metaGtvOff != null) FbGlobalConstantDefinition.addMetaGtv(builder, metaGtvOff)
    return FbGlobalConstantDefinition.endGlobalConstantDefinition(builder)
}

// --- Helpers ---

private fun SerializerContext.serializeAttributes(attrs: List<RR_Attribute>): Int {
    val offsets = attrs.map { attr ->
        val name = createString(attr.rName.str)
        val type = serializeType(attr.type)
        val sqlMapping = createString(attr.sqlMapping)
        val defaultExprOff = attr.defaultExpr?.let { serializeExpr(it) }
        val sizeConstraintOff = attr.sizeConstraint?.let { serializeSizeConstraint(it) }

        FbAttribute.startAttribute(builder)
        FbAttribute.addIndex(builder, attr.index)
        FbAttribute.addName(builder, name)
        FbAttribute.addType(builder, type)
        FbAttribute.addMutable(builder, attr.mutable)
        attr.keyIndexKind?.let { kik ->
            val fbKind = when (kik) {
                net.postchain.rell.base.model.KeyIndexKind.KEY -> FbKeyIndexKind.KEY
                net.postchain.rell.base.model.KeyIndexKind.INDEX -> FbKeyIndexKind.INDEX
            }
            builder.forcedScalar { FbAttribute.addKeyIndexKind(builder, fbKind) }
        }
        FbAttribute.addSqlMapping(builder, sqlMapping)
        FbAttribute.addCanSetInCreate(builder, attr.canSetInCreate)
        if (defaultExprOff != null) FbAttribute.addDefaultExpr(builder, defaultExprOff)
        FbAttribute.addIsDbModification(builder, attr.isDbModification)
        if (sizeConstraintOff != null) FbAttribute.addSizeConstraint(builder, sizeConstraintOff)
        FbAttribute.endAttribute(builder)
    }.toIntArray()
    return builder.createVectorOfTables(offsets)
}

private fun SerializerContext.serializeSizeConstraint(sc: RR_SizeConstraint): Int {
    val codePrefix = createString(sc.codePrefix)
    val kind = when (sc.kind) {
        RR_SizeConstraintKind.BYTE_ARRAY -> SizeConstraintKind.BYTE_ARRAY
        RR_SizeConstraintKind.TEXT -> SizeConstraintKind.TEXT
    }
    SizeConstraint.startSizeConstraint(builder)
    sc.min?.let { builder.forcedScalar { SizeConstraint.addMin(builder, it) } }
    sc.max?.let { builder.forcedScalar { SizeConstraint.addMax(builder, it) } }
    SizeConstraint.addKind(builder, kind)
    SizeConstraint.addCodePrefix(builder, codePrefix)
    return SizeConstraint.endSizeConstraint(builder)
}

private fun SerializerContext.serializeEntityFlags(flags: net.postchain.rell.base.model.EntityFlags): Int {
    FbEntityFlags.startEntityFlags(builder)
    FbEntityFlags.addIsObject(builder, flags.isObject)
    FbEntityFlags.addCanCreate(builder, flags.canCreate)
    FbEntityFlags.addCanUpdate(builder, flags.canUpdate)
    FbEntityFlags.addCanDelete(builder, flags.canDelete)
    FbEntityFlags.addGtv(builder, flags.gtv)
    FbEntityFlags.addLog(builder, flags.log)
    return FbEntityFlags.endEntityFlags(builder)
}

private fun SerializerContext.serializeEntitySqlMapping(mapping: RR_EntitySqlMapping): Int {
    val mountName = serializeMountName(mapping.mountName)
    val kind = when (mapping.kind) {
        RR_EntitySqlMappingKind.REGULAR -> EntitySqlMappingKind.REGULAR
        RR_EntitySqlMappingKind.EXTERNAL -> EntitySqlMappingKind.EXTERNAL
        RR_EntitySqlMappingKind.TRANSACTION -> EntitySqlMappingKind.TRANSACTION
        RR_EntitySqlMappingKind.BLOCK -> EntitySqlMappingKind.BLOCK
    }

    val metaName = createString(mapping.metaName)
    val rowidColumn = createString(mapping.rowidColumn)
    EntitySqlMapping.startEntitySqlMapping(builder)
    EntitySqlMapping.addKind(builder, kind)
    EntitySqlMapping.addMountName(builder, mountName)
    if (mapping.externalChainIndex >= 0) {
        builder.forcedScalar { EntitySqlMapping.addChainIndex(builder, mapping.externalChainIndex) }
    }
    EntitySqlMapping.addMetaName(builder, metaName)
    EntitySqlMapping.addRowidColumn(builder, rowidColumn)
    EntitySqlMapping.addAutoCreateTable(builder, mapping.autoCreateTable)
    EntitySqlMapping.addIsSystemEntity(builder, mapping.isSystemEntity)
    return EntitySqlMapping.endEntitySqlMapping(builder)
}

private fun SerializerContext.serializeExternalEntity(ext: RR_ExternalEntity): Int {
    val chainName = createString(ext.chainName)
    ExternalEntity.startExternalEntity(builder)
    ExternalEntity.addMetaCheck(builder, ext.metaCheck)
    ExternalEntity.addChainName(builder, chainName)
    return ExternalEntity.endExternalEntity(builder)
}

private fun serializeMirrorStructDefKind(defTypeName: String): UByte = when (defTypeName) {
    "ENTITY" -> MirrorStructDefKind.ENTITY
    "OBJECT" -> MirrorStructDefKind.OBJECT
    "OPERATION" -> MirrorStructDefKind.OPERATION
    else -> error("Unknown mirror struct def type: $defTypeName")
}

private fun SerializerContext.serializeKeyIndices(keyIndices: List<KeyIndex>): Int {
    val offsets = keyIndices.map { key ->
        val attrNames = createStringVector(key.attribs.map { it.str })
        FbKeyIndex.createKeyIndex(builder, attrNames)
    }.toIntArray()
    return builder.createVectorOfTables(offsets)
}

internal fun SerializerContext.serializeFunctionBody(fnBase: RR_FunctionBase): Int {
    val type = serializeType(fnBase.resultType)
    val params = serializeFunctionParams(fnBase.params)
    FbFunctionBody.startParamPtrsVector(builder, fnBase.paramVars.size)
    for (i in fnBase.paramVars.indices.reversed()) {
        val pv = fnBase.paramVars[i]
        VarPtr.createVarPtr(builder, pv.ptr.blockUid.toUInt(), pv.ptr.offset)
    }
    val paramPtrsVec = builder.endVector()
    val stmt = serializeStmt(fnBase.body)
    val frame = serializeFrameDescriptor(fnBase.frame)
    val defName = serializeDefinitionName(fnBase.defName)

    FbFunctionBody.startFunctionBody(builder)
    FbFunctionBody.addType(builder, type)
    FbFunctionBody.addParams(builder, params)
    FbFunctionBody.addParamPtrs(builder, paramPtrsVec)
    FbFunctionBody.addBody(builder, stmt)
    FbFunctionBody.addFrame(builder, frame)
    FbFunctionBody.addDefName(builder, defName)
    return FbFunctionBody.endFunctionBody(builder)
}

private fun SerializerContext.serializeOperationBody(op: RR_OperationDefinition): Int {
    val typeOff = serializeType(RR_Type.Primitive(RR_PrimitiveKind.UNIT))
    val paramsOff = serializeFunctionParams(op.params)
    FbFunctionBody.startParamPtrsVector(builder, op.paramVars.size)
    for (i in op.paramVars.indices.reversed()) {
        val pv = op.paramVars[i]
        VarPtr.createVarPtr(builder, pv.ptr.blockUid.toUInt(), pv.ptr.offset)
    }
    val paramPtrsVec = builder.endVector()
    val stmtOff = serializeStmt(op.body)
    val frameOff = serializeFrameDescriptor(op.frame)

    FbFunctionBody.startFunctionBody(builder)
    FbFunctionBody.addType(builder, typeOff)
    FbFunctionBody.addParams(builder, paramsOff)
    FbFunctionBody.addParamPtrs(builder, paramPtrsVec)
    FbFunctionBody.addBody(builder, stmtOff)
    FbFunctionBody.addFrame(builder, frameOff)
    return FbFunctionBody.endFunctionBody(builder)
}

internal fun SerializerContext.serializeFunctionParams(params: List<RR_FunctionParam>): Int {
    val offsets = params.map { param ->
        val name = createString(param.name.str)
        val type = serializeType(param.type)
        val initFrame = serializeFrameDescriptor(param.initFrame)
        val defaultExpr = param.defaultExpr?.let { serializeExpr(it) }
        val sizeConstraint = param.sizeConstraint?.let { serializeSizeConstraint(it) }
        FbFunctionParam.startFunctionParam(builder)
        FbFunctionParam.addName(builder, name)
        FbFunctionParam.addType(builder, type)
        FbFunctionParam.addInitFrame(builder, initFrame)
        if (defaultExpr != null) FbFunctionParam.addDefaultExpr(builder, defaultExpr)
        if (sizeConstraint != null) FbFunctionParam.addSizeConstraint(builder, sizeConstraint)
        FbFunctionParam.endFunctionParam(builder)
    }.toIntArray()
    return builder.createVectorOfTables(offsets)
}

private fun SerializerContext.serializeQueryBody(body: RR_QueryBody): Int {
    val (unionType, unionOffset) = when (body) {
        is RR_UserQueryBody -> {
            val retType = serializeType(body.retType)
            val params = serializeFunctionParams(body.params)
            UserQueryBody.startParamPtrsVector(builder, body.paramVars.size)
            for (i in body.paramVars.indices.reversed()) {
                val pv = body.paramVars[i]
                VarPtr.createVarPtr(builder, pv.ptr.blockUid.toUInt(), pv.ptr.offset)
            }
            val paramPtrsVec = builder.endVector()
            val stmt = serializeStmt(body.body)
            val frame = serializeFrameDescriptor(body.frame)
            UserQueryBody.startUserQueryBody(builder)
            UserQueryBody.addRetType(builder, retType)
            UserQueryBody.addParams(builder, params)
            UserQueryBody.addParamPtrs(builder, paramPtrsVec)
            UserQueryBody.addBody(builder, stmt)
            UserQueryBody.addFrame(builder, frame)
            QueryBodyUnion.UserQueryBody to UserQueryBody.endUserQueryBody(builder)
        }

        is RR_SysQueryBody -> {
            val retType = serializeType(body.retType)
            val params = serializeFunctionParams(body.params)
            val fnName = createString(body.fnName)
            SysQueryBody.startSysQueryBody(builder)
            SysQueryBody.addRetType(builder, retType)
            SysQueryBody.addParams(builder, params)
            SysQueryBody.addFnName(builder, fnName)
            QueryBodyUnion.SysQueryBody to SysQueryBody.endSysQueryBody(builder)
        }
    }

    QueryBody.startQueryBody(builder)
    QueryBody.addBodyType(builder, unionType)
    QueryBody.addBody(builder, unionOffset)
    return QueryBody.endQueryBody(builder)
}
