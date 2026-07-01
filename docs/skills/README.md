# AI Skills System — NoteLib

Reusable thinking and workflow patterns for AI-assisted development.

This is an internal development tool, not a NoteLib feature.

---

## Why This Exists

NoteLib development uses Claude for product thinking and Codex for implementation. As the product has grown, the same types of prompts keep appearing — Codex implementation prompts, UX reviews, doc alignment checks, roadmap audits. Writing them from scratch each time wastes effort and produces inconsistent results.

This skills system captures the recurring prompt structures as lightweight, reusable guides. Each skill is a markdown file you read before composing a prompt — it tells you what to include, what to skip, and what to watch out for.

---

## Philosophy

**Reusable thinking patterns, not implementation patterns.**

NoteLib's architecture is still evolving. Features change quickly. Implementation patterns are not stable enough to templatize. What IS stable is the *meta-workflow*: how to review a UX decision, how to structure an implementation prompt, how to check doc alignment after a feature ships.

Skills reduce prompt fatigue. They do not constrain your judgment.

---

## Current NoteLib Stage

NoteLib is in the **exploration and refinement phase** of v0.12.0:

- Architecture evolves every sprint
- UX decisions are being discovered through shipping, not pre-planned
- Implementation patterns are stabilizing but not frozen
- Core product identity (notes-first, learning-loop-focused) is stable

Because of this, skills should stay **lightweight and adaptable**. A skill that embeds specific file paths, component names, or implementation patterns will become stale within weeks. Skills that encode philosophy and process will stay useful indefinitely.

---

## Claude vs Codex

### Use Claude when

- The question is *should we do this?* not *how do we implement this?*
- You need to reason about UX, product philosophy, or user experience
- You are drafting roadmap scope or reviewing feature fit
- You are discussing architecture tradeoffs
- You are writing or reviewing prompt structures and documentation
- The output is a decision, recommendation, or written artifact

**Default model**: Claude Sonnet for product/UX/doc work. Claude Opus for deep architecture decisions or when Sonnet attempts are not converging.

### Use Codex when

- The spec is clear and agreed: you know what to build
- The task is implementation, refactoring, cleanup, or migration
- The task is adding tests for agreed behavior
- The task is repo-wide changes following an established pattern
- The output is working, tested code

**Default model/effort**: Codex medium for standard implementation. Escalate to high effort only when the task touches multiple systems, has significant ambiguity, or previous attempts produced poor results.

### When to plan first

Always plan before implementing when:
- The feature touches three or more modules
- The feature changes a data flow, persistence contract, or API contract
- The feature introduces a new architectural pattern
- The scope is unclear and the wrong implementation direction would require a full redo

Plan by running the `roadmap-feature-audit` skill and discussing with Claude before writing the Codex prompt.

---

## Model and Effort Recommendations

| Task type | Tool | Default effort |
|---|---|---|
| UX/product review | Claude Sonnet | Standard |
| Architecture discussion | Claude Sonnet / Opus | High |
| Roadmap and doc review | Claude Sonnet | Standard |
| Prompt drafting | Claude Sonnet | Standard |
| Standard feature implementation | Codex | Medium |
| Multi-system or ambiguous implementation | Codex | High |
| Refactor / cleanup / migration | Codex | Medium |
| Tests for agreed behavior | Codex | Low / Medium |

Escalate effort or model only when complexity genuinely requires it. Defaulting to maximum effort wastes time and produces over-engineered results.

---

## Invocable Slash Commands

Registered as Claude Code slash commands in `.claude/commands/`. Type `/command-name` to invoke.

| Command | Purpose |
|---|---|
| `/codex-prompt` | Write a Codex prompt — template + pre-send checklist |
| `/kickoff` | Open a new version — 7-file atomic commit on the release branch |
| `/signoff` | Close a version — 3-file atomic commit + release notes file |
| `/audit-diff` | Post-Codex delivery audit: error states, transactions, idempotency |
| `/feature-doc` | Update `docs/features/<feature>.md` after shipping a behavioral change |
| `/version-check` | Verify all 7 version references are in sync |

## Reference Guides

Longer-form guides for AI context in prompts. Reference these in the `source of truth docs` section of Codex prompts.

| Guide | File | Purpose |
|---|---|---|
| Codex Prompt Generator | `codex-prompt-generator.md` | Full section philosophy, common mistakes, anti-drift notes |
| UX / Product Review | `ux-product-review.md` | Consistent product review against NoteLib philosophy |
| Release / Doc Alignment | `release-doc-alignment.md` | Ensure roadmap and docs stay aligned after feature work |
| Roadmap / Feature Audit | `roadmap-feature-audit.md` | Prevent scope creep and roadmap drift before starting work |

---

## How to Use a Skill

1. Identify which recurring workflow you are in (pre-implementation? post-ship? UX review?)
2. Read the matching skill file before composing your prompt
3. Use the skill's sections and checklist as a guide — not a rigid template
4. Add NoteLib-specific context from AGENTS.md, ROADMAP.md, and the relevant feature doc
5. Compose the prompt and run it

Skills are reference material. They do not replace your judgment.

---

## What Skills Are Not

- Autonomous agent pipelines
- Code-generation frameworks
- Rigid prompt templates that must be followed exactly
- Tightly coupled to specific files, components, or implementation patterns
- A substitute for reading AGENTS.md and the relevant feature doc
