# Challenge Quiz quality evaluation: pre-registration (v0.177.0)

Status: **DRAFT, NOT YET PRE-REGISTERED.** It becomes the pre-registration when every "PENDING" cell below is filled and the file is committed **before the first paid command**. Anything later found wrong is reported as a protocol deviation, never fixed silently. This file records only values fixed before any generated result exists. It authorizes **no paid call by an agent, no production prompt change and no deployment**; the owner runs every paid command with their own key, under a **$4 maximum** (Product UX, 2026-10-28: a ceiling, not an authorization).

Specification: `docs/claude-plans/2026-10-28-challenge-quiz-quality-scoring-rubric-v2.md` (signed by the owner 2026-10-28). Scorer instructions: `docs/claude-plans/2026-10-27-challenge-quiz-quality-scorer-instructions.md` (frozen 2026-10-27, unchanged). The only gate changed from `v0.176.0` is **G5**; G2 (mean at least 4.0 and at least +0.3) and every other gate are as in `v0.176.0`.

## 1. Pinned documents and calculator

| Item | Value |
|---|---|
| Rubric v2 SHA-256 | `3e52c815b09e623f23255f91abd7cf9fbaef954b1356b2eca6d63c30dc14e6f3` (any later edit voids this pre-registration) |
| Scorer-instructions SHA-256 (same as `v0.176.0`) | `62311fa47458c7fd234ffccb7a75db9d57ab03f4dfcca3484ab63662248fafa0` |
| Frozen `v0.176.0` rubric SHA-256 (the record, not edited) | `be465df1e7623ea9e9701449bb500a1760aadb3be6545dce51d0ece2e8d5d261` |
| Git tree hash of `backend/src/test/java/com/studysnap/backend/benchmark` after the G5 calculator PR | PENDING |
| Rubric version passed to the calculator | `v2`, explicit, no default |
| Giveaway-only prompt (the proposed-prompt arms B2 and B3): resource path and git blob hash | PENDING (scope item 2; the exact wording goes to the owner before the brief) |
| SHA-256 of the production prompt `challenge-quiz-developer.txt` (the current-prompt arms) | PENDING, re-read at freeze (`v0.176.0`: `5bf4a8e9c93aa3644811c96a302d5c078d88f72e2b442656c2627991539a3fe7`) |
| Release-branch commit the hashes are taken at | PENDING |
| Review-order seed (`-Dllm.benchmark.reviewSeed`) | PENDING (not reused from `v0.176.0`'s `176` unless stated) |

**Reproducing `v0.176.0`.** The `v0.176.0` evaluation is reproduced with its recorded benchmark tree hash `8643dc740e8de3f476304e7e9072aebe327ad465` and the calculator at rubric version `v1`, whose output must be identical to the recorded `v0.176.0` gate report. **That evaluation is never rescored under v2.**

## 2. Design (unchanged from `v0.176.0`, owner, 2026-10-28)

- The same 20 Official notes (design A: 10 `COLLEGE` and 10 `BOARD_EXAM_REVIEW`, each at least 4 strata), the same five generation arms plus the Official stock arm: A1 `gpt-4.1-mini`, A2 Luna, A3 Sol on the **current** prompt; **B2 Luna and B3 Sol on the giveaway-only prompt** (not the `v0.176.0` Changes A, B and C wording). All `hard`, one fresh 15-question start per note per arm, bank off. The note set, selection and strata: PENDING (reuse the `v0.176.0` selection, or state why not).
- Luna and Sol are judged separately and **both must pass**; an incomplete, failed or inconclusive result is HOLD.
- **Cap: $4 maximum** for the owner-run generation, with explicit spend stops per command written in the README run sheet before the run. `v0.176.0`'s run sheet totalled $3.61 (smoke 0.33 plus full 3.28) for the same arms. The cap is a ceiling; the owner runs and decides.

## 3. Whole-arm re-review of the proposed arms (pre-registered, owner, 2026-10-28)

The rubric (section 2, rule 5) already requires that if a cold reviewer finds even one confirmed critical defect that the primary scorer missed in a **proposed-prompt arm (B2 or B3)**, **that entire arm** is independently re-reviewed by a fresh cold reviewer, with a separate adjudication, before its gates are interpreted. `v0.176.0` Amendment 4b then staged that re-review by cost and skipped it for Luna because Sol's result had already decided HOLD, which left Luna's proposed arm uninterpreted.

For `v0.177.0`:
1. **Amendment 4b's skip is withdrawn.** The whole-arm re-review of **each** proposed arm is run whenever the rubric's trigger fires for that arm, **whatever the other model's result is.** A model's arm is never left uninterpreted to save cost.
2. **Scope and mechanism:** all about 300 items of the arm, one fresh reviewer identity for the whole arm, the same scratch-directory isolation, self-report and receipt checks as `v0.176.0` Amendment 3, with new disagreements adjudicated by fresh adjudicators and the harness's own validator accepting every row.
3. **Cost, stated before the run:** up to about 300 items per proposed arm, so up to about 600 items if both triggers fire (about 6 reviewer passes of about 100). That is reviewer usage on the owner's Claude and Codex plans, not OpenAI API spend, and it is not part of the $4 cap.
4. **If the owner wants the re-review run unconditionally rather than on trigger, that is a different pre-registration and has not been chosen.** This section is the on-trigger reading of "pre-register the re-review"; the owner may rule otherwise before the freeze.

## 4. Reporting commitments (G5 and disclosure)

The report prints, per model, every format's status (PASS, FAIL or NOT_EVALUATED) with `n` and both means for distractor plausibility and mode alignment, each format's count and share of scored items in both arms, and the owner-review flag in the model's top-level reasons. A NOT_EVALUATED format is never counted as a pass. Per-level results stay descriptive. Luna's current-arm baseline (distractors 3.23, mode alignment 3.36, giveaway estimate 10.5%) is restated in the report as context, never as a gate input.

## 5. Not yet fixed (each must be filled before the freeze)

The G5 calculator PR merged to the release branch and its tree hash; the giveaway-only prompt wording approved by the owner, then its resource file and blob hash; the note selection; the review seed; the README run sheet with per-command spend stops under $4; the release-branch commit the hashes were taken at.
