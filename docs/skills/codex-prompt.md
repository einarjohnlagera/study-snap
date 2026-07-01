# /codex-prompt — Write a Codex Prompt

Convert a scoped, agreed feature into a structured Codex implementation prompt.

For full guidance (section philosophy, common mistakes, anti-drift notes), read `docs/skills/codex-prompt-generator.md`. This skill is the fast-path: the prompt template ready to fill in, with the pre-send checklist at the end.

---

## Before You Start

- [ ] Feature is scoped — you know what's in and what's NOT in
- [ ] Architecture decisions are made (or run `/roadmap-feature-audit` first)
- [ ] Relevant anti-drift rules from `AGENTS.md` are in front of you
- [ ] Check `docs/codex-prompts/` for an existing prompt for this feature — don't rewrite one that exists

---

## Prompt Mode

| Use **Long** when | Use **Short** when |
|---|---|
| New feature, non-trivial refactor | UI polish, small bug fix, copy/spacing change |
| Data flow or persistence changes | Low-risk incremental follow-up |
| Multi-doc updates, higher-risk tasks | Task touches 1–3 files with a clear root cause |

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

[1–3 sentences: user value / learning-loop outcome, not just technical output]

## CONTEXT

[What was agreed and by whom. Key decisions already made.]
[What is explicitly OUT of scope.]
[Anti-drift rules from AGENTS.md that apply to this task.]
[Prior implementation patterns to reuse.]

## REQUIRED CHANGES

[Backend]
- [specific change]

[Frontend]
- [specific change]

## ERROR STATES

- [Network/transient error on load → ...]
- [API error on submit → ...]
- [404 → ...]
- [Quota exceeded → ...]
- [@Transactional required if: ...]
- [Idempotency: ...]

## TESTING

- [Happy path]
- [Error/edge state per user-facing action]
- [Load-on-refresh if a persistent record is created]

## DOCUMENTATION

- Update RELEASES.md vX.Y.Z with a bullet under Shipped
- Update docs/features/[feature].md if behavior changed
- Update AGENTS.md if a new rule was established

## CLEANUP

- [Specific dead code or deprecated patterns to remove, or: "No cleanup required"]

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
3. Suggested commit message (format from AGENTS.md)
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
- [ ] Source docs listed at the top
- [ ] Scope is explicit — in AND out
- [ ] Relevant AGENTS.md anti-drift rules in CONTEXT
- [ ] ERROR STATES covers every failure mode
- [ ] At least one error-path AC per user-facing action
- [ ] If feature creates a persistent record — AC for loading it on refresh
- [ ] If quota-check + write in same flow — `@Transactional` called out
- [ ] DOCUMENTATION names specific files
- [ ] Save prompt to `docs/codex-prompts/[version]-[feature].md` before sending

---

## After Codex Delivers

Run `/audit-diff` before committing. Required for Long prompts.
