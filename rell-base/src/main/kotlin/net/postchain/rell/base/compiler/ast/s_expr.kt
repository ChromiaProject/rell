/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.compiler.base.utils.C_Utils
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.*
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.stmt.R_IterableAdapter
import net.postchain.rell.base.model.stmt.R_IterableAdapter_Direct
import net.postchain.rell.base.mtype.M_TypeUtils
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.utils.doc.DocSymbolFactory
import net.postchain.rell.base.utils.ide.IdeSymbolId
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmSet

abstract class S_Expr(val startPos: S_Pos) {
    abstract fun compile(ctx: C_ExprContext, hint: C_ExprHint = C_ExprHint.DEFAULT): C_Expr

    fun compileOpt(ctx: C_ExprContext, hint: C_ExprHint = C_ExprHint.DEFAULT): C_Expr? {
        return ctx.msgCtx.consumeError {
            compile(ctx, hint)
        }
    }

    fun compileSafe(ctx: C_ExprContext, hint: C_ExprHint = C_ExprHint.DEFAULT): C_Expr {
        return compileOpt(ctx, hint) ?: C_ExprUtils.errorExpr(ctx, startPos)
    }

    fun compileWithFacts(ctx: C_ExprContext, facts: C_VarFacts, hint: C_ExprHint = C_ExprHint.DEFAULT): C_Expr {
        val factsCtx = ctx.updateFacts(facts)
        return compile(factsCtx, hint)
    }

    fun compile(ctx: C_StmtContext, hint: C_ExprHint = C_ExprHint.DEFAULT) = compile(ctx.exprCtx, hint)
    fun compileOpt(ctx: C_StmtContext, hint: C_ExprHint = C_ExprHint.DEFAULT) = compileOpt(ctx.exprCtx, hint)

    open fun compileFrom(ctx: C_ExprContext, fromCtx: C_AtFromContext, subValues: MutableList<V_Expr>): C_AtFrom {
        val item = compileFromItem(ctx, null)
        return when (item) {
            is C_AtFromItem_Entity -> {
                val atEntityId = ctx.appCtx.nextAtEntityId(fromCtx.atExprId)
                val cAtEntity = S_AtExpr.makeDbAtEntity(item.entity, item.alias, null, atEntityId, ctx.docFactory)
                C_AtFrom_Entities(ctx, fromCtx, immListOf(cAtEntity))
            }
            is C_AtFromItem_Iterable -> {
                subValues.add(item.vExpr)
                C_AtFrom_Iterable(ctx, fromCtx, null, item)
            }
        }
    }

    open fun compileFromItem(ctx: C_ExprContext, explicitAlias: R_Name?): C_AtFromItem {
        val cExpr = compileSafe(ctx)
        return exprToFromItem(ctx, explicitAlias, cExpr)
    }

    protected fun exprToFromItem(ctx: C_ExprContext, explicitAlias: R_Name?, cExpr: C_Expr): C_AtFromItem {
        val vExpr = cExpr.value()

        val rType = vExpr.type
        val cIterableAdapter = C_IterableAdapter.compile(ctx, rType, true)

        val rItemType: R_Type
        val rAdapter: R_IterableAdapter

        if (cIterableAdapter == null) {
            val s = rType.strCode()
            ctx.msgCtx.error(startPos, "at:from:bad_type:$s", "Invalid type for at-expression: $s")
            rItemType = rType
            rAdapter = R_IterableAdapter_Direct
        } else {
            rItemType = cIterableAdapter.itemType
            rAdapter = cIterableAdapter.rAdapter
        }

        val ideInfo = S_AtExpr.makeColAtIdeInfo(ctx.docFactory, explicitAlias, rItemType)
        return C_AtFromItem_Iterable(startPos, vExpr, rItemType, ideInfo, rAdapter)
    }

    open fun compileNestedAt(ctx: C_ExprContext, parentAtCtx: C_AtContext): C_Expr = compileSafe(ctx)

    protected open fun compileWhenEnum(ctx: C_ExprContext, type: R_EnumType): C_Expr = compile(ctx)

    fun compileWhenEnumOpt(ctx: C_ExprContext, type: R_EnumType): C_Expr? {
        return ctx.msgCtx.consumeError {
            compileWhenEnum(ctx, type)
        }
    }

    open fun asName(): S_QualifiedName? = null
    open fun constantValue(): Rt_Value? = null
}

sealed class S_LiteralExpr(pos: S_Pos): S_Expr(pos) {
    abstract fun value(): Rt_Value

    final override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        val v = value()
        val vExpr = V_ConstantValueExpr(ctx, startPos, v)
        return C_ValueExpr(vExpr)
    }
}

class S_StringLiteralExpr(pos: S_Pos, val literal: String): S_LiteralExpr(pos) {
    override fun value() = Rt_TextValue.get(literal)
}

class S_ByteArrayLiteralExpr(pos: S_Pos, val bytes: ByteArray): S_LiteralExpr(pos) {
    override fun value() = Rt_ByteArrayValue.get(bytes)
}

class S_IntegerLiteralExpr(pos: S_Pos, val value: Long): S_LiteralExpr(pos) {
    override fun value() = Rt_IntValue.get(value)
}

class S_CommonLiteralExpr(pos: S_Pos, val value: Rt_Value): S_LiteralExpr(pos) {
    override fun value() = value
}

class S_BooleanLiteralExpr(pos: S_Pos, val value: Boolean): S_LiteralExpr(pos) {
    override fun value() = Rt_BooleanValue.get(value)
}

class S_NullLiteralExpr(pos: S_Pos): S_LiteralExpr(pos) {
    override fun value() = Rt_NullValue
}

class S_SubscriptExpr(val opPos: S_Pos, val base: S_Expr, val expr: S_Expr): S_Expr(base.startPos) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        val vBase = base.compile(ctx).value()
        val vExpr = expr.compile(ctx).value()

        val baseType = vBase.type
        val effectiveType = C_Types.removeNullable(baseType)

        val kind = compileSubscriptKind(ctx, effectiveType)
        if (kind == null) {
            return C_ExprUtils.errorExpr(ctx, opPos)
        }

        C_Errors.check(ctx.msgCtx, baseType !is R_NullableType, opPos) {
            "expr_subscript_null" toCodeMsg "Cannot apply '[]' on nullable value"
        }

        C_Types.match(kind.keyType, vExpr.type, expr.startPos) {
            "expr_subscript_keytype" toCodeMsg "Invalid subscript key type"
        }

        val vResExpr = kind.compile(ctx, vBase, vExpr)
        return C_ValueExpr(vResExpr)
    }

    private fun compileSubscriptKind(ctx: C_ExprContext, baseType: R_Type): Subscript? {
        return when (baseType) {
            R_TextType -> Subscript_Common(R_IntegerType, V_CommonSubscriptKind_Text)
            R_ByteArrayType -> Subscript_Common(R_IntegerType, V_CommonSubscriptKind_ByteArray)
            is R_ListType -> Subscript_Common(R_IntegerType, V_CommonSubscriptKind_List(baseType.elementType))
            is R_VirtualListType -> {
                val resType = S_VirtualType.virtualMemberType(baseType.innerType.elementType)
                Subscript_Common(R_IntegerType, V_CommonSubscriptKind_VirtualList(resType))
            }
            is R_MapType -> Subscript_Common(baseType.keyType, V_CommonSubscriptKind_Map(baseType.valueType))
            is R_VirtualMapType -> {
                val resType = S_VirtualType.virtualMemberType(baseType.innerType.valueType)
                Subscript_Common(baseType.innerType.keyType, V_CommonSubscriptKind_VirtualMap(resType))
            }
            is R_TupleType -> Subscript_Tuple(baseType)
            is R_VirtualTupleType -> Subscript_VirtualTuple(baseType)
            else -> {
                val typeStr = baseType.strCode()
                ctx.msgCtx.error(opPos, "expr_subscript_base:$typeStr", "Operator '[]' undefined for type $typeStr")
                null
            }
        }
    }

    private fun compileTuple0(vExpr: V_Expr, baseType: R_TupleType): Int {
        val indexZ = C_ExprUtils.evaluate(expr.startPos) {
            val value = vExpr.constantValue(V_ConstantValueEvalContext())
            value?.asInteger()
        }

        val index = C_Errors.checkNotNull(indexZ, expr.startPos) {
            "expr_subscript:tuple:no_const" toCodeMsg
            "Subscript key for a tuple must be a constant value, not an expression"
        }

        val fields = baseType.fields
        C_Errors.check(index >= 0 && index < fields.size, expr.startPos) {
            "expr_subscript:tuple:index:$index:${fields.size}" toCodeMsg
            "Index out of bounds, must be from 0 to ${fields.size - 1}"
        }

        return index.toInt()
    }

    private inner abstract class Subscript(val keyType: R_Type) {
        abstract fun compile(ctx: C_ExprContext, base: V_Expr, key: V_Expr): V_Expr
    }

    private inner class Subscript_Common(keyType: R_Type, val kind: V_CommonSubscriptKind): Subscript(keyType) {
        override fun compile(ctx: C_ExprContext, base: V_Expr, key: V_Expr): V_Expr {
            return V_CommonSubscriptExpr(ctx, startPos, base, key, kind)
        }
    }

    private inner class Subscript_Tuple(private val baseType: R_TupleType): Subscript(R_IntegerType) {
        override fun compile(ctx: C_ExprContext, base: V_Expr, key: V_Expr): V_Expr {
            val index = compileTuple0(key, baseType)
            val resType = baseType.fields[index].type
            return V_TupleSubscriptExpr(ctx, startPos, base, V_TupleSubscriptKind_Simple, resType, index)
        }
    }

    private inner class Subscript_VirtualTuple(private val baseType: R_VirtualTupleType): Subscript(R_IntegerType) {
        override fun compile(ctx: C_ExprContext, base: V_Expr, key: V_Expr): V_Expr {
            val index = compileTuple0(key, baseType.innerType)
            val fieldType = baseType.innerType.fields[index].type
            val resType = S_VirtualType.virtualMemberType(fieldType)
            return V_TupleSubscriptExpr(ctx, startPos, base, V_TupleSubscriptKind_Virtual, resType, index)
        }
    }
}

class S_CreateExpr(pos: S_Pos, val entityName: S_QualifiedName, val args: List<S_CallArgument>): S_Expr(pos) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        ctx.checkDbUpdateAllowed(startPos)

        val entityNameHand = entityName.compile(ctx)

        val entity = ctx.nsCtx.getEntity(entityNameHand)
        entity ?: return C_ExprUtils.errorExpr(ctx, entityName.pos)

        val cArgs = C_CallArgument.compileAttributes(ctx, args, entity.attributes)

        var vExpr = compileStruct(ctx, entity, cArgs, entity.mirrorStructs.immutable)
        if (vExpr == null) vExpr = compileStruct(ctx, entity, cArgs, entity.mirrorStructs.mutable)
        if (vExpr == null) vExpr = compileStructList(ctx, entity, cArgs, entity.mirrorStructs.immutable)
        if (vExpr == null) vExpr = compileStructList(ctx, entity, cArgs, entity.mirrorStructs.mutable)

        if (vExpr == null) {
            vExpr = compileRegular(ctx, entity, cArgs)
        }

        return C_ValueExpr(vExpr)
    }

    private fun compileRegular(ctx: C_ExprContext, entity: R_EntityDefinition, callArgs: List<C_CallArgument>): V_Expr {
        val createCtx = C_CreateContext(ctx, entity.initFrameGetter, startPos.toFilePos())

        val attrArgs = C_CallArgument.toAttrArguments(ctx, callArgs, C_CodeMsg("create_expr", "create expression"))

        val attrs = C_AttributeResolver.resolveCreate(
            createCtx,
            entity.appLevelName,
            entity.attributes,
            attrArgs,
            startPos,
        )

        C_Errors.check(entity.flags.canCreate, startPos) {
            val entityNameStr = entityName.str()
            "expr_create_cant:$entityNameStr" toCodeMsg "Not allowed to create instances of entity '$entityNameStr'"
        }

        return V_RegularCreateExpr(ctx, startPos, entity, attrs)
    }

    private fun compileStruct(
        ctx: C_ExprContext,
        entity: R_EntityDefinition,
        args: List<C_CallArgument>,
        struct: R_Struct,
    ): V_Expr? {
        val vExpr = getSingleArgExprOrNull(args)
        vExpr ?: return null

        val structType = struct.type
        return if (!structType.isAssignableFrom(vExpr.type)) null else {
            V_StructCreateExpr(ctx, startPos, entity, structType, vExpr)
        }
    }

    private fun compileStructList(
        ctx: C_ExprContext,
        entity: R_EntityDefinition,
        args: List<C_CallArgument>,
        struct: R_Struct,
    ): V_Expr? {
        val vExpr = getSingleArgExprOrNull(args)
        vExpr ?: return null

        val listType = R_ListType(struct.type)
        return if (!listType.isAssignableFrom(vExpr.type)) null else {
            V_StructListCreateExpr(ctx, startPos, entity, struct.type, vExpr)
        }
    }

    private fun getSingleArgExprOrNull(args: List<C_CallArgument>): V_Expr? {
        if (args.size != 1 || args[0].name != null) return null
        val arg = args[0]
        return when (arg.value) {
            is C_CallArgumentValue_Wildcard -> null
            is C_CallArgumentValue_Expr -> arg.value.vExpr
        }
    }
}

class S_ParenthesesExpr(startPos: S_Pos, val expr: S_Expr): S_Expr(startPos) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        val cExpr = expr.compile(ctx, hint)
        val vExpr = cExpr.value()
        return C_ValueExpr(vExpr)
    }

    override fun compileNestedAt(ctx: C_ExprContext, parentAtCtx: C_AtContext) = expr.compileNestedAt(ctx, parentAtCtx)
    override fun compileFromItem(ctx: C_ExprContext, explicitAlias: R_Name?) = expr.compileFromItem(ctx, explicitAlias)
}

sealed class S_TupleExprField
class S_TupleExprField_Expr(val expr: S_Expr): S_TupleExprField()
class S_TupleExprField_NameEqExpr(val name: S_Name, val eqPos: S_Pos, val expr: S_Expr): S_TupleExprField()
class S_TupleExprField_NameColonExpr(val name: S_Name, val colonPos: S_Pos, val expr: S_Expr): S_TupleExprField()

class S_TupleExpr(startPos: S_Pos, val fields: List<S_TupleExprField>): S_Expr(startPos) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        val sPairs = fields.map {
            when (it) {
                is S_TupleExprField_NameColonExpr -> {
                    ctx.msgCtx.error(it.colonPos, "tuple_name_colon_expr:${it.name}", "Syntax error")
                    S_NameOptValue(it.name, it.expr)
                }
                is S_TupleExprField_NameEqExpr -> S_NameOptValue(it.name, it.expr)
                is S_TupleExprField_Expr -> S_NameOptValue(null, it.expr)
            }
        }

        // ID needs to be allocaed in advance, before processing sub-expressions (for correct numbering).
        val tupleIdeId = ctx.defCtx.tupleIdeId()

        val fields = sPairs.map { (name, expr) ->
            val nameHand = name?.compile(ctx)
            C_TupleField(nameHand, expr)
        }

        checkNameConflicts(ctx, fields)

        val vExprs = fields.mapIndexed { index, field ->
            val fieldTypeHint = hint.typeHint.getTupleFieldHint(index)
            val fieldExprHint = C_ExprHint(fieldTypeHint)
            field.sExpr.compileSafe(ctx, fieldExprHint).value()
        }

        val vExpr = compile0(ctx, tupleIdeId, fields, vExprs)
        return C_ValueExpr(vExpr)
    }

    private fun checkNameConflicts(ctx: C_ExprContext, fields: List<C_TupleField>): Set<String> {
        val names = mutableSetOf<String>()
        val dups = mutableSetOf<String>()

        for (field in fields) {
            val name = field.name
            if (name != null) {
                if (!names.add(name.str)) {
                    ctx.msgCtx.error(name.pos, "expr_tuple_dupname:$name", "Duplicate field: '$name'")
                    dups.add(name.str)
                }
            }
        }

        return dups.toImmSet()
    }

    private fun compile0(
        ctx: C_ExprContext,
        tupleIdeId: IdeSymbolId,
        fields: List<C_TupleField>,
        vExprs: List<V_Expr>,
    ): V_Expr {
        for ((i, vExpr) in vExprs.withIndex()) {
            C_Utils.checkUnitType(fields[i].sExpr.startPos, vExpr.type) {
                "expr_tuple_unit" toCodeMsg "Type of expression is unit"
            }
        }

        val rFields = vExprs.mapIndexed { i, vExpr ->
            val nameHand = fields[i].nameHand
            val rType = vExpr.type
            val fieldName = compileFieldName(tupleIdeId, nameHand, rType, ctx.docFactory)
            R_TupleField(fieldName, rType)
        }

        val type = R_TupleType(rFields)
        return V_TupleExpr(ctx, startPos, type, vExprs)
    }

    private fun compileFieldName(
        tupleIdeId: IdeSymbolId,
        nameHand: C_NameHandle?,
        rType: R_Type,
        docFactory: DocSymbolFactory,
    ): R_IdeName? {
        nameHand ?: return null
        val ideDef = S_TupleType.makeFieldIdeDef(docFactory, tupleIdeId, nameHand.name, rType)
        nameHand.setIdeInfo(ideDef.defInfo)
        return R_IdeName(nameHand.rName, ideDef.refInfo)
    }

    override fun compileFrom(ctx: C_ExprContext, fromCtx: C_AtFromContext, subValues: MutableList<V_Expr>): C_AtFrom {
        val sPairs = fields.map {
            when (it) {
                is S_TupleExprField_NameColonExpr -> Pair(it.name, it.expr)
                is S_TupleExprField_NameEqExpr -> {
                    ctx.msgCtx.error(it.eqPos, "expr:at:from:tuple_name_eq_expr:${it.name}", "Syntax error")
                    Pair(it.name, it.expr)
                }
                is S_TupleExprField_Expr -> Pair(null, it.expr)
            }
        }

        val pairs = sPairs.map { (name, expr) ->
            val nameHand = name?.compile(ctx)
            val item = expr.compileFromItem(ctx, nameHand?.rName)
            item to nameHand
        }

        val entities = mutableListOf<Pair<C_AtFromItem_Entity, C_NameHandle?>>()
        val iterables = mutableListOf<Pair<C_AtFromItem_Iterable, C_NameHandle?>>()

        for ((item, aliasNameHand) in pairs) {
            when (item) {
                is C_AtFromItem_Entity -> processFromItem(ctx, item, aliasNameHand, entities, iterables)
                is C_AtFromItem_Iterable -> processFromItem(ctx, item, aliasNameHand, iterables, entities)
            }
        }

        if (entities.isNotEmpty()) {
            for (pair in iterables) {
                pair.second?.setIdeInfo(C_IdeSymbolInfo.UNKNOWN)
            }
            val cEntities = entities.map { (item, explicitAliasHand) ->
                val alias = explicitAliasHand?.name ?: item.alias
                val atEntityId = ctx.appCtx.nextAtEntityId(fromCtx.atExprId)
                S_AtExpr.makeDbAtEntity(item.entity, alias, explicitAliasHand, atEntityId, ctx.docFactory)
            }
            return C_AtFrom_Entities(ctx, fromCtx, cEntities)
        }

        subValues.addAll(iterables.map { it.first.vExpr })

        if (iterables.size > 1 && iterables.any { it.first.vExpr.type.isNotError() }) {
            ctx.msgCtx.error(iterables[1].first.pos, "at:from:many_iterables:${iterables.size}",
                    "Only one collection is allowed in at-expression")
        }

        for (pair in iterables) {
            val nameHand = pair.second
            val ideInfo = S_AtExpr.makeColAtIdeInfo(ctx.docFactory, pair.second?.rName, pair.first.elemType)
            nameHand?.setIdeInfo(ideInfo)
        }

        val (iterable, alias) = iterables.first()
        return C_AtFrom_Iterable(ctx, fromCtx, alias?.name, iterable)
    }

    private fun <T: C_AtFromItem> processFromItem(
        ctx: C_ExprContext,
        item: T,
        aliasNameHand: C_NameHandle?,
        targets: MutableList<Pair<T, C_NameHandle?>>,
        opposites: List<*>,
    ) {
        if (opposites.isNotEmpty()) {
            ctx.msgCtx.error(item.pos,
                "at:from:mix_entity_iterable", "Cannot mix entities and collections in at-expression")
        }
        targets.add(item to aliasNameHand)
    }

    private class C_TupleField(val nameHand: C_NameHandle?, val sExpr: S_Expr) {
        val name = nameHand?.name
    }

    private class C_RawAtItem<out T: C_AtFromItem>(val aliasNameHand: C_NameHandle?, val item: T)
}

class S_IfExpr(pos: S_Pos, val cond: S_Expr, val trueExpr: S_Expr, val falseExpr: S_Expr): S_Expr(pos) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        val cCond = cond.compile(ctx).value()
        val (cTrue, cFalse, resFacts) = compileTrueFalse(ctx, cCond, hint)

        C_Types.match(R_BooleanType, cCond.type, cond.startPos) {
            "expr_if_cond_type" toCodeMsg "Wrong type of condition expression"
        }

        checkUnitType(trueExpr, cTrue)
        checkUnitType(falseExpr, cFalse)

        val trueType = cTrue.type
        val falseType = cFalse.type

        val resType = C_Types.commonType(trueType, falseType, startPos) {
            "expr_if_restype" toCodeMsg "Incompatible types of if branches"
        }

        val vExpr = V_IfExpr(ctx, startPos, resType, cCond, cTrue, cFalse, resFacts)
        return C_ValueExpr(vExpr)
    }

    private fun compileTrueFalse(ctx: C_ExprContext, cCond: V_Expr, hint: C_ExprHint): Triple<V_Expr, V_Expr, C_ExprVarFacts> {
        val condFacts = cCond.varFacts
        val trueFacts = condFacts.postFacts.and(condFacts.trueFacts)
        val falseFacts = condFacts.postFacts.and(condFacts.falseFacts)

        val cTrue0 = trueExpr.compileWithFacts(ctx, trueFacts, hint).value()
        val cFalse0 = falseExpr.compileWithFacts(ctx, falseFacts, hint).value()
        val (cTrue, cFalse) = C_BinOp_Common.promoteNumeric(ctx, cTrue0, cFalse0)

        val truePostFacts = trueFacts.and(cTrue.varFacts.postFacts)
        val falsePostFacts = falseFacts.and(cFalse.varFacts.postFacts)
        val resPostFacts = condFacts.postFacts.and(C_VarFacts.forBranches(ctx, listOf(truePostFacts, falsePostFacts)))
        val resFacts = C_ExprVarFacts.of(postFacts = resPostFacts)

        return Triple(cTrue, cFalse, resFacts)
    }

    private fun checkUnitType(expr: S_Expr, vExpr: V_Expr) {
        C_Utils.checkUnitType(expr.startPos, vExpr.type) { "expr_if_unit" toCodeMsg "Expression returns nothing" }
    }
}

class S_ListLiteralExpr(pos: S_Pos, val exprs: List<S_Expr>): S_Expr(pos) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        val vExprs = exprs.map { it.compile(ctx).value() }
        val listType = ctx.msgCtx.consumeError { compileType(vExprs, hint.typeHint) }
        listType ?: return C_ExprUtils.errorExpr(ctx, startPos)
        val vExpr = V_ListLiteralExpr(ctx, startPos, vExprs, listType)
        return C_ValueExpr(vExpr)
    }

    private fun compileType(vExprs: List<V_Expr>, typeHint: C_TypeHint): R_ListType {
        for (vExpr in vExprs) {
            C_Utils.checkUnitType(vExpr.pos, vExpr.type) { "expr_list_unit" toCodeMsg "Element expression returns nothing" }
        }

        val hintElemType = getHintElemType(typeHint)
        val rElemType = compileElementType(vExprs, hintElemType)
        return R_ListType(rElemType)
    }

    private fun getHintElemType(typeHint: C_TypeHint): R_Type? {
        val mGenType = Lib_Rell.LIST_TYPE.mGenericType
        val mHintType = L_TypeUtils.getExpectedType(mGenType.params, mGenType.commonType, typeHint)
        mHintType ?: return null

        val typeArgs = M_TypeUtils.getTypeArgs(mHintType)
        val mElemType = typeArgs[mGenType.params[0]]?.getExactType()
        mElemType ?: return null

        return L_TypeUtils.getRType(mElemType)
    }

    private fun compileElementType(vExprs: List<V_Expr>, hintElemType: R_Type?): R_Type {
        if (vExprs.isEmpty()) {
            return C_Errors.checkNotNull(hintElemType, startPos) {
                "expr_list_no_type" toCodeMsg
                "Cannot determine the type of the list; use list<T>() syntax to specify the type"
            }
        }

        var rType = vExprs[0].type
        for ((i, vExpr) in vExprs.withIndex()) {
            rType = C_Types.commonType(rType, vExpr.type, exprs[i].startPos) {
                "expr_list_itemtype" toCodeMsg "Wrong list item type"
            }
        }

        if (hintElemType != null) {
            rType = C_Types.commonTypeOpt(rType, hintElemType) ?: rType
        }

        return rType
    }
}

class S_MapLiteralExpr(startPos: S_Pos, val entries: List<Pair<S_Expr, S_Expr>>): S_Expr(startPos) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        val valueEntries = entries.map { (key, value) -> Pair(key.compile(ctx).value(), value.compile(ctx).value()) }
        val mapType = compileType(ctx, hint.typeHint, valueEntries)
        val vExpr = V_MapLiteralExpr(ctx, startPos, valueEntries, mapType)
        return C_ValueExpr(vExpr)
    }

    private fun compileType(ctx: C_ExprContext, typeHint: C_TypeHint, vEntries: List<Pair<V_Expr, V_Expr>>): R_MapType {
        for ((i, vEntry) in vEntries.withIndex()) {
            val keyType = vEntry.first.type
            val valueType = vEntry.second.type
            val keyExpr = entries[i].first
            val valueExpr = entries[i].second
            C_Utils.checkUnitType(keyExpr.startPos, keyType) { "expr_map_key_unit" toCodeMsg "Key expression returns nothing" }
            C_Utils.checkUnitType(valueExpr.startPos, valueType) { "expr_map_value_unit" toCodeMsg "Value expression returns nothing" }
            C_Utils.checkMapKeyType(ctx.defCtx, valueExpr.startPos, keyType)
        }

        val hintKeyValueTypes = getHintKeyValueType(typeHint)
        val rKeyValueTypes = compileKeyValueTypes(vEntries, hintKeyValueTypes)
        return R_MapType(rKeyValueTypes)
    }

    private fun getHintKeyValueType(typeHint: C_TypeHint): R_MapKeyValueTypes? {
        val mGenType = Lib_Rell.MAP_TYPE.mGenericType

        val mHintType = L_TypeUtils.getExpectedType(mGenType.params, mGenType.commonType, typeHint)
        mHintType ?: return null

        val typeArgs = M_TypeUtils.getTypeArgs(mHintType)
        val mKeyType = typeArgs[mGenType.params[0]]?.getExactType()
        val mValueType = typeArgs[mGenType.params[1]]?.getExactType()
        mKeyType ?: return null
        mValueType ?: return null

        val rKeyType = L_TypeUtils.getRType(mKeyType)
        val rValueType = L_TypeUtils.getRType(mValueType)
        rKeyType ?: return null
        rValueType ?: return null

        return R_MapKeyValueTypes(rKeyType, rValueType)
    }

    private fun compileKeyValueTypes(vEntries: List<Pair<V_Expr, V_Expr>>, hintTypes: R_MapKeyValueTypes?): R_MapKeyValueTypes {
        if (vEntries.isEmpty()) {
            return C_Errors.checkNotNull(hintTypes, startPos) {
                "expr_map_notype" toCodeMsg
                "Cannot determine type of the map; use map<K,V>() syntax to specify the type"
            }
        }

        var rTypes = R_MapKeyValueTypes(vEntries[0].first.type, vEntries[0].second.type)

        for ((i, kv) in vEntries.withIndex()) {
            val (vKey, vValue) = kv
            val rKeyType = C_Types.commonType(rTypes.key, vKey.type, entries[i].first.startPos) {
                "expr_map_keytype" toCodeMsg "Wrong map entry key type"
            }
            val rValueType = C_Types.commonType(rTypes.value, vValue.type, entries[i].second.startPos) {
                "expr_map_valuetype" toCodeMsg "Wrong map entry value type"
            }
            rTypes = R_MapKeyValueTypes(rKeyType, rValueType)
        }

        if (hintTypes != null) {
            rTypes = C_Types.commonTypesOpt(rTypes, hintTypes) ?: rTypes
        }

        return rTypes
    }
}

sealed class S_CallArgumentValue {
    abstract fun compile(ctx: C_ExprContext, typeHint: C_TypeHint): C_CallArgumentValue
}

class S_CallArgumentValue_Expr(val expr: S_Expr): S_CallArgumentValue() {
    override fun compile(ctx: C_ExprContext, typeHint: C_TypeHint): C_CallArgumentValue {
        val exprHint = C_ExprHint(typeHint)
        val cExpr = expr.compile(ctx, exprHint)
        val vExpr = cExpr.value()
        return C_CallArgumentValue_Expr(expr.startPos, vExpr)
    }
}

class S_CallArgumentValue_Wildcard(val pos: S_Pos): S_CallArgumentValue() {
    override fun compile(ctx: C_ExprContext, typeHint: C_TypeHint) = C_CallArgumentValue_Wildcard(pos)
}

class S_CallArgument(val name: S_Name?, val value: S_CallArgumentValue) {
    fun compile(
        ctx: C_ExprContext,
        index: Int,
        positional: Boolean,
        typeHints: C_CallTypeHints,
    ): C_CallArgumentHandle {
        val nameHand = name?.compile(ctx)
        val hintIndex = if (positional) index else null
        val typeHint = typeHints.getTypeHint(hintIndex, nameHand?.rName)
        val argValue = value.compile(ctx, typeHint)
        return C_CallArgumentHandle(index, nameHand, argValue)
    }
}

class S_CallExpr(val base: S_Expr, val args: List<S_CallArgument>): S_Expr(base.startPos) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        val cBase = base.compileSafe(ctx, C_ExprHint.DEFAULT_CALLABLE)
        return cBase.call(ctx, base.startPos, args, hint.typeHint)
    }
}

class S_GenericTypeExpr(val type: S_GenericType): S_Expr(type.pos) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        val ref = type.compileGenericType(ctx.defCtx)
        val rType = ref?.def ?: R_CtErrorType
        val ideInfoPtr = ref?.ideInfoPtr ?: C_UniqueDefaultIdeInfoPtr()
        return C_SpecificTypeExpr(type.pos, rType, ideInfoPtr)
    }
}

class S_SpecialTypeExpr(val type: S_Type): S_Expr(type.pos) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        val rType = type.compile(ctx)
        return C_SpecificTypeExpr(type.pos, rType)
    }
}

class S_MirrorStructExpr(pos: S_Pos, val mutable: Boolean, val type: S_Type): S_Expr(pos) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        val structType = type.compileMirrorStructType(ctx.defCtx, mutable)
        structType ?: return C_ExprUtils.errorExpr(ctx, startPos)
        return C_SpecificTypeExpr(startPos, structType)
    }
}
