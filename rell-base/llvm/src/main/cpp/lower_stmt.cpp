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
//   - A declarator carrying a non-trivial TypeAdapter (the `adapter()` field) implies an
//     implicit conversion (e.g. integer->decimal) on the stored value. We only inline DIRECT
//     (absent) adapters here; any present adapter soft-fails so the JVM applies the exact
//     conversion. (lowerExpr may itself surface the adapter via TypeAdapterExpr; if so it is
//     handled there and the declarator adapter is absent.)
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

            // A non-DIRECT type adapter means an implicit conversion on assignment. We do not
            // reproduce conversions in the declarator; route to the JVM. DETERMINISM: the exact
            // numeric/nullable adaptation (integer->decimal, etc.) must match the interpreter.
            if (simple->adapter() != nullptr) {
                return ec.fail("VarStatement: declarator has a type adapter (conversion)");
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

            // Store the initializer (if any) at the declaration point.
            if (initExpr != nullptr) {
                llvm::Value *init = lowerExpr(ec, *initExpr);
                if (init == nullptr) return false;
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
//   - Compound assignment (`+=`, `-=`, ... — op() present): the read-modify-write must reproduce
//     the operator's exact overflow/Rt_Exception semantics AND re-store; rather than re-run the
//     binary-op overlay through an lvalue load here, we route the whole function to the JVM.
//     DETERMINISM: avoids duplicating the checked-arithmetic envelope at an assignment site.
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

    // op() is a ::flatbuffers::Optional<BinaryOp>: PRESENT ⇒ compound assignment (`+=`, `-=`, ...);
    // ABSENT ⇒ plain `=`. DETERMINISM: compound assignment is a read-modify-write that must
    // reproduce the operator's exact overflow/Rt_Exception semantics; we do not duplicate the
    // checked-arithmetic envelope at an assignment site, so any present op soft-fails (whole
    // function -> JVM). Only the absent case (plain `=`) is lowered inline.
    if (stmt.op().has_value()) {
        return ec.fail("AssignStatement: compound assignment (soft-fail)");
    }

    // Plain `=`. The only inline-lowerable destination is a local/param slot (a VarExpr whose
    // ptr is in ec.slots()). Anything else (subscript/member/attribute) is non-local -> soft-fail.
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
            // DETERMINISM: WhenStatement dispatch (IterativeWhenChooser / LookupWhenChooser) is a
            // value-keyed multi-way branch whose chooser lowering (key compare, lookup-table
            // semantics, else handling) is not reproduced here; a wrong branch is a consensus
            // divergence. Route the whole function to the JVM. (A later pass may lower the
            // iterative chooser as a chain of CmpInfo branches once the chooser overlay exists.)
            return ec.fail("WhenStatement: chooser lowering not implemented (soft-fail)");

        case ir::StmtUnion_ForStatement:
            // DETERMINISM: For iterates an arbitrary iterable via IterableAdapterKind (DIRECT /
            // LEGACY_MAP) — collections, ranges, maps — whose iteration order and per-element
            // adaptation are JVM iterator semantics. Reproducing the iterator inline (and the
            // per-element HANDLE marshalling) risks order/semantic divergence; soft-fail until a
            // JNI-iterator-backed lowering exists. Whole function -> JVM.
            return ec.fail("ForStatement: iterable lowering not implemented (soft-fail)");

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
