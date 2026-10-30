# Challenge Quiz quality evaluation: pre-registration (v0.176.0)

Status: **PRE-REGISTERED 2026-10-27, committed before the first paid command.** Anything below that is later found to be wrong is reported as a protocol deviation, never fixed silently. This file records only values fixed **before any generated result exists**. It authorizes **no paid call by an agent, no production prompt change and no deployment**; the owner runs every paid command with their own key.

Specification: `docs/claude-plans/2026-10-27-challenge-quiz-quality-scoring-rubric.md` (signed 2026-10-27). Scorer instructions: `docs/claude-plans/2026-10-27-challenge-quiz-quality-scorer-instructions.md` (frozen 2026-10-27).

## 1. Pinned documents and calculator

| Item | Value |
|---|---|
| Rubric SHA-256 (committed content at `a5b40481`) | `be465df1e7623ea9e9701449bb500a1760aadb3be6545dce51d0ece2e8d5d261` |
| Scorer-instructions SHA-256 (same commit) | `62311fa47458c7fd234ffccb7a75db9d57ab03f4dfcca3484ab63662248fafa0` |
| Git tree hash of `backend/src/test/java/com/studysnap/backend/benchmark` | `8643dc740e8de3f476304e7e9072aebe327ad465` |
| Git blob hash of `backend/src/test/resources/benchmark/challenge-quality/challenge-quiz-developer.proposed.txt` | `352bc91b1fb3fa2e90420f72f9963041e042a3df` |
| SHA-256 of the production prompt `challenge-quiz-developer.txt` (the current-prompt arms) | `5bf4a8e9c93aa3644811c96a302d5c078d88f72e2b442656c2627991539a3fe7` |
| Release-branch commit the hashes were taken at | `a5b40481ea5513eb438ffe144002e8388d3411eb` (`releases/v0.176.0`, includes the merged harness, PR #1536 as `10f3c2e4`) |
| Review-order seed (`-Dllm.benchmark.reviewSeed`) | `176` |

The calculator hashes are content hashes, so they survive any merge style. The tests of the calculator reproduce the rubric's G4 illustrations and Appendix A cells, and pass at the commit above.

## 2. The sample

`backend/target/llm-benchmark/quality-e.jsonl` (git-ignored; produced by the read-only `sample-query-quality.sql`, 20 lines).

- **SHA-256:** `f2dad548bbeeb2f85f97f28a339034ff0cbde12af94e39b63111fe9e77522759`
- Design A: 10 `COLLEGE` + 10 `BOARD_EXAM_REVIEW` Official notes by each note's own level; cell S15 (15 questions, `hard`, production constants). Validated: 20 unique pack ids, 12 strata by label (target at least 6; at least 4 per level), 4 quantitative notes (2 per level; target at least 2 per level), `officialOwner` true on every line.
- **Disclosed composition:** 5 of the 10 `COLLEGE` notes are `ACCOUNTANCY`; the two `BOARD_EXAM_REVIEW` mathematics notes carry the strata labels `ENGINEERING_MATHEMATICS` and `Engineering Mathematics`, which count as two strata by label. Both follow from the approved selection rule and are reported, not corrected. With many single-note strata, the rubric's stratum trigger (section 5) is sensitive, as already disclosed to the owner.

| Run | Level | Stratum | Quant. | Pack id |
|---:|---|---|:---:|---|
| 1 | COLLEGE | ENGINEERING_SCIENCES |  | f6211756-084e-4395-b935-564f5a2e5614 |
| 2 | COLLEGE | ACCOUNTANCY |  | 2200ff42-77d3-477c-902c-9952e39c7fd7 |
| 3 | COLLEGE | PROFESSIONAL_PRACTICE_AND_REGULATION |  | a90badee-eccc-4433-9df3-3a93956c8b2a |
| 4 | BOARD_EXAM_REVIEW | Reinforced Concrete Design |  | a6fc4593-6afa-454e-8512-5a50c0206d55 |
| 5 | BOARD_EXAM_REVIEW | Maternal and Child Nursing |  | 317d9868-b29b-4493-98f1-7c4bd6434f36 |
| 6 | COLLEGE | ACCOUNTANCY |  | 01740435-a99a-4d89-82cd-552dee77486c |
| 7 | COLLEGE | ACCOUNTANCY |  | 27d408d0-5d1f-45fb-931a-731b8a9f5ebc |
| 8 | COLLEGE | ACCOUNTANCY |  | 126551b2-3fff-4d6d-8e6f-6306dfd40d6a |
| 9 | COLLEGE | CIVIL_ENGINEERING |  | 28cfe651-31ce-4894-a178-f3496a7c319d |
| 10 | COLLEGE | ACCOUNTANCY | Q | 8409b384-5b8c-4a58-a702-17731599595f |
| 11 | COLLEGE | COMPUTING |  | 6cb1d13a-4313-4ff5-8a5b-1932123a9f79 |
| 12 | BOARD_EXAM_REVIEW | CIVIL_ENGINEERING |  | 93917bdf-fd57-4679-9692-08ea15ae3136 |
| 13 | BOARD_EXAM_REVIEW | Medical – Surgical Nursing |  | ea63cde2-6e50-4b8b-9cce-72c09b8d79c2 |
| 14 | COLLEGE | ENGINEERING_SCIENCES | Q | 1ac5921b-1222-40e1-8188-2c3ce63a2923 |
| 15 | BOARD_EXAM_REVIEW | ENGINEERING_SCIENCES |  | d10cd467-9c08-4cd2-a0c4-1d5fca080406 |
| 16 | BOARD_EXAM_REVIEW | Pediatric Nursing |  | 4f16abc4-db6d-4d51-a888-7a0974969fc9 |
| 17 | BOARD_EXAM_REVIEW | PROFESSIONAL_EDUCATION |  | eec90e22-e5d7-417d-b17e-9ae7aa37e89f |
| 18 | BOARD_EXAM_REVIEW | PROFESSIONAL_EDUCATION |  | f31c58f0-8618-45b2-9efc-552abaffaf08 |
| 19 | BOARD_EXAM_REVIEW | ENGINEERING_MATHEMATICS | Q | 477632a0-71a0-434d-98c0-fcbe7630fe61 |
| 20 | BOARD_EXAM_REVIEW | Engineering Mathematics | Q | d787f871-b94f-4fdb-b98b-b93869727983 |

**Stock arm (about 100 Official template questions, 5 per pack):** read-only count check run 2026-10-27 through the Render Postgres tool inside a read-only transaction, same NULL-or-current generation-stamp claimability rule as `sample-query-stock.sql`: **all 20 packs have 20 claimable ADMIN template rows (the minimum is 5)**. The stock export itself is run by the owner and must give 100 lines.

## 3. Arms, order and spend (unchanged from the README run sheet)

Five generation arms on the 20 notes: A1 `gpt-4.1-mini`, A2 Luna, A3 Sol on the current prompt; B2 Luna, B3 Sol on the proposed prompt; plus the stock arm. Luna and Sol are judged separately, each proposed arm against its same-model current arm on the paired notes. Order A2 then B2, A3 then B3, A1 anywhere. Hard cap **$4**; per-start spend stops as in the README (smoke total 0.33, full total 3.28, sum 3.61); the meter checks before every call. A spend-stopped or failed start is a missing side of a pair, reported, **never re-run or replaced**.

## 4. Still to be added, in order, before scoring begins

1. After generation and the offline export: the review-order manifest SHA-256 and the per-arm counts, as an addendum to this file, **before any scoring starts**.
2. Scoring then follows the rubric (Codex read-only in a scratch directory outside the repository; cold Opus independent review of about 100 items per pass; adjudication; whole-arm re-review if a proposed arm has a missed critical defect; gate report). The default outcome is HOLD.

## 5. Amendments and deviations

Each entry is dated and states its reason. Nothing above is edited.

**Amendment 1, 2026-10-27, made after the smoke pass and before any full-run result exists (owner approved):** the two Sol **full-run** spend stops (A3 and B3) are raised from $1.40 to **$1.55**. All other stops are unchanged (smoke 0.03, 0.03, 0.12, 0.12, 0.03; full A2 0.15, B2 0.15, A1 0.18).
- **Evidence:** the smoke pass (all five starts succeeded, 15 of 15 delivered) measured the meter's conservative cost at $0.0764 for A3 and $0.0696 for B3 per start. The meter counts reasoning tokens twice, so real money is about 12% lower. The stop is checked before each call, so A3 completes all 19 full-run notes only if its first 18 average under $0.0778, a 2% margin measured on one note.
- **Why it matters:** the last full-run note (run 20, `Engineering Mathematics`) is alone in its stratum. A3 stopping before it would leave that stratum without a paired note and make Sol INCONCLUSIVE under the rubric's stratum trigger, so the stop, not the prompt, would decide the verdict.
- **Cap:** worst case is $0.158 smoke spend (conservative figure) + $3.58 full stops + about $0.19 overshoot (one final Sol call at most $0.08 per Sol command, about $0.01 per other command) = about **$3.93**, inside the **$4.00** cap. Real money is lower because the meter's figure is conservative.
- **Rule kept:** a spend-stopped or failed start is still a missing side of a pair, reported and **never re-run or replaced**; no stop is raised after any full-run result exists.
- **Timing, stated plainly:** the owner approved the amendment, the run script was edited (local time 16:12:46) and this text was drafted (16:12:54) **before** the first full arm started (A2, 16:13:15), but the owner ran the full pass **before this file was committed**. The git record of the amendment therefore post-dates the run; the decision and the on-disk draft did not. The committed text differs from the draft only by this bullet and the next.
- **In hindsight the amendment was not needed:** the full run finished with no spend stop in any arm (A3 conservative $1.318 and B3 $1.290 for 19 notes, both under the original $1.40). The amendment changed no outcome.

No other deviation is recorded at the time of writing.

## 6. Addendum A: generation record and review plan (2026-10-27, after generation and the offline export, before any scoring)

Fulfils section 4 item 1. Nothing above is edited. No score, review or gate result exists when this is written; the review plan uses no scores.

**Generation (owner-run, with Amendment 1's stops):** five arms x 20 notes = 100 starts, **all successful**, 15 of 15 questions delivered each, validator telemetry captured on every start, no spend stop, no timeout, no verifier run. Meter total about $3.03 (smoke $0.158 + full $2.876, conservative figure) against the $4.00 cap. Descriptive only: validator rejections and retries (equal by construction) of 300 requested per arm: A2 0, B2 0, A3 0, B3 1, A1 4; one discarded attempt (A1, one note). Both models have 20 paired notes and the descriptors' missingness verdict is CLEAR for both.

**Offline export:** 1,600 blind items (300 per generated arm plus 100 stock), 16 chunks of up to 100; item ids unique and equal to the private key's; no item field names the arm, model, level, prompt hash, pack or generation time. The blind-key, notes, results, review order and manifests stay private under `backend/target/llm-benchmark/` and are never shown to a scorer.

**Review plan:** seed `176`; review-order manifest SHA-256 **`34c1dcad2a0305ecd8aed03f9a6f6e967ab425be2f777ab201fd05f19a898d0d`** (equal to the SHA-256 of `review-order.jsonl`). Choice-based items per arm in the order: A1 280, A2 274, A3 274, B2 268, B3 280, stock 88; the seeded 20% sample per arm: A1 56, A2 55, A3 55, B2 54, B3 56, stock 18 (the review-batch step tops each generated arm up to the signed floor of 60 unflagged items).

**Content hashes of the data the scorers will see or the calculator will read (SHA-256):**

| File | SHA-256 |
|---|---|
| `blind-items-001..016.jsonl` concatenated in order | `8f530ce520862af1649de7ac0fa4d22f4ec0ab9e77beed52c609178449e28e6e` |
| `notes.jsonl` | `4a31f63c743c2d304e8fee5b2e7c0d01b0b6a1bf7e5ea85fefd7feb37c4130c4` |
| `blind-key.jsonl` (private) | `631038e7d6fe1212912558582753eec13e9c6ddd78bdaded8c3ca3a8d4c236f1` |
| `quality-stock.jsonl` (100 lines) | `434fcb6a5f4bf245336a04538eb4ddd46a7301f9ce42ca303005c7713f8cfeea` |
| `descriptors.json` | `d4fa7da06d3cbd3feac49691206370800b0294bbcb358c0d8bfe4fc206f2de81` |
| `review-manifest.json` | `a22f3a3ee532523828a44a91b70ab57f439ec0ea19850cf267b7ccd73f8b79fd` |

`results.jsonl` per arm (smoke / full):

| Arm | Smoke | Full |
|---|---|---|
| A1 | `4304f1dc9b8f2066a7cb30c6e25370761a42a75026d5fb27f4db3b8cb6405130` | `2853faee597ba08a3304b97aaea0dfe208cd06e29bb583d6520d156f25aac80d` |
| A2 | `74be6722add55f4685a312762ce63a81e5ff223afd4de6df9f06b686075e173e` | `d39bce23d40028a23ccb870e6f1fb766ecd04405adf181a0b41e373ad6857a58` |
| A3 | `0cb9eeec97cacf2e667acf03072cb4985c74663b6e163a04dc3638dc761d1bed` | `9e6345fb0474f9554cf58f2e19bb52771693e58620129406ea512fcd2122d30f` |
| B2 | `0cfc8a1970faad5ae8f3c7b0bfbe141875179d7562e02709fa2755dfa71ecaef` | `266bc03517d385733540c733515593a0831e70f511650e93b5a5650baed8f5da` |
| B3 | `749d01ce74cd181aee61fd7d0a0329a02674adc38f438b69e2a1f257c23612f3` | `d46d2e144637b946d279296b02e34f3f99799b719122c29972fb0c7e1007e215` |

The calculator tree hash was re-read after the run and is unchanged (`8643dc740e8de3f476304e7e9072aebe327ad465`).

## 7. Addendum B: primary scoring record (2026-10-27, after primary scoring, before the Opus batches are built)

Nothing above is edited. No independent review, adjudication or gate result exists when this is written.

**Run (owner-run, `codex exec -s read-only`, `codex-cli 0.158.0`, Codex's default model, not pinned by the script):** 16 chunks of 100 items, one fresh `--ephemeral` session per chunk, run sequentially, 2026-10-27 13:08:56Z to 13:43:04Z. The prompt was sections 1 to 6 of the frozen scorer instructions verbatim (SHA-256 of the instructions `62311fa4...`, verified before staging) plus one pointer line naming the chunk file and `notes.jsonl`; SHA-256 of the prompt body `b9ab123d58e30ac423eb11537a2838a5f7ceaed4fc4ff0657682637f5fdeece4`. The scratch directory held only the 16 chunks and `notes.jsonl`, outside the repository, with hashes checked against Addendum A before staging.

**Isolation audit, and one correction to the audit rule itself.** Each chunk's full transcript is scanned for the key, results, review order, manifests, descriptors, the repository path, `.env` and any other chunk. The first merge voided all 16 chunks on a single word: the rule listed the bare substring `manifest`, which matched **"clinical manifestations"** in a nursing note's text, not a file. No other forbidden word appeared in any transcript. The audit rule was narrowed to the actual manifest file names (`review-manifest`, `blind-manifest`, `batch-manifest`, `-manifest.json`) and the merge re-run; it then found no hit. The correction was checked independently: across all 52 distinct command patterns in the 16 transcripts, **every command read only its own `blind-items-NNN.jsonl` and `notes.jsonl`**, and none named any other file. No chunk was re-run and no output was edited.

**Result:** 1,600 of 1,600 items scored, one row each, ids equal to the private key's, every row valid against the schema and enums (checked independently of the harness), the reviewer id `codex-1`, the round `PRIMARY`. Primary flags, **unconfirmed**: 72 critical, 65 serious giveaway, 137 items in total, each with a one-sentence note in the private notes stream. Flags are not findings; the independent review decides what is confirmed.

| File (private) | SHA-256 |
|---|---|
| `scores-codex.jsonl` (frozen here) | `02ea34e56ae7af07685a87c2ae3147de99935844733ea31673cdcb65b4340e3c` |
| `quality-primary/notes-codex.jsonl` | `072e4914d9a97ceae1f3d7e4a564d46d89fbec051078e3f3bada1149d1e03040` |
| `quality-primary/prompt-body.txt` | `b9ab123d58e30ac423eb11537a2838a5f7ceaed4fc4ff0657682637f5fdeece4` |

Next, in order: the review-batch step builds the Opus batches from this frozen file (every flagged item plus the stratified unflagged sample, topped up to the signed floor of 60 per generated arm), then independent cold review, adjudication and the gate report.

## 8. Amendment 2 and a correction to Addendum B (2026-10-27, after PR #1537 merged, before the Opus batches are built and before any independent review)

Nothing above is edited. No independent review, adjudication or gate result exists when this is written.

**Correction to Addendum B.** Section 7 says every primary row was "valid against the schema and enums (checked independently of the harness)". **That was wrong.** The check did not apply the harness's rule that a choice-based item must carry a 1 to 5 distractor score and a boolean serious-giveaway flag. The review-batch step, which uses the harness's validator, aborted on **two rows, `blind-0235` and `blind-1429`, both arm A1**, so the claim was false for 2 of 1,600 rows. The primary scores file is unchanged and still has SHA-256 `02ea34e56ae7af07685a87c2ae3147de99935844733ea31673cdcb65b4340e3c`; no row was repaired, edited or re-scored.

**Instruction violation, not ambiguity.** Both items carry `questionFormat` `MCQ`. The frozen scorer instructions (section 5, SHA-256 `62311fa47458c7fd234ffccb7a75db9d57ab03f4dfcca3484ab63662248fafa0`) state that choice-based means standard multiple-choice, multi-select, assertion-style and matching items, so the three choice-only fields had to be filled. Codex flagged both items critical (a wrong key on a matching-style prompt delivered as MCQ) and then returned `null` for those fields, which breaks the instructions under either reading of the format.

**Effect under the signed rubric (section 5, denominators and missing data).** An invalid primary row makes that arm incompletely scored. The arm is A1, the reference arm, which no gate uses: Luna is A2 against B2 and Sol is A3 against B3. Neither model's gates are affected. The two items are flagged and go to independent review. A1 is reported incompletely scored.

**Amendment 2a: the calculator changed after it was pinned.** The batch builder aborted on an invalid primary row, which contradicts the signed rubric, so `QualityReviewBatches` was fixed (PR #1537, merged as `448c4dc8ed9d683a41dd237cec97a53f0843056f`, fix commit `9ed856090ff1f8e9c3ec25b7bd92d35258937b1d`). An invalid primary row is now flagged for independent review at the same point in the order loop where a valid flagged row is marked, is never sampled or topped up, and its own `critical` field is never read. The reason value stays `FLAGGED`; `invalidPrimaryIds` and `invalidPrimaryArms` appear in the batch manifest only when invalid rows exist, so all-valid output is byte-identical to before. A missing primary row, mismatched ids or a non-PRIMARY round still abort. Test scope only, nothing under `backend/src/main` changed. The change was implemented by Codex from a brief, then audited: the full offline suite passes, five mutants are each killed by a named test, and `backend/target/llm-benchmark` had the same content fingerprint before and after.
- **New benchmark tree hash (supersedes section 1's `8643dc74...`): `f5f20006c3a2ecf705460bc2366e82f664a5ab14`**, at release-branch commit `448c4dc8ed9d683a41dd237cec97a53f0843056f`. Four files differ from the previously pinned tree: `QualityReviewBatches.java`, the new `QualityReviewBatchesTest.java`, `QualityGateIntegrationTest.java` and `README.md`.
- Unchanged and re-read after the merge: proposed prompt blob `352bc91b1fb3fa2e90420f72f9963041e042a3df`, rubric SHA-256 `be465df1e7623ea9e9701449bb500a1760aadb3be6545dce51d0ece2e8d5d261`, scorer-instructions SHA-256 `62311fa47458c7fd234ffccb7a75db9d57ab03f4dfcca3484ab63662248fafa0`, the frozen scores file, and every data hash in sections 6 and 7.
- A dry run of the fixed builder on the real frozen scores into a scratch directory outside the repository, deleted afterwards, gave 5 batches and 497 items (137 flagged, 294 sampled, 66 topped up), both invalid rows flagged, and every generated arm at the signed floor of 60 unflagged items.

**Amendment 2b: how a returned reviewer batch is accepted (pre-declared before any review exists).** A test on 2026-10-27 showed that the gate report's loader rejects an invalid review row in a gated arm (B2) but **silently accepts the same invalid row in A1**, because rows of ungated arms and of the stock arm never go through it. So the gate report is not an acceptance check. Instead, **every returned batch of independent-review, whole-arm re-review and adjudication rows is checked on receipt with the harness's own validator** (`QualityScoreFiles.validate`, with `QualityScoreFiles.choice` taken from the private key), on **every row of every arm**, plus: the expected round, the expected reviewer id, exactly the ids of the batch (no missing, no extra, no duplicate). The checker below does this; it is run from outside the repository against the compiled harness classes and is **not** part of the pinned calculator. **An invalid pass is discarded whole and re-run by a fresh reviewer; the original is kept on record, never edited or partly used.** If a fresh reviewer also fails on the same batch, the failure is reported to the owner before anything else is done. Parity was checked: on the real primary scores the checker reports exactly the two rows the batch builder found and none other, and on synthetic batches it rejects an invalid row in A1 and in B2, a missing row and a wrong round, and accepts a valid batch. No hand-written schema check is used for acceptance from here on.

Checker source (SHA-256 `98a6cc037e60c121065ceccd4dc7dcc8fc49414379197f43d49ba14cef6ab53b`, kept in `~/quality-eval-v0176-backup/receipt-check/ReceiptCheck.java`):

```java
package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Receipt check for a returned scoring batch. It calls the harness's OWN validator
 * (QualityScoreFiles.validate, with QualityScoreFiles.choice from the private key) on EVERY row of EVERY arm,
 * which the gate report's loader does not do for ungated arms (A1, stock).
 * usage: ReceiptCheck <blind-key.jsonl> <returned rows .jsonl> <expectedRound> <expectedReviewerId> <batch .jsonl (ids expected)>
 * exit 0 = accepted, exit 1 = REJECT (whole pass is discarded and re-run by a fresh reviewer; the original is kept on record).
 */
public final class ReceiptCheck {
    public static void main(String[] args) throws Exception {
        Map<String, JsonNode> keys = QualityScoreFiles.keyed(Path.of(args[0]));
        List<JsonNode> rows = QualityScoreFiles.lines(Path.of(args[1]));
        String round = args[2], reviewer = args[3];
        Set<String> expected = new HashSet<>();
        for (JsonNode item : QualityScoreFiles.lines(Path.of(args[4]))) expected.add(item.path("id").asText());
        List<String> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode row : rows) {
            String id = row.path("id").asText("");
            if (!seen.add(id)) problems.add("duplicate id " + id);
            JsonNode key = keys.get(id);
            if (key == null) { problems.add("id not in key " + id); continue; }
            if (!expected.contains(id)) problems.add("id not in this batch " + id);
            try {
                QualityScoreFiles.validate(row, QualityScoreFiles.choice(key));
                if (!round.equals(row.path("round").asText())) problems.add(id + ": round is not " + round);
                if (!reviewer.equals(row.path("reviewerId").asText())) problems.add(id + ": reviewerId is not " + reviewer);
            } catch (IllegalArgumentException invalid) {
                problems.add(id + ": " + invalid.getMessage());
            }
        }
        for (String id : expected) if (!seen.contains(id)) problems.add("missing " + id);
        System.out.println("rows " + rows.size() + ", expected " + expected.size() + ", problems " + problems.size());
        for (String p : problems.stream().limit(20).toList()) System.out.println("  - " + p);
        System.exit(problems.isEmpty() ? 0 : 1);
    }
}
```

Next, in order: build the Opus batches from the frozen primary scores with the merged code, then independent cold review with every returned batch checked on receipt, adjudication, and the gate report.

## 9. Amendment 3: independent reviewers read a scratch directory, not inline text (2026-10-27, owner-approved, after Amendment 2 and before any independent review exists)

Nothing above is edited. This changes **only the isolation mechanism** in the signed rubric (section 4 item 6, "inline in the prompt, with no file paths"). It changes no gate, threshold, definition, sampling or instruction text.

**Why.** Inline delivery means the orchestrator reads each 41k-token batch into its own context and writes it back out in the agent call: about 80k tokens of orchestrator context per batch and about 400k for five, on a limited budget, and a hand transcription of about 76 KB of item text per batch that cannot be verified against the file the agent received. A scratch directory delivers byte-exact content that is hash-checked.

**Mechanism.** One directory per reviewer under `~/quality-review-v0176/r1` to `r5`, outside the repository and with no repository name in its path. Each holds only that reviewer's batch file and `notes.jsonl`, copied byte for byte and verified by hash before any agent runs; the staged directories were scanned for the key, results, scores, hashes, arm and repository names and nothing was found.

| Reviewer | Batch file | SHA-256 |
|---|---|---|
| `opus-1` | `opus-batch-001.jsonl` (100 items) | `b9bdb6a67ec9f4712a4655bf2bc321b895880950b95c283943581fbee176d941` |
| `opus-2` | `opus-batch-002.jsonl` (100 items) | `9b0447813573909ebe65fe2e227d597cbe9aa9dbc433b072645b67d5e07a83c8` |
| `opus-3` | `opus-batch-003.jsonl` (100 items) | `3c335e63cb99f13eb40b6b4e87b24048f2080dcf7357281cd9dde66a1812508b` |
| `opus-4` | `opus-batch-004.jsonl` (100 items) | `0137165eef843ab1909405a7f2991eb5e53fcbc73aec602476abad621b5c4db8` |
| `opus-5` | `opus-batch-005.jsonl` (97 items) | `2a941430e7e0dfd912c0c13cb31e8350380e2c8cf579e5334e62be05aff047ad` |

`notes.jsonl` in every directory has SHA-256 `4a31f63c743c2d304e8fee5b2e7c0d01b0b6a1bf7e5ea85fefd7feb37c4130c4` (the pinned file from Addendum A).

**Each reviewer** is a fresh Opus agent of the general-purpose type, with no prior context, no primary scores and no earlier review. Its prompt is sections 1 to 6 of the frozen scorer instructions verbatim (prompt-body SHA-256 `b9ab123d58e30ac423eb11537a2838a5f7ceaed4fc4ff0657682637f5fdeece4`, the same text the primary scorer received) plus orchestrator lines that add only: the absolute path of its own directory and the instruction to read only its batch file and `notes.jsonl`; the instruction to write its complete answer, exactly as section 6 specifies and nothing else, to `review-00N.txt` in that directory (so the answer is not copied through the orchestrator's context); and the instruction that its final chat message list every file it opened or wrote. Reviewer ids are `opus-1` to `opus-5`, round `INDEPENDENT_REVIEW`, added by the orchestrator, not by the reviewer.

**What this does not give, stated plainly.** The primary pass had a full command transcript that was audited. An agent's commands cannot be audited that way, and an agent with file tools can in principle read anywhere on the disk. The substitutes are weaker: the agent's own report of every file it touched, and the tool-use count its result shows. **A pass whose report names any file other than its own batch, `notes.jsonl` and its own output file, or whose report is missing, is void and is re-run by a fresh reviewer; the original is kept on record.** Adjudicators and whole-arm re-reviewers use the same mechanism with their own directories, with the two earlier judgements labelled "Reviewer 1" and "Reviewer 2" as the scorer-instructions notes require.

**Receipt.** The orchestrator extracts each `review-00N.txt`, adds `reviewerId` and `round`, and runs the receipt checker of Amendment 2b on every row of every arm before the pass is accepted.

## 10. Addendum C and Amendment 4: independent review record, adjudication design and the staged B2 escalation (2026-10-27, after the independent review, before any adjudicator runs; "Adjudicate first" approved by the owner)

Nothing above is edited. No adjudication and no gate result exists when this is written. **No reviewer's scores were read; only counts that the harness's own rules define were computed.**

### Addendum C: independent review record
- Five Opus reviewers ran in parallel under Amendment 3, one per batch (reviewer ids `opus-1` to `opus-4` for batches 1 to 4, and `opus-5` for batch 5), fresh agents of the general-purpose type, model Opus. Reviewers `opus-1` to `opus-4` each reported opening only their own batch, `notes.jsonl` and their own output file, and each directory held exactly those three files.
- **One pass was void under Amendment 3's pre-declared rule.** Reviewer `opus-5`'s report also named a fourth file, a 133-line helper script (`gen005.py`) it had written in the orchestrator's scratchpad, containing a table of its own judgements and writing only its own output file; it read no other file. The rule was applied as written, not waived: the pass is void and unused, and is kept on record (review output SHA-256 `7a143ba587db426ab812b08682fee288e92b00a80258abd0b5a4beb238154222`, script SHA-256 `e359cb5bd214d9eb4e9a652ac16b5ae85f13b612cc472c1d628dae40a9fe6bbf`). Batch 5 was re-run by a **fresh reviewer `opus-6`** in a new directory, with the original pass and script moved out of reach first and one orchestrator line added to the prompt (create no file other than the output file). Its report named only its three permitted files.
- Every accepted pass was checked on receipt with the Amendment 2b checker (the harness's own validator on every row of every arm, expected round, reviewer id and exact batch ids): `opus-1` to `opus-4` 100 of 100 rows each and `opus-6` 97 of 97, no problems. Combined file `reviews-opus.jsonl`: 497 rows, each item once, round `INDEPENDENT_REVIEW`, SHA-256 `80e9a7a7b045193b354e20cfef5fa61934dbdd1ffe77e096464e294a3516f3b2`.

| Pass | Raw output SHA-256 |
|---|---|
| `opus-1` | `c2eb026d82af71d52e47069ad9f91cee3e330955c15b6c4f22b016ca4490d845` |
| `opus-2` | `480a576f60e9f9f45081ddce5a72bd8825ea196bb8dbbd1690864378a5d972a2` |
| `opus-3` | `9c1db0bb29a035165c97859af51dd3687ae22fa367a617cdbea2052b28f7ceb9` |
| `opus-4` | `6373166e5a1fd32c2f1ecb3c2068033de609f56b733b7fe8be75467204cdd242` |
| `opus-6` (replaces void `opus-5`) | `14fda2e90958e68007d5c677bead1461e70299f4f975912508967f1d1cda6d2c` |

### Mechanical counts that follow from the harness's rules
- **Disagreements needing adjudication: 144 of 497 reviewed items.** The harness's own definition (`QualityGateReport.Item.disagreement`): the primary and independent rows differ on `critical`, or on `seriousGiveaway`, or by 2 or more points on any of `distractorPlausibility`, `modeAlignment`, `cognitiveDemand`, `clarity`, `faithfulness`. An unresolved disagreement makes that arm unable to pass. By trigger (an item can have several): giveaway flag 62, critical flag 21, distractor 31, faithfulness 25, mode alignment 20, clarity 18, cognitive demand 10. By arm: A1 44, A2 30, A3 14, B2 23, B3 15, stock 18. Id list SHA-256 `5cefb7dc14a997228e8845f5b7ab8fc55487708a66699567724240e39251a8eb`.
- **The missed-defect escalation is triggered for B2.** The rubric's trigger is a critical defect "found by an independent reviewer" that the primary scorer did not flag, in a proposed-prompt arm. There are 3 such items overall: one in A1, one in the stock arm (neither triggers) and **one in B2 (`blind-0394`)**, none in B3. Under the signed rubric the entire B2 arm (300 items) then needs a separate independent re-review by a fresh cold reviewer, and B2 cannot pass without it.

### Amendment 4a: adjudication design
- The 144 items are split into **two batches of 72** by a seeded shuffle (seed `176 XOR 0xAD17`, recorded here; the shuffle and the labelling below are the only random choices). Each batch line is the blind item plus two earlier judgements under `reviewer1` and `reviewer2` (the 12 scoring fields only; no reviewer id, round, arm, flag or other label). **Which of the two judgements is `reviewer1` is chosen by the same seeded generator per item** (reviewer 1 is the primary row in 71 of 144), so position carries no information. The private assignment map is kept out of the adjudicators' reach.
- Adjudicator ids `adj-1` and `adj-2`, round `ADJUDICATION`, fresh Opus general-purpose agents, a scratch directory each under `~/quality-adjudication-v0176/a1` and `a2` holding only the batch and `notes.jsonl`, with the same delivery and isolation rules, self-report and receipt check as Amendment 3 and 2b. The harness's own rule that an adjudicator must not have scored the arm before is satisfied by fresh ids.
- The prompt is the frozen sections 1 to 6 verbatim (prompt-body SHA-256 `b9ab123d58e30ac423eb11537a2838a5f7ceaed4fc4ff0657682637f5fdeece4`) plus orchestrator lines: the directory path and the two-files-only rule; **"Each line also carries two conflicting earlier judgements, `reviewer1` and `reviewer2`, in the section 6 fields. Decide each item yourself from the item and its note; both earlier judgements may be wrong."**; the output file instruction; the rule that no other file may be created; and the file-report rule.

| Batch | Items | SHA-256 |
|---|---|---|
| `adjudication-batch-001.jsonl` (`adj-1`) | 72 | `2a3611afea02b34ca79a86739a7fbec796fb56c81e4679f31f49f0c778308120` |
| `adjudication-batch-002.jsonl` (`adj-2`) | 72 | `59b7f7f5dd159c13c4220a1de793aa07001d9e0c728a37d6efe4d67df4ebed91` |
| `assignment.jsonl` (private) | 144 | `6305005ad1ac8ccbc54af229e8a43c04dba13622a2f23cf22bfee2a542b9af16` |

### Amendment 4b: the B2 escalation is staged by cost, B2's gates stay uninterpreted until it is resolved
The rubric requires the B2 re-review "before its gates are interpreted". It is read here as binding **B2's** gates only. The order is: adjudicate; run the gate report with the B2 re-review recorded as **not yet done**, so B2 is reported as unable to pass and B2's gates are **not interpreted**; then decide the re-review. **If the other model's result already makes the overall outcome HOLD whatever B2 would show** (the signed rule is that both models must pass), the whole-arm B2 re-review is not run, B2's gates stay uninterpreted, and the report says exactly that. **If the overall outcome could still be PASS, the B2 re-review is run**: one fresh reviewer identity for the whole arm, all 300 items, in the same mechanism, with its own receipt checks and with new disagreements adjudicated by fresh adjudicators. The decision to skip is stated to the owner before anything is skipped and is recorded here as a dated entry.

## 11. Addendum D: adjudication record, gate report and the staged B2 decision (2026-10-27)

Nothing above is edited.

**Adjudication.** Two fresh Opus adjudicators (`adj-1` on 72 items, `adj-2` on 72 items) ran under Amendment 4a. Each reported opening only its own batch, `notes.jsonl` and its own output file; each directory held exactly those three files. Every row of both passes was checked on receipt with the Amendment 2b checker: 72 of 72 and 72 of 72, no problems. The two passes cover exactly the 144 ids of Amendment 4. Combined `adjudications.jsonl`: 144 rows, round `ADJUDICATION`, SHA-256 `73bb823ac6f35806b0af586144b3310172a7b68f5f691ef24c297964c8219f0b`. Raw outputs: `adj-1` `48d14ecc6d285b6c1f8eb5291a6cef4cbd4aaf46ee2694e606aad3ba4dd11d6c`, `adj-2` `29e5449b3a9e9a629a17c6e9986a2c3eafde9eff6bdd0c74b60ac60d805f33d6`.

**Gate report.** Run offline with the merged calculator (tree `f5f20006c3a2ecf705460bc2366e82f664a5ab14`) on the frozen primary scores, the accepted reviews and the adjudications, with **no B2 whole-arm re-review present**. Overall verdict **HOLD**. `gates.json` SHA-256 `fc6875e7b228dc1899e471a01719beffd1de98d423c18888e8daf941aaab25a6`, `gates.md` `de804e80870b6ae5f5bf61ac8e972d4c2ef6d1f7a97d5b72d67c1f654e4ade49`. A1 is reported incompletely scored (the two rows of Amendment 2); it is no gate's arm.

**Sol (A3 against B3, 20 paired notes, missingness CLEAR):** G1 PASS (0 confirmed critical defects in either arm); **G2 FAIL** (distractor mean 3.59 against 3.75); **G3 FAIL** (mode alignment 3.81 against 4.23); G4 PASS for B3 (estimate 2.7%, upper limit 7.3%) with A3's own G4 at 4.5%; **G5 FAIL**; G6, G7 and G8 PASS. Cognitive demand, descriptive only, fell from 3.47 to 2.90. Per-level reads are descriptive: BOARD_EXAM_REVIEW distractor -0.14 and mode alignment -0.34, COLLEGE mode alignment 3.75 against 4.25. **Sol fails G2, G3 and G5, so the overall outcome is HOLD under the signed rule that both models must pass, whatever B2 would show.**

**Disclosure.** While reading the report the orchestrator printed the overall reasons line, which also listed Luna's failed gates. That showed B2's gate outcomes, which Amendment 4b said stay uninterpreted. Nothing in the decision below rests on it.

**Staged B2 decision under Amendment 4b (owner approved 2026-10-27):** the whole-arm B2 re-review (300 items) is **not run**. Reason: the overall outcome is already HOLD because of Sol alone, so the re-review could not change it. B2's gates remain **uninterpreted**, B2 is reported as unable to pass without the re-review, and no statement about the Luna comparison is made in the evaluation report beyond that. The B2 escalation fact itself stands: one critical defect found by an independent reviewer and not flagged by the primary scorer in a proposed-prompt arm (`blind-0394`).

**Outcome under the signed rubric (section 6):** HOLD. No prompt change and no measurement-only deploy without separate owner approval. The evaluation recommends only; it changes no production prompt.
