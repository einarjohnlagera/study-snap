# v0.175.0 R1 acceptance run: statement-pair guard on `gpt-4.1-mini` and `gpt-6-luna`

**Result: mini FAILS the pre-stated pass rule; Luna PASSES it as a smoke test (31 of 31, see the Luna arm
section).** On mini, blind checkers judged 3 of 32 delivered statement-pair keys wrong: 2 clearly, 1 arguably.
These are not the error class R1 targets. In all three, the model's own verdicts, its explanation and its
key agree with each other, and the verdict itself is factually wrong. The wording doc (§2) and owner
requirement B anticipated exactly this.

Written 2026-10-20 by Release Implementor. The paid calls were run by the owner (cap $1.00). Raw items, the
blind key and the scores are local and not committed: `backend/target/llm-benchmark/r1-mini*/` and the
session scratchpad.

## Run

- **What ran:**
  - **Full run:** Leg E single-note cells S15, S12 and S10, 10 packs each, 30 starts.
  - **Earlier small run:** 3 starts (one per cell) from a mis-specified first command (`runs=10` selected
    only run 10). 37 of 37 questions were delivered, with 4 statement-pair items and no rejections. Those 4
    items are included in the blind check.
- **Model and path:** `gpt-4.1-mini`, through the real `generateChallengeQuiz` path with the R1 schema and
  check.
- **Cost:** about $0.18 in total ($0.169 + $0.013).

**Full-run generation counts** (`challenge_quiz_generation_counts`: 33 call lines, for 30 starts plus 3 failed
first attempts):

| | Value |
|---|---|
| Requested | 370 questions across the 30 starts (the 3 failed attempts add another 45) |
| Statement-pair rejections | **0** |
| H4 (numeric) rejections | 8, each retried once; 1 omitted after its replacement failed |
| Whole-call invalid-output retries | 3: invalid answer mapping, invalid choices, invalid multi-select; all succeeded on attempt 2 |
| Delivered | 369 of 370. One S12 start delivered 11 of 12, within the owner's minimum |
| Retry time | 2.0–3.8 s per correction call; median call 15.1 s, max 30.6 s |

The model filled `statementVerdicts` on every statement-pair item, and every item used the four standard
choices in standard order.

## Blind check (owner requirement B)

- **Method:**
  - Two cold Opus reviewers each checked 16 of the 32 statement-pair items (shuffled, seed 1513).
  - Each saw the item's source note, the question and the choices. Neither saw the key, the model's verdicts
    or its explanation.
  - Each judged both statements independently against the source, then picked the choice.
- **Agreement:** 29 of 32 matched the key. One of the 29 (an open-traverse accuracy item) was marked
  ambiguous on Statement 1, with the reviewer leaning to the key's answer.
- **Disagreements:** 3. In each the model keyed "Both statements are correct" with an explanation saying so,
  and one statement is false.
  - The release implementor re-read all three after seeing the reviewers' reasoning, so that re-read is not
    independent.
  - r1-04 is arguable: its reviewer noted that the author may have meant deflection angles. The other two
    are clear.

  | Item | Topic | Why one statement is false |
  |---|---|---|
  | r1-04 | Surveying, open traverse | Says bearings are measured relative to the prior line. Bearings are referenced to the meridian; angles from the prior line are deflection angles |
  | r1-06 | Architecture, Corinthian order | Says columns "always" have fluted shafts. Unfluted Corinthian shafts exist |
  | r1-07 | Steel connections | Says pretensioning "eliminates" slip and "ensures equal" load sharing. Both absolutes are false |

- **The pattern:** two of the three turn on an absolute word the model accepted as true.

## What it means

- **The delivered error rate has not been shown to drop.** 3 of 32 now, against 2 of 17 in C7 before R1, cannot
  be told apart at this size (requirement E).
- **What the data does suggest:** no delivered item had an explanation contradicting its key, which was the
  dominant pre-R1 error. At mini's earlier rate about 4 of 32 would be expected, and seeing none by chance is
  roughly a 2% event. That is suggestive, not proof. 17 of 32 keys are "Only Statement 1", the answer that was
  previously mis-keyed about a third of the time.
- **The server check fired zero times.** Any improvement came from the field order and the prompt, not from
  rejections. The check only catches a key that contradicts the model's own verdicts. With the verdicts
  written just before the answer, that rarely happens. **Nothing in production catches the factual-verdict
  errors, and they are what remains.**
- **The remaining errors are factual verdicts:** about 9% of statement-pair items here (3 of 32), or about 0.8%
  of all delivered questions. Learners would still be marked wrong for a right answer on these.
- **For comparison:** C7 found 2 of 17 wrong statement-pair keys on mini before R1 (the index class). Sol had 0
  of 42 in C7.

## Options for the owner (not decided)

1. **Accept R1 as merged** and record mini's factual-verdict errors as a known limitation.
   - Item 6 then moves FREE Challenge to Luna, which **now passes with R1** (Luna arm below), and Plus/Pro
     Challenge to Sol.
   - **Sol is untested with R1.** Its 0 of 42 in C7 predates R1's schema change, which applies to every model.
     A Sol arm of this run is available on request; it needs separate paid approval.
   - **What stays on mini after item 6:** multi-note starts stay pinned until item 5 ships (`RELEASES.md`
     item 6 seam), along with every other quiz mode. Mini's factual-verdict errors therefore remain live on
     multi-note Challenge, and in today's production, until then.
2. **Add a prompt rule:** when judging a statement, a standard exception to an absolute word ("always",
   "never", "only", "eliminates", "ensures") makes the statement false. This is a prompt change, so it needs
   owner sign-off on the exact text. A comparable paid re-run (about $0.18) would check it, with the same
   smoke-test framing.
3. **Add a verifier call** for statement-pair items. This adds latency and cost to a blocking start, against the
   owner's performance rule; it fits item 2's background preparation better.

## Luna arm (owner-approved follow-up, 2026-10-20)

**Result: the pass rule PASSES on Luna, as a smoke test only (requirement E).** Blind checkers agreed with
31 of 31 delivered statement-pair keys.

- **What ran:** the same 30 single-note starts (S15, S12, S10, runs 1–10), on `gpt-6-luna` through the real
  `generateChallengeQuiz` path. Output is in `backend/target/llm-benchmark/r1-luna/`.
- **Cost:** $0.0996 (conservative meter). The total across all R1 acceptance runs is about $0.28 of the $1.00
  cap.

| | Value |
|---|---|
| Requested | 370 questions across 30 starts |
| Delivered | **370 of 370**; every start was full on attempt 1 |
| Statement-pair rejections | 0 |
| H4 rejections | 0 |
| Whole-call retries | 0 |
| Statement-pair items | 31: 14 "Both", 10 "Only Statement 1", 7 "Only Statement 2", 0 "Neither"; all four standard choices in standard order |
| Call latency | median 45.6 s, p95 63.9 s, max 69.3 s (one call per start) |

- **Blind check:**
  - Same method and the same reviewer prompt as the mini arm: two cold Opus reviewers, 16 and 15 items,
    shuffled with seed 1513, no key, verdicts or explanations shown.
  - The result was 31 of 31 agreed, and no item was marked ambiguous.
  - The reviewers raised wording caveats on three items, all still judged as keyed:
    - L-13: international recognition of the 1898 declaration.
    - L-17 and L-20: July 4 described as "autonomy" rather than independence.
- **Explanations:** the release implementor read all 31 (this read is not blind). None contradicts its key.
- **What it means:**
  - Before R1, Luna had 11 of 58 wrong statement keys (about 19%). That baseline comes from C7 plus B7 Phase 1:
    blind Opus factual checks of quiz-mode items, 26 of them from Challenge starts.
  - At that rate, seeing 0 of 31 would be about a 0.15% event. Even at a pessimistic 10% baseline it would be
    about 4%. This is strong evidence the rate dropped.
  - It does not show the rate is zero. The 95% upper bound for 0 of 31 is about 9%, which overlaps mini's 3 of
    32.
  - The absence of "Neither" keys is noted but not assessed.
- **Latency, compared start to start:**
  - Luna start p50 by cell: 45.0 s (S15), 52.1 s (S12), 36.9 s (S10). The worst start was 69.3 s.
  - Mini start p50 by cell: 18.0, 14.1 and 10.4 s. Mini's worst start was 40.0 s.
  - **This confirms the earlier figure; it is not new.** The 2026-10-19 targeted start benchmark measured Luna
    at a 46–49 s p50 on the same sizes, which is why item 6 is queued after item 2's background preparation.
  - **R1 added no measurable latency on Luna:** zero retries fired.

**Nothing has reached learners.** R1 is merged into `releases/v0.175.0` only, which cannot reach `main` until
after v0.174.0's #1512.
