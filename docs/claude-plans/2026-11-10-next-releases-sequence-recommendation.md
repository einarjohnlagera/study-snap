# Next releases after v0.180.0: recommended sequence, with the evidence behind it (PROPOSED, NOT APPROVED)

Written 2026-11-10 by the Claude Code release session, answering the Product UX and owner request for the missing future-release planning response. **Planning only: no scope here is approved, nothing is implemented, no production change is proposed for authorization.** Release numbers are not final. The model-retirement schedule was checked on the provider's own deprecations page; other provider facts (prices, Luna's reasoning behaviour) are from secondary sources and are marked.

## 1. Recommended order

| Order | Release (working number) | Theme | Why here |
|---|---|---|---|
| 0 | `v0.180.0` | Public Page Reliability and Privacy | Built and verified; deploy plan in `2026-11-10-v0180-deploy-plan.md` (CONDITIONAL GO, not authorized) |
| 1 | `v0.181.0` | Affordable NoteLib (PH pricing) | Owner decisions of 2026-11-04 are complete, the change is small (about 3 source files), and nothing else is blocked on it |
| 2 | `v0.182.0` | Remaining interactive LLM migration | **Deadline-driven: `gpt-4.1` and `gpt-4.1-mini` leave the API on 2027-01-14** (the roadmap's 2026-12-15 checkpoint is 35 days away, the shutdown 65). Moved AHEAD of Challenge Quiz quality |
| 3 | `v0.183.0` | Challenge Quiz Quality, revisited | Optional improvement with an uncertain payoff, and its measurement harness is reusable by the migration benchmark; it should not compete with the deadline |

**Why the migration is ahead of Challenge Quiz quality:** at roughly one to two weeks per release with a 24-hour minimum between deployments, a third slot lands in late December against a hard January deadline, with holiday slack and a database whose history is kills after restarts. The migration's benchmark needs OpenAI-key runs (the owner's) and can be designed and run during the `v0.180.0` and `v0.181.0` watch windows without touching production. The one thing that could argue for the reverse order is if Challenge Quiz quality turned out to be the reason learners leave; the evidence says learner-facing LLM volume is tiny (section 3), so deadline risk wins.

## 2. `v0.181.0` Affordable NoteLib

- **Scope (the 2026-11-04 decisions, re-verified against `origin/main` on 2026-11-10):** Plus ₱99 and Pro ₱149 monthly (PH only), USD unchanged; hide Pro yearly (₱1,999) and the Pro exam pass (₱599) via their existing `active` flags (`PRICING_PH_PRO_YEARLY_ACTIVE`, `PRICING_PH_PRO_EXAM_CYCLE_ACTIVE`); plain price, no end date; `INTRO-PH-*` vouchers stay inert. The brief's frontend change list still matches the code (`frontend/lib/pricing-config.ts:44-77` still holds 179, 249, 1,999 and the 149 and 199 intro amounts; `application.yaml:370,383,387,391` still default to 179, 249, 1,999, 599). Source: `docs/claude-plans/2026-11-04-price-decrease-release-brief.md`.
- **Exclusions:** no entitlement or quota change, no new tier, no USD change, no checkout or Xendit change, no voucher change, no Challenge Quiz or prompt change (pricing is kept separate so its effect is measurable on its own).
- **Render environment-variable redeployment risk:** the price is an env change, which is a Render redeploy (an env save that redeploys counts as a deploy; `CLAUDE.md` forbids Claude from calling the environment update). It is the owner's action, attended, with the deploy checklist, and not within 24 hours of another deploy or of a non-deploy kill.
- **Two corrections to the brief's deploy order (decisions needed, section 6):**
  1. **The brief says to keep the yaml defaults untouched "so the release does not trigger a second Render deploy".** A kickoff that bumps `backend/pom.xml` would trigger one anyway on merge (Render's root directory is `backend`). If the `pom.xml` bump is skipped for this frontend-only release, the only Render restart is the owner's env save.
  2. **The brief orders the frontend merge and the env save "on the same day".** That conflicts with the 24-hour minimum, which Product UX says stays. Recommended: **frontend merge first (Vercel only), then the env save at least 24 hours later, then the owner's ₱99 test purchase.** The fallback prices (shown only if the backend pricing call fails) then go live before the env, which the brief worried about; the exposure is cosmetic and narrow, because a backend that cannot answer the pricing call generally cannot create a checkout either, but confirm at implementation whether the pricing endpoint can fail while checkout still works.
- **Display check still owed at implementation:** between the frontend merge and the env save, the backend still reports the old price and an inert-or-active intro; verify what the plain-price lines show in that interval.
- **Measurement plan (stated before the data; counts, not rates, because the denominators are tiny):** the 2026-11-04 read in the brief found 18 invoices from 14 people in total (4 of them the owner's) and, **since 2026-10-09**, 0 genuine completions and 1 genuine initiation; that read is not repeated today and should be refreshed with a read-only SELECT at kickoff. Track weekly, excluding the owner's accounts: (1) genuine checkout starts (invoices created); (2) successful payments (webhook-confirmed activations only); (3) conversion as completions over starts, reported with both numbers; (4) paid usage: generations and quiz sessions by paid accounts per week from the usage table. Read at +14 and +28 days from the env change, as a `[CHECKPOINT]` row dated at that change. **Proposed first signal (for the owner to accept or change): at least one genuine completion within 28 days.** Margin is not computed (provider fee, tax and premium-model cost per paid user are unknown); revenue per paid learner falls 45% (Plus) and 40% (Pro) against list.

## 3. `v0.182.0` Remaining interactive LLM migration

**Verified routing (code read 2026-11-10; production `action=llm_call` log lines: a query for 2026-11-03T00:00Z to 2026-11-10T14:00Z returned 84 lines with `hasMore: false`, the first at 11-03T13:12Z (after the 10:56Z Stage A restart) and the last at 11-09T14:41Z, none in the following 23 hours; the count assumes Render's retention covered the whole window):**

| Route and model | Used by (code) |
|---|---|
| `TIERED` (Luna for Free, Sol for Plus/Pro and admin): already migrated | Study Pack family; **Challenge Quiz start, +5 and multi-note** (`ChallengeQuizService.java:612,884,1854`); official Challenge templates (`OfficialChallengeQuizTemplateService.java:292`); note generation |
| `INTERACTIVE_QUIZ`, `LLM_MODEL_INTERACTIVE_QUIZ`, default `gpt-4.1-mini` (**still pinned**) | Adaptive Practice and Quick Review (`QuickReviewAdaptivePracticeService.java:253,519`), the Quick Review study tip (`QuickReviewStudyTipService.java:67`), teacher quizzes (`GeneratedQuizService.java:132`), Board Exam generation inside the Challenge service (`ChallengeQuizService.java:1155`), Long Exam and its pool (`LongExamService.java:1167`, `ExamQuestionPoolService.java:295,324,333`) |
| `PAID_INTERACTIVE`, `LLM_MODEL_PAID_INTERACTIVE`, default `gpt-4.1` (**still pinned**) | Interview Practice quiz (`InterviewPracticeService.java:697`) and the collection companion (`NoteCollectionService.java:1027`) |
| `CRITIQUE`, `LLM_MODEL_CRITIQUE`, default `gpt-4.1-mini` (**still pinned**) | Ask Companion (`OpenAiAskCompanionLlmService.java:80`) and Interview critique (`OpenAiLlmStudyPackService.java:431`) |

**The Challenge Quiz does NOT need migration**; production logs show it on Luna and Sol. In the 84-line window: 59 `TIERED` Sol calls on the admin tier (the owner's own account), 6 `TIERED` Luna calls on the Free tier, 19 `INTERACTIVE_QUIZ` calls on `gpt-4.1-mini`, and none on `PAID_INTERACTIVE` or `CRITIQUE`. **Learner-facing LLM volume is tiny (about 25 non-admin calls in about 6.5 days as logged), so the benchmark's risk is correctness and compatibility, not cost.** What the production env actually holds for the three pinned variables is not readable by Claude; the logs show the `INTERACTIVE_QUIZ` route resolving to `gpt-4.1-mini`.

**Provider facts.** **Verified on OpenAI's own deprecations page, fetched 2026-11-10 (`https://developers.openai.com/api/docs/deprecations`, also served at `platform.openai.com/docs/deprecations`):** GPT-4.1 (`gpt-4.1-2025-04-14`) and GPT-4.1 mini (`gpt-4.1-mini-2025-04-14`) are shut down on **January 14, 2027**, with **`gpt-6-luna`** as the listed replacement; fine-tuned versions follow the same schedule. (The repo's 2027-01-14 date and the roadmap's 2026-12-15 checkpoint stand.) **From secondary sources only, not on that page** ([DEV Community summary](https://dev.to/max_bob/openai-api-deprecations-2026-2027-every-shutdown-date-and-recommended-replacement-gpt-4o-2ffm), [AICoder](https://aicoder.com/news/news-20261014-openai-gpt-4o-gpt-4-1-api-shutdown-jan-2027-gpt-6-luna)): Luna is a reasoning model with different tool-calling behaviour, so it is **not a drop-in swap**. The repo's own price table (`StudySnapProperties.java:608-609`) agrees with the reported rates: Luna $0.10/$0.50, Sol $2/$10, `gpt-4.1` $2/$8, `gpt-4.1-mini` $0.40/$1.60 per million tokens. The model IDs `gpt-6-luna` and `gpt-6.1-sol` are confirmed by successful production calls through our own key. Secondary sources disagree on the Sol name ("GPT-6 Sol" or "GPT-6.1 Sol"); the repo uses `gpt-6.1-sol` and it works.

- **Scope proposal:** benchmark each remaining workflow on candidate replacement models for blind-scored correctness (answer-key validity first), parse success, latency against the existing timeouts, and cost per call; then switch the three env variables only for workflows that pass, with the owner making each env save. Per-workflow gates stated before the run, as in the `v0.176.0` method.
- **Exclusions:** no entitlement change, no verifier switch, no prompt rewrite, no Challenge Quiz change.
- **Note:** Board Exam and Long Exam run on the pinned model with no answer-key guard (flagged by the `2026-10-26` scope file); the migration is a natural moment to test their answer keys, but adding a guard is a separate decision.
- **Success measure:** every pinned path is on a supported model, or has an owner-accepted exception, before the 2026-12-15 checkpoint, with the benchmark record preserved.

## 4. `v0.183.0` Challenge Quiz Quality, revisited

- **What the evidence says (v0.176.0 and v0.177.0, both HOLD):** prompt wording alone did not lift distractor plausibility to the gate for either model; on Sol it made it worse (3.92 to 3.65). On the unchanged prompt Luna scored 3.27 and Sol 3.92 on the same notes, so **model routing already accounts for a gap larger than any wording change tested**, with a run-to-run noise of about 0.17 on a 20-note start. The learner-visible gap is therefore mostly a Free-tier (Luna) gap.
- **Smallest next experiment (new hypothesis, preregistered, no production prompt change):** test ONE lever against the existing baseline: **Luna plus a separate distractor-repair pass** (the repair call regenerates only the distractors of already-keyed questions, **but a regenerated distractor can itself be a second correct answer, so G1 answer-key correctness stays a gate on this arm**), against Luna alone, with Sol-alone as the routing reference arm. Same frozen notes, a single reviewer source for every arm (the `v0.177.0` audit found part of Sol's difficulty result depended on which reviewer scored it), rubric v2, **G2 distractor plausibility as the single primary metric** with a pre-stated minimum difference and an interval that must exclude zero, more notes than 20 to beat the noise, Luna and Sol reported separately, and cost and latency measured per call. No production prompt change unless the gates pass and the owner approves.
- **Exclusions:** no entitlement change; the three verifier switches stay OFF (separate authorization); no repeat of the prompt-only change without a new hypothesis.

## 5. Other deferred work (classified, none approved)

| Item | Class | Note |
|---|---|---|
| Model pins (the 2026-12-15 checkpoint) | **Urgent, deadline-driven** | Section 3 |
| Board Exam "Redo Missed" silently ends a started Board Exam (`ROADMAP` row C6, live defect predating v0.175.0) | **High learner value; revenue-related once pricing creates paying learners** | The owner deferred it because there were no paying learners; **that premise stops holding after `v0.181.0`.** Candidate to move up (decision, section 6) |
| Board and Long Exam start-timer and expiry sweep (the rest of C6) | High learner value | Second part of C6 |
| Per-instance profile cache's backend load; observability of profile failures | Stability or privacy | `v0.180.0` checkpoint rows and the D5 follow-up |
| Remaining database reliability (memory 264 of 268.4 MB at 2026-11-02, restart-then-kill history) | Stability | No upgrade (owner, 2026-11-02); every deploy is watched |
| Challenge Quiz session edge cases | Deferred pending evidence | |
| Outcome-aware question-bank selection (Phase 2) | Deferred pending evidence | Learner-owned stock is small |
| Per-question "Report a problem" (Phase 3) | Deferred pending evidence | Needs the owner's acknowledgment copy |
| Retention and activation follow-ups | Deferred pending evidence | Stage 2 reads are dated 2026-11-23 or later |

**Nothing found that must supersede the order above**, with one flag: the Redo Missed defect should be scheduled no later than the first release after pricing.

## 6. Decisions needed (minimum)

1. **Approve the order** (pricing, then migration, then Challenge Quiz quality), or name the reason to change it.
2. **Stop bumping `backend/pom.xml` on frontend-only or env-only releases from `v0.181.0` on?** Each bump costs a Render restart on a 256 MB database whose history is kills after restarts. Recommended: yes. (`v0.180.0` keeps its bump and is a normal release with no `[skip render]`, owner decision 2026-11-10; this question is still open for `v0.181.0` onward.)
3. **Pricing deploy order:** frontend first, then the env save at least 24 hours later (recommended), versus the brief's same-day order.
4. **Whether the Redo Missed fix moves up** to right after pricing (recommended: yes, as its own small release or the first item of the next one).

## 7. Roadmap and release-document corrections still needed (listed, not yet applied except where noted)

- **Price brief and its Backlog row:** "`v0.180.0` (tentative number)" is now `v0.181.0`; the "no second Render deploy" and "same day" statements conflict with the points in section 2.
- **Stage B deploy plan** (`2026-11-04-v0179-stage-b-deploy-plan.md`): "a merge with nothing under `backend/` does not redeploy Render" holds only when no commit in the pushed range touches `backend/` (the `#1573` redeploy shows the range matters).
- **`scripts/check-deploys.sh`:** its "redeploy the lagging platform" advice contradicts the approved reading when a skip was intentional; a note or flag is a separate small script change.
- **`docs/claude-plans/2026-10-16-llm-model-migration-benchmark-design.md`:** its slot table still lists `gpt-4.1-mini` and `gpt-4.1` as the FREE and PREMIUM defaults; they are `gpt-6-luna` and `gpt-6.1-sol` now.
- **`ROADMAP` row for the 2026-12-15 checkpoint:** "Not covered by any plan yet" stops being true once this sequence is approved; and the C6 row's "waits because there are no paying learners" goes stale with `v0.181.0`.
- **Still-true background to keep visible:** 49 old checkpoint rows are past their due date and were listed, not actioned, at the `v0.180.0` kickoff.
