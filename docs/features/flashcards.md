# flashcards.md - NoteLib Feature Context

## Goal

Flashcards is a lightweight, non-scored review surface for a Study Pack-ready private note.

It should feel:

- fast
- low-pressure
- focused on recall
- separate from quiz or exam performance

## Availability

- available on Free, Plus, and Pro
- available to Student, Board Exam, Professional, and Parent profiles
- hidden in Teacher mode
- private authenticated Note Detail only

Teacher stays scoped to Quiz Preview and Export. Flashcards is not shown on teacher note detail, public note pages, public library pages, or shared quiz links.

**Entry point:** the Key Concepts tab, not the quiz-mode action row (Start Quick Review / Challenge Quiz / Adaptive Practice). This is deliberate, not just a layout choice — per `EXAM_MODES.md`'s "Non-Engine Review Surfaces" classification, Flashcards is not a quiz mode, so it does not belong in the quiz-mode CTA row. It also keeps that row from growing an unbounded number of buttons as review surfaces are added.

## Core flow

Study Pack-ready private note
-> Flashcards
-> concept front
-> flip to definition
-> previous / next through the deck
-> back to Note Detail

There is no start, submit, score, result screen, timer, or session history.

## Data source

Flashcards reuses data already returned by the existing note-detail read path:

- `keyConcepts: string[]` supplies the card fronts.
- `quiz[].concept` is matched to each key concept using a normalized, bidirectional-substring fuzzy match (exact match first, then either string containing the other, gated by a minimum length to avoid short-token false positives). Exact-only matching measured ~18% average coverage across generated Study Packs; fuzzy matching brings that to ~56% — the practical ceiling given the next constraint.
- `quiz[].explanation` supplies the card back when a matching quiz item exists.

**Known, permanent limitation:** `keyConcepts` (5–10 items) and `quiz` (a fixed, smaller question count) are independently generated lists — a Study Pack will always have more key concepts than quiz questions, so a meaningful share of concepts can never have a matching explanation, regardless of match quality. This is not a bug to keep chasing; it is a structural consequence of reusing quiz data instead of generating real per-concept definitions (which would require a Study Pack schema/prompt change — deliberately out of scope, see below).

If a key concept has no matching quiz explanation, the card still renders and shows:

- `No definition yet for this concept.`

Flashcards must never request new generated content to fill that gap. Closing the remaining gap would require adding a real per-concept definition field to Study Pack generation — a schema/prompt change with real token cost, and existing packs would only benefit after regeneration. That is a deliberate non-goal for this surface as currently scoped.

## Generation states

Flashcards follows the same category of guard states as the private Note Detail Key Concepts tab:

- `DRAFT`: no key concepts yet; generate a Study Pack first.
- `GENERATING`: key concepts are being generated.
- `FAILED`: generation did not complete; retry generation from Note Detail.
- ready Study Pack with no key concepts: explicit empty state, not a broken deck.

## ConceptHealth

Flashcards does not record to `ConceptHealth`.

Flipping a card, moving between cards, or returning to Note Detail must not update `lastCorrectAt`, `lastIncorrectAt`, mastery, due-state, struggling state, note readiness, plan readiness, or `Overall Readiness`.

Flashcards also does not need to read `ConceptHealth`. It is a self-review surface over existing Study Pack content, not a progress engine.

## AI / LLM boundary

Flashcards never triggers an AI or LLM call.

Missing definitions use the per-card fallback state instead of generation, regeneration, background repair, or a new endpoint.

## Product boundary

Flashcards is not a quiz mode and is not routed through the Quiz Session Engine.

It must not use `QuickReviewSessionEntity`, add a `quizSession` discriminator, create a session row, or appear in quiz-session history. Real spaced-repetition scheduling belongs to Memorization, which is a later feature with its own separate state model.
