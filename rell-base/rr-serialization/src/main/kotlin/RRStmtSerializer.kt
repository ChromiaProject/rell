/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import net.postchain.rell.base.model.rr.RR_IterableAdapterKind
import net.postchain.rell.base.model.rr.RR_Statement
import net.postchain.rell.base.model.rr.RR_VarDeclarator
import rell.ir.*
import rell.ir.Stmt as FbStmt

fun SerializerContext.serializeStmt(stmt: RR_Statement): Int {
    val (unionType, unionOffset) = serializeRRStmtUnion(stmt)
    FbStmt.startStmt(builder)
    FbStmt.addStmtType(builder, unionType)
    FbStmt.addStmt(builder, unionOffset)
    return FbStmt.endStmt(builder)
}

private fun SerializerContext.serializeRRStmtUnion(stmt: RR_Statement): Pair<UByte, Int> = when (stmt) {
    is RR_Statement.Empty -> {
        EmptyStatement.startEmptyStatement(builder)
        StmtUnion.EmptyStatement to EmptyStatement.endEmptyStatement(builder)
    }

    is RR_Statement.Var -> {
        val declarator = serializeRRVarDeclarator(stmt.declarator)
        val expr = stmt.expr?.let { serializeExpr(it) }
        VarStatement.startVarStatement(builder)
        VarStatement.addDeclarator(builder, declarator)
        if (expr != null) VarStatement.addExpr(builder, expr)
        StmtUnion.VarStatement to VarStatement.endVarStatement(builder)
    }

    is RR_Statement.Return -> {
        val expr = stmt.expr?.let { serializeExpr(it) }
        ReturnStatement.startReturnStatement(builder)
        if (expr != null) ReturnStatement.addExpr(builder, expr)
        StmtUnion.ReturnStatement to ReturnStatement.endReturnStatement(builder)
    }

    is RR_Statement.Block -> {
        val stmts = stmt.stmts.map { serializeStmt(it) }.toIntArray()
        val stmtsVec = builder.createVectorOfTables(stmts)
        val fb = serializeFrameBlock(stmt.frameBlock)
        BlockStatement.startBlockStatement(builder)
        BlockStatement.addStmts(builder, stmtsVec)
        BlockStatement.addFrameBlock(builder, fb)
        StmtUnion.BlockStatement to BlockStatement.endBlockStatement(builder)
    }

    is RR_Statement.Expr -> {
        val expr = serializeExpr(stmt.expr)
        ExprStatement.startExprStatement(builder)
        ExprStatement.addExpr(builder, expr)
        StmtUnion.ExprStatement to ExprStatement.endExprStatement(builder)
    }

    is RR_Statement.ReplExpr -> {
        val expr = serializeExpr(stmt.expr)
        ReplExprStatement.startReplExprStatement(builder)
        ReplExprStatement.addExpr(builder, expr)
        StmtUnion.ReplExprStatement to ReplExprStatement.endReplExprStatement(builder)
    }

    is RR_Statement.Assign -> {
        val dst = serializeExpr(stmt.dstExpr)
        val expr = serializeExpr(stmt.expr)
        AssignStatement.startAssignStatement(builder)
        AssignStatement.addDstExpr(builder, dst)
        AssignStatement.addExpr(builder, expr)
        val assignOp = stmt.op
        if (assignOp != null) {
            AssignStatement.addHasOp(builder, true)
            AssignStatement.addOp(builder, serializeRRBinaryOp(assignOp))
        }
        StmtUnion.AssignStatement to AssignStatement.endAssignStatement(builder)
    }

    is RR_Statement.If -> {
        val cond = serializeExpr(stmt.cond)
        val t = serializeStmt(stmt.trueStmt)
        val f = serializeStmt(stmt.falseStmt)
        IfStatement.startIfStatement(builder)
        IfStatement.addCond(builder, cond)
        IfStatement.addTrueStmt(builder, t)
        IfStatement.addFalseStmt(builder, f)
        StmtUnion.IfStatement to IfStatement.endIfStatement(builder)
    }

    is RR_Statement.When -> {
        val chooser = serializeRRWhenChooser(stmt.chooser)
        val stmts = stmt.stmts.map { serializeStmt(it) }.toIntArray()
        val stmtsVec = builder.createVectorOfTables(stmts)
        WhenStatement.startWhenStatement(builder)
        WhenStatement.addChooser(builder, chooser)
        WhenStatement.addStmts(builder, stmtsVec)
        StmtUnion.WhenStatement to WhenStatement.endWhenStatement(builder)
    }

    is RR_Statement.While -> {
        val cond = serializeExpr(stmt.cond)
        val body = serializeStmt(stmt.body)
        val fb = serializeFrameBlock(stmt.frameBlock)
        WhileStatement.startWhileStatement(builder)
        WhileStatement.addCond(builder, cond)
        WhileStatement.addBody(builder, body)
        WhileStatement.addFrameBlock(builder, fb)
        StmtUnion.WhileStatement to WhileStatement.endWhileStatement(builder)
    }

    is RR_Statement.For -> {
        val decl = serializeRRVarDeclarator(stmt.varDeclarator)
        val expr = serializeExpr(stmt.expr)
        val body = serializeStmt(stmt.body)
        val fb = serializeFrameBlock(stmt.frameBlock)
        val adapter = when (stmt.iterableAdapter) {
            RR_IterableAdapterKind.DIRECT -> IterableAdapterKind.DIRECT
            RR_IterableAdapterKind.LEGACY_MAP -> IterableAdapterKind.LEGACY_MAP
        }
        ForStatement.startForStatement(builder)
        ForStatement.addVarDeclarator(builder, decl)
        ForStatement.addExpr(builder, expr)
        ForStatement.addIterableAdapter(builder, adapter)
        ForStatement.addBody(builder, body)
        ForStatement.addFrameBlock(builder, fb)
        StmtUnion.ForStatement to ForStatement.endForStatement(builder)
    }

    is RR_Statement.Break -> {
        BreakStatement.startBreakStatement(builder)
        StmtUnion.BreakStatement to BreakStatement.endBreakStatement(builder)
    }

    is RR_Statement.Continue -> {
        ContinueStatement.startContinueStatement(builder)
        StmtUnion.ContinueStatement to ContinueStatement.endContinueStatement(builder)
    }

    is RR_Statement.Guard -> {
        val body = serializeStmt(stmt.body)
        GuardStatement.startGuardStatement(builder)
        GuardStatement.addBody(builder, body)
        StmtUnion.GuardStatement to GuardStatement.endGuardStatement(builder)
    }

    is RR_Statement.Lambda -> {
        val argExprs = serializeExprList(stmt.argExprs)
        LambdaStatement.startArgPtrsVector(builder, stmt.argPtrs.size)
        for (i in stmt.argPtrs.indices.reversed()) {
            val p = stmt.argPtrs[i]
            VarPtr.createVarPtr(builder, p.blockUid.toUInt(), p.offset)
        }
        val argPtrsVec = builder.endVector()
        val fb = serializeFrameBlock(stmt.block)
        val body = serializeStmt(stmt.body)
        LambdaStatement.startLambdaStatement(builder)
        LambdaStatement.addArgExprs(builder, argExprs)
        LambdaStatement.addArgPtrs(builder, argPtrsVec)
        LambdaStatement.addBlock(builder, fb)
        LambdaStatement.addBody(builder, body)
        StmtUnion.LambdaStatement to LambdaStatement.endLambdaStatement(builder)
    }

    is RR_Statement.Update -> {
        val entity = serializeDbAtEntity(stmt.entity)
        val extraEntities = stmt.extraEntities?.map { serializeDbAtEntity(it) }?.toIntArray()
        val extraEntitiesVec = extraEntities?.let { builder.createVectorOfTables(it) }
        val where = stmt.where?.let { serializeDbExpr(it) }
        val what = stmt.what.map { w ->
            val name = createString(w.attrName)
            val expr = serializeDbExpr(w.expr)
            UpdateStatementWhat.startUpdateStatementWhat(builder)
            UpdateStatementWhat.addAttrName(builder, name)
            UpdateStatementWhat.addAttrIndex(builder, w.attrIndex)
            UpdateStatementWhat.addExpr(builder, expr)
            UpdateStatementWhat.endUpdateStatementWhat(builder)
        }.toIntArray()
        val whatVec = builder.createVectorOfTables(what)
        val fromBlock = serializeFrameBlock(stmt.fromBlock)
        val errPos = serializeErrorPos(stmt.errPos)
        val lambdaBlock = stmt.lambdaBlock?.let { serializeFrameBlock(it) }
        val lambdaExpr = stmt.lambdaExpr?.let { serializeExpr(it) }
        val exprListType = stmt.exprListType?.let { serializeType(it) }
        UpdateStatement.startUpdateStatement(builder)
        UpdateStatement.addEntity(builder, entity)
        if (extraEntitiesVec != null) UpdateStatement.addExtraEntities(builder, extraEntitiesVec)
        if (where != null) UpdateStatement.addWhere(builder, where)
        UpdateStatement.addWhat(builder, whatVec)
        UpdateStatement.addFromBlock(builder, fromBlock)
        UpdateStatement.addErrPos(builder, errPos)
        if (lambdaBlock != null) UpdateStatement.addLambdaBlock(builder, lambdaBlock)
        if (stmt.lambdaVarPtr != null) UpdateStatement.addLambdaVarPtr(
            builder,
            VarPtr.createVarPtr(builder, stmt.lambdaVarPtr!!.blockUid.toUInt(), stmt.lambdaVarPtr!!.offset),
        )
        if (lambdaExpr != null) UpdateStatement.addLambdaExpr(builder, lambdaExpr)
        UpdateStatement.addTargetKind(builder, stmt.targetKind.ordinal.toUByte())
        if (stmt.cardinality != null) {
            UpdateStatement.addHasCardinality(builder, true)
            UpdateStatement.addCardinality(builder, serializeAtCardinality(stmt.cardinality!!))
        }
        UpdateStatement.addIsExprSet(builder, stmt.isExprSet)
        if (exprListType != null) UpdateStatement.addExprListType(builder, exprListType)
        StmtUnion.UpdateStatement to UpdateStatement.endUpdateStatement(builder)
    }

    is RR_Statement.Delete -> {
        val entity = serializeDbAtEntity(stmt.entity)
        val extraEntities = stmt.extraEntities?.map { serializeDbAtEntity(it) }?.toIntArray()
        val extraEntitiesVec = extraEntities?.let { builder.createVectorOfTables(it) }
        val where = stmt.where?.let { serializeDbExpr(it) }
        val fromBlock = serializeFrameBlock(stmt.fromBlock)
        val errPos = serializeErrorPos(stmt.errPos)
        val lambdaBlock = stmt.lambdaBlock?.let { serializeFrameBlock(it) }
        val lambdaExpr = stmt.lambdaExpr?.let { serializeExpr(it) }
        val exprListType = stmt.exprListType?.let { serializeType(it) }
        DeleteStatement.startDeleteStatement(builder)
        DeleteStatement.addEntity(builder, entity)
        if (extraEntitiesVec != null) DeleteStatement.addExtraEntities(builder, extraEntitiesVec)
        if (where != null) DeleteStatement.addWhere(builder, where)
        DeleteStatement.addFromBlock(builder, fromBlock)
        DeleteStatement.addErrPos(builder, errPos)
        if (lambdaBlock != null) DeleteStatement.addLambdaBlock(builder, lambdaBlock)
        if (stmt.lambdaVarPtr != null) DeleteStatement.addLambdaVarPtr(
            builder,
            VarPtr.createVarPtr(builder, stmt.lambdaVarPtr!!.blockUid.toUInt(), stmt.lambdaVarPtr!!.offset),
        )
        if (lambdaExpr != null) DeleteStatement.addLambdaExpr(builder, lambdaExpr)
        DeleteStatement.addTargetKind(builder, stmt.targetKind.ordinal.toUByte())
        if (stmt.cardinality != null) {
            DeleteStatement.addHasCardinality(builder, true)
            DeleteStatement.addCardinality(builder, serializeAtCardinality(stmt.cardinality!!))
        }
        DeleteStatement.addIsExprSet(builder, stmt.isExprSet)
        if (exprListType != null) DeleteStatement.addExprListType(builder, exprListType)
        StmtUnion.DeleteStatement to DeleteStatement.endDeleteStatement(builder)
    }

}

// --- Variable declarator ---

private fun SerializerContext.serializeRRVarDeclarator(decl: RR_VarDeclarator): Int {
    val (unionType, unionOffset) = when (decl) {
        is RR_VarDeclarator.Simple -> {
            val type = serializeType(decl.type)
            val adapter = decl.adapter?.let { serializeRRTypeAdapter(it) }
            SimpleVarDeclarator.startSimpleVarDeclarator(builder)
            SimpleVarDeclarator.addPtr(
                builder,
                VarPtr.createVarPtr(builder, decl.ptr.blockUid.toUInt(), decl.ptr.offset),
            )
            SimpleVarDeclarator.addType(builder, type)
            if (adapter != null) SimpleVarDeclarator.addAdapter(builder, adapter)
            VarDeclaratorUnion.SimpleVarDeclarator to SimpleVarDeclarator.endSimpleVarDeclarator(builder)
        }

        is RR_VarDeclarator.Tuple -> {
            val subs = decl.subDeclarators.map { serializeRRVarDeclarator(it) }.toIntArray()
            val subsVec = builder.createVectorOfTables(subs)
            TupleVarDeclarator.startTupleVarDeclarator(builder)
            TupleVarDeclarator.addSubDeclarators(builder, subsVec)
            VarDeclaratorUnion.TupleVarDeclarator to TupleVarDeclarator.endTupleVarDeclarator(builder)
        }

        is RR_VarDeclarator.Wildcard -> {
            WildcardVarDeclarator.startWildcardVarDeclarator(builder)
            VarDeclaratorUnion.WildcardVarDeclarator to WildcardVarDeclarator.endWildcardVarDeclarator(builder)
        }
    }
    VarDeclarator.startVarDeclarator(builder)
    VarDeclarator.addDeclaratorType(builder, unionType)
    VarDeclarator.addDeclarator(builder, unionOffset)
    return VarDeclarator.endVarDeclarator(builder)
}
