# study-library.md — Study Snap Feature Context

This file extracts and consolidates all Study Library-related context from the legacy docs.

## Goal

The Study Library turns Study Snap from a one-shot generator into a reusable study workspace.

Core idea:
- users generate Study Packs
- saved Study Packs can be revisited later
- the library becomes a long-term learning workspace

## MVP behavior

The dashboard is primarily for authenticated users.

It should support:
- listing saved Study Packs
- opening a saved Study Pack
- deleting a saved Study Pack

Recommended list metadata:
- id
- title
- summary preview
- createdAt
- quiz question count
- tags when available

### Study consistency card (UI guidance)

The dashboard includes a lightweight Study Consistency card to encourage repeated study behavior.

Purpose:
- keep the dashboard action-oriented even with few saved Study Packs
- prompt users to continue reviewing or create a new Study Pack
- reinforce a regular study habit without pressure

Current behavior:
- motivational message and clear actions (continue studying, create new Study Pack)
- if no Study Packs exist, guidance focuses on creating the first Study Pack
- no streak numbers and no fake activity data

Future direction:
- real streak/activity tracking can be layered later when backend activity signals are available

### Dashboard CTA hierarchy refinement

To keep the Study Library focused and intentional:
- the hero section owns the main primary action (`New Study Pack`)
- continue studying appears only when at least one Study Pack exists
- the study consistency card stays supportive and motivational (not a duplicate CTA area)
- empty state owns the `Create your first Study Pack` action for new users

The dashboard should avoid repeating multiple equivalent primary actions that all point to the same route.

### Smart Continue Studying

The dashboard includes a Smart Continue Studying recommendation so the spotlight suggests one high-value next Study Pack instead of only showing the latest item.

Purpose:
- keep the dashboard learning-focused and personalized
- guide users toward the most useful immediate review action
- keep recommendation behavior deterministic and explainable

Endpoint:
- `GET /api/dashboard/continue-studying`

Priority logic (v1):
- priority 1: in-progress Quick Review session (`RESUME_REVIEW`)
- priority 2: weakest recently reviewed Study Pack, using each Study Pack's latest completed Quick Review score below `100%`
  - compare by `score_percentage ASC`
  - tie-break by `completed_at DESC`
- priority 3: otherwise most recently opened Study Pack
- priority 4: otherwise most recently created Study Pack
- priority 5: if no Study Packs exist, return no recommendation

Recommendation reasons:
- `RESUME_REVIEW`: user has an unfinished Quick Review session; spotlight should prioritize resuming it
- `LOW_SCORE_RECENT`: user recently reviewed this Study Pack and scored below `100%`; spotlight should encourage score improvement
- `RECENTLY_OPENED`: user recently opened this Study Pack; spotlight should encourage continuing review
- `RECENTLY_CREATED`: no stronger activity signal exists; spotlight should encourage starting review

Response shape direction:
- `studyPackId`
- `title`
- `summaryPreview`
- `reason`
- `lastScorePercentage` (nullable)
- `lastReviewedAt` (nullable)
- `lastOpenedAt` (nullable)
- `createdAt` (nullable)
- `currentQuestionIndex` (nullable, populated for `RESUME_REVIEW`)
- `totalQuestions` (nullable, populated for `RESUME_REVIEW`)

Scope note:
- Smart Continue Studying now supports unfinished-session priority via `RESUME_REVIEW`
- dashboard resume recommendation is complementary to Quick Review session resume endpoints

Automated test coverage:
- backend service-level tests validate Smart Continue Studying recommendation priority and ranking:
  - `RESUME_REVIEW` priority over all other reasons
  - weakest latest-score selection and tie-break by most recent completion
  - fallbacks to `RECENTLY_OPENED`, then `RECENTLY_CREATED`, then empty recommendation
  - latest completed session per Study Pack is used (not historical best score)

Smart Continue Studying card messaging (score-aware):
- if `lastScorePercentage` exists and is below `100`:
  - title: `Continue studying`
  - message: encourages improving score with latest percentage
  - CTA: `Continue Review`
- if `lastScorePercentage` exists and equals `100`:
  - title: `Nice work on this pack`
  - message: reinforces mastery and encourages occasional practice
  - CTA: `Practice Again`
- if `lastScorePercentage` is null (no completed Quick Review yet):
  - title: `Start studying`
  - message: encourages first Quick Review
  - CTA: `Start Review`

CTA navigation:
- Smart Continue Studying action routes directly to Quick Review:
  - `/study-packs/{id}/quick-review`

Smart Continue Studying card hierarchy:
- motivational feedback appears in a distinct visual block (badge/label + message) to improve scanability
- Study Pack summary appears in a separate secondary section:
  - label: `About this Study Pack`
- metadata is formatted as compact secondary lines (for example `Last reviewed · ...`)
- layout and behavior remain consistent with existing dashboard patterns; this is a presentation-only improvement

## User-account dependency

The Study Library works better after user accounts exist because:
- ownership is explicit
- access control is simpler
- free/premium history becomes easier
- future family or shared access can be layered later

## Future directions

Future versions may support:
- rename
- search
- filters
- folders / collections
- reviewed status
- richer dashboard organization

## Tags

Tags may be used for lightweight organization.

Purpose:
- subject/topic grouping
- dashboard filtering
- future search
- future analytics

Initial recommendation:
- store tags as a simple array field on the Study Pack record

Possible sources for tags:
- generated title
- detected topic
- subject selected by user
- manual editing later

## API support needed

Required backend support:
- list my Study Packs
- fetch Study Pack by id
- delete Study Pack

### Study Pack detail page

The Study Library now includes a dedicated Study Pack detail page for authenticated users.

Purpose:
- make Open/Continue actions meaningful
- support focused studying from saved Study Packs
- reinforce Study Snap as a reusable study workspace

Frontend route:
- `/study-packs/[id]`

API detail endpoint:
- `GET /api/study-packs/{id}`

Dashboard integration:
- Continue Studying and Study Pack card Open actions now navigate to the detail route

Response model separation:
- list endpoint stays lightweight for dashboard usage (`id`, `title`, `summaryPreview`, `quizCount`, `createdAt`, `tags`)
- detail endpoint returns full study content (`title`, `summary`, `keyConcepts`, `quiz`, `createdAt`, `tags`)

Quiz rendering consistency:
- Study Pack detail quiz cards should match Study page quiz card behavior
- correct answer choice is highlighted in green with the same "Correct answer" treatment
- shared quiz rendering components should be preferred to avoid UI drift

### Quick Review Mode

The Study Library includes a focused Quick Review Mode for saved Study Packs.

Route:
- `/study-packs/[id]/quick-review`

Purpose:
- give users a lightweight way to study quiz content one question at a time
- make Study Snap feel like an active study workflow, not only a generator and library

Behavior (v1):
- show one question at a time
- reveal correct answer and explanation immediately after selection
- highlight the correct answer in green
- show progress (`Question X of Y`)
- show final results with score, retry, and return-to-detail actions

Retry reinforcement behavior:
- after the initial pass, missed questions are retried once in the same Quick Review flow
- retry round includes only the originally missed questions
- if no questions were missed, Quick Review goes directly to final results
- retry does not increase `total_questions`; final score remains based on the original quiz size
- no third round is created; retry is limited to one immediate reinforcement round

Retry prompt UX:
- shows progress summary before retry starts (`score`, `percentage`, and missed-question count)
- uses encouraging guidance (study-supportive tone) instead of repetitive warning copy
- action labels are explicit and learning-oriented:
`Retry Incorrect Questions` and `Return to Study Pack`

Results screen UX:
- shows final score as `correct / total` plus percentage
- includes lightweight visual score indicator (simple progress bar)
- includes motivational feedback by score band:
  - `100%`: mastery reinforcement
  - `80-99%`: near-mastery encouragement
  - `50-79%`: reinforcement-oriented nudge
  - `<50%`: supportive retry guidance
- shows session-to-session context when available:
`Previous Attempt` and `Best Score`
- if the latest score beats the previous attempt, the UI highlights improvement

Activity tracking:
- start event: `STARTED_QUICK_REVIEW`
- completion event: `COMPLETED_QUICK_REVIEW`
- tracking failures should not block the review experience

Quick Review session persistence:
- Quick Review session records are stored separately from Study Pack content in `quick_review_sessions`
- Study Packs remain the source of static quiz content
- Quick Review sessions store user performance history per attempt
- one retry round (when needed) is still part of the same Quick Review session record

Quick Review resume lifecycle:
- session statuses:
  - `IN_PROGRESS`: user can leave and later resume from saved progress
  - `COMPLETED`: attempt is finalized and appears in session history
- one `IN_PROGRESS` session is allowed per user and Study Pack
- starting Quick Review reuses an existing `IN_PROGRESS` session for that user + Study Pack
- a new session is created only when no `IN_PROGRESS` session exists
- completing Quick Review updates the same session to `COMPLETED` (does not create a new session)
- resume restores practical progress state (current question index, current round, and retry question context)
- resuming a session is different from starting a new attempt:
  - resume continues existing progress
  - new attempt starts a fresh session after completion

Stored session fields:
- `id`
- `user_id`
- `study_pack_id`
- `status`
- `current_question_index`
- `current_round`
- `total_questions`
- `correct_answers`
- `score_percentage`
- `retry_count`
- `duration_seconds` (nullable)
- `session_metadata` (nullable, optional context)
- `session_state` (nullable, lightweight progress state for resume)
- `created_at`
- `completed_at`

Session API flow:
- `POST /api/quick-review-sessions/start` reuses in-progress session when available, otherwise creates a new one
- `GET /api/quick-review-sessions/study-packs/{studyPackId}/in-progress` returns current in-progress session (or none)
- `POST /api/quick-review-sessions/{sessionId}/progress` persists lightweight progress during review
- `POST /api/quick-review-sessions/{sessionId}/complete` computes and stores `score_percentage`
- `GET /api/quick-review-sessions/study-packs/{studyPackId}/recent` returns recent completed sessions

Study Pack detail history:
- Study Pack detail page shows a compact "Recent Review Sessions" list (latest attempts)
- each item includes completed date, score (`correct/total`), and percentage

## Roadmap note

The original roadmap placed Study Library after several other phases.
The updated direction places user accounts before a fully authenticated library so ownership is cleaner.

## Legacy preservation note

This context was extracted from:
- `SPEC.md`
- `ARCHITECTURE.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`
