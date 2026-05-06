# Codex Prompt Generator

Convert a rough feature discussion into a structured, Codex-ready implementation prompt.

---

## When to Use Codex

Use Codex when the specification is agreed and the task is implementation:
- The feature is scoped (what to build, what NOT to build)
- Architecture and data flow decisions have been made
- You need working, tested code — not a recommendation

If scope or architecture is still unclear, run the `roadmap-feature-audit` skill and discuss with Claude first. Sending Codex an under-specified prompt produces an over-engineered or mis-directed implementation.

---

## Prompt Mode (from AGENTS.md)

AGENTS.md defines two prompt modes. Use them as a guide for how much detail to include:

**Long Prompt** — new features, non-trivial refactors, data flow or persistence changes, multi-doc updates, higher-risk tasks.

**Short Prompt** — UI polish, small bug fixes, copy or spacing changes, low-risk incremental follow-ups.

Always declare the prompt mode at the top of every Codex prompt:
```
Prompt mode: Long
```
or
```
Prompt mode: Short
```

---

## Required Context Prelude

Every Codex prompt should open with a pointer to the primary source-of-truth docs:

```
Use the following docs as the source of truth:
- AGENTS.md
- docs/product/ROADMAP.md
- docs/features/[relevant-feature].md
- [any other relevant feature or architecture doc]
```

This prevents Codex from guessing product rules it should be reading instead.

---

## Prompt Template (Long Mode)

```
Prompt mode: Long

Use the following docs as the source of truth:
- AGENTS.md
- docs/product/ROADMAP.md
- docs/features/[feature].md

---

## TASK

[One sentence: what are we building?]

## GOAL

[One to three sentences: what outcome does this achieve? Frame in terms of the learning loop or product value, not just technical output.]

## CONTEXT

[What Claude or the product team agreed on. Key decisions already made. What this touches and what it does NOT touch. Link to any relevant prior discussion or doc.]

[Anti-drift notes: any rules from AGENTS.md that are directly relevant to this task.]

## REQUIRED CHANGES

[Backend]
- [specific change]
- [specific change]

[Frontend]
- [specific change]
- [specific change]

[Keep changes scoped. List files or modules only when it helps Codex stay on track, not to micromanage.]

## TESTING

- [What must be tested]
- [Boundary conditions or edge cases that matter]
- [Any integration test requirements]

## DOCUMENTATION

- Update RELEASES.md v0.12.0 with a brief bullet under the appropriate section
- Update docs/features/[feature].md if behavior changed
- Update AGENTS.md if a new rule was established
- [Any other doc that needs updating]

## CLEANUP

- [Remove dead code, old feature flags, deprecated patterns if applicable]
- [If nothing, write "No cleanup required"]

## ACCEPTANCE CRITERIA

- [ ] [Behavior 1 passes]
- [ ] [Behavior 2 passes]
- [ ] [Tests pass]
- [ ] [Docs updated]

## OUTPUT

Return:
1. All changed files
2. Summary of what changed and why
3. Suggested commit message (format from AGENTS.md)
```

---

## Prompt Template (Short Mode)

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
- [ ] [tests pass if affected]

## OUTPUT

Return changed files and a commit message.
```

---

## Section Guidance

### TASK
One sentence. Avoid "we want to" — be direct: "Add X to Y so that Z."

### GOAL
Frame in learning-loop or user value terms. Not "this adds the field" but "this lets students see their learner level on the result screen without leaving the quiz flow."

### CONTEXT
The most important section. Include:
- What was agreed and by whom (Claude review, product discussion, ROADMAP)
- What is explicitly OUT of scope for this prompt
- Anti-drift notes from AGENTS.md that apply (upgrade CTA rule, analytics rule, learner level vs course/program separation, etc.)
- Any prior implementation patterns to reuse (existing components, utilities, constants)

### REQUIRED CHANGES
List what to build, not how to build it. If implementation approach matters (e.g., "reuse `StudyPackGenerationContextResolver`, do not inline"), say so — but keep it minimal.

### TESTING
Specify what must be tested. Do not leave this empty. At minimum: happy path and one error/edge state.

### DOCUMENTATION
Always include RELEASES.md. Add feature doc and AGENTS.md updates when rules change. Skip docs that genuinely don't need updating — but name them explicitly rather than leaving a vague "update docs."

### CLEANUP
Name specific things to clean up. If nothing, say so. Do not leave this section blank or Codex may over-clean.

### ACCEPTANCE CRITERIA
Specific, checkable. Not "works correctly" — name the behavior. Acceptance criteria are the test for whether the prompt succeeded.

---

## Common Mistakes

**Under-specifying scope.** If you don't say what's out of scope, Codex will implement it. Name explicit exclusions: "Do not add this to Board Exam Mode." "Do not change the Study Pack generation flow."

**Missing anti-drift context.** If the task touches upgrade CTAs, analytics events, learner level, quiz generation, onboarding, or public library — copy the relevant AGENTS.md anti-drift rule into the CONTEXT section. Codex does not remember previous sessions.

**Skipping DOCUMENTATION.** Every prompt that changes behavior should update RELEASES.md at minimum. Make it a required section, not an afterthought.

**Over-specifying implementation.** Listing every file path and function call turns a Codex prompt into a code review. Specify outcomes and constraints; let Codex handle structure.

**Sending a long prompt for a short task.** Short-mode prompts are faster, cheaper, and produce cleaner output for incremental changes. Use them.

---

## Checklist Before Sending

- [ ] Prompt mode declared (Long / Short)
- [ ] Source docs listed at the top
- [ ] Scope is explicit (what's in, what's out)
- [ ] Relevant AGENTS.md anti-drift rules included in CONTEXT
- [ ] DOCUMENTATION section names specific files to update
- [ ] ACCEPTANCE CRITERIA are checkable, not vague
- [ ] If architecture is unclear, ran `roadmap-feature-audit` first
