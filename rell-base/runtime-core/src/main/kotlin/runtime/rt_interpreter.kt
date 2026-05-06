/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.rell.base.model.DefinitionId
import net.postchain.rell.base.model.FilePos
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.rr.*

/**
 * Tree-walk interpreter contract. The concrete implementation
 * (`Rt_InterpreterImpl` in `runtime-interpreter`) holds the dispatch tables, helpers, and
 * extension-function targets used by the rest of the runtime; this seam lets the foundational
 * runtime types (values, contexts, stdlib) live in `runtime-core` without depending on the
 * interpreter back-end.
 */
interface Rt_Interpreter {
    val rrApp: RR_App
    val stdlib: Rt_StdlibEnv
    val metaGtv: Gtv

    // --- Type resolution ---

    /** Build a runtime type-class from [RR_Type], cached per-app. */
    fun resolveType(type: RR_Type): Rt_ValueClass<*>

    /** Resolve an [R_Type] to an [Rt_ValueClass] for this app. */
    fun resolveRType(rType: R_Type): Rt_ValueClass<*>

    // --- Definition entry points ---

    fun callFunction(
        fn: RR_FunctionDefinition,
        exeCtx: Rt_ExecutionContext,
        args: List<Rt_Value>,
        dbUpdateAllowed: Boolean = false,
    ): Rt_Value

    fun callOperation(op: RR_OperationDefinition, exeCtx: Rt_ExecutionContext, args: List<Rt_Value>)

    fun executeOperationGuard(op: RR_OperationDefinition, exeCtx: Rt_ExecutionContext, args: List<Rt_Value>)

    fun callQuery(query: RR_QueryDefinition, exeCtx: Rt_ExecutionContext, args: List<Rt_Value>): Rt_Value

    fun evaluateConstant(const: RR_GlobalConstantDefinition, exeCtx: Rt_ExecutionContext): Rt_Value

    fun evaluateAttributeDefault(
        defId: DefinitionId,
        attrIndex: Int,
        exeCtx: Rt_ExecutionContext,
        dbUpdateAllowed: Boolean = false,
    ): Rt_Value

    fun evaluateParamDefault(
        param: RR_FunctionParam,
        defCtx: Rt_DefinitionContext,
    ): Rt_Value

    /** Used by [Rt_RR_LazyValue] to evaluate the captured expression on demand. */
    fun evaluateExpr(expr: RR_Expr, frame: Rt_Frame): Rt_Value

    /** Dispatch a captured RR call target with the given closure frame. */
    fun callTarget(
        target: RR_FunctionCallTarget,
        base: Rt_Value?,
        args: List<Rt_Value>,
        frame: Rt_Frame,
        callPos: FilePos? = null,
    ): Rt_Value

    /**
     * Returns the plainest tree-walker interpreter underlying this instance, for code paths that
     * intrinsically require the tree-walker (REPL line execution, low-level frame state
     * inspection, etc.).
     */
    fun unwrapInterpreterImpl(): Any
}
