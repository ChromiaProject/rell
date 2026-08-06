/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.utils.C_FeatureRestrictions
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.V_CommonFunctionCall_Partial
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.compiler.vexpr.V_FunctionCallExpr
import net.postchain.rell.base.compiler.vexpr.V_FunctionCallTarget_RegularUserFunction
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.R_PartialArgMapping
import net.postchain.rell.base.model.expr.R_PartialCallMapping
import net.postchain.rell.base.model.stmt.R_ExprStatement
import net.postchain.rell.base.model.stmt.R_ReturnStatement
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.ide.IdeLocalSymbolLink
import net.postchain.rell.base.utils.ide.IdeSymbolKind

/**
 * Arrow lambda: `x -> e`, `(x, y) -> e`, `() -> e`, or with a value-block body
 * `x -> { stmt; ...; result }` (the trailing expression is the result; `return` is not allowed).
 *
 * A parameter's type comes from its optional explicit annotation (parenthesised form only,
 * `(x: integer) -> e`) or, when unannotated, from the expected function type at the use site (target typing).
 * The return type is always inferred from the body.
 * Lowering: the body is lifted into a synthetic hidden function and the lambda value is a partial application of it
 * (captured outer locals bound, lambda params left as wildcards).
 */
internal class S_LambdaParam(val name: S_Name, val type: S_Type?)

internal class S_LambdaExpr(
    pos: S_Pos,
    val params: ImmList<S_LambdaParam>,
    val body: S_LambdaBody,
    val bodyNames: Set<String>,
): S_Expr(pos) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        RESTRICTIONS_LAMBDA.access(ctx.msgCtx, startPos)
        return C_LambdaCompiler.compile(ctx, hint, startPos, params, body, bodyNames)
    }

    companion object {
        private val RESTRICTIONS_LAMBDA = C_FeatureRestrictions.make(
            "0.16.1",
            "expr_lambda" toCodeMsg "Lambda expressions are",
        )
    }
}

internal sealed interface S_LambdaBody

internal class S_LambdaBody_Expr(val expr: S_Expr): S_LambdaBody

internal class S_LambdaBody_Block(
    val posRange: S_PosRange,
    val stmts: ImmList<S_Statement>,
    val result: S_Expr?,
): S_LambdaBody

/**
 * The trailing expression of a value-block lambda body. Whether it becomes the lambda's result or
 * is evaluated and discarded is decided here from the lambda's return type: a `unit` expected
 * return discards it (mirroring a body with no trailing expression); a non-unit expected return
 * returns it, adapted to that type; and with no expected return (bottom-up inference) it is
 * returned when it has a value, or discarded when it is `unit`-typed, since `return <unit>` is not
 * allowed.
 */
private class S_LambdaResultStatement(private val expr: S_Expr): S_Statement(expr.startPos, expr.startPos) {
    override fun compile(ctx: C_StmtContext, repl: Boolean): C_Statement {
        val fnCtx = ctx.fnCtx
        val expRetType = fnCtx.explicitReturnType
        val vExpr = expr.compile(ctx, C_ExprHint.ofType(expRetType)).vExpr()
        val discard = expRetType == R_UnitType || (expRetType == null && vExpr.type == R_UnitType)
        if (discard) {
            return C_Statement(R_ExprStatement(vExpr.toRExpr()), alwaysReturns = false)
        }
        val adapter = fnCtx.matchReturnType(expr.startPos, vExpr.type)
        val rExpr = adapter.adaptExpr(ctx.exprCtx, vExpr).toRExpr()
        return C_Statement(R_ReturnStatement(rExpr), alwaysReturns = true)
    }
}

private object C_LambdaCompiler {
    /** A single captured outer local: its name, its offset in the enclosing frame (for a stable
     *  ordering), and the by-value read of it evaluated in the enclosing context. */
    private class Capture(val name: Name, val offset: Int, val boundArg: V_Expr)

    /**
     * The formal parameters of the lifted function: the lambda's own parameters followed by the
     * captured outer locals, plus the by-value bound arguments that supply those captures. [varStates]
     * marks every formal as initialized for the body's statement context.
     */
    private class LiftedFormals(
        val rParams: ImmList<R_FunctionParam>,
        val rParamVars: ImmList<R_ParamVar>,
        val varStates: C_VarStatesDelta,
        val boundArgs: ImmList<V_Expr>,
    )

    fun compile(
        ctx: C_ExprContext,
        hint: C_ExprHint,
        pos: S_Pos,
        params: ImmList<S_LambdaParam>,
        body: S_LambdaBody,
        bodyNames: Set<String>,
    ): C_Expr {
        val defCtx = ctx.defCtx

        // Parameter types come from two sources — an explicit annotation, or the expected function
        // type at the use site. The four combinations of {annotated?} × {expected type present?}
        // are resolved into a single list of parameter types (or an error).
        val expected = hint.typeHint.getFunctionType()
        val paramTypes = resolveParamTypes(ctx, pos, params, expected) ?: return C_ExprUtils.errorExpr(ctx, pos)

        // A fresh function + frame context for the lifted body. When an expected type is present its
        // result fixes the return type (Explicit tracker, body checked against it); otherwise the
        // return type is inferred bottom-up from the body (Implicit tracker).
        val explicitRetType = expected?.result
        val cDefName = defCtx.cDefName.toPath().subName(Name.of("lambda"))
        val defName = cDefName.toRDefName()

        // For a value-block body the block is materialised up-front, because its declared vars must
        // be discovered before the function context is created. Whether the trailing expression is
        // returned or discarded is decided when it is compiled (see S_LambdaResultStatement).
        val valueBlock = (body as? S_LambdaBody_Block)?.let { buildValueBlock(it) }

        val statementVars = if (valueBlock == null) ImmTypedKeyMap() else {
            val map = MutableTypedKeyMap()
            valueBlock.discoverVars(map)
            map.toImmTypedKeyMap()
        }

        val fnCtx = C_FunctionContext(defCtx, defName.appLevelName, explicitRetType, statementVars, insideLambda = true)
        val frameCtx = C_FrameContext.create(fnCtx)
        val blkCtx = frameCtx.rootBlkCtx

        val formals = buildLiftedFormals(ctx, defCtx, blkCtx, pos, params, paramTypes, bodyNames)
            ?: return C_ExprUtils.errorExpr(ctx, pos)

        val stmtCtx = C_StmtContext.createRoot(blkCtx).updateVarStates(formals.varStates)

        val cBody = when (body) {
            is S_LambdaBody_Expr -> compileExprBody(stmtCtx, fnCtx, body.expr, explicitRetType)
            is S_LambdaBody_Block -> valueBlock!!.compileSafe(stmtCtx)
        }

        val callFrame = frameCtx.makeCallFrame(cBody.guardBlock)
        val rRetType = fnCtx.actualReturnType()

        // A value-typed lambda must yield a value on every path. An expression body always does; a
        // value block that neither ends in a trailing expression nor returns on all paths does not.
        if (body is S_LambdaBody_Block && rRetType != R_UnitType && !cBody.alwaysReturns) {
            ctx.msgCtx.error(
                pos, "lambda:no_return",
                "Lambda must return a value of type '${rRetType.strCode()}' on all code paths",
            )
        }

        val rFn = synthesizeFunction(
            defCtx = defCtx,
            cDefName = cDefName,
            defName = defName,
            pos = pos,
            rBody = R_FunctionBody(rRetType, formals.rParams, formals.rParamVars, cBody.rStmt, callFrame.rFrame),
        )

        return lambdaValue(
            ctx = ctx,
            pos = pos,
            paramTypes = paramTypes,
            rRetType = rRetType,
            rFn = rFn,
            boundArgs = formals.boundArgs
        )
    }

    /**
     * Bind the lifted function's formals: the lambda's own parameters as the leading locals (offsets
     * 0..m-1, mirroring `C_FormalParameters.compile`), then the captured outer locals (offsets m..m+k-1).
     * Captures are the outer locals the body mentions by simple name — (body name set) ∩ (initialized
     * in-scope locals of the enclosing context) — bound by value; reserving them here, before the body
     * is compiled, keeps their frame slots from overlapping any sub-block created while compiling the body.
     * Returns null (after reporting an error) on a duplicate parameter name.
     */
    private fun buildLiftedFormals(
        ctx: C_ExprContext,
        defCtx: C_DefinitionContext,
        blkCtx: C_BlockContext,
        pos: S_Pos,
        params: ImmList<S_LambdaParam>,
        paramTypes: List<R_Type>,
        bodyNames: Set<String>,
    ): LiftedFormals? {
        val rParams = mutableListOf<R_FunctionParam>()
        val rParamVars = mutableListOf<R_ParamVar>()
        var varStates = C_VarStatesDelta.EMPTY
        val paramNames = mutableSetOf<String>()
        var paramError = false

        for ((i, sParam) in params.withIndex()) {
            // Register the parameter name as a real local definition (LOC_PARAMETER): the declaration
            // occurrence gets the def symbol, and body references resolve to it via the ref symbol's
            // link - so a lambda parameter is highlighted, renameable, and findable like any local.
            val nameHand = sParam.name.compile(ctx, def = true)
            val cName = nameHand.name
            if (!paramNames.add(cName.str)) {
                ctx.msgCtx.error(
                    cName.pos,
                    "lambda:dup_param:${cName.str}",
                    "Duplicate lambda parameter: '${cName.str}'",
                )
                nameHand.setIdeInfo(C_IdeSymbolInfo.UNKNOWN)
                paramError = true
                continue
            }
            val type = paramTypes[i]
            val ideDef = C_IdeSymbolDef.make(
                IdeSymbolKind.LOC_PARAMETER,
                link = IdeLocalSymbolLink(cName.pos),
                doc = DocSymbol.NONE,
            )
            nameHand.setIdeInfo(ideDef.defInfo)
            val cVarRef = blkCtx.addLocalVar(cName, type, false, null, ideDef.refInfo)
            varStates = varStates.changed(C_VarStateKey(cVarRef.target.uid))
            rParams += R_FunctionParam(cName.rName, type, defCtx.initFrameGetter)
            rParamVars += R_ParamVar(type, cVarRef.ptr)
        }

        if (paramError) return null

        val boundArgs = mutableListOf<V_Expr>()

        for (cap in discoverCaptures(ctx, pos, bodyNames, paramNames)) {
            val capType = cap.boundArg.type
            val cName = C_Name.make(pos, cap.name)
            // The captured name is a synthetic local in the lifted frame, so a body occurrence of it
            // stays UNKNOWN: a local IDE link is frame-offset-scoped and cannot point across frames to
            // the outer variable's declaration, and any within-frame link would resolve to the wrong
            // symbol. The outer variable itself is still referenced (through the bound argument), so
            // this is only a missing highlight on the in-body occurrence, not a lost reference.
            val cVarRef = blkCtx.addLocalVar(cName, capType, false, null, C_IdeSymbolInfo.UNKNOWN)
            varStates = varStates.changed(C_VarStateKey(cVarRef.target.uid))
            rParams += R_FunctionParam(cName.rName, capType, defCtx.initFrameGetter)
            rParamVars += R_ParamVar(capType, cVarRef.ptr)
            boundArgs += cap.boundArg
        }

        return LiftedFormals(rParams.toImmList(), rParamVars.toImmList(), varStates, boundArgs.toImmList())
    }

    /**
     * The lambda value: a partial application of the hidden function with every lambda param left as a
     * wildcard (offsets 0..m-1) and each captured outer local supplied as a bound arg (offsets m..m+k-1,
     * read from the enclosing frame at closure-creation time).
     */
    private fun lambdaValue(
        ctx: C_ExprContext,
        pos: S_Pos,
        paramTypes: List<R_Type>,
        rRetType: R_Type,
        rFn: R_FunctionDefinition,
        boundArgs: ImmList<V_Expr>,
    ): C_Expr {
        val fnType = R_FunctionType(paramTypes.toImmList(), rRetType)
        val m = paramTypes.size
        val k = boundArgs.size

        val argMappings = buildList {
            for (p in 0..<m) add(R_PartialArgMapping(wild = true, index = p))
            for (j in 0..<k) add(R_PartialArgMapping(wild = false, index = j))
        }.toImmList()

        return C_ValueExpr(
            vExpr = V_FunctionCallExpr(
                exprCtx = ctx,
                pos = pos,
                base = null,
                call = V_CommonFunctionCall_Partial(
                    pos = pos,
                    returnType = fnType,
                    target = V_FunctionCallTarget_RegularUserFunction(rFn),
                    args = boundArgs,
                    mapping = R_PartialCallMapping(
                        exprCount = k,
                        wildCount = m,
                        args = argMappings,
                    ),
                ),
                safe = false,
            ),
        )
    }

    /**
     * The outer locals to capture: every body identifier that resolves, in the enclosing context,
     * to an initialized local variable (and is not shadowed by a lambda parameter). Ordered by the
     * variable's enclosing-frame offset so the capture list is deterministic regardless of the
     * body-name set's iteration order.
     */
    private fun discoverCaptures(
        ctx: C_ExprContext,
        pos: S_Pos,
        bodyNames: Set<String>,
        paramNames: Set<String>,
    ): List<Capture> = bodyNames
        .mapNotNull { rawName ->
            if (rawName in paramNames) return@mapNotNull null
            val name = Name.of(rawName)
            val outerRef = ctx.blkCtx.lookupLocalVar(name) ?: return@mapNotNull null
            val target = outerRef.target
            if (target.rName == null) return@mapNotNull null
            if (ctx.varStates.getInited(target.varKey) != true) return@mapNotNull null
            Capture(name, target.offset, outerRef.compile(ctx, pos))
        }
        .sortedBy { it.offset }

    /**
     * Resolve the lambda's parameter types from the two available sources — explicit annotations
     * and the expected function type — covering the four combinations of {annotated?} × {expected
     * type present?}. Returns null (after reporting an error) when the parameters cannot be typed.
     */
    private fun resolveParamTypes(
        ctx: C_ExprContext,
        pos: S_Pos,
        params: ImmList<S_LambdaParam>,
        expected: R_FunctionType?,
    ): List<R_Type>? {
        // Arity is checked first: an expected type of a different arity is a use-site error
        // regardless of annotations.
        if (expected != null && expected.params.size != params.size) {
            ctx.msgCtx.error(
                pos, "lambda:param_count:${expected.params.size}:${params.size}",
                "Lambda has ${params.size} parameter(s) but the expected type has ${expected.params.size}",
            )
            return null
        }

        // Annotation is all-or-nothing across the parameters.
        val annCount = params.count { it.type != null }
        if (annCount != 0 && annCount != params.size) {
            ctx.msgCtx.error(
                pos, "lambda:partial_param_types",
                "Either all lambda parameters must be type-annotated, or none",
            )
            return null
        }
        val annTypes = if (annCount > 0) params.map { it.type!!.compile(ctx) } else null

        // When both an annotation and an expected type are present, each annotated type must equal
        // the corresponding expected one, structurally (nullability included): neither the sound-
        // but-useless contravariant direction nor the unsound covariant one is accepted.
        if (annTypes != null && expected != null) {
            for (i in params.indices) {
                if (annTypes[i] != expected.params[i]) {
                    val pName = params[i].name.getRNameSpecial().str
                    ctx.msgCtx.error(
                        params[i].name.pos,
                        "lambda:param_type:$i:[${expected.params[i].strCode()}]:[${annTypes[i].strCode()}]",
                        "Parameter '$pName' is annotated '${annTypes[i].strCode()}' but the expected type is '${expected.params[i].strCode()}'",
                    )
                    return null
                }
            }
        }

        return when {
            annTypes != null -> annTypes
            expected != null -> expected.params
            params.isEmpty() -> immListOf()
            else -> {
                ctx.msgCtx.error(
                    pos, "lambda:no_type",
                    "Cannot infer lambda type: no expected function type in this context and the parameters are not annotated",
                )
                null
            }
        }
    }

    private fun compileExprBody(
        stmtCtx: C_StmtContext,
        fnCtx: C_FunctionContext,
        bodyExpr: S_Expr,
        expectedResult: R_Type?,
    ): C_Statement {
        val vExpr = bodyExpr.compile(stmtCtx, C_ExprHint.ofType(expectedResult)).vExpr()
        val adapter = fnCtx.matchReturnType(bodyExpr.startPos, vExpr.type)
        val rExpr = adapter.adaptExpr(stmtCtx.exprCtx, vExpr).toRExpr()

        return if (rExpr.type != R_UnitType) {
            C_Statement(R_ReturnStatement(rExpr), alwaysReturns = true)
        } else {
            C_Statement(R_ExprStatement(rExpr), alwaysReturns = false)
        }
    }

    /**
     * Turn a value-block `{ stmt; ...; result }` into a plain statement block. The trailing
     * expression is wrapped in [S_LambdaResultStatement], which decides at compile time — from the
     * lambda's (possibly inferred) return type — whether to return it or discard it.
     */
    private fun buildValueBlock(body: S_LambdaBody_Block): S_Statement {
        val result = body.result
        val stmts = if (result == null) body.stmts else body.stmts + S_LambdaResultStatement(result)
        return S_BlockStatement(body.posRange, stmts)
    }

    private fun synthesizeFunction(
        defCtx: C_DefinitionContext,
        cDefName: C_DefinitionName,
        defName: DefinitionName,
        pos: S_Pos,
        rBody: R_FunctionBody,
    ): R_FunctionDefinition = R_FunctionDefinition(
        base = R_DefinitionBase(
            // Source-position-derived id keeps stack traces stable and unique per lambda
            defId = DefinitionId(defName.module, "${defName.qualifiedName}[${pos.line()}:${pos.column()}]"),
            defName = defName,
            cDefName = cDefName,
            initFrameGetter = defCtx.initFrameGetter,
            docPos = null,
            docGetter = C_LateGetter.const(DocSymbol.NONE),
        ),
        fnBase = R_FunctionBase.eager(
            defName = defName,
            header = R_FunctionHeader(rBody.type, rBody.params),
            body = rBody,
        ),
        isTest = false,
        disabled = false,
    )
}
