---
name: self-review-loop
description: Use after a non-trivial change (refactor, migration, semantic rewrite, or anything spanning >5 files) and BEFORE declaring it done. Spawns an independent code-review subagent against the current diff, applies the real fixes, and iterates until a pass produces only nits or "clean". Burns context budget on its own subagent so the main thread keeps room for the work itself. Especially valuable on long sessions where the context-window subtle bug rate climbs.
---

# Extended self-review loop

The Rell codebase is determinism-sensitive (consensus code) and large enough that a single review pass misses subtle correctness bugs. After a non-trivial change, run this loop. Each iteration spawns a fresh subagent — no shared context with previous passes — so each review is genuinely independent.

## When to run

Run this skill when the diff:

- spans more than ~5 files, or
- migrates a subsystem (SQL emission, type system, frontend pass, etc.), or
- changes runtime semantics (anything the interpreter consumes), or
- introduces a new bind/render/eval ordering invariant, or
- replaces hand-rolled code with a library that has its own implicit semantics (jOOQ, ANTLR, FlatBuffers).

Do NOT run it for:

- one-file bug fixes with a regression test
- pure documentation changes
- doc-comment / wording cleanups
- formatter/import-only diffs

The loop is expensive — each iteration spawns a Sonnet/Opus-class subagent. Use it on changes where a missed bug is worse than the review cost.

## How to invoke

Use the `Agent` tool with `subagent_type: general-purpose`. Pass a prompt that:

1. **Names the change concretely** — what was migrated, from what to what, in which files. Don't just say "review my diff" — give the agent enough context that it can reason about correctness without re-deriving your design.
2. **Lists the prior fixes already addressed** — every iteration after the first should explicitly enumerate what previous iterations caught. This prevents the next iteration from re-flagging the same issues.
3. **Lists the specific risk areas to inspect** — bind ordering, identifier escaping, evaluation order, side-effect duplication, sealed-class exhaustiveness, dialect-specific rendering, etc.
4. **Caps the output** — "find at most 5 high-signal items" or "if everything is clean, say so". Without a cap, agents pad with nits.
5. **Forbids cosmetic suggestions** — "skip formatting/naming preferences", "skip 'could be more elegant'".
6. **Asks for the format**: `file:line, issue, fix`. Forces concrete suggestions; rejects vague critique.

## The loop

1. Run iteration N.
2. Read the report. Categorise findings:
   - **Real bugs** (correctness, security, semantic): fix immediately, even if subtle.
   - **Documentation inaccuracies** that mislead future readers: fix.
   - **Defensive cleanups** (catch-alls hiding errors, dead `@Suppress`, redundant casts): fix when easy.
   - **"Could be more elegant"**: skip unless you actually believe it.
3. Apply fixes via the appropriate edit tool.
4. Run `mcp__IntelliJ__build_project` (or the equivalent fast-feedback check) to confirm the fixes compile.
5. Run the relevant test slice — for Rell, typically `./gradlew :rell-base:test` or `./gradlew :<module>:test`.
6. **Spawn iteration N+1.** Pass the running list of "already addressed" so the new agent doesn't repeat itself.
7. **Stop** when one of these holds:
   - The agent reports "clean" or "no blockers found" with zero items.
   - The remaining items are all stylistic preferences ("could rename", "could extract a helper", "missing comment") with no correctness implications.
   - You've hit 4 iterations and the bugs found are increasingly minor (diminishing returns).

## Loop termination heuristics

The loop self-terminates because each iteration's prompt enumerates what's already addressed. The agent has progressively less surface area to find new bugs in. Watch for these signs that the next iteration will be unproductive:

- Items shrink from "real bug" to "defensive cleanup" to "naming preference".
- The agent starts qualifying findings with "low-priority" / "borderline" / "cosmetic".
- Items contradict prior agents (one says "add this guard", next says "the guard is unnecessary").
- The agent re-flags something the previous iteration explicitly addressed (means you didn't list it in "already addressed" — fix the prompt and rerun, don't add the loop iteration).

## Worked example (from the jOOQ SQL-emission migration)

The loop ran 4 iterations against a ~3000 LOC migration:

- **Iteration 1** (10 items): real bugs in `nullableEq` double-eval, missing `!hasAggregate` guard in `subQueryOrderBy`, dead `isMany` parameter, missing `SAFE_ALIAS_REGEX` assertion, two leaf-helper migrations skipped, dev.txt wording inaccuracy.
- **Iteration 2** (4 items): Elvis double-eval, jOOQ `LIMIT 1` rendering claim wrong, stale `@Suppress` on `extras`, catch-all in `existsField` swallowing real errors.
- **Iteration 3** (5 items): bind/render desync for `entityJoinWheres` (REAL bug, not exercised by tests), `keyExpr` in `keyedWhenWithDbKey` re-evaluated per case, `reduceDbExpr` catch hides intent, `sortField` re-splice fragility note, dev.txt §6 historically misleading.
- **Iteration 4** (0 items): explicit "everything looks clean", with affirmative checks on five specific risk areas (`joinWhereBindPosition` timing, `truncateBinds` interaction with the splice, spec.field re-rendering consistency, side-effect rel-join binds, dead helpers).

The loop terminated at iteration 4. The most consequential bug — the bind/render desync — was caught only at iteration 3. Without the loop, the migration would have shipped with that bug latent in the codebase, since no inlined test exercises a `joinWhere` with bind values.

## Prompt template

```
Code-review iteration N of the <feature> change in <repo path>. Diff: `git diff HEAD`.

Files of interest:
- <list>

Render context / invariants:
- <one or two sentences setting up the model the agent needs>

Previous iterations already addressed:
1. <fix from iter N-1>
2. <fix from iter N-2>
…

Find anything that's wrong, risky, or sloppy. Specifically:
1. <risk area 1>
2. <risk area 2>
…

Skip pure formatting and naming preferences. Skip "could be more elegant". Cap to <N> items.
If everything is clean, say so explicitly. Format: file:line, issue, fix.
```

Adjust the cap downward as iterations progress (10 → 5 → 3) and the surface area shrinks. By the final iteration, the agent should be down to 0–2 items.

## What this skill is NOT

- Not a substitute for writing tests. The loop catches reasoning errors that tests didn't cover; tests catch behavioural regressions on inputs you knew about.
- Not a substitute for understanding the change yourself. If you can't enumerate the risk areas in step 3 of the prompt, the agent has nothing to anchor against.
- Not a way to pad commits with "review pass" busywork. If a change is small enough to review in your head, the loop is overhead.