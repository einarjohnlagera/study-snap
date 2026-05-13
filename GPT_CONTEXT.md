# GPT_CONTEXT.md — NoteLib Product Context Handoff

> Paste the block below as your **first message** in a new GPT chat session.
> Update this file whenever a new version ships or the roadmap shifts significantly.
> Last updated: v0.12.0 (near-complete) — 2026-05-13

---

Here's the full context for our NoteLib product session. Please treat this as the source of truth for where we are. Read it fully before responding to anything.

---

## App: NoteLib (formerly StudySnap)

**What it is:** A notes-first study workspace — not a generic AI tool. Users capture notes, generate AI-powered Study Packs from them, and practice with quizzes. Every feature must connect to a measurable learning outcome.

**Core learning loop:** Capture → Generate → Review → Improve → Copy → Repeat

**Positioning:** "Turn your notes into exam-ready study materials"

**Versioning model:** Never overwrite generated content. Users make a copy of a note, edit the copy, and generate a new Study Pack from it.

**Rebrand note:** The product is NoteLib. The codebase and database still use `studysnap` naming unless explicitly changed.

---

## Tech Stack

| Layer | Stack |
|---|---|
| Frontend | Next.js 16 (App Router), React 19, TypeScript 5, Tailwind 4, shadcn/ui |
| Backend | Spring Boot 4, Java 21, PostgreSQL 16, Flyway migrations |
| Auth | JWT + Google OAuth |
| Payments | Xendit hosted checkout, manual renewal (no auto-charge) |
| AI | OpenAI (Study Pack + quiz generation), Google Cloud Vision (OCR) |

---

## Profile Types

| Feature | Student | Board Exam | Teacher |
|---|---|---|---|
| Dashboard focus | Learning continuity, weak concepts | Exam countdown, challenge practice | Quiz creation, export readiness |
| Quiz modes | Challenge, Quick Review, Adaptive Practice | Challenge, Board Exam (Pro), Quick Review | Challenge (preview only), no scoring |
| Special features | — | Exam date tracking | Exam Builder, DOCX quiz export |
| Long Exam | Coming soon (v0.13.0) | — | — |

**PARENT and PROFESSIONAL** are defined as enum values and appear in the UI as "Coming Soon" (disabled). No implementation exists yet for either.

---

## Core Architecture

- **Note** is the primary entity. States: `DRAFT → GENERATING → FAILED → STUDY_PACK_READY`. Visibility: `PRIVATE / PUBLIC`.
- **Study Pack** is AI-generated content attached to a Note (summary, key concepts, quiz).
- **Quiz sessions** use a shared session entity across all modes (Quick Review, Challenge Quiz, Adaptive Practice, Board Exam).
- **Plans:** FREE / PLUS / PRO with monthly quotas. Payments via Xendit manual renewal.

---

## Quiz Mode Hierarchy (locked contract — exactly 5 modes)

1. **Quick Review** — practice, all plans
2. **Challenge Quiz** — exam, progressive 5→20 questions, all plans (with quota)
3. **Adaptive Practice** — practice, targets weak concepts, Plus/Pro only
4. **Long Exam** — exam, fixed long-form, Pro only (backend comes in v0.13.0)
5. **Board Exam** — exam, high-stakes simulation, Pro only, Board Exam profile only

Rule: adding a sixth mode requires updating `docs/product/EXAM_MODES.md` and `ROADMAP.md` together. Do not propose a sixth mode without flagging this.

---

## v0.12.0 — Current Release

**Theme:** Learning Experience, Discovery, and Retention

**Shipped so far:**
- Progressive Challenge Quiz (5→20 questions with +5 mid-session)
- Google social login
- Public Library conversion funnel (multi-question Quick Check, related notes, AppModal auth consolidation)
- Retention loops (continue studying, focus areas with free-tier fallback)
- Guidance Foundation System (GuidanceTip engine, library milestone tips)
- Conversion funnel polish (PaywallModal redesign, PostSuccessUpgradeNudge, context-aware getUpgradeCtas)
- Public creator attribution with stable @usernames
- Note metadata fixes: courseProgram source-of-truth, AI subject resilience, quiz context consistency
- Adaptive Practice tier reconciliation — Plus = 10/mo, Pro = 30/mo aligned across all docs and runtime
- Profile-aware mode selection + Long Exam coming-soon placeholder (`lib/exam-mode-visibility.ts`)
- Board Exam premium UX polish — pre-flight checklist, "Score Report" result, learner-level pill removed
- Learner Level grouped picker on quiz result screens (Recommended / Other Learning Styles by profile)

**Still remaining in v0.12.0:**

| Item | Risk class |
|---|---|
| Faster quiz generation investigation | Research only (no production changes) |
| Proration / recomputation design | Design doc only |

These are research/design artifacts — safe to defer to cut the release or carry into v0.13.0.

---

## Future Roadmap

**v0.13.0:**
- Long Exam Mode v1 — backend session support, fixed long-form, Pro-only, single note
- Guidance Engine extensions (cooldown, dashboard contextual tips)
- **Professional Profile MVP** — applied learning and interview practice (design in session)
- **Parent Profile MVP** — oversight of child's performance (**requires new parent↔child relationship model — not a UI variant; needs schema design first**)

**v0.14.0+:**
- Multi-note Long Exam
- Teacher class management (roster, student assignment, per-student performance view)
- Board Exam advanced analytics (trend, percentile framing)
- Public Library trending section (blocked on windowed engagement fields in backend)
- Cross-profile mode unlock (Student opts into Board Exam without changing profile)
- Recurring billing / auto-renewal
- Connected account management (unlink Google, add password for Google-only users)

---

## New Profile Types to Design This Session

**Professional Profile** (`PROFESSIONAL` enum + LLM prompt hints already exist):
- Use cases: professional upskilling, certifications, interview prep
- Quiz style: applied scenarios, case-based framing, real-world decision points
- No exam date field; same quiz modes as Student
- Open question: dedicated interview-practice quiz mode, or just prompt-level framing?

**Parent Profile** (`PARENT` enum exists, zero implementation):
- Use case: read-only oversight of a linked child's notes, scores, and weak areas
- Architectural blocker: needs a parent↔child relationship model — invite/link model, child confirmation, data visibility scope all need design first
- Do not propose implementation until the schema design is agreed

**Teacher Profile — strengthening ideas:**
- Current: quiz generation + DOCX export
- Gaps: no class management, no per-student performance view, no quiz assignment
- Questions to resolve: is class management v0.14.0 or later? How does teacher attribution interact with Public Library ranking?

---

## How We Work Together

- **You (GPT):** product thinking, roadmap decisions, UX philosophy, feature scoping, architecture tradeoffs
- **Claude Code (Sonnet):** implementation prompt drafting, doc writing, code review
- **Claude Code (Codex, medium effort default):** standard feature implementation, refactors, tests

Your output for implementation tasks is always a structured Codex prompt. Always declare prompt mode and include a model/effort recommendation.

---

## Prompt Format You Must Preserve

**Long mode** (new features, data flow changes, multi-doc updates):
```
Prompt mode: Long

Use the following docs as the source of truth:
- AGENTS.md
- docs/product/ROADMAP.md
- docs/features/[feature].md

## TASK
## GOAL
## CONTEXT
## REQUIRED CHANGES
## TESTING
## DOCUMENTATION
## CLEANUP
## ACCEPTANCE CRITERIA
## OUTPUT
```

**Short mode** (UI polish, small bug fixes, incremental follow-ups):
```
Prompt mode: Short

Use the following docs as the source of truth:
- AGENTS.md
- docs/features/[feature].md

## TASK
## GOAL
## CHANGES
## ACCEPTANCE CRITERIA
## OUTPUT
```

**Rules:** CONTEXT is the most important section — always paste the relevant AGENTS.md anti-drift rules. DOCUMENTATION always includes RELEASES.md. ACCEPTANCE CRITERIA must be checkable, not vague.

---

## Model / Effort Recommendations

| Task type | Tool | Effort |
|---|---|---|
| UX / product review | Claude Sonnet | Standard |
| Architecture discussion | Claude Sonnet or Opus | High |
| Roadmap / doc review | Claude Sonnet | Standard |
| Prompt drafting | Claude Sonnet | Standard |
| Standard feature implementation | Codex | Medium |
| Multi-system or ambiguous implementation | Codex | High |
| Refactor / cleanup / migration | Codex | Medium |
| Tests for agreed behavior | Codex | Low / Medium |

---

## Key Source-of-Truth Docs

- `AGENTS.md` — implementation rules and anti-drift constraints
- `docs/product/ROADMAP.md` — sequencing and planned scope
- `docs/product/SPEC.md` — canonical product behavior
- `docs/product/EXAM_MODES.md` — quiz mode hierarchy (locked contract)
- `docs/product/PLANS.md` — plan tiers and quotas
- `docs/features/` — 48 feature-specific docs
- `RELEASES.md` — release history and in-progress scope
- `docs/skills/codex-prompt-generator.md` — how to write Codex prompts
- `docs/skills/README.md` — model/effort philosophy

---

Context loaded. What are we working on?
