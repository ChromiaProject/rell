// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// lower_call.cpp — lowering for FunctionCallExpr / MemberExpr in the Rell LLVM backend.
//
// SCOPE OF THIS FILE
// ------------------
// This file owns the call site. It translates the two call-bearing Expr variants —
// FunctionCallExpr (an explicit `f(args)`) and MemberExpr (a `base.member` access, possibly
// itself a member call) — into either:
//
//   (a) a DIRECT native call to a JIT'd user callee (FnTarget_RegularUser / _RegularQuery),
//       linked through the ORC layer by the callee's mangled symbol name; or
//
//   (b) a runtime back-call into the JVM via the universal stdlib caller (rell_sysfn_call) for
//       every SysGlobal / SysMember / NativeUser target and every MemberCalculator_SysFunction;
//       or into the SQL bridge (rell_db_eval_expr) for a DataAttribute DB read; or
//
//   (c) a function-level SOFT-FAIL (EmitContext::fail) for every target/shape we cannot lower
//       bit-exactly: operations, function values, abstract/extendable dispatch, partial calls,
//       and any member-calculator family that needs the interpreter.
//
// THE EMIT-TIME SLOW-PATH HELPER LIVES HERE
// -----------------------------------------
// The intrinsic overlays (intrinsics_integer/decimal/biginteger/text/bytearray/math) deliberately
// do NOT emit the rell_sysfn_call back-call themselves: they only signal `*escaped` and leave the
// actual slow-path emission to the lowering files (see the headers of intrinsics_biginteger.cpp
// and intrinsics_text.cpp — "A pure intrinsic here cannot itself emit that back-call"). This file
// supplies that one sanctioned emit-time mechanism — `emitSysfnCall` — that lowers an escaped op /
// a sys-target call into IR that invokes the host `rell_sysfn_call(env, id, args*, nargs, ctx)`.
// When lower_ops.cpp lands it reuses these same getOrInsert* declarations (they are keyed by the
// runtime function's stable C++ symbol name, so `Module::getOrInsertFunction` is idempotent across
// translation units and the ODR is respected).
//
// THE HIDDEN ctx PARAM
// --------------------
// Per rell_runtime.h §6, every JIT'd function receives the live `RellCallCtx*` as a hidden trailing
// pointer parameter (carrying the per-call arena, the frame handle, the JNIEnv, and the sysfn
// table). lower_expr.cpp's function prologue (when it lands) is responsible for creating the
// function with that trailing `ptr` arg; this file reads it back via `currentCtxArg`, which simply
// returns the last argument of the function currently being emitted. We do NOT invent a parallel
// channel: one ctx pointer, threaded through arguments, reused by every back-call site.
//
// DETERMINISM (consensus-critical): no Rell semantics are computed here. Sys/native targets run on
// the JVM R_SysFunction (bit-exact by construction — stdlib_bridge.cpp). DB members run on the JVM
// interpreter (sql_bridge.cpp). Direct user calls reuse the SAME JIT'd body the interpreter would
// have run, with arguments marshalled through the shared RellValue ABI. Anything we cannot prove
// equivalent soft-fails to the interpreter. We never approximate a call.
//
// Style mirrors jni_bridge.cpp / rell_runtime.h: `namespace ir = rell::ir;`, defensive null-checks
// even after VerifyAppBuffer, LLVM 19+ IRBuilder API, never emit IR after ec.fail().

#include "rell_runtime.h"

#include <string>
#include <string_view>
#include <vector>

#include <llvm/IR/Constants.h>
#include <llvm/IR/DerivedTypes.h>

namespace rell::llvm_rt {

// Defined in intrinsics_integer.cpp (external linkage, not header-declared). Emits inline IR for the
// pure-i64 integer stdlib functions (abs / sign / min / max). `args` are already-unpacked i64
// payloads; returns the inline runtime value, or nullptr WITHOUT ec.fail() to mean "not inlinable —
// use the rell_sysfn_call slow path". abs(Long.MIN) records the exact overflow Rt_Exception into the
// integer-error channel (rell_int_overflow) that callValueFunction polls — never a wrong value.
llvm::Value *tryEmitIntegerSysFn(EmitContext &ec, const std::string &fnName,
                                 llvm::ArrayRef<llvm::Value *> args);

namespace {

// =====================================================================================
// Shared LLVM type shapes for the runtime ABI.
//
// A runtime Rell value SSA is `valueType()` == { i8, i32, i64 } (rell_runtime.h §8). Pointers are
// opaque (LLVM 19 typed-pointer-free): `ptr`. The host runtime entry points all take/return these.
// =====================================================================================

llvm::PointerType *opaquePtr(EmitContext &ec) { return llvm::PointerType::getUnqual(ec.ctx()); }

// The RellValue-returning C ABI used by both the universal stdlib caller and a JIT'd user callee:
// the value is returned by value as the { i8, i32, i64 } aggregate. The LLVM struct-return lowering
// matches the C++ RellValue POD layout (static_assert'd 16 bytes in rell_runtime.h) on the host
// target, so a native `RellValue (*)()`-shaped symbol and this IR signature agree on the wire.
llvm::FunctionType *sysfnCallType(EmitContext &ec) {
    // RellValue rell_sysfn_call(JNIEnv* env, SysFnId id, const RellValue* args, int32_t nargs,
    //                           RellCallCtx* ctx)
    //   -> { i8, i32, i64 } (ptr env, i32 id, ptr args, i32 nargs, ptr ctx)
    auto *valTy = ec.valueType();
    auto *i32Ty = ec.builder().getInt32Ty();
    auto *ptrTy = opaquePtr(ec);
    return llvm::FunctionType::get(valTy, {ptrTy, i32Ty, ptrTy, i32Ty, ptrTy}, /*isVarArg=*/false);
}

llvm::FunctionType *dbEvalType(EmitContext &ec) {
    // RellValue rell_db_eval_expr(JNIEnv* env, DbNodeId id, RellCallCtx* ctx)
    //   -> { i8, i32, i64 } (ptr env, i32 id, ptr ctx)
    auto *valTy = ec.valueType();
    auto *i32Ty = ec.builder().getInt32Ty();
    auto *ptrTy = opaquePtr(ec);
    return llvm::FunctionType::get(valTy, {ptrTy, i32Ty, ptrTy}, /*isVarArg=*/false);
}

// The full-runtime JIT'd-callee ABI (supersedes jni_bridge.cpp's prototype `i64(i64*)`): a compiled
// user function returns a RellValue and takes (ptr args, ptr ctx) — `args` points at a contiguous
// array of RellValue, `ctx` is the hidden trailing RellCallCtx*. The Wiring phase emits callee
// prologues with this exact signature; a cross-function call here must match it on the wire.
[[maybe_unused]] llvm::FunctionType *userCalleeType(EmitContext &ec) {
    auto *valTy = ec.valueType();
    auto *ptrTy = opaquePtr(ec);
    return llvm::FunctionType::get(valTy, {ptrTy, ptrTy}, /*isVarArg=*/false);
}

// Declare-or-reuse a runtime function by its stable host symbol name. getOrInsertFunction is
// idempotent: a second TU asking for the same {name, type} reuses the prior declaration, so the
// emit-time helpers here and in lower_ops.cpp share one external decl per symbol. The ORC layer
// resolves these names to the host process symbols (rell_sysfn_call / rell_db_eval_expr are
// extern "C"-visible; the LLJIT's host-process search generator binds them at lookup time).
llvm::FunctionCallee runtimeFn(EmitContext &ec, const char *symbol, llvm::FunctionType *type) {
    return ec.module().getOrInsertFunction(symbol, type);
}

// The hidden trailing ctx pointer of the function currently being emitted. By the JIT'd-function
// ABI (userCalleeType / lower_expr.cpp's prologue) it is always the LAST argument and is a `ptr`.
// Returns nullptr (and the caller soft-fails) if the insertion point has no enclosing function or
// the function has no arguments — that can only happen if a lowering file calls us outside a
// function body, which is a wiring bug, not an envelope miss.
llvm::Value *currentCtxArg(EmitContext &ec) {
    llvm::BasicBlock *bb = ec.builder().GetInsertBlock();
    if (bb == nullptr) return nullptr;
    llvm::Function *fn = bb->getParent();
    if (fn == nullptr || fn->arg_empty()) return nullptr;
    return fn->getArg(fn->arg_size() - 1);
}

// =====================================================================================
// emitSysfnCall — THE sanctioned emit-time slow path.
//
// Lowers a call to the universal JNI stdlib caller. `id` is the dense SysFnId (already interned via
// ec.sysFns()); `argVals` are the evaluated runtime-value SSAs (valueType()) for each argument, in
// call order. We spill them into an on-stack RellValue array (an alloca of `[N x valueType()]`),
// pass &array[0] and N, pass a null JNIEnv (rell_sysfn_call pulls the live env from ctx->env — see
// stdlib_bridge.cpp), and pass the hidden ctx pointer. The result is the returned RellValue SSA.
//
// DETERMINISM: the entire operation runs on the JVM R_SysFunction (stdlib_bridge.cpp), so the
// emitted IR computes nothing — it only marshals handles. On a pending Rt_Exception the host helper
// returns rv_none() leaving the exception set; the JIT trampoline (Wiring phase) turns a NONE-
// tagged return into "abort", so the JVM observes the identical Rt_Exception. We therefore do NOT
// branch on the result tag here.
// =====================================================================================
[[maybe_unused]] llvm::Value *emitSysfnCall(EmitContext &ec, SysFnId id,
                                            llvm::ArrayRef<llvm::Value *> argVals) {
    if (id == kSysFnIdNone) {
        return ec.failExpr("emitSysfnCall: un-interned sysfn id");
    }
    llvm::Value *ctx = currentCtxArg(ec);
    if (ctx == nullptr) {
        return ec.failExpr("emitSysfnCall: no enclosing function ctx arg");
    }

    auto &b = ec.builder();
    auto *valTy = ec.valueType();
    auto *ptrTy = opaquePtr(ec);
    const int32_t nargs = static_cast<int32_t>(argVals.size());

    // Spill the args into a stack-resident RellValue[nargs]. Allocate in the function ENTRY block
    // so the alloca is hoisted out of any loop the call site sits in (the standard LLVM idiom);
    // restore the insertion point afterwards. A zero-arg call still allocates a 0-length array and
    // passes a valid (dangling-but-unread) pointer with nargs == 0 (rell_sysfn_call tolerates
    // args == anything when nargs == 0; we pass a real pointer to stay defensive).
    llvm::Value *argsArr = nullptr;
    {
        llvm::Function *fn = b.GetInsertBlock()->getParent();
        llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
        auto *arrTy = llvm::ArrayType::get(valTy, static_cast<uint64_t>(nargs < 0 ? 0 : nargs));
        argsArr = entryB.CreateAlloca(arrTy, nullptr, "sysfn_args");
        // Store each evaluated arg into its slot at the CURRENT insertion point (after the args
        // were computed), GEP'ing element i of the array.
        for (int32_t i = 0; i < nargs; ++i) {
            llvm::Value *slot = b.CreateInBoundsGEP(
                arrTy, argsArr,
                {b.getInt32(0), b.getInt32(i)}, "sysfn_arg_slot");
            b.CreateStore(argVals[i], slot);
        }
    }

    // &array[0] as a `ptr` to the first RellValue (the C `const RellValue*` arg).
    llvm::Value *argsPtr = argsArr;
    if (nargs > 0) {
        auto *arrTy = llvm::ArrayType::get(valTy, static_cast<uint64_t>(nargs));
        argsPtr = b.CreateInBoundsGEP(arrTy, argsArr, {b.getInt32(0), b.getInt32(0)}, "sysfn_args_p");
    }

    llvm::FunctionCallee callee = runtimeFn(ec, "rell_sysfn_call", sysfnCallType(ec));
    llvm::Value *nullEnv = llvm::ConstantPointerNull::get(ptrTy);  // env pulled from ctx->env
    return b.CreateCall(callee,
                        {nullEnv, b.getInt32(static_cast<int32_t>(id)), argsPtr,
                         b.getInt32(nargs), ctx},
                        "sysfn_result");
}

// =====================================================================================
// Inline integer-stdlib overlay attempt (REVIEW_coverage target 1).
//
// Before emitting the universal rell_sysfn_call slow path for a SysGlobal/SysMember/NativeUser
// target, try to lower the call to PURE INLINE IR via tryEmitIntegerSysFn (abs/sign/min/max). The
// overlay emits no back-call — it computes the result in i64 and re-packs as INTEGER — so it
// sidesteps the by-value rell_sysfn_call return-ABI hazard entirely (the reason the sys path soft-
// fails today). It is invoked ONLY when the result type is statically `integer`, which guarantees
// every operand is INTEGER-tagged at runtime (integer never escapes to a HANDLE — value.cpp), so
// unpacking the i64 payload with no tag branch is safe and bit-exact.
//
// DETERMINISM: integer abs/sign/min/max match the interpreter (Lib_Math / lib_type_integer.kt)
// exactly: min/max/sign are total; abs(Long.MIN_VALUE) records the SAME overflow Rt_Exception via
// the integer-error channel (rell_int_overflow) that callValueFunction polls. Anything the overlay
// does not cover (the other names, non-integer result types, wrong arity) returns nullptr and the
// caller falls through to the existing soft-fail / slow path. We NEVER emit an approximate result.
//
// The interned fn_name key has the shape "<fullName>#<M|G>#(<argSig>)-><resultSig>[@<pos>]" (the
// resolver's buildSysFnKey). We decode the simple name (after the last '.', before '#') and the
// result strCode (after "->" up to an optional "@pos") with the same parsing intrinsics_math.cpp
// uses — content-addressed and identical on every JVM, so it is deterministic.
// =====================================================================================

// Simple function name = segment of <fullName> after the last '.', truncated at the first '#'.
std::string_view sysFnSimpleName(std::string_view key) {
    const std::size_t hash = key.find('#');
    if (hash == std::string_view::npos) return {};
    std::string_view fullName = key.substr(0, hash);
    const std::size_t dot = fullName.rfind('.');
    return dot == std::string_view::npos ? fullName : fullName.substr(dot + 1);
}

// Result-type strCode = substring after "->" up to an optional "@<pos>" tail.
std::string_view sysFnResultType(std::string_view key) {
    const std::size_t arrow = key.rfind("->");
    if (arrow == std::string_view::npos) return {};
    std::string_view tail = key.substr(arrow + 2);
    const std::size_t at = tail.find('@');
    if (at != std::string_view::npos) tail = tail.substr(0, at);
    return tail;
}

// Attempt the inline integer overlay for a sys-target fn_name. `argVals` are the evaluated runtime-
// value SSAs (in source order). Returns the inline result, or nullptr (WITHOUT ec.fail()) to mean
// "not inlinable here — caller uses the slow path / soft-fail". Never sets ec.fail() on a clean
// hand-off; tryEmitIntegerSysFn only fails on a recognised name with a wrong arity (a caller bug).
llvm::Value *tryInlineSysFn(EmitContext &ec, const char *fnNameKey,
                            llvm::ArrayRef<llvm::Value *> argVals) {
    if (fnNameKey == nullptr) return nullptr;
    const std::string_view key(fnNameKey);
    if (sysFnResultType(key) != "integer") {
        // Only the integer-result leaf is inline-safe: a non-integer result (decimal/big_integer
        // widening) can carry a HANDLE operand at runtime, so unpacking i64 would be wrong. Defer.
        return nullptr;
    }
    const std::string_view simple = sysFnSimpleName(key);
    if (simple != "abs" && simple != "sign" && simple != "min" && simple != "max") {
        return nullptr;  // not an inlinable integer stdlib fn — slow path.
    }

    // Unpack each operand's i64 payload (safe: integer-result leaf ⇒ every operand is INTEGER-tagged).
    std::vector<llvm::Value *> unpacked;
    unpacked.reserve(argVals.size());
    for (llvm::Value *v : argVals) {
        unpacked.push_back(ec.unpackPayloadI64(v));
    }
    // Prefix with "integer." so tryEmitIntegerSysFn matches the integer specialisation of every name
    // (it accepts "integer.abs"/"abs", "integer.sign", "min"/"integer.min", "max"/"integer.max").
    const std::string fnName = "integer." + std::string(simple);
    return tryEmitIntegerSysFn(ec, fnName, unpacked);
}

// Full function name = the <fullName> segment of the key (before the first '#'), e.g.
// "byte_array.size". Distinct from sysFnSimpleName (which drops the receiver-type prefix); we need the
// FULL name here so we only match byte_array members, not list.size / text.empty.
std::string_view sysFnFullName(std::string_view key) {
    const std::size_t hash = key.find('#');
    return hash == std::string_view::npos ? std::string_view{} : key.substr(0, hash);
}

// Attempt the native byte_array member overlay (.size() -> integer, .empty() -> boolean) on a
// SysMember target. `receiver` is the already-lowered BYTEARRAY receiver SSA. Returns the inline
// result, or nullptr (WITHOUT ec.fail()) to mean "not a native byte_array member — caller uses the
// soft-fail floor". Bit-exact with the interpreter (byte_array .size()/.empty() over value.size).
// Only fires for ZERO-arg members (size/empty take no args); a member with args falls through.
llvm::Value *tryInlineByteArrayMember(EmitContext &ec, const char *fnNameKey,
                                      llvm::Value *receiver) {
    if (fnNameKey == nullptr || receiver == nullptr) return nullptr;
    const std::string_view key(fnNameKey);
    const std::string_view full = sysFnFullName(key);
    if (full == "byte_array.size") {
        // .size() -> integer (value.size). Native via rell_bytearray_size.
        return emitByteArraySize(ec, receiver);
    }
    if (full == "byte_array.empty") {
        // .empty() -> boolean (value.isEmpty == size == 0). Compute from the native size to stay
        // bit-exact (and JIT-resident): size == 0. rell_bytearray_size returns an INTEGER runtime
        // value; compare its payload to 0 and pack a BOOLEAN.
        llvm::Value *sizeVal = emitByteArraySize(ec, receiver);
        if (sizeVal == nullptr) return nullptr;  // ec.fail already set (no ctx) — surfaced by caller.
        llvm::IRBuilder<> &b = ec.builder();
        llvm::Value *isEmpty =
            b.CreateICmpEQ(ec.unpackPayloadI64(sizeVal), b.getInt64(0), "ba_empty");
        return ec.packInline(RellTag::BOOLEAN, b.getInt32(0),
                             b.CreateZExt(isEmpty, b.getInt64Ty(), "ba_empty_i64"));
    }
    return nullptr;
}

// Emit a native crypto hash call. `symbol` is the runtime entry point ("rell_crypto_sha256" /
// "rell_crypto_keccak256"); `operand` is the already-lowered BYTEARRAY input SSA. Mirrors the
// rell_bytearray_concat emission (sret out-pointer + the hidden ctx for arena allocation): spill the
// operand and an out slot into the entry block, call `void symbol(ptr out, ptr ctx, ptr operand)`,
// and load the BYTEARRAY result. Returns nullptr WITHOUT ec.fail() only via ctx-missing soft-fail.
llvm::Value *emitCryptoHash(EmitContext &ec, const char *symbol, llvm::Value *operand) {
    if (operand == nullptr) return nullptr;
    llvm::Value *ctx = currentCtxArg(ec);
    if (ctx == nullptr) {
        return ec.failExpr("emitCryptoHash: no enclosing function ctx arg");
    }
    auto &b = ec.builder();
    auto *valTy = ec.valueType();
    auto *ptrTy = opaquePtr(ec);

    llvm::Function *fn = b.GetInsertBlock()->getParent();
    llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
    llvm::Value *inSlot = entryB.CreateAlloca(valTy, nullptr, "crypto_in");
    llvm::Value *outSlot = entryB.CreateAlloca(valTy, nullptr, "crypto_out");
    b.CreateStore(operand, inSlot);

    // void symbol(ptr out, ptr ctx, ptr operand)
    auto *fnTy = llvm::FunctionType::get(llvm::Type::getVoidTy(ec.ctx()), {ptrTy, ptrTy, ptrTy},
                                         /*isVarArg=*/false);
    llvm::FunctionCallee callee = runtimeFn(ec, symbol, fnTy);
    b.CreateCall(callee, {outSlot, ctx, inSlot});
    return b.CreateLoad(valTy, outSlot, "crypto_hash");
}

// Attempt the native crypto-hash overlay for a SysGlobal/SysMember target. Wires crypto.sha256 /
// byte_array.sha256 / crypto.keccak256 to the native rell::crypto hashes, replacing the
// rell_sysfn_call back-call. `operandVals` is the operand list the interpreter would pass — for a
// SysGlobal `crypto.sha256(x)` that is the call args; for a SysMember `b.sha256()` it is `[receiver]`
// (the caller prepends the receiver, matching evaluateMemberCalculator). We match by SIMPLE name
// (sha256/keccak256), result type byte_array, and EXACTLY ONE byte_array operand — robust to both the
// `crypto.` qualified form and the top-level alias. Returns the native result, or nullptr (WITHOUT
// ec.fail) to mean "not a wired hash — caller uses the rell_sysfn_call floor".
//
// DETERMINISM: bit-exact with lib_crypto.kt (SHA-256 = MessageDigest("SHA-256"); keccak256 = BC
// Keccak.Digest256, 0x01 pad). Only sha256/keccak256 are wired; every OTHER crypto name (get_signature
// / verify_signature / eth_* / privkey_to_pubkey / ...) returns nullptr → stays on the back-call.
llvm::Value *tryInlineCryptoHash(EmitContext &ec, const char *fnNameKey,
                                 llvm::ArrayRef<llvm::Value *> operandVals) {
    if (fnNameKey == nullptr) return nullptr;
    const std::string_view key(fnNameKey);
    if (sysFnResultType(key) != "byte_array") return nullptr;  // a hash returns byte_array.
    if (operandVals.size() != 1) return nullptr;               // exactly one byte_array operand.
    const std::string_view simple = sysFnSimpleName(key);
    if (simple == "sha256") return emitCryptoHash(ec, "rell_crypto_sha256", operandVals[0]);
    if (simple == "keccak256") return emitCryptoHash(ec, "rell_crypto_keccak256", operandVals[0]);
    return nullptr;  // not a wired hash — slow path.
}

// Attempt the native text member overlay (.size() -> integer, .empty() -> boolean) on a SysMember
// target. `receiver` is the already-lowered TEXT receiver SSA. Returns the inline result, or nullptr
// (WITHOUT ec.fail()) to mean "not a native text member — caller uses the soft-fail floor". Bit-exact
// with the interpreter (text .size() == String.length code units; .empty() == isEmpty == size == 0).
// Only fires for ZERO-arg members. Non-trivial text members (sub/upper_case/char_at/...) fall through
// to the rell_sysfn_call floor (the JVM owns those String semantics).
llvm::Value *tryInlineTextMember(EmitContext &ec, const char *fnNameKey, llvm::Value *receiver) {
    if (fnNameKey == nullptr || receiver == nullptr) return nullptr;
    const std::string_view key(fnNameKey);
    const std::string_view full = sysFnFullName(key);
    if (full == "text.size") {
        // .size() -> integer (String.length, UTF-16 code units). Native via rell_text_size.
        return emitTextSize(ec, receiver);
    }
    if (full == "text.empty") {
        // .empty() -> boolean (String.isEmpty == length == 0). Compute from the native size to stay
        // bit-exact (and JIT-resident): size == 0.
        llvm::Value *sizeVal = emitTextSize(ec, receiver);
        if (sizeVal == nullptr) return nullptr;  // ec.fail already set (no ctx) — surfaced by caller.
        llvm::IRBuilder<> &b = ec.builder();
        llvm::Value *isEmpty =
            b.CreateICmpEQ(ec.unpackPayloadI64(sizeVal), b.getInt64(0), "txt_empty");
        return ec.packInline(RellTag::BOOLEAN, b.getInt32(0),
                             b.CreateZExt(isEmpty, b.getInt64Ty(), "txt_empty_i64"));
    }
    return nullptr;
}

// Emit a native call to a member text-op runtime helper. `symbol` is the runtime entry point; `receiver`
// is the already-lowered TEXT receiver SSA; `args` are the already-lowered call-arg SSAs (in source
// order, evaluated AFTER the receiver — the receiver reaches us pre-lowered via the MemberExpr path,
// matching the interpreter's base-then-args order). `wantsCtx` selects the ABI: helpers that ALLOCATE in
// the arena (sub/repeat/replace) take a leading ctx pointer, the pure ones (starts_with/.../char_at) do
// not. Spills receiver + args into entry-block allocas, calls `void symbol([ctx,] out, recv, args..., [int
// scalars])`. NOTE: int-typed args (char_at's i, sub's start/end, repeat's n, index_of/2's start) are
// passed as raw i64 (the helper's signature), unpacked from the INTEGER arg's payload; text-typed args
// are passed as a `ptr` to the spilled RellValue. `intArgMask` marks which call args are integer scalars.
llvm::Value *emitTextMemberOp(EmitContext &ec, const char *symbol, bool wantsCtx,
                              llvm::Value *receiver, llvm::ArrayRef<llvm::Value *> args,
                              uint32_t intArgMask) {
    if (receiver == nullptr) return nullptr;
    llvm::Value *ctx = currentCtxArg(ec);
    if (wantsCtx && ctx == nullptr) {
        return ec.failExpr("emitTextMemberOp: no enclosing function ctx arg");
    }
    auto &b = ec.builder();
    auto *valTy = ec.valueType();
    auto *ptrTy = opaquePtr(ec);
    auto *i64Ty = b.getInt64Ty();

    llvm::Function *fn = b.GetInsertBlock()->getParent();
    llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
    llvm::Value *recvSlot = entryB.CreateAlloca(valTy, nullptr, "txt_op_recv");
    llvm::Value *outSlot = entryB.CreateAlloca(valTy, nullptr, "txt_op_out");
    b.CreateStore(receiver, recvSlot);

    // Build the call argument list and the matching parameter-type list. RellValue args are spilled to a
    // slot and passed as `ptr`; integer args are unpacked to i64 and passed by value.
    std::vector<llvm::Value *> callArgs;
    std::vector<llvm::Type *> paramTys;
    if (wantsCtx) {
        callArgs.push_back(outSlot);
        callArgs.push_back(ctx);
        paramTys.push_back(ptrTy);
        paramTys.push_back(ptrTy);
    } else {
        callArgs.push_back(outSlot);
        paramTys.push_back(ptrTy);
    }
    callArgs.push_back(recvSlot);
    paramTys.push_back(ptrTy);

    for (uint32_t i = 0; i < args.size(); ++i) {
        if ((intArgMask >> i) & 1u) {
            callArgs.push_back(ec.unpackPayloadI64(args[i]));
            paramTys.push_back(i64Ty);
        } else {
            llvm::Value *argSlot = entryB.CreateAlloca(valTy, nullptr, "txt_op_arg");
            b.CreateStore(args[i], argSlot);
            callArgs.push_back(argSlot);
            paramTys.push_back(ptrTy);
        }
    }

    auto *fnTy = llvm::FunctionType::get(llvm::Type::getVoidTy(ec.ctx()), paramTys,
                                         /*isVarArg=*/false);
    llvm::FunctionCallee callee = runtimeFn(ec, symbol, fnTy);
    b.CreateCall(callee, callArgs);
    return b.CreateLoad(valTy, outSlot, "txt_op_result");
}

// Attempt the native arg-bearing text member overlay on a SysMember target. `receiver` is the already-
// lowered TEXT receiver SSA (evaluated before the call args, matching the interpreter); `args` are the
// already-lowered call-arg SSAs. Returns the native result, or nullptr (WITHOUT ec.fail) to mean "not a
// wired arg-bearing text member — caller uses the rell_sysfn_call floor". Each wired op is bit-exact with
// lib_type_text.kt (see value.cpp); the error-bearing ones (sub/char_at/index_of/2/repeat) record their
// EXACT Rt_Exception via the text-op error channel. DEFERRED text members (upper_case/lower_case/format/
// split/trim/matches/like/regex*/to_bytes/compare_to/reversed/last_index_of) are not matched here and
// fall through to the back-call. The arity gate (args.size()) disambiguates the overloads (index_of/1 vs
// /2, sub/1 vs /2).
llvm::Value *tryInlineTextMemberArgs(EmitContext &ec, const char *fnNameKey, llvm::Value *receiver,
                                     llvm::ArrayRef<llvm::Value *> args) {
    if (fnNameKey == nullptr || receiver == nullptr) return nullptr;
    const std::string_view full = sysFnFullName(std::string_view(fnNameKey));
    const std::size_t n = args.size();

    // Boolean results — total, no error path. One text arg.
    if (full == "text.starts_with" && n == 1) {
        return emitTextMemberOp(ec, "rell_text_starts_with", /*wantsCtx=*/false, receiver, args, 0);
    }
    if (full == "text.ends_with" && n == 1) {
        return emitTextMemberOp(ec, "rell_text_ends_with", /*wantsCtx=*/false, receiver, args, 0);
    }
    if (full == "text.contains" && n == 1) {
        return emitTextMemberOp(ec, "rell_text_contains", /*wantsCtx=*/false, receiver, args, 0);
    }
    // index_of: /1 (text) total -> -1 if absent; /2 (text, integer) PRE-CHECKS start (custom error).
    if (full == "text.index_of" && n == 1) {
        return emitTextMemberOp(ec, "rell_text_index_of", /*wantsCtx=*/false, receiver, args, 0);
    }
    if (full == "text.index_of" && n == 2) {
        // args = (text sub, integer start) — second arg is the i64 scalar (mask bit 1).
        return emitTextMemberOp(ec, "rell_text_index_of_from", /*wantsCtx=*/false, receiver, args,
                                0b10);
    }
    // char_at(i): integer result, OOB -> custom error. One integer arg.
    if (full == "text.char_at" && n == 1) {
        return emitTextMemberOp(ec, "rell_text_char_at", /*wantsCtx=*/false, receiver, args, 0b1);
    }
    // sub/1 (start) and sub/2 (start, end): text result, arena-allocating (wantsCtx). Integer args.
    if (full == "text.sub" && n == 1) {
        return emitTextMemberOp(ec, "rell_text_sub1", /*wantsCtx=*/true, receiver, args, 0b1);
    }
    if (full == "text.sub" && n == 2) {
        return emitTextMemberOp(ec, "rell_text_sub2", /*wantsCtx=*/true, receiver, args, 0b11);
    }
    // repeat(n): text result, arena-allocating. One integer arg.
    if (full == "text.repeat" && n == 1) {
        return emitTextMemberOp(ec, "rell_text_repeat", /*wantsCtx=*/true, receiver, args, 0b1);
    }
    // replace(old, new): text result, arena-allocating. Two text args (mask 0). Escapes to the
    // interpreter at runtime when `old` is EMPTY (rell_jit_escape) — still bit-exact.
    if (full == "text.replace" && n == 2) {
        return emitTextMemberOp(ec, "rell_text_replace", /*wantsCtx=*/true, receiver, args, 0);
    }
    return nullptr;  // not a wired arg-bearing text member — slow path.
}

// =====================================================================================
// Argument evaluation. A FullFunctionCall carries `args: Vector<Expr>` plus a `mapping:
// Vector<int32_t>` that reorders the evaluated arg list into parameter order (default-value /
// named-arg handling on the JVM side). We evaluate args in SOURCE order (the order they appear in
// `args()`), because evaluation order is observable (side effects, exceptions) and must match the
// interpreter, then apply the mapping only when handing to a DIRECT user callee. For a sys-call the
// JVM dispatch already expects source-order boxed args (Llvm_SysBridge re-applies any mapping), so
// we pass them straight through.
//
// Returns false (after ec.fail) on any arg we cannot lower; the whole function then soft-fails.
// =====================================================================================
bool evalArgs(EmitContext &ec, const ir::FullFunctionCall &call,
              std::vector<llvm::Value *> &out) {
    const auto *args = call.args();
    if (args == nullptr) {
        ec.fail("FullFunctionCall has null args");
        return false;
    }
    out.reserve(args->size());
    for (flatbuffers::uoffset_t i = 0; i < args->size(); ++i) {
        const auto *argExpr = args->Get(i);
        if (argExpr == nullptr) {
            ec.fail("null arg Expr in FullFunctionCall");
            return false;
        }
        llvm::Value *v = lowerExpr(ec, *argExpr);
        if (v == nullptr) return false;  // ec.fail already set by lowerExpr
        out.push_back(v);
    }
    return true;
}

// =====================================================================================
// Direct user-callee dispatch (FnTarget_RegularUser / _RegularQuery).
//
// LINKAGE STRATEGY (the ORC story): each user function compiles to an ORC-resident symbol whose
// name is a deterministic function of its App index — `rell_fn_<index>` (mirroring jni_bridge.cpp's
// `rell_fn_<counter>`, but keyed on the stable App.functions index rather than a global counter so
// the name is reproducible across compiles and independent of compile ORDER). A call site here
// emits a DECLARATION of that symbol with the full-runtime callee ABI (userCalleeType) via
// getOrInsertFunction and a direct `call`. Resolution happens at ORC lookup time:
//
//   * compile-on-demand: the callee may not yet be JIT'd when the caller is. ORC's lazy/
//     symbol-not-found behaviour is the Wiring phase's responsibility — either the driver
//     compiles callees transitively before the caller is materialised, or a lazy-reexport stub is
//     installed so the first call triggers callee compilation. Either way the symbol name is the
//     contract, and this file only needs to emit the correctly-named external call.
//
// WHY THIS IS GATED OFF TODAY (soft-fail):
//   The cross-function native ABI requires (a) the callee compiled with the SAME userCalleeType
//   signature (the Wiring phase replaces jni_bridge.cpp's `i64(i64*)` prototype ABI with it), and
//   (b) the caller to MATERIALISE the call's args into a RellValue[] and pass the ctx — both of
//   which depend on the not-yet-landed function prologue (lower_expr.cpp) agreeing on argument
//   marshalling and on the ORC driver wiring transitive compilation. Until that contract is live,
//   emitting a direct call would link against a symbol with the prototype's incompatible signature
//   (silent ABI mismatch → wrong results / crash). DETERMINISM + safety: we SOFT-FAIL the direct
//   user-call path now (the interpreter runs the callee, itself possibly re-entering the JIT via
//   Llvm_Backend.outerInterp), and flip it on in the Wiring phase. The marshalling skeleton below
//   is written and exercised only behind that gate so the shape is reviewed but never mis-links.
// =====================================================================================
// Emit a DIRECT call to a user function. Only SELF-recursion is supported — a call whose
// fn_def_index() equals the index of the function currently being compiled (ec.selfFn()) — mirroring
// the i64 oracle (jni_bridge.cpp Lowerer::lowerSelfCall), which inlines self-recursion and soft-fails
// every other user call. A cross-function call needs the callee compiled under the SAME sret callee
// ABI and ORC transitive wiring (not yet landed), so it soft-fails: the interpreter runs the callee,
// itself possibly re-entering the JIT via Llvm_Backend.outerInterp.
//
// SELF-CALL ABI: the callee is `void(ptr result, ptr args, ptr ctx)` (sret). We allocate a result
// slot and a RellValue[nargs] arg array in the entry block (hoisted out of loops), store each
// evaluated arg positionally, call `selfFn(resultSlot, argsArr, ctx)`, and load the result back out
// of the slot. `ctx` is threaded straight through (the same RellCallCtx the caller received), so a
// recursive call shares the live frame/arena/sysfn table.
llvm::Value *emitDirectUserCall(EmitContext &ec, uint32_t fnDefIndex,
                                llvm::ArrayRef<llvm::Value *> argVals) {
    // Bound-check the index against App.functions (defensive even though the resolver produced it).
    const auto *functions = ec.app().functions();
    if (functions == nullptr || fnDefIndex >= functions->size()) {
        return ec.failExpr("RegularUser fn_def_index out of range");
    }

    // Only self-recursion is wired. A different user function soft-fails to the interpreter.
    if (ec.selfFn() == nullptr || ec.selfFnIndex() < 0 ||
        static_cast<int>(fnDefIndex) != ec.selfFnIndex()) {
        return ec.failExpr("cross-function user call not wired (only self-recursion); soft-fail");
    }

    llvm::Value *ctx = currentCtxArg(ec);
    if (ctx == nullptr) {
        return ec.failExpr("emitDirectUserCall: no enclosing function ctx arg");
    }

    auto &b = ec.builder();
    auto *valTy = ec.valueType();
    const int32_t nargs = static_cast<int32_t>(argVals.size());

    // Allocate the sret result slot and the arg array in the ENTRY block (hoisted so a recursive
    // call inside a loop does not grow the stack per iteration).
    llvm::Function *fn = b.GetInsertBlock()->getParent();
    llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
    auto *arrTy = llvm::ArrayType::get(valTy, static_cast<uint64_t>(nargs < 0 ? 0 : nargs));
    llvm::Value *argsArr = entryB.CreateAlloca(arrTy, nullptr, "self_args");
    llvm::Value *resultSlot = entryB.CreateAlloca(valTy, nullptr, "self_result");

    // Store each evaluated arg positionally at the current insertion point (after args computed).
    for (int32_t i = 0; i < nargs; ++i) {
        llvm::Value *slot =
            b.CreateInBoundsGEP(arrTy, argsArr, {b.getInt32(0), b.getInt32(i)}, "self_arg_slot");
        b.CreateStore(argVals[i], slot);
    }
    llvm::Value *argsPtr = argsArr;
    if (nargs > 0) {
        argsPtr =
            b.CreateInBoundsGEP(arrTy, argsArr, {b.getInt32(0), b.getInt32(0)}, "self_args_p");
    }

    // void selfFn(ptr result, ptr args, ptr ctx); then load the RellValue back out of the slot.
    b.CreateCall(ec.selfFnType(), ec.selfFn(), {resultSlot, argsPtr, ctx});
    return b.CreateLoad(valTy, resultSlot, "self_result_val");
}

// =====================================================================================
// FunctionCall dispatch. Handles ONLY FullFunctionCall; PartialFunctionCall soft-fails (a partial
// application captures wildcards into an Rt_FunctionValue closure — a heap value the JVM owns; we
// never synthesise that inline). `baseExpr` is the FunctionCallExpr.base() (nullable): present for
// a value-call `expr(args)` whose target is FnTarget_FunctionValue, absent for a plain `f(args)`.
// =====================================================================================
// `baseExpr` is the call receiver as a not-yet-lowered Expr (the FunctionCallExpr.base() path, where
// the receiver has NOT been evaluated yet). `memberBaseVal` is the receiver already lowered to a
// runtime-value SSA (the MemberExpr path: `lowerMember` evaluates base before reaching here). At most
// one is ever non-null. Both feed the SysMember integer overlay, which prepends the receiver to the
// operand list to match the interpreter (`evaluateMemberCalculator`: `callTarget(target, base, args)`
// prepends `base` for a SysMember). Passing the pre-lowered SSA on the MemberExpr path avoids
// re-evaluating the receiver (which would be a duplicated, possibly side-effecting, evaluation).
llvm::Value *lowerFunctionCall(EmitContext &ec, const ir::FunctionCall &call,
                               const ir::Expr *baseExpr,
                               llvm::Value *memberBaseVal = nullptr) {
    if (call.call_type() == ir::FunctionCallUnion_PartialFunctionCall) {
        // DETERMINISM: a partial call builds an Rt_FunctionValue closure capturing bound args and
        // wildcard slots — a heap value only the JVM constructs. Soft-fail the whole function.
        return ec.failExpr("PartialFunctionCall: closure construction is JVM-only");
    }
    const auto *full = call.call_as_FullFunctionCall();
    if (full == nullptr) {
        return ec.failExpr("FunctionCall is neither Full nor a recognised Partial");
    }
    const auto *tgt = full->target();
    if (tgt == nullptr) {
        return ec.failExpr("FullFunctionCall has null target");
    }

    // Evaluate args once, in source order (evaluation order is observable). The same SSA list feeds
    // either the sys-call slow path (JVM re-applies mapping) or the direct user call (already in
    // param order via the resolver's mapping for the gated path).
    std::vector<llvm::Value *> argVals;
    if (!evalArgs(ec, *full, argVals)) return nullptr;

    switch (tgt->target_type()) {
        case ir::FunctionCallTargetUnion_FnTarget_SysGlobal:
        case ir::FunctionCallTargetUnion_FnTarget_SysMember:
        case ir::FunctionCallTargetUnion_FnTarget_NativeUser: {
            // REVIEW_coverage target 1: before the slow path, try the inline integer overlay
            // (abs/sign/min/max on the integer leaf). It emits pure inline IR — NO rell_sysfn_call —
            // so it is unaffected by the by-value return-ABI hazard described below, and is bit-exact
            // vs the interpreter (abs(Long.MIN) routes the exact overflow through the integer-error
            // channel). If the overlay does not cover this target, fall through to the soft-fail.
            const bool isMember =
                tgt->target_type() == ir::FunctionCallTargetUnion_FnTarget_SysMember;
            const auto *fnName =
                tgt->target_type() == ir::FunctionCallTargetUnion_FnTarget_SysGlobal
                    ? tgt->target_as_FnTarget_SysGlobal()->fn_name()
                : isMember
                    ? tgt->target_as_FnTarget_SysMember()->fn_name()
                    : tgt->target_as_FnTarget_NativeUser()->fn_name();
            if (fnName != nullptr) {
                // For a SysMember target the interpreter calls the stdlib fn with `[base] + args`
                // (rr_interpreter.kt: SysMember case / evaluateMemberCalculator -> callTarget prepends
                // `base`), where `base` is the receiver evaluated BEFORE any call args. `evalArgs`
                // above produced only the call args (the receiver is NOT in args()), so the integer-
                // leaf overlay would see one operand too few (e.g. `a.sign()` -> 0 operands, failing
                // the arity check) and the whole function would soft-fail. Reconstruct the
                // interpreter's operand list by prepending the receiver.
                //
                // The receiver reaches us two ways and we use whichever is set:
                //   * `memberBaseVal` — the MemberExpr path (`a.sign()` is a MemberCalculator_
                //     FunctionCall): `lowerMember` ALREADY lowered the receiver before dispatching
                //     here, so we reuse that SSA directly (re-lowering it would double-evaluate a
                //     possibly side-effecting receiver).
                //   * `baseExpr` — the FunctionCallExpr path (the receiver is a not-yet-lowered Expr):
                //     lower it here, after the (empty) arg list.
                //
                // EVALUATION ORDER: the receiver is observed first, the call args after. We only
                // prepend the receiver when there are NO call args, so the order matches the
                // interpreter. That is exactly the shape of every inline-eligible integer member
                // overlay (`integer.sign`, `integer.abs` take no extra args). A member call WITH args
                // keeps argVals as-is and falls through to the soft-fail, so we never reorder
                // observable side effects.
                // Lower the zero-arg SysMember receiver ONCE (shared by the integer and byte_array
                // overlays) so a side-effecting receiver is never double-evaluated. The receiver
                // reaches us as a pre-lowered SSA (memberBaseVal) or a not-yet-lowered Expr (baseExpr).
                llvm::Value *receiverVal = nullptr;
                if (isMember && argVals.empty() && (memberBaseVal != nullptr || baseExpr != nullptr)) {
                    receiverVal = memberBaseVal;
                    if (receiverVal == nullptr) {
                        receiverVal = lowerExpr(ec, *baseExpr);
                        if (receiverVal == nullptr) return nullptr;  // ec.fail already set by lowerExpr
                    }
                }

                std::vector<llvm::Value *> overlayArgs;
                const std::vector<llvm::Value *> *overlayArgVals = &argVals;
                if (receiverVal != nullptr) {
                    overlayArgs.push_back(receiverVal);
                    overlayArgVals = &overlayArgs;
                }
                llvm::Value *inlined = tryInlineSysFn(ec, fnName->c_str(), *overlayArgVals);
                if (ec.failed()) return nullptr;  // overlay surfaced a malformed-call soft-fail.
                if (inlined != nullptr) return inlined;

                // Native crypto-hash overlay (crypto.sha256 / byte_array.sha256 / crypto.keccak256):
                // a single-byte_array-operand sys-fn with a byte_array result. `*overlayArgVals` is the
                // interpreter's operand list — the lone call arg for the SysGlobal `crypto.sha256(x)`, or
                // `[receiver]` for the zero-arg SysMember `b.sha256()` (the receiver was prepended above).
                // Bit-exact with lib_crypto.kt; every non-hash crypto name falls through to the back-call.
                llvm::Value *hashed = tryInlineCryptoHash(ec, fnName->c_str(), *overlayArgVals);
                if (ec.failed()) return nullptr;  // ctx-missing soft-fail.
                if (hashed != nullptr) return hashed;

                // Native byte_array member overlay (.size()/.empty()): a zero-arg SysMember whose
                // receiver is a BYTEARRAY carrier. Reuses the SAME receiver SSA lowered above (no
                // double-evaluation). Only fires when there are NO call args (size/empty take none), so
                // evaluation order is preserved.
                if (receiverVal != nullptr) {
                    llvm::Value *baMember = tryInlineByteArrayMember(ec, fnName->c_str(), receiverVal);
                    if (ec.failed()) return nullptr;
                    if (baMember != nullptr) return baMember;
                }

                // Native text member overlay (.size()/.empty()): a zero-arg SysMember whose receiver is a
                // TEXT carrier. Reuses the SAME receiver SSA lowered above (no double-evaluation). Only
                // fires when there are NO call args (size/empty take none), so evaluation order holds.
                if (receiverVal != nullptr) {
                    llvm::Value *txtMember = tryInlineTextMember(ec, fnName->c_str(), receiverVal);
                    if (ec.failed()) return nullptr;
                    if (txtMember != nullptr) return txtMember;
                }

                // Native ARG-BEARING text member overlay (sub / starts_with / ends_with / contains /
                // index_of / char_at / replace / repeat): a SysMember whose receiver is a TEXT carrier and
                // whose call args we lowered above (argVals). The receiver reaches us as the already-
                // lowered `memberBaseVal` — the MemberExpr path evaluated it in lowerMember BEFORE
                // evalArgs ran here, so the interpreter's base-then-args evaluation order is preserved.
                // (A text member never arrives via the FunctionCallExpr `baseExpr` path, which is for
                // FunctionValue calls.) The overlay disambiguates the overloads by arity and routes each
                // op to its native helper, bit-exact with lib_type_text.kt. DEFERRED text members
                // (upper_case/lower_case/format/split/...) fall through to the back-call.
                if (isMember && memberBaseVal != nullptr) {
                    llvm::Value *txtArgMember =
                        tryInlineTextMemberArgs(ec, fnName->c_str(), memberBaseVal, argVals);
                    if (ec.failed()) return nullptr;
                    if (txtArgMember != nullptr) return txtArgMember;
                }
            }
            // DETERMINISM / CORRECTNESS FLOOR: the stdlib back-call (rell_sysfn_call) returns a
            // RellValue BY VALUE through the LLVM aggregate-return convention, which does NOT agree
            // with the C++ `RellValue` POD return ABI on the host target (the same mismatch the
            // value-ABI callee sidesteps with an sret pointer — see jni_bridge.cpp valueCalleeType).
            // A by-value back-call therefore reads back a correct tag but a ZEROED payload (e.g.
            // integer.abs() -> 0), which is a silent consensus split. Until the back-call return ABI
            // is moved to an sret form matching the callee, the only correct action is to SOFT-FAIL
            // the WHOLE function so the interpreter runs it bit-exactly. The i64 oracle reaches NO
            // stdlib by design; the value path matches that floor here. (emitSysfnCall is retained
            // for the future sret-based wiring.)
            return ec.failExpr("stdlib call routes to the interpreter (back-call ABI not yet sret)");
        }
        case ir::FunctionCallTargetUnion_FnTarget_RegularUser: {
            const auto *t = tgt->target_as_FnTarget_RegularUser();
            if (t == nullptr) {
                return ec.failExpr("FnTarget_RegularUser missing payload");
            }
            // Require an IDENTITY argument mapping (one positional arg per param, in order), exactly
            // as the i64 oracle does (jni_bridge.cpp Lowerer::lowerSelfCall): a non-identity mapping
            // means defaults/named/varargs reordering the native self-call path does not model.
            // Soft-fail otherwise so the interpreter applies the mapping.
            const auto *mapping = full->mapping();
            const auto *callArgs = full->args();
            if (mapping == nullptr || callArgs == nullptr || mapping->size() != callArgs->size()) {
                return ec.failExpr("RegularUser call missing/!=-sized mapping (soft-fail)");
            }
            for (flatbuffers::uoffset_t i = 0; i < mapping->size(); ++i) {
                if (mapping->Get(i) != static_cast<int32_t>(i)) {
                    return ec.failExpr("RegularUser call mapping is not identity (soft-fail)");
                }
            }
            return emitDirectUserCall(ec, t->fn_def_index(), argVals);
        }
        case ir::FunctionCallTargetUnion_FnTarget_RegularQuery: {
            const auto *t = tgt->target_as_FnTarget_RegularQuery();
            if (t == nullptr) {
                return ec.failExpr("FnTarget_RegularQuery missing payload");
            }
            // A query body is, for the native ABI, an ordinary callable. The same direct-call path
            // applies; it shares the gate with RegularUser (no native query callee exists until the
            // Wiring phase compiles query bodies under userCalleeType). Until then: soft-fail.
            // DETERMINISM: a query may run a db at-expr; its callee body, once JIT'd, embeds the SQL
            // bridge — but the cross-call ABI is the same gated contract as RegularUser.
            return emitDirectUserCall(ec, t->query_def_index(), argVals);
        }
        case ir::FunctionCallTargetUnion_FnTarget_Operation:
            // DETERMINISM: invoking an operation runs the operation's body + guard with its own
            // transaction/db-update semantics, orchestrated by the interpreter (callOperation).
            // Not a value-returning native call. Soft-fail to the JVM.
            return ec.failExpr("FnTarget_Operation: interpreter-only (callOperation)");

        case ir::FunctionCallTargetUnion_FnTarget_FunctionValue:
            // DETERMINISM: the callee is a runtime Rt_FunctionValue produced by base() — a HANDLE we
            // would have to unwrap and dispatch dynamically (closure / fn-ref). The JVM owns that
            // dispatch; soft-fail.
            return ec.failExpr("FnTarget_FunctionValue: dynamic fn-value dispatch is JVM-only");

        case ir::FunctionCallTargetUnion_FnTarget_AbstractUser:
            // An abstract function's concrete body is selected at link/override time. Resolving the
            // override target requires the interpreter's abstract-resolution; soft-fail.
            // DETERMINISM: picking the wrong override is a consensus split — defer to the JVM.
            return ec.failExpr("FnTarget_AbstractUser: override resolution is JVM-only");

        case ir::FunctionCallTargetUnion_FnTarget_AbstractOverride:
            // Carries a concrete FunctionBody, but invoking it still goes through the interpreter's
            // override dispatch path; we do not inline a second copy of the body here. Soft-fail.
            return ec.failExpr("FnTarget_AbstractOverride: invoked via interpreter dispatch");

        case ir::FunctionCallTargetUnion_FnTarget_Extendable:
            // DETERMINISM: an extendable call fans out to every registered extension and COMBINES
            // their results per combiner_kind (UNIT/BOOLEAN/NULLABLE/LIST/MAP). The extension set
            // and combine semantics live entirely in the interpreter; soft-fail.
            return ec.failExpr("FnTarget_Extendable: fan-out + combine is JVM-only");

        default:
            return ec.failExpr("unsupported FunctionCallTargetUnion variant: " +
                               std::to_string(static_cast<int>(tgt->target_type())));
    }
}

// =====================================================================================
// MemberCalculator dispatch (the body of MemberExpr). `base` is the already-lowered receiver SSA
// (valueType()); `safe` is the MemberExpr.safe() null-safe `?.` flag.
//
// INLINE-CAPABLE TODAY: none — even StructAttr / TupleAttr field reads require unwrapping a HANDLE
// receiver (struct/tuple values are heap HANDLEs in the native rep; value.cpp keeps them opaque),
// which means a JNI round-trip with no compute win and a real risk of mis-modelling the field
// layout. So attribute reads route through a sys-function-style accessor where one exists, and
// otherwise soft-fail. SysFunction members route to the universal caller. DataAttribute is a DB
// read (sql bridge / interpreter). Everything else soft-fails. See per-case DETERMINISM notes.
// =====================================================================================
llvm::Value *lowerMemberCalculator(EmitContext &ec, const ir::MemberCalculator &mc,
                                   llvm::Value *base, bool safe) {
    if (safe) {
        // DETERMINISM: `?.` requires a runtime null-check on the receiver that short-circuits to
        // `null` (the Rt_NullValue singleton) without evaluating the member. We can branch on the
        // receiver tag (NULL_) in IR, but the non-null branch still hits the cases below — most of
        // which soft-fail anyway. To keep the null-propagation semantics provably bit-exact we
        // soft-fail the whole safe-member access for now rather than emit a partial branch whose
        // member arm cannot be lowered. (A future inline path lands once a member arm is inlinable.)
        return ec.failExpr("safe member access (?.) not lowered inline");
    }

    switch (mc.calculator_type()) {
        case ir::MemberCalculatorUnion_MemberCalculator_SysFunction:
            // DETERMINISM / CORRECTNESS FLOOR: same by-value back-call ABI hazard as the SysGlobal/
            // SysMember/NativeUser targets above (rell_sysfn_call returns RellValue by value, which
            // reads back a zeroed payload on the host ABI). Soft-fail the whole function until the
            // back-call return is sret. The interpreter runs the member sys-function bit-exactly.
            return ec.failExpr("member stdlib call routes to the interpreter (back-call ABI not yet sret)");
        case ir::MemberCalculatorUnion_MemberCalculator_FunctionCall: {
            const auto *t = mc.calculator_as_MemberCalculator_FunctionCall();
            if (t == nullptr || t->call() == nullptr) {
                return ec.failExpr("MemberCalculator_FunctionCall missing call");
            }
            // A member that is itself a call (`base.method(args)`, e.g. `a.sign()`): delegate to the
            // FunctionCall dispatcher. The resolver does NOT fold the receiver into the inner
            // FullFunctionCall's args() — for a SysMember the interpreter prepends `base` at call time
            // (rr_interp_expr.kt: evaluateMemberCalculator -> callTarget(target, base, args)). So the
            // inner call's args() carries ONLY the explicit call args, and we hand the already-lowered
            // receiver (`base`) through as `memberBaseVal` so the integer overlay can prepend it.
            return lowerFunctionCall(ec, *t->call(), /*baseExpr=*/nullptr, /*memberBaseVal=*/base);
        }
        case ir::MemberCalculatorUnion_MemberCalculator_DataAttribute:
            // DETERMINISM: reading an entity attribute is a DB load (a SELECT on the row) — the SQL
            // bridge / interpreter owns it. A DataAttribute appears as a value-producing member;
            // lowering it inline would require re-deriving the entity SQL mapping in C++. The
            // canonical route is rell_db_eval_expr on the enclosing at-node, not a peeled attribute
            // read. Soft-fail so the interpreter performs the load.
            return ec.failExpr("MemberCalculator_DataAttribute: DB read is interpreter-only");

        case ir::MemberCalculatorUnion_MemberCalculator_StructAttr: {
            // NATIVE: struct/tuple values are now arena-owned COMPOSITEs (value.cpp / rell_runtime.h
            // §1). A field read indexes composite->fields[attr_index] via rell_composite_get — bit-
            // exact with the interpreter's (base as Rt_StructValue).get(attrIndex) (rr_interp_expr.kt).
            // `base` is the already-lowered receiver SSA. If `base` is actually a HANDLE at runtime
            // (e.g. a struct that arrived wide), rell_composite_get poisons the result and the call
            // aborts — but the static type proves it is a struct COMPOSITE here (a struct param/return
            // or a struct field of a struct is always cracked to a COMPOSITE by from_jvm, and a
            // locally-constructed struct is a COMPOSITE by construction).
            const auto *t = mc.calculator_as_MemberCalculator_StructAttr();
            if (t == nullptr) return ec.failExpr("MemberCalculator_StructAttr missing payload");
            return emitCompositeGet(ec, base, t->attr_index());
        }
        case ir::MemberCalculatorUnion_MemberCalculator_TupleAttr: {
            // NATIVE: index Rt_TupleValue.elements[attr_index] via rell_composite_get. The tuple is a
            // COMPOSITE the body constructed (a tuple param/return is gate-forbidden, so the only
            // tuple a TupleAttr ever reads is one built in-body by lowerTupleExpr) — bit-exact with
            // the interpreter's (base as Rt_TupleValue).elements[attrIndex] (rr_interp_expr.kt).
            const auto *t = mc.calculator_as_MemberCalculator_TupleAttr();
            if (t == nullptr) return ec.failExpr("MemberCalculator_TupleAttr missing payload");
            return emitCompositeGet(ec, base, t->attr_index());
        }

        case ir::MemberCalculatorUnion_MemberCalculator_VirtualTupleAttr:
        case ir::MemberCalculatorUnion_MemberCalculator_VirtualStructAttr:
            // Virtual values (Merkle-proof-backed) decode lazily on access — JVM-only. Soft-fail.
            return ec.failExpr("MemberCalculator_Virtual*Attr: virtual decode is JVM-only");

        case ir::MemberCalculatorUnion_MemberCalculator_DataAttributeExpr:
            // A computed entity-attribute expression evaluated in a lambda frame — interpreter-only.
            return ec.failExpr("MemberCalculator_DataAttributeExpr: lambda-frame eval is JVM-only");

        case ir::MemberCalculatorUnion_MemberCalculator_ExprEval:
            // DETERMINISM: ExprEval re-evaluates an inner Expr as the member result. We COULD lower
            // that inner expr inline, but its frame/receiver binding contract (how `base` threads
            // into the inner expr's variable slots) is the interpreter's; modelling it wrong is a
            // silent divergence. Soft-fail until that binding is specified.
            return ec.failExpr("MemberCalculator_ExprEval: inner-expr frame binding not modelled");

        default:
            return ec.failExpr("unsupported MemberCalculatorUnion variant: " +
                               std::to_string(static_cast<int>(mc.calculator_type())));
    }
}

}  // namespace

// =====================================================================================
// Public entry points (declared in rell_runtime.h §9).
// =====================================================================================

// lowerCall — FunctionCallExpr. `call.base()` is nullable (present only for a value-call whose
// target is a FunctionValue); `call.call()` is the required FunctionCall; `call.safe()` is the
// null-safe call flag.
llvm::Value *lowerCall(EmitContext &ec, const ir::FunctionCallExpr &call) {
    const auto *fnCall = call.call();
    if (fnCall == nullptr) {
        return ec.failExpr("FunctionCallExpr has null call");
    }
    if (call.safe()) {
        // DETERMINISM: a null-safe call `base?.f(args)` must short-circuit to `null` when the
        // receiver is null WITHOUT evaluating args. That needs a receiver null-branch around the
        // whole call; rather than emit a partial branch whose call arm frequently soft-fails
        // anyway, soft-fail the whole safe call for now.
        return ec.failExpr("safe function call (?.) not lowered inline");
    }
    return lowerFunctionCall(ec, *fnCall, call.base());
}

// lowerMember — MemberExpr. `base()` (required) is the receiver expr; `calculator()` (required)
// computes the member; `safe()` is the `?.` flag.
llvm::Value *lowerMember(EmitContext &ec, const ir::MemberExpr &member) {
    const auto *calc = member.calculator();
    if (calc == nullptr) {
        return ec.failExpr("MemberExpr has null calculator");
    }
    const auto *baseExpr = member.base();
    if (baseExpr == nullptr) {
        return ec.failExpr("MemberExpr has null base");
    }
    llvm::Value *base = lowerExpr(ec, *baseExpr);
    if (base == nullptr) return nullptr;  // ec.fail set by lowerExpr
    return lowerMemberCalculator(ec, *calc, base, member.safe());
}

}  // namespace rell::llvm_rt
