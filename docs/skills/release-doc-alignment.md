# Release / Doc Alignment

Ensure roadmap and docs stay aligned after feature work ships.

Run this after completing any feature, fix, or significant change — before closing the session or opening a pull request.

---

## Why This Matters

NoteLib's product docs are the source of truth for AI coding agents (Claude and Codex) in future sessions. Stale docs mean future prompts work from wrong assumptions. The cost of a 5-minute doc update now is avoiding hours of correction later.

The most common failures:
- RELEASES.md not updated, so future sessions don't know what shipped
- ROADMAP.md not reflecting completed work, creating confusion about what's still planned
- AGENTS.md missing a new rule introduced by an architecture or behavior decision
- Feature docs not updated, so a future Codex prompt implements something that was already changed

---

## When to Run This

- After completing any feature (new functionality, not just fixes)
- After any significant behavior change (not just UI polish)
- After any architecture decision that established a new rule
- After any fix that revealed a gap in the documented rules

For small UI polish or copy fixes: RELEASES.md is still required; the rest may not apply.

---

## Required Doc Checklist

Work through this list after every non-trivial change. Check each item consciously — don't assume it's covered.

### RELEASES.md

**Always required.**

- [ ] Add a bullet under the current `v0.12.0` section
- [ ] Place it under the correct subsection: `### ✅ Shipped`, `### 🐛 Fixes`, or `### Planned Scope`
- [ ] Move items from `### Planned Scope` to `### ✅ Shipped` when they land
- [ ] Write the bullet as a past-tense statement of what shipped, not future-tense plans
- [ ] Include enough detail that a future AI session can understand what changed without reading the code

**Bullet format:**
```
- **[Feature Name]** — [one to two sentences describing what shipped and what behavior it established; include key technical decisions if they constrain future work]
```

### ROADMAP.md

**Required when:** a feature moves from planned to complete, scope changes for the current release, or a new item is added to the roadmap.

- [ ] Move completed items to `### Completed in v0.12.0 so far`
- [ ] Update `### High Priority (Current Phase)` if priorities shifted
- [ ] Add new planned items if the work revealed a follow-up that should be tracked
- [ ] Remove items that were explicitly descoped (with a note if the decision matters)

### AGENTS.md

**Required when:** the work established a new product rule, a new behavior contract, or a constraint that AI agents must know about in future sessions.

Ask: "If a new Codex prompt were written tomorrow for a related feature, what rule from this work would it need to know?" If the answer is anything, write it into AGENTS.md.

- [ ] Add a new rule section or append to an existing one
- [ ] Follow the existing pattern: declarative rule, then supporting detail
- [ ] Cross-reference related rules if they interact (e.g., a new quiz rule might interact with the existing Progressive Generation rule)
- [ ] If a rule was made more specific or corrected, update the existing rule — don't duplicate it

### PROJECT_CONTEXT.md

**Required when:** the work changes a fundamental product behavior, architecture contract, or positioning decision.

This doc is slower-moving than AGENTS.md. Update it for:
- Changes to how Public Library works as an acquisition surface
- Changes to the learning loop or user state model
- Changes to what's in-scope for the current release
- Changes to the tech stack or payment/billing model

### docs/features/[relevant feature]

**Required when:** a feature doc exists for the area you changed.

- [ ] Locate the relevant file under `docs/features/`
- [ ] Update any section that describes behavior that changed
- [ ] Add a new section if the feature introduced a new sub-behavior not previously documented
- [ ] Keep the doc behavior-focused, not implementation-focused

If no feature doc exists and the work is significant, create one under `docs/features/`.

### Commit Message

**Always required.** Per AGENTS.md Implementation Workflow Rules:

```
type: concise subject line

- high-signal change 1
- high-signal change 2
- high-signal change 3 (if relevant)
- docs updated (RELEASES.md, ROADMAP.md, etc.)
```

Common types: `feat`, `fix`, `polish`, `refactor`, `docs`, `chore`, `test`

---

## Per-Doc Guidance

### What to write in RELEASES.md

Write for a future AI agent that needs to know what state the product is in. Not "improved the quick check" — but "evolved single-question Quick Check into a sequential 3-question preview with a progress indicator, feedback microcopy, and a completion state with CTAs; no backend changes, no new AI generation, fallback-safe when fewer than 3 questions exist."

Overly brief bullets create ambiguity. If you had to make a product decision during implementation (e.g., "no challenge-quiz redirect target exists, so we used quick-review instead"), record that decision.

### What to write in AGENTS.md

Write in the same style as existing rules: direct, present tense, imperative where appropriate. Rules should be enforceable by an AI coding agent reading them fresh. Avoid "we decided to" — write "X must Y."

If a rule has an exception, document the exception explicitly. "Board Exam Mode is exempt from progressive generation" is more valuable than leaving it implied.

### What NOT to update

- `docs/legacy/` — historical reference only, never update
- `docs/architecture/DATA_MODEL.md` — only update for schema changes
- `docs/testing/` — update only when test strategy changes, not for individual feature tests

---

## Quick Decision Table

| What changed | RELEASES.md | ROADMAP.md | AGENTS.md | Feature doc |
|---|---|---|---|---|
| New feature shipped | ✅ | ✅ | If new rule | If doc exists |
| Bug fix | ✅ | No | If revealed a gap | If doc exists |
| UI polish | ✅ | No | No | No |
| Architecture decision | ✅ | No | ✅ | Maybe |
| New product rule | ✅ | No | ✅ | Yes |
| Scope change | ✅ | ✅ | No | Maybe |

---

## Alignment Red Flags

If any of these are true after closing a task, stop and fix the docs:

- RELEASES.md does not mention the work
- ROADMAP.md still shows the shipped item as planned
- AGENTS.md has no entry for a new rule that was just established
- A future Codex prompt for this area would contradict what was just built
- The commit message is missing
