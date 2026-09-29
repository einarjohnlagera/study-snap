# quiz-session.md - NoteLib Feature Context

## Goal

Quiz sessions persist progress separately from generated Study Pack content so users can leave and resume review safely.

Long Exam and Board Exam generation both use the shared numeric-answer/explanation internal-consistency
gate before a generated `QuizItem` enters a session or pool. An inconsistent eligible MCQ is retried
once as a single question and omitted if the replacement is still inconsistent. Session response counts
come from the resulting quiz array, so a permitted omission is represented by the actual question count.

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

## Anchor Contract

Every `quick_review_sessions` row has exactly one usable anchor shape:

- `study_pack_id` and `note_id` are both non-null for Quick Review, Challenge Quiz, Long Exam,
  Board Exam, Interview Practice and note-scoped Adaptive Practice; or
- `source_collection_id` is non-null for plan-scoped Adaptive Practice.

`chk_quick_review_sessions_anchor` enforces that an **active** row cannot be anchorless, while entity
lifecycle validation also rejects partial or mixed shapes with `INVALID_QUIZ_SESSION_ANCHOR`.
**⚠️ `QuickReviewSessionEntity.validateAnchor()` is the SINGLE application-side authority for that
rule** — it runs on `@PrePersist`/`@PreUpdate` and is called directly wherever a service must reject
an anchor before persisting. Do not reintroduce a second service-local copy: `v0.113.0` shipped one
that was strictly weaker than the entity's (it accepted a partial pack/note pair whenever a collection
anchor was present), and `v0.113.1` deleted it rather than keep two rules in sync.
Active-session exclusivity is enforced by three partial unique indexes keyed by user, mode and
respectively pack, note or collection; each predicate explicitly excludes a null key.

A **terminal** row (`COMPLETED` or `FORFEITED`) may be anchorless, and that is deliberate: the
collection FK is **`ON DELETE SET NULL`, not cascade**, so deleting a Study Plan orphans the sessions
run against it rather than destroying the learner's history. Those sessions stay reachable through
`session_state.sourceNoteRefs`, which credits every note the session sampled. Account-deletion cleanup
does not depend on this — `user_id` has cascaded since `V4`. Non-terminal plan-scoped sessions are
cleared by `NoteCollectionService.delete` before the plan is removed, because an anchorless active
session would be unreachable and the `SET NULL` would otherwise violate the constraint.

## Dashboard Resume Metadata

Dashboard resume recommendations are note-addressed for pack/note sessions and session-addressed for
collection-anchored Adaptive Practice.

The backend should join:

- quiz session
- study pack
- note

Required resume metadata:

- `noteId` for pack/note sessions, or `sessionId` for collection sessions
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

Long Exam sessions stay anchored to the caller-supplied primary `studyPackId`. A plan-sourced launch resolves
the whole verified ready-Study-Pack pool, then stores its deterministic representative sample in
`sessionState.sourceNoteRefs`; the primary is force-included at index 0. Manual launches retain their
same-subject selected-source path. This is deliberately not a session-anchoring migration.

Each entry contains:

- `studyPackId`
- `noteId`
- `noteTitle`
- `questionCount`

This keeps multi-source Long Exam generation inside the shared quiz-session lifecycle without adding a new persistence aggregate.

## Item Source Provenance

Each serialized `QuizItem` may carry `sourceStudyPackId`. Multi-source Challenge Quiz, Board Exam, and Long Exam
stamp it at the per-source generation seam, after deduplication and before merge. It travels on the item because
the assembled quiz may be shuffled after merging; no parallel index-keyed provenance array is valid.

The key is optional for sessions persisted before v0.104.0. A missing or malformed value deserializes as `null`:
Challenge Quiz completes it using the primary Study Pack fallback, while Long Exam uses its historical source-list
fallback. This keeps an in-flight pre-release assessment completable without inventing provenance it did not store.

### Long Exam generation recovery

Age-based recovery may move a `LONG_EXAM` session from `GENERATING` to `FAILED` when its immutable `created_at` is older than the configured Long Exam bound (default `30` minutes). `FAILED` remains observable but is not active, so the existing start flow creates a fresh session on the learner's next attempt instead of handing back the stale row. The frontend already treats this state as recoverable, stops generation polling, explains that the learner can try again, and returns to setup rather than showing an indefinite spinner.

Long Exam progress additionally accepts `selectedIdentificationAnswer`. A blank value clears the saved answer;
on completion it is scored as incorrect rather than causing a submission failure. Identification uses the same
generation-time `acceptableAnswers` and normalized exact-match grading as Challenge Quiz.

Long Exam session responses are feedback-free at the wire boundary as well as in the UI. Start, active, get,
progress, pause, and resume responses never carry `correctIndex`, `correctIndices`, `explanation`,
`workingSolution`, `acceptableAnswers`, or `acceptableAnswerGroups` for any question at any point in the
session, including questions the learner has already answered.

The recovery query is intentionally `LONG_EXAM`-only. Challenge Quiz needs its mode-owned stale-session path to release question-bank claims; Adaptive Practice and the Interview Practice sub-mode are also excluded. Recovery never generates replacement questions itself.

## Interview Practice feedback boundary

Interview Practice start and resume responses never carry the answer key in `question`, and answer responses
never carry it in `nextQuestion`. The natural-language critique is the reveal for the question just answered.
Once that critique has been served and stored, the question cannot be answered differently; an identical retry
is idempotent and returns the stored critique without another LLM call or database write. The lock is keyed on
the stored critique, so a failed critique attempt does not prevent the learner from retrying the question.

## Adaptive Practice answer and reveal boundary

Adaptive Practice session responses carry the full answer key only for question indexes already answered
through `POST /adaptive-practice/sessions/{sessionId}/answer`. Unanswered questions redact `correctIndex`,
`correctIndices`, `explanation`, `workingSolution`, and `acceptableAnswers`, and carry no accepted-answer
content in `acceptableAnswerGroups`. The same response includes the server-recorded `selectedChoices` and
`selectedMultiChoices`, allowing resume to restore the learner's selections, advance to the first unanswered
question, and keep completed items revealed.

The answer endpoint locks the session row and records one immutable selection per question index. Repeating the
same selection is idempotent and returns the stored question reveal without a write; changing it returns HTTP
409. MATCHING blocks use this contract once per item, while MULTI_SELECT choices stay locally editable until the
learner selects Check Answer. `/answer` accepts only choice indices (single or multi); this is safe only because
`OpenAiLlmStudyPackService`'s schema-name gate keeps `note_lib_adaptive_quiz` generation from ever emitting
IDENTIFICATION or ENUMERATION questionFormats. Widening that gate for Adaptive Practice would produce items this
endpoint cannot answer, permanently redacted with no code path to reveal them.

Completion derives its stored score and all `ConceptHealth` inputs from these server-recorded selections. Legacy client score and selection fields remain accepted during deployment overlap
but do not override server state. The persisted selection is the reveal marker because Adaptive feedback is a
deterministic lookup from the immutable stored quiz, not a separately generated critique. The full quiz in
`session_state.quiz` is never redacted or rewritten, and `correctConceptNames` is consulted only for a legacy
session whose stored quiz itself is empty. A session with no server-recorded selections at all (an old
frontend that never called `/answer`) is not scored as merely uncredited: every question in it is treated as
an active miss, and `recordIncorrectAnswers` is called for every concept in the quiz.

## Quick Review answer and reveal boundary

Quick Review start and in-progress responses serve the Study Pack's fixed quiz with answer fields redacted for
every unanswered index. `POST /quick-review/{sessionId}/answer` records a single- or multi-choice selection under
a row lock and returns the full stored `QuizItem` for that index. An identical selection in the same attempt is
idempotent; a different selection returns HTTP 409. The lock is bucketed by `retryCount` (`0` for INITIAL, `1`
for RETRY), while cumulative `selectedChoices` and `selectedMultiChoices` maps always contain the latest accepted
answer used by completion, `ConceptHealth`, and verified mastery. The answer request advances the durable retry
count itself, so retry remains usable even if the transition's best-effort progress request was lost.

Start and resume reveal indexes found in the cumulative maps and leave all others redacted. They also return the
current attempt's selections and navigation state so a refresh restores position and feedback. MATCHING waits
for the whole local group, calls `/answer` once per item, retries only failed items, and reveals the group after
all calls succeed. MULTI_SELECT checkboxes remain local until explicit submission. `/progress` owns only
`retryQuestionIndexes` and `activeQuestionIndexes`; it cannot overwrite answer or lock maps.

The same Note-anchored start and resume routes serve owners and authorized share recipients. Authorization runs
before the unscoped Note metadata read, and mastery resolves for the caller. If the Note's
`generationEnqueuedAt` is newer than the session's creation time, start forfeits the stale row and creates a
fresh session, resume reports no active session, and `/answer` tells the client to restart. The quiz itself is
never copied into session state; its full answer key remains only on the persisted Study Pack.

## Board Exam Multi-source State

Board Exam sessions continue to use the existing `CHALLENGE` session row with `sessionState.mode = "board_exam"`. When a Pro user adds same-subject notes, the session stays anchored to the primary `studyPackId` and stores source attribution in `sessionState.sourceNoteRefs`.

Each entry contains:

- `studyPackId`
- `noteId`
- `noteTitle`
- `questionCount`

Multi-note Board Exam does not introduce a new mode or quota category. On the legacy manual path question count scales with source count (`min(12 × sourceCount, 30)`): single-note stays at 12, two-note generates 24, three-note caps at 30; a Review Set Board Exam is a flat `boardExamTargetQuestionCount` (30). **Quota is deducted PER SESSION, not per source note** — one `boardExamUsed` unit plus one shared quiz-generation unit, whatever the source count. Multi-note sessions skip the single-note pre-generated pool path and use live Board Exam generation per source. Board Exam sessions now surface on every participating note's Recent Sessions list (not only the primary note), and `lastSessionCompletedAt` is updated for all source notes.

For a single-note Board Exam (and Long Exam), the per-user pool is usage-driven: the first real PRO session may live-generate while it schedules that learner's pool, and later matching-learner-level sessions can sample it. This path is independent of the disabled-by-default eager Study-Pack-generation prewarm flag; ordinary Study Pack generation still does not create pools while that flag is off.
