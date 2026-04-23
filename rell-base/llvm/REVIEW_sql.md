# Adversarial fidelity review — native SQL generator

Scope: `rell_sql_expr.cpp`, `rell_sql_select.cpp`, `rell_sql_write.cpp`, `rell_sql_types.cpp`
against `rr_interp_sql_gen.kt` (DbSqlGen), `rr_interp_db_at.kt`, `rr_interp_db_write.kt`.

Ground truth cross-checked against the golden strings in
`rell-base/src/test/kotlin/sql/SqlEmissionTest.kt` (jOOQ-rendered `?`; native emits `$N`).

Severity legend: **BLOCKER** (won't build / link), **CRITICAL** (wrong SQL or wrong bind order
on a real case — consensus divergence), **MAJOR** (wrong on a less-common case), **MINOR** (style /
latent), **OK** (verified faithful — listed to record the check).

---

## BLOCKERS (do not compile / link as-is)

### B1. Duplicate definition of `SqlGen::db_sub_query` + undefined `render_sub_query_body`
- `rell_sql_expr.cpp:949` defines `SqlGen::db_sub_query`, which calls
  `render_sub_query_body(*this, e, frame_handle)` (declared extern at `rell_sql_expr.cpp:66`).
- `rell_sql_select.cpp:581` **also** defines `SqlGen::db_sub_query`.
- `render_sub_query_body` is **never defined** in any TU.

Result: link-time duplicate symbol for `db_sub_query` **and** unresolved
`render_sub_query_body`. Pick one owner: delete the expr.cpp definition + the extern, keep the
select.cpp definition (it has the from-clause/splice machinery). The dispatch in
`db_expr_to_sql` already calls `db_sub_query` as a member, so no seam is needed.

### B2. Three TUs invented three incompatible state models; none match `rell_sql.h`
The header's `SqlGen` private section is only `{ ctx_, binds_, text_ }`. But:
- `rell_sql_expr.cpp` resolves aliases / rel-joins / attr mappings / evaluation through a
  `SqlExprEnv` obtained by `dynamic_cast<SqlExprEnv&>(ctx_)` (`env_of`, line 123).
- `rell_sql_select.cpp` invents a file-local `SqlGenState` struct and threads it manually.
- `rell_sql_write.cpp` calls `register_entity(...)`, `get_entity_alias(...)`, `rel_joins()`,
  and `ctx_.attr_sql_mapping(...)` (`rell_sql_write.cpp:209,218,232,246,...`) — **none of which
  exist** on `SqlGen` or `SqlContext`. This TU does not compile.

These three models are not connected (see C1). The header must grow ONE shared per-query state
(entity-alias map + rel-join registry + `attr_sql_mapping` on `SqlContext`) and all three TUs must
route through it. The headers were declared "complete, no edits needed" in the design spec — that
is wrong; the `.cpp` bodies cannot be wired without header changes.

---

## CRITICAL (wrong SQL / wrong bind order on real cases)

### C1. Rel-JOINs discovered by the expr dispatch never reach the SELECT FROM clause
This is the single highest-impact divergence. `db_attr` / `db_rel` / `db_rowid`
(`rell_sql_expr.cpp:446,477,508`) register relationship JOINs via
`SqlExprEnv::register_rel_join`. But `build_from_clause` (`rell_sql_select.cpp:254`) reads rel-joins
from the file-local `SqlGenState::entity_rel_joins` (`:270`), which `register_rel_join` never
populates — they are two disconnected registries.

Divergent query — golden `at_path_join` (`SqlEmissionTest.kt:174`):
```
expected: SELECT A00."rowid" FROM "c0.emp" A00 JOIN "c0.company" A01 ON A00."company" = A01."rowid" WHERE A01."name" = $1 ORDER BY A00."rowid"
native:   SELECT A00."rowid" FROM "c0.emp" A00 WHERE A01."name" = $1 ORDER BY A00."rowid"
```
The JOIN is dropped and `A01` dangles → SQL error / wrong result. **Consensus-breaking.**

Fix: unify the rel-join registry (B2). `resolveTableExpr`'s rel-join must land in the same
`entity_rel_joins[rootAlias]` map that `build_from_clause` folds into `composed`. Also mirror
`DbSqlGen.resolveTableExpr`'s caching key `(baseAlias, attrName)` and the `aliasToRootEntity`
chaining so a JOIN is registered exactly once and attached to the correct root entity.

### C2. SELECT path-joins must render as structural `JOIN ... ON`, not be lost
Corollary of C1, but worth stating as its own contract: in the read path DbSqlGen emits rel-joins
**structurally** inside FROM (`addFromTo` → `acc.join(joined).on(left.eq(right))`), producing
`FROM "c0.emp" A00 JOIN "c0.company" A01 ON A00."company" = A01."rowid"`. `build_from_clause`
already has the right shape (`" JOIN " + aliased_table + " ON " + join_on_condition`) — it just
never receives the joins (C1). Once C1 is fixed, verify the ON-predicate spelling matches:
golden shows `A00."company" = A01."rowid"` (no extra parens), which `join_on_condition`
(`rell_sql_select.cpp:178`) produces correctly.

### C3. `db_when` keyed-RConst equality compares raw payload, not `Rt_Value.equals`
`rell_sql_expr.cpp:815`:
```cpp
bool eq = condValue.tag == keyConst.tag && condValue.scale == keyConst.scale
          && condValue.payload.i64 == keyConst.payload.i64;
```
DbSqlGen uses `condValue == keyConst` (`rr_interp_sql_gen.kt:525`), i.e. `Rt_Value.equals`. The
raw-payload compare is wrong for:
- **HANDLE** keys (text / enum / entity WHEN keys): compares jobject pointer identity, so two
  equal `Rt_TextValue("a")` with distinct handles compare unequal → the matching case is NOT
  folded away and instead emits a live `WHEN ... THEN` branch. Different SQL + different binds.
- **DEC_LONG** with differing scales representing the same number (e.g. `1.0` vs `1.00`) →
  spuriously unequal.

A keyed `when (someStrConst) { "a" -> ... }` mis-folds. Route value equality through a hook
mirroring `Rt_Value.equals` (the same JVM the evaluator uses), or restrict the inline compare to
INTEGER/ROWID/BOOLEAN/BIGINT_LONG and route everything else through the env. Carry `// FIDELITY:`.

### C4. LIMIT/OFFSET binds are poisoned (`eval_extra_int` returns NONE)
`rell_sql_select.cpp:145` `eval_extra_int` returns `rv_none()` unconditionally (TODO G.1). Any
at-expression with `limit`/`offset` (golden `at_limit`, `at_offset`, `at_limit_offset`,
`SqlEmissionTest.kt:272,283,294`) binds a NONE value → `bind_param` treats NONE as SQL NULL
(`rell_sql_types.cpp:244`), so `LIMIT $n` receives NULL. Wrong result / PG error.

This is a known seam (InterpretedEvaluator not yet a ctor param), but it is consensus-critical and
currently silently produces wrong SQL binds. At minimum the renderer must not be shipped until the
evaluator is threaded; the golden `LIMIT/OFFSET` cases will fail. Note also the text is correct
(`" LIMIT $n"` / `" OFFSET $n"`, bound last, in that order — matches `renderLimitOffsetSuffix`); only
the bound value is poisoned.

### C5. `db_exists` Interpreted branch evaluates the R leaf twice
`rell_sql_expr.cpp:682,691`: calls `eval_interpreted(...)` then, if non-null,
`eval_collection(...)` on the **same** leaf — two evaluations. DbSqlGen evaluates once
(`rr_interp_sql_gen.kt:444-445`: one `evaluateExpr`, then casts the result to
`Rt_CollectionValue`). For a side-effecting R expression this double-runs it (consensus risk if the
expression mutates frame state or calls an impure function). No bind/text impact (both paths emit a
literal TRUE/FALSE), but the second evaluation is an observable semantic divergence. Fix: a single
`eval_interpreted` returning the collection value, with emptiness derived from it (add an env hook
`collection_is_empty(value)` rather than re-evaluating).

---

## MAJOR (wrong on less-common cases)

### M1. UPDATE/DELETE rel-join conditions are emitted UNCONDITIONALLY wrapped in parens
`combine_where` (`rell_sql_write.cpp:107`) wraps every join cond in `"(" + jc + ")"` and, when ≥2
conditions, wraps the whole in `"(" + ... + ")"`. Golden `update_path` / `delete_path`
(`SqlEmissionTest.kt:420,444`) confirm `WHERE ((A00."company" = A01."rowid") AND A01."name" = $1)`
— so the double-paren is correct **for that shape**. But verify the single-join-no-user-where case:
DbSqlGen with one join cond and no user WHERE renders `q.addConditions(cond)` once → jOOQ emits the
bare predicate `A00."company" = A01."rowid"` (no outer parens, and the inner raw
`DSL.condition("{0} = {1}", ...)` is NOT self-parenthesized when it is the sole condition). The
native `combine_where` with one join cond returns `conds[0]` = `"(" + jc + ")"` →
`A00."company" = A01."rowid"` wrapped in **parens**. Likely divergence:
```
jOOQ:   WHERE A00."company" = A01."rowid"
native: WHERE (A00."company" = A01."rowid")
```
The per-join `"(" + jc + ")"` is only observed in the golden because it is an AND operand. Confirm
jOOQ's lone-condition rendering and drop the per-cond parens when it is the only condition. Carry
`// FIDELITY:` and add a golden case for one-join-no-user-where.

### M2. `or_combine` flat-group parenthesization is unverified against jOOQ `.or()` chains
`rell_sql_expr.cpp:716` renders a multi-cond OR as `"(c0 OR c1 OR ...)"` (one flat group). The
unkeyed-WHEN and keyed-null-key paths reduce `a.or(b).or(c)`. jOOQ renders a left-assoc `.or()`
chain — confirm it flattens to a single parenthesized group rather than nested `((c0 OR c1) OR c2)`.
The only golden CASE (`at_where_when_if`, `:305`) has single-cond branches, so this is untested.
Add a multi-cond WHEN golden and verify; carry the existing `// FIDELITY:` note.

### M3. `db_attr`/`db_rel` UPDATE SET column lookup uses fallback, but UPDATE must assert
DbSqlGen's UPDATE SET resolves the column via `entityDef.strAttributes[w.attrName]!!.sqlMapping`
(`rr_interp_db_write.kt:274` — hard `!!`, no fallback). The expr-dispatch `attr_sql_mapping`
(`rell_sql_expr.cpp:95` contract) falls back to the attr name itself (mirrors the READ-path
`?: expr.attrName`). For the write path the write.cpp comment (`:228`) correctly notes the `!!`
semantics, but the shared `attr_sql_mapping` contract is the fallback flavor. Ensure the SET-column
resolution does not silently fall back to a raw attr name that differs from the sqlMapping (it would
emit a wrong column). Low practical risk (mapping usually equals the name) but a real divergence if
an attr has a distinct `sqlMapping`.

### M4. UPDATE RETURNING omits snapshot attrs (text + decode contract incomplete)
`render_update` (`rell_sql_write.cpp:296`) emits only `RETURNING A00."rowid"`. DbSqlGen appends
every attribute column when `opCtx.hasSnapshotContext()` is true
(`rr_interp_db_write.kt:313-315`), changing both the SQL text and the executor's decode column
count. The snapshot flag is a runtime (opCtx) property not in the FlatBuffer; it must be surfaced
via `SqlContext` (the file documents the intended `ctx_.snapshot_active()` /
`ctx_.entity_attr_sql_mappings(...)` seam at `:302`). Until wired, snapshot-context UPDATEs diverge.
DELETE RETURNING is rowid-only in both (correct).

---

## MINOR / latent

### m1. Duplicated `quote_ident`/`column_ref` logic across TUs
`rell_sql_select.cpp:156,168` define file-local `quote_ident`/`column_ref` duplicating
`SqlGen::quote_ident`/`column_ref` (`rell_sql_expr.cpp:174,187`). Two copies of consensus-critical
identifier quoting must stay byte-identical. Consolidate onto the `SqlGen` statics (they are private
but the renderer runs inside member functions) or a shared internal header.

### m2. `db_binary` `FN:` branch is dead from the enum set (documented) — keep the guard
`db_binary_op_sql` (`rell_sql_expr.cpp:203`) has no `FN:`-prefixed mapping (matches
`RROpDeserializer.FB_TO_DB_BINARY_OP`, verified — no function-form op exists today). The `FN:`
branch in `db_binary` (`:385`) is unreachable. This is correct and faithful; keep the
`// FIDELITY:` and the guard for future ops. **OK as-is**, noted for completeness.

### m3. DbCall Template encoding `#{i}` is assumed, not verified
`db_call` (`rell_sql_expr.cpp:623`) parses `#{i}` fragments. The schema comment
(`ir.fbs:581`: `"text#{i}text"`) supports this, but the exact serializer encoding
(`deserializeDbSysFn`) was not located/confirmed. Verify against the JVM serializer; a mismatch
silently mis-renders system-function calls. Carry the existing `// FIDELITY:`.

### m4. Bind-renumber-on-splice relies on emit-time `$N`; verify subquery join-where splice
`render_select_body` (`rell_sql_select.cpp:456`) captures join-where binds, truncates, renders
SELECT/WHERE/etc, then `binds.insert(... join_where_bind_position ...)`. Because the native side
emits `$N` **eagerly** at capture time, the captured fragment's `$N` reflect the indices at capture
(post-SELECT positions, since capture runs before SELECT renders any binds). After WHERE/GROUP/ORDER
append further binds and the join-where binds are spliced at `join_where_bind_position`, the spliced
binds shift WHERE/GROUP/ORDER binds rightward — but their `$N` text was already emitted with the
PRE-splice numbers. This is the consensus-critical hazard the file comments call out
(`:218-227`). For the top-level no-join-where cases (all current goldens) it is inert; but any
at-expression with an entity join-where (`entity @{ x: ... }`) plus a WHERE clause will have the
WHERE `$N` off by the join-where bind count. Needs a dedicated golden (join-where + WHERE + a bind
in each) before trusting it. **Untested; likely CRITICAL once join-where binds exist.**

---

## Type adapters (`rell_sql_types.cpp`) — bind/decode round-trip

### t1. OK — NUMERIC text round-trip (decimal / big_integer)
`dec_long_to_numeric_text` (`:189`) = `RellBigDec::valueOf(mantissa, scale).toPlainString()`;
`bigint_long_to_numeric_text` (`:195`) = `RellBigInt::fromInt64(v).toString()`. Matches JVM
`setBigDecimal` canonical text. Decode (`:397,417`) parses NUMERIC text → DEC_LONG/BIGINT_LONG when
in the i64 envelope, else routes to the JVM rebox hook with the canonical text. Faithful to
`fromSql` → `toBigIntegerExact`. **OK.**

### t2. OK — BYTEA `\x` hex decode in TEXT result format
`decode_bytea_text` (`:159`) handles `\x<hex>`; the pack/unpack big-endian int helpers are correct
network order. `pg_type_info` requests BINARY result for BYTEA (`:225`) but `decode_cell` branches
on the per-cell `fmt` and handles both — robust to the executor forcing uniform TEXT results. **OK.**

### t3. NULL sentinel mapping — verify the executor raises on the NONE return
`decode_cell` (`:365`) returns `rv_null()` for nullable NULL and `rv_none()` for non-nullable NULL,
matching the header's contract that the **executor** turns NONE into `sql_null:<type>`
(`Rt_SqlNull.check(name, nullable=false)`). The equivalence `PQgetisnull ⇔ (sentinel && wasNull())`
is correct (the sentinel is a necessary-but-not-sufficient precondition; `wasNull()` is the real
gate, and PQgetisnull == wasNull). **OK at this layer**, but the executor TU MUST honor the NONE →
raise contract or a non-nullable NULL silently becomes a poison value. Flag for the executor review.

### t4. MINOR — BOOLEAN bound as TEXT `t`/`f` while INT/BYTEA bound BINARY
`pg_type_info` (`:212`) binds BOOLEAN as TEXT. Faithful to `setBoolean` semantics and accepted by
PG for BOOL params. No divergence; noted because the mixed text/binary param formats must be
threaded correctly into `PQexecParams` paramFormats[] by the executor (its concern, not this TU).

### t5. MINOR — ENTITY/ENUM inline-fallback binds are defensive, not reached in practice
`bind_param` ENTITY/ENUM (`:264,280`) accept an inline INTEGER/ROWID as a fallback when the value
is not a HANDLE. DbSqlGen always produces HANDLE entity/enum values; the inline path is dead but
harmless. **OK.**

---

## Verified-faithful checks (recorded so they are not re-litigated)

- **Operator string tables** `db_binary_op_sql` / `db_unary_op_sql` (`rell_sql_expr.cpp:203,241`)
  match `RROpDeserializer.FB_TO_DB_BINARY_OP` / `FB_TO_DB_UNARY_OP` **exactly** (incl.
  `EQ_NULL → "IS NOT DISTINCT FROM"`, `NE_NULL → "IS DISTINCT FROM"`, `MOD_* → "%"`,
  `CONCAT → "||"`). ✓
- **Binary parenthesization**: `opTpl` `enclose ? "(L op R)" : "L op R"` with single spaces;
  operands recursed `enclose=true`. Matches `binaryToField`. ✓
- **AND/OR short-circuit** (`:316`) folds R-const operands to a bool literal or the other operand,
  preserving the `enclose` flag — mirrors `binaryToField`. ✓
- **nullable-eq collapse** (`:340`): `EQ_NULL`+known-NULL → `x IS NULL`; `NE_NULL` → `x IS NOT NULL`;
  const on one side → `opTpl(other, bind(const))` with the non-const operand rendered first
  (bind order preserved). Matches `:296-332`. ✓
- **Unary** `"OP {x}"` single space, `(OP {x})` when enclosed; operand `enclose=true`. ✓
- **IN empty static list** → bool literal (`:559`); non-empty → `key IN (i0,i1,...)` with bare-comma
  tuple. Golden `at_in_operator` `A00."k" IN ($1,$2)`. ✓
- **tuple_field** bare comma, `()` when empty — matches `tupleField` `joinToString(",")`. ✓
- **Elvis** COALESCE + R-const collapse (`:595`). ✓
- **Decimal wrap** `ROUND(x, 20)` on Attr/Binary/Call DECIMAL; golden
  `ROUND(COALESCE(SUM(ROUND(A00."balance", 20)),0), 20)`. ✓
- **db_attr** `wrap_decimal` applied; **db_rel** emits bare FK col (no wrap) — matches the Kotlin
  Rel branch having no `isDecimalType` check. ✓
- **db_rowid** Rowid(Rel) FK-shortcut reads base FK col without a JOIN; else rowid col. ✓
- **SELECT empty what** → `SELECT 0`; **ORDER BY** ASC bare (no `ASC`), DESC `field DESC`; default
  per-entity rowid trigger top-level `many|limit|offset`, subquery `limit|offset`. Goldens
  `at_sort_*`, `at_limit*`. ✓
- **ORDER BY de-dup** by `(rendered_sql, binds_added)` with rollback; golden
  `at_sort_desc_parameterized` proves the re-render + re-bind of `.k + n` produces a second `$N`. ✓
- **INSERT** text/bind order exact vs golden `INSERT INTO "c0.user" ("rowid","name",...) VALUES
  ("c0.make_rowid"(), $1,...) RETURNING "rowid"`; rowid fn quoted, no bind. ✓
- **UPDATE/DELETE** SET→WHERE / WHERE-only bind order; USING vs FROM; golden strings match for the
  join cases (modulo M1's single-cond paren question). ✓
- **quote_ident** doubles embedded `"`, EXPLICIT_DEFAULT_QUOTED. ✓

---

## Priority fix order
1. **B1, B2** — make it build/link (one `db_sub_query`, one shared state model, header edits).
2. **C1/C2** — wire the rel-join registry into the FROM clause (path-joins are completely broken).
3. **C4** — thread the InterpretedEvaluator so LIMIT/OFFSET (and all Interpreted leaves) bind real
   values.
4. **C3** — `Rt_Value.equals`-faithful WHEN key folding.
5. **m4** — add a join-where-plus-WHERE golden and verify the bind splice numbering.
6. **C5, M1–M4** — semantic/edge-case fidelity, each gated by a new golden case.
