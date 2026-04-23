// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// intrinsics_text.cpp — text intrinsic overlay for the Rell LLVM backend.
//
// This file implements intrinsicText(), the entry lower_ops.cpp calls for a BinaryExpr whose
// operands are `text`. The contract (rell_runtime.h §9) is: produce an in-envelope result
// inline, OR set *escaped = true so the caller emits the rell_sysfn_call slow path. *escaped
// is never set true together with a non-null return.
//
// =====================================================================================
// UNICODE / TEXT-SEMANTICS DECISION (the load-bearing determinism analysis)
// =====================================================================================
//
// Rell `text` is a JVM java.lang.String. Every text stdlib function in lib_type_text.kt
// operates on that String with plain Java/Kotlin String semantics, i.e. UTF-16 *code units*,
// NOT Unicode code points and NOT bytes:
//
//   - size()        -> String.length              (UTF-16 code-unit count)
//   - empty()       -> String.isEmpty()           (length == 0)
//   - char_at(i)    -> s[i].code  (16-bit char), bounds on s.length, error code
//                      "fn:text.char_at:index:<len>:<i>"
//   - sub(a[,b])    -> String.substring(a,b), bounds start in 0..len & end in start..len,
//                      error code "fn:text.sub:range:<len>:<a>:<b>"
//   - contains(t)   -> String.contains(t)
//   - starts_with(p)-> String.startsWith(p)
//   - ends_with(s)  -> String.endsWith(s)
//   - index_of / last_index_of / replace / upper_case / ... -> the matching String method.
//
// CONCAT_TEXT (the one BinaryOp that reaches this overlay) is rt_ops.kt:
//   Rt_TextValue.get(left.value + right.value)  -- plain java.lang.String concatenation.
//
// In native code a `text` value is ALWAYS carried as RellTag::HANDLE: an arena-owned JNI
// global ref to the JVM Rt_TextValue (the marshalling layer in value.cpp keeps text opaque by
// design — from_jvm/to_jvm never unwrap a String, and HANDLE is the universal escape for all
// heap/string types). So this overlay's operands `a`/`b` are runtime-value SSA ({i8,i32,i64})
// whose tag is HANDLE; the i64 payload is a jobject pointer, never a String the IR can index.
//
// Could we still do these inline? Mechanically yes for some (concat, size, empty are
// unambiguous on a String). But to do so the JIT'd code would have to call BACK into the JVM
// anyway — GetStringChars / GetStringLength / NewString / Rt_TextValue.get — i.e. the same JNI
// round-trip the universal caller already performs, with ZERO arithmetic win and with a real
// determinism hazard:
//
//   * char_at/sub/index_of must reproduce, bit-exactly, the JVM bounds checks AND the exact
//     Rt_Exception error-CODE strings ("fn:text.char_at:index:..", "fn:text.sub:range:..").
//     Re-emitting those in C++ is duplicated, drift-prone consensus logic. DETERMINISM: any
//     divergence in an error code or boundary is a consensus split. Route to the JVM.
//   * contains/starts_with/ends_with/index_of must match String's UTF-16 matching exactly,
//     including the empty-needle and surrogate-pair edge cases. Re-deriving them in C++ risks
//     code-unit vs code-point divergence. DETERMINISM: defer to java.lang.String.
//   * Reading the String out of a Rt_TextValue handle needs the Rt_TextValue backing-field
//     plumbing that value.cpp deliberately does NOT expose (text is opaque). Re-creating it
//     here would fork marshalling state.
//
// CONCLUSION: there is no text operation that is BOTH (a) cheaper than a single JNI back-call
// AND (b) provably bit-exact without re-implementing java.lang.String. So the entire text
// family routes through the universal JNI stdlib caller:
//
//   - BinaryExpr CONCAT_TEXT  -> intrinsicText() escapes -> lower_ops emits rell_sysfn_call.
//   - All member functions (size/empty/char_at/sub/contains/starts_with/ends_with/index_of/
//     last_index_of/replace/repeat/reversed/upper_case/lower_case/to_bytes/format/...) arrive
//     as MemberCalculator_SysFunction / FnTarget_SysMember and are handled in lower_call.cpp
//     via rell_sysfn_call — they never reach this overlay.
//
// This is the correctness floor working as intended: 100% of text is reachable (through the
// JVM), nothing is mis-lowered, and there is exactly one source of truth for String semantics
// (the JVM stdlib). If a future change makes text length available inline in the tag (e.g. a
// cached code-unit count carried alongside the handle), `empty`/`size` become candidates for a
// true zero-JNI inline; until then they correctly escape.

#include "rell_runtime.h"

namespace rell::llvm_rt {

// The text intrinsic overlay. Operands `a`/`b` are runtime-value SSA ({i8,i32,i64}); for text
// they always carry RellTag::HANDLE. There is no text op we can compute inline more cheaply
// than, or more correctly than, the JVM (see the file header). So we ALWAYS escape: signal the
// caller to emit the rell_sysfn_call slow path, and return nullptr without emitting any IR.
//
// DETERMINISM: emitting no IR here is the whole point — every text result then comes from
// java.lang.String via the universal caller, bit-exact with the interpreter. We never fail()
// (that would soft-fail the WHOLE function and force the interpreter even for the integer parts
// around the text op); escaping keeps the rest of the function JITable while the text op alone
// goes through JNI.
llvm::Value *intrinsicText(EmitContext &ec, ir::BinaryOp op, llvm::Value *a, llvm::Value *b,
                           bool *escaped) {
    (void)ec;
    (void)a;
    (void)b;

    // The only BinaryOp that ever reaches the text overlay is CONCAT_TEXT (rt_ops.kt
    // "R_BinaryOp_Concat_Text" -> Rt_TextValue.get(left.value + right.value)). Equality
    // (EQ/NE) and comparisons (CmpInfo with cmp_type == TEXT) are dispatched elsewhere by
    // lower_ops and likewise routed to the JVM. Anything else arriving here is a caller bug,
    // but the safe response is identical: escape to the universal caller, never mis-lower.
    switch (op) {
        case ir::BinaryOp_CONCAT_TEXT:
            // DETERMINISM: java.lang.String concatenation is unambiguous, but materializing it
            // needs a JNI round-trip anyway (GetStringChars + NewString + Rt_TextValue.get) with
            // no compute win, and the result must re-enter the arena as a HANDLE. Route through
            // rell_sysfn_call so the JVM stdlib owns the operation.
            *escaped = true;
            return nullptr;

        default:
            // Any non-concat text BinaryOp (should not occur — text equality/comparison do not
            // come through here) is conservatively escaped, never approximated.
            *escaped = true;
            return nullptr;
    }
}

}  // namespace rell::llvm_rt
