// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// intrinsics_math.cpp — the generic math-family overlay (`abs` / `min` / `max` / `sign`
// across integer / decimal / big_integer).
//
// This is a THIN DISPATCHER. The math stdlib functions are reachable from the RR-tree as
// SysMember/SysGlobal targets, so the universal JNI stdlib caller already covers 100% of
// them correctly. This file overlays a FAST PATH for the narrow slice that can be lowered
// to pure i64 IR with bit-exact JVM-equivalent semantics; everything else signals
// `*escaped = true` and the caller (lower_ops.cpp / lower_call.cpp) emits the
// `rell_sysfn_call` slow path that runs the real `R_SysFunction` on the JVM.
//
// The entry point is `intrinsicMath(ec, fn, args, escaped)` declared in rell_runtime.h.
// `fn` is the interned SysFnId; `args` are runtime-value SSAs of `rellValueLlvmType()`
// (`{ i8 tag, i32 scale, i64 payload }`); they are NOT yet proven in-envelope.
//
// CORRECTNESS RULE (consensus-critical): this overlay never throws and never emits an
// exception-bearing path. The only Rell-math operations whose envelope is statically
// provable from the call's key — with no possible HANDLE operand, no overflow trap, and
// no JVM exception — are inlined. Anything that could (a) carry a HANDLE-tagged operand at
// runtime, (b) overflow into a JVM Rt_Exception, or (c) require scale alignment that could
// overflow, ESCAPES to the JVM. When in doubt: escape.

#include "rell_runtime.h"

#include <string>
#include <string_view>

namespace rell::llvm_rt {

namespace {

// =====================================================================================
// SysFn-key decoding.
//
// The interned name produced by the resolver (rr_ir_resolve.kt buildSysFnKey) has the shape
//
//     "<fullName>#<M|G>#(<argSig>)-><resultSig>[@<file>:<line>]"
//
// where <fullName> ends in the simple function name (e.g. "abs", "integer.min", ...),
// <argSig> is a comma list of `R_Type.strCode()` tokens, and <resultSig> is the result
// type's strCode(). For the primitive math leaves strCode() is exactly "integer",
// "decimal", or "big_integer".
//
// We classify by parsing the key. This is content-addressed and identical on every JVM, so
// it is deterministic. We deliberately recognise ONLY the exact families we can prove; any
// shape we don't fully understand falls through to escape (never a wrong inline).
// =====================================================================================

// The simple function name = the segment of <fullName> after the last '.', truncated at the
// first '#'. Returns empty view if the key is malformed (→ caller escapes).
std::string_view sysFnSimpleName(std::string_view key) {
    const std::size_t hash = key.find('#');
    if (hash == std::string_view::npos) return {};
    std::string_view fullName = key.substr(0, hash);
    const std::size_t dot = fullName.rfind('.');
    return dot == std::string_view::npos ? fullName : fullName.substr(dot + 1);
}

// The result-type strCode token: the substring after "->" up to an optional "@<pos>" tail.
// Empty if the key lacks the "->" marker (→ caller escapes).
std::string_view sysFnResultType(std::string_view key) {
    const std::size_t arrow = key.rfind("->");
    if (arrow == std::string_view::npos) return {};
    std::string_view tail = key.substr(arrow + 2);
    const std::size_t at = tail.find('@');
    if (at != std::string_view::npos) tail = tail.substr(0, at);
    return tail;
}

// Look up the interned name for a SysFnId. Out-of-range / unknown → empty (→ escape).
std::string_view sysFnName(EmitContext &ec, SysFnId fn) {
    if (fn < 0) return {};
    const auto &names = ec.sysFns().names();
    if (static_cast<std::size_t>(fn) >= names.size()) return {};
    return names[static_cast<std::size_t>(fn)];
}

// =====================================================================================
// i64-payload helpers. The math inline slice operates purely on the INTEGER payload, so we
// extract field 2 of each runtime-value SSA, compute in i64, and repack via packInteger().
//
// IMPORTANT: extracting the payload does NOT validate the tag. We only ever do this on the
// integer leaf, where the Rell static type is `integer` and therefore the runtime tag is
// ALWAYS RellTag::INTEGER (integer never escapes to a HANDLE — see rell_runtime.h §1/§2).
// That static guarantee is what lets us skip a runtime tag branch here. For decimal /
// big_integer the operand can be DEC_LONG / BIGINT_LONG *or* HANDLE at runtime, so those
// leaves are not inlined in this file (they escape).
// =====================================================================================

llvm::Value *payload(EmitContext &ec, llvm::Value *runtimeValue) {
    return ec.unpackPayloadI64(runtimeValue);
}

// abs over the integer leaf. NOT inlined: Lib_Math.Abs_Integer throws Rt_Exception on
// Long.MIN_VALUE, and we cannot raise that from pure IR without a JVM back-call. We have no
// static proof the operand is non-MIN, so we escape to keep the overflow behaviour
// bit-exact. (Kept as a named predicate so the dispatch table reads clearly.)
//
// DETERMINISM: integer `abs(Long.MIN_VALUE)` must raise "Integer overflow" exactly as the
// interpreter (lib_math.kt). Pure IR cannot, so abs always routes through the JVM.
constexpr bool kIntegerAbsInlineable = false;

}  // namespace

// =====================================================================================
// Entry point.
//
// Recognises the math family from the interned key and emits the inline fast path for the
// statically-provable, exception-free, HANDLE-free slice (integer min / max / sign). All
// other families/leaves set *escaped and return nullptr so the caller emits the universal
// JNI stdlib call (which is always correct).
// =====================================================================================
llvm::Value *intrinsicMath(EmitContext &ec, SysFnId fn, const llvm::ArrayRef<llvm::Value *> args,
                           bool *escaped) {
    *escaped = false;

    const std::string_view key = sysFnName(ec, fn);
    if (key.empty()) {
        *escaped = true;  // unknown / un-interned id — let the caller route to JNI.
        return nullptr;
    }

    const std::string_view name = sysFnSimpleName(key);
    const std::string_view resultType = sysFnResultType(key);

    // The only leaf we inline is `integer` (result type strCode == "integer"). For that leaf
    // every operand is statically `integer` → runtime tag is always INTEGER, so unpacking the
    // i64 payload is safe with no tag branch, and none of {min,max,sign} can throw.
    //
    // DETERMINISM: decimal/big_integer leaves can carry a HANDLE operand at runtime (out-of-
    // envelope value) and min/max would need possibly-overflowing scale alignment, so they are
    // NOT inlined here — they escape to the JVM R_SysFunction for a bit-exact result.
    if (resultType != "integer") {
        *escaped = true;
        return nullptr;
    }

    auto &b = ec.builder();

    if (name == "sign") {
        // Unary: sign(a) = (a > 0) - (a < 0), i.e. -1 / 0 / +1. Matches
        // `self.value.sign.toLong()` in lib_type_integer.kt exactly — no overflow, no
        // exception (sign over Long is total).
        if (args.size() != 1) {
            *escaped = true;
            return nullptr;
        }
        llvm::Value *a = payload(ec, args[0]);
        llvm::Value *zero = b.getInt64(0);
        llvm::Value *gt = b.CreateICmpSGT(a, zero, "sign_gt");
        llvm::Value *lt = b.CreateICmpSLT(a, zero, "sign_lt");
        llvm::Value *gtI = b.CreateZExt(gt, b.getInt64Ty(), "sign_gt_i64");
        llvm::Value *ltI = b.CreateZExt(lt, b.getInt64Ty(), "sign_lt_i64");
        llvm::Value *res = b.CreateSub(gtI, ltI, "sign");
        return ec.packInteger(res);
    }

    if (name == "min" || name == "max") {
        // Binary: pure signed select. min/max over Long are total (no overflow, no exception),
        // matching kotlin.math.min/max in Lib_Math.Min_Integer / Max_Integer exactly.
        //
        // NB: the integer-leaf min/max overloads whose OTHER argument is big_integer/decimal
        // have a non-"integer" result type (they widen) and so were already escaped above;
        // only the both-integer overload reaches here.
        if (args.size() != 2) {
            *escaped = true;
            return nullptr;
        }
        llvm::Value *a = payload(ec, args[0]);
        llvm::Value *c = payload(ec, args[1]);
        llvm::Value *pred = (name == "min") ? b.CreateICmpSLE(a, c, "min_cmp")
                                            : b.CreateICmpSGE(a, c, "max_cmp");
        llvm::Value *res = b.CreateSelect(pred, a, c, name == "min" ? "min" : "max");
        return ec.packInteger(res);
    }

    if (name == "abs") {
        // DETERMINISM: integer abs must throw on Long.MIN_VALUE; pure IR can't, so escape.
        if (!kIntegerAbsInlineable) {
            *escaped = true;
            return nullptr;
        }
    }

    // Not a recognised math family (or an arity we don't inline) → route to the JVM.
    *escaped = true;
    return nullptr;
}

}  // namespace rell::llvm_rt
