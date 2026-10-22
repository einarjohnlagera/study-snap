# challenge-quiz.md - NoteLib Feature Context

> **Mode identity is locked in `docs/product/EXAM_MODES.md`.** Challenge Quiz is *practice with stakes* — flexible, progressive, user-controlled. It is **not** a sit-down exam. Long Exam Mode and Board Exam Mode cover those needs. The progressive generation, `+5 Questions`, early-submit, and inline learner-level adjustment are core to Challenge Quiz's identity and must be preserved.

## Goal

Challenge Quiz is the timed, exam-style quiz mode built from a Study Pack-ready note.

It is separate from Quick Review:

- separate generation flow
- separate session history
- stricter timing and setup
- result screen designed around next-step practice

It is also separate from Board Exam Mode and Long Exam Mode:

- Challenge Quiz is flexible and progressive; Long Exam is fixed and pause-friendly; Board Exam is a strict simulation with reduced UI chrome and no progressive controls
- per-mode identity contracts are in `docs/product/EXAM_MODES.md`

## Entry flow

Student and Board Taker enter through a shared mode-selection screen. Profile type determines which cards appear and which mode is emphasized. Source of truth: `lib/exam-mode-visibility.ts`.

### Profile-aware mode cards (v0.12.0)

**Student:**

- `Challenge Quiz` — Recommended
- `Long Exam Mode` — Coming Soon (card visible; tapping it shows a coming-soon screen with "Long Exam — Coming Soon" disabled; "Choose another mode" returns to mode-selection)
- Escape-hatch line shown: *"Preparing for boards? Switch your profile in Settings to enable Board Exam Mode."*

**Board Taker:**

- `Board Exam Mode` — Recommended (Pro gate shown for Free/Plus users)
- `Challenge Quiz` — listed as alternate

**Teacher:**

- Skips mode-selection entirely; lands directly on the Challenge Quiz setup step — **except** when a live in-progress session is found, in which case the Teacher is routed through mode-selection too, so the Resume/Start Fresh prompt (below) is never skipped (v0.60.1)

**Professional:**

- `Certification Review` — Challenge Quiz engine with professional-facing label
- `Full Practice Exam` — Long Exam engine with professional-facing label
- `Interview Practice` — Adaptive Practice sub-mode tile for Professional users; Free/Plus users can open setup and see the Interview Practice paywall from the Start CTA

Study Plan / Review Set detail resolves on both profile and plan: Free and Plus learners (including `PARENT`) launch Challenge Quiz with `collectionId`, while Pro retains its profile-appropriate premium route. The Challenge Quiz prescreen restricts additional-note selection to quiz-ready notes from that plan; Teacher remains on the DOCX Exam Builder path.

### Long Exam backend (v0.13.0)

- Long Exam Mode backend session support is shipped: `LONG_EXAM` sessions use the shared quiz-session table, generate and commit a fixed question set before the session begins, support `PAUSED` resume state, and return a mastery report on completion
- Frontend activation can wire `Start Long Exam` to the dedicated `/long-exam` endpoints when the UI is ready

## Current plan gating

- Challenge Quiz is available on Free, Plus, and Pro with rolling monthly limits of `20`, `100`, and `200` sessions respectively
- A multi-note Challenge Quiz remains a `CHALLENGE` session, also consuming the shared Quiz allowance, but has a separate internal monthly ceiling: Free `2`, Plus `10`, Pro `200` by default. The counter is enforcement plumbing only: it is not surfaced as a Settings allowance.
- Multi-note source selection is accepted only after the server verifies the claimed plan's ownership and membership (including the primary). Free may use 3 sources including the primary; Plus and Pro use the same `floor(questionCount / 3)` formula as Long Exam, fed a **fixed 18-question multi-note count**, so the cap is a stable **6 sources**. **⚠️ 12 questions / 4 sources was itself rejected as arithmetic leakage (owner, 2026-09-02); ~10 sources is UNREACHABLE because it needs 30 questions, ten past `MAX_CHALLENGE_QUIZ_QUESTIONS` (20), which `+5 More Questions` depends on — do NOT lift that ceiling to chase it.** **⚠️ NOT Long Exam's 6 / 8 / 10** — that comes from 20 / 25 / 30, and applying it here gave sources a single question each. **⚠️ And NOT the score-adaptive 10 / 12 / 15 a single-note quiz uses** — that would make the cap move between sessions on the same plan while the prestart renders it as a stable promise (owner, 2026-09-02). A per-source floor rejects any split below three questions. An invalid, ineligible, or over-cap `additionalStudyPackIds` request is rejected; it is never silently downgraded to a single-note session. A non-plan source still follows the carried same-subject rule.
- Board Exam Mode is Pro-only
- Board Exam Mode consumes the shared quiz-generation monthly budget and also has a dedicated Board Exam hard cap (`10` **sessions** / month; default configurable). One unit per session, whatever the source count.
- Board Exam reserves one shared Challenge Quiz unit and one Board Exam unit at start. A failed asynchronous
  generation reverses both together; this does not change either entitlement.
- Free and Plus users who choose Board Exam Mode can open the setup screen first; the Pro paywall fires from the Begin/Unlock Board Exam Start CTA so the strict exam flow is visible before the upgrade ask
- A Review Set launch does not show a source picker at all: the server samples the whole owned Review Set across its Subject Plans, so there is nothing for the learner to choose and a caller-supplied `additionalStudyPackIds` list is **rejected**, never silently dropped. The legacy manual path (a note with no Review Set behind it) keeps its picker, its source cap and its quota rules unchanged
- **Board Exam's source is the WHOLE Review Set, and a Subject Plan claim resolves UP to its parent.** A
  learner reaches Board Exam from whichever collection page they were on, which is normally a Subject Plan —
  a child. Using the claimed collection directly would ship "assess across the plan you came from", which is
  Long Exam's job, not Board Exam's. **Ownership is re-verified on the parent**: a child of a Review Set you
  do not own is not a route into someone else's curriculum. A childless top-level plan is treated as one
  stratum, exactly as the Goal endpoint does.
- **The eligible pool is deduped by Study Pack, not by note id.** A note belonging to two Subject Plans of the
  same Review Set produced two pool entries carrying the same Study Pack, which let a **single note satisfy
  the two-contributing-sources assembly floor**. First occurrence wins, so a shared note is attributed to its
  earliest stratum and position.
- **A Board Exam ships short above two assembly floors rather than failing outright** — a configured minimum
  of assembled questions (`10`) and of contributing sources (`min(sourceCount, 2)`). A short exam is marked
  `shortExam` with the count it expected. Below either floor the session FAILS and **both meters are
  reversed**. This applies to the legacy single-note path too, which previously failed strictly on any
  shortfall.
- **The warm question pool is never served to a Review Set Board Exam**, mirroring the `v0.105.0` Long Exam
  rule: a sampled multi-source exam must not be served primary-only pooled questions while its session
  records the sampled sources. On the legacy single-note path the pool is used, and **the pool
  itself now excludes the note's own saved quiz** — at generation time, so new pools cannot contain the
  answer key, and again at sampling time, so pools built before that fix cannot serve it either. The
  sampling filter runs BEFORE the have-we-got-enough check, so a leaked question is never a candidate and no
  clean pool row is spent rejecting it.
- Board Exam quota exhaustion remains separate from Pro-only feature gating
- monthly quiz-limit exhaustion is separate from Pro-only feature gating

## Generation and session behavior

### Prepared Challenge sessions (v0.175.0)

Ordinary Challenge Quiz starts with **Prepare Quiz**. A generating session shows “Getting your quiz ready”
with a progress indicator and a Continue reviewing link. The page polls every two seconds and moves to
“Your quiz is ready!” automatically when preparation finishes. Bank-served and redo-missed sessions use
the same ready screen. Questions and the timer appear only after the learner presses **Start Quiz**.
Reloading restores generating, ready, failed, or running sessions without starting the timer.

Every ordinary Challenge transition to a prepared, unstarted quiz publishes one in-app
`CHALLENGE_QUIZ_READY` notification after the session commit. Its `CHALLENGE_QUIZ_READY:<sessionId>`
dedup key makes bank hits, redo, multi-note preparation, and async completion converge on one row.
Board Exam has no readiness notification. A learner who sees the ready screen calls the bodyless
`POST /challenge-quiz/sessions/{sessionId}/ready-seen`; this creates or finds that same row and
marks it read without changing the session, quota, or timer. Notification delivery failures do not
change preparation.

The notification links to `/notes/{noteId}/challenge-quiz?session={sessionId}`. The page first
reads `GET /challenge-quiz/sessions/{sessionId}/status`, then loads that exact session through the
read-only `GET /challenge-quiz/sessions/{sessionId}` when it can resume. Generating, ready, and
started sessions retain their own state even when a newer session exists. Expired or unstarted
forfeited sessions show the expiry screen; failed sessions show preparation failure; completed or
started-then-forfeited sessions return to setup. A not-owned session returns 404 and setup. These
reads never call Start Quiz, forfeit a session, or start the timer.

Failed preparation shows **Try Again** and **Continue Reviewing**. An expired or stale prepared quiz
shows **Prepare New Quiz**, which returns to setup without creating a replacement. A conflicting intent
shows the existing prepared quiz and lets the learner resume it or discard it by resending the original
start request with `discardPreparedSessionId`. Discarding an unstarted quiz uses no monthly quota.

Entry intents are kept when an unstarted preparation already exists:
- **Mode selection** (`?entry=mode-selection`):
  - A failed preparation is ignored, and mode selection shows as if there were no session.
  - A preparation still generating is offered as a resume candidate ("You already have a quiz being
    prepared"), with **Resume** but no **Start Fresh**, because the forfeit endpoint only ends in-progress
    sessions. Choosing another mode goes through the collision choice, which discards it.
  - A ready prepared quiz keeps **Resume** and **Start Fresh**.
- **Redo Missed Questions** (`?entry=redo-missed`):
  - With a ready or generating preparation, the redo request is still sent, and the collision choice
    appears.
  - With a failed preparation, the redo starts normally.
- **A quota rejection at Start Quiz** keeps the learner on the ready screen:
  - The multi-note limit opens the multi-note paywall.
  - The ordinary limit shows the limit page on plans that use it, and otherwise opens the Challenge paywall.

If the preparation poll keeps failing (for example during an outage), the page keeps polling at the same
interval and shows no error. **Continue reviewing** always works, so the learner is never stuck.

`POST /challenge-quiz/sessions/{id}/start-quiz` returns `409 PREPARATION_IN_PROGRESS` while generating,
`409 PREPARATION_FAILED` after a failed preparation, and `409 PREPARED_CHALLENGE_EXPIRED` for an
unstarted session forfeited by expiry or discard. A completed or already-started forfeited session
still returns `400 SESSION_NOT_IN_PROGRESS`; a stale generation stamp also uses that code.

Ordinary Challenge preparation creates a quota-exempt `GENERATING` session. A single-note shortfall is
generated by an after-commit job: it reads inputs in one short transaction, calls the model outside a
transaction, and writes the result in another. A transport failure or timeout gets one retry on the
same model route; invalid output is not retried by the job because the generation service already handles
that case. The bank and Official template are claimed first; a full bank/template hit becomes ready in
the request. Multi-note preparation claims from each source's bank and Official template first, then generates
remaining source shortfalls concurrently in the background. It becomes ready when at least two thirds of
requested questions and two thirds of selected sources contribute; otherwise it fails without a quota charge.
Redo Missed Questions also becomes ready
without starting.

Ready is `IN_PROGRESS` with `preparedNotStarted=true`, `preparedAtEpochSeconds`, and the pack's
`preparedGenerationStamp`; it has a time limit but no timer start. Start and resume responses hide both
the quiz and the quiz inside `sessionState` until `POST /challenge-quiz/sessions/{id}/start-quiz` succeeds.
That action checks the current pack stamp and monthly quota under pack-then-session-then-user row locks, stamps the
timer once, and returns the questions. Repeating it returns the same running attempt. A stale pack forfeits
the prepared session and releases its bank claims.

An unstarted prepared Challenge expires seven days after `preparedAtEpochSeconds` (falling back to
`createdAt` for older rows). Start Quiz checks expiry under its lock. Reopening an expired preparation
forfeits it and returns an expired error without preparing a replacement. The recovery job also sweeps
expired preparations and ordinary Challenge jobs stuck in `GENERATING` for over 20 minutes. Both release
bank claims; a late async result may still save valid questions unclaimed. Board Exam is excluded.

If a new ordinary, Board Exam, or redo-missed request conflicts with an unstarted ordinary Challenge
preparation, the response carries a `collision` with the existing session and both intents. The learner
can resume it or submit its id as `discardPreparedSessionId` on the new request. Discard forfeits the
unstarted preparation and releases claims before proceeding. A wrong id or an already-started quiz is
rejected. A same-intent request resumes. Existing started-session behavior is unchanged.

Preparation, failure, discard, and expiry consume no ordinary Challenge quota. The successful Start Quiz
charges the billing period in which Start happened, including the multi-note meter when applicable.
Redo Missed Questions stays quota-exempt; an already-started quiz remains consumed after forfeiture or
timeout. Board Exam keeps its existing charge-at-creation and timer-at-ready behavior.

Newly generated questions are banked under the captured generation stamp. If a still-current preparation
cannot become ready, valid generated questions are saved unclaimed and session claims are released, so
the next preparation can reuse them. A changed generation stamp prevents stale questions from being saved.

- Challenge Quiz is LLM-generated
- Challenge Quiz questions use the shared numeric-answer/explanation internal-consistency gate before construction; an inconsistent question is retried once and omitted if its replacement is still inconsistent
- start flow must be idempotent
- an existing `GENERATING` or non-expired `IN_PROGRESS` session is reused for the same intent instead of creating duplicates; a different intent colliding with an unstarted ordinary Challenge asks the learner. A fresh preparation request forfeits an expired prepared session and prepares another; `start-quiz` on the expired session rejects it without replacement. Expired started Challenge Quiz and Board Exam sessions keep their existing auto-forfeit behavior, and expired Challenge cleanup releases its bank claims. An expired `+5` rolls its forfeit back with the failed request, leaving the session `IN_PROGRESS` with its claims until a later `startSession` forfeits it. Reuse detection reads the existing session twice — once unlocked to capture its status, once locked — and calls `entityManager.refresh()` on the locked read before comparing: both reads resolve to the same Java object by Hibernate identity within the one transaction, so without the refresh a status a concurrent request committed between the two reads is invisible and the stale session gets incorrectly resumed instead of a fresh one being started.
- when the mode-selection entry (`?entry=mode-selection`) finds a live in-progress Challenge Quiz or Board Exam session, it offers **Resume** or **Start Fresh** rather than silently discarding progress; expired sessions return to a clean prestart and are never resumed into an immediate timeout. The note detail Challenge Quiz card and collection/Review Set premium-exam launch both add this entry marker, so their explicit "start" intent gets the same choice. This prompt is forced onto the mode-selection step even for profiles (Teacher) whose initial step otherwise skips it, so a live session is never silently re-entered without the choice (v0.60.1, collection launch aligned in v0.60.2). Direct/bookmarked URLs, page refresh, and Dashboard's Continue widget still auto-resume a live non-expired session with no prompt by design — that is deliberate "continue where you left off" behavior.
- `+5 Questions` checks expiry before setting a pending marker. The finish transaction appends and extends by 90 seconds per added question only while the session is running, the marker matches, the pack stamp is current, and the timer has not expired. Otherwise it releases this job's claims, banks valid current-stamp generated questions unclaimed, and leaves quiz and timer untouched.
- Board Exam generation uses the shared generation lock; ordinary Challenge preparation allows the learner to continue reviewing and return later
- Standard Challenge mode starts with a score-adaptive question count from the learner's latest completed Quick Review on the same Study Pack: below 50 → 10 questions, 50–79 → 12 questions, and 80 or above → 15 questions; no prior score starts with 12. Redo Missed Questions remains fixed at up to 5 claimed questions.
- Challenge mode's assembled question pool (banked + Official template + freshly generated) is shuffled once at initial session start (and at `startRedoMissedSession`'s assembly), so batches never present in fixed `generatedAt` order. A MATCHING block (2–4 consecutive questions sharing a `questionGroup`) always shuffles as one contiguous unit — never split apart — since the frontend (`lib/quiz.ts`) groups them by scanning for adjacency. **A block is additionally bounded by its source Study Pack (`v0.104.0`)**: the generation prompt tells every call to label its block `group-1`, and multi-note sources are appended back to back, so without that bound one source's trailing block and the next source's leading block would fuse into a single block with two provenances. `+5 Questions` shuffles only the newly-appended batch, never the whole array, to avoid remapping already-recorded index-keyed answers (`selectedChoices` et al. are keyed by array index, not question identity). Board Exam Mode's ordering is unaffected (v0.60.1).
- Each real-LLM Challenge Quiz shortfall records the response model plus input, output, and cached-input token usage on the session. Successful `+5 Questions` calls add to the existing values so the row represents cumulative session usage; sessions fully served from the per-user bank or Official template keep all usage columns null. Missing or malformed usage metadata is ignored without blocking the quiz. Board Exam Mode, Long Exam, and Adaptive Practice do not participate in this telemetry.
- Challenge mode has no user-facing difficulty selector. Its difficulty is fully automatic and comes only from the latest completed Quick Review score on the same Study Pack: below 50 → Easy, 50–79 → Default/Medium, 80 or above → Hard; no prior score also uses Default/Medium.
- Board Exam Mode uses its configured target count after representative Review-Set sampling; source count is
  not an exam-length multiplier.
- Board Exam Mode does not use progressive generation; question count is fixed at session start
- Board Exam generation completes asynchronously through this service after its start transaction commits.
- Board Exam Mode's separate difficulty behavior is unchanged: it does not expose a selector and remains fixed at `DIFFICULTY_MIXED` to preserve exam-simulation framing

### Per-user question bank (v0.58.0)

- Challenge Quiz persists each newly LLM-generated question in an owned, per-user, per-Study-Pack bank. It never shares questions between users or Study Packs.
- Bank rows are unique per `(user, Study Pack, question, learner level)`. A question regenerated after an authored-depth correction can coexist with the preserved row at the previous level instead of colliding with it. Nullable legacy levels use nulls-not-distinct uniqueness, so they retain the same duplicate protection.
- On both session start and `+5 Questions`, Challenge Quiz claims eligible banked questions before calling the LLM, then generates only the shortfall. A banked question must match the note's effective curriculum level (`notes.learner_level` -> reader level -> `COLLEGE`) and is deduplicated against the live session with `QuizDeduplicationUtils`.
- Claims use a pessimistic lock and stay attached to the in-progress session, preventing two concurrent starts or add-more requests from claiming the same banked row. A newly generated question can still duplicate one claimed by another in-progress session of the same user when its bank insert is skipped. Completion records the question's last known correct, incorrect, or unanswered outcome and releases the claim; forfeiture and generation failure also release claims.
- `study_packs.generation_stamp` is non-null from migration onward and advances with summary/key-concept replacement in the same transaction as bank invalidation, on both learner regeneration and admin summary repair. Generation captures it from the same loaded pack row as the summary before the LLM call. New bank rows, including Official template seeds and adopter copies, require that captured stamp. Ordinary claim, Redo Missed count/claim, and the Official template's own source-content read accept only the current stamp or a null legacy stamp. Null legacy rows remain usable until that pack's next regeneration removes them; unrelated metadata edits do not advance the stamp. The owning session's outcome and release read is deliberately **not** stamp-filtered: a session may finish questions it already claimed even after its pack changes. Neither are the three write-side dedup/existence reads (`existsByUserIdAndStudyPackId`, the batched owner-pair form, and the caller's own key-exclusion read used before a template copy): each guards a write against re-seeding or re-copying, not a claim, so it must see a stale-stamped row too — filtering it would make that row invisible to the guard and let a template copy re-serve its `question_key`; the insert would silently skip the duplicate bank row.
- Claim release uses one count-tolerant targeted bulk `UPDATE` in the caller's transaction. It sees a failed start's uncommitted claim and clears it before that transaction commits; a stopped, ended, expired, or failed background `+5` releases only claims from that job, leaving the original session claims attached. The older `REQUIRES_NEW` release came from `c76e5c1d`'s mistaken premise that rollback would leave claims behind, and could wait on a lock held by its own caller. A concurrent regeneration delete simply makes the release update affect zero rows. The marker is cleared on stop or finish; a failed job records a token and failure timestamp.
- Completion's claimed-row read has a `PESSIMISTIC_WRITE` lock. A real two-connection PostgreSQL test confirmed a concurrent bank delete waits until completion commits, then deletes; completion does not throw from a missing-row flush in this path. Outcome recording remains one load/mutate/save pass, with no generation-stamp filter on its owning-session read.
- Bank inserts join the owning transaction (the Challenge session's on start and the background finish transaction for `+5`; `seedTemplateAsync`, which has no session, uses its own transaction) and skip a same-level duplicate (including a NULL-level duplicate) at the insert itself. A skipped question can still be served in that session, but it is not banked or claimed for that session. Its outcome is not recorded in the bank, and Redo Missed Questions will not offer it from that session. The template key pre-read still avoids re-serving known keys; the insert also closes concurrent races. A generated shortfall is deduplicated against the Study Pack's quiz (on start) or the live session's questions (on `+5`), plus the questions just claimed or copied for it, but not against the user's other bank rows, so it can serve a duplicate currently claimed by another in-progress session. Isolating a start write in its own transaction was attempted and reverted because the bank's session foreign keys cannot see an uncommitted session row. Bank read failures and non-duplicate write failures (for example, a foreign-key violation) can still poison the caller's PostgreSQL transaction despite the surrounding catch. Adaptive Practice remains intentionally always-fresh and does not use this bank.
- Ordinary bank claims, Redo Missed claims, and the Redo Missed availability count exclude learner-original statement-pair rows generated before `studysnap.challenge-quiz.statement-pair-legacy-cutoff`. When the cutoff is unset (the deployment default), they exclude every learner-original statement-pair row. A row whose key belongs to that pack's resolved Official template remains eligible, including the Official owner's own rows. Excluded rows remain stored and unchanged; later eligible rows can fill an ordinary claim shortfall. The owner sets the cutoff after v0.175.0 deploys. “Post-cutoff” means the question passed R1's consistency check, **not** that its facts were independently verified. If a template is regenerated, older adopter copies whose keys leave the template's current key set lose the exemption and become excluded.
- When at least three eligible bank entries were last answered `INCORRECT` **after the statement-pair exclusion**, the Challenge result handoff offers **Redo Missed Questions**. It starts a normal `CHALLENGE` session sourced only from those same-effective-curriculum-level claimed questions, with no LLM call and no Challenge quota consumption to *start*. The availability count and the claim query resolve the same effective curriculum level and use the same statement-pair predicate, so the CTA and claim agree on eligibility. The same-route handoff resets the completed result and reliably starts the redo session; if fewer than three entries remain eligible, it returns to a clean prestart with the backend's message. A bank failure or a claim race fails closed rather than starting a partial redo. Completion still records `ConceptHealth` through the ordinary Challenge path; this is an entry point, not a new quiz mode or sub-mode. The frontend's one-shot retrigger guard resets on every start outcome (success or error), so requesting Redo Missed Questions again later in the same page session (e.g. after redoing once, then missing questions again) reliably fires a second time instead of silently no-op'ing (v0.60.1). Redo session matching uses a dedicated provenance marker: a marked prior redo session still resumes. A mismatched unstarted ordinary Challenge session now returns a collision; a started unrelated session retains the older forfeit-and-replace behavior (v0.60.3).
- Completion records outcomes and clears claims for every bank row owned by the session and user, regardless of Study Pack. Discard, expiry, failed preparation, generation recovery, and forfeiture also release claims by session and user in the caller's transaction. A repeated release has no further effect.
- `+5 Questions` on a redo-missed session is not quota-exempt-aware: it falls through to normal LLM generation like any other Challenge session, so the zero-cost guarantee applies only to the session's start, not to any later add-more request. Whether to block `+5 Questions` on redo-missed sessions is an open product question, not yet decided.

### Official template source (v0.60.0)

- A currently `PUBLIC` Study Pack owned by an Official author (`role = ADMIN` **or** the reserved official account's email, excluding the deleted-user sentinel — aligned v0.62.0 with `PublicProfileService.isOfficialAuthor`'s reference form; previously admin-role only) receives one canonical Challenge Quiz template of up to 20 questions after the public-note or Study-Pack write commits. An admin-only idempotent backfill can seed already-published Official content; template generation failures are logged and never affect publishing or Study Pack generation.
- **⚠️ The admin backfill reads the catalog in THREE bounded queries, never two per note (v0.125.0).** It used to load every `PUBLIC` `NoteEntity` — ~1,442 rows, each carrying `content` — plus every matching `StudyPackEntity`, then run `userRepository.findById` AND `existsByUserIdAndStudyPackId` **inside** the loop: ~2,884 queries and two full entity loads for one admin action. It now reads note and Study Pack **projections** (`NoteOwnerVisibilityProjection`, `StudyPackOwnerProjection` — id, owner, visibility/note id only), resolves Official authors once for the distinct owner set via `findAllById`, and batches the bank-existence check into `findOwnerStudyPackPairsByStudyPackIdIn`. **⚠️ WHICH notes are eligible is unchanged** — the shape and Official-author legs are one shared predicate the eager per-note hook also uses, and the bank check is still asked only about notes that already passed both, exactly as the old `||` short-circuit did, so `queued`/`skipped`/`rejected` mean what they meant before. **⚠️ It is deliberately NOT paged** (the response is three counts over the whole catalog, so a page would change what the numbers mean) and the **dispatch order stays `updated_at desc`**, because it decides which packs seed first when the executor queue fills.
- Template seed dispatch (both the eager per-note hook and the admin backfill) runs on the shared bulk LLM fan-out pool (`llmParallelTaskExecutor`), never on the live Study Pack generation pool, so a large backfill can't delay or reject real user-facing generation requests (v0.60.1 fix — it originally shared the live-generation pool and could saturate it). The admin backfill response (`AdminSeedOfficialChallengeQuizTemplatesResponse`) reports `queued`/`skipped`/`rejected` counts; a task rejected due to a full queue is logged and counted as `rejected` rather than silently dropped or thrown as an error.
- When an adopter's ordinary Challenge start or `+5 Questions` claim has a shortfall, the bank resolves exactly one lineage hop through the adopter note's `copiedFromNoteId`. It uses a template only when that immediate parent is still currently `PUBLIC` and Official-owned; it never follows `sourceNoteId` or chases further copy hops.
- Template rows are read-only and are never shared as a learner's live bank rows. Eligible unseen template questions are copied into fresh rows under the learner's own user id and copied Study Pack id, claimed by the live session, tagged with the adopter note's effective curriculum level, and initialized as `UNANSWERED`. This preserves each learner's own outcomes and Redo Missed Questions history.
- The source read intentionally ignores the Official author's bank-row learner level. The copied row uses the adopter note's effective curriculum level so later ordinary claims use the same key. The adopter note already inherits its authored learner level from the Official source through note copying. Any template shortfall still uses the existing LLM path; private/non-Official Study Packs, Adaptive Practice, quotas, and the redo-missed claim path are unchanged.

### AI Generation Spec

- Challenge mode uses `challenge-quiz-*.txt` prompts for flexible practice with stakes.
- Board Exam uses `board-exam-*.txt` prompts for high-stakes licensure / certification simulation framing.
- Do not route Board Exam through `generateChallengeQuiz()`; it must call the dedicated Board Exam generation method while keeping the fixed question count and no-progressive-generation contract.

### Question formats

Challenge Quiz can generate:

- `MCQ`
- `TRUE_FALSE`
- `MULTI_SELECT`
- `MATCHING`
- `IDENTIFICATION`
- `ENUMERATION`

`IDENTIFICATION` is a free-text active-recall format scored through deterministic `acceptableAnswers[]` matching. It is Challenge Quiz-only for now and has no per-submission LLM call. See `docs/features/identification.md`.

`ENUMERATION` is a multi-slot free-text format (2-5 required items) scored all-or-nothing via exhaustive bipartite matching against `acceptableAnswerGroups[]`. Same Challenge Quiz-only scope and no per-submission LLM call. See `docs/features/enumeration.md`.

## Statement-pair answer-key guard and start minimum

Challenge Quiz generation asks the model to judge Statement 1 and Statement 2 separately before choosing an answer. For a stem containing both statements, the server checks that the four choices map uniquely to both, only Statement 1, only Statement 2, and neither, and that the keyed choice matches the verdicts. A missing verdict, ambiguous choices, or a wrong key triggers one correction request; a failed replacement is omitted. The guard applies only to newly generated Challenge questions, including starts, `+5`, and Official template seeds. Existing bank and session questions are not rewritten. Board Exam, Long Exam, Adaptive Practice, teacher quizzes, and Study Pack generation do not use this guard.

A single-note preparation must deliver at least one fewer than its requested 10, 12, or 15 questions after bank, template, and generated questions are deduplicated. A multi-note preparation must deliver at least `ceil(2 × requested / 3)` questions from at least `ceil(2 × positively allocated sources / 3)` sources. These are acceptance floors; every source still pursues its full allocation. Below either floor the session becomes FAILED before any quota increment, releases all claims, and salvages valid generated questions unclaimed. The LLM layer logs `challenge_quiz_generation_counts` for each generation attempt (including retry time), and the session layer logs `challenge_quiz_delivery_counts` after assembly for starts and `+5`.

### Known limitations

A multi-note ready quiz can contain 12–18 of its requested 18 questions when the source coverage floor also passes. A single-note ready quiz can contain one question fewer than requested. The ready screen shows the delivered and requested counts when underfilled.

## Progressive Quiz Generation (Challenge mode only)

Challenge mode supports on-demand question batching within a live session:

- **Initial count**: 10, 12, or 15 questions from the learner's latest Quick Review score band (below 50, 50–79, or 80+); 12 with no prior score
- **Batch size**: +5 questions per request
- **Maximum**: 20 questions per session (`MAX_CHALLENGE_QUIZ_QUESTIONS = 20`)
- Backend minimum for a valid batch: 3 unique new questions after dedup (`MIN_NEW_QUESTIONS_AFTER_DEDUP = 3`)
- Users see a `+5 Questions` button in the action bar at the last answered question (when under max and `noMoreQuestions` is false)
- The POST records a token and start timestamp under the session lock and returns HTTP 200 with `morePending: true`. A second POST returns 409 `MORE_IN_PROGRESS`; POST `generate-more/stop` clears the token and returns 204. The background worker claims bank rows and copies templates in a short transaction, calls the tiered model outside any transaction, then takes the session lock to append. The in-progress read and Start Quiz responses expose `morePending` and `moreFailed`. The page polls every 2 seconds while pending, keeps answering enabled, and only appends new questions and timer values; it never resets local answers or the current index. After 45 seconds it offers Stop adding. A failure offers Try again. The timer continues throughout.
- Backend first reuses eligible per-user banked questions, then uses `QuizDeduplicationUtils.uniqueQuestions()` to deduplicate any generated shortfall by normalized question text against all existing session questions
- If fewer than 3 unique new questions survive dedup, the background job records `moreFailed`; the page offers Try again
- New questions are appended to the session JSONB state via `QuizSessionStateUtils.appendQuizItems()`; no schema changes required
- Every `+5` item is stamped with the primary Study Pack id. This is accurate even for a multi-note session because
  the bank, Official template, and live generation paths for `+5` all use the primary pack only.
- Board Exam Mode is exempt — no generate-more button; fixed question count for the session

## Action bar and button labels

**Challenge mode (non-Board Exam):**

- At the last question, the action bar renders:
  - `+5 Questions` — visible when `totalQuestions < MAX_SESSION_QUESTIONS` and `noMoreQuestions` is false
  - `Complete Quiz` — submits the session and navigates to the result screen
- An action hint appears above the buttons at the last question: `What would you like to do next?`
- The action bar is rendered by `StickyAssessmentFooter` (`components/ui/sticky-assessment-footer.tsx`) — fixed to the viewport bottom at all breakpoints with iOS safe-area inset and backdrop blur
- The hint text is passed via the `hint` prop and appears above the buttons

**Board Exam Mode:**

- Uses `variant="board-exam"` on `StickyAssessmentFooter` (slightly different border color)
- Retains the original submit button label (`submitButtonLabel`), unchanged by progressive quiz work

## Scoring

- Score is based on **answered questions** (selected single-choice answers, selected multi-choice answers, nonblank Identification answers, and Enumeration answers with at least one nonblank slot), not the total questions in the session
- `computeStatistics()` uses the answered-question index set as `totalQuestions` when the user has answered at least one question; falls back to `quiz.size()` only when nothing is answered
- This allows users to finish early and receive a fair score based only on what they attempted

### Dual score display (Challenge mode only)

When the user skips questions (`hasUnansweredQuestions = !isBoardExamMode && quiz.length > result.totalQuestions`), the result screen shows two scores in one outcome hierarchy:

- **Answered Accuracy** (`result.scorePercentage`) — correct out of answered; primary metric
- **Overall Completion Score** (`Math.round(correctAnswers / totalGenerated * 100)`) — correct out of all generated questions

Both values live in one shared `ScoreReveal` outcome block: Answered Accuracy uses the primary large percentage, while Overall Completion Score uses the optional, visually subordinate `secondaryMetric` slot with `correct of total` description text. The explanatory helper line remains below the reveal. When all questions were answered, `ScoreReveal` shows the single primary `Score` metric and no secondary slot. There is no separate Score Summary grid card.

## UX microcopy

Running state (Challenge mode only):

- Banner at top of quiz: `Your starting question set adapts to your recent performance. Generate more as you go (up to 20).`
- Below choice list at last question: `You can finish anytime. Score is based on answered questions.`
- Action bar hint at last question: `What would you like to do next?`
- Toast on successful generate-more: `{n} questions added` using the actual appended count
- When `noMoreQuestions = true`, the `+5 Questions` button is hidden; no toast shown

## Result screen

Current result screen sections:

1. shared `ScoreReveal` outcome block (score, correct/answered count, duration, and performance level; optional Overall Completion Score when questions are unanswered)
2. concept breakdown
3. shared post-session next step
4. secondary actions

Primary CTA rules:

- after completion, the page fetches `GET /study-packs/{studyPackId}/next-step`
- the shared `<PostSessionNextStep>` component renders the dominant next action for Challenge Quiz
- when genuine weak concepts remain, `Practice Weak Concepts` is primary because Challenge Quiz provides the diagnostic signal for targeted Adaptive Practice
- when no genuine weak concepts remain, the primary action stays progression-oriented with another Challenge
- fallback UI keeps the previous weak-concepts card plus retry/practice actions when the next-step fetch fails
- Board Exam Mode and Long Exam do not consume this recommendation service; their report and next-step flows remain mode-owned

Secondary actions:

- `Review Answers`
- `← Back to Note`

If Adaptive Practice quota is exhausted, the shared component shows the targeted concepts plus a plan-aware upgrade CTA instead of routing into the limit wall.

## ConceptHealth

- on completion, Challenge Quiz records concepts answered fully correctly in the session to `ConceptHealth`
- on completion, Challenge Quiz also records concepts missed in the session to `ConceptHealth.lastIncorrectAt`
- Board Exam Mode uses the same Challenge completion path and records fully-correct and missed concepts the same way
- In a multi-note Challenge Quiz or Board Exam, each generated item carries its source Study Pack id and completion
  records that item's evidence only against that pack. This prevents shared concept names from being attributed to
  the primary pack or to non-contributing sibling notes. Pre-v0.104.0 unstamped Challenge items retain the
  historical primary-pack fallback so an in-flight session can still complete.
- a concept is recorded only when its concept breakdown is `correctAnswers == totalQuestions` and `totalQuestions > 0`
- weak or partially correct concepts are recorded as missed, not mastered
- the post-session next-step endpoint reads ConceptHealth after completion, so fully correct concepts can immediately reset due-ness before the next recommendation is resolved
- a later fully-correct Challenge or Board Exam session updates `lastCorrectAt` and clears the struggling state derived from a newer `lastIncorrectAt`
- genuine weak concepts are the capped union of reviewed-and-decayed ConceptHealth entries plus concepts actually missed in the completed Challenge session
- never-reviewed concepts are treated as not started and cannot trigger Adaptive Practice by themselves

## Learner level control

Challenge Quiz result screens expose the same learner-level adjustment control used by Quick Review.

The control saves the reader's profile level. It remains relevant to prompt scaffolding and is the curriculum fallback for notes without an authored learner level, but it does not change which questions are claimed for a note that carries its own learner level. The note's authored curriculum floor wins in that case.

Current save toast:

- `Learner level updated. Future Study Packs and quizzes will match this level.`

The retained toast predates note-level authoring and now overstates the control for notes with an authored learner level; changing that UI copy is outside this backend-only re-keying.

## Review and export

- completed sessions remain note-owned
- Challenge Quiz and Board Exam start, in-progress, and progress responses never carry `correctIndex`,
  `correctIndices`, `explanation`, `workingSolution`, `acceptableAnswers`, or accepted Enumeration answer
  content; Challenge Quiz `generate-more` follows the same rule. Enumeration keeps only the outer
  `acceptableAnswerGroups` length needed to render its input slots. The completion response is the sole inline
  reveal channel: it carries the full quiz plus the learner's single-choice, multi-choice, Identification, and
  Enumeration selection maps, and the result screen reads `QuizAnswerReview` from that completion payload.
- answer review uses the shared review layout (`QuizAnswerReview`)
- review/export must use persisted session data only
- standalone session review (`NoteSessionReviewPageClient`) renders `QuizAnswerReview` with `stickyNav={true}`, which replaces the inline Prev/Next navigation with a `StickyAssessmentFooter` fixed to the viewport bottom — eliminates layout jitter when explanations expand/collapse
- inline answer review on the challenge quiz result page does NOT use `stickyNav` (inline nav is correct there)

## AI Generation Spec

### Shared JSON contract

All generated quiz payloads must return JSON only with this shape:

```json
{
  "questions": [
    {
      "question": "...",
      "choices": ["A", "B", "C", "D"],
      "answer": "B",
      "explanation": "...",
      "concept": "..."
    }
  ]
}
```

Rules: exactly 4 choices; `answer` must be `A`, `B`, `C`, or `D`; `explanation` and `concept` are required; no markdown, no comments, no prose before or after JSON.

### Learner level guidance

If the user has no saved learner level, default prompt difficulty to `College`.

| Level | Expected behavior |
|---|---|
| `GRADE_SCHOOL` | Very simple definitions and identification; no tricky distractors; no complex computation |
| `JUNIOR_HIGH` | Concept understanding; simple problem solving; basic computation when supported |
| `SENIOR_HIGH` | Concept understanding plus moderate application; simple to moderate computation |
| `COLLEGE` | Deeper concept questions; situational and analytical prompts; moderate computation |
| `BOARD_EXAM_REVIEW` | Exam-style questions; plausible distractors; situational and multi-step reasoning; computation when quantitative |
| `PROFESSIONAL` | Applied or case-based framing; real-world scenarios |
| `PERSONAL_LEARNING` | Practical and accessible; around a college-foundation baseline unless notes suggest otherwise |

### Quantitative / computation guidance

A note's context counts as quantitative in one of two ways, and the first is authoritative. **(1) Declared:** the effective Domain Context carries a `quantitative` property on the `DomainContext` enum itself — `ENGINEERING_MATHEMATICS`, `ENGINEERING_SCIENCES`, `CIVIL_ENGINEERING`, `NURSING` and `ACCOUNTANCY` declare `true`; `GENERAL_EDUCATION`, `PROFESSIONAL_EDUCATION`, `PROFESSIONAL_PRACTICE_AND_REGULATION`, `v0.111.0`'s `ARCHITECTURAL_DESIGN`, `ARCHITECTURAL_HISTORY_AND_THEORY` and `PLANNING_AND_SITE_DEVELOPMENT`, `v0.145.0`'s `BASIC_MEDICAL_SCIENCES`, and `v0.164.0`'s `COMPUTING` declare `false`. **(2) Inferred:** if the domain does not declare it (including when no Domain Context resolves at all), the effective authoring domain (Domain Context label, else course/program), subject, tags, concept hints and summary are scanned for quantitative keywords (engineering, physics, math, accounting, finance, chemistry, statistics, …). The declared check runs first and only ever *adds* — a note the keyword scan would have caught is still caught, so `false` means "fall through to the scan", not "never quantitative". Do not read the keyword list as the definition of the signal; before v0.85.0 it was, and because one keyword is `engineering` the signal tracked a program's *name*, leaving 48.4% of the public catalog without guidance. When the context is quantitative, the prompt should allow computation questions, formula-based questions, word problems, unit conversions, and multi-step calculations when appropriate. Keep them multiple choice; ensure the computed answer matches one choice exactly; explanation should show short step-by-step solution flow.

### Explanation quality

Explanations should sound like a tutor, explain why the correct answer is correct, stay concise but useful, and include short steps for numeric problems. Avoid empty explanations such as `B is correct because it is correct.`

## Leave quiz guard

- `useQuizSessionGuard` intercepts navigation (document click, popstate, beforeunload) when the quiz is active
- `onBeforeRouteLeave` and `onConfirmLeave` passed to `useQuizSessionGuard` must be stable references (memoized via `useCallback`) — timer-driven re-renders fire every second and will cause `LeaveQuizModal` to unmount/remount on every tick if these are inline functions
- `onConfirmLeave` reads session state from `challengeSessionRef.current` (not `challengeSession` state) to keep its deps stable while still seeing the latest session value
- Board Exam Mode confirm leave submits the session (`finalizeChallengeSession`) and counts it as completed
- Standard Challenge mode confirm leave calls `forfeitChallengeQuizSession`

## Incomplete-submission guard

- Manual `Complete Quiz` / `Submit Exam` checks the existing answered-question state before finalizing.
- With unanswered questions, an `AppModal` names the unanswered count and lets the learner go back to the first unanswered question or submit anyway with the existing incomplete-answer scoring behavior.
- This is a submit-time guard, separate from the navigation-away leave guard.
- Timer-triggered auto-submit bypasses this confirmation and continues to finalize immediately.

# Model routing (v0.175.0)

Ordinary Challenge single-note, multi-note per-source, and background `+5 Questions` generation use `LlmRouting.tiered(learnerUserId)`: FREE resolves `LLM_MODEL_FREE`; Plus, Pro, and ADMIN on any plan resolve `LLM_MODEL_PREMIUM`. A failed premium call gets the existing same-route transport retry only; it never falls back to the free model. Board Exam and Long Exam keep their interactive routes. The Official template seed uses the owner's tier. The model and token usage are recorded on the session, with the first non-null model retained for multi-source generation.

## Statement-pair verifier (v0.175.0)

The independent verifier runs for the Official template seed and single-note Challenge preparation after Challenge's existing statement-pair consistency check and before generated questions enter the bank. It checks only statement-pair items. Multi-note starts use the background preparation path; the verifier switches remain off. Sol and Luna both judge each pair from the source note and Study Pack summary without seeing the generator's choices, key, explanation or verdicts. An item is kept only when both checkers give definite verdicts matching the generator's pair. A failed checker is retried once; if it still fails, the item is rejected. Rejected items receive one batched request for non-assertion replacements on the generator's own model; each replacement passes the normal schema, statement-pair and answer/explanation checks. A replacement that is statement-pair or fails validation is omitted. The normal start minimum still decides whether preparation succeeds.

The path switches `studysnap.llm.statement-pair-verifier.template-seed-enabled` and `studysnap.llm.statement-pair-verifier.live-start-enabled` are both **false by default**. The fixed checker model settings default to `gpt-6.1-sol` and `gpt-6-luna`. The template seed and live-start paths retain their existing verifier selection; +5 is never verified. This keeps verifier results and model names internal, with no learner-facing copy.

The +5 statement-pair exclusion switch remains off. This change does not alter prompts, schemas, or verifier switches.
