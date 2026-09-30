# RELEASES.md - NoteLib

## v0.163.0 - No Peeking

**Status: Released**

Theme: stop a quiz from telling a learner the answer before they've committed to one, and stop Challenge
Quiz's question bank from racing itself during regeneration.

### Planned Scope

**Scope picked by the owner, 2026-09-27, from a Backlog Index survey (five candidates originally picked;
one dropped at kickoff — see the note after item 4 — leaving four, at the top of the documented 3-4 item
sweet spot).**

1. **Quiz answer-key redaction across 6 practice-session surfaces (full-stack, far larger than first
   scoped — see the design plan, revised after `advisor()` (3 rounds) and again after a cold Opus
   falsification pass, which itself needed one more round of direct re-verification before its findings were
   trusted).** `correctIndex`/`correctIndices`/`explanation` are served unfiltered, unconditionally, across
   Long Exam, Board Exam, Adaptive Practice, and Challenge Quiz. **Interview Practice, discovered mid-item to
   be a fully separate backend (`InterviewPracticeService`), added to scope: it has a real, already-shipping
   resubmission exploit** — directly re-verified, not taken from a subagent's report: `answerQuestion`
   (`:168-211`) takes a client-supplied `questionIndex` with no re-answer guard, generates a fresh LLM
   critique (which reveals correctness) on every call, and `buildReport` (`:461`) scores whatever was stored
   for that index at completion time — so submit-wrong-then-resubmit-correct is real and counts today. This
   is the learner's own practice record (an Interview Readiness Report), not a cross-user exposure — treat it
   as part of this item's normal ship cadence, not a separate hotfix, unless the owner decides otherwise.
   **Quick Review gets the LEAST protection of the 6, not the most, and is deferred to its own design pass:**
   its session has never stored a quiz at all (`session.setSessionState(null)` from creation) — this actually
   makes the original kickoff claim ("the `/progress` round trip carries no quiz-item data") TRUE, just true
   for a different, uglier reason than claimed: there's no session-side quiz to protect because the frontend
   never reads one — `quick-review/page.tsx:371,417` sources the entire rendered quiz, answer key included,
   from `getNote(noteId)` (`NoteResponse`), the exact endpoint decided to stay unredacted. **The "backend-only
   pitch" history in the original kickoff text was still false** (traced to the discarded unauthorized fork,
   echoed in without verification) — that correction stands; the response-type claim itself did not need
   correcting, only its conclusion did. **Full corrected design:**
   `docs/claude-plans/2026-09-28-quiz-answer-key-redaction-plan.md`. Owner decisions: Note Detail, Study
   Pack, the public note page, and DOCX `WITH_ANSWERS` export all stay **unredacted**; Quick Review gets a
   genuinely new quiz-less fetch, sized as its own short design pass, not dropped from scope; Challenge
   Quiz/Board Exam's post-completion answer review widens `ChallengeQuizSessionResponse` directly rather than
   adding a second round trip; Adaptive Practice's completion endpoint currently lets the client's submitted
   selections override server-stored ones for scoring, which also changes what `ConceptHealth` gets written
   from — fixed as part of this item, flagged for the pre-signoff falsification brief specifically. **⚠️
   Explicitly a practice-integrity fix, not a security boundary** for the 5 modes other than Interview
   Practice's already-live exploit — an account owner can still read their own note's answer key via the
   Note/Study Pack page; this closes the *accidental* exposure and the *resubmission* exploit, not
   account-owner self-access. Board Exam confirmed served by `ChallengeQuizService` (`MODE_BOARD_EXAM`), not
   `LongExamService`. Teacher share-link quiz path (`/quiz/[token]`) traced and confirmed **already safe**.
   Backlog Index row: "Quiz session wire payload already includes `correctIndex`/`correctIndices`/
   `explanation` for unanswered questions, across every shared quiz mode."
2. **Challenge Quiz `releaseClaims` deadlock risk.** The `REQUIRES_NEW` transaction can wait indefinitely
   on locks its own caller already holds (all three relevant timeouts are 0); dormant in production logs
   today, not actively firing. Needs a real two-connection Postgres integration test before any fix
   ships — a mocked-repository test would pass under the same defect by construction. Backlog Index row:
   "`ChallengeQuizQuestionBankService.releaseClaims`'s `REQUIRES_NEW` transaction can wait indefinitely on
   locks its own caller already holds."
3. **Challenge Quiz bank-invalidation race.** `generateMoreQuestions` can race the bank-invalidation path
   and let stale rows survive a regeneration. Needs a generation-stamp migration; no rewrite of existing
   rows. Backlog Index row: "`ChallengeQuizService.generateMoreQuestions` can race `v0.162.0`'s new bank
   invalidation, letting stale-content rows survive a regeneration."
4. **Challenge Quiz session-complete vs. bank-delete throw.** Lower-severity, same falsification pass
   that surfaced items 2-3, same shared `ChallengeQuizService`/bank code — bundled here rather than
   deferred, since items 2-4 all touch the same shared method. Backlog Index row: "A Challenge session
   completing at the exact moment a regeneration's bulk bank-delete commits could throw, not corrupt."
5. **Shared quiz-session-entity concurrency: unlocked writers can resurrect a completed session, and
   Quick Review's staleness anchor is wrong.** Found by the item 1 pre-signoff falsification pass (three
   cold Opus agents, one per surface), and by directly reading the shared entity's full writer set across
   all five services afterward — the three-way split let this fall between the agents, since none of them
   owned the shared entity itself. `QuickReviewSessionEntity` has no `@Version`/`@DynamicUpdate`; several
   writers per service (`updateSessionProgress`/`forfeitSession` on Challenge Quiz; `answerQuestion`/
   `completeSession` on Interview Practice; `completeSession`/`forfeitSession`/`saveConfidenceLevel` on
   Quick Review; `saveProgress`/`pauseSession`/`resumeSession`/`completeSession`/`forfeitSession` on Long
   Exam; `completeAdaptiveSession` on Adaptive Practice) read the row unlocked and later overwrite the
   *entire* row. A racing unlocked write that read before a completion commits and saves after silently
   reverts status to non-terminal — reproduced live on Postgres for Challenge Quiz by the falsification
   agent. Before this release that was cosmetic; after item 1's redaction, `/complete` is the one channel
   that reveals several modes' answer keys, so resurrecting a completed session is a real resubmit-with-
   the-revealed-key exploit, not just data corruption. Interview Practice's own resubmission fix (item 1)
   is separately incomplete: its per-index lock only stops a *sequential* resubmit of the *same* index; a
   race across *different* indexes lets the later-committing write erase the other index's stored
   critique, unlocking it for a real resubmit with the now-revealed answer. Separately, Quick Review's
   staleness check compares session creation time against `notes.generation_enqueued_at` (set at enqueue),
   but the quiz only changes at commit, and nothing blocks entry while the note is `GENERATING` — a
   session created in that window is never caught as stale, which can leak the new quiz's key on resume
   and let `completeSession`/`updateSessionProgress` (neither checks staleness at all) grant mastery from
   answers given on a different quiz. `study_packs.generation_stamp` cannot anchor this fix as-is: it
   advances on a summary-only admin repair that never touches the quiz (false stale) and does not advance
   on the malformed-quiz repair path that does change the quiz (false fresh). Fix direction, decided with
   the owner: extend the existing `PESSIMISTIC_WRITE`/`FOR UPDATE` pattern (already used by several other
   writers in each service) to every unlocked mutating writer above, rather than adding `@Version` —
   Challenge Quiz's `persistProgress` fires unserialized on every answer toggle with failures silently
   swallowed client-side, so a reject-based optimistic lock would need new retry/merge semantics on the
   highest-traffic path to avoid silently dropping legitimate overlapping writes; a wait-based lock closes
   the same defect with no new conflict-handling code, since each writer's *existing* status check
   correctly sees the fresh terminal state once unblocked. Interview Practice's fix does not hold a lock
   across the LLM call (would serialize all answers behind LLM latency): it re-reads locked only for the
   short merge-after-LLM step, matching the existing split-transaction precedent used elsewhere in this
   codebase for the same reason. Quick Review's staleness anchor becomes a dedicated `quiz_stamp` (distinct
   from `generation_stamp`), bumped only where `study_packs.quiz` is actually written in place, stored on
   the session at creation and compared by value (not timestamp) at `/answer`, `/progress`, `/complete`,
   and `findQuizMasteredAt`, with a defined fallback to the current timestamp check for sessions that
   predate the migration. Known Limitations NOT in this item's scope, carried forward: two concurrent
   recipient session starts can still 500 (insert-vs-insert, no row to lock yet); a lost `/progress`
   network write can still lose the active retry-question set on reload; Adaptive Practice's legacy
   concept-name fallback still trusts unfiltered client strings; and `AdminStudyPackTransactionHelper`'s
   admin-repair stamp race (documented in item 2-4's Known Limitations) is broader than originally
   described — it can revert a concurrent user regeneration's entire quiz and content, not just drop a
   stamp increment.

**A fifth candidate from the original kickoff survey — Study Plan Builder drag-persist race, unrelated to
item 5 above — was DROPPED at kickoff, not scoped in.** Its Backlog Index row ("Study Plan Builder drag persists per drop and races its own save")
had never carried a `Last reviewed` date; actually reading the current code at this kickoff (not just
grepping for the old, lost Codex prompt) showed the deferred "Save order" model it called for already
shipped in `v0.96.0` (`185e0cc7`, 2026-08-29) — `study-plan-builder-page-client.tsx`'s
`savePendingLeafOrder`/`persistLeafItems` implement exactly that model, and all three traps the row named
were addressed per that commit's own mutation-verified audit. The row is corrected to SHIPPED; see
`ROADMAP.md`.

Anti-drift: no automated Tier 3 question-quality gate this release; H4's validator, H5's wording, and
H6's exclusion (still gated on H5's post-ship checkpoint) are all unchanged; the bank-invalidation
migration (item 3) adds two generation-stamp columns only; the teacher share-link quiz path is a known,
explicitly out-of-scope gap for item 1, not silently ignored. Note/Study Pack pages, the public note page,
and DOCX `WITH_ANSWERS` export all stay unredacted by owner decision (item 1's own plan file). **⚠️ Quick
Review's fix does not add a quiz store**: the session continues to use the Study Pack's persisted quiz and
stores only locked selections plus navigation state in its existing `session_state` JSONB.

**Verification tier — the pre-signoff falsification pass this note called "still owed" has since run, in
three separate layers, and is now closed:**
1. **Three cold Opus agents, one per item-1 surface, no inherited context**, run against the merged
   diffs of PRs #1460-1464. This is what *found* item 5 (the shared-entity concurrency defect) — none of
   the three individually owned the shared `QuickReviewSessionEntity`, so the systemic gap fell between
   them; reading the entity's full writer set directly, afterward, is what actually surfaced it.
2. **A fourth, scoped Opus agent against item 5's own diff** (PR #1466's first commit) found four more
   issues in that delivery itself — most seriously, that Interview Practice's split-transaction fix did
   not actually isolate its two phases under this app's own `spring.jpa.open-in-view=true` — all fixed,
   each proven with a failing-first test (real Postgres or jsdom).
3. **A fifth, narrowly-scoped Opus agent against just the `AbortController`-based queue-ordering
   mechanism** added in step 2's fix, since it was the one piece no prior agent had reviewed — found the
   mechanism was itself asymmetric and could resolve a superseded caller's waiters too early. Fixed and
   proven failing-first; PR #1467.

Every `MockMvc` route test and `advisor()` checkpoint this note called for was applied across all three
layers. See RELEASES.md's Shipped section (items 1 and 5) for the full finding list and fixes.

### Shipped

- **Quiz-session concurrency and Quick Review quiz staleness (item 5):** Mutating writers across
  Challenge Quiz, Interview Practice, Quick Review, Long Exam, and Adaptive Practice now lock the
  session row before checking status and saving, so a writer that waits behind completion sees the
  terminal state. Interview answers validate in one short transaction, call the LLM with no
  database-transaction or row lock held, then lock and merge into a fresh session in a second
  transaction; this closes the cross-index critique-erasure/resubmit race left by item 1. **The split
  frees the transaction and row lock, not the pooled connection** — `spring.jpa.open-in-view` is ON here,
  so one connection is held for the whole HTTP request regardless of transaction boundaries (per
  `ConnectionLifetimeStartupLogger`'s own startup line); do not describe this as freeing a connection.
  Challenge Quiz coalesces client
  progress writes and awaits its final progress flush before leave. `V152` adds
  `study_packs.quiz_stamp BIGINT NOT NULL DEFAULT 0` and nullable
  `quick_review_sessions.quiz_stamp_at_creation`; quiz replacement bumps only the quiz stamp (inside
  `saveStudyPack` itself, guarded by `!isNewStudyPack`, so all five of its callers are covered, not only
  the async worker), and Quick Review compares the captured value on answer, progress, completion, and
  mastery lookup. Legacy sessions with a null capture retain the earlier enqueue-timestamp rule. Real
  PostgreSQL tests prove every newly locked writer waits behind a completion before reading terminal
  status, prove the split Interview lock boundary and cross-index merge, and cover Quick Review's stamp
  and legacy fallback. MockMvc requests pin both Adaptive completion aliases and the Quick Review
  no-share denial. **Audit corrections, found before commit:**
  - **The delivered Interview split did not actually split under production's own configuration.**
    `spring.jpa.open-in-view` is ON here (the Spring Boot default — `application.yaml`'s own startup
    logger confirms `open-in-view=ON` at boot), so one `EntityManager` spans the whole HTTP request; the
    two `TransactionOperations.execute()` calls each open their own database transaction but share that
    one `EntityManager`'s identity map, so phase B's "fresh" locked read handed back phase A's already-
    managed, now-stale Java object — the row lock was real, but it protected a write into stale memory,
    reopening the exact cross-index erasure this item exists to close. Confirmed with a new test that
    manually binds an `EntityManagerHolder` per worker thread (the same mechanism
    `OpenEntityManagerInViewInterceptor` uses), which failed against the delivered code and passes only
    after adding `entityManager.refresh(session)` immediately after phase B's locked read. The original
    two-thread test (no bound `EntityManagerHolder`) could not have caught this: absent open-in-view
    binding, each phase gets its own fresh `EntityManager`, so it never exercised the identity-map path
    at all.
  - **Challenge Quiz's `finalizeChallengeSession` did not await the last progress write before
    completing.** The server grades from stored `session_state`, not the client's request body — if the
    last answer's coalesced progress write was still queued when `/complete` fired, the server would
    score from a state missing that answer. `finalizeChallengeSession` now awaits the latest progress
    flush first, mirroring Long Exam's `await flushIdentificationAnswer()` before its own completion.
  - **The tab-hidden write regressed on real unload.** Coalescing made the `visibilitychange`-hidden
    write wait behind whatever was already in flight; on a real tab close or mobile background-kill the
    JS context can die before the queue ever drains, so the latest state might never be sent — before
    coalescing, that write fired immediately. Added a queue-bypassing `persistLatestProgressImmediately`
    used only by the tab-hidden path (send now, drop any stale queued entry for that session, and only
    resolve that dropped entry's own waiters once the immediate write itself settles — resolving them
    synchronously would let an awaiting caller like the completion flush above proceed before the
    immediate write is even sent, racing it for the row lock). The explicit Leave action keeps the
    original queue-respecting, awaited path deliberately — it already awaits the flush itself, so
    queueing costs nothing there, and an existing test pins that sequencing. **The
    `beforeunload`/route-change guard (`handleBeforeRouteLeave`) was deliberately left on the same
    queue-respecting path, unlike the tab-hidden handler, even though it is NOT awaited and the page can
    in principle close during it.** This is not verified to be safe by any test here — the assumption is
    that modern browsers also fire `visibilitychange`→hidden before or alongside an actual unload, so
    the tab-hidden bypass above already covers the real risk in practice. Known Limitation: a browser or
    OS path that triggers `beforeunload` without ever firing `visibilitychange`→hidden first would still
    queue behind an in-flight write with no bypass; not fixed here.
  - **A newly added staleness guard on `/progress` and `/complete` contradicted an existing, deliberate
    design decision.** `recheckMaterialAccess`'s empty-`Optional` case covers two paths, both
    deliberately tolerated by the existing code, neither a denial: an infrastructure fault reading the
    pack (its own comment: "completion has always tolerated a pack it cannot read"), and a pack that has
    genuinely been deleted (`findVisibleStudyPack`'s Javadoc: "a pack that... has been deleted has
    always succeeded — the caller owns the SESSION... denying it here would strand the learner's own
    session for a reason that has nothing to do with sharing"). A genuine access denial already throws
    through a separate path in the same method, so the added check could only ever fire on one of these
    two tolerated cases, and only ever wrongly. Removed; `isStale`'s existing
    `accessibleStudyPack.isPresent()` guard already does the right thing when the pack can't be read.
  - Added an in-flight guard on Quick Review's `initializeSession(force=true)`: three call sites
    (`/answer`, `/progress`, `/complete`) can each independently hit a stale-session 409 and each call
    `initializeSession(true)`, and without a guard that can fire more than one concurrent `startSession`
    — reaching this item's own already-accepted insert-vs-insert Known Limitation by a new path, not a
    new failure mode, but cheap to close off anyway.
  - **A second, independent falsification pass (a fresh Opus agent, no inherited context) found four
    more issues in the diff above, all fixed and each proven with a real Postgres or jsdom failing-first
    test:**
    - **The identical stale-read-after-lock pattern also exists in `ChallengeQuizService.
      resolveExistingChallengeSession`, pre-existing since July (`c76e5c1d`/`287f0069`), not introduced by
      this item.** It reads the session unlocked to capture `observedStatus`, then locks it and compares
      `lockedExisting.getStatus() != observedStatus` — the same object by Hibernate identity, so the
      comparison can never fire. Proven live on real Postgres (a session completed by one connection
      still read as `IN_PROGRESS` by a racing `startSession`). Fixed with the same
      `entityManager.refresh()` pattern as Interview Practice's fix above, proven with a new test that
      pauses between the two reads via an AOP interceptor and commits a status change from a second
      connection in that window.
    - **The coalescing queue had no per-request timeout, and nothing cleared it on
      `resetToPrestart`** — one hung progress write (a fetch has no default timeout) would have blocked
      every later progress write for the rest of the page's life, including a later session on the same
      page, and `handleLeaveSession`'s new await could hang indefinitely, contradicting this item's own
      "costs nothing there" claim above. Fixed: each queued write now carries an `AbortController` with
      a 30-second dead-request bound (generous on purpose — a legitimate `+5 Questions` LLM call can
      legitimately hold this row's lock that long), and `resetToPrestart` aborts and clears the queue.
    - **The tab-hidden immediate write and the queue were not ordered against each other** — an
      in-flight (or 401-refresh-retried) queued write carrying older state could still land on the
      server after the newer immediate write, overwriting it. `updateChallengeQuizSessionProgress` now
      accepts an optional `AbortSignal`. **This mechanism was itself corrected in a third round below —
      it was asymmetric and dropped waiters early; read that bullet for the actual current design.**
    - **A quiz-repair race could give two different quiz contents the same `quiz_stamp`.** Both
      `StudyPackService.saveStudyPack` and `AdminStudyPackTransactionHelper`'s malformed-quiz repair read
      the pack unlocked and incremented `quizStamp` in Java, so a repair racing a real user regeneration
      could compute the identical stamp value the regeneration just committed — defeating the invariant
      `V152` exists to protect. Fixed with a new atomic `StudyPackRepository.bumpQuizStamp` (`SET
      quiz_stamp = quiz_stamp + 1`, a targeted DB-level increment) replacing the Java-side
      read-increment-write in both places; neither caller's in-memory `quizStamp` field is set anymore,
      so nothing reads a value staler than what the atomic bump already applied.
  - **A third, narrowly-scoped falsification pass (a fresh Opus agent, no inherited context, targeted
    specifically at the abort/ordering mechanism above since it was the one piece no cold agent had yet
    reviewed) found the mechanism above was itself broken in two ways, both confirmed with a temporary
    probe test and both fixed here, each with its own failing-first test:**
    - **The queue never aborted the previous holder — only the immediate path did.** `drainProgressQueue`
      wrote its own controller into the shared ref without aborting whatever was there before it, so a
      tab-hidden write left running when a later queue item started became silently untracked (no longer
      abortable by anything).
    - **The "hold waiters until settle" fix only covered a write that was still merely QUEUED, not one
      already IN FLIGHT.** If the write a newer one superseded was the one currently being sent (e.g.
      `finalizeChallengeSession`'s own flush, sent immediately because the queue was idle when Submit was
      clicked), aborting it resolved its waiters right away — `/complete` could fire before the write that
      actually superseded it had landed, reopening the "server scores from stale state" defect on a
      narrower path.
    - **Fixed by redesigning the shared state as `progressLiveWriteRef` — one record of `{ controller,
      waiters }` for whichever write is currently live, queue-drained or immediate — and a shared
      `supersedeLiveWrite()` helper that both writers call before sending their own request: it aborts
      the current holder and returns its waiters, which the caller merges into its OWN waiters before
      taking over the ref.** Waiters therefore chain forward through any number of supersessions and
      resolve exactly once, when a write finally settles without itself being superseded again. The
      immediate path also gained the same 30-second dead-request timeout the queue already had, closing
      a case where a hung immediate write (with `handleLeaveSession`'s wait dropped into it) could stall
      forever.
    - **Known Limitation, not fixed here — inherent to a client-side-only ordering scheme:** an abort
      only stops the *client* from waiting; it cannot recall bytes a request already sent to the server,
      and the server has no sequence number to resolve two genuinely-simultaneous requests by anything
      other than which one's transaction takes the row lock first. Two writes that are BOTH actually
      in flight to the server at the same moment (not one queued behind the other) are still ordered by
      server arrival, not by which one the client considers "newer." The one case this fix does close
      completely is a write stuck mid-401-refresh-retry, since the abort signal is threaded through the
      retry (`fetchWithAuth`) and a fetch started with an already-aborted signal never sends at all.
      `/auth/refresh` itself is not abortable, and remains a narrow gap within that one case.

- **Challenge Quiz bank concurrency (items 2-4):** Real Spring-proxied, PostgreSQL 18 Testcontainers
  reproductions found three `releaseClaims` faults before the fix: `generateMoreQuestions` held a bank-row
  lock while its `REQUIRES_NEW` release waited for a second connection until PostgreSQL's test-only
  `lock_timeout` returned `55P03`; a failed start committed an unreleased claim on a `FAILED` session;
  and expired `+5` rolled back its forfeit but committed an independent claim release. `c76e5c1d`
  introduced `REQUIRES_NEW` on the mistaken premise that a rolled-back claim write needed a separate
  release. Release is now a count-tolerant bulk `UPDATE` in the caller's transaction: it sees a failed
  start's uncommitted claims, rolls back with a failed `+5`, and does not flush deleted entities at
  commit. The same reproductions pass without a lock wait, orphaned claim, or released claim on rollback.
  Read-only production checks on 2026-09-29 found zero claims owned by non-`IN_PROGRESS` sessions and zero
  idle-in-transaction connections: no live incident or cleanup write is owed.
- **Generation-stamp invalidation:** `V151` adds `study_packs.generation_stamp BIGINT NOT NULL DEFAULT 0`
  and nullable `challenge_quiz_question_bank.generation_stamp`. Both content replacement paths advance
  the pack stamp with the content write and bank delete in one transaction. Generation captures the
  stamp with the summary before the LLM call and passes it to every bank insert path. New-question
  claim and Redo Missed count/claim reads require the current stamp, accepting `NULL` only for bank
  rows that predate the migration; the owning session's release and outcome reads never stamp-filter.
  A two-connection LLM-window reproduction inserted five old-summary rows after invalidation: they
  survived physically but ceased to be claimable after the stamp fix. Existing rows are neither wiped
  nor made unclaimable at deploy. **Audit correction:** the delivered diff also stamp-filtered
  `existsByUserIdAndStudyPackId`, `findOwnerStudyPackPairsByStudyPackIdIn`, and
  `findQuestionKeysByUserIdAndStudyPackId` — these guard a WRITE (skip a re-seed, skip re-copying a
  key the caller already holds) rather than hand out content, so filtering them made a stale-but-present
  row invisible to the guard, letting the Official-template re-seed and adopter-copy paths reuse that
  row's `question_key` and collide with `uq_challenge_quiz_question_bank_user_pack_key` — the same
  rollback-only-transaction failure `docs/features/challenge-quiz.md` already documents for a same-level
  duplicate. Reverted the filter on those three; the two claim/content-serving read paths above are
  unaffected. The concurrency integration test's stale-stamp assertions for these three methods were
  corrected to match (they must still see the stale row, not treat it as absent). **This closes the
  template-copy and re-seed collision paths only — see Known Limitations for what it does not close.**
- **Known limitations (Challenge Quiz bank concurrency):**
  - The plain LLM-generation path (`ChallengeQuizService`'s shortfall call into
    `persistGeneratedQuestions`) still does not check the caller's existing bank keys — including a
    stale-stamped row's key — before inserting; it only dedupes within its own freshly generated batch.
    A stale row now sits unclaimable until the pack's next regeneration (instead of being claimed away
    quickly, as before this fix), so it occupies its key slot longer, raising the odds of hitting the
    same already-documented same-level-duplicate failure. Only `+5`/`generateMoreQuestions` and
    `seedTemplateAsync` can reach this collision: `startSession` holds the pack `FOR UPDATE` across its
    LLM call. A collision rolls back that request and surfaces as HTTP 500; it is not a silent drop.
    Not fixed here; would need the bank's existing
    keys threaded into the LLM dedup set the same way `copyTemplateQuestions` now does.
  - Unfiltering `existsByUserIdAndStudyPackId` trades one race for another: if `seedTemplateAsync` races
    the Official pack's own regeneration and leaves a stale-only template, `exists` now reports `true`
    forever (until that pack regenerates again), so the template is never re-seeded — adopters silently
    fall back to the LLM instead, and `queueBackfill` counts that pack as `skipped`. Accepted as a rare,
    self-resolving trade rather than fixed now.
  - `AdminStudyPackTransactionHelper.regenerateOnePack`'s stamp bump
    (`currentPack.setGenerationStamp(currentPack.getGenerationStamp() + 1)`) is a read-modify-write with
    no `@Version` and no `GENERATING`-status interlock — confirmed by reading
    `StudyPackGenerationContextResolver.assertGenerationReady`, which only checks the multi-program
    Domain Context rule, not note status. A concurrent user-initiated regeneration of the same pack can
    revert the concurrent user regeneration's entire quiz and content, including its stamp, rather
    than merely lose one increment. Pre-existing gap, not introduced by this release; not fixed here.
- **Completion/delete hypothesis resolved without a production fix:** The repository's owning-session
  read currently has `PESSIMISTIC_WRITE`. A first test harness substituted an unlocked query and
  produced the predicted stale-row throw, exposing an unfaithful fixture; the corrected real-query
  PostgreSQL test showed the delete waits for completion to commit. Completion succeeds, then the
  delete commits. The test pins this lock so removing it would fail the race guard.

- **Long Exam answer-key redaction:** `LongExamStartResponse` and `LongExamSessionResponse` now redact
  `correctIndex`, `correctIndices`, `explanation`, `workingSolution`, `acceptableAnswers`, and
  `acceptableAnswerGroups` from every question at every point in a Long Exam session. Responses retain
  `keyConcept` for the domain breakdown, while the stored session quiz remains complete for scoring. No frontend
  change or deploy ordering constraint applies: the existing frontend type already permits a null
  `correctIndex`, its resolver falls back to `-1`, and every Long Exam question component already renders with
  answer reveal disabled, so old and new frontend/backend combinations remain user-visible-behavior compatible.
- **Interview Practice answer-key redaction and answer lock:** `InterviewPracticeStartResponse.question` and
  `InterviewPracticeAnswerResponse.nextQuestion` now redact `correctIndex`, `correctIndices`, `explanation`,
  `workingSolution`, `acceptableAnswers`, and `acceptableAnswerGroups`; the natural-language critique remains
  the reveal for the question just answered. A question locks when its critique is first stored in
  `sessionState.aiFeedback`, rather than when its provisional selection is written: retrying the same choice
  returns the stored critique without another save or LLM call, while changing the choice returns HTTP 409 and
  leaves the first answer intact for a sequential same-index retry. Item 5 additionally closes the
  cross-index concurrent overwrite that could erase that critique. This also closes the unmetered, un-rate-limited critique loop: Interview
  Practice quota is charged once at session start, so repeated answers previously created unlimited additional
  LLM calls inside the same paid session.

  **Concurrency correction in item 5:** the earlier per-index guard stopped sequential same-index
  retries, but did not stop concurrent answers on different indexes from erasing one another. Two
  short locked transactions around the LLM call now recheck and merge fresh state. **Deploy order: either.** The frontend never reads the redacted answer fields and its
  normal flow always advances after a critique, so an old client does not submit a changed answer that the new
  409 guard would reject; old and new frontend/backend combinations remain compatible.
- **Challenge Quiz and Board Exam answer-key redaction:** active-session `ChallengeQuizStartResponse` payloads
  and Challenge Quiz `GenerateMoreChallengeQuizResponse.newQuestions` now redact `correctIndex`,
  `correctIndices`, `explanation`, `workingSolution`, and `acceptableAnswers`, and remove every accepted answer
  from `acceptableAnswerGroups` while preserving its outer length so Enumeration still renders the required
  number of inputs. `ChallengeQuizSessionResponse` now reveals the full unredacted `quiz` only at completion,
  together with `selectedChoices`, `selectedMultiChoices`, `selectedIdentificationAnswers`, and
  `selectedEnumerationAnswers`; both result branches use those completion fields for `QuizAnswerReview`.
  Stored session questions remain complete for scoring and completion review.

  **Deploy order: frontend and backend together.** An old frontend against the new backend would give
  `QuizAnswerReview` the redacted active-session quiz with answer reveal enabled and render every learner
  selection as incorrect, even though server scoring remains correct. The new frontend keeps a compatibility
  fallback to the old active-session fields for an older cached or pre-deploy completion response, so the
  reverse overlap is safe, but it does not make backend-first deployment safe. Run `scripts/check-deploys.sh`
  promptly after this release merges and confirm both Vercel and Render are on the release.
- **Adaptive Practice answer-key redaction, per-answer reveal, and server-owned scoring:** every
  `QuickReviewAdaptiveQuizResponse` now redacts `correctIndex`, `correctIndices`, `explanation`,
  `workingSolution`, and `acceptableAnswers` from unanswered questions and removes accepted-answer content
  from `acceptableAnswerGroups`; questions already answered through the new
  `POST /adaptive-practice/sessions/{sessionId}/answer` endpoint are revealed together with the persisted
  `selectedChoices`/`selectedMultiChoices` maps so a resumed session restores both position and feedback.
  The endpoint locks the session row and each question index on its first persisted selection: an identical
  retry is idempotent and performs no write, while a changed selection returns HTTP 409. MATCHING locks at
  the same per-item index granularity, and MULTI_SELECT uses an explicit Check Answer step so checkbox edits
  remain reversible until submission. The full stored quiz remains unchanged for scoring.

  **Production-data semantics change:** Adaptive Practice completion previously had no server-side selections
  and therefore used the client's `correctAnswers`, `selectedChoices`, and `selectedMultiChoices` as the only
  source for both the stored score and `ConceptHealth`. Completion now ignores those legacy request
  claims and derives both outputs from the server-persisted, locked selections for every session. The legacy
  fields remain accepted for request-shape compatibility; `correctConceptNames` is consulted only for an old
  edge-case row whose stored quiz is empty; a normal session with no stored selections scores zero and cannot
  claim correct concepts. Include this change explicitly in the pre-signoff falsification brief.

  **Deploy order: frontend and backend together; both overlap directions break.** An old frontend against the
  new backend never calls `/answer`, so no selection is ever persisted; completion then treats every question
  as unanswered and calls `recordIncorrectAnswers` for every concept in the quiz — an active miss on each
  concept, not merely a withheld credit, feeding weak-concept selection and `twiceMissedConcepts` for every
  learner who completes a session during the skew window. A new frontend against an old backend receives 404
  from `/answer` and cannot reveal or advance. Run `scripts/check-deploys.sh` promptly after merge and confirm
  both Vercel and Render are on the release.
- **Quick Review answer-key redaction for owners and share recipients:** the widened start/resume response is
  now the page's only pre-completion data source. It supplies current Note metadata and a Study Pack quiz whose
  unanswered items have `correctIndex`, `correctIndices`, `explanation`, `workingSolution`, and accepted-answer
  content redacted. `GET /notes/{id}` and `GET /study-packs/shared/{id}` remain fully unredacted and unchanged. The new
  `POST /quick-review/{sessionId}/answer` endpoint locks the session row, reveals one stored Study Pack question,
  treats the same selection in the same attempt as idempotent, and returns HTTP 409 for a changed selection.
  MATCHING keeps its whole-group reveal while recording each item independently; MULTI_SELECT remains editable
  until Submit. The cumulative selection maps retain the latest accepted answer for completion and mastery,
  while attempt-bucketed maps enforce separate INITIAL and RETRY locks.

  `/answer` also advances the stored `retryCount` to the client's requested attempt, bounded to `0` or `1`, so
  a lost best-effort retry-transition `/progress` write cannot leave a RETRY answer colliding with the INITIAL
  lock bucket. `/progress` now accepts only `retryQuestionIndexes` and `activeQuestionIndexes` from the client,
  preserves all four server-owned answer maps, and rejects retry-count or round regression. Sessions created
  before the Note's latest `generationEnqueuedAt` are stale: start forfeits and replaces them, resume treats them
  as absent, and `/answer` returns a distinct restartable error. The unscoped Note read happens only after the
  existing owner-or-live-share authorization, so the same staleness and title behavior applies to recipients.

  Removing the owner-only `getNote` preflight also fixes recipient entry through Dashboard-shaped
  `/notes/{id}/quick-review` links and due-concepts-digest links, in addition to the explicit shared-note link;
  `isOwner` from the authorized response now selects the valid Note-detail destination. Production currently has
  zero recipient Quick Review sessions and zero live `note_shares` rows, so these recipient paths ship before
  real usage has exercised the Dashboard in-progress reader, `ConceptHealth`, or mastery-unlock analytics for a
  recipient.

  **Deploy order: frontend and backend together.** A new frontend against an old backend gets 404 from
  `/answer`. An old frontend against the new backend never calls `/answer`, while narrowed `/progress` discards
  the four answer-bearing keys it still sends. With no stored selections,
  `computeConceptBreakdownForStoredSelections` returns an empty list; both `recordCorrectAnswers` and
  `recordIncorrectAnswers` short-circuit, `verifiedCorrectAnswers` stays unset, and `verifiedPerfect` is always
  false. This is a silent non-unlock rather than an active miss, but Quick Review's traffic makes it the
  largest-reach deploy-skew window among the six answer-key PRs. Run `scripts/check-deploys.sh` promptly after
  merge and confirm both frontend and backend are on the release.

## v0.162.0 - Say the Value

**Status: Released** (signed off 2026-09-27; PRs #1455/#1456/#1457/#1458 merged into the release branch; release PR merged to `main` as #1459 and tagged 2026-09-27; deploy verified on both platforms — Render live `c3e4deaa` at 14:21:57Z, Vercel matched at 14:25:36Z)

Theme: a quiz explanation is finally allowed to say what the numeric answer actually is, so the model's own internal-consistency check has something to check — and stale exam content stops surviving a regeneration it should have invalidated.

**⚠️ CORRECTED 2026-09-27, same day as kickoff, before any Codex prompt was written.** The kickoff folded H6 into this
release. The incident doc's own LOCKED owner decision (`docs/claude-findings/2026-09-19-quick-review-percentage-increase-correctness-incident.md`
§Q.1 item 4, 2026-09-21) sequences H6 strictly AFTER H4/H5, not simultaneous with H5, specifically so a post-ship
change in H4's retry rate stays attributable to H5 alone. **H6 is REMOVED from this release** and re-logged in the
Backlog Index as its own future release, gated on H5's post-ship baseline read. The kickoff also wrote H5's wording
as "explanations **may** state the answer's value"; the locked decision says "**must**" — "may" would not reliably
move H4's recall, which is the whole point of H5. Corrected below.

### Planned Scope

**Scope picked by the owner, 2026-09-27: three related items from the Backlog Index, surveyed and verified against
code at kickoff, not taken from their status cells at face value.** A fourth item (H6) was folded in at kickoff and
REMOVED the same day on re-reading a locked owner decision (see the correction above). Two stale rows were found
during the survey and are NOT part of this release's scope (see "Also found" below).

1. **Phase A0 (documentation, Claude-direct): ratify `docs/architecture/ADR-002-quiz-answer-identity-by-text.md`.**
   Flipped `Status` from `PROPOSED` to `ACCEPTED`. Its own open question ("does `board-exam-developer.txt` inherit
   the letter contract from `schema.json` alone?") is RESOLVED by grep, not inference, and the answer splits by
   which of two separate lines each prompt file carries: the ANSWER-FORMAT line ("exactly one of A, B, C, D") is in
   exactly six files (`adaptive-practice-developer.txt:18`, `challenge-quiz-developer.txt:20`,
   `interview-practice-developer.txt:13`, `developer.txt:90`, `long-exam-developer.txt:24`,
   `teacher-quiz-developer.txt:18`) — `board-exam-developer.txt` carries NONE of its own and does inherit from
   `schema.json` alone, confirming the ADR's suspicion. The EXPLANATION-RESTRICTION line (the one H5 touches) is a
   DIFFERENT set of six: `adaptive-practice-developer.txt:28`, `board-exam-developer.txt:19`,
   `challenge-quiz-developer.txt:48`, `developer.txt:105`, `long-exam-developer.txt:46`,
   `teacher-quiz-developer.txt:29` — `board-exam-developer.txt` DOES carry this one, so H5 must edit it explicitly;
   `interview-practice-developer.txt` carries no such line, so H5 has nothing to relax there. MULTI_SELECT's
   equivalent contract stays explicitly deferred, unchanged by this ratification.

2. **Phase A (H5, backend, Codex).** Relax the explanation-restriction line in the six files named above so a quiz
   explanation **must** state the answer's value when every choice in that question is a short numeric/unit
   literal, while still forbidding a letter reference (`A`/`B`/`C`/`D`) — per the incident doc's locked wording, not
   the softer "may" the kickoff first wrote. **Numeric-conditional, not universal — owner decision 2026-09-27**: an
   unconditional version was considered and rejected once the ratio was read (numeric-literal MCQs are 1.7% of all
   MCQ-shaped items, 88 of 5,110 generated since H4 shipped; an unconditional rule would force the other 98.3%,
   prose-answer MCQs H4 never reads, to restate their full choice text verbatim for no validator benefit). Prose
   MCQs keep the existing "don't restate" rule unchanged. **What this actually buys:**
   `QuizValidationUtils.isAnswerExplanationInternallyInconsistent` (`:194-201`) excludes any MCQ with a non-numeric
   choice unconditionally, before the explanation is even read — H4 has ZERO evaluation of prose-answer MCQs, not
   "near-zero recall" as the Backlog row's original framing claimed. H5 can only raise H4's evaluable coverage on
   NUMERIC-LITERAL-answer MCQs: today an explanation that fully complies with "don't restate" gives H4 no evidence
   to check at all; "must state the value" closes that gap for the numeric subset only. Do not claim a prose-answer
   effect in the release notes. Gated on reading H4's production rejection-rate baseline first, so a post-ship rate
   change is attributable to H5 alone — **which requires H6 to ship separately** (see the correction above).
   **Shipped:** updated `adaptive-practice-developer.txt`, `board-exam-developer.txt`, `challenge-quiz-developer.txt`, `developer.txt`, `long-exam-developer.txt`, and `teacher-quiz-developer.txt`, pinned by `quizExplanationPromptsRequireExactNumericAnswerValueWithoutLetterReferences`.
   **Measurement, corrected 2026-09-27: the H4 retry/omit COUNT is the wrong metric for H5's effect and must not be
   read as a regression signal.** H5 gives H4 more evidence to check, so the retry count is EXPECTED TO RISE after H5
   ships — a rise is success, not a problem. The Challenge-bank fix (Phase C, same release) also raises generation
   volume, which inflates the raw count independent of H5, and Render drops logs after ~30 days (the 2026-09-22
   entries below expire ~2026-10-22), so a count-based read has no denominator and no shelf life. **The real metric
   is a per-question coverage ratio computed from the stored JSONB, not the log:** among MCQs where every choice is
   ≤20 characters and contains a digit (H4's own `isNumericUnitLiteral` predicate), what share have
   `explanation || workingSolution` containing the text of `choices[correctIndex]`. Compare packs generated between
   the H4 deploy (`v0.155.0`, 2026-09-22) and the H5 deploy against packs generated after H5 ships; the ratio should
   rise post-H5. **This is the checkpoint's instrument, to be minted in full at signoff, not run now** (H5 has not
   shipped yet), but its PRE-H5 baseline was read at correction time rather than left for signoff to discover it was
   never read. **Exact query, full context and caveats saved verbatim to
   `docs/claude-plans/2026-09-27-h5-coverage-ratio-baseline.sql`** (an approximation compared only against its own
   future re-run, not a re-implementation of `QuizValidationUtils`'s normalized matcher) — signoff must run the
   IDENTICAL query with the H5 deploy timestamp as the partition point, not a rewritten one. **As of 2026-09-27,
   read against `study_packs.quiz` for packs generated since the H4 deploy (`v0.155.0`, 2026-09-22):** of 5,110
   MCQ-shaped items, only **88 (1.7%) are numeric-literal** — the entire population H5's evaluable-coverage claim
   applies to; of those 88, **60 (68.2%) already state the correct value verbatim** under the CURRENT "don't
   restate" instruction (the ban is imperfectly followed today, this is not evidence H5 shipped); and **10 (16.7%
   of the 60) already also mention a distractor's value** — a PRE-EXISTING case `QuizValidationUtils:207-209`'s
   short-circuit cannot catch (it returns "consistent" the moment the correct value is found, before ever checking
   for a distractor), tracked as a masking-risk baseline to re-read post-H5, not a defect introduced by H5. A
   post-H5 numeric-MCQ sample well under ~80 items should re-date the checkpoint rather than be read as a verdict.
   Confirmed no in-place regeneration occurred in this window (`updated_at` never exceeds `created_at` by more than
   a minute across all 1,057 packs since 2026-09-22), so `created_at` is a clean partition point for the post-H5
   comparison — re-verify this assumption at signoff rather than reusing it uncritically. **The 88-item numeric
   population is small enough that "raises H4's evaluable coverage" is real but narrow — say so plainly rather than
   implying broad impact.** Exact log filter for the retry/omit COUNT, recorded for context only, not as the
   pass/fail signal: resource `srv-d6u0jkvgi27c73dvl9k0`, text `quiz_answer_explanation_consistency`, window
   2026-09-22–2026-09-27 (H4-only baseline): 10 `outcome=retrying`, 0 `outcome=omitted` — five days of total
   headroom across the whole system, for context on how small this signal currently is. The retry path
   (`retryInternallyInconsistentQuestion`) reuses the SAME input messages as the first attempt
   (`context.inputMessages().deepCopy()`), so there is no separate retry-prompt copy of the restriction to edit.
   **The incident doc's own locked text (§Q.1 item 3, and the original recommendation at line 691) requires this to
   ship "with a before/after sample review"** — a human reading of actual generated output under the old vs. new
   prompt, distinct from the coverage-ratio metric above. **This is a gate on merging the H5 PR, run by this session
   (not Codex — Codex has no OpenAI key/network access, so it cannot generate real packs and must not fabricate
   sample output): after Codex delivers the diff, generate a few Study Packs locally against source notes behind
   the 88 numeric-literal items above (so the new numeric-case wording actually fires) and a few prose-answer notes
   (so the unconditional "otherwise" branch is confirmed unchanged), under the old prompt then the new one, and read
   the explanations before merging.** **Gate cleared, 2026-09-27** — called the real `/responses` endpoint directly
   (same messages/schema `OpenAiLlmStudyPackService` builds, `gpt-4.1-mini`) on one numeric and one prose sample,
   old prompt vs. new: numeric explanations now state the value verbatim with no letter references in either
   version; no masking observed; the "don't discuss the other choices" and formula-text-echoing gaps found are
   pre-existing and appear identically under the OLD prompt, not introduced or widened by H5. Full findings at
   `docs/claude-plans/2026-09-27-h5-before-after-sample-review.md`. Prompt-only change; no schema, no parser, no
   migration.

3. **~~Phase B (H6)~~ — REMOVED from this release, see the correction above.** Logged in the Backlog Index as its
   own future release, gated on H5's post-ship baseline read.

4. **Phase C (Challenge Quiz bank invalidation, backend, Codex).** The sibling leg of the exam-pool invalidation
   defect `v0.143.0` already fixed for `StudyPackService`'s and the admin repair path's regeneration flows (both
   confirmed at kickoff to already call `examQuestionPoolService.refreshPool`). The Challenge question bank leg is
   confirmed STILL open: `ChallengeQuizQuestionBankService`/`ChallengeQuizService` (grep-verified) are never called
   from either regeneration path, so a regenerated note's Challenge Quiz keeps serving questions drawn from the
   deleted content. Fix: invalidate or refresh the bank on the same regeneration boundary, mirroring the exam-pool
   fix's shape. No migration expected; confirm against `ChallengeQuizQuestionBankService`'s actual write path before
   the Codex prompt is written.
   **Shipped:** `ChallengeQuizQuestionBankRepository.bulkDeleteAllForStudyPack` and
   `ChallengeQuizQuestionBankService.invalidateForStudyPack` now delete all bank rows for a regenerated pack in one
   JPQL statement. Exactly two of the three exam-pool invalidation sites call it after their existing
   `studyPackRepository.flush()` and Long/Board refreshes: `StudyPackService` regeneration replaces `summary` and
   `keyConcepts`, and `AdminStudyPackTransactionHelper.regenerateOnePack` replaces `summary`; quiz-only
   `repairMalformedQuiz` remains untouched because `quiz` is not a Challenge-generation input. The admin helper now
   reports whether content was actually replaced, and `AdminStudyPackService` then reloads the committed note and
   pack and calls `OfficialChallengeQuizTemplateService.queueSeedIfEligible`; the learner-facing path already had
   the equivalent post-commit seed. Production files: `ChallengeQuizQuestionBankRepository.java`,
   `ChallengeQuizQuestionBankService.java`, `StudyPackService.java`, `AdminStudyPackTransactionHelper.java`, and
   `AdminStudyPackService.java`. Tests: `NativeQueryPostgresIntegrationTest.java` proves the delete's pack predicate
   and claimed-row behavior against Flyway PostgreSQL; `StudyPackServiceTest.java` and
   `AdminStudyPackTransactionHelperTest.java` pin flush → exam refreshes → bank invalidation ordering;
   `AdminStudyPackServiceTest.java` pins post-commit reload/re-seed and the false-result skip; and
   `ChallengeQuizQuestionBankServiceTest.java` pins the unannotated transaction-joining service method.
   **Verified via a scoped Opus falsification pass (worktree pinned to `d4cd98f3`), CORRECTED 2026-09-27: the
   executor-rejection framing below was wrong, and two additional findings surfaced, both documented rather than
   fixed.**
   - **Executor math corrected.** 890 admin-owned packs matching summary regeneration had bank rows in production
     on 2026-09-27 (not all Official-template eligible). The 4-core/8-max/50-queue `llmParallelTaskExecutor`
     admits roughly the FIRST 58 of a bulk run's regeneration tasks and rejects the rest at submission —
     **those rejected packs are never regenerated, so never invalidated, and need no re-seed at all.** Among the
     ~58 admitted, only the one whose re-seed happens to land while the queue is still full is rejected — expect
     about ONE rejected seed per saturated run, not most of them (the original wording overclaimed this). A
     rejected seed still degrades safely (`copyTemplateQuestions` copies nothing, Challenge Quiz generates fresh
     shortfall questions). Recovery: rerun `POST /admin/study-packs/seed-official-challenge-quiz-templates` only
     AFTER the bulk run has fully finished, not while seeds may still be in flight — its existence gate can't see
     an uncommitted seed, so an overlapping rerun wastes LLM calls and can occasionally double a template.
   - **New finding, documented not fixed (`docs/features/quiz.md`): `generateMoreQuestions` ("+5 questions") can
     race this invalidation.** It reads `summary` unlocked, then calls the LLM while holding a `PESSIMISTIC_WRITE`
     lock on its own claimed bank rows; a concurrent regeneration's delete can run (or wait) around that call, and
     the `+5` request's LLM-derived rows — built from the pre-regeneration summary — are inserted afterward and
     survive the delete. A real fix needs a generation stamp on bank rows and a migration; tracked as its own
     Backlog row (`docs/product/ROADMAP.md`) rather than folded in here. The same call also introduces a genuinely
     new wait: a regeneration's bank delete can now block up to the LLM read timeout (180s) behind an in-flight
     `+5` call on the same pack, while holding the Study Pack and Note row locks and one of only two
     `studyPackGenerationTaskExecutor` threads. No cross-transaction deadlock was found reachable on the main
     paths (`study_packs` is always locked before the bank, on both sides) — only this bounded-but-long wait.
   - **Pre-existing bug this release widens the blast radius of, NOT fixed here, Backlog row added
     (`docs/product/ROADMAP.md`): `ChallengeQuizQuestionBankService.releaseClaims`'s `REQUIRES_NEW` transaction can
     wait indefinitely on locks its own caller's outer transaction already holds** (v0.60.2; fires on any
     `RuntimeException` in `generateMoreQuestions`, including the ordinary `NotEnoughNewQuestionsException`, not
     only real errors). **Verified against production, 2026-09-27:** `lock_timeout`, `statement_timeout`, and
     `idle_in_transaction_session_timeout` are all `0` (unbounded) — if this ever fires, nothing currently stops
     it. **Also checked 30 days of Render logs for direct evidence: 3 "Apparent connection leak detected" events
     exist, and all 3 trace through `NoteController.listMine` — an unrelated path — not through
     `ChallengeQuizService` at all.** No evidence this specific hang has ever fired; the risk is real but appears
     dormant, not active. **What THIS release widens:** before this commit, a hang here only pinned one learner's
     request and two DB connections; after this commit, a regeneration's new bank-delete call can queue up behind
     the same held lock, so a hang also now blocks that Study Pack's regeneration indefinitely, holding a
     `study_packs`/`notes` row lock and one of only two `studyPackGenerationTaskExecutor` threads. Not fixed in
     this prompt — the naive fix (lock `study_packs` inside `generateMoreQuestions`) would invert `startSession`'s
     own pack-then-session lock order and create a new same-user deadlock; a real fix needs more care than this
     release's scope affords.

5. **Phase D (Question Quality, Claude-direct, documentation/audit ONLY — no code).** Distinct from H4 (which
   verifies a stored answer agrees with its own explanation) and from H5/H6 (representation, not correctness): this
   is whether a generated question has a single defensible best answer at all. Its own Backlog row says there is no
   measured defect rate yet for genuine ambiguity. This phase reads production for one, using the three-tier
   discipline (STRUCTURAL / INTERNAL-CONSISTENCY / SEMANTIC) the original incident doc established, and produces an
   owner decision document: is this worth building, and if so, at which tier. **It ships no code.** Do not let this
   phase drift into an implementation mid-release — if the read makes a strong case, that becomes its own future
   release, not a scope change to this one.
   **Shipped:** `docs/claude-plans/2026-09-27-question-quality-tier3-audit.md` (decision document) and its
   companion `2026-09-27-question-quality-tier3-sample.sql` (the exact sampling queries, with two real bugs found
   and stated rather than smoothed over — the stored keyed answer is `correctIndex`, not `answer`; a discarded
   draft draw is named, not silently dropped). 65 real production questions read across 2 of 4 quiz stores
   (`study_packs.quiz`; `exam_question_pool`'s Board/Long Exam tier, the store the one confirmed historical defect
   came from) — 0 confirmed genuine-ambiguity defects, 2 near-miss patterns noted. **Corrected mid-audit, stated
   plainly rather than smoothed over:** a first draft read the zero-defect sample as "no evidence of an actionable
   rate," which overclaimed — the honest rule-of-three bound (0/65 rules out roughly a 1-in-22 rate, still >5,000
   questions across the corpus if the true rate sits there) rules out a COMMON defect only, not a rare one, which
   is the shape the one historical defect actually had. **Recommendation: do not build an automated Tier 3 gate
   now; scope a learner-facing "flag this question" affordance first** (none exists in the product today, checked
   directly) as the one instrument that scales to a rare-event rate a fixed-size sample cannot resolve —
   explicitly weighed against the incident doc's own rejection of a learner-wide "answers may be wrong"
   announcement on trust grounds, so the owner sees that tension named rather than assumed away. Existing Backlog
   row updated with the outcome rather than duplicated.

**Also found during the Backlog Index survey, NOT part of this release (flagged for a separate doc-correction pass):**
The Backlog row titled "Admin summary/quiz repair paths replace Study Pack content in place with no exam-pool invalidation" is
stale — `AdminStudyPackTransactionHelper.regenerateOnePack` already calls `refreshPool` for both exam modes (`:77-78`). The row
titled "`companionMayBeOutdated` returns false for non-ADMIN callers" is also stale — the guard already lets an adopted copy (`sourcePlanId != null`) through to the real
staleness check (`NoteCollectionService.java:1663-1675`). Both would have been false positives if scoped as work;
neither is touched by this release.

Anti-drift: H4's internal-consistency validator, its retry-then-omit chain, and its MCQ-numeric-choices-only scope
are UNCHANGED — this release only decides what a *new* explanation is allowed to say, not how the answer is
represented (that is H6, removed above) or how H4 grades it.
No structural answer-key validation is added (the original incident's full corpus scan found zero violations of any
kind; still not the fix, still not built). No migration touches `study_packs.quiz`, `exam_question_pool.questions`,
`challenge_quiz_question_bank.question`, or `generated_quizzes.questions`. MULTI_SELECT gets no text-based contract
this release. Phase D produces a decision document only, never code, in this release. The Challenge-bank fix (Phase
C) touches only the regeneration-invalidation boundary, not Challenge Quiz's broader question-selection logic.

**Verification tier (per `CLAUDE.md`'s release-size rule):** three items, within the 3-4-item sweet spot. Phase A0 is
docs-only. `advisor()` before each phase's Codex prompt and on each diff is the baseline. **CORRECTED 2026-09-27,
scoping the Phase C prompt: the trigger fires for Phase C.** It bulk-deletes a learner's own stored
`challenge_quiz_question_bank` rows — including recorded `lastKnownOutcome` history — as a side effect of a
regeneration action, and for an Official-author pack those same deleted rows are the Challenge Quiz templates other
learners' sessions read from (`OfficialChallengeQuizTemplateService.copyTemplateQuestions`). **Read against
production, 2026-09-27: every bank row's `user_id` matches its pack's `owner_user_id` (0 counter-examples across all
31,776 rows) — this is always the pack owner's own data, never a different learner's, so "who does the delete
affect" was verified rather than assumed.** That still changes production-data semantics (deleted outcome history,
and for 890 admin-owned packs with existing bank rows read at the same time — not necessarily all Official
templates, only those additionally passing `isEligibleOfficialTemplate` actually re-seed — a genuine re-seed
dependency on a bounded 8-worker/50-slot executor queue that admits roughly the first ~58 of a run this size and
rejects the rest AT SUBMIT, deterministically, not merely "under load") — the class of change
`v0.143.0`'s own precedent for this shared invalidation shape needed a falsification pass to catch a real deadlock
risk in. **One scoped cold agent (Opus), falsification-framed, runs on the Phase C diff after Codex delivers it,
before merge — not before, since there is nothing to falsify until the diff exists.** Two named targets, not an
open-ended review: (1) row-lock ORDERING AND WAITING between the new bulk `DELETE` and
`ChallengeQuizQuestionBankRepository.findClaimableForUpdate`/`findIncorrectClaimableForUpdate` (both already take
`PESSIMISTIC_WRITE` locks) — not just whether a deadlock is possible (the `v0.143.0` class of bug), but also
whether `ChallengeQuizService.startSession` can hold a bank row lock across its own LLM call while a regeneration's
transaction waits on that same lock while ALSO holding a `study_packs` row lock `LongExamService.startSession`
takes first — a long wait, not a deadlock, but a real contention path; (2) whether the Official-template re-seed
(`AdminStudyPackService` re-fetching note+pack and calling `queueSeedIfEligible` after `regenerateOnePack` returns
`true`) actually fires in practice given the shared `llmParallelTaskExecutor` (core 4, max 8, queue 50) both the
890-pack bulk regeneration AND its own re-seed dispatch compete for — read the diff against
`OfficialChallengeQuizTemplateService.queueSeedIfEligible`'s real behavior and that executor's real capacity, not
the prompt's stated intent. H5 does not touch a shared method, a permission boundary, or production-data
semantics, so it stays on the `advisor()`-only baseline — only Phase C's tier changed.

### Shipped

- **Phase A0 — `ADR-002` ratified.** Status `PROPOSED` → `ACCEPTED`; its own open question (does `board-exam-developer.txt` inherit the letter contract from `schema.json` alone?) resolved by direct grep, not inference, and the resolution written back into the ADR itself. H6's implementation explicitly NOT scoped into this release — see the correction banner above.
- **Phase A (H5) — PR #1455, merged `e0037692`.** All six quiz-prompt files now require an explanation to state a numeric-only MCQ's exact value, still forbidding any letter reference; prose-answer MCQs (98.3% of the corpus) unchanged, an owner decision made after reading the real numeric/prose split. Pre-deploy coverage-ratio baseline read (60/88, 68.2%); post-deploy read minted as a `[CHECKPOINT]` in `ROADMAP.md`'s Backlog Index. Before/after sample review run against the real OpenAI endpoint before merge, per the incident doc's own locked gate.
- **Phase C — PR #1456, merged `9ac11bff`.** `ChallengeQuizQuestionBankService.invalidateForStudyPack` closes the Challenge-bank leg of the derived-artifacts invalidation defect class (`ROADMAP.md`'s "Derived artifacts keyed on the preserved `study_packs.id`" row, both legs now closed). Wired into exactly two of the exam-pool fix's three call sites, not a blind structural copy. A scoped Opus falsification pass on this diff (before merge) found and the release documented rather than fixed: a `generateMoreQuestions` race that can let a narrow window of stale-content rows survive a regeneration, and a pre-existing `releaseClaims` hang (`v0.60.2`) whose blast radius this fix widens — both logged as their own Backlog rows, production verified to have zero configured lock timeouts and no evidence the hang has ever fired.
- **Phase D — PR #1457, merged `cf91ce5a`.** Question Quality Tier 3 audit: 65 real production questions read by hand across 2 of 4 quiz stores, 0 confirmed genuine-ambiguity defects, honest statistical reading (rules out a common defect, not a rare one), recommendation to build a learner "flag this question" affordance before an automated semantic gate. Ships no code, per its own scope. Full document: `docs/claude-plans/2026-09-27-question-quality-tier3-audit.md`.
- **H4 sign-conflation fix — PR #1458, merged `e3625535`.** Found by a scoped Opus falsification pass run at signoff, against the actual merged release state (`cf91ce5a`), not any individual PR's own diff. `QuizValidationUtils`'s exact-value match treated `"0.40"` as present inside evidence text `"-0.40"` — the minus sign was invisible to the pattern — which could mask a real answer/explanation mismatch for any difference-type numeric question (discrimination index, net change, signed error). Pre-existing since `v0.155.0`'s H4, not introduced by H5, but H5 (this same release) makes it more reachable by requiring explanations to state a value at all. Fixed and mutation-verified (reverted the fix, confirmed the new test fails against pre-fix code, restored it).
- **Signoff falsification pass, full report folded into the rows above and into `docs/product/ROADMAP.md`'s Backlog Index** rather than repeated here. Two additional findings, both documented as Known Limitations / Backlog rows, neither blocking: the coverage-ratio metric's denominator (H4's `isNumericUnitLiteral`, ≤20 chars + a digit) is slightly wider than H5's own numeric-condition wording ("not a phrase"), so the ratio cannot reach 100% by design — the post-deploy checkpoint read should say so rather than read a sub-100% result as a defect; and a low-severity, genuinely uncertain race between a Challenge session completing and a concurrent regeneration's bank delete, needing a two-connection Postgres test to resolve, not reproduced.

## v0.161.0 - Scannable Study Plans

**Status: Released** (signed off 2026-09-27; PRs #1452 frontend, #1453 pressure-test fix merged into the release branch; release PR to `main` pending the owner's admin merge)

Theme: make a Study Plan page scannable. A learner opening a plan sees its Sections collapsed until they choose one,
can open or close them all at once, and is told `Not started` instead of `0% · 0 due` when they have no evidence yet.

### Planned Scope

**Scope picked by the owner, 2026-09-26 (at the `v0.160.0` signoff); kicked off 2026-09-27: Degree Study Journeys Phase B (plan §13, §18 Phase B, §9.6), scoped frontend-only at kickoff; a
backend fix landed during pre-signoff pressure testing (see below).** Source: `docs/claude-plans/degree-study-journeys-stage1-architecture-audit.md`. Independent of Phase A, which shipped
in `v0.160.0`. **Every claim below was re-read in code at kickoff, not taken from the plan (the plan's line numbers had
drifted):**

1. **Sections collapsed by default at every breakpoint.** Today the default is seeded once at mount from viewport width
   (`LARGE_VIEWPORT_MIN_WIDTH = 1024`, `collection-detail-page-client.tsx:136`; seeded at `:3131`; applied at `:3484` and the
   two `?? defaultSectionExpanded` render sites `:4183`, `:4256`), so a desktop learner sees every Section of a 312-note plan
   expanded. New rule: collapsed everywhere, with ONE deterministic exception: a plan with exactly one Section expands it.
   Screen width may decide layout density, never how much curriculum a learner must process.
2. **An `Expand all` / `Collapse all` toggle on the Study Plan page.** It does not exist there (grep-verified in
   `collection-detail-page-client.tsx`); the Year builder already has a two-button pair at `study-plan-builder-page-client.tsx:2925`/`:2933`
   that this must not be confused with. One low-prominence text button in the Section
   list header that reads `Expand all` normally and `Collapse all` once every Section is open.
3. **`Not started` at the Section summary row.** The header badge is gated only on `sectionReadiness.total > 0`
   (`SectionCardHeader`, `:310`), so a Section whose notes exist but are all unpracticed renders `0% · 0 due`. `SectionReadiness`
   (`:123`) has no not-practiced count, so it grows one (`aggregateSectionReadiness`, `:230`), and the badge shows `Not started`
   when nothing has been practiced.
4. **`Not started` at the Year/plan summary.** The compact header in the shared `ReadinessSummary`
   (`components/readiness/readiness-summary.tsx:171-177`) renders `0% ready · 0/N mastered · 0 due` for untouched content, while
   the same file already has `isReadinessNotStarted` (`:19`) for per-subject entries. **Verify every consumer of
   `ReadinessSummary` before changing its compact header, and scope the change to the collection pages if others exist.**

**Already built, so NOT scope (verified):** full-row clickable Section headers, the collapsed summary row with note count and
3-title peek, multiple Sections open at once, `aria-expanded` on the header. The Subject-card `Not started` shipped with the
compact card in `v0.160.0`.

**Owner decisions, 2026-09-27:** (a) **Collapsed by default in the READ view; expanded by default on the BUILD surfaces.** ⚠️ CORRECTED post-kickoff: the Year builder does not "start every Subject expanded" — `refreshBuilder({ seedCollapsed: true })` collapses every Subject on first load (`dc9ca58c`, `collapsedSubjectIds` seeded from `nextSubjects`, not empty as first stated at `:1392`), and its toggle pair is at `:2943`/`:2951`, not `:2925`/`:2933`. The owner re-confirmed on 2026-09-27, after this was found, that the Year builder is left unchanged by this release regardless. The
Year builder (`/collections/[id]/builder`, the only build surface a user can reach: the Study Plan page's own organize mode is dormant, `organizeMode` being a constant `false`) keeps
their expanded default and are otherwise unchanged; the `Expand all` / `Collapse all` toggle is added to the Study Plan page in
the read view (organize mode being dormant, the toggle's organize-mode behaviour is not user-reachable). (Cross-section drag is already a no-op, `handleDragEnd` `:3343`, and a note changes Section
through its row's Section control, so nothing here may auto-expand a Section on drag.) (b) **Routing: Codex.** Written as
`docs/codex-prompts/v0.161.0-scannable-study-plans.md` (gitignored); the diff is audited with `/audit-diff` before anything is
committed. (c) **The `Not started` header change is Study Plan pages only for now**; whether it becomes product-wide is to be
judged after this ships (the Progress page's goal card and the note detail page use the same compact header and are untouched).

**Implementor decision:** item 4 is done through an opt-in prop on the compact `ReadinessSummary` header, set ONLY at the two Study
Plan page call sites (Goal `collection-detail-page-client.tsx:3889`, leaf `:4121`), so the other compact call sites (the Progress
page's goal card `app/progress/progress-report-client.tsx:507` and the note detail page
`components/notes/private-note-detail-page-client.tsx:3301`) render exactly as before.

**Explicitly NOT in this release (as scoped at kickoff; a backend fix was added afterward by pre-signoff pressure testing, see Shipped):** persisting expansion state (decided in plan §13.1; it would restore a deep expansion the
learner does not remember making); accordion single-open; the artifact-level `getCollectionLabels(profileType, collection?)`
terminology resolver (plan: only if justified, and nothing here justifies it); any migration or new endpoint; any
Term, Degree or placement-revision work (placement revisions is its own Backlog row); any change to mastery math or
`ConceptHealth`.

Anti-drift: Sections stay a computed grouping (no entity, no table); no new mastery signal at any level; the Year page term
grouping and compact cards from `v0.160.0` are untouched; Review Set rendering must not change beyond the default-collapsed
Sections and the `Not started` wording (five live Review Sets). **Verification:** frontend `tsc --noEmit`, lint and jest;
tests for the default state at BOTH viewport widths (now identical), the single-Section exception, the toggle's label flip,
and a render assertion that `0% · 0 due` never appears for zero evidence at Section or plan grain; mutation-check every new
test and name the killer; a diff that changes behaviour must touch a test that runs it; `advisor()` before the Codex prompt is written, `/audit-diff` on delivery, and `advisor()` on the
diff. The first two Opus passes (frontend behaviour; release housekeeping) and the Codex pass ran while the release was still frontend-only; the Codex pass surfaced the adoption-boundary defect, which was then fixed and re-verified by a fourth, narrower Opus pass scoped to that fix diff.

### Shipped

- Study Plan read views now start Sections collapsed at every viewport width, except that an exactly one-Section plan starts expanded. The expansion logic keeps separate read-view and organize-mode overrides (`useSectionExpansionState`); organize mode is dormant on this page (`organizeMode` is a constant `false`, no setter, and the inline Organize toggle is no longer exposed), so that split is defensive code exercised only by its hook test, and the only build surface a user can reach is the Year builder, which is unchanged and already collapses its Subject blocks on first load (with its own `Expand all` / `Collapse all` pair). Expansion state is deliberately not persisted across reloads.
- A low-prominence `Expand all` / `Collapse all` text toggle on the Study Plan read view controls every Section and is hidden when fewer than two Sections exist.
- Untouched Section readiness badges say `Not started` only when no note in the Section has a completed session (a learner who practiced but never answered a concept correctly keeps the `N% · M due` wording, matching the note rows' `Practiced` label); Sections with some evidence keep the existing `N% · M due` wording, and zero-concept Sections still show no badge.
- The compact readiness header opts into `Not started · N concepts` for an untouched Goal, and for an untouched leaf Study Plan only while `progress.notesPracticed === 0`. The Progress page and note detail page do not opt in and render exactly as before. The Goal header and the termed compact Subject cards (`isSubjectStarted`) have no per-note practice signal, so a Goal or a termed Subject where the learner practiced without ever answering correctly still reads `Not started` — a documented limitation, not exposed today (no live Year carries a term).
- **Pre-signoff pressure-test fix, backend (PR #1453; this widens the release beyond frontend-only):** a child Subject Plan that is `PUBLIC` but has never been published (null `published_at`) is now treated as not found by the anonymous public read and by a standalone `adopt()`, so its term (or title) cannot reach an anonymous reader or a standalone adopter before Publish update. `adoptGoal` now adopts every child that carries a publication stamp, whatever its visibility, instead of requiring `PUBLIC`. A published-but-`PRIVATE` child made a fresh Goal adoption throw *after* the learner's Goal had already been persisted, leaving a half-created Goal a retry could not repair; **confirmed live by two distinct mechanisms, not one:** PNLE's two `PRIVATE` children were created 2026-09-10 (after `V141`) with `published_at IS NULL` and were later stamped by an explicit Publish update on 2026-09-14 without their visibility being flipped to `PUBLIC` — the mechanism the code comments already describe. ALE's seven `PRIVATE` children predate `V141` (created 2026-08-29 and 2026-09-05) and have `published_at == created_at` exactly, the signature of `V141`'s blanket backfill (`UPDATE note_collections SET published_at = created_at WHERE published_at IS NULL`, with no visibility predicate) rather than an explicit Publish update — these were curator drafts that the backfill made look published. Both mechanisms produce the identical symptom in `adoptGoal` and are fixed by the same change: the publication stamp, not visibility, is the boundary. **Since when:** `adoptGoal` has required each child to be `PUBLIC` (by delegating to the public-route `adopt()`) since Goal adoption itself was introduced, well before any publication-stamp concept existed; ALE's child at sibling position 0 has been `PRIVATE` since its creation on 2026-08-29, so the earliest zero-Subject ALE adoptions (2026-08-31, 2026-09-04) predate `V141` (2026-09-08) entirely and cannot be blamed on the stamp filter — the stamp filter (added in `v0.132.0`, PR introducing the publication boundary) only added an ADDITIONAL required condition on top of the pre-existing `PUBLIC` requirement; it never explains the defect's origin. This now matches what the public preview counts and what Official update already delivers (title, description and term of a published `PRIVATE` child). Deploy order is either: the API shape is unchanged, and a never-published child now returns 404 on a direct read or adopt where it previously either leaked or threw mid-adoption.
- Four cold falsification passes ran on this release, in two rounds: while the release was still frontend-only, one Opus pass ran over the whole release AND one Opus pass ran over release housekeeping/docs, in parallel, alongside a separate Codex pass over both v0.160.0 and v0.161.0; the Codex pass is what found the adoption-boundary defect below. After the fix, a fourth, narrower Opus pass ran on the fix diff alone and confirmed it. Findings from all passes are dispositioned in Known Limitations below.

### Known Limitations

- **Existing adopters are not repaired by the adoption-boundary fix.** As of the 2026-09-27 read, 27 of 31 ALE adopters and all 17 PNLE adopters hold fewer Subject Plans than the source now has published (2 ALE adopters hold zero); this count is not split between pre- and post-defect causes and is not itself evidence the defect affected all of them equally, since ALE's own source additions have been growing independently. Re-adopting returns the existing (short) Goal unchanged. `Review update` already offers the missing Subject Plans as additions and was not changed by this release; no backfill was written or run — repairing existing adopters is a separate owner decision, tracked as its own Backlog row.
- **The `Not started` header change is scoped to the Study Plan pages, not product-wide** (owner decision, pending a judgment after this ships): the Progress page's goal card and the note detail page keep the old `0% ready · 0/N mastered · 0 due` wording for untouched content.
- **A Codex finding was refuted, not adopted:** a claim that the frontend-first deploy order was unsafe (an unknown-property rejection) was checked against the live Spring context; the JSON converter is a Jackson 3 `JsonMapper` with `FAIL_ON_UNKNOWN_PROPERTIES=false`, so either deploy order remains safe.
- **Section grouping keys on the trimmed label only**, not the canonical whitespace-collapsed form `docs/features/collections.md` describes elsewhere; two legacy labels differing only in internal whitespace render as two Sections. Pre-existing, unrelated to this release's changes; not fixed here.
- **The SMALLINT term-order ceiling (32767) has no dedicated guard** in the frontend combobox or the pipeline builder; reaching it needs an impractical number of terms per Year and is not fixed in this release.


### Checkpoint gate

No `[CHECKPOINT — due YYYY-MM-DD]` row was minted for this release. Nothing shipped ahead of its own evidence: the collapse/toggle/`Not started` scope was owner-decided against re-read code, and the adoption-boundary fix was verified against production reads (the ALE/PNLE stamped-but-private shapes, the note-visibility check, and the mechanism split above), not shipped on a bootstrap argument.

### Post-deploy verification owed

`SELECT count(*) FROM note_collections a JOIN note_collections r ON r.id = a.source_plan_id WHERE a.parent_collection_id IS NULL AND r.parent_collection_id IS NULL AND a.created_at >= '<deploy timestamp>' AND NOT EXISTS (SELECT 1 FROM note_collections k WHERE k.parent_collection_id = a.id);` — expected 0; a `STUDY_GOAL_ADOPTED` analytics event does not fire on the failing first attempt (it throws before `trackStudyGoalAdopted`), only on the harmless retry, so this reads the actual row shape rather than the event log. Also run `scripts/check-deploys.sh` (both Vercel and Render matter for this release) at least five minutes after the merge, and confirm `v0.160.0`'s still-unverified Vercel deploy while there.

## v0.160.0 - Study Plans by Semester

**Status: Released** (signed off 2026-09-26; PRs #1447 backend, #1448 frontend, #1449 pipeline, #1450 pressure-test fixes merged into the release branch; release PR merged as #1451 and tagged. Backend deploy verified 2026-09-27 by a read-only query through Render: `V150` applied 2026-09-26T16:06:30Z, success. The Vercel side and `scripts/check-deploys.sh` were NOT run)

Theme: let a curator place each Subject Plan in an academic term, so a Year reads as a semester-by-semester study
plan, without adding a level to the collection hierarchy and without touching any Note.

### Planned Scope

**Scope picked and release shape confirmed by the owner, 2026-09-26: Degree Study Journeys, Phase A0 and Phase A.**
Source: `docs/claude-plans/degree-study-journeys-stage1-architecture-audit.md` (decision-complete feature plan;
**§21 is the implementor handoff, §18 the phases and decisions A-F, §22 confirms no owner decisions remain**).
The learner-facing promise is **"BS Computer Science - 1st Year Study Plan"**, never a complete Degree Study Journey.

- **Phase A0 (documentation first, Claude-direct):** `docs/architecture/ADR-003-curriculum-placement-and-hierarchy-depth.md`
  (decisions A-F exactly as enumerated in plan §18; ADR-002 is taken by the quiz-answer-identity proposal), plus the
  `docs/features/collections.md` update. No application behaviour.
- **Phase A (implementation, Codex in slices: backend, then frontend, then pipeline):**
  1. Two nullable columns on `note_collections`, `term_label VARCHAR(60)` and `term_order SMALLINT`; one additive
     migration, no backfill, no index.
  2. Persistence, DTO, service and **adoption preservation** of the term.
  3. Curator term assignment in the Year builder: a combobox over terms already used in that Year, never raw freetext.
  4. Year-page conditional term grouping (all-NULL flat / all-placed grouped / mixed with a trailing `Term not specified`).
  5. Compact Subject cards on the Year page (title, note count, ONE progress signal), gated on the SAME condition as
     term grouping (any non-null `term_label`), no count threshold.
  6. `academic_term` in the curriculum pipeline: extend `review-set-workbook-spec.md` and `build_review_set_workbook.py`
     and regenerate; never hand-add the column to a generated workbook.
  7. The regression and invariant tests in plan §21.5.
- **Endpoint form (decided here, plan §21.9):** extend the existing collection update with two OPTIONAL fields. That is
  additive in both directions (optional on request, nullable on response), so frontend and backend may deploy in either
  order, and **the release notes must say so explicitly.** If a new endpoint is added instead, that stops being true and
  the release owes a deploy-ordering statement and a real-request `MockMvc` test with `.contentType(MediaType.APPLICATION_JSON)`.
- **Backend Academic Term slice:** migration `V150` adds nullable `term_label` / `term_order`; the existing collection
  PATCH accepts optional `termLabel` / `termOrder`; `persistAdoptedPlan`, `createSubjectAddition`, and the `adoptGoal()`
  re-parent branch carry child placement; and the Goal-child plus owned/public detail DTOs expose it. The PATCH fields
  are optional on request and nullable on response, so frontend and backend may deploy in either order.
- **Frontend Academic Term slice:** `lib/collection-terms.ts` holds the single `hasTermPlacement` gate that drives BOTH
  Year-page term grouping and compact Subject cards (no count threshold); the Year page renders ordered static term
  headers with a subject count and an in-progress count (shown only when above zero), a trailing `Term not specified`
  group in the mixed case, and compact cards (title, note count, ONE of `N% ready` / `Not started`); with every child
  term NULL the existing full-size grid is byte-for-byte unchanged. The Year builder gains a per-Subject term combobox
  over the Year's existing terms (a new label is allowed; the order is assigned, never typed). The PATCH fields it sends
  are optional on request, so this slice also deploys in either order relative to the backend.
- **Pipeline Academic Term slice:** `build_review_set_workbook.py` accepts an OPTIONAL `academic_term` column, constant
  per plan, validated per Study Plan: unused for all Subject Plans or assigned to all of them, and a partial
  assignment is refused with an error naming the Study Plan and the unassigned Subject Plans (also refused: mixed
  values inside one plan, over 60 characters, `Term not specified`, and case/spacing-variant duplicates). The term
  order is derived from first-seen file order and printed in the workbook. With no terms the output is unchanged:
  ALE, CPALE, LET and PNLE were rebuilt with the old and new builder and compared on cell values, fonts, fills, borders, merges, column widths, row heights and freeze panes: identical. The new `docs/curriculum/test_build_review_set_workbook.py` runs by hand in the venv and is NOT in CI.
  `docs/curriculum/review-set-workbook-spec.md` and the strategist module `docs/gpt-contexts/REVIEW_SET_SHAPING_CONTEXT.md`
  now carry the column, and the strategist module's TSV header was corrected to include `applicable_programs`, which the
  builder has required since 2026-09-10 (a contract drift, not a behaviour change).
- **Phase B (collapsed-by-default Sections, and so on) is NOT in this release**; it has no dependency on Phase A and rides
  in a later one. **Phase C (a Degree entity and landing page) is out.**
- **⚠️ A gap in the plan, found and verified in code at kickoff (and since corrected in the plan, §7.1a), that the
  implementation MUST close:** the plan's adoption invariant named only the adoption path, but a child Subject Plan copy
  is built field by field in TWO places. `persistAdoptedPlan` (`NoteCollectionService.java:1956-2017`) serves BOTH `adopt()`
  and `adoptGoal()`, because `adoptGoal` creates each child by calling `adopt()` and then re-parents it. The SECOND is the
  Official-update addition, `createSubjectAddition` (`:2476-2515`, `CreatedSubjectAddition`), which copies title,
  description, course program, learner level, estimated hours and the source-sync fields and, without the term, lands a
  Subject added to an already-adopted Year with a NULL term next to siblings that have terms. That manufactures the mixed
  state the plan calls a curator-quality defect and shows a `Term not specified` group with no curator involved. The term
  must be carried at BOTH, each with its own test that fails when it is dropped. **`persistAdoptedGoal` (`:2019-2057`,
  the root copy) must NOT get the term**: a root has no parent and therefore no term placement, so adding it there would be
  a silent dead column, not a fix.

Anti-drift (plan §21.2 and §21.4, binding): with every child term NULL the existing Review Set and Goal rendering is
UNCHANGED, with no term headers, no `Term not specified` group, and FULL-SIZE cards, protecting five live Review Sets
(PNLE, CPALE, ALE, LET, Civil Engineering); density is gated on the same condition as grouping and never on a count;
Official update stays additive-only forever; NO Degree progress in any phase (a permanent product rule); academic
placement never touches the Note and `applicable_programs` is never overloaded to carry a term (ADR-001); the hierarchy
stays at exactly two persisted levels; no Degree landing page and no `journey_key`/`journey_order` fallback, no Degree
entity, no whole-Degree adoption, no learner curriculum customization, no term entity/catalog/enum, no collection
`type`/`kind` enum, no change to `ConceptHealth`. Subject Plans are NOT reusable across Degree Journeys; only canonical
Notes are. **Verification:** `advisor()` BEFORE the Codex prompt is written and on each diff; a diff that changes
behaviour must touch a test that runs it; mutation-check every new test and name the killer; the all-NULL regression must
assert full-size cards; frontend `tsc --noEmit`, lint and tests plus the full backend build with Docker; and, because the
release touches the adoption engine and five live Review Sets, ONE scoped Opus cold agent framed as falsification of
invariants 1, 2 (every copy site), 3 and 4 before signoff. Seven items is a large release; say what that does to the
verification tier if more is folded in. **Owner-side, not this release's work:** the Note Strategist keeps authoring
Subject to Section to Note; Year and term placement stays in a separate editorial file until the pipeline extension ships.

### Shipped

**Status: Released 2026-09-26 on `releases/v0.160.0`; the release PR to `main` is the owner's admin merge and its auto-deploy runs `V150`.**

**Scope disposition (every Planned Scope item, checked against code):**

| Item | Disposition | Evidence |
|---|---|---|
| Phase A0: ADR-003 (decisions A-F) and `collections.md` | **Shipped** | `docs/architecture/ADR-003-curriculum-placement-and-hierarchy-depth.md`; amended 2026-09-26 from "two" to "three" child-copy sites, decisions unchanged (owner-approved) |
| 1. Two nullable columns, one additive migration | **Shipped** | `V150__collection_academic_term.sql` |
| 2. Persistence, DTO, service, adoption preservation at BOTH child-copy builders (+ a third) | **Shipped, with one addition** | `persistAdoptedPlan` (`NoteCollectionService.java:2020`), `createSubjectAddition` (`:2537`), and the `adoptGoal()` re-parent branch (`:1161`), which the plan missed; NOT `persistAdoptedGoal` (`:2058`) |
| 3. Curator term assignment (combobox) | **Shipped, changed** | `frontend/components/collections/subject-term-control.tsx`. Changed: hidden on adopted copies, disabled on published rows, partial-term warning |
| 4. Year-page term grouping | **Shipped** | `hasTermPlacement` (`frontend/lib/collection-terms.ts:36`) at `collection-detail-page-client.tsx:1764` |
| 5. Compact Subject cards on the same gate | **Shipped** | `CompactSubjectCard`, no count threshold |
| 6. `academic_term` in the pipeline | **Shipped** | `resolve_terms` (`docs/curriculum/build_review_set_workbook.py:99`); no existing workbook needed regenerating (none uses terms) |
| 7. Regression and invariant tests | **Shipped** | all-NULL full-size regression, one test per carry site, PATCH round-trip with a real MockMvc request, grouping, pipeline tests |
| Phase B / Phase C | **Not in this release, by owner decision** | Phase B is the next release's scope (Backlog row); Phase C is out |

**Changed mid-release, by owner decision (2026-09-26), not in the kickoff scope:**
- **Partial term assignment is invalid authoring input**, enforced by the pipeline builder and by refusing to publish a partially-termed Year (first publication and Publish update).
- **A term obeys the Official publication boundary by being settled before it.** It can change only while its Subject Plan is unpublished (own `published_at` AND the root's `last_update_published_at` both set means frozen; `published_at` alone is not enough because V141 stamped every pre-existing row). An adopted copy can never change a term and the builder hides the control there. Moving a published Subject that carries a term, or that joins a termed Year, is refused.
- Owner rejected documenting the original Finding 1 (terms reaching learners around the publication boundary) as a permanent limitation; option A above was chosen over a placement-revisions redesign of the Official update engine, which is logged in the Backlog Index as its own future release.

**Deploy order: either.** Both new request fields (`termLabel`, `termOrder`) are optional on the request and nullable on the response, and `termLocked` is an added response field the frontend treats as absent-means-unlocked. A frontend-first deploy sends nothing the old backend rejects (Spring Boot 4 / Jackson 3 ignores unknown properties, which was read, not run against a live backend), and a backend-first deploy serves fields the old frontend ignores. **`V150` is additive and nullable (`ADD COLUMN` twice, no default, no `NOT NULL`, no index).**

**Verification:** full backend build with Docker 2557 tests, 0 failures (the PostgreSQL 16 harness applies `V150`); frontend `tsc --noEmit` clean, lint 0 errors, jest 224 suites / 2533 tests; every new test mutation-checked with the killer named in the PR threads (#1447, #1448, #1449, #1450). The pipeline unit tests (`docs/curriculum/test_build_review_set_workbook.py`, 13 tests) run by hand in the venv and are NOT in CI. ALE, CPALE, LET and PNLE were rebuilt with the old and new builder and compared on cell values, fonts, fills, borders, merges, widths, row heights and freeze panes: identical. Civil Engineering is still refused for lacking `applicable_programs`, as before.

**Pressure test (two scoped Opus cold agents, framed as falsification, plus `advisor()` before the prompt and on each diff).** The first ran over the whole release: no blocker; two SHOULD-FIX (a Continue/hero target that could differ from the first card shown in a termed Year, fixed; the system-created half-termed learner Year, resolved by the publication-boundary decision above) and notes. The second ran on the freeze and publish guards: no blocker; two SHOULD-FIX (the re-flip named a subject the curator could not change, fixed; moving a published Subject with `updateParent` could leave it un-termed and permanently locked in a termed Year and block every later Publish update, fixed by refusing that move) and notes. Both agents confirmed the all-NULL invariant, the copy sites, additive-only Official update and the transport names.

**Known limitations (documented, owner-visible):**
- **Retroactive term introduction or rename on an already-published Year is UNSUPPORTED.** The freeze makes those cases unreachable rather than merged, and the five live Official Review Sets can never gain terms (their children are published under a stamped root; delete-and-recreate is the only route and existing adopters keep old copies). Backlog row: *Official update: placement revisions*. Do not build it speculatively.
- **The in-flight BSCS Year 1 file is a single `plan_no` with the subjects as sections, so it cannot carry per-Subject terms until it is reshaped to one `plan_no` per subject.** It is another session's untracked file and was not touched.
- The `Term not specified` reserved name is rejected in the builder and pipeline only; the backend does not reject the string, so a direct API call can store it (the page renders it without error).
- A `termOrder` beyond the 32-bit integer range, or a non-number, returns 500 from the catch-all handler (pre-existing behaviour of every `Integer` PATCH field); values from 32768 up to the int maximum return 400.
- `termLocked` covers the freeze rule only, not the adopted-copy rule: a standalone adopted plan nested under a learner's own Goal by direct API would show an enabled control the backend refuses (the optimistic update rolls back).
- A label of only NBSP is stored by the backend (Java `trim`/`isBlank`) though the frontend treats it as unplaced; the create option ("Use X") saves on blur rather than on click. Neither is reachable from normal use.
- The `Term` control also shows on a non-admin's own (non-adopted) Goals, with a warning that says a partially termed plan cannot be published; those users cannot publish. Not scoped further.
- Non-admin-owned public collections that existed at V141 may have permanently locked children (V141 stamped their root); sized by the post-deploy read below.

**Post-deploy verification (read-only `SELECT`s, run 2026-09-27 through the reconnected Render MCP):**
1. `SELECT count(*) FROM note_collections WHERE term_label IS NOT NULL;` **= 0** (as expected; the column exists, `V150` applied 2026-09-26T16:06:30Z).
2. `SELECT count(*) FROM note_collections c JOIN users u ON u.id = c.owner_user_id WHERE c.last_update_published_at IS NOT NULL AND u.role <> 'ADMIN';` **= 0**, so the non-admin locked-children limitation above affects no existing row.
3. `scripts/check-deploys.sh` was NOT run (no Render API key in the session); the backend deploy is evidenced by `V150` above, the Vercel deploy remains unverified.

**Checkpoint gate: none minted.** Everything shipped was owner-decided and none of it was gated on evidence; there is no instrumentation to read and a checkpoint without a metric is decorative. Real usage of the term feature will first be visible when a curator terms the BSCS Year, so the honest follow-up is the Phase B kickoff read, not a dated checkpoint.

## v0.159.0 - Nothing Lost in the Batch

**Status: Released** (signed off 2026-09-25; Release A merged as #1444; release PR merged as #1446 and tagged; deployed and verified: Render live 15:24Z, Vercel production 15:28Z)

Theme: stop batch operations losing their result. A bulk generation that fails topics leaves no trace once its
consume-once receipt is read or swept, and a bulk regeneration finishes with no signal at all. Also close the one
evidence question this project still owes an answer on: what the 5 s connection timeout is doing to users.

### Planned Scope

**Scope picked by the owner at kickoff (2026-09-25): Notifications Release A, and the `connection-timeout` follow-up.**
Source for item 1: `docs/claude-plans/learning-relevant-notifications-stage1-plan.md` (audit and plan, written
2026-09-24, NOT yet owner-approved to build; §12 is the release slice, §14 the decisions). **Every production
figure in that plan is a 2026-09-24 snapshot and one had already decayed; re-read before any of it reaches a
prompt.**

0. **PREREQUISITE, OWNER DECISION, NOTHING IS BUILT UNTIL IT IS MADE: the badge/retention flag split.**
   `NotificationCategory` (`entity/NotificationCategory.java`) derives badge eligibility and retention expiry from
   ONE boolean as exact complements, so a completion notification cannot be both badge-eligible and
   retention-expirable. The plan recommends option (c): split into `badgeEligible` and `retentionExpirable`, add
   `ASYNC_RESULT(true, true)`, and REWRITE (not delete) the two XOR partition tests. Java-only, no migration; it
   deliberately changes a documented invariant, which is why it is the owner's call.
1. **Notifications Release A: async completion for BULK operations only (backend, no migration, no API/DTO/frontend
   change).** Two new `NotificationType` values (`BULK_GENERATION_INCOMPLETE`, `BULK_REGENERATION_COMPLETE`), one
   new `NotificationCategory` (`ASYNC_RESULT`), one producer service, two call sites:
   `NoteBulkGenerationService.java:247-274` inside the existing `finally`, after the `recordResult` block, only
   when something failed; `NoteBulkRegenerationService.java:439-443`, deliberately NOT in a `finally`. Destination
   `/library`; dedup on `resultId` / `batchId`. ~9-11 files; routed to **Codex** (write the prompt from the plan,
   with `advisor()` BEFORE it is written and on the diff, then `/audit-diff`). Still-open plan decisions
   (§14): ship both triggers or one (recommend both), the failure-copy truncation budget (recommend ~850 chars then
   "and N more"), and whether a zero-accepted or all-quota-blocked batch delivers nothing or the failure form.
2. **`connection-timeout: 5000` follow-up.** (a) A proper read-only 500-cause read: classify the 500s by cause
   (pool timeout vs database I/O drop vs other) over the retained window, since the 2026-09-24 read sampled only
   the newest 30 log lines. (b) The owner's verdict against the row's kill criterion, which has no numeric
   "material and sustained" threshold, so the owner sets it. (c) Any resulting change (revert to 30 s, or a
   structural fix on pool holds) is its own owner-scoped item, never an inline fix.
3. **One doc correction, found by the plan's audit and verified in code:** `CLAUDE.md` names
   `NoteService.startAsyncGenerationFromNote()`, which does not exist; the real entry point is
   `StudyPackService.startAsyncGenerationFromNote` (`StudyPackService.java:171`).

Anti-drift: no single-note notification of any kind (the learner is on a page polling every 3 s); NO "Study Packs
are ready" notification for bulk generation (the count it would use over-reports, plan §1.1); no per-item
notifications, presence, websocket or SSE; no retry promise in the copy (the receipt is consume-once); never state a
reconciled "N of M" count; never add a `finally` to `NoteBulkRegenerationService.processBatch` (`:64-65` forbids
it); never call `deliver()` inside a transaction; no migration, endpoint, DTO field or `notification-inbox.tsx`
change; Release B (learning continuity) is DEFERRED, not scheduled, and `RetentionEmailType.UNFINISHED_NOTE` stays
untouched. No retention Stage 3; do not cap or reorder `INACTIVITY` here (its effectiveness and the budget-starvation
rows are separate owner decisions). The notes and curriculum files other sessions left untracked are not this
release's. **Verification:** a diff that changes behaviour must touch a test that runs it, so both call sites need
a test that executes them; mutation-check every new test; one `advisor()` on the diff (no permission, money or
production-data semantics change), escalating to one scoped falsification agent only if delivery introduces a defect
the same session then fixes.

### Scope disposition (signoff, 2026-09-25)

- **Prerequisite decision (badge/retention flag split): DECIDED and SHIPPED**, option (c).
- **Notifications Release A: SHIPPED** (#1444). Both triggers; 850-character topic budget; an all-quota-blocked batch
  delivers the quota form. Anchors: `NoteBulkGenerationService.java:296`, `NoteBulkRegenerationService.java:449`,
  `NotificationCategory.java:14-16`, `BulkOperationNotificationService.java`.
- **`connection-timeout` follow-up: PARTLY DONE.** (a) the 500-cause read is DONE, recorded below and on its Backlog
  row; (b) the owner's VERDICT is NOT made and carries forward on the row; (c) nothing was changed, by design.
- **`CLAUDE.md` entry-point correction: SHIPPED** in the kickoff commit (`StudyPackService.java:171`).
- **Not from the scope list, left open by the owner's call:** the `[CHECKPOINT — due 2026-09-27]` click/open read and the
  2026-09-28 publication-boundary read.

### Checkpoint gate

Release A shipped ahead of its own evidence (one user drove regeneration; bulk generation volume had no direct metric),
so it owes a checkpoint, added in this signoff commit: `[CHECKPOINT — due deploy + 30 days, backstop 2026-11-10]`
with a kill criterion, a denominator clause, and the `notifications` table as the instrument. The instrument is the
table, and the first production row is what proves it emits; both call sites are exercised by mutation-checked tests.

### Known limitations

- The regeneration call site has no try/catch of its own; the producer swallows every delivery failure and a test
  (mutant M10) guards that, so an escape could only come from a future change to the producer.
- The notification copy (exact titles and bodies) was drafted by the release and not separately reviewed by the owner.
- The 500-cause read is a subagent's report, not independently re-run; application logs only go back to
  2026-09-18 05:40Z, so ~350 of 439 500s (2026-09-04..09-18) cannot be attributed, and its log event count (68) exceeds
  the metric 500 count (62) by ~5 unexplained.
- The bulk generation RECEIPT still marks every accepted topic failed after an interruption (the outer catch), including
  notes already created; only the notification was corrected. Whether the row actually persists during a real shutdown
  was not verified.
- Verification: `advisor()` before the Codex prompt and on the diff, mutation checks (21 killed), and one Opus cold
  agent as a scoped falsification pass. No authorization, money or production-data semantics changed, so no full
  three-agent test was warranted.

### Shipped

- **Bulk-operation in-app results.** Notifications now keep badge eligibility and unread expiry as
  independent category policies. Failed or capacity-blocked bulk generation records its topic strings in
  one bounded notification and stays silent on success; normally completed bulk regeneration sends one
  completion notification, while an interrupted run sends none. Unit, call-path, badge/retention, and
  real-database length guards exercise these claims.
  Copy is fixed and exact (`docs/features/notifications.md`); bodies are bounded to an 850-character topic
  budget in code, and the regeneration retry mints its own batch id so it notifies too. The pre-commit audit
  found and fixed a contradiction in `notifications.md` (it still said every unread actionable row is retained
  forever, which is false for `ASYNC_RESULT`) and a test gap (nothing pinned the dedup id of either trigger).
  13 of 13 planted mutants were killed at first, each by a named test. **⚠️ That figure overstated the guard:** the
  pressure test below found five mutants the merged suite did not kill (regeneration count arguments, separator
  budget accounting, mixed-case suffix, one-per-group interleave); all are killed now (21 in total).
- **Pre-signoff pressure test (one Opus cold agent, isolated worktree, framed as falsification) and its fixes, PR #1445.**
  It held nine claims and broke three, plus test overstatement and doc defects; each was verified in code before it
  was fixed. **(1)** An interrupted or failed-before-loop bulk generation notified that EVERY accepted topic failed,
  including notes already created: the delay between items throws outside the per-item try and the outer catch
  overwrites the lists. The notification now lists only topics that were NOT created (the receipt keeps the older
  behaviour, see Known limitations). **(2)** In the mixed failed and quota-blocked case "and N more" attached to the
  quota list although the omitted topics could all be failed ones; it is now `Plus N more not listed.` after both
  sentences. **(3)** Regeneration copy claimed Study Packs were "unchanged" although a timed-out item may still succeed;
  it now says they still work. **(4)** Five mutants survived the merged suite; new tests kill them. **(5)** Doc defects:
  a self-contradicting ROADMAP row, a checkpoint SQL that omitted dismissals from its own kill criterion, and a
  rationale that ignored the polling regenerate modal. The full build passed (2,527 tests) and all 21 mutants are killed.
- **`connection-timeout: 5000` 500-cause read (read-only, no code change).** Application logs are retained only from
  2026-09-18 05:40Z. In the observable week: ONE real saturation cluster (09-18 14:46-14:48, 34 requests, pool 20/20,
  peak waiting 6); pool timeouts on 09-18 16:03 and 09-22 06:04 that followed database I/O drops with a collapsed pool;
  26 database I/O drops in bursts on the first requests after a deploy goes live; 33 client-abort broken pipes not counted
  as 500s; one 405 logged as a 500; one unknown. The 5 s timeout produced 500s in one incident; most other 500s are
  deploy-time DB drops it does not cause. The verdict remains the owner's; a deploy-time-burst finding has its own row.

## v0.158.0 - Reading the Evidence

**Status: Released** (signed off 2026-09-25; merged as #1443 and tagged; deployed and verified: Render live 06:30Z, Vercel production 06:33Z)

Theme: discharge the evidence reads this project already owes (three overdue checkpoint reads and the first
readings of the retention instrumentation) before any new feature scope is chosen, plus one ready one-line fix.

### Planned Scope

**PROVISIONAL: this release was kicked off without an owner scope pick. Amend this list before any
implementation.**

- **Three overdue checkpoint reads (read-only).** Each row's own kill criterion stays authoritative, and a fired
  criterion becomes its own owner-scoped item rather than being fixed inline.
  - `[CHECKPOINT — due 2026-09-22]` publication boundary (`docs/claude-plans/v0.132.0-publication-boundary-checkpoint-read.sql`):
    if it shows stranded curriculum, the response is to build F5, a publication surface in the Builder.
  - `[CHECKPOINT — due 2026-09-18]` `connection-timeout: 5000`: read Render `http_request_count` by `statusCode`
    for the 14 days after deploy against the pre-deploy window; if 5xx is material and sustained, revert to the 30 s default.
  - `[CHECKPOINT — due 2026-09-19]` Learning Connections demand: `linked_learner_relationships` grouped by status.
- **Retention instrumentation readings (`v0.157.0` follow-through, read-only).** (a) The first daily run after
  deploy: `retention.email.*.dispatch` logs show digest, then weak-concept, then `INACTIVITY`, with `INACTIVITY`
  near 40-47. (b) `[CHECKPOINT — due 2026-09-27]`: click/open tracking is emitting. (c) Re-date the retention
  checkpoint rows if the real deploy date matters. Owner prerequisite: Resend click and open tracking and the
  `email.clicked`/`email.opened` webhook events.
- **`RetentionEmailScheduler.runMonthly()` zone pin (backend, one line plus a test) — DONE and MERGED into this branch (PR #1442, `540b866b`); full backend build green, 2,511 tests.** Pin it to `Asia/Manila`
  like `runDaily`/`runWeekly`; the Backlog Index row has the detail. Routing: Claude-direct on its own branch and
  PR into this release branch (isolated, one file).
- **`INACTIVITY` email effectiveness: OWNER DECISION PENDING (evidence read, added 2026-09-24 at the owner's
  request; no implementation).** A read-only production read found 4,466 `INACTIVITY` sends to 235 users
  (2026-04-22 to 2026-09-23), 222 of them sent 10 or more, max 44, while only 6 of 408 accounts logged in during
  the last 7 days. Return rate after a send (any `analytics_events` row within 7 days, sends at least 7 days old):
  1st send 4.7%, 2nd 2.2%, 3rd-5th 1.3%, 6th-10th 1.0%, 11th+ 0.7%; 20 of 235 emailed users have any recorded event
  after their first send. **Limits: no control group (some return unprompted, so lift is lower than shown), "return"
  is any analytics event and may miss a plain login, and `marketing_emails_enabled` is on for 0 users with the
  consent basis of these sends unchecked.** This is evidence against the rationale for "do not cap `INACTIVITY`'s
  share (gated on opt-in growth)"; the rule itself is the owner's and is unchanged until the owner decides. Options
  to scope if wanted: cap sends per user, stop after N unanswered emails, or check the consent basis first. Any
  change is its own owner-scoped item, not an inline fix.

Anti-drift: no retention Stage 3 (it waits on the three retention `[CHECKPOINT]` rows); do not cap `INACTIVITY`'s
share (gated on opt-in growth); do not reorder retention dispatch (`docs/features/retention-emails.md`); nothing
here is feature scope. Choose feature scope explicitly.

### Scope disposition (signoff, 2026-09-25)

- **Three overdue checkpoint reads: SHIPPED as reads**, results above and on each Backlog row. Publication
  boundary not fired (re-dated to 2026-09-28); Learning Connections kill criterion does not fire; `connection-timeout`
  inconclusive, owner decision.
- **Retention readings (a) first daily run: SHIPPED**, as designed. **(b) `[CHECKPOINT — due 2026-09-27]`: NOT DONE,
  by the owner's call** — it is read on or after 2026-09-27 and stays an open Backlog row. **(c) re-date the retention
  rows: NOT NEEDED**, the real deploy was 2026-09-24 as assumed.
- **`runMonthly()` zone pin: SHIPPED** (#1442, `RetentionEmailScheduler.java:79`, guard `ScheduledJobCronContractTest`).
- **Added mid-release, owner-requested:** the `INACTIVITY` effectiveness evidence item and indexing the notifications
  Stage 1 plan; both have Backlog rows and are undecided.

### Checkpoint gate

Nothing in this release shipped ahead of its own evidence, so no new `[CHECKPOINT]` row is owed. The open
checkpoints are all carried from earlier releases and are re-stated on their rows.

### Known limitations

- The `connection-timeout` read is inconclusive (10-day pre-window, traffic growth, `v0.116.0`/`v0.123.0`
  confounds, log sample was the newest 30 lines only).
- The `INACTIVITY` return-rate read has no control group and measures any analytics event.
- Verification tier: one small code change with no authorization, money or production-data semantics, so a single
  `advisor()` pass rather than a pressure test.

### Shipped

- **`RetentionEmailScheduler.runMonthly()` zone pin** merged as PR #1442 (`540b866b`); full backend build green.
- **Checkpoint reads, run 2026-09-24 (read-only production and Render reads; results are also on each Backlog row).**
  - **Publication boundary (due 2026-09-22): NOT FIRED, re-date to 2026-09-28.** One public Review Set, `CPALE
    Comprehensive Review`, holds 325 unpublished topics and has never been published since `V141` (only the
    backfill stamp). Its oldest unpublished row is 2026-09-14, 9 days old, under the 14-day threshold; it crosses
    on 2026-09-28. `LET` (2026-09-10) and `PNLE` (2026-09-14) were really published, so the control is being used.
  - **Learning Connections demand (due 2026-09-19): kill criterion does NOT fire; re-read at the next release.**
    1 `ACCEPTED` relationship, unchanged since 2026-09-05; 2 invitations (1 `ACCEPTED`, 1 `PENDING`); no new
    activity in 19 days. One pair is weak evidence and may be a test pair.
  - **`connection-timeout: 5000` (due 2026-09-18): INCONCLUSIVE, leaning concerning, owner decision.** 500s went
    from 25 in the 10 available pre-window days (about 0.07%) to 352 in 09-05..09-18 (about 0.27%), with spikes on
    09-17 (59) and 09-18 (68); 502s peaked at 329 on 09-17. Assumptions: deploy taken as 2026-09-04; Render keeps
    only 30 days so the pre-window is 10 days; traffic also grew. Logs: 09-18 14:48 genuine pool saturation
    (`total=20, active=20, waiting=4`); 09-18 16:03 and 09-22 06:04 pool timeouts following Postgres I/O errors
    (pool collapsed to 7 then 2), which looks like the DB dropping rather than load. Only the newest 30 log lines were
    read, so this is not a count. `v0.116.0` and `v0.123.0` confound it. The row's "material and sustained" has no
    number, so the kill criterion was not applied.
  - **First `v0.157.0` retention run, read 2026-09-25 (fired 2026-09-24T18:45Z = 02:45 Manila): AS DESIGNED.** Dispatch
    order was digest (18:45:06.497), then weak-concept (18:45:06.530), then `INACTIVITY` (18:45:23.422), about 17 s
    total, no errors, one instance. Digest `budget=60 attempted=14 sent=14 skippedForBudget=0`; weak-concept
    `budget=46 attempted=0`; `INACTIVITY` `budget=46 sentToday=14 attempted=46 sent=46 skippedForBudget=75`, so it
    landed inside the expected 40-47 band instead of the old pin at 60. `email_log` agrees (14 `DUE_CONCEPTS_DIGEST` +
    46 `INACTIVITY` = 60, the full shared budget). The pre-deploy baseline was the 2026-09-23T18:45Z run on
    `v0.156.0` code: `inactivity budget=60 sent=60`, `dueConceptsDigest=23`, no per-type dispatch lines. The digest
    due-count differs day to day (14 vs 23), so the two are not a like-for-like "14 of 23". `email_log.clicked_at` is
    still 0 (click tracking not enabled yet); `email_open_daily_counts` had 6 (09-23) and 3 (09-24) before this run. The open date is Resend's own event `created_at` in UTC (`ResendWebhookService.java:123`), so opens dated 09-23 that arrived after the 02:39Z deploy are late delivery, not a dating bug.
    `[CHECKPOINT — due 2026-09-27]` remains open. 75 eligible learners were skipped for budget; see the
    `INACTIVITY` effectiveness item above for whether that matters.
  - **Cross-note review re-check:** `quick_review_sessions` 906 total, `source_collection_id` NULL on all 906
    (179 since the Stage 1 audit); DEFER stands, gate is `[CHECKPOINT — due 2026-10-13]`.
