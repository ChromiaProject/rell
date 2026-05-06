/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle

import com.oracle.truffle.api.RootCallTarget
import net.postchain.gtv.Gtv
import net.postchain.rell.base.model.DefinitionId
import net.postchain.rell.base.model.FilePos
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.utils.toImmMap

/**
 * Truffle backend for Rell.
 *
 * Implements [Rt_Interpreter] as a peer to [Rt_InterpreterImpl] in `runtime-interpreter`. Both
 * backends consume the same [RR_App], use the same stdlib, hit the same database — they differ
 * only in dispatch.
 *
 * # Strategy
 *
 * Each user-defined function/operation/query body is translated once to a Truffle [RootCallTarget]
 * (cached per definition). At call time, the host bridge sets up an
 * [net.postchain.rell.base.runtime.Rt_CallFrame] (reusing the tree-walker's frame implementation)
 * and invokes the cached call target. The root node holds a tree of
 * [net.postchain.rell.base.runtime.truffle.nodes.Tf_ExprNode] children.
 * Hot, simple expressions execute through specialized Truffle nodes;
 * complex (database-heavy, etc.) nodes go through a fallback that delegates to [Rt_InterpreterImpl].
 * This guarantees bit-identical semantics for every test from day one while giving Graal a hot path it can specialize
 * and inline.
 *
 * # Why the wrap of [Rt_InterpreterImpl]
 *
 * The interpreter implementation owns a lot of plumbing the Truffle backend needs verbatim:
 * `createFrame`, `setParams`, `validateParams`, `executeStmt`, `evaluateExpr`, `callTarget`,
 * at-expression / update / delete execution, type resolution, meta-Gtv synthesis. Re-implementing
 * these inside the Truffle module would duplicate a lot of code and re-introduce subtle
 * divergence between backends.
 */
class Tf_Backend(
    override val rrApp: RR_App,
    override val stdlib: Rt_StdlibEnv = Rt_StdlibEnv.global(),
) : Rt_Interpreter {

    /**
     * Wrapped tree-walker. All "fallback" code paths route through this.
     *
     * `outerInterp = this` redirects every user-function/query call the tree-walker would
     * otherwise dispatch internally back into [Tf_Backend], so that the Truffle-cached call
     * target gets used for the callee — even when the call originates inside a fallback
     * subtree that the tree-walker is currently evaluating. Without this wire-up, recursive
     * function calls reached through a fallback (e.g. `delegate.evaluateExpr` for a
     * not-yet-translated `RR_Expr`) would tree-walk forever, defeating Truffle's per-callee
     * cache for everything beneath that fallback.
     */
    internal val delegate: Rt_InterpreterImpl = Rt_InterpreterImpl(rrApp, stdlib).also {
        it.outerInterp = this
    }

    override val metaGtv: Gtv
        get() = delegate.metaGtv

    /**
     * Per-definition call-target cache. Once a body has been translated to a Truffle AST
     * + wrapped in a [RootCallTarget], subsequent calls reuse that target so Graal's compiled
     * code persists across invocations. Keyed by definition identity (the [RR_App] is
     * immutable; identity is stable per-app).
     */
    internal val fnTargets: MutableMap<RR_FunctionDefinition, RootCallTarget> = HashMap()
    internal val opTargets: MutableMap<RR_OperationDefinition, RootCallTarget> = HashMap()
    internal val opGuardTargets: MutableMap<RR_OperationDefinition, RootCallTarget> = HashMap()
    internal val queryTargets: MutableMap<RR_QueryDefinition, RootCallTarget> = HashMap()
    internal val constTargets: MutableMap<RR_GlobalConstantDefinition, RootCallTarget> = HashMap()

    /** Runtime driver that owns translation and call-target cache management. */
    internal val driver: Tf_Driver = Tf_Driver(this)

    /**
     * Returns (and lazily creates) the cached Truffle [RootCallTarget] for [fn]. Used by
     * call-site nodes that hold a [com.oracle.truffle.api.nodes.DirectCallNode] for the callee:
     * the cached target lets Graal inline across the call boundary, which is the entire reason
     * to be running on Truffle in the first place.
     *
     * Safe under self-recursion because we only invoke this lazily — at first call-site
     * execution, well after the caller's own body has finished translating.
     */
    internal fun functionTarget(fn: RR_FunctionDefinition): RootCallTarget =
        fnTargets.getOrPut(fn) { driver.buildFunctionTarget(fn) }

    override fun resolveType(type: RR_Type): Rt_ValueClass<*> = delegate.resolveType(type)
    override fun resolveRType(rType: R_Type): Rt_ValueClass<*> = delegate.resolveRType(rType)

    override fun callFunction(
        fn: RR_FunctionDefinition,
        exeCtx: Rt_ExecutionContext,
        args: List<Rt_Value>,
        dbUpdateAllowed: Boolean,
    ): Rt_Value = driver.callFunction(fn, exeCtx, args, dbUpdateAllowed)

    override fun callOperation(op: RR_OperationDefinition, exeCtx: Rt_ExecutionContext, args: List<Rt_Value>) {
        driver.callOperation(op, exeCtx, args)
    }

    override fun executeOperationGuard(op: RR_OperationDefinition, exeCtx: Rt_ExecutionContext, args: List<Rt_Value>) {
        driver.executeOperationGuard(op, exeCtx, args)
    }

    override fun callQuery(query: RR_QueryDefinition, exeCtx: Rt_ExecutionContext, args: List<Rt_Value>): Rt_Value =
        driver.callQuery(query, exeCtx, args)

    override fun evaluateConstant(const: RR_GlobalConstantDefinition, exeCtx: Rt_ExecutionContext): Rt_Value =
        driver.evaluateConstant(const, exeCtx)

    override fun evaluateAttributeDefault(
        defId: DefinitionId,
        attrIndex: Int,
        exeCtx: Rt_ExecutionContext,
        dbUpdateAllowed: Boolean,
    ): Rt_Value = delegate.evaluateAttributeDefault(defId, attrIndex, exeCtx, dbUpdateAllowed)

    override fun evaluateParamDefault(param: RR_FunctionParam, defCtx: Rt_DefinitionContext): Rt_Value =
        delegate.evaluateParamDefault(param, defCtx)

    override fun evaluateExpr(expr: RR_Expr, frame: Rt_Frame): Rt_Value = delegate.evaluateExpr(expr, frame)

    /**
     * Delegates to the wrapped tree-walker, which itself routes user-function/query targets
     * through `outerInterp` (== this backend) — so the call lands on [Tf_Backend.callFunction]
     * / [callQuery] and uses the cached Truffle [RootCallTarget] for the callee.
     */
    override fun callTarget(
        target: RR_FunctionCallTarget,
        base: Rt_Value?,
        args: List<Rt_Value>,
        frame: Rt_Frame,
        callPos: FilePos?,
    ): Rt_Value = delegate.callTarget(target, base, args, frame, callPos)

    /**
     * Unwraps to the underlying [Rt_InterpreterImpl]. Used by REPL line execution which
     * threads `Rt_CallFrameState` between commands and is fundamentally a tree-walker concept.
     */
    override fun unwrapInterpreterImpl(): Any = delegate

    companion object {
        /**
         * Pairs an [RR_App] with its compilation-local sys-function registry. The map comes
         * from `C_CompilationResult.compilationSysFns` / `T_App.compilationSysFns`; values are
         * downcast to [R_SysFunction]. Callers should use this rather than the raw constructor
         * so meta-body closure captures don't leak across unrelated compilations.
         */
        fun forCompilation(rrApp: RR_App, compilationSysFns: Map<String, Any>): Tf_Backend {
            val sysFnMap = Tf_Unchecked.cast<Map<String, R_SysFunction>>(compilationSysFns)
            return Tf_Backend(rrApp, Rt_StdlibEnv(sysFnMap.toImmMap()))
        }
    }
}
