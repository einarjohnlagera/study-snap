# Ask Companion

## Goal

Ask Companion gives PLUS and PRO learners quick answers about how to use an owned Review Set without searching through every static Companion section. Answers are grounded only in the collection's curator-authored Companion content; the feature does not use learner performance, outside knowledge, or adaptive/personalized guidance.

The Ask Companion chat is hosted only on authenticated collection detail pages. Adaptive Practice, Challenge Quiz, and Quick Review result screens may link to that panel after a learner misses the same concept consecutively; no result screen embeds the chat or invokes its LLM endpoints. Long Exam, Board Exam, Interview Practice, and all in-session quiz/exam screens remain excluded.

## Eligibility And Access

A collection is eligible only when all of the following are true:

- the requester owns the collection
- the collection is top-level (`parentCollectionId == null`)
- its `companion` has at least one renderable value across Overview, Study Strategy, Common Mistakes, Resources, FAQ question/answer text, or Mentor Tip title/body text
- the requester's resolved plan is PLUS or PRO (`Feature.ASK_COMPANION`)
- the requester has verified their email

The frontend uses the same renderable-content test as `CompanionDisplayCard`, so the static guide and Ask Companion entry point appear or disappear together. A direct API call for an owned collection without renderable content returns `COMPANION_NOT_AVAILABLE`; a missing or non-owned collection returns `COLLECTION_NOT_FOUND` / `404` before revealing whether Companion content exists.

FREE users see a context-specific upgrade prompt built through `getUpgradeCtas(currentPlan, "ask-companion")`. PLUS and PRO receive the same runtime capability and the same quota.

## Twice-Missed Concept Entry Point

`concept_health.incorrect_streak` stores consecutive misses per user, Study Pack, and normalized concept:

- recording an incorrect concept increments the streak
- recording a correct concept resets the streak to `0`
- a concept qualifies for the result CTA when the updated streak is at least `2`
- a miss-correct-miss sequence ends at streak `1` and does not qualify

Adaptive Practice, Challenge Quiz, and Quick Review completion responses return the qualifying concepts from that completion as `twiceMissedConcepts`. Long Exam, Board Exam, and Interview Practice do not expose this field as an Ask Companion entry point and render no CTA.

Each scoped result page reuses its existing `getMe()` → `primaryCollectionId` → `getCollectionGoal()` resolution and already-fetched Companion content:

- FREE shows the same `getUpgradeCtas(currentPlan, "ask-companion")` nudge as the collection-detail panel, even when no eligible Primary Review Set is resolved
- PLUS/PRO with a Primary Review Set whose Companion has renderable content see `Ask Companion about this`
- PLUS/PRO without an eligible Primary Review Set render nothing

The paid CTA navigates to `/collections/{id}` with a pre-filled draft such as `Can you explain {concept} a different way?`. The learner must press send. Loading collection detail may resume/read an active conversation, but navigation alone never starts a session, calls the LLM, or consumes monthly quota.

## Data Model

Ask Companion does not reuse `QuickReviewSessionEntity` or `QuizSessionStateUtils`.

`ask_companion_sessions` stores:

- `id`
- `collection_id`
- `user_id`
- `status` (`ACTIVE` or `ENDED`)
- `turn_count` (`0` through `6`)
- `turns` JSONB array containing persisted `question`, `answer`, and `createdAt` values
- `created_at`, `updated_at`, and nullable `ended_at`

A partial unique index permits only one `ACTIVE` session for each user/collection pair. Collection and user foreign keys cascade on deletion. The service also locks the owned collection during start/resume, making repeated or concurrent starts return the existing active session instead of double-counting quota.

`user_usage.ask_companion_used_this_month` stores the rolling billing-period session count. Ask Companion uses the existing billing-period rows, so the scheduled usage reset job creates/ensures a zeroed row for the new period in the same way as the other monthly counters.

## Quota, Turn Cap, And Rate Limit

- FREE: `0` sessions/month (feature-gated before quota handling)
- PLUS: `20` sessions/month
- PRO: `20` sessions/month
- one session allows at most `6` successful question/answer turns
- quota increments once after a new session row is successfully created, never on resume and never per turn
- an LLM failure does not persist a turn or increment `turn_count`
- the sixth successful turn persists and marks the session `ENDED`
- a seventh request against that ended session returns `ASK_COMPANION_TURN_LIMIT_REACHED`
- the next collection-level question starts a new session and consumes another monthly session
- every LLM call passes through `AiRateLimitService` with the `ask-companion` scope

Quota check, session creation, and quota increment run in one transaction under a user lock. LLM success, turn persistence, turn-count increment, and the possible `ACTIVE` → `ENDED` transition run in one transaction under a session lock.

Monthly exhaustion returns `ASK_COMPANION_QUOTA_EXHAUSTED`. Because PLUS and PRO both have 20 sessions, the UI uses plain reset-period guidance and does not imply that upgrading to PRO would create more room. Turn-cap messaging is plan-neutral and separate from monthly exhaustion.

## Grounding And Model

Ask Companion uses the CRITIQUE model tier (`LLM_MODEL_CRITIQUE`, default `gpt-4.1-mini`). Its system/developer prompt pair is loaded from:

- `prompts/study-pack-v1/ask-companion-system.txt`
- `prompts/study-pack-v1/ask-companion-developer.txt`

The developer prompt supplies only the target collection's serialized `CompanionContent`. Persisted conversation history may resolve follow-up references but is explicitly not an additional factual source. The system prompt instructs the model to refuse unsupported or unrelated questions by saying the authored Companion does not cover them, rather than using outside knowledge or fabricating an answer. Curator-authored content is treated as untrusted reference text, not executable prompt instructions.

## Endpoints

All endpoints resolve the caller from the authenticated principal.

- `POST /collections/{collectionId}/ask-companion/sessions` — start a new session or resume the existing `ACTIVE` session; returns quota and turn counters
- `GET /collections/{collectionId}/ask-companion/sessions/active` — return the caller's active session with full persisted turns, or `204` when none exists
- `POST /ask-companion/sessions/{sessionId}/messages` — ask one question on an owned active session and return the updated conversation

The collection-detail panel calls the active-session endpoint on load. A transient load failure produces a retry state instead of assuming there is no conversation. A send failure preserves the typed question for retry.

## Analytics

Backend server-truth events are:

- `ASK_COMPANION_STARTED`
- `ASK_COMPANION_MESSAGE_SENT`
- `ASK_COMPANION_QUOTA_EXHAUSTED`

Analytics publication remains non-blocking and follows the shared after-commit persistence path.
