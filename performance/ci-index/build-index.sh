#!/bin/sh
# Renders performance/ci-index/index.html with a server-side list of currently-available
# benchmark / profile pages deployments. The project is private, so anonymous browser
# fetches against the Environments API return 401 — we bake the data here using
# CI_JOB_TOKEN and ship a fully static page.
#
# Inputs (from GitLab CI):  CI_API_V4_URL, CI_PROJECT_ID, CI_JOB_TOKEN.
# Outputs: writes the rendered HTML to stdout.
set -eu

template=$(dirname "$0")/index.html

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
: > "$tmp/all.json"

page=1
while [ "$page" -lt 20 ]; do
  body=$(curl -fsS \
    --header "JOB-TOKEN: ${CI_JOB_TOKEN}" \
    "${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/environments?states=available&per_page=100&page=${page}")
  count=$(printf '%s' "$body" | jq 'length')
  printf '%s' "$body" | jq -c '.[]' >> "$tmp/all.json"
  [ "$count" -lt 100 ] && break
  page=$((page + 1))
done

# Group benchmarks/<slug> and profile/<slug> envs by slug, sort dev/master first.
table=$(jq -rs '
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
' "$tmp/all.json")

generated_at=$(date -u +"%Y-%m-%d %H:%M UTC")

# Substitute in two passes; use awk so multiline `table` survives intact (sed would choke
# on the embedded newlines on some platforms).
awk -v content="$table" -v ts="$generated_at" '
  { gsub(/@CONTENT@/, content); gsub(/@GENERATED_AT@/, ts); print }
' "$template"
