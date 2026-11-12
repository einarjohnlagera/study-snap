---
name: kickoff
description: Open a new release version. Creates the releases/vX.Y.Z branch and makes the required 7-file atomic commit directly on it before any feature work starts. Use when starting a new release cycle after the previous version has been signed off.
argument-hint: <version> <theme>
---

You are opening a new NoteLib release. The argument is the version number and theme (e.g. `v0.35.0 "Feature Name"`).

## Pre-flight

```bash
git branch --show-current   # must NOT already be on releases/<version>
```

If the release branch doesn't exist yet:
```bash
git checkout -b releases/<version>
```

Run `/version-check` first to confirm what version you are bumping FROM.

## The 7-File Checklist

Make ALL changes before committing — this is one atomic commit.

- [ ] **`RELEASES.md`** — Add new `## vX.Y.Z - Theme` section at top (Status: In Progress). Mark the *prior* version Released.
- [ ] **`ROADMAP.md`** — Add new version section. Update "Current Release Baseline" line.
- [ ] **`CLAUDE.md`** — **REPLACE** the `Current version:` block with the new release's. **⚠️ REPLACE, NOT PREPEND — this is the step whose absence cost ~80,000 tokens per session.** That line grew to **322,329 characters** (63 chained `Previous:` blocks, `v0.37.0` → `v0.125.0`, ~90% of the file) because every kickoff prepended and none ever removed. `RELEASES.md` is the canonical record and always was; this line is a pointer to the OPEN release, never a history. Keep the product description above it and the anti-drift of the release you are opening — nothing else.
- [ ] **`frontend/package.json`** — Bump `"version": "X.Y.Z"`.
- [ ] **`backend/pom.xml`** — Bump `<version>X.Y.Z</version>` (project version, not a dependency) **ONLY when the release changes backend code** (Product UX, 2026-11-11, from `v0.181.0`). A frontend-only or environment-only release leaves it at the last backend release's version and says so in its `RELEASES.md` section (a "Backend version" sentence), because a change under `backend/` makes Render redeploy on merge. **A missing bump does not by itself prove Render will skip a deployment:** it redeploys when ANY commit in the pushed range changes `backend/` against its first parent (a merge-forward commit counts). For a frontend-only release, squash-merge the release PR and run the pre-merge range check from the sign-off command.
- [ ] **`AGENTS.md`** — Update documentation baseline and version reference line.
- [ ] **`README.md`** — Update release baseline line.

## RELEASES.md New Section Template

```markdown
## vX.Y.Z - [Theme Name]

**Status: In Progress**

Theme: [one sentence: what this release achieves for the user]

### Planned Scope

- **[Feature 1] (backend + frontend).** [What it does and why]
- **[Feature 2] (frontend).** [What it does and why]

Anti-drift: [locked rules — what this release does NOT change]

### Shipped

_(nothing yet)_
```

## Commit

```bash
git add RELEASES.md ROADMAP.md CLAUDE.md frontend/package.json AGENTS.md README.md
# add backend/pom.xml ONLY for a release that changes backend code (see the checklist above)
git commit -m "chore: kick off vX.Y.Z — [Theme Name]"
```

Commit directly on `releases/vX.Y.Z`. No sub-branch, no PR for this commit.

After kickoff, all feature/fix work branches off `releases/vX.Y.Z` via PR. The release branch only receives two direct commits: kickoff and signoff.
