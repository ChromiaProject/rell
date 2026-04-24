/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import net.postchain.rell.base.model.DefinitionName
import net.postchain.rell.base.model.rr.*
import rell.ir.*
import rell.ir.Expr as FbExpr

fun SerializerContext.serializeExpr(expr: RR_Expr): Int {
    val (unionType, unionOffset) = serializeRRExprUnion(expr)
    FbExpr.startExpr(builder)
    FbExpr.addExprType(builder, unionType)
    FbExpr.addExpr(builder, unionOffset)
    return FbExpr.endExpr(builder)
}

fun SerializerContext.serializeExprList(exprs: List<RR_Expr>): Int {
    val offsets = exprs.map { serializeExpr(it) }.toIntArray()
    return builder.createVectorOfTables(offsets)
}

private fun SerializerContext.serializeRRExprUnion(expr: RR_Expr): Pair<UByte, Int> {
    val type = serializeType(expr.type)
    return when (expr) {
        is RR_Expr.Var -> {
            val name = createString(expr.name)
            VarExpr.startVarExpr(builder)
            VarExpr.addType(builder, type)
            VarExpr.addPtr(builder, VarPtr.createVarPtr(builder, expr.ptr.blockUid.toUInt(), expr.ptr.offset))
            VarExpr.addName(builder, name)
            ExprUnion.VarExpr to VarExpr.endVarExpr(builder)
        }

        is RR_Expr.ConstantValue -> {
            val tv = serializeRRConstantValue(expr.type, expr.value)
            ExprUnion.ConstantValueExpr to ConstantValueExpr.createConstantValueExpr(builder, tv)
        }

        is RR_Expr.Binary -> {
            val left = serializeExpr(expr.left)
            val right = serializeExpr(expr.right)
            val errPos = expr.errPos?.let { serializeErrorPos(it) }
            val cmpOff = expr.cmpInfo?.let { ci ->
                CmpInfo.createCmpInfo(builder, serializeCmpOp(ci.cmpOp), serializeCmpType(ci.cmpType))
            }
            BinaryExpr.startBinaryExpr(builder)
            BinaryExpr.addType(builder, type)
            BinaryExpr.addOp(builder, serializeRRBinaryOp(expr.op))
            BinaryExpr.addLeft(builder, left)
            BinaryExpr.addRight(builder, right)
            if (errPos != null) BinaryExpr.addErrPos(builder, errPos)
            if (cmpOff != null) BinaryExpr.addCmp(builder, cmpOff)
            ExprUnion.BinaryExpr to BinaryExpr.endBinaryExpr(builder)
        }

        is RR_Expr.Unary -> {
            val inner = serializeExpr(expr.expr)
            val errPos = serializeErrorPos(expr.errPos)
            UnaryExpr.startUnaryExpr(builder)
            UnaryExpr.addType(builder, type)
            UnaryExpr.addOp(builder, serializeRRUnaryOp(expr.op))
            UnaryExpr.addExpr(builder, inner)
            UnaryExpr.addErrPos(builder, errPos)
            ExprUnion.UnaryExpr to UnaryExpr.endUnaryExpr(builder)
        }

        is RR_Expr.If -> {
            val cond = serializeExpr(expr.cond)
            val t = serializeExpr(expr.trueExpr)
            val f = serializeExpr(expr.falseExpr)
            IfExpr.startIfExpr(builder)
            IfExpr.addType(builder, type)
            IfExpr.addCond(builder, cond)
            IfExpr.addTrueExpr(builder, t)
            IfExpr.addFalseExpr(builder, f)
            ExprUnion.IfExpr to IfExpr.endIfExpr(builder)
        }

        is RR_Expr.When -> {
            val chooser = serializeRRWhenChooser(expr.chooser)
            val exprs = expr.exprs.map { serializeExpr(it) }.toIntArray()
            val exprsVec = builder.createVectorOfTables(exprs)
            WhenExpr.startWhenExpr(builder)
            WhenExpr.addType(builder, type)
            WhenExpr.addChooser(builder, chooser)
            WhenExpr.addExprs(builder, exprsVec)
            ExprUnion.WhenExpr to WhenExpr.endWhenExpr(builder)
        }

        is RR_Expr.Elvis -> {
            val left = serializeExpr(expr.left)
            val right = serializeExpr(expr.right)
            ElvisExpr.startElvisExpr(builder)
            ElvisExpr.addType(builder, type)
            ElvisExpr.addLeft(builder, left)
            ElvisExpr.addRight(builder, right)
            ExprUnion.ElvisExpr to ElvisExpr.endElvisExpr(builder)
        }

        is RR_Expr.NotNull -> {
            val inner = serializeExpr(expr.expr)
            val errPos = serializeErrorPos(expr.errPos)
            NotNullExpr.startNotNullExpr(builder)
            NotNullExpr.addType(builder, type)
            NotNullExpr.addExpr(builder, inner)
            NotNullExpr.addErrPos(builder, errPos)
            ExprUnion.NotNullExpr to NotNullExpr.endNotNullExpr(builder)
        }

        is RR_Expr.TupleLiteral -> {
            val exprs = serializeExprList(expr.exprs)
            TupleExpr.startTupleExpr(builder)
            TupleExpr.addType(builder, type)
            TupleExpr.addExprs(builder, exprs)
            ExprUnion.TupleExpr to TupleExpr.endTupleExpr(builder)
        }

        is RR_Expr.ListLiteral -> {
            val exprs = serializeExprList(expr.exprs)
            ListLiteralExpr.startListLiteralExpr(builder)
            ListLiteralExpr.addType(builder, type)
            ListLiteralExpr.addExprs(builder, exprs)
            ExprUnion.ListLiteralExpr to ListLiteralExpr.endListLiteralExpr(builder)
        }

        is RR_Expr.MapLiteral -> {
            val keys = serializeExprList(expr.keys)
            val values = serializeExprList(expr.values)
            val errPos = serializeErrorPos(expr.errPos)
            MapLiteralExpr.startMapLiteralExpr(builder)
            MapLiteralExpr.addType(builder, type)
            MapLiteralExpr.addKeys(builder, keys)
            MapLiteralExpr.addValues(builder, values)
            MapLiteralExpr.addErrPos(builder, errPos)
            ExprUnion.MapLiteralExpr to MapLiteralExpr.endMapLiteralExpr(builder)
        }

        is RR_Expr.StructCreate -> {
            val attrs = serializeRRCreateAttrs(expr.attrs)
            StructExpr.startStructExpr(builder)
            StructExpr.addStructDefIndex(builder, expr.structDefIndex.toUInt())
            StructExpr.addAttrs(builder, attrs)
            ExprUnion.StructExpr to StructExpr.endStructExpr(builder)
        }

        is RR_Expr.RegularCreate -> {
            val errPos = serializeErrorPos(expr.errPos)
            val attrs = serializeRRCreateAttrs(expr.attrs)
            RegularCreateExpr.startRegularCreateExpr(builder)
            RegularCreateExpr.addEntityDefIndex(builder, expr.entityDefIndex.toUInt())
            RegularCreateExpr.addErrPos(builder, errPos)
            RegularCreateExpr.addAttrs(builder, attrs)
            ExprUnion.RegularCreateExpr to RegularCreateExpr.endRegularCreateExpr(builder)
        }

        is RR_Expr.StructEntityCreate -> {
            val errPos = serializeErrorPos(expr.errPos)
            val structExpr = serializeExpr(expr.structExpr)
            StructCreateExpr.startStructCreateExpr(builder)
            StructCreateExpr.addEntityDefIndex(builder, expr.entityDefIndex.toUInt())
            StructCreateExpr.addErrPos(builder, errPos)
            StructCreateExpr.addStructDefIndex(builder, expr.structDefIndex.toUInt())
            StructCreateExpr.addStructExpr(builder, structExpr)
            ExprUnion.StructCreateExpr to StructCreateExpr.endStructCreateExpr(builder)
        }

        is RR_Expr.StructListCreate -> {
            val errPos = serializeErrorPos(expr.errPos)
            val resultType = serializeType(expr.resultListType)
            val listExpr = serializeExpr(expr.listExpr)
            StructListCreateExpr.startStructListCreateExpr(builder)
            StructListCreateExpr.addEntityDefIndex(builder, expr.entityDefIndex.toUInt())
            StructListCreateExpr.addErrPos(builder, errPos)
            StructListCreateExpr.addStructDefIndex(builder, expr.structDefIndex.toUInt())
            StructListCreateExpr.addResultListType(builder, resultType)
            StructListCreateExpr.addListExpr(builder, listExpr)
            ExprUnion.StructListCreateExpr to StructListCreateExpr.endStructListCreateExpr(builder)
        }

        is RR_Expr.FunctionCall -> {
            val base = expr.base?.let { serializeExpr(it) }
            val call = serializeRRFunctionCall(expr.call)
            FunctionCallExpr.startFunctionCallExpr(builder)
            FunctionCallExpr.addType(builder, type)
            if (base != null) FunctionCallExpr.addBase(builder, base)
            FunctionCallExpr.addCall(builder, call)
            FunctionCallExpr.addSafe(builder, expr.safe)
            ExprUnion.FunctionCallExpr to FunctionCallExpr.endFunctionCallExpr(builder)
        }

        is RR_Expr.MemberAccess -> {
            val base = serializeExpr(expr.base)
            val calc = serializeRRMemberCalculator(expr.calculator)
            MemberExpr.startMemberExpr(builder)
            MemberExpr.addBase(builder, base)
            MemberExpr.addCalculator(builder, calc)
            MemberExpr.addSafe(builder, expr.safe)
            ExprUnion.MemberExpr to MemberExpr.endMemberExpr(builder)
        }

        is RR_Expr.Assign -> {
            val dst = serializeExpr(expr.dstExpr)
            val src = serializeExpr(expr.srcExpr)
            AssignExpr.startAssignExpr(builder)
            AssignExpr.addType(builder, type)
            val assignOp = expr.op
            if (assignOp != null) AssignExpr.addOp(builder, serializeRRBinaryOp(assignOp))
            AssignExpr.addDstExpr(builder, dst)
            AssignExpr.addSrcExpr(builder, src)
            AssignExpr.addPost(builder, expr.post)
            ExprUnion.AssignExpr to AssignExpr.endAssignExpr(builder)
        }

        is RR_Expr.StatementExpr -> {
            val stmt = serializeStmt(expr.stmt)
            StatementExpr.startStatementExpr(builder)
            StatementExpr.addType(builder, type)
            StatementExpr.addStmt(builder, stmt)
            ExprUnion.StatementExpr to StatementExpr.endStatementExpr(builder)
        }

        is RR_Expr.GlobalConstant -> {
            GlobalConstantExpr.startGlobalConstantExpr(builder)
            GlobalConstantExpr.addType(builder, type)
            GlobalConstantExpr.addConstDefIndex(builder, expr.constDefIndex.toUInt())
            ExprUnion.GlobalConstantExpr to GlobalConstantExpr.endGlobalConstantExpr(builder)
        }

        is RR_Expr.ChainHeight -> {
            val chain = serializeExternalChainRef(expr.chainIndex)
            ChainHeightExpr.startChainHeightExpr(builder)
            ChainHeightExpr.addChain(builder, chain)
            ExprUnion.ChainHeightExpr to ChainHeightExpr.endChainHeightExpr(builder)
        }

        is RR_Expr.TypeAdapter -> {
            val inner = serializeExpr(expr.expr)
            val adapter = serializeRRTypeAdapter(expr.adapter)
            TypeAdapterExpr.startTypeAdapterExpr(builder)
            TypeAdapterExpr.addType(builder, type)
            TypeAdapterExpr.addExpr(builder, inner)
            TypeAdapterExpr.addAdapter(builder, adapter)
            ExprUnion.TypeAdapterExpr to TypeAdapterExpr.endTypeAdapterExpr(builder)
        }

        is RR_Expr.ParameterDefaultValue -> {
            val callFilePos = serializeFilePos(expr.callFilePos)
            val initFrame = serializeFrameDescriptor(expr.initFrame)
            val innerExpr = serializeExpr(expr.innerExpr)
            val defIdName =
                serializeDefinitionName(DefinitionName(expr.defId.module, expr.defId.definition, expr.defId.definition))
            ParameterDefaultValueExpr.startParameterDefaultValueExpr(builder)
            ParameterDefaultValueExpr.addType(builder, type)
            ParameterDefaultValueExpr.addCallFilePos(builder, callFilePos)
            ParameterDefaultValueExpr.addInitFrame(builder, initFrame)
            ParameterDefaultValueExpr.addInnerExpr(builder, innerExpr)
            ParameterDefaultValueExpr.addDefId(builder, defIdName)
            ExprUnion.ParameterDefaultValueExpr to ParameterDefaultValueExpr.endParameterDefaultValueExpr(builder)
        }

        is RR_Expr.AttributeDefaultValue -> {
            val attrName = createString(expr.attrName)
            val createFilePos = expr.createFilePos?.let { serializeFilePos(it) }
            val initFrame = serializeFrameDescriptor(expr.initFrame)
            val innerExpr = serializeExpr(expr.innerExpr)
            val defIdName =
                serializeDefinitionName(DefinitionName(expr.defId.module, expr.defId.definition, expr.defId.definition))
            AttributeDefaultValueExpr.startAttributeDefaultValueExpr(builder)
            AttributeDefaultValueExpr.addAttrIndex(builder, expr.attrIndex)
            AttributeDefaultValueExpr.addAttrName(builder, attrName)
            if (createFilePos != null) AttributeDefaultValueExpr.addCreateFilePos(builder, createFilePos)
            AttributeDefaultValueExpr.addInitFrame(builder, initFrame)
            AttributeDefaultValueExpr.addInnerExpr(builder, innerExpr)
            AttributeDefaultValueExpr.addDefId(builder, defIdName)
            ExprUnion.AttributeDefaultValueExpr to AttributeDefaultValueExpr.endAttributeDefaultValueExpr(builder)
        }

        is RR_Expr.Error -> {
            val msg = createString(expr.message)
            ErrorExpr.startErrorExpr(builder)
            ErrorExpr.addType(builder, type)
            ErrorExpr.addMessage(builder, msg)
            ExprUnion.ErrorExpr to ErrorExpr.endErrorExpr(builder)
        }

        is RR_Expr.ListSubscript -> {
            val b = serializeExpr(expr.base)
            val i = serializeExpr(expr.index)
            val e = serializeErrorPos(expr.errPos)
            ListSubscriptExpr.startListSubscriptExpr(builder)
            ListSubscriptExpr.addType(builder, type)
            ListSubscriptExpr.addBase(builder, b)
            ListSubscriptExpr.addIndex(builder, i)
            ListSubscriptExpr.addErrPos(builder, e)
            ExprUnion.ListSubscriptExpr to ListSubscriptExpr.endListSubscriptExpr(builder)
        }

        is RR_Expr.MapSubscript -> {
            val b = serializeExpr(expr.base)
            val k = serializeExpr(expr.key)
            val e = serializeErrorPos(expr.errPos)
            MapSubscriptExpr.startMapSubscriptExpr(builder)
            MapSubscriptExpr.addType(builder, type)
            MapSubscriptExpr.addBase(builder, b)
            MapSubscriptExpr.addKey(builder, k)
            MapSubscriptExpr.addErrPos(builder, e)
            ExprUnion.MapSubscriptExpr to MapSubscriptExpr.endMapSubscriptExpr(builder)
        }

        is RR_Expr.TextSubscript -> {
            val b = serializeExpr(expr.base)
            val i = serializeExpr(expr.index)
            val e = serializeErrorPos(expr.errPos)
            TextSubscriptExpr.startTextSubscriptExpr(builder)
            TextSubscriptExpr.addBase(builder, b)
            TextSubscriptExpr.addIndex(builder, i)
            TextSubscriptExpr.addErrPos(builder, e)
            ExprUnion.TextSubscriptExpr to TextSubscriptExpr.endTextSubscriptExpr(builder)
        }

        is RR_Expr.ByteArraySubscript -> {
            val b = serializeExpr(expr.base)
            val i = serializeExpr(expr.index)
            val e = serializeErrorPos(expr.errPos)
            ByteArraySubscriptExpr.startByteArraySubscriptExpr(builder)
            ByteArraySubscriptExpr.addBase(builder, b)
            ByteArraySubscriptExpr.addIndex(builder, i)
            ByteArraySubscriptExpr.addErrPos(builder, e)
            ExprUnion.ByteArraySubscriptExpr to ByteArraySubscriptExpr.endByteArraySubscriptExpr(builder)
        }

        is RR_Expr.VirtualListSubscript -> {
            val b = serializeExpr(expr.base)
            val i = serializeExpr(expr.index)
            VirtualListSubscriptExpr.startVirtualListSubscriptExpr(builder)
            VirtualListSubscriptExpr.addType(builder, type)
            VirtualListSubscriptExpr.addBase(builder, b)
            VirtualListSubscriptExpr.addIndex(builder, i)
            ExprUnion.VirtualListSubscriptExpr to VirtualListSubscriptExpr.endVirtualListSubscriptExpr(builder)
        }

        is RR_Expr.VirtualMapSubscript -> {
            val b = serializeExpr(expr.base)
            val k = serializeExpr(expr.key)
            val e = serializeErrorPos(expr.errPos)
            VirtualMapSubscriptExpr.startVirtualMapSubscriptExpr(builder)
            VirtualMapSubscriptExpr.addType(builder, type)
            VirtualMapSubscriptExpr.addBase(builder, b)
            VirtualMapSubscriptExpr.addKey(builder, k)
            VirtualMapSubscriptExpr.addErrPos(builder, e)
            ExprUnion.VirtualMapSubscriptExpr to VirtualMapSubscriptExpr.endVirtualMapSubscriptExpr(builder)
        }

        is RR_Expr.JsonArraySubscript -> {
            val b = serializeExpr(expr.base)
            val i = serializeExpr(expr.index)
            val e = serializeErrorPos(expr.errPos)
            JsonArraySubscriptExpr.startJsonArraySubscriptExpr(builder)
            JsonArraySubscriptExpr.addBase(builder, b)
            JsonArraySubscriptExpr.addIndex(builder, i)
            JsonArraySubscriptExpr.addErrPos(builder, e)
            ExprUnion.JsonArraySubscriptExpr to JsonArraySubscriptExpr.endJsonArraySubscriptExpr(builder)
        }

        is RR_Expr.JsonObjectSubscript -> {
            val b = serializeExpr(expr.base)
            val k = serializeExpr(expr.key)
            val e = serializeErrorPos(expr.errPos)
            JsonObjectSubscriptExpr.startJsonObjectSubscriptExpr(builder)
            JsonObjectSubscriptExpr.addBase(builder, b)
            JsonObjectSubscriptExpr.addKey(builder, k)
            JsonObjectSubscriptExpr.addErrPos(builder, e)
            ExprUnion.JsonObjectSubscriptExpr to JsonObjectSubscriptExpr.endJsonObjectSubscriptExpr(builder)
        }

        is RR_Expr.StructMember -> {
            val base = serializeExpr(expr.base)
            val name = createString(expr.attrName)
            StructMemberExpr.startStructMemberExpr(builder)
            StructMemberExpr.addType(builder, type)
            StructMemberExpr.addBase(builder, base)
            StructMemberExpr.addAttrName(builder, name)
            StructMemberExpr.addAttrIndex(builder, expr.attrIndex)
            ExprUnion.StructMemberExpr to StructMemberExpr.endStructMemberExpr(builder)
        }

        is RR_Expr.ObjectValue -> {
            ExprUnion.ObjectValueExpr to ObjectValueExpr.createObjectValueExpr(
                builder,
                type,
                expr.objectDefIndex.toUInt(),
            )
        }

        is RR_Expr.DbAt -> {
            val from = serializeDbAtFrom(expr.from)
            val what = serializeDbAtWhatFields(expr.what)
            val where = expr.where?.let { serializeDbExpr(it) }
            val internals = serializeDbAtInternals(expr.internals)
            val errPos = serializeErrorPos(expr.errPos)
            val extras = expr.extras?.let { serializeAtExtras(it) }
            val whatFieldGroups = expr.whatFieldGroups?.let { serializeWhatFieldGroups(it) }
            val objectName = expr.objectName?.let { createString(it) }
            DbAtExpr.startDbAtExpr(builder)
            DbAtExpr.addType(builder, type)
            DbAtExpr.addFrom(builder, from)
            DbAtExpr.addWhat(builder, what)
            if (where != null) DbAtExpr.addWhere(builder, where)
            DbAtExpr.addCardinality(builder, serializeAtCardinality(expr.cardinality))
            if (extras != null) DbAtExpr.addExtras(builder, extras)
            DbAtExpr.addInternals(builder, internals)
            DbAtExpr.addErrPos(builder, errPos)
            if (whatFieldGroups != null) DbAtExpr.addWhatFieldGroups(builder, whatFieldGroups)
            if (objectName != null) DbAtExpr.addObjectName(builder, objectName)
            expr.objectDefIndex?.let { builder.forcedScalar { DbAtExpr.addObjectDefIndex(builder, it) } }
            ExprUnion.DbAtExpr to DbAtExpr.endDbAtExpr(builder)
        }

        is RR_Expr.ColAt -> {
            val block = serializeFrameBlock(expr.block)
            val param = serializeColAtParam(expr.param)
            val from = serializeColAtFrom(expr.from)
            val what = serializeColAtWhat(expr.what)
            val where = serializeExpr(expr.where)
            val errPos = serializeErrorPos(expr.errPos)
            val extras = expr.extras?.let { serializeAtExtras(it) }
            val fieldSumVec = if (expr.fieldSummarizations.isNotEmpty()) {
                val sumInfos = expr.fieldSummarizations.map { info ->
                    val zeroVal = info.zeroValue?.let { serializeRRUntypedConstantValue(it) }
                    val colType = info.collectionType?.let { serializeType(it) }
                    val mapValType = info.mapValueType?.let { serializeType(it) }
                    val kind = serializeColAtFieldSummarizationKind(info.kind)
                    ColAtFieldSummarizationInfo.startColAtFieldSummarizationInfo(builder)
                    ColAtFieldSummarizationInfo.addKind(builder, kind)
                    info.binaryOpKey?.let {
                        builder.forcedScalar {
                            ColAtFieldSummarizationInfo.addBinaryOp(builder, serializeRRBinaryOp(it))
                        }
                    }
                    if (zeroVal != null) ColAtFieldSummarizationInfo.addZeroValue(builder, zeroVal)
                    if (info.isMin != null) ColAtFieldSummarizationInfo.addIsMin(builder, info.isMin!!)
                    if (colType != null) ColAtFieldSummarizationInfo.addCollectionType(builder, colType)
                    if (mapValType != null) ColAtFieldSummarizationInfo.addMapValueType(builder, mapValType)
                    ColAtFieldSummarizationInfo.endColAtFieldSummarizationInfo(builder)
                }.toIntArray()
                builder.createVectorOfTables(sumInfos)
            } else 0
            val sortVec = if (expr.sorting.isNotEmpty()) {
                val sorts = expr.sorting.map { s ->
                    ColAtSortEntry.createColAtSortEntry(builder, s.fieldIndex, s.ascending)
                }.toIntArray()
                builder.createVectorOfTables(sorts)
            } else 0
            ColAtExpr.startColAtExpr(builder)
            ColAtExpr.addType(builder, type)
            ColAtExpr.addBlock(builder, block)
            ColAtExpr.addParam(builder, param)
            ColAtExpr.addFrom(builder, from)
            ColAtExpr.addWhat(builder, what)
            ColAtExpr.addWhere(builder, where)
            ColAtExpr.addSummarization(builder, serializeColAtSummarizationKind(expr.summarization))
            ColAtExpr.addErrPos(builder, errPos)
            ColAtExpr.addCardinality(builder, serializeAtCardinality(expr.cardinality))
            if (extras != null) ColAtExpr.addExtras(builder, extras)
            if (fieldSumVec != 0) ColAtExpr.addFieldSummarizations(builder, fieldSumVec)
            if (sortVec != 0) ColAtExpr.addSorting(builder, sortVec)
            ExprUnion.ColAtExpr to ColAtExpr.endColAtExpr(builder)
        }

        is RR_Expr.Lazy -> {
            val inner = serializeExpr(expr.innerExpr)
            ExprUnion.LazyExpr to LazyExpr.createLazyExpr(builder, type, inner)
        }
    }
}

// --- Helpers ---

private fun SerializerContext.serializeRRCreateAttrs(attrs: List<RR_CreateAttr>): Int {
    val offsets = attrs.map { attr ->
        val name = createString(attr.attrName)
        val expr = serializeExpr(attr.expr)
        CreateExprAttr.startCreateExprAttr(builder)
        CreateExprAttr.addAttrIndex(builder, attr.attrIndex)
        CreateExprAttr.addAttrName(builder, name)
        CreateExprAttr.addExpr(builder, expr)
        CreateExprAttr.endCreateExprAttr(builder)
    }.toIntArray()
    return builder.createVectorOfTables(offsets)
}

private fun SerializerContext.serializeValueUnion(value: RR_ConstantValue): Pair<UByte, Int> = when (value) {
    is RR_ConstantValue.Null -> {
        NullValue.startNullValue(builder)
        ValueUnion.NullValue to NullValue.endNullValue(builder)
    }

    is RR_ConstantValue.Unit -> {
        UnitValue.startUnitValue(builder)
        ValueUnion.UnitValue to UnitValue.endUnitValue(builder)
    }

    is RR_ConstantValue.Bool -> ValueUnion.BoolValue to BoolValue.createBoolValue(builder, value.value)
    is RR_ConstantValue.Int -> ValueUnion.IntValue to IntValue.createIntValue(builder, value.value)
    is RR_ConstantValue.Text -> {
        val s = createString(value.value)
        ValueUnion.TextValue to TextValue.createTextValue(builder, s)
    }

    is RR_ConstantValue.ByteArray -> {
        val v = ByteArrayValue.createValueVector(builder, value.value.toUByteArray())
        ValueUnion.ByteArrayValue to ByteArrayValue.createByteArrayValue(builder, v)
    }

    is RR_ConstantValue.Decimal -> {
        val s = createString(value.value)
        ValueUnion.DecimalValue to DecimalValue.createDecimalValue(builder, s)
    }

    is RR_ConstantValue.BigInteger -> {
        val s = createString(value.value)
        ValueUnion.BigIntegerValue to BigIntegerValue.createBigIntegerValue(builder, s)
    }

    is RR_ConstantValue.Rowid -> ValueUnion.RowidValue to RowidValue.createRowidValue(builder, value.value)
    is RR_ConstantValue.Enum -> ValueUnion.EnumValue to EnumValue.createEnumValue(
        builder,
        value.enumDefIndex.toUInt(),
        value.enumValue.toUInt(),
    )

    is RR_ConstantValue.Gtv -> {
        val binary = GtvBinaryHelper.jsonToBinary(value.json)
        val v = GtvValue.createValueVector(builder, binary.toUByteArray())
        ValueUnion.GtvValue to GtvValue.createGtvValue(builder, v)
    }

    is RR_ConstantValue.Struct -> {
        val fields = value.fieldValues.map { serializeRRUntypedConstantValue(it) }.toIntArray()
        val fieldsVec = builder.createVectorOfTables(fields)
        ValueUnion.StructConstantValue to StructConstantValue.createStructConstantValue(
            builder,
            value.structDefIndex,
            fieldsVec,
        )
    }

    is RR_ConstantValue.Collection -> {
        val elemType = serializeType(value.elementType)
        val elems = value.elementValues.map { serializeRRUntypedConstantValue(it) }.toIntArray()
        val elemsVec = builder.createVectorOfTables(elems)
        ValueUnion.CollectionConstantValue to CollectionConstantValue.createCollectionConstantValue(
            builder,
            elemType,
            elemsVec,
        )
    }

    is RR_ConstantValue.MapConstant -> {
        val keyType = serializeType(value.keyType)
        val valType = serializeType(value.valueType)
        val keys = value.keys.map { serializeRRUntypedConstantValue(it) }.toIntArray()
        val vals = value.values.map { serializeRRUntypedConstantValue(it) }.toIntArray()
        val keysVec = builder.createVectorOfTables(keys)
        val valsVec = builder.createVectorOfTables(vals)
        MapConstantValue.startMapConstantValue(builder)
        MapConstantValue.addKeyType(builder, keyType)
        MapConstantValue.addValueType(builder, valType)
        MapConstantValue.addKeys(builder, keysVec)
        MapConstantValue.addValues(builder, valsVec)
        ValueUnion.MapConstantValue to MapConstantValue.endMapConstantValue(builder)
    }

    is RR_ConstantValue.TupleConstant -> {
        val tupleType = serializeType(value.tupleType)
        val fields = value.fieldValues.map { serializeRRUntypedConstantValue(it) }.toIntArray()
        val fieldsVec = builder.createVectorOfTables(fields)
        ValueUnion.TupleConstantValue to TupleConstantValue.createTupleConstantValue(builder, tupleType, fieldsVec)
    }

    is RR_ConstantValue.Meta -> {
        val kind = serializeMetaDefinitionKind(value.kind)
        val moduleName = createString(value.moduleName)
        val fullName = createString(value.fullName)
        val simpleName = createString(value.simpleName)
        val mountName = createString(value.mountName)
        ValueUnion.MetaConstantValue to MetaConstantValue.createMetaConstantValue(
            builder,
            kind,
            moduleName,
            fullName,
            simpleName,
            mountName,
        )
    }
}

private fun SerializerContext.serializeRRConstantValue(rrType: RR_Type, value: RR_ConstantValue): Int {
    val typeOff = serializeType(rrType)
    val (unionType, unionOffset) = serializeValueUnion(value)
    TypedValue.startTypedValue(builder)
    TypedValue.addType(builder, typeOff)
    TypedValue.addValueType(builder, unionType)
    TypedValue.addValue(builder, unionOffset)
    return TypedValue.endTypedValue(builder)
}

private fun SerializerContext.serializeRRUntypedConstantValue(value: RR_ConstantValue): Int {
    val (unionType, unionOffset) = serializeValueUnion(value)
    ConstantValue.startConstantValue(builder)
    ConstantValue.addValueType(builder, unionType)
    ConstantValue.addValue(builder, unionOffset)
    return ConstantValue.endConstantValue(builder)
}

internal fun SerializerContext.serializeRRFunctionCall(call: RR_FunctionCall): Int {
    val (unionType, unionOffset) = when (call) {
        is RR_FunctionCall.Full -> {
            val retType = serializeType(call.returnType)
            val target = serializeRRFunctionCallTarget(call.target)
            val callPos = serializeFilePos(call.callPos)
            val args = serializeExprList(call.args)
            val mapping = call.mapping.toIntArray()
            val mappingVec = FullFunctionCall.createMappingVector(builder, mapping)
            FullFunctionCall.startFullFunctionCall(builder)
            FullFunctionCall.addReturnType(builder, retType)
            FullFunctionCall.addTarget(builder, target)
            FullFunctionCall.addCallPos(builder, callPos)
            FullFunctionCall.addArgs(builder, args)
            FullFunctionCall.addMapping(builder, mappingVec)
            FunctionCallUnion.FullFunctionCall to FullFunctionCall.endFullFunctionCall(builder)
        }

        is RR_FunctionCall.Partial -> {
            val retType = serializeType(call.returnType)
            val target = serializeRRFunctionCallTarget(call.target)
            val args = serializeExprList(call.args)
            val mappingVec = PartialFunctionCall.createMappingValuesVector(builder, call.mappingValues.toIntArray())
            PartialFunctionCall.startPartialFunctionCall(builder)
            PartialFunctionCall.addReturnType(builder, retType)
            PartialFunctionCall.addTarget(builder, target)
            PartialFunctionCall.addWildArgCount(builder, call.wildArgCount)
            PartialFunctionCall.addMappingValues(builder, mappingVec)
            PartialFunctionCall.addArgs(builder, args)
            FunctionCallUnion.PartialFunctionCall to PartialFunctionCall.endPartialFunctionCall(builder)
        }
    }
    FunctionCall.startFunctionCall(builder)
    FunctionCall.addCallType(builder, unionType)
    FunctionCall.addCall(builder, unionOffset)
    return FunctionCall.endFunctionCall(builder)
}

private fun SerializerContext.serializeRRFunctionCallTarget(target: RR_FunctionCallTarget): Int {
    val (unionType, unionOffset) = when (target) {
        is RR_FunctionCallTarget.RegularUser -> FunctionCallTargetUnion.FnTarget_RegularUser to FnTarget_RegularUser.createFnTarget_RegularUser(
            builder,
            target.fnDefIndex.toUInt(),
        )

        is RR_FunctionCallTarget.RegularQuery -> FunctionCallTargetUnion.FnTarget_RegularQuery to FnTarget_RegularQuery.createFnTarget_RegularQuery(
            builder,
            target.queryDefIndex.toUInt(),
        )

        is RR_FunctionCallTarget.AbstractUser -> FunctionCallTargetUnion.FnTarget_AbstractUser to FnTarget_AbstractUser.createFnTarget_AbstractUser(
            builder,
            target.fnDefIndex.toUInt(),
        )

        is RR_FunctionCallTarget.NativeUser -> {
            val n = createString(target.fullName.str())
            FunctionCallTargetUnion.FnTarget_NativeUser to FnTarget_NativeUser.createFnTarget_NativeUser(builder, n)
        }

        is RR_FunctionCallTarget.Operation -> {
            FunctionCallTargetUnion.FnTarget_Operation to FnTarget_Operation.createFnTarget_Operation(
                builder,
                target.opDefIndex.toUInt(),
            )
        }

        is RR_FunctionCallTarget.FunctionValue -> {
            FnTarget_FunctionValue.startFnTarget_FunctionValue(builder)
            FunctionCallTargetUnion.FnTarget_FunctionValue to FnTarget_FunctionValue.endFnTarget_FunctionValue(builder)
        }

        is RR_FunctionCallTarget.SysGlobal -> {
            val n = createString(target.fnName)
            FunctionCallTargetUnion.FnTarget_SysGlobal to FnTarget_SysGlobal.createFnTarget_SysGlobal(builder, n)
        }

        is RR_FunctionCallTarget.SysMember -> {
            val n = createString(target.fnName)
            FunctionCallTargetUnion.FnTarget_SysMember to FnTarget_SysMember.createFnTarget_SysMember(builder, n)
        }

        is RR_FunctionCallTarget.AbstractOverride -> {
            val body = serializeFunctionBody(target.fnBase)
            FunctionCallTargetUnion.FnTarget_AbstractOverride to FnTarget_AbstractOverride.createFnTarget_AbstractOverride(
                builder,
                body,
            )
        }

        is RR_FunctionCallTarget.Extendable -> {
            val retType = serializeType(target.returnType)
            val combiner = when (target.combinerKind) {
                RR_ExtendableCombinerKind.UNIT -> ExtendableCombinerKind.UNIT
                RR_ExtendableCombinerKind.BOOLEAN -> ExtendableCombinerKind.BOOLEAN
                RR_ExtendableCombinerKind.NULLABLE -> ExtendableCombinerKind.NULLABLE
                RR_ExtendableCombinerKind.LIST -> ExtendableCombinerKind.LIST
                RR_ExtendableCombinerKind.MAP -> ExtendableCombinerKind.MAP
            }
            FnTarget_Extendable.startFnTarget_Extendable(builder)
            FnTarget_Extendable.addExtendableUidId(builder, target.extendableUidId)
            FnTarget_Extendable.addCombinerKind(builder, combiner)
            FnTarget_Extendable.addReturnType(builder, retType)
            FunctionCallTargetUnion.FnTarget_Extendable to FnTarget_Extendable.endFnTarget_Extendable(builder)
        }
    }
    FunctionCallTarget.startFunctionCallTarget(builder)
    FunctionCallTarget.addTargetType(builder, unionType)
    FunctionCallTarget.addTarget(builder, unionOffset)
    return FunctionCallTarget.endFunctionCallTarget(builder)
}

internal fun SerializerContext.serializeRRMemberCalculator(calc: RR_MemberCalculator): Int {
    val (unionType, unionOffset) = when (calc) {
        is RR_MemberCalculator.StructAttr -> {
            val t = serializeType(calc.type)
            MemberCalculatorUnion.MemberCalculator_StructAttr to MemberCalculator_StructAttr.createMemberCalculator_StructAttr(
                builder,
                t,
                calc.attrIndex,
            )
        }

        is RR_MemberCalculator.TupleAttr -> {
            val t = serializeType(calc.type)
            MemberCalculatorUnion.MemberCalculator_TupleAttr to MemberCalculator_TupleAttr.createMemberCalculator_TupleAttr(
                builder,
                t,
                calc.attrIndex,
            )
        }

        is RR_MemberCalculator.VirtualTupleAttr -> {
            val t = serializeType(calc.type)
            MemberCalculatorUnion.MemberCalculator_VirtualTupleAttr to MemberCalculator_VirtualTupleAttr.createMemberCalculator_VirtualTupleAttr(
                builder,
                t,
                calc.fieldIndex,
            )
        }

        is RR_MemberCalculator.VirtualStructAttr -> {
            val t = serializeType(calc.type)
            val n = createString(calc.attrName)
            MemberCalculatorUnion.MemberCalculator_VirtualStructAttr to MemberCalculator_VirtualStructAttr.createMemberCalculator_VirtualStructAttr(
                builder,
                t,
                calc.attrDefIndex.toUInt(),
                n,
            )
        }

        is RR_MemberCalculator.DataAttribute -> {
            val t = serializeType(calc.type)
            val n = createString(calc.attrName)
            MemberCalculatorUnion.MemberCalculator_DataAttribute to MemberCalculator_DataAttribute.createMemberCalculator_DataAttribute(
                builder,
                t,
                calc.entityDefIndex,
                n,
            )
        }

        is RR_MemberCalculator.DataAttributeExpr -> {
            val t = serializeType(calc.type)
            val expr = serializeExpr(calc.expr)
            val lb = serializeFrameBlock(calc.lambdaBlock)
            MemberCalculator_DataAttributeExpr.startMemberCalculator_DataAttributeExpr(builder)
            MemberCalculator_DataAttributeExpr.addType(builder, t)
            MemberCalculator_DataAttributeExpr.addExpr(builder, expr)
            MemberCalculator_DataAttributeExpr.addLambdaBlock(builder, lb)
            MemberCalculator_DataAttributeExpr.addLambdaVarPtr(
                builder,
                VarPtr.createVarPtr(builder, calc.lambdaVarPtr.blockUid.toUInt(), calc.lambdaVarPtr.offset),
            )
            MemberCalculatorUnion.MemberCalculator_DataAttributeExpr to MemberCalculator_DataAttributeExpr.endMemberCalculator_DataAttributeExpr(
                builder,
            )
        }

        is RR_MemberCalculator.SysFunction -> {
            val t = serializeType(calc.type)
            val n = createString(calc.fnName)
            MemberCalculatorUnion.MemberCalculator_SysFunction to MemberCalculator_SysFunction.createMemberCalculator_SysFunction(
                builder,
                t,
                n,
            )
        }

        is RR_MemberCalculator.FunctionCall -> {
            val t = serializeType(calc.type)
            val call = serializeRRFunctionCall(calc.call)
            MemberCalculatorUnion.MemberCalculator_FunctionCall to MemberCalculator_FunctionCall.createMemberCalculator_FunctionCall(
                builder,
                t,
                call,
            )
        }

        is RR_MemberCalculator.ExprEval -> {
            val t = serializeType(calc.type)
            val expr = serializeExpr(calc.expr)
            MemberCalculatorUnion.MemberCalculator_ExprEval to MemberCalculator_ExprEval.createMemberCalculator_ExprEval(
                builder,
                t,
                expr,
            )
        }
    }
    MemberCalculator.startMemberCalculator(builder)
    MemberCalculator.addCalculatorType(builder, unionType)
    MemberCalculator.addCalculator(builder, unionOffset)
    return MemberCalculator.endMemberCalculator(builder)
}

internal fun SerializerContext.serializeRRWhenChooser(chooser: RR_WhenChooser): Int {
    val (unionType, unionOffset) = when (chooser) {
        is RR_WhenChooser.Iterative -> {
            val keyExpr = serializeExpr(chooser.keyExpr)
            val conds = chooser.conditions.map { cond ->
                val expr = serializeExpr(cond.expr)
                WhenCondition.createWhenCondition(builder, cond.index, expr)
            }.toIntArray()
            val condsVec = builder.createVectorOfTables(conds)
            IterativeWhenChooser.startIterativeWhenChooser(builder)
            IterativeWhenChooser.addKeyExpr(builder, keyExpr)
            IterativeWhenChooser.addConditions(builder, condsVec)
            if (chooser.elseIndex >= 0) {
                builder.forcedScalar { IterativeWhenChooser.addElseIndex(builder, chooser.elseIndex) }
            }
            WhenChooserUnion.IterativeWhenChooser to IterativeWhenChooser.endIterativeWhenChooser(builder)
        }

        is RR_WhenChooser.Lookup -> {
            val keyExpr = serializeExpr(chooser.keyExpr)
            val keys = chooser.keys.map { serializeRRUntypedConstantValue(it) }.toIntArray()
            val keysVec = builder.createVectorOfTables(keys)
            val valuesVec = LookupWhenChooser.createLookupValuesVector(builder, chooser.values.toIntArray())
            LookupWhenChooser.startLookupWhenChooser(builder)
            LookupWhenChooser.addKeyExpr(builder, keyExpr)
            LookupWhenChooser.addLookupKeys(builder, keysVec)
            LookupWhenChooser.addLookupValues(builder, valuesVec)
            if (chooser.elseIndex >= 0) {
                builder.forcedScalar { LookupWhenChooser.addElseIndex(builder, chooser.elseIndex) }
            }
            WhenChooserUnion.LookupWhenChooser to LookupWhenChooser.endLookupWhenChooser(builder)
        }
    }
    WhenChooser.startWhenChooser(builder)
    WhenChooser.addChooserType(builder, unionType)
    WhenChooser.addChooser(builder, unionOffset)
    return WhenChooser.endWhenChooser(builder)
}

internal fun SerializerContext.serializeRRTypeAdapter(adapter: RR_TypeAdapter): Int {
    val kind = when (adapter) {
        is RR_TypeAdapter.Direct -> TypeAdapterKind.DIRECT
        is RR_TypeAdapter.IntegerToBigInteger -> TypeAdapterKind.INTEGER_TO_BIG_INTEGER
        is RR_TypeAdapter.IntegerToDecimal -> TypeAdapterKind.INTEGER_TO_DECIMAL
        is RR_TypeAdapter.BigIntegerToDecimal -> TypeAdapterKind.BIG_INTEGER_TO_DECIMAL
        is RR_TypeAdapter.Nullable -> TypeAdapterKind.NULLABLE
    }
    val inner = if (adapter is RR_TypeAdapter.Nullable) serializeRRTypeAdapter(adapter.inner) else 0
    TypeAdapter.startTypeAdapter(builder)
    TypeAdapter.addKind(builder, kind)
    if (inner != 0) TypeAdapter.addInner(builder, inner)
    return TypeAdapter.endTypeAdapter(builder)
}

private fun SerializerContext.serializeExternalChainRef(chainIndex: Int): Int {
    val chain = app.externalChains.find { it.index == chainIndex }
    val name = createString(chain?.name ?: "")
    ExternalChainRef.startExternalChainRef(builder)
    ExternalChainRef.addName(builder, name)
    ExternalChainRef.addIndex(builder, chainIndex.toUInt())
    return ExternalChainRef.endExternalChainRef(builder)
}

private fun serializeMetaDefinitionKind(kind: String): UByte = when (kind) {
    "entity" -> MetaDefinitionKind.ENTITY
    "module" -> MetaDefinitionKind.MODULE
    "object" -> MetaDefinitionKind.OBJECT
    "operation" -> MetaDefinitionKind.OPERATION
    "query" -> MetaDefinitionKind.QUERY
    else -> error("Unknown meta definition kind: $kind")
}

