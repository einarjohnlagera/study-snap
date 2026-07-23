# Activation Onboarding: Practice-First Branch for Board-Exam Learners

> Planning document. No code changed. Builds on the identity/thesis anchor in
> `docs/claude-prompt/company-redefinition-out/01-strategic-redefinition.md` (Decisions carried
> forward block) — does not redesign it. This is an onboarding **routing** change only.

## Decisions carried forward

**Branch logic.** Decision point: right after onboarding Step 2 (`Study Goal`) submits, before
`Step 3 — Input Method` renders. Condition: `profileType === BOARD_EXAM` AND a depth-qualifying
Official Review Set exists for the collected `courseProgram` (published, `itemCount > 0`,
`readyCount > 0` — same fields Dashboard already reads). If true, Steps 3–4 are skipped and
replaced by one "Confirm & Practice" screen: confirm `courseProgram`/`examDate` already collected →
show the matched Review Set (same card as Step-5's `DashboardStudyPlanSection`, promoted to primary)
→ one-tap `Start this plan` (existing free/instant/no-LLM `adopt`/`adopt-goal`) → persists
onboarding completion (existing `POST /auth/onboarding` call, just triggered earlier) → routes
straight into a **Quick Review** session on the first Study Pack-ready note in the adopted plan.
Zero authoring, zero generation. No qualifying Review Set → branch doesn't fire, falls through to
the unchanged Step 3→4→5 path; closing that coverage gap is an admin-curation dependency, separate
session.

**What's unchanged.** `STUDENT`/`TEACHER`/`PROFESSIONAL` keep the exact 5-step flow unchanged,
including Step 5's card. Only `BOARD_EXAM` collects an exam date today — no "exam-dated Student"
cohort exists, so the condition reduces to `profileType === BOARD_EXAM` in practice (same
depth-check should gate a future exam-dated-Student field too, not built here). Study Pack concept,
note ownership, and the generation pipeline are untouched — adopted packs are pre-existing content;
nothing calls `LlmStudyPackService`. Universal spine preserved: this skips pack *generation*, not
the pack — one already exists (via adoption) before the quiz.

**Validation metric.** Naive practice-first-vs-create-first-for-BOARD_EXAM is confounded — the two
arms are actually different course/programs (covered vs. uncovered tracks), so any gap could be
track mix, not path. Primary read is **pre/post on the same covered tracks** instead: BOARD_EXAM
learners on an already-covered track, W1→W2 retention for those onboarding just **before** ship
(forced create-first) vs. just **after** (practice-first fires) — same track, only path differs.
Adopt-to-first-quiz completion and time-to-first-practice are supporting diagnostics, not the
causal read. Floor: ~30 completed onboardings/arm (directional), ~75+/arm (decision-grade),
following this codebase's small-live-cohort-over-surveys precedent — reconcile against the actual
2.4%/0% sample size in Section 5 of the strategic-redefinition doc (not opened here per the
read-only-these-files constraint) before finalizing. Window: 14 days after the last onboarding in
the intake window, since the metric itself is W1→W2.

---

## 1. The redesigned onboarding branch (BOARD_EXAM, Official Review Set exists)

### 1.1 Where the branch is evaluated

The check happens once, immediately after Step 2 (`Study Goal`) submits `learnerLevel` +
`courseProgram` (+ optional `examDate`) through the existing Learning Profile update path. This is
the same underlying signal Step 5's supplementary adopt card already uses to decide whether to
self-hide ("card self-hides when the learner's course/program has no published plan" —
`onboarding.md`). Nothing new is being computed; the existing course/program-match-against-public-
plans check is simply run one step earlier and used as a hard branch instead of a soft,
supplementary, always-reachable card.

Concretely: the same `listPublicStudyPlans({ courseProgram })` (or `/collections/public`) call
already made for the Step-5 card and for Dashboard's `DashboardStudyPlanSection` is made right after
Step 2, before rendering Step 3. Depth is defined as `itemCount > 0 && readyCount > 0` — a published
plan that exists but has zero Study Pack-ready notes cannot deliver "land directly in a first quiz,"
so it does not count as depth for this branch (it may still count for Step 5's softer "recommended,
explore later" framing elsewhere — that's unchanged and out of scope here).

### 1.2 The replacement screen ("Confirm & Practice")

Renders in place of Step 3 (`Input Method`) when the branch condition is true:

- Headline confirms the target already collected at Step 2 — e.g. "You're preparing for
  {courseProgram}." — plus the exam-date framing already used on Dashboard's Exam Countdown card if
  `examDate` was supplied (reuse that existing countdown presentation, do not invent a new one).
- Shows the matched Official Review Set card: same visual component and props as the existing Step-5
  `DashboardStudyPlanSection` adopt card (`courseProgram`, `profileType`, `context="onboarding"`),
  just invoked at this earlier point and made the primary action rather than a below-the-fold
  supplementary surface. Card shows `{readyCount} of {itemCount} notes practice-ready` exactly as
  Dashboard already renders it.
- Single primary CTA: `Start this plan` (label reuses the existing `Start`/`Continue this {label}`
  resolution already in `DashboardStudyPlanSection` / `PublicStudyPlanCard`).
- No secondary "write your own note instead" escape hatch on this screen — Board Exam profile
  intent is narrow enough (per constraint 4/5) that offering both here re-introduces the decision
  this branch exists to remove. A learner who wants to author instead can always do so later from
  Library/Collections; onboarding's job is just the fastest path to a first real practice rep.

### 1.3 Adopt mechanics

`Start this plan` calls whichever of the existing adopt endpoints the matched plan's shape already
requires — standalone plan → `POST /collections/{id}/adopt`; Goal-shaped Review Set → `POST
/collections/{id}/adopt-goal` — using the same call-site branching logic Dashboard's matching-plan
section already implements. No new endpoint, no new adopt variant. Both are already free, instant,
and make no LLM call (constraint 4) — this branch adds no new cost model.

If the matched Review Set is a Goal, the first quiz (1.5) is drawn from the first Study Pack-ready
item, in position order, inside the first child Subject plan, in sibling order — reusing the
existing deterministic ordering already documented in `collections.md` (`siblingPosition asc`
children, `position` order within a plan). No new "which note first" resolver is introduced.

This branch's entire "zero generation" promise rests on `adopt`/`adopt-goal` carrying each note's
linked Study Pack into the copy, not just the note — constraint 4 grants this (adopt is "free,
instant, no LLM call" and "the Study Pack's stored quiz already powers Quick Review with zero
generation cost"), but it should be explicitly verified against the adopt implementation before this
ships: if a copied note ever lands without its Study Pack, it falls back to `DRAFT` and there is no
quiz to launch into.

### 1.4 Persistence timing

Today, `profileType` is persisted to the backend only at Step 5's completion call; onboarding
mid-flow (`onboardingCompletedAt == null`) is exempt from the profile-setup guard regardless
(`onboarding.md`, Server-Side Boundary). Collection adopt/adopt-goal are not in the enumerated
list of profile-gated mutations at all. So calling adopt at this earlier point, before the backend
has persisted `profileType`, violates no documented guard.

This branch triggers the existing `POST /auth/onboarding` completion call (persists `profileType`,
optional `examDate`, `onboardingCompletedAt`) at the moment the learner commits to `Start this plan`
— this is the practice-first analog of today's "Study Pack finished generating, now persist
completion" moment. There is no separate Step 5 render for this branch; completion persistence and
the transition into first practice happen in the same action.

### 1.5 Landing in the first quiz

The first quiz is launched as a **Quick Review** session specifically, not Challenge Quiz, Adaptive
Practice, or Board Exam Mode. This matters: Quick Review is the mode that runs entirely off a Study
Pack's already-stored questions with zero generation cost (constraint 4). Challenge Quiz's
progressive generation (starts at 5, adds 5 up to 20) and Board Exam Mode's own question-pool
resolution would both reintroduce a generation step this branch is designed to avoid — using either
here would quietly break the "zero note-authoring, zero generation" promise the whole branch rests
on. Launch reuses the existing Quick Review start path (the same one Continue Studying / Focus Areas
/ plan detail already use against a note's stored quiz) — no new session-launch code path.

After the Quick Review result screen (unchanged, existing screen), the learner lands on Dashboard,
where the already-documented Board Taker prioritization (`Exam Countdown`, `Start Board Exam`, `Weak
Areas`, `Adaptive Practice`) takes over exactly as `dashboard.md` describes today. No Dashboard
change is proposed.

### 1.6 No Official Review Set exists yet

If `listPublicStudyPlans({ courseProgram })` returns nothing, or returns a plan without depth
(`itemCount === 0` or `readyCount === 0`), the branch simply does not fire. The learner proceeds
through the existing, unmodified Step 3 (`Input Method`) → Step 4 (`Study Pack Generation`) → Step 5
(`Completion`) exactly as documented today. This still satisfies onboarding's stated job — "get the
user to a real first Study Pack" — through the only mechanism currently available for an uncovered
track: authoring one.

No new admin tooling, no new "check back soon" messaging, and no new fallback UI is proposed to
backfill coverage for uncovered tracks. Closing that gap is explicitly a dependency on the
admin-curation side (which Official Review Sets exist, for which tracks, at what depth) and is
flagged here as out of scope for a separate planning session, per the task constraints.

### 1.7 Interaction with existing generation guardrails

Idempotency-on-repeated-clicks, the back-button lock during active generation, and note-generation
retry (`onboarding.md`, Generation Safety) are all specific to Step 4's note/Study-Pack generation
step. None of them apply to this branch because no generation happens on this path. Adoption already
carries its own idempotency (`adoptGoal`'s `alreadyAdopted=true` no-op re-adopt, item-level idempotent
add-skip) — reused as-is, nothing new to build. If adopt succeeds but the completion call (1.4) then
fails, a retry is safe: re-adopting the same plan is the existing no-op success, and the completion
call can simply be re-issued on its own — no combined transaction or new rollback logic needed.

### 1.8 Interaction with Step 5's existing supplementary adopt card

Because this branch does not render a Step 5 for this cohort at all (1.4), there is no double-prompt
risk to guard against structurally. This is called out explicitly so an implementer does not instead
try to "keep Step 5 as-is" for this branch — doing so would re-show a recommended-plan card for a
plan the learner already just adopted seconds earlier, which the existing self-hide condition
(`no published plan for this course/program`) would not catch, since a plan *does* exist — it was
just already adopted. The correct implementation skips Step 5 for this branch, not merely reuses it
unchanged.

### 1.9 Difficulty note

Adopted Study Packs carry the curator's authored difficulty, not the learner's own `learnerLevel` —
this is a pre-existing, already-solved condition: the `copied-study-pack-regenerate-hint` one-time
tip (`guidance.md`) already fires whenever `copiedFromPublic === true` and the pack is ready,
prompting the learner to regenerate if the level doesn't match. This branch does not need a new
guidance surface; the existing tip already covers an adopted/copied pack.

---

## 2. What stays unchanged for TEACHER / PROFESSIONAL / generic STUDENT

**Exact branch point:** the same one described in 1.1 — the check inserted between Step 2's submit
and Step 3's render. For every profile except `BOARD_EXAM` matched to a depth-qualifying Official
Review Set, this check evaluates false and the existing Step 3 (`Input Method`) renders exactly as
documented, with no visible change to those users at all.

Per-profile rationale for why the condition should stay false for them, not just happen to:

- **TEACHER** — the note-authoring step exists so the teacher's *own* material becomes the seed for
  their Lesson Plan / Exam Builder output. There is no "Official Review Set" analog that serves
  "produce my own teaching content" intent; adopting someone else's public review set does not
  advance a teacher's actual onboarding goal. Constraint 5 (stay horizontal, do not degrade the
  creator-first flow) applies directly here.
- **PROFESSIONAL** — Interview Practice is scenario/note-based practice, not a stored-quiz consumption
  model in the same shape as Quick Review; there is no equivalent "land in a scenario drawn from an
  adopted pack's stored questions" destination to route to. Creator-first stays the only path.
  (`PARENT` remains unimplemented per `CLAUDE.md` and is not addressed here.)
- **Generic STUDENT** — today, Step 2 only shows the optional `Exam Date` field for `Board Taker`
  (`BOARD_EXAM`). A generic Student never supplies an exam date, so there is no reliable "target
  exam" signal to confirm on a Confirm & Practice screen, and no natural mapping from "Study Goal" to
  a single Official Review Set the way a board-exam track maps to one. Nothing in this plan invents
  that mapping or adds an exam-date field to Student's Step 2 — that would be new onboarding scope,
  not a routing change. If a future release adds exam-dating for generic Students, the same
  depth-qualifying-plan condition (not a profile-type check) should be the gate, consistent with how
  this plan frames the condition as "has a target + a matching Official Review Set exists," not
  hard-coded to the `BOARD_EXAM` enum value specifically.

No other onboarding.md-documented behavior changes for any profile: Profile Type Re-prompt,
copy-on-signup lightweight profile completion, Deferred Personalization, the Dashboard learner-level
follow-up prompt, Metadata Auto-Apply, and the Server-Side Boundary gating rules all apply exactly as
already documented, for every profile, including `BOARD_EXAM` users who take the practice-first
branch.

---

## 3. Interview-free validation plan

### 3.1 Why the naive split is confounded

A tempting first design: tag every `BOARD_EXAM` onboarding with `PRACTICE_FIRST` or `CREATE_FIRST`
based on whether the branch fired, then segment W1→W2 retention by that tag, holding `profileType`
constant at `BOARD_EXAM`. This is *not* apples-to-apples, because the tag is not randomly assigned —
it is fully determined by whether the learner's `courseProgram` happens to have a depth-qualifying
Official Review Set yet. `PRACTICE_FIRST` learners are, by construction, all on covered tracks
(likely more mature, more popular, possibly closer to an established exam cycle); `CREATE_FIRST`
learners are disproportionately on uncovered tracks. Any retention gap this produces could be track
popularity, exam-date proximity, or cohort motivation — not the onboarding path. Segmenting by
"furthest step reached" for a genuinely single population (as the strategic-redefinition decision
describes for the *Exam Hub activation funnel*) is a different, valid instrument; this specific
practice-first-vs-create-first bet needs its own design because the two arms here are pre-selected
by track coverage, not by chance.

### 3.2 Primary design: pre/post on the same covered tracks

Hold track coverage constant instead of profile type. For BOARD_EXAM learners on a `courseProgram`
that **already** has a depth-qualifying Official Review Set as of ship day (a track that was already
covered before this branch existed — likely a small number of tracks, given the admin-curation gap
noted in 1.6):

- **Pre cohort:** learners on that same track who completed onboarding in a window immediately
  *before* this branch ships (necessarily create-first — the branch didn't exist yet).
- **Post cohort:** learners on that same track who complete onboarding in a window immediately
  *after* ship (practice-first — the branch fires for them because the track already qualifies).
- Compare W1→W2 retention between the two cohorts. Same track(s), same profile type, only the
  onboarding path differs by which side of the ship date they landed on.

This needs the existing W1→W2 retention metric to be segmentable by `courseProgram` + cohort date, in
addition to `profileType` — stated here as an assumption to verify, not assumed away. The strategic-
redefinition decision already commits to reusing "the existing W1→W2 retention metric," which implies
it is computed from underlying user-level records that already carry `profileType`/`courseProgram`
timestamps; if in practice it is only available as a pre-rolled aggregate without those dimensions,
that is a real instrumentation dependency to close before this read can run, and should be flagged
back rather than discovered mid-read.

Residual confound to watch even in this design: calendar proximity to the actual exam sitting. If
ship date happens to land shortly before a major exam date for the covered track, the post cohort's
motivation may differ from the pre cohort's for reasons unrelated to onboarding path. Mitigation:
keep both intake windows short and adjacent to the ship date (see 3.4), and sanity-check that the
`examDate` distribution is similar between pre and post cohorts before trusting the retention read.

One more reason this design is conservative rather than contaminated: the pre-ship cohort is
create-first, but because their track is already covered, some fraction of them still reach Step 5's
existing supplementary adopt card and adopt the same Review Set at the end of onboarding anyway. So
the real contrast is closer to "create-then-maybe-adopt" (pre) vs. "adopt-only, immediately" (post),
not a clean "no adoption at all" control. That means if the post cohort still shows materially higher
retention, the practice-first effect is real and likely understated, not an artifact of the pre
cohort having zero exposure to adoption.

### 3.3 Stronger alternative: holdout (heavier, optional)

If the pre/post read comes back ambiguous or the confound in 3.2 can't be ruled out, a cleaner causal
design is a holdout: after ship, randomly route a slice of covered-track `BOARD_EXAM` learners to the
old create-first path (feature-flag the branch off for that slice) instead of comparing across a ship
boundary. This removes the calendar-proximity confound entirely because both arms run concurrently,
but it delays "everyone gets the practice-first path" and requires assignment/bucketing logic that
does not exist today. Recommend leading with 3.2's pre/post read since it needs no new
infrastructure and best fits this codebase's existing precedent of reading small live cohorts
directly rather than building holdout machinery first; reach for 3.3 only if 3.2's result is genuinely
inconclusive.

### 3.4 Diagnostics (supporting, not causal)

Track these regardless of which design above is used — they describe funnel health, not the
retention bet itself:

- **Adopt-to-first-quiz completion rate:** of `BOARD_EXAM` learners who reach the Confirm & Practice
  screen (branch fired), what fraction reach a *completed* first Quick Review session in the same
  onboarding session. Diagnoses drop-off inside the new branch itself (e.g., adopt succeeds but the
  learner abandons before finishing the quiz) — useful for iterating on the branch's UX, not for
  proving or disproving the practice-first bet.
- **Time-to-first-practice:** wall-clock delta from onboarding-started to first-completed-quiz-
  session, compared practice-first vs. create-first, same track where possible. Expected to be much
  lower for practice-first given zero generation latency and zero authoring time — a supporting signal
  that the mechanism works as intended, not the primary evidence for the retention claim.

### 3.5 Minimum cohort size and read window

- **Directional read floor:** ~30 completed onboardings per arm (same track, pre vs. post) before
  treating any observed retention gap as directional signal.
- **Decision-grade floor:** ~75–100 completed onboardings per arm before treating the gap as strong
  enough to justify a go/no-go call (expand the branch to more tracks, or not) — chosen to avoid a
  single-user swing dominating a small-percentage read, while staying consistent with this codebase's
  established practice of reading small live cohorts rather than waiting for classical statistical-
  significance sample sizes.
- **Read window:** at least 14 days after the *last* onboarding completion in the observed intake
  window, since the metric itself is W1→W2 — every user in the cohort needs to have had the chance to
  reach "week 2" before the split is read.
- **Suggested intake window:** roughly 2–4 weeks of onboarding starts on each side of the ship date,
  symmetric where possible, to reduce the seasonal/exam-calendar confound noted in 3.2. The exact
  calendar length needed to reach the per-arm floors above depends on current BOARD_EXAM signup
  volume on the covered track(s), which was not measured as part of this planning pass — the floors
  above are stated as population-size targets, not fixed day counts.
- **Reconcile against precedent:** this file's read-only-these-files constraint meant Section 5 of
  `01-strategic-redefinition.md` (which documents the actual 2.4%/0% figures referenced in the task)
  was deliberately not opened here. Before finalizing the thresholds above, cross-check them against
  whatever per-arm sample size actually produced that 2.4%/0% read — if that precedent used a smaller
  or larger floor than proposed here, prefer the precedent's actual number over this plan's estimate,
  since the whole point is consistency with how this codebase already treats small-cohort evidence as
  sufficient.

---

## 4. What does NOT change

This is an onboarding **routing** change only:

- **The Study Pack concept is untouched.** No new content type, no new generation shape, no change to
  what a Study Pack is or how it's structured.
- **The note ownership model is untouched.** Adopting the Official Review Set creates a private
  snapshot copy in the learner's own library, exactly as `adopt`/`adopt-goal` already work for every
  other adoption entry point in the product today (`collections.md`) — nothing new is introduced for
  this branch specifically.
- **The generation pipeline is untouched.** `LlmStudyPackService` / `OpenAiLlmStudyPackService` is
  never invoked by this branch. Every note and Study Pack the learner receives on this path was
  already generated (by its original author) and already published by an admin before this learner
  ever reached onboarding. Constraint 2 ("curation, never generation" — a learner never receives
  auto-generated content without an admin publishing it first) holds exactly as it does everywhere
  else in the product; this plan does not weaken or reinterpret it.
- **The Internal Curator / Learning Assistant split is untouched.** This branch only recommends and
  adopts already-published material through already-existing endpoints; it does not add a new
  generation-on-the-student-side path and does not blur the two-system boundary.
- **What changes is exactly:** which onboarding screens render, in which order, for one narrow
  cohort (`BOARD_EXAM` + depth-qualifying track), based on a check that already exists elsewhere in
  the product (Step 5's card self-hide condition), evaluated one step earlier and treated as a hard
  branch instead of a soft supplementary surface.
