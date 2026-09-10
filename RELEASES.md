# RELEASES.md - NoteLib

## v0.140.0 - Pending Work in Reach

**Status: In Progress** (kicked off 2026-09-10, base branch `releases/v0.140.0`, cut from `main` after `v0.139.0` merged and tagged)

Theme: a curator arranging a 79-note review set works hundreds of pixels below the only control that commits their arrangement. This puts the commit in reach, and says honestly what is pending.

Source: `docs/claude-plans/authoring-and-quiz-legibility-fix-plan.md` §§5-7 and **§10** — owner-reported 2026-09-05 from real use with screenshots, audited against code, tightened by the owner, then a GPT tightening pass. **⚠️ Read §10 FIRST.** It is the plan's own record of three places where the code contradicts the tightening, and one of them removes a premise this release would otherwise ship on.

**⚠️ THIS IS A LEGIBILITY FIX, NOT A DATA-LOSS DEFECT — stated so the release is not oversold.** Navigation protection already exists and was **verified in code at kickoff** (`study-plan-builder-page-client.tsx:1663-1671`: a `beforeunload` handler plus an in-app click interceptor, both gated on `leafOrderDirty`). **Work is not silently lost today.** The failure is a curator who drags on a long plan, scrolls away from the header that holds Save, and redoes the arrangement. Recoverable, and worth fixing because it lands on the only people actually using the product.

**The trigger is verified live, not assumed:** production holds a **79-note** plan (General Education), two **77-note** plans (Engineering Mathematics), a **76-note** plan (Geotechnical Engineering) and five more at 59+. In the 14 days to 2026-09-10, **12 authors created 1,768 notes** — 7 `BOARD_EXAM` learners (919), the admin account (782) and 4 students (67). This release serves them.

### Planned Scope

1. **The dirty-state sticky bar (frontend).** Shown only while there are pending changes, absent otherwise, holding the pending-state text plus Discard and Save. **⚠️ No two equally-prominent Save controls** — the buttons at `study-plan-builder-page-client.tsx:2496-2515` move INTO the bar; if the card header retains anything it is status text, never a competing primary action.

2. **Copy that describes what is actually pending — `[DECISION]`, owner's call, owed before implementation.** **⚠️ Both obvious wordings are wrong in opposite directions**, per §10 Finding B: `Unsaved changes` **over-claims** (it implies the already-persisted combobox pick is pending), and `Order changes not saved` **under-claims** (`leafOrdersMatch` at `:118-125` compares `noteId` sequence **and** `label`, so a pending drag can also have moved a note between sections). The plan proposes `Arrangement not saved` or `Drag changes not saved`. **The semantic requirement is fixed even though the words are not: the bar must describe drag-originated order AND placement, and must not promise isolation.**

3. **§7 navigation protection — narrowed, and narrowed on evidence.** §7's stated premise (*"current silent loss is unacceptable"*) is **false** and §10 Finding C says so; the handler exists. What remains is real but smaller: the existing `confirm()` offers **two** choices where the owner requires **three**, plus §7's named coverage gap. **Scope it as the gap, not as the original item.**

4. **Backlog Index corrections — three rows that claim open work which has shipped.** **⚠️ Each was found by opening the code, and each would have been offered to the owner as a release candidate.** (a) The **LaTeX (b)** row reads *"BLOCKED until 2026-09-11 … candidate for `v0.96.0`"*; `remark-math` is at `summary-markdown.tsx:3`, shipped **`v0.100.0`, 2026-08-29**. (b) The **public-catalog unbounded read** row reads *"⚠️ STILL OPEN IN CODE, BUT ITS GATE BECAME TRUE AND NOBODY ACTED"*; **all three legs shipped in `v0.119.1` on 2026-09-06** (`d10d92bc` Legs B+C, `542622f1` Leg A) — the gate said *"un-parks the moment `v0.119.0` is signed off"* and the fix landed in the very next release, so somebody acted immediately. (c) That row was **"verified" as unfixed by the `v0.138.0` verification pass**, which is recorded as an error of that pass, not quietly repaired.

### The structural finding this release records

**Every stale row found across `v0.138.0`, `v0.139.0` and this kickoff was stale in the SAME direction — overstating open work — and every one shipped in a release that never re-read the row.** `v0.138.0` added a verification procedure to **kickoff**; nothing updates a Backlog row when the thing it describes **ships**. That is a missing signoff step, and it is the cheap fix. **⚠️ The near-miss that makes this concrete: on 2026-09-10 the onboarding redesign — shipped as `v0.73.0` a month earlier — came within one verification step of being scoped and rebuilt as an 8-screen rewrite of a 2506-line file.**

Anti-drift — locked:

- **⚠️ Autosave-per-drop stays REJECTED. Do not re-propose it.** It raced itself: each drop awaited a save plus a full refresh, nothing gated dragging meanwhile, so a second drag wrote from a diverging base and was clobbered when the first refresh landed. **Two releases were paid to close this.**
- **⚠️ Do NOT change the combobox flush (§10 Finding A).** `handleLeafLabelChange` calls `moveLeafNote(..., deferSave = false, ...)`, so a section pick persists the curator's pending drags too and clears the dirty state. That is deliberate — `CLAUDE.md` records that non-drag mutations must **"flush, never discard"** — and it is the safe direction, because pending work is saved rather than lost. **Requirement 9 is satisfied vacuously, not by isolation; fix the COPY, not the behaviour.**
- **⚠️ Item 4 (immediate section commit) already SHIPPED in `v0.117.0` and is not reopened here.** The reason items 4 and 5 were split stands: immediate commit makes the flush reachable in one click, so the sticky bar will disappear the moment a section is picked. **Benign — the work is saved — and it must not be "fixed".**
- **⚠️ The Challenge Quiz bank-write isolation is NOT in this release.** Verified genuinely unshipped at kickoff (`ChallengeQuizQuestionBankService.java:118-125`; the `REQUIRES_NEW` at `:235` is `releaseClaims`, a different method). It carries `[DECISION]` with three shapes that differ in **failure semantics**, not just mechanics. It needs that decision before it can be scoped, and must not be folded in because it is nearby.
- **⚠️ No `frontend/app/onboarding` work.** The `2026-09-11` checkpoint closed 2026-09-10 as **KILL CRITERION NOT CLEARED**; its own pre-committed wording says **reopen the framing rather than iterate on further onboarding polish**. Do not treat the closure as permission.
- **No new drag-and-drop or animation dependency.** Use the motion vocabulary already in `globals.css`.
- **`globalThis`, never `window` / `self` / `global`** — ESLint enforces it.
- **Collection vocabulary stays profile-aware** — no hardcoded "Study Plan" or "Review Set" in copy.
- **New analytics events go in the `AnalyticsEventType` enum before being fired, with a real fire site** (`v0.116.0` / `v0.117.0` both shipped events that could never fire).

### Shipped

- **The dirty-state sticky bar (frontend).** While drag changes are pending, a bar sticks to the bottom of the builder carrying **`Drag changes not saved`** plus Discard and Save changes. **The Save/Discard controls were MOVED out of the "Your notes" card header, not duplicated** — that header scrolls out of view on a long plan, which is the whole defect, and a test now asserts there is exactly **one** Save and **one** Discard in the document. The header keeps status text only (progress states plus the idle "Drag notes or …s to reorganize."), never a competing action.
- **Copy decided by the owner, with its reasoning pinned in the code.** `Drag changes not saved`, chosen over `Unsaved changes` (over-claims — implies the combobox section pick is pending, and it is not: that path persists immediately) and `Order changes not saved` (under-claims — `leafOrdersMatch` compares `noteId` sequence AND `label`, so a pending drag can also have moved a note between sections). The comment at the bar tells the next reader not to "improve" it without re-reading §10 Finding B.
- **A three-choice navigation dialog replaces the two-choice `confirm()` (frontend).** In-app link clicks while dirty now offer **Save and leave**, **Discard and leave** and **Keep editing**. The old dialog offered only "lose it" or "stay", so a curator who had genuinely finished had no way to leave *with* their work. **⚠️ A FAILED SAVE DOES NOT NAVIGATE** — the dialog stays open, says nothing was lost, and offers a retry; a dialog that navigated on a failed save would be a *new* way to lose pending work, strictly worse than what it replaced. `beforeunload` stays a **warning only**, deliberately: the browser permits no custom actions and no reliable async save, and promising a save path the page lifecycle cannot guarantee is worse than warning honestly.
- **Modified clicks are deliberately not intercepted.** A cmd/ctrl/shift/alt or middle click, and any `target` other than `_self`, opens elsewhere and leaves the builder and its pending drags exactly where they are — interrupting it would be a dialog for a problem that does not exist, and would cost the curator the new tab. Guarded by its own test.
- **Verification: 65 tests in `app/collections/[id]/builder/page.test.tsx` (5 new), `tsc --noEmit` clean, `npm run lint` 0 errors.** **Three mutations were applied and each was killed by a named test:** navigating despite a failed save → *"does NOT navigate when Save and leave fails, and says so"*; intercepting modified clicks → *"leaves a modified click alone, because it does not replace this page"*; restoring a second Save control in the header → *"shows the sticky bar only while changes are pending, and never a second Save control"* (plus four pre-existing tests). **⚠️ Five existing tests were CORRECTED rather than left passing for the wrong reason** — four pinned the old `"Save order"` label and one pinned the old `confirm()` two-choice guard.

- **`/signoff` gains a Backlog-row closure gate, which is the structural fix for five stale rows (docs).** For every item a release ships, the row that described it must be opened and marked shipped **with a `file:line`** — plus its `Gate` cell when the release satisfied it, and **never from the release notes alone**. **⚠️ `v0.138.0` added a verification procedure to KICKOFF; nothing updated a row when the thing it describes SHIPPED**, so a row written at proposal time was never touched again. Every stale row found so far was stale in the **same direction — overstating open work** — which is what makes it dangerous rather than untidy: such a row gets offered to the owner as a release candidate. Recorded in `.claude/commands/signoff.md` and `CLAUDE.md`.
- **Eight Backlog rows had content in the wrong columns, and the repair recovered eight real dates (docs).** Distinct from `v0.138.0`'s four-column class. Three rows were missing a **`Source`** cell (folded into `Item`); five were missing a **`Gate`** cell, so `v0.138.0`'s appended `⚠️ never stamped` shunted a **genuine `Last reviewed` date into the `Gate` column** — where kickoff step 9 would read it as a gate condition. Each is repaired in place, and the five carry an explicit note that they never had a `Gate` cell rather than an invented one.
- **The Study Plan Builder section-label refresh loop is confirmed SHIPPED — the fifth stale row of this cycle, and the first found by the new gate (docs).** Verified at `study-plan-builder-page-client.tsx:498-531`: the guard now compares through the shared `canonicalSectionLabel`, holds a `lastRequestedLabelRef` keyed on both current and requested label, and reads `onLabelChangeRef` instead of putting a re-created callback in the dependency array — which was what made the effect re-run on every render. **⚠️ Its ingress question is still unresolved and is NOT closed by the mechanics fix.**
- **Two "unmeasured by decision" rows are now measured, both by read-only production `SELECT`s (docs).** **⚠️ Both rows also claimed the query was *"the owner's to run"* — that is wrong and is corrected: a `LIKE` scan is a `SELECT`, which `CLAUDE.md` permits; only WRITES are the owner's.**
  - **Contaminated note titles: the debt is 89 notes across 8 programs, and it is a closed population.** Discriminating on titles ending in the note's **own** `course_program` — the Bulk Generate overwrite shape — rather than the raw `% in %` scan, which returns 1,077 mostly-legitimate matches. Earliest 2026-05-23, **latest 2026-08-02, none since**. ⚠️ It stopped a month *before* `v0.120.0` shipped, so the row's claim that `v0.120.0` is what stopped it is **not** established by this read.
  - **⚠️ The raw-LaTeX row is not the closed curator backlog it describes — it is a live generation defect.** Re-running its own `v0.74.0` query: `NEEDS_FIX` = **15** (down from ~23, as the row predicted), `MIXED_CHECK_IT` = 189, `LIKELY_OK` = 224. **But 7 of the 15 were generated in the last 14 days**, and 184 of the 189 `MIXED_CHECK_IT` since `v0.74.0` deployed. Its `Math notation` prompt rule **reduces but does not eliminate** undelimited math, so hand regeneration cannot drain the population while the source keeps producing it. The row's *"owner/curator work, not engineering"* framing is no longer accurate; the engineering half is unscoped and should be triaged before more regeneration is spent.

### Known limitations

- **⚠️ The navigation interceptor still covers `a[href]` clicks only — named here rather than left to be discovered.** Programmatic `router.push`, browser back/forward, and any navigation from a control that is not an anchor are **not** covered. §7 of the plan put extending this in scope for Release B and instructed that the residual ship as a named limitation if it could not be done; App Router makes `popstate` interception unreliable enough that half-building it would give a false sense of coverage. **On those paths pending drags ARE lost silently** — `beforeunload` covers only refresh and tab close, not an in-app programmatic navigation, so nothing warns the curator.
- **⚠️ The chosen copy names an input device, and one pending path is not a drag.** The plan justified `Drag changes not saved` as *"everything pending came from a drag"*. That is not exactly true: the keyboard **Move up / Move down** controls are the accessible equivalent of dragging and also defer, so a curator who never touches a pointer can still be shown this wording. The owner chose it knowing the alternatives; `Arrangement not saved` is the candidate that covers both without naming a device. Recorded in the code comment beside the bar.
- **⚠️ THE STICKY POSITIONING ITSELF IS NOT COVERED BY ANY TEST, AND THE GREEN SUITE IS NOT EVIDENCE ABOUT IT.** jsdom computes no layout, so all 65 tests pass whether the bar pins to the viewport or sits in normal flow at the end of the page — and normal flow is precisely the failure this release exists to fix. What *is* verified: no `overflow` rule on `html`/`body`/`layout.tsx` would break `position: sticky`, and the bar is a direct flow child of the page's `<main>`, so it resolves against the document scroller. **This is the `v0.116.0` / `v0.117.0` class — a behaviour change whose suite executes none of the behaviour — and it is recorded as unverified rather than written up as shipped.** It wants one look in a real browser against a long plan.
- **⚠️ The cross-section drag case has no UI test, because the suite cannot reach it.** `leafOrdersMatch` comparing `label` is what makes the copy honest about section placement, but the only deferred path that changes `label` is `handleLeafDragEnd`, and this suite has **no dnd-kit simulation at all** — every existing "drag" test uses the arrow controls, which are within-section. Rather than hand-build a state no code path in the test can produce (the `v0.116.0` / `v0.117.0` failure), the gap is recorded. The within-section pending case **is** covered.

## v0.139.0 - Reopened

**Status: Released** (kicked off 2026-09-10, signed off 2026-09-10, base branch `releases/v0.139.0`, cut from `main` after `v0.138.0` merged as #1363 and tagged)

Source: `docs/claude-findings/2026-09-10-september-checkpoint-reads.md` — the reads that came due, run at this kickoff **before** scope was proposed. **Read it first: item 1 is not a metrics chore, it is a pre-committed rule firing.**

Theme: a checkpoint fired, so the thing it was watching gets reopened — and the detector that missed a deploy learns to report what it found.

### ⚠️⚠️ THE `v0.72.0` RETENTION CHECKPOINT FIRED ITS KILL CRITERION

**VERIFIED read-only, 2026-09-10, window 2026-08-11 (deploy) → 2026-09-09: nine learners were shown the review-commitment prompt. ZERO committed. One declined.**

**⚠️ THE ZERO IS REAL, AND TWO INDEPENDENT INSTRUMENTS AGREE — this was checked first, because `v0.116.0` and `v0.117.0` both shipped events that could never fire.** `COMMITTED` and `DECLINED` are emitted from **the same line** (`review-commitment-prompt.tsx:102`, a ternary); `DECLINED` fired once, so the call site provably executes and the other branch simply never happened. Independently, `SELECT count(*) FROM users WHERE cardinality(review_days) > 0` returns **0** — **the entity table agrees with the event stream, which rules out analytics delivery loss**, the bias that made `v0.80.0` necessary.

**The pre-committed rule, quoted from the row and written before the read:** *"the return-loop framing reverts to **unconfirmed** and is **reopened rather than iterated on with further nudge tuning**."*

### ⚠️ OWNER OVERRIDE, RECORDED RATHER THAN SMOOTHED OVER

**The owner elected to REDESIGN THE PROMPT rather than only reopen the framing — which is the "nudge tuning" the pre-committed rule names.** They were told that before choosing. It is recorded here because a pre-committed rule that is quietly stepped over stops being a rule, and the next checkpoint inherits the precedent. **The consequence: this release ships a redesign on a `n=9` signal, so it owes a checkpoint with a real denominator — see below.**

**⚠️ WHY `n=9` IS SMALL BUT NOT NOTHING, STATED HONESTLY:** at nine impressions a modest true commit rate (~10%) is not excluded by chance; a high one (≥30%, P(zero) ≈ 4%) effectively is. **The redesign is therefore a bet, not a correction.**

### Planned Scope

1. **Instrumentation first — the redesign is unmeasurable without it, and this is NOT optional.** (a) The prompt fires **only after a successful save** (`review-commitment-prompt.tsx:96-107`), so **8 of 9 learners vanished with nothing recorded** and the funnel cannot tell *ignored* from *considered and rejected*. Add a dismiss/abandon event. (b) All 11 `DUE_CONCEPTS_DIGEST_LANDED` rows carry a **NULL `user_id`**, so the checkpoint's second metric — *"digest → first answer **among committers**"* — **was never computable**. Give the event its user.
2. **Redesign the commitment ask** (owner decision, 2026-09-10). **⚠️ The design constraint that matters is the trigger, not the copy:** it renders on `isFirstCompletedSessionEver === true`, so it is **ONE impression per learner, ever**, asking for a weekday multi-select plus an exam date **immediately after a first session, before the learner has seen any payoff**. Nine impressions in a month is the trigger being narrow, not the copy being weak.
3. **`scripts/check-deploys.sh` — report a confirmed drift as drift.** **VERIFIED empirically against today's live miss:** the script prints *"VERCEL: serving 98ef1955, but origin/main is 05c367c4 — BEHIND"* and then **exits 2**, which its own contract defines as *"could not check"* rather than *"drift"*. `drift=1` is set at `:63` and discarded by the `exit 2` at `:82`. **A caller reading the exit code — `/signoff`, or any CI job — sees "I could not look" when the truth is "Vercel is definitively behind."**

### ⚠️ Vercel and the deploy-latency false positive — CORRECTED

**⚠️⚠️ CORRECTED 2026-09-10, SAME DAY: VERCEL DID NOT MISS `v0.138.0`. It deployed `05c367c4` at 01:21:06Z, **4 minutes 24 seconds after the 01:16:42Z merge** — it was IN FLIGHT when this session checked, and the check was read as an absence. **The true record is ONE confirmed miss (`v0.136.0`), not two of three: `v0.137.0` and `v0.138.0` both auto-deployed normally.** ⚠️ **THIS IS `v0.137.0`'s OWN RULE BROKEN THE DAY AFTER IT WAS WRITTEN** — a production-state reading taken at one instant and asserted as a standing property. **⚠️ AND IT IS A REAL LESSON FOR THE DETECTOR, NOT JUST AN EMBARRASSMENT: testing for ABSENCE requires waiting past the normal deploy latency, or the test manufactures its own false positive.** Observed latency is ~2–5 minutes on both platforms. **Item 3's defect is UNAFFECTED and still real** — it was reproduced by mutation against the pre-fix script, independently of any live drift.** 

**Original (wrong) reading, kept for the record:** Render auto-deployed `v0.138.0` (`dep-dah09v15efls739b7t9g`, `new_commit`, **live** 01:18:59Z); **Vercel has no Production deployment for that commit at all.** Missed `v0.136.0`, fired for `v0.137.0`, missed `v0.138.0`.

**⚠️ THE CONSEQUENCE WAS COSMETIC THIS TIME AND THAT IS LUCK, NOT DESIGN:** `v0.138.0`'s only frontend change is the `package.json` bump and **no controller or `lib/api` file changed**, so there is no API-form skew of the kind that killed Your Impact in `v0.136.0`. **⚠️ THE UPSTREAM CAUSE IS NOT FIXABLE FROM THIS REPO** — transient GitHub push-event delivery loss to the Vercel GitHub App, INFERRED and unchanged from the `v0.136.0` diagnosis. **This release fixes the reporting, not the cause, and must say so.**

### ⚠️ Anti-drift

- ❌ **Do NOT ship item 2 without item 1.** A redesign that cannot be measured reproduces the exact position this release is in — and the read that would judge it is already blind in two places.
- ❌ **Do NOT re-date the `v0.72.0` proximal checkpoint as though it had not fired.** It fired. The row records FIRED plus the owner override; a silent re-date would erase the only evidence the rule was overridden.
- ❌ **Do NOT claim this release fixes the Vercel auto-deploy.** It fixes the exit contract. The cause is upstream and stays unfixed.
- ❌ **Do NOT make `/actuator/metrics` public to make a checkpoint readable.** The `v0.134.0` row's *"externally readable"* claim is simply WRONG — `application.yaml`'s own comment says *"not permitAll … stays authenticated-only"*. **Correct the row, not the security posture.**
- ❌ **Do NOT add a new analytics event without a fire site** — that is the `v0.116.0`/`v0.117.0` defect, and this release's own headline finding only survived scrutiny because the fire site was proven live first.
- ❌ **No Learning Connections work** (`[CHECKPOINT — due 2026-09-19]`, denominator ONE). **No `frontend/app/onboarding` work before the `2026-09-11` read.**
- ⚠️ **The four `FAILED` notes from `v0.138.0` are still the OWNER's to re-run** — the backend is live on `v0.138.0` as of 01:18:59Z, so that clock has started.

### ⚠️ Pre-declared guards

- **Item 1a:** assert the dismiss event fires on the **close/ignore path specifically** — ⚠️ a test that only asserts "some event fires" passes under the current code, which already fires on save.
- **Item 1b:** assert the persisted `DUE_CONCEPTS_DIGEST_LANDED` row **carries a non-null `user_id`** — ⚠️ not that the client sent one; the 11 existing rows prove the gap is at persistence or auth-resolution time.
- **Item 3:** assert **exit code 1** when Vercel is behind and `RENDER_API_KEY` is absent. ⚠️ **A test asserting only that the message is printed passes under the defect** — the message already prints today; the exit code is the whole bug.
- **Item 2:** at least one test must exercise the trigger condition, not just render the component with `visible=true` — the trigger is the finding.

### Verification tier

**Three items, one of them docs-adjacent.** Items 1 and 3 are small and testable; item 2 is a frontend redesign across the five surfaces that render the prompt. **Tier: one `advisor()` call on the diff**, plus the four guards above. **⚠️ It rises to one scoped cold agent if item 2 grows a backend surface** — the `reviewCommitmentOutstanding` flag and `users.review_days` are already there, so it should not.

### Routing

**CODEX for items 1 and 2** — frontend across five call sites plus a backend analytics change; more than five files. **CLAUDE CODE inline for item 3** — one shell script, one exit path.

### Scope completeness — each planned item against the code that implements it

| # | Planned | Verdict | Evidence |
|---|---|---|---|
| 1 | Instrumentation first — a dismiss/abandon event, and a `user_id` on `DUE_CONCEPTS_DIGEST_LANDED` | **SHIPPED** | `REVIEW_COMMITMENT_DISMISSED` at `AnalyticsEventType:48` with **one real fire site** (`review-commitment-prompt.tsx:97`); the 401 at `AnalyticsController:28` |
| 2 | Redesign the ask — re-showable trigger, committing becomes an upgrade | **SHIPPED, and CHANGED mid-release** | `V144`; `AuthService#isReviewCommitmentPromptEligible`; `MeController:39`; `RetentionService:51`. **⚠️ Changed twice against evidence — see below** |
| 3 | `scripts/check-deploys.sh` reports a confirmed drift AS drift | **SHIPPED** | `scripts/check-deploys.sh` precedence block; `scripts/check-deploys.test.sh` (9 cases) |

**⚠️ ITEM 2 CHANGED TWICE AFTER IT WAS SCOPED, AND BOTH REVERSALS ARE RECORDED RATHER THAN SMOOTHED INTO THE ORIGINAL PLAN.** (1) The first design re-triggered on *return after a gap*; its own query refuted it — only **7 of 136** stranded learners had returned in 14 days, so any in-app trigger tops out at 7–13/month. (2) `advisor()` then found, **before the Codex prompt was written**, that the ask had **no benefit to offer at all** — `isEligibleReviewDay` returns `true` for empty `review_days`, so choosing days *restricted* eligibility rather than granting reminders. A one-tap *"remind me"* button would have shipped as a no-op. The owner then chose to make committing genuinely mean something, and later to stop asking learners whose digest is off. **The release describes what was built, not what was first proposed.**

### Shipped

- **Item 1 — commitment and digest instrumentation.** Review-prompt abandonment now emits
  `REVIEW_COMMITMENT_DISMISSED` with `exit=pagehide|unmount`, deduplicated to one dismissal per
  impression and suppressed after either saved outcome. `DUE_CONCEPTS_DIGEST_LANDED` now requires a
  resolved principal: an expired bearer on the otherwise-public analytics endpoint receives `401`,
  allowing the existing analytics refresh-and-retry path to persist the landing with its `user_id`;
  events that genuinely originate anonymously remain accepted.
- **⚠️ THE `401` EXCEEDED THIS ITEM'S STATED CONSTRAINT AND WAS ACCEPTED ON REVIEW — recorded as an expansion, not as the plan.** The prompt said the fix *"must not delay or drop the `LANDED` event"* and *"do not make the analytics endpoint reject anonymous events"*. The delivery does both, narrowly: a new `AuthenticationRequiredException`, a new status on a `permitAll` endpoint, and a behaviour change to a shared analytics path — none of which item 1 was scoped for. **It was accepted because the diagnosis is correct and the alternative is worse** (an unattributable landing can never answer the checkpoint's question), and because the rejection is scoped to one event type with `anonymousAnalyticsEvents_remainAccepted` guarding the boundary. **The reasoning is stated so a later reader does not mistake it for what was asked.**
- **⚠️ AND THE `401` HAS A COST THE NOTE MUST NOT OMIT: a landing whose token cannot be refreshed is now DROPPED, where it previously persisted with a NULL `user_id`.** `dueConceptsDigestLanding_withoutResolvedPrincipal_requestsAuthenticationRetry` asserts exactly that — 401 **and zero rows**. The trade is deliberate: an unattributable landing could never answer the checkpoint's question (*digest → first answer **among committers***), and the refresh-and-retry path it now reaches **already existed** at `lib/api.ts:3351` and was simply unreachable while the endpoint answered `200` to an expired bearer. **⚠️ But it changes what a landing COUNT means** — the 619-sends/11-landings ratio is not comparable across this change, and `trackAnalyticsEvent` still returns without retrying when `visibilityState === "hidden"`. **⚠️ FOR THE DISMISS EVENT THAT IS NOT AN EDGE CASE, IT IS THE COMMON PATH: abandonment fires on `pagehide`, when visibility is hidden BY DEFINITION**, so a dismissal sent with an expired token is lost **every time**, not occasionally. The `unmount` exit can still retry. **This is a known limitation of item 1's headline metric and is recorded rather than papered over** — the dismiss count is a floor, not a total.
- **Hardened the abandonment effect against a false positive found in the audit, and pinned it with a test.** `trackDismissed` closed over `noteId`, and **four of the five call sites pass `note?.id ?? null`** — so a `null → value` transition while mounted would run the effect's **cleanup**, firing a dismissal the learner never performed, with a stale `entityId`, and latching the state machine to `dismissed` so the real abandonment could never be recorded. Today's ordering makes it unlikely (the prompt renders only after a completed session), **but that is ordering, not a guarantee.** `noteId` is now read through a ref and the effect owns an empty dep array. ⚠️ **Mutation-verified: closing over `noteId` again fails `does not report abandonment when noteId resolves while the prompt is open`, and nothing else.**
- **The dismissal guard is discriminating, verified by mutation with the killing test named.** Deleting the `resolved` transition on save — so a saved outcome would later report as abandonment — fails **`does not report a saved decline as abandonment`** and only that test. ⚠️ **A test asserting merely that "some analytics event fires" passes under the defect**, because save already fired one; that is why this one asserts the absence after a save.
- **The forbidden over-broad change is guarded too.** Rejecting *every* anonymous analytics event — which the prompt explicitly ruled out, since other callers legitimately have no user — fails `anonymousAnalyticsEvents_remainAccepted`.
- **The zero first-answer result is genuine engagement data, not a dead query-string gate.** The email
  links directly to `/notes/{noteId}/quick-review?source=due-concepts-digest`; that route renders the
  quiz without navigation, session creation does not replace the URL, and the only legacy
  `/study-packs/{id}` redirect copies the full query string. The answer handlers therefore still see
  `source=due-concepts-digest`. No first-answer code changed. **⚠️ VERIFIED IN THE AUDIT RATHER THAN TAKEN ON REPORT** — the one `router.replace` on that page (`quick-review/page.tsx:396`) is the legacy `/study-packs/` → `/notes/` redirect, it sits inside an error handler, and it **explicitly copies the query string** into its target; the other two `router.push` calls are exits to `/dashboard`. **So the dead-gate hypothesis this release opened with is REFUTED, and the zero is a real product finding: 619 digests sent, 11 landings, 0 first answers.**
- **Documented the commitment surface and its actual scheduling meaning.** `users.review_days` is
  initially collected after a completed session, later editable in Settings, and narrows eligible
  digest weekdays; null or empty days do not disable the digest.
- **Item 2 — the commitment prompt is re-askable and committing is now an upgrade.** Server-owned
  eligibility allows an unanswered learner to see the prompt after a later completed session, with a
  14-day cooldown and a lifetime cap of three impressions. A transactional, row-locked
  `POST /me/review-commitment/prompted` records each eligible impression without writing
  `review_commitment_prompted_at`, which continues to mean *answered*; client `sessionStorage`
  deduplication and the server eligibility update make the impression idempotent across remounts and
  duplicate requests.
- **All five completion call sites now use the same server decision.** Long Exam, Adaptive Practice,
  Board Exam, Challenge Quiz, and Quick Review no longer pass `isFirstCompletedSessionEver` into the
  prompt. The prompt explains the existing weekly nudge and the benefit of choosing days, keeps
  Monday/Wednesday/Friday selected by default, and keeps the BOARD_EXAM exam-date field.
- **⚠️ CORRECTED IN THE AUDIT: the exam date was NOT "kept optional" — it was REQUIRED, and this release makes it optional.** A BOARD_EXAM learner previously could not commit at all without supplying one (`review-commitment-prompt.tsx:121-124`, *"Choose your exam date before setting your review plan."*). That gate is now removed, because the prompt's stated purpose is review days and the release brief said the exam date must not block the primary action. **⚠️ THE CONSEQUENCE IS A WEAKER COLLECTION PATH AND IT IS NAMED HERE RATHER THAN LEFT TO BE DISCOVERED: 79 of 185 BOARD_EXAM accounts (43%) still have a NULL `exam_date`, and this prompt was one of the few places that collected it.** Fewer will now be captured. The field still renders and still saves when filled. **Mutation-verified: restoring the requirement fails `lets a BOARD_EXAM learner commit while the optional exam date is empty`, so the change is deliberate and covered rather than incidental.**
- **⚠️ The completion gate moved from the component to its callers, which is a contract change worth stating.** The prompt used to hide itself unless `isFirstCompletedSessionEver` was true, so it was safe to render anywhere; server-owned eligibility is about the **ask**, not about whether a session just finished. All five call sites render inside a completion branch (`isComplete`, a `masteryReport`, or a `result`), **verified individually in the audit**, so behaviour is unchanged today — but a sixth call site placed outside such a branch would show the prompt on page load and burn one of three lifetime impressions. The contract is now documented at the component.
- **Choosing review days now improves the due-concepts digest schedule without removing anyone's
  existing digest.** Learners with null or empty `review_days` retain every-day eligibility and the
  seven-day cooldown. Learners with chosen days remain eligible only on those weekdays and use a
  one-day cooldown, allowing a digest on each chosen day when concepts are due. This may spread future
  sends across the week, but it does **not** fix R1: non-committers keep the synchronized default and
  committers can receive more messages.
- **Mutation-verified, killing tests named — the two destructive changes this design makes available are both guarded.** Flipping `isEligibleReviewDay`'s empty case to `false` — the naive reading of *make the commitment mean something*, which would cut off **all 115 current digest recipients** — fails four tests, including a **pre-existing** one (`findDueConceptsDigestUsers_nullAndEmptyReviewDaysKeepExistingScheduleEligibility`) that was already protecting it, plus `sendDueConceptsDigestEmails_stillSendsToAnUncommittedLearner`. Making an impression stamp `review_commitment_prompted_at` — which would silently resolve **395 outstanding rows** and close the ask permanently for every one of them — fails `recordReviewCommitmentPrompted_isIdempotentAndKeepsTheCommitmentOutstanding`, and only that test.
- **⚠️⚠️ THE SCOPED COLD AGENT CONFIRMED ALL SIX NAMED CLAIMS AND FOUND TWO DEFECTS BEYOND THEM — both are fixed here, and the second is the more serious.** This is the tier the owner re-decided for item 2, and it earned its cost.
- **⚠️ THE PROMPT TOLD 64% OF THE USER BASE SOMETHING FALSE.** The copy asserted *"You already get a weekly nudge when concepts are due"* unconditionally, but the digest is gated on `dueConceptsDigestRemindersEnabled` **and** a verified email (`RetentionService:188`). **VERIFIED read-only: 252 of 396 accounts have that preference OFF; 255 would have seen the false line; 79 of those are otherwise prompt-eligible.** ⚠️ **And for them the whole ask is INERT — choosing days changes nothing, because no digest is sent either way.** `MeResponse` already carried the flag and the component never read it. **⚠️ OWNER DECISION, SAME DAY: DO NOT ASK WHEN THE DIGEST IS OFF.** The conditional copy was an interim fix and is gone; `dueConceptsDigestRemindersEnabled` is now part of **server** eligibility (`AuthService#isReviewCommitmentPromptEligible`), which is the same preference `RetentionService` gates the digest on. **That makes the prompt's claim true BY CONSTRUCTION rather than by wording** — the off-branch became unreachable and was deleted rather than left as dead code, because dead code is how a false claim quietly returns. The invariant is documented at both ends, with the instruction to change them together or not at all. **⚠️ THE REACH COST IS REAL AND MEASURED, NOT WAVED AWAY: the eligible pool drops from 136 to 57.** But near-term in-app reach moves by **one** learner (7 → 6), because the 79 excluded were largely not returning — and every one of them was being asked to configure something that could not affect what they receive. **All 57 who remain are email-verified**, so no further clause was needed. ⚠️ **Do not "restore reach" by dropping the clause, and do not make committing switch the preference on — that is an email-consent change and is explicitly out of scope.** Mutation-verified: dropping the clause fails `getMe_doesNotOfferTheCommitmentPromptWhenTheDigestIsOff` and `recordReviewCommitmentPrompted_doesNotCountAnImpressionWhenTheDigestIsOff`, and nothing else.
- **⚠️ The new endpoint's CLIENT request shape was untested, which is the `v0.119.0` defect class exactly.** All five frontend suites mock `@/lib/api` wholesale, so not one line of the real request executed — no method, no headers, no body — while the repo already had **sixteen** `lib/api-*.test.ts` files establishing the pattern. `lib/api-review-commitment.test.ts` now pins it. **⚠️ THE DEMONSTRATION IS THE POINT: dropping the `Content-Type` — which makes Spring reject the request before the controller is entered — fails the new test and is MISSED by all sixteen component tests, which pass.** That is `v0.119.0` reproduced on demand.
- **One claim was confirmed for the wrong stated reason, and the agent said so.** `POST /me/review-commitment/prompted` cannot inflate the count — but not because `findByIdForUpdate` prevents a race. Under OSIV the `UserEntity` is already managed before the lock is taken (the anti-pattern `UserRepository:66-75` documents against itself), so two concurrent impressions read the same pre-lock state and both compute the same `+1`. **It under-counts rather than over-counts**, which is safe for a cap, and is recorded so nobody later "fixes" it on a wrong mental model.
- **⚠️⚠️ THE PRE-SIGNOFF PRESSURE TEST WAS OWED, WAS NEARLY SKIPPED, AND FOUND A CROSS-PR DEFECT — the owner asked for it before signoff, correctly.** The per-item cold agent did NOT discharge it: that one was scoped to item 2's diff and never saw item 1's code. **Items 1 and 2 both rewrote `review-commitment-prompt.tsx`**, which is precisely the interaction class `CLAUDE.md` says a diff-scoped review structurally cannot see.
- **⚠️ THE FINDING: a dismissal could fire for a prompt nobody ever saw.** `globalThis.sessionStorage` **THROWS** — it does not return null — when site data is blocked (Chrome *block all cookies*, some embedded webviews, restricted iOS contexts), and **optional chaining guards a null storage object, not a throwing accessor.** Item 2 put that access **inside item 1's guarded block**, after `shownTrackedRef` had advanced to `"shown"` and `promptVisibleRef` to `true`, but **before `REVIEW_COMMITMENT_PROMPT_SHOWN` fires**. The outer `.catch` reset only `visible`. Net: a later pagehide/unmount emitted `REVIEW_COMMITMENT_DISMISSED` with **no matching `PROMPT_SHOWN`** — inflating the exact abandonment funnel item 1 was built to measure. ⚠️ **Neither PR's review could have caught it: one supplied the guard, the other supplied the throw.** ⚠️ **And the repo already knew** — `lib/guidance.ts:9-33` wraps every storage access in try/catch; this was the only frontend access that did not. Fixed with total accessors; **mutation-verified** — restoring the unguarded form fails `still reports the impression, and never a phantom dismissal, when storage throws`, and nothing else. **No existing test could have caught it: jsdom's `sessionStorage` never throws.**
- **⚠️ A user-facing claim went stale on a file that appears NOWHERE in this release's diff.** `settings/page.tsx:971` still read *"A weekly reminder when concepts are due"* — false for a committed learner, who can now receive up to **seven** a week — and `:917` described only the no-days case. **`docs/features/email-preferences.md`, updated BY THIS RELEASE, points learners at that exact surface.** This is the *sweep by SURFACE, not by diff* failure `CLAUDE.md` records as having cost three releases running; it has now cost a fourth. Both strings corrected, and `retention-emails.md` now carries the standing obligation so the next cadence change sweeps the surface.
- **⚠️ Three findings are RECORDED, NOT FIXED — deliberately, because shipping unreviewed behaviour at signoff is worse than a stated limitation.** **(a) No volume ceiling on the digest:** `resolveReengagementBudget` gates the inactivity dispatch only, so the per-learner ceiling moves 1/week → 7/week with nothing capping it. **Zero impact today (0 accounts have chosen days), so it is a forward exposure — and it compounds R1**, whose cause this release already attributed to this same producer. **(b) A below-the-fold impression burns one of three lifetime chances:** the prompt renders inside a `weakConceptsRef` block far down long result pages — challenge-quiz even has a button that *scrolls to it*, which is in-repo evidence it is off-screen. Before item 2 an unseen render cost nothing; now it permanently consumes an impression and starts a 14-day cooldown. **This corrupts the checkpoint's own denominator and is named in its clause.** **(c) `PROMPT_SHOWN` and `DISMISSED` can carry different `entityId`s** — the release's own test fixture demonstrates the mismatched pair. Analytics-only.
- **⚠️ One overstated claim corrected rather than defended.** A code comment said the prompt's copy is true *"by construction"*. It is not: the digest audience is preference **AND** verified email **AND** active status, while eligibility checks only the preference. Three accounts today have the preference on with no verified email. Now documented as true-in-practice, not proven.
- **⚠️ Item 3's guard is MANUAL-ONLY and the release says so rather than implying enforcement.** `scripts/check-deploys.test.sh` is referenced nowhere outside itself — **this repo has no `.github/workflows` at all** — so nothing runs it automatically. The `/signoff` change added a wait warning, not a trigger.
- **The reach claim stays bounded by the production read.** This redesign compounds for future
  learners by giving them more than one chance to answer. It does **not** recover the 128 already
  stranded learners who no longer open the app; reaching them requires email work still blocked by R1.
- **Item 3 — `scripts/check-deploys.sh` reports a confirmed drift AS drift.** The precedence is now explicit and commented: **drift > unknown > ok**. Both platforms' "cannot check" paths set a flag instead of exiting early, so neither short-circuits the other — a confirmed Vercel drift survives a missing `RENDER_API_KEY`, and a confirmed **Render** drift now survives an unreadable Vercel, which the old order could not even reach.
- **The guard asserts the EXIT CODE, because the message was never the bug.** `scripts/check-deploys.test.sh` establishes a shell-test convention this repo did not have (no `.test.sh`, no CI workflow existed). It stubs only `gh`, `curl` and `git` on `PATH` — **`jq` stays real, because the script's `jq` filters are part of what is under test** — and covers nine exit-code cases.
- **Mutation-verified against the pre-fix script, with the killing cases named.** Restoring the original makes exactly two cases fail: *"Vercel BEHIND + no `RENDER_API_KEY` → DRIFT"* (`exit=2 want=1`) and *"Render BEHIND + Vercel API failure → DRIFT"*. The other seven pass under both, correctly — they were never affected. ⚠️ **The failing output reproduces the real symptom verbatim**: it prints `VERCEL … BEHIND` and still exits 2. **A test asserting the message passes under the defect; that is why the guard asserts the code.**
- **⚠️⚠️ THE MOTIVATING EXAMPLE WAS WRONG, AND THE CORRECTION IS THE MOST USEFUL THING IN THIS ITEM.** This session claimed Vercel had **missed** `v0.138.0` and had missed *"2 of the last 3 release merges"*. **Both false.** Vercel created the deployment at **01:21:06Z against a 01:16:42Z merge — 4m24s** — and Render went live at 01:18:59Z. **An in-flight deploy was read as an absence**, ~4 minutes after merge, and a release section was scoped around it before it was caught. **The true record is ONE confirmed miss (`v0.136.0`).**
- **⚠️ That broke `v0.137.0`'s own rule the day after it was written** — *a claim about production state is a snapshot, not a fact.* An instantaneous reading was asserted as a standing property. **Recorded rather than quietly fixed, because this is the second consecutive release in which a claim reached a tracker before it was re-read.**
- **⚠️ The generalizable lesson went into the code, not just the write-up: a test for ABSENCE must wait past the thing's normal latency or it manufactures its own false positive.** Observed auto-deploy latency is **~2–5 minutes** on both platforms. The script cannot know when you merged, so it cannot enforce the wait — **`/signoff` now states it, and the script's header explains why.**
- **⚠️ WHAT THE 9 PASSING GUARDS DO AND DO NOT COVER, because "9 passed" invites over-reading.** Every case is **stubbed** — `gh`, `curl` and `git` are faked on `PATH`. **The drift path is verified by MUTATION under stubs and has NOT been observed against a live drift**: the one live run (`RENDER_API_KEY` absent, 2026-09-10) exercised the *unknown* path, because Vercel matched `main` by then. **No drift was manufactured in production to test it, deliberately.**
- **⚠️ The defect itself was never contingent on the false reading.** It was reproduced by mutation under stubbed conditions, so item 3 stands exactly as scoped — but without the correction the release would have described a platform problem that does not exist.

## v0.138.0 - Stated and Enforced

**Status: Released** (kicked off 2026-09-09, signed off 2026-09-10, base branch `releases/v0.138.0`, cut from `main` after `v0.137.0` merged as #1360 and tagged)

Sources: `docs/claude-findings/2026-09-09-regeneration-invalid-title-failures.md` (the diagnosis) and `docs/claude-plans/2026-09-09-generated-title-bound-and-failure-observability-plan.md` (the fix plan). **Read the findings first — Leg A looks like a nice-to-have until you have seen its §4.**

Theme: make what is *enforced* match what is *stated* — in the prompt contract, and in the backlog.

### ⚠️⚠️ A LIVE PRODUCTION DEFECT, DETERMINISTIC, WITH FOUR NOTES STUCK `FAILED` RIGHT NOW

Owner-reported 2026-09-09. Seven `LLM_INVALID_OUTPUT` failures across four notes, all `BOARD_EXAM_REVIEW`. **⚠️ Batch `6a7a8fa9` is a RETRY of the three that failed in `f5c690f5`, and all three failed identically — so this is DETERMINISTIC and retrying is not a workaround.**

**The probable cause is a bound the model is never told.** The title is validated at **12 WORDS** (`MAX_GENERATED_NOTE_TITLE_WORDS`, `OpenAiLlmStudyPackService:75`), while the published contract carries **no numeric bound at all** — schema `maxLength: 160` **characters**, plus prose "concise and specific". The same prompt file already templates `{MAX_ITEM_CHARS}` (3×) and `{MAX_WORDS}`. **⚠️ AND THE CODEBASE DOCUMENTS THIS EXACT ANTI-PATTERN AGAINST ITSELF:** `OpenAiLlmStudyPackService:2553-2561` records removing an unpublished word ceiling from *bullets* and instructs *"do not reintroduce a bound the model cannot see."* **The title kept its.**

**⚠️⚠️ BUT THE FAILING BRANCH IS INFERRED, NOT CONFIRMED — AND THAT IS WHY LEG A COMES FIRST.** `normalizeGeneratedNoteText` throws on *either* a blank value or a word count outside `1..12`, and **the log records neither the rejected title, its word count, nor which bound failed.** Four notes failed seven times and produced zero evidence of what was wrong. **⚠️ The source-title length correlation is NOT discriminating and must not be cited as support** — the FAILED titles average 8.5 words, well inside the cap.

### Planned Scope

1. **Leg A — log what was actually rejected.** When `normalizeGeneratedNoteText` rejects: the field, which bound failed (`blank` vs `wordCount`), the measured count, the limits, and **the offending value TRUNCATED** (owner decision, 2026-09-09). **⚠️ Independently shippable and ships even if Leg B slips** — without it the next occurrence is equally unresolvable. **This is the `v0.87.0` lesson one layer down:** that release added `failed_topic_reasons` because "which topic failed" without "why" had already cost two investigations.
2. **Leg B1 — publish the bound** (owner decision, 2026-09-09). Template a `{MAX_TITLE_WORDS}` line into `note-generation-developer.txt`, exactly as `{MAX_ITEM_CHARS}` and `{MAX_WORDS}` already are. Smallest change, output shape unchanged, contract made honest.
3. **Backlog Index verification pass over the 16 scope-eligible rows** — every row claiming OPEN or NOT SHIPPED, checked against real code or a read-only query, stale ones corrected.
4. **Extend the `v0.137.0` rule to cover SHIPPED-CODE claims, not just production state**, and index the two artifacts above.

### ⚠️ Why item 3 exists: three stale rows surfaced by accident in ONE day

`v0.137.0` added the rule that a claim about *production state* is a snapshot. **That rule does not reach a claim about shipped CODE, and the third failure was exactly that.**

| Claim | Reality |
|---|---|
| `V141`/`V142` never run | Ran 2026-09-08 |
| Profile-string write pending | Already applied |
| **Study Plan Builder reorder NOT SHIPPED** | **Shipped in `v0.96.0` (`185e0cc7`, 2026-08-29) — 41 releases ago** |

**⚠️ THE THIRD ONE WAS OFFERED TO THE OWNER AS A RELEASE CANDIDATE AT THE `v0.137.0` KICKOFF AND IS RECORDED IN THAT RELEASE'S NARRATIVE AS AN OPTION THEY CHOSE AGAINST.** Had it been picked, a Codex prompt would have been written to build something that already exists — the `Save order` button is at `study-plan-builder-page-client.tsx:2513`, and two of that row's three "verified traps" were also already addressed. **A stale Backlog row costs a whole release, not a paragraph**, because the Index is the input to every kickoff's scope decision.

### ⚠️ Anti-drift

- ❌ **Do NOT do B1 and B2 together.** Two bounds on one field is how this defect was created. **B1 is authoritative; B2 and B3 are not in scope.**
- ❌ **Do NOT increase `MAX_INVALID_OUTPUT_ATTEMPTS`.** The failure is deterministic — the retry already ran and failed identically. More retries buy nothing and cost an LLM call each.
- ❌ **Do NOT skip or soften title validation on the regeneration path only.** The generated title becomes the note body's **first line**; a divergent rule between first generation and regeneration is a new defect.
- ❌ **Do NOT rename or retitle the four failed notes to work around it.** `v0.120.0` established the typed title as canonical. Their titles are correct; the validator rejected the model's *output*.
- ❌ **Do NOT bundle the `OfficialChallengeQuizTemplateService` seed failures** (findings §7) — separate symptom, unproven relation, and folding it moves the verification tier.
- ❌ **Do NOT touch the `subject value='Education' … overly broad ai suggestion ignored` path** — it appeared 6× in the window and is a working guard reporting normal operation.
- ⚠️ **Nothing here may make `generateStudyPackFromExistingNoteAsync` public or route it through the `@Transactional` proxy** — both LLM calls run with no transaction and no JDBC connection held (`v0.112.0`), and that must survive.
- ❌ **NO `frontend/app/onboarding` work before the `2026-09-11` read** (62.4% baseline, cannot be re-run). **NO Learning Connections work** — `[CHECKPOINT — due 2026-09-19]`, denominator ONE.
- ⚠️ **The four failed notes are NOT repaired by this release.** They stay `FAILED` until re-run, and **that re-run is the OWNER's** — it is also the only real end-to-end confirmation, since the failing branch could not be confirmed from logs.

### ⚠️ Pre-declared guards — written so a fixture cannot pass under both the defect and the fix

- **Leg A:** assert the emitted payload **names the bound and the measured count**. ⚠️ A test asserting only that generation fails **passes under both the defect and the fix** — it already fails today.
- **Leg B1:** assert the **RENDERED** prompt contains the numeric bound. ⚠️ Not the template file — the placeholder is substituted at build time, and a test that greps the raw template passes even if substitution is broken.
- **Regression, all legs:** the four real titles from findings §1 must round-trip. ⚠️ **Use those exact strings, not invented ones** — they are the only known-failing inputs, and an invented "long title" fixture is a guess about a failure mode the logs never confirmed.
- ⚠️ **Reach the validator the way production does** — through the real response-parsing path. A fixture that hand-builds a `PromptGeneratedNote` skips `repairJsonEatenLatexCommands` and the whitespace normaliser, **either of which could be the branch that actually fired.**

### ⚠️ Size, and what the fold does to the verification tier

**The owner took both the title fix and the backlog pass in one release, after being told the repo's rule that release size is the biggest lever on verification cost. Recorded rather than smoothed over.** **The honest consequence here is mild, and only because the second half changes NO code:** items 3–4 are docs, so the code diff stays at two files with no migration and no endpoint. **Tier: one `advisor()` call on the code diff**, per the plan's §7, plus the rendered-prompt guard. **⚠️ It would NOT stay there if Leg B2 were taken** — that changes what is accepted into a note body on a shipped generation path and would need a scoped cold agent. B2 is explicitly out of scope.

**⚠️ The transport lesson does not apply: no new endpoint is added, so nothing here owes a real-request `MockMvc` test.**

### Routing

**CLAUDE CODE inline** for Legs A and B1 — two files, no migration, no endpoint, per the plan's §7. Items 3–4 are docs and are Claude Code by definition.

### Scope completeness — each planned item against the code that implements it

| # | Planned | Verdict | Evidence |
|---|---|---|---|
| 1 | **Leg A** — log the field, failing bound, measured count, limits, value truncated | **SHIPPED** | `OpenAiLlmStudyPackService:2592`/`:2597` (both branches), emitter at `:2603-2612` |
| 2 | **Leg B1** — publish `{MAX_TITLE_WORDS}` into the prompt | **SHIPPED** | `note-generation-developer.txt:31`, substituted at `OpenAiLlmStudyPackService:727`; bound unchanged at `:87` |
| 3 | Verify **the 16 scope-eligible** Backlog rows | **⚠️ CHANGED — the stated number could not be reproduced** | No filter was ever written down at kickoff. A defensible filter yields **26 raw / 23 after 3 stated over-catches**; 7 read against code, 4 stale. **Deliberately not reverse-engineered to return 16** |
| 4 | Extend the `v0.137.0` rule to SHIPPED-CODE claims, and index the two artifacts | **SHIPPED** | Six-step procedure in `CLAUDE.md`; both source artifacts carry Backlog rows (3 references each) |

**⚠️ Item 3's verdict is recorded as CHANGED rather than SHIPPED on purpose.** The release was scoped on a number, the number turned out to be unverifiable, and a pass that quietly delivered "16 rows" would have reproduced the exact failure the release exists to fix — a stated figure nobody can re-derive. **The count found is reported instead of the count asserted.**

### Shipped

- **Leg A — every generated-note text rejection now names what failed.** `normalizeGeneratedNoteText` threw a bare `LLM_INVALID_OUTPUT` on either branch; it now emits `generated_note_text_rejected field=… bound=blank|wordCount words=… min=… max=… chars=… value="…"` before throwing, and the two branches are separated so the bound is reported rather than inferred. Applies to `title`, `overview` and `keyIdea` alike. **On the blank branch the RAW value is logged, not the normalized one** — what is diagnostic there is what arrived and collapsed to nothing, which a `null` cannot show.
- **Leg B1 — the title bound is published.** `note-generation-developer.txt` now states *"keep the title at or under `{MAX_TITLE_WORDS}` words"*, templated beside the existing `{MAX_ITEM_CHARS}` and `{MAX_WORDS}`. **The bound was NOT raised** — 12 words is unchanged; the model is simply told the number it is judged against.
- **Reused the class's existing `truncateForLog` rather than adding a second truncation rule.** The first attempt defined a duplicate helper, which failed the build; the existing one already backs thirteen other truncated-value logs and caps at `MAX_LOG_VALUE_LENGTH = 80`. ⚠️ **That does not mean the rejected value always survives intact, and the note should not be read as claiming it does:** the four production titles are 48–71 characters and fit, but a title rejected *for exceeding* the word bound is by construction longer than those, and the 15-word regression fixture (~105 chars) is truncated in the log. That is accepted deliberately — **the diagnostic payload is the field name, the failing bound and the measured count, all of which are logged unconditionally and in full**; the value is context, not the evidence.
- **Corrected the `:68` comment that predicted this defect.** It said all three word bounds were unpublished and "surviving deliberately", ending with a standing instruction: *"if one starts rejecting valid content, publish the bound in the prompt rather than raising it."* **That prediction came true and the instruction was followed.** The comment now records which bound was published and why the other two deliberately were not — the instruction is evidence-gated (*"if ONE starts rejecting valid content"*), and neither `overview` nor `keyIdea` has produced a single observed rejection.
- **Corrected `docs/features/study-pack-generation.md`, which claimed the two title-rule blocks are "byte-identical today".** That stopped being true with Leg B1. The divergence is deliberate and one line: only the note path enforces a title word bound, so publishing one in `developer.txt` would state a rule nothing enforces. The doc now says so and warns against "restoring" byte-equality.
- **Five guards, each mutation-verified with the killing test named.** Deleting the published bound from the real prompt kills `noteGenerationPromptResourceDeclaresTheTitleWordPlaceholder`; breaking the substitution kills `noteGenerationPromptStatesTheTitleWordBound`; silencing the rejection log kills `rejectedGeneratedTitleLogsWhichBoundFailedAndTheMeasuredCount`. The fourth, `theFourTitlesThatFailedInProductionRoundTripThroughTheRealParsingPath`, uses the four real production titles rather than invented ones and drives the real response-parsing path.
- **A fifth guard, added by `advisor()` on the diff, closes a claim that was asserted rather than tested.** Leg A logs the field name for all three bounds, and the release justifies leaving `overview` and `keyIdea` unpublished on the grounds that *a first rejection would announce itself*. **No test exercised either of them** — the discriminating guard covered `title` only, so that justification rested on two of three fields being untested. `rejectedGeneratedOverviewIsReportedUnderItsOwnFieldName` asserts `field=overview`, `words=93`, `max=90`; mutating the overview call site to log under the title's field name kills it, and kills nothing else. ⚠️ **Its first draft used a 90-word overview — exactly the bound — and did not throw, because the check is `> maxWords`.** A boundary fixture sitting ON the limit proves nothing about either side of it; the fixture is now 93 words and the comment says why.
- **Derived the asserted word count instead of fitting it to observed output.** The title fixture's `words=15` was reached by writing 17, watching it fail, and changing the number — the shape of an assertion tuned to whatever the code emits. The count is 15 because the fixture carries no LaTeX and no irregular whitespace, so `repairJsonEatenLatexCommands` and the whitespace collapse are the identity on it. That reasoning is now a comment on the fixture, so a future change to either normalizer fails the test loudly rather than quietly shifting the count.
- Backend suite: **2350 tests, 0 failures, 0 errors, 0 skipped**, PostgreSQL container included — up exactly five from the 2345 baseline, which is the five guards above and nothing else.
- **Item 3 — the Backlog Index was checked against code for the first time, and the release's own stated scope did not survive it.** The scope said *"the 16 scope-eligible rows"*. **That number was asserted at kickoff with no filter written down and could not be reproduced** — a defensible filter (live rows whose Status asserts something about **code state**, excluding date-gated checkpoints and already-struck rows) yields **26 raw, 23 after removing 3 over-catches** — rows the filter hit on the word *OPEN* inside their own strikethrough. **The over-catches are stated rather than quietly trimmed**, for the same reason the pass refused to reverse-engineer 16. ⚠️ **The filter was NOT reverse-engineered to return 16**, because fitting a filter to an expected number is the same error `advisor()` caught in this release's own `words=15` assertion hours earlier. 26 classified, **7 read against code** (the rest were classified as out-of-scope or already-corrected without opening a file).
- **Four rows were stale, and all four asserted the absence of code that exists.** (1) *"Unconfirmed connection requests never expire — no `expires_at` and no sweep, **verified**"* — **false on all three clauses**; `LinkedLearnerRelationshipEntity:56-59` carries `expiredAt` and `expiresAt`, `LinkedLearnerRequestExpiryJob:24` is the sweep, shipped `4b701532` 2026-08-30. (2) Domain Context curator copy read **NOT SHIPPED**; landed `607e12e5` 2026-08-31. (3) `adoptGoal` NULLing a learner's exam date read **STILL OPEN**; fixed in `v0.127.0` (`f7269474`) **two days before this release opened** — `NoteCollectionService.java:864-883` promotes the date instead. (4) *"notes strand in `GENERATING`"* — **mechanism true, headline false**: `AppConfig.java:102-110` still sets no drain, but `GenerationRecoveryJob:18` has swept `GENERATING` every ten minutes since 2026-08-18, so the strand is bounded at one interval.
- **⚠️ Two of the four were free to catch — the row's own cells contradicted each other.** One had `Gate` = *"✅ SHIPPED in `v0.97.0`"* sitting beside `Status` = *"NOT SHIPPED"*; the other a **struck-through title reading "DISCHARGED … verified shipped"** beside a live `Status` of NOT SHIPPED. **No code read was needed for either.** `CLAUDE.md` already named this detector after `v0.133.0`; this is its first deliberate application, and it out-yielded every grep in the pass.
- **⚠️ The word "verified" in a row turned out to be worth nothing.** The connection-request row stamped its claim as verified and was wrong on every clause. Every row that survived this pass now carries a **`file:line`** in its Status cell instead — a row with no anchor has been *read*, not verified.
- **⚠️ A structural defect was found that defeats kickoff step 8 itself: 19 rows carried FOUR columns instead of five**, so the ritual's `Last reviewed` bump could never have reached them, and nothing announced it. Eighteen now carry an explicit **`⚠️ never stamped`** (the nineteenth was verified in this pass and carries a real date) — deliberately **not** a back-dated guess, since a stamp advancing without the claim being re-read is worse than an old date (`v0.93.0`'s own scan note said exactly this). **When a check iterates a structure, verify the structure before trusting the iteration.**
- **⚠️ One Gate was found already true and unactioned.** The public-catalog unbounded read (a real production outage fix) un-parks *"the moment `v0.119.0` is signed off"* — **19 releases ago** — and the legacy branch is still live at `NoteService.java:774`/`:872`. **This is the other half of step 8**: it checks whether a Gate became true, and a Gate that quietly came true is as invisible as an unindexed file. Left open and anchored rather than folded in — it is a backend fix with a product decision attached, and folding it would move this release's tier.
- **Seven rows marked out of scope for a code check with the reason written into the row** — production reads, curator work, an unreproduced variance — so the next pass does not re-litigate them. **And the nine this pass did not treat are named in the scan note with a reason each**, because a completeness pass that silently skips a third of its own set reads as complete to the next scan. None is an unverified code claim: each is already closed, already gated on a read this release must not pre-empt, or is this release's own subject.
- **⚠️ One of the four corrections was itself wrong on first writing, was caught by `advisor()` before commit, and the error is kept on the record.** The `GENERATING` row's replacement text said notes strand *"at most one sweep interval"* — **mistaking the recovery job's 10-minute cron cadence for the strand bound**, which is actually `noteBoundMinutes` (default **120**, `application.yaml:535`) plus a cadence, understating it roughly twelve-fold. It also passed over a branch already visible on screen: rows with a null `generation_enqueued_at` are counted, logged *"leaving them untouched"*, and **strand indefinitely** — so the original headline is exactly right for that class. ⚠️ **A correction is a claim like any other and decays the same way.** `CLAUDE.md` step 2 now carries this as its worked example, including the near-miss: when a job bounds something the bound is a **configured property** — read the value — and always check what the sweep **refuses** to touch.
- **Item 4 — the rule is the procedure this pass actually ran, not a rule written beside it.** `v0.137.0`'s snapshot rule reached `flyway_schema_history`, row counts and deploy state; it did **not** reach *"X is NOT SHIPPED"* or *"nothing does Y"*, which rot just as fast because the fix ships in a release that never re-reads the row claiming it is missing. The extension carries six numbered steps — classify by claim type before reading, **verify the title's claim and not only the Status cell's** (the `GENERATING` row diverged exactly there), anchor every survivor to `file:line`, read the row's cells against each other first, never advance `Last reviewed` on a row you did not re-read, and check the table's structure before trusting an iteration over it — plus the standing instruction to **report the count you actually found rather than the one that was asserted**.

## v0.137.0 - Deploy Integrity

**Status: Released** (kicked off 2026-09-09, signed off 2026-09-09, base branch `releases/v0.137.0`, cut from `main` after `v0.136.0` merged as #1359. **⚠️ RE-SCOPED the same day — see below.** No feature or fix PRs: every commit is release-management or docs, plus one script.)

Theme: the deploy pipeline failed silently and the record of what was deployed was wrong. Make both true again.

### ⚠️⚠️ THIS RELEASE WAS SCOPED ON A FALSE PREMISE AND RE-SCOPED WITHIN THE HOUR — THE ORIGINAL IS RECORDED, NOT OVERWRITTEN

It opened as **Deploy and Read**, justified by *"`v0.132.0` through `v0.136.0` are merged and none is deployed"*, taken from `ROADMAP.md`. **The owner chose that scope over two feature candidates on the strength of it. Read-only production queries then disproved it:**

| Claim as written | Reality, read 2026-09-09 |
|---|---|
| `V141` has never been run | **RAN 2026-09-08T12:23:16Z**, `success = true`, on `v0.132.0`'s own auto-deploy |
| `V142` has never been run | **RAN 2026-09-08T15:47:36Z**, on `v0.133.0`'s |
| The profile-string write is still pending | **ALREADY APPLIED** — the read returns `0 old / 1 new` |
| Five releases are undeployed | **The BACKEND deployed automatically every time.** Only `v0.136.0`'s FRONTEND is missing |

`notifications` and `announcements` really are at zero rows, so the notification checkpoints genuinely remain unreadable. That half survived; the rest did not.

**⚠️ THE CHEAPEST DETECTOR EXISTED AND NOBODY USED IT: the `v0.133.0` row said `V142` HAS NOT BEEN RUN in its Gate cell while its own Status cell said the read was *"READ AND CLOSED 2026-09-09, one day after the deploy, exactly on its date"* — which is only possible if it HAD deployed.** The Status was updated when the read closed; the Gate was not. **A row that contradicts itself is free to detect and nobody read the two cells against each other.**

### ⚠️ The live defect this investigation actually found

**`v0.136.0` did not auto-deploy on EITHER platform, and the resulting version skew broke Your Impact in production.**

Render's config is correct and untouched (`autoDeploy: yes`, `autoDeployTrigger: commit`, `branch: main`) and every prior release fired in **2–3 seconds** with `trigger: new_commit`. For `4ee2c752` it never fired; the owner deployed manually 5.5 minutes later (`trigger: manual`). Vercel matches exactly — GitHub records a `vercel[bot]` Production deployment for every prior release merge and **ZERO** for this one. There are no repo-level webhooks and no CI workflows, so both platforms are GitHub App integrations consuming **the same push event**, and neither received it. GitHub declared no incident that day.

**The consequence, verified against the live commit rather than assumed:** Vercel's newest Production deployment is still `7f371e65` (`v0.135.0`) while Render runs `v0.136.0`. `v0.135.0`'s `api.ts:5990` calls `/creator-impact/me` with **no** parameters, from `public-profile-page-client.tsx:225` and `settings/page.tsx:281` — and `v0.136.0` made `impacted` **required**, so both calls now 400. The owner sees *"Could not load your impact."* with a Retry that can never succeed, and the Knowledge Impact digest toggle hidden in Settings. Both degrade rather than crash, which was deliberate, but the feature is dead until Vercel ships.

**⚠️ AND THE CHANGE WAS BREAKING IN BOTH DIRECTIONS — frontend-first would have 404'd on `/creator-impact/me/summary`. `v0.136.0` recorded no deploy-ordering requirement at all.**

### Planned Scope

1. **Restore the frontend deploy — OWNER ACTION.** Deploy `4ee2c752` (or later) on Vercel. Until then the skew above is live.
2. **A detector for a merge that produced no deploy.** Nothing in the repo or the process noticed; it was found only because the owner happened to look. The cheapest form is a signoff-time check that the deployed commit matches `main` on both platforms — decide detector shape before building anything.
3. **Two standing rules, written where they will be read.** (a) A release that removes, renames or makes-required an existing API form is **breaking in both directions** and must state its deploy ordering or that both sides ship together. (b) **A `ROADMAP.md` claim about PRODUCTION STATE is a snapshot, not a fact** — re-read it read-only before repeating it, and treat a row whose Gate and Status cells disagree as a defect in itself.
4. **The reads that are now live**, which the false premise had wrongly deferred: `2026-09-10` (`v0.72.0` proximal retention, `v0.134.0` `deploy + 1 day`) and `2026-09-11` (onboarding funnel, `v0.62.0` Knowledge Impact).

### ⚠️ Anti-drift

- ❌ **NO `frontend/app/onboarding` work before the `2026-09-11` read.** It measures signup-funnel completion against a **62.4%** baseline (234/375) that cannot be re-run.
- ❌ **NO Learning Connections work** — `[CHECKPOINT — due 2026-09-19]`, denominator ONE. Stage D2's connection-request notification is exactly the nudge that converts `PENDING` into `ACCEPTED`, the metric that checkpoint kills the arc on.
- ❌ **Do NOT change any analytics event, fire site or gating condition a live checkpoint reads.** `v0.136.0` moved and re-gated `KNOWLEDGE_IMPACT_DASHBOARD_VIEWED` two days before the `2026-09-11` read that depends on it; a second such change would corrupt another read.
- ❌ **Do NOT add a second entry point to `/impact`** before its own `[CHECKPOINT — due deploy + 30 days]` reads — adding one destroys the measurement.
- ❌ **Do NOT add CI infrastructure as a reflex.** The repo has **no** `.github/workflows/`, and item 2 is a detection problem, not automatically a CI problem. State the shape before building.
- ⚠️ **A quiet read is NOT a pass.** On rows with a denominator clause (`notifications` at zero, one curator, zero digest opt-ins), `no data` means **NOT YET MEASURABLE → RE-DATE**.
- ⚠️ **⚠️ THE PRODUCTION DATABASE IS READ-ONLY FOR CLAUDE, ALWAYS** — every diagnostic in this section came from `SELECT`s. The deploy is the owner's.

### ⚠️ What is actually deployed, as of 2026-09-09

- **Backend (Render):** `4ee2c752` = `v0.136.0`, live, deployed manually 12:04:31Z.
- **Frontend (Vercel):** `7f371e65` = `v0.135.0`. **One release behind, and that is the live defect.**
- **Database:** migrations current at **`V143`**, matching the repo's highest. Nothing pending.

### Scope completeness

| Planned item | Outcome |
|---|---|
| 1. Restore the frontend deploy | **✅ SHIPPED (owner).** Vercel Production now carries `4ee2c752` at 2026-09-09T14:00:53Z, `state=success`; Render and `main` agree. Skew resolved, verified read-only rather than assumed. |
| 2. Detector for a merge that produced no deploy | **✅ SHIPPED.** `scripts/check-deploys.sh`, wired into `/signoff`. Shape was decided before building, per the anti-drift, and CI was deliberately not introduced. |
| 3. Two standing rules | **✅ SHIPPED** in `CLAUDE.md` — the production-state-is-a-snapshot rule and the API-form-is-breaking-both-ways rule. |
| 4. The `2026-09-10` and `2026-09-11` reads | **❌ NOT SHIPPED — the dates had not arrived, and reading early is the failure those dates exist to prevent.** Nothing about them is blocked by this release; they are unchanged in the Backlog Index and land in the next cycle. Recorded here so a reader does not mistake the release's own scope list for work that silently vanished. |

### Known limitations

- **The Render half of `check-deploys.sh` has not been run against the live Render API.** No `RENDER_API_KEY` was available in the session that wrote it, so the request, auth and error paths are unexercised; only the response *parsing* is covered, by fixtures. **The first real run is the test** — if it fails, the fault is in the request/auth path, not the comparison. The Vercel half ran end-to-end against the live API and is confirmed working.
- **The detector is manual.** It runs when `/signoff` says to run it, so a release that never reaches signoff is not covered, and neither is a deploy that goes missing between releases. Making it automatic requires a `schedule:`-triggered workflow — **not `on: push`, which cannot observe the absence of the event that triggers it** — and that was deliberately deferred rather than introduce CI to a repo with no workflows.

### Routing

**CLAUDE CODE** for the doc corrections, the rules and the reads. Item 2 is re-routed once its shape is decided.

### Shipped

- Added `scripts/check-deploys.sh`, a detector for a merge that produced no deploy, and wired it into `/signoff` after the release PR merges. It compares `origin/main` against Vercel's newest successful Production deployment (read through GitHub's deployments API, so it needs no new secret) and Render's newest live deploy. **It tests for ABSENCE rather than failure**, because Render's `notifyOnFail` had nothing to fire on — nothing failed, nothing was queued — and **it exits 2 rather than 0 when it cannot check**, since "I could not look" reported as "all clear" is the same failure class it exists to catch.
- **Verified the detector against the defect itself rather than asserting it:** replayed today's 12:04 state (`main` at `4ee2c752`, Vercel newest `7f371e65`) through the comparison and confirmed it reports drift and exits 1. The Render response parser is covered by fixtures for the wrapped and unwrapped API shapes, a no-live-deploy list and an empty list.
- Added two standing rules to `CLAUDE.md`: a claim about production state is a snapshot that must be re-read before it is repeated (with internal Gate/Status contradiction named as the free detector), and removing/renaming/making-required an API form is breaking in both directions and owes a deploy-ordering statement.
- Corrected four false production-state claims in `ROADMAP.md` — the `V141`/`V142` gate cells and the profile-string write — each against the read-only query that disproves it, and recorded the self-contradicting row as the detector that was available and unused.

## v0.136.0 - Contribution Surface

**Status: Released** (kicked off 2026-09-09, signed off 2026-09-09, base branch `releases/v0.136.0`, cut from `main` after `v0.135.0` merged as #1356 and tagged `7f371e65`. Shipped as PRs #1357 and #1358.)

Source: `docs/claude-plans/your-impact-private-contribution-surface-stage1.md`.

Theme: make Your Impact a real destination — and stop it costing every Settings page load a 1,587-record read.

### ⚠️⚠️ THE HEADLINE IS A LIVE DEFECT, NOT THE IA MOVE

`frontend/app/settings/page.tsx:276-296` calls `getCreatorImpact()` in a page-load `Promise.all` and uses the result for **exactly one thing**:

```ts
setHasPublicNotes(Boolean(impact.value?.notes.length));
```

**To evaluate a boolean it pulls the entire creator-impact payload — 1,587 note records on the owner's account** — via three `IN (:noteIds)` grouped queries over a 1,587-element list plus an aggregate, **on the 256 MB Postgres behind the 20-connection pool that has already failed twice from unbounded reads** (2026-09-04, 2026-09-05, whose recorded lesson was *bound the work instead*). Payload precedent too: `docs/claude-findings/2026-09-01-prod-frontend-build-failure-public-notes-2mb.md`.

**⚠️ This is live on the owner's account today, and it is independent of whether the page ever moves.**

### ❌ THE BRIEF'S PERFORMANCE PREMISE IS FALSE — DO NOT REPEAT IT

**Impact was never part of the Public Profile payload.** It is a separate `GET /creator-impact/me`, fetched client-side in an effect gated on `isOwner`, and the controller takes **no `userId` parameter at all**. A public visitor triggers no impact query. **So moving the page saves Public Profile NOTHING** — the IA move is a discoverability fix (7 lifetime dashboard views), not a performance one. **Do not write a test asserting a Public Profile improvement that cannot occur.**

### Planned Scope — all 7 items, by owner decision

1. **Bound the impact query** — server-side sort by impact, pagination or a cap, and a filter for the zero-impact set.
2. **Lightweight summary endpoint** (`{distinctLearnersHelped, publicNoteCount}`) and **switch Settings to it**.
3. **Dedicated `/impact` route**, reusing `CreatorImpactService` — **no duplicated analytics logic**.
4. **Public Profile:** remove the full dashboard, add an owner-only `View impact →`.
5. **Per-note hierarchy:** impacted first, zero-impact collapsed, deterministic tie-break.
6. **Zero states** per the audit's F.2.
7. **Preserve mobile;** the collapse control must be keyboard-reachable and not hover-dependent.

### ⚠️ Anti-drift

- ❌ **NEVER add a `userId` parameter to the impact endpoint. Its ABSENCE is the privacy control** — `/impact?userId=…` must stay *unrepresentable*, not merely blocked.
- ❌ No public impact metrics: no rankings, leaderboards, top-contributor badges, follower counts, XP, points, levels, trophies or streaks.
- ❌ Do not merge Impact into Progress or into Learning Connections. **Progress = how I am learning. Your Impact = how my shared knowledge is helping other learners.**
- ❌ Do not add Impact to the permanent sidebar — a first-class page does not require first-class navigation, and the denominator is one.
- ❌ Do not expose learner identities, scores, weak concepts, or who copied/studied.
- ❌ Do not duplicate the analytics logic — move and reuse `CreatorImpactService`.
- ❌ Do not load full impact analytics from the app shell, notification polling, or Dashboard.
- ❌ **`/impact?note=<id>` is DEFERRED, not optional** — no shipped consumer needs it, and building it now creates an unused deep-link contract.
- ⚠️ **The tie-breaker is `noteId ASC` ALONE — NOT `copyCount DESC`.** `copyCount` is not in the ranking query, and it would promote a distribution metric into semantic ranking. `noteId` is immutable and unique: the only tie-break that guarantees stable pagination.
- ⚠️ **Label views as "page views".** `PUBLIC_NOTE_VIEWED` has 25,262 rows and **ZERO** with a `user_id` — all anonymous traffic — so views can never be deduplicated per person. Not taste; the metric cannot support it.
- ⚠️ **Do NOT put a prominent "Awaiting first study · 1,526" counter on the page.** It turns a contribution surface into a deficit scoreboard and would be the largest number on it. Show the impacted count; do not promote the zero-impact one.
- ✅ **Preserve:** the `PRIVATE TO YOU` labelling; the headline-dedup explanatory copy (**it is accurate — verified against the queries**); the self-copy exclusion; the `impactRequestIdRef` out-of-order guard; and `public-profile-page-client.test.tsx:277` (a visitor issues no impact request).
- ❌ No Learning Connections work — `[CHECKPOINT — due 2026-09-19]`, denominator ONE. No `frontend/app/onboarding` work — `[CHECKPOINT — due 2026-09-11]` is two days out.

### ⚠️ Denominator, and why it does NOT justify deferral here

**Exactly one account in production has any impact, and it is the owner's own ADMIN account** (1,587 public notes, 56 learners helped); three other accounts have 1–2 public notes and zero learners. **The denominator-based deferral used for note likes and Learning Connections does NOT transfer:** the single user is the owner, who is asking for the feature, and 56 learners helped is *measured* impact rather than a speculative event stream.

### ⚠️ Size and verification tier — the owner took both halves against the audit's advice

**The audit recommended splitting this into two releases** (items 1–2, then 3–7) precisely so R2 never consumes an unbounded endpoint, and because **7 items is well past the 3–4 band where verification stays at a single `advisor()` call.** The owner elected to take both halves together. **That is recorded rather than smoothed over, and the consequence is stated up front: verification is AT LEAST one scoped cold agent, and the tier must be re-decided if the diff grows** — this touches an owner-only privacy surface, and four of the last four releases each shipped a test that passed for a reason unrelated to its change.

**⚠️ Ordering still binds inside the release: bound the query (item 1) BEFORE the new page consumes it (item 3), so the new surface is never briefly worse than the old one.**

### Cross-reference to settle when this ships

`/impact` becomes the canonical `IMPACT_MILESTONE` destination. `attention-notifications-email-expansion-stage1.md` §15 currently points at `/progress` and must be updated — the tightening addendum already forbids contracting it to `/progress`.

### Routing

**CODEX** — a bounded/paginated endpoint, a new summary endpoint, a new route and a Public Profile change span backend service, controller, DTO and several frontend surfaces.

### Shipped

- Bounded creator-impact reads with required impacted/zero-impact pagination, stable database ordering, server-capped page sizes, page-sized views/copies aggregates, and section totals. Removed the parameterless full-payload response and its DTO.
- Added `GET /creator-impact/me/summary` with exactly `distinctLearnersHelped` and `publicNoteCount`. Settings now uses this fixed-size response and preserves its retryable fallback when the check fails.
- Added the authenticated top-level `/impact` destination. Its headline comes only from the summary endpoint; impacted notes load first, and zero-impact notes stay collapsed and unfetched until expanded.
- Replaced the owner Public Profile dashboard with a compact owner-only `View impact →` card. Visitors remain unchanged and issue no impact request.
- Added the two contribution zero states, retry handling for the page and expanded section, page-view labelling, mobile card layout, and an accessible keyboard-operable disclosure.
- Added regression coverage for bounded metric ID lists, the required `impacted` parameter, absent user identifiers, summary response shape, deterministic impact ordering, headline deduplication, structural self-copy exclusion, Settings endpoint selection, Public Profile privacy, and lazy zero-impact loading.
- **Repointed the Knowledge Impact digest email at `/impact`.** Its `View Your Impact` button linked to `/public/creator/{username}#your-impact-heading`, and that anchor now resolves to the owner-only link card rather than a dashboard — so the button promised the dashboard and would have delivered another link. Found by a surface sweep, not by the diff: `RetentionService` was not otherwise touched by this release, and the anchor id still exists, so it would have degraded silently in an email that cannot be corrected once sent.
- Gave the impacted-notes `Load more` a visible failure state and a retry. It had a `finally` with no `catch`, so a failed second page produced an unhandled rejection and no user-visible change; the owner's 61 impacted notes at a page size of 20 make that the first-visit path, not an edge case.
- Clamped paging inputs in `CreatorImpactService` (`Math.clamp`, repo convention per Sonar S6877) so a degenerate `size` or negative `page` cannot reach `PageRequest.of` and 500. The controller's `@Min` annotations are inert under `standaloneSetup`, so the guard now lives where it is actually exercised.
- Restored `@Transactional(readOnly = true)` on `CreatorImpactService`, removed during delivery. Behaviourally near-neutral — OSIV plus `DELAYED_ACQUISITION_AND_HOLD` (pinned by `ConnectionHandlingModeContractTest`) holds one connection per request either way — but it is a defensive annotation on the release whose subject is pool pressure.
- Stopped discarding the fresh section totals that every impact page response already carries. `totalImpacted` / `totalZeroImpact` were read once at initial load, so a note crossing from zero-impact to impacted while the page was open left `Load more` comparing against a stale total — the button kept requesting pages that came back empty and never resolved. Both loaders now refresh the pair from the response in hand, at no extra query cost. Found by the pre-signoff cold agent.
- Finished the `IMPACT_MILESTONE` cross-reference in the notification audit. Delivery updated Appendix A's milestone row; §15's deep-link matrix, §7.1's verified-routes table, and Appendix A's note-copy row still pointed at `/progress`.

## v0.135.0 - Update Signal

**Status: Released** (kicked off 2026-09-09, signed off 2026-09-09, base branch `releases/v0.135.0`, cut from `main` after `v0.134.0` merged as #1353 and tagged `eac429a4`. Shipped as PRs #1354 and #1355.)

Source: `docs/claude-plans/attention-notifications-email-expansion-stage1.md` (Stage D) **as corrected by `docs/claude-plans/attention-notifications-stage1-tightening-addendum.md` §2, which is the binding design.**

Theme: give the notification substrate its first real producer — when a curator publishes an Official Review Set update, the learners who adopted it are told.

### ⚠️⚠️ READ THE ADDENDUM'S §2, NOT THE AUDIT'S §17.2 — THE AUDIT'S DEDUP KEY IS A PERMANENT SUPPRESSION BUG

The audit proposes `REVIEW_SET_UPDATE:<adoptedCollectionId>`. Under the permanent unique index on `(recipient_user_id, dedup_key)` **that key can deliver exactly ONE notification per learner per set, ever.** `NotificationService.deliver` catches the constraint violation and **returns the OLD row** — no exception, no log line, publish reports success. `dismissed_at` is not in the index, so dismissing never frees the key, and retention only frees it after 90 days *and* a read/dismiss — so **a learner who never opens the bell is suppressed forever.**

**The corrected key is source-side:**

```
REVIEW_SET_UPDATE:<sourceCollectionId>:<lastUpdatePublishedAt as epochMilli>
```

`lastUpdatePublishedAt` already advances **only** when `unpublishedChanges` is true (`NoteCollectionService:754-760`), so **re-press idempotency is inherited for free** and no migration is needed.

### Planned Scope

1. **`NotificationType.REVIEW_SET_UPDATE` + `NotificationCategory.LEARNING_SYSTEM` (backend).** The first type with a real producer.
2. **The producer (backend).** Fires from the `publishReviewSetUpdate` **call site**, delivering to adopters of that source root, deep-linked to `/collections/{adoptedCollectionId}`.
3. **Episode suppression (backend).** A batch filter in the producer, between audience resolution and fan-out, dropping recipients who already hold an **undismissed** `REVIEW_SET_UPDATE` row for that source. **Settled by owner decision A2 — ships WITH the producer, not later.**
4. **Retire the transitional `ACTION_REQUIRED`.** Delete both the type and its producerless category;
   the settled taxonomy decision is recorded below.

### ⚠️ Anti-drift

- ❌ **NEVER fire on raw source drift.** Only `publishReviewSetUpdate`. The publication boundary exists to forbid exactly this.
- ⚠️ **THE TRIGGER IS THE CALL SITE, NOT THE STAMP.** `markReviewSetUpdatePublished` has **two** call sites — `publishReviewSetUpdate:758` and `publishInitialCurriculum:1849`. Wiring the producer to "the stamp advanced" would also fire on a set's first-ever publication.
- ❌ **NO COUNT IN THE COPY.** Under episode suppression the single open row is the learner's only signal across N publishes, so *"3 new notes"* is false by the second one. Generic copy only — this also preserves R10, the inbox's zero-N+1-by-construction property.
- ❌ **Do NOT call `deliver()` inside a transaction (R9).** It is deliberately non-transactional so a dedup-index violation cannot mark a whole fan-out rollback-only. `FanOutTransactionBoundaryTest` guards this — **do not delete it, and do not delete the open-in-view runtime test either; they cover different angles.**
- ❌ **Do NOT put suppression in `AnnouncementAudienceResolver`** — that class is editorial, never authorization, and a test guards it.
- ❌ **NO email** (decision D3; R1 unresolved — the 100/day cap is breached at 156–159 observed).
- ❌ No new endpoint unless the producer genuinely needs one; no admin surface; no preferences centre.
- ❌ **NO Learning Connections work** — `[CHECKPOINT — due 2026-09-19]` is ten days out and its kill criterion keys on `ACCEPTED`, denominator ONE.
- ✅ **Reuse `notificationFanOutExecutor`** (core 1 / max 2) — do not add a second executor or resize it (R5/R13).
- ✅ Dismiss must change no curriculum truth; applying an update stays authoritative in Review Set state.

### ⚠️ Corrections to carry — the audit's own numbers are stale

- **The audit says "≤30 recipients". `LET Comprehensive Review` now has 42 adopters** (ALE 30, PNLE 15, CPALE 8, Civil Engineering 1). D2's justification cited the old figure.
- **Exactly ONE update publish exists in the product's history** — 2026-09-09 at 01:50:47, on LET, forty seconds after the Education tagging run. Four of five public source roots have never published an update at all.
- **⚠️ That read WEAKENED the case for shipping suppression now, and the owner took the recommendation anyway.** Record it as a judgement call made with the counter-evidence in view, **not** as "suppression was obviously necessary."

### Settled owner decisions (do not re-litigate)

- **A1 — dismiss without applying → the next publish DOES re-notify.** Suppress-until-applied would reintroduce permanent silence through a different trigger.
- **A2 — episode suppression ships WITH the first producer.**

### Settled taxonomy decision

**This release deletes both transitional `ACTION_REQUIRED` values.** `REVIEW_SET_UPDATE` maps to
`LEARNING_SYSTEM`, so every shipped type and category has a real producer. A later connection-request
release can add its category together with its producer instead of preserving a producerless slot.

### ⚠️ Inherited checkpoint — `v0.134.0` is DEPLOYED and its clock has started

`V143` ran **2026-09-09 03:53:07**, so `[CHECKPOINT — due deploy + 1 day]` is **2026-09-10**. **Proximal (a) is already ANSWERED: `idx_notifications_inbox` is live in production with the exact expected definition.** Proximal (b) — `/actuator/metrics/announcement.fanout.duration` resolves — is still owed. **⚠️ Its distal tier is gated on a first announcement publish, and ZERO announcements have ever been published**, so a quiet read is *not yet measurable* → re-date, never a pass.

### Verification tier

**To be decided when the shape is known**, but the prior is **one scoped cold agent**: this adds a producer to a fan-out path that has never run in production, and the last two releases each shipped a delivered test that passed for a reason unrelated to its change. **Routing: CODEX** — new enum values, a producer, a batch suppression query and its tests span backend service, repository and entity layers.

### ⚠️ Audit note — Codex hit its session limit mid-delivery, and the completion gap was ONE test

**The delivery was further along than the interruption implied: it compiled, the full backend suite passed, and 7 of the prompt's 12 required tests were present — including the discriminating one.** Auditing against the prompt's list rather than against the delivery's own shape found the real state:

| Required test | Status |
|---|---|
| Dismiss → later publish → NEW row (**the discriminating test**) | ✅ delivered |
| Re-press idempotency; episode suppression | ✅ delivered |
| R9 through the real Spring-managed path | ✅ delivered, **plus the annotation guard extended to the new service and listener** |
| Read ≠ resolved; no-count copy; learner's-own deep link | ✅ delivered |
| Real `MockMvc` request | ✅ **already existed** from `v0.132.0` |
| Taxonomy + badge counter | ✅ delivered |
| First-ever publication notifies nobody (**two-writers trap**) | ✅ **already covered** — see the correction below |
| **Editing without publishing reaches nobody** | ❌ **MISSING — added by this audit** |

**⚠️ A CORRECTION WORTH RECORDING, BECAUSE THE FIRST READ WAS WRONG.** This audit initially judged the two-writers trap uncovered and wrote a duplicate test for it. It **is** covered: `NoteCollectionServiceTest.updateVisibility_publishesWhenEveryItemIsPublic` asserts `markReviewSetUpdatePublished` fires **and** `verifyNoInteractions(applicationEventPublisher)` — the stamp advances, no event does. **The grep missed it because the test is named for the visibility change, not for the private method it exercises.** The duplicate was removed. *Searching for a behaviour by the name of the method that implements it will keep missing tests named for the user action.*

**The one genuine gap — `curatorEditsWithoutPublishingReachNoAdopter` — is the anti-drift line this release leans on hardest** (*editing is not publishing*), and it covers a path nothing else did: items added with **no publish call at all**. The existing no-op test covers publishing with nothing to publish, which is a different thing.

**Two mutants run, both killed:**

| Mutant | Killed by |
|---|---|
| **Dedup key reverted to the audit's `REVIEW_SET_UPDATE:<adoptedCollectionId>`** — the permanent suppression bug | **`dismissingOneRevisionAllowsTheNextPublishedRevisionToCreateANewRow`** — the design's whole reason for existing, caught |
| `@Transactional` added to the new producer's `fanOut` (R9 on the new path) | `FanOutTransactionBoundaryTest.reviewSetUpdateFanOutIsNotTransactional` |

**Verification run:** backend 2,335 tests + the 102-query PostgreSQL harness against a real container; frontend 2,348 across 212 suites; `tsc --noEmit` clean; `npm run lint` 0 errors.

### ✅ Pre-signoff cold agent — ONE CLAIM REFUTED, TWO INERT TESTS FOUND, ALL FIXED

The scoped cold agent ran against the merged state and **refuted C6** while upholding the other seven.

**⚠️ C6 REFUTED — a curator is notified of their own publish.** `adopt()` carries **no owner guard**, so a curator can self-adopt their own PUBLIC Review Set; `findReviewSetUpdateRecipients` had no self-copy exclusion, so publishing an update then told them about their own action. **The fix was already sitting eight lines above in the same file:** `countAdoptionsByCollectionIds` excludes self-copies with `adoption.ownerUserId <> source.ownerUserId` for the adoption COUNT. The two queries now agree on what an adoption is, and `aCuratorWhoAdoptedTheirOwnSetIsNotNotifiedOfTheirOwnPublish` fails if they diverge again. Cosmetic in severity, real as a gap.

**⚠️⚠️ TWO TESTS PASSED FOR REASONS UNRELATED TO WHAT THEY CLAIMED — AND ONE OF THEM WAS WRITTEN BY THE AUDIT THAT WENT LOOKING FOR EXACTLY THIS SHAPE.** That is the finding worth keeping:

1. **`curatorEditsWithoutPublishingReachNoAdopter`** — added by this release's own audit to guard *"editing is not publishing"*. Its act phase inserted rows with `jdbcTemplate` and **called no service method**, so a producer wired to fire on a WRITE rather than on the publish call would have sailed straight through it. **The test that existed to catch the release's boldest anti-drift claim could not catch it.** Renamed to `unpublishedRowsAloneDispatchNoFanOut` — which is what it actually proves — and the real guard now lives on the real edit path as `NoteCollectionServiceTest.addItems_doesNotAnnounceAnUnpublishedEdit`, which drives `addItems` and asserts the publisher is untouched.
2. **The `taskTransactionActive` assertion** captured `isActualTransactionActive()` in a wrapper that runs **on the test thread after the publishing transaction already committed and unbound** — so it read `false` whether or not `fanOut` was transactional, and could not see the regression its own `.as(...)` named. The capture moved **inside `deliver`**, via a spy on the **Spring-managed** bean so the proxy is live. **Verified by mutation: `@Transactional` on `fanOut` now fails it, and previously did not.**

**Four mutants run across this release, all killed:**

| Mutant | Killed by |
|---|---|
| Dedup key reverted to the audit's `<adoptedCollectionId>` | `dismissingOneRevisionAllowsTheNextPublishedRevisionToCreateANewRow` |
| `@Transactional` on the producer's `fanOut` | `FanOutTransactionBoundaryTest` — and **now also** the corrected runtime assertion |
| **Self-copy exclusion removed** | **`aCuratorWhoAdoptedTheirOwnSetIsNotNotifiedOfTheirOwnPublish`** |

**Upheld and worth recording:** the `LIKE` prefix cannot collide across sources (UUIDs are fixed-length and contain no wildcards); a same-millisecond key collision additionally requires a dismiss and a lock-serialized round trip inside 1 ms; adopted CHILD collections point at their own child source, not the root, so a Goal adoption cannot multi-notify. **`ACTION_REQUIRED` has zero references repo-wide, and production still holds zero notification rows of any type — re-verified read-only at this audit, not inherited.**

**Known limitation, recorded not fixed:** no test exercises delivery on the real `ThreadPoolTaskExecutor` thread, and the mid-fan-out unique-index conflict is covered only with a mocked `NotificationService`, never against a real database for two recipients in one fan-out.

**⚠️ Method note: the branch was NOT switched under the agent this time** — the `v0.134.0` lesson held.


### Shipped

- Replaced the producerless `ACTION_REQUIRED` type/category pair with the actionable
  `REVIEW_SET_UPDATE` / `LEARNING_SYSTEM` pair, backed by the first real feature producer.
- Publishing real changes to an Official Review Set now emits a plain-value event and queues adopter
  delivery only after the locked publication transaction commits, reusing the unchanged bounded
  `notificationFanOutExecutor`.
- Added source-revision identity as
  `REVIEW_SET_UPDATE:<sourceCollectionId>:<persistedPublishedAtEpochMilli>`, fixed count-free copy and
  deep links to each learner's adopted collection.
- Added one-query adopter resolution and one-query undismissed-episode suppression. Dismissal closes an
  episode, allowing the next genuinely published revision to create a new notification row.
- Added real Spring-transaction, revision-key, suppression, dismissal, no-op retry, queue-rejection,
  copy, deep-link and badge coverage while preserving both existing R9 guards.
