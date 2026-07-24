# Knowledge Impact: CTO Evaluation of a Creator-Recognition Proposal

> Planning document. No code changed. Authored by Claude Code (not a Fable session) on 2026-07-25,
> in response to a GPT-authored roadmap-addition proposal ("Knowledge Impact") the owner relayed for
> a CTO-level evaluation, explicitly not implementation, explicitly inviting disagreement. Extends
> `company-redefinition-out/01` (strategic redefinition) and `04` (reusable assets / Reviewer
> decision); cross-references the pricing/segmentation debate captured in
> `docs/gpt-contexts/GPT_CONTEXT.md`. The owner separately framed this as "only for the idea we
> thought" — a future candidate to capture, not an immediate build; this document's verdict matches
> that framing but arrives at it independently, via verification, not by assumption.

## Decisions carried forward

**What prompted this.** GPT proposed a future feature: give note creators aggregate, non-addictive
feedback that their published notes are helping others ("your notes helped 42 learners this week")
— a pull dashboard, explicitly not social-notification/dopamine mechanics, course/subject-agnostic,
no hardcoded exam names — citing production growth (297 verified users, 12,211 public note views,
2,094 public note copies) as the trigger. The memo asked 12 specific questions plus a deliverable:
initiative decision, roadmap priority (explicitly not immediate), name, principles, phases, risks.

**The verdict: yes, it belongs on the roadmap — as a parked, data-gated future initiative, not now.**

**The load-bearing premise, verified before answering (Explore pass against the actual codebase, not
assumed):** does a real base of individual creators exist to reward, or is the cited engagement
concentrated on admin-curated content?
- ~232 of ~235 public notes are official/admin-curated (the Exam Hub course/programs); only ~3
  notes total have ever been published by non-official users — roughly 98.7% of public notes are
  official.
- The Admin dashboard's 12,211 views / 2,094 copies are a **single global aggregate**
  (`AdminDashboardService.getSummary()`). There is **no existing metric anywhere** that splits this
  by official-vs-community authorship, and **no existing query counts distinct non-official
  creators** at all. Both are buildable — the primitives (`owner_user_id`, the shipped
  `isOfficialAuthor`/`officialAuthorPredicate` classifier, per-note event aggregation via
  `findTopPublicNotesByEventType`/`countPublicNoteEventsByTypeAndNoteIds`) all exist — but neither
  has ever actually been run or joined together.
- Separately, the platform's own discovery mechanisms (Featured score, Popular thresholds, official
  curation depth) actively concentrate engagement on already-popular content — a rich-get-richer
  dynamic. Combined with the 232:3 ratio, it is a very safe **inference** — but explicitly not a
  *measured* fact — that the cited engagement is overwhelmingly on official content, not
  community-authored notes.
- One shipped inconsistency worth flagging if this classifier is ever reused for real: the Java-level
  `isOfficialAuthor` (`PublicProfileService`) checks the official account email OR `role == ADMIN`;
  the SQL-level `officialAuthorPredicate` (`PublicLibraryRepositoryImpl`) checks only `role ==
  'ADMIN'`. Resolve this before trusting a query built on it.

**What this does NOT mean:** that there's no real audience, full stop, forever. It means today's
tiny handful of individual creators can't yet be sized or rewarded meaningfully. That's a concrete,
checkable gate — not a veto.

**Two independent framings weighed against each other, deliberately not resolved unilaterally:**
1. **The strategic fork.** Everything decided this session so far — practice-first onboarding, the
   reusable-assets reframe (`07-reprioritization.md`), the "3 published notes indicts the
   community/flywheel narrative" line — points toward **curated/official consumption** being where
   NoteLib's actual traction is, and the **author-your-own-notes flywheel having none**. Knowledge
   Impact quietly assumes the opposite: that reviving the stalled author flywheel is worth investing
   in. That can be a legitimate, deliberate supply-side bet — but it is a real fork (double down on
   curated consumption vs. try to *ignite* community authorship are different companies), and the
   honest answer names it rather than resolving it unasked.
2. **The causal-arrow counter.** Near-zero publishing may exist *because* creators get nothing back
   for it — "publishing into a void" is exactly the deadweight GPT named. So Knowledge Impact is
   plausibly a small *lever* on the publishing rate, not merely a *reward* that must wait for
   publishing to already be high. This doesn't collapse the chicken-and-egg (a feature built for ~3
   people is still premature, and "you helped 1 learner this week" reads as a pity metric, not a
   motivating one) — but it means the gate below tracks creator count **and** raw publishing rate,
   not scale alone.

**Guardrail:** the 297-verified-user figure is acquisition proof, nothing more — it doesn't speak to
retention or to creator-base size, and shouldn't be read as momentum for this specific proposal.

---

# Full detail

## Answers to the memo's 12 questions

1. **Does it align with NoteLib's philosophy?** Yes, directionally — retrospective/aggregate,
   anti-notification, anti-leaderboard instincts match existing precedent (Companion's
   static/authored framing; readiness deliberately hidden from list/browse contexts to avoid role
   confusion).
2. **Should it exist at all?** As a gated future item, yes — not now. See the gate below.
3. **Dashboard vs. notifications?** Dashboard (pull-based). Matches the product's existing restraint;
   a live/real-time surface would be the one genuine philosophy violation here.
4. **Should it be completely passive?** Yes — a visit-when-curious surface, not push.
5. **Digest notifications?** Opt-in only, low-frequency (weekly/monthly), reusing the existing Email
   Preferences category system — never default-on without evidence, the same lesson the due-concepts
   digest already had to learn the hard way.
6. **Which metrics matter?** Weight downstream signal over raw counts — "led to N actual study
   sessions" says more than a view count. Retrospective, aggregate, effort-affirming language
   ("helped," not "ranked").
7. **What should never be shown?** Anything comparative/competitive across creators (leaderboards,
   "#1 this week"), anything real-time, anything identifying *who* viewed/copied (privacy —
   consistent with "private notes never mined").
8. **Unhealthy-competition risk?** Real, and avoidable the same way the product already avoids it
   elsewhere: keep every metric private-to-the-creator-themselves, never cross-creator, never ranked.
9. **Prevent gamification, keep motivation?** Frame around impact-on-others (a values signal), never
   volume/rank (a status signal). No streaks, no badges, no public leaderboards.
10. **Roadmap placement?** Parked/conditional, not scheduled — see the concrete gate below, not a
    vague "someday."
11. **What kind of feature is this (retention / creator / publishing / ecosystem)?** A
    **creator/ecosystem** feature, with at best a second-order retention effect on a tiny slice of
    users. **Explicitly not a lever on the ~2.4% general retention problem** that's still the binding
    constraint — this must not be allowed to jump the queue ahead of the pending 2026-08-06
    Diagnostic Read (see `08-diagnostic-read-methodology.md`).
12. **Rename?** "Knowledge Impact" is fine but slightly abstract; "Your Impact" is a cleaner, warmer
    fit with the product's existing voice (Companion, Coach, Today's Focus). Not worth over-indexing
    on — naming is the least important decision here.

**GPT's closing "Creator Dashboard" framing:** agreed — a dashboard *is* the natural shape of this
idea, not a separate concept. Same gate applies. **GPT's 17%-copy-rate observation** (2,094/12,211)
is addressed in the pricing/segmentation thread in `GPT_CONTEXT.md`, not here — it's a broader
engagement-health signal, not specific to creator recognition, and carries its own caveat (some
fraction of those copies are practice-first onboarding adoptions, not organic browse discovery).

## The gate (a one-time query pair nobody has run)

1. `COUNT(DISTINCT owner_user_id)` among public, non-official notes — sizes the real creator
   population today. Reuses `owner_user_id` (already on `NoteEntity`) and the `isOfficialAuthor`
   classifier (resolve the Java-vs-SQL inconsistency above first if this becomes a real query).
2. Attribute the existing 12,211/2,094 aggregate views/copies to official vs. community notes — join
   `analytics_events.entity_id → notes.id`, filter by the same classifier. Both
   `findTopPublicNotesByEventType` and `countPublicNoteEventsByTypeAndNoteIds` already do the
   per-note aggregation half of this; nothing today combines it with authorship.

**Un-park threshold:** a real, meaningful count of non-official creators (order-of-magnitude 20-30+,
echoing the bar this repo already uses elsewhere for "is there enough depth to justify building for
this") **and/or** visible upward movement in the raw community-publish rate — track both, since the
causal-arrow argument above means the feature could plausibly help create the very scale it needs,
not only wait for it.

## Risks to avoid, if/when this is un-parked

- Building the dashboard before the gate clears — the numbers would read as pity metrics on a ~3-
  creator base, undermining trust in the feature the first time anyone sees it.
- Any comparative/ranked framing across creators, even accidentally (e.g. a "top creators" admin
  view leaking into the creator-facing surface).
- Treating this as a retention fix and letting it compete for priority against the 2026-08-06
  Diagnostic Read or Reusable Practice Assets & the Return Loop — it isn't one.
- Resolving the strategic fork implicitly by building this without naming, out loud, that it's a
  supply-side (authoring) bet in a product whose current evidence favors curated consumption.

## What this deliberately does not do

Does not decide whether Knowledge Impact ships, ever — that's the owner's call once the gate clears,
per this roadmap's standing "nothing proceeds to `/kickoff` without explicit ratification" rule. Does
not change Phase 1-4 of the Company Redefinition roadmap, the Diagnostic Read's status, or the
Reusable Practice Assets initiative — those proceed on their own gates, untouched by this proposal.
Does not resolve the curated-consumption-vs-community-authorship strategic fork named above — that
choice is surfaced, not made, here.
