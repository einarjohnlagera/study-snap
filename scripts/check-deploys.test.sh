#!/usr/bin/env bash
#
# check-deploys.test.sh — guards for check-deploys.sh's EXIT-CODE contract.
#
# ⚠️ THE EXIT CODE IS THE WHOLE SUBJECT, AND THAT IS NOT A STYLE CHOICE.
# The v0.139.0 defect was NOT a missing message: against the real 2026-09-10 miss
# the script already printed "VERCEL: serving 98ef1955 ... BEHIND" and then exited
# 2, which its own contract defines as "could not check" rather than "drift". So a
# test that asserts the OUTPUT passes under both the defect and the fix and proves
# nothing. Every case below asserts the exit code; output is only ever an extra.
#
# Method: stub `gh`, `curl`, `git` and `jq`... no — jq is real, because the script's
# jq filters are part of what we are testing. Only the three NETWORK/REPO commands
# are stubbed, by putting a fake bin dir first on PATH.
#
# Usage: scripts/check-deploys.test.sh     (exit 0 = all guards pass)

set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="${SCRIPT_DIR}/check-deploys.sh"
STUB_ROOT="$(mktemp -d)"
trap 'rm -rf "$STUB_ROOT"' EXIT

MAIN_SHA="1111111111111111111111111111111111111111"
OLD_SHA="2222222222222222222222222222222222222222"

pass=0
fail=0

# Build a stub bin dir. $1 = vercel sha to report ("" = no deployments, "ERR" =
# API failure). $2 = render sha ("" = none live, "ERR" = API failure).
make_stubs() {
  local vercel_sha="$1" render_sha="$2" bin="$STUB_ROOT/bin"
  rm -rf "$bin"; mkdir -p "$bin"

  cat > "$bin/git" <<EOF
#!/usr/bin/env bash
case "\$*" in
  *"rev-parse"*)  echo "$MAIN_SHA" ;;
  *"fetch"*)      exit 0 ;;
  *"log"*)        echo "a commit subject" ;;
  *)              exit 0 ;;
esac
EOF

  cat > "$bin/gh" <<EOF
#!/usr/bin/env bash
if [ "$vercel_sha" = "ERR" ]; then exit 1; fi
case "\$*" in
  *"/statuses"*)  echo "success" ;;
  *"deployments"*)
    if [ -z "$vercel_sha" ]; then echo "[]";
    else echo '[{"sha":"$vercel_sha","id":9}]'; fi ;;
  *) echo "[]" ;;
esac
EOF

  cat > "$bin/curl" <<EOF
#!/usr/bin/env bash
if [ "$render_sha" = "ERR" ]; then exit 1; fi
if [ -z "$render_sha" ]; then echo "[]";
else echo '[{"deploy":{"status":"live","commit":{"id":"$render_sha"}}}]'; fi
EOF

  chmod +x "$bin/git" "$bin/gh" "$bin/curl"
  echo "$bin"
}

# check <name> <expected_exit> <vercel_sha> <render_sha> [RENDER_KEY_SET]
check() {
  local name="$1" expected="$2" vsha="$3" rsha="$4" keyset="${5:-yes}"
  local bin; bin="$(make_stubs "$vsha" "$rsha")"
  local out actual
  if [ "$keyset" = "yes" ]; then
    out="$(PATH="$bin:$PATH" RENDER_API_KEY=stub bash "$TARGET" 2>&1)"; actual=$?
  else
    out="$(PATH="$bin:$PATH" env -u RENDER_API_KEY bash "$TARGET" 2>&1)"; actual=$?
  fi
  if [ "$actual" -eq "$expected" ]; then
    printf '  ok    %-58s exit=%s\n' "$name" "$actual"; pass=$((pass+1))
  else
    printf '  FAIL  %-58s exit=%s want=%s\n' "$name" "$actual" "$expected"
    printf '%s\n' "$out" | sed 's/^/          | /'
    fail=$((fail+1))
  fi
}

echo "check-deploys.sh — exit-code contract"
echo

# ⚠️ THE REGRESSION GUARD. This is the exact 2026-09-10 situation: Vercel is
# definitively behind AND Render cannot be checked. Before the fix this exited 2
# ("could not check"), discarding a confirmed finding. It MUST be 1.
check "Vercel BEHIND + no RENDER_API_KEY  => DRIFT, not unknown" 1 "$OLD_SHA" "$MAIN_SHA" no

# The case that must NOT regress: nothing wrong on Vercel, Render unreadable.
# "I could not look" must still never be reported as all-clear.
check "Vercel OK + no RENDER_API_KEY      => cannot check"       2 "$MAIN_SHA" "$MAIN_SHA" no

# Both readable and matching.
check "both match                          => ok"                0 "$MAIN_SHA" "$MAIN_SHA"

# Ordinary drift, both readable.
check "Vercel BEHIND, Render OK            => drift"             1 "$OLD_SHA"  "$MAIN_SHA"
check "Render BEHIND, Vercel OK            => drift"             1 "$MAIN_SHA" "$OLD_SHA"

# Absence, which is the condition this script exists to detect.
check "no Vercel deployment at all         => drift"             1 ""         "$MAIN_SHA"
check "no live Render deploy               => drift"             1 "$MAIN_SHA" ""

# ⚠️ Symmetric to the regression guard: a confirmed RENDER drift must survive an
# unreadable Vercel. The old code exited before Render was ever queried.
check "Render BEHIND + Vercel API failure  => DRIFT, not unknown" 1 "ERR"     "$OLD_SHA"

# Neither side readable: genuinely unknown.
check "both unreadable                     => cannot check"      2 "ERR"      "ERR" no

echo
if [ "$fail" -ne 0 ]; then
  printf '%s passed, %s FAILED\n' "$pass" "$fail"; exit 1
fi
printf '%s passed\n' "$pass"
