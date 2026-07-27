---
name: write-release-notes
description: Use when adding or editing entries in doc/release-notes/dev.txt or any doc/release-notes/A.B.C.txt for the Rell project. Triggers on user-facing change documentation: language/library/runtime/compiler/tools/docs/tests/API additions, breaking changes, deprecations, bug fixes. Follows the formatting conventions in doc/release-notes-guide.md.
---

# Writing Rell Release Notes

User-facing changes in the Rell project are documented in `doc/release-notes/`:

- `dev.txt` — the unreleased changes file. Always edit this for ongoing work.
- `A.B.C.txt` — finalized notes for a released version (don't touch after release).

The authoritative formatting conventions live in [doc/release-notes-guide.md](../../../doc/release-notes-guide.md). Read it first if anything in this skill is unclear; it's the source of truth.

## Section header

Each section starts with a separator line of 80 `@` characters and a numbered, category-prefixed title:

```
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
N. Category: short title
```

Numbering restarts at 1 for each release file. Categories (one of):

- **Language** — syntax, types, new constructs
- **Library** — stdlib functions/types
- **Tools** — CLI, build, dev utilities
- **Runtime** — execution-engine behavior
- **Compiler** — compiler internals, error messages, performance
- **Docs** — documentation system, doc comments
- **Tests** — testing framework
- **API** — public API surface (incl. breaking changes here)

## Structure of an entry

1. **One-line summary** in the title (after the `Category:` prefix).
2. **Lead paragraph** — what changed and why, in one or two sentences. Plain English, not change-log shorthand.
3. **Body** — code samples, tables, or prose as needed.
4. **Compatibility note** — only if the change is breaking or deprecating something. Use the standard wording in the guide.

## Formatting rules

- **Code blocks**: 4-space indented (no triple-backticks). The release-notes file is plain text.
- **Tables**: Unicode box drawing (`┌─┬─┐ ├─┼─┤ └─┴─┘`).
- **Bulleted lists**: `-` for main points.
- **Numbered lists**: Arabic numerals for sequential reasoning.
- **Sub-items**: `(a)`, `(b)`, … for sub-categorization.
- Keep lines ≤ 120 chars where reasonable; the release file isn't strict about this but long lines are harder to review.
- No emojis.

## What goes in dev.txt

Add an entry whenever you ship a user-visible change:

- New language feature, syntax, or built-in type
- New stdlib function or method (with a code example showing usage)
- Compiler-error wording change that user code might match against
- Runtime behavior change (especially anything observable by deployed code)
- Tooling change (CLI flags, build config, scripts)
- Public API additions or breaking removals
- Bug fix that user code might have been relying on

Skip pure refactors that aren't observable by Rell users. If unsure, err on the side of writing it down — the release-notes file is the single source of truth that a Rell user reads after upgrading.

## What does NOT go in dev.txt

- Internal refactors with no user-visible effect.
- Test-only changes.
- Build-tool plumbing that users don't touch.
- Doc-only changes (those land in the guide directly).
- Routine dependency bumps. Only a Postchain *minor or major* upgrade earns an entry (`1. Upgrade to Postchain 3.7.0`); a Postchain patch bump and everything else — log4j, json-schema-validator, kotlinx-collections-immutable — gets nothing. A dependency change with a visible effect (changed on-disk format, new JDK requirement) is written up as that effect, not as a version bump.

## Editing flow

1. Open `doc/release-notes/dev.txt`.
2. Pick the right Category. If a similar section already exists, append to it rather than starting a new one.
3. If starting a new section, use the next free number (continue the existing sequence — don't restart).
4. Draft the lead paragraph in plain English, then add code/tables/lists as needed.
5. For breaking changes, include the standard wording:

       Note. This is a breaking change - it may break compilation of existing code, …

6. For deprecations, name the new replacement and the old name in one sentence.
7. Verify the [review checklist](../../../doc/release-notes-guide.md#review-checklist) before finishing:
   - Header format / category correct
   - Code examples 4-space indented
   - Breaking changes flagged
   - Examples actually compile/work
   - Cross-references resolve

## Cross-references

When referring to another section in the same file, use `§N` or "see §N" (lowercase section sign). Don't link to other release files unless absolutely necessary.

## Common pitfalls

- **Don't decide from first principles whether a change is worth an entry.** Grep the released `doc/release-notes/*.txt` for how that kind of change was handled before, and follow the precedent. A single past exception is not the pattern.
- **Don't introduce a new category prefix** without reason. Stick to the standard set above. If a change spans two categories, pick the most user-facing one.
- **Don't merge unrelated changes into one section** to save numbering. Each logical change gets its own section.
- **Don't omit code examples** for new library functions. The reader needs to see usage.
- **Don't use markdown** in the release-notes file. It's plain text.
- **Don't use the term "golden file"** for inline-string regression tests in dev.txt. The term implies a separate fixture file; if expectations are inlined in test source, call them "regression test" / "expected strings" / "captured output".
