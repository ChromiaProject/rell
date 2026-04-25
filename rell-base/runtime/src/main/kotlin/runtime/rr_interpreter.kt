/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString
import net.postchain.gtv.GtvType
import net.postchain.rell.base.lib.Rt_RellMetaValue
import net.postchain.rell.base.lib.test.Rt_TestOpValue
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.checkNull
import net.postchain.rell.base.utils.mapIndexedToImmList
import net.postchain.rell.base.utils.toImmList

/**
 * Interprets [RR_Expr] and [RR_Statement] trees via exhaustive `when` matching.
 *
 * Reuses the existing runtime infrastructure: [Rt_CallFrame], [Rt_Value], [Rt_StatementResult].
 */
class Rt_Interpreter(
    val rrApp: RR_App,
    val stdlib: Rt_StdlibEnv = Rt_StdlibEnv.global(),
) {
    companion object {
        /**
         * Factory that pairs an [RR_App] with its compilation-local sys-function registry. The
         * map comes from `C_CompilationResult.compilationSysFns` / `T_App.compilationSysFns` —
         * values are downcast to [R_SysFunction]. Using this factory (rather than the raw
         * constructor) is strongly preferred so meta-body closure captures (e.g. `log()` call
         * positions, `gtv_ext(T)` `Rt_Type` captures) don't leak across unrelated compilations.
         */
        fun forCompilation(rrApp: RR_App, compilationSysFns: Map<String, Any>): Rt_Interpreter {
            @Suppress("UNCHECKED_CAST")
            val sysFnMap = compilationSysFns as Map<String, R_SysFunction>
            return Rt_Interpreter(rrApp, Rt_StdlibEnv.forCompilation(sysFnMap))
        }
    }

    val metaGtv: Gtv by lazy { buildMetaGtv() }

    // --- Definition entry points ---

    fun callFunction(
        fn: RR_FunctionDefinition,
        exeCtx: Rt_ExecutionContext,
        args: List<Rt_Value>,
        dbUpdateAllowed: Boolean = false
    ): Rt_Value {
        val base = fn.fnBase
        // Validate size constraints on parameters
        validateParams(base.params, args)
        val frame = createFrame(exeCtx, base.frame, dbUpdateAllowed, fn.base.defId)
        setParams(frame, base.paramVars, args, base.defName.appLevelName)
        val result = executeStmt(base.body, frame)
        return if (result is Rt_StatementResult_Return) result.value ?: Rt_UnitValue else Rt_UnitValue
    }

    fun callOperation(op: RR_OperationDefinition, exeCtx: Rt_ExecutionContext, args: List<Rt_Value>) {
        // Validate size constraints on parameters
        validateParams(op.params, args)
        val frame = createFrame(exeCtx, op.frame, dbUpdateAllowed = true, op.base.defId)
        setParams(frame, op.paramVars, args, op.base.appLevelName)
        executeStmt(op.body, frame)
    }

    fun executeOperationGuard(op: RR_OperationDefinition, exeCtx: Rt_ExecutionContext, args: List<Rt_Value>) {
        val guardBody = op.guardBody ?: return
        val frame = createFrame(exeCtx, op.frame, dbUpdateAllowed = true, op.base.defId)
        setParams(frame, op.paramVars, args, op.base.appLevelName)
        executeStmt(guardBody, frame)
    }

    fun callQuery(query: RR_QueryDefinition, exeCtx: Rt_ExecutionContext, args: List<Rt_Value>): Rt_Value {
        // Validate size constraints on parameters
        validateParams(query.body.params, args)
        return when (val body = query.body) {
            is RR_UserQueryBody -> {
                val frame = createFrame(exeCtx, body.frame, dbUpdateAllowed = false, query.base.defId)
                setParams(frame, body.paramVars, args, query.base.appLevelName)
                val result = executeStmt(body.body, frame)
                if (result is Rt_StatementResult_Return) result.value ?: Rt_UnitValue else Rt_UnitValue
            }

            is RR_SysQueryBody -> {
                val fn = checkNotNull(stdlib.sysFunctions[body.fnName]) {
                    "Sys query function not found: ${body.fnName}"
                }
                val callCtx = Rt_CallContext(Rt_DefinitionContext(exeCtx, false, query.base.defId))
                fn.call(callCtx, args)
            }
        }
    }

    fun evaluateConstant(const: RR_GlobalConstantDefinition, exeCtx: Rt_ExecutionContext): Rt_Value {
        val frame = createFrame(exeCtx, const.base.initFrame, dbUpdateAllowed = false, const.base.defId)
        return evaluateExpr(const.expr, frame)
    }

    fun evaluateParamDefault(param: RR_FunctionParam, defCtx: Rt_DefinitionContext): Rt_Value {
        val expr = checkNotNull(param.defaultExpr) { "No default expression for parameter ${param.name}" }
        val frame = Rt_CallFrame(defCtx, param.initFrame, null)
        return evaluateExpr(expr, frame)
    }

    /**
     * Evaluates an attribute default value expression given a definition ID and attribute index.
     * Used by GTV conversion when a struct/entity field has a default value but no GTV value was provided.
     */
    fun evaluateAttributeDefault(defId: DefinitionId, attrIndex: Int, exeCtx: Rt_ExecutionContext): Rt_Value {
        // Search entities first.
        val entityIdx = rrApp.entityDefIdIndex[defId]
        if (entityIdx != null) {
            val entity = rrApp.allEntities[entityIdx]
            val attr = checkNotNull(entity.attributes.values.find { it.index == attrIndex }) {
                "Attribute index $attrIndex not found in entity ${entity.rName}"
            }
            val defaultExpr = checkNotNull(attr.defaultExpr) {
                "No default expression for attribute ${attr.name} in entity ${entity.rName}"
            }
            val frame = createFrame(exeCtx, entity.base.initFrame, dbUpdateAllowed = false, entity.base.defId)
            return evaluateExpr(defaultExpr, frame)
        }

        // Search structs.
        for (structDef in rrApp.allStructs) {
            if (structDef.base.defId == defId) {
                val attr = checkNotNull(structDef.struct.attributesList.find { it.index == attrIndex }) {
                    "Attribute index $attrIndex not found in struct ${structDef.struct.name}"
                }
                val defaultExpr = checkNotNull(attr.defaultExpr) {
                    "No default expression for attribute ${attr.name} in struct ${structDef.struct.name}"
                }
                val frame = createFrame(exeCtx, structDef.base.initFrame, dbUpdateAllowed = false, structDef.base.defId)
                return evaluateExpr(defaultExpr, frame)
            }
        }

        error("Definition not found for defId: $defId")
    }

    // --- Expression interpreter ---

    fun evaluateExpr(expr: RR_Expr, frame: Rt_CallFrame): Rt_Value = when (expr) {
        is RR_Expr.Var -> frame.get(expr.ptr)
        is RR_Expr.ConstantValue -> toRtValue(expr.value, expr.type)
        is RR_Expr.Binary -> evaluateBinary(expr, frame)
        is RR_Expr.Unary -> evaluateUnary(expr, frame)
        is RR_Expr.If -> {
            val cond = evaluateExpr(expr.cond, frame).asBoolean()
            evaluateExpr(if (cond) expr.trueExpr else expr.falseExpr, frame)
        }

        is RR_Expr.When -> evaluateWhen(expr, frame)
        is RR_Expr.Elvis -> {
            val left = evaluateExpr(expr.left, frame)
            if (left != Rt_NullValue) left else evaluateExpr(expr.right, frame)
        }

        is RR_Expr.NotNull -> {
            val value = evaluateExpr(expr.expr, frame)
            if (value == Rt_NullValue) {
                frame.error(expr.errPos, "null_value", "Null value")
            }
            value
        }

        is RR_Expr.TupleLiteral -> {
            val values = expr.exprs.map { evaluateExpr(it, frame) }
            Rt_TupleValue(resolveType(expr.type), values)
        }

        is RR_Expr.ListLiteral -> {
            val values = expr.exprs.map { evaluateExpr(it, frame) }
            Rt_ListValue(resolveType(expr.type), values.toMutableList())
        }

        is RR_Expr.MapLiteral -> {
            val map = mutableMapOf<Rt_Value, Rt_Value>()
            for (i in expr.keys.indices) {
                val k = evaluateExpr(expr.keys[i], frame)
                val v = evaluateExpr(expr.values[i], frame)
                if (k in map) {
                    frame.error(expr.errPos, "expr_map_dupkey:${k.strCode()}", "Duplicate map key: ${k.str()}")
                }
                map[k] = v
            }
            Rt_MapValue(resolveType(expr.type), map.toMutableMap())
        }

        is RR_Expr.StructCreate -> {
            val structDef = rrApp.allStructs[expr.structDefIndex]
            val attrValues = Array<Rt_Value?>(structDef.struct.attributesList.size) { null }
            for (attr in expr.attrs) {
                attrValues[attr.attrIndex] = evaluateExpr(attr.expr, frame)
            }
            val values = attrValues.map { it ?: Rt_NullValue }.toMutableList()

            // Apply size-constraint validation from RR attributes.
            for ((i, rrAttr) in structDef.struct.attributesList.withIndex()) {
                rrAttr.sizeConstraint?.let { checkSizeConstraint(it, values[i]) }
            }
            Rt_StructValue(resolveType(expr.type), structDef.struct.attributesList.map { it.name }, values)
        }

        is RR_Expr.FunctionCall -> evaluateFunctionCall(expr, frame)
        is RR_Expr.MemberAccess -> evaluateMemberAccess(expr, frame)
        is RR_Expr.Assign -> {
            val srcValue = evaluateExpr(expr.srcExpr, frame)
            val oldValue = if (expr.op != null || expr.post) evaluateExpr(expr.dstExpr, frame) else null
            // Safe member access: if the old value is null (from safe navigation), skip the assignment.
            // Null can come from MemberAccess(safe=true) or StructMember with null base.
            val dstExpr = expr.dstExpr
            if (oldValue == Rt_NullValue && (
                        (dstExpr is RR_Expr.MemberAccess && dstExpr.safe) ||
                                expr.type is RR_Type.Nullable)
            ) {
                Rt_NullValue
            } else {
                val opKey = expr.op
                val newValue = if (opKey != null) evaluateBinaryOp(opKey, oldValue!!, srcValue) else srcValue
                assignTo(expr.dstExpr, frame, newValue, null)
                if (expr.post) oldValue!! else newValue
            }
        }

        is RR_Expr.StatementExpr -> {
            executeStmt(expr.stmt, frame)
            Rt_UnitValue
        }

        is RR_Expr.GlobalConstant -> {
            frame.appCtx.getGlobalConstant(rrApp.allConstants[expr.constDefIndex].constId)
        }

        is RR_Expr.ChainHeight -> {
            val rtChain = frame.defCtx.sqlCtx.linkedChainByIndex(expr.chainIndex)
            Rt_IntValue.get(rtChain.height)
        }

        is RR_Expr.TypeAdapter -> evaluateTypeAdapter(expr, frame)
        is RR_Expr.ParameterDefaultValue -> {
            val initFrame =
                createFrame(frame.exeCtx, expr.initFrame, dbUpdateAllowed = frame.defCtx.dbUpdateAllowed, expr.defId)
            try {
                evaluateExpr(expr.innerExpr, initFrame)
            } catch (e: Rt_Exception) {
                frame.error(ErrorPos(expr.callFilePos), e, true)
            }
        }

        is RR_Expr.AttributeDefaultValue -> evaluateAttributeDefaultValue(expr, frame)
        is RR_Expr.RegularCreate -> evaluateRegularCreate(expr, frame)
        is RR_Expr.StructEntityCreate -> evaluateStructEntityCreate(expr, frame)
        is RR_Expr.StructListCreate -> evaluateStructListCreate(expr, frame)
        is RR_Expr.DbAt -> evaluateDbAt(expr, frame)
        is RR_Expr.ColAt -> evaluateColAt(expr, frame)
        is RR_Expr.Lazy -> Rt_RR_LazyValue(resolveType(expr.type), expr.innerExpr, frame, this)
        is RR_Expr.Error -> error("RR_Expr.Error reached at runtime: ${expr.message}")

        // Subscript expressions
        is RR_Expr.ListSubscript -> {
            val list = evaluateExpr(expr.base, frame).asList()
            val idx = evaluateExpr(expr.index, frame).asInteger()
            Rt_ListValue.checkIndex(frame, expr.errPos, list.size, idx)
            list[idx.toInt()]
        }

        is RR_Expr.MapSubscript -> {
            val map = evaluateExpr(expr.base, frame).asMap()
            val key = evaluateExpr(expr.key, frame)
            map[key] ?: frame.error(expr.errPos, "fn_map_get_novalue:${key.strCode()}", "Key not in map: ${key.str()}")
        }

        is RR_Expr.TextSubscript -> {
            val text = evaluateExpr(expr.base, frame).asString()
            val idx = evaluateExpr(expr.index, frame).asInteger()
            if (idx < 0 || idx >= text.length) frame.error(
                expr.errPos,
                "expr_text_subscript_index:${text.length}:$idx",
                "Index out of bounds: $idx (length ${text.length})",
            )
            Rt_TextValue.get(text[idx.toInt()].toString())
        }

        is RR_Expr.ByteArraySubscript -> {
            val ba = evaluateExpr(expr.base, frame).asByteArray()
            val idx = evaluateExpr(expr.index, frame).asInteger()
            if (idx < 0 || idx >= ba.size) frame.error(
                expr.errPos,
                "expr_bytearray_subscript_index:${ba.size}:$idx",
                "Byte array index out of range: $idx (size ${ba.size})",
            )
            Rt_IntValue.get(ba[idx.toInt()].toLong() and 0xFF)
        }

        is RR_Expr.VirtualListSubscript -> {
            val list = evaluateExpr(expr.base, frame).asVirtualList()
            val index = evaluateExpr(expr.index, frame).asInteger()
            list.get(index)
        }

        is RR_Expr.VirtualMapSubscript -> {
            val map = evaluateExpr(expr.base, frame).asMap()
            val key = evaluateExpr(expr.key, frame)
            map[key] ?: frame.error(expr.errPos, "fn_map_get_novalue:${key.strCode()}", "Key not in map: ${key.str()}")
        }

        is RR_Expr.JsonArraySubscript -> {
            val jsonNode = evaluateExpr(expr.base, frame).asJson().node
            val index = evaluateExpr(expr.index, frame).asInteger().toInt()
            when (val result = JsonUtils.arrayGet(jsonNode, index, "subscript")) {
                is JsonUtils.Success -> Rt_JsonValue(result.value)
                is JsonUtils.Failure -> frame.error(expr.errPos, result.codeMsg.code, result.codeMsg.msg)
            }
        }

        is RR_Expr.JsonObjectSubscript -> {
            val jsonNode = evaluateExpr(expr.base, frame).asJson().node
            val key = evaluateExpr(expr.key, frame).asString()
            when (val result = JsonUtils.objectGet(jsonNode, key, "subscript")) {
                is JsonUtils.Success -> Rt_JsonValue(result.value)
                is JsonUtils.Failure -> frame.error(expr.errPos, result.codeMsg.code, result.codeMsg.msg)
            }
        }

        is RR_Expr.StructMember -> {
            val base = evaluateExpr(expr.base, frame)
            if (base == Rt_NullValue) Rt_NullValue
            else base.asStruct().get(expr.attrIndex)
        }

        is RR_Expr.ObjectValue -> {
            Rt_ObjectValue(resolveType(expr.type))
        }
    }

    // --- Statement interpreter ---

    fun executeStmt(stmt: RR_Statement, frame: Rt_CallFrame): Rt_StatementResult? = when (stmt) {
        is RR_Statement.Empty -> null
        is RR_Statement.Var -> {
            val value = stmt.expr?.let { evaluateExpr(it, frame) }
            if (value != null) initializeDeclarator(stmt.declarator, frame, value)
            null
        }

        is RR_Statement.Return -> {
            val value = stmt.expr?.let { evaluateExpr(it, frame) }
            Rt_StatementResult_Return(value)
        }

        is RR_Statement.Block -> frame.block(stmt.frameBlock) {
            executeStatements(stmt.stmts, frame)
        }

        is RR_Statement.Expr -> {
            evaluateExpr(stmt.expr, frame)
            null
        }

        is RR_Statement.ReplExpr -> {
            val value = evaluateExpr(stmt.expr, frame)
            frame.appCtx.replOut?.printValue(value)
            null
        }

        is RR_Statement.Assign -> {
            val value = evaluateExpr(stmt.expr, frame)
            assignTo(stmt.dstExpr, frame, value, stmt.op)
            null
        }

        is RR_Statement.If -> {
            val cond = evaluateExpr(stmt.cond, frame).asBoolean()
            executeStmt(if (cond) stmt.trueStmt else stmt.falseStmt, frame)
        }

        is RR_Statement.When -> executeWhenStmt(stmt, frame)
        is RR_Statement.While -> {
            while (true) {
                val cond = evaluateExpr(stmt.cond, frame).asBoolean()
                if (!cond) break
                val res = frame.block(stmt.frameBlock) { executeStmt(stmt.body, frame) }
                if (res is Rt_StatementResult_Break) break
                if (res is Rt_StatementResult_Continue) continue
                if (res != null) return res
            }
            null
        }

        is RR_Statement.For -> {
            val iterable = evaluateExpr(stmt.expr, frame)
            val iterator = when (stmt.iterableAdapter) {
                RR_IterableAdapterKind.DIRECT -> iterable.asIterable()
                RR_IterableAdapterKind.LEGACY_MAP -> {
                    val rrType = when (val d = stmt.varDeclarator) {
                        is RR_VarDeclarator.Simple -> d.type
                        else -> RR_Type.Primitive(RR_PrimitiveKind.UNIT)
                    }
                    iterable.asMap().entries.map { (k, v) ->
                        Rt_TupleValue(resolveType(rrType), listOf(k, v))
                    }
                }
            }
            for (element in iterator) {
                val res = frame.block(stmt.frameBlock) {
                    initializeDeclarator(stmt.varDeclarator, frame, element)
                    executeStmt(stmt.body, frame)
                }
                if (res is Rt_StatementResult_Break) break
                if (res is Rt_StatementResult_Continue) continue
                if (res != null) return res
            }
            null
        }

        is RR_Statement.Break -> Rt_StatementResult_Break
        is RR_Statement.Continue -> Rt_StatementResult_Continue
        is RR_Statement.Guard -> {
            executeStmt(stmt.body, frame)
            frame.guardCompleted()
            null
        }

        is RR_Statement.Lambda -> {
            // Evaluate arg expressions outside the block (matches R_LambdaStatement.execute).
            val values = stmt.argExprs.map { evaluateExpr(it, frame) }
            frame.block(stmt.block) {
                for (i in values.indices) {
                    frame.setUnchecked(stmt.argPtrs[i], values[i], false)
                }
                executeStmt(stmt.body, frame)
            }
        }

        is RR_Statement.Update -> {
            executeUpdate(stmt, frame); null
        }

        is RR_Statement.Delete -> {
            executeDelete(stmt, frame); null
        }
    }

    // --- Helpers ---

    fun toRtValue(cv: RR_ConstantValue, hintType: RR_Type? = null): Rt_Value = when (cv) {
        is RR_ConstantValue.Null -> Rt_NullValue
        is RR_ConstantValue.Unit -> Rt_UnitValue
        is RR_ConstantValue.Bool -> Rt_BooleanValue.get(cv.value)
        is RR_ConstantValue.Int -> Rt_IntValue.get(cv.value)
        is RR_ConstantValue.Text -> Rt_TextValue.get(cv.value)
        is RR_ConstantValue.ByteArray -> Rt_ByteArrayValue.get(cv.value)
        is RR_ConstantValue.Decimal -> Rt_DecimalValue.get(cv.value.toBigDecimal())
        is RR_ConstantValue.BigInteger -> Rt_BigIntegerValue.get(cv.value.toBigInteger())
        is RR_ConstantValue.Rowid -> Rt_RowidValue.get(cv.value)
        is RR_ConstantValue.Enum -> {
            val rtType = resolveType(RR_Type.Enum(cv.enumDefIndex))
            val enumDef = rrApp.allEnums[cv.enumDefIndex]
            val attr = enumDef.attrs[cv.enumValue]
            Rt_RR_EnumValue(lazy { rtType }, attr)
        }

        is RR_ConstantValue.Gtv -> {
            val gtv = PostchainGtvUtils.jsonToGtv(cv.json)
            // If the target type is something other than gtv, decode through the Rt_Type GTV converter.
            val rtType = if (hintType != null) resolveType(hintType) else null
            val conv = rtType?.gtvConversion
            if (conv != null && rtType.rrType !is RR_Type.Primitive) {
                try {
                    val gtvCtx = GtvToRtContext.make(pretty = false)
                    conv.gtvToRt(gtvCtx, gtv)
                } catch (_: Throwable) {
                    Rt_GtvValue.get(gtv)
                }
            } else {
                Rt_GtvValue.get(gtv)
            }
        }

        is RR_ConstantValue.Struct -> {
            val values = cv.fieldValues.map { toRtValue(it) }.toMutableList()
            val rrStructType = RR_Type.Struct(cv.structDefIndex)
            val attrNames = rrApp.allStructs[cv.structDefIndex].struct.attributesList.map { it.name }
            Rt_StructValue(resolveType(rrStructType), attrNames, values)
        }

        is RR_ConstantValue.Collection -> {
            val elements = cv.elementValues.map { toRtValue(it) }.toMutableList()
            Rt_ListValue(resolveType(RR_Type.List(cv.elementType)), elements)
        }

        is RR_ConstantValue.MapConstant -> {
            val map = mutableMapOf<Rt_Value, Rt_Value>()
            for (i in cv.keys.indices) {
                map[toRtValue(cv.keys[i])] = toRtValue(cv.values[i])
            }
            Rt_MapValue(resolveType(RR_Type.Map(cv.keyType, cv.valueType)), map.toMutableMap())
        }

        is RR_ConstantValue.Meta -> {
            val mountName = MountName.ofOpt(cv.mountName) ?: MountName.EMPTY
            val meta = R_DefinitionMeta(
                kind = cv.kind,
                moduleName = cv.moduleName,
                fullName = cv.fullName,
                simpleName = cv.simpleName,
                mountName = mountName,
            )
            Rt_RellMetaValue(meta)
        }

        is RR_ConstantValue.TupleConstant -> {
            val values = cv.fieldValues.map { toRtValue(it) }
            Rt_TupleValue(resolveType(cv.tupleType), values)
        }
    }

    /**
     * Build [Rt_Type] from [RR_Type] and pre-populate its GTV/SQL/comparator/default
     * capability slots so the runtime never needs per-call-site `R_Type` dispatch.
     */
    fun resolveType(type: RR_Type): Rt_Type = typeCache.getOrPut(type) { buildRtType(type) }

    private val typeCache = mutableMapOf<RR_Type, Rt_Type>()

    private fun buildRtType(type: RR_Type): Rt_Type = when (type) {
        is RR_Type.Entity -> buildEntityRtType(type)
        is RR_Type.Enum -> buildEnumRtType(type)
        is RR_Type.Object -> buildObjectRtType(type)
        is RR_Type.Struct -> buildStructRtType(type)
        else -> Rt_Type(
            rrType = type,
            name = rrTypeName(type),
            sqlAdapter = buildCompositeSqlAdapter(type),
            gtvConversion = buildCompositeGtvConversion(type),
            comparator = buildCompositeComparator(type),
            nativeConversion = buildCompositeNativeConversion(type),
        )
    }

    private fun buildCompositeSqlAdapter(type: RR_Type): Rt_TypeSqlAdapter? = when (type) {
        is RR_Type.Primitive -> primitiveSqlAdapter(type.kind)
        is RR_Type.Nullable -> buildNullableSqlAdapter(type)
        else -> null
    }

    private fun buildNullableSqlAdapter(type: RR_Type.Nullable): Rt_TypeSqlAdapter? {
        val inner = resolveType(type.value)
        val innerAdapter = inner.sqlAdapter ?: return null
        return object: Rt_TypeSqlAdapter {
            override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) {
                if (value == Rt_NullValue) {
                    params.setObject(idx, null)
                } else {
                    innerAdapter.toSql(params, idx, value)
                }
            }

            override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
                return innerAdapter.fromSql(row, idx, true)
            }

            override fun metaName(sqlCtx: Rt_SqlContext): String = innerAdapter.metaName(sqlCtx)
        }
    }

    private fun buildCompositeComparator(type: RR_Type): Comparator<Rt_Value>? = when (type) {
        is RR_Type.Primitive -> primitiveComparator(type.kind)
        is RR_Type.Null -> Comparator { _, _ -> 0 }
        is RR_Type.Nullable -> {
            val inner = resolveType(type.value).comparator
            inner?.let { c ->
                Comparator { a, b ->
                    when {
                        a == Rt_NullValue && b == Rt_NullValue -> 0
                        a == Rt_NullValue -> -1
                        b == Rt_NullValue -> 1
                        else -> c.compare(a, b)
                    }
                }
            }
        }

        is RR_Type.Entity -> Comparator { a, b -> a.asObjectId().compareTo(b.asObjectId()) }
        is RR_Type.Enum -> Comparator { a, b -> a.asEnum().value.compareTo(b.asEnum().value) }
        is RR_Type.Tuple -> {
            val fieldComparators = type.fields.map { resolveType(it.type).comparator }
            if (fieldComparators.any { it == null }) null
            else Comparator { a, b ->
                val ta = a.asTuple()
                val tb = b.asTuple()
                var result = 0
                for (i in fieldComparators.indices) {
                    result = fieldComparators[i]!!.compare(ta[i], tb[i])
                    if (result != 0) break
                }
                result
            }
        }

        is RR_Type.List -> {
            val elemComparator = resolveType(type.element).comparator
            elemComparator?.let { ec ->
                Comparator { a, b ->
                    val la = a.asList()
                    val lb = b.asList()
                    val len = minOf(la.size, lb.size)
                    var result = 0
                    for (i in 0 until len) {
                        result = ec.compare(la[i], lb[i])
                        if (result != 0) break
                    }
                    if (result == 0) la.size.compareTo(lb.size) else result
                }
            }
        }

        else -> null
    }

    private fun buildCompositeNativeConversion(type: RR_Type): Rt_TypeNativeConversion? = when (type) {
        is RR_Type.Primitive -> primitiveNativeConversion(type.kind)
        is RR_Type.Nullable -> {
            val inner = resolveType(type.value).nativeConversion
            inner?.let { Rt_TypeNativeConversion_Nullable(it) }
        }

        else -> null
    }

    private fun buildEntityRtType(rrType: RR_Type.Entity): Rt_Type {
        val entityDef = rrApp.allEntities[rrType.defIndex]
        val name = entityDef.base.appLevelName
        lateinit var rtType: Rt_Type
        val sqlAdapter: Rt_TypeSqlAdapter = object: Rt_TypeSqlAdapter {
            override val sqlType: org.jooq.DataType<*>? = org.jooq.impl.SQLDataType.BIGINT

            override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) {
                params.setLong(idx, value.asObjectId())
            }

            override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
                val v = row.getLong(idx)
                return if (v == 0L && row.wasNull()) {
                    if (nullable) Rt_NullValue
                    else throw Rt_Exception.common("sql_null:$name", "SQL value is NULL for type $name")
                } else {
                    Rt_EntityValue(rtType, v)
                }
            }

            override fun metaName(sqlCtx: Rt_SqlContext): String {
                val mapping = entityDef.sqlMapping
                val chainMapping = if (mapping.externalChainIndex >= 0) {
                    sqlCtx.chainMappingByIndex(mapping.externalChainIndex)
                } else {
                    sqlCtx.mainChainMapping()
                }
                return "class:${chainMapping.chainId}:${mapping.metaName}"
            }
        }
        val gtvConversion: Rt_TypeGtvConversion = object: Rt_TypeGtvConversion {
            override fun rtToGtv(value: Rt_Value, pretty: Boolean) =
                GtvInteger(value.asObjectId())

            override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
                val rowid = if (gtv.type == GtvType.INTEGER) gtv.asInteger()
                else throw GtvRtUtils.errGtvType(
                    ctx,
                    name,
                    "INTEGER:${gtv.type}",
                    "expected INTEGER, actual ${gtv.type}",
                )
                return ctx.rtValue {
                    ctx.trackRecordRR(entityDef, rowid)
                    Rt_EntityValue(rtType, rowid)
                }
            }
        }
        val entityComparator = Comparator<Rt_Value> { a, b -> a.asObjectId().compareTo(b.asObjectId()) }
        rtType = Rt_Type(
            rrType = rrType,
            name = name,
            sqlAdapter = sqlAdapter,
            gtvConversion = gtvConversion,
            comparator = entityComparator,
        )
        return rtType
    }

    private fun buildEnumRtType(rrType: RR_Type.Enum): Rt_Type {
        val enumDef = rrApp.allEnums[rrType.defIndex]
        val name = enumDef.base.appLevelName
        lateinit var rtType: Rt_Type
        val rtValues: List<Rt_Value> by lazy {
            enumDef.attrs.map { attr -> Rt_RR_EnumValue(lazy { rtType }, attr) }
        }
        val sqlAdapter: Rt_TypeSqlAdapter = object: Rt_TypeSqlAdapter {
            override val sqlType: org.jooq.DataType<*>? = org.jooq.impl.SQLDataType.INTEGER

            override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) {
                params.setInt(idx, value.asEnum().value)
            }

            override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
                val v = row.getInt(idx)
                return if (v == 0 && row.wasNull()) {
                    if (nullable) Rt_NullValue
                    else throw Rt_Exception.common("sql_null:$name", "SQL value is NULL for type $name")
                } else {
                    rtValues.getOrNull(v)
                        ?: throw Rt_Exception.common(
                            "enum_bad_sql:$name:$v",
                            "Invalid enum value $v for type $name",
                        )
                }
            }

            override fun metaName(sqlCtx: Rt_SqlContext): String = "enum:$name"
        }
        val gtvConversion: Rt_TypeGtvConversion = object: Rt_TypeGtvConversion {
            override fun rtToGtv(value: Rt_Value, pretty: Boolean): Gtv {
                val e = value.asEnum()
                return if (pretty) GtvString(e.name) else GtvInteger(e.value.toLong())
            }

            override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
                val attr = if (ctx.pretty && gtv.type == GtvType.STRING) {
                    val s = gtv.asString()
                    enumDef.attr(s)
                        ?: throw GtvRtUtils.errGtvType(ctx, name, "enum:bad_value:$s", "invalid value: '$s'")
                } else {
                    val v = if (gtv.type == GtvType.INTEGER) gtv.asInteger()
                    else throw GtvRtUtils.errGtvType(
                        ctx,
                        name,
                        "INTEGER:${gtv.type}",
                        "expected INTEGER, actual ${gtv.type}",
                    )
                    enumDef.attr(v)
                        ?: throw GtvRtUtils.errGtvType(ctx, name, "enum:bad_value:$v", "invalid value: $v")
                }
                val idx = attr.value
                return ctx.rtValue { rtValues[idx] }
            }
        }
        val comparator = Comparator<Rt_Value> { a, b -> a.asEnum().value.compareTo(b.asEnum().value) }
        rtType = Rt_Type(
            rrType = rrType,
            name = name,
            sqlAdapter = sqlAdapter,
            gtvConversion = gtvConversion,
            comparator = comparator,
        )
        return rtType
    }

    private fun buildObjectRtType(rrType: RR_Type.Object): Rt_Type {
        val objectDef = rrApp.allObjects[rrType.defIndex]
        val name = objectDef.base.appLevelName
        return Rt_Type(
            rrType = rrType,
            name = name,
            sqlAdapter = null,
            gtvConversion = null,
            comparator = null,
        )
    }

    private fun buildStructRtType(rrType: RR_Type.Struct): Rt_Type {
        val structDef = rrApp.allStructs[rrType.defIndex]
        val name = structDef.struct.name
        return Rt_Type(
            rrType = rrType,
            name = name,
            sqlAdapter = null,
            gtvConversion = buildStructGtvConversion(rrType, structDef),
            comparator = null,
        )
    }

    internal fun rrTypeName(type: RR_Type): String = when (type) {
        is RR_Type.Primitive -> type.kind.name.lowercase()
        is RR_Type.Null -> "null"
        is RR_Type.Entity -> rrApp.allEntities.getOrNull(type.defIndex)?.base?.appLevelName ?: "entity#${type.defIndex}"
        is RR_Type.Struct -> rrApp.allStructs.getOrNull(type.defIndex)?.struct?.name ?: "struct#${type.defIndex}"
        is RR_Type.Enum -> rrApp.allEnums.getOrNull(type.defIndex)?.base?.appLevelName ?: "enum#${type.defIndex}"
        is RR_Type.Object -> rrApp.allObjects.getOrNull(type.defIndex)?.base?.appLevelName ?: "object#${type.defIndex}"
        is RR_Type.Nullable -> {
            val inner = rrTypeName(type.value)
            if (type.value is RR_Type.Function) "($inner)?" else "$inner?"
        }

        is RR_Type.List -> "list<${rrTypeName(type.element)}>"
        is RR_Type.Set -> "set<${rrTypeName(type.element)}>"
        is RR_Type.Map -> "map<${rrTypeName(type.key)},${rrTypeName(type.value)}>"
        is RR_Type.Tuple -> "(${
            type.fields.joinToString(",") { f ->
                if (f.name != null) "${f.name}:${rrTypeName(f.type)}" else rrTypeName(
                    f.type,
                )
            }
        })"

        is RR_Type.Function -> "(${type.params.joinToString(",") { rrTypeName(it) }})->${rrTypeName(type.result)}"
        is RR_Type.VirtualList -> "virtual<list<${rrTypeName(type.element)}>>"
        is RR_Type.VirtualSet -> "virtual<set<${rrTypeName(type.element)}>>"
        is RR_Type.VirtualMap -> "virtual<map<${rrTypeName(type.key)},${rrTypeName(type.value)}>>"
        is RR_Type.VirtualStruct -> "virtual<${rrApp.allStructs.getOrNull(type.defIndex)?.struct?.name ?: "struct#${type.defIndex}"}>"
        is RR_Type.VirtualTuple -> "virtual<(${
            type.fields.joinToString(",") { f ->
                if (f.name != null) "${f.name}:${rrTypeName(f.type)}" else rrTypeName(f.type)
            }
        })>"

        is RR_Type.Generic -> if (type.args.isEmpty()) type.name else "${type.name}<${
            type.args.joinToString(",") {
                rrTypeName(
                    it,
                )
            }
        }>"

        is RR_Type.Operation -> rrApp.allOperations[type.defIndex].base.simpleName
        is RR_Type.Error -> "<error>"
    }

    /** Optionally wraps [body] in a lambda block entry if [lambdaBlock] is non-null. */
    fun <T> executeInLambdaBlock(
        lambdaBlock: RR_FrameBlock?,
        lambdaVarPtr: RR_VarPtr?,
        lambdaExpr: RR_Expr?,
        frame: Rt_CallFrame,
        body: () -> T
    ): T {
        if (lambdaBlock == null) return body()
        // Evaluate the target expression before entering the block (matches R_UpdateTarget_Expr.execute).
        val value = lambdaExpr?.let { evaluateExpr(it, frame) }
        return frame.block(lambdaBlock) {
            if (value != null && lambdaVarPtr != null) {
                frame.setUnchecked(lambdaVarPtr, value, false)
            }
            body()
        }
    }

    fun createFrame(
        exeCtx: Rt_ExecutionContext,
        rrFrame: RR_FrameDescriptor,
        dbUpdateAllowed: Boolean,
        defId: DefinitionId = DefinitionId.ERROR
    ): Rt_CallFrame {
        val defCtx = Rt_DefinitionContext(exeCtx, dbUpdateAllowed, defId)
        return Rt_CallFrame(defCtx, rrFrame, null)
    }

    private fun validateParams(params: List<RR_FunctionParam>, args: List<Rt_Value>) {
        for (i in args.indices) {
            if (i < params.size) {
                val constraint = params[i].sizeConstraint ?: continue
                checkSizeConstraint(constraint, args[i])
            }
        }
    }

    internal fun checkSizeConstraint(constraint: RR_SizeConstraint, value: Rt_Value) {
        if (value == Rt_NullValue) return
        val size = when (constraint.kind) {
            RR_SizeConstraintKind.BYTE_ARRAY -> value.asByteArray().size
            RR_SizeConstraintKind.TEXT -> value.asString().length
        }
        val min = constraint.min
        val max = constraint.max
        // Parse codePrefix "defType:ownerName:targetType:paramName" for building the error message.
        val parts = constraint.codePrefix.split(":")
        val defTypeStr = parts.getOrElse(0) { "?" }
        val ownerName = parts.getOrElse(1) { "?" }
        val targetType = parts.getOrElse(2) { "?" }
        val targetTypeCapitalized =
            targetType.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val paramName = parts.getOrElse(3) { "?" }
        if (min != null && size < min) {
            val maxStr = if (max != null) " The specified maximum size is $max." else " No maximum is specified."
            throw Rt_Exception.common(
                "${constraint.codePrefix}:validator:size:too_small",
                "$targetTypeCapitalized $paramName of $defTypeStr $ownerName: size too small: specified minimum is $min (inclusive), got $size.$maxStr",
            )
        }
        if (max != null && size > max) {
            val minStr = if (min != null) " The specified minimum size is $min." else " No minimum is specified."
            throw Rt_Exception.common(
                "${constraint.codePrefix}:validator:size:too_large",
                "$targetTypeCapitalized $paramName of $defTypeStr $ownerName: size too large: specified maximum is $max (inclusive), got $size.$minStr",
            )
        }
    }

    private fun setParams(
        frame: Rt_CallFrame,
        paramVars: List<RR_ParamVar>,
        args: List<Rt_Value>,
        fnName: String? = null
    ) {
        if (args.size != paramVars.size) {
            val name = fnName ?: "?"
            throw Rt_Exception.common(
                "fn_wrong_arg_count:$name:${paramVars.size}:${args.size}",
                "Wrong number of arguments for '$name': expected ${paramVars.size}, got ${args.size}",
            )
        }
        for (i in paramVars.indices) {
            val paramType = paramVars[i].type
            val argType = args[i].type().rrType!!
            if (!isAssignableRR(paramType, argType)) {
                val name = fnName ?: "?"
                val expected = rrTypeName(paramType)
                val actual = rrTypeName(argType)
                throw Rt_Exception.common(
                    "fn_wrong_arg_type:$name:$expected:$actual",
                    "Wrong argument type for '$name': expected $expected, got $actual",
                )
            }
            frame.setUnchecked(paramVars[i].ptr, args[i], false)
        }
    }

    private fun isAssignableRR(paramType: RR_Type, argType: RR_Type): Boolean {
        if (paramType == argType) return true
        // null literal is assignable to any nullable type
        if (argType == RR_Type.Null && paramType is RR_Type.Nullable) return true
        // T is assignable to T?
        if (paramType is RR_Type.Nullable && paramType.value == argType) return true
        // RR_Type.Error appears when the JVM code path (rTypeToRRType) cannot resolve
        // definition-based types (struct/entity/enum/object) that require a def index.
        // Skip the type check in that case — the runtime value is correctly typed.
        if (containsError(argType)) return true
        return false
    }

    private fun containsError(type: RR_Type): Boolean = when (type) {
        is RR_Type.Error -> true
        // JVM-path rTypeToRRType produces invalid (-1) def indices for definition-backed types
        // (entity/struct/enum/object) that haven't gone through RR resolution. Treat them as unresolved.
        is RR_Type.Entity -> type.defIndex < 0
        is RR_Type.Struct -> type.defIndex < 0
        is RR_Type.Enum -> type.defIndex < 0
        is RR_Type.Object -> type.defIndex < 0
        is RR_Type.Nullable -> containsError(type.value)
        is RR_Type.List -> containsError(type.element)
        is RR_Type.Set -> containsError(type.element)
        is RR_Type.Map -> containsError(type.key) || containsError(type.value)
        is RR_Type.Tuple -> type.fields.any { containsError(it.type) }
        is RR_Type.VirtualList -> containsError(type.element)
        is RR_Type.VirtualSet -> containsError(type.element)
        is RR_Type.VirtualMap -> containsError(type.key) || containsError(type.value)
        is RR_Type.VirtualTuple -> type.fields.any { containsError(it.type) }
        is RR_Type.VirtualStruct -> type.defIndex < 0
        is RR_Type.Function -> type.params.any { containsError(it) } || containsError(type.result)
        else -> false
    }

    fun callTarget(
        target: RR_FunctionCallTarget,
        base: Rt_Value?,
        args: List<Rt_Value>,
        frame: Rt_CallFrame,
        callPos: FilePos? = null,
    ): Rt_Value {
        val result = try {
            callTarget0(target, base, args, frame)
        } catch (e: Rt_Exception) {
            if (callPos != null) {
                frame.error(ErrorPos(callPos), e, true)
            } else {
                throw e
            }
        }
        return result
    }

    private fun callTarget0(
        target: RR_FunctionCallTarget,
        base: Rt_Value?,
        args: List<Rt_Value>,
        frame: Rt_CallFrame,
    ): Rt_Value = when (target) {
        is RR_FunctionCallTarget.RegularUser -> {
            val fn = rrApp.allFunctions[target.fnDefIndex]
            callFunction(fn, frame.exeCtx, args, dbUpdateAllowed = frame.dbUpdateAllowed())
        }

        is RR_FunctionCallTarget.RegularQuery -> {
            val query = rrApp.allQueries[target.queryDefIndex]
            callQuery(query, frame.exeCtx, args)
        }

        is RR_FunctionCallTarget.AbstractUser -> {
            val fn = rrApp.allFunctions[target.fnDefIndex]
            callFunction(fn, frame.exeCtx, args, dbUpdateAllowed = frame.dbUpdateAllowed())
        }

        is RR_FunctionCallTarget.AbstractOverride -> {
            // Call the override function body via the RR interpreter
            val fnBase = target.fnBase
            val overrideFrame =
                createFrame(frame.exeCtx, fnBase.frame, dbUpdateAllowed = frame.dbUpdateAllowed(), fnBase.defId)
            checkEquals(args.size, fnBase.paramVars.size) { "Expected ${fnBase.paramVars.size} args, got ${args.size}" }
            for (i in fnBase.paramVars.indices) {
                overrideFrame.setUnchecked(fnBase.paramVars[i].ptr, args[i], false)
            }
            val result = executeStmt(fnBase.body, overrideFrame)
            if (result is Rt_StatementResult_Return) result.value ?: Rt_UnitValue else Rt_UnitValue
        }

        is RR_FunctionCallTarget.Extendable -> {
            val extensions = rrApp.functionExtensions[target.extendableUidId].extensions
            val combiner = createExtendableCombiner(target.combinerKind, target.returnType)
            for (rrFnBase in extensions) {
                val extFrame =
                    createFrame(frame.exeCtx, rrFnBase.frame, dbUpdateAllowed = frame.dbUpdateAllowed(), rrFnBase.defId)
                setParams(extFrame, rrFnBase.paramVars, args, rrFnBase.defName.appLevelName)
                val result = executeStmt(rrFnBase.body, extFrame)
                val value = if (result is Rt_StatementResult_Return) result.value ?: Rt_UnitValue else Rt_UnitValue
                if (combiner.addExtensionResult(value)) break
            }
            combiner.getCombinedResult()
        }

        is RR_FunctionCallTarget.SysGlobal -> {
            val fn = checkNotNull(stdlib.sysFunctions[target.fnName]) {
                "Sys function not found: ${target.fnName}"
            }
            checkNull(base)
            R_SysFunctionUtils.call(frame.callCtx(), fn, stripSysFnHash(target.fnName), args)
        }

        is RR_FunctionCallTarget.SysMember -> {
            val fn = checkNotNull(stdlib.sysFunctions[target.fnName]) {
                "Sys member function not found: ${target.fnName}"
            }
            checkNotNull(base)
            R_SysFunctionUtils.call(frame.callCtx(), fn, stripSysFnHash(target.fnName), listOf(base) + args)
        }

        is RR_FunctionCallTarget.Operation -> {
            val op = rrApp.allOperations[target.opDefIndex]
            // In test mode, operations return Rt_TestOpValue (not executed).
            // In production mode, they would be executed via callOperation.
            val params = op.params
            val gtvArgs = args.mapIndexedToImmList { i, arg ->
                if (i < params.size) {
                    val constraint = params[i].sizeConstraint
                    if (constraint != null) {
                        checkSizeConstraint(constraint, arg)
                    }
                }
                val rtType = resolveType(if (i < params.size) params[i].type else arg.type().rrType!!)
                val conv: Rt_TypeGtvConversion = checkNotNull(rtType.gtvConversion) {
                    "No GTV conversion for type: ${rtType.name}"
                }
                conv.rtToGtv(arg, false)
            }
            Rt_TestOpValue(op.mountName, gtvArgs)
        }

        is RR_FunctionCallTarget.FunctionValue -> {
            checkNotNull(base)
            val fnValue = base.asFunction()
            fnValue.call(args)
        }

        is RR_FunctionCallTarget.NativeUser -> {
            checkNull(base)
            val fn = frame.appCtx.nativeFunctions.getValue(target.fullName)
            val rrHeader = rrApp.nativeFunctions.getValue(target.fullName)
            val nativeArgs = args.mapIndexed { i, value ->
                val paramRtType = resolveType(rrHeader.params[i].type)
                val conv = checkNotNull(paramRtType.nativeConversion) {
                    "No native conversion for type: ${paramRtType.name}"
                }
                conv.rtToNative(value)
            }.toImmList()
            val nativeRes = fn.call(nativeArgs)
            val resultRtType = resolveType(rrHeader.type)
            val resultConv = checkNotNull(resultRtType.nativeConversion) {
                "No native conversion for result type: ${resultRtType.name}"
            }
            resultConv.nativeToRt(nativeRes)
        }
    }

    private fun createExtendableCombiner(
        kind: RR_ExtendableCombinerKind,
        returnType: RR_Type
    ): Rt_ExtendableFunctionCombiner = when (kind) {
        RR_ExtendableCombinerKind.UNIT -> Rt_ExtendableFunctionCombiner_Unit
        RR_ExtendableCombinerKind.BOOLEAN -> Rt_ExtendableFunctionCombiner_Boolean()
        RR_ExtendableCombinerKind.NULLABLE -> Rt_ExtendableFunctionCombiner_Nullable()
        RR_ExtendableCombinerKind.LIST -> Rt_ExtendableFunctionCombiner_List(resolveType(returnType))
        RR_ExtendableCombinerKind.MAP -> Rt_ExtendableFunctionCombiner_Map(resolveType(returnType))
    }

    fun checkAtCount(
        frame: Rt_CallFrame,
        errPos: ErrorPos,
        cardinality: AtCardinality,
        count: Int,
        itemMsg: String,
    ) {
        if (!cardinality.matches(count)) {
            val code = "at:wrong_count:$count"
            val msg = if (count == 0) "No $itemMsg found" else "Multiple $itemMsg found: $count"
            frame.error(errPos, code, msg)
        }
    }

    val AtCardinality.isMany: Boolean get() = this == AtCardinality.ZERO_MANY || this == AtCardinality.ONE_MANY

    fun RR_Type.elementType(): RR_Type = when (this) {
        is RR_Type.List -> element
        is RR_Type.Set -> element
        is RR_Type.Nullable -> value.elementType()
        else -> this
    }
}

/**
 * Captured RR function call target plus the closure frame, used by [Rt_FunctionValue]
 * for partial application — invoking the value later dispatches through
 * [Rt_Interpreter.callTarget] with [outerFrame].
 */
class Rt_FunctionCallTarget(
    val interpreter: Rt_Interpreter,
    val rrTarget: RR_FunctionCallTarget,
    val outerFrame: Rt_CallFrame,
)

/** Shortcut to the shared sys-fn display-name helper in rt_fn_call_dispatch.kt. */
private fun stripSysFnHash(fnName: String): String = sysFnDisplayName(fnName)

/**
 * Lazy value that evaluates an [RR_Expr] on demand through the [Rt_Interpreter].
 * Used for `try_call` and similar constructs.
 */
private class Rt_RR_LazyValue(
    private val rtType: Rt_Type,
    private val expr: RR_Expr,
    private val frame: Rt_CallFrame,
    private val interpreter: Rt_Interpreter,
): Rt_Value() {
    private var cachedValue: Rt_Value? = null

    override val valueType get() = Rt_CoreValueTypes.LAZY.type()

    override fun type(): Rt_Type = rtType
    override fun str(format: StrFormat): String = "lazy[...]"
    override fun strCode(showTupleFieldNames: Boolean): String = "lazy[...]"

    override fun asLazyValue(): Rt_Value {
        var res = cachedValue
        if (res == null) {
            res = interpreter.evaluateExpr(expr, frame)
            cachedValue = res
        }
        return res
    }
}

/**
 * Enum value for the deserialized (RR_-only) path, where no [R_EnumType] is available.
 */
internal class Rt_RR_EnumValue(
    private val rtTypeRef: Lazy<Rt_Type>,
    private val rrAttr: RR_EnumAttr,
): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.ENUM.type()

    override fun type() = rtTypeRef.value
    override fun asEnum() = rrAttr
    override fun strCode(showTupleFieldNames: Boolean) = "${rtTypeRef.value.name}[${rrAttr.name}]"
    override fun str(format: StrFormat) = rrAttr.name

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is Rt_Value) return false
        // Compare by enum type name + value index for cross-compatibility.
        val otherAttr = try {
            other.asEnum()
        } catch (_: Exception) {
            return false
        }
        if (otherAttr.value != rrAttr.value) return false
        val otherType = try {
            other.type()
        } catch (_: Exception) {
            return false
        }
        return otherType.name == rtTypeRef.value.name
    }

    override fun hashCode(): Int = rtTypeRef.value.name.hashCode() * 31 + rrAttr.value
}
