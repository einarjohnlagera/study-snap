#!/usr/bin/env bash
#
# check-deploys.sh — does what is actually deployed match `main`?
#
# WHY THIS EXISTS
# ---------------
# On 2026-09-09 the v0.136.0 merge to `main` (4ee2c752) did not auto-deploy on
# EITHER platform. Render's config was correct and every prior release had fired
# in 2-3 seconds; Vercel had a Production deployment for every prior release
# merge and none for this one. Both are GitHub App integrations consuming the
# same push event, and neither received it. GitHub declared no incident.
#
# The result was a live version skew: a v0.136.0 backend served a v0.135.0
# frontend, and v0.135.0 called an endpoint form v0.136.0 had made a 400. The
# shipped feature was dead on arrival and nothing noticed for six hours.
#
# ⚠️ THE KEY DESIGN POINT: Render's service carries notifyOnFail, and a
# notify-on-FAILURE cannot detect a deploy that never STARTED. Nothing failed
# here; nothing was ever queued. So this script tests for ABSENCE, not failure.
#
# ⚠️ AND IT NEVER PASSES WHEN IT CANNOT CHECK. A missing RENDER_API_KEY exits
# non-zero, because "I could not look" reported as "all clear" is the same
# failure class this script exists to catch.
#
# USAGE
#   RENDER_API_KEY=rnd_xxx scripts/check-deploys.sh
#
#   Exit 0  both platforms serve the same commit as origin/main
#   Exit 1  drift, or a platform has no successful deploy for that commit
#   Exit 2  could not check (missing dependency, missing key, API error)
#
set -uo pipefail

REPO="${DEPLOY_CHECK_REPO:-einarjohnlagera/study-snap}"
RENDER_SERVICE_ID="${RENDER_SERVICE_ID:-srv-d6u0jkvgi27c73dvl9k0}"

red()   { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
warn()  { printf '\033[33m%s\033[0m\n' "$*"; }

for dep in git gh jq curl; do
  command -v "$dep" >/dev/null 2>&1 || { red "CANNOT CHECK: '$dep' is not installed."; exit 2; }
done

git fetch --quiet origin main 2>/dev/null || { red "CANNOT CHECK: could not fetch origin/main."; exit 2; }
MAIN_SHA="$(git rev-parse origin/main)" || exit 2
printf 'origin/main  %s  %s\n\n' "${MAIN_SHA:0:8}" "$(git log -1 --format=%s "$MAIN_SHA" | cut -c1-64)"

drift=0

# --- Vercel -----------------------------------------------------------------
# Read through GitHub's deployments API rather than Vercel's: vercel[bot]
# records every Production deployment there, so this needs no extra secret.
vercel_json="$(gh api "repos/${REPO}/deployments?environment=Production&per_page=10" 2>/dev/null)" || {
  red "CANNOT CHECK Vercel: GitHub deployments API call failed."; exit 2; }

vercel_sha="$(printf '%s' "$vercel_json" | jq -r 'map(select(.sha != null)) | (first // {}) | .sha // ""')"
if [ -z "$vercel_sha" ]; then
  red "VERCEL: no Production deployment found at all."
  drift=1
elif [ "$vercel_sha" != "$MAIN_SHA" ]; then
  red "VERCEL: serving ${vercel_sha:0:8}, but origin/main is ${MAIN_SHA:0:8} — BEHIND."
  drift=1
else
  dep_id="$(printf '%s' "$vercel_json" | jq -r 'first | .id')"
  state="$(gh api "repos/${REPO}/deployments/${dep_id}/statuses" --jq '.[0].state // "unknown"' 2>/dev/null)"
  if [ "$state" = "success" ]; then
    green "VERCEL: ${vercel_sha:0:8}, state=success — matches main."
  else
    red "VERCEL: ${vercel_sha:0:8} matches main but state=${state} — NOT serving."
    drift=1
  fi
fi

# --- Render -----------------------------------------------------------------
# Render creates no GitHub deployment records, so this needs the Render API.
# Keep the key in the local environment; it must never become a repo secret.
if [ -z "${RENDER_API_KEY:-}" ]; then
  red "CANNOT CHECK Render: RENDER_API_KEY is not set."
  warn "  Refusing to report all-clear on a platform that was not checked."
  exit 2
fi

render_json="$(curl -sS --max-time 20 -H "Authorization: Bearer ${RENDER_API_KEY}" \
  "https://api.render.com/v1/services/${RENDER_SERVICE_ID}/deploys?limit=20" 2>/dev/null)" || {
  red "CANNOT CHECK Render: API request failed."; exit 2; }

printf '%s' "$render_json" | jq -e 'type == "array"' >/dev/null 2>&1 || {
  red "CANNOT CHECK Render: unexpected API response."; exit 2; }

# Render's v1 API wraps each entry as {"deploy": {...}}, but some clients hand
# back the unwrapped object. Accept both rather than guess — `.deploy // .`.
render_sha="$(printf '%s' "$render_json" \
  | jq -r '[.[] | (.deploy // .) | select(.status == "live")] | (first // {}) | .commit.id // ""')"

if [ -z "$render_sha" ]; then
  red "RENDER: no live deploy found."
  drift=1
elif [ "$render_sha" != "$MAIN_SHA" ]; then
  red "RENDER: serving ${render_sha:0:8}, but origin/main is ${MAIN_SHA:0:8} — BEHIND."
  drift=1
else
  green "RENDER: ${render_sha:0:8} live — matches main."
fi

echo
if [ "$drift" -ne 0 ]; then
  red "DRIFT: what is deployed does not match main."
  warn "Redeploy the lagging platform before signing off."
  warn "⚠️ If only ONE side is behind, treat the API contract as skewed: check whether"
  warn "   this release removed, renamed or made-required an endpoint form."
  exit 1
fi

green "OK: both platforms serve origin/main."
