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

# Group benchmarks/<slug> and profile/<slug> envs by slug, sort dev/master first.
table=$(printf '%s' "$envs_json" | jq -r '
  map(select(.name | test("^(benchmarks|profile)/")))
  | map({
      kind: (.name | capture("^(?<k>benchmarks|profile)/").k),
      slug: (.name | capture("^[^/]+/(?<s>.+)$").s),
      url:  .external_url,
      when: ((.updated_at // .created_at) | (.[0:10] // ""))
    })
  | group_by(.slug)
  | map({
      slug: .[0].slug,
      benchmarks: (map(select(.kind == "benchmarks")) | first),
      profile:    (map(select(.kind == "profile"))    | first)
    })
  | sort_by([(if .slug == "dev" then 0 elif .slug == "master" then 1 else 2 end), .slug])
  | if length == 0 then
      "<p class=\"empty-msg\">No reports published yet — trigger the manual jobs in a pipeline.</p>"
    else
      "<table><thead><tr><th>Branch</th><th>Benchmarks</th><th>Profile</th></tr></thead><tbody>"
      + (map(
          "<tr><td class=\"branch\">" + (.slug | @html) + "</td>"
          + ([.benchmarks, .profile] | map(
              if . == null then "<td class=\"empty\">—</td>"
              else "<td><a href=\"" + (.url | @html) + "\">report</a>"
                + (if .when != "" then "<span class=\"when\">· " + (.when | @html) + "</span>" else "" end)
                + "</td>"
              end
            ) | join(""))
          + "</tr>"
        ) | join(""))
      + "</tbody></table>"
    end
')

generated_at=$(date -u +"%Y-%m-%d %H:%M UTC")

awk -v content="$table" -v ts="$generated_at" '
  { gsub(/@CONTENT@/, content); gsub(/@GENERATED_AT@/, ts); print }
' "$template"
