/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import net.postchain.rell.base.model.FullName
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.utils.mapToImmList
import net.postchain.rell.base.utils.toImmList
import rell.ir.*
import rell.ir.Expr as FbExpr

fun deserializeExpr(fb: FbExpr?): RR_Expr {
    if (fb == null) return RR_Expr.Error(RR_Type.Error, "null expression")
    return deserializeExprUnion(fb)
}

private fun deserializeExprUnion(fb: FbExpr): RR_Expr = when (fb.exprType) {
    ExprUnion.VarExpr -> {
        val e = VarExpr().also { fb.expr(it) }
        val ptr = e.ptr?.let { RR_VarPtr(it.blockUid.toLong(), it.offset) } ?: RR_VarPtr(0, 0)
        RR_Expr.Var(deserializeType(e.type), ptr, e.name)
    }

    ExprUnion.ConstantValueExpr -> {
        val e = ConstantValueExpr().also { fb.expr(it) }
        val tv = e.typedValue
        val type = deserializeType(tv.type)
        val value = deserializeConstantValue(tv)
        RR_Expr.ConstantValue(type, value)
    }

    ExprUnion.BinaryExpr -> {
        val e = BinaryExpr().also { fb.expr(it) }
        val cmpInfo =
            if (e.hasCmp) RR_CmpBinaryOp(deserializeCmpOp(e.cmpOp), deserializeCmpType(e.cmpType)) else null
        val op = if (cmpInfo != null) "Cmp_${cmpInfo.cmpOp}_${cmpInfo.cmpType}" else deserializeRRBinaryOp(e.op)
        RR_Expr.Binary(
            type = deserializeType(e.type),
            op = op,
            cmpInfo = cmpInfo,
            left = deserializeExpr(e.left),
            right = deserializeExpr(e.right),
            errPos = e.errPos?.let { deserializeErrorPos(it) },
        )
    }

    ExprUnion.UnaryExpr -> {
        val e = UnaryExpr().also { fb.expr(it) }
        RR_Expr.Unary(
            type = deserializeType(e.type),
            op = deserializeRRUnaryOp(e.op),
            expr = deserializeExpr(e.expr),
            errPos = deserializeErrorPos(e.errPos),
        )
    }

    ExprUnion.IfExpr -> {
        val e = IfExpr().also { fb.expr(it) }
        RR_Expr.If(
            type = deserializeType(e.type),
            cond = deserializeExpr(e.cond),
            trueExpr = deserializeExpr(e.trueExpr),
            falseExpr = deserializeExpr(e.falseExpr),
        )
    }

    ExprUnion.WhenExpr -> {
        val e = WhenExpr().also { fb.expr(it) }
        val chooser = deserializeWhenChooser(e.chooser)
        val exprs = (0 until e.exprsLength).mapToImmList { deserializeExpr(e.exprs(it)) }
        RR_Expr.When(deserializeType(e.type), chooser, exprs)
    }

    ExprUnion.ElvisExpr -> {
        val e = ElvisExpr().also { fb.expr(it) }
        RR_Expr.Elvis(deserializeType(e.type), deserializeExpr(e.left), deserializeExpr(e.right))
    }

    ExprUnion.NotNullExpr -> {
        val e = NotNullExpr().also { fb.expr(it) }
        RR_Expr.NotNull(deserializeType(e.type), deserializeExpr(e.expr), deserializeErrorPos(e.errPos))
    }

    ExprUnion.TupleExpr -> {
        val e = TupleExpr().also { fb.expr(it) }
        val exprs = (0 until e.exprsLength).mapToImmList { deserializeExpr(e.exprs(it)) }
        RR_Expr.TupleLiteral(deserializeType(e.type), exprs)
    }

    ExprUnion.ListLiteralExpr -> {
        val e = ListLiteralExpr().also { fb.expr(it) }
        val exprs = (0 until e.exprsLength).mapToImmList { deserializeExpr(e.exprs(it)) }
        RR_Expr.ListLiteral(deserializeType(e.type), exprs)
    }

    ExprUnion.MapLiteralExpr -> {
        val e = MapLiteralExpr().also { fb.expr(it) }
        val keys = (0 until e.keysLength).mapToImmList { deserializeExpr(e.keys(it)) }
        val values = (0 until e.valuesLength).mapToImmList { deserializeExpr(e.values(it)) }
        RR_Expr.MapLiteral(deserializeType(e.type), keys, values, deserializeErrorPos(e.errPos))
    }

    ExprUnion.StructExpr -> {
        val e = StructExpr().also { fb.expr(it) }
        val attrs = deserializeCreateAttrs(e).toImmList()
        RR_Expr.StructCreate(RR_Type.Struct(e.structDefIndex.toInt()), e.structDefIndex.toInt(), attrs)
    }

    ExprUnion.RegularCreateExpr -> {
        val e = RegularCreateExpr().also { fb.expr(it) }
        val attrs = deserializeCreateAttrsFromRegular(e).toImmList()
        RR_Expr.RegularCreate(
            type = RR_Type.Entity(e.entityDefIndex.toInt()),
            entityDefIndex = e.entityDefIndex.toInt(),
            errPos = deserializeErrorPos(e.errPos),
            attrs = attrs,
        )
    }

    ExprUnion.StructCreateExpr -> {
        val e = StructCreateExpr().also { fb.expr(it) }
        RR_Expr.StructEntityCreate(
            type = RR_Type.Entity(e.entityDefIndex.toInt()),
            entityDefIndex = e.entityDefIndex.toInt(),
            errPos = deserializeErrorPos(e.errPos),
            structDefIndex = e.structDefIndex.toInt(),
            structExpr = deserializeExpr(e.structExpr),
        )
    }

    ExprUnion.StructListCreateExpr -> {
        val e = StructListCreateExpr().also { fb.expr(it) }
        RR_Expr.StructListCreate(
            type = RR_Type.List(RR_Type.Entity(e.entityDefIndex.toInt())),
            entityDefIndex = e.entityDefIndex.toInt(),
            errPos = deserializeErrorPos(e.errPos),
            structDefIndex = e.structDefIndex.toInt(),
            resultListType = deserializeType(e.resultListType),
            listExpr = deserializeExpr(e.listExpr),
        )
    }

    ExprUnion.FunctionCallExpr -> {
        val e = FunctionCallExpr().also { fb.expr(it) }
        val base = e.base?.let { deserializeExpr(it) }
        val call = deserializeFunctionCall(e.call)
        RR_Expr.FunctionCall(deserializeType(e.type), base, call, e.safe)
    }

    ExprUnion.MemberExpr -> {
        val e = MemberExpr().also { fb.expr(it) }
        val calc = deserializeMemberCalculator(e.calculator)
        // Type comes from the member calculator.
        RR_Expr.MemberAccess(calc.type, deserializeExpr(e.base), calc, e.safe)
    }

    ExprUnion.AssignExpr -> {
        val e = AssignExpr().also { fb.expr(it) }
        RR_Expr.Assign(
            type = deserializeType(e.type),
            op = if (e.op != BinaryOp.EQ) deserializeRRBinaryOp(e.op) else null,
            dstExpr = deserializeExpr(e.dstExpr),
            srcExpr = deserializeExpr(e.srcExpr),
            post = e.post,
        )
    }

    ExprUnion.StatementExpr -> {
        val e = StatementExpr().also { fb.expr(it) }
        val t = e.type?.let { deserializeType(it) } ?: RR_Type.Primitive(RR_PrimitiveKind.UNIT)
        RR_Expr.StatementExpr(t, deserializeStmt(e.stmt))
    }

    ExprUnion.GlobalConstantExpr -> {
        val e = GlobalConstantExpr().also { fb.expr(it) }
        RR_Expr.GlobalConstant(deserializeType(e.type), e.constDefIndex.toInt())
    }

    ExprUnion.ChainHeightExpr -> {
        val e = ChainHeightExpr().also { fb.expr(it) }
        RR_Expr.ChainHeight(RR_Type.Primitive(RR_PrimitiveKind.INTEGER), e.chain.index.toInt())
    }

    ExprUnion.TypeAdapterExpr -> {
        val e = TypeAdapterExpr().also { fb.expr(it) }
        RR_Expr.TypeAdapter(deserializeType(e.type), deserializeExpr(e.expr), deserializeTypeAdapter(e.adapter))
    }

    ExprUnion.ParameterDefaultValueExpr -> {
        val e = ParameterDefaultValueExpr().also { fb.expr(it) }
        val defIdName = e.defId?.let { deserializeDefinitionName(it) }
        RR_Expr.ParameterDefaultValue(
            type = deserializeType(e.type),
            callFilePos = deserializeFilePos(e.callFilePos),
            initFrame = deserializeFrameDescriptor(e.initFrame),
            defId = defIdName?.let { net.postchain.rell.base.model.DefinitionId(it.module, it.qualifiedName) }
                ?: net.postchain.rell.base.model.DefinitionId("", ""),
            innerExpr = deserializeExpr(e.innerExpr),
        )
    }

    ExprUnion.AttributeDefaultValueExpr -> {
        val e = AttributeDefaultValueExpr().also { fb.expr(it) }
        val defIdName = e.defId?.let { deserializeDefinitionName(it) }
        RR_Expr.AttributeDefaultValue(
            type = RR_Type.Error, // Type not serialized; resolved at runtime.
            attrIndex = e.attrIndex,
            attrName = e.attrName,
            createFilePos = e.createFilePos?.let { deserializeFilePos(it) },
            initFrame = deserializeFrameDescriptor(e.initFrame),
            defId = defIdName?.let { net.postchain.rell.base.model.DefinitionId(it.module, it.qualifiedName) }
                ?: net.postchain.rell.base.model.DefinitionId("", ""),
            innerExpr = deserializeExpr(e.innerExpr),
        )
    }

    ExprUnion.ListSubscriptExpr -> {
        val e = ListSubscriptExpr().also { fb.expr(it) }
        RR_Expr.ListSubscript(
            deserializeType(e.type),
            deserializeExpr(e.base), deserializeExpr(e.index), deserializeErrorPos(e.errPos),
        )
    }

    ExprUnion.MapSubscriptExpr -> {
        val e = MapSubscriptExpr().also { fb.expr(it) }
        RR_Expr.MapSubscript(
            deserializeType(e.type),
            deserializeExpr(e.base),
            deserializeExpr(e.key),
            deserializeErrorPos(e.errPos),
        )
    }

    ExprUnion.TextSubscriptExpr -> {
        val e = TextSubscriptExpr().also { fb.expr(it) }
        RR_Expr.TextSubscript(
            RR_Type.Primitive(RR_PrimitiveKind.TEXT),
            deserializeExpr(e.base),
            deserializeExpr(e.index),
            deserializeErrorPos(e.errPos),
        )
    }

    ExprUnion.ByteArraySubscriptExpr -> {
        val e = ByteArraySubscriptExpr().also { fb.expr(it) }
        RR_Expr.ByteArraySubscript(
            RR_Type.Primitive(RR_PrimitiveKind.INTEGER),
            deserializeExpr(e.base),
            deserializeExpr(e.index),
            deserializeErrorPos(e.errPos),
        )
    }

    ExprUnion.VirtualListSubscriptExpr -> {
        val e = VirtualListSubscriptExpr().also { fb.expr(it) }
        RR_Expr.VirtualListSubscript(deserializeType(e.type), deserializeExpr(e.base), deserializeExpr(e.index))
    }

    ExprUnion.VirtualMapSubscriptExpr -> {
        val e = VirtualMapSubscriptExpr().also { fb.expr(it) }
        RR_Expr.VirtualMapSubscript(
            deserializeType(e.type),
            deserializeExpr(e.base),
            deserializeExpr(e.key),
            deserializeErrorPos(e.errPos),
        )
    }

    ExprUnion.JsonArraySubscriptExpr -> {
        val e = JsonArraySubscriptExpr().also { fb.expr(it) }
        RR_Expr.JsonArraySubscript(
            RR_Type.Primitive(RR_PrimitiveKind.JSON),
            deserializeExpr(e.base),
            deserializeExpr(e.index),
            deserializeErrorPos(e.errPos),
        )
    }

    ExprUnion.JsonObjectSubscriptExpr -> {
        val e = JsonObjectSubscriptExpr().also { fb.expr(it) }
        RR_Expr.JsonObjectSubscript(
            RR_Type.Primitive(RR_PrimitiveKind.JSON),
            deserializeExpr(e.base),
            deserializeExpr(e.key),
            deserializeErrorPos(e.errPos),
        )
    }

    ExprUnion.StructMemberExpr -> {
        val e = StructMemberExpr().also { fb.expr(it) }
        RR_Expr.StructMember(deserializeType(e.type), deserializeExpr(e.base), e.attrName, e.attrIndex)
    }

    ExprUnion.LazyExpr -> {
        val e = LazyExpr().also { fb.expr(it) }
        RR_Expr.Lazy(deserializeType(e.type), deserializeExpr(e.innerExpr))
    }

    ExprUnion.ObjectValueExpr -> {
        val e = ObjectValueExpr().also { fb.expr(it) }
        RR_Expr.ObjectValue(deserializeType(e.type), e.objectDefIndex.toInt())
    }

    ExprUnion.DbAtExpr -> {
        val e = DbAtExpr().also { fb.expr(it) }
        val from = deserializeDbAtFrom(e.from)
        val what = deserializeDbAtWhatFields(e.whatLength) { e.what(it) }
        val where = e.where?.let { deserializeDbExpr(it) }
        val extras = e.extras?.let { deserializeAtExtras(it) }
        val internals = deserializeDbAtInternals(e.internals)
        val whatFieldGroups = if (e.whatFieldGroupsLength > 0) {
            (0 until e.whatFieldGroupsLength).mapToImmList { deserializeWhatFieldGroup(e.whatFieldGroups(it)!!) }
        } else null
        val objectDefIndex = if (e.objectDefIndex >= 0) e.objectDefIndex else null
        RR_Expr.DbAt(
            type = deserializeType(e.type),
            from = from,
            what = what,
            where = where,
            cardinality = deserializeAtCardinality(e.cardinality),
            extras = extras,
            internals = internals,
            errPos = deserializeErrorPos(e.errPos),
            whatFieldGroups = whatFieldGroups,
            objectName = e.objectName,
            objectDefIndex = objectDefIndex,
        )
    }

    ExprUnion.ColAtExpr -> {
        val e = ColAtExpr().also { fb.expr(it) }
        RR_Expr.ColAt(
            type = deserializeType(e.type),
            block = deserializeFrameBlock(e.block),
            param = deserializeColAtParam(e.param),
            from = deserializeColAtFrom(e.from),
            what = deserializeColAtWhat(e.what),
            where = deserializeExpr(e.where),
            summarization = deserializeColAtSummarizationKind(e.summarization),
            errPos = deserializeErrorPos(e.errPos),
            cardinality = deserializeAtCardinality(e.cardinality),
            extras = e.extras?.let { deserializeAtExtras(it) },
            fieldSummarizations = (0 until e.fieldSummarizationsLength).mapToImmList { i ->
                val info = e.fieldSummarizations(i)!!
                val kind = deserializeColAtFieldSummarizationKind(info.kind)
                RR_ColAtFieldSummarizationInfo(
                    kind = kind,
                    binaryOpKey = info.binaryOpKey,
                    zeroValue = info.zeroValue?.let { deserializeUntypedConstantValue(it) },
                    isMin = if (kind == RR_ColAtFieldSummarizationKind.MIN || kind == RR_ColAtFieldSummarizationKind.MAX) info.isMin else null,
                    collectionType = info.collectionType?.let { deserializeType(it) },
                    mapValueType = info.mapValueType?.let { deserializeType(it) },
                )
            },
            sorting = (0 until e.sortingLength).mapToImmList { i ->
                val s = e.sorting(i)!!
                RR_ColAtSortEntry(s.fieldIndex, s.ascending)
            },
        )
    }

    ExprUnion.ErrorExpr -> {
        val e = ErrorExpr().also { fb.expr(it) }
        RR_Expr.Error(deserializeType(e.type), e.message)
    }

    else -> RR_Expr.Error(RR_Type.Error, "unknown expr union type: ${fb.exprType}")
}

// --- Constant values ---

private fun deserializeConstantValue(fb: TypedValue): RR_ConstantValue = when (fb.valueType) {
    ValueUnion.NullValue -> RR_ConstantValue.Null
    ValueUnion.UnitValue -> RR_ConstantValue.Unit
    ValueUnion.BoolValue -> {
        val v = BoolValue().also { fb.value(it) }
        RR_ConstantValue.Bool(v.value)
    }

    ValueUnion.IntValue -> {
        val v = IntValue().also { fb.value(it) }
        RR_ConstantValue.Int(v.value)
    }

    ValueUnion.TextValue -> {
        val v = TextValue().also { fb.value(it) }
        RR_ConstantValue.Text(v.value)
    }

    ValueUnion.ByteArrayValue -> {
        val v = ByteArrayValue().also { fb.value(it) }
        val bytes = ByteArray(v.valueLength) { v.value(it).toByte() }
        RR_ConstantValue.ByteArray(bytes)
    }

    ValueUnion.DecimalValue -> {
        val v = DecimalValue().also { fb.value(it) }
        RR_ConstantValue.Decimal(v.value)
    }

    ValueUnion.BigIntegerValue -> {
        val v = BigIntegerValue().also { fb.value(it) }
        RR_ConstantValue.BigInteger(v.value)
    }

    ValueUnion.RowidValue -> {
        val v = RowidValue().also { fb.value(it) }
        RR_ConstantValue.Rowid(v.value)
    }

    ValueUnion.EnumValue -> {
        val v = EnumValue().also { fb.value(it) }
        RR_ConstantValue.Enum(v.defIndex.toInt(), v.attrIndex.toInt())
    }

    ValueUnion.GtvValue -> {
        val v = GtvValue().also { fb.value(it) }
        RR_ConstantValue.Gtv(v.json)
    }

    ValueUnion.StructConstantValue -> {
        val v = StructConstantValue().also { fb.value(it) }
        val fields = (0 until v.fieldValuesLength).mapToImmList { deserializeUntypedConstantValue(v.fieldValues(it)) }
        RR_ConstantValue.Struct(v.structDefIndex, fields)
    }

    ValueUnion.CollectionConstantValue -> {
        val v = CollectionConstantValue().also { fb.value(it) }
        val elemType = deserializeType(v.elementType)
        val elems =
            (0 until v.elementValuesLength).mapToImmList { deserializeUntypedConstantValue(v.elementValues(it)) }
        RR_ConstantValue.Collection(elemType, elems)
    }

    ValueUnion.MapConstantValue -> {
        val v = MapConstantValue().also { fb.value(it) }
        val keys = (0 until v.keysLength).mapToImmList { deserializeUntypedConstantValue(v.keys(it)) }
        val vals = (0 until v.valuesLength).mapToImmList { deserializeUntypedConstantValue(v.values(it)) }
        RR_ConstantValue.MapConstant(deserializeType(v.keyType), deserializeType(v.valueType), keys, vals)
    }

    ValueUnion.TupleConstantValue -> {
        val v = TupleConstantValue().also { fb.value(it) }
        val tupleType = deserializeType(v.tupleType)
        val fields = (0 until v.fieldValuesLength).mapToImmList { deserializeUntypedConstantValue(v.fieldValues(it)) }
        RR_ConstantValue.TupleConstant(tupleType, fields)
    }

    ValueUnion.MetaConstantValue -> {
        val v = MetaConstantValue().also { fb.value(it) }
        RR_ConstantValue.Meta(v.kind, v.moduleName, v.fullName, v.simpleName, v.mountName)
    }

    else -> RR_ConstantValue.Null
}

private fun deserializeUntypedConstantValue(fb: ConstantValue?): RR_ConstantValue = when (fb?.valueType) {
    null -> RR_ConstantValue.Null
    ValueUnion.NullValue -> RR_ConstantValue.Null
    ValueUnion.UnitValue -> RR_ConstantValue.Unit
    ValueUnion.BoolValue -> {
        val v = BoolValue().also { fb.value(it) }; RR_ConstantValue.Bool(v.value)
    }

    ValueUnion.IntValue -> {
        val v = IntValue().also { fb.value(it) }; RR_ConstantValue.Int(v.value)
    }

    ValueUnion.TextValue -> {
        val v = TextValue().also { fb.value(it) }; RR_ConstantValue.Text(v.value)
    }

    ValueUnion.ByteArrayValue -> {
        val v = ByteArrayValue().also { fb.value(it) }
        val bytes = ByteArray(v.valueLength) { v.value(it).toByte() }
        RR_ConstantValue.ByteArray(bytes)
    }

    ValueUnion.DecimalValue -> {
        val v = DecimalValue().also { fb.value(it) }; RR_ConstantValue.Decimal(v.value)
    }

    ValueUnion.BigIntegerValue -> {
        val v = BigIntegerValue().also { fb.value(it) }; RR_ConstantValue.BigInteger(v.value)
    }

    ValueUnion.RowidValue -> {
        val v = RowidValue().also { fb.value(it) }; RR_ConstantValue.Rowid(v.value)
    }

    ValueUnion.EnumValue -> {
        val v = EnumValue().also { fb.value(it) }; RR_ConstantValue.Enum(v.defIndex.toInt(), v.attrIndex.toInt())
    }

    ValueUnion.GtvValue -> {
        val v = GtvValue().also { fb.value(it) }; RR_ConstantValue.Gtv(v.json)
    }

    ValueUnion.StructConstantValue -> {
        val v = StructConstantValue().also { fb.value(it) }
        val fields = (0 until v.fieldValuesLength).mapToImmList { deserializeUntypedConstantValue(v.fieldValues(it)) }
        RR_ConstantValue.Struct(v.structDefIndex, fields)
    }

    ValueUnion.CollectionConstantValue -> {
        val v = CollectionConstantValue().also { fb.value(it) }
        val elemType = deserializeType(v.elementType)
        val elems =
            (0 until v.elementValuesLength).mapToImmList { deserializeUntypedConstantValue(v.elementValues(it)) }
        RR_ConstantValue.Collection(elemType, elems)
    }

    ValueUnion.MapConstantValue -> {
        val v = MapConstantValue().also { fb.value(it) }
        val keys = (0 until v.keysLength).mapToImmList { deserializeUntypedConstantValue(v.keys(it)) }
        val vals = (0 until v.valuesLength).mapToImmList { deserializeUntypedConstantValue(v.values(it)) }
        RR_ConstantValue.MapConstant(deserializeType(v.keyType), deserializeType(v.valueType), keys, vals)
    }

    ValueUnion.TupleConstantValue -> {
        val v = TupleConstantValue().also { fb.value(it) }
        val tupleType = deserializeType(v.tupleType)
        val fields = (0 until v.fieldValuesLength).mapToImmList { deserializeUntypedConstantValue(v.fieldValues(it)) }
        RR_ConstantValue.TupleConstant(tupleType, fields)
    }

    ValueUnion.MetaConstantValue -> {
        val v = MetaConstantValue().also { fb.value(it) }
        RR_ConstantValue.Meta(v.kind, v.moduleName, v.fullName, v.simpleName, v.mountName)
    }

    else -> RR_ConstantValue.Null
}

// --- Function call ---

internal fun deserializeFunctionCall(fb: FunctionCall?): RR_FunctionCall = when (fb?.callType) {
    null -> error("Null function call")
    FunctionCallUnion.FullFunctionCall -> {
        val c = FullFunctionCall().also { fb.call(it) }
        val target = deserializeFunctionCallTarget(c.target)
        val args = (0 until c.argsLength).mapToImmList { deserializeExpr(c.args(it)) }
        val mapping = (0 until c.mappingLength).mapToImmList { c.mapping(it) }
        RR_FunctionCall.Full(
            returnType = deserializeType(c.returnType),
            target = target,
            args = args,
            callPos = deserializeFilePos(c.callPos),
            mapping = mapping,
        )
    }

    FunctionCallUnion.PartialFunctionCall -> {
        val c = PartialFunctionCall().also { fb.call(it) }
        val target = deserializeFunctionCallTarget(c.target)
        val args = (0 until c.argsLength).mapToImmList { deserializeExpr(c.args(it)) }
        val mappingValues = (0 until c.mappingValuesLength).mapToImmList { c.mappingValues(it) }
        RR_FunctionCall.Partial(
            returnType = deserializeType(c.returnType),
            target = target,
            args = args,
            wildArgCount = c.wildArgCount,
            mappingValues = mappingValues,
        )
    }

    else -> error("Unknown function call union type: ${fb.callType}")
}

private fun deserializeFunctionCallTarget(fb: FunctionCallTarget?): RR_FunctionCallTarget = when (fb?.targetType) {
    null -> error("Null function call target")
    FunctionCallTargetUnion.FnTarget_RegularUser -> {
        val t = FnTarget_RegularUser().also { fb.target(it) }
        RR_FunctionCallTarget.RegularUser(t.fnDefIndex.toInt())
    }

    FunctionCallTargetUnion.FnTarget_AbstractUser -> {
        val t = FnTarget_AbstractUser().also { fb.target(it) }
        RR_FunctionCallTarget.AbstractUser(t.fnDefIndex.toInt())
    }

    FunctionCallTargetUnion.FnTarget_NativeUser -> {
        val t = FnTarget_NativeUser().also { fb.target(it) }
        RR_FunctionCallTarget.NativeUser(FullName.of(t.fnName))
    }

    FunctionCallTargetUnion.FnTarget_Operation -> {
        val t = FnTarget_Operation().also { fb.target(it) }
        RR_FunctionCallTarget.Operation(t.opDefIndex.toInt())
    }

    FunctionCallTargetUnion.FnTarget_FunctionValue -> RR_FunctionCallTarget.FunctionValue
    FunctionCallTargetUnion.FnTarget_SysGlobal -> {
        val t = FnTarget_SysGlobal().also { fb.target(it) }
        RR_FunctionCallTarget.SysGlobal(t.fnName)
    }

    FunctionCallTargetUnion.FnTarget_SysMember -> {
        val t = FnTarget_SysMember().also { fb.target(it) }
        RR_FunctionCallTarget.SysMember(t.fnName)
    }

    FunctionCallTargetUnion.FnTarget_AbstractOverride -> {
        val t = FnTarget_AbstractOverride().also { fb.target(it) }
        val defName = net.postchain.rell.base.model.DefinitionName("", "abstract_override", "abstract_override")
        RR_FunctionCallTarget.AbstractOverride(deserializeFunctionBody(t.body, defName))
    }

    FunctionCallTargetUnion.FnTarget_Extendable -> {
        val t = FnTarget_Extendable().also { fb.target(it) }
        val combiner = when (t.combinerKind) {
            ExtendableCombinerKind.UNIT -> RR_ExtendableCombinerKind.UNIT
            ExtendableCombinerKind.BOOLEAN -> RR_ExtendableCombinerKind.BOOLEAN
            ExtendableCombinerKind.NULLABLE -> RR_ExtendableCombinerKind.NULLABLE
            ExtendableCombinerKind.LIST -> RR_ExtendableCombinerKind.LIST
            ExtendableCombinerKind.MAP -> RR_ExtendableCombinerKind.MAP
            else -> RR_ExtendableCombinerKind.UNIT
        }
        RR_FunctionCallTarget.Extendable(t.extendableUidId, combiner, deserializeType(t.returnType))
    }

    FunctionCallTargetUnion.FnTarget_RegularQuery -> {
        val t = FnTarget_RegularQuery().also { fb.target(it) }
        RR_FunctionCallTarget.RegularQuery(t.queryDefIndex.toInt())
    }

    else -> error("Unknown function call target union type: ${fb.targetType}")
}

// --- Member calculator ---

internal fun deserializeMemberCalculator(fb: MemberCalculator?): RR_MemberCalculator = when (fb?.calculatorType) {
    null -> error("Null member calculator")
    MemberCalculatorUnion.MemberCalculator_StructAttr -> {
        val c = MemberCalculator_StructAttr().also { fb.calculator(it) }
        RR_MemberCalculator.StructAttr(deserializeType(c.type), c.attrIndex)
    }

    MemberCalculatorUnion.MemberCalculator_TupleAttr -> {
        val c = MemberCalculator_TupleAttr().also { fb.calculator(it) }
        RR_MemberCalculator.TupleAttr(deserializeType(c.type), c.attrIndex)
    }

    MemberCalculatorUnion.MemberCalculator_VirtualTupleAttr -> {
        val c = MemberCalculator_VirtualTupleAttr().also { fb.calculator(it) }
        RR_MemberCalculator.VirtualTupleAttr(deserializeType(c.type), c.fieldIndex)
    }

    MemberCalculatorUnion.MemberCalculator_VirtualStructAttr -> {
        val c = MemberCalculator_VirtualStructAttr().also { fb.calculator(it) }
        RR_MemberCalculator.VirtualStructAttr(deserializeType(c.type), c.attrDefIndex.toInt(), c.attrName)
    }

    MemberCalculatorUnion.MemberCalculator_DataAttribute -> {
        val c = MemberCalculator_DataAttribute().also { fb.calculator(it) }
        RR_MemberCalculator.DataAttribute(deserializeType(c.type), c.entityDefIndex, c.attrName)
    }

    MemberCalculatorUnion.MemberCalculator_DataAttributeExpr -> {
        val c = MemberCalculator_DataAttributeExpr().also { fb.calculator(it) }
        val ptr = c.lambdaVarPtr.let { RR_VarPtr(it.blockUid.toLong(), it.offset) }
        RR_MemberCalculator.DataAttributeExpr(
            deserializeType(c.type),
            deserializeExpr(c.expr),
            deserializeFrameBlock(c.lambdaBlock),
            ptr,
        )
    }

    MemberCalculatorUnion.MemberCalculator_SysFunction -> {
        val c = MemberCalculator_SysFunction().also { fb.calculator(it) }
        RR_MemberCalculator.SysFunction(deserializeType(c.type), c.fnName)
    }

    MemberCalculatorUnion.MemberCalculator_FunctionCall -> {
        val c = MemberCalculator_FunctionCall().also { fb.calculator(it) }
        RR_MemberCalculator.FunctionCall(deserializeType(c.type), deserializeFunctionCall(c.call))
    }

    MemberCalculatorUnion.MemberCalculator_ExprEval -> {
        val c = MemberCalculator_ExprEval().also { fb.calculator(it) }
        RR_MemberCalculator.ExprEval(deserializeType(c.type), deserializeExpr(c.expr))
    }

    else -> error("Unknown member calculator union type: ${fb.calculatorType}")
}

// --- When chooser ---

internal fun deserializeWhenChooser(fb: WhenChooser?): RR_WhenChooser = when (fb?.chooserType) {
    null -> error("Null when chooser")
    WhenChooserUnion.IterativeWhenChooser -> {
        val c = IterativeWhenChooser().also { fb.chooser(it) }
        val keyExpr = deserializeExpr(c.keyExpr)
        val conditions = (0 until c.conditionsLength).mapToImmList { i ->
            val cond = c.conditions(i)
            RR_WhenCondition(cond.index, deserializeExpr(cond.expr))
        }
        RR_WhenChooser.Iterative(keyExpr, conditions, c.elseIndex)
    }

    WhenChooserUnion.LookupWhenChooser -> {
        val c = LookupWhenChooser().also { fb.chooser(it) }
        val keyExpr = deserializeExpr(c.keyExpr)
        val keys = (0 until c.lookupKeysLength).mapToImmList { deserializeUntypedConstantValue(c.lookupKeys(it)) }
        val values = (0 until c.lookupValuesLength).mapToImmList { c.lookupValues(it) }
        RR_WhenChooser.Lookup(keyExpr, keys, values, c.elseIndex)
    }

    else -> error("Unknown when chooser type: ${fb.chooserType}")
}

// --- Create attrs ---

private fun deserializeCreateAttrs(fb: StructExpr): List<RR_CreateAttr> =
    (0 until fb.attrsLength).map { i ->
        val a = fb.attrs(i)
        RR_CreateAttr(a.attrIndex, a.attrName, deserializeExpr(a.expr))
    }

private fun deserializeCreateAttrsFromRegular(fb: RegularCreateExpr): List<RR_CreateAttr> =
    (0 until fb.attrsLength).map { i ->
        val a = fb.attrs(i)
        RR_CreateAttr(a.attrIndex, a.attrName, deserializeExpr(a.expr))
    }
