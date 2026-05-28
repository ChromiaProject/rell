#!/bin/sh
# Upserts a single "Dev Portal deployments" note on the current merge request listing the Pages
# deployments published by the manual `pages:*` jobs. Called once per dispatched portal job (via the
# `pages:mr-comment:after-*` follow-ups); the first call creates the note, later calls for other
# kinds — or re-runs of the same kind — edit it in place instead of stacking new comments.
#
# The note is found by a hidden marker; its current contents are kept in a hidden JSON blob
# (`rell-portal-state`) keyed by deployment kind, so each call only has to merge in its own row and
# re-render. The `pages:mr-comment:after-*` jobs share a per-MR `resource_group`, so these
# read-modify-write cycles run one at a time and can't race into duplicate notes.
#
# Auth: QODANA_GITLAB_TOKEN — the `qodana-ci` project access token (api scope, Reporter). Unlike
# GITLAB_TOKEN it is *not* protected, so it is exposed on MR/feature branches; a Reporter can create
# and edit MR notes. CI_JOB_TOKEN can't (the notes endpoint isn't on its allowlist — 403/404).
#
# Inputs (from GitLab CI): CI_API_V4_URL, CI_PROJECT_ID, CI_MERGE_REQUEST_IID, QODANA_GITLAB_TOKEN,
#   and PORTAL_KEY / PORTAL_BULLET from the upstream job's dotenv artifact.
set -eu

: "${CI_MERGE_REQUEST_IID:?only meaningful in a merge request pipeline}"
: "${QODANA_GITLAB_TOKEN:?QODANA_GITLAB_TOKEN is required (qodana-ci token, api scope)}"
: "${PORTAL_KEY:?PORTAL_KEY is required (set in the upstream pages job dotenv)}"
: "${PORTAL_BULLET:?PORTAL_BULLET is required (set in the upstream pages job dotenv)}"

api="$CI_API_V4_URL/projects/$CI_PROJECT_ID/merge_requests/$CI_MERGE_REQUEST_IID/notes"
auth="PRIVATE-TOKEN: $QODANA_GITLAB_TOKEN"
marker="<!-- rell-portal-links -->"
state_open="<!-- rell-portal-state:"
state_close=" -->"

# Locate our note (if any) and recover the per-kind state stashed in it. Page through all notes:
# on a chatty MR the portal note can drift past the first page, and missing it would stack a
# duplicate instead of editing.
note_id=""
note_body=""
page=1
while :; do
  batch=$(curl -fsS --header "$auth" "$api?per_page=100&page=$page")
  count=$(printf '%s' "$batch" | jq 'length')
  [ "$count" -eq 0 ] && break
  found=$(printf '%s' "$batch" | jq -c --arg m "$marker" 'map(select(.body | contains($m))) | .[0] // empty')
  if [ -n "$found" ]; then
    note_id=$(printf '%s' "$found" | jq -r '.id')
    note_body=$(printf '%s' "$found" | jq -r '.body')
    break
  fi
  [ "$count" -lt 100 ] && break
  page=$((page + 1))
done

state='{}'
if [ -n "$note_id" ]; then
  prev=$(printf '%s\n' "$note_body" | grep -o "$state_open.*$state_close" \
    | sed "s|^$state_open||; s|$state_close\$||" || true)
  [ -n "$prev" ] && state="$prev"
fi

# Merge in this kind's row and re-render newest contents.
state=$(printf '%s' "$state" | jq -c --arg k "$PORTAL_KEY" --arg v "$PORTAL_BULLET" '. + {($k): $v}')
bullets=$(printf '%s' "$state" | jq -r 'to_entries | sort_by(.key) | .[] | "- " + .value')

body=$(printf '%s\n\n### Dev Portal deployments\n\n%s\n\n<sub>Updated automatically when a manual `pages:*` job is dispatched on this MR.</sub>\n%s%s%s\n' \
  "$marker" "$bullets" "$state_open" "$state" "$state_close")

if [ -n "$note_id" ]; then
  curl -fsS --request PUT --header "$auth" --data-urlencode "body=$body" "$api/$note_id" >/dev/null
  echo "Updated MR !$CI_MERGE_REQUEST_IID portal note ($note_id) with '$PORTAL_KEY'"
else
  curl -fsS --request POST --header "$auth" --data-urlencode "body=$body" "$api" >/dev/null
  echo "Created MR !$CI_MERGE_REQUEST_IID portal note with '$PORTAL_KEY'"
fi
