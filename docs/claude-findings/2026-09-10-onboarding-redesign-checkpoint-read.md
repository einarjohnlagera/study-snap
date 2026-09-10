# 2026-09-10 — the onboarding redesign was already built, and its checkpoint's kill criterion does NOT clear

## Status: reads only. No code, config or prompt changed. No release opened.

Run in response to *"scope the onboarding redesign."* **The scoping stopped at the first verification
step**, because the thing to be scoped had already shipped. All queries are `SELECT`s executed
read-only against `notelib-db-prod` (`dpg-d6tvb8fkijhs73fda4m0-a`).

Claims are labelled **VERIFIED** (a query result or code opened) or **INFERRED**.

---

## 1. ⚠️⚠️ THE ONBOARDING REDESIGN SHIPPED AS `v0.73.0` (signed off 2026-08-12, served 2026-08-14). IT WAS ABOUT TO BE SCOPED AGAIN.

**VERIFIED in code, not from a doc:**

- `frontend/app/onboarding/page.tsx:117-126` — `STEP_NAMES` holds **eight** entries: `profile`,
  `course-program`, `learner-level`, `first-intent`, `input-method`, `note`, `generating`,
  `completion`. **This is the 5→8 split, live.**
- `docs/archive/RELEASES_ARCHIVE.md:12805` — `## v0.73.0 - Onboarding Redesign`, **Status: Released**,
  signed off 2026-08-12. Eight of ten planned items landed.
- Item 10 — the per-step funnel whose absence the Backlog row calls *"one thing no read can currently
  answer"* — **shipped in that release** and is emitting (§3b below reads it).

### ⚠️ How this nearly became the third repeat of a documented failure

`CLAUDE.md` records the Study Plan Builder row that read **NOT SHIPPED** for 41 releases after
shipping and *"was offered to the owner as a `v0.137.0` release candidate; had they picked it, a Codex
prompt would have been written to build something that already existed."* **This is that, one release
later, on a much larger item** — an 8-screen rewrite of a 2506-line file.

**The row that caused it is self-contradicting in exactly the way `CLAUDE.md` step 4 predicts.** Its
Gate cell holds the pre-ship `[DECISION]` text *and* the post-ship record, concatenated:

> …*if a narrower scope is wanted, instrument first — the events already exist and the denominator
> grows on its own*  **SHIPPED as `v0.73.0`, signed off and deployed 2026-08-12.**

Everything before `SHIPPED` describes a world in which the work has not happened. A scan reads the
`[DECISION]` framing, the migration hazard and the *"instrument first"* advice as live guidance —
**they are all discharged.** The two sentences sit in one cell with no separator.

⚠️ **The design doc has the same defect and no marker at all.**
`docs/claude-plans/onboarding-redesign-ux-review.md` (292 lines) is written entirely in the future
tense — *"split 5 screens into 8"*, *"renumbering is a live migration hazard"* — and **carries
nothing recording that it was implemented.** Read cold it is indistinguishable from an open proposal.

---

## 2. ⚠️⚠️ THE DEPLOY DATE THE CHECKPOINT IS ANCHORED TO IS WRONG BY TWO DAYS, AND IT DECIDES THE VERDICT

The row dates the checkpoint *"deploy-relative (**deployed 2026-08-12**)"*. **That is the signoff date,
not the date the new flow reached learners.**

**VERIFIED** — first appearance of each genuinely-new post-split `step_name` (the five the `v0.73.0`
release notes list as new; `profile`, `generating`, `completion` and `confirm-practice` existed in the
5-screen flow too and cannot mark the boundary):

| `step_name` | First ever seen |
|---|---|
| `learning-context` (pre-split) | 2026-05-24 → **last seen 2026-08-12T02:26:32Z** |
| `input` (pre-split) | 2026-05-01 → **last seen 2026-08-12T02:26:32Z** |
| **`course-program`** | **2026-08-14T02:48:14Z** |
| **`learner-level`** | 2026-08-14T02:48:20Z |
| **`first-intent`** | 2026-08-14T02:48:24Z |

**The 8-screen flow began serving at 2026-08-14T02:48:14Z — 2 days and 3 hours after signoff.** Two
accounts that signed up on 2026-08-12 (01:43:30Z and 02:26:11Z) fired `learning-context` and `input`
**seconds after signing up**, which is direct evidence they were served the OLD bundle well after the
release was signed off.

⚠️ **This is the `v0.136.0` frontend-deploy-lag class again, showing up in a measurement instead of a
feature** — and it is exactly what `CLAUDE.md`'s *"A MERGE IS NOT A DEPLOY"* rule and
`scripts/check-deploys.sh` exist to catch. Here it did not break a feature; it silently mis-dated the
cohort boundary of the read that decides whether a release worked.

---

## 3. The `[CHECKPOINT — due 2026-09-11]` — **the kill criterion does NOT clear**

**Criterion, quoted from the row and written before the read:** *"if post-deploy completion is not
measurably above **62.4%**, the comprehension hypothesis this release was justified on is
**unsupported** — reopen the framing rather than iterating on further onboarding polish."*

Metric, per the row: completion for signups created on or after deploy, from
`users.onboarding_completed_at`, never from `ONBOARDING_V2_COMPLETED`.

**VERIFIED, on the corrected 2026-08-14T02:48:14Z boundary:**

| Bucket | Signups | Completed | Rate | vs 62.4% baseline |
|---|---|---|---|---|
| July and earlier | 351 | 212 | 60.4% | — |
| **Aug 1 → Aug 14 02:48 — PRE new flow** | 27 | 25 | **92.6%** | **p = 0.00043** |
| **After Aug 14 02:48 — POST new flow** | 18 | 15 | **83.3%** | **p = 0.0506** |

*(Exact one-tailed binomial against p = 0.624. The pre-deploy population reproduces the pre-declared
**62.4% (234/375)** baseline byte-for-byte, confirming the query shape matches the one that set it.)*

### The verdict, stated so it cannot be smoothed over

1. **The literal criterion FAILS.** Post-deploy completion is 83.3% against a 62.4% baseline, and at
   n=18 that is **p = 0.0506 — not "measurably above"** by any conventional threshold. It misses.
2. **The 13 days BEFORE the new flow shipped beat the baseline far more decisively than the 27 days
   after it: 92.6%, p = 0.00043 (~3.5σ).** The rise is real, large, and **entirely pre-deploy**.
3. **Post vs pre shows no effect at all: Fisher exact p = 0.375**, direction nominally *negative*
   (92.6% → 83.3%).

⚠️ **On the row's own stated boundary of 2026-08-12 this read would have returned 18/21 = 85.7%,
p = 0.019, and been recorded as a clean PASS.** The entire difference between "the redesign worked"
and "the kill criterion fires" is a two-day error in the deploy date. **The checkpoint's verdict was
decided by `scripts/check-deploys.sh`'s subject matter, not by the product.**

### Why the pre-deploy rise happened — INFERRED, and it is not the redesign

Weekly completion, **VERIFIED**, showing a smooth climb that starts six weeks before the deploy:

| Week of | Signups | Rate |
|---|---|---|
| 2026-06-29 | 44 | 40.9% |
| 2026-07-06 | 18 | 61.1% |
| 2026-07-13 | 14 | 71.4% |
| 2026-07-20 | 74 | 68.9% |
| 2026-07-27 | 17 | 76.5% |
| 2026-08-03 | 20 | **90.0%** ⚠️ pre-deploy |
| 2026-08-10 | 7 | 85.7% |
| 2026-08-17 | 5 | 80.0% |
| 2026-08-24 | 3 | 66.7% |
| 2026-08-31 | 5 | 100% |

Signup volume collapsed across the same window — **183 (June) → 146 (July) → 38 (August) → 7
(September to date)** — and completion rose monotonically as it fell. **INFERRED:** cohort
composition. Whatever supplied June–July volume brought signups completing at ~40–60%; what remains
completes at ~90%. **The redesign and the traffic collapse are perfectly confounded, and there is no
post-deploy high-volume period anywhere in the data to separate them.**

⚠️ **The row's denominator premise is falsified and must be corrected:** it reasoned *"signups run
~120/month, so ~120 is readable at 30 days."* Actual post-deploy volume is **18 signups in 27 days**.
**A distal re-date would not fix this** — at 6 signups in the last 7 days and none since 2026-09-07,
the read does not become answerable by waiting.

---

## 3b. What item 10 was built to answer — now readable, and it relocates the problem

The Backlog row's standing question: *which* step do learners abandon on. Post-deploy cohort only
(signups after 2026-08-14T02:48:14Z), distinct learners per `step_name`. **VERIFIED:**

| Screen | Distinct cohort learners |
|---|---|
| 1 `profile` | 16 |
| 2 `course-program` | 15 |
| 3 `learner-level` | 15 |
| 4 `first-intent` | 15 |

**There is no meaningful drop anywhere in screens 1–4.** One learner is lost between Screen 1 and
Screen 2; screens 2, 3 and 4 lose nobody. **The step-level abandonment the redesign was built to
locate is not present in the post-deploy cohort** — because the cohort barely abandons at all.

### ⚠️ The finding that should govern any future onboarding work

Branch taken at the Screen 4 fork, joined to `users.onboarding_completed_at`. **VERIFIED:**

| Path | Learners | Completed onboarding |
|---|---|---|
| **Ready-made branch only** | **11** | **11 — 100%** |
| Note-authoring branch only | 3 | 2 |

**Of the 14 learners who branch, 11 (79%) take the ready-made door, and every one completes.** The
note-authoring path — **screens 5, 6, 7 and 8, where the redesign's design effort was concentrated** —
is walked by **3 learners**.

**INFERRED:** the load-bearing onboarding surface is Screen 4's fork and the ready-made branch behind
it. Any future proposal that reworks the authoring screens is optimising the road ~21% of forking
learners take.

⚠️ **`confirm-practice` never fires `completion`** — it exits via `completeOnboardingAndLeave`. That is
correct and documented in `v0.73.0`'s pressure-test findings; recorded here because a `completion` row
reading 2 looks like catastrophic drop-off and is not.

## 4. ⚠️ The leak the redesign was justified on has stopped flowing

The justification, quoted: *"132 learners — 35.2% of all signups — verify their email and never finish
onboarding. That is the largest single drop anywhere in the funnel."*

**VERIFIED**, email-verified accounts with `onboarding_completed_at IS NULL`:

| Cohort | Stranded | Range |
|---|---|---|
| Pre-deploy | **132** | 2026-04-09 → 2026-08-08 |
| Post-deploy | **3** | 2026-08-15 → 2026-08-24 |

**The 132 is exactly the row's number, and it is a CLOSED historical population.** Current
abandonment is 3 accounts, and **none since 2026-08-24 — seventeen days.**

**This is the single most important fact for scoping.** The 132 are not an ongoing leak that a
better onboarding flow would stem; they are a fixed backlog of people who already left. **Onboarding
changes cannot reach them by construction** — they will never see onboarding again. Only an outbound
path (email, a resume link) can, and that is a different piece of work with a different owner
decision behind it.

---

## 5. Obligations

- This file needs a **Backlog Index row** (kickoff step 8 names `docs/claude-findings/`).
- **The `[CHECKPOINT — due 2026-09-11]` closes as: KILL CRITERION NOT CLEARED.** Post-deploy
  completion is 83.3% against 62.4%, **p = 0.0506** — not *"measurably above"* — while the 13 days
  **before** the flow shipped hit 92.6% at p = 0.00043. Per the criterion's own wording the
  comprehension hypothesis is **unsupported**, and the instruction is to **reopen the framing rather
  than iterate on further onboarding polish**. ⚠️ Do not re-date it: the denominator does not grow
  (§3), so a re-date is a permanent deferral wearing a date.
- **⚠️ Correct the checkpoint's deploy anchor from 2026-08-12 to 2026-08-14T02:48:14Z**, and record
  that the two-day error is what separated a recorded PASS (p = 0.019) from a fired kill criterion.
- **The Backlog row's Gate cell must be split.** Its pre-ship `[DECISION]` text and its post-ship
  `SHIPPED as v0.73.0` record are concatenated with no separator, so the discharged half — including
  *"instrument first — the events already exist and the denominator grows on its own"* — reads as live
  guidance. This is the `CLAUDE.md` step-4 contradiction class, and it is what nearly caused a
  shipped 8-screen rewrite to be scoped a second time.
- **`docs/claude-plans/onboarding-redesign-ux-review.md` needs an implementation header** recording
  that it shipped as `v0.73.0` on 2026-08-12 (served 2026-08-14), which items landed, and which were
  dropped or reversed. It is written entirely in the future tense and carries no such marker; read
  cold it is indistinguishable from an open proposal. **Its one evidence-based recommendation —
  "remove the exam-date question" — is doubly dead**: closed on review 2026-08-12 for the reach
  reason, and its stated justification (*the commitment prompt "requires a date before a review plan
  can be set"*) was **removed by `v0.139.0`**, which also gated that prompt on a preference 252 of 396
  accounts have OFF and capped it at 3 lifetime impressions.
- **The row's `~120/month` denominator premise must be corrected** to the measured 18 signups / 27
  days, with the note that waiting does not fix it.
- **The `v0.138.0` anti-drift** *"no `frontend/app/onboarding` work before the `2026-09-11` read"* is
  **discharged by this file**.
- ⚠️ **Nothing here is a scope.** The evidence says onboarding is not the next release: current
  abandonment is 3 accounts with none since 2026-08-24, and the 132 who left are unreachable by any
  onboarding change because they will never see onboarding again.
