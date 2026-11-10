# Board Exam / Long Exam follow-up: scope proposal (C6)

Status: **PROPOSAL, awaiting owner decisions D1–D6.** Nothing here is approved or built. Written 2026-10-26 by the
Release Implementor from the code on `main` (`v0.175.0`, `06a09f7c`) and the docs named in each claim.

Source decision: C6 (`docs/claude-plans/2026-10-19-v0174-v0175-consolidated-implementation-plan.md:410`): "Board/Long Exam
'press Start first' plus expiry sweep … their own follow-up release right after v0.175.0". The Long Exam pool reduction
(A4, `docs/claude-plans/2026-10-19-owner-final-decisions-v0174-a1-a7.md:62`) was deferred to v0.175.0 and then left out of
it; it is `LONG_EXAM_POOL_SIZE` = 48 today (`application.yaml`, `StudySnapProperties.java:203`).

## 1. What is true today (anchored)

| Claim | Evidence |
|---|---|
| A Board Exam's timer starts the moment generation finishes, wherever the learner is. | `ChallengeQuizService.generateBoardExamAsync` → `markSessionReady` → `writeReadySession(..., startTimer=true)` (`:1101`, `:3448`) |
| That is the locked contract, not an accident. | `docs/product/EXAM_MODES.md` Timer Policy: "`timerStartedAtEpochSeconds` is stamped server-side when the quiz becomes ready" |
| Challenge Quiz was moved off it in v0.175.0 (prepared, then **Start quiz**); Board and Long Exam were not. | `writeReadySession(startTimer=false)` is called only from `markChallengeSessionReady` |
| Board/Long quota is **reserved at creation**, refunded on failure; Challenge now charges at Start. | `docs/features/challenge-quiz.md` (Board Exam reserves a Challenge unit and a Board unit at start); `LongExamService` refund guard `:349` |
| An abandoned started Board/Long exam is never swept; only stuck GENERATING ones are (30 min). | `GenerationRecoveryService.recoverStaleLongExamSessions/recoverStaleBoardExamSessions` (GENERATING only); the v0.175.0 started-expiry sweep is Challenge-only |
| Redo Missed on a note with a **started Board Exam** silently forfeits it. | `resolveExistingChallengeSession`: the `startedRedoCollision` guard requires `ordinary`; Board falls through to `forfeitStaleOrdinarySession`. Trigger needs an active Board Exam plus a Redo Missed entry for the same note (`PostSessionNextStepService.java:60`, dashboard or notification links) |
| Board and Long Exam generation has **no answer-key guard** and runs on the pinned `gpt-4.1-mini`. | Board: `generateBoardExamQuiz` → `generateQuizWithSchema`; Long: `generateLongExam*`; the R1 guard lives only in `generateChallengeQuizWithSchema`. Routes: `LlmRouting.interactiveQuiz()` (`ChallengeQuizService:1155`, `LongExamService:1167`) |
| mini's statement-pair wrong-key rate was about 12% before R1, 3 of 32 in the R1 acceptance run. | `docs/claude-findings/2026-10-19-v0175-c7-statement-pair-answer-key-comparison.md`, `…-2026-10-20-v0175-r1-acceptance-run.md` |
| Long Exam renders inside the Challenge page. | `frontend/app/study-packs/[id]/long-exam/page.tsx` is 69 lines; `startLongExam` is wired in `challenge-quiz/page.tsx` (3,883 lines) |
| The proposed Long Exam pool of 30 equals the highest tier's question count. | `StudySnapProperties.java:200–203` (`longExamHighTierCount = 30`, `longExamPoolSize = 48`) |

## 2. Candidate items

1. **Started Board Exam vs Redo Missed (live defect, predates v0.175.0). Tier N.** Extend the `existingStarted` collision
   (#1530) to Board Exam: "resume" or "end and redo missed". Board Exam's identity (leave = forfeit, no gentle copy,
   `EXAM_MODES.md`) needs its own wording, so the copy is **not** the Challenge copy. Tests: the same real-PostgreSQL
   keep/discard/expiry set as #1530, from the real Redo Missed entry.
2. **Answer-key guard for Board Exam and Long Exam. Tier H (prompt and schema change, owner signs the wording first).**
   Reuse R1's already-signed per-statement-verdict wording on `generateQuizWithSchema` and `generateLongExam*`. This is the
   biggest accuracy gap left: the most high-stakes mode runs on the model with the highest measured wrong-key rate and
   none of the guard. Open: whether to also move these two modes off the pin (pin retirement, separate).
3. **Prepared, then Begin, for Board Exam and Long Exam. Tier H.** Reuse the Challenge machinery
   (`preparedNotStarted`, `start-quiz`, 7-day expiry, ready briefing, stuck-preparation sweep). The timer and the quota
   unit move to the Begin press. Both modes share `ChallengeQuizService`'s lifecycle methods, so this touches the same
   shared methods as Challenge: **a scoped cold falsification agent is required**, and Board Exam's "no encouragement
   microcopy / strict" identity constrains the briefing copy.
4. **Expiry sweep for abandoned started Board/Long exams. Tier N.** Same pattern as the Challenge started-expiry sweep
   (time plus grace, locks in sorted order, no quota change). Today an abandoned started session stays `IN_PROGRESS`.
5. **Long Exam pool 48 → 30 (A4). Tier N, config only.** **Settle slack first:** 30 equals the largest question count, so a
   pool of 30 leaves no room for dedup or sampling loss. Read the pool sampler before choosing 30 or something like 36.
6. **Board Exam over its monthly limit by one under a race (known limitation). Tier N.** Cheap only if the check and the
   reservation can share one lock; otherwise leave it recorded.

## 3. Owner decisions needed

- **D1 (quota).** Charge at **Begin** like Challenge (recommended: consistent fairness, nothing consumed by an abandoned
  preparation; plans are Pro-only and capped at 10 Board / 12 Long per month, and the 2026-10-16 production read found 0 active paid subscriptions, **a snapshot to re-read before relying on it**), or keep reserve-at-creation with a refund on expiry.
- **D2 (locked contract).** `EXAM_MODES.md` says the timer starts when the quiz becomes ready, for all three modes.
  Moving Board/Long to Begin amends a locked document, so it needs your explicit sign-off.
- **D3 (accuracy).** Extend the R1 guard to Board and Long Exam, and when.
- **D4 (pool).** 30 or a value with slack.
- **D5 (copy).** New learner-facing strings go through the voice guide and Product UX before the brief. Board Exam copy stays
  serious by design.
- **D6 (shape).** One release or two (below).

## 4. Recommended shape

**Two releases, accuracy and the live defect first.**

- **v0.176.0 (small):** items 1, 2, 5. Fixes the data-loss defect and the largest accuracy gap, with no state-machine
  change. About 3 items, so one `advisor()` plus one scoped cold agent on the guard.
- **v0.177.0:** items 3, 4, 6. The prepared state and quota move; Tier H, the same cold-agent falsification as v0.175.0.

Why: verification cost grows faster than item count (`CLAUDE.md`, release-size note). Items 1, 2 and 5 are independent of
the state machine, address your top priorities (accuracy, no lost progress), and can ship while the owner decides D1 and D2.
If you prefer the original C6 shape (one release), the cost is one Tier H release with six items.

## 5. Constraints that stay binding

- No pricing, entitlement or quota-limit change; D1 changes only **when** the unit is charged, for Board/Long.
- Customer copy never names a model and implies no plan quality difference.
- No prompt or schema change beyond R1's already-signed wording (item 2); any other wording goes to the owner first.
- Every new endpoint owes a real-request MockMvc test; every new native query must `PREPARE` in
  `NativeQueryPostgresIntegrationTest`; real-PostgreSQL tests for the sweep and the Begin transition.
- **Deploy risk:** every deploy since 2026-10-25 was followed by a Postgres signal-9 kill (4 of 4). One deploy per release,
  at the quietest hour, and nothing else deployed in the same hour.

## 6. Not in scope

Pin retirement (benchmarks for the interactive, interview and critique slots; its own release before 2026-12-15),
verifier activation, the "note is also in another prepared quiz" line, Leg A (database plan).
