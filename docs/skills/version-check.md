# /version-check — Version Consistency Check

Verify all 7 version references are in sync before kickoff or signoff.

A mismatch here causes confusing build failures, mismatched release notes, and wrong version banners in production. Run this before any release-management commit.

---

## The 7 Version Locations

| File | What to check |
|---|---|
| `CLAUDE.md` | `Current version: vX.Y.Z` near the top |
| `RELEASES.md` | The topmost `## vX.Y.Z` section header |
| `ROADMAP.md` | "Current Release Baseline" line |
| `AGENTS.md` | Documentation baseline and version reference line |
| `README.md` | Release baseline line |
| `frontend/package.json` | `"version": "X.Y.Z"` |
| `backend/pom.xml` | `<version>X.Y.Z</version>` (project version, not a dependency) |

---

## Quick Check Commands

```bash
# Print all 7 in one shot — they should all show the same version
grep -m1 "Current version" CLAUDE.md
grep -m1 "^## v" RELEASES.md
grep -i "current release baseline" ROADMAP.md
grep -i "documentation baseline\|version reference" AGENTS.md | head -2
grep -i "release baseline" README.md
grep '"version"' frontend/package.json | head -1
grep "<version>" backend/pom.xml | head -1
```

---

## What to Do on Mismatch

1. Identify which file is behind.
2. If you are in a kickoff: bump the stale file as part of the kickoff commit.
3. If you are in a signoff: the mismatch is a bug — find the commit that diverged and fix it on the release branch before signing off.
4. Never leave mismatched versions in a merged branch.

---

## When to Run

- **Before `/kickoff`** — confirm the version you are bumping FROM is consistent
- **Before `/signoff`** — confirm the version you are closing is consistent everywhere
- **After any Codex prompt** that mentions version bumps in its DOCUMENTATION section
