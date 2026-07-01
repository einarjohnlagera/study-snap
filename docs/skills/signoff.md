# /signoff — Release Sign-off

Close a version and mark it Released.

This is a **3-file atomic commit** made directly on the `releases/vX.Y.Z` branch. No sub-branch, no PR for the signoff commit itself. After the commit, open a PR from `releases/vX.Y.Z` → `main` to merge the release.

---

## Pre-flight

```bash
# Confirm you are on the release branch
git branch --show-current
# Must be: releases/vX.Y.Z

# Confirm all feature PRs are merged into the release branch
git log --oneline releases/vX.Y.Z | head -20

# Run /version-check to confirm all 7 version references are consistent
```

---

## The 3-File Checklist

Make all changes before committing. Every file must land in the same commit.

- [ ] **`RELEASES.md`** — Change `Status: In Progress` → `Status: Released` for the version being closed.
- [ ] **`ROADMAP.md`** — Mark the version Released and update "Current Release Baseline" to the *next* planned version (if known).
- [ ] **`docs/releases/vX.Y.Z.md`** — Write the release notes file (see template below). Use the `Write` tool; do not paste notes as conversation text.

---

## Commit

```bash
git add RELEASES.md ROADMAP.md docs/releases/vX.Y.Z.md
git commit -m "chore: sign off vX.Y.Z — [Theme Name]"
```

Commit directly on `releases/vX.Y.Z`. No `--no-verify`.

---

## Release Notes File Template

```markdown
# Release Notes: vX.Y.Z — [Theme Name]

## Release Theme
[One sentence: what this release achieves for the user]

## Key Features

**[Emoji] [Feature Name]**
- [bullet: what it does]
- [bullet: what it unlocks]

**[Emoji] [Feature Name]**
- [bullet]

## Polish & Fixes
- [flat bullet: concise, no sub-bullets]
- [flat bullet]
```

Rules:
- Bold + emoji prefix on each Key Feature title (`**📋 Feature Name**`)
- No emojis in `##` section headers themselves
- Polish & Fixes is a flat list, no titles

Reference: existing files in `docs/releases/` for tone and length calibration.

---

## After Sign-off

Open a PR from `releases/vX.Y.Z` → `main`. The PR description comes from the release notes file — do not draft a separate PR description. Merge after CI passes.

Then run `/kickoff` for the next version.
