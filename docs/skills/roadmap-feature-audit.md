# Roadmap / Feature Audit

Prevent scope creep and roadmap drift before starting any new work.

Run this when: a feature idea surfaces, a request comes in mid-sprint, you are unsure whether something belongs in the current release, or you suspect a task is larger than it first appears.

---

## Why This Matters

The two most common ways NoteLib loses velocity:

1. **Scope creep**: a polish task expands into a system redesign because nobody drew a line
2. **Roadmap drift**: work gets done that doesn't appear in the roadmap, so future sessions don't know it exists — or conflicts with something planned

This audit draws the line before implementation starts.

---

## Step 1: Check If It Already Exists

Before classifying a feature, verify it hasn't already been planned or shipped.

Check in order:
1. `docs/product/ROADMAP.md` — current v0.12.0 section and completed items
2. `RELEASES.md` — v0.12.0 shipped items and fixes
3. `AGENTS.md` — existing rules that may already cover the request
4. `docs/features/[relevant feature].md` — existing feature behavior documentation

**If it already exists**: the task is a bug fix, a doc gap, or scope clarification — not a new feature. Treat it accordingly.

**If it doesn't exist**: proceed to classification.

---

## Step 2: Classify the Work

Every piece of work is one of four types. Be honest about which one it is — the classification determines how much design, effort, and doc work it needs.

### Core Feature

**Definition**: New user-facing capability that belongs in the current release roadmap. Changes the product's capability surface.

**Signals**:
- Mentioned in ROADMAP.md v0.12.0 planned scope
- Directly supports the current release theme (Learning Experience, Discovery, Retention)
- Would be clearly noticed and valued by users
- Requires backend + frontend changes or a new product rule

**What it needs**:
- Roadmap entry if missing
- Product decision before implementation (Claude review recommended)
- Full Codex prompt (Long Mode)
- RELEASES.md entry on completion
- Feature doc update

### Polish

**Definition**: Quality improvement to an existing feature. Does not change what the feature does — changes how well it does it.

**Signals**:
- The feature exists and works; this makes it better
- The change is primarily UX, copy, visual, or interaction quality
- No new backend endpoints, no new data model changes
- A user would notice it feels better but might not name it as a new feature

**What it needs**:
- No roadmap entry required (unless it's substantial polish tied to a planned item)
- Short Codex prompt (Short Mode) for implementation
- RELEASES.md note if the polish is meaningful
- No feature doc update unless behavior changed

### Future Enhancement

**Definition**: Work that is valid and desirable but does not belong in the current release. Queue it for a future sprint.

**Signals**:
- Makes sense for v0.13.0+ or a later phase
- Current release is not the right time (scope, dependencies, or architectural readiness)
- Would distract from the current release's primary goals (conversion, first-quiz experience, retention)

**What it needs**:
- Add to ROADMAP.md `## Future Directions` or a future version section
- Do not implement now
- Note any dependencies or prerequisites for when it becomes relevant

### Low-Priority Idea

**Definition**: An idea worth tracking but with no clear timeline or strong signal that it's the right direction.

**Signals**:
- It surfaced as a passing thought, not a user-reported need or roadmap goal
- No clear place in the product loop
- Could be a real improvement — but could also drift the product away from its core identity

**What it needs**:
- Record it informally (notes, a brief comment in a doc, or nowhere)
- Do not add to ROADMAP.md unless it elevates to Future Enhancement
- Do not implement

---

## Step 3: Release Boundary Check

For anything classified as **Core Feature**, verify it belongs in the current release:

**Current release theme**: `v0.12.0 — Learning Experience, Discovery, and Retention`

**Current phase emphasis** (from ROADMAP.md):
- Improve user conversion and the first-study / first-quiz experience
- Progressive Challenge Quiz generation is the active quiz-flow optimization path
- Board Exam Mode optimization is deferred to next phase

Ask these four questions:

1. **Does it fit the theme?** If it doesn't clearly support learning experience, discovery, or retention — it probably belongs in a later release.

2. **Does it conflict with current priorities?** v0.12.0 prioritizes conversion (public note pages) and first-quiz experience. Work that delays those items has a high bar for inclusion.

3. **Does it introduce new architectural risk?** Features that require new tables, new service boundaries, or untested patterns should not be rushed into an in-progress release without explicit justification.

4. **Does it help ship or delay shipping?** The best answer to "should we add this?" is often "only if it makes the current priorities better, not if it adds more."

---

## Step 4: Implementation Decision

Based on classification and boundary check:

| Classification | Decision |
|---|---|
| Core Feature, in scope | Proceed. Use `codex-prompt-generator` skill. Update roadmap before implementing. |
| Core Feature, out of scope | Add to future roadmap section. Defer. |
| Polish | Proceed with Short Mode prompt. No roadmap update needed. |
| Future Enhancement | Add to roadmap future section. Do not implement. |
| Low-Priority Idea | Record informally or discard. Do not implement. |

---

## Decision Framework: When to Say No

Saying no to a feature (or deferring it) is not a failure. It's how the current release stays coherent.

Say no or defer when:

- **It's not in the current release theme.** Even good ideas don't belong in every sprint.
- **The current phase has higher-priority items unfinished.** Public note conversion is top priority. Anything that delays it needs explicit justification.
- **It introduces new architectural complexity without a clear payoff.** Complexity is a cost. A feature that adds complexity must clearly earn it.
- **It's solving a problem users haven't actually reported yet.** Speculative features often miss the actual need. Wait for signal.
- **It would require significant design work before implementation.** Research-only items (quiz latency investigation, Long Exam mode planning) must produce a written doc before code is written.

Say yes when:

- It's already in ROADMAP.md v0.12.0 planned scope
- It directly unblocks or strengthens a current-phase priority
- It's a polish improvement to a feature that's actively in users' hands
- It's a bug fix for a rule that's already documented and agreed

---

## Scope Creep Warning Signs

Stop and re-classify if any of these appear mid-implementation:

- A "small change" requires a new backend endpoint
- A UI improvement requires a new data model field
- Fixing a bug reveals a missing feature that "should also be fixed"
- The Codex prompt keeps expanding as implementation progresses
- A polish task turns into a component redesign
- Two features are being bundled that could be separate PRs

When scope creep appears: stop, re-scope the current PR to the minimal viable change, and file the remainder as a new task with its own classification.

---

## Quick Audit Format

Use this when you need to run a fast audit in conversation:

```
Feature: [name]
Already documented? [yes/no — where]
Classification: [Core Feature / Polish / Future Enhancement / Low-Priority Idea]
In current release scope? [yes / no / uncertain]
Decision: [proceed / defer / discard]
Next action: [...]
```
