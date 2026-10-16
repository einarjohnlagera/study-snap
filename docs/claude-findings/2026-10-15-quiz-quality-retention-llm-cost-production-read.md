# Production read: quiz-pool snapshot, post-repair contamination check, retention, and LLM cost/volume

## Status: diagnosis only. No code, config, or data changed. Fix handoff: `docs/claude-plans/done/2026-10-15-study-packs-residual-answer-key-defects-repair.sql` (owner-run). No Backlog Index row written by this session — Release Implementor's to add. **Update 2026-10-16 (Release Implementor): §2's repair was applied by the owner via the tightened copy `docs/claude-plans/done/2026-10-16-study-packs-residual-answer-key-defects-repair-tightened.sql` and verified read-only (`RELEASES.md` `v0.173.0` item 3). Backlog row added. §6's open items remain open.**

**Requested by:** Feature Planner, cross-session message, 2026-10-15 — read-only production diagnostics feeding a combined brief ("NoteLib — Quiz Reliability, LLM Model Strategy & Retention Investigation") that this session does not otherwise own. Four objectives, answered in §1–§4. All reads are `SELECT`/`SHOW`, read-only, per charter. No writes were made; no env vars or deploys touched.

**Merge-gate safety check (done first, per advisor review):** `v0.173.0`'s `[CHECKPOINT — due 2026-10-22]` signal-9 read opened today at 2026-10-15T07:09:09Z. Checked `*terminated by signal 9*` on `dpg-d6tvb8fkijhs73fda4m0-a` for the whole elapsed window (07:09Z→15:16Z): **zero kills.** `memory_usage` over the same window peaked at ~249 MB (14:31Z), never approaching the 268 MB ceiling, and none of the queries in this session's own investigation coincide with a kill. Stated plainly because this session ran several corpus-scanning queries against the same memory-constrained instance `v0.173.0` is actively measuring — they did not touch the checkpoint's count.

---

## §1 — Fresh pool snapshot (corrected from a naive first pass — read §5's note on what changed)

| Store | Rows | Questions | On `visibility='PUBLIC'` notes |
|---|---:|---:|---:|
| `study_packs.quiz` | 14,017 | 70,085 | 2,002 packs / 10,010 questions |
| `exam_question_pool.questions` | 1,316 (unchanged since 09-19 — pools refresh in place, never created fresh) | 45,768 | 1,024 pools / 35,544 questions |
| `challenge_quiz_question_bank.question` | 31,795 | 31,795 | 31,308 rows |
| `generated_quizzes.questions` | 8 (unchanged) | 100 | 8 rows |
| **Total** | | **147,748** (vs. 115,333 on 09-19 — +28% over ~26 days) | |

**The `challenge_quiz_question_bank` "public note" figure is misleading on its own — corrected:** of the 31,308 rows tied to public notes, **31,288 (99.9%) belong to the owner's own admin/test account** (`dee4225c-…`, same account identified throughout the original incident). Real, non-owner Challenge Quiz activity is **507 rows across 32 distinct real users** — two orders of magnitude smaller than the raw count suggests. Report this split whenever this table's size is cited; the raw 31,795 overstates organic learner content by ~98%.

**Quick Review and Challenge Quiz draw from separate pools, confirmed both by code and data:** `QuickReviewSessionService` reads `StudyPackEntity.quiz` live (`study_pack_id` join, no caching) — confirmed at `QuickReviewSessionService.java:101-124`. `ChallengeQuizService` persists to and draws from `challenge_quiz_question_bank`, a separate per-user table. No shared pool between the two modes.

**`study_packs` — original generations vs. copies, the split that matters for both contamination and cost (§5):** of 14,017 packs, **11,783 (84%) are copies** (`notes.copied_from_note_id IS NOT NULL`) and **2,234 (16%) are original LLM generations.** Copies are byte-identical duplicates made at copy time (confirmed in the 2026-09-19 incident doc, Stage 3) — they never pass through generation-time validation (H4 or otherwise), because nothing generates them; they clone an existing row.

---

## §2 — Is new contamination still accumulating under H4/H5? **No evidence of that. All confirmed defects found this pass pre-date H4's 2026-09-22 deploy.**

**Caveat stated up front, per the methodology this whole incident chain uses:** the SQL re-scan below is **not** `QuizValidationUtils.isAnswerExplanationInternallyInconsistent` (H4's actual Java implementation) — it is a cruder approximation (no word boundaries, no LaTeX/rounding handling, strips all whitespace) built to match the documented detector *logic*, not H4's code. Every candidate below was independently re-derived from the question's own stated inputs, the same standard the original incident used — this is what makes the confirmed/false-positive calls trustworthy, not the SQL detector by itself.

**First pass scoped by `created_at > '2026-09-22'` was wrong and is explicitly withdrawn:** `study_packs.created_at` is the *copy's* timestamp, not the original generation's. A copy of year-old content made yesterday passes a `created_at`-only filter and looks like fresh contamination. Caught before this reached the finding — every candidate below was traced to its actual first-generation pack via `notes.copied_from_note_id`.

**Full current `study_packs` corpus re-scanned (not date-limited): 1,517 numeric-literal MCQs, 13 raw candidates.** Each manually re-derived from its own stated inputs:

| Question | Verdict | First-generation pack, created | Current copies |
|---|---|---|---|
| APGAR score needing resuscitation (choices 9/8/7/4, keyed "4") | **False positive** — 4 is the correct (lowest/most urgent) score; explanation states the general rule ("below 7") without restating "4", the same T.2 pattern the original audit catalogued | `37f82f86-…`, 2026-06-06 | 4 copies (09-15, 09-17, 09-28, 10-06) — all inherit the same correct key |
| PH percentage tax (gross sales 800,000) | **False positive** — 24,000 is correct (3% × 800,000), explanation agrees | `bf8c2e32-…`, 2026-06-16 | none found |
| Grain size range for sand | **False positive** — 0.075–4.75mm is correct, explanation agrees | `41c11e2f-…`, 2026-08-02 | none found |
| Audit detection risk (IR .4, CR .5, AR .06) | **CONFIRMED DEFECT** — DR = AR/(IR×CR) = 0.06/0.2 = **0.3** (idx0); keyed idx3 = "0.25". Explanation/workingSolution both independently state 0.3 and still key 0.25 | `b64de9fd-…`, **2026-06-16** | none found |
| Labor efficiency variance (120 vs 100 hrs, $15/hr) | **CONFIRMED DEFECT** — (120-100)×15 = **$300 unfavorable** (idx2); keyed idx0 = "$150 unfavorable" (wrong on both magnitude and the unfavorable/favorable pairing). Explanation/workingSolution both state $300 unfavorable | `3e671707-…`, **2026-06-16** | none found |
| Weighted average cost/unit ($10,000 / 500 units) | **CONFIRMED DEFECT** — 10,000/500 = **$20** (idx1); keyed idx2 = "$25". Explanation/workingSolution both state $20 | `95aab696-…`, **2026-09-14** | none found |
| Straight-line depreciation ($50,000 cost, $5,000 salvage, 9yr) | **CONFIRMED DEFECT** — (50,000-5,000)/9 = **$5,000** (idx0); keyed idx3 = "$4,000". Explanation/workingSolution both state $5,000 | `d83013bd-…`, **2026-08-18** | 2 copies: `5fe4e2be-…` (09-07), `deaa49eb-…` (10-04, a real learner's private copy) |

**Every genuinely confirmed defect pre-dates both H4 (2026-09-22) and the original incident's own 2026-09-19 scan.** `b64de9fd`/`3e671707` are from 2026-06-16, `d83013bd` from 2026-08-18, `95aab696` from 2026-09-14 — all existed before the original deterministic scan ran. **The likely explanation, not independently re-derived from logs but consistent with the original finding's own text:** the 2026-09-19 incident doc's §D table reports **17 raw `study_packs` candidates**, of which only **6 were manually reviewed** ("6/6 precision confirmed... all of which happen to be in `study_packs`" — §D). The later T-section "extended review" (2026-09-21) explicitly covers only `exam_question_pool` (31) and `challenge_quiz_question_bank` (40) — **`study_packs`'s remaining ~11 unreviewed candidates were never revisited by either pass.** These 4 defects are plausibly among that unreviewed set, not new H4 failures — this session did not have the original 71-candidate ID list to confirm the match exactly, so this is stated as the likely explanation, not a verified one.

**Real learner exposure, checked directly: zero sessions on any of the 6 defective packs.** `quick_review_sessions` lookup on all 6 pack ids returns exactly one row — `b64de9fd` (audit-risk pack), **1 incomplete session by the owner's own test account** (`dee4225c-…`), not a real learner. The depreciation defect's one real-learner copy (`deaa49eb`, owned by a real account, private note) has **not yet been quizzed on** — zero `quick_review_sessions` rows exist for it. This is a live latent risk (a real learner holds a copy with a wrong key) but not yet a realized one.

**Repair recommended** — see `docs/claude-plans/done/2026-10-15-study-packs-residual-answer-key-defects-repair.sql`. Same single-scalar-correction approach as the original incident's repair (not regeneration), made pack-agnostic (matches on `choices` + `correctIndex`, not hardcoded pack ids) per the lesson that new copies keep appearing after a scan is written.

**What this does NOT show:** no evidence either way on whether H4 itself has a bug — none of the confirmed defects were generated after H4 shipped, so H4 was never actually tested by this pass. If Feature Planner or Release Implementor wants that specific question answered, it needs either (a) the original 71-candidate ID list cross-referenced against these 4, or (b) a clean test: generate new content now and re-run this scan against only packs created today forward.

**H5's coverage-ratio fall (68.2%→61.4%) is the Feature Planner brief's own prior claim, not verified in this pass** — restated here only because the brief cites it; this session did not re-run that specific query. What this session's method *can* say: the coverage ratio only ever measures numeric-literal MCQs (≤4.7% of the corpus), so **H4 has zero evaluation of prose-answer MCQs by prompt design, not "near-zero"** — this was the original incident's own T.2/§I framing and still holds; nothing in this pass changes it either way.

---

## §3 — Other question-accuracy reports: none exist beyond the one original incident

`feedback` table: **3 rows, ever**, full history (not a sample):

| Created | Message | Status |
|---|---|---|
| 2026-05-18 | "Testing" (owner admin account) | NEW |
| 2026-07-27 | "Test" (owner admin account) | NEW |
| 2026-09-16 | The original quiz-answer-key incident report | NEW |

No other question-accuracy report, in English or otherwise, exists in this table. **`campaign_feedback_responses` (V148, the second feedback-shaped store found by grep) has 0 rows** — not a live channel. No Tagalog or other-language search was needed; the table is empty.

---

## §4 — Retention hypothesis: **the data cannot support exposed-vs-unexposed comparison (n=1), and that is the headline, not a caveat below it**

The 2026-09-19/21/22 incident established: of 30 total confirmed defects, **exactly one** had any real learner exposure (2 sessions, 1 learner, `68c5ff32-…`) — every other confirmed defect was either never served (`exam_question_pool`, empty `served_question_keys`) or on the owner's own test account (`challenge_quiz_question_bank`). **A statistical comparison between "exposed" and "unexposed" learner cohorts cannot be built from n=1.** State this to whoever is drawing conclusions from this brief, plainly, before any number below.

**That one learner, described without their email (user id only), for color, not as a statistic:** account created 2026-09-16T04:58:42Z (the same morning as the incident), `profile_type=BOARD_EXAM`. Did **155 Quick Review-family sessions across ~2.2 days** (through 2026-09-18T12:01), then **zero activity in the 27 days since** — including roughly 2 more days of activity *after* their defect exposure, so if anything their immediate post-exposure behavior was unchanged. This single account holds **150 of the corpus's 677 total `QUICK_REVIEW`-mode sessions (22%)** — a cram-and-vanish pattern common enough in this product's own prior retention work (see `ROADMAP.md`'s "Target-habit definition" row, exam-bound learners' expected signup→practice→exam→stop arc) that this session cannot distinguish it from ordinary behavior using this data. **This is not evidence the defect caused or didn't cause churn — there is no control group of size >0 to compare against.**

**General Quick Review baseline, reported both ways per the above:**

| | All users (n=119) | Excluding the one exposed learner (n=118) |
|---|---:|---:|
| Total `QUICK_REVIEW` sessions | 677 | 527 |
| Completed | 366 | 228 |
| Avg `retry_count` (completed) | 0.38 | 0.32 |
| Avg score % (completed) | 92.4 | 88.2 |
| Returned on a later calendar day | 13 (10.9%) | 12 (10.2%) |

**Two caveats on these numbers, both load-bearing:** `retry_count` is the automatic retry round the frontend triggers after ≥1 wrong answer in round 1 (confirmed in the 2026-09-19 incident doc, Stage 5) — it is **not** a voluntary "try again" action, and completed-session scores are measured **after** that automatic retry, which is why the corpus-wide average sits near 90%+ by construction, not because Quick Review is easy. **Return rate is right-censored** — a user who signed up last week has had less calendar time to "return" than one who signed up in June, so 10-11% is a lower bound on eventual return, not a stable rate. This session did not build a new return-rate metric for that reason; `ROADMAP.md`'s existing "Target-habit definition" backlog row already defines the correct exam-bound-vs-open-ended segmentation and an admin funnel exists for it — Feature Planner should use that rather than a fresh ad hoc definition from this read.

Other quiz modes, same corpus, for context: `CHALLENGE` 227 sessions/109 users/avg score 58.4%; `ADAPTIVE` 68/39/70.5%; `LONG_EXAM` 5/1/35.7% (effectively unused — confirmed by the original incident doc too: "there has never been a single `BOARD_EXAM` session in production").

---

## §5 — Real LLM cost/volume data, corrected for the copy/original split

**The headline fact for Feature Planner's model-migration comparison: both `model_tier=FREE` and `model_tier=PREMIUM` currently resolve to the same model in production — `gpt-4.1-mini-2025-04-14`.** The code's own configured default for `LLM_MODEL_PREMIUM` is `gpt-4.1` (`StudySnapProperties.java:51`, wired correctly at `OpenAiLlmStudyPackService.java:351`) — but every one of the 13,749 `PREMIUM`-tier `study_packs` rows was generated with the mini variant, identical to all 268 `FREE`-tier rows. This was read directly from `model_used`, not inferred from config — ground truth beats the default. **This session did not try to read the live Render env var value** (would require a different tool and risks exposing secrets in output; `model_used` already answers the question more reliably than an env var read would). Whatever the decommission notice covers, confirm which variant string it actually names before assuming PREMIUM-tier traffic needs migrating differently from FREE-tier.

**Real per-generation cost basis — original (non-copy) generations only, since copies consume zero new tokens:**

| | Original generations (2,234 packs) | Copies (11,783 packs) |
|---|---:|---:|
| Packs with token data | 2,233 (99.96%) | 3 (0.03% — negligible, likely a data artifact) |
| Total input tokens | 7,651,324 | 7,324 |
| Total output tokens | 2,202,179 | 2,653 |
| Avg input tokens/pack | 3,426 | n/a |
| Avg output tokens/pack | 986 | n/a |

**An earlier pass of this read averaged tokens across all 14,017 rows including copies, which dilutes the real per-generation cost by ~6x — that number is withdrawn; use the original-only figures above.**

**`estimated_cost` is NULL on every single row in `study_packs`** — the column exists (`V1__init.sql:48`) but nothing populates it; it is effectively dead. No dollar-cost ledger exists anywhere else either — `analytics_events` (checked: `id, user_id, event_type, entity_id, metadata_json, created_at`) has no cost-specific column, and this session did not find a per-event metadata convention that reliably carries it. **Feature Planner should compute dollar cost from the token totals above against current OpenAI pricing directly — there is no stored dollar figure to read.**

**`LLM_MODEL_CRITIQUE` usage was not separately measurable from this pass.** The only other token-tracking column set found (`quick_review_sessions.model_used`/`input_tokens`/`output_tokens`, added `V98`) shows **25 rows, model `gpt-4.1-mini-2025-04-14`, 73,447 input + 52,274 output tokens** — this is Challenge Quiz's progressive-generation top-up (`ChallengeQuizService.setInputTokens`, confirmed by code), not a critique/feedback feature. No dedicated Interview Practice or critique-model usage table was found in the time available; flagged as an open gap rather than reported as "zero usage."

**Volume trend:** 1,729 original+copy packs created in the last 7 days vs. 1,758 in the 7 days before that — roughly flat, not accelerating or decelerating.

---

## §6 — Open items, stated rather than silently dropped

1. Whether H4 (the real Java validator, not this session's SQL approximation) has ever actually failed on freshly-generated content remains untested by this pass — every confirmed defect found predates H4's deploy. Closing this needs either the original 71-candidate ID list or a forward-looking re-scan after this read.
2. The ~11 unreviewed original `study_packs` candidates from the 2026-09-19 scan were never positively identified by ID in this pass (only 4 were found via the current re-scan, which excludes anything already corrected or naturally aged out of the corpus by edits). If an exact reconciliation against the original 17-candidate list matters, that list needs to be re-pulled from that scan's own saved query, which this session did not have on hand.
3. `LLM_MODEL_CRITIQUE` usage/cost is unmeasured — no table found tracking it directly.
4. Dollar-denominated cost does not exist anywhere in this database; every cost figure anyone computes from this read is a token count times an assumed price, not a stored fact.
5. Whether the `PREMIUM`/`FREE` model convergence in production is intentional (owner cost decision) or a drifted config was not investigated — this session reports the observed fact only, per the read-only/no-config-read charter boundary.

---

## Queries

All queries were `SELECT` only, run via `mcp__render__query_render_postgres` against `notelib_db_prod` (`dpg-d6tvb8fkijhs73fda4m0-a`), plus `mcp__render__list_logs`/`get_metrics` for the merge-gate safety check and local code reads. No `EXPLAIN ANALYZE`, no `SET`, no env var or deploy calls were made.
