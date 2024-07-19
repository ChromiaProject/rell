/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.expr

import net.postchain.rell.base.compiler.ast.C_BinOp
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.ast.S_UpdateWhat
import net.postchain.rell.base.compiler.base.core.C_LambdaBlock
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.lib.type.Lib_Type_Entity
import net.postchain.rell.base.lib.type.R_BooleanType
import net.postchain.rell.base.lib.type.R_UnitType
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.model.stmt.*

class C_AssignOp(val pos: S_Pos, val code: String, val rOp: R_BinaryOp, val dbOp: Db_BinaryOp?)

abstract class C_Destination {
    abstract fun type(): R_Type
    open fun effectiveType(): R_Type = type()
    open fun resultType(srcType: R_Type): R_Type = srcType

    abstract fun compileAssignStatement(ctx: C_ExprContext, srcExpr: R_Expr, op: C_AssignOp?): R_Statement

    abstract fun compileAssignExpr(
            ctx: C_ExprContext,
            startPos: S_Pos,
            resType: R_Type,
            srcExpr: R_Expr,
            op: C_AssignOp,
            post: Boolean
    ): R_Expr
}

class C_Destination_Simple(private val rDstExpr: R_DestinationExpr): C_Destination() {
    override fun type() = rDstExpr.type

    override fun compileAssignStatement(ctx: C_ExprContext, srcExpr: R_Expr, op: C_AssignOp?): R_Statement {
        return R_AssignStatement(rDstExpr, srcExpr, op?.rOp)
    }

    override fun compileAssignExpr(
            ctx: C_ExprContext,
            startPos: S_Pos,
            resType: R_Type,
            srcExpr: R_Expr,
            op: C_AssignOp,
            post: Boolean
    ): R_Expr {
        return R_AssignExpr(resType, op.rOp, rDstExpr, srcExpr, post)
    }
}

class C_Destination_EntityAttr(
        private val base: V_Expr,
        private val rEntity: R_EntityDefinition,
        private val path: List<C_EntityAttrRef>,
        private val attr: R_Attribute,
): C_Destination() {
    override fun type() = attr.type
    override fun resultType(srcType: R_Type) = R_UnitType

    override fun compileAssignStatement(ctx: C_ExprContext, srcExpr: R_Expr, op: C_AssignOp?): R_Statement {
        if (op != null && op.dbOp == null) {
            C_BinOp.errTypeMismatch(ctx.msgCtx, op.pos, op.code, attr.type, srcExpr.type)
            return C_ExprUtils.ERROR_STATEMENT
        }

        val metaName = "${rEntity.appLevelName}.${attr.name}="

        val lambdaBlkCtx = ctx.blkCtx.createSubContext("<$metaName:lambda>")
        val lambdaCtx = ctx.copy(blkCtx = lambdaBlkCtx)
        val baseVar = lambdaBlkCtx.newLocalVar("<$metaName:base>", null, base.type, false, null)
        val srcVar = lambdaBlkCtx.newLocalVar("<$metaName:src>", null, srcExpr.type, false, null)

        val cLambdaB = C_LambdaBlock.builder(lambdaCtx, base.type)
        val fromBlkCtx = cLambdaB.innerBlkCtx.createSubContext("<$metaName:from>")
        val rFromBlock = fromBlkCtx.buildBlock().rBlock
        val cLambda = cLambdaB.build()

        val atExprId = ctx.appCtx.nextAtExprId()
        val atEntity = ctx.makeAtEntity(rEntity, atExprId)
        val extraAtEntities = mutableListOf<R_DbAtEntity>()
        val where = makeWhere(ctx, atExprId, atEntity, extraAtEntities, cLambda, rFromBlock)

        val rBaseVarExpr = baseVar.toRef(lambdaBlkCtx.blockUid).toRExpr()
        val rTarget = R_UpdateTarget_Expr_One(atEntity, extraAtEntities, where, rBaseVarExpr, cLambda.rLambda)
        val dbSrcVarExpr = srcVar.toRef(rFromBlock.uid).toDbExpr()
        val rWhat = S_UpdateWhat.makeRWhat(atEntity, attr, dbSrcVarExpr, op?.dbOp)
        val rUpdateStmt = R_UpdateStatement(rTarget, rFromBlock, listOf(rWhat))

        val rBaseExpr = base.toRExpr()
        val lambdaArgs = listOf(
                rBaseExpr to baseVar.toRef(lambdaBlkCtx.blockUid).ptr,
                srcExpr to srcVar.toRef(lambdaBlkCtx.blockUid).ptr
        )

        val rSubBlock = lambdaBlkCtx.buildBlock().rBlock
        return R_LambdaStatement(lambdaArgs, rSubBlock, rUpdateStmt)
    }

    private fun makeWhere(
        ctx: C_ExprContext,
        atExprId: R_AtExprId,
        lastAtEntity: R_DbAtEntity,
        extraAtEntities: MutableList<R_DbAtEntity>,
        cLambda: C_LambdaBlock,
        rFromBlock: R_FrameBlock,
    ): Db_Expr {
        if (path.isEmpty()) {
            val left = Db_EntityExpr(lastAtEntity)
            val right = cLambda.compileVarDbExpr(rFromBlock.uid)
            return C_ExprUtils.makeDbBinaryExprEq(left, right)
        }

        val first = path.first()
        val firstAtEntity = ctx.makeAtEntity(first.rEntity, atExprId)
        extraAtEntities.add(firstAtEntity)

        val left1 = Db_EntityExpr(firstAtEntity)
        val right1 = cLambda.compileVarDbExpr(rFromBlock.uid)
        val where1 = C_ExprUtils.makeDbBinaryExprEq(left1, right1)

        val left2 = Db_EntityExpr(lastAtEntity)
        val right2 = Lib_Type_Entity.pathToDbExpr(ctx, firstAtEntity, path, rEntity.type, base.pos) //TODO base.pos is a bit wrong
        val where2 = C_ExprUtils.makeDbBinaryExprEq(left2, right2)

        return C_ExprUtils.makeDbBinaryExpr(R_BooleanType, R_BinaryOp_And, Db_BinaryOp_And, where1, where2)
    }

    override fun compileAssignExpr(
            ctx: C_ExprContext,
            startPos: S_Pos,
            resType: R_Type,
            srcExpr: R_Expr,
            op: C_AssignOp,
            post: Boolean
    ): R_Expr {
        val rStmt = compileAssignStatement(ctx, srcExpr, op)
        return R_StatementExpr(rStmt)
    }
}

class C_Destination_ObjectAttr(
        private val rObject: R_ObjectDefinition,
        private val attr: R_Attribute
): C_Destination() {
    override fun type() = attr.type
    override fun resultType(srcType: R_Type) = R_UnitType

    override fun compileAssignStatement(ctx: C_ExprContext, srcExpr: R_Expr, op: C_AssignOp?): R_Statement {
        if (op != null && op.dbOp == null) {
            C_BinOp.errTypeMismatch(ctx.msgCtx, op.pos, op.code, attr.type, srcExpr.type)
            return C_ExprUtils.ERROR_STATEMENT
        }

        val metaName = "${rObject.appLevelName}.${attr.name}="

        val lambdaBlkCtx = ctx.blkCtx.createSubContext("<$metaName:lambda>")
        val srcVar = lambdaBlkCtx.newLocalVar("<$metaName:src>", null, srcExpr.type, false, null)

        val fromBlkCtx = lambdaBlkCtx.createSubContext("<$metaName:from>")
        val rFromBlock = fromBlkCtx.buildBlock().rBlock
        val rLambdaBlock = lambdaBlkCtx.buildBlock().rBlock

        val rAtEntity = ctx.makeAtEntity(rObject.rEntity, ctx.appCtx.nextAtExprId())
        val rTarget = R_UpdateTarget_Object(rAtEntity)
        val dbSrcVarExpr = srcVar.toRef(rFromBlock.uid).toDbExpr()
        val rWhat = S_UpdateWhat.makeRWhat(rAtEntity, attr, dbSrcVarExpr, op?.dbOp)
        val rUpdateStmt = R_UpdateStatement(rTarget, rFromBlock, listOf(rWhat))

        val lambdaArgs = listOf(
                srcExpr to srcVar.toRef(rLambdaBlock.uid).ptr
        )

        return R_LambdaStatement(lambdaArgs, rLambdaBlock, rUpdateStmt)
    }

    override fun compileAssignExpr(
            ctx: C_ExprContext,
            startPos: S_Pos,
            resType: R_Type,
            srcExpr: R_Expr,
            op: C_AssignOp,
            post: Boolean
    ): R_Expr {
        val rStmt = compileAssignStatement(ctx, srcExpr, op)
        return R_StatementExpr(rStmt)
    }
}
