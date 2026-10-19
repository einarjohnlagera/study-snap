# v0.175.0 — Challenge Quiz preparation UX: plan

**Status: DRAFT — architecture verification complete, revised once after `advisor()` corrected a wrong
absence claim, an unusable retry check, and an overconfident state-design call; implementation NOT
authorized.** Written 2026-10-19 by Feature Planner, consolidating both owner decision sets
(`docs/claude-plans/2026-10-19-owner-decisions-v0174-split-and-challenge-preparation-ux.md`, Set 2
authoritative wherever it refines Set 1) against this session's own prior root-cause/UX work
(`docs/claude-plans/2026-10-17-challenge-quiz-latency-root-cause-and-ux-recommendation.md`) and the
targeted benchmark (`docs/claude-findings/2026-10-19-v0174-targeted-challenge-start-benchmark-results.md`).
Release Implementor is writing the v0.174.0 half (temporary Challenge routing, routing plan) separately
and will consolidate both into one document. **This document covers v0.175.0 only** — per §6, one item
from the original required list belongs in v0.174.0 instead and is flagged there, not duplicated here.

**Headline finding from the architecture read: no mode in this product — Challenge, Board Exam, or Long
Exam — has ever had a "generation just finished, confirm before the timer starts" gate.** Every mode today
transitions straight from "ready" into an actively-timed state the instant the backend marks it ready. The
owner's hybrid UX requires building this gate for the first time, not wiring up something that already
exists elsewhere. The closest precedent (Long Exam's `PAUSED`/`resumeSession` toggle) solves a different
problem — resuming an *already-running* exam after an interruption, not gating the *first* transition into
one — but it is the right shape to copy: an explicit, owned action required to move a session from "not
actively timed" to "actively timed," on the same `QuickReviewSessionEntity`/`QuickReviewSessionStatus`
model Challenge already uses.

## 1. Architecture verification — what exists, what must be built

| Component | What exists today (file:line) | What's missing / must be built |
|---|---|---|
| **A "ready, not yet timed" session state** | None. `QuickReviewSessionStatus` (`entity/QuickReviewSessionStatus.java`) has `GENERATING, FAILED, IN_PROGRESS, PAUSED, COMPLETED, FORFEITED` — no value means "content exists, timer hasn't started." `markSessionReady` (`ChallengeQuizService.java:2614-2636`) sets `status = IN_PROGRESS` **and** stamps `timerStartedAtEpochSeconds = now()` in the same call — "ready" and "timer started" are the same event today, for every mode. | **A real tradeoff, not a settled implementation detail — the first pass of this document called it one and was wrong to.** A genuine database-level fact breaks the tie partway: `V19__note_first_lifecycle_and_relationships.sql` creates `idx_quick_review_sessions_one_in_progress_note`, a **partial unique index scoped literally to `WHERE status = 'IN_PROGRESS'`**, enforcing at most one in-progress session per `(user, note, mode)`. A `sessionState` flag layered on top of `IN_PROGRESS` (the codebase's existing pattern — `shortExam`, `boardExamQuotaReserved`, `poolSourced` are all flags on this same JSONB column) inherits that uniqueness guarantee and `ACTIVE_GENERATION_STATUSES`'s existing dedup (`:140-143`, already includes `IN_PROGRESS`) for free. A new enum value would need its own matching partial index to get the same protection, or the gap reopens. **The cost of the flag approach, named plainly rather than assumed away:** every consumer that currently reads `IN_PROGRESS` as "timer is running, submittable" needs an explicit new guard — `completeSession`/`updateSessionProgress` (must reject a submission against a ready-not-started session), `applyStartedSession:779` on the frontend (must check the flag before jumping to `"running"`), and the frontend's own status unions. `USAGE_COUNTED_STATUSES` (`:149-153`) already includes `IN_PROGRESS` harmlessly, since quota is charged at creation regardless (item 4) — not a new problem either way. This needs an implementer's deliberate choice with this list in hand, not a default. |
| **Splitting "ready" from "started"** | `markSessionReady` does both atomically. | Split into two backend actions: (a) the existing generation-completion path persists the quiz and sets the new ready-not-started state, **without** stamping `timerStartedAtEpochSeconds`; (b) a new explicit action (e.g. `POST .../start-quiz`), modeled directly on `LongExamService.resumeSession` (`:456-465`) — check the session is in the ready state, stamp the timer, flip to `IN_PROGRESS`, save. This is the single largest code change this plan requires. |
| **In-app notification, with dedup and deep-linking** | **Already exists, and fits with no new infrastructure.** `NotificationEntity` (`entity/NotificationEntity.java`) has `ctaPath` (a same-origin relative path + query string, validated by `AnnouncementCtaPathValidator` — general-purpose, not an allow-list) and `dedupKey`. `NotificationService.deliver` (`service/NotificationService.java:28-50`) enforces **exactly one notification per `(recipientUserId, dedupKey)`** via a unique DB constraint — "no repeats" is already guaranteed by construction, not something to build. `ASYNC_RESULT` is an existing `NotificationCategory` (`entity/NotificationCategory.java`) built for precisely this shape — "a background job finished, tell the user once" — with `BULK_GENERATION_INCOMPLETE`/`BULK_REGENERATION_COMPLETE` (`entity/NotificationType.java`, produced by `service/BulkOperationNotificationService.java`) as the existing, shipped precedent to copy. **No email or push channel exists anywhere in this service** — the owner's "no email, push, or repeated reminders" requirement is satisfied by what this system *is*, not by a restraint that has to be enforced. |
| **Bank/template reuse gating whether generation is needed at all** | **Already exists and already does exactly this.** `claimEligibleQuestions`/`copyTemplateQuestions` (`ChallengeQuizQuestionBankService.java`, `OfficialChallengeQuizTemplateService.java`) are what decide whether a shortfall — and therefore any wait at all — exists. The owner's "continue prioritizing eligible question-bank reuse… allow the learner to start normally" (Set 2 §1) is the current behavior unchanged; nothing here needs building. |
| **A "stay and watch progress" overlay** | `QuizGenerationOverlay` (`frontend/app/study-packs/[id]/challenge-quiz/page.tsx:1874-1879`) and the `"generating"` phase's poll loop (`:1392-1427`) already exist and are generic. | Needs new copy/CTAs (the primary "Stay on this page" / secondary "Continue reviewing" pair), not a new component. |
| **"Continue reviewing" (leave and keep generating)** | Mechanically, this **already works once generation is async** (Required item 2) — navigating away doesn't interrupt a server-side background job, and `resolveExistingChallengeSession` (`ChallengeQuizService.java:1397-1472`) already finds and returns an in-flight session for any mode on return. | The missing half is **discoverability and closure**: a visible "Continue reviewing" action that actually navigates away (today there's no such affordance on this page — leaving just means closing the tab or clicking elsewhere), and the notification that closes the loop (below). |
| **A pre-generation confirmation screen named "prescreen" in this codebase** | Exists, but is a **different thing**: the FREE-plan "Challenge Quiz Setup" screen (`page.test.tsx:652-665`, confirmed by the actual component) shown **before generation starts at all** — rules copy, a "Start Quiz" button that *triggers* generation. This is reusable UI vocabulary (the same button label, "Start Quiz," already exists and already means "begin" in this product) but is not the owner's post-generation gate — don't conflate the two in implementation or in conversation with the owner. | The owner's gate is a **new, second** "Start Quiz" moment, after generation, not a rename of this one. |
| **A deep-link landing route that validates ownership, staleness, and already-started state** | Partially: `resolveExistingChallengeSession` already returns the right thing for a `GENERATING` session on any mode, and (once built) could be extended to return the ready-not-started state too. Nothing today handles "this link is for a session that was already forfeited/expired/started from elsewhere." | A route (or an extension of the existing one) that, given a session reference, checks current status and responds safely for all four cases: still ready (land on the pre-screen) / already started elsewhere (land on the running quiz, mirroring today's resume behavior) / expired or forfeited (a clear "this is no longer available, start a new one" state) / not owned by this user (404, same as every other session lookup in this file already does). |

## 2. Mapping the owner's flow onto the previous required items 1–9

| # | Original scope (2026-10-17 doc) | Keep / merge / drop | Owner sentence driving the change |
|---|---|---|---|
| 1 | Thread the resolved model parameter through Challenge's call sites | **Drop from this document — belongs in v0.174.0.** | Set 1 §3: v0.174.0 includes "Correct and verify the relevant model-routing defects"; Release Implementor is already writing this. |
| 2 | Single-note async dispatch, returning a bare "Generating…" overlay | **Keep, merged:** the async-dispatch mechanism is unchanged, but what it returns into is now the hybrid prep screen (primary/secondary CTA), not a plain overlay, and completion lands on the new ready-not-started state, not straight into `IN_PROGRESS`. | Set 2 §1 ("show a quiz preparation screen with two options"), §2 ("automatically transition to the quiz pre-screen, not into an active timed quiz"). |
| 3 | Challenge-specific `GenerationRecoveryRowWriter` pair | **Keep.** Now also covers the new ready-not-started state (a session stuck "ready" with no learner action, if that's ever treated as recoverable — see §4) in addition to stuck `GENERATING`. | Set 2 §5 ("if preparation fails or times out, provide a recoverable state"). |
| 4 | Quota charge at session-creation, before dispatch | **Keep, now load-bearing for retry safety too** (§3 below). | Set 1 §2 ("prevent duplicate quota charges"); Set 2 §4/§5. |
| 5 | Board-Exam-style floor-based graceful degradation for multi-note | **Keep.** | Set 1 §3 ("safe multi-note parallel generation"). |
| 6 | Multi-note async dispatch | **Keep, same merge as item 2.** | Same as item 2, plus Set 1 §3. |
| 7 | Multi-note per-source catch-with-a-floor | **Keep — this is literally named in the owner's text.** | Set 1 §3: "safe multi-note parallel generation." |
| 8 | Multi-note per-source parallelization (H-fan), now measured (~4–5× cut) | **Keep, now required rather than optional** — the owner's own text names it directly. | Set 1 §3: "safe multi-note parallel generation." |
| 9 | Single-note Sol timeout: an open decision this document couldn't make | **Resolved by the owner, converted from a decision into a design requirement** — see §3. | Set 1 §2, in full: retry once when safe, check persistence first, clear recovery state, no silent Luna fallback. |

**New items, driven by Set 2, with no equivalent in the original nine:**

| # | New scope | Owner sentence |
|---|---|---|
| 10 | Split `markSessionReady` into "persist and mark ready" (no timer) and a new explicit "start the timer" action, modeled on `LongExamService.resumeSession` | Set 2 §2, §4 |
| 11 | A new ready-not-started session state (flag or enum — §1) | Set 2 §2 |
| 12 | A new frontend phase between `"generating"` and `"running"` for the pre-screen | Set 2 §2 |
| 13 | A visible "Continue reviewing" action and its navigation target | Set 2 §1, §3 |
| 14 | An in-app readiness-notification producer, built on the existing `NotificationService`/`ASYNC_RESULT` infrastructure | Set 2 §3, §4 |
| 15 | A deep-link landing path handling ready / already-started / expired-or-forfeited / not-owned | Set 2 §4, §5 |
| 16 | Board Exam / Long Exam evaluation (not implementation) of the same pattern | Set 2 §6, §7 |
| 17 | **Missed in the first draft, caught by `advisor()`: validation of tier-specific generation routing for Challenge Quiz** — Set 1 §3 names this explicitly as a v0.175.0 item in its own right, not a restatement of item 1. **The baseline it inherits from, per Release Manager's own v0.174.0 routing read:** Challenge live generation (start, "+5," and the template seed) is pinned to `gpt-4.1-mini` for every tier in v0.174.0 via a dedicated key, except the template seed, which goes to Sol — so Sol-authored template content already reaches every tier's Challenge sessions by copy, before any tier-aware routing exists for live generation at all. **Acceptance criterion:** after this item lands, `model_used` on Challenge sessions (already tracked — see below) shows Luna for FREE and Sol for Plus/Pro live generation, matching the rest of the product, not the v0.174.0 pin. | Set 1 §3: "Validation of tier-specific generation routing for Challenge Quiz" |

**Telemetry for item 17 and for the notification/monitoring design generally already exists, per Release
Manager's own read of `ChallengeQuizService`:** `QuickReviewSessionEntity` already stores `model_used` plus
input/output/cached token counts (`:95-105`, migration `V98`), filled by `accumulateLlmUsage`
(`ChallengeQuizService.java:2501-2516`, first-write-wins at `:2508`) for single-note start and "+5" only —
**multi-note discards this today.** Whatever readiness/preparation-job record this plan ends up building
should extend or reuse this existing column rather than starting a parallel telemetry mechanism from
scratch; multi-note's gap is pre-existing and worth closing alongside item 8's parallelization work, not a
new finding this document is raising independently.

## 3. Retry-once-when-safe design

**What "the original job completed or persisted" must actually check — corrected after `advisor()` showed
the first version of this check was unusable.** The first draft proposed checking for
`challenge_quiz_question_bank` rows with `claimedSessionId = session.getId()` as evidence the original
attempt had persisted something. **That signal can't distinguish anything**, because both
`claimEligibleQuestions` and `copyTemplateQuestions` set `claimedSessionId` to the session's id **before
any generation runs**, for the bank/template portion of *any* start — so rows keyed on the session exist
for every partly-bank-served start, including ones where generation never ran at all. **Dropped; the only
reliable signal is the one already cited, used correctly this time:** `generateBoardExamAsync`
(`:626-633`) locks the session row, then re-reads its scalar status, and bails out if it's no longer
`GENERATING` — the retry-once logic should do exactly this and nothing else: before attempting the retry,
lock-and-re-check the session's own status; if it's no longer in the pre-retry state (a recovery sweep or
a second, concurrent attempt already resolved it), discard the retry's own result rather than racing to
overwrite it.

**Where the retry lives — one layer, not two, and why.** A real, pre-existing retry wrapper,
`retryOnceOnInvalidOutput` (`service/impl/OpenAiLlmStudyPackService.java:2570-2587`), already exists and
already wraps quiz generation — but it only retries on `AppException` carrying `INVALID_OUTPUT_CODE`, never
on a read timeout, which is a different, unretried exception path today (confirming the benchmark's own
observation that the timed-out run made exactly one attempt). **Recommend implementing the owner's
retry-once at the job level, inside the Challenge async task, not inside this low-level wrapper** — adding
a second, independent retry loop to `OpenAiLlmStudyPackService` *as well* would let a single Sol timeout
produce up to 2×2 = 4 real attempts (an inner retry inside the HTTP-client layer, times an outer retry at
the job level), each able to run close to 180 s. Pick one layer; this document recommends the job level,
since that's what can see the session's state and quota before deciding whether a retry is "safe," which is
exactly what the owner's wording asks for.

**No new persisted counter needed, and `retryCount` must NOT be reused for this — a second overloading
mistake this document almost made.** `advisor()` caught that `QuickReviewSessionEntity.retryCount`
(confirmed by reading every call site: `service/QuickReviewSessionService.java:200-422`) is Quick Review's
own **per-question retry** counter, driven by client requests and surfaced in dashboard/analytics/history —
unrelated to LLM generation attempts, and reusing it would be exactly the kind of field-overloading this
document already rejected for `PAUSED` above. The retry-once bound doesn't need to survive across
requests: it's an in-memory loop bound within the single async task's own execution (try, catch a timeout,
lock-and-recheck status, retry once, catch again, mark failed) — no new column required.

**Worst case, stated plainly rather than left implicit:** inside a provider-side slow window like the one
the benchmark observed, a learner could reach `FAILED` after roughly **2 × 180 s plus overhead** — around
six minutes — before the recovery state the owner also asked for even appears. Separately, per the first
benchmark's own caveat on `gpt-4.1-mini`'s timeouts ("the 3 mini timeouts recorded no tokens and may have
been billed anyway"), **a timed-out call that is retried may be billed for both attempts**, not one —
worth carrying into whoever scopes the cost-monitoring side of this.

**Duplicate prevention, reusing mechanisms that already exist:**
- **Quota:** already solved by Required item 4 (charge at session-creation, before any generation attempt)
  — a retry of the *same* session never re-charges, because the charge isn't per-attempt.
- **Duplicate sessions:** already solved by `resolveExistingChallengeSession` (`:1397-1472`) for any
  active-generation status, and by the DB-level partial unique index on `IN_PROGRESS` (§1) if the ready
  state is layered on it — needs extending to cover the new ready state explicitly either way.
- **Duplicate generation:** bounded by the single in-memory retry-once loop above, not a persisted counter.

**After a retry also fails:** mark the session `FAILED` (existing mechanism), release any bank claims
(existing mechanism, `releaseClaims`), and surface an actionable retry-again option, reusing the
`FAILED`-status branch the frontend already renders (`applyStartedSession:714`) rather than building new
UI for this case — "preserve the learner's existing progress" has nothing to preserve for a single-note
start beyond the session's own existence and plan selection, unlike a mid-quiz failure.

## 4. Acceptance criteria (deliverable, Set 1 §1's explicit ask)

**State the conflict plainly before the criteria below, rather than let the numbers imply it's resolved:**
under this document's own reading of the owner's targets (§6), **a Sol single-note start cannot meet
either the 30 s target or the 60 s maximum with any lever approved so far** — the benchmark's own harness
measured a 106.7 s p50 at 15 questions, and low reasoning effort (the only tested latency lever) isn't
adopted pending its quality review. The owner's own fallback sentence — "if generation takes longer, the
learner must be able to leave the loading state safely and return when the quiz is ready" — is not a
contingency for an edge case under this data; for Sol, it is **the normal path**, not the exception.

**Readiness:**
- A Challenge start that can be fully served from the bank/template never shows the preparation screen —
  `started` → directly `running`, unchanged from today (bank-reuse behavior is not touched by this plan).
- The **start endpoint's own response time** (session insert plus a bank/template claim, before any model
  call) is what must be fast, not generation itself — **target: under 1 s**, matching today's synchronous
  claim-then-decide step minus the LLM call. **Assert:** the preparation screen renders on the response to
  this call, never after waiting on a model response.
- Once the learner is on the preparation screen, the poll loop's existing 2 s interval
  (`pollGenerationStatus`, `page.tsx:1392-1427`) bounds how quickly "ready" is noticed — **target: ready
  state observed within one poll interval (≤ 2 s) of the backend marking it.**
- **Worst-case time to `FAILED`, stated explicitly, not left to fall out of other numbers:** per §3's
  retry design, up to **2 × 180 s plus overhead (≈ 6 minutes)** inside a provider-side slow window, before
  the recovery state appears.
- When generation completes, the session transitions to the ready-not-started state with the quiz content
  persisted and `timerStartedAtEpochSeconds` **absent** — **assert:** a session in this state, read back
  immediately, has `status`/flag = ready and no timer-start timestamp.
- Clicking "Start Quiz" from the pre-screen is the only path that stamps `timerStartedAtEpochSeconds` —
  **assert:** no other code path (poll response, notification click, page reload) sets it.

**Failure recovery:**
- A single-note Sol timeout triggers exactly one retry, gated by the persisted-check in §3 — **assert:** a
  forced-timeout test produces exactly one additional model call, not zero and not more than one.
- If the retry also fails, the session reaches `FAILED` with bank claims released — **assert:** no
  orphaned `claimedSessionId` rows remain for that session after a double failure.
- No scenario charges quota twice for one session — **assert:** a session that times out, retries, and
  eventually succeeds (or fails) shows exactly one quota decrement, checked against the existing
  `UserUsageEntity` counters.

**Timeout / notification behavior — the trigger must be named before this is testable, which the first
draft of this document skipped.** The backend has no way to directly observe that a learner "left" a page;
it only sees what the frontend tells it. Three candidate triggers, with the actual tradeoff named rather
than assumed:
- **Persist intent on the "Continue reviewing" click** — an explicit signal, cheap, but misses a learner
  who just closes the tab or navigates away without clicking either button.
- **Always create the notification, and auto-dismiss it if the quiz is started from the pre-screen before
  it's ever read** — covers the close-tab case, at the cost of a notification sometimes existing briefly
  for a learner who was never going to need it.
- **A presence heartbeat from the open tab** — would need a periodic write from an otherwise read-only
  poll, a real cost for a mechanism this plan doesn't otherwise require.
**The close-tab-without-clicking-anything case is a Product UX decision, not a technical default** — naming
it here rather than picking silently. Recommend taking it to the owner/Product UX alongside the other open
decisions in §6, with the second option (always-create-then-auto-dismiss) as this document's suggestion,
since it's the only one of the three that doesn't miss a real, likely-common case.

- Once a trigger is chosen: leaving the preparation screen does not stop, duplicate, or restart generation
  — **assert:** the same session id, same `claimedSessionId`, same generation attempt continues
  server-side.
- Exactly one notification is created per session reaching the ready state while the learner has left —
  **assert:** re-triggering readiness logic (e.g., a retried sweep) does not create a second notification
  for the same `(recipientUserId, dedupKey)` — this is the existing unique-constraint guarantee
  (`NotificationService.java`, confirmed via `grep -n`: the `deliver` method at lines 28-50, the
  `DataIntegrityViolationException` fallback at lines 45-48), not new code, so the assertion is "the
  existing mechanism is actually wired up," not "a new dedup check works."
- Clicking the notification lands on the pre-screen of the exact session referenced, never on an active
  timer — **assert:** `timerStartedAtEpochSeconds` is unchanged by opening the notification.
- An expired, forfeited, or already-completed session's notification/deep-link produces a clear,
  non-crashing "no longer available" state, not an error page or a silently-started quiz.
- **A fourth case beyond expired/forfeited/already-started, missed in the first draft: staleness from a
  pack regeneration that happens while a session is sitting ready.** `invalidateForStudyPack` deletes bank
  rows by `studyPackId` regardless of claim status — if the source pack regenerates while this learner's
  session is in the ready state, the claimed rows the session's quiz was built from could be gone
  underneath it. **Store the pack's `generationStamp` at the moment preparation completes, and compare it
  against the pack's current stamp on the deep-link open** — a mismatch means stale, same signal
  `findClaimableForUpdate` already uses for the same purpose elsewhere in this file.

## 5. Board Exam / Long Exam fit

**Board Exam: the pattern does not exist today, and the gap is identical to Challenge's — but Board Exam's
existing async infrastructure makes it cheaper to retrofit, not harder.** `generateBoardExamAsync`
(`ChallengeQuizService.java:602-665`) already dispatches after commit and already calls `markSessionReady`
(`:637`) — which, exactly like single-note Challenge, stamps the timer the instant content is ready, with
no gate. The frontend's poll loop (shared with Challenge, same page component) goes straight from
`"generating"` to `"running"` for Board Exam too. **Retrofitting the same ready-not-started split onto
Board Exam is lower-effort than building it for Challenge in the first place**, since the dispatch-after-
commit mechanism this plan needs for Challenge (items 2/6 above) already exists for Board Exam — only the
"split `markSessionReady`" and "new frontend phase" halves are new work, shared with Challenge's build.

**Long Exam: has a real, different, already-shipped mechanism (`PAUSED`/`resumeSession`,
`service/LongExamService.java:445-465`) — but it solves resuming an *interrupted, already-running* exam,
not gating the *first* transition into one. Verified directly, not inferred, after an `advisor()` review
flagged this as an unverified absence claim:** `LongExamService.java:783` sets
`timerStartedAtEpochSeconds = now()` at generation-completion time — the same conflation Challenge has, in
a different file. The frontend's `enterRunningFromStart`
(`frontend/app/notes/[id]/long-exam/page.tsx:303-319`) takes that already-stamped timer straight into
`setPhase("running")` with no gate in between. `pauseSession` requires the session to already be
`IN_PROGRESS` (`:447`) — it cannot be reached from `GENERATING`, so it plays no role in the first
transition. The frontend's own first-load logic (`:437-439`) confirms the same ungated path on reload: a
freshly-ready `IN_PROGRESS` session with `!canResume` goes straight to `"running"`. **So Long Exam needs
the same new gate built, same as the other two** — its
existing `PAUSED` state is a useful *pattern* to copy (an explicit action required to move between
not-actively-timed and actively-timed), and possibly even a mechanism to reuse directly if the owner is
comfortable with "ready-not-started" and "paused-mid-exam" sharing one status with a sessionState flag
distinguishing them — but it is not a shortcut that already does what the owner is asking for.

**Answer to the owner's §6, "never automatically begin a timed exam because a background job completed or
a notification was opened":** **this is not true of any mode today**, Board Exam and Long Exam included —
all three auto-start the instant content is ready. Evaluating "the same pattern" (owner's own word,
"evaluate," not "ship") means: the fix built for Challenge is directly transferable to Board Exam (same
file, same dispatch mechanism, same page component) and transferable in spirit but not in code to Long Exam
(different service, different page, but the same state-machine shape). **Recommend scoping the actual
Board Exam / Long Exam retrofit as a decision for after Challenge's version ships and is observed in
production**, not bundled into the same v0.175.0 PR — the owner asked this document to evaluate fit, not to
commit a implementation date, and bundling three modes' worth of state-machine change into one release
raises the same release-size concern this session has flagged before.

## 6. Conflicts, unresolved dependencies, and decisions the owner must make

**No outright contradiction between the two decision sets** — Set 2 refines Set 1 exactly as the owner's
own framing says, rather than conflicting with it. **One real ambiguity worth surfacing rather than
silently resolving:** Set 1 §1 states a "60 seconds maximum blocking wait," and Set 2 §1 says only "if
generation is needed, show a preparation screen" with no mention of a blocking wait at all. **This
document's reading: the preparation screen appears immediately whenever a shortfall exists — there is no
remaining synchronous blocking wait in the final design, and "30 s target / 60 s max" describes an
engineering target for how long preparation should typically take, not a client-side wait before showing
the screen.** This reading is consistent with Set 1's own framing ("these are product UX targets, not hard
deadlines for background generation jobs"), but it's a reading, not a restated instruction — confirm with
the owner before this becomes the acceptance bar in §4.

**Dependency on v0.174.0, sharpened after this plan was reviewed — confirmed against Release Manager's
own v0.174.0 routing read, not left as a hypothetical.** v0.174.0 pins all Challenge live generation
(start, "+5," and the template seed's own call — except the seed itself, which goes to Sol) to
`gpt-4.1-mini` for every tier, via a dedicated key — this *is* the interim mitigation Set 1 §3 asks for,
already decided, not still open. **But that pin has to actually cover every synchronous Challenge path, or
v0.174.0 breaks its own "no unvalidated latency regression" rule by itself, independent of anything in
this document:** multi-note's `generateChallengeQuizForSources` and "+5"'s `generateMoreChallengeQuiz` are
not single-call paths — a multi-note start still makes up to 6 **sequential** calls inside one request
even on `mini`, and this document's own targeted-benchmark read found a 6×3 **Sol** case at a 217 s p50
sequential. If v0.174.0's pin doesn't also cover multi-note and "+5," v0.174.0 itself ships the regression
it's supposed to avoid. **Render's proxy request limit, flagged as unchecked when this was first written,
was checked 2026-10-19 and resolved as not the binding constraint**: the frontend calls Render directly
with no Vercel proxy hop, and staff commentary on Render's own platform limit (~100 minutes, not an
official docs figure) is well above any measured multi-note scenario — the application's own 180 s
per-call LLM timeout fires first regardless. Removed as an open item; the multi-note latency/reliability
exposure (§1 above) stands on its own merits without it.

**Decisions that need the owner, not an implementer:**
1. Confirm the blocking-wait reading above.
2. Board Exam / Long Exam retrofit timing — evaluated now (§5), but the decision to actually schedule
   either retrofit, and whether Long Exam's `PAUSED` state should be shared or kept separate from the new
   ready-not-started state, is a product/architecture call for after v0.175.0 ships, not before.
3. The exact staleness bound for a ready-not-started session that nobody ever returns to (how long before
   it's swept as expired) — recommend reusing the existing recovery-sweep's configuration pattern
   (`noteBoundMinutes`-style, per `GenerationRecoveryRowWriter`'s existing design) rather than a new
   standalone timeout, but the actual number is a product choice, not a code fact.
4. **The notification-trigger choice (§4)** — specifically whether a learner who closes the tab without
   clicking either CTA still gets notified. A product call, not a default to pick silently.
5. **The two "Start Quiz" problem, raised here for the first time:** this design now has two different
   buttons both labeled "Start Quiz" — the pre-generation one (today's, which *triggers* generation) and
   the new post-generation one (which starts the timer). A learner clicking the first one has no way to
   know in advance whether they'll land on a running quiz immediately (bank-served) or a preparation screen
   (generation needed) — the label can't describe an outcome that isn't known yet. This needs Product UX's
   input on copy, not an engineering default.
6. **Option G (the 432-pack official-template backfill, from the earlier root-cause document) has no home
   in either release's scope as written** — it's a real, sized, owner-approved-in-principle lever that
   reduces how often generation is needed at all, but nothing in Set 1 or Set 2 schedules it. Flagging it
   here as unplaced rather than letting it quietly drop.

**A constraint that applies to every new user-facing surface in this plan, carried forward from the
release's own existing anti-drift rule, not new to this document:** notification copy, preparation-screen
copy, and any recovery-state copy must never name a model (Sol, Luna, or otherwise) — the same rule already
governing the rest of this release's customer-facing text.

**Release-size note, per `CLAUDE.md`'s own rule, stated rather than left for Release Implementor to
discover at scope time:** this plan's new items alone — a new session state, a split timer-start action,
a notification producer, a deep-link landing route, multi-note parallelization plus a per-source floor, and
a retry-once design — is on the order of fifteen distinct pieces of work layered on quota timing,
concurrency, and a new state machine. That is comfortably a full Tier-H release before Board Exam or Long
Exam retrofits are even considered; per the rule this session has cited before, say so explicitly rather
than let the count grow unremarked as the plan is scoped into a Codex brief.

**Decisions that don't need the owner (implementation details, noted so nobody escalates them
unnecessarily):** whether the notification's `ctaPath` carries the session id as a path segment or a query
parameter (either validates fine per `AnnouncementCtaPathValidator`); the exact field name for the
in-memory retry-once bound (§3 — not `retryCount`, and not necessarily persisted at all). **The
ready-state flag-vs-enum choice (§1) no longer belongs on this list** — it's a real tradeoff with
consequences either way, not a free implementation detail, and needs a deliberate call from whoever
implements it, with the consumer list in §1 in hand.
