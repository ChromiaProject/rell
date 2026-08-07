// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// lower_stmt.cpp — the Stmt-union lowering pass for the Rell LLVM backend.
//
// This file turns the RR statement tree (StmtUnion, 17 variants) into LLVM basic blocks for a
// single JIT'd function body. It is the control-flow half of the lowering; the value half lives
// in lower_expr.cpp (lowerExpr) / lower_ops.cpp / lower_call.cpp. The header-declared entry is
//
//     bool lowerStmt(EmitContext &ec, const ir::Stmt &stmt);
//
// returning true on success and false (after ec.fail()) to make the WHOLE function soft-fail —
// the jni_bridge return-0 path, so Rt_InterpreterImpl runs the function instead. Per the
// correctness floor (rell_runtime.h §"CORRECTNESS RULE", cheat-sheet "SOFT-FAIL CONTRACT"):
// anything we cannot lower bit-exactly must soft-fail rather than emit approximate IR.
//
// ------------------------------------------------------------------------------------------
// STATUS / BRANCH MODEL
// ------------------------------------------------------------------------------------------
// We do NOT thread a runtime "statement result" status value the way the interpreter's
// Rt_StatementResult does. Control-flow exits (return / break / continue / guard-fallthrough)
// are encoded directly as LLVM TERMINATORS on the current basic block. After lowering a
// statement that unconditionally transfers control, the current block already has a terminator;
// downstream statements in the same sequence are dead and must NOT be emitted. We track this
// with currentBlockTerminated(): a block is "open" iff it has no terminator. The IRBuilder's
// insertion block is the single source of truth for "where code goes next"; when a statement
// opens a fresh continuation block, it points the builder there.
//
// This keeps the IR shape close to a structured-CFG lowering: every compound statement creates
// its join/continuation block, lowers its arms into predecessor blocks, and leaves the builder
// positioned at the join (or leaves the join unreachable and the block closed if every arm
// terminated). Unreachable continuations are pruned by emitting `unreachable` only where the IR
// would otherwise be malformed; we prefer to simply leave the builder on a block with no
// successors and let later statements (or the function epilogue in the driver) terminate it.
//
// ------------------------------------------------------------------------------------------
// BREAK / CONTINUE BLOCK-STACK DESIGN
// ------------------------------------------------------------------------------------------
// break/continue are unlabelled in Rell and always target the innermost enclosing loop
// (WhileStatement / ForStatement). We maintain a stack of LoopTargets — one entry per loop we
// are currently lowering INTO — each holding the two LLVM blocks a jump needs:
//
//     breakTarget    : the loop's join/after block (where control resumes past the loop)
//     continueTarget : the loop's latch/condition block (where the next iteration is decided)
//
// A BreakStatement emits `br %breakTarget`; a ContinueStatement emits `br %continueTarget`;
// both then close the current block. The stack is pushed when we begin a loop body and popped
// (RAII, exception/early-return safe) when we finish it, so the innermost loop is always
// stack.back(). A break/continue with an EMPTY stack is malformed RR (the frontend rejects
// loop-free break/continue) — we soft-fail rather than trust it.
//
// The stack lives in a per-function FunctionLowering controller, addressed via a thread_local
// pointer. compileFunctionByIndex serialises all lowering under g_jitMutex and lowers exactly
// one function at a time on one thread, so a single thread_local controller is safe and is set
// up / torn down by FunctionLowering's RAII. (We use thread_local, not a plain static, purely
// as defence-in-depth against the lowering ever being driven off-lock.)
//
// ------------------------------------------------------------------------------------------
// RETURN ABI CONTRACT
// ------------------------------------------------------------------------------------------
// A ReturnStatement lowers its expression to a runtime value SSA (valueType() == { i8,i32,i64 })
// and stores it into a shared return-value alloca, then branches to a shared return block that
// the driver finalises with a single `ret`. This single-exit shape lets every nested return,
// from inside arbitrary control flow, converge without the dispatcher needing to know the
// function's calling convention. The driver (lower_expr.cpp's function builder, not yet wired)
// owns creating the entry alloca region and the trailing `ret`; lower_stmt only needs the
// builder positioned in the function's entry-reachable IR and a way to reach the return block,
// which it lazily materialises on first Return via FunctionLowering. `return;` with no
// expression (unit-returning) stores rv_unit()'s packed form.
//
// ------------------------------------------------------------------------------------------
// COVERAGE SUMMARY (see the per-handler comments for the exact envelope)
// ------------------------------------------------------------------------------------------
//   INLINE-LOWERED : Empty, Expr, Return, Block (frame-block scoped), VarStatement (alloca +
//                    Simple/Wildcard declarator), AssignStatement (plain `=` to a local slot),
//                    If, While (+ break/continue), Guard, Break, Continue.
//   ROUTED TO JNI  : Update/Delete (sql_bridge: rell_db_exec_stmt) — emitted as a status call;
//                    a negative status aborts the native frame (ABI §6).
//   SOFT-FAIL      : When (chooser lowering is value-side and not reproduced here), For (iterable
//                    adapters / collection iteration need JVM iterator semantics), Lambda,
//                    Repl-only statements, Tuple/destructuring declarators, compound-assign and
//                    non-local assignment targets, and any malformed/unknown shape. Each is a
//                    DELIBERATE soft-fail with a // DETERMINISM: rationale where bit-exactness is
//                    the reason.
//
// Style mirrors jni_bridge.cpp / the intrinsics overlays: namespace rell::llvm_rt, defensive
// null-checks even after VerifyAppBuffer, LLVM 19+ IRBuilder API, never emit IR after ec.fail().

#include "rell_runtime.h"

#include <string>
#include <string_view>
#include <vector>

#include <llvm/IR/BasicBlock.h>
#include <llvm/IR/Constants.h>
#include <llvm/IR/Function.h>
#include <llvm/IR/Instructions.h>

namespace rell::llvm_rt {

namespace {

// =====================================================================================
// Per-function lowering controller — owns the loop-target stack and the shared return block.
//
// One instance per function body, established by FunctionLoweringScope (RAII) before the body
// is lowered and torn down after. The thread_local pointer lets the fixed-signature lowerStmt
// dispatcher reach it without threading it through every recursive call.
// =====================================================================================

struct LoopTarget {
    llvm::BasicBlock *breakTarget;     // loop join / "after" block.
    llvm::BasicBlock *continueTarget;  // loop latch / condition re-test block.
};

class FunctionLowering {
public:
    explicit FunctionLowering(EmitContext &ec) : ec_(ec) {}

    EmitContext &ec() { return ec_; }

    // ---- loop-target stack -----------------------------------------------------------
    void pushLoop(llvm::BasicBlock *breakBB, llvm::BasicBlock *continueBB) {
        loops_.push_back({breakBB, continueBB});
    }
    void popLoop() { loops_.pop_back(); }
    bool inLoop() const { return !loops_.empty(); }
    const LoopTarget &innermostLoop() const { return loops_.back(); }

    // ---- shared return path ----------------------------------------------------------
    // Lazily allocate (in the function entry block) the return-value slot and the shared
    // return block. Returns false (after ec.fail()) only on a hard IR-shape fault.
    bool ensureReturnPath() {
        if (returnBlock_ != nullptr) return true;

        llvm::Function *fn = currentFunction();
        if (fn == nullptr) return ec_.fail("ReturnStatement: no enclosing function");

        // Allocate the return-value slot at the very top of the entry block, before any code,
        // so it dominates every store from every nested return. Save/restore the builder.
        llvm::IRBuilder<> &b = ec_.builder();
        llvm::BasicBlock *savedBlock = b.GetInsertBlock();
        llvm::BasicBlock::iterator savedPt = b.GetInsertPoint();

        llvm::BasicBlock &entry = fn->getEntryBlock();
        if (entry.empty()) {
            b.SetInsertPoint(&entry);
        } else {
            b.SetInsertPoint(&entry, entry.getFirstInsertionPt());
        }
        returnSlot_ = b.CreateAlloca(ec_.valueType(), nullptr, "retval.slot");

        // The shared return block is appended last; the driver attaches the final `ret`.
        returnBlock_ = llvm::BasicBlock::Create(ec_.ctx(), "return", fn);

        b.SetInsertPoint(savedBlock, savedPt);
        return true;
    }

    llvm::Value *returnSlot() const { return returnSlot_; }
    llvm::BasicBlock *returnBlock() const { return returnBlock_; }

    llvm::Function *currentFunction() {
        llvm::BasicBlock *bb = ec_.builder().GetInsertBlock();
        return bb == nullptr ? nullptr : bb->getParent();
    }

private:
    EmitContext &ec_;
    std::vector<LoopTarget> loops_;
    llvm::Value *returnSlot_ = nullptr;
    llvm::BasicBlock *returnBlock_ = nullptr;
};

// thread_local current controller. Set by FunctionLoweringScope; null between functions.
thread_local FunctionLowering *g_fnLowering = nullptr;

// RAII establishing the per-function controller. The driver wraps a body-lowering call in this.
class FunctionLoweringScope {
public:
    explicit FunctionLoweringScope(EmitContext &ec) : lowering_(ec), prev_(g_fnLowering) {
        g_fnLowering = &lowering_;
    }
    ~FunctionLoweringScope() { g_fnLowering = prev_; }

    FunctionLoweringScope(const FunctionLoweringScope &) = delete;
    FunctionLoweringScope &operator=(const FunctionLoweringScope &) = delete;

    FunctionLowering &lowering() { return lowering_; }

private:
    FunctionLowering lowering_;
    FunctionLowering *prev_;
};

// RAII push/pop of a loop target so early returns (soft-fail) cannot leave the stack unbalanced.
class LoopScope {
public:
    LoopScope(FunctionLowering &fl, llvm::BasicBlock *breakBB, llvm::BasicBlock *continueBB)
        : fl_(fl) {
        fl_.pushLoop(breakBB, continueBB);
    }
    ~LoopScope() { fl_.popLoop(); }

    LoopScope(const LoopScope &) = delete;
    LoopScope &operator=(const LoopScope &) = delete;

private:
    FunctionLowering &fl_;
};

// =====================================================================================
// Small IR helpers.
// =====================================================================================

// True iff the builder's current insertion block already has a terminator (a return/break/
// continue or a branch we just emitted). When true, no further straight-line code may be added
// to it; the surrounding handler must either open a fresh block or stop emitting (dead code).
bool currentBlockTerminated(EmitContext &ec) {
    llvm::BasicBlock *bb = ec.builder().GetInsertBlock();
    return bb != nullptr && bb->getTerminator() != nullptr;
}

llvm::Function *currentFunction(EmitContext &ec) {
    llvm::BasicBlock *bb = ec.builder().GetInsertBlock();
    return bb == nullptr ? nullptr : bb->getParent();
}

// Fetch the active per-function controller, or soft-fail if lowering was driven without a
// FunctionLoweringScope established (a driver bug — never emit IR in that state).
FunctionLowering *requireLowering(EmitContext &ec) {
    if (g_fnLowering == nullptr) {
        ec.fail("lowerStmt: no FunctionLoweringScope active (driver must establish one)");
        return nullptr;
    }
    return g_fnLowering;
}

// =====================================================================================
// Forward decls for the mutually-recursive handlers.
// =====================================================================================

bool lowerEmpty(EmitContext &ec, const ir::EmptyStatement &stmt);
bool lowerExprStmt(EmitContext &ec, const ir::ExprStatement &stmt);
bool lowerReturn(EmitContext &ec, const ir::ReturnStatement &stmt);
bool lowerBlock(EmitContext &ec, const ir::BlockStatement &stmt);
bool lowerVar(EmitContext &ec, const ir::VarStatement &stmt);
bool lowerAssign(EmitContext &ec, const ir::AssignStatement &stmt);
bool lowerIf(EmitContext &ec, const ir::IfStatement &stmt);
bool lowerWhile(EmitContext &ec, const ir::WhileStatement &stmt);
bool lowerBreak(EmitContext &ec, const ir::BreakStatement &stmt);
bool lowerContinue(EmitContext &ec, const ir::ContinueStatement &stmt);
bool lowerGuard(EmitContext &ec, const ir::GuardStatement &stmt);
bool lowerWhenStmt(EmitContext &ec, const ir::WhenStatement &stmt);
bool lowerForStmt(EmitContext &ec, const ir::ForStatement &stmt);
bool lowerForOverList(EmitContext &ec, const ir::ForStatement &stmt);
bool lowerDbWrite(EmitContext &ec, const ir::Stmt &stmt);

// =====================================================================================
// EmptyStatement — no-op. The interpreter does nothing; so do we.
// =====================================================================================
bool lowerEmpty(EmitContext &ec, const ir::EmptyStatement & /*stmt*/) {
    (void)ec;
    return true;
}

// =====================================================================================
// ExprStatement — evaluate the expression for its side effects, discard the value.
//
// The expression may itself be a JNI back-call (a stdlib op, a db at-expr used as a statement)
// whose side effects matter; we lower it and ignore the result SSA. If lowerExpr soft-fails it
// has already called ec.fail(); we propagate.
// =====================================================================================
bool lowerExprStmt(EmitContext &ec, const ir::ExprStatement &stmt) {
    const auto *expr = stmt.expr();
    if (expr == nullptr) return ec.fail("ExprStatement: null expr");
    llvm::Value *v = lowerExpr(ec, *expr);
    if (v == nullptr) return false;  // ec.fail() already set by lowerExpr.
    (void)v;
    return true;
}

// =====================================================================================
// ReturnStatement — store the result into the shared return slot and jump to the return block.
//
// `return <e>;` lowers <e> to a runtime value; `return;` (unit-returning function) stores the
// packed rv_unit() form. After the store+branch the current block is terminated; any following
// statement in the same sequence is dead (the block handler stops emitting).
//
// RETURN ABI: see the file header. The single shared return block converges every nested
// return so the dispatcher stays calling-convention-agnostic; the driver attaches the final
// `ret` that reads the slot.
// =====================================================================================
bool lowerReturn(EmitContext &ec, const ir::ReturnStatement &stmt) {
    FunctionLowering *fl = requireLowering(ec);
    if (fl == nullptr) return false;
    if (!fl->ensureReturnPath()) return false;

    auto &b = ec.builder();

    llvm::Value *result = nullptr;
    const auto *expr = stmt.expr();
    if (expr == nullptr) {
        // `return;` — unit. Pack rv_unit(): tag=UNIT, scale=0, payload=0.
        result = ec.packInline(RellTag::UNIT, b.getInt32(0), b.getInt64(0));
    } else {
        result = lowerExpr(ec, *expr);
        if (result == nullptr) return false;  // soft-fail propagated.
    }

    b.CreateStore(result, fl->returnSlot());
    b.CreateBr(fl->returnBlock());
    return true;
}

// =====================================================================================
// BlockStatement — a lexical block with its own FrameBlock scope.
//
// We lower the contained statements in order. Frame-block scoping: the locals declared inside
// belong to frame_block(); their slots are keyed on (block_uid, offset) in ec.slots() (the same
// key space jni_bridge uses for params). The CallFrame pre-sizes every block, and VarPtr keys
// are globally unique across the frame, so we do NOT need to push/pop a scope or re-zero slots
// here — a VarStatement registers its slot on declaration and the key uniqueness prevents
// cross-block aliasing. (Re-entering a block, e.g. a loop body, RE-DECLARES via VarStatement,
// which re-binds the same alloca; correct because Rell block-locals are fresh per entry only in
// value, not in storage identity, and our allocas are entry-block-hoisted.)
//
// Sequencing rule: once a contained statement terminates the current block (return/break/
// continue), the rest of the block is unreachable. We stop emitting further statements — they
// would be dead and could reference an already-closed block. This matches the interpreter
// short-circuiting on a non-NORMAL Rt_StatementResult.
// =====================================================================================
bool lowerBlock(EmitContext &ec, const ir::BlockStatement &stmt) {
    if (stmt.frame_block() == nullptr) {
        // (required) per schema, but soft-fail defensively rather than deref null downstream.
        return ec.fail("BlockStatement: null frame_block");
    }
    const auto *stmts = stmt.stmts();
    if (stmts == nullptr) return ec.fail("BlockStatement: null stmts vector");

    for (flatbuffers::uoffset_t i = 0; i < stmts->size(); ++i) {
        if (currentBlockTerminated(ec)) {
            // Remaining statements are unreachable (a prior return/break/continue closed the
            // block). The interpreter would never reach them either; stop emitting.
            break;
        }
        const auto *inner = stmts->Get(i);
        if (inner == nullptr) return ec.fail("BlockStatement: null inner statement");
        if (!lowerStmt(ec, *inner)) return false;
    }
    return true;
}

// =====================================================================================
// VarStatement — local variable declaration (`val`/`var x [= init]`).
//
// We support the SimpleVarDeclarator and WildcardVarDeclarator forms:
//   - Simple: alloca a runtime-value slot, register it in ec.slots() keyed on the declarator's
//     VarPtr (block_uid, offset), and store the initializer if present. A declarator with a
//     NULLABLE ptr (no backing storage — e.g. a discarded binding) behaves like Wildcard.
//   - Wildcard: evaluate the initializer for side effects (if present) and discard it.
//
// SOFT-FAIL:
//   - TupleVarDeclarator (destructuring `val (a, b) = ...`): correct lowering must materialise
//     the tuple value and project each field with the JVM's exact tuple-field semantics; the
//     projection crosses the HANDLE boundary and is not yet reproduced inline. DETERMINISM:
//     route the whole function to the JVM rather than guess field extraction.
//   - A declarator carrying a value-changing TypeAdapter (the `adapter()` field with a non-DIRECT
//     kind) implies an implicit conversion (e.g. integer->decimal) on the stored value. We inline
//     a DIRECT adapter (the identity no-op the frontend attaches even for same-type initializers
//     such as `var acc = 0;`) and an absent adapter; any non-DIRECT adapter soft-fails so the JVM
//     applies the exact conversion. (lowerExpr may itself surface the adapter via TypeAdapterExpr;
//     if so it is handled there and the declarator adapter is absent or DIRECT.)
//
// The alloca is hoisted to the function entry block so it dominates all uses regardless of the
// control-flow position of the declaration (loops, branches). The slot type is valueType().
// =====================================================================================
bool lowerVar(EmitContext &ec, const ir::VarStatement &stmt) {
    const auto *declarator = stmt.declarator();
    if (declarator == nullptr) return ec.fail("VarStatement: null declarator");

    const auto *initExpr = stmt.expr();  // NULLABLE — no initializer.

    switch (declarator->declarator_type()) {
        case ir::VarDeclaratorUnion_WildcardVarDeclarator: {
            // Evaluate the initializer for side effects; discard. No slot.
            if (initExpr != nullptr) {
                llvm::Value *v = lowerExpr(ec, *initExpr);
                if (v == nullptr) return false;
                (void)v;
            }
            return true;
        }
        case ir::VarDeclaratorUnion_SimpleVarDeclarator: {
            const auto *simple = declarator->declarator_as_SimpleVarDeclarator();
            if (simple == nullptr) return ec.fail("VarStatement: SimpleVarDeclarator null");

            // A non-DIRECT type adapter means an implicit conversion on the stored value. We inline
            // the closed set of MECHANICAL numeric-widening adapters and soft-fail the rest:
            //   DIRECT                 — identity no-op the frontend attaches even for same-type
            //                            initializers (e.g. `var acc = 0;` carries a DIRECT
            //                            integer->integer adapter); accepted exactly as the i64
            //                            oracle (jni_bridge.cpp Lowerer::lowerVarStmt) does.
            //   INTEGER_TO_BIG_INTEGER — re-tag the i64 payload as BIGINT_LONG (scale 0). Bit-exact:
            //                            an i64 integer always fits the inline big_integer slice
            //                            (|v| <= Long.MAX), and to_jvm reboxes it via
            //                            BigInteger.valueOf — identical to the interpreter's
            //                            integer->big_integer conversion (value.cpp BIGINT_LONG).
            //   INTEGER_TO_DECIMAL     — re-tag as DEC_LONG with mantissa = the i64, scale 0.
            //                            Bit-exact: to_jvm reboxes via BigDecimal.valueOf(mantissa,
            //                            0) -> Rt_DecimalValue.get, the interpreter's integer->
            //                            decimal path (value.cpp DEC_LONG).
            // SOFT-FAIL: BIG_INTEGER_TO_DECIMAL (the big_integer operand may be a HANDLE at runtime,
            //   not an inline i64) and NULLABLE (carries an inner adapter + null handling). Both
            //   route the whole function to the JVM for a bit-exact conversion.
            const auto *adapter = simple->adapter();
            ir::TypeAdapterKind adapterKind = ir::TypeAdapterKind_DIRECT;
            if (adapter != nullptr) {
                adapterKind = adapter->kind();
                if (adapterKind != ir::TypeAdapterKind_DIRECT &&
                    adapterKind != ir::TypeAdapterKind_INTEGER_TO_BIG_INTEGER &&
                    adapterKind != ir::TypeAdapterKind_INTEGER_TO_DECIMAL) {
                    return ec.fail(
                        "VarStatement: declarator type adapter is not a mechanical integer-widening "
                        "conversion (soft-fail)");
                }
            }

            const auto *ptr = simple->ptr();  // NULLABLE — discarded binding behaves as wildcard.
            if (ptr == nullptr) {
                if (initExpr != nullptr) {
                    llvm::Value *v = lowerExpr(ec, *initExpr);
                    if (v == nullptr) return false;
                    (void)v;
                }
                return true;
            }

            llvm::Function *fn = currentFunction(ec);
            if (fn == nullptr) return ec.fail("VarStatement: no enclosing function");

            // Hoist the slot alloca to the entry block so it dominates every use.
            auto &b = ec.builder();
            llvm::BasicBlock *savedBlock = b.GetInsertBlock();
            llvm::BasicBlock::iterator savedPt = b.GetInsertPoint();
            llvm::BasicBlock &entry = fn->getEntryBlock();
            if (entry.empty()) {
                b.SetInsertPoint(&entry);
            } else {
                b.SetInsertPoint(&entry, entry.getFirstInsertionPt());
            }
            llvm::Value *slot = b.CreateAlloca(ec.valueType(), nullptr, "local.slot");
            b.SetInsertPoint(savedBlock, savedPt);

            // Register the slot so VarExpr reads and AssignStatement writes find it.
            ec.slots()[{ptr->block_uid(), ptr->offset()}] = slot;

            // Store the initializer (if any) at the declaration point, applying the mechanical
            // integer-widening adapter inline. The init is integer-typed (so INTEGER-tagged at
            // runtime); the widening is a pure re-tag of the same i64 payload — bit-exact with the
            // interpreter's integer->big_integer / integer->decimal conversion (see the adapter
            // note above and value.cpp's BIGINT_LONG / DEC_LONG rebox).
            if (initExpr != nullptr) {
                llvm::Value *init = lowerExpr(ec, *initExpr);
                if (init == nullptr) return false;
                if (adapterKind == ir::TypeAdapterKind_INTEGER_TO_BIG_INTEGER) {
                    init = ec.packInline(RellTag::BIGINT_LONG, b.getInt32(0),
                                         ec.unpackPayloadI64(init));
                } else if (adapterKind == ir::TypeAdapterKind_INTEGER_TO_DECIMAL) {
                    init = ec.packInline(RellTag::DEC_LONG, b.getInt32(0),
                                         ec.unpackPayloadI64(init));
                }
                b.CreateStore(init, slot);
            }
            return true;
        }
        case ir::VarDeclaratorUnion_TupleVarDeclarator:
            // DETERMINISM: destructuring projects tuple fields whose extraction crosses the
            // HANDLE boundary; not reproduced inline. Whole function -> JVM.
            return ec.fail("VarStatement: tuple destructuring declarator (soft-fail)");
        default:
            return ec.fail("VarStatement: unknown declarator variant " +
                           std::to_string(static_cast<int>(declarator->declarator_type())));
    }
}

// =====================================================================================
// AssignStatement — assignment to an lvalue.
//
// INLINE-COVERED: plain `=` (op absent) whose destination is a local/param slot, i.e. a
// VarExpr resolving to an entry in ec.slots(). We lower the RHS to a runtime value and store it.
//
// SOFT-FAIL:
//   - Compound assignment with a non-integer or div/mod op: the read-modify-write must reproduce
//     the operator's exact overflow/Rt_Exception semantics. We reproduce only the wrapping/checked
//     integer ADD/SUB/MUL family inline (via intrinsicInteger, exactly as lowerIntegerArith does);
//     DIV/MOD and any non-integer compound op soft-fail to the JVM. DETERMINISM: this mirrors the
//     i64 oracle (jni_bridge.cpp Lowerer::applyIntArith), which JITs the same integer ADD/SUB/MUL
//     family for `+=`/`-=`/`*=` and soft-fails the rest.
//   - Non-local destinations: subscript stores (list[i]=, map[k]=), member/attribute stores
//     (struct.field=, entity attr writes), and anything that is not a plain VarExpr-to-slot.
//     These either mutate heap/db state through JVM-defined semantics or are db writes; they
//     must marshal through the JVM. DETERMINISM/SQL: soft-fail (or, for entity attrs, the
//     statement arrives as Update — handled there, not here).
// =====================================================================================
bool lowerAssign(EmitContext &ec, const ir::AssignStatement &stmt) {
    const auto *dst = stmt.dst_expr();
    const auto *src = stmt.expr();
    if (dst == nullptr || src == nullptr) return ec.fail("AssignStatement: null dst/src");

    // The only inline-lowerable destination is a local/param slot (a VarExpr whose ptr is in
    // ec.slots()). Anything else (subscript/member/attribute) is non-local -> soft-fail.
    if (dst->expr_type() != ir::ExprUnion_VarExpr) {
        return ec.fail("AssignStatement: non-local assignment target (soft-fail)");
    }
    const auto *varExpr = dst->expr_as_VarExpr();
    if (varExpr == nullptr) return ec.fail("AssignStatement: VarExpr null");
    const auto *ptr = varExpr->ptr();
    if (ptr == nullptr) return ec.fail("AssignStatement: VarExpr.ptr null");

    llvm::Value *slot = ec.slotFor(*ptr);
    if (slot == nullptr) {
        // No registered slot: either a parameter carried by a read-only GEP (the prototype's
        // i64* args path, which is not assignable as a runtime-value slot) or an out-of-scope
        // ptr. Either way we cannot safely store; soft-fail.
        return ec.fail("AssignStatement: destination has no assignable slot (soft-fail)");
    }

    llvm::Value *value = lowerExpr(ec, *src);
    if (value == nullptr) return false;  // soft-fail propagated.

    // op() is a ::flatbuffers::Optional<BinaryOp>: PRESENT ⇒ compound assignment (`+=`, `-=`,
    // ...); ABSENT ⇒ plain `=`. For compound assignment we read-modify-write: load the current
    // slot value, combine it with the rhs via the SAME checked integer envelope the standalone
    // binary ops use, then store. DETERMINISM: only the wrapping ADD/SUB/MUL integer family is
    // bit-reproducible inline (intrinsicInteger); DIV/MOD and any non-integer op soft-fail — the
    // exact decision the i64 oracle's applyIntArith makes.
    if (stmt.op().has_value()) {
        const ir::BinaryOp op = stmt.op().value();
        if (op != ir::BinaryOp_ADD_INTEGER && op != ir::BinaryOp_SUB_INTEGER &&
            op != ir::BinaryOp_MUL_INTEGER) {
            return ec.fail("AssignStatement: compound op not in wrapping integer ADD/SUB/MUL "
                           "family (soft-fail)");
        }
        llvm::Value *cur = ec.builder().CreateLoad(ec.valueType(), slot, "compound.cur");
        bool escaped = false;
        llvm::Value *combined = intrinsicInteger(
            ec, op, ec.unpackPayloadI64(cur), ec.unpackPayloadI64(value), &escaped);
        if (escaped) {
            return ec.fail("AssignStatement: compound integer arith escaped inline envelope");
        }
        if (combined == nullptr) return false;  // intrinsic already called ec.fail().
        value = combined;
    }

    ec.builder().CreateStore(value, slot);
    return true;
}

// =====================================================================================
// IfStatement — two-armed conditional.
//
// We lower cond() to a runtime BOOLEAN value, extract its i64 payload (in {0,1}; the frontend
// types the condition as boolean, and lowerExpr produces a BOOLEAN-tagged value), compare != 0,
// and branch to the then/else blocks. After lowering each arm we branch to a shared join block
// IFF the arm did not already terminate (return/break/continue). If BOTH arms terminate, the
// join is unreachable; we still create it but leave the builder there only if it has a
// predecessor — otherwise we erase it to keep the IR well-formed.
//
// false_stmt() is (required) in the schema; a Rell `if` with no else carries an EmptyStatement.
// DETERMINISM: the condition's truth is the JVM boolean payload (0/1) — no fuzzy coercion; if
// lowerExpr ever yields a non-BOOLEAN here that is a lowering bug surfaced upstream, not masked.
// =====================================================================================
bool lowerIf(EmitContext &ec, const ir::IfStatement &stmt) {
    const auto *cond = stmt.cond();
    const auto *thenStmt = stmt.true_stmt();
    const auto *elseStmt = stmt.false_stmt();
    if (cond == nullptr || thenStmt == nullptr || elseStmt == nullptr) {
        return ec.fail("IfStatement: null cond/true_stmt/false_stmt");
    }

    llvm::Function *fn = currentFunction(ec);
    if (fn == nullptr) return ec.fail("IfStatement: no enclosing function");
    auto &b = ec.builder();

    llvm::Value *condVal = lowerExpr(ec, *cond);
    if (condVal == nullptr) return false;
    // BOOLEAN payload is 0/1; truthy iff payload != 0.
    llvm::Value *condI64 = ec.unpackPayloadI64(condVal);
    llvm::Value *condBit = b.CreateICmpNE(condI64, b.getInt64(0), "if.cond");

    auto *thenBB = llvm::BasicBlock::Create(ec.ctx(), "if.then", fn);
    auto *elseBB = llvm::BasicBlock::Create(ec.ctx(), "if.else", fn);
    auto *joinBB = llvm::BasicBlock::Create(ec.ctx(), "if.end", fn);
    b.CreateCondBr(condBit, thenBB, elseBB);

    // then arm.
    b.SetInsertPoint(thenBB);
    if (!lowerStmt(ec, *thenStmt)) return false;
    if (!currentBlockTerminated(ec)) b.CreateBr(joinBB);

    // else arm.
    b.SetInsertPoint(elseBB);
    if (!lowerStmt(ec, *elseStmt)) return false;
    if (!currentBlockTerminated(ec)) b.CreateBr(joinBB);

    // join. If no arm fell through, the join has no predecessors — erase it so verifyFunction
    // does not see an unreachable, predecessor-less block, and leave the builder on the (now
    // terminated) else block; the enclosing sequence will see currentBlockTerminated()==true.
    if (joinBB->hasNPredecessorsOrMore(1)) {
        b.SetInsertPoint(joinBB);
    } else {
        joinBB->eraseFromParent();
        // Builder is on elseBB, which is terminated; the caller's block loop stops here.
    }
    return true;
}

// =====================================================================================
// WhileStatement — pre-test loop with break/continue targets.
//
// Shape:
//     br %cond
//   cond:  %c = <cond>; br %c, %body, %after
//   body:  <body>; br %cond            (continue target == cond)
//   after: ...                          (break target == after)
//
// continue jumps to %cond (re-evaluate the guard); break jumps to %after. We push the
// LoopTarget {after, cond} for the duration of the body so nested break/continue resolve to the
// innermost loop. After the body, if it did not terminate, we branch back to %cond. The builder
// finishes on %after.
//
// frame_block() scopes the body's locals exactly as for BlockStatement; the body Stmt is
// typically a BlockStatement carrying that frame_block, so re-declaration on each iteration
// re-binds the hoisted slot — correct for Rell loop-local semantics (value-fresh per iteration).
// =====================================================================================
bool lowerWhile(EmitContext &ec, const ir::WhileStatement &stmt) {
    const auto *cond = stmt.cond();
    const auto *body = stmt.body();
    if (cond == nullptr || body == nullptr) return ec.fail("WhileStatement: null cond/body");
    if (stmt.frame_block() == nullptr) return ec.fail("WhileStatement: null frame_block");

    FunctionLowering *fl = requireLowering(ec);
    if (fl == nullptr) return false;

    llvm::Function *fn = currentFunction(ec);
    if (fn == nullptr) return ec.fail("WhileStatement: no enclosing function");
    auto &b = ec.builder();

    auto *condBB = llvm::BasicBlock::Create(ec.ctx(), "while.cond", fn);
    auto *bodyBB = llvm::BasicBlock::Create(ec.ctx(), "while.body", fn);
    auto *afterBB = llvm::BasicBlock::Create(ec.ctx(), "while.after", fn);

    b.CreateBr(condBB);

    // condition.
    b.SetInsertPoint(condBB);
    llvm::Value *condVal = lowerExpr(ec, *cond);
    if (condVal == nullptr) return false;
    llvm::Value *condI64 = ec.unpackPayloadI64(condVal);
    llvm::Value *condBit = b.CreateICmpNE(condI64, b.getInt64(0), "while.test");
    b.CreateCondBr(condBit, bodyBB, afterBB);

    // body, with break -> afterBB, continue -> condBB.
    b.SetInsertPoint(bodyBB);
    {
        LoopScope loop(*fl, afterBB, condBB);
        if (!lowerStmt(ec, *body)) return false;
    }
    if (!currentBlockTerminated(ec)) b.CreateBr(condBB);

    // continuation.
    b.SetInsertPoint(afterBB);
    return true;
}

// =====================================================================================
// BreakStatement / ContinueStatement — innermost-loop control transfer.
//
// break  -> br %breakTarget (loop's after block); continue -> br %continueTarget (loop's cond/
// latch block). Both then close the current block. An empty loop stack means a break/continue
// outside any loop, which the frontend rejects — reaching it here is malformed RR, so soft-fail.
// =====================================================================================
bool lowerBreak(EmitContext &ec, const ir::BreakStatement & /*stmt*/) {
    FunctionLowering *fl = requireLowering(ec);
    if (fl == nullptr) return false;
    if (!fl->inLoop()) return ec.fail("BreakStatement: not inside a loop (malformed)");
    ec.builder().CreateBr(fl->innermostLoop().breakTarget);
    return true;
}

bool lowerContinue(EmitContext &ec, const ir::ContinueStatement & /*stmt*/) {
    FunctionLowering *fl = requireLowering(ec);
    if (fl == nullptr) return false;
    if (!fl->inLoop()) return ec.fail("ContinueStatement: not inside a loop (malformed)");
    ec.builder().CreateBr(fl->innermostLoop().continueTarget);
    return true;
}

// =====================================================================================
// GuardStatement — the body of an operation's `guard { ... }` block.
//
// A guard is a leading statement block that must execute before any database mutation; the
// interpreter runs guard_body() in the guard region of the frame. From a control-flow lowering
// view it is just its inner body Stmt — there is no extra branching to introduce here (the
// "no db writes before the guard completes" invariant is enforced by the frontend's placement of
// the guard, not by a runtime barrier we emit). We lower body() straight through.
//
// has_guard_block() on the CallFrame and the guard's frame region are pre-sized by the frame; we
// do not re-scope. If the guard body contains anything we cannot lower, that inner soft-fail
// propagates and takes the whole function to the JVM — correct, since a partially-native guard
// could not preserve the pre-mutation ordering guarantee anyway.
// =====================================================================================
bool lowerGuard(EmitContext &ec, const ir::GuardStatement &stmt) {
    const auto *body = stmt.body();
    if (body == nullptr) return ec.fail("GuardStatement: null body");
    return lowerStmt(ec, *body);
}

// =====================================================================================
// WhenStatement — `when (key) { ... }` as a statement (rt_interp_stmt.kt executeWhenStmt).
//
// The interpreter evaluates the chooser to an arm INDEX (or -1 for "no match and no else"), then
// runs stmts[idx]; on -1 it is a no-op (falls through). This is the value-less twin of WhenExpr:
// we reuse the EXACT chooser semantics and the SAME inline-key-type restriction (lower_expr.cpp's
// whenKeyTypeInlineComparable / emitWhenInlineEq), so a statement-`when` matches bit-for-bit with
// an expression-`when`. Any non-inline key (ENUM is inline-comparable; text/decimal/bigint/etc. are
// NOT) or a chooser shape we cannot reduce to inline equality soft-fails the whole function.
//
// DETERMINISM divergence from WhenExpr: a when-STATEMENT need NOT be exhaustive — `when (x) { 1 ->
// ...; }` with no else and no match is a legal no-op (executeWhenStmt returns null). So a MISSING
// else_index is NOT a soft-fail here (it is for the expression); it means "fall through to the
// continuation with no arm executed", which we lower as the final test's else edge going straight
// to the join block. (We still soft-fail a keyless guard chain — see below — only where bit-exact
// reproduction is not provable.)
//
// Arm sequencing mirrors lowerIf/lowerWhile: each selected arm lowers into its own block and, if it
// did not itself terminate (return/break/continue), branches to the shared join. If every reachable
// path terminates, the join is erased.
// =====================================================================================

// Lower the statement arm at `armIndex` into the current block, then branch to joinBB iff the arm
// did not already terminate. Shared by both chooser shapes.
bool emitWhenStmtArm(EmitContext &ec, const ir::WhenStatement &stmt, int32_t armIndex,
                     llvm::BasicBlock *joinBB) {
    const auto *stmts = stmt.stmts();
    if (stmts == nullptr || armIndex < 0 ||
        static_cast<uint32_t>(armIndex) >= stmts->size()) {
        return ec.fail("WhenStatement: selected arm index out of range");
    }
    const ir::Stmt *arm = stmts->Get(armIndex);
    if (arm == nullptr) return ec.fail("WhenStatement: arm statement is null");
    if (!lowerStmt(ec, *arm)) return false;
    if (!currentBlockTerminated(ec)) ec.builder().CreateBr(joinBB);
    return true;
}

bool lowerWhenStmt(EmitContext &ec, const ir::WhenStatement &stmt) {
    const auto *chooserWrap = stmt.chooser();
    if (chooserWrap == nullptr) return ec.fail("WhenStatement: null chooser");
    if (stmt.stmts() == nullptr) return ec.fail("WhenStatement: null stmts");

    auto &b = ec.builder();
    llvm::Function *fn = currentFunction(ec);
    if (fn == nullptr) return ec.fail("WhenStatement: no enclosing function");

    auto *joinBB = llvm::BasicBlock::Create(ec.ctx(), "when_stmt_end", fn);

    switch (chooserWrap->chooser_type()) {
        case ir::WhenChooserUnion_IterativeWhenChooser: {
            const auto *chooser = chooserWrap->chooser_as_IterativeWhenChooser();
            if (chooser == nullptr) return ec.fail("WhenStatement: null iterative chooser");

            const ir::Expr *keyExpr = chooser->key_expr();
            llvm::Value *keyVal = nullptr;
            if (keyExpr != nullptr) {
                // DETERMINISM: only inline-canonical key carriers can be matched by tag+payload eq.
                if (!whenKeyTypeInlineComparable(exprStaticResultType(keyExpr))) {
                    return ec.fail("WhenStatement: key is not an inline-comparable type (soft-fail)");
                }
                keyVal = lowerExpr(ec, *keyExpr);
                if (keyVal == nullptr) return false;
            }

            const auto *conds = chooser->conditions();
            if (conds == nullptr) return ec.fail("WhenStatement: null conditions");

            for (uint32_t i = 0; i < conds->size(); ++i) {
                const auto *cond = conds->Get(i);
                if (cond == nullptr) return ec.fail("WhenStatement: null WhenCondition");
                const ir::Expr *condExpr = cond->expr();
                if (condExpr == nullptr) return ec.fail("WhenStatement: null condition expr");

                llvm::Value *condVal = lowerExpr(ec, *condExpr);
                if (condVal == nullptr) return false;

                // With a key: arm fires when key == condVal. Without a key (keyless guard chain):
                // condExpr IS a boolean guard, fires when its payload is truthy. Both are bit-exact
                // with evaluateWhenChooser (the interpreter's per-condition value equality / boolean
                // test), the same shape lowerWhenIterative uses for the expression form.
                llvm::Value *match;
                if (keyVal != nullptr) {
                    match = emitWhenInlineEq(ec, keyVal, condVal);
                    if (match == nullptr) return false;
                } else {
                    llvm::Value *p = ec.unpackPayloadI64(condVal);
                    match = b.CreateICmpNE(p, b.getInt64(0), "when_stmt_guard");
                }

                auto *armBB = llvm::BasicBlock::Create(ec.ctx(), "when_stmt_arm", fn);
                auto *nextBB = llvm::BasicBlock::Create(ec.ctx(), "when_stmt_next", fn);
                b.CreateCondBr(match, armBB, nextBB);

                b.SetInsertPoint(armBB);
                if (!emitWhenStmtArm(ec, stmt, cond->index(), joinBB)) return false;

                b.SetInsertPoint(nextBB);
            }

            // Fallthrough = else (if present) or no-op (statement-`when` need not be exhaustive).
            auto elseIndexOpt = chooser->else_index();
            if (elseIndexOpt.has_value()) {
                if (!emitWhenStmtArm(ec, stmt, *elseIndexOpt, joinBB)) return false;
            } else {
                // No else: no arm runs (executeWhenStmt returns null). Branch straight to the join.
                b.CreateBr(joinBB);
            }
            break;
        }
        case ir::WhenChooserUnion_LookupWhenChooser: {
            const auto *chooser = chooserWrap->chooser_as_LookupWhenChooser();
            if (chooser == nullptr) return ec.fail("WhenStatement: null lookup chooser");

            const ir::Expr *keyExpr = chooser->key_expr();
            if (keyExpr == nullptr) return ec.fail("WhenStatement: lookup chooser null key_expr");
            if (!whenKeyTypeInlineComparable(exprStaticResultType(keyExpr))) {
                return ec.fail("WhenStatement: lookup key is not inline-comparable (soft-fail)");
            }
            llvm::Value *keyVal = lowerExpr(ec, *keyExpr);
            if (keyVal == nullptr) return false;

            const auto *keys = chooser->lookup_keys();
            const auto *values = chooser->lookup_values();
            if (keys == nullptr || values == nullptr || keys->size() != values->size()) {
                return ec.fail("WhenStatement: lookup keys/values mismatch");
            }

            for (uint32_t i = 0; i < keys->size(); ++i) {
                const auto *constKey = keys->Get(i);
                if (constKey == nullptr) return ec.fail("WhenStatement: null lookup key");
                llvm::Value *keyConst = lowerInlineConstant(ec, *constKey);
                if (keyConst == nullptr) return false;  // non-inline key -> already soft-failed.

                llvm::Value *match = emitWhenInlineEq(ec, keyVal, keyConst);
                if (match == nullptr) return false;

                auto *armBB = llvm::BasicBlock::Create(ec.ctx(), "lookup_stmt_arm", fn);
                auto *nextBB = llvm::BasicBlock::Create(ec.ctx(), "lookup_stmt_next", fn);
                b.CreateCondBr(match, armBB, nextBB);

                b.SetInsertPoint(armBB);
                if (!emitWhenStmtArm(ec, stmt, values->Get(i), joinBB)) return false;

                b.SetInsertPoint(nextBB);
            }

            auto elseIndexOpt = chooser->else_index();
            if (elseIndexOpt.has_value()) {
                if (!emitWhenStmtArm(ec, stmt, *elseIndexOpt, joinBB)) return false;
            } else {
                b.CreateBr(joinBB);
            }
            break;
        }
        default:
            return ec.fail("WhenStatement: unsupported chooser variant");
    }

    // Position the builder at the join for the following statements. If nothing reached it (every
    // path terminated), erase it and leave the builder on the terminated block so the enclosing
    // sequence sees currentBlockTerminated()==true.
    if (joinBB->hasNPredecessorsOrMore(1)) {
        b.SetInsertPoint(joinBB);
    } else {
        joinBB->eraseFromParent();
    }
    return true;
}

// =====================================================================================
// ForStatement over an integer RANGE iterable (REVIEW_coverage target 4).
//
// Rell `for (i in <range>) <body>` where <range> is built by the `range(...)` constructor (the
// `..` operator desugars to the same constructor). We lower ONLY this case natively; for-over-
// collection (list/set/map, the LEGACY_MAP map-entry adapter) and any range value that does NOT
// arrive as an inline range(...) construction soft-fail — those need JVM iterator/marshalling
// semantics, not reproducible inline.
//
// WHY INLINE CONSTRUCTION (not a HANDLE): a `range(...)` call routes through the stdlib sysfn path,
// which currently SOFT-FAILS the whole function (the rell_sysfn_call by-value return ABI is not yet
// sret-wired — lower_call.cpp). So we cannot obtain a range HANDLE. Instead we recognise the
// constructor call, lower its integer arguments inline, and reproduce the range bit-exactly:
//
// BIT-EXACTNESS (vs rt_interp_stmt.kt RR_Statement.For, lib_type_range.kt calcRange,
// rt_value_range.kt Rt_RangeValue.RangeIterator, utils_number.kt saturatedAdd):
//   * Argument layout matches the two `range` constructors (lib_type_range.kt):
//       range(end)            -> start=0, end=end, step=1
//       range(start,end)      -> step=1
//       range(start,end,step) -> as given
//     Args are integer-typed exprs (INTEGER-tagged at runtime); we lower each inline and unpack i64.
//   * VALIDATION matches calcRange EXACTLY: invalid iff
//       step == 0 || (step > 0 && start > end) || (step < 0 && start < end).
//     On the invalid edge we emit rell_jit_escape() — the JVM RE-RUNS the whole call on the
//     interpreter (Llvm_Backend.invokeValueNative), which raises the precise `fn_range_args:
//     start:end:step` Rt_Exception. We do NOT synthesise that throw inline (its dynamic code/message
//     cannot be reproduced); the escape channel makes the error bit-exact. The valid edge proceeds.
//   * ITERATION mirrors RangeIterator EXACTLY:
//       - element = current; current = saturatedAdd(current, step) BEFORE the body runs (the
//         interpreter's `for (e in iterator)` calls next() — which reads then advances — before the
//         body). So `continue` re-tests hasNext WITHOUT re-advancing: continue target == cond,
//         identical to WhileStatement and to the Kotlin for-loop.
//       - hasNext: step > 0 ? current < end : current > end  (exclusive end; sign-of-step bound).
//         step != 0 is guaranteed on the valid (non-escape) path, so the two-way select is total.
//       - empty range (range(n,n), or start==end): hasNext is false on entry -> 0 iterations.
//       - advance = saturatedAdd: wrapping i64 add, then on signed overflow clamp to Long.MAX
//         (element >= 0) / Long.MIN, using the SAME predicate ((element ^ sum) & (step ^ sum)) < 0
//         and the SAME clamp, so a range whose advance would overflow terminates identically (the
//         clamped value can never satisfy hasNext again).
//   * Loop var: initializeDeclarator(Simple) sets the slot to the element, Rt_IntValue.get(res)
//     (an INTEGER value); we pack the i64 as INTEGER. A non-DIRECT declarator adapter soft-fails
//     (same gate as VarStatement) — the JVM applies the widening conversion.
// =====================================================================================

// Simple function name from a buildSysFnKey: the segment after the last '.' of <fullName>, before
// the first '#'. (Mirrors lower_call.cpp's sysFnSimpleName; replicated to keep this file self-
// contained.) Empty when the key has no '#'.
std::string_view forSysFnSimpleName(std::string_view key) {
    const std::size_t hash = key.find('#');
    if (hash == std::string_view::npos) return {};
    std::string_view fullName = key.substr(0, hash);
    const std::size_t dot = fullName.rfind('.');
    return dot == std::string_view::npos ? fullName : fullName.substr(dot + 1);
}

// Result-type strCode of a buildSysFnKey: the substring after "->" up to an optional "@pos" tail.
std::string_view forSysFnResultType(std::string_view key) {
    const std::size_t arrow = key.rfind("->");
    if (arrow == std::string_view::npos) return {};
    std::string_view tail = key.substr(arrow + 2);
    const std::size_t at = tail.find('@');
    if (at != std::string_view::npos) tail = tail.substr(0, at);
    return tail;
}

// If `iterExpr` is a `range(...)` constructor call (SysGlobal target, simple name "range",
// result type "range"), return its FullFunctionCall; else nullptr (the caller soft-fails). The
// `..` operator desugars to this same constructor, so this covers `for (i in a..b)` too.
const ir::FullFunctionCall *asRangeConstructorCall(const ir::Expr *iterExpr) {
    if (iterExpr == nullptr || iterExpr->expr_type() != ir::ExprUnion_FunctionCallExpr) return nullptr;
    const auto *callExpr = iterExpr->expr_as_FunctionCallExpr();
    if (callExpr == nullptr) return nullptr;
    const auto *call = callExpr->call();
    if (call == nullptr) return nullptr;
    const auto *full = call->call_as_FullFunctionCall();
    if (full == nullptr) return nullptr;  // PartialFunctionCall -> not a direct range() ctor.
    const auto *tgt = full->target();
    if (tgt == nullptr || tgt->target_type() != ir::FunctionCallTargetUnion_FnTarget_SysGlobal) {
        return nullptr;
    }
    const auto *sysGlobal = tgt->target_as_FnTarget_SysGlobal();
    if (sysGlobal == nullptr) return nullptr;
    const auto *fnName = sysGlobal->fn_name();
    if (fnName == nullptr) return nullptr;
    const std::string_view key(fnName->c_str());
    return (forSysFnSimpleName(key) == "range" && forSysFnResultType(key) == "range") ? full
                                                                                      : nullptr;
}

// =====================================================================================
// ForStatement over a native LIST (the for-over-collection case DEFERRED in P6).
//
// Rell `for (x in <list>) <body>`. The iterable lowers to a native LIST runtime value (the LIST
// carrier — value.cpp). We iterate elements by index in ITERATION order, EXACTLY as the interpreter
// (rr_interpreter.kt RR_Statement.For -> DIRECT -> `for (element in iterator)` over Rt_ListValue's
// element order):
//   * size = rell_list_size(list)  (Rt_ListValue.elements.size).
//   * i from 0; hasNext = i < size; element = rell_list_get(list, i); i = i + 1 BEFORE the body
//     (so `continue` re-tests WITHOUT re-advancing: continue target == cond, like WhileStatement).
//   * empty list (size 0): hasNext false on entry -> 0 iterations.
//   * the loop var slot is bound to each element RellValue (initializeDeclarator(Simple) sets the
//     slot to `element`); a non-DIRECT declarator adapter soft-fails (the element may need a widening
//     conversion the JVM applies) — same gate as VarStatement / the range loop.
// The element-get uses the SAME rell_list_get the subscript path uses, but indices are ALWAYS in
// bounds here (i < size), so the bounds check never fires — no OOB error is possible from iteration.
// =====================================================================================
bool lowerForOverList(EmitContext &ec, const ir::ForStatement &stmt) {
    const auto *iterExpr = stmt.expr();
    const auto *varDecl = stmt.var_declarator();
    const auto *body = stmt.body();
    // (iterExpr/varDecl/body/frame_block/adapter were already null/DIRECT-checked by lowerForStmt.)

    // Loop var must be a Simple declarator with a backing slot and a DIRECT/absent adapter.
    if (varDecl->declarator_type() != ir::VarDeclaratorUnion_SimpleVarDeclarator) {
        return ec.fail("ForStatement(list): loop var is not a simple declarator (soft-fail)");
    }
    const auto *simple = varDecl->declarator_as_SimpleVarDeclarator();
    if (simple == nullptr) return ec.fail("ForStatement(list): SimpleVarDeclarator null");
    const auto *varPtr = simple->ptr();
    if (varPtr == nullptr) return ec.fail("ForStatement(list): loop var has no slot (soft-fail)");
    const auto *adapter = simple->adapter();
    if (adapter != nullptr && adapter->kind() != ir::TypeAdapterKind_DIRECT) {
        return ec.fail("ForStatement(list): loop var has a non-DIRECT type adapter (soft-fail)");
    }

    FunctionLowering *fl = requireLowering(ec);
    if (fl == nullptr) return false;
    llvm::Function *fn = currentFunction(ec);
    if (fn == nullptr) return ec.fail("ForStatement(list): no enclosing function");
    auto &b = ec.builder();
    auto *i64Ty = b.getInt64Ty();
    auto *valTy = ec.valueType();
    auto *ptrTy = llvm::PointerType::getUnqual(ec.ctx());

    // Evaluate the iterable ONCE (observable: side-effecting/throwing iterable is evaluated before
    // iterating, exactly as the interpreter evaluates `evaluateExpr(stmt.expr)` first).
    llvm::Value *listVal = lowerExpr(ec, *iterExpr);
    if (listVal == nullptr) return false;  // soft-fail propagated.

    // Entry-block scratch: the live index `i`, the list value, and the loop-var runtime-value slot,
    // hoisted so they dominate the loop blocks.
    llvm::Value *idxSlot = nullptr;
    llvm::Value *listSlot = nullptr;
    llvm::Value *varSlot = nullptr;
    {
        llvm::BasicBlock *savedBlock = b.GetInsertBlock();
        llvm::BasicBlock::iterator savedPt = b.GetInsertPoint();
        llvm::BasicBlock &entry = fn->getEntryBlock();
        if (entry.empty()) {
            b.SetInsertPoint(&entry);
        } else {
            b.SetInsertPoint(&entry, entry.getFirstInsertionPt());
        }
        idxSlot = b.CreateAlloca(i64Ty, nullptr, "for.list.i");
        listSlot = b.CreateAlloca(valTy, nullptr, "for.list.val");
        varSlot = b.CreateAlloca(valTy, nullptr, "for.list.var");
        b.SetInsertPoint(savedBlock, savedPt);
    }
    b.CreateStore(listVal, listSlot);
    b.CreateStore(b.getInt64(0), idxSlot);

    // Register the loop-var slot so the body's VarExpr reads resolve to it.
    ec.slots()[{varPtr->block_uid(), varPtr->offset()}] = varSlot;

    // size = rell_list_size(&list).  void rell_list_size(ptr out, ptr list)
    llvm::Value *sizeI64 = nullptr;
    {
        llvm::Function *cf = b.GetInsertBlock()->getParent();
        llvm::IRBuilder<> entryB(&cf->getEntryBlock(), cf->getEntryBlock().begin());
        llvm::Value *sizeOut = entryB.CreateAlloca(valTy, nullptr, "for.list.size_out");
        auto *fnTy = llvm::FunctionType::get(llvm::Type::getVoidTy(ec.ctx()), {ptrTy, ptrTy},
                                             /*isVarArg=*/false);
        llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_list_size", fnTy);
        b.CreateCall(callee, {sizeOut, listSlot});
        llvm::Value *sizeRv = b.CreateLoad(valTy, sizeOut, "for.list.size_rv");
        sizeI64 = ec.unpackPayloadI64(sizeRv);
    }

    auto *condBB = llvm::BasicBlock::Create(ec.ctx(), "for.list.cond", fn);
    auto *bodyBB = llvm::BasicBlock::Create(ec.ctx(), "for.list.body", fn);
    auto *afterBB = llvm::BasicBlock::Create(ec.ctx(), "for.list.after", fn);

    b.CreateBr(condBB);

    // cond: hasNext = i < size.
    b.SetInsertPoint(condBB);
    llvm::Value *i = b.CreateLoad(i64Ty, idxSlot, "for.list.cur");
    llvm::Value *hasNext = b.CreateICmpSLT(i, sizeI64, "for.list.hasnext");
    b.CreateCondBr(hasNext, bodyBB, afterBB);

    // body: element = list[i]; i = i + 1 (advance BEFORE the body, like the range loop / next());
    // bind the loop var; run body.
    b.SetInsertPoint(bodyBB);
    llvm::Value *iBody = b.CreateLoad(i64Ty, idxSlot, "for.list.ib");
    llvm::Value *listForGet = b.CreateLoad(valTy, listSlot, "for.list.lv");
    llvm::Value *element = emitListGet(ec, listForGet, iBody);  // i < size, so never OOB.
    if (element == nullptr) return false;
    // advance i (no overflow: i < size <= Int.MAX list length, +1 stays in range).
    llvm::Value *iNext = b.CreateAdd(iBody, b.getInt64(1), "for.list.inext");
    b.CreateStore(iNext, idxSlot);
    // Bind the loop var to the element runtime value.
    b.CreateStore(element, varSlot);

    {
        // break -> afterBB, continue -> condBB (re-test hasNext; advance already happened above).
        LoopScope loop(*fl, afterBB, condBB);
        if (!lowerStmt(ec, *body)) return false;
    }
    if (!currentBlockTerminated(ec)) b.CreateBr(condBB);

    b.SetInsertPoint(afterBB);
    return true;
}

bool lowerForStmt(EmitContext &ec, const ir::ForStatement &stmt) {
    const auto *iterExpr = stmt.expr();
    const auto *varDecl = stmt.var_declarator();
    const auto *body = stmt.body();
    if (iterExpr == nullptr || varDecl == nullptr || body == nullptr) {
        return ec.fail("ForStatement: null expr/var_declarator/body");
    }
    if (stmt.frame_block() == nullptr) return ec.fail("ForStatement: null frame_block");

    // Only DIRECT iteration is native. LEGACY_MAP (map-entry tuples) needs JVM iterator semantics.
    if (stmt.iterable_adapter() != ir::IterableAdapterKind_DIRECT) {
        return ec.fail("ForStatement: non-DIRECT iterable adapter (soft-fail)");
    }

    // The iterable must be an inline `range(...)` construction (see WHY INLINE CONSTRUCTION above).
    // A range VALUE that is not constructed here (a VarExpr, a function result, ...) cannot be read
    // inline (it is a HANDLE we cannot crack while the sysfn return ABI is unsret'd) -> soft-fail.
    // for-over-LIST is handled natively below (the LIST carrier exposes size/element-get); set/map
    // iteration still soft-fails (they stay HANDLEs).
    const ir::FullFunctionCall *rangeCall = asRangeConstructorCall(iterExpr);
    if (rangeCall == nullptr) {
        const ir::Type *iterType = exprStaticResultType(iterExpr);
        if (iterType != nullptr && iterType->type_type() == ir::TypeUnion_ListType) {
            return lowerForOverList(ec, stmt);
        }
        return ec.fail("ForStatement: iterable is neither an inline range(...) nor a list (soft-fail)");
    }
    const auto *rangeArgs = rangeCall->args();
    if (rangeArgs == nullptr || rangeArgs->size() < 1 || rangeArgs->size() > 3) {
        return ec.fail("ForStatement: range() call has unexpected arity (soft-fail)");
    }

    // The loop variable must be a Simple declarator with a backing slot and a DIRECT/absent adapter
    // (the element is an INTEGER value; a widening adapter would need the JVM conversion). Tuple /
    // wildcard / adapter declarators soft-fail.
    if (varDecl->declarator_type() != ir::VarDeclaratorUnion_SimpleVarDeclarator) {
        return ec.fail("ForStatement: loop var is not a simple declarator (soft-fail)");
    }
    const auto *simple = varDecl->declarator_as_SimpleVarDeclarator();
    if (simple == nullptr) return ec.fail("ForStatement: SimpleVarDeclarator null");
    const auto *varPtr = simple->ptr();
    if (varPtr == nullptr) return ec.fail("ForStatement: loop var has no slot (soft-fail)");
    const auto *adapter = simple->adapter();
    if (adapter != nullptr && adapter->kind() != ir::TypeAdapterKind_DIRECT) {
        return ec.fail("ForStatement: loop var has a non-DIRECT type adapter (soft-fail)");
    }

    FunctionLowering *fl = requireLowering(ec);
    if (fl == nullptr) return false;
    llvm::Function *fn = currentFunction(ec);
    if (fn == nullptr) return ec.fail("ForStatement: no enclosing function");
    auto &b = ec.builder();
    auto *i64Ty = b.getInt64Ty();

    // Lower the range arguments inline, IN SOURCE ORDER (evaluation order is observable — a
    // side-effecting/throwing arg must be evaluated before the loop, exactly as the interpreter
    // evaluates `evaluateExpr(stmt.expr)` — the range() call — before iterating). Each arg is
    // integer-typed, so INTEGER-tagged at runtime; unpack the i64 payload.
    llvm::Value *argI64[3] = {nullptr, nullptr, nullptr};
    for (flatbuffers::uoffset_t i = 0; i < rangeArgs->size(); ++i) {
        const ir::Expr *argExpr = rangeArgs->Get(i);
        if (argExpr == nullptr) return ec.fail("ForStatement: null range() argument");
        llvm::Value *v = lowerExpr(ec, *argExpr);
        if (v == nullptr) return false;  // soft-fail propagated.
        argI64[i] = ec.unpackPayloadI64(v);
    }

    // Map args to (start, end, step) per the two range constructors (lib_type_range.kt):
    //   range(end)            -> start=0, step=1
    //   range(start,end)      -> step=1
    //   range(start,end,step) -> as given
    llvm::Value *start;
    llvm::Value *end0;
    llvm::Value *step0;
    if (rangeArgs->size() == 1) {
        start = b.getInt64(0);
        end0 = argI64[0];
        step0 = b.getInt64(1);
    } else {
        start = argI64[0];
        end0 = argI64[1];
        step0 = (rangeArgs->size() == 3) ? argI64[2] : b.getInt64(1);
    }

    // Entry-block scratch: the live `current` and the loop-var runtime-value slot, hoisted so they
    // dominate the loop blocks.
    llvm::Value *currentSlot = nullptr;
    llvm::Value *varSlot = nullptr;
    {
        llvm::BasicBlock *savedBlock = b.GetInsertBlock();
        llvm::BasicBlock::iterator savedPt = b.GetInsertPoint();
        llvm::BasicBlock &entry = fn->getEntryBlock();
        if (entry.empty()) {
            b.SetInsertPoint(&entry);
        } else {
            b.SetInsertPoint(&entry, entry.getFirstInsertionPt());
        }
        currentSlot = b.CreateAlloca(i64Ty, nullptr, "for.current");
        varSlot = b.CreateAlloca(ec.valueType(), nullptr, "for.var");
        b.SetInsertPoint(savedBlock, savedPt);
    }

    // Register the loop-var slot so the body's VarExpr reads (and any assignments) resolve to it.
    ec.slots()[{varPtr->block_uid(), varPtr->offset()}] = varSlot;

    auto *escBB = llvm::BasicBlock::Create(ec.ctx(), "for.invalid", fn);
    auto *preBB = llvm::BasicBlock::Create(ec.ctx(), "for.pre", fn);    // valid-edge preheader.
    auto *condBB = llvm::BasicBlock::Create(ec.ctx(), "for.cond", fn);
    auto *bodyBB = llvm::BasicBlock::Create(ec.ctx(), "for.body", fn);
    auto *afterBB = llvm::BasicBlock::Create(ec.ctx(), "for.after", fn);

    // VALIDATION (calcRange): invalid iff step==0 || (step>0 && start>end) || (step<0 && start<end).
    // On the invalid edge, branch to escBB which marks the call for re-run on the interpreter (the
    // exact fn_range_args Rt_Exception is raised there) and SKIPS the native loop entirely — never
    // iterate with invalid bounds (step==0 would spin forever before the JVM discards the result).
    {
        llvm::Value *stepZero = b.CreateICmpEQ(step0, b.getInt64(0), "for.stepzero");
        llvm::Value *stepPosV = b.CreateICmpSGT(step0, b.getInt64(0), "for.steppos.v");
        llvm::Value *stepNegV = b.CreateICmpSLT(step0, b.getInt64(0), "for.stepneg.v");
        llvm::Value *startGtEnd = b.CreateICmpSGT(start, end0, "for.sgte");
        llvm::Value *startLtEnd = b.CreateICmpSLT(start, end0, "for.slte");
        llvm::Value *badPos = b.CreateAnd(stepPosV, startGtEnd, "for.badpos");
        llvm::Value *badNeg = b.CreateAnd(stepNegV, startLtEnd, "for.badneg");
        llvm::Value *invalid =
            b.CreateOr(stepZero, b.CreateOr(badPos, badNeg, "for.bad"), "for.invalid.cond");
        b.CreateCondBr(invalid, escBB, preBB);
    }

    // invalid edge: escape (JVM re-runs and raises fn_range_args), then leave the loop unentered.
    b.SetInsertPoint(escBB);
    emitJitEscape(ec);
    b.CreateBr(afterBB);

    // valid-edge preheader: initialise current = start ONCE, then enter the loop test.
    b.SetInsertPoint(preBB);
    b.CreateStore(start, currentSlot);
    b.CreateBr(condBB);

    // cond: hasNext = step > 0 ? current < end : current > end. (step != 0 guaranteed by calcRange.)
    b.SetInsertPoint(condBB);
    llvm::Value *cur = b.CreateLoad(i64Ty, currentSlot, "cur");
    llvm::Value *stepPos = b.CreateICmpSGT(step0, b.getInt64(0), "for.steppos");
    llvm::Value *ltEnd = b.CreateICmpSLT(cur, end0, "for.lt");
    llvm::Value *gtEnd = b.CreateICmpSGT(cur, end0, "for.gt");
    llvm::Value *hasNext = b.CreateSelect(stepPos, ltEnd, gtEnd, "for.hasnext");
    b.CreateCondBr(hasNext, bodyBB, afterBB);

    // body: element = current; current = saturatedAdd(current, step); bind loop var; run body.
    b.SetInsertPoint(bodyBB);
    llvm::Value *element = b.CreateLoad(i64Ty, currentSlot, "for.elem");
    // saturatedAdd(current, step): wrapping add, then clamp on signed overflow.
    llvm::Value *sum = b.CreateAdd(element, step0, "for.sum");
    // overflow iff ((element ^ sum) & (step ^ sum)) < 0  (utils_number.kt saturatedAdd).
    llvm::Value *x1 = b.CreateXor(element, sum, "for.x1");
    llvm::Value *x2 = b.CreateXor(step0, sum, "for.x2");
    llvm::Value *ovfBits = b.CreateAnd(x1, x2, "for.ovfbits");
    llvm::Value *ovf = b.CreateICmpSLT(ovfBits, b.getInt64(0), "for.ovf");
    // clamp = (element >= 0) ? Long.MAX : Long.MIN.
    llvm::Value *elemNonNeg = b.CreateICmpSGE(element, b.getInt64(0), "for.elemnn");
    llvm::Value *clamp = b.CreateSelect(
        elemNonNeg, b.getInt64(INT64_MAX), b.getInt64(INT64_MIN), "for.clamp");
    llvm::Value *advanced = b.CreateSelect(ovf, clamp, sum, "for.advanced");
    b.CreateStore(advanced, currentSlot);
    // Bind the loop var to the element as an INTEGER runtime value (Rt_IntValue.get(res)).
    b.CreateStore(ec.packInteger(element), varSlot);

    {
        // break -> afterBB, continue -> condBB (re-test hasNext; advance already happened above).
        LoopScope loop(*fl, afterBB, condBB);
        if (!lowerStmt(ec, *body)) return false;
    }
    if (!currentBlockTerminated(ec)) b.CreateBr(condBB);

    b.SetInsertPoint(afterBB);
    return true;
}

// =====================================================================================
// Update / Delete — SQL writes routed to the JVM interpreter via sql_bridge.
//
// DETERMINISM / SQL: an UPDATE/DELETE generates PostgreSQL through DbSqlGen and runs against the
// live JDBC connection inside the interpreter (see sql_bridge.cpp). None of that is reproducible
// as native IR. We intern the node (DbNodeTable, walk-order mirrored JVM-side) and emit a single
// rell_db_exec_stmt back-call: status >= 0 continues, status < 0 means a pending Rt_Exception and
// the trampoline aborts the native frame (ABI §6).
//
// IMPORTANT — current envelope: emitting the IR call requires the RellCallCtx* SSA (the hidden
// trailing param threaded into every JIT'd function) and a declared `rell_db_exec_stmt` extern
// in the module. That wiring (the function signature carrying RellCallCtx*, and the extern
// declaration) is owned by the driver in lower_expr.cpp / the function builder, which is not yet
// in place. Until the ctx param is available to this file, we conservatively SOFT-FAIL db-write
// statements: interning here without an emit site would desynchronise the DbNodeTable id space
// from the JVM mirror, which is worse than a clean soft-fail. When the ctx wiring lands, replace
// this with: id = ec.dbNodes().intern(); emit call rell_db_exec_stmt(env, id, ctx); branch on
// (status < 0) to the abort path. Soft-failing now is bit-safe (JVM runs the whole function).
// =====================================================================================
bool lowerDbWrite(EmitContext &ec, const ir::Stmt &stmt) {
    const char *kind = stmt.stmt_type() == ir::StmtUnion_UpdateStatement ? "Update" : "Delete";
    // DETERMINISM/SQL: see above — soft-fail the whole function until the RellCallCtx* is threaded
    // into this file so the rell_db_exec_stmt call can be emitted with a synchronised db-node id.
    return ec.fail(std::string(kind) + "Statement: SQL write needs ctx-threaded back-call (soft-fail)");
}

}  // namespace

// =====================================================================================
// lowerStmt — the StmtUnion dispatcher (header-declared entry).
//
// Returns true on success; false after ec.fail() makes the whole function soft-fail. Mirrors the
// jni_bridge lowerExpr switch shape: one case per supported variant, an explicit soft-fail
// default. Variants intentionally NOT lowered inline are routed (Update/Delete -> sql_bridge) or
// soft-failed with a // DETERMINISM: rationale at the handler.
// =====================================================================================
bool lowerStmt(EmitContext &ec, const ir::Stmt &stmt) {
    if (ec.failed()) return false;  // never emit IR after a prior soft-fail.

    switch (stmt.stmt_type()) {
        case ir::StmtUnion_EmptyStatement:
            return lowerEmpty(ec, *stmt.stmt_as_EmptyStatement());
        case ir::StmtUnion_ExprStatement:
            return lowerExprStmt(ec, *stmt.stmt_as_ExprStatement());
        case ir::StmtUnion_ReturnStatement:
            return lowerReturn(ec, *stmt.stmt_as_ReturnStatement());
        case ir::StmtUnion_BlockStatement:
            return lowerBlock(ec, *stmt.stmt_as_BlockStatement());
        case ir::StmtUnion_VarStatement:
            return lowerVar(ec, *stmt.stmt_as_VarStatement());
        case ir::StmtUnion_AssignStatement:
            return lowerAssign(ec, *stmt.stmt_as_AssignStatement());
        case ir::StmtUnion_IfStatement:
            return lowerIf(ec, *stmt.stmt_as_IfStatement());
        case ir::StmtUnion_WhileStatement:
            return lowerWhile(ec, *stmt.stmt_as_WhileStatement());
        case ir::StmtUnion_BreakStatement:
            return lowerBreak(ec, *stmt.stmt_as_BreakStatement());
        case ir::StmtUnion_ContinueStatement:
            return lowerContinue(ec, *stmt.stmt_as_ContinueStatement());
        case ir::StmtUnion_GuardStatement:
            return lowerGuard(ec, *stmt.stmt_as_GuardStatement());

        case ir::StmtUnion_UpdateStatement:
        case ir::StmtUnion_DeleteStatement:
            return lowerDbWrite(ec, stmt);

        case ir::StmtUnion_WhenStatement:
            // Lowered with the SAME chooser semantics + inline-key restriction as WhenExpr (reusing
            // lower_expr.cpp's whenKeyTypeInlineComparable/emitWhenInlineEq). Non-inline key (text/
            // decimal/...), or a chooser shape we can't reduce to inline equality, soft-fails inside.
            return lowerWhenStmt(ec, *stmt.stmt_as_WhenStatement());

        case ir::StmtUnion_ForStatement:
            // Native ONLY for DIRECT iteration over an integer RANGE (bit-exact with Rt_RangeValue's
            // RangeIterator); for-over-collection / LEGACY_MAP / non-simple loop var soft-fails inside.
            return lowerForStmt(ec, *stmt.stmt_as_ForStatement());

        case ir::StmtUnion_LambdaStatement:
            // DETERMINISM: a lambda-binding statement captures args into a frame block and runs a
            // body under that binding (used by at-expr / collection callbacks). Native lowering of
            // the capture+dispatch is not reproduced here; soft-fail. Whole function -> JVM.
            return ec.fail("LambdaStatement: not implemented (soft-fail)");

        case ir::StmtUnion_ReplExprStatement:
            // REPL-only; never appears in a compiled consensus function. Soft-fail defensively.
            return ec.fail("ReplExprStatement: not JITed (soft-fail)");

        default:
            return ec.fail("lowerStmt: unsupported StmtUnion variant " +
                           std::to_string(static_cast<int>(stmt.stmt_type())));
    }
}

// =====================================================================================
// lowerFunctionBody — driver helper establishing the per-function lowering scope and finalising
// the shared return path.
//
// The function builder (lower_expr.cpp / the driver) calls this with the builder positioned at
// the entry block. It:
//   1. establishes the FunctionLoweringScope (loop stack + lazy return path),
//   2. lowers the body Stmt,
//   3. if any ReturnStatement was lowered, finalises the shared return block — the driver is
//      responsible for attaching the actual `ret` that reads the return slot, but we expose the
//      slot/block via the controller for that step.
//
// Returns true on success; false (after ec.fail()) means the whole function soft-fails. This is
// declared for the driver in rell_runtime.h's lowering section ALONGSIDE lowerStmt once the
// driver lands; until then it is an internal convenience that the driver may adopt. It is given
// external linkage (no anonymous namespace) so the driver can call it.
//
// NOTE: this helper does NOT itself emit the trailing `ret`, because the function's return ABI
// (does it return a runtime value by value? via an sret pointer? an i64 like the prototype?) is
// the driver's decision. The controller hands back returnSlot()/returnBlock() so the driver can
// emit the convention-correct epilogue. If the body never returns explicitly (e.g. a unit op
// that falls off the end), returnBlock() is null and the driver supplies the fallthrough ret.
// =====================================================================================
bool lowerFunctionBody(EmitContext &ec, const ir::Stmt &body, llvm::Value **returnSlotOut,
                       llvm::BasicBlock **returnBlockOut) {
    FunctionLoweringScope scope(ec);
    if (!lowerStmt(ec, body)) {
        if (returnSlotOut != nullptr) *returnSlotOut = nullptr;
        if (returnBlockOut != nullptr) *returnBlockOut = nullptr;
        return false;
    }
    if (returnSlotOut != nullptr) *returnSlotOut = scope.lowering().returnSlot();
    if (returnBlockOut != nullptr) *returnBlockOut = scope.lowering().returnBlock();
    return true;
}

}  // namespace rell::llvm_rt
