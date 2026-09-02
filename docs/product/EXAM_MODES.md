# EXAM_MODES.md — NoteLib Exam Mode Hierarchy

## Purpose

This document is the canonical reference for NoteLib's quiz and exam mode architecture. It defines the **identity, audience, UX boundaries, monetization, and future direction** of every quiz-flavored mode so the product does not drift as new modes are added.

If a mode discussion contradicts another doc, this document is the source of truth for **mode identity and hierarchy**. Operational rules continue to live in feature docs (`quiz.md`, `challenge-quiz.md`, `quick-review.md`, `adaptive-practice.md`, etc.). Plan/billing rules continue to live in `docs/product/PLANS.md`.

This document does **not** prescribe specific question counts, time limits, or quotas. Those are implementation decisions and live in feature docs and runtime config.

---

## Vocabulary

- **Quiz Session Engine** (informally "the engine") — the shared backend session, generation, scoring, persistence, and recovery pipeline used by all timed quiz modes. Defined by the existing `quizSession` aggregate with a mode discriminator (`QUICK_REVIEW`, `CHALLENGE`, `ADAPTIVE`, `LONG_EXAM`, `BOARD_EXAM`).
- **Mode** — a product-facing presentation layer over the engine. Modes share the engine; they differ in identity, framing, parameters, and surfaces.
- **Mode family**:
  - *Practice modes* — Quick Review, Adaptive Practice
  - *Exam modes* — Challenge Quiz, Long Exam, Board Exam

---

## Mode Hierarchy (locked)

NoteLib supports exactly **five quiz-flavored modes** — three exam modes plus two practice modes. Adding a sixth mode requires updating this document.

| Mode | Family | Audience | Vibe | Engine discriminator |
|------|--------|----------|------|----------------------|
| Quick Review | Practice | All | Lightweight, encouraging | `QUICK_REVIEW` (uses base Study Pack quiz) |
| Challenge Quiz | Exam | All | Flexible, progressive, practice with stakes | `CHALLENGE` |
| Adaptive Practice | Practice | All plans with quota (`Free` 3/mo, `Plus` 10/mo, `Pro` 30/mo per `PLANS.md`) | Targeted weak-area reinforcement | `ADAPTIVE` |
| Long Exam | Exam | Student profile (primary), Board Taker (secondary) | Long-form mastery testing | `LONG_EXAM` |
| Board Exam | Exam | Board Taker profile | High-stakes simulation | `BOARD_EXAM` (currently presented as a Challenge variant; future: own discriminator) |

### Sub-modes (variants of an existing mode, do NOT count toward the 5)

Sub-modes are presentation/generation variants that share an existing engine discriminator. They are surfaced under different labels and entry points but do not add a sixth mode. Sub-mode identity is carried in session state JSONB (e.g. `subMode: "INTERVIEW"`), never as a new discriminator.

| Sub-mode | Parent mode | Discriminator | Audience | Differentiator |
|----------|-------------|---------------|----------|----------------|
| Interview Practice | Adaptive Practice | `ADAPTIVE` + `subMode: "INTERVIEW"` | Professional profile, Pro plan at Start CTA | Scenario-based questions + per-answer AI critique + Interview Readiness Report result framing |

Adding a new sub-mode requires updating this table and the parent feature doc. A sub-mode that requires a new persistence aggregate, new session lifecycle state, or its own engine logic is **not a sub-mode** — it must be promoted to a full mode and go through the 5-mode contract review.

### Naming clarification (history)

The earlier roadmap line *"Multi-topic exam / Long Exam mode planning — design a Board Exam session that spans multiple notes or topics"* framed Long Exam as a multi-topic Board Exam. **This document supersedes that framing.**

- **Long Exam Mode** is the Student-facing long-form exam mode.
- **Multi-topic** is a *capability* of Long Exam (and optionally Board Exam later), not a separate mode.

If a future requirement does not fit cleanly inside Challenge / Long / Board, prefer extending one of the three with a new capability flag rather than introducing a fourth exam mode.

---

## Mode Identities

### Quick Review (existing)

- **Identity**: Lightweight concept check. Entry point to active recall.
- **Audience**: All profile types, all plans.
- **Boundary**: Re-uses the base Study Pack quiz; not LLM-generated per session. **Result screen guides into the source note or Challenge Quiz — never into Adaptive Practice** (amended 2026-08-13, see below). Quick Review writes `ConceptHealth` on completion (since 2026-07-11, `fix: track ConceptHealth on Quick Review completion` — Free-tier's primary quiz mode previously left no durable spaced-repetition signal) and can move mastery, due-state, and Overall Readiness the same as Challenge Quiz and Adaptive Practice.

**Amendment, 2026-08-13 (owner decision, `v0.74.0`): Quick Review's result screen no longer routes into Adaptive Practice.** This line previously read *"Result screen guides into Challenge Quiz / Adaptive Practice."*

- **Why.** Adaptive Practice is quota-limited, LLM-generated remediation (Free 3/mo). Routing a learner into it from a 5-question static refresher spends a scarce paid resource on a weak signal. The result screen now offers **Review the Notes** before mastery and **Take a Challenge** after it.
- **Discovery is unaffected.** Adaptive Practice is still reached from the Dashboard's Today Focus (both the due-concepts and weak-concepts branches) and from the mode-selection screen, so this removes a route, not the mode.
- **⚠️ This amendment is deliberately narrow, and the boundary matters.** The owner's reasoning — *"Quick Review is just a static quiz, not the real quiz"* — is the same reasoning behind `8c7a4821` (2026-07-03), which excluded Quick Review from `ConceptHealth` writes and **was reverted eight days later** by `6d054bad`. **Only routing changed here. `ConceptHealth` writes stay**, because Quick Review's *first attempt* at each question is still a genuine assessment — and `v0.74.0`'s justification for locking the Quiz tab depends on that write existing. **Do not extend this amendment into a second exclusion of `ConceptHealth`** without reckoning with the reverted attempt and with `v0.74.0`'s rationale.

No other identity changes from `quick-review.md`; included here for completeness.

### Challenge Quiz (existing — keep flexible)

- **Identity**: *Practice with stakes.* Flexible, progressive, user-controlled. The default exam-style entry for everyday studying.
- **Audience**: All profile types, all plans (with monthly quota per `PLANS.md`).
- **Boundary**:
  - Progressive generation (`5 → 20`).
  - **Multi-note retrieval is a Challenge Quiz capability, not a mode or sub-mode.** From a verified Study Plan, Free may select up to 3 sources and Plus receives the server-derived cap of **6** (a fixed 18-question multi-note count divided by the three-per-source floor — deliberately NOT Long Exam's 6 / 8 / 10, which comes from 20 / 25 / 30, and deliberately not score-adaptive). **PRO's plan launch opens Board Exam rather than multi-note Challenge.** **⚠️ ~10 sources is UNREACHABLE — it needs 30 questions, ten past the 20-question Challenge ceiling that `+5` depends on; do not lift that ceiling to chase it (owner, 2026-09-02)**; questions, scoring, result framing, and the `CHALLENGE` discriminator remain unchanged.
  - Mid-flight `+5 Questions` and early submit are **core to the identity**.
  - Score is computed against answered questions.
  - Result screen guides the next study step (`Practice Weak Concepts`, retry, etc.).
- **What Challenge Quiz must never become**: a fixed, sit-down exam. The flexibility is the value. If a stricter exam is needed, route to Long Exam or Board Exam — do not bend Challenge Quiz.

### Adaptive Practice (existing)

- **Identity**: Targeted weak-area reinforcement. Not a full-coverage assessment.
- **Audience**: All learning profiles with monthly quota per `PLANS.md` (`Free` 3/mo, `Plus` 10/mo, `Pro` 30/mo). See *Open Discrepancies* below.
- **Boundary**: Generated only from weak concepts. Result screen `Generate New Set` only.

### Long Exam Mode (new — Student-facing)

- **Identity**: *Broad mastery testing.* The Student answer to "have I really learned this material?" Spans a whole topic or set of notes; longer than Challenge Quiz; less ceremony than Board Exam.
- **Audience**: Student profile (primary). Available to Board Taker profile as a less-ceremonial long-form practice option.
- **Vibe**: Serious but practice-oriented. Closer to a comprehensive in-class exam than a board exam sitting.
- **Boundary**:
  - **Question count**: fixed at start, adapts to learner level. Larger than Challenge Quiz, smaller than a board sitting. Specific values are an implementation decision.
  - **Generation**: not progressive. The exam set is generated and committed before the user begins.
  - **Pacing**: countdown timer (90 seconds per question, server-anchored). Timer never stops — tab close does not pause the clock. Timer expiry triggers auto-submit. Leave = forfeit; there is no pause/resume option available to the user.
  - **Source**: single note at v1 launch; multi-note (cross-topic exam spanning 2–4 notes from the same subject) is the planned v0.14+ evolution — see Roadmap Pointers.
  - **Result**: a *mastery report* — coverage, weak domains, suggested next study step. Inline learner-level adjustment IS allowed (this is still a study tool).
  - **Setup**: confirmation screen ("This is a longer session — set aside ~N minutes"). Less ceremony than Board Exam. No difficulty selector; Long Exam defaults to Mixed.
- **What Long Exam must never become**:
  - A longer Challenge Quiz with the same UX. The experience must feel structured and graded, not progressive.
  - A board exam simulation. If a user wants a strict exam, that is Board Exam Mode.
  - A mode where pause/resume is offered as a leave option. Leave = forfeit, same as Board Exam. The anti-procrastination principle applies equally here.

### Board Exam Mode (existing — sharpen identity)

- **Identity**: *High-stakes exam simulation.* The user is sitting an exam, not practicing. Premium-grade flow.
- **Audience**: Board Taker profile, Pro plan.
- **Vibe**: Immersive, timed, serious. Designed to feel different from any other mode in NoteLib.

#### What makes Board Exam fundamentally different from Challenge Quiz

| Dimension | Challenge Quiz | Board Exam |
|---|---|---|
| **UX** | Inline action bar, friendly hints, progressive generation, learner-level pill on result | Pre-flight confirmation ("Begin Exam"), reduced UI chrome, no progressive controls, no inline learner-level adjustment on result |
| **Behavioral** | `+5 Questions`, `Complete Quiz`, "Almost there" microcopy | No question generation mid-session, `Submit Exam` with strong confirmation, no encouragement microcopy |
| **Navigation** | Standard `Leave Quiz` modal; forfeit available | `Forfeit Exam` modal with stronger language; full-screen request; confirmed leaves are recorded as forfeits with timestamp |
| **Scoring** | Score against answered questions (early submit fair) | Score against full question count (unanswered = wrong, like a real exam) |
| **Pacing** | Per-question or freeform | Fixed total timer; section markers if multi-domain; no pause |
| **Difficulty** | No manual selector (removed v0.60.1); fully automatic from the last Quick Review score | No selector; defaults to Mixed to preserve simulation framing |
| **Result** | "Practice Weak Concepts" / retry / keep going | A *score report*: overall result, domain breakdown, study-plan recommendation, optional `Schedule re-take`. No inline learner-level pill |
| **Psychological** | Encouraging, low-friction | Serious, consequential, premium |

These differences are not cosmetic. Implementation should treat them as Board Exam Mode's **identity contract** — not a "Challenge Quiz with flags." The current SPEC.md framing of Board Exam as "the strict exam-simulation presentation of the Challenge Quiz engine" remains correct at the engine layer, but the product surface must diverge along every dimension above.

#### What Board Exam must never become

- A longer Challenge Quiz with a stricter timer.
- A flow that lets users adjust their learner level mid- or post-exam (breaks simulation framing).
- A flow with progressive generation.

---

## Non-Engine Review Surfaces (locked classification)

Not every study activity is a quiz mode. Flashcards and Memorization are **review-only surfaces that sit outside the Quiz Session Engine entirely** — they do not add a 6th/7th mode, do not use a `quizSession` discriminator, and never write `ConceptHealth`. This mirrors Quick Review's existing "practice, don't assess" boundary, applied to a non-timed, non-scored surface.

| Surface | Engine? | `ConceptHealth` on completion? | Audience / Plan |
|---|---|---|---|
| Flashcards | No — new frontend-only surface over Study Pack content | No | All profiles, all plans (Free) |
| Memorization | No — Flashcards' matching logic + a new, separate `memorization_cards` entity | No | All profiles, all plans (Free) |

- **Flashcards**: renders each `keyConcepts` entry as a card front; the back is the matching `QuizItem.explanation` (matched by normalized, bidirectional-substring fuzzy concept match) where a quiz question exists for that concept, else a "no definition yet" fallback. No new AI/LLM call — reuses data already returned by the existing note-detail/Study Pack read path. No new endpoint. Flip/self-review interaction only; no scoring, no session, no `ConceptHealth` write.
- **Memorization** (locked at its own kickoff): Flashcards' concept-matching logic, minus fallback cards — only concepts with a real matched `QuizItem.explanation` are eligible, since self-grading a card with no answer is meaningless. Adds a real spaced-repetition schedule per `(user, study_pack, concept)`, stored in a **new, separate `memorization_cards` entity** — never a `ConceptHealth` column, never joined or read by `ProgressReportService` or any readiness/`Overall Readiness` calculation (firewalled by design, not just convention). Scheduling algorithm: simplified SM-2 (Anki-family) — 4-button self-grade (Again/Hard/Good/Easy); Again resets the interval and lowers the ease factor; Hard/Good/Easy grow the interval (first success flips to a fixed 1-day or 4-day interval, subsequent successes multiply the prior interval by the ease factor). One-card-at-a-time review ordered by due date, not list order — not a Previous/Next browse like Flashcards. New endpoints: `GET /notes/{id}/memorization` (due state per eligible concept) and `POST /notes/{id}/memorization/grade` (persist a grade, recompute schedule). Entry point sits beside Flashcards on the Key Concepts tab (same non-engine-surface placement).

Constraint: neither surface may be routed through `QuickReviewSessionEntity` or any other engine discriminator. If a future requirement needs scoring, a timer, or persistence beyond spaced-repetition scheduling, it no longer fits this classification and must go through the 5-mode contract review instead.

---

## New Question Formats (locked classification)

Identification and Enumeration are new **question formats on the existing Quiz Session Engine** — not a 6th/7th mode, not a new discriminator, not a review-only surface. They behave exactly like MCQ/TRUE_FALSE/MULTI_SELECT/MATCHING already do: generated by a mode's prompt, scored on submission, and write `ConceptHealth` on completion via the same path every other format already uses, regardless of mode. Whether a given session writes `ConceptHealth` is a property of the **mode**, not the question format — Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, and Board Exam all write it today; only Flashcards and Memorization (review-only surfaces outside the Quiz Session Engine entirely — see above) never do.

| Format | Status | Where it generates | Scoring |
|---|---|---|---|
| Identification | Shipped (v0.39.0) | Challenge Quiz first; Long Exam fast-follow | Free-text input matched against a generation-time `acceptableAnswers[]` list (normalized, case-insensitive). No per-submission LLM call. |
| Enumeration | Shipped (v0.39.0) | Challenge Quiz | Fixed-N free-text inputs (N = required item count), each matched order-independently against a generation-time `acceptableAnswerGroups: List<List<String>>` (one synonym group per required item) via exhaustive bipartite matching — a naive first-match-greedy assignment can wrongly reject a valid answer when synonym groups overlap. **All-or-nothing**: every required item must match a distinct group for the question to count correct — same boolean correct/incorrect model as every other format, no partial credit. Same normalization, no per-submission LLM call. |

- **Ungated — available to every plan tier.** Per the product principle "gate control/workflows, not learning quality": question-format variety is a learning-quality dimension, so it is not a Plus/Pro differentiator. This applies to both formats regardless of which mode they later reach. Monetization continues to live in existing mode-level and quota-level differentiation (Board Exam Pro-only, Adaptive Practice quota tiers) — not in which question formats a user can see.
- **Consistent mix per progressive-generation batch.** Challenge Quiz's 5→20 progressive batches must each carry the same format mix (some MCQ, some Identification) rather than segregating by type across batches (e.g. never "first 5 MCQ, then next 5 Identification") — segregating by batch reads as arbitrary rather than intentional variety.
- **Board Exam**: excluded pending a real-exam-fidelity check — if the board exams a given Board Exam session models are MCQ-only in reality, adding free-text formats breaks the simulation and the format should stay excluded there.
- **Quick Review**: out of scope for v1. Quick Review is a locked review-only surface (see above); adding assessment formats there is a separate decision, not required to make Free's experience feel rich (Free already reaches these formats via Challenge Quiz and its Adaptive Practice quota).

---

## Audience & Profile-Type Mapping

Profile type controls **which exam modes are visible** in mode-selection. The engine and feature docs remain unified.

| Profile | Quick Review | Challenge Quiz | Adaptive Practice | Long Exam | Board Exam |
|---|---|---|---|---|---|
| Student | ✓ | ✓ (default emphasis) | ✓ (per `PLANS.md`) | ✓ (default emphasis on long-form entry) | Hidden by default |
| Board Taker | ✓ | ✓ | ✓ (per `PLANS.md`) | ✓ (secondary; less ceremony than Board Exam) | ✓ (default emphasis) |
| Teacher | Quiz Preview only | Quiz Preview only | — | — | — |
| Professional | ✓ | ✓ (shown as "Certification Review") | ✓ (per `PLANS.md`) | ✓ Pro only (shown as "Full Practice Exam") | Hidden |

### Cross-profile escape hatch

Hiding Board Exam Mode from Students removes a Pro upsell surface. To mitigate without restoring the confusion, mode-selection for Students should include a single muted line:

> *Preparing for boards? Switch profile in Settings to enable Board Exam Mode.*

This preserves discoverability without forcing a Pro-grade simulation experience into a Student's casual study flow. It is a deliberate trade-off: clarity over surface area.

### Teacher

Teacher remains scoped to Quiz Preview / Export workflows per `teacher-flow.md`. Teacher does not "take" any exam mode. No Teacher-facing exam-mode work is in scope here.

### Professional

Professional Profile uses the same exam mode access as Student, with profile-aware display label overrides. See `docs/features/professional-profile.md` for the full feature spec.

Label overrides applied in `exam-mode-visibility.ts`:

- Challenge Quiz → displayed as **"Certification Review"**
- Long Exam → displayed as **"Full Practice Exam"**

Engine discriminators (`CHALLENGE`, `LONG_EXAM`) do not change. Labels must not appear in backend APIs, session storage, or analytics events.

Interview Practice also appears as a Professional-only mode-selection tile, but it remains a sub-mode of Adaptive Practice (`ADAPTIVE` + `subMode: "INTERVIEW"`), not a sixth quiz mode.

---

## UX Boundaries

### Mode Entry

All exam modes route through the existing shared mode-selection screen. Profile type determines:

- which mode tiles are shown
- which tile is emphasized as default
- the cross-profile escape-hatch line

Profile-type filtering is **presentational only**. The engine accepts any mode for any user; the UI does not.

### Setup Screen

Setup screens scale with the seriousness of the mode:

- **Challenge Quiz**: minimal setup; user can begin immediately.
- **Long Exam**: confirmation screen with expected duration and what is included (single note vs. multi-note). Light disclaimer, friendly tone.
- **Board Exam**: pre-flight checklist — "Time required: X. Do not refresh. Fullscreen recommended." The setup screen is part of the simulation. No difficulty selector; Board Exam defaults to Mixed.

Setup tone is part of mode identity. Do **not** unify these into a single `ExamSetup` component without preserving per-mode framing.

### In-Session

- **Challenge Quiz**: keep current progressive UX intact.
- **Long Exam**: no progressive generation; countdown timer dominant (90s/question, server-anchored); show progress out of fixed total; leave = forfeit with gatekeeper modal; beforeunload warning on tab close; sticky navigation footer; Board Exam-style top bar (Leave Exam / mode label / timer).
- **Board Exam**: reduced chrome, fullscreen, no learner-level controls, no `+N Questions`, no encouraging microcopy. Timer is dominant (60s/question, server-anchored).

### Leave Guard

- **Challenge Quiz**: `Leave Quiz` modal; confirming forfeits the session.
- **Long Exam**: `Leave Exam` modal; confirming forfeits the session. Same forfeit-only pattern as Board Exam. No pause/resume escape hatch. Tab close triggers a `beforeunload` browser warning; the session timer continues running server-side and auto-submits on expiry.
- **Board Exam**: `Forfeit Exam` modal with strongest language; confirming forfeits the session. No "save & resume."

Rationale for unified forfeit behavior across Long Exam and Board Exam: both modes simulate a real exam sitting. Allowing a pause option undermines focus and enables procrastination. The anti-procrastination principle applies equally to both.

### Timer Policy

All three exam modes use a **server-anchored absolute deadline** — not a client-side countdown reset on tab restore.

| Mode | Rate | Extends mid-session? |
|---|---|---|
| Challenge Quiz | 90 seconds / question | Yes — extends by 90s per question when `+5 Questions` is generated |
| Long Exam | 90 seconds / question | No — fixed at session start |
| Board Exam | 60 seconds / question | No — fixed at session start |

- `timerStartedAtEpochSeconds` is stamped server-side when the quiz becomes ready (not at GENERATING time).
- `deadlineEpochSeconds = timerStartedAtEpochSeconds + timeLimitSeconds`.
- On tab return, the frontend recalculates `deadline − now`. If expired, auto-submit fires immediately.
- Timer display uses warning (amber, ≤ 3 min) and urgent (red, ≤ 1 min) states in all three modes.

### Result Screen

- **Challenge Quiz**: existing — guide the next study step.
- **Long Exam**: mastery report — coverage, weak domains, suggested next note/topic, optional "Take again" CTA. Inline learner-level pill IS allowed.
- **Board Exam**: score report — overall result, domain breakdown, study-plan recommendation, optional `Schedule re-take`. No inline learner-level pill (would break simulation framing).

---

## Learner Level — Profile-Aware Grouping

The seven learner levels remain the canonical enum (no merging with Course/Program). The list is reorganized in the UI by profile to reduce cognitive overload.

| Profile | Recommended | Other Learning Styles |
|---|---|---|
| Student | Grade School, Junior High, Senior High, College | Board Exam Review, Professional, Personal Learning |
| Board Taker | Board Exam Review | Grade School, Junior High, Senior High, College, Professional, Personal Learning |
| Teacher | Personal Learning, College | The rest |

Rules:

- Grouping is presentational. The underlying enum and `users.learner_level` storage do not change.
- **Soft auto-suggestion** is acceptable: when a user selects a profile + study goal during onboarding, the picker can pre-highlight (not save) a recommended level. The user must still confirm via the existing Dashboard prompt. Do **not** persist a learner level without an explicit user confirmation.
- Existing Dashboard prompt (`Too easy or too hard?`) and `Adjust level` CTA remain the canonical collection point.
- The grouping rule, once implemented, lives in `docs/features/profile-learning-context.md`. This document references it.

---

## Metadata Onboarding Guidance

Constraint: **do not redesign onboarding.** The current `Profile Type → Study Goal → Input Method → Study Pack Generation → Completion` flow stands.

Direction:

- All future personalization prompts ride on the existing **Guidance Engine** (`lib/guidance-engine.ts`). No new prompt mechanism.
- Acceptable new tips:
  - **Course/Program inline reminder** in the note editor when the field is blank after first save.
  - **Learner-level confirmation prompt** after the first quiz attempt (existing Dashboard prompt; no change).
  - **First-Long-Exam tip**: when a Student has 5+ Study Packs but has never run Long Exam, surface a one-time tip pointing to it.
- Tips fire **only after the first study win**. Never before.
- Use the engine's planned `cooldownMs` and `condition()` callbacks. Do **not** stack multiple prompts on the Dashboard at once.

---

## Premium Positioning & Monetization Boundaries

### Current state (per `PLANS.md`, canonical)

| Feature | Free | Plus | Pro |
|---|---|---|---|
| Quick Review | ✓ | ✓ | ✓ |
| Challenge Quiz | quota | quota | quota |
| Adaptive Practice | — | 10 / mo | 30 / mo |
| Board Exam Mode | — | — | ✓ |

Manual difficulty selection was removed in v0.60.1 (no plan tier gates it); Challenge Quiz difficulty is now fully automatic from the last Quick Review score.

### Recommended positioning for new modes

| Feature | Free | Plus | Pro |
|---|---|---|---|
| Long Exam Mode (single-note) | — | — | ✓ (launch Pro-only) |
| Long Exam Mode (multi-note) | — | — | ✓ |
| Board Exam Mode | — | — | ✓ |

Reasoning:

- **Long Exam launches Pro-only at Start CTA** to control multi-note generation cost and to preserve Pro's "exam prep" identity while still letting Free and Plus users inspect the prestart setup. Plus already has a unique value prop in Adaptive Practice; it does not need Long Exam to differentiate at launch.
- **Long Exam and Board Exam use separate Pro monthly quotas** measured per exam session. Multi-note source counts still shape question-count and generation breadth, but they do not multiply quota deduction.
- **Promote Long Exam to Plus only after** runtime usage data justifies the LLM cost — and only if Plus needs the differentiator. State this as a future direction, not a v1 decision.

### Future premium positioning opportunities (planning-only)

- **Per-mode quota separation (v0.15+)** — Long Exam, Board Exam, and Interview Practice each get their own explicit monthly cap on Pro (proposed: Long 10/mo, Board 5/mo, Interview 10/mo). Replaces today's uncapped Long/Board state. Frames as a transparency uplift, not a quota reduction. Cost-control rationale: a single power user running unlimited Board Exams (~$0.056/session in LLM cost) can exceed Pro revenue from one feature alone. See `ROADMAP.md` Premium Mode Uplift entry.
- **Premium feel for Long Exam and Board Exam (v0.15+)** — visual / framing / pacing polish to differentiate from Challenge Quiz without adding mid-exam coaching. Board Exam stays feedback-free during the session by design; Long Exam stays forfeit-only with no mid-exam coaching. Premium uplift is presentation, not interactive AI.
- **Score-report depth** (percentile-style framing, trend over time, schedule re-take) as Pro-only Board Exam refinements.
- **Curated exam decks / cohort content** (Board Exam practice on curated content packs) as a future Pro+ tier.
- **Exam analytics history** (long-term performance tracking) as a Pro-only refinement.

These are scaffolding for future tier work, sequenced in `ROADMAP.md`.

---

## Architecture Direction

### One engine, multiple modes

- The **Quiz Session Engine** is the canonical backend. The mode discriminator on `quizSession` already supports adding `LONG_EXAM` and `BOARD_EXAM` (the latter currently rides on `CHALLENGE` plus presentation flags; future: own discriminator) without forking storage.
- New modes must:
  - add a value to the discriminator
  - reuse the existing session lifecycle (`GENERATING → IN_PROGRESS → COMPLETED / FORFEITED / FAILED`)
  - reuse the shared generation lock and recovery flow
- New modes must **not** introduce new persistence aggregates.

### Generation context

All exam modes resolve generation context the same way (already in place):

- Note `courseProgram` first, then profile `courseProgram`.
- User `learnerLevel` (defaulting to College).
- Mode-specific parameters (count, difficulty, multi-note inputs) are passed alongside, **never folded into** learner level or course/program.

### Forbidden divergence

- Do not introduce profile-type-specific tables (e.g. `board_exam_sessions`).
- Do not duplicate engine logic across modes. Mode behavior is parameterized; if behavior cannot be parameterized, escalate to update this document **before** forking the engine.

---

## Open Discrepancies (resolve before next milestone)

1. ~~**Adaptive Practice tier mismatch.**~~ ✅ Resolved in v0.12.0 — `docs/features/adaptive-practice.md`, `docs/features/quiz.md`, `docs/PROJECT_CONTEXT.md`, and runtime gating now all align with `PLANS.md`: Plus = 10 / mo, Pro = 30 / mo.
2. ~~**Roadmap continuity.**~~ ✅ Resolved in v0.13.0 — `ROADMAP.md` updated to reflect Long Exam as a Student-facing exam mode; multi-note is a v0.14+ capability documented above.

---

## Roadmap Pointers

This doc proposes shape, not schedule. Concrete sequencing is owned by `ROADMAP.md` and `RELEASES.md`.

### v0.12.x (shipped)

- ✅ Resolved the **Adaptive Practice tier mismatch** in docs and runtime.
- ✅ **Board Exam premium UX polish**: pre-flight setup, score-report-style result framing, fullscreen behavior, removal of inline learner-level pill on result.
- ✅ **Long Exam Mode spec** finalized in this doc.

### v0.13 (shipped)

- ✅ **Long Exam Mode v1 backend**: single-note, fixed long-form, Pro-only session with mastery report. Pause/resume endpoints exist in the backend but are not exposed in the frontend (leave = forfeit per product decision).
- ✅ **Long Exam Mode frontend**: new `/notes/[id]/long-exam` page; Board Exam-style top bar; forfeit-only leave guard; sticky navigation footer; Coming Soon placeholder removed from challenge-quiz mode selection.
- ✅ **Profile-aware mode-selection rendering**: Long Exam hidden from BOARD_EXAM and TEACHER profiles; Board Exam hidden from STUDENT profile with cross-profile escape-hatch line.
- ✅ **Timer fix**: per-question time limits replace hardcoded 600s constant across Challenge Quiz, Board Exam, and Long Exam; server-anchored deadline mechanism for Long Exam; Challenge Quiz timer extends when more questions are generated.
- ✅ **UI consistency**: Previous/Next navigation aligned left/right within card width across all three exam modes.

### v0.14 (shipped)

- ✅ **Multi-note Long Exam**: from a note, Pro users can add 1–3 additional notes from the same subject during the prestart step. **From a Study Plan (`v0.102.0`) the predicate is plan MEMBERSHIP instead of subject, and the cap is the learner-level-derived `maxSourceNotes` (6 / 8 / 10 including the primary) rather than 4.** This adds no mode — it is a capability on Long Exam, which this contract already anticipates. The primary Study Pack remains the session anchor, additional sources are stored as `sourceNoteRefs` in session JSONB, and questions are generated proportionally from each source without adding a new persistence aggregate.
- ✅ **Interview Practice sub-mode**: Professional profile Pro users get an Adaptive Practice sub-mode with scenario MCQs, per-answer AI critique, dedicated monthly quota, and an Interview Readiness Report result screen. Runs on the `ADAPTIVE` discriminator with `subMode: "INTERVIEW"` in session JSONB. 5-mode contract preserved.

### v0.15+ (later)

- **Board Exam advanced result analytics** (trend over time, percentile-style framing).
- **Long Exam tier promotion to Plus** if usage data justifies it.

### Planning-only

- **Cross-profile mode unlock** (Students opting into Board Exam without changing profile).
- **Curated exam decks / cohort content** (Pro+).
- **Cross-profile journey** (Student → Board Taker upgrade flow with continuity).
- **Flexible review methods over one Study Pack** (`ROADMAP.md` v0.39.0) — see "Non-Engine Review Surfaces" above for Flashcards/Memorization (shipped), and "New Question Formats" above for Identification and Enumeration (both shipped, Challenge Quiz only).

---

## Constraints (recap)

- **Three exam modes total**: Challenge / Long / Board. Adding a fourth requires updating this document and `ROADMAP.md` together.
- **Five quiz-flavored modes total** (Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, Board Exam). Sub-modes (e.g. Interview Practice) ride existing discriminators and do not count toward this five.
- Learner Level and Course/Program remain separate concerns.
- Onboarding stays simplified; new prompts ride the Guidance Engine.
- Challenge Quiz keeps progressive flexibility.
- Long Exam never offers pause/resume to the user. Leave = forfeit.
- Board Exam never becomes "longer Challenge Quiz."
- Teacher remains scoped to Quiz Preview / Export.
- No new persistence aggregates per profile or per mode.
- Professional Profile label overrides ("Certification Review", "Full Practice Exam") are display-only in `exam-mode-visibility.ts` and the mode-selection UI. Engine discriminators do not change.
- Interview Practice (sub-mode of Adaptive Practice) carries its variant identity in session state JSONB only. No new `QuickReviewSessionMode` enum value. Pro-only at Start CTA.

---

## Cross-Reference

- Engine implementation: `docs/features/quiz-session.md`
- Practice modes: `docs/features/quick-review.md`, `docs/features/adaptive-practice.md`
- Exam modes: `docs/features/challenge-quiz.md` (Long Exam and Board Exam to extend)
- Profile context: `docs/features/profile-learning-context.md`
- Professional Profile: `docs/features/professional-profile.md`
- Plans (canonical): `docs/product/PLANS.md`
- Spec: `docs/product/SPEC.md` §`Quiz entry defaults`, §`Profile type effects`, §`Challenge Quiz`
- Project context: `docs/PROJECT_CONTEXT.md`
