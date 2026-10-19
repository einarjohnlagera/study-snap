# v0.175.0 — Challenge Quiz preparation UX: plan

**Status: DRAFT, tightened against owner decisions B1–B7.** Written 2026-10-19 by Feature Planner.
Incorporates `docs/claude-plans/2026-10-19-owner-decisions-v0175-b1-b7.md` (A1–A7 unchanged, referenced
where relevant) into the architecture work already done in this document's earlier pass, which itself
consolidated Set 1/Set 2 of the 2026-10-19 Challenge-preparation owner decisions. v0.174.0 is signed off
(PR #1512 held behind #1505); v0.175.0 is **not yet kicked off** — this remains a plan, not an
implementation, per the owner's explicit "do not implement or deploy yet" on every decision set so far.
Release Implementor is separately writing B6 (targeted Official template-coverage proposal) and B7
(low-reasoning-effort quality review proposal) — not covered here.

**What changed from the previous pass, in one paragraph:** B1 resolves this document's own open question
about whether "30 s / 60 s" is a client-side blocking wait (it isn't — confirmed, not just this document's
reading). B4 gives exact, mode-specific copy, which also resolves the "two buttons both say Start Quiz"
problem this document raised, since the setup CTA becomes "Prepare Quiz" and only the post-ready CTA stays
"Start Quiz." B3 (seven-day expiry) is new scope with a real lifecycle question this document now has to
answer, not just flag. B5 narrows Board Exam/Long Exam to "evaluate a small correction, propose a
sequence" rather than "evaluate fit" in the abstract. The owner's own closing note reopens "Stay on this
page" as a UX question this document should actually answer, not just relay.

## 1. The finalized Challenge Quiz learner journey

Every screen below uses B4's exact copy. This is the single learner-facing flow this plan builds — multi-note
and "+5" are addressed separately in §3, since they don't follow this journey the same way.

1. **Setup screen.** Learner picks Challenge Quiz. CTA: **"Prepare Quiz"** (replaces today's "Start Quiz"
   on this exact button, `frontend/.../challenge-quiz/page.tsx:2192-2197`). Clicking it calls the start
   endpoint, which claims eligible bank/template questions first (unchanged behavior) — per B1, this
   happens immediately, with no artificial wait before deciding which path comes next.
2. **Readiness check (not a visible screen, a backend decision).** If the claim fully covers the requested
   `quizCount`, skip straight to step 6 — **the learner never sees a preparation screen**, matching B1 and
   B4's "proceed immediately through the normal quiz-start flow." This is today's bank-reuse behavior,
   unchanged.
3. **Preparation screen** (shown only when a shortfall exists). Title: **"Getting your quiz ready."** Body:
   **"We're preparing your questions. You can stay here or continue reviewing while we finish."** A visible
   progress indicator (not a "Stay" button — see §4 for why). Secondary action: **"Continue reviewing"**,
   which navigates the learner back to their notes/Study Pack while generation continues server-side
   (Required item 2/6, already async by this point).
4. **Leave/close (B2).** Whether the learner clicks "Continue reviewing," closes the tab, or navigates away
   without clicking anything, generation continues unaffected — it was already running server-side before
   this screen rendered. **On completion, exactly one in-app notification is created**, body copy
   **"Your quiz is ready! Pick up where you left off,"** deep-linking to this exact session's pre-screen
   (step 6). No email, push, or repeats (B2, cross-cutting #5/#10 — §4 of this document covers the one open
   question this raises, which trigger actually creates the notification).
5. **Staying on the page.** If the learner remains on the preparation screen until it finishes, it
   **auto-transitions directly to the ready pre-screen** (step 6) — no notification is created for this
   path (the learner is already there), no click required to advance past "Getting your quiz ready."
6. **Ready pre-screen.** Title: **"Your quiz is ready!"** Body: **"Your questions are prepared. Start
   whenever you're ready."** Primary CTA: **"Start Quiz."** This is the **only** action in the entire
   journey that stamps `timerStartedAtEpochSeconds` — reached either by staying (step 5), returning from
   "Continue reviewing," or opening the notification's deep link (step 4), all landing on the identical
   screen for the identical session.
7. **Failure.** If generation fails (including after the one retry — §7), the learner sees: title **"We
   couldn't finish preparing your quiz,"** body **"Your quiz isn't ready yet. You can try again or return
   to reviewing,"** actions **"Try Again"** / **"Continue Reviewing."** "Try Again" re-enters step 1 against
   the same intent (a fresh attempt, not a resumed one — the failed session is done).
8. **Expiry (B3, seven days).** A prepared-but-unstarted session that sits in the ready state for seven
   days without the learner clicking "Start Quiz" expires. Opening its notification or deep link after that
   shows: title **"This prepared quiz has expired,"** body **"Your prepared quiz is no longer available,
   but you can create a new one when you're ready,"** action **"Prepare New Quiz"** — which re-enters step
   1. **No automatic replacement is ever created** — expiry is terminal until the learner explicitly asks
   again, exactly as B3 requires. §5 covers what has to happen server-side for this to actually be true
   (claim release, not just a UI state).

## 2. Architecture verification — what exists, what must be built

| Component | What exists today (file:line) | What's missing / must be built |
|---|---|---|
| **A "ready, not yet timed" session state** | None. `QuickReviewSessionStatus` has `GENERATING, FAILED, IN_PROGRESS, PAUSED, COMPLETED, FORFEITED` — no value means "content exists, timer hasn't started." `markSessionReady` (`ChallengeQuizService.java:2614-2636`) sets `status = IN_PROGRESS` **and** stamps the timer in the same call. | A real tradeoff, not a default — see §5 for the B3-driven resolution of this. A `sessionState` flag on `IN_PROGRESS` inherits `idx_quick_review_sessions_one_in_progress_note` (a partial unique index scoped to `WHERE status = 'IN_PROGRESS'`) and the existing `ACTIVE_GENERATION_STATUSES` dedup for free, at the cost of new guards in `completeSession`/`updateSessionProgress` and the frontend's `applyStartedSession:779`. |
| **Splitting "ready" from "started"** | `markSessionReady` does both atomically. | Split into (a) persist-and-mark-ready, no timer stamp, (b) a new explicit `start-quiz` action, modeled on `LongExamService.resumeSession` (`:456-465`) — lock, check ready state, stamp the timer, flip status, save. The largest code change this plan requires. |
| **In-app notification, dedup, deep-linking** | Already exists and fits with no new infrastructure. `NotificationEntity` has `ctaPath` (general same-origin path + query, validated) and `dedupKey`, unique per `(recipientUserId, dedupKey)` at the DB level (`NotificationService.deliver:28-50`). `ASYNC_RESULT` category and `BULK_GENERATION_INCOMPLETE`/`BULK_REGENERATION_COMPLETE` are the shipped precedent. No email/push channel exists in this service at all. | A new `NotificationType` (e.g. `CHALLENGE_QUIZ_READY`) under `ASYNC_RESULT`, copy exactly as B4 §"In-app notification," `ctaPath` to the ready pre-screen. |
| **Bank/template reuse gating whether generation is needed** | Already exists and already does this (`claimEligibleQuestions`/`copyTemplateQuestions`). | Nothing — B1/B4's "proceed immediately" behavior is current behavior. |
| **A "stay and watch progress" surface** | `QuizGenerationOverlay` and the `"generating"` phase's poll loop already exist and are generic. | New copy (§1), and — per §4 below — a progress indicator in place of a clickable primary button. |
| **"Continue reviewing" as a real action** | Mechanically works once generation is async — navigating away doesn't interrupt a server-side job, and `resolveExistingChallengeSession` already finds an in-flight session on return. | A visible button that navigates away (none exists today), plus the notification that closes the loop. |
| **The existing "Start Quiz" on the setup screen** | `page.tsx:2192-2197`, triggers generation directly, synchronously, today. | Relabel to **"Prepare Quiz"** per B4 — a copy change on this exact button, not a new screen. |
| **A deep-link landing route validating ready/started/expired/stale/not-owned** | Partial: `resolveExistingChallengeSession` already handles `GENERATING` for any mode. | Extend it to cover the ready-not-started state and all four terminal cases listed in §8. |

## 3. Conflicts with current app behavior, anchored to code

**"Prepare Quiz" vs. today's "Start Quiz" on the setup screen.** Direct relabeling of one existing button
(`page.tsx:2197`). No conflict — the button's handler (`handleStartChallenge`) is unchanged; only its label
and what status the response can now carry (ready-not-started, in addition to today's `GENERATING`/
`IN_PROGRESS`) change.

**A real behavior change for bank-served starts that needs an explicit call, not an assumption.** §1 step
2 says a fully bank-served start skips the preparation screen and goes straight into the normal quiz-start
flow — but "normal quiz-start flow" currently means `markSessionReady` runs synchronously and the learner
lands directly in `"running"`, timer already ticking. Once `markSessionReady` is split (§2, item 10), a
bank-served start could either keep today's behavior (synchronous, straight to running, no gate — the
literal reading of "normal flow") or also land on the ready pre-screen for one beat before "Start Quiz" is
clicked (consistent with every other path in §1, at the cost of an extra click for the fast, common case).
**Also relevant: Board Exam's pooled-questions path (`:337`) and the redo-missed-questions path (`:773`)
both call `markSessionReady` directly too** — the same split affects them the moment it exists, whether or
not Board Exam's own full fix (§6) ships in this release. **Recommend: bank-served Challenge starts keep
today's direct-to-running behavior** — "Prepare Quiz" shouldn't make the common, already-fast case slower
by inserting a click that protects against nothing (there's no wait to gate). The redo-missed and pooled
Board Exam paths should do the same, by the same reasoning, independent of whether Board Exam's
confirmation-click fix (§6) ships alongside this release. Flagging this as this document's recommendation,
not an owner decision — it follows directly from B1/B4's own "proceed immediately... without unnecessary
preparation screens," but stating it explicitly since the previous pass of this document didn't.

**Whether "Prepare Quiz" fits untimed modes — answered, with a mode-aware split recommended, not the label
itself.** Quick Review and Adaptive Practice are genuinely untimed: neither sets `timeLimitSeconds` or
`timerStartedAtEpochSeconds` anywhere (`QuickReviewSessionService.java`,
`QuickReviewAdaptivePracticeService.java` — no such field in either). Quick Review never calls the model
live at all (it reads the pack's already-generated static quiz), so the question doesn't apply to it.
**Adaptive Practice does call the model live** (`generateAdaptivePracticeQuiz`, benchmarked at 36–44 s on
Luna/Sol for 5 questions) and could in principle show a similar wait. **The label "Prepare Quiz" itself
generalizes fine** ("get this ready for me" doesn't depend on timing) — **what doesn't generalize is the
downstream ready-pre-screen-plus-explicit-Start gate**, because there's nothing to protect with a
confirmation click when no timer is about to start. **Recommend:** keep "Prepare Quiz" as the universal
initial CTA if this pattern ever extends beyond Challenge, but make the *post-generation* step mode-aware
— timed modes get the "Your quiz is ready! → Start Quiz" gate (§1), untimed modes go straight from ready
into the content with no second click. **This document is not proposing to touch Adaptive Practice in
v0.175.0** — flagging the label question's answer, not expanding scope.

**How multi-note fits:** identically, from the learner's perspective. Multi-note goes through the exact same
screens and copy in §1 — the only difference is backend-only (per-source parallel calls plus a floor,
§8 items 5/7/8), invisible to the journey itself. No special-casing needed in the UX.

**How "+5" fits: it does not, and should not, enter the journey screens at all — but it is NOT already
settled, and this plan's previous pass treated it as more resolved than it is.** "+5"
(`generateMoreChallengeQuiz`) happens mid-session, while the timer from an *already-started* quiz is
actively running — there is no "leave and come back later" story for it, and that part is unaffected by
anything below. The latency data genuinely is reassuring regardless of which model runs it: the first
benchmark ran this exact size (`GENERATE_MORE_BATCH_SIZE = 5`) directly on Sol and found it safe (n = 8,
max 55 s, no timeouts). **What's actually unresolved: item 17 (tier-routing validation) changes which
model "+5" runs on, and this plan hasn't said what it should do about that.** Release Manager confirmed
start, "+5," and the template seed all go through the same `generateChallengeQuizWithSchema` call — so
once item 17 moves Challenge's live generation off v0.174.0's `mini` pin and onto real tier routing,
Plus/Pro "+5" becomes a Sol call mid-quiz by the same mechanism, not a deliberate choice. Whether that's
the right outcome (consistent with the main start's own tier) or whether "+5" should be deliberately pinned
to a faster model regardless of tier (a third routing destination, needing the owner's explicit sign-off,
since pinning away from Sol for a paying tier is exactly what the "no silent downgrade" rule polices) is an
open scope question for item 17, not something this document can default silently. Added to §9.

**B3's "reusable questions remain eligible after expiry" vs. `claimed_session_id` — a real gap, resolved by
extending an existing mechanism, not inventing one.** `ChallengeQuizQuestionBankService.releaseClaims` is
already called by `forfeitStaleOrdinarySession` (`:1475-1487`) and `forfeitExpiredSession`
(`:1498-1513`) — but both are **lazy, triggered only on the learner's next access** to that pack
(`resolveExistingChallengeSession`'s `isSessionExpired` check), not a proactive sweep. **If nobody ever
reopens an expired-but-unstarted session's link, its claims would never be released under today's
mechanism** — directly contradicting B3's "reusable questions should remain eligible." **This needs a
proactive sweep** (§5), not just the existing lazy check, which can stay as a secondary/redundant path for
the case where the learner does happen to reopen it.

## 4. The owner's "Stay on this page" concern — recommendation

**Recommend removing "Stay on this page" as a primary clickable CTA, replacing it with a passive progress
indicator, keeping "Continue reviewing" as the one explicit action** — agreeing with the owner's own
instinct, with the mechanical reason that makes it more than a style preference. Per B1, generation is
**already dispatched before the preparation screen ever renders** — by the time a learner would see "Stay
on this page," the only thing that button *could* do is nothing: staying is simply not clicking anything
and not navigating away. A button whose click handler has no effect different from inaction is exactly the
kind of UI the owner's instinct is flagging.

**Correction: this does NOT resolve the notification-trigger question, and the previous pass's claim that
it did was wrong against B2's own text.** B2 is explicit — "if the learner closes the tab, navigates away,
**or returns to their notes without choosing an option**, the preparation should continue [and] create one
in-app notification." That means the notification fires on *any* way of leaving, chosen or not, not only on
a "Continue reviewing" click — so "Continue reviewing" cannot be the sole trigger. **Correct design:**
deliver the notification unconditionally on readiness (`NotificationService.deliver` already dedups per
`(recipientUserId, dedupKey)`, so an unconditional call is safe even for the stay-and-watch path), and mark
it read/dismissed automatically when that session's own pre-screen renders for a learner who stayed (§1
step 5) — this avoids ever needing a presence heartbeat, and removes the tab-close case as an open owner
decision (§9): it's already answered by B2, this document just read it wrong the first time. **Amends
B4's copy as follows:** drop the "Primary action: Stay on this page" line; keep the progress indicator
description; "Continue reviewing" becomes the preparation screen's only *button*, but the notification
itself is not gated on clicking it.

## 5. B3 storage and lifecycle check

**Storage cost: negligible at current volume, not a real concern.** A ready-not-started session's
`sessionState` JSONB holds up to 20 `QuizItem` rows (question, choices, explanation, metadata) — a few KB
per session, the same size a completed session already stores today. Production Challenge volume is
measured at roughly 23 sessions/30 days (`docs/claude-findings/2026-10-17-v0174-llm-benchmark-results.md`,
caveat 1's production read) — even holding every
ready session for the full seven days at several times today's volume is on the order of low hundreds of
KB, not a storage question worth further investigation. Bank claims held during the wait are existing rows
(`challenge_quiz_question_bank`), not new ones.

**Lifecycle: needs a proactive sweep, mirroring `GenerationRecoveryRowWriter`'s existing pattern, because
the existing lazy forfeit path (§3) can't guarantee the seven-day bound on its own.** Recommend a new
`recoverExpiredChallengeReadySession`-shaped method in that file, run on the same schedule as the existing
stale-`GENERATING` sweep: find ready-not-started sessions older than seven days, mark them `FORFEITED` (or
equivalent), and call `releaseClaims` — the exact same two-step shape `forfeitExpiredSession` already uses,
just sweep-triggered instead of access-triggered, so B3's guarantee holds even for a session nobody ever
revisits.

**The partial unique index interaction — corrected: this is a real, previously-unexamined collision, not
simply "desired behavior," because of a fact this plan hadn't traced before.**
`resolveExistingChallengeSession` (`:233`) looks up by `(studyPackId, QuickReviewSessionMode.CHALLENGE,
ACTIVE_GENERATION_STATUSES)` — **before** it ever branches on whether the request is for ordinary Challenge
or Board Exam, and **Board Exam shares `session_mode = 'CHALLENGE'`** (established in §2). Today this
lookup's collision window is bounded by the quiz timer (tens of minutes at most); a seven-day ready session
makes that window **seven days**, during which: a Board Exam request on the same pack would resume the
ready Challenge session instead of starting its own; a resumed session could reflect a stale difficulty
profile if the learner retook Quick Review in the meantime; and `startRedoMissedSession`'s existing
`requireRedoMissedSource` guard (`:1452-1457`) **silently forfeits a non-matching existing session** — so
a learner going for "redo missed questions" would silently discard a prepared, unstarted quiz they might
still have wanted. **This is a real decision, not a default to resolve here: when a ready session doesn't
match the new request's mode/intent, should it be resumed anyway, replaced (forfeit plus release claims,
a refund question — see the quota gap below), or should the learner be asked?** Added to §9.

## 6. Board Exam / Long Exam: audit and proposed sequence

**Audit, both confirmed by direct code read:** Board Exam shares `markSessionReady`
(`ChallengeQuizService.java:2614-2636`, called from `generateBoardExamAsync:637`) — the exact same
conflation Challenge has, in the exact same method. Long Exam has its own, separate call
(`LongExamService.java:783`) doing the same thing in a different file. Both frontends take the
already-stamped timer straight into their running phase with no gate (`applyStartedSession:779` for Board
Exam, `enterRunningFromStart` for Long Exam) — confirmed, not assumed, after an earlier pass of this
document was flagged for treating this as an unverified absence claim.

**Is a small, independently-safe correction genuinely low-risk? Only if it includes an expiry sweep, not
the confirmation click alone — the previous pass of this document got this wrong.** The smallest fix
imagined was "don't stamp the timer in `markSessionReady`; add a confirmation click that does." But
`isSessionExpired` (`:1489-1496`) returns `false` whenever `timerStartedAtEpochSeconds <= 0` — **removing
the timer stamp removes the only existing expiry bound for that session.** An unstarted Board Exam would
then never expire on its own: it would hold its `IN_PROGRESS` slot and its `boardExamQuotaReserved`
reservation indefinitely, and — because Board Exam is stored as `session_mode = 'CHALLENGE'` (confirmed
earlier in this plan) — that stuck session would also block any ordinary Challenge request on the same
note, not just another Board Exam. **So the correction is only genuinely low-risk if it ships with an
expiry sweep from day one** — reusing the exact same seven-day-sweep shape §5 already designs for
Challenge's ready state, not a smaller or deferred version of it. With that included: `markSessionReady`
is the **same method** Challenge's own core rework (§2) already changes, so Board Exam's fix is a
nearly-free extension of that same split — it can ship *alongside* v0.175.0's Challenge work as a
narrowly-scoped addition (confirmation click plus its own expiry sweep; no notification, no
leave-and-return journey, since Board Exam has no "leave the page" story being built). **Long Exam needs
the same check before it can be called low-risk too** — its own expiry mechanism (separate from
`isSessionExpired`, in `LongExamService`) needs reading to confirm whether it has the same
timer-stamp dependency before this document can claim the fix is safe there; not yet done, flagged rather
than assumed.

**Proposed sequence, keeping v0.175.0's own scope exactly as the owner specified (do not broaden it):**
1. v0.175.0 ships Challenge's full journey (§1) only.
2. **Board Exam's confirmation-click fix** — present as a separate, small, independently-reviewable PR,
   cut once v0.175.0's core split merges (it depends on that split existing), not folded invisibly into the
   Challenge PR. Whether it lands in the same release or a tiny follow-up is a release-boundary call for
   Release Implementor, not a technical constraint either way.
3. **Long Exam's confirmation-click fix** — its own separate PR, sequenced after Board Exam's, same
   reasoning, same narrow scope (confirmation click only).
4. **The full preparation overhaul for both modes** (notification, leave-and-return, expiry) — scheduled
   for the release after v0.175.0, once Challenge's version has been observed in production, exactly as B5
   asks. Not committed to a specific version number here.

This answers B5's "intended long-term rule" directly: no timed exam should start merely because background
preparation finished, for any mode — step 2/3 close that gap for Board Exam/Long Exam immediately and
cheaply, without waiting for their full preparation-UX overhaul in step 4.

## 7. Retry-once-when-safe design

**What "the original job completed or persisted" must check.** The first draft of this check (bank-row
existence) was unusable — `claimEligibleQuestions`/`copyTemplateQuestions` set `claimedSessionId` **before**
any generation runs, so that signal can't distinguish anything. **The only reliable signal:**
`generateBoardExamAsync` (`:626-633`) locks the session row, re-reads its scalar status, and bails out if
it's no longer in the expected pre-retry state — the retry-once logic does exactly this and nothing else.

**Where the retry lives — one layer, not two.** `retryOnceOnInvalidOutput`
(`OpenAiLlmStudyPackService.java:2570-2587`) already exists but only retries `INVALID_OUTPUT_CODE`, never a
read timeout. **Implement the owner's retry-once at the job level** (inside the Challenge async task), not
by adding a second retry loop to the low-level wrapper — doing both risks a single Sol timeout producing up
to 2×2 = 4 real attempts.

**No new persisted counter.** `QuickReviewSessionEntity.retryCount` is Quick Review's own per-question
retry counter (confirmed via every call site in `QuickReviewSessionService.java:200-422`) — unrelated, and
reusing it would repeat the same field-overloading mistake this document already avoided for `PAUSED`. The
retry-once bound is an in-memory loop inside the single async task's execution, not a persisted field.

**Worst case, stated plainly:** inside a provider-side slow window, a learner could reach `FAILED` after
roughly 2 × 180 s plus overhead (≈ 6 minutes) before the recovery state (§1, step 7) appears. A timed-out
call that is retried may be billed for both attempts, not one, per the first benchmark's own caveat on
`mini`'s timeouts.

**Duplicate prevention, reusing what exists:** quota at session-creation (unaffected by retry count);
duplicate sessions via `resolveExistingChallengeSession`, extended to the ready state; duplicate generation
bounded by the in-memory retry-once loop, not a counter.

## 8. Acceptance criteria (Set 1 §1's explicit ask — restored and updated for B1–B4, dropped in the
previous rewrite of this document and flagged rather than left silently missing)

**State the conflict plainly rather than let the numbers imply it's resolved:** under B1's own targets, a
Sol single-note start still cannot meet either the 30 s target or the 60 s maximum with any lever approved
so far — the benchmark's harness measured a 106.7 s p50 at 15 questions, and low reasoning effort (B7) is
explicitly not adopted pending its quality review. B1's own fallback — "if generation takes longer, the
learner must be able to leave the loading state safely" — is the **normal** path for Sol under this data,
not a contingency.

**Readiness:**
- A start fully served from the bank/template skips the preparation screen entirely (B1, B4) —
  **assert:** `started` → directly `running`, no preparation-screen render, for any request the claim
  already fully covers.
- The start endpoint's own response time (claim plus session insert, before any model call) is what must
  be fast — **target: under 1 s. Assert:** the preparation screen (or the skip-straight-through path)
  renders on the response to this call, never after waiting on a model response.
- Once on the preparation screen, the existing 2 s poll interval bounds how quickly "ready" is noticed —
  **target: observed within one poll interval of the backend marking it.**
- **Worst-case time to the failure state:** up to 2 × 180 s plus overhead (≈ 6 minutes) per §7, before the
  "We couldn't finish preparing your quiz" state (§1 step 7) appears.
- A session reaching the ready state has the quiz persisted and `timerStartedAtEpochSeconds` **absent** —
  **assert:** read back immediately, ready flag/status is set and no timer-start timestamp exists.
- "Start Quiz" on the ready pre-screen (§1 step 6) is the **only** path that stamps
  `timerStartedAtEpochSeconds` — **assert:** no other code path (poll response, notification click, page
  reload, the sweep) ever sets it.

**Failure recovery and quota (updated to cover the gap found in §9):**
- A single-note Sol timeout triggers exactly one retry — **assert:** a forced-timeout test produces exactly
  one additional model call.
- If the retry also fails, the session reaches the failure state with bank claims released — **assert:**
  no orphaned `claimedSessionId` rows remain for that session.
- **"Try Again" after a failure nets exactly one quota charge for the whole attempt, not one per failure.**
  This depends on §9's quota-refund gap being closed (a Challenge-specific
  `decrementChallengeQuizUsageNotBelowZero`, mirroring the Board Exam/Long Exam methods that already
  exist) — **assert, once built:** a session that times out, retries, fails, and is retried fresh via "Try
  Again" shows exactly one net quota decrement across the whole sequence, not one per failed attempt.

**Expiry (B3, new in this pass):**
- A ready-not-started session swept after seven days releases its bank claims — **assert:** no
  `claimedSessionId` rows remain tied to that session after the sweep runs, matching the existing
  `forfeitExpiredSession`/`forfeitStaleOrdinarySession` shape.
- Opening an expired session's notification or deep link shows "This prepared quiz has expired" (B4) and
  never auto-creates a replacement — **assert:** no new session row is created by opening an expired link,
  only by explicitly clicking "Prepare New Quiz."
- **Whether the sweep's own quota treatment double-charges a never-taken quiz is gated on the same product
  decision as the refund gap above** — if the sweep marks the session in a status quota already counted
  at creation, and no decrement exists, a quiz the learner never took would still consume their monthly
  allowance. Named here as an assertion to write once §9's decision lands, not asserted as already correct.

**Notification behavior (corrected per §4 — not gated on a single click):**
- The notification is created on readiness regardless of how the learner left (B2) — **assert:** closing
  the tab, clicking "Continue reviewing," and navigating away without clicking anything all result in
  exactly one notification once ready, via the existing `(recipientUserId, dedupKey)` unique-constraint
  guarantee (`NotificationService.deliver:28-50`), not new dedup code.
- A learner who stayed and watched the preparation screen through to the automatic transition does **not**
  see a separate, redundant notification appear for the session they're already looking at — **assert:**
  the notification is marked read/dismissed at the moment that session's own ready pre-screen renders for
  that learner, whichever path got them there.
- Clicking the notification lands on the pre-screen of the exact session referenced, never on an active
  timer — **assert:** `timerStartedAtEpochSeconds` is unchanged by opening the notification.

**The four terminal cases a deep-link open must handle safely (referenced from §2's table row, listed
here since the earlier pass of this document pointed to this list without actually writing it):**
1. **Still ready** — land on the pre-screen (§1 step 6).
2. **Already started elsewhere** (e.g. opened on a second device after starting on the first) — land on
   the running quiz, mirroring today's resume behavior for an `IN_PROGRESS` session.
3. **Expired or forfeited** — the B4 "expired" state, with "Prepare New Quiz"; never a generic error, never
   an automatic replacement.
4. **Stale from a pack regeneration that happened while the session sat ready** — `invalidateForStudyPack`
   deletes bank rows by `studyPackId` regardless of claim status, so a regeneration during the ready
   window could invalidate the content underneath an unstarted session. Store the pack's
   `generationStamp` at the moment preparation completes and compare it on deep-link open; a mismatch
   means stale. **B4 gives no distinct copy for this case — recommend reusing the "expired" copy verbatim**
   ("This prepared quiz has expired... Prepare New Quiz"), since the learner-facing outcome is identical
   (this quiz can't be started, make a new one) even though the cause differs. Not owned by this user is a
   fifth, ordinary case (404, same as every other session lookup in this file), not a new state to design.

## 9. Mapping to the required items (unchanged from the previous pass except where noted)

| # | Scope | Status |
|---|---|---|
| 1 | Thread the resolved model parameter through Challenge's call sites | Belongs in v0.174.0 (already shipped per Release Implementor's routing read). |
| 2 | Single-note async dispatch | Returns into §1's journey, not a bare overlay. |
| 3 | Challenge-specific recovery-writer pair | Extended by §5's new seven-day sweep. |
| 4 | Quota at session-creation | Unchanged; load-bearing for §7's retry safety. |
| 5 | Board-Exam-style floor for multi-note | Unchanged. |
| 6 | Multi-note async dispatch | Unchanged; same journey as single-note per §3. |
| 7 | Multi-note per-source catch-with-a-floor | Unchanged. |
| 8 | Multi-note per-source parallelization (H-fan) | Unchanged, required, measured ~4–5× cut. |
| 9 | Single-note Sol timeout | Resolved into §7's design. |
| 10 | Split `markSessionReady` | Unchanged; also what makes §6's Board Exam fix cheap. |
| 11 | Ready-not-started session state | Flag-on-`IN_PROGRESS` now has a concrete reason to lean that way — §5. |
| 12 | New frontend phase for the pre-screen | Unchanged. |
| 13 | "Continue reviewing" action | Now the preparation screen's *only* button — §4. |
| 14 | In-app notification producer | Trigger resolved to "the Continue Reviewing click" — §4; tab-close-without-clicking remains open (§9). |
| 15 | Deep-link landing path | Now must also handle the seven-day-expired case (§1 step 8, §5's sweep). |
| 16 | Board Exam / Long Exam | Sequenced concretely in §6, not left as "evaluate." |
| 17 | Tier-routing validation for Challenge | Unchanged; baseline is v0.174.0's mini-pin plus Sol-routed template seed. |

## 10. Remaining owner decisions that genuinely block implementation

Most of this document's previously-open items are now resolved by B1–B7 directly (the blocking-wait
reading — confirmed by B1; the two-"Start Quiz"-labels problem — resolved by B4's distinct copy; Option
G — now explicitly B6, Release Implementor's separate track; low reasoning effort — now explicitly B7,
same; the notification-trigger question — answered by B2's own text, not actually open, corrected in §4).
What's left:

1. **Confirm §4's "Stay on this page" removal itself** — this document's recommendation, not yet the
   owner's decision; B4's copy still shows it as written pending this.
2. **§6's release-boundary choice** — whether Board Exam's and Long Exam's confirmation-click-plus-sweep
   fixes land inside v0.175.0 as separate PRs or as their own tiny follow-up release.
3. **The ready-session mode/intent mismatch (§5): resume, replace, or ask?** A seven-day ready Challenge
   session colliding with a later Board Exam request, a retaken Quick Review, or a redo-missed-questions
   request on the same pack. This blocks finalizing the ready-not-started state's design, not just its
   copy.
4. **The quota refund gap (new, this pass): no `decrementChallengeQuizUsageNotBelowZero`-equivalent method
   exists for Challenge today.** `UserUsageService` already has `decrementLongExamUsageNotBelowZero` and
   `decrementBoardExamUsageNotBelowZero` for exactly this purpose (refunding a charge when a session fails
   after being counted), but **no Challenge equivalent** — confirmed by grep, not inferred. Once quota
   moves to session-creation time (item 4), a session that fails (and is retried, per §7) or expires after
   seven days unstarted needs this method to exist, or "Try Again" and a seven-day expiry both risk
   charging quota for a quiz the learner never got. Whether an expired-unstarted quiz should consume
   monthly quota at all is a product choice feeding directly into whether this refund path is required —
   flagging both together since one depends on the other.
5. **Item 17's "+5" scope question (§3)** — does Plus/Pro "+5" inherit Sol once tier routing lands, or
   does it get pinned to a faster model deliberately? The latency data supports either being safe; this is
   a routing-consistency and "no silent downgrade" question, not a performance one.

**Release-size note, unchanged from the previous pass and still worth restating:** this plan's items (a new
session state, a split timer-start action, a notification producer, a deep-link route, multi-note
parallelization plus a floor, a retry-once design, and now a seven-day expiry sweep) are comfortably a full
Tier-H release on their own, before Board Exam/Long Exam's small fixes are even added. Say so explicitly
when this is scoped into a Codex brief, per the rule this session has cited before.
