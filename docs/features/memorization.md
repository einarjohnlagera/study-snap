# memorization.md - NoteLib Feature Context

## Goal

Memorization is a spaced-repetition review surface for Study Pack-ready private notes.

It should feel:

- focused
- return-worthy
- self-paced
- separate from quiz or exam performance

## Availability

- available on Free, Plus, and Pro
- available to Student, Board Exam, Professional, and Parent profiles
- hidden in Teacher mode
- private authenticated Note Detail only

Teacher stays scoped to Quiz Preview and Export. Memorization is not shown on teacher note detail, public note pages, public library pages, or shared quiz links.

**Entry point:** the Key Concepts tab, beside Flashcards. This placement is deliberate: Memorization is a non-engine review surface, not a quiz mode.

## Core Flow

Study Pack-ready private note
-> Key Concepts tab
-> Memorization
-> one due concept card
-> reveal definition
-> self-grade with Again / Hard / Good / Easy
-> next due card
-> caught-up state when nothing is due

There is no timer, score, quiz result screen, session history row, or quiz-session discriminator.

## Data Source And Eligibility

Memorization uses the same concept-matching logic as Flashcards:

- `keyConcepts: string[]` supplies possible card fronts.
- `quiz[].concept` is matched to each key concept using normalized, bidirectional-substring fuzzy matching.
- `quiz[].explanation` supplies the answer side.

Unlike Flashcards, Memorization only schedules concepts with a real matched quiz explanation.

If a key concept has no matched explanation, it is excluded from Memorization entirely. This avoids asking a student to self-grade a card with no answer.

## Scheduling State

Memorization state lives in `memorization_cards`, keyed by:

- `user_id`
- `study_pack_id`
- normalized `concept`

Eligible concepts with no row are treated as new cards due immediately. Grading upserts the row instead of blindly inserting.

Stored schedule fields:

- `intervalDays`
- `easeFactor`
- `repetitions`
- `dueAt`
- `lastReviewedAt`
- `lastGrade`

## Scheduling Algorithm

New card baseline:

- `repetitions = 0`
- `intervalDays = 0`
- `easeFactor = 2.5`
- `dueAt = now`

Grades:

- `AGAIN`: `repetitions = 0`, `intervalDays = 0`, `easeFactor = max(1.3, easeFactor - 0.2)`, `dueAt = now`
- `HARD`: `repetitions += 1`, `intervalDays = max(1, round(previousIntervalDays * 1.2))`, `easeFactor = max(1.3, easeFactor - 0.15)`
- `GOOD`: `repetitions += 1`; first successful repetition schedules `1` day, later successes use `round(previousIntervalDays * easeFactor)`
- `EASY`: `repetitions += 1`; first successful repetition schedules `4` days, later successes use `round(previousIntervalDays * easeFactor * 1.3)`, then `easeFactor += 0.15`

## ConceptHealth And Readiness Firewall

Memorization never writes `ConceptHealth`.

It also must never be read by `ProgressReportService`, plan readiness, note readiness, My Progress, or `Overall Readiness`.

SRS recall is its own review schedule, not a mastery signal. Keeping `memorization_cards` firewalled from readiness preserves the assessment-only mastery boundary.

## AI / LLM Boundary

Memorization never triggers an AI or LLM call.

If no quiz explanation matches a key concept, that concept is excluded. The feature must not generate missing definitions, repair old Study Packs, or call a backend generation endpoint.

## Product Boundary

Memorization is not a quiz mode and is not routed through the Quiz Session Engine.

It must not use `QuickReviewSessionEntity`, add a `quizSession` discriminator, create a quiz-session row, appear in quiz-session history, or count toward quiz performance.
