/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_LambdaBlock
import net.postchain.rell.base.compiler.base.core.C_Name
import net.postchain.rell.base.compiler.base.core.C_NameHandle
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.lib.C_LibFuncCaseCtx
import net.postchain.rell.base.compiler.base.lib.C_LibFuncCaseUtils
import net.postchain.rell.base.compiler.base.lib.C_SpecialLibMemberFunctionBody
import net.postchain.rell.base.compiler.base.lib.V_SpecialMemberFunctionCall
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.compiler.vexpr.V_GlobalConstantRestriction
import net.postchain.rell.base.compiler.vexpr.V_TypeValueMember
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.model.R_EntityType
import net.postchain.rell.base.model.R_Struct
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.utils.CommonUtils
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmList

object Lib_Type_Entity {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("entity", abstract = true, hidden = true) {
            supertypeStrategySpecial { mType ->
                val rType = L_TypeUtils.getRType(mType)
                rType is R_EntityType
            }
        }

        namespace("rell") {
            extension("entity_ext", type = "entity") {
                function("to_struct", C_Fn_ToStruct(false))
                function("to_mutable_struct", C_Fn_ToStruct(true))
            }
        }
    }

    fun getValueMembers(type: R_EntityType): List<C_TypeValueMember> {
        return C_EntityAttrRef.getEntityAttrs(type.rEntity).map { C_TypeValueMember_EntityAttr(it) }
    }

    fun pathToDbExpr(ctx: C_ExprContext, atEntity: R_DbAtEntity, path: List<C_EntityAttrRef>, resType: R_Type, linkPos: S_Pos): Db_Expr {
        var dbExpr: Db_Expr = Db_EntityExpr(atEntity)
        for (step in path) {
            val dbTableExpr = EntityUtils.asTableExpr(ctx, dbExpr, step, linkPos)
            dbTableExpr ?: return C_ExprUtils.errorDbExpr(resType)
            dbExpr = step.createDbMemberExpr(ctx, dbTableExpr)
        }
        return dbExpr
    }

    private class C_TypeValueMember_EntityAttr(
        val attr: C_EntityAttrRef,
    ): C_TypeValueMember_Value(attr.ideName, attr.type) {
        override fun kindMsg() = "attribute"
        override fun nameMsg(): C_CodeMsg = attr.attrName.str toCodeMsg attr.attrName.str

        override fun value(ctx: C_ExprContext, linkPos: S_Pos, linkName: C_Name?): V_TypeValueMember {
            return V_TypeValueMember_EntityAttr(ctx, linkPos, linkName, attr, null)
        }
    }

    private class V_TypeValueMember_EntityAttr(
        private val exprCtx: C_ExprContext,
        private val memberPos: S_Pos,
        private val memberName: C_Name?,
        private val attrRef: C_EntityAttrRef,
        private val prev: V_TypeValueMember_EntityAttr?,
    ): V_TypeValueMember(attrRef.type, attrRef.ideName.ideInfo) {
        private val cLambda = EntityUtils.createLambda(exprCtx, attrRef.rEntity)

        override fun implicitAttrName() = if (prev != null) null else memberName
        override fun vExprs() = immListOf<V_Expr>()
        override fun globalConstantRestriction() = V_GlobalConstantRestriction("entity_attr", null)

        override fun calculator(): R_MemberCalculator {
            val members = CommonUtils.chainToList(this) { it.prev }.asReversed()
            val path = members.map { it.attrRef }.toImmList()
            val baseEntity = path.first().rEntity
            val lambda = members.first().cLambda
            return EntityUtils.createCalculator(exprCtx, baseEntity, path, type, memberPos, lambda)
        }

        override fun destination(base: V_Expr): C_Destination {
            if (base.info.dependsOnDbAtEntity) {
                throw C_Errors.errBadDestination(memberPos)
            }
            val attr = attrRef.attribute()
            if (attr == null || !attr.mutable) {
                val simpleName = attrRef.attrName.str
                val fullName = "${attrRef.rEntity.defName.qualifiedName}.$simpleName"
                throw C_Errors.errAttrNotMutable(memberPos, simpleName, fullName)
            }
            exprCtx.checkDbUpdateAllowed(memberPos)

            val members = CommonUtils.chainToList(this) { it.prev }.asReversed()
            val path = members.map { it.attrRef }.dropLast(1).toImmList()

            return C_Destination_EntityAttr(base, attrRef.rEntity, path, attr)
        }

        override fun canBeDbExpr() = true

        override fun dbExpr(base: Db_Expr): Db_Expr {
            val path = CommonUtils.chainToList(this) { it.prev }.map { it.attrRef }.asReversed().toImmList()
            var cur = base
            for (step in path) {
                val dbBaseTable = EntityUtils.asTableExpr(exprCtx, cur, step, memberPos)
                dbBaseTable ?: return C_ExprUtils.errorDbExpr(step.type)
                cur = step.createDbMemberExpr(exprCtx, dbBaseTable)
            }
            return cur
        }

        override fun member(
            ctx: C_ExprContext,
            memberNameHand: C_NameHandle,
            member: C_TypeValueMember,
            safe: Boolean,
            exprHint: C_ExprHint,
        ): V_TypeValueMember? {
            if (member !is C_TypeValueMember_EntityAttr) return null
            memberNameHand.setIdeInfo(member.attr.ideName.ideInfo)
            val memberName = memberNameHand.name
            return V_TypeValueMember_EntityAttr(ctx, memberName.pos, memberName, member.attr, this)
        }
    }

    abstract class C_SysFn_ToStruct_Common: C_SpecialLibMemberFunctionBody() {
        protected abstract fun compile0(ctx: C_ExprContext, selfType: R_Type): V_SpecialMemberFunctionCall?

        final override fun compileCall(
            ctx: C_ExprContext,
            callCtx: C_LibFuncCaseCtx,
            selfType: R_Type,
            args: List<V_Expr>,
        ): V_SpecialMemberFunctionCall? {
            if (args.isNotEmpty()) {
                val errArgs = args.map { null to it.type }
                C_LibFuncCaseUtils.errNoMatch(ctx.msgCtx, callCtx.linkPos, callCtx.qualifiedNameMsg(), errArgs)
            }
            return compile0(ctx, selfType)
        }
    }

    private class C_Fn_ToStruct(private val mutable: Boolean): C_SysFn_ToStruct_Common() {
        override fun compile0(ctx: C_ExprContext, selfType: R_Type): V_SpecialMemberFunctionCall? {
            val entityType = selfType as? R_EntityType
            entityType ?: return null
            val struct = entityType.rEntity.mirrorStructs.getStruct(mutable)
            return V_SpecialMemberFunctionCall_EntityToStruct(ctx, entityType, struct)
        }
    }
}

private object EntityUtils {
    fun createLambda(ctx: C_ExprContext, rEntity: R_EntityDefinition): C_LambdaBlock {
        val cLambdaB = C_LambdaBlock.builder(ctx, rEntity.type)
        return cLambdaB.build()
    }

    fun createCalculator(
        ctx: C_ExprContext,
        rEntity: R_EntityDefinition,
        path: List<C_EntityAttrRef>,
        resType: R_Type,
        linkPos: S_Pos,
        cLambda: C_LambdaBlock,
    ): R_MemberCalculator {
        val atEntity = ctx.makeAtEntity(rEntity, ctx.appCtx.nextAtExprId())

        val dbExpr = Lib_Type_Entity.pathToDbExpr(ctx, atEntity, path, resType, linkPos)
        val valueType = path.last().type
        val whatValue = Db_AtWhatValue_DbExpr(dbExpr, valueType)
        val whatField = Db_AtWhatField(R_AtWhatFieldFlags.DEFAULT, whatValue)

        return createCalculator0(atEntity, whatField, resType, cLambda)
    }

    fun createCalculator0(
        atEntity: R_DbAtEntity,
        whatField: Db_AtWhatField,
        resType: R_Type,
        cLambda: C_LambdaBlock
    ): R_MemberCalculator {
        val whereLeft = Db_EntityExpr(atEntity)
        val whereRight = cLambda.compileVarDbExpr()
        val where = C_ExprUtils.makeDbBinaryExprEq(whereLeft, whereRight)

        val what = listOf(whatField)

        val from = listOf(atEntity)
        val atBase = Db_AtExprBase(from, what, where, isMany = false)
        return R_MemberCalculator_DataAttribute(resType, atBase, cLambda.rLambda)
    }

    fun asTableExpr(ctx: C_ExprContext, dbExpr: Db_Expr, attrRef: C_EntityAttrRef, attrPos: S_Pos): Db_TableExpr? {
        val res = dbExpr as? Db_TableExpr
        if (res == null) {
            ctx.msgCtx.error(attrPos, "expr:entity_attr:no_table:${attrRef.attrName}",
                "Cannot access attribute '${attrRef.attrName}'")
        }
        return res
    }
}

private class V_SpecialMemberFunctionCall_EntityToStruct(
    exprCtx: C_ExprContext,
    private val entityType: R_EntityType,
    private val struct: R_Struct,
): V_SpecialMemberFunctionCall(exprCtx, struct.type) {
    private val structType = struct.type
    private val cLambda = EntityUtils.createLambda(exprCtx, entityType.rEntity)

    override fun globalConstantRestriction() = V_GlobalConstantRestriction("entity_to_struct", null)

    override fun calculator(): R_MemberCalculator {
        val atEntity = exprCtx.makeAtEntity(entityType.rEntity, exprCtx.appCtx.nextAtExprId())
        val cWhatValue = createWhatValue(Db_EntityExpr(atEntity))
        val dbWhatValue = cWhatValue.toDbWhatSub()
        val whatField = Db_AtWhatField(R_AtWhatFieldFlags.DEFAULT, dbWhatValue)
        return EntityUtils.createCalculator0(atEntity, whatField, structType, cLambda)
    }

    override fun dbExprWhat(base: V_Expr, safe: Boolean): C_DbAtWhatValue {
        val dbEntityExpr = base.toDbExpr() as Db_TableExpr
        return createWhatValue(dbEntityExpr)
    }

    private fun createWhatValue(dbEntityExpr: Db_TableExpr): C_DbAtWhatValue {
        val rEntity = entityType.rEntity
        val dbExprs = rEntity.attributes.values.map {
            C_EntityAttrRef.create(rEntity, it).createDbContextAttrExpr(dbEntityExpr)
        }
        val dbWhatValue = Db_AtWhatValue_ToStruct(struct, dbExprs)
        return C_DbAtWhatValue_Other(dbWhatValue)
    }
}
