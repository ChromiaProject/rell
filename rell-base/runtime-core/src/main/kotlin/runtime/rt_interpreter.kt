/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.rell.base.model.DefinitionId
import net.postchain.rell.base.model.FilePos
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.runtime.Rt_Interpreter.Companion.setInterpreterFactory
import net.postchain.rell.base.utils.toImmMap

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

    companion object {
        /**
         * Factory that pairs an [RR_App] with its compilation-local sys-function registry. The
         * map comes from `C_CompilationResult.compilationSysFns` / `T_App.compilationSysFns` —
         * values are downcast to [R_SysFunction]. Using this factory (rather than the raw
         * constructor) is strongly preferred so meta-body closure captures (e.g. `log()` call
         * positions, `gtv_ext(T)` `Rt_ValueClass` captures) don't leak across unrelated compilations.
         */
        fun forCompilation(rrApp: RR_App, compilationSysFns: Map<String, Any>): Rt_Interpreter {
            @Suppress("UNCHECKED_CAST")
            val sysFnMap = compilationSysFns as Map<String, R_SysFunction>
            ensureImplLoaded()
            return interpreterFactory(rrApp, Rt_StdlibEnv(sysFnMap.toImmMap()))
        }

        /**
         * Forces class-loading of the `runtime-interpreter` impl so its static-init block
         * registers the concrete factory. No-op if the JAR isn't on the classpath — the
         * subsequent factory call will throw the registration error.
         */
        private fun ensureImplLoaded() {
            try {
                Class.forName("net.postchain.rell.base.runtime.Rt_InterpreterImpl")
            } catch (_: ClassNotFoundException) {
                // runtime-interpreter not on classpath — factory call will surface a clear error.
            }
        }

        /**
         * Wired up by `runtime-interpreter` (via [setInterpreterFactory]) so that
         * `runtime-core` can construct an [Rt_Interpreter] without a compile-time dependency
         * on the implementation. The default implementation throws — calling code paths
         * that need an interpreter must run with `runtime-interpreter` on the classpath.
         */
        @Volatile
        private var interpreterFactory: (RR_App, Rt_StdlibEnv) -> Rt_Interpreter = { _, _ ->
            error("Rt_Interpreter implementation not registered — runtime-interpreter must be on the classpath")
        }

        /** Called once by `runtime-interpreter` at class-load to install the concrete factory. */
        fun setInterpreterFactory(factory: (RR_App, Rt_StdlibEnv) -> Rt_Interpreter) {
            interpreterFactory = factory
        }

        /** Direct factory mirroring the original `Rt_Interpreter(rrApp)` constructor call site. */
        fun create(rrApp: RR_App, stdlib: Rt_StdlibEnv = Rt_StdlibEnv.global()): Rt_Interpreter {
            ensureImplLoaded()
            return interpreterFactory(rrApp, stdlib)
        }
    }
}
