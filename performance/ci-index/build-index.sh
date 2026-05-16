#!/bin/sh
# Builds the Rell performance reports index: a static client-side app (index.html) plus a
# manifest.json describing every currently-available benchmark / profile / regression Pages
# deployment. The Environments API requires authentication even on public projects, so the
# manifest is baked here at publish time; the browser app then fetches it — and the
# per-deployment data/main.json files it points at — anonymously.
#
# Auth: requires GITLAB_TOKEN (a project/group access token with `read_api` scope) exposed
# as a masked CI variable on dev. CI_JOB_TOKEN can't read the Environments API — it's not
# on GitLab's allowlist for that endpoint and returns 403.
#
# Inputs (from GitLab CI):  CI_PROJECT_ID, GITLAB_TOKEN.
# Usage:   build-index.sh <output-dir>
# Outputs: writes <output-dir>/index.html (the app) and <output-dir>/manifest.json (the data).
set -eu

: "${GITLAB_TOKEN:?GITLAB_TOKEN is required (project access token with read_api)}"

out_dir=${1:?usage: build-index.sh <output-dir>}
script_dir=$(dirname "$0")
mkdir -p "$out_dir"

envs_json=$(glab api --paginate \
  "projects/${CI_PROJECT_ID}/environments?states=available&per_page=100")

# Cross-reference with the live Pages deployments. Environment records persist after a
# path-prefixed Pages deployment is removed (expiry, manual delete), so the env-only view
# would link to 404s. The /pages endpoint is the authoritative list of currently-served
# deployments — match by path_prefix to drop ones whose content is gone.
#
# Best-effort: /projects/:id/pages requires Maintainer role, while GITLAB_TOKEN only needs
# `read_api` (Reporter) for the Environments API. On 403, fall back to no filtering — new
# deployments use `expire_in: never` so they don't rot, and stale historical entries will
# age out as their environments are removed.
if pages_json=$(glab api "projects/${CI_PROJECT_ID}/pages" 2>/dev/null); then
  active_prefixes=$(printf '%s' "$pages_json" | jq -c '[.deployments[].path_prefix | select(. != null)]')
else
  echo "warning: /projects/${CI_PROJECT_ID}/pages unavailable (token likely lacks Maintainer); skipping liveness filter" >&2
  active_prefixes=null
fi

# Resolve each deployment's commit SHA to its commit title via the Commits API, so the index
# shows commit messages rather than bare SHAs. `read_api` scope covers repository commits.
# One call per unique commit — fine for a manually-published index. A SHA that no longer
# resolves (force-push, deleted branch) is simply left without a title.
commit_titles="{}"
for sha in $(printf '%s' "$envs_json" | jq -r '
  [ .[] | select(.name | test("^(benchmarks|profile|regression)/[^/]+/[^/]+$"))
        | (.name | capture("/(?<s>[^/]+)$")).s ] | unique | .[]'); do
  title=$(glab api "projects/${CI_PROJECT_ID}/repository/commits/${sha}" 2>/dev/null \
            | jq -r '.title // empty' 2>/dev/null || true)
  if [ -n "$title" ]; then
    commit_titles=$(printf '%s' "$commit_titles" | jq --arg s "$sha" --arg t "$title" '. + {($s): $t}')
  fi
done

# Verify which benchmark/profile deployments actually serve `data/main.json`. Older runs
# predate the structured-data export, so the derived URL would 404 — and the compare tool
# must never offer a profile selection that can't be fetched. Probe each candidate over HTTP
# (Pages is served anonymously, the same assumption the browser app relies on) and keep only
# the URLs that resolve; jq attaches `data` only for these.
verified_data="[]"
for url in $(printf '%s' "$envs_json" | jq -r '
  [ .[] | select(.name | test("^(benchmarks|profile)/[^/]+/[^/]+$"))
        | select(.external_url != null)
        | (.external_url | sub("/report\\.html$"; "")) + "/data/main.json" ] | unique | .[]'); do
  code=$(curl -o /dev/null -s -L -w '%{http_code}' --max-time 15 "$url" || echo "000")
  if [ "$code" = "200" ]; then
    verified_data=$(printf '%s' "$verified_data" | jq --arg u "$url" '. + [$u]')
  else
    echo "note: no data/main.json at $url (HTTP $code) — omitting from manifest" >&2
  fi
done

generated_at=$(date -u +"%Y-%m-%d %H:%M UTC")

# Env names follow the schema `benchmarks/<branch>/<sha>`, `profile/<branch>/<sha>` and
# `regression/<branch>/<sha>` — per-commit so that same-branch reruns don't clobber each
# other (diffing two profiles needs both deployments coexisting). Group by (branch, sha)
# into one entry per commit; sort newest first within each branch, dev/master pinned to the
# top. `data` is the deployment's machine-readable JSON (kotlinx-benchmark scores for
# benchmarks, the profile summary written by writeProfileData() for profiles) — the app
# fetches those to diff two commits. It is attached only for deployments whose data URL was
# verified above; deployments that predate the export are left with just a `url`.
printf '%s' "$envs_json" | jq \
  --argjson active "$active_prefixes" \
  --argjson titles "$commit_titles" \
  --argjson verified "$verified_data" \
  --arg ts "$generated_at" '
  def branch_rank(b): if b == "dev" then 0 elif b == "master" then 1 else 2 end;
  # path_prefix mirrors the CI config: `benchmarks/dev/<sha>` -> `bench-dev-<sha>`,
  # `profile/dev/<sha>` -> `profile-dev-<sha>`, `regression/dev/<sha>` -> `regression-dev-<sha>`.
  def env_prefix(k; b; s):
    (if k == "benchmarks" then "bench"
     elif k == "regression" then "regression"
     else "profile" end) + "-" + b + "-" + s;
  # Every deployment serves its report at `<root>/report.html`; the parseable JSON sits at
  # `<root>/data/main.json`.
  def data_url(u): (u | sub("/report\\.html$"; "")) + "/data/main.json";
  # `data` is attached only when `withData` is set (benchmarks / profiles, not regression)
  # AND the data URL was confirmed reachable by the HTTP probe — `index` is null (falsy)
  # for an unverified URL, so the deployment falls through to a `url`-only entry.
  def link(entry; withData):
    if entry == null then null
    elif withData and (data_url(entry.url) as $d | $verified | index($d))
      then { url: entry.url, data: data_url(entry.url) }
    else { url: entry.url }
    end;

  {
    generated_at: $ts,
    commits: (
      map(select(.name | test("^(benchmarks|profile|regression)/[^/]+/[^/]+$")))
      | map(select(.external_url != null))
      | map(
          (.name | capture("^(?<k>benchmarks|profile|regression)/(?<b>[^/]+)/(?<s>[^/]+)$")) as $c
          | {
              kind: $c.k,
              branch: $c.b,
              sha: $c.s,
              url: .external_url,
              when: ((.updated_at // .created_at) | (.[0:10] // ""))
            }
        )
      | map(select($active == null or (env_prefix(.kind; .branch; .sha) as $p | $active | index($p))))
      | group_by([.branch, .sha])
      | map({
          branch: .[0].branch,
          sha: .[0].sha,
          title: ($titles[.[0].sha] // ""),
          when: (map(.when) | max),
          benchmarks: link((map(select(.kind == "benchmarks")) | first); true),
          profile:    link((map(select(.kind == "profile"))    | first); true),
          regression: link((map(select(.kind == "regression")) | first); false)
        })
      | sort_by([branch_rank(.branch), .branch, (.when // ""), .sha])
      | reverse
      | sort_by(branch_rank(.branch))   # stable sort preserves date-desc within branch
    )
  }
' > "$out_dir/manifest.json"

cp "$script_dir/index.html" "$out_dir/index.html"

echo "wrote $out_dir/manifest.json and $out_dir/index.html" >&2
