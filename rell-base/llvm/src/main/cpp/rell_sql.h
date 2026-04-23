// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_sql.h — namespace rell::sql: the native SQL string generator for the Rell LLVM
// backend. It walks the FlatBuffer RR-tree DB nodes (DbAtExpr / UpdateStatement /
// DeleteStatement and the 16-variant DbExprUnion) and produces a RellSql = { text, binds }:
// raw parameterized PostgreSQL whose TEXT and BIND ORDER are faithful, byte-for-byte, to the
// JVM interpreter's DbSqlGen (runtime-interpreter/src/runtime/rr_interp_sql_gen.kt) — but
// rendered as direct C++ string building, NO jOOQ-equivalent DSL.
//
// PORT TARGET (the ground-truth Kotlin):
//   - DbSqlGen.dbExprToField          → SqlGen::db_expr_to_sql + the per-variant dispatch
//   - DbSqlGen.buildSelectQuery       → SqlGen::render_db_at  (top-level SELECT)
//   - executeUpdateSqlInner           → SqlGen::render_update
//   - executeDeleteSqlInner           → SqlGen::render_delete
//   - buildInsertSql                  → SqlGen::render_insert (single-row create)
//
// =====================================================================================
// PLACEHOLDER + BIND CONTRACT (consensus-critical)
// =====================================================================================
// jOOQ renders ParameterizedSql with indexed '?' placeholders and a parallel ordered list of
// Rt_Value binds, appended in DSL-tree-traversal order as each Interpreted/value leaf is
// visited. libpq's PQexecParams uses POSITIONAL $1..$N, so this generator emits $1, $2, ... in
// the SAME order the corresponding RellValue is pushed onto `RellSql.binds`. The Nth '?' in the
// jOOQ output is emitted here as "$N" and binds[N-1] is its value. The bind counter is a single
// monotonically-increasing index shared across the whole query INCLUDING correlated subqueries
// (jOOQ shares one bindList across nested DbSqlGen contexts — see createSub / spliceBindsAt).
//
// RENDER-ORDER INVARIANT: binds must be pushed in the exact textual order their placeholders
// appear in the final SQL: SELECT-list → FROM/JOIN-ON (join-where) → WHERE → GROUP BY →
// ORDER BY → LIMIT → OFFSET. DbSqlGen achieves this by (a) capturing per-entity join-where
// Fields' binds into a side buffer (captureJoinWhere) and (b) splicing them back into the
// global bindList at the post-SELECT position (spliceBindsAt / buildSelectQuery's
// joinWhereBindPosition) right before render. The C++ port MUST reproduce that splice: emit the
// SELECT list (advancing the bind index), remember the index, build join-where fragments into a
// side list, then weave their binds in at the remembered index before WHERE.
//
// =====================================================================================
// NAMING RULES (must match DbSqlGen exactly)
// =====================================================================================
// - Table name: RR_EntitySqlMapping.table(sqlCtx) — for REGULAR/EXTERNAL entities this is
//   chainMapping.fullName(mountName) == "c<chainId>." + mountName (e.g. "c0.user"). For
//   TRANSACTION → chainMapping.transactionsTable, BLOCK → chainMapping.blocksTable. The C++
//   side gets all four via SqlContext::table_name(entity_def_index).
// - Alias: raw short token "A%02d" (A00, A01, ...) from a shared monotonic counter, allocated
//   in entity declaration order (root entities first, then rel-join aliases lazily). Aliases are
//   spliced into raw SQL UNQUOTED (they satisfy SAFE_ALIAS_REGEX ^[A-Za-z][A-Za-z0-9_]*$). The
//   C++ allocator is SqlContext::fresh_alias().
// - Column reference: "<alias>.\"<col>\"" — alias bare, column quoted via renderName (jOOQ
//   DSL.name → double-quoted identifier; embedded '"' doubled). See quote_ident below.
// - rowid column: RR_EntitySqlMapping.rowidColumn (usually "rowid") via
//   SqlContext::rowid_column(entity_def_index).
// - Table reference in FROM/USING: "\"<table>\" <alias>" (table quoted, alias unquoted).
// - Keywords UPPER, no formatting (single-space separated), POSTGRES dialect — mirror JOOQ_CTX
//   (rt_jooq_ctx.kt: RenderKeywordCase.UPPER, RenderFormatted(false),
//   RenderQuotedNames.EXPLICIT_DEFAULT_QUOTED, ParamType.INDEXED).
//
// =====================================================================================
// STYLE / DEPENDENCY RULES
// =====================================================================================
// This header (and its .cpp) include ONLY rell_runtime.h + app_generated.h — NO <libpq-fe.h> —
// so SQL string generation is unit-testable against golden-string vectors with no database. The
// libpq executor lives behind rell_pg_executor.h; the type adapters behind rell_sql_types.h.
//
// FIDELITY: where the exact rendering is subtle, the .cpp must mirror rr_interp_sql_gen.kt line
// for line and carry a // FIDELITY: comment. Determinism is non-negotiable (consensus code).

#ifndef RELL_SQL_H
#define RELL_SQL_H

#include <cstdint>
#include <map>
#include <string>
#include <utility>
#include <vector>

#include "app_generated.h"
#include "rell_runtime.h"

namespace ir = rell::ir;

namespace rell::sql {

using rell::llvm_rt::RellValue;

// =====================================================================================
// RellSql — the rendered, parameterized query.
// =====================================================================================
//
// `text` is raw PostgreSQL with $1..$N positional placeholders. `binds[i]` is the value for
// $(i+1). For SELECT, the caller (PgExecutor::exec_select) decodes result columns per the
// at-expression's result types. For INSERT/UPDATE/DELETE the query ends in RETURNING and the
// caller reads the returned rowid (+ snapshot attrs) back.
struct RellSql {
    std::string text;
    std::vector<RellValue> binds;
};

// =====================================================================================
// SqlContext — runtime chain/alias environment, injected so SQL-string generation is testable
// without a live chain. A test double returns canned table names ("c0.user") and a counter; the
// production impl is wired to Rt_SqlContext / Rt_ChainSqlMapping over the JNI boundary.
// =====================================================================================
class SqlContext {
public:
    virtual ~SqlContext() = default;

    // RR_EntitySqlMapping.table(sqlCtx): the fully-qualified, UNquoted table name for the entity
    // at App.entities()[entity_def_index] (e.g. "c0.user"; or the chain's transactions/blocks
    // table for TRANSACTION/BLOCK kinds). The caller quotes it when splicing into SQL.
    virtual std::string table_name(uint32_t entity_def_index) const = 0;

    // RR_EntitySqlMapping.rowidColumn for the entity (usually "rowid"). Returned UNquoted.
    virtual std::string rowid_column(uint32_t entity_def_index) const = 0;

    // Next "A%02d" alias from the shared monotonic counter. One SqlContext == one query's alias
    // space (parent + correlated subqueries share it, matching DbSqlGen.AliasCounter).
    virtual std::string fresh_alias() = 0;

    // mainChainMapping().rowidFunction — the make_rowid() fn name for single-row INSERT
    // (e.g. "c0.make_rowid"). Returned UNquoted; the caller quotes it via DSL.name.
    virtual std::string rowid_function() const = 0;

    // ---- entity-model + JVM-frame hooks (formerly the separate SqlExprEnv interface) ----------
    //
    // These were a separate cross-cast interface in the first attempt; they belong on SqlContext
    // because they reach into the same chain/entity model + live frame the rest of the context
    // describes. The alias map and rel-join registry are NOT here — those are STRUCTURAL per-query
    // state owned by SqlGen (see below), shared across all three TUs through one SqlGen instance.

    // entityDef.strAttributes[attrName]?.sqlMapping ?: attrName — the SQL column name for an
    // attribute. Mirrors the Kotlin `?: expr.attrName` READ-path fallback. (For the UPDATE SET
    // column the Kotlin asserts the attr exists, `!!`; a missing attr is a wiring bug, not a
    // fallback — so the production impl must return the real sqlMapping, never an arbitrary name.)
    virtual std::string attr_sql_mapping(uint32_t entity_def_index, const std::string &attr_name) const = 0;

    // InterpretedEvaluator hook: evaluate a DbInterpreted leaf's R_Expr in the live frame to its
    // bind value (Rt_Interpreter.evaluateExpr; NULL tag for Rell null). reducedCache identity
    // semantics are the impl's responsibility (mirrors DbSqlGen.reducedCache). `frame_handle` is
    // the opaque JVM frame; 0 in golden-string tests that stub canned values.
    virtual RellValue eval_interpreted(const ir::Expr &expr_node, int64_t frame_handle) = 0;

    // Evaluate an R collection expression, appending its elements (iteration order) to `out`.
    virtual void eval_collection(const ir::Expr &expr_node, int64_t frame_handle,
                                 std::vector<RellValue> &out) = 0;

    // tryEvaluateInterpreted(expr): the fully-R-level value of `expr`, or rv_none() if it contains
    // any non-R-level (DB) leaf or evaluation throws. Drives WHICH binds appear, so it is
    // consensus-critical and shares the reduced-cache identity with eval_interpreted.
    virtual RellValue try_evaluate_fully(const ir::DbExpr &expr, int64_t frame_handle) = 0;

    // reduceDbExpr(expr): pre-evaluate + cache R-level Interpreted leaves under `expr` so a later
    // re-emit reuses the value (no re-run of side-effecting R code). No return.
    virtual void reduce_db_expr(const ir::DbExpr &expr, int64_t frame_handle) = 0;

    // Rt_Value.equals-faithful equality for WHEN key folding (C3). DbSqlGen compares
    // `condValue == keyConst`, i.e. Rt_Value.equals: for HANDLE keys (text/enum/entity) this is
    // value equality, NOT jobject identity; for DEC_LONG it ignores scale-only differences
    // (1.0 == 1.00). The production impl routes through the JVM Rt_Value.equals; the test stub
    // can compare inline payloads for the primitive WHEN-key tags it constructs.
    virtual bool values_equal(const RellValue &a, const RellValue &b) const = 0;

    // collection_is_empty(value): emptiness of an already-evaluated Rt_CollectionValue, WITHOUT
    // re-evaluating the R leaf (C5: db_exists must evaluate the leaf exactly once). DbSqlGen casts
    // the single evaluated value to Rt_CollectionValue and reads .isNotEmpty.
    virtual bool collection_is_empty(const RellValue &value) const = 0;

    // ---- snapshot-context UPDATE RETURNING (M4) ----------------------------------------------
    //
    // frame.exeCtx.opCtx.hasSnapshotContext(): a RUNTIME (opCtx) property NOT in the FlatBuffer
    // node. When true, executeUpdateSqlInner appends every entity attribute column to the UPDATE
    // RETURNING list so the post-image can be emitted as a snapshot datum (changing both the SQL
    // text and the executor's decode column count). DELETE RETURNING stays rowid-only in both.
    virtual bool snapshot_active() const = 0;

    // entityDef.strAttributes.values.map { it.sqlMapping } — every attribute's SQL column for the
    // entity, in declaration order. Used to build the snapshot RETURNING list (and the executor's
    // parallel returning_types). Returned UNquoted (the caller quotes via column_ref).
    virtual std::vector<std::string> entity_attr_sql_mappings(uint32_t entity_def_index) const = 0;
};

// =====================================================================================
// SqlGen — walks the FlatBuffer DB nodes into a RellSql.
//
// One SqlGen instance == one top-level query. It owns the bind list, the entity-alias map
// (entity_id → alias + def_index), and the lazily-discovered relationship-JOIN registry. Build
// it, call one of render_db_at / render_update / render_delete / render_insert, then move out
// result(). The walk mirrors DbSqlGen's: each db_expr_* helper appends to `binds_` exactly when
// DbSqlGen.bind() is called, and returns the SQL fragment string ($N placeholders inline).
// =====================================================================================
class SqlGen {
public:
    explicit SqlGen(SqlContext &ctx) : ctx_(ctx) {}

    SqlGen(const SqlGen &) = delete;
    SqlGen &operator=(const SqlGen &) = delete;

    // ---- stateless identifier/render helpers (public so all three gen TUs share ONE copy; m1) --
    // "<alias>.\"<col>\"" — alias bare (must be safe), column quoted via quote_ident.
    static std::string column_ref(const std::string &alias, const std::string &col);
    // Quote a PG identifier: surround with '"', doubling any embedded '"' (jOOQ DSL.name).
    static std::string quote_ident(const std::string &name);
    // "ROUND(<inner>, 20)" — wrapDecimal (Lib_DecimalMath.DECIMAL_FRAC_DIGITS == 20).
    static std::string wrap_decimal(const std::string &inner);

    // ---- top-level entry points -----------------------------------------------------

    // DbAtExpr → "SELECT <what> FROM <entities+joins> [WHERE ...] [GROUP BY ...]
    //            [ORDER BY ...] [LIMIT $n] [OFFSET $n]". Mirrors evaluateDbAt +
    //            DbSqlGen.buildSelectQuery. `frame_handle` is the opaque JVM frame used to
    //            evaluate Interpreted (R-level) leaves into bind values via the
    //            InterpretedEvaluator hook; 0 in golden-string tests that contain no Interpreted
    //            leaves (or use a stub evaluator).
    RellSql render_db_at(const ir::DbAtExpr &at, int64_t frame_handle);

    // UpdateStatement → "UPDATE \"<table>\" <A> SET \"col\" = <expr>, ... [FROM ...]
    //                    [WHERE <joinconds> AND <where>] RETURNING <A>.\"rowid\"[, attrs]".
    //                    Mirrors executeUpdateSqlInner.
    RellSql render_update(const ir::UpdateStatement &upd, int64_t frame_handle);

    // DeleteStatement → "DELETE FROM \"<table>\" <A> [USING ...]
    //                    [WHERE <joinconds> AND <where>] RETURNING <A>.\"rowid\"".
    //                    Mirrors executeDeleteSqlInner.
    RellSql render_delete(const ir::DeleteStatement &del, int64_t frame_handle);

    // Single-row create → "INSERT INTO \"<table>\" (\"rowid\", \"col\", ...)
    //                      VALUES (\"<rowidFn>\"(), $1, $2, ...) RETURNING \"rowid\"".
    //                      Mirrors buildInsertSql. attr_sql_mappings / attr_binds are parallel
    //                      (the resolved column names and their already-evaluated values, in the
    //                      same order DbSqlGen passes them).
    RellSql render_insert(uint32_t entity_def_index, const std::vector<std::string> &attr_sql_mappings,
                          const std::vector<RellValue> &attr_binds);

    // Accessor for the accumulated result (text built into a member by the entry points).
    RellSql result() &&;

    // ---- shared per-query STRUCTURAL state (alias map + rel-join registry) -------------
    //
    // One relationship JOIN discovered while resolving a Rel base. Mirrors DbSqlJoinInfo.
    struct RelJoin {
        std::string table;         // target entity table, UNquoted.
        std::string alias;         // fresh "A%02d" alias for the joined table.
        std::string base_alias;    // alias of the base table holding the FK column.
        std::string base_column;   // FK column on the base table, UNquoted.
        std::string target_rowid;  // rowid column of the target table, UNquoted.
    };

    // Alias + def-index for one entity occurrence. Mirrors DbSqlAliasInfo.
    struct EntityAlias {
        std::string alias;
        uint32_t entity_def_index;
    };

    // One SELECT/sub-query scope. Mirrors a DbSqlGen instance's mutable fields. Scopes share the
    // alias counter (ctx_.fresh_alias) and the bind list (binds_); only the alias/join MAPS are
    // per-scope, with `parent` for the correlated-subquery alias walk (resolveEntityAlias).
    struct Scope {
        std::map<uint32_t, EntityAlias> entity_aliases;                            // entity_id -> alias
        std::map<std::pair<std::string, std::string>, EntityAlias> rel_join_aliases;  // (baseAlias,attr)
        std::map<std::string, std::vector<RelJoin>> entity_rel_joins;              // rootAlias -> joins
        // Root aliases in FIRST-JOIN insertion order — mirrors DbSqlGen.entityRelJoins being a
        // LinkedHashMap (getAllRelJoins flattens by insertion == declaration order). Used by
        // rel_joins(); robust where a plain std::map key sort would diverge (>99 aliases).
        std::vector<std::string> rel_join_root_order;
        std::map<std::string, std::string> alias_to_root;                          // alias -> rootAlias
        Scope *parent = nullptr;
    };

    // ---- structural-state accessors used by all three TUs (expr/select/write) ----------

    // DbSqlGen.init / write-path ctor: allocate a fresh alias for `entity_id` in declaration order
    // and seed it as its own root. Idempotent per entity_id within a scope.
    void register_entity(uint32_t entity_id, uint32_t entity_def_index);

    // resolveEntityAlias(entityId): the alias for an at-entity, walking the parent chain for
    // correlated subqueries. `out_def_index` receives its def index. Aborts on a wiring bug
    // (checkNotNull).
    std::string resolve_entity_alias(uint32_t entity_id, uint32_t &out_def_index);

    // getEntityAlias(entityId): the alias bound to a root entity in the CURRENT scope (no parent
    // walk) — the write paths' analogue of DbSqlGen.getEntityAlias.
    std::string get_entity_alias(uint32_t entity_id) const;

    // getEntityRelJoins(rootAlias): the rel-JOINs stemming from a root entity's alias (for the
    // SELECT FROM clause's structural-join composition). Empty when none.
    const std::vector<RelJoin> &entity_rel_joins(const std::string &root_alias) const;

    // getAllRelJoins(): every rel-JOIN across all root entities in this scope, in declaration
    // order (insertion order of entity_rel_joins values, flattened) — the write paths' FROM/USING
    // list + join-condition source.
    std::vector<RelJoin> rel_joins() const;

private:
    // ---- DbExprUnion dispatch (one private method per of the 16 variants) ------------
    //
    // db_expr_to_sql pattern-matches dbexpr.expr_type() and delegates. `enclose` requests outer
    // parentheses for operator-precedence preservation (DbSqlGen's `enclose` param): top-level
    // callers pass false, binary/unary operands pass true. Each helper appends to binds_ in
    // tree order and returns the rendered fragment.
    std::string db_expr_to_sql(const ir::DbExpr &expr, int64_t frame_handle, bool enclose);

    // DbInterpretedExpr: evaluate the embedded R_Expr at frame → a value; push it as a bind and
    // emit "$N", OR emit literal "NULL" when the value is Rt_NullValue. (DbSqlGen: bind(value) /
    // NULL_FIELD.)
    std::string db_interpreted(const ir::DbInterpretedExpr &e, int64_t frame_handle);

    // DbBinaryExpr: "({L} OP {R})" (or unparenthesized at top level). OP comes from
    // db_binary_op_sql(e.op()). Handles the nullable-eq collapse (e.nullable_eq()): EQ_NULL with
    // a known-NULL side → "{x} IS NULL"; NE_NULL → "{x} IS NOT NULL"; AND/OR short-circuit on
    // R-level-constant operands. "FN:" prefix → "fn({L}, {R})". (DbSqlGen.binaryToField.)
    std::string db_binary(const ir::DbBinaryExpr &e, int64_t frame_handle, bool enclose);

    // DbUnaryExpr: "OP {x}" / "(OP {x})". OP from db_unary_op_sql(e.op()) ("-" or "NOT").
    std::string db_unary(const ir::DbUnaryExpr &e, int64_t frame_handle, bool enclose);

    // DbEntityExpr: the entity's rowid column → "<alias>.\"<rowidCol>\"". (resolveEntityAlias.)
    std::string db_entity(const ir::DbEntityExpr &e);

    // DbAttrExpr: "<alias>.\"<sqlCol>\"" for base entity's attribute; wrapped in
    // "ROUND(<col>, 20)" when type is DECIMAL (wrapDecimal, DECIMAL_FRAC_DIGITS=20). Resolving
    // the base may lazily register a rel-JOIN (resolveTableExpr on a DbRelExpr base).
    std::string db_attr(const ir::DbAttrExpr &e);

    // DbRelExpr: FK column on the BASE table — "<baseAlias>.\"<fkCol>\"" — no JOIN forced. Also
    // resolveTableExpr registers the rel-JOIN if the value (not just the FK) is later needed.
    std::string db_rel(const ir::DbRelExpr &e);

    // DbRowidExpr: optimization — Rowid(Rel(base, attr, target)) reads the FK column from the
    // base table directly (no JOIN); otherwise the resolved table's rowid column.
    std::string db_rowid(const ir::DbRowidExpr &e);

    // DbCollectionInterpretedExpr: evaluate the R collection, bind each element, emit
    // "($a,$b,...)" (tupleField; "()" if empty).
    std::string db_collection_interpreted(const ir::DbCollectionInterpretedExpr &e, int64_t frame_handle);

    // DbInExpr: "{key} IN (tuple)" / "{key} NOT IN (tuple)". Empty static list short-circuits to
    // the boolean literal "TRUE"/"FALSE" per not_() (PG rejects "IN ()").
    std::string db_in(const ir::DbInExpr &e, int64_t frame_handle);

    // DbElvisExpr: "COALESCE({L}, {R})" — or, when L is an R-level constant, collapses to {R}
    // (L==NULL) or {L} (L!=NULL). (elvisField.)
    std::string db_elvis(const ir::DbElvisExpr &e, int64_t frame_handle);

    // DbCallExpr: Db_SysFunction. e.fn_name() is the encoded template: if it contains "#{" it is
    // a Template (substitute "#{i}" fragments with rendered args); else it is Simple → render
    // "<fn_name>(arg0, arg1, ...)" (args joined ", "). Wrap in ROUND(...,20) if type is DECIMAL.
    // (callField; deserializeDbSysFn parse — see render_db_sysfn below.)
    std::string db_call(const ir::DbCallExpr &e, int64_t frame_handle);

    // DbExistsExpr: "EXISTS <subquery>" / "NOT EXISTS <subquery>" when sub is a SubQuery;
    // R-level collection-at evaluates to a TRUE/FALSE literal; else "EXISTS({x})". (existsField.)
    std::string db_exists(const ir::DbExistsExpr &e, int64_t frame_handle);

    // DbInCollectionExpr: evaluate the R collection on the right; "{left} IN (binds...)" /
    // "NOT IN"; empty collection → "TRUE"/"FALSE" literal per not_(). (inCollectionField.)
    std::string db_in_collection(const ir::DbInCollectionExpr &e, int64_t frame_handle);

    // DbWhenExpr: CASE. Keyed (key_expr() present) vs unkeyed; R-level-constant key/cond folding
    // mirrors whenField/keyedWhen*/unkeyedWhen exactly. Emits "CASE WHEN <cond> THEN <then> ...
    // [ELSE <else>] END", with cond shape "{key} = {c}" / "{key} IN (c1, c2)" / "{x} IS NULL".
    std::string db_when(const ir::DbWhenExpr &e, int64_t frame_handle, bool enclose);

    // DbNestedAtExpr: transparent — render inner(). (NestedAt → dbExprToField(inner).)
    std::string db_nested_at(const ir::DbNestedAtExpr &e, int64_t frame_handle, bool enclose);

    // DbSubQueryExpr: recursively render "(SELECT ... FROM ... [WHERE ...] [GROUP BY ...]
    // [ORDER BY ...] [LIMIT/OFFSET])" into a child SqlGen sharing this->binds_ and alias counter.
    // (subQueryField / subQueryParameterizedSql / renderSubQuerySql.)
    std::string db_sub_query(const ir::DbSubQueryExpr &e, int64_t frame_handle);

    // ---- shared SELECT-body renderer (top-level DbAt + subquery) ----------------------
    // Renders the FROM clause (entity tables + composed rel-JOINs + join-where ON / CROSS JOIN),
    // weaves the pre-captured join-where binds at `join_where_bind_pos`, appends WHERE / GROUP BY
    // / ORDER BY / LIMIT / OFFSET. Mirrors buildSelectQueryAst + addFromTo + renderLimitOffset.

    // ---- operator string tables (port of RROpDeserializer.FB_TO_DB_*_OP) --------------
    // The FlatBuffer DbBinaryOp/DbUnaryOp enums map to the SAME SQL operator strings DbSqlGen
    // consumes. Defined in the .cpp:
    //   db_binary_op_sql(ir::DbBinaryOp): LT"<" GT">" LE"<=" GE">=" AND"AND" OR"OR"
    //     ADD_*"+" SUB_*"-" MUL_*"*" DIV_*"/" MOD_*"%" CONCAT"||" IN"IN" NOT_IN"NOT IN"
    //     EQ"=" NE"<>" EQ_NULL"IS NOT DISTINCT FROM" NE_NULL"IS DISTINCT FROM".
    //   db_unary_op_sql(ir::DbUnaryOp): MINUS_*"-" NOT"NOT".
    static const char *db_binary_op_sql(ir::DbBinaryOp op);
    static const char *db_unary_op_sql(ir::DbUnaryOp op);

    // resolveTableExpr (DbSqlGen): resolve a Db base (Entity or Rel) to (alias, def_index). A Rel
    // base lazily registers a relationship JOIN (cached by (baseAlias, attrName)) into the current
    // scope's entity_rel_joins, attached to the base's root entity. Returns the resolved alias and
    // writes its def index. Shared by db_attr / db_rel / db_rowid (rell_sql_expr.cpp).
    std::pair<std::string, uint32_t> resolve_table_expr(const ir::DbExpr &expr);

    // findOwnerForAlias: the scope in the parent chain that owns `alias` (for correlated-subquery
    // join ownership). Returns the current scope when the alias is not found upstream.
    Scope &find_owner_for_alias(const std::string &alias);

    // ---- low-level emit helpers -------------------------------------------------------
    // Push a value bind and return its "$N" placeholder (N = binds_.size() after push).
    std::string bind(const RellValue &v);

    SqlContext &ctx_;
    std::vector<RellValue> binds_;
    std::string text_;

    // The current SELECT/sub-query scope (alias map + rel-join registry). The top-level entry
    // points seed `root_scope_` and point `scope_` at it; db_sub_query pushes a child scope whose
    // parent is the enclosing one, then restores. NOT copyable (raw parent pointers).
    Scope root_scope_;
    Scope *scope_ = &root_scope_;
};

// The InterpretedEvaluator hook the SQL generator needs (evaluate an RR_DbExpr.Interpreted /
// CollectionInterpreted leaf's R_Expr in the live frame to its bind value, plus the R-level
// constant-fold + value-equality hooks) now lives on SqlContext above: eval_interpreted /
// eval_collection / try_evaluate_fully / reduce_db_expr / values_equal / collection_is_empty.
// Production wiring routes them to the JVM (rell_db_eval_expr); the golden-string test stubs them.

}  // namespace rell::sql

#endif  // RELL_SQL_H
