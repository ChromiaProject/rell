/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.utils.mapToImmList
import rell.ir.*
import rell.ir.Stmt as FbStmt

private fun deserializeUpdateTargetKind(k: UByte): RR_UpdateTargetKind = when (k) {
    UpdateTargetKind.SIMPLE -> RR_UpdateTargetKind.SIMPLE
    UpdateTargetKind.EXPR_ONE -> RR_UpdateTargetKind.EXPR_ONE
    UpdateTargetKind.EXPR_MANY -> RR_UpdateTargetKind.EXPR_MANY
    UpdateTargetKind.OBJECT -> RR_UpdateTargetKind.OBJECT
    else -> error("Unknown update target kind: $k")
}

fun deserializeStmt(fb: FbStmt?): RR_Statement = withDeserializerDepth { deserializeStmtInner(fb) }

private fun deserializeStmtInner(fb: FbStmt?): RR_Statement = when (fb?.stmtType) {
    null -> RR_Statement.Empty
    StmtUnion.EmptyStatement -> RR_Statement.Empty

    StmtUnion.VarStatement -> {
        val s = VarStatement().also { fb.stmt(it) }
        val decl = deserializeVarDeclarator(s.declarator)
        val expr = s.expr?.let { deserializeExpr(it) }
        RR_Statement.Var(decl, expr)
    }

    StmtUnion.ReturnStatement -> {
        val s = ReturnStatement().also { fb.stmt(it) }
        val expr = s.expr?.let { deserializeExpr(it) }
        RR_Statement.Return(expr)
    }

    StmtUnion.BlockStatement -> {
        val s = BlockStatement().also { fb.stmt(it) }
        val stmts = (0 until s.stmtsLength).mapToImmList { deserializeStmt(s.stmts(it)) }
        val frameBlock = deserializeFrameBlock(s.frameBlock)
        RR_Statement.Block(stmts, frameBlock)
    }

    StmtUnion.ExprStatement -> {
        val s = ExprStatement().also { fb.stmt(it) }
        RR_Statement.Expr(deserializeExpr(s.expr))
    }

    StmtUnion.ReplExprStatement -> {
        val s = ReplExprStatement().also { fb.stmt(it) }
        RR_Statement.ReplExpr(deserializeExpr(s.expr))
    }

    StmtUnion.AssignStatement -> {
        val s = AssignStatement().also { fb.stmt(it) }
        val dst = deserializeExpr(s.dstExpr)
        val expr = deserializeExpr(s.expr)
        val op = s.op?.let { deserializeRRBinaryOp(it) }
        RR_Statement.Assign(dst, expr, op)
    }

    StmtUnion.IfStatement -> {
        val s = IfStatement().also { fb.stmt(it) }
        RR_Statement.If(
            cond = deserializeExpr(s.cond),
            trueStmt = deserializeStmt(s.trueStmt),
            falseStmt = deserializeStmt(s.falseStmt),
        )
    }

    StmtUnion.WhenStatement -> {
        val s = WhenStatement().also { fb.stmt(it) }
        val chooser = deserializeWhenChooser(s.chooser)
        val stmts = (0 until s.stmtsLength).mapToImmList { deserializeStmt(s.stmts(it)) }
        RR_Statement.When(chooser, stmts)
    }

    StmtUnion.WhileStatement -> {
        val s = WhileStatement().also { fb.stmt(it) }
        RR_Statement.While(
            cond = deserializeExpr(s.cond),
            body = deserializeStmt(s.body),
            frameBlock = deserializeFrameBlock(s.frameBlock),
        )
    }

    StmtUnion.ForStatement -> {
        val s = ForStatement().also { fb.stmt(it) }
        val adapter = when (s.iterableAdapter) {
            IterableAdapterKind.LEGACY_MAP -> RR_IterableAdapterKind.LEGACY_MAP
            else -> RR_IterableAdapterKind.DIRECT
        }
        RR_Statement.For(
            varDeclarator = deserializeVarDeclarator(s.varDeclarator),
            expr = deserializeExpr(s.expr),
            iterableAdapter = adapter,
            body = deserializeStmt(s.body),
            frameBlock = deserializeFrameBlock(s.frameBlock),
        )
    }

    StmtUnion.BreakStatement -> RR_Statement.Break
    StmtUnion.ContinueStatement -> RR_Statement.Continue

    StmtUnion.GuardStatement -> {
        val s = GuardStatement().also { fb.stmt(it) }
        RR_Statement.Guard(deserializeStmt(s.body))
    }

    StmtUnion.LambdaStatement -> {
        val s = LambdaStatement().also { fb.stmt(it) }
        val argExprs = (0 until s.argExprsLength).mapToImmList { deserializeExpr(s.argExprs(it)) }
        val argPtrs = (0 until s.argPtrsLength).mapToImmList { i ->
            val p = s.argPtrs(i)
            RR_VarPtr(p.blockUid.toLong(), p.offset)
        }
        val block = deserializeFrameBlock(s.block)
        val body = deserializeStmt(s.body)
        RR_Statement.Lambda(argExprs, argPtrs, block, body)
    }

    StmtUnion.UpdateStatement -> {
        val s = UpdateStatement().also { fb.stmt(it) }
        val entity = deserializeDbAtEntity(s.entity)
        val extraEntities = if (s.extraEntitiesLength > 0) {
            (0 until s.extraEntitiesLength).mapToImmList { deserializeDbAtEntity(s.extraEntities(it)) }
        } else null
        val where = s.where?.let { deserializeDbExpr(it) }
        val what = (0 until s.whatLength).mapToImmList { i ->
            val w = s.what(i)
            RR_UpdateWhat(w.attrName, w.attrIndex, deserializeDbExpr(w.expr))
        }
        val lambdaVarPtr = s.lambdaVarPtr?.let { RR_VarPtr(it.blockUid.toLong(), it.offset) }
        val targetKind = deserializeUpdateTargetKind(s.targetKind)
        val cardinality = s.cardinality?.let { deserializeAtCardinality(it) }
        RR_Statement.Update(
            entity = entity,
            extraEntities = extraEntities,
            where = where,
            what = what,
            fromBlock = deserializeFrameBlock(s.fromBlock),
            errPos = deserializeErrorPos(s.errPos),
            lambdaBlock = s.lambdaBlock?.let { deserializeFrameBlock(it) },
            lambdaVarPtr = lambdaVarPtr,
            lambdaExpr = s.lambdaExpr?.let { deserializeExpr(it) },
            targetKind = targetKind,
            cardinality = cardinality,
            isExprSet = s.isExprSet,
            exprListType = s.exprListType?.let { deserializeType(it) },
        )
    }

    StmtUnion.DeleteStatement -> {
        val s = DeleteStatement().also { fb.stmt(it) }
        val entity = deserializeDbAtEntity(s.entity)
        val extraEntities = if (s.extraEntitiesLength > 0) {
            (0 until s.extraEntitiesLength).mapToImmList { deserializeDbAtEntity(s.extraEntities(it)) }
        } else null
        val where = s.where?.let { deserializeDbExpr(it) }
        val lambdaVarPtr = s.lambdaVarPtr?.let { RR_VarPtr(it.blockUid.toLong(), it.offset) }
        val targetKind = deserializeUpdateTargetKind(s.targetKind)
        val cardinality = s.cardinality?.let { deserializeAtCardinality(it) }
        RR_Statement.Delete(
            entity = entity,
            extraEntities = extraEntities,
            where = where,
            fromBlock = deserializeFrameBlock(s.fromBlock),
            errPos = deserializeErrorPos(s.errPos),
            lambdaBlock = s.lambdaBlock?.let { deserializeFrameBlock(it) },
            lambdaVarPtr = lambdaVarPtr,
            lambdaExpr = s.lambdaExpr?.let { deserializeExpr(it) },
            targetKind = targetKind,
            cardinality = cardinality,
            isExprSet = s.isExprSet,
            exprListType = s.exprListType?.let { deserializeType(it) },
        )
    }

    else -> RR_Statement.Empty
}

private fun deserializeVarDeclarator(fb: VarDeclarator?): RR_VarDeclarator = when (fb?.declaratorType) {
    null -> RR_VarDeclarator.Wildcard
    VarDeclaratorUnion.SimpleVarDeclarator -> {
        val s = SimpleVarDeclarator().also { fb.declarator(it) }
        val ptr = s.ptr?.let { RR_VarPtr(it.blockUid.toLong(), it.offset) } ?: RR_VarPtr(0, 0)
        val adapter = s.adapter?.let { deserializeTypeAdapter(it) }
        RR_VarDeclarator.Simple(ptr, deserializeType(s.type), adapter)
    }

    VarDeclaratorUnion.TupleVarDeclarator -> {
        val s = TupleVarDeclarator().also { fb.declarator(it) }
        val subs = (0 until s.subDeclaratorsLength).mapToImmList { deserializeVarDeclarator(s.subDeclarators(it)) }
        RR_VarDeclarator.Tuple(subs)
    }

    VarDeclaratorUnion.WildcardVarDeclarator -> RR_VarDeclarator.Wildcard
    else -> RR_VarDeclarator.Wildcard
}

internal fun deserializeTypeAdapter(fb: TypeAdapter?): RR_TypeAdapter = when (fb?.kind) {
    null -> RR_TypeAdapter.Direct
    TypeAdapterKind.DIRECT -> RR_TypeAdapter.Direct
    TypeAdapterKind.INTEGER_TO_BIG_INTEGER -> RR_TypeAdapter.IntegerToBigInteger
    TypeAdapterKind.INTEGER_TO_DECIMAL -> RR_TypeAdapter.IntegerToDecimal
    TypeAdapterKind.BIG_INTEGER_TO_DECIMAL -> RR_TypeAdapter.BigIntegerToDecimal
    TypeAdapterKind.NULLABLE -> RR_TypeAdapter.Nullable(deserializeTypeAdapter(fb.inner))
    else -> RR_TypeAdapter.Direct
}
