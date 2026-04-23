// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_sql_expr.cpp — namespace rell::sql: the per-variant DbExprUnion dispatch of the native
// SQL string generator. This file implements SqlGen::db_expr_to_sql and the 16 per-variant
// helpers declared in rell_sql.h, producing raw $1..$N parameterized PostgreSQL whose TEXT and
// BIND ORDER are byte-for-byte faithful to the JVM interpreter's DbSqlGen.dbExprToField
// (runtime-interpreter/src/runtime/rr_interp_sql_gen.kt). NO jOOQ DSL: every fragment is built
// directly as a C++ string, with the Nth bind rendered as "$N" at the point DbSqlGen.bind()
// would append it.
//
// PORT MAP (Kotlin → C++):
//   DbSqlGen.dbExprToField           → SqlGen::db_expr_to_sql (the when-dispatch)
//   binaryToField                    → SqlGen::db_binary
//   inField / inCollectionField      → SqlGen::db_in / db_in_collection
//   elvisField                       → SqlGen::db_elvis
//   callField (+ deserializeDbSysFn) → SqlGen::db_call
//   existsField                      → SqlGen::db_exists
//   whenField/unkeyed/keyed*         → SqlGen::db_when
//   subQueryField                    → SqlGen::db_sub_query (recursive SELECT, sibling file owns
//                                      the shared SELECT-body renderer; this file delegates)
//   tupleField/boolField/NULL_FIELD  → tuple_field / bool_lit / "NULL"
//
// DEPENDENCY RULES: includes ONLY rell_runtime.h + app_generated.h (via rell_sql.h) — NO libpq —
// so this TU is unit-testable against golden-string vectors with no database.
//
// ============================================================================================
// SHARED STATE (B2): all four entity-model / rel-join / evaluation needs of the DbExpr dispatch
// are now served by ONE coherent model in rell_sql.h:
//   (1) resolveEntityAlias(entityId)            → SqlGen::resolve_entity_alias (structural state)
//   (2) resolveTableExpr over Entity / Rel      → SqlGen::resolve_table_expr   (structural state)
//   (3) entityDef.strAttributes[name].sqlMapping → SqlContext::attr_sql_mapping
//   (4) InterpretedEvaluator + R-level folding  → SqlContext::eval_* / try_evaluate_fully / ...
// The alias map + rel-join registry are SqlGen members shared by this dispatch, the SELECT FROM
// renderer (rell_sql_select.cpp), and the write paths (rell_sql_write.cpp) — one SqlGen instance,
// one registry. The old dynamic_cast<SqlExprEnv> hack and the extern render_sub_query_body are
// gone; db_sub_query is defined once, in rell_sql_select.cpp.

#include "rell_sql.h"

#include <cstdint>
#include <string>
#include <utility>
#include <vector>

namespace rell::sql {

using rell::llvm_rt::RellTag;
using rell::llvm_rt::rv_none;

namespace {

// isDecimalType(type): (type as? RR_Type.Primitive)?.kind == DECIMAL. Operates directly on the
// FlatBuffer Type — no JVM needed.
bool is_decimal_type(const ir::Type *type) {
    if (type == nullptr) return false;
    if (type->type_type() != ir::TypeUnion_PrimitiveType) return false;
    const ir::PrimitiveType *p = type->type_as_PrimitiveType();
    return p != nullptr && p->kind() == ir::PrimitiveTypeKind_DECIMAL;
}

// boolField(b) → the literal "TRUE"/"FALSE" (DbSqlGen.boolField / TRUE_FIELD / FALSE_FIELD).
inline const char *bool_lit(bool b) { return b ? "TRUE" : "FALSE"; }

// tupleField(items) → "(i0,i1,...)" with a BARE comma joiner (no space), "()" when empty.
// Mirrors DbSqlGen.tupleField exactly (joinToString(",")).
std::string tuple_field(const std::vector<std::string> &items) {
    if (items.empty()) return "()";
    std::string s = "(";
    for (size_t i = 0; i < items.size(); ++i) {
        if (i != 0) s += ",";
        s += items[i];
    }
    s += ")";
    return s;
}

// A RellValue is the Rell `null` singleton (Rt_NullValue) iff tagged NULL_.
inline bool is_rt_null(const RellValue &v) { return v.tag == RellTag::NULL_; }

// rv_none() means "not an R-level constant" in the fold helpers below.
inline bool is_none(const RellValue &v) { return v.tag == RellTag::NONE; }

}  // namespace

// =====================================================================================
// Low-level emit helpers (declared in rell_sql.h).
// =====================================================================================

std::string SqlGen::bind(const RellValue &v) {
    binds_.push_back(v);
    // jOOQ's Nth '?' → libpq positional "$N", N == 1-based index of the just-pushed value.
    return "$" + std::to_string(binds_.size());
}

std::string SqlGen::quote_ident(const std::string &name) {
    // jOOQ DSL.name with EXPLICIT_DEFAULT_QUOTED: surround with '"', double any embedded '"'.
    std::string out;
    out.reserve(name.size() + 2);
    out += '"';
    for (char c : name) {
        if (c == '"') out += '"';
        out += c;
    }
    out += '"';
    return out;
}

std::string SqlGen::column_ref(const std::string &alias, const std::string &col) {
    // "<alias>.\"<col>\"" — alias bare (SAFE_ALIAS_REGEX), column quoted. Mirrors columnField.
    return alias + "." + quote_ident(col);
}

std::string SqlGen::wrap_decimal(const std::string &inner) {
    // "ROUND(<inner>, 20)" — Lib_DecimalMath.DECIMAL_FRAC_DIGITS == 20.
    return "ROUND(" + inner + ", 20)";
}

// =====================================================================================
// Operator string tables (port of RROpDeserializer.FB_TO_DB_*_OP — the enum→SQL-string maps
// DbSqlGen consumes as `expr.op`). These are the EXACT strings binaryToField/unaryToField branch
// on, so the string identity matters (e.g. "AND"/"OR" short-circuit, "IS NOT DISTINCT FROM" eq).
// =====================================================================================

const char *SqlGen::db_binary_op_sql(ir::DbBinaryOp op) {
    switch (op) {
        case ir::DbBinaryOp_LT: return "<";
        case ir::DbBinaryOp_GT: return ">";
        case ir::DbBinaryOp_LE: return "<=";
        case ir::DbBinaryOp_GE: return ">=";
        case ir::DbBinaryOp_AND: return "AND";
        case ir::DbBinaryOp_OR: return "OR";
        case ir::DbBinaryOp_ADD_INTEGER:
        case ir::DbBinaryOp_ADD_BIG_INTEGER:
        case ir::DbBinaryOp_ADD_DECIMAL: return "+";
        case ir::DbBinaryOp_SUB_INTEGER:
        case ir::DbBinaryOp_SUB_BIG_INTEGER:
        case ir::DbBinaryOp_SUB_DECIMAL: return "-";
        case ir::DbBinaryOp_MUL_INTEGER:
        case ir::DbBinaryOp_MUL_BIG_INTEGER:
        case ir::DbBinaryOp_MUL_DECIMAL: return "*";
        case ir::DbBinaryOp_DIV_INTEGER:
        case ir::DbBinaryOp_DIV_BIG_INTEGER:
        case ir::DbBinaryOp_DIV_DECIMAL: return "/";
        case ir::DbBinaryOp_MOD_INTEGER:
        case ir::DbBinaryOp_MOD_BIG_INTEGER:
        case ir::DbBinaryOp_MOD_DECIMAL: return "%";
        case ir::DbBinaryOp_CONCAT: return "||";
        case ir::DbBinaryOp_IN: return "IN";
        case ir::DbBinaryOp_NOT_IN: return "NOT IN";
        case ir::DbBinaryOp_EQ: return "=";
        case ir::DbBinaryOp_NE: return "<>";
        case ir::DbBinaryOp_EQ_NULL: return "IS NOT DISTINCT FROM";
        case ir::DbBinaryOp_NE_NULL: return "IS DISTINCT FROM";
    }
    // FIDELITY: op.fbs defines no `FN:`-prefixed DbBinaryOp; the binaryToField `FN:` branch is
    // therefore unreachable from the enum set today (verify against
    // RROpDeserializer.FB_TO_DB_BINARY_OP). If a future op renders as a function call, it must map
    // here to "FN:<name>" and db_binary's startsWith("FN:") branch picks it up.
    return "?";  // unreachable
}

const char *SqlGen::db_unary_op_sql(ir::DbUnaryOp op) {
    switch (op) {
        case ir::DbUnaryOp_MINUS_INTEGER:
        case ir::DbUnaryOp_MINUS_BIG_INTEGER:
        case ir::DbUnaryOp_MINUS_DECIMAL: return "-";
        case ir::DbUnaryOp_NOT: return "NOT";
    }
    return "?";  // unreachable
}

// =====================================================================================
// db_expr_to_sql — the central when-dispatch (DbSqlGen.dbExprToField).
// =====================================================================================

std::string SqlGen::db_expr_to_sql(const ir::DbExpr &expr, int64_t frame_handle, bool enclose) {
    switch (expr.expr_type()) {
        case ir::DbExprUnion_DbInterpretedExpr:
            return db_interpreted(*expr.expr_as_DbInterpretedExpr(), frame_handle);
        case ir::DbExprUnion_DbBinaryExpr:
            return db_binary(*expr.expr_as_DbBinaryExpr(), frame_handle, enclose);
        case ir::DbExprUnion_DbUnaryExpr:
            return db_unary(*expr.expr_as_DbUnaryExpr(), frame_handle, enclose);
        case ir::DbExprUnion_DbEntityExpr:
            return db_entity(*expr.expr_as_DbEntityExpr());
        case ir::DbExprUnion_DbAttrExpr:
            return db_attr(*expr.expr_as_DbAttrExpr());
        case ir::DbExprUnion_DbRelExpr:
            return db_rel(*expr.expr_as_DbRelExpr());
        case ir::DbExprUnion_DbRowidExpr:
            return db_rowid(*expr.expr_as_DbRowidExpr());
        case ir::DbExprUnion_DbCollectionInterpretedExpr:
            return db_collection_interpreted(*expr.expr_as_DbCollectionInterpretedExpr(), frame_handle);
        case ir::DbExprUnion_DbInExpr:
            return db_in(*expr.expr_as_DbInExpr(), frame_handle);
        case ir::DbExprUnion_DbElvisExpr:
            return db_elvis(*expr.expr_as_DbElvisExpr(), frame_handle);
        case ir::DbExprUnion_DbCallExpr:
            return db_call(*expr.expr_as_DbCallExpr(), frame_handle);
        case ir::DbExprUnion_DbExistsExpr:
            return db_exists(*expr.expr_as_DbExistsExpr(), frame_handle);
        case ir::DbExprUnion_DbInCollectionExpr:
            return db_in_collection(*expr.expr_as_DbInCollectionExpr(), frame_handle);
        case ir::DbExprUnion_DbWhenExpr:
            return db_when(*expr.expr_as_DbWhenExpr(), frame_handle, enclose);
        case ir::DbExprUnion_DbNestedAtExpr:
            return db_nested_at(*expr.expr_as_DbNestedAtExpr(), frame_handle, enclose);
        case ir::DbExprUnion_DbSubQueryExpr:
            return db_sub_query(*expr.expr_as_DbSubQueryExpr(), frame_handle);
        case ir::DbExprUnion_NONE:
            break;
    }
    // Unreachable: DbExprUnion is required and exhaustive.
    return std::string();
}

// =====================================================================================
// DbInterpretedExpr — evaluate the R leaf; NULL → literal "NULL" (no bind), else bind → "$N".
// (DbSqlGen: `if (value == Rt_NullValue) NULL_FIELD else bind(value)`.)
// =====================================================================================

std::string SqlGen::db_interpreted(const ir::DbInterpretedExpr &e, int64_t frame_handle) {
    RellValue v = ctx_.eval_interpreted(*e.expr(), frame_handle);
    if (is_rt_null(v)) return "NULL";
    return bind(v);
}

// =====================================================================================
// DbBinaryExpr — binaryToField. AND/OR short-circuit, nullable-eq collapse, FN: prefix, default.
// =====================================================================================

std::string SqlGen::db_binary(const ir::DbBinaryExpr &e, int64_t frame_handle, bool enclose) {
    const std::string sql = db_binary_op_sql(e.op());

    // --- AND/OR short-circuiting on R-level constants (Db_BinaryOp_AndOr.toRedExpr) ---
    if (sql == "AND" || sql == "OR") {
        const bool shortVal = (sql == "OR");
        // tryEvaluateInterpretedBool(left)
        RellValue lc = ctx_.try_evaluate_fully(*e.left(), frame_handle);
        if (!is_none(lc) && lc.tag == RellTag::BOOLEAN) {
            bool lcb = lc.payload.i64 != 0;
            if (lcb == shortVal) return bool_lit(shortVal);
            return db_expr_to_sql(*e.right(), frame_handle, enclose);
        }
        RellValue rc = ctx_.try_evaluate_fully(*e.right(), frame_handle);
        if (!is_none(rc) && rc.tag == RellTag::BOOLEAN) {
            bool rcb = rc.payload.i64 != 0;
            if (rcb == shortVal) return bool_lit(shortVal);
            return db_expr_to_sql(*e.left(), frame_handle, enclose);
        }
    }

    const bool wrapDec = is_decimal_type(e.type());

    auto opTpl = [&](const std::string &l, const std::string &r) -> std::string {
        return enclose ? ("(" + l + " " + sql + " " + r + ")") : (l + " " + sql + " " + r);
    };

    // --- Nullable equality with one known-NULL side collapses to IS NULL / IS NOT NULL ---
    if (e.nullable_eq()) {
        const bool isEqual = (e.op() == ir::DbBinaryOp_EQ_NULL);  // "IS NOT DISTINCT FROM"
        const ir::DbExpr *left = e.left();
        const ir::DbExpr *right = e.right();

        // Pre-evaluate Interpreted operands through the reduced cache (the subsequent re-emit
        // reuses the value). leftConst/rightConst are rv_none() unless the operand is a fully
        // R-level Interpreted leaf.
        RellValue leftConst = rv_none();
        RellValue rightConst = rv_none();
        if (left->expr_type() == ir::DbExprUnion_DbInterpretedExpr) {
            leftConst = ctx_.eval_interpreted(*left->expr_as_DbInterpretedExpr()->expr(), frame_handle);
        }
        if (right->expr_type() == ir::DbExprUnion_DbInterpretedExpr) {
            rightConst = ctx_.eval_interpreted(*right->expr_as_DbInterpretedExpr()->expr(), frame_handle);
        }

        const char *nullCheckOp = isEqual ? "IS NULL" : "IS NOT NULL";
        auto nullTpl = [&](const std::string &x) -> std::string {
            return enclose ? ("(" + x + " " + nullCheckOp + ")") : (x + " " + nullCheckOp);
        };

        std::string result;
        if (!is_none(rightConst) && is_rt_null(rightConst)) {
            result = nullTpl(db_expr_to_sql(*left, frame_handle, true));
        } else if (!is_none(leftConst) && is_rt_null(leftConst)) {
            result = nullTpl(db_expr_to_sql(*right, frame_handle, true));
        } else if (!is_none(rightConst)) {
            // opTpl(left, bind(rightConst)) — left rendered first (bind order preserved).
            std::string l = db_expr_to_sql(*left, frame_handle, true);
            std::string r = bind(rightConst);
            result = opTpl(l, r);
        } else if (!is_none(leftConst)) {
            std::string l = db_expr_to_sql(*right, frame_handle, true);
            std::string r = bind(leftConst);
            result = opTpl(l, r);
        } else {
            std::string l = db_expr_to_sql(*left, frame_handle, true);
            std::string r = db_expr_to_sql(*right, frame_handle, true);
            result = opTpl(l, r);
        }
        return wrapDec ? wrap_decimal(result) : result;
    }

    // --- FN: prefix → "fn({L}, {R})" with operands rendered enclose=false ---
    if (sql.rfind("FN:", 0) == 0) {
        const std::string fn = sql.substr(3);
        std::string l = db_expr_to_sql(*e.left(), frame_handle, false);
        std::string r = db_expr_to_sql(*e.right(), frame_handle, false);
        std::string inner = fn + "(" + l + ", " + r + ")";
        return wrapDec ? wrap_decimal(inner) : inner;
    }

    // --- default binary: opTpl with both operands enclose=true ---
    std::string l = db_expr_to_sql(*e.left(), frame_handle, true);
    std::string r = db_expr_to_sql(*e.right(), frame_handle, true);
    std::string inner = opTpl(l, r);
    return wrapDec ? wrap_decimal(inner) : inner;
}

// =====================================================================================
// DbUnaryExpr — "OP {x}" / "(OP {x})" (single space). OP from db_unary_op_sql.
// =====================================================================================

std::string SqlGen::db_unary(const ir::DbUnaryExpr &e, int64_t frame_handle, bool enclose) {
    std::string operand = db_expr_to_sql(*e.expr(), frame_handle, true);
    const char *op = db_unary_op_sql(e.op());
    if (enclose) return std::string("(") + op + " " + operand + ")";
    return std::string(op) + " " + operand;
}

// =====================================================================================
// DbEntityExpr — the entity's rowid column → "<alias>.\"<rowidCol>\"".
// =====================================================================================

std::string SqlGen::db_entity(const ir::DbEntityExpr &e) {
    uint32_t defIdx = 0;
    std::string alias = resolve_entity_alias(e.entity_id(), defIdx);
    return column_ref(alias, ctx_.rowid_column(defIdx));
}

// resolveTableExpr (Entity or Rel base → alias + def_index, registering a lazy rel-JOIN for a Rel
// base) is SqlGen::resolve_table_expr (rell_sql_select.cpp) — the SAME registry the SELECT FROM
// clause reads. db_attr / db_rel / db_rowid all route through it (C1/C2).

// =====================================================================================
// DbAttrExpr — "<alias>.\"<sqlCol>\"" for base entity's attribute; ROUND-wrapped if DECIMAL.
// =====================================================================================

std::string SqlGen::db_attr(const ir::DbAttrExpr &e) {
    std::pair<std::string, uint32_t> ref = resolve_table_expr(*e.base());
    std::string sqlCol = ctx_.attr_sql_mapping(ref.second, e.attr_name()->str());
    std::string col = column_ref(ref.first, sqlCol);
    return is_decimal_type(e.type()) ? wrap_decimal(col) : col;
}

// =====================================================================================
// DbRelExpr — FK column on the BASE table → "<baseAlias>.\"<fkCol>\"" (no JOIN forced).
// =====================================================================================

std::string SqlGen::db_rel(const ir::DbRelExpr &e) {
    std::pair<std::string, uint32_t> baseRef = resolve_table_expr(*e.base());
    std::string sqlCol = ctx_.attr_sql_mapping(baseRef.second, e.attr_name()->str());
    // Note: no wrapDecimal — DbSqlGen's Rel branch emits the bare FK column.
    return column_ref(baseRef.first, sqlCol);
}

// =====================================================================================
// DbRowidExpr — Rowid(Rel(base, attr, target)) reads the FK column from the base directly (no
// JOIN); otherwise the resolved table's rowid column.
// =====================================================================================

std::string SqlGen::db_rowid(const ir::DbRowidExpr &e) {
    const ir::DbExpr *relBase = e.base();
    if (relBase->expr_type() == ir::DbExprUnion_DbRelExpr) {
        // Optimization: read the FK column from the rel's base table without forcing the JOIN.
        const ir::DbRelExpr *rel = relBase->expr_as_DbRelExpr();
        std::pair<std::string, uint32_t> baseRef = resolve_table_expr(*rel->base());
        std::string sqlCol = ctx_.attr_sql_mapping(baseRef.second, rel->attr_name()->str());
        return column_ref(baseRef.first, sqlCol);
    }
    std::pair<std::string, uint32_t> ref = resolve_table_expr(*relBase);
    return column_ref(ref.first, ctx_.rowid_column(ref.second));
}

// =====================================================================================
// DbCollectionInterpretedExpr — evaluate the R collection, bind each element, "(a,b,...)".
// =====================================================================================

std::string SqlGen::db_collection_interpreted(const ir::DbCollectionInterpretedExpr &e,
                                               int64_t frame_handle) {
    std::vector<RellValue> elems;
    ctx_.eval_collection(*e.expr(), frame_handle, elems);
    std::vector<std::string> items;
    items.reserve(elems.size());
    for (const RellValue &v : elems) items.push_back(bind(v));
    return tuple_field(items);
}

// =====================================================================================
// DbInExpr — "{key} IN (tuple)" / "{key} NOT IN (tuple)"; empty static list → boolean literal.
// =====================================================================================

std::string SqlGen::db_in(const ir::DbInExpr &e, int64_t frame_handle) {
    // PostgreSQL rejects `x IN ()`; an empty static list is unambiguously the boolean of not_().
    if (e.exprs() == nullptr || e.exprs()->size() == 0) return bool_lit(e.not_());

    std::string key = db_expr_to_sql(*e.key_expr(), frame_handle, false);
    std::vector<std::string> items;
    items.reserve(e.exprs()->size());
    for (uint32_t i = 0; i < e.exprs()->size(); ++i) {
        items.push_back(db_expr_to_sql(*e.exprs()->Get(i), frame_handle, false));
    }
    std::string tuple = tuple_field(items);
    const char *op = e.not_() ? "NOT IN" : "IN";
    return key + " " + op + " " + tuple;
}

// =====================================================================================
// DbInCollectionExpr — evaluate the R collection on the right; "{left} IN (binds...)" / NOT IN;
// empty collection → boolean literal.
// =====================================================================================

std::string SqlGen::db_in_collection(const ir::DbInCollectionExpr &e, int64_t frame_handle) {
    std::vector<RellValue> elems;
    ctx_.eval_collection(*e.right(), frame_handle, elems);
    if (elems.empty()) return bool_lit(e.not_());

    std::string left = db_expr_to_sql(*e.left(), frame_handle, false);
    std::vector<std::string> items;
    items.reserve(elems.size());
    for (const RellValue &v : elems) items.push_back(bind(v));
    std::string tuple = tuple_field(items);
    const char *op = e.not_() ? "NOT IN" : "IN";
    return left + " " + op + " " + tuple;
}

// =====================================================================================
// DbElvisExpr — "COALESCE({L}, {R})"; L R-const NULL → {R}; L R-const non-NULL → {L}.
// =====================================================================================

std::string SqlGen::db_elvis(const ir::DbElvisExpr &e, int64_t frame_handle) {
    // Pre-reduce so R-level Interpreted leaves under left are evaluated once (cached for re-emit).
    ctx_.reduce_db_expr(*e.left(), frame_handle);
    RellValue leftConst = ctx_.try_evaluate_fully(*e.left(), frame_handle);
    if (!is_none(leftConst)) {
        if (is_rt_null(leftConst)) return db_expr_to_sql(*e.right(), frame_handle, false);
        return db_expr_to_sql(*e.left(), frame_handle, false);
    }
    std::string l = db_expr_to_sql(*e.left(), frame_handle, false);
    std::string r = db_expr_to_sql(*e.right(), frame_handle, false);
    return "COALESCE(" + l + ", " + r + ")";
}

// =====================================================================================
// DbCallExpr — Db_SysFunction. Simple → "fn(a0, a1, ...)"; Template → fragments with inline args.
// Wrap in ROUND(...,20) if the call's type is DECIMAL. (callField + deserializeDbSysFn.)
// =====================================================================================

std::string SqlGen::db_call(const ir::DbCallExpr &e, int64_t frame_handle) {
    const std::string encoded = e.fn_name()->str();
    const flatbuffers::Vector<flatbuffers::Offset<ir::DbExpr>> *args = e.args();

    std::string inner;
    // Template encoding: "text#{i}text" — a '#{i}' fragment substitutes the i-th rendered arg
    // inline at that position. Simple form has no '#{' and is the raw SQL function name.
    // FIDELITY: confirm the exact serialized Template encoding against the JVM serializer /
    // deserializeDbSysFn. We mirror the documented `text#{i}text` form (ir.fbs line 581).
    if (encoded.find("#{") != std::string::npos) {
        // Walk the template, emitting literal text and substituting "#{i}" with arg[i] rendered
        // enclose=false (DbSqlGen renders Template args via dbExprToField(args[idx], frame)).
        std::string out;
        size_t i = 0;
        const size_t n = encoded.size();
        while (i < n) {
            if (i + 1 < n && encoded[i] == '#' && encoded[i + 1] == '{') {
                size_t j = i + 2;
                std::string digits;
                while (j < n && encoded[j] >= '0' && encoded[j] <= '9') {
                    digits += encoded[j];
                    ++j;
                }
                if (j < n && encoded[j] == '}' && !digits.empty()) {
                    uint32_t idx = static_cast<uint32_t>(std::stoul(digits));
                    out += db_expr_to_sql(*args->Get(idx), frame_handle, false);
                    i = j + 1;
                    continue;
                }
            }
            out += encoded[i];
            ++i;
        }
        inner = out;
    } else {
        // Simple: "<fn_name>(arg0, arg1, ...)" with args rendered enclose=false, joined ", ".
        std::string out = encoded + "(";
        if (args != nullptr) {
            for (uint32_t k = 0; k < args->size(); ++k) {
                if (k != 0) out += ", ";
                out += db_expr_to_sql(*args->Get(k), frame_handle, false);
            }
        }
        out += ")";
        inner = out;
    }

    return is_decimal_type(e.type()) ? wrap_decimal(inner) : inner;
}

// =====================================================================================
// DbExistsExpr — "EXISTS <subquery>" / "NOT EXISTS <subquery>"; Interpreted → TRUE/FALSE literal;
// else "EXISTS({x})".
// =====================================================================================

std::string SqlGen::db_exists(const ir::DbExistsExpr &e, int64_t frame_handle) {
    const ir::DbExpr *sub = e.sub_expr();
    if (sub->expr_type() == ir::DbExprUnion_DbSubQueryExpr) {
        std::string subSql = db_sub_query(*sub->expr_as_DbSubQueryExpr(), frame_handle);
        const char *op = e.not_() ? "NOT EXISTS" : "EXISTS";
        // subQueryParameterizedSql already wraps the body in "(...)"; "$op {0}" → "EXISTS (...)".
        return std::string(op) + " " + subSql;
    }
    if (sub->expr_type() == ir::DbExprUnion_DbInterpretedExpr) {
        // Non-subquery (e.g. collection at). Evaluate at R-level → literal TRUE/FALSE.
        // FIDELITY (C5): evaluate the R leaf EXACTLY ONCE (DbSqlGen does one evaluateExpr, then
        // `=== Rt_NullValue ? false : (value as Rt_CollectionValue).isNotEmpty`). A second
        // evaluation would re-run side-effecting/impure R code — a consensus divergence. Emptiness
        // is derived from the single evaluated value via collection_is_empty, NOT a re-eval.
        RellValue v = ctx_.eval_interpreted(*sub->expr_as_DbInterpretedExpr()->expr(), frame_handle);
        const bool exists = is_rt_null(v) ? false : !ctx_.collection_is_empty(v);
        bool out = e.not_() ? !exists : exists;
        return bool_lit(out);
    }
    // Fallback: "EXISTS({x})" / "NOT EXISTS({x})" — note NO space before '(' (DbSqlGen "$op({0})").
    std::string subField = db_expr_to_sql(*sub, frame_handle, false);
    const char *op = e.not_() ? "NOT EXISTS" : "EXISTS";
    return std::string(op) + "(" + subField + ")";
}

// =====================================================================================
// DbWhenExpr — CASE. Keyed (key_expr present) vs unkeyed; R-level-constant key/cond folding
// mirrors whenField / unkeyedWhen / keyedWhenWithRConst / keyedWhenWithDbKey exactly.
// =====================================================================================

namespace {

// OR-combine a list of already-rendered boolean condition fragments the way jOOQ renders a
// `c0.or(c1).or(c2)` Condition chain: a single flat group "(c0 OR c1 OR ...)" with the keyword
// uppercased (JOOQ_CTX RenderKeywordCase.UPPER). A single condition renders bare (jOOQ does not
// parenthesize a lone Condition). An empty list should never occur here.
// FIDELITY: jOOQ flattens a left-associative .or() chain into one parenthesized group; confirm the
// exact parenthesization against rendered jOOQ output if a regression surfaces.
std::string or_combine(const std::vector<std::string> &conds) {
    if (conds.empty()) return std::string();  // never occurs (Kotlin reduce would throw)
    if (conds.size() == 1) return conds[0];
    std::string out = "(";
    for (size_t i = 0; i < conds.size(); ++i) {
        if (i != 0) out += " OR ";
        out += conds[i];
    }
    out += ")";
    return out;
}

// "CASE WHEN c THEN t ... [ELSE e] END" assembled from prepared (cond, then) parts + optional else.
std::string assemble_case(const std::vector<std::pair<std::string, std::string>> &parts,
                          const std::string *else_sql) {
    std::string out = "CASE";
    for (const auto &p : parts) {
        out += " WHEN " + p.first + " THEN " + p.second;
    }
    if (else_sql != nullptr) out += " ELSE " + *else_sql;
    out += " END";
    return out;
}

}  // namespace

std::string SqlGen::db_when(const ir::DbWhenExpr &e, int64_t frame_handle, bool /*enclose*/) {
    SqlContext &env = ctx_;
    const ir::DbExpr *keyExpr = e.key_expr();
    const ir::DbExpr *elseExpr = e.else_expr();
    const auto *cases = e.cases();

    // ---- unkeyed CASE: each branch's conds is a boolean expr list, OR-combined ----
    if (keyExpr == nullptr) {
        std::vector<std::pair<std::vector<const ir::DbExpr *>, const ir::DbExpr *>> activeCases;
        const ir::DbExpr *matchedExpr = nullptr;
        for (uint32_t ci = 0; ci < cases->size() && matchedExpr == nullptr; ++ci) {
            const ir::DbWhenCase *c = cases->Get(ci);
            std::vector<const ir::DbExpr *> liveConds;
            bool caseMatched = false;
            for (uint32_t k = 0; k < c->conds()->size(); ++k) {
                const ir::DbExpr *cond = c->conds()->Get(k);
                env.reduce_db_expr(*cond, frame_handle);
                RellValue cv = env.try_evaluate_fully(*cond, frame_handle);
                if (!is_none(cv) && cv.tag == RellTag::BOOLEAN) {
                    if (cv.payload.i64 != 0) {  // constValue == true
                        caseMatched = true;
                        break;
                    }
                    // constValue == false → drop this cond
                } else {
                    liveConds.push_back(cond);  // constValue == null → keep live
                }
            }
            if (caseMatched) {
                matchedExpr = c->expr();
                break;
            }
            if (!liveConds.empty()) activeCases.emplace_back(std::move(liveConds), c->expr());
        }

        if (matchedExpr != nullptr) return db_expr_to_sql(*matchedExpr, frame_handle, false);
        if (activeCases.empty()) {
            return elseExpr != nullptr ? db_expr_to_sql(*elseExpr, frame_handle, false) : std::string("NULL");
        }
        std::vector<std::pair<std::string, std::string>> parts;
        for (auto &ac : activeCases) {
            std::vector<std::string> condFrags;
            condFrags.reserve(ac.first.size());
            for (const ir::DbExpr *cc : ac.first) condFrags.push_back(db_expr_to_sql(*cc, frame_handle, false));
            std::string thenSql = db_expr_to_sql(*ac.second, frame_handle, false);
            parts.emplace_back(or_combine(condFrags), std::move(thenSql));
        }
        std::string elseSql;
        bool hasElse = elseExpr != nullptr;
        if (hasElse) elseSql = db_expr_to_sql(*elseExpr, frame_handle, false);
        return assemble_case(parts, hasElse ? &elseSql : nullptr);
    }

    // ---- keyed CASE ----
    env.reduce_db_expr(*keyExpr, frame_handle);
    RellValue keyConst = env.try_evaluate_fully(*keyExpr, frame_handle);
    if (!is_none(keyConst)) {
        // keyedWhenWithRConst: cases narrow against the cached key value.
        std::vector<std::pair<std::vector<const ir::DbExpr *>, const ir::DbExpr *>> activeCases;
        const ir::DbExpr *matchedExpr = nullptr;
        for (uint32_t ci = 0; ci < cases->size() && matchedExpr == nullptr; ++ci) {
            const ir::DbWhenCase *c = cases->Get(ci);
            std::vector<const ir::DbExpr *> liveConds;
            bool caseMatched = false;
            for (uint32_t k = 0; k < c->conds()->size(); ++k) {
                const ir::DbExpr *cond = c->conds()->Get(k);
                env.reduce_db_expr(*cond, frame_handle);
                RellValue condValue = env.try_evaluate_fully(*cond, frame_handle);
                if (!is_none(condValue)) {
                    // FIDELITY (C3): DbSqlGen folds via `condValue == keyConst`, i.e.
                    // Rt_Value.equals — value equality. Route through ctx_.values_equal, NOT a raw
                    // tag+payload compare: a raw compare is WRONG for HANDLE keys (text/enum/entity
                    // WHEN keys compare jobject identity → two equal Rt_TextValue("a") miscompare,
                    // a matching case is NOT folded and emits a live WHEN branch) and for DEC_LONG
                    // scale-only differences (1.0 vs 1.00 → spuriously unequal).
                    if (env.values_equal(condValue, keyConst)) {
                        caseMatched = true;
                        break;
                    }
                } else {
                    liveConds.push_back(cond);
                }
            }
            if (caseMatched) {
                matchedExpr = c->expr();
                break;
            }
            if (!liveConds.empty()) activeCases.emplace_back(std::move(liveConds), c->expr());
        }

        if (matchedExpr != nullptr) return db_expr_to_sql(*matchedExpr, frame_handle, false);
        if (activeCases.empty()) {
            return elseExpr != nullptr ? db_expr_to_sql(*elseExpr, frame_handle, false) : std::string("NULL");
        }
        const bool keyIsNull = is_rt_null(keyConst);
        std::vector<std::pair<std::string, std::string>> parts;
        for (auto &ac : activeCases) {
            std::string cond;
            if (keyIsNull) {
                // Each cond becomes "{cond} IS NULL", OR-combined.
                std::vector<std::string> condFrags;
                condFrags.reserve(ac.first.size());
                for (const ir::DbExpr *cc : ac.first) {
                    condFrags.push_back(db_expr_to_sql(*cc, frame_handle, false) + " IS NULL");
                }
                cond = or_combine(condFrags);
            } else {
                std::string keyField = bind(keyConst);  // bind ONCE per case (DbSqlGen rebinds per case)
                if (ac.first.size() == 1) {
                    std::string c0 = db_expr_to_sql(*ac.first[0], frame_handle, false);
                    cond = keyField + " = " + c0;
                } else {
                    std::vector<std::string> items;
                    items.reserve(ac.first.size());
                    for (auto *cc : ac.first) items.push_back(db_expr_to_sql(*cc, frame_handle, false));
                    cond = keyField + " IN (";
                    for (size_t k = 0; k < items.size(); ++k) {
                        if (k != 0) cond += ", ";
                        cond += items[k];
                    }
                    cond += ")";
                }
            }
            std::string thenSql = db_expr_to_sql(*ac.second, frame_handle, false);
            parts.emplace_back(std::move(cond), std::move(thenSql));
        }
        std::string elseSql;
        bool hasElse = elseExpr != nullptr;
        if (hasElse) elseSql = db_expr_to_sql(*elseExpr, frame_handle, false);
        return assemble_case(parts, hasElse ? &elseSql : nullptr);
    }

    // keyedWhenWithDbKey: key is a DB-level expr; partition conds into null vs normal.
    env.reduce_db_expr(*keyExpr, frame_handle);
    for (uint32_t ci = 0; ci < cases->size(); ++ci) {
        const ir::DbWhenCase *c = cases->Get(ci);
        for (uint32_t k = 0; k < c->conds()->size(); ++k) env.reduce_db_expr(*c->conds()->Get(k), frame_handle);
    }
    for (uint32_t ci = 0; ci < cases->size(); ++ci) env.reduce_db_expr(*cases->Get(ci)->expr(), frame_handle);
    if (elseExpr != nullptr) env.reduce_db_expr(*elseExpr, frame_handle);

    std::vector<std::pair<std::string, std::string>> parts;
    for (uint32_t ci = 0; ci < cases->size(); ++ci) {
        const ir::DbWhenCase *c = cases->Get(ci);
        std::vector<const ir::DbExpr *> nullConds;
        std::vector<const ir::DbExpr *> normalConds;
        for (uint32_t k = 0; k < c->conds()->size(); ++k) {
            const ir::DbExpr *cond = c->conds()->Get(k);
            RellValue condValue = env.try_evaluate_fully(*cond, frame_handle);
            if (!is_none(condValue) && is_rt_null(condValue)) {
                nullConds.push_back(cond);
            } else {
                normalConds.push_back(cond);
            }
        }

        std::vector<std::string> condParts;
        if (!normalConds.empty()) {
            std::string keyField = db_expr_to_sql(*keyExpr, frame_handle, false);
            if (normalConds.size() == 1) {
                std::string c0 = db_expr_to_sql(*normalConds[0], frame_handle, false);
                condParts.push_back(keyField + " = " + c0);
            } else {
                std::vector<std::string> items;
                items.reserve(normalConds.size());
                for (auto *cc : normalConds) items.push_back(db_expr_to_sql(*cc, frame_handle, false));
                std::string in = keyField + " IN (";
                for (size_t k = 0; k < items.size(); ++k) {
                    if (k != 0) in += ", ";
                    in += items[k];
                }
                in += ")";
                condParts.push_back(in);
            }
        }
        if (!nullConds.empty()) {
            // "{key} IS NULL" — re-render the key expr (DbSqlGen emits keyField a second time here).
            std::string keyField = db_expr_to_sql(*keyExpr, frame_handle, false);
            condParts.push_back(keyField + " IS NULL");
        }
        std::string cond = or_combine(condParts);
        std::string thenSql = db_expr_to_sql(*c->expr(), frame_handle, false);
        parts.emplace_back(std::move(cond), std::move(thenSql));
    }
    std::string elseSql;
    bool hasElse = elseExpr != nullptr;
    if (hasElse) elseSql = db_expr_to_sql(*elseExpr, frame_handle, false);
    return assemble_case(parts, hasElse ? &elseSql : nullptr);
}

// =====================================================================================
// DbNestedAtExpr — transparent: render inner() with the same enclose flag.
// =====================================================================================

std::string SqlGen::db_nested_at(const ir::DbNestedAtExpr &e, int64_t frame_handle, bool enclose) {
    return db_expr_to_sql(*e.inner(), frame_handle, enclose);
}

// DbSubQueryExpr — SqlGen::db_sub_query is defined ONCE, in rell_sql_select.cpp (B1): it owns the
// shared SELECT-body renderer (FROM clause + join-where bind splice + LIMIT/OFFSET). The dispatch
// in db_expr_to_sql above calls it as a member, sharing this->binds_ and the alias counter
// (DbSqlGen.createSub) and pushing a child scope whose parent is the enclosing one.

}  // namespace rell::sql
