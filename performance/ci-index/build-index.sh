#!/bin/sh
# Builds the Rell Developer Portal index: a static client-side app (index.html) plus a
# manifest.json describing every currently-available docs / benchmark / profile / lsp / regression
# report — some served as GitLab Pages deployments (historical, from before those jobs moved off
# Pages — see the file-level comment in .gitlab/ci/pages.yml), some as plain CI job artifacts
# (current). Both sources require authentication even on this private project, so the manifest is
# baked here at publish time; the browser app then fetches it anonymously — but note that clicking
# through to a job-artifact link still requires the viewer to be logged into GitLab with project
# access, unlike the old Pages links which were served anonymously.
#
# Auth: requires GITLAB_TOKEN (a project/group access token with `read_api` scope) exposed
# as a masked CI variable on the protected dev and version-* branches. CI_JOB_TOKEN can't read
# the Environments/Jobs API — it's not on GitLab's allowlist for those endpoints and returns 403.
#
# Inputs (from GitLab CI):  CI_PROJECT_ID, CI_PROJECT_URL, GITLAB_TOKEN.
# Usage:   build-index.sh <output-dir>
# Outputs: writes <output-dir>/index.html (the app) and <output-dir>/manifest.json (the data).
set -eu

: "${GITLAB_TOKEN:?GITLAB_TOKEN is required (project access token with read_api)}"

out_dir=${1:?usage: build-index.sh <output-dir>}
script_dir=$(dirname "$0")
mkdir -p "$out_dir"

envs_json=$(glab api --paginate \
  "projects/${CI_PROJECT_ID}/environments?states=available&per_page=100")

# `glab api --paginate` emits one JSON array per page (`[...][...]`) rather than a single
# merged array, so once the environment count crosses the per_page=100 boundary `envs_json`
# becomes a multi-document stream. The final `jq` filter below would then run once per page
# and emit one manifest object per page — an invalid multi-document manifest.json that the
# browser app fails to parse. Merge the pages into a single array up front.
envs_json=$(printf '%s' "$envs_json" | jq -s 'add // []')

# Cross-reference with the live Pages deployments. Environment records persist after a
# path-prefixed Pages deployment is removed (expiry, manual delete), so the env-only view
# would link to 404s. The /pages endpoint is the authoritative list of currently-served
# deployments — match by path_prefix to drop ones whose content is gone.
#
# Best-effort: /projects/:id/pages requires Maintainer role, while GITLAB_TOKEN only needs
# `read_api` (Reporter) for the Environments API. On 403, fall back to no filtering — these
# deployments use `expire_in: never` so they don't rot, and stale historical entries will
# age out as their environments are removed.
if pages_json=$(glab api "projects/${CI_PROJECT_ID}/pages" 2>/dev/null); then
  active_prefixes=$(printf '%s' "$pages_json" | jq -c '[.deployments[].path_prefix | select(. != null)]')
else
  echo "warning: /projects/${CI_PROJECT_ID}/pages unavailable (token likely lacks Maintainer); skipping liveness filter" >&2
  active_prefixes=null
fi

# Resolve each Pages deployment's commit SHA to its commit title via the Commits API, so the
# index shows commit messages rather than bare SHAs. `read_api` scope covers repository commits.
# One call per unique commit — fine for a manually-published index. A SHA that no longer
# resolves (force-push, deleted branch) is simply left without a title. (Job-artifact rows below
# already carry their commit title straight from the Jobs API, no separate lookup needed.)
commit_titles="{}"
for sha in $(printf '%s' "$envs_json" | jq -r '
  [ .[] | select(.name | test("^(docs|benchmarks|profile|lsp|regression)/[^/]+/[^/]+$"))
        | (.name | capture("/(?<s>[^/]+)$")).s ] | unique | .[]'); do
  title=$(glab api "projects/${CI_PROJECT_ID}/repository/commits/${sha}" 2>/dev/null \
            | jq -r '.title // empty' 2>/dev/null || true)
  if [ -n "$title" ]; then
    commit_titles=$(printf '%s' "$commit_titles" | jq --arg s "$sha" --arg t "$title" '. + {($s): $t}')
  fi
done

# Verify which historical Pages benchmark/profile deployments actually serve `data/main.json`.
# Older runs predate the structured-data export, so the derived URL would 404 — and the compare
# tool must never offer a selection that can't be fetched. Probe each candidate over HTTP (Pages
# content is served anonymously, the same assumption the browser app relies on) and keep only the
# URLs that resolve.
verified_pages_data="[]"
for url in $(printf '%s' "$envs_json" | jq -r '
  [ .[] | select(.name | test("^(benchmarks|profile)/[^/]+/[^/]+$"))
        | select(.external_url != null)
        | (.external_url | sub("/report\\.html$"; "")) + "/data/main.json" ] | unique | .[]'); do
  code=$(curl -o /dev/null -s -L -w '%{http_code}' --max-time 15 "$url" || echo "000")
  if [ "$code" = "200" ]; then
    verified_pages_data=$(printf '%s' "$verified_pages_data" | jq --arg u "$url" '. + [$u]')
  else
    echo "note: no data/main.json at $url (HTTP $code) — omitting from manifest" >&2
  fi
done

# Normalise the Environments API records (one per historical Pages deployment) into flat rows:
# {kind, branch, sha, title, url, when, ts, data?}. `env_prefix`/`data_url` mirror the CI config's
# path_prefix scheme so the liveness filter and the data/main.json derivation match what pages.yml
# actually publishes (publishing has since moved to job artifacts, but old deployments are frozen
# in place and keep this shape).
env_rows=$(printf '%s' "$envs_json" | jq -c \
  --argjson active "$active_prefixes" \
  --argjson titles "$commit_titles" \
  --argjson verified "$verified_pages_data" '
  def env_prefix(k; b; s):
    (if k == "benchmarks" then "bench"
     elif k == "regression" then "regression"
     elif k == "docs" then "docs"
     elif k == "lsp" then "lsp"
     else "profile" end) + "-" + b + "-" + s;
  def data_url(u): (u | sub("/report\\.html$"; "")) + "/data/main.json";
  [ .[] | select(.name | test("^(docs|benchmarks|profile|lsp|regression)/[^/]+/[^/]+$"))
        | select(.external_url != null)
        | (.name | capture("^(?<k>docs|benchmarks|profile|lsp|regression)/(?<b>[^/]+)/(?<s>[^/]+)$")) as $c
        | select($active == null or (env_prefix($c.k; $c.b; $c.s) as $p | $active | index($p)))
        | {
            kind: $c.k,
            branch: $c.b,
            sha: $c.s,
            title: ($titles[$c.s] // ""),
            url: .external_url,
            when: ((.updated_at // .created_at) | (.[0:10] // "")),
            ts: ((.updated_at // .created_at) // "")
          }
        | if (.kind == "benchmarks" or .kind == "profile") and ($verified | index(data_url(.url)))
          then . + {data: data_url(.url)}
          else . end
  ]')

# New runs of the report jobs no longer create GitLab Pages deployments (see the file-level
# comment in .gitlab/ci/pages.yml); they publish their HTML/JSON purely as job artifacts.
# Discover recent ones via the Jobs API instead — it has no name/ref filter, so scan newest-first
# and stop after $JOB_SCAN_PAGES pages. This is a bounded, best-effort layer: a report job that
# hasn't been clicked in a very long time simply won't surface here, the same trade-off as the
# /pages liveness filter above.
JOB_SCAN_PAGES=${JOB_SCAN_PAGES:-10}
job_rows_file=$(mktemp)
echo '[]' > "$job_rows_file"
scanned=0
page=1
while [ "$page" -le "$JOB_SCAN_PAGES" ]; do
  batch=$(glab api "projects/${CI_PROJECT_ID}/jobs?scope[]=success&per_page=100&page=${page}")
  count=$(printf '%s' "$batch" | jq 'length')
  [ "$count" -eq 0 ] && break
  scanned=$((scanned + count))

  matched=$(printf '%s' "$batch" | jq -c '
    # GitLab timestamps carry millisecond fractions (".000Z"); fromdateiso8601 only accepts
    # whole seconds, so strip the fraction before parsing.
    def parse_ts: sub("[.][0-9]+Z$"; "Z") | fromdateiso8601;
    [ .[] | select(.name | test("^pages:(benchmarks|profile|lsp|regression|docs)$"))
          | select(.artifacts_file != null)
          | select(.artifacts_expire_at == null or (.artifacts_expire_at | parse_ts) > now)
          | {
              kind: (.name | sub("^pages:"; "")),
              branch: .ref,
              sha: .commit.short_id,
              title: (.commit.title // ""),
              when: ((.finished_at // .created_at) | .[0:10]),
              ts: (.finished_at // .created_at),
              job_id: .id
            }
    ]')
  tmp=$(mktemp)
  jq -c --argjson m "$matched" '. + $m' "$job_rows_file" > "$tmp" && mv "$tmp" "$job_rows_file"

  [ "$count" -lt 100 ] && break
  page=$((page + 1))
done
if [ "$page" -gt "$JOB_SCAN_PAGES" ]; then
  echo "note: stopped job scan after ${JOB_SCAN_PAGES} pages (${scanned} jobs) — older report-job runs beyond this window won't appear in the portal" >&2
fi

# Turn job_id into the artifact link (report.html, or index.html for docs — Dokka's landing page).
job_rows_file2=$(mktemp)
jq -c --arg proj "$CI_PROJECT_URL" '
  def entry_file(k): if k == "docs" then "index.html" else "report.html" end;
  map(. + { url: ($proj + "/-/jobs/" + (.job_id | tostring) + "/artifacts/file/public/" + entry_file(.kind)) } | del(.job_id))
' "$job_rows_file" > "$job_rows_file2"

# Probe benchmark/profile job-artifact runs for their data/main.json. Unlike Pages content, job
# artifacts on a private project aren't served anonymously, so this needs the same GITLAB_TOKEN
# used for the API calls above rather than a plain curl.
job_rows_final_file=$(mktemp)
echo '[]' > "$job_rows_final_file"
jq -c '.[]' "$job_rows_file2" | while IFS= read -r row; do
  kind=$(printf '%s' "$row" | jq -r '.kind')
  if [ "$kind" = "benchmarks" ] || [ "$kind" = "profile" ]; then
    url=$(printf '%s' "$row" | jq -r '.url')
    data_url=$(printf '%s' "$url" | sed 's|/report\.html$|/data/main.json|')
    code=$(curl -o /dev/null -s -L -w '%{http_code}' --max-time 15 --header "PRIVATE-TOKEN: $GITLAB_TOKEN" "$data_url" || echo "000")
    if [ "$code" = "200" ]; then
      row=$(printf '%s' "$row" | jq -c --arg d "$data_url" '. + {data: $d}')
    fi
  fi
  tmp=$(mktemp)
  jq -c --argjson r "$row" '. + [$r]' "$job_rows_final_file" > "$tmp" && mv "$tmp" "$job_rows_final_file"
done
job_rows=$(cat "$job_rows_final_file")
rm -f "$job_rows_file" "$job_rows_file2" "$job_rows_final_file"

generated_at=$(date -u +"%Y-%m-%d %H:%M UTC")

# Combine both sources into one flat row list and group into one manifest entry per (branch,
# sha) commit, with per-kind links. This is the same shape build-index.sh has always produced —
# it just now draws rows from two sources instead of one.
printf '%s' "$env_rows" | jq \
  --argjson job_rows "$job_rows" \
  --arg ts "$generated_at" '
  def branch_rank(b): if b == "dev" then 0 elif b == "master" then 1 else 2 end;
  def linkify(rows; k):
    (rows | map(select(.kind == k)) | first) as $r
    | if $r == null then null
      elif $r.data then {url: $r.url, data: $r.data}
      else {url: $r.url} end;
  (. + $job_rows) as $rows
  | {
      generated_at: $ts,
      commits: (
        $rows
        | group_by([.branch, .sha])
        | map({
            branch: .[0].branch,
            sha: .[0].sha,
            title: ((map(.title) | map(select(. != "")) | .[0]) // ""),
            when: (map(.when) | max),
            ts: (map(.ts) | max),
            docs:       linkify(.; "docs"),
            benchmarks: linkify(.; "benchmarks"),
            profile:    linkify(.; "profile"),
            lsp:        linkify(.; "lsp"),
            regression: linkify(.; "regression")
          })
        # Order newest-first within a branch. Sort on the full timestamp `ts`, not the date-only
        # `when`: same-day commits would otherwise tie and fall back to an arbitrary lexical SHA
        # order. `ts` is a UTC ISO-8601 string (from either the environment or the job), so a
        # plain lexical sort is chronological.
        | sort_by([branch_rank(.branch), .branch, .ts])
        | reverse
        | sort_by(branch_rank(.branch))   # stable sort preserves time-desc within branch
        | map(del(.ts))
      )
    }
' > "$out_dir/manifest.json"

cp "$script_dir/index.html" "$out_dir/index.html"

echo "wrote $out_dir/manifest.json and $out_dir/index.html" >&2
