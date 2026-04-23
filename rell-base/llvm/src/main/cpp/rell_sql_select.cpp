// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_sql_select.cpp — namespace rell::sql: the SELECT-body renderer for the native SQL
// generator. C++ port of the two read-path entry points in DbSqlGen (rr_interp_sql_gen.kt)
// plus the at-expression driver in rr_interp_db_at.kt:
//
//   SqlGen::render_db_at   <-  Rt_InterpreterImpl.evaluateDbAt (SQL half) + DbSqlGen.buildSelectQuery
//   SqlGen::db_sub_query   <-  DbSqlGen.subQueryParameterizedSql / renderSubQuerySql /
//                              buildSelectQueryAst / addFromTo / renderLimitOffsetSuffix
//
// Both funnel through file-local render_select_body(), which mirrors buildSelectQueryAst +
// addFromTo + renderLimitOffsetSuffix exactly, INCLUDING the consensus-critical join-where bind
// re-splice (captureJoinWhere / spliceBindsAt / buildSelectQuery's joinWhereBindPosition).
//
// =====================================================================================
// BIND / PLACEHOLDER RENDER ORDER (consensus-critical, mirrors DbSqlGen)
// =====================================================================================
// jOOQ appends a bind to its shared bindList the instant it renders a `?`; the rendered SQL's
// Nth `?` is binds[N-1]. The native side emits "$N" with N == binds.size() AFTER the push (see
// SqlGen::bind, rell_sql.cpp). The textual placeholder order MUST be:
//
//     SELECT-list -> FROM/JOIN-ON (join-where) -> WHERE -> GROUP BY -> ORDER BY -> LIMIT -> OFFSET
//
// DbSqlGen reaches this WITHOUT rendering FROM first: it (1) renders SELECT (advancing the bind
// index), (2) records joinWhereBindPosition = current bind count, (3) builds the per-entity
// join-where fragments into a side buffer whose binds are pulled OUT of the global list by
// captureJoinWhere (this also registers the rel-JOINs those fragments reference, BEFORE SELECT),
// (4) renders WHERE / GROUP BY / ORDER BY (appending binds), then (5) splices the captured
// join-where binds back into the global list AT joinWhereBindPosition before the final render.
// The FROM text (with the join-where ON predicates) is assembled last; its `$N` line up because
// the splice re-inserts the join-where binds at the post-SELECT slot.
//
// This file reproduces that splice precisely: capture join-where fragments + binds FIRST (so
// rel-JOINs register in Kotlin order, with their binds pulled out), render SELECT and record the
// position, render WHERE/GROUP BY/ORDER BY, then std::vector::insert the join-where binds at the
// recorded position. Because we emit "$N" eagerly, the FROM-clause join-where fragments are
// rendered AGAINST the live bind list at capture time and re-numbered when spliced — see the
// renumber note at capture_join_where().
//
// =====================================================================================
// STYLE / DEPENDENCY RULES
// =====================================================================================
// Includes ONLY rell_sql.h (which pulls rell_runtime.h + app_generated.h) — NO libpq — so the
// SELECT renderer is unit-testable against golden-string vectors with stub SqlContext /
// InterpretedEvaluator and no database.

#include "rell_sql.h"

#include <cstdint>
#include <functional>
#include <map>
#include <string>
#include <utility>
#include <vector>

namespace rell::sql {

using rell::llvm_rt::RellTag;

namespace {

// =====================================================================================
// SHARED PER-QUERY STATE (B2). The entity-alias map + rel-join registry that the DbExpr dispatch
// (rell_sql_expr.cpp), this SELECT renderer, and the write paths (rell_sql_write.cpp) all touch
// now live as STRUCTURAL members on SqlGen (SqlGen::Scope, reached via SqlGen::scope_). This TU
// implements the SqlGen structural accessors (register_entity / resolve_entity_alias /
// resolve_table_expr / entity_rel_joins / rel_joins / find_owner_for_alias) so all three TUs share
// ONE instance. No more file-local SqlGenState. The SELECT-body helpers below take the SqlGen by
// reference and read its scope_ directly.
// =====================================================================================

using RelJoin = SqlGen::RelJoin;

// A captured join-where: rendered SQL + the binds produced building it. Mirrors JoinWhereSpec.
struct JoinWhereSpec {
    std::string sql;
    std::vector<RellValue> binds;
};

// One rendered ORDER BY entry. ASC emits the bare field (no `ASC` keyword); DESC appends ` DESC`.
struct OrderByEntry {
    std::string sql;
    bool desc;
};

// The DbExpr -> SQL dispatch callable. Bridges to SqlGen::db_expr_to_sql via a lambda the member
// entry points install (capturing `this`). Signature mirrors db_expr_to_sql(expr, enclose).
using DbExprDispatch = std::function<std::string(const ir::DbExpr &, bool enclose)>;

// "<table-quoted> <alias>" — FROM/JOIN table reference (aliasedTable). Table quoted, alias bare.
// quote_ident/column_ref are the SqlGen statics (m1: ONE source of truth, byte-identical to the
// dispatch TU).
std::string aliased_table(const std::string &table, const std::string &alias) {
    return SqlGen::quote_ident(table) + " " + alias;
}

// "<base>.\"<col>\" = <ja>.\"<rowid>\"" — a rel-JOIN ON predicate.
std::string join_on_condition(const RelJoin &j) {
    return SqlGen::column_ref(j.base_alias, j.base_column) + " = " +
           SqlGen::column_ref(j.alias, j.target_rowid);
}

// Structural bind equality for ORDER BY de-dup (distinctBy over (sql, params)). Two binds match
// iff same tag, scale, and i64 payload. HANDLE binds compare by jobject identity — adequate here
// because ORDER BY de-dup only sees the SAME RellValue produced for two literal subscripts (e.g.
// `.firstName[0]` vs `.firstName[1]`); identical inline ints differ by payload, distinct handles
// differ by ref. FIDELITY: matches the Kotlin (sql, params) de-dup intent.
bool binds_equal(const std::vector<RellValue> &a, const std::vector<RellValue> &b) {
    if (a.size() != b.size()) return false;
    for (size_t i = 0; i < a.size(); i++) {
        if (a[i].tag != b[i].tag || a[i].scale != b[i].scale) return false;
        if (a[i].tag == RellTag::HANDLE) {
            if (a[i].payload.handle != b[i].payload.handle) return false;
        } else if (a[i].payload.i64 != b[i].payload.i64) {
            return false;
        }
    }
    return true;
}

// --------------------------------------------------------------------------------------
// allocate_aliases — root/inner entities, in declaration order, from the SHARED counter. Mirrors
// the DbSqlGen init{} block (and createSub's init for subqueries). Rel-JOIN aliases are allocated
// lazily later by the dispatch (resolveTableExpr) against this same SqlGen scope.
// --------------------------------------------------------------------------------------
void allocate_aliases(SqlGen &gen,
                      const ::flatbuffers::Vector<::flatbuffers::Offset<ir::DbAtEntity>> &entities) {
    for (::flatbuffers::uoffset_t i = 0; i < entities.size(); i++) {
        const ir::DbAtEntity *entity = entities.Get(i);
        gen.register_entity(entity->id(), entity->entity_def_index());
    }
}

// --------------------------------------------------------------------------------------
// capture_join_where — build each entity's join-where fragment, pulling its binds out of the
// global list into a JoinWhereSpec. Mirrors DbSqlGen.captureJoinWhere. MUST run BEFORE SELECT so
// the rel-JOINs the fragments reference register in the Kotlin order. `binds` is the global list.
//
// FIDELITY: in jOOQ the join-where Field holds anonymous '?'; here db_expr_to_sql emits "$N"
// eagerly against the live binds list. The captured fragment's "$N" therefore reflect the bind
// indices AT CAPTURE TIME (which are the post-SELECT positions, since SELECT has not rendered
// yet — capture runs first). After SELECT/WHERE/etc render and we splice these binds back at
// joinWhereBindPosition (== the SELECT-end index), the fragment's "$N" already match: the binds
// were captured starting at the SELECT-end index (0 if no SELECT binds preceded), then truncated,
// and re-inserted at exactly joinWhereBindPosition. See the entry points' insert() call.
// --------------------------------------------------------------------------------------
std::map<uint32_t, JoinWhereSpec> capture_join_where(
    const ::flatbuffers::Vector<::flatbuffers::Offset<ir::DbAtEntity>> *entities,
    std::vector<RellValue> &binds, const DbExprDispatch &dispatch) {
    std::map<uint32_t, JoinWhereSpec> specs;
    if (entities == nullptr) return specs;
    for (::flatbuffers::uoffset_t i = 0; i < entities->size(); i++) {
        const ir::DbAtEntity *entity = entities->Get(i);
        const ir::DbExpr *jw = entity->join_where();
        if (jw == nullptr) continue;
        const size_t before = binds.size();
        JoinWhereSpec spec;
        spec.sql = dispatch(*jw, /*enclose=*/false);
        for (size_t k = before; k < binds.size(); k++) spec.binds.push_back(binds[k]);
        binds.resize(before);  // truncateBinds(before)
        specs[entity->id()] = std::move(spec);
    }
    return specs;
}

// --------------------------------------------------------------------------------------
// build_from_clause — mirrors addFromTo. First entity -> "FROM <composed>"; later entity with a
// join-where -> " [LEFT OUTER] JOIN <composed> ON <jw>"; later without join-where but siblings
// have join-wheres -> " CROSS JOIN <composed>"; else comma list (", <composed>"). An outer entity
// WITHOUT a join-where uses ON TRUE. `composed` is the entity table folded with its rel-JOINs.
// --------------------------------------------------------------------------------------
std::string build_from_clause(
    SqlGen &gen, SqlContext &ctx,
    const ::flatbuffers::Vector<::flatbuffers::Offset<ir::DbAtEntity>> &entities,
    const std::map<uint32_t, std::string> &entity_join_wheres) {
    const bool has_join_wheres = !entity_join_wheres.empty();
    std::string from = " FROM ";
    bool first = true;

    for (::flatbuffers::uoffset_t i = 0; i < entities.size(); i++) {
        const ir::DbAtEntity *entity = entities.Get(i);
        const uint32_t entity_id = entity->id();
        const std::string alias = gen.get_entity_alias(entity_id);
        const std::string table = ctx.table_name(entity->entity_def_index());

        std::string composed = aliased_table(table, alias);
        // C1/C2: fold this root entity's lazily-discovered rel-JOINs into FROM as structural
        // `JOIN "table" Ann ON Amm."col" = Ann."rowid"`. The joins live in the SAME SqlGen scope
        // the expr dispatch registered them in (resolve_table_expr), keyed by the root alias.
        for (const RelJoin &j : gen.entity_rel_joins(alias)) {
            composed += " JOIN " + aliased_table(j.table, j.alias) + " ON " + join_on_condition(j);
        }

        auto jwit = entity_join_wheres.find(entity_id);
        const bool has_explicit_jw = jwit != entity_join_wheres.end();
        const bool is_outer = entity->is_outer();

        if (first) {
            from += composed;
        } else if (!has_explicit_jw && !is_outer) {
            from += has_join_wheres ? (" CROSS JOIN " + composed) : (", " + composed);
        } else {
            const std::string jw = has_explicit_jw ? jwit->second : std::string("TRUE");
            from += (is_outer ? " LEFT OUTER JOIN " : " JOIN ") + composed + " ON " + jw;
        }
        first = false;
    }
    return from;
}

// --------------------------------------------------------------------------------------
// build_select_text — stitch SELECT + FROM + WHERE + GROUP BY + ORDER BY. Pure text; all bind
// appends already happened (in placeholder order) in the entry point. Mirrors renderJooq(q) of
// buildSelectQueryAst's output.
// --------------------------------------------------------------------------------------
std::string build_select_text(SqlGen &gen, const std::vector<std::string> &select_fields,
                              const std::string &where_sql, bool has_where,
                              const std::vector<std::string> &group_by,
                              const std::vector<OrderByEntry> &order_by, SqlContext &ctx,
                              const ::flatbuffers::Vector<::flatbuffers::Offset<ir::DbAtEntity>> &entities,
                              const std::map<uint32_t, std::string> &entity_join_wheres) {
    std::string sql = "SELECT ";
    if (select_fields.empty()) {
        sql += "0";  // empty select -> addSelect(DSL.field("0")) -> "SELECT 0".
    } else {
        for (size_t i = 0; i < select_fields.size(); i++) {
            if (i > 0) sql += ", ";
            sql += select_fields[i];
        }
    }

    sql += build_from_clause(gen, ctx, entities, entity_join_wheres);

    if (has_where) sql += " WHERE " + where_sql;

    if (!group_by.empty()) {
        sql += " GROUP BY ";
        for (size_t i = 0; i < group_by.size(); i++) {
            if (i > 0) sql += ", ";
            sql += group_by[i];
        }
    }

    if (!order_by.empty()) {
        sql += " ORDER BY ";
        for (size_t i = 0; i < order_by.size(); i++) {
            if (i > 0) sql += ", ";
            sql += order_by[i].sql;
            if (order_by[i].desc) sql += " DESC";  // ASC bare (sortDefault, no keyword).
        }
    }
    return sql;
}

// --------------------------------------------------------------------------------------
// render_order_by — mirrors generateDbAtOrderBy (top-level) and subQueryOrderBy (subquery). The
// ONLY difference is the default-rowid trigger: top-level fires on (cardinality.many OR limit OR
// offset); subquery fires on (limit OR offset) only. `default_rowid_trigger` carries that.
//
// De-dup by (rendered SQL, binds added during render); on duplicate, truncate the binds back.
// --------------------------------------------------------------------------------------
std::vector<OrderByEntry> render_order_by(
    SqlGen &gen, const ::flatbuffers::Vector<::flatbuffers::Offset<ir::DbAtWhatField>> *what,
    const ::flatbuffers::Vector<::flatbuffers::Offset<ir::DbAtEntity>> *entities, SqlContext &ctx,
    std::vector<RellValue> &binds, const DbExprDispatch &dispatch, bool default_rowid_trigger) {
    std::vector<OrderByEntry> out;
    std::vector<std::pair<std::string, std::vector<RellValue>>> seen;

    auto try_add = [&](const std::string &sql, bool desc, size_t saved) {
        std::vector<RellValue> new_binds;
        for (size_t k = saved; k < binds.size(); k++) new_binds.push_back(binds[k]);
        for (const auto &s : seen) {
            if (s.first == sql && binds_equal(s.second, new_binds)) {
                binds.resize(saved);  // roll back the duplicate's binds.
                return;
            }
        }
        seen.emplace_back(sql, new_binds);
        out.push_back(OrderByEntry{sql, desc});
    };

    // Explicit sort fields (flags.sort != 0); sort < 0 -> DESC.
    if (what != nullptr) {
        for (::flatbuffers::uoffset_t i = 0; i < what->size(); i++) {
            const ir::DbAtWhatField *f = what->Get(i);
            const ir::AtWhatFieldFlags *flags = f->flags();
            const int32_t sort = flags != nullptr ? flags->sort() : 0;
            if (sort != 0) {
                const size_t saved = binds.size();
                std::string sql = dispatch(*f->expr(), /*enclose=*/false);
                try_add(sql, sort < 0, saved);
            }
        }
    }

    bool has_group = false;
    bool has_aggregate = false;
    if (what != nullptr) {
        for (::flatbuffers::uoffset_t i = 0; i < what->size(); i++) {
            const ir::AtWhatFieldFlags *flags = what->Get(i)->flags();
            if (flags != nullptr) {
                if (flags->group()) has_group = true;
                if (flags->aggregate()) has_aggregate = true;
            }
        }
    }

    if (has_group) {
        for (::flatbuffers::uoffset_t i = 0; what != nullptr && i < what->size(); i++) {
            const ir::DbAtWhatField *f = what->Get(i);
            const ir::AtWhatFieldFlags *flags = f->flags();
            if (flags != nullptr && flags->group() && flags->sort() == 0) {
                const size_t saved = binds.size();
                std::string sql = dispatch(*f->expr(), /*enclose=*/false);
                try_add(sql, false, saved);
            }
        }
    } else if (!has_aggregate && default_rowid_trigger) {
        // Default per-entity rowid ordering. (Top-level: only reaches here when !hasGroup, so the
        // generateDbAtOrderBy `else if` shape is preserved. Subquery: subQueryOrderBy additionally
        // requires `out.empty()`, which holds here because no sort field added an entry — if any
        // had, try_add would have populated `out` and the subquery default would be suppressed.
        // FIDELITY: subQueryOrderBy guards on pairs.isEmpty(); generateDbAtOrderBy does not. The
        // distinction is immaterial because both only enter this branch when no explicit sort
        // produced an entry AND !hasGroup — see the per-caller default_rowid_trigger note.)
        if (out.empty()) {
            for (::flatbuffers::uoffset_t i = 0; entities != nullptr && i < entities->size(); i++) {
                const ir::DbAtEntity *entity = entities->Get(i);
                const std::string alias = gen.get_entity_alias(entity->id());
                if (!alias.empty()) {
                    const std::string col = ctx.rowid_column(entity->entity_def_index());
                    const size_t saved = binds.size();
                    std::string sql = SqlGen::column_ref(alias, col);
                    try_add(sql, false, saved);
                }
            }
        }
    }

    return out;
}

// --------------------------------------------------------------------------------------
// render_limit_offset — " LIMIT $n"[" OFFSET $n"], binding the int values LAST in this textual
// order. Mirrors renderLimitOffsetSuffix.
//
// C4 (consensus-critical): the limit/offset operands are R-level Expr nodes (Rt_IntValue at
// runtime). DbSqlGen evaluates them via Rt_Interpreter.evaluateExpr (evaluateAtExtras) and binds
// the resulting Rt_IntValue. We thread that through the SAME InterpretedEvaluator hook every other
// R-level leaf uses — ctx.eval_interpreted(*extras->limit(), frame) — so a REAL value is bound, NOT
// a NONE poison. The evaluateAtExtras normalization (negative -> Rt_Exception; offset dropped when
// limit<=0) is performed JVM-side by the driver BEFORE handing extras to the renderer, so a present
// extras->offset() here always renders. The `bind` callback pushes onto binds and returns "$N".
// --------------------------------------------------------------------------------------
std::string render_limit_offset(const ir::AtExprExtras *extras, SqlContext &ctx, int64_t frame_handle,
                                const std::function<std::string(const RellValue &)> &bind) {
    if (extras == nullptr) return "";
    std::string out;
    if (extras->limit() != nullptr) {
        out += " LIMIT " + bind(ctx.eval_interpreted(*extras->limit(), frame_handle));
    }
    if (extras->offset() != nullptr) {
        out += " OFFSET " + bind(ctx.eval_interpreted(*extras->offset(), frame_handle));
    }
    return out;
}

// --------------------------------------------------------------------------------------
// render_select_body — the shared driver. Builds aliases, captures join-wheres, renders SELECT
// (records the splice position), WHERE, GROUP BY, ORDER BY, splices the join-where binds at the
// recorded position, assembles the text, appends LIMIT/OFFSET. Returns the
// "SELECT ... [LIMIT $n][OFFSET $n]" string; binds accumulate on `binds`.
//
// `default_rowid_trigger` is the caller's ORDER BY default predicate (top-level: many|limit|
// offset; subquery: limit|offset).
// --------------------------------------------------------------------------------------
std::string render_select_body(
    SqlGen &gen, const ir::DbAtExprFrom *from,
    const ::flatbuffers::Vector<::flatbuffers::Offset<ir::DbAtWhatField>> *what,
    const ir::DbExpr *where, const ir::AtExprExtras *extras, bool default_rowid_trigger,
    SqlContext &ctx, int64_t frame_handle, std::vector<RellValue> &binds,
    const DbExprDispatch &dispatch, const std::function<std::string(const RellValue &)> &bind) {
    const auto *entities = from != nullptr ? from->entities() : nullptr;

    // Aliases for the from-clause entities (shared counter), in declaration order.
    if (entities != nullptr) allocate_aliases(gen, *entities);

    // Capture join-where fragments (registers rel-JOINs early, pulls their binds out).
    std::map<uint32_t, JoinWhereSpec> join_where_specs =
        capture_join_where(entities, binds, dispatch);

    // SELECT what-fields (non-omit). Empty -> "SELECT 0".
    std::vector<std::string> select_fields;
    if (what != nullptr) {
        for (::flatbuffers::uoffset_t i = 0; i < what->size(); i++) {
            const ir::DbAtWhatField *f = what->Get(i);
            const ir::AtWhatFieldFlags *flags = f->flags();
            if (flags != nullptr && flags->omit()) continue;
            select_fields.push_back(dispatch(*f->expr(), /*enclose=*/false));
        }
    }
    const size_t join_where_bind_position = binds.size();

    // WHERE.
    std::string where_sql;
    bool has_where = false;
    if (where != nullptr) {
        where_sql = dispatch(*where, /*enclose=*/false);
        has_where = true;
    }

    // GROUP BY: what-fields with flags.group, in `what` order.
    std::vector<std::string> group_by;
    if (what != nullptr) {
        for (::flatbuffers::uoffset_t i = 0; i < what->size(); i++) {
            const ir::DbAtWhatField *f = what->Get(i);
            const ir::AtWhatFieldFlags *flags = f->flags();
            if (flags != nullptr && flags->group()) {
                group_by.push_back(dispatch(*f->expr(), /*enclose=*/false));
            }
        }
    }

    // ORDER BY.
    std::vector<OrderByEntry> order_by =
        render_order_by(gen, what, entities, ctx, binds, dispatch, default_rowid_trigger);

    // Splice the captured join-where binds at the post-SELECT position.
    if (!join_where_specs.empty()) {
        std::vector<RellValue> join_binds;
        for (auto &kv : join_where_specs) {
            for (const RellValue &b : kv.second.binds) join_binds.push_back(b);
        }
        binds.insert(binds.begin() + static_cast<std::ptrdiff_t>(join_where_bind_position),
                     join_binds.begin(), join_binds.end());
    }
    std::map<uint32_t, std::string> entity_join_wheres;
    for (auto &kv : join_where_specs) entity_join_wheres[kv.first] = kv.second.sql;

    // Assemble + LIMIT/OFFSET.
    std::string sql = build_select_text(gen, select_fields, where_sql, has_where, group_by, order_by,
                                        ctx, *entities, entity_join_wheres);
    sql += render_limit_offset(extras, ctx, frame_handle, bind);
    return sql;
}

}  // namespace

// =====================================================================================
// SqlGen structural-state + result methods (shared by all three gen TUs; B2). Defined here
// because this TU owns the FROM/scope machinery; the dispatch (rell_sql_expr.cpp) and the write
// paths (rell_sql_write.cpp) call them on the same SqlGen instance. (bind / quote_ident /
// column_ref / wrap_decimal are defined in rell_sql_expr.cpp — one copy, linked into every build.)
// =====================================================================================

RellSql SqlGen::result() && { return RellSql{std::move(text_), std::move(binds_)}; }

// DbSqlGen.init / write-path ctor: allocate a fresh alias for `entity_id` in declaration order,
// seeded as its own root entity. Idempotent within a scope (re-registering keeps the first alias).
void SqlGen::register_entity(uint32_t entity_id, uint32_t entity_def_index) {
    if (scope_->entity_aliases.count(entity_id) != 0) return;
    const std::string alias = ctx_.fresh_alias();
    scope_->entity_aliases[entity_id] = EntityAlias{alias, entity_def_index};
    scope_->alias_to_root[alias] = alias;
}

// resolveEntityAlias: current scope, then walk parents (correlated subqueries). Aborts on a wiring
// bug — DbSqlGen uses checkNotNull.
std::string SqlGen::resolve_entity_alias(uint32_t entity_id, uint32_t &out_def_index) {
    for (Scope *s = scope_; s != nullptr; s = s->parent) {
        auto it = s->entity_aliases.find(entity_id);
        if (it != s->entity_aliases.end()) {
            out_def_index = it->second.entity_def_index;
            return it->second.alias;
        }
    }
    std::abort();  // checkNotNull: alias must exist (entities are seeded before dispatch).
}

std::string SqlGen::get_entity_alias(uint32_t entity_id) const {
    auto it = scope_->entity_aliases.find(entity_id);
    return it == scope_->entity_aliases.end() ? std::string() : it->second.alias;
}

SqlGen::Scope &SqlGen::find_owner_for_alias(const std::string &alias) {
    for (Scope *s = scope_; s != nullptr; s = s->parent) {
        if (s->alias_to_root.count(alias) != 0) return *s;
    }
    return *scope_;
}

// resolveTableExpr (DbSqlGen): Entity → (alias, def); Rel → resolve the base, map the FK column,
// register a rel-JOIN (cached by (baseAlias, attrName)) into the owner scope's entity_rel_joins
// keyed by the base's ROOT alias. C1: this is the SAME registry build_from_clause reads.
std::pair<std::string, uint32_t> SqlGen::resolve_table_expr(const ir::DbExpr &expr) {
    if (expr.expr_type() == ir::DbExprUnion_DbEntityExpr) {
        const ir::DbEntityExpr *ent = expr.expr_as_DbEntityExpr();
        uint32_t defIdx = 0;
        std::string alias = resolve_entity_alias(ent->entity_id(), defIdx);
        return {alias, defIdx};
    }
    // Rel base.
    const ir::DbRelExpr *rel = expr.expr_as_DbRelExpr();
    std::pair<std::string, uint32_t> base = resolve_table_expr(*rel->base());
    const std::string &baseAlias = base.first;
    const std::string attrName = rel->attr_name()->str();

    Scope &owner = find_owner_for_alias(baseAlias);
    const std::pair<std::string, std::string> key{baseAlias, attrName};
    auto cached = owner.rel_join_aliases.find(key);
    if (cached != owner.rel_join_aliases.end()) {
        return {cached->second.alias, cached->second.entity_def_index};
    }

    const uint32_t targetDef = rel->target_entity_def_index();
    const std::string alias = ctx_.fresh_alias();
    owner.rel_join_aliases[key] = EntityAlias{alias, targetDef};

    const std::string baseColumn = ctx_.attr_sql_mapping(base.second, attrName);
    const std::string table = ctx_.table_name(targetDef);
    const std::string targetRowid = ctx_.rowid_column(targetDef);
    RelJoin joinInfo{table, alias, baseAlias, baseColumn, targetRowid};

    auto rootIt = owner.alias_to_root.find(baseAlias);
    const std::string rootAlias = rootIt != owner.alias_to_root.end() ? rootIt->second : baseAlias;
    owner.alias_to_root[alias] = rootAlias;
    if (owner.entity_rel_joins.find(rootAlias) == owner.entity_rel_joins.end()) {
        owner.rel_join_root_order.push_back(rootAlias);  // record first-join insertion order.
    }
    owner.entity_rel_joins[rootAlias].push_back(std::move(joinInfo));
    return {alias, targetDef};
}

const std::vector<SqlGen::RelJoin> &SqlGen::entity_rel_joins(const std::string &root_alias) const {
    static const std::vector<RelJoin> kEmpty;
    auto it = scope_->entity_rel_joins.find(root_alias);
    return it == scope_->entity_rel_joins.end() ? kEmpty : it->second;
}

// getAllRelJoins: every rel-JOIN across all root entities in the current scope, flattened in
// root first-join INSERTION order (rel_join_root_order) — faithful to DbSqlGen.entityRelJoins
// being a LinkedHashMap (values.flatten() iterates by insertion == declaration order), robust
// where a std::map key sort would diverge.
std::vector<SqlGen::RelJoin> SqlGen::rel_joins() const {
    std::vector<RelJoin> out;
    for (const std::string &root : scope_->rel_join_root_order) {
        auto it = scope_->entity_rel_joins.find(root);
        if (it == scope_->entity_rel_joins.end()) continue;
        for (const RelJoin &j : it->second) out.push_back(j);
    }
    return out;
}

// =====================================================================================
// SqlGen::render_db_at — top-level SELECT for a DbAtExpr. Produces the RellSql. Cardinality /
// row decode / object-init / field-group reduction (evaluateDbAt's second half) belong to the
// executor + caller, not here. Mirrors DbSqlGen.buildSelectQuery.
//
// C4: db_expr_to_sql (DbInterpreted leaves) and the LIMIT/OFFSET evaluation both route the live
// R-level value through SqlContext::eval_interpreted — a REAL bind value, not a NONE poison.
// =====================================================================================
RellSql SqlGen::render_db_at(const ir::DbAtExpr &at, int64_t frame_handle) {
    DbExprDispatch dispatch = [this, frame_handle](const ir::DbExpr &e, bool enclose) {
        return db_expr_to_sql(e, frame_handle, enclose);
    };
    auto bind_cb = [this](const RellValue &v) { return bind(v); };

    const bool many = at.cardinality() == ir::AtCardinality_ZERO_MANY ||
                      at.cardinality() == ir::AtCardinality_ONE_MANY;
    const ir::AtExprExtras *extras = at.extras();
    const bool has_limit = extras != nullptr && extras->limit() != nullptr;
    const bool has_offset = extras != nullptr && extras->offset() != nullptr;
    // generateDbAtOrderBy default-rowid trigger: cardinality.isMany || limit || offset.
    const bool default_rowid = many || has_limit || has_offset;

    text_ = render_select_body(*this, at.from(), at.what(), at.where(), extras, default_rowid, ctx_,
                               frame_handle, binds_, dispatch, bind_cb);
    return std::move(*this).result();
}

// =====================================================================================
// SqlGen::db_sub_query — correlated sub-query "(SELECT ... [LIMIT][OFFSET])". The LIMIT/OFFSET
// suffix lives INSIDE the parens (renderSubQuerySql). Binds accumulate on the SHARED binds_ and
// the alias counter is shared (ctx_.fresh_alias) — DbSqlGen.createSub. A child Scope is pushed
// whose parent is the enclosing scope, so resolveEntityAlias's parent-walk reaches correlated
// outer entities; it is restored on return.
//
// subQueryOrderBy default-rowid trigger: limit || offset (NO cardinality-many term — a subquery
// is not driven by outer cardinality).
// =====================================================================================
std::string SqlGen::db_sub_query(const ir::DbSubQueryExpr &e, int64_t frame_handle) {
    Scope child;
    child.parent = scope_;
    Scope *saved = scope_;
    scope_ = &child;

    DbExprDispatch dispatch = [this, frame_handle](const ir::DbExpr &d, bool enclose) {
        return db_expr_to_sql(d, frame_handle, enclose);
    };
    auto bind_cb = [this](const RellValue &v) { return bind(v); };

    const ir::AtExprExtras *extras = e.extras();
    const bool has_limit = extras != nullptr && extras->limit() != nullptr;
    const bool has_offset = extras != nullptr && extras->offset() != nullptr;
    const bool default_rowid = has_limit || has_offset;

    std::string body = render_select_body(*this, e.from(), e.what(), e.where(), extras,
                                          default_rowid, ctx_, frame_handle, binds_, dispatch,
                                          bind_cb);
    scope_ = saved;
    return "(" + body + ")";
}

}  // namespace rell::sql
