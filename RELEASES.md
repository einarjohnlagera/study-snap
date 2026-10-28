# RELEASES.md - NoteLib

## v0.177.0 - Challenge Quiz Quality, Continued

**Status: In Progress** (kicked off 2026-10-28 on `releases/v0.177.0`, cut from `releases/v0.176.0` at `5e2db468`, not from `main`: `v0.176.0` is Released on its branch, unmerged, deploy on HOLD; draft `DO NOT MERGE` PR #1538 keeps its diff against `main` visible, still open and draft when read 2026-10-28). **The theme name is proposed by the Release Implementor and the owner may rename it. It is deliberately model-neutral: this repo's docs are internal, but the name travels into the README baseline, the release notes and the PR title, and customer copy never names a model or implies a plan quality difference.**

Theme: close the Challenge Quiz quality gap the `v0.176.0` evaluation found on the free plan. On today's prompt Luna scored distractors 3.23 against Sol's 3.75, a giveaway estimate of 10.5% against 4.5%, and 5 confirmed critical defects of 300 against 0; the proposed wording lowered difficulty on Sol and so was not shipped. This release decides which lever to test, under the same blind pipeline, and changes the production prompt only if a new evaluation passes for BOTH Luna and Sol. Internal only: customer copy never names a model and implies no plan quality difference.

**Selection record.** The owner selected "Luna-first quality follow-up" as the theme on 2026-10-28 (answer to a scope question in the kickoff session). No plan file is marked `Status: ready for release`; the Backlog row and the report's section 6 list levers, not a contract. **Scope locked by the owner on 2026-10-28 (after the kickoff commit `3d70da87`, answer to a scope question): items 1 to 3, with item 4 gated on the evaluation result.**

**Deploy gate (carried unchanged):** on HOLD for deployment until the operational gate clears (four Postgres signal-9 kills after four deploys; first input is the 2026-11-01 cache read). Every `main` merge deploys. Nothing merges to `main` before the final deploy decision. **Re-read at kickoff (Render deploy list, 2026-10-28):** the newest deploy is still `v0.175.0`, live 2026-10-26T14:58:47Z; none since.

**Sources:**
- `docs/claude-plans/2026-10-27-challenge-quiz-quality-evaluation-report.md` (section 6, the levers), the pre-registration `docs/claude-plans/2026-10-27-challenge-quiz-quality-preregistration.md`, the frozen scorer instructions and the rubric (v3), all from `v0.176.0`.
- The `v0.176.0` harness (PRs #1536, #1537), reusable as is: alternate prompt directory, five generation arms plus an Official stock arm, blind export, leak check, per-arm prompt hash.
- `ROADMAP.md` Backlog row "Challenge Quiz Luna-first quality follow-up".
- `docs/claude-plans/2026-10-28-challenge-quiz-g5-reword-proposal.md` (item 1's options, awaiting the owner).

### Planned Scope

Items 1 to 3 are locked (owner, 2026-10-28); item 4 is gated. Status per item: `[queued | with-Codex | in-audit | merged-to-release | blocked: <reason>]`.

1. **Reword G5 and re-pre-register. Docs plus a small test-scope calculator change (Tier N, Codex). `[DECIDED 2026-10-28: Option A with a NOT EVALUATED disclosure, $4 cap (record `docs/claude-plans/2026-10-28-v0177-g5-decisions-product-ux.md`). v2 rubric drafted (`docs/claude-plans/2026-10-28-challenge-quiz-quality-scoring-rubric-v2.md`), blocked: owner signature on its G5 text, then the Codex brief for the calculator. Not yet with-Codex.]`** G5 ("no format below 3.5 with at least 20 items") was "harsh by construction" (evaluation report section 4) and is one of the three gates Sol failed. The reword needs the owner's sign-off before any paid run, and a new pre-registration is committed before the first paid command; the frozen `v0.176.0` rubric and scorer instructions stay as the record and are not edited.
2. **Giveaway-only prompt variant, without Change A. Test scope, Tier N (Codex). `[queued, blocked: exact wording goes to the owner before the brief]`** An alternate Challenge prompt file that carries only the giveaway fix (absolute words, parallel choices), so the arms can be attributed. The exact wording goes to the owner before the brief. The production file `challenge-quiz-developer.txt` is not touched by this item.
3. **Evaluation run. Owner-run, no agent paid call. `[queued, after items 1 and 2]`** Luna and Sol judged separately, same blind pipeline, owner-set generation cap (about $3 to $4 per rerun last time), gates re-registered in item 1. The scorer pipeline uses the harness's own validator.
4. **Production prompt change. Tier N, gated by item 3. `[NOT STARTED, gated]`** Only if the new evaluation passes for BOTH models and the owner gives final approval; otherwise HOLD, with no measurement-only deploy without separate owner approval.
5. **Backlog rows and checkpoints. Docs. `[done at kickoff, extended at signoff]`**

**Levers the report lists that this release's anti-drift EXCLUDES, so the owner is not offered them as scope:** routing free-plan Challenge to a stronger model (a pricing and entitlement change), and a Luna-specific verifier pass (the three verifier switches stay OFF). Either needs its own owner decision and release.

Anti-drift: no learner-facing difficulty selection; no change to the 10/12/15 question counts; no Phase 2 bank selection or Phase 3 question reporting; no mass regeneration or bank migration; Board Exam and Long Exam untouched (deferred by the owner, 2026-10-27); no pricing, quota or entitlement change; customer copy never names a model; no production prompt change before a new evaluation passes for both models and the owner approves; the +5 signed-prompt file, its byte-parity test and the three verifier switches untouched (switches stay OFF); no deployment until the operational gate clears; the `v0.176.0` frozen rubric, scorer instructions and pre-registration are never edited.

### Owner items carried at kickoff

- **Overdue `[CHECKPOINT]` rows, carried unread, not actioned at this kickoff** (same convention as the `v0.176.0` kickoff). Crude filter: a dated `[CHECKPOINT — due …]` title on or before 2026-10-28 with no `✅`, `READ`, `CLOSED`, `RESOLVED`, `VERDICT`, `DISCHARGED`, `SUPERSEDED` or strikethrough marker in its title or the first 400 characters of its Status. **12 rows, and the filter over- and under-counts (it reads titles, not verdicts), so none is claimed open or closed:** `v0.111.0` and `v0.96.0` (due 2026-09-28), the `v0.74.0` conversion read (09-30), the `v0.119.1` public-read row (10-04), `v0.107.0`, `v0.90.0`, `v0.106.0` and `v0.97.0` (10-13), the `v0.166.0` Hikari row (10-14), **RC-3's Xendit payment reminder (10-21, `v0.171.0`; an owner action, a real unpaid test checkout, with no instrumentation possible)**, the `v0.160.0` bulk-notification read (10-25) and `v0.92.0` (10-27). No `Last reviewed` was advanced on any of them. A real read of each is owed before any is called overdue-and-open.
- **Dated reads:** 2026-10-29 (set `CHALLENGE_QUIZ_STATEMENT_PAIR_LEGACY_CUTOFF`), 2026-11-01 (cache and kill read), 2026-11-03 (`v0.110.0` distal checkpoint), 2026-11-08 (`v0.174.0` model read, last day logs exist), 2026-11-09 (`v0.175.0` Challenge read).
- **Rotate the OpenAI key in `backend/.env`:** a line of it appeared in the 2026-10-27 session transcript.
- **Decide what happens to draft PR #1538** (close or merge) when this release's own PR goes in. A merge to `main` auto-deploys.
- **Do not run `./mvnw clean` while the `v0.176.0` evaluation data is in `backend/target/llm-benchmark`** (backup at `~/quality-eval-v0176-backup/`).

### Shipped

_(nothing yet)_

## v0.176.0 - Challenge Quiz Quality

**Status: Released** (kicked off 2026-10-27 on `releases/v0.176.0`, cut from `main` at `06a09f7c` after `v0.175.0` merged as #1533; **closed 2026-10-28 on its branch, NOT merged to `main`, deploy on HOLD**; the evaluation outcome was HOLD, so this release changes no production behavior).

Theme: Challenge Quiz should test meaningful understanding with accurate answers and believable distractors, without a difficulty
setting or any new learner flow. A learner's feedback ("Distractors are easily spotted", "Too easy", 2026-10-23) led to a read-only audit
that found the Challenge prompt carries no distractor rule and a bare, undefined difficulty word. This release improves **newly
generated** questions, and only if an owner-run evaluation proves it safe.

**⚠️ DEPLOY GATE:** this release is **on HOLD for deployment** until the operational gate below clears. Every `main` merge deploys, so
nothing merges to `main` before the final deploy decision; the evaluation harness runs from this branch. A failed, incomplete or
inconclusive evaluation means HOLD, with **no measurement-only deploy without separate owner approval** (Product UX, 2026-10-27).

**Sources:**
- Feature Planner's audit `docs/claude-findings/2026-10-26-challenge-quiz-quality-audit-distractors-and-difficulty.md` and Phase 1
  proposal `docs/claude-plans/2026-10-26-challenge-quiz-quality-phase1-proposal.md` (both uncommitted in the shared checkout at kickoff; the
  owner commits them from Feature Planner's session), which record the owner's 2026-10-27 approval of the exact prompt wording (Changes A, B, C),
  the evaluation with a $4 generation cap run by the owner, and the reframed correctness criterion. I have the record, not the owner's own words.
- This release's scope `docs/claude-plans/2026-10-26-challenge-quiz-quality-scope.md`, with Product UX's final review applied (its sections 7 and 8).
- The deferred Board Exam / Long Exam follow-up scope `docs/claude-plans/2026-10-26-board-long-exam-follow-up-scope.md` (owner deferral,
  2026-10-27: no paying learners yet).

### Planned Scope

1. **Evaluation harness (test scope). Tier N. `[✅ SHIPPED to the release branch: PR #1536 (harness), PR #1537 (batch builder follows the rubric for an invalid primary row)]`** Extends the existing Leg E (cell `S15`) with a quality mode: an alternate Challenge
   prompt file (`challengeResources` replaces `challengeQuizDeveloperPromptTemplate`), five generation arms (current prompt on `gpt-4.1-mini`, Luna, Sol;
   proposed prompt on Luna and Sol) plus a stock arm of about 100 Official template questions, with blind export, a leak check and a per-arm
   prompt hash. Official notes only. No production code.
2. **Evaluation run. Owner-run, no agent paid call. `[✅ DONE 2026-10-27, outcome HOLD: Sol fails G2, G3 and G5; see `docs/claude-plans/2026-10-27-challenge-quiz-quality-evaluation-report.md`]`** **20 Official notes, 10 `COLLEGE` + 10 `BOARD_EXAM_REVIEW` by each note's own level** (owner decision, design A,
   2026-10-27; each level at least 4 strata and 2 quantitative notes, overall at least 6 and 4), with template stock, all `hard` (cell `S15`),
   one fresh 15-question start per note per arm, bank off, **$4 generation cap**. Blind Codex scoring (D11), independent Opus review of a stratified
   sample including unflagged items against the source notes, disputes adjudicated. **Finalized gates (Luna and Sol judged separately, both must
   pass):** G1 correctness (no more confirmed critical defects than the same-model current arm, zero attributable to the new wording; owner-approved);
   G2 distractor plausibility at least 4.0 and at least +0.3; G3 mode alignment at least 4.0 of 5 (cognitive demand reported separately, no multi-step
   requirement); G4 independently confirmed serious giveaways at most 5% of reviewed choice-based items with no recurring pattern (mechanical indicators
   diagnostic only); G5 no format below 3.5 with at least 20 items; G6 collateral (clarity and faithfulness within 0.2, delivered count and retry rate
   within 2 points, latency and tokens within 15%); G7 MULTI_SELECT cardinality and scoring unchanged; G8 inconclusive counts as not passing (a stratum
   with fewer than 150 reviewed choice-based items is inconclusive). Feasibility is stated before the run: about 200 choice-based items per arm cannot
   prove 5%, so G4 is applied to the observed rate with the interval reported.
3. **Prompt change. Tier N code, gated by item 2. `[NOT SHIPPED: the gate did not pass (HOLD); the production prompt is unchanged]`** The single file `challenge-quiz-developer.txt` (Changes A, B, C as approved);
   the Java `{DIFFICULTY}` substitution stays; the 10/12/15 counts are untouched. **The one-sentence reword in
   `challenge-more-no-statement-pair-developer.txt` is DEFERRED** (it is pinned byte for byte to the owner-signed wording document by
   `StatementPairProductionParityTest`, and the +5 exclusion switch is OFF, so the file is never sent). Tests: a prompt-contract test pinning the
   MULTI_SELECT cardinality sentence, and a test that a normal +5 still uses the updated Challenge template. Update
   `docs/features/challenge-quiz.md:238` and the "Balancing difficulty…" loading line (`quiz-generation-overlay.tsx:9`) under `VOICE_AND_TONE.md`.
4. **Measurement log. Tier N. `[NOT BUILT: HOLD allows no measurement-only deploy without separate owner approval; awaiting the owner's decision]`** Extend `challenge_quiz_delivery_counts` with prior bank outcomes at delivery time (separate from the current
   attempt), actual delivered counts, fresh versus bank or template reuse, and the model, with **unknown provenance kept as unknown**; no question text,
   private-note content or unnecessary learner identifiers; a failure to log never affects correctness, availability or quota; a real-database or
   log-contract test. It also makes the `model_used` read in the `[CHECKPOINT — due 2026-11-09]` row readable.
5. **Backlog rows and checkpoints. Docs. `[done at kickoff, extended at signoff]`**

**Owner decisions and corrections recorded 2026-10-27 (evaluation design).**
- **Design A (owner):** 10 `COLLEGE` + 10 `BOARD_EXAM_REVIEW` notes by each note's own level; the difficulty stays `hard`. Gates are read **overall**; per-level results are
  descriptive only (10 notes each).
- **Why:** the prompt's `{LEARNER_LEVEL_GUIDANCE}` comes from `StudyPackGenerationContextResolver.effectiveCurriculumLevel`, which uses the **note's own level first**, so a viewer-level pin
  only affects notes with no level. For `BOARD_EXAM_REVIEW` the guidance already says "plausible distractors, multi-step thinking". Real completed Challenge sessions run at
  `BOARD_EXAM_REVIEW` for 128 of 215 (about 59%); the Official eligible pool is 875 `COLLEGE`, 447 `BOARD_EXAM_REVIEW`, 207 with no level.
- **Correction:** Official template rows carry a **NULL** `generation_stamp` and are claimable (`findClaimableForUpdate`); an early draft of the selection filtered on the pack's current stamp and found only 33
  notes in 4 strata. The corrected pool is 1,529 notes in 41 strata, and the per-level selection validated read-only against production (counts only): 10 + 10 notes, 12 strata, 4 quantitative.
- **The drift guard must be retired or updated by item 3.** `ChallengeQualityPromptGuardTest` pins production's current prompt hash, so the prompt-change commit breaks it by design.
- **Spend stops (README run sheet):** smoke 0.33 + full 3.28 = 3.61, worst case about 3.95 under the $4 cap; the Sol full stops are 1.40 against an expected spend of about $1.20 per Sol arm.
- **Scoring rubric v3 (SIGNED and frozen 2026-10-27, with its pre-registration committed before any paid call):** `docs/claude-plans/2026-10-27-challenge-quiz-quality-scoring-rubric.md`. Product UX's three decisions and four sign-offs of 2026-10-27 (relayed by the owner) are in it: grounding is
  scored against the generator's inputs (summary and key concepts) with the full note as a consistency safeguard; **G4 decides on a sampling-adjusted estimate** (at most 5%, 90% one-sided bound, `UL` at most 10% as a tolerance,
  at least 60 randomly sampled unflagged items per arm stratified across notes and formats, 3-items-across-2-notes pattern rule, `N` at least 150); a **missed critical defect in a proposed arm triggers a whole-arm independent
  re-review**; the **wording-sensitive regression rule** (excess over the same-model current arm for four defect types; a mechanical screen, not proof of causation); **G7 tolerance 10 percentage points**, absolute. Denominators and
  missing-data handling were written by the Release Implementor and are not separately signed.
- **Done before the paid run (kept for the record):** the owner signed and froze the rubric (its SHA-256, the 20 pack ids, the SHA-256 of `quality-e.jsonl`, the review-order seed and the gate calculator's commit SHA are committed to a
  pre-registration file on this branch before the first paid command; the review-order manifest hash follows as an addendum after generation and before scoring).

**Operational deployment gate (Product UX, 2026-10-27):** the four Postgres signal-9 kills that followed four deploys are investigated before any
production approval: cause and current database health (the `[CHECKPOINT — due cache deploy + 7 days]` read of 2026-11-01 is the first input),
capacity and pre-deploy health checks, a documented rollback and recovery, and post-deploy checks of the core quiz and study flows. A single deploy
reduces exposure but does not resolve the risk; **unexplained with no credible recovery plan means HOLD.**

**Not in this release:** a learner-facing difficulty selector; any change to the 10/12/15 counts; Phase 2 outcome-aware bank selection and Phase 3
per-question reporting (indexed as Backlog rows); a difficulty-aware bank (it needs a migration); mass regeneration; Board Exam and Long Exam; pricing,
quota or entitlement; the +5 reword; retiring `resolveGenerationProfile` or the stored difficulty label.

**Verification tier:** about four code and test items, and the risky one (the prompt) is gated by the evaluation rather than by code review.
`advisor()` before the harness brief and on the diff, a real-PostgreSQL or log-contract test for item 4, and a scoped cold agent only if item 3 or 4
touches a shared method beyond the log line.

**Kickoff scan (2026-10-27):**
- **Docs carried in.** The diffs of PR #1534 (dated checkpoints) and PR #1535 (deploy state of `v0.173.0` to `v0.175.0`) were applied inside this
  kickoff commit, as the owner chose (no extra deploys). Their PRs are closed with a note, unmerged.
- **Production facts re-read before carrying them in:** no Render deploy since `v0.175.0` (2026-10-26T14:56 to 14:58Z) and one Postgres signal-9 kill
  since (14:58:07Z, during that deploy), so "4 of 4 deploys followed by a kill" stands.
- `v0.170.0` was archived from `RELEASES.md` and the ROADMAP baseline (verified verbatim at the end of each archive); the `RELEASES_ARCHIVE.md`
  header now also names `v0.169.0` (moved at the `v0.175.0` kickoff, omitted from it then).
- **Planning files with no Backlog row:** 7 `v0.175.0` files are now indexed in its Backlog row (two are one-off Product UX consultation prompts, the
  release-artifact exemption). The Challenge quality files and the deferred Board/Long Exam scope each have a row.
- **Checkpoint scan, carried unread, not actioned at this kickoff.** Crude filter (a dated `[CHECKPOINT — due …]` title in the past, with no
  `✅`, `READ`, `CLOSED` or similar marker in its title or the first 400 characters of its Status): **10 of 36 past-due rows** (`v0.111.0` and
  `v0.96.0` of 2026-09-28; the public-read pool row of 10-07; `v0.106.0`, `v0.107.0`, `v0.90.0`, `v0.97.0` of 10-13; `v0.166.0` Hikari of 10-14;
  RC-3's reminder of 10-21; the bulk-notification read of 10-25), plus five rows due today (`v0.91.0` to `v0.95.0`). The filter overcounts. Rows
  due inside this release are `[CHECKPOINT — due 2026-10-29]` (the cutoff variable, reported set by the owner), the cache read of 2026-11-01, and
  the `v0.174.0` and `v0.175.0` reads of 2026-11-08 and 2026-11-09.

Anti-drift: no learner-facing difficulty selection; no change to the 10/12/15 question counts; no Phase 2 or Phase 3 implementation; no mass
regeneration or bank migration; Board Exam and Long Exam untouched; no pricing, quota or entitlement change; customer copy never names a model and
implies no plan quality difference; **no production prompt change before the evaluation passes for both Luna and Sol and the owner gives final
approval**; the +5 signed-prompt file, its parity test and the three verifier switches (all OFF) are untouched; **no deployment is authorized by any
review so far**; feature PRs never edit this file.

### Shipped

- **Evaluation harness and method (test scope, no production code).** `QualityEvaluation*`, the blind export, the review plan, the review-batch builder and the offline gate report under `backend/src/test/java/com/studysnap/backend/benchmark/` (PR #1536; PR #1537 fixed the batch builder so an invalid primary row marks only that arm incompletely scored). Rubric signed and pre-registered, with Amendments 1 to 4 and Addenda A to D recording every deviation.
- **Evaluation result (2026-10-27): HOLD.** 20 Official notes, five generation arms plus stock, 1,600 blind questions, Codex primary scoring, 497 items independently re-scored by cold Opus reviewers, 144 disagreements adjudicated; about $3.03 spent of the $4 cap. **Sol, proposed against current prompt: G1 PASS, G2 FAIL (distractors 3.59 against 3.75), G3 FAIL (mode alignment 3.81 against 4.23), G4 PASS, G5 FAIL, G6 to G8 PASS.** The proposed wording reduced giveaways but made questions easier. Both models must pass, so HOLD; Luna's proposed arm was deliberately not interpreted (a whole-arm re-review could not change the outcome). **The prompt change (item 3) is not shipped and the production prompt is unchanged.**
- **Product finding for the owner:** on today's prompt Luna (free plan) scored distractors 3.23, mode alignment 3.36, a 10.5% giveaway estimate and 5 confirmed critical defects, against Sol's 3.75, 4.23, 4.5% and 0 (internal only; customer copy still never names a model). Full report: `docs/claude-plans/2026-10-27-challenge-quiz-quality-evaluation-report.md`.
- **Not shipped:** item 3 (prompt change, gate failed), item 4 (measurement log, awaiting the owner's decision), and no deploy of any kind.

### Signoff record (2026-10-28)

- **Scope completeness.** Item 1 shipped: the harness (`backend/src/test/java/com/studysnap/backend/benchmark/`, PR #1536) and the batch-builder fix (`QualityReviewBatches.java`, PR #1537). Item 2 done: pre-registration `docs/claude-plans/2026-10-27-challenge-quiz-quality-preregistration.md` and gate report `gates.json` (SHA-256 `fc6875e7…`), outcome HOLD. Item 3 **not shipped**: the gate failed; it lives on as the candidate row "Challenge Quiz Luna-first quality follow-up" in the Backlog. Item 4 **not built**: HOLD allows no measurement-only deploy without separate owner approval (owner chose to leave it unbuilt, 2026-10-28). Item 5 done.
- **Feature-doc drift.** No feature doc was touched and no behavior changed (`git diff origin/main...HEAD` shows nothing under `backend/src/main` or any frontend source), so there is nothing to re-read; `docs/features/challenge-quiz.md:238` is unchanged because the prompt change did not ship.
- **Backlog closure.** The Challenge quality row is marked EVALUATED: HOLD with the evidence; a new candidate row indexes the follow-up; Phase 2, Phase 3 and the Board/Long Exam deferral rows are unchanged.
- **Checkpoint gate.** Nothing shipped ahead of its own evidence (no production change), so no new `[CHECKPOINT]` row is owed. Existing checkpoints are unchanged (2026-10-29 cutoff variable, 2026-11-01 cache and kill read, 2026-11-08 and 2026-11-09 model reads).
- **Pre-signoff check.** The "delivery introduced a defect the same session then fixed" trigger fired (the batch-builder abort and a wrong "all rows valid" claim, both corrected in the pre-registration). One scoped cold claim-checker (Sonnet) tried to falsify 8 claims of the evaluation report against the real files: **all 8 held**; it found four wording imprecisions in the report, all corrected. Mutation checks were run on the late harness rounds (round 5: 7 mutants, round 6: 5 mutants) and every mutant was killed by a named test.
- **Known limitations, carried.** 20 notes and one start per arm; model reviewers only, no human review; reviewer isolation by scratch directory and self-report, weaker than the command-transcript audit used for the primary pass; G5 is harsh by construction and should be reworded before a rerun; the test cannot attribute the drop to Change A, B or C; Luna's proposed arm (B2) was deliberately not interpreted and the 300-item whole-arm re-review was not run (it could not change the outcome); the `gpt-4.1-mini` arm is incompletely scored (2 rows) and is no gate's arm; the orchestrator once printed Luna's failed-gate list it had said would stay uninterpreted; **the 20% missing-telemetry threshold is hard-coded (`>.20`) in `QualityGateReport.java:563` and not a named constant in `QualityGateParameters`** as the Round 3 acceptance criteria required (it matches the signed value and changed no verdict).
- **Handoff.** `releases/v0.176.0` is closed and **not merged to `main`** (no production change; the deploy gate and the unexplained Postgres kills stand). **A held draft PR keeps the diff visible: #1538 (`DO NOT MERGE`, draft so it cannot be merged by accident; mergeable against `main` at 2026-10-28).** The next release is cut from this branch, as `v0.175.0` was cut from `v0.174.0`. Private evaluation data stays git-ignored under `backend/target/llm-benchmark/` with a backup at `~/quality-eval-v0176-backup/`; **do not run `./mvnw clean` until it is archived.**

## v0.175.0 - Quiz Ready

**Status: Released and deployed** (kicked off 2026-10-20, signed off 2026-10-25 on `releases/v0.175.0`, **cut from `releases/v0.174.0`
(`e539b045`)**, the same stacking pattern `v0.174.0` used on `v0.173.0`. **Merged to `main` as #1533 (`06a09f7c`) on 2026-10-26;
Render live at 14:58:47Z, Vercel at 15:02:51Z.** `v0.174.0` had been squash-merged to `main` with a tree identical to `e539b045`, so #1533 carried a
no-op `ours` merge (`bff16fbd`) to clear the conflict.
Anything that changes on `releases/v0.174.0` after this cut is merged forward.)

Theme: a learner who starts a Challenge Quiz is never stuck watching a spinner or racing a timer that
started without them. The quiz is prepared in the background, waits on a "Your quiz is ready!" screen, and
starts only when they press Start Quiz. Challenge also moves to each plan's model, behind a guard that
catches wrong answer keys.

**MERGE GATE (discharged 2026-10-26):** this release's PR merged to `main` only after `v0.174.0`'s PR #1512 (2026-10-25), which in turn
followed `v0.173.0`'s #1505 (2026-10-25). The owner overrode #1512's "wait for the cache read" hold on 2026-10-25; the cost is attribution,
recorded in the cache checkpoint row in `ROADMAP.md`.

**Sources, all owner-approved:**
- plan: `docs/claude-plans/2026-10-19-v0175-challenge-quiz-preparation-plan.md` (Feature Planner; §1
  journey, §5 lifecycle, §7 retry, §8 acceptance criteria, §9 items 1–17);
- decisions: B1–B7 (`docs/claude-plans/2026-10-19-owner-decisions-v0175-b1-b7.md`), and C1–C8 plus the C7
  follow-ups R1–R3 (consolidated plan, Part G);
- R1 evidence: `docs/claude-findings/2026-10-19-v0175-c7-statement-pair-answer-key-comparison.md` and
  `docs/claude-findings/2026-10-19-v0175-r2-statement-pair-answer-key-scan.md`.
  - 370 of 1,389 Official template statement-pair items (26.6%) had a wrong key on today's model.
  - The owner repaired them on 2026-10-20.
- **Plan §10's five open decisions are all closed:** C1 (no "Stay" button), C6 (Board/Long Exam follow-up
  release), C3 (collision asks), C4 (refund) and C5 (+5 inherits Sol).

### Planned Scope

Order matters: item 1 ships before item 6. Items 2–4 form one learner journey and are briefed together, but
delivered as separate PRs where the seams allow.

1. **R1: statement-pair answer-key guard. Tier N, raised to one cold falsification review by the start minimum. FIRST. `[shipped: #1513, verifier #1514 and #1520, switches OFF]`** PR #1513, merged 2026-10-20 (`7b9ae007`). Wording signed off 2026-10-20: `docs/claude-plans/2026-10-20-v0175-r1-statement-pair-guard-wording.md`. **Acceptance run (mini, 2026-10-20, about $0.18): the smoke-test rule FAILED.** 3 of 32 delivered statement-pair keys were wrong (2 clear, 1 arguable). All three are factual-verdict errors, which the check cannot catch; none is the index class R1 targets. Findings: `docs/claude-findings/2026-10-20-v0175-r1-acceptance-run.md`. **Luna arm (2026-10-20, about $0.10): the smoke-test rule PASSED.** 31 of 31 statement-pair keys agreed in the blind check, 370 of 370 questions were delivered, and there were zero rejections. The pre-R1 Luna rate was about 19%. Luna starts take a median of 45 s and up to 69 s, versus mini's 15 s, so item 6 stays behind item 2. **Sol arm (2026-10-20, cap raised to about $1.75, meter $1.45): the smoke-test rule PASSED.** 28 of 28 statement-pair keys agreed in the blind check, and 348 of 348 questions were delivered on the 28 completed starts (1 network timeout, 1 spend-stopped). Sol start p50 is 55–105 s, max 150 s. Mini's factual-verdict errors stay live on multi-note Challenge until item 5. **Owner direction, 2026-10-20: Option 3 (an independent statement-pair verifier) is approved for investigation only, as a v0.175.0 candidate subject to a separate go/no-go** (`docs/claude-plans/2026-10-20-owner-direction-v0175-option3-verifier-investigation.md`). Proposal: `docs/claude-plans/2026-10-20-v0175-statement-pair-verifier-investigation.md`. **Owner decisions, 2026-10-20** (`docs/claude-plans/2026-10-20-owner-decisions-v0175-statement-pair-verifier.md`): Option F (Sol and Luna check every statement-pair item on every plan), conditional on validation; +5 batches exclude statement-pair items by a prompt rule (wording pending sign-off), with skip-and-log as the fallback; a rejected item may be replaced by another question type; no new provider in v0.175.0; a read-only recheck of the about 890 first-pass-only Official stock items after validation. **The verifier is never a prerequisite for background preparation** (Product UX): it ships with every switch OFF, and live-start verification is enabled only after items 2 and 5 are validated. **Follow-up decisions, 2026-10-20** (`docs/claude-plans/2026-10-20-owner-decisions-v0175-verifier-follow-up.md`): a local, non-production test harness is approved (test scope only, no paid calls); the exact +5 rule, replacement message, verifier prompt and schema, and the two-stage pass rules are drafted for owner sign-off in `docs/claude-plans/2026-10-20-v0175-statement-pair-verifier-wording-and-pass-rules.md`. **Wording and pass rules signed off, 2026-10-20** (`docs/claude-plans/2026-10-20-owner-decisions-v0175-verifier-wording-and-pass-rules.md`), with clarifications incorporated into the exact text: rejected statements are untrusted; SILENT stays visible; a note that conflicts with established knowledge gives UNCERTAIN; only genuine, applicable exceptions count for absolute words; r1-04 strict; a gray zone returns for review; a wrong key among accepted items triggers a rollout review. **The local verifier harness merged** as PR #1514 (`944cbfdb`), test scope only, after the owner's merge conditions were confirmed. **Paid Stage 1 ran 2026-10-20 (owner-run, meter $1.08): KILL on S-A4b**, with F-union false rejection at 10/78 against a kill line above 9. Detection was perfect: mini's three errors 3/3 each, 20/20 constructed, 20/20 unseen, no joint miss, 6/6 ambiguous. Analysis: 5 rejections were Luna evidence-quote artifacts, where the verdicts were correct. A blind Codex adjudication (D11) sided with the checkers on the other 5, all mini-generated, which suggests the test-set labels were wrong there and mini's R1 rate may be about 8/32, not 3/32. That is unconfirmed: all three judges are OpenAI-family. Revised proposal (gold correction, quote-rule fix, fresh re-run at a $2.20 cap) awaits the owner: `docs/claude-plans/2026-10-20-v0175-statement-pair-verifier-wording-and-pass-rules.md` Part 10. No threshold was relaxed; nothing is in production code; Stage 2 stays gated. **Owner approved all four items, with an Opus tie-break first (2026-10-20).** Only r1-30 was relabeled; Opus sided with the original label on r1-02, r1-08 and r1-23 and called r1-05 ambiguous, so mini's R1 count is 4/32, not 8. Corrected gold: N 77 plus 1 adjudicated. Quote rule and scorer updated (test scope, branch `feat/v0.175.0-verifier-rerun`). Re-run cap $2.20, commands in Part 11 of the wording doc. **Re-run (owner-run, meter $1.15): Stage 1 PASS as a smoke test.** Every criterion passes; per-checker false rejection 3/77 each is exactly at the limit, F union 4/77, joint miss 0/40, all positives caught. Verifier evaluation total $2.23. Next, each separately gated: the production implementation, activation for Official question-set creation, the read-only stock recheck, and Stage 2.
   - **Owner additions (2026-10-20):** a start minimum (single-note at most one short; multi-note fails at zero), with a failed start charging no quota and releasing its claims. Multi-note underfill (1–17 of 18) stays a high-priority issue for items 2 and 5.
   - Generation records a true/false verdict for each statement in a "Statement 1 / Statement 2" item.
   - The validator checks the keyed choice against those verdicts and retries on a mismatch.
   - An explanation-only check is not enough: R2 measured about 40% recall.
   - **Scope: the Challenge generation path only, on every model, including today's pinned mini.**
     Challenge start (single and multi-note, `ChallengeQuizService.generateChallengeQuizForSources:1832`),
     +5 (`OpenAiLlmStudyPackService.generateMoreChallengeQuiz:2031`) and the Official template seed
     (`OfficialChallengeQuizTemplateService:301`) all go through `generateChallengeQuizWithSchema`
     (`OpenAiLlmStudyPackService.java:2305`).
   - **Board Exam, Long Exam, Adaptive, teacher quiz and Study Pack generation share the prompt rule but are
     NOT in scope.** Board and Long Exam are untouched in this release (C6), and R2 found no defect in Study
     Pack quizzes. Extending the guard to them is a separate owner question.
   - **It changes the prompt and the response schema, so the exact wording goes to the owner before the
     brief.**
2. **Preparation state machine (backend). Tier H. `[shipped: #1518, #1519]`** Plan items 2–4 and 9–11.
   - Single-note async dispatch.
   - A ready-not-started state: plan §5 leans to a flag on `IN_PROGRESS`, to keep the partial unique index.
   - `markSessionReady` is split from an explicit **Start Quiz** action, **for Challenge sessions only**.
     There the timer is stamped only on Start Quiz.
   - **Board Exam's callers keep "ready" and "started" as one step until the C6 follow-up** (the pooled
     path, `ChallengeQuizService:338`, and `generateBoardExamAsync`, `:638`). An unstamped Board Exam never expires (`isSessionExpired`,
     `:1489-1496`), and it shares `session_mode='CHALLENGE'`, so it would block Challenge requests on that
     note.
   - **Quota is charged only when Start Quiz succeeds** (owner decision 2026-10-20, superseding C4's
     charge-at-creation; `docs/claude-plans/2026-10-20-owner-decisions-v0175-analytics-and-quota-at-start.md`).
     Preparation, failure, discard and expiry never consume quota, so no refund path is needed. Prepared,
     unstarted sessions are `quotaExempt` until Start.
   - `CHALLENGE_QUIZ_STARTED` fires once, at a successful Start Quiz (owner decision 1, same file).
     Preparation-requested, ready and abandoned events are new.
   - Retry once, only after a lock-and-recheck (`generateBoardExamAsync:626-633` pattern). **No Luna fallback
     for Plus/Pro.**
   - Recovery-sweep entries for stuck generating and ready sessions.
   - **A 7-day expiry sweep** returns the questions to the bank (B3).
   - **A collision with a ready session asks the learner (C3).**
   - **Bank-served starts and redo-missed land on the pre-screen (C2).**
3. **Preparation screens (frontend). Tier N. `[shipped: #1521]`** Plan items 12–13.
   - "Getting your quiz ready" shows progress and **Continue reviewing** only, with no "Stay" button (C1).
   - "Your quiz is ready!" has **Start Quiz**.
   - Failure recovery offers Try Again and Continue Reviewing; the expired state offers Prepare New Quiz;
     and there is the collision prompt.
   - B4 copy. No model names.
4. **Readiness notification and deep link. Tier N. `[shipped: #1522]`** Plan items 14–15.
   - One in-app `ASYNC_RESULT` notification through `NotificationService.deliver`, deduplicated per
     session, sent for any way of leaving (B2).
   - It is marked read if the learner stayed and saw the pre-screen.
   - The deep link handles four cases: ready, already started, expired or forfeited, and not owned.
   - A real-request test is owed for any new endpoint.
5. **Multi-note preparation. Tier H. `[shipped: #1523, #1525]`** Plan items 5–8.
   - Async dispatch through the same journey.
   - Per-source calls run in parallel on `llmParallelTaskExecutor` (measured 4–5× faster).
   - A per-source failure no longer fails the start: it continues if enough questions survive (the floor).
   - Token usage is recorded.
   - **Split into 5a and 5b (2026-10-21).**
     - **5a** (PR #1523, merged) is the shared claim and release surface: claims are released per session, and
       legacy statement-pair rows are excluded.
     - **5b** is multi-note background preparation, plus Feature Planner's Part A (each source's own bank,
       A.1 and A.2). Plan: `docs/claude-plans/2026-10-21-v0175-multinote-bank-and-start-while-generating-plan.md`.
   - **Owner decisions, 2026-10-21:** `docs/claude-plans/2026-10-21-owner-decisions-v0175-open-questions-and-priorities.md`.
     - **Q1, the multi-note floor:** at least 2/3 of the requested questions **and** at least 2/3 of the notes
       with a positive allocation, both rounded up. A shorter ready quiz discloses "Your quiz is ready with X
       of Y questions."
     - **Q2:** the +5 exclusion rule sits behind its own switch, OFF.
     - **Q3:** the replacement instruction is to be revised; the checker stays OFF.
     - **Q4:** the copy is approved with edits (PR #1524).
     - **Q5:** +5 direction is set (no silent downgrade; fix timer fairness first).
     - **Final product priorities:** accuracy, correctness, reliability, smooth flow, fairness.
6. **Challenge tier routing. Tier N. `[shipped: #1526, with background +5]`** Plan item 17.
   - **Gated on item 2 as well as item 1.** Routing Plus/Pro to Sol before the preparation flow exists
     brings back the blocking start of about 107 s, which is why v0.174.0 pinned Challenge.
   - **Multi-note starts stay pinned until item 5 ships.** Sequential Sol generation takes about 3.5–4
     minutes. If Sol's benchmark rate of about 1 timeout in 40 calls holds per source call, a 6-note start
     fails roughly 1 time in 7.
   - Challenge start, +5 and multi-note move from `LLM_MODEL_INTERACTIVE_QUIZ` to the `TIERED` route:
     FREE → `LLM_MODEL_FREE`; Plus/Pro and ADMIN → `LLM_MODEL_PREMIUM`.
   - **+5 inherits (C5).**
   - `model_used` is recorded on the session for the checkpoint read.
   - Live models still depend on the owner's v0.174.0 Render switch.

**Not in this release:**
- Board Exam / Long Exam "press Start first" plus their expiry sweep: their own follow-up release right
  after this one (C6).
- The B6 targeted template backfill: a separate small PR after the v0.174.0 switch (C8).
- Low reasoning effort (B7, not adopted).
- A scan of learner-original statement-pair content.
- The R3 impairment-note correction (Note Strategist and owner).

**Verification tier: full Tier H.**
- It touches quota semantics (a charge moves to creation, plus a new refund), adds a new session state and
  a concurrency surface (the sweep against Start, the retry lock), and two or more items touch
  `ChallengeQuizService`'s session lifecycle.
- **Six items is above the three-to-four-item size guidance.** Expect the full pre-signoff pressure test,
  not a single scoped agent.
- If the owner wants a smaller first release, the clean seam is: items 1–4 plus 6 here, with item 5
  (multi-note) as its own release. **Item 6 then routes single-note starts and +5 only, and multi-note
  stays pinned until item 5 ships.**

**Kickoff scan (2026-10-20):**
- **14 live planning files had no Backlog row.** They are the `v0.174.0`/`v0.175.0` package: benchmark,
  latency, Product UX, the A1–A7 decisions, routing plans, the B6/B7 proposals and the four R2 `.sql`
  handoffs. Each is now named in the `v0.174.0` or `v0.175.0` Backlog row. None was moved to `done/`.
- The `v0.175.0` row's title still described the removed "Stay on this page" button. Corrected (C1).
- **Overdue `[CHECKPOINT]` rows are carried unread, not actioned at this kickoff.**
  - A crude filter (a Gate cell with a past due date and no "closed" or "✅" in the first 300 characters
    of Status) returns 39 rows. That overcounts, because several are struck through or resolved in their
    title. `v0.174.0`'s kickoff recorded 16.
  - Due inside this release: RC-3's reminder timing (2026-10-21), the `v0.166.0` Hikari backstop
    (2026-10-21), and `v0.173.0`'s signal-9 read (2026-10-22), which gates #1505 and so this release's
    merge chain.
- `v0.169.0` was archived from `RELEASES.md` and the ROADMAP baseline. Both files were rebuilt from the
  archives and compared byte-for-byte against the pre-move copies: identical.

Anti-drift: no pricing, entitlement or quota-limit change. **The only quota change is charging Challenge
when Start Quiz succeeds, never during preparation** (owner, 2026-10-20, superseding the charge-at-creation
plan);
customer copy never names a model and implies no FREE/paid quality difference; no email or push; **no timer
starts before Start Quiz**; no Luna fallback for Plus/Pro; Board/Long Exam untouched; **no prompt or schema
change beyond R1's owner-signed wording**; no `LLM_MODEL_CRITIQUE` change; no mass regeneration; feature PRs
never edit this file.

### Shipped

- **R1: statement-pair answer-key guard and Challenge start minimum** (PR #1513, `7b9ae007`).
  - **The guard.** Challenge generation records a true/false verdict for each statement before it picks the
    answer. The server rejects a key that contradicts those verdicts, retries the question once, and then omits
    it.
  - **Scope.** It covers Challenge start (single and multi-note), +5 and the Official template seed. Board,
    Long Exam, Adaptive, teacher quiz and Study Pack generation are unchanged.
  - **Field order.** The Challenge item order is now question, choices, statementVerdicts, answer, explanation.
  - **Start minimum.** A single-note start may be at most one question short; a multi-note start fails at zero.
    A start below its minimum ends FAILED before quota is charged, and its claims are released.
  - **The old 0-question path is fixed.** It charged quota for a failed start; Codex's pre-fix test showed this.
    A read-only check found that the 3 production 0-question failures failed before the charge, so no refund is
    owed.
  - **Count logs.** `challenge_quiz_generation_counts` and `challenge_quiz_delivery_counts`.
  - **Verification.** `./mvnw clean install`: 2,789 tests, 0 failures, including the real-transaction floor
    tests. Codex review found nothing. A cold Sonnet falsification review of 10 claims disproved none.
  - **Known limitations:**
    - A single-note start can be one short (item 2). *(Superseded for multi-note by item 5b: a multi-note start below its two-thirds floors now fails with no charge, instead of opening with as few as 1 of 18.)*
    - A stem without "Statement 1:" with a colon is not checked.
    - A choice like "Only Statement 1 and 2 are correct" would misclassify. Neither appears in stored data.
    - A statement-pair item emitted as TRUE_FALSE now takes the one-question retry instead of a whole-batch
      retry.
    - **Factual-verdict errors are not caught.** The model can judge a statement wrongly and key it
      consistently. The acceptance run found 3 of 32 such keys on mini, 0 of 31 on Luna and 0 of 28 on Sol.
    - A start whose database write fails mid-transaction returns a server error instead of "Try again". This
      is pre-existing and loses no quota (item 2).

- **Item 2a: Challenge preparation state and charge at Start Quiz** (PR #1518, merged as `5c80f809`).
  - **No timer before Start Quiz.** Every ordinary Challenge start lands on `preparedNotStarted`. The new
    `POST /challenge-quiz/sessions/{id}/start-quiz` is the only thing that starts the timer, and it is
    idempotent. Board Exam is unchanged.
  - **Quota at Start only** (owner decision 2026-10-20). It is re-checked under the user-row lock.
    Preparation, failure and expiry never charge.
  - **Async preparation.** Single-note generation runs on a dedicated executor with no transaction across the
    model call. Failed preparations keep their questions in the bank and release claims.
  - **Analytics.** `CHALLENGE_QUIZ_STARTED` fires at Start, redo-missed included. There are three new
    preparation events.
  - **Verification.** `./mvnw clean install`: 2,829 tests, 0 failures. A cold falsification review found
    2 P2 and 5 P3 defects; all are fixed with tests.
  - **Not done yet, by design:** the frontend Start Quiz wiring (item 3; **items 2 and 3 deploy together**)
    and the expiry and stuck-preparation sweeps (2b).
  - **Known limitation:** regeneration of an additional source note in a multi-note quiz is not caught
    before Start (item 5).

- **Item 2b: prepared-quiz expiry, stuck-preparation sweep and collision choice** (PR #1519, merged as
  `f086851f`).
  - **7-day expiry of an unstarted quiz.** It is enforced three ways: lazily (forfeit and proceed), by a
    sweep, and under lock in `startQuiz`, which returns 409 `PREPARED_CHALLENGE_EXPIRED`. Claims are released
    and quota is untouched. A reload never shows an expired quiz as ready. The sweep selects candidates by
    preparation time.
  - **A preparation stuck for 20 minutes is marked FAILED** by the recovery job (ordinary Challenge only),
    with claims released and no quota effect.
  - **Collision instead of silent reuse.** A Board Exam or Redo Missed request against an unstarted
    ordinary Challenge on the same note returns `collision`. Re-sending with `discardPreparedSessionId`
    forfeits it (`DISCARDED`) and proceeds; a mismatched id returns 409 `INVALID_PREPARED_CHALLENGE_DISCARD`.
  - **Analytics.** `CHALLENGE_QUIZ_PREPARATION_ABANDONED` carries the reason EXPIRED, DISCARDED or STALE. An
    explicit forfeit of an unstarted quiz fires DISCARDED.
  - **Verification.** `./mvnw clean install`: 2,854 tests, 0 failures. That includes real-PostgreSQL tests of
    both sweep row writers, the expiry-sweep versus Start race and the stuck-sweep versus preparation-finish
    race. A cold falsification review disproved none of 9 claims. Codex review findings are fixed, except
    the frontend collision handling, which is item 3's.
  - **⚠️ Release dependency: the client does not yet act on `collision`.** Until item 3 lands, a learner
    with an unstarted Challenge cannot reach Board Exam or Redo Missed on that note. **v0.175.0 must not merge
    to `main` without item 3.** ✅ Satisfied by item 3 (Challenge preparation screens).
  - **Known limitations:**
    - The 20-minute stuck bound counts from `createdAt`, so a preparation queued under heavy load can be
      failed. That costs no quota.
    - A double-submitted Board Exam discard gets a 409 on the second request.
    - Claim release is scoped to the session's primary pack. That is correct today, because multi-note
      starts do not claim from the bank; item 5 replaces it with a session-scoped release.

- **Item 3: Challenge preparation screens, with Part B folded in** (PR #1521, merged as `9951f4e6`).
  - **The learner flow.** The ordinary Challenge setup button reads **Prepare Quiz**. It leads to "Getting
    your quiz ready" (with Continue reviewing and no Stay button), which moves to "Your quiz is ready!" on
    its own, and the timer starts only on **Start Quiz**.
  - **The other screens.** Preparation failed (Try Again), expired (Prepare New Quiz, which creates nothing
    until clicked) and the collision choice (Resume, or Start something else, which re-sends the original
    request with `discardPreparedSessionId`).
  - **Every route in lands on the right screen.** Reload, the dashboard card ("Open your prepared quiz"),
    mode selection and Redo Missed all do, and no path reaches running without a successful `start-quiz`.
  - **Board Exam is unchanged,** with regression tests.
  - **Part B** (the 2026-10-21 plan): `startQuiz` now returns a separate code per case:
    - 409 `PREPARATION_IN_PROGRESS` while generating;
    - 409 `PREPARATION_FAILED`;
    - 409 `PREPARED_CHALLENGE_EXPIRED` for a quiz forfeited before it started.

    A completed or started quiz keeps `SESSION_NOT_IN_PROGRESS`. The page maps the first two to the
    preparation and failure screens.
  - **Copy:** "Counts toward your monthly quiz limit when you press Start Quiz. Leaving before you start
    doesn't use an attempt." (ordinary Challenge only), plus the collision and dashboard strings. **New
    strings are pending owner sign-off.**
  - **Verification.**
    - Backend `./mvnw clean install`: 2,860 tests, 0 failures, including a MockMvc 409 test.
    - Frontend: 2,723 tests; `tsc`, lint and `next build` clean.
    - The new `lib/api-challenge-quiz.test.ts` pins the request shapes.
    - Three Codex reviews and three fix rounds; 9 findings fixed and 1 dismissed. The last fix round had
      full checks but no further review.
  - **Known limitations:**
    - During an API outage the preparation screen keeps polling and shows no error. Continue reviewing
      always works.
    - Multi-note Prepare Quiz is still one synchronous request (item 5).
  - **⚠️ Deploy ordering.** The new frontend calls `start-quiz` and the new codes, and the old frontend
    cannot drive prepared sessions. Backend and frontend must ship together in the release merge; that
    statement is owed at signoff.
  - **Review note.** `startQuiz` has now been edited by 2a, 2b and item 3, with item 5 to come. It is a
    named input to the scoped cold review at signoff.

- **Item 4: readiness notification and deep link** (PR #1522, merged as `5a6fc945`).
  - **One in-app notification per ready session** (`CHALLENGE_QUIZ_READY`, `ASYNC_RESULT`). It is sent on every
    ordinary-Challenge transition into ready, through one shared hook that item 5 inherits:
    - the async single-note finish;
    - a full bank or template hit;
    - Redo Missed;
    - a multi-note start.

    It is published inside the transaction and delivered after commit on `notificationFanOutExecutor`, so it
    runs outside any transaction. Dedup is the existing `(recipient, dedupKey)` index, keyed by session. A
    delivery failure never changes the session. Board Exam never notifies.
  - **Copy (pending owner sign-off):** "Your quiz is ready!" / "Pick up where you left off." / "Open your
    prepared quiz". The link goes to `/notes/{noteId}/challenge-quiz?session=<id>`.
  - **Marked read when seen:** `POST /challenge-quiz/sessions/{id}/ready-seen`. It never writes the session, so
    it cannot race `start-quiz`. It delivers with the same dedup key and marks the row read, so both orders end
    with one read row.
  - **Deep link:**
    - `GET /challenge-quiz/sessions/{id}/status` and `GET /challenge-quiz/sessions/{id}`, both owner only, 404
      for anyone else, read-only, and redacted while unstarted;
    - the page lands on generating, ready, running, expired, failed or setup, and never calls `start-quiz`;
    - a link opened while the page is already mounted also resolves.
  - **Verification.**
    - Backend `./mvnw clean install`: 2,868 tests, 0 failures. That includes 4 real-PostgreSQL tests:
      committed and deduplicated after commit, a rollback delivers nothing, ready-seen-first leaves one read row,
      and another owner gets 404.
    - MockMvc tests for the three endpoints.
    - Frontend: 2,739 tests; `tsc`, lint and `next build` clean.
    - One Codex review and one fix round, with 3 findings fixed.
  - **Review note.** `ChallengeQuizService` readiness paths are a named input to the scoped cold review at
    signoff.

- **Item 5a: question-bank integrity, the first half of item 5** (PR #1523, merged as `14b14361`).
  Item 5 is split: 5a is the shared claim/release surface; 5b is multi-note background preparation.
  - **Claims release per session, not per pack (A.4).** Every release and outcome update is scoped to
    `(userId, claimedSessionId)`; no pack-scoped call is left. Rerouted:
    - `startSession`, `finishChallengePreparation`, `completeSession`, `forfeitSession`;
    - `generateMoreQuestions`;
    - `forfeitPreparedSession`, `forfeitStaleOrdinarySession`, `forfeitExpiredSession`;
    - both `GenerationRecoveryRowWriter` sweeps.

    Rows a session claims from several packs are all freed. This becomes reachable from multi-note in 5b.
  - **Legacy statement-pair exclusion (A.3, Option A, owner-confirmed).** A bank row is excluded from claims when
    it is a statement pair, **not** template-derived, and older than the cutoff. While the cutoff is unset,
    every such row is excluded.
    - One shared predicate is applied to the ordinary claim (single-note start and +5), the Redo Missed claim
      (before its minimum check, so it still fails closed) and the Redo Missed count (a non-locking fetch).
    - Template copies are exempt, by membership in the template's key set. That includes an Official note's
      own seeded rows when the note was itself copied.
    - Excluded rows are never written.
    - **"Post-cutoff" means the row passed R1's consistency check, not that it is factually verified.**
  - **⚠️ Behaviour change on paths already live:**
    - single-note starts and +5 stop serving legacy learner-original statement-pair rows, which means a little
      extra live generation until the cutoff is set;
    - the Redo Missed CTA can appear less often, because its count and claim both filter.
  - **⚠️ Owner post-deploy step (v0.175.0 signoff handoff).** Set `CHALLENGE_QUIZ_STATEMENT_PAIR_LEGACY_CUTOFF`
    on Render to the moment the v0.175.0 backend is **confirmed live**, from `scripts/check-deploys.sh` or the
    Render deploy record. Do not use the merge time.
    - The format is ISO-8601 with an offset, for example `2026-10-28T09:00:00+08:00`.
    - A malformed value fails startup.
    - This is an owner action only.
  - **Verification.**
    - `./mvnw clean install`: 2,881 tests, 0 failures. That includes the A.4 lifecycle tests on real
      PostgreSQL (completion, discard, expiry, failed preparation, stuck recovery, two-pack release,
      idempotent release, session isolation, concurrent completion and discard), the filter tests on real rows,
      and a startup test for a malformed cutoff.
    - One Codex review, which found the copied-Official-note exemption gap (fixed), and two test-fixture fixes.
  - **Known limitations:**
    - Regenerating an Official template removes the exemption from adopters' older copies. That errs safe.
    - An excluded legacy row blocks banking an identical regenerated question for the same learner, pack
      and level, so that question is regenerated on each start.
    - The template-key lookup's reads run inside the caller's transaction, which widens the documented
      class of a failed read poisoning a PostgreSQL transaction.
    - `templateQuestionKeys` loads whole bank rows to read their keys; switch it to the key-only query in 5b.
  - **Review note.** The shared claim/release surface is a named input to the scoped cold review at signoff.

- **Item 5b: multi-note preparation in the background** (PR #1525, merged as `aac57fc1`).
  - **Same journey as single-note.** A multi-note Prepare Quiz returns at once with `GENERATING` (or ready on a
    full bank hit) and shows "Getting your quiz ready", the readiness notification and Start Quiz. Nothing
    generates inside the request, which used to take 1–4 minutes.
  - **Each source's own bank first (plan A.1, A.2).** Each source, in sorted pack order:
    1. claims from its own bank;
    2. copies from its Official template;
    3. generates only its shortfall, with its own level, stamp and disallowed keys (including its own saved
       quiz);
    4. persists that shortfall back to its own bank.

    So re-preparing the same set costs no model calls once its questions are banked, and multi-note and
    single-note now feed each other.
  - **Parallel, bounded generation.** Shortfalls run concurrently on a dedicated
    `challengeMultiNoteSourceTaskExecutor` (six threads), with one shared 300-second deadline. No paid
    attempt or retry starts after it. The job runs on `challengePreparationTaskExecutor`, so it never waits
    on its own pool. Token usage is recorded for every source.
  - **Owner-approved floors (Q1).** Ready requires at least `ceil(2/3 × requested)` questions **and** at least
    `ceil(2/3 × sources)` contributing sources, in integer arithmetic (18 → 12, 6 sources → 4, 2 → 2). Below
    either floor the session fails: no charge, Try Again offered, claims released, valid generated questions
    salvaged unclaimed. The thresholds are configurable; changing them in production needs owner approval.
  - **Stale protection.** If any source regenerates or is deleted during preparation, the session fails. At
    Start Quiz or on resume, every source stamp is checked: a mismatch or a missing pack forfeits STALE and
    releases claims, and resume prepares fresh.
  - **Disclosure.** An underfilled ready quiz, single-note or multi-note, shows "Your quiz is ready with X of Y
    questions." (owner Q4 wording). Multi-note also shows source coverage "From {n} of {m} notes" (owner-approved
    2026-10-22). A Redo Missed ready quiz shows "Redo Missed Questions doesn't count toward your
    monthly quiz limit."
  - **Also:** `templateQuestionKeys` now uses the key-only query, which clears 5a's recorded limitation.
  - **Verification.**
    - `./mvnw clean install`: 2,913 tests, 0 failures, including real-PostgreSQL tests for: full and partial
      bank hits; per-source banking with its own level and stamp; cross-pollination; non-primary release;
      parallel and deadline behaviour; the floors; stale and deleted sources; and overlapping starts with no
      deadlock.
    - Frontend: 2,746 tests, `tsc`, lint and `next build` clean.
    - Two Codex reviews and two fix rounds, with 5 findings fixed.
  - **⚠️ Behaviour change:** a multi-note start below the floors now fails, instead of opening underfilled.
    R1's limitation line is updated above.
  - **Known limitation:** queue time within the dedicated pool counts against the shared deadline.
  - **Review note.** The shared start, Start Quiz and bank paths are named inputs to the scoped cold review at
    signoff.

- **Item 6: Challenge tier routing plus background +5** (PR #1526, merged as `a4555d42`). Owner kept
  it in v0.175.0 on 2026-10-21. The +5 design is the owner-approved proposal
  `docs/claude-plans/2026-10-21-v0175-plus5-slower-models-proposal.md`.
  - **Routing by plan.** Ordinary Challenge single-note preparation, multi-note source shortfalls and +5 use
    `LlmRouting.tiered(userId)`: FREE uses `LLM_MODEL_FREE`; Plus, Pro and ADMIN use `LLM_MODEL_PREMIUM`.
    - There is **no free fallback** for a paid learner, and a transport retry stays on the same route.
    - Board Exam and Long Exam are unchanged (C6). Official template seeding was already tiered.
    - `model_used` is recorded for every path.
    - **Why it matters for correctness:** R1's arms found 0 wrong statement-pair keys on Luna (31/31) and Sol
      (28/28), against about 4/32 on the interactive model.
  - **Background +5.**
    - **The request** records an in-flight marker under a short lock and returns HTTP 200 with
      `morePending` at once. No model call happens in the request, and no lock or transaction is held across
      one; before this, +5 held the session lock and a database connection for the whole model call.
    - **One +5 per session:** a second request gets 409 `MORE_IN_PROGRESS`.
    - **The job** runs on `challengePreparationTaskExecutor` with a 240-second deadline. No paid call starts
      after the deadline or after Stop.
    - **The append** happens only if the quiz is still running, the marker still matches and the timer has
      not expired. It is append-only, the time is extended 90 seconds per question added, and it never
      touches answers or progress.
    - **Stop:** `POST …/generate-more/stop`. A retry waits until the stopped job has released its claims,
      so a stopped job can never release a retry's claims.
    - **Recovery:** a lost worker's stale marker recovers after the deadline plus a margin. Completion
      releases unserved +5 claims and keeps their prior outcome, so Redo Missed eligibility is preserved.
  - **The page.** Answering stays enabled during +5 and the timer is never paused.
    - Copy: "Adding questions…", "{n} questions added", "Still adding questions…" / "Stop adding" after 45
      seconds, and "Couldn't add questions" / "Try again" (owner-approved).
    - The append never calls `applyStartedSession` and never overwrites unsaved answers or the current index.
    - The page re-reads the server after Stop, and before a timeout auto-submit while +5 is pending.
    - **New string pending owner sign-off:** "Finishing the previous request…", shown only on Try again right
      after Stop.
  - **Executor sizing.** `challengePreparationTaskExecutor` is now configurable, default 6 threads with a
    24-task queue (it was 4). That keeps it within the 20-connection Hikari pool shared with 18 Tomcat
    threads; production showed 20 open and all idle on 2026-10-22. Jobs hold a connection only for short
    load and finish transactions.
  - **Verification.**
    - `./mvnw clean install`: 2,976 tests, 0 failures. That includes real-PostgreSQL tests for: the latched
      race between append and a progress save; concurrent +5 requests; stop; stop then retry; timer expiry
      mid-generation; regeneration; a stale pending marker; and completion during +5.
    - MockMvc tests for 200 with `morePending`, 409, and stop 204.
    - Frontend: 2,753 tests; `tsc`, lint and `next build` clean.
    - Two Codex reviews and two fix rounds, with 5 race findings fixed.
  - **⚠️ Deploy ordering.** The `generate-more` response contract changed, so the backend and frontend must
    ship together in the release merge.
  - **⚠️ Your Render switch now moves Challenge too.** The v0.174.0 Render switch (`LLM_MODEL_FREE`,
    `LLM_MODEL_PREMIUM`) now also moves Challenge start, multi-note and +5, not only Study Packs. Rolling those
    env vars back rolls Challenge back with them. This belongs in the v0.175.0 signoff handoff.
  - **Review note.** It changes the running-quiz state and shares `startQuiz` and the bank paths, so it is a named
    input to the signoff pressure test. This release changes quota semantics, so that is the **full**
    three-agent test.

- **Voice copy for v0.175.0** (PR #1527, merged as `a42a1548`). Owner-approved 2026-10-22 under the new
  `docs/product/VOICE_AND_TONE.md`. It applies the 19-row copy diff in
  `docs/claude-plans/2026-10-22-v0175-copy-review-product-ux-prompt.md`:
  - sentence-case Challenge buttons ("Prepare quiz", "Start quiz", "Prepare a new quiz", "Try again", "Continue
    reviewing", "+5 questions");
  - the setup line "…counts toward your monthly limit only once you start it…";
  - +5: "Added {n} more questions." and "We couldn't add more questions. Your answers are safe.";
  - a warmer mode-selection line;
  - the screen-reader label "Preparing your quiz";
  - five reassuring server messages.

  **Copy only:** no behaviour, rule or configuration change. Buttons shared with Board Exam ("Start Fresh",
  "Resume", "Starting...") are unchanged (C6) and left for a later sweep. Backend 2,976 tests and frontend
  2,753 tests pass; `tsc`, lint and `next build` are clean.

- **Challenge Quiz flow polish** (PR #1528, merged as `e864fee2`). It applies the owner-approved audit
  and proposal `docs/claude-plans/2026-10-22-v0175-challenge-flow-polish-audit-and-proposal.md` (§1–§9), to
  ordinary Challenge only.
  - **⚠️ Defect fixed (F5).** The collision check ignored the note selection, so asking for different notes on a
    note that already had a prepared quiz silently reopened the old quiz.
    - Now an unstarted quiz with a different source set gets the existing collision choice.
    - An exact match (the same set in any order, the same `sourceCollectionId` for plan quizzes, and the same
      Redo Missed flag) reopens it.
    - It is compared straight from the request, with no cap check or lock, so 5b's resume-after-cap-drop still
      holds.
  - **Direct open.** Choosing Challenge Quiz with an exact ready or generating single-note quiz opens it, with no
    redundant Prepare quiz. No path calls `start-quiz` except the Start quiz button.
  - **The continuation card.** A new `challenge-continuation-card` in the lighter Today's Focus style, with one
    action per state:
    - "Open prepared quiz", "View preparation" or "Resume quiz";
    - the Redo Missed titles "Your missed-question review is ready!" and "We're getting your missed-question
      review ready…";
    - **no Start Fresh** on the ordinary card, since a started quiz can still be ended from inside it;
    - Board Exam's continuation is unchanged (C6).
  - **The ready briefing.**
    - "All set! Your quiz is ready.", the note title, the real "{N} questions · {M} minutes" (one shared
      `Math.ceil` helper), the coverage and X-of-Y lines, and the timer and attempt reminder (with a Redo Missed
      variant).
    - One "Start quiz" button, plus quiet links "Continue reviewing" and "Want to study different notes?". The
      second shows only when notes can be chosen, and restores only notes the picker can still show.
  - **⚠️ Defect fixed (found in review).** Returning to setup restored notes that had left the plan. They stayed
    selected but invisible and used up the source cap; now only still-selectable notes are restored. A
    mutation-checked test covers it.
  - **Deferred:** the "this note is also in another prepared quiz" line on secondary notes. It needs a
    source-membership endpoint, beyond the approved small scope.
  - **Verification.**
    - `./mvnw clean install`: 2,980 tests, 0 failures, including a real-request MockMvc test that a ready
      unstarted response carries the stored count and time.
    - Frontend: 2,764 tests; `tsc`, lint and `next build` clean.
    - One Codex review.
  - **Review note.** The `startSession` collision change is a named input to the signoff pressure test.

- **Statement-pair verifier in production code, switches off** (PR #1520, merged as `f054a37a`).
  - **What it does.** Before a statement-pair question is kept, two independent checkers (Sol and Luna, run
    concurrently on their own `statementPairVerifierTaskExecutor`) judge each statement against the source
    note. A question is kept only when both agree with its key. Rejected questions get one batched
    replacement round; anything still rejected is omitted. Every failure is closed: a timeout, a rejected
    submission, a missing verdict, an unparseable pair or blank note text rejects the question, and no
    paid attempt starts after the shared deadline.
  - **Three switches, all default false:** `STATEMENT_PAIR_VERIFIER_TEMPLATE_SEED_ENABLED` (Official template
    seeding), `STATEMENT_PAIR_VERIFIER_LIVE_START_ENABLED` (single-note Challenge preparation), and
    `STATEMENT_PAIR_PLUS5_EXCLUSION_ENABLED` (+5 exclusion rule). Production behavior is unchanged on merge.
  - **Prompts.** The signed verifier prompts are byte-identical to the Stage 1 harness, and a parity test
    guards them. Template fields are filled in one pass, so note text is inserted literally.
  - **Verification.** `./mvnw clean install`: 2,870 tests, 0 failures. Seven Codex reviews and six fix
    rounds covered:
    - a shared-pool deadlock;
    - one deadline for both checkers;
    - item-ID gaps;
    - closing-question stripping, checked against 1,607 real stems;
    - placeholder expansion;
    - the +5 leak count including retries;
    - replacement questions with a single statement marker.
  - **Merge gate (owner update, 2026-10-21):** #1520 may merge with all three switches OFF after code review
    and release checks. No checker activation is authorized; the +5 exclusion switch remains OFF.
  - **Stage 2 record:** Part 15 of `docs/claude-plans/2026-10-20-v0175-statement-pair-verifier-wording-and-pass-rules.md`
    records P1 pass; P2 KILL as computed with the timeout confound; an inconclusive paired diagnostic;
    R1r concept GRAY at 82.5%; and R3r pass.
  - **Activation scope:** multi-note Challenge starts are not checked (`ChallengeVerifierPath.NONE`) until
    item 5 moves them to background preparation with a question-count floor.
  - **Known limitation:** verifier calls are logged under the `CRITIQUE` route, and cost attribution uses
    `action=statement_pair_verifier`. A dedicated `LlmRoute` value would also change model routing, so it
    was left out.

- **Signoff fix rounds** (PRs #1529, #1530, #1531, #1532). The full pre-signoff pressure test (three cold agents)
  found no P1 and these confirmed defects; Codex fixed them, and each round was re-verified on the Docker suite
  with a mutation check.
  - **#1529 (`8fb55423`), the first fix round.**
    - **Complete during +5.** The page stops and re-reads the session before submitting, and does not submit if
      questions arrived. The server grades only the questions the client saw: it drops trailing unanswered items
      beyond the client's `totalQuestions` and returns their claims to the pool unserved. Scoring is unchanged,
      since the denominator is the answered count.
    - **Entry routing restored (C6).** Item 3 had forced mode selection on Review Set and Teacher entries. Those
      now land on their pre-release first screen; mode selection appears only when a quiz exists to continue.
    - **Mode cards while preparing.** They now show for a generating Challenge. The test fixture had set
      `preparedNotStarted` on a GENERATING session, a shape production never produces.
    - **Abandoned started quizzes.** A bounded recovery sweep forfeits a started ordinary Challenge after its time
      plus a 10-minute grace (`studysnap.generation.challenge-started-expiry-grace-minutes`) and releases every
      source's claims. It takes pack locks in sorted order, then the session, and charges or refunds no quota.
    - **+5 and saved quizzes.** +5 now excludes the saved Study Pack quiz of the primary and every source note
      from the bank claim, the template copy and generation. A source deleted mid-session is skipped instead of
      failing every +5.
    - **Locks and salvage.** The multi-note start locks every requested pack in sorted order before the session.
      A deleted source no longer rolls back the other sources' salvage.
    - **Smaller fixes.** Try again after a deep link rebuilds its request from the session; a linked poll stops on
      a quiz ended elsewhere; a second tab polls an in-progress +5; the two API fallback messages use the approved
      wording; a non-empty `generate-more` response is appended through the poll path.
    - **Tests.** Real-request MockMvc tests for the `generate-more` response and the `PREPARED_CHALLENGE_EXPIRED`
      and `INVALID_PREPARED_CHALLENGE_DISCARD` wire codes; real-PostgreSQL tests for the completion trim, the sweep
      and the lock order. The sweep predicate was mutation-checked on its cutoff and its mode filter.
  - **#1530 (`e58108bc`), Redo Missed no longer ends a started quiz silently.** A Redo Missed request on a note with a
    started ordinary Challenge now returns the collision choice (`existingStarted: true`). "Resume quiz" returns to
    the running quiz without calling `start-quiz`; "End quiz and redo missed" ends only that exact quiz (forfeit and
    release claims, no prepared-discard analytics, no quota change). An expired quiz is still cleared quietly.
    Owner-approved copy.
  - **#1531 (`737e6f4c`), the +5 status on every question.** "Adding questions…" with a spinner, Stop and the retry
    line show in the action bar on every question, not only the last. The owner's click-through found this.
  - **#1532 (`69062102`), the clock and ended-quiz fixes.** Found by the cross-PR interaction test.
    - **Complete lockout closed.** Complete is held only for a +5 started by this tab's own successful request,
      timed by this tab's clock for at most 45 s. A reloaded or other-tab +5, a server clock ahead or behind, a hung
      poll or a dead worker can no longer keep it disabled. The earlier reload test had omitted the server start
      field; it now uses the production shape.
    - **Ended quizzes.** Resume re-reads the session first; any poll or reconcile that sees a status other than
      `IN_PROGRESS` stops, never appends, and returns to Challenge setup; Complete failing with
      `SESSION_NOT_IN_PROGRESS` does the same. The response reports `morePending` only while the session is
      `IN_PROGRESS`.
  - **Verification at close.** `./mvnw clean install`-equivalent full suite on Docker: 2,998 tests, 0 failures,
    0 skipped, real-PostgreSQL tests included. Frontend: 2,786 tests; `tsc`, lint (0 errors) and `next build` clean.
    The old server-time hold was killed by 4 tests, and the started-quiz collision guard by 4.

### Signoff record (2026-10-25)

**Scope-completeness gate.** Items 1–6 are all shipped, each with its PRs above. Item 2's "recovery-sweep entries" are
the stuck-generating, prepared-expiry and started-expiry sweeps in `GenerationRecoveryService`. The verifier ships
with every switch OFF by design. **Not shipped, by the owner's earlier decisions:** Board Exam and Long Exam "press
Start first" (C6), the Long Exam pool reduction 48 to 30 (A4, deferred from v0.174.0 into the Board/Long Exam release;
`LONG_EXAM_POOL_SIZE` is still 48), the B6 backfill (C8), and low reasoning effort (B7). **Deferred:** the "this note is also in a
prepared quiz" line on secondary notes.

**⚠️ Deploy-ordering statement. There is no safe order: ship backend and frontend together from one push.**
- **Old frontend against the new backend:** a prepared response carries an empty quiz, so a tab opened before the
  deploy cannot start a Challenge Quiz at all (Prepare quiz returns to setup; nothing is charged). On a +5 that was
  already running, the old page appends nothing and can step past the last question. **Open tabs need a refresh;
  the app has no stale-bundle reload mechanism.**
- **New frontend against the old backend:** start and +5 behave as in v0.174.0 (`morePending` is read as false and
  `newQuestions` append), but the started-quiz collision never arrives, so Redo Missed silently ends a started quiz
  again, and the completion trim is a no-op.
- **Redo Missed on a started quiz in a pre-deploy tab:** the old page shows the unstarted wording ("discarded without
  using a monthly attempt") for a started quiz, and "Start something else" ends it.
- **Frontend-first does less harm than backend-first.** After the merge, wait about five minutes, then run
  `scripts/check-deploys.sh`; if one platform lags, redeploy it at once.

**Owner post-deploy actions (none run by Claude):**
1. Set `CHALLENGE_QUIZ_STATEMENT_PAIR_LEGACY_CUTOFF` to the deploy time of R1 (an ISO-8601 instant). Until it is set,
   every learner-original statement-pair bank row is excluded from reuse (template-derived rows are exempt), which
   lowers bank reuse and nothing else.
2. **The v0.174.0 Render model switch now also moves Challenge:** FREE to `LLM_MODEL_FREE`, Plus, Pro and ADMIN to
   `LLM_MODEL_PREMIUM`, with no fallback. Preparation runs in the background, so the longer starts (Luna p50 45 s,
   Sol 55–105 s, max 150 s in the benchmarks) no longer block the learner. The rollback values are in the v0.174.0
   signoff record.
3. The three verifier switches stay OFF; activation is a separate gated decision.

**Known limitations (every finding is fixed or listed here).**
- **Board Exam can still be lost silently.** A started, charged Board Exam is ended with no confirmation when the
  learner follows the `?entry=redo-missed` next step on that note (`PostSessionNextStepService.java:60`, then
  `forfeitStaleOrdinarySession`). It predates this release and is kept out under C6; it is the first item of the
  Board Exam release (Backlog row).
- A completion with a lowered `totalQuestions` (only a hand-made API request) skips the miss penalty for trailing
  unanswered questions in concept tracking. The score is unaffected, and the app always sends the true count.
- A quiz completed or ended in another tab sends this tab back to Challenge setup with no message, so the result can
  look as if it vanished. It is saved. Better wording needs new copy and is next release.
- In one two-tab case, "Adding questions…" shows without Stop (Complete stays enabled). If the session read behind
  Resume never answers, the button stays busy until a refresh. The running quiz flashes briefly before the started
  collision screen. Under clock skew, "still adding" can appear early.
- The +5 stale-marker threshold (270 s) is shorter than one model call plus its retry. A superseded job's append is
  refused by its token, so the cost is a wasted call, not a double append.
- Capacity: `challengePreparationTaskExecutor` defaults to 6 threads against 20 Hikari connections and 18 Tomcat
  threads; heavy concurrent preparation queues rather than fails. A stale pack heals on retry.
- Board Exam can exceed its monthly limit by one under a race (predates this release).
- Redo Missed on a multi-note quiz's secondary note starts a parallel session; a reload exactly at the deadline
  forfeits instead of grading; the learner-facing expired screen describes a prepared quiz for a stale one. All
  predate this release.
- Smaller bank items that predate this release: swallowed exceptions on some forfeit paths, `ON CONFLICT` insert
  behaviour, the template-exemption shrink, and a cross-source saved-quiz gap on the start path.
- Not yet read: the verifier's Stage 2 result is inconclusive, so it ships OFF; multi-note starts are not verified
  (`ChallengeVerifierPath.NONE`); verifier calls are logged under the `CRITIQUE` route.
- Deferred to a later release: the "this note is also in another prepared quiz" line, and a second click on the same
  readiness notification does nothing.


## v0.174.0 - Enhanced Generation

**Status: Released** (signed off 2026-10-19; **merged to `main` as #1512 (`cd2d55f3`) on 2026-10-25 and live on
Render at 2026-10-25T15:56:25Z; the owner has set `LLM_MODEL_FREE` / `LLM_MODEL_PREMIUM` on Render (reported 2026-10-26; 25 of 25 Study
Packs since the deploy ran on `gpt-6.1-sol`)**; see the merge-day
handoff in the signoff record below. Kicked off 2026-10-16 on `releases/v0.174.0`, **cut from
`releases/v0.173.0` (`7d1407a1`, signed off, NOT yet on `main`)** — owner decision 2026-10-16, the same pattern `v0.111.0`
and `v0.120.0` used. Anything that changes on `releases/v0.173.0` after this cut is merged forward.)

Theme: paid plans get the stronger model they were always tagged for. FREE moves to a newer model,
Plus and Pro to the stronger one, and official NoteLib curation is routed on purpose rather than by
the admin account's subscription tier, all before `gpt-4.1` retires on 2027-01-14.

**Why this release exists.**
- Every generation path ignores the plan tier. `ModelTier.PREMIUM` is written for paid plans
  (`StudyPackService.java:721`), but nothing reads it to pick a model. Nine `LlmStudyPackService`
  methods always use `LLM_MODEL_FREE` (`OpenAiLlmStudyPackService.java:231,251,288,1793,2199,2273`);
  premium is used only at `:273` (companion) and `:1860` (Interview Practice).
- The owner chose the new models on 2026-10-16: FREE → `gpt-6-luna`, Plus/Pro → `gpt-6.1-sol`.
- The planning package is in `docs/claude-plans/2026-10-16-v0174-llm-tiering-remediation-tightened.md`
  (plan), `…-audit-and-scope.md` (audit, 5a/5b trace) and
  `docs/claude-plans/2026-10-16-llm-model-migration-benchmark-design.md` (benchmark).
- Cost context (`docs/claude-findings/2026-10-16-study-packs-plantype-distribution-original-generations.md`):
  2,002 of 2,234 original Study Packs come from the owner's ADMIN account, which holds a
  non-billable PRO grant. No paid subscription is active. So the real Sol exposure is official
  curation, not subscribers.

**⚠️ MERGE GATE: this release's PR merges to `main` only after BOTH:**
1. `v0.173.0`'s PR #1505 has merged, with a merge commit (it is held for the
   `[CHECKPOINT — due 2026-10-22]` signal-9 read); and
2. the `[CHECKPOINT — due cache deploy + 7 days]` read of `v0.173.0`'s cache. Owner decision
   2026-10-16. Any `main` merge, and any Render env-var change, redeploys the backend. A deploy
   briefly runs two connection pools, which is a known kill mechanism and would muddy that read.
   `gpt-4.1` retires 2027-01-14, so waiting costs nothing.
- The owner's Render env-var change for the new models is held to the same gate.

### Planned Scope

1. **Model benchmark (read-only, owner-approved $10 spend cap). Runs FIRST, and gates item 3.**
   - Candidates: `gpt-4.1-mini` (baseline), `gpt-6-luna`, `gpt-6.1-sol`. Design in
     `docs/claude-plans/2026-10-16-llm-model-migration-benchmark-design.md`: deterministic metrics
     plus blind-adjudicated quality.
   - Sol runs at its documented default reasoning effort. Measure billed reasoning tokens, quality,
     latency and retries; tune effort afterwards.
   - **$10 is a maximum, not an allowance for unlimited runs.** The execution plan (who runs it,
     where the API key lives, the per-run cost estimate) goes to the owner before any spend. No
     production writes.
   - It also verifies API compatibility of both new models with the current request shape.
2. **Tier routing for the Study Pack family, and pins for every interactive path.** Delivered as **three
   pieces**, so each verification stays proportionate:
   - **2a:** routing, pins, the log line and the `model_used` fix. Backend, Tier H, Codex at high effort;
     brief `docs/codex-prompts/v0.174.0-item2a-tier-routing-and-pins.md`.
   - **2b:** the cost alert (item 4). Tier N, Codex; brief `docs/codex-prompts/v0.174.0-item2b-curation-cost-alert.md`.
   - **2c:** the `/study` and note-editor error-and-retry state. Frontend, Tier S, inline.

   Both briefs were approved 2026-10-19 and shipped: #1508 (2b), #1509 (2a), #1510 (2c), plus the
   pre-signoff fixes in #1511. **Revised 2026-10-19** by the owner's final decisions A1–A7,
   verbatim in `docs/claude-plans/2026-10-19-owner-final-decisions-v0174-a1-a7.md`. Plan:
   `docs/claude-plans/2026-10-19-v0174-v0175-consolidated-implementation-plan.md`.
   - **Routed by tier** through one resolver that reads `SubscriptionService.resolvePlan` (expiry-aware):
     FREE → `LLM_MODEL_FREE` (Luna); Plus/Pro → `LLM_MODEL_PREMIUM` (Sol); `UserRole.ADMIN` → Sol whatever
     its plan (A1).
     - Do NOT use `CuratorAuthoringPredicate.isCurator()`: it also matches TEACHER profiles.
     - Two separate checks, one destination: `model_tier` stays `planType.isPaid()`
       (`StudyPackService:721`).
     - **Paths:**
       - Study Pack generation: async, plus the synchronous `/study` routes;
       - summary regeneration;
       - note-from-topic, single and bulk;
       - the Official template seed (its owner is the Official author, so Sol);
       - the malformed-quiz repair, using **the pack owner's current effective tier** (A3), resolved inside
         the async task. A missing or deleted owner routes as FREE and logs a warning.
     - The tier is resolved once per generation, and that one snapshot sets both the model sent and the
       `model_tier` label.
     - The benchmark harness sets all four model keys, so every leg still sends the candidate model.
   - **Pinned to today's model for every plan** (A1, A2):
     - new `LLM_MODEL_INTERACTIVE_QUIZ` = `gpt-4.1-mini`: Challenge start, "+5" and multi-note; Adaptive
       Practice; the Quick Review study tip; the teacher quiz; Board Exam and Long Exam, sessions and pools;
     - new `LLM_MODEL_PAID_INTERACTIVE` = `gpt-4.1` (the production value of `LLM_MODEL_PREMIUM`, owner
       verified, A7): Interview Practice quiz and the companion.
     - `LLM_MODEL_CRITIQUE` is unchanged: Interview critique and Ask Companion.
   - **The model is passed in by the caller.** Challenge start, "+5" and the template seed share
     `generateChallengeQuizWithSchema`, and `generateStudyPack` is shared by learner routes and the repair.
   - **`/study` conditions (A5):**
     - keep the existing loading feedback and duplicate-submit guard (`use-study-pack.ts:191`);
     - **add an actionable error-and-retry state** (the error card at `study-page-client.tsx:156-162` has no
       retry action);
     - no copy that promises a completion time;
     - no new automatic retry.
   - **Monitoring:**
     - one structured log line per LLM call (data only, read by a Render log search);
     - fix the Study Pack `model_used` fallback (`StudyPackService:722-723`), which records the free model
       when a response lacks `model`;
     - repaired packs keep their original `model_used` / `model_tier`. That is a documented known
       limitation, not a rewrite (A3).
   - **Shipped in #1509:** a test per path asserting the model **actually sent** for FREE, Plus, Pro, ADMIN on a FREE
     plan and a lapsed paid plan, on every routed and pinned row. Pinned paths must ignore changes to
     `LLM_MODEL_FREE` / `LLM_MODEL_PREMIUM`.
     - The test surface spans all 11 caller services, even though routing changes only the Study Pack
       family.
3. **Model defaults to Luna / Sol (config + an OWNER env-var handoff).** The benchmark is complete and
   adoption is approved (2026-10-17, as amended 2026-10-19).
   - `application.yaml`: `LLM_MODEL_FREE` = `gpt-6-luna`, `LLM_MODEL_PREMIUM` = `gpt-6.1-sol`, plus the two
     new pin keys at today's models.
   - **The production env vars are an owner action.** Claude never calls the Render MCP's env-var update.
     They change only after the merge gate.
   - **Owner-verified Render state (2026-10-19):** `LLM_MODEL_FREE=gpt-4.1-mini` and
     `LLM_MODEL_PREMIUM=gpt-4.1` are set; `LLM_MODEL_CRITIQUE` is unset (YAML default `gpt-4.1-mini`).
     - **[CORRECTED 2026-10-19, pre-signoff falsification]** The earlier "the merge changes no model" claim was
       WRONG. Before this release, every Study Pack path read `LLM_MODEL_FREE` (`gpt-4.1-mini` on Render) for
       every plan. After it, Plus/Pro and ADMIN read `LLM_MODEL_PREMIUM`, which Render sets to `gpt-4.1`.
       - **So merging to `main` with today's Render values moves Plus/Pro and all official curation from
         `gpt-4.1-mini` to `gpt-4.1`**, at about 5× the per-token price. That covers Study Pack generation, summary
         regeneration, repair, note-from-topic and the template seed.
       - FREE and every pinned path are unchanged.
       - **Rollback to today's behaviour** is `LLM_MODEL_PREMIUM=gpt-4.1-mini`, not "back to its current value".
         Interview Practice and the companion are now pinned separately (`LLM_MODEL_PAID_INTERACTIVE`).
       - **Owner decision 2026-10-19:** keep the Render overrides and change their values right after the
         merge. Do not remove them, so that no future code default can switch production models on its own.
         The steps are in the merge-day handoff in the signoff record.
     - The two new pin keys stay unset and fall back to today's models.
   - **The Long Exam pool stays at 48 in v0.174.0** (A4). Effective value confirmed 2026-10-19:
     `application.yaml:249` `${LONG_EXAM_POOL_SIZE:48}`, the Java default `StudySnapProperties:169`, no
     `application-prod.yaml` override, no Render override (owner, A7). All 626 production Long Exam pools
     have `pool_size` 48. The reduction to 30 moves to v0.175.0.
4. **Official curation cost alert (backend, Tier N, brief 2b).**
   - **$10 per calendar month (A6). It alerts and never blocks.**
   - The estimate is computed by a scheduled read-only job from **persisted** tokens on ADMIN-owned Study
     Packs (`input_tokens`, `output_tokens`, `cached_input_tokens`), at configured per-token rates with the
     cached-input discount.
   - **It is labelled as partial, never as total curation spend.** Note generation from topics, template
     seeding, summary regeneration and repair persist no tokens, so their cost is reviewed on the OpenAI
     billing dashboard.
   - **Delivery:** one in-app notification to the ADMIN account through `NotificationService.deliver`, with
     a per-month dedup key, so a threshold crossing alerts once per month. `type` is a plain `VARCHAR(64)`
     (`V139`), so a new `NotificationType` needs no migration.
   - **The temporary pins expire:** `gpt-4.1` and `gpt-4.1-mini` shut down 2027-01-14. Every pinned path
     needs its own replacement, not only Challenge (A2). Tracked by a `[CHECKPOINT]` row in `ROADMAP.md`.

**Owner decision on the benchmark (2026-10-17, verbatim in
`docs/claude-plans/2026-10-17-owner-conditional-approval-challenge-quiz-ux.md`). Conditional approval of the strategy,
not approval to deploy.**
- **APPROVED:** FREE → `gpt-6-luna`.
- **CONDITIONALLY APPROVED:** Plus/Pro → `gpt-6.1-sol`, and official curation → Sol regardless of the admin
  account's plan, within the agreed cost caps and monitoring.
- **Condition: Challenge Quiz start latency is a product UX issue this release must address, not an accepted
  caveat.** Do not treat the 5-question benchmark leg as a proxy for the real 10/12/15-question and multi-note
  starts.
- Ten deliverables are owed before final rollout approval:
  1. the updated v0.174.0 scope;
  2. the model-routing matrix for FREE, Plus, Pro and curation;
  3. the start-latency root cause;
  4. a recommended UX solution;
  5. a comparison of solutions;
  6. a targeted-benchmark proposal for the real start sizes, needing owner approval before any paid call;
  7. Board Exam and Long Exam validation on the production paths, including the parallel Long Exam variant;
  8. a monitoring and rollback plan;
  9. the split between v0.174.0 and future releases;
  10. the remaining decisions.
- Split by role (agreed between sessions 2026-10-17): **Feature Planner** owns 3, 4 and 5; **Release
  Implementor** owns 1, 2, 6, 7, 8, 9 and 10.
- The decision supersedes the separate Product UX consultation: Feature Planner's deliverables 3–5 answer the
  prompt's questions. The prompt stays as input.
- **Owner decisions, 2026-10-17 (later):**
  - **Targeted benchmark: option A approved.** It runs Tier 1 (10/12/15-question starts and multi-note
    3×6 and 6×3) plus the lower-reasoning-effort variant, under a **$4.50 hard stop** that includes the smoke
    run (`docs/claude-plans/2026-10-17-v0174-targeted-challenge-start-benchmark-proposal.md`).
    - The harness change is test scope only and goes to Codex on its own PR.
    - The owner runs the paid calls.
  - **[SUPERSEDED 2026-10-19 by A4: the pool stays 48 in v0.174.0; the reduction moves to v0.175.0.]**
    **Long Exam pool: lower it rather than benchmark the 26-question pool call (owner).** The owner chose to
    lower it; **Release Implementor chose 30** (`long-exam-pool-size`, `application.yaml:249`;
    `StudySnapProperties:169`).
    - **Trade-off:** at 30 a pool serves one exam and is then refreshed (`ExamQuestionPoolService:134`), so
      reuse across sessions effectively ends. No learner uses Long Exam today.
    - Item 3 checks that no validator or test assumes the pool is larger than the exam (`examSizeForMode`).
    - 30 is the floor: a pool must hold at least one whole exam (`ExamQuestionPoolService:116-123`), and
      the largest Long Exam is 30 questions (`StudySnapProperties:168`).
    - At 30 the pool generates as two calls of 17, the same size a session already sends.
    - It lands with item 3's `application.yaml` defaults. Existing pools are untouched, since none has been
      served and no regeneration is allowed. If `LONG_EXAM_POOL_SIZE` is set on Render, changing it is an
      owner action.
  - ~~Still owed: the acceptable Challenge start wait~~ **Answered 2026-10-19:** a 30 s target and a 60 s
    maximum blocking wait (owner decisions, Set 1). The Challenge work is split out to v0.175.0.
- ~~Item 3's default and the item 2 brief wait on these deliverables.~~ **Discharged 2026-10-19:** the
  deliverables were returned, the owner decided A1–A7, and items 2 and 3 shipped. No Challenge-specific
  routing destination was adopted; Challenge work moved to v0.175.0.

**Anti-drift:**
- No change to Plus/Pro entitlements, `FeatureGateService` quotas or `UserUsageEntity`.
- No pricing, plan copy or announcement. If customer-facing copy is ever needed, it says "Enhanced
  Study Pack Generation" for Plus/Pro and never names an internal model.
- No mass regeneration, and no historical `model_tier` or `model_used` rewrite. 5a: those columns
  correctly record who last generated each pack.
- No prompt-content change beyond what model compatibility strictly requires.
- No migration.
- No checkout/payment path.
- No Hikari pool change.
- Existing notes, Study Packs and learner progress are untouched.

**Deferred, each to be scoped on its own once it has data** (decision 1, approved 2026-10-16):
- the optional regeneration UX; quiz-only regeneration stays a feasibility assessment;
- Sol-as-checker quiz-pool remediation, which needs the benchmark numbers and selective validation
  first;
- the `LLM_MODEL_CRITIQUE` migration, which needs its own feedback-quality check (tightened plan
  §10) and must also land before 2027-01-14;
- pricing/subscription copy and announcements;
- retention instrumentation.

**Verification tier:** Tier H, with at least one scoped cold falsification pass at pre-signoff.
Item 2 changes a shared method and per-generation cost, so falsify both the tier resolution and the
ADMIN carve-out.

**Kickoff scan (2026-10-16):**
- One unindexed plan file (the `v0.173.0` Product UX consultation prompt) was moved to
  `docs/claude-plans/done/`: a consumed release artifact.
- 16 overdue `[CHECKPOINT]` rows are carried unread, not actioned at this kickoff. Among them are
  the 7 long-running ones (`v0.107.0`, `v0.90.0`, `v0.106.0`, `v0.97.0`, `v0.111.0`, `v0.96.0`,
  `v0.74.0`).
- New this scan: the `v0.166.0` Hikari checkpoint row's title still reads open although its Status
  records the 2026-10-14 read. That is a title/status contradiction, left for a dedicated pass.
- RC-3's reminder-timing checkpoint falls due 2026-10-21, inside this release.
- `v0.168.0` was archived from both files; no line was lost, checked against the pre-move copies.

### Shipped

- **Item 1, part 1: the benchmark harness (PR #1506, `8d417a35`, test scope only). No API call made
  and nothing spent yet; the owner runs the paid smoke and full runs.**
  - `backend/src/test/java/com/studysnap/backend/benchmark/`. `LlmModelBenchmark` runs the real
    `OpenAiLlmStudyPackService` and production prompts on one candidate model. It is skipped unless
    `-Dllm.benchmark=true`, so a normal build and CI never run it. No file under `backend/src/main`
    changed.
  - `MeteringInterceptor` meters every HTTP call, including the service's own retries. It stops
    before the next call at `spendStopUsd` (hard max $10) or `maxCalls`, and records raw `usage`
    including reasoning tokens. It never saves the request or its headers.
  - The README sets explicit per-model stops totalling $9.25 under the $10 cap: smoke $0.25 × 3,
    then full runs at mini $1.50, Luna $0.75, Sol $7.00. Without them, the $8 per-command default
    would have allowed up to $24.
  - `sample-query.sql` is one read-only `SELECT` producing 81 public-note lines (A 40, B 32, C 5,
    D 4). Its output holds note text, stays under `backend/target/`, and is never committed.
  - Codex review P2, fixed with a regression test that fails without the fix: a transport failure
    was classified `EMPTY`, which distorted that metric and bypassed the 3-consecutive-errors stop.
  - Verification: full `./mvnw clean install` with Docker, 2,713 tests, 0 failures, before the
    test-only P2 fix; `LlmModelBenchmarkTest` 12/12 after it.
  - **Results caveat:** Leg B reuses each note's existing `gpt-4.1-mini` Study Pack as identical
    input for all three models; the context fields approximate `StudyPackGenerationContextResolver`.
  - Findings for item 2/4: `LlmResponseUtils` takes the first `output_text` and skips reasoning
    items, so a second `output_text` is silently ignored; `extractUsageMetadata` does not record
    reasoning tokens, which item 4's cost monitor needs.
- **Item 1, part 2: benchmark run and results (2026-10-17). COMPLETE; item 3 is the owner's decision.**
  Results and recommendation: `docs/claude-findings/2026-10-17-v0174-llm-benchmark-results.md`.
  - The owner ran the smoke run and the three full runs: about **$2.62** of the $10 cap. Billed cost
    is lower: `output_tokens` already includes reasoning, and cached input is billed below list price.
  - Generations succeeded: `gpt-4.1-mini` 77/81 (3 timeouts at 180 s, 1 invalid Long Exam format),
    `gpt-6-luna` 81/81, `gpt-6.1-sol` 81/81. No multi-output responses and no H4 flags on any model.
  - Blind tier, 20 items per model, scored by a fresh agent: no regression detected for either
    candidate. Differences between the means are within noise and are not evidence for an "Enhanced"
    quality claim.
  - Cost per generation: mini $0.0033, Luna $0.0015, Sol $0.0272.
  - Recommendation: both candidates pass the pre-stated rule; Sol's cost is accepted under the execution
    plan's own clause. Caveat for items 2 and 3: STARTING a Challenge Quiz waits in the foreground for 10–15
    live questions whenever the learner's bank and the Official template fall short (33 starts by 29 of
    34 learners since 2026-07-24). That wait is already p50 30 s / p90 62 s on mini, and projects to about
    55–90 s on Luna and 90–175 s on Sol, near the 180 s timeout. "+5" is almost always served from the
    bank (2 live batches for learners). The owner made it a release condition on 2026-10-17 (see Planned Scope); Sol's single-call Long Exam took up to 126 s against the 180 s timeout.

- **Item 1, part 3: the Leg E harness for the targeted Challenge start benchmark (PR #1507, `5dffd5e6`,
  test scope only). Nothing spent yet; the owner runs the paid calls.**
  - `backend/src/test/java/com/studysnap/backend/benchmark/` gains cells S15 / S10 / S12 (15 `hard`,
    10 `easy`, 12 `medium`, read from the `ChallengeQuizService` constants), M6x3 and M3x6 (multi-note
    starts as sequential per-source calls), and VLOW (S15 with `reasoning.effort: "low"`, injected by the
    interceptor on every attempt).
  - It mirrors production's avoid-lists. Single-note uses the pack's saved quiz (`:396,463`). Multi-note
    starts empty, accumulates, aborts on a failed source, and dedups the final set against the primary
    pack's quiz (`:421,1831-1860`).
  - It records accepted vs requested questions, the sequential start latency, the slowest source call, the
    failed source index and timeouts. The summary excludes spend-stopped runs.
  - The pre-flight fails the command before any HTTP call. `sample-query-e.sql` is one read-only `SELECT`;
    a count-only version run against production returned all 52 paired lines.
  - Run sheet: ten commands with explicit stops totalling $4.10, about $4.45 worst case with a one-call
    overshoot per command, under the approved $4.50.
  - Release Implementor fixed two issues after Codex, each with an assertion mutation-checked to fail
    without its fix: a malformed line no longer passes silently, and spend-stopped runs no longer skew the
    latency summary.
  - Verification: `./mvnw install` with Docker, 2,723 tests, 0 failures, including
    `NativeQueryPostgresIntegrationTest` (115). No file under `backend/src/main` changed.
  - Known difference from production: multi-note cells use `medium`, but a learner's last Quick Review
    score can make it `easy` or `hard`.
- **Item 4: the Official curation cost alert (brief 2b, PR #1508, `c1a28305`). Alert only; never blocks.**
  - `CurationCostAlertJob` runs hourly in UTC.
    - It estimates the month's **tracked** cost of ADMIN-owned Study Pack generation from persisted tokens:
      one native aggregate query by `model_used`, at configured per-model rates.
    - At **USD 10** (owner A6) it delivers one in-app `CURATION_COST_ALERT` per ADMIN per month, deduplicated
      by a per-month key through `NotificationService.deliver`.
  - The copy states its partial scope: Study Pack generation only, excluding note generation from topics
    (added in #1511), template seeding, summary regeneration and repair, with a pointer to the billing
    dashboard. No model is named.
  - The cached-input rate falls back to the input rate, which overstates cost. Reasoning tokens are not
    added, because `output_tokens` already includes them.
  - **Known limits (corrected by the pre-signoff pass; the earlier "$45 bulk regeneration" and "a repair
    counts the original tokens" examples were wrong):**
    - Summary regeneration and repair do **not** advance `updated_at`, so they are not counted at all.
    - Share-link creation, owner reassignment, and title, subject and tag edits **do** advance it, and
      re-count a pack's original tokens (over-count).
    - An ADMIN remix copies the source pack's tokens into a new pack (over-count, with no LLM call behind it).
    - Tokens from failed or retried attempts are never persisted (under-count).
    - The month is UTC.
  - Verified:
    - `CurationCostAlertJobTest` 8/8, plus the notification dedup integration test.
    - The full suite with Docker. The 7 Docker-dependent classes failed during a disk-full Docker outage
      and all passed on rerun, including `NativeQueryPostgresIntegrationTest` at 116/116.
    - The query, run read-only against production, returned 10 admin packs this month.
  - Release Implementor fixed the new native test's fixture: notes for the fixture packs, and a past-month
    window for isolation. No migration, and no frontend change (notification `type` is a plain string).
- **Item 2a: tier routing for Study Pack generation and pins for every interactive path (PR #1509,
  `96a46d78`; owner A1–A3).**
  - `LlmModelRouter` resolves the model from an explicit route; a missing route throws.
    - **TIERED:** FREE → `LLM_MODEL_FREE`; Plus/Pro → `LLM_MODEL_PREMIUM`, from the expiry-aware
      `SubscriptionService` snapshot; `UserRole.ADMIN` on any plan → premium. A missing or deleted tier user
      routes as FREE with a warning.
    - **Routed paths:** Study Pack async and synchronous (requester); summary regeneration and the
      malformed-quiz repair (pack owner, resolved at repair time); note-from-topic, single and bulk; the
      Official template seed (note owner).
  - **Pinned for every plan:**
    - `LLM_MODEL_INTERACTIVE_QUIZ` (`gpt-4.1-mini`): Challenge start, +5, multi-note, Board Exam, Adaptive,
      study tip, teacher quiz, Long Exam and the exam pools.
    - `LLM_MODEL_PAID_INTERACTIVE` (`gpt-4.1`): Interview quiz and the companion.
    - `LLM_MODEL_CRITIQUE` is untouched.
  - **Metadata:** one tier snapshot per generation sets the model sent and `model_tier`, keeping the
    PLUS||PRO rule. `model_used` falls back to the model sent. A repair keeps the original metadata (known
    limitation).
  - **Logging:** a structured log line per HTTP attempt (route, model, tier, latency, attempt, outcome, tokens
    including reasoning). No prompt, body or headers.
  - **Defaults:** `application.yaml` now defaults to `gpt-6-luna` / `gpt-6.1-sol`. Render sets
    `LLM_MODEL_FREE` / `LLM_MODEL_PREMIUM`. **[CORRECTED: the merge to `main` DOES change a model.** Plus/Pro
    and ADMIN Study Pack paths move from `gpt-4.1-mini` to Render's `LLM_MODEL_PREMIUM=gpt-4.1`. See the
    correction under Planned Scope item 3.]
  - **Verified:**
    - Tests assert the model in every outgoing request body, per route and for every pinned method (Long
      Exam halves, salvage, fallback and consistency retry included). Pins hold when the tier keys change.
      Each caller's route is tested.
    - A cold Opus falsification pass found no claim wrong. It found five gaps, which Codex fixed and Release
      Implementor verified in the code: the null-route fallback, the Board Exam route test, body-level pin
      tests, log outcome labels, and upstream error detail. Release Implementor also added a safeguard so a
      deferred log line cannot be overwritten.
    - `./mvnw install` with Docker: 2,770 tests, 0 failures, including `NativeQueryPostgresIntegrationTest`
      116/116. Feature docs are updated; no migration.
  - **Known:** an email-only Official author would route FREE. In production that email is the single ADMIN
    account.
- **Item 2c: Try again on the `/study` Study Pack error card (PR #1510, `f1a5c244`; owner A5).**
  - A failed synchronous generation now offers a deliberate retry. It goes through the existing guarded
    handler (disabled while loading or redirecting), never retries on its own, and makes no time promise.
  - **The note-editor half of 2c needed no change.** Note-from-topic already meets A5: an inline error and a
    toast, the topic kept, and "Generate Note" re-enabled (`note-editor-page-client.tsx` `handleGenerateNote`).
  - Frontend suite 2,669 passed; tsc and eslint clean.
- **Pre-signoff fixes (PR #1511, `fa474931`), from the cold falsification pass:**
  - **Try again ran the wrong operation after a confirm-text failure.** It re-uploaded the image, spent OCR
    quota and overwrote the learner's edited text.
    - It now shows only after a retryable generation failure (a `retryable` flag in `useStudyPack`).
    - It is hidden while confirmation is pending and after the signed-out error.
    - Tests cover each case.
  - The cost-alert copy now also excludes note generation from topics. The feature docs list every
    over-count and under-count.
  - `StudySnapPropertiesTest` binds the production YAML price map.
  - Backend 2,771 tests with Docker; frontend 2,672.

### Signoff record (2026-10-19)

**Scope completeness, item by item:**
1. **Benchmark: shipped**: #1506, #1507, and both owner-run runs (about $5.37 of the $10 cap).
2. **Routing: shipped as CHANGED by owner A1.** Tier routing covers the Study Pack family only. Every
   interactive path is pinned to today's model. Challenge routing moves to v0.175.0. Delivered as #1509,
   #1510 and #1511.
3. **Defaults: shipped in code** (`application.yaml`: Luna/Sol defaults plus two pins). **The production switch
   is the owner's post-merge env change.** The Long Exam pool reduction is **deferred to v0.175.0** (A4).
4. **Cost alert: shipped**: #1508 and #1511.

**Verification:**
- Tier H. Cold Opus falsification on 2a (five gaps, fixed). A release-wide cold pass at pre-signoff found
  three confirmed issues, fixed in #1511, plus one wrong production-safety claim, corrected above.
- Final backend build: 2,771 tests with Docker, 0 failures. Frontend: 2,672 passed.

**Known limitations, recorded and not fixed:**
- No test calls `CurationCostAlertJob.run()` through its `@Scheduled` entry or `findAdminUserIds` directly.
  The cron and zone are asserted by `ScheduledJobCronContractTest`, and the job logic by `runAt`.
- An email-only Official author would route FREE. Today that email is the single ADMIN account.
- A repaired pack keeps its original `model_used` / `model_tier`.
- The cost estimate's over-count and under-count cases are listed in item 4.

**⚠️ MERGE-DAY HANDOFF. Every step is an owner action unless marked; follow it in order:**
1. **#1505 (v0.173.0) merges with a merge commit, after its 2026-10-22 signal-9 read.** Then the v0.173.0
   cache deploy + 7 days read.
2. **Only then, open the `releases/v0.174.0` → `main` PR.** Opening it earlier would show v0.173.0's commits
   in its diff.
3. Merge it. Wait at least 5 minutes, then run `scripts/check-deploys.sh` (exit 0 = both platforms serve
   `main`).
   - **From this deploy until step 4, Plus/Pro and ADMIN Study Pack paths run on `gpt-4.1`** (Render's current
     `LLM_MODEL_PREMIUM`).
4. **Immediately set on Render: `LLM_MODEL_FREE=gpt-6-luna` and `LLM_MODEL_PREMIUM=gpt-6.1-sol`.** Do not
   remove the overrides. This is a second redeploy: record both deploy times, because they matter for the
   v0.173.0 memory-kill and cache reads. Wait, then run `check-deploys.sh` again.
5. **First-use check** (read-only, Release Implementor can run it):
   - the next ADMIN Study Pack's `model_used` starts with `gpt-6.1-sol`;
   - the next FREE pack's starts with `gpt-6-luna`;
   - a new Challenge session's `model_used` is still `gpt-4.1-mini`;
   - Render logs show the structured per-call line.
6. **Rollback to pre-release behaviour:** `LLM_MODEL_FREE=gpt-4.1-mini` and `LLM_MODEL_PREMIUM=gpt-4.1-mini`.
   Interview Practice and the companion stay on their own pin. No data rewrite is needed.

**Checkpoint:** `[CHECKPOINT — due env switch + 14 days]` in `ROADMAP.md`. The pin-retirement checkpoint is
due 2026-12-15.
## v0.173.0 - Cached Catalog

**Status: Released** (signed off 2026-10-16; **merged to `main` as #1505 (`ff2ea964`) on 2026-10-25 after the 2026-10-22 gate read (partial); the first deploy failed on a database kill, and the cache has been live since 2026-10-25T01:47:11Z**. Kicked off 2026-10-15 on `releases/v0.173.0`, cut from `docs/v0.172.0-deploy-stamp`
(`3961383e`), which is `main` after `v0.172.0` merged as #1501 plus one docs commit.)

Theme: take the remaining repeat load off the production database's Explore discovery reads by
caching the copies/likes/views aggregates in-process for a short TTL, so most page views stop
recomputing them at all.

**Why this release exists.** `v0.172.0` made each discovery call compute the three engagement
aggregates once instead of once per section. The fix plan's durable follow-up is to stop computing
them on every call: an in-process Caffeine cache, TTL 60–300 s
(`docs/claude-plans/2026-10-14-db-oom-memory-ceiling-fix-plan.md`, Leg D "Durable follow-up"). The
owner deferred it to this release on 2026-10-14.

**⚠️ MERGE GATE: the release PR merges to `main` only AFTER the `[CHECKPOINT — due 2026-10-22]` read.**
- That checkpoint measures `v0.172.0` (Leg D) plus Leg B with **no** cache, over 7 days from the
  2026-10-15T07:09:09Z deploy. Shipping the cache inside that window would make the read unattributable.
- Every `main` merge also triggers a Render deploy, and the deploy double-pool overlap is itself a
  known kill mechanism. So nothing merges to `main` before 2026-10-22T07:09Z.
- **The read can redirect this release.** ≥ 5 kills: Leg A (plan upgrade) becomes the next lever and
  the cache alone is not enough; the owner decides whether this release still ships. 2–4: proceed as
  planned. 0–1: the checkpoint is silent on the cache; owner call after the read.
- Work can be built, audited and merged into `releases/v0.173.0` before then.

### Planned Scope

1. **Discovery ranking cache (backend, Tier H, Codex). ✅ Merged #1503 (`70fdd019`), 2026-10-15; see Shipped.**
   - **Tier N → H (2026-10-15, before the brief):** single-flight under concurrent load is the charter's concurrency trigger, and a waiter holding a JDBC connection under open-in-view would reproduce this method's own leak traces. A scoped cold falsification pass is owed at pre-signoff. Brief: `docs/codex-prompts/v0.173.0-discovery-ranking-cache.md` (no stale-on-error: `expireAfterWrite` 300 s keeps Product UX's bound).
   - Add Caffeine and cache the copies/likes/views aggregates behind
     `NoteService.getPublicLibraryDiscoverySections` for a short TTL (fix plan: 60–300 s).
   - **Product UX decision, 2026-10-15 (APPROVED with safeguards; consultation prompt
     `docs/claude-plans/done/2026-10-15-discovery-cache-product-ux-consultation-prompt.md`):**
     - **Cache the ranked id lists of all three sections (Featured, Most Popular, Recently Added) as
       one coherent snapshot, TTL 300 s.** Existing ranking and exclusion semantics are preserved.
       Card counts and the viewer's liked state stay live per request, and are never cached.
     - **No early invalidation**: not on publish, unpublish, copy, like or view.
     - **The per-request `visibility = 'PUBLIC'` re-check stays on every response, cache hits
       included.** A private or deleted note drops out at once, even if a section then shows fewer
       than 6 cards.
     - No user-facing freshness notice, and no promise that publishing lands a note in Recently Added
       (it orders by creation date, which this release must not change).
     - **Safeguards to verify:** concurrent misses must not each run the ranking query
       (single-flight). A failing refresh must not cause a retry storm. Cold-start, expiry and
       failure behaviour must be tested under concurrency. No per-copy or per-like invalidation, and
       no expensive per-request backfill.
     - **Post-deploy:** compare the 7-day signal-9 count against the baseline of 9. Add bounded
       instrumentation for ranking computations, hit/miss, refresh failures, ranking duration and
       short (< 6) sections.
     - **Why the copy-count concern is gone:** counts are loaded per request by
       `NoteService.toListItems`, not by the ranking, so #1500's in-page freshness is unaffected.
   - **Re-read 2026-10-15, read-only:** backend `numInstances` = 1 (Render `get_service`), so an
     in-memory cache is consistent across visitors.
   - Guard (fix plan): a test asserting the aggregate computation runs at most once per TTL window
     across repeated `getPublicLibraryDiscoverySections` calls, plus a real-request test if any
     endpoint changes.
2. **Carried docs commit (no code). `[merged-to-release: 3961383e]`** Stamps the `v0.172.0`
   production deploy time (2026-10-15T07:09:09Z) into the `ROADMAP.md` Backlog rows. It rides in via
   this release instead of PR #1502, so it does not trigger its own deploy mid-checkpoint window.

Anti-drift:
- **Caffeine is the one allowed new dependency.** No Redis, no HTTP cache headers, no Next.js
  `revalidate` change.
- No Hikari pool-size change (pinned by the 09-04 outage fix and
  `TomcatThreadPoolHikariAlignmentTest`).
- No change to FEATURED/POPULAR weights, decay, thresholds, or the paginated listing's SQL (pinned by
  the 14 golden SQL files from #1498).
- No checkout/payment path (RC-4 baseline) and no public-note/adoption path (RC-5 read).
- No migration.

**Open at kickoff, not scoped:**
- `[CHECKPOINT — due 2026-10-22]` (signal-9 kills; read by 2026-10-29 at the latest, Render log
  retention). RC-3 checkpoint due 2026-10-21.
- Overdue checkpoint reads carried from `v0.172.0`: `v0.107.0`, `v0.90.0`, `v0.106.0`, `v0.97.0` (due
  10-13); `v0.111.0`, `v0.96.0` (09-28); the `v0.74.0` conversion read (09-30).
- The `v0.166.0` Hikari checkpoint row's title still reads open, though the `v0.171.0` kickoff read
  it (cell contradiction, not re-read here).
- Owner: re-run the admin Official Challenge backfill seed for note `0c6b0be8`.
- Workflow redesign: `v0.172.0` ran two Tier-N items through the new worktree lifecycle, but no M5
  pilot measurement was recorded. D4/D9 stay gated on it.

### Shipped

1. **Explore discovery rankings cached for 300 s** — #1503 (`70fdd019`), Tier H.
   - `PublicLibraryDiscoveryRankingCache` holds one viewer-independent snapshot of the Featured /
     Popular / Recent id lists. It is a Caffeine `AsyncLoadingCache` (`:51-55`): the 300 s TTL counts
     from completion, concurrent misses share one computation and its failure, and a failure is not
     cached.
   - The ranking runs on `discoveryRankingTaskExecutor` (`AppConfig.java:35`) in its own read-only
     transaction. `NoteService.getPublicLibraryDiscoverySections` (`:940-942`) is `NOT_SUPPORTED`, so
     waiting requests hold no JDBC connection.
   - Card projections, counts, the `visibility = 'PUBLIC'` re-check and the viewer's liked state run on
     every request. There is no early invalidation and no stale-on-error. Repository SQL, ranking
     formulas, the pool and migrations are unchanged; the one new dependency is Caffeine 3.2.3, from
     the Boot BOM.
   - Instrumentation:
     - `DISCOVERY_RANKING_CACHE computed` (INFO): `durationMs`, the three list sizes, cumulative
       hits/misses/failures, and `shortSectionResponses`. The line emits; it appeared 13 times in the
       full build.
     - `DISCOVERY_RANKING_CACHE failed` (WARN).
     - A request that joins a ranking still in flight counts as a **hit**: in a cold-start burst of 8,
       7 show as hits.
   - Verification:
     - Full `./mvnw clean install` with Docker: 2,694 tests, 0 failures.
     - `concurrentHttpDiscoveryWaitersDoNotHoldJdbcConnections` (real HTTP, 8 concurrent requests,
       ≤1 active connection) fails with **9** when the method is reverted to `readOnly = true`. That
       mutation was run by Release Implementor.
     - `inFlightRankingSurvivesTtlAndExpiresFromCompletion` fails with 2 rankings under insertion-time
       expiry. That mutation was run by Codex; the test was read by Release Implementor.
     - Two defects in Codex pass 1 were confirmed and fixed in fix-1: the TTL started at insertion, and
       the connection guard bypassed the real request path.
   - **Known gaps (as merged in #1503; the first two are closed by item 2):**
     - No test asserts that `shortSectionResponses` rises.
     - The HTTP test's identical-body check is trivial, because every section is stubbed empty.
     - ~~`future.join()` has no timeout. That is no worse than the previous per-request query.~~
       **Corrected 2026-10-16: this was wrong.** The old query failed alone. Here a stalled ranking left an
       in-flight future that never expired, and every request pinned a Tomcat thread (25 total) on it.
       Health checks starved, and Explore stayed frozen until restart. The cold pass found it (C8), and
       item 2 fixes it.
   - The Tier-H cold falsification pass (Opus, read-only) ran on 2026-10-15. It found no blockers;
     its findings are resolved under item 2.

2. **Stalled rankings bounded and the cache hardened** — #1504 (`ff555530`), Tier H, Codex at high
   effort (D10).
   - **C8 (stall):** the ranking transaction has a 50 s statement timeout
     (`PublicLibraryDiscoveryRankingCache.java:40`, via `TransactionTemplate.setTimeout`). The loader
     future has a 55 s `orTimeout` backstop (`:41`). Both are under the 60 s Hikari leak threshold.
     - 50/55 rather than the 30/45 that was built: before `v0.172.0`, this endpoint held connections over
       60 s three times during CPU spikes. A shorter cancel risks a cancel-and-retry loop with no backoff.
       The owner chose this on 2026-10-16.
     - A timed-out ranking is not cached, so the next request retries.
   - **Rejection:** when the queue of 16 is full, the load fails at once and reports one failure, with
     no orphan timer. A queued load whose future already timed out skips the ranking.
   - **Accounting (both Codex-review P2s):**
     - The success line and the `shortSectionResponses` reset happen only when `complete()` delivers
       the snapshot.
     - The counter is captured before publishing and added back if the snapshot is not delivered. That
       last race can't be reproduced by a test, so this fix is unverified.
     - Failures, including `Error`, are counted once per load.
   - **Missing decorator:** `InFlightThreadRegisteringTaskDecorator` is now set on
     `discoveryRankingTaskExecutor`, pinned by
     `AppConfigTest.discoveryRankingExecutorIsBoundedAbortOnOverflowAndRegistered`.
   - **C10 (test gaps):**
     - The pagination fixture now has 7 notes, so a backfill would be detected, and it asserts
       `shortSectionResponses`.
     - The cold test is now `coldRequestsShareOneRanking`; its "no connection" half could not fail, so
       that claim was dropped there and is left to the HTTP test.
     - The HTTP test now sends one authenticated (JWT) request.
   - The inert `invalidateAll` call in the pagination test was removed.
   - **New tests:**
     - `PublicLibraryDiscoveryTimeoutPostgresIntegrationTest.lockedNativeRankingIsCancelledServerSideAndNextRequestRetries`
       (Docker): an `ACCESS EXCLUSIVE` lock on `notes` checks the server-side cancel, the worker being
       released, and recovery after unlock.
     - Unit tests for a late result after the wait timeout, a rejected submission, a timed-out queued
       load, and a failure logged once.
   - **Verification:**
     - Full `./mvnw clean install` with Docker: 2,701 tests, 0 failures, at 30/45 and again at 50/55.
     - Mutations run by Release Implementor:
       - Removing the transaction timeout fails the lock test at "each caller sees PostgreSQL
         cancellation before the 5 s backstop".
       - Removing both bounds now fails in about 7 s. Before fix-1, the test hung instead.
       - Removing `readOnly` gives 9 connections against 1 in the HTTP test.
     - Codex killed the complete-guard, rejection-handling and decorator mutations.
   - **D10 cost (gpt-6-sol), as 5h-window / weekly `used_percent`:**

     | Run | Effort | 5h window | Weekly |
     |---|---|---|---|
     | Hardening pass | high | 49→72 | 8→11 |
     | Review | medium | +2 | 11→12 |
     | Fix-1 | high | 0→7 (after the 5h reset) | 12→13 |

     That is two D10 runs this week on one item, and the owner was told.
   - **Known limitations (cold-pass notes, not fixed):**
     - **A dead socket can't be cancelled.** The worker stays stuck until restart. Each request fails
       after 55 s, and requests fail at once once the queue of 16 fills. Tomcat threads stay free, so
       health checks don't restart the service, but Explore is down until restart.
     - **C2:** for a few microseconds after a failed ranking, a request can join the failed future.
       Retries have no backoff.
     - **C7 (log emitters):**
       - One failure also emits Caffeine's own WARNING and one `unhandled_exception` ERROR per waiting
         request. Count failures from `DISCOVERY_RANKING_CACHE failed` only.
       - Joining an in-flight ranking counts as a hit.
       - `shortSectionResponses` resets after each delivered computation.
     - The HTTP test's identical-body check is still trivial.
     - **C11:** the doc-wording notes were folded into `docs/features/public-library.md`.

3. **Owner action: four residual pre-H4 answer-key defects repaired in `study_packs.quiz`** — 2026-10-16,
   run by the owner with psql.
   - Source: `docs/claude-findings/2026-10-15-quiz-quality-retention-llm-cost-production-read.md` §2
     (Feature Planner / Prod Investigator).
   - Script: `docs/claude-plans/done/2026-10-16-study-packs-residual-answer-key-defects-repair-tightened.sql`.
     It is the Release Implementor's tightened copy of the Prod Investigator's
     `done/2026-10-15-…-repair.sql`. It adds a question-text match (D3's choices are generic), keeps array
     order (`WITH ORDINALITY … ORDER BY`), and runs as one transaction with checks that abort it.
     Before production, it was tested in a throwaway local Postgres, with a decoy question sharing
     D3's choices and wrong index.
   - Corrections:
     - D1 audit detection risk: 0.06/(0.4×0.5) = 0.3, so idx 3 → 0.
     - D2 labor efficiency variance: (120−100)×$15 = $300 unfavorable, so idx 0 → 2.
     - D3 weighted average cost: $10,000/500 = $20, so idx 2 → 1.
     - D4 straight-line depreciation: ($50,000−$5,000)/9 = $5,000, so idx 3 → 0, on the source and
       both public-note copies.
   - Owner run: `UPDATE 1/1/1/3`, the check block passed ("6 packs corrected"), and `COMMIT`.
   - **Verified read-only by Release Implementor, after commit:**
     - In each of the 6 packs, only the targeted question's `correctIndex` changed, compared with a
       per-question read taken before the repair.
     - Question order is unchanged, and every quiz still has 5 questions.
     - `quiz_stamp` (0) and `updated_at` are unchanged, so no Quick Review mastery was reset.
     - A whole-table recount finds 0 wrong keys for all four defects, and 3 corrected D4 copies.
   - Exposure: one incomplete owner test session existed on these packs. The real learner's D4 copy
     had no sessions.
   - Not repaired, by design: the APGAR, percentage-tax and grain-size candidates were re-derived as
     correctly keyed (false positives).

### Signoff (2026-10-16)

- **Signed off ahead of the merge, owner decision 2026-10-16**, so `v0.174.0` can be cut from this
  branch. The merge gate is unchanged: the release PR merges to `main` only after the
  `[CHECKPOINT — due 2026-10-22]` signal-9 read (by 2026-10-29 at the latest). If that read shows
  ≥ 5 kills, the owner decides whether this release still ships, and any change made here is merged
  forward into `v0.174.0`.
- **Interim reads at signoff (read-only, NOT the verdict):**
  - 0 `terminated by signal 9` lines from 2026-10-15T07:09:09Z to 2026-10-16T03:14Z. The filter was
    checked against the known 07:08:54Z kill.
  - 1 `Apparent connection leak` trace since the `v0.172.0` deploy: 2026-10-15T14:25:32Z on
    `PublicProfileController.getByUsername`, returned to the pool about 70 s after acquire. That is a
    `v0.166.0` method, not discovery, and is recorded on its checkpoint row.
- **Scope completeness:**
  - Item 1 shipped: #1503 plus the #1504 hardening, file:line anchors under Shipped.
  - Item 2 shipped: the carried deploy-time stamp `3961383e` is on this branch.
  - Item 3 is an owner action, done and verified.
  - Nothing was planned and left unbuilt.
- **Pressure test:** one scoped cold falsification agent (Opus, read-only), Tier H for concurrency.
  It found no blockers; every finding is fixed or listed as a known limitation under item 2.
- **Feature-doc drift:** `docs/features/public-library.md`'s cache paragraph was re-read against the
  final `PublicLibraryDiscoveryRankingCache`. These all match:
  - the 50 s / 55 s bounds (`:40-41`);
  - a failure is not cached;
  - immediate rejection when the queue is full;
  - no viewer id;
  - no backfill;
  - the log and counter semantics.
- **Backlog:**
  - The "Production DB memory ceiling" and discovery long-hold `[DECISION]` rows are updated: cache
    built, deploy held.
  - The `v0.166.0` Hikari checkpoint row carries the `getByUsername` recurrence.
  - A new `[CHECKPOINT]` row covers the cache itself, dated from its deploy.

## v0.172.0 - Headroom

**Status: Released** (signed off 2026-10-15 on `releases/v0.172.0`; kicked off 2026-10-14, cut from
`main` after `v0.171.0` merged as #1497. PRs #1498, #1499, #1500.)

Theme: stop the production database being pushed past its 268 MB memory ceiling. The cheapest
already-diagnosed code legs land here: stop the Explore discovery sections computing the same
engagement aggregates twice per call, and stop them re-fetching on every tab switch. Close the
Challenge `+5` duplicate-question HTTP 500 alongside them.

**Why this release exists.** At this kickoff, the database I/O bursts the Backlog Index attributed to
deploys were traced to the database itself. `notelib-db-prod` (`basic_256mb`) had **9 backend kills by
signal 9 in 7 days** (2026-10-08 → 10-14), each followed by a full Postgres crash-recovery that drops
every in-flight request. Daily memory peaks hit 251–268 MB against a 268,435,460-byte limit on **every
day from 09-15 to 10-14**: a long-standing undersizing, not a regression. Prod Investigator root-caused
two mechanisms (`docs/claude-findings/2026-10-14-db-oom-memory-ceiling-public-catalog.md`; fix plan at
`docs/claude-plans/2026-10-14-db-oom-memory-ceiling-fix-plan.md`):
- **Deploy overlap.** The old instance keeps its 20-connection pool open 39 s–2 min after the new
  instance is live. All 4 deploy-adjacent kills fit this, but the overlap is not sufficient alone:
  10-12 had the same overlap with no kill.
- **CPU-saturated public-catalog reads.** These precede most, not all, non-deploy kills.

Both are made worse by a memory floor sitting within ~45 MB of the ceiling.

**Owner decisions, 2026-10-14:**
- **Leg A (plan upgrade): not now.** There are no paying users yet.
- **Leg B: yes**, as an owner `SET`.
- **Leg D: yes**, as a single-statement rewrite, not Java-side ranking, because Java-side ranking
  would duplicate the FEATURED/POPULAR scoring formula that the paginated Public Library listing
  keeps in SQL.
- **Durable aggregate cache: next release.**
- **Legs C, E, F: not scoped.**

### Planned Scope

1. **Discovery sections: compute the engagement aggregates once (Leg D, Option 1), and stop
   re-fetching on discovery-overview re-entry (backend + frontend, Tier H, Codex). `[shipped: #1498 4871be7e + follow-up #1500 fc7da866]`**
   - **Re-tiered N → H (2026-10-15):** delivery introduced defects that this session then fixed, which is the charter's Tier H trigger. Pass 1 sorted the whole public catalog twice with ~2,437-byte rows (prod `EXPLAIN`, and Codex's own review). Pass 2 moved `n.content` into a 1,290-byte-wide hash join inside the ranking subquery (prod `EXPLAIN`). Pass 1's H2 oracle tie-check also sorted UUIDs in Java (signed `compareTo`), so it passed or failed depending on the random ids; Release Implementor rewrote that one assertion as `subList(0, 2).containsExactlyInAnyOrder(...)`. The oracle comparison itself is unchanged. A scoped cold falsification pass is owed at pre-signoff (it can be batched with item 2). The paginated listing's golden SQL files were verified against the pre-refactor code (`fcfae82b`) in a throwaway worktree.
   - **Backend.** `NoteService.getPublicLibraryDiscoverySections` (`NoteService.java:953-960`) runs
     the ranked query separately for `FEATURED` and `POPULAR`. Each run builds the
     `RANK_COPIES_JOIN`/`RANK_LIKES_JOIN`/`RANK_VIEWS_JOIN` aggregates from scratch
     (`PublicLibraryRepositoryImpl.java:534-564`). Rewrite it so the three aggregates are computed
     once per call and both rankings read from them. `POPULAR` must still exclude `FEATURED`'s
     returned IDs. Same results, same ordering, same thresholds.
   - **One scoring definition.** The paginated listing (`NoteService.java:829`, `:915`) keeps using
     the existing fragments, so there must be exactly one definition of the scoring formula.
   - **Frontend.** `public-library-page-client.tsx:648-675` re-requests discovery sections each time
     discovery overview is re-entered (`?view=` round-trip, filter cleared; Explore only `hidden`-toggles the tab, so its tab switch does not remount). Reuse the already-loaded response instead.
   - **Guards.**
     - A test asserting the aggregate computation runs at most once per call.
     - The rewritten SQL covered by `NativeQueryPostgresIntegrationTest`.
     - A result-equivalence test against the old two-query output on seeded data.
2. **Challenge `+5`: duplicate bank question no longer 500s (backend, Tier N, Codex). `[shipped: #1499 89f4edad]`**
   - **Audit (2026-10-15).** Both bank writers (`persistGeneratedQuestions`, which also serves
     `seedTemplateAsync`, and `copyTemplateQuestions`) now go through one native
     `insertIfAbsent … on conflict do nothing`. A skipped question is still served in its session but
     is not banked or claimed for it. Real-PostgreSQL guards: same key twice at the same level, at a
     NULL level and at a different level; a two-connection race; `+5` with a pre-existing duplicate;
     bank rows present after start, `+5`, seed and copy; and a jsonb round-trip through the entity.
     Removing `on conflict do nothing` fails three tests by name, including the end-to-end `+5` test.
     Removing `flushAutomatically` fails nothing, so the start-path row-existence test guards the
     outcome but does not prove the unflushed-session state was reproduced; the flag stays as a
     harmless safeguard. The three claim queries gained `cast(:learnerLevel as string)`, which changes
     no behaviour (verified by reverting it). It is needed only because `NativeQueryParameterTypingTest`
     scans whole files and flags JPQL `:param is null` once a file holds a native query; narrowing
     that lint is a follow-up. Full `./mvnw clean install` with Docker: 2685 tests, 0 failures.
   - **The defect.** `ChallengeQuizQuestionBankService` de-duplicates new bank rows only within the
     incoming batch (`:101-106`), never against rows already in the bank. A same-level repeat of
     `question_key` therefore hits `uq_challenge_quiz_question_bank_user_pack_key` and rolls back the
     request as HTTP 500. This is documented as a known limitation in `docs/features/challenge-quiz.md:119`.
   - **The fix.** Make the insert itself skip duplicates, for example `ON CONFLICT DO NOTHING` on that
     key, so a concurrent `+5` or `seedTemplateAsync` cannot race a pre-read. The exact mechanism is
     settled in the brief.
   - **Guard.** A real-PostgreSQL test inserting the same key twice.
   - **Evidence.** No occurrence in the 7 retained days of logs, so this is a latent-defect fix, not
     an active incident.

**Owner action, outside the code release:** Leg B,
`ALTER DATABASE notelib_db_prod SET max_parallel_workers_per_gather = 0;`. The statement, expected
effect and verification are handed over in `docs/claude-plans/done/2026-10-14-db-leg-b-parallel-workers.sql`.
It only applies to new sessions, so it is fully live after the next deploy cycles the pool.
**Applied by the owner 2026-10-15** (before ~03:04Z). Verified read-only at signoff (05:39Z):
`pg_db_role_setting` holds `max_parallel_workers_per_gather=0` for `notelib_db_prod`, a new connection
reads `0` (source `database`), and all 20 app connections opened at or after 05:14Z, after the `ALTER`.
The owner's IntelliJ console kept reading `1` because its connection predated the `ALTER` and
"detach session" reuses it.

**Kill criterion, stated before any leg lands:** count `*terminated by signal 9*` lines on
`dpg-d6tvb8fkijhs73fda4m0-a` over 7 days after the release deploys and Leg B is applied. **Baseline: 9.**

Anti-drift:
- No Hikari `maximum-pool-size` or `minimum-idle` change. The pool size is pinned by the 2026-09-04
  outage fix and `TomcatThreadPoolHikariAlignmentTest`.
- No change to the FEATURED/POPULAR scoring weights, decay, thresholds or section limits. No change
  to the paginated Public Library listing's results.
- No new cache and no new dependency (the Caffeine cache is next release).
- No checkout, payment or pricing path, so RC-4's baseline window is untouched. No public-note-page
  or adoption-path change, so RC-5's read is untouched.
- No migration.
- No `statement_timeout`.

### Shipped

1. **Discovery sections compute the engagement aggregates once (Leg D), and stop re-fetching on
   overview re-entry** — #1498 (`4871be7e`), Tier H.
   - `PublicLibraryRepositoryImpl.findPublicLibraryFeaturedAndPopularIds` (`:314`) ranks Featured and
     Popular in one statement. Each of the copies/likes/views aggregates appears once. A narrow
     `featured_ids` CTE keeps `n.content`/`sp.summary`/`sp.quiz` in scan filters only. Each section
     filters to eligible notes, then `ORDER BY … LIMIT`. Called from `NoteService.java:955`; RECENT
     keeps its own query and still excludes both.
   - One scoring definition, shared with the paginated listing via `rankedOrderKeys`/`rankScoreSql`.
     14 golden files pin the paginated page/count SQL. The pin was verified against the pre-refactor
     code.
   - Prod `EXPLAIN` (read-only, planner estimate): old pair ~25,300 total cost, widest node 48 bytes;
     final ~18,600, widest node 64 bytes. Two earlier Codex passes were rejected for wide sorts and
     joins (see the re-tier note in Planned Scope).
   - Frontend: `discoverySectionsLoadedRef` (`public-library-page-client.tsx:552`) reuses a successful
     load across overview re-entry. Retry always fetches, and an error resets the flag.
2. **Copy counts stay current on the reused overview** — #1500 (`fc7da866`), a follow-up to item 1
   that the cold falsification pass found. Reusing the sections froze `copyCount` at first load.
   `handleCopySuccess` (`public-library-page-client.tsx:774`) now adds one on the source card in the
   browse list and all three sections. It skips a re-copy (the backend returns the existing copy) and
   an owner's own copy, which is never counted. Copies recorded during the mount are merged over the
   copied-notes load (`:510`), so an in-flight load cannot erase them. Each of the four guards is
   mutation-checked against a named test.
3. **Challenge bank duplicates are skipped at insert instead of returning HTTP 500** — #1499
   (`89f4edad`).
   - Both bank writers route through `ChallengeQuizQuestionBankRepository.insertIfAbsent`
     (`:31`, `on conflict do nothing` at `:29`), called from `ChallengeQuizQuestionBankService.java:133`
     and `OfficialChallengeQuizTemplateService.java:257`.
   - A skipped question is still served but not banked or claimed for that session
     (`challenge-quiz.md` "Per-user question bank").
   - Real-PostgreSQL guards cover same, NULL and different level; a two-connection race; `+5` with a
     pre-existing duplicate; rows persisted on every path; and a jsonb round-trip.

**Pre-signoff pressure test: one scoped cold falsification agent (Opus), Tier H trigger.** Leg D's
delivery introduced defects that the same session fixed. The agent tested 6 Leg D claims, 7 Item 2
claims and 2 release-wide sweeps, using mutation testing in a throwaway worktree. **No code defect was
found in either PR.** Confirmed findings and their disposition:
- Frozen copy counts on the reused overview: **fixed in #1500**.
- Stale Backlog rows (`v0.81.0` checkpoint, "Isolate Challenge bank writes properly"): **corrected in
  this signoff**.
- Two imprecise sentences in `challenge-quiz.md` (the seed path's transaction; the shortfall's dedup
  scope): **corrected in this signoff**.
- An unpinned flush flag and narrow golden coverage: recorded below.

**Checkpoint:** `[CHECKPOINT — due 2026-10-22]` in `ROADMAP.md`'s Backlog Index carries the signal-9
kill criterion (baseline 9 in 7 days).

**Owner note, outside this release's scope:** the Official Challenge template seed for note
`0c6b0be8-8b7d-41dc-9619-94cab1d52064` (pack `3deae879-…`) failed at 2026-10-13T03:32:35Z. A read-only
log read at signoff traced it to an LLM output-validation failure: `AppException: Challenge quiz
generation returned an invalid quiz format` from `validateGeneratedQuizItem`, after its one retry. It
was **not** a duplicate-key violation, so it is not evidence against item 2 being latent. The pack has
**0** bank rows and the note is `PUBLIC`, so the admin backfill seed will pick it up if re-run.

### Known Limitations

- **Shared-then-public copy count:** a learner who copied a note while it was privately shared, and
  then copies it again after it goes public, reuses the old copy. That copy isn't counted publicly
  (`copied_from_public = false`), but the card shows +1 until reload. Closing this needs the backend to
  say whether a copy is new.
- **`@Modifying(flushAutomatically = true)` on `insertIfAbsent` is not pinned by any test.** Removing it
  fails nothing, most likely because Hibernate already flushes before native DML. The start-path test
  proves bank rows land, but not that an unflushed session row was ever reproduced.
- **The paginated golden files pin only the H2 dialect with empty criteria.** The PostgreSQL branch of
  `rankScoreSql`, which #1498 edited, is unchanged by reading but not byte-pinned. Discovery's filter
  parameters are oracle-tested on H2 only; production discovery always sends empty criteria.
- **No end-to-end duplicate test on the start or template-copy paths.** They share `insertIfAbsent`
  with the tested `+5` path, so they are covered by construction only.
- **Non-duplicate bank write failures** (foreign-key, check-constraint or 512-character `question_key`
  overflow) still poison the caller's transaction, as before.
- **`NativeQueryParameterTypingTest` scans whole files**, so it flagged the JPQL `:learnerLevel is null`
  in the bank repository once that file held a native query. The three claim queries now use
  `cast(:learnerLevel as string)`, which is behaviour-neutral (verified on PG with a NULL level).
  Narrowing the lint to native-query blocks is a follow-up.
