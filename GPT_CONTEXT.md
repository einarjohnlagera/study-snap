# GPT_CONTEXT.md — NoteLib Product Context Handoff

> Paste the block below as your **first message** in a new GPT chat session.
> Update this file whenever a new version ships or the roadmap shifts significantly.
> Last updated: v0.15.1 (in progress) — 2026-05-21

---

Here's the full context for our NoteLib product session. Please treat this as the source of truth for where we are. Read it fully before responding to anything.

---

## App: NoteLib (formerly StudySnap)

**What it is:** A notes-first study workspace — not a generic AI tool. Users capture notes, generate AI-powered Study Packs from them, and practice with quizzes. Every feature must connect to a measurable learning outcome.

**Core learning loop:** Capture → Generate → Review → Improve → Copy → Repeat

**Positioning:** "Your notes become your study system."

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

Four active profiles. `PARENT` enum exists but has zero implementation — do not propose it without flagging the schema blocker.

| Feature | Student | Exam Reviewer | Teacher | Professional |
|---|---|---|---|---|
| Dashboard focus | Learning continuity, weak concepts | Exam countdown, challenge practice | Quiz creation, export readiness | Certification / interview prep |
| Quiz modes | Quick Review, Challenge, Adaptive (Plus+), Long Exam (Pro) | Quick Review, Challenge, Board Exam (Pro) | Challenge preview (no scoring) | Quick Review, Challenge ("Certification Review"), Long Exam ("Full Practice Exam"), Interview Practice (Pro) |
| Special features | — | Exam date tracking | Exam Builder, DOCX export, per-note learner level | Interview Practice Mode |
| Plan overrides | — | — | Free: 10 DOCX/mo; Plus: unlimited DOCX | — |

---

## Core Architecture

- **Note** is the primary entity. States: `DRAFT → GENERATING → FAILED → STUDY_PACK_READY`. Visibility: `PRIVATE / PUBLIC`.
- **Study Pack** is AI-generated content attached to a Note (summary, key concepts, quiz). Notes carry an optional per-note `learnerLevel` override that Study Pack generation and question pools use first, falling back to the profile.
- **Quiz sessions** use a shared `quick_review_sessions` entity across all modes. Mode stored as `QuickReviewSessionMode` enum; session state (questions, choices, timer) as JSONB.
- **Pre-generated question pools** — new Study Pack generation asynchronously creates reusable Long Exam (48 questions) and Board Exam (24 questions) pools. Exams start immediately without live LLM generation.
- **Plans:** FREE / PLUS / PRO with monthly quotas. Payments via Xendit manual renewal. Export quotas are profile-aware: Teacher DOCX exports get higher/unlimited limits vs. the standard plan cap.

---

## Quiz Mode Hierarchy (locked contract — exactly 5 modes)

1. **Quick Review** — practice, all plans
2. **Challenge Quiz** — exam, progressive 5→20 questions, all plans (with quota); labeled "Certification Review" for Professional profile
3. **Adaptive Practice** — practice, targets weak concepts, Plus/Pro; **Interview Practice is a sub-mode** (JSONB `subMode: "INTERVIEW"` on the `ADAPTIVE` discriminator, Pro only, Professional profile only) — this preserves the 5-mode contract
4. **Long Exam** — exam, fixed long-form (20–30 Qs), Pro only; up to 3 same-subject notes; labeled "Full Practice Exam" for Professional profile; 10 sessions/mo cap
5. **Board Exam** — exam, high-stakes simulation, Pro only, Exam Reviewer profile only; 5 sessions/mo cap; pre-generated 24-question pool

Rule: adding a sixth mode requires updating `docs/product/EXAM_MODES.md` and `ROADMAP.md` together. Do not propose a sixth mode without flagging this.

---

## Release History

### v0.15.1 — Current Release (In Progress)

**Theme:** Teacher Power Features — concrete classroom controls on top of v0.15.0's teacher flow foundation.

| Item | Status |
|---|---|
| Question count control on Generate Quiz (10/20/30; Plus+ unlocks 20/30) | Planned |
| Custom DOCX header (school name, class/section, date — profile + per-export) | Planned |
| Multiple exam versions A/B/C (single DOCX, deterministic shuffle; Plus+ only) | Planned |

---

### v0.15.0 — Released

**Theme:** Premium Mode Uplift + Cost-Control Quota Refactor

**Key shipped:**
- Long Exam and Board Exam simulation-grade experience: in-session focus mode (app chrome hides, dedicated top bar + tabular timer), pre-generated question pools (instant exam start), ScoreReveal result screen, Question Navigator added to Long Exam, pre-session redesign
- Monthly caps: Long Exam 10/mo Pro, Board Exam 5/mo Pro; counters visible in Settings → Plan & Billing
- Teacher plan accessibility: Free Teacher 10 DOCX/mo, Plus Teacher unlimited DOCX; PDF limits unchanged; billing page shows resolved DOCX/PDF quotas separately
- Exam Builder UX: add notes without leaving the builder (target-section picker), balance controls relocated below section list with distinct icons, section summary as chips
- Per-note learner level override: teachers set difficulty per class note independently of their profile default
- Learner Level and Course/Program required on notes (asterisks, aggregate amber toast)
- Landing page repositioned: workspace-first hero, profile showcase, "Five study modes" section
- Fix: Long Exam/Board Exam generation timeout + multi-note INVALID_LONG_EXAM_SOURCE false positive

---

### v0.14.0 — Released

**Theme:** Grow the Surface, Deepen the Practice

**Key shipped:** Interview Practice Mode (Pro, Professional profile, sub-mode of Adaptive); Multi-note Long Exam (Pro, up to 3 same-subject notes); "Board Taker" renamed to "Exam Reviewer"; Subject landing pages (SEO); faster quiz generation (parallel LLM calls + async Long Exam start).

---

### v0.13.0 — Released

**Theme:** Complete the Promise, Reach New Audiences

**Key shipped:** Long Exam Mode v1 (Pro, single note); Professional Profile activated; Pro plan / quota infrastructure; Proration design doc.

---

### v0.12.0 — Released

**Theme:** Learning Experience, Discovery, and Retention

**Key shipped:** Progressive Challenge Quiz (5→20); Google social login; Public Library conversion funnel; Retention loops; Guidance Foundation System; conversion funnel polish (PaywallModal, PostSuccessUpgradeNudge, getUpgradeCtas); Public creator attribution (@usernames); Adaptive Practice tier reconciliation (Plus=10/mo, Pro=30/mo).

---

## Future Roadmap (post v0.15.1)

- **Multi-note Board Exam** — Pro users span Board Exam across up to 3 same-subject notes; blocked on single-note Board Exam usage data
- **Premium mode quota surfacing** — per-mode caps shown more prominently in Settings; honest framing as "monthly plan inclusions"
- **Board Exam advanced result analytics** — domain trend, percentile framing
- **Long Exam tier promotion to Plus** — only if usage data justifies LLM cost
- **Authenticated redirect on marketing pages** — signed-in users go to `/dashboard` from `/`, `/pricing`, `/learn`
- **Public Library trending section** — blocked on windowed engagement fields in backend
- **Cross-profile mode unlock** — Student opts into Board Exam without changing profile
- **Recurring billing / auto-renewal**
- **Connected account management** (unlink Google, add password for Google-only users)

---

## Profile Type Notes

**Professional Profile (active since v0.13.0):**
- Use case: interview and certification prep
- Interview Practice = sub-mode of Adaptive Practice (`subMode: "INTERVIEW"` in session JSONB); preserves 5-mode contract
- Pro only, 10 sessions/mo; `gpt-4.1` generation + `gpt-4.1-mini` critique split for margin control
- Challenge Quiz = "Certification Review"; Long Exam = "Full Practice Exam" — display labels only, engine discriminators unchanged

**Teacher Profile:**
- Core workflow: Generate → View → Export (DOCX); all teacher data uses `generatedQuiz`, never `quizSession`
- Plan override: Free=10 DOCX/mo, Plus=unlimited DOCX, PDF stays standard
- Exam Builder: multi-note combined DOCX with editable sections, Even/Smart Balance, in-builder Add Notes
- Per-note learner level: teachers calibrate difficulty per class note for Study Pack and question pool generation
- v0.15.1 adds: question count control (10/20/30), custom DOCX header, multiple exam versions (A/B/C)
- Do NOT mix teacher preview with student session logic; teacher flow uses `generatedQuiz` only

**Parent Profile (`PARENT` enum exists, zero implementation):**
- Use case: read-only oversight of a linked child's notes, scores, and weak areas
- Architectural blocker: needs a parent↔child relationship model — invite/link model, child confirmation, data visibility scope all need design first
- Do not propose implementation until the schema design is agreed

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
- `docs/product/PLANS.md` — plan tiers and quotas (including Teacher profile override)
- `docs/features/` — per-feature behavior rules
- `RELEASES.md` — release history and in-progress scope
- `docs/codex-prompts/` — ready-to-run Codex prompts for active work
- `docs/skills/codex-prompt-generator.md` — how to write Codex prompts
- `docs/skills/README.md` — model/effort philosophy

---

Context loaded. What are we working on?
