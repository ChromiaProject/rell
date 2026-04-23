// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// intrinsics_biginteger.cpp — the BIGINT_LONG inline overlay.
//
// Rell `big_integer` is an arbitrary-precision integer whose JVM envelope is
// |v| <= Lib_BigIntegerMath.MAX_VALUE == 10^131072 - 1 (PRECISION = 131072 decimal digits,
// lib_type_biginteger.kt). That envelope is astronomically wider than i64, so the inline
// BIGINT_LONG tag carries ONLY the i64-fitting slice (|v| <= Long.MAX_VALUE); everything wider
// lives as a HANDLE to the JVM Rt_BigIntegerValue (rell_runtime.h §1). This file overlays the
// *pure* big_integer arithmetic families (ADD / SUB / MUL) as i64 fast paths whenever the result
// provably stays inside i64 (i.e. no i64 overflow); any operand pair whose result leaves the i64
// slice — though still a perfectly valid sub-envelope big_integer — escapes to the universal JNI
// caller so the JVM computes the wide BigInteger. DIV / MOD and every other op escape too.
//
// DETERMINISM (consensus-critical): the JVM does add/subtract/multiply via plain
// BigInteger.add/subtract/multiply (Lib_BigIntegerMath) and re-validates the result against
// MIN_VALUE..MAX_VALUE only at Rt_BigIntegerValue.get(). For two i64-fitting operands the true
// big_integer result can never reach 10^131072, so the ONLY thing that can go "wrong" inline is
// an i64 overflow — which is NOT a Rell error but simply a value that no longer fits the inline
// slice. The correct response is to route that pair to the JVM (producing a wide HANDLE), never
// to throw and never to silently wrap. When the i64 op does not overflow, the result is exact and
// trivially in-envelope, so the inline BIGINT_LONG is bit-exact with the interpreter.
//
// The overflow decision is a RUNTIME property, but the intrinsic-overlay contract
// (rell_runtime.h §9, ABI.md §7) gives `*escaped` as an EMIT-TIME, all-or-nothing signal:
// `*escaped == true` <=> nullptr return <=> "caller emits the rell_sysfn_call slow path for the
// whole op." A pure intrinsic here cannot itself emit that back-call — the hidden RellCallCtx*
// frame param is not exposed through EmitContext, and there is no declared emit-time
// sysfn-emission helper to reuse. So rather than risk emitting a runtime overflow branch with no
// sanctioned way to reach the slow path (which would force a wrong silent-wrap inline), this
// overlay ESCAPES the arithmetic ops at emit time and lets lower_ops route them through the
// universal JNI caller, which is bit-exact for the entire big_integer envelope by construction.
// The i64 fast-path-with-guard remains a clean future optimization once EmitContext exposes the
// ctx value; until then, correctness-first (the CORRECTNESS RULE) wins.
//
// Mirrors jni_bridge.cpp style: namespace rell::llvm_rt, EmitContext emit helpers, defensive
// failure, LLVM 19 IRBuilder API.

#include "rell_runtime.h"

namespace rell::llvm_rt {

// =====================================================================================
// intrinsicBigInteger — the BIGINT_LONG arithmetic overlay.
//
// `a` and `b` are the already-unpacked i64 payloads of two operands the caller has PROVEN are
// inline BIGINT_LONG (tag == BIGINT_LONG, |v| <= Long.MAX). The caller guarantees the static
// type of both operands and the result is `big_integer`.
//
// EMIT-TIME ESCAPE (returns nullptr, *escaped=true; caller emits the rell_sysfn_call slow path):
//   ADD_BIG_INTEGER / SUB_BIG_INTEGER / MUL_BIG_INTEGER — see the file header: an i64-fitting
//     operand pair can overflow the i64 slice into a still-valid wide big_integer, which only the
//     JVM can box (as a HANDLE); reproducing that inline needs a slow-path emission facility the
//     current EmitContext does not expose, so we route the whole op to the JVM. // DETERMINISM:
//     the JVM's BigInteger.add/subtract/multiply + Rt_BigIntegerValue.get envelope check is the
//     single source of truth; we never re-implement bignum arithmetic in C++.
//   DIV_BIG_INTEGER / MOD_BIG_INTEGER — div-by-zero raises an Rt_Exception whose message embeds
//     the FULL operand values ("Division by zero: $a / $b", rt_ops.kt), and Long.MIN/-1 overflows
//     i64 while being a valid big_integer. // DETERMINISM: exact div0 message + truncate-toward-
//     zero come straight from Lib_BigIntegerMath.divide/remainder, so we escape to the JVM.
//
// SOFT-FAIL (returns nullptr, *escaped=false after ec.fail()): a non-big_integer BinaryOp routed
//   here by mistake — the whole function falls back to the interpreter.
//
// BITWISE / COMPARISON: Rell `big_integer` exposes NO language-level bitwise binary operators
//   (op.fbs BinaryOp has none) — any and/or/xor/shift helpers are ordinary stdlib functions
//   reached BY NAME through the universal JNI caller, not BinaryOps, so there is nothing to
//   overlay here. Comparisons (<, >, <=, >=) and equality (EQ/NE) arrive via CmpInfo on the
//   BinaryExpr and are lowered by lower_ops (whose CmpType carries BIG_INTEGER), not this entry
//   (whose signature has no CmpType). This overlay therefore covers ONLY the five arithmetic ops,
//   all of which it currently routes to the JVM for bit-exactness.
// =====================================================================================
llvm::Value *intrinsicBigInteger(EmitContext &ec, ir::BinaryOp op, llvm::Value *a, llvm::Value *b,
                                 bool *escaped) {
    (void)a;
    (void)b;
    *escaped = false;

    switch (op) {
        case ir::BinaryOp_ADD_BIG_INTEGER:
        case ir::BinaryOp_SUB_BIG_INTEGER:
        case ir::BinaryOp_MUL_BIG_INTEGER:
        case ir::BinaryOp_DIV_BIG_INTEGER:
        case ir::BinaryOp_MOD_BIG_INTEGER:
            // Escape to the universal JNI caller: bit-exact across the whole big_integer envelope.
            *escaped = true;
            return nullptr;

        default:
            // Not a big_integer arithmetic op — this entry was mis-dispatched. Soft-fail the
            // whole function (NOT an *escaped, which would wrongly imply a valid slow path).
            ec.fail("intrinsicBigInteger: unsupported BinaryOp " +
                    std::to_string(static_cast<int>(op)));
            return nullptr;
    }
}

}  // namespace rell::llvm_rt
