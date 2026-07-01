---
name: signoff
description: Close a release version and mark it Released. Makes a 3-file atomic commit directly on the releases/vX.Y.Z branch, writes the release notes file, then opens a PR to main. Use when all planned scope has shipped and the release is ready to close.
argument-hint: <version>
---

You are closing a NoteLib release. The argument is the version number (e.g. `v0.34.0`).

## Pre-flight

```bash
git branch --show-current   # must be: releases/<version>
```

Run `/version-check` to confirm all 7 version references are consistent before closing.

Confirm all feature PRs are merged into the release branch:
```bash
git log --oneline releases/<version> | head -20
```

## The 3-File Checklist

Make ALL changes before committing — this is one atomic commit.

- [ ] **`RELEASES.md`** — Change `Status: In Progress` → `Status: Released` for the closing version.
- [ ] **`ROADMAP.md`** — Mark the version Released. Update "Current Release Baseline" to the next planned version if known.
- [ ] **`docs/releases/vX.Y.Z.md`** — Write the release notes file using the template below. Use the Write tool; do not paste notes as conversation text.

## Release Notes Template

File path: `docs/releases/vX.Y.Z.md`

```markdown
# Release Notes: vX.Y.Z — [Theme Name]

## Release Theme
[One sentence: what this release achieves for the user]

## Key Features

**[Emoji] [Feature Name]**
- [what it does]
- [what it unlocks]

**[Emoji] [Feature Name]**
- [bullet]

## Polish & Fixes
- [flat bullet — no sub-bullets]
- [flat bullet]
```

Rules:
- Bold + emoji prefix on each Key Feature title: `**📋 Feature Name**`
- No emojis inside `##` section headers
- Polish & Fixes is a flat list, no titled sub-sections
- Calibrate length against existing files in `docs/releases/`

## Commit

```bash
git add RELEASES.md ROADMAP.md docs/releases/vX.Y.Z.md
git commit -m "chore: sign off vX.Y.Z — [Theme Name]"
```

Commit directly on `releases/vX.Y.Z`. No sub-branch, no PR for this commit.

## After the Commit

Open a PR from `releases/vX.Y.Z` → `main`. PR description comes from the release notes file — do not draft a separate description. Merge after CI passes.

Then run `/kickoff` for the next version.
