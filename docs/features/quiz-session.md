# quiz-session.md - NoteLib Feature Context

## Goal

Quiz sessions persist progress separately from generated Study Pack content so users can leave and resume review safely.

## Session Modes

Shared session storage supports:

- `QUICK_REVIEW`
- `CHALLENGE`
- `ADAPTIVE`
- `LONG_EXAM`

## Status Lifecycle

Shared session status values:

- `GENERATING` — generated question set is being created and committed to session state
- `FAILED` — generation failed; the caller can recover without losing note or Study Pack data
- `IN_PROGRESS` — session is active and accepting progress updates
- `PAUSED` — Long Exam session is paused and resumable; active-session exclusivity still applies
- `COMPLETED` — session has been submitted and scored
- `FORFEITED` — session was abandoned through an explicit forfeit flow

## Result-screen hierarchy

Quick Review, standard Challenge Quiz, Adaptive Practice, and Board Exam Mode's own result branch preserve their mode-owned score and weak-area outcome content, then group the existing post-session cards by the learner decision they support: `PostSessionNextStep`, `GoalNudgeCard`, and `WeeklyPacingEchoCard` share one "what to do next" visual block; `CompanionResultBridgeCard` and (outside Board Exam Mode) `TwiceMissedAskCompanionCard` share a separate Companion-guidance block. Board Exam Mode's Companion-guidance block only ever contains `CompanionResultBridgeCard` — it has never shown the twice-missed CTA, and this pass does not change that. Each child keeps its own existing condition (`hasCompanionResultBridgeExcerpt`/`getAskableTwiceMissedConcept` exported from their respective components so pages can pre-check without duplicating each card's internal gate) and can render independently inside its group, so a result with no applicable guidance renders no empty block. Answer review and back actions remain after these groups. This is presentation-only: it does not change actions, data fetches, session state, scoring, or analytics. Board Exam Mode's feedback-free *in-session* view (not its result screen) and Long Exam's forfeit-only flow do not use this grouped result treatment.

## Dashboard Resume Metadata

Dashboard resume recommendations must stay note-based even though session data lives on quiz-session rows.

The backend should join:

- quiz session
- study pack
- note

Required resume metadata:

- `noteId`
- `noteTitle`
- `subject`
- optional `courseProgram`
- `resumeType`
- `currentQuestionIndex`
- `totalQuestions`
- `lastReviewedAt`

Rules:

- `resumeType` comes from session mode, not frontend heuristics
- dashboard resume payloads should use one API response
- note metadata should prefer current note values over older generated Study Pack metadata when both exist
- if note metadata is missing, fallback display should still remain usable

## Aggregate Read Safety

`quick_review_sessions.session_state` stores the full quiz payload and is eager JSONB on `QuickReviewSessionEntity`. Dashboard summaries, note-performance summaries, retention checks, and other aggregate/list reads must use repository projections or SQL aggregates that select only scalar columns and, when weak-concept/focus-area logic needs it, `session_metadata`.

Do not load unbounded lists of `QuickReviewSessionEntity` for aggregate screens or scheduled retention jobs. Single-session play/resume paths that genuinely read `sessionState` may still load the entity by id or latest active session, and `QuizSessionStateUtils` remains the owner for `sessionState` reads.

As of v0.38.0, collection practiced counts, collection detail `lastSessionCompletedAt`, and per-note Recent Sessions keep the same response semantics while resolving direct single-note participation from the `note_id` column first. JSONB `sessionState` is read only for bounded Long Exam / Board Exam candidate sets where `sourceNoteRefs` can add extra participating notes.

## Long Exam Multi-source State

Long Exam sessions stay anchored to the primary `studyPackId`. When the user adds same-subject notes, additional source attribution is stored in `sessionState.sourceNoteRefs`.

Each entry contains:

- `studyPackId`
- `noteId`
- `noteTitle`
- `questionCount`

This keeps multi-source Long Exam generation inside the shared quiz-session lifecycle without adding a new persistence aggregate.

### Long Exam generation recovery

Age-based recovery may move a `LONG_EXAM` session from `GENERATING` to `FAILED` when its immutable `created_at` is older than the configured Long Exam bound (default `30` minutes). `FAILED` remains observable but is not active, so the existing start flow creates a fresh session on the learner's next attempt instead of handing back the stale row. The frontend already treats this state as recoverable, stops generation polling, explains that the learner can try again, and returns to setup rather than showing an indefinite spinner.

The recovery query is intentionally `LONG_EXAM`-only. Challenge Quiz needs its mode-owned stale-session path to release question-bank claims; Adaptive Practice and the Interview Practice sub-mode are also excluded. Recovery never generates replacement questions itself.

## Board Exam Multi-source State

Board Exam sessions continue to use the existing `CHALLENGE` session row with `sessionState.mode = "board_exam"`. When a Pro user adds same-subject notes, the session stays anchored to the primary `studyPackId` and stores source attribution in `sessionState.sourceNoteRefs`.

Each entry contains:

- `studyPackId`
- `noteId`
- `noteTitle`
- `questionCount`

Multi-note Board Exam does not introduce a new mode or quota category. Question count scales with source count (`min(12 × sourceCount, 30)`): single-note stays at 12, two-note generates 24, three-note caps at 30. Quota is deducted per source note at session start. Multi-note sessions skip the single-note pre-generated pool path and use live Board Exam generation per source. Board Exam sessions now surface on every participating note's Recent Sessions list (not only the primary note), and `lastSessionCompletedAt` is updated for all source notes.

For a single-note Board Exam (and Long Exam), the per-user pool is usage-driven: the first real PRO session may live-generate while it schedules that learner's pool, and later matching-learner-level sessions can sample it. This path is independent of the disabled-by-default eager Study-Pack-generation prewarm flag; ordinary Study Pack generation still does not create pools while that flag is off.
