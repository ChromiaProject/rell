---
name: release-rell
description: Use when the user asks to publish a new Rell release ("release A.B.C", "cut a release", "publish version A.B.C", "branch off A.B.C", "tag A.B.C"). Walks through the multi-step procedure in doc/release-guide.md from finalising release notes on dev to creating the version branch, tagging, announcing, and post-release cleanup back on dev.
---

# Publishing a Rell release `A.B.C`

The authoritative procedure is in [doc/release-guide.md](../../../doc/release-guide.md). This skill is a checklist for the assistant — read the doc when in doubt; it's the source of truth.

**Hard rules** before doing any of this:
- Confirm with the user the exact `A.B.C` to release before mutating anything.
- Never push to `origin/dev`, push the `version-A.B.C` branch, create the Git tag, or post to Zulip without an explicit go-ahead from the user. Ask once per push/tag/announce step.
- Never amend or force-push.
- Never delete or rename files outside the steps below.
- **The release is not done until the tag `A.B.C` exists on `origin`.** A green pipeline on `version-A.B.C` is not the finish line. Before declaring the release complete, run `git ls-remote --tags origin A.B.C` and confirm it returns a SHA. If it doesn't, you skipped Phase 3 — go back and tag.
- **Never judge tag state from local tags.** `git tag -l`, `git tag A.B.C`, and `git describe` all read the local namespace, which in older clones is polluted with stale local-only tags — including names in the live `0.16.x` range — that point at unrelated `dev` merge commits and were never on `origin`. `git ls-remote --tags origin` is the only source of truth. This is exactly how `0.16.1` shipped untagged: the local `0.16.1` tag already existed (pointing at an ancient merge), `git tag` failed, and the failure was read as "already tagged".

## Phase 1 — Finalise release notes on `dev`

On the `dev` branch:

1. Open `doc/release-notes/dev.txt`. Verify it documents every user-facing change since the last release (use `git log` against the last release tag if needed). If anything is missing, invoke the `write-release-notes` skill or ask the user to fill the gaps before proceeding.
2. Apply the [review checklist](../../../doc/release-notes-guide.md#review-checklist) to `dev.txt` (header, categories, 4-space code blocks, breaking-change flags, code examples that actually compile).
3. **Rename**: `git mv doc/release-notes/dev.txt doc/release-notes/A.B.C.txt`.
4. **Edit the renamed file**: replace the `UNRELEASED NOTES` first line with `RELEASE NOTES A.B.C (YYYY-MM-DD)` using **today's actual date** (run `date +%Y-%m-%d` to be sure).
5. **Create a fresh `dev.txt`** containing only the line `UNRELEASED NOTES` and a trailing newline.
6. **Replace `RellVersions.SINCE_NOW` with `"A.B.C"`** in stdlib source files. Use `grep -rn --include='*.kt' 'RellVersions.SINCE_NOW' rell-base` (excluding the `SINCE_NOW` definition itself in `rell-base/utils/src/utils/RellVersions.kt`) to find every site, then replace each with the literal `"A.B.C"`. This must happen on `dev` (NOT only on the release branch) so the version-history annotations are preserved on `dev` after the branch cuts.
7. Commit on `dev` with a clear message: `Finalize release notes and version annotations for A.B.C`.

Stop and ask the user before pushing this commit.

## Phase 2 — Cut the release branch and bump version

```bash
git checkout -b version-A.B.C
```

Bump the version in **three** places (all in one commit):

- `build.gradle.kts` — change `version = "..."` to `version = "A.B.C"` (no `-SNAPSHOT`).
- `rell-base/utils/src/utils/RellVersions.kt` — change `VERSION_STR` to `"A.B.C"`.
- `rell-base/utils/src/utils/RellVersions.kt` — append `"A.B.C"` to the `SUPPORTED_VERSIONS` list. The `init { check(VERSION in SUPPORTED_VERSIONS) }` block runs at class load and will fail every test in CI if you skip this. Same edit is repeated on `dev` in Phase 5 step 3.

Commit on `version-A.B.C` with: `Bump version to A.B.C`.

**Pushing this branch triggers GitLab CI which auto-publishes the release** — confirm with the user before `git push -u origin version-A.B.C`.

## Phase 3 — Tag the release commit (mandatory)

**Do not skip this phase.** Pushing the version branch starts the publish pipeline, but only the `A.B.C` tag marks the release as cut — every prior release has one on `origin`. After CI succeeds (verify in GitLab), tag the release commit:

```bash
# Verify CI is green on the tip first
glab api "projects/chromaway%2Frell/pipelines?ref=version-A.B.C&per_page=1"

# Push the tag straight to origin by SHA — no local tag involved
SHA=$(git rev-parse origin/version-A.B.C)
git push origin "$SHA:refs/tags/A.B.C"

# Confirm the tag is on the remote
git ls-remote --tags origin A.B.C
```

Push by SHA rather than `git tag A.B.C && git push origin A.B.C`: the two-step form dies on a stale local tag of the same name (see the hard rules above), and its failure is easy to misread as "already tagged".

Confirm with the user before pushing the tag. After pushing, the `git ls-remote` check is the gate to Phase 4 — if the tag isn't on `origin`, the release is not done.

## Phase 4 — Announce on Zulip

After CI succeeds, announce the release on Zulip. The user usually does this manually; offer to draft the message but don't post automatically — Zulip is a shared destination.

## Phase 5 — Post-release cleanup on `dev`

Switch back to `dev` and apply these follow-ups in one commit (or a tightly grouped sequence):

1. **`doc/release-notes/all-releases.txt`** — prepend the new release at the top of the list:

   ```
   - A.B.C
     Notes: A.B.C.txt
     GitLab: https://gitlab.com/chromaway/rell/-/tree/<commit-sha>/
   ```

   This file is the hand-maintained index of every published release — version, notes filename, and the tree of the commit it was built from. Nothing generates or checks it, so a skipped update goes unnoticed. Rules:

   - Insert directly under the blank line after the `1. Releases: List of all Rell versions` heading, above the previous release. Never append at the bottom.
   - Plain text, no markdown and no backticks, same as the release notes.
   - `Notes:` is a bare filename relative to `doc/release-notes/`; the file must exist on `dev` (step 2 below).
   - `GitLab:` uses the full 40-character SHA of the **tagged** commit — the one `git ls-remote --tags origin A.B.C` returned — with a trailing slash. Not `dev`'s tip, and not the pre-fix commit if CI forced an extra commit onto the version branch.
   - Only released versions are listed; the list legitimately has gaps (0.14.4, 0.14.6, …) and never mentions `dev.txt`.
   - Leave the `ALL RELEASES (YYYY-MM-DD)` header date alone — it tracks restructurings of the file, not individual entries.

   Full description: [doc/release-guide.md](../../../doc/release-guide.md#the-all-releasestxt-index).

2. **Add the release-notes file to `dev`** — copy `doc/release-notes/A.B.C.txt` from the release branch back into `dev` so the full history is on `dev` too. (`git checkout version-A.B.C -- doc/release-notes/A.B.C.txt`.)

3. **Add the released version to `SUPPORTED_VERSIONS`** — in `RellVersions.kt` on `dev`, append `"A.B.C"` to the `SUPPORTED_VERSIONS` list. The release branch removed the dev-version entry, but `dev` must keep listing every released version.

4. **If this was a major release** (the `A` or `B` component changed), bump the dev snapshot:

   - `RellVersions.kt`: `const val VERSION_STR = "0.(B+1).0-SNAPSHOT"`
   - `build.gradle.kts`: `version = "0.(B+1).0-SNAPSHOT"`

   For a patch release (only `C` changed), leave the dev snapshot version alone.

Commit with: `Post-release cleanup for A.B.C`. Ask the user before pushing.

## Things that go wrong

- **CI fails on the version branch**: don't tag. Investigate the failure, push a fix as a new commit on `version-A.B.C` (not an amend — preserve the failed commit for diagnosis), wait for green CI, then tag the latest commit. Update the SHA in the all-releases entry to match.
- **Forgot to replace `SINCE_NOW` on `dev` before branching**: cherry-pick the replacement commit onto both `dev` and `version-A.B.C`. The `since` annotations are version-history metadata; losing them on `dev` is a real defect.
- **`git tag A.B.C` fails with `fatal: tag 'A.B.C' already exists`, or `git fetch --tags` says `[rejected] ... (would clobber existing tag)`**: a stale local-only tag, not a tagged release. Check `git ls-remote --tags origin A.B.C` — if it returns nothing, push by SHA (`git push origin <sha>:refs/tags/A.B.C`). Clean up locally afterwards only if you want working local tags: `git tag -d A.B.C && git fetch --tags --force`.
- **A released version is missing from `all-releases.txt`**: Phase 5 step 1 was skipped for it. Fix it by inserting the entry in version order (not necessarily at the top, if newer releases were added meanwhile), using the SHA from `git ls-remote --tags origin <version>`. Nothing detects this automatically — when cutting a release, check that the previous release is present before prepending the new one.
- **Tag pushed to wrong commit**: delete the remote tag (`git push origin :refs/tags/A.B.C`), retag locally on the right SHA, push. Coordinate with anyone who may have already pulled the tag.
- **Patch release vs. major release confusion**: a patch release (only `C` changed) does NOT bump the dev snapshot; a major release (`A` or `B` changed) does.
- **CI fails with `IllegalStateException` from `RellVersions.<init>` and 100% test failures**: you bumped `VERSION_STR` but forgot to add the new version to `SUPPORTED_VERSIONS` on the release branch. Push a follow-up commit appending `"A.B.C"` to the list (see Phase 2).
- **Hotfix off `master` (or any non-`dev` branch)**: when the release isn't cut from `dev` (e.g. a hotfix branch holds the only commits going into the release), treat the hotfix branch as `dev` for Phases 1–2. Phase 5 cleanup (`all-releases.txt`, copy notes file, append to `SUPPORTED_VERSIONS`) still needs to land on `dev` — ask the user whether to cherry-pick or merge.

## When NOT to use this skill

- The user asks about an unreleased feature or release-notes content. Use `write-release-notes` instead.
- The user wants to undo a release. The release procedure is one-way; ask explicitly what they want before touching tags or branches.
