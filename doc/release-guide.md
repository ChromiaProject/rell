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

- **`rell-base/src/main/kotlin/net/postchain/rell/base/utils/RellVersions.kt`** &mdash; change `VERSION_STR` to the release version:
  ```kotlin
  const val VERSION_STR = "A.B.C"
  ```

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

3. **Add the released version to `SUPPORTED_VERSIONS` on `dev`** &mdash; in `RellVersions.kt`, add `"A.B.C"` to the `SUPPORTED_VERSIONS` list. This is needed because the release branch removes the current dev version from the list, but `dev` must know about all released versions.

4. **If this was a major release** (A or B changed), update `VERSION_STR` in `dev` to the next development snapshot:
   ```kotlin
   // In RellVersions.kt on dev branch:
   const val VERSION_STR = "0.(B+1).0-SNAPSHOT"
   ```
   Also update `build.gradle.kts` accordingly:
   ```kotlin
   version = "0.(B+1).0-SNAPSHOT"
   ```
