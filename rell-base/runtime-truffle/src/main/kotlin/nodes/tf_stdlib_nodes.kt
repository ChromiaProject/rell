/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle.nodes

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.profiles.BranchProfile
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.model.ErrorPos
import net.postchain.rell.base.model.FilePos
import net.postchain.rell.base.runtime.*

/**
 * Native: hand-rolled Truffle nodes for the most-frequent stdlib member calls. Each node
 * inlines the stdlib body directly so the dispatch chain
 * (`Tf_SysCallNode$SysMember.execute → buildArgList → buildCallCtx → @TruffleBoundary →
 * R_SysFunctionUtils.call → callAndCatch → fn.call → asXxx().method`) collapses to a
 * single primitive operation on the success path.
 *
 * # Bit-identical with the stdlib fallback
 *
 * Each node body matches the stdlib impl in `Lib_Type_*` exactly (same exception code/message,
 * same return value class). When an [Rt_Exception] is raised, the slow path mirrors
 * [net.postchain.rell.base.runtime.R_SysFunctionUtils.decorateSysFnException]'s
 * `attachExtraMessage` step before delegating to [tfRethrowNested] — so the surface stack
 * frame and "System function '<name>'" wrapper match what `R_SysFunctionUtils.call` would
 * have produced. Pure-success nodes (size, add) skip the catch block entirely; the only one
 * that actually exercises the slow path is [ListGet]'s out-of-bounds branch.
 *
 * # Why hand-roll instead of extending [Tf_MemberAccessNode.SysMemberFnCall]
 *
 * The generic node already pays for: building the `Rt_CallContext` (one allocation per
 * cache miss), wrapping args in [Tf_ArrayBackedList], crossing a `@TruffleBoundary`, and
 * routing through `R_SysFunction.call` — a Lib_*Lambda → MethodHandle invokeStatic chain
 * even for trivial bodies. Profiling shows that overhead is ~10× the actual `MutableList.add`
 * on the FT4 hot path. The native nodes here are pure inline code with no boundary on the
 * success path.
 */
internal sealed class Tf_StdlibMemberNode: Tf_ExprNode() {
    /**
     * Common safe-navigation prelude. Mirrors [Tf_MemberAccessNode.evaluateBase] so safe-call
     * (`base?.fn()`) short-circuits on `Rt_NullValue` without evaluating args or running the body.
     */
    protected fun evaluateBase(frame: VirtualFrame, baseChild: Tf_ExprNode, safe: Boolean): Rt_Value? {
        val baseValue = baseChild.execute(frame)
        if (safe && baseValue === Rt_NullValue) return null
        return baseValue
    }

    /**
     * Slow-path exception decoration matching [net.postchain.rell.base.runtime.R_SysFunctionUtils.decorateSysFnException]'s
     * `Rt_Exception` arm: attach `"System function '<name>'"` extra-message (preserving the
     * caller's existing message and respecting the [Rt_RequireError] carve-out), then route
     * through [tfRethrowNested] for the call-site stack-frame append. `@TruffleBoundary` keeps
     * the exception-mutation off PE; reached only through a [BranchProfile] guard at each
     * call site, so compiled code stays exception-free until the first throw.
     */
    @TruffleBoundary
    protected fun rethrowDecorated(
        rt: Rt_CallFrame,
        callPos: FilePos,
        displayName: String,
        e: Rt_Exception,
    ): Nothing {
        if (e.info.extraMessage == null && e.err !is Rt_RequireError) {
            e.attachExtraMessage("System function '$displayName'")
        }
        tfRethrowNested(rt, ErrorPos(callPos), e)
    }

    // -------------------------------------------------------------------------
    // list.size() / set.size() — pure size read, no allocation, no boundary
    // -------------------------------------------------------------------------

    /**
     * Native: `list.size()` — `((base as Rt_ListValue).elements).size.toLong()` boxed to [Rt_IntValue].
     * Mirrors `Lib_Type_Collection`'s `size` body inherited by `list`. Pure: never throws.
     */
    internal class ListSize(
        @field:Child private var base: Tf_ExprNode,
        @field:CompilationFinal private val safe: Boolean,
    ): Tf_StdlibMemberNode() {
        override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = evaluateBase(frame, base, safe) ?: return Rt_NullValue
            return Rt_IntValue.get((baseValue as Rt_ListValue).elements.size.toLong())
        }

        override fun executeLong(frame: VirtualFrame): Long {
            // Translator only picks the typed entry under non-nullable result type, so
            // `safe == true` cannot reach this path — see [Tf_MemberAccessNode.StructAttr.IntAttr].
            val baseValue = base.execute(frame)
            return (baseValue as Rt_ListValue).elements.size.toLong()
        }
    }

    /**
     * Native: `set.size()` — `((base as Rt_SetValue).elements).size.toLong()` boxed to [Rt_IntValue].
     * Mirrors `Lib_Type_Collection`'s `size` body inherited by `set`. Pure: never throws.
     */
    internal class SetSize(
        @field:Child private var base: Tf_ExprNode,
        @field:CompilationFinal private val safe: Boolean,
    ): Tf_StdlibMemberNode() {
        override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = evaluateBase(frame, base, safe) ?: return Rt_NullValue
            return Rt_IntValue.get((baseValue as Rt_SetValue).elements.size.toLong())
        }

        override fun executeLong(frame: VirtualFrame): Long {
            val baseValue = base.execute(frame)
            return (baseValue as Rt_SetValue).elements.size.toLong()
        }
    }

    /**
     * Native: `text.size()` — `Rt_TextValue.value.length.toLong()` boxed to [Rt_IntValue].
     * Mirrors `Lib_Type_Text`'s `size` body. Pure: never throws.
     */
    internal class TextSize(
        @field:Child private var base: Tf_ExprNode,
        @field:CompilationFinal private val safe: Boolean,
    ): Tf_StdlibMemberNode() {
        override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = evaluateBase(frame, base, safe) ?: return Rt_NullValue
            return Rt_IntValue.get((baseValue as Rt_TextValue).value.length.toLong())
        }

        override fun executeLong(frame: VirtualFrame): Long {
            val baseValue = base.execute(frame)
            return (baseValue as Rt_TextValue).value.length.toLong()
        }
    }

    // -------------------------------------------------------------------------
    // list.get(i) — bounds check + indexed read, slow-path throw on out-of-bounds
    // -------------------------------------------------------------------------

    /**
     * Native: `list.get(i)` — bounds-checked indexed read. Mirrors `Lib_Type_List`'s `get`
     * body byte-for-byte: same code (`fn:list.get:index:<size>:<i>`), same message
     * (`List index out of bounds: <i> (size <size>)`), same `Rt_Exception.common` constructor.
     *
     * Unlike [Tf_ListSubscriptNode] (which serves `list[i]` and uses the different
     * `list:index:...` code), this node's exception is wrapped by
     * [rethrowDecorated]'s `attachExtraMessage` step so the stdlib's "System function
     * 'list<...>.get'" prefix and the call-site stack frame are preserved.
     */
    internal class ListGet(
        @field:Child private var base: Tf_ExprNode,
        @field:Child private var index: Tf_ExprNode,
        @field:CompilationFinal private val callPos: FilePos,
        @field:CompilationFinal private val displayName: String,
        @field:CompilationFinal private val safe: Boolean,
    ): Tf_StdlibMemberNode() {

        /** Cold-branch profile: out-of-bounds is rare; PE folds the throw block out otherwise. */
        private val errorProfile: BranchProfile = BranchProfile.create()

        override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = evaluateBase(frame, base, safe) ?: return Rt_NullValue
            val list = (baseValue as Rt_ListValue).elements
            val i = index.executeLong(frame)
            if (i < 0 || i >= list.size) {
                errorProfile.enter()
                throwOutOfBounds(frame, list.size, i)
            }
            return list[i.toInt()]
        }

        /**
         * Slow-path bounds-check throw. Behind a `@TruffleBoundary` (transitively via
         * [rethrowDecorated]) so PE doesn't trace into the message-formatting / exception
         * decoration chain.
         */
        private fun throwOutOfBounds(frame: VirtualFrame, size: Int, idx: Long): Nothing {
            val e = Rt_Exception.common(
                "fn:list.get:index:$size:$idx",
                "List index out of bounds: $idx (size $size)",
            )
            rethrowDecorated(tfRtFrame(frame), callPos, displayName, e)
        }
    }

    // -------------------------------------------------------------------------
    // collection.add(v) — list & set, pure-success (no exception path in stdlib body)
    // -------------------------------------------------------------------------

    internal class CollectionAdd(
        @field:Child private var base: Tf_ExprNode,
        @field:Child private var value: Tf_ExprNode,
        @field:CompilationFinal private val safe: Boolean,
    ): Tf_StdlibMemberNode() {
        override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = evaluateBase(frame, base, safe) ?: return Rt_NullValue
            val v = value.execute(frame)
            return Rt_BooleanValue.get((baseValue as Rt_CollectionValue).collection.add(v))
        }

        override fun executeBoolean(frame: VirtualFrame): Boolean {
            val baseValue = base.execute(frame)
            val v = value.execute(frame)
            return (baseValue as Rt_CollectionValue).collection.add(v)
        }
    }

    // -------------------------------------------------------------------------
    // enum.name — sys-property, [RR_MemberCalculator.SysFunction] arm
    // -------------------------------------------------------------------------

    /**
     * Native: `enum.name` — sys-property dispatch. Body is `Rt_TextValue.get((self as Rt_RR_EnumValue).rrAttr.name)`,
     * identical to `Lib_Type_Enum`'s `enum_ext.name` extension. Pure: `RR_EnumAttr.name`
     * is a non-null cached field, so this never throws under correct typing.
     *
     * Differs from the function-call nodes ([ListGet], [CollectionAdd]) in receiver-only
     * dispatch: no args, no [Tf_ArrayBackedList] wrapper, no callPos.
     */
    internal class EnumName(
        @field:Child private var base: Tf_ExprNode,
        @field:CompilationFinal private val safe: Boolean,
    ): Tf_StdlibMemberNode() {
        override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = evaluateBase(frame, base, safe) ?: return Rt_NullValue
            return Rt_TextValue.get((baseValue as Rt_RR_EnumValue).rrAttr.nameStr)
        }
    }

    // -------------------------------------------------------------------------
    // gtv.type — sys-property, returns gtv_type enum
    // -------------------------------------------------------------------------

    /**
     * Native: `gtv.type` — sys-property returning the `gtv_type` enum value for the GTV's type
     * tag. Mirrors `Lib_Type_Gtv`'s `type` property body
     * (`Lib_Rell.GTV_TYPE_ENUM.rtGetValueOrNull(gtv.type.ordinal)`) without the
     * `R_SysFunctionUtils.call` dispatch + lambda chain.
     *
     * Big win on FT4 workloads that previously read `gtv.to_bytes()[0]` to inspect the BER tag —
     * 22% of `bench_gtv_text` was the BER serialisation pipeline (`GtvEncoder.encodeGtv`,
     * `RawGtv.encode`, `BerInteger.encode`, etc.) just to read one byte. With `gtv.type` natively
     * dispatched here, the lookup is `gtv.type.ordinal` + a cached enum-attribute lookup.
     *
     * `Lib_Rell.GTV_TYPE_ENUM.rtGetValueOrNull(...)` is invoked behind a `@TruffleBoundary` so
     * the enum-attribute table walk and the `Rt_R_EnumValue` allocation stay opaque to PE; on
     * the success path the return value of `rtGetValueOrNull` is non-null because every
     * `GtvType.ordinal` is a registered `gtv_type` enum value.
     */
    internal class GtvType(
        @field:Child private var base: Tf_ExprNode,
        @field:CompilationFinal private val safe: Boolean,
    ): Tf_StdlibMemberNode() {
        override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = evaluateBase(frame, base, safe) ?: return Rt_NullValue
            return resolve((baseValue as Rt_GtvValue).value.type.ordinal)
        }

        @TruffleBoundary
        private fun resolve(ordinal: Int): Rt_Value =
            Lib_Rell.GTV_TYPE_ENUM.rtGetValueOrNull(ordinal)
                ?: throw Rt_Exception.common("gtv:type:unknown", "Unknown GTV type ordinal: $ordinal")
    }
}
