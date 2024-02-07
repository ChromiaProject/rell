/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.core

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
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.stmt.*
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.mtype.M_TypeParamsResolver
import net.postchain.rell.base.mtype.M_Types
import net.postchain.rell.base.utils.Nullable
import net.postchain.rell.base.utils.doc.DocDeclaration_Variable
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.doc.DocSymbolKind
import net.postchain.rell.base.utils.doc.DocSymbolName
import net.postchain.rell.base.utils.toImmList

class C_Statement(
        val rStmt: R_Statement,
        val returnAlways: Boolean,
        val varFacts: C_VarFacts = C_VarFacts.EMPTY,
        val guardBlock: Boolean = false
) {
    fun update(
            rStmt: R_Statement? = null,
            returnAlways: Boolean? = null,
            varFacts: C_VarFacts? = null,
            guardBlock: Boolean? = null
    ): C_Statement {
        val rStmt2 = rStmt ?: this.rStmt
        val returnAlways2 = returnAlways ?: this.returnAlways
        val varFacts2 = varFacts ?: this.varFacts
        val guardBlock2 = guardBlock ?: this.guardBlock
        return if (rStmt2 === this.rStmt
                && returnAlways2 == this.returnAlways
                && varFacts2 === this.varFacts
                && guardBlock2 == this.guardBlock) this
                else C_Statement(rStmt = rStmt2, returnAlways = returnAlways2, varFacts = varFacts2, guardBlock = guardBlock2)
    }

    companion object {
        val EMPTY = C_Statement(R_EmptyStatement, false)
        val ERROR = C_Statement(C_ExprUtils.ERROR_STATEMENT, false)

        fun empty(varFacts: C_VarFacts): C_Statement {
            return if (varFacts.isEmpty()) EMPTY else C_Statement(R_EmptyStatement, false, varFacts)
        }

        fun calcBranchedVarFacts(ctx: C_StmtContext, stmts: List<C_Statement>): C_VarFacts {
            val noRetStmts = stmts.filter { !it.returnAlways }
            val cases = noRetStmts.map { it.varFacts }
            return C_VarFacts.forBranches(ctx.exprCtx, cases)
        }
    }
}

class C_BlockCode(
    rStmts: List<R_Statement>,
    val returnAlways: Boolean,
    val guardBlock: Boolean,
    val deltaVarFacts: C_VarFacts,
    val factsCtx: C_VarFactsContext,
) {
    val rStmts = rStmts.toImmList()

    fun createProto(): C_BlockCodeProto {
        val varFacts = factsCtx.toVarFacts()
        return C_BlockCodeProto(varFacts)
    }
}

class C_BlockCodeProto(val varFacts: C_VarFacts) {
    companion object { val EMPTY = C_BlockCodeProto(C_VarFacts.EMPTY) }
}

class C_BlockCodeBuilder(
        ctx: C_StmtContext,
        private val repl: Boolean,
        hasGuardBlock: Boolean,
        proto: C_BlockCodeProto
) {
    private val ctx = ctx.updateFacts(proto.varFacts)
    private val rStmts = mutableListOf<R_Statement>()
    private var returnAlways = false
    private var deadCode = false
    private var insideGuardBlock = hasGuardBlock
    private var afterGuardBlock = false
    private val blkVarFacts = C_BlockVarFacts(this.ctx.exprCtx.factsCtx)
    private var build = false

    fun add(stmt: S_Statement) {
        check(!build)

        val subExprCtx = ctx.exprCtx.update(factsCtx = blkVarFacts.subContext(), insideGuardBlock = insideGuardBlock)

        val subCtx = ctx.update(
                exprCtx = subExprCtx,
                afterGuardBlock = afterGuardBlock
        )
        val cStmt = stmt.compile(subCtx, repl)

        if (returnAlways && !deadCode) {
            ctx.msgCtx.error(stmt.pos, "stmt_deadcode", "Dead code")
            deadCode = true
        }

        rStmts.add(cStmt.rStmt)

        if (cStmt.guardBlock) {
            insideGuardBlock = false
            afterGuardBlock = true
        }

        returnAlways = returnAlways || cStmt.returnAlways
        blkVarFacts.putFacts(cStmt.varFacts)
    }

    fun build(): C_BlockCode {
        check(!build)
        build = true
        val deltaVarFacts = blkVarFacts.copyFacts()
        val factsCtx = ctx.exprCtx.factsCtx.sub(deltaVarFacts)
        return C_BlockCode(rStmts, returnAlways, afterGuardBlock, deltaVarFacts, factsCtx)
    }
}

sealed class C_VarDeclarator(protected val ctx: C_StmtContext, protected val mutable: Boolean) {
    abstract fun getHintType(): M_Type
    abstract fun compile(rExprType: R_Type?, varFacts: C_MutableVarFacts): R_VarDeclarator
}

class C_WildcardVarDeclarator(
        ctx: C_StmtContext,
        mutable: Boolean,
): C_VarDeclarator(ctx, mutable) {
    override fun getHintType() = M_Types.NOTHING

    override fun compile(rExprType: R_Type?, varFacts: C_MutableVarFacts): R_VarDeclarator {
        return R_WildcardVarDeclarator
    }
}

class C_SimpleVarDeclarator(
    ctx: C_StmtContext,
    mutable: Boolean,
    private val attrHeader: C_AttrHeader,
    private val name: C_Name,
    private val explicitType: R_Type?,
    private val ideInfo: C_IdeSymbolInfo,
    private val docSymbolLate: C_LateInit<Nullable<DocSymbol>>,
): C_VarDeclarator(ctx, mutable) {
    override fun getHintType() = explicitType?.mType ?: M_Types.NOTHING

    override fun compile(rExprType: R_Type?, varFacts: C_MutableVarFacts): R_VarDeclarator {
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
        docSymbolLate.set(Nullable.of(docSymbol), allowEarly = true)

        varFacts.putFacts(calcVarFacts(rExprType, rVarType, cVarRef.target.uid))

        val rTypeAdapter = typeAdapter.toRAdapter()
        return R_SimpleVarDeclarator(cVarRef.ptr, rVarType, rTypeAdapter)
    }

    private fun calcVarFacts(rExprType: R_Type?, rVarType: R_Type, varUid: C_VarUid): C_VarFacts {
        return if (rExprType != null) {
            val inited = mapOf(varUid to C_VarFact.YES)
            val nulled = C_VarFacts.varTypeToNulled(varUid, rVarType, rExprType)
            C_VarFacts.of(inited = inited, nulled = nulled)
        } else {
            val inited = mapOf(varUid to C_VarFact.NO)
            C_VarFacts.of(inited = inited)
        }
    }

    private fun makeDocSymbol(rType: R_Type): DocSymbol {
        val docType = L_TypeUtils.docType(rType.mType)
        return ctx.globalCtx.docFactory.makeDocSymbol(
            DocSymbolKind.VAR,
            DocSymbolName.local(name.str),
            DocDeclaration_Variable(name.rName, docType, mutable),
        )
    }
}

class C_TupleVarDeclarator(
        ctx: C_StmtContext,
        mutable: Boolean,
        private val pos: S_Pos,
        subDeclarators: List<C_VarDeclarator>
): C_VarDeclarator(ctx, mutable) {
    private val subDeclarators = subDeclarators.toImmList()
    private val hintType = M_Types.tuple(subDeclarators.map { it.getHintType() })

    override fun getHintType() = hintType

    override fun compile(rExprType: R_Type?, varFacts: C_MutableVarFacts): R_VarDeclarator {
        val rSubDeclarators = compileSub(rExprType, varFacts)
        return R_TupleVarDeclarator(rSubDeclarators)
    }

    private fun compileSub(rExprType: R_Type?, varFacts: C_MutableVarFacts): List<R_VarDeclarator> {
        if (rExprType == null) {
            return subDeclarators.map { it.compile(null, varFacts) }
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
            subDeclarator.compile(fieldTypes[i], varFacts)
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
