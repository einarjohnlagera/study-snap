# adaptive-practice.md - NoteLib Feature Context

## Goal

Adaptive Practice is the weak-area follow-up quiz mode for a Study Pack-ready note.

**As of `v0.107.0` it also runs at PLAN scope.** A learner can practise weak areas across a whole
Subject Plan or Review Set, not just one note. This is a **capability of Adaptive Practice, not a new
mode and not a sub-mode** — same questions, same scoring, same result screen, more notes — so it
takes no `subMode` value and adds no row to `EXAM_MODES.md`.

### Plan scope, and the rules that constrain it

- **The session is anchored on the collection itself.** Its `study_pack_id` and `note_id` are null and
  `source_collection_id` identifies the Subject Plan or Review Set. The collection start endpoint is
  collection-addressed, while enter/reload uses `GET /adaptive-practice/sessions/{sessionId}` and the
  session-addressed frontend route. A client must never compute a pack anchor from mutable item order.
- **Resume uses one column-first resolver with a JSONB fallback.** New rows write both
  `source_collection_id` and the legacy `session_state.sourceCollectionId` key. The fallback keeps
  in-flight rows created before `V133` reachable; it must remain until none can still be active.
- **Concepts are never merged across packs.** Two packs weak on the same concept string stay **two**
  focus entries, each carrying its source pack. Concept identity is scoped per Study Pack, so merging
  would assert a cross-pack identity the product does not have. The focus structure is keyed by
  `(studyPackId, concept)` — a `Set<String>` would silently collapse it.
- **One quota unit per session**, regardless of scope or pack count, charged **after** successful
  generation. That ordering is what makes a refund path unnecessary: a generation failure never
  charges. **Do not move generation off the transaction** — doing so destroys that guarantee.
- **Both the sampled pack count and the focus-concept list are bounded.** The question-count cap
  bounds only the output; the focus list feeds the prompt and needs its own bound.
- **Three session types still share the `ADAPTIVE` discriminator**, but their anchors no longer
  contend. Note-scoped Adaptive Practice and Interview Practice keep the pack/note indexes;
  plan-scoped Adaptive Practice uses the collection index. A learner may therefore keep a plan
  session and a note session on a source pack active at the same time, and each resumes as itself.
- **Adaptive Practice and Interview Practice never consume each other's sessions.** Both the start
  and the read path skip a session carrying `subMode: "INTERVIEW"` and return an explanatory message
  instead. They do **not** start a new session in that case — the unique index would reject it — and
  they must **never** forfeit the interview session, which is the destructive half of the defect this
  guard closes (`v0.107.0` item 4). The plan-scoped lookup applies the same exclusion.
- **Historical `ConceptHealth` is not backfilled.** Rows written by Interview Practice before
  `v0.107.0` remain over-attributed.

Interview Practice is a Pro-only Professional Profile sub-mode of Adaptive Practice. It keeps the `ADAPTIVE` session discriminator and stores `subMode: "INTERVIEW"` in session state.

It should stay focused on:

- weak concepts from prior performance
- targeted reinforcement
- repeat practice without drifting into unrelated topics

## Current availability

Adaptive Practice is available on all learner plans with a monthly quota per `PLANS.md` (canonical):

- Free: 3 sessions / month
- Plus: 10 sessions / month
- Pro: 30 sessions / month

If the user cannot access it:

- Free users who have exhausted their monthly quota: use the shared upgrade flow with "upgrade for more sessions" framing
- Plus or Pro users who have exhausted the monthly quota: use the dedicated limit-reached state
- If the `adaptivePracticeProOnly` kill switch is enabled, lower plans should use the shared unavailable/upgrade flow without claiming the feature is normally Pro-only

## Generation behavior

- Adaptive Practice is LLM-generated
- page load may recover `GENERATING`, `IN_PROGRESS`, or `FAILED` state
- page load must not automatically trigger a new generation request
- new generation starts only from the visible CTA

## Entry-point attribution

`ADAPTIVE_PRACTICE_STARTED` records where a newly generated session was launched from. This is attribution-only metadata: it does not change routing, generation, quota enforcement, session behavior, or what the learner sees.

Attributed route links use the existing `entry` query parameter convention with these values:

| value | surface |
|---|---|
| `dashboard-today-focus` | Dashboard — Today's Focus |
| `dashboard-focus-areas` | Dashboard — Focus Areas |
| `dashboard-continue` | Dashboard — Continue/Resume spotlight |
| `challenge-quiz-result` | Challenge Quiz result screen |
| `interview-practice-gap` | Interview Practice readiness gap |
| `note-detail` | Note Detail — the Adaptive Practice mode launch |
| `note-detail-due-concepts` | Note Detail — the "N concepts due for review" prompt |
| `collection-detail` | Collection detail page — the plan-scoped *Practice Weak Areas Across This Plan* action |
| `dashboard-plan` | Dashboard — *Practice Across This Plan* on the Weak Concepts card |
| `direct` | everything else (see below) |

**⚠️ The two plan-scoped values were added in `v0.107.0` and were MISSING from this table until the
`v0.109.0` signoff** — while the sentence below claimed every start link was tagged. Both re-armed
`2026-10-13` checkpoints split on this column, so an incomplete table here is not cosmetic.

**⚠️ The collection-page action appears in BOTH the Goal/Review Set view and the leaf Subject Plan
view.** It reached the Goal view only from `v0.107.0` until `v0.109.0`, so a plan with no children had
no plan-scoped CTA at all; `page.test.tsx` now pins the leaf case, because reverting the fix left the
whole suite green.

**Every route link that can start a session is tagged.** `dashboard-continue` is included even though it is normally a *resume* path: if the session it resumes has expired, the page starts a fresh one, and an untagged link would record that as `direct` — **understating Dashboard-originated discovery, which is the exact figure the `2026-09-12` checkpoint reads.**

**`note-detail` and `note-detail-due-concepts` are deliberately separate.** Both live on Note Detail, but the second is an *evidence-driven* prompt ("you have N concepts due") rather than a mode choice, and telling those apart is the point of the instrument.

The Adaptive Practice page forwards a recognized marker only when the learner actually starts a new session. The backend validates the value independently against its own allowlist. **An absent marker records `direct`** — that means genuine direct navigation, such as a typed URL or a bookmark. An unknown or malformed marker **also** records `direct` and is never persisted verbatim. Resuming an existing session does not emit a new start event.

## Active-question rationale

Each targeted question may show a compact `Reviewing: {concept} — {reason}` tag above the question:

- due-only concepts use `due for review`
- concepts missed in the latest completed Quick Review or Challenge Quiz use `missed last time`
- concepts in both groups use `missed last time and due for review`


Selection provenance does not belong to `QuizItem`: the same question model also represents generated and persisted Study Pack content, while this reason is specific to one Adaptive Practice run. If a generated question's concept does not match the selected focus map, its parallel reason is `null` and the UI renders no tag rather than guessing.

## Result screen

Primary CTA:

- after completion, the page fetches `GET /study-packs/{studyPackId}/next-step`
- the shared `<PostSessionNextStep>` component always steps the learner up to Challenge Quiz after Adaptive Practice instead of making Adaptive Practice its own primary next action
- if genuine weak concepts remain, they stay visible as focus areas; the primary action still does not loop back into Adaptive Practice
- genuine weakness includes reviewed-and-decayed concepts plus actual misses from the completed session, and excludes never-reviewed concepts
- the previous `Generate New Set` action remains as fallback when the next-step fetch fails

Secondary actions:

- `Review Answers`
- `← Back to Note`

The result screen should stay focused and should not compete with unrelated actions. The targeted weak areas block remains mode-owned; only the primary next-action slot is replaced by the shared post-session component. The deterministic server-resolved primary is Challenge Quiz whether genuine weakness remains or has cleared.

## Session rules

- sessions are anchored either to one owned pack/note pair or, for plan scope, one owned collection
- generation and resume flow must be idempotent
- active generation uses the shared generation lock
- leaving an active Adaptive Practice session forfeits that session without refunding quota

## ConceptHealth

- on completion, Adaptive Practice records fully-correct concepts to `ConceptHealth.lastCorrectAt`
- on completion, Adaptive Practice records missed concepts to `ConceptHealth.lastIncorrectAt`
- **Resuming a plan-scoped session spends nothing.** The start endpoint returns an existing session
  for the collection before any quota or rate-limit check runs, so a learner at their monthly limit
  can still resume a session they have already paid for, and a resume consumes no rate-limit budget.
  **⚠️ Those gates sit AFTER the resume branch and BEFORE the eligibility load — both boundaries
  matter, and moving them above the resume branch is a defect that shipped once.**
- **There is no collection-addressed in-progress endpoint.** The collection start endpoint remains
  idempotent; after it returns a session id, enter and reload are session-addressed.
- **⚠️ Both writes depend on the CLIENT submitting `selectedChoices` / `selectedMultiChoices` on
  completion.** Adaptive Practice has no progress endpoint, so nothing persists answers into session
  state during a session. If the client omits them the server's per-source breakdown is empty and it
  falls back to attributing everything to the anchor pack with **no misses recorded at all** — which
  is valid only for a single-note session. A collection session requires stamped source provenance
  and fails loudly if a null source key would otherwise reach ConceptHealth.
- **Attribution is bucketed by `(sourceStudyPackId, concept)`**, so two packs weak on the same
  concept string are recorded separately. Note the API: Adaptive Practice uses
  `recordCorrect/IncorrectAnswers`, **not** the `...ForKnownConcepts` variants, so it applies no
  `keyConcepts` intersection — an asymmetry it shares with Board Exam. Bucketing by source is what
  keeps that safe.
- a concept is missed when it appears in the session and is not fully correct (`correctAnswers < totalQuestions`)
- forfeit paths do not record correct or missed ConceptHealth signals
- a later fully-correct session updates `lastCorrectAt` and clears the struggling state derived from a newer `lastIncorrectAt`
