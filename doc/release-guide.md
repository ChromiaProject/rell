# Release Guide

This document describes how to publish a new Rell release `A.B.C`.

## 1. Finalize Release Notes and Freeze Version Annotations

Before branching, prepare on `dev`:

1. Review and finalize `doc/release-notes/dev.txt`. Make sure all user-facing changes are documented and the content follows the formatting guidelines described in [doc/release-notes-guide.md](release-notes-guide.md).
2. Rename `dev.txt` to `doc/release-notes/A.B.C.txt`.
3. In the renamed file, replace the `UNRELEASED NOTES` header with:
   ```
   RELEASE NOTES A.B.C (YYYY-MM-DD)
   ```
   Use today's actual release date.
4. Double-check the file follows all review checklist items (see the [Review Checklist](release-notes-guide.md#review-checklist)).
5. Create a new blank `doc/release-notes/dev.txt` with just the header line:
   ```
   UNRELEASED NOTES
   ```
6. **Replace `RellVersions.SINCE_NOW` in standard library source files** &mdash; replace all uses of `RellVersions.SINCE_NOW` with the literal version string `"A.B.C"` in `since` annotations. This must be done on `dev` before branching so that both `dev` and the release branch carry the concrete version strings. If this step is deferred to the release branch, `dev` retains `SINCE_NOW` and the version history is lost there.
7. **Verify the replacement was complete** by running the release-mode guard:
   ```shell
   ./gradlew verifyNoSinceNow -PreleaseMode=true
   ```
   This fails the build and lists every offending file if any `RellVersions.SINCE_NOW` reference was missed. Without `-PreleaseMode=true` the task is a no-op, so it never affects regular builds.

Commit these changes to `dev`.

## 2. Create the Release Branch and Bump Version

Create a branch named `version-A.B.C` from `dev`:

```shell
git checkout -b version-A.B.C
```

Update the version in two places:

- **`build.gradle.kts`** &mdash; change `version = "..."` to the release version (without `-SNAPSHOT`):
  ```kotlin
  version = "A.B.C"
  ```

- **`rell-base/utils/src/utils/RellVersions.kt`** &mdash; change `VERSION_STR` to the release version:
  ```kotlin
  const val VERSION_STR = "A.B.C"
  ```

**`SUPPORTED_VERSIONS` on the release branch.** In the common case &mdash; the release is cut straight from `dev`'s tip as `dev`'s own next minor/major &mdash; `A.B.C` is already `dev`'s `VERSION_STR` and is already present in `SUPPORTED_VERSIONS` (added by the previous release's step 5.4 below), so nothing more to do here.

If `A.B.C` does **not** match `dev`'s currently-anticipated version &mdash; e.g. a patch release cut from `dev`'s current tip while `dev`'s own `VERSION_STR` is already snapshotted ahead at the next minor (`0.(B+1).0`) &mdash; the release branch's `SUPPORTED_VERSIONS` must reflect only what is actually shipping: remove `dev`'s not-yet-released anticipated version from the list and put `A.B.C` in its place instead of just appending. `dev` itself stays untouched by this (see step 5.3): its own `VERSION_STR` and `SUPPORTED_VERSIONS` snapshot identity is a separate, forward-looking concern from whatever gets tagged and shipped out of a point-in-time snapshot of it.

Commit and push the branch. Pushing the `version-A.B.C` branch triggers the GitLab CI pipeline, which publishes the release automatically.

## 3. Create the Release Tag

After the CI pipeline completes successfully, create and push a Git tag on the release commit (the last commit on the `version-A.B.C` branch):

```shell
git tag A.B.C <commit-sha>
git push origin A.B.C
```

## 4. Announce the Release

After the CI pipeline completes successfully, report the new version on **Zulip**.

## 5. Post-Release Cleanup on `dev`

Switch back to the `dev` branch and perform these follow-up steps:

1. **Update `doc/release-notes/all-releases.txt`** &mdash; add an entry for the new release at the top of the list:
   ```
   - A.B.C
     Notes: A.B.C.txt
     GitLab: https://gitlab.com/chromaway/rell/-/tree/<commit-sha>/
   ```
   Use the commit SHA of the release commit (the tagged commit).

2. **Add the release notes file to `dev`** &mdash; copy `doc/release-notes/A.B.C.txt` (as finalized on the release branch) into the `dev` branch so that the full release notes history is available on `dev`.

3. **Add the released version to `SUPPORTED_VERSIONS` on `dev`** &mdash; in `RellVersions.kt`, add `"A.B.C"` to the `SUPPORTED_VERSIONS` list, and only that: do not touch `VERSION_STR` or `build.gradle.kts`'s `version` here (that's step 4 below, and only for a major release). This is needed because the release branch's `SUPPORTED_VERSIONS` may be scoped to just what it shipped (see the note in step 2), but `dev` must recognize every released version, including ones that don't match its own current snapshot identity.

4. **If this was a major release** (A or B changed), update `VERSION_STR` in `dev` to the next development snapshot:
   ```kotlin
   // In RellVersions.kt on dev branch:
   const val VERSION_STR = "0.(B+1).0-SNAPSHOT"
   ```
   Also update `build.gradle.kts` accordingly:
   ```kotlin
   version = "0.(B+1).0-SNAPSHOT"
   ```
