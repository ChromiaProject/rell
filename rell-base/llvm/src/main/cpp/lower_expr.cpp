// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// lower_expr.cpp — the value-producing Expr-union dispatcher for the Rell LLVM backend.
//
// This file owns the core of `lowerExpr` (declared in rell_runtime.h §9): the variants that
// produce a runtime RellValue SSA (an llvm::Value* of valueType() == { i8, i32, i64 }) WITHOUT
// touching SQL or the stdlib caller — those route out to lower_call.cpp / the SQL back-call.
//
// COVERED INLINE here:
//   - VarExpr             param AND local frame slots (alloca/SSA slot map, generalising the
//                         prototype's param-only i64 GEP).
//   - ConstantValueExpr   every ValueUnion variant that has an inline RellValue rep
//                         (Bool/Int/Rowid/Enum/Null/Unit + long-fitting Decimal/BigInteger).
//   - IfExpr              ternary: cond -> then/else, phi of the runtime value.
//   - ElvisExpr           `a ?: b`: NULL_-tagged left -> right, else left.
//   - NotNullExpr         `a!!`: pass-through of the runtime value (the not-null assertion is a
//                         no-op on a value already proven non-null by the type system; a genuine
//                         null would be a Rt_Exception the JVM raises — see DETERMINISM note).
//   - WhenExpr            both choosers — IterativeWhenChooser (sequential cond match) and
//                         LookupWhenChooser (constant-key dispatch lowered as a chain of eq tests).
//   - StatementExpr       inline a statement block then read its produced value (route to
//                         lower_stmt.cpp; soft-fail unless it yields exactly one value).
//
// EVERYTHING ELSE soft-fails (ec.failExpr) or routes out:
//   - BinaryExpr/UnaryExpr        -> lowerBinary/lowerUnary (lower_ops.cpp).
//   - FunctionCallExpr/MemberExpr -> lowerCall/lowerMember (lower_call.cpp).
//   - DbAtExpr/ColAtExpr          -> SQL back-call (lower_call.cpp / interpreter).
//   - every other Expr variant    -> ec.failExpr (whole function falls back to the interpreter).
//
// CORRECTNESS RULE (consensus-critical, from rell_runtime.h): never emit approximate IR. Any
// value/variant we cannot reproduce bit-exactly either becomes a HANDLE that routes through the
// JVM, or makes the WHOLE function soft-fail. Determinism is non-negotiable.

#include "rell_runtime.h"

#include <string>

#include <llvm/IR/BasicBlock.h>
#include <llvm/IR/Constants.h>

namespace rell::llvm_rt {

namespace {

// -------------------------------------------------------------------------------------
// EmitContext shape helpers are declared in rell_runtime.h (packInline / unpackTag / ...).
// rellValueLlvmType() and those pack/unpack members are DEFINED in value.cpp (the ABI phase);
// here we only consume them. We do need a couple of local IR idioms that stay private to the
// expression core.
// -------------------------------------------------------------------------------------

// Allocate a runtime-value slot in the function entry block (so it dominates every use, the
// canonical LLVM mem2reg-friendly idiom). Used to phi runtime values across If/Elvis/When arms
// without hand-rolling N-way phi nodes for the nested-block cases.
llvm::AllocaInst *entryAlloca(EmitContext &ec, const char *name) {
    llvm::IRBuilder<> &b = ec.builder();
    llvm::Function *fn = b.GetInsertBlock()->getParent();
    llvm::IRBuilder<> entryBuilder(&fn->getEntryBlock(), fn->getEntryBlock().begin());
    return entryBuilder.CreateAlloca(ec.valueType(), nullptr, name);
}

// Best-effort static result type of an Expr, for the variants that carry a `type()`. Returns
// nullptr when the variant has no readable result type (the caller then treats it as "unknown"
// and soft-fails any decision that needed it). Used to gate `when` key equality on a canonical
// inline carrier.
const ir::Type *exprResultType(const ir::Expr *expr) {
    if (expr == nullptr) return nullptr;
    switch (expr->expr_type()) {
        case ir::ExprUnion_VarExpr: return expr->expr_as_VarExpr()->type();
        case ir::ExprUnion_BinaryExpr: return expr->expr_as_BinaryExpr()->type();
        case ir::ExprUnion_UnaryExpr: return expr->expr_as_UnaryExpr()->type();
        case ir::ExprUnion_IfExpr: return expr->expr_as_IfExpr()->type();
        case ir::ExprUnion_WhenExpr: return expr->expr_as_WhenExpr()->type();
        case ir::ExprUnion_ElvisExpr: return expr->expr_as_ElvisExpr()->type();
        case ir::ExprUnion_NotNullExpr: return expr->expr_as_NotNullExpr()->type();
        case ir::ExprUnion_ConstantValueExpr: {
            const auto *tv = expr->expr_as_ConstantValueExpr()->typed_value();
            return tv != nullptr ? tv->type() : nullptr;
        }
        case ir::ExprUnion_FunctionCallExpr: {
            const auto *call = expr->expr_as_FunctionCallExpr()->call();
            if (call == nullptr) return nullptr;
            if (const auto *full = call->call_as_FullFunctionCall()) return full->return_type();
            if (const auto *part = call->call_as_PartialFunctionCall()) return part->return_type();
            return nullptr;
        }
        default:
            return nullptr;
    }
}

// -------------------------------------------------------------------------------------
// Inline constant lowering.
//
// Produces a runtime-value SSA ({i8,i32,i64}) directly from a serialized ValueUnion, but ONLY
// for the variants with a pure inline RellValue representation. Anything heap/opaque
// (Text/ByteArray/Gtv/Struct/Collection/Map/Tuple/Meta, and out-of-envelope Decimal/BigInteger)
// would require a JVM rebox at run time; this ABI exposes no "rebox-constant-by-bytes" runtime
// entry, so those make the whole function soft-fail rather than emit an incorrect inline.
//
// `valueType` is the TypedValue.type() (nullable for ConstantValue, where type is contextual);
// it is only consulted to disambiguate the Enum ordinal's carrier, never to widen the envelope.
// -------------------------------------------------------------------------------------

// Parse a decimal/bigint string into a long-fitting inline value, or fail the inline path.
//
// DETERMINISM: the C++ side NEVER reimplements Lib_DecimalMath.scale() / BigInteger bounds. We
// only accept the narrow, unambiguous long-fitting forms and route everything else to soft-fail
// so the JVM constructs the exact Rt_DecimalValue/Rt_BigIntegerValue. A bigint string is inline
// iff it is a base-10 integer literal whose value fits int64 (parsed with full overflow
// checking). A decimal string is NOT parsed here at all — its stripped (mantissa, scale)
// normalisation must match Tf_LongScaleDecimal bit-for-bit, which only the JVM guarantees;
// rather than risk a mismatch we soft-fail every decimal CONSTANT. (Decimal *intrinsics* still
// run inline downstream, but only on (mantissa,scale) the JVM validated and handed across.)

// Try to parse a base-10 signed integer literal that fits int64. Returns true and sets *out on
// success; returns false (no diagnostic) when the string is malformed or out of int64 range —
// the caller then soft-fails. Accepts an optional leading '+'/'-'; rejects empty, whitespace,
// and any non-digit body. Does not accept leading zeros beyond a single "0" being irrelevant to
// value (BigInteger string repr is already canonical, so this is belt-and-suspenders).
bool parseI64Strict(const flatbuffers::String *s, int64_t *out) {
    if (s == nullptr || s->size() == 0) return false;
    const char *p = s->c_str();
    const char *end = p + s->size();
    bool neg = false;
    if (*p == '+' || *p == '-') {
        neg = (*p == '-');
        ++p;
        if (p == end) return false;
    }
    // Accumulate in unsigned space with explicit overflow bounds against int64 limits.
    uint64_t acc = 0;
    const uint64_t limit = neg ? (uint64_t(1) << 63)               // |INT64_MIN|
                               : uint64_t(0x7fffffffffffffffULL);  // INT64_MAX
    for (; p != end; ++p) {
        char c = *p;
        if (c < '0' || c > '9') return false;
        uint64_t digit = uint64_t(c - '0');
        if (acc > (limit - digit) / 10) return false;  // would overflow the signed bound
        acc = acc * 10 + digit;
    }
    *out = neg ? -static_cast<int64_t>(acc) : static_cast<int64_t>(acc);
    return true;
}

// `value` is the union wrapper carrying both the discriminant (value_type()) and the typed
// accessors (value_as_<V>()). Works for BOTH ir::TypedValue and ir::ConstantValue since flatc
// generates the same accessor names on each; templated on the wrapper type.
template <typename ValueWrapper>
llvm::Value *lowerInlineValue(EmitContext &ec, [[maybe_unused]] const ir::Type *valueType,
                              const ValueWrapper &value) {
    llvm::IRBuilder<> &b = ec.builder();

    switch (value.value_type()) {
        case ir::ValueUnion_BoolValue: {
            const auto *v = value.value_as_BoolValue();
            return ec.packInline(RellTag::BOOLEAN, b.getInt32(0),
                                 b.getInt64(v->value() ? 1 : 0));
        }
        case ir::ValueUnion_IntValue: {
            const auto *v = value.value_as_IntValue();
            return ec.packInteger(b.getInt64(v->value()));
        }
        case ir::ValueUnion_RowidValue: {
            const auto *v = value.value_as_RowidValue();
            // DETERMINISM: Rt_RowidValue's ctor asserts value >= 0. A serialized constant rowid
            // is always >= 0 (the frontend validated it), but defend anyway: a negative literal
            // would have to raise the JVM's exact Rt_Exception, which we cannot reproduce inline.
            if (v->value() < 0) return ec.failExpr("negative rowid constant");
            return ec.packInline(RellTag::ROWID, b.getInt32(0), b.getInt64(v->value()));
        }
        case ir::ValueUnion_EnumValue: {
            // Enum values are carried by ordinal (attr_index) as an INTEGER-tagged i64 in the
            // inline lattice; the runtime reconstitutes the Rt_EnumValue from (def_index, ordinal)
            // on demarshal. DETERMINISM: the ordinal is the canonical identity of an enum value
            // (Rt_EnumValue compares by ordinal), so an i64 ordinal is bit-exact.
            const auto *v = value.value_as_EnumValue();
            // attr_index is uint; ordinals are small, so the widening is exact.
            return ec.packInteger(b.getInt64(static_cast<int64_t>(v->attr_index())));
        }
        case ir::ValueUnion_NullValue:
            return ec.packInline(RellTag::NULL_, b.getInt32(0), b.getInt64(0));
        case ir::ValueUnion_UnitValue:
            return ec.packInline(RellTag::UNIT, b.getInt32(0), b.getInt64(0));
        case ir::ValueUnion_BigIntegerValue: {
            const auto *v = value.value_as_BigIntegerValue();
            int64_t n = 0;
            if (!parseI64Strict(v->value(), &n)) {
                // Out of the long-fitting envelope (or unparsable): the JVM must build the exact
                // Rt_BigIntegerValue. No constant-rebox runtime entry exists -> soft-fail.
                return ec.failExpr("big_integer constant outside long envelope");
            }
            return ec.packInline(RellTag::BIGINT_LONG, b.getInt32(0), b.getInt64(n));
        }
        case ir::ValueUnion_DecimalValue:
            // DETERMINISM: a decimal constant's stripped (mantissa, scale) must equal
            // Tf_LongScaleDecimal's exactly; we refuse to derive it in C++ and soft-fail so the
            // JVM produces the bit-exact Rt_DecimalValue.
            return ec.failExpr("decimal constant — JVM materialises (no inline rebox)");
        case ir::ValueUnion_TextValue:
        case ir::ValueUnion_ByteArrayValue:
        case ir::ValueUnion_GtvValue:
        case ir::ValueUnion_StructConstantValue:
        case ir::ValueUnion_CollectionConstantValue:
        case ir::ValueUnion_MapConstantValue:
        case ir::ValueUnion_TupleConstantValue:
        case ir::ValueUnion_MetaConstantValue:
            // Heap/opaque constants need a JVM rebox to a HANDLE; this ABI has no by-bytes
            // constant-rebox runtime entry, so the whole function soft-fails.
            return ec.failExpr("non-inline constant value (heap/opaque) — soft-fail");
        default:
            return ec.failExpr("unsupported ValueUnion constant variant");
    }
}

// -------------------------------------------------------------------------------------
// VarExpr — read a parameter or local frame slot.
//
// Generalises the prototype (param-only i64 GEP). The slot map (ec.slots()) is keyed on
// {block_uid, offset}; the Wiring/lower_stmt phase populates it with an alloca of valueType()
// for every declared local AND every parameter. A VarExpr whose ptr is absent from the map
// references a slot that was never lowered -> soft-fail (we must not invent storage).
// -------------------------------------------------------------------------------------

llvm::Value *lowerVar(EmitContext &ec, const ir::VarExpr &var) {
    const auto *ptr = var.ptr();
    if (ptr == nullptr) return ec.failExpr("VarExpr.ptr is null");

    llvm::Value *slot = ec.slotFor(*ptr);
    if (slot == nullptr) {
        // No lowered storage for this slot: it was declared by a construct we soft-failed on,
        // or it is a capture/outer-frame ref we don't model. Soft-fail the whole function.
        return ec.failExpr("VarExpr references an un-lowered frame slot");
    }
    // The slot holds a runtime value ({i8,i32,i64}); a plain load is the read.
    return ec.builder().CreateLoad(ec.valueType(), slot, "var");
}

// -------------------------------------------------------------------------------------
// IfExpr — ternary expression.
//
// cond is a BOOLEAN runtime value; branch on its i64 payload (0/1). Both arms produce a runtime
// value; we store each into a shared entry-block alloca and load it at the merge (mem2reg lifts
// it to a phi). Using an alloca rather than a hand-built phi keeps nested control flow in the
// arms (which may add their own blocks) correct without tracking the arm's terminating block.
// -------------------------------------------------------------------------------------

llvm::Value *lowerIf(EmitContext &ec, const ir::IfExpr &ife) {
    const ir::Expr *cond = ife.cond();
    const ir::Expr *thenE = ife.true_expr();
    const ir::Expr *elseE = ife.false_expr();
    if (cond == nullptr || thenE == nullptr || elseE == nullptr) {
        return ec.failExpr("IfExpr has a null sub-expression");
    }

    llvm::IRBuilder<> &b = ec.builder();
    llvm::Function *fn = b.GetInsertBlock()->getParent();
    llvm::AllocaInst *result = entryAlloca(ec, "if_result");

    llvm::Value *condVal = lowerExpr(ec, *cond);
    if (condVal == nullptr) return nullptr;
    // Branch on the boolean payload (i64 in {0,1}); compare != 0 to get an i1.
    llvm::Value *condI64 = ec.unpackPayloadI64(condVal);
    llvm::Value *condBit = b.CreateICmpNE(condI64, b.getInt64(0), "if_cond");

    auto *thenBB = llvm::BasicBlock::Create(ec.ctx(), "if_then", fn);
    auto *elseBB = llvm::BasicBlock::Create(ec.ctx(), "if_else", fn);
    auto *mergeBB = llvm::BasicBlock::Create(ec.ctx(), "if_merge", fn);
    b.CreateCondBr(condBit, thenBB, elseBB);

    b.SetInsertPoint(thenBB);
    llvm::Value *thenVal = lowerExpr(ec, *thenE);
    if (thenVal == nullptr) return nullptr;
    b.CreateStore(thenVal, result);
    b.CreateBr(mergeBB);

    b.SetInsertPoint(elseBB);
    llvm::Value *elseVal = lowerExpr(ec, *elseE);
    if (elseVal == nullptr) return nullptr;
    b.CreateStore(elseVal, result);
    b.CreateBr(mergeBB);

    b.SetInsertPoint(mergeBB);
    return b.CreateLoad(ec.valueType(), result, "if_value");
}

// -------------------------------------------------------------------------------------
// ElvisExpr — `left ?: right`.
//
// Evaluate left; if its tag is NULL_, the value is `right`, else `left`. left has a nullable
// type, right does not. DETERMINISM: the discriminant is the value tag (NULL_ == Rell null),
// exactly the JVM's `value == Rt_NullValue` check. Right is only evaluated on the null branch
// (matching the interpreter's short-circuit), so any side effect / exception in `right` is gated
// the same way.
// -------------------------------------------------------------------------------------

llvm::Value *lowerElvis(EmitContext &ec, const ir::ElvisExpr &elvis) {
    const ir::Expr *leftE = elvis.left();
    const ir::Expr *rightE = elvis.right();
    if (leftE == nullptr || rightE == nullptr) {
        return ec.failExpr("ElvisExpr has a null operand");
    }

    llvm::IRBuilder<> &b = ec.builder();
    llvm::Function *fn = b.GetInsertBlock()->getParent();
    llvm::AllocaInst *result = entryAlloca(ec, "elvis_result");

    llvm::Value *leftVal = lowerExpr(ec, *leftE);
    if (leftVal == nullptr) return nullptr;
    b.CreateStore(leftVal, result);  // default: keep left.

    // tag == NULL_ ?  (tag is i8 field 0)
    llvm::Value *tag = ec.unpackTag(leftVal);
    llvm::Value *isNull = b.CreateICmpEQ(
        tag, b.getInt8(static_cast<uint8_t>(RellTag::NULL_)), "elvis_isnull");

    auto *rightBB = llvm::BasicBlock::Create(ec.ctx(), "elvis_right", fn);
    auto *mergeBB = llvm::BasicBlock::Create(ec.ctx(), "elvis_merge", fn);
    b.CreateCondBr(isNull, rightBB, mergeBB);

    b.SetInsertPoint(rightBB);
    llvm::Value *rightVal = lowerExpr(ec, *rightE);
    if (rightVal == nullptr) return nullptr;
    b.CreateStore(rightVal, result);
    b.CreateBr(mergeBB);

    b.SetInsertPoint(mergeBB);
    return b.CreateLoad(ec.valueType(), result, "elvis_value");
}

// -------------------------------------------------------------------------------------
// NotNullExpr — `expr!!`.
//
// Asserts the operand is non-null, narrowing the static type. The frontend only emits this where
// the value is dynamically known non-null OR where a null must raise. DETERMINISM: a genuine
// null at run time must raise the exact JVM Rt_Exception (NullPointerException-style "null value"
// error) at the documented err_pos; we cannot synthesise that bit-exact throw inline without a
// runtime back-call, so when the operand's tag could be NULL_ at run time we MUST route the throw
// through the JVM. The conservative, always-correct choice for the expression core is to
// soft-fail the whole function whenever the operand is nullable-typed, letting the interpreter
// run it. When the operand type is statically non-nullable (the common adapter-inserted case),
// the assertion is a no-op and we pass the value through.
// -------------------------------------------------------------------------------------

llvm::Value *lowerNotNull(EmitContext &ec, const ir::NotNullExpr &nn) {
    const ir::Expr *inner = nn.expr();
    if (inner == nullptr) return ec.failExpr("NotNullExpr has a null operand");

    // If the inner expression's static type is itself nullable, a runtime null is possible and
    // would have to raise the JVM's exact error — soft-fail. We can only inspect the inner
    // expression's declared result type for variants that carry a type(); for the rest, be
    // conservative and soft-fail too (NotNullExpr is rare on the hot inline path).
    const ir::Type *innerType = nullptr;
    switch (inner->expr_type()) {
        case ir::ExprUnion_VarExpr: innerType = inner->expr_as_VarExpr()->type(); break;
        case ir::ExprUnion_BinaryExpr: innerType = inner->expr_as_BinaryExpr()->type(); break;
        case ir::ExprUnion_UnaryExpr: innerType = inner->expr_as_UnaryExpr()->type(); break;
        case ir::ExprUnion_IfExpr: innerType = inner->expr_as_IfExpr()->type(); break;
        case ir::ExprUnion_WhenExpr: innerType = inner->expr_as_WhenExpr()->type(); break;
        case ir::ExprUnion_ElvisExpr: innerType = inner->expr_as_ElvisExpr()->type(); break;
        case ir::ExprUnion_NotNullExpr: innerType = inner->expr_as_NotNullExpr()->type(); break;
        default:
            // Cannot cheaply prove non-nullability -> soft-fail (route to interpreter).
            return ec.failExpr("NotNullExpr operand type not statically provable non-null");
    }
    if (innerType != nullptr && innerType->type_type() == ir::TypeUnion_NullableType) {
        // DETERMINISM: a real null here must raise the JVM's exact Rt_Exception; soft-fail.
        return ec.failExpr("NotNullExpr on a nullable operand — JVM must raise on null");
    }

    // Operand is statically non-nullable: the assertion cannot fire. Pass the value through.
    return lowerExpr(ec, *inner);
}

// -------------------------------------------------------------------------------------
// WhenExpr — `when (key) { ... }` as an expression.
//
// Two chooser shapes:
//   IterativeWhenChooser — evaluate key_expr once (if present), then test conditions in order;
//     the first matching WhenCondition selects exprs[condition.index()]; else_index selects the
//     fallback. With no key_expr, each condition.expr() is itself a boolean guard.
//   LookupWhenChooser    — key_expr matched against a table of constant keys; lookup_values[i] is
//     the exprs[] index for lookup_keys[i]; else_index is the fallback.
//
// DETERMINISM: matching semantics must equal the interpreter's. Rell `when` matches by VALUE
// equality. For the inline path we only support keys/conditions that are themselves inline-
// comparable runtime values, and we lower the match as an exact bit/payload comparison of the
// inline lattice. Any non-inline key, missing else with no exhaustive cover, or a condition we
// can't reduce to an inline equality, makes the whole function soft-fail.
//
// We do NOT reimplement cross-type equality here: a `when` over the integer/boolean/rowid/enum
// inline carriers compares the i64 payload (and tag) directly, which is bit-exact with the JVM's
// structural equality for those carriers. Decimal/bigint/text/etc. keys -> soft-fail.
// -------------------------------------------------------------------------------------

// A static key type is safe for inline tag+payload equality ONLY when its inline representation
// is canonical (one bit pattern per value). That holds for BOOLEAN/INTEGER/ROWID (primitive
// carriers) and ENUM (ordinal-as-i64). It does NOT hold for DEC_LONG (same value, many scales),
// nor for any HANDLE-carried type (text/bytes/bigint-over-envelope/collections/...), where the
// JVM's structural/value equality cannot be reproduced by comparing a 16-byte inline struct.
// DETERMINISM: gating on the static type keeps `when` matching bit-exact; everything else
// soft-fails to the interpreter.
bool keyTypeInlineComparable(const ir::Type *type) {
    if (type == nullptr) return false;
    switch (type->type_type()) {
        case ir::TypeUnion_EnumType:
            return true;
        case ir::TypeUnion_PrimitiveType: {
            switch (type->type_as_PrimitiveType()->kind()) {
                case ir::PrimitiveTypeKind_BOOLEAN:
                case ir::PrimitiveTypeKind_INTEGER:
                case ir::PrimitiveTypeKind_ROWID:
                    return true;
                default:
                    return false;
            }
        }
        default:
            return false;
    }
}

// Compare two runtime values for the narrow set of inline-comparable carriers. Returns an i1.
// Compares tag THEN payload so e.g. INTEGER 0 and BOOLEAN false (both payload 0) are distinct —
// matching JVM type identity. The CALLER must have proven (via keyTypeInlineComparable on the
// static key type) that both operands use a canonical inline carrier.
llvm::Value *emitInlineEq(EmitContext &ec, llvm::Value *a, llvm::Value *b) {
    llvm::IRBuilder<> &ib = ec.builder();
    llvm::Value *tagA = ec.unpackTag(a);
    llvm::Value *tagB = ec.unpackTag(b);
    llvm::Value *payA = ec.unpackPayloadI64(a);
    llvm::Value *payB = ec.unpackPayloadI64(b);
    llvm::Value *tagEq = ib.CreateICmpEQ(tagA, tagB, "when_tag_eq");
    llvm::Value *payEq = ib.CreateICmpEQ(payA, payB, "when_pay_eq");
    return ib.CreateAnd(tagEq, payEq, "when_eq");
}

// Lower the selected expr index into `result` then branch to mergeBB. Shared by both choosers.
bool emitWhenArm(EmitContext &ec, const ir::WhenExpr &when, int32_t exprIndex,
                 llvm::AllocaInst *result, llvm::BasicBlock *mergeBB) {
    const auto *exprs = when.exprs();
    if (exprs == nullptr || exprIndex < 0 ||
        static_cast<uint32_t>(exprIndex) >= exprs->size()) {
        return ec.fail("WhenExpr selected index out of range");
    }
    const ir::Expr *armExpr = exprs->Get(exprIndex);
    if (armExpr == nullptr) return ec.fail("WhenExpr arm expr is null");
    llvm::Value *armVal = lowerExpr(ec, *armExpr);
    if (armVal == nullptr) return false;
    ec.builder().CreateStore(armVal, result);
    ec.builder().CreateBr(mergeBB);
    return true;
}

llvm::Value *lowerWhenIterative(EmitContext &ec, const ir::WhenExpr &when,
                                const ir::IterativeWhenChooser &chooser,
                                llvm::AllocaInst *result, llvm::BasicBlock *mergeBB) {
    llvm::IRBuilder<> &b = ec.builder();
    llvm::Function *fn = b.GetInsertBlock()->getParent();

    const ir::Expr *keyExpr = chooser.key_expr();
    llvm::Value *keyVal = nullptr;
    if (keyExpr != nullptr) {
        // DETERMINISM: only inline-canonical key carriers can be matched by tag+payload equality.
        if (!keyTypeInlineComparable(exprResultType(keyExpr))) {
            return ec.failExpr("when key is not an inline-comparable type — soft-fail");
        }
        keyVal = lowerExpr(ec, *keyExpr);
        if (keyVal == nullptr) return nullptr;
    }

    const auto *conds = chooser.conditions();
    if (conds == nullptr) return ec.failExpr("IterativeWhenChooser has null conditions");

    // Sequential test chain: cond_i -> arm_i, else fall to cond_{i+1}; final fallthrough is the
    // else arm (or, if no else, soft-fail — a non-exhaustive when without else can't be proven
    // here and the JVM raises on no-match).
    for (uint32_t i = 0; i < conds->size(); ++i) {
        const auto *cond = conds->Get(i);
        if (cond == nullptr) return ec.failExpr("null WhenCondition");
        const ir::Expr *condExpr = cond->expr();
        if (condExpr == nullptr) return ec.failExpr("WhenCondition has null expr");

        llvm::Value *condVal = lowerExpr(ec, *condExpr);
        if (condVal == nullptr) return nullptr;

        // With a key: arm fires when key == condVal. Without a key: condExpr IS a boolean guard,
        // fires when its payload is truthy.
        llvm::Value *match;
        if (keyVal != nullptr) {
            match = emitInlineEq(ec, keyVal, condVal);
            if (match == nullptr) return nullptr;
        } else {
            llvm::Value *p = ec.unpackPayloadI64(condVal);
            match = b.CreateICmpNE(p, b.getInt64(0), "when_guard");
        }

        auto *armBB = llvm::BasicBlock::Create(ec.ctx(), "when_arm", fn);
        auto *nextBB = llvm::BasicBlock::Create(ec.ctx(), "when_next", fn);
        b.CreateCondBr(match, armBB, nextBB);

        b.SetInsertPoint(armBB);
        if (!emitWhenArm(ec, when, cond->index(), result, mergeBB)) return nullptr;

        b.SetInsertPoint(nextBB);
    }

    // Fallthrough = else. else_index is nullable (=null ⇒ no else).
    auto elseIndexOpt = chooser.else_index();
    int32_t elseIndex = elseIndexOpt.has_value() ? *elseIndexOpt : -1;
    if (elseIndex < 0) {
        // DETERMINISM: a when-EXPRESSION is always exhaustive in valid Rell (the type checker
        // requires an else or full enum cover). If the serialized else_index is absent we cannot
        // prove the cover here, so soft-fail rather than emit an unreachable/UB merge.
        return ec.failExpr("when expression without else arm — soft-fail");
    }
    if (!emitWhenArm(ec, when, elseIndex, result, mergeBB)) return nullptr;

    b.SetInsertPoint(mergeBB);
    return b.CreateLoad(ec.valueType(), result, "when_value");
}

llvm::Value *lowerWhenLookup(EmitContext &ec, const ir::WhenExpr &when,
                             const ir::LookupWhenChooser &chooser,
                             llvm::AllocaInst *result, llvm::BasicBlock *mergeBB) {
    llvm::IRBuilder<> &b = ec.builder();
    llvm::Function *fn = b.GetInsertBlock()->getParent();

    const ir::Expr *keyExpr = chooser.key_expr();
    if (keyExpr == nullptr) return ec.failExpr("LookupWhenChooser has null key_expr");
    // DETERMINISM: only inline-canonical key carriers can be matched by tag+payload equality.
    if (!keyTypeInlineComparable(exprResultType(keyExpr))) {
        return ec.failExpr("lookup when key is not an inline-comparable type — soft-fail");
    }
    llvm::Value *keyVal = lowerExpr(ec, *keyExpr);
    if (keyVal == nullptr) return nullptr;

    const auto *keys = chooser.lookup_keys();
    const auto *values = chooser.lookup_values();
    if (keys == nullptr || values == nullptr || keys->size() != values->size()) {
        return ec.failExpr("LookupWhenChooser keys/values mismatch");
    }

    // Lowered as a chain of inline-equality tests against each constant key. This is O(n) IR but
    // n is small (lookup whens are enum/int dispatch) and stays bit-exact; we deliberately avoid
    // a jump table because the inline lattice's equality (tag+payload) is not a dense index.
    for (uint32_t i = 0; i < keys->size(); ++i) {
        const auto *constKey = keys->Get(i);
        if (constKey == nullptr) return ec.failExpr("null lookup key constant");
        // ConstantValue has no type(); pass nullptr — only inline carriers are accepted.
        llvm::Value *keyConst = lowerInlineValue(ec, /*valueType=*/nullptr, *constKey);
        if (keyConst == nullptr) return nullptr;  // non-inline key -> already soft-failed

        llvm::Value *match = emitInlineEq(ec, keyVal, keyConst);
        if (match == nullptr) return nullptr;

        auto *armBB = llvm::BasicBlock::Create(ec.ctx(), "lookup_arm", fn);
        auto *nextBB = llvm::BasicBlock::Create(ec.ctx(), "lookup_next", fn);
        b.CreateCondBr(match, armBB, nextBB);

        b.SetInsertPoint(armBB);
        if (!emitWhenArm(ec, when, values->Get(i), result, mergeBB)) return nullptr;

        b.SetInsertPoint(nextBB);
    }

    auto elseIndexOpt = chooser.else_index();
    int32_t elseIndex = elseIndexOpt.has_value() ? *elseIndexOpt : -1;
    if (elseIndex < 0) {
        return ec.failExpr("lookup when without else arm — soft-fail");
    }
    if (!emitWhenArm(ec, when, elseIndex, result, mergeBB)) return nullptr;

    b.SetInsertPoint(mergeBB);
    return b.CreateLoad(ec.valueType(), result, "lookup_value");
}

llvm::Value *lowerWhen(EmitContext &ec, const ir::WhenExpr &when) {
    const ir::WhenChooser *chooser = when.chooser();
    if (chooser == nullptr) return ec.failExpr("WhenExpr has null chooser");
    if (when.exprs() == nullptr) return ec.failExpr("WhenExpr has null exprs");

    llvm::AllocaInst *result = entryAlloca(ec, "when_result");
    auto *mergeBB =
        llvm::BasicBlock::Create(ec.ctx(), "when_merge", ec.builder().GetInsertBlock()->getParent());

    switch (chooser->chooser_type()) {
        case ir::WhenChooserUnion_IterativeWhenChooser:
            return lowerWhenIterative(ec, when, *chooser->chooser_as_IterativeWhenChooser(),
                                      result, mergeBB);
        case ir::WhenChooserUnion_LookupWhenChooser:
            return lowerWhenLookup(ec, when, *chooser->chooser_as_LookupWhenChooser(), result,
                                   mergeBB);
        default:
            return ec.failExpr("unsupported WhenChooser variant");
    }
}

// -------------------------------------------------------------------------------------
// StatementExpr — a statement that yields a value (e.g. a block expression).
//
// The statement is lowered by lower_stmt.cpp, which writes the produced value into the slot the
// Wiring phase reserves for the StatementExpr result. We model that contract minimally: a
// StatementExpr whose statement does not reduce to a value lower_stmt can place is soft-failed.
//
// DETERMINISM: the value-yielding statement must place EXACTLY one value with the same evaluation
// order as the interpreter. Rather than guess the result-slot wiring here, we conservatively
// soft-fail StatementExpr unless lower_stmt has been wired to expose a result slot. Until that
// wiring lands, StatementExpr always soft-fails — correct (interpreter runs it), never wrong.
// -------------------------------------------------------------------------------------

llvm::Value *lowerStatementExpr(EmitContext &ec, const ir::StatementExpr &se) {
    const ir::Stmt *stmt = se.stmt();
    if (stmt == nullptr) return ec.failExpr("StatementExpr has null stmt");
    // The result-slot wiring between StatementExpr and lower_stmt is owned by the Wiring phase
    // and not exposed through this ABI revision. Soft-fail until it is: the interpreter runs the
    // statement-expression with the exact semantics. Never emit a guessed value.
    return ec.failExpr("StatementExpr — result-slot wiring not yet available; soft-fail");
}

// -------------------------------------------------------------------------------------
// ConstantValueExpr lowering — unwrap the TypedValue then defer to lowerInlineValue.
// -------------------------------------------------------------------------------------

llvm::Value *lowerConst(EmitContext &ec, const ir::ConstantValueExpr &c) {
    const ir::TypedValue *tv = c.typed_value();
    if (tv == nullptr) return ec.failExpr("ConstantValueExpr has no TypedValue");
    return lowerInlineValue(ec, tv->type(), *tv);
}

}  // namespace

// -------------------------------------------------------------------------------------
// The Expr-union dispatcher (declared in rell_runtime.h §9).
//
// VALUE-CORE variants are handled here. BinaryExpr/UnaryExpr go to lower_ops.cpp;
// FunctionCallExpr/MemberExpr and the DB at-exprs go to lower_call.cpp. Everything else
// soft-fails the whole function.
// -------------------------------------------------------------------------------------

llvm::Value *lowerExpr(EmitContext &ec, const ir::Expr &expr) {
    if (ec.failed()) return nullptr;  // never emit IR after a prior soft-fail.

    switch (expr.expr_type()) {
        // ---- value core (this file) ----
        case ir::ExprUnion_VarExpr:
            return lowerVar(ec, *expr.expr_as_VarExpr());
        case ir::ExprUnion_ConstantValueExpr:
            return lowerConst(ec, *expr.expr_as_ConstantValueExpr());
        case ir::ExprUnion_IfExpr:
            return lowerIf(ec, *expr.expr_as_IfExpr());
        case ir::ExprUnion_ElvisExpr:
            return lowerElvis(ec, *expr.expr_as_ElvisExpr());
        case ir::ExprUnion_NotNullExpr:
            return lowerNotNull(ec, *expr.expr_as_NotNullExpr());
        case ir::ExprUnion_WhenExpr:
            return lowerWhen(ec, *expr.expr_as_WhenExpr());
        case ir::ExprUnion_StatementExpr:
            return lowerStatementExpr(ec, *expr.expr_as_StatementExpr());

        // ---- operators (lower_ops.cpp) ----
        case ir::ExprUnion_BinaryExpr:
            return lowerBinary(ec, *expr.expr_as_BinaryExpr());
        case ir::ExprUnion_UnaryExpr:
            return lowerUnary(ec, *expr.expr_as_UnaryExpr());

        // ---- calls / members (lower_call.cpp) ----
        case ir::ExprUnion_FunctionCallExpr:
            return lowerCall(ec, *expr.expr_as_FunctionCallExpr());
        case ir::ExprUnion_MemberExpr:
            return lowerMember(ec, *expr.expr_as_MemberExpr());

        // ---- everything else: soft-fail the whole function (interpreter runs it) ----
        case ir::ExprUnion_ErrorExpr:
            // DETERMINISM: ErrorExpr should never reach a successfully-compiled App; if it does,
            // refuse to emit IR.
            return ec.failExpr("ErrorExpr in compiled body — soft-fail");
        default:
            return ec.failExpr("unsupported ExprUnion variant " +
                               std::to_string(static_cast<int>(expr.expr_type())));
    }
}

}  // namespace rell::llvm_rt
