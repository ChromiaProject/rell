/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle.nodes

import com.google.common.math.LongMath
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.DirectCallNode
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.IndirectCallNode
import net.postchain.rell.base.model.DefinitionId
import net.postchain.rell.base.model.ErrorPos
import net.postchain.rell.base.model.FilePos
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.truffle.Tf_Backend
import net.postchain.rell.base.runtime.truffle.Tf_Unchecked

/**
 * Native: binary operator. The translator picks a specialised subclass per op shape so the
 * runtime path is straight-line code rather than a `when (key)` dispatch — and so typed
 * `executeLong`/`executeBoolean` paths can chain through arithmetic without intermediate
 * [Rt_IntValue]/[Rt_BooleanValue] allocations.
 *
 * - [IntArith] specialises the five integer arithmetic ops; overrides [executeLong] so a
 *   chained expression like `a + b * c - 1` does five primitive ops and one box at the end.
 * - [BoolAnd] / [BoolOr] handle short-circuit logic on the typed-boolean path.
 * - [Generic] is the catch-all for collection/text/big-int/decimal arithmetic and unusual
 *   comparison shapes; it stays on the existing dispatch tables.
 */
internal sealed class Tf_BinaryNode : Tf_ExprNode() {

    /**
     * Integer arithmetic — `+ - * / %`. One concrete subclass per op so PE never sees a
     * `when (opKind)` dispatch — Kotlin's compiler emits a synthetic `else -> throw
     * NoWhenBranchMatchedException()` for every exhaustive `when` over an enum, and PE was
     * tracing into that constructor on every iteration (~1200 hot samples in profiling).
     *
     * Per-op subclasses also let Graal fold each call site to a single primitive operation
     * without any branching at all, which is what the typed `executeLong` chain is for.
     */
    internal abstract class IntArith(
        @field:Child protected var left: Tf_ExprNode,
        @field:Child protected var right: Tf_ExprNode,
        @field:CompilationFinal protected val errPos: ErrorPos?,
    ) : Tf_BinaryNode() {
        protected abstract val opCode: String
        protected abstract fun op(l: Long, r: Long): Long

        final override fun executeLong(frame: VirtualFrame): Long {
            val l = left.executeLong(frame)
            val r = right.executeLong(frame)
            return try {
                op(l, r)
            } catch (_: ArithmeticException) {
                throw Rt_Exception.common("expr:$opCode:overflow:$l:$r", "Integer overflow: $l $opCode $r")
            }
        }

        final override fun execute(frame: VirtualFrame): Rt_Value = try {
            Rt_IntValue.get(executeLong(frame))
        } catch (e: Rt_Exception) {
            if (errPos != null) tfRethrowAt(frame, errPos, e) else throw e
        }
    }

    internal class IntAdd(left: Tf_ExprNode, right: Tf_ExprNode, errPos: ErrorPos?) :
        IntArith(left, right, errPos) {
        override val opCode
            get() = "+"

        override fun op(l: Long, r: Long): Long = LongMath.checkedAdd(l, r)
    }

    internal class IntSub(left: Tf_ExprNode, right: Tf_ExprNode, errPos: ErrorPos?) :
        IntArith(left, right, errPos) {
        override val opCode
            get() = "-"

        override fun op(l: Long, r: Long): Long = LongMath.checkedSubtract(l, r)
    }

    internal class IntMul(left: Tf_ExprNode, right: Tf_ExprNode, errPos: ErrorPos?) :
        IntArith(left, right, errPos) {
        override val opCode
            get() = "*"

        override fun op(l: Long, r: Long): Long = LongMath.checkedMultiply(l, r)
    }

    internal class IntDiv(left: Tf_ExprNode, right: Tf_ExprNode, errPos: ErrorPos?) :
        IntArith(left, right, errPos) {
        override val opCode
            get() = "/"

        override fun op(l: Long, r: Long): Long {
            if (r == 0L) throw Rt_Exception.common("expr:/:div0:$l", "Division by zero: $l / 0")
            return l / r
        }
    }

    internal class IntMod(left: Tf_ExprNode, right: Tf_ExprNode, errPos: ErrorPos?) :
        IntArith(left, right, errPos) {
        override val opCode
            get() = "%"

        override fun op(l: Long, r: Long): Long {
            if (r == 0L) throw Rt_Exception.common("expr:%:div0:$l", "Division by zero: $l % 0")
            return l % r
        }
    }

    /**
     * Integer comparisons (`<`, `<=`, `>`, `>=`) and equality (`==`, `!=`). One subclass per
     * operator — same rationale as [IntArith]: avoid the synthetic
     * `NoWhenBranchMatchedException` else-branch and let PE fold each comparison to one CMP.
     *
     * Equality (Eq/Ne) on integer-typed operands previously routed through [Generic]'s
     * `evaluateBinaryOp` dispatch (HashMap lookup keyed on op-key string + boxed
     * `Rt_IntValue.equals`). Profiling showed that path responsible for ~13K samples /
     * 30%+ of the profile on the bench workload.
     */
    internal abstract class IntBoolBinary(
        @field:Child protected var left: Tf_ExprNode,
        @field:Child protected var right: Tf_ExprNode,
    ) : Tf_BinaryNode() {
        protected abstract fun op(l: Long, r: Long): Boolean
        final override fun executeBoolean(frame: VirtualFrame): Boolean =
            op(left.executeLong(frame), right.executeLong(frame))
        final override fun execute(frame: VirtualFrame): Rt_Value = Rt_BooleanValue.get(executeBoolean(frame))
    }

    internal class IntLt(left: Tf_ExprNode, right: Tf_ExprNode) : IntBoolBinary(left, right) {
        override fun op(l: Long, r: Long): Boolean = l < r
    }

    internal class IntLe(left: Tf_ExprNode, right: Tf_ExprNode) : IntBoolBinary(left, right) {
        override fun op(l: Long, r: Long): Boolean = l <= r
    }

    internal class IntGt(left: Tf_ExprNode, right: Tf_ExprNode) : IntBoolBinary(left, right) {
        override fun op(l: Long, r: Long): Boolean = l > r
    }

    internal class IntGe(left: Tf_ExprNode, right: Tf_ExprNode) : IntBoolBinary(left, right) {
        override fun op(l: Long, r: Long): Boolean = l >= r
    }

    internal class IntEq(left: Tf_ExprNode, right: Tf_ExprNode) : IntBoolBinary(left, right) {
        override fun op(l: Long, r: Long): Boolean = l == r
    }

    internal class IntNe(left: Tf_ExprNode, right: Tf_ExprNode) : IntBoolBinary(left, right) {
        override fun op(l: Long, r: Long): Boolean = l != r
    }

    /** Short-circuit `&&`. Evaluates `right` only when `left` is true. */
    internal class BoolAnd(
        @field:Child private var left: Tf_ExprNode,
        @field:Child private var right: Tf_ExprNode,
    ) : Tf_BinaryNode() {
        override fun executeBoolean(frame: VirtualFrame): Boolean =
            left.executeBoolean(frame) && right.executeBoolean(frame)

        override fun execute(frame: VirtualFrame): Rt_Value = Rt_BooleanValue.get(executeBoolean(frame))
    }

    /** Short-circuit `||`. Evaluates `right` only when `left` is false. */
    internal class BoolOr(
        @field:Child private var left: Tf_ExprNode,
        @field:Child private var right: Tf_ExprNode,
    ) : Tf_BinaryNode() {
        override fun executeBoolean(frame: VirtualFrame): Boolean =
            left.executeBoolean(frame) || right.executeBoolean(frame)

        override fun execute(frame: VirtualFrame): Rt_Value = Rt_BooleanValue.get(executeBoolean(frame))
    }

    /**
     * Catch-all for everything that isn't integer arithmetic / integer comparison / `&&` / `||`.
     * Keeps the original op-key dispatch via [evaluateBinaryOp] / [evaluateCmpBinaryOp] but
     * routes the actual evaluation through a [TruffleBoundary] so PE doesn't try to inline
     * the giant `when (key)` dispatch tables in those helpers — the intermediate graph from
     * full table expansion is what breaks the inliner-depth budget. We lose the
     * constant-folded fast path for the rare ops, but the cold-path cost is one boundary
     * call per evaluation.
     *
     * The translator picks [GenericInt] / [GenericBool] / [Generic] by result type — typed
     * variants override `executeLong`/`executeBoolean` to unbox via [Tf_Unchecked.cast],
     * dropping the default `execute().asInteger()` / `asBoolean()` chain (whose `typeError`
     * branch dominated PE-traced graphs even though it was never live at runtime).
     */
    internal open class Generic(
        @field:Child private var left: Tf_ExprNode,
        @field:Child private var right: Tf_ExprNode,
        @field:CompilationFinal private val op: RR_BinaryOp,
        @field:CompilationFinal private val cmpInfo: RR_CmpBinaryOp?,
        @field:CompilationFinal private val errPos: ErrorPos?,
    ) : Tf_BinaryNode() {
        final override fun execute(frame: VirtualFrame): Rt_Value {
            val l = left.execute(frame)
            val sc = shortCircuit(l)
            if (sc != null) return sc
            val r = right.execute(frame)
            return try {
                evaluate(l, r)
            } catch (e: Rt_Exception) {
                if (errPos != null) tfRethrowAt(frame, errPos, e) else throw e
            }
        }

        @TruffleBoundary
        private fun shortCircuit(l: Rt_Value): Rt_Value? = shortCircuitBinaryOp(op, l)

        @TruffleBoundary
        private fun evaluate(l: Rt_Value, r: Rt_Value): Rt_Value =
            if (cmpInfo != null) evaluateCmpBinaryOp(cmpInfo, l, r) else evaluateBinaryOp(op, l, r)
    }

    /** Generic binary node whose result is statically integer-typed — unboxes on `executeLong`. */
    internal class GenericInt(
        left: Tf_ExprNode,
        right: Tf_ExprNode,
        op: RR_BinaryOp,
        cmpInfo: RR_CmpBinaryOp?,
        errPos: ErrorPos?,
    ) : Generic(left, right, op, cmpInfo, errPos) {
        override fun executeLong(frame: VirtualFrame): Long =
            Tf_Unchecked.cast<Rt_IntValue>(execute(frame)).value
    }

    /** Generic binary node whose result is statically boolean-typed — unboxes on `executeBoolean`. */
    internal class GenericBool(
        left: Tf_ExprNode,
        right: Tf_ExprNode,
        op: RR_BinaryOp,
        cmpInfo: RR_CmpBinaryOp?,
        errPos: ErrorPos?,
    ) : Generic(left, right, op, cmpInfo, errPos) {
        override fun executeBoolean(frame: VirtualFrame): Boolean =
            Tf_Unchecked.cast<Rt_BooleanValue>(execute(frame)).value
    }

}

/**
 * Native: unary operator. Sealed by op shape so the typed `executeLong` / `executeBoolean`
 * specialisations chain through [Tf_BinaryNode]'s primitive paths without intermediate
 * boxing.
 *
 * - [IntMinus] for integer negation; overrides [executeLong] with explicit overflow
 *   handling (mirrors the tree-walker's `expr:-:overflow:$v` error code exactly).
 * - [Not] for boolean negation; overrides [executeBoolean].
 * - [Generic] for `Minus_BigInteger` / `Minus_Decimal`, which still go through the
 *   `evaluateUnaryOp` dispatch table — the savings on those paths would be minor.
 */
internal sealed class Tf_UnaryNode : Tf_ExprNode() {

    internal class IntMinus(
        @field:Child private var expr: Tf_ExprNode,
        @field:CompilationFinal private val errPos: ErrorPos,
    ) : Tf_UnaryNode() {
        override fun executeLong(frame: VirtualFrame): Long {
            val v = expr.executeLong(frame)
            return try {
                LongMath.checkedSubtract(0L, v)
            } catch (_: ArithmeticException) {
                throw Rt_Exception.common("expr:-:overflow:$v", "Integer overflow: -($v)")
            }
        }

        override fun execute(frame: VirtualFrame): Rt_Value = try {
            Rt_IntValue.get(executeLong(frame))
        } catch (e: Rt_Exception) {
            tfRethrowAt(frame, errPos, e)
        }
    }

    internal class Not(
        @field:Child private var expr: Tf_ExprNode,
    ) : Tf_UnaryNode() {
        override fun executeBoolean(frame: VirtualFrame): Boolean = !expr.executeBoolean(frame)
        override fun execute(frame: VirtualFrame): Rt_Value = Rt_BooleanValue.get(executeBoolean(frame))
    }

    internal class Generic(
        @field:Child private var expr: Tf_ExprNode,
        @field:CompilationFinal private val op: RR_UnaryOp,
        @field:CompilationFinal private val errPos: ErrorPos,
    ) : Tf_UnaryNode() {
        override fun execute(frame: VirtualFrame): Rt_Value {
            val v = expr.execute(frame)
            return try {
                evaluate(v)
            } catch (e: Rt_Exception) {
                tfRethrowAt(frame, errPos, e)
            }
        }

        @TruffleBoundary
        private fun evaluate(v: Rt_Value): Rt_Value = evaluateUnaryOp(op, v)
    }
}

/**
 * Native: implicit type conversion (Direct, IntegerToBigInteger, IntegerToDecimal,
 * BigIntegerToDecimal, Nullable). Adapter is `@CompilationFinal`; the singleton-object adapters
 * fold to no-ops or single conversion paths after PE.
 *
 * The translator picks [DirectInt] / [DirectBool] for the no-op-shape `Direct` adapter when the
 * inner expression is statically integer- or boolean-typed — those subclasses skip
 * `applyTypeAdapter` entirely and chain through the inner node's typed `executeLong` /
 * `executeBoolean`. Other adapter shapes (`IntegerToBigInteger`, `IntegerToDecimal`, ...) box
 * unconditionally because the conversion's output type is non-primitive.
 */
internal class Tf_TypeAdapterNode(
    @field:Child private var expr: Tf_ExprNode,
    @field:CompilationFinal private val adapter: RR_TypeAdapter,
) : Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value =
        applyTypeAdapter(adapter, expr.execute(frame))

    /**
     * `Direct` integer adapter — passes the inner expression's typed `executeLong` through
     * unchanged. Used when the translator proves the inner type is integer.
     */
    internal class DirectInt(
        @field:Child private var inner: Tf_ExprNode,
    ) : Tf_ExprNode() {
        override fun executeLong(frame: VirtualFrame): Long = inner.executeLong(frame)
        override fun execute(frame: VirtualFrame): Rt_Value = Rt_IntValue.get(executeLong(frame))
    }

    /** `Direct` boolean adapter — pass-through on the typed boolean path. See [DirectInt]. */
    internal class DirectBool(
        @field:Child private var inner: Tf_ExprNode,
    ) : Tf_ExprNode() {
        override fun executeBoolean(frame: VirtualFrame): Boolean = inner.executeBoolean(frame)
        override fun execute(frame: VirtualFrame): Rt_Value = Rt_BooleanValue.get(executeBoolean(frame))
    }
}

/**
 * Native: an irreducible compile-time error placeholder (`RR_Expr.Error`). The compiler emits
 * these for nodes that survived type-checking but should be unreachable at runtime; the
 * tree-walker uses Kotlin's `error()` for the same case, throwing [IllegalStateException]. We
 * mirror that exact behaviour to keep differential parity.
 */
internal class Tf_ErrorNode(
    @field:CompilationFinal private val message: String,
) : Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value = error("RR_Expr.Error reached at runtime: $message")
}

/**
 * Native: full function call. Two specialised shapes:
 *
 * - [UserFn] for `RegularUser` / `AbstractUser` targets — invokes the callee through a
 *   [DirectCallNode] wrapping the cached Truffle [com.oracle.truffle.api.RootCallTarget],
 *   so Graal's PE can inline the callee body into the caller. This is the entire reason
 *   to be running on Truffle: cross-call inlining + per-callsite specialisation.
 *
 * - [Generic] for everything else — operations, native functions, function values,
 *   sys-functions, partial calls, abstract-overrides, extendables. These have no Truffle
 *   representation today and dispatch through the wrapped impl's `callTarget`.
 *
 * Both shapes share the argument-evaluation prelude (base, args, mapping, safe-null check).
 * Identity mapping is detected at translate time and the runtime skips the permutation in
 * that case.
 */
internal sealed class Tf_FunctionCallNode : Tf_ExprNode() {
    /**
     * User-function call site. Dispatches via [DirectCallNode] resolved lazily on the first
     * execution from a `@CompilationFinal` [RootCallTarget].
     *
     * **Direct, not indirect.** With Kotlin's null-check intrinsics stripped from all hot-path
     * modules (see `runtime-core`/`utils`/`runtime-interpreter`/`runtime-truffle`
     * `build.gradle.kts`), Graal's PE no longer chases `Intrinsics.checkNotNull → JDK Locale`
     * cycles, so the inliner-depth budget that previously forced [IndirectCallNode] is no
     * longer hit. With [DirectCallNode] each callee body inlines into the caller, which is
     * the entire point of running on Truffle — cross-call constant folding, escape analysis,
     * and primitive-typed `executeLong`/`executeBoolean` chains across function boundaries.
     *
     * **Translator-time validation pushdown.** When the translator can prove (a) every arg's
     * static type structurally matches its corresponding parameter's declared type and (b)
     * no parameter carries an `RR_SizeConstraint`, the call site sets `fastPath = true`,
     * `frameDescriptor` / `defId` / `paramOffsets` are pre-resolved at translate time, and
     * `setupCalleeFrameFast` runs inline — no [TruffleBoundary], no `validateParams`, no
     * `setParams` indirection. The frame is allocated and parameter slots are written
     * directly via [Rt_CallFrame.setUncheckedAt], which lets Graal's PE fold caller-side
     * argument values straight into the callee's body.
     *
     * **Slow path.** When `fastPath = false` (the rare cases: type-adapter reshapes, size
     * constraints), the legacy [TruffleBoundary] path through
     * `delegate.validateParams` / `createFrame` / `setParams` still runs, behind a lazy
     * resolution that mirrors how recursive functions need to be wired up.
     */
    internal open class UserFn(
        @field:Child private var base: Tf_ExprNode?,
        @field:Children private val args: Array<Tf_ExprNode>,
        @field:CompilationFinal(dimensions = 1) private val mapping: IntArray,
        @field:CompilationFinal private val safe: Boolean,
        @field:CompilationFinal private val identityMapping: Boolean,
        @field:CompilationFinal private val callPos: FilePos,
        @field:CompilationFinal private val fnDefIndex: Int,
        // --- Translate-time pushdown ---
        @field:CompilationFinal private val fastPath: Boolean,
        @field:CompilationFinal private val frameDescriptor: RR_FrameDescriptor,
        @field:CompilationFinal private val defId: DefinitionId,
        // `paramOffsets[i]` is the offset within the callee frame where evaluated[mapping[i]]
        // must be written. Pre-extracted from `fnBase.paramVars[i].ptr.offset` at translate time.
        @field:CompilationFinal(dimensions = 1) private val paramOffsets: IntArray,
        private val backend: Tf_Backend,
    ) : Tf_FunctionCallNode() {

        @field:Child
        private var directCall: DirectCallNode? = null

        // Slow-path-only cache: populated lazily on the first call when `fastPath = false`.
        // The fast-path reads `frameDescriptor` / `defId` / `paramOffsets` straight from the
        // ctor-injected fields above, so these are dead code on the hot path.
        @field:CompilationFinal
        private var cachedFnBase: RR_FunctionBase? = null

        @field:CompilationFinal
        private var cachedFnName: String? = null

        /**
         * Lazy resolution of the [DirectCallNode]. Sidesteps a translator-time
         * chicken-and-egg: building `bench`'s body triggers translation of `is_prime` /
         * `collatz_steps` / `fib`, which in turn would try to resolve their own call targets
         * — and recursive functions like `fib` would loop. Resolving on first execute, behind
         * a `transferToInterpreterAndInvalidate()`, lets PE see a `@CompilationFinal` non-null
         * `DirectCallNode` on every subsequent call.
         */
        private fun resolveCall(): DirectCallNode {
            val existing = directCall
            if (existing != null) return existing
            CompilerDirectives.transferToInterpreterAndInvalidate()
            val fn = backend.rrApp.allFunctions[fnDefIndex]
            // Slow path keeps a reference to the full RR_FunctionBase / fnName so
            // `setupCalleeFrameSlow` can hand `validateParams` / `setParams` what they need.
            // On the fast path these stay null and the field reads constant-fold to dead
            // weight that the inliner drops.
            if (!fastPath) {
                val fnBase = fn.fnBase
                cachedFnBase = fnBase
                cachedFnName = fnBase.defName.appLevelName
            }
            val node = DirectCallNode.create(backend.functionTarget(fn))
            directCall = insert(node)
            return node
        }

        @ExplodeLoop
        final override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = base?.execute(frame)
            // Reference equality — `Rt_NullValue` is a singleton object. `==` would compile
            // to `Intrinsics.areEqual`, whose contract (handle null receivers) drags in the
            // String/Throwable construction chain we're trying to keep out of PE.
            if (safe && baseValue === Rt_NullValue) return Rt_NullValue

            val evaluated = arrayOfNulls<Rt_Value>(args.size)
            for (i in args.indices) {
                evaluated[i] = args[i].execute(frame)
            }

            val call = resolveCall()
            val callArgs: Array<Any?> = if (fastPath) {
                buildInnerCallArgs(frame, evaluated)
            } else {
                buildOuterCallArgsSlow(frame, evaluated)
                    ?: return reportSetupError(frame)
            }

            return try {
                // Pass the args array to Truffle through the Java helper so the array becomes
                // `frame.arguments` directly (no spread copy on the way in). The callee root
                // returns the function's return value directly (or [Rt_UnitValue] when the
                // body falls through without an explicit `return`); reading from the
                // [TF_RETURN_VALUE_AUX_SLOT] of the callee frame would require materialising
                // it, so we channel the value through the call-target return slot instead.
                Tf_Unchecked.cast(Tf_Unchecked.callDirect(call, callArgs))
            } catch (e: Rt_Exception) {
                // Mirror the tree-walker's `Rt_InterpreterImpl.callTarget` wrapping: at each call
                // boundary, append the caller's `callPos` to the Rell-level stack via
                // `frame.error(.., nested = true)`. Without this, only the bottom-most frame survives
                // and `StackTraceTest`'s expected call chains collapse to a single entry.
                tfRethrowNested(frame, ErrorPos(callPos), e)
            }
        }

        /**
         * PE-visible inline arg packaging for the validated-at-translate-time path.
         *
         * No [TruffleBoundary]: every step here is straight-line Kotlin/JVM code that PE can
         * trace through. The translator already proved every arg's static type matches its
         * param's declared type and that no param carries a size constraint, so
         * `validateParams` / `setParams`'s type check are pure dead code at this site.
         *
         * Builds the inner-entry arguments shape `[null, callerRtFrame, arg1, …, argN]`:
         *
         * - `arguments[0] = null` — the inner-entry sentinel; the callee root's
         *   [net.postchain.rell.base.runtime.truffle.Tf_FunctionRootNode] dispatches on this.
         * - `arguments[1] = callerRtFrame` — the caller's [Rt_CallFrame], used only by
         *   [tfLazyAllocRtFrame] in the callee body if a slow-path node demands an
         *   [Rt_CallFrame]. The hot-path callee body never reads it.
         * - arguments[2 + i] = evaluated[mapping] — the i-th evaluated argument in
         *   callee-param order. The callee root writes each into the param's frame slot
         *   directly via `frame.setObject` — no [Rt_CallFrame] allocation.
         *
         * The mapping array is `@CompilationFinal` so PE unrolls the loop fully and inlines
         * each evaluated value into the callee's body argument graph. For a recursive
         * `is_prime(i)` call, this collapses (after PE) to a literal store of the caller's
         * `i` into the callee's param slot.
         *
         * The lazy [Rt_CallFrame] in the callee, if needed, references the caller's
         * [Rt_CallFrame] for `exeCtx` and `dbUpdateAllowed()` — so we cache `tfRtFrame(frame)`
         * here at translate-time-stable offsets. Reading from the caller's [VirtualFrame] aux
         * slot is one slot read which PE can fold to the actual frame pointer at the inlining.
         */
        @ExplodeLoop
        private fun buildInnerCallArgs(
            frame: VirtualFrame,
            evaluated: Array<Rt_Value?>,
        ): Array<Any?> {
            val callArgs = arrayOfNulls<Any>(2 + paramOffsets.size)
            // arguments[0] stays null — the inner-entry sentinel.
            // arguments[1] = the outermost live caller's Rt_CallFrame, propagated via
            // [tfPropagateRtFrame] (no alloc, no boundary). On the hot path the callee never
            // touches it; on slow path it provides exeCtx + dbUpdateAllowed via
            // [tfLazyAllocRtFrame]. Using [tfRtFrame] here would eagerly trigger the
            // boundary-crossing lazy alloc for any caller whose own aux slot is empty (i.e.
            // every inner-entry caller), forcing the caller's [VirtualFrame] to materialise
            // and aborting tier-1 compilation with `SourceStackTraceBailoutException`.
            callArgs[1] = tfPropagateRtFrame(frame)
            for (i in paramOffsets.indices) {
                // `evaluated[mapping[i]]!!` would compile to `Intrinsics.checkNotNull`, which
                // drags the JDK String/Locale chain into PE. Cast through Tf_Unchecked instead.
                val argValue: Rt_Value = Tf_Unchecked.cast(evaluated[mapping[i]])
                callArgs[2 + i] = argValue
            }
            return callArgs
        }

        @CompilationFinal
        @Volatile
        private var pendingError: Rt_Exception? = null

        /**
         * Slow-path callee-frame setup. Reached only when the translator could not prove
         * fast-path eligibility — either an arg-to-param type mismatch (e.g. nullable widen)
         * or a param with an `RR_SizeConstraint`. The wrapped helpers' Java bytecode shape
         * is unrelated to the call's payload and would balloon the compiled graph if let
         * into PE, hence the [TruffleBoundary]. Returns null when validation throws so
         * callers can take a cold error path without baking try/catch state into the hot
         * graph.
         *
         * The slow path keeps the legacy [Rt_CallFrame] construction (so size-constraint /
         * type-adapter validation runs through `delegate.validateParams` /
         * `delegate.setParams` exactly as before) and packages it as the **outer-entry**
         * arguments shape `calleeRtFrame`. The callee root
         * [net.postchain.rell.base.runtime.truffle.Tf_FunctionRootNode] sees
         * `arg0 != null` and takes the outer-entry branch — pulling `values[]` into the
         * [VirtualFrame] for the body's first reads, just like a top-level driver call.
         */
        @TruffleBoundary
        private fun buildOuterCallArgsSlow(
            frame: VirtualFrame,
            evaluated: Array<Rt_Value?>,
        ): Array<Any?>? {
            val caller = tfRtFrame(frame)
            val mapped = if (identityMapping) {
                Tf_Unchecked.cast<Array<Rt_Value>>(evaluated).asList()
            } else {
                buildList(mapping.size) {
                    for (i in mapping.indices) {
                        add(evaluated[mapping[i]]!!)
                    }
                }
            }

            val fnBase: RR_FunctionBase = Tf_Unchecked.cast(cachedFnBase)
            val fnName: String = Tf_Unchecked.cast(cachedFnName)

            return try {
                backend.delegate.validateParams(fnBase.params, mapped)
                val callee = backend.delegate.createFrame(
                    caller.exeCtx,
                    frameDescriptor,
                    caller.dbUpdateAllowed(),
                    defId,
                )
                backend.delegate.setParams(callee, fnBase.paramVars, mapped, fnName)
                arrayOf(callee)
            } catch (e: Rt_Exception) {
                pendingError = e
                null
            }
        }

        @TruffleBoundary
        private fun reportSetupError(frame: VirtualFrame): Rt_Value {
            val e = pendingError ?: error("setupCalleeFrame returned null without recording error")
            pendingError = null
            tfRtFrame(frame).error(ErrorPos(callPos), e, true)
        }
    }

    /**
     * User-function call whose return type is statically integer. Overrides [executeLong]
     * to drop the default `execute(frame).asInteger()` chain — `asInteger`'s `typeError`
     * branch dominates PE-traced graphs (~6.5K samples in profiling) even though it's
     * unreachable when the type system already proved the return type.
     */
    internal class UserFnInt(
        base: Tf_ExprNode?,
        args: Array<Tf_ExprNode>,
        mapping: IntArray,
        safe: Boolean,
        identityMapping: Boolean,
        callPos: FilePos,
        fnDefIndex: Int,
        fastPath: Boolean,
        frameDescriptor: RR_FrameDescriptor,
        defId: DefinitionId,
        paramOffsets: IntArray,
        backend: Tf_Backend,
    ) : UserFn(
        base, args, mapping, safe, identityMapping, callPos, fnDefIndex,
        fastPath, frameDescriptor, defId, paramOffsets, backend,
    ) {
        override fun executeLong(frame: VirtualFrame): Long =
            Tf_Unchecked.cast<Rt_IntValue>(execute(frame)).value
    }

    /** User-function call whose return type is statically boolean. See [UserFnInt]. */
    internal class UserFnBool(
        base: Tf_ExprNode?,
        args: Array<Tf_ExprNode>,
        mapping: IntArray,
        safe: Boolean,
        identityMapping: Boolean,
        callPos: FilePos,
        fnDefIndex: Int,
        fastPath: Boolean,
        frameDescriptor: RR_FrameDescriptor,
        defId: DefinitionId,
        paramOffsets: IntArray,
        backend: Tf_Backend,
    ) : UserFn(
        base, args, mapping, safe, identityMapping, callPos, fnDefIndex,
        fastPath, frameDescriptor, defId, paramOffsets, backend,
    ) {
        override fun executeBoolean(frame: VirtualFrame): Boolean =
            Tf_Unchecked.cast<Rt_BooleanValue>(execute(frame)).value
    }

    /**
     * Generic call site for non-user-function targets (sys-fn, operation, native, partial,
     * function-value, abstract-override, extendable). Dispatches via [Tf_Backend.callTarget],
     * which routes to the wrapped impl. Specialising any of these would mostly mirror
     * existing impl helpers without giving Graal a smaller shape to inline.
     */
    internal open class Generic(
        @field:Child private var base: Tf_ExprNode?,
        @field:Children private val args: Array<Tf_ExprNode>,
        @field:CompilationFinal private val target: RR_FunctionCallTarget,
        @field:CompilationFinal(dimensions = 1) private val mapping: IntArray,
        @field:CompilationFinal private val safe: Boolean,
        @field:CompilationFinal private val identityMapping: Boolean,
        @field:CompilationFinal private val callPos: FilePos,
        private val backend: Tf_Backend,
    ) : Tf_FunctionCallNode() {

        @ExplodeLoop
        final override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = base?.execute(frame)
            if (safe && baseValue === Rt_NullValue) return Rt_NullValue

            val evaluated = arrayOfNulls<Rt_Value>(args.size)
            for (i in args.indices) {
                evaluated[i] = args[i].execute(frame)
            }
            val mapped: List<Rt_Value> = if (identityMapping) {
                Tf_Unchecked.cast<Array<Rt_Value>>(evaluated).asList()
            } else {
                buildList(mapping.size) {
                    for (i in mapping.indices) {
                        add(evaluated[mapping[i]]!!)
                    }
                }
            }
            return backend.callTarget(target, baseValue, mapped, tfRtFrame(frame), callPos)
        }
    }

    /** Sys-/native-/op-call whose return type is statically integer. See [UserFnInt]. */
    internal class GenericInt(
        base: Tf_ExprNode?,
        args: Array<Tf_ExprNode>,
        target: RR_FunctionCallTarget,
        mapping: IntArray,
        safe: Boolean,
        identityMapping: Boolean,
        callPos: FilePos,
        backend: Tf_Backend,
    ) : Generic(base, args, target, mapping, safe, identityMapping, callPos, backend) {
        override fun executeLong(frame: VirtualFrame): Long =
            Tf_Unchecked.cast<Rt_IntValue>(execute(frame)).value
    }

    /** Sys-/native-/op-call whose return type is statically boolean. See [UserFnInt]. */
    internal class GenericBool(
        base: Tf_ExprNode?,
        args: Array<Tf_ExprNode>,
        target: RR_FunctionCallTarget,
        mapping: IntArray,
        safe: Boolean,
        identityMapping: Boolean,
        callPos: FilePos,
        backend: Tf_Backend,
    ) : Generic(base, args, target, mapping, safe, identityMapping, callPos, backend) {
        override fun executeBoolean(frame: VirtualFrame): Boolean =
            Tf_Unchecked.cast<Rt_BooleanValue>(execute(frame)).value
    }

    companion object {
        /**
         * Pick the typed [Generic] subclass matching the static return type, or the untyped
         * [Generic] when the result is neither integer nor boolean. Used by the translator's
         * sys-fn dispatch fallback path so the result-type specialisation logic stays in one
         * place.
         */
        fun makeGeneric(
            base: Tf_ExprNode?,
            args: Array<Tf_ExprNode>,
            target: RR_FunctionCallTarget,
            mapping: IntArray,
            safe: Boolean,
            identityMapping: Boolean,
            callPos: FilePos,
            backend: Tf_Backend,
            resultIsInt: Boolean,
            resultIsBool: Boolean,
        ): Tf_ExprNode = when {
            resultIsInt -> GenericInt(base, args, target, mapping, safe, identityMapping, callPos, backend)
            resultIsBool -> GenericBool(base, args, target, mapping, safe, identityMapping, callPos, backend)
            else -> Generic(base, args, target, mapping, safe, identityMapping, callPos, backend)
        }
    }
}

/**
 * Native: struct construction (`RR_Expr.StructCreate`).
 *
 * Each `RR_Expr.StructCreate`'s `attrs` provides an attribute index plus an expression. The
 * declared attribute list is fixed at compile time, so we capture the per-position write order
 * (`attrIndices`) and the expression nodes parallel to it. Missing positions stay null, which
 * the tree-walker materialises as `Rt_NullValue` — we do the same.
 *
 * Per-attribute size constraints (`@max_size`, etc.) are checked through the wrapped impl;
 * this stays reference-correct without re-implementing the constraint dispatch tree here.
 */
internal class Tf_StructCreateNode(
    @field:CompilationFinal private val structDefIndex: Int,
    @field:CompilationFinal private val rtType: Rt_ValueClass<*>,
    @field:CompilationFinal(dimensions = 1) private val attrIndices: IntArray,
    @field:Children private val attrExprs: Array<Tf_ExprNode>,
    @field:CompilationFinal private val attrCount: Int,
    @field:CompilationFinal(dimensions = 1) private val attrNames: Array<String>,
    private val backend: Tf_Backend,
) : Tf_ExprNode() {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Rt_Value {
        val attrValues = arrayOfNulls<Rt_Value>(attrCount)
        for (i in attrIndices.indices) {
            attrValues[attrIndices[i]] = attrExprs[i].execute(frame)
        }

        val values = ArrayList<Rt_Value>(attrCount)
        for (i in 0 until attrCount) {
            values += attrValues[i] ?: Rt_NullValue
        }

        // Apply size-constraint validation via the wrapped impl. The constraint list lives
        // on the struct definition, which the impl already has indexed; rather than mirror
        // the lookup here we delegate the per-attr `checkSizeConstraint` calls.
        val structDef = backend.rrApp.allStructs[structDefIndex]
        for (i in 0 until attrCount) {
            structDef.struct.attributesList[i].sizeConstraint?.let {
                backend.delegate.checkSizeConstraint(it, values[i])
            }
        }

        return Rt_StructValue(rtType, attrNames.asList(), values)
    }
}

/**
 * Native: shared chooser for `when` expressions and statements. Returns the matched branch
 * index, or `elseIndex` (which may be -1) when no branch matched.
 *
 * Two variants:
 *   - [Iterative] — sequential equality scan over evaluated condition expressions
 *   - [Lookup] — pre-computed constant key list, scanned for value equality
 *
 * Both dispatch shapes are common enough to warrant separate sealed implementations rather than
 * a runtime mode field; this lets PE specialise each chooser site cleanly.
 */
internal sealed class Tf_WhenChooserNode : Tf_ExprNode() {

    /** Returns the matched branch index, or -1 when no branch matched and there is no else. */
    abstract fun chooseIndex(frame: VirtualFrame): Int

    /** Required by [Tf_ExprNode]; bridge unused — chooser is never executed as an expression. */
    final override fun execute(frame: VirtualFrame): Rt_Value =
        error("Tf_WhenChooserNode.execute: use chooseIndex instead")

    internal class Iterative(
        @field:Child private var keyExpr: Tf_ExprNode,
        @field:Children private val condExprs: Array<Tf_ExprNode>,
        @field:CompilationFinal(dimensions = 1) private val condIndices: IntArray,
        @field:CompilationFinal private val elseIndex: Int,
    ) : Tf_WhenChooserNode() {
        @ExplodeLoop
        override fun chooseIndex(frame: VirtualFrame): Int {
            val key = keyExpr.execute(frame)
            for (i in condExprs.indices) {
                if (key == condExprs[i].execute(frame)) return condIndices[i]
            }
            return elseIndex
        }
    }

    internal class Lookup(
        @field:Child private var keyExpr: Tf_ExprNode,
        @field:CompilationFinal(dimensions = 1) private val keys: Array<Rt_Value>,
        @field:CompilationFinal(dimensions = 1) private val values: IntArray,
        @field:CompilationFinal private val elseIndex: Int,
    ) : Tf_WhenChooserNode() {
        @ExplodeLoop
        override fun chooseIndex(frame: VirtualFrame): Int {
            val key = keyExpr.execute(frame)
            for (i in keys.indices) {
                if (keys[i] == key) return values[i]
            }
            return elseIndex
        }
    }
}

/**
 * Native: `when` expression. Picks an index via the chooser, then evaluates the matched
 * branch. The compiler guarantees the chooser returns a valid index for expression-shaped
 * `when` (else-branch always present), so no defensive -1 handling is needed here.
 *
 * Typed subclasses ([IntWhen]/[BoolWhen]) chain through each branch's typed
 * `executeLong`/`executeBoolean` so the entire `when` stays primitive when the result type
 * is statically integer or boolean.
 */
internal open class Tf_WhenExprNode(
    @field:Child private var chooser: Tf_WhenChooserNode,
    @field:Children private val branches: Array<Tf_ExprNode>,
) : Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        val idx = chooser.chooseIndex(frame)
        check(idx >= 0) { "when expression: no matching branch and no else" }
        return branches[idx].execute(frame)
    }

    internal class IntWhen(
        @field:Child private var chooser: Tf_WhenChooserNode,
        @field:Children private val branches: Array<Tf_ExprNode>,
    ) : Tf_ExprNode() {
        override fun executeLong(frame: VirtualFrame): Long {
            val idx = chooser.chooseIndex(frame)
            check(idx >= 0) { "when expression: no matching branch and no else" }
            return branches[idx].executeLong(frame)
        }

        override fun execute(frame: VirtualFrame): Rt_Value = Rt_IntValue.get(executeLong(frame))
    }

    internal class BoolWhen(
        @field:Child private var chooser: Tf_WhenChooserNode,
        @field:Children private val branches: Array<Tf_ExprNode>,
    ) : Tf_ExprNode() {
        override fun executeBoolean(frame: VirtualFrame): Boolean {
            val idx = chooser.chooseIndex(frame)
            check(idx >= 0) { "when expression: no matching branch and no else" }
            return branches[idx].executeBoolean(frame)
        }

        override fun execute(frame: VirtualFrame): Rt_Value = Rt_BooleanValue.get(executeBoolean(frame))
    }
}
