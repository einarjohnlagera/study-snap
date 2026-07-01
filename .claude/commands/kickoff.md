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
- [ ] **`CLAUDE.md`** — Bump `Current version: vX.Y.Z`.
- [ ] **`frontend/package.json`** — Bump `"version": "X.Y.Z"`.
- [ ] **`backend/pom.xml`** — Bump `<version>X.Y.Z</version>` (project version, not a dependency).
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
git add RELEASES.md ROADMAP.md CLAUDE.md frontend/package.json backend/pom.xml AGENTS.md README.md
git commit -m "chore: kick off vX.Y.Z — [Theme Name]"
```

Commit directly on `releases/vX.Y.Z`. No sub-branch, no PR for this commit.

After kickoff, all feature/fix work branches off `releases/vX.Y.Z` via PR. The release branch only receives two direct commits: kickoff and signoff.
