/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.llvm

import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.R_SysFunction
import net.postchain.rell.base.runtime.Rt_ByteArrayValue
import net.postchain.rell.base.runtime.Rt_DecimalValue
import net.postchain.rell.base.runtime.Rt_TextValue
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_RR_EnumValue
import net.postchain.rell.base.runtime.Rt_EnumType
import net.postchain.rell.base.runtime.Rt_Frame
import net.postchain.rell.base.runtime.Rt_ListValue
import net.postchain.rell.base.runtime.Rt_StdlibEnv
import net.postchain.rell.base.runtime.Rt_StructValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.Rt_ValueClass
import java.util.concurrent.atomic.AtomicLong

/**
 * JVM landing pad for native back-calls out of JIT'd code.
 *
 * The native runtime (`rell_runtime.h` §5/§6) cannot hold Rell objects; whenever JIT'd code hits a
 * stdlib function (any `R_SysFunction` reachable by name), a DbAt/ColAt expression, or an
 * Update/Delete statement that it cannot lower bit-exactly, it calls BACK into the JVM through this
 * object. Native code addresses everything by dense integer id:
 *
 *  - a [Rt_StdlibEnv] sysfn is reached by `sysfnId` — an index into [callEnvOf]`(ctxHandle).sysFns`.
 *  - the live [Rt_Frame] / [Rt_CallContext] is reached by `ctxHandle` — a key into [registry].
 *
 * The id↔name agreement with the native `SysFnTable` is established at compile time: the native
 * lowerer returns its id-ordered name list (see [RellLlvmNative.compileFunctionExtended]); the JVM
 * resolves each name once against [Rt_StdlibEnv.sysFunctions] into the dense [Llvm_CallEnv.sysFns]
 * array. There is no second, independent interning pass on the JVM side — that is what keeps the
 * two id spaces identical without a shared hash.
 */
object Llvm_SysBridge {

    /** Live call environments keyed by the opaque `ctxHandle` native threads as `RellCallCtx`. */
    private val registry = HashMap<Long, Llvm_CallEnv>()
    private val nextHandle = AtomicLong(1L) // 0 reserved for "no ctx" on the native side.

    /**
     * Registers a call environment for the duration of one JIT'd function invocation and returns
     * its opaque handle. [Llvm_Backend.invokeValueNative] pushes one of these around every native
     * call and pops it in a `finally`, so the map never leaks across calls.
     */
    @Synchronized
    fun register(env: Llvm_CallEnv): Long {
        val h = nextHandle.getAndIncrement()
        registry[h] = env
        return h
    }

    @Synchronized
    fun unregister(handle: Long) {
        registry.remove(handle)
    }

    @Synchronized
    private fun callEnvOf(handle: Long): Llvm_CallEnv =
        registry[handle] ?: error("Llvm_SysBridge: no call environment for handle $handle")

    /**
     * Native entry: invoke the stdlib function interned at [sysfnId] with the reboxed [args].
     *
     * Native JNI signature MUST be the Rt_Value-typed descriptor Kotlin actually compiles this to:
     * `dispatch(I[Lnet/postchain/rell/base/runtime/Rt_Value;J)Lnet/postchain/rell/base/runtime/Rt_Value;`
     * (Kotlin does NOT erase `Array<Rt_Value>`/`Rt_Value` to `Object[]`/`Object`). The `args` come
     * boxed as an `Rt_Value[]`, the `ctxHandle` is the call-env handle. The
     * returned `Rt_Value` is unwrapped by `from_jvm` on the native side. Any `Rt_Exception` thrown
     * by the sysfn propagates out across JNI unchanged (the native trampoline aborts on the pending
     * exception — see `rell_runtime.h` §7).
     */
    @JvmStatic
    fun dispatch(sysfnId: Int, args: Array<Rt_Value>, ctxHandle: Long): Rt_Value {
        val env = callEnvOf(ctxHandle)
        val fn = env.sysFns.getOrNull(sysfnId)
            ?: error("Llvm_SysBridge.dispatch: sysfnId $sysfnId out of range (size ${env.sysFns.size})")
        // `args` arrives as Array<Rt_Value>; R_SysFunction.call takes List<Rt_Value>.
        return fn.call(env.callCtx, args.asList())
    }

    /**
     * Builds the dense `R_SysFunction` array the native ids index into, by resolving each native-
     * interned name (id-ordered, from [RellLlvmNative.compileFunctionExtended]) against the
     * compilation-local [Rt_StdlibEnv]. A name absent from the env yields a `null` slot: native
     * code must never have interned an id it cannot reach, so a `null` hit at run time is a hard
     * bug, surfaced by [dispatch] above rather than silently mis-dispatched.
     */
    fun resolveSysFns(stdlib: Rt_StdlibEnv, sysFnNames: List<String>): Array<R_SysFunction?> =
        Array(sysFnNames.size) { i -> stdlib.sysFunctions[sysFnNames[i]] }

    /**
     * Native entry: crack an enum [Rt_Value] to its inline (enum-type index, ordinal) pair, packed
     * into one `long` as `(typeIdx shl 32) or (ordinal and 0xffffffff)`. The native `from_jvm` ENUM
     * case calls this because naming the enum's `def_index` requires the [net.postchain.rell.base.model.rr.RR_App],
     * reached through the call env keyed by [ctxHandle].
     *
     * The ordinal is `Rt_RR_EnumValue.rrAttr.value` — exactly what `rt_ops.kt` `R_CmpType_Enum`
     * compares and what `Rt_RR_EnumValue.equals` keys on (alongside the type name). The typeIdx is
     * `Rt_EnumType.defIndex` (the [net.postchain.rell.base.model.rr.RR_App.allEnums] index), so [enumValue] can rebuild the EXACT
     * enum type on rebox. JNI descriptor: `(Lnet/postchain/rell/base/runtime/Rt_Value;J)J`.
     */
    @JvmStatic
    fun enumCrack(value: Rt_Value, ctxHandle: Long): Long {
        val enum = value as? Rt_RR_EnumValue
            ?: error("Llvm_SysBridge.enumCrack: value is not an enum: ${value::class.java.name}")
        val typeIdx = (enum.type as? Rt_EnumType)?.defIndex
            ?: error("Llvm_SysBridge.enumCrack: enum value's type is not Rt_EnumType: ${enum.type}")
        val ordinal = enum.rrAttr.value
        return (typeIdx.toLong() shl 32) or (ordinal.toLong() and 0xffffffffL)
    }

    /**
     * Native entry: rebuild the canonical enum [Rt_Value] from (enum-type index, ordinal). Reverses
     * [enumCrack]; the native `to_jvm` ENUM case calls this so the round-trip from_jvm∘to_jvm is
     * identity. The call env (keyed by [ctxHandle]) carries the factory that does
     * `resolveType(RR_Type.Enum(typeIdx))` + `allEnums[typeIdx].attrs[ordinal]` — the same
     * construction the interpreter uses for an `RR_ConstantValue.Enum`, so the result is bit-exact.
     * JNI descriptor: `(IIJ)Lnet/postchain/rell/base/runtime/Rt_Value;`.
     */
    @JvmStatic
    fun enumValue(typeIdx: Int, ordinal: Int, ctxHandle: Long): Rt_Value =
        callEnvOf(ctxHandle).enumValue(typeIdx, ordinal)

    /**
     * Native entry: crack a struct [Rt_Value] to its [net.postchain.rell.base.model.rr.RR_App.allStructs] def-index. The native
     * `from_jvm` STRUCT case calls this — naming the def-index needs no [ctxHandle] state (it is read
     * straight off the value's `RR_Type.Struct`), but the handle is threaded for symmetry with
     * [enumCrack] and to validate the value is reachable. Mirrors [enumCrack]: the def-index is the
     * stable identity the native COMPOSITE carries so [structValue] rebuilds the EXACT struct type.
     * JNI descriptor: `(Lnet/postchain/rell/base/runtime/Rt_Value;J)I`.
     */
    @JvmStatic
    fun structDefIndex(value: Rt_Value, ctxHandle: Long): Int {
        val struct = value as? Rt_StructValue
            ?: error("Llvm_SysBridge.structDefIndex: value is not a struct: ${value::class.java.name}")
        val rrType = struct.type.rrType as? RR_Type.Struct
            ?: error("Llvm_SysBridge.structDefIndex: struct value's type is not RR_Type.Struct: ${struct.type.rrType}")
        return rrType.defIndex
    }

    /**
     * Native entry: rebuild the canonical struct [Rt_Value] from (def-index, attribute values).
     * Reverses [structDefIndex] + the per-field recursion; the native `to_jvm` STRUCT case calls this
     * so from_jvm∘to_jvm is identity. The call env (keyed by [ctxHandle]) carries the factory that
     * does `resolveType(RR_Type.Struct(defIndex))` + the def's attribute name list — the same
     * construction the interpreter uses for an `RR_Expr.StructCreate` (`rr_interpreter.kt`), so the
     * result is bit-exact. `attrs` arrive boxed as an `Rt_Value[]` in DECLARED order (matching the
     * native COMPOSITE field order, which from_jvm read via `Rt_StructValue.get(i)`).
     * JNI descriptor: `(I[Lnet/postchain/rell/base/runtime/Rt_Value;J)Lnet/postchain/rell/base/runtime/Rt_Value;`.
     */
    @JvmStatic
    fun structValue(defIndex: Int, attrs: Array<Rt_Value>, ctxHandle: Long): Rt_Value =
        callEnvOf(ctxHandle).structValue(defIndex, attrs.asList())

    /**
     * Native entry: resolve an interned list-literal type id to its JVM [Rt_ValueClass]. The native
     * `rell_make_list` calls this to obtain the `typeRef` for a constructed list — lists are
     * structural (no `allLists` def-index), so the native side cannot name the type itself; it hands
     * back the dense id [listTypeId] (interned positionally by the native `ListTypeTable`, mirrored
     * by [Llvm_Backend]'s pre-order RR walk that collects each `RR_Expr.ListLiteral.type`). The call
     * env (keyed by [ctxHandle]) carries the dense list-type array. JNI descriptor:
     * `(IJ)Lnet/postchain/rell/base/runtime/Rt_ValueClass;`.
     */
    @JvmStatic
    fun listType(listTypeId: Int, ctxHandle: Long): Rt_ValueClass<*> =
        callEnvOf(ctxHandle).listType(listTypeId)

    /**
     * Native entry: rebuild the canonical list [Rt_Value] from (runtime type, element values). The
     * native `to_jvm` LIST case calls this so `from_jvm`∘`to_jvm` is identity. `type` is the list's
     * [Rt_ValueClass] (the `typeRef` the native carrier holds — either captured from the source
     * `Rt_ListValue.getType()` on a from_jvm'd list, or resolved by [listType] for a constructed
     * one). `elems` arrive boxed as an `Rt_Value[]` in ITERATION order (matching the native LIST
     * carrier element order, which from_jvm read via [listGet]). The result is bit-exact with the
     * interpreter's `Rt_ListValue(type, elems)` construction (same runtime type + element order). JNI
     * descriptor: `(Lnet/postchain/rell/base/runtime/Rt_ValueClass;[Lnet/postchain/rell/base/runtime/Rt_Value;J)Lnet/postchain/rell/base/runtime/Rt_Value;`.
     */
    // Invoked from native via JNI (see value.cpp g_listValueMethod / rell_runtime.h §to_jvm).
    @Suppress("unused")
    @JvmStatic
    fun listValue(type: Rt_ValueClass<*>, elems: Array<Rt_Value>, ctxHandle: Long): Rt_Value {
        callEnvOf(ctxHandle)  // validate the handle is live (the type is already the carrier's typeRef).
        return Rt_ListValue(type, elems.toMutableList())
    }

    /**
     * Native entry: number of elements in a list [Rt_Value] (`Rt_ListValue.elements.size`). The
     * native `from_jvm` LIST case calls this to size the carrier. JNI descriptor:
     * `(Lnet/postchain/rell/base/runtime/Rt_Value;)I`.
     */
    // Invoked from native via JNI (see value.cpp g_listSizeMethod / rell_runtime.h §to_jvm).
    @Suppress("unused")
    @JvmStatic
    fun listSize(value: Rt_Value): Int {
        val list = value as? Rt_ListValue
            ?: error("Llvm_SysBridge.listSize: value is not a list: ${value::class.java.name}")
        return list.elements.size
    }

    /**
     * Native entry: the `i`-th element of a list [Rt_Value] (`Rt_ListValue.elements[i]`). The native
     * `from_jvm` LIST case calls this per element (recursively from_jvm'd). The index is always in
     * bounds (`from_jvm` iterates `0 until listSize`). JNI descriptor:
     * `(Lnet/postchain/rell/base/runtime/Rt_Value;I)Lnet/postchain/rell/base/runtime/Rt_Value;`.
     */
    @JvmStatic
    fun listGet(value: Rt_Value, i: Int): Rt_Value {
        val list = value as? Rt_ListValue
            ?: error("Llvm_SysBridge.listGet: value is not a list: ${value::class.java.name}")
        return list.elements[i]
    }

    /**
     * Native entry: rebuild the canonical byte_array [Rt_Value] from a `byte[]`. The native `to_jvm`
     * BYTEARRAY case calls this so `from_jvm`∘`to_jvm` is content-identity. [Rt_ByteArrayValue.get]
     * canonicalises the empty array to the `EMPTY` singleton — the same construction the interpreter
     * uses for `RR_ConstantValue.ByteArray` / `R_BinaryOp_Concat_ByteArray` (`rt_ops.kt`) — so the
     * result is bit-exact (`Rt_ByteArrayValue.equals` == `contentEquals`). No `ctxHandle` is needed:
     * byte_array is a pure value type. JNI descriptor: `([B)Lnet/postchain/rell/base/runtime/Rt_Value;`.
     */
    // Invoked from native via JNI (see value.cpp g_byteArrayValueMethod / rell_runtime.h §to_jvm).
    @Suppress("unused")
    @JvmStatic
    fun byteArrayValue(bytes: ByteArray): Rt_Value = Rt_ByteArrayValue.get(bytes)

    /**
     * Native entry: rebuild the canonical text [Rt_Value] from a [String]. The native `to_jvm` TEXT
     * case calls this so `from_jvm`∘`to_jvm` is content-identity. [Rt_TextValue.get] canonicalises the
     * empty string to the `EMPTY` singleton — the same construction the interpreter uses for
     * `RR_ConstantValue.Text` / `R_BinaryOp_Concat_Text` / `TextSubscript` (`rt_ops.kt` /
     * `rr_interpreter.kt`) — so the result is bit-exact (`Rt_TextValue.equals` == String content
     * equality). The native side passes the String built by `NewString` from the carrier's UTF-16 code
     * units, so surrogate pairs (and lone surrogates from a subscript) survive exactly. No `ctxHandle`
     * is needed: text is a pure value type. JNI descriptor:
     * `(Ljava/lang/String;)Lnet/postchain/rell/base/runtime/Rt_Value;`.
     */
    // Invoked from native via JNI (see value.cpp g_textValueMethod / rell_runtime.h §to_jvm).
    @Suppress("unused")
    @JvmStatic
    fun textValue(s: String): Rt_Value = Rt_TextValue.get(s)

    /**
     * Native entry: build the canonical decimal [Rt_Value] from its plain string representation. The
     * native `rell_make_decimal` (a decimal CONSTANT materialiser) calls this, then cracks the result
     * back through `from_jvm` — so the inline DEC_LONG it yields is the interpreter's EXACT stripped
     * normal form (or a wide HANDLE), with NO C++ re-derivation of `Lib_DecimalMath.scale` /
     * `BigDecimal.stripTrailingZeros`. [Rt_DecimalValue.get] is the same construction the interpreter
     * uses for `RR_ConstantValue.Decimal` (`rt_type.kt`), so the value is bit-exact. The string is the
     * range-validated, canonical form the tokenizer serialised (`tokenizer.kt` `scaleDecimal`), so the
     * `BigDecimal(s)` parse never throws. No `ctxHandle` is needed: decimal is a pure value type. JNI
     * descriptor: `(Ljava/lang/String;)Lnet/postchain/rell/base/runtime/Rt_Value;`.
     */
    // Invoked from native via JNI (see value.cpp g_decimalValueMethod / rell_runtime.h §rell_make_decimal).
    @Suppress("unused")
    @JvmStatic
    fun decimalValue(s: String): Rt_Value = Rt_DecimalValue.get(java.math.BigDecimal(s))
}

/**
 * Per-invocation call environment the native side reaches by `ctxHandle`. Wraps the live
 * [Rt_CallContext] (the JVM frame/exec context for the running JIT'd function) and the dense
 * sysfn dispatch table for the function being run.
 *
 * `frameHandle` in `rell_runtime.h` §6 == the handle this is registered under; this class is the
 * "Llvm_CallEnv wrapping the live Rt_Frame" referenced there.
 */
class Llvm_CallEnv(
    val callCtx: Rt_CallContext,
    val sysFns: Array<R_SysFunction?>,
    /**
     * Rebuilds the canonical enum [Rt_Value] from (enum-type index, ordinal) — used by the native
     * ENUM rebox ([Llvm_SysBridge.enumValue]). Built by [Llvm_Backend] from its [net.postchain.rell.base.model.rr.RR_App] +
     * `resolveType`, the same construction the interpreter uses for an `RR_ConstantValue.Enum`.
     */
    val enumValue: (typeIdx: Int, ordinal: Int) -> Rt_Value,
    /**
     * Rebuilds the canonical struct [Rt_Value] from (def-index, attribute values) — used by the
     * native STRUCT rebox ([Llvm_SysBridge.structValue]). Built by [Llvm_Backend] from its
     * [net.postchain.rell.base.model.rr.RR_App] + `resolveType`, the same construction the
     * interpreter uses for an `RR_Expr.StructCreate` (attribute name list + values).
     */
    val structValue: (defIndex: Int, attrs: List<Rt_Value>) -> Rt_Value,
    /**
     * Resolves an interned list-literal type id to its [Rt_ValueClass] — used by the native list
     * construction ([Llvm_SysBridge.listType]). Built by [Llvm_Backend] from the dense, walk-order
     * list-type array it collects from the function body's `RR_Expr.ListLiteral.type` nodes (the
     * JVM mirror of the native `ListTypeTable` walk), each resolved via `resolveType(RR_Type.List)`.
     */
    val listType: (listTypeId: Int) -> Rt_ValueClass<*>,
)
