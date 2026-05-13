# EXAM_MODES.md — NoteLib Exam Mode Hierarchy

## Purpose

This document is the canonical reference for NoteLib's quiz and exam mode architecture. It defines the **identity, audience, UX boundaries, monetization, and future direction** of every quiz-flavored mode so the product does not drift as new modes are added.

If a mode discussion contradicts another doc, this document is the source of truth for **mode identity and hierarchy**. Operational rules continue to live in feature docs (`quiz.md`, `challenge-quiz.md`, `quick-review.md`, `adaptive-practice.md`, etc.). Plan/billing rules continue to live in `docs/product/PLANS.md`.

This document does **not** prescribe specific question counts, time limits, or quotas. Those are implementation decisions and live in feature docs and runtime config.

---

## Vocabulary

- **Quiz Session Engine** (informally "the engine") — the shared backend session, generation, scoring, persistence, and recovery pipeline used by all timed quiz modes. Defined by the existing `quizSession` aggregate with a mode discriminator (`QUICK_REVIEW`, `CHALLENGE`, `ADAPTIVE`; future: `LONG_EXAM`, `BOARD_EXAM`).
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
| Adaptive Practice | Practice | Plus / Pro (per `PLANS.md`) | Targeted weak-area reinforcement | `ADAPTIVE` |
| Long Exam | Exam | Student profile (primary), Board Taker (secondary) | Long-form mastery testing | `LONG_EXAM` (planned) |
| Board Exam | Exam | Board Taker profile | High-stakes simulation | `BOARD_EXAM` (currently presented as a Challenge variant; future: own discriminator) |

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
- **Boundary**: Re-uses the base Study Pack quiz; not LLM-generated per session. Result screen guides into Challenge Quiz / Adaptive Practice.

No identity changes from `quick-review.md`; included here for completeness.

### Challenge Quiz (existing — keep flexible)

- **Identity**: *Practice with stakes.* Flexible, progressive, user-controlled. The default exam-style entry for everyday studying.
- **Audience**: All profile types, all plans (with monthly quota per `PLANS.md`).
- **Boundary**:
  - Progressive generation (`5 → 20`).
  - Mid-flight `+5 Questions` and early submit are **core to the identity**.
  - Score is computed against answered questions.
  - Result screen guides the next study step (`Practice Weak Concepts`, retry, etc.).
- **What Challenge Quiz must never become**: a fixed, sit-down exam. The flexibility is the value. If a stricter exam is needed, route to Long Exam or Board Exam — do not bend Challenge Quiz.

### Adaptive Practice (existing)

- **Identity**: Targeted weak-area reinforcement. Not a full-coverage assessment.
- **Audience**: Plus and Pro per `PLANS.md`. See *Open Discrepancies* below.
- **Boundary**: Generated only from weak concepts. Result screen `Generate New Set` only.

### Long Exam Mode (new — Student-facing)

- **Identity**: *Broad mastery testing.* The Student answer to "have I really learned this material?" Spans a whole topic or set of notes; longer than Challenge Quiz; less ceremony than Board Exam.
- **Audience**: Student profile (primary). Available to Board Taker profile as a less-ceremonial long-form practice option.
- **Vibe**: Serious but practice-oriented. Closer to a comprehensive in-class exam than a board exam sitting.
- **Boundary**:
  - **Question count**: fixed at start, adapts to learner level. Larger than Challenge Quiz, smaller than a board sitting. Specific values are an implementation decision.
  - **Generation**: not progressive. The exam set is generated and committed before the user begins.
  - **Pacing**: flexible — pause and resume allowed; per-section timing optional; total time is loose. Focus is on completion, not speed.
  - **Source**: single note at launch; multi-note as a follow-up capability.
  - **Result**: a *mastery report* — coverage, weak domains, suggested next study step. Inline learner-level adjustment IS allowed (this is still a study tool).
  - **Setup**: confirmation screen ("This is a longer session — set aside ~N minutes"). Less ceremony than Board Exam.
- **What Long Exam must never become**:
  - A longer Challenge Quiz with the same UX. The experience must feel structured and graded, not progressive.
  - A board exam simulation. If a user wants a strict exam, that is Board Exam Mode.

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
| **Result** | "Practice Weak Concepts" / retry / keep going | A *score report*: overall result, domain breakdown, study-plan recommendation, optional `Schedule re-take`. No inline learner-level pill |
| **Psychological** | Encouraging, low-friction | Serious, consequential, premium |

These differences are not cosmetic. Implementation should treat them as Board Exam Mode's **identity contract** — not a "Challenge Quiz with flags." The current SPEC.md framing of Board Exam as "the strict exam-simulation presentation of the Challenge Quiz engine" remains correct at the engine layer, but the product surface must diverge along every dimension above.

#### What Board Exam must never become

- A longer Challenge Quiz with a stricter timer.
- A flow that lets users adjust their learner level mid- or post-exam (breaks simulation framing).
- A flow with progressive generation.

---

## Audience & Profile-Type Mapping

Profile type controls **which exam modes are visible** in mode-selection. The engine and feature docs remain unified.

| Profile | Quick Review | Challenge Quiz | Adaptive Practice | Long Exam | Board Exam |
|---|---|---|---|---|---|
| Student | ✓ | ✓ (default emphasis) | ✓ (per `PLANS.md`) | ✓ (default emphasis on long-form entry) | Hidden by default |
| Board Taker | ✓ | ✓ | ✓ (per `PLANS.md`) | ✓ (secondary; less ceremony than Board Exam) | ✓ (default emphasis) |
| Teacher | Quiz Preview only | Quiz Preview only | — | — | — |

### Cross-profile escape hatch

Hiding Board Exam Mode from Students removes a Pro upsell surface. To mitigate without restoring the confusion, mode-selection for Students should include a single muted line:

> *Preparing for boards? Switch profile in Settings to enable Board Exam Mode.*

This preserves discoverability without forcing a Pro-grade simulation experience into a Student's casual study flow. It is a deliberate trade-off: clarity over surface area.

### Teacher

Teacher remains scoped to Quiz Preview / Export workflows per `teacher-flow.md`. Teacher does not "take" any exam mode. No Teacher-facing exam-mode work is in scope here.

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
- **Board Exam**: pre-flight checklist — "Time required: X. Do not refresh. Fullscreen recommended." The setup screen is part of the simulation.

Setup tone is part of mode identity. Do **not** unify these into a single `ExamSetup` component without preserving per-mode framing.

### In-Session

- **Challenge Quiz**: keep current progressive UX intact.
- **Long Exam**: no progressive generation; allow pause/resume; show progress out of fixed total; section markers optional.
- **Board Exam**: reduced chrome, fullscreen, no learner-level controls, no `+N Questions`, no encouraging microcopy. Timer is dominant.

### Leave Guard

- **Challenge Quiz**: existing forfeit guard.
- **Long Exam**: pause-friendly. `Leave & Resume Later` is the primary leave action; `Forfeit Long Exam` is secondary.
- **Board Exam**: `Forfeit Exam` is the only leave path; phrasing matches simulation framing. No "save & resume."

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
| Difficulty selection | — | — | ✓ |
| Board Exam Mode | — | — | ✓ |

### Recommended positioning for new modes

| Feature | Free | Plus | Pro |
|---|---|---|---|
| Long Exam Mode (single-note) | — | — | ✓ (launch Pro-only) |
| Long Exam Mode (multi-note) | — | — | ✓ |
| Board Exam Mode | — | — | ✓ |

Reasoning:

- **Long Exam launches Pro-only** to control multi-note generation cost and to preserve Pro's "exam prep" identity. Plus already has a unique value prop in Adaptive Practice; it does not need Long Exam to differentiate at launch.
- **Shared "Advanced Exam" quota bucket** for Long + Board reduces per-mode counter sprawl. One quota; two modes. The specific number is an implementation decision.
- **Promote Long Exam to Plus only after** runtime usage data justifies the LLM cost — and only if Plus needs the differentiator. State this as a future direction, not a v1 decision.

### Future premium positioning opportunities (planning-only)

- **Score-report depth** (percentile-style framing, trend over time, schedule re-take) as Pro-only Board Exam refinements.
- **Curated exam decks / cohort content** (Board Exam practice on curated content packs) as a future Pro+ tier.
- **Exam analytics history** (long-term performance tracking) as a Pro-only refinement.

These are scaffolding for future tier work, **not v0.13 commitments**.

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
2. **Roadmap continuity.** The v0.12.0 line `"Multi-topic exam / Long Exam mode planning"` should be updated to point to this doc and reflect the redefined scope (Long Exam = Student-facing exam mode; multi-topic = a capability).

---

## Roadmap Pointers

This doc proposes shape, not schedule. Concrete sequencing is owned by `ROADMAP.md` and `RELEASES.md`.

### v0.12.x (in-flight)

- Resolve the **Adaptive Practice tier mismatch** in docs and runtime.
- **Board Exam premium UX polish**: pre-flight setup, score-report-style result framing, fullscreen behavior, removal of inline learner-level pill on result. **Presentation only — no engine changes.**
- **Long Exam Mode spec** finalized based on this doc; remains planning-only in v0.12.x.

### v0.13+ (planned)

- **Long Exam Mode v1** (single-note, fixed long-form, Pro-only).
- **Profile-aware mode-selection rendering** (hide Board Exam from Students; cross-profile escape-hatch line).
- **Learner-level grouped UX** in profile and post-onboarding confirmation surfaces (the existing Dashboard prompt; **not** a new step inside `/onboarding`).
- **Guidance Engine extensions**: `cooldownMs`, dashboard contextual tips, course/program inline reminder.

### v0.14+ (later)

- **Multi-note Long Exam**.
- **Board Exam advanced result analytics** (trend over time, percentile-style framing).
- **Long Exam tier promotion to Plus** if usage data justifies it.

### Planning-only

- **Cross-profile mode unlock** (Students opting into Board Exam without changing profile).
- **Curated exam decks / cohort content** (Pro+).
- **Cross-profile journey** (Student → Board Taker upgrade flow with continuity).

---

## Constraints (recap)

- **Three exam modes total**: Challenge / Long / Board. Adding a fourth requires updating this document and `ROADMAP.md` together.
- Learner Level and Course/Program remain separate concerns.
- Onboarding stays simplified; new prompts ride the Guidance Engine.
- Challenge Quiz keeps progressive flexibility.
- Board Exam never becomes "longer Challenge Quiz."
- Teacher remains scoped to Quiz Preview / Export.
- No new persistence aggregates per profile or per mode.

---

## Cross-Reference

- Engine implementation: `docs/features/quiz-session.md`
- Practice modes: `docs/features/quick-review.md`, `docs/features/adaptive-practice.md`
- Exam modes: `docs/features/challenge-quiz.md` (Long Exam and Board Exam to extend)
- Profile context: `docs/features/profile-learning-context.md`
- Plans (canonical): `docs/product/PLANS.md`
- Spec: `docs/product/SPEC.md` §`Quiz entry defaults`, §`Profile type effects`, §`Challenge Quiz`
- Project context: `docs/PROJECT_CONTEXT.md`
