---
name: codex-prompt
description: Write a structured Codex implementation prompt for a NoteLib feature. Auto-injects the Long or Short mode template with the pre-send checklist. Use when a feature is scoped and agreed and ready to be handed to Codex for implementation. For full section guidance and common mistakes, read docs/skills/codex-prompt-generator.md.
argument-hint: <feature-name>
---

You are writing a Codex prompt for a NoteLib feature. First, check `docs/codex-prompts/` for an existing prompt for this feature — if one exists for the current release, use it directly.

Determine the prompt mode:

| **Long** | **Short** |
|---|---|
| New feature, non-trivial refactor | UI polish, small bug fix, copy change |
| Data flow or persistence changes | Low-risk incremental follow-up, 1–3 files |
| Multi-doc updates, higher-risk tasks | |

Then fill in the appropriate template below. Save the completed prompt to `docs/codex-prompts/<version>-<feature>.md` before sending.

---

## Long Prompt Template

```
Prompt mode: Long

Use the following docs as the source of truth:
- AGENTS.md
- docs/product/ROADMAP.md
- docs/features/[feature].md

---

## TASK

[One sentence: "Add X to Y so that Z."]

## GOAL

[1–3 sentences: user/learning-loop value, not just technical output]

## CONTEXT

[What was agreed and by whom. Key decisions already made.]
[What is explicitly OUT of scope.]
[Anti-drift rules from AGENTS.md that apply.]
[Prior implementation patterns to reuse.]

## REQUIRED CHANGES

[Backend]
- [specific change]

[Frontend]
- [specific change]

## ERROR STATES

- [Network error on load → ...]
- [API error on submit → ...]
- [404 → ...]
- [Quota exceeded → ...]
- [@Transactional required if: ...]
- [Idempotency: ...]

## TESTING

- [Happy path]
- [Error/edge state per user-facing action]
- [Load-on-refresh if persistent record created]

## DOCUMENTATION

- Update RELEASES.md vX.Y.Z with a bullet under Shipped
- Update docs/features/[feature].md if behavior changed
- Update AGENTS.md if a new rule was established

## CLEANUP

- [Specific things to remove, or: "No cleanup required"]

## ACCEPTANCE CRITERIA

- [ ] [Behavior 1]
- [ ] [Error path 1]
- [ ] [Load-on-refresh if applicable]
- [ ] Tests pass
- [ ] Docs updated

## OUTPUT

Return:
1. All changed files
2. Summary of what changed and why
3. Suggested commit message (AGENTS.md format)
```

---

## Short Prompt Template

```
Prompt mode: Short

Use the following docs as the source of truth:
- AGENTS.md
- docs/features/[feature].md

---

## TASK

[One sentence]

## GOAL

[One sentence]

## CHANGES

- [bullet]
- [bullet]

## ACCEPTANCE CRITERIA

- [ ] [passes]
- [ ] Tests pass if affected

## OUTPUT

Return changed files and a commit message.
```

---

## Pre-Send Checklist

- [ ] Prompt mode declared
- [ ] Source docs listed at top
- [ ] Scope explicit — what's in AND what's out
- [ ] Relevant AGENTS.md anti-drift rules in CONTEXT
- [ ] ERROR STATES covers every failure mode
- [ ] At least one error-path AC per user-facing action
- [ ] Persistent record created? → AC for loading it on refresh
- [ ] Quota-check + write? → `@Transactional` called out
- [ ] DOCUMENTATION names specific files
- [ ] Prompt saved to `docs/codex-prompts/`

After Codex delivers, run `/audit-diff` before committing.
