// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_sql_write.cpp — namespace rell::sql: the native renderers for the three Rell write paths,
// the C++ port of rr_interp_db_write.kt:
//
//   SqlGen::render_insert   <- buildInsertSql           (single-row create)
//   SqlGen::render_update   <- executeUpdateSqlInner    (UPDATE ... SET ... [FROM ...] WHERE ...)
//   SqlGen::render_delete   <- executeDeleteSqlInner    (DELETE FROM ... [USING ...] WHERE ...)
//
// Each produces a RellSql { text, binds } whose SQL TEXT and BIND ORDER are byte-for-byte faithful
// to what jOOQ renders for the same statement under JOOQ_CTX (rt_jooq_ctx.kt: POSTGRES dialect,
// keywords UPPER, not formatted, EXPLICIT_DEFAULT_QUOTED identifiers, INDEXED `?` -> here `$N`).
//
// GOLDEN REFERENCE (rell-base/src/test/kotlin/sql/SqlEmissionTest.kt) — the exact strings this file
// reproduces ($N where jOOQ shows ?):
//
//   INSERT INTO "c0.user" ("rowid", "name", "firstName", "lastName", "score")
//     VALUES ("c0.make_rowid"(), $1, $2, $3, $4) RETURNING "rowid"
//   UPDATE "c0.user" A00 SET "score" = $1 WHERE A00."name" = $2 RETURNING A00."rowid"
//   UPDATE "c0.user" A00 SET "firstName" = $1, "lastName" = $2, "score" = $3
//     WHERE A00."name" = $4 RETURNING A00."rowid"
//   UPDATE "c0.emp" A00 SET "salary" = $1 FROM "c0.company" A01
//     WHERE ((A00."company" = A01."rowid") AND A01."name" = $2) RETURNING A00."rowid"
//   DELETE FROM "c0.user" A00 WHERE A00."name" = $1 RETURNING A00."rowid"
//   DELETE FROM "c0.emp" A00 USING "c0.company" A01
//     WHERE ((A00."company" = A01."rowid") AND A01."name" = $1) RETURNING A00."rowid"
//   DELETE FROM "c0.data" A00 RETURNING A00."rowid"
//
// BIND ORDER (consensus-critical): mirrors the Kotlin construction order exactly:
//   - UPDATE: SET value exprs (in `what()` order) -> user WHERE. Building the SET value Fields
//     FIRST is what lazily registers any relationship JOINs, so their tables/aliases exist before
//     the FROM/USING list is emitted. The join *conditions* carry no binds (pure column equality),
//     so weaving them into WHERE does not perturb the SET->WHERE positional order.
//   - DELETE: user WHERE only (no SET).
//   - INSERT: attr value binds in attr_sql_mappings order (the rowid fn takes no bind).
//
// DEPENDENCY RULES: includes ONLY rell_runtime.h + app_generated.h (via rell_sql.h) — NO libpq —
// so the write-path string generation is unit-testable against golden vectors with no database.

#include <string>
#include <vector>

#include "rell_sql.h"

namespace rell::sql {

// =====================================================================================
// SqlGen coordination surface (B2): the write paths share ONE SqlGen state with the SELECT
// renderer + the DbExpr dispatch:
//   - register_entity(id, def)     : seed the alias map (main + extra entities; DbSqlGen.init).
//   - get_entity_alias(id)         : the "A%02d" alias of a root entity (DbSqlGen.getEntityAlias).
//   - rel_joins()                  : the rel-JOINs lazily discovered while rendering SET/WHERE
//                                    DbExprs (DbSqlGen.getAllRelJoins), in declaration order.
//   - ctx_.attr_sql_mapping(def,n) : entityDef.strAttributes[n].sqlMapping (SqlContext).
// All declared on SqlGen / SqlContext in rell_sql.h; the dispatch (rell_sql_expr.cpp) populates
// the rel-join registry via SqlGen::resolve_table_expr as it renders the SET/WHERE value Fields.
// =====================================================================================

namespace {

// Renders the WHERE predicate list exactly as jOOQ combines `q.addConditions(...)` calls under
// JOOQ_CTX. Faithful to the SqlEmissionTest golden strings:
//
//   - 0 conditions            -> "" (no WHERE clause is emitted by the caller).
//   - 1 condition             -> the predicate verbatim, NO surrounding parentheses
//                                  e.g.  A00."name" = $2
//   - >= 2 conditions         -> "(" + c0 + " AND " + c1 + " AND " + ... + ")"
//                                  e.g.  ((A00."company" = A01."rowid") AND A01."name" = $2)
//
// where each relationship-join condition `ci` is itself the raw-SQL predicate
// "(<base_alias>.\"<base_column>\" = <join_alias>.\"<target_rowid>\")" — jOOQ wraps a raw
// DSL.condition("{0} = {1}", ...) operand of an AND in its own parentheses, while the user-WHERE
// predicate (a rendered Field) is spliced in WITHOUT extra parentheses (matching the golden
// `... AND A01."name" = $2`). The join conditions are emitted FIRST (declaration order), then the
// single user-WHERE predicate last — exactly the order DbSqlGen calls addConditions().
//
// FIDELITY (M1): the parenthesization asymmetry (join conds parenthesized, user-WHERE not) is
// jOOQ's internal AND-operand rendering: a raw `DSL.condition("{0} = {1}", ...)` operand of an
// AND is self-parenthesized, but a LONE WHERE condition is NOT. So the per-join `"(" + jc + ")"`
// must be applied ONLY when the condition is combined with others (>= 2 total) — a single join
// cond with no user WHERE renders bare `A00."company" = A01."rowid"` (matching jOOQ's
// q.addConditions(cond) once). The user-WHERE field is spliced WITHOUT extra parens either way.
std::string combine_where(const std::vector<std::string> &join_conds, const std::string *user_where) {
    const size_t total = join_conds.size() + (user_where != nullptr ? 1 : 0);
    if (total == 0) return std::string();

    if (total == 1) {
        // Lone condition: bare, no parentheses (jOOQ renders a single Condition unparenthesized).
        return join_conds.empty() ? *user_where : join_conds[0];
    }

    // >= 2 conditions: each raw join cond parenthesized, the user-WHERE field bare, AND-joined and
    // wrapped — e.g. ((A00."company" = A01."rowid") AND A01."name" = $2).
    std::string out = "(";
    bool first = true;
    for (const auto &jc : join_conds) {
        if (!first) out += " AND ";
        out += "(" + jc + ")";
        first = false;
    }
    if (user_where != nullptr) {
        if (!first) out += " AND ";
        out += *user_where;
    }
    out += ")";
    return out;
}

}  // namespace

// =====================================================================================
// SqlGen::render_insert — port of buildInsertSql.
//
// jOOQ InsertQuery rendering under JOOQ_CTX produces (golden):
//   INSERT INTO "<table>" ("<rowidCol>", "<col1>", ...) VALUES ("<rowidFn>"(), $1, $2, ...)
//     RETURNING "<rowidCol>"
//
// Layout notes matched verbatim:
//   - one space between the quoted table name and the `(` column list.
//   - column list is ", "-separated, each identifier double-quoted.
//   - the rowid column is the FIRST column; its VALUES entry is the rowid fn call `"<fn>"()`
//     (quoted fn name, no bind), NOT a placeholder.
//   - each attribute is a `$N` placeholder bound to attr_binds in attr_sql_mappings order.
//   - RETURNING is the UNqualified, quoted rowid column (no alias — INSERT has no table alias).
//
// attr_sql_mappings and attr_binds are parallel and pre-resolved by the caller (the interpreter
// evaluates the attribute values; mirrors buildInsertSql's attrSqlMappings / attrValues args).
// =====================================================================================
RellSql SqlGen::render_insert(uint32_t entity_def_index,
                              const std::vector<std::string> &attr_sql_mappings,
                              const std::vector<RellValue> &attr_binds) {
    const std::string table = ctx_.table_name(entity_def_index);
    const std::string rowid_col = ctx_.rowid_column(entity_def_index);
    const std::string rowid_fn = ctx_.rowid_function();

    text_ = "INSERT INTO ";
    text_ += quote_ident(table);
    text_ += " (";
    text_ += quote_ident(rowid_col);
    for (const auto &col : attr_sql_mappings) {
        text_ += ", ";
        text_ += quote_ident(col);
    }
    text_ += ") VALUES (";
    // rowid fn call — quoted fn name + "()". (DSL.field("{0}()", DSL.name(rowidFn)).)
    text_ += quote_ident(rowid_fn);
    text_ += "()";
    for (const auto &v : attr_binds) {
        text_ += ", ";
        text_ += bind(v);  // pushes the bind and returns "$N" in attr_sql_mappings order.
    }
    text_ += ") RETURNING ";
    text_ += quote_ident(rowid_col);

    return std::move(*this).result();
}

// =====================================================================================
// SqlGen::render_update — port of executeUpdateSqlInner.
//
// Produces (golden):
//   UPDATE "<table>" <A00> SET "<col>" = <expr>, ... [FROM "<extraTable>" <Ax>, "<joinTable>" <Ay>]
//     [WHERE <joinConds + userWhere>] RETURNING <A00>."<rowidCol>"[, <A00>."<attr>" ...]
//
// Ordering mirrors the Kotlin exactly:
//   1. Seed the alias map for the main entity then the extra entities (DbSqlGen ctor / init).
//   2. Build the SET value fragments (db_expr_to_sql per what.expr) — this populates lazy rel-joins
//      AND appends the SET value binds first.
//   3. Build the user WHERE fragment (if any) — populates any further rel-joins, appends its binds
//      after the SET binds.
//   4. Collect rel-join conditions (joinConditionsAsConditions) — no binds.
//   5. Emit FROM list: extra entities (declaration order) then rel-join tables (declaration order).
//   6. Emit WHERE = combine(joinConds, userWhere).
//   7. RETURNING the main alias' rowid, plus all attrs when the snapshot context is active.
//
// FIDELITY: the SET clause is ", "-separated `"<col>" = <expr>` pairs; jOOQ does NOT wrap the
// individual SET value expressions in extra parentheses (golden: `SET "score" = $1`,
// `SET "score" = A00."score" * $1`). The value fragment is rendered with enclose=false (top level).
// =====================================================================================
RellSql SqlGen::render_update(const ir::UpdateStatement &upd, int64_t frame_handle) {
    const ir::DbAtEntity *main_entity = upd.entity();
    const uint32_t main_def_index = main_entity->entity_def_index();
    const uint32_t main_id = main_entity->id();

    // (1) Seed aliases: main entity first, then extras — declaration order (DbSqlGen.init).
    register_entity(main_id, main_def_index);
    const auto *extras = upd.extra_entities();
    if (extras != nullptr) {
        for (uint32_t i = 0; i < extras->size(); ++i) {
            const ir::DbAtEntity *ex = extras->Get(i);
            register_entity(ex->id(), ex->entity_def_index());
        }
    }

    const std::string main_alias = get_entity_alias(main_id);

    // (2) SET clause — build value fragments first (populates lazy rel-joins + SET binds).
    const auto *what = upd.what();
    std::vector<std::string> set_cols;
    std::vector<std::string> set_vals;
    set_cols.reserve(what->size());
    set_vals.reserve(what->size());
    for (uint32_t i = 0; i < what->size(); ++i) {
        const ir::UpdateStatementWhat *w = what->Get(i);
        // FIDELITY: DbSqlGen looks the column up via entityDef.strAttributes[w.attrName].sqlMapping
        // (NOT a raw attr_name fallback for UPDATE — the attr is asserted to exist, `!!`). The
        // SqlContext resolves the attr's sqlMapping for the main entity (see the attr_sql_mapping
        // coordination note above); the no-DB test ctx returns a canned mapping.
        set_cols.push_back(ctx_.attr_sql_mapping(main_def_index, w->attr_name()->str()));
        set_vals.push_back(db_expr_to_sql(*w->expr(), frame_handle, /*enclose=*/false));
    }

    // (3) User WHERE — built AFTER SET so its binds follow the SET binds; populates more rel-joins.
    const ir::DbExpr *where = upd.where();
    std::string user_where_str;
    bool has_user_where = (where != nullptr);
    if (has_user_where) {
        user_where_str = db_expr_to_sql(*where, frame_handle, /*enclose=*/false);
    }

    // (4) Relationship-join conditions (no binds): base."col" = join."rowid", declaration order.
    std::vector<std::string> join_conds;
    for (const auto &j : rel_joins()) {
        join_conds.push_back(column_ref(j.base_alias, j.base_column) + " = " +
                             column_ref(j.alias, j.target_rowid));
    }

    // ---- assemble the statement text ----
    const std::string table = ctx_.table_name(main_def_index);
    text_ = "UPDATE ";
    text_ += quote_ident(table);
    text_ += " ";
    text_ += main_alias;
    text_ += " SET ";
    for (size_t i = 0; i < set_cols.size(); ++i) {
        if (i > 0) text_ += ", ";
        text_ += quote_ident(set_cols[i]);
        text_ += " = ";
        text_ += set_vals[i];
    }

    // (5) FROM list — extra entities then rel-join tables, each "<table> <alias>" (declaration
    // order). UPDATE uses FROM (DELETE uses USING; see render_delete).
    std::vector<std::string> from_refs;
    if (extras != nullptr) {
        for (uint32_t i = 0; i < extras->size(); ++i) {
            const ir::DbAtEntity *ex = extras->Get(i);
            from_refs.push_back(quote_ident(ctx_.table_name(ex->entity_def_index())) + " " +
                                get_entity_alias(ex->id()));
        }
    }
    for (const auto &j : rel_joins()) {
        from_refs.push_back(quote_ident(j.table) + " " + j.alias);
    }
    if (!from_refs.empty()) {
        text_ += " FROM ";
        for (size_t i = 0; i < from_refs.size(); ++i) {
            if (i > 0) text_ += ", ";
            text_ += from_refs[i];
        }
    }

    // (6) WHERE = join conditions + user where.
    const std::string where_clause =
        combine_where(join_conds, has_user_where ? &user_where_str : nullptr);
    if (!where_clause.empty()) {
        text_ += " WHERE ";
        text_ += where_clause;
    }

    // (7) RETURNING the main alias' rowid; + every attr when the snapshot context is active.
    // FIDELITY (M4): when frame.exeCtx.opCtx.hasSnapshotContext() is true (a runtime opCtx property
    // surfaced by SqlContext, NOT in the FlatBuffer node) the Kotlin appends every entity attribute
    // column (aliasedCol(mainAlias, attr.sqlMapping)) in declaration order so the post-image can be
    // emitted as a snapshot datum — changing both the SQL text and the executor's decode column
    // count. DELETE RETURNING stays rowid-only (see render_delete).
    const std::string rowid_col = ctx_.rowid_column(main_def_index);
    text_ += " RETURNING ";
    text_ += column_ref(main_alias, rowid_col);
    if (ctx_.snapshot_active()) {
        for (const auto &col : ctx_.entity_attr_sql_mappings(main_def_index)) {
            text_ += ", ";
            text_ += column_ref(main_alias, col);
        }
    }

    return std::move(*this).result();
}

// =====================================================================================
// SqlGen::render_delete — port of executeDeleteSqlInner.
//
// Produces (golden):
//   DELETE FROM "<table>" <A00> [USING "<extraTable>" <Ax>, "<joinTable>" <Ay>]
//     [WHERE <joinConds + userWhere>] RETURNING <A00>."<rowidCol>"
//
// Same structure as UPDATE minus the SET clause; bind order is user-WHERE only. The extra/rel-join
// tables go into USING (not FROM). RETURNING is always just the main alias' rowid (delete snapshot
// only needs the rowids; no attrs). Ordering mirrors the Kotlin: build user WHERE first (populates
// lazy rel-joins), then collect join conditions, then assemble.
// =====================================================================================
RellSql SqlGen::render_delete(const ir::DeleteStatement &del, int64_t frame_handle) {
    const ir::DbAtEntity *main_entity = del.entity();
    const uint32_t main_def_index = main_entity->entity_def_index();
    const uint32_t main_id = main_entity->id();

    register_entity(main_id, main_def_index);
    const auto *extras = del.extra_entities();
    if (extras != nullptr) {
        for (uint32_t i = 0; i < extras->size(); ++i) {
            const ir::DbAtEntity *ex = extras->Get(i);
            register_entity(ex->id(), ex->entity_def_index());
        }
    }

    const std::string main_alias = get_entity_alias(main_id);

    // User WHERE first so rel-joins are populated before we collect their conditions / USING tables.
    const ir::DbExpr *where = del.where();
    std::string user_where_str;
    bool has_user_where = (where != nullptr);
    if (has_user_where) {
        user_where_str = db_expr_to_sql(*where, frame_handle, /*enclose=*/false);
    }

    std::vector<std::string> join_conds;
    for (const auto &j : rel_joins()) {
        join_conds.push_back(column_ref(j.base_alias, j.base_column) + " = " +
                             column_ref(j.alias, j.target_rowid));
    }

    const std::string table = ctx_.table_name(main_def_index);
    text_ = "DELETE FROM ";
    text_ += quote_ident(table);
    text_ += " ";
    text_ += main_alias;

    // USING list — extra entities then rel-join tables (declaration order).
    std::vector<std::string> using_refs;
    if (extras != nullptr) {
        for (uint32_t i = 0; i < extras->size(); ++i) {
            const ir::DbAtEntity *ex = extras->Get(i);
            using_refs.push_back(quote_ident(ctx_.table_name(ex->entity_def_index())) + " " +
                                 get_entity_alias(ex->id()));
        }
    }
    for (const auto &j : rel_joins()) {
        using_refs.push_back(quote_ident(j.table) + " " + j.alias);
    }
    if (!using_refs.empty()) {
        text_ += " USING ";
        for (size_t i = 0; i < using_refs.size(); ++i) {
            if (i > 0) text_ += ", ";
            text_ += using_refs[i];
        }
    }

    const std::string where_clause =
        combine_where(join_conds, has_user_where ? &user_where_str : nullptr);
    if (!where_clause.empty()) {
        text_ += " WHERE ";
        text_ += where_clause;
    }

    const std::string rowid_col = ctx_.rowid_column(main_def_index);
    text_ += " RETURNING ";
    text_ += column_ref(main_alias, rowid_col);

    return std::move(*this).result();
}

}  // namespace rell::sql
