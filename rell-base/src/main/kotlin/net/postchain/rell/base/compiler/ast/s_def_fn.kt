/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.def.C_GlobalAttrHeaderIdeData
import net.postchain.rell.base.compiler.base.expr.C_ExprHint
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.expr.C_StmtContext
import net.postchain.rell.base.compiler.base.fn.C_FormalParameter
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.lib.type.R_UnitType
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.stmt.R_ExprStatement
import net.postchain.rell.base.model.stmt.R_ReturnStatement
import net.postchain.rell.base.mtype.M_ParamArity
import net.postchain.rell.base.utils.MutableTypedKeyMap
import net.postchain.rell.base.utils.ImmTypedKeyMap
import net.postchain.rell.base.utils.doc.*
import net.postchain.rell.base.utils.ide.IdeSymbolCategory
import net.postchain.rell.base.utils.ide.IdeSymbolKind

class S_FormalParameter(
    private val attr: S_AttrHeader,
    private val expr: S_Expr?,
    private val comment: S_Comment?,
) {
    fun compile(
        defCtx: C_DefinitionContext,
        index: Int,
        docCommentsGetter: C_LateGetter<DocFunctionParamComments>,
    ): C_FormalParameter {
        val docSymLate = C_LateInit<DocSymbol?>(C_CompilerPass.EXPRESSIONS, null)

        val ideData = C_GlobalAttrHeaderIdeData(
            IdeSymbolCategory.PARAMETER,
            IdeSymbolKind.LOC_PARAMETER,
            null,
            docSymLate.getter,
        )

        val attrHeader = attr.compile(defCtx, false, ideData)
        val name = attrHeader.name
        val type = attrHeader.type ?: R_CtErrorType

        val docType = L_TypeUtils.docType(type.mType)
        val docParam = DocFunctionParam(name.str, docType, arity = M_ParamArity.ONE, exact = false, nullable = false)

        val docDecGetter: C_LateGetter<DocDeclaration>
        val defaultValue: C_ParameterDefaultValue?

        if (expr == null) {
            val docDec = makeDocDeclaration(docParam, null)

            defCtx.executor.onPass(C_CompilerPass.EXPRESSIONS) {
                val docComments = docCommentsGetter.get()
                val docSym = makeDocSymbol(defCtx, name, docDec, docComments)
                docSymLate.set(docSym)
            }

            defaultValue = null
            docDecGetter = C_LateGetter.const(docDec)
        } else {
            val rErrorExpr = C_ExprUtils.errorRExpr(type)
            val rExprLate = C_LateInit(C_CompilerPass.EXPRESSIONS, rErrorExpr)
            val rValueLate = C_LateInit(C_CompilerPass.EXPRESSIONS, R_DefaultValue(rErrorExpr, false))

            //TODO don't create fallback doc every time
            val fallbackDoc = makeDocDeclaration(docParam, C_ExprUtils.errorVExpr(defCtx.initExprCtx, expr.startPos))
            val docDecLate = C_LateInit(C_CompilerPass.EXPRESSIONS, fallbackDoc)
            docDecGetter = docDecLate.getter

            defCtx.executor.onPass(C_CompilerPass.EXPRESSIONS) {
                val vExpr = compileExpr(defCtx, name.rName, type)
                val rExpr = vExpr.toRExpr()

                val docDec = makeDocDeclaration(docParam, vExpr)
                val docComments = docCommentsGetter.get()
                val docSym = makeDocSymbol(defCtx, name, docDec, docComments)

                rExprLate.set(rExpr)
                rValueLate.set(R_DefaultValue(rExpr, vExpr.info.hasDbModifications))
                docDecLate.set(docDec)
                docSymLate.set(docSym)
            }

            defaultValue = C_ParameterDefaultValue(
                expr.startPos,
                name.rName,
                rExprLate.getter,
                defCtx.initFrameGetter,
                rValueLate.getter,
            )
        }

        return C_FormalParameter(
            name,
            type,
            attrHeader.ideInfo,
            docParam,
            comment,
            index,
            defaultValue,
            defCtx.initFrameGetter,
            docSymLate.getter,
            docDecGetter,
        )
    }

    private fun compileExpr(defCtx: C_DefinitionContext, paramName: R_Name, paramType: R_Type): V_Expr {
        val exprCtx = defCtx.initExprCtx
        val cExpr = expr!!.compileOpt(exprCtx, C_ExprHint.ofType(paramType))
        cExpr ?: return C_ExprUtils.errorVExpr(exprCtx, expr.startPos, paramType)

        val vExpr = cExpr.vExpr()

        return if (paramType.isError()) vExpr else {
            val valueType = vExpr.type
            val adapter = paramType.getTypeAdapter(valueType)
            if (adapter != null) adapter.adaptExpr(exprCtx, vExpr) else {
                val code = "def:param:type:$paramName:${paramType.strCode()}:${valueType.strCode()}"
                val msg = "Wrong type of default value of parameter '$paramName': ${valueType.str()} instead of ${paramType.str()}"
                defCtx.msgCtx.error(expr.startPos, code, msg)
                vExpr
            }
        }
    }

    private fun makeDocDeclaration(docParam: DocFunctionParam, expr: V_Expr?): DocDeclaration {
        val docExpr = if (expr == null) null else C_DocUtils.docExpr(expr)
        return DocDeclaration_Parameter(docParam, isLazy = false, implies = null, expr = docExpr)
    }

    private fun makeDocSymbol(
        defCtx: C_DefinitionContext,
        name: C_Name,
        declaration: DocDeclaration,
        docComments: DocFunctionParamComments,
    ): DocSymbol {
        val docComment = docComments.paramComments[name.rName]
        return defCtx.symCtx.docSymbolFactory.makeDocSymbol(
            kind = DocSymbolKind.PARAMETER,
            symbolName = DocSymbolName.local(name.str),
            declaration = declaration,
            comment = docComment,
        )
    }
}

abstract class S_FunctionBody {
    protected abstract fun processStatementVars(): ImmTypedKeyMap
    protected abstract fun compileQuery0(bodyCtx: C_FunctionBodyContext, stmtCtx: C_StmtContext): C_Statement
    protected abstract fun compileFunction0(bodyCtx: C_FunctionBodyContext, stmtCtx: C_StmtContext): C_Statement

    abstract fun returnsValue(): Boolean

    fun compileQuery(ctx: C_FunctionBodyContext): R_QueryBody {
        val statementVars = processStatementVars()
        val fnCtx = C_FunctionContext(ctx.defCtx, ctx.defName.appLevelName, ctx.explicitRetType, statementVars)
        val frameCtx = C_FrameContext.create(fnCtx)
        val actParams = ctx.formalParams.compile(frameCtx)

        fnCtx.executor.onPass(C_CompilerPass.VALIDATION) {
            ctx.ideCompsLate.set(frameCtx.ideCompCtx.finish())
        }

        val cBody = compileQuery0(ctx, actParams.stmtCtx)
        val callFrame = frameCtx.makeCallFrame(cBody.guardBlock)
        val rRetType = fnCtx.actualReturnType()

        return R_UserQueryBody(rRetType, actParams.rParams, actParams.rParamVars, cBody.rStmt, callFrame.rFrame)
    }

    fun compileFunction(ctx: C_FunctionBodyContext): R_FunctionBody {
        val statementVars = processStatementVars()
        val fnCtx = C_FunctionContext(ctx.defCtx, ctx.defName.appLevelName, ctx.explicitRetType, statementVars)
        val frameCtx = C_FrameContext.create(fnCtx)
        val actParams = ctx.formalParams.compile(frameCtx)

        fnCtx.executor.onPass(C_CompilerPass.VALIDATION) {
            ctx.ideCompsLate.set(frameCtx.ideCompCtx.finish())
        }

        val cBody = compileFunction0(ctx, actParams.stmtCtx)
        val callFrame = frameCtx.makeCallFrame(cBody.guardBlock)
        val rRetType = fnCtx.actualReturnType()

        return R_FunctionBody(rRetType, actParams.rParams, actParams.rParamVars, cBody.rStmt, callFrame.rFrame)
    }
}

class S_FunctionBodyShort(
    private val posRange: S_PosRange,
    private val expr: S_Expr,
): S_FunctionBody() {
    override fun processStatementVars() = ImmTypedKeyMap()

    override fun returnsValue() = true

    override fun compileQuery0(bodyCtx: C_FunctionBodyContext, stmtCtx: C_StmtContext): C_Statement {
        val vExpr = compileExpr(bodyCtx, stmtCtx)
        val type = vExpr.type
        C_Utils.checkUnitType(bodyCtx.namePos, type) {
            "query_exprtype_unit" toCodeMsg "Query expressions returns nothing"
        }

        val adapter = stmtCtx.fnCtx.matchReturnType(bodyCtx.namePos, type)
        val vExpr2 = adapter.adaptExpr(stmtCtx.exprCtx, vExpr)

        val rExpr = vExpr2.toRExpr()
        return C_Statement(R_ReturnStatement(rExpr), true)
    }

    override fun compileFunction0(bodyCtx: C_FunctionBodyContext, stmtCtx: C_StmtContext): C_Statement {
        val vExpr = compileExpr(bodyCtx, stmtCtx)
        val type = vExpr.type

        val adapter = stmtCtx.fnCtx.matchReturnType(bodyCtx.namePos, type)
        val vExpr2 = adapter.adaptExpr(stmtCtx.exprCtx, vExpr)
        val rExpr = vExpr2.toRExpr()

        return if (rExpr.type != R_UnitType) {
            C_Statement(R_ReturnStatement(rExpr), true)
        } else {
            C_Statement(R_ExprStatement(rExpr), false)
        }
    }

    private fun compileExpr(bodyCtx: C_FunctionBodyContext, stmtCtx: C_StmtContext): V_Expr {
        stmtCtx.blkCtx.frameCtx.ideCompCtx.trackScope(posRange, stmtCtx.exprCtx)
        val cExpr = expr.compile(stmtCtx, C_ExprHint.ofType(bodyCtx.explicitRetType))
        return cExpr.vExpr()
    }
}

class S_FunctionBodyFull(private val body: S_Statement): S_FunctionBody() {
    override fun processStatementVars(): ImmTypedKeyMap {
        val map = MutableTypedKeyMap()
        body.discoverVars(map)
        return map.toImmTypedKeyMap()
    }

    override fun returnsValue(): Boolean {
        return body.returnsValue() ?: false
    }

    override fun compileQuery0(bodyCtx: C_FunctionBodyContext, stmtCtx: C_StmtContext): C_Statement {
        val cBody = body.compile(stmtCtx)

        C_Errors.check(cBody.alwaysReturns, bodyCtx.namePos) {
            val nameStr = bodyCtx.defName.qualifiedName
            "query_noreturn:$nameStr" toCodeMsg "Query '$nameStr': not all code paths return value"
        }

        return cBody
    }

    override fun compileFunction0(bodyCtx: C_FunctionBodyContext, stmtCtx: C_StmtContext): C_Statement {
        val cBody = body.compile(stmtCtx)

        val retType = stmtCtx.fnCtx.actualReturnType()
        if (retType != R_UnitType) {
            C_Errors.check(cBody.alwaysReturns, bodyCtx.namePos) {
                val nameStr = bodyCtx.defName.qualifiedName
                "fun_noreturn:$nameStr" toCodeMsg "Function '$nameStr': not all code paths return value"
            }
        }

        return cBody
    }
}
