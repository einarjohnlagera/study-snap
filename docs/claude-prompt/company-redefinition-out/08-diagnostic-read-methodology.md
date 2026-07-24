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

## What this does not do

Does not decide, on its own, whether to proceed to Reusable Practice Assets & the Return Loop or
either half of Phase 2 — that decision is the owner's, informed by whichever hypothesis this read
actually supports, per `07-reprioritization.md` and the "Nothing here is authorized for
implementation until ratified" rule in `docs/product/ROADMAP.md`.
