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
//                         (Bool/Int/Rowid/Enum/Null/Unit + long-fitting BigInteger), plus
//                         Text/ByteArray/Decimal materialised by a runtime helper (Decimal reboxes
//                         through the JVM and cracks back via from_jvm — canonical DEC_LONG or HANDLE).
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
#include <vector>

#include <llvm/IR/BasicBlock.h>
#include <llvm/IR/Constants.h>
#include <llvm/IR/DerivedTypes.h>
#include <llvm/IR/GlobalVariable.h>

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

// The hidden trailing ctx pointer of the function currently being emitted (the LAST arg, a `ptr`),
// mirroring lower_call.cpp's currentCtxArg. nullptr if there is no enclosing function — a wiring
// bug, not an envelope miss; the caller then soft-fails.
llvm::Value *currentCtxArgExpr(EmitContext &ec) {
    llvm::BasicBlock *bb = ec.builder().GetInsertBlock();
    if (bb == nullptr) return nullptr;
    llvm::Function *fn = bb->getParent();
    if (fn == nullptr || fn->arg_empty()) return nullptr;
    return fn->getArg(fn->arg_size() - 1);
}

// Emit a native composite construction: spill the already-lowered field values into a stack
// RellValue[n] (in the ENTRY block so it is hoisted out of any loop), then call the sret-style
// runtime helper `rell_make_composite(out, ctx, scaleDisc, fields, n)`. Returns the constructed
// COMPOSITE runtime value (loaded from the out slot), or nullptr after ec.fail(). `scaleDisc` is
// kCompositeTupleDisc for a tuple, the struct-def-index for a struct (rell_runtime.h §4d). The
// fields MUST already be in DECLARED order (matching Rt_TupleValue.elements / Rt_StructValue.get(i)),
// which the caller guarantees.
llvm::Value *emitMakeComposite(EmitContext &ec, int32_t scaleDisc,
                               llvm::ArrayRef<llvm::Value *> fieldVals) {
    llvm::Value *ctx = currentCtxArgExpr(ec);
    if (ctx == nullptr) return ec.failExpr("emitMakeComposite: no enclosing function ctx arg");

    llvm::IRBuilder<> &b = ec.builder();
    llvm::StructType *valTy = ec.valueType();
    auto *ptrTy = llvm::PointerType::getUnqual(ec.ctx());
    const int32_t n = static_cast<int32_t>(fieldVals.size());

    llvm::Function *fn = b.GetInsertBlock()->getParent();
    llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
    auto *arrTy = llvm::ArrayType::get(valTy, static_cast<uint64_t>(n < 0 ? 0 : n));
    llvm::Value *fieldsArr = entryB.CreateAlloca(arrTy, nullptr, "comp_fields");
    llvm::Value *outSlot = entryB.CreateAlloca(valTy, nullptr, "comp_out");

    for (int32_t i = 0; i < n; ++i) {
        llvm::Value *slot =
            b.CreateInBoundsGEP(arrTy, fieldsArr, {b.getInt32(0), b.getInt32(i)}, "comp_field_slot");
        b.CreateStore(fieldVals[i], slot);
    }
    llvm::Value *fieldsPtr = fieldsArr;
    if (n > 0) {
        fieldsPtr =
            b.CreateInBoundsGEP(arrTy, fieldsArr, {b.getInt32(0), b.getInt32(0)}, "comp_fields_p");
    }

    // void rell_make_composite(ptr out, ptr ctx, i32 scaleDisc, ptr fields, i32 n)
    auto *fnTy = llvm::FunctionType::get(
        llvm::Type::getVoidTy(ec.ctx()),
        {ptrTy, ptrTy, b.getInt32Ty(), ptrTy, b.getInt32Ty()}, /*isVarArg=*/false);
    llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_make_composite", fnTy);
    b.CreateCall(callee, {outSlot, ctx, b.getInt32(scaleDisc), fieldsPtr, b.getInt32(n)});
    return b.CreateLoad(valTy, outSlot, "comp_value");
}

// Emit a native list construction: spill the already-lowered element values into a stack
// RellValue[n] (ENTRY block, hoisted out of any loop), then call the sret-style runtime helper
// `rell_make_list(out, ctx, listTypeId, elems, n)`. `listTypeId` is interned (ec.listTypes()) so the
// JVM mirror resolves it to the EXACT Rt_ValueClass list type. Returns the constructed LIST runtime
// value, or nullptr after ec.fail(). Elements MUST be in source/iteration order (matching the
// interpreter's `expr.exprs.map { evaluateExpr(it) }` -> Rt_ListValue.elements).
llvm::Value *emitMakeList(EmitContext &ec, int32_t listTypeId,
                          llvm::ArrayRef<llvm::Value *> elemVals) {
    llvm::Value *ctx = currentCtxArgExpr(ec);
    if (ctx == nullptr) return ec.failExpr("emitMakeList: no enclosing function ctx arg");

    llvm::IRBuilder<> &b = ec.builder();
    llvm::StructType *valTy = ec.valueType();
    auto *ptrTy = llvm::PointerType::getUnqual(ec.ctx());
    const int32_t n = static_cast<int32_t>(elemVals.size());

    llvm::Function *fn = b.GetInsertBlock()->getParent();
    llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
    auto *arrTy = llvm::ArrayType::get(valTy, static_cast<uint64_t>(n < 0 ? 0 : n));
    llvm::Value *elemsArr = entryB.CreateAlloca(arrTy, nullptr, "list_elems");
    llvm::Value *outSlot = entryB.CreateAlloca(valTy, nullptr, "list_out");

    for (int32_t i = 0; i < n; ++i) {
        llvm::Value *slot =
            b.CreateInBoundsGEP(arrTy, elemsArr, {b.getInt32(0), b.getInt32(i)}, "list_elem_slot");
        b.CreateStore(elemVals[i], slot);
    }
    llvm::Value *elemsPtr = elemsArr;
    if (n > 0) {
        elemsPtr =
            b.CreateInBoundsGEP(arrTy, elemsArr, {b.getInt32(0), b.getInt32(0)}, "list_elems_p");
    }

    // void rell_make_list(ptr out, ptr ctx, i32 listTypeId, ptr elems, i32 n)
    auto *fnTy = llvm::FunctionType::get(
        llvm::Type::getVoidTy(ec.ctx()),
        {ptrTy, ptrTy, b.getInt32Ty(), ptrTy, b.getInt32Ty()}, /*isVarArg=*/false);
    llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_make_list", fnTy);
    b.CreateCall(callee, {outSlot, ctx, b.getInt32(listTypeId), elemsPtr, b.getInt32(n)});
    return b.CreateLoad(valTy, outSlot, "list_value");
}

// Emit a native byte_array construction from a fixed literal byte buffer: materialise the bytes as a
// private constant global (a [n x i8] array), then call the sret-style runtime helper
// `rell_make_bytearray(out, ctx, bytes, n)` (which copies the bytes into an arena buffer). Returns the
// constructed BYTEARRAY runtime value, or nullptr after ec.fail(). Bit-exact with rr_interpreter.kt's
// `RR_ConstantValue.ByteArray -> Rt_ByteArrayValue.get(cv.value)` — the bytes are the literal's exact
// bytes, and to_jvm reboxes via Rt_ByteArrayValue.get (canonicalising empty -> EMPTY).
llvm::Value *emitMakeByteArray(EmitContext &ec, const uint8_t *bytes, int32_t n) {
    llvm::Value *ctx = currentCtxArgExpr(ec);
    if (ctx == nullptr) return ec.failExpr("emitMakeByteArray: no enclosing function ctx arg");

    llvm::IRBuilder<> &b = ec.builder();
    llvm::StructType *valTy = ec.valueType();
    auto *ptrTy = llvm::PointerType::getUnqual(ec.ctx());
    if (n < 0) n = 0;

    // The literal bytes as a private, unnamed constant global ([n x i8]); the runtime helper copies
    // them into the arena, so the global's lifetime (the JIT module) safely outlives every call.
    llvm::Value *bytesPtr = llvm::ConstantPointerNull::get(ptrTy);
    if (n > 0) {
        std::vector<uint8_t> data(bytes, bytes + n);
        llvm::Constant *arr = llvm::ConstantDataArray::get(ec.ctx(), data);
        auto *gv = new llvm::GlobalVariable(ec.module(), arr->getType(), /*isConstant=*/true,
                                            llvm::GlobalValue::PrivateLinkage, arr, "ba_const");
        gv->setUnnamedAddr(llvm::GlobalValue::UnnamedAddr::Global);
        bytesPtr = gv;
    }

    llvm::Function *fn = b.GetInsertBlock()->getParent();
    llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
    llvm::Value *outSlot = entryB.CreateAlloca(valTy, nullptr, "ba_out");

    // void rell_make_bytearray(ptr out, ptr ctx, ptr bytes, i32 n)
    auto *fnTy = llvm::FunctionType::get(llvm::Type::getVoidTy(ec.ctx()),
                                         {ptrTy, ptrTy, ptrTy, b.getInt32Ty()}, /*isVarArg=*/false);
    llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_make_bytearray", fnTy);
    b.CreateCall(callee, {outSlot, ctx, bytesPtr, b.getInt32(n)});
    return b.CreateLoad(valTy, outSlot, "ba_value");
}

// Decode a FlatBuffer string (valid UTF-8 — the Rell frontend never emits ill-formed text) into a vector
// of UTF-16 code units, EXACTLY as the JVM produces it when FlatBuffers Java decodes the same wire bytes
// into a java.lang.String. This is the load-bearing determinism step for a text CONSTANT: the
// interpreter's text is `RR_ConstantValue.Text(v.value)` where `v.value` is the UTF-8-decoded String, so
// the native UTF-16 buffer must equal that String's code units. Standard UTF-8 -> UTF-16:
//   * U+0000..U+FFFF (1-3 UTF-8 bytes) -> a single code unit (the BMP code point).
//   * U+10000..U+10FFFF (4 UTF-8 bytes) -> a SURROGATE PAIR of two code units, the same pair the JVM
//     stores (a 4-byte UTF-8 sequence decodes to a supplementary code point, which Java's String holds
//     as a high+low surrogate). So a non-BMP literal round-trips bit-exactly.
// DETERMINISM: we do NOT fork java.lang.String semantics — we reproduce the ONE well-defined UTF-8 ->
// UTF-16 mapping for VALID input (which the frontend guarantees). If the bytes were ever malformed we'd
// diverge, but a Rell text literal is always valid Unicode (compiler-validated source), so this is exact.
// Returns true and fills `out`; returns false only on a structurally malformed sequence (never expected
// — the caller then soft-fails rather than guessing).
bool decodeUtf8ToUtf16(const uint8_t *bytes, int32_t n, std::vector<uint16_t> &out) {
    out.clear();
    int32_t i = 0;
    while (i < n) {
        const uint32_t b0 = bytes[i];
        uint32_t cp;
        int32_t extra;
        if (b0 < 0x80) {
            cp = b0;
            extra = 0;
        } else if ((b0 & 0xE0) == 0xC0) {
            cp = b0 & 0x1F;
            extra = 1;
        } else if ((b0 & 0xF0) == 0xE0) {
            cp = b0 & 0x0F;
            extra = 2;
        } else if ((b0 & 0xF8) == 0xF0) {
            cp = b0 & 0x07;
            extra = 3;
        } else {
            return false;  // invalid leading byte (never from a valid Rell literal).
        }
        if (i + extra >= n) return false;  // truncated sequence.
        for (int32_t k = 0; k < extra; ++k) {
            const uint32_t cont = bytes[i + 1 + k];
            if ((cont & 0xC0) != 0x80) return false;  // invalid continuation byte.
            cp = (cp << 6) | (cont & 0x3F);
        }
        i += extra + 1;
        if (cp <= 0xFFFF) {
            // BMP code point -> one code unit. (A surrogate code point 0xD800..0xDFFF cannot appear in
            // valid UTF-8; if it somehow did it would be emitted as-is, matching a 3-byte CESU-style
            // decode — but the frontend never emits one, so this stays bit-exact for real literals.)
            out.push_back(static_cast<uint16_t>(cp));
        } else {
            // Supplementary code point -> surrogate pair (the EXACT pair Java's String stores).
            cp -= 0x10000;
            out.push_back(static_cast<uint16_t>(0xD800 + (cp >> 10)));
            out.push_back(static_cast<uint16_t>(0xDC00 + (cp & 0x3FF)));
        }
    }
    return true;
}

// Emit a native text construction from a fixed literal UTF-16 buffer: materialise the code units as a
// private constant global (a [n x i16] array), then call the sret-style runtime helper
// `rell_make_text(out, ctx, units, n)` (which copies the units into an arena buffer). Returns the
// constructed TEXT runtime value, or nullptr after ec.fail(). Bit-exact with rr_interpreter.kt's
// `RR_ConstantValue.Text -> Rt_TextValue.get(cv.value)` — the code units are the literal's exact String
// contents, and to_jvm reboxes via Rt_TextValue.get (canonicalising empty -> EMPTY).
llvm::Value *emitMakeText(EmitContext &ec, const uint16_t *units, int32_t n) {
    llvm::Value *ctx = currentCtxArgExpr(ec);
    if (ctx == nullptr) return ec.failExpr("emitMakeText: no enclosing function ctx arg");

    llvm::IRBuilder<> &b = ec.builder();
    llvm::StructType *valTy = ec.valueType();
    auto *ptrTy = llvm::PointerType::getUnqual(ec.ctx());
    if (n < 0) n = 0;

    // The literal code units as a private, unnamed constant global ([n x i16]); the runtime helper copies
    // them into the arena, so the global's lifetime (the JIT module) safely outlives every call.
    llvm::Value *unitsPtr = llvm::ConstantPointerNull::get(ptrTy);
    if (n > 0) {
        std::vector<uint16_t> data(units, units + n);
        llvm::Constant *arr = llvm::ConstantDataArray::get(ec.ctx(), data);
        auto *gv = new llvm::GlobalVariable(ec.module(), arr->getType(), /*isConstant=*/true,
                                            llvm::GlobalValue::PrivateLinkage, arr, "txt_const");
        gv->setUnnamedAddr(llvm::GlobalValue::UnnamedAddr::Global);
        unitsPtr = gv;
    }

    llvm::Function *fn = b.GetInsertBlock()->getParent();
    llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
    llvm::Value *outSlot = entryB.CreateAlloca(valTy, nullptr, "txt_out");

    // void rell_make_text(ptr out, ptr ctx, ptr units, i32 n)
    auto *fnTy = llvm::FunctionType::get(llvm::Type::getVoidTy(ec.ctx()),
                                         {ptrTy, ptrTy, ptrTy, b.getInt32Ty()}, /*isVarArg=*/false);
    llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_make_text", fnTy);
    b.CreateCall(callee, {outSlot, ctx, unitsPtr, b.getInt32(n)});
    return b.CreateLoad(valTy, outSlot, "txt_value");
}

// Emit a native decimal CONSTANT materialisation from a fixed literal string buffer (the literal's
// ASCII code units). Mirrors emitMakeText, but calls `rell_make_decimal(out, ctx, units, n)`, which
// reboxes the string into the canonical Rt_DecimalValue on the JVM and cracks it back via from_jvm —
// so the inline DEC_LONG is the EXACT stripped normal form (or a wide HANDLE) with NO C++
// re-normalisation. Returns the constructed decimal runtime value, or nullptr after ec.fail().
// Bit-exact with rr_interpreter.kt's `RR_ConstantValue.Decimal -> Rt_DecimalValue.get(cv.value)`.
llvm::Value *emitMakeDecimal(EmitContext &ec, const uint16_t *units, int32_t n) {
    llvm::Value *ctx = currentCtxArgExpr(ec);
    if (ctx == nullptr) return ec.failExpr("emitMakeDecimal: no enclosing function ctx arg");

    llvm::IRBuilder<> &b = ec.builder();
    llvm::StructType *valTy = ec.valueType();
    auto *ptrTy = llvm::PointerType::getUnqual(ec.ctx());
    if (n < 0) n = 0;

    // The literal code units as a private, unnamed constant global ([n x i16]); the runtime helper
    // copies them into a JVM String, so the global's lifetime (the JIT module) outlives every call.
    llvm::Value *unitsPtr = llvm::ConstantPointerNull::get(ptrTy);
    if (n > 0) {
        std::vector<uint16_t> data(units, units + n);
        llvm::Constant *arr = llvm::ConstantDataArray::get(ec.ctx(), data);
        auto *gv = new llvm::GlobalVariable(ec.module(), arr->getType(), /*isConstant=*/true,
                                            llvm::GlobalValue::PrivateLinkage, arr, "dec_const");
        gv->setUnnamedAddr(llvm::GlobalValue::UnnamedAddr::Global);
        unitsPtr = gv;
    }

    llvm::Function *fn = b.GetInsertBlock()->getParent();
    llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
    llvm::Value *outSlot = entryB.CreateAlloca(valTy, nullptr, "dec_out");

    // void rell_make_decimal(ptr out, ptr ctx, ptr units, i32 n)
    auto *fnTy = llvm::FunctionType::get(llvm::Type::getVoidTy(ec.ctx()),
                                         {ptrTy, ptrTy, ptrTy, b.getInt32Ty()}, /*isVarArg=*/false);
    llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_make_decimal", fnTy);
    b.CreateCall(callee, {outSlot, ctx, unitsPtr, b.getInt32(n)});
    return b.CreateLoad(valTy, outSlot, "dec_value");
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
        case ir::ExprUnion_ListLiteralExpr: return expr->expr_as_ListLiteralExpr()->type();
        case ir::ExprUnion_ListSubscriptExpr: return expr->expr_as_ListSubscriptExpr()->type();
        // TupleExpr carries a real TupleType (the structural tuple type). StructExpr carries only a
        // struct_def_index (no Type table), so it is classified by expr shape in lower_ops.cpp's
        // equality gate rather than here.
        case ir::ExprUnion_TupleExpr: return expr->expr_as_TupleExpr()->type();
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
// Produces a runtime-value SSA ({i8,i32,i64}) directly from a serialized ValueUnion. Most variants
// have a pure inline RellValue rep; Text/ByteArray/Decimal are materialised by a runtime helper
// (rell_make_text / rell_make_bytearray copy literal bytes into the arena; rell_make_decimal reboxes
// the literal string through the JVM and cracks it back via from_jvm to a canonical DEC_LONG / HANDLE).
// The remaining heap/opaque constants (Gtv/Struct/Collection/Map/Tuple/Meta, and out-of-envelope
// BigInteger) have no by-bytes constant-rebox entry, so those make the whole function soft-fail
// rather than emit an incorrect inline.
//
// `valueType` is the TypedValue.type() (nullable for ConstantValue, where type is contextual);
// it is only consulted to disambiguate the Enum ordinal's carrier, never to widen the envelope.
// -------------------------------------------------------------------------------------

// Parse a decimal/bigint string into a long-fitting inline value, or fail the inline path.
//
// DETERMINISM: the C++ side NEVER reimplements Lib_DecimalMath.scale() / BigInteger bounds. We
// only accept the narrow, unambiguous long-fitting bigint form here; a bigint string is inline iff
// it is a base-10 integer literal whose value fits int64 (parsed with full overflow checking),
// else it soft-fails so the JVM constructs the exact Rt_BigIntegerValue. A decimal string is NOT
// parsed here at all — its stripped (mantissa, scale) normalisation must match Tf_LongScaleDecimal
// bit-for-bit, which only the JVM guarantees; the DecimalValue case routes the string through
// rell_make_decimal (JVM rebox + from_jvm crack) instead, so the inline DEC_LONG / HANDLE is exact
// without any C++ re-derivation. (Decimal *intrinsics* likewise only see JVM-validated mantissa/scale.)

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
            // Enum values are carried inline by the ENUM tag: payload.i64 = ordinal (attr_index),
            // `scale` = enum-type index (def_index). to_jvm reconstitutes the exact Rt_RR_EnumValue
            // from (def_index, ordinal) on rebox (Llvm_SysBridge.enumValue), mirroring the
            // interpreter's RR_ConstantValue.Enum construction. DETERMINISM: the ordinal is the
            // canonical identity (Rt_RR_EnumValue compares by ordinal + typeName), and carrying the
            // def_index makes the rebox pick the EXACT enum type — so it round-trips bit-exactly and
            // a `when`/eq against another value of the SAME enum type compares ordinals correctly.
            const auto *v = value.value_as_EnumValue();
            // attr_index / def_index are uint; ordinals and def indices are small, widening is exact.
            return ec.packInline(RellTag::ENUM,
                                 b.getInt32(static_cast<int32_t>(v->def_index())),
                                 b.getInt64(static_cast<int64_t>(v->attr_index())));
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
        case ir::ValueUnion_DecimalValue: {
            // decimal literal -> a canonical inline DEC_LONG (or a wide HANDLE) reboxed by the JVM.
            // DETERMINISM: the stripped (mantissa, scale) must equal Tf_LongScaleDecimal's EXACTLY; we
            // refuse to derive it in C++. Instead rell_make_decimal routes the literal's range-validated
            // string through Llvm_SysBridge.decimalValue -> Rt_DecimalValue.get, then cracks the result
            // back via from_jvm — the SAME normal-form transcription every reboxed decimal field uses —
            // so the inline value is bit-exact (1.0 and 1.00 both -> (1, 0)). The fbs `value` is the
            // tokenizer's canonical plain string (`scaleDecimal(...).toString()`), already UTF-8/ASCII.
            const auto *v = value.value_as_DecimalValue();
            if (v == nullptr) return ec.failExpr("DecimalValue constant has null payload");
            const auto *s = v->value();  // flatbuffers::String* (UTF-8 wire bytes; ASCII for a decimal).
            const int32_t byteLen = s != nullptr ? static_cast<int32_t>(s->size()) : 0;
            if (s == nullptr || byteLen == 0) {
                // An empty decimal string is impossible from the tokenizer; refuse to guess.
                return ec.failExpr("decimal constant: empty string (soft-fail to JVM)");
            }
            const auto *u8 = reinterpret_cast<const uint8_t *>(s->c_str());
            std::vector<uint16_t> units;
            if (!decodeUtf8ToUtf16(u8, byteLen, units)) {
                return ec.failExpr("decimal constant: malformed UTF-8 (soft-fail to JVM)");
            }
            return emitMakeDecimal(ec, units.empty() ? nullptr : units.data(),
                                   static_cast<int32_t>(units.size()));
        }
        case ir::ValueUnion_ByteArrayValue: {
            // byte_array literal x"..." -> a native BYTEARRAY carrier holding the literal's exact bytes
            // (copied into the arena by rell_make_bytearray). Bit-exact with rr_interpreter.kt
            // `RR_ConstantValue.ByteArray -> Rt_ByteArrayValue.get(cv.value)`; the empty literal x""
            // round-trips to Rt_ByteArrayValue.EMPTY on rebox.
            const auto *v = value.value_as_ByteArrayValue();
            if (v == nullptr) return ec.failExpr("ByteArrayValue constant has null payload");
            const auto *bytes = v->value();  // flatbuffers::Vector<uint8_t>* (the [ubyte] field).
            const int32_t n = bytes != nullptr ? static_cast<int32_t>(bytes->size()) : 0;
            const uint8_t *data = (bytes != nullptr && n > 0) ? bytes->data() : nullptr;
            return emitMakeByteArray(ec, data, n);
        }
        case ir::ValueUnion_TextValue: {
            // text literal "..." -> a native TEXT carrier holding the literal's exact UTF-16 code units
            // (the compile-time UTF-8 -> UTF-16 decode of the FlatBuffer string, then copied into the
            // arena by rell_make_text). Bit-exact with rr_interpreter.kt
            // `RR_ConstantValue.Text -> Rt_TextValue.get(cv.value)`: the fbs `value` is a UTF-8 string the
            // JVM decodes to the SAME String, so decoding the same bytes to UTF-16 here yields the
            // identical code units. The empty literal "" round-trips to Rt_TextValue.EMPTY on rebox.
            const auto *v = value.value_as_TextValue();
            if (v == nullptr) return ec.failExpr("TextValue constant has null payload");
            const auto *s = v->value();  // flatbuffers::String* (UTF-8 wire bytes).
            const int32_t byteLen = s != nullptr ? static_cast<int32_t>(s->size()) : 0;
            const auto *u8 = (s != nullptr && byteLen > 0)
                                 ? reinterpret_cast<const uint8_t *>(s->c_str())
                                 : nullptr;
            std::vector<uint16_t> units;
            if (!decodeUtf8ToUtf16(u8, byteLen, units)) {
                // Malformed UTF-8 (never from a valid Rell literal): refuse to guess — soft-fail so the
                // JVM materialises the exact Rt_TextValue from the same bytes.
                return ec.failExpr("text constant: malformed UTF-8 (soft-fail to JVM)");
            }
            return emitMakeText(ec, units.empty() ? nullptr : units.data(),
                                static_cast<int32_t>(units.size()));
        }
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
// TupleExpr — `(a, b, ...)`.
//
// Evaluate each field expr in SOURCE order (observable: side effects/exceptions must match the
// interpreter's `expr.exprs.map { evaluateExpr(it) }`, rr_interpreter.kt TupleLiteral), then build a
// native tuple COMPOSITE (discriminant kCompositeTupleDisc). The tuple TYPE is NOT carried: the
// value-ABI gate forbids a tuple param/return, so a constructed tuple is only ever field-read
// (TupleAttr) or fed into a soft-failing op — never reboxed (to_jvm hard-faults on a tuple
// COMPOSITE). The field ORDER is the source/declared order, matching Rt_TupleValue.elements.
// -------------------------------------------------------------------------------------
llvm::Value *lowerTupleExpr(EmitContext &ec, const ir::TupleExpr &tup) {
    const auto *exprs = tup.exprs();
    if (exprs == nullptr) return ec.failExpr("TupleExpr has null exprs");

    std::vector<llvm::Value *> fieldVals;
    fieldVals.reserve(exprs->size());
    for (flatbuffers::uoffset_t i = 0; i < exprs->size(); ++i) {
        const ir::Expr *fe = exprs->Get(i);
        if (fe == nullptr) return ec.failExpr("TupleExpr field expr is null");
        llvm::Value *v = lowerExpr(ec, *fe);
        if (v == nullptr) return nullptr;  // ec.fail set
        fieldVals.push_back(v);
    }
    return emitMakeComposite(ec, kCompositeTupleDisc, fieldVals);
}

// -------------------------------------------------------------------------------------
// StructExpr — an in-memory struct constructor `S(a = ..., b = ...)`.
//
// The interpreter (rr_interpreter.kt StructCreate) builds an array sized to the struct's FULL
// attribute list, places each StructExpr attr by attr_index, fills unspecified slots with
// Rt_NullValue (defaults), validates each attr's sizeConstraint (which CAN THROW), then constructs.
//
// DETERMINISM / bit-exactness — we lower natively ONLY when:
//   * every struct attribute is provided exactly once (attrs.size() == attributesList.size() and the
//     attr_index set is a permutation of [0, n)). A partial set would require materialising default
//     expressions in the interpreter's exact order — we don't model that, so soft-fail.
//   * NO attribute carries a sizeConstraint — the validation throws an exact Rt_Exception we cannot
//     reproduce inline; soft-fail so the interpreter raises it.
// Otherwise we soft-fail the whole function (interpreter runs it). When native, we evaluate the
// attr exprs in SOURCE order (matching the interpreter's `for (attr in expr.attrs)` evaluation
// order) but PLACE them into a declared-order field array by attr_index, then build the struct
// COMPOSITE carrying the struct-def-index so to_jvm rebuilds the exact Rt_StructValue.
// -------------------------------------------------------------------------------------
llvm::Value *lowerStructExpr(EmitContext &ec, const ir::StructExpr &se) {
    const auto *attrs = se.attrs();
    if (attrs == nullptr) return ec.failExpr("StructExpr has null attrs");
    const uint32_t structDefIndex = se.struct_def_index();

    // Look up the struct definition to learn the full attribute count + sizeConstraint gate.
    const auto *structs = ec.app().structs();
    if (structs == nullptr || structDefIndex >= structs->size()) {
        return ec.failExpr("StructExpr struct_def_index out of range");
    }
    const auto *structDef = structs->Get(structDefIndex);
    if (structDef == nullptr) return ec.failExpr("StructExpr struct definition is null");
    const auto *defAttrs = structDef->attributes();
    if (defAttrs == nullptr) return ec.failExpr("struct definition has null attributes");
    const uint32_t n = defAttrs->size();

    // Bit-exactness gate 1: every attribute must be provided (no defaults to materialise).
    if (attrs->size() != n) {
        return ec.failExpr("StructExpr does not set every attribute (defaults) — soft-fail");
    }
    // Bit-exactness gate 2: no sizeConstraint anywhere (its validation can throw).
    for (uint32_t i = 0; i < n; ++i) {
        const auto *da = defAttrs->Get(i);
        if (da == nullptr) return ec.failExpr("struct definition attribute is null");
        if (da->size_constraint() != nullptr) {
            return ec.failExpr("StructExpr attribute has a sizeConstraint — soft-fail");
        }
    }

    // Evaluate attr exprs in SOURCE order, placing each into a declared-order slot by attr_index.
    // A slot map detects duplicate/out-of-range indices (a malformed permutation -> soft-fail).
    std::vector<llvm::Value *> fieldVals(n, nullptr);
    for (flatbuffers::uoffset_t i = 0; i < attrs->size(); ++i) {
        const auto *a = attrs->Get(i);
        if (a == nullptr || a->expr() == nullptr) {
            return ec.failExpr("StructExpr attr or attr expr is null");
        }
        const int32_t idx = a->attr_index();
        if (idx < 0 || static_cast<uint32_t>(idx) >= n) {
            return ec.failExpr("StructExpr attr_index out of range");
        }
        if (fieldVals[static_cast<size_t>(idx)] != nullptr) {
            return ec.failExpr("StructExpr duplicate attr_index");
        }
        llvm::Value *v = lowerExpr(ec, *a->expr());  // SOURCE-order evaluation (observable).
        if (v == nullptr) return nullptr;  // ec.fail set
        fieldVals[static_cast<size_t>(idx)] = v;
    }
    for (uint32_t i = 0; i < n; ++i) {
        if (fieldVals[i] == nullptr) {
            return ec.failExpr("StructExpr left an attribute unset");  // not a full permutation
        }
    }
    return emitMakeComposite(ec, static_cast<int32_t>(structDefIndex), fieldVals);
}

// -------------------------------------------------------------------------------------
// ListLiteralExpr — `[a, b, ...]`.
//
// Evaluate each element expr in SOURCE order (observable: side effects/exceptions must match the
// interpreter's `expr.exprs.map { evaluateExpr(it) }`, rr_interpreter.kt ListLiteral), then build a
// native list LIST carrier. The list TYPE is interned (ec.listTypes()) so the JVM mirror resolves
// the EXACT Rt_ValueClass; the element ORDER is the source order, matching Rt_ListValue.elements.
// The carrier round-trips through to_jvm (Rt_ListValue rebuild) and is field-read by subscript /
// iterated by for. List stdlib ops (== / .add / + / ...) soft-fail (the by-value rell_sysfn_call
// return ABI is not yet sret), routing to the interpreter — but a constructed-and-read list is native.
// -------------------------------------------------------------------------------------
llvm::Value *lowerListLiteralExpr(EmitContext &ec, const ir::ListLiteralExpr &lit) {
    const auto *exprs = lit.exprs();
    if (exprs == nullptr) return ec.failExpr("ListLiteralExpr has null exprs");

    // Intern THIS list-literal's static type BEFORE lowering elements, so the id is claimed in a
    // simple PRE-ORDER position (at the ListLiteral node, before its children). The JVM mirror
    // (Llvm_Backend) walks the same RR body in the identical pre-order — visiting a ListLiteral node
    // and claiming its id before recursing into its element exprs — so the dense id agrees on both
    // sides without a shared key. Interning here (not after the element loop) keeps the walk order
    // independent of how many elements lower successfully (a soft-fail mid-loop cannot desync the id
    // space, because on soft-fail the whole function is discarded and the JVM never uses the ids).
    const int32_t listTypeId = ec.listTypes().intern();

    std::vector<llvm::Value *> elemVals;
    elemVals.reserve(exprs->size());
    for (flatbuffers::uoffset_t i = 0; i < exprs->size(); ++i) {
        const ir::Expr *ee = exprs->Get(i);
        if (ee == nullptr) return ec.failExpr("ListLiteralExpr element expr is null");
        llvm::Value *v = lowerExpr(ec, *ee);
        if (v == nullptr) return nullptr;  // ec.fail set
        elemVals.push_back(v);
    }
    return emitMakeList(ec, listTypeId, elemVals);
}

// -------------------------------------------------------------------------------------
// ListSubscriptExpr — `list[i]`.
//
// Evaluate base (a LIST) then index (an integer), and read the bounds-checked element. The index is
// the i64 payload of an INTEGER value (the interpreter reads `(... as Rt_IntValue).value`). On an
// out-of-bounds index, rell_list_get raises the EXACT interpreter Rt_Exception (code/message via
// Rt_ListValue.checkIndex) and the trampoline aborts — bit-exact, no inline error synthesis. A
// negative index is OOB (no Python-style wrap). Evaluation order (base, then index) matches the
// interpreter's `evaluateExpr(expr.base); evaluateExpr(expr.index)`.
// -------------------------------------------------------------------------------------
llvm::Value *lowerListSubscriptExpr(EmitContext &ec, const ir::ListSubscriptExpr &sub) {
    const ir::Expr *baseE = sub.base();
    const ir::Expr *indexE = sub.index();
    if (baseE == nullptr || indexE == nullptr) {
        return ec.failExpr("ListSubscriptExpr has a null base/index");
    }
    llvm::Value *base = lowerExpr(ec, *baseE);
    if (base == nullptr) return nullptr;
    llvm::Value *indexVal = lowerExpr(ec, *indexE);
    if (indexVal == nullptr) return nullptr;
    llvm::Value *indexI64 = ec.unpackPayloadI64(indexVal);
    return emitListGet(ec, base, indexI64);
}

// -------------------------------------------------------------------------------------
// ByteArraySubscriptExpr — `b[i]` -> integer 0..255 (UNSIGNED).
//
// Evaluate base (a BYTEARRAY) then index (an integer), and read the bounds-checked byte. The index is
// the i64 payload of an INTEGER value (the interpreter reads `(... as Rt_IntValue).value`). On OOB,
// rell_bytearray_get records the EXACT interpreter Rt_Exception (code/message via ByteArraySubscript)
// into the byte-array-error channel and poisons the result; the trampoline aborts. A negative index is
// OOB (no wrap). Evaluation order (base, then index) matches `evaluateExpr(expr.base); ...index`.
// -------------------------------------------------------------------------------------
llvm::Value *lowerByteArraySubscriptExpr(EmitContext &ec, const ir::ByteArraySubscriptExpr &sub) {
    const ir::Expr *baseE = sub.base();
    const ir::Expr *indexE = sub.index();
    if (baseE == nullptr || indexE == nullptr) {
        return ec.failExpr("ByteArraySubscriptExpr has a null base/index");
    }
    llvm::Value *base = lowerExpr(ec, *baseE);
    if (base == nullptr) return nullptr;
    llvm::Value *indexVal = lowerExpr(ec, *indexE);
    if (indexVal == nullptr) return nullptr;
    llvm::Value *indexI64 = ec.unpackPayloadI64(indexVal);
    return emitByteArrayGet(ec, base, indexI64);
}

// -------------------------------------------------------------------------------------
// TextSubscriptExpr — `t[i]` -> a 1-code-unit text.
//
// Evaluate base (a TEXT) then index (an integer), and read the bounds-checked code unit as a length-1
// text. The index is the i64 payload of an INTEGER value (the interpreter reads `(... as Rt_IntValue)
// .value`). On OOB, rell_text_get records the EXACT interpreter Rt_Exception (code/message via
// TextSubscript) into the text-error channel and poisons the result; the trampoline aborts. A negative
// index is OOB (no wrap). Evaluation order (base, then index) matches `evaluateExpr(expr.base); ...index`.
// -------------------------------------------------------------------------------------
llvm::Value *lowerTextSubscriptExpr(EmitContext &ec, const ir::TextSubscriptExpr &sub) {
    const ir::Expr *baseE = sub.base();
    const ir::Expr *indexE = sub.index();
    if (baseE == nullptr || indexE == nullptr) {
        return ec.failExpr("TextSubscriptExpr has a null base/index");
    }
    llvm::Value *base = lowerExpr(ec, *baseE);
    if (base == nullptr) return nullptr;
    llvm::Value *indexVal = lowerExpr(ec, *indexE);
    if (indexVal == nullptr) return nullptr;
    llvm::Value *indexI64 = ec.unpackPayloadI64(indexVal);
    return emitTextGet(ec, base, indexI64);
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
// Shared `when`-chooser helpers, exported for the WhenStatement lowering in lower_stmt.cpp.
//
// WhenStatement (rt_interp_stmt.kt executeWhenStmt) selects an arm INDEX with the SAME chooser
// semantics WhenExpr uses, then runs that statement arm (no result value). To keep the matching
// bit-exact and the inline-key restriction identical (BOOLEAN/INTEGER/ROWID/ENUM canonical
// carriers only; ENUM/text/decimal/etc. keys soft-fail per the REVIEW C1 envelope), lower_stmt
// reuses the exact predicates and equality emission here rather than forking the rules. These are
// thin external-linkage forwarders to the anon-namespace implementations above.
// -------------------------------------------------------------------------------------

bool whenKeyTypeInlineComparable(const ir::Type *type) { return keyTypeInlineComparable(type); }

llvm::Value *emitWhenInlineEq(EmitContext &ec, llvm::Value *a, llvm::Value *b) {
    return emitInlineEq(ec, a, b);
}

const ir::Type *exprStaticResultType(const ir::Expr *expr) { return exprResultType(expr); }

llvm::Value *lowerInlineConstant(EmitContext &ec, const ir::ConstantValue &cv) {
    // ConstantValue carries no type(); pass nullptr — only inline-canonical carriers are accepted,
    // exactly as the LookupWhenChooser key path does (lowerWhenLookup).
    return lowerInlineValue(ec, /*valueType=*/nullptr, cv);
}

// Native composite field read (declared in rell_runtime.h §9). External linkage so lower_call.cpp's
// MemberCalculator StructAttr/TupleAttr cases can emit it. Spills `base` (the already-lowered
// COMPOSITE receiver) into a stack slot, calls the sret-style rell_composite_get(out, &base, i)
// runtime helper, and loads the result. Bit-exact with (base as Rt_StructValue).get(i) /
// Rt_TupleValue.elements[i] (rr_interp_expr.kt).
llvm::Value *emitCompositeGet(EmitContext &ec, llvm::Value *base, int32_t index) {
    llvm::IRBuilder<> &b = ec.builder();
    llvm::StructType *valTy = ec.valueType();
    auto *ptrTy = llvm::PointerType::getUnqual(ec.ctx());

    llvm::Function *fn = b.GetInsertBlock()->getParent();
    llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
    llvm::Value *baseSlot = entryB.CreateAlloca(valTy, nullptr, "comp_base");
    llvm::Value *outSlot = entryB.CreateAlloca(valTy, nullptr, "comp_get_out");
    b.CreateStore(base, baseSlot);

    auto *fnTy = llvm::FunctionType::get(llvm::Type::getVoidTy(ec.ctx()),
                                         {ptrTy, ptrTy, b.getInt32Ty()}, /*isVarArg=*/false);
    llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_composite_get", fnTy);
    b.CreateCall(callee, {outSlot, baseSlot, b.getInt32(index)});
    return b.CreateLoad(valTy, outSlot, "comp_field");
}

// Native list element read (declared in rell_runtime.h §9). External linkage so lower_stmt.cpp's
// for-over-list lowering can reuse it. Spills `base` (the already-lowered LIST receiver) into a stack
// slot, calls the sret-style rell_list_get(out, ctx, &base, indexI64) runtime helper (which
// bounds-checks and raises the exact interpreter list:index Rt_Exception on OOB), and loads the
// result. Bit-exact with the interpreter's Rt_ListValue.checkIndex + elements[idx] (rr_interpreter.kt).
llvm::Value *emitListGet(EmitContext &ec, llvm::Value *base, llvm::Value *indexI64) {
    llvm::Value *ctx = currentCtxArgExpr(ec);
    if (ctx == nullptr) return ec.failExpr("emitListGet: no enclosing function ctx arg");

    llvm::IRBuilder<> &b = ec.builder();
    llvm::StructType *valTy = ec.valueType();
    auto *ptrTy = llvm::PointerType::getUnqual(ec.ctx());

    llvm::Function *fn = b.GetInsertBlock()->getParent();
    llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
    llvm::Value *baseSlot = entryB.CreateAlloca(valTy, nullptr, "list_base");
    llvm::Value *outSlot = entryB.CreateAlloca(valTy, nullptr, "list_get_out");
    b.CreateStore(base, baseSlot);

    // void rell_list_get(ptr out, ptr ctx, ptr list, i64 index)
    auto *fnTy = llvm::FunctionType::get(llvm::Type::getVoidTy(ec.ctx()),
                                         {ptrTy, ptrTy, ptrTy, b.getInt64Ty()}, /*isVarArg=*/false);
    llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_list_get", fnTy);
    b.CreateCall(callee, {outSlot, ctx, baseSlot, indexI64});
    return b.CreateLoad(valTy, outSlot, "list_elem");
}

// Native byte_array subscript read (declared in rell_runtime.h §9). Spills `base` (the already-lowered
// BYTEARRAY receiver), calls the sret-style rell_bytearray_get(out, &base, indexI64) helper (which
// bounds-checks and, on OOB, records the exact interpreter expr_bytearray_subscript_index Rt_Exception
// into the byte-array-error channel), and loads the byte as an INTEGER runtime value in [0,255]
// (UNSIGNED). Bit-exact with rr_interpreter.kt ByteArraySubscript.
llvm::Value *emitByteArrayGet(EmitContext &ec, llvm::Value *base, llvm::Value *indexI64) {
    llvm::IRBuilder<> &b = ec.builder();
    llvm::StructType *valTy = ec.valueType();
    auto *ptrTy = llvm::PointerType::getUnqual(ec.ctx());

    llvm::Function *fn = b.GetInsertBlock()->getParent();
    llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
    llvm::Value *baseSlot = entryB.CreateAlloca(valTy, nullptr, "ba_base");
    llvm::Value *outSlot = entryB.CreateAlloca(valTy, nullptr, "ba_get_out");
    b.CreateStore(base, baseSlot);

    // void rell_bytearray_get(ptr out, ptr ba, i64 index)
    auto *fnTy = llvm::FunctionType::get(llvm::Type::getVoidTy(ec.ctx()),
                                         {ptrTy, ptrTy, b.getInt64Ty()}, /*isVarArg=*/false);
    llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_bytearray_get", fnTy);
    b.CreateCall(callee, {outSlot, baseSlot, indexI64});
    return b.CreateLoad(valTy, outSlot, "ba_byte");
}

// Native byte_array .size() read (declared in rell_runtime.h §9). Spills `base` (the already-lowered
// BYTEARRAY receiver), calls the sret-style rell_bytearray_size(out, &base) helper, and loads the
// length as an INTEGER runtime value. Bit-exact with the interpreter's byte_array.size().
llvm::Value *emitByteArraySize(EmitContext &ec, llvm::Value *base) {
    llvm::IRBuilder<> &b = ec.builder();
    llvm::StructType *valTy = ec.valueType();
    auto *ptrTy = llvm::PointerType::getUnqual(ec.ctx());

    llvm::Function *fn = b.GetInsertBlock()->getParent();
    llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
    llvm::Value *baseSlot = entryB.CreateAlloca(valTy, nullptr, "ba_size_base");
    llvm::Value *outSlot = entryB.CreateAlloca(valTy, nullptr, "ba_size_out");
    b.CreateStore(base, baseSlot);

    // void rell_bytearray_size(ptr out, ptr ba)
    auto *fnTy = llvm::FunctionType::get(llvm::Type::getVoidTy(ec.ctx()), {ptrTy, ptrTy},
                                         /*isVarArg=*/false);
    llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_bytearray_size", fnTy);
    b.CreateCall(callee, {outSlot, baseSlot});
    return b.CreateLoad(valTy, outSlot, "ba_size");
}

// Native text subscript read (declared in rell_runtime.h §9). Spills `base` (the already-lowered TEXT
// receiver), calls the sret-style rell_text_get(out, ctx, &base, indexI64) helper (which bounds-checks
// and, on OOB, records the exact interpreter expr_text_subscript_index Rt_Exception into the text-error
// channel and allocates the 1-unit result in the arena), and loads the 1-code-unit TEXT runtime value.
// Bit-exact with rr_interpreter.kt TextSubscript (Rt_TextValue.get(text[idx].toString())).
llvm::Value *emitTextGet(EmitContext &ec, llvm::Value *base, llvm::Value *indexI64) {
    llvm::Value *ctx = currentCtxArgExpr(ec);
    if (ctx == nullptr) return ec.failExpr("emitTextGet: no enclosing function ctx arg");

    llvm::IRBuilder<> &b = ec.builder();
    llvm::StructType *valTy = ec.valueType();
    auto *ptrTy = llvm::PointerType::getUnqual(ec.ctx());

    llvm::Function *fn = b.GetInsertBlock()->getParent();
    llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
    llvm::Value *baseSlot = entryB.CreateAlloca(valTy, nullptr, "txt_base");
    llvm::Value *outSlot = entryB.CreateAlloca(valTy, nullptr, "txt_get_out");
    b.CreateStore(base, baseSlot);

    // void rell_text_get(ptr out, ptr ctx, ptr text, i64 index)
    auto *fnTy = llvm::FunctionType::get(llvm::Type::getVoidTy(ec.ctx()),
                                         {ptrTy, ptrTy, ptrTy, b.getInt64Ty()}, /*isVarArg=*/false);
    llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_text_get", fnTy);
    b.CreateCall(callee, {outSlot, ctx, baseSlot, indexI64});
    return b.CreateLoad(valTy, outSlot, "txt_char");
}

// Native text .size() read (declared in rell_runtime.h §9). Spills `base` (the already-lowered TEXT
// receiver), calls the sret-style rell_text_size(out, &base) helper, and loads the length (UTF-16 code
// units) as an INTEGER runtime value. Bit-exact with the interpreter's text.size() (String.length).
llvm::Value *emitTextSize(EmitContext &ec, llvm::Value *base) {
    llvm::IRBuilder<> &b = ec.builder();
    llvm::StructType *valTy = ec.valueType();
    auto *ptrTy = llvm::PointerType::getUnqual(ec.ctx());

    llvm::Function *fn = b.GetInsertBlock()->getParent();
    llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
    llvm::Value *baseSlot = entryB.CreateAlloca(valTy, nullptr, "txt_size_base");
    llvm::Value *outSlot = entryB.CreateAlloca(valTy, nullptr, "txt_size_out");
    b.CreateStore(base, baseSlot);

    // void rell_text_size(ptr out, ptr text)
    auto *fnTy = llvm::FunctionType::get(llvm::Type::getVoidTy(ec.ctx()), {ptrTy, ptrTy},
                                         /*isVarArg=*/false);
    llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_text_size", fnTy);
    b.CreateCall(callee, {outSlot, baseSlot});
    return b.CreateLoad(valTy, outSlot, "txt_size");
}

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

        // ---- in-memory composites (native; the first heap-value lowering) ----
        case ir::ExprUnion_TupleExpr:
            return lowerTupleExpr(ec, *expr.expr_as_TupleExpr());
        case ir::ExprUnion_StructExpr:
            return lowerStructExpr(ec, *expr.expr_as_StructExpr());

        // ---- native lists (arena-owned LIST carrier) ----
        case ir::ExprUnion_ListLiteralExpr:
            return lowerListLiteralExpr(ec, *expr.expr_as_ListLiteralExpr());
        case ir::ExprUnion_ListSubscriptExpr:
            return lowerListSubscriptExpr(ec, *expr.expr_as_ListSubscriptExpr());

        // ---- native byte_array (arena-owned BYTEARRAY carrier) ----
        case ir::ExprUnion_ByteArraySubscriptExpr:
            return lowerByteArraySubscriptExpr(ec, *expr.expr_as_ByteArraySubscriptExpr());

        // ---- native text (arena-owned TEXT carrier) ----
        case ir::ExprUnion_TextSubscriptExpr:
            return lowerTextSubscriptExpr(ec, *expr.expr_as_TextSubscriptExpr());

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
