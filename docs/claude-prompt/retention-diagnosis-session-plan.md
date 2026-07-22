# Retention Diagnosis — Session Plan

**Status: diagnosis complete, v0.48.0 shipped from it (2 experiments, unproven, cohort data accruing). Everything below the v0.48.0 scope section is prioritization/strategy material for what comes next — none of it is scoped to a release yet.**

## Why this exists

W1→W2 retention is currently **2.4%** (3 of 127 eligible activated users returned in week 2), read from the production Admin dashboard on 2026-07-15. A prior read roughly three weeks earlier found 5.6% (2/36) — already flagged then as "the real constraint" (`docs/archive/conversion-funnel-finding.md`, v0.32.2). The two reads are statistically indistinguishable given cohort size (don't read this as "got worse"), but the level itself is catastrophic either way, and it has not materially improved despite two intervening releases that touched retention surfaces (v0.44.0's conversion/retention audit fixes; v0.46.0's due-concepts digest, exam-date pacing, and app-feel polish).

Two independent Fable consultations were run against the real production data and the actual codebase to diagnose *why*, not to generate a feature list:

- `retention-diagnosis-out/01-growth-diagnosis.md` — growth/retention lens, grounded directly in the codebase (retention email machinery, onboarding's persistence contract, the Facebook Group acquisition playbook).
- `retention-diagnosis-out/02-consumer-psychology.md` — consumer psychology / behavioral economics lens (Hook Model, Fogg Behavior Model, loss aversion, implementation intentions), run with no shared context from the first session.

The two sessions converged independently on the same core diagnosis from different disciplines. That convergence is itself a signal worth trusting.

## The convergent diagnosis, ranked

### 1. The retention infrastructure that exists has never actually been tested (highest confidence)

Both sessions independently found the same structural fact: every content-rich, specific retention trigger the product has (due-concepts digest, weak-concept nudge, weekly summary) ships **default-OFF**, and onboarding explicitly defers reminder-preference collection to Settings (`docs/features/onboarding.md`'s "Deferred Personalization" section). The only default-ON channel is a generic 3-day-inactivity email — the weakest possible trigger class: it names nothing the user owns, and it's additionally throttled to roughly ~60 sends/day across the entire user base against a shared Resend budget, with skipped candidates not even logged.

The practical conclusion: shipping v0.46.0's digest/pacing features did not "fail to move retention" — they were never in front of the population that needed them. The population that would benefit most (new, not-yet-habituated users) is structurally excluded, because opting in requires already being engaged enough to find Settings. This is a bootstrapping paradox, not a feature-quality problem.

### 2. The first session ends in psychological completion, not an open loop (high confidence)

The consumer-psychology session's framing, independently supported by the growth session's "session 1's magic is generative, session 2's value is retentional and invisible" point: activation is healthy (68.6% generate a pack, instantly) and the first quiz loop closes well (58.8% quiz within 7 days) — but a completed Study Pack plus a completed quiz *is a finished task* (Zeigarnik effect: finished tasks are forgotten, unfinished ones intrude on memory). Nothing in the current first-session experience teaches the user that forgetting is coming, that spaced review exists, or that the product is designed to manage that for them. They leave believing they got the full value already.

Everything the product has that *would* create a reason to return — concept health, exam countdown, pacing, streak — is only ever rendered on the Dashboard: visible exclusively to someone who has already come back. It reinforces a habit that has already formed; it does nothing to form one.

### 3. Exam date is the strongest retention primitive the product has, and it's optional and Board-only (high confidence)

Both sessions flagged this independently. `examDate` is only collected (optionally) for the `BOARD_EXAM` profile type. The countdown/pacing mechanics that create a *daily, arithmetic reason* to return — "you're N concepts behind pace with M days left" — all hinge on this one field, which most users never set. The consumer-psychology session frames this as the missing "commitment device": a user cannot feel behind on a pace they never committed to, and implementation-intention research (Gollwitzer) suggests asking for a concrete if-then plan at peak motivation (end of session one, right after a completed quiz) roughly doubles follow-through versus a vague intention.

### 4. Single-serving-utility risk is real, but only for one segment (medium-high confidence, genuinely split verdict)

Both sessions rejected the strong form of "the product is just a one-shot tool" and split the audience instead:

- **Exam-dated reviewees** (nurses, teachers, board takers): no structural ceiling. Their actual job ("be ready on date X") is inherently longitudinal and high-stakes. The failure here is a *conversion* failure — the product never converts "tool user" into "program enrollee" — not a category problem.
- **Anchor-less casual students** (one lecture's notes, no exam date, likely acquired via a Facebook Group challenge post per `docs/product/notelib-facebook-group-marketing-playbook.md`): plausibly a genuine partial ceiling. Their motivation is episodic (spikes before each test). Trying to force a weekly habit loop onto this segment will likely just depress the metrics without changing real behavior — the more honest psychological goal for them is *resurrection at the next motivation peak*, not habit formation.

This segment split matters because it changes what "fixing retention" even means — a single global mechanic will underperform if it's aimed at the wrong segment's psychology.

### 5. Acquisition-mix and channel-fit are real but secondary confounds (medium confidence, cheap to check)

The growth session flagged that Facebook-Group challenge-post acquisition ("people engage with a challenge first, discover NoteLib second") selects for drive-by intent, which could be inflating the apparent severity of the retention problem. It also flagged that email may be a structurally weak channel for a Filipino Facebook/Messenger-native audience — checkable directly from existing Resend open/click data before assuming email itself is the right channel to invest further in.

## What both sessions explicitly ruled out or downgraded

- **Pricing/plan enticement: confirmed red herring right now.** Free quota is essentially never hit (0% ceiling-hit rate), and retention collapses long before payment is ever relevant to a user. Both sessions agreed pricing psychology is not worth revisiting until W1→W2 clears roughly 15%.
- **Return-visit UX/friction: unlikely to be primary.** The returning-user Dashboard is already well-composed (Continue Studying, Today Focus, Focus Areas, streak) — the problem is that almost nobody reaches that surface at all. Friction on a page nobody opens isn't the bottleneck.
- **Content/Study Pack quality: genuinely indeterminate from current data**, and both sessions flagged the same missing metric as the way to resolve it (see below) rather than guessing.

## The cheapest next steps (data pulls and one config experiment, not features)

Both sessions converged on doing three cheap things *before* building anything, because each one discriminates between the competing root causes above. Runnable SQL for the first two is in `docs/claude-prompt/retention-diagnosis-out/03-data-pull-queries.sql`, written to reuse the exact activation/eligibility/return definitions `AdminFunnelService` already uses for the 2.4% dashboard number, so results are directly comparable.

1. **Pull week-1 depth**: of eligible activated users, what fraction complete a genuine second study session more than 24h after their first pack but still within week 1 — before any cross-week trigger is even relevant? Low W1 depth would mean session-1 value is hollow and no trigger fixes that alone; healthy W1 depth with a dead week 2 would isolate the problem to triggers. (Query 1.)
2. **Run the exam-date natural experiment on existing data**: compare week-2 return rates of users who set an exam date vs. those who didn't, using the identical cohort definition as the existing dashboard read. Pure query, no code change, and the fastest way to test whether the exam-dated segment genuinely behaves differently, which the whole segment-split argument above depends on. (Query 2.)
3. **Segment retention by acquisition source** (Facebook challenge-post referral vs. organic/direct) — **checked against the schema and this is not cleanly available today.** There's no UTM/referral/channel column on `users` and no dedicated signup-source table; the only candidate signal is unstructured `metadata_json` on a few signup/landing analytics events, with no guaranteed field. Query 3 pulls a sample of that payload so you can see firsthand whether anything usable is actually being logged before trusting any segmentation built on top of it. If it's empty, this pull needs new instrumentation first — treat it as a possible future task, not a diagnosis blocker.

Only after these reads should any mechanic get scoped, because they determine which of the ranked causes above is actually dominant for this product's real users — not which one is most narratively satisfying.

## Candidate directions surfaced (explicitly not scoped — reference material for a future, deliberate scoping pass)

These are the hypotheses both sessions proposed as testable, grounded in the diagnosis above. None of these should be picked up without first running the data pulls above, and none of these are release-ready specs.

- Flip the due-concepts digest (and/or weak-concept nudge) to default-ON for new signups, with unsubscribe already compliant (one-click unsubscribe exists today).
- Capture reminder-preference intent at a peak-motivation moment (onboarding completion, or immediately after the first quiz) instead of deferring entirely to Settings.
- Teach the forgetting curve explicitly at the end of session one ("you'll lose roughly half of this by Friday — we'll bring back the parts you missed") rather than ending on a plain terminal score.
- Ask for a concrete review-day/time commitment (an implementation intention) right after the first completed quiz, anchored to the user's own exam date where one exists.
- Make due-concept messaging loss-framed against the user's own earned progress ("mastery dropped from 74% to 58%") rather than generic "review to improve" gain-framing — with the explicit constraint that decay shown must reflect the real underlying model, not be manufactured.
- Make every trigger deep-link directly into an already-assembled review session (one tap to the first question), never onto a dashboard requiring the user to decide what to do.
- Expand exam-date capture beyond the Board-exam profile type to any profile with a real deadline, if the natural experiment above confirms it's the driver it looks like.
- Consider the channel question (email vs. a Facebook/Messenger-native or push-based channel) once open/click data is actually reviewed, rather than assuming email is sufficient by default.

## Data pull results (2026-07-15) and the updated read

All three queries were run against production. Results, and what they change:

**Query 1 — week-1 depth: 3.88% (5 of 129 eligible activated users).** This is the single most important result. It is roughly the same magnitude as W1→W2 retention itself (2.4–3.19%), not meaningfully healthier. Per the plan's own discriminator: this rules out the comfortable version of the diagnosis ("session 1 works fine, only the cross-week trigger is broken"). Users aren't failing to come back in week 2 after a healthy week 1 — they're barely coming back *at all*, even inside the same week, even though the existing default-ON 3-day inactivity email fires squarely inside this measurement window. That the only live trigger is already firing during this window and depth is still ~4% is evidence the "psychological completion / no open loop" cause (#2 in the ranked diagnosis) is at least as dominant as the dead-trigger-infrastructure cause (#1), not a secondary factor behind it. It also raises a live question the plan didn't originally ask: is the existing inactivity email actually being *delivered and opened*, or is it going out and being ignored? Worth a quick Resend open/click check for the `INACTIVITY` type specifically before assuming content alone explains this number.

**Query 2 — exam-date natural experiment: has_exam_date=true → 0% (0/35); has_exam_date=false → 3.19% (3/94).** This inverts the naive form of the segment-split hypothesis — exam-dated users did not retain better under the status quo; if anything, numerically worse (though 0/35 is a small sample and one return would have put it in line with the other group — don't read this as "exam date hurts retention"). The more accurate read: **passively storing an exam date currently produces no behavioral difference**, because nothing acts on it outside the Dashboard (which almost nobody reaches — same root cause as #1/#2 above). This doesn't kill the exam-dated-segment thesis; it kills the version of it that assumed the data alone would matter. It strengthens, rather than weakens, candidate direction H1 (ask for a concrete commitment/schedule at peak motivation, tied to the exam date) over the weaker version of just expanding who can set the field.

**Query 3 — acquisition source: not usable, confirmed as expected, with one new observation.** The sampled `metadata_json` payloads are real and structured (`SIGNUP_STARTED.source` = `"auth_page"` for every row, `SIGNUP`/`SIGNUP_COMPLETED.method` = `"google"`, `LANDING_CTA_CLICKED.placement`/`.destination`), but none of it captures external referrer/UTM/channel — `source: "auth_page"` describes internal navigation, not acquisition channel. The Facebook-Group-acquisition hypothesis (cause #5 in the ranked diagnosis) remains genuinely untested; the sampled events don't show evidence of it but can't rule it out either, since there's no tracking that would surface it either way. Treat cause #5 as still-open, not confirmed or denied, and deprioritize it — it would need new instrumentation to ever answer.

**Net effect on the ranked diagnosis:** causes #1 (dead trigger infrastructure) and #2 (session ends in completion, not an open loop) are both confirmed as real and are roughly co-dominant, not one clearly ahead of the other — the data doesn't let a single-cause story survive. Cause #3 (exam date as unexploited commitment primitive) is *strengthened* in its active form (H1) and weakened in its passive form (just having the field). Cause #5 (acquisition mix) stays an open question, deprioritized for lack of measurability.

## v0.48.0 scope (proposed 2026-07-15, confirmed and shipped — Released, merged to `main`)

Given the data pull results above show both remaining causes (dead triggers, no open loop) as roughly co-dominant rather than one clearly dominant, the recommendation was to test both at once — they're independently cheap, address different failure points, and are independently measurable against the same cohort methodology:

1. **Trigger fix.** Flip the due-concepts digest to default-ON for new signups, replacing reliance on the generic inactivity email as the primary channel. Shipped with a strengthened CTA (styled button, not a bare URL) after a pre-build Resend check found domain-wide click-through under 1%.
2. **Open-loop session ending.** End the first quiz on an explicitly incomplete state ("N of M concepts secured — the rest are best reviewed tomorrow") instead of a terminal score. Frontend-only, tests the completion/narrative-gap hypothesis directly, and doesn't depend on email working at all.

Both shipped in `v0.48.0` (see `RELEASES.md`), scoped independently of the bigger commitment-device (H1) and pre-decided-return-action (H5) ideas, which stayed deferred pending these two results, per the strategy checkpoint below. Both are unproven — mechanism shipped, lift not yet measured. Cohort data began accruing on merge; a meaningful read needs roughly 2 weeks.

## Strategy checkpoint (2026-07-15) — post-v0.48.0 prioritization, two Fable consultations

Run after v0.48.0 merged to `main`, before kicking off any further retention work, to sanity-check direction and prioritize the remaining backlog (App Shape candidates, New Capability Ideation's remaining 8 ideas, Bulk Quiz Generation). Two separate Fable sessions, same day — the second a targeted follow-up on items the first didn't individually address.

### Session A — overall prioritization and gut check

**Interim-window actions (now, non-confounding, cheap):**
- **Talk to actual users.** Zero code, zero confounding, and — per Fable — more informative at this scale than any further cohort analysis: email the 3 W1→W2-retained users ("why do you come back?") and 10–15 churned exam-dated users ("you had a board exam coming — why didn't you return?").
- **Ship UTM/referral tracking.** The diagnosis's acquisition-source pull (Query 3 above) is unanswerable until this exists. Cheap, compounds forever, shouldn't wait for a release theme.
- **Pull device mix** (mobile vs. desktop session share) — one query, informs both the App Shape mobile-tab-bar candidate and (per Session B below) Idea 9 (offline access).

**Decision rule for the v0.48.0 read, pre-committed now because the read will likely be ambiguous:** at ~2.4% base rate and typical signup volume, a two-week cohort is roughly n=20–40 — too small to statistically distinguish 2.4% from 10%. Rule: **any positive-or-ambiguous signal → ship H1 (commitment device) + H5 (pre-decided-return-action) together as one release (`v0.50.0` working name), not as two more sequential isolated experiments.** Rationale: they're two halves of one mechanism (commit at peak motivation → trigger honors the commitment → return lands on a pre-decided action), and the exam-date natural experiment (0/35 under status quo) already pre-validates the active-over-passive direction independent of the v0.48.0 read.
- **The Unified Next-Step Resolver (App Shape Core Feature candidate) is reframed as H5's actual infrastructure**, not standalone app-shape work — "pre-decided return action" needs one backend-resolved next-step contract; five quiz modes currently each compute their own.
- Track **week-1 return depth** (currently 3.88%) as the primary near-term metric, not W1→W2 — it accrues faster and the open-loop experiment should move it within days.

**Everything else in the App Shape backlog (Companion Live Milestones, Struggle Map, Concept-to-Note Back-Annotation) — hold indefinitely, doesn't touch the constraint.** Mobile bottom tab bar is the one conditional: promote it if the device-mix pull shows heavy mobile usage.

**Photo Capture of handwritten notes (New Capability Idea 6) — independently re-endorsed as the next Core-Feature bet, explicitly after the retention loop exists, not before** ("pouring new capture into a 2.4% bucket is the classic mistake").

**Bulk Quiz Generation — 5th deferral confirmed correct, reasoning still sound** (missing bulk-batching isn't what's blocking teacher adoption — teachers can already generate per-note; zero teacher users is a positioning/distribution problem, not a feature gap). Structural fix: convert from a per-release backlog decision into an explicit **trigger condition — auto-schedule once ≥5 active teacher accounts exist** — so it stops consuming a scoping decision every cycle.

**The hard critique, worth carrying forward, not just noting once:** the exam-date finding (0/35 retained) is being read charitably as "nothing acts on the trigger yet." The uncomfortable reading — flagged as the avoided question — is that even users with a board exam bearing down didn't find the product worth a second visit, which would be a *value* problem, not a *trigger* problem, and no amount of open-loop/Zeigarnik engineering fixes that. The user interviews above are the only way to arbitrate this; don't declare the comfortable reading correct without them.

### Session B — the remaining 8 New Capability Ideation ideas (Ideas 2, 3, 4, 7, 9, 10, 11; Idea 6 covered in Session A; Ideas 1/5/8 already shipped or found pre-existing)

**Idea 4 (Parent Readiness Digest) promoted to conditional retention candidate, gated on the H1 read.** Reframed as an external accountability trigger — the accountability party can't churn the way a self-directed learner can, which is the strongest form of the commitment-device hypothesis. Stays conditional, not v0.50.0 scope: the parent invite has to happen while the student is still engaged (a W1 action, not a rescue), and the worst-retaining cohort (exam-dated, skewing adult board takers) is a weaker fit for a parent-accountability mechanic than younger students would be. **If H1 shows signal → natural v0.51/v0.52 escalation candidate. If H1 flops → stays parked, inherits the same doubt.**

**Idea 11 (Study Buddy pairing) confirmed lowest, with a sharper reason than the original classification:** at 2.4% W1→W2, the probability both members of a pair are active in week 2 is vanishing — pairing doesn't counteract churn at this scale, it multiplies it (a buddy churning kills the accountability signal). Idea 4 strictly dominates it as an accountability play. One free probe: ask about organic PH review-center study-group culture in the interviews above — unprompted signal there would support a different, later social feature, not this specific two-person mechanic.

**Ideas 2 (teacher shared-quiz-results probe) and 3 (class groups) fold into the Bulk Quiz Generation trigger condition above, not tracked as separate backlog rows.** When the ≥5-active-teachers trigger fires, Idea 2 (cheapest demand test) ships first; its result decides between Bulk Quiz Generation and Idea 3.

**Idea 9 (offline Study Pack access) — cost correction: cheaper than originally scoped.** `frontend/public/sw.js` and `offline.html` already exist (a service worker with offline fallback shipped at some point) — the "first PWA-shaped investment" framing from the original session is stale; remaining work is content-layer (mark-for-offline UX, caching Study Pack data, staleness rules), not platform-layer. **Device mix alone does not promote this the way it promotes the mobile tab bar** — heavily-mobile users on cheap campus wifi have zero offline problem. Real trigger condition: **heavy mobile AND (meaningful PDF export volume OR offline-fallback-page hit rate OR interview connectivity signal)**. PDF export volume and offline-fallback hit tracking should be pulled in the same analytics pass as device mix.

**Ideas 7 (Listen Mode) and 10 (Bilingual UI) — unchanged, stay low.** Listen Mode: "audio improves a loop already running," and at 2.4% almost nobody has one running. Bilingual UI: top exam buckets (ALE/PNLE/LET) are administered in English and all study content is authored in English — UI chrome is the least plausible place for language friction to bind; revisit only if Idea 4 ships (parents are the one persona where it plausibly matters) or interviews surface a language stumble.

**Gray areas — need a real product decision before they're scopeable, not just a priority slot:**
- **Idea 2:** the attribution mechanism (signed-in respondent attempts vs. teacher-issued tokens) is unresolved, and it's unclear whether a respondent's quiz attempt can create a `QuickReviewSessionEntity` against a note they don't own — the session model has never done that. Needs an explicit decision before it's actually a "cheap probe."
- **Idea 4:** email-only digest (reuses the existing retention-email scheduler almost entirely, ships fast) vs. a full read-only parent dashboard (activates the `PARENT` profile type, bigger build). Fable's lean: email-only first, dashboard only if parents click through.
- **Idea 9:** the existing service worker serves `offline.html` on *any* failed navigation today — "offline access" requires deciding between real app-shell offline routing vs. a pre-rendered per-pack snapshot. Different builds, different costs.

**Zero-marginal-cost additions to the interim-window actions above (fold into the same interviews/analytics pass, don't run separately):**
- Interview script: who holds you accountable to studying (Idea 4/11), data cost and commute-study habits (Idea 9), Taglish comfort during the interview itself (Idea 10), organic study-group behavior (Idea 11), **whether churned exam-dated users looked for ready-made review materials for their exam and what they used instead if they didn't find any** (Smart Review Planning check, added 2026-07-15 — see below).
- Analytics pull: PDF export volume per user, offline-fallback-page hit rate (Idea 9's second evidence leg), **browse-without-adopt rate for the 35 exam-dated churned users** (did they visit Public Library / the official plan catalog and adopt nothing — the "searched, found shelves empty, left" signature), **a manual audit of published Official Review Set count and note-count per `courseProgram` bucket** (a by-hand, one-hour preview of what Smart Review Planning's Coverage Board would eventually automate) — all in the same pass as device mix and UTM/referral tracking.

**Schema check (2026-07-15), before running any of the above — which pulls are actually zero-code and which aren't.** Runnable SQL for the zero-code ones is in `docs/claude-prompt/retention-diagnosis-out/04-interim-window-queries.sql`; the interview script (including the content-gap, accountability, connectivity, language, and social probes folded in above) is in `docs/claude-prompt/retention-diagnosis-out/05-interview-script.md`.
- **Answerable now, zero code:** device mix (approximate — proxied off `refresh_tokens.user_agent`, no clean `device_type` column exists, classify by pattern match); PDF export volume (covers `QUIZ_REVIEW_EXPORTED` only — the Quiz Session Review PDF path; DOCX export at `/quizzes/*/export-docx` fires no analytics event at all, a separate gap if DOCX volume matters); the Official Review Set coverage audit (fully answerable — see the query, which accounts for the Goal/Subject hierarchy wrinkle where a Goal holds zero items directly).
- **Needs real instrumentation first, not just a query:** UTM/referral tracking (unchanged from the original diagnosis — no column/table exists anywhere); offline-fallback-page hit rate (`sw.js`/`offline.html` serve the fallback but report nothing back — zero beacon, zero analytics call); the *browse* side of browse-without-adopt (`published-plans-page-client.tsx`, the actual official-plan-catalog page, fires zero analytics events today — don't substitute `EXAM_HUB_VIEWED`, which tracks public-*note* discovery, a different funnel step, not the Review Set catalog). The *adopt* side of that metric is answerable now (`STUDY_PLAN_ADOPTED` events, or `note_collections.source_plan_id IS NOT NULL`), but without the browse side it's a weaker signal — "did they adopt anything," not "did they look and find nothing."

## Third Fable checkpoint (2026-07-15) — Smart Review Planning weighed against the retention thesis

Triggered by the user asking where a separate, fully-planned "Smart Review Planning" exploration (`docs/claude-prompt/fable-out/01–07`, an "Internal Curator" system for AI-assisted curriculum-driven Review Set assembly — curriculum templates, reuse-first matching, mandatory human review before publish, an admin Coverage Board, a read-only Learning Assistant) fit into this prioritization — it had gone unindexed for ~5 release cycles and was never weighed against the retention diagnosis at all. That gap is now closed by `docs/product/ROADMAP.md`'s Backlog Index (see its invariant/review-ritual header); this section is the reasoning behind that index's row for it.

**Verdict: "one constraint at a time" mostly holds, but with one real, currently-untested interaction.** Smart Review Planning is a supply-side scaling system — its whole economic argument (`fable-out/02-matching-coverage-flywheel.md`) is that coverage gets cheap once *many* curricula exist and reuse-matching beats hand-curation. At 127 activated users across essentially 3 exam buckets (ALE/PNLE/LET), that isn't the constraint yet. But `retention-diagnosis-session-plan.md`'s own "hard critique" (above, under the Strategy checkpoint) already flagged an unresolved uncomfortable reading of the exam-date finding: 0/35 retained might mean a *value* problem, not just a trigger problem. If that value gap is specifically "there's no curated content for my exam, I'd have to build it myself," Smart Review Planning's problem is real — nobody has tested this.

**The cheap test is already folded into the zero-marginal-cost additions above** — two interview questions plus a manual coverage audit (count Official Review Sets and notes per `courseProgram`), which is itself a one-hour, by-hand preview of what the Coverage Board would eventually automate.

**Sequencing if it turns out to matter:** the 7-document *system* stays behind Photo Capture, gated on three things, not one — the retention loop existing, the content-gap hypothesis confirming via the interviews/audit above, and hand-curation actually saturating (i.e., maintaining coverage manually starts hurting). But a **manual content sprint** — hand-curating a few Official Review Sets for the top 3 exam buckets — is not gated the same way and could jump the queue independently if the interviews confirm the hypothesis; it's content work, not engineering, so it doesn't compete with H1/H5 or Photo Capture for a release slot at all.

**No hidden prerequisite found elsewhere in the backlog:** checked directly — v0.46.0's exam-date pacing is explicitly scoped to "the learner's owned content only (explicitly not Smart Review Planning)," and Idea 4's Parent Digest reads the learner's own readiness signals, not official-catalog coverage. Neither quietly depends on this. One soft interaction worth tracking: if H1 (commitment device, tied to the user's exam date) ships and the official catalog is thin for their bucket, the commitment device "points at empty shelves" — not a blocker for shipping H1, but a reason the coverage question gets *more* relevant once H1 ships, not less.

## Interim-window pull results (2026-07-15)

The three zero-code queries from `retention-diagnosis-out/04-interim-window-queries.sql` were run against production. Query 4 (adoption activity for the exam-dated churned cohort) was left non-runnable as written — it required hand-splicing a CTE from a different file — and was skipped; it was the explicitly weaker of the four signals anyway, and interviews cover the same ground more directly.

**Query 1 — device mix: 198 mobile vs. 65 desktop distinct users (~75% mobile).** Decisive, not a coin flip. **This meets the App Shape mobile-bottom-tab-bar candidate's stated gate condition** ("promote if the device-mix pull shows heavy mobile usage") — it is no longer correctly described as "held," see the Backlog Index update. Note the token-count skew in the raw numbers (desktop 1637 tokens / 65 users ≈ 25/user vs. mobile 1486 tokens / 198 users ≈ 7.5/user) — desktop sessions refresh tokens far more per user, plausibly longer/more active sessions per desktop user even though there are fewer of them; worth keeping in mind if this data is used for anything beyond the binary mobile-majority read it was pulled for.

**Query 2 — PDF export volume: 1 export, 1 user, ever.** Functionally zero organic usage (very likely internal/test activity, not a real user). This closes off one of Idea 9 (offline access)'s two alternative evidence legs — its gate (heavy mobile AND (PDF export volume OR offline-fallback hit rate OR interview signal)) now needs either the not-yet-instrumented offline-fallback hit rate or a real interview signal; PDF export volume cannot carry it.

**Query 3 — Official Review Set coverage is real but modest, not empty.** Architecture (ALE), Nursing (PNLE), and Education (LET) — the three exam buckets this whole thread cares about — each have exactly one published Official Review Set: 52, 63, and 43 notes respectively (Accountancy also has one, 74 notes). This is a genuine, non-trivial prior: it cuts against the *strongest* form of the content-gap churn hypothesis ("there's nothing there at all"), but does not resolve the *weaker*, still-plausible form ("one set with ~50 notes isn't enough for a full board-exam syllabus"). The interviews are still doing real work here — this result narrows what they need to establish, it doesn't pre-answer it.

**Net effect on gates:** mobile tab bar's gate is met (ready to reconsider, not automatically "do it now" — see the decision point below). Idea 9 (offline access) is still held, now down to one remaining possible evidence leg. Smart Review Planning / manual coverage sprint's gate is unaffected — still waiting on the interviews to actually confirm or deny the content-gap-as-churn-reason hypothesis; the coverage number alone doesn't decide it either way.

## Fourth Fable checkpoint (2026-07-15) — PDF export's near-zero usage

The Query 2 result above (1 export, ever) prompted a side-question: PDF export (quiz session review, free/ungated for every plan and profile) has essentially no organic usage. A `GuidanceTip` already exists on the exact page where Export lives and hasn't moved the number — so "add a discovery tip" was already tried before this checkpoint, not a proposal still on the table.

**Verdict: this doesn't matter right now — log it, don't build.** The failed tip can't actually discriminate value-vs-discovery: at 2.4% W1→W2, the population that finishes a quiz, returns, and navigates into session history (where the tip lives) is already close to zero before the export question even starts — the feature is starved by the same funnel already being worked on, not proven low-value or poorly surfaced in isolation. Chasing this mid-experiment-cycle would be exactly the "second constraint" the current discipline exists to block. Export is a utility-completeness feature, not an obvious retention lever — if anything, a PDF sitting in someone's downloads folder lets them study *without* opening the app, which cuts against the return loop rather than reinforcing it.

**A pattern worth naming, since this is the second time it's shown up:** "add a `GuidanceTip`" keeps getting reached for as the fix (here, and implicitly in the original digest/nudge default-OFF diagnosis), and it keeps not being the fix. A `GuidanceTip` can only amplify intent that already exists at the location it's placed — it cannot create demand or route traffic to that location. Keep this in mind before proposing a tip as a solution to a reach problem anywhere else in this backlog.

**What to actually do:** one interview question folded into the already-queued script (see `retention-diagnosis-out/05-interview-script.md`, item 8) — "do you ever want your review material outside the app?" — arbitrates value-vs-discovery for the whole export/offline cluster at zero marginal cost, and doubles as evidence for Idea 9. Optionally, a page-view analytics event for the Quiz Session Review page (closes the "can't measure the funnel's top" gap) — but only opportunistically, bundled into some other release that already touches that page; not worth its own branch. **No surfacing work (post-quiz redirect, Note Detail placement, a more prominent button) until the retention funnel and the interview signal both resolve.**

## Explicit non-goals of this document

- This is not a release scope. Nothing above has been prioritized against engineering cost, nothing has anti-drift rules written for it, and nothing should be handed to Codex or implemented from this file alone.
- This does not replace the three data pulls above. Several of the candidate directions are contingent on what those pulls show (in particular, the exam-date-expansion idea and the segment-split framing).
- This is not a claim that the two intervening retention releases (v0.44.0, v0.46.0) were wasted work — both sessions were explicit that shipping default-OFF features to a population that never opts in is "never actually tested," not "tried and failed."

## Source material

- `docs/claude-prompt/retention-diagnosis-out/01-growth-diagnosis.md` — full growth/retention session
- `docs/claude-prompt/retention-diagnosis-out/02-consumer-psychology.md` — full consumer psychology session
- `docs/claude-prompt/retention-diagnosis-out/03-data-pull-queries.sql` — runnable SQL for the three data pulls above, schema-verified against the actual migrations
- `docs/archive/conversion-funnel-finding.md` — the earlier (v0.32.2) diagnosis that first flagged retention as the real constraint
- `docs/features/retention-emails.md`, `docs/features/onboarding.md`, `docs/features/dashboard.md`, `docs/product/notelib-facebook-group-marketing-playbook.md` — the codebase facts both sessions grounded their reasoning in
