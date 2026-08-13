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

## Scope-completeness gate — run this BEFORE the commit

**Re-read the release's own Planned Scope list and confirm each item is actually built.** Not "was it
discussed" — find the implementing code.

`v0.73.0` shipped seven PRs and signed off with a planned item that had **never been built**: it received
neither a Codex prompt nor an inline pass, and nothing in the per-item workflow re-read the scope list, so
seven PRs went by without anyone noticing. A fresh-context pressure test caught it on signoff eve. Worse, the
missing item made the release's own headline claim false for the majority profile type.

For each planned item, record one of: shipped (with file evidence), **not shipped** (with the reason and where
it now lives), or **changed** (with the decision that changed it). An item that was reversed mid-release still
needs its reversal written down — otherwise the release notes describe a product that does not exist.

## Feature-doc drift gate — run this BEFORE the commit

**Re-read every feature doc this release touched against the FINAL code state — not against the PR that last
edited it.** This is a different check from "update `docs/features/<feature>.md` when shipping a behavioral
change". That rule is per-PR, and per-PR is precisely where it fails: a later PR changes the behaviour again
and updates some docs but not others. Each PR looks correct in isolation; the contradiction only exists once
everything has landed.

`v0.74.0` accumulated **seven** such contradictions in one release. The worst: `quick-review.md` stated that
Quick Review does **not** write `ConceptHealth` — false since a deliberate revert a month earlier, and the
exact claim the release's own justification depended on. Because feature docs are what Codex reads at the
start of every prompt, a future prompt could have cited it, "fixed" the code to match, and silently undone
both the revert and the release's rationale. Two more were found only by a cold-context agent.

Method, cheapest reliable form: for each behavioural claim in a touched feature doc, locate the code that
implements it and confirm the claim still describes it. **A claim you cannot anchor to code is either stale or
was never true** — resolve it either way before committing. Pay particular attention to docs a PR touched
*early* in the release, since those are the ones a later PR is most likely to have invalidated.

## Checkpoint gate — run this BEFORE the commit

**Ask: did anything in this release ship ahead of its own evidence?** That means an item whose `EVIDENCE` gate was never cleared — it shipped on a pre-committed rule, an owner override, an ambiguous read, or a bootstrap-test argument. If yes, it owes a `[CHECKPOINT — due YYYY-MM-DD]` row in `ROADMAP.md`'s Backlog Index, **added in this same signoff commit** (`ROADMAP.md` is already one of the three files).

**Why this gate exists.** `v0.72.0` shipped H1+H5 on an explicitly ambiguous read and signed off with no checkpoint at all. Nothing caught it until the *next* kickoff's gate scan — so the gap survived from signoff until someone happened to open a new version, and if the next kickoff had been weeks out it would have sat that long. Kickoff step 9 only scans for checkpoints that are *overdue*; it cannot detect one that was never written. This is the only step that can.

A checkpoint needs all five, per the gate-type rules in the Backlog Index intro:

- [ ] **A date**, relative to **deploy**, not merge — the clock starts when users can reach it.
- [ ] **A named kill criterion** — what result would make you stop, stated before you see the result.
- [ ] **It lives in the Gate column**, not buried in Status prose.
- [ ] **The review ritual scans it** — it's in the table, so it does.
- [ ] **Instrumentation shipped in this same release, verified *emitting*.** Enum membership is not instrumentation: grep each event name outside its enum and confirm a real fire site. A checkpoint without a working metric is decorative.

**Check the denominator before setting the date.** If the metric's population is small, a short window reproduces the underpowered read rather than answering it — `v0.72.0`'s own trigger was a cohort of 31 where P(zero | no change) was 54.3%. When that's the risk, **two tiers**: a proximal checkpoint on a funnel rate whose denominator is large enough to read soon (and put the kill criterion there), plus a distal one on the real outcome, carrying a denominator clause that says "not yet measurable" is a re-date, not a verdict.

Also state the fallback honestly: **if the release recorded a fallback scope, verify it is genuinely unbuilt in code** before signing off with it on record. `v0.72.0` named the CPALE Exam Hub as its fallback; it had already shipped as `v0.54.0`, so that release's fallback was empty and nobody would have found out until someone tried to build it.

## The 3-File Checklist

Make ALL changes before committing — this is one atomic commit.

- [ ] **`RELEASES.md`** — Change `Status: In Progress` → `Status: Released` for the closing version.
- [ ] **`ROADMAP.md`** — Mark the version Released. Update "Current Release Baseline" to the next planned version if known. **Add any checkpoint row the gate above requires, and update the Backlog Index row of every item this release shipped** — a row still reading "next up" for something that shipped is how a shipped item silently keeps looking like a candidate.
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
