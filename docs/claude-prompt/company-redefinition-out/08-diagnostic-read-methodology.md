# Diagnostic Read — Methodology

> Companion to `08-diagnostic-read-queries.sql`. Scoped by Claude Code on 2026-07-24, following
> the resequencing in `07-reprioritization.md`. This is the "Diagnostic Read" item that now sits
> immediately after Phase 1 (practice-first onboarding, shipped) and ahead of everything else in
> `docs/product/ROADMAP.md`'s "Company Redefinition Roadmap — Phase Detail" section. Analysis only
> — no code, schema, or endpoint changes. The SQL runs against production (this session has no
> production DB access); the owner runs it and brings results back for interpretation.

## Why this exists

Three prior retention fixes (v0.44.0, v0.46.0, v0.48.0) each shipped on a different hypothesis
without moving W1→W2 retention. That pattern is a diagnosis gap, not proof the next feature will
be the one that works. A real-time signup surge (~15 signups in one evening, Facebook/LET-driven)
is the best research asset available to actually find out where the funnel breaks before
committing to another build cycle — see `07-reprioritization.md` for the full reasoning behind why
this comes before both Phase 2 and the new Reusable Practice Assets initiative.

## The methodological fix this scoping pass found

The existing, shipped W1→W2 retention read (the numbers behind the 2.4%/127 and exam-dated 0%/41
figures already on the Admin dashboard) anchors "activated" on a user's first `STUDY_PACK_GENERATED`
analytics event. That was a correct proxy for "got real value" back when every onboarding path ended
in note-authoring plus LLM generation.

**It silently breaks for the exact population this release targets.** v0.57.0's practice-first
branch lets a `BOARD_EXAM` learner adopt an Official Review Set instead of generating anything —
`NoteService.copySourceStudyPack()` copies an already-generated Study Pack with **no LLM call**, so
`STUDY_PACK_GENERATED` never fires for that user. Under the old cohort definition, every
practice-first-onboarded learner is invisible to the read — not counted as ineligible, never
entering the cohort at all, regardless of how well or poorly they actually retain. Since the surge
is exactly the population the practice-first branch was built for, running the old query unmodified
against the surge cohort would silently exclude the most relevant users and produce a misleading
read.

**The fix, built into `08-diagnostic-read-queries.sql`:** report two anchors side by side rather
than picking one.
- **Signup-anchored (primary, new)** — anchor on `users.created_at`. Onboarding-path-agnostic:
  catches practice-first adopters and create-first learners equally. This is also more literal to
  "read the surge cohort," since the surge is defined by signups, not by a downstream generation
  event.
- **Activation-anchored, widened (historical comparability)** — same shape as the existing
  dashboard read, but "activated" is now `first(STUDY_PACK_GENERATED OR ONBOARDING_V2_COMPLETED)`.
  `ONBOARDING_V2_COMPLETED` fires for both onboarding paths' completion (confirmed at
  `frontend/app/onboarding/page.tsx:622,775`), so the union no longer excludes practice-first
  adopters while staying comparable to the historical baseline. The query reports the
  old-definition and widened-definition counts side by side so the widening's effect is itself
  visible, and includes a built-in sanity check: the old-definition row should roughly reproduce
  the existing 2.4%/127 figure over a comparable historical window — if it doesn't, that's a bug to
  chase before trusting anything else in this pass.

A second, smaller instrumentation note: the v0.57.0 funnel events
(`ONBOARDING_V2_PRACTICE_FIRST_ELIGIBLE`, `ONBOARDING_V2_PRACTICE_FIRST_PLAN_ADOPTED`,
`ONBOARDING_V2_COMPLETED`) are confirmed wired end-to-end — `trackOnboardingEvent`
(`frontend/app/onboarding/page.tsx:326-334`) calls `trackAnalyticsEvent`
(`frontend/lib/api.ts:2527`) → `POST /analytics/events` → the backend's async listener →
`analytics_events`. Client-fired analytics is still fire-and-forget/best-effort, so it's worth one
cheap sanity check (`SELECT event_type, COUNT(*) ... GROUP BY event_type` over the surge window)
before leaning on volume, but this is a confirmation step, not an open risk.

## The three hypotheses, and what result pattern supports each

Test these explicitly — do not adopt one without the data supporting it, per
`07-reprioritization.md`'s own caution against repeating the three-blind-fixes pattern.

1. **Discovery problem** — the value exists, but exam-dated/surge users bounce before reaching it.
   Supported by: low returned-in-week-2 concentrated among users who show little or no early
   activity at all (Query 2/3 low regardless of definition; low `returned_any_event` too, not just
   `returned_meaningful_study`).
2. **Value problem** — they reach the real experience and it isn't worth a second visit. Supported
   by: healthy early activity (`returned_any_event` reasonable, maybe even `returned_meaningful_study`
   showing real study behavior in week 1) but retention still collapsing by week 2.
3. **Lifecycle-metric mismatch** — board-exam prep is episodic (cram → sit the exam → legitimately
   done), so weekly retention may be structurally low regardless of feature quality. **Test this,
   don't assume it** — Query 4 (exam-proximity buckets) is the direct test: if retention is flat
   across proximity to the exam date, that argues *against* a pure lifecycle story, since a learner
   who is, say, 60+ days from their exam has no lifecycle reason to have "finished" yet. This is
   also in tension with the existing 0/41 finding (exam-dated users retaining at 0%, *below* their
   own exam date) — that fact already leans toward (1) or (2), so treat (3) as a hypothesis under
   real test here, not a conclusion to import.

Query 5 (LET/Education concentration) doesn't test a retention hypothesis directly — it's a channel
check tied to the reprioritization doc's "under-investing in LET/Education content depth" finding.
If the surge is concentrated on LET and that sub-cohort retains worse than others, thin official
content on the exact channel bringing in new signups is a plausible compounding factor worth its
own follow-up, separate from the three hypotheses above.

## Interviews

Reuse `docs/claude-prompt/retention-diagnosis-out/05-interview-script.md` rather than writing a new
script from scratch. One adaptation: that script targeted a "retained + churned exam-dated"
population defined over a longer window; the surge cohort is very fresh (days old, not weeks), so
shift a couple of questions from "why did you stop coming back" framing toward "what did you expect
walking in vs. what you actually found in your first session" — the surge cohort can speak to
first-session friction in a way a long-churned user can't recall as clearly. Keep the rest of the
script's structure and question count as-is.

## How to interpret the SQL output once run

- Start with Query 1 — it defines what window Queries 2–6 should actually use (`RAMP_START`), and
  tells you whether the elevated volume is a one-evening blip or a real step-change in baseline.
- **Don't misread an empty or small eligible count from the very newest signups as a bad result** —
  the 14-day eligibility window means the freshest days of the surge literally cannot appear in a
  completed W1→W2 read yet. Re-run once that window closes to add them in; this mirrors the same
  trap the existing `next-priority-new-user-focus-out/02-h1-h5-cohort-recheck-and-cpale-depth.sql`
  flagged for the v0.48.0 cohort.
- Compare Query 3's `old_definition` row against the historical 2.4%/127 baseline as a correctness
  check before trusting Query 2's new signup-anchored number.
- Query 6's cost figure is explicitly directional (assumed multipliers, clearly labeled) — read it
  as "is this roughly a cent or roughly a dollar per active user," not as a billing-grade number.

## Results — Round 1 (run 2026-07-24/25)

**Headline: the actual deliverable — W1→W2 retention on the real surge cohort — is still
unavailable, and nothing below substitutes for it.** The 2026-07-23 surge (29 signups that day)
doesn't close its 14-day eligibility window until roughly **2026-08-06**. Everything reported here
either reads the pre-surge baseline or a same-day funnel snapshot — **re-read the retention queries
after 2026-08-06** before drawing any conclusion about the surge cohort specifically.

**What Round 1 actually found, held to what the sample size supports:**

- Query 1 showed signup volume has been noisy-but-substantial since late June (bursts of 8-12/day),
  with one clear spike on 07-23 (29) — not a clean step-change, more a volatile baseline with one
  standout day.
- Query 2 (signup-anchored, the 07-01–07-09 pre-surge window, n=39 eligible): 0/39 returned by any
  signal. **This is not a new low** — at the historical ~2.4% rate, the expected returner count at
  n=39 is under 1, so zero is what noise looks like at this size, not evidence of decline.
- Query 3 (activation-anchored, all-time): old definition 150 eligible / 3 returned = 2.0%; widened
  definition 151/3 = 1.99%. Matches the historical 2.4%/127 baseline within noise — **still flat,
  no measurable change either direction.** The widened definition barely moved the count because
  practice-first completions this recent aren't 14-day-eligible yet.
- Query 4/5 (exam-proximity and LET/education cuts): all read ~0% across every bucket, because the
  whole n=39 cohort is near-zero — **this cannot discriminate the three hypotheses**, since there's
  no variance across cuts to compare. Not informative either way.
- **The one real, well-supported finding: a chronic ~50% onboarding non-completion rate across
  recent signups generally** — Query 7 found 59/117 (50.4%) non-surge-day signups (last 30 days)
  have `onboarding_completed_at` set, versus 19/29 (65.5%) on the 07-23 surge day itself. **This is
  not a surge-quality problem** — the surge day completed onboarding somewhat *better* than the
  trailing baseline, not worse, so this finding is about the standing signup base generally, not
  something newly caused by this traffic. Caveat: the 30-day baseline bucket includes very recent
  signups who may still be mid-onboarding, so 50% likely overstates true permanent drop-off
  somewhat — still worth its own follow-up look, tracked separately from the surge/retention
  question.
- Query 8 (onboarding step-funnel, event-dated 07-23+): raw counts logged (`ONBOARDING_V2_STARTED`
  30, `PROFILE_SELECTED` 27, `COMPLETED` 20, `PRACTICE_FIRST_ELIGIBLE`/`PLAN_ADOPTED` 9/9,
  `STUDY_PACK_GENERATED` 11, `TOPIC_SUBMITTED` 13, among others). **Two over-reads were caught and
  retracted rather than reported as findings:** (1) "practice-first converts 9/9 = 100%" — n=9 is
  too small to call this validated, and `ELIGIBLE`/`PLAN_ADOPTED` sit close together in the same
  code path, so a clean match could partly be mechanical rather than proof of zero drop-off;
  encouraging, not confirmed. (2) A theory that the standard path's `TOPIC_SUBMITTED`(13) →
  `STUDY_PACK_GENERATED`(11) gap reflects UX friction rather than backend errors, argued from the
  absence of `STUDY_PACK_ERROR` rows — that gap is two people, and "no error event logged" is weak
  evidence given these are best-effort client-fired events; not enough to support a causal story.
  **Log these step counts for re-reading once volume grows; don't interpret the shape yet.**
- Query 6 (cost proxy) has a known bug: the `model_pricing` join used bare model names, and
  production's `study_packs.model_used` almost certainly carries a date suffix, so it matched zero
  rows and returned a blank cost — not a "$0 cost" finding. Fixed in the SQL file with a `SELECT
  DISTINCT model_used` diagnostic step; re-run once the real model id strings are substituted in.

**Net for this round:** no hypothesis verdict, no build decision — correctly, given the sample sizes
and the 14-day timing constraint. The one durable, actionable takeaway is the chronic ~50%
onboarding completion rate, which is real (n=117) but a separate, pre-existing issue from the surge
or the retention question, and doesn't move any of Phase 2 / Reusable Practice Assets' status. Next
concrete step: re-run the retention queries (2-5) after **2026-08-06** once the surge cohort's own
14-day window has actually closed.

## Results — Onboarding funnel re-check (2026-07-28, interim — not the Round 2 retention re-read)

Prompted by the 2026-07-28 strategic reprioritization discussion (`docs/product/ROADMAP.md`, "Prioritization Lens & Strategic Frame") surfacing the chronic onboarding non-completion rate as a possible confound for the target-habit/retention work. This re-runs Query 7 and Query 8 only — **not** a Round 2 retention verdict, which still needs the surge cohort's 14-day window to close (~2026-08-06).

**Query 7 (30-day rolling completion rate):** non-surge 76/130 = 58.46%, surge day 19/29 = 65.52% (unchanged from Round 1, as expected — 07-23 is a closed historical day). Non-surge completion rose from Round 1's 50.4% (59/117) to 58.46% over ~4 days. No onboarding-flow change shipped in that window (v0.60.1 through v0.61.0's kickoff were all Challenge Quiz work) — no known causal driver. Could be genuine improvement in a still-small sample, or an artifact of the rolling 30-day window swapping in different days. **Log and watch, don't call a trend yet** — same bar Round 1 held itself to.

**Query 8 (cumulative step-funnel since 07-23):** STARTED 68, STEP_VIEWED 68, PROFILE_SELECTED 65, EXAM_DATE_SET 46, INPUT_METHOD_SELECTED 35, PRACTICE_FIRST_ELIGIBLE/PLAN_ADOPTED 31/31, TOPIC_SUBMITTED 20, STUDY_PACK_GENERATED 21, OWN_NOTE_SUBMITTED 5, BACK_NAVIGATED 17, CTA_CONTINUE_STUDYING 12, CTA_GO_TO_DASHBOARD 3, COMPLETED 52, ABANDONED 69.
- Completion ratio COMPLETED/STARTED: 66.7% (Round 1, n=30) → 76.5% (now, n=68) — a second, independent signal pointing the same direction as Query 7. Still not enough to call a trend on its own; two agreeing signals is more than one, not yet a confirmed lift.
- `PRACTICE_FIRST_ELIGIBLE`/`PLAN_ADOPTED` are still exactly equal (31/31, was 9/9) — Round 1's caution stands: these sit close together in the same code path, so an exact match may be partly mechanical rather than proof of zero drop-off there. Not re-litigating as a new finding.
- **New data-quality flag, not in Round 1's summary:** `ABANDONED` (69) now exceeds `STARTED` (68) inside the same query window — only possible if `ABANDONED` fires for sessions whose `STARTED` predates the `created_at >= '2026-07-23'` cutoff (e.g. a timeout-based abandonment job marking stale pre-surge sessions abandoned inside this window). A window-boundary artifact, not evidence more people abandoned than started. Needs its own instrumentation check before `ABANDONED` is used for anything — don't build on this number as-is.
- **Real signal, held to sample size:** the steepest true drop is `PROFILE_SELECTED`(65) → `EXAM_DATE_SET`(46), a 29% loss, then `EXAM_DATE_SET`(46) → `INPUT_METHOD_SELECTED`(35), another 24% loss — both before the flow branches into practice-first vs. standard paths. `COMPLETED`(52) exceeding several intermediate steps confirms the flow branches by profile type (not every profile asks for an exam date) rather than being strictly linear, consistent with Round 1's existing caution about this funnel's shape.

**Net:** two independent signals (Query 7's field-based rate, Query 8's event-based ratio) both show onboarding completion up over the last few days, with no known causal driver. **Correction (2026-07-28, same day, after code investigation):** Query 8's completion ratio is not trustworthy as a second confirming signal — see the "Correction" note below. Query 7's field-based rate stands on its own and is unaffected. Does not change Phase 2 / Reusable Practice Assets' status, and does not substitute for the Round 2 retention re-read still due after 2026-08-06.

**Correction, same day — `ABANDONED` root cause found, `ONBOARDING_V2_ABANDONED > ONBOARDING_V2_STARTED` was not a window-boundary artifact.** Investigated via direct code read plus an independent Opus pressure-test (both read `frontend/app/onboarding/page.tsx` directly). Two compounding bugs, not one:
- **Over-fire:** the abandonment-tracking effect's cleanup is keyed on `[draft.currentStep]`. React runs a `useEffect` cleanup both on true unmount and immediately before the effect re-runs on a dependency change, so `ONBOARDING_V2_ABANDONED` fires on nearly every step transition, not only true page-leave, for any session that hasn't yet hit one of the ref-clearing terminal points.
- **Leak paths:** several early-return redirects (already-verified-email check, two separate already-completed-onboarding checks, a `getMe()` rejection) never clear the abandonment guard and never reach the `ONBOARDING_V2_STARTED` tracking code either — so those sessions fire `ABANDONED` with no matching `STARTED` ever recorded. Two of these branches sit on the same underlying condition three lines apart, and only one clears the guard — strong evidence of an oversight, not intent.
- The two bugs compound: the over-fire pulls completers into the `ABANDONED` set (they fire it on their first step change, before reaching a terminal point) rather than leaving them out entirely, which is why the observed gap was a narrow 1-user margin (69 vs. 68) rather than something wilder.
- **Same mechanism implicates Query 8's completion ratio above:** `ONBOARDING_V2_STARTED` is gated behind a successful `getMe()` async round trip, while `ONBOARDING_V2_COMPLETED` is not similarly gated — `STARTED` is structurally undercounted relative to `COMPLETED`, which can inflate the `COMPLETED`/`STARTED` ratio independent of any real funnel improvement. Treat the 66.7%→76.5% ratio move above as **not established as real** until this is fixed and re-measured.
- **Separately, real abandonment is currently never captured at all:** no `pagehide`/`beforeunload`/`visibilitychange` handler exists anywhere in the file — tab close or hard navigation away produces zero `ABANDONED` event. The event was wrong in both directions (over-fires on false positives, misses true positives) before this fix; the tab-close gap is a distinct, larger, not-yet-scoped issue.
- **Fix scoped and sent to Codex** — `docs/codex-prompts/v0.61.0-onboarding-abandoned-event-fix.md`: decouple the cleanup from `draft.currentStep` via a ref, and add one shared invariant (`!startedTrackedRef.current || !shouldTrackAbandonmentRef.current`) so `ABANDONED` structurally cannot fire without a prior `STARTED`, rather than patching each leak site individually.
- **Discriminating query available, not yet run:** `16-abandoned-leak-path-check.sql` — checks (with no time bound) whether any user has an `ABANDONED` event but has never fired `STARTED` at all. `>0` confirms the leak paths as a real, not just theoretical, contributor to the observed gap.

## What this does not do

Does not decide, on its own, whether to proceed to Reusable Practice Assets & the Return Loop or
either half of Phase 2 — that decision is the owner's, informed by whichever hypothesis this read
actually supports, per `07-reprioritization.md` and the "Nothing here is authorized for
implementation until ratified" rule in `docs/product/ROADMAP.md`.
