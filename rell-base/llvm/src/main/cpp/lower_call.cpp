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
#include <vector>

#include <llvm/IR/Constants.h>
#include <llvm/IR/DerivedTypes.h>

namespace rell::llvm_rt {

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
llvm::FunctionType *userCalleeType(EmitContext &ec) {
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
llvm::Value *emitSysfnCall(EmitContext &ec, SysFnId id, llvm::ArrayRef<llvm::Value *> argVals) {
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
constexpr bool kDirectUserCallEnabled = false;

llvm::Value *emitDirectUserCall(EmitContext &ec, uint32_t fnDefIndex,
                                llvm::ArrayRef<llvm::Value *> argVals) {
    if (!kDirectUserCallEnabled) {
        // DETERMINISM: see kDirectUserCallEnabled note — the full-runtime callee ABI is not yet
        // wired, so a direct native call would link against the prototype's incompatible signature.
        // Soft-fail so the JVM interpreter runs the callee (it may re-enter the JIT itself).
        return ec.failExpr("direct user call not yet wired (callee ABI gated)");
    }

    // Bound-check the index against App.functions (defensive even though the resolver produced it).
    const auto *functions = ec.app().functions();
    if (functions == nullptr || fnDefIndex >= functions->size()) {
        return ec.failExpr("RegularUser fn_def_index out of range");
    }

    llvm::Value *ctx = currentCtxArg(ec);
    if (ctx == nullptr) {
        return ec.failExpr("emitDirectUserCall: no enclosing function ctx arg");
    }

    auto &b = ec.builder();
    auto *valTy = ec.valueType();
    const int32_t nargs = static_cast<int32_t>(argVals.size());

    // Materialise args into a stack RellValue[nargs] (entry-block alloca, hoisted), same idiom as
    // emitSysfnCall. The callee reads them positionally; the resolver already laid `argVals` out in
    // parameter order via the FullFunctionCall.mapping the caller applied before getting here.
    llvm::Function *fn = b.GetInsertBlock()->getParent();
    llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
    auto *arrTy = llvm::ArrayType::get(valTy, static_cast<uint64_t>(nargs < 0 ? 0 : nargs));
    llvm::Value *argsArr = entryB.CreateAlloca(arrTy, nullptr, "user_args");
    for (int32_t i = 0; i < nargs; ++i) {
        llvm::Value *slot =
            b.CreateInBoundsGEP(arrTy, argsArr, {b.getInt32(0), b.getInt32(i)}, "user_arg_slot");
        b.CreateStore(argVals[i], slot);
    }
    llvm::Value *argsPtr = argsArr;
    if (nargs > 0) {
        argsPtr =
            b.CreateInBoundsGEP(arrTy, argsArr, {b.getInt32(0), b.getInt32(0)}, "user_args_p");
    }

    const std::string symbol = "rell_fn_" + std::to_string(fnDefIndex);
    llvm::FunctionCallee callee = runtimeFn(ec, symbol.c_str(), userCalleeType(ec));
    return b.CreateCall(callee, {argsPtr, ctx}, "user_result");
}

// =====================================================================================
// FunctionCall dispatch. Handles ONLY FullFunctionCall; PartialFunctionCall soft-fails (a partial
// application captures wildcards into an Rt_FunctionValue closure — a heap value the JVM owns; we
// never synthesise that inline). `baseExpr` is the FunctionCallExpr.base() (nullable): present for
// a value-call `expr(args)` whose target is FnTarget_FunctionValue, absent for a plain `f(args)`.
// =====================================================================================
llvm::Value *lowerFunctionCall(EmitContext &ec, const ir::FunctionCall &call,
                               const ir::Expr *baseExpr) {
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
        case ir::FunctionCallTargetUnion_FnTarget_SysGlobal: {
            const auto *t = tgt->target_as_FnTarget_SysGlobal();
            if (t == nullptr || t->fn_name() == nullptr) {
                return ec.failExpr("FnTarget_SysGlobal missing fn_name");
            }
            // Intern the name and emit the universal stdlib back-call. (A math/integer fast path,
            // when lower_ops gains it, can pre-empt this for a recognised pure family; here we take
            // the always-correct JNI route — the stdlib is reachable by name, bit-exact.)
            const SysFnId id = ec.sysFns().intern(t->fn_name()->str());
            return emitSysfnCall(ec, id, argVals);
        }
        case ir::FunctionCallTargetUnion_FnTarget_SysMember: {
            const auto *t = tgt->target_as_FnTarget_SysMember();
            if (t == nullptr || t->fn_name() == nullptr) {
                return ec.failExpr("FnTarget_SysMember missing fn_name");
            }
            // A SysMember call's receiver, when present, is FunctionCallExpr.base(); the JVM
            // R_SysFunction takes it as args[0] (the dispatch SAM is uniform over member/global).
            // If base() is present we must evaluate it and prepend it; the resolver, however,
            // already folds the receiver into args() for SysMember in the RR-tree's FullFunctionCall
            // (the member is lowered to a flat call). So we forward argVals unchanged. If a base
            // ever arrives here we soft-fail rather than guess the receiver position.
            if (baseExpr != nullptr) {
                // DETERMINISM: receiver-position ambiguity. The flat-call resolver path keeps the
                // receiver inside args(); a non-null base here means a shape we don't model. Soft-
                // fail rather than risk passing the receiver in the wrong slot.
                return ec.failExpr("FnTarget_SysMember with explicit base not modelled");
            }
            const SysFnId id = ec.sysFns().intern(t->fn_name()->str());
            return emitSysfnCall(ec, id, argVals);
        }
        case ir::FunctionCallTargetUnion_FnTarget_NativeUser: {
            const auto *t = tgt->target_as_FnTarget_NativeUser();
            if (t == nullptr || t->fn_name() == nullptr) {
                return ec.failExpr("FnTarget_NativeUser missing fn_name");
            }
            // A native user function is a stdlib-registered R_SysFunction reachable by name — same
            // universal caller as Sys targets.
            const SysFnId id = ec.sysFns().intern(t->fn_name()->str());
            return emitSysfnCall(ec, id, argVals);
        }
        case ir::FunctionCallTargetUnion_FnTarget_RegularUser: {
            const auto *t = tgt->target_as_FnTarget_RegularUser();
            if (t == nullptr) {
                return ec.failExpr("FnTarget_RegularUser missing payload");
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
    (void)base;
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
        case ir::MemberCalculatorUnion_MemberCalculator_SysFunction: {
            const auto *t = mc.calculator_as_MemberCalculator_SysFunction();
            if (t == nullptr || t->fn_name() == nullptr) {
                return ec.failExpr("MemberCalculator_SysFunction missing fn_name");
            }
            // A bare member sys-function with no extra args: the receiver is the sole argument. We
            // forward `base` as args[0]. (Member sys-CALLS with extra args arrive as a
            // MemberCalculator_FunctionCall wrapping a FullFunctionCall — handled below.)
            const SysFnId id = ec.sysFns().intern(t->fn_name()->str());
            llvm::Value *args[] = {base};
            return emitSysfnCall(ec, id, llvm::ArrayRef<llvm::Value *>(args, 1));
        }
        case ir::MemberCalculatorUnion_MemberCalculator_FunctionCall: {
            const auto *t = mc.calculator_as_MemberCalculator_FunctionCall();
            if (t == nullptr || t->call() == nullptr) {
                return ec.failExpr("MemberCalculator_FunctionCall missing call");
            }
            // A member that is itself a call (`base.method(args)`): delegate to the FunctionCall
            // dispatcher. The receiver is carried inside the FullFunctionCall's args() by the
            // resolver (flat-call shape), so we pass no separate base.
            return lowerFunctionCall(ec, *t->call(), /*baseExpr=*/nullptr);
        }
        case ir::MemberCalculatorUnion_MemberCalculator_DataAttribute:
            // DETERMINISM: reading an entity attribute is a DB load (a SELECT on the row) — the SQL
            // bridge / interpreter owns it. A DataAttribute appears as a value-producing member;
            // lowering it inline would require re-deriving the entity SQL mapping in C++. The
            // canonical route is rell_db_eval_expr on the enclosing at-node, not a peeled attribute
            // read. Soft-fail so the interpreter performs the load.
            return ec.failExpr("MemberCalculator_DataAttribute: DB read is interpreter-only");

        case ir::MemberCalculatorUnion_MemberCalculator_StructAttr:
        case ir::MemberCalculatorUnion_MemberCalculator_TupleAttr:
            // DETERMINISM: struct/tuple values are opaque HANDLEs in the native rep (value.cpp
            // keeps composites on the JVM). A field read needs to unwrap the handle and pull the
            // attr_index()-th field — a JNI round-trip into Rt_StructValue/Rt_TupleValue. There is
            // no inline win, and modelling the field layout in C++ forks marshalling state. Until a
            // sanctioned member-accessor back-call exists, soft-fail. (This is the one place the
            // task allows "inline where the value rep allows" — the rep does NOT allow it today, so
            // the correct answer is soft-fail, not a guessed inline.)
            return ec.failExpr("MemberCalculator_Struct/TupleAttr: composite is an opaque HANDLE");

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
