/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.core

import net.postchain.rell.base.compiler.ast.S_Comment
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.ast.S_Statement
import net.postchain.rell.base.compiler.base.def.C_AttrHeader
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.utils.C_LateInit
import net.postchain.rell.base.compiler.base.utils.C_Utils
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.R_MapType
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.R_CtErrorType
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.model.R_TupleType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.stmt.*
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.mtype.M_TypeParamsResolver
import net.postchain.rell.base.mtype.M_Types
import net.postchain.rell.base.utils.doc.DocDeclaration_Variable
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.doc.DocSymbolKind
import net.postchain.rell.base.utils.doc.DocSymbolName
import net.postchain.rell.base.utils.foldSimple
import net.postchain.rell.base.utils.mapView
import net.postchain.rell.base.utils.toImmList

class C_Statement(
    val rStmt: R_Statement,
    val alwaysReturns: Boolean,
    val varStatesDelta: C_VarStatesDelta = C_VarStatesDelta.EMPTY,
    val guardBlock: Boolean = false,
) {
    fun copy(
        rStmt: R_Statement = this.rStmt,
        alwaysReturns: Boolean = this.alwaysReturns,
        varStatesDelta: C_VarStatesDelta = this.varStatesDelta,
        guardBlock: Boolean = this.guardBlock,
    ): C_Statement {
        return if (
            rStmt === this.rStmt
            && alwaysReturns == this.alwaysReturns
            && varStatesDelta === this.varStatesDelta
            && guardBlock == this.guardBlock
        ) this else C_Statement(
            rStmt = rStmt,
            alwaysReturns = alwaysReturns,
            varStatesDelta = varStatesDelta,
            guardBlock = guardBlock,
        )
    }

    companion object {
        val EMPTY = C_Statement(R_EmptyStatement, false)
        val ERROR = C_Statement(C_ExprUtils.ERROR_STATEMENT, false)

        fun empty(varStatesDelta: C_VarStatesDelta): C_Statement {
            return if (varStatesDelta.isEmpty()) EMPTY else C_Statement(R_EmptyStatement, false, varStatesDelta)
        }

        fun varStatesDeltaForBranches(stmts: List<C_Statement>): C_VarStatesDelta {
            val noRetStmts = stmts.filter { !it.alwaysReturns }
            if (noRetStmts.isEmpty()) {
                return C_VarStatesDelta.EMPTY
            }

            val deltas = noRetStmts.mapView { it.varStatesDelta }
            return deltas.foldSimple(C_VarStatesDelta::or)
        }
    }
}

class C_BlockCode(
    rStmts: List<R_Statement>,
    val alwaysReturns: Boolean,
    val guardBlock: Boolean,
    val varStatesDelta: C_VarStatesDelta,
) {
    val rStmts = rStmts.toImmList()

    fun createProto(): C_BlockCodeProto {
        return C_BlockCodeProto(varStatesDelta)
    }
}

class C_BlockCodeProto(val varStatesDelta: C_VarStatesDelta) {
    companion object { val EMPTY = C_BlockCodeProto(C_VarStatesDelta.EMPTY) }
}

class C_BlockCodeBuilder(
    private val ctx: C_StmtContext,
    private val repl: Boolean,
    hasGuardBlock: Boolean,
    proto: C_BlockCodeProto,
) {
    private val rStmts = mutableListOf<R_Statement>()
    private var alwaysReturns = false
    private var deadCode = false
    private var insideGuardBlock = hasGuardBlock
    private var afterGuardBlock = false
    private var varStatesDelta = proto.varStatesDelta
    private var build = false

    fun add(stmt: S_Statement) {
        check(!build)

        val subExprCtx = ctx.exprCtx
            .updateVarStates(varStatesDelta)
            .copy(insideGuardBlock = insideGuardBlock)
        val subCtx = ctx.copy(
            exprCtx = subExprCtx,
            afterGuardBlock = afterGuardBlock,
        )
        val cStmt = stmt.compile(subCtx, repl)

        if (alwaysReturns && !deadCode) {
            ctx.msgCtx.error(stmt.pos, "stmt_deadcode", "Dead code")
            deadCode = true
        }

        rStmts.add(cStmt.rStmt)

        if (cStmt.guardBlock) {
            insideGuardBlock = false
            afterGuardBlock = true
        }

        alwaysReturns = alwaysReturns || cStmt.alwaysReturns
        varStatesDelta = varStatesDelta.and(cStmt.varStatesDelta)
    }

    fun build(): C_BlockCode {
        check(!build)
        build = true
        return C_BlockCode(rStmts, alwaysReturns, afterGuardBlock, varStatesDelta)
    }
}

sealed class C_VarDeclarator(
    protected val ctx: C_StmtContext,
    protected val mutable: Boolean,
) {
    abstract fun getHintType(): M_Type
    abstract fun compile(rExprType: R_Type?): Result

    class Result(val rDeclarator: R_VarDeclarator, val varStatesDelta: C_VarStatesDelta)
}

class C_WildcardVarDeclarator(
    ctx: C_StmtContext,
    mutable: Boolean,
): C_VarDeclarator(ctx, mutable) {
    override fun getHintType() = M_Types.NOTHING

    override fun compile(rExprType: R_Type?): Result {
        return Result(R_WildcardVarDeclarator, C_VarStatesDelta.EMPTY)
    }
}

class C_SimpleVarDeclarator(
    ctx: C_StmtContext,
    mutable: Boolean,
    private val attrHeader: C_AttrHeader,
    private val name: C_Name,
    private val explicitType: R_Type?,
    private val comment: S_Comment?,
    private val ideInfo: C_IdeSymbolInfo,
    private val docSymbolLate: C_LateInit<DocSymbol?>,
): C_VarDeclarator(ctx, mutable) {
    override fun getHintType() = explicitType?.mType ?: M_Types.NOTHING

    override fun compile(rExprType: R_Type?): Result {
        val rType = explicitType ?: (if (rExprType == null) attrHeader.type else null)

        if (rType == null && rExprType == null) {
            ctx.msgCtx.error(name.pos, "stmt_var_notypeexpr:$name", "Neither type nor expression specified for '$name'")
        } else if (rExprType != null) {
            C_Utils.checkUnitType(name.pos, rExprType) {
                "stmt_var_unit:$name" toCodeMsg "Expression for '$name' returns nothing"
            }
        }

        val typeAdapter = if (rExprType != null && rType != null) {
            C_Types.adaptSafe(ctx.msgCtx, rType, rExprType, name.pos) {
                "stmt_var_type:$name" toCodeMsg "Type mismatch for '$name'"
            }
        } else {
            C_TypeAdapter_Direct
        }

        val rVarType = rType ?: rExprType ?: R_CtErrorType
        val cVarRef = ctx.blkCtx.addLocalVar(name, rVarType, mutable, null, ideInfo)

        val docSymbol = makeDocSymbol(rVarType)
        docSymbolLate.set(docSymbol, allowEarly = true)

        val rTypeAdapter = typeAdapter.toRAdapter()
        val varStates = calcVarState(cVarRef.target.uid, rExprType, rVarType)
        return Result(R_SimpleVarDeclarator(cVarRef.ptr, rVarType, rTypeAdapter), varStates)
    }

    private fun calcVarState(varId: C_VarId, rExprType: R_Type?, rVarType: R_Type): C_VarStatesDelta {
        return if (rExprType == null) C_VarStatesDelta.EMPTY else {
            val varKey = C_VarStateKey(varId)
            val nulled = C_VarNulled.forVarType(rVarType, rExprType)
            C_VarStatesDelta.changed(varKey, C_VarChanged.YES, nulled)
        }
    }

    private fun makeDocSymbol(rType: R_Type): DocSymbol {
        val docType = L_TypeUtils.docType(rType.mType)
        return ctx.symCtx.makeDocSymbol(
            DocSymbolKind.VAR,
            DocSymbolName.local(name.str),
            DocDeclaration_Variable(name.rName, docType, mutable),
            comment = comment,
        )
    }
}

class C_TupleVarDeclarator(
    ctx: C_StmtContext,
    mutable: Boolean,
    private val pos: S_Pos,
    subDeclarators: List<C_VarDeclarator>,
): C_VarDeclarator(ctx, mutable) {
    private val subDeclarators = subDeclarators.toImmList()
    private val hintType = M_Types.tuple(subDeclarators.map { it.getHintType() })

    override fun getHintType() = hintType

    override fun compile(rExprType: R_Type?): Result {
        val subResults = compileSub(rExprType)
        val rSubDeclarators = subResults.map { it.rDeclarator }
        val varStatesDelta = subResults.fold(C_VarStatesDelta.EMPTY) { d, res -> d.and(res.varStatesDelta) }
        return Result(R_TupleVarDeclarator(rSubDeclarators), varStatesDelta)
    }

    private fun compileSub(rExprType: R_Type?): List<Result> {
        if (rExprType == null) {
            return subDeclarators.map { it.compile(null) }
        }

        val fieldTypes = if (rExprType is R_TupleType) {
            val n1 = subDeclarators.size
            val n2 = rExprType.fields.size
            if (n1 != n2) {
                ctx.msgCtx.error(pos, "var_tuple_wrongsize:$n1:$n2:${rExprType.strCode()}",
                        "Expression returns a tuple of $n2 element(s) instead of $n1 element(s): ${rExprType.str()}")
            }
            subDeclarators.indices.map {
                if (it < n2) rExprType.fields[it].type else R_CtErrorType
            }
        } else {
            if (rExprType.isNotError()) {
                ctx.msgCtx.error(pos, "var_notuple:${rExprType.strCode()}",
                        "Expression must return a tuple, but it returns '${rExprType.str()}'")
            }
            subDeclarators.map { R_CtErrorType }
        }

        return subDeclarators.withIndex().map { (i, subDeclarator) ->
            subDeclarator.compile(fieldTypes[i])
        }
    }
}

class C_IterableAdapter(val itemType: R_Type, val rAdapter: R_IterableAdapter) {
    companion object {
        fun compile(ctx: C_ExprContext, exprType: R_Type, atExpr: Boolean): C_IterableAdapter? {
            return if (exprType.isError()) {
                C_IterableAdapter(exprType, R_IterableAdapter_Direct)
            } else if (exprType is R_MapType && isMapCompatibilityMode(ctx, atExpr)) {
                C_IterableAdapter(exprType.legacyEntryType, R_IterableAdapter_LegacyMap)
            } else {
                val rItemType = getItemType(exprType)
                if (rItemType == null) null else C_IterableAdapter(rItemType, R_IterableAdapter_Direct)
            }
        }

        private fun getItemType(exprType: R_Type): R_Type? {
            val genType = Lib_Rell.ITERABLE_TYPE.mGenericType
            val map = M_TypeParamsResolver.resolveTypeParams(genType.params, genType.commonType, exprType.mType)
            map ?: return null

            val mItemType = map.values.singleOrNull()
            mItemType ?: return null

            val rItemType = L_TypeUtils.getRType(mItemType)
            return rItemType
        }

        private val LANG_VER_UNNAMED_MAP_FIELDS = R_LangVersion.of("0.10.6")

        private fun isMapCompatibilityMode(ctx: C_ExprContext, atExpr: Boolean): Boolean {
            val opts = ctx.globalCtx.compilerOptions
            return atExpr && opts.compatibility != null && opts.compatibility < LANG_VER_UNNAMED_MAP_FIELDS
        }
    }
}
