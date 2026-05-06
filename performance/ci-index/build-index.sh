#!/bin/sh
# Renders performance/ci-index/index.html with a server-side list of currently-available
# benchmark / profile pages deployments. The project is private, so anonymous browser
# fetches against the Environments API return 401 — we bake the data here at publish time
# and ship a fully static page.
#
# Auth: requires GITLAB_TOKEN (a project/group access token with `read_api` scope) exposed
# as a masked CI variable on dev. CI_JOB_TOKEN can't read the Environments API — it's not
# on GitLab's allowlist for that endpoint and returns 403.
#
# Inputs (from GitLab CI):  CI_PROJECT_ID, GITLAB_TOKEN.
# Outputs: writes the rendered HTML to stdout.
set -eu

: "${GITLAB_TOKEN:?GITLAB_TOKEN is required (project access token with read_api)}"

template=$(dirname "$0")/index.html

envs_json=$(glab api --paginate \
  "projects/${CI_PROJECT_ID}/environments?states=available&per_page=100")

# Env names follow the schema `benchmarks/<branch>/<sha>` and `profile/<branch>/<sha>` —
# per-commit so that same-branch reruns don't clobber each other (regression analysis
# between commits needs both deployments coexisting). Group by (branch, sha) into one
# row per commit; sort newest first within each branch, with dev/master pinned to the top.
# Each benchmark deployment also exposes /data/main.json for programmatic ingestion.
table=$(printf '%s' "$envs_json" | jq -r '
  def branch_rank(b): if b == "dev" then 0 elif b == "master" then 1 else 2 end;

  map(select(.name | test("^(benchmarks|profile)/[^/]+/[^/]+$")))
  | map(select(.external_url != null))
  | map(
      (.name | capture("^(?<k>benchmarks|profile)/(?<b>[^/]+)/(?<s>[^/]+)$")) as $c
      | {
          kind: $c.k,
          branch: $c.b,
          sha: $c.s,
          url: .external_url,
          when: ((.updated_at // .created_at) | (.[0:10] // ""))
        }
    )
  | group_by([.branch, .sha])
  | map({
      branch: .[0].branch,
      sha: .[0].sha,
      when: (map(.when) | max),
      benchmarks: (map(select(.kind == "benchmarks")) | first),
      profile:    (map(select(.kind == "profile"))    | first)
    })
  | sort_by([branch_rank(.branch), .branch, (.when // ""), .sha])
  | reverse
  | sort_by(branch_rank(.branch))   # stable sort preserves date-desc within branch
  | if length == 0 then
      "<p class=\"empty-msg\">No reports published yet.</p>"
    else
      "<table><thead><tr><th>Branch</th><th>Commit</th><th>When</th><th>Benchmarks</th><th>Profile</th></tr></thead><tbody>"
      + (map(
          "<tr>"
          + "<td class=\"branch\">" + (.branch | @html) + "</td>"
          + "<td class=\"sha\">" + (.sha | @html) + "</td>"
          + "<td class=\"when\">" + (.when | @html) + "</td>"
          + (.benchmarks as $b
              | if $b == null then "<td class=\"empty\">—</td>"
                else "<td><a href=\"" + ($b.url | @html) + "\">report</a>"
                  + " <span class=\"sep\">·</span> "
                  + "<a href=\"" + (($b.url | sub("/report\\.html$"; "")) + "/data/main.json" | @html) + "\">json</a>"
                  + "</td>"
                end)
          + (.profile as $p
              | if $p == null then "<td class=\"empty\">—</td>"
                else "<td><a href=\"" + ($p.url | @html) + "\">report</a></td>"
                end)
          + "</tr>"
        ) | join(""))
      + "</tbody></table>"
    end
')

generated_at=$(date -u +"%Y-%m-%d %H:%M UTC")

awk -v content="$table" -v ts="$generated_at" '
  { gsub(/@CONTENT@/, content); gsub(/@GENERATED_AT@/, ts); print }
' "$template"
