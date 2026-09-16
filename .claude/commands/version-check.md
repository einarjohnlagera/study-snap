---
name: version-check
description: Verify all 7 NoteLib version references are in sync. Run before any kickoff or signoff commit to catch mismatches before they reach the release branch.
---

You are verifying version consistency across the NoteLib repo. Run these commands and confirm all 7 show the same version number.

```bash
grep -m1 "Current version" CLAUDE.md
grep -m1 "^## v" RELEASES.md
grep -i "current release baseline" docs/product/ROADMAP.md
grep -i "documentation baseline\|version reference" AGENTS.md | head -2
grep -i "release baseline" README.md
grep '"version"' frontend/package.json | head -1
grep -A1 "<artifactId>backend</artifactId>" backend/pom.xml | grep "<version>"
```

**⚠️ Do NOT use `grep -m1 "<version>" backend/pom.xml` for the last one** — the first `<version>` tag in
the file is `spring-boot-starter-parent`'s (e.g. `4.0.3`), several lines above the project's own
`<version>X.Y.Z</version>`. `-m1` silently returns the parent's version, which reads as a false
mismatch (or worse, a false pass if the two happen to collide). Anchor on the `<artifactId>backend</artifactId>`
line immediately above the real one instead. Found at the `v0.151.0` signoff.

## The 7 Locations

| File | Field |
|---|---|
| `CLAUDE.md` | `Current version: vX.Y.Z` |
| `RELEASES.md` | Topmost `## vX.Y.Z` section header |
| `ROADMAP.md` | "Current Release Baseline" line |
| `AGENTS.md` | Documentation baseline / version reference line |
| `README.md` | Release baseline line |
| `frontend/package.json` | `"version": "X.Y.Z"` |
| `backend/pom.xml` | `<version>X.Y.Z</version>` (project version, not a dependency) |

## On Mismatch

1. Identify which file is behind.
2. **During kickoff** — bump the stale file as part of the kickoff commit.
3. **During signoff** — find the commit that diverged and fix it on the release branch before signing off.
4. Never leave mismatched versions in a merged branch.
