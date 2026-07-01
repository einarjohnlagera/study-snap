# /kickoff — Release Kickoff

Open a new version before any feature work starts.

This is a **7-file atomic commit** made directly on the new `releases/vX.Y.Z` branch. No sub-branch, no PR. Do not let any feature or fix branch be cut before this commit lands.

---

## Pre-flight

```bash
# Confirm you are on the right branch (create it if it doesn't exist)
git branch --show-current

# Create the release branch from main (or from the previous release branch if it hasn't merged yet)
git checkout -b releases/vX.Y.Z

# Verify version files before touching anything
# Run /version-check if available, or manually grep:
grep "Current version" CLAUDE.md
grep "<version>" backend/pom.xml | head -1
grep '"version"' frontend/package.json | head -1
```

---

## The 7-File Checklist

Make all changes before committing. Every file must land in the same commit.

- [ ] **`RELEASES.md`** — Add new version section at the top (Status: In Progress). Mark the *prior* version Released.
- [ ] **`ROADMAP.md`** — Add new version section. Update "Current Release Baseline" line.
- [ ] **`CLAUDE.md`** — Bump `Current version: vX.Y.Z`.
- [ ] **`frontend/package.json`** — Bump `"version": "X.Y.Z"`.
- [ ] **`backend/pom.xml`** — Bump `<version>X.Y.Z</version>` (the project version, not a dependency).
- [ ] **`AGENTS.md`** — Update documentation baseline and version reference line.
- [ ] **`README.md`** — Update release baseline line.

---

## Commit

```bash
git add RELEASES.md ROADMAP.md CLAUDE.md frontend/package.json backend/pom.xml AGENTS.md README.md
git commit -m "chore: kick off vX.Y.Z — [Theme Name]"
```

Commit directly on `releases/vX.Y.Z`. No `--no-verify`.

---

## RELEASES.md Section Template

```markdown
## vX.Y.Z - [Theme Name]

**Status: In Progress**

Theme: [one sentence describing the release theme and user value]

### Planned Scope

- **[Feature 1] (backend + frontend).** [What it does and why]
- **[Feature 2] (frontend).** [What it does and why]

Anti-drift: [list the locked rules — what this release does NOT change]

### Shipped

_(nothing yet)_
```

---

## After Kickoff

Feature work branches off `releases/vX.Y.Z`, not `main`. The standard PR flow applies to those branches. Release-management commits (kickoff, signoff) are the only two commits allowed directly on the release branch.
