# Challenge Quiz quality evaluation: pre-registration (v0.177.0)

Status: **DRAFT, NOT YET PRE-REGISTERED.** It becomes the pre-registration when the run sheet is read, the owner says "freeze" and the file is committed **before the first paid command**. Anything later found wrong is reported as a protocol deviation, never fixed silently. This file records only values fixed before any generated result exists. It authorizes **no paid call by an agent, no production prompt change and no deployment**; the owner runs every paid command with their own key, under a **$4 maximum** (Product UX, 2026-10-28: a ceiling, not an authorization).

Specification: `docs/claude-plans/2026-10-28-challenge-quiz-quality-scoring-rubric-v2.md` (signed by the owner 2026-10-28). Scorer instructions: `docs/claude-plans/2026-10-27-challenge-quiz-quality-scorer-instructions.md` (frozen 2026-10-27, unchanged). The only gate changed from `v0.176.0` is **G5**; G2 (mean at least 4.0 and at least +0.3) and every other gate are as in `v0.176.0`.

## 1. Pinned documents and calculator

| Item | Value |
|---|---|
| Rubric v2 SHA-256 | `3e52c815b09e623f23255f91abd7cf9fbaef954b1356b2eca6d63c30dc14e6f3` (any later edit voids this pre-registration) |
| Scorer-instructions SHA-256 (same as `v0.176.0`) | `62311fa47458c7fd234ffccb7a75db9d57ab03f4dfcca3484ab63662248fafa0` |
| Frozen `v0.176.0` rubric SHA-256 (the record, not edited) | `be465df1e7623ea9e9701449bb500a1760aadb3be6545dce51d0ece2e8d5d261` |
| Git tree hash of `backend/src/test/java/com/studysnap/backend/benchmark` after both PRs (#1539 `deba8e46`, #1540 `9c669442`, merged 2026-10-28) | `6d9fccb1b8a8a1f11ece74c4a764cea7b1ac1f1b` (resources tree `backend/src/test/resources/benchmark`: `8330dc9490c8ebfc1208c924034604d60ac34109`) |
| Rubric version passed to the calculator | `v2`, explicit, no default |
| Proposed-prompt arms B2 and B3: **Variant G+** (owner decision 2026-10-28, `2026-10-28-v0177-item2-gplus-decision.md`): production prompt plus exactly the five approved Change B bullets, no Change A, no Change C. Resource `backend/src/test/resources/benchmark/challenge-quality/challenge-quiz-developer.gplus.txt` | SHA-256 `019c5bdf9f868c0aa206490a345b91020e588c1afe993f8930ecd1eed1029f16`; git blob `9efa83a86068c40f276f47a15059ac0a849897c3`; added by PR #1540 (merge pending; a merge does not change the blob, so this is re-confirmed after the merge); guarded by `ChallengeQualityGPlusPromptGuardTest` (7 tests) |
| Output root for the `v0.177.0` run | `backend/target/llm-benchmark-v0177/` (a **fresh, empty** root; the run sheet's `blindOutDir`, `reviewOutDir`, scores, reviews, adjudications, descriptors and gates paths all sit under it; nothing may be written to `backend/target/llm-benchmark/`, which holds the `v0.176.0` evaluation, backed up at `~/quality-eval-v0176-backup`) |
| SHA-256 of the production prompt `challenge-quiz-developer.txt` (the current-prompt arms) | `5bf4a8e9c93aa3644811c96a302d5c078d88f72e2b442656c2627991539a3fe7`, re-read 2026-10-28 and unchanged since `v0.176.0`; pinned by both guard tests; re-read again at freeze |
| Release-branch commit the hashes are taken at | `9c669442` (`releases/v0.177.0` after PRs #1539 and #1540); the G+ blob, G+ SHA-256, production SHA-256 and the pinned proposed blob were re-read on that tip and match this table. Re-confirmed again at freeze. |
| Blind-export seed and review-order seed (`-Dllm.benchmark.blindSeed`, `-Dllm.benchmark.reviewSeed`) | **177** for both (a fresh draw; `v0.176.0` used 176) |

**Reproducing `v0.176.0`.** The `v0.176.0` evaluation is reproduced with its recorded benchmark tree hash `8643dc740e8de3f476304e7e9072aebe327ad465` and the calculator at rubric version `v1`, whose output must be identical to the recorded `v0.176.0` gate report. **That evaluation is never rescored under v2.** "Identical" in the rubric (section 5, G5 bullet) is read here as **equal parsed JSON and the same Markdown content**, not equal bytes: the calculator builds its JSON with `Map.of`, whose member order differs between JVM runs, and the recorded `v0.176.0` report has the same property. The replay of `v0.176.0` under `v1` into a scratch folder was checked this way on 2026-10-28 and matched; the rubric text is signed and hash-pinned, so this reading is recorded here instead of editing it.

**Item naming.** The signed rubric calls the proposed-prompt arms "the giveaway-only variant of scope item 2". The owner chose Variant G+ for that item (Change B only), so "the proposed prompt" in this evaluation means the G+ file above; no gate depends on the name.

## 2. Design (unchanged from `v0.176.0`, owner, 2026-10-28)

- The same 20 Official notes (design A: 10 `COLLEGE` and 10 `BOARD_EXAM_REVIEW`, each at least 4 strata), the same five generation arms plus the Official stock arm: A1 `gpt-4.1-mini`, A2 Luna, A3 Sol on the **current** prompt; **B2 Luna and B3 Sol on the G+ prompt** (Change B's five bullets only, without Change A and Change C; the evaluation asks whether Change B alone improves quality over the current production prompt, with no attribution to individual bullets). All `hard`, one fresh 15-question start per note per arm, bank off. **The note set is the `v0.176.0` selection, frozen in the input files** `quality-e.jsonl` (SHA-256 `f2dad548bbeeb2f85f97f28a339034ff0cbde12af94e39b63111fe9e77522759`, 20 notes) and `quality-stock.jsonl` (SHA-256 `434fcb6a5f4bf245336a04538eb4ddd46a7301f9ce42ca303005c7713f8cfeea`, 100 stock questions), copied into the fresh root, so the new arms are comparable with the recorded baselines.
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

- **Done and pinned above:** the G5 calculator and the G+ resource are merged (#1539, #1540) with their hashes, the benchmark tree hash, the seed (177), the note set and the output root.
- **Run sheet:** `docs/claude-plans/2026-10-28-v0177-run-sheet.md` (generated by substitution from the harness README: fresh root, the G+ file, seed 177, `rubricVersion=v2`, the same ten spend stops totalling $3.61 with a worst case of about $3.95 inside the $4.00 ceiling) is a DRAFT and needs the owner's read.
- **Freeze:** once the run sheet is read, the owner says "freeze", the file's Status line is changed to PRE-REGISTERED with the commit hash, and that commit is made **before** the first paid command. Until then no paid command is authorized.
