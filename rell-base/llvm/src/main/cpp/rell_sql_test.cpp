// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_sql_test.cpp — standalone golden-string test for the native SQL generator
// (namespace rell::sql). It hand-constructs minimal FlatBuffer RR-tree DB nodes
// (DbAtExpr / UpdateStatement / DeleteStatement + the DbExprUnion variants) with the generated
// builders in app_generated.h, runs SqlGen against a no-DB SqlContext test double, and asserts
// the rendered parameterized PostgreSQL TEXT + bind order is byte-for-byte faithful to the JVM
// interpreter's DbSqlGen (runtime-interpreter/src/runtime/rr_interp_sql_gen.kt). NO libpq,
// NO database, NO JVM, no test framework — its own main().
//
// Each EXPECTED string is derived from a specific DbSqlGen rendering rule, cited inline.
//
// =====================================================================================
// BUILD (the no-DB SQL-string-gen translation units + this test):
//   clang++ -std=c++17 \
//     -I<llvm include> -I<flatbuffers include> -I<app_generated.h dir> -I<java include> -I<cpp> \
//     rell_sql_expr.cpp rell_sql_select.cpp rell_sql_write.cpp rell_sql_test.cpp \
//     -L<llvm libdir> -lLLVMSupport -o rell_sql_test
//
//   The three gen TUs + this test link against LLVMSupport only (for the ABI-checks symbol pulled
//   in transitively via rell_runtime.h). rell_sql_types.cpp is NOT linked here — this test does
//   not decode result cells, and that TU drags in the numerics + LLVM bigdec/bigint symbols.
//
// Run:
//   ./rell_sql_test         # exit 0 == all golden vectors matched
//
// =====================================================================================
// WHAT IS COVERED (all executed + asserted)
// =====================================================================================
// SELECT read path + the full DbExprUnion dispatch: simple select + WHERE, attr access + alias
// (DECIMAL ROUND wrap), IN list, EXISTS subquery, COALESCE/Elvis, CASE/WHEN (keyed + unkeyed),
// AND short-circuit fold, nullable-eq IS NULL collapse, and the C1/C2 path-join (DbAttr over a
// DbRel base → structural `JOIN ... ON` in FROM).
//
// WRITE paths (now linked through the unified SqlGen state): INSERT (buildInsertSql), UPDATE
// (SET+WHERE, and the rel-join FROM + M1 double-paren AND form), DELETE (lone-WHERE no-paren M1,
// and the rel-join USING + double-paren AND form).
//
// SqlGen::result() + the SqlGen structural-state methods are defined in rell_sql_select.cpp; the
// evaluation/alias/equality hooks are on SqlContext (no separate SqlExprEnv). This test implements
// the full SqlContext below.

#include <cstdint>
#include <cstdio>
#include <iostream>
#include <map>
#include <string>
#include <vector>

#include "flatbuffers/flatbuffers.h"

#include "rell_sql.h"

namespace fb = ::flatbuffers;
namespace ir = rell::ir;

using rell::llvm_rt::RellValue;
using rell::llvm_rt::RellTag;
using rell::llvm_rt::rv_integer;
using rell::llvm_rt::rv_null;
using rell::llvm_rt::rv_none;
using rell::llvm_rt::rv_boolean;
using rell::sql::RellSql;
using rell::sql::SqlContext;
using rell::sql::SqlGen;

// SqlGen::result() and the SqlGen structural methods are now defined in rell_sql_select.cpp; the
// evaluation/alias hooks are on SqlContext (no separate SqlExprEnv). This test links the four gen
// TUs and implements the full SqlContext below.

// =====================================================================================
// Tiny assertion harness (no framework, mirrors rell_numerics_test.cpp).
// =====================================================================================
namespace {

int g_failures = 0;
int g_checks = 0;

std::string tag_name(RellTag t) {
    switch (t) {
        case RellTag::NONE: return "NONE";
        case RellTag::NULL_: return "NULL";
        case RellTag::UNIT: return "UNIT";
        case RellTag::BOOLEAN: return "BOOLEAN";
        case RellTag::INTEGER: return "INTEGER";
        case RellTag::ROWID: return "ROWID";
        case RellTag::DEC_LONG: return "DEC_LONG";
        case RellTag::BIGINT_LONG: return "BIGINT_LONG";
        case RellTag::HANDLE: return "HANDLE";
    }
    return "?";
}

std::string binds_to_string(const std::vector<RellValue> &binds) {
    std::string s = "[";
    for (size_t i = 0; i < binds.size(); ++i) {
        if (i) s += ", ";
        s += "$" + std::to_string(i + 1) + "=" + tag_name(binds[i].tag);
        if (binds[i].tag == RellTag::INTEGER || binds[i].tag == RellTag::BOOLEAN ||
            binds[i].tag == RellTag::ROWID || binds[i].tag == RellTag::BIGINT_LONG) {
            s += "(" + std::to_string(binds[i].payload.i64) + ")";
        }
    }
    s += "]";
    return s;
}

void check_sql(const std::string &what, const std::string &got_sql, const std::string &exp_sql) {
    ++g_checks;
    if (got_sql != exp_sql) {
        ++g_failures;
        std::cerr << "FAIL [" << what << "] SQL\n   got:      [" << got_sql << "]\n"
                  << "   expected: [" << exp_sql << "]\n";
    }
}

// Assert SQL text + the bind TAGS in order (the consensus-critical bind order).
void check_query(const std::string &what, const RellSql &got, const std::string &exp_sql,
                 const std::vector<RellTag> &exp_bind_tags) {
    check_sql(what, got.text, exp_sql);
    ++g_checks;
    bool order_ok = got.binds.size() == exp_bind_tags.size();
    for (size_t i = 0; order_ok && i < exp_bind_tags.size(); ++i) {
        if (got.binds[i].tag != exp_bind_tags[i]) order_ok = false;
    }
    if (!order_ok) {
        ++g_failures;
        std::cerr << "FAIL [" << what << "] BINDS\n   got:      " << binds_to_string(got.binds)
                  << "\n   expected: " << exp_bind_tags.size() << " binds in order\n";
    }
}

// =====================================================================================
// Test double — a single SqlContext with canned chain naming, a shared alias counter, canned
// attribute->column mappings, and a programmable InterpretedEvaluator. The alias map + rel-join
// registry are now owned by SqlGen (B2); this context only supplies the leaf services SqlGen calls
// (table/rowid/alias/attr naming + R-level evaluation/equality).
//
// Entity model used across the tests (mirrors the SqlEmissionTest fixture):
//   def_index 0 -> table "c0.user",    rowid "rowid"
//   def_index 1 -> table "c0.company", rowid "rowid"
//   def_index 2 -> table "c0.emp",     rowid "rowid"
//
// attr_sql_mapping returns the attribute name verbatim (every column maps to itself, matching the
// simple fixture). eval_interpreted returns canned values from a FIFO queue the test seeds;
// try_evaluate_fully returns rv_none() (no R-level constant folding — every DbInterpreted leaf is a
// live bind), the common production case for a parameterized WHERE. Tests that exercise folding
// (AND/OR short-circuit, keyed-WHEN const key) seed the fold queue.
// =====================================================================================
class TestCtx : public SqlContext {
public:
    std::string table_name(uint32_t def_index) const override {
        switch (def_index) {
            case 0: return "c0.user";
            case 1: return "c0.company";
            case 2: return "c0.emp";
        }
        return "c0.unknown";
    }
    std::string rowid_column(uint32_t /*def_index*/) const override { return "rowid"; }
    std::string fresh_alias() override {
        char buf[8];
        std::snprintf(buf, sizeof(buf), "A%02d", alias_counter_++);
        return std::string(buf);
    }
    std::string rowid_function() const override { return "c0.make_rowid"; }

    std::string attr_sql_mapping(uint32_t /*def_index*/, const std::string &attr_name) const override {
        return attr_name;  // canned: column == attribute name.
    }
    RellValue eval_interpreted(const ir::Expr & /*node*/, int64_t /*frame*/) override {
        if (eval_queue_.empty()) return rv_integer(0);  // default bind
        RellValue v = eval_queue_.front();
        eval_queue_.erase(eval_queue_.begin());
        return v;
    }
    void eval_collection(const ir::Expr & /*node*/, int64_t /*frame*/,
                         std::vector<RellValue> &out) override {
        out = collection_;
    }
    RellValue try_evaluate_fully(const ir::DbExpr & /*e*/, int64_t /*frame*/) override {
        if (fold_queue_.empty()) return rv_none();  // not an R-level constant
        RellValue v = fold_queue_.front();
        fold_queue_.erase(fold_queue_.begin());
        return v;
    }
    void reduce_db_expr(const ir::DbExpr & /*e*/, int64_t /*frame*/) override {}
    // Rt_Value.equals-faithful (C3). The test only constructs primitive WHEN keys (INTEGER/ROWID/
    // BOOLEAN/BIGINT_LONG), where tag+payload identity == Rt_Value.equals; DEC_LONG ignores
    // scale-only diffs (same number). HANDLE would route to the JVM in production.
    bool values_equal(const RellValue &a, const RellValue &b) const override {
        if (a.tag != b.tag) return false;
        return a.payload.i64 == b.payload.i64;  // primitive WHEN keys only in tests.
    }
    bool collection_is_empty(const RellValue & /*v*/) const override { return collection_.empty(); }
    bool snapshot_active() const override { return snapshot_; }
    std::vector<std::string> entity_attr_sql_mappings(uint32_t /*def_index*/) const override {
        return snapshot_attrs_;
    }

    // ---- test programming ----
    void seed_eval(RellValue v) { eval_queue_.push_back(v); }
    void seed_fold(RellValue v) { fold_queue_.push_back(v); }
    void set_collection(std::vector<RellValue> c) { collection_ = std::move(c); }

private:
    mutable int alias_counter_ = 0;
    std::vector<RellValue> eval_queue_;
    std::vector<RellValue> fold_queue_;
    std::vector<RellValue> collection_;
    bool snapshot_ = false;
    std::vector<std::string> snapshot_attrs_;
};

// =====================================================================================
// FlatBuffer node-builder helpers (thin wrappers over the generated CreateXxx).
// =====================================================================================

fb::Offset<ir::Type> make_primitive(fb::FlatBufferBuilder &b, ir::PrimitiveTypeKind kind) {
    auto p = ir::CreatePrimitiveType(b, kind);
    return ir::CreateType(b, ir::TypeUnion_PrimitiveType, p.Union());
}

// A minimal but STRUCTURALLY-VALID R-level Expr leaf. Expr.expr is a required union, so a NONE
// union fails the FlatBuffer required-field check; we wrap a trivial ErrorExpr (type + message,
// both shallow) instead. The test env's eval_interpreted/eval_collection ignore the node content,
// so the choice of variant is irrelevant to rendering — it only needs to be present and valid.
fb::Offset<ir::Expr> make_dummy_expr(fb::FlatBufferBuilder &b) {
    auto err = ir::CreateErrorExprDirect(b, make_primitive(b, ir::PrimitiveTypeKind_BOOLEAN),
                                         "dummy");
    return ir::CreateExpr(b, ir::ExprUnion_ErrorExpr, err.Union());
}

// DbExpr wrapping a DbInterpretedExpr (an R-level bind leaf).
fb::Offset<ir::DbExpr> make_interpreted(fb::FlatBufferBuilder &b) {
    auto inner = ir::CreateDbInterpretedExpr(b, make_dummy_expr(b));
    return ir::CreateDbExpr(b, ir::DbExprUnion_DbInterpretedExpr, inner.Union());
}

// DbExpr wrapping a DbEntityExpr.
fb::Offset<ir::DbExpr> make_entity(fb::FlatBufferBuilder &b, uint32_t def_index, uint32_t id) {
    auto inner = ir::CreateDbEntityExpr(b, def_index, id);
    return ir::CreateDbExpr(b, ir::DbExprUnion_DbEntityExpr, inner.Union());
}

// DbExpr wrapping a DbAttrExpr over a DbEntity base.
fb::Offset<ir::DbExpr> make_attr(fb::FlatBufferBuilder &b, uint32_t entity_def_index,
                                 uint32_t entity_id, const char *attr, ir::PrimitiveTypeKind kind) {
    auto base = make_entity(b, entity_def_index, entity_id);
    auto inner = ir::CreateDbAttrExprDirect(b, base, attr, make_primitive(b, kind));
    return ir::CreateDbExpr(b, ir::DbExprUnion_DbAttrExpr, inner.Union());
}

// DbExpr wrapping a DbBinaryExpr.
fb::Offset<ir::DbExpr> make_binary(fb::FlatBufferBuilder &b, ir::DbBinaryOp op,
                                   fb::Offset<ir::DbExpr> left, fb::Offset<ir::DbExpr> right,
                                   ir::PrimitiveTypeKind result_kind, bool nullable_eq = false) {
    auto inner = ir::CreateDbBinaryExpr(b, make_primitive(b, result_kind), op, left, right,
                                        nullable_eq);
    return ir::CreateDbExpr(b, ir::DbExprUnion_DbBinaryExpr, inner.Union());
}

// A DbAtWhatField with default (no-op) flags — the SQL gen reads flags->omit()/sort()/group()/
// aggregate() but the FlatBuffer requires the flags table to be PRESENT (required field), so we
// supply a real default AtWhatFieldFlags rather than a null offset.
fb::Offset<ir::DbAtWhatField> make_what(fb::FlatBufferBuilder &b, fb::Offset<ir::DbExpr> expr) {
    auto flags = ir::CreateAtWhatFieldFlags(b, /*omit=*/false, /*sort=*/0, /*group=*/false,
                                            /*aggregate=*/false);
    return ir::CreateDbAtWhatField(b, flags, expr, 0);
}

// A single-entity DbAtExprFrom.
fb::Offset<ir::DbAtExprFrom> make_from1(fb::FlatBufferBuilder &b, uint32_t def_index, uint32_t id) {
    std::vector<fb::Offset<ir::DbAtEntity>> ents;
    ents.push_back(ir::CreateDbAtEntity(b, def_index, id, /*join_where=*/0, /*is_outer=*/false, 0));
    return ir::CreateDbAtExprFromDirect(b, &ents);
}

// Build a complete DbAtExpr. DbAtExpr requires type / from / what / internals / err_pos to be
// PRESENT (FlatBuffer required fields), even though the SQL renderer reads only from/what/where/
// cardinality/extras. We supply minimal placeholders for the required-but-unused fields so the
// builder's required-field verification passes.
fb::Offset<ir::DbAtExpr> make_db_at(fb::FlatBufferBuilder &b, fb::Offset<ir::DbAtExprFrom> from,
                                    fb::Offset<fb::Vector<fb::Offset<ir::DbAtWhatField>>> what,
                                    fb::Offset<ir::DbExpr> where, ir::AtCardinality card,
                                    fb::Offset<ir::AtExprExtras> extras = 0) {
    auto type = make_primitive(b, ir::PrimitiveTypeKind_BOOLEAN);  // unused by the renderer
    auto internals = ir::CreateDbAtExprInternals(b, 0);
    auto err_pos = ir::CreateSourcePos(b, 0, 0, 0);
    return ir::CreateDbAtExpr(b, type, from, what, where, card, extras, internals, err_pos);
}

// Finish a buffer rooted at a DbAtExpr and return the parsed root (buffer stays owned by `b`).
const ir::DbAtExpr *finish_at(fb::FlatBufferBuilder &b, fb::Offset<ir::DbAtExpr> root) {
    b.Finish(root);
    return fb::GetRoot<ir::DbAtExpr>(b.GetBufferPointer());
}

// DbExpr wrapping a DbRelExpr (FK on the base entity → target). base is an entity ref.
fb::Offset<ir::DbExpr> make_rel(fb::FlatBufferBuilder &b, uint32_t base_def_index,
                                uint32_t base_id, const char *attr, uint32_t target_def_index) {
    auto base = make_entity(b, base_def_index, base_id);
    auto inner = ir::CreateDbRelExprDirect(b, base, attr, target_def_index);
    return ir::CreateDbExpr(b, ir::DbExprUnion_DbRelExpr, inner.Union());
}

// DbExpr wrapping a DbAttrExpr whose base is a DbRel (a path-join attr, e.g. `.company.name`).
fb::Offset<ir::DbExpr> make_attr_over_rel(fb::FlatBufferBuilder &b, fb::Offset<ir::DbExpr> rel_base,
                                          const char *attr, ir::PrimitiveTypeKind kind) {
    auto inner = ir::CreateDbAttrExprDirect(b, rel_base, attr, make_primitive(b, kind));
    return ir::CreateDbExpr(b, ir::DbExprUnion_DbAttrExpr, inner.Union());
}

// A DbAtEntity offset (root entity of a write statement).
fb::Offset<ir::DbAtEntity> make_at_entity(fb::FlatBufferBuilder &b, uint32_t def_index, uint32_t id) {
    return ir::CreateDbAtEntity(b, def_index, id, /*join_where=*/0, /*is_outer=*/false, 0);
}

// UpdateStatementWhat: a single SET assignment (attr <- value DbExpr).
fb::Offset<ir::UpdateStatementWhat> make_set(fb::FlatBufferBuilder &b, const char *attr,
                                             fb::Offset<ir::DbExpr> value) {
    return ir::CreateUpdateStatementWhatDirect(b, attr, /*attr_index=*/0, value);
}

// Minimal placeholders for the required-but-renderer-unused write-statement fields.
fb::Offset<ir::FrameBlock> make_frame_block(fb::FlatBufferBuilder &b) {
    return ir::CreateFrameBlock(b);
}
fb::Offset<ir::SourcePos> make_src_pos(fb::FlatBufferBuilder &b) {
    return ir::CreateSourcePos(b, 0, 0, 0);
}

// =====================================================================================
// TEST CASES
// =====================================================================================

// (1) Simple select with WHERE: a DbBinary(GT) over DbAttr + DbInterpreted bind.
//
//   Rell:  user @* { .score > $param } ( .score )
//
// RULE (rr_interp_sql_gen.kt):
//   - SELECT what-field = DbAttr(.score) -> "A00.\"score\"" (columnField; alias bare, col quoted,
//     ROUND only when DECIMAL — here INTEGER, so bare). buildSelectQueryAst.
//   - FROM = addFromTo first entity -> "FROM \"c0.user\" A00" (aliasedTable: table quoted, alias
//     bare).
//   - WHERE = binaryToField default branch, enclose=false at top level:
//       "A00.\"score\" > $1"   (operands enclose=true but are leaf columns/binds; the GT op
//        renders "{L} OP {R}" with single spaces; the bind leaf -> "$1").
//   - Cardinality ZERO_MANY, no limit/offset -> generateDbAtOrderBy default-rowid trigger fires
//     (isMany): "ORDER BY A00.\"rowid\"".
//   Bind order: SELECT-list (none) -> WHERE ($1 = the score param). One INTEGER bind.
void test_simple_select_where(int64_t frame) {
    fb::FlatBufferBuilder b;
    TestCtx ctx;
    ctx.seed_eval(rv_integer(50));  // the .score > 50 param

    auto score_what = make_attr(b, 0, 1, "score", ir::PrimitiveTypeKind_INTEGER);
    std::vector<fb::Offset<ir::DbAtWhatField>> whats;
    whats.push_back(make_what(b, score_what));
    auto what_vec = b.CreateVector(whats);

    auto score_l = make_attr(b, 0, 1, "score", ir::PrimitiveTypeKind_INTEGER);
    auto param_r = make_interpreted(b);
    auto where = make_binary(b, ir::DbBinaryOp_GT, score_l, param_r,
                             ir::PrimitiveTypeKind_BOOLEAN);

    auto from = make_from1(b, 0, 1);
    auto at = make_db_at(b, from, what_vec, where, ir::AtCardinality_ZERO_MANY);
    const ir::DbAtExpr *root = finish_at(b, at);

    SqlGen gen(ctx);
    RellSql sql = gen.render_db_at(*root, frame);

    check_query("simple_select_where",
                sql,
                "SELECT A00.\"score\" FROM \"c0.user\" A00 WHERE A00.\"score\" > $1 "
                "ORDER BY A00.\"rowid\"",
                {RellTag::INTEGER});
}

// (2) Attr access + alias (DECIMAL column gets ROUND-wrapped); ZERO_ONE so no default ORDER BY.
//
//   Rell:  user @ {} ( .balance )      // .balance: decimal
//
// RULE: DbAttr with DECIMAL type -> wrapDecimal -> "ROUND(A00.\"balance\", 20)"
//   (Lib_DecimalMath.DECIMAL_FRAC_DIGITS == 20). Cardinality ZERO_ONE (not many), no
//   limit/offset -> NO default rowid ORDER BY. No WHERE -> no WHERE clause. No binds.
void test_attr_alias_decimal(int64_t frame) {
    fb::FlatBufferBuilder b;
    TestCtx ctx;

    auto bal_what = make_attr(b, 0, 1, "balance", ir::PrimitiveTypeKind_DECIMAL);
    std::vector<fb::Offset<ir::DbAtWhatField>> whats;
    whats.push_back(make_what(b, bal_what));
    auto what_vec = b.CreateVector(whats);

    auto from = make_from1(b, 0, 1);
    auto at = make_db_at(b, from, what_vec, /*where=*/0, ir::AtCardinality_ZERO_ONE);
    const ir::DbAtExpr *root = finish_at(b, at);

    SqlGen gen(ctx);
    RellSql sql = gen.render_db_at(*root, frame);

    check_query("attr_alias_decimal",
                sql,
                "SELECT ROUND(A00.\"balance\", 20) FROM \"c0.user\" A00",
                {});
}

// (3) IN list: DbIn with a key column and a static list of two interpreted binds.
//
//   Rell:  user @* { .score in [10, 20] } ( .rowid )
//
// RULE (inField / tupleField): "{key} IN (i0,i1)" — key rendered enclose=false; tuple is a
//   BARE-comma join "(...)" (no space after comma). not_=false -> "IN". The list binds come
//   AFTER the SELECT-list, in list order. Here SELECT is the entity rowid -> DbEntity ->
//   "A00.\"rowid\"". Cardinality ZERO_MANY -> default ORDER BY rowid.
//   Bind order: $1, $2 = the two list elements (SELECT has no binds; key is a column).
void test_in_list(int64_t frame) {
    fb::FlatBufferBuilder b;
    TestCtx ctx;
    ctx.seed_eval(rv_integer(10));
    ctx.seed_eval(rv_integer(20));

    auto rowid_what = make_entity(b, 0, 1);  // DbEntity -> rowid column
    std::vector<fb::Offset<ir::DbAtWhatField>> whats;
    whats.push_back(make_what(b, rowid_what));
    auto what_vec = b.CreateVector(whats);

    auto key = make_attr(b, 0, 1, "score", ir::PrimitiveTypeKind_INTEGER);
    std::vector<fb::Offset<ir::DbExpr>> list;
    list.push_back(make_interpreted(b));
    list.push_back(make_interpreted(b));
    auto where_in = ir::CreateDbInExprDirect(b, key, &list, /*not_=*/false);
    auto where = ir::CreateDbExpr(b, ir::DbExprUnion_DbInExpr, where_in.Union());

    auto from = make_from1(b, 0, 1);
    auto at = make_db_at(b, from, what_vec, where, ir::AtCardinality_ZERO_MANY);
    const ir::DbAtExpr *root = finish_at(b, at);

    SqlGen gen(ctx);
    RellSql sql = gen.render_db_at(*root, frame);

    check_query("in_list",
                sql,
                "SELECT A00.\"rowid\" FROM \"c0.user\" A00 WHERE A00.\"score\" IN ($1,$2) "
                "ORDER BY A00.\"rowid\"",
                {RellTag::INTEGER, RellTag::INTEGER});
}

// (4) Elvis / COALESCE: DbElvis(left=DbAttr, right=DbInterpreted) where left is a DB expr
//     (NOT an R-level constant), so neither collapse branch fires.
//
//   Rell:  user @* {} ( .nickname ?: $default )
//
// RULE (elvisField): left is reduced; try_evaluate_fully(left) -> none (DB column) so it does
//   not collapse; result = "COALESCE({L}, {R})" with L,R rendered enclose=false.
//   L = "A00.\"nickname\"", R = "$1" (the default bind). Cardinality ZERO_MANY -> ORDER BY rowid.
//   Bind order: $1 = the default (SELECT-list field is the COALESCE; the bind is inside it).
void test_elvis_coalesce(int64_t frame) {
    fb::FlatBufferBuilder b;
    TestCtx ctx;
    ctx.seed_eval(rv_integer(0));  // the $default bind (rendered as $1)
    // try_evaluate_fully returns none by default -> left is a DB column, no collapse.

    auto left = make_attr(b, 0, 1, "nickname", ir::PrimitiveTypeKind_TEXT);
    auto right = make_interpreted(b);
    auto elvis = ir::CreateDbElvisExpr(b, make_primitive(b, ir::PrimitiveTypeKind_TEXT), left,
                                       right);
    auto what_expr = ir::CreateDbExpr(b, ir::DbExprUnion_DbElvisExpr, elvis.Union());

    std::vector<fb::Offset<ir::DbAtWhatField>> whats;
    whats.push_back(make_what(b, what_expr));
    auto what_vec = b.CreateVector(whats);

    auto from = make_from1(b, 0, 1);
    auto at = make_db_at(b, from, what_vec, /*where=*/0, ir::AtCardinality_ZERO_MANY);
    const ir::DbAtExpr *root = finish_at(b, at);

    SqlGen gen(ctx);
    RellSql sql = gen.render_db_at(*root, frame);

    check_query("elvis_coalesce",
                sql,
                "SELECT COALESCE(A00.\"nickname\", $1) FROM \"c0.user\" A00 "
                "ORDER BY A00.\"rowid\"",
                {RellTag::INTEGER});
}

// (5) EXISTS subquery: DbExists(sub = DbSubQuery over company), not_=false, as the WHERE.
//
//   Rell:  user @* { exists( company @* { .owner == user } ) } ( .rowid )
//
// RULE (existsField + subQueryParameterizedSql): when the sub is a DbSubQuery, render the
//   subquery body to "(SELECT ... FROM ...)" and prefix "EXISTS " -> "EXISTS (SELECT ...)".
//   The subquery shares the parent alias counter (createSub): the outer user is A00, the inner
//   company gets A01. The subquery SELECTs nothing -> "SELECT 0" (addSelect(DSL.field("0"))).
//   The inner WHERE is a DbBinary EQ over the company attr "owner" vs an interpreted bind.
//   Cardinality of the subquery defaults (no limit/offset) -> NO default ORDER BY in subquery
//   (subQueryOrderBy trigger is limit||offset only). Outer ZERO_MANY -> outer ORDER BY rowid.
//   Bind order: the inner WHERE bind $1 (rendered while building the WHERE of the subquery,
//   which is the only bind anywhere).
void test_exists_subquery(int64_t frame) {
    fb::FlatBufferBuilder b;
    TestCtx ctx;
    ctx.seed_eval(rv_integer(0));  // inner WHERE bind -> $1

    // outer SELECT field: user rowid
    auto outer_what_expr = make_entity(b, 0, 1);
    std::vector<fb::Offset<ir::DbAtWhatField>> outer_whats;
    outer_whats.push_back(make_what(b, outer_what_expr));
    auto outer_what_vec = b.CreateVector(outer_whats);

    // inner subquery: company @* { .owner == $bind }, selecting nothing -> SELECT 0
    auto inner_owner = make_attr(b, 1, 2, "owner", ir::PrimitiveTypeKind_INTEGER);
    auto inner_bind = make_interpreted(b);
    auto inner_where = make_binary(b, ir::DbBinaryOp_EQ, inner_owner, inner_bind,
                                   ir::PrimitiveTypeKind_BOOLEAN);
    auto inner_from = make_from1(b, 1, 2);
    std::vector<fb::Offset<ir::DbAtWhatField>> inner_whats;  // empty -> SELECT 0
    auto inner_what_vec = b.CreateVector(inner_whats);
    auto subq = ir::CreateDbSubQueryExpr(b, inner_from, inner_what_vec, inner_where,
                                         /*extras=*/0, /*is_many=*/true, /*internals=*/0);
    auto subq_expr = ir::CreateDbExpr(b, ir::DbExprUnion_DbSubQueryExpr, subq.Union());

    auto exists = ir::CreateDbExistsExpr(b, subq_expr, /*not_=*/false);
    auto where = ir::CreateDbExpr(b, ir::DbExprUnion_DbExistsExpr, exists.Union());

    auto outer_from = make_from1(b, 0, 1);
    auto at = make_db_at(b, outer_from, outer_what_vec, where, ir::AtCardinality_ZERO_MANY);
    const ir::DbAtExpr *root = finish_at(b, at);

    SqlGen gen(ctx);
    RellSql sql = gen.render_db_at(*root, frame);

    check_query("exists_subquery",
                sql,
                "SELECT A00.\"rowid\" FROM \"c0.user\" A00 WHERE EXISTS (SELECT 0 FROM "
                "\"c0.company\" A01 WHERE A01.\"owner\" = $1) ORDER BY A00.\"rowid\"",
                {RellTag::INTEGER});
}

// (6) Unkeyed CASE/WHEN as a selected field: every cond is a live DB boolean expr.
//
//   Rell:  user @* {} ( when { .score > $a -> 1; else -> 0 } )
//
// RULE (unkeyedWhen): key_expr == null. Each case's conds (here one) is a boolean DB expr,
//   OR-combined (a single cond renders bare). try_evaluate_fully returns none (no folding), so
//   the one case stays live: "CASE WHEN {cond} THEN {then} ELSE {else} END". cond is a DbBinary
//   GT -> "A00.\"score\" > $1"; then = DbInterpreted "$2"; else = DbInterpreted "$3".
//   Cardinality ZERO_MANY -> default ORDER BY rowid (over the from entity). The ORDER BY field
//   is rendered AFTER WHERE/the SELECT field; here the only entity rowid -> "A00.\"rowid\"".
//   Bind order: SELECT-list renders the CASE first: cond bind $1, then-bind $2, else-bind $3.
void test_when_unkeyed(int64_t frame) {
    fb::FlatBufferBuilder b;
    TestCtx ctx;
    ctx.seed_eval(rv_integer(50));  // cond bind  -> $1
    ctx.seed_eval(rv_integer(1));   // THEN value -> $2
    ctx.seed_eval(rv_integer(0));   // ELSE value -> $3

    // cond: .score > $a
    auto cond_l = make_attr(b, 0, 1, "score", ir::PrimitiveTypeKind_INTEGER);
    auto cond_r = make_interpreted(b);
    auto cond = make_binary(b, ir::DbBinaryOp_GT, cond_l, cond_r, ir::PrimitiveTypeKind_BOOLEAN);
    std::vector<fb::Offset<ir::DbExpr>> conds;
    conds.push_back(cond);
    auto then_expr = make_interpreted(b);
    auto wcase = ir::CreateDbWhenCaseDirect(b, &conds, then_expr);
    std::vector<fb::Offset<ir::DbWhenCase>> cases;
    cases.push_back(wcase);
    auto else_expr = make_interpreted(b);
    auto when = ir::CreateDbWhenExprDirect(b, make_primitive(b, ir::PrimitiveTypeKind_INTEGER),
                                           /*key_expr=*/0, &cases, else_expr);
    auto what_expr = ir::CreateDbExpr(b, ir::DbExprUnion_DbWhenExpr, when.Union());

    std::vector<fb::Offset<ir::DbAtWhatField>> whats;
    whats.push_back(make_what(b, what_expr));
    auto what_vec = b.CreateVector(whats);

    auto from = make_from1(b, 0, 1);
    auto at = make_db_at(b, from, what_vec, /*where=*/0, ir::AtCardinality_ZERO_MANY);
    const ir::DbAtExpr *root = finish_at(b, at);

    SqlGen gen(ctx);
    RellSql sql = gen.render_db_at(*root, frame);

    check_query("when_unkeyed",
                sql,
                "SELECT CASE WHEN A00.\"score\" > $1 THEN $2 ELSE $3 END FROM \"c0.user\" A00 "
                "ORDER BY A00.\"rowid\"",
                {RellTag::INTEGER, RellTag::INTEGER, RellTag::INTEGER});
}

// (7) Keyed CASE/WHEN with a DB key (keyedWhenWithDbKey): key is a DB column, conds are live
//     DB exprs, partitioned into normal vs null.
//
//   Rell:  user @* {} ( when (.score) { $a -> 1; else -> 0 } )
//
// RULE (keyedWhenWithDbKey): key_expr present, try_evaluate_fully(key) -> none (DB column).
//   For each case: cond is a normal DB expr -> "{key} = {c}" (single normal cond). key re-rendered
//   per case. Here one case: "A00.\"score\" = $1" THEN "$2"; ELSE "$3".
//   "CASE WHEN A00.\"score\" = $1 THEN $2 ELSE $3 END". Cardinality ZERO_MANY -> ORDER BY rowid.
//   Bind order: SELECT-list renders CASE: keyField is a column (no bind), cond bind $1, THEN $2,
//   ELSE $3.
void test_when_keyed_db(int64_t frame) {
    fb::FlatBufferBuilder b;
    TestCtx ctx;
    ctx.seed_eval(rv_integer(42));  // cond value -> $1
    ctx.seed_eval(rv_integer(1));   // THEN       -> $2
    ctx.seed_eval(rv_integer(0));   // ELSE       -> $3

    auto key = make_attr(b, 0, 1, "score", ir::PrimitiveTypeKind_INTEGER);
    auto cond_val = make_interpreted(b);
    std::vector<fb::Offset<ir::DbExpr>> conds;
    conds.push_back(cond_val);
    auto then_expr = make_interpreted(b);
    auto wcase = ir::CreateDbWhenCaseDirect(b, &conds, then_expr);
    std::vector<fb::Offset<ir::DbWhenCase>> cases;
    cases.push_back(wcase);
    auto else_expr = make_interpreted(b);
    auto when = ir::CreateDbWhenExprDirect(b, make_primitive(b, ir::PrimitiveTypeKind_INTEGER), key,
                                           &cases, else_expr);
    auto what_expr = ir::CreateDbExpr(b, ir::DbExprUnion_DbWhenExpr, when.Union());

    std::vector<fb::Offset<ir::DbAtWhatField>> whats;
    whats.push_back(make_what(b, what_expr));
    auto what_vec = b.CreateVector(whats);

    auto from = make_from1(b, 0, 1);
    auto at = make_db_at(b, from, what_vec, /*where=*/0, ir::AtCardinality_ZERO_MANY);
    const ir::DbAtExpr *root = finish_at(b, at);

    SqlGen gen(ctx);
    RellSql sql = gen.render_db_at(*root, frame);

    check_query("when_keyed_db",
                sql,
                "SELECT CASE WHEN A00.\"score\" = $1 THEN $2 ELSE $3 END FROM \"c0.user\" A00 "
                "ORDER BY A00.\"rowid\"",
                {RellTag::INTEGER, RellTag::INTEGER, RellTag::INTEGER});
}

// (8) AND short-circuit folding: DbBinary(AND) where the LEFT operand folds to R-level TRUE.
//
//   Rell:  user @* { true and .active } ( .rowid )
//
// RULE (Db_BinaryOp_AndOr): op == "AND", shortVal == false. try_evaluate_fully(left) -> TRUE
//   (a BOOLEAN R-const). Since lcb(true) != shortVal(false), the whole AND collapses to the
//   RIGHT operand rendered at the SAME enclose flag. Right = DbAttr(.active) -> "A00.\"active\"".
//   So WHERE = "A00.\"active\"" — NO parentheses, NO bind for the folded-away constant.
//   Cardinality ZERO_MANY -> ORDER BY rowid. No binds at all.
void test_and_short_circuit(int64_t frame) {
    fb::FlatBufferBuilder b;
    TestCtx ctx;
    // fold queue: first try_evaluate_fully(left) -> TRUE.
    ctx.seed_fold(rv_boolean(true));

    auto rowid_what = make_entity(b, 0, 1);
    std::vector<fb::Offset<ir::DbAtWhatField>> whats;
    whats.push_back(make_what(b, rowid_what));
    auto what_vec = b.CreateVector(whats);

    // left will be folded; build it as an interpreted leaf (content irrelevant — folded away).
    auto left = make_interpreted(b);
    auto right = make_attr(b, 0, 1, "active", ir::PrimitiveTypeKind_BOOLEAN);
    auto where = make_binary(b, ir::DbBinaryOp_AND, left, right, ir::PrimitiveTypeKind_BOOLEAN);

    auto from = make_from1(b, 0, 1);
    auto at = make_db_at(b, from, what_vec, where, ir::AtCardinality_ZERO_MANY);
    const ir::DbAtExpr *root = finish_at(b, at);

    SqlGen gen(ctx);
    RellSql sql = gen.render_db_at(*root, frame);

    check_query("and_short_circuit",
                sql,
                "SELECT A00.\"rowid\" FROM \"c0.user\" A00 WHERE A00.\"active\" "
                "ORDER BY A00.\"rowid\"",
                {});
}

// (9) Nullable-equality collapse: DbBinary EQ_NULL with a known-NULL right operand -> IS NULL.
//
//   Rell:  user @* { .nickname == null } ( .rowid )   // null-safe ==
//
// RULE (binaryToField nullable_eq): op EQ_NULL ("IS NOT DISTINCT FROM"), isEqual=true. The
//   right operand is a DbInterpreted that evaluates to Rt_NullValue (rightConst is NULL), so the
//   branch collapses to nullTpl(left) with "IS NULL", left rendered enclose=true. At top level
//   enclose=false -> "A00.\"nickname\" IS NULL" (no outer parens; the IS NULL template uses the
//   outer enclose flag). NO bind for the folded-away NULL. Cardinality ZERO_MANY -> ORDER BY rowid.
void test_nullable_eq_is_null(int64_t frame) {
    fb::FlatBufferBuilder b;
    TestCtx ctx;
    // db_binary pre-evaluates the right Interpreted operand via eval_interpreted -> NULL.
    ctx.seed_eval(rv_null());

    auto rowid_what = make_entity(b, 0, 1);
    std::vector<fb::Offset<ir::DbAtWhatField>> whats;
    whats.push_back(make_what(b, rowid_what));
    auto what_vec = b.CreateVector(whats);

    auto left = make_attr(b, 0, 1, "nickname", ir::PrimitiveTypeKind_TEXT);
    auto right = make_interpreted(b);  // evaluates to NULL
    auto where = make_binary(b, ir::DbBinaryOp_EQ_NULL, left, right,
                             ir::PrimitiveTypeKind_BOOLEAN, /*nullable_eq=*/true);

    auto from = make_from1(b, 0, 1);
    auto at = make_db_at(b, from, what_vec, where, ir::AtCardinality_ZERO_MANY);
    const ir::DbAtExpr *root = finish_at(b, at);

    SqlGen gen(ctx);
    RellSql sql = gen.render_db_at(*root, frame);

    check_query("nullable_eq_is_null",
                sql,
                "SELECT A00.\"rowid\" FROM \"c0.user\" A00 WHERE A00.\"nickname\" IS NULL "
                "ORDER BY A00.\"rowid\"",
                {});
}

// =====================================================================================
// WRITE-PATH GOLDEN REFERENCES — pinned, NOT executed (see header). These mirror the
// SqlEmissionTest golden block quoted in rell_sql_write.cpp. They document the exact
// strings render_insert / render_update / render_delete must produce once the write TU
// links (the SqlGen state members land on the header). TODO(write-path-link).
// =====================================================================================
// (10) C1/C2 path-join: `emp @* { .company.name == 'Acme' }` — a DbAttr over a DbRel base. The
// rel-join discovered while rendering WHERE must reach the FROM clause as a structural JOIN.
//
// Golden (SqlEmissionTest at_path_join):
//   SELECT A00."rowid" FROM "c0.emp" A00 JOIN "c0.company" A01 ON A00."company" = A01."rowid"
//   WHERE A01."name" = $1 ORDER BY A00."rowid"
// emp=def2 (A00); company=def1 (A01, allocated lazily by resolve_table_expr). Bind: the 'Acme' text.
void test_path_join(int64_t frame) {
    fb::FlatBufferBuilder b;
    TestCtx ctx;
    ctx.seed_eval(rv_integer(0));  // the 'Acme' bind -> $1 (text in prod; tag irrelevant here)

    auto rowid_what = make_entity(b, /*def=*/2, /*id=*/1);  // emp rowid
    std::vector<fb::Offset<ir::DbAtWhatField>> whats;
    whats.push_back(make_what(b, rowid_what));
    auto what_vec = b.CreateVector(whats);

    // WHERE: .company.name == $bind  →  DbAttr(name) over DbRel(emp.company -> company)
    auto rel = make_rel(b, /*base_def=*/2, /*base_id=*/1, "company", /*target_def=*/1);
    auto attr = make_attr_over_rel(b, rel, "name", ir::PrimitiveTypeKind_TEXT);
    auto bind_r = make_interpreted(b);
    auto where = make_binary(b, ir::DbBinaryOp_EQ, attr, bind_r, ir::PrimitiveTypeKind_BOOLEAN);

    auto from = make_from1(b, /*def=*/2, /*id=*/1);
    auto at = make_db_at(b, from, what_vec, where, ir::AtCardinality_ZERO_MANY);
    const ir::DbAtExpr *root = finish_at(b, at);

    SqlGen gen(ctx);
    RellSql sql = gen.render_db_at(*root, frame);

    check_query("path_join", sql,
                "SELECT A00.\"rowid\" FROM \"c0.emp\" A00 JOIN \"c0.company\" A01 ON "
                "A00.\"company\" = A01.\"rowid\" WHERE A01.\"name\" = $1 ORDER BY A00.\"rowid\"",
                {RellTag::INTEGER});
}

// (11) INSERT — buildInsertSql golden. attr_sql_mappings + attr_binds are pre-resolved by caller.
void test_insert() {
    fb::FlatBufferBuilder b;  // unused (INSERT takes resolved args, no FlatBuffer DbExpr)
    (void)b;
    TestCtx ctx;
    std::vector<std::string> cols = {"name", "firstName", "lastName", "score"};
    std::vector<RellValue> vals = {rv_integer(0), rv_integer(0), rv_integer(0), rv_integer(0)};

    SqlGen gen(ctx);
    RellSql sql = gen.render_insert(/*entity_def_index=*/0, cols, vals);
    check_query("insert", sql,
                "INSERT INTO \"c0.user\" (\"rowid\", \"name\", \"firstName\", \"lastName\", "
                "\"score\") VALUES (\"c0.make_rowid\"(), $1, $2, $3, $4) RETURNING \"rowid\"",
                {RellTag::INTEGER, RellTag::INTEGER, RellTag::INTEGER, RellTag::INTEGER});
}

// (12) UPDATE SET + WHERE — executeUpdateSqlInner golden. SET binds first, then user WHERE.
//   update user @ { .name == $n } ( score = $s )
void test_update_set_where(int64_t frame) {
    fb::FlatBufferBuilder b;
    TestCtx ctx;
    ctx.seed_eval(rv_integer(7));  // SET score = $1
    ctx.seed_eval(rv_integer(0));  // WHERE name = $2

    auto entity = make_at_entity(b, /*def=*/0, /*id=*/1);
    auto set_val = make_interpreted(b);
    std::vector<fb::Offset<ir::UpdateStatementWhat>> sets;
    sets.push_back(make_set(b, "score", set_val));
    auto name_attr = make_attr(b, 0, 1, "name", ir::PrimitiveTypeKind_TEXT);
    auto name_bind = make_interpreted(b);
    auto where = make_binary(b, ir::DbBinaryOp_EQ, name_attr, name_bind,
                             ir::PrimitiveTypeKind_BOOLEAN);
    auto upd = ir::CreateUpdateStatementDirect(b, entity, /*extra=*/nullptr, where, &sets,
                                              make_frame_block(b), make_src_pos(b));
    b.Finish(upd);
    const ir::UpdateStatement *root = fb::GetRoot<ir::UpdateStatement>(b.GetBufferPointer());

    SqlGen gen(ctx);
    RellSql sql = gen.render_update(*root, frame);
    check_query("update_set_where", sql,
                "UPDATE \"c0.user\" A00 SET \"score\" = $1 WHERE A00.\"name\" = $2 "
                "RETURNING A00.\"rowid\"",
                {RellTag::INTEGER, RellTag::INTEGER});
}

// (13) UPDATE with rel-join (M1: double-paren AND form) — executeUpdateSqlInner golden.
//   update emp @* { .company.name == $n } ( salary = $s )
void test_update_path_join(int64_t frame) {
    fb::FlatBufferBuilder b;
    TestCtx ctx;
    ctx.seed_eval(rv_integer(0));  // SET salary = $1
    ctx.seed_eval(rv_integer(0));  // WHERE name  = $2

    auto entity = make_at_entity(b, /*def=*/2, /*id=*/1);  // emp A00
    auto set_val = make_interpreted(b);
    std::vector<fb::Offset<ir::UpdateStatementWhat>> sets;
    sets.push_back(make_set(b, "salary", set_val));
    auto rel = make_rel(b, /*base_def=*/2, /*base_id=*/1, "company", /*target_def=*/1);
    auto attr = make_attr_over_rel(b, rel, "name", ir::PrimitiveTypeKind_TEXT);
    auto name_bind = make_interpreted(b);
    auto where = make_binary(b, ir::DbBinaryOp_EQ, attr, name_bind, ir::PrimitiveTypeKind_BOOLEAN);
    auto upd = ir::CreateUpdateStatementDirect(b, entity, /*extra=*/nullptr, where, &sets,
                                              make_frame_block(b), make_src_pos(b));
    b.Finish(upd);
    const ir::UpdateStatement *root = fb::GetRoot<ir::UpdateStatement>(b.GetBufferPointer());

    SqlGen gen(ctx);
    RellSql sql = gen.render_update(*root, frame);
    check_query("update_path_join", sql,
                "UPDATE \"c0.emp\" A00 SET \"salary\" = $1 FROM \"c0.company\" A01 "
                "WHERE ((A00.\"company\" = A01.\"rowid\") AND A01.\"name\" = $2) "
                "RETURNING A00.\"rowid\"",
                {RellTag::INTEGER, RellTag::INTEGER});
}

// (14) DELETE WHERE — executeDeleteSqlInner golden (lone user-WHERE, no parens, M1).
//   delete user @ { .name == $n }
void test_delete_where(int64_t frame) {
    fb::FlatBufferBuilder b;
    TestCtx ctx;
    ctx.seed_eval(rv_integer(0));  // WHERE name = $1

    auto entity = make_at_entity(b, /*def=*/0, /*id=*/1);
    auto name_attr = make_attr(b, 0, 1, "name", ir::PrimitiveTypeKind_TEXT);
    auto name_bind = make_interpreted(b);
    auto where = make_binary(b, ir::DbBinaryOp_EQ, name_attr, name_bind,
                             ir::PrimitiveTypeKind_BOOLEAN);
    auto del = ir::CreateDeleteStatementDirect(b, entity, /*extra=*/nullptr, where,
                                              make_frame_block(b), make_src_pos(b));
    b.Finish(del);
    const ir::DeleteStatement *root = fb::GetRoot<ir::DeleteStatement>(b.GetBufferPointer());

    SqlGen gen(ctx);
    RellSql sql = gen.render_delete(*root, frame);
    check_query("delete_where", sql,
                "DELETE FROM \"c0.user\" A00 WHERE A00.\"name\" = $1 RETURNING A00.\"rowid\"",
                {RellTag::INTEGER});
}

// (15) DELETE with rel-join (USING + double-paren AND) — executeDeleteSqlInner golden.
//   delete emp @* { .company.name == $n }
void test_delete_path_join(int64_t frame) {
    fb::FlatBufferBuilder b;
    TestCtx ctx;
    ctx.seed_eval(rv_integer(0));  // WHERE name = $1

    auto entity = make_at_entity(b, /*def=*/2, /*id=*/1);  // emp A00
    auto rel = make_rel(b, /*base_def=*/2, /*base_id=*/1, "company", /*target_def=*/1);
    auto attr = make_attr_over_rel(b, rel, "name", ir::PrimitiveTypeKind_TEXT);
    auto name_bind = make_interpreted(b);
    auto where = make_binary(b, ir::DbBinaryOp_EQ, attr, name_bind, ir::PrimitiveTypeKind_BOOLEAN);
    auto del = ir::CreateDeleteStatementDirect(b, entity, /*extra=*/nullptr, where,
                                              make_frame_block(b), make_src_pos(b));
    b.Finish(del);
    const ir::DeleteStatement *root = fb::GetRoot<ir::DeleteStatement>(b.GetBufferPointer());

    SqlGen gen(ctx);
    RellSql sql = gen.render_delete(*root, frame);
    check_query("delete_path_join", sql,
                "DELETE FROM \"c0.emp\" A00 USING \"c0.company\" A01 "
                "WHERE ((A00.\"company\" = A01.\"rowid\") AND A01.\"name\" = $1) "
                "RETURNING A00.\"rowid\"",
                {RellTag::INTEGER});
}

}  // namespace

int main() {
    const int64_t frame = 0;  // no live JVM frame; the test env returns canned values.

    std::cerr << "t1\n"; test_simple_select_where(frame);
    std::cerr << "t2\n"; test_attr_alias_decimal(frame);
    std::cerr << "t3\n"; test_in_list(frame);
    std::cerr << "t4\n"; test_elvis_coalesce(frame);
    std::cerr << "t5\n"; test_exists_subquery(frame);
    std::cerr << "t6\n"; test_when_unkeyed(frame);
    std::cerr << "t7\n"; test_when_keyed_db(frame);
    std::cerr << "t8\n"; test_and_short_circuit(frame);
    std::cerr << "t9\n"; test_nullable_eq_is_null(frame);
    std::cerr << "t10\n"; test_path_join(frame);
    std::cerr << "t11\n"; test_insert();
    std::cerr << "t12\n"; test_update_set_where(frame);
    std::cerr << "t13\n"; test_update_path_join(frame);
    std::cerr << "t14\n"; test_delete_where(frame);
    std::cerr << "t15\n"; test_delete_path_join(frame);

    std::cout << "checks: " << g_checks << ", failures: " << g_failures << "\n";
    if (g_failures == 0) {
        std::cout << "OK\n";
        return 0;
    }
    std::cerr << g_failures << " FAILED\n";
    return 1;
}
