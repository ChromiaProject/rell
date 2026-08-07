// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// intrinsics_bytearray.cpp — the byte_array intrinsic overlay for the Rell LLVM backend.
//
// SCOPE OF THIS FILE
// ------------------
// byte_array is a HEAP type in the inline value lattice (rell_runtime.h §1): every byte_array
// value flows through JIT'd code as a HANDLE (RellTag::HANDLE) — an arena-owned JNI global ref
// to a JVM Rt_ByteArrayValue. There is NO inline (i64-payload) representation of the bytes
// themselves: the C++ side never owns, reads, or mutates the underlying buffer, it only shuffles
// opaque handles. That single ABI fact is what shapes this whole overlay.
//
// The task framing is "bytes are simple — maximize the inline set." They are simple to DESCRIBE,
// but on this committed ABI there is no native buffer to compute on, so "inline" here would mean
// reaching back through JNI into the JVM Rt_ByteArrayValue for every byte access — which is
// exactly what the universal JNI stdlib caller (rell_sysfn_call) already does, deterministically,
// against the single source of truth. A hand-rolled C++ fast path would have to re-derive the
// buffer pointer anyway and then duplicate consensus-critical edge cases (range errors, size
// envelopes, encoding alphabets). So the correct, deterministic maximum for this family is to
// route uniformly to the JVM, and to document that routing precisely in one place — this file.
//
// WHERE EACH byte_array OPERATION ACTUALLY GETS LOWERED (none of these reach an "inline" path):
//
//   * a + b  (CONCAT_BYTE_ARRAY) ........ this overlay's binary entry → escape → rell_sysfn_call.
//   * a == b / a != b .................... BinaryOp_EQ / _NE in lower_ops.cpp (NOT this overlay);
//                                          handle identity is not value identity, so it must defer
//                                          to the JVM equals — see the EQUALITY note below.
//   * a < b / <= / > / >= ................ CmpInfo path (cmp_type == BYTE_ARRAY) in lower_ops.cpp,
//                                          lexicographic-unsigned compare over the JVM buffers →
//                                          JNI; never a plain BinaryOp here.
//   * a[i]  (subscript / .get) ........... ByteArraySubscriptExpr in lower_expr.cpp → JNI.
//   * .size() / .empty() ................. MemberExpr + MemberCalculator_SysFunction → lower_call /
//                                          lower_member → rell_sysfn_call by name.
//   * .sub(start) / .sub(start, end) ..... MemberCalculator_SysFunction → rell_sysfn_call.
//   * .to_hex() / .to_base64() ........... MemberCalculator_SysFunction → rell_sysfn_call.
//   * byte_array.from_hex(text) /
//     byte_array.from_base64(text) ....... FnTarget_SysGlobal/SysMember → rell_sysfn_call.
//   * .repeat(n) / .reversed() /
//     .to_list() / .decode() / .sha256() / .hash() ... MemberCalculator_SysFunction → JNI.
//
// So the ONLY node that structurally lands in this file is CONCAT_BYTE_ARRAY (the binary `+`),
// plus any other BinaryOp that erroneously reaches a byte_array operand (a lowering bug upstream).
// Both are escaped. This overlay is, by design, an all-escape overlay — the correct floor.
//
// Style mirrors jni_bridge.cpp / rell_runtime.h: `namespace ir = rell::ir;`, the escaped/soft-
// fail signalling contract from §9 of the header, defensive null-checks, LLVM 19+ IRBuilder.

#include "rell_runtime.h"

namespace rell::llvm_rt {

namespace {

// True iff `type` is the primitive byte_array type. Mirrors jni_bridge's isIntegerType gate,
// retargeted to BYTE_ARRAY. Kept local so the type gate for this family lives next to its
// overlay; call-site lowering (lower_ops / lower_call) uses its own copy on the operand types.
[[maybe_unused]] bool isByteArrayType(const ir::Type *type) {
    if (type == nullptr) return false;
    const auto *prim = type->type_as_PrimitiveType();
    return prim != nullptr && prim->kind() == ir::PrimitiveTypeKind_BYTE_ARRAY;
}

}  // namespace

// Binary-op overlay for byte_array operands. The lower_ops.cpp BinaryExpr dispatcher hands us the
// two already-lowered runtime-value operands (each an llvm::Value* of rellValueLlvmType(),
// carrying a HANDLE for byte_array) and the op. Contract (rell_runtime.h §9): on success return
// the result runtime value; to defer, set `*escaped = true` and return nullptr; `*escaped` is
// never set true together with a non-null return. A genuine lowering bug (null operand / missing
// sink) is a function-level soft-fail (ec.failExpr), NOT an escape.
//
// For byte_array, the only arithmetic-shaped BinaryOp that lands here is CONCAT_BYTE_ARRAY. We
// escape it (and, defensively, any other op) to the universal JNI caller rather than synthesizing
// a native concat — there is no inline payload to splice and the semantics must stay bit-exact.
llvm::Value *intrinsicByteArray(EmitContext &ec, ir::BinaryOp op, llvm::Value *a, llvm::Value *b,
                                bool *escaped) {
    // A null escaped sink is an upstream contract violation, not an envelope miss: soft-fail the
    // whole function rather than silently mis-route.
    if (escaped == nullptr) {
        return ec.failExpr("intrinsicByteArray: null escaped sink");
    }
    // Null operands likewise indicate a broken lower_ops hand-off. Do NOT escape (escaping implies
    // "valid operands, just out of envelope"); soft-fail so the JVM interpreter runs the function.
    if (a == nullptr || b == nullptr) {
        *escaped = false;
        return ec.failExpr("intrinsicByteArray: null operand");
    }

    switch (op) {
        case ir::BinaryOp_CONCAT_BYTE_ARRAY: {
            // NATIVE concat on the BYTEARRAY carriers (value.cpp from_jvm cracks every byte_array to a
            // BYTEARRAY, so both operands carry a native buffer). rell_bytearray_concat splices a fresh
            // arena buffer (a-bytes ++ b-bytes) and returns the result. Bit-exact with rt_ops.kt
            // R_BinaryOp_Concat_ByteArray (Rt_ByteArrayValue.get(a.value + b.value)); the `+` operator
            // has NO size envelope (size constraints are a struct/entity-attribute concern), so a pure
            // native splice reproduces it exactly. NOT escaped.
            *escaped = false;
            llvm::IRBuilder<> &ib = ec.builder();
            auto *ptrTy = llvm::PointerType::getUnqual(ec.ctx());
            llvm::StructType *valTy = ec.valueType();

            llvm::Function *fn = ib.GetInsertBlock()->getParent();
            llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
            llvm::Value *aSlot = entryB.CreateAlloca(valTy, nullptr, "ba_cat_a");
            llvm::Value *bSlot = entryB.CreateAlloca(valTy, nullptr, "ba_cat_b");
            llvm::Value *outSlot = entryB.CreateAlloca(valTy, nullptr, "ba_cat_out");
            ib.CreateStore(a, aSlot);
            ib.CreateStore(b, bSlot);

            // The hidden trailing ctx pointer (last arg) — rell_bytearray_concat allocates in its arena.
            llvm::Value *ctx = nullptr;
            if (!fn->arg_empty()) ctx = fn->getArg(fn->arg_size() - 1);
            if (ctx == nullptr) {
                *escaped = false;
                return ec.failExpr("intrinsicByteArray concat: no enclosing function ctx arg");
            }

            // void rell_bytearray_concat(ptr out, ptr ctx, ptr a, ptr b)
            auto *fnTy = llvm::FunctionType::get(llvm::Type::getVoidTy(ec.ctx()),
                                                 {ptrTy, ptrTy, ptrTy, ptrTy}, /*isVarArg=*/false);
            llvm::FunctionCallee callee =
                ec.module().getOrInsertFunction("rell_bytearray_concat", fnTy);
            ib.CreateCall(callee, {outSlot, ctx, aSlot, bSlot});
            return ib.CreateLoad(valTy, outSlot, "ba_cat");
        }

        default:
            // Comparisons (==, !=, <, <=, >, >=) on byte_array do NOT arrive here: equality is
            // BinaryOp_EQ/_NE handled in lower_ops.cpp, ordering goes through the CmpInfo path
            // (cmp_type == BYTE_ARRAY). Any other BinaryOp reaching a byte_array operand is
            // unexpected.
            //
            // EQUALITY / ORDERING DETERMINISM (for the lower_ops paths that own those ops, noted
            // here so the byte_array story is complete): two byte_array HANDLEs are distinct JNI
            // global refs even when their buffers are byte-identical, so handle-pointer comparison
            // is NOT value equality, and there is no native buffer to compare. Both == and the
            // lexicographic-unsigned ordering must defer to the JVM (Arrays.equals / unsigned
            // compare). Never fold a byte_array comparison from handle identity.
            //
            // DETERMINISM: never emit an approximate native op for an unrecognized byte_array
            // binary op — route to the JVM, which owns the canonical semantics.
            *escaped = true;
            return nullptr;
    }
}

}  // namespace rell::llvm_rt
