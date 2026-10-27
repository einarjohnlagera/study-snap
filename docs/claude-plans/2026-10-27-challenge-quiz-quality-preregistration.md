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
